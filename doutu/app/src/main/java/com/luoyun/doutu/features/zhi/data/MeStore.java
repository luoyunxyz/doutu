package com.luoyun.doutu.features.zhi.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.luoyun.doutu.features.zhi.ui.MeScreen;

/**
 * The one thing on the 我的 tab the user can put away: the 完成身份 strip and its own ✕.
 *
 * <p>It used to come back every time the page was built, so a strip the user had dismissed was
 * waiting for them again after the app was swiped away from the recents list. The dismissal is
 * written to the app's own preferences now.
 *
 * <p>每个账号一套存档（后缀来自 {@link Accounts#suffix()}）。
 */
public final class MeStore {
    private static final String PREFS = "me_store";
    private static final String KEY_BANNER = "banner";

    private static Context context;
    private static boolean loaded;
    private static Boolean banner;

    private MeStore() {
    }

    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    /** 换账号时把这一份缓存丢掉，下一次问它读到的就是新账号的存档。 */
    public static void reset() {
        loaded = false;
        banner = null;
    }

    /** 把存档里的值铺回页面状态；没存过的字段保持 {@link MeScreen.State} 的默认值。 */
    public static void apply(MeScreen.State state) {
        load();
        if (state == null || banner == null) return;
        state.banner = banner;
    }

    public static void banner(boolean shown) {
        load();
        banner = shown;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_BANNER, shown).apply();
    }

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences prefs = prefs();
        if (prefs != null && prefs.contains(KEY_BANNER)) banner = prefs.getBoolean(KEY_BANNER, true);
    }

    private static SharedPreferences prefs() {
        return context == null ? null
                : context.getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }
}
