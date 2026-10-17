package com.luoyun.doutu.features.zhi.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The random-avatar service: one picture per call, used by the avatar sheet (4×4 grid, 换一批)
 * and by the 「我」 page's 更换头像.
 *
 * <p>接口（2026-10-17 起以用户指定的为准）：
 * {@code GET https://api.lvxiaodong.com/api/head} →
 * {@code {"code":200,"msg":"数据请求成功","data":"https://images.xxapi.cn/images/head/<id>.jpg"}}；
 * 拿到的图片地址再下载解码。备用地址是同一套图的另一个网关（老接口），主接口不通时自动降级 ——
 * 这两个返回结构完全一样，所以换网关不影响任何调用方。
 *
 * <p>Everything runs off the main thread and the answer is handed back on it, so the caller only
 * has to decide what to do with the result.
 */
public final class AvatarApi {
    /** 主接口（用户指定）+ 备用网关；按顺序试，第一个拿到图就返回。 */
    private static final String[] ENDPOINTS = {
            "https://api.lvxiaodong.com/api/head",
            "https://v2.xxapi.cn/api/head?return=json",
    };
    private static final int TIMEOUT_MS = 12000;
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    /** Receives the decoded picture, or null when the service could not be reached. */
    public interface Callback {
        void onAvatar(Bitmap bitmap);
    }

    /** Receives one tile of a batch; {@code index} is the slot it was asked to fill. */
    public interface Sink {
        void onAvatar(int index, Bitmap bitmap);
    }

    private AvatarApi() {
    }

    public static void fetch(Callback callback) {
        fetch(callback, 0);
    }

    /** One random avatar, decoded no larger than {@code maxPx} on its longer side. */
    public static void fetch(Callback callback, int maxPx) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Bitmap result = take(maxPx);
            main.post(() -> callback.onAvatar(result));
        }, "avatar-fetch").start();
    }

    /**
     * Fills a grid: {@code count} avatars are fetched over a small pool and handed back one by one
     * as they arrive, so the sheet can draw each tile the moment its picture is ready.
     */
    public static void fetchBatch(int count, int maxPx, Sink sink) {
        Handler main = new Handler(Looper.getMainLooper());
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(4, Math.max(1, count)));
        for (int i = 0; i < count; i++) {
            int slot = i;
            pool.execute(() -> {
                Bitmap result = take(maxPx);
                main.post(() -> sink.onAvatar(slot, result));
            });
        }
        pool.shutdown();
    }

    /** One request and its picture, on whatever thread calls it. Null when anything goes wrong. */
    private static Bitmap take(int maxPx) {
        Bitmap bitmap = attempt(maxPx);
        // a tile that comes back empty is retried once, so the sheet rarely shows a hole
        return bitmap != null ? bitmap : attempt(maxPx);
    }

    private static Bitmap attempt(int maxPx) {
        // 主接口拿不到就换备用网关；两个都试不出来才返回 null（调用方会重试一次）
        for (String endpoint : ENDPOINTS) {
            try {
                String link = new JSONObject(new String(get(endpoint))).optString("data");
                if (!link.startsWith("http")) continue;
                byte[] picture = get(link);
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(picture, 0, picture.length, bounds);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;      // the field starts at 0, which would divide by zero below
                if (maxPx > 0) {
                    int longest = Math.max(bounds.outWidth, bounds.outHeight);
                    while (longest / (options.inSampleSize * 2) >= maxPx) options.inSampleSize *= 2;
                }
                Bitmap bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.length, options);
                if (bitmap != null) return bitmap;
            } catch (Exception e) {
                Log.w("AvatarApi", "avatar fetch failed: " + endpoint, e);
            }
        }
        return null;
    }

    private static byte[] get(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "doutu-demo");
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    if (out.size() > MAX_BYTES) break;
                }
                return out.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }
}
