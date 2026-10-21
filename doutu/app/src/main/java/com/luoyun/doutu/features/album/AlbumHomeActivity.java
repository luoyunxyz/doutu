package com.luoyun.doutu.features.album;

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

import com.luoyun.doutu.features.tools.LocalToolActivity;

import java.io.InputStream;

/**
 * 「团队相册 / 云相册」主页 —— 照 歪点点 4.0.5.39 真机截图重画。
 *
 * <p>版式（1440×3168 参考图 ×0.75 = 1080×2388 设计坐标）：
 * 黄色标题栏（返回 / 云相册 / 创建·加入团队）→ 白底「我加入的团队 3 ›」→
 * 灰底提示条 → 三条特权（素材云存储 / 高效一键转发 / 多人共享使用）→ 灰底 →
 * 右下角黄色加号浮动按钮。图标取自真机截图（assets/album_icons）。
 */
public final class AlbumHomeActivity extends Activity {
    private static final int YELLOW = 0xfff9db61;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
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
        setContentView(new AlbumView());
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

    private final class AlbumView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect src = new Rect();
        private final RectF dst = new RectF();
        private final Bitmap cloud = icon("cloud");
        private final Bitmap send = icon("send");
        private final Bitmap share = icon("share");

        AlbumView() {
            super(AlbumHomeActivity.this);
            setBackgroundColor(0xfff5f5f5);
        }

        private Bitmap icon(String key) {
            try (InputStream in = getContext().getAssets().open("album_icons/" + key + ".png")) {
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

        private void art(Canvas c, Bitmap b, float left, float top, float w, float h) {
            if (b == null) return;
            src.set(0, 0, b.getWidth(), b.getHeight());
            dst.set(left, top, left + w, top + h);
            p.setFilterBitmap(true);
            c.drawBitmap(b, src, dst, p);
        }

        @Override
        protected void onDraw(Canvas c) {
            c.save();
            c.scale(getWidth() / 1080f, getHeight() / 2388f);

            // 标题栏（含状态栏底色）
            p.setColor(YELLOW);
            c.drawRect(0, 0, 1080, 281, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4.5f);
            p.setColor(0xff333333);
            c.drawLine(66, 190, 50, 206, p);
            c.drawLine(50, 206, 66, 222, p);
            p.setStyle(Paint.Style.FILL);
            textCentered(c, "云相册", 540, 219, 42, 0xff1a1a1a, false);
            text(c, "创建/加入团队", 1050 - textWidth("创建/加入团队", 36, false), 212, 36, 0xff1a1a1a, false);

            // 我加入的团队
            p.setColor(Color.WHITE);
            c.drawRect(0, 282, 1080, 432, p);
            text(c, "我加入的团队", 42, 386, 39, 0xff1a1a1a, false);
            text(c, "3", 958, 382, 33, 0xff333333, false);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3.5f);
            p.setColor(0xffb8b8b8);
            c.drawLine(1022, 350, 1032, 361, p);
            c.drawLine(1032, 361, 1022, 372, p);
            p.setStyle(Paint.Style.FILL);

            // 提示条
            p.setColor(0xfff7f7f7);
            c.drawRect(0, 432, 1080, 590, p);
            textCentered(c, "创建团队,即可获得以下特权", 540, 526, 33, 0xff666666, false);

            // 三条特权
            p.setColor(Color.WHITE);
            c.drawRect(0, 590, 1080, 1264, p);
            art(c, cloud, 66, 666, 135, 112);
            text(c, "素材云存储", 249, 722, 39, 0xff333333, false);
            text(c, "支持图片/视频，高清备份", 249, 780, 30, 0xff999999, false);

            art(c, send, 69, 872, 112, 112);
            text(c, "高效一键转发", 249, 924, 39, 0xff333333, false);
            text(c, "一键下载，转发更方便", 249, 982, 30, 0xff999999, false);

            art(c, share, 70, 1074, 120, 112);
            text(c, "多人共享使用", 249, 1118, 39, 0xff333333, false);
            text(c, "团队协作，效率翻倍", 249, 1176, 30, 0xff999999, false);

            // 右下角加号
            p.setColor(YELLOW);
            c.drawCircle(1006, 1218, 75, p);
            p.setColor(0xff333333);
            p.setStrokeWidth(6);
            c.drawLine(976, 1218, 1036, 1218, p);
            c.drawLine(1006, 1188, 1006, 1248, p);

            c.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            float x = e.getX() * 1080f / Math.max(1, getWidth());
            float y = e.getY() * 2388f / Math.max(1, getHeight());
            if (y <= 281) {
                if (x < 100) finish();
                else if (x > 720) openTeam();
                return true;
            }
            if (y > 282 && y <= 432) {
                openTeam();
                return true;
            }
            float dx = x - 1006, dy = y - 1218;
            if (dx * dx + dy * dy <= 75 * 75) openTeam();
            return true;
        }

        private void openTeam() {
            Intent i = new Intent(AlbumHomeActivity.this, LocalToolActivity.class);
            i.putExtra("key", "team");
            i.putExtra("title", "我的团队");
            startActivity(i);
        }
    }
}
