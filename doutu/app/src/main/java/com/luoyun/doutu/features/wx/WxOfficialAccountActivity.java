package com.luoyun.doutu.features.wx;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 微信「公众号」弹窗 —— 按真机截图重画：
 * 头像 + 常用图标 / 从相册获取 + 名称 / 消息内容 + 设为蓝色字体 / 免打扰 / 消息置顶 +
 * 取消 / 确定。
 */
public final class WxOfficialAccountActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xffededed);
        w.setNavigationBarColor(Color.WHITE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xffededed);

        // 背景（简化成灰底）
        View bg = new View(this);
        bg.setBackgroundColor(0xffededed);
        root.addView(bg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(Color.WHITE);
        cbg.setCornerRadius(dp(10));
        card.setBackground(cbg);
        card.setPadding(dp(20), dp(24), dp(20), dp(0));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(28);
        clp.rightMargin = dp(28);
        clp.topMargin = dp(90);
        root.addView(card, clp);

        // 头像 + 两侧说明
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER);
        head.addView(sideAction("★", "常用图标"));
        View avatar = new View(this);
        GradientDrawable abg = new GradientDrawable();
        abg.setColor(0xffd9d9d9);
        abg.setCornerRadius(dp(6));
        avatar.setBackground(abg);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(80), dp(80));
        alp.leftMargin = dp(24);
        alp.rightMargin = dp(24);
        head.addView(avatar, alp);
        head.addView(sideAction("😀", "从相册获取"));
        card.addView(head);

        // 名称
        EditText name = field("名称:");
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        nlp.topMargin = dp(24);
        card.addView(name, nlp);

        // 消息内容
        EditText content = field("消息内容:");
        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        clp2.topMargin = dp(12);
        card.addView(content, clp2);

        card.addView(toggleRow("设为蓝色字体"));
        card.addView(toggleRow("免打扰"));
        card.addView(toggleRow("消息置顶"));

        // 取消 / 确定
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        blp.topMargin = dp(16);
        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextSize(16);
        cancel.setTextColor(0xff666666);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> finish());
        TextView ok = new TextView(this);
        ok.setText("确定");
        ok.setTextSize(16);
        ok.setTextColor(0xff333333);
        ok.setGravity(Gravity.CENTER);
        ok.setOnClickListener(v -> {
            Toast.makeText(this, "公众号已保存", Toast.LENGTH_SHORT).show();
            finish();
        });
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(56), 1f));
        buttons.addView(ok, new LinearLayout.LayoutParams(0, dp(56), 1f));
        card.addView(buttons, blp);

        setContentView(root);
    }

    private LinearLayout sideAction(String icon, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(24);
        iconView.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xfffff7d6);
        bg.setShape(GradientDrawable.OVAL);
        iconView.setBackground(bg);
        box.addView(iconView, new LinearLayout.LayoutParams(dp(56), dp(56)));
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(12);
        labelView.setTextColor(0xff333333);
        labelView.setGravity(Gravity.CENTER);
        labelView.setPadding(0, dp(8), 0, 0);
        box.addView(labelView);
        return box;
    }

    private EditText field(String hint) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextSize(15);
        edit.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setStroke(dp(1), 0xffdddddd);
        bg.setCornerRadius(dp(6));
        edit.setBackground(bg);
        return edit;
    }

    private LinearLayout toggleRow(String label) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setStroke(dp(1), 0xffdddddd);
        bg.setCornerRadius(dp(6));
        row.setBackground(bg);
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(15);
        text.setTextColor(0xff333333);
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch toggle = new Switch(this);
        row.addView(toggle);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        lp.topMargin = dp(12);
        row.setLayoutParams(lp);
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
