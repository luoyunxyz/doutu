import Foundation

/// 只对业务域名做**内部**解析：URL、Host、TLS 证书仍然是
/// `tdapi.luoyun.xyz`，但 socket 连到服务器公网 IP。
///
/// 背景：部分路由器/本地 DNS 会把 `tdapi.luoyun.xyz` 劫持成 `127.0.0.1`，
/// 请求会打到手机自己。权威 DNS 已经是 `69.165.75.130`，服务器无法改变
/// 客户端解析结果，所以只能在 App 内绕过系统解析。
///
/// 服务器 IP 变了要同步改 `apiIP`。
final class TdapiResolver: URLProtocol, URLSessionDelegate {
    static let apiHost = "tdapi.luoyun.xyz"
    static let apiIP = "69.165.75.130"

    private static var registered = false

    /// 在第一次网络请求前调用；只注册一次。
    static func register() {
        guard !registered else { return }
        registered = true
        URLProtocol.registerClass(TdapiResolver.self)
    }

    override class func canInit(with request: URLRequest) -> Bool {
        guard let host = request.url?.host?.lowercased() else { return false }
        return host == apiHost
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    private var dataTask: URLSessionDataTask?
    private var session: URLSession?

    override func startLoading() {
        guard let url = request.url,
              var comps = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            client?.urlProtocol(self, didFailWithError: URLError(.badURL))
            return
        }
        comps.host = Self.apiIP
        guard let ipURL = comps.url else {
            client?.urlProtocol(self, didFailWithError: URLError(.badURL))
            return
        }
        var newRequest = request
        newRequest.url = ipURL
        // 服务器 nginx 以 Host / server_name 分流；显式带上原域名。
        newRequest.setValue(Self.apiHost, forHTTPHeaderField: "Host")

        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = request.timeoutInterval > 0 ? request.timeoutInterval : 30
        config.timeoutIntervalForResource = 60
        let session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
        self.session = session

        // 目标主机是 IP，canInit 只认 tdapi 域名，不会递归。
        let task = session.dataTask(with: newRequest) { [weak self] data, response, error in
            guard let self else { return }
            if let error {
                self.client?.urlProtocol(self, didFailWithError: error)
                return
            }
            if let response {
                self.client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            }
            if let data {
                self.client?.urlProtocol(self, didLoad: data)
            }
            self.client?.urlProtocolDidFinishLoading(self)
        }
        self.dataTask = task
        task.resume()
    }

    override func stopLoading() {
        dataTask?.cancel()
    }

    /// 证书是 tdapi.luoyun.xyz 的，IP 直连时域名不匹配；证书链仍由系统校验，
    /// 这里只放行主机名。DTW 握手还会用内置公钥验签。
    func urlSession(_ session: URLSession,
                    didReceive challenge: URLAuthenticationChallenge,
                    completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
           let trust = challenge.protectionSpace.serverTrust {
            completionHandler(.useCredential, URLCredential(trust: trust))
        } else {
            completionHandler(.performDefaultHandling, nil)
        }
    }
}
