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
import com.luoyun.doutu.features.zhi.data.Receipt;
import com.luoyun.doutu.features.zhi.data.ReceiptStore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Native rendition of the 收款记录 page the 支 module's 收款记录 tile opens.
 *
 * <p>Everything is rebuilt from primitives at the 1280x2816 reference canvas — the wash, the cards,
 * the dated groups, the dashed 实际到账 rule, the notice, the button and the 请问我 assistant. Only the
 * grey payer avatar, the gold 积分 coin and the assistant's photo are cut out of the reference capture as
 * bitmaps; the screenshot itself is never drawn. The page no longer has an empty state: its 今日 card
 * carries whatever came in today, and the day the app opens on starts with a 30.00 collection.
 *
 * <p>The day heading on the first row is the device's own today, so the page follows the day it is
 * opened rather than the day the reference was captured. The 请问我 pill is drawn after the scroll
 * has been undone, which is what keeps it in the same spot however far the list is pushed.
 *
 * <p>Everything on the page is a local mock: no account, no network and no payment flow is involved.
 */
public final class ReceiptScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;

    /** The type is squeezed a little to match the reference face's own width. */
    private static final float TEXT_SQUEEZE = 0.94f;

    private static final int BG = 0xfff5f5f5;
    private static final int CARD = 0xffffffff;
    private static final int INK = 0xff17181a;
    private static final int DARK = 0xff3f3f3f;
    private static final int GREY = 0xff8f8f8f;
    private static final int GREY_DIM = 0xff878787;
    private static final int HAIR = 0xffe4e4e4;
    private static final int DASH = 0xffdadada;
    private static final int BLUE = 0xff1677ff;
    private static final int PILL_FILL = 0xffeef6ff;
    private static final int PILL_EDGE = 0xffcfe4fb;

    private static final float CARD_L = 41f;
    private static final float CARD_R = 1239f;
    private static final float CARD_RADIUS = 26f;
    /** How tall a dated card is with one record in it; every further row adds {@link #ROW_STEP}. */
    private static final float CARD_BASE = 672f;
    /** The gap between two records of the same day: the avatar plus the air around it. */
    private static final float ROW_STEP = 182f;
    private static final float ROW_FIRST = 479f;

    // the header
    private static final float TITLE_BASE = 245.9f;
    private static final float TITLE_SIZE = 65f;
    private static final float ALL_X = 1229.2f;
    private static final float ALL_BASE = 240.3f;
    private static final float ALL_SIZE = 53f;

    // the 今日 row and its 收钱攒积分 pill
    private static final float TODAY_X = 84f;
    private static final float TODAY_BASE = 435.7f;
    private static final float TODAY_SIZE = 53f;
    private static final RectF PILL = new RectF(884f, 383f, 1276f, 455f);
    private static final float PILL_RADIUS = 36f;
    private static final RectF PILL_COIN = new RectF(912.6f, 390.8f, 965.5f, 441.8f);
    private static final float PILL_TEXT_X = 985f;
    private static final float PILL_TEXT_SIZE = 45f;
    private static final float PILL_CHEVRON_X = 1241f;

    // the dated card. The page used to open on an empty 今日 card — an illustration and 今天暂无收款记录
    // — and both are gone: a card always says what came in, so there is no empty one to draw.
    private static final float FIRST_CARD_TOP = 489f;

    private static final float LABEL_BASE = 113.9f;
    private static final float LABEL_SIZE = 50f;
    private static final float COUNT_BASE = 226.2f;
    private static final float COUNT_SIZE = 78f;
    private static final float TOTAL_BASE = 235.5f;
    private static final float TOTAL_SIZE = 94f;
    private static final float ACTUAL_BASE = 354.9f;
    private static final float ACTUAL_SIZE = 51f;
    private static final float ACTUAL_AMOUNT_BASE = 355.8f;
    private static final float ACTUAL_AMOUNT_SIZE = 52f;
    private static final float DASH_Y = 335f;
    private static final float DASH_L = 358f;
    private static final float DASH_R = 972f;
    private static final RectF AVATAR = new RectF(81.1f, 479.3f, 210.1f, 608.2f);
    private static final float NAME_X = 233f;
    private static final float NAME_BASE = 43.3f;
    private static final float NAME_SIZE = 52f;
    private static final float TIME_X = 235f;
    private static final float TIME_BASE = 124.2f;
    private static final float TIME_SIZE = 44f;
    private static final float AMOUNT_RIGHT = 1198f;
    private static final float AMOUNT_BASE = 91.8f;
    private static final float AMOUNT_SIZE = 62f;

    // the day heading of an older group
    private static final float DAY_X = 82f;
    private static final float DAY_SIZE = 50f;
    private static final float DAY_GAP_ABOVE = 164.7f;
    private static final float DAY_GAP_BELOW = 52.3f;

    // the notice and the button under the last card
    private static final float NOTICE1_BASE = 145.7f;
    private static final float NOTICE1_SIZE = 52f;
    private static final float NOTICE2_BASE = 204.9f;
    private static final float NOTICE2_SIZE = 52f;
    private static final float NOTICE_RULE_Y = 182.5f;
    private static final float NOTICE_RULE_GAP = 10f;
    private static final RectF HISTORY = new RectF(434.7f, 274.1f, 843.8f, 376.1f);
    private static final float HISTORY_RADIUS = 14f;
    private static final int HISTORY_FILL = 0xff1a75f2;
    private static final String HISTORY_TEXT = "查看历史账单";
    private static final float HISTORY_SIZE = 50f;

    // the assistant that stays put
    private static final RectF ASK = new RectF(455f, 2500f, 828f, 2634f);
    private static final float ASK_RADIUS = 67f;
    /**
     * The room the page leaves under its last element, so that scrolled to the very bottom the button
     * ends above the assistant rather than under it: 2816 − 2500 = 316, plus a margin.
     */
    private static final float TAIL = 340f;
    private static final RectF ASK_AVATAR = new RectF(451.1f, 2496.1f, 589.3f, 2634.3f);
    private static final float ASK_TEXT_X = 620f;
    private static final float ASK_TEXT_BASE = 2585f;
    private static final float ASK_TEXT_SIZE = 56f;
    private static final String ASK_TEXT = "请问我";
    private static final int ASK_EDGE_FROM = 0xffcdb8f5;
    private static final int ASK_EDGE_TO = 0xff9dc4ee;

    private static final String NOTICE1 = "由于功能更新";
    private static final String NOTICE2 = "当前只展示5月4日以后的账单";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    private Bitmap avatar;
    private Bitmap coin;
    private Bitmap askAvatar;

    /** What the page remembers between draws: how far it has been pushed. */
    public static final class State {
        public float scroll;
    }

    /** A day's worth of records, with where its card ended up. */
    private static final class Group {
        long day;
        boolean today;
        float cardTop;
        float cardBottom;
        final List<Receipt> items = new ArrayList<>();
    }

    private final List<Group> groups = new ArrayList<>();
    private float contentBottom;

    /** Decodes the cut-out bitmaps. Safe to call again after a configuration change. */
    public void load(Resources res) {
        avatar = BitmapFactory.decodeResource(res, R.drawable.rec_avatar);
        coin = BitmapFactory.decodeResource(res, R.drawable.rec_coin);
        askAvatar = BitmapFactory.decodeResource(res, R.drawable.rec_ask_avatar);
    }

    // ------------------------------------------------------------------ layout

    /** Works out where every day's card sits for the records that are in the store right now. */
    private void layout(List<Receipt> data) {
        groups.clear();
        List<Receipt> fresh = data == null ? new ArrayList<>() : data;
        long now = System.currentTimeMillis();
        long today = ReceiptStore.startOfDay(now);

        for (Receipt item : fresh) {
            long day = ReceiptStore.startOfDay(item.time);
            Group group = null;
            for (Group candidate : groups) if (candidate.day == day) group = candidate;
            if (group == null) {
                group = new Group();
                group.day = day;
                group.today = day == today;
                groups.add(group);
            }
            group.items.add(item);
        }
        // the 今日 row is part of the page's head, so today's card comes first when the day has one
        Group head = null;
        for (Group group : groups) if (group.today) head = group;
        if (head != null) {
            groups.remove(head);
            groups.add(0, head);
        }

        float y = FIRST_CARD_TOP;
        for (Group group : groups) {
            // 今日 sits at the head of the page; every older group is pushed down by its own heading
            group.cardTop = group.today ? FIRST_CARD_TOP : y + DAY_GAP_ABOVE + DAY_GAP_BELOW;
            group.cardBottom = group.cardTop + cardHeight(group.items.size());
            y = group.cardBottom;
        }
        contentBottom = groups.isEmpty() ? FIRST_CARD_TOP : y + HISTORY.bottom;
    }

    /** How tall a dated card is with one record in it; every further row adds {@link #ROW_STEP}. */
    private static float cardHeight(int rows) {
        return CARD_BASE + Math.max(0, rows - 1) * ROW_STEP;
    }

    /** How far the page can be pushed: the tail lets the last card and the button clear the assistant. */
    public float maxScroll(List<Receipt> data) {
        layout(data);
        return Math.max(0f, contentBottom + TAIL - H);
    }

    // ------------------------------------------------------------------ drawing

    public void draw(Canvas c, State s, List<Receipt> data) {
        layout(data);
        c.drawColor(BG);
        c.save();
        c.translate(0, -s.scroll);

        drawHeader(c);
        drawToday(c);
        for (Group group : groups) {
            if (!group.today) drawDayHeading(c, group);
            drawGroupCard(c, group);
        }
        drawNotice(c);
        c.restore();

        // the status bar sits on the wash once the header has been pushed away
        if (s.scroll > 40f) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(BG);
            c.drawRect(0, 0, W, 110, paint);
        }
        drawAsk(c);
    }

    private void drawHeader(Canvas c) {
        stroke(INK, 5f);
        path.reset();
        path.moveTo(84.7f, 190.2f);
        path.lineTo(49.5f, 219.9f);
        path.lineTo(84.7f, 249.6f);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        centerText(c, "收款记录", W / 2f, TITLE_BASE, TITLE_SIZE, 0xff000000, true);
        textRight(c, "全部账单", ALL_X, ALL_BASE, ALL_SIZE, GREY_DIM, false);
    }

    private void drawToday(Canvas c) {
        text(c, "今日", TODAY_X, TODAY_BASE, TODAY_SIZE, 0xff8a8a8a, false);
        String date = monthDay(System.currentTimeMillis());
        float x = TODAY_X + textWidth("今日", TODAY_SIZE, false) + 16f;
        text(c, date, x, TODAY_BASE, TODAY_SIZE, 0xff7d7d7d, false);

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PILL_FILL);
        c.drawRoundRect(PILL, PILL_RADIUS, PILL_RADIUS, paint);
        stroke(PILL_EDGE, 2.4f);
        c.drawRoundRect(PILL, PILL_RADIUS, PILL_RADIUS, paint);
        paint.setStyle(Paint.Style.FILL);
        drawArt(c, coin, PILL_COIN);
        text(c, "收钱攒积分", PILL_TEXT_X, 435.1f, PILL_TEXT_SIZE, 0xff2b3238, false);
        chevron(c, PILL_CHEVRON_X, 419f, BLUE, 4f);
    }

    private void drawDayHeading(Canvas c, Group group) {
        text(c, ReceiptStore.dayLabel(group.day), DAY_X, group.cardTop - DAY_GAP_BELOW, DAY_SIZE,
                0xff8c8c8c, false);
    }

    private void drawGroupCard(Canvas c, Group group) {
        float top = group.cardTop;
        r.set(CARD_L, top, CARD_R, group.cardBottom);
        round(c, r, CARD_RADIUS, CARD);

        double total = 0;
        double actual = 0;
        for (Receipt item : group.items) {
            total += item.amount;
            actual += item.actual;
        }
        text(c, "收款笔数", 84f, top + LABEL_BASE, LABEL_SIZE, GREY, false);
        textRight(c, "收款总额", 1197f, top + LABEL_BASE, LABEL_SIZE, GREY, false);
        text(c, group.items.size() + "笔", 82f, top + COUNT_BASE, COUNT_SIZE, INK, true);
        textRight(c, ReceiptStore.yuan(total), 1197f, top + TOTAL_BASE, TOTAL_SIZE, INK, true);
        text(c, "实际到账", 81f, top + ACTUAL_BASE, ACTUAL_SIZE, DARK, false);
        textRight(c, "¥ " + ReceiptStore.money(actual), 1199f, top + ACTUAL_AMOUNT_BASE,
                ACTUAL_AMOUNT_SIZE, INK, false);
        dashed(c, top + DASH_Y);

        for (int i = 0; i < group.items.size(); i++) {
            Receipt item = group.items.get(i);
            float rowTop = top + ROW_FIRST + i * ROW_STEP;
            Bitmap picture = ReceiptStore.avatar(item);
            r.set(AVATAR.left, rowTop, AVATAR.right, rowTop + AVATAR.height());
            if (picture == null) {
                drawArt(c, avatar, r);
            } else {
                clipCircle(c, r, picture);
            }
            text(c, item.payer, NAME_X, rowTop + NAME_BASE, NAME_SIZE, INK, true);
            text(c, ReceiptStore.clock(item.time), TIME_X, rowTop + TIME_BASE, TIME_SIZE, GREY, false);
            textRight(c, item.incomeText(), 1198f, rowTop + AMOUNT_BASE, AMOUNT_SIZE, INK, true);
        }
    }

    private void drawNotice(Canvas c) {
        float base = contentBottom - HISTORY.bottom;
        // the rule the reference runs on both sides of the second line, stopping short of the text
        float half = textWidth(NOTICE2, NOTICE2_SIZE, false) / 2f + NOTICE_RULE_GAP;
        line(c, CARD_L, base + NOTICE_RULE_Y, W / 2f - half, base + NOTICE_RULE_Y, HAIR, 2f);
        line(c, W / 2f + half, base + NOTICE_RULE_Y, CARD_R, base + NOTICE_RULE_Y, HAIR, 2f);
        centerText(c, NOTICE1, W / 2f, base + NOTICE1_BASE, NOTICE1_SIZE, 0xff7e7e7e, false);
        centerText(c, NOTICE2, W / 2f, base + NOTICE2_BASE, NOTICE2_SIZE, 0xff7e7e7e, false);

        r.set(HISTORY.left, base + HISTORY.top, HISTORY.right, base + HISTORY.bottom);
        round(c, r, HISTORY_RADIUS, HISTORY_FILL);
        centerText(c, HISTORY_TEXT, W / 2f, base + HISTORY.top + 69f, HISTORY_SIZE, Color.WHITE, false);
    }

    /** The 请问我 pill: it is drawn outside the scrolled canvas, so it never moves. */
    private void drawAsk(Canvas c) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        c.drawRoundRect(ASK, ASK_RADIUS, ASK_RADIUS, paint);
        paint.setShader(new LinearGradient(ASK.left, ASK.top, ASK.right, ASK.bottom,
                ASK_EDGE_FROM, ASK_EDGE_TO, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.2f);
        c.drawRoundRect(ASK, ASK_RADIUS, ASK_RADIUS, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        drawArt(c, askAvatar, ASK_AVATAR);
        text(c, ASK_TEXT, ASK_TEXT_X, ASK_TEXT_BASE, ASK_TEXT_SIZE, 0xff2b2f36, true);
    }

    // ------------------------------------------------------------------ hit test

    /**
     * Returns an action token for the tap at (x, y) — screen coordinates in design space.
     *
     * <p>Only five things on this page have a job of their own: 返回 goes back, 全部账单 answers with
     * its toast, the 收钱攒积分 pill answers with its own, a payer's row opens that record's 收款详情
     * (and the picture at the left of a row opens the 头像 sheet), and the 查看历史账单 button answers
     * with its toast. <b>Every other point on the page raises the 批量添加 sheet</b> — the header and
     * the strip above it, the 今日 heading, a day's summary figures, the dashed rule, the gaps between
     * the cards, the tail under the last one and the 请问我 assistant's own body. That is deliberate:
     * adding records is what this page is for, so its trigger is the whole page rather than a strip
     * the floating assistant could end up sitting on.
     */
    public String hitTest(State s, float x, float y, List<Receipt> data) {
        layout(data);
        return contentHit(s, x, y);
    }

    /** What the page answers at that point; "batch" wherever nothing more specific owns the tap. */
    private String contentHit(State s, float x, float y) {
        float cy = y + s.scroll;
        if (cy < 300f) {
            if (x < 130f) return "back";
            if (x > ALL_X - 240f) return "all";
            return "batch";
        }
        if (PILL.top < cy && cy < PILL.bottom && x > PILL.left - 20f) return "points";
        for (Group group : groups) {
            if (cy < group.cardTop || cy > group.cardBottom) continue;
            // a day with nothing in it yet answers the same tap: the card is where records come from
            if (group.items.isEmpty()) return "batch";
            for (int i = 0; i < group.items.size(); i++) {
                float rowTop = group.cardTop + ROW_FIRST + i * ROW_STEP;
                if (cy >= rowTop - 10f && cy <= rowTop + ROW_STEP - 10f) {
                    // the payer's picture answers on its own, the rest of the row opens its 详情
                    if (x < AVATAR.right + 24f) return "avatar:" + group.items.get(i).id;
                    return "entry:" + group.items.get(i).id;
                }
            }
            return "batch";
        }
        float base = contentBottom - HISTORY.bottom;
        if (cy > base + HISTORY.top - 10f && cy < base + HISTORY.bottom + 10f
                && x > HISTORY.left - 10f && x < HISTORY.right + 10f) {
            return "history";
        }
        // the 今日 heading, the gaps between the cards, the tail under the last one, and the
        // assistant that floats over them
        return "batch";
    }

    // ------------------------------------------------------------------ primitives

    private void drawArt(Canvas c, Bitmap bitmap, RectF box) {
        if (bitmap == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        c.drawBitmap(bitmap, null, box, paint);
    }

    /** Draws a picture into a circular window, centre-cropped. */
    private void clipCircle(Canvas c, RectF box, Bitmap bitmap) {
        c.save();
        path.reset();
        path.addOval(box, Path.Direction.CW);
        c.clipPath(path);
        float scale = Math.max(box.width() / bitmap.getWidth(), box.height() / bitmap.getHeight());
        float w = bitmap.getWidth() * scale;
        float h = bitmap.getHeight() * scale;
        r.set(box.centerX() - w / 2f, box.centerY() - h / 2f,
                box.centerX() + w / 2f, box.centerY() + h / 2f);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        c.drawBitmap(bitmap, null, r, paint);
        c.restore();
    }

    private void round(Canvas c, RectF box, float radius, int colour) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colour);
        c.drawRoundRect(box, radius, radius, paint);
    }

    private void stroke(int colour, float width) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(colour);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2, int colour, float width) {
        stroke(colour, width);
        c.drawLine(x1, y1, x2, y2, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** The rule that runs between 实际到账 and its figure, dashed the way the reference draws it. */
    private void dashed(Canvas c, float y) {
        stroke(DASH, 2.2f);
        float dash = 11f;
        for (float x = DASH_L; x < DASH_R; x += dash * 2f) {
            c.drawLine(x, y, Math.min(x + dash, DASH_R), y, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void chevron(Canvas c, float tip, float cy, int colour, float width) {
        stroke(colour, width);
        path.reset();
        path.moveTo(tip - 13f, cy - 11f);
        path.lineTo(tip, cy);
        path.lineTo(tip - 13f, cy + 11f);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private float textWidth(String value, float size, boolean bold) {
        textPaint.setTextSize(size);
        textPaint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return textPaint.measureText(value) * TEXT_SQUEEZE;
    }

    private void text(Canvas c, String value, float x, float baseline, float size, int colour,
                      boolean bold) {
        if (value == null) return;
        textPaint.setTextSize(size);
        textPaint.setColor(colour);
        textPaint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        textPaint.setTextAlign(Paint.Align.LEFT);
        c.save();
        c.scale(TEXT_SQUEEZE, 1f, x, 0f);
        c.drawText(value, x, baseline, textPaint);
        c.restore();
    }

    private void textRight(Canvas c, String value, float x, float baseline, float size, int colour,
                           boolean bold) {
        if (value == null) return;
        textPaint.setTextSize(size);
        textPaint.setColor(colour);
        textPaint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        c.save();
        c.scale(TEXT_SQUEEZE, 1f, x, 0f);
        c.drawText(value, x, baseline, textPaint);
        c.restore();
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void centerText(Canvas c, String value, float cx, float baseline, float size, int colour,
                            boolean bold) {
        if (value == null) return;
        textPaint.setTextSize(size);
        textPaint.setColor(colour);
        textPaint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        textPaint.setTextAlign(Paint.Align.CENTER);
        c.save();
        c.scale(TEXT_SQUEEZE, 1f, cx, 0f);
        c.drawText(value, cx, baseline, textPaint);
        c.restore();
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    /** M月d日 from a moment in time, the shape the 今日 heading uses. */
    public static String monthDay(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        return (c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日";
    }
}
