package com.luoyun.doutu.features.zhi;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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

import com.luoyun.doutu.R;
import com.luoyun.doutu.features.zhi.data.AvatarApi;
import com.luoyun.doutu.features.zhi.data.Bill;
import com.luoyun.doutu.features.zhi.data.BillStore;
import com.luoyun.doutu.features.zhi.ui.BillDetailScreen;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Hosts the 账单详情 page a row of the 账单 list slides in.
 *
 * <p>The row the list handed over is the one this page edits: the mark, the merchant, the time, the
 * amount, the description and the two order numbers all answer a tap with a dialog, 计入收支 is a
 * switch, and the change shows on the list behind the moment this page is left. The mark is picked
 * the same way 个人信息's avatar is — a sheet of pictures from the random-image service, or one of
 * the user's own from the album.
 *
 * <p>The page is a local mock: no account, merchant or order exists, and nothing leaves the device
 * except the one request the random-image service needs.
 */
public final class BillDetailActivity extends Activity {
    private static final String EXTRA_NONCE = "bill_detail_nonce";
    private static final int REQUEST_PICK = 0x61;
    private static final int GRID = 4;
    private static final int TILES = GRID * GRID;
    private static final int REFRESH_GREEN = 0xff00b578;
    private static final int REFRESH_GREEN_BUSY = 0xff8ed9be;
    private static final float TOUCH_SLOP = 8f;

    private final BillDetailScreen screen = new BillDetailScreen();
    private final BillDetailScreen.State state = new BillDetailScreen.State();
    private View view;

