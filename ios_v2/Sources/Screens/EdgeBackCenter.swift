import UIKit

/// 任何页面都能响应左边缘右滑返回。
/// 原生 Canvas 页由 ScreenHostView 自己的手势处理；
/// 这里挂在 window 上，兜住登录、微信、联系人、图标选择等模态页面。
@MainActor
protocol EdgeBackHandling: AnyObject {
    func handleEdgeBack()
}

@MainActor
final class EdgeBackCenter: NSObject, UIGestureRecognizerDelegate {
    static let shared = EdgeBackCenter()

    private weak var installedWindow: UIWindow?
    private var lastHandledAt = Date.distantPast

    /// 同一个手势可能同时命中页面自己的 recognizer 和 window 上的 recognizer，
    /// 350ms 内只允许执行一次。
    static func shouldHandle() -> Bool {
        let now = Date()
        if now.timeIntervalSince(shared.lastHandledAt) < 0.35 { return false }
        shared.lastHandledAt = now
        return true
    }

    func install(on window: UIWindow) {
        guard installedWindow !== window else { return }
        installedWindow = window
        let edge = UIScreenEdgePanGestureRecognizer(target: self,
                                                     action: #selector(handleEdge(_:)))
        edge.edges = .left
        edge.cancelsTouchesInView = false
        edge.delegate = self
        window.addGestureRecognizer(edge)
    }

    @objc private func handleEdge(_ gesture: UIScreenEdgePanGestureRecognizer) {
        guard gesture.state == .ended,
              gesture.translation(in: gesture.view).x > 60 else { return }
        guard let top = topViewController() else { return }

        // 原生首页/页面栈由 ScreenHostView 处理，避免双重返回。
        guard top.presentingViewController != nil else { return }

        guard Self.shouldHandle() else { return }
        if let handler = top as? EdgeBackHandling {
            handler.handleEdgeBack()
        } else {
            // 没有实现协议的模态（例如系统相册/提示），按系统习惯关闭。
            top.dismiss(animated: true)
        }
    }

    private func topViewController() -> UIViewController? {
        guard let window = installedWindow,
              var controller = window.rootViewController else { return nil }
        while let presented = controller.presentedViewController {
            controller = presented
        }
        if let nav = controller as? UINavigationController,
           let visible = nav.visibleViewController {
            return visible
        }
        if let tab = controller as? UITabBarController,
           let selected = tab.selectedViewController {
            return selected
        }
        return controller
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        true
    }
}
