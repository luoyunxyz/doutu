package com.luoyun.doutu.features.pay.ui;

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

/**
 * Native rendition of the “收付款” (payment code) page.
 *
 * <p>Everything is redrawn from primitives at the 1280x2856 canvas of the reference screenshot:
 * the blue sheet, the white code card, the barcode, the QR symbol, the payment-method row, the
 * discount strip and the three shortcut rows. The barcode is generated locally and the QR symbol is
 * the original's own asset
 * so no real payment credential is ever involved — this is a mock, not a wallet.
 */
public final class PayCodeScreen {
    public static final float W = 1280f;
    public static final float H = 2856f;

    /** Mutable presentation state owned by the hosting activity. */
    public static final class State {
        /** 0 = 收付款, 1 = 支付有礼. */
        public int tab;
    }

    private static final int BG_TOP = 0xff3f80f0;
    private static final int BG_BOTTOM = 0xff2a63e6;
    private static final int CARD = 0xffffffff;
    private static final int INK = 0xff3c3c3e;
    private static final int MUTED = 0xffa2a6ad;
    /** The bar ink the reference prints; the barcode is black, not dark grey. */
    private static final int CODE_INK = 0xff000000;
    private static final int CAPSULE = 0xff2a5fd9;
    private static final int CAPSULE_EDGE = 0x33000000;
    private static final int PILL = 0xfff2f0f4;
    private static final int PILL_TEXT = 0xff8b8b93;
    private static final int PANEL = 0xff578ceb;
    private static final int PANEL_LINE = 0x30ffffff;
    private static final int BANNER = 0xfff9ece7;
    private static final int BANNER_BADGE = 0xffe4713c;
    private static final int BANNER_TEXT = 0xffc06a4a;

    // ------------------------------------------------------------------ geometry

    private static final float CARD_L = 46f;
    private static final float CARD_T = 371f;
    private static final float CARD_R = 1237f;
    private static final float CARD_B = 2087f;
    private static final float CARD_RADIUS = 36f;

    private static final float TAB_L = 450f;
    private static final float TAB_R = 830f;
    private static final float TAB_DIP = 404f;

    private static final float VIP_ICON_L = 501f;
    private static final float VIP_ICON_T = 315f;
    private static final float VIP_ICON_SIZE = 56f;
    private static final float VIP_TEXT_X = 570f;
    private static final float VIP_TEXT_BASE = 362f;
    private static final float VIP_TEXT_SIZE = 50f;

    private static final float MERCHANT_ICON_L = 93f;
    private static final float MERCHANT_ICON_T = 417f;
    private static final float MERCHANT_ICON_SIZE = 70f;
    private static final float MERCHANT_TEXT_X = 217f;
    private static final float MERCHANT_TEXT_BASE = 474f;
    private static final float MERCHANT_TEXT_SIZE = 58f;

    private static final float BARCODE_L = 78f;
    private static final float BARCODE_R = 1215f;
    private static final float BARCODE_T = 545f;
    private static final float BARCODE_B = 875f;

    private static final float CAPTION_BASE = 958f;
    private static final float CAPTION_SIZE = 49f;

    private static final float QR_L = 361f;
    private static final float QR_T = 1075f;
    private static final float QR_SIZE = 560f;
    /** The 支 badge cut out of the reference, pasted over an otherwise complete symbol. */

    private static final float BALANCE_L = 88f;
    private static final float BALANCE_T = 1795f;
    private static final float BALANCE_SIZE = 80f;
    private static final float ACCOUNT_TEXT_X = 214f;
    private static final float ACCOUNT_TITLE_BASE = 1830f;
    private static final float ACCOUNT_TITLE_SIZE = 62f;
    private static final float ACCOUNT_SUB_BASE = 1898f;
    private static final float ACCOUNT_SUB_SIZE = 46f;
    private static final float ACCOUNT_CHEVRON_X = 1162f;
    private static final float ACCOUNT_CHEVRON_Y = 1838f;

    private static final float BANNER_T = 1945f;
    private static final float BANNER_BADGE_L = 91f;
    private static final float BANNER_BADGE_R = 323f;
    private static final float BANNER_BADGE_T = 1978f;
    private static final float BANNER_BADGE_B = 2045f;
    private static final float BANNER_TEXT_X = 372f;
    private static final float BANNER_TEXT_BASE = 2033f;
    private static final float BANNER_TEXT_SIZE = 43f;

