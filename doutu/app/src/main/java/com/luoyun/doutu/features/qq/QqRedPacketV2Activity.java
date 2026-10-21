package com.luoyun.doutu.features.qq;

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
import android.widget.Toast;

/**
 * QQ「发红包」—— 按真机截图重画：
 * 顶栏（返回 / 发红包 / 发送）+ 红包金额 ¥0 + 恭喜发财 + 发送人（少女蒸汽机 / 切换发送人）。
 */
public final class QqRedPacketV2Activity extends Activity {
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
        title.setText("发红包");
        title.setTextSize(17);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        TextView send = new TextView(this);
        send.setText("发送");
        send.setTextSize(15);
        send.setTextColor(0xff333333);
        send.setGravity(Gravity.CENTER);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0xffffda43);
        sbg.setCornerRadius(dp(6));
        send.setBackground(sbg);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(72), dp(34));
        slp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        slp.rightMargin = dp(12);
        send.setOnClickListener(v -> {
            Toast.makeText(this, "红包已发送", Toast.LENGTH_SHORT).show();
            finish();
        });
        bar.addView(send, slp);
        root.addView(bar);

        // 金额
        LinearLayout amountBox = new LinearLayout(this);
        amountBox.setOrientation(LinearLayout.VERTICAL);
        amountBox.setBackgroundColor(Color.WHITE);
        amountBox.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView label = new TextView(this);
        label.setText("红包金额");
        label.setTextSize(15);
        label.setTextColor(0xff333333);
        amountBox.addView(label);
        LinearLayout money = new LinearLayout(this);
        money.setGravity(Gravity.CENTER_VERTICAL);
        money.setPadding(0, dp(10), 0, 0);
        TextView yen = new TextView(this);
        yen.setText("¥");
        yen.setTextSize(30);
        yen.setTextColor(0xff1a1a1a);
        money.addView(yen);
        EditText input = new EditText(this);
        input.setHint("0");
        input.setHintTextColor(0xffbbbbbb);
        input.setTextSize(40);
        input.setTextColor(0xff1a1a1a);
        input.setBackground(null);
        input.setPadding(dp(10), 0, 0, 0);
        money.addView(input, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        amountBox.addView(money);
        root.addView(amountBox);

        // 祝福语
        EditText wish = new EditText(this);
        wish.setText("恭喜发财");
        wish.setTextSize(15);
        wish.setTextColor(0xff333333);
        wish.setBackgroundColor(Color.WHITE);
        wish.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.addView(wish);

        // 发送人
        LinearLayout sender = new LinearLayout(this);
        sender.setGravity(Gravity.CENTER_VERTICAL);
        sender.setBackgroundColor(Color.WHITE);
        sender.setPadding(dp(16), dp(12), dp(16), dp(12));
        TextView avatar = new TextView(this);
        avatar.setText("少");
        avatar.setTextSize(12);
        avatar.setTextColor(Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable abg = new GradientDrawable();
        abg.setColor(0xffd9a0a0);
        abg.setCornerRadius(dp(4));
        avatar.setBackground(abg);
        sender.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView name = new TextView(this);
        name.setText("少女蒸汽机");
        name.setTextSize(15);
        name.setTextColor(0xff333333);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = dp(10);
        sender.addView(name, nlp);
        TextView switchSender = new TextView(this);
        switchSender.setText("切换发送人");
        switchSender.setTextSize(14);
        switchSender.setTextColor(0xff333333);
        sender.addView(switchSender);
        root.addView(sender);

        root.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
