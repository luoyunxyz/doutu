import UIKit
import AVFoundation
import AudioToolbox

/// 模拟来电：到点后真正弹出全屏来电界面并响铃。
/// 界面按「微」首页的来电页复刻：头像虚化铺满、方形圆角头像、姓名、
/// 「邀请你语音通话...」、底部挂断/接听；接通后是麦克风/挂断/扬声器。
final class SimulatedCall: NSObject {
    static let shared = SimulatedCall()

    private var timer: Timer?
    private var controller: SimulatedCallViewController?
    private var onFire: (() -> Void)?
    private var pendingDate: Date?
    private var pendingName = ""
    private var pendingAvatar: UIImage?

    private override init() {
        super.init()
        NotificationCenter.default.addObserver(self,
                                               selector: #selector(appDidBecomeActive),
                                               name: UIApplication.didBecomeActiveNotification,
                                               object: nil)
    }

    func schedule(at date: Date, name: String, avatar: UIImage?, onFire: (() -> Void)? = nil) {
        cancel()
        self.onFire = onFire
        pendingDate = date
        pendingName = name
        pendingAvatar = avatar
        let interval = date.timeIntervalSinceNow
        if interval <= 0.2 {
            fire(name: name, avatar: avatar)
            return
        }
        let timer = Timer(timeInterval: interval, repeats: false) { [weak self] _ in
            self?.fire(name: name, avatar: avatar)
        }
        RunLoop.main.add(timer, forMode: .common)
        self.timer = timer
    }

    func cancel() {
        timer?.invalidate()
        timer = nil
        onFire = nil
        pendingDate = nil
        pendingName = ""
        pendingAvatar = nil
    }

    func preview() {
        guard let url = Self.ringtoneURL else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        let player = try? AVAudioPlayer(contentsOf: url)
        player?.numberOfLoops = 0
        player?.play()
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { player?.stop() }
    }

    @objc private func appDidBecomeActive() {
        guard let date = pendingDate, date <= Date() else { return }
        fire(name: pendingName, avatar: pendingAvatar)
    }

    private func fire(name: String, avatar: UIImage?) {
        timer = nil
        pendingDate = nil
        onFire?()
        onFire = nil
        guard let top = EditPrompt.topViewController() else { return }
        let vc = SimulatedCallViewController(name: name.isEmpty ? "朋友" : name,
                                             avatar: avatar)
        vc.modalPresentationStyle = .fullScreen
        controller = vc
        top.present(vc, animated: true)
    }

    fileprivate static var ringtoneURL: URL? {
        Bundle.main.url(forResource: "call", withExtension: "mp3",
                        subdirectory: "web/assets/audio")
        ?? Bundle.main.url(forResource: "call", withExtension: "mp3")
    }
}

final class SimulatedCallViewController: UIViewController {
    private let callerName: String
    private let avatar: UIImage?
    private var player: AVAudioPlayer?
    private var vibrateTimer: Timer?
    private var durationTimer: Timer?
    private var seconds = 0
    private var micOn = true
    private var speakerOn = false

    private let statusLabel = UILabel()
    private let actionsStack = UIStackView()
    private let avatarView = UIImageView()

    init(name: String, avatar: UIImage?) {
        self.callerName = name
        self.avatar = avatar
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) 未实现") }

    override func viewDidLoad() {
        super.viewDidLoad()
        buildBackground()
        buildContent()
        renderRinging()
        startRinging()
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        stopAll()
    }

    private func buildBackground() {
        view.backgroundColor = .black
        if let avatar {
            let background = UIImageView(image: avatar)
            background.contentMode = .scaleAspectFill
            background.frame = view.bounds
            background.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            background.transform = CGAffineTransform(scaleX: 1.25, y: 1.25)
            view.addSubview(background)

            let blur = UIVisualEffectView(effect: UIBlurEffect(style: .dark))
            blur.frame = view.bounds
            blur.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            view.addSubview(blur)
        }
        let dim = UIView(frame: view.bounds)
        dim.backgroundColor = UIColor(white: 0, alpha: 0.35)
        dim.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(dim)
    }

