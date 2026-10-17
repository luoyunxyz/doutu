package com.luoyun.doutu.features.zhima.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.luoyun.doutu.features.zhi.data.Accounts;
import com.luoyun.doutu.features.zhima.ui.ZhimaCreditScreen;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Everything on the 芝麻信用 page the user may change: the eleven captions on the two credit cards,
 * and the feed board below them — which module sits in which slot of either column.
 *
 * <p>Both used to live in the activity's own {@code State}, so a swipe away from the recents list
 * put every edited figure and the whole rearranged board back to the reference. They are written to
 * the app's own preferences now and read back the first time the page is built after the process
 * starts.
 *
 * <p>每个账号一套存档（后缀来自 {@link Accounts#suffix()}）。滚动位置、轮播偏移、企业卡展开没展开
 * 这类纯显示状态不存。
 */
public final class ZhimaStore {
    private static final String PREFS = "zhima_store";

    /** 十一个可编辑文案：存档键 → 状态里那个字段。两边名字一一对应，方便照着看。 */
    private static final String[] TEXT_KEYS = {
            "pLabel", "pScore", "pQuota", "pDiscount", "pPrivilege",
            "eLabel", "eScore", "eCompany", "eGo", "eQuota", "ePrivilege",
    };
    private static final String KEY_BOARD = "board";

    private static Context context;
    private static boolean loaded;
    private static final java.util.Map<String, String> texts = new java.util.HashMap<>();
    private static String boardJson;

    private ZhimaStore() {
    }

    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    /** 换账号时把这一份缓存丢掉，下一次问它读到的就是新账号的存档。 */
    public static void reset() {
        loaded = false;
        texts.clear();
        boardJson = null;
    }

    /** 把存档里的值铺回页面状态；没存过的字段保持 {@link ZhimaCreditScreen.State} 的默认值。 */
    public static void apply(ZhimaCreditScreen.State state) {
        load();
        if (state == null) return;
        for (String key : TEXT_KEYS) {
            String value = texts.get(key);
            if (value == null || value.isEmpty()) continue;
            switch (key) {
                case "pLabel": state.pLabel = value; break;
                case "pScore": state.pScore = value; break;
                case "pQuota": state.pQuota = value; break;
                case "pDiscount": state.pDiscount = value; break;
                case "pPrivilege": state.pPrivilege = value; break;
                case "eLabel": state.eLabel = value; break;
                case "eScore": state.eScore = value; break;
                case "eCompany": state.eCompany = value; break;
                case "eGo": state.eGo = value; break;
                case "eQuota": state.eQuota = value; break;
                case "ePrivilege": state.ePrivilege = value; break;
                default: break;
            }
        }
        applyBoard(state);
    }

    /** 用户改完一处就整份写回去。 */
    public static void save(ZhimaCreditScreen.State state) {
        if (state == null) return;
        load();
        texts.clear();
        texts.put("pLabel", state.pLabel);
        texts.put("pScore", state.pScore);
        texts.put("pQuota", state.pQuota);
        texts.put("pDiscount", state.pDiscount);
        texts.put("pPrivilege", state.pPrivilege);
        texts.put("eLabel", state.eLabel);
        texts.put("eScore", state.eScore);
        texts.put("eCompany", state.eCompany);
        texts.put("eGo", state.eGo);
        texts.put("eQuota", state.eQuota);
        texts.put("ePrivilege", state.ePrivilege);
        boardJson = encodeBoard(state);
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : TEXT_KEYS) editor.putString(key, texts.get(key));
        editor.putString(KEY_BOARD, boardJson);
        editor.apply();
    }

    /** 两列分别是 [{key, h}, …]；key 为 null 表示那一格是空的（用户删掉了模块）。 */
    private static String encodeBoard(ZhimaCreditScreen.State state) {
        try {
            JSONObject board = new JSONObject();
            board.put("left", encodeColumn(state.left));
            board.put("right", encodeColumn(state.right));
            return board.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JSONArray encodeColumn(List<ZhimaCreditScreen.Slot> slots) {
        JSONArray out = new JSONArray();
        for (ZhimaCreditScreen.Slot slot : slots) {
            JSONObject one = new JSONObject();
            try {
                one.put("key", slot.key == null ? JSONObject.NULL : slot.key);
                one.put("h", (double) slot.h);
            } catch (Exception ignored) {
                // 单个格子存不进去就跳过
            }
            out.put(one);
        }
        return out;
    }

    private static void applyBoard(ZhimaCreditScreen.State state) {
        if (boardJson == null || boardJson.isEmpty()) return;
        try {
            JSONObject board = new JSONObject(boardJson);
            applyColumn(state.left, board.optJSONArray("left"));
            applyColumn(state.right, board.optJSONArray("right"));
        } catch (Exception ignored) {
            // 存坏了就退回参考图那一版模块板
        }
    }

    private static void applyColumn(List<ZhimaCreditScreen.Slot> slots, JSONArray rows) {
        if (rows == null) return;
        // 行数变了（用户加过模块）就按存档重建；只是换了模块就原地改
        while (slots.size() > rows.length()) slots.remove(slots.size() - 1);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject one = rows.optJSONObject(i);
            if (one == null) continue;
            String key = one.isNull("key") ? null : one.optString("key", null);
            float h = (float) one.optDouble("h", 0d);
            if (i < slots.size()) {
                slots.get(i).key = key;
                if (h > 0f) slots.get(i).h = h;
            } else {
                if (h <= 0f) {
                    ZhimaCreditScreen.Module module = key == null ? null : ZhimaCreditScreen.module(key);
                    h = module == null ? 780f : module.h;
                }
                slots.add(new ZhimaCreditScreen.Slot(key, h));
            }
        }
    }

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        for (String key : TEXT_KEYS) {
            String value = prefs.getString(key, null);
            if (value != null) texts.put(key, value);
        }
        boardJson = prefs.getString(KEY_BOARD, null);
    }

    private static SharedPreferences prefs() {
        return context == null ? null
                : context.getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }
}
