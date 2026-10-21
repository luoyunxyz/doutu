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
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 支付宝「我的」页 —— 按真机 activity_ali_mine_set.xml 重画：
 * 支付宝会员 / 积分 / 余额，用户保护中心、商家服务，蚂蚁保、账户保险状态，
 * 花呗、余利宝、相互宝、借呗、网商贷、网商银行、余额宝、宝藏信用卡、我的小程序、我的客服。
 */
public final class AliMineActivity extends Activity {
    private static final String[][] GROUPS = {
            {"用户保护中心", "商家服务"},
            {"蚂蚁保", "账户保险状态"},
            {"花呗", "余利宝", "相互宝", "借呗", "网商贷", "网商银行", "余额宝", "宝藏信用卡"},
            {"我的小程序", "我的客服"},
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xff3e7af6);
        w.setNavigationBarColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f5f5);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable hbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff3e7af6, 0xff8cc3fa});
        header.setBackground(hbg);
        header.setPadding(dp(16), dp(10), dp(16), dp(20));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(Color.WHITE);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(32), dp(36)));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(36), 1f));
        header.addView(bar);

        TextView member = new TextView(this);
        member.setText("支付宝会员");
        member.setTextSize(15);
        member.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(8);
        header.addView(member, mlp);
        TextView points = new TextView(this);
        points.setText("积分待领取: 0    积分: 0");
        points.setTextSize(12);
        points.setTextColor(0xccffffff);
        header.addView(points);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(10), dp(12), dp(16));
        scroll.addView(body);

        LinearLayout top = card(body);
        top.addView(row("余额", "0.00"));
        for (String[] group : GROUPS) {
            LinearLayout card = card(body);
            for (String name : group) {
                card.addView(row(name, "›"));
            }
        }

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(10));
        card.setBackground(bg);
        card.setPadding(dp(14), dp(4), dp(14), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        parent.addView(card, lp);
        return card;
    }

    private LinearLayout row(String name, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(15);
        n.setTextColor(0xff333333);
        row.addView(n, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(14);
        v.setTextColor(0xff888888);
        row.addView(v);
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
