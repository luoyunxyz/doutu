import UIKit
import AVFoundation
import AudioToolbox

/// 模拟来电：到点后真正弹出全屏来电界面并响铃，而不是只弹一条 toast。
/// 设置页只负责把联系人/时间传进来；计时器、铃声、接听/挂断都在这里。
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

    /// App 从后台回来时 Timer 可能没走，补一次“是否已经到点”。
    @objc private func appDidBecomeActive() {
        guard let date = pendingDate, date <= Date() else { return }
        fire(name: pendingName, avatar: pendingAvatar)
    }

    func preview() {
        guard let url = Self.ringtoneURL else { return }
        let player = try? AVAudioPlayer(contentsOf: url)
        player?.numberOfLoops = 0
        player?.play()
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { player?.stop() }
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

    private let subtitleLabel = UILabel()
    private let durationLabel = UILabel()
    private let acceptButton = UIButton(type: .system)
    private let declineButton = UIButton(type: .system)
    private let hangupButton = UIButton(type: .system)

    init(name: String, avatar: UIImage?) {
        self.callerName = name
        self.avatar = avatar
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) 未实现") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Draw.color(0xff111827)
        buildBackground()
        buildContent()
        startRinging()
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        stopAll()
    }

    private func buildBackground() {
        let gradient = CAGradientLayer()
        gradient.frame = view.bounds
        gradient.colors = [Draw.color(0xff1f2937).cgColor,
                           Draw.color(0xff0b1020).cgColor,
                           UIColor.black.cgColor]
        gradient.locations = [0, 0.45, 1]
        gradient.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.layer.insertSublayer(gradient, at: 0)
    }

    private func buildContent() {
        let avatarView = UIImageView()
        avatarView.contentMode = .scaleAspectFill
        avatarView.layer.cornerRadius = 62
        avatarView.layer.masksToBounds = true
        avatarView.backgroundColor = Draw.color(0xff374151)
        if let avatar {
            avatarView.image = avatar
        } else {
            let initial = UILabel()
            initial.text = String(callerName.prefix(1))
            initial.font = .systemFont(ofSize: 48, weight: .semibold)
            initial.textColor = .white
            initial.textAlignment = .center
            initial.frame = CGRect(x: 0, y: 0, width: 124, height: 124)
            avatarView.addSubview(initial)
        }

        let nameLabel = UILabel()
        nameLabel.text = callerName
        nameLabel.font = .systemFont(ofSize: 32, weight: .semibold)
        nameLabel.textColor = .white
        nameLabel.textAlignment = .center

        subtitleLabel.text = "邀请你语音通话"
        subtitleLabel.font = .systemFont(ofSize: 16)
        subtitleLabel.textColor = UIColor(white: 1, alpha: 0.62)
        subtitleLabel.textAlignment = .center

        durationLabel.font = .monospacedDigitSystemFont(ofSize: 16, weight: .regular)
        durationLabel.textColor = UIColor(white: 1, alpha: 0.62)
        durationLabel.textAlignment = .center
        durationLabel.isHidden = true

        for item in [avatarView, nameLabel, subtitleLabel, durationLabel] {
            item.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview(item)
        }

        configureRound(acceptButton, background: Draw.color(0xff22c55e),
                       symbol: "phone.fill", label: "接听")
        configureRound(declineButton, background: Draw.color(0xffef4444),
                       symbol: "phone.down.fill", label: "拒绝")
        configureRound(hangupButton, background: Draw.color(0xffef4444),
                       symbol: "phone.down.fill", label: "挂断")
        hangupButton.isHidden = true
        acceptButton.addTarget(self, action: #selector(acceptCall), for: .touchUpInside)
        declineButton.addTarget(self, action: #selector(declineCall), for: .touchUpInside)
        hangupButton.addTarget(self, action: #selector(declineCall), for: .touchUpInside)

        NSLayoutConstraint.activate([
            avatarView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 150),
            avatarView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            avatarView.widthAnchor.constraint(equalToConstant: 124),
            avatarView.heightAnchor.constraint(equalToConstant: 124),

            nameLabel.topAnchor.constraint(equalTo: avatarView.bottomAnchor, constant: 28),
            nameLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 32),
            nameLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -32),

            subtitleLabel.topAnchor.constraint(equalTo: nameLabel.bottomAnchor, constant: 10),
            subtitleLabel.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: nameLabel.trailingAnchor),

            durationLabel.topAnchor.constraint(equalTo: subtitleLabel.bottomAnchor, constant: 6),
            durationLabel.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),
            durationLabel.trailingAnchor.constraint(equalTo: nameLabel.trailingAnchor),

            declineButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 54),
            declineButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor,
                                                  constant: -86),
            declineButton.widthAnchor.constraint(equalToConstant: 78),
            declineButton.heightAnchor.constraint(equalToConstant: 78),

            acceptButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -54),
            acceptButton.bottomAnchor.constraint(equalTo: declineButton.bottomAnchor),
            acceptButton.widthAnchor.constraint(equalToConstant: 78),
            acceptButton.heightAnchor.constraint(equalToConstant: 78),

            hangupButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            hangupButton.bottomAnchor.constraint(equalTo: declineButton.bottomAnchor),
            hangupButton.widthAnchor.constraint(equalToConstant: 78),
            hangupButton.heightAnchor.constraint(equalToConstant: 78),
        ])
    }

    private func configureRound(_ button: UIButton, background: UIColor,
                                symbol: String, label: String) {
        button.backgroundColor = background
        button.tintColor = .white
        button.layer.cornerRadius = 39
        button.setImage(UIImage(systemName: symbol), for: .normal)
        button.imageView?.contentMode = .scaleAspectFit
        button.translatesAutoresizingMaskIntoConstraints = false
        button.accessibilityLabel = label
        view.addSubview(button)
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
        acceptButton.isHidden = true
        declineButton.isHidden = true
        hangupButton.isHidden = false
        subtitleLabel.text = "通话中"
        durationLabel.isHidden = false
        durationLabel.text = "00:00"
        seconds = 0
        durationTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            guard let self else { return }
            self.seconds += 1
            self.durationLabel.text = String(format: "%02d:%02d",
                                             self.seconds / 60, self.seconds % 60)
        }
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
