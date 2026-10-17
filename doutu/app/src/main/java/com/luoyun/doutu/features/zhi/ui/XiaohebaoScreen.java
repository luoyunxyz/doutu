package com.luoyun.doutu.features.zhi.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.luoyun.doutu.R;
import com.luoyun.doutu.features.zhi.data.PurseStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The 小荷包 page, rebuilt from {@code docs/reference/xiaohebao/xiaohebao-reference.jpg} — a capture
 * that is already 1280x2816, so every box below is its own pixel.
 *
 * <p>Three things on it belong to the user and are drawn from {@link PurseStore} rather than from the
 * capture: the caption on the blue card (生活费 by default), the total under 总金额(元), and the
 * picture beside the 邀请他人加入 row. Those three are exactly the three the reference bakes in, so
 * the card's cut-out has its own 生活费 painted out by {@code _withdraw/cut_purse.py} before it is
 * hung back here.
 *
 * <p>Everything else — the two promo cards with their rings and ribbons, the empty-state sketch, the
 * bell, the padlock, the eye — is a cut-out or a primitive; the mascot and the card's own gradient
 * travel together as one picture.
 */
public final class XiaohebaoScreen {
    /** 参考图的宽度，也是设计坐标系宽度。 */
    public static final float W = 1280f;
    /** 可见窗口高度，与 支 模块其余页面一致。 */
    public static final float H = 2816f;

    private static final float TEXT_SQUEEZE = 0.94f;

    // ------------------------------------------------------------------ 调色板
    private static final int PAGE_BG = 0xfff2f3f7;
    private static final int SHEET = 0xffffffff;
    private static final int INK = 0xff2a2c30;
    private static final int GREY = 0xff939599;
    private static final int FAINT = 0xffb9bbc2;
    private static final int CHEVRON = 0xffc4c6cb;
    private static final int BUTTON_BG = 0xfff2f2f5;
    private static final int ORANGE = 0xffff8a2b;
    private static final int PLUS_DISC = 0xfff0f1f5;

    // ------------------------------------------------------------------ 顶栏
    private static final RectF BACK = new RectF(50f, 190f, 95f, 265f);
    private static final float BACK_STROKE = 7f;
    private static final RectF BELL = new RectF(707f, 187f, 769f, 240f);
    private static final RectF HOME_PILL = new RectF(816f, 163f, 1000f, 266f);
    private static final RectF HOME_LOCK = new RectF(861f, 192f, 900f, 245f);
    private static final float HOME_CX = 948f;
    private static final RectF SETTINGS_PILL = new RectF(1085f, 163f, 1255f, 266f);
    private static final float SETTINGS_CX = 1170f;
    private static final float PILL_RADIUS = 51.5f;
    private static final float PILL_TEXT_BASE = 250f;
    private static final float PILL_TEXT_SIZE = 46f;

    // ------------------------------------------------------------------ 那张蓝卡
    private static final RectF HEAD = new RectF(39f, 345f, 1239f, 887f);
    private static final float HEAD_RADIUS = 36f;
    private static final float PURSE_X = 99f;
    private static final float PURSE_BASE = 436f;
    private static final float PURSE_SIZE = 54f;

    // ------------------------------------------------------------------ 白页
    private static final float SHEET_TOP = 942f;
    private static final float SHEET_RADIUS = 28f;

    // 头像那一行
    private static final RectF AVATAR = new RectF(52f, 1000f, 165f, 1113f);
    private static final float AVATAR_R = 56.5f;
    /** 头像之间的间距：参考图上 头像(52..165) 和 + (212..317) 的距离正好 156。 */
    private static final float AVATAR_PITCH = 156f;
    private static final float ADD_CX = 264.5f;
    private static final float ADD_CY = 1056.5f;
    private static final float ADD_R = 52.5f;
    private static final float ADD_ARM = 17f;
    private static final float ADD_STROKE = 5f;
    private static final float INVITE_RIGHT = 1190f;
    private static final float INVITE_BASE = 1074f;
    private static final float INVITE_SIZE = 50f;
    private static final RectF INVITE_CHEVRON = new RectF(1194f, 1042f, 1213f, 1072f);

