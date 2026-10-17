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

import com.luoyun.doutu.features.zhi.data.Accounts;
import com.luoyun.doutu.features.zhi.ui.SwitchAccountScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * 切换账号页（「支」宫格里的「切换账号」）。
 *
 * <p>页面是 {@link SwitchAccountScreen} 照着参考图一块块贴出来的，这里管三件事：轻触某个账号的
 * 卡片就换到那个账号（换完回到它的首页，整条返回栈都换成新账号的），点「登录其他账号」就现开一个
 * 新账号并直接进它的首页，右上角「管理」可以改本账号的手机号、删掉别的账号。
 *
 * <p>每个账号的存档是分开的：换账号时 {@link Accounts#switchTo(int)} 会把各个仓库的内存缓存清
 * 掉，所以「总资产」「余额」「余额宝」「花呗」「个人信息」「支付消息」在哪个账号下就是哪个账号
 * 自己的数。
 *
 * <p>整页都是本地假数据：没有登录、没有密码、没有真实手机号，除了新账号取头像那一次请求，
 * 不会联网。
 */
public final class SwitchAccountActivity extends Activity {
    private static final String EXTRA_NONCE = "switch_account_nonce";
    private static final float TOUCH_SLOP = 8f;

    private final SwitchAccountScreen screen = new SwitchAccountScreen();
    private final SwitchAccountScreen.State state = new SwitchAccountScreen.State();
    private PageView view;

    private float downX;
    private float downY;
    private float lastY;
    private boolean moved;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, SwitchAccountActivity.class);
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
        // 当前账号在「个人信息」里改过的名字 / 号码 / 头像先搬回来，页面上看到的就是最新的
        Accounts.syncCurrent();
        // 还没抽到头像的账号（默认那个除外）在这里补一张
        Accounts.ensureAvatars();
        // 新账号的头像回来之后再重画一次
        Accounts.watch(() -> view.invalidate());
        view.invalidate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Accounts.watch(null);
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) configureWindow(getWindow());
    }

    /** 顶上那条是浅灰的，状态栏图标保持深色。 */
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
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
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

    // ------------------------------------------------------------------ 交互

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        if (token.startsWith("toast:")) {
            Toast.makeText(this, token.substring(6), Toast.LENGTH_SHORT).show();
            return;
        }
        switch (token) {
            case "back":
                finish();
                return;
            case "manage":
                showManage();
                return;
            case "new":
                // 现开一个账号：名字、号码当场随机，头像问随机头像接口要
                Accounts.Account created = Accounts.create();
                Accounts.switchTo(created.id);
                Accounts.applyProfile();
                // 先在切换账号页上把这新的一张卡片画出来（预览），头像到了再进它的首页
                view.invalidate();
                Accounts.fetchAvatar(created, this::goHome);
                return;
            case "footer":
                return;
            default:
                break;
        }
        if (token.startsWith("switch:")) {
            int id = Integer.parseInt(token.substring(7));
            if (Accounts.isCurrent(id)) return;
            Accounts.switchTo(id);
            goHome();
        }
    }

    /** 换完账号回它的首页：整条返回栈一起换成新账号的，别让上一个账号的页面留在后面。 */
    private void goHome() {
        Intent intent = ZhiHomeActivity.intentFor(this);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void scrollPageTo(float target) {
        state.scroll = Math.max(0f, Math.min(screen.maxScroll(state), target));
        view.invalidate();
    }

    // ------------------------------------------------------------------ 管理

    private void showManage() {
        // 默认那个账号（原来那个）和当前账号都不删，所以列表里只放能删的
        final List<Accounts.Account> others = new ArrayList<>();
        for (Accounts.Account account : Accounts.all()) {
            if (account.id != Accounts.DEFAULT_ID && !Accounts.isCurrent(account.id)) {
                others.add(account);
            }
        }
        String[] items = others.isEmpty()
                ? new String[]{"修改手机号"}
                : new String[]{"修改手机号", "删除其他账号"};
        new AlertDialog.Builder(this)
                .setTitle("账号管理")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) editPhone(Accounts.currentId());
                    else pickDelete(others);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void editPhone(final int id) {
        Accounts.Account account = Accounts.account(id);
        if (account == null) return;
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setText(account.phone);
        input.selectAll();

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(getResources().getDisplayMetrics().density * 20);
        column.setPadding(pad, pad / 2, pad, 0);
        column.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("修改手机号")
                .setView(column)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        input.setError("内容不能为空");
                        return;
                    }
                    Accounts.rename(id, value);
                    view.invalidate();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void pickDelete(final List<Accounts.Account> others) {
        final String[] labels = new String[others.size()];
        for (int i = 0; i < others.size(); i++) {
            Accounts.Account account = others.get(i);
            labels[i] = account.name + "  " + account.phone;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除哪个账号？")
                .setItems(labels, (dialog, which) -> confirmDelete(others.get(which)))
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete(final Accounts.Account account) {
        new AlertDialog.Builder(this)
                .setTitle(account.phone)
                .setMessage("会把那个账号连同它自己的数据一起删掉，删了不能恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    Accounts.remove(account.id);
                    view.invalidate();
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ------------------------------------------------------------------ 画布

    private final class PageView extends View {
        PageView() {
            super(SwitchAccountActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / SwitchAccountScreen.W, getHeight() / SwitchAccountScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * SwitchAccountScreen.W / Math.max(1, getWidth());
            float y = event.getY() * SwitchAccountScreen.H / Math.max(1, getHeight());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = x;
                    downY = y;
                    lastY = y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!moved && (Math.abs(y - downY) > TOUCH_SLOP
                            || Math.abs(x - downX) > TOUCH_SLOP)) moved = true;
                    if (moved) scrollPageTo(state.scroll + (lastY - y));
                    lastY = y;
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) act(screen.hitTest(state, x, y));
                    return true;
                case MotionEvent.ACTION_CANCEL:
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
