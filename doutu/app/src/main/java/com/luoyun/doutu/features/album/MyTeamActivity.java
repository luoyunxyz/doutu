package com.luoyun.doutu.features.album;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** 「我的团队」页 —— 黄栏 + 团队列表；没有团队时和真机一样是空白。 */
public final class MyTeamActivity extends Activity {
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
        title.setText("我的团队");
        title.setTextSize(18);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(bar);

        List<String> teams = TeamStore.teams(this);
        for (int i = 0; i < teams.size(); i++) {
            String name = teams.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), 0, dp(16), 0);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
            TextView tv = new TextView(this);
            tv.setText(name);
            tv.setTextSize(16);
            tv.setTextColor(0xff333333);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            TextView count = new TextView(this);
            count.setText("1 人");
            count.setTextSize(13);
            count.setTextColor(0xff999999);
            count.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(count);
            root.addView(row);
            View line = new View(this);
            line.setBackgroundColor(0xffeeeeee);
            root.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        setContentView(root);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
