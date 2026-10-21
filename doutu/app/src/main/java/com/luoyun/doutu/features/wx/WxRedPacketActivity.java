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
import android.widget.Toast;

/**
 * 微信「发红包」页 —— 按真机截图重画：
 * 顶栏（返回 / 发红包）+ 红包金额 ¥0 + 恭喜发财，大吉大利 + 发送人（切换发送人）+
 * 黄色「金额」条 + 输入行 + 键盘（1-9 / . / 0 / ⌫ / 完成）。
 */
public final class WxRedPacketActivity extends Activity {
    private final StringBuilder typed = new StringBuilder("0");
    private TextView amountView;

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
        title.setText("发红包");
        title.setTextSize(17);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        root.addView(bar);

        // 金额
        LinearLayout amountBox = new LinearLayout(this);
        amountBox.setOrientation(LinearLayout.VERTICAL);
        amountBox.setBackgroundColor(0xfff5f5f5);
        amountBox.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView amountLabel = new TextView(this);
        amountLabel.setText("红包金额");
        amountLabel.setTextSize(13);
        amountLabel.setTextColor(0xff888888);
        amountBox.addView(amountLabel);
        LinearLayout moneyRow = new LinearLayout(this);
        moneyRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView yen = new TextView(this);
        yen.setText("¥");
        yen.setTextSize(34);
        yen.setTextColor(0xff333333);
        moneyRow.addView(yen);
        amountView = new TextView(this);
        amountView.setText("0");
        amountView.setTextSize(40);
        amountView.setTextColor(0xff333333);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = dp(8);
        moneyRow.addView(amountView, alp);
        amountBox.addView(moneyRow);
        root.addView(amountBox);

        // 祝福语
        TextView wish = new TextView(this);
        wish.setText("恭喜发财，大吉大利");
        wish.setTextSize(15);
        wish.setTextColor(0xff333333);
        wish.setBackgroundColor(0xfff5f5f5);
        wish.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.addView(wish);

        // 发送人
        LinearLayout sender = new LinearLayout(this);
        sender.setGravity(Gravity.CENTER_VERTICAL);
        sender.setBackgroundColor(0xfff5f5f5);
        sender.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView avatar = new TextView(this);
        avatar.setText("群");
        avatar.setTextSize(10);
        avatar.setTextColor(0xff666666);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable abg = new GradientDrawable();
        abg.setColor(0xffeeeeee);
        abg.setCornerRadius(dp(4));
        avatar.setBackground(abg);
        sender.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView name = new TextView(this);
        name.setText("北方佳人.");
        name.setTextSize(15);
        name.setTextColor(0xff333333);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = dp(10);
        sender.addView(name, nlp);
        TextView switchSender = new TextView(this);
        switchSender.setText("切换发送人");
        switchSender.setTextSize(14);
        switchSender.setTextColor(0xff333333);
        sender.addView(switchSender);
        root.addView(sender);

        root.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 黄色金额条
        TextView barTitle = new TextView(this);
        barTitle.setText("金额");
        barTitle.setTextSize(15);
        barTitle.setTextColor(0xff333333);
        barTitle.setGravity(Gravity.CENTER);
        GradientDrawable ybg = new GradientDrawable();
        ybg.setColor(0xffffda43);
        barTitle.setBackground(ybg);
        root.addView(barTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        // 输入行
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setBackgroundColor(Color.WHITE);
        inputRow.setPadding(dp(16), dp(8), dp(16), dp(8));
        TextView y = new TextView(this);
        y.setText("￥");
        y.setTextSize(14);
        y.setTextColor(0xffc9a227);
        y.setGravity(Gravity.CENTER);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(0xffffd84d);
        cbg.setShape(GradientDrawable.OVAL);
        y.setBackground(cbg);
        inputRow.addView(y, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView typedView = new TextView(this);
        typedView.setText("0");
        typedView.setTextSize(18);
        typedView.setTextColor(0xff333333);
        LinearLayout.LayoutParams tlp2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp2.leftMargin = dp(10);
        inputRow.addView(typedView, tlp2);
        TextView clear = new TextView(this);
        clear.setText("✕");
        clear.setTextSize(12);
        clear.setTextColor(Color.WHITE);
        clear.setGravity(Gravity.CENTER);
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(0xffcccccc);
        cg.setShape(GradientDrawable.OVAL);
        clear.setBackground(cg);
        clear.setOnClickListener(v -> { typed.setLength(0); typed.append('0'); typedView.setText("0"); });
        inputRow.addView(clear, new LinearLayout.LayoutParams(dp(20), dp(20)));
        root.addView(inputRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        // 键盘
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.VERTICAL);
        String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {".", "0", "⌫"}};
        for (String[] row : rows) {
            LinearLayout rr = new LinearLayout(this);
            rr.setOrientation(LinearLayout.HORIZONTAL);
            for (String k : row) {
                TextView key = new TextView(this);
                key.setText(k);
                key.setTextSize(20);
                key.setTextColor(0xff333333);
                key.setGravity(Gravity.CENTER);
                key.setBackgroundColor(Color.WHITE);
                key.setOnClickListener(v -> onKey(k, typedView));
                LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                klp.setMargins(1, 1, 1, 1);
                rr.addView(key, klp);
            }
            keys.addView(rr, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        grid.addView(keys, new LinearLayout.LayoutParams(0, dp(220), 3f));
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        TextView done = new TextView(this);
        done.setText("完成");
        done.setTextSize(16);
        done.setTextColor(0xff333333);
        done.setGravity(Gravity.CENTER);
        done.setBackgroundColor(0xffffda43);
        done.setOnClickListener(v -> {
            Toast.makeText(this, "红包已塞入", Toast.LENGTH_SHORT).show();
            finish();
        });
        side.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        side.addView(done, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        grid.addView(side, new LinearLayout.LayoutParams(0, dp(220), 1f));
        root.addView(grid);

        setContentView(root);
    }

    private void onKey(String key, TextView typedView) {
        if ("⌫".equals(key)) {
            if (typed.length() > 0) typed.deleteCharAt(typed.length() - 1);
        } else if (".".equals(key)) {
            if (typed.indexOf(".") < 0 && typed.length() > 0) typed.append('.');
        } else {
            if (typed.length() < 10) typed.append(key);
        }
        if (typed.length() == 0) typed.append('0');
        String raw = typed.toString();
        typedView.setText(raw);
        amountView.setText(raw);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
