package com.luoyun.doutu.features.zhi.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.luoyun.doutu.R;
import com.luoyun.doutu.features.zhi.data.LicaiVersion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Native rendition of the 消息 tab of the 小支主页.
 *
 * <p>The page is rebuilt on the same 1280x2816 reference canvas as the rest of the 支 module. Only
 * the artwork that cannot be redrawn travels as bitmaps — the search pill, the broom badge, the
 * contacts glyph, the two avatars, the four glyphs of the 全部会话 header, the five conversation
 * icons, the AI chip and the whole bottom bar. Everything else is drawn here: the page's own
 * backdrop (a plate sampled from the reference and stretched), the conversation card, every label,
 * figure and timestamp, the unread dot and the + button. The reference screenshot itself is never
 * rendered.
 */
public final class MessageScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;
    public static final float NAV_TOP = 2560f;

    /** Mutable presentation state owned by the hosting activity. */
    public static final class State {
        /** The 消息 tab is the page itself, so there is nothing to scroll to; kept for symmetry. */
        public float scroll;
    }

    // ------------------------------------------------------------------ palette
    private static final int PAGE_BG = 0xfff2f3f7;
    /** Below the card the page settles on a slightly warmer grey than the wash above it. */
    private static final int PAGE_LOW = 0xfff5f5f5;
    private static final int CARD = 0xffffffff;
    private static final int INK = 0xff2e2e2e;
    /** The 消息 header is set in the page's own slightly bluer ink. */
    private static final int HEAD_INK = 0xff2a2e33;
    private static final int SUB_INK = 0xff949494;
    private static final int TIME_INK = 0xffcdcdcd;
    private static final int UNREAD = 0xffcccccc;
    private static final int PLUS_INK = 0xff2b3038;
    private static final int BADGE = 0xffc9d1dc;

    /**
     * The reference face is not the platform default: it sets CJK about 7% smaller on the em and
     * its digits about 5% wider. Every run below is therefore sized for the reference's cap height
     * and then stretched horizontally to the width the reference gives it; these are the factors
     * measured against the reference, run by run. See {@code _msg/cmp3.py}.
     */
    private static final float SX_TITLE = 1.006f;
    private static final float SX_HEAD = 0.995f;
    private static final float SX_NAME = 0.984f;
    private static final float SX_SUB = 1.016f;
    private static final float SX_TIME = 0.978f;
    private static final float SX_SECTION = 0.963f;
    private static final float SX_CAPTION = 1.030f;

    // ------------------------------------------------------------------ geometry
    /** Height of the sampled backdrop plate, which fades into the flat page grey below it. */
    private static final float PLATE_H = 600f;
    /** Height of the 黑卡 header artwork, carried on to the card's own top edge below it. */
    private static final float BLACK_HEADER_H = 440f;
    /** Rows 1380..1500 are hidden behind the card; the two page greys are joined across them. */
    private static final float BLEND_TOP = 1380f;
    private static final float BLEND_BOTTOM = 1500f;

    private static final RectF CARD_RECT = new RectF(35, 636, 1243, 2106);
    private static final float CARD_RADIUS = 52f;

    /** 消息, the broom badge that clears the list, the search pill and the two header buttons. */
    private static final float TITLE_X = 35f;
    private static final float TITLE_BASE = 246f;
    private static final float TITLE_SIZE = 64f;
    private static final float BADGE_X = 243f;
    private static final float BADGE_Y = 222f;
    private static final float BADGE_R = 44f;
    private static final RectF PILL = new RectF(316, 160, 983, 283);
    private static final RectF CONTACTS = new RectF(1010, 170, 1106, 266);
    private static final float PLUS_X = 1200.5f;
    private static final float PLUS_Y = 220.5f;
    private static final float PLUS_R = 37f;
    private static final float PLUS_STROKE = 6.5f;
    private static final float PLUS_ARM = 15.5f;

    /** The two pinned entries above the card. */
    private static final RectF AV_PAN = new RectF(68, 330, 245, 507);
    private static final RectF AV_ALL = new RectF(316, 337, 480, 502);
    private static final float CAP_BASE = 583f;
    private static final float CAP_SIZE = 46f;
    private static final float CAP_PAN_CX = 138f;
    private static final float CAP_ALL_CX = 399f;

    /** 全部会话 and the four glyphs that follow it. */
    private static final float HEAD_X = 78f;
    private static final float HEAD_BASE = 729f;
    private static final float HEAD_SIZE = 50f;
    private static final RectF[] HEAD_ICONS = {
            new RectF(781, 678, 845, 743),
            new RectF(905, 677, 965, 743),
            new RectF(1022, 677, 1088, 743),
            new RectF(1143, 677, 1209, 743)};
    private static final String[] HEAD_ICON_ART = {
            "atsign", "mail", "yen", "gift"};

    /**
     * The conversation rows. Every row carries its icon box, the baseline of its name and of its
     * preview line, the timestamp's baseline and the label the tap answers with.
     */
    private static final float ROW_NAME_SIZE = 58f;
    private static final float ROW_SUB_SIZE = 45f;
    private static final float ROW_TIME_SIZE = 39.5f;
    private static final float ROW_NAME_X = 284f;
    private static final float ROW_SUB_X = 285f;
    private static final float ROW_TIME_RIGHT = 1207f;

    private static final float[][] ROWS = {
            // icon left, top, right, bottom, name base, sub base, time base
            {77, 827, 243, 993, 889, 957, 883},
            {85, 1094, 225, 1226, 1147, 1215, 1141},
            {97, 1408, 223, 1558, 1476, 1544, 1471},
            {77, 1653, 243, 1820, 1725, 1794, 1720},
            {79, 1906, 239, 2063, 1977, 2041, 1969}};
    private static final String[] ROW_ICON_ART = {
            "box", "follow", "cake", "life", "ai"};
    private static final String[] ROW_NAME = {
            "消息盒子", "关注动态", "生日提醒", "生活号", "AI理财助理蚂小财"};
    private static final String[] ROW_SUB = {
            "支付宝就业｜易立德企业向你发来一个投递…",
            "支付宝市民中心：尊界回应在测试中V80…",
            "本周有1个好友过生日，送上祝福吧",
            "【1】外卖补贴待领取",
            "[服务卡片]"};
    private static final String[] ROW_TIME = {"11:58", "07:56", "9月21日", "7月11日", "4月24日"};
    /** The 2周前的消息 divider that splits the card, drawn before the row at this index. */
    private static final int SECTION_BEFORE = 2;
    private static final String SECTION_LABEL = "2周前的消息";
    private static final float SECTION_X = 78f;
    private static final float SECTION_BASE = 1341f;
    private static final float SECTION_SIZE = 52f;
    /** The unread dot that hangs on the 生活号 row, on that row's preview line. */
    private static final int DOT_ROW = 3;
    private static final float DOT_X = 1190.5f;
    private static final float DOT_Y = 1775f;
    private static final float DOT_R = 18f;
    /** The AI chip that follows the last row's name. */
    private static final int TAG_ROW = 4;
    private static final RectF TAG = new RectF(754, 1920, 821, 1986);

    private static final float[] NAV_X = {128f, 382f, 639f, 898f, 1151f};

    private final Map<String, Bitmap> art = new LinkedHashMap<>();
    private final BlackCardNav blackCardNav = new BlackCardNav();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private Bitmap plate;

    /** Decodes the cut-out bitmaps. Safe to call again after a configuration change. */
    public void load(Resources res) {
        art.clear();
        art.put("plate", BitmapFactory.decodeResource(res, R.drawable.msg_bgplate));
        art.put("search", BitmapFactory.decodeResource(res, R.drawable.msg_search));
        art.put("broom", BitmapFactory.decodeResource(res, R.drawable.msg_ic_broom));
        art.put("contacts", BitmapFactory.decodeResource(res, R.drawable.msg_ic_contacts));
        art.put("avPan", BitmapFactory.decodeResource(res, R.drawable.msg_av_pan));
        art.put("avAll", BitmapFactory.decodeResource(res, R.drawable.msg_av_all));
        art.put("atsign", BitmapFactory.decodeResource(res, R.drawable.msg_ic_at));
        art.put("mail", BitmapFactory.decodeResource(res, R.drawable.msg_ic_mail));
        art.put("yen", BitmapFactory.decodeResource(res, R.drawable.msg_ic_yen));
        art.put("gift", BitmapFactory.decodeResource(res, R.drawable.msg_ic_gift));
        art.put("box", BitmapFactory.decodeResource(res, R.drawable.msg_ic_box));
        art.put("follow", BitmapFactory.decodeResource(res, R.drawable.msg_ic_follow));
        art.put("cake", BitmapFactory.decodeResource(res, R.drawable.msg_ic_cake));
        art.put("life", BitmapFactory.decodeResource(res, R.drawable.msg_ic_life));
        art.put("ai", BitmapFactory.decodeResource(res, R.drawable.msg_ic_ai));
        art.put("tagAi", BitmapFactory.decodeResource(res, R.drawable.msg_tag_ai));
        art.put("navbar", BitmapFactory.decodeResource(res, R.drawable.msg_navbar));
        art.put("blackBackground", BitmapFactory.decodeResource(res, R.drawable.hk_message_bg));
        blackCardNav.load(res);
        plate = art.get("plate");
    }

    /** The page is a single screen: nothing scrolls, so the range is always empty. */
    public float maxScroll(State s) {
        return 0f;
    }

    // ------------------------------------------------------------------ drawing

    public void draw(Canvas c, State s) {
        drawBackdrop(c);
        drawCard(c);
        drawHeader(c);
        drawPinnedEntries(c);
        drawNav(c);
    }

    /** The sampled wash at the top, then the two flat greys the page settles onto. */
    private void drawBackdrop(Canvas c) {
        if (LicaiVersion.blackCard(LicaiVersion.current())) {
            // The 黑卡 header runs behind the two pinned entries and stops where the conversation
            // card begins, so the card hides its lower edge and the captions the page draws in
            // white sit on it; everything below the card is the page's own light grey, not black.
            c.drawColor(PAGE_LOW);
            headerPlate(c, "blackBackground", BLACK_HEADER_H, CARD_RECT.top);
            return;
        }
        c.drawColor(PAGE_BG);
        if (plate != null) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            r.set(0, 0, W, PLATE_H);
            c.drawBitmap(plate, null, r, paint);
        }
        paint.setShader(new LinearGradient(0, BLEND_TOP, 0, BLEND_BOTTOM,
                PAGE_BG, PAGE_LOW, Shader.TileMode.CLAMP));
        c.drawRect(0, BLEND_TOP, W, BLEND_BOTTOM, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PAGE_LOW);
        c.drawRect(0, BLEND_BOTTOM, W, H, paint);
    }

    /**
     * Lays a header plate over the page's top and carries its own last row down to {@code bottom},
     * so the wash does not stop short of the card and leave a band of the page's grey between them.
     * The plate is only a few hundred pixels tall at source and the rows it ends on are flat, so the
     * repeated row is indistinguishable from the artwork.
     */
    private void headerPlate(Canvas c, String name, float artHeight, float bottom) {
        Bitmap bitmap = art.get(name);
        if (bitmap == null) return;
        float height = Math.min(bottom, artHeight);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        // The artwork is carried a couple of pixels past its own height and the tail starts inside
        // it: a filtered bitmap's last row blends with whatever is beneath it, and left uncovered
        // that blend showed as a lighter hairline across the wash.
        r.set(0, 0, W, height + 2f);
        c.drawBitmap(bitmap, null, r, paint);
        if (height + 1f >= bottom) return;
        Rect src = new Rect(0, bitmap.getHeight() - 1, bitmap.getWidth(), bitmap.getHeight());
        r.set(0, height + 1f, W, bottom);
        c.drawBitmap(bitmap, src, r, paint);
    }

    private void drawHeader(Canvas c) {
        boolean blackCard = LicaiVersion.blackCard(LicaiVersion.current());
        int headerInk = blackCard ? 0xfff4f4f4 : HEAD_INK;
        text(c, "消息", TITLE_X, TITLE_BASE, TITLE_SIZE, headerInk, true, SX_TITLE);
        circle(c, BADGE_X, BADGE_Y, BADGE_R, BADGE);
        drawArt(c, "broom", 197, 176, 289, 268);
        drawArt(c, "search", PILL.left, PILL.top, PILL.right, PILL.bottom);
        drawArt(c, "contacts", CONTACTS.left, CONTACTS.top, CONTACTS.right, CONTACTS.bottom,
                blackCard ? 0xffeacfa4 : 0);
        // the + is a plain ring and cross, so it is drawn rather than cut
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(PLUS_STROKE);
        paint.setColor(blackCard ? 0xffeacfa4 : PLUS_INK);
        c.drawCircle(PLUS_X, PLUS_Y, PLUS_R, paint);
        c.drawLine(PLUS_X - PLUS_ARM, PLUS_Y, PLUS_X + PLUS_ARM, PLUS_Y, paint);
        c.drawLine(PLUS_X, PLUS_Y - PLUS_ARM, PLUS_X, PLUS_Y + PLUS_ARM, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawPinnedEntries(Canvas c) {
        int ink = LicaiVersion.blackCard(LicaiVersion.current()) ? 0xfff4f4f4 : INK;
        drawArt(c, "avPan", AV_PAN.left, AV_PAN.top, AV_PAN.right, AV_PAN.bottom);
        drawArt(c, "avAll", AV_ALL.left, AV_ALL.top, AV_ALL.right, AV_ALL.bottom);
        centerText(c, "理财盘…", CAP_PAN_CX, CAP_BASE, CAP_SIZE, ink, true, SX_CAPTION);
        centerText(c, "全部", CAP_ALL_CX, CAP_BASE + 1, CAP_SIZE, ink, true, SX_CAPTION);
    }

    private void drawCard(Canvas c) {
        r.set(CARD_RECT.left, CARD_RECT.top, CARD_RECT.right, CARD_RECT.bottom);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(CARD);
        c.drawRoundRect(r, CARD_RADIUS, CARD_RADIUS, paint);

        text(c, "全部会话", HEAD_X, HEAD_BASE, HEAD_SIZE, INK, true, SX_HEAD);
        for (int i = 0; i < HEAD_ICONS.length; i++) {
            RectF box = HEAD_ICONS[i];
            drawArt(c, HEAD_ICON_ART[i], box.left, box.top, box.right, box.bottom);
        }

        for (int i = 0; i < ROWS.length; i++) {
            if (i == SECTION_BEFORE) {
                text(c, SECTION_LABEL, SECTION_X, SECTION_BASE, SECTION_SIZE, SUB_INK,
                        false, SX_SECTION);
            }
            float[] row = ROWS[i];
            drawArt(c, ROW_ICON_ART[i], row[0], row[1], row[2], row[3]);
            text(c, ROW_NAME[i], ROW_NAME_X, row[4], ROW_NAME_SIZE, INK, true, SX_NAME);
            text(c, ROW_SUB[i], ROW_SUB_X, row[5], ROW_SUB_SIZE, SUB_INK, false, SX_SUB);
            textRight(c, ROW_TIME[i], ROW_TIME_RIGHT, row[6], ROW_TIME_SIZE, TIME_INK, SX_TIME);
            if (i == DOT_ROW) circle(c, DOT_X, DOT_Y, DOT_R, UNREAD);
            if (i == TAG_ROW) drawArt(c, "tagAi", TAG.left, TAG.top, TAG.right, TAG.bottom);
        }
    }

    private void drawNav(Canvas c) {
        if (blackCardNav.draw(c, 3, W, NAV_TOP, H)) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        // The bar is the reference's own band, so its geometry, its four unselected entries and
        // its 消息 selection never move; only the page behind it changes from tab to tab.
        drawArt(c, "navbar", 0, NAV_TOP, W, H);
    }

    // ------------------------------------------------------------------ hit test

    /** Returns an action token for the tap at (x, y) — screen coordinates in design space. */
    public String hitTest(State s, float x, float y) {
        if (y >= NAV_TOP) return "nav:" + nearest(NAV_X, x);

        if (y < 300) {
            if (y > 140 && x > 300 && x < 1000) return "noop";
            if (y > 150 && x >= 1000 && x < 1130) return "noop";
            if (y > 150 && x >= 1130) return "noop";
            if (x >= 190 && x < 300) return "toast:已清除消息提醒";
            return "noop";
        }
        if (y >= AV_PAN.top - 10 && y <= AV_PAN.bottom + 10) {
            if (x < 300) return "noop";
            if (x < 520) return "noop";
        }
        if (y >= 520 && y < 620) {
            if (x < 300) return "noop";
            if (x < 520) return "noop";
        }
        if (y < CARD_RECT.top) return "noop";

        if (y >= CARD_RECT.top && y < CARD_RECT.bottom) {
            if (y < 760) {
                if (x < 320) return "noop";
                int icon = headerIcon(x);
                if (icon == 0) return "noop";
                if (icon == 1) return "noop";
                if (icon == 2) return "noop";
                if (icon == 3) return "noop";
                return "noop";
            }
            if (y >= 1290 && y < 1370) return "noop";
            int row = rowAt(y);
            if (row < 0) return "noop";
            // 消息盒子是支付消息页的常规入口，点它就直接进那一页
            if (row == 0) return "box";
            return "noop";
        }
        return "noop";
    }

    private static int headerIcon(float x) {
        for (int i = 0; i < HEAD_ICONS.length; i++) {
            if (x >= HEAD_ICONS[i].left - 30 && x <= HEAD_ICONS[i].right + 30) return i;
        }
        return -1;
    }

    /** The row whose band contains y, or -1 when the tap fell between two rows. */
    private static int rowAt(float y) {
        for (int i = 0; i < ROWS.length; i++) {
            float top = ROWS[i][1] - 18f;
            float bottom = ROWS[i][3] + 18f;
            if (i == SECTION_BEFORE) top -= 60f;
            if (y >= top && y <= bottom) return i;
        }
        return -1;
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

    private float text(Canvas c, String value, float x, float baseline, float size, int color,
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
        return x + paint.measureText(value) * sx;
    }

    private void textRight(Canvas c, String value, float x, float baseline, float size, int color,
                           float sx) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.RIGHT);
        c.save();
        c.scale(sx, 1f);
        c.drawText(value, x / sx, baseline, paint);
        c.restore();
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void centerText(Canvas c, String value, float cx, float baseline, float size, int color,
                            boolean bold, float sx) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        c.save();
        c.scale(sx, 1f);
        c.drawText(value, cx / sx, baseline, paint);
        c.restore();
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void circle(Canvas c, float x, float y, float radius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawCircle(x, y, radius, paint);
    }
}
