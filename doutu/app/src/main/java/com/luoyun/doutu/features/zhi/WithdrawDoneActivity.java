package com.luoyun.doutu.features.zhi;

import android.app.Activity;
import android.content.Context;
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

import com.luoyun.doutu.features.zhi.ui.WithdrawDoneScreen;

/**
 * Hosts 提交成功 — the blue page a withdrawal ends on, reached once the six digits of the payment
 * password are in. Both 完成 (the one at the top right and the button at the bottom) put the page
 * away and hand the user back to the 提现 page behind it, with the amount box empty again.
 *
 * <p>Nothing was withdrawn and nothing was paid: the figures only travel here so the page can print
 * the ones the user typed a moment ago.
 */
public final class WithdrawDoneActivity extends Activity {
    private static final String EXTRA_AMOUNT = "done_amount";
    private static final String EXTRA_BANK = "done_bank";

    private final WithdrawDoneScreen screen = new WithdrawDoneScreen();
    private View view;
    private double amount;
    private String bank = "银行卡";

    public static Intent intentFor(Context context, double amount, String bank) {
        Intent intent = new Intent(context, WithdrawDoneActivity.class);
        intent.putExtra(EXTRA_AMOUNT, amount);
        intent.putExtra(EXTRA_BANK, bank);
        return intent;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        screen.load(getResources());
        amount = getIntent().getDoubleExtra(EXTRA_AMOUNT, 0);
        String passed = getIntent().getStringExtra(EXTRA_BANK);
        if (passed != null && !passed.isEmpty()) bank = passed;
        view = new PageView();
        setContentView(view);
        configureWindow(getWindow());
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) configureWindow(getWindow());
    }

    /**
     * 这一页顶上就是那片蓝，要的是白图标 —— 和 支 模块别的页正好相反。
     */
    private void configureWindow(Window window) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                // 上面是蓝的、下面那条也是页底色，所以状态栏要浅色图标、导航栏要深色
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        switch (token) {
            case "finish":
                finish();
                overridePendingTransition(0, 0);
                break;
            case "progress":
                break;
            default:
                break;
        }
    }

    private final class PageView extends View {
        PageView() {
            super(WithdrawDoneActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / WithdrawDoneScreen.W, getHeight() / WithdrawDoneScreen.H);
            screen.draw(canvas, amount, bank);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * WithdrawDoneScreen.W / Math.max(1, getWidth());
            float y = event.getY() * WithdrawDoneScreen.H / Math.max(1, getHeight());
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                act(screen.hitTest(x, y));
                performClick();
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
