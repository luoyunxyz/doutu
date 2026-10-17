package com.luoyun.doutu.features.travel.ui;

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

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Native rendition of the 出行 page reached from the 小支主页's 出行 entry.
 *
 * <p>The page is a 支付宝-style travel mini-app: the pale wash with the address, the weather and the
 * search pill at the top; the five entries 公交地铁 / 机票 / 加油 / 公共充电 / 租车, the second one
 * being the one the page is on; the 机票 card underneath — its four tabs, the 多平台比价 line with
 * the three platforms' marks, 北京 ⇄ 杭州, the date, 不限舱位 with the two passenger checkboxes, the
 * coupon strip, the 查询机票 button and the four cells under it — then the two service rows of
 * 打车 / 12306 / 骑行 / 酒店预订 / 出行优惠 and 顺风车 / 汽车票 / 实时公交 / 出行规划 / 全部服务,
 * the start of the 出去玩 section and the section's own bar along the bottom.
 *
 * <p>The reference gives this page one screen: the copy under the 机票 card and the four cells under
 * its button are the reference's own, the two service rows and the coupon strip too, and the cards,
 * the pills, the rules, the button and every caption are drawn from primitives on the module's
 * 1280×2816 canvas. Only the artwork that cannot be redrawn is cut from the reference — the
 * colourful glyphs of the entries and the service rows, the three platform marks, the coupon badge,
 * the 里程币 mark, the four cells' glyphs and the bar's four glyphs plus the assistant's photo — by
 * `_travel/cut_icons.py`, each keyed against the background it sits on.
 */
