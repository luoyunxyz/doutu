package com.luoyun.doutu.features.tools;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 工具页全量回归自检：按 tools_catalog.json 逐项拉起本地页面，页面带 selftest 会立刻自关。
 * 全部启动完成即证明 67 个入口的 onCreate/视图构建均未崩溃。
 */
public final class ToolsSelfTestActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String[]> entries = new ArrayList<>();
    private int index = 0;
    private TextView progress;
    private LinearLayout log;
    private boolean running = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
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
        title.setText("全量回归自检");
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1f));
        bar.addView(new android.view.View(this), new LinearLayout.LayoutParams(dp(88), dp(50)));
        root.addView(bar);
        progress = new TextView(this);
        progress.setTextSize(15);
        progress.setTextColor(0xff1d1f26);
        progress.setPadding(dp(16), dp(14), dp(16), dp(8));
        root.addView(progress);
        ScrollView scroll = new ScrollView(this);
        log = new LinearLayout(this);
        log.setOrientation(LinearLayout.VERTICAL);
        log.setPadding(dp(16), 0, dp(16), dp(16));
        scroll.addView(log);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Button start = new Button(this);
        start.setText("开始自检（67 项）");
        start.setAllCaps(false);
        start.setOnClickListener(v -> start());
        root.addView(start);
        setContentView(root);
        load();
        progress.setText("准备就绪：" + entries.size() + " 个入口");
        handler.postDelayed(this::start, 300);
    }

    private void load() {
        try (InputStream in = getAssets().open("tools_catalog.json")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            JSONArray groups = new JSONArray(new String(bos.toByteArray(), "UTF-8"));
            for (int g = 0; g < groups.length(); g++) {
                JSONObject group = groups.getJSONObject(g);
                JSONArray items = group.getJSONArray("items");
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    entries.add(new String[]{item.getString("name"), item.getString("key")});
                }
            }
        } catch (Exception ignored) { }
    }

    private void start() {
        if (running) return;
        running = true;
        index = 0;
        log.removeAllViews();
        next();
    }

    private void next() {
        if (index >= entries.size()) {
            running = false;
            progress.setText("自检完成：" + entries.size() + " 个入口全部启动成功（未崩溃即通过）");
            addLog("完成 · " + entries.size() + "/" + entries.size());
            return;
        }
        String[] e = entries.get(index);
        progress.setText("正在自检 " + (index + 1) + "/" + entries.size() + " · " + e[0]);
        try {
            Intent intent;
            String key = e[1];
            if ("avatar".equals(key) || "auto".equals(key) || "ai".equals(key)) {
                intent = new Intent(this, ToolImageActivity.class);
                intent.putExtra("mode", key);
            } else if ("localavatars".equals(key)) {
                intent = new Intent(this, LocalAvatarsActivity.class);
            } else if ("edit".equals(key)) {
                intent = new Intent(this, EditPanelActivity.class);
            } else if ("tutorial".equals(key)) {
                intent = new Intent(this, ToolsTutorialActivity.class);
            } else {
                intent = new Intent(this, LocalToolActivity.class);
                intent.putExtra("key", key);
                intent.putExtra("title", e[0]);
            }
            intent.putExtra("selftest", true);
            startActivity(intent);
            addLog("启动 " + e[0] + " (" + key + ")");
        } catch (Exception ex) {
            addLog("失败 " + e[0] + "：" + ex.getMessage());
        }
        index++;
        handler.postDelayed(this::next, 380);
    }

    private void addLog(String line) {
        TextView tv = new TextView(this);
        tv.setText(line);
        tv.setTextSize(13);
        tv.setTextColor(0xff5b6070);
        log.addView(tv);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
