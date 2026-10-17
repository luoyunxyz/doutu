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
import android.widget.TextView;
import android.widget.Toast;

import com.luoyun.doutu.features.zhi.ui.AssetScreen;
import com.luoyun.doutu.features.zhi.data.AssetStore;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 总资产页（小支主页 - 我的 - 总资产）。
 *
 * <p>整页由 {@link AssetScreen} 用 Canvas 画出来，这里负责滚动、点击和"点数字改数字"的弹窗。
 * 页面上所有出现的数字都登记成了一个热区，点一下就能在对话框里改成任意值，改完立即重画。
 */
public final class AssetActivity extends Activity {
    private static final float TOUCH_SLOP = 8f;

    /** 数字的格式，决定弹窗输入框和小数位。 */
    private enum Kind { MONEY, INT, PCT, PCT_SIGNED, ONE_DEC }

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

    static {
        FIELDS.put("a.my", new Field("我的资产", 0, Kind.MONEY));
        FIELDS.put("a.profit", new Field("昨日收益", 0, Kind.MONEY));
        FIELDS.put("a.family", new Field("家庭保障份数", 2, Kind.INT));
        FIELDS.put("a.huo1", new Field("活期资产", 0, Kind.MONEY));
        FIELDS.put("a.huo2", new Field("活期资产变动", 0, Kind.MONEY));
        FIELDS.put("a.wen1", new Field("稳健理财", 0, Kind.MONEY));
        FIELDS.put("a.wen2", new Field("稳健理财变动", 0, Kind.MONEY));
        FIELDS.put("a.jin", new Field("进阶理财折扣", 1, Kind.INT));
        FIELDS.put("b.yue", new Field("余额", 0, Kind.MONEY));
        FIELDS.put("b.fund", new Field("基金起投金额", 10, Kind.INT));
        FIELDS.put("b.gold", new Field("黄金买入费率", 0, Kind.INT));
        FIELDS.put("b.leiji", new Field("累计收益", -24.72, Kind.MONEY));
        FIELDS.put("b.shouyi", new Field("收益率", -0.39, Kind.PCT));
        FIELDS.put("c.count", new Field("免费保单份数", 2, Kind.INT));
        FIELDS.put("c.cur", new Field("当前保额", 1.00, Kind.MONEY));
        FIELDS.put("c.max", new Field("可提保额", 30000, Kind.INT));
        FIELDS.put("d.avail", new Field("我的可用额度", 2710.98, Kind.MONEY));
        FIELDS.put("d.free", new Field("免费额度起", 2000, Kind.INT));
        FIELDS.put("d.hbAvail", new Field("花呗可用额度", 2710.98, Kind.MONEY));
        FIELDS.put("d.hbTotal", new Field("花呗总计额度", 3000.00, Kind.MONEY));
        FIELDS.put("e.users", new Field("已测用户数（万）", 6000, Kind.INT));
        FIELDS.put("f.r1", new Field("收益率 1", 34.06, Kind.PCT_SIGNED));
        FIELDS.put("f.r2", new Field("收益率 2", 42.11, Kind.PCT_SIGNED));
        FIELDS.put("f.r3", new Field("收益率 3", 46.26, Kind.PCT_SIGNED));
        FIELDS.put("g.people", new Field("正在体验人数（亿）", 1.4, Kind.ONE_DEC));
    }

    private final AssetScreen screen = new AssetScreen();
    private final AssetScreen.State state = new AssetScreen.State();
    private PageView view;

    private float downY;
    private float lastY;
    private boolean moved;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, AssetActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        screen.load(getResources(), getAssets());
        // 上一次改过的数字和文字从盘上回来，所以这一页一打开就是用户自己的值，不是参考图的默认值
        state.edits.putAll(AssetStore.numbers());
        state.texts.putAll(AssetStore.texts());
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

    @Override
    public void onBackPressed() {
        if (state.scroll > 0f) {
            scrollPageTo(0f);
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
        if (token.equals("eye")) {
            state.hideAll = !state.hideAll;
            view.invalidate();
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

    private void showNumberEditor(final String key) {
        final Field field = FIELDS.get(key);
        if (field == null) return;
        double cur = state.edits.containsKey(key) ? state.edits.get(key) : field.def;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(getResources().getDisplayMetrics().density * 20);
        box.setPadding(pad, pad / 2, pad, 0);

        final EditText input = new EditText(this);
        int flags = InputType.TYPE_CLASS_NUMBER;
        if (field.kind == Kind.MONEY || field.kind == Kind.PCT
                || field.kind == Kind.PCT_SIGNED || field.kind == Kind.ONE_DEC) {
            flags |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
        }
        if (field.kind == Kind.MONEY || field.kind == Kind.PCT || field.kind == Kind.PCT_SIGNED) {
            flags |= InputType.TYPE_NUMBER_FLAG_SIGNED;
        }
        input.setInputType(flags);
        input.setText(hintFor(field.kind, cur));
        input.selectAll();
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("修改「" + field.label + "」")
                .setView(box)
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (d, w) -> {
                    state.edits.remove(key);
                    AssetStore.clearNumber(key);
                    view.invalidate();
                })
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        double v = Double.parseDouble(input.getText().toString().trim());
                        state.edits.put(key, v);
                        AssetStore.number(key, v);
                    } catch (Exception ignored) {
                        Toast.makeText(this, "数字无效，已忽略", Toast.LENGTH_SHORT).show();
                    }
                    view.invalidate();
                })
                .show();
    }

    private static String hintFor(Kind kind, double value) {
        switch (kind) {
            case MONEY:
                return String.format(Locale.US, "%.2f", value);
            case PCT:
            case PCT_SIGNED:
                return String.format(Locale.US, "%.2f", value);
            case ONE_DEC:
                return value == Math.rint(value) ? String.valueOf((long) value)
                        : String.valueOf(value);
            case INT:
            default:
                return String.valueOf((long) value);
        }
    }

    private void showTextEditor(final String key) {
        final String def = key.equals("announce") ? "沪指突破3800！关注什么" : "";
        if (def.isEmpty()) return;
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(state.texts.containsKey(key) ? state.texts.get(key) : def);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("修改公告文字")
                .setView(input)
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (d, w) -> {
                    state.texts.remove(key);
                    AssetStore.clearText(key);
                    view.invalidate();
                })
                .setPositiveButton("保存", (d, w) -> {
                    String v = input.getText().toString().trim();
                    if (v.isEmpty()) {
                        state.texts.remove(key);
                        AssetStore.clearText(key);
                    } else {
                        state.texts.put(key, v);
                        AssetStore.text(key, v);
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
            super(AssetActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / AssetScreen.W, getHeight() / AssetScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * AssetScreen.W / Math.max(1, getWidth());
            float y = event.getY() * AssetScreen.H / Math.max(1, getHeight());
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
