package com.luoyun.doutu.features.ali;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 支付宝「消息盒子」（支付助手）—— 按真机 activity_ali_msg_box.xml 重画：
 * 顶栏 消息盒子 + 服务消息 / 支付消息 两个页签 + 暂无消息。
 */
public final class AliMsgBoxActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xffededed);
        w.setNavigationBarColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f5f5);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(0xff333333);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(56)));
        TextView title = new TextView(this);
        title.setText("消息盒子");
        title.setTextSize(17);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(44), dp(56)));
        root.addView(bar);

        // 页签
        LinearLayout tabs = new LinearLayout(this);
        tabs.setBackgroundColor(Color.WHITE);
        tabs.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        TextView service = tab("服务消息", true);
        TextView pay = tab("支付消息", false);
        tabs.addView(service, new LinearLayout.LayoutParams(0, dp(44), 1f));
        tabs.addView(pay, new LinearLayout.LayoutParams(0, dp(44), 1f));
        root.addView(tabs);

        TextView empty = new TextView(this);
        empty.setText("暂无消息");
        empty.setTextSize(14);
        empty.setTextColor(0xff999999);
        empty.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(empty, elp);

        setContentView(root);
    }

    private TextView tab(String text, boolean active) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(active ? 0xff3e7af6 : 0xff666666);
        tv.setGravity(Gravity.CENTER);
        if (active) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.TRANSPARENT);
            tv.setBackground(bg);
        }
        tv.getPaint().setFakeBoldText(active);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
