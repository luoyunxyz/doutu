package com.luoyun.doutu.features.album;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** 团队名册：创建团队页写、我的团队页读，存 SharedPreferences。 */
public final class TeamStore {
    private static final String PREFS = "doutu_album";
    private static final String KEY = "teams";

    private TeamStore() { }

    public static List<String> teams(Context c) {
        String raw = c.getSharedPreferences(PREFS, 0).getString(KEY, "");
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String s : raw.split("\n")) {
            if (!s.trim().isEmpty()) out.add(s.trim());
        }
        return out;
    }

    public static void add(Context c, String name) {
        List<String> list = teams(c);
        if (list.contains(name)) return;
        list.add(name);
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(s);
        }
        c.getSharedPreferences(PREFS, 0).edit().putString(KEY, sb.toString()).apply();
    }
}
