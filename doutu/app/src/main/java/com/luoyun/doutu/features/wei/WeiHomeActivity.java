package com.luoyun.doutu.features.wei;


import android.app.Activity;
import android.content.res.AssetManager;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.content.Intent;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.view.ViewConfiguration;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebStorage;

import com.luoyun.doutu.features.call.CallNotifications;
import com.luoyun.doutu.features.call.CallScheduler;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.util.Base64;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

/**
 * Hosts the bundled fake-world WeChat experience.
 *
 * The web app is packaged under app/src/main/assets/fake_world. A virtual HTTPS
 * origin is used instead of file:// so Chromium permits ES modules and CSS to
 * load with normal same-origin rules while the app remains completely offline
 * for its core UI.
 */
public final class WeiHomeActivity extends Activity {
    private static final String APP_ORIGIN = "https://fake-world.local";
    private static final String ENTRY_URL = APP_ORIGIN + "/index.html#/wechat";
    /** In-page route for the WeChat-style chat list (the 小微主页 tile). */
    public static final String ROUTE_HOME = "#/wechat";
    /** In-page route that lands on the wallet balance (零钱) page (the 装逼钱包 tile). */
    public static final String ROUTE_WALLET_BALANCE = "#/wechat/wallet/balance";
    /** Locally rendered WeChat screens that have no route in the bundled build. */
    public static final String FEATURE_GROUP_CHAT = "feature:group-chat";
    public static final String FEATURE_NEW_FRIENDS = "feature:new-friends";
    /** 添加朋友 —— 和微信主页右上角 + → 添加朋友 是同一个页面（openAddFriendDialog）。 */
    public static final String FEATURE_ADD_FRIEND = "feature:add-friend";
    /** 模拟来电（语音）—— 全屏来电页 + 循环铃声，素材取自原 APK。 */
    public static final String FEATURE_CALL = "feature:call";
    /** 模拟来电（视频）—— 同一套页面的视频形态。 */
    public static final String FEATURE_VIDEO_CALL = "feature:video-call";
    /** 朋友圈集赞 —— 进朋友圈选一条动态，批量生成点赞的人。 */
    public static final String FEATURE_MOMENTS_LIKES = "feature:moments-likes";
    /** 支付成功 —— 独立的支付成功页（复用扫一扫付款成功那层）。 */
    public static final String FEATURE_PAY_SUCCESS = "feature:pay-success";
    /** 自定义账单 —— 直接落到「我 → 服务 → 钱包 → 账单」那个页面。 */
    public static final String ROUTE_BILLS = "#/wechat/wallet/bills";
    /** 个人信息 —— 「微 → 我 → 个人信息」那一页（从 我 页面点名字进入的个人信息页）。 */
    public static final String ROUTE_MY_PROFILE_EDIT = "#/wechat/my/profile-edit";
    /** 切换账号 —— 原生首页宫格「切换账号」打开整页账号列表（新建 / 切换账号）。 */
    public static final String FEATURE_SWITCH_ACCOUNT = "feature:switch-account";
    /** 年度账单 —— 原生首页宫格「年度账单」直接进账单页里的「收支统计 → 年账单」。 */
    public static final String FEATURE_ANNUAL_BILL = "feature:annual-bill";
    /** 零钱提现 —— 原生首页宫格「零钱提现」打开提现金额页（键盘输金额 → 支付密码 → 结果页）。 */
    public static final String ROUTE_WITHDRAW = "#/wechat/wallet/withdraw";
    /** 银行卡管理 —— 原生首页宫格「银行卡管理」打开银行卡列表（长按删除 / 点击改名改尾号 / 添加银行卡）。 */
    public static final String ROUTE_BANK_CARDS = "#/wechat/wallet/cards";
    private static final String EXTRA_ROUTE = "fw_route";
    private static final String EXTRA_ROUTE_NONCE = "fw_route_nonce";
    private static final int REQUEST_PICK_AVATAR = 4107;
    private static final int REQUEST_GALLERY_PERMISSION = 4108;
    private static final int REQUEST_SAVE_IMAGE_PERMISSION = 4109;
    private static final int REQUEST_CAMERA_PERMISSION = 4110;
    private static final int GALLERY_LIMIT = 160;
    /** Encoded data-URLs keyed by "mediaId:bound" so re-opening a photo is instant. */
    private static final java.util.LinkedHashMap<String,String> GALLERY_CACHE = new java.util.LinkedHashMap<>();
    private static WebView cachedWebView;
    private static boolean preloadStarted;

    /** 待执行的桥脚本与跳转路由（页面加载完成后再执行，冷启动也能生效）。 */
    private static volatile String bridgeScript;
    private static volatile String bridgeRoute;

    /**
     * 页面就绪后执行一段 JS，然后跳到指定路由。
     * 调用时页面还没起来也没关系，等 onPageFinished 时再执行。
     */
    public static void scheduleBridge(String script, String route) {
        bridgeScript = script;
        bridgeRoute = route;
        WeiHomeActivity inst = liveInstance;
        if (inst != null) inst.drainBridge();
    }

    /** 写完数据后需要整页重载，重载完成再跳会话页。 */
    private static volatile String pendingRouteAfterReload;

    private void drainBridge() {
        String script = bridgeScript;
        String route = bridgeRoute;
        if (script == null && route == null) return;
        bridgeScript = null;
        bridgeRoute = null;
        if (script != null && webView != null) {
            try {
                webView.evaluateJavascript(script, null);
            } catch (Throwable ignored) { }
            // 数据刚写进 localStorage，克隆的 React 状态还没读到它 —— 整页重载后路由才认这个新好友
            pendingRouteAfterReload = route;
            try {
                webView.reload();
            } catch (Throwable ignored) { }
            return;
        }
        if (route != null) {
            pendingRoute = route;
            applyPendingRoute();
        }
    }

