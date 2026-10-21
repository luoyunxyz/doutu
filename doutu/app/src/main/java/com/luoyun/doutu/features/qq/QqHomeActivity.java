package com.luoyun.doutu.features.qq;

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
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.io.InputStream;

/**
 * 「小Q」模块主页 —— 照 歪点点 4.0.5.39 真机截图重画。
 *
 * <p>版式（1440×3168 参考图 ×0.75 = 本页 1080×2388 设计坐标）：
 * 淡紫页头（头像 / 昵称 / Q我吧 / 宠物 / 加号）→ 白卡片里的搜索框 → 添加聊天 →
 * 浅灰内容区 → 底部 消息 / 频道 / 短视频 / 联系人 / 动态 五个页签。
 *
 * <p>图标全部是 {@code _ops/extract_qq_icons.py} 从真机截图里裁出来的
 * （assets/qq_icons），页头底色 #F1F4FE、导航条 #F7F8FC 都取自同一张图。
 */
public final class QqHomeActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        // 真机是全面屏：淡紫页头铺到状态栏后面，底部导航条也铺到手势条后面。
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        setContentView(new QqView());
        // 必须在 setContentView 之后取：decorView 还没挂上去时 getInsetsController() 返回 null。
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController ctl = w.getInsetsController();
            if (ctl != null) {
                ctl.setSystemBarsAppearance(
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        }
    }

    private final class QqView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect src = new Rect();
        private final RectF dst = new RectF();
        private final Bitmap avatar = icon("avatar");
        private final Bitmap pet = icon("pet");
        private final Bitmap plus = icon("plus");
        private final Bitmap navMsg = icon("nav_msg");
        private final Bitmap navChannel = icon("nav_channel");
        private final Bitmap navVideo = icon("nav_video");
        private final Bitmap navContact = icon("nav_contact");
        private final Bitmap navMoment = icon("nav_moment");

        QqView() {
            super(QqHomeActivity.this);
            setBackgroundColor(0xfff5f6fa);
        }

        private Bitmap icon(String key) {
            try (InputStream in = getContext().getAssets().open("qq_icons/" + key + ".png")) {
                return BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                return null;
            }
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

        private void round(Canvas c, float l, float t, float r, float b, float rad, int color) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawRoundRect(l, t, r, b, rad, rad, p);
        }

        private void art(Canvas c, Bitmap b, float left, float top, float size) {
            if (b == null) return;
            src.set(0, 0, b.getWidth(), b.getHeight());
            dst.set(left, top, left + size, top + size);
            p.setFilterBitmap(true);
            c.drawBitmap(b, src, dst, p);
        }

        @Override
        protected void onDraw(Canvas c) {
            c.save();
            c.scale(getWidth() / 1080f, getHeight() / 2388f);

            // 页头
            p.setColor(0xfff1f4fe);
            c.drawRect(0, 0, 1080, 248, p);

            // 头像：白色圆底 + 真机头像
            p.setColor(Color.WHITE);
            c.drawCircle(96, 170, 48, p);
            art(c, avatar, 58, 132, 76);

            text(c, "微笑似年华々", 180, 184, 39, 0xff1a1a1a, false);
            // Q我吧
            p.setColor(0xffffd84d);
            c.drawCircle(192, 200, 12, p);
            p.setColor(0xff333333);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.5f);
            c.drawCircle(192, 200, 12, p);
            p.setStyle(Paint.Style.FILL);
            text(c, "Q我吧 ›", 212, 212, 30, 0xff444444, false);

            art(c, pet, 879, 156, 57);
            art(c, plus, 975, 159, 57);

            // 白卡片：搜索 + 添加聊天
            p.setColor(Color.WHITE);
            c.drawRoundRect(-60, 248, 1140, 700, 34, 34, p);
            p.setColor(0xfff5f6fa);
            c.drawRect(0, 532, 1080, 2171, p);

            // 搜索框
            round(c, 48, 283, 1032, 375, 18, 0xfff4f5fb);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4);
            p.setColor(0xff9a9aa0);
            c.drawCircle(500, 323, 15, p);
            c.drawLine(511, 334, 522, 345, p);
            p.setStyle(Paint.Style.FILL);
            text(c, "搜索", 538, 338, 33, 0xff9a9aa0, false);

            // 添加聊天
            p.setColor(0xffffd84d);
            c.drawCircle(396, 474, 30, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            p.setColor(0xff333333);
            c.drawCircle(396, 474, 30, p);
            c.drawLine(380, 474, 412, 474, p);
            c.drawLine(396, 458, 396, 490, p);
            p.setStyle(Paint.Style.FILL);
            text(c, "添加聊天", 444, 488, 42, 0xff1a1a1a, false);

            // 底部页签
            p.setColor(0xfff7f8fc);
            c.drawRect(0, 2171, 1080, 2388, p);
            p.setColor(0xffe8e8ee);
            c.drawRect(0, 2171, 1080, 2172.5f, p);

            navItem(c, navMsg, "消息", 109, 0xff12b7f5, true);
            navItem(c, navChannel, "频道", 326, 0xff333333, false);
            navItem(c, navVideo, "短视频", 540, 0xff333333, false);
            navItem(c, navContact, "联系人", 754, 0xff333333, false);
            navItem(c, navMoment, "动态", 971, 0xff333333, false);

            // 消息红点
            p.setColor(0xfffa5151);
            c.drawCircle(130, 2188, 18, p);
            text(c, "0", 130 - textWidth("0", 24, false) / 2f, 2197, 24, Color.WHITE, false);

            c.restore();
        }

        private void navItem(Canvas c, Bitmap icon, String label, float cx, int color, boolean selected) {
            art(c, icon, cx - 31.5f, 2180, 63);
            text(c, label, cx - textWidth(label, 30, false) / 2f, 2300, 30, color, false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            float x = e.getX() * 1080f / Math.max(1, getWidth());
            float y = e.getY() * 2388f / Math.max(1, getHeight());
            if (y >= 2171) {
                if (x >= 217 && x < 433) {
                    openLocal("video", "短视频");
                } else if (x >= 650 && x < 860) {
                    openLocal("contacts", "联系人");
                } else if (x >= 860) {
                    openLocal("moments", "动态");
                }
                return true;
            }
            if (y >= 400 && y <= 540 && x >= 330 && x <= 700) {
                openLocal("pickchat", "创建聊天");
            }
            return true;
        }

        private void openLocal(String key, String title) {
            Intent i = new Intent(QqHomeActivity.this,
                    com.luoyun.doutu.features.tools.LocalToolActivity.class);
            i.putExtra("key", key);
            i.putExtra("title", title);
            startActivity(i);
        }
    }
}
