package com.luoyun.doutu.features.zhi.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.luoyun.doutu.R;
import com.luoyun.doutu.features.zhi.data.LicaiVersion;
import com.luoyun.doutu.features.zhi.data.ProfileStore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Native rendition of the 我的 tab of the 小支主页.
 *
 * <p>The page is rebuilt on the same 1280x2816 reference canvas as the rest of the 支 module, over
 * a 4067 px content height that scrolls beneath the fixed bottom navigation. Only the header's four
 * glyphs, the 完成身份 banner, the eight service cards and the two bottom-bar cells that differ
 * from the 消息 page's bar travel as bitmaps; the page backdrop, the avatar, the name, the phone
 * pill and the bottom bar's shell are drawn here, so the three of them can answer the user.
 */
public final class MeScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;
    public static final float NAV_TOP = 2560f;
    /** Where the reference's bottom bar begins, i.e. the height of everything above it. */
    public static final float CONTENT_H = 4067f;

    /** Mutable presentation state owned by the hosting activity. */
    public static final class State {
        public float scroll;
        /** The 完成身份 strip can be dismissed with its own ×. */
        public boolean banner = true;
    }

    // ------------------------------------------------------------------ palette
    private static final int PAGE_BG = 0xfff2f3f7;
    private static final int BAR_BG = 0xfffafbff;
    private static final int PHONE_PILL = 0xffe3ecf6;
    private static final int PHONE_INK = 0xff2e4162;
    private static final int NAME_INK = 0xff27292d;

    /** Measured against the reference, run by run; see {@code _msg/cmp3.py}. */
    private static final float SX_NAME = 1.020f;
    private static final float SX_PHONE = 1.000f;

    // ------------------------------------------------------------------ geometry
    private static final float PLATE_H = 700f;
    /** Height of the 黑卡 header artwork, carried on to the first card's top edge below it. */
    private static final float BLACK_HEADER_H = 620f;

    private static final RectF BACK_OLD = new RectF(782, 182, 996, 258);
    private static final RectF HEADSET = new RectF(1032, 178, 1114, 262);
    private static final RectF GEAR = new RectF(1148, 172, 1234, 266);
    private static final RectF HEAD_CHEVRON = new RectF(1166, 367, 1202, 422);

    /** The avatar is the 228 px rounded square the reference draws at the top left. */
    public static final RectF AVATAR = new RectF(85, 280, 313, 508);
    public static final float AVATAR_RADIUS = 44f;

    private static final float NAME_X = 363f;
    private static final float NAME_BASE = 366f;
    private static final float NAME_SIZE = 63f;
    private static final RectF PHONE_PILL_RECT = new RectF(362, 412, 584, 482);
    private static final float PHONE_CX = 473f;
    private static final float PHONE_BASE = 459f;
    private static final float PHONE_SIZE = 36f;
    private static final float PHONE_PILL_RADIUS = 35f;

    private static final RectF BANNER = new RectF(56, 554, 1224, 664);
    private static final RectF BANNER_DONE = new RectF(912, 576, 1100, 644);
    private static final RectF BANNER_CLOSE = new RectF(1118, 570, 1196, 650);

    private static final float CARD_LEFT = 28f;
    private static final float CARD_RIGHT = 1250f;
    /** Top and bottom of each service card, in content coordinates. */
    private static final float[][] CARDS = {
            {698, 841}, {878, 1303}, {1341, 1484}, {1521, 2123},
            {2161, 2665}, {2702, 3089}, {3126, 3513}, {3550, 3937}};
    private static final String[] CARD_ART = {
            "card1", "card2", "card3", "card4", "card5", "card6", "card7", "card8"};

    /** Rows of the 资产 card, each a tap target on its own. */
    private static final String[] ASSET_ROWS = {"总资产", "余额", "余额宝", "花呗"};
    private static final float ASSET_TOP = 1540f;
    private static final float ASSET_ROW_H = 153.5f;

    /** The four columns of the icon grids, and each grid's icon row. */
    private static final float[] GRID_CX = {180f, 486f, 792f, 1098f};
    private static final float GRID_COL_W = 306f;
    private static final String[][] GRID_LABELS = {
            {"蚂蚁保", "芝麻信用", "银行卡", "余利宝"},
            {"网商贷", "网商银行", "数字人民币", "我的信用卡"},
            {"医保", "社保", "住房公积金", "就业"},
            {"芝麻企业信用", "政策信息", "办事指南", "企业经营"},
            {"待付款", "进行中/待评价", "退款/售后", "全部订单"}};

    private final Map<String, Bitmap> art = new LinkedHashMap<>();
    private final BlackCardNav blackCardNav = new BlackCardNav();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private Bitmap plate;

    /** Decodes the cut-out bitmaps. Safe to call again after a configuration change. */
    public void load(Resources res) {
        art.clear();
        art.put("plate", BitmapFactory.decodeResource(res, R.drawable.me_bgplate));
        art.put("backOld", BitmapFactory.decodeResource(res, R.drawable.me_backold));
        art.put("headset", BitmapFactory.decodeResource(res, R.drawable.me_headset));
        art.put("gear", BitmapFactory.decodeResource(res, R.drawable.me_gear));
        art.put("headChevron", BitmapFactory.decodeResource(res, R.drawable.me_chevron));
        art.put("avatar", BitmapFactory.decodeResource(res, R.drawable.me_avatar));
        art.put("banner", BitmapFactory.decodeResource(res, R.drawable.me_banner));
        art.put("card1", BitmapFactory.decodeResource(res, R.drawable.me_card1));
        art.put("card2", BitmapFactory.decodeResource(res, R.drawable.me_card2));
        art.put("card3", BitmapFactory.decodeResource(res, R.drawable.me_card3));
        art.put("card4", BitmapFactory.decodeResource(res, R.drawable.me_card4));
        art.put("card5", BitmapFactory.decodeResource(res, R.drawable.me_card5));
        art.put("card6", BitmapFactory.decodeResource(res, R.drawable.me_card6));
        art.put("card7", BitmapFactory.decodeResource(res, R.drawable.me_card7));
        art.put("card8", BitmapFactory.decodeResource(res, R.drawable.me_card8));
        art.put("navMsg", BitmapFactory.decodeResource(res, R.drawable.me_nav_msg));
        art.put("navMe", BitmapFactory.decodeResource(res, R.drawable.me_nav_me));
        art.put("navbar", BitmapFactory.decodeResource(res, R.drawable.msg_navbar));
        art.put("blackBackground", BitmapFactory.decodeResource(res, R.drawable.hk_me_bg));
        blackCardNav.load(res);
        plate = art.get("plate");
    }

    public float maxScroll(State s) {
        return Math.max(0f, CONTENT_H - NAV_TOP);
    }

    // ------------------------------------------------------------------ drawing

    public void draw(Canvas c, State s) {
        boolean blackCard = LicaiVersion.blackCard(LicaiVersion.current());
        // The page keeps its own grey under everything: the 黑卡 skin only repaints the band above
        // the first service card, and a black page showed as a hard field behind every white card.
        c.drawColor(PAGE_BG);
        c.save();
        c.translate(0, -s.scroll);
        if (blackCard) {
            headerPlate(c, "blackBackground", BLACK_HEADER_H, PLATE_H);
        } else if (plate != null) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            r.set(0, 0, W, PLATE_H);
            c.drawBitmap(plate, null, r, paint);
        }
        drawHeader(c);
        if (s.banner) {
            drawArt(c, "banner", BANNER.left, BANNER.top, BANNER.right, BANNER.bottom);
        }
        for (int i = 0; i < CARDS.length; i++) {
            drawArt(c, CARD_ART[i], CARD_LEFT, CARDS[i][0], CARD_RIGHT, CARDS[i][1]);
        }
        c.restore();
        drawNav(c);
    }

    /**
     * Lays the 黑卡 header artwork over the page's top and carries its own last row down to the
     * first card's top, so the band covers the 完成身份 strip whole and the page's grey starts
     * exactly where the cards do.
     */
    private void headerPlate(Canvas c, String name, float artHeight, float bottom) {
        Bitmap bitmap = art.get(name);
        if (bitmap == null) return;
        float height = Math.min(bottom, artHeight);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        // The artwork is carried a couple of pixels past its own height and the tail starts inside
        // it, so the lighter hairline a filtered bitmap's last row leaves behind is covered.
        r.set(0, 0, W, height + 2f);
        c.drawBitmap(bitmap, null, r, paint);
        if (height + 1f >= bottom) return;
        Rect src = new Rect(0, bitmap.getHeight() - 1, bitmap.getWidth(), bitmap.getHeight());
        r.set(0, height + 1f, W, bottom);
        c.drawBitmap(bitmap, src, r, paint);
    }

    private void drawHeader(Canvas c) {
        boolean blackCard = LicaiVersion.blackCard(LicaiVersion.current());
        drawArt(c, "backOld", BACK_OLD.left, BACK_OLD.top, BACK_OLD.right, BACK_OLD.bottom);
        drawArt(c, "headset", HEADSET.left, HEADSET.top, HEADSET.right, HEADSET.bottom,
                blackCard ? 0xffeacfa4 : 0);
        drawArt(c, "gear", GEAR.left, GEAR.top, GEAR.right, GEAR.bottom,
                blackCard ? 0xffeacfa4 : 0);
        drawArt(c, "headChevron", HEAD_CHEVRON.left, HEAD_CHEVRON.top,
                HEAD_CHEVRON.right, HEAD_CHEVRON.bottom, blackCard ? 0xffeacfa4 : 0);

        Bitmap avatar = ProfileStore.avatar();
        if (avatar == null) avatar = art.get("avatar");
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        r.set(AVATAR.left, AVATAR.top, AVATAR.right, AVATAR.bottom);
        c.save();
        c.clipPath(roundPath(AVATAR, AVATAR_RADIUS));
        c.drawBitmap(avatar, null, r, paint);
        c.restore();

        text(c, ProfileStore.name(), NAME_X, NAME_BASE, NAME_SIZE,
                blackCard ? 0xfff4f4f4 : NAME_INK, true, SX_NAME);
        round(c, PHONE_PILL_RECT.left, PHONE_PILL_RECT.top,
                PHONE_PILL_RECT.right, PHONE_PILL_RECT.bottom, PHONE_PILL_RADIUS, PHONE_PILL);
        centerText(c, ProfileStore.phone(), PHONE_CX, PHONE_BASE, PHONE_SIZE, PHONE_INK, SX_PHONE);
    }

    private android.graphics.Path roundPath(RectF box, float radius) {
        android.graphics.Path p = new android.graphics.Path();
        p.addRoundRect(box, radius, radius, android.graphics.Path.Direction.CW);
        return p;
    }

    private void drawNav(Canvas c) {
        if (blackCardNav.draw(c, 4, W, NAV_TOP, H)) return;
        // The bar is the 消息 page's own band, so its geometry and the first three entries never
        // move; only the two cells that carry this page's selection are replaced.
        drawArt(c, "navbar", 0, NAV_TOP, W, H);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(BAR_BG);
        c.drawRect(768, NAV_TOP + 1, W, NAV_TOP + 173, paint);
        drawArt(c, "navMsg", 768, NAV_TOP, 1024, NAV_TOP + 173);
        drawArt(c, "navMe", 1024, NAV_TOP, W, NAV_TOP + 173);
    }

    // ------------------------------------------------------------------ hit test

    /** Returns an action token for the tap at (x, y) — screen coordinates in design space. */
    public String hitTest(State s, float x, float y) {
        if (y >= NAV_TOP) return "nav:" + nearest(new float[]{128, 382, 639, 898, 1151}, x);
        float cy = y + s.scroll;

        if (cy < 540) {
            if (cy > 160 && cy < 280) {
                if (x >= BACK_OLD.left - 20 && x <= BACK_OLD.right + 20) return "noop";
                if (x >= HEADSET.left - 20 && x <= HEADSET.right + 10) return "noop";
                if (x >= GEAR.left - 10) return "noop";
            }
            // the whole identity row — avatar, name, phone and its chevron — opens 个人信息
            if (x >= 60 && x <= 1110) return "profile";
            return "noop";
        }
        if (cy >= BANNER.top && cy <= BANNER.bottom) {
            if (x >= BANNER_DONE.left && x <= BANNER_DONE.right) return "noop";
            if (x >= BANNER_CLOSE.left && x <= BANNER_CLOSE.right) return "bannerClose";
            return "noop";
        }
        if (cy < CARDS[0][0]) return "noop";

        if (cy <= CARDS[0][1]) return "noop";
        if (cy <= CARDS[1][1]) {
            if (cy < 1080) return "noop";
            return "noop";
        }
        // the 账单 card opens the 账单 page, whose rows in turn open their own 账单详情
        if (cy <= CARDS[2][1]) return "bills";
        if (cy <= CARDS[3][1]) {
            int row = (int) ((cy - ASSET_TOP) / ASSET_ROW_H);
            row = Math.max(0, Math.min(ASSET_ROWS.length - 1, row));
            if (row == 0) return "assets";
            // 「余额」进余额页，「余额宝」进余额宝页，「花呗」进花呗页；其余几行点了什么也不做
            if (row == 1) return "balance";
            if (row == 2) return "yuebao";
            if (row == 3) return "huabei";
            return "noop";
        }
        if (cy <= CARDS[4][1]) {
            int row = cy < 2420 ? 0 : 1;
            return grid(row, x);
        }
        if (cy <= CARDS[5][1]) {
            if (cy < 2830) return "noop";
            return grid(2, x);
        }
        if (cy <= CARDS[6][1]) {
            if (cy < 3255) return "noop";
            return grid(3, x);
        }
        if (cy <= CARDS[7][1]) {
            if (cy < 3680) return "noop";
            return grid(4, x);
        }
        return "noop";
    }

    /** The cell of one grid row the tap landed in. */
    private static String grid(int labelRow, float x) {
        int col = Math.max(0, Math.min(3, (int) ((x - (GRID_CX[0] - GRID_COL_W / 2f)) / GRID_COL_W)));
        return "noop";
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
        drawArt(c, name, l, t, rr, b, 0);
    }

    private void drawArt(Canvas c, String name, float l, float t, float rr, float b, int tint) {
        Bitmap bitmap = art.get(name);
        if (bitmap == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setColorFilter(tint == 0 ? null
                : new android.graphics.PorterDuffColorFilter(tint,
                android.graphics.PorterDuff.Mode.SRC_IN));
        r.set(l, t, rr, b);
        c.drawBitmap(bitmap, null, r, paint);
        paint.setColorFilter(null);
    }

    private void text(Canvas c, String value, float x, float baseline, float size, int color,
                      boolean bold, float sx) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        c.save();
        c.scale(sx, 1f);
        c.drawText(value, x / sx, baseline, paint);
        c.restore();
    }

    private void centerText(Canvas c, String value, float cx, float baseline, float size, int color,
                            float sx) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        c.save();
        c.scale(sx, 1f);
        c.drawText(value, cx / sx, baseline, paint);
        c.restore();
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void round(Canvas c, float l, float t, float rr, float b, float radius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRoundRect(l, t, rr, b, radius, radius, paint);
    }

}
