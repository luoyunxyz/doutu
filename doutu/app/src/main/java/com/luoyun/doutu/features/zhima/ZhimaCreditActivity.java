package com.luoyun.doutu.features.zhima;

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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.luoyun.doutu.features.zhima.data.ZhimaStore;
import com.luoyun.doutu.features.zhima.ui.ZhimaCreditScreen;
import com.luoyun.doutu.features.zhi.ZhiHomeActivity;

import java.util.List;

/**
 * Hosts the interactive “芝麻信用” page opened from the 芝麻信用 card on the 小支主页.
 *
 * <p>The top carousel holds the personal and the enterprise credit card; swiping it left and
 * right switches between them, and 查信用分 on the compact enterprise card expands it to the
 * detailed card of the second reference screenshot. Every value on either card can be edited by
 * tapping it. The feed underneath is a two column board of cut-out modules: long press a module
 * to replace or delete it, long press empty space to add another one.
 */
public final class ZhimaCreditActivity extends Activity {
    private static final String EXTRA_NONCE = "zhima_nonce";
    private static final long LONG_PRESS_MS = 450L;

    private final ZhimaCreditScreen screen = new ZhimaCreditScreen();
    private final ZhimaCreditScreen.State state = new ZhimaCreditScreen.State();
    private PageView view;

