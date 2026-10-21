package com.luoyun.doutu.features.wx;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;

/**
 * 「创建单聊」的打招呼聊天页 —— 按真机截图：
 * 打招呼气泡 + 「以上是打招呼的内容」+「你已添加了X，现在可以开始聊天了」；
 * 首次进入弹出「切换“发送者”的 1 2 种方法」提示（只保留 1、2，去掉了第 3 条）；
 * 点昵称或摇一摇，昵称短暂显示「对方正在输入......」，退出不保存。
 */
public final class WxChatGreetingActivity extends Activity implements SensorEventListener {
    private TextView title;
    private String name;
    private float lastX, lastY, lastZ;
    private long lastShake;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable restoreTitle = () -> title.setText(name);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        name = getIntent().getStringExtra("name");
        if (name == null || name.isEmpty()) name = "残阳€";
        String avatarPath = getIntent().getStringExtra("avatar");
        boolean isNew = getIntent().getBooleanExtra("isNew", true);

        Window w = getWindow();
        w.setStatusBarColor(0xffededed);
        w.setNavigationBarColor(Color.WHITE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xffededed);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        // 顶栏
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(0xffededed);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(0xff333333);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new FrameLayout.LayoutParams(dp(44), dp(56)));
        title = new TextView(this);
        title.setText(name);
        title.setTextSize(17);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        title.setOnClickListener(v -> showTyping());
        bar.addView(title, tlp);
        TextView more = new TextView(this);
        more.setText("···");
        more.setTextSize(18);
        more.setTextColor(0xff333333);
        more.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(56));
        mlp.gravity = Gravity.END;
        mlp.rightMargin = dp(14);
        bar.addView(more, mlp);
        page.addView(bar);

        // 时间
        TextView time = new TextView(this);
        time.setText(nowTime());
        time.setTextSize(12);
        time.setTextColor(0xff999999);
        time.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeLp.topMargin = dp(16);
        page.addView(time, timeLp);

        // 打招呼气泡
        LinearLayout msg = new LinearLayout(this);
        msg.setGravity(Gravity.TOP);
        msg.setPadding(dp(14), dp(14), dp(14), 0);
        ImageView avatar = new ImageView(this);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bmp = load(avatarPath);
        if (bmp != null) avatar.setImageBitmap(bmp);
        msg.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView bubble = new TextView(this);
        bubble.setText("我是" + name);
        bubble.setTextSize(15);
        bubble.setTextColor(0xff333333);
        bubble.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bbg = new GradientDrawable();
        bbg.setColor(Color.WHITE);
        bbg.setCornerRadius(dp(6));
        bubble.setBackground(bbg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = dp(10);
        msg.addView(bubble, blp);
        page.addView(msg);

        if (isNew) {
            TextView t1 = new TextView(this);
            t1.setText("以上是打招呼的内容");
            t1.setTextSize(13);
            t1.setTextColor(0xff999999);
            t1.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            l1.topMargin = dp(26);
            page.addView(t1, l1);
            TextView t2 = new TextView(this);
            t2.setText("你已添加了" + name + "，现在可以开始聊天了。");
            t2.setTextSize(13);
            t2.setTextColor(0xff999999);
            t2.setGravity(Gravity.CENTER);
            t2.setPadding(0, dp(14), 0, 0);
            page.addView(t2);
        }

        page.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底部输入栏
        LinearLayout input = new LinearLayout(this);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setBackgroundColor(0xfff7f7f7);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView voice = new TextView(this);
        voice.setText("◎");
        voice.setTextSize(20);
        voice.setTextColor(0xff333333);
        input.addView(voice, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView field = new TextView(this);
        field.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        flp.leftMargin = dp(8);
        input.addView(field, flp);
        TextView emoji = new TextView(this);
        emoji.setText("☺");
        emoji.setTextSize(20);
        emoji.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(dp(36), dp(36));
        elp.leftMargin = dp(8);
        input.addView(emoji, elp);
        TextView plus = new TextView(this);
        plus.setText("＋");
        plus.setTextSize(22);
        plus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(36), dp(36));
        plp.leftMargin = dp(8);
        input.addView(plus, plp);
        page.addView(input);
        root.addView(page);

        // 教程浮层（只保留 1、2）
        View scrim = new View(this);
        scrim.setBackgroundColor(0x99000000);
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(Color.WHITE);
        sbg.setCornerRadius(dp(12));
        sheet.setBackground(sbg);
        sheet.setPadding(dp(20), dp(18), dp(20), dp(18));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(24);
        slp.rightMargin = dp(24);
        slp.topMargin = dp(110);
        root.addView(sheet, slp);

        LinearLayout headRow = new LinearLayout(this);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView headText = new TextView(this);
        headText.setText("切换“发送者”的");
        headText.setTextSize(16);
        headText.setTextColor(0xff333333);
        headRow.addView(headText);
        headRow.addView(badge("1"));
        headRow.addView(badge("2"));
        TextView headTail = new TextView(this);
        headTail.setText("种方法");
        headTail.setTextSize(16);
        headTail.setTextColor(0xff333333);
        headRow.addView(headTail);
        sheet.addView(headRow);

        sheet.addView(step("1", "点击上方的“昵称”", null));
        sheet.addView(orLine());
        sheet.addView(step("2", "“摇一摇”手机", "🤝"));
        sheet.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10)));

        TextView know = new TextView(this);
        know.setText("知道了");
        know.setTextSize(16);
        know.setTextColor(0xff333333);
        know.setGravity(Gravity.CENTER);
        GradientDrawable kbg = new GradientDrawable();
        kbg.setColor(0xffffda43);
        kbg.setCornerRadius(dp(26));
        know.setBackground(kbg);
        know.setOnClickListener(v -> {
            root.removeView(sheet);
            root.removeView(scrim);
        });
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        klp.topMargin = dp(10);
        sheet.addView(know, klp);

        TextView noMore = new TextView(this);
        noMore.setText("○ 不再提示");
        noMore.setTextSize(14);
        noMore.setTextColor(0xff666666);
        noMore.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(10);
        sheet.addView(noMore, nlp);

        setContentView(root);

        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm != null) {
            Sensor accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accel != null) sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    /** 昵称临时改成「对方正在输入......」，3 秒后恢复；不写任何存储。 */
    private void showTyping() {
        title.setText("对方正在输入......");
        handler.removeCallbacks(restoreTitle);
        handler.postDelayed(restoreTitle, 3000);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0], y = event.values[1], z = event.values[2];
        float delta = Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ);
        lastX = x;
        lastY = y;
        lastZ = z;
        if (delta > 18f) {
            long now = System.currentTimeMillis();
            if (now - lastShake > 1200) {
                lastShake = now;
                showTyping();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(restoreTitle);
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm != null) sm.unregisterListener(this);
    }

    private TextView badge(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(0xff333333);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xffffda43);
        bg.setShape(GradientDrawable.OVAL);
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(24), dp(24));
        lp.leftMargin = dp(5);
        lp.rightMargin = dp(5);
        tv.setLayoutParams(lp);
        return tv;
    }

    private LinearLayout step(String num, String text, String icon) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));
        row.addView(badge(num));
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(0xff333333);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(10);
        row.addView(tv, tlp);
        if (icon != null) {
            TextView ic = new TextView(this);
            ic.setText(icon);
            ic.setTextSize(26);
            row.addView(ic);
        }
        return row;
    }

    private TextView orLine() {
        TextView tv = new TextView(this);
        tv.setText("— — — — — 或 — — — — —");
        tv.setTextSize(12);
        tv.setTextColor(0xffbbbbbb);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private String nowTime() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return String.format(java.util.Locale.US, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY),
                c.get(java.util.Calendar.MINUTE));
    }

    private Bitmap load(String path) {
        if (path == null) return null;
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
