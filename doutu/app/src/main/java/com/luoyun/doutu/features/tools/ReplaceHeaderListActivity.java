package com.luoyun.doutu.features.tools;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.media.FaceDetector;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 对齐参考 activity_replace_header_list：
 * 标题栏 → 「选择需要替换的头像」提示 → 「原始头像 / 替换头像」表头 →
 * 逐行 70dp 行（50dp 原图、替换成、50dp 新图、删除）→
 * 「替换截图内其他头像」→ 底部「人工智能一键合成」。
 */
public final class ReplaceHeaderListActivity extends Activity {
    public static Bitmap base, avatar;
    public static boolean watermark;

    private final List<RectF> slots = new ArrayList<>();
    private final List<Boolean> enabled = new ArrayList<>();
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (base == null || avatar == null) { finish(); return; }
        detect();
        buildUi();
    }

    private void detect() {
        int max = 1600;
        float scale = Math.min(1f, (float) max / Math.max(base.getWidth(), base.getHeight()));
        int w = Math.max(1, Math.round(base.getWidth() * scale));
        int h = Math.max(1, Math.round(base.getHeight() * scale));
        Bitmap work = Bitmap.createScaledBitmap(base, w, h, true);
        try {
            Bitmap rgb = work.getConfig() == Bitmap.Config.RGB_565 ? work : work.copy(Bitmap.Config.RGB_565, false);
            FaceDetector detector = new FaceDetector(rgb.getWidth(), rgb.getHeight(), 8);
            FaceDetector.Face[] faces = new FaceDetector.Face[8];
            int n = detector.findFaces(rgb, faces);
            for (int i = 0; i < n; i++) {
                FaceDetector.Face f = faces[i];
                if (f == null) continue;
                PointF mid = new PointF();
                f.getMidPoint(mid);
                float eyes = Math.max(6f, f.eyesDistance());
                float cx = mid.x, cy = mid.y + eyes * .25f, r = eyes * 1.65f;
                slots.add(new RectF(cx - r, cy - r, cx + r, cy + r));
                enabled.add(true);
            }
        } catch (Throwable ignored) { }
        if (slots.isEmpty()) {
            slots.add(new RectF(w * 0.04f, h * 0.04f, w * 0.18f, h * 0.04f + w * 0.14f));
            enabled.add(true);
        }
        base = Bitmap.createScaledBitmap(base, w, h, true);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff7f7f7);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.WHITE);
        Button back = new Button(this);
        back.setText("‹ 返回");
        back.setAllCaps(false);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(88), dp(50)));
        TextView title = new TextView(this);
        title.setText("换头像");
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(88), dp(50)));
        root.addView(bar);

        TextView tip = new TextView(this);
        tip.setText("选择需要替换的头像");
        tip.setTextSize(13);
        tip.setTextColor(0xff666666);
        tip.setGravity(Gravity.CENTER_VERTICAL);
        tip.setPadding(dp(15), 0, 0, 0);
        root.addView(tip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(45)));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(Color.WHITE);
        TextView oldT = new TextView(this);
        oldT.setText("原始头像");
        oldT.setTextSize(14);
        oldT.setTextColor(0xff333333);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.leftMargin = dp(20);
        head.addView(oldT, olp);
        TextView newT = new TextView(this);
        newT.setText("替换头像");
        newT.setTextSize(14);
        newT.setTextColor(0xff333333);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.leftMargin = dp(99);
        head.addView(newT, nlp);
        root.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        View line = new View(this);
        line.setBackgroundColor(0xffeeeeee);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        llp.leftMargin = dp(20);
        llp.rightMargin = dp(20);
        root.addView(line, llp);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(Color.WHITE);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button add = new Button(this);
        add.setText("替换截图内其他头像");
        add.setAllCaps(false);
        add.setTextSize(14);
        add.setOnClickListener(v -> {
            int w = base.getWidth(), h = base.getHeight();
            slots.add(new RectF(w * 0.04f, h * 0.04f, w * 0.18f, h * 0.04f + w * 0.14f));
            enabled.add(true);
            render();
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(176), dp(40));
        alp.gravity = Gravity.CENTER_HORIZONTAL;
        alp.topMargin = dp(20);
        root.addView(add, alp);

        Button compose = new Button(this);
        compose.setText("人工智能一键合成");
        compose.setAllCaps(false);
        compose.setTextSize(14);
        compose.setOnClickListener(v -> compose());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        clp.topMargin = dp(16);
        root.addView(compose, clp);

        setContentView(root);
        render();
    }

    private void render() {
        list.removeAllViews();
        for (int i = 0; i < slots.size(); i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70));
            row.setLayoutParams(rlp);
            ImageView old = new ImageView(this);
            old.setScaleType(ImageView.ScaleType.CENTER_CROP);
            old.setImageBitmap(crop(slots.get(i)));
            LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(dp(50), dp(50));
            olp.leftMargin = dp(23);
            row.addView(old, olp);
            TextView mid = new TextView(this);
            mid.setText("替换成");
            mid.setTextSize(13);
            mid.setTextColor(0xff333333);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mlp.leftMargin = dp(30);
            row.addView(mid, mlp);
            ImageView now = new ImageView(this);
            now.setScaleType(ImageView.ScaleType.CENTER_CROP);
            now.setImageBitmap(avatar);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(50), dp(50));
            nlp.leftMargin = dp(20);
            row.addView(now, nlp);
            View spacer = new View(this);
            row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
            Button del = new Button(this);
            del.setText("删除");
            del.setAllCaps(false);
            del.setTextSize(12);
            del.setOnClickListener(v -> {
                if (idx < slots.size()) {
                    slots.remove(idx);
                    enabled.remove(idx);
                    render();
                }
            });
            row.addView(del, new LinearLayout.LayoutParams(dp(64), dp(40)));
            list.addView(row);
        }
    }

    private Bitmap crop(RectF r) {
        try {
            int x = Math.max(0, (int) r.left);
            int y = Math.max(0, (int) r.top);
            int w = Math.min(base.getWidth() - x, Math.max(1, (int) r.width()));
            int h = Math.min(base.getHeight() - y, Math.max(1, (int) r.height()));
            return Bitmap.createBitmap(base, x, y, w, h);
        } catch (Exception e) {
            return null;
        }
    }

    private void compose() {
        try {
            Bitmap out = base.copy(Bitmap.Config.ARGB_8888, true);
            Canvas c = new Canvas(out);
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            for (int i = 0; i < slots.size() && i < enabled.size(); i++) {
                if (!Boolean.TRUE.equals(enabled.get(i))) continue;
                RectF slot = slots.get(i);
                int side = Math.min(avatar.getWidth(), avatar.getHeight());
                Bitmap sq = Bitmap.createBitmap(avatar, (avatar.getWidth() - side) / 2, (avatar.getHeight() - side) / 2, side, side);
                Bitmap scaled = Bitmap.createScaledBitmap(sq, Math.max(1, (int) slot.width()), Math.max(1, (int) slot.height()), true);
                Path clip = new Path();
                clip.addCircle(slot.centerX(), slot.centerY(), Math.min(slot.width(), slot.height()) / 2f, Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                c.drawBitmap(scaled, slot.left, slot.top, null);
                c.restore();
                ring.setStyle(Paint.Style.STROKE);
                ring.setStrokeWidth(Math.max(2f, slot.width() * .035f));
                ring.setColor(Color.WHITE);
                c.drawCircle(slot.centerX(), slot.centerY(), Math.min(slot.width(), slot.height()) / 2f, ring);
            }
            if (watermark) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(0x66ffffff);
                p.setTextSize(Math.max(18f, out.getWidth() * .028f));
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText("豆图工具 · 本地处理", out.getWidth() - 16, out.getHeight() - 16, p);
            }
            ReplacePreviewActivity.image = out;
            startActivity(new Intent(this, ReplacePreviewActivity.class));
        } catch (Exception e) {
            Toast.makeText(this, "合成失败", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
