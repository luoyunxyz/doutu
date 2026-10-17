package com.luoyun.doutu.features.zhi;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.luoyun.doutu.features.zhi.data.AvatarApi;

import java.io.InputStream;
import java.util.function.Consumer;

/**
 * The sheet behind every 头像 in this module: sixteen pictures from the random-avatar service laid
 * out four by four, each of them usable with a single tap, a green 刷新 that clears the grid and
 * pulls a whole new sheet, and 从本地上传 under it for the system photo picker.
 *
 * <p>The 个人信息 page owns the first one — the user's own picture — and the 财富黑卡理财师 card on
 * the 理财 tab owns the second. Both end the same way: whichever picture comes back, from a tile or
 * from the album, is handed to the caller to keep.
 *
 * <p>2026-10-17: 也公开给「我」页的账号头像用（{@code account.AvatarUpload}）—— 同一个交互
 * （随机头像 4×4 + 换一批 + 从本地上传）不该在包里写两遍。
 */
public final class AvatarChooser {
    /** The request the album answers to; the host activity passes its results on to {@link #deliver}. */
    public static final int REQUEST_PICK = 0x71;

    private static final int GRID = 4;
    private static final int TILES = GRID * GRID;
    private static final int REFRESH_GREEN = 0xff00b578;
    private static final int REFRESH_GREEN_BUSY = 0xff8ed9be;

    /** What the album pick is for, kept until its answer comes back. */
    private static Consumer<Bitmap> pending;

    private AvatarChooser() {
    }

    /** Raises the sheet; {@code onPicked} receives whatever picture the user settles on. */
    public static void show(Activity activity, String title, Consumer<Bitmap> onPicked) {
        float density = activity.getResources().getDisplayMetrics().density;
        int dp = Math.round(density);
        int gap = Math.round(9 * density);
        int pad = Math.round(18 * density);
        int sheet = Math.min(activity.getResources().getDisplayMetrics().widthPixels - 16 * dp,
                Math.round(330 * density));
        int tile = (sheet - 2 * pad - 3 * gap) / GRID;

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, 16 * dp, pad, 0);
        column.setBackground(fill(0xffffffff, 18 * density));

        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextSize(17f);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setTextColor(0xff23252b);

        final TextView refresh = new TextView(activity);
        refresh.setText("刷新");
        refresh.setTextSize(13f);
        refresh.setTypeface(Typeface.DEFAULT_BOLD);
        refresh.setTextColor(Color.WHITE);
        refresh.setGravity(Gravity.CENTER);
        refresh.setPadding(Math.round(14 * density), Math.round(6 * density),
                Math.round(14 * density), Math.round(6 * density));
        refresh.setBackground(fill(REFRESH_GREEN, 15 * density));
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(heading, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(refresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(header);

        TextView hint = new TextView(activity);
        hint.setText("点击任意头像即可使用");
        hint.setTextSize(12.5f);
        hint.setTextColor(0xff939292);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = 4 * dp;
        column.addView(hint, hintLp);

        final AvatarTile[] tiles = new AvatarTile[TILES];
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridLp.topMargin = 14 * dp;
        for (int row = 0; row < GRID; row++) {
            LinearLayout line = new LinearLayout(activity);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < GRID; col++) {
                int index = row * GRID + col;
                AvatarTile cell = new AvatarTile(activity, 14 * density);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, tile, 1f);
                if (col > 0) lp.leftMargin = gap / 2;
                if (col < GRID - 1) lp.rightMargin = gap / 2;
                if (row > 0) lp.topMargin = gap;
                line.addView(cell, lp);
                tiles[index] = cell;
                cell.setOnClickListener(v -> {
                    if (cell.image() == null) return;
                    onPicked.accept(cell.image());
                    dialog.dismiss();
                });
            }
            grid.addView(line);
        }
        column.addView(grid, gridLp);