    private static final float PANEL_L = 48f;
    private static final float PANEL_T = 2096f;
    private static final float PANEL_R = 1238f;
    private static final float PANEL_B = 2648f;
    private static final float PANEL_RADIUS = 30f;
    private static final float ROW_STEP = 174f;
    private static final float ROW_FIRST_CY = 2204f;
    private static final float ROW_ICON_CX = 130f;
    private static final float ROW_TEXT_X = 217f;
    private static final float ROW_TEXT_SIZE = 54f;
    private static final float ROW_CHEVRON_X = 1162f;
    private static final float ROW_CHEVRON_H = 46f;
    private static final float ROW_DIVIDER_L = 128f;

    private static final float CAPSULE_L = 385f;
    private static final float CAPSULE_R = 890f;
    private static final float CAPSULE_T = 163f;
    private static final float CAPSULE_B = 285f;
    private static final float PILL_L = 390f;
    private static final float PILL_R = 637f;
    private static final float PILL_L_SECOND = 640f;
    private static final float PILL_R_SECOND = 886f;
    private static final float PILL_T = 168f;
    private static final float PILL_B = 280f;
    private static final float TAB_TEXT_BASE = 244f;
    private static final float TAB_TEXT_SIZE = 45f;
    private static final float TAB_FIRST_CX = 513f;
    private static final float TAB_SECOND_CX = 772f;

    private static final String[] ROW_LABELS = {"收钱", "碰一下", "转账"};
    private static final String[] ROW_ART = {"collect", "tap", "transfer"};
    /** Natural size of each cut-out, so nothing is stretched. */
    private static final float[] ROW_ICON_BOX = {72f, 70f, 80f, 48f, 70f, 73f};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final java.util.Map<String, Bitmap> art = new java.util.LinkedHashMap<>();
    private LinearGradient bg;
    private Path barcode;
    private long seed = 0x5eed1234abcdL;

    public void load(Resources res) {
        art.clear();
        art.put("vip", BitmapFactory.decodeResource(res, R.drawable.pay_icon_vip));
        art.put("collect", BitmapFactory.decodeResource(res, R.drawable.pay_icon_collect));
        art.put("tap", BitmapFactory.decodeResource(res, R.drawable.pay_icon_tap));
        art.put("transfer", BitmapFactory.decodeResource(res, R.drawable.pay_icon_transfer));
        art.put("balance", BitmapFactory.decodeResource(res, R.drawable.pay_icon_balance));
        art.put("qrCode", BitmapFactory.decodeResource(res, R.drawable.pay_code_qr));
        art.put("merchant", BitmapFactory.decodeResource(res, R.drawable.pay_icon_merchant));
        // Each visit draws a different, meaningless barcode.
        seed = System.nanoTime() | 1L;
    }

    // ------------------------------------------------------------------ drawing

    public void draw(Canvas c, State s) {
        if (bg == null) {
            bg = new LinearGradient(0, 0, 0, H, BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP);
        }
        paint.setShader(bg);
        c.drawRect(0, 0, W, H, paint);
        paint.setShader(null);

        drawCard(c);
        if (s.tab == 0) {
            drawMerchantRow(c);
            drawBarcode(c);
            drawCaption(c);
            drawQr(c);
            drawAccountRow(c);
            drawDiscount(c);
        } else {
            centerText(c, "支付有礼 · 敬请期待", W / 2f, CARD_T + 300f, 48, MUTED, false);
        }
        drawShortcuts(c);
        drawTopBar(c, s);
    }

    /** The white card plus the member tab that dips out of its top edge. */
    private void drawCard(Canvas c) {
        r.set(CARD_L, CARD_T, CARD_R, CARD_B);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(CARD);
        c.drawRoundRect(r, CARD_RADIUS, CARD_RADIUS, paint);

        // The tab is the blue sheet flowing a little further down, so it is painted with the
        // same gradient and disappears into the header above the card.
        path.reset();
        path.moveTo(TAB_L, CARD_T);
        path.cubicTo(TAB_L + 80f, TAB_DIP, TAB_R - 80f, TAB_DIP, TAB_R, CARD_T);
        path.close();
        paint.setShader(bg);
        c.drawPath(path, paint);
        paint.setShader(null);

        drawArt(c, "vip", VIP_ICON_L, VIP_ICON_T, VIP_ICON_SIZE, VIP_ICON_SIZE);
        text(c, "大众会员", VIP_TEXT_X, VIP_TEXT_BASE, VIP_TEXT_SIZE, Color.WHITE, false);
    }

