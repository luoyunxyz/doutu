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
import com.luoyun.doutu.features.zhi.data.WithdrawStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 选择提现银行卡：the sheet the 提现 page's 到账银行卡 row raises.
 *
 * <p>Rebuilt from {@code docs/reference/withdraw/bank-sheet-reference.jpg}: everything above
 * {@link #SHEET_TOP} is dimmed to three quarters black (the reference's own wash, which leaves the
 * page's header alone), the sheet itself is the page's own pale grey with rounded top corners, the
 * four rows are one white block with a hairline rule between them, the chosen card wears a blue tick
 * and the last row — 添加储蓄卡提现 — is the orange card and the › the capture shows.
 *
 * <p>Rows are laid out evenly, so a card the user adds simply takes its place in the block; the
 * 添加 row keeps its own, shorter height.
 */
public final class BankSheet {
    /** 参考图里变暗的部分从这一行开始（页头不在里面）。 */
    private static final float DIM_TOP = 306f;
    private static final int DIM = 0xbf000000;

    private static final float SHEET_TOP = 1530.7f;
    private static final float SHEET_RADIUS = 30f;
    private static final float TITLE_CX = 624f;
    private static final float TITLE_BASE = 1631.9f;
    private static final float TITLE_SIZE = 65f;
    private static final RectF CLOSE = new RectF(1166.6f, 1582.4f, 1225.1f, 1637.1f);
    private static final float CLOSE_STROKE = 4.4f;

    private static final float BLOCK_L = 46.8f;
    private static final float BLOCK_R = 1232.7f;
    private static final float BLOCK_TOP = 1746.8f;
    private static final float BLOCK_RADIUS = 40f;
    private static final float RULE_L = 250.7f;
    private static final float RULE_R = 1196.2f;
    private static final float RULE_H = 2f;

    /** 一张卡的行的标准高度，以及「添加储蓄卡提现」自己那一行的。 */
    private static final float CARD_ROW_H = 219.9f;
    private static final float ADD_ROW_H = 177.3f;
    /** 提现列表最多铺到这里，卡多了就把每行压扁一点。 */
    private static final float BLOCK_BOTTOM_LIMIT = 2740f;

    private static final float ICON_L = 95.7f;
    private static final float ICON_TOP = 40.6f;
    private static final float ICON_SIZE = 61.2f;
    private static final float NAME_X = 198f;
    private static final float NAME_BASE = 90.3f;
    private static final float NAME_SIZE = 55f;
    private static final float SUB_X = 204f;
    private static final float SUB_BASE = 159.8f;
    private static final float SUB_SIZE = 47.5f;
    private static final RectF TICK = new RectF(1136.6f, 50.8f, 1193.1f, 94.4f);
    /** 参考图里「添加储蓄卡提现」那一行的顶边，下面几个偏移都是相对它量的。 */
    private static final float ADD_ROW_SEED = 2404.3f;
    private static final RectF ADD_ICON = new RectF(92.7f, 2469.7f - ADD_ROW_SEED,
            158.6f, 2518.9f - ADD_ROW_SEED);
    private static final float ADD_NAME_BASE = 2509.6f - ADD_ROW_SEED;
    private static final RectF ADD_CHEVRON = new RectF(1152.1f, 2472.2f - ADD_ROW_SEED,
            1177.2f, 2513.9f - ADD_ROW_SEED);
    private static final float CHEVRON_STROKE = 4.6f;

    private static final int INK = 0xff2a2c30;
    private static final int CAPTION = 0xff9b9ca0;
    private static final int OFF_NAME = 0xffafafaf;
    private static final int OFF_SUB = 0xffc6c8cb;
    private static final int RULE = 0xffeeeeee;
    private static final int CHEVRON = 0xffb9bcc2;
    private static final int SHEET_BG = 0xfff2f3f7;
    private static final int FALLBACK = 0xffeef1f6;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private Bitmap tick;
    private Bitmap addLogo;

    /** 每一帧登记的可点击热区。 */
    private final List<float[]> hitRects = new ArrayList<>();
    private final List<String> hitTokens = new ArrayList<>();
    /** 这一次画出来的行高，命中判定要用同一份。 */
    private float cardRowH = CARD_ROW_H;

    public void load(Resources res) {
        tick = BitmapFactory.decodeResource(res, R.drawable.wd_check);
        addLogo = BitmapFactory.decodeResource(res, R.drawable.wd_icon_add);
    }

    public void draw(Canvas c, List<WithdrawStore.Card> cards, long selected, Bitmap[] logos) {
        hitRects.clear();
        hitTokens.clear();

        // 上面那层变暗
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(DIM);
        c.drawRect(0, DIM_TOP, WithdrawScreen.W, WithdrawScreen.H, paint);

        // 弹窗本体：上半截是页底色，下面那块才是白的
        paint.setColor(SHEET_BG);
        r.set(0, SHEET_TOP, WithdrawScreen.W, WithdrawScreen.H);
        c.drawRoundRect(r, SHEET_RADIUS, SHEET_RADIUS, paint);

        centre(c, "选择提现银行卡", TITLE_CX, TITLE_BASE, TITLE_SIZE, INK, true);
        cross(c, CLOSE, CLOSE_STROKE, 0xffaeafb3);
        hit(CLOSE.left - 16, CLOSE.top - 16, CLOSE.right + 16, CLOSE.bottom + 16, "close");

        int count = Math.max(1, cards == null ? 0 : cards.size());
        float room = BLOCK_BOTTOM_LIMIT - BLOCK_TOP - ADD_ROW_H;
        cardRowH = Math.min(CARD_ROW_H, Math.max(120f, room / count));
        float blockBottom = BLOCK_TOP + count * cardRowH + ADD_ROW_H;
        roundRect(c, BLOCK_L, BLOCK_TOP, BLOCK_R, blockBottom, BLOCK_RADIUS, 0xffffffff);

        for (int i = 0; i < count; i++) {
            WithdrawStore.Card card = cards.get(i);
            float top = BLOCK_TOP + i * cardRowH;
            float scale = cardRowH / CARD_ROW_H;
            Bitmap logo = logos != null && i < logos.length ? logos[i] : null;
            r.set(ICON_L, top + ICON_TOP * scale, ICON_L + ICON_SIZE, top + ICON_TOP * scale + ICON_SIZE);
            if (logo != null) {
                drawLogo(c, logo, r, WithdrawStore.BESPOKE.equals(card.bank));
            } else {
                paint.setColor(FALLBACK);
                c.drawCircle(r.centerX(), r.centerY(), r.width() / 2f, paint);
            }
            boolean usable = card.usable;
            text(c, card.title(), NAME_X, top + NAME_BASE * scale, NAME_SIZE,
                    usable ? INK : OFF_NAME, false);
            text(c, card.arrival, SUB_X, top + SUB_BASE * scale, SUB_SIZE,
                    usable ? CAPTION : OFF_SUB, false);
            if (usable && card.id == selected && tick != null) {
                r.set(TICK.left, top + TICK.top * scale, TICK.right, top + TICK.top * scale
                        + TICK.height() * scale);
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setAlpha(255);
                c.drawBitmap(tick, null, r, paint);
            }
            hit(BLOCK_L, top, BLOCK_R, top + cardRowH, "pick:" + i);
        }

        // 每一张卡下面一条细线，最后那条落在「添加储蓄卡提现」上面
        for (int i = 1; i <= count; i++) {
            float y = BLOCK_TOP + i * cardRowH;
            paint.setColor(RULE);
            c.drawRect(RULE_L, y - RULE_H, RULE_R, y, paint);
        }

        float addTop = BLOCK_TOP + count * cardRowH;
        if (addLogo != null) {
            r.set(ADD_ICON.left, addTop + ADD_ICON.top, ADD_ICON.right, addTop + ADD_ICON.bottom);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(255);
            c.drawBitmap(addLogo, null, r, paint);
        }
        text(c, "添加储蓄卡提现", NAME_X, addTop + ADD_NAME_BASE, NAME_SIZE, INK, false);
        chevron(c, ADD_CHEVRON.left, addTop + ADD_CHEVRON.top,
                ADD_CHEVRON.right, addTop + ADD_CHEVRON.bottom);
        hit(BLOCK_L, addTop, BLOCK_R, blockBottom, "add");
    }

    // ------------------------------------------------------------------ 热区
    private void hit(float l, float t, float rr, float b, String token) {
        hitRects.add(new float[]{l, t, rr, b});
        hitTokens.add(token);
    }

    /** 返回 (x, y) 上的动作 token；坐标是屏幕设计坐标。 */
    public String hitTest(float x, float y) {
        for (int i = hitRects.size() - 1; i >= 0; i--) {
            float[] b = hitRects.get(i);
            if (x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]) return hitTokens.get(i);
        }
        // 弹窗上面那层变暗的地方点一下就收起来
        return y < SHEET_TOP ? "close" : "noop";
    }

    /** 这是哪一行：给长按菜单用，返回 -1 表示不是卡片行。 */
    public int rowAt(List<WithdrawStore.Card> cards, float y) {
        int count = cards == null ? 0 : cards.size();
        for (int i = 0; i < count; i++) {
            float top = BLOCK_TOP + i * cardRowH;
            if (y >= top && y <= top + cardRowH) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ 图元
    /** 一张图标：银行那几张是方的，用户自己上传的按短边放大裁成方的（和模块里别的头像一样）。 */
    private void drawLogo(Canvas c, Bitmap bmp, RectF box, boolean cover) {
        float scale = cover
                ? Math.max(box.width() / bmp.getWidth(), box.height() / bmp.getHeight())
                : Math.min(box.width() / bmp.getWidth(), box.height() / bmp.getHeight());
        float w = bmp.getWidth() * scale;
        float h = bmp.getHeight() * scale;
        float cx = box.centerX();
        float cy = box.centerY();
        r.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        if (cover) {
            int save = c.save();
            path.reset();
            path.addRoundRect(box, box.width() / 2f, box.width() / 2f, Path.Direction.CW);
            c.clipPath(path);
            c.drawBitmap(bmp, null, r, paint);
            c.restoreToCount(save);
        } else {
            c.drawBitmap(bmp, null, r, paint);
        }
    }

    private void roundRect(Canvas c, float l, float t, float rr, float b, float radius, int colour) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colour);
        r.set(l, t, rr, b);
        c.drawRoundRect(r, radius, radius, paint);
    }

    private void cross(Canvas c, RectF box, float stroke, int colour) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(colour);
        float inset = stroke / 2f;
        path.reset();
        path.moveTo(box.left + inset, box.top + inset);
        path.lineTo(box.right - inset, box.bottom - inset);
        path.moveTo(box.right - inset, box.top + inset);
        path.lineTo(box.left + inset, box.bottom - inset);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void chevron(Canvas c, float l, float t, float rr, float b) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(CHEVRON_STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(CHEVRON);
        float inset = CHEVRON_STROKE / 2f;
        path.reset();
        path.moveTo(l + inset, t + inset);
        path.lineTo(rr - inset, (t + b) / 2f);
        path.lineTo(l + inset, b - inset);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private static final float TEXT_SQUEEZE = 0.94f;

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

    private void centre(Canvas c, String value, float cx, float baseline, float size, int colour,
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
}
