package com.luoyun.doutu.features.zhi.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.luoyun.doutu.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 支付消息页（小支主页 - 消息 - 消息盒子，也是「支」宫格里的「支付消息」）。
 *
 * <p>整页照着参考截图 {@code 88f4205fc7447cd303981f13305f8cc2.jpg} 1:1 重建：参考图被切成
 * 「顶栏」「本月统计」「八张卡片」这样一条条整宽的模块，各自单独抠出来后按参考图里的坐标贴回
 * 画布，画布宽 1280（和参考图同宽），整页高 7545，跟着手指滚。参考图不自带状态栏那一段
 * （0..136）不切进模块，顶上留空由页面底色接着，状态栏图标交给系统。
 *
 * <p>默认状态什么都不叠加：所有文字（商户名、时间、金额、付款方式……）都随模块位图一起贴出来，
 * 与参考图逐像素一致。只有用户点开某一行把它改成别的内容之后，页面才会用小色块盖掉原位置、再
 * 画上用户自己的值，改过的值存在 {@code PayMsgStore} 里，下次进来还是他填的。
 *
 * <p>另外五张商家图标单独抠出来存着：点进某一条消息时，那条消息会变成一个 {@code Bill} 交给
 * 账单详情页，图标就跟着过去，和账单页自己用的那套图标是同一份。
 */