    private void drawMerchantRow(Canvas c) {
        drawArt(c, "merchant", MERCHANT_ICON_L, MERCHANT_ICON_T,
                MERCHANT_ICON_SIZE, MERCHANT_ICON_SIZE);
        text(c, "向商家付款", MERCHANT_TEXT_X, MERCHANT_TEXT_BASE, MERCHANT_TEXT_SIZE, INK, false);
    }

    private void drawBarcode(Canvas c) {
        ensureBarcode();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(CODE_INK);
        // Squares are axis-aligned; anti-aliasing would only add hairline seams.
        paint.setAntiAlias(false);
        c.drawPath(barcode, paint);
        paint.setAntiAlias(true);
    }

    private void drawCaption(Canvas c) {
        centerText(c, "点击可查看付款码数字", W / 2f, CAPTION_BASE, CAPTION_SIZE, MUTED, false);
    }

    private void drawQr(Canvas c) {
        // The symbol is the original's own asset — a real code with Alipay's badge printed inside
        // it — rather than a module field invented here, so the page carries the code the original
        // carries. The badge comes with it, which is why nothing is laid over the middle.
        Bitmap code = art.get("qrCode");
        if (code == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffffffff);
        paint.setAntiAlias(false);
        r.set(QR_L, QR_T, QR_L + QR_SIZE, QR_T + QR_SIZE);
        c.drawRect(r, paint);
        paint.setAntiAlias(true);
        c.drawBitmap(code, null, r, paint);
    }

    private void drawAccountRow(Canvas c) {
        drawArt(c, "balance", BALANCE_L, BALANCE_T, BALANCE_SIZE, BALANCE_SIZE);
        text(c, "账户余额", ACCOUNT_TEXT_X, ACCOUNT_TITLE_BASE, ACCOUNT_TITLE_SIZE, INK, false);
        text(c, "优先使用此付款方式", ACCOUNT_TEXT_X, ACCOUNT_SUB_BASE, ACCOUNT_SUB_SIZE, MUTED, false);
        chevron(c, ACCOUNT_CHEVRON_X, ACCOUNT_CHEVRON_Y, 0xffc2c6cc, 24f, 4.5f);
    }

    private void drawDiscount(Canvas c) {
        // Rounded only where the card is rounded, so the strip follows the card's bottom.
        path.reset();
        path.moveTo(CARD_L, BANNER_T);
        path.lineTo(CARD_R, BANNER_T);
        path.lineTo(CARD_R, CARD_B - CARD_RADIUS);
        path.quadTo(CARD_R, CARD_B, CARD_R - CARD_RADIUS, CARD_B);
        path.lineTo(CARD_L + CARD_RADIUS, CARD_B);
        path.quadTo(CARD_L, CARD_B, CARD_L, CARD_B - CARD_RADIUS);
        path.close();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(BANNER);
        c.drawPath(path, paint);

        r.set(BANNER_BADGE_L, BANNER_BADGE_T, BANNER_BADGE_R, BANNER_BADGE_B);
        paint.setColor(BANNER_BADGE);
        c.drawRoundRect(r, 10f, 10f, paint);
        centerText(c, "百次立减", (BANNER_BADGE_L + BANNER_BADGE_R) / 2f, 2033f, 42,
                Color.WHITE, true);
        text(c, "优惠生效中，本单可立减", BANNER_TEXT_X, BANNER_TEXT_BASE, BANNER_TEXT_SIZE,
                BANNER_TEXT, false);
    }

