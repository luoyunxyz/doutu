package com.luoyun.doutu.features.ali;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 支付宝「提现」页 —— 按真机 activity_ali_withdraw.xml 重画：
 * 提现 / 完成 + 到账银行卡（中国工商银行 尾号9988 储蓄卡 立即到账）+
 * 提现金额（￥ / 全部提现 / 可用余额）+ 到账金额 / 服务费（本次提现免费）+
 * 添加银行卡红包 + 预计两小时内到账，确认提现。
 */
public final class AliWithdrawActivity extends Activity {
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
        title.setText("提现");
        title.setTextSize(17);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1f));
        TextView done = new TextView(this);
        done.setText("完成");
        done.setTextSize(15);
        done.setTextColor(0xff333333);
        done.setPadding(0, 0, dp(16), 0);
        done.setGravity(Gravity.CENTER);
        done.setOnClickListener(v -> finish());
        bar.addView(done);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);

        // 到账银行卡
        LinearLayout bank = card(body);
        bank.setOrientation(LinearLayout.VERTICAL);
        TextView bankLabel = new TextView(this);
        bankLabel.setText("到账银行卡");
        bankLabel.setTextSize(13);
        bankLabel.setTextColor(0xff888888);
        bank.addView(bankLabel);
        LinearLayout bankRow = new LinearLayout(this);
        bankRow.setGravity(Gravity.CENTER_VERTICAL);
        bankRow.setPadding(0, dp(10), 0, 0);
        TextView bankIcon = new TextView(this);
        bankIcon.setText("银");
        bankIcon.setTextSize(12);
        bankIcon.setTextColor(Color.WHITE);
        bankIcon.setGravity(Gravity.CENTER);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setColor(0xffe53935);
        ibg.setCornerRadius(dp(6));
        bankIcon.setBackground(ibg);
        bankRow.addView(bankIcon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout bankInfo = new LinearLayout(this);
        bankInfo.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bilp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bilp.leftMargin = dp(10);
        TextView bankName = new TextView(this);
        bankName.setText("中国工商银行");
        bankName.setTextSize(15);
        bankName.setTextColor(0xff333333);
        bankInfo.addView(bankName);
        TextView bankNo = new TextView(this);
        bankNo.setText("尾号9988 储蓄卡");
        bankNo.setTextSize(12);
        bankNo.setTextColor(0xff888888);
        bankInfo.addView(bankNo);
        bankRow.addView(bankInfo, bilp);
        TextView fast = new TextView(this);
        fast.setText("立即到账");
        fast.setTextSize(12);
        fast.setTextColor(0xff3e7af6);
        bankRow.addView(fast);
        bank.addView(bankRow);

        // 金额
        LinearLayout amount = card(body);
        amount.setOrientation(LinearLayout.VERTICAL);
        TextView amountLabel = new TextView(this);
        amountLabel.setText("提现金额");
        amountLabel.setTextSize(13);
        amountLabel.setTextColor(0xff888888);
        amount.addView(amountLabel);
        LinearLayout moneyRow = new LinearLayout(this);
        moneyRow.setGravity(Gravity.CENTER_VERTICAL);
        moneyRow.setPadding(0, dp(8), 0, 0);
        TextView yen = new TextView(this);
        yen.setText("¥");
        yen.setTextSize(28);
        yen.setTextColor(0xff333333);
        moneyRow.addView(yen);
        EditText input = new EditText(this);
        input.setText("0.00");
        input.setTextSize(32);
        input.setTextColor(0xff333333);
        input.setBackground(null);
        input.setPadding(dp(6), 0, 0, 0);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        moneyRow.addView(input, ilp);
        TextView all = new TextView(this);
        all.setText("全部提现");
        all.setTextSize(13);
        all.setTextColor(0xff3e7af6);
        all.setOnClickListener(v -> input.setText("0.10"));
        moneyRow.addView(all);
        amount.addView(moneyRow);
        TextView available = new TextView(this);
        available.setText("可用余额0.10元");
        available.setTextSize(12);
        available.setTextColor(0xff888888);
        amount.addView(available);

        // 到账金额 / 服务费
        LinearLayout fee = card(body);
        fee.addView(row("到账金额", "0.00"));
        fee.addView(row("服务费", "0.00"));
        TextView free = new TextView(this);
        free.setText("本次提现免费");
        free.setTextSize(12);
        free.setTextColor(0xff888888);
        fee.addView(free);

        // 添加银行卡红包
        LinearLayout promo = card(body);
        TextView promoText = new TextView(this);
        promoText.setText("添加提现到账银行卡得5-10元红包，");
        promoText.setTextSize(13);
        promoText.setTextColor(0xff333333);
        promo.addView(promoText);
        TextView addNow = new TextView(this);
        addNow.setText("立即添加");
        addNow.setTextSize(13);
        addNow.setTextColor(0xff3e7af6);
        promo.addView(addNow);

        // 确认提现
        TextView confirm = new TextView(this);
        confirm.setText("预计两小时内到账，确认提现");
        confirm.setTextSize(16);
        confirm.setTextColor(Color.WHITE);
        confirm.setGravity(Gravity.CENTER);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(0xff3e7af6);
        cbg.setCornerRadius(dp(6));
        confirm.setBackground(cbg);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        clp.leftMargin = dp(16);
        clp.rightMargin = dp(16);
        clp.topMargin = dp(16);
        confirm.setOnClickListener(v -> {
            Toast.makeText(this, "提现申请已提交", Toast.LENGTH_SHORT).show();
            finish();
        });
        body.addView(confirm, clp);

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        card.setBackground(bg);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        parent.addView(card, lp);
        return card;
    }

    private LinearLayout row(String name, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(14);
        n.setTextColor(0xff333333);
        row.addView(n, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(14);
        v.setTextColor(0xff333333);
        row.addView(v);
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
