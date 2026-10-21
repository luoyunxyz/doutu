package com.luoyun.doutu.features.qq;

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
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

/**
 * QQ「转账」—— 按真机截图重画：
 * 顶栏（返回 / 转账 / 发送）+ 转账金额 ¥0.0 + 新增转账说明 + 选择发送人
 * （对方发 / 自己发）+ 切换收钱人 + 底部「转账金额 / 转账说明」页签 + 键盘 + 下一项 / 完成。
 */
public final class QqTransferV2Activity extends Activity {
    private final StringBuilder typed = new StringBuilder();
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
        title.setText("转账");
        title.setTextSize(17);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        TextView send = new TextView(this);
        send.setText("发送");
        send.setTextSize(15);
        send.setTextColor(0xff333333);
        send.setGravity(Gravity.CENTER);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0xffffda43);
        sbg.setCornerRadius(dp(6));
        send.setBackground(sbg);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(72), dp(34));
        slp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        slp.rightMargin = dp(12);
        send.setOnClickListener(v -> {
            Toast.makeText(this, "转账已发送", Toast.LENGTH_SHORT).show();
            finish();
        });
        bar.addView(send, slp);
        root.addView(bar);

        // 金额
        LinearLayout amountBox = new LinearLayout(this);
        amountBox.setOrientation(LinearLayout.VERTICAL);
        amountBox.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView label = new TextView(this);
        label.setText("转账金额");
        label.setTextSize(15);
        label.setTextColor(0xff333333);
        amountBox.addView(label);
        LinearLayout money = new LinearLayout(this);
        money.setGravity(Gravity.CENTER_VERTICAL);
        TextView yen = new TextView(this);
        yen.setText("¥");
        yen.setTextSize(30);
        yen.setTextColor(0xff1a1a1a);
        money.addView(yen);
        amountView = new TextView(this);
        amountView.setText("0.0");
        amountView.setTextSize(40);
        amountView.setTextColor(0xff1a1a1a);
        LinearLayout.LayoutParams avlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        avlp.leftMargin = dp(8);
        money.addView(amountView, avlp);
        amountBox.addView(money);
        TextView note = new TextView(this);
        note.setText("新增转账说明");
        note.setTextSize(14);
        note.setTextColor(0xff576b95);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(18);
        amountBox.addView(note, nlp);
        root.addView(amountBox);

        // 选择发送人
        TextView pickLabel = new TextView(this);
        pickLabel.setText("选择发送人");
        pickLabel.setTextSize(15);
        pickLabel.setTextColor(0xff333333);
        pickLabel.setPadding(dp(16), dp(16), dp(16), dp(8));
        root.addView(pickLabel);

        LinearLayout pick = new LinearLayout(this);
        pick.setGravity(Gravity.CENTER_VERTICAL);
        pick.setPadding(dp(16), dp(8), dp(16), dp(8));
        RadioButton other = radio("对方发", true);
        RadioButton self = radio("自己发", false);
        other.setOnCheckedChangeListener((b, c) -> { if (c) self.setChecked(false); });
        self.setOnCheckedChangeListener((b, c) -> { if (c) other.setChecked(false); });
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER);
        left.addView(other);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER);
        right.addView(self);
        pick.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pick.addView(right, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(pick);

        TextView switchPayee = new TextView(this);
        switchPayee.setText("切换收钱人 ›");
        switchPayee.setTextSize(14);
        switchPayee.setTextColor(0xff666666);
        switchPayee.setPadding(dp(16), dp(10), dp(16), dp(10));
        root.addView(switchPayee);

        root.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 页签
        LinearLayout tabs = new LinearLayout(this);
        TextView amountTab = tab("转账金额", true);
        TextView noteTab = tab("转账说明", false);
        tabs.addView(amountTab, new LinearLayout.LayoutParams(0, dp(44), 1f));
        tabs.addView(noteTab, new LinearLayout.LayoutParams(0, dp(44), 1f));
        root.addView(tabs);

        // 输入行
        LinearLayout input = new LinearLayout(this);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setBackgroundColor(Color.WHITE);
        input.setPadding(dp(16), dp(6), dp(16), dp(6));
        TextView y = new TextView(this);
        y.setText("￥");
        y.setTextSize(14);
        y.setTextColor(0xffc9a227);
        y.setGravity(Gravity.CENTER);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(0xffffd84d);
        cbg.setShape(GradientDrawable.OVAL);
        y.setBackground(cbg);
        input.addView(y, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView typedView = new TextView(this);
        typedView.setText("0");
        typedView.setTextSize(18);
        typedView.setTextColor(0xff333333);
        LinearLayout.LayoutParams tlp2 = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp2.leftMargin = dp(10);
        input.addView(typedView, tlp2);
        TextView clear = new TextView(this);
        clear.setText("✕");
        clear.setTextSize(12);
        clear.setTextColor(Color.WHITE);
        clear.setGravity(Gravity.CENTER);
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(0xffcccccc);
        cg.setShape(GradientDrawable.OVAL);
        clear.setBackground(cg);
        clear.setOnClickListener(v -> { typed.setLength(0); typedView.setText("0"); amountView.setText("0.0"); });
        input.addView(clear, new LinearLayout.LayoutParams(dp(20), dp(20)));
        root.addView(input, new LinearLayout.LayoutParams(
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
        TextView next = new TextView(this);
        next.setText("下一项");
        next.setTextSize(16);
        next.setTextColor(0xff333333);
        next.setGravity(Gravity.CENTER);
        next.setBackgroundColor(Color.WHITE);
        TextView done = new TextView(this);
        done.setText("完成");
        done.setTextSize(16);
        done.setTextColor(0xff333333);
        done.setGravity(Gravity.CENTER);
        done.setBackgroundColor(0xffffda43);
        done.setOnClickListener(v -> finish());
        side.addView(next, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        side.addView(done, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        grid.addView(side, new LinearLayout.LayoutParams(0, dp(220), 1f));
        root.addView(grid);

        setContentView(root);
    }

    private RadioButton radio(String text, boolean checked) {
        RadioButton rb = new RadioButton(this);
        rb.setText(text);
        rb.setTextSize(14);
        rb.setChecked(checked);
        return rb;
    }

    private TextView tab(String text, boolean active) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(0xff333333);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(active ? 0xffffda43 : Color.WHITE);
        tv.setBackground(bg);
        tv.getPaint().setFakeBoldText(active);
        return tv;
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
        typedView.setText(typed.toString());
        amountView.setText(typed.toString());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