    /**
     * 给原生页面用的执行 JS 入口：在克隆页面里写入数据或跳转。
     * 没有存活的 WebView 时返回 false，调用方自己兜底。
     */
    public static boolean execJs(String script) {
        if (script == null) return false;
        WeiHomeActivity inst = liveInstance;
        final WebView view = (inst != null && inst.webView != null) ? inst.webView : cachedWebView;
        if (view == null) return false;
        view.post(() -> {
            try {
                view.evaluateJavascript(script, null);
            } catch (Throwable ignored) { }
        });
        return true;
    }
    /**
     * 当前活着的页面实例。preload 是在 MainActivity 里做的，那时候还没有 WeiHomeActivity，
     * 所以注入给 WebView 的是一个静态转发桥，它每次调用都来这里取「现在真正在用的那一份」。
     */
    private static volatile WeiHomeActivity liveInstance;
    /** 上一行那个转发桥；注入对象始终是同一个，所以不需要为了暴露 JS 接口而 reload 页面。 */
    private static final ForwardingBridge FORWARDING_BRIDGE = new ForwardingBridge();
    /** 本实例自己那份真正干活的桥（转发桥把调用转给它）。 */
    private AppNavigationBridge bridgeDelegate;
    private WebView webView;
    /** WebView getUserMedia request waiting for the runtime camera permission. */
    private android.webkit.PermissionRequest pendingWebPermission;
    private volatile boolean payPageBarsVisible = false;
    // The bundled page reports when its long-press message menu is up so the
    // native long-press watcher stays out of the way while it is.
    private volatile boolean messageMenuOpen = false;
    private ValueCallback<Uri[]> avatarFileCallback;
    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;
    private float touchDownX;
    private float touchDownY;
    private float lastTouchX;
    private float lastTouchY;
    private long touchDownAt;
    private boolean touchCandidate;
    private boolean nativeLongPressTriggered;
    /** Route requested by the last startActivity() call; applied once the page is mounted. */
    private String pendingRoute;
    /**
     * 首页「微」宫格深链进来的页内路由（形如 #/wechat/wallet/bills）。停在它的根部
     * 按返回键时直接退回「微」那一页，而不是退到用户没来过的 我 → 服务 → 钱包。
     */
    private String deepLinkRoute;
    private long consumedRouteNonce;
    /**
     * 「模拟来电」设置页（或它设的闹钟）带过来的来电参数：谁打来的、什么头像、放哪首铃声。
     * 页面起来之前先攥着，等 {@code window.__fwOpenCall} 能用了再交过去。
     */
    private String pendingCallJson;

    /**
     * Builds the Intent used by the app shell to open the 微 module at a specific in-page route.
     * The nonce lets a repeated tap on the same tile re-apply the route even when the page is
     * already mounted.
     */
    public static Intent intentFor(Context context, String route) {
        Intent intent = new Intent(context, WeiHomeActivity.class);
        if (route != null && !route.trim().isEmpty()) {
            intent.putExtra(EXTRA_ROUTE, route.trim());
            intent.putExtra(EXTRA_ROUTE_NONCE, System.nanoTime());
        }
        return intent;
    }

    /**
     * 给别的页面（来电设置页）用：在**已经预热好的那个页面**里跑一段 JS 并把结果回调出去。
     *
     * <p>为什么要这样绕：微模块的通讯录、来电界面都活在网页里，数据在页面的 localStorage 里，
     * 从磁盘上读不到。而 MainActivity 启动时就把这个 WebView 预热好了、和调用方还是同一个进程，
     * 所以直接问它一句最快也最准。页面还没起来时回调 null，调用方自己用兜底数据。
     *
     * <p>回调在主线程（WebView 本来就在主线程上跑）。
     */
    public static void evaluateInPage(String script, android.webkit.ValueCallback<String> callback) {
        WebView view = cachedWebView;
        if (view == null) {
            if (callback != null) callback.onReceiveValue(null);
            return;
        }
        try {
            view.evaluateJavascript(script, callback);
        } catch (Exception e) {
            if (callback != null) callback.onReceiveValue(null);
        }
    }

