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
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.luoyun.doutu.R;
import com.luoyun.doutu.features.zhi.data.AdvisorStore;
import com.luoyun.doutu.features.zhi.data.GrowthStore;
import com.luoyun.doutu.features.zhi.data.LicaiVersion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Native rendition of the 理财 tab's 成长版 — the 财富黑卡 page the version sheet switches to.
 *
 * <p>Down to the 纳斯达克 row the page is the first reference: the blue wash behind the status bar,
 * the header above 财富黑卡, the card, the white 市场 / 服务 sheet's own top edge, the grid of ten and
 * that row. Its chrome travels as whole plates and bands, cut with every figure painted out, and
 * those figures are drawn here from {@link GrowthStore}, each answering a tap with the 编辑 dialog
 * the 普通版 uses.
 *
 * <p>Everything under the 纳斯达克 row is the second reference — the 理财 page's own body, which the
 * 成长版 carries there instead of its own 基金自选 / 基金排行 / 严选五类资产 blocks: the three indexes, the
 * 资金流入 and 投什么·今日风口 pair, the 理财好品 / 理财服务 / 盘友圈 bar and the 安稳理财 card with its
 * three products. That section travels as one plate with its own figures painted out, and they are
 * drawn — and editable — the same way. The original screenshot itself is never rendered.
 *
 * <p>A pull down at the top unfolds the 成长版's own panel — the holdings, 更多资产服务, 我的保障资产 and
 * 我的额度 blocks the second reference is captured with open — exactly the way the 普通版 unfolds its
 * own, and the card, the sheet and every figure below travel down with it.
 */
