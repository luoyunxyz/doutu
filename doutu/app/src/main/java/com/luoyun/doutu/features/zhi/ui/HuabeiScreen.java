package com.luoyun.doutu.features.zhi.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.luoyun.doutu.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 花呗页（小支主页 - 我的 - 花呗）。
 *
 * <p>整页按照参考截图 {@code c593bbdcd7cea53e8e1a8178ea5e3dd1.jpg} 1:1 重建：画布宽 1280px，
 * 纵向内容 5805px，跟着手指上下滚动。参考图纵向切成六条整宽模块——蓝底那段、红包横幅、八个入口
 * 的白卡、花呗亲密度卡、服务/兴趣那一段、底下的瀑布流——一条条贴回去，因为相邻两条都出自同一张
 * 图，接缝处严丝合缝。
 *
 * <p><b>月份跟当前月份走</b>：顶部「N月应还(元)」的 N 取系统当前月份，账单胶囊里「M月已累计…」
 * 的 M 取下一个月份。当当前月份正好等于参考图那个月（10 月）时，这两处保持位图原样，页面与参考图
 * 逐像素一致；到了别的月份，才把小色块盖在原位置、用当前月份重画一遍。
 *
 * <p>其余可改的数字（额度、还款日、亲密度、已累计、关注数）和那行「已还清」，都是用户点开改过之后
 * 才盖掉重画，没改过就保持参考图原样。
 */
