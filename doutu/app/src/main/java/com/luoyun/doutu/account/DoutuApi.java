package com.luoyun.doutu.account;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONObject;

import com.luoyun.doutu.BuildConfig;
import com.luoyun.doutu.account.dtw.DtwTransport;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 服务端 API 客户端：负责请求签名、下发会话密钥与状态同步。
 *
 * <b>2026-10-17 起走 DTW/1 私有协议</b>：所有接口都封成 AES-256-GCM 帧发到隧道端点
 * （见 {@code account/dtw/}），响应也是密文。两条重要变化：
 *
 *   1. **MASTER_KEY 不再进客户端**。以前它编译在 BuildConfig 里，反编译就能伪造官方
 *      客户端、铸造成品下载签名、推导会话密钥。现在客户端只有服务端**公钥**（可公开），
 *      靠握手换一把临时会话密钥；引导接口也不再需要 root 签名。
 *   2. 业务签名的公式没变（下面这段仍然保留）：会话接口依旧用 sign_key 签，
 *      服务端也照旧验 —— 协议层只是多套了一层保密与防重放。
 *
 * 签名算法与服务端严格一致（正式版应把这段逻辑放进 libdwcore.so，让 Java 侧无法自行计算）：
 *   body_hash = sha256hex(原始请求体)            // GET 无请求体时是 sha256hex("")
 *   sign      = hmac_sha256_hex(key, ts \n nonce \n device_id \n path \n body_hash)
 *
 * 响应也会验签：服务端把 sign 放在 JSON 最后一个字段，这里按同样方式重算比对。
 * （走隧道时响应本身就是 AEAD 认证过的，服务端不再额外签名，这里会跳过验签。）
 */
public final class DoutuApi {

    private static final String APP_VERSION = "1.0.0";
    private static final int VERSION_CODE = 1;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static String deviceId;

    private DoutuApi() {
    }

    /** 接口结果：code=0 表示成功，msg 可直接展示给用户。 */
    public static final class Result {
        public final int code;
        public final String msg;
        public final JSONObject data;
        /** 响应验签是否通过（false 说明报文被改过或密钥不一致）。 */
        public final boolean signVerified;

        Result(int code, String msg, JSONObject data, boolean signVerified) {
            this.code = code;
            this.msg = msg;
            this.data = data;
            this.signVerified = signVerified;
        }

        public boolean ok() {
            return code == 0;
        }

        /**
         * 取用户信息。
         * 注意两种信封形状：register/login 把用户放在 {@code data.user}，
         * 而 /auth/status 的用户字段是**平铺在 data 上**的 —— 这里统一处理，
         * 否则本地 activated 永远不更新，水印会一直挂着。
         */
        public JSONObject user() {
            if (data == null) return null;
            JSONObject nested = data.optJSONObject("user");
            return nested != null ? nested : data;
        }

        @Override
        public String toString() {
            return "Result{code=" + code + ", msg=" + msg + ", signVerified=" + signVerified + "}";
        }
    }

    public interface Callback {
        void onResult(Result result);
    }

    /* ------------------------------ 对外接口 ------------------------------ */

