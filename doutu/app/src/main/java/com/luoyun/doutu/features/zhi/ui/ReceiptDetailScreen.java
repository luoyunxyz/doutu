package com.luoyun.doutu.features.zhi.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.luoyun.doutu.R;
import com.luoyun.doutu.features.zhi.data.Receipt;
import com.luoyun.doutu.features.zhi.data.ReceiptStore;

/**
 * Native rendition of the 收款详情 page a row of 收款记录 opens.
 *
 * <p>Everything is drawn from primitives at the 1280x2816 reference canvas. The only bitmap is the
 * payer's picture: the detail page's grey rounded square cut out of the reference, or whatever the
 * user picked instead. Every figure on it can be typed over from here — the amount, what arrived,
 * the 积分 line, the free quota, the 交易说明, the payer and, under 顾客画像, the three columns, which
 * are counted from the records until they are pinned.
 *
 * <p>Everything on the page is a local mock: no account, no network and no payment flow is involved.
 */
public final class ReceiptDetailScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;

    private static final float TEXT_SQUEEZE = 0.94f;

    private static final int BG = 0xfff5f5f5;
    private static final int CARD = 0xffffffff;
    private static final int INK = 0xff17181a;
    private static final int GREY = 0xff949494;
    private static final int ROW_LABEL = 0xff565656;
    private static final int ROW_VALUE = 0xff333333;
    private static final int SEPARATOR = 0xffeaeaea;
    private static final int BLUE = 0xff1677ff;

    public static final RectF CARD1 = new RectF(41f, 341.7f, 1239f, 1343.5f);
    public static final RectF CARD2 = new RectF(41f, 1373.2f, 1239f, 1940.8f);
    public static final RectF BUTTON1 = new RectF(41f, 1969.6f, 1239f, 2135.6f);
    public static final RectF BUTTON2 = new RectF(41f, 2163.3f, 1239f, 2331.2f);
    private static final float CARD_RADIUS = 24f;

    private static final float TITLE_X = 138f;
    private static final float TITLE_BASE = 245.9f;
    private static final float TITLE_SIZE = 68f;

    private static final float LABEL1_BASE = 473.9f;
    private static final float LABEL1_SIZE = 55f;
    private static final float AMOUNT_BASE = 614.9f;
    private static final float AMOUNT_SIZE = 122f;
    private static final float ACTUAL_BASE = 731.9f;
    private static final float ACTUAL_SIZE = 58f;

    private static final float ROW_LABEL_X = 85f;
    private static final float ROW_VALUE_RIGHT = 1173f;
    private static final float ROW_LABEL_SIZE = 51f;
    private static final float ROW_VALUE_SIZE = 52f;
    private static final float[] ROW_BASE = {937.7f, 1030.4f, 1122.2f};
    public static final RectF ROW_COIN = new RectF(659.4f, 888.5f, 719.7f, 947.9f);
    private static final float ROW1_VALUE_X = 735f;
    private static final float ROW_CHEVRON_TIP = 1196f;
    private static final float MORE_BASE = 1264.1f;
    private static final float MORE_SIZE = 56f;

    public static final RectF AVATAR = new RectF(80.3f, 1411.4f, 190.7f, 1521.8f);
    private static final float PAYER_X = 218.6f;
    private static final float PAYER_BASE = 1484f;
    private static final float PAYER_SIZE = 50f;
    private static final float CONSUMER_RIGHT = 1156f;
    private static final float[] COL_CX = {240f, 639f, 1040f};
    private static final float COL_LABEL_BASE = 1662f;
    private static final float COL_LABEL_SIZE = 50f;
    private static final float COL_VALUE_BASE = 1755.7f;
    private static final float COL_VALUE_SIZE = 62f;
    private static final float COL_COUNT_BASE = 1839.1f;
    private static final float COL_COUNT_SIZE = 50f;
    private static final float[] COL_SEPARATOR = {438f, 838f};
    private static final float COL_SEPARATOR_TOP = 1624f;
    private static final float COL_SEPARATOR_BOTTOM = 1842f;

    private static final String[] COL_LABELS = {"本月消费", "累计消费", "平均单价"};
    private static final float BUTTON_TEXT_SIZE = 66f;

    private static final float CLAIM_BASE = 2610.7f;
    private static final float CLAIM_SIZE = 51f;
    private static final String CLAIM = "收钱码被恶意替换、覆盖可申请理赔";
    private static final float HELP_BASE = 2714.7f;
    private static final float HELP_SIZE = 51f;
    private static final String HELP = "在线帮助";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    private Bitmap fallback;
    private Bitmap coin;

    /** Decodes the grey payer square and the 积分 coin cut out of the reference. */
    public void load(Resources res) {
        fallback = BitmapFactory.decodeResource(res, R.drawable.rec_avatar_sq);
        coin = BitmapFactory.decodeResource(res, R.drawable.rec_coin);
    }

    // ------------------------------------------------------------------ drawing

    public void draw(Canvas c, Receipt item, ReceiptStore.Stats stats) {
        if (item == null) return;
        if (stats == null) stats = new ReceiptStore.Stats();
        c.drawColor(BG);
        drawHeader(c);
        drawAmountCard(c, item);
        drawPictureCard(c, item, stats);
        drawButtons(c);
        drawFooter(c);
    }

    private void drawHeader(Canvas c) {
        stroke(INK, 5f);
        path.reset();
        path.moveTo(84.7f, 190.2f);
        path.lineTo(49.5f, 219.9f);
        path.lineTo(84.7f, 249.6f);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        text(c, "收款详情", TITLE_X, TITLE_BASE, TITLE_SIZE, 0xff222222, true);
    }

    private void drawAmountCard(Canvas c, Receipt item) {
        round(c, CARD1, CARD_RADIUS, CARD);
        centerText(c, "收款金额", W / 2f, LABEL1_BASE, LABEL1_SIZE, GREY, false);
        centerText(c, ReceiptStore.yuan(item.amount), W / 2f, AMOUNT_BASE, AMOUNT_SIZE, 0xff2b2b2b, true);
        centerText(c, "实际到账 " + ReceiptStore.yuan(item.actual), W / 2f, ACTUAL_BASE, ACTUAL_SIZE,
                0xff2b2b2b, false);

        text(c, "收钱有奖", ROW_LABEL_X, ROW_BASE[0], ROW_LABEL_SIZE, ROW_LABEL, false);
        drawArt(c, coin, ROW_COIN);
        text(c, item.prize, ROW1_VALUE_X, ROW_BASE[0], ROW_VALUE_SIZE, ROW_VALUE, false);
        chevron(c, ROW_CHEVRON_TIP, ROW_BASE[0] - 15f, 0xff9a9a9a, 4f);

        text(c, "提现免费额度", ROW_LABEL_X, ROW_BASE[1], ROW_LABEL_SIZE, ROW_LABEL, false);
        textRight(c, "新增 " + ReceiptStore.money(item.quota), ROW_VALUE_RIGHT, ROW_BASE[1],
                ROW_VALUE_SIZE, ROW_VALUE, false);
        chevron(c, ROW_CHEVRON_TIP, ROW_BASE[1] - 15f, 0xff9a9a9a, 4f);

        text(c, "交易说明", ROW_LABEL_X, ROW_BASE[2], ROW_LABEL_SIZE, ROW_LABEL, false);
        textRight(c, item.note, 1198f, ROW_BASE[2], ROW_VALUE_SIZE, ROW_VALUE, false);

        // 更多 and its caret travel together and stay centred on the card
        float moreWidth = textWidth("更多", MORE_SIZE, false);
        float moreLeft = W / 2f - 78f;
        text(c, "更多", moreLeft, MORE_BASE, MORE_SIZE, GREY, false);
        chevronDown(c, moreLeft + moreWidth + 24f, MORE_BASE - 20f, GREY, 4f);
    }

    private void drawPictureCard(Canvas c, Receipt item, ReceiptStore.Stats stats) {
        round(c, CARD2, CARD_RADIUS, CARD);
        Bitmap picture = ReceiptStore.avatar(item);
        if (picture == null) {
            drawArt(c, fallback, AVATAR);
        } else {
            clipRound(c, AVATAR, 15f, picture);
        }
        text(c, item.payer, PAYER_X, PAYER_BASE, PAYER_SIZE, INK, true);
        textRight(c, "消费记录", CONSUMER_RIGHT, PAYER_BASE, 50f, BLUE, false);
        chevron(c, ROW_CHEVRON_TIP, PAYER_BASE - 15f, BLUE, 4f);

        for (float x : COL_SEPARATOR) {
            line(c, x, COL_SEPARATOR_TOP, x, COL_SEPARATOR_BOTTOM, SEPARATOR, 2f);
        }

        double[] values = {monthSpend(item, stats), totalSpend(item, stats), avgPrice(item, stats)};
        int[] counts = {monthCount(item, stats), totalCount(item, stats), -1};
        for (int i = 0; i < COL_CX.length; i++) {
            centerText(c, COL_LABELS[i], COL_CX[i], COL_LABEL_BASE, COL_LABEL_SIZE, GREY, false);
            centerText(c, ReceiptStore.yuan(values[i]), COL_CX[i], COL_VALUE_BASE, COL_VALUE_SIZE,
                    0xff2b2b2b, true);
            if (counts[i] >= 0) {
                centerText(c, "共" + counts[i] + "笔", COL_CX[i], COL_COUNT_BASE, COL_COUNT_SIZE,
                        0xff2b2b2b, false);
            }
        }
    }

    private void drawButtons(Canvas c) {
        round(c, BUTTON1, CARD_RADIUS, CARD);
        centerText(c, "联系顾客", W / 2f, 2077.7f, BUTTON_TEXT_SIZE, BLUE, false);
        round(c, BUTTON2, CARD_RADIUS, CARD);
        centerText(c, "退款", W / 2f, 2272.7f, BUTTON_TEXT_SIZE, BLUE, false);
    }

    private void drawFooter(Canvas c) {
        float width = textWidth(CLAIM, CLAIM_SIZE, false);
        float cx = W / 2f - 16f;
        float x = cx - width / 2f;
        text(c, CLAIM, x, CLAIM_BASE, CLAIM_SIZE, 0xff939393, false);
        chevron(c, x + width + 26f, CLAIM_BASE - 15f, 0xff939393, 4f);
        centerText(c, HELP, W / 2f, HELP_BASE, HELP_SIZE, BLUE, false);
    }

    // ------------------------------------------------------------------ the columns' figures

    private static double monthSpend(Receipt item, ReceiptStore.Stats stats) {
        return item.monthSpend != null ? item.monthSpend : stats.monthSpend;
    }

    private static int monthCount(Receipt item, ReceiptStore.Stats stats) {
        return item.monthCount != null ? item.monthCount : stats.monthCount;
    }

    private static double totalSpend(Receipt item, ReceiptStore.Stats stats) {
        return item.totalSpend != null ? item.totalSpend : stats.totalSpend;
    }

    private static int totalCount(Receipt item, ReceiptStore.Stats stats) {
        return item.totalCount != null ? item.totalCount : stats.totalCount;
    }

    private static double avgPrice(Receipt item, ReceiptStore.Stats stats) {
        if (item.avgPrice != null) return item.avgPrice;
        double total = totalSpend(item, stats);
        int count = totalCount(item, stats);
        return count == 0 ? 0 : total / count;
    }

    // ------------------------------------------------------------------ hit test

    /** Returns an action token for the tap at (x, y) — screen coordinates in design space. */
    public String hitTest(float x, float y) {
        if (y < 300f) return x < 130f ? "back" : "noop";
        if (CARD1.contains(x, y)) {
            if (y < 520f) return "amountLabel";
            if (y < 660f) return "amount";
            if (y < 800f) return "actual";
            if (y < 985f) return "prize";
            if (y < 1078f) return "quota";
            if (y < 1170f) return "note";
            return "more";
        }
        if (CARD2.contains(x, y)) {
            // the payer's row ends well above the 顾客画像 columns; 1580 sits between the two
            if (y < 1580f) {
                if (x < 210f) return "avatar";
                if (x > 940f) return "consumer";
                return "payer";
            }
            int column = x < COL_SEPARATOR[0] ? 0 : x < COL_SEPARATOR[1] ? 1 : 2;
            return column == 0 ? "month" : column == 1 ? "total" : "avg";
        }
        if (BUTTON1.contains(x, y)) return "contact";
        if (BUTTON2.contains(x, y)) return "refund";
        if (y > 2540f && y < 2650f) return "claim";
        if (y > 2650f && y < 2760f) return "help";
        return "noop";
    }

    // ------------------------------------------------------------------ primitives

    private void drawArt(Canvas c, Bitmap bitmap, RectF box) {
        if (bitmap == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        c.drawBitmap(bitmap, null, box, paint);
    }

    /** Draws a picture into a rounded square, centre-cropped — the shape the detail page uses. */
    private void clipRound(Canvas c, RectF box, float radius, Bitmap bitmap) {
        c.save();
        path.reset();
        path.addRoundRect(box, radius, radius, Path.Direction.CW);
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

    private void chevron(Canvas c, float tip, float cy, int colour, float width) {
        stroke(colour, width);
        path.reset();
        path.moveTo(tip - 15f, cy - 13f);
        path.lineTo(tip, cy);
        path.lineTo(tip - 15f, cy + 13f);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void chevronDown(Canvas c, float cx, float cy, int colour, float width) {
        stroke(colour, width);
        path.reset();
        path.moveTo(cx - 11f, cy - 5f);
        path.lineTo(cx, cy + 7f);
        path.lineTo(cx + 11f, cy - 5f);
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
}
