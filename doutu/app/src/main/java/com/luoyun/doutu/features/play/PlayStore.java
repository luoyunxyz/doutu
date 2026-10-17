package com.luoyun.doutu.features.play;

import android.content.Context;
import android.content.SharedPreferences;

import com.luoyun.doutu.features.zhi.data.Accounts;

/**
 * 「娱乐」页上那几个开关的状态，每个账号存一份。
 *
 * <p>现在只有一个：**撩妹加V**（娱乐页 → 乐趣挑逗）。打开它以后，微模块里点「扫一扫」
 * 就不再进付款页，而是弹一句「摄像头不可用」—— 这正是这一格的文案
 * 「假装摄像头损坏，去购物加老板(娘)的微信~」要做的事。
 *
 * <p>开关状态必须留住：产品要求「用户在客户端里改过的一切都要留下」，
 * 杀掉后台再进来这一格还得是打开的样子。
 */
public final class PlayStore {
    private static final String PREFS = "play_store";
    private static final String KEY_CAMERA_BROKEN = "camera_broken";

    private static Context context;
    private static boolean loaded;
    private static boolean cameraBroken;

    private PlayStore() {
    }

    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    public static void reset() {
        loaded = false;
        cameraBroken = false;
    }

    /** 「撩妹加V」是不是开着 —— 开着就当作摄像头坏了。 */
    public static boolean cameraBroken() {
        load();
        return cameraBroken;
    }

    public static void cameraBroken(boolean value) {
        load();
        cameraBroken = value;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_CAMERA_BROKEN, value).apply();
    }

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences prefs = prefs();
        if (prefs != null) cameraBroken = prefs.getBoolean(KEY_CAMERA_BROKEN, false);
    }

    private static SharedPreferences prefs() {
        return context == null ? null
                : context.getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }
}
