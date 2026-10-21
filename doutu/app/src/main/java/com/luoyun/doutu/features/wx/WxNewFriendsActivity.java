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
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 微信「新的朋友」—— 按真机截图重画：
 * 顶栏（返回 / 新的朋友 / 添加朋友）+ 账号/手机号搜索框 + 添加手机联系人 +
 * 「近三天」好友申请列表（头像 / 昵称 / 我是… / 查看）。
 */
public final class WxNewFriendsActivity extends Activity {
    private static final String[][] PEOPLE = {
            {"趣味少女", "我是趣味少女", "#8fb3d9"},
            {"花祭♀离殇", "我是花祭♀离殇", "#b0a8c9"},
            {"出卖心动", "我是出卖心动", "#e3e3e3"},
            {"眼泪有点失控", "我是眼泪有点失控", "#5b5b6b"},
            {"转身到达终点", "我是转身到达终点", "#d9a05b"},
            {"青萍", "我是青萍", "#4b5a6b"},
            {"水星记", "我是水星记", "#e8c9c9"},
            {"微信名称", "我是微信名称", "#cbb9a8"},
            {"我为自己代言", "我是我为自己代言", "#b08a78"},
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xffededed);
        w.setNavigationBarColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f5f5);

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
        title.setText("新的朋友");
        title.setTextSize(17);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        TextView add = new TextView(this);
        add.setText("添加朋友");
        add.setTextSize(15);
        add.setTextColor(0xff333333);
        add.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(56));
        alp.gravity = Gravity.END;
        alp.rightMargin = dp(14);
        bar.addView(add, alp);
        root.addView(bar);

        // 搜索框
        TextView search = new TextView(this);
        search.setText("⌕  账号/手机号");
        search.setTextSize(15);
        search.setTextColor(0xff999999);
        search.setGravity(Gravity.CENTER);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(Color.WHITE);
        sbg.setCornerRadius(dp(8));
        search.setBackground(sbg);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        slp.leftMargin = dp(12);
        slp.rightMargin = dp(12);
        slp.topMargin = dp(8);
        root.addView(search, slp);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);

        // 添加手机联系人
        LinearLayout phone = new LinearLayout(this);
        phone.setGravity(Gravity.CENTER_VERTICAL);
        phone.setBackgroundColor(Color.WHITE);
        phone.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView phoneIcon = new TextView(this);
        phoneIcon.setText("📞");
        phoneIcon.setTextSize(18);
        phoneIcon.setTextColor(0xff07c160);
        phone.addView(phoneIcon);
        TextView phoneText = new TextView(this);
        phoneText.setText("添加手机联系人");
        phoneText.setTextSize(16);
        phoneText.setTextColor(0xff333333);
        LinearLayout.LayoutParams ptlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ptlp.leftMargin = dp(12);
        phone.addView(phoneText, ptlp);
        TextView phoneArrow = new TextView(this);
        phoneArrow.setText("›");
        phoneArrow.setTextSize(16);
        phoneArrow.setTextColor(0xffbbbbbb);
        phone.addView(phoneArrow);
        LinearLayout.LayoutParams phlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        phlp.topMargin = dp(8);
        body.addView(phone, phlp);

        // 近三天
        TextView section = new TextView(this);
        section.setText("近三天");
        section.setTextSize(13);
        section.setTextColor(0xff888888);
        section.setPadding(dp(16), dp(10), dp(16), dp(8));
        body.addView(section);

        for (String[] p : PEOPLE) {
            body.addView(personRow(p));
        }

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private LinearLayout personRow(String[] person) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.WHITE);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView avatar = new TextView(this);
        avatar.setText(person[0].substring(0, 1));
        avatar.setTextSize(18);
        avatar.setTextColor(Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable abg = new GradientDrawable();
        abg.setColor(Color.parseColor(person[2]));
        abg.setCornerRadius(dp(6));
        avatar.setBackground(abg);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = dp(12);
        TextView name = new TextView(this);
        name.setText(person[0]);
        name.setTextSize(16);
        name.setTextColor(0xff333333);
        copy.addView(name);
        TextView sub = new TextView(this);
        sub.setText(person[1]);
        sub.setTextSize(13);
        sub.setTextColor(0xff999999);
        copy.addView(sub);
        row.addView(copy, clp);

        TextView view = new TextView(this);
        view.setText("查看");
        view.setTextSize(14);
        view.setTextColor(0xff333333);
        view.setGravity(Gravity.CENTER);
        GradientDrawable vbg = new GradientDrawable();
        vbg.setColor(0xfff2f2f2);
        vbg.setCornerRadius(dp(4));
        view.setBackground(vbg);
        row.addView(view, new LinearLayout.LayoutParams(dp(64), dp(32)));
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
