import UIKit
import WebKit

/// 小微主页的 WebView 宿主 —— 对应 Android 的 `WeiHomeActivity`。
///
/// 页面本体是打进包里的 React 产物（`Resources/web`，Vite 构建），Android 那边用
/// `shouldInterceptRequest` 把 `https://fake-world.local/*` 映射到 assets；iOS 这边没有那个回调，
/// 改用 **`WKURLSchemeHandler`** 把 `app://local/*` 映射到包内文件。
///
/// 为什么不用 `loadFileURL`：那份页面是 ES module（`<script type="module" crossorigin>`），
/// `file://` 下模块加载会被 CORS 拦掉，整页白屏。自定义 scheme + 自己的 handler 才有真正的 origin。
///
/// 页面与原生之间的三条通道（与 Android 同名）：
///   · `window.__fwOpenRoute("#/wechat/wallet/bills")`  —— 进页内路由
///   · `window.__fwOpenFeature("group-chat")`          —— 叠一个浮层功能
///   · `window.DoutuApp.xxx(...)`                      —— 调原生（相册、http、键盘…）
final class WeChatV2Host: UIViewController, WKScriptMessageHandler,
                        UIGestureRecognizerDelegate {
    /// 页面根路径（包内 Resources/web）
    private static let scheme = "app"
    private static let host = "local"
    private static let entry = "app://local/index.html#/wechat"

    /// 当前正在显示的实例（调试截图 / 诊断用；同一时刻只会有一个）
    static weak var current: WeChatV2Host?

    private var webView: WKWebView!
    /// 包内网页的本地 HTTP 服务（真正的 origin，ES module 才能加载）
    private var server: LocalWebServer?
    /// 待执行的路由（页面还没起来时先攥着，起来后再交过去）
    /// 关闭后 1 秒内不允许再次 present，挡住「返回键/空白菜单那一下」穿透到首页宫格，
    /// 导致 dismiss 后立刻又打开一个新的微主页。
    static var lastCloseAt: Date?

    private var pendingRoute: String?
    private var routeRetry = 0

    /// 初始路由：`#/wechat` 或 `#/wechat/...` 或 `feature:xxx`
    init(route: String = "#/wechat") {
        super.init(nibName: nil, bundle: nil)
        pendingRoute = route
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        Self.current = self
        CrashLog.append("WebHost viewDidLoad route=\(pendingRoute ?? "-")")
        view.backgroundColor = .white

        let config = WKWebViewConfiguration()
        config.setURLSchemeHandler(BundleSchemeHandler(), forURLScheme: Self.scheme)
        // 与 Android 一致：允许页面里的内联媒体自动播放（来电铃声那类）
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        config.userContentController.add(self, name: "DoutuApp")
        config.userContentController.add(self, name: "FwContacts")

        webView = WKWebView(frame: view.bounds, configuration: config)
        webView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        webView.navigationDelegate = self
        webView.scrollView.bounces = false
        webView.scrollView.isScrollEnabled = true
        webView.scrollView.panGestureRecognizer.isEnabled = true
        // 不要让 WKWebView 把安全区（刘海 47pt + 底部 34pt）加进内容 inset：
        // 页面会整体下移 47pt，底部导航条正好被挤出屏幕（真机表现就是「底栏消失」）。
        // Android 的 WebView 没有这个 inset，页面从 0 开始排版。
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.scrollView.contentInset = .zero
        webView.scrollView.scrollIndicatorInsets = .zero
        webView.isOpaque = false
        webView.backgroundColor = .white
        view.addSubview(webView)

        // 返回入口。
        //
        // 这个控制器是 `.fullScreen` 模态弹出来的（`presentWeb` 里设的），既没有导航栏，
        // 首页没有网页返回箭头，保留左上角返回作为可见出口；子页多一个箭头但不会迷路。
        installBackAffordance()
        installHeaderHitTargets()
        installBlankTap()

        // 把 `DoutuApp` 桥按 Android 的方法清单补齐（缺一个页面里调用就会抛异常）
        let bridge = WKUserScript(source: Self.bridgeShim, injectionTime: .atDocumentStart,
                                  forMainFrameOnly: true)
        config.userContentController.addUserScript(bridge)

        // 排版宽度对齐 Android。
        //
        // Android 的 `WeiHomeActivity` 调了 `setUseWideViewPort(false)`：页面按 **360dp** 排版
        // （1080px ÷ density 3 = 360）。而 iOS 的 WKWebView 认 `width=device-width`，
        // 在 iPhone 12 上是 **390pt** —— 页面比 Android 宽 8%，观感就是「比例不对」。
        // 这里把 viewport 固定成 360，页面就按 Android 的排版宽度渲染，再整体放大到屏幕宽。
        let viewport = WKUserScript(source: Self.viewportShim, injectionTime: .atDocumentStart,
                                    forMainFrameOnly: true)
        config.userContentController.addUserScript(viewport)
        // 诊断：把网页里的 JS 错误回传原生侧，写进 crash.log。
        // 白屏时这就是唯一能拿到真实原因的手段（WKWebView 的 console 在 Mac 上才看得到）。
        let jsErrorHook = WKUserScript(source: WeChatV2Host.errorHook, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        config.userContentController.addUserScript(jsErrorHook)

        // 微信底栏兜底：iOS 上它是 static 定位，恰好贴到 WKWebView 底边，
        // 真机/截图里经常整条不见。固定到视口底部，并保证子元素可见。
        let navFix = WKUserScript(source: Self.navFix, injectionTime: .atDocumentEnd,
                                  forMainFrameOnly: true)
        config.userContentController.addUserScript(navFix)

        // 小微主页顶栏的搜索/加号在原 React 页面里只有样式、没有 onClick（点了没反应）。
        // 这里补上点击投递 + 把两个图标放大一点；原生收到后弹底部操作表。
        let topbarFix = WKUserScript(source: Self.topbarFix, injectionTime: .atDocumentEnd,
                                     forMainFrameOnly: true)
        config.userContentController.addUserScript(topbarFix)

        // 2026-10-19: 原生加号/搜索热区只属于微信主页。钱包等其他页面的右上角
        // 也有「账单」等按钮，热区会盖住它们导致点不动。这里监听 hash，把当前
        // 是否主页告诉原生，原生只在主页显示这两个透明热区。
        let headerRoute = WKUserScript(source: """
        (function(){
          function sync(){
            try {
              var path=(location.hash||'').replace(/^#/,'').split(/[?#]/)[0].replace(/\\/+$/,'');
              var home=(path===''||path==='/wechat'||path==='/wechat/index');
              window.webkit.messageHandlers.DoutuApp.postMessage({method:'headerTargets',args:[home]});
            } catch (e) {}
          }
          window.addEventListener('hashchange', sync);
          document.addEventListener('DOMContentLoaded', sync);
          setTimeout(sync, 0); setTimeout(sync, 300); setTimeout(sync, 1000);
        })();
        """, injectionTime: .atDocumentEnd, forMainFrameOnly: true)
        config.userContentController.addUserScript(headerRoute)

        // 「撩妹加 V」开着时，网页里的扫一扫要直接弹摄像头损坏提示。
        let scanFlag = WKUserScript(
            source: "window.__fwScanDisabled = \(TeaseStore.enabled ? "true" : "false");",
            injectionTime: .atDocumentStart,
            forMainFrameOnly: true)
        config.userContentController.addUserScript(scanFlag)

        // 真正的 origin：Android 用 `https://fake-world.local` + shouldInterceptRequest，
        // iOS 这边用 127.0.0.1 回环 HTTP。自定义 scheme 下 ES module 会被 CORS 拒绝，
        // 页面只剩经典脚本渲染的外壳（微信标题栏 + “Windows 微信已登录”，下面全白）。
        let local = LocalWebServer.shared
        guard let local else {
            CrashLog.append("WebHost local server init failed, fallback app://")
            if let url = URL(string: Self.entry) {
                webView.load(URLRequest(url: url))
            }
            return
        }
        self.server = local
        local.start { [weak self] port in
            guard let self else { return }
            guard let port else {
                CrashLog.append("WebHost local server unavailable, fallback app://")
                if let url = URL(string: Self.entry) {
                    self.webView.load(URLRequest(url: url))
                }
                return
            }
            let raw = "http://localhost:\(port)/index.html#/wechat"
            CrashLog.append("WebHost loading \(raw)")
            if let url = URL(string: raw) {
                self.webView.load(URLRequest(url: url))
            }
        }
    }

    /// 从宫格进来时切换路由（页面已就绪就直接调，否则等 didFinish）
    func open(route: String) {
        pendingRoute = route
        routeRetry = 0
        deliverPendingRouteIfReady()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        webView?.evaluateJavaScript(
            "window.__fwScanDisabled = \(TeaseStore.enabled ? "true" : "false");",
            completionHandler: nil)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        guard let webView else { return }
        // Android 的微主页是沉浸式全屏（隐藏状态栏，页面从 y=0 开始）。
        // iOS 同样隐藏状态栏并把 WebView 铺满，避免顶部多出 47pt 白边。
        if webView.frame != view.bounds {
            webView.frame = view.bounds
        }
        let top = view.safeAreaInsets.top
        backButton?.frame = CGRect(x: 10, y: top + 8, width: 34, height: 34)
        if let backButton { view.bringSubviewToFront(backButton) }
        let right = view.bounds.width
        headerPlusButton?.frame = CGRect(x: right - 42, y: 8, width: 40, height: 50)
        headerSearchButton?.frame = CGRect(x: right - 68, y: 8, width: 26, height: 50)
        if let b = headerPlusButton {
            CrashLog.append("WEBHDR frame \(b.frame) bounds=\(view.bounds) hidden=\(b.isHidden) alpha=\(b.alpha)")
        }
    }

    /// 与 Android `WeiHomeActivity` 的沉浸式一致：本页不显示状态栏。
    override var prefersStatusBarHidden: Bool { true }
    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }

    // MARK: - 返回

    /// 左边缘右滑返回。
    ///
    /// Android 那边用系统返回键/手势；iOS 这个模态没有导航栏，页面本身也没有返回按钮。
    /// 早期版本在左上角叠了一个「‹ 返回」胶囊，但它不属于 Android 的界面，
    /// 会盖住微信标题栏 —— 与「UI 一模一样」冲突，所以去掉，只保留边缘手势。
    private func installBackAffordance() {
        let edge = UIScreenEdgePanGestureRecognizer(target: self, action: #selector(handleEdgePan(_:)))
        edge.edges = .left
        view.addGestureRecognizer(edge)
    }

    /// iOS 没有 Android 的系统返回键；只留边缘滑动的话很多用户会卡在微主页里。
    /// 这里加一个很小的左上角返回按钮，位置在微信标题栏左侧空白处，不挡标题。
    private var backButton: UIButton?
    private var headerSearchButton: UIButton?
    private var headerPlusButton: UIButton?

    private func installBackButton() {
        let button = UIButton(type: .system)
        button.setImage(UIImage(systemName: "chevron.left"), for: .normal)
        button.tintColor = UIColor.black.withAlphaComponent(0.8)
        button.backgroundColor = UIColor.white.withAlphaComponent(0.86)
        button.layer.cornerRadius = 17
        button.layer.shadowColor = UIColor.black.cgColor
        button.layer.shadowOpacity = 0.12
        button.layer.shadowRadius = 4
        button.layer.shadowOffset = CGSize(width: 0, height: 1)
        button.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        view.addSubview(button)
        backButton = button
    }

    @objc private func closeTapped() {
        closeHost()
    }

    /// 所有“退出整个微主页”的动作都走这里，统一记录关闭时间。
    private func closeHost() {
        Self.lastCloseAt = Date()
        dismiss(animated: true)
    }

    /// 页面顶栏的加号/放大镜在 React 侧没有点击处理；再叠两个透明命中区，
    /// 位置按真机 1170×2532 截图上量出来的 33pt 头部中心放。视觉仍用网页图标（已放大）。
    private func installHeaderHitTargets() {
        CrashLog.append("WEBHDR install")
        let plus = UIButton(type: .custom)
        plus.backgroundColor = .clear
        plus.accessibilityLabel = "微信加号"
        plus.addTarget(self, action: #selector(headerPlusTapped), for: .touchUpInside)
        view.addSubview(plus)
        headerPlusButton = plus

        let search = UIButton(type: .custom)
        search.backgroundColor = .clear
        search.accessibilityLabel = "微信搜索"
        search.addTarget(self, action: #selector(headerSearchTapped), for: .touchUpInside)
        view.addSubview(search)
        headerSearchButton = search
        // 默认隐藏，等网页 hash 同步后再决定是否显示（只有微信主页显示）。
        plus.isHidden = true
        search.isHidden = true
    }

    @objc private func headerPlusTapped() {
        CrashLog.append("WEBPLUS native tap")
        webView.evaluateJavaScript(
            "window.__fwOpenPlusMenu ? window.__fwOpenPlusMenu() : 0") { result, error in
                CrashLog.append("WEBPLUS js result=\(String(describing: result)) err=\(error?.localizedDescription ?? "-")")
            }
    }

    @objc private func headerSearchTapped() {
        webView.evaluateJavaScript(
            "(function(){var a=document.querySelector('.wechat-topbar__actions');" +
            "if(!a)return 0;var s=a.querySelector('svg');if(!s)return 0;" +
            "s.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));return 1})()",
            completionHandler: nil)
    }

    // MARK: - 顶部空白 / Windows 登录条菜单

    private func installBlankTap() {
        // 顶部空白仍然是单击打开工具菜单；但点击时必须先判断加号菜单是否开着，
        // 否则加号菜单第一项的点击会被这个手势抢走。cancelsTouchesInView=false
        // 保证网页自身的按钮点击仍然生效。
        let tap = UITapGestureRecognizer(target: self, action: #selector(blankTapped(_:)))
        tap.cancelsTouchesInView = false
        tap.delegate = self
        webView.addGestureRecognizer(tap)
    }

    @objc private func blankTapped(_ gesture: UITapGestureRecognizer) {
        let point = gesture.location(in: webView)
        CrashLog.append("WEBTAP blank y=\(point.y)")
        guard point.y >= 0, point.y <= 140 else { return }
        // 只有“微信主页面”才允许触发 7 项工具菜单；添加朋友/群聊/扫码/
        // 批量添加等任何子页、任何浮层里都绝不能抢点击。
        webView.evaluateJavaScript(
            "(function(){try{" +
            "var h=location.hash||'';" +
            "var path=h.replace(/^#/,'').split(/[?#]/)[0].replace(/\\/+$/,'');" +
            "var home=(path===''||path==='/wechat'||path==='/wechat/index');" +
            "if(!home)return 0;" +
            // 设备登录横条就在顶部，点它是打开设备切换弹层；不能同时再弹 7 项工具菜单。
            "var hit=document.elementFromPoint(\(point.x),\(point.y));" +
            "if(hit&&hit.closest('.wechat-device-row'))return 0;" +
            "var sub=document.querySelector('.fw-overlay, .fw-add-friend, .fw-group-chat, .fw-scanpay, .fw-receive-pay, .fw-local-conversation, .fw-chat-pay, .fw-moments-feed, .fw-moments-page, .fw-friend-profile-page, .fw-my-profile-editor, .fw-my-avatar-picker');" +
            "if(sub)return 0;" +
            "return 1}catch(e){return 0}})()"
        ) { [weak self] value, _ in
            guard let self else { return }
            if let home = value as? Int, home == 0 { return }
            CrashLog.append("WEBTOOLS native tap y=\(point.y)")
            self.webView.evaluateJavaScript(
                "window.__fwOpenToolsSheet ? window.__fwOpenToolsSheet() : 0") { result, error in
                    CrashLog.append("WEBTOOLS js result=\(String(describing: result)) err=\(error?.localizedDescription ?? "-")")
                }
        }
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        true
    }

    private func handleBlankAction(_ action: String) {
        switch action {
        case "返回APP主页":
            closeHost()
        case "调节字体大小":
            showFontSizeMenu()
        case "开启电脑设备登录显示":
            webView.evaluateJavaScript(
                "(function(){var r=document.querySelector('.wechat-device-row');" +
                "if(r){r.style.display=(r.style.display==='none'?'flex':'none');}return 1;})()",
                completionHandler: nil)
        case "清空所有聊天记录":
            let alert = UIAlertController(title: "清空所有聊天记录",
                                          message: "确定清空当前微信页的聊天记录吗？",
                                          preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "取消", style: .cancel))
            alert.addAction(UIAlertAction(title: "清空", style: .destructive) { [weak self] _ in
                self?.webView.evaluateJavaScript(
                    "(function(){try{localStorage.removeItem('feedList');location.reload();}catch(e){}return 1;})()",
                    completionHandler: nil)
            })
            present(alert, animated: true)
        case "不懂操作？联系客服":
            let router = Router.shared
            dismiss(animated: true) {
                router?.handle("entry:0", at: .zero)
            }
        default:
            let alert = UIAlertController(title: action,
                                          message: "这个入口对应的功能还没移植完。",
                                          preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "知道了", style: .cancel))
            present(alert, animated: true)
        }
    }

    private func showFontSizeMenu() {
        let sheet = UIAlertController(title: "调节字体大小", message: nil, preferredStyle: .actionSheet)
        for (title, zoom) in [("小", 0.9), ("标准", 1.0), ("大", 1.15), ("超大", 1.3)] {
            sheet.addAction(UIAlertAction(title: title, style: .default) { [weak self] _ in
                self?.webView.pageZoom = CGFloat(zoom)
            })
        }
        sheet.addAction(UIAlertAction(title: "取消", style: .cancel))
        if let pop = sheet.popoverPresentationController {
            pop.sourceView = view
            pop.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.maxY - 40, width: 1, height: 1)
        }
        present(sheet, animated: true)
    }

    @objc private func handleEdgePan(_ gesture: UIScreenEdgePanGestureRecognizer) {
        guard gesture.state == .ended else { return }
        let translation = gesture.translation(in: view)
        guard translation.x > 60, abs(translation.y) < 120 else { return }
        guard EdgeBackCenter.shouldHandle() else { return }
        performEdgeBack()
    }

    @objc func handleEdgeBack() {
        guard EdgeBackCenter.shouldHandle() else { return }
        performEdgeBack()
    }

    private func performEdgeBack() {
        // 微信内部页面层级很深（钱包、零钱、账单、提现、亲属卡……），
        // 不能只对聊天/支付做判断。统一让网页先处理：
        // 1) 聊天/支付浮层调 __fwHandleChatPayBack；
        // 2) 其他深层路由用 history.back() 回到上一级；
        // 3) 已经在微主页根路由时才关闭整个微主页。
        let script = """
        (function(){
          // 钱包/零钱/账单/银行卡有独立的一层返回逻辑（__fwHandleBack）。
          try {
            if (window.__fwHandleBack && window.__fwHandleBack()) return true;
          } catch (e) {}
          // 聊天、转账、支付浮层。
          try {
            if (window.__fwHandleChatPayBack && window.__fwHandleChatPayBack()) return true;
          } catch (e) {}
          // 其他深层 hash 路由：逐级 history.back。
          var h = String(location.hash || '');
          if (h && h !== '#/wechat' && h !== '#/wechat/' && history.length > 1) {
            try { history.back(); return true; } catch (e) {}
          }
          return false;
        })()
        """
        webView.evaluateJavaScript(script) { [weak self] result, _ in
            guard let self else { return }
            let handled: Bool
            if let flag = result as? Bool {
                handled = flag
            } else if let number = result as? NSNumber {
                handled = number.boolValue
            } else {
                handled = false
            }
            if !handled {
                DispatchQueue.main.async { self.closeHost() }
            }
        }
    }

    private func deliverPendingRouteIfReady() {
        guard let route = pendingRoute else { return }
        let script: String
        if route.hasPrefix("feature:") {
            let name = String(route.dropFirst("feature:".count))
            // 必须把网页函数的返回值带回来：override 里的 __fwOpenFeature
            // 只有真正打开功能时才 return true。之前的壳无条件 return 1，
            // 页面还没挂载/函数还不存在时也会把 pendingRoute 清掉，功能就永远不弹。
            script = "(function(){try{if(typeof window.__fwOpenFeature!=='function')return 0;"
                + "var v=window.__fwOpenFeature(\(Self.jsString(name)));return v?1:0}catch(e){return 0}})()"
        } else {
            script = "(function(){try{if(typeof window.__fwOpenRoute==='function'){"
                + "window.__fwOpenRoute(\(Self.jsString(route)));return 1}"
                + "location.hash=\(Self.jsString(route));return 1}catch(e){return 0}})()"
        }
        webView.evaluateJavaScript(script) { [weak self] result, _ in
            guard let self else { return }
            if let ok = result as? Int, ok == 1 {
                self.pendingRoute = nil
                self.routeRetry = 0
            } else if self.routeRetry < 20 {
                self.routeRetry += 1
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                    self.deliverPendingRouteIfReady()
                }
            }
        }
    }

    private static func jsString(_ value: String) -> String {
        let escaped = value
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "\"", with: "\\\"")
            .replacingOccurrences(of: "\n", with: "\\n")
        return "\"\(escaped)\""
    }

    // MARK: - 调试：页面快照 / DOM 诊断

    /// 只截 WebView 内容（不含原生返回按钮）。
    /// `takeSnapshot` 是 WKWebView 唯一可靠的整页截图方式；`drawHierarchy` 对
    /// 跨进程渲染的网页会得到空白。
    func snapshot(_ completion: @escaping (UIImage?) -> Void) {
        guard webView != nil else { completion(nil); return }
        let config = WKSnapshotConfiguration()
        config.rect = webView.bounds
        // 注意：snapshotWidth 是 **点**，不是像素。之前按 3 倍写成 1170，WKWebView 会把
        // 页面按 1170 CSS px 重新排版 —— 触发桌面断点，手机底栏被 max-lg:hidden 藏掉，
        // 截图里看起来「底栏消失」。这里必须用真实的 390pt。
        config.snapshotWidth = NSNumber(value: Double(webView.bounds.width))
        webView.takeSnapshot(with: config) { image, error in
            if let error {
                CrashLog.append("WEBSNAP error=\(error.localizedDescription)")
            }
            completion(image)
        }
    }

    /// 调试遥控：执行任意 JS 并把返回值写进 crash.log。
    func debugEval(_ script: String) {
        webView.evaluateJavaScript(script) { result, error in
            CrashLog.append("WEBJS result=\(String(describing: result)) err=\(error?.localizedDescription ?? "-")")
        }
    }

    /// 把页面的真实状态写进 crash.log：模块脚本到底有没有执行、
    /// `#root` 里有没有内容、哪些资源没加载出来。
    func logDiagnostics() {
        let js = """
        (function(){
          function pick(sel){ try { var e=document.querySelector(sel); return e?String(e.innerText||'').slice(0,200):null; } catch(e){ return null; } }
          var scripts = [];
          try {
            var list = document.getElementsByTagName('script');
            for (var i=0;i<list.length;i++) {
              scripts.push({src:list[i].src||'', type:list[i].type||'', async:!!list[i].async, defer:!!list[i].defer});
            }
          } catch(e) {}
          var res = [];
          try {
            var entries = performance.getEntriesByType('resource');
            for (var j=0;j<entries.length;j++) {
              var r=entries[j];
              res.push({name:String(r.name).slice(-80), type:r.initiatorType, ms:Math.round(r.duration)});
            }
          } catch(e) {}
          var root = document.getElementById('root');
          var nav = document.querySelector('.wechat-bottom-navbar');
          var chain = [];
          try {
            var navCS0 = nav ? getComputedStyle(nav) : null;
            if (nav) {
              chain.push({
                navColor: navCS0.color, navBg: navCS0.backgroundColor,
                navHTML: String(nav.innerHTML || '').slice(0, 500),
                childCount: nav.children.length
              });
            }
            for (var el = nav; el && chain.length < 12; el = el.parentElement) {
              var cs = getComputedStyle(el);
              var nr = el.getBoundingClientRect();
              chain.push({
                tag: el.tagName, cls: String(el.className || '').slice(0, 80),
                pos: cs.position, tr: cs.transform, fil: cs.filter, per: cs.perspective,
                ov: cs.overflow, h: Math.round(nr.height), top: Math.round(nr.top),
                bottom: Math.round(nr.bottom), z: cs.zIndex, disp: cs.display,
                vis: cs.visibility, op: cs.opacity
              });
            }
          } catch(e) { chain.push({err:String(e)}); }
          return JSON.stringify({
            href: location.href, ready: document.readyState, title: document.title,
            rootLen: root ? root.innerHTML.length : -1,
            rootText: root ? String(root.innerText||'').slice(0,300) : null,
            bodyText: String(document.body ? document.body.innerText : '').slice(0,400),
            navChain: chain,
            appRoute: typeof window.__fwOpenRoute,
            appFeature: typeof window.__fwOpenFeature,
            doutuApp: typeof window.DoutuApp,
            moduleOK: window.__doutuModuleOK || 0,
            moduleERR: window.__doutuModuleERR || '',
            viewport: (document.querySelector('meta[name="viewport"]') || {}).content || '',
            width: document.documentElement.clientWidth,
            height: document.documentElement.clientHeight,
            scripts: scripts, resources: res.slice(0,40)
          });
        })()
        """
        webView.evaluateJavaScript(js) { result, error in
            if let error {
                CrashLog.append("WEBINFO error=\(error.localizedDescription)")
            } else if let text = result as? String {
                CrashLog.append("WEBINFO \(text)")
            } else {
                CrashLog.append("WEBINFO result=\(String(describing: result))")
            }
        }
    }

    deinit {
        if Self.current === self { Self.current = nil }
    }

    // MARK: - 页面调原生

    func userContentController(_ controller: WKUserContentController,
                               didReceive message: WKScriptMessage) {
        if message.name == "FwContacts" {
            WeChatContactStore.shared.save(message.body)
            return
        }
        guard let body = message.body as? [String: Any] else { return }
        // 网页里的 JS 错误钩子（见 errorHook）走的是 {type:'jserror'}，不是 {method:...}。
        // 之前这里直接按 method 取，导致所有网页错误被静默丢掉，白屏完全没有线索。
        if let type = body["type"] as? String, type == "jserror" {
            let kind = body["kind"] as? String ?? "?"
            let msg = body["message"] as? String ?? ""
            let extra = body["extra"] as? String ?? ""
            CrashLog.append("JSERROR kind=\(kind) message=\(msg) extra=\(extra)")
            return
        }
        guard let method = body["method"] as? String else { return }
        let args = body["args"] as? [Any] ?? []
        let callbackId = body["callbackId"] as? String

        switch method {
        case "httpGet":
            // 对应 Android `httpGet(url, callbackId)`：抓完调 page 的 __doutuAppCallback
            if let urlString = args.first as? String {
                nativeHttpGet(urlString, callbackId: callbackId)
            }
        case "returnToAppHome":
            // 这个页面是 present 出来的，没有导航栈；Android 那边是 finish()。
            if presentingViewController != nil {
                closeHost()
            } else {
                Self.lastCloseAt = Date()
                navigationController?.popToRootViewController(animated: true)
            }
        case "hasGalleryPermission":
            reply(callbackId, true)
        case "getGalleryItems":
            reply(callbackId, "[]")
        case "isScanDisabled":
            reply(callbackId, TeaseStore.enabled)
        case "wechatPlus":
            showWeChatPlusMenu()
        case "wechatSearch":
            showWeChatSearch()
        case "headerTargets":
            let visible = args.first as? Bool ?? true
            headerPlusButton?.isHidden = !visible
            headerSearchButton?.isHidden = !visible
        default:
            // 其余方法（相册保存、来电音频、键盘、系统栏…）先按成功/空值应答，
            // 让页面逻辑不至于卡住；逐个补齐记在 README 的待办里。
            reply(callbackId, NSNull())
        }
    }

    // MARK: - 小微主页顶栏（搜索 / 加号）

    /// 加号：安卓是右上角下拉菜单；iOS 用底部 action sheet，点一项执行。
    private func showWeChatPlusMenu() {
        let sheet = UIAlertController(title: "微信", message: nil, preferredStyle: .actionSheet)
        for name in ["发起群聊", "添加朋友", "扫一扫", "收付款"] {
            sheet.addAction(UIAlertAction(title: name, style: .default) { [weak self] _ in
                self?.runWeChatPlus(name)
            })
        }
        sheet.addAction(UIAlertAction(title: "取消", style: .cancel))
        if let pop = sheet.popoverPresentationController {
            pop.sourceView = view
            pop.sourceRect = CGRect(x: view.bounds.width - 40, y: view.safeAreaInsets.top + 30,
                                    width: 1, height: 1)
        }
        present(sheet, animated: true)
    }

    private func runWeChatPlus(_ name: String) {
        // 四个入口都走网页版 openFeature：群聊/添加朋友是网页页，扫一扫/收付款
        // 也必须是网页页（原生页没有收款人头像/底部操作，真机会错位）。
        let js = "(function(){try{return window.__fwPlusAction ? "
            + "(window.__fwPlusAction(\(Self.jsString(name)))?1:0):0}catch(e){return 0}})()"
        webView.evaluateJavaScript(js)
    }

    /// 网页版加号菜单里的入口（群聊/添加朋友）直接走 override 的页面，
    /// 不再弹“未移植”对话框，也不再错误地退回加号页。
    private func openWebFeature(_ feature: String) {
        let js = "(function(){try{return window.__fwOpenFeature ? " +
            "(window.__fwOpenFeature(\(Self.jsString(feature)))?1:0):0}catch(e){return 0}})()"
        webView.evaluateJavaScript(js)
    }

    /// 放大镜：iOS 上先给一个可输入的搜索框（页面的搜索页还没移植）。
    private func showWeChatSearch() {
        let alert = UIAlertController(title: "搜索", message: nil, preferredStyle: .alert)
        alert.addTextField { field in
            field.placeholder = "搜索"
            field.clearButtonMode = .whileEditing
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "搜索", style: .default) { _ in
            // 页面暂无搜索结果路由，保留输入入口，避免点击完全没反应。
        })
        present(alert, animated: true)
    }

    private func reply(_ callbackId: String?, _ value: Any?) {
        guard let callbackId else { return }
        let payload: String
        if let text = value as? String {
            payload = Self.jsString(text)
        } else if let number = value as? NSNumber {
            payload = number.stringValue
        } else {
            payload = "null"
        }
        webView.evaluateJavaScript(
            "window.__doutuAppCallback && window.__doutuAppCallback(\(Self.jsString(callbackId)), \(payload))",
            completionHandler: nil)
    }

    /// 页面要抓外部地址（头像、缩略图），由原生代劳以绕开 CORS
    private func nativeHttpGet(_ urlString: String, callbackId: String?) {
        guard let url = URL(string: urlString) else {
            reply(callbackId, "")
            return
        }
        URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
            let base64 = data?.base64EncodedString() ?? ""
            DispatchQueue.main.async { self?.reply(callbackId, base64) }
        }.resume()
    }

    // MARK: - DoutuApp 桥的 JS 外壳
    //
    // Android 的 `addJavascriptInterface` 让页面能直接 `DoutuApp.xxx()` 同步取返回值。
    // WKWebView 只有异步 postMessage，所以这里垫一层：同步方法返回即刻可用的占位值，
    // 需要结果的走 callbackId（页面侧的 __doutuAppCallback）。
    /// 把排版宽度固定成 Android 那边的 360dp（原因见 viewDidLoad 里的注释）。
    /// 网页错误 → 原生 crash.log（白屏诊断用）
    static let errorHook = """
    (function(){
      function send(kind, msg, extra){
        try { window.webkit.messageHandlers.DoutuApp.postMessage({type:'jserror', kind:kind, message:String(msg||''), extra:String(extra||'')}); } catch(e){}
      }
      window.addEventListener('error', function(e){
        var t = e && e.target;
        var src = t && (t.src || t.href) || '';
        send('error', (e && e.message) || (src ? 'resource failed' : 'error'),
             (e && (e.filename||'') + ':' + (e.lineno||'')) + ' target=' + src);
      }, true);
      window.addEventListener('unhandledrejection', function(e){
        var r = e && e.reason; send('reject', (r && (r.message||r)) || 'reject', '');
      });
      var ce = console.error;
      console.error = function(){ send('console', Array.prototype.join.call(arguments, ' '), ''); ce && ce.apply(console, arguments); };
    })();
    """
    private static let viewportShim = """
    (function () {
      var m = document.querySelector('meta[name="viewport"]');
      if (!m) {
        m = document.createElement('meta');
        m.setAttribute('name', 'viewport');
        (document.head || document.documentElement).appendChild(m);
      }
      m.setAttribute('content', 'width=360, initial-scale=1.0, maximum-scale=1.0, user-scalable=no');
    })();
    """

    /// 底栏兜底：固定到视口底部，避免被 `.wechat-page { overflow:hidden }` 裁掉。
    private static let navFix = """
    (function () {
      function apply() {
        try {
          if (document.getElementById('doutu-ios-nav-fix')) return;
          var s = document.createElement('style');
          s.id = 'doutu-ios-nav-fix';
          s.textContent = [
            '.wechat-bottom-navbar{position:fixed!important;left:0!important;',
            'right:0!important;bottom:0!important;height:68px!important;',
            'display:grid!important;grid-template-columns:repeat(4,1fr)!important;',
            'align-items:center!important;background:#f8f8f8!important;',
            'z-index:2147483000!important;}',
            '.wechat-bottom-navbar *{opacity:1!important;visibility:visible!important;}'
          ].join('');
          (document.head || document.documentElement).appendChild(s);
        } catch (e) {}
      }
      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', apply);
      } else {
        apply();
      }
      setTimeout(apply, 800);
      setTimeout(apply, 2500);
    })();
    """

    private static let topbarFix = """
    (function(){
      function apply(){
        if (!document.getElementById('doutu-topbar-size')) {
          var style = document.createElement('style');
          style.id = 'doutu-topbar-size';
          style.textContent = [
            '.wechat-topbar__actions{column-gap:10px;align-items:center;padding-right:2px;}',
            '.wechat-topbar__icon{transform:scale(1.28);transform-origin:center;overflow:visible;margin-right:8px;}',
            '.wechat-topbar__icon--large{transform:scale(1.20);transform-origin:center;overflow:visible;}'
          ].join('');
          (document.head || document.documentElement).appendChild(style);
        }
        var bar = document.querySelector('.wechat-topbar');
        if (bar && !bar.__doutuHit) {
          bar.__doutuHit = 1;
          bar.style.position = 'relative';
          var hit = document.createElement('div');
          hit.style.cssText = 'position:absolute;top:0;right:0;bottom:0;left:0;z-index:40;background:transparent;pointer-events:auto;touch-action:manipulation;';
          hit.addEventListener('click', function(e){
            e.preventDefault(); e.stopPropagation();
            var rect = bar.getBoundingClientRect();
            var x = e.clientX - rect.left;
            if (x > rect.width - 35) {
              if (window.__fwOpenPlusMenu) window.__fwOpenPlusMenu();
            } else if (window.__fwOpenToolsSheet) {
              window.__fwOpenToolsSheet();
            }
          }, true);
          bar.appendChild(hit);
        }
        var deviceRow = document.querySelector('.wechat-device-row');
        if (deviceRow && !deviceRow.__doutuHit) {
          deviceRow.__doutuHit = 1;
          deviceRow.style.position = 'relative';
          var rowHit = document.createElement('div');
          rowHit.style.cssText = 'position:absolute;top:0;right:0;bottom:0;left:0;z-index:40;background:transparent;pointer-events:auto;touch-action:manipulation;';
          rowHit.addEventListener('click', function(e){
            e.preventDefault(); e.stopPropagation();
            if (window.__fwOpenToolsSheet) window.__fwOpenToolsSheet();
          }, true);
          deviceRow.appendChild(rowHit);
        }
      }
      apply();
      new MutationObserver(apply).observe(document.documentElement, {childList:true, subtree:true});
      setTimeout(apply, 800);
      setTimeout(apply, 2500);
    })();
    """

    private static let bridgeShim = """
    (function(){
      if (window.DoutuApp) return;
      function post(method, args, callbackId){
        try { window.webkit.messageHandlers.DoutuApp.postMessage(
          {method: method, args: args || [], callbackId: callbackId || null}); } catch (e) {}
      }
      window.DoutuApp = {
        httpGet: function(url, callbackId){ post('httpGet', [String(url)], callbackId); },
        downloadAvatarCacheUrl: function(u){ post('downloadAvatarCacheUrl', [String(u)]); return ''; },
        downloadAvatarDataUrl: function(u){ post('downloadAvatarDataUrl', [String(u)]); return ''; },
        getGalleryItems: function(){ post('getGalleryItems', []); return '[]'; },
        getGalleryImage: function(id, size){ post('getGalleryImage', [String(id), Number(size)||0]); return ''; },
        requestSaveImagePermission: function(){ post('requestSaveImagePermission', []); },
        requestGalleryPermission: function(){ post('requestGalleryPermission', []); },
        saveImageToGallery: function(dataUrl){ post('saveImageToGallery', [String(dataUrl)]); return 'ok'; },
        hasGalleryPermission: function(){ post('hasGalleryPermission', []); return true; },
        isScanDisabled: function(){ return window.__fwScanDisabled === true; },
        setCallAudioMode: function(inCall, speakerOn){ post('setCallAudioMode', [!!inCall, !!speakerOn]); },
        setPayPageSystemBars: function(visible){ post('setPayPageSystemBars', [!!visible]); },
        setMessageMenuOpen: function(open){ post('setMessageMenuOpen', [!!open]); },
        showSoftKeyboard: function(){ post('showSoftKeyboard', []); },
        returnToAppHome: function(){ post('returnToAppHome', []); },
        setHeaderTargets: function(v){ post('headerTargets', [!!v]); }
      };

      // 原生在上层盖了透明的加号/搜索按钮，原本会挡住网页子页右上角的
      // “批量添加”等按钮。这里轮询当前是否处于子页/浮层，实时通知原生隐藏。
      var lastHidden = null;
      function syncHeaderTargets() {
        var candidates = document.querySelectorAll('.fw-overlay, .fw-add-friend, .fw-group-chat, .fw-scanpay, .fw-receive-pay, .fw-local-conversation, .fw-chat-pay');
        var overlay = Array.prototype.find.call(candidates, function (el) {
          if (!el) return false;
          var s = getComputedStyle(el);
          return s.display !== 'none' && s.visibility !== 'hidden' && el.getClientRects().length > 0;
        });
        var hidden = !!overlay;
        try { document.body.classList.toggle('fw-subpage-open', hidden); } catch (e) {}
        if (hidden !== lastHidden) {
          lastHidden = hidden;
          post('headerTargets', [!hidden]);
        }
      }
      setInterval(syncHeaderTargets, 350);
      syncHeaderTargets();
    })();
    """
}

