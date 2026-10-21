package com.luoyun.doutu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.luoyun.doutu.account.DoutuApi;
import com.luoyun.doutu.account.LoginActivity;
import com.luoyun.doutu.account.Session;
import com.luoyun.doutu.features.icon.AppIconChooser;
import com.luoyun.doutu.features.icon.AppIconSwitcher;
import com.luoyun.doutu.features.wei.WeiHomeActivity;
import com.luoyun.doutu.features.feedback.FeedbackActivity;
import com.luoyun.doutu.features.zhi.AssetActivity;
import com.luoyun.doutu.features.zhi.BalanceActivity;
import com.luoyun.doutu.features.zhi.BillActivity;
import com.luoyun.doutu.features.zhi.HuabeiActivity;
import com.luoyun.doutu.features.zhi.LicaiActivity;
import com.luoyun.doutu.features.zhi.MessageActivity;
import com.luoyun.doutu.features.zhi.PayMsgActivity;
import com.luoyun.doutu.features.zhi.PaySuccessActivity;
import com.luoyun.doutu.features.zhi.SwitchAccountActivity;
import com.luoyun.doutu.features.zhi.ProfileActivity;
import com.luoyun.doutu.features.zhi.ReceiptActivity;
import com.luoyun.doutu.features.zhi.YuebaoActivity;
import com.luoyun.doutu.features.zhi.WithdrawActivity;
import com.luoyun.doutu.features.zhi.XiaohebaoActivity;
import com.luoyun.doutu.features.zhi.ZhiHomeActivity;
import com.luoyun.doutu.features.zhima.ZhimaCreditActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main shell. Home service pages retain their existing reference artwork; the two utility pages
 * are drawn entirely from native text, paths, shapes and interactive controls.
 */
