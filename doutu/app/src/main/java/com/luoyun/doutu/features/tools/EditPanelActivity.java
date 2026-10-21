package com.luoyun.doutu.features.tools;

import android.app.Activity;
import android.app.AlertDialog;
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
 * 对齐参考 activity_edit_panel：标题栏 → 「我的面板 / 点击图标调整顺序」白条 →
 * 4 列图标网格；点击图标用「上移/下移」完成排序，顺序持久化后工具页立即生效。
 */
public final class EditPanelActivity extends Activity {
    private static final String PREFS = "doutu_tools";
    private static final String KEY = "tool_order";
    private static final String[] DEFAULTS = {"换头像", "自动替换", "AI一键模仿", "本地头像", "编辑面板", "教程"};
    private static final int[] TINTS = {0xffeaf1ff, 0xfffff0e8, 0xffefeaff, 0xffe8f8ee, 0xffeaf1ff, 0xfffff0e8};

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
        root.setBackgroundColor(0xfff7f7f7);

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

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(Color.WHITE);
        head.setPadding(dp(15), 0, dp(15), 0);
        TextView my = new TextView(this);
        my.setText("我的面板");
        my.setTextSize(13);
        my.setTextColor(0xff666666);
        head.addView(my, new LinearLayout.LayoutParams(0, dp(41), 1f));
        TextView hint = new TextView(this);
        hint.setText("点击图标调整顺序");
        hint.setTextSize(13);
        hint.setTextColor(0xff999999);
        head.addView(hint);
        root.addView(head);
        View line = new View(this);
        line.setBackgroundColor(0xffeeeeee);
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(Color.WHITE);
        list.setPadding(dp(0), dp(14), dp(0), dp(15));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        refresh();
        if (getIntent().getBooleanExtra("selftest", false)) finish();
    }

    private void refresh() {
        list.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < order.size(); i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                list.addView(row);
            }
            final int index = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            clp.setMargins(0, dp(8), 0, dp(12));
            cell.setLayoutParams(clp);
            TextView icon = new TextView(this);
            icon.setText(order.get(i).isEmpty() ? "·" : order.get(i).substring(0, 1));
            icon.setTextSize(24);
            icon.setGravity(Gravity.CENTER);
            icon.setTextColor(0xff4a7bf7);
            icon.setBackgroundColor(TINTS[i % TINTS.length]);
            cell.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
            TextView name = new TextView(this);
            name.setText(order.get(i));
            name.setTextSize(11);
            name.setTextColor(0xff666666);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, dp(6), 0, 0);
            cell.addView(name);
            cell.setOnClickListener(v -> showMoveMenu(index));
            row.addView(cell);
        }
    }

    private void showMoveMenu(int index) {
        new AlertDialog.Builder(this)
                .setTitle(order.get(index))
                .setItems(new String[]{"上移", "下移", "取消"}, (d, which) -> {
                    if (which == 0) move(index, -1);
                    else if (which == 1) move(index, 1);
                }).show();
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