// MARK: - 包内文件的 scheme handler

/// 把 `app://local/<path>` 映射到包内 `web/<path>`。
///
/// 要点：
///   · 目录请求补 `index.html`
///   · 带 MIME 类型（JS 的 type 不对，ES module 会被拒）
///   · 404 也要回一个响应，不能调 `didFailWithError`（否则页面整块加载失败）
final class BundleSchemeHandler: NSObject, WKURLSchemeHandler {
    private static let mimeTypes: [String: String] = [
        "html": "text/html; charset=utf-8", "js": "text/javascript; charset=utf-8",
        "mjs": "text/javascript; charset=utf-8", "css": "text/css; charset=utf-8",
        "json": "application/json; charset=utf-8", "svg": "image/svg+xml",
        "png": "image/png", "jpg": "image/jpeg", "jpeg": "image/jpeg",
        "webp": "image/webp", "gif": "image/gif", "ico": "image/x-icon",
        "woff": "font/woff", "woff2": "font/woff2", "ttf": "font/ttf", "otf": "font/otf",
        "mp3": "audio/mpeg", "wav": "audio/wav", "mp4": "video/mp4", "txt": "text/plain; charset=utf-8",
    ]

    func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        guard let url = urlSchemeTask.request.url else {
            urlSchemeTask.didFailWithError(URLError(.badURL))
            return
        }
        var relative = url.path
        if relative.isEmpty || relative == "/" { relative = "/index.html" }
        if relative.hasSuffix("/") { relative += "index.html" }

