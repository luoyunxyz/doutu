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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.luoyun.doutu.features.zhi.data.Receipt;
import com.luoyun.doutu.features.zhi.data.ReceiptStore;
import com.luoyun.doutu.features.zhi.ui.ReceiptDetailScreen;

import java.util.function.Consumer;

/**
 * Hosts the 收款详情 page a row of 收款记录 opens, and owns everything that may be typed over on it.
 *
 * <p>The amount, what arrived, the 积分 line, the free quota, the 交易说明 and the payer all open a
 * dialog; the payer's picture opens the module's own avatar sheet; and the three 顾客画像 columns
 * under the card can be pinned to the user's own figures or handed back to the records they are
 * otherwise counted from. Every change goes straight into {@link ReceiptStore}, so the list behind
 * this page shows it the moment it is back.
 *
 * <p>Everything here is a local mock: no account, no network and no payment flow is involved.
 */
public final class ReceiptDetailActivity extends Activity {
    private static final String EXTRA_ID = "receipt_id";

    private final ReceiptDetailScreen screen = new ReceiptDetailScreen();
    private View view;
    private Receipt item;
    private ReceiptStore.Stats stats;

    public static Intent intentFor(Context context, long id) {
        Intent intent = new Intent(context, ReceiptDetailActivity.class);
        intent.putExtra(EXTRA_ID, id);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        item = ReceiptStore.byId(getIntent().getLongExtra(EXTRA_ID, 0L));
        if (item == null) {
            finish();
            return;
        }
        screen.load(getResources());
        stats = ReceiptStore.stats(item.payer);
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

    /** Keeps every change: the store is what the list behind this page reads back. */
    private void commit() {
        ReceiptStore.save(item);
        stats = ReceiptStore.stats(item.payer);
        view.invalidate();
    }

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        switch (token) {
            case "back":
                finish();
                return;
            case "avatar":
                AvatarChooser.show(this, "收款头像", (Bitmap picked) -> {
                    ReceiptStore.avatar(item, picked);
                    commit();
                });
                return;
            case "amount":
            case "amountLabel":
                askNumber("修改收款金额", item.amount, value -> {
                    item.amount = value;
                    commit();
                });
                return;
            case "actual":
                askNumber("修改实际到账", item.actual, value -> {
                    item.actual = value;
                    commit();
                });
                return;
            case "quota":
                askNumber("修改提现免费额度", item.quota, value -> {
                    item.quota = value;
                    commit();
                });
                return;
            case "prize":
                askText("修改收钱有奖", item.prize, value -> {
                    item.prize = value;
                    commit();
                });
                return;
            case "note":
                askText("修改交易说明", item.note, value -> {
                    item.note = value;
                    commit();
                });
                return;
            case "payer":
                askText("修改支付人", item.payer, value -> {
                    if (value.isEmpty()) return;
                    item.payer = value;
                    commit();
                });
                return;
            case "month":
                editColumn(0, "修改本月消费");
                return;
            case "total":
                editColumn(1, "修改累计消费");
                return;
            case "avg":
                editColumn(2, "修改平均单价");
                return;
            case "consumer":
                return;
            case "more":
                return;
            case "contact":
                return;
            case "refund":
                return;
            case "claim":
                return;
            case "help":
                return;
            default:
        }
    }

    // ------------------------------------------------------------------ the fields behind the card

    private void askText(String title, String current, Consumer<String> save) {
        EditText field = text(current, "请输入");
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(column(field))
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> save.accept(field.getText().toString().trim()))
                .show();
    }

    private void askNumber(String title, double current, Consumer<Double> save) {
        EditText field = decimal(ReceiptStore.money(current));
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(column(field))
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    double value = number(field.getText().toString(), -1);
                    if (value < 0) {
                        toast("金额填得不对");
                        return;
                    }
                    save.accept(value);
                })
                .show();
    }

    /**
     * The three 顾客画像 columns: each is counted from the payer's own records, and each may be
     * pinned to the user's figures instead. 恢复自动 hands it back to the count.
     *
     * @param column 0 本月消费, 1 累计消费, 2 平均单价
     */
    private void editColumn(int column, String title) {
        EditText amount = decimal(ReceiptStore.money(current(column)));
        LinearLayout box = column(amount);
        EditText count = null;
        if (column < 2) {
            count = plain(Integer.toString(currentCount(column)), "笔数");
            box.addView(count, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        final EditText countField = count;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复自动", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                clear(column);
                commit();
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                double value = number(amount.getText().toString(), -1);
                if (value < 0) {
                    toast("金额填得不对");
                    return;
                }
                int rows = -1;
                if (countField != null) {
                    rows = (int) number(countField.getText().toString(), -1);
                    if (rows < 0) {
                        toast("笔数填得不对");
                        return;
                    }
                }
                pin(column, value, rows);
                commit();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private double current(int column) {
        switch (column) {
            case 0:
                return item.monthSpend != null ? item.monthSpend : stats.monthSpend;
            case 1:
                return item.totalSpend != null ? item.totalSpend : stats.totalSpend;
            default:
                if (item.avgPrice != null) return item.avgPrice;
                int count = item.totalCount != null ? item.totalCount : stats.totalCount;
                double total = item.totalSpend != null ? item.totalSpend : stats.totalSpend;
                return count == 0 ? 0 : total / count;
        }
    }

    private int currentCount(int column) {
        if (column == 0) return item.monthCount != null ? item.monthCount : stats.monthCount;
        return item.totalCount != null ? item.totalCount : stats.totalCount;
    }

    private void pin(int column, double value, int rows) {
        switch (column) {
            case 0:
                item.monthSpend = value;
                item.monthCount = rows;
                break;
            case 1:
                item.totalSpend = value;
                item.totalCount = rows;
                break;
            default:
                item.avgPrice = value;
                break;
        }
    }

    private void clear(int column) {
        switch (column) {
            case 0:
                item.monthSpend = null;
                item.monthCount = null;
                break;
            case 1:
                item.totalSpend = null;
                item.totalCount = null;
                break;
            default:
                item.avgPrice = null;
                break;
        }
    }

    // ------------------------------------------------------------------ small widgets

    private EditText text(String value, String hint) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setText(value);
        field.setSelection(field.getText().length());
        return field;
    }

    private EditText decimal(String value) {
        EditText field = plain(value, "金额");
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return field;
    }

    private EditText plain(String value, String hint) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint(hint);
        field.setText(value);
        field.setSelection(field.getText().length());
        return field;
    }

    private LinearLayout column(EditText field) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private static double number(String text, double fallback) {
        if (text == null) return fallback;
        String value = text.trim();
        if (value.isEmpty()) return fallback;
        try {
            return ReceiptStore.round2(Double.parseDouble(value));
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
            super(ReceiptDetailActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / ReceiptDetailScreen.W, getHeight() / ReceiptDetailScreen.H);
            screen.draw(canvas, item, stats);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * ReceiptDetailScreen.W / Math.max(1, getWidth());
            float y = event.getY() * ReceiptDetailScreen.H / Math.max(1, getHeight());
            if (event.getActionMasked() == MotionEvent.ACTION_UP) act(screen.hitTest(x, y));
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
