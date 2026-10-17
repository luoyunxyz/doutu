package com.luoyun.doutu.features.zhi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
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

import com.luoyun.doutu.features.zhi.data.BillStore;
import com.luoyun.doutu.features.zhi.ui.BillAnalyticsScreen;

import java.io.InputStream;
import java.util.Locale;

/**
 * 收支统计页（小支主页 - 我的 - 账单 - 收支分析）。
 *
 * <p>页面分“周度 / 月度 / 年度”三个页签，全部由 {@link BillAnalyticsScreen} 用 Canvas 原生绘制。
 * 所有金额（以及分类笔数）都可以点击修改：点开任意数字即可在弹出的对话框里改数，
 * 环形图与占比会立刻按新数据重新计算。日期始终跟随系统当前时间自动同步。
 */
public final class BillAnalyticsActivity extends Activity {
    private static final float TOUCH_SLOP = 8f;
    private static final int REQUEST_PAY_ICON = 0x72;

    /** 支出排行 / 支出方式两栏的默认文字，点一下就能改。 */
    private static final String[] RANK_NAME =
            {"转账-孝文迪(***迪)", "机票", "美团-tomorrow·明日美发(…"};
    private static final String[] RANK_CAT = {"转账红包", "交通出行", "美容美发"};
    private static final String[] RANK_TIME = {"06-19 19:04", "08-10 22:15", "08-15 16:20"};
    private static final String[] PAY_NAME =
            {"花呗", "招商银行储蓄卡(4646)", "招商银行储蓄卡(5021)"};

    private static String at(String[] a, int i) {
        return (i < 0 || i >= a.length) ? a[0] : a[i];
    }

    private final BillAnalyticsScreen screen = new BillAnalyticsScreen();
    private final BillAnalyticsScreen.State state = new BillAnalyticsScreen.State();
    private PageView view;

    private float downY;
    private float lastY;
    private boolean moved;
    /** 正在等相册返回的是哪一行 支出方式（-1 表示没在等）。 */
    private int payIconRow = -1;

    public static Intent intentFor(Activity activity) {
        Intent intent = new Intent(activity, BillAnalyticsActivity.class);
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
        // 账单页已经按今天生成好数据了，这里直接复用，保证两边数字一致
        if (BillStore.months("全部").isEmpty()) BillStore.generate();
        view.invalidate();
    }

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

    // ── 一个可编辑的数据项 ────────────────────────────────────────────────

    private static final class Edit {
        final String key;      // 金额写入 state.edits 的键
        final double def;      // 默认值
        final String label;    // 对话框标题用的名字
        final String cntKey;   // 笔数写入 state.cnt 的键，可为 null
        final int cntDef;

        Edit(String key, double def, String label, String cntKey, int cntDef) {
            this.key = key;
            this.def = def;
            this.label = label;
            this.cntKey = cntKey;
            this.cntDef = cntDef;
        }
    }

