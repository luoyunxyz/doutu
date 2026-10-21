package com.luoyun.doutu.features.tools;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * 「换头像 → 调整选区」页 —— 按真机布局 fragment_tool_replace_shot.xml 重画：
 * 黄色标题栏（返回 / ✔完成）、黑底照片 + 黄色虚线圆角选框，底部是
 * 放大/缩小、方向键、按住预览/切换颜色、圆角弧度滑杆、还原。
 */
public final class AvatarAdjustActivity extends Activity {
    /** 由 ToolImageActivity 放进来的截图与返回的选框。 */
    public static Bitmap source;
    public static RectF resultRect;

    private ClipView clip;
    private int radiusDp = 24;
    private int colorIndex = 0;
    private final int[] colors = {0xffffd400, 0xffffffff, 0xff4cd964, 0xffff3b30};

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xfff9db61);
        w.setNavigationBarColor(Color.BLACK);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        clip = new ClipView();
        root.addView(clip, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 底部控件
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(12), 0, dp(12), dp(18));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.BOTTOM);
        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.addView(pill("放大", v -> clip.zoom(1.1f)));
        TextView reduce = pill("缩小", v -> clip.zoom(0.9f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(10);
        leftCol.addView(reduce, rlp);
        actions.addView(leftCol, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setGravity(Gravity.CENTER_HORIZONTAL);
        pad.setPadding(dp(30), 0, dp(30), 0);
        TextView up = arrow("⌃", v -> clip.move(0, -dp(10)));
        pad.addView(up, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout mid = new LinearLayout(this);
        mid.setGravity(Gravity.CENTER);
        mid.addView(arrow("‹", v -> clip.move(-dp(10), 0)), new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView down = arrow("⌄", v -> clip.move(0, dp(10)));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(34), dp(34));
        dlp.leftMargin = dp(14);
        dlp.rightMargin = dp(14);
        mid.addView(down, dlp);
        mid.addView(arrow("›", v -> clip.move(dp(10), 0)), new LinearLayout.LayoutParams(dp(34), dp(34)));
        pad.addView(mid);
        actions.addView(pad, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        TextView preview = pill("按住预览", null);
        preview.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) { clip.setPreview(true); return true; }
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                clip.setPreview(false);
                return true;
            }
            return false;
        });
        rightCol.addView(preview);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(10);
        rightCol.addView(pill("切换颜色", v -> clip.cycleColor()), clp);
        actions.addView(rightCol, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bottom.addView(actions);

        // 圆角弧度滑杆
        LinearLayout seekRow = new LinearLayout(this);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        seekLp.topMargin = dp(14);
        seekRow.addView(seekIcon("‹"), new LinearLayout.LayoutParams(dp(28), dp(28)));
        SeekBar bar = new SeekBar(this);
        bar.setMax(180);
        bar.setProgress(24);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                radiusDp = progress;
                clip.setRadius(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.leftMargin = dp(10);
        blp.rightMargin = dp(10);
        seekRow.addView(bar, blp);
        seekRow.addView(seekIcon("›"), new LinearLayout.LayoutParams(dp(28), dp(28)));
        bottom.addView(seekRow, seekLp);

        // 当前角度：N + 还原（和真机一样在同一行）
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = dp(8);
        final TextView angleLabel = new TextView(this);
        angleLabel.setText("当前角度：24");
        angleLabel.setTextSize(15);
        angleLabel.setTextColor(Color.WHITE);
        footer.addView(angleLabel);
        TextView reset = new TextView(this);
        reset.setText("还原");
        reset.setTextSize(15);
        reset.setTextColor(Color.WHITE);
        reset.setPadding(dp(16), dp(4), dp(16), dp(4));
        GradientDrawable resetBg = new GradientDrawable();
        resetBg.setColor(0xff2a2a2a);
        resetBg.setCornerRadius(dp(4));
        reset.setBackground(resetBg);
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resetLp.leftMargin = dp(10);
        footer.addView(reset, resetLp);
        reset.setOnClickListener(v -> {
            clip.reset();
            bar.setProgress(24);
            colorIndex = 0;
            clip.cycleColorTo(colors[0]);
        });
        bottom.addView(footer, flp);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                radiusDp = progress;
                clip.setRadius(progress);
                angleLabel.setText("当前角度：" + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        FrameLayout.LayoutParams blp2 = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp2.gravity = Gravity.BOTTOM;
        root.addView(bottom, blp2);

        // 顶部黄栏
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(0xfff9db61);
        top.setPadding(dp(10), 0, dp(10), 0);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(30);
        back.setTextColor(0xff333333);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView done = new TextView(this);
        done.setText("✔ 完成");
        done.setTextSize(17);
        done.setTextColor(0xff333333);
        done.setGravity(Gravity.CENTER);
        done.setPadding(dp(12), 0, dp(6), 0);
        done.setOnClickListener(v -> {
            resultRect = clip.boxInSource();
            setResult(RESULT_OK, new Intent());
            // 从「替换头像」入口进来时，完成接着进头像列表页（原始头像 → 替换头像 → 一键合成）
            if (getIntent().getBooleanExtra("chain", false)) {
                ReplaceHeaderListActivity.base = source;
                ReplaceHeaderListActivity.initialSlot = resultRect;
                ReplaceHeaderListActivity.avatar = null;
                startActivity(new Intent(this, ReplaceHeaderListActivity.class));
            }
            finish();
        });
        top.addView(done, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        root.addView(top, tlp);

        setContentView(root);
    }

    private TextView pill(String text, View.OnClickListener onClick) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(0xff666666);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xfffefefe);
        bg.setCornerRadius(dp(4));
        tv.setBackground(bg);
        if (onClick != null) tv.setOnClickListener(onClick);
        return tv;
    }

    private TextView arrow(String glyph, View.OnClickListener onClick) {
        TextView tv = new TextView(this);
        tv.setText(glyph);
        tv.setTextSize(16);
        tv.setTextColor(0xff666666);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xffd8d8d8);
        bg.setShape(GradientDrawable.OVAL);
        tv.setBackground(bg);
        tv.setOnClickListener(onClick);
        return tv;
    }

    private TextView seekIcon(String glyph) {
        TextView tv = new TextView(this);
        tv.setText(glyph);
        tv.setTextSize(18);
        tv.setTextColor(0xff666666);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xffeeeeee);
        bg.setShape(GradientDrawable.OVAL);
        tv.setBackground(bg);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** 照片 + 虚线圆角选框。 */
    private final class ClipView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF img = new RectF();
        private final Rect src = new Rect();
        private final RectF dst = new RectF();
        private final RectF box = new RectF();
        private float borderRadius = 24 * getResources().getDisplayMetrics().density;
        private int borderColor = colors[0];
        private boolean preview;
        private float downX, downY;
        private float lastDist;
        /** 底图的缩放与平移（单指拖动平移、双指缩放）。 */
        private float imgZoom = 1f, imgDx = 0f, imgDy = 0f;

        ClipView() {
            super(AvatarAdjustActivity.this);
            resetBox();
        }

        private boolean boxReady;

        void resetBox() {
            // 头像框固定为正方形：边长取图片短边的 55%，居中摆放
            RectF base;
            if (!img.isEmpty()) {
                base = new RectF(img);
            } else if (getWidth() > 0 && getHeight() > 0) {
                base = new RectF(0, 0, getWidth(), getHeight());
            } else {
                boxReady = false;   // 还没有尺寸，等 onDraw 里图片铺好后重算
                return;
            }
            float side = Math.min(base.width(), base.height()) * 0.55f;
            if (side < 1f) side = Math.min(base.width(), base.height());
            box.set(base.centerX() - side / 2, base.centerY() - side / 2,
                    base.centerX() + side / 2, base.centerY() + side / 2);
            boxReady = true;
            clamp();
            invalidate();
        }

        void reset() {
            imgZoom = 1f;
            imgDx = 0f;
            imgDy = 0f;
            resetBox();
            borderRadius = 24 * getResources().getDisplayMetrics().density;
            invalidate();
        }

        void zoom(float factor) {
            float w = box.width() * factor / 2, h = box.height() * factor / 2;
            float cx = box.centerX(), cy = box.centerY();
            box.set(cx - w, cy - h, cx + w, cy + h);
            clamp();
            invalidate();
        }

        void move(float dx, float dy) {
            box.offset(dx, dy);
            clamp();
            invalidate();
        }

        void setRadius(int dpRadius) {
            borderRadius = dpRadius * getResources().getDisplayMetrics().density / 2f;
            invalidate();
        }

        void cycleColor() {
            colorIndex = (colorIndex + 1) % colors.length;
            borderColor = colors[colorIndex];
            invalidate();
        }

        void cycleColorTo(int color) {
            borderColor = color;
            invalidate();
        }

        void setPreview(boolean on) {
            preview = on;
            invalidate();
        }

        RectF boxInImage() {
            return new RectF(box);
        }

        /** 选框换算到「原图像素坐标」，供后续替换头像使用。 */
        RectF boxInSource() {
            if (source == null || img.width() <= 0f || img.height() <= 0f) return new RectF(box);
            float sx = source.getWidth() / img.width();
            float sy = source.getHeight() / img.height();
            RectF r = new RectF(box);
            r.offset(-img.left, -img.top);
            r.left *= sx;
            r.right *= sx;
            r.top *= sy;
            r.bottom *= sy;
            return r;
        }

        private void clamp() {
            if (img.isEmpty()) return;
            if (box.left < img.left) box.offset(img.left - box.left, 0);
            if (box.top < img.top) box.offset(0, img.top - box.top);
            if (box.right > img.right) box.offset(img.right - box.right, 0);
            if (box.bottom > img.bottom) box.offset(0, img.bottom - box.bottom);
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawColor(Color.BLACK);
            if (source != null) {
                float areaTop = dp(56);
                float areaBottom = getHeight() - dp(300);
                float vw = getWidth(), vh = areaBottom - areaTop;
                float k = Math.min(vw / source.getWidth(), vh / source.getHeight());
                float fw = source.getWidth() * k, fh = source.getHeight() * k;
                RectF fit = new RectF((vw - fw) / 2, areaTop + (vh - fh) / 2,
                        (vw - fw) / 2 + fw, areaTop + (vh - fh) / 2 + fh);
                // 叠加拖动/双指缩放
                float cx = fit.centerX() + imgDx, cy = fit.centerY() + imgDy;
                float hw = fit.width() * imgZoom / 2f, hh = fit.height() * imgZoom / 2f;
                img.set(cx - hw, cy - hh, cx + hw, cy + hh);
                if (!boxReady || box.isEmpty()) {
                    resetBox();
                } else {
                    clampImage();
                }
                src.set(0, 0, source.getWidth(), source.getHeight());
                dst.set(img);
                p.setFilterBitmap(true);
                c.drawBitmap(source, src, dst, p);
            }

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            p.setColor(borderColor);
            p.setPathEffect(new DashPathEffect(new float[]{dp(5), dp(4)}, 0));
            c.drawRoundRect(box, borderRadius, borderRadius, p);
            p.setPathEffect(null);
            p.setStyle(Paint.Style.FILL);

            if (preview) {
                float pw = dp(150), ph = dp(150);
                RectF pv = new RectF(getWidth() / 2 - pw / 2, dp(80),
                        getWidth() / 2 + pw / 2, dp(80) + ph);
                if (box.width() > 0 && box.height() > 0) {
                    src.set((int) box.left, (int) box.top, (int) box.right, (int) box.bottom);
                    p.setColor(0xffffffff);
                    c.drawRoundRect(new RectF(pv.left - dp(3), pv.top - dp(3),
                            pv.right + dp(3), pv.bottom + dp(3)), dp(6), dp(6), p);
                    c.drawBitmap(source, src, pv, p);
                }
            }
        }

        /** 保证底图始终盖住选框：不够大就放大，偏移超出就拉回。 */
        private void clampImage() {
            if (img.isEmpty() || box.isEmpty()) return;
            if (img.width() < box.width() || img.height() < box.height()) {
                float need = Math.max(box.width() / Math.max(1f, img.width()),
                        box.height() / Math.max(1f, img.height())) * 1.01f;
                float icx = img.centerX(), icy = img.centerY();
                float w = img.width() * need / 2f, h = img.height() * need / 2f;
                img.set(icx - w, icy - h, icx + w, icy + h);
                imgZoom *= need;
            }
            float dx = 0, dy = 0;
            if (img.left > box.left) dx = box.left - img.left;
            if (img.right < box.right) dx = box.right - img.right;
            if (img.top > box.top) dy = box.top - img.top;
            if (img.bottom < box.bottom) dy = box.bottom - img.bottom;
            if (dx != 0 || dy != 0) {
                img.offset(dx, dy);
                imgDx += dx;
                imgDy += dy;
            }
        }

        private float spacing(MotionEvent e) {
            if (e.getPointerCount() < 2) return 0f;
            float dx = e.getX(0) - e.getX(1);
            float dy = e.getY(0) - e.getY(1);
            return (float) Math.hypot(dx, dy);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    lastDist = 0f;
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    lastDist = spacing(e);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (e.getPointerCount() >= 2) {
                        float d = spacing(e);
                        if (lastDist > 0f && d > 0f) {
                            imgZoom = Math.max(1f, Math.min(8f, imgZoom * (d / lastDist)));
                            clampImage();
                            invalidate();
                        }
                        lastDist = d;
                    } else {
                        imgDx += e.getX() - downX;
                        imgDy += e.getY() - downY;
                        downX = e.getX();
                        downY = e.getY();
                        clampImage();
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    lastDist = 0f;
                    return true;
                default:
                    lastDist = 0f;
                    return true;
            }
        }
    }
}