    // 总金额
    private static final float TOTAL_LABEL_X = 58f;
    private static final float TOTAL_LABEL_BASE = 1224f;
    private static final float TOTAL_LABEL_SIZE = 47f;
    private static final RectF EYE = new RectF(283f, 1184f, 334f, 1224f);
    private static final float AMOUNT_X = 60f;
    private static final float AMOUNT_BASE = 1347f;
    private static final float AMOUNT_SIZE = 114f;
    private static final float HIDDEN_SIZE = 96f;
    private static final float RIGHTS_RIGHT = 1190f;
    private static final float RIGHTS_BASE = 1330f;
    private static final float RIGHTS_SIZE = 50f;
    private static final RectF RIGHTS_CHEVRON = new RectF(1194f, 1298f, 1213f, 1328f);
    /** 「查看 权益」里那两个字分开画：查看是灰的，权益是橙的。 */
    private static final float RIGHTS_GAP = 12f;

    // 三个按钮
    private static final RectF OUT_BUTTON = new RectF(42f, 1436f, 399f, 1569f);
    private static final RectF IN_BUTTON = new RectF(410f, 1436f, 769f, 1569f);
    private static final RectF FN_BOX = new RectF(780f, 1436f, 1238f, 1569f);
    private static final float BUTTON_RADIUS = 24f;
    private static final float BUTTON_TEXT_BASE = 1519f;
    private static final float BUTTON_TEXT_SIZE = 49f;
    private static final RectF FN_ICON = new RectF(921f, 1454f, 990f, 1522f);
    private static final RectF PL_ICON = new RectF(1106f, 1453f, 1177f, 1524f);
    private static final float FN_TEXT_CX = 955f;
    private static final float PL_TEXT_CX = 1141f;
    private static final float FN_TEXT_BASE = 1566f;
    private static final float FN_TEXT_SIZE = 38f;
    private static final float FN_DIVIDER_X = 1048f;
    private static final float FN_DIVIDER_TOP = 1462f;
    private static final float FN_DIVIDER_BOTTOM = 1544f;
    private static final int HAIRLINE = 0xffececf1;

    // 两张推荐卡
    private static final RectF GOAL = new RectF(42f, 1669f, 622f, 2029f);
    private static final RectF PROMISE = new RectF(660f, 1669f, 1230f, 2029f);
    private static final float PROMO_RADIUS = 28f;

    // 三个页签
    private static final String[] TABS = {"动态", "账单", "成员记账"};
    private static final float[] TAB_CX = {111f, 300f, 547f};
    private static final float TAB_BASE = 2187f;
    private static final float TAB_SIZE = 58f;
    private static final RectF TAB_UNDERLINE = new RectF(230f, 2224f, 395f, 2231f);
    /** 橙线比字面宽出来的那一截：参考图上 账单 的字面 118、线 165。 */
    private static final float TAB_UNDERLINE_MARGIN = 47f;

    // 空态
    private static final RectF EMPTY = new RectF(520f, 2482f, 754f, 2618f);
    private static final float HINT_CX = 639.5f;
    private static final float HINT_BASE = 2725f;
    private static final float HINT_SIZE = 45f;

    // 发布
    private static final float PUBLISH_CX = 1140f;
    private static final float PUBLISH_CY = 2455f;
    private static final float PUBLISH_R = 112f;
    private static final RectF PUBLISH_PLUS = new RectF(1117f, 2408f, 1165f, 2457f);
    private static final float PUBLISH_BASE = 2520f;
    private static final float PUBLISH_SIZE = 38f;

    /** 页面上会变的两样：哪一个页签亮着，总金额是不是被眼睛藏起来了。 */
    public static final class State {
        public int tab = 1;
        public boolean hidden;
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final RectF dst = new RectF();
    private final LinkedHashMap<String, Bitmap> art = new LinkedHashMap<>();
    private final List<float[]> hitRects = new ArrayList<>();
    private final List<String> hitTokens = new ArrayList<>();

    /** 把参考图上抠下来的那几张图读进来，只在页面第一次出现时做一次。 */
    public void load(Resources res) {
        if (!art.isEmpty()) return;
        art.put("head", cut(res, R.drawable.xh_head));
        art.put("avatar", cut(res, R.drawable.xh_avatar));
        art.put("goal", cut(res, R.drawable.xh_goal));
        art.put("promise", cut(res, R.drawable.xh_promise));
        art.put("empty", cut(res, R.drawable.xh_empty));
        art.put("bell", cut(res, R.drawable.xh_bell));
        art.put("lock", cut(res, R.drawable.xh_lock));
        art.put("eye", cut(res, R.drawable.xh_eye));
        art.put("func", cut(res, R.drawable.xh_func));
        art.put("plugin", cut(res, R.drawable.xh_plugin));
        art.put("publish", cut(res, R.drawable.xh_publish));
    }

