package com.luoyun.doutu.features.ali;

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
 * 支付宝「支付成功」页 —— 按真机 activity_ali_pay_success.xml 重画：
 * 顶栏「完成」+ 绿色对勾 支付成功 + ¥金额 + 交易方式 + 上传真实截图。
 */
public final class AliPaySuccessActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xffededed);
        w.setNavigationBarColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(56), 1f));
        TextView done = new TextView(this);
        done.setText("完成");
        done.setTextSize(15);
        done.setTextColor(0xff333333);
        done.setPadding(0, 0, dp(16), 0);
        done.setGravity(Gravity.CENTER);
        done.setOnClickListener(v -> finish());
        bar.addView(done);
        root.addView(bar);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(20), dp(60), dp(20), 0);

        TextView check = new TextView(this);
        check.setText("✓");
        check.setTextSize(30);
        check.setTextColor(Color.WHITE);
        check.setGravity(Gravity.CENTER);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(0xff07c160);
        cbg.setShape(GradientDrawable.OVAL);
        check.setBackground(cbg);
        body.addView(check, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView title = new TextView(this);
        title.setText("支付成功");
        title.setTextSize(20);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(14);
        body.addView(title, tlp);

        TextView amount = new TextView(this);
        amount.setText("¥ 0.00");
        amount.setTextSize(34);
        amount.setTextColor(0xff1a1a1a);
        amount.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(18);
        body.addView(amount, alp);

        LinearLayout method = new LinearLayout(this);
        method.setGravity(Gravity.CENTER_VERTICAL);
        method.setPadding(0, dp(24), 0, 0);
        TextView methodLabel = new TextView(this);
        methodLabel.setText("交易方式");
        methodLabel.setTextSize(14);
        methodLabel.setTextColor(0xff888888);
        method.addView(methodLabel);
        TextView methodValue = new TextView(this);
        methodValue.setText("余额");
        methodValue.setTextSize(14);
        methodValue.setTextColor(0xff333333);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.leftMargin = dp(12);
        method.addView(methodValue, mlp);
        body.addView(method);

        TextView upload = new TextView(this);
        upload.setText("上传真实截图");
        upload.setTextSize(14);
        upload.setTextColor(0xff3e7af6);
        upload.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.topMargin = dp(40);
        body.addView(upload, ulp);

        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