    /** Warm and attach the bundled page behind the opaque native home so Chromium can render it before navigation. */
    public static void preload(Activity activity, FrameLayout host) {
        synchronized (WeiHomeActivity.class) {
            if (cachedWebView != null || preloadStarted) return;
            preloadStarted = true;
        }
        migrateStorageOnce(activity.getApplicationContext());
        WebView warm = new WebView(activity);
        configureWebSettings(warm);
        warm.setBackgroundColor(Color.rgb(237, 237, 237));
        warm.setOverScrollMode(View.OVER_SCROLL_NEVER);
        warm.setWebViewClient(new BundledAssetWebViewClient(activity.getAssets()));
        warm.setAlpha(0.01f); // NativeView is opaque and remains the top child.
        warm.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // 桥必须在**第一次 loadUrl 之前**注入，否则页面第一次渲染时 window.DoutuApp 是 undefined，
        // 只能靠进页面后再 reload 一次才能暴露 —— 那一次 reload 正是「进小微主页要等几秒」的元凶。
        // 这里注入的是个转发壳：真正干活的是当前活着的 WeiHomeActivity 自带的那份桥。
        warm.addJavascriptInterface(FORWARDING_BRIDGE, "DoutuApp");
        synchronized (WeiHomeActivity.class) { cachedWebView = warm; }
        host.addView(warm, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        warm.loadUrl(ENTRY_URL);
    }
    private static void migrateStorageOnce(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("wei_home_migration", MODE_PRIVATE);
        if (!prefs.getBoolean("storage_reset_v2", false)) {
            WebStorage.getInstance().deleteAllData();
            prefs.edit().putBoolean("storage_reset_v2", true).apply();
        }
    }

    private static WebView obtainWebView(Context context) {
        synchronized (WeiHomeActivity.class) {
            if (cachedWebView != null) {
                WebView cached = cachedWebView;
                cachedWebView = null;
                return cached;
            }
            preloadStarted = false;
        }
        WebView created = new WebView(context);
        configureWebSettings(created);
        return created;
    }

    private static void configureWebSettings(WebView view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(false);
        settings.setLoadWithOverviewMode(false);
        // The 拍摄 viewfinder mounts a muted <video> straight from getUserMedia; without
        // this WebView still demands a fresh user gesture before it will play.
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        configureWindow(getWindow());
        consumeRouteIntent(getIntent());
        consumeCallIntent(getIntent());

        migrateStorageOnce(getApplicationContext());

        webView = obtainWebView(this);
        webView.setAlpha(1f);
        if (webView.getParent() instanceof ViewGroup) {
            ((ViewGroup) webView.getParent()).removeView(webView);
        }
        webView.setBackgroundColor(Color.rgb(237, 237, 237));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // Do not let Chromium replace the page menu with its text-selection callout.
        // The page owns the context menu; the native timer below is only a
        // reliable bridge for WebView builds that cancel DOM touch events early.
        webView.setLongClickable(false);
        // Disable Chromium's own haptic path and emit one deterministic pulse
        // from the same callback that opens the custom menu.
        webView.setHapticFeedbackEnabled(false);
        webView.setOnLongClickListener(null);
        webView.setOnTouchListener((v, event) -> {
            final int action = event.getActionMasked();
            // While the page's message menu is open every touch belongs to it. In
            // particular the release must reach the WebView, otherwise a slightly
            // long tap on a menu entry is swallowed and never turns into a click.
            if (messageMenuOpen) {
                cancelNativeLongPress();
                return false;
            }
            if (action == MotionEvent.ACTION_DOWN) {
                cancelNativeLongPress();
                touchDownX = event.getX();
                touchDownY = event.getY();
                lastTouchX = touchDownX;
                lastTouchY = touchDownY;
                touchDownAt = android.os.SystemClock.uptimeMillis();
                touchCandidate = true;
                nativeLongPressTriggered = false;
                longPressRunnable = this::triggerNativeLongPress;
                longPressHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
            } else if (action == MotionEvent.ACTION_MOVE && touchCandidate) {
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                float dx = lastTouchX - touchDownX;
                float dy = lastTouchY - touchDownY;
                int slop = ViewConfiguration.get(webView.getContext()).getScaledTouchSlop();
                // Allow ordinary finger drift while still cancelling an intentional
                // vertical swipe. The WebView remains responsible for scrolling.
                int longPressSlop = Math.max(slop * 4, 120);
                if (dx * dx + dy * dy > (float) longPressSlop * longPressSlop) {
                    cancelNativeLongPress();
                }
            } else if (action == MotionEvent.ACTION_UP) {
                // If Android delivered the up event just before the delayed callback,
                // finish a press that reached Android's long-press threshold instead of opening the chat.
                long heldFor = android.os.SystemClock.uptimeMillis() - touchDownAt;
                if (touchCandidate && heldFor >= Math.max(400L, ViewConfiguration.getLongPressTimeout() - 50L)) {
                    triggerNativeLongPress();
                }
                boolean consumeRelease = nativeLongPressTriggered;
                cancelNativeLongPress();
                // Consuming only the release after a native long press prevents the
                // WebView from synthesizing a normal row click and closing our menu.
                return consumeRelease;
            } else if (action == MotionEvent.ACTION_CANCEL) {
                // Do not cancel solely because Chromium hands the gesture to its
                // scrolling/gesture recognizer. MOVE distance is the scroll guard.
            }
            return false;
        });
        webView.addJavascriptInterface(FORWARDING_BRIDGE, "DoutuApp");
        // 预热那份桥是靠静态字段找「当前活着的页面」的，这里把指针交给自己
        liveInstance = this;
                webView.setWebViewClient(new BundledAssetWebViewClient(getAssets()) {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                Log.e("WeiHomeWeb", "PAGE_STARTED " + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.e("WeiHomeWeb", "PAGE_FINISHED " + url);
                drainBridge();
                if (pendingRouteAfterReload != null) {
                    pendingRoute = pendingRouteAfterReload;
                    pendingRouteAfterReload = null;
                }
                applyPendingRoute();
                applyPendingCall();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                Log.e("WeiHomeWeb", "LOAD_ERROR " + request.getUrl() + " code=" + error.getErrorCode() + " desc=" + error.getDescription());
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                Log.e("WeiHomeWeb", message.message() + " @" + message.sourceId() + ":" + message.lineNumber());
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (avatarFileCallback != null) avatarFileCallback.onReceiveValue(null);
                avatarFileCallback = filePathCallback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                try {
                    startActivityForResult(intent, REQUEST_PICK_AVATAR);
                } catch (RuntimeException error) {
                    avatarFileCallback = null;
                    filePathCallback.onReceiveValue(null);
                    return false;
                }
                return true;
            }

            /**
             * 2026-10-11: the 拍摄 tile runs a getUserMedia viewfinder, so the page asks for
             * video (and audio while recording). Runtime permission comes first, then the
             * WebView request is granted with whichever resources we actually hold.
             */
            @Override
            public void onPermissionRequest(final android.webkit.PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean wantsVideo = false;
                    boolean wantsAudio = false;
                    for (String resource : request.getResources()) {
                        if (android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) wantsVideo = true;
                        if (android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) wantsAudio = true;
                    }
                    // 模拟来电只开麦克风（音频）时也要放行，所以音频同样算有效请求。
                    if (!wantsVideo && !wantsAudio) { request.deny(); return; }
                    boolean missing = (wantsVideo && !hasCameraPermission())
                            || (wantsAudio && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED);
                    if (!missing) { request.grant(cameraResources(request)); return; }
                    pendingWebPermission = request;
                    requestPermissions(
                            new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO},
                            REQUEST_CAMERA_PERMISSION);
                });
            }
        });

        configureWebSettings(webView);
        setContentView(webView);
        installKeyboardInsetsBridge();
        String currentUrl = webView.getUrl();
        if (currentUrl == null || !currentUrl.startsWith(ENTRY_URL)) {
            webView.loadUrl(ENTRY_URL);
        } else {
            // 预热过的 WebView 已经把这个页面渲染好了。以前这里要 reload 一次（为了让
            // addJavascriptInterface 生效），代价是刚渲染好的整页被清空重来 —— 用户看到的就是
            // 「进小微主页要等几秒」。现在桥在 preload 里就注入了，不需要 reload，
            // 直接把可能要跳的页内路由补上就行（深链进来的那一套）。
            applyPendingRoute();
        }
        // 来电参数（设置页点「开始来电」/ 定时闹钟到点）单独走一条：它要在页面能跑 __fwOpenCall 之后再交过去
        applyPendingCall();
    }

    private boolean hasCameraPermission() {
        return checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    /** Only hand back the resources we actually hold: granting an unheld one fails the lot. */
    private String[] cameraResources(android.webkit.PermissionRequest request) {
        java.util.List<String> granted = new java.util.ArrayList<>();
        boolean audio = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        for (String resource : request.getResources()) {
            if (android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) granted.add(resource);
            else if (android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource) && audio) granted.add(resource);
        }
        return granted.toArray(new String[0]);
    }

    private void triggerNativeLongPress() {
        if (!touchCandidate || nativeLongPressTriggered || webView == null) return;
        nativeLongPressTriggered = true;
        touchCandidate = false;
        // Keep the tactile feedback and popup on the same long-press callback.
        webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        longPressRunnable = null;
        String script = "window.__fwNativeOpenChatContextAt && window.__fwNativeOpenChatContextAt("
                + lastTouchX + "," + lastTouchY + "," + webView.getWidth() + "," + webView.getHeight() + ")";
        webView.evaluateJavascript(script, null);
    }

    private void cancelNativeLongPress() {
        touchCandidate = false;
        if (longPressRunnable != null) {
            longPressHandler.removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    /** Reads the route carried by an incoming Intent exactly once per nonce. */
    private void consumeRouteIntent(Intent intent) {
        if (intent == null) return;
        long nonce = intent.getLongExtra(EXTRA_ROUTE_NONCE, 0L);
        if (nonce == 0L || nonce == consumedRouteNonce) return;
        String route = intent.getStringExtra(EXTRA_ROUTE);
        if (route == null || route.trim().isEmpty()) { deepLinkRoute = null; return; }
        consumedRouteNonce = nonce;
        pendingRoute = route.trim();
        // 页内路由（#/...）才算深链入口；feature:xxx 是叠在微信页上的浮层，返回键另有处理。
        // 年度账单是「账单页 + 年账单统计」这一层，返回键按账单页这个深链根来算。
        deepLinkRoute = FEATURE_ANNUAL_BILL.equals(pendingRoute) ? ROUTE_BILLS
                : pendingRoute.charAt(0) == '#' ? trimTrailingSlash(pendingRoute) : null;
    }

    /**
     * 「模拟来电」设置页 / 定时闹钟带来的来电参数：交给页面的 {@code __fwOpenCall}，
     * 由它把「谁打来的、什么头像、放哪首铃声」铺进那场模拟来电。
     *
     * <p>页面可能还没起来（闹钟冷启动时尤其常见），所以拿不到执行结果就 100ms 后再试，
     * 最多 2.5 秒，和 {@link #applyPendingRoute} 一个套路。
     */
    private void applyPendingCall() {
        applyPendingCall(0);
    }
    private void applyPendingCall(int attempt) {
        final String payload = pendingCallJson;
        if (payload == null || webView == null) return;
        String script = "(function(){try{return window.__fwOpenCall ? window.__fwOpenCall("
                + jsString(payload) + ") === true : false}catch(e){return false}})()";
        webView.evaluateJavascript(script, result -> {
            if (result != null && result.contains("true")) {
                pendingCallJson = null;
                return;
            }
            if (attempt < 25 && webView != null) {
                webView.postDelayed(() -> applyPendingCall(attempt + 1), 100);
            }
        });
    }

    private static String trimTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') end--;
        return value.substring(0, end);
    }

    /** 这一次启动是不是带着「模拟来电」的参数（设置页点开始 / 定时闹钟到点）。 */
    private void consumeCallIntent(Intent intent) {
        if (intent == null) return;
        String payload = intent.getStringExtra(CallScheduler.EXTRA_CALL);
        if (payload == null || payload.trim().isEmpty()) return;
        intent.removeExtra(CallScheduler.EXTRA_CALL);
        pendingCallJson = payload.trim();
        // 来电界面已经真的起来了，兜底那条通知就该撤掉
        CallNotifications.cancel(this);
    }

    /** True while the WebView sits on the exact page the shell deep-linked into. */
    private boolean isAtDeepLinkRoot() {
        if (deepLinkRoute == null || webView == null) return false;
        String url = webView.getUrl();
        if (url == null) return false;
        int hashAt = url.indexOf('#');
        if (hashAt < 0) return false;
        return trimTrailingSlash(url.substring(hashAt)).equals(deepLinkRoute);
    }

    /** Hands the pending route to the bundled page so the shell tiles can deep-link into it. */
    private void applyPendingRoute() {
        applyPendingRoute(0);
    }

    /**
     * 把待跳转的页内路由交给页面。页面 JS 可能还没起来（预热的 WebView 刚被接过来时很常见），
     * 所以拿不到执行结果就 100ms 后再试，最多 2.5 秒 —— 以前是「onPageFinished 回调一次」，
     * 而预热过的页面不会再触发 onPageFinished，深链会静默失效。
     */
    private void applyPendingRoute(int attempt) {
        final String route = pendingRoute;
        if (route == null || webView == null) return;
        String script = "(function(){try{var t=" + jsString(route) + ";"
                + "if(t.charAt(0)==='#'){if(window.__fwOpenRoute){window.__fwOpenRoute(t);return true;}"
                + "location.hash=t;return true;}"
                // 工具页的「公众号」：在微信主页上弹新建公众号表单。
                + "if(t==='tool:gzh'){if(window.__fwOpenGzhDialog){window.__fwOpenGzhDialog();return true;}}"
                // 工具页的「红包 / 转账」：直接挂微信那套发红包/转账页（不用先进某个聊天）。
                + "if(t.indexOf('tool:')===0){if(window.__fwOpenToolPay){window.__fwOpenToolPay(t.slice(5));return true;}}"
                + "if(t.indexOf('feature:')===0&&window.__fwOpenFeature){window.__fwOpenFeature(t.slice(8));return true;}"
                + "return false;}catch(e){return false;}})()";
        webView.evaluateJavascript(script, result -> {
            if (result != null && result.contains("true")) {
                pendingRoute = null;
                return;
            }
            if (attempt < 25 && webView != null) {
                webView.postDelayed(() -> applyPendingRoute(attempt + 1), 100);
            }
        });
    }

    private static String jsString(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('\'');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\'' || ch == '\\') out.append('\\');
            out.append(ch);
        }
        return out.append('\'').toString();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeRouteIntent(intent);
        consumeCallIntent(intent);
        applyPendingRoute();
        applyPendingCall();
    }

    /**
     * 转发桥：注入给 WebView 的那个 "DoutuApp" 对象。
     *
     * <p>它的存在只有一个原因 —— preload 的时候还没有 WeiHomeActivity 实例，没法注入
     * 那份非静态的内层桥。所以先注入这个静态壳，等页面真的起来了再从 {@link #liveInstance}
     * 里找到当前实例，把调用转给它。这样 JS 接口在**第一次 loadUrl 之前**就已经存在，
     * 页面不用为了暴露接口而 reload（那一次 reload 就是「进小微主页要等几秒」的原因）。
     *
     * <p>页面还没起来时（预热阶段）所有调用都走安全默认值：读接口返回空、写接口什么也不做。
     */
    private static final class ForwardingBridge {
        private static AppNavigationBridge target() {
            WeiHomeActivity host = liveInstance;
            if (host == null) return null;
            if (host.bridgeDelegate == null) host.bridgeDelegate = host.new AppNavigationBridge();
            return host.bridgeDelegate;
        }

        @JavascriptInterface
        public String downloadAvatarCacheUrl(String sourceUrl) {
            AppNavigationBridge bridge = target();
            return bridge == null ? "" : bridge.downloadAvatarCacheUrl(sourceUrl);
        }

        @JavascriptInterface
        public String downloadAvatarDataUrl(String sourceUrl) {
            AppNavigationBridge bridge = target();
            return bridge == null ? "" : bridge.downloadAvatarDataUrl(sourceUrl);
        }

        @JavascriptInterface
        public String getGalleryItems() {
            AppNavigationBridge bridge = target();
            return bridge == null ? "[]" : bridge.getGalleryItems();
        }

        @JavascriptInterface
        public String getGalleryImage(String id, int maxSize) {
            AppNavigationBridge bridge = target();
            return bridge == null ? "" : bridge.getGalleryImage(id, maxSize);
        }

        @JavascriptInterface
        public void requestSaveImagePermission() {
            AppNavigationBridge bridge = target();
            if (bridge != null) bridge.requestSaveImagePermission();
        }

        @JavascriptInterface
        public String saveImageToGallery(String dataUrl) {
            AppNavigationBridge bridge = target();
            return bridge == null ? "denied" : bridge.saveImageToGallery(dataUrl);
        }

        @JavascriptInterface
        public boolean hasGalleryPermission() {
            AppNavigationBridge bridge = target();
            return bridge != null && bridge.hasGalleryPermission();
        }

        /**
         * 娱乐页「撩妹加V」开着没有 —— 开着就表示「这台机器的摄像头是坏的」。
         * 扫一扫那一步读它：true 的时候页面只弹提示，不进付款页。
         */
        @JavascriptInterface
        public boolean isScanDisabled() {
            return com.luoyun.doutu.features.play.PlayStore.cameraBroken();
        }

        @JavascriptInterface
        public void setCallAudioMode(boolean inCall, boolean speakerOn) {
            AppNavigationBridge bridge = target();
            if (bridge != null) bridge.setCallAudioMode(inCall, speakerOn);
        }

        @JavascriptInterface
        public void httpGet(String url, String callbackId) {
            AppNavigationBridge bridge = target();
            if (bridge != null) bridge.httpGet(url, callbackId);
        }

        @JavascriptInterface
        public void requestGalleryPermission() {
            AppNavigationBridge bridge = target();
            if (bridge != null) bridge.requestGalleryPermission();
        }

        @JavascriptInterface
        public void setPayPageSystemBars(boolean visible) {
            AppNavigationBridge bridge = target();
            if (bridge != null) bridge.setPayPageSystemBars(visible);
        }

        @JavascriptInterface
        public void setMessageMenuOpen(boolean open) {
            AppNavigationBridge bridge = target();
            if (bridge != null) bridge.setMessageMenuOpen(open);
        }

        @JavascriptInterface
        public void showSoftKeyboard() {
            AppNavigationBridge bridge = target();
            if (bridge != null) bridge.showSoftKeyboard();
        }

        @JavascriptInterface
        public void returnToAppHome() {
            AppNavigationBridge bridge = target();
            if (bridge != null) bridge.returnToAppHome();
        }
    }

    private final class AppNavigationBridge {
        @JavascriptInterface
        public String downloadAvatarCacheUrl(String sourceUrl) {
            HttpURLConnection connection = null;
            try {
                if (sourceUrl == null || !sourceUrl.startsWith("https://")) return "";
                File cacheDir = new File(getFilesDir(), "fw_avatar_cache");
                String existingKey = Integer.toUnsignedString(sourceUrl.hashCode(), 36);
                String[] knownExtensions = new String[]{".jpg", ".jpeg", ".png", ".webp"};
                for (String existingExtension : knownExtensions) {
                    File cachedFile = new File(cacheDir, "avatar_" + existingKey + existingExtension);
                    if (cachedFile.isFile() && cachedFile.length() > 0) {
                        return "https://fake-world.local/__avatar_cache/" + cachedFile.getName();
                    }
                }
                connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("User-Agent", "Doutu-Android/1.0");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) return "";
                String type = connection.getContentType();
                if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("image/")) return "";
                String extension = ".jpg";
                String lowerType = type.toLowerCase(Locale.ROOT);
                if (lowerType.contains("png")) extension = ".png";
                else if (lowerType.contains("webp")) extension = ".webp";
                File dir = new File(getFilesDir(), "fw_avatar_cache");
                if (!dir.exists() && !dir.mkdirs()) return "";
                // Use a stable cache key. The previous timestamp-based filename made the
                // same remote avatar appear to change every time stabilization retried.
                String stableKey = Integer.toUnsignedString(sourceUrl.hashCode(), 36);
                File file = new File(dir, "avatar_" + stableKey + extension);
                if (file.isFile() && file.length() > 0) {
                    return "https://fake-world.local/__avatar_cache/" + file.getName();
                }
                long total = 0;
                try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > 3L * 1024L * 1024L) {
                            output.close();
                            file.delete();
                            return "";
                        }
                        output.write(buffer, 0, count);
                    }
                }
                return "https://fake-world.local/__avatar_cache/" + file.getName();
            } catch (Exception exception) {
                Log.w("WeiHomeActivity", "Unable to cache selected avatar", exception);
                return "";
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        @JavascriptInterface
        public String downloadAvatarDataUrl(String sourceUrl) {
            HttpURLConnection connection = null;
            try {
                if (sourceUrl == null || !sourceUrl.startsWith("https://")) return "";
                connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("User-Agent", "Doutu-Android/1.0");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) return "";
                String type = connection.getContentType();
                if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("image/")) return "";
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > 3 * 1024 * 1024) return "";
                        output.write(buffer, 0, count);
                    }
                    return "data:" + type + ";base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
                }
            } catch (Exception exception) {
                Log.w("WeiHomeActivity", "Unable to freeze selected avatar", exception);
                return "";
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        @JavascriptInterface
        public String getGalleryItems() {
            if (!hasGalleryPermission()) return "[]";
            org.json.JSONArray items=new org.json.JSONArray();
            String[] projection={MediaStore.Images.Media._ID,MediaStore.Images.Media.DATE_ADDED,MediaStore.Images.Media.MIME_TYPE};
            try(Cursor cursor=getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,projection,null,null,MediaStore.Images.Media.DATE_ADDED+" DESC")){
                if(cursor==null)return "[]"; int idc=cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID),dc=cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED),mc=cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE);
                while(cursor.moveToNext()&&items.length()<GALLERY_LIMIT){org.json.JSONObject o=new org.json.JSONObject();o.put("id",cursor.getLong(idc));o.put("date",cursor.getLong(dc));o.put("mime",cursor.getString(mc));items.put(o);}
            }catch(Exception e){Log.w("WeiHomeActivity","Gallery query failed",e);} return items.toString();
        }
        @JavascriptInterface
        public String getGalleryImage(String id,int maxSize){
            if(!hasGalleryPermission())return "";
            final int bound=Math.max(96,Math.min(maxSize,2560));
            final String key=id+":"+bound;
            synchronized(GALLERY_CACHE){
                String cached=GALLERY_CACHE.get(key);
                if(cached!=null)return cached;
            }
            try{Uri uri=ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,Long.parseLong(id));
                // Grid tiles are ~90dp, so the platform's cached thumbnail is both fast and sharp
                // enough; only the big previews pay for decoding and scaling the real file.
                Bitmap bitmap=bound<=512?loadGalleryThumb(uri,bound):decodeGalleryBitmap(uri,bound);
                // Photos are re-encoded at a quality that survives a full-screen view; the old
                // 78/62 settings were part of why images looked soft in the app.
                if(bitmap==null)return "";ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,maxSize>300?90:72,out);bitmap.recycle();
                String encoded="data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
                synchronized(GALLERY_CACHE){
                    if(GALLERY_CACHE.size()>=48)GALLERY_CACHE.remove(GALLERY_CACHE.keySet().iterator().next());
                    GALLERY_CACHE.put(key,encoded);
                }
                return encoded;
            }catch(Exception e){Log.w("WeiHomeActivity","Gallery image load failed",e);return "";}
        }
        /** Fast path for grid tiles: the platform's cached thumbnail never touches the file. */
        private Bitmap loadGalleryThumb(Uri uri,int bound) throws Exception{
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){
                Bitmap thumb=getContentResolver().loadThumbnail(uri,new android.util.Size(bound,bound),null);
                if(thumb!=null)return thumb;
            }
            return decodeGalleryBitmap(uri,bound);
        }
        /**
         * MediaStore.loadThumbnail () hands back a screen-sized cached thumbnail on this ROM
         * (~360x796 no matter what size is requested), so every photo previewed at the grid
         * thumbnail's quality. Decode the file itself instead and scale it to the bound.
         */
        private Bitmap decodeGalleryBitmap(Uri uri,int bound) throws Exception{
            BitmapFactory.Options o=new BitmapFactory.Options();
            o.inJustDecodeBounds=true;
            try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,o);}
            if(o.outWidth<=0||o.outHeight<=0)return null;
            int longest=Math.max(o.outWidth,o.outHeight);
            o.inSampleSize=Math.max(1,longest/bound);
            o.inJustDecodeBounds=false;
            Bitmap decoded;
            try(InputStream in=getContentResolver().openInputStream(uri)){decoded=BitmapFactory.decodeStream(in,null,o);}
            if(decoded==null)return null;
            int decodedLongest=Math.max(decoded.getWidth(),decoded.getHeight());
            if(decodedLongest<=bound)return decoded;
            float scale=bound/(float)decodedLongest;
            Bitmap scaled=Bitmap.createScaledBitmap(decoded,Math.max(1,Math.round(decoded.getWidth()*scale)),Math.max(1,Math.round(decoded.getHeight()*scale)),true);
            if(scaled!=decoded)decoded.recycle();
            return scaled;
        }
        /**
         * Writing our own new row into the album needs WRITE_EXTERNAL_STORAGE only up to API 28;
         * from Q on MediaStore accepts our inserts without a permission.
         */
        private boolean hasSaveImagePermission(){
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q)return true;
            return checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
        }
        /** Asked for by the page when a save came back as "denied". */
        @JavascriptInterface
        public void requestSaveImagePermission(){runOnUiThread(()->{
            if(hasSaveImagePermission()){
                if(webView!=null)webView.evaluateJavascript("window.__fwSavePermissionResult && window.__fwSavePermissionResult(true)",null);
                return;
            }
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},REQUEST_SAVE_IMAGE_PERMISSION);
        });}
        /** Writes a data-URL image into the device album (Pictures/DouTu) for 保存到相册. */
        @JavascriptInterface
        public String saveImageToGallery(String dataUrl){
            if(!hasSaveImagePermission())return "denied";
            String source=String.valueOf(dataUrl==null?"":dataUrl);
            int comma=source.indexOf(',');
            if(comma<0)return "";
            try{
                byte[] bytes=Base64.decode(source.substring(comma+1),Base64.DEFAULT);
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                if(bitmap==null)return "";
                android.content.ContentValues values=new android.content.ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME,"doutu-"+System.currentTimeMillis()+".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q)
                    values.put(MediaStore.Images.Media.RELATIVE_PATH,android.os.Environment.DIRECTORY_PICTURES+"/DouTu");
                Uri target=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                if(target==null){bitmap.recycle();return "";}
                try(java.io.OutputStream out=getContentResolver().openOutputStream(target)){
                    bitmap.compress(Bitmap.CompressFormat.JPEG,95,out);
                }
                bitmap.recycle();
                return target.toString();
            }catch(Exception e){Log.w("WeiHomeActivity","Save image failed",e);return "";}
        }
        @JavascriptInterface
        public boolean hasGalleryPermission(){
            if(Build.VERSION.SDK_INT>=34){
                return checkSelfPermission("android.permission.READ_MEDIA_IMAGES")==PackageManager.PERMISSION_GRANTED
                        || checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")==PackageManager.PERMISSION_GRANTED;
            }
            String permission=Build.VERSION.SDK_INT>=33?"android.permission.READ_MEDIA_IMAGES":"android.permission.READ_EXTERNAL_STORAGE";
            return checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;
        }
        /** 娱乐页「撩妹加V」开着没有 —— 开着就当作摄像头坏了，扫一扫只弹提示。 */
        @JavascriptInterface
        public boolean isScanDisabled(){
            return com.luoyun.doutu.features.play.PlayStore.cameraBroken();
        }
        /**
         * 模拟来电的音频通道：接通时切到通话模式（并按开关设置扬声器），挂断后恢复。
         * 这样「扬声器已开 / 已关」是真的改了系统路由，而不只是换个图标。
         */
        @JavascriptInterface
        public void setCallAudioMode(final boolean inCall, final boolean speakerOn) {
            runOnUiThread(() -> {
                try {
                    android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (am == null) return;
                    if (inCall) {
                        am.setMode(android.media.AudioManager.MODE_IN_CALL);
                        am.setSpeakerphoneOn(speakerOn);
                    } else {
                        am.setSpeakerphoneOn(false);
                        am.setMode(android.media.AudioManager.MODE_NORMAL);
                    }
                } catch (RuntimeException ignored) { /* never take the page down for audio routing */ }
            });
        }

        /**
         * 通用 GET（朋友圈集赞的随机姓名接口用）：页面 origin 是 fake-world.local，
         * 跨域只能由原生代取。结果异步回给 window.__fwHttpGetResult(callbackId, text)。
         */
        @JavascriptInterface
        public void httpGet(final String url, final String callbackId) {
            final String target = String.valueOf(url == null ? "" : url).trim();
            final String id = String.valueOf(callbackId == null ? "" : callbackId).trim();
            if (target.isEmpty() || id.isEmpty()) return;
            if (!(target.startsWith("https://") || target.startsWith("http://"))) return;
            new Thread(() -> {
                String body = "";
                java.net.HttpURLConnection connection = null;
                try {
                    connection = (java.net.HttpURLConnection) new java.net.URL(target).openConnection();
                    connection.setConnectTimeout(6000);
                    connection.setReadTimeout(6000);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Doutu/1.0");
                    connection.setInstanceFollowRedirects(true);
                    int code = connection.getResponseCode();
                    java.io.InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
                    if (stream != null) {
                        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                        byte[] chunk = new byte[4096];
                        int read;
                        while ((read = stream.read(chunk)) > 0 && buffer.size() < 65536) buffer.write(chunk, 0, read);
                        stream.close();
                        body = new String(buffer.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                } catch (Exception ignored) {
                    body = "";
                } finally {
                    if (connection != null) connection.disconnect();
                }
                final String result = body;
                WebView view = webView;
                if (view == null) return;
                view.post(() -> {
                    WebView live = webView;
                    if (live == null) return;
                    live.evaluateJavascript(
                            "window.__fwHttpGetResult&&window.__fwHttpGetResult('" + id.replace("'", "") + "',"
                                    + org.json.JSONObject.quote(result) + ")", null);
                });
            }).start();
        }

        @JavascriptInterface
        public void requestGalleryPermission(){runOnUiThread(()->{
            if(hasGalleryPermission()){
                if(webView!=null)webView.evaluateJavascript("window.__fwGalleryPermissionResult && window.__fwGalleryPermissionResult(true)",null);
                return;
            }
            String[] permissions;
            if(Build.VERSION.SDK_INT>=34)permissions=new String[]{"android.permission.READ_MEDIA_IMAGES","android.permission.READ_MEDIA_VISUAL_USER_SELECTED"};
            else if(Build.VERSION.SDK_INT>=33)permissions=new String[]{"android.permission.READ_MEDIA_IMAGES"};
            else permissions=new String[]{"android.permission.READ_EXTERNAL_STORAGE"};
            requestPermissions(permissions,REQUEST_GALLERY_PERMISSION);
        });}

        @JavascriptInterface
        public void setPayPageSystemBars(boolean visible) {
            runOnUiThread(() -> {
                if (isFinishing()) return;
                payPageBarsVisible = visible;
                if (visible) configurePayPageWindow(getWindow());
                else configureWindow(getWindow());
            });
        }

        @JavascriptInterface
        public void setMessageMenuOpen(boolean open) {
            messageMenuOpen = open;
            if (open) cancelNativeLongPress();
        }

        @JavascriptInterface
        public void showSoftKeyboard() {
            // Programmatic input.focus() inside the WebView does not reliably raise
            // the IME, so the page asks the platform to show it explicitly.
            runOnUiThread(() -> {
                if (webView == null || isFinishing()) return;
                // Only grab native focus when the WebView lost it: requestFocus() also
                // moves the DOM focus to the first focusable node, which steals the caret
                // from the page's input (repeatedly, when the page retries this call).
                if (!webView.isFocused()) webView.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT);
            });
        }

        @JavascriptInterface
        public void returnToAppHome() {
            runOnUiThread(() -> {
                if (!isFinishing()) showAppHomeBehindWei();
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQUEST_GALLERY_PERMISSION&&webView!=null){
            boolean ok=new AppNavigationBridge().hasGalleryPermission();
            Log.i("WeiHomeActivity","Gallery permission result granted="+ok);
            webView.evaluateJavascript("window.__fwGalleryPermissionResult && window.__fwGalleryPermissionResult("+ok+")",null);
        }
        if(requestCode==REQUEST_SAVE_IMAGE_PERMISSION&&webView!=null){
            boolean ok=new AppNavigationBridge().hasSaveImagePermission();
            Log.i("WeiHomeActivity","Save image permission result granted="+ok);
            webView.evaluateJavascript("window.__fwSavePermissionResult && window.__fwSavePermissionResult("+ok+")",null);
        }
        if(requestCode==REQUEST_CAMERA_PERMISSION){
            android.webkit.PermissionRequest request=pendingWebPermission;
            pendingWebPermission=null;
            boolean ok=hasCameraPermission();
            Log.i("WeiHomeActivity","Camera permission result granted="+ok);
            if(request!=null){
                try{
                    if(ok)request.grant(cameraResources(request));
                    else request.deny();
                }catch(RuntimeException ignored){/* the page may have gone away */}
            }
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_AVATAR) return;
        ValueCallback<Uri[]> callback = avatarFileCallback;
        avatarFileCallback = null;
        if (callback == null) return;
        Uri[] result = resultCode == RESULT_OK && data != null && data.getData() != null
                ? new Uri[]{data.getData()} : null;
        callback.onReceiveValue(result);
    }
    private void showAppHomeBehindWei() {
        Intent home = new Intent(this, com.luoyun.doutu.MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(home);
        // Keep the WebView Activity alive underneath the app home, so reopening
        // it returns to the already-mounted WeChat UI rather than a white reload.
    }
    @Override
    protected void onDestroy() {
        cancelNativeLongPress();
        if (liveInstance == this) liveInstance = null;
        if (avatarFileCallback != null) { avatarFileCallback.onReceiveValue(null); avatarFileCallback = null; }
        if (webView != null) {
            if (webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
            synchronized (WeiHomeActivity.class) {
                if (isChangingConfigurations() || isFinishing()) cachedWebView = webView;
            }
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView == null) { super.onBackPressed(); return; }
        webView.evaluateJavascript(
                "(function(){try{return ((window.__fwCloseOverlay && window.__fwCloseOverlay()) === true) || ((window.__fwHandleChatPayBack && window.__fwHandleChatPayBack()) === true) || ((window.__fwHandleBack && window.__fwHandleBack()) === true) || ((window.__fwCloseConversation && window.__fwCloseConversation()) === true)}catch(e){return false}})()",
                result -> {
                    // WebView may return either true or the JSON string "true".
                    boolean handled = result != null && ("true".equalsIgnoreCase(result.trim())
                            || "\"true\"".equalsIgnoreCase(result.trim()));
                    android.util.Log.d("WeiHomeBack", "jsResult=" + result + ", handled=" + handled
                            + ", deepLink=" + deepLinkRoute + ", url=" + (webView == null ? null : webView.getUrl())
                            + ", atRoot=" + isAtDeepLinkRoot());
                    if (handled) return;
                    // 宫格深链的页面停在根部：直接退回「微」那一页，不要再走 WebView 历史
                    // （历史里只有一开始的 #/wechat，会把用户丢到微信聊天列表）。
                    if (isAtDeepLinkRoot()) {
                        deepLinkRoute = null;
                        showAppHomeBehindWei();
                        return;
                    }
                    if (webView.canGoBack()) webView.goBack();
                    else showAppHomeBehindWei();
                });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            if (payPageBarsVisible) configurePayPageWindow(getWindow());
            else configureWindow(getWindow());
        }
    }

    private static void configurePayPageWindow(Window window) {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            // The main app runs immersive. Reset its legacy flags before asking the
            // insets controller to reveal both bars over this edge-to-edge page.
            window.getDecorView().setSystemUiVisibility(0);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_DEFAULT);
                controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private void installKeyboardInsetsBridge() {
        if (Build.VERSION.SDK_INT < 23) return;
        getWindow().getDecorView().setOnApplyWindowInsetsListener((view, insets) -> {
            int imeBottom = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                if (insets.isVisible(WindowInsets.Type.ime())) imeBottom = ime.bottom;
            } else {
                imeBottom = insets.getSystemWindowInsetBottom();
            }
            if (webView != null) {
                final float density = Math.max(1f, getResources().getDisplayMetrics().density);
                final int bottom = Math.round(imeBottom / density);
                webView.evaluateJavascript("window.__fwSetKeyboardInset && window.__fwSetKeyboardInset(" + bottom + ")", null);
            }
            return insets;
        });
        getWindow().getDecorView().requestApplyInsets();
    }

    private static void configureWindow(Window window) {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }

        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        window.getDecorView().setSystemUiVisibility(flags);

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    private static class BundledAssetWebViewClient extends WebViewClient {
        private final AssetManager assets;

        BundledAssetWebViewClient(AssetManager assets) {
            this.assets = assets;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return openBundledAsset(view, request.getUrl());
        }

        @Override
        @SuppressWarnings("deprecation")
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return openBundledAsset(view, Uri.parse(url));
        }

        private WebResourceResponse openBundledAsset(WebView view, Uri uri) {
            if (uri == null || !APP_ORIGIN.equals(uri.getScheme() + "://" + uri.getHost())) {
                return null;
            }

            String path = uri.getPath();
            if (path == null || path.isEmpty() || path.contains("..")) {
                return null;
            }
            if (path.startsWith("/__avatar_cache/")) {
                String name = path.substring("/__avatar_cache/".length());
                if (name.contains("/") || name.contains("..")) return null;
                File file = new File(new File(view.getContext().getFilesDir(), "fw_avatar_cache"), name);
                if (!file.isFile()) return null;
                try {
                    String mime = mimeType(name);
                    return new WebResourceResponse(mime, null, new java.io.FileInputStream(file));
                } catch (IOException ignored) {
                    return null;
                }
            }
            // 2026-10-16：「模拟来电」设置页给来电界面挑的头像与铃声。网页读不到 content:// 或
            // 应用私有目录，所以由这里把它们按同一条虚拟 origin 提供出去 —— 页面只当它是一张图和
            // 一个音频地址，来电界面那套（循环播放、接听时淡出）一行都不用改。
            if (path.startsWith("/__call_assets/")) {
                String name = path.substring("/__call_assets/".length());
                if (name.isEmpty() || name.contains("/") || name.contains("..")) return null;
                File file = new File(new File(view.getContext().getFilesDir(), "call_assets"), name);
                if (!file.isFile()) return null;
                try {
                    String mime = mimeType(name);
                    return new WebResourceResponse(mime, null, new java.io.FileInputStream(file));
                } catch (IOException ignored) {
                    return null;
                }
            }
            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            String assetPath = "fake_world/" + path;
            try {
                InputStream stream = assets.open(assetPath, AssetManager.ACCESS_STREAMING);
                String mime = mimeType(path);
                String encoding = isText(mime) ? "UTF-8" : null;
                return new WebResourceResponse(mime, encoding, stream);
            } catch (IOException ignored) {
                return null;
            }
        }

        private static boolean isText(String mime) {
            return mime.startsWith("text/")
                    || mime.contains("javascript")
                    || mime.contains("json")
                    || mime.contains("xml");
        }

        private static String mimeType(String path) {
            String lower = path.toLowerCase(Locale.US);
            if (lower.endsWith(".html")) return "text/html";
            if (lower.endsWith(".js")) return "application/javascript";
            if (lower.endsWith(".css")) return "text/css";
            if (lower.endsWith(".json")) return "application/json";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".gif")) return "image/gif";
            if (lower.endsWith(".webp")) return "image/webp";
            if (lower.endsWith(".woff2")) return "font/woff2";
            if (lower.endsWith(".woff")) return "font/woff";
            if (lower.endsWith(".ttf")) return "font/ttf";
            // 模拟来电可以放用户自己挑的音频
            if (lower.endsWith(".mp3")) return "audio/mpeg";
            if (lower.endsWith(".m4a")) return "audio/mp4";
            if (lower.endsWith(".aac")) return "audio/aac";
            if (lower.endsWith(".ogg") || lower.endsWith(".oga")) return "audio/ogg";
            if (lower.endsWith(".wav")) return "audio/wav";
            if (lower.endsWith(".flac")) return "audio/flac";
            return "application/octet-stream";
        }
    }
}
















