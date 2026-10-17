package com.luoyun.doutu.features.zhi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.luoyun.doutu.features.zhi.data.AdvisorStore;
import com.luoyun.doutu.features.zhi.data.GrowthStore;
import com.luoyun.doutu.features.zhi.data.LicaiVersion;
import com.luoyun.doutu.features.zhi.ui.GrowthScreen;
import com.luoyun.doutu.features.zhi.ui.LicaiScreen;

/**
 * Hosts the interactive 理财 tab of the 小支主页, reached by tapping 理财 in that page's bottom
 * navigation. Owns vertical scrolling, the eye that hides the money figures and every edit dialog
 * behind a tappable figure.
 *
 * <p>The 我的保障资产 / 我的额度 sheet between the 资产 header and the 福利中心 bar unfolds when the
 * caret under the two figures is tapped, and by dragging: while the page sits at its top a pull
 * down opens it from anywhere on the page, a pull up folds it back and a fast flick settles it to
 * whichever end it was thrown at.
 *
 * <p>Tapping the header above 福利中心 — anywhere that is not a figure, the eye or the caret — raises
 * the version sheet, the 财富黑卡 等级选择器: 普通版 / 成长版 / V1 / V2 / V3, each with the 获得方式 the
 * original sets beneath it, the one in use marked. Picking 普通版 draws the blue 财富 page; the other
 * four swap in the 财富黑卡 page {@code GrowthScreen} owns, whose card badge wears the picked tier.
 * The pick is kept in {@link LicaiVersion} (每个账号一套 preferences), so it is still there after the
 * app is closed and handed over the moment 换账号 passes to another account.
 *
 * <p>The page is a local mock: every tap is answered inside the app, nothing is requested from the
 * network and no account, balance or holding exists.
 */
public final class LicaiActivity extends Activity {
    private static final String EXTRA_NONCE = "licai_nonce";

    private final LicaiScreen screen = new LicaiScreen();
    private final GrowthScreen growth = new GrowthScreen();
    private final LicaiScreen.State state = new LicaiScreen.State();
    private PageView view;

    /** Design-space travel a finger must cover before a press turns into a drag. */
    private static final float TOUCH_SLOP = 8f;

