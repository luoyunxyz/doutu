package com.luoyun.doutu.features.feedback;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.luoyun.doutu.account.DoutuApi;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 「我 → 求新功能」：意见反馈页。
 *
 * <p>用户在这里可以「上传图片 + 写意见 + 提交」，提交成功后弹一个感谢窗并返回。
 * 后台「意见反馈」菜单里能看到全部提交（含图片、机型、系统版本）。
 *
 * <p>为什么用系统控件而不是像主界面那样手绘：这一页是表单，输入框、滚动、键盘避让
 * 交给系统反而更稳；配色沿用 App 主色，看起来还是同一个应用。
 */
public final class FeedbackActivity extends Activity {

    /** 最多上传几张图（和服务端 MAX_IMAGES 对齐） */
    private static final int MAX_IMAGES = 3;
    /** 单张压缩后的目标上限：服务端单张 1MB，这里压到 350KB 以内，3 张也不会撑爆请求体 */
    private static final int IMAGE_TARGET_BYTES = 350 * 1024;
    /** 压缩后图片最长边 */
    private static final int IMAGE_MAX_SIDE = 1280;
    /** 意见正文上限 */
    private static final int CONTENT_MAX = 1000;

    private static final int REQUEST_PICK = 5101;

    private static final int BRAND = 0xFF6F6AE8;
    private static final int BRAND_DISABLED = 0xFFC9C6E8;
    private static final int INK = 0xFF23232F;
    private static final int SUB = 0xFF8A8A9A;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> images = new ArrayList<>();   // 压缩后的 data-URL

    private LinearLayout thumbs;
    private EditText content;
    private EditText contact;
    private TextView counter;
    private Button submit;
    private boolean sending;

    public static void open(Activity activity) {
        activity.startActivity(new Intent(activity, FeedbackActivity.class));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        buildUi();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFF5F6FA);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        root.addView(scroller, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(18), dp(16), dp(18), dp(20));
        scroller.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── 标题栏：返回 + 标题 ────────────────────────────────────────────
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(bar);