    private Edit editFor(String token) {
        switch (token) {
            // 周度
            case "w.cat:0":  return new Edit("w.c.0", 20.00, "其他", "w.n.0", 1);
            case "w.rank.0": return new Edit("w.rank.0", 20.00, "CAT2610125E01AW", null, 0);
            case "w.day:0":  return new Edit("w.day.0", 20.00, "周一", null, 0);
            case "w.prev":   return new Edit("w.prev", 20.00, "较上周增加的支出", null, 0);
            // 月度
            case "m.cat:0":  return new Edit("m.c.0", 50.00, "充值缴费", "m.n.0", 1);
            case "m.cat:1":  return new Edit("m.c.1", 41.19, "日用百货", "m.n.1", 5);
            case "m.cat:2":  return new Edit("m.c.2", 20.00, "其他", "m.n.2", 1);
            case "m.k:0":    return new Edit("m.k.0", 0, "大额消费", null, 0);
            case "m.k:1":    return new Edit("m.k.1", 4, "自动扣款", null, 0);
            case "m.k:2":    return new Edit("m.k.2", 0, "先用后付", null, 0);
            case "m.k:3":    return new Edit("m.k.3", 0, "分期付款", null, 0);
            case "m.bar:0":  return new Edit("m.bar.0", 3063.44, "6月支出", null, 0);
            case "m.bar:1":  return new Edit("m.bar.1", 1247.21, "7月支出", null, 0);
            case "m.bar:2":  return new Edit("m.bar.2", 1755.18, "8月支出", null, 0);
            case "m.bar:3":  return new Edit("m.bar.3", 182.27, "9月支出", null, 0);
            case "m.bar:4":  return new Edit("m.bar.4", 111.19, "10月支出", null, 0);
            case "m.pay:0":  return new Edit("m.pay.0", 259.06, "招商银行储蓄卡(4646)", null, 0);
            case "m.up":     return new Edit("m.up", 82.92, "较上月增加的支出", null, 0);
            case "m.saved":  return new Edit("m.saved", 0.00, "本月已省", null, 0);
            // 年度
            case "y.cat:0":  return new Edit("y.c.0", 2933.12, "餐饮美食", "y.n.0", 133);
            case "y.cat:1":  return new Edit("y.c.1", 1866.58, "日用百货", "y.n.1", 100);
            case "y.cat:2":  return new Edit("y.c.2", 1401.90, "转账红包", "y.n.2", 9);
            case "y.cat:3":  return new Edit("y.c.3", 1063.50, "交通出行", "y.n.3", 0);
            case "y.cat:4":  return new Edit("y.c.4", 1033.40, "文化休闲", "y.n.4", 0);
            case "y.cat:5":  return new Edit("y.c.5", 403.50, "美容美发", "y.n.5", 0);
            case "y.cat:6":  return new Edit("y.c.6", 1139.67, "其他", "y.n.6", 31);
            case "y.tile:0": return new Edit("y.t.0", 2013.67, "正餐", null, 0);
            case "y.tile:1": return new Edit("y.t.1", 644.05, "其他", null, 0);
            case "y.tile:2": return new Edit("y.t.2", 243.00, "炸鸡汉堡", null, 0);
            case "y.tile:3": return new Edit("y.t.3", 20.40, "生鲜水果", null, 0);
            case "y.tile:4": return new Edit("y.t.4", 8.00, "咖啡奶茶", null, 0);
            case "y.tile:5": return new Edit("y.t.5", 4.00, "零食", null, 0);
            case "y.r:0":    return new Edit("y.r.0", 1000.00, "转账-孝文迪", null, 0);
            case "y.r:1":    return new Edit("y.r.1", 669.42, "机票", null, 0);
            case "y.r:2":    return new Edit("y.r.2", 374.00, "美团-明日美发", null, 0);
            case "y.p:0":    return new Edit("y.p.0", 1902.37, "花呗", "y.pc.0", 33);
            case "y.p:1":    return new Edit("y.p.1", 19526.35, "招商银行储蓄卡(4646)", "y.pc.1", 183);
            case "y.p:2":    return new Edit("y.p.2", 5.00, "招商银行储蓄卡(5021)", "y.pc.2", 1);
            case "y.saved":  return new Edit("y.saved", 53.66, "今年已省", null, 0);
            // 大卡片（支出页的总计）
            case "big.total:0": return new Edit("big.total.0", 20.00, "本周支出合计", null, 0);
            case "big.total:1": return new Edit("big.total.1", 111.19, "本月支出合计", null, 0);
            case "big.total:2": return new Edit("big.total.2", 9841.67, "今年支出合计", null, 0);
            case "yi.total":    return new Edit("yi.total", 3107.49, "今年收入合计", null, 0);
            // 城市解锁里的数字
            case "yc.n":     return new Edit("yc.n", 2, "使用线下支付的城市个数", null, 0);
            case "yc.n1":    return new Edit("yc.n1", 10, "左边那座城市的笔数", null, 0);
            case "yc.n2":    return new Edit("yc.n2", 69, "右边那座城市的笔数", null, 0);
            default: break;
        }
        if (token.startsWith("y.m:")) {
            int i = indexOf(token);
            double[] def = {520, 480, 360, 760, 1700, 3063.44, 1150, 1697, 520, 470, 480, 610};
            if (i >= 0 && i < 12) return new Edit("y.m." + i, def[i], (i + 1) + "月支出", null, 0);
        }
        // 月度 · 收入
        if (token.startsWith("mi.k:")) {
            int i = indexOf(token);
            String[] l = {"大额收入", "退款", "薪酬"};
            if (i >= 0 && i < 3) return new Edit("mi.k." + i, 0, l[i], null, 0);
        }
        if (token.startsWith("mi.b:")) {
            int i = indexOf(token);
            double[] dv = {1666.00, 0, 1400.01, 0, 0};
            if (i >= 0 && i < 5) return new Edit("mi.b." + i, dv[i], "月度收入", null, 0);
        }
        // 年度 · 收入
        if (token.startsWith("yi.m:")) {
            int i = indexOf(token);
            double[] dv = {0, 0, 0, 20.00, 21.49, 1666.00, 0, 1400.01, 0, 0, 0, 0};
            if (i >= 0 && i < 12) return new Edit("yi.m." + i, dv[i], (i + 1) + "月收入", null, 0);
        }
        if (token.startsWith("yi.r:")) {
            int i = indexOf(token);
            double[] dv = {1452.00, 1400.00, 214.00};
            String[] nm = {"转账-转转循环科技有限公司", "转账-染", "转账-转转循环科技有限公司"};
            if (i >= 0 && i < 3) return new Edit("yi.r." + i, dv[i], nm[i], null, 0);
        }
        // 周度：柱状图 / 日期格
        if (token.startsWith("w.bar:")) {
            int i = indexOf(token);
            String[] dn = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
            if (i >= 0 && i < 7) return new Edit("w.bar." + i, 20.00, dn[i] + "支出", null, 0);
        }
        if (token.startsWith("w.day:")) {
            int i = indexOf(token);
            String[] dn = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
            if (i >= 0 && i < 7) return new Edit("w.day." + i, 20.00, dn[i], null, 0);
        }
        return null;
    }

