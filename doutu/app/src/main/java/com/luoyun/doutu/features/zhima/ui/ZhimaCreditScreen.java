package com.luoyun.doutu.features.zhima.ui;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.luoyun.doutu.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native rendition of the “芝麻信用” page reached from the 芝麻信用 card on the 小支主页.
 *
 * <p>Every module on the page is a cut-out bitmap taken from the reference screenshots: the two
 * credit cards of the top carousel, the 大表鸽 Ai assistant card, the eight feed modules and the
 * bottom navigation glyphs. The header, page background, navigation bar and every editable value
 * are drawn from primitives so they can change at runtime.
 *
 * <p>Everything is a local mock: no account, no network and no credit lookup happens here.
 */
public final class ZhimaCreditScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;
    /** Top of the fixed bottom navigation bar. */
    public static final float NAV_TOP = 2544f;
    /** Bottom of the fixed blue header; scrolling content is clipped below it. */
    public static final float HEADER_H = 300f;

    public static final float CAROUSEL_X = 42f;
    public static final float CAROUSEL_Y = 327f;
    public static final float CARD_H = 605f;
    public static final float CARD_GAP = 27f;
    public static final float PERSONAL_W = 858f;
    public static final float ENT_COMPACT_W = 310f;
    public static final float ENT_FULL_W = 858f;

    public static final float AI_X = 40f;
    /** The assistant card starts above its rounded body so the bird keeps its raised head. */
    public static final float AI_Y = 936f;
    public static final float AI_H = 402f;
    public static final float AI_W = 1200f;

    /** Top of the module board, measured on the reference screenshots. */
    public static final float FEED_TOP = 1377f;
    /** Blank space kept under the board so a long press has somewhere to add a module. */
    public static final float FEED_BOTTOM_PAD = 420f;
    public static final float COL_W = 585f;
    public static final float ROW_GAP = 28f;
    private static final float[] COL_X = {41f, 654f};

    private static final int NAV_BLUE = 0xff1677ff;
    private static final int NAV_LABEL = 0xff3c3f45;
    private static final int INK = 0xff1f2023;
    private static final int BLUE_SCORE = 0xff1f63f2;
    private static final int MUTED = 0xff4a4a4e;

    /** A reusable feed module: a cut-out bitmap plus the label shown in the picker sheet. */
    public static final class Module {
        public final String key;
        public final String label;
        public final int res;
        public final float w;
        public final float h;

        Module(String key, String label, int res, float w, float h) {
            this.key = key;
            this.label = label;
            this.res = res;
            this.w = w;
            this.h = h;
        }
    }

    private static final Map<String, Module> LIBRARY = new LinkedHashMap<>();

    static {
        register(new Module("goal", "芝麻分目标", R.drawable.zhm_f_goal, 585f, 780f));
        register(new Module("judge", "芝麻鉴真", R.drawable.zhm_f_judge, 584f, 803f));
        register(new Module("table", "免押额度对照", R.drawable.zhm_f_table, 585f, 1075f));
        register(new Module("huabei", "花呗问答", R.drawable.zhm_f_huabei, 584f, 1076f));
        register(new Module("quote", "信用分问答", R.drawable.zhm_f_quote, 585f, 1076f));
        register(new Module("qa663", "663分资讯", R.drawable.zhm_f_qa663, 584f, 1078f));
        register(new Module("limit", "信用额度", R.drawable.zhm_f_limit, 580f, 781f));
        register(new Module("wlj", "王老吉广告", R.drawable.zhm_f_wlj, 585f, 972f));
    }

    private static void register(Module m) {
        LIBRARY.put(m.key, m);
    }

    public static List<Module> library() {
        return new ArrayList<>(LIBRARY.values());
    }

    public static Module module(String key) {
        return LIBRARY.get(key);
    }

    /** One place on the board. An item whose key is null keeps its height as an empty slot. */
    public static final class Slot {
        public String key;
        public float h;

        public Slot(String key, float h) {
            this.key = key;
            this.h = h;
        }
    }

    /** Mutable presentation state owned by the hosting activity. */
    public static final class State {
        public float scroll;
        /** Horizontal offset of the credit-card carousel. */
        public float carousel;
        /** False = the compact 企业信用 card of the first reference, true = the expanded one. */
        public boolean enterpriseOpen;

        public String pLabel = "芝麻信用良好";
        public String pScore = "636";
        public String pQuota = "2500元";
        public String pDiscount = "1157元";
        public String pPrivilege = "1项";

        public String eLabel = "企业信用较弱";
        public String eScore = "1319";
        public String eCompany = "乐畅云（上海）科技有限公司";
        public String eGo = "去涨分";
        public String eQuota = "测一测额度";
        public String ePrivilege = "5项";

        public final List<Slot> left = new ArrayList<>(Arrays.asList(
                new Slot("goal", 780f), new Slot("table", 1075f), new Slot("quote", 1076f)));
        public final List<Slot> right = new ArrayList<>(Arrays.asList(
                new Slot("judge", 803f), new Slot("huabei", 1076f), new Slot("qa663", 1078f)));

        public List<Slot> column(int col) {
            return col == 0 ? left : right;
        }

        /** Places a module in an existing slot, keeping the board's positions stable. */
        public void place(int col, int index, String key) {
            Module m = LIBRARY.get(key);
            if (m == null || index < 0 || index >= column(col).size()) return;
            Slot slot = column(col).get(index);
            slot.key = key;
            slot.h = m.h;
        }
    }

    private final Map<String, Bitmap> art = new LinkedHashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final RectF clip = new RectF();
    private final Path navPath = new Path();
    private final Path hintPath = new Path();
    private Typeface scoreFace;

    /** Decodes the cut-out bitmaps and the score typeface. Safe to call again on a config change. */
    public void load(Resources res, AssetManager assets) {
        art.clear();
        art.put("personal", BitmapFactory.decodeResource(res, R.drawable.zhm_card_personal));
        art.put("entCompact", BitmapFactory.decodeResource(res, R.drawable.zhm_card_ent_compact));
        art.put("entFull", BitmapFactory.decodeResource(res, R.drawable.zhm_card_ent_full));
        art.put("aiGift", BitmapFactory.decodeResource(res, R.drawable.zhm_ai_gift));
        art.put("aiExpire", BitmapFactory.decodeResource(res, R.drawable.zhm_ai_expire));
        art.put("badge", BitmapFactory.decodeResource(res, R.drawable.zhm_badge));
        art.put("navHome", BitmapFactory.decodeResource(res, R.drawable.zhm_nav_home));
        art.put("navRent", BitmapFactory.decodeResource(res, R.drawable.zhm_nav_rent));
        art.put("navMe", BitmapFactory.decodeResource(res, R.drawable.zhm_nav_me));
        for (Module m : LIBRARY.values()) {
            art.put(m.key, BitmapFactory.decodeResource(res, m.res));
        }
        try {
            scoreFace = Typeface.createFromAsset(assets, "fonts/barlow_semi_condensed_semibold.ttf");
        } catch (RuntimeException e) {
            scoreFace = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        }
    }

    // ------------------------------------------------------------------ geometry

    public static float enterpriseWidth(State s) {
        return s.enterpriseOpen ? ENT_FULL_W : ENT_COMPACT_W;
    }

    public static float carouselContentWidth(State s) {
        return CAROUSEL_X + PERSONAL_W + CARD_GAP + enterpriseWidth(s);
    }

    public static float maxCarousel(State s) {
        return Math.max(0f, carouselContentWidth(s) + CAROUSEL_X - W);
    }

    /** Top of the slot at index in the given column, in page (unscrolled) coordinates. */
    public static float columnTop(State s, int col, int index) {
        float y = FEED_TOP;
        List<Slot> slots = s.column(col);
        for (int i = 0; i < index && i < slots.size(); i++) {
            y += slots.get(i).h + ROW_GAP;
        }
        return y;
    }

    public static float columnBottom(State s, int col) {
        return columnTop(s, col, s.column(col).size());
    }

    public float maxScroll(State s) {
        float bottom = Math.max(columnBottom(s, 0), columnBottom(s, 1));
        return Math.max(0f, bottom + FEED_BOTTOM_PAD - H);
    }

    // ------------------------------------------------------------------ drawing

    public void draw(Canvas c, State s) {
        drawBackground(c);
        c.save();
        clip.set(0f, HEADER_H, W, NAV_TOP);
        c.clipRect(clip);
        c.translate(0f, -s.scroll);
        drawCarousel(c, s);
        drawAssistant(c);
        drawFeed(c, s);
        c.restore();
        drawHeader(c);
        drawNav(c);
    }

    private void drawBackground(Canvas c) {
        // Sampled off the reference: a flat blue down to the assistant card, then fading out.
        paint.setShader(new LinearGradient(0, 0, 0, 1350,
                new int[]{0xff2d7cf6, 0xff2d7cf6, 0xffeef1f7},
                new float[]{0f, 0.704f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, paint);
        paint.setShader(null);
    }

    private void drawHeader(Canvas c) {
        // Back chevron.
        line(c, 88, 190, 62, 219, Color.WHITE, 6);
        line(c, 62, 219, 88, 248, Color.WHITE, 6);
        text(c, "芝麻信用", 130, 247, 64, Color.WHITE, false, 1f);

        // 待收取 marker: the yellow drop cut straight out of the reference, plus its caption.
        drawArt(c, "badge", 1028, 179, 1104, 261);
        text(c, "待收取", 1117, 236, 41, Color.WHITE, false, 1f);
    }

    private void drawNav(Canvas c) {
        navPath.reset();
        navPath.moveTo(0, NAV_TOP + 30);
        navPath.quadTo(0, NAV_TOP, 30, NAV_TOP);
        navPath.lineTo(W - 30, NAV_TOP);
        navPath.quadTo(W, NAV_TOP, W, NAV_TOP + 30);
        navPath.lineTo(W, H);
        navPath.lineTo(0, H);
        navPath.close();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffffffff);
        c.drawPath(navPath, paint);
        centerText(c, "首页", 214, 2718, 40, NAV_BLUE, false, 1f);
        centerText(c, "租赁", 640, 2718, 40, NAV_LABEL, false, 1f);
        centerText(c, "我的", 1067, 2718, 40, NAV_LABEL, false, 1f);
        drawArt(c, "navHome", 183, 2598, 246, 2671);
        drawArt(c, "navRent", 600, 2572, 735, 2684);
        drawArt(c, "navMe", 1025, 2582, 1120, 2677);
    }

    private void drawCarousel(Canvas c, State s) {
        float x = CAROUSEL_X - s.carousel;
        drawArt(c, "personal", x, CAROUSEL_Y, x + PERSONAL_W, CAROUSEL_Y + CARD_H);
        drawPersonalText(c, s, x);

        float ex = x + PERSONAL_W + CARD_GAP;
        if (s.enterpriseOpen) {
            drawArt(c, "entFull", ex, CAROUSEL_Y, ex + ENT_FULL_W, CAROUSEL_Y + CARD_H);
            drawEnterpriseText(c, s, ex);
        } else {
            drawArt(c, "entCompact", ex, CAROUSEL_Y, ex + ENT_COMPACT_W, CAROUSEL_Y + CARD_H);
        }
    }

    private void drawPersonalText(Canvas c, State s, float ox) {
        textFit(c, s.pLabel, ox + 38, CAROUSEL_Y + 64, 34, 320f, INK, true, 1f);
        score(c, s.pScore, ox + 40, CAROUSEL_Y + 213, 330f);
        textFit(c, s.pQuota, ox + 41, CAROUSEL_Y + 447, 58, 235f, INK, true, 0.88f);
        textFit(c, s.pDiscount, ox + 331, CAROUSEL_Y + 447, 58, 235f, INK, true, 0.88f);
        textFit(c, s.pPrivilege, ox + 617, CAROUSEL_Y + 447, 58, 150f, INK, true, 0.88f);
    }

    private void drawEnterpriseText(Canvas c, State s, float ox) {
        textFit(c, s.eLabel, ox + 41, CAROUSEL_Y + 66, 34, 320f, INK, true, 1f);
        score(c, s.eScore, ox + 39, CAROUSEL_Y + 212, 330f);
        textFit(c, s.eCompany, ox + 344, CAROUSEL_Y + 139, 36, 470f, MUTED, false, 1f);
        textFit(c, s.eGo, ox + 344, CAROUSEL_Y + 202, 40, 240f, INK, false, 1f);
        chevron(c, ox + 484, CAROUSEL_Y + 189, 0xff9aa0a8);
        textFit(c, s.eQuota, ox + 43, CAROUSEL_Y + 452, 42, 270f, NAV_BLUE, false, 1f);
        textFit(c, s.ePrivilege, ox + 474, CAROUSEL_Y + 452, 46, 150f, INK, true, 1f);
    }

    /** Draws the big credit score in the same face the reference uses for it. */
    private void score(Canvas c, String value, float x, float baseline, float maxWidth) {
        if (value == null || value.isEmpty()) return;
        Typeface face = scoreFace != null
                ? scoreFace : Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
        float size = 137f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(BLUE_SCORE);
        paint.setTypeface(face);
        paint.setTextSize(size);
        paint.setTextAlign(Paint.Align.LEFT);
        // The reference sets its score in tabular figures, so every digit gets the same
        // advance instead of the narrower one a proportional face gives a "1".
        float advance = 0f;
        for (int i = 0; i < value.length(); i++) {
            advance = Math.max(advance, paint.measureText(value, i, i + 1));
        }
        // The reference face is a touch wider than the bundled one; stretch to match it.
        float natural = advance * value.length();
        float scale = Math.min(1.053f, natural > maxWidth ? maxWidth / natural : 1.053f);
        c.save();
        c.translate(x, 0);
        c.scale(scale, 1f);
        for (int i = 0; i < value.length(); i++) {
            String glyph = value.substring(i, i + 1);
            c.drawText(glyph, i * advance + (advance - paint.measureText(glyph)) / 2f,
                    baseline, paint);
        }
        c.restore();
    }

    /** Draws left aligned text, shrinking it horizontally when the value is too wide. */
    private void textFit(Canvas c, String value, float x, float baseline, float size, float maxWidth,
                         int color, boolean bold, float xScale) {
        if (value == null || value.isEmpty()) return;
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextSize(size);
        float natural = paint.measureText(value) * xScale;
        text(c, value, x, baseline, size, color, bold,
                natural > maxWidth ? xScale * maxWidth / natural : xScale);
    }

    private void drawAssistant(Canvas c) {
        drawArt(c, "aiGift", AI_X, AI_Y, AI_X + AI_W, AI_Y + AI_H);
    }

    private void drawFeed(Canvas c, State s) {
        for (int col = 0; col < 2; col++) {
            List<Slot> slots = s.column(col);
            float y = FEED_TOP;
            for (Slot slot : slots) {
                if (slot.key == null) {
                    drawEmptySlot(c, y, slot.h);
                } else {
                    drawArt(c, slot.key, COL_X[col], y, COL_X[col] + COL_W, y + slot.h);
                }
                y += slot.h + ROW_GAP;
            }
        }
    }

    /** Faint dashed outline marking a place where a module can be dropped back in. */
    private void drawEmptySlot(Canvas c, float top, float height) {
        hintPath.reset();
        hintPath.addRoundRect(new RectF(COL_X[0] + 1, top + 1, COL_X[0] + COL_W - 1, top + height - 1),
                26, 26, Path.Direction.CW);
        hintPath.addRoundRect(new RectF(COL_X[1] + 1, top + 1, COL_X[1] + COL_W - 1, top + height - 1),
                26, 26, Path.Direction.CW);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(0x556d7a8c);
        paint.setPathEffect(new DashPathEffect(new float[]{14, 12}, 0));
        c.drawPath(hintPath, paint);
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);
    }

    // ------------------------------------------------------------------ hit test

    /** Returns a token describing what the tap at screen (x, y) selects. */
    public String hitTest(State s, float x, float y) {
        if (y >= NAV_TOP) {
            if (x < 426) return "nav:0";
            if (x < 853) return "nav:1";
            return "nav:2";
        }
        if (y < HEADER_H) {
            if (x < 140) return "back";
            return "noop";
        }
        float cy = y + s.scroll;
        if (cy >= CAROUSEL_Y && cy <= CAROUSEL_Y + CARD_H) {
            float ox = CAROUSEL_X - s.carousel;
            if (s.enterpriseOpen) {
                float ex = ox + PERSONAL_W + CARD_GAP;
                if (x >= ex && x < ex + ENT_FULL_W) {
                    String f = creditField(x - ex, cy - CAROUSEL_Y, true);
                    return f == null ? "carousel" : "edit:" + f;
                }
            } else {
                float ex = ox + PERSONAL_W + CARD_GAP;
                if (x >= ex && x < ex + ENT_COMPACT_W
                        && cy - CAROUSEL_Y > 460 && cy - CAROUSEL_Y < 565) {
                    return "expandEnt";
                }
            }
            if (x >= ox && x < ox + PERSONAL_W) {
                String f = creditField(x - ox, cy - CAROUSEL_Y, false);
                return f == null ? "carousel" : "edit:" + f;
            }
            return "carousel";
        }
        if (cy >= AI_Y && cy <= AI_Y + AI_H && x >= AI_X && x <= AI_X + AI_W) return "ai";
        int col = x < COL_X[0] + COL_W + (COL_X[1] - COL_X[0] - COL_W) / 2f ? 0 : 1;
        if (cy >= FEED_TOP) {
            List<Slot> slots = s.column(col);
            for (int i = 0; i < slots.size(); i++) {
                float top = columnTop(s, col, i);
                if (cy >= top && cy <= top + slots.get(i).h
                        && x >= COL_X[col] && x <= COL_X[col] + COL_W) {
                    return slots.get(i).key == null ? "empty:" + col + ":" + i
                            : "feed:" + col + ":" + i;
                }
            }
        }
        return "noop";
    }

    /** Returns "col:index" when the long press lands on a module or an empty slot, "add:col"
     * when it lands on the blank area under the board. */
    public String longPress(State s, float x, float y) {
        if (y < HEADER_H || y >= NAV_TOP) return null;
        float cy = y + s.scroll;
        if (cy < FEED_TOP) return null;
        int col = x < COL_X[0] + COL_W + (COL_X[1] - COL_X[0] - COL_W) / 2f ? 0 : 1;
        List<Slot> slots = s.column(col);
        for (int i = 0; i < slots.size(); i++) {
            float top = columnTop(s, col, i);
            if (cy >= top && cy <= top + slots.get(i).h) return col + ":" + i;
        }
        return "add:" + col;
    }

    private String creditField(float lx, float ly, boolean enterprise) {
        if (enterprise) {
            if (box(lx, ly, 36, 30, 246, 72)) return "e.label";
            if (box(lx, ly, 46, 105, 320, 220)) return "e.score";
            if (box(lx, ly, 340, 100, 790, 145)) return "e.company";
            if (box(lx, ly, 340, 160, 500, 212)) return "e.go";
            if (box(lx, ly, 36, 408, 250, 458)) return "e.quota";
            if (box(lx, ly, 466, 400, 550, 460)) return "e.privilege";
            return null;
        }
        if (box(lx, ly, 36, 29, 246, 75)) return "p.label";
        if (box(lx, ly, 40, 105, 252, 224)) return "p.score";
        if (box(lx, ly, 38, 392, 216, 458)) return "p.quota";
        if (box(lx, ly, 328, 392, 504, 458)) return "p.discount";
        if (box(lx, ly, 614, 392, 692, 458)) return "p.privilege";
        return null;
    }

    private static boolean box(float x, float y, float x0, float y0, float x1, float y1) {
        return x >= x0 && x <= x1 && y >= y0 && y <= y1;
    }

    // ------------------------------------------------------------------ primitives

    private void drawArt(Canvas c, String name, float x1, float y1, float x2, float y2) {
        Bitmap b = art.get(name);
        if (b == null) return;
        r.set(x1, y1, x2, y2);
        c.drawBitmap(b, null, r, paint);
    }

    private void text(Canvas c, String value, float x, float baseline, float size, int color,
                      boolean bold, float xScale) {
        if (value == null || value.isEmpty()) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        if (xScale == 1f) {
            c.drawText(value, x, baseline, paint);
        } else {
            c.save();
            c.translate(x, 0);
            c.scale(xScale, 1f);
            c.drawText(value, 0, baseline, paint);
            c.restore();
        }
    }

    private void centerText(Canvas c, String value, float cx, float baseline, float size, int color,
                            boolean bold, float xScale) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        c.save();
        c.translate(cx, 0);
        c.scale(xScale, 1f);
        c.drawText(value, 0, baseline, paint);
        c.restore();
        paint.setTextAlign(Paint.Align.LEFT);
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

    private void chevron(Canvas c, float x, float y, int color) {
        line(c, x - 7, y - 12, x + 6, y, color, 4);
        line(c, x + 6, y, x - 7, y + 12, color, 4);
    }
}
