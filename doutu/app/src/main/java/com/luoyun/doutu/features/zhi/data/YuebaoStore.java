package com.luoyun.doutu.features.zhi.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 余额宝页（小支主页 - 我的 - 余额 - 余额宝）上被用户改过的数字和文字。
 *
 * <p>和 {@link BalanceStore} 一样：只记住「改过的」那些键，写进 App 自己的 preferences，
 * 进程第一次被问到时就全部读回来，所以关掉 App 再打开，用户自己填的数还在。
 */
public final class YuebaoStore {
    private static final String PREFS = "yuebao_store";
    private static final String NUM = "n:";
    private static final String TEXT = "t:";

    private static Context context;
    private static boolean loaded;

    private static final Map<String, Double> numbers = new LinkedHashMap<>();
    private static final Map<String, String> texts = new LinkedHashMap<>();

    private YuebaoStore() {
    }

    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    public static Map<String, Double> numbers() {
        load();
        return numbers;
    }

    public static Map<String, String> texts() {
        load();
        return texts;
    }

    public static void number(String key, double value) {
        load();
        numbers.put(key, value);
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(NUM + key, Double.toString(value)).apply();
    }

    public static void clearNumber(String key) {
        load();
        numbers.remove(key);
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().remove(NUM + key).apply();
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

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!(value instanceof String)) continue;
            if (key.startsWith(NUM)) {
                try {
                    numbers.put(key.substring(NUM.length()), Double.parseDouble((String) value));
                } catch (NumberFormatException ignored) {
                    // 读不出来就当没改过
                }
            } else if (key.startsWith(TEXT)) {
                texts.put(key.substring(TEXT.length()), (String) value);
            }
        }
    }

    /** 换账号时把这一份缓存丢掉，下一次问它读到的就是新账号的存档。 */
    public static void reset() {
        numbers.clear();
        texts.clear();
        loaded = false;
    }

    private static SharedPreferences prefs() {
        // 每个账号一套存档：默认那个账号沿用原来的名字，别的账号各自加后缀
        return context == null ? null
                : context.getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }
}
