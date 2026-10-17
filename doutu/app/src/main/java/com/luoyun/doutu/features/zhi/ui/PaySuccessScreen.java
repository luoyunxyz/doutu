package com.luoyun.doutu.features.zhi.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.luoyun.doutu.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 支付成功页（「支」宫格里的「支付成功」）。
 *
 * <p>整页照着参考截图 {@code 491bf4b671fffb991fdae8d67f59547e.jpg} 1:1 重建：参考图切成三条整宽
 * 模块 —— 上面蓝渐变那一段（勾 + 支付成功 + 回首页 + 金额 + 两行明细 + 对账按钮）、中间那张白卡
 * （支付有礼 + 五行）、下面那一段（完成按钮 + 渐变到底），按原坐标贴回 1280 宽的画布；参考图自带
 * 的状态栏（最上面一条）不切进来，那一小段用渐变顶端的颜色补齐，图标交给系统。
 *
 * <p>默认状态什么都不叠加：所有文字都随模块一起贴出来，与参考图逐像素一致。用户点开某一行改成
 * 别的内容之后，这一行才会先盖掉原位置（蓝底上的用同一条渐变盖、白卡上的用白色盖、红按钮上的
 * 用红色盖），再画上用户自己的值；改过的值存在 {@code PaySuccessStore} 里，按账号各存一份。
 */
