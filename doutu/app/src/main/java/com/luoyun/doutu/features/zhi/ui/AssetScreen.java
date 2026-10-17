package com.luoyun.doutu.features.zhi.ui;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.luoyun.doutu.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 总资产页（小支主页 - 我的 - 总资产）。
 *
 * <p>整页按照参考截图 {@code 5e54e24dc62b3d2bbe410fe3238ba8fb.jpg} 1:1 重建：画布宽度取参考图的
 * 1250px，纵向内容 8120px，跟着手指上下滚动。所有文字、分隔线、卡片、渐变都在这里用 Canvas
 * 画出来，只有确实画不出来的插画（蚂蚁公仔、环形图、存钱罐、头像、雷达图等）才从参考图里
 * 抠出来后单独贴上去。
 *
 * <p>页面上出现的每一个数字都是一个独立的点击热区，点一下会回到 Activity 弹输入框，
 * 改完之后立刻重画，页面上的数字随之更新。
 */
public final class AssetScreen {
    /** 参考图宽度，作为设计坐标系宽度。 */
    public static final float W = 1250f;
    /** 可见窗口高度，保持与设备一致的比例（1250 x 2388/1080）。 */
    public static final float H = 2764f;
    /** 整页内容高度（到“资产账户信息”那一行为止）。 */
    public static final float CONTENT_H = 8120f;

    /** 可变状态由宿主 Activity 持有。 */
    public static final class State {
        public float scroll;
        /** 被点改过的数字，key 见 {@code AssetActivity.FIELDS}。 */
        public final Map<String, Double> edits = new LinkedHashMap<>();
        /** 被点改过的文字。 */
        public final Map<String, String> texts = new LinkedHashMap<>();
        /** 点眼睛后是否隐藏金额。 */
        public boolean hideAll;
    }

    // ------------------------------------------------------------------ 调色板
    private static final int INK = 0xff1a1a1a;
    private static final int INK2 = 0xff26282b;
    private static final int GRAY = 0xff9aa0a6;
    private static final int GRAY2 = 0xff8a9099;
    private static final int BLUE = 0xff1e7bff;
    private static final int BLUE_DEEP = 0xff1677ff;
    private static final int LINK_BLUE = 0xff1e7bff;
    private static final int GREEN = 0xff0e9f74;
    private static final int RED = 0xffe53935;
    private static final int DIVIDER = 0xffeeeeee;
    private static final int PAGE = 0xfff2f2f2;
    private static final int CARD = 0xffffffff;
    private static final int PILL_BG = 0xffeaf2fe;
    private static final int ON_BLUE_LABEL = 0xffcfe0ff;
    private static final int TEXT_ON_CARD2 = 0xff5a6470;
    private static final int NAVY = 0xff16325c;
    private static final int CLOUD_BLUE = 0xff1e6fe0;

    /**
     * 蓝色卡片上那三个数字的字号。参考图里 digit 的高度是 57（本页画布），自带的字面在此字号下
     * 正好也是 57，「0.00」的宽度是 139，与参考图一致。
     */
    private static final float HEAD_NUM = 80f;
    /** 卡片里那张白色面板上一格数字的字号，同样是照参考图的 digit 高度定出来的。 */
    private static final float PANEL_NUM = 53f;

    private final Map<String, Bitmap> art = new LinkedHashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    /**
     * 页面上的数字用的字面，取自 assets 里那份按参考图调好的实例（Archivo 半窄体 650/85）：
     * 参考图里的 0 是竖长、方点、半窄的粗数字，而设备上的 {@code sans-serif-condensed}
     * 在不少机器上会落到普通 Roboto，数字又宽又圆，跟参考图差得远。自带一份就与设备无关了。
     * 万一字体读不出来，退回原来的窄体，页面仍旧画得出来。
     */
    private Typeface numFace = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private final RectF r = new RectF();
    private final Path path = new Path();

    /** 每一帧重画时登记的可点击热区。 */
    private final List<float[]> hitRects = new ArrayList<>();
    private final List<String> hitTokens = new ArrayList<>();