    private float downY;
    private float lastY;
    private boolean moved;
    /** The drag under way steers the unfolding panel rather than the page scroll. */
    private boolean panelDrag;
    /** The drag under way scrolls the page; it can still hand over to the panel at the top. */
    private boolean scrollDrag;
    private VelocityTracker tracker;
    private boolean lightStatusBar = true;
    private ValueAnimator panelAnimator;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, LicaiActivity.class);
        intent.putExtra(EXTRA_NONCE, System.nanoTime());
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        screen.load(getResources());
        growth.load(getResources());
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

    private void applyStatusBarTint() {
        // the 成长版's sheet is dark for longer than the 普通版's header is
        float dark = growth() ? GrowthScreen.HEADER_BOTTOM - 120f : 420f;
        boolean white = state.scroll < dark;
        if (white == lightStatusBar) return;
        lightStatusBar = white;
        configureWindow(getWindow(), white);
    }

    /** Clamps the panel to 0..1 and keeps the scroll offset inside the range it now allows. */
    private void setExpand(float value) {
        float next = Math.max(0f, Math.min(1f, value));
        if (next == state.expand) return;
        state.expand = next;
        state.scroll = Math.min(state.scroll, maxScroll());
        view.invalidate();
    }

    /** Whether the 成长版, which has no panel under its header, is the page showing. */
    private boolean growth() {
        return LicaiVersion.blackCard(LicaiVersion.current());
    }

    /** How far a pull has to travel to open the sheet the version showing carries. */
    private float panelH() {
        return growth() ? GrowthScreen.PANEL_H : LicaiScreen.PANEL_H;
    }

    private float maxScroll() {
        return growth() ? growth.maxScroll(state) : screen.maxScroll(state);
    }

    private String hitTestAt(float x, float y) {
        return growth() ? growth.hitTest(state, screen, x, y) : screen.hitTest(state, x, y);
    }

    /**
     * Settles which of the two drags the gesture under way is steering, on its first significant
     * move. The sheet owns a pull-down while the page still sits at its top — wherever on the page
     * the finger started, the 资产 header and the product grid below the 福利中心 bar alike — and it
     * owns a pull-up while it is still unfolded. Everything else scrolls the page.
     */
    private void claimGesture(float dy) {
        if (state.scroll > 0f) {
            scrollDrag = true;
        } else if (dy > 0f || state.expand > 0f) {
            panelDrag = true;
        } else {
            scrollDrag = true;
        }
        if (panelDrag && panelAnimator != null) panelAnimator.cancel();
    }

    /**
     * Scrolls the page and hands the gesture over to the sheet as soon as the top is overshot, so
     * a pull that runs off the start of the list keeps unfolding instead of stopping dead.
     */
    private void dragScroll(float delta) {
        float want = state.scroll + delta;
        if (want < 0f) {
            panelDrag = true;
            scrollDrag = false;
            state.scroll = 0f;
            setExpand(state.expand - want / panelH());
            view.invalidate();
            return;
        }
        float next = Math.min(want, maxScroll());
        if (next != state.scroll) {
            state.scroll = next;
            view.invalidate();
        }
    }

    /** Slides the panel to 0 (folded) or 1 (unfolded), easing out so it lands like the real one. */
    private void animatePanel(float target) {
        if (panelAnimator != null) panelAnimator.cancel();
        float from = state.expand;
        if (Math.abs(target - from) < 0.001f) return;
        panelAnimator = ValueAnimator.ofFloat(from, target);
        panelAnimator.setDuration((long) Math.max(140f, 300f * Math.abs(target - from)));
        panelAnimator.setInterpolator(new DecelerateInterpolator());
        panelAnimator.addUpdateListener(animation -> {
            state.expand = (Float) animation.getAnimatedValue();
            view.invalidate();
        });
        panelAnimator.start();
    }

    @Override
    public void onBackPressed() {
        if (state.scroll > 0f || state.expand > 0f) {
            state.scroll = 0f;
            state.expand = 0f;
            view.invalidate();
            applyStatusBarTint();
            return;
        }
        super.onBackPressed();
    }

    /** Leaving the tab is instant — the 支 home page is the previous screen, so no slide. */
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }

    /** The album's answer to the avatar sheet, if that is what the user was picking for. */
    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        AvatarChooser.deliver(this, request, result, data);
    }

    // ------------------------------------------------------------------ editing

    /** Opens the text dialog behind one of the tappable figures. */
    private void edit(String key) {
        int index = indexOf(key);
        if (index < 0) return;
        ask(LicaiScreen.State.TITLES[index], state.value(key), text -> {
            state.value(key, text);
            view.invalidate();
        });
    }

    /** The same dialog, behind one of the 成长版's figures. */
    private void editGrowth(String key) {
        ask(GrowthStore.titleOf(key), GrowthStore.value(key), text -> {
            GrowthStore.value(key, text);
            view.invalidate();
        });
    }

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
                .setPositiveButton("保存", (d, w) -> {
                    save.accept(field.getText().toString());
                })
                .show();
    }

    private static int indexOf(String key) {
        return indexOf(LicaiScreen.State.KEYS, key);
    }

    private static int indexOf(String[] keys, String key) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(key)) return i;
        }
        return -1;
    }

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        if (token.equals("redraw")) {
            view.invalidate();
            return;
        }
        if (token.startsWith("toast:")) {
            toast(token.substring(6));
            return;
        }
        if (token.startsWith("edit:")) {
            edit(token.substring(5));
            return;
        }
        if (token.startsWith("gedit:")) {
            editGrowth(token.substring(6));
            return;
        }
        if (token.equals("version")) {
            showVersionSheet();
            return;
        }
        if (token.equals("advisorAvatar")) {
            // the card's picture is the user's to change, the same way 个人信息 changes its own
            AvatarChooser.show(this, "更换理财师头像", picture -> {
                AdvisorStore.avatar(picture);
                view.invalidate();
            });
            return;
        }
        if (token.equals("advisorName")) {
            ask("编辑理财师姓名", AdvisorStore.name(), text -> {
                AdvisorStore.name(text);
                view.invalidate();
            });
            return;
        }
        if (token.equals("toggleHide")) {
            if (growth()) {
                GrowthStore.hidden = !GrowthStore.hidden;
            } else {
                state.hidden = !state.hidden;
            }
            view.invalidate();
            return;
        }
        if (token.equals("toggleExpand")) {
            animatePanel(state.expand > 0.5f ? 0f : 1f);
            return;
        }
        if (token.startsWith("nav:")) {
            int index = Integer.parseInt(token.substring(4));
            if (index == 0) {
                // 首页 is the 小支主页 itself, not whatever happens to sit under this tab: entering
                // the tab straight from the 支 module grid would otherwise drop the user back on the
                // app's own main page instead of the 小支主页 the bar's other entries lead to.
                startActivity(ZhiHomeActivity.intentFor(this));
                overridePendingTransition(0, 0);
            } else if (index == 1) {
                state.scroll = 0f;
                view.invalidate();
                applyStatusBarTint();
            } else if (index == 3) {
                startActivity(MessageActivity.intentFor(this));
                overridePendingTransition(0, 0);
            } else if (index == 4) {
                startActivity(MeActivity.intentFor(this));
                overridePendingTransition(0, 0);
            } else {
            }
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ versions

    /**
     * The sheet the header answers with: it rises from the bottom edge with the five tiers the tab
     * can wear, each carrying the 获得方式 the original writes under it, the one in use marked. Every
     * tier has a page of its own — 普通版 the blue 财富 page, 成长版 / V1 / V2 / V3 the 财富黑卡 page —
     * so a pick is applied straight away and remembered.
     */
    private void showVersionSheet() {
        float density = getResources().getDisplayMetrics().density;
        int dp = Math.round(density);
        int pad = Math.round(24 * density);

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(pad, Math.round(12 * density), pad, Math.round(26 * density));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xffffffff);
        background.setCornerRadii(new float[]{
                22 * density, 22 * density, 22 * density, 22 * density, 0, 0, 0, 0});
        sheet.setBackground(background);

        View handle = new View(this);
        GradientDrawable handleShape = new GradientDrawable();
        handleShape.setColor(0xffe2e2e6);
        handleShape.setCornerRadius(3 * density);
        handle.setBackground(handleShape);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
                Math.round(58 * density), Math.round(5 * density));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = Math.round(16 * density);
        sheet.addView(handle, handleLp);

        TextView title = new TextView(this);
        title.setText("修改财富黑卡等级");
        title.setTextSize(16f);
        title.setTextColor(0xff23252b);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        sheet.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("当前等级：" + LicaiVersion.name(LicaiVersion.current()));
        subtitle.setTextSize(13f);
        subtitle.setTextColor(0xff939292);
        LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = Math.round(6 * density);
        sheet.addView(subtitle, subtitleLp);

        for (int i = 0; i < LicaiVersion.NAMES.length; i++) {
            final int version = i;
            boolean current = LicaiVersion.current() == i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Math.round(13 * density), 0, Math.round(13 * density));

            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);

            TextView label = new TextView(this);
            label.setText(LicaiVersion.NAMES[i]);
            label.setTextSize(16.5f);
            label.setTextColor(current ? 0xff1478fc : 0xff23252b);
            label.setTypeface(current ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            column.addView(label);

            TextView tip = new TextView(this);
            tip.setText(LicaiVersion.TIPS[i]);
            tip.setTextSize(12.5f);
            tip.setTextColor(0xff939292);
            LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tipLp.topMargin = Math.round(3 * density);
            column.addView(tip, tipLp);

            row.addView(column, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (current) {
                View dot = new View(this);
                GradientDrawable dotShape = new GradientDrawable();
                dotShape.setColor(0xff1478fc);
                dotShape.setShape(GradientDrawable.OVAL);
                dot.setBackground(dotShape);
                row.addView(dot, new LinearLayout.LayoutParams(
                        Math.round(14 * density), Math.round(14 * density)));
            }
            row.setOnClickListener(v -> {
                dialog.dismiss();
                pickVersion(version);
            });
            sheet.addView(row);
        }

        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();

        // the sheet rises from the bottom edge rather than fading in
        sheet.post(() -> {
            sheet.setTranslationY(sheet.getHeight());
            sheet.animate().translationY(0f).setDuration(240)
                    .setInterpolator(new DecelerateInterpolator()).start();
        });
    }

    private void pickVersion(int version) {
        if (version == LicaiVersion.current()) return;
        if (!LicaiVersion.drawn(version)) return;
        LicaiVersion.current(version);
        growth.resetTab();
        state.scroll = 0f;
        state.expand = 0f;
        view.invalidate();
        applyStatusBarTint();
    }

    private final class PageView extends View {
        PageView() {
            super(LicaiActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / LicaiScreen.W, getHeight() / LicaiScreen.H);
            if (growth()) {
                growth.draw(canvas, state, screen);
                growth.drawNav(canvas, screen);
            } else {
                screen.draw(canvas, state);
            }
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * LicaiScreen.W / Math.max(1, getWidth());
            float y = event.getY() * LicaiScreen.H / Math.max(1, getHeight());
            if (tracker == null) tracker = VelocityTracker.obtain();
            tracker.addMovement(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = y;
                    lastY = y;
                    moved = false;
                    panelDrag = false;
                    scrollDrag = false;
                    if (panelAnimator != null) panelAnimator.cancel();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!moved && Math.abs(y - downY) > TOUCH_SLOP) {
                        moved = true;
                        // a press that started on the bar only ever suppresses its own tap
                        if (downY < LicaiScreen.NAV_TOP) claimGesture(y - downY);
                    }
                    if (moved) {
                        if (panelDrag) {
                            setExpand(state.expand + (y - lastY) / LicaiScreen.PANEL_H);
                        } else if (scrollDrag) {
                            dragScroll(lastY - y);
                        }
                    }
                    lastY = y;
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    tracker.computeCurrentVelocity(1000);
                    float vy = tracker.getYVelocity();
                    if (panelDrag) {
                        // A flick settles towards the end it was thrown at; a slow drag towards
                        // whichever end it finished closest to.
                        boolean flicked = Math.abs(vy) > 600f;
                        float target = flicked ? (vy > 0f ? 1f : 0f) : (state.expand > 0.5f ? 1f : 0f);
                        animatePanel(target);
                    } else if (!moved) {
                        act(hitTestAt(x, y));
                    }
                    tracker.recycle();
                    tracker = null;
                    panelDrag = false;
                    scrollDrag = false;
                    applyStatusBarTint();
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
