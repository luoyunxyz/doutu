package com.luoyun.doutu.account;

import com.luoyun.doutu.account.dtw.DtwConfig;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 头像缓存：按服务端下发的 `avatar_url` 拉一次图，内存 + 本地文件各存一份。
 *
 * 头像地址是公开只读的 `/avatar/:id?v=版本号`，版本号随 `app_user.updated_at` 变，
 * 所以换过头像后 URL 会变、这里就会重新下载，不需要额外清缓存。
 * 界面上的取用方式：`AvatarStore.bitmap()` 有值就画图，没有就退回用户名首字。
 */
public final class AvatarStore {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    private static Bitmap bitmap;
    private static String loadedUrl = "";
    private static File cacheFile;

    private AvatarStore() {
    }

    public static void attach(Context context) {
        cacheFile = new File(context.getApplicationContext().getFilesDir(), "avatar.img");
    }

    /** 当前头像位图；null 表示没有头像（界面用首字兜底）。 */
    public static Bitmap bitmap() {
        return bitmap;
    }

    /** 换了账号或退出登录时清掉，避免串号。 */
    public static void clear() {
        bitmap = null;
        loadedUrl = "";
        if (cacheFile != null && cacheFile.exists()) cacheFile.delete();
    }

    /**
     * 按当前会话拉头像；URL 没变且已有图就直接回调。
     * onDone 在主线程执行，调用方在里面 invalidate() 即可。
     */
    public static synchronized void load(Context context, Runnable onDone) {
        // 再规范化一次：Session 里已处理 JSON null，这里挡住"别处写进偏好设置里的坏值"
        final String url = Session.normalizeAvatarUrl(Session.avatarUrl());
        if (url == null || url.isEmpty()) {
            bitmap = null;
            loadedUrl = "";
            if (onDone != null) MAIN.post(onDone);
            return;
        }
        if (url.equals(loadedUrl) && bitmap != null) {
            if (onDone != null) MAIN.post(onDone);
            return;
        }
        // 磁盘缓存先顶一下（换 URL 后不命中，会走网络）
        if (bitmap == null && cacheFile != null && cacheFile.exists() && url.equals(loadedUrl)) {
            bitmap = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
            if (bitmap != null) {
                if (onDone != null) MAIN.post(onDone);
                return;
            }
        }
        final Context appCtx = context.getApplicationContext();
        final String full = url.startsWith("http") ? url : Session.baseUrl() + url;
        if (full.endsWith("null") || full.endsWith("undefined")) {
            // 理论上到不了这里（上面已规范化）；真到了就宁可不要头像，也别去打一个坏地址
            android.util.Log.w("AvatarStore", "头像地址异常，跳过：" + full);
            bitmap = null;
            if (onDone != null) MAIN.post(onDone);
            return;
        }
        POOL.execute(() -> {
            final Bitmap fetched = download(full);
            if (fetched != null && cacheFile != null) {
                try (FileOutputStream out = new FileOutputStream(cacheFile)) {
                    fetched.compress(Bitmap.CompressFormat.PNG, 100, out);
                } catch (Exception ignored) {
                    // 缓存失败不影响显示
                }
            }
            MAIN.post(() -> {
                bitmap = fetched;
                loadedUrl = fetched == null ? "" : url;
                if (onDone != null) onDone.run();
            });
        });
        // 供外部引用，避免被优化掉
        if (appCtx == null) return;
    }

    private static Bitmap download(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) DtwConfig.mediaUrl(url).openConnection();
            DtwConfig.prepare(conn);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception e) {
            android.util.Log.w("AvatarStore", "头像下载失败 " + url + " → " + e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}