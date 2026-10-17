package com.luoyun.doutu.account.dtw;

import com.luoyun.doutu.BuildConfig;

import com.luoyun.doutu.account.Session;

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

    private DtwConfig() {
    }

    public static String baseUrl() {
        return Session.baseUrl();
    }
}
