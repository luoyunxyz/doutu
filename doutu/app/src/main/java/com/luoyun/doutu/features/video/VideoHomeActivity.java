package com.luoyun.doutu.features.video;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

import com.luoyun.doutu.features.tools.LocalToolActivity;

import java.io.InputStream;

/**
 * 「视频」模块主页 —— 照 歪点点 4.0.5.39 真机截图重画的黑底短视频封面页。
 *
 * <p>顶部：直播 / 杭州 / 关注(99) / 推荐 / 搜索；中间：黄色加号「上传视频」；
 * 右侧：头像、点赞 10000.0w、评论 10.0w、分享 999、唱片；左下：账号与音乐；
 * 底部：首页 / 朋友 / + / 消息(99) / 我。版式按 1440×3168 参考图 ×0.75 换算。
 */
public final class VideoHomeActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.BLACK);
        w.setNavigationBarColor(Color.BLACK);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        setContentView(new VideoView());
    }

    private final class VideoView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect src = new Rect();
        private final RectF dst = new RectF();
        private final Bitmap avatar = icon("avatar");
        private int tab = 2; // 0 杭州 / 1 关注 / 2 推荐
        private final String[] tabs = {"杭州", "关注", "推荐"};
        private final float[] tabX = {324, 527, 735};

        VideoView() {
            super(VideoHomeActivity.this);
            setBackgroundColor(Color.BLACK);
        }

        private Bitmap icon(String key) {
            try (InputStream in = getContext().getAssets().open("video_icons/" + key + ".png")) {
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

        private void textCentered(Canvas c, String s, float cx, float baseline, float size, int color, boolean bold) {
            text(c, s, cx - textWidth(s, size, bold) / 2f, baseline, size, color, bold);
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
            c.drawColor(Color.BLACK);

            // ── 顶部分类 ───────────────────────────────────────
            drawLive(c);
            for (int i = 0; i < tabs.length; i++) {
                boolean on = i == tab;
                textCentered(c, tabs[i], tabX[i], 203, 38, on ? Color.WHITE : 0xffb5b5b5, on);
                if (on) {
                    p.setColor(Color.WHITE);
                    c.drawRect(tabX[i] - 48, 216, tabX[i] + 48, 221, p);
                }
            }
            // 关注上的 99
            p.setColor(0xffffd84d);
            c.drawRoundRect(560, 132, 618, 176, 22, 22, p);
            textCentered(c, "99", 589, 168, 28, 0xff1a1a1a, false);
            drawSearch(c);

            // ── 中间上传 ───────────────────────────────────────
            p.setColor(0xffffd84d);
            c.drawCircle(540, 1061, 71, p);
            p.setColor(0xff1a1a1a);
            p.setStrokeWidth(8);
            c.drawLine(512, 1061, 568, 1061, p);
            c.drawLine(540, 1033, 540, 1089, p);
            textCentered(c, "上传视频", 540, 1170, 30, 0xff9a9a9a, false);
            textCentered(c, "可自动合成点赞评论等数据", 540, 1222, 26, 0xff8a8a8a, false);

            // ── 右侧互动栏 ─────────────────────────────────────
            art(c, avatar, 927, 1026, 142);
            drawHeart(c, 998, 1321);
            textCentered(c, "10000.0w", 998, 1427, 34, Color.WHITE, false);
            drawComment(c, 998, 1541);
            textCentered(c, "10.0w", 998, 1649, 34, Color.WHITE, false);
            drawShare(c, 998, 1763);
            textCentered(c, "999", 998, 1870, 34, Color.WHITE, false);
            drawDisc(c, 1000, 2055);

            // ── 左下账号与音乐 ─────────────────────────────────
            text(c, "@微笑似年华々", 41, 1954, 48, Color.WHITE, false);
            text(c, "请点击编辑内容", 41, 2026, 44, Color.WHITE, false);
            text(c, "♪", 41, 2118, 34, Color.WHITE, false);
            text(c, "@唯一创作的音乐原声", 82, 2118, 34, Color.WHITE, false);

            // ── 底部导航 ───────────────────────────────────────
            textCentered(c, "首页", 82, 2286, 42, Color.WHITE, true);
            textCentered(c, "朋友", 300, 2286, 42, 0xff9a9a9a, false);
            p.setColor(0xffffd84d);
            c.drawRoundRect(792, 2200, 872, 2244, 22, 22, p);
            textCentered(c, "99", 832, 2236, 26, 0xff1a1a1a, false);
            textCentered(c, "消息", 742, 2286, 42, 0xff9a9a9a, false);
            textCentered(c, "我", 953, 2286, 42, 0xff9a9a9a, false);
            // 中间加号
            p.setColor(Color.TRANSPARENT);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3.5f);
            p.setColor(0xffe8e8e8);
            c.drawRoundRect(472, 2210, 608, 2290, 20, 20, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            p.setStrokeWidth(5);
            c.drawLine(526, 2250, 554, 2250, p);
            c.drawLine(540, 2236, 540, 2264, p);

            c.restore();
        }

        /** 直播：参考图是一个「帐篷/帽子」线条图标，下面一行小字「直播」。 */
        private void drawLive(Canvas c) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4f);
            p.setColor(0xffcfcfcf);
            c.drawLine(58, 196, 90, 156, p);
            c.drawLine(90, 156, 122, 196, p);
            p.setStrokeWidth(5f);
            c.drawLine(52, 200, 126, 200, p);
            p.setStyle(Paint.Style.FILL);
            textCentered(c, "直播", 90, 218, 24, 0xffcfcfcf, false);
        }

        private void drawSearch(Canvas c) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4f);
            p.setColor(0xffdddddd);
            c.drawCircle(986, 176, 20, p);
            c.drawLine(1001, 191, 1016, 206, p);
            p.setStyle(Paint.Style.FILL);
        }

        private void drawHeart(Canvas c, float cx, float cy) {
            Path heart = new Path();
            heart.moveTo(cx, cy + 30);
            heart.cubicTo(cx - 48, cy - 4, cx - 34, cy - 48, cx, cy - 22);
            heart.cubicTo(cx + 34, cy - 48, cx + 48, cy - 4, cx, cy + 30);
            heart.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xffff2b55);
            c.drawPath(heart, p);
        }

        private void drawComment(Canvas c, float cx, float cy) {
            p.setColor(Color.WHITE);
            c.drawCircle(cx, cy, 42, p);
            Path tail = new Path();
            tail.moveTo(cx - 12, cy + 32);
            tail.lineTo(cx + 10, cy + 32);
            tail.lineTo(cx - 10, cy + 54);
            tail.close();
            c.drawPath(tail, p);
            p.setColor(0xff222222);
            for (int i = -1; i <= 1; i++) c.drawCircle(cx + i * 18, cy, 5, p);
        }

        private void drawShare(Canvas c, float cx, float cy) {
            Path a = new Path();
            a.moveTo(cx + 38, cy + 6);
            a.lineTo(cx - 8, cy - 34);
            a.lineTo(cx - 8, cy - 14);
            a.lineTo(cx - 42, cy - 14);
            a.lineTo(cx - 42, cy + 24);
            a.lineTo(cx - 8, cy + 24);
            a.lineTo(cx - 8, cy + 44);
            a.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xffdcdcdc);
            c.drawPath(a, p);
        }

        /** 唱片：深灰外圈 + 浅灰内盘 + 白色头像剪影（参考图就是一张模糊头像）。 */
        private void drawDisc(Canvas c, float cx, float cy) {
            p.setColor(0xff333333);
            c.drawCircle(cx, cy, 73, p);
            p.setColor(0xffd8d8d8);
            c.drawCircle(cx, cy, 48, p);
            p.setColor(Color.WHITE);
            c.drawCircle(cx, cy - 6, 18, p);
            Path body = new Path();
            body.moveTo(cx - 26, cy + 34);
            body.cubicTo(cx - 26, cy + 6, cx + 26, cy + 6, cx + 26, cy + 34);
            body.close();
            c.drawPath(body, p);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            float x = e.getX() * 1080f / Math.max(1, getWidth());
            float y = e.getY() * 2388f / Math.max(1, getHeight());

            if (y > 2190) {
                if (x > 430 && x < 650) open("video", "上传视频");
                else if (x > 230 && x < 430) open("video", "朋友");
                else if (x > 640 && x < 860) open("notices", "消息");
                else if (x > 860) open("profile", "我");
                return true;
            }
            float dx = x - 540, dy = y - 1061;
            if (dx * dx + dy * dy < 95 * 95) {
                open("video", "上传视频");
                return true;
            }
            if (y < 240) {
                for (int i = 0; i < tabs.length; i++) {
                    if (Math.abs(x - tabX[i]) < 70) {
                        tab = i;
                        invalidate();
                        return true;
                    }
                }
            }
            return true;
        }

        private void open(String key, String title) {
            Intent i = new Intent(VideoHomeActivity.this, LocalToolActivity.class);
            i.putExtra("key", key);
            i.putExtra("title", title);
            startActivity(i);
        }
    }
}
