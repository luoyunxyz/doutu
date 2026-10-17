import Foundation

/// DTW/1 会话：握手 + 会话态（sid / 会话密钥 / 序号）。
///
/// 移植自 `account/dtw/DtwSession.java`，两步握手与服务端 `core/dtw.js` 对应：
///
/// ① `hello` → `{salt, salt_id, challenge, kid, proof_iter, expire_at, sig}`
///    客户端**必须**用内置公钥验 `sig`（唯一的信任锚）
/// ② `open`  → 慢哈希证明 + 会话密钥（RSA-OAEP 混合加密）
///    → `{sid, expire_at, sig}`，同样验签并核对 challenge
///
/// 时间片 `bucket = floor(epochSec / 600)`：用片而不是精确时间戳，
/// 否则两边时钟差几百毫秒就永远对不上。
final class DtwSession {
    static let shared = DtwSession()

    private let lock = NSLock()
    private var sid = ""
    private var sessionKey = ""
    private var expireAt: Int64 = 0     // 毫秒
    private var seq: Int32 = 0
    private var blockedUntil: Int64 = 0  // 毫秒
    private(set) var lastError = ""

    private let installKey = "dtw.install_id"

    private init() {}

    // MARK: - 状态

    var hasSession: Bool {
        lock.lock(); defer { lock.unlock() }
        return !sid.isEmpty && !sessionKey.isEmpty && nowMs() < expireAt
    }

    var currentSid: String {
        lock.lock(); defer { lock.unlock() }
        return sid
    }

    var currentKey: String {
        lock.lock(); defer { lock.unlock() }
        return sessionKey
    }

    /// 会话内单调递增序号（服务端据此判断重放窗口）
    func nextSeq() -> Int32 {
        lock.lock(); defer { lock.unlock() }
        seq += 1
        return seq
    }

    func reset() {
        lock.lock(); defer { lock.unlock() }
        sid = ""; sessionKey = ""; expireAt = 0; seq = 0
    }

    // MARK: - 安装标识

    /// 每个安装一份随机标识（换设备/重装即变，服务端拿它做设备维度限流）
    var installId: String {
        if let saved = UserDefaults.standard.string(forKey: installKey), !saved.isEmpty { return saved }
        let fresh = UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
        UserDefaults.standard.set(fresh, forKey: installKey)
        return fresh
    }

    // MARK: - 握手

    /// 确保有可用会话；没有就握一次手。返回失败原因，成功返回 nil。
    func ensure(deviceId: String) -> String? {
        lock.lock(); defer { lock.unlock() }
        if !sid.isEmpty && !sessionKey.isEmpty && nowMs() < expireAt { return nil }
        if nowMs() < blockedUntil {
            return lastError.isEmpty ? "私有协议握手中，请稍后重试" : lastError
        }
        do {
            try handshake(deviceId: deviceId)
            lastError = ""
            blockedUntil = 0
            return nil
        } catch {
            lastError = error.localizedDescription
            // 冷却 15 秒：失败了先别连环撞
            blockedUntil = nowMs() + 15_000
            sid = ""; sessionKey = ""; expireAt = 0; seq = 0
            return lastError
        }
    }

