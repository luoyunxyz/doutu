package com.luoyun.doutu.features.qq;

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
 * QQ「未读消息」—— 按真机截图重画：
 * 暗化的 QQ 首页 + 底部弹层（黄色「未读消息数量」页签 + 数字 + 键盘 + 完成）。
 */
public final class QqUnreadActivity extends Activity {
    private final StringBuilder typed = new StringBuilder("0");
    private TextView valueView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xff9e9e9e);
        w.setNavigationBarColor(Color.WHITE);
        w.getDecorView().setSystemUiVisibility(0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xffbdbdbd);

        // 暗化首页示意
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView name = new TextView(this);
        name.setText("微笑似年华々");
        name.setTextSize(17);
        name.setTextColor(0xff333333);
        header.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView plus = new TextView(this);
        plus.setText("＋");
        plus.setTextSize(22);
        plus.setTextColor(0xff333333);
        header.addView(plus);
        root.addView(header);

        TextView search = new TextView(this);
        search.setText("⌕  搜索");
        search.setTextSize(15);
        search.setTextColor(0xff666666);
        search.setGravity(Gravity.CENTER);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0xffe0e0e0);
        sbg.setCornerRadius(dp(8));
        search.setBackground(sbg);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        slp.leftMargin = dp(12);
        slp.rightMargin = dp(12);
        root.addView(search, slp);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView avatar = new TextView(this);
        avatar.setText("你");
        avatar.setTextSize(12);
        avatar.setTextColor(Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable abg = new GradientDrawable();
        abg.setColor(0xff90a4ae);
        abg.setShape(GradientDrawable.OVAL);
        avatar.setBackground(abg);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView chatName = new TextView(this);
        chatName.setText("你不懂我");
        chatName.setTextSize(16);
        chatName.setTextColor(0xff333333);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = dp(12);
        row.addView(chatName, clp);
        TextView time = new TextView(this);
        time.setText("17:48");
        time.setTextSize(12);
        time.setTextColor(0xff888888);
        row.addView(time);
        root.addView(row);

        TextView addChat = new TextView(this);
        addChat.setText("＋ 添加聊天");
        addChat.setTextSize(15);
        addChat.setTextColor(0xff333333);
        addChat.setGravity(Gravity.CENTER);
        addChat.setPadding(0, dp(10), 0, dp(20));
        root.addView(addChat);

        root.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底部弹层
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackgroundColor(Color.WHITE);

        TextView sheetTitle = new TextView(this);
        sheetTitle.setText("未读消息数量");
        sheetTitle.setTextSize(15);
        sheetTitle.setTextColor(0xff333333);
        sheetTitle.setGravity(Gravity.CENTER_VERTICAL);
        sheetTitle.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(0xffffda43);
        sheetTitle.setBackground(tbg);
        sheet.addView(sheetTitle, new LinearLayout.LayoutParams(dp(170), dp(44)));

        valueView = new TextView(this);
        valueView.setText("0");
        valueView.setTextSize(18);
        valueView.setTextColor(0xff333333);
        valueView.setPadding(dp(16), dp(14), dp(16), dp(14));
        sheet.addView(valueView);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.VERTICAL);
        String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {".", "0", "⌫"}};
        for (String[] r : rows) {
            LinearLayout rr = new LinearLayout(this);
            rr.setOrientation(LinearLayout.HORIZONTAL);
            for (String k : r) {
                TextView key = new TextView(this);
                key.setText(k);
                key.setTextSize(20);
                key.setTextColor(0xff333333);
                key.setGravity(Gravity.CENTER);
                key.setBackgroundColor(Color.WHITE);
                key.setOnClickListener(v -> onKey(k));
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
        side.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView done = new TextView(this);
        done.setText("完成");
        done.setTextSize(16);
        done.setTextColor(0xff333333);
        done.setGravity(Gravity.CENTER);
        done.setBackgroundColor(0xffffda43);
        done.setOnClickListener(v -> finish());
        side.addView(done, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        grid.addView(side, new LinearLayout.LayoutParams(0, dp(220), 1f));
        sheet.addView(grid);

        root.addView(sheet);
        setContentView(root);
    }

    private void onKey(String key) {
        if ("⌫".equals(key)) {
            if (typed.length() > 0) typed.deleteCharAt(typed.length() - 1);
        } else if (".".equals(key)) {
            if (typed.indexOf(".") < 0 && typed.length() > 0) typed.append('.');
        } else {
            if (typed.length() < 6) typed.append(key);
        }
        if (typed.length() == 0) typed.append('0');
        valueView.setText(typed.toString());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
