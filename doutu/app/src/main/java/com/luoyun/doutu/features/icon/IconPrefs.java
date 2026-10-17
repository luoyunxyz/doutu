package com.luoyun.doutu.features.icon;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 当前用的是哪一款应用图标（也就是启动器里显示的那个名字和那张图）。
 *
 * <p>真正把图标和名字换掉的是 {@link AppIconSwitcher}：它去开关清单里那几个
 * {@code activity-alias}。这里只记「用户最后挑的是哪一个」，所以底部弹窗重新打开时能一眼
 * 看出当前用的是哪一行；进程重启之后也还认得。
 */
final class IconPrefs {
    private static final String FILE = "app_icon";
    private static final String KEY_CHOICE = "choice";

    private IconPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** 当前挑中的那一款；没挑过就是默认的豆图。 */
    static AppIcon current(Context context) {
        return AppIcon.byKey(prefs(context).getString(KEY_CHOICE, AppIcon.DOUTU.key));
    }

    static void save(Context context, AppIcon icon) {
        prefs(context).edit().putString(KEY_CHOICE, icon.key).apply();
    }
}
