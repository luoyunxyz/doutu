package com.luoyun.doutu.features.icon;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 「修改应用图标」那一格拉起来的底部弹窗。
 *
 * <p>弹窗从屏幕底下起来：顶上一行把手，一行标题 修改应用图标，一行说明写清楚换了之后会发生
 * 什么；下面是四行，每一行都是「预览图标 + 软件名」贴在一起的一组 —— 支付宝（alipay.png）、
 * 支付宝（hkalipay.png）、微信（wechat.png）、豆图（logo.png）。当前用的那一行右边带一个蓝勾，
 * 名字也用蓝色，一眼能看出现在挂的是哪一个。
 *
 * <p>点一行不直接换，先弹一个确认窗问「是否切换为XXX？」，确认了才真去换。换完名字和图一起变，
 * 因为清单里那四个别名各自就带着自己的一份。
 */
public final class AppIconChooser {
    private static final int SHEET_BG = 0xfff7f7f9;
    private static final int CARD = 0xffffffff;
    private static final int INK = 0xff23252b;
    private static final int CAPTION = 0xff939292;
    private static final int ACTIVE = 0xff1478fc;
    private static final int RULE = 0xffececf1;
    private static final int HANDLE = 0xffdcdce1;

    private AppIconChooser() {
    }

    /** 从底部拉起弹窗。已经在这一款上时那行点下去只提示一句，不做无谓的重启组件。 */
    public static void show(Activity activity) {
        // 没有会员只能挂默认图标与默认名称（第 6 条）：连弹窗都不给，先说清楚为什么
        if (!com.luoyun.doutu.account.Session.isActivated()) {
            new AlertDialog.Builder(activity)
                    .setTitle("修改应用图标")
                    .setMessage(com.luoyun.doutu.account.Session.isLoggedIn()
                            ? "修改应用图标是会员功能。\n\n未开通会员时只能使用默认图标与应用名，请联系客服开通会员后再来切换。"
                            : "修改应用图标是会员功能。\n\n请先在「我」页面登录并开通会员，之后才能切换图标与应用名。")
                    .setNegativeButton("知道了", null)
                    .setPositiveButton("去开通", (d, w) -> {
                        // 直接把用户送到「我」页：会员卡的入口就在那里
                        android.content.Intent home = new android.content.Intent(activity, com.luoyun.doutu.MainActivity.class);
                        home.addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        activity.startActivity(home);
                    })
                    .show();
            return;
        }
        float density = activity.getResources().getDisplayMetrics().density;
        int dp = Math.round(density);
        int side = Math.round(12 * density);
        int pad = Math.round(20 * density);

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(pad, Math.round(8 * density), pad, pad + bottomInset(activity));
        sheet.setBackground(round(SHEET_BG, 20 * density));
        sheet.setOnClickListener(v -> {
            // 空白处点一下不关，免得手一滑就没了；要关按返回键或者点上面那层暗的。
        });

        // 顶上那一小条把手
        View handle = new View(activity);
        handle.setBackground(round(HANDLE, 3 * density));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(
                Math.round(38 * density), Math.round(4.5f * density));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = Math.round(16 * density);
        sheet.addView(handle, handleLp);

        TextView title = new TextView(activity);
        title.setText("修改应用图标");
        title.setTextSize(17f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        sheet.addView(title);

        // 启动器上此刻挂着的那个名字，读系统的那一份，和桌面写的一致
        String showing = AppIconSwitcher.launcherLabel(activity);
        TextView hint = new TextView(activity);
        hint.setText("点一个图标，启动器上的图标和名称会一起换过去"
                + (showing.isEmpty() ? "" : "（当前：" + showing + "）"));
        hint.setTextSize(12.5f);
        hint.setTextColor(CAPTION);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = 5 * dp;
        sheet.addView(hint, hintLp);

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackground(round(CARD, 16 * density));
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        listLp.topMargin = 16 * dp;
        sheet.addView(list, listLp);

        AppIcon current = AppIconSwitcher.current(activity);
        AppIcon[] icons = AppIcon.values();
        for (int i = 0; i < icons.length; i++) {
            AppIcon icon = icons[i];
            boolean chosen = icon == current;
            if (i > 0) list.addView(divider(activity, density));
            list.addView(row(activity, dialog, icon, chosen, density), rowParams());
        }

        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            // 上面那层压暗一点，看得出这是个盖在页面上的弹窗
            window.setDimAmount(0.45f);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        // 贴到屏幕最底：左右各让出一条边，底下的手势条由 padding 让出去
        int width = Math.max(0, activity.getResources().getDisplayMetrics().widthPixels - 2 * side);
        if (window != null) window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.show();
        Window shown = dialog.getWindow();
        if (shown != null) {
            shown.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            shown.setGravity(Gravity.BOTTOM);
        }
    }

    /** 四行里的一行：左边预览图，右边软件名，选中的那一行名字变蓝并在右边点一个勾。 */
    private static View row(Activity activity, Dialog dialog, AppIcon icon, boolean chosen,
                            float density) {
        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(Math.round(16 * density), Math.round(12 * density),
                Math.round(16 * density), Math.round(12 * density));
        line.setBackgroundColor(CARD);

        ImageView preview = new ImageView(activity);
        preview.setImageResource(icon.preview);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // 这一格画的就是用户挑完以后桌面上挂着的那张图，按原样显示，不裁不圆
        line.addView(preview, new LinearLayout.LayoutParams(
                Math.round(46 * density), Math.round(46 * density)));

        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsLp.leftMargin = Math.round(14 * density);
        labels.setLayoutParams(labelsLp);
        line.addView(labels);

        TextView name = new TextView(activity);
        name.setText(icon.label);
        name.setTextSize(16f);
        name.setTypeface(chosen ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        name.setTextColor(chosen ? ACTIVE : INK);
        labels.addView(name);

        TextView file = new TextView(activity);
        file.setText(chosen ? "当前使用中" : "点击切换");
        file.setTextSize(12f);
        file.setTextColor(chosen ? ACTIVE : CAPTION);
        LinearLayout.LayoutParams fileLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fileLp.topMargin = Math.round(3 * density);
        labels.addView(file, fileLp);

        TextView tick = new TextView(activity);
        tick.setText(chosen ? "✓" : "›");
        tick.setTextSize(chosen ? 18f : 16f);
        tick.setTextColor(chosen ? ACTIVE : 0xffb9bcc2);
        tick.setGravity(Gravity.CENTER);
        line.addView(tick, new LinearLayout.LayoutParams(
                Math.round(22 * density), ViewGroup.LayoutParams.WRAP_CONTENT));

        line.setOnClickListener(v -> confirm(activity, dialog, icon, chosen));
        return line;
    }

    /** 点了一行之后那一下确认：是否切换为XXX？。 */
    private static void confirm(Activity activity, Dialog sheet, AppIcon icon, boolean chosen) {
        if (chosen) {
            android.widget.Toast.makeText(activity, "当前已经是" + icon.label + "图标", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("修改应用图标")
                .setMessage("是否切换为" + icon.label + "？")
                .setNegativeButton("取消", null)
                .setPositiveButton("切换", (d, which) -> {
                    if (AppIconSwitcher.apply(activity, icon)) {
                        // 换完把弹窗收起来，用户会看到桌面上的图标正在变
                        sheet.dismiss();
                    }
                })
                .show();
    }

    private static LinearLayout.LayoutParams rowParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** 行与行之间那条发丝线：缩进到和上面那行的名字对齐。 */
    private static View divider(Activity activity, float density) {
        View line = new View(activity);
        line.setBackgroundColor(RULE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(0.7f * density)));
        lp.leftMargin = Math.round(76 * density);
        line.setLayoutParams(lp);
        return line;
    }

    private static GradientDrawable round(int color, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    /** 底下那条系统手势条的高度，弹窗内容别压在它上面。 */
    private static int bottomInset(Activity activity) {
        if (Build.VERSION.SDK_INT < 30) return 0;
        WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
        return insets == null ? 0 : insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
    }
}
