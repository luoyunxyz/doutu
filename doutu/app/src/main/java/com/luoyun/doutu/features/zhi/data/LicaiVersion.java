package com.luoyun.doutu.features.zhi.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 理财 tab（小支主页 → 理财，也就是支宫格里的「财富页」）正在穿的是哪一档 —— 就是逆向出来那套
 * 「财富黑卡等级 / 皮肤」。
 *
 * <p>原 App 这一层不是版本升级、也不联网：本地存一个整数，按这个整数选一套皮肤。整数写在 SQLite
 * 的 {@code user.blackCard} 里（{@code 0 没有 / 1 成长版 / 2 V1 / 3 V2 / 4 V3}），设置里能改的也是
 * 它（文案「修改财富黑卡等级」「当前等级: 成长版/V1/V2/V3」）。这里照搬这套语义：五档、整数索引，
 * 存进 App 自己的 preferences（每个账号一套，见 {@link Accounts#suffix()}），关掉再打开还在。
 *
 * <p>0 普通版 是蓝色的财富页（{@code LicaiScreen}）；1 成长版 和 2/3/4 的 V1/V2/V3 都画在
 * {@code GrowthScreen} 那张财富黑卡页上，一档一档之间换的是黑卡徽标那块铭牌（成长版 / V1 / V2 / V3
 * 各自的底色和铭文）。
 */
public final class LicaiVersion {
    public static final int NORMAL = 0;
    public static final int GROWTH = 1;
    public static final int V1 = 2;
    public static final int V2 = 3;
    public static final int V3 = 4;

    /** 版本选择器里这五档的名字（对应原 App 的选项文案：普通蓝色主题 / V1成长版 / V1 / V2 / V3）。 */
    public static final String[] NAMES = {"普通版", "成长版", "V1", "V2", "V3"};
    /** 每档的「获得方式」，和原 App 设置页里那行提示一字不差。 */
    public static final String[] TIPS = {
            "普通蓝色主题",
            "获得方式：曾经有活动可免费领取",
            "获得方式：近30天日均资产30万即可达成",
            "获得方式：近30天日均资产50万即可达成",
            "获得方式：近30天日均资产100万即可达成"};
    /** 财富黑卡徽标铭牌上印的字；普通版没有黑卡，用 null。 */
    public static final String[] BADGE = {null, "成长版", "V1", "V2", "V3"};

    public static final class Theme {
        public final int value;
        public final String name;
        public final String tip;
        public final int foreground;
        public final int accent;
        public final int backgroundDrawable;
        public final int badgeDrawable;
        public final int cardDrawable;
        public final int holderDrawable;

        private Theme(int value, String name, String tip, int foreground, int accent,
                      int backgroundDrawable, int badgeDrawable, int cardDrawable,
                      int holderDrawable) {
            this.value = value;
            this.name = name;
            this.tip = tip;
            this.foreground = foreground;
            this.accent = accent;
            this.backgroundDrawable = backgroundDrawable;
            this.badgeDrawable = badgeDrawable;
            this.cardDrawable = cardDrawable;
            this.holderDrawable = holderDrawable;
        }
    }

    private static final Theme[] THEMES = {
            new Theme(NORMAL, NAMES[NORMAL], TIPS[NORMAL], 0xff202124, 0xff1677ff,
                    0, 0, 0, 0),
            new Theme(GROWTH, NAMES[GROWTH], TIPS[GROWTH], 0xff38271c, 0xffb88a62,
                    com.luoyun.doutu.R.drawable.licai_bg_growth_v1,
                    com.luoyun.doutu.R.drawable.licai_badge_growth,
                    com.luoyun.doutu.R.drawable.licai_card_growth,
                    com.luoyun.doutu.R.drawable.licai_holder_growth),
            new Theme(V1, NAMES[V1], TIPS[V1], 0xff38271c, 0xffc89f84,
                    com.luoyun.doutu.R.drawable.licai_bg_growth_v1,
                    com.luoyun.doutu.R.drawable.licai_badge_v1,
                    com.luoyun.doutu.R.drawable.licai_card_v1,
                    com.luoyun.doutu.R.drawable.licai_holder_v1),
            new Theme(V2, NAMES[V2], TIPS[V2], 0xff152860, 0xff9ea9c0,
                    com.luoyun.doutu.R.drawable.licai_bg_v2,
                    com.luoyun.doutu.R.drawable.licai_badge_v2,
                    com.luoyun.doutu.R.drawable.licai_card_v2,
                    com.luoyun.doutu.R.drawable.licai_holder_v2),
            new Theme(V3, NAMES[V3], TIPS[V3], 0xff0f0f11, 0xffc8a36f,
                    com.luoyun.doutu.R.drawable.licai_bg_v3,
                    com.luoyun.doutu.R.drawable.licai_badge_v3,
                    com.luoyun.doutu.R.drawable.licai_card_v3,
                    com.luoyun.doutu.R.drawable.licai_holder_v3)
    };

    private static final String PREFS = "licai_version_store";
    private static final String KEY_BLACK_CARD = "blackCard";

    private static Context context;
    private static boolean loaded;
    private static int current = NORMAL;

    private LicaiVersion() {
    }

    /** 进程启动时把 context 交给它；此后每一档都记在这份 preferences 里。 */
    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    public static int current() {
        load();
        return current;
    }

    /** 记住用户新选的这一档。 */
    public static void current(int value) {
        if (!drawn(value)) return;
        load();
        current = value;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(KEY_BLACK_CARD, value).apply();
    }

    /** 这一档是不是财富黑卡 —— 普通版以外都是。 */
    public static boolean blackCard(int value) {
        return value != NORMAL;
    }

    /** 这一档有没有自己的页面。五档现在都有：普通版一张，其余四档共用财富黑卡那张。 */
    public static boolean drawn(int value) {
        return value >= NORMAL && value <= V3;
    }

    public static String name(int value) {
        return drawn(value) ? NAMES[value] : NAMES[NORMAL];
    }

    public static String tip(int value) {
        return drawn(value) ? TIPS[value] : TIPS[NORMAL];
    }

    public static String badge(int value) {
        return drawn(value) ? BADGE[value] : null;
    }

    public static Theme theme() {
        return theme(current());
    }

    public static Theme theme(int value) {
        return THEMES[drawn(value) ? value : NORMAL];
    }

    /** 换账号时把这一份缓存丢掉，下一次问它读到的就是新账号的存档。 */
    public static void reset() {
        current = NORMAL;
        loaded = false;
    }

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        int value = prefs.getInt(KEY_BLACK_CARD, NORMAL);
        if (drawn(value)) current = value;
    }

    private static SharedPreferences prefs() {
        return context == null ? null
                : context.getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }
}
