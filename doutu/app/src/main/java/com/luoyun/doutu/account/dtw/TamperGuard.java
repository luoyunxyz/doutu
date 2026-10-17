package com.luoyun.doutu.account.dtw;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import com.luoyun.doutu.BuildConfig;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 防二次打包 + 环境信号（2026-10-17）。
 *
 * <p>做两件事，但**态度不同**：
 *
 * <ol>
 *   <li><b>签名校验（硬拦）</b>：包被人重新签过一次，说明它被拆开改过 —— 这没有误伤可言，
 *       直接拒绝启动。签名证书的 SHA-256 是编译期注入的**公钥信息**（不是秘密），
 *       改包的人拿不到我们的私钥，换不了这张证书。</li>
 *   <li><b>Root / 调试信号（只记录）</b>：越狱机、调试器、测试签名这些现象本身**不能定罪** ——
 *       不少正常用户就是 root 机。所以这里只收集信号、记日志（后续可上报风控），
 *       绝不因此拒绝服务。</li>
 * </ol>
 *
 * <p>说明白一点：签名校验挡的是「改包重签」这一档（市面上绝大多数二次打包），
 * 挡不住把校验代码本身 patch 掉的人。它提高的是门槛，不是绝对保险。
 */
public final class TamperGuard {
    /** 期望的签名证书 SHA-256（大写十六进制、无冒号），编译期注入 */
    public static final String EXPECTED_CERT_SHA256 = BuildConfig.SIGNING_CERT_SHA256;

    private TamperGuard() {
    }

    /**
     * 校验当前安装包的签名证书。
     *
     * @return null 表示通过；否则返回人话原因
     */
    public static String verifySignature(Context ctx) {
        if (BuildConfig.IS_DEV_BUILD) return null;              // 调试包用调试签名，跳过
        if (EXPECTED_CERT_SHA256 == null || EXPECTED_CERT_SHA256.isEmpty()) return null;
        try {
            String actual = currentCertSha256(ctx);
            if (actual == null) return "读取签名失败";
            if (!actual.equalsIgnoreCase(EXPECTED_CERT_SHA256)) {
                return "应用签名不匹配（当前 " + actual.substring(0, 12) + "…，官方"
                        + EXPECTED_CERT_SHA256.substring(0, 12) + "…）";
            }
            return null;
        } catch (Exception e) {
            return "签名校验异常：" + e.getMessage();
        }
    }

    /** 当前安装包的签名证书 SHA-256（大写十六进制，无分隔符） */
    public static String currentCertSha256(Context ctx) throws Exception {
        PackageManager pm = ctx.getPackageManager();
        String pkg = ctx.getPackageName();
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
            if (info.signingInfo == null) return null;
            signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
        } else {
            @SuppressWarnings("deprecation")
            PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0) return null;
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(signatures[0].toByteArray());
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    /**
     * 环境信号：root / 调试 / 模拟器。
     * 只用来记日志与后续风控，不作为拒绝服务的依据。
     */
    public static List<String> environmentSignals(Context ctx) {
        List<String> signals = new ArrayList<>();
        if (isDebuggable(ctx)) signals.add("debuggable");
        if (hasSuBinary()) signals.add("su_binary");
        if (hasTestKeys()) signals.add("test_keys");
        if (isEmulator()) signals.add("emulator");
        return signals;
    }

    public static boolean isDebuggable(Context ctx) {
        return (ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    /** 常见 su 路径存在即视为有 root 痕迹（各家 root 方案位置不同，取并集） */
    public static boolean hasSuBinary() {
        String[] paths = {
                "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
                "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su", "/data/local/xbin/su",
        };
        for (String p : paths) {
            try {
                if (new File(p).exists()) return true;
            } catch (Exception ignored) {
                // 个别 ROM 上路径访问会抛异常，忽略
            }
        }
        return false;
    }

    /** 系统 build 带 test-keys：多数第三方/定制 ROM 的特征 */
    public static boolean hasTestKeys() {
        String tags = String.valueOf(Build.TAGS);
        return tags.contains("test-keys");
    }

    public static boolean isEmulator() {
        String model = String.valueOf(Build.MODEL) + " " + String.valueOf(Build.MANUFACTURER);
        String fp = String.valueOf(Build.FINGERPRINT);
        return fp.contains("generic") || fp.contains("unknown")
                || model.toLowerCase().contains("sdk") || model.toLowerCase().contains("emulator")
                || model.toLowerCase().contains("genymotion");
    }
}