public final class GrowthScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;
    /** The bar sits where this module's own bar sits, because the two pages share it. */
    public static final float NAV_TOP = LicaiScreen.NAV_TOP;
    /** The blue sheet ends here and the card takes over; the panel unfolds under it. */
    public static final float HEADER_BOTTOM = 600f;
    /** How far the panel travels — the second reference is captured with it open. */
    public static final float PANEL_H = 785f;
    /** Where the sheet's own top edge sits. */
    public static final float SHEET_TOP = 838f;
    /** How tall the page is, for the scroll range the unfolded panel needs. */
    public static final float CONTENT_H = 2816f;
    /** How far the 财富黑卡理财师 card pushes the rest of the reference's own screen down. */
    public static final float MARKET_INSERT = 250f;
    /** How tall the sheet itself is drawn: the reference's own screen plus that card. */
    private static final float SHEET_BOTTOM = CONTENT_H + MARKET_INSERT;
    private static final float[] IDX_VALUE_X = {113f, 502f, 885f};
    /** The change sits just past its own value, the way the reference sets the two side by side. */
    private static final float IDX_PCT_GAP = 4f;
    private static final float IDX_BASE = 1705f;
    private static final float IDX_SIZE = 47f;
    private static final float IDX_PCT_SIZE = 33f;
    private static final float FLOW_X = 120f;
    private static final float[] FLOW_BASE = {2006f, 2173f, 2341f};
    private static final float FLOW_SIZE = 40f;
    private static final float WIND_X = 624f;
    private static final float WIND_NAME_BASE = 1991f;
    private static final float WIND_PCT_BASE = 2082f;
    private static final float WIND_NAME_SIZE = 67f;
    private static final float WIND_PCT_SIZE = 69f;
    private static final float PROD_X = 95f;
    private static final float[] PROD_BASE = {3198f, 3582f, 3965f};
    private static final float PROD_SIZE = 57f;
    private static final int IDX_INK = 0xff0f9070;
    private static final int FLOW_INK = 0xffcc4650;
    private static final int PROD_INK = 0xffc43942;
    private static final int WIND_INK = 0xff2f2f2f;
    /** The blue wash behind the status bar, overlapped into the plate under it so no edge shows. */
    private static final RectF TOP_PLATE = new RectF(0, 0, 1280, 134);
    /** Stops one row above the card: the card's own highlight is cut with the card, not the header. */
    private static final RectF HEADER_PLATE = new RectF(0, 128, 1280, 592);
    /**
     * The sheet's own backdrop behind the card, and the panel's continuation of it. It runs the
     * full width, so the wash also fills the strips beside the card — without it the page's own
     * grey showed as a light band down each side of it.
     */
    private static final RectF BACK_PLATE = new RectF(0, 592, 1280, 920);
    private static final RectF PANEL = new RectF(0, 592, 1280, 600 + PANEL_H);
    private static final RectF CARD_BOX = new RectF(22, 592, 1258, 838);
    /** The 市场 / 服务 block and the sheet's top edge, as one masked piece drawn over the card. */
    private static final RectF TAB_BOX = new RectF(0, 700, 1280, 910);
    /** The sheet's own body, from its top edge down to the 黑卡专区 strip. */
    private static final RectF SHEET = new RectF(0, SHEET_TOP - 1, 1280, SHEET_BOTTOM);
    private static final float SHEET_RADIUS = 56f;
    /** Full-width bands cut from the reference, at their own offsets from the sheet's top. */
    /** The one band the sheet still carries above the 纳斯达克 row: the grid of ten. */
    private static final float GRID_TOP = 41f;
    /**
     * The 财富黑卡理财师 card the 市场 tab carries right under its icon grid: the adviser's avatar, his
     * name and his title, and the two round buttons the original puts beside them — one to write to
     * him and one to book him. It is the one block this page adds to the reference's own screen, so
     * everything under it travels {@link #MARKET_INSERT} further down and the page scrolls by it.
     */
    private static final RectF ADVISOR = new RectF(36, 1358, 1244, 1572);
    private static final float ADVISOR_RADIUS = 28f;
    private static final RectF ADVISOR_AVATAR = new RectF(70, 1391, 223, 1544);
    /** The two buttons, at the boxes their own cut-outs were taken over. */
    private static final RectF ADVISOR_TALK = new RectF(921, 1427, 999, 1502);
    private static final RectF ADVISOR_BOOK = new RectF(1087, 1428, 1163, 1502);
    private static final String ADVISOR_ROLE = "财富黑卡理财师";
    private static final float ADVISOR_NAME_X = 252f;
    private static final float ADVISOR_NAME_BASE = 1451f;
    private static final float ADVISOR_NAME_SIZE = 58f;
    private static final float ADVISOR_NAME_W = 102f;
    private static final float ADVISOR_ROLE_X = 253f;
    private static final float ADVISOR_ROLE_BASE = 1521f;
    private static final float ADVISOR_ROLE_SIZE = 52f;
    private static final float ADVISOR_ROLE_W = 312f;
    private static final float[] ADVISOR_BUTTON_CX = {960f, 1125f};
    private static final float ADVISOR_BUTTON_CY = 1465f;
    private static final float ADVISOR_BUTTON_R = 36f;
    private static final int ADVISOR_BUTTON_INK = 0xffa8846a;
    private static final int ADVISOR_NAME_INK = 0xff232326;
    private static final int ADVISOR_ROLE_INK = 0xff6f6f74;
    /** The sheet's 市场 / 服务 captions: the reference's own two boxes, one per column. */
    private static final float[] TAB_CX = {144f, 436f};
    private static final float TAB_BASE = 813f;
    private static final float TAB_SIZE = 52f;
    /** The reference sets its captions narrower than the face would, so each caption is squeezed
     *  to this advance, which lands its ink on the 97 px the reference draws; the underline the
     *  page adds under the selected one is that same 97 px wide. */
    private static final float TAB_ADVANCE = 106f;
    private static final float TAB_UNDERLINE = 97f;
    private static final int TAB_ON = 0xff8c6958;
    private static final int TAB_OFF = 0xff9a9a9a;
    private static final RectF MARKET_CARD = new RectF(37, 528, 1242, 660);
    private static final float MARKET_NAME_X = 82f;
    private static final float MARKET_BASE = 616f;
    private static final float MARKET_SIZE = 58f;
    private static final float[] MARKET_VALUE_X = {429f, 693f, 928f};
    private static final RectF MARKET_ARROW = new RectF(1150, 561, 1196, 607);

    // ------------------------------------------------------------------ palette
    private static final int PAGE_BG = 0xfff2f3f7;
    private static final int CARD_WHITE = 0xffffffff;
    private static final int SHEET_LOW = 0xfff2f3f8;
    private static final int RED = 0xffc0342b;
    private static final int MOVE_INK = 0xffdd5f6b;
    private static final int ALLOC_INK = 0xff16202b;
    private static final int TAG_BG = 0xffeaf1f9;
    private static final int TAG_INK = 0xff2b7fd4;
    private static final int GOLD = 0xffdac5ad;
    /** The 黑卡专区 title's own ink, read off the reference's capture. */
    private static final int BLACK_INK = 0xff2c3038;
    private static final int LABEL_INK = 0xff8b93a8;
    private static final int HOLDER_INK = 0xffe4ded6;
    /** The panel's own inks: this page's wash is dark, so they run warm white to dim grey. */
    private static final int PANEL_LABEL = 0xffe2ddd6;
    private static final int PANEL_VALUE = 0xffb4b6c0;
    private static final int PANEL_CAPTION = 0xff7f8798;
    private static final int PANEL_CARET = 0xffccceda;
    /** The wash the reference's own capture settles on once the panel is out, and how long the
     *  two ends take to reach it — long enough that neither step reads as a line. */
    private static final int PANEL_WASH = 0xff10192f;
    private static final float PANEL_BLEND = 96f;
    /** How far the panel's last rows are repeated over the plate below it, so no edge shows. */
    private static final float PANEL_FOOT = 4f;
    private static final float SX = 0.93f;

    // ------------------------------------------------------------------ header
    private static final float LABEL_SIZE = 50f;
    private static final float VALUE_SIZE = 74f;
    private static final float VALUE_BASE = 508f;
    private static final float VALUE_TOTAL_X = 50f;
    private static final float VALUE_YEST_RIGHT = 1030f;
    private static final float VALUE_FAMILY_RIGHT = 1215f;
    private static final float FAMILY_UNIT_X = 1230f;
    private static final RectF EYE = new RectF(278, 373, 352, 414);
    /** The 免费升级保障 capsule; it is where the rate the page works from can be typed over. */
    private static final RectF RATE = new RectF(400, 342, 692, 420);
    /** The four stars of each hidden figure, at the x the reference draws them. */
    private static final float[][] STARS = {
            {56, 101, 146, 192}, {808, 853, 899, 945}, {1071, 1116, 1161, 1208}};
    private static final float STAR_SIZE = 83f;
    private static final float STAR_BASE = 525f;

    // ------------------------------------------------------------------ the card's signature
    private static final float HOLDER_X = 986f;
    private static final float HOLDER_BASE = 678f;
    private static final float HOLDER_SIZE = 48f;
    /** The signature is squeezed to this width, so a long name still fits the card. */
    private static final float HOLDER_MAX = 238f;

    private static final RectF THEME_CARD = new RectF(22, 592, 1258, 873);
    private static final RectF THEME_HOLDER = new RectF(880, 700, 1045, 760);
    /**
     * The four bands the sheet carries under the 纳斯达克 row, each at the offset the reference
     * itself cuts it at: 基金自选 / 基金排行, the 选好品 … 盘友圈 tiles, the 严选五类资产 strip and the
     * 黑卡专区 strip that closes the page. The first two have their figures painted out of the
     * artwork, so every one of them is drawn here — that is what fills the white the bands would
     * otherwise show where a return belongs.
     */
    private static final float FUNDS_TOP = 1518f;
    private static final float FUNDS_H = 404f;
    private static final float DISCOVER_TOP = 1938f;
    private static final float DISCOVER_H = 274f;
    private static final float BANNER_TOP = 2238f;
    private static final float BANNER_H = 184f;
    private static final float BLACK_TOP = 2428f;
    /** The strip's own cut, which is as far as the reference's capture shows it. */
    private static final float BLACK_H = 132f;
    /** The 黑卡专区 title, whose top the reference's own capture cut off under its navigation bar. */
    private static final float BLACK_LABEL_X = 40f;
    private static final float BLACK_LABEL_BASE = 2556f;
    private static final float BLACK_LABEL_SIZE = 58f;
    /** Where 基金排行's two returns end, right-aligned the way the reference sets them. */
    private static final float RANK_RIGHT = 1196f;

    // ------------------------------------------------------------------ the panel
    private static final float PANEL_SIZE = 44f;
    private static final float PANEL_CARET_Y = 1355f;
    /** The panel's rows, taken off the reference that is captured with it open. */
    private static final float[] PANEL_BASE = {640f, 741f, 848f, 966f, 1070f, 1192f, 1292f};
    private static final float PANEL_A = 121f;
    private static final float PANEL_B = 259f;
    private static final float PANEL_C = 660f;
    private static final float PANEL_D = 761f;
    private static final float[] NAV_X = {128f, 382f, 639f, 898f, 1151f};

    // ------------------------------------------------------------------ the sheet's figures
    /** 基金自选's own return and the 连涨4个月 tag beside it. */
    private static final float FUND_SELF_X = 78f;
    private static final float FUND_SELF_BASE = 1799f;
    private static final float FUND_SELF_SIZE = 61f;
    private static final RectF FUND_TAG = new RectF(299, 1745, 510, 1802);
    private static final float FUND_TAG_X = 313f;
    private static final float FUND_TAG_BASE = 1789f;
    private static final float FUND_TAG_SIZE = 43f;
    private static final float FUND_TAG_RADIUS = 10f;
    /** 基金排行's two returns, right-aligned where the reference sets them. */
    private static final float RANK_1_BASE = 1788f;
    private static final float RANK_2_BASE = 1871f;
    private static final float RANK_SIZE = 45f;
    /** 看行情's own index move. */
    private static final float MKT_X = 465f;
    private static final float MKT_BASE = 2165f;
    private static final float MKT_SIZE = 36f;
    /** 配资产's own return. */
    private static final float ALLOC_X = 658f;
    private static final float ALLOC_BASE = 2114f;
    private static final float ALLOC_SIZE = 36f;
    /** Keys of the index row, in the order the sheet draws them. */
    private static final String[] MARKET_KEYS = {"idx2", "move2", "idxp2"};
    /** The ten captions of the sheet's grid, and the column each of them sits in. */
    private static final String[] GRID_LABEL = {
            "余额宝", "稳健理财", "基金", "黄金", "股票",
            "定期", "保险", "高端理财", "全球投资", "更多"};
    private static final float[] GRID_CX = {155f, 396f, 636f, 877f, 1130f};
    private static final float[] GRID_BASE = {1093f, 1323f};
    private static final String[] TILE_LABEL = {"选好品", "看行情", "配资产", "盘友圈"};
    private static final float[] TILE_RIGHT = {240f, 600f, 940f, 1280f};
    /**
     * The 服务 tab's own page: the 权益 grid, two rows of four, then 专属理财师 and 市场行情. Only
     * the first row has the reference behind it; the rest is this page's own, laid out so the whole
     * tab still fits the one screen the sheet is.
     */
    private static final String[] SERVICE_TITLES = {
            "财富规划", "黑卡理财", "财富私享会", "专属专家团队",
            "资产配置", "高端保险", "税务筹划", "财富传承"};
    private static final String[] SERVICE_MARKS = {
            "规", "卡", "享", "专", "配", "保", "税", "传"};
    private static final int[] SERVICE_COLORS = {
            0xffc69b70, 0xff6c82a8, 0xffc98669, 0xff718c83,
            0xffa98a6f, 0xff8b9bb8, 0xffbfa07e, 0xff93a89b};
    private static final float[] SERVICE_CX = {185f, 490f, 795f, 1100f};
    private static final float[] SERVICE_ROW_CY = {1008f, 1260f};
    private static final float[] SERVICE_LABEL_BASE = {1114f, 1366f};
    private static final float[] SERVICE_CAPTION_BASE = {1162f, 1414f};
    /** The 市场行情 card that closes the 服务 tab: four markets with their points and change. */
    private static final String[] MARKET_NAMES = {"纳斯达克", "上证指数", "深证成指", "创业板指"};
    private static final String[] MARKET_POINTS = {"idx2", "bidx0", "bidx1", "bidx2"};
    private static final String[] MARKET_CHANGES = {"idxp2", "bpct0", "bpct1", "bpct2"};
    private static final float MARKET_ROW_BASE = 2190f;
    private static final float MARKET_ROW_STEP = 92f;

    private final Map<String, Bitmap> art = new LinkedHashMap<>();
    private final BlackCardNav blackCardNav = new BlackCardNav();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private Typeface script;
    private int selectedTab;

    public void load(Resources res) {
        art.clear();
        art.put("top", BitmapFactory.decodeResource(res, R.drawable.gs_top));
        art.put("header", BitmapFactory.decodeResource(res, R.drawable.gs_header));
        art.put("back", BitmapFactory.decodeResource(res, R.drawable.gs_back));
        art.put("panel", BitmapFactory.decodeResource(res, R.drawable.gs_panel));
        art.put("card", BitmapFactory.decodeResource(res, R.drawable.gs_card));
        art.put("tab", BitmapFactory.decodeResource(res, R.drawable.gs_tab));
        art.put("arrow", BitmapFactory.decodeResource(res, R.drawable.gs_arrow));
        art.put("lower", BitmapFactory.decodeResource(res, R.drawable.gl_lower));
        art.put("gs_grid", BitmapFactory.decodeResource(res, R.drawable.gs_grid));
        art.put("funds", BitmapFactory.decodeResource(res, R.drawable.gs_funds));
        art.put("discover", BitmapFactory.decodeResource(res, R.drawable.gs_discover));
        art.put("banner", BitmapFactory.decodeResource(res, R.drawable.gs_banner));
        art.put("black", BitmapFactory.decodeResource(res, R.drawable.gs_black));
        art.put("advisor", BitmapFactory.decodeResource(res, R.drawable.lci_advisor));
        art.put("advisorTalk", BitmapFactory.decodeResource(res, R.drawable.lci_advisor_talk));
        art.put("advisorBook", BitmapFactory.decodeResource(res, R.drawable.lci_advisor_book));
        art.put("nav", BitmapFactory.decodeResource(res, R.drawable.gs_nav));
        for (int tier = LicaiVersion.GROWTH; tier <= LicaiVersion.V3; tier++) {
            LicaiVersion.Theme theme = LicaiVersion.theme(tier);
            art.put("theme_badge_" + tier, BitmapFactory.decodeResource(res, theme.badgeDrawable));
            art.put("theme_card_" + tier, BitmapFactory.decodeResource(res, theme.cardDrawable));
            art.put("theme_holder_" + tier, BitmapFactory.decodeResource(res, theme.holderDrawable));
        }
        script = scriptFace();
        blackCardNav.load(res);
        // The two ends of the panel, each a two-row strip that runs one row of a plate into the
        // panel's own wash; stretched over the panel's first and last rows they carry the header
        // into it and it into the card's plate, per column, so neither end has a step.
        Bitmap header = art.get("header");
        Bitmap back = art.get("back");
        if (header != null) {
            art.put("panelTop", blendStrip(header, header.getHeight() - 1, true));
        }
        if (back != null) {
            art.put("panelBottom", blendStrip(back, 0, false));
            art.put("panelFoot", rowStrip(back, 0));
        }
    }

    /** One row of a plate on its own, for a fill that has to follow the plate column by column. */
    private static Bitmap rowStrip(Bitmap plate, int row) {
        int width = plate.getWidth();
        Bitmap strip = Bitmap.createBitmap(width, 1, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width];
        plate.getPixels(pixels, 0, width, 0, row, width, 1);
        strip.setPixels(pixels, 0, width, 0, 0, width, 1);
        return strip;
    }

    /** One row of a plate beside the panel's own wash, in the order it runs down the screen. */
    private static Bitmap blendStrip(Bitmap plate, int row, boolean plateAbove) {
        int width = plate.getWidth();
        Bitmap strip = Bitmap.createBitmap(width, 2, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width];
        plate.getPixels(pixels, 0, width, 0, row, width, 1);
        int[] wash = new int[width];
        java.util.Arrays.fill(wash, PANEL_WASH);
        strip.setPixels(plateAbove ? pixels : wash, 0, width, 0, 0, width, 1);
        strip.setPixels(plateAbove ? wash : pixels, 0, width, 0, 1, width, 1);
        return strip;
    }

    /**
     * The face the card's signature is written in. The reference hand-writes the holder's name, and
     * the platform's own script family is the only hand-drawn face guaranteed to be there; where a
     * device has none the italic serif stands in.
     */
    private static Typeface scriptFace() {
        Typeface face = Typeface.create("cursive", Typeface.NORMAL);
        if (face == null || face.equals(Typeface.create((String) null, Typeface.NORMAL))) {
            face = Typeface.create(Typeface.SERIF, Typeface.ITALIC);
        }
        return face;
    }

    /**
     * The unfolded panel lengthens the page, so the scroll range grows with it. The 市场 tab is also
     * the reference's own screen plus the 理财师 card, so it scrolls by exactly that card where the
     * 服务 tab, which is laid out to fit, still sits still.
     */
    public float maxScroll(LicaiScreen.State s) {
        float content = selectedTab == 1 ? CONTENT_H : SHEET_BOTTOM;
        return Math.max(0f, content + PANEL_H * s.expand - H);
    }

    // ------------------------------------------------------------------ drawing

    public void draw(Canvas c, LicaiScreen.State s, LicaiScreen page) {
        float off = unfold(s);
        c.drawColor(PAGE_BG);
        c.save();
        c.translate(0, -s.scroll);
        c.save();
        c.clipRect(0, 0, W, HEADER_BOTTOM + off);
        // The reference's own wash behind the status bar, so the band the system's clock sits on
        // meets the header plate below it without a seam — the tier's own wash is a different
        // colour and read as a hard line across the top of the page.
        drawArt(c, "top", TOP_PLATE);
        drawArt(c, "header", HEADER_PLATE);
        drawLiveHeader(c, s);
        drawPanelWash(c, off);
        drawPanel(c, off);
        c.restore();
        c.save();
        c.translate(0, off);
        // The reference's wash behind the card, carried down past the sheet's own top edge. The
        // tier card assets already carry their frame and their shadow, so nothing of the card is
        // taken from it — only the strips beside it and the sliver its rounded corners leave open.
        drawArt(c, "back", BACK_PLATE);
        drawArt(c, "theme_card_" + LicaiVersion.current(), THEME_CARD);
        drawHolder(c);
        drawSheet(c);
        c.save();
        c.clipRect(0, SHEET_TOP, W, SHEET_BOTTOM);
        if (selectedTab == 0) drawMarketContent(c);
        else drawServiceContent(c);
        c.restore();
        c.restore();
        c.restore();
        // Once the dark sheet has scrolled away the status bar sits on the white page.
        if (s.scroll > HEADER_BOTTOM - 150f) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(PAGE_BG);
            c.drawRect(0, 0, W, 150, paint);
        }
    }

    private static float unfold(LicaiScreen.State s) {
        return PANEL_H * Math.max(0f, Math.min(1f, s.expand));
    }

    /**
     * The run the header plate leaves empty: the three figures under the captions. While the eye is
     * shut they are the reference's own four stars, at the reference's own pitch; opening the eye
     * swaps them for what 总资产 and the rate work out to.
     */
    private void drawLiveHeader(Canvas c, LicaiScreen.State s) {
        if (GrowthStore.hidden) {
            for (float[] group : STARS) {
                for (float x : group) {
                    text(c, "*", x, STAR_BASE, STAR_SIZE, GOLD);
                }
            }
            return;
        }
        text(c, GrowthStore.value("total"), VALUE_TOTAL_X, VALUE_BASE, VALUE_SIZE, GOLD);
        textRight(c, GrowthStore.value("yesterday"), VALUE_YEST_RIGHT, VALUE_BASE, VALUE_SIZE, GOLD);
        textRight(c, GrowthStore.value("family"), VALUE_FAMILY_RIGHT, VALUE_BASE, VALUE_SIZE, GOLD);
        text(c, "份", FAMILY_UNIT_X, VALUE_BASE - 10, LABEL_SIZE, LABEL_INK);
    }

    /**
     * The sheet the panel unfolds into. Its top is carried out of the header plate's own last row
     * and its bottom into the plate the card sits on, both per column, so unfolding it cuts no line
     * across the page under the 资产 figures and meets the card below without a step; in between it
     * settles on the wash the reference's own capture of this panel shows. Painted flat it drew a
     * hard band right across the page, because a single colour cannot follow the wash above it.
     */
    private void drawPanelWash(Canvas c, float off) {
        // Folded, the wash still covers the strip between the header and the plate the card sits
        // on, so the header's own last row runs into it instead of into the page's grey.
        float bottom = 592f + Math.max(off, 8f);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PANEL_WASH);
        // The wash goes down first over the whole panel and the two ends are laid on top: drawn
        // edge to edge instead, each one's filtered last row blended with the page's own grey
        // underneath and left a hairline right across it.
        c.drawRect(0, 592f, W, bottom, paint);
        if (off <= 0f) return;
        float blend = Math.min(PANEL_BLEND, Math.max(0f, off / 2f - PANEL_FOOT));
        if (blend > 1f) {
            drawArt(c, "panelTop", 0, 592f, W, 592f + blend);
            drawArt(c, "panelBottom", 0, bottom - blend, W, bottom - PANEL_FOOT);
        }
        // Its last rows are drawn again exactly as they are, a few rows over the plate below: a
        // ramp that stops where the plate starts leaves its own filtered edge showing through, and
        // that read as a line across the page right above the card.
        drawArt(c, "panelFoot", 0, bottom - PANEL_FOOT, W, bottom + PANEL_FOOT + 2f);
    }

    /**
     * The rows that live between the header and the card: 余额宝 and 基金, 更多资产服务, 我的保障资产 and
     * 我的额度 with the lines under them. They are laid out for the fully open panel and simply get
     * revealed as the clip grows, so the sheet appears to unfold downwards while the card and
     * everything under it slides down — exactly what the 普通版's panel does.
     */
    private void drawPanel(Canvas c, float off) {
        text(c, "余额宝", PANEL_A, PANEL_BASE[0], PANEL_SIZE, PANEL_LABEL);
        text(c, GrowthStore.value("reserve"), PANEL_B, PANEL_BASE[0], PANEL_SIZE, PANEL_VALUE);
        text(c, GrowthStore.value("reserveProfit"), 506f, PANEL_BASE[0], PANEL_SIZE, PANEL_VALUE);
        text(c, "基金", PANEL_C, PANEL_BASE[0], PANEL_SIZE, PANEL_LABEL);
        text(c, GrowthStore.value("fund"), PANEL_D, PANEL_BASE[0], PANEL_SIZE, PANEL_VALUE);
        text(c, GrowthStore.value("fundProfit"), 1002f, PANEL_BASE[0], PANEL_SIZE, PANEL_VALUE);

        text(c, "更多资产服务", PANEL_A, PANEL_BASE[1], PANEL_SIZE, PANEL_LABEL);
        text(c, "去看看", 487f, PANEL_BASE[1], PANEL_SIZE, PANEL_LABEL);
        text(c, "我的保障资产", PANEL_A, PANEL_BASE[2], PANEL_SIZE, PANEL_CAPTION);

        text(c, "保险", PANEL_A, PANEL_BASE[3], PANEL_SIZE, PANEL_LABEL);
        text(c, "1份保单保障中", 221f, PANEL_BASE[3], PANEL_SIZE, PANEL_VALUE);
        text(c, "储蓄|养老", PANEL_C, PANEL_BASE[3], PANEL_SIZE, PANEL_LABEL);
        text(c, GrowthStore.value("savings"), 857f, PANEL_BASE[3], PANEL_SIZE, PANEL_VALUE);

        text(c, "我的额度", PANEL_A, PANEL_BASE[4], PANEL_SIZE, PANEL_CAPTION);
        text(c, "花呗", PANEL_A, PANEL_BASE[5], PANEL_SIZE, PANEL_LABEL);
        text(c, GrowthStore.value("huabei"), 218f, PANEL_BASE[5], PANEL_SIZE, PANEL_VALUE);
        text(c, "借呗", 657f, PANEL_BASE[5], PANEL_SIZE, PANEL_LABEL);
        text(c, "秒到账，随时还", 755f, PANEL_BASE[5], PANEL_SIZE, PANEL_VALUE);

        text(c, "备用金", 120f, PANEL_BASE[6], PANEL_SIZE, PANEL_LABEL);
        text(c, "去使用", 255f, PANEL_BASE[6], PANEL_SIZE, PANEL_VALUE);
        text(c, "网商贷", PANEL_C, PANEL_BASE[6], PANEL_SIZE, PANEL_LABEL);
        text(c, "更新额度", 792f, PANEL_BASE[6], PANEL_SIZE, PANEL_VALUE);
        caret(c, 639f, off);
    }

    /** The sheet the reference carries, under the card and behind the 市场 / 服务 block. */
    private void drawSheet(Canvas c) {
        paint.setShader(new LinearGradient(0, SHEET_TOP, 0, SHEET_TOP + 520,
                CARD_WHITE, SHEET_LOW, Shader.TileMode.CLAMP));
        c.drawRoundRect(SHEET, SHEET_RADIUS, SHEET_RADIUS, paint);
        paint.setShader(null);
        // the 市场 / 服务 block and the sheet's own top edge, as one masked piece
        drawArt(c, "tab", TAB_BOX);
        // the grid of ten
        Bitmap grid = art.get("gs_grid");
        if (grid != null) {
            r.set(0, SHEET_TOP + GRID_TOP, W, SHEET_TOP + GRID_TOP + grid.getHeight());
            paint.setColor(Color.WHITE);
            c.drawBitmap(grid, null, r, paint);
        }
        drawTabLabels(c);
    }

    private void drawTabLabels(Canvas c) {
        // The two captions of the sheet's own tab block, drawn here rather than left in the plate:
        // one of them is the selected tab and the two swap ink with it, and a plate that carried
        // them still printed its own pair under this page's, which read as two 市场 on top of each
        // other. They land on the reference's own two boxes, which are narrower than the face
        // would set them, so each is squeezed to the width the reference gives it.
        boolean market = selectedTab == 0;
        label(c, "市场", TAB_CX[0], TAB_BASE, TAB_SIZE, market ? TAB_ON : TAB_OFF);
        label(c, "服务", TAB_CX[1], TAB_BASE, TAB_SIZE, market ? TAB_OFF : TAB_ON);
        float cx = TAB_CX[market ? 0 : 1];
        round(c, cx - TAB_UNDERLINE / 2f, 838f, cx + TAB_UNDERLINE / 2f, 845f, 4f, 0xff94705e);
    }

    /**
     * A run drawn from {@code x} at exactly the width the reference gives it. The reference's face
     * sets CJK narrower than the platform's, so a run measured for its cap height comes out too
     * wide; squeezing it to the reference's own width puts both ends where they belong.
     */
    private void fitText(Canvas c, String value, float x, float baseline, float size, int color,
                         boolean bold, float width) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        float natural = paint.measureText(value) * SX;
        float squeeze = width / Math.max(1f, natural);
        c.save();
        c.scale(SX * squeeze, 1f);
        c.drawText(value, x / (SX * squeeze), baseline, paint);
        c.restore();
    }

    /** One tab caption, centred and squeezed to the box the reference sets it in. */
    private void label(Canvas c, String value, float cx, float baseline, float size, int color) {        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        float natural = paint.measureText(value) * SX;
        float squeeze = TAB_ADVANCE / Math.max(1f, natural);
        c.save();
        c.scale(SX * squeeze, 1f);
        c.drawText(value, cx / (SX * squeeze), baseline, paint);
        c.restore();
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawMarketContent(Canvas c) {
        drawAdvisor(c);
        // Everything under the 理财师 card sits MARKET_INSERT lower than the reference's own screen,
        // so the card adds itself to the page and moves nothing of the reference's own layout.
        c.save();
        c.translate(0, MARKET_INSERT);
        drawMarketRow(c);
        drawArt(c, "funds", 0, FUNDS_TOP, W, FUNDS_TOP + FUNDS_H);
        drawFundFigures(c);
        drawArt(c, "discover", 0, DISCOVER_TOP, W, DISCOVER_TOP + DISCOVER_H);
        drawDiscoverFigures(c);
        drawArt(c, "banner", 0, BANNER_TOP, W, BANNER_TOP + BANNER_H);
        // The 黑卡专区 strip closes the sheet. The reference's own capture cuts its title off under
        // the navigation bar, so the strip travels without it and both the title and the tail the
        // bar covers are drawn here; the page ends on that strip rather than trailing off into
        // empty white under the 严选五类资产 header.
        drawArt(c, "black", 0, BLACK_TOP, W, BLACK_TOP + BLACK_H);
        // The strip runs on behind the page's own bar — the reference's capture simply stops
        // there — so its last row is carried to the foot of the page. Left as the sheet's grey it
        // drew a line across the page at the bar's top edge.
        drawTail(c, "black", BLACK_TOP + BLACK_H, H);
        text(c, "黑卡专区", BLACK_LABEL_X, BLACK_LABEL_BASE, BLACK_LABEL_SIZE, BLACK_INK, true);
        c.restore();
    }

    /** The 财富黑卡理财师 card: his picture, his name and title, and the two round buttons. */
    private void drawAdvisor(Canvas c) {
        round(c, ADVISOR.left, ADVISOR.top, ADVISOR.right, ADVISOR.bottom, ADVISOR_RADIUS, CARD_WHITE);
        Bitmap picture = AdvisorStore.avatar();
        if (picture == null) picture = art.get("advisor");
        if (picture != null) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            r.set(ADVISOR_AVATAR.left, ADVISOR_AVATAR.top, ADVISOR_AVATAR.right, ADVISOR_AVATAR.bottom);
            c.save();
            c.clipPath(roundPath(ADVISOR_AVATAR, (ADVISOR_AVATAR.width()) / 2f));
            c.drawBitmap(picture, null, r, paint);
            c.restore();
        }
        // his name is the user's to change; the title under it is not
        fitText(c, AdvisorStore.name(), ADVISOR_NAME_X, ADVISOR_NAME_BASE, ADVISOR_NAME_SIZE,
                ADVISOR_NAME_INK, true, ADVISOR_NAME_W);
        fitText(c, ADVISOR_ROLE, ADVISOR_ROLE_X, ADVISOR_ROLE_BASE, ADVISOR_ROLE_SIZE,
                ADVISOR_ROLE_INK, false, ADVISOR_ROLE_W);
        // both buttons are the reference's own glyphs, keyed to alpha and painted in one ink
        for (String name : new String[]{"advisorTalk", "advisorBook"}) {
            Bitmap glyph = art.get(name);
            if (glyph == null) continue;
            RectF box = "advisorTalk".equals(name) ? ADVISOR_TALK : ADVISOR_BOOK;
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ADVISOR_BUTTON_INK);
            paint.setColorFilter(new PorterDuffColorFilter(ADVISOR_BUTTON_INK,
                    PorterDuff.Mode.SRC_IN));
            r.set(box.left, box.top, box.right, box.bottom);
            c.drawBitmap(glyph, null, r, paint);
            paint.setColorFilter(null);
        }
    }

    private Path roundPath(RectF box, float radius) {
        Path path = new Path();
        path.addRoundRect(box, radius, radius, Path.Direction.CW);
        return path;
    }

    /** The returns the 基金自选 and 基金排行 bands are cut with painted out of them. */
    private void drawFundFigures(Canvas c) {
        text(c, GrowthStore.value("fundSelfPct"), FUND_SELF_X, FUND_SELF_BASE,
                FUND_SELF_SIZE, PROD_INK, true);
        round(c, FUND_TAG.left, FUND_TAG.top, FUND_TAG.right, FUND_TAG.bottom,
                FUND_TAG_RADIUS, TAG_BG);
        text(c, GrowthStore.value("fundSelfTag"), FUND_TAG_X, FUND_TAG_BASE,
                FUND_TAG_SIZE, TAG_INK);
        float right = RANK_RIGHT;
        textRight(c, GrowthStore.value("rankPct0"), right, RANK_1_BASE, RANK_SIZE, PROD_INK);
        textRight(c, GrowthStore.value("rankPct1"), right, RANK_2_BASE, RANK_SIZE, PROD_INK);
    }

    /** The same for the two figures the 选好品 … 盘友圈 band is cut without. */
    private void drawDiscoverFigures(Canvas c) {
        text(c, GrowthStore.value("windMove"), MKT_X, MKT_BASE, MKT_SIZE, MOVE_INK, true);
        text(c, GrowthStore.value("allocPct"), ALLOC_X, ALLOC_BASE, ALLOC_SIZE, WIND_INK, true);
    }

    private void drawServiceContent(Canvas c) {
        paint.setShader(null);
        // The sheet's own body is the pale grey the reference lays its cards on, so the cards read
        // as cards; painting it white left every one of them with nothing under it.
        paint.setColor(CARD_WHITE);
        c.drawRect(0, SHEET_TOP + GRID_TOP, W, SHEET_TOP + 72, paint);
        paint.setColor(SHEET_LOW);
        c.drawRect(0, SHEET_TOP + 72, W, CONTENT_H, paint);
        // the 市场 / 服务 captions are the sheet's own and were drawn with it, above this fill
        round(c, 36, 930, 1244, 1545, 34, CARD_WHITE);
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) {
                int i = row * 4 + col;
                float cx = SERVICE_CX[col];
                float cy = SERVICE_ROW_CY[row];
                round(c, cx - 48, cy - 48, cx + 48, cy + 48, 48, SERVICE_COLORS[i]);
                center(c, SERVICE_MARKS[i], cx, cy + 19, 48, Color.WHITE);
                center(c, SERVICE_TITLES[i], cx, SERVICE_LABEL_BASE[row], 40, 0xff292a2d);
                center(c, "专属服务", cx, SERVICE_CAPTION_BASE[row], 28, 0xff92969d);
            }
        }
        line(c, 65, 1452, 1215, 1452, 0xffeff0f2, 2);
        text(c, "为黑卡用户提供专属权益与财富管理服务", 82, 1516, 34, 0xff777c84);
        text(c, "查看全部", 1040, 1516, 30, 0xff9b7257);
        round(c, 36, 1575, 1244, 1975, 34, CARD_WHITE);
        round(c, 84, 1615, 284, 1815, 100, 0xffe9edf4);
        center(c, "理", 184, 1743, 96, 0xff8b725e);
        text(c, "专属理财师", 330, 1685, 48, 0xff25262a);
        text(c, "为你提供一对一财富规划服务", 330, 1756, 34, 0xff858990);
        round(c, 330, 1815, 575, 1890, 38, 0xfff3eee9);
        center(c, "在线咨询", 452, 1866, 34, 0xff9b7257);
        round(c, 800, 1815, 1045, 1890, 38, 0xffedf3fb);
        center(c, "电话联系", 922, 1866, 34, 0xff567aab);
        round(c, 36, 2005, 1244, 2530, 34, CARD_WHITE);
        text(c, "市场行情", 82, 2088, 46, 0xff28292d);
        for (int i = 0; i < MARKET_NAMES.length; i++) {
            float base = MARKET_ROW_BASE + i * MARKET_ROW_STEP;
            text(c, MARKET_NAMES[i], 82, base, 42, 0xff55585e);
            textRight(c, GrowthStore.value(MARKET_POINTS[i]), 985, base, 42, 0xff34363a);
            String change = GrowthStore.value(MARKET_CHANGES[i]);
            textRight(c, change, 1195, base, 42, change.startsWith("+") ? RED : IDX_INK);
        }
    }

    /** The index row the sheet carries: 纳斯达克's points, its move and its change. */
    private void drawMarketRow(Canvas c) {
        round(c, MARKET_CARD.left, SHEET_TOP + MARKET_CARD.top, MARKET_CARD.right,
                SHEET_TOP + MARKET_CARD.bottom, 30f, CARD_WHITE);
        text(c, "纳斯达克", MARKET_NAME_X, SHEET_TOP + MARKET_BASE, MARKET_SIZE, 0xff26262a);
        for (int i = 0; i < MARKET_KEYS.length; i++) {
            text(c, GrowthStore.value(MARKET_KEYS[i]), MARKET_VALUE_X[i],
                    SHEET_TOP + MARKET_BASE, MARKET_SIZE, RED);
        }
        drawArt(c, "arrow", MARKET_ARROW.left, SHEET_TOP + MARKET_ARROW.top,
                MARKET_ARROW.right, SHEET_TOP + MARKET_ARROW.bottom);
    }

    /**
     * Everything under the 纳斯达克 row: the index board, the 资金流入 / 投什么·今日风口 pair, the
     * 好品 / 服务 / 盘友圈 bar and the 安稳理财 card with its three products. The section travels as
     * one piece of the reference it was supplied as — every card, chip, badge and label on it is that
     * reference's own artwork — and only the figures are drawn here, each of them editable.
     */
    /** The card's 持卡人 signature, written where the reference's own blurred one sat. */
    private void drawHolder(Canvas c) {
        String name = GrowthStore.value("holder");
        if (name.isEmpty()) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(HOLDER_INK);
        paint.setTextSize(HOLDER_SIZE);
        paint.setTypeface(script);
        paint.setTextAlign(Paint.Align.LEFT);
        float width = paint.measureText(name);
        float squeeze = width > HOLDER_MAX ? HOLDER_MAX / width : 1f;
        c.save();
        c.scale(squeeze, 1f);
        c.drawText(name, HOLDER_X / squeeze, HOLDER_BASE, paint);
        c.restore();
    }

    /**
     * The caret on the panel's bottom edge: it rides down with the panel and turns over as it opens,
     * pointing down while the panel is shut and up while it is out, exactly like the 普通版's.
     */
    private void caret(Canvas c, float cx, float off) {
        float open = Math.max(0f, Math.min(1f, off / PANEL_H));
        float cy = 572.5f + off;
        float dir = 1f - 2f * open;
        line(c, cx - 19, cy - 10.5f * dir, cx, cy + 10.5f * dir, PANEL_CARET, 5);
        line(c, cx, cy + 10.5f * dir, cx + 19, cy - 10.5f * dir, PANEL_CARET, 5);
    }

    // ------------------------------------------------------------------ hit test

    public String hitTest(LicaiScreen.State s, LicaiScreen page, float x, float y) {
        if (y >= NAV_TOP) return "nav:" + nearest(NAV_X, x);
        float off = unfold(s);
        float cy = y + s.scroll;
        float panelBottom = HEADER_BOTTOM + off;

        // the eye, then the rate the figures work from, then the three figures themselves
        if (x >= EYE.left - 20 && x <= EYE.right + 10
                && cy >= EYE.top - 20 && cy <= EYE.bottom + 12) {
            return "toggleHide";
        }
        if (x >= RATE.left && x <= RATE.right && cy >= RATE.top && cy <= RATE.bottom) {
            return "gedit:rate";
        }
        // the caret, then the rows the panel unfolds
        float caretY = 572.5f + off;
        if (x > 569 && x < 709 && cy > caretY - 40 && cy < caretY + 45) return "toggleExpand";
        if (cy >= HEADER_BOTTOM && cy <= panelBottom) {
            if (cy >= PANEL_BASE[0] - 40 && cy <= PANEL_BASE[0] + 22) {
                if (x >= 250 && x <= 480) return "gedit:reserve";
                if (x >= 486 && x <= 640) return "gedit:reserveProfit";
                if (x >= 750 && x <= 970) return "gedit:fund";
                if (x >= 985) return "gedit:fundProfit";
                return "noop";
            }
            if (cy >= PANEL_BASE[1] - 36 && cy <= PANEL_BASE[1] + 22) {
                return "noop";
            }
            if (cy >= PANEL_BASE[3] - 36 && cy <= PANEL_BASE[3] + 22) {
                if (x >= 850) return "gedit:savings";
                return "noop";
            }
            if (cy >= PANEL_BASE[5] - 36 && cy <= PANEL_BASE[5] + 22) {
                if (x >= 210 && x <= 460) return "gedit:huabei";
                return "noop";
            }
            if (cy >= PANEL_BASE[6] - 36 && cy <= PANEL_BASE[6] + 22) {
                return "noop";
            }
            return "toggleExpand";
        }
        // the card answers with its holder's signature, and is a demo tap everywhere else
        float sheetTop = SHEET_TOP + off;
        if (cy >= TAB_BOX.top + off && cy <= sheetTop) {
            selectedTab = x < 280 ? 0 : 1;
            return "redraw";
        }
        float cardTop = CARD_BOX.top + off;
        if (cy >= cardTop + 12 && cy <= cardTop + 108 && x >= 950) return "gedit:holder";
        // the badge is where the 财富黑卡 等级 is changed, so it raises the version sheet
        if (cy >= cardTop + 38 && cy <= cardTop + 106 && x >= 56 && x <= 414) return "version";
        if (cy >= cardTop && cy <= cardTop + CARD_BOX.height()) {
            return "noop";
        }
        if (cy >= 440 && cy <= 545) {
            if (x < 300) return "gedit:total";
            if (x > 700 && x < 1030) return "gedit:yesterday";
            if (x > 1040) return "gedit:family";
        }
        // the 市场 / 服务 block, and the sheet's own top edge beside it
        if (cy < sheetTop) return "version";
        // the sheet: its own figures answer, everything else is a demo tap
        float sy = cy - off;
        if (selectedTab == 1) {
            // the eight 权益 cells, two rows of four
            if (sy >= 930 && sy <= 1545) {
                return "noop";
            }
            if (sy >= 1575 && sy <= 1975) {
                if (sy >= 1800 && x >= 760) return "noop";
                if (sy >= 1800 && x >= 290) return "noop";
                return "noop";
            }
            if (sy >= 2005 && sy <= 2530) {
                if (sy < MARKET_ROW_BASE - 42) return "noop";
                int row = Math.min(MARKET_NAMES.length - 1,
                        Math.max(0, (int) ((sy - (MARKET_ROW_BASE - 42)) / MARKET_ROW_STEP)));
                return "gedit:" + (x >= 990 ? MARKET_CHANGES[row] : MARKET_POINTS[row]);
            }
            return "noop";
        }
        // the 理财师 card the 市场 tab carries under its grid: his picture and name are the two the
        // user may change, the two round buttons answer with a demo tap like the rest of the page
        if (sy >= ADVISOR.top - 8 && sy <= ADVISOR.bottom + 8) {
            if (sy >= ADVISOR_TALK.top - 6 && sy <= ADVISOR_TALK.bottom + 6) {
                if (x >= ADVISOR_BOOK.left - 6) return "noop";
                if (x >= ADVISOR_TALK.left - 6) return "noop";
            }
            if (x >= ADVISOR_AVATAR.left - 8 && x <= ADVISOR_AVATAR.right + 8
                    && sy >= ADVISOR_AVATAR.top - 8 && sy <= ADVISOR_AVATAR.bottom + 8) {
                return "advisorAvatar";
            }
            if (x >= ADVISOR_NAME_X - 8 && x <= ADVISOR_NAME_X + ADVISOR_NAME_W + 60
                    && sy >= ADVISOR_NAME_BASE - ADVISOR_NAME_SIZE
                    && sy <= ADVISOR_NAME_BASE + 14) {
                return "advisorName";
            }
            return "noop";
        }
        // everything below the card is drawn MARKET_INSERT lower than the reference's own screen
        float my = sy - MARKET_INSERT;
        if (my >= SHEET_TOP + MARKET_CARD.top && my <= SHEET_TOP + MARKET_CARD.bottom) {
            for (int i = MARKET_KEYS.length - 1; i >= 0; i--) {
                if (x >= MARKET_VALUE_X[i] - 20) return "gedit:" + MARKET_KEYS[i];
            }
            return "noop";
        }
        if (my >= SHEET_TOP + GRID_TOP && my < SHEET_TOP + MARKET_CARD.top) {
            int row = my >= GRID_BASE[0] + 6 ? 1 : 0;
            return "noop";
        }
        // the figures the two bands are cut without answer first, each with the 编辑 dialog the
        // rest of the page uses
        if (my >= FUND_TAG.top - 6 && my <= FUND_TAG.bottom + 16) {
            if (x >= 60 && x <= 292) return "gedit:fundSelfPct";
            if (x > 292 && x <= 540) return "gedit:fundSelfTag";
        }
        if (my >= RANK_1_BASE - 52 && my <= RANK_1_BASE + 24 && x >= 990) return "gedit:rankPct0";
        if (my >= RANK_2_BASE - 52 && my <= RANK_2_BASE + 24 && x >= 990) return "gedit:rankPct1";
        if (my >= MKT_BASE - 42 && my <= MKT_BASE + 20 && x >= 440 && x <= 610) {
            return "gedit:windMove";
        }
        if (my >= ALLOC_BASE - 42 && my <= ALLOC_BASE + 20 && x >= 630 && x <= 800) {
            return "gedit:allocPct";
        }
        if (my >= FUNDS_TOP && my <= FUNDS_TOP + FUNDS_H) return "noop";
        if (my >= DISCOVER_TOP && my <= DISCOVER_TOP + DISCOVER_H) {
            return "noop";
        }
        if (my >= BANNER_TOP && my <= BANNER_TOP + BANNER_H) {
            return "noop";
        }
        return "noop";
    }

    public void drawNav(Canvas c, LicaiScreen page) {
        // The same bar every other tab of the module wears, so switching to 理财 does not swap the
        // five glyphs underneath the page. The reference's own cut bar carried a lighter set the
        // rest of the module does not use.
        if (blackCardNav.draw(c, 1, W, NAV_TOP, H)) return;
        Bitmap nav = art.get("nav");
        if (nav == null) {
            page.drawNav(c);
            return;
        }
        paint.setShader(null);
        paint.setColor(Color.WHITE);
        c.drawRect(0, NAV_TOP, W, H, paint);
        r.set(0, NAV_TOP, W, H);
        c.drawBitmap(nav, null, r, paint);
    }

    public void resetTab() {
        selectedTab = 0;
    }

    /** Which of the 服务 tab's eight 权益 cells a tap landed in. */
    private static int serviceCell(float sy, float x) {
        int row = sy >= 1195 ? 1 : 0;
        int col = x < 337 ? 0 : x < 642 ? 1 : x < 947 ? 2 : 3;
        return row * 4 + col;
    }

    private static int column(float[] boundaries, float x) {
        for (int i = 0; i < boundaries.length; i++) {
            if (x < boundaries[i]) return i;
        }
        return boundaries.length - 1;
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

    private void drawArt(Canvas c, String name, RectF box) {
        drawArt(c, name, box.left, box.top, box.right, box.bottom);
    }

    private void drawArt(Canvas c, String name, float l, float t, float rr, float b) {
        Bitmap bitmap = art.get(name);
        if (bitmap == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        r.set(l, t, rr, b);
        c.drawBitmap(bitmap, null, r, paint);
    }

    /**
     * Carries the bottom row of one of the cut bands down to a lower edge, at its own scale above
     * and one repeated row below. The bands are cut to the height the reference's capture shows and
     * nothing of the page below that; repeating the row they end on is what keeps a flat grey from
     * showing under them wherever the page runs on further than the capture does.
     */
    private void drawTail(Canvas c, String name, float from, float to) {
        Bitmap bitmap = art.get(name);
        if (bitmap == null || to <= from) return;
        Rect src = new Rect(0, bitmap.getHeight() - 1, bitmap.getWidth(), bitmap.getHeight());
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        r.set(0, from, W, to);
        c.drawBitmap(bitmap, src, r, paint);
    }

    private float text(Canvas c, String value, float x, float baseline, float size, int color) {
        return text(c, value, x, baseline, size, color, false);
    }

    private float text(Canvas c, String value, float x, float baseline, float size, int color,
                       boolean bold) {
        if (value == null) return x;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        c.save();
        c.scale(SX, 1f);
        c.drawText(value, x / SX, baseline, paint);
        c.restore();
        return x + paint.measureText(value) * SX;
    }

    private void textRight(Canvas c, String value, float x, float baseline, float size, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.RIGHT);
        c.save();
        c.scale(SX, 1f);
        c.drawText(value, x / SX, baseline, paint);
        c.restore();
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void center(Canvas c, String value, float cx, float baseline, float size, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        c.save();
        c.scale(SX, 1f);
        c.drawText(value, cx / SX, baseline, paint);
        c.restore();
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private float textWidth(String value, float size) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        return paint.measureText(value) * SX;
    }

    private void round(Canvas c, float l, float t, float rr, float b, float radius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRoundRect(l, t, rr, b, radius, radius, paint);
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
}