    private static int indexOf(String token) {
        try {
            return Integer.parseInt(token.substring(token.indexOf(':') + 1));
        } catch (Exception e) {
            return -1;
        }
    }

    // ── 交互 ──────────────────────────────────────────────────────────────

    private void act(String token) {
        if (token == null || token.equals("noop")) return;
        if (token.equals("back")) {
            finish();
            return;
        }
        if (token.startsWith("tab:")) {
            String[] parts = token.split(":");
            state.tab = Math.max(0, Math.min(2, Integer.parseInt(parts[1])));
            if (parts.length >= 4 && "mode".equals(parts[2])) state.income = "1".equals(parts[3]);
            state.scroll = 0f;
            state.showAll = false;
            state.expanded = 0;
            view.invalidate();
            return;
        }
        switch (token) {
            case "w.compare":
            case "m.compare":
                state.compare = !state.compare;
                view.invalidate();
                return;
            case "m.expand":
            case "y.expand":
            case "yi.rank.expand":
                state.showAll = !state.showAll;
                view.invalidate();
                return;
            case "big:total":
                if (state.tab == 1 && state.income) showEditor(editFor("mi.b:4"));
                else if (state.tab == 2 && state.income) showEditor(editFor("yi.total"));
                else showEditor(editFor("big.total:" + state.tab));
                return;
            case "big:cnt":
                if (state.tab == 1 && state.income) showCountEditor("mi.n", "收入笔数", 0);
                else if (state.tab == 2 && state.income) showCountEditor("yi.n", "收入笔数", 9);
                else showCountEditor("big.cnt." + state.tab, "笔数",
                        state.tab == 0 ? 1 : state.tab == 1 ? 7 : 273);
                return;
            case "y.notice.x":
                state.noticeClosed = true;
                view.invalidate();
                return;
            case "y.invest":
                state.investIncluded = !state.investIncluded;
                view.invalidate();
                return;
            case "week:prev":
                state.weekOffset -= 1;
                view.invalidate();
                return;
            case "week:next":
                if (state.weekOffset < 0) state.weekOffset += 1;
                view.invalidate();
                return;
            case "month:pick":
                pickPeriod(true);
                return;
            case "year:pick":
                pickPeriod(false);
                return;
            case "m.subscribe":
                Toast.makeText(this, "已订阅每月消费周报", Toast.LENGTH_SHORT).show();
                return;
            case "more":
                Toast.makeText(this, "更多分析", Toast.LENGTH_SHORT).show();
                return;
            default:
                break;
        }
        if (token.startsWith("m.exp:")) {
            int i = indexOf(token);
            state.expanded = (state.expanded == i) ? -1 : i;
            view.invalidate();
            return;
        }
        if (token.startsWith("y.exp:")) {
            int i = indexOf(token);
            state.expanded = (state.expanded == i) ? -1 : i;
            view.invalidate();
            return;
        }
        if (token.startsWith("mi.day:")) {
            showDayEditor("mi.day.", indexOf(token), "收入");
            return;
        }
        if (token.startsWith("m.day:")) {
            showDayEditor("m.day.", indexOf(token), "支出");
            return;
        }
        if (token.startsWith("yc.")) { showCityField(token); return; }
        if (token.startsWith("y.rn:")) { showTextEditor("y.rn."+indexOf(token), "交易名称", at(RANK_NAME, indexOf(token))); return; }
        if (token.startsWith("y.rc:")) { showTextEditor("y.rc."+indexOf(token), "分类", at(RANK_CAT, indexOf(token))); return; }
        if (token.startsWith("y.rt:")) { showTextEditor("y.rt."+indexOf(token), "时间", at(RANK_TIME, indexOf(token))); return; }
        if (token.startsWith("y.pn:")) { showTextEditor("y.pn."+indexOf(token), "支付方式", at(PAY_NAME, indexOf(token))); return; }
        if (token.startsWith("y.pi:")) { pickPayIcon(indexOf(token)); return; }
        Edit edit = editFor(token);
        if (edit != null) {
            showEditor(edit);
            return;
        }
        // 其余热区（如分类标题、小卡）也当成可编辑项，尽量不吞掉点击
    }

