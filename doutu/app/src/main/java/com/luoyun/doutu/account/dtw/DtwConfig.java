package com.luoyun.doutu.account.dtw;

import com.luoyun.doutu.BuildConfig;

import com.luoyun.doutu.account.Session;

import java.net.HttpURLConnection;
import java.net.URL;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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

    /**
     * 直连 IP 时证书是 tdapi.luoyun.xyz 的，且部分老设备的系统信任库不认这条链，
     * 会在别的手机上报 "A TLS error caused the secure connection to fail"。
     *
     * 这里只对 API 连接放宽 TLS 证书校验；服务器身份仍由 DTW 握手的内置公钥验签，
     * TLS 不是唯一信任锚。HTTPS 之外的其他域名（头像等）不走这里。
     */
    private static volatile SSLSocketFactory trustAllFactory;

    private static SSLSocketFactory trustAllFactory() {
        if (trustAllFactory != null) return trustAllFactory;
        synchronized (DtwConfig.class) {
            if (trustAllFactory != null) return trustAllFactory;
            try {
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new TrustManager[]{new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }}, new SecureRandom());
                trustAllFactory = ctx.getSocketFactory();
            } catch (Exception e) {
                trustAllFactory = null;
            }
            return trustAllFactory;
        }
    }

    /** IP direct: the certificate is still for the domain; allow the host name. */
    public static void prepare(HttpURLConnection conn) {
        if (conn == null) return;
        URL url = conn.getURL();
        String host = url == null ? "" : url.getHost();
        boolean api = API_IP.equals(host) || API_HOST.equalsIgnoreCase(host);
        if (api) {
            try {
                conn.setRequestProperty("Host", API_HOST);
            } catch (Exception ignored) {
            }
        }
        if (conn instanceof HttpsURLConnection && api) {
            HttpsURLConnection https = (HttpsURLConnection) conn;
            try {
                SSLSocketFactory factory = trustAllFactory();
                if (factory != null) https.setSSLSocketFactory(factory);
            } catch (Exception ignored) {
            }
            try {
                https.setHostnameVerifier((hostname, session) ->
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
