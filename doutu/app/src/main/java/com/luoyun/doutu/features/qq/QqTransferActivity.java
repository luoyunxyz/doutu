package com.luoyun.doutu.features.qq;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

/**
 * QQ「转账」页 —— 按真机 layout（activity_qq_chat_transfer_create.xml）逐项重画：
 * 灰栏（返回 / 转账 / 黄色发送）→ 白卡（转账金额 ￥0.0）→ 分隔线 → 转账说明 →
 * 选择发送人（对方发 / 自己发）→ 切换收钱人。
 */
public final class QqTransferActivity extends Activity {
    private EditText money;
    private EditText mark;
    private RadioButton leftCb;
    private RadioButton rightCb;
    private boolean ali;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ali = getIntent().getBooleanExtra("ali", false);
        Window w = getWindow();
        w.setStatusBarColor(0xffededed);
        w.setNavigationBarColor(Color.WHITE);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff7f7f7);

        // 标题栏
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(0xffededed);
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        ImageView back = new ImageView(this);
        back.setImageBitmap(backArrow());
        back.setOnClickListener(v -> finish());
        bar.addView(back, new FrameLayout.LayoutParams(dp(56), dp(56)));
        TextView title = new TextView(this);
        title.setText("转账");
        title.setTextSize(16);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        TextView send = new TextView(this);
        send.setText(ali ? "完成" : "发送");
        send.setTextSize(14);
        send.setTextColor(Color.BLACK);
        send.setGravity(Gravity.CENTER);
        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setColor(0xffffda43);
        sendBg.setCornerRadius(dp(4));
        send.setBackground(sendBg);
        send.setPadding(dp(15), 0, dp(15), 0);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        slp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        slp.rightMargin = dp(15);
        bar.addView(send, slp);
        send.setOnClickListener(v -> submit());
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 金额卡
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(15), dp(15), dp(15), dp(15));
        TextView label = new TextView(this);
        label.setText("转账金额");
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
        money.setText("0.0");
        money.setTextSize(50);
        money.setTextColor(0xff333333);
        money.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        money.setBackground(null);
        money.setPadding(0, 0, 0, 0);
        money.setSingleLine(true);
        money.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && "0.0".equals(money.getText().toString())) money.setText("");
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.leftMargin = dp(10);
        moneyRow.addView(money, mlp);
        card.addView(moneyRow, mrlp);
        body.addView(card);

        View divider = new View(this);
        divider.setBackgroundColor(0xffeeeeee);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dlp.leftMargin = dp(17);
        body.addView(divider, dlp);

        // 转账说明
        LinearLayout markRow = new LinearLayout(this);
        markRow.setBackgroundColor(Color.WHITE);
        markRow.setPadding(dp(32), dp(10), dp(15), dp(10));
        mark = new EditText(this);
        mark.setHint("转账说明");
        mark.setHintTextColor(0xff999999);
        mark.setTextColor(Color.BLACK);
        mark.setTextSize(14);
        mark.setBackground(null);
        mark.setSingleLine(true);
        markRow.addView(mark, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(markRow);

        View space = new View(this);
        body.addView(space, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10)));

        Bitmap avatar = asset("qq_icons/rp_avatar.png");

        // 支付宝转账：切换发送人 + 转账类型（收入 / 支出）
        if (ali) {
            LinearLayout changeUser = new LinearLayout(this);
            changeUser.setGravity(Gravity.CENTER_VERTICAL);
            changeUser.setBackgroundColor(Color.WHITE);
            changeUser.setPadding(dp(15), dp(10), dp(15), dp(10));
            ImageView icon = new ImageView(this);
            if (avatar != null) icon.setImageBitmap(avatar);
            changeUser.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
            TextView userName = new TextView(this);
            userName.setText("你");
            userName.setTextSize(14);
            userName.setTextColor(0xff333333);
            LinearLayout.LayoutParams unlp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            unlp.leftMargin = dp(13);
            changeUser.addView(userName, unlp);
            TextView change = new TextView(this);
            change.setText("切换发送人 ›");
            change.setTextSize(14);
            change.setTextColor(0xff333333);
            changeUser.addView(change);
            body.addView(changeUser);

            View d1 = new View(this);
            d1.setBackgroundColor(0xffeeeeee);
            LinearLayout.LayoutParams d1lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            d1lp.leftMargin = dp(14);
            d1lp.rightMargin = dp(16);
            body.addView(d1, d1lp);

            LinearLayout account = new LinearLayout(this);
            account.setBackgroundColor(Color.WHITE);
            account.setPadding(dp(15), dp(15), dp(15), dp(15));
            TextView typeLabel = new TextView(this);
            typeLabel.setText("转账类型");
            typeLabel.setTextSize(14);
            typeLabel.setTextColor(0xff333333);
            account.addView(typeLabel, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout typeRow = new LinearLayout(this);
            typeRow.setGravity(Gravity.CENTER_VERTICAL);
            android.widget.CheckBox income = new android.widget.CheckBox(this);
            income.setText("收入");
            income.setTextSize(13);
            income.setChecked(true);
            android.widget.CheckBox expend = new android.widget.CheckBox(this);
            expend.setText("支出");
            expend.setTextSize(13);
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            elp.leftMargin = dp(30);
            typeRow.addView(income);
            typeRow.addView(expend, elp);
            account.addView(typeRow);
            income.setOnCheckedChangeListener((b, checked) -> { if (checked) expend.setChecked(false); });
            expend.setOnCheckedChangeListener((b, checked) -> { if (checked) income.setChecked(false); });
            body.addView(account);

            View d2 = new View(this);
            d2.setBackgroundColor(0xffeeeeee);
            LinearLayout.LayoutParams d2lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            d2lp.leftMargin = dp(14);
            d2lp.rightMargin = dp(16);
            body.addView(d2, d2lp);
        }

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

        FrameLayout senderRow = new FrameLayout(this);
        LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srlp.topMargin = dp(15);

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
        senderRow.addView(left);

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
        senderRow.addView(right, rightLp);
        panel.addView(senderRow, srlp);

        TextView change = new TextView(this);
        change.setText("切换收钱人 ›");
        change.setTextSize(13);
        change.setTextColor(0xff888888);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(5);
        change.setOnClickListener(v -> editRecipient());
        panel.addView(change, clp);

        body.addView(panel);

        setContentView(root);
    }

    private void select(boolean self) {
        leftCb.setChecked(!self);
        rightCb.setChecked(self);
    }

    private void editRecipient() {
        EditText input = new EditText(this);
        input.setHint("收钱人昵称");
        input.setText("你");
        new AlertDialog.Builder(this)
                .setTitle("切换收钱人")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, w) -> Toast.makeText(this,
                        "收钱人：" + input.getText().toString().trim(), Toast.LENGTH_SHORT).show())
                .show();
    }

    private void submit() {
        double v = 0;
        try {
            v = Double.parseDouble(money.getText().toString().trim());
        } catch (Exception ignored) { }
        if (v <= 0) {
            Toast.makeText(this, "请输入转账金额", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "转账已发送", Toast.LENGTH_SHORT).show();
        finish();
    }

    private Bitmap backArrow() {
        int s = 120;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xff333333);
        p.setStyle(android.graphics.Paint.Style.STROKE);
        p.setStrokeWidth(4);
        p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
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
