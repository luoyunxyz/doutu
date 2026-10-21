package com.luoyun.doutu.features.wx;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

import com.luoyun.doutu.features.tools.LocalToolActivity;

/**
 * 「群发助手」页 —— 照 歪点点 4.0.5.39 真机截图重画：
 * 浅灰页头（返回 / 群发助手 / 齿轮）+ 空列表 + 底部「新建群发」。
 * 点「新建群发」进本地群发页（选联系人 + 内容 + 发送）。
 */
public final class MassAssistantActivity extends Activity {
    private static final int BG = 0xffededed;
    private static final int FOOT = 0xfffbfbfb;
    private static final int ACCENT = 0xff58668c;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(FOOT);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        setContentView(new MassView());
    }

    private final class MassView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        MassView() {
            super(MassAssistantActivity.this);
            setBackgroundColor(BG);
        }

        private void text(Canvas c, String s, float x, float baseline, float size, int color, boolean bold) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            p.setTextSize(size);
            p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            c.drawText(s, x, baseline, p);
        }

        @Override
        protected void onDraw(Canvas c) {
            c.save();
            c.scale(getWidth() / 1080f, getHeight() / 2388f);
            c.drawColor(BG);
            // 返回箭头
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4.5f);
            p.setColor(0xff333333);
            c.drawLine(58, 160, 40, 180, p);
            c.drawLine(40, 180, 58, 200, p);
            p.setStyle(Paint.Style.FILL);
            // 标题
            p.setColor(0xff1a1a1a);
            p.setTextSize(42);
            p.setTypeface(Typeface.DEFAULT);
            float tw = p.measureText("群发助手");
            c.drawText("群发助手", 540 - tw / 2f, 205, p);
            // 齿轮
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4);
            p.setColor(0xff333333);
            c.drawCircle(1005, 180, 20, p);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                c.drawLine(1005 + (float) Math.cos(a) * 22, 180 + (float) Math.sin(a) * 22,
                        1005 + (float) Math.cos(a) * 30, 180 + (float) Math.sin(a) * 30, p);
            }
            c.drawCircle(1005, 180, 7, p);
            p.setStyle(Paint.Style.FILL);

            // 分隔线 + 空列表
            p.setColor(0xffe5e5e5);
            c.drawRect(0, 244, 1080, 245.5f, p);
            p.setColor(BG);
            c.drawRect(0, 245, 1080, 2201, p);

            // 底部「新建群发」
            p.setColor(0xffeaeaea);
            c.drawRect(0, 2198, 1080, 2201, p);
            p.setColor(FOOT);
            c.drawRect(0, 2201, 1080, 2388, p);
            p.setColor(ACCENT);
            p.setTextSize(36);
            float bw = p.measureText("新建群发");
            c.drawText("新建群发", 540 - bw / 2f, 2295, p);
            c.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            float x = e.getX() * 1080f / Math.max(1, getWidth());
            float y = e.getY() * 2388f / Math.max(1, getHeight());
            if (y < 244) {
                if (x < 90) finish();
                return true;
            }
            if (y > 2201) {
                Intent i = new Intent(MassAssistantActivity.this, LocalToolActivity.class);
                i.putExtra("key", "broadcast");
                i.putExtra("title", "群发助手");
                startActivity(i);
            }
            return true;
        }
    }
}
