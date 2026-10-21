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
import android.widget.TextView;

/**
 * 微信「零钱提现」—— 按真机截图重画：
 * 顶栏（返回 / 零钱提现 / …）+ 到账银行卡（请选择银行卡，2小时内到账）+ 提现金额 ¥ +
 * 当前零钱余额0.00元，全部提现 + 键盘（1-9 / 0 / . / ⌫ / 提现）。
 */
public final class WxWithdrawActivity extends Activity {
    private final StringBuilder typed = new StringBuilder();
    private TextView amountView;
    private TextView submit;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xffededed);
        w.setNavigationBarColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f5f5);

        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(0xffededed);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(0xff333333);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new FrameLayout.LayoutParams(dp(44), dp(56)));
        TextView title = new TextView(this);
        title.setText("零钱提现");
        title.setTextSize(17);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        TextView more = new TextView(this);
        more.setText("···");
        more.setTextSize(18);
        more.setTextColor(0xff333333);
        more.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(56));
        mlp.gravity = Gravity.END;
        mlp.rightMargin = dp(14);
        bar.addView(more, mlp);
        root.addView(bar);

        // 到账银行卡
        LinearLayout bank = new LinearLayout(this);
        bank.setGravity(Gravity.CENTER_VERTICAL);
        bank.setPadding(dp(16), dp(18), dp(16), dp(18));
        bank.setBackgroundColor(0xfff5f5f5);
        TextView bankLabel = new TextView(this);
        bankLabel.setText("到账银行卡");
        bankLabel.setTextSize(15);
        bankLabel.setTextColor(0xff333333);
        bank.addView(bankLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout bankInfo = new LinearLayout(this);
        bankInfo.setOrientation(LinearLayout.VERTICAL);
        TextView bankName = new TextView(this);
        bankName.setText("请选择银行卡");
        bankName.setTextSize(15);
        bankName.setTextColor(0xff333333);
        bankInfo.addView(bankName);
        TextView bankEta = new TextView(this);
        bankEta.setText("2小时内到账");
        bankEta.setTextSize(12);
        bankEta.setTextColor(0xff999999);
        bankEta.setGravity(Gravity.END);
        bankInfo.addView(bankEta);
        bank.addView(bankInfo);
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(16);
        arrow.setTextColor(0xffbbbbbb);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = dp(8);
        bank.addView(arrow, alp);
        root.addView(bank);

        // 提现金额卡
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(Color.WHITE);
        cbg.setCornerRadius(dp(12));
        card.setBackground(cbg);
        card.setPadding(dp(16), dp(20), dp(16), dp(20));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(12);
        clp.rightMargin = dp(12);
        clp.topMargin = dp(6);
        root.addView(card, clp);

        TextView amountLabel = new TextView(this);
        amountLabel.setText("提现金额");
        amountLabel.setTextSize(14);
        amountLabel.setTextColor(0xff333333);
        card.addView(amountLabel);
        LinearLayout moneyRow = new LinearLayout(this);
        moneyRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mlp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        mlp2.topMargin = dp(6);
        TextView yen = new TextView(this);
        yen.setText("¥");
        yen.setTextSize(34);
        yen.setTextColor(0xff1a1a1a);
        moneyRow.addView(yen);
        amountView = new TextView(this);
        amountView.setText("");
        amountView.setTextSize(34);
        amountView.setTextColor(0xff1a1a1a);
        LinearLayout.LayoutParams avlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        avlp.leftMargin = dp(10);
        moneyRow.addView(amountView, avlp);
        card.addView(moneyRow, mlp2);

        View divider = new View(this);
        divider.setBackgroundColor(0xffeeeeee);
        card.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout balanceRow = new LinearLayout(this);
        balanceRow.setGravity(Gravity.CENTER_VERTICAL);
        balanceRow.setPadding(0, dp(14), 0, 0);
        TextView balance = new TextView(this);
        balance.setText("当前零钱余额0.00元，");
        balance.setTextSize(14);
        balance.setTextColor(0xff888888);
        balanceRow.addView(balance);
        TextView all = new TextView(this);
        all.setText("全部提现");
        all.setTextSize(14);
        all.setTextColor(0xff576b95);
        all.setOnClickListener(v -> {
            typed.setLength(0);
            typed.append("0.00");
            amountView.setText("0.00");
        });
        balanceRow.addView(all);
        card.addView(balanceRow);

        root.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 键盘
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setBackgroundColor(0xfff2f2f2);
        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.VERTICAL);
        String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"0", ".", "⌫"}};
        for (String[] row : rows) {
            LinearLayout rr = new LinearLayout(this);
            rr.setOrientation(LinearLayout.HORIZONTAL);
            for (String k : row) {
                TextView key = new TextView(this);
                key.setText(k);
                key.setTextSize(22);
                key.setTextColor(0xff333333);
                key.setGravity(Gravity.CENTER);
                GradientDrawable kbg = new GradientDrawable();
                kbg.setColor(Color.WHITE);
                kbg.setCornerRadius(dp(6));
                key.setBackground(kbg);
                key.setOnClickListener(v -> onKey(k));
                LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                klp.setMargins(dp(3), dp(3), dp(3), dp(3));
                rr.addView(key, klp);
            }
            keys.addView(rr, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        grid.addView(keys, new LinearLayout.LayoutParams(0, dp(240), 3f));

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(dp(3), dp(3), dp(3), dp(3));
        submit = new TextView(this);
        submit.setText("提现");
        submit.setTextSize(16);
        submit.setTextColor(Color.WHITE);
        submit.setGravity(Gravity.CENTER);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0xffb7e6c8);
        sbg.setCornerRadius(dp(6));
        submit.setBackground(sbg);
        side.addView(submit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        grid.addView(side, new LinearLayout.LayoutParams(0, dp(240), 1f));
        root.addView(grid);

        setContentView(root);
    }

    private void onKey(String key) {
        if ("⌫".equals(key)) {
            if (typed.length() > 0) typed.deleteCharAt(typed.length() - 1);
        } else if (".".equals(key)) {
            if (typed.indexOf(".") < 0 && typed.length() > 0) typed.append('.');
        } else {
            if (typed.length() < 12) typed.append(key);
        }
        amountView.setText(typed.toString());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