        let bundle = Bundle.main
        // Resources/web 是文件夹引用，打包后是 bundle 根下的 web/ 目录
        let base = bundle.bundlePath + "/web"
        let candidates = [base + relative, base + relative + "/index.html",
                          base + "/index.html"]

        var data: Data?
        var usedPath = ""
        for path in candidates where FileManager.default.fileExists(atPath: path) {
            if let read = FileManager.default.contents(atPath: path) {
                data = read
                usedPath = path
                break
            }
        }

        // 页面用的是 hash 路由，深链接进来时 url.path 仍是 index.html；
        // 若真遇到带段路径的请求（如 /wechat/wallet/bills），回落 index.html 交给前端路由
        if data == nil, let fallback = FileManager.default.contents(atPath: base + "/index.html") {
            data = fallback
            usedPath = base + "/index.html"
        }

        // ⚠️ 关键：index.html 里是 `<script type="module" crossorigin src="./assets/index-*.js">`。
        // `crossorigin` 会强制走 CORS 检查，而 app://local 这个自定义 scheme 不会返回
        // Access-Control-Allow-Origin，于是**整个模块脚本被拒绝执行**，页面只剩空壳
        // （真机现象：只有标题栏和一行「Windows 微信已登录」，下面全白）。
        // 安卓 WebView 不做这个检查，所以安卓正常 —— 这里直接把 crossorigin 去掉。
        if var body = data, usedPath.hasSuffix(".html") {
            if let text = String(data: body, encoding: .utf8) {
                let cleaned = text
                    .replacingOccurrences(of: " crossorigin", with: "")
                    .replacingOccurrences(of: "crossorigin ", with: "")
                if let encoded = cleaned.data(using: .utf8) { body = encoded }
            }
            data = body
        }

