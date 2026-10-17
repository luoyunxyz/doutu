package com.luoyun.doutu.features.zhi.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付成功页上被用户改过的文字。
 *
 * <p>和别的页一样：页面默认就是参考图原样（整块切好贴出来的），只有用户点开某一行改成别的内容
 * 之后，才会用小色块盖掉原位置、再画上用户自己的值。这里只记「改过的」那些键，写进 App 自己的
 * preferences（每个账号一套，见 {@link Accounts#suffix()}），关掉 App 再打开还在。
 */
public final class PaySuccessStore {
    private static final String PREFS = "pay_success_store";
    private static final String TEXT = "t:";

    private static Context context;
    private static boolean loaded;

    private static final Map<String, String> texts = new LinkedHashMap<>();

    private PaySuccessStore() {
    }

    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    public static Map<String, String> texts() {
        load();
        return texts;
    }

    public static void text(String key, String value) {
        load();
        texts.put(key, value);
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(TEXT + key, value).apply();
    }

    public static void clearText(String key) {
        load();
        texts.remove(key);
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().remove(TEXT + key).apply();
    }

    /** 换账号时把这一份缓存丢掉，下一次问它读到的就是新账号的存档。 */
    public static void reset() {
        texts.clear();
        loaded = false;
    }

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String && key.startsWith(TEXT)) {
                texts.put(key.substring(TEXT.length()), (String) value);
            }
        }
    }

    private static SharedPreferences prefs() {
        return context == null ? null
                : context.getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }
}
