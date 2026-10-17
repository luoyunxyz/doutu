package com.luoyun.doutu.features.zhi.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.Arrays;
import java.util.List;

/**
 * Offline, native rendition of the Alipay-style demo page for the Zhi service.
 * The downloaded hubhe project contains only a web-view route plus alipay.js mock data;
 * its page bundle and static assets are not present. Menu labels follow the local demo
 * reference and the apps_list data in hubhe/sumer/common/alipay.js.
 * All account/payment views are presentation-only and do not perform transactions.
 */
public final class AlipayHomeScreen {
    public static final float WIDTH = 1080f;
    public static final float HEIGHT = 2388f;

    private static final int INK = 0xff202124;
    private static final int MUTED = 0xff858589;
    private static final int BLUE = 0xff1677ff;
    private static final int[] SERVICE_COLORS = {
            0xff777777, 0xff5978eb, 0xff777777, 0xff777777
    };
    private static final String[] SERVICE_LABELS = {"微", "支", "抖", "Q"};
    private static final String[] HOME_ITEMS = {
            "小支主页", "总资产", "余额", "余额宝",
            "花呗", "支付消息", "支付成功", "自定义账单",
            "个人信息", "切换账号", "财富页", "收款记录",
            "余额提现", "转账银行卡", "小荷包", "芝麻信用"
    };
    private static final String[] ENTERTAINMENT_ITEMS = {
            "滴滴出行", "饿了么", "我的快递", "股票", "花呗", "运动",
            "信用卡还款", "生活缴费", "市民中心", "蚂蚁庄园", "手机充值",
            "芝麻信用", "蚂蚁森林", "淘票票"
    };
    private static final String[] PROFILE_ITEMS = {
            "个人资料", "银行卡", "账单设置", "隐私与安全", "帮助中心", "关于模拟页面"
    };
    private static final int[] APP_COLORS = {
            0xffff9c25, 0xff2e9bff, 0xffffb124, 0xfff15145, 0xff2b83fb,
            0xff20a9ef, 0xffff8a25, 0xff2482f7, 0xff27be65, 0xffff7e24,
            0xffffaa25, 0xff3277ed, 0xff20bd67, 0xffff5733
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    public void draw(Canvas canvas, int tab, float scroll, String detailTitle) {
        canvas.drawColor(0xfff7f7f7);
        if (detailTitle != null) {
            drawDetail(canvas, detailTitle);
            drawBottomNav(canvas, tab);
            return;
        }
        if (tab == 0) drawHome(canvas);
        else if (tab == 1) drawEntertainment(canvas, scroll);
        else drawProfile(canvas, scroll);
        drawBottomNav(canvas, tab);
    }

    /** Returns an action token or the title of the local detail screen at a tap location. */
    public String hitTest(int tab, String detailTitle, float x, float y, float scroll) {
        if (detailTitle != null) {
            if (y < 245f) return "@back";
            if (y >= 920f && y < 1065f) return "@demo-action";
            return null;
        }
        if (y >= 2212f) return "@tab:" + (x < 360f ? 0 : x < 720f ? 1 : 2);
        if (y >= 730f && y <= 910f) {
            int service = x < 280f ? 0 : x < 540f ? 1 : x < 795f ? 2 : 3;
            return "@service:" + service;
        }
        if (tab == 0) {
            if (y < 950f || y > 1850f || x < 50f || x > 1030f) return null;
            int column = Math.min(3, Math.max(0, (int) ((x - 52f) / 243f)));
            int row = Math.min(3, Math.max(0, (int) ((y - 950f) / 220f)));
            return HOME_ITEMS[row * 4 + column];
        }
        if (tab == 1) {
            float contentY = y + scroll;
            int index = (int) ((contentY - 922f) / 138f);
            if (index >= 0 && index < ENTERTAINMENT_ITEMS.length && contentY < 922f + (index + 1) * 138f)
                return ENTERTAINMENT_ITEMS[index];
            return null;
        }
        float contentY = y + scroll;
        if (contentY >= 916f) {
            int index = (int) ((contentY - 916f) / 156f);
            if (index >= 0 && index < PROFILE_ITEMS.length) return PROFILE_ITEMS[index];
        }
        if (y >= 250f && y < 620f) return "模拟账号信息";
        return null;
    }

    public float maxScroll(int tab) {
        if (tab == 1) return Math.max(0, 922f + ENTERTAINMENT_ITEMS.length * 138f - 2110f);
        if (tab == 2) return Math.max(0, 916f + PROFILE_ITEMS.length * 156f - 2110f);
        return 0;
    }

    private void drawHome(Canvas c) {
        drawBanner(c);
        drawServiceSelector(c, 0xfff7f7f7);
        round(c, 52, 952, 1028, 1838, 34, 0xffe7e7e7);
        for (int i = 0; i < HOME_ITEMS.length; i++) {
            int row = i / 4;
            int col = i % 4;
            float cx = 177 + col * 241f;
            float cy = 1040 + row * 216f;
            drawHomeIcon(c, i, cx, cy);
            centerText(c, HOME_ITEMS[i], cx, cy + 91, 35, Color.BLACK, false);
        }
        text(c, "演示软件，仅供娱乐体验，请勿用于真实交易", 104, 2181, 31, 0xffff5147, false);
    }

    private void drawBanner(Canvas c) {
        round(c, 52, 304, 1028, 702, 48, 0xff7161e7);
        round(c, 510, 304, 1028, 702, 48, 0xff806ff1);
        // decorative code-drawn stars and bubbles
        circle(c, 765, 451, 130, 0xff8bf078);
        circle(c, 944, 568, 95, 0xff9bee72);
        star(c, 673, 464, 17, Color.WHITE);
        star(c, 962, 458, 18, 0xffe7ff65);
        star(c, 921, 619, 13, Color.WHITE);
        drawMascot(c, 790, 430);
        text(c, "晚上好！一天将尽，放松", 108, 397, 42, Color.WHITE, false);
        text(c, "身心，别太辛苦~", 108, 455, 42, Color.WHITE, false);
        round(c, 106, 548, 974, 657, 58, Color.WHITE);
        strokeRound(c, 106, 548, 974, 657, 58, 0xff72a3ff, 6);
        centerText(c, "想改哪里点哪里，如果不行就长按 ☝", 540, 615, 36, 0xff666666, false);
        drawServiceSelector(c, Color.TRANSPARENT);
    }

    private void drawMascot(Canvas c, float x, float y) {
        // A simple vector mascot replaces the unavailable bitmap assets from the source folder.
        circle(c, x + 58, y + 117, 128, 0xff412274);
        oval(c, x - 8, y + 38, x + 184, y + 233, 0xffe7a9cf);
        path.reset();
        path.moveTo(x - 7, y + 105);
        path.cubicTo(x - 32, y + 18, x + 45, y - 58, x + 124, y - 13);
        path.cubicTo(x + 220, y + 12, x + 201, y + 137, x + 165, y + 177);
        path.cubicTo(x + 154, y + 75, x + 112, y + 43, x + 48, y + 68);
        path.close();
        fill(0xff762caa);
        c.drawPath(path, paint);
        stroke(0xff1d5477, 15);
        c.drawOval(x + 8, y + 51, x + 86, y + 107, paint);
        c.drawOval(x + 88, y + 51, x + 166, y + 107, paint);
        line(c, x + 86, y + 76, x + 89, y + 76, 0xff1d5477, 12);
        circle(c, x + 50, y + 78, 9, 0xff25202c);
        circle(c, x + 126, y + 78, 9, 0xff25202c);
        line(c, x + 89, y + 131, x + 108, y + 137, 0xffad566f, 5);
        round(c, x + 35, y - 2, x + 146, y + 24, 16, 0xff37a2b7);
        circle(c, x + 57, y + 11, 9, 0xff84ffdc);
        circle(c, x + 126, y + 11, 9, 0xff84ffdc);
        path.reset();
        path.moveTo(x + 17, y + 205);
        path.quadTo(x + 95, y + 163, x + 175, y + 211);
        path.lineTo(x + 216, y + 304);
        path.lineTo(x - 12, y + 304);
        path.close();
        fill(0xffd9c4ff);
        c.drawPath(path, paint);
        line(c, x + 91, y + 188, x + 111, y + 257, 0xff383044, 8);
        circle(c, x + 110, y + 258, 10, 0xffffdb52);
    }

    private void drawServiceSelector(Canvas c, int background) {
        if (background != Color.TRANSPARENT) fillRect(c, 0, 716, WIDTH, 925, background);
        float[] left = {52, 304, 556, 808};
        float[] right = {267, 520, 773, 1028};
        for (int i = 0; i < 4; i++) {
            int color = i == 1 ? 0xff5978eb : 0xffe6e6e6;
            round(c, left[i], 739, right[i], 891, 40, color);
            centerText(c, SERVICE_LABELS[i], (left[i] + right[i]) / 2f, 828, 80,
                    i == 1 ? Color.WHITE : SERVICE_COLORS[i], true);
        }
    }

    private void drawEntertainment(Canvas c, float scroll) {
        text(c, "娱乐", 52, 268, 54, INK, true);
        round(c, 52, 323, 1028, 403, 38, Color.WHITE);
        text(c, "⌕  搜索应用和服务", 91, 375, 33, 0xff99999d, false);
        drawServiceSelector(c, 0xfff7f7f7);
        c.save();
        c.clipRect(0, 910, 1080, 2140);
        float y = 922 - scroll;
        for (int i = 0; i < ENTERTAINMENT_ITEMS.length; i++) {
            fillRect(c, 52, y, 1028, y + 128, Color.WHITE);
            circle(c, 115, y + 64, 39, APP_COLORS[i]);
            centerText(c, iconLetter(ENTERTAINMENT_ITEMS[i]), 115, y + 76, 33, Color.WHITE, true);
            text(c, ENTERTAINMENT_ITEMS[i], 181, y + 76, 35, INK, false);
            chevron(c, 983, y + 64);
            line(c, 180, y + 127, 1028, y + 127, 0xffeeeeee, 2);
            y += 138;
        }
        c.restore();
        text(c, "本地模拟页面，不接入真实支付服务", 162, 2180, 30, 0xff99999d, false);
    }

    private void drawProfile(Canvas c, float scroll) {
        text(c, "我的", 52, 268, 54, INK, true);
        round(c, 52, 309, 1028, 673, 28, Color.WHITE);
        circle(c, 152, 407, 62, 0xff80a6f8);
        centerText(c, "支", 152, 426, 62, Color.WHITE, true);
        text(c, "演示账号", 245, 396, 43, INK, true);
        text(c, "账号信息仅保存在本地模拟页面", 245, 454, 28, MUTED, false);
        line(c, 77, 514, 1003, 514, 0xffeeeeee, 2);
        centerText(c, "模拟余额", 220, 580, 27, MUTED, false);
        centerText(c, "¥ 0.00", 470, 580, 34, INK, false);
        text(c, "查看模拟账户", 694, 582, 28, BLUE, false);
        drawServiceSelector(c, 0xfff7f7f7);
        c.save();
        c.clipRect(0, 910, 1080, 2140);
        float y = 916 - scroll;
        for (int i = 0; i < PROFILE_ITEMS.length; i++) {
            fillRect(c, 52, y, 1028, y + 146, Color.WHITE);
            circle(c, 116, y + 73, 28, profileIconColor(i));
            drawProfileIcon(c, i, 116, y + 73);
            text(c, PROFILE_ITEMS[i], 176, y + 84, 35, INK, false);
            chevron(c, 981, y + 73);
            line(c, 176, y + 145, 1028, y + 145, 0xffeeeeee, 2);
            y += 156;
        }
        c.restore();
        text(c, "模拟演示 · 无真实账户或支付能力", 223, 2180, 30, 0xff99999d, false);
    }

    private void drawDetail(Canvas c, String title) {
        round(c, 30, 78, 182, 180, 28, Color.WHITE);
        line(c, 117, 103, 91, 129, INK, 6);
        line(c, 91, 129, 117, 155, INK, 6);
        text(c, "返回", 137, 143, 30, INK, false);
        centerText(c, title, 540, 291, 52, INK, true);
        round(c, 42, 340, 1038, 1220, 30, Color.WHITE);
        circle(c, 540, 509, 88, 0xffe8f1ff);
        drawHomeIcon(c, detailIcon(title), 540, 509);
        centerText(c, title, 540, 675, 42, INK, true);
        centerText(c, "本页面为本地界面演示", 540, 752, 30, MUTED, false);
        centerText(c, "不连接支付宝，不产生真实资金或交易记录", 540, 802, 26, MUTED, false);
        round(c, 98, 920, 982, 1065, 72, 0xff1677ff);
        centerText(c, "查看演示内容", 540, 1009, 35, Color.WHITE, true);
        round(c, 42, 1260, 1038, 1470, 26, Color.WHITE);
        text(c, "模拟状态", 80, 1346, 32, INK, false);
        text(c, "仅供界面预览", 80, 1412, 28, MUTED, false);
        chevron(c, 979, 1362);
    }

    private int detailIcon(String title) {
        for (int i = 0; i < HOME_ITEMS.length; i++) if (HOME_ITEMS[i].equals(title)) return i;
        return 10;
    }

    private void drawBottomNav(Canvas c, int activeTab) {
        fillRect(c, 0, 2210, WIDTH, HEIGHT, Color.WHITE);
        line(c, 0, 2210, WIDTH, 2210, 0xffededed, 2);
        float[] centers = {180, 540, 900};
        String[] labels = {"首页", "娱乐", "我"};
        for (int i = 0; i < 3; i++) {
            if (i == activeTab) round(c, centers[i] - 95, 2226, centers[i] + 95, 2350, 62, 0xffe8e8e8);
            int color = i == activeTab ? 0xff222222 : 0xff99999d;
            if (i == 0) drawNavHome(c, centers[i], 2263, color);
            else if (i == 1) drawNavPlay(c, centers[i], 2263, color);
            else drawNavPerson(c, centers[i], 2263, color);
            centerText(c, labels[i], centers[i], 2328, 32, color, i == activeTab);
        }
    }

    private void drawHomeIcon(Canvas c, int index, float cx, float cy) {
        int color = 0xff17191c;
        stroke(color, 7);
        switch (index) {
            case 0: // house
                path.reset(); path.moveTo(cx - 28, cy + 1); path.lineTo(cx, cy - 28); path.lineTo(cx + 29, cy + 1);
                path.lineTo(cx + 23, cy + 1); path.lineTo(cx + 23, cy + 29); path.lineTo(cx - 22, cy + 29); path.lineTo(cx - 22, cy + 1); path.close(); c.drawPath(path, paint);
                c.drawArc(cx - 14, cy + 6, cx + 14, cy + 24, 5, 170, false, paint); break;
            case 1: roundStroke(c, cx - 28, cy - 23, cx + 28, cy + 24, 5); roundStroke(c, cx - 14, cy - 8, cx + 29, cy + 12, 5); circleStroke(c, cx + 8, cy + 2, 3); break;
            case 2: circleStroke(c, cx, cy, 28); path.reset(); path.moveTo(cx, cy - 14); path.lineTo(cx + 14, cy); path.lineTo(cx, cy + 14); path.lineTo(cx - 14, cy); path.close(); c.drawPath(path, paint); break;
            case 3: path.reset(); path.moveTo(cx, cy - 29); path.lineTo(cx + 25, cy - 15); path.lineTo(cx + 20, cy + 13); path.lineTo(cx, cy + 29); path.lineTo(cx - 20, cy + 13); path.lineTo(cx - 25, cy - 15); path.close(); c.drawPath(path, paint); circleStroke(c, cx, cy, 8); break;
            case 4: c.drawArc(cx - 25, cy - 25, cx + 25, cy + 25, 205, 125, false, paint); c.drawArc(cx - 25, cy - 25, cx + 25, cy + 25, 25, 125, false, paint); line(c, cx, cy + 28, cx, cy - 1, color, 7); line(c, cx, cy + 5, cx - 20, cy - 13, color, 7); line(c, cx, cy + 2, cx + 18, cy - 17, color, 7); break;
            case 5: roundStroke(c, cx - 27, cy - 23, cx + 26, cy + 19, 18); path.reset(); path.moveTo(cx - 15, cy - 1); path.lineTo(cx - 6, cy - 9); path.lineTo(cx + 1, cy + 1); path.lineTo(cx + 12, cy - 9); c.drawPath(path, paint); line(c, cx - 13, cy + 20, cx - 21, cy + 30, color, 7); break;
            case 6: circleStroke(c, cx, cy, 24); centerText(c, "¥", cx, cy + 11, 30, color, true); arrowHead(c, cx - 24, cy - 22, -1); arrowHead(c, cx + 24, cy + 22, 1); break;
            case 7: roundStroke(c, cx - 28, cy - 25, cx + 28, cy + 24, 3); line(c, cx - 15, cy + 12, cx - 15, cy - 4, color, 8); line(c, cx, cy + 12, cx, cy - 13, color, 8); line(c, cx + 15, cy + 12, cx + 15, cy - 1, color, 8); break;
            case 8: roundStroke(c, cx - 29, cy - 21, cx + 29, cy + 21, 4); circle(c, cx - 14, cy - 5, 7, color); line(c, cx - 23, cy + 12, cx - 5, cy + 12, color, 5); line(c, cx + 2, cy - 10, cx + 20, cy - 10, color, 4); line(c, cx + 2, cy, cx + 20, cy, color, 4); line(c, cx + 2, cy + 10, cx + 15, cy + 10, color, 4); break;
            case 9: circleStroke(c, cx - 1, cy - 15, 11); path.reset(); path.moveTo(cx - 19, cy + 25); path.cubicTo(cx - 16, cy + 2, cx + 12, cy + 2, cx + 17, cy + 22); c.drawPath(path, paint); line(c, cx + 11, cy + 13, cx + 31, cy + 13, color, 6); line(c, cx + 31, cy + 13, cx + 21, cy + 4, color, 6); line(c, cx + 31, cy + 13, cx + 21, cy + 22, color, 6); break;
            case 10: circleStroke(c, cx, cy, 26); centerText(c, "¥", cx, cy + 12, 34, color, true); break;
            case 11: path.reset(); path.moveTo(cx - 28, cy - 17); path.lineTo(cx - 13, cy - 29); path.lineTo(cx, cy - 19); path.lineTo(cx + 14, cy - 29); path.lineTo(cx + 28, cy - 18); path.lineTo(cx + 28, cy + 20); path.lineTo(cx - 28, cy + 20); path.close(); c.drawPath(path, paint); line(c, cx - 23, cy + 10, cx + 24, cy + 10, color, 6); break;
            case 12: circleStroke(c, cx, cy, 25); line(c, cx - 17, cy + 12, cx + 15, cy - 10, color, 7); line(c, cx + 15, cy - 10, cx + 12, cy + 1, color, 6); line(c, cx + 15, cy - 10, cx + 4, cy - 8, color, 6); break;
            case 13: roundStroke(c, cx - 29, cy - 20, cx + 29, cy + 20, 4); line(c, cx - 27, cy - 5, cx + 27, cy - 5, color, 6); line(c, cx - 16, cy + 8, cx + 4, cy + 8, color, 5); break;
            case 14: path.reset(); path.moveTo(cx, cy + 24); path.cubicTo(cx - 11, cy + 12, cx - 29, cy + 1, cx - 23, cy - 14); path.cubicTo(cx - 18, cy - 27, cx - 3, cy - 25, cx, cy - 12); path.cubicTo(cx + 8, cy - 28, cx + 25, cy - 24, cx + 25, cy - 8); path.cubicTo(cx + 25, cy + 5, cx + 9, cy + 17, cx, cy + 24); c.drawPath(path, paint); circleStroke(c, cx + 20, cy + 17, 9); break;
            default: circleStroke(c, cx, cy, 22); line(c, cx, cy - 13, cx, cy + 3, color, 6); circle(c, cx, cy + 14, 4, color); break;
        }
    }

    private void drawProfileIcon(Canvas c, int index, float cx, float cy) {
        int color = profileIconColor(index);
        stroke(Color.WHITE, 5);
        if (index == 0) { circle(c, cx, cy - 7, 8, Color.WHITE); round(c, cx - 13, cy + 3, cx + 13, cy + 16, 8, Color.WHITE); }
        else if (index == 1) { round(c, cx - 18, cy - 12, cx + 18, cy + 12, 4, Color.WHITE); line(c, cx - 17, cy - 3, cx + 17, cy - 3, color, 4); }
        else if (index == 2) { line(c, cx - 13, cy - 13, cx + 13, cy - 13, Color.WHITE, 4); line(c, cx - 13, cy, cx + 13, cy, Color.WHITE, 4); line(c, cx - 13, cy + 13, cx + 8, cy + 13, Color.WHITE, 4); }
        else if (index == 3) { path.reset(); path.moveTo(cx, cy - 17); path.lineTo(cx + 15, cy - 10); path.lineTo(cx + 12, cy + 8); path.lineTo(cx, cy + 18); path.lineTo(cx - 12, cy + 8); path.lineTo(cx - 15, cy - 10); path.close(); c.drawPath(path, paint); }
        else { circle(c, cx, cy, 13, Color.WHITE); centerText(c, "?", cx, cy + 8, 21, color, true); }
        paint.setStyle(Paint.Style.FILL);
    }

    private int profileIconColor(int i) {
        int[] colors = {0xff528cff, 0xffffad32, 0xff36b879, 0xff8d6ce0, 0xff19a8a8, 0xff87909c};
        return colors[Math.min(i, colors.length - 1)];
    }

    private void drawNavHome(Canvas c, float cx, float cy, int color) {
        stroke(color, 5); path.reset(); path.moveTo(cx - 17, cy); path.lineTo(cx, cy - 17); path.lineTo(cx + 17, cy); path.lineTo(cx + 13, cy); path.lineTo(cx + 13, cy + 16); path.lineTo(cx - 13, cy + 16); path.lineTo(cx - 13, cy); path.close(); c.drawPath(path, paint); paint.setStyle(Paint.Style.FILL);
    }
    private void drawNavPlay(Canvas c, float cx, float cy, int color) {
        stroke(color, 5); c.drawCircle(cx, cy, 19, paint); path.reset(); path.moveTo(cx - 5, cy - 10); path.lineTo(cx + 11, cy); path.lineTo(cx - 5, cy + 10); path.close(); fill(color); c.drawPath(path, paint); paint.setStyle(Paint.Style.FILL);
    }
    private void drawNavPerson(Canvas c, float cx, float cy, int color) {
        stroke(color, 5); c.drawCircle(cx, cy - 8, 8, paint); c.drawArc(cx - 18, cy + 2, cx + 18, cy + 27, 180, 180, false, paint); paint.setStyle(Paint.Style.FILL);
    }

    private String iconLetter(String title) {
        if (title.length() >= 2) return title.substring(0, 1);
        return title;
    }
    private void arrowHead(Canvas c, float x, float y, int dir) {
        line(c, x - 6, y, x, y + dir * 8, 0xff17191c, 6);
        line(c, x, y + dir * 8, x + 7, y, 0xff17191c, 6);
    }
    private void chevron(Canvas c, float x, float y) {
        line(c, x - 5, y - 10, x + 5, y, 0xffaaaaaa, 4);
        line(c, x + 5, y, x - 5, y + 10, 0xffaaaaaa, 4);
    }
    private void star(Canvas c, float cx, float cy, float r, int color) {
        path.reset();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = i % 2 == 0 ? r : r * .42f;
            float x = cx + (float) Math.cos(a) * rr;
            float y = cy + (float) Math.sin(a) * rr;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close(); fill(color); c.drawPath(path, paint);
    }
    private void centerText(Canvas c, String value, float cx, float baseline, float size, int color, boolean bold) {
        paint.setStyle(Paint.Style.FILL); paint.setShader(null); paint.setColor(color); paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        c.drawText(value, cx - paint.measureText(value) / 2f, baseline, paint);
    }
    private void text(Canvas c, String value, float x, float baseline, float size, int color, boolean bold) {
        paint.setStyle(Paint.Style.FILL); paint.setShader(null); paint.setColor(color); paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT); c.drawText(value, x, baseline, paint);
    }
    private void round(Canvas c, float l, float t, float r, float b, float radius, int color) {
        paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(color); c.drawRoundRect(l, t, r, b, radius, radius, paint);
    }
    private void strokeRound(Canvas c, float l, float t, float r, float b, float radius, int color, float width) {
        paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(width); paint.setColor(color); c.drawRoundRect(l, t, r, b, radius, radius, paint); paint.setStyle(Paint.Style.FILL);
    }
    private void roundStroke(Canvas c, float l, float t, float r, float b, float radius) {
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(6); c.drawRoundRect(l, t, r, b, radius, radius, paint); paint.setStyle(Paint.Style.FILL);
    }
    private void oval(Canvas c, float l, float t, float r, float b, int color) {
        fill(color); c.drawOval(l, t, r, b, paint);
    }
    private void circle(Canvas c, float x, float y, float radius, int color) {
        fill(color); c.drawCircle(x, y, radius, paint);
    }
    private void circleStroke(Canvas c, float x, float y, float radius) {
        paint.setStyle(Paint.Style.STROKE); c.drawCircle(x, y, radius, paint); paint.setStyle(Paint.Style.FILL);
    }
    private void fillRect(Canvas c, float l, float t, float r, float b, int color) {
        fill(color); c.drawRect(l, t, r, b, paint);
    }
    private void fill(int color) { paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(color); }
    private void stroke(int color, float width) { paint.setShader(null); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(width); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); paint.setColor(color); }
    private void line(Canvas c, float x1, float y1, float x2, float y2, int color, float width) {
        stroke(color, width); c.drawLine(x1, y1, x2, y2, paint); paint.setStyle(Paint.Style.FILL);
    }
}