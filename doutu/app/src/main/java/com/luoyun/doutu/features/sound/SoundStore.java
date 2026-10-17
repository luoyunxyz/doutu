package com.luoyun.doutu.features.sound;

import android.content.Context;
import android.content.SharedPreferences;

import com.luoyun.doutu.features.zhi.data.Accounts;

/**
 * 「模拟收款声音」页里那些设置，每个账号存一份。
 *
 * <p>原包的这一页把配置放在 shared_preferences 里（资料包 §4.5），这里照同样的思路，
 * 只是跟着本项目「一个账号一套存档」的规矩来（后缀取自 {@link Accounts#suffix()}）。
 */
public final class SoundStore {
    private static final String PREFS = "sound_store";
    private static final String KEY_STYLE = "style";          // 0 小支 / 1 小微
    private static final String KEY_RANDOM_STYLE = "random_style";
    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_SPEED = "speed";
    private static final String KEY_DELAY = "delay";
    private static final String KEY_RANGE = "range";          // -1 不定额 / 0 10-100 / 1 100-1000 / 2 1000-10000
    private static final String KEY_MODE = "mode";            // 0 一次 / 1 循环 / 2 无限

    public static final int STYLE_ZHI = 0, STYLE_WEI = 1;
    public static final int MODE_ONCE = 0, MODE_LOOP = 1, MODE_INFINITE = 2;
    /** 原包循环间隔的常量：5,000,000 微秒 = 5 秒。 */
    public static final int LOOP_GAP_MS = 5000;

    private static Context context;
    private static boolean loaded;
    private static int style = STYLE_ZHI;
    private static boolean randomStyle;
    private static String amount = "";
    private static float speed = 1f;
    private static int delay;
    private static int range = -1;
    private static int mode = MODE_ONCE;

    private SoundStore() {
    }

    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    public static void reset() {
        loaded = false;
        style = STYLE_ZHI;
        randomStyle = false;
        amount = "";
        speed = 1f;
        delay = 0;
        range = -1;
        mode = MODE_ONCE;
    }

    public static int style() {
        load();
        return style;
    }

    public static void style(int value) {
        load();
        style = value;
        prefs().edit().putInt(KEY_STYLE, style).apply();
    }

    public static boolean randomStyle() {
        load();
        return randomStyle;
    }

    public static void randomStyle(boolean value) {
        load();
        randomStyle = value;
        prefs().edit().putBoolean(KEY_RANDOM_STYLE, randomStyle).apply();
    }

    public static String amount() {
        load();
        return amount;
    }

    public static void amount(String value) {
        load();
        amount = value == null ? "" : value.trim();
        prefs().edit().putString(KEY_AMOUNT, amount).apply();
    }

    public static float speed() {
        load();
        return speed;
    }

    public static void speed(float value) {
        load();
        speed = value;
        prefs().edit().putFloat(KEY_SPEED, speed).apply();
    }

    public static int delay() {
        load();
        return delay;
    }

    public static void delay(int value) {
        load();
        delay = value;
        prefs().edit().putInt(KEY_DELAY, delay).apply();
    }

    public static int range() {
        load();
        return range;
    }

    public static void range(int value) {
        load();
        range = value;
        prefs().edit().putInt(KEY_RANGE, range).apply();
    }

    public static int mode() {
        load();
        return mode;
    }

    public static void mode(int value) {
        load();
        mode = value;
        prefs().edit().putInt(KEY_MODE, mode).apply();
    }

    /** 某个随机区间的两端（元）；{@code range} 越小额度越低。 */
    public static int[] rangeBounds(int index) {
        switch (index) {
            case 0: return new int[]{10, 100};
            case 1: return new int[]{100, 1000};
            case 2: return new int[]{1000, 10000};
            default: return null;
        }
    }

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences prefs = prefs();
        style = prefs.getInt(KEY_STYLE, STYLE_ZHI);
        randomStyle = prefs.getBoolean(KEY_RANDOM_STYLE, false);
        amount = prefs.getString(KEY_AMOUNT, "");
        speed = prefs.getFloat(KEY_SPEED, 1f);
        delay = prefs.getInt(KEY_DELAY, 0);
        range = prefs.getInt(KEY_RANGE, -1);
        mode = prefs.getInt(KEY_MODE, MODE_ONCE);
    }

    private static SharedPreferences prefs() {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }
}
