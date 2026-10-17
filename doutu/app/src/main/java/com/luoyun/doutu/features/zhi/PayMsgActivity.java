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

import com.luoyun.doutu.R;
import com.luoyun.doutu.features.zhi.data.Bill;
import com.luoyun.doutu.features.zhi.data.BillStore;
import com.luoyun.doutu.features.zhi.data.PayMsgStore;
import com.luoyun.doutu.features.zhi.ui.PayMsgScreen;

import java.util.Calendar;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 支付消息页（小支主页 - 消息 - 消息盒子，也是「支」宫格里的「支付消息」）。
 *
 * <p>页面本身是 {@link PayMsgScreen} 照着参考图一块块贴出来的，这里只负责三件事：跟着手指滚、
 * 点开某一行弹个输入框改掉它（改完写进 {@link PayMsgStore}，关掉 App 再进来还在），以及点进某
 * 一条消息时把它变成一条账单，交给账单详情页 —— 和账单列表点进去的是同一页。
 *
 * <p>整页都是本地假数据：没有账号、没有余额、没有商户、没有订单，也不会联网。
 */
public final class PayMsgActivity extends Activity {
    private static final String EXTRA_NONCE = "pay_msg_nonce";
    private static final float TOUCH_SLOP = 8f;

    /** 每张卡片的账单分类，进详情页时用得上。 */
    private static final String[] CATEGORY = {
            "商业服务", "商业服务", "信用借还", "日用百货", "生活缴费",
            "日用百货", "日用百货", "日用百货"};
    /** 每张卡片对应账单页那套图标里的哪一个（云猫 / 花呗 / 闲鱼 / 移动）。 */
    private static final String[] MARK = {"", "云猫", "花呗", "闲鱼", "移动", "闲鱼", "闲鱼", "闲鱼"};
    private static final int[] TYPE = {0, 1, 0, 2, 1, 2, 2, 2};

    private final PayMsgScreen screen = new PayMsgScreen();
    private final PayMsgScreen.State state = new PayMsgScreen.State(PayMsgStore.texts());
    private PageView view;

