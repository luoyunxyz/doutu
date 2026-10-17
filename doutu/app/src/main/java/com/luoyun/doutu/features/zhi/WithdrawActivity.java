package com.luoyun.doutu.features.zhi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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

import com.luoyun.doutu.features.zhi.data.ReceiptStore;
import com.luoyun.doutu.features.zhi.data.WithdrawStore;
import com.luoyun.doutu.features.zhi.ui.BankSheet;
import com.luoyun.doutu.features.zhi.ui.WithdrawScreen;

import java.util.List;

/**
 * Hosts the 提现 page the 支 module's 余额提现 tile opens.
 *
 * <p>The page draws itself, so this owns the window and what a gesture means. Tapping the amount box
 * raises the page's own number pad and the ⌄ above it puts it away again; tapping anywhere on the
 * 到账银行卡 row raises 选择提现银行卡, where a row picks the card the page then shows, a long press
 * offers 替换图标 / 修改名称, and 添加储蓄卡提现 drops another card in above the 网商 one and asks
 * which logo it wears.
 *
 * <p>Everything here is a local mock: no bank, no account and no payment is involved.
 */
public final class WithdrawActivity extends Activity {
    private static final String EXTRA_NONCE = "withdraw_nonce";
    /** 按住多久算长按：金额框长按改可用余额，免费额度长按改额度，卡片行长按出菜单。 */
    private static final long HOLD_MS = 480;

    private final WithdrawScreen screen = new WithdrawScreen();
    private final WithdrawScreen.State state = new WithdrawScreen.State();
    private final BankSheet sheet = new BankSheet();
    private View view;

    private boolean sheetOpen;
    private float downX;
    private float downY;
    private long downAt;
    private String downToken;
    private boolean moved;

    public static Intent intentFor(Context context) {
        Intent intent = new Intent(context, WithdrawActivity.class);
        intent.putExtra(EXTRA_NONCE, System.nanoTime());
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        screen.load(getResources());
        sheet.load(getResources());
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
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
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
        if (sheetOpen) {
            if (token.equals("close")) {
                sheetOpen = false;
                view.invalidate();
            } else if (token.startsWith("pick:")) {
                pick(Integer.parseInt(token.substring(5)));
            } else if (token.equals("add")) {
                addCard();
            }
            return;
        }
        if (token.startsWith("key:")) {
            key(token.substring(4));
            return;
        }
        if (token.startsWith("pin:")) {
            pin(token.substring(4));
            return;
        }
        switch (token) {
            case "back":
                finish();
                break;
            case "dots":
                break;
            case "bank":
                sheetOpen = true;
                view.invalidate();
                break;
            case "amount":
                state.keypad = true;
                view.invalidate();
                break;
            case "clear":
                // 输入框右边那个 ⊗
                state.amount = "";
                view.invalidate();
                break;
            case "info":
                break;
            case "quota":
                toast("提现免费额度 · 长按可以改");
                break;
            case "row:0":
                break;
            case "row:1":
                break;
            case "row:2":
                break;
            case "submit":
                submit();
                break;
            default:
                break;
        }
    }

