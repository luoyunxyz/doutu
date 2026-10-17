package com.luoyun.doutu.features.zhi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
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
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.luoyun.doutu.features.zhi.data.AvatarApi;
import com.luoyun.doutu.features.zhi.data.ProfileStore;
import com.luoyun.doutu.features.zhi.ui.ProfileScreen;

import java.io.InputStream;

/**
 * Hosts the 个人信息 page reached from the identity row of the 我的 tab.
 *
 * <p>The three rows that answer the user are the ones this activity owns: 头像 replaces the
 * picture either from the system photo picker or with a fresh one from the random-avatar service,
 * 昵称 and 支付宝账号 open a text dialog. Everything the user changes is written to
 * {@link ProfileStore}, so the 我的 tab behind this page shows it as soon as it comes back.
 */
public final class ProfileActivity extends Activity {
    private static final String EXTRA_NONCE = "profile_nonce";
    private static final int REQUEST_PICK = 0x51;
    /** The chooser offers a 4x4 sheet of avatars from the random-avatar service. */
    private static final int GRID = 4;
    private static final int TILES = GRID * GRID;
    /** The green of the sheet's 刷新 button. */
    private static final int REFRESH_GREEN = 0xff00b578;
    private static final int REFRESH_GREEN_BUSY = 0xff8ed9be;