    private float downX;
    private float downY;
    private float lastY;
    private boolean moved;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, PayMsgActivity.class);
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
        view.invalidate();
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

    /** 退回上一页是瞬时的：底下的页面本来就是那一页。 */
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
        if (token.equals("back")) {
            finish();
            return;
        }
        if (token.startsWith("edit:")) {
            showEditor(token.substring(5));
            return;
        }
        if (token.startsWith("detail:")) {
            openDetail(Integer.parseInt(token.substring(7)));
        }
    }

    private void scrollPageTo(float target) {
        state.scroll = Math.max(0f, Math.min(screen.maxScroll(state), target));
        view.invalidate();
    }

    // ------------------------------------------------------------------ 改内容

    private String valueOf(String key) {
        String stored = state.texts.get(key);
        if (stored != null) return stored;
        PayMsgScreen.Field field = PayMsgScreen.field(key);
        return field == null ? "" : field.def;
    }

    private void showEditor(final String key) {
        final PayMsgScreen.Field field = PayMsgScreen.field(key);
        if (field == null) return;
        final EditText input = new EditText(this);
        input.setInputType(field.numeric
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                : InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setText(valueOf(key));
        // 整段选中：点开就能直接换成新的，和余额宝页一样
        input.selectAll();

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(getResources().getDisplayMetrics().density * 20);
        column.setPadding(pad, pad / 2, pad, 0);
        column.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(PayMsgScreen.titleOf(field))
                .setView(column)
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (d, w) -> {
                    state.texts.remove(key);
                    PayMsgStore.clearText(key);
                    view.invalidate();
                })
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        input.setError("内容不能为空");
                        return;
                    }
                    if (field.numeric) value = PayMsgScreen.money(value);
                    state.texts.put(key, value);
                    PayMsgStore.text(key, value);
                    view.invalidate();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    // ------------------------------------------------------------------ 进详情

    /**
     * 点开某一条消息：这条消息变成一条账单，交给 {@link BillDetailActivity} —— 和账单列表点进
     * 去的是同一页，改法也一样。图标跟着这条消息自己的图标走。
     */
    private void openDetail(int card) {
        if (card < 0 || card >= TYPE.length) return;
        String prefix = "c" + (card + 1) + ".";
        boolean automatic = TYPE[card] == 2;

        Bill bill = new Bill();
        bill.merchant = valueOf(prefix + "name");
        bill.mark = MARK[card];
        bill.icon = screen.iconOf(card);
        bill.markColor = 0xff1677ff;
        bill.markInk = Color.WHITE;
        bill.category = CATEGORY[card];
        bill.billCategory = CATEGORY[card];
        bill.kind = "支出";
        bill.status = automatic ? valueOf(prefix + "status") : "";
        bill.amount = -number(valueOf(prefix + "amount"), 0);
        bill.time = when(card, valueOf(prefix + "time"));
        bill.payMethod = valueOf(prefix + "v1");
        bill.payee = automatic ? valueOf(prefix + "v2") : bill.merchant;
        String extra = valueOf(prefix + "l2") + " " + valueOf(prefix + "v2");
        bill.goods = extra.trim().isEmpty() ? bill.merchant : extra.trim();
        bill.reward = "已领取0积分";
        bill.orderNo = orderNo(bill.time, card);
        bill.merchantOrderNo = merchantOrderNo(bill.time, card, automatic);
        bill.counted = true;

        BillStore.select(null, bill);
        startActivity(BillDetailActivity.intentFor(this));
        overridePendingTransition(R.anim.bill_in, 0);
    }

    private static double number(String value, double fallback) {
        try {
            return Double.parseDouble(value.replace(",", "").trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static final Pattern MD = Pattern.compile(
            "(\\d{1,2})月(\\d{1,2})日\\s*(\\d{1,2}):(\\d{2})");
    private static final Pattern HM = Pattern.compile("(\\d{1,2}):(\\d{2})");

    /**
     * 卡片上写的时间是「11:44」「昨天 20:44」「10月8日 20:13」这样的，详情页要一个真正的时间点，
     * 所以在这里读回一个 Calendar：认不出来的就退回这条消息本来的时间。
     */
    private static long when(int card, String text) {
        Calendar now = Calendar.getInstance();
        String clean = text == null ? "" : text.trim();
        Matcher md = MD.matcher(clean);
        if (md.find()) {
            Calendar at = Calendar.getInstance();
            at.set(now.get(Calendar.YEAR), Integer.parseInt(md.group(1)) - 1,
                    Integer.parseInt(md.group(2)), Integer.parseInt(md.group(3)),
                    Integer.parseInt(md.group(4)), 0);
            at.set(Calendar.MILLISECOND, 0);
            return at.getTimeInMillis();
        }
        Matcher hm = HM.matcher(clean);
        if (hm.find()) {
            Calendar at = Calendar.getInstance();
            if (clean.startsWith("昨天")) at.add(Calendar.DAY_OF_MONTH, -1);
            at.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm.group(1)));
            at.set(Calendar.MINUTE, Integer.parseInt(hm.group(2)));
            at.set(Calendar.SECOND, 0);
            at.set(Calendar.MILLISECOND, 0);
            return at.getTimeInMillis();
        }
        Calendar at = Calendar.getInstance();
        at.add(Calendar.DAY_OF_MONTH, -card);
        return at.getTimeInMillis();
    }

    /** 订单号：付款那天加二十位数字，二十八位，和账单页的规矩一样。 */
    private static String orderNo(long time, int card) {
        Random random = new Random(time + card * 7919L);
        StringBuilder out = new StringBuilder(stamp(time));
        out.append("22");
        while (out.length() < 28) out.append(random.nextInt(10));
        return out.toString();
    }

    /** 商家订单号：自动扣款的是 24 位，其余 15 位。 */
    private static String merchantOrderNo(long time, int card, boolean automatic) {
        Random random = new Random(time * 31 + card);
        Calendar at = Calendar.getInstance();
        at.setTimeInMillis(time);
        if (automatic) {
            StringBuilder out = new StringBuilder("T200P");
            while (out.length() < 24) out.append(random.nextInt(10));
            return out.toString();
        }
        StringBuilder out = new StringBuilder();
        String letters = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        for (int i = 0; i < 3; i++) out.append(letters.charAt(random.nextInt(letters.length())));
        out.append(String.format(Locale.US, "%02d%02d",
                at.get(Calendar.MONTH) + 1, at.get(Calendar.DAY_OF_MONTH)));
        String pool = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        while (out.length() < 15) out.append(pool.charAt(random.nextInt(pool.length())));
        return out.toString();
    }

    private static String stamp(long time) {
        Calendar at = Calendar.getInstance();
        at.setTimeInMillis(time);
        return String.format(Locale.US, "%04d%02d%02d",
                at.get(Calendar.YEAR), at.get(Calendar.MONTH) + 1, at.get(Calendar.DAY_OF_MONTH));
    }

    // ------------------------------------------------------------------ 画布

    private final class PageView extends View {
        PageView() {
            super(PayMsgActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / PayMsgScreen.W, getHeight() / PayMsgScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * PayMsgScreen.W / Math.max(1, getWidth());
            float y = event.getY() * PayMsgScreen.H / Math.max(1, getHeight());
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
