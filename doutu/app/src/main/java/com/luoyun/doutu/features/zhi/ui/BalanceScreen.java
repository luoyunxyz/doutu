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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 余额页（小支主页 - 我的 - 余额）。
 *
 * <p>整页按照参考截图 {@code 8ef6d6ee62443a0f255f4eb196d85ba6.jpg} 1:1 重建：画布宽度取参考图的
 * 1280px，纵向内容 4520px，跟着手指上下滚动。参考图被切成一个个「模块」——顶部大卡、三张迷你卡、
 * 青春广场的六张卡片、底部客服与免责声明——以及返回箭头、主题、更多这几个图标，各自单独抠出来后
 * 按参考图里的坐标贴回画布；页面底色与顶部那层浅青渐变则按参考图采样后自己画出来，所以模块之间
 * 没有接缝。
 *
 * <p>默认状态什么都不用叠加：所有文字（含金额）都随模块位图一起贴出来，和参考图逐像素一致。
 * 只有当用户点开某个数字/文字把它改成别的内容之后，{@code BalanceScreen} 才会用小色块盖掉原
 * 位置、再画上用户自己的值——所以「没改过就一模一样，改过就显示新值」。
 */
public final class BalanceScreen {
    /** 参考图宽度，作为设计坐标系宽度。 */
    public static final float W = 1280f;
    /** 可见窗口高度，与 支 模块其余页面一致。 */
    public static final float H = 2816f;
    /** 整页内容高度（到底部免责声明下面一点）。 */
    public static final float CONTENT_H = 4520f;

    /** 可变状态由宿主 Activity 持有。 */
    public static final class State {
        public float scroll;
        /** 被点改过的数字，key 见 {@code BalanceActivity.FIELDS}。 */
        public final Map<String, Double> nums;
        /** 被点改过的文字。 */
        public final Map<String, String> texts;

        public State(java.util.Map<String, Double> numbers, java.util.Map<String, String> texts) {
            this.nums = numbers;
            this.texts = texts;
        }
    }

    // ------------------------------------------------------------------ 调色板
    private static final int PAGE_BG = 0xffedeef2;
    private static final int INK = 0xff26282b;
    private static final int CAPTION = 0xff8d9299;
    private static final int TITLE_BLUE = 0xff17407f;
    private static final int ORANGE_L = 0xfffdb433;
    private static final int ORANGE_R = 0xfffea03d;
    private static final int BLUE_L = 0xff87d5fc;
    private static final int BLUE_R = 0xff78cbfd;

