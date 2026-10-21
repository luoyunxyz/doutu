package com.luoyun.doutu.features.tools;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 工具教程：本地说明页（参考 FunctionTutorialActivity 的图文教程列表）。 */
public final class ToolsTutorialActivity extends Activity {
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
        title.setText("教程");
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(88), dp(50)));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(24));
        add(col, "换头像", "1. 点「底图」选择一张聊天截图或照片；\n2. 点「头像图片」选择要换上去的头像；\n3. 点「开始替换」，自动检测人脸并合成；\n4. 点「保存到相册」，同时会在「本地头像」留一份。");
        add(col, "自动替换头像", "1. 底图可以选群聊截图；\n2. 选好头像后点「开始替换」，会检测并替换全部人脸；\n3. 可勾选「保存时添加水印」；\n4. 保存到相册。");
        add(col, "AI 一键模仿", "1. 点「选择图片」，可多选（最多 9 张）；\n2. 选择要替换成的头像，勾选「替换头像」；\n3. 点「生成」自动识别并批量替换头像；\n4. 点「保存到相册」一键保存以上图片。");
        add(col, "本地头像", "工具生成的头像都在这里；右上角「添加本地头像」可以从相册导入，点缩略图预览，长按删除。");
        add(col, "编辑面板", "用 ↑ / ↓ 调整工具顺序，保存后工具页立即按新顺序显示。");
        add(col, "隐私说明", "所有图片处理都在本机完成，不上传服务器，也不调用本软件其它模块。");
        scroll.addView(col);
        Button selfTest = new Button(this);
        selfTest.setText("运行全量回归自检（67 入口）");
        selfTest.setAllCaps(false);
        selfTest.setOnClickListener(v -> startActivity(new android.content.Intent(this, ToolsSelfTestActivity.class)));
        root.addView(selfTest);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        if (getIntent().getBooleanExtra("selftest", false)) finish();
    }

    private void add(LinearLayout parent, String title, String body) {
        TextView h = new TextView(this);
        h.setText(title);
        h.setTextSize(16);
        h.setTextColor(0xff1d1f26);
        h.setPadding(dp(2), dp(16), dp(2), dp(6));
        parent.addView(h);
        TextView b = new TextView(this);
        b.setText(body);
        b.setTextSize(14);
        b.setLineSpacing(dp(3), 1f);
        b.setTextColor(0xff5b6070);
        b.setBackgroundColor(Color.WHITE);
        b.setPadding(dp(14), dp(12), dp(14), dp(12));
        parent.addView(b);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
