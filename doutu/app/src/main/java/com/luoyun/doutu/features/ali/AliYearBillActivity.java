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
 * 支付宝「年账单」页 —— 按真机 activity_ali_year_bill.xml 重画：
 * 这一年你在支付宝，至少为世界减少 30.01kg 碳排放；
 * 你所坚持的爱心公益……感谢努力生活的你；
 * 这一年你在支付宝：支出了 ¥125,116.68 / 收到了 / 收支分析。
 */
public final class AliYearBillActivity extends Activity {
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
                new int[]{0xff3e7af6, 0xff72b0f8});
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
        TextView title = new TextView(this);
        title.setText("年账单");
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(36), 1f));
        header.addView(bar);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(12), dp(12), dp(16));
        scroll.addView(body);

        LinearLayout carbon = card(body);
        TextView c1 = new TextView(this);
        c1.setText("这一年你在支付宝，至少为世界减少 30.01kg 碳排放。");
        c1.setTextSize(15);
        c1.setTextColor(0xff333333);
        c1.setLineSpacing(dp(6), 1f);
        carbon.addView(c1);
        TextView c2 = new TextView(this);
        c2.setText("你所坚持的爱心公益，累积成一股力量，默默地推动着世界变好，感谢努力生活的你。");
        c2.setTextSize(13);
        c2.setTextColor(0xff888888);
        c2.setLineSpacing(dp(5), 1f);
        LinearLayout.LayoutParams c2lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        c2lp.topMargin = dp(10);
        carbon.addView(c2, c2lp);

        LinearLayout summary = card(body);
        TextView thisYear = new TextView(this);
        thisYear.setText("这一年你在支付宝");
        thisYear.setTextSize(14);
        thisYear.setTextColor(0xff888888);
        summary.addView(thisYear);
        TextView paid = new TextView(this);
        paid.setText("支出了  ¥125,116.68");
        paid.setTextSize(26);
        paid.setTextColor(0xff1a1a1a);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(10);
        summary.addView(paid, plp);
        TextView got = new TextView(this);
        got.setText("收到了  ¥0.00");
        got.setTextSize(18);
        got.setTextColor(0xff333333);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(10);
        summary.addView(got, glp);
        TextView analysis = new TextView(this);
        analysis.setText("收支分析");
        analysis.setTextSize(15);
        analysis.setTextColor(Color.WHITE);
        analysis.setGravity(Gravity.CENTER);
        GradientDrawable abg = new GradientDrawable();
        abg.setColor(0xff3e7af6);
        abg.setCornerRadius(dp(20));
        analysis.setBackground(abg);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        alp.topMargin = dp(20);
        summary.addView(analysis, alp);

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
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        parent.addView(card, lp);
        return card;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