public final class PayMsgScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;
    /** 顶栏（含状态栏那一段）固定不滚，下面从 348 开始滚。 */
    public static final float HEAD_BOTTOM = 348f;
    /** 八张卡片最后一张的下沿 7513，再留一点底色。 */
    public static final float CONTENT_H = 7545f;

    private static final int PAGE_BG = 0xfff5f5f5;
    private static final int CARD = 0xffffffff;
    private static final int INK = 0xff333333;
    private static final int GREY = 0xff999999;
    private static final int VALUE = 0xff666666;
    private static final int BLUE = 0xff1677ff;
    private static final int CHIP_BG = 0xffedf4fe;

    /** 一行文字的画法：普通文字、带小号「¥」的金额、浅蓝胶囊。 */
    public static final int PLAIN = 0, MONEY = 1, CHIP = 2;

    private static final float CARD_L = 28f;
    private static final float CARD_R = 1252f;
    private static final float CENTER = 640f;

    public static final class State {
        public float scroll;
        public final Map<String, String> texts;

        public State(Map<String, String> texts) {
            this.texts = texts;
        }
    }

    /** 一行可以点开改的内容：位置、默认值、字体，以及它属于哪张卡片。 */
    public static final class Field {
        public final String key;
        public final String def;
        public final float left, top, right, bottom;
        public final float x;
        public final float baseline;
        public final float size;
        public final int color;
        public final int kind;
        public final Paint.Align align;
        public final boolean numeric;
        /** 位图里那一行和这张卡片对不上（第 8 张卡片借了第 7 张的图），所以永远自己画。 */
        public boolean force;

        Field(String key, String def, float left, float top, float right, float bottom,
              float x, float baseline, float size, int color, Paint.Align align, int kind,
              boolean numeric) {
            this.key = key;
            this.def = def;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.x = x;
            this.baseline = baseline;
            this.size = size;
            this.color = color;
            this.align = align;
            this.kind = kind;
            this.numeric = numeric;
        }
    }

    // ------------------------------------------------------------------ 卡片

    /** 每张卡片的 {上沿, 高, 型}；型 0 有「付款方式 + 一行附注」，1 只有付款方式，2 是闲鱼那种带胶囊和两行操作的。 */
    private static final float[][] CARDS = {
            {712, 669, 0},
            {1424, 587, 1},
            {2054, 669, 0},
            {2766, 997, 2},
            {3806, 587, 1},
            {4436, 997, 2},
            {5476, 997, 2},
            {6516, 997, 2}};

    /** 每张卡片贴的模块位图；最后一张卡片在原图里被截断，直接借用第 7 张。 */
    private static final String[] CARD_ART = {
            "pm_c1", "pm_c2", "pm_c3", "pm_c4", "pm_c5", "pm_c6", "pm_c7", "pm_c7"};

    /** 每张卡片左上角图标的那张抠图，点进详情页时跟着过去。 */
    private static final String[] CARD_ICON = {
            "pm_ic_shop", "pm_ic_cat", "pm_ic_huabei", "pm_ic_xianyu",
            "pm_ic_mobile", "pm_ic_xianyu", "pm_ic_xianyu", "pm_ic_xianyu"};

    /** 每张卡片上的文字：商户名、时间、状态、金额、左标签、值、右标签、值、第一行操作、第二行操作。 */
    private static final String[][] CARD_TEXT = {
            {"杭州深度求索", "11:44", "付款成功", "19.92",
                    "付款方式", "招商银行储蓄卡(4646)", "抵扣金额", "红包 0.08", "", ""},
            {"南京爱维云网络科技有限公司", "昨天 20:44", "付款成功", "20.00",
                    "付款方式", "招商银行储蓄卡(4646)", "", "", "", ""},
            {"花呗", "10月11日 21:40", "付款成功", "147.87",
                    "付款方式", "招商银行储蓄卡(4646)", "还款到", "花呗", "", ""},
            {"闲鱼", "10月8日 20:13", "自动扣款成功", "1.40",
                    "付款方式", "招商银行储蓄卡(4646)", "交易对象", "x***0",
                    "管理当前自动扣款服务", "进入闲鱼"},
            {"中国移动", "10月3日 10:08", "付款成功", "50.00",
                    "付款方式", "招商银行储蓄卡(4646)", "", "", "", ""},
            {"闲鱼", "10月1日 15:31", "自动扣款成功", "36.00",
                    "付款方式", "招商银行储蓄卡(4646)", "交易对象", "x***0",
                    "管理当前自动扣款服务", "进入闲鱼"},
            {"闲鱼", "10月1日 15:10", "自动扣款成功", "1.40",
                    "付款方式", "招商银行储蓄卡(4646)", "交易对象", "x***0",
                    "管理当前自动扣款服务", "进入闲鱼"},
            {"闲鱼", "10月1日 14:56", "自动扣款成功", "1.40",
                    "付款方式", "招商银行储蓄卡(4646)", "交易对象", "x***0",
                    "管理当前自动扣款服务", "进入闲鱼"}};

    private static final String[] SUM_NUM = {"0", "4", "0", "0"};
    private static final String[] SUM_LABEL = {"大额消费", "自动扣款", "花呗付款", "分期付款"};
    private static final float[] SUM_NUM_CX = {199f, 492f, 787f, 1081f};
    private static final float[] SUM_LAB_CX = {198f, 493f, 786f, 1080f};

    /** 本月的那个「10月统计支出」：跟着当前月份走，改过之后就用用户自己的。 */
    private static String monthTitle() {
        return (Calendar.getInstance().get(Calendar.MONTH) + 1) + "月统计支出";
    }

    public static final List<Field> FIELDS = new ArrayList<>();

    static {
        float[] numL = {140f, 432f, 728f, 1022f};
        for (int i = 0; i < SUM_NUM.length; i++) {
            FIELDS.add(new Field("sum.n" + (i + 1), SUM_NUM[i], numL[i], 494f, numL[i] + 120f, 566f,
                    SUM_NUM_CX[i], 559f, 79f, INK, Paint.Align.CENTER, PLAIN, true));
            FIELDS.add(new Field("sum.l" + (i + 1), SUM_LABEL[i], numL[i] - 60f, 576f,
                    numL[i] + 180f, 628f, SUM_LAB_CX[i], 620f, 41f, GREY, Paint.Align.CENTER,
                    PLAIN, false));
        }
        FIELDS.add(new Field("sum.title", monthTitle(), 66f, 396f, 430f, 452f, 71f, 445f, 47f,
                INK, Paint.Align.LEFT, PLAIN, false));
        FIELDS.add(new Field("sum.spend", "本月支出 ¥131.11，较上月降低", 600f, 396f, 1198f, 452f,
                1195f, 445f, 44f, GREY, Paint.Align.RIGHT, PLAIN, false));

        for (int i = 0; i < CARDS.length; i++) {
            float top = CARDS[i][0];
            int type = (int) CARDS[i][2];
            String[] text = CARD_TEXT[i];
            String p = "c" + (i + 1) + ".";

            FIELDS.add(new Field(p + "name", text[0], 198f, top + 58f, 1012f, top + 112f,
                    205f, top + 106f, 50f, INK, Paint.Align.LEFT, PLAIN, false));
            FIELDS.add(new Field(p + "time", text[1], 198f, top + 118f, 900f, top + 158f,
                    207f, top + 152f, 38f, GREY, Paint.Align.LEFT, PLAIN, false));

            if (type == 2) {
                FIELDS.add(new Field(p + "status", text[2], 452f, top + 194f, 828f, top + 312f,
                        CENTER, top + 277f, 49f, BLUE, Paint.Align.CENTER, CHIP, false));
                FIELDS.add(new Field(p + "amount", text[3], 300f, top + 312f, 984f, top + 402f,
                        CENTER, top + 391f, 104f, INK, Paint.Align.LEFT, MONEY, true));
            } else {
                FIELDS.add(new Field(p + "status", text[2], 410f, top + 206f, 872f, top + 272f,
                        CENTER, top + 264f, 49f, GREY, Paint.Align.CENTER, PLAIN, false));
                FIELDS.add(new Field(p + "amount", text[3], 300f, top + 282f, 984f, top + 372f,
                        CENTER, top + 363f, 104f, INK, Paint.Align.LEFT, MONEY, true));
            }

            float row1 = type == 2 ? top + 564f : top + 532f;
            float row2 = type == 2 ? top + 640f : top + 614f;
            FIELDS.add(new Field(p + "l1", text[4], 78f, row1 - 46f, 282f, row1 + 10f,
                    85f, row1, 45f, GREY, Paint.Align.LEFT, PLAIN, false));
            FIELDS.add(new Field(p + "v1", text[5], 290f, row1 - 50f, 1214f, row1 + 14f,
                    297f, row1, 46f, VALUE, Paint.Align.LEFT, PLAIN, false));
            if (!text[6].isEmpty()) {
                FIELDS.add(new Field(p + "l2", text[6], 78f, row2 - 46f, 282f, row2 + 10f,
                        85f, row2, 45f, GREY, Paint.Align.LEFT, PLAIN, false));
                FIELDS.add(new Field(p + "v2", text[7], 290f, row2 - 50f, 1214f, row2 + 14f,
                        297f, row2, 46f, VALUE, Paint.Align.LEFT, PLAIN, false));
            }
            if (type == 2) {
                FIELDS.add(new Field(p + "a1", text[8], 78f, top + 726f, 1146f, top + 808f,
                        85f, top + 795f, 50f, INK, Paint.Align.LEFT, PLAIN, false));
                FIELDS.add(new Field(p + "a2", text[9], 78f, top + 876f, 1146f, top + 958f,
                        85f, top + 944f, 50f, INK, Paint.Align.LEFT, PLAIN, false));
            }
        }

        // 第 8 张卡片借的是第 7 张的位图，时间那一行长的不一样，所以那一行永远自己画
        for (Field f : FIELDS) if (f.key.equals("c8.time")) f.force = true;
    }

    // ------------------------------------------------------------------ 资源

    private final LinkedHashMap<String, Bitmap> art = new LinkedHashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    public void load(Resources res) {
        art.clear();
        art.put("pm_head", BitmapFactory.decodeResource(res, R.drawable.pm_head));
        art.put("pm_sum", BitmapFactory.decodeResource(res, R.drawable.pm_sum));
        art.put("pm_c1", BitmapFactory.decodeResource(res, R.drawable.pm_c1));
        art.put("pm_c2", BitmapFactory.decodeResource(res, R.drawable.pm_c2));
        art.put("pm_c3", BitmapFactory.decodeResource(res, R.drawable.pm_c3));
        art.put("pm_c4", BitmapFactory.decodeResource(res, R.drawable.pm_c4));
        art.put("pm_c5", BitmapFactory.decodeResource(res, R.drawable.pm_c5));
        art.put("pm_c6", BitmapFactory.decodeResource(res, R.drawable.pm_c6));
        art.put("pm_c7", BitmapFactory.decodeResource(res, R.drawable.pm_c7));
        art.put("pm_ic_shop", BitmapFactory.decodeResource(res, R.drawable.pm_ic_shop));
        art.put("pm_ic_cat", BitmapFactory.decodeResource(res, R.drawable.pm_ic_cat));
        art.put("pm_ic_huabei", BitmapFactory.decodeResource(res, R.drawable.pm_ic_huabei));
        art.put("pm_ic_xianyu", BitmapFactory.decodeResource(res, R.drawable.pm_ic_xianyu));
        art.put("pm_ic_mobile", BitmapFactory.decodeResource(res, R.drawable.pm_ic_mobile));
    }

    /** 点进某一条消息时跟着去详情页的那张图标。 */
    public Bitmap iconOf(int card) {
        return card >= 0 && card < CARD_ICON.length ? art.get(CARD_ICON[card]) : null;
    }

    public float maxScroll(State s) {
        return Math.max(0f, CONTENT_H - H);
    }

    // ------------------------------------------------------------------ 画

    public void draw(Canvas c, State s) {
        c.drawColor(PAGE_BG);
        // 顶栏连着状态栏那一段一起铺底色：参考图自带的状态栏内容不画，交给系统
        drawArt(c, "pm_head", 0f, 136f);

        c.save();
        c.clipRect(0f, HEAD_BOTTOM, W, H);
        c.translate(0f, -s.scroll);
        drawArt(c, "pm_sum", 0f, 348f);
        for (int i = 0; i < CARDS.length; i++) {
            drawArt(c, CARD_ART[i], 0f, CARDS[i][0]);
        }
        drawEdits(c, s);
        c.restore();
    }

    private void drawEdits(Canvas c, State s) {
        for (Field f : FIELDS) {
            String changed = s.texts.get(f.key);
            if (changed == null && !f.force) continue;
            String value = changed == null ? f.def : changed;
            switch (f.kind) {
                case MONEY:
                    drawMoney(c, f, value);
                    break;
                case CHIP:
                    drawChip(c, f, value);
                    break;
                default:
                    patch(c, f.left, f.top, f.right, f.bottom, CARD);
                    // 改得比原来长很多也不能糊出这一行的范围，放不下就把字号一点点收小
                    float size = fit(value, f.size, f.right - f.left);
                    text(c, value, f.x, f.baseline, size, f.color, f.align, false);
                    break;
            }
        }
    }

    /** 金额：小一号的「¥」加上大字号的数字，整体还是居中在参考图那个位置上。 */
    private void drawMoney(Canvas c, Field f, String value) {
        patch(c, f.left, f.top, f.right, f.bottom, CARD);
        float max = f.right - f.left;
        float size = fit(value, f.size, max - measure("¥", 72f, false) - 18f);
        float yuanSize = Math.min(72f, size * 72f / f.size);
        float yuan = measure("¥", yuanSize, false);
        float digits = measure(value, size, false);
        float start = CENTER - (yuan + 18f + digits) / 2f;
        text(c, "¥", start, f.baseline, yuanSize, INK, Paint.Align.LEFT, false);
        text(c, value, start + yuan + 18f, f.baseline, size, INK, Paint.Align.LEFT, false);
    }

    /** 把一行字的字号收到能塞进 {@code max} 为止（最低到 24）。 */
    private float fit(String value, float size, float max) {
        float s = size;
        while (s > 24f && measure(value, s, false) > max) s -= 1f;
        return s;
    }

    /** 「自动扣款成功」那颗浅蓝胶囊：宽度跟着文字走，背景自己画。 */
    private void drawChip(Canvas c, Field f, String value) {
        patch(c, f.left, f.top, f.right, f.bottom, CARD);
        float size = fit(value, f.size, 760f);
        float wide = measure(value, size, false) / 2f + 22f;
        float top = f.baseline - 71f;
        float bottom = f.baseline + 23f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(CHIP_BG);
        c.drawRoundRect(CENTER - wide, top, CENTER + wide, bottom, (bottom - top) / 2f,
                (bottom - top) / 2f, paint);
        text(c, value, CENTER, f.baseline, size, BLUE, Paint.Align.CENTER, false);
    }

    private void drawArt(Canvas c, String name, float x, float y) {
        Bitmap bitmap = art.get(name);
        if (bitmap == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        c.drawBitmap(bitmap, x, y, paint);
    }

    private void patch(Canvas c, float left, float top, float right, float bottom, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRect(left, top, right, bottom, paint);
    }

    private void text(Canvas c, String value, float x, float baseline, float size, int color,
                      Paint.Align align, boolean bold) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(align);
        c.drawText(value, x, baseline, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private float measure(String value, float size, boolean bold) {
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextSize(size);
        return paint.measureText(value);
    }

    // ------------------------------------------------------------------ 点哪儿

    public String hitTest(State s, float x, float y) {
        if (y < HEAD_BOTTOM) {
            if (x < 140f) return "back";
            if (y > 160f && y < 280f) {
                if (x >= 330f && x < 610f) return "noop";
                if (x >= 690f && x < 950f) return "noop";
                if (x >= 985f && x < 1115f) return "noop";
                if (x >= 1115f) return "noop";
            }
            return "noop";
        }
        float cy = y + s.scroll;
        for (int i = FIELDS.size() - 1; i >= 0; i--) {
            Field f = FIELDS.get(i);
            if (x >= f.left && x <= f.right && cy >= f.top && cy <= f.bottom) {
                return "edit:" + f.key;
            }
        }
        for (int i = 0; i < CARDS.length; i++) {
            if (cy >= CARDS[i][0] && cy <= CARDS[i][0] + CARDS[i][1]
                    && x >= CARD_L && x <= CARD_R) {
                return "detail:" + i;
            }
        }
        return "noop";
    }

    /** 点开某一行的标题。 */
    public static String titleOf(Field f) {
        if (f.key.startsWith("sum.n")) return "修改统计数字";
        switch (f.key) {
            case "sum.title":
                return "修改统计标题";
            case "sum.spend":
                return "修改本月支出";
            default:
                break;
        }
        String tail = f.key.substring(f.key.indexOf('.') + 1);
        switch (tail) {
            case "name":
                return "修改商户名称";
            case "time":
                return "修改时间";
            case "status":
                return "修改交易状态";
            case "amount":
                return "修改金额";
            case "l1":
            case "l2":
                return "修改这一行的名称";
            case "v1":
                return "修改这一行的内容";
            case "v2":
                return "修改这一行的内容";
            case "a1":
            case "a2":
                return "修改操作名称";
            default:
                return "修改内容";
        }
    }

    public static Field field(String key) {
        for (Field f : FIELDS) if (f.key.equals(key)) return f;
        return null;
    }

    /** 第几张卡片，或者 -1。 */
    public static int cardOf(String key) {
        if (key.length() > 2 && key.charAt(0) == 'c') {
            int dot = key.indexOf('.');
            if (dot > 1) {
                try {
                    return Integer.parseInt(key.substring(1, dot)) - 1;
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    public static String money(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) return "0.00";
        try {
            return String.format(Locale.US, "%.2f", Double.parseDouble(clean.replace(",", "")));
        } catch (NumberFormatException e) {
            return value;
        }
    }

}
