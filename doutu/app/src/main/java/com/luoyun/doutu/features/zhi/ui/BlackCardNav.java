package com.luoyun.doutu.features.zhi.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.luoyun.doutu.R;
import com.luoyun.doutu.features.zhi.data.LicaiVersion;

/**
 * The bottom bar the 财富黑卡 skin wears, shared by every tab of the 支 module so the five entries
 * never change when the user switches pages.
 *
 * <p>The bar is the 黑卡 reference's own five glyphs — 首页, 理财, 视频, 消息 and 我的 — drawn at the
 * module's own bar height, with only the entry the page shows restyled. Its top edge fades into
 * whatever the page has drawn above it instead of cutting across it: the bar is a fixed band over a
 * page that may sit at any scroll offset, so a flat edge read as a seam — a 断层 — wherever a card
 * or a plate happened to end under it.
 */
final class BlackCardNav {
    private static final int[] NORMAL = {
            R.drawable.hk_nav_index, R.drawable.hk_nav_fortune, R.drawable.hk_nav_video,
            R.drawable.hk_nav_message, R.drawable.hk_nav_me};
    private static final int[] ACTIVE = {
            R.drawable.hk_nav_index_active, R.drawable.hk_nav_fortune_active,
            R.drawable.hk_nav_video_active, R.drawable.hk_nav_message_active,
            R.drawable.hk_nav_me_active};
    private static final String[] LABELS = {"首页", "理财", "视频", "消息", "我的"};

    /** The bar's own fill, and how far its top edge takes to reach it. */
    private static final int BAR_BG = 0xfffafbff;
    private static final float EDGE_FADE = 46f;

    private final Bitmap[] normal = new Bitmap[5];
    private final Bitmap[] active = new Bitmap[5];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF box = new RectF();

    void load(Resources resources) {
        for (int i = 0; i < normal.length; i++) {
            normal[i] = BitmapFactory.decodeResource(resources, NORMAL[i]);
            active[i] = BitmapFactory.decodeResource(resources, ACTIVE[i]);
        }
    }

    boolean draw(Canvas canvas, int selected, float width, float top, float bottom) {
        if (!LicaiVersion.blackCard(LicaiVersion.current())) return false;
        paint.setStyle(Paint.Style.FILL);
        // The band fades in over its own first rows, so the page above it — white sheet, flat grey
        // or a plate — meets it without an edge; only then does it settle on its own fill.
        paint.setShader(new LinearGradient(0, top, 0, top + EDGE_FADE,
                0x00fafbff, BAR_BG, Shader.TileMode.CLAMP));
        canvas.drawRect(0, top, width, top + EDGE_FADE, paint);
        paint.setShader(null);
        paint.setColor(BAR_BG);
        canvas.drawRect(0, top + EDGE_FADE, width, bottom, paint);
        float cell = width / 5f;
        float iconSize = 86f;
        for (int i = 0; i < 5; i++) {
            float center = cell * (i + 0.5f);
            box.set(center - iconSize / 2f, top + 25f, center + iconSize / 2f,
                    top + 25f + iconSize);
            canvas.drawBitmap(i == selected ? active[i] : normal[i], null, box, paint);
            paint.setColor(i == selected ? 0xffb39162 : 0xff10244c);
            paint.setTextSize(35f);
            paint.setTypeface(i == selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(LABELS[i], center, top + 160f, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
        return true;
    }
}
