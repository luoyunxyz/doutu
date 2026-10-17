package com.luoyun.doutu.features.zhi.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * The 财富黑卡理财师 who sits on the 市场 tab's card: what he is called and what he looks like.
 *
 * <p>The picture and the name are the two the user may change — tapping the card's avatar offers a
 * fresh set to pick from, tapping his name opens a text dialog — and both outlive the page and the
 * app: the name goes to the app's own preferences and the picture to its own files directory, read
 * back the first time this store is asked for anything after the process starts. The title under
 * the name, 财富黑卡理财师, is not his to change and is drawn by the page.
 *
 * <p>Nothing here is an account or a person: the name and the picture are local stand-ins, and the
 * only request that leaves the device is the one the random-avatar service needs.
 */
public final class AdvisorStore {
    private static final String PREFS = "advisor_store";
    private static final String KEY_NAME = "name";
    private static final String AVATAR_FILE = "advisor_avatar.png";
    /** The copy kept on disk is capped here: the card never shows the picture larger than this. */
    private static final int AVATAR_MAX = 512;

    private static final String DEFAULT_NAME = "赵军";

    /** The application context, handed over once at startup; null until then. */
    private static Context context;
    private static boolean loaded;
    private static Bitmap avatar;
    private static String name = DEFAULT_NAME;

    private AdvisorStore() {
    }

    /** Hands the store the context it persists through; the last run's values are read back here. */
    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    /** The picture the user picked, or null to fall back to the one cut out of the reference. */
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
        name = value == null || value.trim().isEmpty() ? DEFAULT_NAME : value.trim();
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(KEY_NAME, name).apply();
    }

    // ------------------------------------------------------------------ the copy that outlives the run

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences prefs = prefs();
        if (prefs != null) {
            String kept = prefs.getString(KEY_NAME, null);
            if (kept != null && !kept.trim().isEmpty()) name = kept.trim();
        }
        avatar = read();
    }

    private static SharedPreferences prefs() {
        // 每个账号一套存档，和别的仓库一样
        return context == null ? null
                : context.getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }

    /** 换账号时把这一份缓存丢掉，下一次问它读到的就是新账号的存档。 */
    public static void reset() {
        loaded = false;
        avatar = null;
        name = DEFAULT_NAME;
    }

    private static File file() {
        return context == null ? null
                : new File(context.getFilesDir(), "advisor_avatar" + Accounts.suffix() + ".png");
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

    private static Bitmap shrink(Bitmap value) {
        int longest = Math.max(value.getWidth(), value.getHeight());
        if (longest <= AVATAR_MAX) return value;
        float factor = AVATAR_MAX / (float) longest;
        int width = Math.max(1, Math.round(value.getWidth() * factor));
        int height = Math.max(1, Math.round(value.getHeight() * factor));
        return Bitmap.createScaledBitmap(value, width, height, true);
    }
}