public final class MainActivity extends Activity {
    private static final int PAGE_WEI = 0, PAGE_ZHI = 1, PAGE_DOU = 2, PAGE_Q = 3;
    private static final float W = 1080f, H = 2388f;
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // 桌面图标是哪一个（TermsActivity 传过来）：决定这一趟落在小支主页还是小微主页
        Intent launchIntent = getIntent();
        entryTarget = launchIntent == null ? null : launchIntent.getStringExtra(TermsActivity.EXTRA_ENTRY_ICON);
        NativeView view = new NativeView();
        this.nativeView = view;
        FrameLayout root = new FrameLayout(this);
        root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        configureWindow(getWindow());
        view.post(() -> configureWindow(getWindow()));
        // Start parsing and rendering the bundled WeChat page before it is requested.
        WeiHomeActivity.preload(this, root);
        // 首屏就拉一次公开配置：首页横幅（后台公告）不该等到用户进登录页才生效
        refreshPublicConfig();
        // 版本推送优先：必须更新时它要让用户连界面都进不去，所以先发请求、先弹
        checkAppUpdate();
        // 弹窗公告（type=1）：先用本地缓存的公告弹，配置回来后若内容有更新会再弹一次
        view.postDelayed(this::maybeShowPopup, 1500);
        // 桌面图标决定落地页：**onCreate 里就跳**，不等版本检查、也不等第一帧 ——
        // 等它回来应用主页已经明晃晃地显示好几百毫秒了（用户反馈的「先闪一下主页」）。
        // 升级弹窗晚一点弹出来也没关系：checkAppUpdate 发现要弹时会把已经跳过去的内部页面弹掉。
        routeLauncherEntry();
        // 没开通会员时只能用默认图标与默认名字（见 AppIconSwitcher.enforceMembership）
        AppIconSwitcher.enforceMembership(this, false);
    }

    /** 首页横幅轮播心跳：平时低频重绘，长条文字上下滚动时切成高频（约 30fps）。 */
    private final Runnable bannerTick = new Runnable() {
        @Override
        public void run() {
            if (nativeView != null && nativeView.getVisibility() == View.VISIBLE) {
                nativeView.invalidate();
                // 滚动动画期间 33ms 一帧，静止时 250ms 一帧（省电）
                nativeView.postDelayed(this, Session.hintRolling() ? 33 : 250);
            }
        }
    };

    /**
     * 拉公开配置（MASTER_KEY 验签，不需要登录）：首页横幅公告、水印文案、合规文案。
     * 每次回到前台最多 60 秒一次，避免频繁请求。
     */
    /** 正在显示的弹窗（避免同一时刻弹两个） */
    private android.app.AlertDialog popupDialog;

    /**
     * 后台「公告管理」里 type=1 的弹窗公告：进入 App 弹一次。
     * 同一条内容只弹一次（后台改了标题或内容会再弹一次），避免每次启动都骚扰用户。
     * 样式按 App 主色手搓：圆角白卡 + 紫色标题 + 深灰正文 + 紫色圆角按钮。
     */
    private void maybeShowPopup() {
        if (popupDialog != null && popupDialog.isShowing()) return;
        // 已经因为「必须更新」弹了升级窗口：别再叠一个公告弹窗（而且用户也点不到）
        // 升级弹窗由全局的 UpdatePrompt 负责显示；它正弹着的时候别再叠一个公告弹窗
        if (com.luoyun.doutu.account.UpdatePrompt.isShowing()) return;
        Session.Popup notice = Session.pendingPopup();
        if (notice == null) return;

        int pad = dp(22);
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, dp(18));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(18));
        box.setBackground(bg);

        if (!notice.title.isEmpty()) {
            android.widget.TextView title = new android.widget.TextView(this);
            title.setText(notice.title);
            title.setTextSize(18f);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setTextColor(0xFF2B2B3C);
            box.addView(title);
        }
        if (!notice.content.isEmpty()) {
            android.widget.TextView content = new android.widget.TextView(this);
            content.setText(notice.content);
            content.setTextSize(15f);
            content.setTextColor(0xFF5A5A6E);
            content.setLineSpacing(dp(5), 1f);
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(12);
            box.addView(content, lp);
        }

        android.widget.TextView ok = new android.widget.TextView(this);
        ok.setText("我知道了");
        ok.setGravity(android.view.Gravity.CENTER);
        ok.setTextSize(16f);
        ok.setTextColor(0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
        btnBg.setColor(0xFF6F6AE8);
        btnBg.setCornerRadius(dp(24));
        ok.setBackground(btnBg);
        android.widget.LinearLayout.LayoutParams lp2 =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        lp2.topMargin = dp(18);
        box.addView(ok, lp2);

        popupDialog = new android.app.AlertDialog.Builder(this)
                .setView(box)
                .setCancelable(true)
                .create();
        if (popupDialog.getWindow() != null) {
            popupDialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        ok.setOnClickListener(v -> popupDialog.dismiss());
        popupDialog.setOnDismissListener(d -> {
            Session.markPopupShown(notice);   // 关掉才算「看过」，中途被杀进程下次还会弹
            popupDialog = null;
        });
        popupDialog.show();
    }

    /** 桌面图标带来的落地页标记（"zhi" = 小支主页，其余/为空 = 小微主页），见 TermsActivity */
    private String entryTarget;
    /** 落地页是否已经跳过（只会跳一次，返回主页后不再自动跳回去） */
    private boolean entryRouted;

    /**
     * 版本推送：换成全局的 {@link com.luoyun.doutu.account.UpdatePrompt}。
     *
     * <p>原来这段逻辑长在这里，只在 onCreate 问一次 —— 后台发了新版本，用户切回前台看不到弹窗；
     * 落地页是微信/支付宝时更糟（回前台的是那一页，这个 Activity 根本收不到 onResume）。
     * 现在由 DoutuApp 的 ActivityLifecycleCallbacks 在**每个**页面 resume 时统一检查。
     */
    private void checkAppUpdate() {
        com.luoyun.doutu.account.UpdatePrompt.maybeCheck(this);
    }

    /** 强制升级中：由 UpdatePrompt 维护，这里只读。 */
    private boolean isUpdateForced() {
        return com.luoyun.doutu.account.UpdatePrompt.isForced();
    }

    /**
     * 桌面图标决定落地页（第 7 条）：
     *   支付宝 / 支付宝（香港）图标 → 小支主页
     *   微信图标                   → 小微主页
     *   豆图图标以及其它一切入口     → 就留在应用主页
     *
     * <p>**第一帧就跳**（{@code view.post}），不等版本检查：一旦等网络往返回来，
     * 应用主页已经显示好几百毫秒了，用户看到的就是「先闪一下主页再进去」。
     * 这里仍然先起 MainActivity 再压一层目标页，理由是：
     *   · 返回键天然退回应用主页（不用每个内页自己实现一套）；
     *   · 「小微主页」的 WebView 预热本来就在 MainActivity.onCreate 里做，留着它页面才开得快。
     */
    private void routeLauncherEntry() {
        if (entryRouted) return;
        // 豆图 / 其它图标 / 被页面内返回键拉回来（不带标记）：就停在应用主页
        if (entryTarget == null || entryTarget.isEmpty() || "home".equals(entryTarget)) {
            entryRouted = true;
            return;
        }
        // 已经知道「必须更新」：不跳了，让升级弹窗拦住
        if (isUpdateForced()) return;
        entryRouted = true;
        if ("zhi".equals(entryTarget)) {
            startActivity(ZhiHomeActivity.intentFor(this));
            overridePendingTransition(0, 0);
        } else {
            // 小微主页：带上 #/wechat 这一层索引，这样页内返回键会直接退回应用主页，
            // 而不是走 WebView 历史（历史里没有别的页面，会看起来「返回没反应」）。
            startActivity(WeiHomeActivity.intentFor(this, WeiHomeActivity.ROUTE_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            overridePendingTransition(0, 0);
        }
    }

    /** 直接结束整个进程：用于「必须更新」时用户想绕过升级的场景。 */
    private void exitApp() {
        try {
            finishAffinity();
        } catch (Exception ignored) {
            // 忽略
        }
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    /** dp → px */
    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }


    private long lastConfigSync;

    private void refreshPublicConfig() {
        if (System.currentTimeMillis() - lastConfigSync < 60000) return;
        lastConfigSync = System.currentTimeMillis();
        com.luoyun.doutu.account.DoutuApi.config(this, result -> {
            if (!result.ok() || result.data == null) return;
            Session.applyConfig(result.data);
            // 玩法配置是启动后才拉回来的，而娱乐页在 onCreate 就按当时的配置建好了版式 ——
            // 这里比对版本号，变了就重建一次（后台改完玩法不用重启 App）。
            if (nativeView != null) nativeView.applyPlaybookIfChanged();
            redrawNative();
            // 配置里可能刚多了一条弹窗公告（type=1），拉完立刻检查
            maybeShowPopup();
        });
    }

    private NativeView nativeView;
    private long lastAccountSync;

    @Override
    protected void onResume() {
        super.onResume();
        if (nativeView != null) nativeView.rebuildTools();
        // 不做登录门禁：默认就能进应用。
        // 会员只决定「有没有水印」——未开通时整页叠加遮罩，用户可以选择不开会员继续用。
        // 登录/注册入口在「我」页顶部的头像与名字上。
        if (Session.isLoggedIn()) {
            refreshAccount(false);
            com.luoyun.doutu.account.AvatarStore.load(this, this::redrawNative);
        } else {
            com.luoyun.doutu.account.AvatarStore.clear();
        }
        com.luoyun.doutu.account.WatermarkOverlay.attachIfNeeded(this);
        // 登录 / 退出 / 会员到期之后回来，图标与名称要跟着会员状态走（没会员只能用默认款）
        AppIconSwitcher.enforceMembership(this, false);
        // 回到前台顺手刷新配置并启动横幅轮播心跳（横幅文案可能被后台改了时间段）
        refreshPublicConfig();
        if (nativeView != null) {
            nativeView.removeCallbacks(bannerTick);
            nativeView.postDelayed(bannerTick, 1000);
        }
        redrawNative();
        // 版本推送：后台刚发的版本，用户切回前台就该看到弹窗（不必杀进程重进）。
        // 其它页面由 DoutuApp 的生命周期回调统一检查（落地页是微信/支付宝时，回前台的是那一页）。
        checkAppUpdate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nativeView != null) nativeView.removeCallbacks(bannerTick);
    }

    /** 重画主界面（头像下好了、状态变了都要走它）。 */
    private void redrawNative() {
        if (nativeView != null) nativeView.invalidate();
    }

    /**
     * 「更换头像」面板里「从本地上传」选完图会回到这里（2026-10-17）。
     * 面板自己拿着回调，这里只负责把结果递回去。
     */
    @Override
    protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (com.luoyun.doutu.account.AvatarUpload.deliver(this, request, result, data)) redrawNative();
    }

    /**
     * 同步会员状态（已登录才调用）。
     * 拉到新状态后重画本页，并让水印遮罩跟着出现/消失。
     */
    private void refreshAccount(boolean force) {
        long nowMs = System.currentTimeMillis();
        if (!force && nowMs - lastAccountSync < 15000) return;
        lastAccountSync = nowMs;
        DoutuApi.status(this, result -> {
            if (result.code == 2005) {
                // 会话失效：只清本地会话，不强行弹登录页（用户随时可以在「我」页重新登录）
                Session.clear();
                if (nativeView != null) nativeView.invalidate();
                com.luoyun.doutu.account.WatermarkOverlay.attachIfNeeded(this);
                return;
            }
            if (result.data == null) return;
            Session.updateUser(result.user());
            String overlay = result.data.optString("watermark_overlay_text", null);
            if (overlay != null && !overlay.isEmpty()) Session.setOverlayText(overlay);
            if (nativeView != null) nativeView.invalidate();
            // 会员状态变了就重挂一次遮罩，保证水印立刻消失/出现
            com.luoyun.doutu.account.WatermarkOverlay.attachIfNeeded(this);
        });
    }

    /** 打开登录/注册页（「我」页用户行的未登录入口）。 */
    private void openAccount() {
        LoginActivity.open(this);
    }

    /** 打开意见反馈页（「我 → 求新功能」）。 */
    private void openFeedback() {
        FeedbackActivity.open(this);
    }

    /**
     * 分享好友（「微 → 分享好友」与「我 → 邀请好友」共用一套）：
     * 先把后台配置的链接写进剪贴板，再弹窗告诉用户已复制（弹窗里也能再复制一次）。
     */
    private void shareToFriend() {
        String link = Session.shareLink();
        copyToClipboard(link);
        new AlertDialog.Builder(this)
                .setTitle("分享好友")
                .setMessage(Session.shareTip() + "\n\n" + link)
                .setNegativeButton("关闭", null)
                .setPositiveButton("再复制一次", (d, w) -> {
                    copyToClipboard(link);
                    Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /** 往系统剪贴板里放一段纯文本（失败不抛：个别 ROM 在后台会拒绝写剪贴板）。 */
    private void copyToClipboard(String text) {
        try {
            android.content.ClipboardManager manager =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(android.content.ClipData.newPlainText("doutu_share", text));
            }
        } catch (Exception ignored) {
            // 写不进去也不影响下面那句提示
        }
    }

    /** 打开账号设置页（已登录时点用户行进入）。 */
    private void openSettings() {
        com.luoyun.doutu.account.SettingsActivity.open(this);
    }

    /** 打开「一键去水印」解析页（娱乐页那一格）。 */
    private void openParse() {
        com.luoyun.doutu.account.ParseActivity.open(this);
    }

    /**
     * 打开「模拟来电」——**先进设置页**，不直接开响：选来电人（从通讯录）、换头像、改名字、
     * 挑铃声（本地音频或内置）、定什么时候响（立刻 / 多少分钟后 / 指定钟点）。
     *
     * <p>真正那场来电还是「微」模块里原来那一页（循环铃声 + 接听 / 挂断 / 免提），设置页只是把
     * 「谁打来的、什么头像、放哪首歌」交给它，所以看起来和以前完全一样。
     */
    private void openWeiCall() {
        com.luoyun.doutu.features.call.CallSetupActivity.open(this);
        overridePendingTransition(0, 0);
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) configureWindow(getWindow());
    }

    private static void configureWindow(Window w) {
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 28)
            w.getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = w.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    /**
     * 从桌面又点了一次图标（TermsActivity 用 CLEAR_TOP 复用本实例）：重新按这一次的图标决定落地页。
     *
     * <p>**只有带着落地页标记的 Intent 才重新跳**。页面内「返回应用主页」
     * （{@code WeiHomeActivity.showAppHomeBehindWei()}）也是把自己的主页挪到前面，但它不带这个标记 ——
     * 要是照样跳一次，用户从「小微主页」返回就会立刻被弹回去，返回键看起来像失灵。
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent == null || !intent.hasExtra(TermsActivity.EXTRA_ENTRY_ICON)) return;
        entryTarget = intent.getStringExtra(TermsActivity.EXTRA_ENTRY_ICON);
        entryRouted = false;
        routeLauncherEntry();
    }

    @Override
    public void onBackPressed() {
        // 「必须更新」期间返回键一律按「想绕过升级」处理：旧版本作废，直接退出
        if (isUpdateForced()) {
            exitApp();
            return;
        }
        View content = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        NativeView view = findNativeView(content);
        if (view != null && view.detailTitle != null) {
            view.detailTitle = null;
            view.invalidate();
        } else super.onBackPressed();
    }

private NativeView findNativeView(View candidate) {
        if (candidate instanceof NativeView) return (NativeView) candidate;
        if (candidate instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) candidate;
            for (int i = 0; i < group.getChildCount(); i++) {
                NativeView found = findNativeView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * 支模块宫格里某一格对应的界面。行 0-3 自上而下，列 0-3 自左而右：
     * <pre>
     * 小支主页  总资产   余额     余额宝
     * 花呗      支付消息 支付成功  自定义账单
     * 个人信息  切换账号 财富页    收款记录
     * 余额提现  转账银行卡 小荷包  芝麻信用
     * </pre>
     * 模块里还没有对应页面的格子（小荷包）暂时返回 {@code null}，点了仍旧走原来的演示提示。
     * 余额提现 和 转账银行卡 走的是同一个提现页。
     */
    private Intent zhiGridIntent(int row, int col) {
        switch (row * 4 + col) {
            case 0: return ZhiHomeActivity.intentFor(this);      // 小支主页
            case 1: return AssetActivity.intentFor(this);        // 总资产
            case 2: return BalanceActivity.intentFor(this);      // 余额
            case 3: return YuebaoActivity.intentFor(this);       // 余额宝
            case 4: return HuabeiActivity.intentFor(this);       // 花呗
            case 5: return PayMsgActivity.intentFor(this);       // 支付消息 → 支付消息页
            case 6: return PaySuccessActivity.intentFor(this);   // 支付成功
            case 7: return BillActivity.intentFor(this);         // 自定义账单 → 小支主页 账单页
            case 8: return ProfileActivity.intentFor(this);      // 个人信息
            case 9: return SwitchAccountActivity.intentFor(this); // 切换账号
            case 10: return LicaiActivity.intentFor(this);       // 财富页 → 理财
            case 11: return ReceiptActivity.intentFor(this);     // 收款记录 → 商家收款记录页
            case 12: return WithdrawActivity.intentFor(this);    // 余额提现 → 提现页（银行卡选择 + 数字键盘）
            case 13: return WithdrawActivity.intentFor(this);    // 转账银行卡 → 同一个提现页
            case 14: return XiaohebaoActivity.intentFor(this);   // 小荷包 → 小荷包页
            case 15: return ZhimaCreditActivity.intentFor(this); // 芝麻信用
            default: return null;
        }
    }

static final class FeatureRow {
        /** 稳定标识（服务端下发）：决定点进去哪个页面。改标题/换语言都不会串功能。 */
        final String key;
        final String title, subtitle;
        /** 渲染成开关的行（如「撩妹加V」） */
        final boolean switchRow;
        boolean enabled;

        FeatureRow(String key, String title, String subtitle) {
            this(key, title, subtitle, false);
        }

        FeatureRow(String key, String title, String subtitle, boolean switchRow) {
            this.key = key;
            this.title = title;
            this.subtitle = subtitle;
            this.switchRow = switchRow;
        }
    }

    static final class Section {
        final String title;
        /** 强调色键：blue / purple / orange（服务端下发），决定图标底色 */
        final String accent;
        final List<FeatureRow> rows = new ArrayList<>();

        Section(String title, FeatureRow... rows) {
            this(title, "", rows);
        }

        Section(String title, String accent, FeatureRow... rows) {
            this.title = title;
            this.accent = accent == null ? "" : accent;
            this.rows.addAll(Arrays.asList(rows));
        }
    }

    /**
     * 娱乐页里的一行摆在哪。画的时候和点的时候读的是同一份，所以改了版式不会出现
     * 「看到的位置」和「点得到的位置」对不上。
     */
    static final class EntertainmentRow {
        final FeatureRow row;
        /** 属于第几个分区（决定同一张卡片的分组与标题）。 */
        final int sectionIndex;
        /** 画哪个字形。认的是服务端下发的 key，不是第几行 —— 增删行不会把图标串掉。 */
        final int glyph;
        /** 这个分区的强调色与底纹色：由服务端 accent 决定，**在这里就算好**，避免拿序号去查表越界。 */
        final int accent, tint;
        final float top, bottom;
        /** 是不是所在卡片的第一行 —— 第一行头顶不画分隔线。 */
        final boolean firstInSection;

        EntertainmentRow(FeatureRow row, int sectionIndex, int glyph, int accent, int tint,
                         float top, float bottom, boolean firstInSection) {
            this.row = row;
            this.sectionIndex = sectionIndex;
            this.glyph = glyph;
            this.accent = accent;
            this.tint = tint;
            this.top = top;
            this.bottom = bottom;
            this.firstInSection = firstInSection;
        }
    }

    private final class NativeView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Rect src = new Rect();
        private final RectF dst = new RectF();
        private final int[] homeRefs = {R.drawable.home_reference, R.drawable.zhi_reference, R.drawable.dou_reference, R.drawable.q_reference};
        /** 四个分段各自高亮的那一块（微 / 支 / 抖 / Q），换页时只贴这一块。 */
        private final int[] segmentRefs = {R.drawable.seg_wei, R.drawable.seg_zhi, R.drawable.seg_dou, R.drawable.seg_q};
        /**
         * 顶上那一段（横幅 + 分段控件）四个分段页共用同一张，切页时不跟着换 ——
         * 换的只有下面那块功能区，和分段控件上那一格高亮。
         */
        private static final float SEGMENT_TOP = 736f;
        private static final float SEGMENT_BOTTOM = 890f;
        private static final float[] SEGMENT_LEFT = {53f, 306f, 558f, 811f};
        private static final float[] SEGMENT_WIDTH = {215f, 215f, 216f, 215f};
        private static final float TOP_HEIGHT = 912f;
        private Bitmap homeBitmap;
        private Bitmap topBitmap;
        private final Bitmap[] segmentBitmaps = new Bitmap[4];
        private int servicePage = PAGE_WEI, bottom = 0;
        /** 工具页独立使用 bottom=4，保留 0/1/2 原有语义。 */
        private float toolsBottom;
        /**
         * 「工具」页的版式数据。整页照 歪点点 4.0.5.39 真机截图重建：
         * 黄色标题栏 + 「替换头像」单行 + 六组白底分组（小微 / 阿宝 / 小Q / 其它 /
         * 团队相册 / 视频），每组 4 列图标网格，底部「更多功能及教程」。
         * 所有坐标都是 1080×2388 设计坐标（参考图 1440 宽 × 0.75），画和点共用同一份。
         */
        private static final float TOOL_BAR_H = 234f;
        private static final float TOOL_ROW_H = 258f;
        private static final float TOOL_HDR_LOGO = 168f;
        private static final float TOOL_LOGO = 132f;
        private static final float TOOL_CELL_W = 264f;
        private static final float TOOL_CELL_H = 272.25f;
        private static final float TOOL_LEFT = 12f;
        /** 分组标题行顶 → 第一行图标顶（参考图 498px）。 */
        private static final float TOOL_GRID_DY = 373.5f;
        private static final float TOOL_LABEL_DY = 150f;
        private static final float TOOL_LABEL_H = 83.25f;
        private static final float TOOL_TUT_W = 301.5f;
        private static final float TOOL_TUT_H = 80.25f;
        private static final float TOOL_DARK_H = 127.5f;
        private static final float TOOL_TEAM_H = 168f;
        private static final int TOOL_BAR_COLOR = 0xfff9db61;
        private static final int TOOL_ACCENT = 0xffff7a1a;
        private boolean toolsDark;
        private final java.util.List<ToolSection> toolSections = new java.util.ArrayList<>();
        private final java.util.HashMap<String, Bitmap> toolIconCache = new java.util.HashMap<>();
    private final java.util.HashMap<String, Rect> toolInkCache = new java.util.HashMap<>();
        private float scroll = 0, downY;
        private float downX;
        private long downAt;
        private boolean moved;
        private final List<Section> sections = new ArrayList<>();

        // 娱乐页（更多玩法）的版式。这一页整页是手画的，行、卡片、分区标题都照下面这几个数排出来，
        // 改版式只动这里；点击热区读的是 buildEntertainmentLayout() 算出来的同一份坐标。
        private static final float HERO_TOP = 112f;
        private static final float HERO_BOTTOM = 372f;
        private static final float SECTION_HEADER_H = 96f;
        private static final float SECTION_GAP = 44f;
        /** 一行的高度。三个分区各两行，用这个数刚好把一屏铺满、底下不留大片空白。 */
        private static final float ROW_H = 210f;
        private static final float CARD_LEFT = 32f;
        private static final float CARD_RIGHT = 1048f;
        private static final float CARD_RADIUS = 30f;
        private static final float CONTENT_BOTTOM = 2145f;
        private static final int PAGE_BG = 0xfff5f6fa;
        private static final int CARD_EDGE = 0xffeef0f6;
        private static final int CARD_HAIRLINE = 0xfff1f2f7;
        private static final int INK = 0xff1d1f26;
        private static final int SUB_INK = 0xff8b90a0;
        private static final int BRAND = 0xff4a7bf7;
        private static final int BRAND_2 = 0xff8b5cf6;
        /** 三个分区各自的强调色：图标块的淡底 + 里面那个字形的颜色。 */
        private final int[] sectionAccent = {0xff4a7bf7, 0xff7c5cf6, 0xffff7a45};
        /** 客户端真正实现了的玩法 key：服务端下发的行认不出来就不显示，避免点了没反应。 */
        private static final java.util.Set<String> KNOWN_HANDLERS = new java.util.HashSet<>(Arrays.asList(
                "parse", "order", "call", "shutdown", "sound", "tease", "avatar", "balance"));
        private final int[] sectionTint = {0xffeaf1ff, 0xffefeaff, 0xfffff0e8};
        /** 娱乐页每一行的坐标，画和点共用。 */
        private final List<EntertainmentRow> entertainmentRows = new ArrayList<>();
        private float entertainmentBottom;

        NativeView() {
            super(MainActivity.this);
            setFocusable(true);
            setBackgroundColor(Color.rgb(247, 247, 249));
            loadHome();
            loadEntertainmentFromServer();
            buildEntertainmentLayout();
            toolsDark = getContext().getSharedPreferences("doutu_tools", 0)
                    .getBoolean("dark_mode", false);
            buildToolsLayout();
            setContentDescription("豆图助手主页面");
        }

        /** 这份版式是用哪一版玩法配置建出来的；服务端换版本号就重建一次。 */
        private int playbookVersionUsed = -1;

        /**
         * 服务端玩法配置到了（或版本变了）就重建娱乐页。
         *
         * <p>为什么需要它：启动时 `/config` 还在路上，娱乐页已经按当时的配置（可能是空）建好了版式。
         * 没有这个钩子，用户要重启一次才能看到服务端下发的玩法 —— 那「服务端下发」就成了摆设。
         */
        void applyPlaybookIfChanged() {
            if (com.luoyun.doutu.account.Session.playbook() == null) return;
            int version = com.luoyun.doutu.account.Session.playbookVersion();
            if (version == playbookVersionUsed && !sections.isEmpty()) return;
            playbookVersionUsed = version;
            sections.clear();
            loadEntertainmentFromServer();
            buildEntertainmentLayout();
            invalidate();
        }

        /**
         * 娱乐页的内容从服务端下发（2026-10-17）。
         *
         * <p>为什么这么做：这一页的分区、标题、副标题、顺序、开关、点击去哪，以前全写死在客户端 ——
         * 是整包里最容易被整块抄走的部分。现在它们由 `/config` 的 `playbook.entertainment` 决定，
         * 抄走 APK 只拿到渲染壳；运营改文案、下线某个玩法、调整顺序都不用发版。
         *
         * <p>退回策略：服务端还没下发过（首次冷启动且离线）时，只给一条「一键去水印」——
         * 也就是**把有辨识度的玩法文案留在服务端**，而不是在包里留一份完整副本。
         */
        private void loadEntertainmentFromServer() {
            org.json.JSONObject book = com.luoyun.doutu.account.Session.playbook();
            if (book == null) {
                sections.add(new Section("实用功能", "blue",
                        new FeatureRow("parse", "短视频一键去水印", "支持抖音、快手、小红书、B站等100+平台")));
                return;
            }
            org.json.JSONArray list = book.optJSONArray("sections");
            if (list == null) return;
            for (int i = 0; i < list.length(); i++) {
                org.json.JSONObject sec = list.optJSONObject(i);
                if (sec == null) continue;
                String title = sec.optString("title", "").trim();
                if (title.isEmpty()) continue;
                List<FeatureRow> rows = new ArrayList<>();
                org.json.JSONArray rawRows = sec.optJSONArray("rows");
                if (rawRows != null) {
                    for (int r = 0; r < rawRows.length(); r++) {
                        org.json.JSONObject item = rawRows.optJSONObject(r);
                        if (item == null) continue;
                        // enabled=false 的行直接不出现：后台下线一个玩法不用发版
                        if (item.has("enabled") && !item.optBoolean("enabled", true)) continue;
                        String key = item.optString("key", "").trim();
                        String rowTitle = item.optString("title", "").trim();
                        if (key.isEmpty() || rowTitle.isEmpty()) continue;
                        if (!KNOWN_HANDLERS.contains(key)) continue;   // 客户端认不出来的行不显示
                        FeatureRow row = new FeatureRow(key, rowTitle, item.optString("subtitle", ""),
                                item.optBoolean("switch", false));
                        if (row.switchRow) {
                            row.enabled = com.luoyun.doutu.features.play.PlayStore.cameraBroken();
                        }
                        rows.add(row);
                    }
                }
                if (!rows.isEmpty()) {
                    Section section = new Section(title, sec.optString("accent", ""),
                            rows.toArray(new FeatureRow[0]));
                    sections.add(section);
                }
            }
            if (sections.isEmpty()) {
                sections.add(new Section("实用功能", "blue",
                        new FeatureRow("parse", "短视频一键去水印", "支持抖音、快手、小红书、B站等100+平台")));
            }
        }

        /** 把上面那几个数摊成一行行的格子；页面短于一屏，所以滚动上限也在这里算出来。 */
        private void buildEntertainmentLayout() {
            entertainmentRows.clear();
            float y = HERO_BOTTOM + 40f;
            for (int s = 0; s < sections.size(); s++) {
                Section section = sections.get(s);
                int accent = accentFor(section, s);
                int tint = tintFor(accent);
                y += SECTION_HEADER_H;
                for (int r = 0; r < section.rows.size(); r++) {
                    FeatureRow row = section.rows.get(r);
                    entertainmentRows.add(new EntertainmentRow(row, s, glyphFor(row.key), accent, tint,
                            y, y + ROW_H, r == 0));
                    y += ROW_H;
                }
                y += SECTION_GAP;
            }
            entertainmentBottom = y - SECTION_GAP;
        }

        /**
         * 分区强调色：优先用服务端下发的 `accent`（blue/purple/orange），
         * 认不出来就按序号循环取 —— 服务端加几个分区也不会把客户端搞越界。
         */
        private int accentFor(Section section, int index) {
            String key = section == null ? "" : section.accent;
            if ("blue".equals(key)) return sectionAccent[0];
            if ("purple".equals(key)) return sectionAccent[1];
            if ("orange".equals(key)) return sectionAccent[2];
            return sectionAccent[Math.abs(index) % sectionAccent.length];
        }

        /** 图标底纹：强调色的极淡版本（原来按分区序号硬编码的那三块） */
        private int tintFor(int accent) {
            for (int i = 0; i < sectionAccent.length; i++) {
                if (sectionAccent[i] == accent) return sectionTint[i];
            }
            return sectionTint[0];
        }

        /** 这一行画哪个字形（见 {@link #drawEntertainmentIcon}）；认 key，删行加行都不会串。 */
        private static int glyphFor(String key) {
            switch (key) {
                case "order": return 1;
                case "avatar": return 2;
                case "call": return 3;
                case "shutdown": return 4;
                case "sound": return 5;
                case "balance": return 6;
                case "tease": return 7;
                default: return 0; // parse：短视频一键去水印
            }
        }

        /** 当前这一页还能往下滚多少。娱乐页的内容比一屏短，本来就是 0。 */
        private float maxScrollForCurrentPage() {
            if (bottom == 4) return Math.max(0f, toolsBottom - 2192f);
            if (bottom != 1) return 0f;
            return Math.max(0f, entertainmentBottom + 40f - CONTENT_BOTTOM);
        }

        /** 换页时只重取下面那块功能区；顶上一段和四个高亮块只解一次。 */
        private void loadHome() {
            if (homeBitmap != null) {
                homeBitmap.recycle();
                homeBitmap = null;
            }
            homeBitmap = BitmapFactory.decodeResource(getResources(), homeRefs[servicePage]);
            if (topBitmap == null)
                topBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.home_top);
            for (int i = 0; i < segmentBitmaps.length; i++)
                if (segmentBitmaps[i] == null)
                    segmentBitmaps[i] = BitmapFactory.decodeResource(getResources(), segmentRefs[i]);
        }

        private float sx(float v) {
            return v * getWidth() / W;
        }

        private float sy(float v) {
            return v * getHeight() / H;
        }

        private String detailTitle;

        private float contentHeight() {
            return bottom == 4 ? toolsBottom + 40 : bottom == 1 ? 2350 : 2050;
        }

        private void drawDetail(Canvas c) {
            c.drawColor(PAGE_BG);
            // 返回：白底圆角块里一枚细箭头，比原来那两根粗黑线干净
            round(c, 32, 96, 130, 194, 26, Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            p.setColor(INK);
            Path back = new Path();
            back.moveTo(94, 121);
            back.lineTo(71, 145);
            back.lineTo(94, 169);
            c.drawPath(back, p);
            p.setStyle(Paint.Style.FILL);
            text(c, "返回", 150, 162, 34, INK, false);
            text(c, detailTitle, 40, 306, 54, INK, true);
            // 这一页还没做，先摆一张和列表同一套配色的卡片，写清楚点进来的是哪个功能
            float top = 366, bottom = 570;
            round(c, CARD_LEFT, top, CARD_RIGHT, bottom, CARD_RADIUS, Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f);
            p.setColor(CARD_EDGE);
            c.drawRoundRect(CARD_LEFT, top, CARD_RIGHT, bottom, CARD_RADIUS, CARD_RADIUS, p);
            p.setStyle(Paint.Style.FILL);
            EntertainmentRow box = entertainmentRowNamed(detailTitle);
            int entry = profileEntryIndex(detailTitle);
            float textLeft = 78;
            if (box != null || entry >= 0) {
                round(c, 76, top + 64, 152, top + 140, 24, sectionTint[0]);
                if (box != null) {
                    drawEntertainmentIcon(c, box.glyph, 114, top + 102, sectionAccent[box.sectionIndex]);
                } else {
                    // 「我」页那五个入口的图标是另一套画法，缩放对齐到同一个图标块里
                    c.save();
                    c.translate(114, top + 102);
                    c.scale(.78f, .78f);
                    drawProfileIcon(c, profileEntryIcons[entry], 0, 0, sectionAccent[0]);
                    c.restore();
                }
                textLeft = 178;
            }
            text(c, detailTitle, textLeft, top + 102, 40, INK, true);
            text(c, "功能页面", textLeft, top + 150, 28, SUB_INK, false);
            drawFooterAndNav(c);
        }

        /** 详情页按标题反查是哪一行，好把图标和分区配色照搬过来。 */
        private EntertainmentRow entertainmentRowNamed(String title) {
            for (EntertainmentRow box : entertainmentRows)
                if (box.row.title.equals(title)) return box;
            return null;
        }

        /** 详情页也可能来自「我」页那五个入口，同样按标题反查图标。 */
        private int profileEntryIndex(String title) {
            for (int i = 0; i < profileEntries.length; i++)
                if (profileEntries[i].equals(title)) return i;
            return -1;
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.save();
            c.scale(getWidth() / W, getHeight() / H);
            if (detailTitle != null) drawDetail(c);
            else if (bottom == 0) drawHome(c);
            else if (bottom == 4) drawTools(c);
            else if (bottom == 1) drawEntertainment(c);
            else drawProfile(c);
            c.restore();
        }

        private void drawHome(Canvas c) {
            // 先铺当前分段页（格子区域），再用固定顶部把这一页自己的横幅和分段控件盖掉，
            // 最后单独贴一块选中态 —— 切页时看起来只有高亮和下面那块功能区在动。
            if (homeBitmap != null) {
                src.set(0, 0, homeBitmap.getWidth(), homeBitmap.getHeight());
                dst.set(0, 0, W, H);
                c.drawBitmap(homeBitmap, src, dst, p);
            }
            if (topBitmap != null) {
                src.set(0, 0, topBitmap.getWidth(), topBitmap.getHeight());
                dst.set(0, 0, W, TOP_HEIGHT);
                c.drawBitmap(topBitmap, src, dst, p);
            }
            int page = Math.max(0, Math.min(3, servicePage));
            Bitmap highlight = segmentBitmaps[page];
            if (highlight != null) {
                src.set(0, 0, highlight.getWidth(), highlight.getHeight());
                dst.set(SEGMENT_LEFT[page], SEGMENT_TOP,
                        SEGMENT_LEFT[page] + SEGMENT_WIDTH[page], SEGMENT_BOTTOM);
                c.drawBitmap(highlight, src, dst, p);
            }
            // 首页大图左侧那块紫底是空的 —— 把后台公告的文字框叠在这里
            drawHomeBanner(c);
            // 各页参考图底部那行红字（重要通知 / 温馨提示 / 唯一官网…）不要了，压成和周围一样的底色。
            // 这里**不能**用纯白：四张参考图都是 y=2145 起才转纯白的，上面是 #f6f6f6，
            // 压白（或只从 2155 开始压）都会在标签栏上方留出一条一眼就能看出来的白边。
            // 从 2142 起整段压成 #f6f6f6，和上面的灰完全接上，红字也照样盖住。
            p.setColor(0xfff6f6f6);
            c.drawRect(0, 2142, W, 2212, p);
            drawNav(c);
        }

        /**
         * 首页横幅文字框：内容与轮播节奏都由后台「公告管理」下发（见 Session.Banner）。
         *
         * 画在两块半透明圆角底上，位置照着参考图留白的比例来：
         *   第一行主文案 —— 稍大的圆角框，最多两行；
         *   第二行小字   —— 更小的一枚胶囊。
         * 没有后台公告时用默认问候语（后台可配），所以这里永远有东西可画。
         */
        private void drawHomeBanner(Canvas c) {
            if (!Session.bannerEnabled()) return;
            Session.Banner banner = Session.currentBanner();
            if (banner == null) return;
            String title = banner.title == null ? "" : banner.title.trim();
            String hint = banner.hint == null ? "" : banner.hint.trim();
            if (title.isEmpty() && hint.isEmpty()) return;

            // 版式全部按底图实测标定（home_top.png：紫卡 x 53..1025 / y 301..702）：
            //   主文案在人物**旁边**：无底框，往右下挪了一档（左 120、首行基线 396）；
            //   长条在人物**下面**：不透明白底 + 细边框，铺到紫卡内侧的最长。
            // 实测关键点：y≥664 之后人物轮廓已退到 x≥1025，所以长条只要压在卡片最下沿，
            // 就能从 x=96 一直拉到 x=985（左右各留 43/40px 内边距）而不碰到人物。
            final float left = 120f;          // 主文案左边距（从 96 右移到 120）
            final float titleMaxRight = 560f; // 人物在这一段（y≈396~452）的左边界约 671
            final float titleBaseline = 396f; // 首行基线（从 356 下移到 396）

            if (!title.isEmpty()) {
                // 人物旁边：无底框，靠阴影压住紫底保证可读
                TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                tp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                tp.setTextSize(42f);
                tp.setColor(Color.WHITE);
                tp.setShadowLayer(8f, 0f, 3f, 0x73000000);
                java.util.List<String> lines = wrapText(title, tp, titleMaxRight - left, 2);
                float ty = titleBaseline;
                for (String line : lines) {
                    c.drawText(line, left, ty, tp);
                    ty += 56f;
                }
            }

            if (!hint.isEmpty()) {
                // 人物下面：长条 —— 不透明白底 + 细边框，尽量拉长
                // 高度 100 / 上沿 565（再上移 5px，下沿到 665）
                // 字号**跟着条高自适应**：基准 = 条高 × 0.33，宽度放不下时再自动缩小，
                // 这样以后改条高不用再手动调字号，长文案也不会被截断。
                final float barL = 96f, barR = 985f, barH = 100f, barY = 565f;
                round(c, barL, barY, barR, barY + barH, barH / 2f, 0xFFFFFFFF);   // 不透明白
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2f);
                p.setColor(0xFF8F86E8);                                          // 淡紫细边框（在白底上看得见）
                c.drawRoundRect(barL, barY, barR, barY + barH, barH / 2f, barH / 2f, p);
                p.setStyle(Paint.Style.FILL);

                TextPaint hp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                hp.setColor(0xFF4A44B5);                                         // 白底上用深紫字
                float textPad = Math.max(24f, barH * 0.30f);
                float fontSize = Math.max(18f, barH * 0.42f);   // 字号占比调大：100px 条高 → 约 42px
                hp.setTextSize(fontSize);
                float maxTextW = barR - barL - textPad * 2;

                // 上下滚动轮播：每条停留 hintRotateSec 秒，最后 420ms 做上下切换动画
                Session.HintFrame frame = Session.hintFrame();
                String oneLine = frame.current.replaceAll("\\s+", " ").trim();
                String nextLine = frame.next == null ? "" : frame.next.replaceAll("\\s+", " ").trim();
                // 宽度自适应：本条放不下就缩字号（同时管住下一条，免得滚动时字忽大忽小）
                while (Math.max(hp.measureText(oneLine), hp.measureText(nextLine)) > maxTextW && fontSize > 18f) {
                    fontSize -= 2f;
                    hp.setTextSize(fontSize);
                }
                float centerY = barY + barH / 2f;
                float baseline = centerY + fontSize * 0.35f;

                if (frame.rolling() && !nextLine.isEmpty()) {
                    // 滚动中：把长条内部裁出来，上一条往上滑出、下一条从下面滑入
                    c.save();
                    Path clip = new Path();
                    clip.addRoundRect(new RectF(barL, barY, barR, barY + barH), barH / 2f, barH / 2f, Path.Direction.CW);
                    c.clipPath(clip);
                    float dy = frame.progress * barH;
                    c.drawText(oneLine, barL + textPad, baseline - dy, hp);
                    c.drawText(nextLine, barL + textPad, baseline + barH - dy, hp);
                    c.restore();
                } else {
                    c.drawText(oneLine, barL + textPad, baseline, hp);
                }
            }
        }

        /** 按像素宽折行（中文逐字、英文按词），最多 maxLines 行，超出用省略号。 */
        private java.util.List<String> wrapText(String s, TextPaint tp, float maxWidth, int maxLines) {
            java.util.List<String> out = new java.util.ArrayList<>();
            if (s == null || s.isEmpty()) return out;
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                String next = line.toString() + ch;
                if (tp.measureText(next) > maxWidth && line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                    if (out.size() == maxLines) break;
                }
                line.append(ch);
            }
            if (out.size() < maxLines && line.length() > 0) out.add(line.toString());
            if (out.size() == maxLines) {
                // 还有剩余内容就补省略号
                String last = out.get(maxLines - 1);
                float used = 0;
                for (String l : out) used += l.length();
                if (used < s.length() && tp.measureText(last + "…") <= maxWidth) {
                    out.set(maxLines - 1, last + "…");
                }
            }
            return out;
        }

        private void text(Canvas c, String s, float x, float baseline, float size, int color, boolean bold) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            p.setTextSize(size);
            p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            c.drawText(s, x, baseline, p);
        }

        /** 先量一下这段字有多宽，好在它后面接着摆东西（NEW 那条小牌子）。 */
        private float textWidth(String s, float size, boolean bold) {
            p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            p.setTextSize(size);
            return p.measureText(s);
        }

        private void textCentered(Canvas c, String s, float centerX, float baseline, float size, int color, boolean bold) {
            text(c, s, centerX - textWidth(s, size, bold) / 2f, baseline, size, color, bold);
        }

        private void round(Canvas c, float l, float t, float r, float b, float rad, int color) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawRoundRect(l, t, r, b, rad, rad, p);
        }

        private void line(Canvas c, float x1, float y1, float x2, float y2, int color, float width) {
            p.setColor(color);
            p.setStrokeWidth(width);
            p.setStyle(Paint.Style.STROKE);
            c.drawLine(x1, y1, x2, y2, p);
            p.setStyle(Paint.Style.FILL);
        }

        /** 顶部那张渐变头图：整页的脸面，标题和一句话说明都放在里头。 */
        private void drawEntertainmentHero(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(CARD_LEFT, HERO_TOP, CARD_RIGHT, HERO_BOTTOM,
                    BRAND, BRAND_2, Shader.TileMode.CLAMP));
            c.drawRoundRect(CARD_LEFT, HERO_TOP, CARD_RIGHT, HERO_BOTTOM, 34, 34, p);
            p.setShader(null);
            c.save();
            Path clip = new Path();
            clip.addRoundRect(new RectF(CARD_LEFT, HERO_TOP, CARD_RIGHT, HERO_BOTTOM), 34, 34, Path.Direction.CW);
            c.clipPath(clip);
            // 右边几圈半透明的光斑，和首页那张头图上的装饰一个意思
            p.setColor(0x26ffffff);
            c.drawCircle(918, 196, 92, p);
            p.setColor(0x1affffff);
            c.drawCircle(1006, 302, 54, p);
            p.setColor(0x40ffffff);
            c.drawCircle(846, 302, 9, p);
            c.restore();
            text(c, "更多玩法", 76, 244, 58, Color.WHITE, true);
            text(c, "挑一个玩法，让截图更真实", 78, 306, 28, 0xccffffff, false);
        }

        /** 分区标题：一根小色条 + 深色粗字，替掉原来那条通栏灰底。 */
        private void drawSectionTitle(Canvas c, String title, float top, int accent) {
            round(c, 44, top + 34, 56, top + 62, 6, accent);
            text(c, title, 78, top + 64, 34, INK, true);
        }

        private void drawEntertainment(Canvas c) {
            c.drawColor(PAGE_BG);
            c.save();
            c.clipRect(0, 0, 1080, CONTENT_BOTTOM);
            c.translate(0, -scroll);
            drawEntertainmentHero(c);
            int i = 0;
            while (i < entertainmentRows.size()) {
                EntertainmentRow first = entertainmentRows.get(i);
                int last = i;
                while (last + 1 < entertainmentRows.size()
                        && entertainmentRows.get(last + 1).sectionIndex == first.sectionIndex) last++;
                float cardBottom = entertainmentRows.get(last).bottom;
                drawSectionTitle(c, sections.get(first.sectionIndex).title,
                        first.top - SECTION_HEADER_H, first.accent);
                // 一个分区一张白卡片：圆角 + 一条淡边，白底压在浅灰底上才看得出边界
                round(c, CARD_LEFT, first.top, CARD_RIGHT, cardBottom, CARD_RADIUS, Color.WHITE);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.6f);
                p.setColor(CARD_EDGE);
                c.drawRoundRect(CARD_LEFT, first.top, CARD_RIGHT, cardBottom, CARD_RADIUS, CARD_RADIUS, p);
                p.setStyle(Paint.Style.FILL);
                for (int k = i; k <= last; k++) drawEntertainmentRow(c, entertainmentRows.get(k));
                i = last + 1;
            }
            c.restore();
            drawFooterAndNav(c);
        }

        private void drawEntertainmentRow(Canvas c, EntertainmentRow box) {
            FeatureRow row = box.row;
            float top = box.top;
            float middle = top + ROW_H / 2f; // 行正中：图标、箭头、开关都照它对齐
            float titleBaseline = middle - 8f;
            boolean hasSubtitle = !row.subtitle.isEmpty();
            if (!box.firstInSection) line(c, 76, top, CARD_RIGHT - 44, top, CARD_HAIRLINE, 1.6f);
            // 左边一块淡色圆角图标，替掉原来那根灰线条
            round(c, 76, middle - 38, 152, middle + 38, 24, box.tint);
            drawEntertainmentIcon(c, box.glyph, 114, middle, box.accent);
            text(c, row.title, 178, hasSubtitle ? titleBaseline : middle + 13, 36, INK, false);
            if (hasSubtitle) text(c, row.subtitle, 178, middle + 36, 28, SUB_INK, false);
            if ("sound".equals(row.key))
                drawNewBadge(c, 178 + textWidth(row.title, 36, false) + 22, titleBaseline);
            if (isSwitchRow(row)) drawSwitch(c, 872, middle - 28, row.enabled);
            else drawChevron(c, 990, middle);
        }

        /** 开关行（服务端 `switch:true` 的行，如「撩妹加V」）右端画开关、不是箭头；画和点都以它为准。 */
        private static boolean isSwitchRow(FeatureRow row) {
            return row != null && row.switchRow;
        }

        private void drawNewBadge(Canvas c, float left, float baseline) {
            float width = textWidth("NEW", 24, true) + 26;
            round(c, left, baseline - 30, left + width, baseline + 12, 21, 0xffff4d4f);
            text(c, "NEW", left + 13, baseline, 24, Color.WHITE, true);
        }

        /** 图标块里那个字形：还是原来那八个，只是缩小一圈、换成所在分区的强调色。 */
        private void drawEntertainmentIcon(Canvas c, int index, float cx, float cy, int color) {
            c.save();
            c.translate(cx, cy);
            c.scale(.78f, .78f);
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            if (index == 0) {
                c.drawCircle(0, 0, 23, p);
                c.drawArc(-27, -27, 27, 27, 35, 290, false, p);
            } else if (index == 1) {
                text(c, "淘", -23, 20, 42, color, true);
            } else if (index == 2) {
                round(c, -26, -22, 26, 23, 11, color);
                p.setColor(Color.WHITE);
                c.drawCircle(-9, -4, 2, p);
                c.drawCircle(9, -4, 2, p);
            } else if (index == 3) {
                c.drawArc(-26, -26, 26, 26, 125, 290, false, p);
                line(c, -23, 15, -23, 28, color, 7);
                line(c, 23, 15, 23, 28, color, 7);
            } else if (index == 4) {
                round(c, -25, -18, 24, 17, 5, color);
                p.setColor(Color.WHITE);
                Path z = new Path();
                z.moveTo(1, -17);
                z.lineTo(-9, 1);
                z.lineTo(1, 1);
                z.lineTo(-4, 17);
                z.lineTo(12, -4);
                z.lineTo(3, -4);
                z.close();
                c.drawPath(z, p);
            } else if (index == 5) {
                c.drawArc(-25, -23, 25, 25, 205, 300, true, p);
                c.drawArc(-12, -12, 12, 12, 0, 360, false, p);
            } else if (index == 6) {
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(0, 0, 24, p);
                text(c, "¥", -13, 14, 29, Color.WHITE, true);
            } else {
                c.drawCircle(0, -13, 12, p);
                c.drawLine(0, -1, 0, 25, p);
                c.drawLine(-15, 13, 15, 13, p);
            }
            p.setStyle(Paint.Style.FILL);
            c.restore();
        }

        private void drawChevron(Canvas c, float cx, float cy) {
            line(c, cx - 9, cy - 12, cx + 3, cy, 0xffaaaaaa, 4);
            line(c, cx + 3, cy, cx - 9, cy + 12, 0xffaaaaaa, 4);
        }

        private void drawSwitch(Canvas c, float x, float top, boolean on) {
            round(c, x, top, x + 132, top + 56, 28, on ? BRAND : 0xffe3e6ef);
            p.setColor(Color.WHITE);
            c.drawCircle(x + (on ? 104 : 28), top + 28, 23, p);
        }

        // ── 「我」那一页 ────────────────────────────────────────────────────────
        // 这一页画在 1280 x 2560 的设计空间里（见 drawProfile 里那一次 scale），下面这些数都是
        // 那个空间的坐标；点会员卡 / 点服务入口的判定范围也用同一份，画到哪就点到哪。
        // 版式参考：标题栏「我的」 + 无底卡的用户行（头像/名字/ID） + 深蓝金冠会员卡 +
        // 「常用服务」白卡列表。
        private static final float ME_LEFT = 88f;
        private static final float ME_RIGHT = 1192f;
        /** 用户行（头像 + 名字）：整行是一张带边框的卡片，也是登录/注册与账号设置的入口。 */
        private static final float ME_USER_TOP = 214f;
        private static final float ME_USER_BOTTOM = 484f;
        /** 这一排比下面的卡片再宽一点（左右各多出 32）。 */
        private static final float ME_USER_LEFT = 56f;
        private static final float ME_USER_RIGHT = 1224f;
        /** 深蓝会员卡。 */
        private static final float ME_MEMBER_TOP = 508f;
        private static final float ME_MEMBER_BOTTOM = 838f;
        /** 卡片下面那两颗轮播指示点。 */
        private static final float ME_DOTS_Y = 900f;
        /** 「常用服务」白卡。 */
        private static final float ME_ENTRIES_TOP = 948f;
        private static final float ME_ENTRY_H = 190f;
        /** 服务入口：标签用的是文件里原有的那份列表，图标和它一一对得上。 */
        private final String[] profileEntries = {"人工客服", "求新功能", "邀请好友", "更多设置"};
        private final String[] profileEntryIcons = {"service", "feature", "share", "more"};
        /** 每行右侧那句灰色小字（参考图里的人工客服下面写「7×12小时在线」）。 */
        private final String[] profileEntryNotes = {"7×12 小时在线", "提交产品建议", "一起体验更好用的功能", "账号、设备与隐私"};
        private static final int VIP_CARD_1 = 0xff16265c;
        private static final int VIP_CARD_2 = 0xff2a5bd7;
        private static final int VIP_GOLD = 0xffffe3a3;
        private static final int VIP_GOLD_2 = 0xfff5c169;
        private static final int VIP_FAINT = 0xffc9d6ff;

        private void drawProfile(Canvas c) {
            c.drawColor(PAGE_BG);
            // The reference screenshot is 1280x2560; render this page in that design space
            // and scale it to the current device without clipping the right edge.
            c.save();
            c.scale(1080f / 1280f, 2388f / 2560f);
            c.translate(0, -(bottom == 2 ? scroll : 0f));
            drawProfileTitle(c);
            drawProfileUser(c);
            drawMemberCard(c);
            drawProfileEntries(c);
            c.restore();
            // Draw the same shared bottom navigation used by the other tabs, in the base canvas space.
            drawNav(c);
        }

        /** 标题栏：居中「我的」，右上角「···」（点它开账号菜单）。 */
        private void drawProfileTitle(Canvas c) {
            textCentered(c, "我的", 640, 150, 52, INK, true);
            p.setStyle(Paint.Style.FILL);
            p.setColor(INK);
            for (int i = 0; i < 3; i++) c.drawCircle(1136 + i * 30, 132, 7.5f, p);
        }

        /**
         * 用户行：**整行是一张带边框的卡片**（头像 + 名字），也是登录/注册与账号设置的入口。
         * 这一排比下面的卡片更宽，只有头像和名字两样东西（没有 ID、没有右侧图标）。
         */
        private void drawProfileUser(Canvas c) {
            boolean logged = Session.isLoggedIn();
            float cy = (ME_USER_TOP + ME_USER_BOTTOM) / 2f;

            // 这一排的边框：白底 + 淡蓝描边，整行可点
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(Color.WHITE);
            c.drawRoundRect(ME_USER_LEFT, ME_USER_TOP, ME_USER_RIGHT, ME_USER_BOTTOM, 44, 44, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.4f);
            p.setColor(0xFFE1E7F5);
            c.drawRoundRect(ME_USER_LEFT, ME_USER_TOP, ME_USER_RIGHT, ME_USER_BOTTOM, 44, 44, p);
            p.setStyle(Paint.Style.FILL);

            // 头像：有上传过就画图（圆形裁剪），否则渐变圆 + 首字。
            // 注意：drawCircle/drawPath 上的 LinearGradient 在这个画布上不稳定（实测填充会丢），
            // 所以一律「实色打底 + 渐变叠加」，渐变没生效时底色也是对的。
            float ax = (ME_USER_LEFT + ME_USER_RIGHT) / 2f - (ME_USER_RIGHT - ME_USER_LEFT) / 2f + 104;
            float radius = 82;
            p.setShader(null);
            p.setColor(0x1a2f6fed);
            c.drawCircle(ax, cy, radius + 8, p);
            p.setColor(BRAND);
            c.drawCircle(ax, cy, radius, p);
            p.setShader(new LinearGradient(ax - radius, cy - radius, ax + radius, cy + radius, BRAND, 0xff8a6bff, Shader.TileMode.CLAMP));
            c.drawCircle(ax, cy, radius, p);
            p.setShader(null);

            android.graphics.Bitmap avatar = com.luoyun.doutu.account.AvatarStore.bitmap();
            if (logged && avatar != null && !avatar.isRecycled()) {
                c.save();
                Path clip = new Path();
                clip.addCircle(ax, cy, radius, Path.Direction.CW);
                c.clipPath(clip);
                android.graphics.Matrix matrix = com.luoyun.doutu.account.AvatarView.coverMatrix(avatar, radius * 2);
                matrix.postTranslate(ax - radius, cy - radius);
                p.setFilterBitmap(true);
                c.drawBitmap(avatar, matrix, p);
                p.setFilterBitmap(false);
                c.restore();
            } else if (!logged || avatar == null) {
                String glyph = logged ? firstChar(Session.displayName()) : "登";
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextSize(radius * 0.82f);
                p.setColor(Color.WHITE);
                p.setTextAlign(Paint.Align.LEFT);
                c.drawText(glyph, ax - p.measureText(glyph) / 2f, cy + radius * 0.29f, p);
            }

            text(c, logged ? Session.displayName() : "点击登录 / 注册", ax + radius + 46, cy + 20, 60, INK, true);
        }

        /** 取昵称第一个字符（中文单字即可，空串兜底成「登」）。 */
        private String firstChar(String text) {
            if (text == null || text.isEmpty()) return "登";
            return text.substring(0, 1);
        }

        /**
         * 深蓝金冠会员卡：左边一枚金质盾牌王冠，中间标题与有效期，
         * 右边一颗金色胶囊按钮。整张卡（{@link #ME_MEMBER_TOP}..{@link #ME_MEMBER_BOTTOM}）都是热点。
         */
        private void drawMemberCard(Canvas c) {
            boolean logged = Session.isLoggedIn();
            boolean active = Session.isActivated();

            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(VIP_CARD_1);
            c.drawRoundRect(ME_LEFT, ME_MEMBER_TOP, ME_RIGHT, ME_MEMBER_BOTTOM, 44, 44, p);
            p.setShader(new LinearGradient(ME_LEFT, ME_MEMBER_TOP, ME_RIGHT, ME_MEMBER_BOTTOM,
                    VIP_CARD_1, VIP_CARD_2, Shader.TileMode.CLAMP));
            c.drawRoundRect(ME_LEFT, ME_MEMBER_TOP, ME_RIGHT, ME_MEMBER_BOTTOM, 44, 44, p);
            p.setShader(null);

            // 右上角那道浅色弧光（参考图里卡面右上有一片亮面）
            c.save();
            Path clip = new Path();
            clip.addRoundRect(new RectF(ME_LEFT, ME_MEMBER_TOP, ME_RIGHT, ME_MEMBER_BOTTOM), 44, 44, Path.Direction.CW);
            c.clipPath(clip);
            p.setColor(0x1affffff);
            c.drawCircle(1180, ME_MEMBER_TOP + 20, 260, p);
            p.setColor(0x12ffffff);
            c.drawCircle(1230, ME_MEMBER_BOTTOM, 200, p);
            c.restore();

            drawVipCrown(c, 224, (ME_MEMBER_TOP + ME_MEMBER_BOTTOM) / 2f);

            text(c, active ? "VIP 会员" : "VIP 会员", 366, ME_MEMBER_TOP + 132, 56, Color.WHITE, true);
            String line;
            if (!logged) {
                line = "登录后查看会员状态";
            } else if (active) {
                line = "有效期至 " + (Session.expireAt().isEmpty() ? "-" : Session.expireAt().substring(0, 10).replace('-', '.'));
            } else {
                line = Session.overlayText();
            }
            text(c, line.length() > 16 ? line.substring(0, 16) + "…" : line, 368, ME_MEMBER_TOP + 196, 32, VIP_FAINT, false);

            // 金色胶囊按钮（未开通=开通会员，已开通=续费升级）：实色打底 + 渐变叠加
            p.setShader(null);
            p.setColor(VIP_GOLD);
            c.drawRoundRect(872, ME_MEMBER_TOP + 118, 1128, ME_MEMBER_TOP + 232, 57, 57, p);
            p.setShader(new LinearGradient(872, ME_MEMBER_TOP + 118, 872, ME_MEMBER_TOP + 232, VIP_GOLD, VIP_GOLD_2, Shader.TileMode.CLAMP));
            c.drawRoundRect(872, ME_MEMBER_TOP + 118, 1128, ME_MEMBER_TOP + 232, 57, 57, p);
            p.setShader(null);
            textCentered(c, active ? "续费升级" : "开通会员", 1000, ME_MEMBER_TOP + 186, 36, 0xff4a3410, true);

            // 轮播指示点：短的深色当前态 + 长的浅色
            round(c, 570, ME_DOTS_Y - 7, 646, ME_DOTS_Y + 7, 7, BRAND);
            round(c, 662, ME_DOTS_Y - 7, 726, ME_DOTS_Y + 7, 7, 0xffd7dced);
        }

        /** 金质盾牌 + 王冠：卡面左侧那一枚（纯 Path 画，不引图片资源）。 */
        private void drawVipCrown(Canvas c, float cx, float cy) {
            float w = 62, h = 74;
            // 盾牌底（实色打底，再叠一层渐变；见 drawProfileUser 里的说明）
            Path shield = new Path();
            shield.moveTo(cx, cy - h);
            shield.lineTo(cx + w, cy - h * 0.55f);
            shield.lineTo(cx + w, cy + h * 0.30f);
            shield.lineTo(cx, cy + h);
            shield.lineTo(cx - w, cy + h * 0.30f);
            shield.lineTo(cx - w, cy - h * 0.55f);
            shield.close();
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(VIP_GOLD);
            c.drawPath(shield, p);
            p.setShader(new LinearGradient(cx - w, cy - h, cx + w, cy + h, VIP_GOLD, VIP_GOLD_2, Shader.TileMode.CLAMP));
            c.drawPath(shield, p);
            p.setShader(null);
            // 内圈浅色，做出厚度
            Path inner = new Path();
            float iw = w * 0.82f, ih = h * 0.84f;
            inner.moveTo(cx, cy - ih);
            inner.lineTo(cx + iw, cy - ih * 0.55f);
            inner.lineTo(cx + iw, cy + ih * 0.30f);
            inner.lineTo(cx, cy + ih);
            inner.lineTo(cx - iw, cy + ih * 0.30f);
            inner.lineTo(cx - iw, cy - ih * 0.55f);
            inner.close();
            p.setColor(0x33ffffff);
            c.drawPath(inner, p);
            // 王冠（三个尖 + 底座），深金实色
            Path crown = new Path();
            float cw = w * 0.68f, baseY = cy + h * 0.42f, tipTop = cy - h * 0.42f;
            crown.moveTo(cx - cw, baseY);
            crown.lineTo(cx - cw, tipTop + h * 0.16f);
            crown.lineTo(cx - cw * 0.40f, tipTop + h * 0.42f);
            crown.lineTo(cx, tipTop);
            crown.lineTo(cx + cw * 0.40f, tipTop + h * 0.42f);
            crown.lineTo(cx + cw, tipTop + h * 0.16f);
            crown.lineTo(cx + cw, baseY);
            crown.close();
            p.setColor(0xff7a5410);
            c.drawPath(crown, p);
            // 王冠底下的横条
            round(c, cx - cw, baseY + 6, cx + cw, baseY + 16, 5, 0xff7a5410);
            p.setStyle(Paint.Style.FILL);
        }

        /** 服务入口：一张白卡，标题「常用服务」，每行「图标块 + 标题 + 灰色小字 + 箭头」。 */
        private void drawProfileEntries(Canvas c) {
            float bottom = ME_ENTRIES_TOP + 150 + profileEntries.length * ME_ENTRY_H;
            round(c, ME_LEFT, ME_ENTRIES_TOP, ME_RIGHT, bottom, 40, Color.WHITE);
            text(c, "常用服务", 128, ME_ENTRIES_TOP + 90, 46, INK, true);
            for (int i = 0; i < profileEntries.length; i++) {
                float rowTop = ME_ENTRIES_TOP + 150 + i * ME_ENTRY_H;
                float middle = rowTop + ME_ENTRY_H / 2f;
                if (i > 0) line(c, 254, rowTop, 1076, rowTop, CARD_HAIRLINE, 1.6f);
                round(c, 128, middle - 50, 232, middle + 50, 30, sectionTint[0]);
                c.save();
                c.translate(180, middle);
                c.scale(.82f, .82f);
                drawProfileIcon(c, profileEntryIcons[i], 0, 0, sectionAccent[0]);
                c.restore();
                text(c, profileEntries[i], 274, middle - 6, 44, INK, false);
                text(c, profileEntryNotes[i], 276, middle + 46, 28, SUB_INK, false);
                drawChevron(c, 1096, middle);
            }
        }

        private void drawProfileIcon(Canvas c, String kind, float cx, float cy, int color) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            p.setColor(color);
            if (kind.equals("service")) {
                c.drawArc(cx - 25, cy - 28, cx + 25, cy + 28, 180, 180, false, p);
                c.drawLine(cx - 25, cy, cx - 25, cy + 22, p);
                c.drawLine(cx + 25, cy, cx + 25, cy + 22, p);
                c.drawRoundRect(cx - 33, cy + 10, cx - 20, cy + 30, 4, 4, p);
                c.drawRoundRect(cx + 20, cy + 10, cx + 33, cy + 30, 4, 4, p);
            } else if (kind.equals("font")) {
                text(c, "Aa", cx - 36, cy + 18, 42, color, true);
            } else if (kind.equals("feature")) {
                c.drawCircle(cx - 16, cy - 12, 7, p);
                c.drawCircle(cx + 13, cy - 12, 7, p);
                c.drawCircle(cx - 16, cy + 16, 7, p);
                c.drawCircle(cx + 13, cy + 16, 7, p);
                line(c, cx + 13, cy + 16, cx + 13, cy - 6, color, 7);
                line(c, cx + 2, cy + 5, cx + 24, cy + 5, color, 7);
            } else if (kind.equals("share")) {
                c.drawCircle(cx - 20, cy + 8, 7, p);
                c.drawCircle(cx + 18, cy - 18, 7, p);
                c.drawCircle(cx + 18, cy + 29, 7, p);
                c.drawLine(cx - 14, cy + 5, cx + 12, cy - 14, p);
                c.drawLine(cx - 14, cy + 12, cx + 12, cy + 25, p);
            } else if (kind.equals("watch")) {
                // 参考图右上角那枚「六边形 + 指针」：账号入口
                Path hex = new Path();
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI / 3 * i - Math.PI / 6;
                    float hx = cx + (float) Math.cos(a) * 30, hy = cy + (float) Math.sin(a) * 30;
                    if (i == 0) hex.moveTo(hx, hy);
                    else hex.lineTo(hx, hy);
                }
                hex.close();
                c.drawPath(hex, p);
                c.drawLine(cx, cy - 13, cx, cy + 1, p);
                c.drawLine(cx, cy + 1, cx + 11, cy + 7, p);
            } else {
                Path hex = new Path();
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI / 3 * i - Math.PI / 6;
                    float px = cx + (float) Math.cos(a) * 29, py = cy + (float) Math.sin(a) * 29;
                    if (i == 0) hex.moveTo(px, py);
                    else hex.lineTo(px, py);
                }
                hex.close();
                c.drawPath(hex, p);
                c.drawCircle(cx, cy, 9, p);
            }
            p.setStyle(Paint.Style.FILL);
        }

        private void drawMessage(Canvas c, float x, float y) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            p.setColor(0xff626266);
            c.drawRoundRect(x - 35, y - 31, x + 35, y + 29, 20, 20, p);
            c.drawCircle(x - 12, y, 3, p);
            c.drawCircle(x + 12, y, 3, p);
            p.setStyle(Paint.Style.FILL);
        }

        private void drawTargetNav(Canvas c) {
            p.setColor(Color.WHITE);
            c.drawRect(0, 2125, 1280, 2388, p);
            navItemTarget(c, "首页", 210, false);
            navItemTarget(c, "娱乐", 640, false);
            round(c, 985, 2225, 1170, 2350, 65, 0xffeeeeee);
            navItemTarget(c, "我", 1080, true);
        }

        private void navItemTarget(Canvas c, String label, float x, boolean selected) {
            text(c, label, x - 45, 2305, 51, selected ? 0xff222226 : 0xff99999d, false);
        }

        private void drawGear(Canvas c, float x, float y) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(7);
            p.setColor(0xff626266);
            c.drawCircle(x, y, 29, p);
            c.drawCircle(x, y, 10, p);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                c.drawLine(x + (float) Math.cos(a) * 31, y + (float) Math.sin(a) * 31, x + (float) Math.cos(a) * 43, y + (float) Math.sin(a) * 43, p);
            }
            p.setStyle(Paint.Style.FILL);
        }

        private void drawShare(Canvas c, float x, float y) {
            p.setColor(0xff414146);
            c.drawCircle(x - 20, y + 7, 7, p);
            c.drawCircle(x + 18, y - 16, 7, p);
            c.drawCircle(x + 18, y + 28, 7, p);
            line(c, x - 14, y + 4, x + 12, y - 12, 0xff414146, 5);
            line(c, x - 14, y + 11, x + 12, y + 25, 0xff414146, 5);
        }

        private void drawFooterAndNav(Canvas c) {
            // 以前这里把 CONTENT_BOTTOM(2145) 到屏幕底部整条刷成白色、还在 2145 画一条分隔线，
            // 于是「娱乐」页的标签栏上方多出明显的一排白边（和「我」页不一致）。
            // 底层 drawEntertainment/drawDetail 已经铺过整屏 PAGE_BG，这里不再重复刷白，
            // 只留 drawNav 自己那条标签栏分隔线。
            drawNav(c);
        }

        private void buildToolsLayout() {
            toolSections.clear();
            float y = TOOL_BAR_H;
            // 顶部「替换头像」：单独一行，后面没有网格
            ToolSection replace = new ToolSection();
            replace.replaceRow = true;
            replace.title = "替换头像";
            replace.icon = "h_replace";
            replace.action = "tool:avatar";
            replace.top = y;
            replace.height = TOOL_ROW_H;
            toolSections.add(replace);
            y += TOOL_ROW_H + 1.5f;
            try (java.io.InputStream in = getContext().getAssets().open("tools_page.json")) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                org.json.JSONArray groups = new org.json.JSONArray(new String(bos.toByteArray(), "UTF-8"));
                for (int gi = 0; gi < groups.length(); gi++) {
                    org.json.JSONObject g = groups.getJSONObject(gi);
                    org.json.JSONArray items = g.getJSONArray("items");
                    ToolSection s = new ToolSection();
                    s.title = g.optString("title");
                    s.icon = g.optString("icon");
                    s.action = g.optString("action", "");
                    s.dark = g.optBoolean("dark", false);
                    s.team = g.optBoolean("team", false);
                    s.top = y;
                    s.gridTop = y + TOOL_GRID_DY;
                    for (int i = 0; i < items.length(); i++) {
                        org.json.JSONObject o = items.getJSONObject(i);
                        ToolCell cell = new ToolCell(o.optString("name"), o.optString("icon"), o.optString("action"));
                        cell.left = TOOL_LEFT + (i % 4) * TOOL_CELL_W;
                        cell.top = s.gridTop + (i / 4) * TOOL_CELL_H;
                        s.items.add(cell);
                    }
                    int rows = Math.max(1, (s.items.size() + 3) / 4);
                    float lastLogo = s.gridTop + (rows - 1) * TOOL_CELL_H;
                    float lastLabelBottom = lastLogo + TOOL_LABEL_DY + TOOL_LABEL_H;
                    if (s.dark) {
                        s.darkTop = lastLabelBottom + 84.75f;
                        s.tutorialTop = s.darkTop + TOOL_DARK_H;
                    } else if (s.team) {
                        s.teamTop = lastLabelBottom + 60f;
                        s.tutorialTop = s.teamTop + TOOL_TEAM_H;
                    } else {
                        s.tutorialTop = lastLabelBottom + 84.75f;
                    }
                    // 下一组标题 = 教程按钮文案顶 + 215.25（参考图 287px）
                    s.height = s.tutorialTop - s.top + 215.25f;
                    y = s.top + s.height;
                    toolSections.add(s);
                }
            } catch (Exception e) {
                android.util.Log.w("DoutuTools", "tools_page.json 读取失败", e);
            }
            toolsBottom = y + 30f;
        }

        /** 工具图标是照真机截图裁出来的（assets/tools_icons），第一次用到才解码。 */
        private Bitmap toolIcon(String key) {
            if (key == null || key.isEmpty()) return null;
            Bitmap b = toolIconCache.get(key);
            if (b != null) return b;
            try (java.io.InputStream in = getContext().getAssets().open("tools_icons/" + key + ".png")) {
                b = BitmapFactory.decodeStream(in);
            } catch (Exception ignored) { }
            if (b != null) toolIconCache.put(key, b);
            return b;
        }

        /** 图标 PNG 是逐张从截图裁的，字形在画布里的位置/大小并不统一；这里取不透明像素的范围。 */
        private Rect toolInk(String key) {
            Rect r = toolInkCache.get(key);
            if (r != null) return r;
            Bitmap b = toolIcon(key);
            if (b == null) {
                r = new Rect(0, 0, 1, 1);
            } else {
                int w = b.getWidth(), h = b.getHeight();
                int minX = w, minY = h, maxX = -1, maxY = -1;
                int[] px = new int[w * h];
                b.getPixels(px, 0, w, 0, 0, w, h);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if ((px[y * w + x] >>> 24) > 8) {
                            if (x < minX) minX = x;
                            if (x > maxX) maxX = x;
                            if (y < minY) minY = y;
                            if (y > maxY) maxY = y;
                        }
                    }
                }
                r = maxX < 0 ? new Rect(0, 0, w, h) : new Rect(minX, minY, maxX + 1, maxY + 1);
            }
            toolInkCache.put(key, r);
            return r;
        }

        /** 把图标的不透明部分按比例居中画进圆底。 */
        private void drawToolGlyph(Canvas c, String key, float centerX, float centerY, float maxInk) {
            Bitmap b = toolIcon(key);
            if (b == null) return;
            Rect ink = toolInk(key);
            float iw = Math.max(1, ink.width()), ih = Math.max(1, ink.height());
            float k = maxInk / Math.max(iw, ih);
            dst.set(centerX - iw * k / 2f, centerY - ih * k / 2f,
                    centerX + iw * k / 2f, centerY + ih * k / 2f);
            p.setFilterBitmap(true);
            c.drawBitmap(b, ink, dst, p);
        }

        private void drawToolArt(Canvas c, String key, float left, float top, float size, int circleColor) {
            if (circleColor != 0) round(c, left, top, left + size, top + size, size / 2f, circleColor);
            Bitmap b = toolIcon(key);
            if (b == null) return;
            src.set(0, 0, b.getWidth(), b.getHeight());
            dst.set(left, top, left + size, top + size);
            p.setFilterBitmap(true);
            c.drawBitmap(b, src, dst, p);
        }

        void rebuildTools() { buildToolsLayout(); invalidate(); }

        private void drawToolHeaderRow(Canvas c, ToolSection s, int ink) {
            boolean dark = toolsDark;
            round(c, 45, s.top + 45f, 45 + TOOL_HDR_LOGO, s.top + 45f + TOOL_HDR_LOGO,
                    TOOL_HDR_LOGO / 2f, dark ? 0xff242424 : 0xfff5f5f5);
            drawToolGlyph(c, s.icon, 45 + TOOL_HDR_LOGO / 2f, s.top + 45f + TOOL_HDR_LOGO / 2f,
                    TOOL_HDR_LOGO * 0.78f);
            text(c, s.title, 258, s.top + 143f, 39, ink, false);
            line(c, 1013, s.top + 113f, 1027, s.top + 129f, 0xffb0b0b0, 4.5f);
            line(c, 1027, s.top + 129f, 1013, s.top + 145f, 0xffb0b0b0, 4.5f);
            line(c, 0, s.top + TOOL_ROW_H, W, s.top + TOOL_ROW_H,
                    dark ? 0xff2b2b2b : 0xffeeeeee, 1.5f);
        }

        private void drawToolsSwitch(Canvas c, float left, float top, boolean on) {
            float w = 101f, h = 60f;
            round(c, left, top, left + w, top + h, h / 2f, on ? TOOL_BAR_COLOR : 0xffcccccc);
            p.setColor(Color.WHITE);
            c.drawCircle(on ? left + w - h / 2f : left + h / 2f, top + h / 2f, h / 2f - 5f, p);
        }

        private void drawTeamCodeRow(Canvas c, ToolSection s) {
            round(c, 150, s.teamTop, 702, s.teamTop + 132, 12, toolsDark ? 0xff1e1e1e : 0xfff7f7f7);
            text(c, "请输入团队暗号", 180, s.teamTop + 82, 33, toolsDark ? 0xff8b8b8b : 0xff999999, false);
            round(c, 747, s.teamTop + 21, 945, s.teamTop + 111, 45, TOOL_BAR_COLOR);
            textCentered(c, "确定", 846, s.teamTop + 82, 36, 0xff1a1a1a, false);
        }

        private void drawTutorialPill(Canvas c, ToolSection s, int pillBg, int ink) {
            float left = 540 - TOOL_TUT_W / 2f;
            round(c, left, s.tutorialTop, left + TOOL_TUT_W, s.tutorialTop + TOOL_TUT_H,
                    TOOL_TUT_H / 2f, pillBg);
            String a = "更多功能及", b = "教程";
            float size = 32;
            float wa = textWidth(a, size, false), wb = textWidth(b, size, false);
            float x = 540 - (wa + wb) / 2f;
            text(c, a, x, s.tutorialTop + 52f, size, ink, false);
            text(c, b, x + wa, s.tutorialTop + 52f, size, TOOL_ACCENT, false);
        }

        private void drawTools(Canvas c) {
            boolean dark = toolsDark;
            int ink = dark ? 0xffeeeeee : 0xff333333;
            int band = dark ? 0xff1b1b1b : 0xfff7f7f7;
            int circle = dark ? 0xff242424 : 0xfff5f5f5;
            int pillBg = dark ? 0xff202020 : 0xfff5f5f5;
            c.drawColor(dark ? 0xff111111 : Color.WHITE);
            c.save();
            c.clipRect(0, TOOL_BAR_H, W, 2212);
            c.translate(0, -scroll);
            for (int si = 0; si < toolSections.size(); si++) {
                ToolSection s = toolSections.get(si);
                if (s.replaceRow) {
                    drawToolHeaderRow(c, s, ink);
                    continue;
                }
                if (si >= 2) {
                    p.setColor(band);
                    c.drawRect(0, s.top - 47.5f, W, s.top - 17.5f, p);
                }
                drawToolHeaderRow(c, s, ink);
                for (ToolCell cell : s.items) {
                    float logoLeft = cell.left + (TOOL_CELL_W - TOOL_LOGO) / 2f;
                    // 参考图的圆底比 176px 的图标槽小一圈（152/176），图标本身仍铺满槽位。
                    float dot = TOOL_LOGO * 0.86f;
                    float dotInset = (TOOL_LOGO - dot) / 2f;
                    round(c, logoLeft + dotInset, cell.top + dotInset,
                            logoLeft + dotInset + dot, cell.top + dotInset + dot, dot / 2f, circle);
                    // 字形在 PNG 里的位置/大小不统一：按不透明像素范围居中，统一缩放到圆底的 63%
                    drawToolGlyph(c, cell.icon, logoLeft + TOOL_LOGO / 2f,
                            cell.top + TOOL_LOGO / 2f, dot * 0.63f);
                    float labelTop = cell.top + TOOL_LABEL_DY;
                    float centerX = cell.left + TOOL_CELL_W / 2f;
                    if (cell.name.length() > 4) {
                        int cut = 4;
                        textCentered(c, cell.name.substring(0, cut), centerX, labelTop + 34f, 33, ink, false);
                        textCentered(c, cell.name.substring(cut), centerX, labelTop + 71.5f, 33, ink, false);
                    } else {
                        textCentered(c, cell.name, centerX, labelTop + 52f, 33, ink, false);
                    }
                }
                if (s.dark) {
                    // 深色模式图标：黑圆 + 左半黄 + 中间白线（真机是同一个图形）
                    float dcx = 360, dcy = s.darkTop + 63f;
                    p.setColor(0xff1a1a1a);
                    c.drawCircle(dcx, dcy, 46, p);
                    p.setColor(0xfff9db61);
                    p.setStyle(Paint.Style.FILL);
                    c.drawArc(new android.graphics.RectF(dcx - 38, dcy - 38, dcx + 38, dcy + 38),
                            90, 180, true, p);
                    p.setColor(0xffffffff);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(3);
                    c.drawLine(dcx, dcy - 38, dcx, dcy + 38, p);
                    p.setStyle(Paint.Style.FILL);
                    text(c, "深色模式", 438, s.darkTop + 72f, 39, ink, false);
                    drawToolsSwitch(c, 666, s.darkTop + 28f, toolsDark);
                }
                if (s.team) drawTeamCodeRow(c, s);
                drawTutorialPill(c, s, pillBg, ink);
            }
            c.restore();
            p.setColor(TOOL_BAR_COLOR);
            c.drawRect(0, 0, W, TOOL_BAR_H, p);
            textCentered(c, "工具", 540, 174, 42, 0xff1a1a1a, false);
            drawNav(c);
        }

        private void handleToolTap(float x, float y) {
            float py = y + scroll;
            for (ToolSection s : toolSections) {
                if (s.replaceRow) {
                    if (py >= s.top && py < s.top + TOOL_ROW_H) {
                        openTool(s.action, s.title);
                        return;
                    }
                    continue;
                }
                if (py >= s.top && py < s.top + TOOL_ROW_H) {
                    if (!s.action.isEmpty()) openTool(s.action, s.title);
                    return;
                }
                for (ToolCell cell : s.items) {
                    if (x >= cell.left && x < cell.left + TOOL_CELL_W
                            && py >= cell.top && py < cell.top + TOOL_CELL_H) {
                        openTool(cell.action, cell.name);
                        return;
                    }
                }
                if (s.dark && py >= s.darkTop && py < s.darkTop + TOOL_DARK_H) {
                    toolsDark = !toolsDark;
                    getContext().getSharedPreferences("doutu_tools", 0).edit()
                            .putBoolean("dark_mode", toolsDark).apply();
                    invalidate();
                    return;
                }
                if (s.team && py >= s.teamTop && py < s.teamTop + 132) {
                    askTeamCode();
                    return;
                }
                if (py >= s.tutorialTop - 8 && py < s.tutorialTop + TOOL_TUT_H
                        && x >= 540 - TOOL_TUT_W / 2f && x <= 540 + TOOL_TUT_W / 2f) {
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.tools.ToolsTutorialActivity.class));
                    return;
                }
            }
        }

        private void askTeamCode() {
            android.widget.EditText input = new android.widget.EditText(MainActivity.this);
            input.setHint("请输入团队暗号");
            input.setSingleLine(true);
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("加入团队")
                    .setView(input)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确定", (d, w) -> toast("团队暗号：" + input.getText().toString().trim()))
                    .show();
        }

        private void openTool(String action, String name) {
            if (action == null || action.isEmpty()) return;
            if (action.startsWith("wechat:")) {
                String route = action.substring(7);
                // 微信转账：真机是「转账金额 + 收钱间隔 + 转账类型 + 键盘」的独立页，
                // 克隆页里的简化版对不上，直接进原生重画页。
                if ("tool:transfer".equals(route)) {
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.wx.WxTransferActivity.class));
                    overridePendingTransition(0, 0);
                    return;
                }
                // 微信支付：真机是「提现 / 零钱提现到账」两张记录卡，不是收付款码页。
                if ("#/wechat/payment".equals(route)) {
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.wx.WxPayRecordsActivity.class));
                    overridePendingTransition(0, 0);
                    return;
                }
                // 零钱提现：真机是「到账银行卡 + 提现金额 + 键盘」的提现页
                if ("#/wechat/wallet/withdraw".equals(route)) {
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.wx.WxWithdrawActivity.class));
                    overridePendingTransition(0, 0);
                    return;
                }
                // 创建群聊：真机进入群聊页并弹出「切换发送者的 3 种方法」教程浮层
                if ("feature:group-chat".equals(route)) {
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.wx.WxGroupChatTutorialActivity.class));
                    overridePendingTransition(0, 0);
                    return;
                }
                // 新的朋友：真机是「添加朋友 / 账号手机号搜索 / 添加手机联系人 / 近三天」列表
                if ("feature:new-friends".equals(route)) {
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.wx.WxNewFriendsActivity.class));
                    overridePendingTransition(0, 0);
                    return;
                }
                // 上传截图：真机打开的是「收付款」页（付款码 / 二维码收款 / 确认演示）
                if ("#/wechat/receive-pay".equals(route)) {
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.wx.WxReceivePayActivity.class));
                    overridePendingTransition(0, 0);
                    return;
                }
                // 红包：真机是「发红包」页（红包金额 / 恭喜发财，大吉大利 / 切换发送人 / 键盘）
                if ("tool:redpacket".equals(route)) {
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.wx.WxRedPacketActivity.class));
                    overridePendingTransition(0, 0);
                    return;
                }
                // 年账单：真机是绿色「账单」页（月账单/年账单 + 支出对比 + 支出排行榜）
                if ("feature:annual-bill".equals(route)) {
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.wx.WxAnnualBillActivity.class));
                    overridePendingTransition(0, 0);
                    return;
                }
                // 零钱明细：真机是「× / 零钱明细 / 2019年6月 ⌄ / 空列表」，不是完整账单页
                if ("#/wechat/wallet/bills".equals(route)) {
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.wx.WxChangeDetailActivity.class));
                    overridePendingTransition(0, 0);
                    return;
                }
                // 微信收款助手：真机是「个人收款服务」消息卡 + 个人收款/经营收款/更多
                if ("#/wechat/service".equals(route)) {
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.wx.WxReceiptAssistantActivity.class));
                    overridePendingTransition(0, 0);
                    return;
                }
                startActivity(com.luoyun.doutu.features.wei.WeiHomeActivity
                        .intentFor(MainActivity.this, route)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                overridePendingTransition(0, 0);
                return;
            }
            if (action.startsWith("tool:")) {
                com.luoyun.doutu.features.tools.ToolImageActivity.open(MainActivity.this, action.substring(5));
                return;
            }
            if (action.startsWith("local:")) {
                com.luoyun.doutu.features.tools.LocalToolActivity.open(MainActivity.this, action.substring(6), name);
                return;
            }
            switch (action) {
                case "alipay":
                    startActivity(com.luoyun.doutu.features.zhi.ZhiHomeActivity.intentFor(MainActivity.this));
                    break;
                case "qq":
                    // 小Q 分组标题：进 QQ 风格的主页（消息 / 频道 / 短视频 / 联系人 / 动态）。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.qq.QqHomeActivity.class));
                    break;
                case "album":
                    // 团队相册分组标题：进「云相册」主页（我加入的团队 / 三条特权 / 加号）。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.album.AlbumHomeActivity.class));
                    break;
                case "teamcreate":
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.album.CreateTeamActivity.class));
                    break;
                case "teammine":
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.album.MyTeamActivity.class));
                    break;
                case "videohome":
                    // 视频分组标题：进黑底短视频主页（分类 / 上传 / 右侧互动栏 / 底部五格）。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.video.VideoHomeActivity.class));
                    break;
                case "dycomment":
                    // 评论切片：抖音评论卡片 + 保存截图。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.video.DyCommentActivity.class));
                    break;
                case "mass":
                    // 群发助手：浅灰页头 + 空列表 + 底部「新建群发」。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.wx.MassAssistantActivity.class));
                    break;
                case "uploadimg":
                    // 上传截图：白页 + 黄色加号「上传图片」，选图后就地显示。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.wx.UploadImageActivity.class));
                    break;
                case "sentrp":
                    // 发出的红包：红色页头 + 统计区（0.00 元 / 0 个）。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.wx.SentRedPacketActivity.class));
                    break;
                case "qqrp":
                    // QQ 发红包：金额 / 祝福语 / 发送人一行。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.qq.QqRedPacketActivity.class));
                    break;
                case "qqgz":
                    // QQ 公众号：进 QQ 主页并弹表单（只有一个「消息置顶」开关）。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.qq.QqHomeActivity.class).putExtra("gzh", true));
                    break;
                case "qqedit":
                    // QQ 在线状态：真机进的是「编辑个人资料」页。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.qq.QqEditProfileActivity.class));
                    break;
                case "qqunread":
                    // QQ 未读消息红点：进 QQ 主页并弹底部未读数量键盘。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.qq.QqHomeActivity.class).putExtra("unread", true));
                    break;
                case "qqnewchat":
                    // QQ 创建聊天：进 QQ 主页并弹新建聊天表单。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.qq.QqHomeActivity.class).putExtra("newchat", true));
                    break;
                case "qqtransfer":
                    // QQ 转账：金额 / 转账说明 / 选择发送人 / 切换收钱人。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.qq.QqTransferActivity.class));
                    break;
                case "alitransfer":
                    // 支付宝转账：同一套版式 + 切换发送人 + 转账类型（收入/支出）。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.qq.QqTransferActivity.class).putExtra("ali", true));
                    break;
                case "alicontacts":
                    // 支付宝通讯录：顶部快捷入口 + 按字母分组的联系人。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.ali.AliContactsActivity.class));
                    break;
                case "alired":
                    // 支付宝红包：金额 / 祝福语 / 发送人。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.ali.AliRedPacketActivity.class));
                    break;
                case "alimsg":
                    // 支付宝公众号：消息页 + 添加公众号弹窗。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.ali.AliMsgActivity.class));
                    break;
                case "act:licai":
                    // 财富：真机 ACTION_ALIPAY_TREASURE 打开的就是支付宝首页
                    startActivity(com.luoyun.doutu.features.zhi.ZhiHomeActivity.intentFor(MainActivity.this));
                    overridePendingTransition(0, 0);
                    break;
                case "act:balance":
                    // 余额：按真机 activity_ali_balance 重画（资金安全/可用余额/提现·充值/上传截图/变动明细）
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.ali.AliBalanceActivity.class));
                    break;
                case "act:bill":
                    startActivity(com.luoyun.doutu.features.zhi.BillActivity.intentFor(MainActivity.this));
                    break;
                case "act:assets":
                    // 总资产：真机是「我的资产 + 三笔钱 + 我的保障」的独立页，按布局重画。
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.ali.AliAssetsActivity.class));
                    break;
                case "act:me":
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.ali.AliMineActivity.class));
                    break;
                case "act:paymsg":
                    // 支付助手：真机是独立页（我的账单 / 退款查询 / 支付设置）
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.ali.AliPayAssistantActivity.class));
                    break;
                case "act:billstats":
                    // 年账单：按真机 activity_ali_year_bill 重画（碳减排 / 这一年你在支付宝 / 收支分析）
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.ali.AliYearBillActivity.class));
                    break;
                case "act:withdraw":
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.ali.AliWithdrawActivity.class));
                    break;
                case "act:receipt":
                    // 商家收款记录：真机是「收款记录 / 统计 / 自定义查询」的空列表页
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.ali.AliReceiptActivity.class));
                    break;
                case "act:paysuccess":
                    // 支付成功：真机是对勾 + 支付成功 + ¥金额 + 交易方式 + 上传真实截图
                    startActivity(new Intent(MainActivity.this,
                            com.luoyun.doutu.features.ali.AliPaySuccessActivity.class));
                    break;
                case "act:sound":
                    com.luoyun.doutu.features.sound.AccountSoundActivity.open(MainActivity.this);
                    break;
                case "act:order":
                    com.luoyun.doutu.features.order.TaobaoOrderActivity.open(MainActivity.this);
                    break;
                default:
                    break;
            }
        }

        private static final class ToolCell {
            final String name, icon, action;
            float left, top;

            ToolCell(String name, String icon, String action) {
                this.name = name;
                this.icon = icon;
                this.action = action;
            }
        }

        private static final class ToolSection {
            String title, icon, action;
            boolean dark, team, replaceRow;
            final java.util.List<ToolCell> items = new java.util.ArrayList<>();
            float top, height, gridTop, darkTop, teamTop, tutorialTop;
        }

        private void drawNav(Canvas c) {
            p.setColor(Color.WHITE);
            c.drawRect(0, 2212, 1080, 2388, p);
            line(c, 0, 2212, 1080, 2212, 0xffeef0f6, 1.6f);
            navItem(c, "首页", 135, bottom == 0);
            navItem(c, "工具", 405, bottom == 4);
            navItem(c, "娱乐", 675, bottom == 1);
            navItem(c, "我", 945, bottom == 2);
        }

        /**
         * 底下那三格。参考图（home_reference）上这里本来就只有三个大字，没有图标：选中那一格
         * 套一块浅灰胶囊、字用深色粗体，其余两格走灰 —— 和顶上「微 / 支 / 抖 / Q」那条是同一套。
         */
        private void navItem(Canvas c, String label, float x, boolean selected) {
            if (selected) round(c, x - 88, 2250, x + 88, 2358, 54, 0xfff1f2f5);
            textCentered(c, label, x, 2324, 46, selected ? 0xff222226 : 0xff9aa0ac, selected);
            // 参考图上「娱乐」右上角那颗红点：不在这两格时才点着
            if (!selected && "娱乐".equals(label)) {
                p.setColor(0xffee3b30);
                c.drawCircle(x + 48, 2274, 11, p);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float rawX = e.getX() * W / Math.max(1, getWidth()), rawY = e.getY() * H / Math.max(1, getHeight());
            float x = bottom == 2 ? rawX * 1280f / W : rawX, y = bottom == 2 ? rawY * 2560f / H : rawY;
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                downY = y;
                moved = false;
                downAt = System.currentTimeMillis();
                downX = x;
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_MOVE) {
                if (bottom != 0 && detailTitle == null && Math.abs(y - downY) > 8) {
                    // 只有真的能滚的那一页才跟着手指动；滚不动也照样记成「拖动过」，
                    // 免得滑一下松手被当成点到了某个格子。
                    moved = true;
                    float limit = maxScrollForCurrentPage();
                    if (limit > 0) {
                        scroll = Math.max(0, Math.min(limit, scroll + downY - y));
                        downY = y;
                        invalidate();
                    }
                }
                return true;
            }
            if (e.getAction() != MotionEvent.ACTION_UP) return true;

            // ---- 底部标签栏永远优先响应 ----
            // 这段以前放在下面的 detailTitle 分支之后，于是「进了详情态就再也切不出去」：
            // 详情页（drawDetail 末尾调了 drawFooterAndNav）**照样画着底部三个标签**，
            // 但 `if (detailTitle != null) { ... return true; }` 会把所有点击吞掉，
            // 只有左上角「返回」有反应 —— 用户看到的就是「标签栏点着没反应」（4 台手机实测）。
            // 现在先判导航：命中就直接换页并退出详情态。
            if (bottom == 2) {
                // 注意坐标系：rawX/rawY 在上面 ACTION_DOWN 那行已经换算成 **1080x2388 设计坐标**，
                // 所以这里必须用设计坐标判断。原写法是 `rawY >= 2212f * getHeight() / H`、
                // `rawX < getWidth() / 3f` —— 把设计坐标和屏幕像素混着比：
                //   · 1080x2388 的机器上两者恰好相等，看起来正常；
                //   · 1440x3168 的机器上阈值变成 2934，而 rawY 最大只有 2388，
                //     判定永远不成立 → 停在「我」页时点底部标签栏完全没反应。
                // 这里改成和 drawNav() 同一套坐标（分割线 2212，三格中心 180/540/900）。
                if (rawY >= 2212f && !moved) {
                    bottom = rawX < 270f ? 0 : rawX < 540f ? 4 : rawX < 810f ? 1 : 2;
                    detailTitle = null;   // 切页即退出详情态，免得回来还停在详情上
                    scroll = 0;
                    invalidate();
                    performClick();
                    return true;
                }
            } else if (y >= 2212 && !moved) {
                bottom = x < 270 ? 0 : x < 540 ? 4 : x < 810 ? 1 : 2;
                detailTitle = null;
                scroll = 0;
                invalidate();
                performClick();
                return true;
            }

            if (bottom == 4) {
                // 拖动过的手指松在哪里都不算点击：以前这里不看 moved，滑一下列表就会把
                // 松手位置下面那个入口打开（真机上就是这样点进了「收付款」）。
                if (!moved) handleToolTap(x, y);
                return true;
            }

            if (detailTitle != null) {
                if (y < 210) {
                    detailTitle = null;
                    invalidate();
                }
                return true;
            }
            if (bottom == 2) {
                // 这一页画在 1280x2560 的空间里，热点也照那个空间算：rawY 是 2388 空间的，
                // 再乘一次 2560/H 才落到画的那套坐标上。
                float px = rawX * 1280f / W, py = rawY * 2560f / H;
                // 右上角「···」：账号菜单（改名 / 刷新 / 退出登录）
                if (py >= 90 && py < 190 && px >= 1090) {
                    showAccountMenu();
                    return true;
                }
                // 用户行（头像 + 名字）：未登录 → 登录/注册页；已登录 → 账号设置页。
                // 长按 = 快捷菜单（改名 / 刷新 / 退出登录）
                //
                // 2026-10-17: 头像那一圈单独拆出来 —— 点名字进设置，点**头像本身**直接换头像
                // （随机头像 4×4 + 换一批 + 从本地上传）。用户的第一反应就是「点头像换头像」，
                // 让他先去设置页再点一行是多余的一步。
                if (py >= ME_USER_TOP && py < ME_USER_BOTTOM && px >= ME_USER_LEFT && px <= ME_USER_RIGHT) {
                    float avatarCx = ME_USER_LEFT + 104f;
                    float avatarCy = (ME_USER_TOP + ME_USER_BOTTOM) / 2f;
                    float dx = px - avatarCx, dy = py - avatarCy;
                    boolean onAvatar = dx * dx + dy * dy <= 104f * 104f;   // 82 半径 + 一点余量，好点
                    if (System.currentTimeMillis() - downAt >= 500) showAccountMenu();
                    else if (onAvatar && Session.isLoggedIn()) {
                        // 注意在 NativeView 内部：this 是 View，宿主 Activity 要显式写全
                        com.luoyun.doutu.account.AvatarUpload.pick(MainActivity.this, MainActivity.this::redrawNative);
                    } else if (Session.isLoggedIn()) openSettings();
                    else openAccount();
                    return true;
                }
                // 深蓝会员卡：整张可点
                if (py >= ME_MEMBER_TOP && py < ME_MEMBER_BOTTOM) {
                    showActivation();
                    return true;
                }
                // 「常用服务」四行
                float entriesTop = ME_ENTRIES_TOP + 150;
                if (py >= entriesTop && py < entriesTop + profileEntries.length * ME_ENTRY_H) {
                    int index = (int) ((py - entriesTop) / ME_ENTRY_H);
                    if (index >= 0 && index < profileEntries.length) {
                        // 求新功能：不再进占位详情页，直接开意见反馈页（图片 + 文字 + 提交）
                        if ("求新功能".equals(profileEntries[index])) {
                            openFeedback();
                            return true;
                        }
                        // 邀请好友：和「微」页面那一格「分享好友」同一件事 —— 复制链接到剪贴板
                        if ("邀请好友".equals(profileEntries[index])) {
                            shareToFriend();
                            return true;
                        }
                        // 人工客服：进客服页（二维码 + 后台可配的文案与联系方式）
                        if ("人工客服".equals(profileEntries[index])) {
                            com.luoyun.doutu.features.service.CustomerServiceActivity.open(MainActivity.this);
                            return true;
                        }
                        // 更多设置：这一页还没做，按产品要求只弹一句「待更新」，不进占位详情页
                        if ("更多设置".equals(profileEntries[index])) {
                            toast("待更新");
                            return true;
                        }
                        detailTitle = profileEntries[index];
                        invalidate();
                    }
                    return true;
                }
                return true;
            }
            if (moved) return true;
            if (bottom == 0) {
                // 分段控件（微 / 支 / 抖 / Q）：格子那一带命中就换页；换的只是高亮和下面的功能区
                if (y >= SEGMENT_TOP - 8 && y <= SEGMENT_BOTTOM + 8) {
                    int pg = x < 280 ? 0 : x < 540 ? 1 : x < 795 ? 2 : x <= 1045 ? 3 : -1;
                    if (pg >= 0 && pg != servicePage) {
                        servicePage = pg;
                        loadHome();
                        invalidate();
                    }
                    return true;
                }
                // 微 module grid. Columns are separated at the tile centres measured on the
                // reference artwork (295 / 540 / 782); each row repeats the same columns.
                if (servicePage == PAGE_WEI) {
                    String route = null;
                    if (y > 900 && y <= 1170) {
                        route = x < 295 ? WeiHomeActivity.ROUTE_HOME
                                : x < 540 ? WeiHomeActivity.ROUTE_WALLET_BALANCE
                                : x < 782 ? WeiHomeActivity.FEATURE_GROUP_CHAT
                                // 2026-10-11: 模拟来电 格子接上原 APK 的来电页（循环铃声 + 接听/挂断）。
                                // 2026-10-16: 点了不再直接开响 —— 先进设置页选来电人/头像/铃声/时间。
                                : null;
                        if (route == null) {
                            com.luoyun.doutu.features.call.CallSetupActivity.open(MainActivity.this);
                            return true;
                        }
                    } else if (y > 1170 && y <= 1390 && x < 295) {
                        // 2026-10-11: 新的朋友 格子改成直接进「添加朋友」页（和微信主页右上角
                        // + → 添加朋友 完全同一个页面），不再是微模块里的新的朋友列表页。
                        route = WeiHomeActivity.FEATURE_ADD_FRIEND;
                    } else if (y > 1170 && y <= 1390 && x >= 295 && x < 540) {
                        // 2026-10-11: 朋友圈集赞 —— 进朋友圈选一条动态批量生成点赞。
                        route = WeiHomeActivity.FEATURE_MOMENTS_LIKES;
                    } else if (y > 1170 && y <= 1390 && x >= 540 && x < 782) {
                        // 2026-10-11: 支付成功 —— 独立支付成功页。
                        route = WeiHomeActivity.FEATURE_PAY_SUCCESS;
                    } else if (y > 1170 && y <= 1390 && x >= 782) {
                        // 2026-10-11: 自定义账单 —— 直接进「我 → 服务 → 钱包 → 账单」那一页。
                        route = WeiHomeActivity.ROUTE_BILLS;
                    } else if (y > 1390 && y <= 1610 && x < 295) {
                        // 2026-10-11: 个人信息 —— 进「小微主页 → 我 → 个人信息」那一页。
                        route = WeiHomeActivity.ROUTE_MY_PROFILE_EDIT;
                    } else if (y > 1390 && y <= 1610 && x >= 295 && x < 540) {
                        // 2026-10-11: 切换账号 —— 整页账号列表（新建账号 / 切换账号）。
                        route = WeiHomeActivity.FEATURE_SWITCH_ACCOUNT;
                    } else if (y > 1390 && y <= 1610 && x >= 782) {
                        // 2026-10-11: 年度账单 —— 进「我 → 服务 → 钱包 → 账单 → 收支统计（年账单）」。
                        route = WeiHomeActivity.FEATURE_ANNUAL_BILL;
                    } else if (y > 1390 && y <= 1610 && x >= 540 && x < 782) {
                        // 2026-10-11: 零钱提现 —— 提现金额页（输金额 → 支付密码 → 到账进度页）。
                        route = WeiHomeActivity.ROUTE_WITHDRAW;
                    } else if (y > 1610 && y <= 1830 && x < 295) {
                        // 2026-10-11: 银行卡管理 —— 银行卡列表（长按删除 / 点卡片改名改尾号 / 添加银行卡）。
                        route = WeiHomeActivity.ROUTE_BANK_CARDS;
                    } else if (y > 1610 && y <= 1830 && x >= 295 && x < 540) {
                        // 修改应用图标 —— 参考图第 4 行第 2 格（x 295..540）。从底下拉起弹窗挑一款图标；
                        // 挑中的那一款连图标带名字一起换到启动器上，所以它不进微模块，就地弹窗。
                        AppIconChooser.show(MainActivity.this);
                        return true;
                    } else if (y > 1610 && y <= 1830 && x >= 540 && x < 782) {
                        // 2026-10-15: 抖音去水印 —— 微页面第 4 行第 3 格，和娱乐页那一格进同一个解析页。
                        openParseWithGate();
                        return true;
                    } else if (y > 1610 && y <= 1830 && x >= 782) {
                        // 2026-10-16: 分享好友 —— 微页面第 4 行第 4 格。
                        // 点一下把后台配的链接复制到剪贴板，再弹一句「已复制」的提示。
                        shareToFriend();
                        return true;
                    }
                    // 第 4 行最后一格（分享好友）以前落到下面那句占位提示上，现在已经接上了。
                    if (route != null) {
                        startActivity(WeiHomeActivity.intentFor(MainActivity.this, route)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        // Show the local loading skeleton immediately, skipping the system Activity transition.
                        overridePendingTransition(0, 0);
                        return true;
                    }
                }
                if (servicePage == PAGE_ZHI) {
                    // 支模块宫格：四列四行，格子的行列分界照参考图上的图标中心量出来的
                    // （列 295 / 540 / 782，行 1180 / 1400 / 1610）。宫格里的图标入口
                    // 与小支主页内已有的界面一一对应，点外面这格就等于直接进里面那一页。
                    int zcol = x < 295 ? 0 : x < 540 ? 1 : x < 782 ? 2 : 3;
                    int zrow = y <= 940 || y > 1830 ? -1
                            : y <= 1180 ? 0 : y <= 1400 ? 1 : y <= 1610 ? 2 : 3;
                    Intent zhi = zrow < 0 ? null : zhiGridIntent(zrow, zcol);
                    if (zhi != null) {
                        startActivity(zhi);
                        overridePendingTransition(0, 0);
                        return true;
                    }
                }
                // 2026-10-16：宫格里还没有对应页面的格子点了就是「什么也不做」——
                // 原来这里弹一句占位提示，产品要求把这类「演示功能」的弹窗全部去掉。
                return true;
            }
            if (bottom == 1) {
                FeatureRow row = rowAt(y + scroll);
                if (row != null) {
                    if (isSwitchRow(row)) {
                        // 开关在行的右端（x 872..1004），点这一带有反应，点左边的字仍旧进详情页
                        if (x >= 830) {
                            row.enabled = !row.enabled;
                            if ("tease".equals(row.key)) {
                                // 2026-10-17: 这一格开着的时候，「小微主页 → 加号 → 扫一扫」
                                // 只弹一句「摄像头不可用」，不再往下走付款页。
                                com.luoyun.doutu.features.play.PlayStore.cameraBroken(row.enabled);
                            }
                            toast(row.title + (row.enabled ? " 已开启" : " 已关闭"));
                            invalidate();
                        } else {
                            detailTitle = row.title;
                            invalidate();
                        }
                    } else if ("parse".equals(row.key)) {
                        // 娱乐页那一格：和微页面「抖音去水印」走同一套门禁
                        openParseWithGate();
                    } else if ("call".equals(row.key)) {
                        // 2026-10-16: 娱乐页的「模拟朋友来电」= 微模块的「模拟来电」那一格，
                        // 同一页、同一套铃声与接听/挂断，不再进那张占位详情页。
                        openWeiCall();
                    } else if ("order".equals(row.key)) {
                        // 2026-10-17: 实用功能第 2 条 —— 照用户给的两张订单截图做的详情页，
                        // 页面上每一项都能点着改，改完存进 OrderStore（跟着账号走）。
                        com.luoyun.doutu.features.order.TaobaoOrderActivity.open(MainActivity.this);
                    } else if ("sound".equals(row.key)) {
                        // 原包路由 /account_sound（显示名「支付宝到账声音」）：金额拆成音素 mp3 顺序播报
                        com.luoyun.doutu.features.sound.AccountSoundActivity.open(MainActivity.this);
                    } else if ("shutdown".equals(row.key)) {
                        // 原包路由 /shutdown（显示名「模拟关机」）：纯黑全屏，隐藏系统栏 + 屏幕常亮
                        com.luoyun.doutu.features.shutdown.ShutdownActivity.open(MainActivity.this);
                    } else {
                        detailTitle = row.title;
                        invalidate();
                    }
                    performClick();
                }
                return true;
            }
            // The target page has one account card, one service row and six list rows.
            if (y >= 230 && y < 503) {
                detailTitle = "个人资料";
                invalidate();
                return true;
            }
            if (y >= 837 && y < 1069) {
                detailTitle = "服务";
                invalidate();
                return true;
            }
            if (y >= 1069 && y < 1228) {
                detailTitle = "收藏";
                invalidate();
                return true;
            }
            if (y >= 1228 && y < 1387) {
                detailTitle = "朋友圈";
                invalidate();
                return true;
            }
            if (y >= 1387 && y < 1546) {
                detailTitle = "作品";
                invalidate();
                return true;
            }
            if (y >= 1546 && y < 1748) {
                detailTitle = "小店与卡包";
                invalidate();
                return true;
            }
            if (y >= 1748 && y < 1907) {
                detailTitle = "表情";
                invalidate();
                return true;
            }
            if (y >= 1939 && y < 2098) {
                detailTitle = "设置";
                invalidate();
                return true;
            }
            return true;
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        /** 娱乐页落在哪一行：读的就是画出来那份坐标（y 已经加上滚动量）。 */
        private FeatureRow rowAt(float y) {
            for (EntertainmentRow box : entertainmentRows)
                if (y >= box.top && y < box.bottom) return box.row;
            return null;
        }

        /**
         * 会员面板（真实状态）。
         *
         * 会员只决定「有没有水印」，不影响能不能用：未开通时界面叠加遮罩、成品带水印，
         * 用户可以选择不开会员继续用；开通后水印消失。开通动作只能在后台完成。
         */
        private void showActivation() {
            if (!Session.isLoggedIn()) {
                openAccount();
                return;
            }
            boolean member = Session.isActivated();
            String title = member ? "账号与会员" : "未开通会员";
            StringBuilder sb = new StringBuilder();
            sb.append("用户名：").append(Session.username().isEmpty() ? Session.displayName() : Session.username()).append('\n');
            if (!Session.phone().isEmpty()) sb.append("手机号：").append(Session.phone()).append('\n');
            sb.append("状态：").append(Session.statusText()).append('\n');
            if (member) {
                sb.append("到期：").append(Session.expireAt().isEmpty() ? "-" : Session.expireAt());
            } else {
                sb.append('\n').append("未开通会员也可以正常使用，只是界面与成品会带水印。\n");
                sb.append("开通会员后水印会自动消失，请联系客服在后台为你开通。");
            }
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(title)
                    .setMessage(sb.toString())
                    .setNegativeButton("关闭", null)
                    .setNeutralButton("修改用户名", (d, w) -> renameDialog())
                    .setPositiveButton("刷新状态", (d, w) -> refreshAccount(true))
                    .show();
        }

        /**
         * 去水印的统一入口（娱乐页「短视频一键去水印」与微页面「抖音去水印」共用）：
         * 没登录先去登录，登录了没开通会员先引导开通，都满足才进解析页。
         * 服务端也会兜底（未开通返回 2001），客户端这层只是少跑一趟。
         */
        private void openParseWithGate() {
            if (!Session.isLoggedIn()) {
                toast("请先在「我」页面登录 / 注册");
                bottom = 2;
                scroll = 0;
                invalidate();
            } else if (!Session.isActivated()) {
                toast("去水印是会员功能，请先开通会员");
                showActivation();
            } else {
                openParse();
            }
        }

        /** 长按身份条：退出登录等低频操作。 */        private void showAccountMenu() {
            if (!Session.isLoggedIn()) {
                openAccount();
                return;
            }
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle(Session.displayName())
                    .setItems(new String[]{"修改用户名", "刷新会员状态", "退出登录"}, (d, which) -> {
                        if (which == 0) renameDialog();
                        else if (which == 1) refreshAccount(true);
                        else new AlertDialog.Builder(MainActivity.this)
                                .setTitle("退出登录")
                                .setMessage("退出后仍然可以继续使用，只是不再记录你的使用额度。")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("退出", (dd, ww) -> DoutuApi.logout(MainActivity.this, r -> {
                                    invalidate();
                                    com.luoyun.doutu.account.WatermarkOverlay.attachIfNeeded(MainActivity.this);
                                    toast("已退出登录");
                                }))
                                .show();
                    })
                    .show();
        }

        /**
         * 修改用户名：改完同步到服务端，成功后就地刷新界面。
         * 用户名是登录标识 —— 改完新名字立刻能登录、旧名字立刻失效，手机号不受影响。
         */
        private void renameDialog() {
            final android.widget.EditText input = new android.widget.EditText(MainActivity.this);
            input.setSingleLine(true);
            input.setText(Session.username());
            input.setSelection(input.getText().length());
            input.setHint("2~20 位，中文/字母/数字/下划线");
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            android.widget.FrameLayout wrap = new android.widget.FrameLayout(MainActivity.this);
            wrap.setPadding(pad, pad / 2, pad, 0);
            wrap.addView(input);
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("修改用户名")
                    .setMessage("用户名同时也是登录账号，改完可立即用新用户名登录，手机号不受影响。")
                    .setView(wrap)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("保存", (d, w) -> {
                        String name = input.getText().toString().trim();
                        String err = LoginActivity.checkUsername(name);
                        if (err != null) {
                            toast(err);
                            return;
                        }
                        DoutuApi.changeUsername(MainActivity.this, name, result -> {
                            if (!result.ok()) {
                                toast(result.msg == null || result.msg.isEmpty() ? "修改失败" : result.msg);
                                return;
                            }
                            Session.setUsername(name);
                            Session.updateUser(result.user());
                            invalidate();
                            com.luoyun.doutu.account.WatermarkOverlay.attachIfNeeded(MainActivity.this);
                            toast("用户名已修改为 " + name);
                        });
                    })
                    .show();
        }

        private void toast(String s) {
            Toast.makeText(MainActivity.this, s, Toast.LENGTH_SHORT).show();
        }
    }
}

