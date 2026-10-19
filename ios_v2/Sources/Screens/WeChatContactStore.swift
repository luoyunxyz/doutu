import UIKit
import WebKit

/// 从「微」的本地网页里读取通讯录（allProfiles / chat-actions contacts），
/// 供模拟来电选人使用：备注名优先，头像同步加载成 UIImage。
final class WeChatContactStore: NSObject, WKNavigationDelegate {
    static let shared = WeChatContactStore()

    private var webView: WKWebView?
    private var completion: (([CallSetupScreen.Contact]) -> Void)?
    private var finished = false
    private let cacheKey = "wechat.call.contacts.v1"

    private override init() { super.init() }

    /// 微页面同步通讯录时通过 FwContacts 消息直接缓存到原生，不依赖隐藏 WebView 的 origin。
    func save(_ body: Any) {
        guard let rows = body as? [[String: Any]] else { return }
        let slim: [[String: String]] = rows.compactMap { row in
            let id = String(row["id"] as? String ?? row["wechat"] as? String ?? "")
            let name = String((row["remark"] as? String).flatMap { $0.isEmpty ? nil : $0 }
                              ?? (row["nickname"] as? String) ?? (row["name"] as? String) ?? "")
            let avatar = String((row["avatarInfo"] as? String) ?? (row["avatar"] as? String) ?? "")
            guard !id.isEmpty, !name.isEmpty else { return nil }
            return ["id": id, "name": name, "avatar": avatar]
        }
        guard !slim.isEmpty else { return }
        UserDefaults.standard.set(slim, forKey: cacheKey)
        CrashLog.append("WeChatContactStore cached=\(slim.count)")
    }

    private func cachedProfiles() -> [[String: Any]] {
        guard let rows = UserDefaults.standard.array(forKey: cacheKey) as? [[String: String]] else {
            return []
        }
        return rows.map { ["id": $0["id"] ?? "", "name": $0["name"] ?? "",
                           "avatar": $0["avatar"] ?? ""] as [String: Any] }
    }

    func load(completion: @escaping ([CallSetupScreen.Contact]) -> Void) {
        self.completion = completion
        finished = false

        // 优先用微页面主动同步过来的缓存，打开来电页就是即时可用的。
        let cached = cachedProfiles()
        if !cached.isEmpty {
            CrashLog.append("WeChatContactStore use cache=\(cached.count)")
            processProfiles(cached)
            return
        }

        if let server = LocalWebServer.shared {
            server.start { [weak self] port in
                self?.startWeb(port: port ?? 18080)
            }
        } else {
            startWeb(port: 18080)
        }
    }

    private func startWeb(port: UInt16) {
        let config = WKWebViewConfiguration()
        let web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = self
        web.isHidden = true
        webView = web
        if let window = UIApplication.shared.windows.first(where: { $0.isKeyWindow }) {
            window.addSubview(web)
        }
        // 直接进「微」的路由，页面初始化时才会把 allProfiles/contacts 同步进
        // localStorage；只开根路径有时只拿到空壳。
        let raw = "http://localhost:\(port)/index.html#/wechat"
        CrashLog.append("WeChatContactStore loading \(raw)")
        if let url = URL(string: raw) {
            web.load(URLRequest(url: url))
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 9) { [weak self] in
            self?.finish([])
        }
    }

    private func finish(_ contacts: [CallSetupScreen.Contact]) {
        guard !finished else { return }
        finished = true
        webView?.removeFromSuperview()
        webView = nil
        let callback = completion
        completion = nil
        callback?(contacts)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        CrashLog.append("WeChatContactStore didFinish")
        let js = """
        (function () {
          try {
            if (typeof window.__fwGetCallContacts === 'function') {
              var hooked = window.__fwGetCallContacts();
              if (Array.isArray(hooked) && hooked.length) return JSON.stringify(hooked);
            }
            var list = JSON.parse(localStorage.getItem('allProfiles') || '[]');
            if (!Array.isArray(list) || !list.length) {
              var state = JSON.parse(localStorage.getItem('fake-world.wechat.chat-actions.v1') || '{}');
              list = Array.isArray(state.contacts) ? state.contacts : [];
            }
            return JSON.stringify(list.map(function (p) {
              return {
                id: String(p.id || p.wechat || p.name || ''),
                name: String(p.remark || p.nickname || p.name || ''),
                avatar: String(p.avatarInfo || p.avatar || '')
              };
            }).filter(function (p) { return p.id && p.name; }));
          } catch (e) { return '[]'; }
        })();
        """
        // 等 React 路由挂载完、__fwSyncNativeProfiles 写入 localStorage 后再读。
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak webView] in
            guard let webView else { return }
            webView.evaluateJavaScript(js) { [weak self] result, _ in
                self?.handleContactsResult(result)
            }
        }
    }

    private func handleContactsResult(_ result: Any?) {
        guard !finished else { return }
        let raw = (result as? String) ?? "[]"
        let data = raw.data(using: .utf8) ?? Data()
        let array = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]] ?? []
        CrashLog.append("WeChatContactStore raw=\(array.count)")
        processProfiles(array)
    }

    private func processProfiles(_ array: [[String: Any]]) {
        guard !finished else { return }
        let limited = Array(array.prefix(40))
        let group = DispatchGroup()
        var contacts = Array(repeating: CallSetupScreen.Contact(id: "", name: ""),
                             count: limited.count)
        let lock = NSLock()
        for (index, item) in limited.enumerated() {
            let id = item["id"] as? String ?? ""
            let name = item["name"] as? String ?? ""
            let avatar = item["avatar"] as? String ?? ""
            group.enter()
            Self.loadAvatar(avatar) { image in
                lock.lock()
                contacts[index] = CallSetupScreen.Contact(id: id, name: name, avatar: image)
                lock.unlock()
                group.leave()
            }
        }
        group.notify(queue: .main) {
            self.finish(contacts.filter { !$0.id.isEmpty && !$0.name.isEmpty })
        }
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        finish([])
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!,
                 withError error: Error) {
        finish([])
    }

    private static func loadAvatar(_ raw: String, completion: @escaping (UIImage?) -> Void) {
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.isEmpty { completion(nil); return }
        if value.hasPrefix("data:image/"),
           let comma = value.firstIndex(of: ","),
           let data = Data(base64Encoded: String(value[value.index(after: comma)...])) {
            completion(UIImage(data: data))
            return
        }
        var urlString = value
        if urlString.hasPrefix("./") {
            urlString = "http://localhost:18080/" + urlString.dropFirst(2)
        } else if !urlString.lowercased().hasPrefix("http") {
            if urlString.hasPrefix("/") {
                urlString = "http://localhost:18080" + urlString
            } else {
                urlString = "http://localhost:18080/" + urlString
            }
        }
        guard let url = URL(string: urlString) else { completion(nil); return }
        URLSession.shared.dataTask(with: url) { data, _, _ in
            let image = data.flatMap(UIImage.init(data:))
            DispatchQueue.main.async { completion(image) }
        }.resume()
    }
}
