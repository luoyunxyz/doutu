package com.luoyun.doutu.features.tools;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.media.FaceDetector;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.luoyun.doutu.account.AvatarUpload;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 独立本地图片工具（对齐参考 App 的换头像 / 自动替换 / AI 一键模仿链路）：
 *  - avatar：底图 + 头像 → 检测人脸槽位 → 替换 → 预览 → 保存
 *  - auto：底图 + 头像 → 检测全部人脸 → 批量替换（可选水印）→ 保存
 *  - ai：一张或多张图片 → 本地风格化（海报/黑白/暖色/冷色）→ 批量保存
 * 全部本机处理，不访问网络、不调用本软件其它模块。
 */
public final class ToolImageActivity extends Activity {
    private static final int REQ_BASE = 51, REQ_AVATAR = 52, REQ_AI = 53, REQ_CROP = 54;
    private static final int MODE_AVATAR = 0, MODE_AUTO = 1, MODE_AI = 2;

    private int mode;
    private Bitmap base, avatar;
    private final List<Bitmap> sources = new ArrayList<>();
    private final List<Bitmap> results = new ArrayList<>();
    private ImageView basePreview, avatarPreview, preview;
    private TextView hint;
    private CheckBox watermark, replaceAvatar;
    private LinearLayout aiSources, aiResults;
    private TextView aiResultsTitle;
    private LinearLayout slotList;
    private final List<RectF> slots = new ArrayList<>();
    private final List<Boolean> slotEnabled = new ArrayList<>();
    private Bitmap workBase;
    private boolean slotsShown = false, synthesized = false;
    private LinearLayout styleBar;
    private int style = 0;
    private Button processBtn, saveBtn;