        TextView back = new TextView(this);
        back.setText("‹ 返回");
        back.setTextSize(16f);
        back.setTextColor(INK);
        back.setPadding(0, dp(6), dp(10), dp(6));
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText("求新功能");
        title.setTextSize(20f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(INK);
        bar.addView(title);

        TextView hint = new TextView(this);
        hint.setText("想要什么功能、哪里用着别扭，都可以告诉我们。支持上传截图，最长 1000 字。");
        hint.setTextSize(13f);
        hint.setTextColor(SUB);
        hint.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(4);
        column.addView(hint, hintLp);

        // ── 图片 ──────────────────────────────────────────────────────────
        column.addView(sectionLabel("上传图片（选填，最多 " + MAX_IMAGES + " 张）"), topMargin(dp(16)));

        thumbs = new LinearLayout(this);
        thumbs.setOrientation(LinearLayout.HORIZONTAL);
        thumbs.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(thumbs, topMargin(dp(8)));
        renderThumbs();

        // ── 意见正文 ──────────────────────────────────────────────────────
        column.addView(sectionLabel("你的意见 / 建议"), topMargin(dp(18)));

        content = new EditText(this);
        content.setHint("例如：希望增加批量去水印、希望支持小红书图文…");
        content.setTextSize(15f);
        content.setTextColor(INK);
        content.setGravity(Gravity.TOP);
        content.setMinLines(5);
        content.setMaxLines(9);
        content.setFilters(new InputFilter[]{new InputFilter.LengthFilter(CONTENT_MAX)});
        content.setBackground(round(Color.WHITE, dp(12)));
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentLp.topMargin = dp(8);
        column.addView(content, contentLp);

        counter = new TextView(this);
        counter.setTextSize(12f);
        counter.setTextColor(SUB);
        counter.setGravity(Gravity.END);
        content.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { updateCounter(); }
        });
        column.addView(counter, topMargin(dp(4)));
        updateCounter();

        // ── 联系方式 ──────────────────────────────────────────────────────
        column.addView(sectionLabel("联系方式（选填）"), topMargin(dp(14)));

        contact = new EditText(this);
        contact.setHint("手机号 / 微信 / QQ，方便我们回复你");
        contact.setTextSize(15f);
        contact.setTextColor(INK);
        contact.setSingleLine(true);
        contact.setBackground(round(Color.WHITE, dp(12)));
        contact.setPadding(dp(14), dp(12), dp(14), dp(12));
        column.addView(contact, topMargin(dp(8)));

        // ── 提交 ──────────────────────────────────────────────────────────
        submit = new Button(this);
        submit.setText("提交");
        submit.setTextSize(16f);
        submit.setAllCaps(false);
        submit.setTextColor(Color.WHITE);
        submit.setBackground(round(BRAND, dp(24)));
        LinearLayout.LayoutParams submitLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        submitLp.topMargin = dp(22);
        column.addView(submit, submitLp);
        submit.setOnClickListener(v -> send());

        setContentView(root);
    }

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = margin;
        return lp;
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(14f);
        label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(INK);
        return label;
    }

    private static GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private void updateCounter() {
        if (counter == null || content == null) return;
        counter.setText(content.getText().length() + " / " + CONTENT_MAX);
    }

    /* ------------------------------ 图片选择与压缩 ------------------------------ */

    /** 画一排缩略图 + 一个「添加图片」的虚线格子。 */
    private void renderThumbs() {
        if (thumbs == null) return;
        thumbs.removeAllViews();
        int side = dp(78);
        for (int i = 0; i < images.size(); i++) {
            final int index = i;
            FrameLayout box = new FrameLayout(this);
            ImageView view = new ImageView(this);
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setImageBitmap(decodeThumb(images.get(i)));
            view.setBackground(round(0xFFEDEDF3, dp(10)));
            box.addView(view, new FrameLayout.LayoutParams(side, side));

            TextView remove = new TextView(this);
            remove.setText("×");
            remove.setTextSize(14f);
            remove.setGravity(Gravity.CENTER);
            remove.setTextColor(Color.WHITE);
            remove.setBackground(round(0xCC000000, dp(11)));
            FrameLayout.LayoutParams removeLp = new FrameLayout.LayoutParams(dp(22), dp(22));
            removeLp.gravity = Gravity.TOP | Gravity.END;
            box.addView(remove, removeLp);
            remove.setOnClickListener(v -> {
                if (sending) return;
                images.remove(index);
                renderThumbs();
            });

            LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(side, side);
            boxLp.rightMargin = dp(10);
            thumbs.addView(box, boxLp);
        }

        if (images.size() < MAX_IMAGES) {
            TextView add = new TextView(this);
            add.setText("+ 添加图片");
            add.setTextSize(12f);
            add.setGravity(Gravity.CENTER);
            add.setTextColor(BRAND);
            GradientDrawable dashed = new GradientDrawable();
            dashed.setShape(GradientDrawable.RECTANGLE);
            dashed.setColor(0xFFFFFFFF);
            dashed.setCornerRadius(dp(10));
            dashed.setStroke(dp(1), 0xFFC7C3F0);
            add.setBackground(dashed);
            add.setOnClickListener(v -> pickImage());
            LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(side, side);
            addLp.rightMargin = dp(10);
            thumbs.addView(add, addLp);
        }
    }

    private Bitmap decodeThumb(String dataUrl) {
        try {
            byte[] bytes = Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    private void pickImage() {
        if (sending) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQUEST_PICK);
        } catch (Exception e) {
            toast("打不开相册：" + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        setBusy(true, "正在处理图片…");
        new Thread(() -> {
            final String encoded = encodeImage(uri);
            handler.post(() -> {
                setBusy(false, null);
                if (encoded == null || encoded.isEmpty()) {
                    toast("这张图片读不出来，换一张试试");
                    return;
                }
                images.add(encoded);
                renderThumbs();
            });
        }).start();
    }

    /**
     * 读一张图 → 按最长边缩到 IMAGE_MAX_SIDE → 压成 JPEG。
     * 目标是让单张落在 IMAGE_TARGET_BYTES 以内：先按 82 压，超了就降质量再压，
     * 降到 45 还超就再缩一次尺寸。整个过程都在后台线程里跑。
     */
    private String encodeImage(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (longest / sample > IMAGE_MAX_SIDE * 2) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap decoded;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                decoded = BitmapFactory.decodeStream(in, null, opts);
            }
            if (decoded == null) return null;

            Bitmap scaled = decoded;
            int decodedLongest = Math.max(decoded.getWidth(), decoded.getHeight());
            if (decodedLongest > IMAGE_MAX_SIDE) {
                float ratio = IMAGE_MAX_SIDE / (float) decodedLongest;
                scaled = Bitmap.createScaledBitmap(decoded,
                        Math.max(1, Math.round(decoded.getWidth() * ratio)),
                        Math.max(1, Math.round(decoded.getHeight() * ratio)), true);
                if (scaled != decoded) decoded.recycle();
            }

            byte[] bytes = compress(scaled, 82);
            for (int quality = 70; bytes != null && bytes.length > IMAGE_TARGET_BYTES && quality >= 45; quality -= 12) {
                bytes = compress(scaled, quality);
            }
            if (bytes != null && bytes.length > IMAGE_TARGET_BYTES) {
                // 还是太大：再缩一半尺寸压一次（截图类图片基本不会走到这里）
                Bitmap smaller = Bitmap.createScaledBitmap(scaled,
                        Math.max(1, scaled.getWidth() / 2), Math.max(1, scaled.getHeight() / 2), true);
                bytes = compress(smaller, 70);
                smaller.recycle();
            }
            scaled.recycle();
            if (bytes == null || bytes.length == 0) return null;
            return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] compress(Bitmap bitmap, int quality) {
        if (bitmap == null || bitmap.isRecycled()) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return out.toByteArray();
    }

    /* ------------------------------ 提交 ------------------------------ */

    private void setBusy(boolean busy, String label) {
        sending = busy;
        if (submit == null) return;
        submit.setEnabled(!busy);
        submit.setText(busy ? (label == null ? "提交中…" : label) : "提交");
        submit.setBackground(round(busy ? BRAND_DISABLED : BRAND, dp(24)));
    }

    private void send() {
        if (sending) return;
        final String text = content.getText().toString().trim();
        if (text.length() < 2) {
            toast("请先写两句你的意见或建议");
            return;
        }
        setBusy(true, "提交中…");
        DoutuApi.submitFeedback(this, text, images, contact.getText().toString().trim(),
                Build.MANUFACTURER + " " + Build.MODEL,
                "Android " + Build.VERSION.RELEASE,
                result -> {
                    setBusy(false, null);
                    if (!result.ok()) {
                        toast(result.msg == null || result.msg.isEmpty() ? "提交失败，请稍后重试" : result.msg);
                        return;
                    }
                    thankYou();
                });
    }

    /** 提交成功后的感谢弹窗：点「好的」回上一页。 */
    private void thankYou() {
        new AlertDialog.Builder(this)
                .setTitle("感谢您的反馈！")
                .setMessage("我们已经收到你的建议，会认真阅读每一条。\n你的想法很可能就是下一个版本里的新功能，谢谢你帮我们把它做得更好。")
                .setCancelable(false)
                .setPositiveButton("好的", (d, w) -> {
                    finish();
                })
                .show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
