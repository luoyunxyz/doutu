package com.luoyun.doutu.features.qq;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

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
        QqView content = new QqView();
        setContentView(content);
        // 工具页「公众号」：进 QQ 主页并弹新建公众号表单（只有一个「消息置顶」开关）。
        if (getIntent().getBooleanExtra("gzh", false)) {
            content.post(this::showGzhDialog);
        }
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

    /**
     * QQ 的「公众号」表单：和微信版同一套图标/输入框，但少「设为蓝色字体」「免打扰」
     * 两个开关，只剩「消息置顶」。
     */
    private void showGzhDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(10));
        root.setBackground(bg);
        root.setPadding(0, dp(14), 0, 0);

        LinearLayout icons = new LinearLayout(this);
        icons.setGravity(Gravity.TOP);
        icons.setPadding(dp(26), 0, dp(26), 0);
        icons.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96)));
        icons.addView(iconColumn("gzh_star", 42, "常用图标"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        ImageView avatar = new ImageView(this);
        avatar.setImageBitmap(asset("wx_icons/gzh_avatar.png"));
        icons.addView(avatar, new LinearLayout.LayoutParams(dp(79), dp(79)));
        icons.addView(iconColumn("gzh_photo", 45, "从相册获取"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        root.addView(icons);

        EditText name = field(root, "名称：");
        EditText content = field(root, "消息内容：");
        switchRow(root, "消息置顶");

        View divider = new View(this);
        divider.setBackgroundColor(0xffeeeeee);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dlp.topMargin = dp(16);
        root.addView(divider, dlp);

        LinearLayout footer = new LinearLayout(this);
        footer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextSize(16);
        cancel.setTextColor(0xff888888);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> dialog.dismiss());
        footer.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        TextView ok = new TextView(this);
        ok.setText("确定");
        ok.setTextSize(16);
        ok.setTextColor(0xff333333);
        ok.setGravity(Gravity.CENTER);
        ok.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String m = content.getText().toString().trim();
            getSharedPreferences("doutu_tools", 0).edit()
                    .putString("qq_gzh_name", n)
                    .putString("qq_gzh_content", m)
                    .apply();
            dialog.dismiss();
            Toast.makeText(QqHomeActivity.this, "公众号消息已生成", Toast.LENGTH_SHORT).show();
        });
        footer.addView(ok, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        root.addView(footer);

        dialog.setContentView(root);
        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            win.setDimAmount(0.38f);
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = win.getAttributes();
            lp.width = dp(282);
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            win.setAttributes(lp);
        }
        dialog.show();
    }

    private View iconColumn(String key, int size, String label) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView img = new ImageView(this);
        img.setImageBitmap(asset("wx_icons/" + key + ".png"));
        col.addView(img, new LinearLayout.LayoutParams(dp(size), dp(size)));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(11);
        tv.setTextColor(0xff333333);
        tv.setPadding(0, dp(6), 0, 0);
        col.addView(tv);
        return col;
    }

    private EditText field(LinearLayout root, String hint) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(5));
        g.setStroke(dp(1), 0xffd9d9d9);
        box.setBackground(g);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(35));
        blp.leftMargin = dp(30);
        blp.rightMargin = dp(30);
        blp.topMargin = dp(18);
        box.setLayoutParams(blp);
        box.setPadding(dp(8), 0, dp(8), 0);
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(13);
        input.setBackground(null);
        input.setSingleLine(true);
        input.setPadding(0, 0, 0, 0);
        box.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        TextView clear = new TextView(this);
        clear.setText("✕");
        clear.setTextSize(10);
        clear.setTextColor(Color.WHITE);
        clear.setGravity(Gravity.CENTER);
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(0xffc9c9c9);
        cg.setShape(GradientDrawable.OVAL);
        clear.setBackground(cg);
        clear.setOnClickListener(v -> input.setText(""));
        box.addView(clear, new LinearLayout.LayoutParams(dp(16), dp(16)));
        root.addView(box);
        return input;
    }

    private void switchRow(LinearLayout root, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(5));
        g.setStroke(dp(1), 0xffd9d9d9);
        row.setBackground(g);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(35));
        rlp.leftMargin = dp(30);
        rlp.rightMargin = dp(30);
        rlp.topMargin = dp(18);
        row.setLayoutParams(rlp);
        row.setPadding(dp(10), 0, dp(4), 0);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTextColor(0xff333333);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        Switch sw = new Switch(this);
        sw.setScaleX(0.72f);
        sw.setScaleY(0.72f);
        row.addView(sw, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(row);
    }

    private Bitmap asset(String path) {
        try (InputStream in = getAssets().open(path)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
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
            // Q我吧：黄色笑脸 + 深色描边
            p.setColor(0xffffd84d);
            c.drawCircle(192, 200, 12, p);
            p.setColor(0xff5a4a00);
            c.drawCircle(188, 198, 1.6f, p);
            c.drawCircle(196, 198, 1.6f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f);
            c.drawArc(new RectF(186.5f, 196.5f, 197.5f, 205.5f), 20, 140, false, p);
            p.setColor(0xff333333);
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
