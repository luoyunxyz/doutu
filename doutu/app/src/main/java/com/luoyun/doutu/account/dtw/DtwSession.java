package com.luoyun.doutu.account.dtw;

import android.content.Context;
import android.content.SharedPreferences;

import com.luoyun.doutu.BuildConfig;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.PublicKey;
import java.util.UUID;

/**
 * DTW/1 的会话：握手 + 会话状态（sid / 会话密钥 / 序号）。
 *
 * <p>握手两步（与服务端 {@code core/dtw.js} 对应）：
 * <pre>
 *   ① hello → { salt, challenge, kid, expire_at, sig }
 *      客户端必须用**内置公钥**验 sig —— 这是唯一的信任锚。不验的话，
 *      中间人换掉公钥/盐就能把整套协议架空。
 *   ② open  → 慢哈希证明 + 会话密钥，用服务端公钥混合加密送上去
 *      → { sid, expire_at, sig }（同样验签，并核对 challenge 是否一致）
 * </pre>
 *
 * <p>时间片（bucket）用「10 分钟一片」而不是精确时间戳：否则两边时钟差几百毫秒，
 * 客户端算出的证明和服务端复算的就对不上。时间片在片内稳定、跨片失效。
 */
public final class DtwSession {
    private static final String PREFS = "dtw_store";
    private static final String KEY_INSTALL = "install_id";
    /** 与服务端 PROOF_BUCKET_SEC 必须一致 */
    private static final long PROOF_BUCKET_SEC = 600;

    private static final Object LOCK = new Object();

    private static String sid = "";
    private static String sessionKey = "";
    private static long expireAt = 0;
    private static int seq = 0;
    /** 握手失败后的冷却时间：别让每个请求都去撞一次握手 */
    private static long blockedUntil = 0;
    private static String lastError = "";

    private DtwSession() {
    }

    public static boolean hasSession() {
        synchronized (LOCK) {
            return !sid.isEmpty() && !sessionKey.isEmpty() && System.currentTimeMillis() < expireAt;
        }
    }

    public static String sid() {
        synchronized (LOCK) {
            return sid;
        }
    }

    public static String sessionKey() {
        synchronized (LOCK) {
            return sessionKey;
        }
    }

    public static String lastError() {
        synchronized (LOCK) {
            return lastError;
        }
    }

    /** 下一个请求序号（会话内单调递增，服务端据此做重放窗口判断） */
    public static int nextSeq() {
        synchronized (LOCK) {
            seq += 1;
            return seq;
        }
    }

    public static int currentSeq() {
        synchronized (LOCK) {
            return seq;
        }
    }

    public static void reset() {
        synchronized (LOCK) {
            sid = "";
            sessionKey = "";
            expireAt = 0;
            seq = 0;
        }
    }

    /* ------------------------------ 安装标识 ------------------------------ */

    /** 每个安装一份随机标识：换设备/重装即变，服务端用它做设备维度限流 */
    public static String installId(Context ctx) {
        SharedPreferences sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String value = sp.getString(KEY_INSTALL, "");
        if (value == null || value.isEmpty()) {
            value = UUID.randomUUID().toString().replace("-", "");
            sp.edit().putString(KEY_INSTALL, value).apply();
        }
        return value;
    }

    /* ------------------------------ 握手 ------------------------------ */

    /**
     * 确保有一个可用会话；没有就握一次手。
     *
     * @return 失败原因，成功返回 null
     */
    public static String ensure(Context ctx, String deviceId) {
        synchronized (LOCK) {
            if (hasSession()) return null;
            if (System.currentTimeMillis() < blockedUntil) {
                return lastError.isEmpty() ? "私有协议握手中，请稍后重试" : lastError;
            }
            try {
                handshake(ctx.getApplicationContext(), deviceId);
                lastError = "";
                blockedUntil = 0;
                return null;
            } catch (Exception e) {
                lastError = e.getMessage() == null ? "私有协议握手失败" : e.getMessage();
                // 冷却 15 秒：失败了先别连环撞
                blockedUntil = System.currentTimeMillis() + 15_000;
                reset();
                return lastError;
            }
        }
    }

