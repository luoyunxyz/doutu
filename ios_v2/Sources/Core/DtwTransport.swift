import Foundation

/// 极简 HTTP 帮助器（同步版给握手用，异步版给业务用）。
///
/// 为什么需要一个同步版：握手是在 `DtwSession.ensure()` 里同步完成的（对应 Android 的
/// 阻塞式 HttpURLConnection），调用方拿到结果才决定后续；用信号量桥接是最直白的写法。
/// 注意：**不要在主线程调用 `sync`**（会阻塞 UI）。
enum DtwHttp {
    struct Response {
        let statusCode: Int
        let data: Data
    }

    /// 第一次请求前注册 tdapi 域名的内部解析器（见 TdapiResolver.swift）。
    private static let resolverReady: Void = {
        TdapiResolver.register()
    }()

    /// 显式把 TdapiResolver 放进 protocolClasses，确保 URLSession 一定走它。
    private static let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.protocolClasses = [TdapiResolver.self] + (config.protocolClasses ?? [])
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        return URLSession(configuration: config)
    }()

    /// 同步请求（仅用于握手，超时 20/30 秒）
    ///
    /// 用一个小 class 装结果，而不是在逃逸闭包里改捕获的 `var` —— 后者在新版工具链里会报
    /// 「mutation of captured var in concurrently-executing code」，是编译**错误**而非警告。
    private final class Box {
        var result: Result<(Data, Response?), Error>?
    }

    static func sync(_ request: URLRequest) throws -> (Data, Response?) {
        _ = resolverReady
        let box = Box()
        let sem = DispatchSemaphore(value: 0)
        session.dataTask(with: request) { data, response, error in
            if let error {
                box.result = .failure(error)
            } else {
                let http = response as? HTTPURLResponse
                box.result = .success((data ?? Data(), http.map {
                    Response(statusCode: $0.statusCode, data: data ?? Data())
                }))
            }
            sem.signal()
        }.resume()
        sem.wait()
        guard let outcome = box.result else { throw DtwError.transport("请求未完成") }
        switch outcome {
        case .success(let value): return value
        case .failure(let error): throw error
        }
    }

    /// 异步请求
    ///
    /// ⚠️ 不要用 `URLSession.shared.data(for:)` —— 那是 **iOS 15 起**才有的 API，
    /// 而这个 App 要支持到 iOS 14.3（用户那台 iPhone 12 停在 14.3，那样才能用 TrollStore 永久装）。
    /// `async/await` 本身从 iOS 13 起可用（Xcode 13.2+ 会把并发运行时回带到 App 里），
    /// 所以这里用 `withCheckedThrowingContinuation` 把老的 `dataTask` 包成 async 即可。
    static func async(_ request: URLRequest) async throws -> (Data, Int) {
        let (data, response) = try await data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        return (data, status)
    }

    /// `data(for:)` 的 iOS 13+ 版本（语义与 `URLSession.data(for:)` 对齐：
    /// 非 2xx 也照常返回数据，由调用方看 statusCode）
    private static func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        _ = resolverReady
        return try await withCheckedThrowingContinuation { continuation in
            session.dataTask(with: request) { data, response, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let data, let response {
                    continuation.resume(returning: (data, response))
                } else {
                    continuation.resume(throwing: DtwError.transport("响应为空"))
                }
            }.resume()
        }
    }
}

/// DTW/1 隧道：组帧 / 拆帧 / 发请求。
///
/// 帧格式（与服务端 `core/dtw.js`、Android `DtwTransport.java` 完全一致）：
/// ```
/// 偏移  长度  字段
/// 0     4     magic "DTW1"
/// 4     1     ver 0x01
/// 5     1     flags 0x00
/// 6     8     sid（会话号十六进制转成的原始字节，不足补 0）
/// 14    4     seq（大端）
/// 18    12    iv
/// 30    n     ciphertext
/// 30+n  16    tag
/// ```
/// AAD = 前 18 字节（magic..seq），所以帧头本身也不可篡改。
enum DtwTransport {
    static let magic: [UInt8] = Array("DTW1".utf8)
    static let headerLength = 18
    static let tagLength = 16
    static let ivLength = 12

    /// 隧道响应：业务状态码与 Content-Type 都封在帧里
    struct Reply {
        let httpStatus: Int
        let status: Int
        let contentType: String
        let body: String
    }

    // MARK: - 组帧

