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
 * 支付宝「账单详情」页 —— 按真机 activity_ali_bill_transfer_detail.xml 重画：
 * 账单详情 / 小歪 +0.01 交易成功 / 创建时间 / 收款方式 / 支付宝积分（立即领取1积分）/
 * 转账备注 / 对方账户 / 订单号 / 账单分类 / 标签和备注 / 计入收支 + 往来记录、流水证明、
 * 电子回单、投诉、AA 收款、再转一笔。
 */
public final class AliBillDetailActivity extends Activity {
    private static final String[][] ROWS = {
            {"创建时间", "2019-07-29 19:48"},
            {"收款方式", "余额"},
            {"支付宝积分", "立即领取1积分"},
            {"转账备注", "转账"},
            {"对方账户", "184***@qq.com"},
            {"订单号", "20190624200040011100210083872644"},
            {"账单分类", "其他"},
            {"标签和备注", "添加"},
    };

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
        title.setText("账单详情");
        title.setTextSize(17);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(44), dp(56)));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);

        // 头卡：姓名 + 金额 + 状态
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER);
        head.setBackgroundColor(Color.WHITE);
        head.setPadding(0, dp(24), 0, dp(20));
        TextView name = new TextView(this);
        name.setText("小歪");
        name.setTextSize(15);
        name.setTextColor(0xff333333);
        head.addView(name);
        TextView amount = new TextView(this);
        amount.setText("+0.01");
        amount.setTextSize(32);
        amount.setTextColor(0xff1a1a1a);
        amount.setPadding(0, dp(8), 0, 0);
        head.addView(amount);
        TextView status = new TextView(this);
        status.setText("交易成功");
        status.setTextSize(13);
        status.setTextColor(0xff888888);
        status.setPadding(0, dp(6), 0, 0);
        head.addView(status);
        body.addView(head);

        body.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setBackgroundColor(Color.WHITE);
        rows.setPadding(dp(16), dp(4), dp(16), dp(4));
        for (String[] r : ROWS) {
            rows.addView(row(r[0], r[1]));
        }
        body.addView(rows);

        body.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setBackgroundColor(Color.WHITE);
        actions.setPadding(dp(16), dp(4), dp(16), dp(4));
        String[] acts = {"查看往来记录", "往来流水证明", "申请电子回单", "对此订单有疑问", "投诉", "AA收款", "再转一笔"};
        for (String a : acts) {
            actions.addView(row(a, "›"));
        }
        body.addView(actions);

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private LinearLayout row(String name, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(14);
        n.setTextColor(0xff888888);
        row.addView(n, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(14);
        v.setTextColor("立即领取1积分".equals(value) ? 0xff3e7af6 : 0xff333333);
        row.addView(v);
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
