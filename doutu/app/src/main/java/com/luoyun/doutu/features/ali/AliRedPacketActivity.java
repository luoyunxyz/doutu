package com.luoyun.doutu.features.ali;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

/**
 * 支付宝「红包」—— 按真机 activity_ali_chat_red_envelope.xml 重画：
 * 灰栏（返回 / 红包 / 黄色发送）→ 红包金额 ￥ → 祝福语输入框 → 更换发送人 →
 * 选择发送人（对方发 / 自己发）。
 */
public final class AliRedPacketActivity extends Activity {
    private EditText money;
    private EditText content;
    private RadioButton leftCb;
    private RadioButton rightCb;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xffededed);
        w.setNavigationBarColor(Color.WHITE);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff7f7f7);

        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(0xffededed);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        ImageView back = new ImageView(this);
        back.setImageBitmap(backArrow());
        back.setOnClickListener(v -> finish());
        bar.addView(back, new FrameLayout.LayoutParams(dp(56), dp(56)));
        TextView title = new TextView(this);
        title.setText("红包");
        title.setTextSize(16);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        TextView send = new TextView(this);
        send.setText("发送");
        send.setTextSize(14);
        send.setTextColor(Color.BLACK);
        send.setGravity(Gravity.CENTER);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0xffffda43);
        sbg.setCornerRadius(dp(4));
        send.setBackground(sbg);
        send.setPadding(dp(15), 0, dp(15), 0);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        slp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        slp.rightMargin = dp(15);
        bar.addView(send, slp);
        root.addView(bar);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(15), dp(15), dp(15), dp(15));
        TextView label = new TextView(this);
        label.setText("红包金额");
        label.setTextSize(13);
        label.setTextColor(0xff333333);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.leftMargin = dp(17);
        card.addView(label, llp);
        LinearLayout moneyRow = new LinearLayout(this);
        moneyRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mrlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mrlp.leftMargin = dp(17);
        mrlp.topMargin = dp(13);
        TextView yen = new TextView(this);
        yen.setText("￥");
        yen.setTextSize(35);
        yen.setTextColor(0xff333333);
        moneyRow.addView(yen);
        money = new EditText(this);
        money.setHint("0");
        money.setHintTextColor(0xffaaaaaa);
        money.setTextSize(50);
        money.setTextColor(0xff333333);
        money.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        money.setBackground(null);
        money.setPadding(0, 0, 0, 0);
        money.setSingleLine(true);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.leftMargin = dp(10);
        moneyRow.addView(money, mlp);
        card.addView(moneyRow, mrlp);
        root.addView(card);

        root.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10)));

        content = new EditText(this);
        content.setHint("恭喜发财,大吉大利");
        content.setTextSize(14);
        content.setTextColor(0xff333333);
        content.setGravity(Gravity.TOP | Gravity.START);
        content.setBackgroundColor(Color.WHITE);
        content.setPadding(dp(15), dp(10), dp(15), dp(10));
        content.setMaxLines(3);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(100)));

        root.addView(new View(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10)));

        Bitmap avatar = asset("qq_icons/rp_avatar.png");

        // 更换发送人
        LinearLayout sender = new LinearLayout(this);
        sender.setGravity(Gravity.CENTER_VERTICAL);
        sender.setBackgroundColor(Color.WHITE);
        sender.setPadding(dp(15), dp(10), dp(15), dp(10));
        ImageView av = new ImageView(this);
        if (avatar != null) av.setImageBitmap(avatar);
        sender.addView(av, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView name = new TextView(this);
        name.setText("你");
        name.setTextSize(14);
        name.setTextColor(0xff333333);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = dp(13);
        sender.addView(name, nlp);
        TextView change = new TextView(this);
        change.setText("发送人 ›");
        change.setTextSize(14);
        change.setTextColor(0xff333333);
        sender.addView(change);
        root.addView(sender);

        // 选择发送人
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.WHITE);
        panel.setPadding(dp(15), dp(15), dp(15), dp(15));
        TextView choose = new TextView(this);
        choose.setText("选择发送人");
        choose.setTextSize(14);
        choose.setTextColor(0xff333333);
        panel.addView(choose);
        FrameLayout row = new FrameLayout(this);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(15);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout leftTop = new LinearLayout(this);
        leftTop.setGravity(Gravity.CENTER_VERTICAL);
        ImageView leftIcon = new ImageView(this);
        if (avatar != null) leftIcon.setImageBitmap(avatar);
        leftTop.addView(leftIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        leftCb = new RadioButton(this);
        leftCb.setChecked(true);
        LinearLayout.LayoutParams lclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lclp.leftMargin = dp(15);
        leftTop.addView(leftCb, lclp);
        leftTop.setOnClickListener(v -> select(false));
        left.addView(leftTop);
        TextView leftLabel = new TextView(this);
        leftLabel.setText("对方发");
        leftLabel.setTextSize(13);
        leftLabel.setTextColor(0xff333333);
        LinearLayout.LayoutParams lllp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lllp.topMargin = dp(6);
        left.addView(leftLabel, lllp);
        row.addView(left);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout rightTop = new LinearLayout(this);
        rightTop.setGravity(Gravity.CENTER_VERTICAL);
        ImageView rightIcon = new ImageView(this);
        if (avatar != null) rightIcon.setImageBitmap(avatar);
        rightTop.addView(rightIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        rightCb = new RadioButton(this);
        LinearLayout.LayoutParams rclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rclp.leftMargin = dp(15);
        rightTop.addView(rightCb, rclp);
        rightTop.setOnClickListener(v -> select(true));
        right.addView(rightTop);
        TextView rightLabel = new TextView(this);
        rightLabel.setText("自己发");
        rightLabel.setTextSize(13);
        rightLabel.setTextColor(0xff333333);
        rightLabel.setGravity(Gravity.END);
        LinearLayout.LayoutParams rllp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rllp.topMargin = dp(6);
        rllp.gravity = Gravity.END;
        right.addView(rightLabel, rllp);
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rightLp.gravity = Gravity.END;
        row.addView(right, rightLp);

        panel.addView(row, rlp);
        root.addView(panel);

        send.setOnClickListener(v -> {
            double v2 = 0;
            try {
                v2 = Double.parseDouble(money.getText().toString().trim());
            } catch (Exception ignored) { }
            if (v2 <= 0) {
                Toast.makeText(this, "请输入红包金额", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, "红包已发送", Toast.LENGTH_SHORT).show();
            finish();
        });

        setContentView(root);
    }

    private void select(boolean self) {
        leftCb.setChecked(!self);
        rightCb.setChecked(self);
    }

    private Bitmap backArrow() {
        int s = 120;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xff333333);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(70, 34, 46, 60, p);
        c.drawLine(46, 60, 70, 86, p);
        return b;
    }

    private Bitmap asset(String path) {
        try (InputStream in = getAssets().open(path)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
