import Foundation
import CommonCrypto
import CryptoKit
import Security

/// DTW/1 用到的全部密码学原语。
///
/// **必须与 Android 端 `account/dtw/DtwCrypto.java` 逐项对齐**（否则握手/解帧必然失败）：
///
/// | 用途 | Android | iOS（本文件） |
/// |---|---|---|
/// | 慢哈希 | PBKDF2-HMAC-SHA256，key=password，msg=salt‖INT(1) | `CCKeyDerivationPBKDF` |
/// | 握手加密 | RSA-OAEP(SHA-256 / MGF1-SHA256) | `SecKeyCreateEncryptedData(.rsaEncryptionOAEPSHA256)` |
/// | 验签 | SHA256withRSA | `SecKeyVerifySignature(.rsaSignatureMessagePKCS1v15SHA256)` |
/// | 帧加密 | AES-256-GCM，tag 128 位，AAD=帧头 18 字节 | `AES.GCM`（CryptoKit） |
/// | 摘要 | SHA-256 | `SHA256` |
enum DtwCrypto {

    // MARK: - 字节工具

    /// 十六进制（**小写**，与 Node `Buffer.toString('hex')` 一致）
    static func hex(_ data: Data) -> String {
        data.map { String(format: "%02x", $0) }.joined()
    }

    /// 十六进制 → Data（长度奇数或非法字符返回 nil）
    static func fromHex(_ text: String) -> Data? {
        let chars = Array(text.lowercased())
        guard chars.count % 2 == 0 else { return nil }
        var out = Data(capacity: chars.count / 2)
        var i = 0
        while i < chars.count {
            guard let high = chars[i].hexDigitValue, let low = chars[i + 1].hexDigitValue else { return nil }
            out.append(UInt8(high << 4 | low))
            i += 2
        }
        return out
    }

    /// 随机字节（`SecureRandom` 等价物）
    static func randomBytes(_ count: Int) -> Data {
        var data = Data(count: count)
        _ = data.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, count, $0.baseAddress!) }
        return data
    }

    /// 随机十六进制串。注意与 Android 语义一致：**入参是字节数**，输出长度是它的两倍
    static func randomHex(_ bytes: Int) -> String {
        hex(randomBytes(bytes))
    }

    static func base64(_ data: Data) -> String {
        data.base64EncodedString()
    }

    static func unbase64(_ text: String) -> Data? {
        Data(base64Encoded: text, options: [.ignoreUnknownCharacters])
    }

    // MARK: - PBKDF2-HMAC-SHA256

    /// 与服务端 `crypto.pbkdf2Sync(password, salt, iter, 32, 'sha256')` 等价。
    ///
    /// ⚠️ 参数方向不能反：password 是 HMAC 的 key，salt 是被消息的前缀。
    /// 反了会得到一个「看起来正常但永远对不上」的值 —— 这个坑 Android 那边也踩过。
    static func pbkdf2Sha256(password: Data, salt: Data, iterations: Int, keyLength: Int) -> Data? {
        var out = Data(count: keyLength)
        let status = out.withUnsafeMutableBytes { outBytes -> Int32 in
            password.withUnsafeBytes { passBytes in
                salt.withUnsafeBytes { saltBytes in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        passBytes.baseAddress?.assumingMemoryBound(to: Int8.self),
                        password.count,
                        saltBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                        UInt32(iterations),
                        outBytes.baseAddress?.assumingMemoryBound(to: UInt8.self),
                        keyLength
                    )
                }
            }
        }
        return status == kCCSuccess ? out : nil
    }

    // MARK: - RSA

    /// 解析 X.509 SubjectPublicKeyInfo 的 PEM 公钥
    static func publicKey(pem: String) -> SecKey? {
        let body = pem
            .replacingOccurrences(of: "-----BEGIN PUBLIC KEY-----", with: "")
            .replacingOccurrences(of: "-----END PUBLIC KEY-----", with: "")
            .components(separatedBy: .whitespacesAndNewlines)
            .joined()
        guard let der = Data(base64Encoded: body) else { return nil }
        let attrs: [CFString: Any] = [
            kSecAttrKeyType: kSecAttrKeyTypeRSA,
            kSecAttrKeyClass: kSecAttrKeyClassPublic,
            kSecAttrKeySizeInBits: 2048,
        ]
        var error: Unmanaged<CFError>?
        guard let key = SecKeyCreateWithData(der as CFData, attrs as CFDictionary, &error) else { return nil }
        return key
    }

    /// RSA-OAEP(SHA-256) 加密
    static func rsaOaepEncrypt(_ key: SecKey, _ plain: Data) -> Data? {
        var error: Unmanaged<CFError>?
        return SecKeyCreateEncryptedData(key, .rsaEncryptionOAEPSHA256, plain as CFData, &error) as Data?
    }

    /// 校验服务端私钥签名（SHA256withRSA，base64）
    static func verifyServerSignature(_ key: SecKey, payload: String, sigBase64: String) -> Bool {
        guard let sig = unbase64(sigBase64), let data = payload.data(using: .utf8) else { return false }
        var error: Unmanaged<CFError>?
        return SecKeyVerifySignature(key, .rsaSignatureMessagePKCS1v15SHA256,
                                     data as CFData, sig as CFData, &error)
    }

    // MARK: - AES-256-GCM

    /// 密封：返回 `(iv 12 字节, 密文, tag 16 字节)` —— 与 Android `seal()` 的返回结构一致
    static func seal(keyHex: String, plain: Data, aad: Data?) -> (iv: Data, ct: Data, tag: Data)? {
        guard let keyData = fromHex(keyHex), keyData.count == 32 else { return nil }
        guard let key = try? SymmetricKey(data: keyData) else { return nil }
        let nonceLength = 12
        let nonce = try? AES.GCM.Nonce(data: randomBytes(nonceLength))
        guard let nonce else { return nil }
        let aadData = aad ?? Data()
        guard let sealed = try? AES.GCM.seal(plain, using: key, nonce: nonce, authenticating: aadData) else {
            return nil
        }
        return (Data(nonce), sealed.ciphertext, sealed.tag)
    }

    /// 拆封（iv/ct/tag 分开传，AAD 必须与加密时完全一致，否则认证失败）
    static func open(keyHex: String, iv: Data, ct: Data, tag: Data, aad: Data?) -> Data? {
        guard let keyData = fromHex(keyHex), keyData.count == 32 else { return nil }
        guard let key = try? SymmetricKey(data: keyData) else { return nil }
        guard let nonce = try? AES.GCM.Nonce(data: iv) else { return nil }
        let box = try? AES.GCM.SealedBox(nonce: nonce, ciphertext: ct, tag: tag)
        guard let box else { return nil }
        return try? AES.GCM.open(box, using: key, authenticating: aad ?? Data())
    }

    // MARK: - 摘要

    static func sha256(_ data: Data) -> Data {
        Data(SHA256.hash(data: data))
    }

    static func sha256Hex(_ data: Data) -> String {
        hex(sha256(data))
    }
}
