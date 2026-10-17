package com.luoyun.doutu.features.pay.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.luoyun.doutu.features.pay.ui.CollectMoneyScreen;
import com.luoyun.doutu.features.zhi.data.Accounts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * The two things on the 收钱 page that are the user's: the name printed over the collection code,
 * and the code itself when it was replaced with a picture from the gallery.
 *
 * <p>The picture used to last only as long as the page was open; a swipe away from the recents list
 * took it with it. Both are written to the app's own preferences / files directory now and read back
 * the first time the page is built after the process starts.
 *
 * <p>每个账号一套存档（后缀来自 {@link Accounts#suffix()}）。
 */
public final class CollectStore {
    private static final String PREFS = "collect_store";
    private static final String KEY_NAME = "name";
    /** 换成相册图片后存下来的那份，跟着账号走。 */
    private static final String QR_FILE = "collect_qr";
    /** 存图前先缩到这个边长：收钱码本身不需要更大的分辨率。 */
    private static final int QR_MAX = 1024;

    private static Context context;
    private static boolean loaded;
    private static String name;
    private static Bitmap qr;

    private CollectStore() {
    }

    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    /** 换账号时把这一份缓存丢掉，下一次问它读到的就是新账号的存档。 */
    public static void reset() {
        loaded = false;
        name = null;
        qr = null;
    }

    /** 把存档里的值铺回页面状态；没存过的字段保持 {@link CollectMoneyScreen.State} 的默认值。 */
    public static void apply(CollectMoneyScreen.State state) {
        load();
        if (state == null) return;
        if (name != null && !name.trim().isEmpty()) state.name = name;
        if (qr != null && !qr.isRecycled()) state.qr = qr;
    }

    public static String name() {
        load();
        return name;
    }

    public static void name(String value) {
        load();
        name = value;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(KEY_NAME, value).apply();
    }

    /** 用户从相册换了一张收钱码；原图在这里缩过再存。 */
    public static Bitmap qr() {
        load();
        return qr;
    }

    public static void qr(Bitmap value) {
        load();
        qr = value;
        write(value);
    }

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences prefs = prefs();
        if (prefs != null) name = prefs.getString(KEY_NAME, null);
        qr = read();
    }

    private static SharedPreferences prefs() {
        return context == null ? null
                : context.getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }

    private static File file() {
        return context == null ? null
                : new File(context.getFilesDir(), QR_FILE + Accounts.suffix() + ".png");
    }

    private static void write(Bitmap value) {
        File target = file();
        if (target == null) return;
        if (value == null) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            return;
        }
        Bitmap scaled = shrink(value);
        try (OutputStream out = new FileOutputStream(target)) {
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            // 这一次仍然显示得出来，只是下次打开会退回生成的那张
        } finally {
            if (scaled != value) scaled.recycle();
        }
    }

    private static Bitmap read() {
        File source = file();
        if (source == null || !source.isFile()) return null;
        try {
            return BitmapFactory.decodeFile(source.getAbsolutePath());
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap shrink(Bitmap value) {
        int longest = Math.max(value.getWidth(), value.getHeight());
        if (longest <= QR_MAX) return value;
        float factor = QR_MAX / (float) longest;
        int width = Math.max(1, Math.round(value.getWidth() * factor));
        int height = Math.max(1, Math.round(value.getHeight() * factor));
        return Bitmap.createScaledBitmap(value, width, height, true);
    }
}
