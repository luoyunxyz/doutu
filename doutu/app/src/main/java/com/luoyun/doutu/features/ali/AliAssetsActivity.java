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
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 支付宝「总资产」页 —— 按真机 activity_ali_totle_assets.xml 重画：
 * 蓝渐变头（返回 / 总资产 / 服务）+ 我的资产卡（昨日收益、灵活取用/稳健理财/进阶理财、进度条）
 * + 资产明细 + 我的三笔钱 + 我的保障（相互宝/保险）+ 我的额度 + 资产服务。
 */
public final class AliAssetsActivity extends Activity {
    private static final int INK = 0xff333333;
    private static final int SUB = 0xff888888;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xff3e7af6);
        w.setNavigationBarColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f5f5);

        // 蓝色头
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable hbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff3e7af6, 0xff72b0f8});
        header.setBackground(hbg);
        header.setPadding(dp(16), dp(10), dp(16), dp(18));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(Color.WHITE);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(32), dp(36)));
        TextView title = new TextView(this);
        title.setText("总资产");
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(36), 1f));
        TextView service = new TextView(this);
        service.setText("服务");
        service.setTextSize(15);
        service.setTextColor(Color.WHITE);
        bar.addView(service);
        header.addView(bar);

        TextView myAsset = new TextView(this);
        myAsset.setText("我的资产(元)");
        myAsset.setTextSize(13);
        myAsset.setTextColor(0xccffffff);
        LinearLayout.LayoutParams malp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        malp.topMargin = dp(14);
        header.addView(myAsset, malp);
        TextView total = new TextView(this);
        total.setText("00.0");
        total.setTextSize(40);
        total.setTextColor(Color.WHITE);
        header.addView(total);
        TextView profit = new TextView(this);
        profit.setText("昨日收益  0.00");
        profit.setTextSize(12);
        profit.setTextColor(0xccffffff);
        header.addView(profit);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(10), dp(12), dp(16));
        scroll.addView(body);

        // 三笔钱：灵活取用 / 稳健理财 / 进阶理财
        LinearLayout three = card(body);
        three.addView(label("灵活取用  69.80", 14));
        three.addView(label("稳健理财  0.00", 14));
        three.addView(label("进阶理财  0.00", 14));
        View progress = new View(this);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(0xff3e7af6);
        pbg.setCornerRadius(dp(3));
        progress.setBackground(pbg);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(120), dp(6));
        plp.topMargin = dp(10);
        three.addView(progress, plp);

        card(body).addView(row("资产明细", "›"));
        LinearLayout mine = card(body);
        mine.addView(label("我的三笔钱", 16));
        mine.addView(row("总资产(元)", "0.00"));
        mine.addView(row("去看看更多理财产品", "›"));
        LinearLayout protect = card(body);
        protect.addView(label("账户保障免费升级", 16));
        protect.addView(row("我的保障", "›"));
        protect.addView(row("相互宝", "›"));
        protect.addView(row("保险", "›"));
        card(body).addView(row("我的额度", "›"));
        card(body).addView(row("资产服务", "›"));

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
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        parent.addView(card, lp);
        return card;
    }

    private TextView label(String text, int size) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(size);
        tv.setTextColor(INK);
        tv.setPadding(0, dp(4), 0, dp(4));
        return tv;
    }

    private LinearLayout row(String name, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(14);
        n.setTextColor(INK);
        row.addView(n, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(14);
        v.setTextColor(SUB);
        row.addView(v);
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