    private void drawShortcuts(Canvas c) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PANEL);
        r.set(PANEL_L, PANEL_T, PANEL_R, PANEL_B);
        c.drawRoundRect(r, PANEL_RADIUS, PANEL_RADIUS, paint);

        for (int i = 1; i < ROW_LABELS.length; i++) {
            float y = ROW_FIRST_CY + ROW_STEP * i - ROW_STEP / 2f;
            line(c, ROW_DIVIDER_L, y, PANEL_R - 10f, y, PANEL_LINE, 2f);
        }
        for (int i = 0; i < ROW_LABELS.length; i++) {
            float cy = ROW_FIRST_CY + ROW_STEP * i;
            float w = ROW_ICON_BOX[i * 2];
            float h = ROW_ICON_BOX[i * 2 + 1];
            drawArt(c, ROW_ART[i], ROW_ICON_CX - w / 2f, cy - h / 2f, w, h);
            text(c, ROW_LABELS[i], ROW_TEXT_X, cy + 19f, ROW_TEXT_SIZE, Color.WHITE, false);
            chevron(c, ROW_CHEVRON_X, cy, Color.WHITE, 20f, 5f);
        }
    }

    private void drawTopBar(Canvas c, State s) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(CAPSULE);
        r.set(CAPSULE_L, CAPSULE_T, CAPSULE_R, CAPSULE_B);
        c.drawRoundRect(r, (CAPSULE_B - CAPSULE_T) / 2f, (CAPSULE_B - CAPSULE_T) / 2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(CAPSULE_EDGE);
        c.drawRoundRect(r, (CAPSULE_B - CAPSULE_T) / 2f, (CAPSULE_B - CAPSULE_T) / 2f, paint);
        paint.setStyle(Paint.Style.FILL);

        // The light pill sits under whichever tab is selected.
        float pillL = s.tab == 0 ? PILL_L : PILL_L_SECOND;
        float pillR = s.tab == 0 ? PILL_R : PILL_R_SECOND;
        r.set(pillL, PILL_T, pillR, PILL_B);
        paint.setColor(PILL);
        c.drawRoundRect(r, (PILL_B - PILL_T) / 2f, (PILL_B - PILL_T) / 2f, paint);

        centerText(c, "收付款", TAB_FIRST_CX, TAB_TEXT_BASE, TAB_TEXT_SIZE,
                s.tab == 0 ? PILL_TEXT : Color.WHITE, false);
        centerText(c, "支付有礼", TAB_SECOND_CX, TAB_TEXT_BASE, TAB_TEXT_SIZE,
                s.tab == 1 ? PILL_TEXT : Color.WHITE, false);

        line(c, 104f, 197f, 63f, 225f, Color.WHITE, 7f);
        line(c, 63f, 225f, 104f, 253f, Color.WHITE, 7f);
        for (int i = 0; i < 3; i++) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            c.drawCircle(1172f + i * 20f, 225f, 5f, paint);
        }
    }

    // ------------------------------------------------------------------ asset building

    /** A barcode-looking strip: deterministic, and not tied to any real account. */
    private void ensureBarcode() {
        if (barcode != null) return;
        Path p = new Path();
        long state = seed ^ 0x2545f4914f6cdd1dL;
        float unit = 5.4f;
        float x = BARCODE_L;
        while (true) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            float bar = 1f + (float) ((state >>> 40) % 3);
            state = state * 6364136223846793005L + 1442695040888963407L;
            float gap = 3f + (float) ((state >>> 40) % 4);
            float right = x + bar * unit;
            if (right > BARCODE_R - 2f) break;
            p.addRect(x, BARCODE_T, right, BARCODE_B, Path.Direction.CW);
            x = right + gap * unit;
        }
        barcode = p;
    }

    // ------------------------------------------------------------------ hit test

    /** Returns an action token for the tap at (x, y) in design coordinates. */
    public String hitTest(State s, float x, float y) {
        if (y < 100f) return "noop";
        if (y < 320f) {
            if (x < 150f) return "back";
            if (x > 1100f) return "more";
            if (x > CAPSULE_L && x < CAPSULE_R) {
                return x < (CAPSULE_L + CAPSULE_R) / 2f ? "tab:0" : "tab:1";
            }
            return "noop";
        }
        if (s.tab == 0) {
            if (x > 150f && x < 600f && y > 900f && y < 1000f) return "numbers";
            if (y > 1780f && y < 1920f && x > 46f && x < CARD_R) return "account";
            if (y > BANNER_T && y < CARD_B) return "offer";
        }
        if (y > PANEL_T && y < PANEL_B) {
            int row = Math.round((y - ROW_FIRST_CY) / ROW_STEP);
            if (row >= 0 && row < ROW_LABELS.length) return "row:" + ROW_LABELS[row];
        }
        return "noop";
    }

    // ------------------------------------------------------------------ primitives

    private void drawArt(Canvas c, String name, float left, float top, float w, float h) {
        Bitmap b = art.get(name);
        if (b == null) return;
        int color = paint.getColor();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        // Bitmaps are modulated by the paint's alpha, so it has to be opaque here.
        paint.setColor(Color.WHITE);
        r.set(left, top, left + w, top + h);
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

    private void centerText(Canvas c, String value, float cx, float baseline, float size, int color,
                            boolean bold) {
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
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color);
        c.drawLine(x1, y1, x2, y2, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void chevron(Canvas c, float x, float y, int color, float halfH, float width) {
        line(c, x - halfH * 0.45f, y - halfH, x + halfH * 0.45f, y, color, width);
        line(c, x + halfH * 0.45f, y, x - halfH * 0.45f, y + halfH, color, width);
    }
}
