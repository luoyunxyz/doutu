package com.luoyun.doutu.features.zhi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
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

import com.luoyun.doutu.features.zhi.data.PaySuccessStore;
import com.luoyun.doutu.features.zhi.ui.PaySuccessScreen;

import java.util.Locale;

/**
 * 支付成功页（「支」宫格里的「支付成功」）。
 *
 * <p>页面是 {@link PaySuccessScreen} 照着参考图一块块贴出来的，这里只负责点按：右上角「回首页」
 * 回小支主页，底部「完成」退回上一页，几个红按钮和对账那条给的是演示提示；其余每一行字（标题、
 * 金额、两行明细、对账按钮、白卡里的标题与五行优惠、完成）点一下都能改，改完写进
 * {@link PaySuccessStore}（按账号各存一份），下次进来还是自己填的。
 *
 * <p>顶上那条是蓝底，参考图自带的状态栏内容不画，交给系统（图标保持白色）。
 *
 * <p>整页都是本地假数据：没有真的交易、没有账户、没有优惠券，也不会联网。
 */
public final class PaySuccessActivity extends Activity {
    private static final String EXTRA_NONCE = "pay_success_nonce";
    /** 按住多久算长按（毫秒）——红按钮上的字要长按才改。 */
    private static final long LONG_PRESS_MS = 500;

    private final PaySuccessScreen screen = new PaySuccessScreen();
    private final PaySuccessScreen.State state = new PaySuccessScreen.State(PaySuccessStore.texts());
    private PageView view;
    private float downX;
    private float downY;
    private long downAt;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, PaySuccessActivity.class);
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
    protected void onResume() {
        super.onResume();
        view.invalidate();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) configureWindow(getWindow());
    }

    /** 顶上是蓝底，状态栏图标保持白色。 */
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
                // 不点亮 LIGHT_STATUS_BARS：顶上蓝底上状态栏图标要是白的
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    // ------------------------------------------------------------------ 交互

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        if (token.startsWith("toast:")) {
            Toast.makeText(this, token.substring(6), Toast.LENGTH_SHORT).show();
            return;
        }
        if (token.startsWith("edit:")) {
            showEditor(token.substring(5));
            return;
        }
        switch (token) {
            case "home":
            case "done":
                // 「回首页」和「完成」都是回支付宝的首页，不是退出软件
                startActivity(ZhiHomeActivity.intentFor(this));
                overridePendingTransition(0, 0);
                finish();
                return;
            default:
        }
    }

    // ------------------------------------------------------------------ 改内容

    private String valueOf(String key) {
        String stored = state.texts.get(key);
        if (stored != null) return stored;
        PaySuccessScreen.Run field = PaySuccessScreen.field(key);
        return field == null ? "" : field.def;
    }

    private void showEditor(final String key) {
        final PaySuccessScreen.Run field = PaySuccessScreen.field(key);
        if (field == null) return;
        final boolean numeric = PaySuccessScreen.money(key);
        final EditText input = new EditText(this);
        input.setInputType(numeric
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                : InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setText(valueOf(key));
        input.selectAll();

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(getResources().getDisplayMetrics().density * 20);
        column.setPadding(pad, pad / 2, pad, 0);
        column.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(PaySuccessScreen.titleOf(key))
                .setView(column)
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (d, w) -> {
                    state.texts.remove(key);
                    PaySuccessStore.clearText(key);
                    view.invalidate();
                })
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        input.setError("内容不能为空");
                        return;
                    }
                    if (numeric) value = format(value);
                    state.texts.put(key, value);
                    PaySuccessStore.text(key, value);
                    view.invalidate();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    /** 大金额那一格存的是数字部分，「¥」是画的时候单独加的。 */
    private static String format(String value) {
        try {
            return String.format(Locale.US, "%.2f",
                    Double.parseDouble(value.replace(",", "").trim()));
        } catch (NumberFormatException e) {
            return value;
        }
    }

    // ------------------------------------------------------------------ 画布

    private final class PageView extends View {
        PageView() {
            super(PaySuccessActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / PaySuccessScreen.W, getHeight() / PaySuccessScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * PaySuccessScreen.W / Math.max(1, getWidth());
            float y = event.getY() * PaySuccessScreen.H / Math.max(1, getHeight());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = x;
                    downY = y;
                    downAt = System.currentTimeMillis();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (System.currentTimeMillis() - downAt >= LONG_PRESS_MS) {
                        // 长按落在某个红按钮上：改那行字；落在别处就还按轻点算
                        String key = PaySuccessScreen.buttonKeyAt(x, y);
                        if (key != null) {
                            showEditor(key);
                            return true;
                        }
                        if (Math.abs(x - downX) < 16f && Math.abs(y - downY) < 16f) {
                            return true;
                        }
                    }
                    act(screen.hitTest(state, x, y));
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
