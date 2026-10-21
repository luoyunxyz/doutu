package com.luoyun.doutu.features.ali;

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
 * 支付宝「支付助手」—— 按真机截图重画：
 * 顶栏 支付助手（返回 / 齿轮）+ 空列表灰底 + 底部「我的账单 / 退款查询 / 支付设置」。
 */
public final class AliPayAssistantActivity extends Activity {
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
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(0xff333333);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(56)));
        TextView title = new TextView(this);
        title.setText("支付助手");
        title.setTextSize(18);
        title.setTextColor(0xff333333);
        bar.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(56), 1f));
        TextView gear = new TextView(this);
        gear.setText("⚙");
        gear.setTextSize(20);
        gear.setTextColor(0xff333333);
        gear.setGravity(Gravity.CENTER);
        gear.setPadding(0, 0, dp(14), 0);
        bar.addView(gear, new LinearLayout.LayoutParams(dp(44), dp(56)));
        root.addView(bar);

        View space = new View(this);
        root.addView(space, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setBackgroundColor(Color.WHITE);
        bottom.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        String[] tabs = {"我的账单", "退款查询", "支付设置"};
        for (int i = 0; i < tabs.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(tabs[i]);
            tv.setTextSize(15);
            tv.setTextColor(0xff333333);
            tv.setGravity(Gravity.CENTER);
            bottom.addView(tv, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            if (i < tabs.length - 1) {
                View line = new View(this);
                line.setBackgroundColor(0xffeeeeee);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                        dp(1), dp(20));
                llp.gravity = Gravity.CENTER_VERTICAL;
                bottom.addView(line, llp);
            }
        }
        root.addView(bottom);

        setContentView(root);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
