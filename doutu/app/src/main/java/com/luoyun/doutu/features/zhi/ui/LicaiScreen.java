package com.luoyun.doutu.features.zhi.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.luoyun.doutu.R;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Native rendition of the 理财 tab of the 小支主页 (Alipay-style 财富 page).
 *
 * <p>The page is rebuilt on the same 1280x2816 reference canvas as the rest of the 支 module.
 * Only the pictographic app icons, the two promo charts, the 自选 / 热门ETF / 今日风口 cards and
 * the tab bar are cut out of the reference screenshot as bitmaps — and the numbers inside those
 * cards were painted out of the crops so they can be rendered (and edited) here. Every other
 * surface, label and figure is drawn from primitives; the original screenshot is never drawn.
 */
public final class LicaiScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;
    public static final float NAV_TOP = 2560f;
    /** The product list is cut off in the reference; the page ends below its last row. */
    public static final float CONTENT_H = 4440f;

    /** Mutable presentation state owned by the hosting activity. */
    public static final class State {
        public float scroll;
        /** 总资产 / 昨日收益 / 家庭保障 figures, all editable. */
        public String total = "0.00";
        public String yesterday = "0.00";
        public String family = "2";
        /** The eye hides the two money figures the way the real page does. */
        public boolean hidden = false;
        /** How far the 我的保障资产 / 我的额度 panel under the header is unfolded, 0..1. */
        public float expand = 0f;
        /** 上证指数 / 深证成指 / 创业板指. */
        public final String[] index = {"3808.33", "12495.44", "3002.50"};
        public final String[] indexPct = {"\u22120.14%", "\u22121.16%", "\u22121.34%"};
        public String fundName = "汇安鼎利纯债债券C";
        public String fundPct = "+5.07%";
        public String stockName = "深高速";
        public String stockPct = "+4.32%";
        public final String[] etfName = {"通信ET...ETF", "科创50...ETF", "煤炭ET...ETF"};
        public final String[] etfPct = {"\u22123.38%", "\u22121.30%", "\u22121.39%"};
        public String windName = "创新药";
        public String windPct = "\u22121.26%";
        public String windLine1 = "创新药政策利好，叠加AI技";
        public String windLine2 = "术加持催化。";
        public String prodName = "年年享｜招商信用添利";
        public String prodPct = "+6.56%";

        /** Field keys addressable from the edit dialogs. */
        public static final String[] KEYS = {
                "total", "yesterday", "family",
                "idx0", "idx1", "idx2", "idxp0", "idxp1", "idxp2",
                "fundName", "fundPct", "stockName", "stockPct",
                "etf0", "etf1", "etf2", "etfp0", "etfp1", "etfp2",
                "windName", "windPct", "windLine1", "windLine2",
                "prodName", "prodPct"};

        public static final String[] TITLES = {
                "编辑总资产", "编辑昨日收益", "编辑家庭保障份数",
                "编辑上证指数", "编辑深证成指", "编辑创业板指",
                "编辑上证涨跌幅", "编辑深证涨跌幅", "编辑创业板涨跌幅",
                "编辑基金名称", "编辑基金涨幅", "编辑股票名称", "编辑股票涨幅",
                "编辑ETF第1名", "编辑ETF第2名", "编辑ETF第3名",
                "编辑ETF第1涨幅", "编辑ETF第2涨幅", "编辑ETF第3涨幅",
                "编辑风口名称", "编辑风口涨幅", "编辑风口说明第一行", "编辑风口说明第二行",
                "编辑产品名称", "编辑产品涨幅"};

        /** Current text of a field; used to seed its edit dialog. */
        public String value(String key) {
            switch (key) {
                case "total": return total;
                case "yesterday": return yesterday;
                case "family": return family;
                case "idx0": return index[0];
                case "idx1": return index[1];
                case "idx2": return index[2];
                case "idxp0": return indexPct[0];
                case "idxp1": return indexPct[1];
                case "idxp2": return indexPct[2];
                case "fundName": return fundName;
                case "fundPct": return fundPct;
                case "stockName": return stockName;
                case "stockPct": return stockPct;
                case "etf0": return etfName[0];
                case "etf1": return etfName[1];
                case "etf2": return etfName[2];
                case "etfp0": return etfPct[0];
                case "etfp1": return etfPct[1];
                case "etfp2": return etfPct[2];
                case "windName": return windName;
                case "windPct": return windPct;
                case "windLine1": return windLine1;
                case "windLine2": return windLine2;
                case "prodName": return prodName;
                case "prodPct": return prodPct;
                default: return "";
            }
        }

        public void value(String key, String text) {
            switch (key) {
                case "total": total = text; break;
                case "yesterday": yesterday = text; break;
                case "family": family = text; break;
                case "idx0": index[0] = text; break;
                case "idx1": index[1] = text; break;
                case "idx2": index[2] = text; break;
                case "idxp0": indexPct[0] = text; break;
                case "idxp1": indexPct[1] = text; break;
                case "idxp2": indexPct[2] = text; break;
                case "fundName": fundName = text; break;
                case "fundPct": fundPct = text; break;
                case "stockName": stockName = text; break;
                case "stockPct": stockPct = text; break;
                case "etf0": etfName[0] = text; break;
                case "etf1": etfName[1] = text; break;
                case "etf2": etfName[2] = text; break;
                case "etfp0": etfPct[0] = text; break;
                case "etfp1": etfPct[1] = text; break;
                case "etfp2": etfPct[2] = text; break;
                case "windName": windName = text; break;
                case "windPct": windPct = text; break;
                case "windLine1": windLine1 = text; break;
                case "windLine2": windLine2 = text; break;
                case "prodName": prodName = text; break;
                case "prodPct": prodPct = text; break;
                default: break;
            }
        }
    }

    // ------------------------------------------------------------------ palette
    private static final int PAGE_BG = 0xfff2f3f7;
    private static final int CARD = 0xffffffff;
    private static final int INK = 0xff23252b;
    private static final int MUTED = 0xff939292;
    private static final int BLUE = 0xff1478fc;
    private static final int DEEP_BLUE = 0xff2f6df0;
    private static final int GREEN = 0xff0b906a;
    private static final int RED = 0xfff0241c;
    private static final int TAG_BG = 0xffe4f0fc;
    private static final int SUB_CARD = 0xfff4f8fe;
    private static final int HAIRLINE = 0xffececf1;

    /**
     * The reference face is a little narrower than the platform default, so every run of text is
     * drawn with this horizontal squeeze: sizes are picked to match the reference's cap height and
     * the squeeze brings the advance widths back onto the reference's grid.
     */
    private static final float TEXT_SCALE_X = 0.93f;

    // ------------------------------------------------------------------ geometry
    public static final float HEADER_BOTTOM = 546f;
    /**
     * Height of the panel that unfolds between the 资产 header and the 福利中心 bar: 我的保障资产,
     * 我的额度 and the two credit lines, plus the caret that sits on its bottom edge.
     */
    public static final float PANEL_H = 601f;
    /** Centre of the caret that toggles the panel, in the folded layout. */
    private static final float CARET_Y = 515f;
    /** The 福利中心 bar travels whole; the cut starts on this row and is drawn back onto it. */
    private static final float FULI_TOP = 546f;
    private static final float FULI_BOTTOM = 728f;
    public static final float GRID_TOP = 680f;
    private static final float GRID_BOTTOM = 1184f;

    private static final RectF GOLD_CARD = new RectF(42, 1210, 1237, 1708);
    private static final float CAROUSEL_TOP = 1742f;
    private static final float CAROUSEL_BOTTOM = 2203f;
    private static final float CAROUSEL_CARD_W = 585f;
    private static final float CAROUSEL_Y = 1742f;

    private static final RectF BOARD = new RectF(28, 2235, 1252, 3157);
    private static final RectF INDEX_CARD = new RectF(71, 2270, 1207, 2445);
    private static final RectF ETF_CARD = new RectF(71, 2478, 523, 3155);
    private static final RectF WIND_CARD = new RectF(554, 2478, 1207, 3155);
    private static final float TAB_TOP = 3180f;
    private static final float TAB_BOTTOM = 3332f;
    private static final RectF STEADY_CARD = new RectF(42, 3374, 1237, 4180);

    /** Icon boxes of the two grid rows, keyed to the cut-out artwork. */
    private static final float[][] ROW1_BOX = {
            {90, 728, 192, 837}, {340, 732, 441, 834}, {581, 733, 695, 834},
            {836, 732, 942, 834}, {1088, 732, 1187, 834}};
    private static final float[][] ROW2_BOX = {
            {89, 964, 196, 1080}, {331, 965, 451, 1080}, {572, 967, 707, 1076},
            {776, 950, 945, 1080}, {1077, 971, 1201, 1078}};
    private static final String[] ROW1_ART = {
            "icYuebao", "icWenjian", "icGold", "icFund", "icStock"};
    private static final String[] ROW2_ART = {
            "icDingqi", "icJinjie", "icBaoxian", "icBangni", "icYanglao"};
    private static final String[] ROW1_TEXT = {
            "余额宝", "稳健理财", "黄金", "基金", "股票"};
    private static final String[] ROW2_TEXT = {
            "定期", "进阶理财", "保险", "帮你投", "养老金"};
    private static final float[] ROW1_CX = {141, 390, 639, 888, 1137};
    private static final float[] ROW2_CX = {143, 391, 639, 888, 1137};

    private static final float[] NAV_X = {128f, 382f, 639f, 898f, 1151f};
    private static final String[] PILLS = {"热销产品", "月月享", "季季享", "年年享"};
    private static final float[] PILL_L = {80f, 372f, 659f, 946f};
    private static final float PILL_R = 1201f;
    private static final float PILL_TOP = 3554f;
    private static final float PILL_BOTTOM = 3669f;

    /** Keys of the tap targets that open an edit dialog. */
    private static final String[] INDEX_KEYS = {"idx0", "idx1", "idx2"};
    private static final String[] INDEX_PCT_KEYS = {"idxp0", "idxp1", "idxp2"};
    private static final String[] ETF_KEYS = {"etf0", "etf1", "etf2"};
    private static final String[] ETF_PCT_KEYS = {"etfp0", "etfp1", "etfp2"};

    private final Map<String, Bitmap> art = new LinkedHashMap<>();
    private final BlackCardNav blackCardNav = new BlackCardNav();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private PorterDuffColorFilter blueFilter;
    private Bitmap headerPlate;

    /**
     * The header's blue, fitted to the reference screenshot: the plate is sampled from these
     * per-channel quadratics, so the drawn backdrop is the reference's own gradient. Only the
     * backdrop comes from the fit — every glyph, icon and bubble on top is drawn here.
     */
    private static final float[] HEADER_BLUE = {
            34.54431f, -0.0314062f, 0.0169517f, 0.00000517f, -0.00000712f, 0.00000510f,   // red
            141.45530f, -0.0532967f, 0.0250387f, 0.00001342f, -0.00001059f, 0.00000673f,   // green
            254.33077f, 0.0003059f, 0.0014226f, 0.000000172f, -0.000000917f, -0.000001554f // blue
    };

    /** Decodes the cut-out bitmaps. Safe to call again after a configuration change. */
    public void load(Resources res) {
        art.clear();
        art.put("mascot", BitmapFactory.decodeResource(res, R.drawable.lci_mascot));
        art.put("searchbar", BitmapFactory.decodeResource(res, R.drawable.lci_searchbar));
        art.put("search", BitmapFactory.decodeResource(res, R.drawable.lci_search));
        art.put("gear", BitmapFactory.decodeResource(res, R.drawable.lci_gear));
        art.put("eye", BitmapFactory.decodeResource(res, R.drawable.lci_eye));
        art.put("shield", BitmapFactory.decodeResource(res, R.drawable.lci_shield));
        art.put("chawen", BitmapFactory.decodeResource(res, R.drawable.lci_badge_chatiao));
        art.put("icYuebao", BitmapFactory.decodeResource(res, R.drawable.lci_ic_yuebao));
        art.put("icWenjian", BitmapFactory.decodeResource(res, R.drawable.lci_ic_wenjian));
        art.put("icGold", BitmapFactory.decodeResource(res, R.drawable.lci_ic_gold));
        art.put("icFund", BitmapFactory.decodeResource(res, R.drawable.lci_ic_fund));
        art.put("icStock", BitmapFactory.decodeResource(res, R.drawable.lci_ic_stock));
        art.put("icDingqi", BitmapFactory.decodeResource(res, R.drawable.lci_ic_dingqi));
        art.put("icJinjie", BitmapFactory.decodeResource(res, R.drawable.lci_ic_jinjie));
        art.put("icBaoxian", BitmapFactory.decodeResource(res, R.drawable.lci_ic_baoxian));
        art.put("icBangni", BitmapFactory.decodeResource(res, R.drawable.lci_ic_bangni));
        art.put("icYanglao", BitmapFactory.decodeResource(res, R.drawable.lci_ic_yanglao));
        art.put("goldChart", BitmapFactory.decodeResource(res, R.drawable.lci_gold_chart));
        art.put("cardFund", BitmapFactory.decodeResource(res, R.drawable.lci_card_fund));
        art.put("cardStock", BitmapFactory.decodeResource(res, R.drawable.lci_card_stock));
        art.put("cardEtf", BitmapFactory.decodeResource(res, R.drawable.lci_card_etf));
        art.put("cardWind", BitmapFactory.decodeResource(res, R.drawable.lci_card_wind));
        art.put("tabbar", BitmapFactory.decodeResource(res, R.drawable.lci_tabbar));
        art.put("navbar", BitmapFactory.decodeResource(res, R.drawable.zhi_navbar));
        art.put("navHome", BitmapFactory.decodeResource(res, R.drawable.lci_nav_home));
        art.put("steadyMark", BitmapFactory.decodeResource(res, R.drawable.lci_steady_mark));
        art.put("fuli", BitmapFactory.decodeResource(res, R.drawable.lci_fuli));
        art.put("navZhi", BitmapFactory.decodeResource(res, R.drawable.lci_nav_zhi));
        art.put("navLicai", BitmapFactory.decodeResource(res, R.drawable.lci_nav_licai));
        art.put("navVideo", BitmapFactory.decodeResource(res, R.drawable.lci_nav_video));
        art.put("navMsg", BitmapFactory.decodeResource(res, R.drawable.lci_nav_msg));
        art.put("navMe", BitmapFactory.decodeResource(res, R.drawable.lci_nav_me));
        blackCardNav.load(res);
        buildHeaderPlate();
        // the 理财 entry of the bottom bar is the selected one, so its glyph is tinted brand blue
        blueFilter = new PorterDuffColorFilter(BLUE, PorterDuff.Mode.SRC_IN);
    }

    /** Renders {@link #HEADER_BLUE} into the small plate the header backdrop is drawn from. */
    private void buildHeaderPlate() {
        int n = 8;
        headerPlate = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        float full = HEADER_BOTTOM + PANEL_H;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                float x = W * i / (n - 1f);
                float y = full * j / (n - 1f);
                int r = shade(0, x, y);
                int g = shade(6, x, y);
                int b = shade(12, x, y);
                headerPlate.setPixel(i, j, Color.rgb(r, g, b));
            }
        }
    }

    private static int shade(int offset, float x, float y) {
        float[] c = HEADER_BLUE;
        float v = c[offset] + c[offset + 1] * x + c[offset + 2] * y
                + c[offset + 3] * x * x + c[offset + 4] * x * y + c[offset + 5] * y * y;
        return Math.max(0, Math.min(255, Math.round(v)));
    }

    /** The unfolded panel lengthens the page, so the scroll range grows with it. */
    public float maxScroll(State s) {
        return Math.max(0f, CONTENT_H + PANEL_H * s.expand - H);
    }

    // ------------------------------------------------------------------ drawing

    public void draw(Canvas c, State s) {
        float off = PANEL_H * s.expand;
        c.drawColor(PAGE_BG);
        c.save();
        c.translate(0, -s.scroll);
        // The blue sheet is the header plus however much of the panel is unfolded; the panel's
        // own rows and the caret ride on it, clipped to the part that has been pulled open.
        c.save();
        c.clipRect(0, 0, W, HEADER_BOTTOM + off);
        drawHeaderBackdrop(c);
        drawHeader(c, s, off);
        drawPanel(c, s);
        c.restore();
        // everything below the 福利中心 bar slides down as the panel opens
        c.save();
        c.translate(0, off);
        drawBody(c, s, 0f, true);
        c.restore();
        c.restore();
        // Once the blue header has scrolled away the status bar sits on light content.
        if (s.scroll > HEADER_BOTTOM - 150f) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(PAGE_BG);
            c.drawRect(0, 0, W, 150, paint);
        }
        drawNav(c);
    }

    /**
     * Everything the page carries under its own header — the sheet's top with the 福利中心 bar, the
     * grid of ten, the gold promotion, the two 自选 cards, the index board, the 好品 / 服务 / 盘友圈
     * bar and the 安稳理财 card — laid down at {@code dy} from where this page draws it. The 成长版
     * draws the same run under its own header, which is why the whole body travels together.
     */
    public void drawBody(Canvas c, State s, float dy, boolean bar) {
        c.save();
        c.translate(0, dy);
        drawGridCard(c, bar);
        drawGoldCard(c);
        drawCarousel(c, s);
        drawBoard(c, s);
        drawTabs(c);
        drawSteadySection(c, s);
        c.restore();
    }

    private void drawHeaderBackdrop(Canvas c) {
        if (headerPlate != null) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            r.set(0, 0, W, HEADER_BOTTOM + PANEL_H);
            c.drawBitmap(headerPlate, null, r, paint);
        }
    }

    private void drawHeader(Canvas c, State s, float off) {

        // mascot badge, then the search pill (rounded box, tail, 去看看 tag and its text), all cut
        // straight from the reference so the shape and typography match
        drawArt(c, "mascot", 35, 158, 173, 296);
        drawArt(c, "searchbar", 140, 150, 1000, 292);

        drawArt(c, "search", 998, 156, 1116, 268);
        drawArt(c, "gear", 1142, 156, 1260, 268);

        text(c, "总资产(元)", 45, 388, 45, 0xffd6e6ff);
        text(c, s.hidden ? "****" : s.total, 45, 498, 100, Color.WHITE);
        drawArt(c, "eye", 286, 338, 374, 412);
        round(c, 410, 342, 616, 406, 32, 0xff1a6dd7);
        drawArt(c, "shield", 428, 344, 478, 404);
        text(c, "保障中", 488, 390, 42, Color.WHITE);

        text(c, "昨日收益", 814, 387, 45, 0xffd6e6ff);
        textRight(c, s.hidden ? "****" : s.yesterday, 990, 498, 100, Color.WHITE);
        line(c, 1017, 340, 1017, 500, 0x55ffffff, 2.5f);
        text(c, "家庭保障", 1069, 386, 45, 0xffd6e6ff);
        text(c, s.family, 1104, 489, 100, Color.WHITE);
        text(c, "份", 1104 + textWidth(s.family, 100) + 6, 486, 40, Color.WHITE);

        // The caret rides the panel's bottom edge and flips as the panel unfolds.
        float caretY = CARET_Y + off;
        c.save();
        c.rotate(180f * s.expand, 639f, caretY);
        line(c, 624, caretY - 7.5f, 639, caretY + 7.5f, 0xffbcd6ff, 5);
        line(c, 639, caretY + 7.5f, 654, caretY - 7.5f, 0xffbcd6ff, 5);
        c.restore();
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * The rows that live between the 资产 header and the 福利中心 bar. They are laid out for the
     * fully open panel and simply get revealed as the clip grows, so the sheet appears to unfold
     * downwards while everything below it slides down.
     */
    private void drawPanel(Canvas c, State s) {
        text(c, "我的保障资产", 45, 638, 44, 0xffd6e6ff);
        text(c, "保险", 44, 776, 44, Color.WHITE);
        text(c, "2份免费保单保障中", 158, 776, 44, 0xffd6e6ff);
        text(c, "我的额度", 45, 902, 44, 0xffd6e6ff);
        text(c, "花呗", 44, 1041, 44, Color.WHITE);
        text(c, "可用2,710.98", 158, 1041, 44, 0xffd6e6ff);
        text(c, "信用卡", 676, 1041, 44, Color.WHITE);
        text(c, "2000免费额度起", 833, 1041, 44, 0xffd6e6ff);
    }

    private void drawGridCard(Canvas c, boolean bar) {
        r.set(0, GRID_TOP, W, GRID_BOTTOM);
        paint.setShader(new LinearGradient(0, GRID_TOP, 0, GRID_BOTTOM,
                0xffffffff, PAGE_BG, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 58, 58, paint);
        paint.setShader(null);
        // the 福利中心 bar and the sheet's rounded top corners, straight from the reference
        if (bar) drawArt(c, "fuli", 0, FULI_TOP, W, FULI_BOTTOM);
        for (int i = 0; i < 5; i++) {
            drawArt(c, ROW1_ART[i], ROW1_BOX[i][0], ROW1_BOX[i][1], ROW1_BOX[i][2], ROW1_BOX[i][3]);
            centerText(c, ROW1_TEXT[i], ROW1_CX[i], 902, 43, INK, false);
            drawArt(c, ROW2_ART[i], ROW2_BOX[i][0], ROW2_BOX[i][1], ROW2_BOX[i][2], ROW2_BOX[i][3]);
            centerText(c, ROW2_TEXT[i], ROW2_CX[i], 1131, 43, INK, false);
        }
        drawArt(c, "chawen", 648, 923, 774, 969);
        // The icon grid's pager: a brand-blue dash and one inactive dot. Both sit in the gap
        // under the second row's captions (caption glyphs end at y 1140), centred on the page.
        round(c, 604, 1168, 652, 1182, 7, 0xff2a76f7);
        circle(c, 668, 1175, 6.5f, 0xffdedde1);
    }

    private void drawGoldCard(Canvas c) {
        round(c, GOLD_CARD.left, GOLD_CARD.top, GOLD_CARD.right, GOLD_CARD.bottom, 28, CARD);
        round(c, 78, 1249, 228, 1305, 10, 0xffe42838);
        centerText(c, "账户服务", 153, 1291, 40, Color.WHITE, false);
        text(c, "你的黄金账户停留在开户很久了", 240, 1303, 53, 0xff22242a, true);

        // call-to-action bubble with its tail over the first step of the progress track
        paint.setShader(new LinearGradient(105, 1378, 430, 1445,
                0xff3f9cff, 0xff0f6ff5, Shader.TileMode.CLAMP));
        r.set(105, 1378, 430, 1445);
        c.drawRoundRect(r, 14, 14, paint);
        paint.setShader(null);
        path.reset();
        path.moveTo(180, 1443);
        path.lineTo(197, 1462);
        path.lineTo(205, 1443);
        path.close();
        paint.setColor(0xff0f6ff5);
        c.drawPath(path, paint);
        centerText(c, "查看今日国内实时金价", 267, 1432, 34, Color.WHITE, false);

        drawArt(c, "goldChart", 698, 1316, 1202, 1502);

        circle(c, 193, 1476, 29, BLUE);
        path.reset();
        path.moveTo(179, 1476);
        path.lineTo(189, 1486);
        path.lineTo(208, 1466);
        stroke(0xffffffff, 7);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        line(c, 222, 1476, 503, 1476, BLUE, 13);
        line(c, 503, 1476, 762, 1476, 0xffd8e5f5, 13);
        circle(c, 503, 1476, 15, BLUE);
        round(c, 757, 1467, 779, 1485, 6, 0xffd8e5f5);

        centerText(c, "进入开户", 193, 1536, 36, DEEP_BLUE, false);
        centerText(c, "身份校验", 467, 1536, 36, DEEP_BLUE, false);
        centerText(c, "开户成功", 770, 1536, 36, DEEP_BLUE, false);

        text(c, "开通黄金实时交易账户", 85, 1643, 43, 0xff2f3138);
        round(c, 985, 1580, 1200, 1668, 44, BLUE);
        centerText(c, "去开户", 1092, 1649, 48, Color.WHITE, true);
    }

    private void drawCarousel(Canvas c, State s) {
        // the row bleeds off the left edge: the previous card only shows a sliver
        round(c, -550, CAROUSEL_TOP, 30, CAROUSEL_BOTTOM, 27, CARD);
        drawArt(c, "cardFund", 57, CAROUSEL_Y, 57 + CAROUSEL_CARD_W, CAROUSEL_Y + 461);
        drawArt(c, "cardStock", 669, CAROUSEL_Y, 669 + CAROUSEL_CARD_W, CAROUSEL_Y + 461);

        text(c, s.fundName, 100, 1851, 50, INK, true);
        text(c, s.fundPct, 99, 1934, 56, RED, true);
        text(c, "近一年净值增长", 100, 2003, 43, MUTED);

        float nx = text(c, s.stockName, 705, 1851, 50, INK, true);
        text(c, s.stockPct, nx + 16, 1851, 47, RED, true);
        text(c, s.stockPct, 704, 1929, 56, RED, true);
        text(c, "最大涨幅", 705, 2001, 43, MUTED);
    }

    private void drawBoard(Canvas c, State s) {
        r.set(BOARD.left, BOARD.top, BOARD.right, BOARD.bottom);
        paint.setShader(new LinearGradient(0, BOARD.top, 0, BOARD.bottom,
                0xffdceefe, 0xfffbfbfb, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 30, 30, paint);
        paint.setShader(null);

        round(c, INDEX_CARD.left, INDEX_CARD.top, INDEX_CARD.right, INDEX_CARD.bottom, 26, CARD);
        for (int i = 0; i < 3; i++) {
            float lx = 114 + i * 385.5f;
            float end = text(c, indexLabel(i), lx, 2344, 46, 0xff2a2b2d);
            chevron(c, end + 16, 2324, 0xffb6bac0);
            float vx = text(c, s.index[i], lx, 2415, 52, GREEN, true);
            text(c, s.indexPct[i], vx + 12, 2415, 36, GREEN, true);
        }

        drawArt(c, "cardEtf", ETF_CARD.left, ETF_CARD.top, ETF_CARD.right, ETF_CARD.bottom);
        drawArt(c, "cardWind", WIND_CARD.left, WIND_CARD.top, WIND_CARD.right, WIND_CARD.bottom);
        for (int i = 0; i < 3; i++) {
            float top = 2608f + i * 173.5f;
            text(c, s.etfName[i], 224, top + 52, 38, 0xff2c2c2a);
            text(c, s.etfPct[i], 224, top + 115, 48, GREEN, true);
        }
        text(c, s.windName, 624, 2706, 64, INK, true);
        text(c, s.windPct, 624, 2794, 62, GREEN, true);
        text(c, s.windLine1, 628, 2988, 43, 0xff40424a);
        text(c, s.windLine2, 628, 3038, 43, 0xff40424a);
    }

    private static String indexLabel(int i) {
        return i == 0 ? "上证指数" : i == 1 ? "深证成指" : "创业板指";
    }

    private void drawTabs(Canvas c) {
        // the section under the tabs is a touch lighter than the page background
        paint.setShader(new LinearGradient(0, TAB_BOTTOM, 0, 3540,
                0xfffcfcfc, PAGE_BG, Shader.TileMode.CLAMP));
        c.drawRect(0, TAB_BOTTOM, W, 3540, paint);
        paint.setShader(null);
        drawArt(c, "tabbar", 0, TAB_TOP, W, TAB_BOTTOM);
    }

    private void drawSteadySection(Canvas c, State s) {
        r.set(STEADY_CARD.left, STEADY_CARD.top, STEADY_CARD.right, STEADY_CARD.bottom);
        paint.setShader(new LinearGradient(0, STEADY_CARD.top, 0, 3590,
                0xffe7ecfd, 0xffffffff, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, 28, 28, paint);
        paint.setShader(null);
        drawArt(c, "steadyMark", 540, 3374, 1242, 3512);

        text(c, "安稳理财", 99, 3488, 59, INK, true);
        text(c, "主投债券·波动小·收益稳", 388, 3489, 50, 0xff5e5f63);
        chevron(c, 1187, 3465, 0xff8a8f99);

        for (int i = 0; i < PILLS.length; i++) {
            float left = PILL_L[i];
            float right = i == 0 ? 339f : left + 255f;
            round(c, left, PILL_TOP, right, PILL_BOTTOM, 57, CARD);
            if (i == 0) stroke(BLUE, 2.5f);
            if (i == 0) c.drawRoundRect(new RectF(left, PILL_TOP, right, PILL_BOTTOM), 57, 57, paint);
            paint.setStyle(Paint.Style.FILL);
            centerText(c, PILLS[i], (left + right) / 2f, 3636, i == 0 ? 49 : 48,
                    i == 0 ? BLUE : 0xff919294, i == 0);
        }

        text(c, s.prodName, 100, 3796, 50, INK, true);
        // the 关注 button and the two badges that follow the product name
        float plusX = 585;
        r.set(plusX, 3750, plusX + 52, 3800);
        stroke(0xffc3c9d4, 2.5f);
        c.drawRoundRect(r, 8, 8, paint);
        line(c, plusX + 15, 3775, plusX + 37, 3775, 0xff8d949f, 4);
        line(c, plusX + 26, 3764, plusX + 26, 3786, 0xff8d949f, 4);
        paint.setStyle(Paint.Style.FILL);
        tag(c, 645, 3750, "超10万人关注", 32);
        tag(c, 890, 3750, "银行系", 32);

        text(c, s.prodPct, 98, 3932, 62, RED, true);
        text(c, "近一年净值增长", 99, 3980, 43, MUTED);
        text(c, "灵活申赎", 583, 3939, 61, INK, true);
        text(c, "10元起购 ｜ 中低风险 ｜ 债券基金", 583, 3983, 42, MUTED);
    }

    private void tag(Canvas c, float left, float top, String label, float size) {
        float width = textWidth(label, size) + 36;
        round(c, left, top, left + width, top + 44, 8, TAG_BG);
        centerText(c, label, left + width / 2f, top + 33, size, 0xff1677ff, false);
    }

    /** The bottom bar, shared with the 成长版 so the tab's chrome never moves. */
    public void drawNav(Canvas c) {
        if (blackCardNav.draw(c, 1, W, NAV_TOP, H)) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        // The bar is the 支 home page's own cut artwork, so its geometry, background and home
        // indicator never move when the user switches tabs. Only the two stateful entries are
        // restyled: 支 goes inactive and 理财 becomes the selected one.
        drawArt(c, "navbar", 0, NAV_TOP, W, H);
        drawArt(c, "navHome", 20, 2560, 245, 2816);
        r.set(340, 2584, 426, 2732);
        paint.setColor(0xfffafbff);
        c.drawRect(r, paint);
        drawArt(c, "navLicai", 340, 2584, 426, 2732, true);
    }

    // ------------------------------------------------------------------ hit test

    /** Returns an action token for the tap at (x, y) — screen coordinates in design space. */
    public String hitTest(State s, float x, float y) {
        if (y >= NAV_TOP) return "nav:" + nearest(NAV_X, x);
        float cy = y + s.scroll;

        // the caret between the 资产 and 收益 columns unfolds the panel; its target is the whole
        // notch around the glyph rather than the two strokes, so the tap is easy to land
        float caretY = CARET_Y + PANEL_H * s.expand;
        if (x > 569 && x < 709 && cy > caretY - 40 && cy < caretY + 45) return "toggleExpand";

        // Above 福利中心 the page answers with the version sheet wherever it is not a figure, the
        // eye or the caret — that is the gesture the tab is switched with.
        if (cy < 300) return "version";
        if (cy < 545) {
            if (x >= 280 && x <= 380) return "toggleHide";
            if (cy > 400) {
                if (x < 300) return "edit:total";
                if (x > 760 && x < 1000) return "edit:yesterday";
                if (x > 1040) return "edit:family";
            }
            return "version";
        }
        if (cy < 546f + PANEL_H * s.expand) {
            if (cy > 960 && cy < 1070) {
                if (x < 460) return "noop";
                if (x > 600) return "noop";
            }
            if (cy > 700 && cy < 800) return "noop";
            return "version";
        }
        // Past the panel everything is drawn shifted down by the unfolded height, so read the
        // rest of the page in the folded frame.
        cy -= PANEL_H * s.expand;
        if (cy < GRID_BOTTOM) {
            int col = column(x);
            if (cy >= 868 && cy < 912) return touch(ROW1_TEXT[col]);
            if (cy >= 1096) return touch(ROW2_TEXT[col]);
            if (cy < 868) return touch(ROW1_TEXT[col]);
            return touch(ROW2_TEXT[col]);
        }
        if (cy < GOLD_CARD.bottom) {
            if (cy > 1560 && x > 960) return "noop";
            if (cy >= 1370 && cy <= 1470 && x < 460) return "noop";
            if (cy >= 1440 && cy <= 1520) return "noop";
            return "noop";
        }
        if (cy >= CAROUSEL_TOP && cy <= CAROUSEL_BOTTOM) {
            boolean right = x > 660;
            if (cy >= 1795 && cy <= 1865) return "edit:" + (right ? "stockName" : "fundName");
            if (cy >= 1870 && cy <= 1945) return "edit:" + (right ? "stockPct" : "fundPct");
            if (cy >= 2070 && cy <= 2180) return "noop";
            return "noop";
        }
        if (cy >= INDEX_CARD.top && cy <= INDEX_CARD.bottom) {
            int col = x < 590 ? 0 : x < 1000 ? 1 : 2;
            return "edit:" + (cy > 2360 ? INDEX_PCT_KEYS[col] : INDEX_KEYS[col]);
        }
        if (cy >= ETF_CARD.top && cy <= ETF_CARD.bottom) {
            int row = Math.max(0, Math.min(2, (int) ((cy - 2600) / 173.5f)));
            return "edit:" + (cy > 2672 + row * 173.5f ? ETF_PCT_KEYS[row] : ETF_KEYS[row]);
        }
        if (cy >= WIND_CARD.top && cy <= WIND_CARD.bottom) {
            if (cy >= 2940) return "edit:windLine1";
            if (cy >= 2630 && cy < 2720) return "edit:windName";
            if (cy >= 2720 && cy <= 2810) return "edit:windPct";
            return "noop";
        }
        if (cy > STEADY_CARD.top) {
            if (cy >= 3740 && cy <= 3830) {
                if (x < 578) return "edit:prodName";
                if (x > 630 && x < 880) return "noop";
                if (x >= 880) return "noop";
                return "noop";
            }
            if (cy >= 3850 && cy <= 3945) {
                return "noop";
            }
            if (cy >= 3945) return "noop";
            if (cy >= PILL_TOP && cy <= PILL_BOTTOM) return "noop";
            return "noop";
        }
        return "noop";
    }

    private static String touch(String label) {
        return "noop";
    }

    private int column(float x) {
        if (x < 265) return 0;
        if (x < 515) return 1;
        if (x < 762) return 2;
        if (x < 1009) return 3;
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

    private void drawArt(Canvas c, String name, float l, float t, float rr, float b) {
        drawArt(c, name, l, t, rr, b, false);
    }

    private void drawArt(Canvas c, String name, float l, float t, float rr, float b, boolean tinted) {
        Bitmap bitmap = art.get(name);
        if (bitmap == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setColorFilter(tinted ? blueFilter : null);
        r.set(l, t, rr, b);
        c.drawBitmap(bitmap, null, r, paint);
        paint.setColorFilter(null);
    }

    private float text(Canvas c, String value, float x, float baseline, float size, int color) {
        return text(c, value, x, baseline, size, color, false);
    }

    private float text(Canvas c, String value, float x, float baseline, float size, int color,
                       boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        c.save();
        c.scale(TEXT_SCALE_X, 1f);
        c.drawText(value, x / TEXT_SCALE_X, baseline, paint);
        c.restore();
        return x + paint.measureText(value) * TEXT_SCALE_X;
    }

    private void textRight(Canvas c, String value, float x, float baseline, float size, int color) {
        textRight(c, value, x, baseline, size, color, false);
    }

    private void textRight(Canvas c, String value, float x, float baseline, float size, int color,
                           boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.RIGHT);
        c.save();
        c.scale(TEXT_SCALE_X, 1f);
        c.drawText(value, x / TEXT_SCALE_X, baseline, paint);
        c.restore();
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void centerText(Canvas c, String value, float cx, float baseline, float size, int color,
                            boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        c.save();
        c.scale(TEXT_SCALE_X, 1f);
        c.drawText(value, cx / TEXT_SCALE_X, baseline, paint);
        c.restore();
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private float textWidth(String value, float size) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        return paint.measureText(value) * TEXT_SCALE_X;
    }

    private void round(Canvas c, float l, float t, float rr, float b, float radius, int color) {
        paint.setShader(null);
        paint.setColorFilter(null);
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
        line(c, x - 7, y - 12, x + 6, y, color, 4);
        line(c, x + 6, y, x - 7, y + 12, color, 4);
    }
}
