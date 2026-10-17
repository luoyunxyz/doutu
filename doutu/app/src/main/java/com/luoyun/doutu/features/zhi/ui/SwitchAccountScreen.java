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
import com.luoyun.doutu.features.zhi.data.Accounts;

import java.util.ArrayList;
import java.util.List;

/**
 * 切换账号页（「支」宫格里的「切换账号」）。
 *
 * <p>整页照着参考截图 {@code 876f3421f66fae511d6961455db6d20c.jpg} 1:1 重建：顶栏、大标题、
 * 账号卡、加号卡、底部蓝字各自切成一块贴回 1280 宽的画布，参考图自带的状态栏（最上面那一条）
 * 不切进来，交给系统。账号卡上要按账号换的两块 —— 头像和「当前」角标 —— 在模块里已经抹掉，
 * 抹的时候搬的是同一张参考图上第二张卡片同一位置的像素，所以贴回去不会有接缝。
 *
 * <p>一个账号就是一张卡片：当前那个右上角带「当前」角标，头像用这个账号自己的图（新账号的头像
 * 是随机头像接口给的，默认那个用参考图里抠出来的那张），手机号不一样时用小色块盖掉卡上的原字
 * 再画上这一行的号。账号多到一屏放不下时跟着手指滚。
 */
public final class SwitchAccountScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;

    /** 自己那套模块以外的底色。 */
    private static final int PAGE_BG = 0xfff5f5f5;
    private static final int CARD = 0xffffffff;
    private static final int INK = 0xff333333;

    private static final float HEAD_Y = 136f;
    private static final float TITLE_X = 255f;
    private static final float TITLE_Y = 545f;

    /** 账号卡：第一张在 818，每张 313 高，中间空 42，加号卡接在最后一张后面。 */
    private static final float LIST_TOP = 818f;
    private static final float CARD_H = 313f;
    private static final float CARD_GAP = 42f;

    /** 头像方块（卡片里的位置）与它的圆角。 */
    private static final float AVATAR_X = 113f;
    private static final float AVATAR_Y = 70f;
    private static final float AVATAR_SIZE = 172f;
    private static final float AVATAR_RADIUS = 28f;

    /** 手机号：卡片里那一行的位置、字号、基线（没名字的卡片就是参考图那一行）。 */
    private static final float PHONE_X = 331f;
    private static final float PHONE_BASELINE = 182f;
    private static final float PHONE_SIZE = 70f;
    /** 自己有名字的账号：名字一行、号码一行，两行一起在卡片里居中。 */
    private static final float NAME_BASELINE = 136f;
    private static final float NAME_SIZE = 48f;
    private static final float PHONE_BASELINE_NAMED = 214f;

    /** 「当前」角标：贴在卡片的右上角，模块是按 (1040, 卡顶-8) 抠的。 */
    private static final float TAG_X = 1040f;
    private static final float TAG_DY = -8f;
    private static final float TAG_W = 180f;
    private static final float TAG_H = 85f;

    /** 底部那句蓝字：默认落在参考图的位置，账号多了就跟着往下让。 */
    private static final float FOOTER_X = 350f;
    private static final float FOOTER_Y = 2598f;
    private static final float FOOTER_W = 580f;
    private static final float FOOTER_H = 70f;

    public static final class State {
        public float scroll;
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private Bitmap head, title, card, avatarArt, tag, add, footer;

    /** 这一帧算出来的位置，点哪儿要用。 */
    private List<Accounts.Account> shown = new ArrayList<>();
    private float footerY = FOOTER_Y;
    private float contentH = H;

    public void load(Resources res) {
        head = BitmapFactory.decodeResource(res, R.drawable.sw_head);
        title = BitmapFactory.decodeResource(res, R.drawable.sw_title);
        card = BitmapFactory.decodeResource(res, R.drawable.sw_card);
        avatarArt = BitmapFactory.decodeResource(res, R.drawable.sw_avatar);
        tag = BitmapFactory.decodeResource(res, R.drawable.sw_tag);
        add = BitmapFactory.decodeResource(res, R.drawable.sw_add);
        footer = BitmapFactory.decodeResource(res, R.drawable.sw_foot);
    }

    /** 账号多了就把整页往下拉长，跟着手指滚。 */
    public float maxScroll(State s) {
        return Math.max(0f, contentH - H);
    }

    public void draw(Canvas c, State s) {
        shown = Accounts.all();
        float addTop = addTop(shown.size());
        footerY = Math.max(FOOTER_Y, addTop + CARD_H + 80f);
        contentH = Math.max(H, footerY + FOOTER_H + 46f);

        c.drawColor(PAGE_BG);
        draw(c, head, 0f, HEAD_Y);

        c.save();
        c.translate(0f, -s.scroll);
        draw(c, title, TITLE_X, TITLE_Y);
        for (int i = 0; i < shown.size(); i++) drawAccount(c, shown.get(i), cardTop(i));
        draw(c, add, 0f, addTop);
        draw(c, footer, FOOTER_X, footerY);
        c.restore();
    }

    private void drawAccount(Canvas c, Accounts.Account account, float top) {
        draw(c, card, 0f, top);
        Bitmap picture = account.avatar != null ? account.avatar : avatarArt;
        if (picture != null) {
            float left = AVATAR_X;
            float top2 = top + AVATAR_Y;
            rect.set(left, top2, left + AVATAR_SIZE, top2 + AVATAR_SIZE);
            c.save();
            path.reset();
            path.addRoundRect(rect, AVATAR_RADIUS, AVATAR_RADIUS, Path.Direction.CW);
            c.clipPath(path);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setAlpha(255);
            c.drawBitmap(picture, null, rect, paint);
            c.restore();
        }
        if (account.ownIdentity()) {
            // 自己有名字和号码的账号：盖掉参考图那一行，名字在上、号码在下
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(CARD);
            c.drawRect(300f, top + 78f, 1006f, top + 234f, paint);
            text(c, account.name, PHONE_X, top + NAME_BASELINE, NAME_SIZE, INK);
            text(c, account.phone, PHONE_X, top + PHONE_BASELINE_NAMED, PHONE_SIZE, INK);
        } else if (!Accounts.DEFAULT_PHONE.equals(account.phone)) {
            // 卡片上印的是参考图里那个号，只有别的号才盖掉重画
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(CARD);
            c.drawRect(300f, top + 112f, 1006f, top + 202f, paint);
            text(c, account.phone, PHONE_X, top + PHONE_BASELINE, PHONE_SIZE, INK);
        }
        if (Accounts.isCurrent(account.id)) draw(c, tag, TAG_X, top + TAG_DY);
    }

    private float cardTop(int index) {
        return LIST_TOP + index * (CARD_H + CARD_GAP);
    }

    private float addTop(int count) {
        return cardTop(count);
    }

    private void draw(Canvas c, Bitmap bitmap, float x, float y) {
        if (bitmap == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        c.drawBitmap(bitmap, x, y, paint);
    }

    private void text(Canvas c, String value, float x, float baseline, float size, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText(value, x, baseline, paint);
    }

    // ------------------------------------------------------------------ 点哪儿

    public String hitTest(State s, float x, float y) {
        if (y < HEAD_Y + 30f) return "noop";
        if (y >= 160f && y <= 290f) {
            if (x < 150f) return "back";
            if (x >= 1040f) return "manage";
        }
        float cy = y + s.scroll;
        for (int i = 0; i < shown.size(); i++) {
            float top = cardTop(i);
            if (cy >= top && cy <= top + CARD_H) {
                Accounts.Account account = shown.get(i);
                if (Accounts.isCurrent(account.id)) return "toast:这已经是当前账号";
                return "switch:" + account.id;
            }
        }
        float addTop = addTop(shown.size());
        if (cy >= addTop && cy <= addTop + CARD_H) return "new";
        if (cy >= footerY - 10f && cy <= footerY + FOOTER_H + 10f
                && x >= FOOTER_X - 40f && x <= FOOTER_X + FOOTER_W + 40f) {
            return "footer";
        }
        return "noop";
    }

}
