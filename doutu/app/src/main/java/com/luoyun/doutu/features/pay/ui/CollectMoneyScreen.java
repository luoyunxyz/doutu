package com.luoyun.doutu.features.pay.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.luoyun.doutu.R;

/**
 * Native rendition of the “收钱” (collect money) page.
 *
 * <p>Everything is redrawn from primitives at the 1280x2816 canvas of the reference screenshot:
 * the warm page gradient, the four white cards, the collection code and its shortcut rows.  The
 * code symbol itself is generated locally so no real payment credential is ever involved — this
 * is a mock, not a wallet.
 */
public final class CollectMoneyScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;

    /** Mutable presentation state owned by the hosting activity. */
    public static final class State {
        /** The name printed over the collection code; editable from the page. */
        public String name = "落陨(**玮)";
        /** A code the user dropped in from the gallery; null draws the generated one. */
        public Bitmap qr;
        /** How far the sheet has been pulled up, in canvas pixels. */
        public float scroll;
    }

    // The page background is a single diagonal ramp that flattens out towards the bottom right.
    private static final int[] BG_COLORS = {
            0xfff4b45a, 0xfff3ae55, 0xfff2a64d, 0xfff09f47, 0xfff09c45,
            0xfff09a44, 0xffef9a45, 0xffef9841, 0xffef953e};
    private static final float[] BG_STOPS = {
            0f, 0.20f, 0.30f, 0.40f, 0.50f, 0.60f, 0.75f, 0.90f, 1f};

    private static final int CARD = 0xffffffff;
    private static final int INK = 0xff202020;
    private static final int GLYPH_INK = 0xff2b2b2b;
    private static final int MUTED = 0xff909090;
    private static final int PROTECT_INK = 0xff5c5c5c;
    private static final int CHEVRON_LIGHT = 0xffcacaca;
    private static final int CHEVRON_MID = 0xff999999;
    private static final int BLUE = 0xff3a78f0;
    private static final int RED_DOT = 0xffeb5232;
    private static final int CREAM = 0xfffdf7f1;
    private static final int DIVIDER = 0xffececec;
    private static final int CODE_INK = 0xff000000;
    private static final int UP_INK = 0xff26292c;
    private static final int UP_SUB_INK = 0xff4c4e52;
    private static final int UP_BUTTON_TOP = 0xff3d90f4;
    private static final int UP_BUTTON_BOTTOM = 0xff3379ee;

    // ------------------------------------------------------------------ geometry

    private static final float CARD_L = 54f;
    private static final float CARD_R = 1226f;
    private static final float CARD_RADIUS = 46f;

    private static final float CARD1_T = 320f;
    private static final float CARD1_B = 1999f;
    private static final float CARD2_T = 2030f;
    private static final float CARD2_B = 2198f;
    private static final float CARD3_T = 2230f;
    private static final float CARD3_B = 2643f;
    /** The sheet scrolls under the fixed header; the home-indicator strip never scrolls. */
    private static final float VIEWPORT_BOTTOM = 2752f;
    private static final float CONTENT_H = 3060f;

    /** The “升级你的收钱码” card that closes the sheet. */
    private static final float UP_T = 2686f;
    private static final float UP_B = 2999f;
    private static final float UP_ART_L = 54f;
    private static final float UP_ART_T = 2690f;
    private static final float UP_TITLE_X = 416f;
    private static final float UP_TITLE_BASE = 2813f;
    private static final float UP_TITLE_SIZE = 61f;
    private static final float UP_TITLE_W = 376f;
    private static final float UP_SUB_X = 415f;
    private static final float UP_SUB_BASE = 2898f;
    private static final float UP_SUB_SIZE = 52f;
    private static final float UP_SUB_W = 399f;
    private static final float UP_BTN_L = 940f;
    private static final float UP_BTN_T = 2800f;
    private static final float UP_BTN_R = 1192f;
    private static final float UP_BTN_B = 2890f;
    private static final float UP_BTN_TEXT_BASE = 2863f;
    private static final float UP_BTN_TEXT_SIZE = 40f;

    private static final float BACK_TIP_X = 70f;
    private static final float BACK_CY = 220f;
    private static final float BACK_HALF_W = 29f;
    private static final float BACK_HALF_H = 26f;
    private static final float TITLE_X = 164f;
    private static final float TITLE_BASE = 240f;
    private static final float TITLE_SIZE = 63f;
    private static final float[] DOT_CX = {1171f, 1195f, 1218f};
    private static final float DOT_CY = 220f;
    private static final float DOT_R = 8f;

    private static final float HEAD_ICON_L = 96f;
    private static final float HEAD_ICON_T = 368f;
    private static final float HEAD_TEXT_X = 218f;
    private static final float HEAD_TEXT_BASE = 425f;
    private static final float HEAD_TEXT_SIZE = 60f;
    private static final float COLLAPSE_CX = 1152f;
    private static final float COLLAPSE_CY = 405f;
    private static final float COLLAPSE_HALF_W = 26f;
    private static final float COLLAPSE_HALF_H = 15f;

    private static final float DIVIDER_L = 100f;
    private static final float DIVIDER_R = 1178f;
    private static final float DIVIDER_W = 2f;

    private static final float SHIELD_L = 462f;
    private static final float SHIELD_T = 534f;
    private static final float PROTECT_X = 531f;
    private static final float PROTECT_BASE = 577f;
    private static final float PROTECT_SIZE = 46f;

    /** Gap the reference leaves between the payee name and the 修改 link. */
    private static final float NAME_GAP = 16f;
    private static final float NAME_BASE = 677f;
    private static final float NAME_SIZE = 60f;
    private static final float EDIT_BASE = 674f;
    private static final float EDIT_SIZE = 56f;

    private static final float QR_L = 323f;
    private static final float QR_T = 759f;
    private static final float QR_SIZE = 634f;
    private static final int QR_MODULES = 37;
    private static final float QR_LOGO_L = 585f;
    private static final float QR_LOGO_T = 1021f;
    private static final float QR_LOGO_SIZE = 110f;

    private static final float ACT_BASE = 1523f;
    private static final float ACT_SIZE = 53f;
    private static final float[] ACT_CX = {254f, 639f, 1025.5f};
    private static final String[] ACT_LABELS = {"设置金额", "保存图片", "更多设置"};
    private static final float[] ACT_SEP_X = {446.5f, 832.5f};
    private static final float ACT_SEP_T = 1479f;
    private static final float ACT_SEP_B = 1531f;

    private static final float RECEIPT_L = 100f;
    private static final float RECEIPT_T = 1729f;
    private static final float ROW_TEXT_X = 216f;
    private static final float RECEIPT_BASE = 1785f;
    private static final float ROW_TEXT_SIZE = 60f;
    private static final float RECEIPT_CHEV_X = 1169f;
    private static final float RECEIPT_CHEV_CY = 1765f;
    private static final float RECEIPT_CHEV_HALF_W = 13f;
    private static final float RECEIPT_CHEV_HALF_H = 24f;

    private static final float CREAM_T = 1858f;
    private static final float ASSIST_ICON_L = 96f;
    private static final float ASSIST_ICON_T = 1892f;
    private static final float ASSIST_BASE = 1947f;
    private static final float ASSIST_SIZE = 50f;
    private static final float PILL1_L = 973f;
    private static final float PILL1_T = 1883f;
    private static final float PILL1_R = 1179f;
    private static final float PILL1_B = 1972f;
    private static final float PILL1_TEXT_BASE = 1943f;
    private static final float PILL1_TEXT_SIZE = 43f;

    private static final float SHOP_L = 92f;
    private static final float SHOP_T = 2076f;
    private static final float BIZ_BASE = 2134f;
    private static final float SUPPORT_X = 1063f;
    private static final float SUPPORT_BASE = 2131f;
    private static final float SUPPORT_SIZE = 53f;
    private static final float RED_CX = 1094.5f;
    private static final float RED_CY = 2113.5f;
    private static final float RED_R = 18f;
    private static final float CHEV_DOWN_CX = 1153f;
    private static final float CHEV_DOWN_CY = 2113f;
    private static final float CHEV_DOWN_HALF_W = 25f;
    private static final float CHEV_DOWN_HALF_H = 14f;

    private static final float STORE_L = 96f;
    private static final float STORE_T = 2275f;
    private static final float SERVICE_BASE = 2336f;
    private static final float SERVICE_CHEV_X = 1166f;
    private static final float SERVICE_CHEV_CY = 2313f;

    private static final float DIVIDER3_Y = 2399f;
    private static final float PROMO_L = 92f;
    private static final float PROMO_T = 2449f;
    private static final float PROMO_TEXT_X = 280f;
    private static final float PROMO_TITLE_BASE = 2509f;
    private static final float PROMO_SUB_BASE = 2584f;
    private static final float PILL2_L = 933f;
    private static final float PILL2_T = 2478f;
    private static final float PILL2_R = 1177f;
    private static final float PILL2_B = 2568f;
    private static final float PILL2_TEXT_BASE = 2539f;
    private static final float PILL2_TEXT_SIZE = 46f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final java.util.Map<String, Bitmap> art = new java.util.LinkedHashMap<>();
    private LinearGradient bg;
    private LinearGradient upgradeButton;
    private boolean[] qrField;
    private long seed = 0x5eed1234abcdL;

    public void load(Resources res) {
        art.clear();
        art.put("bag", BitmapFactory.decodeResource(res, R.drawable.pay_collect_bag));
        art.put("receipt", BitmapFactory.decodeResource(res, R.drawable.pay_collect_receipt));
        art.put("shop", BitmapFactory.decodeResource(res, R.drawable.pay_collect_shop));
        art.put("store", BitmapFactory.decodeResource(res, R.drawable.pay_collect_store));
        art.put("shield", BitmapFactory.decodeResource(res, R.drawable.pay_collect_shield));
        art.put("promo", BitmapFactory.decodeResource(res, R.drawable.pay_collect_promo));
        art.put("qrlogo", BitmapFactory.decodeResource(res, R.drawable.pay_collect_qrlogo));
        art.put("upgrade", BitmapFactory.decodeResource(res, R.drawable.pay_collect_upgrade));
        // Each visit paints a different, meaningless module field.
        seed = System.nanoTime() | 1L;
    }

    // ------------------------------------------------------------------ drawing

    /** Highest scroll position; beyond the last card only background follows. */
    public float maxScroll() {
        return Math.max(0f, CONTENT_H - VIEWPORT_BOTTOM);
    }

    public void draw(Canvas c, State s) {
        if (bg == null) {
            bg = new LinearGradient(0f, 0f, W, H, BG_COLORS, BG_STOPS, Shader.TileMode.CLAMP);
        }
        paint.setShader(bg);
        c.drawRect(0f, 0f, W, H, paint);
        paint.setShader(null);

        // The sheet: cards on the page background, clipped to the scroll viewport.
        c.save();
        c.clipRect(0f, 0f, W, VIEWPORT_BOTTOM);
        c.translate(0f, -s.scroll);
        drawContent(c, s);
        c.restore();

        // Fixed chrome: the gradient header and the home-indicator strip.
        paint.setShader(bg);
        c.drawRect(0f, 0f, W, CARD1_T, paint);
        c.drawRect(0f, VIEWPORT_BOTTOM, W, H, paint);
        paint.setShader(null);
        drawTopBar(c);
    }

    private void drawContent(Canvas c, State s) {
        drawCard(c, CARD1_T, CARD1_B);
        drawCard(c, CARD2_T, CARD2_B);
        drawCard(c, CARD3_T, CARD3_B);
        drawCard(c, UP_T, UP_B);

        drawCard1(c, s);
        drawCard2(c);
        drawCard3(c);
        drawUpgradeCard(c);
    }

    private void drawCard(Canvas c, float top, float bottom) {
        r.set(CARD_L, top, CARD_R, bottom);
        fill(c, CARD);
        c.drawRoundRect(r, CARD_RADIUS, CARD_RADIUS, paint);
    }

    private void drawTopBar(Canvas c) {
        stroke(c, Color.WHITE, 8f);
        c.drawLine(BACK_TIP_X + BACK_HALF_W, BACK_CY - BACK_HALF_H,
                BACK_TIP_X, BACK_CY, paint);
        c.drawLine(BACK_TIP_X, BACK_CY,
                BACK_TIP_X + BACK_HALF_W, BACK_CY + BACK_HALF_H, paint);
        text(c, "收钱", TITLE_X, TITLE_BASE, TITLE_SIZE, Color.WHITE, false);
        fill(c, Color.WHITE);
        for (float cx : DOT_CX) c.drawCircle(cx, DOT_CY, DOT_R, paint);
    }

    private void drawCard1(Canvas c, State s) {
        drawArt(c, "bag", HEAD_ICON_L, HEAD_ICON_T);
        text(c, "个人收钱", HEAD_TEXT_X, HEAD_TEXT_BASE, HEAD_TEXT_SIZE, INK, false);
        chevronUp(c, COLLAPSE_CX, COLLAPSE_CY, COLLAPSE_HALF_W, COLLAPSE_HALF_H,
                CHEVRON_MID, 6f);
        divider(c, 490f);

        drawArt(c, "shield", SHIELD_L, SHIELD_T);
        text(c, "你敢收 我敢赔", PROTECT_X, PROTECT_BASE, PROTECT_SIZE, PROTECT_INK, false);

        // The name and the 修改 link travel together and stay centred on the card.
        float nameWidth = measure(s.name, NAME_SIZE, false);
        float editWidth = measure("修改", EDIT_SIZE, false);
        float lineWidth = nameWidth + NAME_GAP + editWidth;
        float lineLeft = (CARD_L + CARD_R) / 2f - lineWidth / 2f;
        text(c, s.name, lineLeft, NAME_BASE, NAME_SIZE, INK, false);
        text(c, "修改", lineLeft + nameWidth + NAME_GAP, EDIT_BASE, EDIT_SIZE, BLUE, false);

        drawCode(c, s);

        for (int i = 0; i < ACT_LABELS.length; i++) {
            centerText(c, ACT_LABELS[i], ACT_CX[i], ACT_BASE, ACT_SIZE, MUTED, false);
        }
        for (float x : ACT_SEP_X) {
            line(c, x, ACT_SEP_T, x, ACT_SEP_B, 0xffdcdcdc, 2f);
        }
        divider(c, 1679f);

        drawArt(c, "receipt", RECEIPT_L, RECEIPT_T);
        text(c, "收款记录", ROW_TEXT_X, RECEIPT_BASE, ROW_TEXT_SIZE, INK, false);
        chevronRight(c, RECEIPT_CHEV_X, RECEIPT_CHEV_CY, RECEIPT_CHEV_HALF_W,
                RECEIPT_CHEV_HALF_H, CHEVRON_LIGHT, 5f);

        // The assistant row is tinted, so it is clipped to the card's rounded bottom.
        c.save();
        r.set(CARD_L, CREAM_T, CARD_R, CARD1_B);
        c.clipRect(CARD_L, CREAM_T, CARD_R, CARD1_B);
        fill(c, CREAM);
        c.drawRect(CARD_L, CREAM_T, CARD_R, CARD1_B, paint);
        c.restore();
        drawArt(c, "bag", ASSIST_ICON_L, ASSIST_ICON_T);
        text(c, "开启收款助手，首页便捷查账", ROW_TEXT_X, ASSIST_BASE, ASSIST_SIZE, INK, false);
        pill(c, PILL1_L, PILL1_T, PILL1_R, PILL1_B, "去开启", PILL1_TEXT_BASE,
                PILL1_TEXT_SIZE);
    }

    private void drawCard2(Canvas c) {
        drawArt(c, "shop", SHOP_L, SHOP_T);
        text(c, "经营收钱", ROW_TEXT_X, BIZ_BASE, ROW_TEXT_SIZE, INK, false);
        textRight(c, "支持信用卡/花呗", SUPPORT_X, SUPPORT_BASE, SUPPORT_SIZE, MUTED, false);
        fill(c, RED_DOT);
        c.drawCircle(RED_CX, RED_CY, RED_R, paint);
        chevronDown(c, CHEV_DOWN_CX, CHEV_DOWN_CY, CHEV_DOWN_HALF_W, CHEV_DOWN_HALF_H,
                CHEVRON_MID, 5f);
    }

    private void drawCard3(Canvas c) {
        drawArt(c, "store", STORE_L, STORE_T);
        text(c, "商家服务", ROW_TEXT_X, SERVICE_BASE, ROW_TEXT_SIZE, INK, false);
        chevronRight(c, SERVICE_CHEV_X, SERVICE_CHEV_CY, 13f, 23f, CHEVRON_LIGHT, 5f);
        divider(c, DIVIDER3_Y);

        drawArt(c, "promo", PROMO_L, PROMO_T);
        text(c, "充值锁客提升复购", PROMO_TEXT_X, PROMO_TITLE_BASE, ROW_TEXT_SIZE, INK, false);
        text(c, "超50万商家使用中", PROMO_TEXT_X, PROMO_SUB_BASE, SUPPORT_SIZE, MUTED, false);
        pill(c, PILL2_L, PILL2_T, PILL2_R, PILL2_B, "去开通", PILL2_TEXT_BASE,
                PILL2_TEXT_SIZE);
    }

    /** Closes the sheet with the 升级你的收钱码 promotion. */
    private void drawUpgradeCard(Canvas c) {
        // Two QR standees, a 奖励 bubble and a seal whose type curls round it: artwork only.
        drawArt(c, "upgrade", UP_ART_L, UP_ART_T);
        textFit(c, "升级你的收钱码", UP_TITLE_X, UP_TITLE_BASE, UP_TITLE_SIZE, UP_TITLE_W,
                UP_INK, true);
        textFit(c, "首碰收款奖2元红包", UP_SUB_X, UP_SUB_BASE, UP_SUB_SIZE, UP_SUB_W,
                UP_SUB_INK, false);

        if (upgradeButton == null) {
            upgradeButton = new LinearGradient(0f, UP_BTN_T, 0f, UP_BTN_B, UP_BUTTON_TOP,
                    UP_BUTTON_BOTTOM, Shader.TileMode.CLAMP);
        }
        r.set(UP_BTN_L, UP_BTN_T, UP_BTN_R, UP_BTN_B);
        paint.setShader(upgradeButton);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        c.drawRoundRect(r, (UP_BTN_B - UP_BTN_T) / 2f, (UP_BTN_B - UP_BTN_T) / 2f, paint);
        paint.setShader(null);
        centerText(c, "立即换", (UP_BTN_L + UP_BTN_R) / 2f, UP_BTN_TEXT_BASE,
                UP_BTN_TEXT_SIZE, Color.WHITE, false);
    }

    private void drawCode(Canvas c, State s) {
        if (s.qr != null) {
            r.set(QR_L, QR_T, QR_L + QR_SIZE, QR_T + QR_SIZE);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            c.drawBitmap(s.qr, null, r, paint);
            return;
        }
        ensureQr();
        float module = QR_SIZE / QR_MODULES;
        fill(c, CODE_INK);
        // Modules are axis aligned; anti-aliasing would only add hairline seams.
        paint.setAntiAlias(false);
        for (int y = 0; y < QR_MODULES; y++) {
            for (int x = 0; x < QR_MODULES; x++) {
                if (!qrField[y * QR_MODULES + x]) continue;
                float left = QR_L + x * module;
                float top = QR_T + y * module;
                c.drawRect(left, top, left + module, top + module, paint);
            }
        }
        paint.setAntiAlias(true);
        drawArt(c, "qrlogo", QR_LOGO_L, QR_LOGO_T);
    }

    // ------------------------------------------------------------------ asset building

    /** A QR-looking symbol: finder, timing and alignment patterns over a module field. */
    private void ensureQr() {
        if (qrField != null) return;
        int n = QR_MODULES;
        boolean[] m = new boolean[n * n];
        long state = seed ^ 0x9e3779b97f4a7c15L;
        for (int i = 0; i < m.length; i++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            m[i] = ((state >>> 41) & 1L) == 1L;
        }
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                m[y * n + x] = false;
                m[y * n + (n - 8 + x)] = false;
                m[(n - 8 + y) * n + x] = false;
            }
        }
        finder(m, n, 0, 0);
        finder(m, n, n - 7, 0);
        finder(m, n, 0, n - 7);
        for (int i = 8; i < n - 8; i++) {
            m[6 * n + i] = i % 2 == 0;
            m[i * n + 6] = i % 2 == 0;
        }
        align(m, n, n - 9, n - 9);
        qrField = m;
    }

    private static void finder(boolean[] m, int n, int ox, int oy) {
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 7; x++) {
                boolean edge = x == 0 || x == 6 || y == 0 || y == 6;
                boolean core = x >= 2 && x <= 4 && y >= 2 && y <= 4;
                m[(oy + y) * n + ox + x] = edge || core;
            }
        }
    }

    private static void align(boolean[] m, int n, int cx, int cy) {
        for (int y = -2; y <= 2; y++) {
            for (int x = -2; x <= 2; x++) {
                boolean ring = Math.max(Math.abs(x), Math.abs(y)) != 1;
                m[(cy + y) * n + cx + x] = ring;
            }
        }
    }

    // ------------------------------------------------------------------ hit test

    /**
     * Returns an action token for the tap at (x, y), given in screen design coordinates; the
     * sheet is scrolled, so the lookup happens in content space.
     */
    public String hitTest(State s, float x, float y) {
        if (y < 320f) {
            if (x < 150f) return "back";
            if (x > 1100f) return "more";
            return "noop";
        }
        y += s.scroll;
        if (x > QR_L - 10f && x < QR_L + QR_SIZE + 10f
                && y > QR_T - 10f && y < QR_T + QR_SIZE + 10f) {
            return "qr";
        }
        // The name and the 修改 link are one hit target; a long name widens the pair.
        if (y > 600f && y < 705f) return "name";
        if (y > 1440f && y < 1570f) {
            if (x < ACT_SEP_X[0]) return "amount";
            if (x < ACT_SEP_X[1]) return "save";
            return "more2";
        }
        if (y > 1679f && y < CREAM_T) return "records";
        if (x > PILL1_L && x < PILL1_R && y > PILL1_T - 20f && y < PILL1_B + 20f) {
            return "assistant";
        }
        if (y > CARD2_T && y < CARD2_B) return "business";
        if (y > DIVIDER3_Y && y < CARD3_B) {
            if (x > PILL2_L && x < PILL2_R && y > PILL2_T - 20f && y < PILL2_B + 20f) {
                return "promo";
            }
            return "noop";
        }
        if (y > CARD3_T && y < DIVIDER3_Y) return "service";
        if (y > UP_T && y < UP_B) {
            if (x > UP_BTN_L && x < UP_BTN_R && y > UP_BTN_T && y < UP_BTN_B) {
                return "upgrade";
            }
            return "noop";
        }
        return "noop";
    }

    // ------------------------------------------------------------------ primitives

    private void divider(Canvas c, float y) {
        line(c, DIVIDER_L, y, DIVIDER_R, y, DIVIDER, DIVIDER_W);
    }

    private void pill(Canvas c, float l, float t, float rr, float b, String label,
                      float baseline, float size) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(BLUE);
        r.set(l, t, rr, b);
        c.drawRoundRect(r, (b - t) / 2f, (b - t) / 2f, paint);
        paint.setStyle(Paint.Style.FILL);
        centerText(c, label, (l + rr) / 2f, baseline, size, BLUE, false);
    }

    private void drawArt(Canvas c, String name, float left, float top) {
        Bitmap b = art.get(name);
        if (b == null) return;
        int color = paint.getColor();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        // Bitmaps are modulated by the paint's alpha, so it has to be opaque here.
        paint.setColor(Color.WHITE);
        r.set(left, top, left + b.getWidth(), top + b.getHeight());
        c.drawBitmap(b, null, r, paint);
        paint.setColor(color);
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

    private float measure(String value, float size, boolean bold) {
        paint.setShader(null);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        return paint.measureText(value);
    }

    /**
     * Draws a run so it occupies exactly {@code width}: the promotion card sets its headline in
     * a tighter face than the platform default, so the glyphs are squeezed to the same measure
     * instead of being re-set smaller and coming out the wrong height.
     */
    private void textFit(Canvas c, String value, float x, float baseline, float size,
                         float width, int color, boolean bold) {
        float natural = measure(value, size, bold);
        if (natural <= 0f) return;
        paint.setColor(color);
        c.save();
        c.translate(x, baseline);
        c.scale(width / natural, 1f);
        c.drawText(value, 0f, 0f, paint);
        c.restore();
    }

    private void textRight(Canvas c, String value, float x, float baseline, float size,
                           int color, boolean bold) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.RIGHT);
        c.drawText(value, x, baseline, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void centerText(Canvas c, String value, float cx, float baseline, float size,
                            int color, boolean bold) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(value, cx, baseline, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2, int color, float width) {
        stroke(c, color, width);
        c.drawLine(x1, y1, x2, y2, paint);
    }

    private void chevronRight(Canvas c, float x, float cy, float halfW, float halfH,
                              int color, float width) {
        line(c, x - halfW, cy - halfH, x + halfW, cy, color, width);
        line(c, x + halfW, cy, x - halfW, cy + halfH, color, width);
    }

    private void chevronUp(Canvas c, float cx, float cy, float halfW, float halfH,
                           int color, float width) {
        line(c, cx - halfW, cy + halfH, cx, cy - halfH, color, width);
        line(c, cx, cy - halfH, cx + halfW, cy + halfH, color, width);
    }

    private void chevronDown(Canvas c, float cx, float cy, float halfW, float halfH,
                             int color, float width) {
        line(c, cx - halfW, cy - halfH, cx, cy + halfH, color, width);
        line(c, cx, cy + halfH, cx + halfW, cy - halfH, color, width);
    }

    private void fill(Canvas c, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
    }

    private void stroke(Canvas c, int color, float width) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color);
    }
}
