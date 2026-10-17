package com.luoyun.doutu.features.zhi;

import android.app.Activity;
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
import android.widget.Toast;

import com.luoyun.doutu.features.zhi.ui.MeScreen;
import com.luoyun.doutu.features.zhi.data.LicaiVersion;
import com.luoyun.doutu.features.zhi.data.MeStore;

/**
 * Hosts the interactive 我的 tab of the 小支主页, reached by tapping 我的 in the bottom navigation
 * of the 支 home page or of the 理财 / 消息 tabs.
 *
 * <p>Owns the page's vertical scrolling and the tap that opens 个人信息. The page is a local mock:
 * every tap is answered inside the app, nothing is requested from the network and no account,
 * balance or order exists.
 *
 * <p>The scroll follows the finger exactly: the offset is the finger's own travel, clamped to the
 * page's two ends, and it stays there when the finger is lifted. There is no momentum and no
 * overscroll, so the page stops where it is dragged to rather than sailing on past it.
 */
public final class MeActivity extends Activity {
    private static final String EXTRA_NONCE = "me_nonce";
    private static final String[] NAV_LABELS = {"支", "理财", "视频", "消息", "我的"};
    private static final float TOUCH_SLOP = 8f;

    private final MeScreen screen = new MeScreen();
    private final MeScreen.State state = new MeScreen.State();
    private PageView view;

    private float downY;
    private float lastY;
    private boolean moved;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, MeActivity.class);
        intent.putExtra(EXTRA_NONCE, System.nanoTime());
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        screen.load(getResources());
        // 用户关掉过的「完成身份」条不该再回来
        MeStore.apply(state);
        view = new PageView();
        setContentView(view);
        // The page is light from its very top, so the status bar keeps its dark glyphs.
        configureWindow(getWindow(), LicaiVersion.blackCard(LicaiVersion.current()));
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) configureWindow(getWindow(), LicaiVersion.blackCard(LicaiVersion.current()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        configureWindow(getWindow(), LicaiVersion.blackCard(LicaiVersion.current()));
        view.invalidate();
    }

    private void configureWindow(Window window, boolean whiteStatusIcons) {
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
                int appearance = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                int mask = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                if (!whiteStatusIcons) appearance |= WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                controller.setSystemBarsAppearance(appearance, mask);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            if (!whiteStatusIcons) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    @Override
    public void onBackPressed() {
        if (state.scroll > 0f) {
            scrollPageTo(0f);
            return;
        }
        super.onBackPressed();
    }

    /** Leaving the tab is instant — the page underneath is the previous tab, so no slide. */
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    // ------------------------------------------------------------------ acting

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        if (token.startsWith("toast:")) {
            toast(token.substring(6));
            return;
        }
        if (token.equals("profile")) {
            startActivity(ProfileActivity.intentFor(this));
            overridePendingTransition(0, 0);
            return;
        }
        if (token.equals("bills")) {
            startActivity(BillActivity.intentFor(this));
            overridePendingTransition(0, 0);
            return;
        }
        if (token.equals("assets")) {
            startActivity(AssetActivity.intentFor(this));
            overridePendingTransition(0, 0);
            return;
        }
        if (token.equals("balance")) {
            startActivity(BalanceActivity.intentFor(this));
            overridePendingTransition(0, 0);
            return;
        }
        if (token.equals("yuebao")) {
            startActivity(YuebaoActivity.intentFor(this));
            overridePendingTransition(0, 0);
            return;
        }
        if (token.equals("huabei")) {
            startActivity(HuabeiActivity.intentFor(this));
            overridePendingTransition(0, 0);
            return;
        }
        if (token.equals("bannerClose")) {
            state.banner = false;
            MeStore.banner(false);
            view.invalidate();
            return;
        }
        if (token.startsWith("nav:")) {
            int index = Integer.parseInt(token.substring(4));
            if (index == 0) {
                startActivity(ZhiHomeActivity.intentFor(this));
                overridePendingTransition(0, 0);
            } else if (index == 1) {
                startActivity(LicaiActivity.intentFor(this));
                overridePendingTransition(0, 0);
            } else if (index == 3) {
                startActivity(MessageActivity.intentFor(this));
                overridePendingTransition(0, 0);
            } else if (index == 4) {
                scrollPageTo(0f);
            } else {

            }
        }
    }

    /** Puts the page at a scroll offset, clamped to its own ends. */
    private void scrollPageTo(float target) {
        state.scroll = Math.max(0f, Math.min(screen.maxScroll(state), target));
        view.invalidate();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private final class PageView extends View {
        PageView() {
            super(MeActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / MeScreen.W, getHeight() / MeScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * MeScreen.W / Math.max(1, getWidth());
            float y = event.getY() * MeScreen.H / Math.max(1, getHeight());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = y;
                    lastY = y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!moved && Math.abs(y - downY) > TOUCH_SLOP) moved = true;
                    if (moved && downY < MeScreen.NAV_TOP) {
                        scrollPageTo(state.scroll + (lastY - y));
                    }
                    lastY = y;
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // The page stays exactly where the drag left it — a release is not a flick.
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