public final class PaySuccessScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;

    /**
     * 三条模块的落点。上面那一条连着参考图自带的状态栏一起贴（那一段的字在切图时已经抹掉，
     * 整条走同一次色彩转换，顶上就不会有接缝），状态栏图标交给系统画。
     */
    private static final float TOP_Y = 0f;
    private static final float CARD_Y = 1013f;
    private static final float BOTTOM_Y = 2344f;

    // ------------------------------------------------------------------ 配色
    /** 兜底的底色；真正画出来的蓝底是那条模块，这里只在下不到图时露一眼。 */
    private static final int TOP_BLUE = 0xff3874f6;
    /** 左边那条 6px 宽的纯渐变（ps_back），蓝底上要盖字时拉它来铺。 */
    private static final float BACK_W = 6f;
    private static final float BACK_H = 2816f;
    /** 红按钮里那条 6px 宽的纯红（ps_red）。 */
    private static final float RED_W = 6f;
    private static final float RED_H = 78f;
    /** 对账按钮那块底比背景暗一点：铺完渐变再压一层很淡的黑。 */
    private static final int DIM_ALPHA = 9;

    private static final int CARD = 0xffffffff;
    private static final int INK = 0xff2b2b2b;
    private static final int CARD_TITLE = 0xff1a1a1a;
    private static final int GREY = 0xff9a9a9a;
    private static final int RED = 0xffed4e3b;
    private static final int RED_TEXT = 0xffe8503f;
    private static final int CHIP_BG = 0xfffef7f1;
    private static final int CHIP_LINE = 0xffebe2dc;
    private static final int CHIP_TEXT = 0xffc4683c;
    private static final int DONE_BG = 0xfff5f5f5;
    private static final int DONE_TEXT = 0xff1677ff;
    private static final int WHITE = 0xffffffff;

    /** 一行（或一条）里可以点开的字。 */
    public static final class Run {
        public final String key;
        public final String def;
        final float x;
        final float baseline;
        final float size;
        final int color;
        final Paint.Align align;
        final boolean bold;
        /** 这一格前面带浅底胶囊（支付有礼那几行的小标签）。 */
        boolean chip;
        /** 金额那一行用的小号「¥」的字号。 */
        float yuan;

        Run(String key, String def, float x, float baseline, float size, int color,
            Paint.Align align, boolean bold, boolean chip, float yuan) {
            this.key = key;
            this.def = def;
            this.x = x;
            this.baseline = baseline;
            this.size = size;
            this.color = color;
            this.align = align;
            this.bold = bold;
            this.chip = chip;
            this.yuan = yuan;
        }
    }

    /** 补丁底下垫什么：纯色、蓝渐变、还是蓝渐变压暗一点（对账按钮那块底）。 */
    private static final int PLAIN = 0, GRADIENT = 1, GRADIENT_DIM = 2, MONEY = 3, CHAIN = 4;

    public static final class Line {
        final String id;
        final float left, top, right, bottom;
        final int patch;
        int patchColor = CARD;
        /** 这一行的两段字是接着排的（红色数字 + 深色标题），不是各按各的 x。 */
        boolean chain;
        final List<Run> runs = new ArrayList<>();

        Line(String id, float left, float top, float right, float bottom, int patch) {
            this.id = id;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.patch = patch;
        }
    }

    public static final class State {
        public final Map<String, String> texts;

        public State(Map<String, String> texts) {
            this.texts = texts;
        }
    }

    public static final List<Line> LINES = new ArrayList<>();

    private static Line line(String id, float l, float t, float r, float b, int patch) {
        Line line = new Line(id, l, t, r, b, patch);
        LINES.add(line);
        return line;
    }

    private static Run run(Line line, String key, String def, float x, float baseline, float size,
                           int color, Paint.Align align, boolean bold) {
        Run r = new Run(key, def, x, baseline, size, color, align, bold, false, 0f);
        line.runs.add(r);
        return r;
    }

    private static final String[][] ROWS = {
            // 红色数字, 标题, 标签, 说明, 按钮
            {"14元", "闪购新人红包", "淘宝闪购", "下单0元起用", "免费领"},
            {"", "资金周转找网商贷", "网商贷", "最高可借1000000元", "去看看"},
            {"4元", "漫花厨房湿巾优惠券", "分期生活", "满4元可用", "免费领"},
            {"3元", "国网电费券", "生活缴费", "满50元立减", "免费领"},
            {"8.8折", "顺风车红包", "同程旅行", "满20元可用", "免费领"}};
    private static final float ROW_TOP = 1191f;
    private static final float ROW_STEP = 239f;

    static {
        Line title = line("title", 556f, 220f, 816f, 305f, GRADIENT);
        run(title, "ps.title", "支付成功", 574f, 295f, 57f, WHITE, Paint.Align.LEFT, false);
        Line home = line("home", 1058f, 188f, 1238f, 262f, GRADIENT);
        run(home, "ps.home", "回首页", 1072f, 251f, 54f, WHITE, Paint.Align.LEFT, false);
        Line amount = line("amount", 400f, 378f, 880f, 545f, MONEY);
        Run big = run(amount, "ps.amount", "0.00", 640f, 523f, 174f, WHITE,
                Paint.Align.CENTER, false);
        big.yuan = 119f;
        Line l1 = line("l1", 78f, 642f, 330f, 716f, GRADIENT);
        run(l1, "ps.l1", "你", 87f, 704f, 51f, WHITE, Paint.Align.LEFT, false);
        Line v1 = line("v1", 900f, 642f, 1215f, 716f, GRADIENT);
        run(v1, "ps.v1", "¥ 0.01", 1188f, 702f, 57f, WHITE, Paint.Align.RIGHT, false);
        Line l2 = line("l2", 78f, 737f, 330f, 811f, GRADIENT);
        run(l2, "ps.l2", "百次立减", 88f, 799f, 51f, WHITE, Paint.Align.LEFT, false);
        Line v2 = line("v2", 900f, 737f, 1215f, 811f, GRADIENT);
        run(v2, "ps.v2", "-¥ 0.01", 1188f, 797f, 57f, WHITE, Paint.Align.RIGHT, false);
        Line check = line("check", 498f, 876f, 860f, 948f, GRADIENT_DIM);
        run(check, "ps.check", "点击给商家对账", 505f, 935f, 49f, WHITE, Paint.Align.LEFT, false);

        Line card = line("card", 60f, 1038f, 400f, 1112f, PLAIN);
        run(card, "ps.card", "支付有礼", 68f, 1100f, 52f, CARD_TITLE, Paint.Align.LEFT, true);

        for (int i = 0; i < ROWS.length; i++) {
            float top = ROW_TOP + i * ROW_STEP;
            String[] row = ROWS[i];
            Line head = line("r" + i + ".head", 230f, top - 8f, 1000f, top + 56f, PLAIN);
            head.chain = true;
            if (!row[0].isEmpty()) {
                run(head, "r" + i + ".amount", row[0], 239f, top + 51f, 49f, RED_TEXT,
                        Paint.Align.LEFT, true);
            }
            run(head, "r" + i + ".title", row[1], row[0].isEmpty() ? 239f : 0f, top + 51f, 49f,
                    INK, Paint.Align.LEFT, true);

            Line tag = line("r" + i + ".tag", 232f, top + 64f, 1000f, top + 132f, PLAIN);
            Run chip = run(tag, "r" + i + ".tag", row[2], 255f, top + 119f, 41f, CHIP_TEXT,
                    Paint.Align.LEFT, false);
            chip.chip = true;
            run(tag, "r" + i + ".desc", row[3], 452f, top + 119f, 41f, GREY, Paint.Align.LEFT,
                    false);

            Line button = line("r" + i + ".button", 1010f, top + 24f, 1208f, top + 104f, PLAIN);
            button.patchColor = RED;
            run(button, "r" + i + ".button", row[4], 1109f, top + 83f, 40f, WHITE,
                    Paint.Align.CENTER, false);
        }

        Line done = line("done", 555f, 2578f, 725f, 2662f, PLAIN);
        done.patchColor = DONE_BG;
        run(done, "ps.done", "完成", 640f, 2647f, 60f, DONE_TEXT, Paint.Align.CENTER, false);
    }

    // ------------------------------------------------------------------ 资源

    private final LinkedHashMap<String, Bitmap> art = new LinkedHashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final android.graphics.Rect source = new android.graphics.Rect();
    private final RectF target = new RectF();
    /**
     * 数字用的是那套半窄字体，和「总资产」页是同一份（参考图上的金额、数字都是它）；
     * 汉字和「¥」仍然是系统字体 —— 这段字体里没有的字按字符分开排。
     */
    private Typeface numFace;

    public void load(Resources res) {
        art.clear();
        try {
            numFace = Typeface.createFromAsset(res.getAssets(), "fonts/zhi_asset_number.ttf");
        } catch (Exception e) {
            numFace = null;
        }
        art.put("top", BitmapFactory.decodeResource(res, R.drawable.ps_top));
        art.put("card", BitmapFactory.decodeResource(res, R.drawable.ps_card));
        art.put("bottom", BitmapFactory.decodeResource(res, R.drawable.ps_bottom));
        art.put("back", BitmapFactory.decodeResource(res, R.drawable.ps_back));
        art.put("red", BitmapFactory.decodeResource(res, R.drawable.ps_red));
    }

    public void draw(Canvas c, State s) {
        c.drawColor(TOP_BLUE);
        drawArt(c, "top", TOP_Y);
        drawArt(c, "card", CARD_Y);
        drawArt(c, "bottom", BOTTOM_Y);
        for (Line line : LINES) drawLine(c, s, line);
    }

    private void drawLine(Canvas c, State s, Line line) {
        boolean changed = false;
        for (Run r : line.runs) {
            if (s.texts.containsKey(r.key)) changed = true;
        }
        if (!changed) return;
        patch(c, line);
        float cursor = 0f;
        boolean first = true;
        for (Run r : line.runs) {
            String value = value(s, r);
            if (value.isEmpty()) continue;
            float x = r.x;
            if (line.chain && !first) x = cursor + 14f;
            if (line.chain) cursor = x + mixedWidth(value, r.size, r.bold);
            if (r.chip) drawChip(c, r, value, x);
            else if (r.yuan > 0f) drawMoney(c, r, value);
            else text(c, value, x, r.baseline, r.size, r.color, r.align, r.bold);
            first = false;
        }
    }

    /** 金额那一行：小一号的「¥」加大字号的数字，整体居中。 */
    private void drawMoney(Canvas c, Run r, String value) {
        float digits = mixedWidth(value, r.size, r.bold);
        float yuan = mixedWidth("¥", r.yuan, r.bold);
        float start = r.x - (yuan + 18f + digits) / 2f;
        text(c, "¥", start, r.baseline, r.yuan, r.color, Paint.Align.LEFT, r.bold);
        text(c, value, start + yuan + 18f, r.baseline, r.size, r.color, Paint.Align.LEFT,
                r.bold);
    }

    /** 浅底胶囊：宽度跟着文字走。 */
    private void drawChip(Canvas c, Run r, String value, float x) {
        float size = r.size;
        while (size > 26f && mixedWidth(value, size, r.bold) > 170f) size -= 1f;
        float wide = mixedWidth(value, size, r.bold);
        float left = x - 16f;
        float right = x + wide + 16f;
        float top = r.baseline - 43f;
        float bottom = r.baseline + 17f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(CHIP_BG);
        c.drawRoundRect(left, top, right, bottom, 8f, 8f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(CHIP_LINE);
        c.drawRoundRect(left, top, right, bottom, 8f, 8f, paint);
        paint.setStyle(Paint.Style.FILL);
        text(c, value, x, r.baseline, size, r.color, Paint.Align.LEFT, r.bold);
    }

    /**
     * 盖掉这一行原来的字。蓝底上的那几行（包括金额那一行）不是涂一块纯色，而是把左边那条
     * 纯渐变按同样的竖坐标拉过来铺上 —— 补丁和周围是同一张图上的像素，颜色一模一样。
     */
    private void patch(Canvas c, Line line) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        Bitmap back = art.get("back");
        if (line.patch == GRADIENT || line.patch == GRADIENT_DIM || line.patch == MONEY) {
            float top = Math.max(0f, Math.min(BACK_H, line.top));
            float bottom = Math.max(0f, Math.min(BACK_H, line.bottom));
            source.set(0, Math.round(top), Math.round(BACK_W), Math.round(bottom));
            target.set(line.left, line.top, line.right, line.bottom);
            paint.setColor(Color.WHITE);
            paint.setAlpha(255);
            c.drawBitmap(back, source, target, paint);
            if (line.patch == GRADIENT_DIM) {
                paint.setColor(Color.BLACK);
                paint.setAlpha(DIM_ALPHA);
                c.drawRect(line.left, line.top, line.right, line.bottom, paint);
                paint.setAlpha(255);
            }
            return;
        }
        if (line.patchColor == RED) {
            source.set(0, 0, Math.round(RED_W), Math.round(RED_H));
            target.set(line.left, line.top, line.right, line.bottom);
            paint.setColor(Color.WHITE);
            paint.setAlpha(255);
            c.drawBitmap(art.get("red"), source, target, paint);
            return;
        }
        paint.setColor(line.patchColor);
        c.drawRect(line.left, line.top, line.right, line.bottom, paint);
    }

    private void drawArt(Canvas c, String name, float y) {
        Bitmap bitmap = art.get(name);
        if (bitmap == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        c.drawBitmap(bitmap, 0f, y, paint);
    }

    /**
     * 一行字分两段排：数字（还有点、逗号、减号）用那套半窄数字体，别的字符（¥、汉字）用系统
     * 字体 —— 参考图就是这样，数字比汉字窄一号，直接整行用一种字体就对不上了。
     */
    private void text(Canvas c, String value, float x, float baseline, float size, int color,
                      Paint.Align align, boolean bold) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(Paint.Align.LEFT);
        float cursor = x;
        if (align == Paint.Align.CENTER) cursor = x - mixedWidth(value, size, bold) / 2f;
        else if (align == Paint.Align.RIGHT) cursor = x - mixedWidth(value, size, bold);
        int index = 0;
        while (index < value.length()) {
            boolean numeric = numeric(value.charAt(index));
            int start = index;
            while (index < value.length() && numeric(value.charAt(index)) == numeric) index++;
            String run = value.substring(start, index);
            paint.setTypeface(face(numeric, bold));
            c.drawText(run, cursor, baseline, paint);
            cursor += paint.measureText(run);
        }
    }

    private float mixedWidth(String value, float size, boolean bold) {
        paint.setTextSize(size);
        float total = 0f;
        int index = 0;
        while (index < value.length()) {
            boolean numeric = numeric(value.charAt(index));
            int start = index;
            while (index < value.length() && numeric(value.charAt(index)) == numeric) index++;
            paint.setTypeface(face(numeric, bold));
            total += paint.measureText(value.substring(start, index));
        }
        return total;
    }

    private Typeface face(boolean numeric, boolean bold) {
        if (numeric && numFace != null) return numFace;
        return bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
    }

    /** 数字那一段的字符：阿拉伯数字、点、逗号、正负号。 */
    private static boolean numeric(char c) {
        return Character.isDigit(c) || c == '.' || c == ',' || c == '-' || c == '+';
    }

    private static String value(State s, Run r) {
        String stored = s.texts.get(r.key);
        return stored == null ? r.def : stored;
    }

    // ------------------------------------------------------------------ 点哪儿

    public String hitTest(State s, float x, float y) {
        // 红按钮、对账按钮、完成按钮、每行右上角的「…」先按整块算
        if (y >= 2538f && y <= 2696f && x >= 275f && x <= 1005f) return "done";
        if (y >= 856f && y <= 965f && x >= 390f && x <= 890f) return "noop";
        for (int i = 0; i < ROWS.length; i++) {
            float top = ROW_TOP + i * ROW_STEP;
            if (y >= top + 20f && y <= top + 107f && x >= 1007f && x <= 1211f) {
                Run label = findByKey("r" + i + ".button");
                return "noop";
            }
            if (y >= top - 10f && y <= top + 60f && x >= 1140f && x <= 1220f) {
                return "noop";
            }
        }
        for (Line line : LINES) {
            for (Run r : line.runs) {
                if (r.key.equals("ps.home") && y >= 180f && y <= 270f && x >= 1040f) return "home";
                if (inside(r, s, x, y)) return "edit:" + r.key;
            }
        }
        return "noop";
    }

    private boolean inside(Run r, State s, float x, float y) {
        String value = value(s, r);
        float wide = Math.max(40f, mixedWidth(value, r.size, r.bold));
        float left;
        float right;
        if (r.align == Paint.Align.CENTER) {
            left = r.x - wide / 2f - 12f;
            right = r.x + wide / 2f + 12f;
        } else if (r.align == Paint.Align.RIGHT) {
            left = r.x - wide - 12f;
            right = r.x + 12f;
        } else {
            left = r.x - 12f;
            right = r.x + wide + 12f;
        }
        return x >= left && x <= right && y >= r.baseline - r.size - 8f
                && y <= r.baseline + 16f;
    }

    private static Run findByKey(String key) {
        for (Line line : LINES) {
            for (Run r : line.runs) if (r.key.equals(key)) return r;
        }
        return null;
    }

    public static Run field(String key) {
        return findByKey(key);
    }

    /** 点开某一行的标题。 */
    /**
     * 红按钮上那行字：轻点是演示动作，长按才是改字。返回那一行的键，没落上就是 null。
     */
    public static String buttonKeyAt(float x, float y) {
        for (int i = 0; i < ROWS.length; i++) {
            float top = ROW_TOP + i * ROW_STEP;
            if (y >= top + 20f && y <= top + 107f && x >= 1007f && x <= 1211f) {
                return "r" + i + ".button";
            }
        }
        return null;
    }

    public static String titleOf(String key) {
        switch (key) {
            case "ps.title":
                return "修改标题";
            case "ps.home":
                return "修改右上角文字";
            case "ps.amount":
                return "修改金额";
            case "ps.l1":
            case "ps.l2":
                return "修改这一行的名称";
            case "ps.v1":
            case "ps.v2":
                return "修改这一行的金额";
            case "ps.check":
                return "修改对账按钮文字";
            case "ps.card":
                return "修改卡片标题";
            case "ps.done":
                return "修改底部按钮文字";
            default:
                break;
        }
        if (key.endsWith(".amount")) return "修改优惠数字";
        if (key.endsWith(".title")) return "修改优惠名称";
        if (key.endsWith(".tag")) return "修改标签文字";
        if (key.endsWith(".desc")) return "修改说明文字";
        if (key.endsWith(".button")) return "修改按钮文字";
        return "修改内容";
    }

    /** 改的是不是那个大金额（那一格用数字键盘、并且会整理成两位小数）。 */
    public static boolean money(String key) {
        return key.equals("ps.amount");
    }
}