    // ------------------------------------------------------------------ 资源
    public void load(Resources res, AssetManager assets) {
        art.clear();
        numFace = loadNumberFace(assets);
        art.put("ant", BitmapFactory.decodeResource(res, R.drawable.asset_ant));
        art.put("donut", BitmapFactory.decodeResource(res, R.drawable.asset_donut));
        art.put("piggy", BitmapFactory.decodeResource(res, R.drawable.asset_piggy));
        art.put("iconMoney", BitmapFactory.decodeResource(res, R.drawable.asset_icon_money));
        art.put("iconShield", BitmapFactory.decodeResource(res, R.drawable.asset_icon_shield));
        art.put("iconCard", BitmapFactory.decodeResource(res, R.drawable.asset_icon_card));
        art.put("cleanstar", BitmapFactory.decodeResource(res, R.drawable.asset_cleanstar));
        art.put("minidonut", BitmapFactory.decodeResource(res, R.drawable.asset_minidonut));
        art.put("triangle", BitmapFactory.decodeResource(res, R.drawable.asset_triangle));
        art.put("avatarsSmall", BitmapFactory.decodeResource(res, R.drawable.asset_avatars_small));
        art.put("avatarsLian", BitmapFactory.decodeResource(res, R.drawable.asset_avatars_lian));
        art.put("ledgerArt", BitmapFactory.decodeResource(res, R.drawable.asset_ledger_art));
        art.put("more", BitmapFactory.decodeResource(res, R.drawable.asset_more_services));
        art.put("licai", BitmapFactory.decodeResource(res, R.drawable.asset_licai));
        art.put("baozhang", BitmapFactory.decodeResource(res, R.drawable.asset_baozhang));
        art.put("xinyong", BitmapFactory.decodeResource(res, R.drawable.asset_xinyong));
    }

    /**
     * 页面数字那份自带字面。按参考图里的「0.00」调出来的：字形半窄、点画是方的，尺寸取到
     * digit 高度与参考图一致。读不出来就退回系统的窄体，页面照样画得出来。
     */
    private static Typeface loadNumberFace(AssetManager assets) {
        if (assets != null) {
            try {
                return Typeface.createFromAsset(assets, "fonts/zhi_asset_number.ttf");
            } catch (Exception ignored) {
                // 落到下面的系统窄体
            }
        }
        return Typeface.create("sans-serif-condensed", Typeface.BOLD);
    }

    public float maxScroll(State s) {
        return Math.max(0f, CONTENT_H - H);
    }

    // ------------------------------------------------------------------ 入口
    public void draw(Canvas c, State s) {
        hitRects.clear();
        hitTokens.clear();

        // 页面底色
        c.drawColor(PAGE);
        c.save();
        c.translate(0, -s.scroll);

        drawTopBackdrop(c);
        drawHeader(c);
        drawAnnouncement(c, s);
        drawBlueCard(c, s);
        drawHoldings(c);
        drawLicai(c, s);
        drawBaozhang(c, s);
        drawXinyong(c, s);
        drawMoreServices(c, s);
        drawFooter(c);

        c.restore();
    }

