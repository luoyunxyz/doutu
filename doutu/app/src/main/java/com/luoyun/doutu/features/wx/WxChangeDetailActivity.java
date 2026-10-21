package com.luoyun.doutu.features.wx;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 微信「零钱明细」—— 按真机截图重画：
 * 灰底顶栏（× / 零钱明细）+ 月份下拉「2019年6月 ⌄」+ 空列表。
 */
public final class WxChangeDetailActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xfff7f7f7);
        w.setNavigationBarColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f5f5);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xfff7f7f7);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextSize(18);
        close.setTextColor(0xff333333);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> finish());
        bar.addView(close, new LinearLayout.LayoutParams(dp(44), dp(56)));
        TextView title = new TextView(this);
        title.setText("零钱明细");
        title.setTextSize(17);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(44), dp(56)));
        root.addView(bar);

        LinearLayout month = new LinearLayout(this);
        month.setGravity(Gravity.CENTER_VERTICAL);
        month.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView monthText = new TextView(this);
        monthText.setText("2019年6月 ⌄");
        monthText.setTextSize(16);
        monthText.setTextColor(0xff333333);
        month.addView(monthText);
        root.addView(month);

        View rest = new View(this);
        rest.setBackgroundColor(0xfff5f5f5);
        root.addView(rest, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
