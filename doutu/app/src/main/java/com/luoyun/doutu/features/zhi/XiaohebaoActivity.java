package com.luoyun.doutu.features.zhi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
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

import com.luoyun.doutu.features.zhi.data.PurseStore;
import com.luoyun.doutu.features.zhi.ui.XiaohebaoScreen;

/**
 * Hosts the 小荷包 page the 支 module grid's 小荷包 tile opens.
 *
 * <p>Three things on it are the user's: the picture beside 邀请他人加入 opens the module's own avatar
 * sheet — sixteen pictures from the random-avatar service plus 从本地上传 for the album — and the
 * card's caption (生活费) and the total under 总金额(元) open the same small editor. All three are kept
 * by {@link PurseStore}, so they are still there the next time the page is opened.
 *
 * <p>The eye beside the total hides it and shows it again, and everything else on the page answers
 * with a demo toast: this is a local mock with no purse, no member and no money behind it.
 */
public final class XiaohebaoActivity extends Activity {
    private static final String EXTRA_NONCE = "xiaohebao_nonce";

    private final XiaohebaoScreen screen = new XiaohebaoScreen();
    private final XiaohebaoScreen.State state = new XiaohebaoScreen.State();
    private View view;

    public static Intent intentFor(Context context) {
        Intent intent = new Intent(context, XiaohebaoActivity.class);
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
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) configureWindow(getWindow());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view != null) view.invalidate();
    }

    private void configureWindow(Window window) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        window.setStatusBarColor(0x00000000);
        window.setNavigationBarColor(0x00000000);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int both = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(both, both);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    // ------------------------------------------------------------------ 点一下

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        if (token.startsWith("avatar:")) {
            // 点哪个头像就改哪个；第一个是小荷包自己的，后面几个是加进来的
            int index = Integer.parseInt(token.substring(7));
            AvatarChooser.show(this, index == 0 ? "小荷包头像" : "换这个头像", picture -> {
                PurseStore.avatar(index, picture);
                view.invalidate();
            });
            return;
        }
        if (token.equals("add")) {
            // 头像那一行的 +：加一个新头像，加满了就说一声
            if (PurseStore.avatarCount() >= PurseStore.MAX_AVATARS) {
                toast("小荷包最多 " + PurseStore.MAX_AVATARS + " 个头像");
                return;
            }
            AvatarChooser.show(this, "添加头像", picture -> {
                PurseStore.addAvatar(picture);
                view.invalidate();
            });
            return;
        }
        switch (token) {
            case "back":
                finish();
                return;
            case "eye":
                state.hidden = !state.hidden;
                view.invalidate();
                return;
            case "purse":
                ask("给这个小荷包改个名", PurseStore.name(), "例如：生活费", value -> {
                    PurseStore.name(value);
                    view.invalidate();
                });
                return;
            case "total":
                ask("改一下总金额", PurseStore.total(), "例如：1000.00", value -> {
                    PurseStore.total(value);
                    view.invalidate();
                });
                return;
            case "avatar":
                // 头像带序号，上面就处理掉了
                return;
            case "tab:0":
            case "tab:1":
            case "tab:2":
                state.tab = token.charAt(4) - '0';
                view.invalidate();
                return;
            default:
                break;
        }
        if (token.equals("bell")) {
        } else if (token.equals("home")) {
        } else if (token.equals("settings")) {
        } else if (token.equals("invite")) {
        } else if (token.equals("rights")) {
        } else if (token.equals("out")) {
        } else if (token.equals("in")) {
        } else if (token.equals("func")) {
        } else if (token.equals("plugin")) {
        } else if (token.equals("goal")) {
        } else if (token.equals("promise")) {
        } else if (token.equals("publish")) {
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
                    view.invalidate();
                })
                .show();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (AvatarChooser.deliver(this, request, result, data)) {
            view.invalidate();
        }
    }

    private final class PageView extends View {
        PageView() {
            super(XiaohebaoActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / XiaohebaoScreen.W, getHeight() / XiaohebaoScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * XiaohebaoScreen.W / Math.max(1, getWidth());
            float y = event.getY() * XiaohebaoScreen.H / Math.max(1, getHeight());
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                act(screen.hitTest(state, x, y));
                return true;
            }
            return event.getActionMasked() == MotionEvent.ACTION_DOWN || super.onTouchEvent(event);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
