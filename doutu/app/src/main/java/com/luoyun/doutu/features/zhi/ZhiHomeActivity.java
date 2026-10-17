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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.luoyun.doutu.features.zhi.data.ZhiHomeStore;
import com.luoyun.doutu.features.zhi.ui.ZhiHomeScreen;
import com.luoyun.doutu.features.zhima.ZhimaCreditActivity;
import com.luoyun.doutu.features.pay.PayCodeActivity;
import com.luoyun.doutu.features.scan.ScanActivity;
import com.luoyun.doutu.features.travel.TravelActivity;

/**
 * Hosts the interactive “小支主页” (Alipay-style home) page reached from the 支 module grid.
 *
 * <p>The page is a local mock: every tap is answered inside the app, nothing is requested
 * from the network and no account or payment state exists.
 */
public final class ZhiHomeActivity extends Activity {
    private static final String EXTRA_NONCE = "zhi_nonce";
    private static final String[] NAV_LABELS = {"首页", "理财", "视频", "消息", "我的"};

    private final ZhiHomeScreen screen = new ZhiHomeScreen();
    private final ZhiHomeScreen.State state = new ZhiHomeScreen.State();
    private PageView view;

    private float downY;
    private float lastY;
    private boolean moved;
    private boolean lightStatusBar = true;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, ZhiHomeActivity.class);
        intent.putExtra(EXTRA_NONCE, System.nanoTime());
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        screen.load(getResources());
        // 用户上一次改过的文案先铺回来（地址 / 新消息 / 芝麻分 / 信用状态 / 最近消息）
        ZhiHomeStore.apply(state);
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
        // White system-bar icons only while the blue header still backs the status bar.
        boolean white = state.plusMenu || state.scroll < 520f;
        if (white == lightStatusBar) return;
        lightStatusBar = white;
        configureWindow(getWindow(), white);
    }

    @Override
    public void onBackPressed() {
        if (state.plusMenu) {
            state.plusMenu = false;
            view.invalidate();
            return;
        }
        if (state.scroll > 0f) {
            state.scroll = 0;
            view.invalidate();
            applyStatusBarTint();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------------ editing

    private EditText field(String value, String hint) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        edit.setHint(hint);
        edit.setText(value);
        edit.setSelection(value.length());
        return edit;
    }

    private LinearLayout column(EditText... fields) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        for (EditText f : fields) box.addView(f, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private void editAddress() {
        EditText address = field(state.address, "请输入地址");
        new AlertDialog.Builder(this)
                .setTitle("编辑地址")
                .setView(column(address))
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String value = address.getText().toString().trim();
                    state.address = value.isEmpty() ? "恩施土家族苗族自治州" : value;
                    ZhiHomeStore.save(state);
                    view.invalidate();
                })
                .show();
    }

    private void editNewMessage() {
        EditText label = field(state.newMessage, "例如：1条新消息");
        CheckBox unread = new CheckBox(this);
        unread.setText("未读");
        unread.setChecked(state.unread);
        LinearLayout box = column(label);
        box.addView(unread, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("编辑新消息提示")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String value = label.getText().toString().trim();
                    // Leave it blank to show nothing at all next to 最近消息.
                    state.newMessage = value;
                    state.unread = unread.isChecked();
                    ZhiHomeStore.save(state);
                    view.invalidate();
                })
                .show();
    }

    private void editScore() {
        EditText value = field(state.score, "请输入芝麻分");
        value.setInputType(InputType.TYPE_CLASS_NUMBER);
        value.setSelection(value.getText().length());
        CheckBox hide = new CheckBox(this);
        hide.setText("隐藏");
        hide.setChecked(state.hideScoreTail);
        LinearLayout box = column(value);
        box.addView(hide, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("编辑芝麻分")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    state.score = value.getText().toString().trim();
                    state.hideScoreTail = hide.isChecked();
                    ZhiHomeStore.save(state);
                    view.invalidate();
                })
                .show();
    }

    private void editCreditLabel() {
        EditText label = field(state.creditLabel, "例如：信用良好");
        new AlertDialog.Builder(this)
                .setTitle("编辑信用状态")
                .setView(column(label))
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    state.creditLabel = label.getText().toString().trim();
                    ZhiHomeStore.save(state);
                    view.invalidate();
                })
                .show();
    }

    private void editMessage(int index) {
        String[] row = state.messages[index];
        EditText title = field(row[0], "标题");
        EditText detail = field(row[1], "内容");
        EditText time = field(row[2], "时间");
        new AlertDialog.Builder(this)
                .setTitle("编辑最近消息")
                .setView(column(title, detail, time))
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    state.messages[index][0] = title.getText().toString();
                    state.messages[index][1] = detail.getText().toString();
                    state.messages[index][2] = time.getText().toString();
                    ZhiHomeStore.save(state);
                    view.invalidate();
                })
                .show();
    }

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        if (token.startsWith("nav:")) {
            int index = Integer.parseInt(token.substring(4));
            if (index == 0) {
                state.scroll = 0f;
                view.invalidate();
                applyStatusBarTint();
            } else if (index == 1) {
                startActivity(LicaiActivity.intentFor(this));
                overridePendingTransition(0, 0);
            } else if (index == 3) {
                startActivity(MessageActivity.intentFor(this));
                overridePendingTransition(0, 0);
            } else if (index == 4) {
                startActivity(MeActivity.intentFor(this));
                overridePendingTransition(0, 0);
            } else {
            }
            return;
        }
        if (token.equals("plus")) {
            state.plusMenu = true;
            // The pop-up is anchored under the "+", so the header has to sit at the top.
            state.scroll = 0f;
            applyStatusBarTint();
            view.invalidate();
            return;
        }
        if (token.equals("plusClose")) {
            state.plusMenu = false;
            applyStatusBarTint();
            view.invalidate();
            return;
        }
        if (token.startsWith("plus:")) {
            String item = token.substring(5);
            state.plusMenu = false;
            applyStatusBarTint();
            view.invalidate();
            if (item.equals("扫一扫")) {
                startActivity(ScanActivity.intentFor(this));
            } else {
            }
            return;
        }
        if (token.equals("search")) {
            return;
        }
        if (token.startsWith("action:")) {
            String action = token.substring(7);
            if (action.equals("扫一扫")) {
                startActivity(ScanActivity.intentFor(this));
            } else if (action.equals("收付款")) {
                startActivity(PayCodeActivity.intentFor(this));
            } else if (action.equals("出行")) {
                startActivity(TravelActivity.intentFor(this));
            } else {
            }
            return;
        }
        if (token.startsWith("icon:")) {
            return;
        }
        if (token.equals("edit:address")) {
            editAddress();
            return;
        }
        if (token.equals("edit:newmsg")) {
            editNewMessage();
            return;
        }
        if (token.equals("edit:score")) {
            editScore();
            return;
        }
        if (token.equals("edit:credit")) {
            editCreditLabel();
            return;
        }
        if (token.startsWith("edit:msg")) {
            editMessage(Integer.parseInt(token.substring(8)));
            return;
        }
        switch (token) {
            case "all":
                break;
            case "zhimaMore":
                startActivity(ZhimaCreditActivity.intentFor(this));
                break;
            case "zhima":
                startActivity(ZhimaCreditActivity.intentFor(this));
                break;
            case "collect":
                toast("信用进度球已收取 +1");
                break;
            case "feed":
                break;
            default:
                break;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private final class PageView extends View {
        PageView() {
            super(ZhiHomeActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / ZhiHomeScreen.W, getHeight() / ZhiHomeScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * ZhiHomeScreen.W / Math.max(1, getWidth());
            float y = event.getY() * ZhiHomeScreen.H / Math.max(1, getHeight());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = y;
                    lastY = y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (state.plusMenu) {
                        lastY = y;
                        return true;
                    }
                    if (y < ZhiHomeScreen.NAV_TOP) {
                        float delta = lastY - y;
                        if (Math.abs(y - downY) > 6) moved = true;
                        if (moved) {
                            float next = Math.max(0f, Math.min(screen.maxScroll(), state.scroll + delta));
                            if (next != state.scroll) {
                                state.scroll = next;
                                invalidate();
                            }
                        }
                    }
                    lastY = y;
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!moved) act(screen.hitTest(state, x, y));
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
