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
    /** 调整页「完成」时框选的头像区域；为空则自动检测人脸。 */
    public static RectF initialSlot;
    private static final int REQ_AVATAR = 71;

    private final List<RectF> slots = new ArrayList<>();
    private final List<Boolean> enabled = new ArrayList<>();
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (base == null) { finish(); return; }
        if (initialSlot != null) {
            slots.add(new RectF(initialSlot));
            enabled.add(true);
            initialSlot = null;
        } else {
            detect();
        }
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
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(54), dp(54));
            nlp.leftMargin = dp(20);
            if (avatar != null) {
                ImageView now = new ImageView(this);
                now.setScaleType(ImageView.ScaleType.CENTER_CROP);
                now.setImageBitmap(avatar);
                now.setOnClickListener(v -> pickAvatar());
                row.addView(now, nlp);
            } else {
                TextView pick = new TextView(this);
                pick.setText("＋\n相册选择");
                pick.setTextSize(10);
                pick.setTextColor(0xff888888);
                pick.setGravity(Gravity.CENTER);
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(0xfff2f2f2);
                bg.setCornerRadius(dp(4));
                pick.setBackground(bg);
                pick.setOnClickListener(v -> pickAvatar());
                row.addView(pick, nlp);
            }
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

    private void pickAvatar() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("image/*");
        startActivityForResult(Intent.createChooser(pick, "选择替换头像"), REQ_AVATAR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_AVATAR || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
            avatar = android.graphics.BitmapFactory.decodeStream(in);
            render();
        } catch (Exception e) {
            Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show();
        }
    }

    /** 头像中正方形裁切，避免拉伸。 */
    private Bitmap squareCover(Bitmap src) {
        int side = Math.min(src.getWidth(), src.getHeight());
        return Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
    }

    /** 圆角方形替换：圆角半径取边长 20%，并外扩 3% 盖住原头像边缘。 */
    private void drawReplacement(Canvas c, Paint paint, Bitmap cover, RectF slot) {
        RectF r = new RectF(slot);
        float grow = Math.min(r.width(), r.height()) * 0.03f;
        r.inset(-grow, -grow);
        float radius = Math.min(r.width(), r.height()) * 0.16f;
        Path path = new Path();
        path.addRoundRect(r, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(path);
        // 先铺白底：替换图带透明像素时不会透出原来的头像
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xffffffff);
        c.drawRect(r, bg);
        c.drawBitmap(cover, null, r, paint);
        c.restore();
    }

    private java.util.List<RectF> dedupe(java.util.List<RectF> list) {
        java.util.List<RectF> out = new java.util.ArrayList<>();
        for (RectF r : list) {
            boolean dup = false;
            for (RectF o : out) {
                if (RectF.intersects(r, o)) { dup = true; break; }
            }
            if (!dup) out.add(r);
        }
        return out;
    }

    /** 采样成 12×12 灰度网格，用于归一化互相关（对压缩/亮度差异更耐受）。 */
    private float[] grayGrid(int[] px, int tw, int th) {
        final int g = 12;
        float[] out = new float[g * g];
        for (int gy = 0; gy < g; gy++) {
            int sy = Math.min(th - 1, gy * th / g);
            for (int gx = 0; gx < g; gx++) {
                int sx = Math.min(tw - 1, gx * tw / g);
                int c = px[sy * tw + sx];
                out[gy * g + gx] = 0.299f * ((c >> 16) & 255)
                        + 0.587f * ((c >> 8) & 255) + 0.114f * (c & 255);
            }
        }
        return out;
    }

    /** 归一化互相关，-1~1；1 表示完全一致。 */
    private float ncc(float[] a, float[] b) {
        float ma = 0f, mb = 0f;
        for (int i = 0; i < a.length; i++) {
            ma += a[i];
            mb += b[i];
        }
        ma /= a.length;
        mb /= b.length;
        double num = 0, da = 0, db = 0;
        for (int i = 0; i < a.length; i++) {
            double x = a[i] - ma, y = b[i] - mb;
            num += x * y;
            da += x * x;
            db += y * y;
        }
        double den = Math.sqrt(da * db);
        return den < 1e-3 ? 0f : (float) (num / den);
    }

    /**
     * 以 ref 选框为模板，用归一化互相关找出整张图里所有相同头像。
     * 多个尺度（0.9 / 1.0 / 1.1）扫描，命中后映射回原图坐标。
     */
    private java.util.List<RectF> findSameAvatars(RectF ref) {
        java.util.List<RectF> found = new java.util.ArrayList<>();
        if (base == null || ref.width() < 10 || ref.height() < 10) return found;
        float inset = 0.10f;   // 内缩，避开选框边缘的背景
        RectF core = new RectF(ref);
        core.inset(ref.width() * inset, ref.height() * inset);
        float k = Math.min(1f, 420f / base.getWidth());
        Bitmap work = Bitmap.createScaledBitmap(base,
                Math.max(1, Math.round(base.getWidth() * k)),
                Math.max(1, Math.round(base.getHeight() * k)), true);
        final float threshold = 0.86f;
        float[] scales = {1.0f, 0.9f, 1.1f};
        java.util.List<int[]> hits = new java.util.ArrayList<>();
        for (float sc : scales) {
            int tw = Math.max(6, Math.round(core.width() * k * sc));
            int th = Math.max(6, Math.round(core.height() * k * sc));
            if (tw >= work.getWidth() || th >= work.getHeight()) continue;
            int tx = Math.max(0, Math.min(work.getWidth() - tw, Math.round(core.left * k)));
            int ty = Math.max(0, Math.min(work.getHeight() - th, Math.round(core.top * k)));
            int[] tpl = new int[tw * th];
            try {
                work.getPixels(tpl, 0, tw, tx, ty, tw, th);
            } catch (Exception e) {
                continue;
            }
            float[] tplGrid = grayGrid(tpl, tw, th);
            int stride = 3;
            int[] buf = new int[tw * th];
            for (int y = 0; y + th <= work.getHeight(); y += stride) {
                for (int x = 0; x + tw <= work.getWidth(); x += stride) {
                    work.getPixels(buf, 0, tw, x, y, tw, th);
                    float score = ncc(tplGrid, grayGrid(buf, tw, th));
                    if (score >= threshold) {
                        hits.add(new int[]{x, y, Math.round((1f - score) * 1000),
                                Math.round(tw / sc), Math.round(th / sc)});
                    }
                }
            }
        }
        hits.sort((a, b) -> Integer.compare(a[2], b[2]));
        java.util.List<RectF> picked = new java.util.ArrayList<>();
        for (int[] h : hits) {
            if (picked.size() >= 20) break;
            float cx = h[0] + h[3] * k / 2f, cy = h[1] + h[4] * k / 2f;
            float fw = h[3] * k / (1f - inset * 2f), fh = h[4] * k / (1f - inset * 2f);
            RectF r = new RectF((cx - fw / 2f) / k, (cy - fh / 2f) / k,
                    (cx + fw / 2f) / k, (cy + fh / 2f) / k);
            boolean dup = false;
            for (RectF o : picked) {
                if (RectF.intersects(r, o)) { dup = true; break; }
            }
            if (!dup) picked.add(r);
        }
        found.addAll(picked);
        return found;
    }

    /** 平均色差（0~255）；超过 limit 立刻返回 -1，省时间。 */
    private float meanDiff(int[] a, int[] b, float limit) {
        long sum = 0;
        int n = 0;
        for (int i = 0; i < a.length; i += 2) {
            int ca = a[i], cb = b[i];
            sum += Math.abs(((ca >> 16) & 255) - ((cb >> 16) & 255))
                    + Math.abs(((ca >> 8) & 255) - ((cb >> 8) & 255))
                    + Math.abs((ca & 255) - (cb & 255));
            n += 3;
            if (n > 0 && sum / (float) n > limit) return -1f;
        }
        return sum / (float) Math.max(1, n);
    }

    private void compose() {
        if (avatar == null) {
            Toast.makeText(this, "请先选择替换头像", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Bitmap out = base.copy(Bitmap.Config.ARGB_8888, true);
            Canvas c = new Canvas(out);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setFilterBitmap(true);
            Bitmap cover = squareCover(avatar);
            java.util.List<RectF> targets = new java.util.ArrayList<>();
            if (!slots.isEmpty()) {
                targets.add(new RectF(slots.get(0)));
                targets.addAll(findSameAvatars(slots.get(0)));
            }
            for (RectF slot : dedupe(targets)) {
                drawReplacement(c, paint, cover, slot);
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