    /** 长按：页面上三个能改的东西，和卡片行的那两件事。 */
    private void hold(String token) {
        if (token == null) return;
        if (sheetOpen) {
            if (token.startsWith("pick:")) {
                int index = Integer.parseInt(token.substring(5));
                cardMenu(index);
            }
            return;
        }
        switch (token) {
            case "amount":
                askNumber("修改可用余额", WithdrawStore.avail(), value -> {
                    WithdrawStore.avail(value);
                    view.invalidate();
                });
                break;
            case "quota":
                askNumber("修改提现免费额度", WithdrawStore.quota(), value -> {
                    WithdrawStore.quota(value);
                    view.invalidate();
                });
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ 键盘

    private void key(String what) {
        if (what.equals("hide")) {
            state.keypad = false;
            view.invalidate();
            return;
        }
        if (what.equals("submit")) {
            submit();
            return;
        }
        if (what.equals("del")) {
            if (!state.amount.isEmpty()) {
                state.amount = state.amount.substring(0, state.amount.length() - 1);
            }
            view.invalidate();
            return;
        }
        if (what.equals("dot")) {
            if (!state.amount.contains(".")) {
                state.amount = state.amount.isEmpty() ? "0." : state.amount + ".";
            }
            view.invalidate();
            return;
        }
        // a digit
        if (state.amount.contains(".") && state.amount.length() - state.amount.indexOf('.') > 2) return;
        if (state.amount.replace(".", "").length() >= 9) return;
        if (state.amount.equals("0")) state.amount = "";
        state.amount = state.amount + what;
        view.invalidate();
    }

    /**
     * 提现：这一版的余额是演示用的，金额也随便 —— 点它就一定把支付密码那一层升起来（金额还没填就
     * 按 ¥0.00 算），因为这一页要看的正是「点提现 → 弹密码键盘 → 输六位 → 提交成功」这条线。
     * 按钮的浅蓝和下面那行红字只是照着参考图渲染的状态，不拦人。
     */
    private void submit() {
        state.password = true;
        state.pin = "";
        view.invalidate();
    }

    /** 支付密码那一层：敲数字、退格、关掉、或者忘了密码。 */
    private void pin(String what) {
        if (what.equals("close")) {
            state.password = false;
            state.pin = "";
            view.invalidate();
            return;
        }
        if (what.equals("forgot")) {
            return;
        }
        if (!state.password) return;
        if (what.equals("del")) {
            if (!state.pin.isEmpty()) {
                state.pin = state.pin.substring(0, state.pin.length() - 1);
            }
            view.invalidate();
            return;
        }
        if (state.pin.length() >= WithdrawScreen.PIN_LENGTH) return;
        state.pin = state.pin + what;
        if (state.pin.length() == WithdrawScreen.PIN_LENGTH) {
            // 六位齐了：进到账成功那一页，回来时输入框是空的
            double amount = WithdrawScreen.value(state);
            WithdrawStore.Card card = WithdrawStore.selected();
            String bank = card == null ? "银行卡" : card.name + "(" + card.tail + ")";
            state.password = false;
            state.keypad = false;
            state.pin = "";
            state.amount = "";
            startActivity(WithdrawDoneActivity.intentFor(this, amount, bank));
            overridePendingTransition(0, 0);
        }
        view.invalidate();
    }

    // ------------------------------------------------------------------ 银行卡

    /** 选择列表里点了一行：能用的就换成它，不能用的照参考图说一句。 */
    private void pick(int index) {
        List<WithdrawStore.Card> cards = WithdrawStore.cards();
        if (index < 0 || index >= cards.size()) return;
        WithdrawStore.Card card = cards.get(index);
        if (!card.usable) {
            toast("此卡不可用于提现");
            return;
        }
        WithdrawStore.select(card.id);
        sheetOpen = false;
        view.invalidate();
    }

    /** 长按一行：替换它的图标，或者改它的名字。 */
    private void cardMenu(int index) {
        List<WithdrawStore.Card> cards = WithdrawStore.cards();
        if (index < 0 || index >= cards.size()) return;
        final WithdrawStore.Card card = cards.get(index);
        new AlertDialog.Builder(this)
                .setTitle(card.title())
                .setItems(new String[]{"替换图标", "修改名称"}, (dialog, which) -> {
                    if (which == 0) chooseLogo(card);
                    else askName(card);
                })
                .show();
    }

    /** 添加储蓄卡提现：新卡直接落在网商银行那张的上面，接着就让人挑一个图标。 */
    private void addCard() {
        WithdrawStore.Card card = WithdrawStore.add("zhaoshangyh",
                WithdrawStore.bankCardName("zhaoshangyh"), WithdrawStore.newTail());
        view.invalidate();
        toast("已添加 " + card.title());
        chooseLogo(card);
    }

    private void chooseLogo(final WithdrawStore.Card card) {
        BankIconChooser.show(this, "选择" + card.name + "的图标", picked -> {
            // 名字还跟着图标走的话，换图标就顺手改一下；改过名字的、以及自己上传的图都不动
            boolean auto = WithdrawStore.autoName(card.name);
            WithdrawStore.logo(card, picked.bank, picked.custom);
            String name = WithdrawStore.bankCardName(picked.bank);
            if (auto && name != null) WithdrawStore.rename(card, name);
            view.invalidate();
        });
    }

    private void askName(final WithdrawStore.Card card) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(card.name);
        new AlertDialog.Builder(this)
                .setTitle("修改银行卡名称")
                .setView(field)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    WithdrawStore.rename(card, field.getText().toString());
                    view.invalidate();
                })
                .show();
    }

    /** 一个数字字段：可用余额和提现免费额度都用它改。 */
    private void askNumber(String title, double current, java.util.function.DoubleConsumer onDone) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setText(ReceiptStore.money(current));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(18 * getResources().getDisplayMetrics().density);
        column.setPadding(pad, pad / 2, pad, 0);
        TextView hint = new TextView(this);
        hint.setText("两位小数，改完这一页立刻跟着变");
        hint.setTextSize(12.5f);
        hint.setTextColor(0xff8a8a8e);
        column.addView(hint);
        column.addView(field);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(column)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    String text = field.getText().toString().trim();
                    try {
                        onDone.accept(Double.parseDouble(text));
                    } catch (Exception e) {
                        toast("这个数字填得不对");
                    }
                })
                .show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (BankIconChooser.deliver(this, request, result, data)) return;
        super.onActivityResult(request, result, data);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ 触摸

    private String tokenAt(float x, float y) {
        return sheetOpen ? sheet.hitTest(x, y) : screen.hitTest(state, x, y);
    }

    private final class PageView extends View {
        PageView() {
            super(WithdrawActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / WithdrawScreen.W, getHeight() / WithdrawScreen.H);
            WithdrawStore.Card card = WithdrawStore.selected();
            screen.draw(canvas, state, card, WithdrawStore.icon(card),
                    WithdrawStore.quota(), WithdrawStore.avail());
            if (sheetOpen) {
                List<WithdrawStore.Card> cards = WithdrawStore.cards();
                Bitmap[] logos = new Bitmap[cards.size()];
                for (int i = 0; i < cards.size(); i++) logos[i] = WithdrawStore.icon(cards.get(i));
                sheet.draw(canvas, cards, card == null ? 0 : card.id, logos);
            }
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * WithdrawScreen.W / Math.max(1, getWidth());
            float y = event.getY() * WithdrawScreen.H / Math.max(1, getHeight());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = x;
                    downY = y;
                    downAt = System.currentTimeMillis();
                    downToken = tokenAt(x, y);
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(x - downX) > 14 || Math.abs(y - downY) > 14) moved = true;
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved) {
                        if (System.currentTimeMillis() - downAt >= HOLD_MS) hold(downToken);
                        else act(tokenAt(x, y));
                        performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    moved = true;
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
