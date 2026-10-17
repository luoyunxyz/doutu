package com.luoyun.doutu.account;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 服务端「版本推送」的客户端落点（2026-10-17 重做）。
 *
 * <p>以前这段逻辑长在 {@code MainActivity} 里，只在它的 {@code onCreate} 问一次服务端。两个后果：
 *
 * <ol>
 *   <li><b>后台发了新版本，用户把 App 切回前台看不到弹窗</b> —— 必须彻底杀掉进程重进才会出现。
 *       运营在后台点了「发布」，回头在手机上怎么试都没反应，看起来就是「版本推送不起作用」。</li>
 *   <li><b>桌面图标是微信 / 支付宝时更糟</b>：应用一进来就压着小微主页 / 小支主页，
 *       回前台时拿到 {@code onResume} 的是那一页，底下的 MainActivity 根本不会被唤醒。</li>
 * </ol>
 *
 * <p>所以改成一个全局单例，由 {@code DoutuApp} 的 ActivityLifecycleCallbacks 在**每一个**页面
 * resume 时调用（和水印同一个挂载点，理由也一样：本项目有 20 多个 Activity，逐个改必然会漏）。
 * 弹窗直接弹在**当前正在显示的那个页面**上，也就顺带解决了「弹窗被内部页面盖住」的问题。
 *
 * <p>节流 {@value #RECHECK_MS} 毫秒；点过「稍后再说」在本次运行内不再打扰，下次冷启动照常检查。
 */
public final class UpdatePrompt {
    /** 回前台复查的最短间隔：够短，运营刚发的版本切回来就能看到；又不至于每次切前台都请求。 */
    private static final long RECHECK_MS = 60_000L;

    private static long lastCheckAt;
    private static boolean checking;
    /** 用户点了「稍后再说」：本次运行内不再弹（下次冷启动重置）。 */
    private static boolean snoozed;
    private static AlertDialog dialog;
    /** 用户是否点了「立即更新」（用于区分「正常关闭」与「想绕过升级」）。 */
    private static boolean tapped;
    private static boolean forced;

    private UpdatePrompt() {
    }

    /** 后台标了「必须更新」：旧版本作废，不更新进不去。 */
    public static boolean isForced() {
        return forced;
    }

    /** 升级弹窗是否正显示着（别的地方要避免同时再弹一个）。 */
    public static boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    /**
     * 每个页面回到前台都调一次；节流 + 有弹窗在显示 + 已点稍后 → 直接跳过。
     */
    public static void maybeCheck(Activity activity) {
        if (activity == null) return;
        if (snoozed || checking) return;
        if (dialog != null && dialog.isShowing()) return;
        long nowMs = System.currentTimeMillis();
        if (lastCheckAt != 0 && nowMs - lastCheckAt < RECHECK_MS) return;
        lastCheckAt = nowMs;
        check(activity);
    }

    private static void check(final Activity activity) {
        checking = true;
        DoutuApi.versionCheck(activity, "android", com.luoyun.doutu.BuildConfig.VERSION_CODE, result -> {
            checking = false;
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (!result.ok() || result.data == null) return;
            if (!result.data.optBoolean("has_update", false)) {
                // 服务端已经没有更新了（比如版本被下架）：强制状态要撤掉，否则用户被永久锁在升级页
                forced = false;
                return;
            }
            org.json.JSONObject latest = result.data.optJSONObject("latest");
            if (latest == null) return;
            String url = latest.optString("url", "");
            if (url.isEmpty()) return;
            boolean force = latest.optBoolean("force", false);
            forced = force;
            show(activity, latest.optString("version_name", ""), latest.optString("changelog", ""), url, force);
        });
    }

    private static void show(final Activity activity, String versionName, String changelog, final String url, final boolean force) {
        if (dialog != null && dialog.isShowing()) return;
        int pad = dp(activity, 22);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, dp(activity, 18));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(activity, 18));
        box.setBackground(bg);

        TextView title = new TextView(activity);
        title.setText("发现新版本 " + (versionName.isEmpty() ? "" : versionName));
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF2B2B3C);
        box.addView(title);

        if (!changelog.isEmpty()) {
            TextView body = new TextView(activity);
            body.setText("更新内容：\n" + changelog);
            body.setTextSize(15f);
            body.setTextColor(0xFF5A5A6E);
            body.setLineSpacing(dp(activity, 5), 1f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(activity, 12);
            box.addView(body, lp);
        }

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(activity, 18);
        box.addView(row, rowLp);

        TextView update = new TextView(activity);
        update.setText("立即更新");
        update.setGravity(Gravity.CENTER);
        update.setTextSize(16f);
        update.setTextColor(Color.WHITE);
        GradientDrawable upBg = new GradientDrawable();
        upBg.setColor(0xFF6F6AE8);
        upBg.setCornerRadius(dp(activity, 24));
        update.setBackground(upBg);
        update.setLayoutParams(new LinearLayout.LayoutParams(0, dp(activity, 46), 1f));
        row.addView(update);

        if (!force) {
            TextView later = new TextView(activity);
            later.setText("稍后再说");
            later.setGravity(Gravity.CENTER);
            later.setTextSize(16f);
            later.setTextColor(0xFF6B6B7B);
            GradientDrawable laterBg = new GradientDrawable();
            laterBg.setColor(0xFFE8E8EF);
            laterBg.setCornerRadius(dp(activity, 24));
            later.setBackground(laterBg);
            LinearLayout.LayoutParams laterLp = new LinearLayout.LayoutParams(0, dp(activity, 46), 1f);
            laterLp.rightMargin = dp(activity, 10);
            later.setLayoutParams(laterLp);
            row.addView(later, 0);
            later.setOnClickListener(v -> {
                // 本次运行内不再复查，别一直骚扰；下次冷启动照常检查
                snoozed = true;
                dialog.dismiss();
            });
        }

        tapped = false;
        // 刻意让弹窗**可取消**：Android 13+ 的返回键走 OnBackInvoked/onBackPressed，
        // Dialog.setOnKeyListener 收不到。点外面关不掉，按返回键 = 取消弹窗 →
        // 在 onDismiss 里发现「必须更新且没点立即更新」就结束进程，这才是真正拦得住的做法。
        dialog = new AlertDialog.Builder(activity)
                .setView(box)
                .setCancelable(true)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        update.setOnClickListener(v -> {
            tapped = true;
            openInBrowser(activity, url);
        });
        dialog.setOnDismissListener(d -> {
            if (force && !tapped) {
                // 想绕过升级：旧版本作废，直接退出（产品要求「不更新进不去、直接闪退」）
                exitApp(activity);
            }
        });
        dialog.show();
    }

    /** 用系统浏览器打开下载地址（后台「版本推送」里填的那个）。 */
    private static void openInBrowser(Activity activity, String url) {
        try {
            Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(it);
        } catch (Exception e) {
            Toast.makeText(activity, "打不开下载地址：" + url, Toast.LENGTH_LONG).show();
        }
    }

    /** 直接结束整个进程：用于「必须更新」时用户想绕过升级的场景。 */
    private static void exitApp(Activity activity) {
        try {
            activity.finishAffinity();
        } catch (Exception ignored) {
            // 忽略
        }
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
