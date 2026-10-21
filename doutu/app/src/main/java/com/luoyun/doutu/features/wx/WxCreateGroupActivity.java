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
import android.widget.TextView;

/**
 * 「创建群聊」弹窗 —— 按真机：微信首页之上弹一个白色对话框，两个输入框
 * （群名称 / 人数），底部 取消 / 确定。
 */
public final class WxCreateGroupActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(0x66000000);

        FrameLayout root = new FrameLayout(this);
        View scrim = new View(this);
        scrim.setBackgroundColor(0x8c000000);
        root.addView(scrim, new FrameLayout.LayoutParams(
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
        clp.leftMargin = dp(24);
        clp.rightMargin = dp(24);
        clp.topMargin = dp(210);
        root.addView(card, clp);

        final EditText name = field("群名称", "再歪一点");
        card.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        final EditText count = field("人数", "100");
        count.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        nlp.topMargin = dp(12);
        card.addView(count, nlp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        blp.topMargin = dp(22);
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
            String groupName = name.getText().toString().trim();
            if (groupName.isEmpty()) groupName = "再歪一点";
            String members = count.getText().toString().trim();
            final String fullName = members.isEmpty() ? groupName : groupName + "(" + members + ")";
            String safe = fullName.replace("\\", "\\\\").replace("'", "\\'");
            // 走克隆自己的落库路径，建完后回微信首页，聊天列表里就能看到这个群
            String js = "window.__fwAddFriend && window.__fwAddFriend('" + safe + "','')";
            startActivity(com.luoyun.doutu.features.wei.WeiHomeActivity
                    .intentFor(this, "#/wechat"));
            com.luoyun.doutu.features.wei.WeiHomeActivity.scheduleBridge(js, "#/wechat");
            android.widget.Toast.makeText(this, "已创建群聊：" + fullName, android.widget.Toast.LENGTH_SHORT).show();
            finish();
        });
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(56), 1f));
        buttons.addView(ok, new LinearLayout.LayoutParams(0, dp(56), 1f));
        card.addView(buttons, blp);

        setContentView(root);
    }

    private EditText field(String hint, String value) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setTextSize(15);
        edit.setSingleLine(true);
        edit.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setStroke(dp(1), 0xffdddddd);
        bg.setCornerRadius(dp(6));
        edit.setBackground(bg);
        return edit;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