    private void drawTopBackdrop(Canvas c) {
        paint.setShader(new LinearGradient(0, 0, 0, 500,
                0xffdce9fb, 0xffedf2fa, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, W, 500, paint);
        paint.setShader(null);
    }

    // ------------------------------------------------------------------ 顶部栏
    private void drawHeader(Canvas c) {
        // 返回箭头
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(9f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(INK);
        path.reset();
        path.moveTo(80, 190);
        path.lineTo(46, 221);
        path.lineTo(80, 252);
        c.drawPath(path, paint);

        text(c, "总资产", 132, 238, 62, INK, true, Paint.Align.LEFT);
        text(c, "去理财", 1195, 238, 54, INK, false, Paint.Align.RIGHT);
        hit(30, 165, 1120, 280, "back");
        hit(1000, 165, 1240, 280, "noop");
    }

    private void drawAnnouncement(Canvas c, State s) {
        drawArt(c, "ant", 56, 310, 180, 432);

        card(c, 205, 308, 905, 428, 24f);
        round(c, 237, 345, 305, 400, 10f, 0xff2a7dff);
        centerText(c, "热", 271, 386, 34, Color.WHITE, false);

        String msg = s.texts.containsKey("announce")
                ? s.texts.get("announce") : "沪指突破3800！关注什么";
        text(c, msg, 316, 382, 46, INK, false, Paint.Align.LEFT);
        hit(205, 308, 905, 428, "text:announce");
    }

    // ------------------------------------------------------------------ 蓝色资产概览
    private void drawBlueCard(Canvas c, State s) {
        paint.setShader(new LinearGradient(27, 0, 1223, 0,
                0xff1777ff, 0xff3a8bff, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        r.set(27, 478, 1223, 1166);
        c.drawRoundRect(r, 26f, 26f, paint);
        paint.setShader(null);

        text(c, "资产概览", 68, 572, 46, Color.WHITE, true, Paint.Align.LEFT);
        round(c, 278, 530, 474, 596, 33f, 0x40ffffff);
        shieldGlyph(c, 300, 549, 332, 583, Color.WHITE);
        text(c, "保障中", 342, 578, 36, Color.WHITE, false, Paint.Align.LEFT);

        text(c, "我的资产（元）", 68, 696, 38, ON_BLUE_LABEL, false, Paint.Align.LEFT);
        chevron(c, 340, 672, 358, 698, ON_BLUE_LABEL, 4f);
        eyeGlyph(c, 368, 660, 432, 706);
        number(c, money(s, "a.my", 0), 68, 789, HEAD_NUM, Color.WHITE, Paint.Align.LEFT);
        hit(60, 660, 460, 810, "num:a.my");
        hit(362, 654, 440, 712, "eye");

        text(c, "昨日收益", 700, 696, 38, ON_BLUE_LABEL, false, Paint.Align.LEFT);
        chevron(c, 866, 672, 884, 698, ON_BLUE_LABEL, 4f);
        number(c, money(s, "a.profit", 0), 748, 789, HEAD_NUM, Color.WHITE, Paint.Align.LEFT);
        hit(690, 660, 930, 810, "num:a.profit");

        paint.setColor(0x59ffffff);
        c.drawRect(945, 662, 947, 812, paint);

        text(c, "家庭保障", 994, 696, 38, ON_BLUE_LABEL, false, Paint.Align.LEFT);
        chevron(c, 1158, 672, 1176, 698, ON_BLUE_LABEL, 4f);
        String family = plain(s, "a.family", 2);
        number(c, family, 1084, 789, HEAD_NUM, Color.WHITE, Paint.Align.LEFT);
        // “份”跟在自己的数字后面，数字变大变小时都紧贴着，参考图里也是这样一对
        text(c, "份", 1084 + measureNum(family, HEAD_NUM) + 8, 786, 38, Color.WHITE, false,
                Paint.Align.LEFT);
        hit(990, 660, 1204, 810, "num:a.family");

        // 内嵌白色面板
        card(c, 41, 854, 1209, 1152, 18f);
        paint.setColor(DIVIDER);
        c.drawRect(421, 884, 423, 1126, paint);
        c.drawRect(811, 884, 813, 1126, paint);

        // 三列
        String[] labels = {"活期资产", "稳健理财", "进阶理财"};
        String[] keys1 = {"a.huo1", "a.wen1", "a.jin"};
        String[] keys2 = {"a.huo2", "a.wen2", null};
        double[] def1 = {0, 0, 1};
        double[] def2 = {0, 0, 0};
        float[] xs = {96, 485, 859};
        for (int i = 0; i < 3; i++) {
            float x = xs[i];
            text(c, labels[i], x, 938, 36, GRAY, false, Paint.Align.LEFT);
            chevron(c, x + measure(labels[i], 36, false) + 8, 916, x + measure(labels[i], 36, false) + 26, 940,
                    GRAY, 4f);
            if (i == 2) {
                String v = "费率" + plain(s, "a.jin", 1) + "折起";
                text(c, v, x, 1012, 46, INK2, false, Paint.Align.LEFT);
            } else {
                number(c, money(s, keys1[i], def1[i]), x, 1012, PANEL_NUM, INK2, Paint.Align.LEFT);
                number(c, money(s, keys2[i], def2[i]), x, 1078, PANEL_NUM, 0xffa9afb6,
                        Paint.Align.LEFT);
            }
        }
        hit(90, 970, 400, 1100, "num:a.huo1");
        hit(479, 970, 790, 1100, "num:a.wen1");
        hit(853, 890, 1160, 1040, "num:a.jin");
    }

    // ------------------------------------------------------------------ 持仓分析
    private void drawHoldings(Canvas c) {
        card(c, 27, 1204, 1223, 1814, 26f);

        text(c, "持仓分析", 66, 1278, 46, BLUE, true, Paint.Align.LEFT);
        paint.setColor(BLUE);
        c.drawRect(66, 1300, 252, 1306, paint);
        text(c, "收益分析", 322, 1278, 44, 0xff333333, false, Paint.Align.LEFT);
        text(c, "投资机会", 575, 1278, 44, 0xff333333, false, Paint.Align.LEFT);
        text(c, "保障分析", 828, 1278, 44, 0xff333333, false, Paint.Align.LEFT);
        hit(300, 1230, 540, 1312, "noop");
        hit(553, 1230, 800, 1312, "noop");
        hit(806, 1230, 1040, 1312, "noop");

        round(c, 43, 1332, 1207, 1795, 20f, 0xfff0f6fe);

        text(c, "查看三笔钱配置优化方案", 78, 1448, 54, NAVY, true, Paint.Align.LEFT);
        text(c, "你的专属资产配置工具", 78, 1543, 38, TEXT_ON_CARD2, false, Paint.Align.LEFT);

        String[] tags = {"灵活取用", "未来保障", "投资增值"};
        float[] tx = {132, 346, 560};
        float[] cx = {78, 292, 506};
        for (int i = 0; i < 3; i++) {
            tick(c, cx[i], 1600, cx[i] + 38, 1638);
            text(c, tags[i], tx[i], 1630, 38, INK2, false, Paint.Align.LEFT);
        }

        drawArt(c, "donut", 890, 1400, 1185, 1660);

        text(c, "三笔钱｜帮你管理财富 科学规划未来", 80, 1750, 40, 0xff333333, false, Paint.Align.LEFT);
        pill(c, 965, 1710, 1170, 1790, BLUE_DEEP);
        centerText(c, "去看看", 1067, 1766, 40, Color.WHITE, false);
        hit(940, 1690, 1190, 1810, "noop");
    }

    // ------------------------------------------------------------------ 理财资产
    private void drawLicai(Canvas c, State s) {
        // 整张卡片直接取自参考图，圆角、分隔线、文字位置一比一
        drawArt(c, "licai", 0, 1856, 1250, 3038);

        // 余额
        patch(c, 60, 2140, 158, 2190, 0xffffffff);
        number(c, money(s, "b.yue", 0), 68, 2182, 46, INK2, Paint.Align.LEFT);
        hit(58, 2134, 320, 2198, "num:b.yue");

        // 基金：10 元试试
        patch(c, 660, 2352, 862, 2408, 0xffffffff);
        String fund = plain(s, "b.fund", 10);
        number(c, fund, 668, 2403, 46, INK2, Paint.Align.LEFT);
        text(c, "元试试", 668 + measureNum(fund, 46), 2403, 46, INK2, false, Paint.Align.LEFT);
        hit(660, 2346, 880, 2412, "num:b.fund");

        // 黄金：买入 0 费率
        patch(c, 62, 2568, 286, 2630, 0xffffffff);
        text(c, "买入", 68, 2621, 45, INK2, false, Paint.Align.LEFT);
        float inW = measure("买入", 45, false);
        String gold = plain(s, "b.gold", 0);
        number(c, gold, 68 + inW, 2621, 45, INK2, Paint.Align.LEFT);
        text(c, "费率", 68 + inW + measureNum(gold, 45), 2621, 45, INK2, false, Paint.Align.LEFT);
        hit(62, 2564, 300, 2634, "num:b.gold");

        // 累计收益
        patch(c, 326, 2790, 470, 2842, 0xfffafafa);
        number(c, money(s, "b.leiji", -24.72), 332, 2833, 46, GREEN, Paint.Align.LEFT);
        hit(322, 2786, 480, 2846, "num:b.leiji");

        // 收益率
        patch(c, 848, 2790, 1008, 2842, 0xfffafafa);
        number(c, pct(s, "b.shouyi", -0.39), 855, 2833, 46, GREEN, Paint.Align.LEFT);
        hit(844, 2786, 1018, 2846, "num:b.shouyi");

        hit(66, 2930, 470, 3010, "noop");
    }

    // ------------------------------------------------------------------ 保障资产
    private void drawBaozhang(Canvas c, State s) {
        drawArt(c, "baozhang", 0, 3076, 1250, 4022);

        // 免费保单份数
        patch(c, 62, 3244, 126, 3332, 0xffffffff);
        number(c, plain(s, "c.count", 2), 70, 3322, 90, INK2, Paint.Align.LEFT);
        hit(60, 3240, 300, 3336, "num:c.count");

        // 当前保额
        patch(c, 60, 3770, 222, 3832, 0xfffafafa);
        String cur = money(s, "c.cur", 1.00);
        number(c, cur, 70, 3822, 54, INK2, Paint.Align.LEFT);
        text(c, "元", 70 + measureNum(cur, 54), 3820, 42, INK2, false, Paint.Align.LEFT);
        hit(58, 3766, 268, 3836, "num:c.cur");

        // 可提保额
        patch(c, 396, 3770, 606, 3832, 0xfffafafa);
        String max = plain(s, "c.max", 30000);
        number(c, max, 402, 3822, 54, RED, Paint.Align.LEFT);
        text(c, "元", 402 + measureNum(max, 54), 3820, 42, RED, false, Paint.Align.LEFT);
        hit(394, 3766, 630, 3836, "num:c.max");

        hit(66, 3912, 300, 3990, "noop");
    }

    // ------------------------------------------------------------------ 信用资产
    private void drawXinyong(Canvas c, State s) {
        drawArt(c, "xinyong", 0, 4058, 1250, 5192);

        // 我的可用额度
        patch(c, 62, 4350, 412, 4440, 0xffffffff);
        number(c, money(s, "d.avail", 2710.98), 68, 4436, 100, INK, Paint.Align.LEFT);
        hit(58, 4344, 424, 4446, "num:d.avail");

        // 2000 免费额度起
        patch(c, 658, 4392, 1006, 4444, 0xffffffff);
        String free = plain(s, "d.free", 2000);
        number(c, free, 666, 4438, 48, GRAY2, Paint.Align.LEFT);
        text(c, "免费额度起", 666 + measureNum(free, 48), 4436, 48, GRAY2, false,
                Paint.Align.LEFT);
        hit(656, 4386, 1012, 4448, "num:d.free");

        // 花呗可用额度
        patch(c, 60, 4722, 244, 4774, 0xffffffff);
        number(c, money(s, "d.hbAvail", 2710.98), 68, 4768, 50, INK2, Paint.Align.LEFT);
        hit(58, 4716, 260, 4780, "num:d.hbAvail");

        // 花呗总计额度
        patch(c, 658, 4722, 848, 4774, 0xffffffff);
        number(c, money(s, "d.hbTotal", 3000.00), 665, 4768, 50, INK2, Paint.Align.LEFT);
        hit(656, 4716, 866, 4780, "num:d.hbTotal");

        hit(66, 5085, 300, 5160, "noop");
    }


    // ------------------------------------------------------------------ 更多服务
    private void drawMoreServices(Canvas c, State s) {
        // 「更多服务」整块直接取自参考图，卡片、插画、阴影一比一
        drawArt(c, "more", 0, 5198, 1250, 8105);

        // 覆盖掉图里的数字，再画成可点击修改的原生文字
        patch(c, 672, 5542, 1013, 5593, 0xffffffff);
        text(c, "超" + plain(s, "e.users", 6000) + "万用户已测", 678, 5588, 39,
                0xff545454, false, Paint.Align.LEFT);
        hit(668, 5538, 1012, 5598, "num:e.users");

        patch(c, 672, 7093, 1068, 7150, 0xffffffff);
        text(c, "超过" + oneDec(s, "g.people", 1.4) + "亿人正在体验", 678, 7145, 42,
                0xff222222, false, Paint.Align.LEFT);
        hit(668, 7088, 1065, 7154, "num:g.people");

        String[] keys = {"f.r1", "f.r2", "f.r3"};
        double[] defs = {34.06, 42.11, 46.26};
        float[] base = {7352, 7457, 7562};
        for (int i = 0; i < 3; i++) {
            patch(c, 410, base[i] - 38, 572, base[i] + 12, 0xfff6f6f6);
            number(c, pctSigned(s, keys[i], defs[i]), 416, base[i], 38, RED, Paint.Align.LEFT);
            hit(406, base[i] - 42, 578, base[i] + 18, "num:" + keys[i]);
        }

        // 卡片按钮：图片里已经画好，这里只登记点击热区
        hit(70, 6012, 320, 6090, "noop");
        hit(680, 6002, 885, 6080, "noop");
        hit(70, 6832, 320, 6910, "noop");
        hit(678, 6832, 930, 6910, "noop");
        hit(68, 7622, 320, 7704, "noop");
        hit(678, 7222, 930, 7310, "noop");
    }

    private void drawFooter(Canvas c) {
        // “资产账户信息”已经随“更多服务”那块一起从参考图取回来了，这里只留点击热区
        hit(500, 7890, 760, 7970, "noop");
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
    private static String group(String digits) {
        if (digits.length() <= 3) return digits;
        StringBuilder sb = new StringBuilder();
        int c = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sb.append(digits.charAt(i));
            if (++c % 3 == 0 && i > 0) sb.append(',');
        }
        return sb.reverse().toString();
    }

    static String formatMoney(double value) {
        boolean neg = value < 0;
        double v = Math.abs(value);
        long ip = (long) Math.floor(v + 1e-9);
        long frac = Math.round((v - ip) * 100);
        if (frac >= 100) {
            ip += 1;
            frac = 0;
        }
        String f = frac < 10 ? "0" + frac : String.valueOf(frac);
        return (neg ? "-" : "") + group(String.valueOf(ip)) + "." + f;
    }

    static String formatPlain(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        String t = String.valueOf(value);
        if (t.endsWith(".0")) t = t.substring(0, t.length() - 2);
        return t;
    }

    static String formatOneDec(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    static String formatPct(double value) {
        return String.format(java.util.Locale.US, "%.2f", value) + "%";
    }

    static String formatPctSigned(double value) {
        String v = String.format(java.util.Locale.US, "%.2f", value);
        return (value >= 0 ? "+" : "") + v + "%";
    }

    private double val(State s, String key, double def) {
        Double v = s.edits.get(key);
        return v == null ? def : v;
    }

    private String money(State s, String key, double def) {
        if (s.hideAll) return "****";
        return formatMoney(val(s, key, def));
    }

    private String plain(State s, String key, double def) {
        if (s.hideAll && def >= 100) return "****";
        return formatPlain(val(s, key, def));
    }

    private String oneDec(State s, String key, double def) {
        if (s.hideAll && def >= 100) return "****";
        return formatOneDec(val(s, key, def));
    }

    private String pct(State s, String key, double def) {
        return formatPct(val(s, key, def));
    }

    private String pctSigned(State s, String key, double def) {
        if (s.hideAll) return "****";
        return formatPctSigned(val(s, key, def));
    }

    // ------------------------------------------------------------------ 图元
    private void card(Canvas c, float l, float t, float rr, float b, float radius) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(CARD);
        r.set(l, t, rr, b);
        c.drawRoundRect(r, radius, radius, paint);
    }

    private void round(Canvas c, float l, float t, float rr, float b, float radius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        r.set(l, t, rr, b);
        c.drawRoundRect(r, radius, radius, paint);
    }

    private void pill(Canvas c, float l, float t, float rr, float b, int color) {
        round(c, l, t, rr, b, (b - t) / 2f, color);
    }

    private void hline(Canvas c, float l, float rr, float y) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(DIVIDER);
        c.drawRect(l, y, rr, y + 2, paint);
    }

    /** 用一块纯色盖住位图里的旧数字（底色取自原图，肉眼看不出来）。 */
    private void patch(Canvas c, float l, float t, float rr, float b, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRect(l, t, rr, b, paint);
    }

    private void drawArt(Canvas c, String name, float l, float t, float rr, float b) {
        Bitmap bitmap = art.get(name);
        if (bitmap == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        r.set(l, t, rr, b);
        c.drawBitmap(bitmap, null, r, paint);
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
    }

    private void centerText(Canvas c, String value, float cx, float baseline, float size, int color,
                            boolean bold) {
        text(c, value, cx, baseline, size, color, bold, Paint.Align.CENTER);
    }

    /** 金额数字：用自带的那份半窄粗数字，跟参考图里的形状和大小一致。 */
    private void number(Canvas c, String value, float x, float baseline, float size, int color,
                        Paint.Align align) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(numFace);
        paint.setTextAlign(align);
        c.drawText(value, x, baseline, paint);
        paint.setTypeface(Typeface.DEFAULT);
    }

    private float measure(String value, float size, boolean bold) {
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextSize(size);
        return paint.measureText(value);
    }

    /** 量一下窄体数字的宽度，方便把“元”这类字紧贴着数字排。 */
    private float measureNum(String value, float size) {
        paint.setTypeface(numFace);
        paint.setTextSize(size);
        float w = paint.measureText(value);
        paint.setTypeface(Typeface.DEFAULT);
        return w;
    }

    private void chevron(Canvas c, float x0, float y0, float x1, float y1, int color, float stroke) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
        float mx = x0;
        float my = (y0 + y1) / 2f;
        path.reset();
        path.moveTo(mx, y0);
        path.lineTo(x1, my);
        path.lineTo(mx, y1);
        c.drawPath(path, paint);
    }

    private void shieldGlyph(Canvas c, float l, float t, float rr, float b, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.6f);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color);
        float w = rr - l;
        float h = b - t;
        path.reset();
        path.moveTo(l + w / 2f, t);
        path.lineTo(rr, t + h * 0.20f);
        path.lineTo(rr, t + h * 0.52f);
        path.quadTo(rr, b, l + w / 2f, b);
        path.quadTo(l, b, l, t + h * 0.52f);
        path.lineTo(l, t + h * 0.20f);
        path.close();
        c.drawPath(path, paint);
        path.reset();
        path.moveTo(l + w * 0.33f, t + h * 0.48f);
        path.lineTo(l + w * 0.46f, t + h * 0.62f);
        path.lineTo(l + w * 0.70f, t + h * 0.34f);
        c.drawPath(path, paint);
    }

