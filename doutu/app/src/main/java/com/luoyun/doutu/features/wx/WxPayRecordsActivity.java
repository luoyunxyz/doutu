package com.luoyun.doutu.features.wx;

import android.app.Activity;
import android.graphics.Color;
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
 * 微信「支付」记录页 —— 按真机重画：
 * 灰栏（返回 / 微信支付 / 搜索 / 齿轮）+ 两张记录卡（提现金额 ¥100.00 + 明细行）+
 * 底部「我的账单 / 支付服务」。
 */
public final class WxPayRecordsActivity extends Activity {
    private static final int BG = 0xffededed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(Color.WHITE);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // 顶栏
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(BG);
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(30);
        back.setTextColor(0xff333333);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new FrameLayout.LayoutParams(dp(56), dp(56)));
        TextView title = new TextView(this);
        title.setText("微信支付");
        title.setTextSize(16);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        TextView search = new TextView(this);
        search.setText("⌕");
        search.setTextSize(20);
        search.setTextColor(0xff333333);
        search.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(40), dp(56));
        slp.gravity = Gravity.END;
        slp.rightMargin = dp(40);
        bar.addView(search, slp);
        TextView gear = new TextView(this);
        gear.setText("⚙");
        gear.setTextSize(20);
        gear.setTextColor(0xff333333);
        gear.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(40), dp(56));
        glp.gravity = Gravity.END;
        glp.rightMargin = dp(4);
        bar.addView(gear, glp);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(4), dp(14), dp(14));
        scroll.addView(list);

        // 卡1：提现通知
        LinearLayout card1 = card();
        card1.addView(label("提现金额", 13, 0xff888888, Gravity.CENTER));
        TextView money1 = new TextView(this);
        money1.setText("￥100.00");
        money1.setTextSize(34);
        money1.setTextColor(0xff1a1a1a);
        money1.setGravity(Gravity.CENTER);
        card1.addView(money1);
        card1.addView(row("提现银行", "交通银行(2353)"));
        card1.addView(row("提现时间", "2026-10-21 19:21:02"));
        card1.addView(row("预计到账时间", "2026-10-21 19:21:05"));
        card1.addView(divider());
        card1.addView(row("查看详情", "›"));
        list.addView(card1, cardLp());

        // 卡2：零钱提现到账
        LinearLayout card2 = card();
        TextView t2 = label("零钱提现到账", 16, 0xff1a1a1a, Gravity.START);
        card2.addView(t2);
        card2.addView(label("提现金额", 13, 0xff888888, Gravity.CENTER));
        TextView money2 = new TextView(this);
        money2.setText("￥100.00");
        money2.setTextSize(34);
        money2.setTextColor(0xff1a1a1a);
        money2.setGravity(Gravity.CENTER);
        card2.addView(money2);
        card2.addView(row("提现银行", "交通银行(2353)"));
        card2.addView(row("提现时间", "2026-10-21 19:21:02"));
        card2.addView(row("到账时间", "2026-10-21 19:21:05"));
        card2.addView(row("备注", "你的零钱提现已到账至银行卡"));
        card2.addView(divider());
        card2.addView(row("查看详情", "›"));
        list.addView(card2, cardLp());

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底栏
        LinearLayout bottom = new LinearLayout(this);
        bottom.setBackgroundColor(Color.WHITE);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        TextView grid = new TextView(this);
        grid.setText("▦");
        grid.setTextSize(20);
        grid.setTextColor(0xff333333);
        grid.setGravity(Gravity.CENTER);
        bottom.addView(grid, new LinearLayout.LayoutParams(dp(56), dp(48)));
        TextView bills = new TextView(this);
        bills.setText("我的账单");
        bills.setTextSize(15);
        bills.setTextColor(0xff333333);
        bills.setGravity(Gravity.CENTER);
        bottom.addView(bills, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        TextView services = new TextView(this);
        services.setText("支付服务");
        services.setTextSize(15);
        services.setTextColor(0xff333333);
        services.setGravity(Gravity.CENTER);
        bottom.addView(services, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        root.addView(bottom);

        setContentView(root);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(16), dp(16), dp(16), dp(8));
        return card;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        return lp;
    }

    private TextView label(String text, int size, int color, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setGravity(gravity);
        tv.setPadding(0, dp(6), 0, dp(4));
        return tv;
    }

    private LinearLayout row(String name, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(14);
        n.setTextColor(0xff888888);
        n.setWidth(dp(90));
        row.addView(n);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(14);
        v.setTextColor(0xff333333);
        row.addView(v, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(0xffeeeeee);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
