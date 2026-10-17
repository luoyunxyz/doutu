package com.luoyun.doutu.features.zhi.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The three things the 小荷包 page lets the user change — the name of the purse (the caption on its
 * card, 生活费 by default), its total and the picture beside it.
 *
 * <p>All three outlive the page and the run: the name and the total go to this module's own
 * preferences and the picture to its own file, and they are read back the first time the store is
 * asked for anything after the process starts. Each account keeps its own set ({@link
 * Accounts#suffix()}), so switching accounts switches purses.
 *
 * <p>The page is a local mock: the total is a number the user typed, there is no account behind it
 * and nothing leaves the device except the one request the random-avatar service needs.
 */
public final class PurseStore {
    private static final String PREFS = "purse_store";
    private static final String KEY_NAME = "name";
    private static final String KEY_TOTAL = "total";
    private static final String KEY_COUNT = "avatars";
    private static final String AVATAR_FILE = "purse_avatar_";
    /** The copy kept on disk is capped here: the page never shows the picture larger than this. */
    private static final int AVATAR_MAX = 512;
    /** 头像那一行最多放这么多个：再宽就压到右边的「邀请他人加入」了。 */
    public static final int MAX_AVATARS = 5;

    public static final String DEFAULT_NAME = "生活费";
    public static final String DEFAULT_TOTAL = "0.00";

    private static Context context;
    private static boolean loaded;
    private static final List<Bitmap> avatars = new ArrayList<>();
    private static String name = DEFAULT_NAME;
    private static String total = DEFAULT_TOTAL;

    private PurseStore() {
    }

    /** Hands the store the context it persists through; the last run's values are read back here. */
    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
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

    /** The total as the page prints it: two decimals, no grouping. */
    public static String total() {
        load();
        return total;
    }

    public static void total(String value) {
        load();
        total = money(value);
        put(KEY_TOTAL, total);
    }

    /**
     * 头像那一行：第一个是小荷包自己的，后面几个是点 + 加进来的。加不进去（已经满了）返回 false。
     */
    public static int avatarCount() {
        load();
        return avatars.size();
    }

    /** 第 index 个头像，没有就是 null（第一个一定有，加进来的那几个也都有）。 */
    public static Bitmap avatar(int index) {
        load();
        return index >= 0 && index < avatars.size() ? avatars.get(index) : null;
    }

    /** 换掉第 index 个头像：点哪个头像就是改哪个。 */
    public static void avatar(int index, Bitmap value) {
        load();
        if (index < 0 || index >= avatars.size()) return;
        avatars.set(index, value);
        write(index, value);
    }

    /** 在末尾加一个新的头像（+ 那个圆点），加满了返回 false。 */
    public static boolean addAvatar(Bitmap value) {
        load();
        if (avatars.size() >= MAX_AVATARS) return false;
        avatars.add(value);
        write(avatars.size() - 1, value);
        put(KEY_COUNT, String.valueOf(avatars.size()));
        return true;
    }

    /** 换账号时把这一份缓存丢掉，下一次问它读到的就是新账号的存档。 */
    public static void reset() {
        loaded = false;
        avatars.clear();
        name = DEFAULT_NAME;
        total = DEFAULT_TOTAL;
    }

    /** 用户敲进来的金额：留下数字和小数点，最多两位小数，空的按 0.00 算。 */
    public static String money(String value) {
        if (value == null) return DEFAULT_TOTAL;
        StringBuilder digits = new StringBuilder();
        boolean dot = false;
        for (char ch : value.trim().toCharArray()) {
            if (ch >= '0' && ch <= '9') {
                if (dot && digits.length() - digits.indexOf(".") > 2) continue;
                digits.append(ch);
            } else if ((ch == '.' || ch == '。') && !dot && digits.length() > 0) {
                dot = true;
                digits.append('.');
            }
        }
        if (digits.length() == 0) return DEFAULT_TOTAL;
        String text = digits.toString();
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        int cut = text.indexOf('.');
        if (cut >= 0) {
            while (text.length() - cut > 3) text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    // ------------------------------------------------------------------ 留在机器上的那一份

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences prefs = prefs();
        int count = 1;
        if (prefs != null) {
            name = clean(prefs.getString(KEY_NAME, null), DEFAULT_NAME);
            total = money(prefs.getString(KEY_TOTAL, null));
            count = Math.max(1, Math.min(MAX_AVATARS,
                    parseInt(prefs.getString(KEY_COUNT, null), 1)));
        }
        avatars.clear();
        // 第一个是参考图自带的那张（读不到就让页面用抠下来的那张）；后面几个是用户自己加的，读不到就不放
        for (int i = 0; i < count; i++) {
            Bitmap picture = read(i);
            if (i == 0) {
                avatars.add(picture);
            } else if (picture != null) {
                avatars.add(picture);
            }
        }
        if (avatars.isEmpty()) avatars.add(null);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static SharedPreferences prefs() {
        return context == null ? null
                : context.getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }

    private static void put(String key, String value) {
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(key, value).apply();
    }

    private static String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static File file(int index) {
        return context == null ? null
                : new File(context.getFilesDir(), AVATAR_FILE + index + Accounts.suffix() + ".png");
    }

    private static void write(int index, Bitmap value) {
        File target = file(index);
        if (target == null) return;
        if (value == null) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            return;
        }
        try (OutputStream out = new FileOutputStream(target)) {
            value.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception ignored) {
            // 写不进去就只留在内存里，页面照样显示
        }
    }

    private static Bitmap read(int index) {
        File source = file(index);
        if (source == null || !source.isFile()) return null;
        try (InputStream in = new java.io.FileInputStream(source)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            Bitmap raw = BitmapFactory.decodeStream(in, null, options);
            if (raw == null) return null;
            int longest = Math.max(raw.getWidth(), raw.getHeight());
            if (longest <= AVATAR_MAX) return raw;
            float scale = AVATAR_MAX / (float) longest;
            return Bitmap.createScaledBitmap(raw, Math.round(raw.getWidth() * scale),
                    Math.round(raw.getHeight() * scale), true);
        } catch (Exception e) {
            return null;
        }
    }
}
