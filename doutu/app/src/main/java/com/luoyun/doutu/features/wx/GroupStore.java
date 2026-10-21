package com.luoyun.doutu.features.wx;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 原生群聊数据：群名、显示人数、成员。存在 SharedPreferences，杀进程后仍在。 */
public final class GroupStore {
    public static final class Group {
        public String id = "";
        public String name = "再歪一点";
        public int count = 100;
        public final List<String> members = new ArrayList<>();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("wx_groups", Context.MODE_PRIVATE);
    }

    public static List<Group> all(Context c) {
        List<Group> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("groups", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Group g = new Group();
                g.id = o.optString("id", "");
                g.name = o.optString("name", "再歪一点");
                g.count = o.optInt("count", 100);
                JSONArray m = o.optJSONArray("members");
                if (m != null) {
                    for (int k = 0; k < m.length(); k++) g.members.add(m.optString(k, ""));
                }
                out.add(g);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public static Group get(Context c, String id) {
        for (Group g : all(c)) if (g.id.equals(id)) return g;
        return null;
    }

    public static void save(Context c, Group group) {
        List<Group> list = all(c);
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(group.id)) {
                list.set(i, group);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(0, group);
        JSONArray arr = new JSONArray();
        try {
            for (Group g : list) {
                JSONObject o = new JSONObject();
                o.put("id", g.id);
                o.put("name", g.name);
                o.put("count", g.count);
                JSONArray m = new JSONArray();
                for (String s : g.members) m.put(s);
                o.put("members", m);
                arr.put(o);
            }
        } catch (Exception ignored) { }
        prefs(c).edit().putString("groups", arr.toString()).apply();
    }
}
