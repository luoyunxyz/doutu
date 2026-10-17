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
import com.luoyun.doutu.features.zhi.data.ProfileStore;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Native rendition of the 个人信息 page, reached from the identity row at the top of the 我的 tab.
 *
 * <p>The card that carries the avatar, the nickname and the account number is drawn here, because
 * all three answer the user; the two cards below it are static and travel as cut-outs. Only the
 * back arrow, the headset, the 名片 glyph and the row chevron come from the reference as artwork.
 */
public final class ProfileScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;

    /** Mutable presentation state owned by the hosting activity. */
    public static final class State {
    }

    // ------------------------------------------------------------------ palette
    private static final int PAGE_BG = 0xfff5f5f5;
    private static final int CARD = 0xffffffff;
    private static final int DIVIDER = 0xffededed;
    private static final int LABEL_INK = 0xff252525;
    private static final int VALUE_INK = 0xff909090;

    private static final float SX_LABEL = 1.023f;
    private static final float SX_VALUE = 0.996f;
    private static final float SX_ACCOUNT = 0.984f;
    private static final float SX_TITLE = 1.014f;

    // ------------------------------------------------------------------ geometry
    private static final RectF BACK = new RectF(44, 184, 90, 256);
    private static final float TITLE_X = 136f;
    private static final float TITLE_BASE = 240.5f;
    private static final float TITLE_SIZE = 63f;
    private static final RectF HEADSET = new RectF(1156, 180, 1240, 260);

    private static final RectF CARD_1 = new RectF(41, 350, 1238, 1253);
    private static final float CARD_RADIUS = 30f;
    /** Where the reference rules its five rows apart, and where each label sits on its row. */
    private static final float[] DIVIDERS = {569, 740, 912, 1084};
    private static final float[] ROW_BASE = {475.5f, 671.5f, 843.5f, 1012f, 1183f};
    /** The two value runs sit a little above their labels' baseline, as the reference sets them. */
    private static final float NAME_VALUE_BASE = 668f;
    private static final float ACCOUNT_VALUE_BASE = 1009f;
    private static final String[] ROW_LABEL = {"头像", "昵称", "数字形象", "支付宝账号", "身份信息"};
    private static final float LABEL_X = 81.5f;
    private static final float LABEL_SIZE = 57f;

    private static final RectF AVATAR_CELL = new RectF(983, 390, 1120, 527);
    private static final float AVATAR_RADIUS = 16f;
    private static final float VALUE_RIGHT = 1119.5f;
    private static final float VALUE_SIZE = 51f;
    private static final float ACCOUNT_SIZE = 52f;

    private static final RectF ROW_CHEVRON = new RectF(1146, 427, 1188, 491);
    private static final RectF QR_ICON = new RectF(1060, 1346, 1120, 1406);

    private static final RectF CARD_2 = new RectF(41, 1295, 1238, 1635);
    private static final RectF CARD_3 = new RectF(41, 1676, 1238, 2015);

    private final Map<String, Bitmap> art = new LinkedHashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();

    public void load(Resources res) {
        art.clear();
        art.put("back", BitmapFactory.decodeResource(res, R.drawable.pf_ic_back));
        art.put("headset", BitmapFactory.decodeResource(res, R.drawable.pf_ic_headset));
        art.put("chevron", BitmapFactory.decodeResource(res, R.drawable.pf_ic_chevron));
        art.put("qr", BitmapFactory.decodeResource(res, R.drawable.pf_ic_qr));
        art.put("thumb", BitmapFactory.decodeResource(res, R.drawable.pf_avatar_thumb));
        art.put("card2", BitmapFactory.decodeResource(res, R.drawable.pf_card2));
        art.put("card3", BitmapFactory.decodeResource(res, R.drawable.pf_card3));
    }

    // ------------------------------------------------------------------ drawing

    public void draw(Canvas c, State s) {
        c.drawColor(PAGE_BG);
        drawArt(c, "back", BACK.left, BACK.top, BACK.right, BACK.bottom);
        text(c, "个人信息", TITLE_X, TITLE_BASE, TITLE_SIZE, LABEL_INK, SX_TITLE);
        drawArt(c, "headset", HEADSET.left, HEADSET.top, HEADSET.right, HEADSET.bottom);

        round(c, CARD_1.left, CARD_1.top, CARD_1.right, CARD_1.bottom, CARD_RADIUS, CARD);
        for (float y : DIVIDERS) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(DIVIDER);
            c.drawRect(LABEL_X, y, CARD_1.right, y + 1.5f, paint);
        }
        for (int i = 0; i < ROW_LABEL.length; i++) {
            text(c, ROW_LABEL[i], LABEL_X, ROW_BASE[i], LABEL_SIZE, LABEL_INK, SX_LABEL);
        }
        // the value column: only the two rows the user owns carry one
        textRight(c, ProfileStore.name(), VALUE_RIGHT, NAME_VALUE_BASE, VALUE_SIZE, VALUE_INK,
                SX_VALUE);
        textRight(c, ProfileStore.phone(), VALUE_RIGHT, ACCOUNT_VALUE_BASE, ACCOUNT_SIZE, VALUE_INK,
                SX_ACCOUNT);
        // the chevron rides every row
        for (float base : ROW_BASE) {
            drawArt(c, "chevron", ROW_CHEVRON.left, base - 32, ROW_CHEVRON.right, base + 32);
        }

        Bitmap avatar = ProfileStore.avatar();
        if (avatar == null) avatar = art.get("thumb");
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        r.set(AVATAR_CELL.left, AVATAR_CELL.top, AVATAR_CELL.right, AVATAR_CELL.bottom);
        c.save();
        path.reset();
        path.addRoundRect(AVATAR_CELL, AVATAR_RADIUS, AVATAR_RADIUS, Path.Direction.CW);
        c.clipPath(path);
        c.drawBitmap(avatar, null, r, paint);
        c.restore();

        drawArt(c, "card2", CARD_2.left, CARD_2.top, CARD_2.right, CARD_2.bottom);
        drawArt(c, "card3", CARD_3.left, CARD_3.top, CARD_3.right, CARD_3.bottom);
        drawArt(c, "qr", QR_ICON.left, QR_ICON.top, QR_ICON.right, QR_ICON.bottom);
    }

    // ------------------------------------------------------------------ hit test

    /** Returns an action token for the tap at (x, y) — screen coordinates in design space. */
    public String hitTest(State s, float x, float y) {
        if (y < 300) {
            if (x < 120) return "back";
            if (x > 1130) return "noop";
            return "noop";
        }
        if (y >= CARD_1.top && y <= CARD_1.bottom) {
            int row = rowAt(y);
            if (row == 0) return "avatar";
            if (row == 1) return "editName";
            if (row == 3) return "editPhone";
            if (row < 0) return "noop";
            return "noop";
        }
        if (y >= CARD_2.top && y <= CARD_2.bottom) {
            return "noop";
        }
        if (y >= CARD_3.top && y <= CARD_3.bottom) {
            return "noop";
        }
        return "noop";
    }

    private static int rowAt(float y) {
        if (y < 350) return -1;
        float[] edges = {350, 569, 740, 912, 1084, 1253};
        for (int i = 0; i < 5; i++) {
            if (y >= edges[i] && y < edges[i + 1]) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ primitives

    private void drawArt(Canvas c, String name, float l, float t, float rr, float b) {
        Bitmap bitmap = art.get(name);
        if (bitmap == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        r.set(l, t, rr, b);
        c.drawBitmap(bitmap, null, r, paint);
    }

    private void text(Canvas c, String value, float x, float baseline, float size, int color,
                      float sx) {
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        c.save();
        c.scale(sx, 1f);
        c.drawText(value, x / sx, baseline, paint);
        c.restore();
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

    private void round(Canvas c, float l, float t, float rr, float b, float radius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRoundRect(l, t, rr, b, radius, radius, paint);
    }
}