    private Bitmap cut(Resources res, int id) {
        return BitmapFactory.decodeResource(res, id);
    }

    // ------------------------------------------------------------------ 画

    public void draw(Canvas c, State s) {
        hitRects.clear();
        hitTokens.clear();

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PAGE_BG);
        c.drawRect(0, 0, W, H, paint);

        drawCard(c);
        // 白页：卡片下面从 942 起一直白到底，上面两个圆角
        paint.setColor(SHEET);
        r.set(0, SHEET_TOP, W, H);
        c.drawRoundRect(r, SHEET_RADIUS, SHEET_RADIUS, paint);
        c.drawRect(0, SHEET_TOP + SHEET_RADIUS, W, H, paint);

        drawHeader(c);
        drawAvatarRow(c);
        drawTotal(c, s);
        drawButtons(c);
        drawPromos(c);
        drawTabs(c, s);
        drawEmpty(c);
        drawPublish(c);
    }

    /** 顶栏：返回、铃铛、主页和设置两个白药丸。 */
    private void drawHeader(Canvas c) {
        chevronLeft(c, BACK, BACK_STROKE, INK);
        hit(BACK.left - 20, BACK.top - 20, BACK.right + 20, BACK.bottom + 20, "back");

        drawArt(c, "bell", BELL);
        hit(BELL.left - 16, BELL.top - 16, BELL.right + 16, BELL.bottom + 16, "bell");

        roundRect(c, HOME_PILL, PILL_RADIUS, SHEET);
        drawArt(c, "lock", HOME_LOCK);
        centerText(c, "主页", HOME_CX, PILL_TEXT_BASE, PILL_TEXT_SIZE, INK, true);
        hit(HOME_PILL.left, HOME_PILL.top, HOME_PILL.right, HOME_PILL.bottom, "home");

        roundRect(c, SETTINGS_PILL, PILL_RADIUS, SHEET);
        centerText(c, "设置", SETTINGS_CX, PILL_TEXT_BASE, PILL_TEXT_SIZE, INK, true);
        hit(SETTINGS_PILL.left, SETTINGS_PILL.top, SETTINGS_PILL.right, SETTINGS_PILL.bottom,
                "settings");
    }

    /** 那张蓝卡：渐变和吉祥物是一整张抠图，只把「生活费」这三个字自己画上去。 */
    private void drawCard(Canvas c) {
        Bitmap head = art.get("head");
        if (head != null) {
            int save = c.save();
            path.reset();
            path.addRoundRect(HEAD, HEAD_RADIUS, HEAD_RADIUS, Path.Direction.CW);
            c.clipPath(path);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(255);
            c.drawBitmap(head, null, HEAD, paint);
            c.restoreToCount(save);
        }
        text(c, PurseStore.name(), PURSE_X, PURSE_BASE, PURSE_SIZE, 0xffffffff, true);
        hit(HEAD.left + 20, PURSE_BASE - PURSE_SIZE, 400, PURSE_BASE + 20, "purse");
    }

