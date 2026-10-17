package com.luoyun.doutu.features.wei.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/** Shared typography, spacing helpers, and original color tokens for the inbox feature. */
final class WeiPalette {
    static final int BACKGROUND = Color.rgb(245, 246, 250);
    static final int SURFACE = Color.WHITE;
    static final int TEXT = Color.rgb(37, 41, 55);
    static final int MUTED = Color.rgb(125, 130, 145);
    static final int ACCENT = Color.rgb(91, 83, 194);
    static final int LINE = Color.rgb(231, 233, 239);

    private WeiPalette() { }

    static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static TextView text(Context context, String value, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    static GradientDrawable rounded(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setShape(GradientDrawable.OVAL);
        return drawable;
    }
}