    public static void open(Activity activity, String mode) {
        Intent intent = new Intent(activity, ToolImageActivity.class);
        intent.putExtra("mode", mode);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String m = getIntent().getStringExtra("mode");
        mode = "ai".equals(m) ? MODE_AI : "auto".equals(m) ? MODE_AUTO : MODE_AVATAR;
        String title = mode == MODE_AI ? "AI 一键模仿" : mode == MODE_AUTO ? "自动替换头像" : "换头像";

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f6fa);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.WHITE);
        Button back = new Button(this);
        back.setText("‹ 返回");
        back.setAllCaps(false);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(88), dp(50)));
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(18);
        tv.setGravity(Gravity.CENTER);
        bar.addView(tv, new LinearLayout.LayoutParams(0, dp(50), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(88), dp(50)));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(20));

        hint = new TextView(this);
        hint.setTextSize(13);
        hint.setTextColor(0xff8b90a0);
        hint.setPadding(dp(2), 0, dp(2), dp(12));
        col.addView(hint);

        if (mode == MODE_AI) {
            Button pickAll = new Button(this);
            pickAll.setText("选择图片（可多选，最多 9 张）");
            pickAll.setAllCaps(false);
            pickAll.setOnClickListener(v -> pickMulti());
            col.addView(pickAll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            aiSources = new LinearLayout(this);
            aiSources.setOrientation(LinearLayout.HORIZONTAL);
            aiSources.setPadding(0, dp(6), 0, dp(6));
            col.addView(aiSources);
            avatarPreview = addPickRow(col, "头像图片（用于替换）", () -> pickImage(REQ_AVATAR, false));
            replaceAvatar = new CheckBox(this);
            replaceAvatar.setText("替换头像（请确保头像完整性）");
            replaceAvatar.setTextSize(13);
            replaceAvatar.setChecked(true);
            col.addView(replaceAvatar);
            hint.setText("参考 AI一键模仿：选多张图 + 头像，自动识别图中的头像槽位并批量替换，生成后可一键保存以上图片。");
        } else {
            basePreview = addPickRow(col, "底图（聊天截图 / 任意图片）", () -> pickImage(REQ_BASE, false));
            avatarPreview = addPickRow(col, "头像图片", () -> pickImage(REQ_AVATAR, false));
            Button cropBtn = new Button(this);
            cropBtn.setText("裁剪底图（可选）");
            cropBtn.setAllCaps(false);
            cropBtn.setOnClickListener(v -> openCrop());
            col.addView(cropBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
            watermark = new CheckBox(this);
            watermark.setText("保存时添加水印");
            watermark.setTextSize(13);
            watermark.setVisibility(mode == MODE_AUTO ? View.VISIBLE : View.GONE);
            col.addView(watermark);
            hint.setText(mode == MODE_AUTO
                    ? "选底图和头像后点「开始替换」，会检测全部人脸并批量替换。"
                    : "选底图和头像后点「开始替换」，会检测最大的人脸并替换。");
        }

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.WHITE);
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360));
        flp.topMargin = dp(12);
        col.addView(frame, flp);

        aiResultsTitle = new TextView(this);
        aiResultsTitle.setText("生成结果");
        aiResultsTitle.setTextSize(14);
        aiResultsTitle.setTextColor(0xff1d1f26);
        aiResultsTitle.setVisibility(View.GONE);
        col.addView(aiResultsTitle);
        aiResults = new LinearLayout(this);
        aiResults.setOrientation(LinearLayout.HORIZONTAL);
        col.addView(aiResults);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, dp(12), 0, 0);
        processBtn = new Button(this);
        processBtn.setText(mode == MODE_AI ? "立即生成" : "开始替换");
        processBtn.setAllCaps(false);
        processBtn.setOnClickListener(v -> handleProcess());
        actions.addView(processBtn, new LinearLayout.LayoutParams(0, dp(50), 1f));
        saveBtn = new Button(this);
        saveBtn.setText(mode == MODE_AI ? "一键保存以上图片" : "保存到相册");
        saveBtn.setAllCaps(false);
        saveBtn.setEnabled(false);
        saveBtn.setOnClickListener(v -> saveAll());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        slp.leftMargin = dp(10);
        actions.addView(saveBtn, slp);

        scroll.addView(col);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(actions);

        setContentView(root);
        if (getIntent().getBooleanExtra("selftest", false)) { finish(); return; }
        if (mode == MODE_AI) pickMulti();
    }

    private ImageView addPickRow(LinearLayout parent, String label, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.WHITE);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(14);
        tv.setTextColor(0xff1d1f26);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(0xffeef0f6);
        row.addView(thumb, new LinearLayout.LayoutParams(dp(52), dp(52)));
        Button pick = new Button(this);
        pick.setText("选择");
        pick.setAllCaps(false);
        pick.setTextSize(13);
        pick.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(76), dp(46));
        plp.leftMargin = dp(8);
        row.addView(pick, plp);
        parent.addView(row);
        return thumb;
    }

    private void pickImage(int request, boolean multi) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        if (multi) intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择图片"), request);
        } catch (Exception e) {
            toast("没有可用的图片选择器");
        }
    }

    private void pickMulti() {
        pickImage(REQ_AI, true);
    }

    private void openCrop() {
        if (base == null) { toast("请先选择底图"); return; }
        CropImageActivity.source = base;
        startActivityForResult(new Intent(this, CropImageActivity.class), REQ_CROP);
    }

    private void resetSlotState() {
        slots.clear();
        slotEnabled.clear();
        slotsShown = false;
        synthesized = false;
        workBase = null;
        if (slotList != null) slotList.removeAllViews();
        if (processBtn != null) processBtn.setText("开始替换");
        if (saveBtn != null) saveBtn.setEnabled(false);
        if (preview != null) preview.setImageBitmap(null);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQ_CROP) {
            if (result == RESULT_OK && CropImageActivity.result != null) {
                base = CropImageActivity.result;
                if (basePreview != null) basePreview.setImageBitmap(base);
                resetSlotState();
                toast("已裁剪底图");
            }
            return;
        }
        if (result != RESULT_OK || data == null) return;
        if (request == REQ_AI) {
            sources.clear();
            if (data.getClipData() != null) {
                ClipData clip = data.getClipData();
                for (int i = 0; i < Math.min(clip.getItemCount(), 9); i++) addSource(clip.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                addSource(data.getData());
            }
            hint.setText("已选择 " + sources.size() + " 张图片，点「立即生成」。");
            renderAiSources();
        } else if (request == REQ_BASE || request == REQ_AVATAR) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                Bitmap bmp = AvatarUpload.decode(this, uri);
                if (request == REQ_BASE) { base = bmp; basePreview.setImageBitmap(bmp); }
                else { avatar = bmp; avatarPreview.setImageBitmap(bmp); }
            } catch (Exception e) {
                toast("图片读取失败");
            }
        }
    }

    private void addSource(Uri uri) {
        try {
            Bitmap bmp = AvatarUpload.decode(this, uri);
            if (bmp != null) sources.add(bmp);
        } catch (Exception ignored) { }
    }

    private void handleProcess() {
        if (mode == MODE_AI) {
            process();
            return;
        }
        if (base == null || avatar == null) { toast("请先选择底图和头像"); return; }
        ReplaceHeaderListActivity.base = base;
        ReplaceHeaderListActivity.avatar = avatar;
        ReplaceHeaderListActivity.watermark = mode == MODE_AUTO && watermark != null && watermark.isChecked();
        startActivity(new Intent(this, ReplaceHeaderListActivity.class));
    }

    private void detectSlots() {
        if (base == null || avatar == null) { toast("请先选择底图和头像"); return; }
        int max = 1600;
        float scale = Math.min(1f, (float) max / Math.max(base.getWidth(), base.getHeight()));
        int w = Math.max(1, Math.round(base.getWidth() * scale));
        int h = Math.max(1, Math.round(base.getHeight() * scale));
        workBase = Bitmap.createScaledBitmap(base, w, h, true);
        slots.clear();
        slotEnabled.clear();
        List<RectF> found = detectFaces(workBase);
        slots.addAll(found);
        for (int i = 0; i < slots.size(); i++) slotEnabled.add(true);
        if (slots.isEmpty()) {
            addManualSlot();
            return;
        }
        slotsShown = true;
        processBtn.setText("人工智能一键合成");
        renderSlots();
        toast("识别到 " + slots.size() + " 个头像，可逐项开关");
    }

    private void addManualSlot() {
        if (workBase == null) { toast("请先选择底图和头像"); return; }
        int w = workBase.getWidth(), h = workBase.getHeight();
        slots.add(new RectF(w * 0.04f, h * 0.04f, w * 0.18f, h * 0.04f + w * 0.14f));
        slotEnabled.add(true);
        slotsShown = true;
        processBtn.setText("人工智能一键合成");
        renderSlots();
    }

    private void renderSlots() {
        if (slotList == null) return;
        slotList.removeAllViews();
        for (int i = 0; i < slots.size(); i++) {
            final int index = i;
            RectF r = slots.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(Color.WHITE);
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);
            ImageView old = new ImageView(this);
            old.setScaleType(ImageView.ScaleType.CENTER_CROP);
            old.setImageBitmap(cropSlot(workBase, r));
            row.addView(old, new LinearLayout.LayoutParams(dp(56), dp(56)));
            TextView arrow = new TextView(this);
            arrow.setText("→");
            arrow.setTextSize(16);
            arrow.setGravity(Gravity.CENTER);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(56)));
            ImageView now = new ImageView(this);
            now.setScaleType(ImageView.ScaleType.CENTER_CROP);
            now.setImageBitmap(avatar);
            row.addView(now, new LinearLayout.LayoutParams(dp(56), dp(56)));
            CheckBox cb = new CheckBox(this);
            cb.setText("替换");
            cb.setTextSize(13);
            cb.setChecked(slotEnabled.get(index));
            cb.setOnCheckedChangeListener((b, checked) -> slotEnabled.set(index, checked));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            clp.leftMargin = dp(8);
            row.addView(cb, clp);
            Button del = new Button(this);
            del.setText("删除");
            del.setAllCaps(false);
            del.setTextSize(12);
            del.setOnClickListener(v -> {
                if (index < slots.size()) {
                    slots.remove(index);
                    slotEnabled.remove(index);
                    renderSlots();
                }
            });
            row.addView(del, new LinearLayout.LayoutParams(dp(64), dp(40)));
            slotList.addView(row);
        }
    }

    private Bitmap cropSlot(Bitmap src, RectF r) {
        try {
            int x = Math.max(0, (int) r.left);
            int y = Math.max(0, (int) r.top);
            int w = Math.min(src.getWidth() - x, Math.max(1, (int) r.width()));
            int h = Math.min(src.getHeight() - y, Math.max(1, (int) r.height()));
            return Bitmap.createBitmap(src, x, y, w, h);
        } catch (Exception e) {
            return null;
        }
    }

    private void synthesize() {
        if (workBase == null || avatar == null) { toast("请先选择底图和头像"); return; }
        try {
            Bitmap out = workBase.copy(Bitmap.Config.ARGB_8888, true);
            Canvas c = new Canvas(out);
            for (int i = 0; i < slots.size() && i < slotEnabled.size(); i++) {
                if (Boolean.TRUE.equals(slotEnabled.get(i))) drawAvatar(c, avatar, slots.get(i));
            }
            if (mode == MODE_AUTO && watermark.isChecked()) drawWatermark(out);
            results.clear();
            results.add(out);
            preview.setImageBitmap(out);
            saveBtn.setEnabled(true);
            synthesized = true;
            processBtn.setText("返回修改");
            if (slotList != null) slotList.removeAllViews();
            toast("合成完成，可返回修改或保存");
        } catch (Exception e) {
            toast("合成失败：" + e.getMessage());
        }
    }


    private void renderAiSources() {
        if (aiSources == null) return;
        aiSources.removeAllViews();
        for (int i = 0; i < sources.size(); i++) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(sources.get(i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(64));
            lp.rightMargin = dp(6);
            aiSources.addView(iv, lp);
        }
    }

    private void renderAiResults() {
        if (aiResults == null) return;
        aiResultsTitle.setVisibility(View.VISIBLE);
        aiResults.removeAllViews();
        for (int i = 0; i < results.size(); i++) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(results.get(i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), dp(72));
            lp.rightMargin = dp(6);
            aiResults.addView(iv, lp);
        }
    }

    private void process() {
        results.clear();
        try {
            for (Bitmap src : sources) {
                if (replaceAvatar != null && replaceAvatar.isChecked() && avatar != null) {
                    Bitmap done = replaceFaces(src, avatar, true);
                    results.add(done != null ? done : src.copy(Bitmap.Config.ARGB_8888, true));
                } else {
                    results.add(src.copy(Bitmap.Config.ARGB_8888, true));
                }
            }
            preview.setImageBitmap(results.get(0));
            saveBtn.setEnabled(true);
            toast("已生成 " + results.size() + " 张，点「一键保存以上图片」");
            renderAiResults();
        } catch (Exception e) {
            toast("处理失败：" + e.getMessage());
        }
    }

    private Bitmap replaceFaces(Bitmap src, Bitmap avatarBmp, boolean all) {
        int max = 1600;
        float scale = Math.min(1f, (float) max / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, Math.round(src.getWidth() * scale)), h = Math.max(1, Math.round(src.getHeight() * scale));
        Bitmap work = Bitmap.createScaledBitmap(src, w, h, true);
        Bitmap out = work.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(out);
        List<RectF> slots = detectFaces(work);
        if (slots.isEmpty()) {
            slots.add(new RectF(w * 0.04f, h * 0.04f, w * 0.18f, h * 0.04f + w * 0.14f));
        }
        for (int i = 0; i < slots.size(); i++) {
            if (!all && i > 0) break;
            drawAvatar(c, avatarBmp, slots.get(i));
        }
        return out;
    }

    private List<RectF> detectFaces(Bitmap src) {
        List<RectF> out = new ArrayList<>();
        try {
            Bitmap rgb = src.getConfig() == Bitmap.Config.RGB_565 ? src : src.copy(Bitmap.Config.RGB_565, false);
            FaceDetector detector = new FaceDetector(rgb.getWidth(), rgb.getHeight(), 8);
            FaceDetector.Face[] faces = new FaceDetector.Face[8];
            int n = detector.findFaces(rgb, faces);
            for (int i = 0; i < n; i++) {
                FaceDetector.Face f = faces[i];
                if (f == null) continue;
                PointF mid = new PointF();
                f.getMidPoint(mid);
                float eyes = Math.max(6f, f.eyesDistance());
                float cx = mid.x, cy = mid.y + eyes * .25f;
                float r = eyes * 1.65f;
                out.add(new RectF(cx - r, cy - r, cx + r, cy + r));
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private void drawAvatar(Canvas c, Bitmap avatarBmp, RectF slot) {
        float w = slot.width(), h = slot.height();
        int side = Math.min(avatarBmp.getWidth(), avatarBmp.getHeight());
        int sx = (avatarBmp.getWidth() - side) / 2, sy = (avatarBmp.getHeight() - side) / 2;
        Bitmap sq = Bitmap.createBitmap(avatarBmp, sx, sy, side, side);
        Bitmap scaled = Bitmap.createScaledBitmap(sq, Math.max(1, (int) w), Math.max(1, (int) h), true);
        Path clip = new Path();
        clip.addCircle(slot.centerX(), slot.centerY(), Math.min(w, h) / 2f, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        c.drawBitmap(scaled, slot.left, slot.top, null);
        c.restore();
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Math.max(2f, w * .035f));
        ring.setColor(Color.WHITE);
        c.drawCircle(slot.centerX(), slot.centerY(), Math.min(w, h) / 2f, ring);
    }

    private void drawWatermark(Bitmap bmp) {
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x66ffffff);
        p.setTextSize(Math.max(18f, bmp.getWidth() * .028f));
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("豆图工具 · 本地处理", bmp.getWidth() - 16, bmp.getHeight() - 16, p);
    }

    private void saveAll() {
        if (results.isEmpty()) { toast("请先生成"); return; }
        int ok = 0;
        for (Bitmap bmp : results) {
            try {
                MediaStore.Images.Media.insertImage(getContentResolver(), bmp, "doutu_tool_" + System.currentTimeMillis(), "doutu tools");
                if (mode != MODE_AI) saveLocal(bmp);
                ok++;
            } catch (Exception ignored) { }
        }
        toast(ok > 0 ? "已保存 " + ok + " 张到相册" : "保存失败");
    }

    private void saveLocal(Bitmap bmp) {
        try {
            File dir = new File(getFilesDir(), "avatars");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "avatar_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(out);
            bmp.compress(Bitmap.CompressFormat.PNG, 95, fos);
            fos.close();
        } catch (Exception ignored) { }
    }

    private Bitmap styleize(Bitmap src, int kind) {
        int max = 1200;
        float scale = Math.min(1f, (float) max / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, Math.round(src.getWidth() * scale)), h = Math.max(1, Math.round(src.getHeight() * scale));
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int a = (px[i] >>> 24) & 0xff;
            int r = (px[i] >> 16) & 0xff, g = (px[i] >> 8) & 0xff, b = px[i] & 0xff;
            if (kind == 0) {
                int step = 51;
                r = Math.round((float) r / step) * step;
                g = Math.round((float) g / step) * step;
                b = Math.round((float) b / step) * step;
                r = clamp((r - 128) * 1.15f + 128);
                g = clamp((g - 128) * 1.15f + 128);
                b = clamp((b - 128) * 1.15f + 128);
            } else if (kind == 1) {
                int v = clamp(r * .299f + g * .587f + b * .114f);
                r = g = b = v;
            } else if (kind == 2) {
                r = clamp(r * 1.12f + 12);
                g = clamp(g * 1.04f + 4);
                b = clamp(b * .88f);
            } else {
                r = clamp(r * .88f);
                g = clamp(g * .98f);
                b = clamp(b * 1.14f + 10);
            }
            px[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