    /** 头像、加号、邀请他人加入。头像是一个跟着一个排的，点 + 会再添一个，+ 就往右挪一位。 */
    private void drawAvatarRow(Canvas c) {
        int count = Math.max(1, PurseStore.avatarCount());
        for (int i = 0; i < count; i++) {
            r.set(AVATAR.left + i * AVATAR_PITCH, AVATAR.top,
                    AVATAR.right + i * AVATAR_PITCH, AVATAR.bottom);
            Bitmap picture = PurseStore.avatar(i);
            int save = c.save();
            path.reset();
            path.addCircle(r.centerX(), r.centerY(), AVATAR_R, Path.Direction.CW);
            c.clipPath(path);
            if (picture != null) {
                cover(c, picture, r);
            } else {
                drawArt(c, "avatar", r);
            }
            c.restoreToCount(save);
            hit(r.left - 10, r.top - 10, r.right + 10, r.bottom + 10, "avatar:" + i);
        }

        if (count < PurseStore.MAX_AVATARS) {
            // + 排在最后一个头像后面：只有一个头像时它就落在参考图那个位置上
            float cx = ADD_CX + (count - 1) * AVATAR_PITCH;
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(PLUS_DISC);
            c.drawCircle(cx, ADD_CY, ADD_R, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(ADD_STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(INK);
            c.drawLine(cx - ADD_ARM, ADD_CY, cx + ADD_ARM, ADD_CY, paint);
            c.drawLine(cx, ADD_CY - ADD_ARM, cx, ADD_CY + ADD_ARM, paint);
            paint.setStyle(Paint.Style.FILL);
            hit(cx - ADD_R, ADD_CY - ADD_R, cx + ADD_R, ADD_CY + ADD_R, "add");
        }

        textRight(c, "邀请他人加入", INVITE_RIGHT, INVITE_BASE, INVITE_SIZE, INK, false);
        chevron(c, INVITE_CHEVRON, 4.6f, CHEVRON);
        hit(880, 1010, 1240, 1090, "invite");
    }

    /** 总金额那一块：标签、眼睛、数字，右边是查看权益。 */
    private void drawTotal(Canvas c, State s) {
        text(c, "总金额(元)", TOTAL_LABEL_X, TOTAL_LABEL_BASE, TOTAL_LABEL_SIZE, GREY, false);
        drawArt(c, "eye", EYE);
        hit(EYE.left - 14, EYE.top - 14, EYE.right + 14, EYE.bottom + 14, "eye");

        if (s.hidden) {
            text(c, "****", AMOUNT_X, AMOUNT_BASE, HIDDEN_SIZE, INK, true);
        } else {
            text(c, PurseStore.total(), AMOUNT_X, AMOUNT_BASE, AMOUNT_SIZE, INK, true);
        }
        hit(40, 1240, 340, 1370, "total");

        float size = RIGHTS_SIZE;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ORANGE);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.RIGHT);
        float chevronLeft = RIGHTS_CHEVRON.left - 8f;
        c.save();
        c.scale(TEXT_SQUEEZE, 1f, chevronLeft, 0f);
        c.drawText("权益", chevronLeft, RIGHTS_BASE, paint);
        c.restore();
        float rightsRight = chevronLeft - paint.measureText("权益") * TEXT_SQUEEZE - RIGHTS_GAP;
        paint.setColor(GREY);
        c.save();
        c.scale(TEXT_SQUEEZE, 1f, rightsRight, 0f);
        c.drawText("查看", rightsRight, RIGHTS_BASE, paint);
        c.restore();
        paint.setTextAlign(Paint.Align.LEFT);
        chevron(c, RIGHTS_CHEVRON, 4.6f, CHEVRON);
        hit(860, 1270, 1240, 1345, "rights");
    }

    /** 转出 / 转入 / 功能 + 插件。 */
    private void drawButtons(Canvas c) {
        roundRect(c, OUT_BUTTON, BUTTON_RADIUS, BUTTON_BG);
        centerText(c, "转出", OUT_BUTTON.centerX(), BUTTON_TEXT_BASE, BUTTON_TEXT_SIZE, INK, true);
        hit(OUT_BUTTON.left, OUT_BUTTON.top, OUT_BUTTON.right, OUT_BUTTON.bottom, "out");

        roundRect(c, IN_BUTTON, BUTTON_RADIUS, BUTTON_BG);
        centerText(c, "转入", IN_BUTTON.centerX(), BUTTON_TEXT_BASE, BUTTON_TEXT_SIZE, INK, true);
        hit(IN_BUTTON.left, IN_BUTTON.top, IN_BUTTON.right, IN_BUTTON.bottom, "in");

        roundRect(c, FN_BOX, BUTTON_RADIUS, SHEET);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(HAIRLINE);
        c.drawRect(FN_DIVIDER_X, FN_DIVIDER_TOP, FN_DIVIDER_X + 2f, FN_DIVIDER_BOTTOM, paint);
        drawArt(c, "func", FN_ICON);
        centerText(c, "功能", FN_TEXT_CX, FN_TEXT_BASE, FN_TEXT_SIZE, INK, false);
        drawArt(c, "plugin", PL_ICON);
        centerText(c, "插件", PL_TEXT_CX, FN_TEXT_BASE, FN_TEXT_SIZE, INK, false);
        hit(FN_BOX.left, FN_BOX.top, FN_DIVIDER_X, FN_BOX.bottom, "func");
        hit(FN_DIVIDER_X, FN_BOX.top, FN_BOX.right, FN_BOX.bottom, "plugin");
    }

    /** 攒钱目标 和 承诺券 两张卡：连里面的圆环、礼盒都是抠图。 */
    private void drawPromos(Canvas c) {
        drawCardArt(c, "goal", GOAL);
        hit(GOAL.left, GOAL.top, GOAL.right, GOAL.bottom, "goal");
        drawCardArt(c, "promise", PROMISE);
        hit(PROMISE.left, PROMISE.top, PROMISE.right, PROMISE.bottom, "promise");
    }