    private void pickPeriod(boolean month) {
        final String[] labels;
        if (month) {
            labels = new String[12];
            for (int i = 0; i < 12; i++) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.add(java.util.Calendar.MONTH, -i);
                labels[i] = cal.get(java.util.Calendar.YEAR) + "年"
                        + (cal.get(java.util.Calendar.MONTH) + 1) + "月";
            }
        } else {
            labels = new String[6];
            for (int i = 0; i < 6; i++) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                labels[i] = (cal.get(java.util.Calendar.YEAR) - i) + "年";
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(month ? "选择月份" : "选择年份")
                .setItems(labels, (d, which) -> {
                    if (month) state.monthOffset = -which;
                    else state.yearOffset = -which;
                    state.scroll = 0f;
                    view.invalidate();
                })
                .show();
    }

    private void showEditor(final Edit edit) {
        double amount = state.edits.containsKey(edit.key)
                ? state.edits.get(edit.key) : edit.def;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(getResources().getDisplayMetrics().density * 20);
        box.setPadding(pad, pad / 2, pad, 0);

        final EditText amountBox = new EditText(this);
        amountBox.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amountBox.setHint("金额");
        amountBox.setText(String.format(Locale.US, "%.2f", amount));
        amountBox.selectAll();
        box.addView(amountBox);

        final EditText countBox;
        if (edit.cntKey != null) {
            int count = state.cnt.containsKey(edit.cntKey)
                    ? state.cnt.get(edit.cntKey) : edit.cntDef;
            TextView tip = new TextView(this);
            tip.setText("笔数");
            tip.setPadding(0, pad / 2, 0, 0);
            box.addView(tip);
            countBox = new EditText(this);
            countBox.setInputType(InputType.TYPE_CLASS_NUMBER);
            countBox.setHint("笔数");
            countBox.setText(String.valueOf(count));
            box.addView(countBox);
        } else {
            countBox = null;
        }

        new AlertDialog.Builder(this)
                .setTitle("修改「" + edit.label + "」")
                .setView(box)
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (d, w) -> {
                    state.edits.remove(edit.key);
                    if (edit.cntKey != null) state.cnt.remove(edit.cntKey);
                    view.invalidate();
                })
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        state.edits.put(edit.key,
                                Math.abs(Double.parseDouble(amountBox.getText().toString().trim())));
                    } catch (Exception ignored) {
                        Toast.makeText(this, "金额无效，已忽略", Toast.LENGTH_SHORT).show();
                    }
                    if (countBox != null) {
                        try {
                            state.cnt.put(edit.cntKey,
                                    Math.max(0, Integer.parseInt(countBox.getText().toString().trim())));
                        } catch (Exception ignored) {
                            // 笔数无效就不改
                        }
                    }
                    view.invalidate();
                })
                .show();
    }

    /**
     * 日历格里填数字：点一下格子就能输入金额，填进去的数字会用绿色显示，
     * 取代原来的短横线；“清除”会把它退回短横线。
     */
    private void showDayEditor(final String prefix, final int day, String kind) {
        if (day <= 0) return;
        final String key = prefix + day;
        final EditText box = new EditText(this);
        box.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Double cur = state.edits.get(key);
        box.setText(cur == null ? "" : trimNum(cur));
        box.setHint("0.00");
        box.selectAll();
        new AlertDialog.Builder(this)
                .setTitle("填写「" + day + "日" + kind + "」")
                .setView(box)
                .setNegativeButton("取消", null)
                .setNeutralButton("清除", (d, w) -> {
                    state.edits.remove(key);
                    view.invalidate();
                })
                .setPositiveButton("保存", (d, w) -> {
                    String t = box.getText().toString().trim();
                    if (t.isEmpty()) {
                        state.edits.remove(key);
                    } else {
                        try {
                            state.edits.put(key, Math.abs(Double.parseDouble(t)));
                        } catch (Exception ignored) {
                            Toast.makeText(this, "金额无效，已忽略", Toast.LENGTH_SHORT).show();
                        }
                    }
                    view.invalidate();
                })
                .show();
    }

    private static String trimNum(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    /** 城市解锁卡片：数字项走金额编辑器，文字项走文字编辑器。 */
    private void showCityField(String token) {
        if (token.equals("yc.c1")) { showTextEditor("yc.c1", "另一座城市", "恩施土家族苗族…"); return; }
        if (token.equals("yc.c2")) { showTextEditor("yc.c2", "消费最多的城市", "上海"); return; }
        if (token.equals("yc.t1d")) { showTextEditor("yc.t1d", "第一座城市的时间", "01/03"); return; }
        if (token.equals("yc.t2n")) { showTextEditor("yc.t2n", "第二座城市", "恩施土…"); return; }
        if (token.equals("yc.t2d")) { showTextEditor("yc.t2d", "第二座城市的时间", "08/17"); return; }
        if (token.equals("yc.year")) { showTextEditor("yc.year", "年份", "2026"); return; }
        Edit e = editFor(token);
        if (e != null) showEditor(e);
    }

    /** 改一段文字（城市名、交易名称、分类、时间…），留空就恢复默认。 */
    private void showTextEditor(final String key, String label, String def) {
        final EditText box = new EditText(this);
        box.setInputType(InputType.TYPE_CLASS_TEXT);
        String cur = state.texts.containsKey(key) ? state.texts.get(key) : def;
        box.setText(cur);
        box.setSelection(box.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("修改「" + label + "」")
                .setView(box)
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (d, w) -> {
                    state.texts.remove(key);
                    view.invalidate();
                })
                .setPositiveButton("保存", (d, w) -> {
                    String v = box.getText().toString().trim();
                    if (v.isEmpty()) state.texts.remove(key);
                    else state.texts.put(key, v);
                    view.invalidate();
                })
                .show();
    }

    /** 支出方式的图标：从相册里选一张图换上。 */
    private void pickPayIcon(int i) {
        if (i < 0 || i > 2) return;
        payIconRow = i;
        Intent intent;
        if (Build.VERSION.SDK_INT >= 33) {
            intent = new Intent("android.provider.action.PICK_IMAGES");
            intent.setType("image/*");
        } else {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
        }
        try {
            startActivityForResult(intent, REQUEST_PAY_ICON);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开相册", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_PAY_ICON || result != RESULT_OK || data == null
                || data.getData() == null || payIconRow < 0) {
            return;
        }
        Bitmap picture = decode(data.getData());
        if (picture == null) {
            Toast.makeText(this, "这张图片读不出来", Toast.LENGTH_SHORT).show();
            return;
        }
        state.payIcons.put(payIconRow, picture);
        view.invalidate();
    }

    /** 把相册选中的图片按一个合适的大小读进来。 */
    private Bitmap decode(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            options.inSampleSize = 1;
            while (longest / options.inSampleSize > 512) options.inSampleSize *= 2;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** 只改笔数的对话框（收入页的“共 N 笔”）。 */
    private void showCountEditor(final String cntKey, String label, int def) {
        int cur = state.cnt.containsKey(cntKey) ? state.cnt.get(cntKey) : def;
        final EditText box = new EditText(this);
        box.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.setText(String.valueOf(cur));
        box.selectAll();
        new AlertDialog.Builder(this)
                .setTitle("修改「" + label + "」")
                .setView(box)
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (d, w) -> {
                    state.cnt.remove(cntKey);
                    view.invalidate();
                })
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        state.cnt.put(cntKey,
                                Math.max(0, Integer.parseInt(box.getText().toString().trim())));
                    } catch (Exception ignored) { }
                    view.invalidate();
                })
                .show();
    }

    private void scrollPageTo(float target) {
        state.scroll = Math.max(0f, Math.min(screen.maxScroll(state), target));
        view.invalidate();
    }

    // ── PageView ──────────────────────────────────────────────────────────

    private final class PageView extends View {
        PageView() {
            super(BillAnalyticsActivity.this);
            setFocusable(true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / BillAnalyticsScreen.W, getHeight() / BillAnalyticsScreen.H);
            screen.draw(canvas, state);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX() * BillAnalyticsScreen.W / Math.max(1, getWidth());
            float y = event.getY() * BillAnalyticsScreen.H / Math.max(1, getHeight());
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = y;
                    lastY = y;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!moved && Math.abs(y - downY) > TOUCH_SLOP) moved = true;
                    if (moved) scrollPageTo(state.scroll + (lastY - y));
                    lastY = y;
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!moved) act(screen.hitTest(state, x, y));
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
