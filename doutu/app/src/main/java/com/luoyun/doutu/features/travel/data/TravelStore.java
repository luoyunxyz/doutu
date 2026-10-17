package com.luoyun.doutu.features.travel.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.luoyun.doutu.features.travel.ui.TravelScreen;
import com.luoyun.doutu.features.zhi.data.Accounts;

/**
 * The 出行 page's three editable fields — the address and the two cities the route row carries.
 *
 * <p>They used to live only in the activity's own {@code State}, so the moment the app was swiped
 * away from the recents list and opened again the page fell back to the reference's 恩施 / 北京 /
 * 杭州. Everything the user types here is now written to the app's own preferences and read back
 * the first time the page asks for it after the process starts.
 *
 * <p>每个账号一套存档（后缀来自 {@link Accounts#suffix()}），和别的页面一致：换账号看到的是那个
 * 账号自己改过的地址与城市。
 */
public final class TravelStore {
    private static final String PREFS = "travel_store";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_FROM = "from";
    private static final String KEY_TO = "to";

    private static Context context;
    private static boolean loaded;
    private static String address;
    private static String from;
    private static String to;

    private TravelStore() {
    }

    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    /** 换账号时把这一份缓存丢掉，下一次问它读到的就是新账号的存档。 */
    public static void reset() {
        loaded = false;
        address = null;
        from = null;
        to = null;
    }

    /** 把存档里的值铺回页面状态；没存过的字段保持 {@link TravelScreen.State} 的默认值。 */
    public static void apply(TravelScreen.State state) {
        load();
        if (state == null) return;
        if (address != null && !address.isEmpty()) state.address = address;
        if (from != null && !from.isEmpty()) state.from = from;
        if (to != null && !to.isEmpty()) state.to = to;
    }

    /** 用户改完一处就整份写回去。 */
    public static void save(TravelScreen.State state) {
        if (state == null) return;
        load();
        address = state.address;
        from = state.from;
        to = state.to;
        SharedPreferences prefs = prefs();
        if (prefs != null) {
            prefs.edit()
                    .putString(KEY_ADDRESS, address)
                    .putString(KEY_FROM, from)
                    .putString(KEY_TO, to)
                    .apply();
        }
    }

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        address = prefs.getString(KEY_ADDRESS, null);
        from = prefs.getString(KEY_FROM, null);
        to = prefs.getString(KEY_TO, null);
    }

    private static SharedPreferences prefs() {
        return context == null ? null
                : context.getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }
}