public final class HuabeiScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;
    /** 整页内容高度（瀑布流切到参考图最下面那条为止）。 */
    public static final float CONTENT_H = 5805f;

    public static final class State {
        public float scroll;
        public final Map<String, Double> nums;
        public final Map<String, String> texts;

        public State(Map<String, Double> nums, Map<String, String> texts) {
            this.nums = nums;
            this.texts = texts;
        }
    }

    // ------------------------------------------------------------------ 调色板
    private static final int PAGE_BG = 0xfff2f3f7;
    private static final int BLUE = 0xff1777ff;
    private static final int BLUE_TEXT = 0xffb7e1ff;
    private static final int LABEL_TEXT = 0xffcfe0ff;
    private static final int PILL_TEXT = 0xffbce3ff;
    private static final int YELLOW = 0xffffe956;
    private static final int YELLOW_INK = 0xff4a4a3c;

    /** 参考图里那两处月份文字，当前月份正好是 10 月时不必盖。 */
    private static final String REF_LABEL = "10月应还(元)";
    private static final String REF_PILL = "11月已累计147.88，查看我的账单";

    private final LinkedHashMap<String, Bitmap> art = new LinkedHashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private final List<float[]> hitRects = new ArrayList<>();
    private final List<String> hitTokens = new ArrayList<>();

    // ------------------------------------------------------------------ 资源
    public void load(Resources res) {
        art.clear();
        art.put("top", BitmapFactory.decodeResource(res, R.drawable.hb_top));
        art.put("banner", BitmapFactory.decodeResource(res, R.drawable.hb_banner));
        art.put("icons", BitmapFactory.decodeResource(res, R.drawable.hb_icons));
        art.put("qinmi", BitmapFactory.decodeResource(res, R.drawable.hb_qinmi));
        art.put("tabs", BitmapFactory.decodeResource(res, R.drawable.hb_tabs));
        art.put("feed", BitmapFactory.decodeResource(res, R.drawable.hb_feed));
    }

    public float maxScroll(State s) {
        return Math.max(0f, CONTENT_H - H);
    }

    // ------------------------------------------------------------------ 月份
    /** 本月月份（1-12）。 */
    public static int currentMonth() {
        return Calendar.getInstance().get(Calendar.MONTH) + 1;
    }

    /** 下一个月（12 月之后回到 1 月）。 */
    public static int nextMonth() {
        int m = currentMonth();
        return m == 12 ? 1 : m + 1;
    }

    // ------------------------------------------------------------------ 入口
    public void draw(Canvas c, State s) {
        hitRects.clear();
        hitTokens.clear();

        c.drawColor(PAGE_BG);
        c.save();
        c.translate(0, -s.scroll);

        // ---- 模块：整宽一条条贴回 ----
        drawArt(c, "top", 0f, 0f);
        drawArt(c, "banner", 0f, 960f);
        drawArt(c, "icons", 0f, 1470f);
        drawArt(c, "qinmi", 0f, 1935f);
        drawArt(c, "tabs", 0f, 2790f);
        drawArt(c, "feed", 0f, 3660f);

        registerHits();

        // ---- 顶部那两处月份文字 + 用户改过的数字/文字 ----
        drawDynamic(c, s);

        c.restore();
    }

    private void drawDynamic(Canvas c, State s) {
        // 1 「N月应还(元)」：N = 当前月份
        String label = s.texts.containsKey("hb.repayLabel")
                ? s.texts.get("hb.repayLabel") : (currentMonth() + "月应还(元)");
        if (!label.equals(REF_LABEL)) {
            patch(c, 300, 326, 980, 392, BLUE);
            centerText(c, label, W / 2f, 381, 44, LABEL_TEXT, false);
        }

        // 2 「已还清」——这一行是整页居中的，改完之后仍旧居中显示
        if (s.texts.containsKey("hb.status")) {
            patch(c, 120, 394, 1160, 490, BLUE);
            centerText(c, s.texts.get("hb.status"), W / 2f, 474, 74, Color.WHITE, true);
        }

        // 3 账单胶囊：「M月已累计…」：M = 下一个月
        String pill = nextMonth() + "月已累计" + money(s, "hb.accrued", 147.88) + "，查看我的账单";
        if (!pill.equals(REF_PILL)) {
            patch(c, 282, 556, 948, 620, BLUE);
            text(c, pill, 289, 606, 42, PILL_TEXT, false);
        }

        // 4 还款日每月 N 日
        if (s.nums.containsKey("hb.repayDay")) {
            patch(c, 88, 802, 392, 854, BLUE);
            text(c, "还款日每月", 97, 845, 41, BLUE_TEXT, false);
            String n = plain(s, "hb.repayDay", 20);
            text(c, n, 97 + measure("还款日每月", 41, false), 845, 41, BLUE_TEXT, false);
            text(c, "日", 97 + measure("还款日每月", 41, false) + measure(n, 41, false),
                    845, 41, BLUE_TEXT, false);
        }

        // 5 总计额度 N可用
        if (s.nums.containsKey("hb.credit")) {
            patch(c, 514, 804, 766, 856, BLUE);
            String v = money(s, "hb.credit", 2710.98);
            text(c, v, 524, 847, 44, BLUE_TEXT, false);
            text(c, "可用", 524 + measure(v, 44, false), 847, 44, BLUE_TEXT, false);
        }

        // 6 亲密度 N+权益待解锁
        if (s.nums.containsKey("hb.qinmi")) {
            patch(c, 895, 802, 1184, 854, BLUE);
            String v = plain(s, "hb.qinmi", 10);
            text(c, v, 905, 845, 41, BLUE_TEXT, false);
            text(c, "+权益待解锁", 905 + measure(v, 41, false), 845, 41, BLUE_TEXT, false);
        }

        // 7 吃谷社区关注数
        if (s.nums.containsKey("hb.followers")) {
            patch(c, 688, 3750, 984, 3804, YELLOW);
            String v = plain(s, "hb.followers", 39569);
            text(c, v, 697, 3795, 41, YELLOW_INK, false);
            text(c, "人已关注", 697 + measure(v, 41, false), 3795, 41, YELLOW_INK, false);
        }
    }

    // ------------------------------------------------------------------ 热区
    private void hit(float l, float t, float rr, float b, String token) {
        hitRects.add(new float[]{l, t, rr, b});
        hitTokens.add(token);
    }

    public String hitTest(State s, float x, float y) {
        float cy = y + s.scroll;
        for (int i = hitRects.size() - 1; i >= 0; i--) {
            float[] b = hitRects.get(i);
            if (x >= b[0] && x <= b[2] && cy >= b[1] && cy <= b[3]) return hitTokens.get(i);
        }
        return "noop";
    }

    private void registerHits() {
        // 顶栏
        hit(30, 158, 128, 286, "back");
        hit(1022, 170, 1122, 270, "noop");
        hit(1158, 166, 1268, 274, "noop");

        // 顶部蓝底
        hit(506, 330, 776, 390, "text:hb.repayLabel");
        hit(524, 398, 758, 486, "text:hb.status");
        hit(282, 548, 950, 626, "num:hb.accrued");
        hit(88, 802, 392, 854, "num:hb.repayDay");
        hit(514, 804, 766, 856, "num:hb.credit");
        hit(895, 802, 1184, 854, "num:hb.qinmi");

        // 红包横幅
        hit(455, 1322, 810, 1406, "noop");

        // 八个入口
        hit(50, 1440, 1230, 1910, "noop");

        // 花呗亲密度
        hit(940, 2010, 1215, 2090, "noop");
        hit(80, 2160, 1215, 2320, "noop");
        hit(430, 2590, 850, 2710, "noop");

        // 服务 / 兴趣
        hit(60, 2790, 640, 2920, "noop");
        hit(645, 2790, 1220, 2920, "noop");
        // 分类
        hit(40, 2985, 1230, 3080, "noop");
        // 三张促销卡
        hit(40, 3110, 1250, 3410, "noop");

        // 瀑布流
        hit(40, 3430, 1250, 5805, "noop");
        // 关注数压在瀑布流那一块之上
        hit(688, 3750, 984, 3804, "num:hb.followers");
    }

    // ------------------------------------------------------------------ 数值
    private double val(State s, String key, double def) {
        Double v = s.nums.get(key);
        return v == null ? def : v;
    }

    private String money(State s, String key, double def) {
        return AssetScreen.formatMoney(val(s, key, def));
    }

    private String plain(State s, String key, double def) {
        return AssetScreen.formatPlain(val(s, key, def));
    }

    // ------------------------------------------------------------------ 图元
    private void drawArt(Canvas c, String name, float x, float y) {
        Bitmap bmp = art.get(name);
        if (bmp == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        c.drawBitmap(bmp, x, y, paint);
    }

    private void patch(Canvas c, float l, float t, float rr, float b, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRect(l, t, rr, b, paint);
    }

    private void text(Canvas c, String value, float x, float baseline, float size, int color,
                      boolean bold) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText(value, x, baseline, paint);
    }

    /** 居中的一行字：改过内容之后仍旧以页面中心为准排布。 */
    private void centerText(Canvas c, String value, float cx, float baseline, float size, int color,
                            boolean bold) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(value, cx, baseline, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private float measure(String value, float size, boolean bold) {
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextSize(size);
        return paint.measureText(value);
    }
}
