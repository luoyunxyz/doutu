package com.luoyun.doutu.features.wx;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

import java.io.InputStream;

/**
 * 「发出的红包」页 —— 照 歪点点 4.0.5.39 真机截图重画：
 * 红色页头（返回 / 发出的红包 / 省略号）+ 浅灰统计区（年份下拉、头像、
 * 「xxx共发出」、0.00 元、发出红包总数0个）+ 白色明细列表（当前为空）。
 */
public final class SentRedPacketActivity extends Activity {
    private static final int RED = 0xffe36855;
    private static final int GRAY = 0xffefefef;
    private static final int GOLD = 0xffbea475;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(RED);
        w.setNavigationBarColor(Color.WHITE);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        setContentView(new SentView());
    }

    private final class SentView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect src = new Rect();
        private final RectF dst = new RectF();
        private final Bitmap avatar;

        SentView() {
            super(SentRedPacketActivity.this);
            Bitmap b = null;
            try (InputStream in = getContext().getAssets().open("wx_icons/sentrp_avatar.png")) {
                b = BitmapFactory.decodeStream(in);
            } catch (Exception ignored) { }
            avatar = b;
        }

        private void text(Canvas c, String s, float x, float baseline, float size, int color, boolean bold) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            p.setTextSize(size);
            p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            c.drawText(s, x, baseline, p);
        }

        private float textWidth(String s, float size, boolean bold) {
            p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            p.setTextSize(size);
            return p.measureText(s);
        }

        private void textCentered(Canvas c, String s, float cx, float baseline, float size, int color, boolean bold) {
            text(c, s, cx - textWidth(s, size, bold) / 2f, baseline, size, color, bold);
        }

        @Override
        protected void onDraw(Canvas c) {
            c.save();
            c.scale(getWidth() / 1080f, getHeight() / 2388f);
            // 页头
            p.setColor(RED);
            c.drawRect(0, 0, 1080, 244, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5);
            p.setColor(Color.WHITE);
            c.drawLine(56, 150, 38, 172, p);
            c.drawLine(38, 172, 56, 194, p);
            p.setStyle(Paint.Style.FILL);
            textCentered(c, "发出的红包", 540, 205, 42, Color.WHITE, false);
            p.setColor(Color.WHITE);
            for (int i = 0; i < 3; i++) c.drawCircle(1000 + i * 22, 178, 4.5f, p);

            // 统计区
            p.setColor(GRAY);
            c.drawRect(0, 244, 1080, 1086, p);
            text(c, "2021年", 1040 - textWidth("2021年", 33, false), 386, 33, GOLD, false);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3.5f);
            p.setColor(GOLD);
            c.drawLine(955, 424, 968, 438, p);
            c.drawLine(968, 438, 981, 424, p);
            p.setStyle(Paint.Style.FILL);

            // 头像方块（截图里就是白底方图，整块照搬）
            p.setColor(Color.WHITE);
            c.drawRect(434, 380, 651, 597, p);
            if (avatar != null) {
                src.set(0, 0, avatar.getWidth(), avatar.getHeight());
                dst.set(434, 380, 651, 597);
                p.setFilterBitmap(true);
                c.drawBitmap(avatar, src, dst, p);
            }

            textCentered(c, "微笑似年华々共发出", 540, 702, 39, 0xff1e1e1e, false);

            // 大金额 0.00
            p.setColor(0xff111111);
            p.setTextSize(150);
            p.setTypeface(Typeface.DEFAULT);
            float mw = p.measureText("0.00");
            float left = 540 - mw / 2f - 40;
            c.drawText("0.00", left, 856, p);
            text(c, "元", left + mw + 6, 848, 38, 0xff111111, false);

            // 总数
            String a = "发出红包总数", b = "个";
            float wa = textWidth(a, 36, false), wc = textWidth("0", 36, false), wb = textWidth(b, 36, false);
            float x = 540 - (wa + wc + wb) / 2f;
            text(c, a, x, 986, 36, 0xff9a9a9a, false);
            text(c, "0", x + wa, 986, 36, GOLD, false);
            text(c, b, x + wa + wc, 986, 36, 0xff9a9a9a, false);

            // 明细列表（空）
            p.setColor(Color.WHITE);
            c.drawRect(0, 1086, 1080, 2388, p);
            c.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            float x = e.getX() * 1080f / Math.max(1, getWidth());
            float y = e.getY() * 2388f / Math.max(1, getHeight());
            if (y < 244 && x < 100) finish();
            return true;
        }
    }
}
