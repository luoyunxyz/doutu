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
    private static var cache: [String: MockRemoteSpec] = [:]
    private static var loading: Set<String> = []

    static func key(_ module: MockModule, _ sub: Int) -> String {
        String(format: "%02d_%02d", module.rawValue, sub)
    }

    static func spec(_ module: MockModule, _ sub: Int) -> MockRemoteSpec? {
        cache[key(module, sub)]
    }

    static func load(_ module: MockModule, _ sub: Int, done: @escaping () -> Void) {
        let k = key(module, sub)
        if cache[k] != nil || loading.contains(k) { return }
        loading.insert(k)
        guard let url = URL(string: "http://192.168.1.96:8000/specs/\(k).json") else { return }
        var request = URLRequest(url: url)
        request.timeoutInterval = 2.5
        URLSession.shared.dataTask(with: request) { data, _, _ in
            DispatchQueue.main.async {
                loading.remove(k)
                if let data, let spec = try? JSONDecoder().decode(MockRemoteSpec.self, from: data) {
                    cache[k] = spec
                    done()
                }
            }
        }.resume()
    }

    static func page(from spec: MockRemoteSpec) -> MockPage {
        var ops: [MockOp] = []
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
                ops.append(.hairline(y, x, x + w, CssColor.parse(o.c) ?? .clear))
            case "t":
                let size = (o.s ?? 14) * 3
                let bold = o.b ?? false
                let baseline = y + Draw.font(size: size, bold: bold).ascender
                let color = CssColor.parse(o.c) ?? .black
                let text = o.t ?? ""
                switch o.a {
                case "center":
                    ops.append(.center(text, x + w / 2, baseline, size, color, bold))
                case "right":
                    ops.append(.right(text, x + w, baseline, size, color, bold))
                default:
                    ops.append(.text(text, x, baseline, size, color, bold))
                }
            default:
                break
            }
        }
        return MockPage(ops: ops, hits: [], height: spec.h * 3, background: .white)
    }
}
