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

import com.luoyun.doutu.features.zhi.ui.MessageScreen;
import com.luoyun.doutu.features.zhi.data.LicaiVersion;

/**
 * Hosts the interactive 消息 tab of the 小支主页, reached by tapping 消息 in that page's bottom
 * navigation and from the 理财 tab's bar as well.
 *
 * <p>The page is a local mock: every tap is answered inside the app, nothing is requested from the
 * network and no message, account or contact exists.
 */
public final class MessageActivity extends Activity {
    private static final String EXTRA_NONCE = "msg_nonce";
    private static final String[] NAV_LABELS = {"支", "理财", "视频", "消息", "我的"};

    private final MessageScreen screen = new MessageScreen();
    private final MessageScreen.State state = new MessageScreen.State();
    private PageView view;

    private float downY;
    private float lastY;
    private boolean moved;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, MessageActivity.class);
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
        // The page is light from its very top, so the status bar keeps its dark glyphs.
        configureWindow(getWindow(), LicaiVersion.blackCard(LicaiVersion.current()));
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) configureWindow(getWindow(), LicaiVersion.blackCard(LicaiVersion.current()));
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

    /** Leaving the tab is instant — the page underneath is the previous tab, so no slide. */
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        if (token.equals("box")) {
            startActivity(PayMsgActivity.intentFor(this));
            overridePendingTransition(0, 0);
            return;
        }
        if (token.startsWith("toast:")) {
            toast(token.substring(6));
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
                // already here
            } else {
                if (index == 4) {
                    startActivity(MeActivity.intentFor(this));
                    overridePendingTransition(0, 0);
                } else {
                }
            }
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private final class PageView extends View {
        PageView() {
            super(MessageActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / MessageScreen.W, getHeight() / MessageScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * MessageScreen.W / Math.max(1, getWidth());
            float y = event.getY() * MessageScreen.H / Math.max(1, getHeight());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = y;
                    lastY = y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(y - downY) > 8) moved = true;
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
