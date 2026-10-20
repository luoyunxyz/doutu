package com.luoyun.doutu.account.dtw;

import com.luoyun.doutu.BuildConfig;

import com.luoyun.doutu.account.Session;

import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * DTW/1 的客户端配置：全部来自编译期注入（{@code app/build.gradle}）。
 *
 * <p>这里**只有公钥和盐**，没有任何服务端私密材料 —— 这正是这次改造的核心：
 * 以前 APK 里编译着服务端 MASTER_KEY，反编译就能伪造官方客户端。
 * 公钥可以公开（本来就是设计成公开的），盐是慢哈希的公开输入。
 */
public final class DtwConfig {
    /** 协议版本，与服务端 VERSION 对应 */
    public static final int VERSION = BuildConfig.DTW_VERSION;
    /** 服务端 RSA 公钥（PEM，X.509 SubjectPublicKeyInfo） */
    public static final String PUBLIC_KEY = BuildConfig.DTW_PUBLIC_KEY;
    /** 慢哈希迭代次数，必须与服务端 DTW_PROOF_ITER 一致 */
    public static final int PROOF_ITER = BuildConfig.DTW_PROOF_ITER;
    /** 隧道与握手地址 */
    public static final String TUNNEL_PATH = BuildConfig.DTW_TUNNEL_PATH;
    public static final String HANDSHAKE_PATH = BuildConfig.DTW_HANDSHAKE_PATH;

    public static final String APP_VERSION = BuildConfig.VERSION_NAME;
    public static final int VERSION_CODE = BuildConfig.VERSION_CODE;

    /** API domain: the app keeps using this single domain. */
    public static final String API_HOST = "tdapi.luoyun.xyz";
    /** Public server IP: used only for the internal socket, not for URL/Host/cert. */
    public static final String API_IP = "69.165.75.130";

    /** Map the business-domain URL to the server IP; other hosts are untouched. */
    public static URL connectUrl(String path) throws Exception {
        return mapHost(new URL(baseUrl() + path));
    }

    public static URL mediaUrl(String url) throws Exception {
        return mapHost(new URL(url));
    }

    public static URL mapHost(URL url) throws Exception {
        if (url != null && API_HOST.equalsIgnoreCase(url.getHost())) {
            return new URL(url.getProtocol(), API_IP, url.getPort(), url.getFile());
        }
        return url;
    }

    /** IP direct: the certificate is still for the domain; allow the host name. */
    public static void prepare(HttpURLConnection conn) {
        if (conn == null) return;
        try {
            conn.setRequestProperty("Host", API_HOST);
        } catch (Exception ignored) {
        }
        if (conn instanceof HttpsURLConnection) {
            try {
                ((HttpsURLConnection) conn).setHostnameVerifier((hostname, session) ->
                        API_IP.equals(hostname) || API_HOST.equalsIgnoreCase(hostname));
            } catch (Exception ignored) {
            }
        }
    }

    private DtwConfig() {
    }

    public static String baseUrl() {
        return Session.baseUrl();
    }
}
