package com.luoyun.doutu.account;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 「短视频一键去水印」页（娱乐页那一格点进来）。
 *
 * 版式从上到下：粘贴链接的输入框 → 一键解析按钮 → 进度 → 结果卡片（页面底部）
 * → 点击结果内联预览（视频用 VideoView，图集用图片）→ 预览下方「下载」按钮。
 *
 * **仅会员可用**：进页面前与提交解析时都会校验会员状态，服务端也会兜底返回 2001。
 */
public final class ParseActivity extends Activity {

    private static final int BRAND = 0xFF2F6FED;
    private static final int BRAND_2 = 0xFF6E8BFF;
    private static final int INK = 0xFF1B1F27;
    private static final int SUB = 0xFF8A8F9A;
    private static final int FIELD_BG = 0xFFF4F6FA;
    /** 轮询总时长兜底：超过这个时间还没结果就明确报超时，不要无限转圈。 */
    private static final long POLL_DEADLINE_MS = 8 * 60 * 1000L;

    /** 状态 → 进度百分比（服务端 task.status）。 */
    private static int percentOf(int status) {
        switch (status) {
            case 0: return 15;
            case 1: return 45;
            case 2: return 80;
            case 3: return 100;
            default: return 0;
        }
    }

    private EditText linkField;
    private TextView parseButton;
    private LinearLayout progressCard;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView progressHint;
    private LinearLayout resultCard;
    private ImageView coverView;
    private TextView resultTitle;
    private TextView resultMeta;
    private TextView previewHint;
    private FrameLayout previewBox;
    private VideoView videoView;
    private ImageView imagePreview;
    private LinearLayout downloadButton;
    private TextView downloadLabel;
    private TextView downloadProgress;
    private LinearLayout imageStrip;
    /** 已保存到本地的成品（下载成功后「下载」按钮变成「打开」）。 */
    private Uri savedUri;
    private String savedWhere = "";
    private boolean downloading;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    private String taskNo;
    private boolean polling;
    /** 连续查询失败次数；单次网络抖动不应该让进度条停住。 */
    private int pollFailures;
    /** 本轮轮询的起点，用于总时长兜底。 */
    private long pollStartedAt;
    private String outUrl;
    private String coverUrl;
    private String title = "doutu";
    private boolean imageMode;
    private final java.util.List<String> imageUrls = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(buildUi());
        if (!Session.isActivated()) {
            new AlertDialog.Builder(this)
                    .setTitle("会员功能")
                    .setMessage("去水印是会员功能，开通后即可解析并下载无水印成品。")
                    .setNegativeButton("知道了", (d, w) -> finish())
                    .setPositiveButton("查看会员", (d, w) -> {
                        SettingsActivity.open(this);
                        finish();
                    })
                    .show();
        }
    }

    @Override
    protected void onDestroy() {
        polling = false;
        if (videoView != null) {
            videoView.stopPlayback();
        }
        pool.shutdownNow();
        super.onDestroy();
    }

    /* ------------------------------ 界面 ------------------------------ */

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        GradientDrawable page = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFE9EFFF, 0xFFFDFDFF});
        scroll.setBackground(page);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(24));
        scroll.addView(root);

        // 标题栏：返回 + 标题
        FrameLayout header = new FrameLayout(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        root.addView(header);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(36);
        back.setTextColor(INK);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new FrameLayout.LayoutParams(dp(52), dp(52)));
        TextView titleView = new TextView(this);
        titleView.setText("短视频去水印");
        titleView.setTextSize(19);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(INK);
        titleView.setGravity(Gravity.CENTER);
        header.addView(titleView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 输入卡
        LinearLayout inputCard = card(root);
        TextView label = new TextView(this);
        label.setText("粘贴分享链接或文案");
        label.setTextSize(13);
        label.setTextColor(SUB);
        inputCard.addView(label);

        linkField = new EditText(this);
        linkField.setHint("例如：7.22 复制打开抖音，看看【xx的作品】https://v.douyin.com/xxx/");
        linkField.setHintTextColor(0xFFB9BEC8);
        linkField.setTextSize(14.5f);
        linkField.setTextColor(INK);
        linkField.setGravity(Gravity.TOP);
        linkField.setMinLines(3);
        linkField.setMaxLines(5);
        linkField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        GradientDrawable fieldBg = new GradientDrawable();
        fieldBg.setColor(FIELD_BG);
        fieldBg.setCornerRadius(dp(12));
        linkField.setBackground(fieldBg);
        linkField.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fieldParams.topMargin = dp(10);
        linkField.setLayoutParams(fieldParams);
        inputCard.addView(linkField);

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(0, dp(10), 0, 0);
        inputCard.addView(tools);
        tools.addView(smallButton("粘贴", BRAND, v -> pasteFromClipboard()));
        tools.addView(space(dp(10)));
        tools.addView(smallButton("清空", 0xFF9AA3B2, v -> linkField.setText("")));

        parseButton = new TextView(this);
        parseButton.setText("一键解析");
        parseButton.setTextSize(17);
        parseButton.setTypeface(Typeface.DEFAULT_BOLD);
        parseButton.setTextColor(Color.WHITE);
        parseButton.setGravity(Gravity.CENTER);
        GradientDrawable btn = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{BRAND, 0xFF5B84FF});
        btn.setCornerRadius(dp(14));
        parseButton.setBackground(btn);
        parseButton.setPadding(0, dp(16), 0, dp(16));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = dp(16);
        parseButton.setLayoutParams(btnParams);
        parseButton.setOnClickListener(v -> startParse());
        inputCard.addView(parseButton);

        // 进度卡（默认隐藏）
        progressCard = card(root);
        progressCard.setVisibility(View.GONE);
        progressText = new TextView(this);
        progressText.setTextSize(14);
        progressText.setTextColor(INK);
        progressText.setTypeface(Typeface.DEFAULT_BOLD);
        progressCard.addView(progressText);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        barParams.topMargin = dp(10);
        progressBar.setLayoutParams(barParams);
        progressCard.addView(progressBar);
        progressHint = new TextView(this);
        progressHint.setTextSize(12.5f);
        progressHint.setTextColor(SUB);
        progressHint.setPadding(0, dp(8), 0, 0);
        progressHint.setText("排队中 → 解析中 → 合成中");
        progressCard.addView(progressHint);

        // 结果卡（默认隐藏，出现在页面底部）
        resultCard = card(root);
        resultCard.setVisibility(View.GONE);
        TextView resultLabel = new TextView(this);
        resultLabel.setText("解析结果");
        resultLabel.setTextSize(13);
        resultLabel.setTextColor(SUB);
        resultCard.addView(resultLabel);

        LinearLayout resultRow = new LinearLayout(this);
        resultRow.setOrientation(LinearLayout.HORIZONTAL);
        resultRow.setPadding(0, dp(10), 0, 0);
        resultRow.setClickable(true);
        resultRow.setOnClickListener(v -> togglePreview());
        resultCard.addView(resultRow);

        coverView = new ImageView(this);
        coverView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable coverBg = new GradientDrawable();
        coverBg.setColor(0xFFE8EDF9);
        coverBg.setCornerRadius(dp(10));
        coverView.setBackground(coverBg);
        resultRow.addView(coverView, new LinearLayout.LayoutParams(dp(96), dp(96)));

        LinearLayout resultTexts = new LinearLayout(this);
        resultTexts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textsParams.leftMargin = dp(12);
        resultRow.addView(resultTexts, textsParams);
        resultTitle = new TextView(this);
        resultTitle.setTextSize(15);
        resultTitle.setTextColor(INK);
        resultTitle.setTypeface(Typeface.DEFAULT_BOLD);
        resultTitle.setMaxLines(2);
        resultTexts.addView(resultTitle);
        resultMeta = new TextView(this);
        resultMeta.setTextSize(12.5f);
        resultMeta.setTextColor(SUB);
        resultMeta.setPadding(0, dp(6), 0, 0);
        resultTexts.addView(resultMeta);

        previewHint = new TextView(this);
        previewHint.setTextSize(12.5f);
        previewHint.setTextColor(BRAND);
        previewHint.setGravity(Gravity.CENTER);
        previewHint.setPadding(0, dp(10), 0, 0);
        previewHint.setText("点击结果可预览");
        resultCard.addView(previewHint);

        // 图集缩略图（图集作品才有）
        imageStrip = new LinearLayout(this);
        imageStrip.setOrientation(LinearLayout.HORIZONTAL);
        imageStrip.setVisibility(View.GONE);
        imageStrip.setPadding(0, dp(10), 0, 0);
        resultCard.addView(imageStrip);

        // 预览区（默认隐藏）
        previewBox = new FrameLayout(this);
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setColor(Color.BLACK);
        previewBg.setCornerRadius(dp(12));
        previewBox.setBackground(previewBg);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220));
        previewParams.topMargin = dp(12);
        previewBox.setLayoutParams(previewParams);
        previewBox.setVisibility(View.GONE);
        resultCard.addView(previewBox);

        // 下载按钮（预览下方）
        downloadButton = new LinearLayout(this);
        downloadButton.setOrientation(LinearLayout.HORIZONTAL);
        downloadButton.setGravity(Gravity.CENTER);
        downloadButton.setClickable(true);
        GradientDrawable dlBg = new GradientDrawable();
        dlBg.setColor(Color.WHITE);
        dlBg.setCornerRadius(dp(30));
        dlBg.setStroke(dp(1.6f), 0xFF9FB3D9);
        downloadButton.setBackground(dlBg);
        downloadButton.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams dlParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlParams.topMargin = dp(14);
        downloadButton.setLayoutParams(dlParams);
        downloadButton.setVisibility(View.GONE);
        downloadLabel = new TextView(this);
        downloadLabel.setText("下载到本地");
        downloadLabel.setTextSize(16.5f);
        downloadLabel.setTypeface(Typeface.DEFAULT_BOLD);
        downloadLabel.setTextColor(0xFF2B3A55);
        downloadButton.addView(downloadLabel);
        downloadButton.setOnClickListener(v -> {
            if (savedUri != null) openSaved();
            else downloadToLocal();
        });
        resultCard.addView(downloadButton);

        downloadProgress = new TextView(this);
        downloadProgress.setTextSize(12.5f);
        downloadProgress.setTextColor(SUB);
        downloadProgress.setGravity(Gravity.CENTER);
        downloadProgress.setPadding(0, dp(8), 0, 0);
        downloadProgress.setVisibility(View.GONE);
        resultCard.addView(downloadProgress);

        TextView tip = new TextView(this);
        tip.setTextSize(11.5f);
        tip.setTextColor(0xFF9AA1AC);
        tip.setLineSpacing(0, 1.3f);
        tip.setPadding(0, dp(16), 0, 0);
        tip.setText("仅会员可用 · 请只处理你自己创作或拥有版权的内容，成品请在 15 分钟内下载。");
        root.addView(tip);

        return scroll;
    }

    private LinearLayout card(LinearLayout root) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(16));
        box.setBackground(bg);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        box.setElevation(dp(4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        box.setLayoutParams(params);
        root.addView(box);
        return box;
    }

    private TextView smallButton(String text, int color, View.OnClickListener onClick) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13.5f);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x112F6FED);
        bg.setCornerRadius(dp(18));
        tv.setBackground(bg);
        tv.setPadding(dp(18), dp(8), dp(18), dp(8));
        tv.setOnClickListener(onClick);
        return tv;
    }

    private View space(int width) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(width, 1));
        return v;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    /* ------------------------------ 解析流程 ------------------------------ */

    private void pasteFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) {
            toast("剪贴板是空的");
            return;
        }
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            toast("剪贴板是空的");
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text == null || text.length() == 0) {
            toast("剪贴板是空的");
            return;
        }
        linkField.setText(text.toString());
        toast("已粘贴");
    }

    private void startParse() {
        String link = linkField.getText().toString().trim();
        if (link.isEmpty()) {
            toast("请先粘贴分享链接");
            return;
        }
        if (!Session.isActivated()) {
            new AlertDialog.Builder(this)
                    .setTitle("会员功能")
                    .setMessage("去水印是会员功能，开通后即可解析。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("查看会员", (d, w) -> SettingsActivity.open(this))
                    .show();
            return;
        }
        resetResult();
        taskNo = null;
        outUrl = null;
        imageUrls.clear();
        imageMode = false;

        progressCard.setVisibility(View.VISIBLE);
        setProgress(8, "提交中…", "正在把链接发给服务端");
        parseButton.setEnabled(false);
        parseButton.setAlpha(0.6f);

        DoutuApi.parse(this, link, result -> {
            parseButton.setEnabled(true);
            parseButton.setAlpha(1f);
            if (!result.ok()) {
                progressCard.setVisibility(View.GONE);
                if (result.code == 2001) {
                    new AlertDialog.Builder(this)
                            .setTitle("需要开通会员")
                            .setMessage(result.msg == null || result.msg.isEmpty() ? "去水印是会员功能" : result.msg)
                            .setNegativeButton("知道了", null)
                            .setPositiveButton("查看会员", (d, w) -> SettingsActivity.open(this))
                            .show();
                } else {
                    toast(result.msg == null || result.msg.isEmpty() ? "解析失败" : result.msg);
                }
                return;
            }
            taskNo = result.data.optString("task_no", "");
            pollFailures = 0;
            pollStartedAt = System.currentTimeMillis();
            setProgress(percentOf(result.data.optInt("status", 0)), "已提交，排队中…", "任务号 " + taskNo);
            poll();
        });
    }

    /**
     * 轮询任务进度；完成后把结果渲染到页面底部。
     *
     * 两个「进度条卡死」的坑（2026-10-17 抖音实测）：
     *   - 以前只要有一次查询失败就 `polling = false` 永久停轮询 —— 网络抖一下、验签慢一次，
     *     进度条就永远钉在原地，看起来像死了。现在连续失败几次才放弃；
     *   - 加一个总时长兜底，避免服务端状态出问题时无限轮询。
     */
    private void poll() {
        if (taskNo == null || taskNo.isEmpty()) return;
        polling = true;
        if (System.currentTimeMillis() - pollStartedAt > POLL_DEADLINE_MS) {
            polling = false;
            progressCard.setVisibility(View.GONE);
            toast("解析超时，请稍后重试");
            return;
        }
        DoutuApi.task(this, taskNo, new DoutuApi.Callback() {
            @Override
            public void onResult(DoutuApi.Result result) {
                if (!polling) return;
                if (!result.ok() || result.data == null) {
                    pollFailures += 1;
                    if (pollFailures < 5) {
                        // 单次失败别停：继续问，进度条要能一直走
                        main.postDelayed(ParseActivity.this::poll, 2000);
                        return;
                    }
                    polling = false;
                    progressCard.setVisibility(View.GONE);
                    toast(result.msg == null || result.msg.isEmpty() ? "查询进度失败" : result.msg);
                    return;
                }
                pollFailures = 0;
                JSONObject d = result.data;
                int status = d.optInt("status", 0);
                String statusText = d.optString("status_text", "");
                int percent = percentOf(status);
                if (status == 3) {
                    polling = false;
                    setProgress(100, "解析完成", "成品已生成，可在下方预览与下载");
                    showResult(d);
                    return;
                }
                if (status == 4 || status == 5) {
                    polling = false;
                    progressCard.setVisibility(View.GONE);
                    String err = str(d, "err_msg", "");
                    toast(("超时".equals(statusText) ? "解析超时" : "解析失败") + (err.isEmpty() ? "" : "：" + err));
                    return;
                }
                String hint = status == 1 ? "正在解析分享链接…" : status == 2 ? "正在下载与合成成品…" : "排队中…";
                setProgress(percent, statusText + " · " + percent + "%", hint);
                main.postDelayed(ParseActivity.this::poll, 1200);
            }
        });
    }

    private void setProgress(int percent, String title, String hint) {
        progressBar.setProgress(Math.max(2, Math.min(100, percent)));
        progressText.setText(title);
        progressHint.setText(hint);
    }

    /* ------------------------------ 结果与预览 ------------------------------ */

    private void resetResult() {
        resultCard.setVisibility(View.GONE);
        previewBox.setVisibility(View.GONE);
        downloadButton.setVisibility(View.GONE);
        imageStrip.setVisibility(View.GONE);
        imageStrip.removeAllViews();
        if (videoView != null) {
            videoView.setVisibility(View.GONE);
            videoView.stopPlayback();
        }
        if (imagePreview != null) imagePreview.setVisibility(View.GONE);
        previewHint.setText("点击结果可预览");
    }

    /** 取字符串字段：JSON 里是 null 时不要显示成 "null"。 */
    private static String str(JSONObject o, String key, String fallback) {
        if (o == null || o.isNull(key)) return fallback;
        String v = o.optString(key, fallback);
        return "null".equals(v) ? fallback : v;
    }

    private void showResult(JSONObject d) {
        title = str(d, "title", "doutu");
        String platform = str(d, "platform_name", str(d, "platform", ""));
        String size = str(d, "out_size", "");
        String duration = str(d, "duration", "");
        outUrl = str(d, "out_url", "");
        String cover = str(d, "cover", "");
        coverUrl = imageMode ? "" : cover;
        JSONArray images = d.optJSONArray("images");
        imageUrls.clear();
        if (images != null) {
            for (int i = 0; i < images.length(); i++) {
                String u = images.optString(i, "");
                if (!u.isEmpty()) imageUrls.add(u);
            }
        }
        imageMode = d.optString("media_type", "VIDEO").equals("IMAGE") && !imageUrls.isEmpty();

        resultCard.setVisibility(View.VISIBLE);
        resultTitle.setText(title.isEmpty() ? "解析结果" : title);
        StringBuilder meta = new StringBuilder();
        if (!platform.isEmpty()) meta.append(platform);
        if (!duration.isEmpty()) meta.append(meta.length() > 0 ? " · " : "").append(duration).append(" 秒");
        if (!size.isEmpty()) meta.append(meta.length() > 0 ? " · " : "").append(formatSize(size));
        if (d.optInt("watermark", 0) == 0) meta.append(meta.length() > 0 ? " · " : "").append("无水印（会员）");
        resultMeta.setText(meta.toString());

        String thumb = imageMode ? imageUrls.get(0) : cover;
        if (thumb != null && !thumb.isEmpty()) loadImage(thumb, coverView);

        if (imageMode) {
            imageStrip.setVisibility(View.VISIBLE);
            for (int i = 0; i < Math.min(9, imageUrls.size()); i++) {
                final int index = i;
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xFFE8EDF9);
                bg.setCornerRadius(dp(8));
                iv.setBackground(bg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(64));
                lp.rightMargin = dp(8);
                iv.setLayoutParams(lp);
                iv.setOnClickListener(v -> {
                    previewBox.setVisibility(View.VISIBLE);
                    showImagePreview(imageUrls.get(index));
                    downloadButton.setVisibility(View.VISIBLE);
                    previewHint.setText("第 " + (index + 1) + " 张 / 共 " + imageUrls.size() + " 张");
                });
                imageStrip.addView(iv);
                loadImage(imageUrls.get(i), iv);
            }
        }

        previewHint.setText(imageMode
                ? "共 " + imageUrls.size() + " 张图片，点击缩略图预览"
                : "点击可预览 · 预览下方可下载");
        // 视频：先把预览与下载按钮显示出来（预览要用户点了才播，省流量）
        if (!imageMode) {
            downloadButton.setVisibility(View.VISIBLE);
            previewBox.setVisibility(View.GONE);
        }
    }

    private void togglePreview() {
        if (outUrl == null || outUrl.isEmpty()) {
            toast("成品地址还没准备好");
            return;
        }
        if (imageMode) {
            previewBox.setVisibility(View.VISIBLE);
            showImagePreview(imageUrls.isEmpty() ? outUrl : imageUrls.get(0));
            downloadButton.setVisibility(View.VISIBLE);
            return;
        }
        if (previewBox.getVisibility() == View.VISIBLE) {
            if (videoView != null) videoView.pause();
            previewBox.setVisibility(View.GONE);
            previewHint.setText("点击可预览 · 预览下方可下载");
            return;
        }
        previewBox.setVisibility(View.VISIBLE);
        downloadButton.setVisibility(View.VISIBLE);
        previewHint.setText("预览中…");
        if (videoView == null) {
            videoView = new VideoView(this);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            videoView.setLayoutParams(lp);
            MediaController controller = new MediaController(this);
            controller.setAnchorView(videoView);
            videoView.setMediaController(controller);
            previewBox.addView(videoView);
        }
        videoView.setVisibility(View.VISIBLE);
        videoView.setVideoURI(Uri.parse(outUrl));
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            videoView.start();
        });
        videoView.setOnErrorListener((mp, what, extra) -> {
            toast("预览失败，可以直接下载用播放器打开");
            return true;
        });
        videoView.requestFocus();
    }

    private void showImagePreview(String url) {
        if (imagePreview == null) {
            imagePreview = new ImageView(this);
            imagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
            previewBox.addView(imagePreview, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        imagePreview.setVisibility(View.VISIBLE);
        loadImage(url, imagePreview);
    }

    /** 后台下载图片并显示（成品地址 15 分钟有效，所以不缓存）。 */
    private void loadImage(String url, ImageView target) {
        pool.execute(() -> {
            Bitmap bmp = null;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)");
                if (conn.getResponseCode() == 200) {
                    try (InputStream in = conn.getInputStream()) {
                        bmp = BitmapFactory.decodeStream(in);
                    }
                }
            } catch (Exception ignored) {
                // 图片只是装饰，失败就留着占位底色
            } finally {
                if (conn != null) conn.disconnect();
            }
            final Bitmap result = bmp;
            if (result != null) main.post(() -> target.setImageBitmap(result));
        });
    }

    private String formatSize(String raw) {
        try {
            long bytes = Long.parseLong(raw);
            if (bytes >= 1024 * 1024) return String.format(java.util.Locale.CHINA, "%.1f MB", bytes / 1048576f);
            return (bytes / 1024) + " KB";
        } catch (Exception e) {
            return raw;
        }
    }

    /* ------------------------------ 下载到本地 ------------------------------ */

    /**
     * 把成品存进手机本地：
     *   Android 10+ 走 MediaStore 写 **`DCIM/豆图`**（图片写 `Pictures/豆图`），**不需要任何存储权限**。
     *   为什么是 DCIM：国产 ROM（vivo/OPPO/小米）的相册默认只扫 DCIM / Pictures / Download 这几个目录，
     *   写到 `Movies/` 虽然媒体库有记录，但相册里根本看不到（实测 vivo 就是这样）。
     *   写完后还会主动触发一次媒体扫描，双保险。
     * 下载过程在页面上显示百分比；存好后按钮变成「打开」。
     */
    private void downloadToLocal() {
        final String url = imageMode && !imageUrls.isEmpty() ? imageUrls.get(0) : outUrl;
        if (url == null || url.isEmpty()) {
            toast("成品地址还没准备好");
            return;
        }
        if (downloading) return;
        downloading = true;
        savedUri = null;
        downloadLabel.setText("下载中…");
        downloadButton.setAlpha(0.6f);
        downloadProgress.setVisibility(View.VISIBLE);
        downloadProgress.setText("正在下载…");

        final String safe = safeName();
        final String ext = imageMode ? ".jpg" : ".mp4";
        final String mime = imageMode ? "image/jpeg" : "video/mp4";
        // 相册能看到的目录（DCIM 对应视频、Pictures 对应图片）
        final String relativeDir = (imageMode ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DCIM) + "/豆图";

        pool.execute(() -> {
            HttpURLConnection conn = null;
            java.io.OutputStream out = null;
            Uri target = null;
            String where = "";
            String absolutePath = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)");
                if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());
                final long total = conn.getContentLength();

                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    android.content.ContentValues cv = new android.content.ContentValues();
                    cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, safe + ext);
                    cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime);
                    cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, relativeDir);
                    cv.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1);
                    final long nowSec = System.currentTimeMillis() / 1000;
                    cv.put(android.provider.MediaStore.MediaColumns.DATE_ADDED, nowSec);
                    cv.put(android.provider.MediaStore.MediaColumns.DATE_MODIFIED, nowSec);
                    Uri collection = imageMode
                            ? android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            : android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    target = getContentResolver().insert(collection, cv);
                    if (target == null) throw new Exception("无法在媒体库里创建文件");
                    out = getContentResolver().openOutputStream(target);
                    where = relativeDir + "/" + safe + ext;
                } else {
                    java.io.File dir = new java.io.File(
                            getExternalFilesDir(imageMode ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_MOVIES),
                            "豆图");
                    if (!dir.exists() && !dir.mkdirs()) throw new Exception("无法创建目录");
                    java.io.File file = new java.io.File(dir, safe + ext);
                    out = new java.io.FileOutputStream(file);
                    where = file.getAbsolutePath();
                    absolutePath = file.getAbsolutePath();
                }

                try (java.io.InputStream in = conn.getInputStream()) {
                    byte[] buf = new byte[64 * 1024];
                    long done = 0;
                    long lastTick = 0;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        done += n;
                        long now = System.currentTimeMillis();
                        if (now - lastTick > 200) {
                            lastTick = now;
                            final long d = done;
                            final long t = total;
                            main.post(() -> downloadProgress.setText(t > 0
                                    ? String.format(java.util.Locale.CHINA, "正在下载 %d%%（%.1f / %.1f MB）",
                                    d * 100 / t, d / 1048576f, t / 1048576f)
                                    : String.format(java.util.Locale.CHINA, "正在下载 %.1f MB", d / 1048576f)));
                        }
                    }
                }
                out.flush();
                out.close();
                out = null;

                if (android.os.Build.VERSION.SDK_INT >= 29 && target != null) {
                    // 取消 pending，媒体库里才会真正可见
                    android.content.ContentValues done = new android.content.ContentValues();
                    done.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0);
                    getContentResolver().update(target, done, null, null);
                    // 再取一次真实路径，供显式媒体扫描用（部分国产 ROM 只认扫描结果）
                    try (android.database.Cursor c = getContentResolver().query(target,
                            new String[]{android.provider.MediaStore.MediaColumns.DATA}, null, null, null)) {
                        if (c != null && c.moveToFirst()) absolutePath = c.getString(0);
                    } catch (Exception ignored) {
                        // DATA 列在新系统上可能取不到，那就跳过显式扫描（MediaStore 记录本身已经够了）
                    }
                }

                // 双保险：主动让系统扫一遍（老 ROM / 国产 ROM 常需要这一下才在相册出现）
                if (absolutePath != null) {
                    try {
                        android.media.MediaScannerConnection.scanFile(
                                getApplicationContext(), new String[]{absolutePath}, new String[]{mime}, null);
                    } catch (Exception ignored) {
                        // 扫描失败不影响已经写入的文件
                    }
                    try {
                        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new java.io.File(absolutePath))));
                    } catch (Exception ignored) {
                        // Android 10+ 已废弃这个广播，忽略
                    }
                }

                final Uri saved = target;
                final String whereFinal = where;
                final String pathFinal = absolutePath;
                // 顺手把封面也存进同一个相册目录：
                // 视频首帧常常是黑场淡入（B 站 MV 尤其明显），相册用它当封面就是一块黑，
                // 看着像「没东西」。补一张封面图，相册里一眼能看出是哪条作品。
                if (!imageMode) saveCoverSidecar();
                main.post(() -> {
                    downloading = false;
                    savedUri = saved != null ? saved : (pathFinal != null ? Uri.fromFile(new java.io.File(pathFinal)) : null);
                    savedWhere = whereFinal;
                    downloadButton.setAlpha(1f);
                    downloadLabel.setText("打开");
                    downloadProgress.setText("已保存到相册：" + whereFinal);
                    toast("已保存到相册（" + whereFinal + "）");
                });
            } catch (Exception e) {
                final String msg = e.getMessage();
                if (out != null) {
                    try {
                        out.close();
                    } catch (Exception ignored) {
                        // 收尾失败无所谓，下面按失败处理
                    }
                }
                main.post(() -> {
                    downloading = false;
                    downloadButton.setAlpha(1f);
                    downloadLabel.setText("重试下载");
                    downloadProgress.setText("下载失败：" + msg);
                    toast("下载失败：" + msg);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    /** 下载文件名：标题里的路径字符要去掉，否则媒体库接不了。 */
    private String safeName() {
        String safe = title == null || title.isEmpty() ? "doutu" : title.replaceAll("[\\\\/:*?\"<>|\\r\\n\\s]", "_");
        if (safe.length() > 40) safe = safe.substring(0, 40);
        return safe;
    }

    /**
     * 把封面图存进同一个相册目录（`<标题>_封面.jpg`）。
     * 失败也无所谓——视频已经存好了，这只是让相册里好看一点。
     */
    private void saveCoverSidecar() {
        if (coverUrl == null || coverUrl.isEmpty()) return;
        java.io.OutputStream out = null;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(coverUrl).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)");
            if (conn.getResponseCode() != 200) return;

            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, safeName() + "_封面.jpg");
            cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DCIM + "/豆图");
            Uri target = getContentResolver().insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (target == null) return;
            out = getContentResolver().openOutputStream(target);
            try (java.io.InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            out.flush();
            out.close();
            out = null;
        } catch (Exception e) {
            android.util.Log.w("ParseActivity", "封面保存失败：" + e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                    // 忽略
                }
            }
            if (conn != null) conn.disconnect();
        }
    }

    /** 打开刚保存的成品（系统播放器 / 图库）。 */
    private void openSaved() {
        try {
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(savedUri, imageMode ? "image/*" : "video/*");
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(view);
        } catch (Exception e) {
            toast("没有可打开它的应用\n" + savedWhere);
        }
    }

    /** 从「娱乐」页进来。 */
    public static void open(Context context) {
        Intent intent = new Intent(context, ParseActivity.class);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
