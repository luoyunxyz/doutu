package com.luoyun.doutu.features.call;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 来电设置页里那排联系人头像的加载器。
 *
 * <p>两种地址都要认：
 *   · {@code https://cdn-fakeworld.azureedge.net/...} —— 页面自带联系人的头像，走网络；
 *   · {@code ./assets/user-avatar-4-Cmbgep_s.jpg} —— 页面内置的那几张，直接从 APK 的 assets 里取
 *     （网页把 assets/fake_world/ 当根目录，这里补上这段前缀就行）。
 *
 * <p>解出来的图按边长分档缓存在内存里，滚动那一排时不会反复下载。
 */
public final class CallImages {
    private static final int TIMEOUT_MS = 12000;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_CACHE = 48;

    /**
     * 页面自带的那几张头像。
     *
     * <p>为什么要这份名单：页面里联系人的 {@code avatarInfo} 指的是它自己的 CDN
     * （{@code cdn-fakeworld.azureedge.net}），实测那个域名**已经解析不出来了**，拉谁都失败 ——
     * 网页那边只能退回同一张兜底图。这里按名字稳定地分一张本地图，通讯录里每个人就都有一张
     * 不一样的脸，断网也一样。
     */
    private static final String[] AVATAR_POOL = {
            "./assets/user-avatar-1-CKPDcH57.jpg",
            "./assets/user-avatar-4-Cmbgep_s.jpg",
            "./assets/user-avatar-5-DMigR-cn.jpg",
            "./assets/user-avatar-6-Cq95zrQT.jpg",
            "./assets/user-avatar-7-C4bh6bYx.jpg",
            "./assets/user-avatar-8-BysMeksY.jpg",
            "./assets/user-avatar-10-ZKj7tiz_.jpg",
    };

    /**
     * 一个名字对应一张固定的本地头像（同名永远同一张，看着像「这个人的头像」而不是随机图）。
     * 页面和原生都认 {@code ./assets/...} 这种写法，所以拿到的地址可以直接交给来电界面。
     */
    public static String localAvatarFor(String name) {
        String key = name == null ? "" : name.trim();
        int hash = Math.abs(key.hashCode());
        return AVATAR_POOL[hash % AVATAR_POOL.length];
    }

    /**
     * 按序号取一张本地头像。
     *
     * <p>名字哈希容易撞车（实测「林克」「老婆」「星之笨比」会落到同一张），通讯录是一排并排展示的，
     * 撞了就变成三张一样的脸。所以列表里按序号发牌，一排看下来张张不同。
     */
    public static String poolAvatar(int index) {
        return AVATAR_POOL[Math.floorMod(index, AVATAR_POOL.length)];
    }

    /** 判断一个地址是不是需要联网取（页面的联系人头像大多是这种）。 */
    public static boolean isRemote(String url) {
        return url != null && url.trim().toLowerCase().startsWith("http");
    }

    private static final Map<String, Bitmap> CACHE = new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > MAX_CACHE;
        }
    };

    private CallImages() {
    }

    /** 把 {@code url} 的图填进 {@code target}；缓存命中就同步填，否则后台取完再填。 */
    public static void into(final Context context, final String url, final int sizePx, final ImageView target) {
        final String key = url + "@" + sizePx;
        synchronized (CACHE) {
            Bitmap hit = CACHE.get(key);
            if (hit != null) {
                target.setImageBitmap(hit);
                return;
            }
        }
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            final Bitmap bitmap = decode(app, url, sizePx);
            main.post(() -> {
                if (bitmap == null) return;
                synchronized (CACHE) {
                    CACHE.put(key, bitmap);
                }
                // 视图可能已经被复用去显示别人了：只填地址还对得上的那一次
                Object tag = target.getTag();
                if (tag == null || tag.equals(url)) target.setImageBitmap(bitmap);
            });
        }, "call-avatar").start();
    }

    private static Bitmap decode(Context context, String url, int sizePx) {
        if (url == null || url.trim().isEmpty()) return null;
        String value = url.trim();
        try {
            byte[] bytes;
            if (value.startsWith("./")) {
                // 页面里的 ./assets/xxx → APK 里的 fake_world/assets/xxx
                String path = "fake_world/" + value.substring(2);
                try (InputStream in = context.getAssets().open(path)) {
                    bytes = readAll(in);
                }
            } else if (value.startsWith("http")) {
                bytes = get(value);
            } else if (value.startsWith("/")) {
                try (InputStream in = context.getAssets().open("fake_world" + value)) {
                    bytes = readAll(in);
                }
            } else {
                return null;
            }
            if (bytes == null || bytes.length == 0) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            if (sizePx > 0) {
                int longest = Math.max(bounds.outWidth, bounds.outHeight);
                while (longest / (options.inSampleSize * 2) >= sizePx) options.inSampleSize *= 2;
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] get(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "doutu-demo");
            try (InputStream in = connection.getInputStream()) {
                return readAll(in);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
            if (out.size() > MAX_BYTES) break;
        }
        return out.toByteArray();
    }
}
