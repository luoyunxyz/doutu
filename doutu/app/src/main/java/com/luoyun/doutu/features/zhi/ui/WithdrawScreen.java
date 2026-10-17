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
import com.luoyun.doutu.features.zhi.data.ReceiptStore;
import com.luoyun.doutu.features.zhi.data.WithdrawStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The 提现 page the 支 module's 余额提现 tile opens, and the number pad it raises.
 *
 * <p>Rebuilt 1:1 from the three captures in {@code docs/reference/withdraw}: the wash, the
 * 到账银行卡 block, the 提现金额 card with its 可用余额 and 剩余免费额度 rows, the second card's three
 * 去… rows, the 提现 button at the bottom and — when the keypad is up — the panel of white keys that
 * covers everything from {@link #PANEL_TOP} down. The captures show the amount box untouched, so the
 * button and the keypad's own key are the pale blue of a disabled action; typing a figure turns both
 * of them the app's own blue.
 *
 * <p>The artwork that cannot be redrawn is cut out of the captures by {@code _withdraw/cut_assets.py}
 * (the three round service logos, the two 红包 badges, the ⓘ, the keypad's ⌫, the sheet's blue tick)
 * and hung back on the coordinates printed there. The current bank's logo is not one of those: it is
 * whatever card the 到账银行卡 row is wearing, so the host hands it in.
 */
public final class WithdrawScreen {
    /** 参考图宽度，作为设计坐标系宽度。 */
    public static final float W = 1280f;
    /** 可见窗口高度，与 支 模块其余页面一致。 */
    public static final float H = 2816f;

    private static final float TEXT_SQUEEZE = 0.94f;

    // ------------------------------------------------------------------ 调色板
    private static final int PAGE_BG = 0xfff2f3f7;
    private static final int CARD = 0xffffffff;
    private static final int INK = 0xff2a2c30;
    private static final int CAPTION = 0xff9b9ca0;
    private static final int FAINT = 0xffa8acb4;
    private static final int RULE = 0xffeeeeee;
    private static final int PANEL = 0xfff6f7fb;
    private static final int KEY = 0xffffffff;
    private static final int DIGIT = 0xff303030;
    private static final int CHEVRON = 0xffb9bcc2;
    private static final int ACTION_ON = 0xff1677ff;
    private static final int ACTION_OFF = 0xffa4c8fa;
    private static final int CLOSE = 0xffaeafb3;

    // ------------------------------------------------------------------ 页头
    private static final RectF BACK = new RectF(49.2f, 190.2f, 84.4f, 250.5f);
    private static final float TITLE_X = 135f;
    private static final float TITLE_BASE = 245.1f;
    private static final float TITLE_SIZE = 68f;
    private static final float[] DOTS_X = {1175.7f, 1197.9f, 1220.2f};
    private static final float DOTS_Y = 219.6f;
    private static final float DOTS_R = 8.8f;

    // ------------------------------------------------------------------ 到账银行卡
    private static final float LABEL_X = 82f;
    private static final float LABEL_BASE = 437.6f;
    private static final float LABEL_SIZE = 54f;
    private static final RectF BANK_ICON = new RectF(81.7f, 489.3f, 142.0f, 549.6f);
    private static final float BANK_NAME_X = 171f;
    private static final float BANK_NAME_BASE = 537.1f;
    private static final float BANK_NAME_SIZE = 54.5f;
    private static final float BANK_SUB_X = 171f;
    private static final float BANK_SUB_BASE = 608.6f;
    private static final float BANK_SUB_SIZE = 47f;
    private static final RectF BANK_CHEVRON = new RectF(1156.8f, 503.2f, 1178.1f, 535.7f);

    // ------------------------------------------------------------------ 提现金额 卡
    private static final RectF CARD1 = new RectF(40.8f, 707.2f, 1238.3f, 1310.1f);
    private static final float CARD_RADIUS = 47f;
    private static final float CARD1_TITLE_X = 82f;
    private static final float CARD1_TITLE_BASE = 794.6f;
    private static final float CARD1_TITLE_SIZE = 55f;
    private static final float AMOUNT_X = 80f;
    private static final float AMOUNT_BASE = 1105f;
    private static final float AMOUNT_SIZE = 98f;
    private static final float AMOUNT_VALUE_X = 193f;
    private static final float AMOUNT_VALUE_SIZE = 134f;
    private static final float AVAIL_RIGHT = 1198f;
    private static final float AVAIL_BASE = 1094.5f;
    private static final float AVAIL_SIZE = 47.5f;
    private static final float DIVIDER1_Y = 1152.9f;
    private static final float DIVIDER_L = 84.6f;
    private static final float DIVIDER_R = 1194.4f;
    private static final float RULE_H = 2f;
    private static final float QUOTA_X = 82f;
    private static final float QUOTA_BASE = 1251f;
    private static final float QUOTA_SIZE = 54.5f;
    private static final RectF INFO = new RectF(672.6f, 1204.7f, 722.7f, 1253.9f);

    // ---- 输入框里有了数字之后才有的那几件东西（都是照提现金额那张参考图量的）----
    /** 光标：数字左边那道竖线，和它右边那个量级。 */
    private static final float UNIT_CARET_X = 191.5f;
    private static final float UNIT_CARET_W = 2.8f;
    private static final float UNIT_CARET_TOP = 888f;
    private static final float UNIT_CARET_BOTTOM = 950f;
    private static final float UNIT_X = 210f;
    private static final float UNIT_BASE = 936f;
    private static final float UNIT_SIZE = 53f;
    private static final int UNIT_CARET = 0xff7d838c;
    /** 输入的数字：和 ¥ 差不多同一条基线（自己再高两像素），但大得多。 */
    private static final float AMOUNT_VALUE_BASE = 1099f;
    private static final RectF CLEAR = new RectF(1139f, 1052.8f, 1194.6f, 1108.4f);
    private static final float CLEAR_CROSS = 4.6f;
    private static final int CLEAR_DISC = 0xffcecece;
    /** 余额不够时那行红字，以及它把下面顶下去多少。 */
    private static final float WARN_X = 84f;
    private static final float WARN_BASE = 1244f;
    private static final float WARN_SIZE = 53.5f;
    private static final int WARN_RED = 0xffe1483c;
    private static final float WARN_QUOTA_SHIFT = 99f;
    private static final float WARN_CARD1_SHIFT = 101f;
    private static final float WARN_CARD2_SHIFT = 102.4f;

    // ------------------------------------------------------------------ 去… 卡
    private static final RectF CARD2 = new RectF(40.8f, 1337.9f, 1238.3f, 2008.5f);
    private static final RectF[] ROW_ICON = {
            new RectF(102.6f, 1400.6f, 196.3f, 1494.3f),
            new RectF(102.6f, 1626.6f, 196.3f, 1720.3f),
            new RectF(86.6f, 1829.6f, 213.7f, 1968.8f)};
    private static final float ROW_TEXT_X = 253f;
    private static final float ROW_TITLE_SIZE = 54f;
    private static final float[] ROW_TITLE_BASE = {1432.8f, 1653.7f, 1884.7f};
    private static final float ROW_SUB_SIZE = 47f;
    private static final float[] ROW_SUB_BASE = {1512.2f, 1728.7f, 1960.7f};
    private static final RectF[] ROW_BADGE = {
            new RectF(784.9f, 1387.9f, 915.7f, 1442.7f),
            null,
            new RectF(673.9f, 1837.9f, 858.5f, 1891.7f)};
    private static final RectF[] ROW_CHEVRON = {
            new RectF(1179.5f, 1431.4f, 1197.3f, 1463.6f),
            new RectF(1179.5f, 1656.7f, 1197.3f, 1689.0f),
            new RectF(1179.5f, 1882.3f, 1197.3f, 1914.7f)};
    private static final float[] ROW_RULE_Y = {1560.9f, 1786.5f};
    private static final float ROW_RULE_L = 255.7f;
    private static final float ROW_RULE_R = 1198f;

    private static final String[] ROW_TITLE = {"余利宝收益率可达1.8%", "去查看你的卡账单", "去会员中心领积分"};
    private static final String[] ROW_SUB = {
            "免费转出到银行卡，闲钱享收益", "点击查看详情", "领取支付宝积分可兑红包、生活用品"};

    // ------------------------------------------------------------------ 提现 按钮
    private static final RectF BUTTON = new RectF(52.9f, 2515f, 1226.2f, 2683.8f);
    private static final float BUTTON_RADIUS = 84.4f;
    private static final float BUTTON_CX = 639.5f;
    private static final float BUTTON_BASE = 2622.9f;
    private static final float BUTTON_SIZE = 62f;

    // ------------------------------------------------------------------ 数字键盘
    /**
     * 键盘面板从这一行往下，把页面盖住（参考图里键盘一升起，「去会员中心领积分」那行就没了）。
     *
     * <p>支付密码那一层用的是同一个面板，但键不一样：只剩 1…9、一个落在中间那列的 0 和最后一行的
     * ⌫ —— 没有小数点，也没有那个蓝的提现键，和参考图里那台手机下面露出来的键盘一样。
     */
    public static final float PANEL_TOP = 1830f;
    private static final float PANEL_BOTTOM = 2752.9f;
    private static final float[] KEY_L = {20.4f, 333.0f, 643.7f, 954.4f};
    private static final float[] KEY_R = {311.7f, 624.2f, 934.0f, 1259.6f};
    private static final float[] KEY_T = {1988.1f, 2180.1f, 2371.2f, 2562.3f};
    private static final float KEY_H = 170f;
    private static final float KEY_RADIUS = 20f;
    private static final float DIGIT_SIZE = 82f;
    /** 数字在键里不是正中：参考图的字面比键心低 33px。 */
    private static final float DIGIT_DROP = 33f;
    /** 小数点自己有一套：参考图里它落在键心上，还比列心偏右 8.6。 */
    private static final float DOT_DROP = 2f;
    private static final float DOT_SHIFT_X = 8.6f;
    private static final RectF DEL = new RectF(1059.3f, 2037.3f, 1154.8f, 2108.7f);
    private static final RectF ACTION = new RectF(954.4f, 2180.5f, 1259.6f, 2730f);
    private static final float ACTION_TEXT_SIZE = 61f;
    private static final float ACTION_TEXT_DROP = 33f;
    private static final RectF CHEVRON_DOWN = new RectF(620.6f, 1882.9f, 668.8f, 1911.6f);
    private static final float CHEVRON_STROKE = 4.6f;
    /** 参考图里返回箭头比其余几个 › 都粗。 */
    private static final float CHEVRON_LEFT_STROKE = 6.4f;

    /** 键盘上的数字：前三行的三列，第四行只有 0（跨两列）和一个小数点。 */
    private static final String[][] KEY_LABEL = {
            {"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}};
    /** 第四行：0 从第 0 列跨到第 1 列，小数点占第 2 列。 */
    private static final int KEY_ZERO_FROM = 0;
    private static final int KEY_ZERO_TO = 1;
    private static final int KEY_DOT_COL = 2;

    /** 页面状态由宿主 Activity 持有。 */
    public static final class State {
        /** 用户已经敲进去的金额，空串时那个大 ¥ 后面什么都没有。 */
        public String amount = "";
        /** 键盘有没有升起；默认收起，点金额输入框才弹。 */
        public boolean keypad;
        /** 支付密码那一层升起来了没有（点提现之后）。 */
        public boolean password;
        /** 已经敲进去的支付密码位数，最多 {@link #PIN_LENGTH} 位。 */
        public String pin = "";
    }

    private final java.util.LinkedHashMap<String, Bitmap> art = new java.util.LinkedHashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();

    /** 每一帧登记的可点击热区。 */
    private final List<float[]> hitRects = new ArrayList<>();
    private final List<String> hitTokens = new ArrayList<>();

    // ------------------------------------------------------------------ 资源
    public void load(Resources res) {
        art.clear();
        art.put("yulibao", BitmapFactory.decodeResource(res, R.drawable.wd_icon_yulibao));
        art.put("bill", BitmapFactory.decodeResource(res, R.drawable.wd_icon_bill));
        art.put("member", BitmapFactory.decodeResource(res, R.drawable.wd_icon_member));
        art.put("badgeLhb", BitmapFactory.decodeResource(res, R.drawable.wd_badge_lhb));
        art.put("badgeYuan", BitmapFactory.decodeResource(res, R.drawable.wd_badge_yuan));
        art.put("info", BitmapFactory.decodeResource(res, R.drawable.wd_info));
        art.put("del", BitmapFactory.decodeResource(res, R.drawable.wd_del));
    }

    // ------------------------------------------------------------------ 入口
    public void draw(Canvas c, State s, WithdrawStore.Card card, Bitmap logo,
                     double quota, double avail) {
        hitRects.clear();
        hitTokens.clear();
        c.drawColor(PAGE_BG);

        String warn = warning(s, avail);
        float shift = warn == null ? 0f : WARN_CARD2_SHIFT;

        drawHeader(c);
        drawBankRow(c, card, logo);
        drawAmountCard(c, s, quota, avail, warn);
        drawServiceCard(c, shift);
        drawButton(c, canWithdraw(s, avail));

        if (s.password) {
            // 密码那一层：键盘是亮的，只有它上面那半截压暗 —— 参考图里那块键盘明显比上面亮
            drawKeypad(c, false, true);
            drawPassword(c, s);
        } else if (s.keypad) {
            drawKeypad(c, canWithdraw(s, avail), false);
        }
    }

    /**
     * 输入框里有没有一个**能提出去**的数：够得着可用余额才算。有的话按钮和键盘上那个键都变成实蓝，
     * 和参考图里那张一样 —— 那张输入了 1234568 而余额是 0，所以它们还是浅蓝，下面那行红字说明原因。
     */
    public static boolean canWithdraw(State s, double avail) {
        double amount = value(s);
        return amount > 0 && avail > 0 && amount <= avail + 0.0001;
    }

    /** 提不了的时候下面那行红字；能提就是 null。 */
    private static String warning(State s, double avail) {
        double amount = value(s);
        if (amount <= 0) return null;
        if (avail <= 0) return "当前余额为" + plain(avail) + "，暂无法提现";
        if (amount > avail + 0.0001) return "可用余额不足，暂无法提现";
        return null;
    }

    /** 红字里那个数不带小数：参考图上写的是「当前余额为0」。 */
    private static String plain(double value) {
        String text = ReceiptStore.money(value);
        if (text.endsWith(".00")) text = text.substring(0, text.length() - 3);
        return text;
    }

    /**
     * 光标旁边那个量级：五位数是万，七位数是百万 —— 参考图上 1234568 的旁边就写着 百万。太小的时候
     * 不写（那几件东西整行都不出现）。
     */
    private static String unit(State s) {
        if (s == null || s.amount == null) return "";
        int digits = 0;
        for (int i = 0; i < s.amount.length(); i++) {
            if (Character.isDigit(s.amount.charAt(i))) digits++;
        }
        switch (digits) {
            case 5: return "万";
            case 6: return "十万";
            case 7: return "百万";
            case 8: return "千万";
            default: return digits > 8 ? "亿" : "";
        }
    }

    /** 输入框里的数；还没敲或敲的不是数就是 0。 */
    public static double value(State s) {
        if (s == null || s.amount == null || s.amount.isEmpty()) return 0;
        try {
            return Double.parseDouble(s.amount);
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ 页头
    private void drawHeader(Canvas c) {
        // 返回箭头尖朝左，和参考图一样（其余几个 › 都是朝右的）
        chevronLeft(c, BACK, CHEVRON_LEFT_STROKE, INK);
        text(c, "提现", TITLE_X, TITLE_BASE, TITLE_SIZE, INK, true);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(INK);
        for (float x : DOTS_X) c.drawCircle(x, DOTS_Y, DOTS_R, paint);
        hit(28, 156, 140, 286, "back");
        hit(1136, 176, 1264, 266, "dots");
    }

    // ------------------------------------------------------------------ 到账银行卡
    private void drawBankRow(Canvas c, WithdrawStore.Card card, Bitmap logo) {
        text(c, "到账银行卡", LABEL_X, LABEL_BASE, LABEL_SIZE, INK, false);
        if (logo != null) {
            drawLogo(c, logo, BANK_ICON,
                    card != null && WithdrawStore.BESPOKE.equals(card.bank));
        } else {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffeef1f6);
            c.drawCircle(BANK_ICON.centerX(), BANK_ICON.centerY(), BANK_ICON.width() / 2f, paint);
        }
        text(c, card == null ? "" : card.title(), BANK_NAME_X, BANK_NAME_BASE, BANK_NAME_SIZE, INK, false);
        text(c, card == null ? "" : card.arrival, BANK_SUB_X, BANK_SUB_BASE, BANK_SUB_SIZE, CAPTION, false);
        chevron(c, BANK_CHEVRON, CHEVRON_STROKE, CHEVRON);
        // 整排都能点，包括名字右边的空白
        hit(40, 470, 1240, 630, "bank");
    }

    // ------------------------------------------------------------------ 提现金额 卡
    private void drawAmountCard(Canvas c, State s, double quota, double avail, String warn) {
        boolean filled = s.amount != null && !s.amount.isEmpty();
        r.set(CARD1.left, CARD1.top, CARD1.right, CARD1.bottom + (warn == null ? 0f : WARN_CARD1_SHIFT));
        roundRect(c, r, CARD_RADIUS, CARD);
        text(c, "提现金额", CARD1_TITLE_X, CARD1_TITLE_BASE, CARD1_TITLE_SIZE, INK, false);
        text(c, "¥", AMOUNT_X, AMOUNT_BASE, AMOUNT_SIZE, INK, true);
        if (filled) {
            // 光标 + 量级，然后才是那个大数字：参考图上三样都在同一个输入框里
            String unit = unit(s);
            if (!unit.isEmpty()) {
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(UNIT_CARET);
                c.drawRect(UNIT_CARET_X, UNIT_CARET_TOP, UNIT_CARET_X + UNIT_CARET_W,
                        UNIT_CARET_BOTTOM, paint);
                text(c, unit, UNIT_X, UNIT_BASE, UNIT_SIZE, CAPTION, false);
            }
            text(c, s.amount, AMOUNT_VALUE_X, AMOUNT_VALUE_BASE, AMOUNT_VALUE_SIZE, INK, true);
            // 有数字的时候右边让给清空用的 ⊗，可用余额那一行不画（参考图就是这样）
            clearButton(c);
        } else {
            textRight(c, "可用余额 ¥" + ReceiptStore.money(avail), AVAIL_RIGHT, AVAIL_BASE,
                    AVAIL_SIZE, CAPTION, false);
        }
        rule(c, DIVIDER1_Y, DIVIDER_L, DIVIDER_R);
        if (warn != null) text(c, warn, WARN_X, WARN_BASE, WARN_SIZE, WARN_RED, false);
        float quotaBase = QUOTA_BASE + (warn == null ? 0f : WARN_QUOTA_SHIFT);
        text(c, "剩余免费额度 ¥" + ReceiptStore.money(quota), QUOTA_X, quotaBase, QUOTA_SIZE,
                FAINT, false);
        drawArt(c, "info", INFO, warn == null ? 0f : WARN_QUOTA_SHIFT);
        hit(55, 1000, 1185, 1150, "amount");
        if (filled) hit(CLEAR.left - 6, CLEAR.top - 6, CLEAR.right + 6, CLEAR.bottom + 6, "clear");
        hit(60, quotaBase - 55, 660, quotaBase + 17, "quota");
        hit(660, quotaBase - 55, 740, quotaBase + 17, "info");
    }

    /** 输入框右边那个 ⊗：把它清空。 */
    private void clearButton(Canvas c) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(CLEAR_DISC);
        c.drawCircle(CLEAR.centerX(), CLEAR.centerY(), CLEAR.width() / 2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(CLEAR_CROSS);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.WHITE);
        float arm = CLEAR.width() * 0.21f;
        path.reset();
        path.moveTo(CLEAR.centerX() - arm, CLEAR.centerY() - arm);
        path.lineTo(CLEAR.centerX() + arm, CLEAR.centerY() + arm);
        path.moveTo(CLEAR.centerX() + arm, CLEAR.centerY() - arm);
        path.lineTo(CLEAR.centerX() - arm, CLEAR.centerY() + arm);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    // ------------------------------------------------------------------ 去… 卡
    /** shift 是余额不够那行红字顶下来的距离：整张卡连同它里面的东西一起往下走。 */
    private void drawServiceCard(Canvas c, float shift) {
        r.set(CARD2.left, CARD2.top + shift, CARD2.right, CARD2.bottom + shift);
        roundRect(c, r, CARD_RADIUS, CARD);
        for (int i = 0; i < ROW_ICON.length; i++) {
            drawArt(c, i == 0 ? "yulibao" : i == 1 ? "bill" : "member", ROW_ICON[i], shift);
        }
        for (int i = 0; i < ROW_TITLE.length; i++) {
            text(c, ROW_TITLE[i], ROW_TEXT_X, ROW_TITLE_BASE[i] + shift, ROW_TITLE_SIZE, INK, false);
            text(c, ROW_SUB[i], ROW_TEXT_X, ROW_SUB_BASE[i] + shift, ROW_SUB_SIZE, CAPTION, false);
            chevron(c, ROW_CHEVRON[i].left, ROW_CHEVRON[i].top + shift, ROW_CHEVRON[i].right,
                    ROW_CHEVRON[i].bottom + shift, CHEVRON_STROKE, CHEVRON);
            float top = (i == 0 ? CARD2.top : ROW_RULE_Y[i - 1]) + shift;
            float bottom = (i == ROW_TITLE.length - 1 ? CARD2.bottom : ROW_RULE_Y[i]) + shift;
            hit(CARD2.left + 14, top + 6, CARD2.right - 14, bottom - 6, "row:" + i);
        }
        if (ROW_BADGE[0] != null) drawArt(c, "badgeLhb", ROW_BADGE[0], shift);
        if (ROW_BADGE[2] != null) drawArt(c, "badgeYuan", ROW_BADGE[2], shift);
        for (float y : ROW_RULE_Y) rule(c, y + shift, ROW_RULE_L, ROW_RULE_R);
    }

    // ------------------------------------------------------------------ 提现 按钮
    private void drawButton(Canvas c, boolean active) {
        roundRect(c, BUTTON, BUTTON_RADIUS, active ? ACTION_ON : ACTION_OFF);
        centerText(c, "提现", BUTTON_CX, BUTTON_BASE, BUTTON_SIZE, Color.WHITE, true);
        hit(BUTTON.left, BUTTON.top, BUTTON.right, BUTTON.bottom, "submit");
    }

    // ------------------------------------------------------------------ 数字键盘
    private void drawKeypad(Canvas c, boolean active, boolean password) {
        if (password) {
            drawPinKeypad(c);
            return;
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PANEL);
        c.drawRect(0, PANEL_TOP, W, PANEL_BOTTOM, paint);
        // 面板把页面盖住了：它上面除了下面登记的那些键，点哪儿都不该落到页面上去
        hit(0, PANEL_TOP, W, H, "noop");

        chevronDown(c, CHEVRON_DOWN, CHEVRON_STROKE, 0xff8a8f99);
        hit(600, 1866, 690, 1930, "key:hide");

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                String digit = KEY_LABEL[row][col];
                key(c, KEY_L[col], KEY_R[col], row, digit, "key:" + digit);
            }
        }

        key(c, KEY_L[KEY_ZERO_FROM], KEY_R[KEY_ZERO_TO], 3, "0", "key:0");
        // 小数点不跟数字一条基线：参考图里它就落在键心上，还比列心偏右一点
        key(c, KEY_L[KEY_DOT_COL], KEY_R[KEY_DOT_COL], 3, ".", "key:dot", DOT_DROP, DOT_SHIFT_X);

        drawArt(c, "del", DEL);
        hit(KEY_L[3], KEY_T[0], KEY_R[3], KEY_T[0] + KEY_H, "key:del");

        roundRect(c, ACTION, KEY_RADIUS + 4, active ? ACTION_ON : ACTION_OFF);
        centerText(c, "提现", ACTION.centerX(), ACTION.centerY() + ACTION_TEXT_DROP,
                ACTION_TEXT_SIZE, Color.WHITE, true);
        hit(ACTION.left, ACTION.top, ACTION.right, ACTION.bottom, "key:submit");
    }

    /** 一个白键：数字落在键心偏下一点的地方，和参考图一样。 */
    private void key(Canvas c, float l, float r, int row, String label, String token) {
        key(c, l, r, row, label, token, DIGIT_DROP, 0f);
    }

    /** 同一个键，drop / shift 给那些不跟数字站一队的字（小数点）自己调。 */
    private void key(Canvas c, float l, float r, int row, String label, String token,
                     float drop, float shift) {
        float top = KEY_T[row];
        roundRect(c, l, top, r, top + KEY_H, KEY_RADIUS, KEY);
        centerText(c, label, (l + r) / 2f + shift, top + KEY_H / 2f + drop, DIGIT_SIZE, DIGIT, false);
        hit(l, top, r, top + KEY_H, token);
    }

    /**
     * 支付密码那把键盘，和金额那把是两副脸 —— 参考图那张照片上量出来的是：
     *
     * <ul>
     *   <li>一整片白从键盘顶边铺到底，没有浅灰面板，也没有一张张白键；</li>
     *   <li>格线把它分成 **三列四行**（三列等宽，1/6、1/2、5/6 处正好是三个数字的中心），
     *       线是极浅的灰，横三条、竖两条；</li>
     *   <li>1…9 占前三行三列，数字是中灰的；</li>
     *   <li>最后一排只有两样：<b>0 落在中间那一列</b>、<b>⌫ 落在右边那一列</b>（左边那格空着），
     *       ⌫ 是细描边的空心箭头，不是实心那个。</li>
     * </ul>
     *
     * 金额那把键盘还是老样子（浅灰面板 + 白圆角键 + 蓝色提现键，四列），两者不共用一套几何。
     */
    private void drawPinKeypad(Canvas c) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PIN_SHEET);
        c.drawRect(0, PIN_TOP, W, PANEL_BOTTOM, paint);
        hit(0, PIN_TOP, W, H, "noop");

        float colW = W / PIN_COLS;
        float rowH = (PANEL_BOTTOM - PIN_TOP) / PIN_ROWS;

        // 格线：竖两条、横三条，都是极浅的灰
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(PIN_LINE_W);
        paint.setColor(PIN_LINE);
        for (int i = 1; i < PIN_COLS; i++) {
            float x = i * colW;
            c.drawLine(x, PIN_TOP, x, PANEL_BOTTOM, paint);
        }
        for (int i = 1; i < PIN_ROWS; i++) {
            float y = PIN_TOP + i * rowH;
            c.drawLine(0, y, W, y, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        // 1…9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < PIN_COLS; col++) {
                String digit = KEY_LABEL[row][col];
                pinKey(c, col, row, colW, rowH, digit, PIN_INK);
                hit(col * colW, PIN_TOP + row * rowH, (col + 1) * colW,
                        PIN_TOP + (row + 1) * rowH, "pin:" + digit);
            }
        }
        // 最后一排：中间那格是 0，右边那格是 ⌫，左边那格空着
        pinKey(c, 1, 3, colW, rowH, "0", PIN_INK);
        hit(colW, PIN_TOP + 3 * rowH, 2 * colW, PANEL_BOTTOM, "pin:0");
        float cx = 2.5f * colW;
        float cy = PIN_TOP + 3.5f * rowH;
        r.set(cx - PIN_BACK_W / 2f, cy - PIN_BACK_H / 2f, cx + PIN_BACK_W / 2f,
                cy + PIN_BACK_H / 2f);
        backspace(c, r, PIN_BACK_STROKE, PIN_INK);
        hit(2 * colW, PIN_TOP + 3 * rowH, W, PANEL_BOTTOM, "pin:del");
    }

    /** 密码键盘上的一个字：白底上灰字，没有键的底，字号是照片上量的。 */
    private void pinKey(Canvas c, int col, int row, float colW, float rowH, String label, int colour) {
        float size = PIN_DIGIT_SIZE;
        centerText(c, label, (col + 0.5f) * colW,
                PIN_TOP + (row + 0.5f) * rowH + size * 0.39f, size, colour, false);
    }

    /** ⌫：一个左边带尖的空心方框，里面一个 ×。 */
    private void backspace(Canvas c, RectF box, float stroke, int colour) {
        float tail = box.height() * 0.38f;
        float inset = stroke / 2f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(colour);
        path.reset();
        path.moveTo(box.right - inset, box.top + inset);
        path.lineTo(box.left + tail, box.top + inset);
        path.lineTo(box.left + inset, box.centerY());
        path.lineTo(box.left + tail, box.bottom - inset);
        path.lineTo(box.right - inset, box.bottom - inset);
        path.close();
        c.drawPath(path, paint);
        float arm = box.height() * 0.17f;
        path.reset();
        path.moveTo(box.centerX() + tail * 0.18f - arm, box.centerY() - arm);
        path.lineTo(box.centerX() + tail * 0.18f + arm, box.centerY() + arm);
        path.moveTo(box.centerX() + tail * 0.18f + arm, box.centerY() - arm);
        path.lineTo(box.centerX() + tail * 0.18f - arm, box.centerY() + arm);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    // ------------------------------------------------------------------ 支付密码
    /** 密码位数：参考图里那排格子正好六个。 */
    public static final int PIN_LENGTH = 6;
    // 下面这一套是拿照片透视校正成 1280x2816 之后量的（_withdraw/flatten_pin.py + probe_flat.py）：
    // 面板 (124,628)-(1158,1459)，✕ 58 见方，标题字面 830..882、副标题 937..985、
    // 六个格子 (197,1055)-(1082,1218)（每格 147.5 宽）、忘记密码？字面 1290..1341。
    private static final RectF PW_PANEL = new RectF(124f, 628f, 1158f, 1459f);
    /**
     * 上面那一套是参考图上量到的实际尺寸（见 README），但「按参考图原样」在真机上看着偏大，
     * 所以整块面板连同里面的东西一起按这个比例缩一点，缩的中心还是面板自己的中心。
     */
    private static final float PW_SCALE = 0.88f;
    private static final float PW_RADIUS = 24f;
    private static final RectF PW_CLOSE = new RectF(190f, 683f, 248f, 741f);
    private static final float PW_CLOSE_STROKE = 4.6f;
    private static final float PW_TITLE_BASE = 880f;
    private static final float PW_TITLE_SIZE = 70f;
    private static final float PW_SUB_BASE = 983f;
    private static final float PW_SUB_SIZE = 57f;
    private static final int PW_SUB_INK = 0xff6b7076;
    private static final RectF PW_CELLS = new RectF(197f, 1055f, 1082f, 1218f);
    private static final float PW_CELL_RADIUS = 8f;
    private static final float PW_CELL_STROKE = 2f;
    private static final int PW_CELL_LINE = 0xffe6e8ec;
    private static final float PW_DOT_R = 10f;
    private static final int PW_DOT = 0xff26282b;
    private static final float PW_FORGOT_BASE = 1339f;
    private static final float PW_FORGOT_SIZE = 53f;
    private static final int PW_BLUE = 0xff1677ff;

    /**
     * 支付密码的键盘和金额那把不一样：参考图里它是一整片白，格子之间只有几条极浅的细线，
     * ⌫ 是一个细描边的空心箭头 —— 没有浅灰面板，也没有一张张白键。数字和 ⌫ 比照片再深一点。
     */
    private static final int PIN_SHEET = 0xffffffff;
    private static final int PIN_INK = 0xff33363a;
    private static final int PIN_LINE = 0xffe9eaee;
    private static final float PIN_LINE_W = 2f;
    private static final float PIN_BACK_STROKE = 4.6f;
    /** 密码键盘的格子：三列四行，最后一排只有 0 和 ⌫；片子从 2030 铺到 2752.9。 */
    private static final int PIN_COLS = 3;
    private static final int PIN_ROWS = 4;
    private static final float PIN_TOP = 2030f;
    private static final float PIN_DIGIT_SIZE = 84f;
    private static final float PIN_BACK_W = 82f;
    private static final float PIN_BACK_H = 56f;

    /**
     * 支付密码那一层：把键盘上面那半截压暗，再盖一块白面板。
     *
     * <p>暗色只压到 {@link #PANEL_TOP} 为止 —— 键盘那一块是亮的，和参考图一样：照片上键盘那片白
     * 明显比它上面的页面亮一大截，密码键盘要是也跟着压暗，就只剩一片深灰，看着像「没有键盘」。
     */
    /** 面板里的坐标按 {@link #PW_SCALE} 缩过之后落在哪儿。 */
    private static float pwX(float x) {
        return PW_PANEL.centerX() + (x - PW_PANEL.centerX()) * PW_SCALE;
    }

    private static float pwY(float y) {
        return PW_PANEL.centerY() + (y - PW_PANEL.centerY()) * PW_SCALE;
    }

    private void drawPassword(Canvas c, State s) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xbf000000);
        c.drawRect(0, 0, W, PIN_TOP, paint);
        // 压暗的那半截吃掉所有点击，免得落到后面的页面上
        hit(0, 0, W, PIN_TOP, "noop");

        // 整块面板连里面的东西一起缩放，坐标仍然是参考图上量的那一套
        int save = c.save();
        c.scale(PW_SCALE, PW_SCALE, PW_PANEL.centerX(), PW_PANEL.centerY());
        roundRect(c, PW_PANEL, PW_RADIUS, CARD);
        chevronCross(c, PW_CLOSE, PW_CLOSE_STROKE, 0xffa9aeb6);
        centerText(c, "请输入支付密码", PW_PANEL.centerX(), PW_TITLE_BASE, PW_TITLE_SIZE, INK, false);
        centerText(c, "提现金额 ¥" + ReceiptStore.money(value(s)), PW_PANEL.centerX(), PW_SUB_BASE,
                PW_SUB_SIZE, PW_SUB_INK, false);

        // 六个格子：整块圆角描边 + 里面的五条竖线 + 已经敲进去的黑点
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(PW_CELL_STROKE);
        paint.setColor(PW_CELL_LINE);
        c.drawRoundRect(PW_CELLS, PW_CELL_RADIUS, PW_CELL_RADIUS, paint);
        float step = PW_CELLS.width() / PIN_LENGTH;
        for (int i = 1; i < PIN_LENGTH; i++) {
            float x = PW_CELLS.left + i * step;
            c.drawLine(x, PW_CELLS.top, x, PW_CELLS.bottom, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PW_DOT);
        for (int i = 0; i < s.pin.length(); i++) {
            c.drawCircle(PW_CELLS.left + (i + 0.5f) * step, PW_CELLS.centerY(), PW_DOT_R, paint);
        }

        centerText(c, "忘记密码？", PW_PANEL.centerX(), PW_FORGOT_BASE, PW_FORGOT_SIZE, PW_BLUE, false);
        c.restore();

        hit(pwX(PW_CLOSE.left - 20), pwY(PW_CLOSE.top - 20), pwX(PW_CLOSE.right + 20),
                pwY(PW_CLOSE.bottom + 20), "pin:close");
        hit(pwX(PW_PANEL.centerX() - 220), pwY(PW_FORGOT_BASE - 46), pwX(PW_PANEL.centerX() + 220),
                pwY(PW_FORGOT_BASE + 16), "pin:forgot");
    }

    /** 支付密码那层右上角/左上角那个 ✕。 */
    private void chevronCross(Canvas c, RectF box, float stroke, int colour) {
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

    // ------------------------------------------------------------------ 热区
    private void hit(float l, float t, float rr, float b, String token) {
        hitRects.add(new float[]{l, t, rr, b});
        hitTokens.add(token);
    }

    /** 返回落在 (x, y) 上的动作 token；坐标是屏幕设计坐标。 */
    public String hitTest(State s, float x, float y) {
        for (int i = hitRects.size() - 1; i >= 0; i--) {
            float[] b = hitRects.get(i);
            if (x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]) return hitTokens.get(i);
        }
        return "noop";
    }

    // ------------------------------------------------------------------ 图元
    /**
     * 一张图标：银行那几张是方的，按框铺满就行；用户自己上传的图长宽不一定，按短边放大裁成方的，
     * 和模块里别的头像一样。
     */
    private void drawLogo(Canvas c, Bitmap bmp, RectF box, boolean cover) {
        if (bmp == null) return;
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

    private void drawArt(Canvas c, String name, RectF box) {
        if (box == null) return;
        Bitmap bmp = art.get(name);
        if (bmp == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        c.drawBitmap(bmp, null, box, paint);
    }

    /** 同一张图，整个往下挪 shift：红字把卡片顶下去时里面的东西跟着走。 */
    private void drawArt(Canvas c, String name, RectF box, float shift) {
        if (box == null) return;
        r.set(box.left, box.top + shift, box.right, box.bottom + shift);
        drawArt(c, name, r);
    }

    private void roundRect(Canvas c, RectF box, float radius, int colour) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(colour);
        c.drawRoundRect(box, radius, radius, paint);
    }

    private void roundRect(Canvas c, float l, float t, float rr, float b, float radius, int colour) {
        r.set(l, t, rr, b);
        roundRect(c, r, radius, colour);
    }

    private void rule(Canvas c, float y, float l, float rr) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(RULE);
        c.drawRect(l, y, rr, y + RULE_H, paint);
    }

    /** The app's thin › : two strokes meeting on the box's right edge. */
    private void chevron(Canvas c, RectF box, float stroke, int colour) {
        chevron(c, box.left, box.top, box.right, box.bottom, stroke, colour);
    }

    private void chevron(Canvas c, float l, float t, float rr, float b, float stroke, int colour) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(colour);
        float inset = stroke / 2f;
        path.reset();
        path.moveTo(l + inset, t + inset);
        path.lineTo(rr - inset, (t + b) / 2f);
        path.lineTo(l + inset, b - inset);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** 返回箭头：同一个 › 反过来，尖朝左。 */
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

    /** The keypad's collapse handle: a ⌄. */
    private void chevronDown(Canvas c, RectF box, float stroke, int colour) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(colour);
        float inset = stroke / 2f;
        path.reset();
        path.moveTo(box.left + inset, box.top + inset);
        path.lineTo(box.centerX(), box.bottom - inset);
        path.lineTo(box.right - inset, box.top + inset);
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

    // ------------------------------------------------------------------ 键盘的动作
    /** 键盘上「提现」那一格的颜色，宿主画按钮时也用它。 */
    public static int actionColour(boolean active) {
        return active ? ACTION_ON : ACTION_OFF;
    }
}
