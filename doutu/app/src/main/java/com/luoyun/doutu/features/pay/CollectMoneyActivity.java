package com.luoyun.doutu.features.pay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.luoyun.doutu.features.pay.data.CollectStore;
import com.luoyun.doutu.features.pay.ui.CollectMoneyScreen;

import java.io.InputStream;

/**
 * Hosts the “收钱” page reached from the 收钱 shortcut of the 收付款 sheet.
 *
 * <p>The page is a local mock.  The collection code is generated inside the app, and the only
 * picture that ever enters it is one the user picks from their own gallery: it is cropped to the
 * symbol and kept in memory for as long as the page is open, nothing is uploaded anywhere.
 */
public final class CollectMoneyActivity extends Activity {
    private static final String EXTRA_NONCE = "collect_nonce";
    private static final int REQUEST_PICK = 0x2c01;
    /** Longest edge fed to the locator: sharp enough for the modules, cheap enough to scan. */
    private static final int DECODE_MAX = 2000;

    private final CollectMoneyScreen screen = new CollectMoneyScreen();
    private final CollectMoneyScreen.State state = new CollectMoneyScreen.State();
    private PageView view;
    private float downX;
    private float downY;
    private float lastY;
    private boolean moved;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, CollectMoneyActivity.class);
        intent.putExtra(EXTRA_NONCE, System.nanoTime());
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        screen.load(getResources());
        // 用户上一次改过的名字、以及从相册换来的那张收钱码，先铺回来
        CollectStore.apply(state);
        view = new PageView();
        setContentView(view);
        configureWindow(getWindow());
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) configureWindow(getWindow());
    }

    @Override
    public void onBackPressed() {
        if (state.scroll > 0f) {
            state.scroll = 0f;
            view.invalidate();
            return;
        }
        super.onBackPressed();
    }

    /** The whole page is the warm gradient, so the system bars keep their light icons. */
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
                controller.setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    // ------------------------------------------------------------------ actions

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        switch (token) {
            case "back":
                finish();
                break;
            case "qr":
                pickCode();
                break;
            case "name":
                editName();
                break;
            case "more":
                break;
            case "amount":
                break;
            case "save":
                break;
            case "more2":
                break;
            case "records":
                // 收钱页那一行「收款记录」和支模块宫格里的同一格进的是同一页
                startActivity(com.luoyun.doutu.features.zhi.ReceiptActivity.intentFor(this));
                overridePendingTransition(0, 0);
                break;
            case "assistant":
                break;
            case "business":
                break;
            case "service":
                break;
            case "promo":
                break;
            case "upgrade":
                break;
            default:
                break;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void editName() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("收款人姓名");
        input.setText(state.name);
        input.setSelection(state.name.length());
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("修改姓名")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String value = input.getText().toString().trim();
                    state.name = value.isEmpty() ? "落陨(**玮)" : value;
                    CollectStore.name(state.name);
                    view.invalidate();
                })
                .show();
    }

    // ------------------------------------------------------------------ picking a code

    private void pickCode() {
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
        Uri uri = data.getData();
        Bitmap src = decode(uri);
        if (src == null) {
            toast("图片读取失败");
            return;
        }
        RectF box = QrLocator.locate(src);
        Bitmap code;
        if (box != null) {
            code = Bitmap.createBitmap(src, Math.round(box.left), Math.round(box.top),
                    Math.round(box.width()), Math.round(box.height()));
        } else {
            // No symbol found: fall back to the middle square of the picture.
            int side = Math.min(src.getWidth(), src.getHeight());
            code = Bitmap.createBitmap(src, (src.getWidth() - side) / 2,
                    (src.getHeight() - side) / 2, side, side);
            toast("未识别到二维码，已使用所选图片");
        }
        if (code != src) src.recycle();
        Bitmap previous = state.qr;
        state.qr = code;
        // 换来的这张码也存一份：以前只在这页开着的时候有效，划掉后台再进来就没了
        CollectStore.qr(code);
        if (previous != null) previous.recycle();
        view.invalidate();
    }

    private Bitmap decode(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            while (longest / sample > DECODE_MAX) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ view

    private final class PageView extends View {
        PageView() {
            super(CollectMoneyActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / CollectMoneyScreen.W, getHeight() / CollectMoneyScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * CollectMoneyScreen.W / Math.max(1, getWidth());
            float y = event.getY() * CollectMoneyScreen.H / Math.max(1, getHeight());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = x;
                    downY = y;
                    lastY = y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(y - downY) > 6) moved = true;
                    if (moved) {
                        // Dragging the sheet up reveals the card that closes the page.
                        float next = Math.max(0f,
                                Math.min(screen.maxScroll(), state.scroll + (lastY - y)));
                        if (next != state.scroll) {
                            state.scroll = next;
                            invalidate();
                        }
                    }
                    lastY = y;
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!moved) act(screen.hitTest(state, x, y));
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
