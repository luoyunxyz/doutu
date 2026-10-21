package com.luoyun.doutu.features.ali;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 支付宝通讯录：默认几条联系人，添加的存本地。 */
public final class AliContactStore {
    private static final String PREFS = "doutu_ali";
    private static final String KEY = "contacts";

    private AliContactStore() { }

    private static final String[] DEFAULTS = {
            "A|阿姨", "B|爸爸", "C|陈晨", "D|大鹏", "L|李雷",
            "M|妈妈", "W|王芳", "X|小明", "Z|张三"
    };

    public static Map<String, List<String>> grouped(Context c) {
        String raw = c.getSharedPreferences(PREFS, 0).getString(KEY, "");
        List<String> lines = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            for (String d : DEFAULTS) lines.add(d);
        } else {
            for (String line : raw.split("\n")) {
                if (!line.trim().isEmpty()) lines.add(line);
            }
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String line : lines) {
            int i = line.indexOf('|');
            if (i <= 0) continue;
            String letter = line.substring(0, i);
            String name = line.substring(i + 1);
            out.computeIfAbsent(letter, k -> new ArrayList<>()).add(name);
        }
        return out;
    }

    public static void add(Context c, String name) {
        Map<String, List<String>> groups = grouped(c);
        String letter = "N";
        if (!name.isEmpty()) {
            char ch = name.charAt(0);
            if (ch >= 'A' && ch <= 'Z') letter = String.valueOf(ch);
            else if (ch >= 'a' && ch <= 'z') letter = String.valueOf(Character.toUpperCase(ch));
        }
        groups.computeIfAbsent(letter, k -> new ArrayList<>()).add(name);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> e : groups.entrySet()) {
            for (String n : e.getValue()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(e.getKey()).append('|').append(n);
            }
        }
        c.getSharedPreferences(PREFS, 0).edit().putString(KEY, sb.toString()).apply();
    }
}
