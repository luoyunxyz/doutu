package com.luoyun.doutu.features.zhi.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.luoyun.doutu.features.zhi.ui.ZhiHomeScreen;

import org.json.JSONArray;

/**
 * The 小支主页 fields the user may type over — the address beside the drop-down arrow, the 最近消息
 * label with its unread dot, the 芝麻分 and its 隐藏 switch, the 信用状态 caption, and the two
 * 最近消息 rows.
 *
 * <p>The page kept all of them in the activity's own {@code State}, so they were gone the moment the
 * app was swiped away from the recents list. They are written to the app's own preferences now and
 * read back the first time the page is built after the process starts.
 *
 * <p>每个账号一套存档（后缀来自 {@link Accounts#suffix()}）。滚动位置、下拉菜单开没开这类纯显示
 * 状态不存：那不是用户「改」的东西，重新打开应该是干净的。
 */
public final class ZhiHomeStore {
    private static final String PREFS = "zhi_home_store";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_NEW_MESSAGE = "new_message";
    private static final String KEY_UNREAD = "unread";
    private static final String KEY_SCORE = "score";
    private static final String KEY_HIDE_TAIL = "hide_score_tail";
    private static final String KEY_CREDIT = "credit_label";
    private static final String KEY_MESSAGES = "messages";

    private static Context context;
    private static boolean loaded;
    private static String address;
    private static String newMessage;
    private static Boolean unread;
    private static String score;
    private static Boolean hideTail;
    private static String creditLabel;
    private static String messagesJson;

    private ZhiHomeStore() {
    }

    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    /** 换账号时把这一份缓存丢掉，下一次问它读到的就是新账号的存档。 */
    public static void reset() {
        loaded = false;
        address = null;
        newMessage = null;
        unread = null;
        score = null;
        hideTail = null;
        creditLabel = null;
        messagesJson = null;
    }

    /** 把存档里的值铺回页面状态；没存过的字段保持 {@link ZhiHomeScreen.State} 的默认值。 */
    public static void apply(ZhiHomeScreen.State state) {
        load();
        if (state == null) return;
        if (notEmpty(address)) state.address = address;
        if (newMessage != null) state.newMessage = newMessage;
        if (unread != null) state.unread = unread;
        if (notEmpty(score)) state.score = score;
        if (hideTail != null) state.hideScoreTail = hideTail;
        if (notEmpty(creditLabel)) state.creditLabel = creditLabel;
        applyMessages(state);
    }

    /** 用户改完一处就整份写回去。 */
    public static void save(ZhiHomeScreen.State state) {
        if (state == null) return;
        load();
        address = state.address;
        newMessage = state.newMessage;
        unread = state.unread;
        score = state.score;
        hideTail = state.hideScoreTail;
        creditLabel = state.creditLabel;
        messagesJson = encodeMessages(state);
        SharedPreferences prefs = prefs();
        if (prefs != null) {
            prefs.edit()
                    .putString(KEY_ADDRESS, address)
                    .putString(KEY_NEW_MESSAGE, newMessage)
                    .putBoolean(KEY_UNREAD, unread)
                    .putString(KEY_SCORE, score)
                    .putBoolean(KEY_HIDE_TAIL, hideTail)
                    .putString(KEY_CREDIT, creditLabel)
                    .putString(KEY_MESSAGES, messagesJson)
                    .apply();
        }
    }

    /** 两行 最近消息：{标题, 内容, 时间}，按固定两行存，行数不变。 */
    private static String encodeMessages(ZhiHomeScreen.State state) {
        JSONArray rows = new JSONArray();
        try {
            for (String[] row : state.messages) {
                JSONArray one = new JSONArray();
                for (String cell : row) one.put(cell == null ? "" : cell);
                rows.put(one);
            }
        } catch (Exception ignored) {
            return null;
        }
        return rows.toString();
    }

    private static void applyMessages(ZhiHomeScreen.State state) {
        if (messagesJson == null || messagesJson.isEmpty()) return;
        try {
            JSONArray rows = new JSONArray(messagesJson);
            for (int i = 0; i < rows.length() && i < state.messages.length; i++) {
                JSONArray one = rows.optJSONArray(i);
                if (one == null) continue;
                for (int j = 0; j < one.length() && j < state.messages[i].length; j++) {
                    state.messages[i][j] = one.optString(j, state.messages[i][j]);
                }
            }
        } catch (Exception ignored) {
            // 存坏了就退回默认两行，不影响页面
        }
    }

    private static boolean notEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        address = prefs.getString(KEY_ADDRESS, null);
        newMessage = prefs.getString(KEY_NEW_MESSAGE, null);
        score = prefs.getString(KEY_SCORE, null);
        creditLabel = prefs.getString(KEY_CREDIT, null);
        messagesJson = prefs.getString(KEY_MESSAGES, null);
        if (prefs.contains(KEY_UNREAD)) unread = prefs.getBoolean(KEY_UNREAD, true);
        if (prefs.contains(KEY_HIDE_TAIL)) hideTail = prefs.getBoolean(KEY_HIDE_TAIL, true);
    }

    private static SharedPreferences prefs() {
        return context == null ? null
                : context.getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }
}
