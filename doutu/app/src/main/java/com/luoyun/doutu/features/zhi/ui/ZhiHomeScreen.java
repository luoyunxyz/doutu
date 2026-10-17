package com.luoyun.doutu.features.zhi.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.luoyun.doutu.R;
import com.luoyun.doutu.features.zhi.data.LicaiVersion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Native rendition of the “小支主页” (Alipay-style home) page for the 支 service.
 *
 * <p>The artwork is rebuilt from primitives (paths, gradients, text) at the 1280x2816
 * reference canvas the source screenshot uses. Only the small pictographic app icons and
 * the promo fruit are cut out of the reference screenshot as bitmaps; the original
 * screenshot itself is never drawn. Everything on the page is a local mock: no account,
 * no network and no payment flow is involved.
 */
public final class ZhiHomeScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;
    public static final float NAV_TOP = 2560f;
    /** The page ends with the 芝麻信用 card; the tail only adds a short scroll. */
    public static final float CONTENT_H = 2990f;

    /** Mutable presentation state owned by the hosting activity. */
    public static final class State {
        public float scroll;
        public boolean plusMenu;
        /** Editable location shown next to the drop-down arrow. */
        public String address = "恩施土…";
        /** Right-hand label of the 最近消息 header, plus its unread dot. */
        public String newMessage = "7条新消息";
        public boolean unread = true;
        /** 最近消息 rows: {title, detail, time}. */
        public final String[][] messages = {
                {"支付宝会员", "你有33积分待领取，解锁酷洛米瓶中精灵主⋯", "刚刚"},
                {"花呗", "查看你的账单进度", "2小时前"},
        };
        /** 芝麻分 value; the last digit is masked when {@link #hideScoreTail} is set. */
        public String score = "638";
        public boolean hideScoreTail = true;
        /** Caption under the ring; also editable. */
        public String creditLabel = "信用良好";
    }

    private static final int INK = 0xff16181c;
    private static final int LABEL = 0xff26262a;
    private static final int MUTED = 0xff9aa0a8;
    private static final int BLUE = 0xff1677ff;
    private static final int CARD = 0xffffffff;
    private static final int PAGE_BG = 0xfff2f3f7;

    private static final float[] GRID_COLS = {145f, 391f, 638f, 885f, 1131f};
    private static final float[] COMMON_COLS = {151f, 386f, 634f, 875f, 1125f};
    private static final float[] COL_EDGE = {0f, 268f, 514f, 761f, 1008f, 1280f};

    private static final String[] GRID_ROW1 = {"alipay", "job", "mobile", "sport", "movie"};
    private static final String[] GRID_ROW1_TEXT = {"支付宝会员", "就业", "手机营业厅", "运动", "淘票票"};
    private static final String[] GRID_ROW2 = {"ant", "city", "purse", "yuebao", "more"};
    private static final String[] GRID_ROW2_TEXT = {"蚂蚁保", "市民中心", "支付宝荷包", "余额宝", "更多"};
    private static final String[] COMMON = {"car", "huabei", "suishen", "med", "fund"};
    private static final String[] COMMON_TEXT = {"鄂车管", "花呗", "随申办", "医疗健康", "基金"};

    private static final String[] ACTIONS = {"扫一扫", "收付款", "出行", "卡包"};
    private static final float[] ACTION_X = {159f, 479f, 799f, 1119f};
    private static final String[] ACTION_ART = {"actScan", "actPay", "actTravel", "actCard"};

    /** 顶部四个入口那条带子：原资源把两套配色成对写死，跟着黑卡档位切。 */
    private static final float BAND_H = 648f;
    private static final int BAND_BLUE_DARK = 0xff176cfc;
    private static final int BAND_BLUE_LIGHT = 0xff5387f8;
    private static final int BAND_HEIKA = 0xff0f152c;

    private static final float[] NAV_X = {128f, 382f, 639f, 898f, 1151f};
    private static final String[] PLUS_ITEMS = {"扫一扫", "收款", "添加朋友", "历史记录"};
    private static final String[] PLUS_ART = {"plusScan", "plusPay", "plusFriend", "plusHistory"};

    // The “+” pop-up: a near-black panel with a beak pointing at the button and four rows.
    private static final float PLUS_L = 768f;
    private static final float PLUS_T = 330f;
    private static final float PLUS_R = 1258f;
    private static final float PLUS_B = 1071f;
    private static final float PLUS_RADIUS = 37f;
    private static final float PLUS_ROW_H = (PLUS_B - PLUS_T) / PLUS_ITEMS.length;
    private static final float PLUS_ICON_CX = 874f;
    private static final float PLUS_ICON_SIZE = 85f;
    private static final float PLUS_TEXT_X = 945f;
    private static final float PLUS_TEXT_SIZE = 54f;
    private static final float PLUS_DIVIDER_L = 917f;
    private static final float PLUS_BEAK_CX = 1192f;
    private static final float PLUS_BEAK_TIP = 308f;
    private static final float PLUS_BEAK_HALF = 21f;
    private static final int PLUS_PANEL = 0xe6000000;
    private static final int PLUS_HAIRLINE = 0x24ffffff;

    private static final String[] FEED_NAMES_TUIJIAN = {
            "芝麻信用分月报", "花呗账单分期", "余额宝收益提醒", "蚂蚁森林能量",
            "医疗健康卡", "小荷包家庭账本"};
    private static final String[] FEED_DESC_TUIJIAN = {
            "本月信用进度已更新，去看看", "3月账单可分期，手续费低至0.3%",
            "昨日收益 +0.86 元", "收能量，种一棵属于自己的树",
            "电子健康卡，挂号缴费一码通", "和家人一起记录每一笔开销"};
    private static final int[] FEED_COLORS = {
            0xff4a8df7, 0xff33b07a, 0xffff9c25, 0xff6d7bf0, 0xffe2503a, 0xff2fb0c8};

    private final Map<String, RectF> iconRects = new LinkedHashMap<>();
    private final Map<String, Bitmap> art = new LinkedHashMap<>();
    private final BlackCardNav blackCardNav = new BlackCardNav();

    private final Path path = new Path();
    private final RectF r = new RectF();

    public ZhiHomeScreen() {
        icon("alipay", 85, 604, 195, 720);
        icon("job", 337, 584, 478, 718);
        icon("mobile", 593, 612, 683, 716);
        icon("sport", 830, 615, 941, 714);
        icon("movie", 1081, 611, 1184, 718);
        icon("ant", 89, 846, 200, 949);
        icon("city", 339, 848, 445, 951);
        icon("purse", 590, 844, 687, 954);
        icon("yuebao", 833, 843, 937, 954);
        icon("more", 1083, 849, 1182, 945);
        icon("car", 99, 1208, 198, 1303);
        icon("huabei", 340, 1202, 446, 1308);
        icon("suishen", 583, 1199, 693, 1304);
        icon("med", 827, 1208, 938, 1303);
        icon("fund", 1073, 1208, 1181, 1304);
        icon("msgA", 64, 1592, 119, 1654);
        icon("msgH", 64, 1753, 118, 1807);
        icon("actScan", 97, 316, 221, 440);
        icon("actPay", 417, 316, 541, 440);
        icon("actTravel", 737, 316, 861, 440);
        icon("actCard", 1057, 316, 1181, 440);
        icon("tabbar", 0, 1878, 1280, 2046);
        icon("credit", 0, 2056, 1280, 2590);
        icon("locArrow", 262, 178, 312, 212);
        icon("navbar", 0, 2560, 1280, 2816);
        icon("fruit", 361, 180, 443, 262);
        for (int i = 0; i < PLUS_ITEMS.length; i++) {
            float cy = PLUS_T + PLUS_ROW_H * (i + 0.5f);
            icon(PLUS_ART[i], PLUS_ICON_CX - PLUS_ICON_SIZE / 2f, cy - PLUS_ICON_SIZE / 2f,
                    PLUS_ICON_CX + PLUS_ICON_SIZE / 2f, cy + PLUS_ICON_SIZE / 2f);
        }
    }

    private void icon(String name, float x1, float y1, float x2, float y2) {
        iconRects.put(name, new RectF(x1, y1, x2, y2));
    }

    /** Decodes the cut-out bitmaps. Safe to call again after a configuration change. */
    public void load(Resources res) {
        art.clear();
        art.put("alipay", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_alipay));
        art.put("job", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_job));
        art.put("mobile", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_mobile));
        art.put("sport", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_sport));
        art.put("movie", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_movie));
        art.put("ant", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_ant));
        art.put("city", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_city));
        art.put("purse", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_purse));
        art.put("yuebao", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_yuebao));
        art.put("more", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_more));
        art.put("car", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_car));
        art.put("huabei", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_huabei));
        art.put("suishen", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_suishen));
        art.put("med", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_med));
        art.put("fund", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_fund));
        art.put("msgA", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_msg_a));
        art.put("msgH", BitmapFactory.decodeResource(res, R.drawable.zhi_icon_msg_h));
        art.put("actScan", BitmapFactory.decodeResource(res, R.drawable.zhi_act_scan));
        art.put("actPay", BitmapFactory.decodeResource(res, R.drawable.zhi_act_pay));
        art.put("actTravel", BitmapFactory.decodeResource(res, R.drawable.zhi_act_travel));
        art.put("actCard", BitmapFactory.decodeResource(res, R.drawable.zhi_act_card));
        art.put("tabbar", BitmapFactory.decodeResource(res, R.drawable.zhi_tabbar));
        art.put("credit", BitmapFactory.decodeResource(res, R.drawable.zhi_credit_card));
        art.put("locArrow", BitmapFactory.decodeResource(res, R.drawable.zhi_loc_arrow));
        art.put("navbar", BitmapFactory.decodeResource(res, R.drawable.zhi_navbar));
        art.put("fruit", BitmapFactory.decodeResource(res, R.drawable.zhi_fruit));
        art.put("plusScan", BitmapFactory.decodeResource(res, R.drawable.zhi_plus_scan));
        art.put("plusPay", BitmapFactory.decodeResource(res, R.drawable.zhi_plus_pay));
        art.put("plusFriend", BitmapFactory.decodeResource(res, R.drawable.zhi_plus_addfriend));
        art.put("plusHistory", BitmapFactory.decodeResource(res, R.drawable.zhi_plus_history));
        art.put("hkScan", BitmapFactory.decodeResource(res, R.drawable.hk_home_scan));
        art.put("hkPay", BitmapFactory.decodeResource(res, R.drawable.hk_home_pay));
        art.put("hkTravel", BitmapFactory.decodeResource(res, R.drawable.hk_home_travel));
        art.put("hkCard", BitmapFactory.decodeResource(res, R.drawable.hk_home_card));
        art.put("hkMobile", BitmapFactory.decodeResource(res, R.drawable.hk_feature_mobile));
        art.put("hkCity", BitmapFactory.decodeResource(res, R.drawable.hk_feature_city));
        art.put("hkYuebao", BitmapFactory.decodeResource(res, R.drawable.hk_feature_yuebao));
        art.put("hkMovie", BitmapFactory.decodeResource(res, R.drawable.hk_feature_movie));
        art.put("hkMore", BitmapFactory.decodeResource(res, R.drawable.hk_feature_more));
        blackCardNav.load(res);
        iconRects.put("hkScan", iconRects.get("actScan"));
        iconRects.put("hkPay", iconRects.get("actPay"));
        iconRects.put("hkTravel", iconRects.get("actTravel"));
        iconRects.put("hkCard", iconRects.get("actCard"));
        iconRects.put("hkMobile", iconRects.get("mobile"));
        iconRects.put("hkCity", iconRects.get("city"));
        iconRects.put("hkYuebao", iconRects.get("yuebao"));
        iconRects.put("hkMovie", iconRects.get("movie"));
        iconRects.put("hkMore", iconRects.get("more"));
    }

    public float maxScroll() {
        return Math.max(0f, CONTENT_H - H);
    }

    // ------------------------------------------------------------------ drawing

    public void draw(Canvas c, State s) {
        c.drawColor(PAGE_BG);
        c.save();
        c.translate(0, -s.scroll);
        drawHeader(c, s);
        drawGridCard(c);
        drawCommonCard(c);
        drawMessages(c, s);
        drawTabs(c, s);
        drawCreditCard(c, s);
        c.restore();
        // Once the blue header has scrolled away the status bar sits on light content.
        if (s.scroll > 420f) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(PAGE_BG);
            c.drawRect(0, 0, W, 150, paint);
        }
        if (!blackCardNav.draw(c, 0, W, NAV_TOP, H)) drawArt(c, "navbar");
        if (s.plusMenu) drawPlusMenu(c);
    }

    private void drawHeader(Canvas c, State s) {
        boolean blackCard = LicaiVersion.blackCard(LicaiVersion.current());
        // The band the four entries sit on. The original hard-codes a pair of palettes for this one
        // block and picks between them by the black-card tier: the 普通蓝色 theme runs its blue
        // from the dark corner at the top right to the light one at the bottom left — which is the
        // way the reference's own capture runs, not straight down — and the 财富黑卡 skin lays the
        // white captions over the skin's own ground. The icons above the captions switch with it.
        if (blackCard) {
            paint.setShader(null);
            paint.setColor(BAND_HEIKA);
            c.drawRect(0, 0, W, BAND_H, paint);
        } else {
            paint.setShader(new LinearGradient(W, 0, 0, BAND_H,
                    BAND_BLUE_DARK, BAND_BLUE_LIGHT, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, BAND_H, paint);
            paint.setShader(null);
        }

        // Keep the address inside its own column so a long, hand-edited value cannot run
        // under the search field.
        String address = ellipsize(s.address, 200f, 50);
        text(c, address, 44, 244, 50, Color.WHITE, false);
        float textEnd = 44f + textWidth(address, 50, false);
        // The drop-down arrow is the cut-out from the reference screenshot; it is drawn
        // at the reference position, just right of the address text.
        r.set(textEnd + 14f, 204, textEnd + 64f, 238);
        drawArtAt(c, "locArrow", r);

        round(c, 339, 163, 1101, 276, 56, Color.WHITE);
        drawArt(c, "fruit");
        text(c, "花呗", 445, 240, 50, 0xff3c3c3e, false);
        round(c, 563, 190, 621, 249, 12, 0xffe2503a);
        centerText(c, "热", 592, 236, 38, Color.WHITE, true);
        line(c, 914, 192, 914, 240, 0xffd9dce1, 3);
        text(c, "搜索", 963, 240, 50, 0xff5a6474, false);

        stroke(Color.WHITE, 5);
        c.drawCircle(1190, 219, 37, paint);
        line(c, 1170, 219, 1210, 219, Color.WHITE, 5);
        line(c, 1190, 199, 1190, 239, Color.WHITE, 5);
        paint.setStyle(Paint.Style.FILL);

        String[] blackActions = {"hkScan", "hkPay", "hkTravel", "hkCard"};
        for (int i = 0; i < 4; i++) {
            drawArt(c, blackCard ? blackActions[i] : ACTION_ART[i]);
            centerText(c, ACTIONS[i], ACTION_X[i], 494, 47, Color.WHITE, false);
        }
    }


    private void drawGridCard(Canvas c) {
        boolean blackCard = LicaiVersion.blackCard(LicaiVersion.current());
        r.set(0, 545, W, 1032);
        paint.setShader(new LinearGradient(0, 545, 0, 736, 0xffc8daf9, 0xffffffff, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 36, 36, paint);
        paint.setShader(null);
        for (int i = 0; i < 5; i++) {
            String icon = i == 2 && blackCard ? "hkMobile" : GRID_ROW1[i];
            drawArt(c, icon);
            centerText(c, GRID_ROW1_TEXT[i], GRID_COLS[i], 764, 43, LABEL, false);
            String icon2 = i == 1 && blackCard ? "hkCity" : i == 3 && blackCard ? "hkYuebao"
                    : i == 4 && blackCard ? "hkMore" : GRID_ROW2[i];
            drawArt(c, icon2);
            centerText(c, GRID_ROW2_TEXT[i], GRID_COLS[i], 998, 42, LABEL, false);
        }
        round(c, 617, 1054, 642, 1064, 5, 0xff2b5fd9);
        round(c, 650, 1054, 680, 1064, 5, 0xffcfe0f7);
    }

    private void drawCommonCard(Canvas c) {
        round(c, 22, 1085, 1258, 1432, 26, CARD);
        path.reset();
        path.moveTo(22, 1085);
        path.lineTo(180, 1085);
        path.lineTo(198, 1142);
        path.lineTo(22, 1142);
        path.close();
        paint.setShader(new LinearGradient(22, 1085, 198, 1142, 0xff84b9ff, 0xff4a8df7, Shader.TileMode.CLAMP));
        c.drawPath(path, paint);
        paint.setShader(null);
        text(c, "常用", 44, 1131, 44, Color.WHITE, true);
        text(c, "全部", 1062, 1159, 45, 0xff33363b, false);
        chevron(c, 1200, 1140, 0xff9aa0a8);
        for (int i = 0; i < 5; i++) {
            drawArt(c, COMMON[i]);
            centerText(c, COMMON_TEXT[i], COMMON_COLS[i], 1355, 43, LABEL, false);
        }
        round(c, 600, 1386, 628, 1396, 5, 0xff2b5fd9);
        round(c, 636, 1386, 660, 1396, 5, 0xffcfe0f7);
    }

    private void drawMessages(Canvas c, State s) {
        round(c, 22, 1456, 1258, 1876, 26, CARD);
        text(c, "最近消息", 66, 1546, 44, 0xff8a8a8e, false);
        // The unread dot sits between the label and the chevron; it can be turned off.
        if (!s.newMessage.isEmpty()) textRight(c, s.newMessage, 1094, 1551, 45, 0xff4a4a4e, false);
        if (s.unread) circle(c, 1141, 1524, 22, 0xffe8452c);
        chevron(c, 1198, 1524, 0xff9aa0a8);
        drawArt(c, "msgA");
        text(c, s.messages[0][0], 144, 1640, 48, INK, true);
        text(c, s.messages[0][1], 144, 1709, 45, MUTED, false);
        textRight(c, s.messages[0][2], 1206, 1709, 40, 0xffb6bac0, false);
        drawArt(c, "msgH");
        text(c, s.messages[1][0], 144, 1798, 47, INK, true);
        float detailX = 144f + textWidth(s.messages[1][0], 47, true) + 16f;
        text(c, s.messages[1][1], detailX, 1798, 45, MUTED, false);
        textRight(c, s.messages[1][2], 1206, 1798, 42, 0xffb6bac0, false);
    }

    private float textWidth(String value, float size, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        return paint.measureText(value);
    }

    /** Trims text with a trailing ellipsis until it fits into maxWidth. */
    private String ellipsize(String value, float maxWidth, float size) {
        if (textWidth(value, size, false) <= maxWidth) return value;
        String cut = value;
        while (cut.length() > 1 && textWidth(cut + "…", size, false) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    private void drawTabs(Canvas c, State s) {
        // The bar itself is the reference artwork (including its typography); only the
        // 为你推荐 underline was removed from the crop and is drawn here. The 秒杀商品
        // half is a static label in the reference, so it is not interactive.
        drawArt(c, "tabbar");
        round(c, 293, 2002, 347, 2013, 6, 0xff2f6ff0);
    }

    private void drawCreditCard(Canvas c, State s) {
        // The card body (ring, score, right-hand panel) is the reference artwork; the
        // “信用良好” caption was removed from it and is rendered here.
        round(c, 12, 2058, 1268, 2690, 28, CARD);
        drawArt(c, "credit");
        String caption = ellipsize(s.creditLabel, 240f, 34);
        if (!caption.isEmpty()) centerTextScaled(c, caption, 348, 2557, 34, 1.12f, 0xff3f7fd0);
        // The score is editable; the reference only exposes its first digits.
        String score = s.score;
        if (!score.isEmpty() && s.hideScoreTail) {
            score = score.substring(0, score.length() - 1) + "*";
        }
        if (!score.isEmpty()) centerText(c, score, 345, 2470, 84, 0xff3c71f0, true);
    }

    private void drawPlusMenu(Canvas c) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PLUS_PANEL);
        path.reset();
        path.addRoundRect(new RectF(PLUS_L, PLUS_T, PLUS_R, PLUS_B), PLUS_RADIUS, PLUS_RADIUS,
                Path.Direction.CW);
        path.moveTo(PLUS_BEAK_CX - PLUS_BEAK_HALF, PLUS_T + 6f);
        path.lineTo(PLUS_BEAK_CX, PLUS_BEAK_TIP);
        path.lineTo(PLUS_BEAK_CX + PLUS_BEAK_HALF, PLUS_T + 6f);
        path.close();
        c.drawPath(path, paint);

        for (int i = 1; i < PLUS_ITEMS.length; i++) {
            float y = PLUS_T + PLUS_ROW_H * i;
            line(c, PLUS_DIVIDER_L, y, PLUS_R - 4f, y, PLUS_HAIRLINE, 1.6f);
        }
        for (int i = 0; i < PLUS_ITEMS.length; i++) {
            float cy = PLUS_T + PLUS_ROW_H * (i + 0.5f);
            // Bitmaps are modulated by the paint's alpha, so it has to be opaque here.
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            r.set(PLUS_ICON_CX - PLUS_ICON_SIZE / 2f, cy - PLUS_ICON_SIZE / 2f,
                    PLUS_ICON_CX + PLUS_ICON_SIZE / 2f, cy + PLUS_ICON_SIZE / 2f);
            drawArtAt(c, PLUS_ART[i], r);
            text(c, PLUS_ITEMS[i], PLUS_TEXT_X, cy + 18f, PLUS_TEXT_SIZE, Color.WHITE, false);
        }
    }

    // ------------------------------------------------------------------ hit test

    /** Returns an action token for the tap at (x, y) — screen coordinates in design space. */
    public String hitTest(State s, float x, float y) {
        if (s.plusMenu) {
            if (x < PLUS_L || x > PLUS_R || y < PLUS_T || y > PLUS_B) return "plusClose";
            int row = (int) ((y - PLUS_T) / PLUS_ROW_H);
            row = Math.max(0, Math.min(PLUS_ITEMS.length - 1, row));
            return "plus:" + PLUS_ITEMS[row];
        }
        if (y >= NAV_TOP) return "nav:" + nearest(NAV_X, x);

        float cy = y + s.scroll;
        if (y < 300) {
            if (x > 1120) return "plus";
            if (x > 330 && x < 1120) return "search";
            return "edit:address";
        }
        if (cy >= 300 && cy <= 540) return "action:" + ACTIONS[nearest(ACTION_X, x)];
        if (cy > 545 && cy < 1060) {
            int col = column(x);
            if (cy < 810) return "icon:" + GRID_ROW1_TEXT[col];
            if (cy < 1030) return "icon:" + GRID_ROW2_TEXT[col];
            return "noop";
        }
        if (cy >= 1060 && cy < 1100) return "noop";
        if (cy >= 1085 && cy < 1432) {
            if (cy < 1160 && x > 1040) return "all";
            return "icon:" + COMMON_TEXT[column(x)];
        }
        if (cy >= 1456 && cy < 1876) {
            if (cy < 1580) return x > 850 ? "edit:newmsg" : "noop";
            if (cy < 1740) return "edit:msg0";
            return "edit:msg1";
        }
        if (cy >= 1878 && cy < 2046) return "noop";
        if (cy >= 2058 && cy < 2760) {
            if (cy < 2160 && x > 1100) return "zhimaMore";
            if (cy > 2380 && cy < 2510 && x > 230 && x < 460) return "edit:score";
            if (cy > 2515 && cy < 2600 && x > 200 && x < 500) return "edit:credit";
            if (cy > 2400 && cy < 2500 && x > 690 && x < 900) return "collect";
            return "zhima";
        }
        return "noop";
    }

    private int column(float x) {
        for (int i = 0; i < 5; i++) if (x < COL_EDGE[i + 1]) return i;
        return 4;
    }

    private static int nearest(float[] values, float x) {
        int best = 0;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            float d = Math.abs(values[i] - x);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ primitives

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private void drawArt(Canvas c, String name) {
        Bitmap b = art.get(name);
        RectF box = iconRects.get(name);
        if (b == null || box == null) return;
        c.drawBitmap(b, null, box, paint);
    }

    private void drawArtAt(Canvas c, String name, RectF dst) {
        Bitmap b = art.get(name);
        if (b == null) return;
        if (iconRects.get(name) == null) return;
        c.drawBitmap(b, null, dst, paint);
    }

    private void text(Canvas c, String value, float x, float baseline, float size, int color, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText(value, x, baseline, paint);
    }

    private void textRight(Canvas c, String value, float x, float baseline, float size, int color, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.RIGHT);
        c.drawText(value, x, baseline, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void centerText(Canvas c, String value, float cx, float baseline, float size, int color, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(value, cx, baseline, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** Centred text with a horizontal stretch, to match the reference face's width. */
    private void centerTextScaled(Canvas c, String value, float cx, float baseline, float size,
                                  float xScale, int color) {
        c.save();
        c.translate(cx, 0);
        c.scale(xScale, 1f);
        centerText(c, value, 0, baseline, size, color, false);
        c.restore();
    }

    private void round(Canvas c, float l, float t, float rr, float b, float radius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRoundRect(l, t, rr, b, radius, radius, paint);
    }

    private void circle(Canvas c, float x, float y, float radius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawCircle(x, y, radius, paint);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2, int color, float width) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color);
        c.drawLine(x1, y1, x2, y2, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void stroke(int color, float width) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
    }

    private void chevron(Canvas c, float x, float y, int color) {
        line(c, x - 8, y - 13, x + 6, y, color, 4);
        line(c, x + 6, y, x - 8, y + 13, color, 4);
    }

}
