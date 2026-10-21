package com.luoyun.doutu.features.wx;

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
 * 微信「收款助手」—— 按真机截图重画：
 * 顶栏（返回 / 微信收款助手 / 齿轮）+ 个人收款服务消息卡（收款到账通知 / 收款金额 /
 * 汇总 / 备注 / 查看详情 / 查看经营报表）+ 底部「个人收款 / 经营收款 / 更多」。
 */
public final class WxReceiptAssistantActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xfff7f7f7);
        w.setNavigationBarColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff2f2f2);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xfff7f7f7);
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
        title.setText("微信收款助手");
        title.setTextSize(17);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1f));
        TextView gear = new TextView(this);
        gear.setText("⚙");
        gear.setTextSize(20);
        gear.setTextColor(0xff333333);
        gear.setGravity(Gravity.CENTER);
        gear.setPadding(0, 0, dp(14), 0);
        bar.addView(gear, new LinearLayout.LayoutParams(dp(44), dp(56)));
        root.addView(bar);

        // 时间
        TextView time = new TextView(this);
        time.setText("19:22");
        time.setTextSize(12);
        time.setTextColor(0xff999999);
        time.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(24);
        root.addView(time, tlp);

        // 消息卡
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(Color.WHITE);
        cbg.setCornerRadius(dp(8));
        card.setBackground(cbg);
        card.setPadding(dp(16), dp(14), dp(16), dp(8));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(16);
        clp.rightMargin = dp(16);
        clp.topMargin = dp(10);
        root.addView(card, clp);

        TextView svc = new TextView(this);
        svc.setText("🟡 个人收款服务");
        svc.setTextSize(15);
        svc.setTextColor(0xff333333);
        card.addView(svc);

        TextView notice = new TextView(this);
        notice.setText("收款到账通知\n10月21日 19:22");
        notice.setTextSize(14);
        notice.setTextColor(0xff333333);
        notice.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(14);
        card.addView(notice, nlp);

        TextView amountLabel = new TextView(this);
        amountLabel.setText("收款金额");
        amountLabel.setTextSize(12);
        amountLabel.setTextColor(0xff888888);
        amountLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(12);
        card.addView(amountLabel, alp);

        TextView amount = new TextView(this);
        amount.setText("¥10022.00");
        amount.setTextSize(32);
        amount.setTextColor(0xff1a1a1a);
        amount.setGravity(Gravity.CENTER);
        card.addView(amount);

        TextView sum = new TextView(this);
        sum.setText("汇总 今日第1笔收款，共计¥100.00");
        sum.setTextSize(13);
        sum.setTextColor(0xff333333);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(10);
        card.addView(sum, slp);

        TextView note = new TextView(this);
        note.setText("备注 收款成功，已存入零钱。点击可查看详情");
        note.setTextSize(13);
        note.setTextColor(0xff333333);
        card.addView(note);

        View divider = new View(this);
        divider.setBackgroundColor(0xffeeeeee);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dlp.topMargin = dp(12);
        card.addView(divider, dlp);
        card.addView(row("查看详情"));
        card.addView(row("查看经营报表"));

        root.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setBackgroundColor(Color.WHITE);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        TextView grid = new TextView(this);
        grid.setText("▦");
        grid.setTextSize(20);
        grid.setTextColor(0xff333333);
        grid.setGravity(Gravity.CENTER);
        bottom.addView(grid, new LinearLayout.LayoutParams(dp(56), dp(52)));
        String[] tabs = {"个人收款", "≡ 经营收款", "≡ 更多"};
        for (String t : tabs) {
            TextView tv = new TextView(this);
            tv.setText(t);
            tv.setTextSize(14);
            tv.setTextColor(0xff333333);
            tv.setGravity(Gravity.CENTER);
            bottom.addView(tv, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        root.addView(bottom);

        setContentView(root);
    }

    private LinearLayout row(String name) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(14);
        n.setTextColor(0xff333333);
        row.addView(n, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText("›");
        v.setTextSize(14);
        v.setTextColor(0xffbbbbbb);
        row.addView(v);
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
