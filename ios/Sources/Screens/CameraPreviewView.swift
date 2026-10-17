import AVFoundation
import UIKit

/// 相机预览 —— 对应 Android `ScanActivity` 里那个 `TextureView` + 后置摄像头。
///
/// Android 用的是旧 Camera API + `previewRotation = 90`（竖屏）；iOS 这边用
/// `AVCaptureVideoPreviewLayer`，方向交给它的 connection 处理。
///
/// **不解码、不上传**：这一层只负责把画面显示出来，与 Android 的注释一致
/// （"nothing is decoded or uploaded"）。扫码识别本身两边都没做。
///
/// 权限：
///   · 首次显示时请求 `AVCaptureDevice.requestAccess(for: .video)`
///   · 用户拒绝 → 显示一行提示，不崩、不黑屏卡住
///   · 模拟器没有摄像头 → 同样走「不可用」提示
final class CameraPreviewView: UIView {
    /// 预览层就是它自己的 backing layer，省掉一层容器
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }

    private var previewLayer: AVCaptureVideoPreviewLayer? {
        layer as? AVCaptureVideoPreviewLayer
    }

    private let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "doutu.camera.session")
    private let hintLabel = UILabel()
    private var configured = false
    /// 配置成功且正在出画面
    private(set) var isRunning = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        clipsToBounds = true
        setupHint()
        prepare()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) 未实现") }

    private func setupHint() {
        hintLabel.text = "相机不可用"
        hintLabel.textColor = UIColor.white.withAlphaComponent(0.8)
        hintLabel.font = .systemFont(ofSize: 15)
        hintLabel.textAlignment = .center
        hintLabel.numberOfLines = 0
        hintLabel.isHidden = true
        addSubview(hintLabel)
        hintLabel.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            hintLabel.centerXAnchor.constraint(equalTo: centerXAnchor),
            hintLabel.centerYAnchor.constraint(equalTo: centerYAnchor),
            hintLabel.widthAnchor.constraint(lessThanOrEqualTo: widthAnchor, multiplier: 0.8),
        ])
    }

    private func showHint(_ text: String) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.hintLabel.text = text
            self.hintLabel.isHidden = false
        }
    }

    /// 权限 + 配置，都在后台队列上做（`startRunning` 会阻塞，不能放主线程）
    private func prepare() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configureAndStart()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                guard let self else { return }
                if granted {
                    self.configureAndStart()
                } else {
                    self.showHint("已拒绝相机权限\n可在「设置 → 抖音图 → 相机」里打开")
                }
            }
        default:
            showHint("已拒绝相机权限\n可在「设置 → 抖音图 → 相机」里打开")
        }
    }

    private func configureAndStart() {
        sessionQueue.async { [weak self] in
            guard let self, !self.configured else { return }
            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera,
                                                       for: .video, position: .back)
                ?? AVCaptureDevice.default(for: .video) else {
                self.showHint("这台设备没有可用的摄像头")
                return
            }
            do {
                let input = try AVCaptureDeviceInput(device: device)
                self.session.beginConfiguration()
                self.session.sessionPreset = .high
                if self.session.canAddInput(input) { self.session.addInput(input) }
                self.session.commitConfiguration()
                self.configured = true
                DispatchQueue.main.async {
                    self.attachPreview()
                }
                self.session.startRunning()
                DispatchQueue.main.async { self.isRunning = true }
            } catch {
                self.showHint("相机启动失败：\(error.localizedDescription)")
            }
        }
    }

    private func attachPreview() {
        guard let previewLayer else { return }
        previewLayer.session = session
        previewLayer.videoGravity = .resizeAspectFill
        // 竖屏（Android 侧靠 previewRotation = 90 达到同一效果）
        if let connection = previewLayer.connection,
           connection.isVideoOrientationSupported {
            connection.videoOrientation = .portrait
        }
        bringSubviewToFront(hintLabel)
    }

    func stop() {
        sessionQueue.async { [weak self] in
            guard let self, self.session.isRunning else { return }
            self.session.stopRunning()
            DispatchQueue.main.async { self.isRunning = false }
        }
    }

    deinit {
        let session = self.session
        sessionQueue.async {
            if session.isRunning { session.stopRunning() }
        }
    }
}
