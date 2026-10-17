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
import com.luoyun.doutu.features.zhi.data.ReceiptStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 提交成功：the page a withdrawal ends on, rebuilt from {@code withdraw-done-reference.jpg} — which
 * is not a 1380-wide capture like the others but the design canvas itself, so every number here is
 * the reference's own pixel.
 *
 * <p>The blue sheet runs from the very top (under the status bar) down past the white card and then
 * fades into the page's own pale grey, which is why the sheet is drawn as one multi-stop gradient:
 * the reference darkens from #3B96FF at the top to #1778FD where the card starts and then lightens
 * back to the page's #F2F3F7 by y 1460. On it sit the 支 mark, 提交成功, 预计2小时内到账, the
 * amount, and the three 到账银行卡 / 提现金额 / 服务费 rows, all of them white except the pale blue
 * 预计… line; below them the white card with its list icon and 查看提现到账进度, and at the bottom of
 * the page the 完成 button, drawn as the reference's own left-to-right blue gradient.
 */
public final class WithdrawDoneScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;

    private static final float TEXT_SQUEEZE = 0.94f;

    private static final int PAGE_BG = 0xfff2f3f7;
    private static final int CARD = 0xffffffff;
    private static final int WHITE = 0xffffffff;
    private static final int SUBTLE = 0xffb4d6ff;
    private static final int CARD_INK = 0xff313131;
    private static final int CARD_SUB = 0xff9d9d9d;
    private static final int CHEVRON = 0xffb9bcc2;
    private static final int DISC = 0xfff7f8fa;
    private static final int ORANGE = 0xfffca51a;

    /** 蓝面：从顶上一直铺到白卡下面，再淡出到页底色。 */
    private static final float SHEET_H = 1460f;
    private static final int[] SHEET_COLOURS = {
            0xff3b96ff, 0xff2f8aff, 0xff2781ff, 0xff1d78fd, 0xff1778fd,
            0xff2c84ff, 0xff4892fd, 0xff75adfa, 0xffa8c8f9, 0xffd9e6f9, 0xfff2f3f7};
    private static final float[] SHEET_STOPS = {
            0f, 300f / SHEET_H, 600f / SHEET_H, 900f / SHEET_H, 1000f / SHEET_H,
            1050f / SHEET_H, 1100f / SHEET_H, 1200f / SHEET_H, 1300f / SHEET_H,
            1400f / SHEET_H, 1f};

    private static final float DONE_BASE = 242f;
    private static final float DONE_SIZE = 61.5f;
    private static final float DONE_RIGHT = 1234f;

    private static final RectF MARK = new RectF(594f, 311f, 683f, 400f);
    private static final float TITLE_BASE = 474f;
    private static final float TITLE_SIZE = 54f;
    private static final float SUB_BASE = 571f;
    private static final float SUB_SIZE = 44f;
    private static final float AMOUNT_BASE = 755f;
    private static final float AMOUNT_SIZE = 158f;

    private static final float ROW_LABEL_X = 81f;
    private static final float ROW_VALUE_RIGHT = 1198f;
    private static final float ROW_SIZE = 51f;
    private static final float[] ROW_BASE = {884f, 982f, 1071f};

    private static final RectF CARD_BOX = new RectF(41f, 1134f, 1237f, 1432f);
    private static final float CARD_RADIUS = 47f;
    private static final float DISC_R = 101f;
    private static final float DISC_CX = 189f;
    private static final float DISC_CY = 1281f;
    private static final RectF ICON = new RectF(135f, 1231f, 232f, 1328f);
    private static final float ICON_RADIUS = 16f;
    private static final float ICON_BAR_H = 10f;
    private static final float ICON_BAR_R = 5f;
    private static final float CARD_TITLE_X = 323f;
    private static final float CARD_TITLE_BASE = 1271f;
    private static final float CARD_TITLE_SIZE = 58f;
    private static final float CARD_SUB_BASE = 1335f;
    private static final float CARD_SUB_SIZE = 47f;
    private static final RectF CARD_CHEVRON = new RectF(1165f, 1266f, 1183f, 1299f);

    private static final RectF BUTTON = new RectF(240f, 2560f, 1038f, 2710f);
    private static final float BUTTON_RADIUS = 75f;
    private static final int BUTTON_L = 0xff119dff;
    private static final int BUTTON_R = 0xff1879fe;
    private static final float BUTTON_TEXT_BASE = 2657f;
    private static final float BUTTON_TEXT_SIZE = 60f;

    private static final float CHEVRON_STROKE = 4.6f;

    private final java.util.LinkedHashMap<String, Bitmap> art = new java.util.LinkedHashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();

    private final List<float[]> hitRects = new ArrayList<>();
    private final List<String> hitTokens = new ArrayList<>();

    public void load(Resources res) {
        art.clear();
        art.put("mark", BitmapFactory.decodeResource(res, R.drawable.wd_done_mark));
    }

    /**
     * 画这一页。
     *
     * @param amount  提现多少
     * @param bank    到账银行卡的名字加尾号，参考图上这一页写的是「招商银行储蓄卡(4646)」——括号前没空格
     */
    public void draw(Canvas c, double amount, String bank) {
        hitRects.clear();
        hitTokens.clear();
        c.drawColor(PAGE_BG);

        paint.setShader(new LinearGradient(0, 0, 0, SHEET_H, SHEET_COLOURS, SHEET_STOPS,
                Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, W, SHEET_H, paint);
        paint.setShader(null);

        textRight(c, "完成", DONE_RIGHT, DONE_BASE, DONE_SIZE, WHITE, false);
        hit(DONE_RIGHT - 160, DONE_BASE - 60, DONE_RIGHT, DONE_BASE + 18, "finish");

        drawArt(c, "mark", MARK);
        centerText(c, "提交成功", W / 2f - 2, TITLE_BASE, TITLE_SIZE, WHITE, false);
        centerText(c, "预计2小时内到账", W / 2f - 1, SUB_BASE, SUB_SIZE, SUBTLE, false);
        centerText(c, "¥" + ReceiptStore.money(amount), W / 2f - 6, AMOUNT_BASE, AMOUNT_SIZE,
                WHITE, true);

        text(c, "到账银行卡", ROW_LABEL_X, ROW_BASE[0], ROW_SIZE, WHITE, false);
        textRight(c, bank, ROW_VALUE_RIGHT, ROW_BASE[0], ROW_SIZE, WHITE, false);
        text(c, "提现金额", ROW_LABEL_X, ROW_BASE[1], ROW_SIZE, WHITE, false);
        textRight(c, "¥" + ReceiptStore.money(amount), ROW_VALUE_RIGHT, ROW_BASE[1], ROW_SIZE,
                WHITE, false);
        text(c, "服务费", ROW_LABEL_X, ROW_BASE[2], ROW_SIZE, WHITE, false);
        textRight(c, "本次提现免费", ROW_VALUE_RIGHT, ROW_BASE[2], ROW_SIZE, WHITE, false);

        roundRect(c, CARD_BOX, CARD_RADIUS, CARD);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(DISC);
        c.drawCircle(DISC_CX, DISC_CY, DISC_R, paint);
        drawIcon(c);
        text(c, "查看提现到账进度", CARD_TITLE_X, CARD_TITLE_BASE, CARD_TITLE_SIZE, CARD_INK, false);
        text(c, "可在「我的-账单」查看进度", CARD_TITLE_X + 1, CARD_SUB_BASE, CARD_SUB_SIZE,
                CARD_SUB, false);
        chevron(c, CARD_CHEVRON, CHEVRON_STROKE, CHEVRON);
        hit(CARD_BOX.left, CARD_BOX.top, CARD_BOX.right, CARD_BOX.bottom, "progress");

        paint.setShader(new LinearGradient(BUTTON.left, 0, BUTTON.right, 0, BUTTON_L, BUTTON_R,
                Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        c.drawRoundRect(BUTTON, BUTTON_RADIUS, BUTTON_RADIUS, paint);
        paint.setShader(null);
        centerText(c, "完成", BUTTON.centerX(), BUTTON_TEXT_BASE, BUTTON_TEXT_SIZE, WHITE, false);
        hit(BUTTON.left, BUTTON.top, BUTTON.right, BUTTON.bottom, "finish");
    }

    /** 白卡里那个橙色的清单图标：一个圆角方块加三条横杠。 */
    private void drawIcon(Canvas c) {
        roundRect(c, ICON, ICON_RADIUS, ORANGE);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(WHITE);
        float l = ICON.left + 20f;
        float rr = ICON.right - 20f;
        float[] ys = {ICON.top + 26f, ICON.centerY(), ICON.bottom - 26f};
        for (float y : ys) {
            r.set(l, y - ICON_BAR_H / 2f, rr, y + ICON_BAR_H / 2f);
            c.drawRoundRect(r, ICON_BAR_R, ICON_BAR_R, paint);
        }
    }

    /** 返回落在 (x, y) 上的动作 token；坐标是屏幕设计坐标。 */
    public String hitTest(float x, float y) {
        for (int i = hitRects.size() - 1; i >= 0; i--) {
            float[] b = hitRects.get(i);
            if (x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]) return hitTokens.get(i);
        }
        return "noop";
    }

    // ------------------------------------------------------------------ 图元
    private void hit(float l, float t, float rr, float b, String token) {
        hitRects.add(new float[]{l, t, rr, b});
        hitTokens.add(token);
    }

    private void drawArt(Canvas c, String name, RectF box) {
        Bitmap bmp = art.get(name);
        if (bmp == null || box == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        c.drawBitmap(bmp, null, box, paint);
    }

    private void roundRect(Canvas c, RectF box, float radius, int colour) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colour);
        c.drawRoundRect(box, radius, radius, paint);
    }

    private void chevron(Canvas c, RectF box, float stroke, int colour) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(colour);
        float inset = stroke / 2f;
        path.reset();
        path.moveTo(box.left + inset, box.top + inset);
        path.lineTo(box.right - inset, box.centerY());
        path.lineTo(box.left + inset, box.bottom - inset);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    // ------------------------------------------------------------------ 文字
    private void text(Canvas c, String value, float x, float baseline, float size, int colour,
                      boolean bold) {
        if (value == null || value.isEmpty()) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colour);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        c.save();
        c.scale(TEXT_SQUEEZE, 1f, x, 0f);
        c.drawText(value, x, baseline, paint);
        c.restore();
    }

    private void textRight(Canvas c, String value, float right, float baseline, float size, int colour,
                           boolean bold) {
        if (value == null || value.isEmpty()) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colour);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.RIGHT);
        c.save();
        c.scale(TEXT_SQUEEZE, 1f, right, 0f);
        c.drawText(value, right, baseline, paint);
        c.restore();
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void centerText(Canvas c, String value, float cx, float baseline, float size, int colour,
                            boolean bold) {
        if (value == null || value.isEmpty()) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colour);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        c.save();
        c.scale(TEXT_SQUEEZE, 1f, cx, 0f);
        c.drawText(value, cx, baseline, paint);
        c.restore();
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** 提交成功那一页的背景色，宿主设系统栏时用得上。 */
    public static int sheetColour() {
        return SHEET_COLOURS[0];
    }

    /** 这一页不滚动。 */
    public static float contentHeight() {
        return H;
    }

    private static final int UNUSED = Color.TRANSPARENT;
}
