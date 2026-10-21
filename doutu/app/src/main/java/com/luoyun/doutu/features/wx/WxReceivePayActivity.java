package com.luoyun.doutu.features.wx;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 微信「收付款」页 —— 按真机截图重画：
 * 顶栏（返回 / 收付款）+ 付款码·二维码收款 页签 + 演示提示 + 付款码（模拟）+
 * 演示金额（可选）+ 绿色「确认演示」。
 */
public final class WxReceivePayActivity extends Activity {
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
        title.setText("收付款");
        title.setTextSize(17);
        title.setTextColor(0xff333333);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER;
        bar.addView(title, tlp);
        root.addView(bar);

        // 页签卡
        LinearLayout tabs = new LinearLayout(this);
        tabs.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        tabsLp.leftMargin = dp(12);
        tabsLp.rightMargin = dp(12);
        tabsLp.topMargin = dp(10);
        TextView pay = tab("付款码", true);
        TextView receive = tab("二维码收款", false);
        tabs.addView(pay, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        tabs.addView(receive, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        root.addView(tabs, tabsLp);

        // 主体卡
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(Color.WHITE);
        cbg.setCornerRadius(dp(10));
        card.setBackground(cbg);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(12);
        clp.rightMargin = dp(12);
        clp.topMargin = dp(10);
        root.addView(card, clp);

        TextView tip = new TextView(this);
        tip.setText("仅供界面演示，不会发生真实支付或资金交易");
        tip.setTextSize(12);
        tip.setTextColor(0xff999999);
        tip.setGravity(Gravity.CENTER);
        card.addView(tip);

        ImageView qr = new ImageView(this);
        qr.setImageBitmap(makeQr(280));
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(dp(180), dp(180));
        qlp.gravity = Gravity.CENTER;
        qlp.topMargin = dp(16);
        card.addView(qr, qlp);

        TextView label = new TextView(this);
        label.setText("付款码（模拟）");
        label.setTextSize(14);
        label.setTextColor(0xff333333);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(12);
        card.addView(label, llp);

        LinearLayout amountRow = new LinearLayout(this);
        amountRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        alp.topMargin = dp(16);
        TextView amountLabel = new TextView(this);
        amountLabel.setText("演示金额（可选）");
        amountLabel.setTextSize(14);
        amountLabel.setTextColor(0xff333333);
        amountRow.addView(amountLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        EditText input = new EditText(this);
        input.setHint("输入金额");
        input.setTextSize(14);
        input.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        GradientDrawable ibg = new GradientDrawable();
        ibg.setColor(Color.WHITE);
        ibg.setStroke(dp(1), 0xffe0e0e0);
        ibg.setCornerRadius(dp(6));
        input.setBackground(ibg);
        input.setPadding(dp(10), 0, dp(10), 0);
        amountRow.addView(input, new LinearLayout.LayoutParams(dp(160), dp(44)));
        card.addView(amountRow, alp);

        TextView confirm = new TextView(this);
        confirm.setText("确认演示");
        confirm.setTextSize(16);
        confirm.setTextColor(Color.WHITE);
        confirm.setGravity(Gravity.CENTER);
        GradientDrawable gbg = new GradientDrawable();
        gbg.setColor(0xff07c160);
        gbg.setCornerRadius(dp(6));
        confirm.setBackground(gbg);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        glp.topMargin = dp(16);
        card.addView(confirm, glp);

        setContentView(root);
    }

    private TextView tab(String text, boolean active) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(active ? 0xff07c160 : 0xff333333);
        tv.setGravity(Gravity.CENTER);
        tv.getPaint().setFakeBoldText(active);
        return tv;
    }

    /** 确定性的伪二维码图案（25×25，带三个定位角）。 */
    private Bitmap makeQr(int size) {
        int n = 25;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        int cell = size / n;
        int pad = (size - cell * n) / 2;
        bmp.eraseColor(Color.WHITE);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                boolean dark = ((x * 7 + y * 13 + (x * y) % 5) % 3) == 0;
                if (inFinder(x, y, 0, 0) || inFinder(x, y, n - 7, 0) || inFinder(x, y, 0, n - 7)) {
                    dark = true;
                }
                if (inFinderRing(x, y, 0, 0) || inFinderRing(x, y, n - 7, 0) || inFinderRing(x, y, 0, n - 7)) {
                    dark = false;
                }
                if (!dark) continue;
                for (int dy = 0; dy < cell; dy++) {
                    for (int dx = 0; dx < cell; dx++) {
                        int px = pad + x * cell + dx;
                        int py = pad + y * cell + dy;
                        if (px < size && py < size) bmp.setPixel(px, py, Color.BLACK);
                    }
                }
            }
        }
        return bmp;
    }

    private boolean inFinder(int x, int y, int fx, int fy) {
        return x >= fx && x < fx + 7 && y >= fy && y < fy + 7;
    }

    private boolean inFinderRing(int x, int y, int fx, int fy) {
        if (!inFinder(x, y, fx, fy)) return false;
        boolean border = x == fx || x == fx + 6 || y == fy || y == fy + 6;
        return !border;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
