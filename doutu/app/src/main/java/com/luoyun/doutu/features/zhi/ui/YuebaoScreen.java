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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 余额宝页（小支主页 - 我的 - 余额 - 余额宝）。
 *
 * <p>整页按照参考截图 {@code 40b3e5ff1ffa71623ac23d858a3b8892.jpg} 1:1 重建：画布就是参考图的
 * 1280x2816，正好一屏，不用滚动。参考图被切成「主卡那一段」「稳健理财那一段」「底部免责声明」
 * 三块模块，加上返回箭头、标题、七日年化胶囊、衣服图标、更多这几个顶栏图标，各自单独抠出来后按
 * 参考图里的坐标贴回画布；顶上那层纯橙底与下面的页面底色则按参考图采样后自己画。
 *
 * <p>默认状态什么都不叠加：所有文字（含金额、年化）都随模块位图一起贴出来，与参考图逐像素一致。
 * 只有用户点开某个数字/文字把它改成别的内容之后，页面才会用小色块盖掉原位置、再画上用户自己的值。
 */
public final class YuebaoScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;
    /** 整页内容正好一屏，不用滚动。 */
    public static final float CONTENT_H = 2816f;

    public static final class State {
        public float scroll;
        public final Map<String, Double> nums;
        public final Map<String, String> texts;

        public State(Map<String, Double> nums, Map<String, String> texts) {
            this.nums = nums;
            this.texts = texts;
        }
    }

    // ------------------------------------------------------------------ 调色板
    private static final int PAGE_BG = 0xfff5f5f5;
    private static final int ORANGE = 0xffed7449;
    private static final int PILL_BG = 0xffee8156;
    private static final int WARM = 0xfffcf2e9;
    private static final int INK = 0xff282828;
    private static final int RED = 0xffb04044;

    /** 顶栏橙色底的高度；下面是主卡那一段模块。 */
    private static final float TOP_ORANGE_H = 340f;

    /** 「转出」「转入」两个按钮的位置；两个按钮一样大。 */
    private static final RectF BTN_OUT = new RectF(113f, 1252f, 626f, 1411f);
    /**
     * 「转入」那张图是按 {@code (630,1244)-(1182,1422)} 抠的，图里按钮本体在 {@code x 6..540}。
     * 把它贴到「转出」位置时，要让本体正好落在 {@link #BTN_OUT} 上，所以外面这块要往左、往上各挪一点。
     */
    private static final RectF BTN_ART = new RectF(107f, 1244f, 638f, 1422f);

    private final LinkedHashMap<String, Bitmap> art = new LinkedHashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    private final List<float[]> hitRects = new ArrayList<>();
    private final List<String> hitTokens = new ArrayList<>();

    // ------------------------------------------------------------------ 资源
    public void load(Resources res) {
        art.clear();
        art.put("main", BitmapFactory.decodeResource(res, R.drawable.yb_main));
        art.put("licai", BitmapFactory.decodeResource(res, R.drawable.yb_licai));
        art.put("disc", BitmapFactory.decodeResource(res, R.drawable.yb_disc));
        art.put("back", BitmapFactory.decodeResource(res, R.drawable.yb_back));
        art.put("title", BitmapFactory.decodeResource(res, R.drawable.yb_title));
        art.put("pill", BitmapFactory.decodeResource(res, R.drawable.yb_pill));
        art.put("tshirt", BitmapFactory.decodeResource(res, R.drawable.yb_tshirt));
        art.put("dots", BitmapFactory.decodeResource(res, R.drawable.yb_dots));
        art.put("btnIn", BitmapFactory.decodeResource(res, R.drawable.yb_btn_in));
    }

    public float maxScroll(State s) {
        return Math.max(0f, CONTENT_H - H);
    }

    // ------------------------------------------------------------------ 入口
    public void draw(Canvas c, State s) {
        hitRects.clear();
        hitTokens.clear();

        c.drawColor(PAGE_BG);
        c.drawRect(0, 0, W, TOP_ORANGE_H, orangePaint());

        c.save();
        c.translate(0, -s.scroll);

        // ---- 模块：按参考图坐标逐个贴回 ----
        drawArt(c, "main", 0f, 336f);
        drawArt(c, "licai", 0f, 1878f);
        drawArt(c, "disc", 0f, 2500f);

        // ---- 顶栏图标 ----
        drawArt(c, "back", 36f, 176f);
        drawArt(c, "title", 532f, 168f);
        drawArt(c, "pill", 438f, 246f);
        drawArt(c, "tshirt", 1024f, 166f);
        drawArt(c, "dots", 1138f, 184f);

        registerHits();

        // ---- 被用户改过的数字 / 文字 ----
        drawEdits(c, s);
        // ---- 余额不为 0 时，「转出」也点亮成和「转入」一样 ----
        drawButtons(c, s);

        c.restore();
    }

    /**
     * 有余额（总金额不是 0）时，「转出」按钮直接借用「转入」那张图点亮成同一个橙底白字；
     * 余额是 0 时什么都不画，位图里那种浅底橙字的常态就露出来了。
     */
    private void drawButtons(Canvas c, State s) {
        if (val(s, "yb.total", 0) == 0) return;
        Bitmap in = art.get("btnIn");
        if (in != null) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            c.drawBitmap(in, null, BTN_ART, paint);
        }
        centerText(c, "转出", (BTN_OUT.left + BTN_OUT.right) / 2f, 1359f, 68f, Color.WHITE);
    }

    private void registerHits() {
        hit(26, 158, 124, 282, "back");
        hit(1010, 158, 1128, 258, "noop");
        hit(1130, 168, 1272, 248, "noop");

        // 顶部大卡
        hit(336, 452, 414, 518, "noop");
        hit(418, 452, 636, 518, "noop");
        hit(100, 546, 402, 670, "num:yb.total");
        // 可用 / 攒着 那一块
        hit(140, 786, 402, 858, "num:yb.avail");
        hit(665, 786, 928, 858, "num:yb.zan");
        hit(1074, 788, 1162, 866, "noop");
        // 收益三列
        hit(88, 998, 404, 1064, "noop");
        hit(88, 1088, 312, 1154, "text:yb.yes");
        hit(480, 1086, 626, 1150, "num:yb.cum");
        hit(858, 998, 1054, 1140, "noop");
        // 转出 / 转入 / 定时转入
        hit(96, 1232, 642, 1412, "noop");
        hit(650, 1232, 1182, 1412, "noop");
        hit(486, 1528, 792, 1622, "noop");
        // 账户服务
        hit(80, 1636, 300, 1692, "noop");
        hit(1072, 1636, 1204, 1692, "noop");
        hit(232, 1754, 744, 1818, "text:yb.gift");
        hit(1058, 1756, 1204, 1818, "noop");

        // 稳健理财
        hit(80, 1960, 300, 2022, "noop");
        hit(928, 1960, 1212, 2020, "noop");
        hit(80, 2100, 320, 2180, "noop");
        hit(1058, 2114, 1208, 2172, "num:yb.huo");
        hit(80, 2300, 340, 2378, "noop");
        hit(1058, 2318, 1210, 2376, "num:yb.ding");

        // 七日年化胶囊最后登记，压在标题下面那一块之上
        hit(466, 244, 800, 330, "num:yb.rate");
    }

    // ------------------------------------------------------------------ 可改字段
    private void drawEdits(Canvas c, State s) {
        if (s.nums.containsKey("yb.rate")) {
            // 七日年化：连右边那个「>」一起盖掉，再按文字实际宽度把「>」重新贴到末尾
            patch(c, 465, 248, 820, 302, PILL_BG);
            text(c, "七日年化", 488, 290, 40, Color.WHITE, false);
            float w = measure("七日年化", 40, false);
            String rate = pct4(s, "yb.rate", 0.8360);
            text(c, rate, 488 + w, 290, 40, Color.WHITE, false);
            chevron(c, 488 + w + measure(rate, 40, false) + 16, 270, 302, Color.WHITE, 5f);
        }
        if (s.nums.containsKey("yb.total")) {
            // 总金额：那个「>」也要跟着数字走，数字长了就整体往右挪
            patch(c, 104, 546, 420, 670, Color.WHITE);
            String total = money(s, "yb.total", 0);
            text(c, total, 118, 649, 118, INK, true);
            chevron(c, 118 + measure(total, 118, true) + 26, 586, 630, 0xffcccccb, 8f);
        }
        if (s.nums.containsKey("yb.avail")) {
            patch(c, 148, 786, 402, 858, WARM);
            text(c, "可用", 159, 844, 60, INK, true);
            text(c, money(s, "yb.avail", 0), 266, 844, 60, INK, true);
        }
        if (s.nums.containsKey("yb.zan")) {
            patch(c, 672, 786, 928, 858, WARM);
            text(c, "攒着", 683, 844, 60, INK, true);
            text(c, money(s, "yb.zan", 0), 795, 844, 60, INK, true);
        }
        if (s.nums.containsKey("yb.cum")) {
            patch(c, 490, 1086, 626, 1152, Color.WHITE);
            text(c, money(s, "yb.cum", 8.81), 502, 1139, 60, 0xff353535, true);
        }
        if (s.texts.containsKey("yb.yes")) {
            patch(c, 92, 1088, 312, 1154, Color.WHITE);
            text(c, s.texts.get("yb.yes"), 101, 1142, 56, RED, true);
        }
        if (s.texts.containsKey("yb.gift")) {
            patch(c, 232, 1754, 744, 1818, Color.WHITE);
            text(c, s.texts.get("yb.gift"), 243, 1804, 46, INK, false);
        }
        if (s.nums.containsKey("yb.huo")) {
            patch(c, 1058, 2114, 1208, 2172, Color.WHITE);
            text(c, pct2(s, "yb.huo", 2.12), 1077, 2159, 46, RED, true);
        }
        if (s.nums.containsKey("yb.ding")) {
            patch(c, 1058, 2318, 1210, 2376, Color.WHITE);
            text(c, pct2(s, "yb.ding", 3.29), 1077, 2363, 46, RED, true);
        }
    }

    // ------------------------------------------------------------------ 热区
    private void hit(float l, float t, float rr, float b, String token) {
        hitRects.add(new float[]{l, t, rr, b});
        hitTokens.add(token);
    }

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

    private String pct2(State s, String key, double def) {
        return String.format(Locale.US, "%.2f%%", val(s, key, def));
    }

    private String pct4(State s, String key, double def) {
        return String.format(Locale.US, "%.4f%%", val(s, key, def));
    }

    // ------------------------------------------------------------------ 图元
    private final Paint orangePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Paint orangePaint() {
        orangePaint.setColor(ORANGE);
        orangePaint.setStyle(Paint.Style.FILL);
        orangePaint.setShader(null);
        return orangePaint;
    }

    private void drawArt(Canvas c, String name, float x, float y) {
        Bitmap bmp = art.get(name);
        if (bmp == null) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setAlpha(255);
        c.drawBitmap(bmp, x, y, paint);
    }

    private void patch(Canvas c, float l, float t, float rr, float b, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRect(l, t, rr, b, paint);
    }

    /** 一个「>」形箭头，左端落在 {@code x}，纵向从 {@code yTop} 到 {@code yBottom}。 */
    private void chevron(Canvas c, float x, float yTop, float yBottom, int color, float stroke) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
        float mid = (yTop + yBottom) / 2f;
        float w = (yBottom - yTop) * 0.62f;
        path.reset();
        path.moveTo(x, yTop);
        path.lineTo(x + w, mid);
        path.lineTo(x, yBottom);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void text(Canvas c, String value, float x, float baseline, float size, int color,
                      boolean bold) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText(value, x, baseline, paint);
    }

    private float measure(String value, float size, boolean bold) {
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextSize(size);
        return paint.measureText(value);
    }

    private void centerText(Canvas c, String value, float cx, float baseline, float size, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(value, cx, baseline, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }
}