        guard let body = data else {
            let response = HTTPURLResponse(url: url, statusCode: 404, httpVersion: "HTTP/1.1",
                                           headerFields: ["Content-Type": "text/plain",
                                                          "Access-Control-Allow-Origin": "*"])!
            urlSchemeTask.didReceive(response)
            urlSchemeTask.didReceive(Data())
            urlSchemeTask.didFinish()
            return
        }

        let ext = (usedPath as NSString).pathExtension.lowercased()
        let mime = Self.mimeTypes[ext] ?? "application/octet-stream"
        let headers = [
            "Content-Type": mime,
            "Content-Length": String(body.count),
            // 页面是本地资源，不需要缓存协商
            "Cache-Control": "no-cache",
            // 模块脚本（<script type="module">）永远按 CORS 规则取。
            // 自定义 scheme 的 origin 是 null，不给 ACAO 时 WKWebView 会直接拒绝执行，
            // 表现就是「经典脚本渲染出的标题栏还在、React 页面整块空白」。
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
            "Cross-Origin-Resource-Policy": "cross-origin",
        ]
        let response = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1",
                                       headerFields: headers)!
        urlSchemeTask.didReceive(response)
        urlSchemeTask.didReceive(body)
        urlSchemeTask.didFinish()
    }

    func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {
        // 没有长连接需要中断
    }
}

// MARK: - 页面加载完成后再交付路由

extension WeChatV2Host: WKNavigationDelegate {
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        CrashLog.append("WebHost didFinish \(webView.url?.absoluteString ?? "-")")
        webView.evaluateJavaScript(
            "(function(){var r=document.getElementById('root');return JSON.stringify({ready:document.readyState,root:r?r.innerHTML.length:-1,body:(document.body?document.body.innerText:'').slice(0,120)})})()"
        ) { result, error in
            CrashLog.append("WebHost didFinish state=\(result as? String ?? "-") err=\(error?.localizedDescription ?? "-")")
        }
        deliverPendingRouteIfReady()
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        CrashLog.append("WebHost didFail \(error.localizedDescription)")
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!,
                 withError error: Error) {
        CrashLog.append("WebHost didFailProvisional \(error.localizedDescription)")
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        CrashLog.append("WebHost content process terminated")
    }
}
