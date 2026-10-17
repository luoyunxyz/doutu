package com.luoyun.doutu.features.zhi.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 余额页（小支主页 - 我的 - 余额）上被用户改过的数字和文字。
 *
 * <p>那一页上的每一个金额、每一段说明文字都是可以点开改的，改完 {@code BalanceScreen} 会立刻重画。
 * 页面自己的状态只活到这次打开为止，所以这里把改过的值收到 App 自己的 preferences 里，进程第一次
 * 被问到时就全部读回来；关掉 App 再从最近任务里打开，用户自己填的数还在。清掉 App 的数据，这里
 * 的东西一并清掉。
 *
 * <p>只记住「改过的」那些键：没动过的字段仍旧走 {@code BalanceScreen} / {@code BalanceActivity}
 * 里的默认值，将来默认值变了，没动过的数字也跟着变。
 */
public final class BalanceStore {
    private static final String PREFS = "balance_store";
    private static final String NUM = "n:";
    private static final String TEXT = "t:";

    private static Context context;
    private static boolean loaded;

    private static final Map<String, Double> numbers = new LinkedHashMap<>();
    private static final Map<String, String> texts = new LinkedHashMap<>();

    private BalanceStore() {
    }

    /** 把持久化用的 Context 交进来，并把上次留下的值读回来。由 {@code DoutuApp} 调用一次。 */
    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    /** 用户改过的数字，键与 {@code BalanceActivity.FIELDS} 一致；改过才在里面。 */
    public static Map<String, Double> numbers() {
        load();
        return numbers;
    }

    /** 用户改过的文字，改过才在里面。 */
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

    // ------------------------------------------------------------------ 盘上那一份

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
                    // 盘上那一条读不出来就当没改过
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
