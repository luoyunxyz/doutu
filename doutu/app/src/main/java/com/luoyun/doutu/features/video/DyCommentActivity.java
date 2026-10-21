package com.luoyun.doutu.features.video;

import android.app.Activity;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 「评论切片」页 —— 按真机 activity_dy_comment.xml 重画：
 * 白栏 + 头像/昵称/评论正文/时间地点/星标 + 「更多回复」+ 浅色模式开关 + 保存截图。
 * 保存截图把当前评论卡片渲染成 PNG 存到相册。
 */
public final class DyCommentActivity extends Activity {
    private LinearLayout card;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xffffd84a);
        w.setNavigationBarColor(Color.WHITE);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff7f7f7);

        // 标题栏
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(0xffffd84a);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(30);
        back.setTextColor(0xff161722);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new FrameLayout.LayoutParams(dp(56), dp(56)));
        TextView title = new TextView(this);
        title.setText("发送评论");
        title.setTextSize(17);
        title.setTextColor(0xff161722);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        root.addView(bar);

        // 评论卡片
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);

        LinearLayout head = new LinearLayout(this);
        head.setPadding(dp(16), dp(19), dp(16), dp(16));
        ImageView avatar = new ImageView(this);
        Bitmap av = asset("video_icons/avatar.png");
        if (av != null) avatar.setImageBitmap(circle(av));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(36), dp(36));
        head.addView(avatar, alp);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rlp.leftMargin = dp(6);
        head.addView(right, rlp);

        TextView name = new TextView(this);
        name.setText("小鱼");
        name.setTextSize(13);
        name.setTextColor(0xffafb0b3);
        right.addView(name);

        final EditText content = new EditText(this);
        content.setText("这个也太好看了吧！");
        content.setTextSize(15);
        content.setTextColor(0xff161722);
        content.setBackground(null);
        content.setPadding(0, 0, dp(28), 0);
        content.setMaxLines(4);
        right.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(0, dp(5), dp(17), 0);
        TextView time = new TextView(this);
        time.setText("· 2小时前 · 北京");
        time.setTextSize(13);
        time.setTextColor(0xffb0b0b3);
        meta.addView(time, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView star = new TextView(this);
        star.setText("☆ 1.2万");
        star.setTextSize(13);
        star.setTextColor(0xff73747a);
        meta.addView(star);
        TextView unstar = new TextView(this);
        unstar.setText("☆");
        unstar.setTextSize(13);
        unstar.setTextColor(0xff73747a);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.leftMargin = dp(25);
        meta.addView(unstar, ulp);
        right.addView(meta);

        LinearLayout more = new LinearLayout(this);
        more.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(18);
        View line = new View(this);
        line.setBackgroundColor(0xffe4e4e5);
        more.addView(line, new LinearLayout.LayoutParams(dp(20), dp(1)));
        TextView moreTv = new TextView(this);
        moreTv.setText("更多回复");
        moreTv.setTextSize(13);
        moreTv.setTextColor(0xff73747a);
        LinearLayout.LayoutParams mtlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mtlp.leftMargin = dp(8);
        more.addView(moreTv, mtlp);
        TextView arrow = new TextView(this);
        arrow.setText("▾");
        arrow.setTextSize(8);
        arrow.setTextColor(0xff73747a);
        more.addView(arrow);
        right.addView(more, mlp);

        card.addView(head);
        root.addView(card);

        root.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10)));

        // 浅色模式
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setGravity(Gravity.CENTER_VERTICAL);
        modeRow.setBackgroundColor(Color.WHITE);
        modeRow.setPadding(dp(15), dp(15), dp(15), dp(15));
        TextView modeLabel = new TextView(this);
        modeLabel.setText("浅色模式");
        modeLabel.setTextSize(14);
        modeLabel.setTextColor(0xff333333);
        modeRow.addView(modeLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(true);
        sw.setThumbTintList(android.content.res.ColorStateList.valueOf(0xffffffff));
        sw.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{0xffffda43, 0xffe5e5e5}));
        sw.setOnCheckedChangeListener((b, checked) -> {
            card.setBackgroundColor(checked ? Color.WHITE : 0xff161722);
            name.setTextColor(checked ? 0xffafb0b3 : 0xff8a8b90);
            content.setTextColor(checked ? 0xff161722 : 0xffeeeeee);
            time.setTextColor(checked ? 0xffb0b0b3 : 0xff8a8b90);
        });
        modeRow.addView(sw);
        root.addView(modeRow);

        // 保存截图
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setBackgroundColor(Color.WHITE);
        bottom.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        TextView save = new TextView(this);
        save.setText("保存截图");
        save.setTextSize(14);
        save.setTextColor(0xff333333);
        save.setGravity(Gravity.CENTER);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0xffffda43);
        sbg.setCornerRadius(dp(20));
        save.setBackground(sbg);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        slp.leftMargin = dp(20);
        slp.rightMargin = dp(20);
        bottom.addView(save, slp);
        save.setOnClickListener(v -> saveScreenshot());

        FrameLayout.LayoutParams rootLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(root, rootLp);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        blp.gravity = Gravity.BOTTOM;
        wrap.addView(bottom, blp);
        setContentView(wrap);
    }

    private void saveScreenshot() {
        try {
            Bitmap b = Bitmap.createBitmap(card.getWidth(), card.getHeight(), Bitmap.Config.ARGB_8888);
            card.draw(new Canvas(b));
            String name = "doutu_comment_" + System.currentTimeMillis() + ".png";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= 29) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            }
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("uri");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap circle(Bitmap src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawBitmap(src, new android.graphics.Rect(0, 0, size, size),
                new RectF(0, 0, size, size), p);
        p.setXfermode(new android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.DST_IN));
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        return b;
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
}
