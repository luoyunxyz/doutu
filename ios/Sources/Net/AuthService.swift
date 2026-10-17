import Foundation
import UIKit

/// 账号接口 —— 对应 Android `DoutuApi` 里的认证相关方法。
///
/// 这些接口都是 **root 类**（不需要登录签名），因为走隧道时服务端已用会话密钥认证过：
///   · `POST /api/v1/auth/register`      {username, invite_code, phone, password, password_confirm}
///   · `POST /api/v1/auth/login`         {username, password}
///   · `POST /api/v1/auth/invite/verify` {phone, invite_code} → reset_token（10 分钟有效）
///   · `POST /api/v1/auth/invite/reset`  {reset_token, password, password_confirm}
///   · `POST /api/v1/auth/profile`       {username}（改用户名，需登录）
///   · `POST /api/v1/auth/avatar`        {image_base64}（换头像，需登录）
///
/// 登录成功的响应里带 `token` 与 `sign_key`（后者用来给业务请求算 `x-sign`），
/// 与 Android `LoginActivity.onAuthResult` 的处理完全一致。
enum AuthService {

    /// 统一的返回：成功时带上提示语，失败时带服务端的 msg
    struct Outcome {
        let ok: Bool
        let message: String
    }

    // MARK: - 登录

    static func login(account: String, password: String) async -> Outcome {
        let res = await ApiClient.shared.call("POST", "/api/v1/auth/login",
                                             body: ["username": account, "password": password],
                                             root: true)
        guard res.ok, let data = res.data as? [String: Any] else {
            return Outcome(ok: false, message: res.msg.isEmpty ? "登录失败" : res.msg)
        }
        let token = data["token"] as? String ?? ""
        let signKey = data["sign_key"] as? String ?? ""
        guard !token.isEmpty else { return Outcome(ok: false, message: "服务端未返回登录凭证") }

        let user = data["user"] as? [String: Any] ?? [:]
        let phone = user["phone"] as? String ?? ""
        let name = user["username"] as? String ?? account
        Session.shared.applyLogin(
            token: token, signKey: signKey,
            name: name, nickname: user["nickname"] as? String,
            activated: (user["activated"] as? Bool) ?? ((user["status"] as? Int) == 1),
            expireAt: user["expire_at"] as? String
        )
        ProfileStore.shared.name = name
        ProfileStore.shared.phone = phone
        return Outcome(ok: true, message: res.msg.isEmpty ? "登录成功" : res.msg)
    }

    // MARK: - 邀请码注册

    static func register(username: String, inviteCode: String, phone: String,
                         password: String, confirm: String) async -> Outcome {
        // 客户端先做一遍一致性检查，省一次往返
        if password != confirm { return Outcome(ok: false, message: "两次输入的密码不一致") }
        let res = await ApiClient.shared.call("POST", "/api/v1/auth/register", body: [
            "username": username, "invite_code": inviteCode, "phone": phone,
            "password": password, "password_confirm": confirm,
        ], root: true)
        guard res.ok, let data = res.data as? [String: Any] else {
            return Outcome(ok: false, message: res.msg.isEmpty ? "注册失败" : res.msg)
        }
        // 注册成功直接登录（与 Android 一致：注册接口也返回 token/sign_key）
        let token = data["token"] as? String ?? ""
        let signKey = data["sign_key"] as? String ?? ""
        if !token.isEmpty {
            Session.shared.applyLogin(token: token, signKey: signKey, name: username,
                                     nickname: nil, activated: false, expireAt: nil)
            ProfileStore.shared.name = username
            ProfileStore.shared.phone = phone
        }
        return Outcome(ok: true, message: res.msg.isEmpty ? "注册成功" : res.msg)
    }

    // MARK: - 邀请码找回密码（两步）

    /// 第一步：核验「注册手机号 + 邀请码」，通过后拿到一次性票据
    static func verifyInviteForReset(phone: String, inviteCode: String) async -> (Outcome, String) {
        let res = await ApiClient.shared.call("POST", "/api/v1/auth/invite/verify",
                                             body: ["phone": phone, "invite_code": inviteCode],
                                             root: true)
        guard res.ok, let data = res.data as? [String: Any] else {
            return (Outcome(ok: false, message: res.msg.isEmpty ? "核验失败" : res.msg), "")
        }
        let ticket = data["reset_token"] as? String ?? ""
        return (Outcome(ok: true, message: res.msg.isEmpty ? "核验通过" : res.msg), ticket)
    }

    /// 第二步：用票据换新密码（服务端会把旧会话全部吊销）
    static func resetPassword(ticket: String, password: String, confirm: String) async -> Outcome {
        if password != confirm { return Outcome(ok: false, message: "两次输入的密码不一致") }
        let res = await ApiClient.shared.call("POST", "/api/v1/auth/invite/reset", body: [
            "reset_token": ticket, "password": password, "password_confirm": confirm,
        ], root: true)
        return Outcome(ok: res.ok, message: res.msg.isEmpty ? (res.ok ? "密码已重置" : "重置失败") : res.msg)
    }

    // MARK: - 资料（需登录）

    static func changeUsername(_ username: String) async -> Outcome {
        let res = await ApiClient.shared.call("POST", "/api/v1/auth/profile",
                                             body: ["username": username])
        if res.ok {
            ProfileStore.shared.name = username
            UserDefaults.standard.set(username, forKey: "profile.name")
        }
        return Outcome(ok: res.ok, message: res.msg.isEmpty ? (res.ok ? "已更新" : "更新失败") : res.msg)
    }

    /// 换头像：传 320px 的 JPEG base64（与服务端约定的尺寸一致）
    static func uploadAvatar(_ image: UIImage) async -> Outcome {
        guard let base64 = AvatarCompressor.jpegBase64(image) else {
            return Outcome(ok: false, message: "图片处理失败")
        }
        let res = await ApiClient.shared.call("POST", "/api/v1/auth/avatar",
                                             body: ["image_base64": base64])
        if res.ok {
            ProfileStore.shared.avatar = image
        }
        return Outcome(ok: res.ok, message: res.msg.isEmpty ? (res.ok ? "头像已更新" : "上传失败") : res.msg)
    }

    static func logout() async -> Outcome {
        // ⚠️ 不能传 `root: true`：服务端 `/auth/logout` 挂的是
        // `clientAuth({ mode: 'session' })`，那条路径会 `checkSignature(session.sign_key)`
        // —— 也就是**必须带 x-sign**。之前写成 root 会被拒「缺少签名」。
        // 这里登录态还在，`call` 会自动带 x-token 并用 signKey 签名。
        let res = await ApiClient.shared.call("POST", "/api/v1/auth/logout")
        Session.shared.clear()
        ProfileStore.shared.avatar = nil
        return Outcome(ok: res.ok, message: res.msg.isEmpty ? "已退出登录" : res.msg)
    }
}

/// 头像压缩：与 Android `AvatarUpload` 一致，缩到 320px、JPEG 质量 0.85
enum AvatarCompressor {
    static func jpegBase64(_ image: UIImage) -> String? {
        let maxSide: CGFloat = 320
        let size = image.size
        guard size.width > 0, size.height > 0 else { return nil }
        let scale = min(1, maxSide / max(size.width, size.height))
        let target = CGSize(width: size.width * scale, height: size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: target)
        let scaled = renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: target))
        }
        guard let data = scaled.jpegData(compressionQuality: 0.85) else { return nil }
        return data.base64EncodedString()
    }
}
