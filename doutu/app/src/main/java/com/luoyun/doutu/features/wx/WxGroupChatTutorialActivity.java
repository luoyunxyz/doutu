package com.luoyun.doutu.features.wx;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 微信「创建群聊」—— 按真机截图重画：
 * 群聊页（再歪一点(100) / 一条消息 / 底部输入栏）+ 「切换“发送者”的 3 种方法」教程浮层
 * （1 点击昵称 / 2 摇一摇手机 / 3 右下角 ＋ → 切换角色，知道了 + 不再提示）。
 */
public final class WxGroupChatTutorialActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xffededed);
        w.setNavigationBarColor(Color.WHITE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xffededed);

        // 聊天页
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

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
        TextView title = new TextView(this);
        title.setText("再歪一点(100)");
        title.setTextSize(17);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        TextView more = new TextView(this);
        more.setText("···");
        more.setTextSize(18);
        more.setTextColor(0xff333333);
        more.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(56));
        mlp.gravity = Gravity.END;
        mlp.rightMargin = dp(14);
        bar.addView(more, mlp);
        page.addView(bar);

        // 消息
        LinearLayout msg = new LinearLayout(this);
        msg.setGravity(Gravity.TOP);
        msg.setPadding(dp(16), dp(16), dp(16), 0);
        TextView avatar = new TextView(this);
        avatar.setText("头");
        avatar.setTextSize(12);
        avatar.setTextColor(Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable abg = new GradientDrawable();
        abg.setColor(0xffef9a9a);
        abg.setCornerRadius(dp(6));
        avatar.setBackground(abg);
        msg.addView(avatar, new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView bubble = new TextView(this);
        bubble.setText("爸爸爸爸");
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
        msg.addView(bubble, blp);
        page.addView(msg);

        page.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底部输入栏
        LinearLayout input = new LinearLayout(this);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setBackgroundColor(0xfff7f7f7);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        TextView voice = new TextView(this);
        voice.setText("◎");
        voice.setTextSize(20);
        voice.setTextColor(0xff333333);
        input.addView(voice, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView field = new TextView(this);
        field.setText("");
        field.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        flp.leftMargin = dp(8);
        input.addView(field, flp);
        TextView mic = new TextView(this);
        mic.setText("🎤");
        mic.setTextSize(16);
        mic.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        micLp.leftMargin = dp(8);
        input.addView(mic, micLp);
        TextView emoji = new TextView(this);
        emoji.setText("☺");
        emoji.setTextSize(20);
        emoji.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(dp(36), dp(36));
        elp.leftMargin = dp(8);
        input.addView(emoji, elp);
        TextView plus = new TextView(this);
        plus.setText("＋");
        plus.setTextSize(22);
        plus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(36), dp(36));
        plp.leftMargin = dp(8);
        input.addView(plus, plp);
        page.addView(input);
        root.addView(page);

        // 遮罩
        View scrim = new View(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(scrim);

        // 浮层
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(Color.WHITE);
        sbg.setCornerRadius(dp(12));
        sheet.setBackground(sbg);
        sheet.setPadding(dp(20), dp(18), dp(20), dp(18));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(24);
        slp.rightMargin = dp(24);
        slp.topMargin = dp(110);
        root.addView(sheet, slp);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView headText = new TextView(this);
        headText.setText("切换“发送者”的");
        headText.setTextSize(16);
        headText.setTextColor(0xff333333);
        head.addView(headText);
        for (int i = 1; i <= 3; i++) {
            head.addView(numBadge(String.valueOf(i)), badgeLp());
        }
        TextView headTail = new TextView(this);
        headTail.setText("种方法");
        headTail.setTextSize(16);
        headTail.setTextColor(0xff333333);
        head.addView(headTail);
        sheet.addView(head);

        sheet.addView(step("1", "点击上方的“昵称”", null));
        sheet.addView(orLine());
        sheet.addView(step("2", "“摇一摇”手机", "🤝"));
        sheet.addView(orLine());
        sheet.addView(step("3", "右下角 ＋ → 切换角色", "👤"));
        sheet.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10)));

        TextView know = new TextView(this);
        know.setText("知道了");
        know.setTextSize(16);
        know.setTextColor(0xff333333);
        know.setGravity(Gravity.CENTER);
        GradientDrawable kbg = new GradientDrawable();
        kbg.setColor(0xffffda43);
        kbg.setCornerRadius(dp(26));
        know.setBackground(kbg);
        know.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        klp.topMargin = dp(10);
        sheet.addView(know, klp);

        TextView noMore = new TextView(this);
        noMore.setText("○ 不再提示");
        noMore.setTextSize(14);
        noMore.setTextColor(0xff666666);
        noMore.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(10);
        sheet.addView(noMore, nlp);

        setContentView(root);
    }

    private LinearLayout.LayoutParams badgeLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(24), dp(24));
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        return lp;
    }

    private TextView numBadge(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(0xff333333);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xffffda43);
        bg.setShape(GradientDrawable.OVAL);
        tv.setBackground(bg);
        return tv;
    }

    private LinearLayout step(String num, String text, String icon) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));
        row.addView(numBadge(num), badgeLp());
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(0xff333333);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(10);
        row.addView(tv, tlp);
        if (icon != null) {
            TextView ic = new TextView(this);
            ic.setText(icon);
            ic.setTextSize(26);
            row.addView(ic);
        }
        return row;
    }

    private TextView orLine() {
        TextView tv = new TextView(this);
        tv.setText("— — — — — 或 — — — — —");
        tv.setTextSize(12);
        tv.setTextColor(0xffbbbbbb);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
