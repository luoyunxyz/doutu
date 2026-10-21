package com.luoyun.doutu.features.tools;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 编辑面板：参考 EditPanelActivity 的「工具顺序自定义」，
 * 用上移/下移替代拖拽，顺序写入 SharedPreferences，工具页立即生效。
 */
public final class EditPanelActivity extends Activity {
    private static final String PREFS = "doutu_tools";
    private static final String KEY = "tool_order";
    private static final String[] DEFAULTS = {"换头像", "自动替换", "AI一键模仿", "本地头像", "编辑面板", "教程"};

    private final List<String> order = new ArrayList<>();
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences sp = getSharedPreferences(PREFS, 0);
        String raw = sp.getString(KEY, "");
        order.clear();
        if (!raw.isEmpty()) order.addAll(Arrays.asList(raw.split(",")));
        for (String d : DEFAULTS) if (!order.contains(d)) order.add(d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f6fa);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.WHITE);
        Button back = new Button(this);
        back.setText("‹ 返回");
        back.setAllCaps(false);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(88), dp(50)));
        TextView title = new TextView(this);
        title.setText("编辑面板");
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1f));
        Button save = new Button(this);
        save.setText("保存");
        save.setAllCaps(false);
        save.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < order.size(); i++) { if (i > 0) sb.append(','); sb.append(order.get(i)); }
            getSharedPreferences(PREFS, 0).edit().putString(KEY, sb.toString()).apply();
            Toast.makeText(this, "已保存，工具页顺序已更新", Toast.LENGTH_SHORT).show();
            finish();
        });
        bar.addView(save, new LinearLayout.LayoutParams(dp(76), dp(50)));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(14), dp(16), dp(20));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        refresh();
    }

    private void refresh() {
        list.removeAllViews();
        for (int i = 0; i < order.size(); i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(Color.WHITE);
            row.setPadding(dp(14), dp(10), dp(10), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);
            TextView name = new TextView(this);
            name.setText(order.get(i));
            name.setTextSize(15);
            name.setTextColor(0xff1d1f26);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button up = new Button(this);
            up.setText("↑");
            up.setAllCaps(false);
            up.setEnabled(index > 0);
            up.setOnClickListener(v -> move(index, -1));
            row.addView(up, new LinearLayout.LayoutParams(dp(48), dp(40)));
            Button down = new Button(this);
            down.setText("↓");
            down.setAllCaps(false);
            down.setEnabled(index < order.size() - 1);
            down.setOnClickListener(v -> move(index, 1));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(48), dp(40));
            dlp.leftMargin = dp(4);
            row.addView(down, dlp);
            list.addView(row);
        }
    }

    private void move(int from, int delta) {
        int to = from + delta;
        if (to < 0 || to >= order.size()) return;
        String item = order.remove(from);
        order.add(to, item);
        refresh();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
