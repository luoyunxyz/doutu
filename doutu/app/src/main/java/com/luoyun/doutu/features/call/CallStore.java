package com.luoyun.doutu.features.call;

import android.content.Context;
import android.content.SharedPreferences;

import com.luoyun.doutu.features.zhi.data.Accounts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 「模拟来电」设置页里的那一份设置：来电人（名字 + 头像）、铃声、什么时候响。
 *
 * <p>和别的页面一样**每个账号一套存档**（后缀来自 {@link Accounts#suffix()}）：换账号之后看到的是那个
 * 账号自己设过的来电。头像和铃声是文件，放在 {@code filesDir/call_assets/} 下，存档里只记文件名 ——
 * 这两个文件同时也是「微」模块那个网页要读的东西：WebView 会把它按
 * {@code https://fake-world.local/__call_assets/<文件名>} 提供给页面（见 WeiHomeActivity 的资源拦截），
 * 所以页面里那个来电界面能直接显示用户挑的头像、循环播放用户挑的铃声。
 */
public final class CallStore {
    private static final String PREFS = "call_store";
    private static final String KEY_NAME = "name";
    private static final String KEY_AVATAR_URL = "avatar_url";
    private static final String KEY_AVATAR_FILE = "avatar_file";
    private static final String KEY_RINGTONE_FILE = "ringtone_file";
    private static final String KEY_RINGTONE_LABEL = "ringtone_label";
    private static final String KEY_MODE = "time_mode";
    private static final String KEY_DELAY = "delay_minutes";
    private static final String KEY_HOUR = "at_hour";
    private static final String KEY_MINUTE = "at_minute";

    /** 用户自己挑的头像在存储里的文件名（**带账号后缀**，否则换个账号会看到上一个账号的头像）。 */
    public static String avatarFileName() {
        return "caller" + Accounts.suffix() + ".png";
    }

    /** 用户自己挑的铃声的文件名前缀（同样带账号后缀）。 */
    public static String ringtonePrefix() {
        return "ringtone" + Accounts.suffix();
    }

    /** 时间三选一：立刻响 / 多少分钟之后 / 指定的钟点。 */
    public static final int MODE_NOW = 0, MODE_DELAY = 1, MODE_AT = 2;

    private static Context context;
    private static boolean loaded;
    private static String name = "";
    private static String avatarUrl = "";
    private static String avatarFile = "";
    private static String ringtoneFile = "";
    private static String ringtoneLabel = "";
    private static int mode = MODE_NOW;
    private static int delayMinutes = 1;
    private static int atHour = -1, atMinute = 0;

    private CallStore() {
    }

    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    /** 换账号时把这一份缓存丢掉，下一次问它读到的就是新账号的存档。 */
    public static void reset() {
        loaded = false;
        name = "";
        avatarUrl = "";
        avatarFile = "";
        ringtoneFile = "";
        ringtoneLabel = "";
        mode = MODE_NOW;
        delayMinutes = 1;
        atHour = -1;
        atMinute = 0;
    }

    public static String name() {
        load();
        return name;
    }

    public static void name(String value) {
        load();
        name = value == null ? "" : value.trim();
        prefs().edit().putString(KEY_NAME, name).apply();
    }

    public static String avatarUrl() {
        load();
        return avatarUrl;
    }

    public static String avatarFile() {
        load();
        return avatarFile;
    }

    /**
     * 选定一个联系人：名字和头像一起换过来，并把之前挑过的本地头像清掉（否则本地的会盖住它）。
     */
    public static void pickContact(CallContact contact) {
        load();
        name = contact == null ? "" : contact.name;
        avatarUrl = contact == null ? "" : contact.avatarUrl;
        avatarFile = "";
        File old = assetFile(avatarFileName());
        if (old != null && old.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            old.delete();
        }
        prefs().edit()
                .putString(KEY_NAME, name)
                .putString(KEY_AVATAR_URL, avatarUrl)
                .putString(KEY_AVATAR_FILE, "")
                .apply();
    }

    /** 用户在设置页里改的名字（和选联系人分开，改完还要保留头像）。 */
    public static void rename(String value) {
        name(value);
    }

    public static String ringtoneFile() {
        load();
        return ringtoneFile;
    }

    public static String ringtoneLabel() {
        load();
        return ringtoneLabel;
    }

    public static void useDefaultRingtone() {
        load();
        // 换回内置铃声时把复制进来的那份删掉：留着既占地方，也容易让人以为还在用它
        File copied = assetFile(ringtoneFile);
        if (copied != null && copied.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            copied.delete();
        }
        ringtoneFile = "";
        ringtoneLabel = "";
        prefs().edit().putString(KEY_RINGTONE_FILE, "").putString(KEY_RINGTONE_LABEL, "").apply();
    }

    public static int timeMode() {
        load();
        return mode;
    }

    public static int delayMinutes() {
        load();
        return delayMinutes;
    }

    public static int atHour() {
        load();
        return atHour;
    }

    public static int atMinute() {
        load();
        return atMinute;
    }

    public static void time(int modeValue, int delay, int hour, int minute) {
        load();
        mode = modeValue;
        delayMinutes = Math.max(1, delay);
        atHour = hour;
        atMinute = minute;
        prefs().edit()
                .putInt(KEY_MODE, mode)
                .putInt(KEY_DELAY, delayMinutes)
                .putInt(KEY_HOUR, atHour)
                .putInt(KEY_MINUTE, atMinute)
                .apply();
    }

    /* ------------------------------ 文件 ------------------------------ */

    /** {@code filesDir/call_assets}，页面通过 {@code /__call_assets/} 读的就是这个目录。 */
    public static File assetDir() {
        File dir = new File(context.getFilesDir(), "call_assets");
        if (!dir.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    public static File assetFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;
        return new File(assetDir(), fileName);
    }

    /** 把用户挑的头像存成 {@link #avatarFileName()}，并记下文件名。 */
    public static boolean saveAvatar(InputStream in) {
        load();
        if (in == null) return false;
        File target = assetFile(avatarFileName());
        try (InputStream source = in; OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = source.read(buffer)) > 0) out.write(buffer, 0, read);
        } catch (Exception e) {
            return false;
        }
        avatarFile = target.getName();
        avatarUrl = "";
        prefs().edit().putString(KEY_AVATAR_FILE, avatarFile).putString(KEY_AVATAR_URL, "").apply();
        return true;
    }

    /** 把用户挑的铃声复制进 call_assets，返回它自己的显示名（选文件时那个名字）。 */
    public static boolean saveRingtone(InputStream in, String displayName, String extension) {
        load();
        if (in == null) return false;
        String ext = extension == null || extension.isEmpty() ? "mp3" : extension.toLowerCase();
        String fileName = ringtonePrefix() + "." + ext;
        File target = assetFile(fileName);
        try (InputStream source = in; OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = source.read(buffer)) > 0) out.write(buffer, 0, read);
        } catch (Exception e) {
            return false;
        }
        // 换扩展名时把上一份删掉（只删本账号的），免得目录里越攒越多
        String prefix = ringtonePrefix() + ".";
        File[] files = assetDir().listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().startsWith(prefix) && !file.getName().equals(fileName)) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
        ringtoneFile = fileName;
        ringtoneLabel = displayName == null || displayName.trim().isEmpty() ? fileName : displayName.trim();
        prefs().edit().putString(KEY_RINGTONE_FILE, ringtoneFile).putString(KEY_RINGTONE_LABEL, ringtoneLabel).apply();
        return true;
    }

    /** 页面里那个来电界面要用的头像地址：本地挑的优先，其次是联系人的，最后交给页面的兜底图。 */
    public static String avatarForPage() {
        load();
        if (!avatarFile.isEmpty()) return "https://fake-world.local/__call_assets/" + avatarFile;
        return avatarUrl;
    }

    /** 页面里那个来电界面要用的铃声地址；空字符串表示用页面内置的那首。 */
    public static String ringtoneForPage() {
        load();
        return ringtoneFile.isEmpty() ? "" : "https://fake-world.local/__call_assets/" + ringtoneFile;
    }

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences prefs = prefs();
        name = prefs.getString(KEY_NAME, "");
        avatarUrl = prefs.getString(KEY_AVATAR_URL, "");
        avatarFile = prefs.getString(KEY_AVATAR_FILE, "");
        ringtoneFile = prefs.getString(KEY_RINGTONE_FILE, "");
        ringtoneLabel = prefs.getString(KEY_RINGTONE_LABEL, "");
        mode = prefs.getInt(KEY_MODE, MODE_NOW);
        delayMinutes = Math.max(1, prefs.getInt(KEY_DELAY, 1));
        atHour = prefs.getInt(KEY_HOUR, -1);
        atMinute = prefs.getInt(KEY_MINUTE, 0);

        // 存档里记的是文件名，文件却可能已经不在了（删账号时清过、用户清过应用数据、
        // 或者换了账号）。那种情况下必须退回「内置铃声 / 联系人头像」，否则来电会拿着一个
        // 指向空文件的地址去放，等于没声音 —— 用户看到的就是「默认音频不对」。
        SharedPreferences.Editor edit = null;
        File avatar = assetFile(avatarFile);
        if (!avatarFile.isEmpty() && (avatar == null || !avatar.isFile())) {
            avatarFile = "";
            edit = prefs.edit().putString(KEY_AVATAR_FILE, "");
        }
        File ring = assetFile(ringtoneFile);
        if (!ringtoneFile.isEmpty() && (ring == null || !ring.isFile())) {
            ringtoneFile = "";
            ringtoneLabel = "";
            edit = (edit == null ? prefs.edit() : edit)
                    .putString(KEY_RINGTONE_FILE, "")
                    .putString(KEY_RINGTONE_LABEL, "");
        }
        if (edit != null) edit.apply();
    }

    private static SharedPreferences prefs() {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }
}