    private final ProfileScreen screen = new ProfileScreen();
    private final ProfileScreen.State state = new ProfileScreen.State();
    private View view;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, ProfileActivity.class);
        intent.putExtra(EXTRA_NONCE, System.nanoTime());
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        screen.load(getResources());
        view = new PageView();
        setContentView(view);
        configureWindow(getWindow());
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) configureWindow(getWindow());
    }

    private void configureWindow(Window window) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            window.getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                            | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    /** Leaving the card is instant — the tab underneath is the previous screen, so no slide. */
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    // ------------------------------------------------------------------ acting

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        if (token.equals("back")) {
            finish();
            return;
        }
        if (token.startsWith("toast:")) {
            toast(token.substring(6));
            return;
        }
        if (token.equals("avatar")) {
            chooseAvatar();
            return;
        }
        if (token.equals("editName")) {
            edit("修改昵称", "昵称", ProfileStore.name(), ProfileStore::name);
            return;
        }
        if (token.equals("editPhone")) {
            edit("修改支付宝账号", "支付宝账号", ProfileStore.phone(), ProfileStore::phone);
        }
    }

    /**
     * The avatar row answers with a sheet: sixteen avatars from the random-avatar service laid out
     * four by four, each of them usable with a tap, and 从本地上传 under them for the album.
     */
    private void chooseAvatar() {
        float density = getResources().getDisplayMetrics().density;
        int dp = Math.round(density);
        int gap = Math.round(9 * density);
        int pad = Math.round(18 * density);
        int sheet = Math.min(getResources().getDisplayMetrics().widthPixels - 16 * dp,
                Math.round(330 * density));
        int tile = (sheet - 2 * pad - 3 * gap) / GRID;

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, 16 * dp, pad, 0);
        column.setBackground(fill(0xffffffff, 18 * density));

        TextView title = new TextView(this);
        title.setText("更换头像");
        title.setTextSize(17f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xff23252b);

        // the header row carries the title and the button that pulls a whole new sheet
        final TextView refresh = new TextView(this);
        refresh.setText("刷新");
        refresh.setTextSize(13f);
        refresh.setTypeface(Typeface.DEFAULT_BOLD);
        refresh.setTextColor(Color.WHITE);
        refresh.setGravity(Gravity.CENTER);
        refresh.setPadding(Math.round(14 * density), Math.round(6 * density),
                Math.round(14 * density), Math.round(6 * density));
        refresh.setBackground(fill(REFRESH_GREEN, 15 * density));
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(refresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(header);

        TextView hint = new TextView(this);
        hint.setText("点击任意头像即可使用");
        hint.setTextSize(12.5f);
        hint.setTextColor(0xff939292);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = 4 * dp;
        column.addView(hint, hintLp);

        final AvatarTile[] tiles = new AvatarTile[TILES];
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridLp.topMargin = 14 * dp;
        for (int row = 0; row < GRID; row++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < GRID; col++) {
                int index = row * GRID + col;
                AvatarTile cell = new AvatarTile(this, 14 * density);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, tile, 1f);
                if (col > 0) lp.leftMargin = gap / 2;
                if (col < GRID - 1) lp.rightMargin = gap / 2;
                if (row > 0) lp.topMargin = gap;
                line.addView(cell, lp);
                tiles[index] = cell;
                cell.setOnClickListener(v -> {
                    if (cell.image() == null) return;
                    ProfileStore.avatar(cell.image());
                    view.invalidate();
                    dialog.dismiss();
                });
            }
            grid.addView(line);
        }
        column.addView(grid, gridLp);

        View rule = new View(this);
        rule.setBackgroundColor(0xffececf1);
        LinearLayout.LayoutParams ruleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp));
        ruleLp.topMargin = 16 * dp;
        column.addView(rule, ruleLp);

        LinearLayout upload = new LinearLayout(this);
        upload.setOrientation(LinearLayout.HORIZONTAL);
        upload.setGravity(Gravity.CENTER_VERTICAL);
        upload.setPadding(0, 13 * dp, 0, 15 * dp);
        upload.setBackground(fill(0x00000000, 0f));

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextSize(15f);
        plus.setTextColor(Color.WHITE);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(fill(0xff1478fc, 13 * density));
        LinearLayout.LayoutParams plusLp = new LinearLayout.LayoutParams(
                Math.round(26 * density), Math.round(26 * density));
        upload.addView(plus, plusLp);

        TextView label = new TextView(this);
        label.setText("从本地上传");
        label.setTextSize(15f);
        label.setTextColor(0xff23252b);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.leftMargin = 12 * dp;
        upload.addView(label, labelLp);
        upload.setOnClickListener(v -> {
            dialog.dismiss();
            pickPicture();
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

    /**
     * Puts a fresh batch of avatars into the sheet: every tile drops back to its placeholder and
     * the button says so while the sixteen requests are in the air.
     */
    private void fill(AvatarTile[] tiles, TextView refresh, int maxPx) {
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
    private void setRefreshing(TextView refresh, boolean busy) {
        float density = getResources().getDisplayMetrics().density;
        refresh.setEnabled(!busy);
        refresh.setText(busy ? "刷新中" : "刷新");
        refresh.setBackground(fill(busy ? REFRESH_GREEN_BUSY : REFRESH_GREEN, 15 * density));
        refresh.setAlpha(busy ? 0.75f : 1f);
    }

    private GradientDrawable fill(int color, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private void pickPicture() {
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
            startActivityForResult(intent, REQUEST_PICK);
        } catch (Exception e) {
            toast("无法打开相册");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_PICK || result != RESULT_OK || data == null
                || data.getData() == null) {
            return;
        }
        Bitmap picture = decode(data.getData());
        if (picture == null) {
            toast("这张图片读不出来");
            return;
        }
        ProfileStore.avatar(picture);
        view.invalidate();
    }

    /** Decodes a picked picture at a sensible size, whichever way the picker hands it over. */
    private Bitmap decode(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            options.inSampleSize = 1;
            while (longest / options.inSampleSize > 1080) options.inSampleSize *= 2;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** The shared text dialog behind 昵称 and 支付宝账号. */
    private void edit(String title, String hint, String current, java.util.function.Consumer<String> save) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setHint(hint);
        field.setText(current);
        field.setSelection(field.getText().length());
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        column.setPadding(pad, pad / 2, pad, 0);
        column.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(column)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    save.accept(field.getText().toString());
                    view.invalidate();
                })
                .show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

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

    private final class PageView extends View {
        PageView() {
            super(ProfileActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / ProfileScreen.W, getHeight() / ProfileScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * ProfileScreen.W / Math.max(1, getWidth());
            float y = event.getY() * ProfileScreen.H / Math.max(1, getHeight());
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                act(screen.hitTest(state, x, y));
            }
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
