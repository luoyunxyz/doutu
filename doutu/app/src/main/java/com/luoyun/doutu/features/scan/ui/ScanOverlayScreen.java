package com.luoyun.doutu.features.scan.ui;

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

/**
 * Overlay drawn on top of the live camera preview of the 扫一扫 page.
 *
 * <p>The camera image is the background, so this layer only paints the chrome of the reference
 * screenshot: the back arrow, the “…” menu, the hint line, the two round shortcut buttons, the
 * black tab bar and the looping blue scan line.
 */
public final class ScanOverlayScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;
    /** Top of the black bottom bar. */
    public static final float BAR_TOP = 2510f;

    private static final float SCAN_TOP = 250f;
    private static final float SCAN_BOTTOM = 2010f;
    /** Height of the blue trail that streams above the leading edge. */
    private static final float GLOW_H = 114f;
    /** The bright line at the leading edge. */
    private static final float CORE_H = 5f;
    private static final long PERIOD_MS = 2600L;

    private static final int SCAN_BLUE = 0xff3c78f7;
    private static final int CORE_RGB = 0x00e6efff;
    private static final int TAB_IDLE = 0xff8c8c8c;
    private static final int BUTTON_SCRIM = 0x5c000000;

    /** Mutable presentation state owned by the hosting activity. */
    public static final class State {
        /** 0 = 扫码, 1 = 灵光. */
        public int tab;
        /** Wall clock used to place the scan line; 0 means “start now”. */
        public long clock;
    }

    private static final float[] TAB_CX = {422f, 853f};
    private static final String[] TAB_LABELS = {"扫码", "灵光"};
    private static final float BUTTON_R = 84f;
    private static final float[] BUTTON_CX = {165f, 1108f};
    private static final float BUTTON_CY = 2283f;
    private static final RectF PAY_RECT = new RectF(95, 2200, 235, 2360);
    private static final RectF ALBUM_RECT = new RectF(1045, 2200, 1175, 2360);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private Bitmap pay, album, glow;

    /** Decodes the two button cut-outs. Safe to call again after a configuration change. */
    public void load(Resources res) {
        pay = BitmapFactory.decodeResource(res, R.drawable.scan_pay);
        album = BitmapFactory.decodeResource(res, R.drawable.scan_album);
    }

    // ------------------------------------------------------------------ drawing

    public void draw(Canvas c, State s) {
        drawScanLine(c, s);
        drawTopBar(c);
        drawHint(c);
        drawButtons(c);
        drawBottomBar(c, s);
    }

    /**
     * The blue scan line: a trail that sweeps down the preview over and over, easing in and out
     * at both ends so the loop does not look mechanical.
     */
    private void drawScanLine(Canvas c, State s) {
        ensureGlow();
        long clock = s.clock;
        float t = (clock % PERIOD_MS) / (float) PERIOD_MS;
        float eased = t * t * (3f - 2f * t);
        float y = SCAN_TOP + (SCAN_BOTTOM - SCAN_TOP) * eased;
        RectF dst = new RectF(0f, y - GLOW_H - CORE_H, W, y);
        c.drawBitmap(glow, null, dst, paint);
    }

    /**
     * The scan line is a blue trail that fades away from the leading edge, with a bright line at
     * the edge itself. Both fade out towards the sides, mirroring the reference sweep.
     */
    private void ensureGlow() {
        if (glow != null) return;
        int w = 256;
        int trail = 96;
        int core = 6;
        int h = trail + core;
        int[] pixels = new int[w * h];
        int trailRgb = SCAN_BLUE & 0x00ffffff;
        int coreRgb = CORE_RGB & 0x00ffffff;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float d = Math.abs((x + 0.5f) / w - 0.5f) * 2f;
                // Hann window: full in the middle, fading to nothing past ~0.85 of the width.
                float horiz = 0.5f * (1f + (float) Math.cos(Math.PI * Math.min(1f, d)));
                int a;
                int rgb;
                if (y < trail) {
                    // 0 at the tail, 1 where the trail meets the bright edge.
                    float v = (y + 1f) / trail;
                    a = (int) (158f * v * horiz);
                    rgb = trailRgb;
                } else {
                    // Bright edge: a short vertical triangle peaking in its middle.
                    float v = 1f - Math.abs((y - trail + 0.5f) / core - 0.5f) * 2f;
                    a = (int) (62f * v * horiz);
                    rgb = coreRgb;
                }
                pixels[y * w + x] = (a << 24) | rgb;
            }
        }
        glow = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        glow.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    private void drawTopBar(Canvas c) {
        stroke(Color.WHITE, 7);
        c.drawLine(78, 188, 44, 219, paint);
        c.drawLine(44, 219, 78, 250, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 3; i++) {
            c.drawCircle(1203 + i * 16, 217, 6.5f, paint);
        }
    }

    private void drawHint(Canvas c) {
        centerText(c, "扫二维码/条码/小程序码", 640, 2069, 49, Color.WHITE, false);
    }

    private void drawButtons(Canvas c) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(BUTTON_SCRIM);
        c.drawCircle(BUTTON_CX[0], BUTTON_CY, BUTTON_R, paint);
        c.drawCircle(BUTTON_CX[1], BUTTON_CY, BUTTON_R, paint);
        drawArt(c, pay, PAY_RECT);
        drawArt(c, album, ALBUM_RECT);
    }

    private void drawBottomBar(Canvas c, State s) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        c.drawRect(0, BAR_TOP, W, H, paint);
        for (int i = 0; i < TAB_LABELS.length; i++) {
            boolean active = i == s.tab;
            centerText(c, TAB_LABELS[i], TAB_CX[i], 2640, 52,
                    active ? SCAN_BLUE : TAB_IDLE, false);
            if (active) {
                path.reset();
                path.addRoundRect(new RectF(TAB_CX[i] - 39, 2674, TAB_CX[i] + 39, 2685), 6, 6,
                        Path.Direction.CW);
                paint.setColor(SCAN_BLUE);
                c.drawPath(path, paint);
            }
        }
    }

    // ------------------------------------------------------------------ hit test

    /** Returns what the tap at design-space (x, y) selects. */
    public String hitTest(State s, float x, float y) {
        if (y < 260 && x < 140) return "back";
        if (y >= BUTTON_CY - BUTTON_R && y <= BUTTON_CY + BUTTON_R) {
            if (Math.abs(x - BUTTON_CX[0]) <= BUTTON_R) return "pay";
            if (Math.abs(x - BUTTON_CX[1]) <= BUTTON_R) return "album";
        }
        if (y >= 2560 && y <= 2700) {
            for (int i = 0; i < TAB_CX.length; i++) {
                if (Math.abs(x - TAB_CX[i]) <= 120) return "tab:" + i;
            }
        }
        return "noop";
    }

    // ------------------------------------------------------------------ primitives

    private void drawArt(Canvas c, Bitmap bitmap, RectF dst) {
        if (bitmap == null) return;
        c.drawBitmap(bitmap, null, dst, paint);
    }

    private void centerText(Canvas c, String value, float cx, float baseline, float size, int color,
                            boolean bold) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(value, cx, baseline, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void stroke(int color, float width) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color);
    }
}
