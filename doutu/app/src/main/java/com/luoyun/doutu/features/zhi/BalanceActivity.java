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

import com.luoyun.doutu.features.zhi.data.BalanceStore;
import com.luoyun.doutu.features.zhi.ui.BalanceScreen;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 余额页（小支主页 - 我的 - 余额）。
 *
 * <p>整页由 {@link BalanceScreen} 用 Canvas 画出来，这里负责滚动、点击，和「点数字/点文字改内容」
 * 的弹窗。页面上出现的每一个金额都登记成了一个热区，点一下就能在对话框里改成任意值，改完立即重画；
 * 改过的值写进 {@link BalanceStore}，关掉 App 再打开还在。页面是本地 mock：所有点击都在 App 内
 * 应答，不联网，也不存在任何真实账户或余额。
 */
public final class BalanceActivity extends Activity {
    private static final float TOUCH_SLOP = 8f;

    /** 数字的格式，决定弹窗输入框与小数位。 */
    private enum Kind { MONEY }

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

    /** 点得动的数字：可用余额、余额变动金额、昨日收益、总金额、花呗可用额度。 */
    private static final Map<String, Field> FIELDS = new LinkedHashMap<>();
    /** 点得动的文字：变动说明、以及青春广场几张卡片上的标题/说明。 */
    private static final Map<String, String> TEXTS = new LinkedHashMap<>();

    static {
        FIELDS.put("bal.avail", new Field("可用余额", 0, Kind.MONEY));
        FIELDS.put("bal.change", new Field("余额变动金额", -0.01, Kind.MONEY));
        FIELDS.put("bal.yuebaoProfit", new Field("余额宝昨日收益", 0, Kind.MONEY));
        FIELDS.put("bal.yuebaoTotal", new Field("余额宝总金额", 0, Kind.MONEY));
        FIELDS.put("bal.huabei", new Field("花呗可用额度", 2710.98, Kind.MONEY));

        TEXTS.put("bal.changeNote", "余额宝-自动转入");
        TEXTS.put("p.l1title", "轻松3步走 零花钱到手");
        TEXTS.put("p.l2t", "开通我的专属零花钱\n续费快乐假期");
        TEXTS.put("p.l3t", "开通小荷包无痛攒钱");
        TEXTS.put("p.r1t", "零花钱轻松到手\n表情包帮我开口");
        TEXTS.put("p.r2t", "奶龙花语秘境皮肤套装，\n有效期180天");
        TEXTS.put("p.r3t", "聚会分账的3个潜规则，\n99%的人都不知道（附…");
    }

    private final BalanceScreen screen = new BalanceScreen();
    private final BalanceScreen.State state =
            new BalanceScreen.State(BalanceStore.numbers(), BalanceStore.texts());
    private PageView view;

    private float downY;
    private float lastY;
    private boolean moved;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, BalanceActivity.class);
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
        if (token.startsWith("toast:")) {
            Toast.makeText(this, token.substring(6), Toast.LENGTH_SHORT).show();
            return;
        }
        if (token.startsWith("num:")) {
            showNumberEditor(token.substring(4));
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
        if (token.equals("withdraw")) {
            // 余额页的「提现」按钮进的就是 支 宫格「余额提现」那一页
            startActivity(WithdrawActivity.intentFor(this));
            overridePendingTransition(0, 0);
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
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(String.format(Locale.US, "%.2f", cur));
        input.selectAll();

        new AlertDialog.Builder(this)
                .setTitle("修改「" + field.label + "」")
                .setView(column(this, input))
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (d, w) -> {
                    state.nums.remove(key);
                    BalanceStore.clearNumber(key);
                    view.invalidate();
                })
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        double v = Double.parseDouble(input.getText().toString().trim());
                        state.nums.put(key, v);
                        BalanceStore.number(key, v);
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
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(state.texts.containsKey(key) ? state.texts.get(key) : def);
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("修改文字（换行可分段）")
                .setView(column(this, input))
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (d, w) -> {
                    state.texts.remove(key);
                    BalanceStore.clearText(key);
                    view.invalidate();
                })
                .setPositiveButton("保存", (d, w) -> {
                    String v = input.getText().toString();
                    if (v.trim().isEmpty()) {
                        state.texts.remove(key);
                        BalanceStore.clearText(key);
                    } else {
                        state.texts.put(key, v);
                        BalanceStore.text(key, v);
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
            super(BalanceActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / BalanceScreen.W, getHeight() / BalanceScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * BalanceScreen.W / Math.max(1, getWidth());
            float y = event.getY() * BalanceScreen.H / Math.max(1, getHeight());
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
