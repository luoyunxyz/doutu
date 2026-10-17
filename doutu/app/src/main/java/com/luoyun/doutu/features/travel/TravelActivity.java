package com.luoyun.doutu.features.travel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.luoyun.doutu.features.travel.data.TravelStore;
import com.luoyun.doutu.features.travel.ui.TravelScreen;

/**
 * Hosts the 出行 page reached from the 出行 shortcut of the 小支主页.
 *
 * <p>The page draws itself, so this owns only the window — full bleed under the status and
 * navigation bars, the same canvas the rest of the module draws on — and what a tap means. The
 * page is a mock: every entry answers with a demo toast rather than opening anything.
 */
public final class TravelActivity extends Activity {
    private static final String EXTRA_NONCE = "travel_nonce";

    private final TravelScreen screen = new TravelScreen();
    private final TravelScreen.State state = new TravelScreen.State();
    private View view;

    public static Intent intentFor(Context context) {
        Intent intent = new Intent(context, TravelActivity.class);
        intent.putExtra(EXTRA_NONCE, System.nanoTime());
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        screen.load(getResources());
        // 用户上一次改过的出发地 / 出发城市 / 到达城市先铺回来（这几个值以前只活在内存里，
        // 从最近任务里划掉再打开就没了）
        TravelStore.apply(state);
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
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        if (token.equals("back")) {
            finish();
            return;
        }
        if (token.equals("nav:0")) {
            finish();
            return;
        }
        if (token.startsWith("entry:")) {
            int index = Integer.parseInt(token.substring(6));
            if (index == 1) toast("机票 · 就是当前页");
            return;
        }
        if (token.startsWith("tab:")) {
            return;
        }
        if (token.startsWith("city:")) {
            editCity(Integer.parseInt(token.substring(5)));
            return;
        }
        if (token.startsWith("grid:")) {
            int index = Integer.parseInt(token.substring(5));
            return;
        }
        if (token.startsWith("foot:")) {
            int index = Integer.parseInt(token.substring(5));
            return;
        }
        if (token.startsWith("nav:")) {
            int index = Integer.parseInt(token.substring(4));
            return;
        }
        switch (token) {
            case "address":
                ask("编辑出发地", state.address, value -> {
                    state.address = value;
                    TravelStore.save(state);
                    view.invalidate();
                });
                break;
            case "search":
                break;
            case "mileage":
                break;
            case "swap":
                toast("北京 ⇄ 杭州 · 已对调");
                break;
            case "cabin":
                break;
            case "check:0":
            case "check:1":
                break;
            case "coupon":
                break;
            case "couponLink":
                break;
            case "query":
                break;
            default:
                break;
        }
    }

    private static final String[] GRID_LABELS = {"打车", "12306", "骑行", "酒店预订", "出行优惠",
            "顺风车", "汽车票", "实时公交", "出行规划", "全部服务"};
    private static final String[] FOOT_LABELS = {"我的订单", "乘机服务", "低价提醒", "更多服务"};
    private static final String[] NAV_LABELS = {"首页", "车生活", "AI出行助手", "出去玩", "我的"};

    /** The dialog behind the page's header address and its two city fields. */
    private void ask(String title, String current, java.util.function.Consumer<String> save) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setText(current);
        field.setSelection(field.getText().length());
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        column.setPadding(pad, pad / 2, pad, 0);
        column.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(column)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> save.accept(field.getText().toString().trim()))
                .show();
    }

    /** The departure and the destination: the left field and the right one on the route row. */
    private void editCity(int index) {
        boolean from = index == 0;
        ask(from ? "编辑出发城市" : "编辑到达城市", from ? state.from : state.to, value -> {
            if (value.isEmpty()) return;
            if (from) {
                state.from = value;
            } else {
                state.to = value;
            }
            TravelStore.save(state);
            view.invalidate();
        });
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private final class PageView extends View {
        PageView() {
            super(TravelActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / TravelScreen.W, getHeight() / TravelScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * TravelScreen.W / Math.max(1, getWidth());
            float y = event.getY() * TravelScreen.H / Math.max(1, getHeight());
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                act(screen.hitTest(state, x, y));
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
