package com.luoyun.doutu.features.wx;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import java.io.InputStream;

/**
 * 「上传截图」页 —— 照 歪点点 4.0.5.39 真机截图重画：
 * 纯白页 + 中间黄色加号圆 + 「上传图片」，点一下从相册选一张图，选完就地显示。
 */
public final class UploadImageActivity extends Activity {
    private Bitmap picked;
    private UploadView view;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.WHITE);
        w.setNavigationBarColor(Color.WHITE);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        view = new UploadView();
        setContentView(view);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 41 || result != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            picked = BitmapFactory.decodeStream(in);
            if (picked != null) view.invalidate();
        } catch (Exception e) {
            Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show();
        }
    }

    private final class UploadView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect src = new Rect();
        private final RectF dst = new RectF();

        UploadView() {
            super(UploadImageActivity.this);
            setBackgroundColor(Color.WHITE);
        }

        @Override
        protected void onDraw(Canvas c) {
            c.save();
            c.scale(getWidth() / 1080f, getHeight() / 2388f);
            if (picked != null) {
                // 选完图：按原比例塞进中间 420 的方框。
                float box = 420, left = 540 - box / 2f, top = 1000;
                float k = Math.min(box / picked.getWidth(), box / picked.getHeight());
                float w = picked.getWidth() * k, h = picked.getHeight() * k;
                src.set(0, 0, picked.getWidth(), picked.getHeight());
                dst.set(540 - w / 2f, top + (box - h) / 2f, 540 + w / 2f, top + (box - h) / 2f + h);
                p.setFilterBitmap(true);
                c.drawBitmap(picked, src, dst, p);
            } else {
                p.setColor(0xfff9db61);
                c.drawCircle(540, 1187, 62, p);
                p.setColor(0xff333333);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3);
                c.drawCircle(540, 1187, 62, p);
                p.setStrokeWidth(8);
                c.drawLine(516, 1187, 564, 1187, p);
                c.drawLine(540, 1163, 540, 1211, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xff555555);
                p.setTextSize(34);
                p.setTypeface(Typeface.DEFAULT);
                float w = p.measureText("上传图片");
                c.drawText("上传图片", 540 - w / 2f, 1319, p);
            }
            c.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            float x = e.getX() * 1080f / Math.max(1, getWidth());
            float y = e.getY() * 2388f / Math.max(1, getHeight());
            float dx = x - 540, dy = y - (picked == null ? 1187 : 1180);
            if (dx * dx + dy * dy < 120 * 120) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(i, "选择图片"), 41);
            }
            return true;
        }
    }
}
