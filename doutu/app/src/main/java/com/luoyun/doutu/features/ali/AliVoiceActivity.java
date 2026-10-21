package com.luoyun.doutu.features.ali;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 「支付宝到账语音」—— 按真机截图重画：
 * 黄栏（返回 / 支付宝到账语音）+ 「定时播放：（请先不要关闭软件）」+ 空状态
 * 「当前暂无待播放语音」+ 黄色「新增」。
 */
public final class AliVoiceActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xffffd84a);
        w.setNavigationBarColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f5f5);

        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(0xffffd84a);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(28);
        back.setTextColor(0xff333333);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new FrameLayout.LayoutParams(dp(48), dp(56)));
        TextView title = new TextView(this);
        title.setText("支付宝到账语音");
        title.setTextSize(17);
        title.setTextColor(0xff1a1a1a);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        root.addView(bar);

        TextView hint = new TextView(this);
        hint.setText("定时播放：（请先不要关闭软件）");
        hint.setTextSize(15);
        hint.setTextColor(0xff333333);
        hint.setBackgroundColor(0xfff2f2f2);
        hint.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.addView(hint);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);
        body.setBackgroundColor(Color.WHITE);
        body.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView ghost = new TextView(this);
        ghost.setText("☹");
        ghost.setTextSize(56);
        ghost.setTextColor(0xffcccccc);
        ghost.setGravity(Gravity.CENTER);
        body.addView(ghost);
        TextView empty = new TextView(this);
        empty.setText("当前暂无待播放语音");
        empty.setTextSize(14);
        empty.setTextColor(0xff999999);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(10), 0, 0);
        body.addView(empty);
        root.addView(body);

        TextView add = new TextView(this);
        add.setText("新增");
        add.setTextSize(16);
        add.setTextColor(0xff1a1a1a);
        add.setGravity(Gravity.CENTER);
        GradientDrawable abg = new GradientDrawable();
        abg.setColor(0xffffd84a);
        abg.setCornerRadius(dp(6));
        add.setBackground(abg);
        add.setOnClickListener(v -> Toast.makeText(this, "新增到账语音", Toast.LENGTH_SHORT).show());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        alp.leftMargin = dp(16);
        alp.rightMargin = dp(16);
        alp.topMargin = dp(10);
        alp.bottomMargin = dp(16);
        root.addView(add, alp);

        setContentView(root);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
