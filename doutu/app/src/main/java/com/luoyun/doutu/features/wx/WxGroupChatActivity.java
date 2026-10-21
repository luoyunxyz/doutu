package com.luoyun.doutu.features.wx;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 原生「群聊」聊天页：标题 群名(人数)、右上「…」进群设置、左右滑切换发送者。 */
public final class WxGroupChatActivity extends Activity {
    private GroupStore.Group group;
    private TextView title;
    private LinearLayout bubbleRow;
    private TextView bubble;
    private boolean self;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra("groupId");
        group = GroupStore.get(this, id);
        if (group == null) {
            group = new GroupStore.Group();
            group.id = "g" + System.currentTimeMillis();
        }

        Window w = getWindow();
        w.setStatusBarColor(0xffededed);
        w.setNavigationBarColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xffededed);

        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(0xffededed);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(0xff333333);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new FrameLayout.LayoutParams(dp(44), dp(56)));
        title = new TextView(this);
        title.setText(titleText());
        title.setTextSize(16);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        TextView dots = new TextView(this);
        dots.setText("···");
        dots.setTextSize(18);
        dots.setTextColor(0xff333333);
        dots.setGravity(Gravity.CENTER);
        dots.setOnClickListener(v -> openSettings());
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(52), dp(56));
        dlp.gravity = Gravity.END;
        bar.addView(dots, dlp);
        root.addView(bar);

        // 群信息条
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setBackgroundColor(Color.WHITE);
        info.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView members = new TextView(this);
        members.setText("群成员：" + (group.members.isEmpty() ? "微笑似年华々" : join(group)));
        members.setTextSize(13);
        members.setTextColor(0xff666666);
        info.addView(members);
        TextView hint = new TextView(this);
        hint.setText("群聊已创建 · 共 " + group.count + " 人");
        hint.setTextSize(12);
        hint.setTextColor(0xff999999);
        hint.setPadding(0, dp(6), 0, 0);
        info.addView(hint);
        root.addView(info);

        // 消息区
        bubbleRow = new LinearLayout(this);
        bubbleRow.setGravity(Gravity.TOP);
        bubbleRow.setPadding(dp(14), dp(16), dp(14), 0);
        TextView avatar = new TextView(this);
        avatar.setText("群");
        avatar.setTextSize(11);
        avatar.setTextColor(Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable abg = new GradientDrawable();
        abg.setColor(0xff8fb3d9);
        abg.setCornerRadius(dp(5));
        avatar.setBackground(abg);
        bubbleRow.addView(avatar, new LinearLayout.LayoutParams(dp(42), dp(42)));
        bubble = new TextView(this);
        bubble.setText("我是" + group.name);
        bubble.setTextSize(15);
        bubble.setTextColor(0xff333333);
        bubble.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bbg = new GradientDrawable();
        bbg.setColor(Color.WHITE);
        bbg.setCornerRadius(dp(6));
        bubble.setBackground(bbg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = dp(10);
        bubbleRow.addView(bubble, blp);
        bubble.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getX();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getX() - downX) > dp(40)) moved = true;
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) swapSender();
                        return true;
                }
                return false;
            }
        });
        root.addView(bubbleRow);

        root.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 输入栏
        LinearLayout input = new LinearLayout(this);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setBackgroundColor(0xfff7f7f7);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView voice = new TextView(this);
        voice.setText("◎");
        voice.setTextSize(20);
        voice.setTextColor(0xff333333);
        input.addView(voice, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView field = new TextView(this);
        field.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        flp.leftMargin = dp(8);
        input.addView(field, flp);
        TextView emoji = new TextView(this);
        emoji.setText("☺");
        emoji.setTextSize(20);
        emoji.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(dp(34), dp(34));
        elp.leftMargin = dp(8);
        input.addView(emoji, elp);
        TextView plus = new TextView(this);
        plus.setText("＋");
        plus.setTextSize(22);
        plus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(34), dp(34));
        plp.leftMargin = dp(8);
        input.addView(plus, plp);
        root.addView(input);

        setContentView(root);
    }

    private String titleText() {
        return group.name + "(" + group.count + ")";
    }

    private String join(GroupStore.Group g) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(3, g.members.size());
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append("、");
            sb.append(g.members.get(i));
        }
        return sb.toString();
    }

    private void swapSender() {
        self = !self;
        bubbleRow.removeAllViews();
        bubbleRow.setGravity(self ? Gravity.END : Gravity.START);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(self ? 0xff95ec69 : Color.WHITE);
        bg.setCornerRadius(dp(6));
        bubble.setBackground(bg);
        if (self) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.rightMargin = dp(10);
            bubbleRow.addView(bubble, blp);
        } else {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.leftMargin = dp(10);
            bubbleRow.addView(bubble, blp);
        }
    }

    private void openSettings() {
        startActivity(new Intent(this, WxGroupSettingsActivity.class)
                .putExtra("groupId", group.id));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