public final class TravelScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;

    /** The reference's font is narrower than the device's; captions are squeezed to match it. */
    private static final float TEXT_SQUEEZE = 0.94f;

    // ------------------------------------------------------------------ the wash and the header
    private static final int WASH_TOP = 0xffc3e2fe;
    private static final int WASH_BOTTOM = 0xffe9f5ff;
    private static final float WASH_H = 700f;
    private static final int INK = 0xff1f2733;
    private static final int GREY = 0xff6b7280;
    private static final int WHITE = 0xffffffff;
    private static final int RULE = 0xffeef0f4;
    private static final int BLUE = 0xff1677ff;

    private static final float ADDR_X = 145f;
    private static final float ADDR_BASE = 226f;
    private static final float ADDR_SIZE = 52f;
    private static final float WEATHER_X = 146f;
    private static final float WEATHER_BASE = 264f;
    private static final float WEATHER_SIZE = 30f;
    private static final RectF PILL = new RectF(402f, 157f, 1122f, 269f);
    private static final float PILL_RADIUS = 56f;
    private static final float HINT_X = 492f;
    private static final float HINT_BASE = 233f;
    private static final float HINT_SIZE = 44f;
    private static final float SEARCH_DIVIDER_X = 940f;
    private static final float SEARCH_X = 966f;
    private static final float SEARCH_SIZE = 48f;

    // ------------------------------------------------------------------ the five entries
    private static final float[] ENTRY_CX = {139f, 390f, 640f, 890f, 1141f};
    private static final String[] ENTRY_NAMES = {"公交地铁", "机票", "加油", "公共充电", "租车"};
    private static final String[] ENTRY_ART = {"entryBus", "entryPlane", "entryFuel",
            "entryCharge", "entryCar"};
    private static final float ENTRY_TILE_CY = 395f;
    private static final float ENTRY_TILE = 152f;
    private static final float ENTRY_LABEL_BASE = 545f;
    private static final float ENTRY_LABEL_SIZE = 52f;
    /** 机票 is the entry the page stands on, so its tile is the skin's blue rather than the pale one. */
    private static final int TILE = 0xffe8f3fe;
    private static final int TILE_SELECTED = 0xff1c86f5;

    // ------------------------------------------------------------------ the 机票 card
    private static final RectF CARD = new RectF(24f, 598f, 1256f, 1891f);
    private static final float CARD_RADIUS = 28f;
    private static final RectF TAB_STRIP = new RectF(78f, 636f, 1205f, 716f);
    private static final RectF TAB_PILL = new RectF(89f, 636f, 354f, 716f);
    private static final float[] TAB_CX = {216f, 498f, 780f, 1063f};
    private static final String[] TAB_NAMES = {"单程", "往返", "值机选座", "快速安检"};
    private static final float TAB_BASE = 698f;
    private static final float TAB_SIZE = 46f;
    private static final int TAB_STRIP_FILL = 0xffe8f2fe;

    private static final float COMPARE_X = 95f;
    private static final float COMPARE_BASE = 822f;
    private static final float COMPARE_SIZE = 46f;
    private static final float COMPARE_SUB_X = 341f;
    private static final float COMPARE_SUB_SIZE = 40f;
    private static final String COMPARE = "多平台比价";
    private static final String COMPARE_SUB = "· 多家官方平台，智选低价";
    private static final RectF PLATFORMS = new RectF(1029f, 771f, 1185f, 845f);

    private static final float CITY_SIZE = 84f;
    private static final float ROUTE_BASE = 980f;
    private static final float CITY_LEFT_X = 85f;
    private static final float CITY_RIGHT_X = 1060f;
    private static final float SWAP_CX = 640f;
    private static final float SWAP_CY = 948f;
    private static final float SWAP_R = 38f;
    private static final float RULE_Y = 1032f;
    private static final float DATE_X = 89f;
    private static final float DATE_BASE = 1158f;
    private static final float DATE_SIZE = 84f;
    private static final float TODAY_SIZE = 46f;
    private static final String TODAY = "今天";

    private static final float CABIN_X = 84f;
    private static final float CABIN_BASE = 1338f;
    private static final float CABIN_SIZE = 46f;
    private static final String CABIN = "不限舱位";
    private static final float[] CHECK_CX = {696f, 987f};
    private static final float CHECK_CY = 1321f;
    private static final float CHECK_R = 25f;
    private static final String[] CHECK_NAMES = {"携带儿童", "携带婴儿"};
    private static final String[] CHECK_SUBS = {"2-12岁", "14天-2岁"};

    private static final RectF COUPON = new RectF(84f, 1433f, 1191f, 1524f);
    private static final int COUPON_FILL = 0xfffdeeee;
    private static final float COUPON_BASE = 1502f;
    private static final float COUPON_SIZE = 46f;
    private static final String COUPON_TEXT = "最高434元出行券包";
    private static final String COUPON_TAG = "周周领";
    private static final int COUPON_RED = 0xffe0301e;
    private static final String COUPON_LINK = "领优惠";
    private static final float COUPON_LINK_X = 1022f;

    private static final RectF QUERY = new RectF(105f, 1573f, 1174f, 1735f);
    private static final int QUERY_LEFT = 0xff2f9dff;
    private static final int QUERY_RIGHT = 0xff0a63f5;
    private static final String QUERY_TEXT = "查询机票";
    private static final float QUERY_TEXT_SIZE = 64f;

    private static final String[] FOOT_NAMES = {"我的订单", "乘机服务", "低价提醒", "更多服务"};
    private static final String[] FOOT_ART = {"footOrder", "footFlight", "footPrice", "footMore"};
    private static final float[] FOOT_TEXT_X = {144f, 442f, 739f, 1038f};
    private static final float FOOT_BASE = 1837f;
    private static final float FOOT_SIZE = 44f;

    // ------------------------------------------------------------------ the service card
    private static final RectF CARD2 = new RectF(24f, 1950f, 1256f, 2450f);
    private static final String[] GRID_ART = {"gridTaxi", "grid12306", "gridBike", "gridHotel",
            "gridCoupon", "gridCarpool", "gridBusticket", "gridTransit", "gridPlan", "gridAll"};
    private static final String[] GRID_NAMES = {"打车", "12306", "骑行", "酒店预订", "出行优惠",
            "顺风车", "汽车票", "实时公交", "出行规划", "全部服务"};
    private static final float[] GRID_TILE_CY = {2043f, 2294f};
    private static final float[] GRID_LABEL_BASE = {2185f, 2403f};
    private static final float GRID_LABEL_SIZE = 50f;
    private static final int TILE_WARM = 0xfffcf1e7;

    // ------------------------------------------------------------------ 出去玩 and the bar
    private static final RectF PLAY = new RectF(24f, 2475f, 1256f, 2816f);
    private static final float PLAY_TITLE_X = 73f;
    private static final float PLAY_TITLE_BASE = 2610f;
    private static final float PLAY_TITLE_SIZE = 66f;
    private static final String PLAY_TITLE = "出去玩";
    private static final float PLAY_SUB_X = 804f;
    private static final float PLAY_SUB_SIZE = 52f;
    private static final String PLAY_SUB = "AI 预测一下";

    private static final float NAV_TOP = 2560f;
    private static final float[] NAV_CX = {128f, 384f, 640f, 897f, 1151f};
    private static final String[] NAV_NAMES = {"首页", "车生活", "AI出行助手", "出去玩", "我的"};
    private static final String[] NAV_ART = {"navHome", "navCar", null, "navPlay", "navMe"};
    private static final float NAV_LABEL_BASE = 2724f;
    private static final float NAV_LABEL_SIZE = 44f;
    private static final int NAV_ON = 0xff1677ff;
    private static final int NAV_OFF = 0xff2b3550;
    private static final float NAV_AVATAR_CY = 2607f;
    private static final float NAV_AVATAR_R = 67f;

    // ------------------------------------------------------------------ the artwork
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final Map<String, Bitmap> art = new LinkedHashMap<>();
    private final Map<String, RectF> boxes = new LinkedHashMap<>();

    public void load(Resources res) {
        art.clear();
        boxes.clear();
        art.put("entryBus", BitmapFactory.decodeResource(res, R.drawable.tr_entry_bus));
        art.put("entryPlane", BitmapFactory.decodeResource(res, R.drawable.tr_entry_plane));
        art.put("entryFuel", BitmapFactory.decodeResource(res, R.drawable.tr_entry_fuel));
        art.put("entryCharge", BitmapFactory.decodeResource(res, R.drawable.tr_entry_charge));
        art.put("entryCar", BitmapFactory.decodeResource(res, R.drawable.tr_entry_car));
        art.put("platforms", BitmapFactory.decodeResource(res, R.drawable.tr_platforms));
        art.put("compareMark", BitmapFactory.decodeResource(res, R.drawable.tr_compare_mark));
        art.put("couponBadge", BitmapFactory.decodeResource(res, R.drawable.tr_coupon_badge));
        art.put("mileage", BitmapFactory.decodeResource(res, R.drawable.tr_mileage));
        art.put("footOrder", BitmapFactory.decodeResource(res, R.drawable.tr_foot_order));
        art.put("footFlight", BitmapFactory.decodeResource(res, R.drawable.tr_foot_flight));
        art.put("footPrice", BitmapFactory.decodeResource(res, R.drawable.tr_foot_price));
        art.put("footMore", BitmapFactory.decodeResource(res, R.drawable.tr_foot_more));
        art.put("gridTaxi", BitmapFactory.decodeResource(res, R.drawable.tr_grid_taxi));
        art.put("grid12306", BitmapFactory.decodeResource(res, R.drawable.tr_grid_12306));
        art.put("gridBike", BitmapFactory.decodeResource(res, R.drawable.tr_grid_bike));
        art.put("gridHotel", BitmapFactory.decodeResource(res, R.drawable.tr_grid_hotel));
        art.put("gridCoupon", BitmapFactory.decodeResource(res, R.drawable.tr_grid_coupon));
        art.put("gridCarpool", BitmapFactory.decodeResource(res, R.drawable.tr_grid_carpool));
        art.put("gridBusticket", BitmapFactory.decodeResource(res, R.drawable.tr_grid_busticket));
        art.put("gridTransit", BitmapFactory.decodeResource(res, R.drawable.tr_grid_transit));
        art.put("gridPlan", BitmapFactory.decodeResource(res, R.drawable.tr_grid_plan));
        art.put("gridAll", BitmapFactory.decodeResource(res, R.drawable.tr_grid_all));
        art.put("navHome", BitmapFactory.decodeResource(res, R.drawable.tr_nav_home));
        art.put("navCar", BitmapFactory.decodeResource(res, R.drawable.tr_nav_car));
        art.put("navPlay", BitmapFactory.decodeResource(res, R.drawable.tr_nav_play));
        art.put("navMe", BitmapFactory.decodeResource(res, R.drawable.tr_nav_me));
        art.put("navAvatar", BitmapFactory.decodeResource(res, R.drawable.tr_nav_avatar));

        // The boxes the cuts were taken over, so each glyph goes back where the reference has it.
        box("entryBus", 97f, 354f, 183f, 437f);
        box("entryPlane", 315f, 319f, 467f, 470f);
        box("entryFuel", 601f, 356f, 689f, 436f);
        box("entryCharge", 853f, 356f, 940f, 436f);
        box("entryCar", 1083f, 324f, 1217f, 433f);
        box("platforms", 1029f, 771f, 1185f, 845f);
        box("compareMark", 238f, 784f, 297f, 813f);
        box("couponBadge", 103f, 1456f, 161f, 1508f);
        box("mileage", 1170f, 158f, 1255f, 272f);
        box("footOrder", 86f, 1798f, 127f, 1838f);
        box("footFlight", 382f, 1796f, 424f, 1840f);
        box("footPrice", 679f, 1799f, 722f, 1837f);
        box("footMore", 979f, 1796f, 1019f, 1839f);
        icon("gridTaxi", "taxi", 89f, 1998f, 190f, 2084f);
        icon("grid12306", "12306", 346f, 1995f, 432f, 2094f);
        icon("gridBike", "bike", 589f, 1997f, 691f, 2084f);
        icon("gridHotel", "hotel", 846f, 2003f, 936f, 2093f);
        icon("gridCoupon", "coupon", 1095f, 1999f, 1186f, 2090f);
        icon("gridCarpool", "carpool", 92f, 2255f, 185f, 2333f);
        icon("gridBusticket", "busticket", 343f, 2253f, 436f, 2337f);
        icon("gridTransit", "transit", 596f, 2252f, 683f, 2337f);
        icon("gridPlan", "plan", 846f, 2257f, 936f, 2334f);
        icon("gridAll", "all", 1109f, 2263f, 1173f, 2327f);
        box("navHome", 91f, 2591f, 166f, 2663f);
        box("navCar", 342f, 2593f, 427f, 2663f);
        box("navPlay", 852f, 2591f, 937f, 2662f);
        box("navMe", 1116f, 2590f, 1188f, 2665f);
        box("navAvatar", 575f, 2540f, 708f, 2674f);
    }

    private void box(String name, float l, float t, float right, float b) {
        boxes.put(name, new RectF(l, t, right, b));
    }

    private void icon(String name, String label, float l, float t, float right, float b) {
        boxes.put(name, new RectF(l, t, right, b));
    }

    /** The page is one screen tall in the reference, so it does not scroll. */
    public float maxScroll() {
        return 0f;
    }

    // ------------------------------------------------------------------ drawing

    public void draw(Canvas c, State s) {
        paint.setShader(new LinearGradient(0, 0, 0, WASH_H, WASH_TOP, WASH_BOTTOM,
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, WASH_H, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(WASH_BOTTOM);
        c.drawRect(0, WASH_H, W, NAV_TOP, paint);
        // the grey the reference leaves under the service card, and the section that follows
        paint.setColor(0xfff5f7fa);
        c.drawRect(0, 2450f, W, NAV_TOP, paint);

        drawHeader(c, s);
        drawEntries(c);
        drawFlightCard(c, s);
        drawServiceCard(c);
        drawPlaySection(c);
        drawNav(c);
    }

    private void drawHeader(Canvas c, State s) {
        // the back arrow, the address with its caret and the weather under it
        stroke(INK, 6f);
        path.reset();
        path.moveTo(81f, 183f);
        path.lineTo(47f, 201f);
        path.lineTo(81f, 219f);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        String address = ellipsize(s.address, ADDR_X, ADDR_SIZE, true, 230f);
        text(c, address, ADDR_X, ADDR_BASE, ADDR_SIZE, INK, true);
        float end = ADDR_X + textWidth(address, ADDR_SIZE, true);
        stroke(INK, 5f);
        path.reset();
        path.moveTo(end + 16f, ADDR_BASE - 34f);
        path.lineTo(end + 34f, ADDR_BASE - 16f);
        path.lineTo(end + 52f, ADDR_BASE - 34f);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        text(c, "阴 21℃", WEATHER_X, WEATHER_BASE, WEATHER_SIZE, GREY, false);

        // the search pill, the magnifier inside it, the placeholder and 搜索
        round(c, PILL, PILL_RADIUS, WHITE);
        stroke(0xff9aa3b0, 5f);
        c.drawCircle(452f, 208f, 18f, paint);
        line(c, 464f, 222f, 478f, 236f, 0xff9aa3b0, 5f);
        paint.setStyle(Paint.Style.FILL);
        text(c, "领8元打车券", HINT_X, HINT_BASE, HINT_SIZE, 0xff3a4353, false);
        line(c, SEARCH_DIVIDER_X, 190f, SEARCH_DIVIDER_X, 236f, 0xffd9dfe8, 3f);
        text(c, "搜索", SEARCH_X, HINT_BASE, SEARCH_SIZE, INK, false);
        drawArt(c, "mileage");
    }

    private void drawEntries(Canvas c) {
        for (int i = 0; i < ENTRY_CX.length; i++) {
            float cx = ENTRY_CX[i];
            int fill = i == 1 ? TILE_SELECTED : TILE;
            if (i != 1) {
                round(c, new RectF(cx - ENTRY_TILE / 2f, ENTRY_TILE_CY - ENTRY_TILE / 2f,
                        cx + ENTRY_TILE / 2f, ENTRY_TILE_CY + ENTRY_TILE / 2f), 40f, fill);
            }
            drawArt(c, ENTRY_ART[i]);
            centerText(c, ENTRY_NAMES[i], cx, ENTRY_LABEL_BASE, ENTRY_LABEL_SIZE,
                    i == 1 ? BLUE : INK, true);
        }
    }

    private void drawFlightCard(Canvas c, State s) {
        round(c, CARD, CARD_RADIUS, WHITE);
        round(c, TAB_STRIP, 40f, TAB_STRIP_FILL);
        round(c, TAB_PILL, 40f, WHITE);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            centerText(c, TAB_NAMES[i], TAB_CX[i], TAB_BASE, TAB_SIZE, INK, i == 0);
        }

        // the blue mark the reference tucks behind 「比价」, then the line itself on top of it
        drawArt(c, "compareMark");
        text(c, COMPARE, COMPARE_X, COMPARE_BASE, COMPARE_SIZE, INK, true);
        text(c, COMPARE_SUB, COMPARE_SUB_X, COMPARE_BASE, COMPARE_SUB_SIZE, GREY, false);
        drawArt(c, "platforms");

        text(c, s.from, CITY_LEFT_X, ROUTE_BASE, CITY_SIZE, INK, true);
        text(c, s.to, CITY_RIGHT_X, ROUTE_BASE, CITY_SIZE, INK, true);
        drawSwap(c);

        line(c, 60f, RULE_Y, 1220f, RULE_Y, RULE, 3f);
        text(c, today(), DATE_X, DATE_BASE, DATE_SIZE, INK, true);
        float dateEnd = DATE_X + textWidth(today(), DATE_SIZE, true);
        text(c, TODAY, dateEnd + 24f, DATE_BASE, TODAY_SIZE, INK, false);

        text(c, CABIN, CABIN_X, CABIN_BASE, CABIN_SIZE, INK, false);
        float cabinEnd = CABIN_X + textWidth(CABIN, CABIN_SIZE, false);
        stroke(0xff9aa3b0, 4.5f);
        path.reset();
        path.moveTo(cabinEnd + 22f, CABIN_BASE - 34f);
        path.lineTo(cabinEnd + 40f, CABIN_BASE - 16f);
        path.lineTo(cabinEnd + 58f, CABIN_BASE - 34f);
        c.drawPath(path, paint);
        for (int i = 0; i < CHECK_CX.length; i++) {
            stroke(0xffc9ced8, 4f);
            c.drawCircle(CHECK_CX[i], CHECK_CY, CHECK_R, paint);
            paint.setStyle(Paint.Style.FILL);
            text(c, CHECK_NAMES[i], CHECK_CX[i] + 48f, CHECK_CY + 4f, 44f, INK, false);
            text(c, CHECK_SUBS[i], CHECK_CX[i] + 48f, CHECK_CY + 50f, 36f, GREY, false);
        }

        // the coupon strip, its badge, the amount, 周周领 and the link
        round(c, COUPON, 20f, COUPON_FILL);
        drawArt(c, "couponBadge");
        text(c, COUPON_TEXT, 183f, COUPON_BASE, COUPON_SIZE, INK, true);
        float couponEnd = 183f + textWidth(COUPON_TEXT, COUPON_SIZE, true);
        text(c, COUPON_TAG, couponEnd + 20f, COUPON_BASE, COUPON_SIZE, COUPON_RED, true);
        text(c, COUPON_LINK, COUPON_LINK_X, COUPON_BASE, COUPON_SIZE, COUPON_RED, true);
        float linkEnd = COUPON_LINK_X + textWidth(COUPON_LINK, COUPON_SIZE, true);
        stroke(COUPON_RED, 4.5f);
        path.reset();
        path.moveTo(linkEnd + 16f, COUPON_BASE - 32f);
        path.lineTo(linkEnd + 34f, COUPON_BASE - 14f);
        path.lineTo(linkEnd + 52f, COUPON_BASE - 32f);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setShader(new LinearGradient(QUERY.left, 0, QUERY.right, 0, QUERY_LEFT, QUERY_RIGHT,
                Shader.TileMode.CLAMP));
        c.drawRoundRect(QUERY, QUERY.height() / 2f, QUERY.height() / 2f, paint);
        paint.setShader(null);
        centerText(c, QUERY_TEXT, (QUERY.left + QUERY.right) / 2f,
                QUERY.centerY() + QUERY_TEXT_SIZE * 0.36f, QUERY_TEXT_SIZE, WHITE, true);

        for (int i = 0; i < FOOT_NAMES.length; i++) {
            drawArt(c, FOOT_ART[i]);
            text(c, FOOT_NAMES[i], FOOT_TEXT_X[i], FOOT_BASE, FOOT_SIZE, INK, false);
        }
    }

    /** The two arrows in a ring that swap the two cities. */
    private void drawSwap(Canvas c) {
        stroke(0xffc2c8d2, 4f);
        c.drawCircle(SWAP_CX, SWAP_CY, SWAP_R, paint);
        paint.setStyle(Paint.Style.FILL);
        triangle(c, SWAP_CX + 16f, SWAP_CY - 18f, SWAP_CX - 4f, SWAP_CY - 18f, SWAP_CX - 4f, SWAP_CY - 32f);
        triangle(c, SWAP_CX - 16f, SWAP_CY + 18f, SWAP_CX + 4f, SWAP_CY + 18f, SWAP_CX + 4f, SWAP_CY + 32f);
        line(c, SWAP_CX - 14f, SWAP_CY - 18f, SWAP_CX + 8f, SWAP_CY - 18f, 0xffc2c8d2, 5f);
        line(c, SWAP_CX - 8f, SWAP_CY + 18f, SWAP_CX + 14f, SWAP_CY + 18f, 0xffc2c8d2, 5f);
    }

    private void triangle(Canvas c, float x1, float y1, float x2, float y2, float x3, float y3) {
        paint.setColor(0xffc2c8d2);
        path.reset();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.close();
        c.drawPath(path, paint);
    }

    private void drawServiceCard(Canvas c) {
        round(c, CARD2, CARD_RADIUS, WHITE);
        for (int i = 0; i < GRID_ART.length; i++) {
            int row = i / 5;
            int col = i % 5;
            float cx = ENTRY_CX[col];
            float cy = GRID_TILE_CY[row];
            round(c, new RectF(cx - ENTRY_TILE / 2f, cy - ENTRY_TILE / 2f,
                    cx + ENTRY_TILE / 2f, cy + ENTRY_TILE / 2f), 40f,
                    "gridCoupon".equals(GRID_ART[i]) ? TILE_WARM : TILE);
            drawArt(c, GRID_ART[i]);
            centerText(c, GRID_NAMES[i], cx, GRID_LABEL_BASE[row], GRID_LABEL_SIZE, INK, false);
        }
    }

    private void drawPlaySection(Canvas c) {
        round(c, PLAY, 24f, WHITE);
        // the reference's own gradient at the section's top left
        paint.setShader(new LinearGradient(PLAY.left, PLAY.top, PLAY.left + 420f, PLAY.top + 180f,
                0x33f8a2c0, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(PLAY.left, PLAY.top, PLAY.left + 420f, PLAY.top + 180f),
                24f, 24f, paint);
        paint.setShader(null);
        text(c, PLAY_TITLE, PLAY_TITLE_X, PLAY_TITLE_BASE, PLAY_TITLE_SIZE, INK, true);
        text(c, PLAY_SUB, PLAY_SUB_X, PLAY_TITLE_BASE, PLAY_SUB_SIZE, INK, true);
    }

    private void drawNav(Canvas c) {
        paint.setColor(WHITE);
        c.drawRect(0, NAV_TOP, W, H, paint);
        drawArt(c, "navAvatar");
        for (int i = 0; i < NAV_CX.length; i++) {
            if (NAV_ART[i] != null) drawArt(c, NAV_ART[i]);
            centerText(c, NAV_NAMES[i], NAV_CX[i], NAV_LABEL_BASE, NAV_LABEL_SIZE,
                    i == 0 ? NAV_ON : NAV_OFF, false);
        }
    }

    // ------------------------------------------------------------------ hits

    /** One token for the tap at (x, y) in design space; null where the page does not answer. */
    public String hitTest(State s, float x, float y) {
        if (y < 150f && x < 130f) return "back";
        // the header's address is the user's to change, like the two cities further down
        if (y >= 150f && y <= 280f && x >= 120f && x <= 340f) return "address";
        if (y > 150f && y < 276f && x > 1180f) return "mileage";
        if (PILL.contains(x, y)) return "search";
        if (y >= 300f && y <= 560f) {
            for (int i = 0; i < ENTRY_CX.length; i++) {
                if (Math.abs(x - ENTRY_CX[i]) < ENTRY_TILE / 2f + 20f) return "entry:" + i;
            }
        }
        if (y >= 630f && y <= 720f) {
            for (int i = 0; i < TAB_CX.length; i++) {
                if (Math.abs(x - TAB_CX[i]) < 120f) return "tab:" + i;
            }
        }
        if (y >= 900f && y <= 1000f) {
            if (Math.abs(x - SWAP_CX) < 52f) return "swap";
            if (x < 300f) return "city:0";
            if (x > 1000f) return "city:1";
        }
        if (y >= 1290f && y <= 1370f) {
            if (x < 320f) return "cabin";
            for (int i = 0; i < CHECK_CX.length; i++) {
                if (Math.abs(x - CHECK_CX[i]) < 160f) return "check:" + i;
            }
        }
        if (COUPON.contains(x, y)) return x > 990f ? "couponLink" : "coupon";
        if (QUERY.contains(x, y)) return "query";
        if (y >= 1780f && y <= 1850f) {
            for (int i = 0; i < FOOT_TEXT_X.length; i++) {
                if (x > FOOT_TEXT_X[i] - 90f && x < FOOT_TEXT_X[i] + 200f) return "foot:" + i;
            }
        }
        if (CARD2.contains(x, y)) {
            int row = y < 2210f ? 0 : 1;
            for (int i = 0; i < 5; i++) {
                if (Math.abs(x - ENTRY_CX[i]) < 122f) return "grid:" + (row * 5 + i);
            }
        }
        if (y >= NAV_TOP) {
            for (int i = 0; i < NAV_CX.length; i++) {
                if (Math.abs(x - NAV_CX[i]) < 128f) return "nav:" + i;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ primitives

    private void drawArt(Canvas c, String name) {
        Bitmap bitmap = art.get(name);
        RectF box = boxes.get(name);
        if (bitmap == null || box == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        c.drawBitmap(bitmap, null, box, paint);
    }

    private float round(Canvas c, RectF box, float radius, int colour) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colour);
        c.drawRoundRect(box, radius, radius, paint);
        return radius;
    }

    private void stroke(int colour, float width) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(colour);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2, int colour, float width) {
        stroke(colour, width);
        c.drawLine(x1, y1, x2, y2, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private float textWidth(String value, float size, boolean bold) {
        textPaint.setTextSize(size);
        textPaint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return textPaint.measureText(value) * TEXT_SQUEEZE;
    }

    /** Shortens a hand-edited value to its column, the way the reference's own captions read. */
    private String ellipsize(String value, float x, float size, boolean bold, float limit) {
        if (value == null) return "";
        if (textWidth(value, size, bold) <= limit) return value;
        String cut = value;
        while (cut.length() > 1 && textWidth(cut + "···", size, bold) > limit) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "···";
    }

    private void text(Canvas c, String value, float x, float baseline, float size, int colour,
                      boolean bold) {
        textPaint.setTextSize(size);
        textPaint.setColor(colour);
        textPaint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        textPaint.setTextAlign(Paint.Align.LEFT);
        c.save();
        c.scale(TEXT_SQUEEZE, 1f, x, 0f);
        c.drawText(value, x, baseline, textPaint);
        c.restore();
    }

    private void centerText(Canvas c, String value, float cx, float baseline, float size, int colour,
                            boolean bold) {
        textPaint.setTextSize(size);
        textPaint.setColor(colour);
        textPaint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        textPaint.setTextAlign(Paint.Align.CENTER);
        c.save();
        c.scale(TEXT_SQUEEZE, 1f, cx, 0f);
        c.drawText(value, cx, baseline, textPaint);
        c.restore();
    }

    /** What the page remembers between draws: the address and the two cities the user may edit. */
    public static final class State {
        public String address = "恩施土家族苗族自治州";
        public String from = "北京";
        public String to = "杭州";
    }

    /**
     * The date the query runs on: the device's own today, so the card follows the day it is used
     * rather than the day the reference was captured, and stays 今天 with it.
     */
    public static String today() {
        Calendar now = Calendar.getInstance();
        return (now.get(Calendar.MONTH) + 1) + "月" + now.get(Calendar.DAY_OF_MONTH) + "日";
    }
}
