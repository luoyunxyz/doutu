package com.luoyun.doutu.features.call;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.ValueCallback;

import com.luoyun.doutu.features.wei.WeiHomeActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 来电设置页里「从通讯录选择来电人」那一排人是谁。
 *
 * <p>通讯录的真身在「微」模块那个网页里（页面自己把联系人存在 localStorage 的 {@code allProfiles}，
 * 就是它渲染通讯录用的那份数据）。这份名单没法从磁盘上直接读，但它**可以在页面里跑一段 JS 拿到**：
 * 「小微主页」的 WebView 在 MainActivity 启动时就预热好了、而且和这里是同一个进程，所以设置页直接
 * 问它要一份就行（见 {@link WeiHomeActivity#evaluateInPage}）。
 *
 * <p>拿到的名单会存一份到自己的 preferences 里，所以下一次打开设置页不用等页面、也不会空着；
 * 一次都没同步成功过（比如全新安装、还没进过微模块）就用下面这份兜底名单 —— 就是页面自带的那几位
 * 联系人，连头像链接都是它们自己的，所以点进去看到的人跟通讯录里一模一样。
 */
public final class CallContacts {
    private static final String PREFS = "call_contacts";
    private static final String KEY_LIST = "list";

    /** 页面自带的联系人（网页里 allProfiles 的默认那几位），同步不到时顶上。 */
    private static final String[][] DEFAULTS = {
            {"link", "林克", ""},
            {"zelda", "老婆", ""},
            {"kirby", "星之笨比", ""},
            {"mario", "大叔", ""},
            {"doutu", "微信用户", ""},
    };

    private CallContacts() {
    }

    /** 上一次同步到的通讯录；一次都没同步过就是兜底名单。 */
    public static List<CallContact> cached(Context context) {
        List<CallContact> saved = parse(prefs(context).getString(KEY_LIST, null));
        return saved.isEmpty() ? defaults() : saved;
    }

    /**
     * 问一次页面里的真实通讯录，拿到就存下来并回调 true；页面还没起来就回调 false（调用方用缓存）。
     * 回调在主线程。
     */
    public static void refresh(final Context context, final Result result) {
        final Context app = context.getApplicationContext();
        WeiHomeActivity.evaluateInPage(
                "(function(){try{return localStorage.getItem('allProfiles')||''}catch(e){return ''}})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        List<CallContact> list = parse(unquote(value));
                        if (list.isEmpty()) {
                            if (result != null) result.onContacts(false);
                            return;
                        }
                        prefs(app).edit().putString(KEY_LIST, encode(list)).apply();
                        if (result != null) result.onContacts(true);
                    }
                });
    }

    /** evaluateJavascript 回的是一个 JSON 字符串字面量，这里把外层引号解开。 */
    private static String unquote(String value) {
        if (value == null || value.length() < 2 || "null".equals(value)) return "";
        try {
            return new JSONArray("[" + value + "]").optString(0, "");
        } catch (Exception e) {
            return value;
        }
    }

    private static List<CallContact> parse(String json) {
        List<CallContact> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return out;
        try {
            JSONArray rows = new JSONArray(json);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                // 显示名和页面里 callContact() 的取法一致：备注优先
                String name = row.optString("remark", "").trim();
                if (name.isEmpty()) name = row.optString("nickname", "").trim();
                if (name.isEmpty()) name = row.optString("name", "").trim();
                if (name.isEmpty()) continue;
                String avatar = row.optString("avatarInfo", "").trim();
                if (avatar.isEmpty()) avatar = row.optString("avatar", "").trim();
                // 页面给的多数是它自己那个已经解析不出来的 CDN 地址：换成一张本地头像，
                // 这样设置页里显示得出来、交给来电界面也一样显示得出来（见 CallImages）
                if (avatar.isEmpty() || CallImages.isRemote(avatar)) {
                    avatar = CallImages.poolAvatar(i);
                }
                String id = row.optString("id", String.valueOf(i));
                out.add(new CallContact(id, name, avatar));
                if (out.size() >= 40) break;
            }
        } catch (Exception ignored) {
            // 存坏了就当没有，调用方会退回兜底名单
        }
        return out;
    }

    private static String encode(List<CallContact> list) {
        JSONArray rows = new JSONArray();
        for (CallContact contact : list) {
            JSONObject row = new JSONObject();
            try {
                row.put("id", contact.id);
                row.put("name", contact.name);
                row.put("avatar", contact.avatarUrl);
            } catch (Exception ignored) {
                continue;
            }
            rows.put(row);
        }
        return rows.toString();
    }

    private static List<CallContact> defaults() {
        List<CallContact> out = new ArrayList<>();
        for (int i = 0; i < DEFAULTS.length; i++) {
            String[] row = DEFAULTS[i];
            out.add(new CallContact(row[0], row[1], CallImages.poolAvatar(i)));
        }
        return out;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 同步结果：true 表示拿到了页面里的真通讯录。 */
    public interface Result {
        void onContacts(boolean fromPage);
    }
}
