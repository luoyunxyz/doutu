import UIKit
import WebKit

/// 从「微」的本地网页里读取通讯录（allProfiles / chat-actions contacts），
/// 供模拟来电选人使用：备注名优先，头像同步加载成 UIImage。
final class WeChatContactStore: NSObject, WKNavigationDelegate {
    static let shared = WeChatContactStore()

    private var webView: WKWebView?
    private var completion: (([CallSetupScreen.Contact]) -> Void)?
    private var finished = false

    private override init() { super.init() }

    func load(completion: @escaping ([CallSetupScreen.Contact]) -> Void) {
        self.completion = completion
        finished = false

        let config = WKWebViewConfiguration()
        let web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = self
        web.isHidden = true
        webView = web
        if let window = UIApplication.shared.windows.first(where: { $0.isKeyWindow }) {
            window.addSubview(web)
        }
        web.load(URLRequest(url: URL(string: "http://localhost:18080/")!))

        DispatchQueue.main.asyncAfter(deadline: .now() + 4) { [weak self] in
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
        let js = """
        (function () {
          try {
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
        webView.evaluateJavaScript(js) { [weak self] result, _ in
            guard let self else { return }
            let raw = (result as? String) ?? "[]"
            let data = raw.data(using: .utf8) ?? Data()
            let array = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]] ?? []
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