    private void drawCardArt(Canvas c, String name, RectF box) {
        Bitmap bmp = art.get(name);
        if (bmp == null) return;
        int save = c.save();
        path.reset();
        path.addRoundRect(box, PROMO_RADIUS, PROMO_RADIUS, Path.Direction.CW);
        c.clipPath(path);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        c.drawBitmap(bmp, null, box, paint);
        c.restoreToCount(save);
    }

    /** 动态 / 账单 / 成员记账，选中的那个字加粗、底下压一条橙线。 */
    private void drawTabs(Canvas c, State s) {
        for (int i = 0; i < TABS.length; i++) {
            centerText(c, TABS[i], TAB_CX[i], TAB_BASE, TAB_SIZE, INK, i == s.tab);
            hit(TAB_CX[i] - 80, TAB_BASE - TAB_SIZE, TAB_CX[i] + 80, TAB_BASE + 22, "tab:" + i);
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(TAB_SIZE);
        paint.setTypeface(s.tab == 1 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        float half = (paint.measureText(TABS[s.tab]) * TEXT_SQUEEZE + TAB_UNDERLINE_MARGIN) / 2f;
        r.set(TAB_CX[s.tab] - half, TAB_UNDERLINE.top, TAB_CX[s.tab] + half, TAB_UNDERLINE.bottom);
        roundRect(c, r, TAB_UNDERLINE.height() / 2f, ORANGE);
    }

    /** 一条记录都没有时那张插图和一句话。 */
    private void drawEmpty(Canvas c) {
        drawArt(c, "empty", EMPTY);
        centerText(c, "未来的存钱和消费等记录会展示在这", HINT_CX, HINT_BASE, HINT_SIZE, FAINT, false);
    }

    /** 右下角那个浮起来的「+ 发布」：白圆片压在一圈很淡的影子上。 */
    private void drawPublish(Canvas c) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x0f000000);
        c.drawCircle(PUBLISH_CX, PUBLISH_CY + 3f, PUBLISH_R + 6f, paint);
        paint.setColor(SHEET);
        c.drawCircle(PUBLISH_CX, PUBLISH_CY, PUBLISH_R, paint);
        drawArt(c, "publish", PUBLISH_PLUS);
        centerText(c, "发布", PUBLISH_CX, PUBLISH_BASE, PUBLISH_SIZE, INK, false);
        hit(PUBLISH_CX - PUBLISH_R, PUBLISH_CY - PUBLISH_R,
                PUBLISH_CX + PUBLISH_R, PUBLISH_CY + PUBLISH_R + 20f, "publish");
    }

    // ------------------------------------------------------------------ 点的东西

    private void hit(float l, float t, float rr, float b, String token) {
        hitRects.add(new float[]{l, t, rr, b});
        hitTokens.add(token);
    }

    /** 返回落在 (x, y) 上的动作 token；坐标是设计坐标。 */
    public String hitTest(State s, float x, float y) {
        for (int i = hitRects.size() - 1; i >= 0; i--) {
            float[] b = hitRects.get(i);
            if (x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]) return hitTokens.get(i);
        }
        return "noop";
    }

    // ------------------------------------------------------------------ 图元

    /** 一张图按短边放大、长边裁掉地铺满一个框，头像和用户上传的图都用它。 */
    private void cover(Canvas c, Bitmap bmp, RectF box) {
        float scale = Math.max(box.width() / bmp.getWidth(), box.height() / bmp.getHeight());
        float w = bmp.getWidth() * scale;
        float h = bmp.getHeight() * scale;
        dst.set(box.centerX() - w / 2f, box.centerY() - h / 2f,
                box.centerX() + w / 2f, box.centerY() + h / 2f);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        c.drawBitmap(bmp, null, dst, paint);
    }

    private void drawArt(Canvas c, String name, RectF box) {
        Bitmap bmp = art.get(name);
        if (bmp == null) return;
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

    private void chevronLeft(Canvas c, RectF box, float stroke, int colour) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(colour);
        float inset = stroke / 2f;
        path.reset();
        path.moveTo(box.right - inset, box.top + inset);
        path.lineTo(box.left + inset, box.centerY());
        path.lineTo(box.right - inset, box.bottom - inset);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

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

    private void textRight(Canvas c, String value, float right, float baseline, float size,
                           int colour, boolean bold) {
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
}
