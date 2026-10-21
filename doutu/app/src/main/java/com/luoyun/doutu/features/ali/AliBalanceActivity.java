package com.luoyun.doutu.features.ali;

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
 * 支付宝「余额」页 —— 按真机 activity_ali_balance.xml 重画：
 * 蓝渐变头（返回 / 余额 / …）+ 资金安全有保障 + 可用余额(元) 0.00 + 提现·充值 +
 * 专款专用，不乱花 + 上传自己余额页面截图 + 余额变动明细（全部）。
 */
public final class AliBalanceActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xff3e7af6);
        w.setNavigationBarColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f5f5);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable hbg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xff3e7af6, 0xff8cc3fa});
        header.setBackground(hbg);
        header.setPadding(dp(16), dp(10), dp(16), dp(20));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(Color.WHITE);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(32), dp(36)));
        TextView title = new TextView(this);
        title.setText("余额");
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(36), 1f));
        TextView more = new TextView(this);
        more.setText("···");
        more.setTextSize(18);
        more.setTextColor(Color.WHITE);
        bar.addView(more);
        header.addView(bar);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(10), dp(12), dp(16));
        scroll.addView(body);

        // 余额卡
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(Color.WHITE);
        cbg.setCornerRadius(dp(12));
        card.setBackground(cbg);
        card.setPadding(dp(16), dp(14), dp(16), dp(16));

        TextView safe = new TextView(this);
        safe.setText("⛨ 资金安全有保障 ›");
        safe.setTextSize(13);
        safe.setTextColor(0xff3e7af6);
        safe.setGravity(Gravity.CENTER);
        card.addView(safe);
        TextView label = new TextView(this);
        label.setText("可用余额(元)  👁");
        label.setTextSize(14);
        label.setTextColor(0xff333333);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(18);
        card.addView(label, llp);
        TextView money = new TextView(this);
        money.setText("0.00");
        money.setTextSize(40);
        money.setTextColor(0xff1a1a1a);
        money.setGravity(Gravity.CENTER);
        card.addView(money);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        alp.topMargin = dp(18);
        TextView withdraw = new TextView(this);
        withdraw.setText("提现");
        withdraw.setTextSize(16);
        withdraw.setTextColor(0xff333333);
        withdraw.setGravity(Gravity.CENTER);
        GradientDrawable wbg = new GradientDrawable();
        wbg.setColor(Color.WHITE);
        wbg.setStroke(dp(1), 0xffdddddd);
        wbg.setCornerRadius(dp(22));
        withdraw.setBackground(wbg);
        TextView recharge = new TextView(this);
        recharge.setText("充值");
        recharge.setTextSize(16);
        recharge.setTextColor(Color.WHITE);
        recharge.setGravity(Gravity.CENTER);
        GradientDrawable rbg = new GradientDrawable();
        rbg.setColor(0xff3e7af6);
        rbg.setCornerRadius(dp(22));
        recharge.setBackground(rbg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(0, dp(44), 1f);
        blp2.leftMargin = dp(12);
        actions.addView(withdraw, blp);
        actions.addView(recharge, blp2);
        card.addView(actions, alp);

        View divider = new View(this);
        divider.setBackgroundColor(0xffeeeeee);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dlp.topMargin = dp(16);
        card.addView(divider, dlp);

        LinearLayout promo = new LinearLayout(this);
        promo.setGravity(Gravity.CENTER_VERTICAL);
        promo.setPadding(0, dp(12), 0, 0);
        TextView promoText = new TextView(this);
        promoText.setText("专款专用，不乱花");
        promoText.setTextSize(14);
        promoText.setTextColor(0xff333333);
        promo.addView(promoText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView go = new TextView(this);
        go.setText("去看看");
        go.setTextSize(13);
        go.setTextColor(Color.WHITE);
        go.setGravity(Gravity.CENTER);
        GradientDrawable gbg = new GradientDrawable();
        gbg.setColor(0xff3e7af6);
        gbg.setCornerRadius(dp(14));
        go.setBackground(gbg);
        promo.addView(go, new LinearLayout.LayoutParams(dp(62), dp(28)));
        card.addView(promo);
        body.addView(card);

        // 上传余额截图
        LinearLayout upload = new LinearLayout(this);
        upload.setOrientation(LinearLayout.VERTICAL);
        upload.setGravity(Gravity.CENTER);
        upload.setPadding(0, dp(20), 0, dp(18));
        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextSize(26);
        plus.setTextColor(0xff333333);
        plus.setGravity(Gravity.CENTER);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(0xffffd84d);
        pbg.setShape(GradientDrawable.OVAL);
        plus.setBackground(pbg);
        upload.addView(plus, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView upText = new TextView(this);
        upText.setText("上传自己余额页面截图");
        upText.setTextSize(13);
        upText.setTextColor(0xff666666);
        upText.setPadding(0, dp(8), 0, 0);
        upload.addView(upText);
        body.addView(upload);

        // 余额变动明细
        LinearLayout bills = new LinearLayout(this);
        bills.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bbg = new GradientDrawable();
        bbg.setColor(Color.WHITE);
        bbg.setCornerRadius(dp(10));
        bills.setBackground(bbg);
        bills.setPadding(dp(14), dp(16), dp(14), dp(16));
        TextView bTitle = new TextView(this);
        bTitle.setText("余额变动明细");
        bTitle.setTextSize(15);
        bTitle.setTextColor(0xff333333);
        bills.addView(bTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView all = new TextView(this);
        all.setText("全部 ›");
        all.setTextSize(14);
        all.setTextColor(0xff888888);
        bills.addView(all);
        body.addView(bills);

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
