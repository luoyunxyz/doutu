import Foundation

/// DTW/1 的客户端配置 —— 与服务端 `.env` 必须成对。
///
/// 对应 Android 的 `account/dtw/DtwConfig.java`。这些值在 Android 端是构建时注入
/// （`-PdtwPublicKeyFile` / `-PdtwSalt` / `-PdoutuApi`），iOS 这边直接写常量；
/// **换服务端域名或轮换公钥时必须同步改这里**，否则握手会失败。
enum DtwConfig {
    /// 生产服务端地址（与 Android 构建参数 `-PdoutuApi` 一致）
    /// 生产服务端地址：所有客户端/服务端交互统一走这个域名。
    /// 域名必须解析到 69.165.75.130；如果本地 DNS 把它解析到 127.0.0.1，
    /// 需要修路由器/手机 DNS，而不是在 App 里硬编码 IP。
    static let baseUrl = "https://tdapi.luoyun.xyz"

    /// 协议版本（帧头 ver 字段）
    static let version = 1

    /// 隧道与握手路径（服务端 `DTW_TUNNEL_PATH` / `DTW_HANDSHAKE_PATH`）
    static let tunnelPath = "/api/v1/dtw"
    static let handshakePath = "/api/v1/dtw/hs"

    /// 服务端公钥（PEM）。这是**唯一的信任锚**：握手应答必须用它验签，
    /// 不验的话中间人换掉盐/公钥就能把整套协议架空。
    /// 与 `_ops/dtw-public.pem` 同源。
    static let publicKeyPEM = """
    -----BEGIN PUBLIC KEY-----
    MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzHubijhtQRickqOZNcyY
    l+8nAByna6J52mXXMO0NLSg4kfEqaDnlmuD12BFLLCX23/8e+CbAaah2Li0JfAYL
    37Hnp5Yd2eDsHkjKza9Zmqn9bHBc5MCAb3n0vcIjrtfWjqnhJ8VegUSI/FdpYlzF
    dXcEYLH+Fm0Em/BDwDsHm0b26oPTEMkc1368nbZRiUluhGNTynBLXWZp/F9aR9q0
    IStEXtjALYWs1fBJ3LzIgpV3uA5GfdXkTikH7gFL9Pirp+U6jYbpL1ONh3GSkr2O
    hmR1SZOQryskSYABbT5wBfpYXCEHUaXoaflKNStrqTzevZOVl77BSeeWhdVscvw2
    ZwIDAQAB
    -----END PUBLIC KEY-----
    """

    /// 慢哈希迭代次数（服务端 `DTW_PROOF_ITER`，默认 20000；**两端必须一致**）
    static let proofIter = 20000

    /// 时间片宽度（服务端 `PROOF_BUCKET_SEC`，硬编码 600 秒）
    static let proofBucketSec: Int64 = 600

    /// 时间窗（服务端 `DTW_TS_WINDOW`，秒）
    static let tsWindow: Int64 = 300

    /// App 版本（上报用；与后台版本号规则一致：major*10000+minor*100+patch）
    static let appVersion = "1.0.6"
    static let versionCode = 10006

    /// 业务请求的默认请求头（与服务端 `clientAuth` 的期望一致）
    static func platform() -> String { "ios" }
}