    private long downTime;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private boolean moved;
    private boolean draggingCarousel;
    private boolean lightStatusBar = true;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, ZhimaCreditActivity.class);
        intent.putExtra(EXTRA_NONCE, System.nanoTime());
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        screen.load(getResources(), getAssets());
        // 用户上一次改过的文案与模块板先铺回来（这两样以前只活在内存里，
        // 从最近任务里划掉再打开就全回到参考图那一版了）
        ZhimaStore.apply(state);
        view = new PageView();
        setContentView(view);
        configureWindow(getWindow(), true);
        view.post(() -> configureWindow(getWindow(), lightStatusBar));
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) configureWindow(getWindow(), lightStatusBar);
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
                int dark = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                int mask = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                if (!whiteStatusIcons) dark |= WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                controller.setSystemBarsAppearance(dark, mask);
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
            state.scroll = 0f;
            view.invalidate();
            return;
        }
        backToZhiHome();
    }

    /**
     * Leaving 芝麻信用 always lands on the 小支主页, whether the page was opened from its
     * 芝麻信用 card or from the 芝麻信用 tile of the 支 module grid.
     */
    private void backToZhiHome() {
        startActivity(ZhiHomeActivity.intentFor(this));
        overridePendingTransition(0, 0);
        finish();
    }

    // ------------------------------------------------------------------ editing

    private void edit(String token) {
        switch (token) {
            case "p.label":
                ask("编辑信用状态", state.pLabel, "例如：芝麻信用良好",
                        value -> state.pLabel = value);
                break;
            case "p.score":
                ask("编辑芝麻分", state.pScore, "例如：636", value -> state.pScore = value);
                break;
            case "p.quota":
                ask("编辑可用额度", state.pQuota, "例如：2500元", value -> state.pQuota = value);
                break;
            case "p.discount":
                ask("编辑可享优惠", state.pDiscount, "例如：1157元", value -> state.pDiscount = value);
                break;
            case "p.privilege":
                ask("编辑可用特权", state.pPrivilege, "例如：1项", value -> state.pPrivilege = value);
                break;
            case "e.label":
                ask("编辑企业信用状态", state.eLabel, "例如：企业信用较弱",
                        value -> state.eLabel = value);
                break;
            case "e.score":
                ask("编辑企业信用分", state.eScore, "例如：1319", value -> state.eScore = value);
                break;
            case "e.company":
                ask("编辑企业名称", state.eCompany, "例如：乐畅云（上海）科技有限公司",
                        value -> state.eCompany = value);
                break;
            case "e.go":
                ask("编辑按钮文案", state.eGo, "例如：去涨分", value -> state.eGo = value);
                break;
            case "e.quota":
                ask("编辑信贷额度", state.eQuota, "例如：测一测额度", value -> state.eQuota = value);
                break;
            case "e.privilege":
                ask("编辑企业特权", state.ePrivilege, "例如：5项", value -> state.ePrivilege = value);
                break;
            default:
                break;
        }
    }

    private interface Sink {
        void accept(String value);
    }

    private void ask(String title, String current, String hint, Sink sink) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setHint(hint);
        field.setText(current);
        field.setSelection(current.length());
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    sink.accept(field.getText().toString().trim());
                    // 改一处就整份写回存档：从最近任务里划掉再打开还是这句
                    ZhimaStore.save(state);
                    view.invalidate();
                })
                .show();
    }

    // ------------------------------------------------------------------ feed board

    private void showModuleMenu(String token) {
        if (token.startsWith("add:")) {
            int col = Integer.parseInt(token.substring(4));
            pickModule("添加到" + columnName(col), key -> {
                ZhimaCreditScreen.Module m = ZhimaCreditScreen.module(key);
                if (m == null) return;
                state.column(col).add(new ZhimaCreditScreen.Slot(key, m.h));
                ZhimaStore.save(state);
                view.invalidate();
            });
            return;
        }
        String[] parts = token.split(":");
        int col = Integer.parseInt(parts[0]);
        int index = Integer.parseInt(parts[1]);
        if (index >= state.column(col).size()) return;
        ZhimaCreditScreen.Slot slot = state.column(col).get(index);
        if (slot.key == null) {
            pickModule("添加到空白位置", key -> {
                state.place(col, index, key);
                ZhimaStore.save(state);
                view.invalidate();
            });
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("模块操作")
                .setItems(new String[]{"替换模块", "删除模块"}, (d, w) -> {
                    if (w == 0) {
                        pickModule("替换模块", key -> {
                            state.place(col, index, key);
                            ZhimaStore.save(state);
                            view.invalidate();
                        });
                    } else {
                        // The slot keeps its height: the modules under it stay where they are,
                        // and the empty gap can be long-pressed to add a module back.
                        slot.key = null;
                        ZhimaStore.save(state);
                        view.invalidate();
                        Toast.makeText(this, "已删除，该位置留空，长按可再添加", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String columnName(int col) {
        return col == 0 ? "左列" : "右列";
    }

    private void pickModule(String title, Sink sink) {
        List<ZhimaCreditScreen.Module> modules = ZhimaCreditScreen.library();
        String[] labels = new String[modules.size()];
        for (int i = 0; i < modules.size(); i++) labels[i] = modules.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels, (d, w) -> sink.accept(modules.get(w).key))
                .setNegativeButton("取消", null)
                .show();
    }

    // ------------------------------------------------------------------ input

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        if (token.equals("back")) {
            if (state.scroll > 0f) {
                state.scroll = 0f;
                view.invalidate();
            } else {
                backToZhiHome();
            }
            return;
        }
        if (token.startsWith("edit:")) {
            edit(token.substring(5));
            return;
        }
        if (token.equals("expandEnt")) {
            state.enterpriseOpen = true;
            state.carousel = screen.maxCarousel(state);
            view.invalidate();
            return;
        }
        if (token.startsWith("nav:")) {
            int index = Integer.parseInt(token.substring(4));
            if (index == 0 && state.scroll > 0f) {
                state.scroll = 0f;
                view.invalidate();
            }
            return;
        }
        if (token.equals("ai")) {
            return;
        }
        if (token.startsWith("feed:")) {
            // The module itself only reacts to a long press; a tap is a no-op.
            Toast.makeText(this, "长按可替换或删除该模块", Toast.LENGTH_SHORT).show();
        }
        if (token.startsWith("empty:")) {
            Toast.makeText(this, "长按此处可添加模块", Toast.LENGTH_SHORT).show();
        }
    }

    private final class PageView extends View {
        PageView() {
            super(ZhimaCreditActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / ZhimaCreditScreen.W, getHeight() / ZhimaCreditScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * ZhimaCreditScreen.W / Math.max(1, getWidth());
            float y = event.getY() * ZhimaCreditScreen.H / Math.max(1, getHeight());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downTime = event.getEventTime();
                    downX = x;
                    downY = y;
                    lastX = x;
                    lastY = y;
                    moved = false;
                    draggingCarousel = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float cy = y + state.scroll;
                    boolean inCarousel = cy >= ZhimaCreditScreen.CAROUSEL_Y - 30
                            && cy <= ZhimaCreditScreen.CAROUSEL_Y + ZhimaCreditScreen.CARD_H + 30
                            && y > ZhimaCreditScreen.HEADER_H && y < ZhimaCreditScreen.NAV_TOP;
                    if (!draggingCarousel && inCarousel
                            && Math.abs(x - downX) > Math.abs(y - downY) * 1.2f) {
                        draggingCarousel = true;
                    }
                    if (Math.abs(x - downX) > 6 || Math.abs(y - downY) > 6) moved = true;
                    if (draggingCarousel) {
                        float max = screen.maxCarousel(state);
                        float next = Math.max(0f, Math.min(max, state.carousel - (x - lastX)));
                        if (next != state.carousel) {
                            state.carousel = next;
                            invalidate();
                        }
                    } else if (y > ZhimaCreditScreen.HEADER_H && y < ZhimaCreditScreen.NAV_TOP) {
                        float next = Math.max(0f,
                                Math.min(screen.maxScroll(state), state.scroll - (y - lastY)));
                        if (next != state.scroll) {
                            state.scroll = next;
                            invalidate();
                        }
                    }
                    lastX = x;
                    lastY = y;
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (draggingCarousel) {
                        snapCarousel(x - downX);
                    } else if (!moved) {
                        // A stationary press held past LONG_PRESS_MS opens the module menu.
                        if (event.getEventTime() - downTime >= LONG_PRESS_MS) {
                            String token = screen.longPress(state, downX, downY);
                            if (token != null) {
                                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                                showModuleMenu(token);
                            }
                        } else {
                            act(screen.hitTest(state, x, y));
                        }
                    }
                    return true;
                default:
                    return true;
            }
        }

        /** Snaps the carousel to the nearer of its two resting positions. */
        private void snapCarousel(float dragX) {
            float max = screen.maxCarousel(state);
            if (max <= 0f) {
                state.carousel = 0f;
            } else if (state.carousel > max / 2f) {
                state.carousel = max;
            } else {
                state.carousel = 0f;
            }
            invalidate();
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