    static func encodeFrame(sid: String, seq: Int32, keyHex: String, payload: Data) throws -> Data {
        var header = Data(capacity: headerLength)
        header.append(contentsOf: magic)                    // 0..3
        header.append(UInt8(DtwConfig.version))             // 4
        header.append(0)                                    // 5 flags
        let sidRaw = DtwCrypto.fromHex(sid) ?? Data()
        var sidBytes = Data(count: 8)                       // 6..13，不足补 0
        for (i, byte) in sidRaw.prefix(8).enumerated() { sidBytes[i] = byte }
        header.append(sidBytes)
        var beSeq = seq.bigEndian                          // 14..17
        withUnsafeBytes(of: &beSeq) { header.append(contentsOf: $0) }
        precondition(header.count == headerLength)

        guard let sealed = DtwCrypto.seal(keyHex: keyHex, plain: payload, aad: header) else {
            throw DtwError.transport("帧加密失败")
        }
        var frame = Data()
        frame.append(header)
        frame.append(sealed.iv)
        frame.append(sealed.ct)
        frame.append(sealed.tag)
        return frame
    }

    // MARK: - 拆帧

    static func decodeFrame(_ frame: Data, keyHex: String) throws -> Reply {
        guard frame.count >= headerLength + ivLength + tagLength else {
            throw DtwError.transport("响应帧太短")
        }
        guard Array(frame.prefix(4)) == magic else { throw DtwError.transport("响应帧 magic 不对") }
        let header = frame.prefix(headerLength)
        let iv = frame.subdata(in: headerLength ..< (headerLength + ivLength))
        let tagStart = frame.count - tagLength
        let ct = frame.subdata(in: (headerLength + ivLength) ..< tagStart)
        let tag = frame.subdata(in: tagStart ..< frame.count)

        guard let plain = DtwCrypto.open(keyHex: keyHex, iv: iv, ct: ct, tag: tag, aad: header) else {
            throw DtwError.transport("响应解密失败（会话密钥不对或数据被改）")
        }
        guard let json = try? JSONSerialization.jsonObject(with: plain) as? [String: Any] else {
            throw DtwError.transport("响应载荷不是 JSON")
        }
        let status = (json["s"] as? NSNumber)?.intValue ?? 0
        let contentType = json["ct"] as? String ?? "application/json; charset=utf-8"
        let bodyBase64 = json["b"] as? String ?? ""
        let body = DtwCrypto.unbase64(bodyBase64).flatMap { String(data: $0, encoding: .utf8) } ?? ""
        return Reply(httpStatus: 200, status: status, contentType: contentType, body: body)
    }

    // MARK: - 发请求

    /// 发一个隧道请求。`headers` 是业务层要用的头（时间戳/nonce/签名/token），原样塞进载荷的 h。
    static func send(deviceId: String,
                     method: String,
                     path: String,
                     query: [String: String],
                     rawBody: String?,
                     bodyObject: [String: Any]?,
                     headers: [String: String]) async throws -> Reply {
        if let err = DtwSession.shared.ensure(deviceId: deviceId) {
            throw DtwError.transport(err)
        }

        var payload: [String: Any] = [
            "m": method,
            "p": path,
            "q": query,
            "t": Int64(Date().timeIntervalSince1970 * 1000),
        ]
        // ⚠️ 只发一份 body：`rb` 是**签名时用的那个原始串**，服务端按它算 body_hash 并解析业务体。
        // 早先 Android 端 `b`/`rb` 都塞，等于每个请求 body 发两遍 —— 大请求会直接冲破服务端 2MB 上限。
        if let rawBody, !rawBody.isEmpty {
            payload["rb"] = rawBody
        } else if let bodyObject {
            payload["b"] = bodyObject
        }
        payload["h"] = headers

        let payloadData = try JSONSerialization.data(withJSONObject: payload, options: [])
        let seq = DtwSession.shared.nextSeq()
        let frame = try encodeFrame(sid: DtwSession.shared.currentSid, seq: seq,
                                    keyHex: DtwSession.shared.currentKey, payload: payloadData)

        guard let url = URL(string: DtwConfig.baseUrl + DtwConfig.tunnelPath) else {
            throw DtwError.transport("隧道地址无效")
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 30
        request.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        request.httpBody = frame

        let (data, status) = try await DtwHttp.async(request)
        guard status == 200 else {
            // 隧道层自己报错时返回的是 JSON 信封，不是帧
            let text = String(data: data, encoding: .utf8) ?? ""
            throw DtwError.transport("隧道被拒（HTTP \(status)）：\(text.prefix(200))")
        }
        return try decodeFrame(data, keyHex: DtwSession.shared.currentKey)
    }
}
