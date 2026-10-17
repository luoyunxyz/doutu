package com.luoyun.doutu.features.zhi;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.luoyun.doutu.features.zhi.data.WithdrawStore;

import java.io.InputStream;
import java.util.function.Consumer;

/**
 * The sheet behind a 提现 card's logo: the banks that came with the project laid out five to a row —
 * the folder the user asked to move in, {@code assets/images/common/bank}, now
 * {@code res/drawable-nodpi/bank_*.jpg} — and 本地上传 beside them for a picture of the user's own.
 *
 * <p>It is the same idea as {@link AvatarChooser}: whichever logo comes back, from a tile or from
 * the album, is handed to the caller to keep on the card.
 */
final class BankIconChooser {
    /** The request the album answers to; the host activity passes its results on to {@link #deliver}. */
    static final int REQUEST_PICK = 0x72;

    private static final int COLUMNS = 5;

    /** What the user settled on: a bank of the project's own, or his own picture. */
    static final class Pick {
        /** The bank key, or {@link WithdrawStore#BESPOKE} for a picture of the user's own. */
        final String bank;
        final Bitmap custom;

        Pick(String bank, Bitmap custom) {
            this.bank = bank;
            this.custom = custom;
        }
    }

    /** What the album pick is for, kept until its answer comes back. */
    private static Consumer<Pick> pending;

    private BankIconChooser() {
    }

    /** Raises the sheet; {@code onPicked} receives whichever logo the user settles on. */
    static void show(Activity activity, String title, Consumer<Pick> onPicked) {
        float density = activity.getResources().getDisplayMetrics().density;
        int dp = Math.round(density);
        int gap = Math.round(8 * density);
        int pad = Math.round(16 * density);
        int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels - 16 * dp,
                Math.round(370 * density));
        int tile = (width - 2 * pad - (COLUMNS - 1) * gap) / COLUMNS;

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, 16 * dp, pad, 18 * dp);
        column.setBackground(fill(0xffffffff, 18 * density));

        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextSize(17f);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setTextColor(0xff23252b);
        column.addView(heading);

        TextView hint = new TextView(activity);
        hint.setText("点一个银行图标，或者从本地上传自己的图");
        hint.setTextSize(12.5f);
        hint.setTextColor(0xff939292);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = 4 * dp;
        column.addView(hint, hintLp);

        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridLp.topMargin = 14 * dp;
        int perRow = 0;
        LinearLayout line = null;
        for (WithdrawStore.Bank bank : WithdrawStore.BANKS) {
            if (perRow == 0) {
                line = new LinearLayout(activity);
                line.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(line, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            Bitmap logo = BitmapFactory.decodeResource(activity.getResources(),
                    WithdrawStore.drawable(bank.key));
            ImageView cell = new ImageView(activity);
            cell.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cell.setPadding(tile / 6, tile / 6, tile / 6, tile / 6);
            cell.setBackground(fill(0xfff4f5f9, 14 * density));
            cell.setImageBitmap(logo);
            cell.setContentDescription(bank.label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, tile, 1f);
            if (perRow > 0) lp.leftMargin = gap / 2;
            if (perRow < COLUMNS - 1) lp.rightMargin = gap / 2;
            if (grid.getChildCount() > 1) lp.topMargin = gap;
            cell.setOnClickListener(v -> {
                onPicked.accept(new Pick(bank.key, null));
                dialog.dismiss();
            });
            line.addView(cell, lp);
            perRow++;
            if (perRow == COLUMNS) perRow = 0;
        }

        // 本地上传：跟银行图标排在同一个格子里，一眼就是「自己挑一张」
        TextView upload = new TextView(activity);
        upload.setText("＋\n本地上传");
        upload.setTextSize(11f);
        upload.setGravity(Gravity.CENTER);
        upload.setTextColor(0xff1478fc);
        upload.setBackground(fill(0xffeef4ff, 14 * density));
        upload.setOnClickListener(v -> {
            dialog.dismiss();
            pending = onPicked;
            pickPicture(activity);
        });
        LinearLayout.LayoutParams uploadLp = new LinearLayout.LayoutParams(0, tile, 1f);
        uploadLp.leftMargin = gap / 2;
        uploadLp.rightMargin = gap / 2;
        uploadLp.topMargin = gap;
        if (perRow > 0) {
            line.addView(upload, uploadLp);
        } else {
            LinearLayout extra = new LinearLayout(activity);
            extra.setOrientation(LinearLayout.HORIZONTAL);
            extra.addView(upload, uploadLp);
            grid.addView(extra, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        column.addView(grid, gridLp);

        dialog.setContentView(column);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    /** Hands an album result to whatever the pick was for; true when it was ours. */
    static boolean deliver(Activity activity, int request, int result, Intent data) {
        if (request != REQUEST_PICK) return false;
        Consumer<Pick> target = pending;
        pending = null;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        Bitmap picture = decode(activity, data.getData());
        if (picture == null) {
            Toast.makeText(activity, "这张图片读不出来", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (target != null) target.accept(new Pick(WithdrawStore.BESPOKE, picture));
        return true;
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
            while (longest / options.inSampleSize > 512) options.inSampleSize *= 2;
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