        View rule = new View(activity);
        rule.setBackgroundColor(0xffececf1);
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp));
        ruleLp.topMargin = 16 * dp;
        column.addView(rule, ruleLp);

        LinearLayout upload = new LinearLayout(activity);
        upload.setOrientation(LinearLayout.HORIZONTAL);
        upload.setGravity(Gravity.CENTER_VERTICAL);
        upload.setPadding(0, 13 * dp, 0, 15 * dp);

        TextView plus = new TextView(activity);
        plus.setText("+");
        plus.setTextSize(15f);
        plus.setTextColor(Color.WHITE);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(fill(0xff1478fc, 13 * density));
        upload.addView(plus, new LinearLayout.LayoutParams(
                Math.round(26 * density), Math.round(26 * density)));

        TextView label = new TextView(activity);
        label.setText("从本地上传");
        label.setTextSize(15f);
        label.setTextColor(0xff23252b);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.leftMargin = 12 * dp;
        upload.addView(label, labelLp);
        upload.setOnClickListener(v -> {
            dialog.dismiss();
            pending = onPicked;
            pickPicture(activity);
        });
        column.addView(upload);

        dialog.setContentView(column);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(sheet, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();

        refresh.setOnClickListener(v -> fill(tiles, refresh, Math.max(tile * 2, 256)));
        fill(tiles, refresh, Math.max(tile * 2, 256));
    }

    /** Hands an album result to whatever the pick was for; true when it was ours. */
    public static boolean deliver(Activity activity, int request, int result, Intent data) {
        if (request != REQUEST_PICK) return false;
        Consumer<Bitmap> target = pending;
        pending = null;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        Bitmap picture = decode(activity, data.getData());
        if (picture == null) {
            Toast.makeText(activity, "这张图片读不出来", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (target != null) target.accept(picture);
        return true;
    }

    /**
     * Puts a fresh batch of avatars into the sheet: every tile drops back to its placeholder and
     * the button says so while the sixteen requests are in the air.
     */
    private static void fill(AvatarTile[] tiles, TextView refresh, int maxPx) {
        if (!refresh.isEnabled()) return;
        for (AvatarTile tile : tiles) tile.setImage(null);
        setRefreshing(refresh, true);
        int[] outstanding = {tiles.length};
        AvatarApi.fetchBatch(tiles.length, maxPx, (index, bitmap) -> {
            if (bitmap != null) tiles[index].setImage(bitmap);
            if (--outstanding[0] <= 0) setRefreshing(refresh, false);
        });
    }

    /** The button greys out and renames itself while a batch is on its way. */
    private static void setRefreshing(TextView refresh, boolean busy) {
        float density = refresh.getResources().getDisplayMetrics().density;
        refresh.setEnabled(!busy);
        refresh.setText(busy ? "刷新中" : "刷新");
        refresh.setBackground(fill(busy ? REFRESH_GREEN_BUSY : REFRESH_GREEN, 15 * density));
        refresh.setAlpha(busy ? 0.75f : 1f);
    }

    private static GradientDrawable fill(int color, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private static void pickPicture(Activity activity) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= 33) {
            intent = new Intent("android.provider.action.PICK_IMAGES");
            intent.setType("image/*");
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
        }
        try {
            activity.startActivityForResult(intent, REQUEST_PICK);
        } catch (Exception e) {
            Toast.makeText(activity, "无法打开相册", Toast.LENGTH_SHORT).show();
        }
    }

    /** Decodes a picked picture at a sensible size, whichever way the picker hands it over. */
    private static Bitmap decode(Activity activity, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            options.inSampleSize = 1;
            while (longest / options.inSampleSize > 1080) options.inSampleSize *= 2;
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** One tile of the sheet: a rounded square that centre-crops whatever picture it is given. */
    private static final class AvatarTile extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Path path = new Path();
        private final RectF box = new RectF();
        private final RectF dst = new RectF();
        private final float radius;
        private Bitmap image;

        AvatarTile(Context context, float radius) {
            super(context);
            this.radius = radius;
        }

        void setImage(Bitmap value) {
            image = value;
            invalidate();
        }

        Bitmap image() {
            return image;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            box.set(0, 0, getWidth(), getHeight());
            path.reset();
            path.addRoundRect(box, radius, radius, Path.Direction.CW);
            canvas.clipPath(path);
            if (image == null) {
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xffeef1f6);
                canvas.drawRect(box, paint);
                return;
            }
            // centre crop: the shorter side decides the scale, the longer one is trimmed
            float scale = Math.max(getWidth() / (float) image.getWidth(),
                    getHeight() / (float) image.getHeight());
            float w = image.getWidth() * scale;
            float h = image.getHeight() * scale;
            dst.set((getWidth() - w) / 2f, (getHeight() - h) / 2f,
                    (getWidth() + w) / 2f, (getHeight() + h) / 2f);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawBitmap(image, null, dst, paint);
        }
    }
}
