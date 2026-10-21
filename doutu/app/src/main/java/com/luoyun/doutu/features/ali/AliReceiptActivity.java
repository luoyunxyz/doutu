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
 * 支付宝「收款记录」（商家收款记录）—— 按真机截图重画：
 * 顶栏 收款记录（返回 / 统计 / …）+ 自定义查询行 + 空列表灰底。
 */
public final class AliReceiptActivity extends Activity {
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
        title.setText("收款记录");
        title.setTextSize(18);
        title.setTextColor(0xff333333);
        bar.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(56), 1f));
        TextView stat = new TextView(this);
        stat.setText("统计");
        stat.setTextSize(16);
        stat.setTextColor(0xff333333);
        stat.setPadding(0, 0, dp(18), 0);
        stat.setGravity(Gravity.CENTER);
        bar.addView(stat);
        TextView more = new TextView(this);
        more.setText("···");
        more.setTextSize(16);
        more.setTextColor(0xff333333);
        more.setPadding(0, 0, dp(14), 0);
        more.setGravity(Gravity.CENTER);
        bar.addView(more);
        root.addView(bar);

        LinearLayout query = new LinearLayout(this);
        query.setGravity(Gravity.CENTER_VERTICAL);
        query.setBackgroundColor(0xfff7f7f7);
        query.setPadding(dp(16), dp(10), dp(16), dp(10));
        TextView icon = new TextView(this);
        icon.setText("▤");
        icon.setTextSize(16);
        icon.setTextColor(0xff888888);
        query.addView(icon);
        TextView label = new TextView(this);
        label.setText("自定义查询");
        label.setTextSize(14);
        label.setTextColor(0xff333333);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.leftMargin = dp(8);
        query.addView(label, llp);
        root.addView(query);

        View gap = new View(this);
        gap.setBackgroundColor(0xfff5f5f5);
        root.addView(gap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
