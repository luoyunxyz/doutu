package com.luoyun.doutu.features.wx;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
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
 * 微信「转账」页 —— 按真机截图重画：
 * 灰栏（返回 / 转账）+ 白卡（转账金额 ￥0.00 / 新增转账说明 / 切换发送人 /
 * 收钱间隔 55秒 / 转账类型 收入·支出）+ 底部键盘（转账金额 / 转账说明 两个页签，
 * 1-9、.、0、⌫、下一项、完成）。
 */
public final class WxTransferActivity extends Activity {
    private static final int BG = 0xffededed;

    private final StringBuilder amount = new StringBuilder("0.00");
    private TextView amountView;
    private boolean editingAmount = true;

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
        root.setBackgroundColor(Color.WHITE);

        // 顶部灰栏
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
        title.setText("转账");
        title.setTextSize(16);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        root.addView(bar);

        // 金额卡
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(18), dp(16), dp(18));
        card.setBackgroundColor(Color.WHITE);
        TextView label = new TextView(this);
        label.setText("转账金额");
        label.setTextSize(13);
        label.setTextColor(0xff888888);
        card.addView(label);
        amountView = new TextView(this);
        amountView.setText("￥ 0.00");
        amountView.setTextSize(38);
        amountView.setTextColor(0xff333333);
        amountView.getPaint().setFakeBoldText(true);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(10);
        card.addView(amountView, alp);
        TextView note = new TextView(this);
        note.setText("新增转账说明");
        note.setTextSize(14);
        note.setTextColor(0xff576b95);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(18);
        card.addView(note, nlp);
        root.addView(card);

        root.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));

        // 切换发送人
        root.addView(row("切换发送人", "", true));
        root.addView(row("收钱间隔", "55秒", false));
        // 转账类型
        LinearLayout typeRow = new LinearLayout(this);
        typeRow.setGravity(Gravity.CENTER_VERTICAL);
        typeRow.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView typeLabel = new TextView(this);
        typeLabel.setText("转账类型");
        typeLabel.setTextSize(15);
        typeLabel.setTextColor(0xff333333);
        typeRow.addView(typeLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        RadioButton income = new RadioButton(this);
        income.setText("收入");
        income.setTextSize(14);
        income.setChecked(false);
        RadioButton expend = new RadioButton(this);
        expend.setText("支出");
        expend.setTextSize(14);
        expend.setChecked(true);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.leftMargin = dp(18);
        typeRow.addView(income);
        typeRow.addView(expend, elp);
        income.setOnCheckedChangeListener((b, c) -> { if (c) expend.setChecked(false); });
        expend.setOnCheckedChangeListener((b, c) -> { if (c) income.setChecked(false); });
        root.addView(typeRow);

        // 弹簧，把键盘推到底部
        root.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底部键盘
        root.addView(buildKeypad());
        setContentView(root);
    }

    private LinearLayout row(String name, String value, boolean arrow) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(15);
        tv.setTextColor(0xff333333);
        row.addView(tv, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView val = new TextView(this);
        val.setText(value.isEmpty() ? "›" : value + " ›");
        val.setTextSize(14);
        val.setTextColor(0xff888888);
        row.addView(val);
        return row;
    }

    private LinearLayout buildKeypad() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xfff7f7f7);

        // 页签
        LinearLayout tabs = new LinearLayout(this);
        tabs.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        TextView amountTab = tab("转账金额", true);
        TextView noteTab = tab("转账说明", false);
        amountTab.setOnClickListener(v -> { editingAmount = true; amountView.setTextColor(0xff333333); });
        noteTab.setOnClickListener(v -> { editingAmount = false; Toast.makeText(this, "转账说明", Toast.LENGTH_SHORT).show(); });
        tabs.addView(amountTab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        tabs.addView(noteTab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        box.addView(tabs);

        // 输入行
        LinearLayout input = new LinearLayout(this);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setBackgroundColor(Color.WHITE);
        input.setPadding(dp(16), dp(6), dp(16), dp(6));
        TextView yen = new TextView(this);
        yen.setText("￥");
        yen.setTextSize(16);
        yen.setTextColor(0xffc9a227);
        yen.setGravity(Gravity.CENTER);
        GradientDrawable c = new GradientDrawable();
        c.setColor(0xffffd84d);
        c.setShape(GradientDrawable.OVAL);
        yen.setBackground(c);
        input.addView(yen, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView typed = new TextView(this);
        typed.setText("0");
        typed.setTextSize(18);
        typed.setTextColor(0xff333333);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(10);
        input.addView(typed, tlp);
        TextView clear = new TextView(this);
        clear.setText("✕");
        clear.setTextSize(12);
        clear.setTextColor(Color.WHITE);
        clear.setGravity(Gravity.CENTER);
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(0xffcccccc);
        cg.setShape(GradientDrawable.OVAL);
        clear.setBackground(cg);
        clear.setOnClickListener(v -> { amount.setLength(0); amount.append("0"); typed.setText("0"); amountView.setText("￥ 0.00"); });
        input.addView(clear, new LinearLayout.LayoutParams(dp(20), dp(20)));
        box.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        // 键盘
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.VERTICAL);
        String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {".", "0", "⌫"}};
        for (int r = 0; r < 4; r++) {
            LinearLayout rr = new LinearLayout(this);
            rr.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 3; col++) {
                final String k = rows[r][col];
                TextView key = new TextView(this);
                key.setText(k);
                key.setTextSize(20);
                key.setTextColor(0xff333333);
                key.setGravity(Gravity.CENTER);
                key.setBackgroundColor(Color.WHITE);
                key.setOnClickListener(v -> onKey(k, typed));
                LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                klp.setMargins(1, 1, 1, 1);
                rr.addView(key, klp);
            }
            keys.addView(rr, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        grid.addView(keys, new LinearLayout.LayoutParams(0, dp(220), 3f));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        TextView next = new TextView(this);
        next.setText("下一项");
        next.setTextSize(16);
        next.setTextColor(0xff333333);
        next.setGravity(Gravity.CENTER);
        next.setBackgroundColor(Color.WHITE);
        next.setOnClickListener(v -> Toast.makeText(this, "下一项", Toast.LENGTH_SHORT).show());
        TextView done = new TextView(this);
        done.setText("完成");
        done.setTextSize(16);
        done.setTextColor(0xff333333);
        done.setGravity(Gravity.CENTER);
        done.setBackgroundColor(0xffffda43);
        done.setOnClickListener(v -> {
            Toast.makeText(this, "转账已提交", Toast.LENGTH_SHORT).show();
            finish();
        });
        actions.addView(next, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        actions.addView(done, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        grid.addView(actions, new LinearLayout.LayoutParams(0, dp(220), 1f));

        box.addView(grid);
        return box;
    }

    private TextView tab(String text, boolean active) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(active ? 0xff333333 : 0xff666666);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(active ? 0xffffda43 : 0xffffffff);
        tv.setBackground(bg);
        tv.getPaint().setFakeBoldText(active);
        return tv;
    }

    private void onKey(String key, TextView typed) {
        if ("⌫".equals(key)) {
            if (amount.length() > 0) amount.deleteCharAt(amount.length() - 1);
        } else if (".".equals(key)) {
            if (amount.indexOf(".") < 0 && amount.length() > 0) amount.append('.');
        } else {
            if (amount.length() < 12) amount.append(key);
        }
        if (amount.length() == 0) amount.append('0');
        String raw = amount.toString();
        typed.setText(raw);
        try {
            double v = Double.parseDouble(raw);
            amountView.setText(String.format(java.util.Locale.US, "￥ %.2f", v));
        } catch (Exception ignored) { }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
