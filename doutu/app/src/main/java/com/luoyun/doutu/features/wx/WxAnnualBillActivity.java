package com.luoyun.doutu.features.wx;

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
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 微信「年账单」—— 按真机截图重画：
 * 绿色头（× / 账单 / 月账单·年账单）+ 2026年10月 ⌄ + 支出·收入·其他 +
 * 共支出41笔，合计 ¥557.44 + 记账本提示卡 + 支出对比柱状图 + 支出排行榜（添加排行榜数据）。
 */
public final class WxAnnualBillActivity extends Activity {
    private static final String[] MONTHS = {"5月", "6月", "7月", "8月", "9月", "10月"};
    private static final float[] VALUES = {731.81f, 926.42f, 972.07f, 625.75f, 557.47f, 767.39f};
    private static final int GREEN = 0xff4bbf7a;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(GREEN);
        w.setNavigationBarColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f5f5);

        // 绿色头
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable hbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff4bbf7a, 0xff57c98a});
        header.setBackground(hbg);
        header.setPadding(dp(16), dp(10), dp(16), dp(16));

        FrameLayout bar = new FrameLayout(this);
        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextSize(18);
        close.setTextColor(Color.WHITE);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> finish());
        bar.addView(close, new FrameLayout.LayoutParams(dp(40), dp(36)));
        TextView title = new TextView(this);
        title.setText("账单");
        title.setTextSize(17);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        header.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

        // 页签
        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        tabLp.topMargin = dp(8);
        TextView monthly = tab("月账单", true);
        TextView yearly = tab("年账单", false);
        tabs.addView(monthly);
        tagsSpacer(tabs);
        tabs.addView(yearly);
        header.addView(tabs, tabLp);

        // 月份 + 类型
        LinearLayout filter = new LinearLayout(this);
        filter.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        flp.topMargin = dp(10);
        TextView month = new TextView(this);
        month.setText("2026年10月 ⌄");
        month.setTextSize(15);
        month.setTextColor(Color.WHITE);
        filter.addView(month, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        filter.addView(chip("支出", true));
        filter.addView(chip("收入", false));
        filter.addView(chip("其他", false));
        header.addView(filter, flp);

        TextView totalLabel = new TextView(this);
        totalLabel.setText("共支出41笔，合计");
        totalLabel.setTextSize(13);
        totalLabel.setTextColor(0xccffffff);
        LinearLayout.LayoutParams tll = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tll.topMargin = dp(6);
        header.addView(totalLabel, tll);
        TextView total = new TextView(this);
        total.setText("¥ 557.44");
        total.setTextSize(34);
        total.setTextColor(Color.WHITE);
        header.addView(total);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(10), dp(12), dp(20));
        scroll.addView(body);

        // 记账本提示
        LinearLayout tip = new LinearLayout(this);
        tip.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(Color.WHITE);
        tbg.setCornerRadius(dp(10));
        tip.setBackground(tbg);
        tip.setPadding(dp(14), dp(14), dp(14), dp(14));
        TextView tipText = new TextView(this);
        tipText.setText("📗 使用记账本，查看分类统计(餐饮、交通等)");
        tipText.setTextSize(13);
        tipText.setTextColor(0xff333333);
        tip.addView(tipText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(16);
        arrow.setTextColor(0xffbbbbbb);
        tip.addView(arrow);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipLp.bottomMargin = dp(10);
        body.addView(tip, tipLp);

        // 支出对比
        TextView compare = new TextView(this);
        compare.setText("支出对比");
        compare.setTextSize(16);
        compare.setTextColor(0xff333333);
        compare.setPadding(dp(6), dp(10), 0, dp(10));
        body.addView(compare);

        LinearLayout chart = new LinearLayout(this);
        chart.setGravity(Gravity.BOTTOM);
        chart.setPadding(0, dp(10), 0, 0);
        chart.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(240)));
        float max = 0;
        for (float v : VALUES) max = Math.max(max, v);
        for (int i = 0; i < VALUES.length; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            TextView value = new TextView(this);
            value.setText("¥" + String.format(java.util.Locale.US, "%.2f", VALUES[i]));
            value.setTextSize(11);
            value.setTextColor(0xff999999);
            col.addView(value);
            View barView = new View(this);
            GradientDrawable bbg = new GradientDrawable();
            bbg.setColor(i == VALUES.length - 1 ? 0xff4bbf7a : 0xffcdebd8);
            barView.setBackground(bbg);
            int h = Math.round(160 * (VALUES[i] / max));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    dp(34), dp(h));
            blp.topMargin = dp(6);
            col.addView(barView, blp);
            TextView label = new TextView(this);
            label.setText(MONTHS[i]);
            label.setTextSize(12);
            label.setTextColor(0xff888888);
            label.setPadding(0, dp(6), 0, 0);
            col.addView(label);
            chart.addView(col, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        body.addView(chart);

        // 支出排行榜
        TextView rank = new TextView(this);
        rank.setText("支出排行榜");
        rank.setTextSize(16);
        rank.setTextColor(0xff333333);
        rank.setPadding(dp(6), dp(24), 0, dp(10));
        body.addView(rank);
        TextView add = new TextView(this);
        add.setText("添加排行榜数据");
        add.setTextSize(15);
        add.setTextColor(0xff333333);
        add.setGravity(Gravity.CENTER);
        GradientDrawable abg = new GradientDrawable();
        abg.setColor(0xffffda43);
        abg.setCornerRadius(dp(26));
        add.setBackground(abg);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        alp.leftMargin = dp(20);
        alp.rightMargin = dp(20);
        alp.topMargin = dp(10);
        body.addView(add, alp);

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void tagsSpacer(LinearLayout tabs) {
        tabs.addView(new View(this), new LinearLayout.LayoutParams(dp(40), dp(40)));
    }

    private TextView tab(String text, boolean active) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(active ? Color.WHITE : 0xbbffffff);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(6), 0, dp(6), dp(4));
        tv.getPaint().setFakeBoldText(active);
        return tv;
    }

    private TextView chip(String text, boolean active) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        if (active) {
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(dp(1), Color.WHITE);
        } else {
            bg.setColor(0x00000000);
        }
        bg.setCornerRadius(dp(14));
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(58), dp(28));
        lp.leftMargin = dp(8);
        tv.setLayoutParams(lp);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
