package com.luoyun.doutu.features.tools;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 「更多功能及教程」页 —— 照 歪点点 4.0.5.39 真机截图重建：
 * 黄色标题栏（返回 + 功能及教程）、微信/支付宝/QQ/其他/云相册/视频 六个页签，
 * 页签下面一条橙色指示线，内容区是浅灰底 + 空状态（幽灵图标 + 没有更多内容了）。
 *
 * <p>参考包里这一页的教程条目也是服务端下发的，当前线上为空，所以空状态就是
 * 真机上的实际样子；页签仍然可以切换，切换后保持同一套空状态。
 */
public final class ToolsTutorialActivity extends Activity {
    private static final String[] TABS = {"微信", "支付宝", "QQ", "其他", "云相册", "视频"};
    private static final int YELLOW = 0xfff9db61;
    private static final int INK = 0xff333333;
    private static final int SUB = 0xff999999;
    private static final int ORANGE = 0xffff7a1a;

    private LinearLayout tabRow;
    private int selected = 0;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent().getBooleanExtra("selftest", false)) { finish(); return; }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff7f7f7);

        // ── 标题栏 ───────────────────────────────────────────────
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(YELLOW);
        bar.setPadding(dp(12), 0, dp(12), 0);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(28);
        back.setTextColor(0xff222222);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView title = new TextView(this);
        title.setText("功能及教程");
        title.setTextSize(18);
        title.setTextColor(0xff222222);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(44), dp(56)));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        // ── 页签 ─────────────────────────────────────────────────
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setBackgroundColor(Color.WHITE);
        hs.addView(tabRow);
        root.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        View line = new View(this);
        line.setBackgroundColor(0xffe5e5e5);
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // ── 空状态 ───────────────────────────────────────────────
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(0, dp(200), 0, 0);
        GhostView ghost = new GhostView();
        body.addView(ghost, new LinearLayout.LayoutParams(dp(96), dp(96)));
        TextView empty = new TextView(this);
        empty.setText("没有更多内容了");
        empty.setTextSize(14);
        empty.setTextColor(SUB);
        empty.setGravity(Gravity.CENTER);
        body.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        buildTabs();
    }

    private void buildTabs() {
        tabRow.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, 0, 0, 0);
            TextView label = new TextView(this);
            label.setText(TABS[i]);
            label.setTextSize(13);
            label.setSingleLine(true);
            label.setGravity(Gravity.CENTER);
            label.setTextColor(i == selected ? INK : SUB);
            cell.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            View indicator = new View(this);
            indicator.setBackgroundColor(i == selected ? ORANGE : Color.TRANSPARENT);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(22), dp(3));
            ilp.bottomMargin = dp(4);
            cell.addView(indicator, ilp);
            cell.setOnClickListener(v -> {
                selected = index;
                buildTabs();
            });
            tabRow.addView(cell, new LinearLayout.LayoutParams(dp(60), ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    /** 参考图里的空状态幽灵：圆顶轮廓 + 两只叉眼 + 一张小嘴。 */
    private final class GhostView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        GhostView() {
            super(ToolsTutorialActivity.this);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setColor(0xffdedede);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(3));
            Path head = new Path();
            RectF top = new RectF(w * .18f, h * .12f, w * .82f, h * .62f);
            head.addArc(top, 180, 180);
            head.lineTo(w * .82f, h * .86f);
            head.lineTo(w * .66f, h * .74f);
            head.lineTo(w * .50f, h * .86f);
            head.lineTo(w * .34f, h * .74f);
            head.lineTo(w * .18f, h * .86f);
            head.close();
            c.drawPath(head, p);
            p.setStyle(Paint.Style.FILL);
            drawCross(c, w * .38f, h * .40f);
            drawCross(c, w * .62f, h * .40f);
            Path mouth = new Path();
            mouth.moveTo(w * .50f, h * .52f);
            mouth.quadTo(w * .58f, h * .52f, w * .58f, h * .60f);
            mouth.close();
            c.drawPath(mouth, p);
        }

        private void drawCross(Canvas c, float x, float y) {
            float r = dp(5);
            p.setStrokeWidth(dp(3));
            p.setStyle(Paint.Style.STROKE);
            c.drawLine(x - r, y - r, x + r, y + r, p);
            c.drawLine(x + r, y - r, x - r, y + r, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
