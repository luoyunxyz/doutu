import Foundation
import UIKit

/// 真机调试遥控（**仅调试期使用**）。
///
/// 为什么需要：真机视觉校准要反复「点一下 → 看结果」，但手上只有两个只读通道
/// （爱思助手的实时屏幕只能看、不能反向控制；系统的崩溃日志拿不到点击后的画面）。
/// 于是让 App 主动去拉指令：
///
/// ```text
/// App ──GET /cmd.json──▶ 电脑上的 debug_server.py      {"seq": 12, "token": "tab:1"}
///     ◀──返回上一帧截图── POST /shot                    （raw PNG）
/// ```
///
/// 指令就是 Router 认识的 `ActionToken`（`tab:1`、`page:1`、`zhiCell:3`、`back`、
/// `weiCell:0:0` …），所以和真实点击走**完全同一条代码路径**，测出来的就是用户看到的。
///
/// 关闭方式：注释掉 `ScreenHostController.viewDidLoad` 里的 `DebugBridge.shared.start()`，
/// 或把 `Self.enabled` 改成 false。正式发布前必须关掉。
final class DebugBridge {
    /// 调试总开关
    static let enabled = true

    static let shared = DebugBridge()
    private init() {}

    /// 电脑上 `debug_server.py` 的地址（手机与电脑同一 Wi-Fi）
    private let base = URL(string: "http://192.168.1.96:8000")!

    private var timer: Timer?
    /// 已执行的指令序号，避免同一条指令重复执行
    private var lastSeq = -1

    /// 启动即跳转：从 Documents/demo.txt 读一个命令令牌并执行一次。
    /// 用途是**不经点按**就能在真机上逐屏截图验证（配合 `pymobiledevice3 apps push` + `dvt launch`）。
    @MainActor
    func runLaunchTokenIfNeeded() {
        let url = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("demo.txt")
        guard let raw = try? String(contentsOf: url, encoding: .utf8) else { return }
        let token = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !token.isEmpty else { return }
        CrashLog.append("launch token: \(token)")
        Router.shared?.handle(token, at: .zero)
        Router.shared?.hostView?.setNeedsDisplay()
    }

    func start() {
        guard Self.enabled, timer == nil else { return }
        // 先记一条日志，方便在文件 App 里确认调试端起来了
        CrashLog.append("DebugBridge started → \(base.absoluteString)")
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.poll()
        }
    }

    // MARK: - 拉指令

    private func poll() {
        var request = URLRequest(url: base.appendingPathComponent("cmd.json"))
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = 2
        URLSession.shared.dataTask(with: request) { [weak self] data, _, _ in
            guard let self, let data, !data.isEmpty,
                  let raw = try? JSONSerialization.jsonObject(with: data),
                  let obj = raw as? [String: Any],
                  let seq = obj["seq"] as? Int else { return }
            let token = (obj["token"] as? String) ?? "noop"
            Task { @MainActor in
                guard seq != self.lastSeq else { return }
                self.lastSeq = seq
                if token == "report" {
                    // 回传 crash.log（文本）：远程诊断「水印没挂上」这类问题
                    self.uploadDiagnostics()
                    return
                }
                // 网页截图：只截 WKWebView 内容（原生 drawHierarchy 截不到跨进程网页）
                if token == "webshot" {
                    guard let web = WeChatV2Host.current else {
                        CrashLog.append("DebugBridge webshot: no WebHost")
                        self.captureAndUpload(seq: seq, token: token)
                        return
                    }
                    web.snapshot { image in
                        if let image { self.upload(image: image, seq: seq, name: "web") }
                        else { CrashLog.append("DebugBridge webshot: snapshot nil") }
                    }
                    return
                }
                // 整窗截图：包含模态页（网页页 / 弹窗），用于逐屏对比
                if token == "winshot" {
                    self.captureWindowAndUpload(seq: seq)
                    return
                }
                // 网页 DOM 诊断：结果写 crash.log，随后用 report 取回
                if token == "webinfo" {
                    WeChatV2Host.current?.logDiagnostics()
                    return
                }
                if token.hasPrefix("webjs:"),
                   let data = Data(base64Encoded: String(token.dropFirst("webjs:".count))),
                   let script = String(data: data, encoding: .utf8) {
                    WeChatV2Host.current?.debugEval(script)
                    return
                }
                if token != "noop" {
                    Router.shared?.handle(token, at: .zero)
                }
                // 等一拍再截图：让 setNeedsDisplay 的绘制真正发生
                try? await Task.sleep(nanoseconds: 450_000_000)
                self.captureAndUpload(seq: seq, token: token)
            }
        }.resume()
    }

    // MARK: - 回传截图

    @MainActor
    private func captureAndUpload(seq: Int, token: String) {
        guard let host = Router.shared?.hostView else { return }
        let renderer = UIGraphicsImageRenderer(bounds: host.bounds)
        let image = renderer.image { _ in
            host.drawHierarchy(in: host.bounds, afterScreenUpdates: true)
        }
        guard let png = image.pngData() else { return }
        CrashLog.append("DebugBridge shot seq=\(seq) token=\(token) bytes=\(png.count)")
        upload(png: png, seq: seq, name: nil)
    }

    /// 截整个 key window（含模态页）。`drawHierarchy` 对普通 UIKit 页面有效，
    /// WKWebView 的跨进程内容可能为空，所以网页另用 `webshot`。
    @MainActor
    private func captureWindowAndUpload(seq: Int) {
        let window = UIApplication.shared.windows.first(where: { $0.isKeyWindow })
            ?? UIApplication.shared.windows.first
        guard let window else { return }
        let renderer = UIGraphicsImageRenderer(bounds: window.bounds)
        let image = renderer.image { _ in
            window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        }
        guard let png = image.pngData() else { return }
        CrashLog.append("DebugBridge winshot seq=\(seq) bytes=\(png.count)")
        upload(png: png, seq: seq, name: "window")
    }

    @MainActor
    private func upload(image: UIImage, seq: Int, name: String) {
        guard let png = image.pngData() else { return }
        CrashLog.append("DebugBridge \(name)shot seq=\(seq) bytes=\(png.count)")
        upload(png: png, seq: seq, name: name)
    }

    private func upload(png: Data, seq: Int, name: String?) {
        var request = URLRequest(url: base.appendingPathComponent("shot"))
        request.httpMethod = "POST"
        request.setValue("image/png", forHTTPHeaderField: "Content-Type")
        if let name { request.setValue(name, forHTTPHeaderField: "X-Shot-Name") }
        request.timeoutInterval = 6
        URLSession.shared.uploadTask(with: request, from: png).resume()
    }

    // MARK: - 回传诊断文本

    /// 把 Documents/crash.log 当正文 POST 到 /shot（服务端按字节保存，名字仍是 .png，内容是文本）
    @MainActor
    private func uploadDiagnostics() {
        let url = CrashLog.fileURL
        let text = (try? String(contentsOf: url, encoding: .utf8)) ?? "(没有 crash.log)"
        let body = Data(text.utf8)
        CrashLog.append("DebugBridge report upload bytes=\(body.count)")
        var request = URLRequest(url: base.appendingPathComponent("shot"))
        request.httpMethod = "POST"
        request.setValue("text/plain; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 6
        URLSession.shared.uploadTask(with: request, from: body).resume()
    }
}
