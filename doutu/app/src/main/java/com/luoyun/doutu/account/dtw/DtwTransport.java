package com.luoyun.doutu.account.dtw;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * DTW/1 的隧道传输：把「一个普通请求」封成二进制帧发出去，再把响应帧解回来。
 *
 * <p>帧格式（与服务端 {@code core/dtw.js} 一致）：
 * <pre>
 *   magic "DTW1"(4) | ver(1) | flags(1) | sid(8) | seq(4, 大端) | iv(12) | ct | tag(16)
 *   AAD = 前 18 字节（magic..seq），所以连帧头都改不了
 * </pre>
 *
 * <p>请求载荷里的 {@code rb} 是**客户端签名时用的那个原始 JSON 串**。业务层验签是对它取
 * SHA256 的，如果服务端用解密出来的对象重新 stringify，键序一变就验签失败 —— 这个坑在
 * 协议设计时就避掉了，客户端务必要把原文带上。
 */
public final class DtwTransport {
    private static final byte[] MAGIC = {'D', 'T', 'W', '1'};
    private static final int HEADER_LEN = 18;
    private static final int TAG_LEN = 16;

    /** 隧道调用结果（业务状态码与 Content-Type 都封在帧里） */
    public static final class Reply {
        public final int httpStatus;
        public final int status;
        public final String contentType;
        public final String body;

        Reply(int httpStatus, int status, String contentType, String body) {
            this.httpStatus = httpStatus;
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }
    }

    private DtwTransport() {
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        return b == null ? "" : b;
    }

    /**
     * 发一个隧道请求。
     *
     * @param headers 业务层要用的请求头（时间戳/nonce/签名/token…），原样带进载荷的 h 字段
     */
    public static Reply send(Context ctx, String method, String path, String query, JSONObject body,
                             String rawBody, JSONObject headers) throws Exception {
        Context app = ctx.getApplicationContext();
        // 头名统一按小写取（服务端的 ctx.headers 也是小写）
        String deviceId = headers == null ? ""
                : firstNonEmpty(headers.optString("x-device-id", ""), headers.optString("X-Device-Id", ""));
        String err = DtwSession.ensure(app, deviceId);
        if (err != null) throw new IllegalStateException(err);

        JSONObject payload = new JSONObject();
        payload.put("m", method);
        payload.put("p", path);
        payload.put("q", queryObject(query));
        // ⚠️ 只发一份 body：`rb` 是**签名时用的那个原始串**，服务端按它算 body_hash，
        // 也是它解析成业务 body（见服务端 openTunnelRequest）。
        // 早先这里 `b` 和 `rb` 都塞，等于每个请求的 body 发两遍 —— 小请求看不出来，
        // 头像上传（1MB 图 → base64 1.37MB）直接翻倍冲破服务端 2MB 的请求体上限。
        if (rawBody != null && !rawBody.isEmpty()) {
            payload.put("rb", rawBody);
        } else if (body != null) {
            payload.put("b", body);
        }
        payload.put("t", System.currentTimeMillis());
        if (headers != null) payload.put("h", headers);

        int seq = DtwSession.nextSeq();
        byte[] frame = encodeFrame(DtwSession.sid(), seq, DtwSession.sessionKey(),
                DtwCrypto.utf8(payload.toString()));

        HttpURLConnection conn = null;
        try {
            URL url = new URL(DtwConfig.baseUrl() + DtwConfig.TUNNEL_PATH);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(frame);
            }
            int httpCode = conn.getResponseCode();
            InputStream in = httpCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
            byte[] reply = readAllBytes(in);
            if (httpCode != 200) {
                // 隧道层自己报错（解包失败等），此时不是帧而是 JSON 信封
                throw new IllegalStateException("隧道被拒（HTTP " + httpCode + "）："
                        + new String(reply, "UTF-8"));
            }
            return decodeFrame(reply);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /* ------------------------------ 组帧 / 拆帧 ------------------------------ */

    static byte[] encodeFrame(String sid, int seq, String keyHex, byte[] payload) {
        ByteBuffer head = ByteBuffer.allocate(HEADER_LEN);
        head.put(MAGIC);
        head.put((byte) DtwConfig.VERSION);
        head.put((byte) 0);
        byte[] sidRaw = DtwCrypto.fromHex(sid);
        head.put(sidRaw, 0, Math.min(8, sidRaw.length));
        while (head.position() < 14) head.put((byte) 0);
        head.putInt(seq);
        byte[] aad = head.array();

        byte[][] sealed = DtwCrypto.seal(keyHex, payload, aad);
        ByteBuffer out = ByteBuffer.allocate(HEADER_LEN + sealed[0].length + sealed[1].length + sealed[2].length);
        out.put(aad);
        out.put(sealed[0]);
        out.put(sealed[1]);
        out.put(sealed[2]);
        return out.array();
    }

    static Reply decodeFrame(byte[] frame) throws Exception {
        if (frame.length < HEADER_LEN + TAG_LEN) throw new IllegalStateException("响应帧太短");
        for (int i = 0; i < 4; i++) {
            if (frame[i] != MAGIC[i]) throw new IllegalStateException("响应帧标识不对");
        }
        byte[] aad = new byte[HEADER_LEN];
        System.arraycopy(frame, 0, aad, 0, HEADER_LEN);

        byte[] iv = new byte[12];
        System.arraycopy(frame, HEADER_LEN, iv, 0, 12);
        int ctLen = frame.length - HEADER_LEN - 12 - TAG_LEN;
        byte[] ct = new byte[ctLen];
        System.arraycopy(frame, HEADER_LEN + 12, ct, 0, ctLen);
        byte[] tag = new byte[TAG_LEN];
        System.arraycopy(frame, frame.length - TAG_LEN, tag, 0, TAG_LEN);

        byte[] plain = DtwCrypto.open(DtwSession.sessionKey(), iv, ct, tag, aad);
        JSONObject box = new JSONObject(DtwCrypto.utf8(plain));
        String body = new String(DtwCrypto.unb64(box.optString("b", "")), "UTF-8");
        return new Reply(200, box.optInt("s", 200), box.optString("ct", "application/json"), body);
    }

    /** 查询串 → JSON 对象（服务端会拿它当 ctx.query） */
    private static JSONObject queryObject(String query) {
        JSONObject out = new JSONObject();
        if (query == null || query.isEmpty()) return out;
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            try {
                out.put(java.net.URLDecoder.decode(k, "UTF-8"), java.net.URLDecoder.decode(v, "UTF-8"));
            } catch (Exception ignored) {
                // 解析不了的键值对直接跳过，不影响其它参数
            }
        }
        return out;
    }

    /** 把请求头对象摊平成「小写名 → 值」的 JSON（服务端直接塞进 ctx.headers） */
    public static JSONObject headersToJson(java.util.Map<String, String> headers) {
        JSONObject out = new JSONObject();
        if (headers == null) return out;
        Iterator<java.util.Map.Entry<String, String>> it = headers.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String, String> e = it.next();
            try {
                out.put(e.getKey().toLowerCase(), e.getValue());
            } catch (Exception ignored) {
                // 极少数非法头名忽略即可
            }
        }
        return out;
    }

    private static byte[] readAllBytes(InputStream in) throws Exception {
        if (in == null) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }
}
