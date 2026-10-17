package com.luoyun.doutu.features.zhi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import com.luoyun.doutu.R;
import com.luoyun.doutu.features.zhi.data.Bill;
import com.luoyun.doutu.features.zhi.data.BillStore;
import com.luoyun.doutu.features.zhi.ui.BillScreen;

/**
 * Hosts the 账单 page the 我的 tab''s 账单 row opens.
 *
 * <p>The list is rebuilt every time the page is opened — {@link BillStore#generate()} — so the month
 * it heads with is the month it is opened in and its orders are made up fresh. The page scrolls the
 * way the rest of the module does: it follows the finger and stays where it is let go. A row opens
 * the 账单详情 page, which arrives from the right edge.
 *
 * <p>The page is a local mock: no account, balance, merchant or order exists, and nothing leaves
 * the device.
 */
public final class BillActivity extends Activity {
    private static final String EXTRA_NONCE = "bill_nonce";
    /** Design-space travel a finger must cover before a press turns into a drag. */
    private static final float TOUCH_SLOP = 8f;
    /** 按住多久算长按（毫秒）。 */
    private static final long LONG_PRESS_MS = 500;

    private final BillScreen screen = new BillScreen();
    private final BillScreen.State state = new BillScreen.State();
    private PageView view;

    private float downY;
    private float lastY;
    private float downX;
    private long downAt;
    private boolean moved;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, BillActivity.class);
        intent.putExtra(EXTRA_NONCE, System.nanoTime());
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        BillStore.generate();
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

    /** Coming back from a row is instant: the list is already the page underneath. */
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    // ------------------------------------------------------------------ acting

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        if (token.startsWith("toast:")) {
            android.widget.Toast.makeText(this, token.substring(6),
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (token.equals("analytics")) {
            startActivity(BillAnalyticsActivity.intentFor(this));
            overridePendingTransition(0, 0);
            return;
        }
        if (token.equals("back")) {
            finish();
            return;
        }
        if (token.equals("more")) {
            showMoreMenu();
            return;
        }
        if (token.startsWith("chip:")) {
            state.kind = token.substring(5);
            state.scroll = 0f;
            view.invalidate();
            return;
        }
        if (token.startsWith("bill:")) {
            String[] parts = token.split(":");
            Bill bill = screen.billAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            if (bill == null) return;
            BillStore.select(screen.monthAt(Integer.parseInt(parts[1])), bill);
            startActivity(BillDetailActivity.intentFor(this));
            overridePendingTransition(R.anim.bill_in, 0);
        }
    }

    /** The page follows the finger exactly and stays where it is let go. */
    private void scrollPageTo(float target) {
        state.scroll = Math.max(0f, Math.min(screen.maxScroll(state), target));
        view.invalidate();
    }

    // ------------------------------------------------------------------ 右上角菜单 / 长按菜单

    /** 右上角三个点：清空 / 秒变富豪。 */
    private void showMoreMenu() {
        new AlertDialog.Builder(this)
                .setTitle("账单管理")
                .setItems(new String[]{"清空", "秒变富豪"}, (d, which) -> {
                    if (which == 0) confirmClear();
                    else becomeRich();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("清空所有账单？")
                .setMessage("会把全部账单删掉，之后不会自动恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (d, w) -> {
                    BillStore.clear();
                    state.scroll = 0f;
                    view.invalidate();
                    toast("已清空");
                })
                .show();
    }

    private void becomeRich() {
        BillStore.becomeRich();
        state.scroll = 0f;
        view.invalidate();
        toast("已生成大额账单");
    }

    /** 长按一条账单：弹删除。 */
    private void onLongPress(float x, float y) {
        String token = screen.hitTest(state, x, y);
        if (token == null || !token.startsWith("bill:")) return;
        String[] parts = token.split(":");
        if (parts.length < 3) return;
        try {
            showBillMenu(screen.billAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        } catch (Exception ignored) {
        }
    }

    private void showBillMenu(final Bill bill) {
        if (bill == null) return;
        String head = bill.merchant == null || bill.merchant.isEmpty() ? "这条账单" : bill.merchant;
        new AlertDialog.Builder(this)
                .setTitle(head)
                .setItems(new String[]{"删除这条账单"}, (d, which) -> {
                    if (BillStore.remove(bill)) {
                        state.scroll = Math.min(state.scroll, screen.maxScroll(state));
                        view.invalidate();
                        toast("已删除");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    private final class PageView extends View {
        PageView() {
            super(BillActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / BillScreen.W, getHeight() / BillScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * BillScreen.W / Math.max(1, getWidth());
            float y = event.getY() * BillScreen.H / Math.max(1, getHeight());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = y;
                    lastY = y;
                    downX = x;
                    downAt = System.currentTimeMillis();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!moved && (Math.abs(y - downY) > TOUCH_SLOP
                            || Math.abs(x - downX) > TOUCH_SLOP)) moved = true;
                    if (moved) scrollPageTo(state.scroll + (lastY - y));
                    lastY = y;
                    return true;
                case MotionEvent.ACTION_UP:
                    if (moved) return true;
                    if (System.currentTimeMillis() - downAt >= LONG_PRESS_MS) onLongPress(x, y);
                    else act(screen.hitTest(state, x, y));
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
