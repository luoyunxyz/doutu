package com.luoyun.doutu.account;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * 圆形头像控件：有图就画图（按 cover 裁进圆里），没图就画用户名首字。
 * 用在「设置」页那一行，也可以放在任何地方。
 */
public final class AvatarView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap bitmap;
    private String initial = "登";
    private int fromColor = 0xFF2F6FED;
    private int toColor = 0xFF8A6BFF;

    public AvatarView(Context context) {
        this(context, null);
    }

    public AvatarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setBitmap(Bitmap bmp) {
        this.bitmap = bmp;
        invalidate();
    }

    public void setInitial(String text) {
        this.initial = text == null || text.isEmpty() ? "登" : text.substring(0, 1);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f;

        // 底色（实色打底 + 渐变叠加，和主界面保持一致）
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(fromColor);
        canvas.drawCircle(cx, cy, r, paint);
        paint.setShader(new android.graphics.LinearGradient(0, 0, w, h, fromColor, toColor, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setShader(null);

        if (bitmap != null && !bitmap.isRecycled()) {
            canvas.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addCircle(cx, cy, r, android.graphics.Path.Direction.CW);
            canvas.clipPath(clip);
            // cover：短边铺满，长边居中裁掉
            float scale = Math.max(w / bitmap.getWidth(), h / bitmap.getHeight());
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            matrix.postTranslate(cx - bitmap.getWidth() * scale / 2f, cy - bitmap.getHeight() * scale / 2f);
            Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(bitmap, matrix, bmp);
            canvas.restore();
        } else {
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(r * 0.9f);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(initial, cx - paint.measureText(initial) / 2f, cy + r * 0.33f, paint);
        }
    }

    /**
     * 把头像按「短边铺满、长边居中裁掉」算好缩放，供别处（如主界面 Canvas）用同样的裁法画。
     * @param size 目标边长
     */
    public static Matrix coverMatrix(Bitmap bitmap, float size) {
        float scale = Math.max(size / bitmap.getWidth(), size / bitmap.getHeight());
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postTranslate((size - bitmap.getWidth() * scale) / 2f, (size - bitmap.getHeight() * scale) / 2f);
        return matrix;
    }
}