    /** 资产记账本卡片底部的柔和蓝色山水，用渐变疊出来，不用原图。 */
    private void drawLedgerArt(Canvas c) {
        c.save();
        path.reset();
        r.set(28, 6152, 610, 6978);
        path.addRoundRect(r, 24f, 24f, Path.Direction.CW);
        c.clipPath(path);

        paint.setShader(new LinearGradient(0, 6152, 0, 6978,
                0xffffffff, 0xffe6f1fd, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        c.drawRect(28, 6152, 610, 6978, paint);

        paint.setShader(new RadialGradient(590, 6960, 430,
                0x66a9d6fc, 0x00a9d6fc, Shader.TileMode.CLAMP));
        c.drawRect(28, 6152, 610, 6978, paint);

        paint.setShader(new RadialGradient(180, 6620, 330,
                0x3ac4e0fb, 0x00c4e0fb, Shader.TileMode.CLAMP));
        c.drawRect(28, 6152, 610, 6978, paint);

        paint.setShader(new RadialGradient(430, 6870, 300,
                0x4dbcdffb, 0x00bcdffb, Shader.TileMode.CLAMP));
        c.drawRect(28, 6152, 610, 6978, paint);

        // 一道斜斜的浅蓝山影，呼应参考图里那层波纹
        path.reset();
        path.moveTo(28, 6978);
        path.lineTo(28, 6790);
        path.quadTo(210, 6690, 400, 6580);
        path.quadTo(520, 6510, 610, 6460);
        path.lineTo(610, 6978);
        path.close();
        paint.setShader(new LinearGradient(28, 6820, 610, 6500,
                0x22ffffff, 0x55b6dcfb, Shader.TileMode.CLAMP));
        c.drawPath(path, paint);

        paint.setShader(null);
        c.restore();
    }

    private void eyeGlyph(Canvas c, float l, float t, float rr, float b) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(Color.WHITE);
        float cx = (l + rr) / 2f;
        float cy = (t + b) / 2f;
        float w = (rr - l) / 2f;
        float h = (b - t) / 2f;
        path.reset();
        path.moveTo(cx - w, cy);
        path.quadTo(cx, cy - h, cx + w, cy);
        path.quadTo(cx, cy + h, cx - w, cy);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, 8f, paint);
    }