    private static void handshake(Context ctx, String deviceId) throws Exception {
        PublicKey serverKey = DtwCrypto.parsePublicKey(DtwConfig.PUBLIC_KEY);
        String installId = installId(ctx);

        // ① hello
        JSONObject helloReq = new JSONObject();
        helloReq.put("v", DtwConfig.VERSION);
        helloReq.put("step", "hello");
        helloReq.put("device_id", deviceId);
        helloReq.put("install_id", installId);
        JSONObject hello = postJson(ctx, DtwConfig.HANDSHAKE_PATH, helloReq, deviceId);

        JSONObject data = hello.optJSONObject("data");
        if (data == null) {
            throw new IllegalStateException("握手失败：" + hello.optString("msg", "服务端未返回数据"));
        }
        String challenge = data.optString("challenge", "");
        String salt = data.optString("salt", "");
        // kid 是**字符串**（服务端配置里就是 'k1' 这种），按 int 取会变成 0，
        // 转录串一拼就和服务端对不上 → 验签必失败。这个坑踩过一次。
        String kid = data.optString("kid", "");
        long expireAtRemote = data.optLong("expire_at", 0);
        if (challenge.isEmpty() || salt.isEmpty()) throw new IllegalStateException("握手响应不完整");

        String helloTranscript = transcript(DtwConfig.VERSION, kid, "", challenge, deviceId, installId,
                data.optString("salt_id", ""), expireAtRemote);
        if (!DtwCrypto.verifyServerSignature(serverKey, helloTranscript, data.optString("sig", ""))) {
            throw new IllegalStateException("服务端签名校验失败（可能有人在中间换钥匙），已中止");
        }

        // ② open：慢哈希证明 + 一条随机会话密钥
        long bucket = System.currentTimeMillis() / 1000 / PROOF_BUCKET_SEC;
        String material = salt + "|" + challenge + "|" + deviceId + "|" + installId + "|" + bucket;
        String proof = DtwCrypto.hex(DtwCrypto.pbkdf2Sha256(
                DtwCrypto.utf8(material), DtwCrypto.utf8(salt), DtwConfig.PROOF_ITER, 32));

        String candidateKey = DtwCrypto.randomHex(32);
        String hsKey = DtwCrypto.randomHex(32);
        JSONObject inner = new JSONObject();
        inner.put("session_key", candidateKey);
        inner.put("device_id", deviceId);
        inner.put("install_id", installId);
        inner.put("challenge", challenge);
        inner.put("proof", proof);
        inner.put("bucket", bucket);
        inner.put("ts", System.currentTimeMillis());
        inner.put("app_ver", DtwConfig.APP_VERSION);

        byte[][] sealed = DtwCrypto.seal(hsKey, DtwCrypto.utf8(inner.toString()), null);
        JSONObject openReq = new JSONObject();
        openReq.put("v", DtwConfig.VERSION);
        openReq.put("step", "open");
        openReq.put("ek", DtwCrypto.b64(DtwCrypto.rsaOaepEncrypt(serverKey, DtwCrypto.fromHex(hsKey))));
        openReq.put("iv", DtwCrypto.b64(sealed[0]));
        openReq.put("ct", DtwCrypto.b64(sealed[1]));
        openReq.put("tag", DtwCrypto.b64(sealed[2]));
        JSONObject open = postJson(ctx, DtwConfig.HANDSHAKE_PATH, openReq, deviceId);

        JSONObject openData = open.optJSONObject("data");
        if (openData == null) {
            throw new IllegalStateException("握手失败：" + open.optString("msg", "服务端未建立会话"));
        }
        String newSid = openData.optString("sid", "");
        long sessionExpire = openData.optLong("expire_at", 0);
        if (newSid.isEmpty()) throw new IllegalStateException("握手未返回会话号");

        String openTranscript = transcript(DtwConfig.VERSION, kid, newSid, challenge, deviceId, installId,
                data.optString("salt_id", ""), sessionExpire);
        if (!DtwCrypto.verifyServerSignature(serverKey, openTranscript, openData.optString("sig", ""))) {
            throw new IllegalStateException("会话应答签名校验失败，已中止");
        }

        sid = newSid;
        sessionKey = candidateKey;
        // 提前 60 秒过期，别卡在边界上
        expireAt = (sessionExpire > 0 ? sessionExpire : System.currentTimeMillis() + 3600_000L) - 60_000L;
        seq = 0;
    }

    /** 与服务端 transcript() 的字段顺序必须完全一致 */
    private static String transcript(int v, String kid, String sid, String challenge, String deviceId,
                                     String installId, String saltId, long expireAt) {
        return "v=" + v
                + "&kid=" + kid
                + "&sid=" + sid
                + "&challenge=" + challenge
                + "&device=" + deviceId
                + "&install=" + installId
                + "&salt=" + saltId
                + "&exp=" + expireAt;
    }

    /** 握手用的最小 HTTP：只发 JSON、只收 JSON（不复用业务传输，避免先有鸡还是先有蛋） */
    private static JSONObject postJson(Context ctx, String path, JSONObject body, String deviceId) throws Exception {
        HttpURLConnection conn = null;
        try {
            byte[] raw = DtwCrypto.utf8(body.toString());
            URL url = new URL(DtwConfig.baseUrl() + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Device-Id", deviceId);
            conn.setRequestProperty("X-Timestamp", String.valueOf(System.currentTimeMillis() / 1000));
            conn.setRequestProperty("X-Nonce", DtwCrypto.randomHex(16));
            conn.setRequestProperty("X-App-Version", DtwConfig.APP_VERSION);
            conn.setRequestProperty("X-Version-Code", String.valueOf(DtwConfig.VERSION_CODE));
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(raw);
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = readAll(in);
            if (text == null || text.isEmpty()) throw new IllegalStateException("握手无响应（HTTP " + code + "）");
            return new JSONObject(text);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return new String(out.toByteArray(), "UTF-8");
    }

    /** 客户端自检用的公钥指纹（与服务端 /api/v1/dtw/info 对比） */
    public static String keyFingerprint() {
        try {
            PublicKey key = DtwCrypto.parsePublicKey(DtwConfig.PUBLIC_KEY);
            return DtwCrypto.sha256Hex(key.getEncoded()).substring(0, 16);
        } catch (Exception e) {
            return "";
        }
    }

    static {
        // 让混淆后的字段名不影响调试日志
        if (BuildConfig.IS_DEV_BUILD) {
            android.util.Log.d("DtwSession", "公钥指纹 " + keyFingerprint());
        }
    }
}
