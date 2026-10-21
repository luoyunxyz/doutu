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
 * 「淘宝账单详情」—— 按真机截图重画：
 * 卖家已发货 / 还剩6天2时自动确认 + 运输中物流卡 + 商品卡（周小二美妆、实付款、订单编号、
 * 交易快照、花呗账单）+ 订单服务（运费险、延长收货/查看物流/确认收货）。
 */
public final class AliOrderActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(0xfff5f5f5);
        w.setNavigationBarColor(Color.WHITE);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f5f5);

        // 顶栏
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(0xfff5f5f5);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(28);
        back.setTextColor(0xff333333);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new FrameLayout.LayoutParams(dp(48), dp(78)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(2), dp(12), 0, 0);
        TextView title = new TextView(this);
        title.setText("卖家已发货");
        title.setTextSize(22);
        title.setTextColor(0xff1a1a1a);
        titles.addView(title);
        TextView sub = new TextView(this);
        sub.setText("还剩6天2时自动确认");
        sub.setTextSize(12);
        sub.setTextColor(0xff888888);
        titles.addView(sub);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(46);
        tlp.topMargin = dp(10);
        bar.addView(titles, tlp);
        TextView more = new TextView(this);
        more.setText("···");
        more.setTextSize(20);
        more.setTextColor(0xff333333);
        more.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(dp(44), dp(78));
        mlp.gravity = Gravity.END;
        bar.addView(more, mlp);
        TextView badge = new TextView(this);
        badge.setText("13");
        badge.setTextSize(11);
        badge.setTextColor(Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable bbg = new GradientDrawable();
        bbg.setColor(0xffff6a00);
        bbg.setShape(GradientDrawable.OVAL);
        badge.setBackground(bbg);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(22), dp(22));
        blp.gravity = Gravity.END | Gravity.TOP;
        blp.topMargin = dp(14);
        blp.rightMargin = dp(4);
        bar.addView(badge, blp);
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(4), dp(12), dp(16));
        scroll.addView(body);

        // 物流卡
        LinearLayout logistics = card(body);
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextSize(10);
        dot.setTextColor(0xffff5000);
        statusRow.addView(dot);
        TextView status = new TextView(this);
        status.setText("运输中 20:42");
        status.setTextSize(16);
        status.setTextColor(0xffff5000);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slp.leftMargin = dp(6);
        statusRow.addView(status, slp);
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(0xffbbbbbb);
        statusRow.addView(arrow);
        logistics.addView(statusRow);
        logistics.addView(text("【池州市】快件已由【三二四便利店】签收，如有疑问请电联派件业务员：18356653050，签…", 14, 0xff333333));
        logistics.addView(text("送至 东湖中路269号胜利新村6号楼269号胜利新村6号楼4401", 15, 0xff1a1a1a));
        logistics.addView(text("肆意 86-152****1201  号码保护中", 13, 0xff888888));
        logistics.addView(text("取件可出示虚拟号 86-152****1201", 13, 0xff888888));

        // 商品卡
        LinearLayout product = card(body);
        product.addView(text("天猫  周小二美妆 ›", 15, 0xff333333));
        LinearLayout itemRow = new LinearLayout(this);
        itemRow.setPadding(0, dp(10), 0, 0);
        View image = new View(this);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setColor(0xffe0e0e0);
        ibg.setCornerRadius(dp(6));
        image.setBackground(ibg);
        itemRow.addView(image, new LinearLayout.LayoutParams(dp(80), dp(80)));
        LinearLayout itemInfo = new LinearLayout(this);
        itemInfo.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams iilp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        iilp.leftMargin = dp(10);
        itemInfo.addView(text("pony王菲菲同款270遮瑕刷无…", 15, 0xff1a1a1a), iilp);
        itemInfo.addView(text("270遮瑕刷;人造纤维", 13, 0xff888888));
        itemInfo.addView(text("运费险", 12, 0xffff5000));
        itemRow.addView(itemInfo, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout price = new LinearLayout(this);
        price.setOrientation(LinearLayout.VERTICAL);
        price.setGravity(Gravity.END);
        price.addView(text("¥9.90", 15, 0xff1a1a1a));
        price.addView(text("x1", 12, 0xff888888));
        itemRow.addView(price);
        product.addView(itemRow);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, dp(12), 0, 0);
        buttons.addView(pill("加入购物车", 0xff333333, false));
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        b2.leftMargin = dp(10);
        buttons.addView(pill("退款", 0xff333333, false), b2);
        product.addView(buttons);

        LinearLayout paidRow = new LinearLayout(this);
        paidRow.setGravity(Gravity.CENTER_VERTICAL);
        paidRow.setPadding(0, dp(14), 0, 0);
        paidRow.addView(text("实付款", 15, 0xff333333), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        paidRow.addView(text("¥10.90", 19, 0xffff5000));
        product.addView(paidRow);

        product.addView(row("订单编号:", "3555369936238415557 | 复制"));
        product.addView(row("交易快照", "发生交易争议时，可作为判断依据 ›"));
        product.addView(row("花呗账单:", "去支付宝中查看 ›"));
        product.addView(text("查看更多 ⌄", 13, 0xff888888));

        // 订单服务
        LinearLayout service = card(body);
        service.addView(text("订单服务 保障中", 16, 0xff1a1a1a));
        service.addView(row("运费险", "七天无理由"));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(12), 0, 0);
        actions.addView(pill("延长收货", 0xff333333, false));
        LinearLayout.LayoutParams a2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        a2.leftMargin = dp(8);
        actions.addView(pill("查看物流", 0xff333333, false), a2);
        LinearLayout.LayoutParams a3 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        a3.leftMargin = dp(8);
        actions.addView(pill("确认收货", 0xffff5000, true), a3);
        service.addView(actions);

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(10));
        card.setBackground(bg);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        parent.addView(card, lp);
        return card;
    }

    private TextView text(String value, int size, int color) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setLineSpacing(dp(4), 1f);
        tv.setPadding(0, dp(6), 0, dp(6));
        return tv;
    }

    private LinearLayout row(String name, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(14);
        n.setTextColor(0xff333333);
        row.addView(n, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(13);
        v.setTextColor(0xff888888);
        row.addView(v);
        return row;
    }

    private TextView pill(String label, int color, boolean filled) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTextColor(filled ? color : 0xff333333);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        if (filled) {
            bg.setColor(0xfffff1e6);
            bg.setStroke(dp(1), color);
        } else {
            bg.setColor(Color.WHITE);
            bg.setStroke(dp(1), 0xffdddddd);
        }
        bg.setCornerRadius(dp(17));
        tv.setBackground(bg);
        tv.setPadding(dp(14), 0, dp(14), 0);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
