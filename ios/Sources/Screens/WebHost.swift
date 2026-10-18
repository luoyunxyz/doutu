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
final class WebHostController: UIViewController, WKScriptMessageHandler {
    /// 页面根路径（包内 Resources/web）
    private static let scheme = "app"
    private static let host = "local"
    private static let entry = "app://local/index.html#/wechat"

    private var webView: WKWebView!
    /// 待执行的路由（页面还没起来时先攥着，起来后再交过去）
    private var pendingRoute: String?
    /// 左上角返回按钮（这个模态没有导航栏，必须自己补）
    private var backButton: UIButton?

    /// 初始路由：`#/wechat` 或 `#/wechat/...` 或 `feature:xxx`
    init(route: String = "#/wechat") {
        super.init(nibName: nil, bundle: nil)
        pendingRoute = route
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .white

        let config = WKWebViewConfiguration()
        config.setURLSchemeHandler(BundleSchemeHandler(), forURLScheme: Self.scheme)
        // 与 Android 一致：允许页面里的内联媒体自动播放（来电铃声那类）
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        config.userContentController.add(self, name: "DoutuApp")

        webView = WKWebView(frame: view.bounds, configuration: config)
        webView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        webView.navigationDelegate = self
        webView.scrollView.bounces = false
        webView.isOpaque = false
        webView.backgroundColor = .white
        view.addSubview(webView)

        // 返回入口。
        //
        // 这个控制器是 `.fullScreen` 模态弹出来的（`presentWeb` 里设的），既没有导航栏，
        // 页面本身也没有返回按钮 —— 不补这个就「进去出不来」。
        // 给两个入口：左上角浮层按钮（看得见） + 左边缘右滑（符合直觉）。
        installBackAffordance()

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
        let jsErrorHook = WKUserScript(source: WebHostController.errorHook, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        config.userContentController.addUserScript(jsErrorHook)

        if let url = URL(string: Self.entry) {
            webView.load(URLRequest(url: url))
        }
    }

    /// 从宫格进来时切换路由（页面已就绪就直接调，否则等 didFinish）
    func open(route: String) {
        pendingRoute = route
        deliverPendingRouteIfReady()
    }

    // MARK: - 返回

    /// 左上角半透明返回按钮 + 左边缘右滑
    private func installBackAffordance() {
        let button = UIButton(type: .system)
        button.setTitle("‹ 返回", for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 13, weight: .semibold)
        button.setTitleColor(.white, for: .normal)
        button.backgroundColor = UIColor.black.withAlphaComponent(0.38)
        button.layer.cornerRadius = 15
        button.frame = CGRect(x: 8, y: 0, width: 62, height: 30)
        button.autoresizingMask = [.flexibleRightMargin]
        button.addTarget(self, action: #selector(goBack), for: .touchUpInside)
        backButton = button
        view.addSubview(button)

        let edge = UIScreenEdgePanGestureRecognizer(target: self, action: #selector(handleEdgePan(_:)))
        edge.edges = .left
        view.addGestureRecognizer(edge)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        // 贴在安全区下方，避免被刘海/状态栏压住
        let top = view.safeAreaInsets.top + 6
        backButton?.frame = CGRect(x: 8, y: top, width: 62, height: 30)
        view.bringSubviewToFront(backButton ?? UIView())
    }

    @objc private func goBack() {
        dismiss(animated: true)
    }

    @objc private func handleEdgePan(_ gesture: UIScreenEdgePanGestureRecognizer) {
        guard gesture.state == .ended else { return }
        let translation = gesture.translation(in: view)
        if translation.x > 60, abs(translation.y) < 120 {
            dismiss(animated: true)
        }
    }

    private func deliverPendingRouteIfReady() {
        guard let route = pendingRoute else { return }
        let script: String
        if route.hasPrefix("feature:") {
            let name = String(route.dropFirst("feature:".count))
            script = "window.__fwOpenFeature && window.__fwOpenFeature(\(Self.jsString(name)))"
        } else {
            script = "window.__fwOpenRoute && window.__fwOpenRoute(\(Self.jsString(route)))"
        }
        webView.evaluateJavaScript("(function(){try{\(script);return 1}catch(e){return 0}})()") {
            [weak self] result, _ in
            guard let self else { return }
            if let ok = result as? Int, ok == 1 {
                self.pendingRoute = nil
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

    // MARK: - 页面调原生

    func userContentController(_ controller: WKUserContentController,
                               didReceive message: WKScriptMessage) {
        guard let body = message.body as? [String: Any],
              let method = body["method"] as? String else { return }
        let args = body["args"] as? [Any] ?? []
        let callbackId = body["callbackId"] as? String

        switch method {
        case "httpGet":
            // 对应 Android `httpGet(url, callbackId)`：抓完调 page 的 __doutuAppCallback
            if let urlString = args.first as? String {
                nativeHttpGet(urlString, callbackId: callbackId)
            }
        case "returnToAppHome":
            navigationController?.popToRootViewController(animated: true)
        case "hasGalleryPermission":
            reply(callbackId, true)
        case "getGalleryItems":
            reply(callbackId, "[]")
        case "isScanDisabled":
            reply(callbackId, false)
        default:
            // 其余方法（相册保存、来电音频、键盘、系统栏…）先按成功/空值应答，
            // 让页面逻辑不至于卡住；逐个补齐记在 README 的待办里。
            reply(callbackId, NSNull())
        }
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
        send('error', (e && e.message) || 'error', (e && (e.filename||'') + ':' + (e.lineno||'')));
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
        isScanDisabled: function(){ post('isScanDisabled', []); return false; },
        setCallAudioMode: function(inCall, speakerOn){ post('setCallAudioMode', [!!inCall, !!speakerOn]); },
        setPayPageSystemBars: function(visible){ post('setPayPageSystemBars', [!!visible]); },
        setMessageMenuOpen: function(open){ post('setMessageMenuOpen', [!!open]); },
        showSoftKeyboard: function(){ post('showSoftKeyboard', []); },
        returnToAppHome: function(){ post('returnToAppHome', []); }
      };
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
                                           headerFields: ["Content-Type": "text/plain"])!
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

extension WebHostController: WKNavigationDelegate {
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        deliverPendingRouteIfReady()
    }
}
