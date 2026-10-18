import UIKit

/// 收款码页「二维码」入口：从相册选一张收款码替换默认图案。
/// 对应 Android `CollectMoneyActivity.pickCode()`。
final class QRImagePicker: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    private static var current: QRImagePicker?
    private var completion: ((UIImage?) -> Void)?

    static func present(from host: UIViewController, completion: @escaping (UIImage?) -> Void) {
        let picker = UIImagePickerController()
        picker.sourceType = .photoLibrary
        picker.allowsEditing = false
        let delegate = QRImagePicker()
        delegate.completion = completion
        picker.delegate = delegate
        current = delegate
        host.present(picker, animated: true)
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
        completion?(nil)
        Self.current = nil
    }

    func imagePickerController(_ picker: UIImagePickerController,
                               didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        let image = info[.originalImage] as? UIImage
        picker.dismiss(animated: true)
        completion?(image)
        Self.current = nil
    }
}
