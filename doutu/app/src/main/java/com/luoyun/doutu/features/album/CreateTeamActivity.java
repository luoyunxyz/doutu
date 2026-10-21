package com.luoyun.doutu.features.album;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 「创建团队」页 —— 照 歪点点 4.0.5.39 真机截图重画：黄栏 + 团队名称 + 输入框 + 提交。 */
public final class CreateTeamActivity extends Activity {
    private static final int YELLOW = 0xfff9db61;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(YELLOW);
        w.setNavigationBarColor(Color.WHITE);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(YELLOW);
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(24);
        back.setTextColor(0xff333333);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView title = new TextView(this);
        title.setText("创建团队");
        title.setTextSize(18);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        TextView submit = new TextView(this);
        submit.setText("提交");
        submit.setTextSize(16);
        submit.setTextColor(0xff333333);
        submit.setGravity(Gravity.CENTER);
        submit.setPadding(0, 0, dp(12), 0);
        bar.addView(submit, new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(bar);

        TextView label = new TextView(this);
        label.setText("团队名称");
        label.setTextSize(15);
        label.setTextColor(0xff999999);
        label.setPadding(dp(5), dp(14), 0, dp(6));
        root.addView(label);

        EditText input = new EditText(this);
        input.setHint("请输入团队名称");
        input.setHintTextColor(0xff999999);
        input.setTextSize(15);
        input.setTextColor(0xff333333);
        input.setSingleLine(true);
        input.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xfff7f7f7);
        bg.setCornerRadius(dp(6));
        input.setBackground(bg);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        ilp.leftMargin = dp(15);
        ilp.rightMargin = dp(15);
        root.addView(input, ilp);

        submit.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "请输入团队名称", Toast.LENGTH_SHORT).show();
                return;
            }
            TeamStore.add(this, name);
            Toast.makeText(this, "创建成功", Toast.LENGTH_SHORT).show();
            finish();
        });

        setContentView(root);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