    private void tick(Canvas c, float l, float t, float rr, float b) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xff4f8ff5);
        c.drawCircle((l + rr) / 2f, (t + b) / 2f, (rr - l) / 2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.4f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.WHITE);
        float cx = (l + rr) / 2f;
        float cy = (t + b) / 2f;
        path.reset();
        path.moveTo(cx - 7, cy);
        path.lineTo(cx - 2, cy + 6);
        path.lineTo(cx + 8, cy - 6);
        c.drawPath(path, paint);
    }

    /** 一块浅灰圆角小方块，衬在 ¥ / 趋势箭头这类小图标后面。 */
    private void iconTile(Canvas c, float l, float t, float rr, float b) {
        round(c, l, t, rr, b, 8f, 0xfff0f1f3);
    }

    /** 小方块里的蓝色折线箭头。 */
    private void trendGlyph(Canvas c, float l, float t, float rr, float b) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5.5f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(BLUE_DEEP);
        float w = rr - l;
        float h = b - t;
        path.reset();
        path.moveTo(l, b);
        path.lineTo(l + w * 0.30f, t + h * 0.40f);
        path.lineTo(l + w * 0.54f, t + h * 0.70f);
        path.lineTo(rr, t);
        c.drawPath(path, paint);
        path.reset();
        path.moveTo(rr - w * 0.26f, t);
        path.lineTo(rr, t);
        path.lineTo(rr, t + h * 0.28f);
        c.drawPath(path, paint);
    }

    private void infoGlyph(Canvas c, float l, float t, float rr, float b) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffc9ced4);
        c.drawCircle((l + rr) / 2f, (t + b) / 2f, (rr - l) / 2f, paint);
        text(c, "i", (l + rr) / 2f, b - 8, 34, Color.WHITE, true, Paint.Align.CENTER);
    }

    /** “1.00 元 → 30000 元”中间那个粉红色的上扬箭头。 */
    private void upArrow(Canvas c) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffff8589);
        path.reset();
        path.moveTo(333, 3794);   // 右上尖角
        path.lineTo(283, 3803);
        path.lineTo(295, 3818);
        path.quadTo(274, 3836, 262, 3848);   // 收成尖尾
        path.quadTo(289, 3838, 304, 3831);
        path.quadTo(315, 3843, 328, 3851);
        path.close();
        c.drawPath(path, paint);
    }

    /** 底部直角、下面两角带圆角的色块，用来给“本年理财成绩 / 门诊保额”铺底色。 */
    private void blockBottom(Canvas c, float l, float t, float rr, float b, float radius,
                             int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        path.reset();
        path.moveTo(l, t);
        path.lineTo(rr, t);
        path.lineTo(rr, b - radius);
        path.quadTo(rr, b, rr - radius, b);
        path.lineTo(l + radius, b);
        path.quadTo(l, b, l, b - radius);
        path.close();
        c.drawPath(path, paint);
    }

    private void radar(Canvas c, float cx, float cy, float radius) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(0xffcfe0f7);
        for (int ring = 1; ring <= 4; ring++) {
            float rr = radius * ring / 4f;
            path.reset();
            for (int i = 0; i < 5; i++) {
                double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
                float x = (float) (cx + rr * Math.cos(a));
                float y = (float) (cy + rr * Math.sin(a));
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            c.drawPath(path, paint);
        }
        // 数据多边形
        float[] data = {0.95f, 0.62f, 0.78f, 0.35f, 0.5f};
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xb35b9bf0);
        path.reset();
        for (int i = 0; i < 5; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
            float rr = radius * data[i];
            float x = (float) (cx + rr * Math.cos(a));
            float y = (float) (cy + rr * Math.sin(a));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        c.drawPath(path, paint);
        // 顶点
        paint.setColor(0xff2f6fe4);
        for (int i = 0; i < 5; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
            float rr = radius * data[i];
            c.drawCircle((float) (cx + rr * Math.cos(a)), (float) (cy + rr * Math.sin(a)), 7f, paint);
        }
    }
}
