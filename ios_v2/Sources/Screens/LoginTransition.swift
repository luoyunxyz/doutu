import UIKit

/// 登录页从右向左推入 / 向右退出，和 Android Activity 的转场一致。
@MainActor
final class LoginSlideTransition: NSObject, UIViewControllerTransitioningDelegate {
    static let shared = LoginSlideTransition()

    func animationController(forPresented presented: UIViewController,
                             presenting: UIViewController,
                             source: UIViewController) -> UIViewControllerAnimatedTransitioning? {
        LoginSlideAnimator(presenting: true)
    }

    func animationController(forDismissed dismissed: UIViewController)
        -> UIViewControllerAnimatedTransitioning? {
        LoginSlideAnimator(presenting: false)
    }
}

@MainActor
private final class LoginSlideAnimator: NSObject, UIViewControllerAnimatedTransitioning {
    private let presenting: Bool

    init(presenting: Bool) {
        self.presenting = presenting
    }

    func transitionDuration(using transitionContext: UIViewControllerContextTransitioning?) -> TimeInterval {
        0.28
    }

    func animateTransition(using context: UIViewControllerContextTransitioning) {
        let container = context.containerView
        let width = container.bounds.width

        if presenting {
            guard let to = context.view(forKey: .to) else {
                context.completeTransition(false)
                return
            }
            to.frame = container.bounds
            to.transform = CGAffineTransform(translationX: width, y: 0)
            container.addSubview(to)
            UIView.animate(withDuration: transitionDuration(using: context),
                           delay: 0,
                           options: [.curveEaseOut]) {
                to.transform = .identity
            } completion: { finished in
                context.completeTransition(finished && !context.transitionWasCancelled)
            }
        } else {
            guard let from = context.view(forKey: .from) else {
                context.completeTransition(false)
                return
            }
            UIView.animate(withDuration: transitionDuration(using: context),
                           delay: 0,
                           options: [.curveEaseIn]) {
                from.transform = CGAffineTransform(translationX: width, y: 0)
            } completion: { finished in
                from.transform = .identity
                context.completeTransition(finished && !context.transitionWasCancelled)
            }
        }
    }
}
