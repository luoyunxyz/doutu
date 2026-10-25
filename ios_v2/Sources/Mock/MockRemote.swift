import UIKit

/// 远程布局（调试用）：从电脑上的 `specs/<id>_<sub>.json` 读取安卓页面的
/// 视觉块（背景/分割线/文字），按 1080 画布重绘。
///
/// 目的：一次装包之后，页面的字号/行距/位置都能在电脑上改 JSON 即时生效，
/// 不用为了每个像素重新构建安装。没有 spec 时回退到原生版式。
struct MockRemoteOp: Decodable {
    let k: String
    let x: CGFloat?
    let y: CGFloat?
    let w: CGFloat?
    let h: CGFloat?
    let c: String?
    let s: CGFloat?
    let b: Bool?
    let a: String?
    let t: String?
    let r: CGFloat?
    let src: String?
}

struct MockRemoteSpec: Decodable {
    let w: CGFloat
    let h: CGFloat
    let ops: [MockRemoteOp]
}

enum MockRemote {
    /// 调试总开关：默认 **关**。
    ///
    /// 远程 spec 只用来在电脑上改 JSON 调版式；成品页必须只由 App 内的版式决定，
    /// 否则同一页会随「debug_server 是否可达」变样（开着电脑是一种样子、拔掉网线
    /// 又是另一种），用户点「生成图片」得到的图就不可复现。
    /// 需要临时用远程 spec 对版式时，把它改成 `true` 重新打包即可。
    static let enabled = false

    private static var cache: [String: MockRemoteSpec] = [:]
    private static var images: [String: UIImage] = [:]
    private static var loading: Set<String> = []

    static func key(_ module: MockModule, _ sub: Int) -> String {
        String(format: "%02d_%02d", module.rawValue, sub)
    }

    static func spec(_ module: MockModule, _ sub: Int) -> MockRemoteSpec? {
        guard enabled else { return nil }
        return cache[key(module, sub)]
    }

    static func load(_ module: MockModule, _ sub: Int, done: @escaping () -> Void) {
        guard enabled else { return }
        let k = key(module, sub)
        if cache[k] != nil || loading.contains(k) { return }
        loading.insert(k)
        guard let url = URL(string: "http://192.168.1.96:8000/specs/\(k).json") else { return }
        var request = URLRequest(url: url)
        request.timeoutInterval = 2.5
        URLSession.shared.dataTask(with: request) { data, _, _ in
            guard let data, let spec = try? JSONDecoder().decode(MockRemoteSpec.self, from: data) else {
                DispatchQueue.main.async { loading.remove(k) }
                return
            }
            let srcs = spec.ops.filter { $0.k == "i" }.compactMap { $0.src }
            if srcs.isEmpty {
                DispatchQueue.main.async { loading.remove(k); cache[k] = spec; done() }
                return
            }
            let group = DispatchGroup()
            for src in srcs where images[src] == nil {
                group.enter()
                fetchImage(src) { img in
                    if let img { images[src] = img }
                    group.leave()
                }
            }
            group.notify(queue: .main) {
                loading.remove(k)
                cache[k] = spec
                done()
            }
        }.resume()
    }

    private static func fetchImage(_ src: String, done: @escaping (UIImage?) -> Void) {
        guard let url = URL(string: src) else { done(nil); return }
        var request = URLRequest(url: url)
        request.timeoutInterval = 4
        URLSession.shared.dataTask(with: request) { data, _, _ in
            done(data.flatMap { UIImage(data: $0) })
        }.resume()
    }

    static func page(from spec: MockRemoteSpec) -> MockPage {
        var ops: [MockOp] = []
        // 分割线单独收着，最后再画：spec 是按 DOM 顺序导出的，标题栏那条线排在
        // 白色底块**前面**，照原顺序画会被后面的底块盖住（参考图里标题下面那条线就丢了）。
        var lines: [MockOp] = []
        for o in spec.ops {
            let x = (o.x ?? 0) * 3
            let y = (o.y ?? 0) * 3
            let w = (o.w ?? 0) * 3
            let h = (o.h ?? 0) * 3
            switch o.k {
            case "r":
                let color = CssColor.parse(o.c) ?? .clear
                if let radius = o.r, radius > 0 {
                    ops.append(.round(CGRect(x: x, y: y, width: w, height: h),
                                      radius * 3, color))
                } else {
                    ops.append(.fill(CGRect(x: x, y: y, width: w, height: h), color))
                }
            case "l":
                lines.append(.hairline(y, x, x + w, CssColor.parse(o.c) ?? .clear))
            case "i":
                if let src = o.src, let img = images[src] {
                    ops.append(.bitmap(img, CGRect(x: x, y: y, width: w, height: h)))
                }
            case "t":
                let size = (o.s ?? 14) * 3
                let bold = o.b ?? false
                let font = Draw.font(size: size, bold: bold)
                // CSS 盒比一行字高很多时（按钮、标题栏），文字在盒内**垂直居中**；
                // 否则按「盒顶 + ascender」落基线（等价于 Android TextView 的 top padding）。
                let tall = h > size * 1.2 * 1.3
                let baseline = tall
                    ? y + h / 2 + font.ascender / 2 - size * 0.06
                    : y + font.ascender
                let color = CssColor.parse(o.c) ?? .black
                let text = o.t ?? ""
                var align = o.a
                var cx = x + w / 2
                // 标题栏：DOM 里标题盒是占满整栏的居中盒，但 `text-align` 是 start，
                // 直接按 start 画会偏在左边；参考图（以及安卓原生页）都是居中的。
                if (align == nil || align == "start"), y < 60 * 3, tall,
                   abs((x + w / 2) - spec.w * 1.5) < 12 {
                    align = "center"
                    cx = spec.w * 1.5
                }
                switch align {
                case "center":
                    ops.append(.center(text, cx, baseline, size, color, bold))
                case "right":
                    ops.append(.right(text, x + w, baseline, size, color, bold))
                default:
                    ops.append(.text(text, x, baseline, size, color, bold))
                }
            default:
                break
            }
        }
        return MockPage(ops: ops + lines, hits: [], height: spec.h * 3, background: .white)
    }

    /// 规格页顶部标题栏的高度：spec 里第一条 `y < 60` 的分割线（没有就按 44dp 兜底）。
    /// 用来补画 DOM 里没有的回退箭头 `‹`。
    static func headerHeight(_ spec: MockRemoteSpec) -> CGFloat {
        for o in spec.ops where o.k == "l" {
            if let y = o.y, y > 0, y < 60 { return y * 3 }
        }
        return dp(44)
    }
}
