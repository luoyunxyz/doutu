package com.luoyun.doutu.features.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 对齐参考 fragment_function_tutorial / item_function_tutorial：
 * 2 列教程卡片（竖版封面 + 13sp 标题 + 「使用 / 教程」按钮）。
 */
public final class ToolsTutorialActivity extends Activity {
    private static final String[][] ITEMS = {
            {"换头像", "换", "0xffeaf1ff", "1. 选底图（聊天截图或照片）；\n2. 选头像；\n3. 可裁剪底图；\n4. 开始替换，逐项确认后一键合成；\n5. 预览保存到相册与本地头像。"},
            {"自动替换", "自", "0xfffff0e8", "1. 选群聊底图和头像；\n2. 开始替换，检测全部人脸；\n3. 可勾选水印；\n4. 合成后保存。"},
            {"AI一键模仿", "AI", "0xffefeaff", "1. 多选最多 9 张图片；\n2. 选替换头像，打开替换开关；\n3. 立即生成；\n4. 一键保存以上图片。"},
            {"本地头像", "头", "0xffe8f8ee", "工具生成的头像都在这里；\n右上角可从相册导入；\n点开预览，长按删除。"},
            {"编辑面板", "编", "0xfffff0e8", "进入编辑面板，点击图标选择上移/下移；\n保存后工具页立即按新顺序显示。"},
            {"隐私说明", "私", "0xffeaf1ff", "所有图片处理都在本机完成，不上传服务器，也不调用本软件其它模块。"}
    };
    private LinearLayout grid;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
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
        title.setText("教程");
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(88), dp(50)));
        root.addView(bar);

        Button selfTest = new Button(this);
        selfTest.setText("运行全量回归自检（67 入口）");
        selfTest.setAllCaps(false);
        selfTest.setOnClickListener(v -> startActivity(new android.content.Intent(this, ToolsSelfTestActivity.class)));
        root.addView(selfTest);

        ScrollView scroll = new ScrollView(this);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(8), dp(10), dp(8), dp(16));
        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        if (getIntent().getBooleanExtra("selftest", false)) { finish(); return; }
        render();
    }

    private void render() {
        LinearLayout row = null;
        for (int i = 0; i < ITEMS.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            row.addView(card(ITEMS[i]));
        }
    }

    private View card(String[] item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(4), dp(8), dp(4), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        card.setLayoutParams(lp);

        TextView cover = new TextView(this);
        cover.setText(item[1] + "\n\n豆图工具");
        cover.setTextSize(30);
        cover.setTextColor(0xff4a7bf7);
        cover.setGravity(Gravity.CENTER);
        cover.setBackgroundColor(Color.parseColor(item[2].replace("0x", "#")));
        card.addView(cover, new LinearLayout.LayoutParams(dp(143), dp(254)));

        TextView name = new TextView(this);
        name.setText(item[0]);
        name.setTextSize(13);
        name.setTextColor(0xff333333);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(8), 0, dp(8));
        card.addView(name);

        LinearLayout btns = new LinearLayout(this);
        btns.setGravity(Gravity.CENTER);
        Button use = new Button(this);
        use.setText("使用");
        use.setAllCaps(false);
        use.setTextSize(12);
        use.setMinHeight(0);
        use.setMinimumHeight(0);
        use.setPadding(0, 0, 0, 0);
        use.setIncludeFontPadding(false);
        use.setOnClickListener(v -> {
            String key = item[0].contains("换头像") ? "avatar" : item[0].contains("自动") ? "auto"
                    : item[0].contains("AI") ? "ai" : item[0].contains("本地头像") ? "localavatars"
                    : item[0].contains("编辑") ? "edit" : "tutorial";
            if ("localavatars".equals(key)) startActivity(new android.content.Intent(this, LocalAvatarsActivity.class));
            else if ("edit".equals(key)) startActivity(new android.content.Intent(this, EditPanelActivity.class));
            else if ("tutorial".equals(key)) showDetail(item);
            else ToolImageActivity.open(this, key);
        });
        btns.addView(use, new LinearLayout.LayoutParams(dp(60), dp(30)));
        Button tut = new Button(this);
        tut.setText("教程");
        tut.setAllCaps(false);
        tut.setTextSize(12);
        tut.setMinHeight(0);
        tut.setMinimumHeight(0);
        tut.setPadding(0, 0, 0, 0);
        tut.setIncludeFontPadding(false);
        tut.setOnClickListener(v -> showDetail(item));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(60), dp(30));
        tlp.leftMargin = dp(10);
        btns.addView(tut, tlp);
        card.addView(btns);
        return card;
    }

    private void showDetail(String[] item) {
        new AlertDialog.Builder(this)
                .setTitle(item[0])
                .setMessage(item[3])
                .setPositiveButton("知道了", null)
                .show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
