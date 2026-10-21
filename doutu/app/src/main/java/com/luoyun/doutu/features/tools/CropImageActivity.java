package com.luoyun.doutu.features.tools;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 参考 ClipView 的裁剪步骤：拖动裁剪框、双指或 +/- 缩放、还原、完成。
 * 用于换头像/自动替换的底图取景。
 */
public final class CropImageActivity extends Activity {
    public static Bitmap source;
    public static Bitmap result;

    private CropView cropView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        result = null;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff111111);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setAllCaps(false);
        cancel.setTextColor(Color.WHITE);
        cancel.setBackgroundColor(Color.TRANSPARENT);
        cancel.setOnClickListener(v -> finish());
        bar.addView(cancel, new LinearLayout.LayoutParams(0, dp(50), 1f));
        TextView title = new TextView(this);
        title.setText("裁剪底图");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(50), 2f));
        Button done = new Button(this);
        done.setText("完成");
        done.setAllCaps(false);
        done.setTextColor(0xffffda43);
        done.setBackgroundColor(Color.TRANSPARENT);
        done.setOnClickListener(v -> {
            result = cropView.crop();
            setResult(RESULT_OK);
            finish();
        });
        bar.addView(done, new LinearLayout.LayoutParams(0, dp(50), 1f));
        root.addView(bar);

        FrameLayout frame = new FrameLayout(this);
        cropView = new CropView(this);
        cropView.setBitmap(source);
        frame.addView(cropView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER);
        tools.setBackgroundColor(0xff1b1b1b);
        Button reset = new Button(this);
        reset.setText("还原");
        reset.setAllCaps(false);
        reset.setOnClickListener(v -> cropView.reset());
        tools.addView(reset, new LinearLayout.LayoutParams(dp(84), dp(46)));
        Button minus = new Button(this);
        minus.setText("－");
        minus.setAllCaps(false);
        minus.setOnClickListener(v -> cropView.scaleBox(0.88f));
        tools.addView(minus, new LinearLayout.LayoutParams(dp(70), dp(46)));
        Button plus = new Button(this);
        plus.setText("＋");
        plus.setAllCaps(false);
        plus.setOnClickListener(v -> cropView.scaleBox(1.12f));
        tools.addView(plus, new LinearLayout.LayoutParams(dp(70), dp(46)));
        Button color = new Button(this);
        color.setText("切换颜色");
        color.setAllCaps(false);
        color.setTextSize(12);
        color.setOnClickListener(v -> cropView.toggleBorder());
        tools.addView(color, new LinearLayout.LayoutParams(dp(96), dp(46)));
        Button previewBtn = new Button(this);
        previewBtn.setText("按住预览");
        previewBtn.setAllCaps(false);
        previewBtn.setTextSize(12);
        previewBtn.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) { cropView.setPreview(true); return true; }
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) { cropView.setPreview(false); return true; }
            return false;
        });
        tools.addView(previewBtn, new LinearLayout.LayoutParams(dp(96), dp(46)));
        root.addView(tools);
        setContentView(root);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static final class CropView extends View {
        private Bitmap bitmap;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF image = new RectF();
        private final RectF box = new RectF();
        private float scale = 1f;
        private float downX, downY;
        private boolean dragging;
        private int borderColor = Color.WHITE;
        private boolean preview = false;
        private ScaleGestureDetector scaleDetector;

        CropView(Context context) {
            super(context);
            setBackgroundColor(0xff111111);
            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    scaleBox(detector.getScaleFactor());
                    return true;
                }
            });
        }

        void setBitmap(Bitmap b) {
            bitmap = b;
            reset();
        }

        void reset() {
            if (bitmap == null) return;
            float vw = getWidth() > 0 ? getWidth() : 1080;
            float vh = getHeight() > 0 ? getHeight() : 1800;
            float s = Math.min(vw / bitmap.getWidth(), vh / bitmap.getHeight());
            float w = bitmap.getWidth() * s, h = bitmap.getHeight() * s;
            image.set((vw - w) / 2f, (vh - h) / 2f, (vw - w) / 2f + w, (vh - h) / 2f + h);
            float side = Math.min(w, h) * .6f;
            box.set(image.centerX() - side / 2f, image.centerY() - side / 2f, image.centerX() + side / 2f, image.centerY() + side / 2f);
            invalidate();
        }

        void toggleBorder() {
            if (borderColor == Color.WHITE) borderColor = 0xffffda43;
            else if (borderColor == 0xffffda43) borderColor = 0xff00e0c6;
            else borderColor = Color.WHITE;
            invalidate();
        }

        void setPreview(boolean on) { preview = on; invalidate(); }

        void scaleBox(float factor) {
            if (image.isEmpty()) return;
            float side = Math.max(Math.min(120f, Math.min(image.width(), image.height())), Math.min(box.width() * factor, Math.min(image.width(), image.height())));
            float cx = box.centerX(), cy = box.centerY();
            box.set(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f);
            constrain(false);
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (bitmap != null) reset();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (bitmap == null) return;
            c.drawBitmap(bitmap, null, image, paint);
            if (!preview) {
                paint.setColor(0x99000000);
                c.drawRect(0, 0, getWidth(), box.top, paint);
                c.drawRect(0, box.bottom, getWidth(), getHeight(), paint);
                c.drawRect(0, box.top, box.left, box.bottom, paint);
                c.drawRect(box.right, box.top, getWidth(), box.bottom, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(borderColor);
            c.drawRect(box, paint);
            paint.setColor(0x55ffffff);
            paint.setStrokeWidth(1f);
            for (int i = 1; i < 3; i++) {
                float x = box.left + box.width() * i / 3f;
                float y = box.top + box.height() * i / 3f;
                c.drawLine(x, box.top, x, box.bottom, paint);
                c.drawLine(box.left, y, box.right, y, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffffda43);
            float r = 10f;
            c.drawCircle(box.left, box.top, r, paint);
            c.drawCircle(box.right, box.top, r, paint);
            c.drawCircle(box.left, box.bottom, r, paint);
            c.drawCircle(box.right, box.bottom, r, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getPointerCount() > 1) {
                scaleDetector.onTouchEvent(e);
                return true;
            }
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX(); downY = e.getY();
                    dragging = box.contains(downX, downY);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        float dx = e.getX() - downX, dy = e.getY() - downY;
                        box.offset(dx, dy);
                        downX = e.getX(); downY = e.getY();
                        constrain(false);
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    dragging = false;
                    return true;
            }
            return super.onTouchEvent(e);
        }

        private void constrain(boolean keepSize) {
            if (image.isEmpty()) return;
            if (box.width() > image.width()) {
                float side = image.width();
                box.set(image.centerX() - side / 2f, box.centerY() - side / 2f, image.centerX() + side / 2f, box.centerY() + side / 2f);
            }
            if (box.left < image.left) box.offset(image.left - box.left, 0);
            if (box.top < image.top) box.offset(0, image.top - box.top);
            if (box.right > image.right) box.offset(image.right - box.right, 0);
            if (box.bottom > image.bottom) box.offset(0, image.bottom - box.bottom);
        }

        Bitmap crop() {
            if (bitmap == null || image.isEmpty()) return null;
            float sx = bitmap.getWidth() / image.width();
            float sy = bitmap.getHeight() / image.height();
            int x = Math.max(0, Math.round((box.left - image.left) * sx));
            int y = Math.max(0, Math.round((box.top - image.top) * sy));
            int w = Math.min(bitmap.getWidth() - x, Math.round(box.width() * sx));
            int h = Math.min(bitmap.getHeight() - y, Math.round(box.height() * sy));
            if (w <= 0 || h <= 0) return null;
            return Bitmap.createBitmap(bitmap, x, y, w, h);
        }
    }
}
