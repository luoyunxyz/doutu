package com.luoyun.doutu.features.qq;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** QQ 主页的聊天列表：创建聊天后存起来，主页画出来。 */
public final class QqChatStore {
    private static final String PREFS = "doutu_qq";
    private static final String KEY = "chats";

    private QqChatStore() { }

    public static final class Chat {
        public final String name;
        public final String time;
        Chat(String name, String time) { this.name = name; this.time = time; }
    }

    public static List<Chat> chats(Context c) {
        String raw = c.getSharedPreferences(PREFS, 0).getString(KEY, "");
        List<Chat> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String line : raw.split("\n")) {
            int i = line.indexOf('|');
            if (i > 0) out.add(new Chat(line.substring(0, i), line.substring(i + 1)));
        }
        return out;
    }

    public static void add(Context c, String name, String time) {
        List<Chat> list = chats(c);
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('|').append(time);
        for (Chat chat : list) {
            if (chat.name.equals(name)) continue;
            sb.append('\n').append(chat.name).append('|').append(chat.time);
        }
        c.getSharedPreferences(PREFS, 0).edit().putString(KEY, sb.toString()).apply();
    }
}