    private float downY;
    private float lastY;
    private boolean moved;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, BillDetailActivity.class);
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

    /** Leaving slides the card back off to the right, the way it came in. */
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.bill_out);
    }

    // ------------------------------------------------------------------ acting

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        if (token.startsWith("toast:")) {
            toast(token.substring(6));
            return;
        }
        Bill bill = BillStore.selected();
        if (bill == null) {
            finish();
            return;
        }
        switch (token) {
            case "back":
                finish();
                return;
            case "icon":
                chooseMark(bill);
                return;
            case "toggle:counted":
                bill.counted = !bill.counted;
                view.invalidate();
                return;
            case "edit:payMethod":
                edit("修改付款方式", "付款方式", bill.payMethod, value -> bill.payMethod=value); return;
            case "edit:reward":
                edit("修改支付奖励", "支付奖励", bill.reward, value -> bill.reward=value); return;
            case "edit:category":
                edit("修改账单分类", "账单分类", bill.billCategory, value -> {bill.billCategory=value; bill.category=value;}); return;
            case "edit:payee":
                edit("修改商户名称", "商户名称", bill.payee, value -> bill.payee = value);
                return;
            case "edit:amount":
                edit("修改金额", "金额，负数表示支出", plain(bill.amount), value -> {
                    Double parsed = number(value);
                    if (parsed == null) {
                        throw new IllegalArgumentException("请输入有效金额");
                    }
                    bill.amount = Math.round(parsed * 100) / 100.0;
                });
                return;
            case "edit:time":
                edit("修改支付时间", "yyyy-MM-dd HH:mm:ss",
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                                .format(new Date(bill.time)),
                        value -> {
                            try {
                                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                                format.setLenient(false);
                                java.text.ParsePosition position = new java.text.ParsePosition(0);
                                Date parsed = format.parse(value,position);
                                if(parsed == null || position.getIndex()!=value.length()) throw new IllegalArgumentException();
                                bill.time = parsed.getTime();
                            } catch (Exception e) {
                                throw new IllegalArgumentException("请输入有效时间，格式为 yyyy-MM-dd HH:mm:ss");
                            }
                        });
                return;
            case "edit:goods":
                edit("修改商品说明", "商品说明", bill.goods, value -> { bill.goods = value; bill.merchant = value; });
                return;
            case "edit:orderNo":
                edit("修改订单号", "订单号", bill.orderNo, value -> { if(!value.matches("[0-9]{28}")) throw new IllegalArgumentException("订单号须为28位数字"); bill.orderNo = value; });
                return;
            case "edit:merchantOrderNo":
                edit("修改商家订单号", "商家订单号", bill.merchantOrderNo,
                        value -> { int length = "自动扣款成功".equals(bill.status)?24:15; if(!value.matches("[A-Za-z0-9]{"+length+"}")) throw new IllegalArgumentException("商家订单号须为"+length+"位字母或数字"); bill.merchantOrderNo = value; });
                return;
            case "edit:tag":
                edit("修改标签", "标签", bill.tag.isEmpty() ? "请选择" : bill.tag,
                        value -> bill.tag = value.equals("请选择") ? "" : value);
                return;
            case "edit:note":
                edit("修改备注", "备注", bill.note, value -> bill.note = value);
                return;
            default:

        }
    }

    private static String plain(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    /** Digits, a dot and a leading minus, so "+12.5" and "12.50" both read. */
    private static Double number(String text) {
        try {
            double value = Double.parseDouble(text.replace(",", "").trim());
            return Double.isFinite(value) && Math.abs(value)<1e10 ? value : null;
        } catch (NumberFormatException e) { return null; }
    }

    /** The shared text dialog behind every editable line of the card. */
    private void edit(String title, String hint, String current,
                      java.util.function.Consumer<String> save) {
        EditText field = new EditText(this);
        field.setSingleLine(!title.contains("商品说明"));
        if(title.contains("商品说明")) { field.setMinLines(2); field.setMaxLines(5); }
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
        AlertDialog editor = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(column)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null).create();
        editor.setOnShowListener(ignored -> editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = field.getText().toString().trim();
            if(value.isEmpty() && !title.contains("备注") && !title.contains("标签")) { field.setError("内容不能为空"); return; }
            try { save.accept(value); view.invalidate(); editor.dismiss(); }
            catch(IllegalArgumentException ex) { field.setError(ex.getMessage()); }
        }));
        editor.show();
    }

    // ------------------------------------------------------------------ the mark

    /**
     * The mark answers the way 个人信息's avatar does: a sheet of sixteen pictures from the
     * random-image service, and 从本地上传 under it for the album.
     */
    private void chooseMark(Bill bill) {
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
        title.setText("更换图标");
        title.setTextSize(17f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(0xff23252b);

        final TextView refresh = new TextView(this);
        refresh.setText("刷新");
        refresh.setTextSize(13f);
        refresh.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
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
        hint.setText("点击任意图片即可使用");
        hint.setTextSize(12.5f);
        hint.setTextColor(0xff939292);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = 4 * dp;
        column.addView(hint, hintLp);

        final MarkTile[] tiles = new MarkTile[TILES];
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
                MarkTile cell = new MarkTile(this, 14 * density);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, tile, 1f);
                if (col > 0) lp.leftMargin = gap / 2;
                if (col < GRID - 1) lp.rightMargin = gap / 2;
                if (row > 0) lp.topMargin = gap;
                line.addView(cell, lp);
                tiles[index] = cell;
                cell.setOnClickListener(v -> {
                    if (cell.image() == null) return;
                    bill.icon = cell.image();
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

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextSize(15f);
        plus.setTextColor(Color.WHITE);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(fill(0xff1478fc, 13 * density));
        upload.addView(plus, new LinearLayout.LayoutParams(
                Math.round(26 * density), Math.round(26 * density)));

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

    private void fill(MarkTile[] tiles, TextView refresh, int maxPx) {
        if (!refresh.isEnabled()) return;
        for (MarkTile tile : tiles) tile.setImage(null);
        setRefreshing(refresh, true);
        int[] outstanding = {tiles.length};
        AvatarApi.fetchBatch(tiles.length, maxPx, (index, bitmap) -> {
            if (bitmap != null) tiles[index].setImage(bitmap);
            if (--outstanding[0] <= 0) setRefreshing(refresh, false);
        });
    }

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
        Bill bill = BillStore.selected();
        if (request != REQUEST_PICK || result != RESULT_OK || data == null
                || data.getData() == null || bill == null) {
            return;
        }
        Bitmap picture = decode(data.getData());
        if (picture == null) {
            toast("这张图片读不出来");
            return;
        }
        bill.icon = picture;
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
            while (longest / options.inSampleSize > 512) options.inSampleSize *= 2;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ the tile and the view

    private static final class MarkTile extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Path path = new Path();
        private final RectF box = new RectF();
        private final RectF dst = new RectF();
        private final float radius;
        private Bitmap image;

        MarkTile(Context context, float radius) {
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
            super(BillDetailActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / BillDetailScreen.W, getHeight() / BillDetailScreen.H);
            screen.draw(canvas, state, BillStore.selected());
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * BillDetailScreen.W / Math.max(1, getWidth());
            float y = event.getY() * BillDetailScreen.H / Math.max(1, getHeight());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = y;
                    lastY = y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!moved && Math.abs(y - downY) > TOUCH_SLOP) moved = true;
                    if (moved) {
                        Bill bill = BillStore.selected();
                        state.scroll = Math.max(0f,
                                Math.min(screen.maxScroll(state, bill), state.scroll + (lastY - y)));
                        invalidate();
                    }
                    lastY = y;
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) { performClick(); act(screen.hitTest(state, BillStore.selected(), x, y)); }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return true;
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
