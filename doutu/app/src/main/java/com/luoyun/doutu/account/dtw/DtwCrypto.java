package com.luoyun.doutu.account.dtw;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * DTW/1 的密码学原语（客户端侧）。
 *
 * <p>与服务端 {@code server/src/core/dtw.js} 必须逐字节一致，三个坑都在这里踩平：
 *
 * <ol>
 *   <li><b>PBKDF2 自己实现</b>：{@code PBKDF2WithHmacSHA256} 要 API 26 才有，而本项目
 *       minSdk 是 24；{@code PBKDF2WithHmacSHA1} 又和服务端的 SHA256 对不上。
 *       所以按标准手写一遍 —— PBKDF2 本来就是「反复 HMAC」，二十行的事，
 *       顺带也让逆向的人不能直接调库糊弄过去。</li>
 *   <li><b>OAEP 的 MGF1 必须显式指定 SHA-256</b>：Android 上
 *       {@code RSA/ECB/OAEPWithSHA-256AndMGF1Padding} 的 MGF1 **默认是 SHA-1**，
 *       而 Node 的 {@code oaepHash:'sha256'} 用的是 MGF1-SHA256 —— 不显式传
 *       {@link OAEPParameterSpec} 就会解密失败，而且报错很难看懂。</li>
 *   <li><b>数字全部大端</b>：Java 的 ByteBuffer 默认就是大端，与 Node 的
 *       {@code writeUInt32BE} 一致，组帧时不要手动翻转。</li>
 * </ol>
 */
public final class DtwCrypto {
    private static final String HMAC_ALG = "HmacSHA256";
    private static final int GCM_TAG_BITS = 128;

    private DtwCrypto() {
    }

    /* ------------------------------ PBKDF2 ------------------------------ */

    /**
     * PBKDF2-HMAC-SHA256，与 Node {@code crypto.pbkdf2Sync(pwd, salt, iter, 32, 'sha256')} 等价。
     *
     * <p>Node 的签名是 pbkdf2(password, salt, ...)，即 PRF 的 key 是 password、
     * 被 HMAC 的消息是 salt||INT(1)。顺序千万不能反，反了就是个看起来正常但永远对不上的值。
     */
    public static byte[] pbkdf2Sha256(byte[] password, byte[] salt, int iterations, int keyLen) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(password, HMAC_ALG));
            int hashLen = mac.getMacLength();
            int blocks = (keyLen + hashLen - 1) / hashLen;
            byte[] out = new byte[keyLen];
            byte[] block = new byte[salt.length + 4];
            System.arraycopy(salt, 0, block, 0, salt.length);
            for (int i = 1; i <= blocks; i++) {
                block[salt.length] = (byte) (i >>> 24);
                block[salt.length + 1] = (byte) (i >>> 16);
                block[salt.length + 2] = (byte) (i >>> 8);
                block[salt.length + 3] = (byte) i;

                byte[] u = mac.doFinal(block);
                byte[] acc = u.clone();
                for (int c = 1; c < iterations; c++) {
                    u = mac.doFinal(u);
                    for (int k = 0; k < acc.length; k++) acc[k] ^= u[k];
                }
                int offset = (i - 1) * hashLen;
                int take = Math.min(hashLen, keyLen - offset);
                System.arraycopy(acc, 0, out, offset, take);
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 计算失败：" + e.getMessage(), e);
        }
    }

    /** 十六进制小写，与服务端 Buffer.toString('hex') 一致 */
    public static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16));
            sb.append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }

    public static byte[] fromHex(String hex) {
        int len = hex.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static byte[] randomBytes(int len) {
        byte[] out = new byte[len];
        new java.security.SecureRandom().nextBytes(out);
        return out;
    }

    public static String randomHex(int len) {
        return hex(randomBytes(len));
    }

    public static String b64(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public static byte[] unb64(String text) {
        return Base64.decode(text, Base64.NO_WRAP);
    }

    /* ------------------------------ RSA ------------------------------ */

    /** 解析 X.509 SubjectPublicKeyInfo 格式的 PEM 公钥 */
    public static PublicKey parsePublicKey(String pem) {
        try {
            String body = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            byte[] der = Base64.decode(body, Base64.DEFAULT);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("DTW 公钥解析失败：" + e.getMessage(), e);
        }
    }

    /** RSA-OAEP(SHA-256 / MGF1-SHA256)，与 Node 的 oaepHash:'sha256' 对齐 */
    public static byte[] rsaOaepEncrypt(PublicKey key, byte[] plain) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            OAEPParameterSpec spec = new OAEPParameterSpec(
                    "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            return cipher.doFinal(plain);
        } catch (Exception e) {
            throw new IllegalStateException("RSA 加密失败：" + e.getMessage(), e);
        }
    }

    /** 校验服务端用私钥签的握手应答（SHA256withRSA，base64） */
    public static boolean verifyServerSignature(PublicKey key, String payload, String sigBase64) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.decode(sigBase64, Base64.DEFAULT));
        } catch (Exception e) {
            return false;
        }
    }

    /* ------------------------------ AES-GCM ------------------------------ */

    /** 返回 {iv(12), ct, tag(16)} */
    public static byte[][] seal(String keyHex, byte[] plain, byte[] aad) {
        try {
            byte[] iv = randomBytes(12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(fromHex(keyHex), "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            if (aad != null) cipher.updateAAD(aad);
            byte[] out = cipher.doFinal(plain);
            int bodyLen = out.length - 16;
            byte[] ct = new byte[bodyLen];
            byte[] tag = new byte[16];
            System.arraycopy(out, 0, ct, 0, bodyLen);
            System.arraycopy(out, bodyLen, tag, 0, 16);
            return new byte[][]{iv, ct, tag};
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败：" + e.getMessage(), e);
        }
    }

    public static byte[] open(String keyHex, byte[] iv, byte[] ct, byte[] tag, byte[] aad) {
        try {
            byte[] joined = new byte[ct.length + tag.length];
            System.arraycopy(ct, 0, joined, 0, ct.length);
            System.arraycopy(tag, 0, joined, ct.length, tag.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(fromHex(keyHex), "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            if (aad != null) cipher.updateAAD(aad);
            return cipher.doFinal(joined);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败（密钥不对或数据被改）：" + e.getMessage(), e);
        }
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public static String sha256Hex(byte[] data) {
        return hex(sha256(data));
    }

    public static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public static String utf8(byte[] data) {
        return new String(data, StandardCharsets.UTF_8);
    }
}