    private func handshake(deviceId: String) throws {
        guard let serverKey = DtwCrypto.publicKey(pem: DtwConfig.publicKeyPEM) else {
            throw DtwError.handshake("内置公钥解析失败")
        }
        let install = installId

        // ① hello
        let helloReq: [String: Any] = [
            "v": DtwConfig.version,
            "step": "hello",
            "device_id": deviceId,
            "install_id": install,
        ]
        let hello = try postJson(path: DtwConfig.handshakePath, body: helloReq, deviceId: deviceId)
        guard let data = hello["data"] as? [String: Any] else {
            throw DtwError.handshake("握手失败：\(hello["msg"] as? String ?? "服务端未返回数据")")
        }
        let challenge = data["challenge"] as? String ?? ""
        let salt = data["salt"] as? String ?? ""
        // kid 是**字符串**（服务端配置里就是 'k1' 这种）；按整数取会变成 0，验签必失败
        let kid = data["kid"] as? String ?? ""
        let saltId = data["salt_id"] as? String ?? ""
        let expireRemote = int64(data["expire_at"])
        guard !challenge.isEmpty, !salt.isEmpty else { throw DtwError.handshake("握手响应不完整") }

        let helloTranscript = transcript(kid: kid, sid: "", challenge: challenge, deviceId: deviceId,
                                        installId: install, saltId: saltId, expireAt: expireRemote)
        guard DtwCrypto.verifyServerSignature(serverKey, payload: helloTranscript,
                                             sigBase64: data["sig"] as? String ?? "") else {
            throw DtwError.handshake("服务端签名校验失败（可能有人在中间换钥匙），已中止")
        }

        // ② open：慢哈希证明 + 随机会话密钥
        let bucket = Int64(Date().timeIntervalSince1970) / DtwConfig.proofBucketSec
        let material = "\(salt)|\(challenge)|\(deviceId)|\(install)|\(bucket)"
        guard let proofBytes = DtwCrypto.pbkdf2Sha256(
            password: Data(material.utf8),
            salt: Data(salt.utf8),
            iterations: DtwConfig.proofIter,
            keyLength: 32
        ) else { throw DtwError.handshake("慢哈希计算失败") }
        let proof = DtwCrypto.hex(proofBytes)

        let candidateKey = DtwCrypto.randomHex(32)   // 32 **字节** → 64 hex（AES-256）
        let hsKey = DtwCrypto.randomHex(32)

        let inner: [String: Any] = [
            "session_key": candidateKey,
            "device_id": deviceId,
            "install_id": install,
            "challenge": challenge,
            "proof": proof,
            "bucket": bucket,
            "ts": nowMs(),
            "app_ver": DtwConfig.appVersion,
        ]
        let innerData = try JSONSerialization.data(withJSONObject: inner, options: [.sortedKeys])
        guard let hsKeyData = DtwCrypto.fromHex(hsKey),
              let sealed = DtwCrypto.seal(keyHex: hsKey, plain: innerData, aad: nil),
              let ek = DtwCrypto.rsaOaepEncrypt(serverKey, hsKeyData) else {
            throw DtwError.handshake("会话密钥封装失败")
        }
        let openReq: [String: Any] = [
            "v": DtwConfig.version,
            "step": "open",
            "ek": DtwCrypto.base64(ek),
            "iv": DtwCrypto.base64(sealed.iv),
            "ct": DtwCrypto.base64(sealed.ct),
            "tag": DtwCrypto.base64(sealed.tag),
        ]
        let open = try postJson(path: DtwConfig.handshakePath, body: openReq, deviceId: deviceId)
        guard let openData = open["data"] as? [String: Any] else {
            throw DtwError.handshake("握手失败：\(open["msg"] as? String ?? "服务端未建立会话")")
        }
        let newSid = openData["sid"] as? String ?? ""
        let sessionExpire = int64(openData["expire_at"])
        guard !newSid.isEmpty else { throw DtwError.handshake("握手未返回会话号") }

        let openTranscript = transcript(kid: kid, sid: newSid, challenge: challenge, deviceId: deviceId,
                                       installId: install, saltId: saltId, expireAt: sessionExpire)
        guard DtwCrypto.verifyServerSignature(serverKey, payload: openTranscript,
                                             sigBase64: openData["sig"] as? String ?? "") else {
            throw DtwError.handshake("会话应答签名校验失败，已中止")
        }

        sid = newSid
        sessionKey = candidateKey
        // 提前 60 秒过期，别卡在边界上
        expireAt = (sessionExpire > 0 ? sessionExpire : nowMs() + 3_600_000) - 60_000
        seq = 0
    }

    /// 与服务端 `transcript()` 的字段顺序必须完全一致
    private func transcript(kid: String, sid: String, challenge: String, deviceId: String,
                            installId: String, saltId: String, expireAt: Int64) -> String {
        "v=\(DtwConfig.version)&kid=\(kid)&sid=\(sid)&challenge=\(challenge)&device=\(deviceId)"
            + "&install=\(installId)&salt=\(saltId)&exp=\(expireAt)"
    }

    /// 握手用的最小 HTTP：只发 JSON、只收 JSON（不走隧道，避免先有鸡还是先有蛋）
    private func postJson(path: String, body: [String: Any], deviceId: String) throws -> [String: Any] {
        guard let url = URL(string: DtwConfig.baseUrl + path) else { throw DtwError.handshake("地址无效") }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 20
        request.setValue("application/json;charset=UTF-8", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(deviceId, forHTTPHeaderField: "X-Device-Id")
        request.setValue(String(nowMs() / 1000), forHTTPHeaderField: "X-Timestamp")
        request.setValue(DtwCrypto.randomHex(16), forHTTPHeaderField: "X-Nonce")
        request.setValue(DtwConfig.appVersion, forHTTPHeaderField: "X-App-Version")
        request.setValue(String(DtwConfig.versionCode), forHTTPHeaderField: "X-Version-Code")
        request.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])

        let (data, response) = try DtwHttp.sync(request)
        guard !data.isEmpty else {
            throw DtwError.handshake("握手无响应（HTTP \(response?.statusCode ?? 0)）")
        }
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw DtwError.handshake("握手响应不是 JSON")
        }
        return json
    }

    private func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    private func int64(_ value: Any?) -> Int64 {
        if let n = value as? NSNumber { return n.int64Value }
        if let s = value as? String, let n = Int64(s) { return n }
        return 0
    }
}

enum DtwError: LocalizedError {
    case handshake(String)
    case transport(String)

    var errorDescription: String? {
        switch self {
        case .handshake(let m), .transport(let m): return m
        }
    }
}