    /** 邀请码注册：用户名 + 邀请码 + 手机号 + 两次密码（一个邀请码只能创建一个账号）。 */
    public static void register(Context ctx, String username, String inviteCode, String phone,
                                String password, String confirm, Callback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("username", username);
            body.put("invite_code", inviteCode);
            body.put("phone", phone);
            body.put("password", password);
            body.put("password_confirm", confirm);
            call(ctx, "POST", "/api/v1/auth/register", body, true, cb);
        } catch (Exception e) {
            fail(cb, e);
        }
    }

    /**
     * 邀请码找回密码第一步：核验「注册手机号 + 邀请码」。
     *
     * 一个邀请码只能注册一个账号，服务端靠 invite_code.used_by / used_phone 认人；
     * 通过以后回一张 10 分钟有效的一次性票据（reset_token），下一步改密只认票据。
     */
    public static void verifyInviteForReset(Context ctx, String phone, String inviteCode, Callback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("phone", phone);
            body.put("invite_code", inviteCode);
            call(ctx, "POST", "/api/v1/auth/invite/verify", body, true, cb);
        } catch (Exception e) {
            fail(cb, e);
        }
    }

    /**
     * 邀请码找回密码第二步：拿票据换新密码。
     *
     * 服务端换完会把旧会话全部吊销 —— 旧密码从此登不上，已经登录的地方也要重新登录。
     */
    public static void resetPasswordByInvite(Context ctx, String resetToken, String password,
                                             String confirm, Callback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("reset_token", resetToken);
            body.put("password", password);
            body.put("password_confirm", confirm);
            call(ctx, "POST", "/api/v1/auth/invite/reset", body, true, cb);
        } catch (Exception e) {
            fail(cb, e);
        }
    }

    /** 真实登录：账号标识（手机号或用户名）+ 密码。 */
    public static void login(Context ctx, String account, String password, Callback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("username", account);
            body.put("password", password);
            call(ctx, "POST", "/api/v1/auth/login", body, true, cb);
        } catch (Exception e) {
            fail(cb, e);
        }
    }

    /**
     * 修改用户名（会话签名）。
     * 成功后服务端返回 data.user，客户端覆盖本地缓存即可，**不需要重新登录**；
     * 用户名是登录标识，改完新名字立即能用来登录、旧名字立即失效，手机号不受影响。
     */
    public static void changeUsername(Context ctx, String username, Callback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("username", username);
            call(ctx, "POST", "/api/v1/auth/profile", body, false, cb);
        } catch (Exception e) {
            fail(cb, e);
        }
    }

    /**
     * 换头像（会话签名）。图片压到 320px 后以 base64 传，服务端存进存储并回 `data.user`。
     * 传 remove=true 则清空头像、恢复用户名首字。
     */
    public static void uploadAvatar(Context ctx, String base64, Callback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("image_base64", base64);
            call(ctx, "POST", "/api/v1/auth/avatar", body, false, cb);
        } catch (Exception e) {
            fail(cb, e);
        }
    }

    public static void removeAvatar(Context ctx, Callback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("remove", true);
            call(ctx, "POST", "/api/v1/auth/avatar", body, false, cb);
        } catch (Exception e) {
            fail(cb, e);
        }
    }

    /**
     * 提交解析任务（会话签名）。**默认仅会员可用**：未开通会员时服务端返回 2001，
     * 客户端拿 `msg` 引导开通即可。
     * 成功后用 {@link #task} 轮询进度，完成后返回体里有 `out_url`（成品签名地址）。
     */
    public static void parse(Context ctx, String link, Callback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("share_text", link);
            call(ctx, "POST", "/api/v1/dw/parse", body, false, cb);
        } catch (Exception e) {
            fail(cb, e);
        }
    }

    /** 查询解析任务进度（会话签名）：status 0排队 1解析 2合成 3完成 4失败 5超时。 */
    public static void task(Context ctx, String taskNo, Callback cb) {
        call(ctx, "GET", "/api/v1/dw/task/" + taskNo, null, false, cb);
    }

    /** 查询授权状态（会话密钥签名）：activated 决定是否显示水印遮罩。 */
    public static void status(Context ctx, Callback cb) {
        call(ctx, "GET", "/api/v1/auth/status", null, false, cb);
    }

    /** 拉取热更新配置：水印文案、未激活提示、客服信息都从这里下发。 */
    public static void config(Context ctx, Callback cb) {
        call(ctx, "GET", "/api/v1/config", null, true, cb);
    }

    /** 版本检查：后台「版本推送」里发布的版本，客户端启动时拉一次。 */
    public static void versionCheck(Context ctx, String platform, int versionCode, Callback cb) {
        call(ctx, "GET", "/api/v1/version/check",
                "platform=" + platform + "&version_code=" + versionCode, null, true, cb);
    }

    /**
     * 提交意见反馈（「我 → 求新功能」）。
     *
     * 用 DTW 会话验签（root 模式）：没登录的用户也能提意见，登录了服务端会顺手记上是谁提的。
     * 图片是压缩后的 data-URL 数组（服务端只认 JPG/PNG/WebP/GIF 的文件头，单张上限 1MB）。
     */
    public static void submitFeedback(Context ctx, String content, java.util.List<String> images,
                                      String contact, String model, String osVersion, Callback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("content", content);
            org.json.JSONArray arr = new org.json.JSONArray();
            if (images != null) for (String image : images) if (image != null && !image.isEmpty()) arr.put(image);
            body.put("images", arr);
            if (contact != null && !contact.isEmpty()) body.put("contact", contact);
            if (model != null && !model.isEmpty()) body.put("model", model);
            if (osVersion != null && !osVersion.isEmpty()) body.put("os_version", osVersion);
            call(ctx, "POST", "/api/v1/feedback/create", null, body, true, cb);
        } catch (Exception e) {
            if (cb != null) cb.onResult(new Result(-1, "提交内容有误：" + e.getMessage(), null, false));
        }
    }

    public static void logout(Context ctx, Callback cb) {
        // 先本地清干净，服务端撤销失败也不影响用户
        call(ctx, "POST", "/api/v1/auth/logout", null, false, new Callback() {
            @Override
            public void onResult(Result result) {
                Session.clear();
                if (cb != null) cb.onResult(result);
            }
        });
    }

    /* ------------------------------ 设备号 ------------------------------ */

    /** 设备指纹：Android ID + 机型做 SHA-256，小写（必须与服务端的归一化规则一致）。 */
    public static synchronized String deviceId(Context ctx) {
        if (deviceId != null) return deviceId;
        String androidId;
        try {
            androidId = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception e) {
            androidId = "unknown";
        }
        String raw = "doutu|" + androidId + "|" + android.os.Build.MANUFACTURER + "|" + android.os.Build.MODEL;
        deviceId = "and-" + sha256Hex(raw).substring(0, 32);
        return deviceId;
    }

    /* ------------------------------ 实现 ------------------------------ */

    private static void call(Context ctx, String method, String path, JSONObject body, boolean useRootKey, Callback cb) {
        call(ctx, method, path, null, body, useRootKey, cb);
    }

    /**
     * 带查询串的版本。注意：**签名只覆盖 pathname**（服务端就是这么验的），
     * 查询串只拼在 URL 上、不进签名，否则会被判成验签失败。
     */
    private static void call(Context ctx, String method, String path, String query, JSONObject body,
                             boolean useRootKey, Callback cb) {
        final Context appCtx = ctx.getApplicationContext();
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                Result result = request(appCtx, method, path, query, body, useRootKey);
                if (cb != null) MAIN.post(() -> cb.onResult(result));
            }
        });
    }

    private static Result request(Context ctx, String method, String path, JSONObject body, boolean useRootKey) {
        return request(ctx, method, path, null, body, useRootKey);
    }

    /**
     * 走 DTW/1 私有协议发一个请求。
     *
     * <p>业务签名仍然照旧算（会话接口用 sign_key），只是这些头不再直接挂在 HTTP 上，
     * 而是塞进加密载荷的 {@code h} 字段由服务端还原；{@code rb} 则带上**签过名的那个原始串**，
     * 否则服务端重新 stringify 后键序一变就验签失败。
     *
     * <p>root 类引导接口不再签名（客户端没有 MASTER_KEY 了）：隧道本身已经用会话密钥
     * 做过认证，服务端也据此放行。
     */
    private static Result request(Context ctx, String method, String path, String query, JSONObject body,
                                  boolean useRootKey) {
        try {
            String raw = body == null ? "" : body.toString();
            long ts = System.currentTimeMillis() / 1000;
            String nonce = randomHex(16);
            String dev = deviceId(ctx);

            JSONObject headers = new JSONObject();
            headers.put("x-device-id", dev);
            headers.put("x-timestamp", String.valueOf(ts));
            headers.put("x-nonce", nonce);
            headers.put("x-app-version", APP_VERSION);
            headers.put("x-version-code", String.valueOf(VERSION_CODE));
            if (Session.isLoggedIn()) headers.put("x-token", Session.token());
            if (!useRootKey) {
                String bodyHash = sha256Hex(raw);
                String payload = ts + "\n" + nonce + "\n" + dev + "\n" + path + "\n" + bodyHash;
                headers.put("x-sign", hmacHex(Session.signKey(), payload));
            }

            DtwTransport.Reply reply = DtwTransport.send(ctx, method, path, query, body, raw, headers);
            String text = reply.body;
            JSONObject json = text == null || text.isEmpty() ? new JSONObject() : new JSONObject(text);
            int code = json.optInt("code", reply.status);
            String msg = json.optString("msg", "");
            JSONObject data = json.optJSONObject("data");
            // 隧道响应的完整性与来源由 AEAD 标签保证，服务端不再额外签名 → 这里视为已验签
            return new Result(code, msg, data, true);
        } catch (Exception e) {
            // 握手失败、帧被拒、DNS/超时都会走到这里，带上地址方便真机定位
            android.util.Log.e("DoutuApi", "请求失败 " + method + " " + Session.baseUrl() + path + " → " + e, e);
            return new Result(-1, "网络异常：" + e.getMessage(), null, false);
        }
    }

    /**
     * 会话接口的响应验签：服务端把 sign 作为 JSON 的最后一个字段，
     * 因此把尾部 `,"sign":"..."` 去掉再补上 `}` 就是被签名的原文。
     *
     * <p>走隧道之后这一层是**冗余**的（整个响应被 AEAD 认证过），保留它是为了兼容
     * 明文回退路径与离线自检；root 接口不再验签（客户端已经没有 MASTER_KEY）。
     */
    private static boolean verifyResponse(String text, boolean useRootKey) {
        if (useRootKey) return false;
        if (text == null || text.isEmpty()) return false;
        int idx = text.lastIndexOf(",\"sign\":");
        if (idx < 0) return false;
        String signedPart = text.substring(0, idx) + "}";
        String signPart = text.substring(idx + ",\"sign\":".length());
        int end = signPart.indexOf('}');
        if (end < 0) return false;
        String sign = signPart.substring(0, end).replace("\"", "").trim();
        if (sign.isEmpty() || "null".equals(sign)) return false;
        return hmacHex(Session.signKey(), signedPart).equalsIgnoreCase(sign);
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static void fail(Callback cb, Exception e) {
        final Result r = new Result(-1, "本地错误：" + e.getMessage(), null, false);
        if (cb != null) MAIN.post(() -> cb.onResult(r));
    }

    /* ------------------------------ 摘要工具 ------------------------------ */

    public static String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return toHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hmacHex(String key, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        new java.security.SecureRandom().nextBytes(buf);
        return toHex(buf);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }
}
