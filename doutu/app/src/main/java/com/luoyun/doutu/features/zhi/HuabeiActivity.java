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

import com.luoyun.doutu.features.zhi.data.HuabeiStore;
import com.luoyun.doutu.features.zhi.ui.HuabeiScreen;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 花呗页（小支主页 - 我的 - 花呗）。
 *
 * <p>整页由 {@link HuabeiScreen} 画出来，这里负责滚动、点击，和「点数字/点文字改内容」的弹窗。
 * 顶部的月份跟着系统当前月份走。页面是本地 mock：所有点击都在 App 内应答，不联网。
 */
public final class HuabeiActivity extends Activity {
    private static final float TOUCH_SLOP = 8f;

    private enum Kind { MONEY, INT }

    private static final class Field {
        final String label;
        final double def;
        final Kind kind;

        Field(String label, double def, Kind kind) {
            this.label = label;
            this.def = def;
            this.kind = kind;
        }
    }

    private static final Map<String, Field> FIELDS = new LinkedHashMap<>();
    private static final Map<String, String> TEXTS = new LinkedHashMap<>();

    static {
        FIELDS.put("hb.accrued", new Field("本月已累计", 147.88, Kind.MONEY));
        FIELDS.put("hb.repayDay", new Field("还款日（每月几号）", 20, Kind.INT));
        FIELDS.put("hb.credit", new Field("总计额度", 2710.98, Kind.MONEY));
        FIELDS.put("hb.qinmi", new Field("亲密度权益数", 10, Kind.INT));
        FIELDS.put("hb.followers", new Field("吃谷社区关注人数", 39569, Kind.INT));

        TEXTS.put("hb.status", "已还清");
        // 这行默认跟着当前月份走；点开改成别的之后就以用户填的为准，点「恢复默认」再回到自动月份
        TEXTS.put("hb.repayLabel", HuabeiScreen.currentMonth() + "月应还(元)");
    }

    private final HuabeiScreen screen = new HuabeiScreen();
    private final HuabeiScreen.State state =
            new HuabeiScreen.State(HuabeiStore.numbers(), HuabeiStore.texts());
    private PageView view;

    private float downY;
    private float lastY;
    private boolean moved;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, HuabeiActivity.class);
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

    @Override
    protected void onResume() {
        super.onResume();
        view.invalidate();
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
                // 顶上那层是蓝底，状态栏图标要保持白色
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
    public void onBackPressed() {
        if (state.scroll > 0f) {
            state.scroll = 0f;
            view.invalidate();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    // ------------------------------------------------------------------ 交互

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        if (token.equals("back")) {
            finish();
            return;
        }
        if (token.startsWith("toast:")) {
            Toast.makeText(this, token.substring(6), Toast.LENGTH_SHORT).show();
            return;
        }
        if (token.startsWith("num:")) {
            showNumberEditor(token.substring(4));
            return;
        }
        if (token.startsWith("text:")) {
            showTextEditor(token.substring(5));
        }
    }

    private static LinearLayout column(Activity activity, EditText... fields) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(activity.getResources().getDisplayMetrics().density * 20);
        box.setPadding(pad, pad / 2, pad, 0);
        for (EditText f : fields) box.addView(f, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private void showNumberEditor(final String key) {
        final Field field = FIELDS.get(key);
        if (field == null) return;
        double cur = state.nums.containsKey(key) ? state.nums.get(key) : field.def;

        final EditText input = new EditText(this);
        int flags = InputType.TYPE_CLASS_NUMBER;
        if (field.kind == Kind.MONEY) {
            flags |= InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
        }
        input.setInputType(flags);
        input.setText(field.kind == Kind.MONEY
                ? String.format(Locale.US, "%.2f", cur)
                : String.valueOf((long) cur));
        input.selectAll();

        new AlertDialog.Builder(this)
                .setTitle("修改「" + field.label + "」")
                .setView(column(this, input))
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (d, w) -> {
                    state.nums.remove(key);
                    HuabeiStore.clearNumber(key);
                    view.invalidate();
                })
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        double v = Double.parseDouble(input.getText().toString().trim());
                        state.nums.put(key, v);
                        HuabeiStore.number(key, v);
                    } catch (Exception ignored) {
                        Toast.makeText(this, "数字无效，已忽略", Toast.LENGTH_SHORT).show();
                    }
                    view.invalidate();
                })
                .show();
    }

    private void showTextEditor(final String key) {
        final String def = TEXTS.get(key);
        if (def == null) return;
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(state.texts.containsKey(key) ? state.texts.get(key) : def);
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("修改文字")
                .setView(column(this, input))
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (d, w) -> {
                    state.texts.remove(key);
                    HuabeiStore.clearText(key);
                    view.invalidate();
                })
                .setPositiveButton("保存", (d, w) -> {
                    String v = input.getText().toString().trim();
                    if (v.isEmpty()) {
                        state.texts.remove(key);
                        HuabeiStore.clearText(key);
                    } else {
                        state.texts.put(key, v);
                        HuabeiStore.text(key, v);
                    }
                    view.invalidate();
                })
                .show();
    }

    private void scrollPageTo(float target) {
        state.scroll = Math.max(0f, Math.min(screen.maxScroll(state), target));
        view.invalidate();
    }

    private final class PageView extends View {
        PageView() {
            super(HuabeiActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / HuabeiScreen.W, getHeight() / HuabeiScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * HuabeiScreen.W / Math.max(1, getWidth());
            float y = event.getY() * HuabeiScreen.H / Math.max(1, getHeight());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = y;
                    lastY = y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!moved && Math.abs(y - downY) > TOUCH_SLOP) moved = true;
                    if (moved) scrollPageTo(state.scroll + (lastY - y));
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