    private func buildContent() {
        avatarView.contentMode = .scaleAspectFill
        avatarView.layer.cornerRadius = 9
        avatarView.layer.masksToBounds = true
        avatarView.backgroundColor = UIColor(white: 0.22, alpha: 1)
        if let avatar {
            avatarView.image = avatar
        } else {
            let initial = UILabel()
            initial.text = String(callerName.prefix(1))
            initial.font = .systemFont(ofSize: 34, weight: .regular)
            initial.textColor = .white
            initial.textAlignment = .center
            initial.frame = CGRect(x: 0, y: 0, width: 76, height: 76)
            avatarView.addSubview(initial)
        }

        let nameLabel = UILabel()
        nameLabel.text = callerName
        nameLabel.font = .systemFont(ofSize: 20, weight: .regular)
        nameLabel.textColor = .white
        nameLabel.textAlignment = .center

        statusLabel.text = "邀请你语音通话..."
        statusLabel.font = .systemFont(ofSize: 15)
        statusLabel.textColor = UIColor(white: 0.72, alpha: 1)
        statusLabel.textAlignment = .center

        actionsStack.axis = .horizontal
        actionsStack.alignment = .top
        actionsStack.distribution = .equalSpacing
        actionsStack.spacing = 34

        for item in [avatarView, nameLabel, statusLabel, actionsStack] {
            item.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview(item)
        }

        NSLayoutConstraint.activate([
            avatarView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor,
                                            constant: 140),
            avatarView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            avatarView.widthAnchor.constraint(equalToConstant: 76),
            avatarView.heightAnchor.constraint(equalToConstant: 76),

            nameLabel.topAnchor.constraint(equalTo: avatarView.bottomAnchor, constant: 18),
            nameLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            nameLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),

            statusLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            statusLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            statusLabel.bottomAnchor.constraint(equalTo: actionsStack.topAnchor, constant: -54),

            actionsStack.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            actionsStack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor,
                                                 constant: -54),
            actionsStack.heightAnchor.constraint(equalToConstant: 96),
        ])
    }

    private func actionItem(title: String, symbol: String, background: UIColor,
                            selector: Selector) -> UIView {
        let button = UIButton(type: .system)
        button.backgroundColor = background
        button.tintColor = .white
        button.layer.cornerRadius = 33.5
        button.setImage(UIImage(systemName: symbol), for: .normal)
        button.imageView?.contentMode = .scaleAspectFit
        button.addTarget(self, action: selector, for: .touchUpInside)
        button.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            button.widthAnchor.constraint(equalToConstant: 67),
            button.heightAnchor.constraint(equalToConstant: 67),
        ])
        button.accessibilityLabel = title

        let label = UILabel()
        label.text = title
        label.font = .systemFont(ofSize: 12)
        label.textColor = UIColor(white: 0.78, alpha: 1)
        label.textAlignment = .center

        let stack = UIStackView(arrangedSubviews: [button, label])
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = 9
        return stack
    }

    private func renderRinging() {
        actionsStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        actionsStack.addArrangedSubview(actionItem(title: "挂断", symbol: "phone.down.fill",
                                                   background: Draw.color(0xffd54241),
                                                   selector: #selector(declineCall)))
        actionsStack.addArrangedSubview(actionItem(title: "接听", symbol: "phone.fill",
                                                   background: Draw.color(0xff1bc911),
                                                   selector: #selector(acceptCall)))
    }

    private func renderActive() {
        actionsStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        actionsStack.addArrangedSubview(actionItem(title: micOn ? "麦克风已开" : "麦克风已关",
                                                   symbol: micOn ? "mic.fill" : "mic.slash.fill",
                                                   background: UIColor(white: 0.24, alpha: 1),
                                                   selector: #selector(toggleMic)))
        actionsStack.addArrangedSubview(actionItem(title: "挂断", symbol: "phone.down.fill",
                                                   background: Draw.color(0xffd54241),
                                                   selector: #selector(declineCall)))
        actionsStack.addArrangedSubview(actionItem(title: speakerOn ? "扬声器已开" : "扬声器已关",
                                                   symbol: speakerOn ? "speaker.wave.2.fill" : "speaker.slash.fill",
                                                   background: UIColor(white: 0.24, alpha: 1),
                                                   selector: #selector(toggleSpeaker)))
    }

    private func startRinging() {
        guard let url = SimulatedCall.ringtoneURL else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        player = try? AVAudioPlayer(contentsOf: url)
        player?.numberOfLoops = -1
        player?.play()
        vibrateTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { _ in
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        }
    }

    private func stopRinging() {
        player?.stop()
        player = nil
        vibrateTimer?.invalidate()
        vibrateTimer = nil
    }

    @objc private func acceptCall() {
        stopRinging()
        renderActive()
        statusLabel.text = "00:00"
        seconds = 0
        durationTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            guard let self else { return }
            self.seconds += 1
            self.statusLabel.text = String(format: "%02d:%02d",
                                           self.seconds / 60, self.seconds % 60)
        }
    }

    @objc private func toggleMic() {
        micOn.toggle()
        renderActive()
    }

    @objc private func toggleSpeaker() {
        speakerOn.toggle()
        try? AVAudioSession.sharedInstance().overrideOutputAudioPort(speakerOn ? .speaker : .none)
        renderActive()
    }

    @objc private func declineCall() {
        stopAll()
        dismiss(animated: true)
    }

    private func stopAll() {
        stopRinging()
        durationTimer?.invalidate()
        durationTimer = nil
        try? AVAudioSession.sharedInstance().setActive(false)
    }
}
