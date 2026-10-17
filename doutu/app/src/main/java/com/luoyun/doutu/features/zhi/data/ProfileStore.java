package com.luoyun.doutu.features.zhi.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * The three fields of the 小支主页 the user may change — the avatar, the nickname and the phone
 * number. Both the 我的 tab and the 个人信息 page read them, so the change shows on both.
 *
 * <p>What is changed here outlives the page and the app: the nickname and the number are written to
 * the app's own preferences and the picture to its own files directory, and the three are read back
 * the first time this store is asked for anything after the process starts — so closing the app
 * from the recents list and opening it again brings the user's own profile back rather than the
 * reference's defaults. Nothing is written anywhere the user cannot see, and clearing the app's
 * data clears this with it.
 *
 * <p>The page is a local mock: nothing here is an account, a credential or a real telephone
 * number, and nothing leaves the device except the one request the random-avatar service needs.
 */
public final class ProfileStore {
    private static final String PREFS = "profile_store";
    private static final String KEY_NAME = "name";
    private static final String KEY_PHONE = "phone";
    private static final String AVATAR_FILE = "profile_avatar.png";
    /** The copy kept on disk is capped here: the page never shows the picture larger than this. */
    private static final int AVATAR_MAX = 512;

    private static final String DEFAULT_NAME = "落陨";
    private static final String DEFAULT_PHONE = "159******20";

    /** The application context, handed over once at startup; null until then. */
    private static Context context;
    private static boolean loaded;
    private static Bitmap avatar;
    private static String name = DEFAULT_NAME;
    private static String phone = DEFAULT_PHONE;

    private ProfileStore() {
    }

    /**
     * Hands the store the context it persists through. Called once from the application, before any
     * page reads a field; the values kept from the last run are read back here.
     */
    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    /** The picture the user picked, or null to fall back to the reference's own cut-out. */
    public static Bitmap avatar() {
        load();
        return avatar;
    }

    public static void avatar(Bitmap value) {
        load();
        avatar = value;
        write(value);
    }

    public static String name() {
        load();
        return name;
    }

    public static void name(String value) {
        load();
        name = clean(value, DEFAULT_NAME);
        put(KEY_NAME, name);
    }

    public static String phone() {
        load();
        return phone;
    }

    public static void phone(String value) {
        load();
        phone = clean(value, DEFAULT_PHONE);
        put(KEY_PHONE, phone);
    }

    // ------------------------------------------------------------------ the copy that outlives the run

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences prefs = prefs();
        if (prefs != null) {
            name = clean(prefs.getString(KEY_NAME, null), DEFAULT_NAME);
            phone = clean(prefs.getString(KEY_PHONE, null), DEFAULT_PHONE);
        }
        avatar = read();
    }

    private static SharedPreferences prefs() {
        // 每个账号一套存档：默认那个账号沿用原来的名字，别的账号各自加后缀
        return context == null ? null
                : context.getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }

    /** 换账号时把这一份缓存丢掉，下一次问它读到的就是新账号的存档。 */
    public static void reset() {
        loaded = false;
        avatar = null;
        name = DEFAULT_NAME;
        phone = DEFAULT_PHONE;
    }

    private static void put(String key, String value) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(key, value).apply();
    }

    private static String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static File file() {
        return context == null ? null
                : new File(context.getFilesDir(), "profile_avatar" + Accounts.suffix() + ".png");
    }

    /**
     * Writes the picture the user picked. The page keeps showing it from memory either way; this is
     * what makes it still be there the next time the app is opened.
     */
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
            // the change still shows for this run; it just does not survive the next one
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

    /** The stored copy is scaled down to {@link #AVATAR_MAX}; anything smaller travels as it is. */
    private static Bitmap shrink(Bitmap value) {
        int longest = Math.max(value.getWidth(), value.getHeight());
        if (longest <= AVATAR_MAX) return value;
        float factor = AVATAR_MAX / (float) longest;
        int width = Math.max(1, Math.round(value.getWidth() * factor));
        int height = Math.max(1, Math.round(value.getHeight() * factor));
        return Bitmap.createScaledBitmap(value, width, height, true);
    }
}