    /** 顶部浅青渐变的底图高度；下面从 PAGE_BG 起。 */
    private static final float BG_H = 1600f;
    /** 青春广场上面那层白色柔光：从白渐到透明。 */
    private static final float PANEL_TOP = 1596f;
    private static final float PANEL_BOTTOM = 2260f;

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
        art.put("bg", BitmapFactory.decodeResource(res, R.drawable.bal_bg));
        art.put("headBack", BitmapFactory.decodeResource(res, R.drawable.bal_head_back));
        art.put("headTitle", BitmapFactory.decodeResource(res, R.drawable.bal_head_title));
        art.put("headTheme", BitmapFactory.decodeResource(res, R.drawable.bal_head_theme));
        art.put("headDots", BitmapFactory.decodeResource(res, R.drawable.bal_head_dots));
        art.put("cardTop", BitmapFactory.decodeResource(res, R.drawable.bal_card_top));
        art.put("miniYuebao", BitmapFactory.decodeResource(res, R.drawable.bal_mini_yuebao));
        art.put("miniHuabei", BitmapFactory.decodeResource(res, R.drawable.bal_mini_huabei));
        art.put("miniBank", BitmapFactory.decodeResource(res, R.drawable.bal_mini_bank));
        art.put("youthHead", BitmapFactory.decodeResource(res, R.drawable.bal_youth_head));
        art.put("l1", BitmapFactory.decodeResource(res, R.drawable.bal_l1));
        art.put("l2", BitmapFactory.decodeResource(res, R.drawable.bal_l2));
        art.put("l3", BitmapFactory.decodeResource(res, R.drawable.bal_l3));
        art.put("r1", BitmapFactory.decodeResource(res, R.drawable.bal_r1));
        art.put("r2", BitmapFactory.decodeResource(res, R.drawable.bal_r2));
        art.put("r3", BitmapFactory.decodeResource(res, R.drawable.bal_r3));
        art.put("service", BitmapFactory.decodeResource(res, R.drawable.bal_service));
        art.put("disclaimer", BitmapFactory.decodeResource(res, R.drawable.bal_disclaimer));
    }

    public float maxScroll(State s) {
        return Math.max(0f, CONTENT_H - H);
    }

    // ------------------------------------------------------------------ 入口
    public void draw(Canvas c, State s) {
        hitRects.clear();
        hitTokens.clear();

        c.drawColor(PAGE_BG);
        c.save();
        c.translate(0, -s.scroll);

        // 顶部浅青渐变（参考图里状态栏下面那一整块底色）
        drawArt(c, "bg", 0f, 0f);
        // 青春广场上面那层白色柔光
        drawPanel(c);

        // ---- 模块：按参考图坐标逐个贴回 ----
        drawArt(c, "headBack", 40f, 168f);
        drawArt(c, "headTitle", 108f, 170f);
        drawArt(c, "headTheme", 896f, 162f);
        drawArt(c, "headDots", 1136f, 162f);

        drawArt(c, "cardTop", 40f, 324f);

        drawArt(c, "miniYuebao", 40f, 1144f);
        drawArt(c, "miniHuabei", 598f, 1144f);
        drawArt(c, "miniBank", 1152f, 1144f);

        drawArt(c, "youthHead", 56f, 1626f);
        drawArt(c, "l1", 30f, 1738f);
        drawArt(c, "r1", 644f, 1738f);
        drawArt(c, "l2", 30f, 2082f);
        drawArt(c, "r2", 644f, 2556f);
        drawArt(c, "l3", 30f, 2886f);
        drawArt(c, "r3", 644f, 3320f);

        drawArt(c, "service", 500f, 4216f);
        drawArt(c, "disclaimer", 250f, 4366f);

        registerHits();

        // ---- 被用户改过的数字 / 文字，盖在原位置上 ----
        drawEdits(c, s);

        c.restore();
    }

    /**
     * 每一帧登记一遍热区。整块卡片先登记，卡片里的小数字/文字后登记——{@link #hitTest} 从后往前
     * 找，所以后登记的那块优先，点在数字上就是改数字，点在别处就是什么也不做。
     */
    private void registerHits() {
        // 顶栏
        hit(28, 156, 140, 286, "back");
        hit(886, 156, 1126, 282, "noop");
        hit(1128, 156, 1274, 282, "noop");

        // 顶部大卡：提现 进的是 支 宫格「余额提现」那一页
        hit(60, 760, 624, 912, "withdraw");
        hit(650, 760, 1204, 912, "noop");
        hit(60, 996, 420, 1066, "noop");
        hit(686, 996, 1030, 1068, "text:bal.changeNote");
        hit(1030, 996, 1150, 1068, "num:bal.change");
        hit(60, 462, 402, 632, "num:bal.avail");

        // 三张迷你卡
        // 余额宝那张迷你卡整块进余额宝页；卡里的数字热区后登记，所以点数字仍旧是改数字
        hit(40, 1144, 592, 1560, "yuebao");
        // 花呗那张迷你卡整块进花呗页
        hit(598, 1144, 1150, 1560, "huabei");
        hit(1152, 1144, 1276, 1560, "noop");
        hit(650, 1422, 952, 1506, "num:bal.huabei");
        hit(282, 1180, 508, 1238, "num:bal.yuebaoProfit");
        hit(86, 1422, 280, 1506, "num:bal.yuebaoTotal");

        // 青春广场 —— 左列
        hit(104, 1796, 592, 1874, "text:p.l1title");
        hit(30, 1738, 640, 2076, "noop");
        hit(108, 2560, 566, 2704, "text:p.l2t");
        hit(196, 2730, 472, 2834, "noop");
        hit(108, 3390, 566, 3468, "text:p.l3t");
        hit(196, 3480, 472, 3584, "noop");

        // 青春广场 —— 右列
        hit(760, 2190, 1136, 2348, "text:p.r1t");
        hit(816, 2392, 1090, 2496, "noop");
        hit(672, 3170, 1200, 3300, "text:p.r2t");
        hit(672, 3984, 1218, 4118, "text:p.r3t");

        // 底部
        hit(500, 4216, 780, 4284, "noop");
    }

    private void drawPanel(Canvas c) {
        paint.setShader(new LinearGradient(0, PANEL_TOP, 0, PANEL_BOTTOM,
                0xffffffff, 0x00ffffff, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        c.save();
        path.reset();
        r.set(16, PANEL_TOP, W - 16, PANEL_BOTTOM);
        path.addRoundRect(r, 40f, 40f, Path.Direction.CW);
        c.clipPath(path);
        c.drawRect(0, PANEL_TOP, W, PANEL_BOTTOM, paint);
        c.restore();
        paint.setShader(null);
    }

    // ------------------------------------------------------------------ 可改字段
    private void drawEdits(Canvas c, State s) {
        // 1 可用余额
        if (s.nums.containsKey("bal.avail")) {
            patch(c, 60, 468, 402, 626, 0xffffffff);
            number(c, money(s, "bal.avail", 0), 90, 608, 158, INK);
        }

        // 2 余额变动明细那一行（说明文字 + 金额）
        if (s.nums.containsKey("bal.change") || s.texts.containsKey("bal.changeNote")) {
            patch(c, 686, 1000, 1146, 1064, 0xffffffff);
            String note = s.texts.containsKey("bal.changeNote")
                    ? s.texts.get("bal.changeNote") : "余额宝-自动转入";
            String amt = money(s, "bal.change", -0.01);
            text(c, note + "，" + amt, 697, 1048, 39, CAPTION, false, Paint.Align.LEFT);
        }

        // 3 余额宝 昨日收益
        if (s.nums.containsKey("bal.yuebaoProfit")) {
            patch(c, 278, 1182, 506, 1236, 0xffffffff);
            text(c, "昨日收益:", 284, 1223, 31, 0xff9aa0a6, false, Paint.Align.LEFT);
            String v = money(s, "bal.yuebaoProfit", 0);
            text(c, v, 435, 1223, 31, 0xff9aa0a6, false, Paint.Align.LEFT);
        }

        // 4 余额宝 总金额（橙卡上）
        if (s.nums.containsKey("bal.yuebaoTotal")) {
            patchGrad(c, 86, 1426, 280, 1502, ORANGE_L, ORANGE_R);
            text(c, "¥", 100, 1480, 40, Color.WHITE, false, Paint.Align.LEFT);
            number(c, money(s, "bal.yuebaoTotal", 0), 134, 1489, 68, Color.WHITE);
        }

        // 5 花呗 可用额度（蓝卡上）
        if (s.nums.containsKey("bal.huabei")) {
            patchGrad(c, 650, 1426, 950, 1502, BLUE_L, BLUE_R);
            text(c, "¥", 660, 1480, 40, Color.WHITE, false, Paint.Align.LEFT);
            number(c, money(s, "bal.huabei", 2710.98), 694, 1489, 68, Color.WHITE);
        }

        // 6 「轻松3步走 零花钱到手」标题
        if (s.texts.containsKey("p.l1title")) {
            patch(c, 104, 1798, 590, 1872, 0xffffffff);
            text(c, s.texts.get("p.l1title"), 119, 1854, 43, TITLE_BLUE, true, Paint.Align.LEFT);
        }

        // 7 开通我的专属零花钱 / 续费快乐假期
        if (s.texts.containsKey("p.l2t")) {
            patch(c, 108, 2562, 566, 2702, 0xffffffff);
            String[] lines = splitLines(s.texts.get("p.l2t"),
                    new String[]{"开通我的专属零花钱", "续费快乐假期"});
            text(c, lines[0], 122, 2620, 44, INK, false, Paint.Align.LEFT);
            text(c, lines[1], 206, 2684, 44, INK, false, Paint.Align.LEFT);
        }

        // 8 开通小荷包无痛攒钱
        if (s.texts.containsKey("p.l3t")) {
            patch(c, 108, 3392, 566, 3466, 0xffffffff);
            text(c, s.texts.get("p.l3t"), 122, 3450, 44, INK, false, Paint.Align.LEFT);
        }

        // 9 零花钱轻松到手 / 表情包帮我开口
        if (s.texts.containsKey("p.r1t")) {
            patch(c, 760, 2192, 1134, 2346, 0xffffffff);
            String[] lines = splitLines(s.texts.get("p.r1t"),
                    new String[]{"零花钱轻松到手", "表情包帮我开口"});
            text(c, lines[0], 783, 2247, 46, INK, false, Paint.Align.LEFT);
            text(c, lines[1], 781, 2325, 46, INK, false, Paint.Align.LEFT);
        }

        // 10 奶龙花语秘境皮肤套装 / 有效期180天
        if (s.texts.containsKey("p.r2t")) {
            patch(c, 672, 3172, 1198, 3298, 0xffffffff);
            String[] lines = splitLines(s.texts.get("p.r2t"),
                    new String[]{"奶龙花语秘境皮肤套装，", "有效期180天"});
            text(c, lines[0], 683, 3238, 44, INK, false, Paint.Align.LEFT);
            text(c, lines[1], 683, 3283, 44, INK, false, Paint.Align.LEFT);
        }

        // 11 聚会分账的3个潜规则 / 99%的人都不知道（附…
        if (s.texts.containsKey("p.r3t")) {
            patch(c, 672, 3986, 1216, 4116, 0xffffffff);
            String[] lines = splitLines(s.texts.get("p.r3t"),
                    new String[]{"聚会分账的3个潜规则，", "99%的人都不知道（附…"});
            text(c, lines[0], 683, 4045, 44, INK, false, Paint.Align.LEFT);
            text(c, lines[1], 683, 4105, 44, INK, false, Paint.Align.LEFT);
        }
    }

    private static String[] splitLines(String value, String[] def) {
        String[] parts = value.split("\n", -1);
        String[] out = {parts.length > 0 ? parts[0] : def[0], parts.length > 1 ? parts[1] : def[1]};
        return out;
    }

    // ------------------------------------------------------------------ 热区
    private void hit(float l, float t, float rr, float b, String token) {
        hitRects.add(new float[]{l, t, rr, b});
        hitTokens.add(token);
    }

    /** 返回落在 (x, y) 上的动作 token；坐标是屏幕设计坐标。 */
    public String hitTest(State s, float x, float y) {
        float cy = y + s.scroll;
        for (int i = hitRects.size() - 1; i >= 0; i--) {
            float[] b = hitRects.get(i);
            if (x >= b[0] && x <= b[2] && cy >= b[1] && cy <= b[3]) return hitTokens.get(i);
        }
        return "noop";
    }

    // ------------------------------------------------------------------ 数值
    private double val(State s, String key, double def) {
        Double v = s.nums.get(key);
        return v == null ? def : v;
    }

    private String money(State s, String key, double def) {
        return AssetScreen.formatMoney(val(s, key, def));
    }

    // ------------------------------------------------------------------ 图元
    private void drawArt(Canvas c, String name, float x, float y) {
        Bitmap bmp = art.get(name);
        if (bmp == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        c.drawBitmap(bmp, x, y, paint);
    }

    /** 用一块纯色盖住位图里的旧内容（底色取自原图，肉眼看不出来）。 */
    private void patch(Canvas c, float l, float t, float rr, float b, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRect(l, t, rr, b, paint);
    }

    /** 用一条横向渐变盖住彩卡上的旧内容，和卡片本身的渐变融在一起。 */
    private void patchGrad(Canvas c, float l, float t, float rr, float b, int from, int to) {
        paint.setShader(new LinearGradient(l, 0, rr, 0, from, to, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        c.drawRect(l, t, rr, b, paint);
        paint.setShader(null);
    }

    private void text(Canvas c, String value, float x, float baseline, float size, int color,
                      boolean bold, Paint.Align align) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(align);
        c.drawText(value, x, baseline, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** 金额数字：粗体，和参考图里那串金额的大小一致。 */
    private void number(Canvas c, String value, float x, float baseline, float size, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText(value, x, baseline, paint);
    }
}
