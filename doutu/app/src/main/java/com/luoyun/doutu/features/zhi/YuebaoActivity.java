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

import com.luoyun.doutu.features.zhi.data.YuebaoStore;
import com.luoyun.doutu.features.zhi.ui.YuebaoScreen;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 余额宝页（小支主页 - 我的 - 余额 - 余额宝）。
 *
 * <p>整页由 {@link YuebaoScreen} 画出来，这里负责点击，和「点数字/点文字改内容」的弹窗。
 * 页面上出现的每一个金额、每一个年化都登记成了热区，点一下就能改，改完立即重画；改过的值写进
 * {@link YuebaoStore}，关掉 App 再打开还在。页面是本地 mock：所有点击都在 App 内应答，不联网。
 */
public final class YuebaoActivity extends Activity {
    private static final float TOUCH_SLOP = 8f;

    private enum Kind { MONEY, PCT2, PCT4 }

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

    /** 点得动的数字。 */
    private static final Map<String, Field> FIELDS = new LinkedHashMap<>();
    /** 点得动的文字。 */
    private static final Map<String, String> TEXTS = new LinkedHashMap<>();

    static {
        FIELDS.put("yb.rate", new Field("七日年化（%）", 0.8360, Kind.PCT4));
        FIELDS.put("yb.total", new Field("总金额", 0, Kind.MONEY));
        FIELDS.put("yb.avail", new Field("可用", 0, Kind.MONEY));
        FIELDS.put("yb.zan", new Field("攒着", 0, Kind.MONEY));
        FIELDS.put("yb.cum", new Field("累计收益", 8.81, Kind.MONEY));
        FIELDS.put("yb.huo", new Field("活期理财年化（%）", 2.12, Kind.PCT2));
        FIELDS.put("yb.ding", new Field("定期理财年化（%）", 3.29, Kind.PCT2));

        TEXTS.put("yb.yes", "暂无收益");
        TEXTS.put("yb.gift", "送你最高88元随机立减红包");
    }

    private final YuebaoScreen screen = new YuebaoScreen();
    private final YuebaoScreen.State state =
            new YuebaoScreen.State(YuebaoStore.numbers(), YuebaoStore.texts());
    private PageView view;

    private float downY;
    private float lastY;
    private boolean moved;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, YuebaoActivity.class);
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
                // 顶上那层是橙色，状态栏图标要保持白色
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
        int flags = InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
        input.setInputType(flags);
        input.setText(hintFor(field.kind, cur));
        input.selectAll();

        new AlertDialog.Builder(this)
                .setTitle("修改「" + field.label + "」")
                .setView(column(this, input))
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (d, w) -> {
                    state.nums.remove(key);
                    YuebaoStore.clearNumber(key);
                    view.invalidate();
                })
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        double v = Double.parseDouble(input.getText().toString().trim());
                        state.nums.put(key, v);
                        YuebaoStore.number(key, v);
                    } catch (Exception ignored) {
                        Toast.makeText(this, "数字无效，已忽略", Toast.LENGTH_SHORT).show();
                    }
                    view.invalidate();
                })
                .show();
    }

    private static String hintFor(Kind kind, double value) {
        switch (kind) {
            case PCT4:
                return String.format(Locale.US, "%.4f", value);
            case PCT2:
                return String.format(Locale.US, "%.2f", value);
            case MONEY:
            default:
                return String.format(Locale.US, "%.2f", value);
        }
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
                    YuebaoStore.clearText(key);
                    view.invalidate();
                })
                .setPositiveButton("保存", (d, w) -> {
                    String v = input.getText().toString().trim();
                    if (v.isEmpty()) {
                        state.texts.remove(key);
                        YuebaoStore.clearText(key);
                    } else {
                        state.texts.put(key, v);
                        YuebaoStore.text(key, v);
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
            super(YuebaoActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / YuebaoScreen.W, getHeight() / YuebaoScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * YuebaoScreen.W / Math.max(1, getWidth());
            float y = event.getY() * YuebaoScreen.H / Math.max(1, getHeight());
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
