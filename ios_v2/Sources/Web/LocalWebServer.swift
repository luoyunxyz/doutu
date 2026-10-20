import Foundation
import Network

/// 包内网页的本地 HTTP 服务（127.0.0.1 随机端口）。
///
/// 为什么不用 `app://` 自定义 scheme：WKWebView 对自定义 scheme 的 **ES module**
/// 会按 CORS 处理，而 `app://local` 的 origin 是 null；即使响应里补
/// `Access-Control-Allow-Origin: *`，模块脚本仍可能被拒绝，页面只剩经典脚本
/// 渲染出来的外壳（真机现象：微信标题栏 + “Windows 微信已登录”，下面全白）。
///
/// Android 那边是 `https://fake-world.local` + `shouldInterceptRequest`，是真正的
/// HTTPS origin；iOS 这边用回环 HTTP origin 达到同样效果。`NSAllowsLocalNetworking`
/// 已放开 localhost 明文。
final class LocalWebServer {
    /// 全 App 复用一个本地服务器：端口固定，WKWebView origin 才能稳定，
    /// 否则重复打开微信页会抢 18080 失败并退回 app://，localStorage 又换了 origin。
    static let shared = LocalWebServer()

    /// AppIconChooser 生成的 WebClip 描述文件；由本地服务器临时提供给 Safari 下载。
    static var webClipData: Data?
    private let listener: NWListener
    private let queue = DispatchQueue(label: "com.luoyun.doutu.localweb")
    private let root: String
    private(set) var port: UInt16 = 0
    private var started = false

    private init?() {
        // localStorage 按 origin（含端口）隔离。以前每次启动都随机端口，导致
        // 重启 App 后微信好友/朋友圈/聊天记录全部“消失”。固定端口后 origin
        // 稳定为 http://localhost:18080，WKWebView 才能复用同一份 localStorage。
        var candidate: NWListener?
        if let fixed = NWEndpoint.Port(rawValue: 18080) {
            candidate = try? NWListener(using: .tcp, on: fixed)
        }
        if candidate == nil {
            candidate = try? NWListener(using: .tcp)
        }
        guard let listener = candidate else { return nil }
        self.listener = listener
        self.root = Bundle.main.bundlePath + "/web"
        listener.newConnectionHandler = { [weak self] connection in
            self?.accept(connection)
        }
    }

    /// 启动并回调实际端口（失败回调 nil）
    func start(_ completion: @escaping (UInt16?) -> Void) {
        guard !started else { completion(port == 0 ? nil : port); return }
        started = true
        listener.stateUpdateHandler = { [weak self] state in
            guard let self else { return }
            switch state {
            case .ready:
                self.port = self.listener.port?.rawValue ?? 0
                CrashLog.append("LocalWebServer ready port=\(self.port)")
                DispatchQueue.main.async { completion(self.port == 0 ? nil : self.port) }
            case .failed(let error):
                CrashLog.append("LocalWebServer failed \(error.localizedDescription)")
                DispatchQueue.main.async { completion(nil) }
            case .cancelled:
                DispatchQueue.main.async { completion(nil) }
            default:
                break
            }
        }
        listener.start(queue: queue)
    }

    func stop() {
        listener.cancel()
    }

    // MARK: - 连接处理

    private func accept(_ connection: NWConnection) {
        connection.start(queue: queue)
        receive(connection, buffer: Data())
    }

    private func receive(_ connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) {
            [weak self] data, _, isComplete, error in
            guard let self else { connection.cancel(); return }
            var buffer = buffer
            if let data { buffer.append(data) }
            if let range = buffer.range(of: Data("\r\n\r\n".utf8)) {
                let head = String(data: buffer.subdata(in: 0..<range.lowerBound),
                                  encoding: .utf8) ?? ""
                self.respond(head, on: connection)
            } else if buffer.count > 2 * 1024 * 1024 || isComplete || error != nil {
                connection.cancel()
            } else {
                self.receive(connection, buffer: buffer)
            }
        }
    }

    private func respond(_ head: String, on connection: NWConnection) {
        let firstLine = head.components(separatedBy: "\r\n").first ?? ""
        let parts = firstLine.split(separator: " ")
        guard parts.count >= 2 else { connection.cancel(); return }
        let method = String(parts[0])
        var path = String(parts[1])
        if let q = path.firstIndex(of: "?") { path = String(path[..<q]) }
        path = path.removingPercentEncoding ?? path
        if path.isEmpty || path == "/" { path = "/index.html" }
        if path.hasSuffix("/") { path += "index.html" }

        if path == "/__webclip.mobileconfig", let data = Self.webClipData {
            send(status: "200 OK", mime: "application/x-apple-aspen-config",
                 body: data, method: method, on: connection)
            return
        }

        let file = root + path
        guard file.hasPrefix(root),
              let data = FileManager.default.contents(atPath: file) else {
            send(status: "404 Not Found", mime: "text/plain", body: Data("not found".utf8),
                 method: method, on: connection)
            return
        }
        let ext = (file as NSString).pathExtension.lowercased()
        send(status: "200 OK", mime: Self.mime(ext), body: data, method: method, on: connection)
    }

    private func send(status: String, mime: String, body: Data, method: String,
                      on connection: NWConnection) {
        var header = "HTTP/1.1 \(status)\r\n"
        header += "Content-Type: \(mime)\r\n"
        header += "Content-Length: \(body.count)\r\n"
        header += "Cache-Control: no-cache\r\n"
        header += "Access-Control-Allow-Origin: *\r\n"
        header += "Connection: close\r\n\r\n"
        var payload = Data(header.utf8)
        if method != "HEAD" { payload.append(body) }
        connection.send(content: payload, completion: .contentProcessed { _ in
            connection.cancel()
        })
    }

    private static func mime(_ ext: String) -> String {
        switch ext {
        case "html", "htm": return "text/html; charset=utf-8"
        case "js", "mjs": return "text/javascript; charset=utf-8"
        case "css": return "text/css; charset=utf-8"
        case "json": return "application/json; charset=utf-8"
        case "svg": return "image/svg+xml"
        case "png": return "image/png"
        case "jpg", "jpeg": return "image/jpeg"
        case "webp": return "image/webp"
        case "gif": return "image/gif"
        case "ico": return "image/x-icon"
        case "woff": return "font/woff"
        case "woff2": return "font/woff2"
        case "ttf": return "font/ttf"
        case "otf": return "font/otf"
        case "mp3": return "audio/mpeg"
        case "wav": return "audio/wav"
        case "mp4": return "video/mp4"
        case "txt": return "text/plain; charset=utf-8"
        default: return "application/octet-stream"
        }
    }
}
