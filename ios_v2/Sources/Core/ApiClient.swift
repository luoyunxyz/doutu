import Foundation
import CryptoKit
import Security
import UIKit

/// 登录态（token / signKey / 设备号），存 Keychain。
///
/// 对应 Android 的 `account/Session.java`。`signKey` 是**登录时服务端下发的会话密钥**，
/// 用来给业务请求算 `x-sign`（HMAC-SHA256），不是服务端的 MASTER_KEY —— 客户端已经不再持有长期密钥。
enum Keychain {
    private static let service = "com.luoyun.doutu.session"

    static func set(_ value: String, for key: String) {
        guard let data = value.data(using: .utf8) else { return }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(add as CFDictionary, nil)
    }

    static func get(_ key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func remove(_ key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

/// 应用会话：token / 资料 / 签名密钥 / 设备号。
final class Session {
    static let shared = Session()
    private init() {}

    private let tokenKey = "x-token"
    private let signKeyKey = "x-sign-key"
    private let deviceKey = "device_id"

    var token: String {
        get { Keychain.get(tokenKey) ?? "" }
        set { setOrRemove(newValue, tokenKey) }
    }

    var signKey: String {
        get { Keychain.get(signKeyKey) ?? "" }
        set { setOrRemove(newValue, signKeyKey) }
    }

    var isLoggedIn: Bool { !token.isEmpty }

    /// 新安装默认必须是未登录。
    /// iOS 的 Keychain 在删除 App 后仍会保留，所以「新装」可能继承上一次安装的
    /// x-token，启动后看起来像已经登录。UserDefaults 会随卸载清空，用
    /// `terms.accepted` 判断是否首次启动；首次启动时清掉 Keychain 里的会话。
    func prepareFreshInstall() {
        if !UserDefaults.standard.bool(forKey: "terms.accepted") {
            token = ""
            signKey = ""
        }
    }

    /// 昵称（Android 的 `Session.displayName()`）：优先昵称，其次用户名，都没有就空
    var displayName: String {
        let nick = UserDefaults.standard.string(forKey: "profile.nickname") ?? ""
        return nick.isEmpty ? (UserDefaults.standard.string(forKey: "profile.name") ?? "") : nick
    }

    /// 是否已开通会员（决定界面水印与会员卡文案）
    var isActivated: Bool {
        UserDefaults.standard.bool(forKey: "profile.activated")
    }

    /// 会员到期日（`yyyy-MM-dd`，未开通为空）
    var expireAt: String {
        UserDefaults.standard.string(forKey: "profile.expire_at") ?? ""
    }

    /// 未开通时会员卡上那行提示（服务端 `activation_notice`，可配）
    var overlayText: String {
        let notice = UserDefaults.standard.string(forKey: "config.activation_notice") ?? ""
        return notice.isEmpty ? "未开通会员，可直接使用（成品带水印）" : notice
    }

    /// 把登录结果写进会话（登录/资料接口回来时调用）
    func applyLogin(token: String, signKey: String, name: String?, nickname: String?,
                    activated: Bool, expireAt: String?) {
        self.token = token
        self.signKey = signKey
        if let name { UserDefaults.standard.set(name, forKey: "profile.name") }
        if let nickname { UserDefaults.standard.set(nickname, forKey: "profile.nickname") }
        UserDefaults.standard.set(activated, forKey: "profile.activated")
        if let expireAt { UserDefaults.standard.set(expireAt, forKey: "profile.expire_at") }
    }

    /// 设备号：安装内稳定（服务端按它做限流与"同设备顶号"）
    var deviceId: String {
        if let saved = Keychain.get(deviceKey), !saved.isEmpty { return saved }
        // 与服务端 / DtwSession 的 install_id 同样取 32 位小写十六进制
        let fresh = DtwCrypto.randomHex(16)
        Keychain.set(fresh, for: deviceKey)
        return fresh
    }

    func clear() {
        Keychain.remove(tokenKey)
        Keychain.remove(signKeyKey)
        DtwSession.shared.reset()
    }

    private func setOrRemove(_ value: String, _ key: String) {
        if value.isEmpty { Keychain.remove(key) } else { Keychain.set(value, for: key) }
    }
}

/// 业务接口客户端 —— 对齐 Android `account/DoutuApi.java` 的请求构造。
///
/// 每个请求都走 DTW 隧道，业务头（时间戳/nonce/签名/token）塞进加密载荷的 `h` 字段：
/// ```
/// x-device-id / x-timestamp / x-nonce / x-app-version / x-version-code
/// x-token      登录后带
/// x-sign       HMAC-SHA256(signKey, "ts\nnonce\ndev\npath\nsha256hex(rawBody)")
/// ```
/// 隧道本身已由会话密钥认证，所以 root 类引导接口不再签名（客户端没有 MASTER_KEY 了）。
final class ApiClient {
    static let shared = ApiClient()
    private init() {}

    /// 服务端统一信封
    struct Envelope {
        let code: Int
        let msg: String
        let data: Any?
        var ok: Bool { code == 0 }
    }

    /// 发请求
    /// - Parameters:
    ///   - body: 业务体（会同时作为签名原文）
    ///   - root: 引导类接口（登录/注册/配置），不带 x-sign
    func call(_ method: String,
              _ path: String,
              query: [String: String] = [:],
              body: [String: Any]? = nil,
              root: Bool = false) async -> Envelope {
        let raw = body.flatMap { try? JSONSerialization.data(withJSONObject: $0, options: []) }
            .flatMap { String(data: $0, encoding: .utf8) } ?? ""
        let ts = Int64(Date().timeIntervalSince1970)
        let nonce = DtwCrypto.randomHex(16)
        let device = Session.shared.deviceId

        var headers: [String: String] = [
            "x-device-id": device,
            "x-timestamp": String(ts),
            "x-nonce": nonce,
            "x-app-version": DtwConfig.appVersion,
            "x-version-code": String(DtwConfig.versionCode),
            "x-platform": DtwConfig.platform(),
        ]
        if Session.shared.isLoggedIn {
            headers["x-token"] = Session.shared.token
        }
        if !root {
            let bodyHash = DtwCrypto.sha256Hex(Data(raw.utf8))
            let payload = "\(ts)\n\(nonce)\n\(device)\n\(path)\n\(bodyHash)"
            if let sign = Self.hmacHex(key: Session.shared.signKey, message: payload) {
                headers["x-sign"] = sign
            }
        }

        do {
            let reply = try await DtwTransport.send(
                deviceId: device,
                method: method,
                path: path,
                query: query,
                rawBody: raw.isEmpty ? nil : raw,
                bodyObject: raw.isEmpty ? nil : body,
                headers: headers
            )
            return Self.parse(reply.body, fallback: reply.status)
        } catch {
            // 握手失败、帧被拒、DNS/超时都会走到这里
            NSLog("[DoutuApi] 请求失败 \(method) \(path) → \(error.localizedDescription)")
            return Envelope(code: -1, msg: "网络异常：\(error.localizedDescription)", data: nil)
        }
    }

    /// 解析服务端信封 `{code,msg,data,ts,sign}`
    static func parse(_ text: String, fallback: Int) -> Envelope {
        guard !text.isEmpty, let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return Envelope(code: fallback, msg: "", data: nil)
        }
        let code = (json["code"] as? NSNumber)?.intValue ?? fallback
        let msg = json["msg"] as? String ?? ""
        return Envelope(code: code, msg: msg, data: json["data"])
    }

    /// HMAC-SHA256（小写 hex）—— 与服务端 `crypto.createHmac('sha256', key).update(payload).digest('hex')` 一致
    ///
    /// 用 CryptoKit 而不是 CommonCrypto：`import CommonCrypto` 在没有 bridging header
    /// 的工程里不是可用的 Swift 模块（会报 `no such module 'CommonCrypto'`），
    /// 而 CryptoKit 是系统模块、结果完全等价。
    static func hmacHex(key: String, message: String) -> String? {
        guard let keyData = DtwCrypto.fromHex(key) ?? key.data(using: .utf8) else { return nil }
        let mac = HMAC<SHA256>.authenticationCode(
            for: Data(message.utf8), using: SymmetricKey(data: keyData))
        return DtwCrypto.hex(Data(mac))
    }
}
