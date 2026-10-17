package com.luoyun.doutu.features.zhi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.luoyun.doutu.features.zhi.data.Receipt;
import com.luoyun.doutu.features.zhi.data.ReceiptStore;
import com.luoyun.doutu.features.zhi.ui.ReceiptScreen;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Hosts the 收款记录 page the 支 module's 收款记录 tile opens.
 *
 * <p>The page draws itself, so this owns the window and what a tap means: an entry row opens its
 * 收款详情 page, the summary block above the rows raises the 批量添加 sheet, and the 请问我 assistant
 * answers with a demo toast. The sheet is the page's one real control — the five spreads the
 * reference app offers plus 自定义, a count for each and the month they land in.
 *
 * <p>Everything here is a local mock: no account, no network and no payment flow is involved.
 */
public final class ReceiptActivity extends Activity {
    private static final String EXTRA_NONCE = "receipt_nonce";

    private final ReceiptScreen screen = new ReceiptScreen();
    private final ReceiptScreen.State state = new ReceiptScreen.State();
    private View view;

    private float downY;
    private float lastY;
    private boolean moved;

    public static Intent intentFor(Context context) {
        Intent intent = new Intent(context, ReceiptActivity.class);
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
        // whatever the detail page did to a record shows here as soon as it comes back
        if (view != null) view.invalidate();
        // and the payers still wearing the grey default are asked for a picture from the module's
        // own random-avatar service, the same one the 个人信息 sheet uses
        ReceiptStore.primeAvatars(ReceiptStore.PRIME_MAX, () -> {
            if (view != null) view.invalidate();
        });
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
                // the page is pale from top to bottom, so the system bars keep their dark icons
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

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        if (token.equals("back")) {
            finish();
            return;
        }
        if (token.startsWith("entry:")) {
            long id = Long.parseLong(token.substring(6));
            startActivity(ReceiptDetailActivity.intentFor(this, id));
            overridePendingTransition(0, 0);
            return;
        }
        if (token.startsWith("avatar:")) {
            // the payer's face: the module's own sheet, with 刷新 for another random one and
            // 从本地上传 for the user's own picture
            Receipt item = ReceiptStore.byId(Long.parseLong(token.substring(7)));
            if (item == null) return;
            AvatarChooser.show(this, "收款头像", picked -> {
                ReceiptStore.avatar(item, picked);
                view.invalidate();
            });
            return;
        }
        switch (token) {
            case "batch":
                showBatchSheet();
                break;
            case "all":
                break;
            case "points":
                break;
            case "history":
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ the 批量添加 sheet

    /** The month the sheet's records land in, newest first: this month, then the eleven before it. */
    private static final int MONTHS = 12;

    private void showBatchSheet() {
        float density = getResources().getDisplayMetrics().density;
        int dp = Math.round(density);
        int pad = Math.round(18 * density);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, Math.round(8 * density), pad, 0);

        TextView hint = new TextView(this);
        hint.setText("勾选要生成的档位并填上条数，金额在区间内随机（两位小数）");
        hint.setTextSize(12.5f);
        hint.setTextColor(0xff8a8a8e);
        column.addView(hint);

        final CheckBox[] boxes = new CheckBox[ReceiptStore.TIER_NAMES.length];
        final EditText[] counts = new EditText[ReceiptStore.TIER_NAMES.length];
        for (int i = 0; i < boxes.length; i++) {
            double[] range = ReceiptStore.TIER_RANGES[i];
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Math.round(4 * density), 0, 0);

            CheckBox box = new CheckBox(this);
            box.setText(ReceiptStore.TIER_NAMES[i] + "（" + bound(range[0]) + " ~ " + bound(range[1]) + "）");
            box.setTextSize(14.5f);
            row.addView(box, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            EditText count = numberField("条数", "");
            row.addView(count, new LinearLayout.LayoutParams(Math.round(74 * density),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            column.addView(row);
            boxes[i] = box;
            counts[i] = count;
        }

        // 自定义: its own bounds and its own count
        LinearLayout custom = new LinearLayout(this);
        custom.setOrientation(LinearLayout.HORIZONTAL);
        custom.setGravity(Gravity.CENTER_VERTICAL);
        custom.setPadding(0, Math.round(6 * density), 0, 0);
        CheckBox customBox = new CheckBox(this);
        customBox.setText("自定义");
        customBox.setTextSize(14.5f);
        custom.addView(customBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        EditText customMin = numberField("最低", "");
        EditText customMax = numberField("最高", "");
        custom.addView(customMin, new LinearLayout.LayoutParams(Math.round(62 * density),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView dash = new TextView(this);
        dash.setText("~");
        dash.setGravity(Gravity.CENTER);
        custom.addView(dash, new LinearLayout.LayoutParams(Math.round(16 * density),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        custom.addView(customMax, new LinearLayout.LayoutParams(Math.round(62 * density),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        EditText customCount = numberField("条数", "");
        custom.addView(customCount, new LinearLayout.LayoutParams(Math.round(62 * density),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(custom);

        // 指定到哪个月
        LinearLayout monthRow = new LinearLayout(this);
        monthRow.setOrientation(LinearLayout.HORIZONTAL);
        monthRow.setGravity(Gravity.CENTER_VERTICAL);
        monthRow.setPadding(0, Math.round(14 * density), 0, Math.round(4 * density));
        TextView monthLabel = new TextView(this);
        monthLabel.setText("指定月份");
        monthLabel.setTextSize(14.5f);
        monthLabel.setTextColor(0xff23252b);
        monthRow.addView(monthLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final int[] years = new int[MONTHS];
        final int[] months = new int[MONTHS];
        String[] labels = new String[MONTHS];
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        for (int i = 0; i < MONTHS; i++) {
            years[i] = c.get(Calendar.YEAR);
            months[i] = c.get(Calendar.MONTH) + 1;
            labels[i] = years[i] + "年" + months[i] + "月" + (i == 0 ? " 本月" : "");
            c.add(Calendar.MONTH, -1);
        }
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        spinner.setAdapter(adapter);
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
                Math.round(196 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
        spinnerLp.leftMargin = Math.round(10 * density);
        monthRow.addView(spinner, spinnerLp);
        column.addView(monthRow);

        ScrollView scroller = new ScrollView(this);
        scroller.addView(column);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("批量添加收款记录")
                .setView(scroller)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    List<ReceiptStore.Spec> specs = new ArrayList<>();
                    String problem = collect(boxes, counts, customBox, customMin, customMax,
                            customCount, specs);
                    if (problem != null) {
                        toast(problem);
                        return;
                    }
                    int index = Math.max(0, Math.min(MONTHS - 1, spinner.getSelectedItemPosition()));
                    int made = ReceiptStore.add(specs, years[index], months[index]);
                    toast("已添加 " + made + " 条收款记录");
                    state.scroll = 0f;
                    view.invalidate();
                    // the new payers get faces from the same service as everything else on the page
                    ReceiptStore.primeAvatars(ReceiptStore.PRIME_MAX, () -> view.invalidate());
                    dialog.dismiss();
                }));
        dialog.show();
    }

    /**
     * Reads the sheet back: the checked spreads with their counts, then 自定义.
     *
     * @return what is wrong with it, or null when it can be added
     */
    private String collect(CheckBox[] boxes, EditText[] counts, CheckBox customBox,
                           EditText customMin, EditText customMax, EditText customCount,
                           List<ReceiptStore.Spec> specs) {
        int total = 0;
        for (int i = 0; i < boxes.length; i++) {
            if (!boxes[i].isChecked()) continue;
            int count = parseInt(counts[i].getText().toString(), -1);
            if (count < 0) return ReceiptStore.TIER_NAMES[i] + " 的条数填得不对";
            if (count == 0) continue;
            double[] range = ReceiptStore.TIER_RANGES[i];
            specs.add(new ReceiptStore.Spec(ReceiptStore.TIER_NAMES[i], range[0], range[1], count));
            total += count;
        }
        if (customBox.isChecked()) {
            double min = parseDouble(customMin.getText().toString(), -1);
            double max = parseDouble(customMax.getText().toString(), -1);
            int count = parseInt(customCount.getText().toString(), -1);
            if (min <= 0 || max <= 0) return "自定义的金额范围要填两个正数";
            if (max < min) return "自定义的最高金额不能小于最低金额";
            if (count < 0) return "自定义的条数填得不对";
            if (count > 0) {
                specs.add(new ReceiptStore.Spec("自定义", min, max, count));
                total += count;
            }
        }
        if (total == 0) return "请先勾选档位并填上要生成的条数";
        if (total > ReceiptStore.MAX_BATCH) return "一次性只能批量添加" + ReceiptStore.MAX_BATCH + "个记录";
        return null;
    }

    /** A compact bound for the sheet's captions: 0.1 stays 0.1, 50 stays 50. */
    private static String bound(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001) return Long.toString(Math.round(value));
        String text = ReceiptStore.money(value);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    /** A short numeric field; the negative fallback comes back when the text is not a number. */
    private EditText numberField(String hint, String value) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint(hint);
        field.setText(value);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return field;
    }

    private static double parseDouble(String text, double fallback) {
        if (text == null) return fallback;
        String value = text.trim();
        if (value.isEmpty()) return fallback;
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** How many records a row asks for; negative when the field does not hold a count. */
    private static int parseInt(String text, int fallback) {
        if (text == null) return fallback;
        String value = text.trim();
        if (value.isEmpty()) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (AvatarChooser.deliver(this, request, result, data)) return;
        super.onActivityResult(request, result, data);
    }

    private final class PageView extends View {
        PageView() {
            super(ReceiptActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / ReceiptScreen.W, getHeight() / ReceiptScreen.H);
            screen.draw(canvas, state, ReceiptStore.records());
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * ReceiptScreen.W / Math.max(1, getWidth());
            float y = event.getY() * ReceiptScreen.H / Math.max(1, getHeight());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = y;
                    lastY = y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float limit = screen.maxScroll(ReceiptStore.records());
                    if (limit > 0f) {
                        float delta = lastY - y;
                        if (Math.abs(y - downY) > 6) moved = true;
                        if (moved) {
                            float next = Math.max(0f, Math.min(limit, state.scroll + delta));
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
                    if (!moved) act(screen.hitTest(state, x, y, ReceiptStore.records()));
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
