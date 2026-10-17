import UIKit
import PhotosUI

/// 关联对象的 key（Swift 里不能用静态属性取地址，必须是文件级变量）
private var avatarPickerDelegateKey: UInt8 = 0

/// 头像上传 —— 对应 Android `account/AvatarUpload.java` + `ProfileStore` 那套。
///
/// 流程与 Android 完全一致：
///   选图 → 居中裁成正方形 → 缩到 320px → JPEG 0.85 → base64 → `POST /api/v1/auth/avatar`
/// → 成功后把服务端返回的 `data.user` 覆盖本地缓存（头像 URL + 昵称），并刷新界面。
///
/// iOS 侧用系统相册选择器 `PHPickerViewController`（iOS 14+）：
/// 它**不需要相册权限**（用户选中的那一张由系统直接交给 App），所以 Info.plist 里那句
/// 权限说明只在本机拍照（相机）时才用得上 —— 这点和 Android 要申请 `READ_MEDIA_IMAGES` 不同。
enum AvatarPicker {

    /// 弹出相册并上传；`onDone` 在成功/失败后都会回调（用于刷新界面）
    @MainActor
    static func present(onDone: ((Bool, String) -> Void)? = nil) {
        guard let host = UpdatePrompt.topViewController() else { return }

        var config = PHPickerConfiguration(photoLibrary: .shared())
        config.filter = .images
        config.selectionLimit = 1
        // 让系统直接给一张压好的图，省掉一次大图解码（头像最终只有 320px）
        config.preferredAssetRepresentationMode = .current

        let picker = PHPickerViewController(configuration: config)
        let delegate = Delegate(onDone: onDone)
        picker.delegate = delegate
        // PHPicker 不持有 delegate，这里用关联对象保命
        objc_setAssociatedObject(picker, &avatarPickerDelegateKey, delegate,
                                 .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        host.present(picker, animated: true)
    }

    /// 选图完成后干活：裁方、缩放、压缩、上传
    fileprivate static func handle(picked image: UIImage, onDone: ((Bool, String) -> Void)?) {
        let square = centerSquare(image)
        Task {
            let outcome = await AuthService.uploadAvatar(square)
            await MainActor.run {
                onDone?(outcome.ok, outcome.message)
                if outcome.ok {
                    // 通知界面重画（「我」页与个人信息页都读 ProfileStore）
                    NotificationCenter.default.post(name: .doutuProfileChanged, object: nil)
                }
            }
        }
    }

    /// 居中裁成正方形（与 Android 端 `Bitmap.createBitmap` 的居中裁剪一致）
    static func centerSquare(_ image: UIImage) -> UIImage {
        let size = image.size
        guard size.width > 0, size.height > 0 else { return image }
        let side = min(size.width, size.height)
        let origin = CGPoint(x: (size.width - side) / 2, y: (size.height - side) / 2)
        guard let cg = image.cgImage?.cropping(
            to: CGRect(x: origin.x * image.scale, y: origin.y * image.scale,
                       width: side * image.scale, height: side * image.scale)) else {
            return image
        }
        return UIImage(cgImage: cg, scale: image.scale, orientation: image.imageOrientation)
    }

    /// 相册选择器的代理（必须被强引用到选择结束）
    private final class Delegate: NSObject, PHPickerViewControllerDelegate {
        private let onDone: ((Bool, String) -> Void)?

        init(onDone: ((Bool, String) -> Void)?) {
            self.onDone = onDone
        }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            picker.dismiss(animated: true)
            guard let provider = results.first?.itemProvider,
                  provider.canLoadObject(ofClass: UIImage.self) else {
                // 用户取消：不提示
                return
            }
            provider.loadObject(ofClass: UIImage.self) { object, _ in
                guard let image = object as? UIImage else {
                    DispatchQueue.main.async { self.onDone?(false, "图片读取失败") }
                    return
                }
                DispatchQueue.main.async {
                    AvatarPicker.handle(picked: image, onDone: self.onDone)
                }
            }
        }
    }
}

extension Notification.Name {
    /// 资料（名字/头像）变了，界面需要重画
    static let doutuProfileChanged = Notification.Name("doutu.profile.changed")
}
