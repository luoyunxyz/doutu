package com.luoyun.doutu.features.tools;

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
import android.widget.Switch;
import android.widget.TextView;

/**
 * 「自动替换」—— 按真机 activity_auto_replace_header_list 重画：
 * 选择需要替换的头像（头像网格）+ 原始头像 / 替换头像（选择）+ 替换截图内其他头像开关 +
 * 人工智能一键合成 + 正在合成。
 */
public final class AutoReplaceActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xfff7f7f7);
        w.setNavigationBarColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f5f5);

        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(0xfff7f7f7);
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
        title.setText("自动替换");
        title.setTextSize(17);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        root.addView(bar);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(Color.WHITE);
        cbg.setCornerRadius(dp(10));
        card.setBackground(cbg);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(12);
        clp.rightMargin = dp(12);
        clp.topMargin = dp(10);
        root.addView(card, clp);

        TextView pick = new TextView(this);
        pick.setText("选择需要替换的头像");
        pick.setTextSize(15);
        pick.setTextColor(0xff333333);
        card.addView(pick);

        // 头像网格
        LinearLayout grid = new LinearLayout(this);
        grid.setPadding(0, dp(10), 0, 0);
        for (int i = 0; i < 4; i++) {
            View cell = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setColor(0xffe6e6e6);
            g.setCornerRadius(dp(6));
            cell.setBackground(g);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(56), dp(56));
            glp.rightMargin = dp(10);
            grid.addView(cell, glp);
        }
        card.addView(grid);

        card.addView(row("原始头像", "选择"));
        card.addView(row("替换头像", "选择"));

        LinearLayout switchRow = new LinearLayout(this);
        switchRow.setGravity(Gravity.CENTER_VERTICAL);
        switchRow.setPadding(0, dp(10), 0, dp(10));
        TextView switchLabel = new TextView(this);
        switchLabel.setText("替换截图内其他头像");
        switchLabel.setTextSize(14);
        switchLabel.setTextColor(0xff333333);
        switchRow.addView(switchLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        switchRow.addView(new Switch(this));
        card.addView(switchRow);

        TextView compose = new TextView(this);
        compose.setText("人工智能一键合成");
        compose.setTextSize(16);
        compose.setTextColor(Color.WHITE);
        compose.setGravity(Gravity.CENTER);
        GradientDrawable gbg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xff5b8def, 0xff8f6bff});
        gbg.setCornerRadius(dp(24));
        compose.setBackground(gbg);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        klp.topMargin = dp(14);
        compose.setOnClickListener(v -> status.setText("正在合成…"));
        card.addView(compose, klp);

        status = new TextView(this);
        status.setText("");
        status.setTextSize(13);
        status.setTextColor(0xff888888);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(10), 0, 0);
        card.addView(status);

        setContentView(root);
    }

    private LinearLayout row(String name, String action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(14);
        n.setTextColor(0xff333333);
        row.addView(n, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView a = new TextView(this);
        a.setText(action);
        a.setTextSize(13);
        a.setTextColor(0xff3e7af6);
        row.addView(a);
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
