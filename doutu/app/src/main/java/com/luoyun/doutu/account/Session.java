package com.luoyun.doutu.account;

import android.content.Context;
import android.content.SharedPreferences;

import com.luoyun.doutu.BuildConfig;

/**
 * 登录会话与会员状态的本地存储。
 *
 * 账号标识有两个：**用户名**（注册时自己起的，可改）与**手机号**，
 * 两者都能用来登录（服务端按用户名忽略大小写查，查不到再按手机号查）。
 * 界面上优先显示用户名（{@link #displayName()}）。
 *
 * 服务端地址来自编译期注入的 {@code BuildConfig.API_BASE}（**只允许域名**，见 app/build.gradle）：
 * 正式包连 HTTPS 域名，客户端里不存 IP、也不给用户改地址的口子；调试包才允许内网地址与临时切换。
 *
 * 会员状态（是否开通）只决定「有没有水印」，不影响能不能用；
 * 未开通时整机会叠加斜向水印遮罩（见 {@link WatermarkOverlay}）。
 */
public final class Session {

    private static final String PREF = "doutu_account";

    private static SharedPreferences prefs;

    private Session() {
    }

    public static void attach(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        // 先把上次的横幅配置读回来，首屏就不必等网络
        restoreBanners();
    }

    private static SharedPreferences p() {
        return prefs;
    }

    /* ------------------------------ 服务端地址 ------------------------------ */

    /**
     * 服务端地址：**编译期写死的域名**（BuildConfig.API_BASE）。
     * 调试包允许被 {@link #setBaseUrl} 临时改写（真机联调时换机器用），正式包永远返回编译期那个值。
     */
    public static String baseUrl() {
        String built = BuildConfig.API_BASE == null ? "" : BuildConfig.API_BASE.trim();
        if (!BuildConfig.IS_DEV_BUILD) return built;
        String v = p() == null ? null : p().getString("base_url", null);
        return (v == null || v.isEmpty()) ? built : v.replaceAll("/+$", "");
    }

    /** 只允许调试包调用；正式包里是空实现（客户端不能被改成连别的地址）。 */
    public static void setBaseUrl(String url) {
        if (!BuildConfig.IS_DEV_BUILD) return;
        if (p() != null) p().edit().putString("base_url", url == null ? "" : url.trim()).apply();
    }

    /** 当前是不是正式包（用来决定要不要显示「长按改地址」这类调试入口）。 */
    public static boolean isDevBuild() {
        return BuildConfig.IS_DEV_BUILD;
    }

    /** 当前连的是不是 HTTPS（正式包必须为 true）。 */
    public static boolean isSecure() {
        return baseUrl().startsWith("https://");
    }

    /* ------------------------------ 会话字段 ------------------------------ */

    public static String phone() {
        return p() == null ? "" : p().getString("phone", "");
    }

    public static String token() {
        return p() == null ? "" : p().getString("token", "");
    }

    public static String signKey() {
        return p() == null ? "" : p().getString("sign_key", "");
    }

    public static String nickname() {
        String v = p() == null ? null : p().getString("nickname", null);
        return (v == null || v.isEmpty()) ? phone() : v;
    }

    /** 用户名（注册时填的登录标识，可在「我的」页修改）。 */
    public static String username() {
        return p() == null ? "" : p().getString("username", "");
    }

    /**
     * 界面上显示的名字：用户名优先，没登录时给一句引导。
     * 用户名与手机号都可能为空（老数据），逐级兜底。
     */
    public static String displayName() {
        if (!isLoggedIn()) return "点击登录 / 注册";
        String name = username();
        if (name.isEmpty()) name = nickname();
        if (name.isEmpty()) name = phone();
        return name.isEmpty() ? "豆图用户" : name;
    }

    public static String expireAt() {
        return p() == null ? "" : p().getString("expire_at", "");
    }

    public static String statusText() {
        return p() == null ? "未登录" : p().getString("status_text", "未登录");
    }

    public static int status() {
        return p() == null ? -1 : p().getInt("status", -1);
    }

    public static int quotaDay() {
        return p() == null ? 0 : p().getInt("quota_day", 0);
    }

    /**
     * 头像地址（服务端下发的相对路径 `/avatar/{id}?v=版本号`，空串表示没有头像）。
     * 客户端把 Session.baseUrl() 拼在前面去取图。
     */
    public static String avatarUrl() {
        return normalizeAvatarUrl(p() == null ? null : p().getString("avatar_url", ""));
    }

    /**
     * JSON null 经 `optString()` 会变成**字符串 "null"**（不是空串），
     * 直接存下来/显示出去就是「手机号：null」这种东西。统一在这里按空处理。
     * 头像地址那次就是这样拼出了 `https://域名null` 去打网络。
     */
    private static String clean(String raw) {
        if (raw == null) return "";
        String v = raw.trim();
        if ("null".equalsIgnoreCase(v) || "undefined".equalsIgnoreCase(v)) return "";
        return v;
    }

    /**
     * 规范化头像地址。
     *
     * 坑：服务端没有头像时下发的是 JSON `null`，而 Android 的 `JSONObject.optString()`
     * 遇到 JSON null 会返回**字符串 "null"**（不是空串）。这个 "null" 既不是空、也不以
     * http 开头，于是被当成相对地址拼成了 `https://域名null` —— 日志里就是
     * 「头像下载失败 https://tdapi.luoyun.xyznull」。这里统一按空处理。
     */
    public static String normalizeAvatarUrl(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (url.isEmpty() || "null".equalsIgnoreCase(url) || "undefined".equalsIgnoreCase(url)) return "";
        // 只接受 http(s) 绝对地址或 / 开头的站内相对地址，别的（含拼坏的）一律当没有
        if (!url.startsWith("http") && !url.startsWith("/")) return "";
        return url;
    }

    /** 未激活时遮罩上那行字，由服务端配置下发。 */    public static String overlayText() {
        String v = p() == null ? null : p().getString("overlay_text", null);
        return (v == null || v.isEmpty()) ? "开通会员去除水印" : v;
    }

    public static void setOverlayText(String text) {
        if (p() != null && text != null && !text.isEmpty()) p().edit().putString("overlay_text", text).apply();
    }

    /** 未激活时给用户看的提示（服务端配置可改）。 */
    public static String activationNotice() {
        String v = p() == null ? null : p().getString("activation_notice", null);
        return (v == null || v.isEmpty()) ? "账号未激活，请联系客服开通会员" : v;
    }

    public static void setActivationNotice(String text) {
        if (p() != null && text != null && !text.isEmpty()) p().edit().putString("activation_notice", text).apply();
    }

    public static boolean isLoggedIn() {
        return !token().isEmpty();
    }

    public static boolean isActivated() {
        return isLoggedIn() && p() != null && p().getBoolean("activated", false);
    }

    /* ------------------------------ 分享好友（后台可配） ------------------------------ */

    /** 后台没配链接时的兜底：官网地址（客户端不存 IP，只用域名）。 */
    private static final String SHARE_LINK_FALLBACK = "https://tdapi.luoyun.xyz/";
    private static final String SHARE_TIP_FALLBACK = "链接已复制，粘贴给好友即可~";

    /**
     * 点「分享好友 / 邀请好友」时复制到剪贴板的那条链接，后台「全局配置」里可改。
     *
     * 服务端下发的是 JSON，未配置的键会是字符串 "null" —— 直接用会复制出一句 "null"，
     * 所以这里统一按「空/null/undefined → 退回兜底值」处理。
     */
    public static String shareLink() {
        String v = p() == null ? "" : p().getString("share_link", "");
        v = v == null ? "" : v.trim();
        if (v.isEmpty() || "null".equalsIgnoreCase(v) || "undefined".equalsIgnoreCase(v)) return SHARE_LINK_FALLBACK;
        return v;
    }

    /** 复制成功后给用户看的那句话，后台可改。 */
    public static String shareTip() {
        String v = p() == null ? "" : p().getString("share_tip", "");
        v = v == null ? "" : v.trim();
        if (v.isEmpty() || "null".equalsIgnoreCase(v) || "undefined".equalsIgnoreCase(v)) return SHARE_TIP_FALLBACK;
        return v;
    }

    /** 解析 /config 里的 share_link / share_tip，并落盘（下次冷启动也能用）。 */
    public static void applyShareConfig(String link, String tip) {
        String l = cleanText(link);
        String t = cleanText(tip);
        if (p() != null) {
            p().edit().putString("share_link", l).putString("share_tip", t).apply();
        }
    }

    private static String cleanText(String raw) {
        String v = raw == null ? "" : raw.trim();
        if ("null".equalsIgnoreCase(v) || "undefined".equalsIgnoreCase(v)) return "";
        return v;
    }

    /* ------------------------------ 人工客服（后台可配） ------------------------------ */

    /**
     * 「我 → 人工客服」那一页的三样东西，全部来自后台「全局配置 → 客服」分组：
     * {@code notice}（二维码上方那行字）、{@code qr_image}（二维码图片地址）、
     * {@code contact}（二维码下方那行手机号 / 微信号，点了复制）。
     *
     * <p>落盘保存，所以点进客服页时先拿这一份立刻画出来，再去拉一次最新的覆盖掉 ——
     * 后台改完不用等下一次冷启动。
     */
    public static String csNotice() {
        return p() == null ? "" : p().getString("cs_notice", "");
    }

    public static String csContact() {
        return p() == null ? "" : p().getString("cs_contact", "");
    }

    public static String csQrImage() {
        return p() == null ? "" : p().getString("cs_qr_image", "");
    }

    /** 解析 /config 里的 customer_service 那一块并落盘。 */
    public static void applyCustomerService(org.json.JSONObject block) {
        if (block == null || p() == null) return;
        p().edit()
                .putString("cs_notice", cleanText(block.optString("notice", "")))
                .putString("cs_contact", cleanText(block.optString("contact", "")))
                .putString("cs_qr_image", cleanText(block.optString("qr_image", "")))
                .apply();
    }

    /**
     * 服务端下发的「玩法配置」（2026-10-17）。
     *
     * <p>娱乐页原来把分区、标题、副标题、开关、点击去向全写死在客户端 —— 那是整包里最容易被
     * 抄走的一块。现在这些内容由 `/config` 的 `playbook.entertainment` 下发：抄走 APK 只有
     * 一个渲染壳，改文案/下线玩法也不用发版。
     *
     * <p>返回 null 表示服务端还没下发过（或者下发的结构不合法）—— 调用方据此退回内置的最小默认。
     */
    public static org.json.JSONObject playbook() {
        if (p() == null) return null;
        String raw = p().getString("playbook_entertainment", "");
        if (raw == null || raw.isEmpty()) return null;
        try {
            org.json.JSONObject obj = new org.json.JSONObject(raw);
            if (!obj.has("sections") || !(obj.opt("sections") instanceof org.json.JSONArray)) return null;
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    public static int playbookVersion() {
        return p() == null ? 0 : p().getInt("playbook_version", 0);
    }

    /** 解析 /config 里的 playbook 那一块并落盘。 */
    public static void applyPlaybook(org.json.JSONObject block) {
        if (block == null || p() == null) return;
        org.json.JSONObject ent = block.optJSONObject("entertainment");
        int version = block.optInt("version", 0);
        android.content.SharedPreferences.Editor edit = p().edit();
        if (ent != null && ent.opt("sections") instanceof org.json.JSONArray) {
            edit.putString("playbook_entertainment", ent.toString());
        }
        if (version > 0) edit.putInt("playbook_version", version);
        edit.apply();
    }

    /* ------------------------------ 首页横幅（后台公告下发） ------------------------------ */

    /**
     * 首页大图上那个文字框的内容，来自后台「公告管理」里**类型=3 · 首页横幅**的公告。
     *
     * 服务端已经把「状态=启用 + 时间段命中 + 目标等级匹配」过滤完了，客户端只管画：
     * 多条时按 {@link #bannerRotateSec()} 轮播；一条都没有时用默认文案（时间问候语）。
     */
    public static final class Banner {
        public final String title;
        public final String hint;

        Banner(String title, String hint) {
            this.title = title == null ? "" : title;
            this.hint = hint == null ? "" : hint;
        }
    }

    private static final java.util.List<Banner> bannerItems = new java.util.ArrayList<>();
    /** 上排（人物旁边那行）文字池 —— 后台「上排文字池」 */
    private static final java.util.List<String> bannerTitleList = new java.util.ArrayList<>();
    /** 下排（白色长条里那行）文字池 —— 后台「下排文字池」 */
    private static final java.util.List<String> bannerHintList = new java.util.ArrayList<>();
    private static int bannerRotateSec = 8;
    private static int titleRotateSec = 8;
    private static int hintRotateSec = 8;
    private static boolean bannerEnabled = true;
    private static String bannerDefaultTitle = "";
    private static String bannerDefaultHint = "";
    /** 后台配的问候语时段规则（`起点-终点|文案`），为空则用内置表 */
    private static final java.util.List<String> bannerGreetingRules = new java.util.ArrayList<>();
    /** type=1 的弹窗公告（进入 App 时弹一次） */
    private static final java.util.List<Popup> popupItems = new java.util.ArrayList<>();

    /** 一条弹窗公告。version() 用来判断「用户看过的是不是这一版内容」。 */
    public static final class Popup {
        public final long id;
        public final String title;
        public final String content;

        Popup(long id, String title, String content) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.content = content == null ? "" : content;
        }

        public String version() {
            return id + ":" + title + "|" + content;
        }
    }

    /** 解析 /api/v1/config 里的 notices（type=3）与 banner 配置，并落盘以便下次启动立即显示。 */
    public static void applyConfig(org.json.JSONObject data) {
        if (data == null) return;
        try {
            org.json.JSONArray arr = data.optJSONArray("notices");
            org.json.JSONArray banners = new org.json.JSONArray();
            org.json.JSONArray pops = new org.json.JSONArray();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject n = arr.optJSONObject(i);
                    if (n == null) continue;
                    int type = n.optInt("type", 0);
                    if (type == 3) {
                        // 首页横幅
                        org.json.JSONObject item = new org.json.JSONObject();
                        item.put("title", n.optString("title", ""));
                        item.put("hint", n.optString("content", ""));
                        banners.put(item);
                    } else if (type == 1) {
                        // 弹窗（进入 App 弹一次）
                        org.json.JSONObject item = new org.json.JSONObject();
                        item.put("id", n.optLong("id", 0));
                        item.put("title", n.optString("title", ""));
                        item.put("content", n.optString("content", ""));
                        pops.put(item);
                    }
                    // type=2（顶部滚动）暂时不进首页横幅，保留给后续版本
                }
            }
            org.json.JSONObject b = data.optJSONObject("banner");
            int rotate = b != null ? b.optInt("rotate_sec", 8) : 8;
            boolean enabled = b == null || b.optBoolean("enabled", true);
            String defTitle = b != null ? b.optString("default_title", "") : "";
            String defHint = b != null ? b.optString("default_hint", "") : "";
            // 上排 / 下排各自一个文字池 + 各自的轮播间隔（后台可分别设置）
            String titleListJson = listToJson(b != null ? b.optJSONArray("title_list") : null);
            String hintListJson = listToJson(b != null ? b.optJSONArray("hint_list") : null);
            String greetingsJson = listToJson(b != null ? b.optJSONArray("greetings") : null);
            int titleRotate = b != null ? b.optInt("title_rotate_sec", 8) : 8;
            int hintRotate = b != null ? b.optInt("hint_rotate_sec", 8) : 8;
            if (p() != null) {
                p().edit()
                        .putString("banner_items", banners.toString())
                        .putInt("banner_rotate_sec", rotate)
                        .putBoolean("banner_enabled", enabled)
                        .putString("banner_default_title", defTitle)
                        .putString("banner_default_hint", defHint)
                        .putString("banner_title_list", titleListJson)
                        .putString("banner_hint_list", hintListJson)
                        .putString("banner_greetings", greetingsJson)
                        .putString("popup_items", pops.toString())
                        .putInt("banner_title_rotate_sec", titleRotate)
                        .putInt("banner_hint_rotate_sec", hintRotate)
                        .apply();
            }
            parseBanners(banners.toString());
            bannerRotateSec = Math.max(3, rotate);
            bannerEnabled = enabled;
            bannerDefaultTitle = defTitle;
            bannerDefaultHint = defHint;
            parseList(titleListJson, bannerTitleList);
            parseList(hintListJson, bannerHintList);
            parseList(greetingsJson, bannerGreetingRules);
            parsePopups(pops.toString());
            titleRotateSec = Math.max(3, titleRotate);
            hintRotateSec = Math.max(3, hintRotate);
            // 分享好友（后台「全局配置 → 分享好友链接 / 提示语」下发；没配就退回内置文案）
            applyShareConfig(data.optString("share_link", ""), data.optString("share_tip", ""));
            // 人工客服页（后台「全局配置 → 客服」下发的那三样）
            applyCustomerService(data.optJSONObject("customer_service"));
            // 娱乐页玩法配置（分区/顺序/标题/开关/去向，全部由服务端下发）
            applyPlaybook(data.optJSONObject("playbook"));
        } catch (Exception ignored) {
            // 配置解析失败不影响主流程：横幅退回默认问候语
        }
    }

    private static String listToJson(org.json.JSONArray arr) {
        return arr == null ? "[]" : arr.toString();
    }

    private static void parseList(String json, java.util.List<String> out) {
        out.clear();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "").trim();
                if (!s.isEmpty()) out.add(s);
            }
        } catch (Exception ignored) {
            // 同上
        }
    }

    private static void parsePopups(String json) {
        popupItems.clear();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                popupItems.add(new Popup(o.optLong("id", 0), o.optString("title", ""), o.optString("content", "")));
            }
        } catch (Exception ignored) {
            // 同上
        }
    }

    /**
     * 取一条「还没给用户看过」的弹窗公告；没有就返回 null。
     * 判定依据是 version()（id + 标题 + 内容）——后台改了内容会重新弹一次，只是重启 App 不会重复弹。
     */
    public static Popup pendingPopup() {
        for (Popup item : popupItems) {
            if (item.title.isEmpty() && item.content.isEmpty()) continue;
            if (!isPopupShown(item)) return item;
        }
        return null;
    }

    private static boolean isPopupShown(Popup item) {
        if (p() == null) return false;
        String shown = p().getString("popup_shown", "");
        for (String v : shown.split("\n")) {
            if (v.equals(item.version())) return true;
        }
        return false;
    }

    /** 记下「已弹过」，最多保留最近 20 条，避免配置无限膨胀。 */
    public static void markPopupShown(Popup item) {
        if (p() == null || item == null) return;
        String shown = p().getString("popup_shown", "");
        if (isPopupShown(item)) return;
        String merged = shown.isEmpty() ? item.version() : shown + "\n" + item.version();
        String[] lines = merged.split("\n");
        if (lines.length > 20) {
            StringBuilder sb = new StringBuilder();
            for (int i = lines.length - 20; i < lines.length; i++) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(lines[i]);
            }
            merged = sb.toString();
        }
        p().edit().putString("popup_shown", merged).apply();
    }

    /** 从文字池里按自己的间隔取一条 */
    private static String pick(java.util.List<String> list, int intervalSec, long nowSec) {
        if (list == null || list.isEmpty()) return null;
        int idx = (int) ((nowSec / Math.max(3, intervalSec)) % list.size());
        return list.get(Math.max(0, idx));
    }

    private static void parseBanners(String json) {
        bannerItems.clear();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o != null) bannerItems.add(new Banner(o.optString("title", ""), o.optString("hint", "")));
            }
        } catch (Exception ignored) {
            // 同上
        }
    }

    /** 从本地缓存恢复（App 启动时先调用，避免等网络那几百毫秒里横幅是空的）。 */
    private static void restoreBanners() {
        if (p() == null) return;
        parseBanners(p().getString("banner_items", "[]"));
        bannerRotateSec = Math.max(3, p().getInt("banner_rotate_sec", 8));
        bannerEnabled = p().getBoolean("banner_enabled", true);
        bannerDefaultTitle = p().getString("banner_default_title", "");
        bannerDefaultHint = p().getString("banner_default_hint", "");
        parseList(p().getString("banner_title_list", "[]"), bannerTitleList);
        parseList(p().getString("banner_hint_list", "[]"), bannerHintList);
        parseList(p().getString("banner_greetings", "[]"), bannerGreetingRules);
        parsePopups(p().getString("popup_items", "[]"));
        titleRotateSec = Math.max(3, p().getInt("banner_title_rotate_sec", 8));
        hintRotateSec = Math.max(3, p().getInt("banner_hint_rotate_sec", 8));
    }

    /** 首页横幅总开关（后台「全局配置 → 首页横幅开关」）。 */
    public static boolean bannerEnabled() {
        return bannerEnabled;
    }

    /** 后台配置的轮播间隔（秒），最小 3 秒。 */
    public static int bannerRotateSec() {
        return Math.max(3, bannerRotateSec);
    }

    public static String bannerDefaultTitle() {
        return bannerDefaultTitle == null ? "" : bannerDefaultTitle;
    }

    public static String bannerDefaultHint() {
        return bannerDefaultHint == null ? "" : bannerDefaultHint;
    }

    /**
     * 当前该显示的两行文字。**两行是各自独立取的**：
     *   上排：type=3 公告的 title（按 notice_rotate_sec 轮播）优先，否则从「上排文字池」按 title_rotate_sec 轮播；
     *   下排：type=3 公告的 content（同一节奏）优先，否则从「下排文字池」按 hint_rotate_sec 轮播。
     * 所以后台可以让两行用不同条数、不同间隔各自转，互不影响。
     */
    public static Banner currentBanner() {
        long sec = System.currentTimeMillis() / 1000L;

        // 公告（带时间段的临时活动）：两条文字成对轮播，命中时优先覆盖对应的池子
        String noticeTitle = null;
        String noticeHint = null;
        if (!bannerItems.isEmpty()) {
            int idx = (int) ((sec / bannerRotateSec()) % bannerItems.size());
            Banner b = bannerItems.get(Math.max(0, idx));
            if (!b.title.isEmpty()) noticeTitle = b.title;
            if (!b.hint.isEmpty()) noticeHint = b.hint;
        }

        // 上排：把「当前公告的文字」排进池子最前面，再和池子里的其它文案一起轮播
        // （公告不再覆盖池子 —— 否则后台设了池子却永远只显示公告那一条，很难理解）
        java.util.List<String> titles = new java.util.ArrayList<>();
        if (noticeTitle != null) titles.add(noticeTitle);
        titles.addAll(bannerTitleList);
        String title = pick(titles, titleRotateSec, sec);
        if (title == null) title = bannerDefaultTitle();
        if (title == null || title.isEmpty()) title = "{greeting}";
        title = title.replace("{greeting}", greeting());

        // 下排：同样并入
        java.util.List<String> hints = new java.util.ArrayList<>();
        if (noticeHint != null) hints.add(noticeHint);
        hints.addAll(bannerHintList);
        String hint = pick(hints, hintRotateSec, sec);
        if (hint == null) hint = bannerDefaultHint();

        return new Banner(title, hint);
    }

    /** 下排长条一帧：正在显示的文字、下一条，以及上下滚动的进度（0 = 静止，0~1 = 正在滚）。 */
    public static final class HintFrame {
        public final String current;
        public final String next;
        public final float progress;

        HintFrame(String current, String next, float progress) {
            this.current = current == null ? "" : current;
            this.next = next == null ? "" : next;
            this.progress = progress;
        }

        public boolean rolling() {
            return progress > 0f && progress < 1f;
        }
    }

    /** 切换动画时长（毫秒）：这段时间里长条内的文字上下滚动。 */
    private static final long HINT_ANIM_MS = 420;

    /**
     * 下排长条的当前帧：每条停留 {@link #bannerHintRotateSec()} 秒，
     * 切换到下一条时用最后 {@link #HINT_ANIM_MS} 毫秒做**上下滚动**。
     */
    public static HintFrame hintFrame() {
        long nowMs = System.currentTimeMillis();
        java.util.List<String> hints = hintListNow();
        if (hints.isEmpty()) {
            return new HintFrame(bannerDefaultHint(), null, 0f);
        }
        long intervalMs = Math.max(3, hintRotateSec) * 1000L;
        long phase = nowMs % intervalMs;
        int idx = (int) ((nowMs / intervalMs) % hints.size());
        String current = hints.get(idx);
        if (phase >= intervalMs - HINT_ANIM_MS && hints.size() > 1) {
            float progress = (float) (phase - (intervalMs - HINT_ANIM_MS)) / HINT_ANIM_MS;
            return new HintFrame(current, hints.get((idx + 1) % hints.size()), Math.min(1f, progress));
        }
        return new HintFrame(current, null, 0f);
    }

    /** 最小 3 秒的下排停留间隔。 */
    public static int bannerHintRotateSec() {
        return Math.max(3, hintRotateSec);
    }

    /** 是否有滚动动画在进行（主界面用它决定要不要高频重绘）。 */
    public static boolean hintRolling() {
        return hintFrame().rolling();
    }

    /** 下排当前该显示的文字池：公告内容（若有）排在最前 + 后台配置的下排文字池。 */
    private static java.util.List<String> hintListNow() {
        java.util.List<String> hints = new java.util.ArrayList<>();
        if (!bannerItems.isEmpty()) {
            long sec = System.currentTimeMillis() / 1000L;
            int idx = (int) ((sec / bannerRotateSec()) % bannerItems.size());
            String noticeHint = bannerItems.get(Math.max(0, idx)).hint;
            if (noticeHint != null && !noticeHint.isEmpty()) hints.add(noticeHint);
        }
        hints.addAll(bannerHintList);
        return hints;
    }

    /**
     * 按当前小时给的问候语（用于上排文字池里的 {@code {greeting}} 占位）。
     *
     * 取值顺序：
     *   1. 后台配置的时段规则（配置项 `banner_greetings`，每条形如 `19-22|晚上好！…`）；
     *   2. 没配就用下面这份内置表（覆盖 凌晨 / 清晨 / 上午 / 中午 / 下午 / 傍晚 / 晚上 / 睡前）。
     *
     * 时段按「起点小时 ≤ 当前小时 < 终点小时」匹配，夜里那段提醒早睡。
     */
    public static String greeting() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        // 1) 后台规则优先
        for (String rule : bannerGreetingRules) {
            int bar = rule.indexOf('|');
            if (bar <= 0) continue;
            String span = rule.substring(0, bar).trim();
            String text = rule.substring(bar + 1).trim();
            if (text.isEmpty()) continue;
            int dash = span.indexOf('-');
            if (dash <= 0) continue;
            try {
                int from = Integer.parseInt(span.substring(0, dash).trim());
                int to = Integer.parseInt(span.substring(dash + 1).trim());
                if (inHourRange(h, from, to)) return text;
            } catch (NumberFormatException ignored) {
                // 单条规则写错就跳过，不影响其它
            }
        }
        // 2) 内置兜底
        if (h < 5) return "凌晨了！这个点就别忙了，早点睡，明天再弄也不迟~";
        if (h < 8) return "早晨好！新的一天开始了，慢慢来~";
        if (h < 11) return "上午好！有需要随时招呼我~";
        if (h < 13) return "中午好！先好好吃顿饭，忙活不差这一会儿~";
        if (h < 17) return "下午好！今天想弄点什么？";
        if (h < 19) return "傍晚好！忙了一天，先歇口气~";
        if (h < 22) return "晚上好！一天将尽，放松身心，别太辛苦~";
        return "夜深了！早点休息吧，明天再弄也不迟~";
    }

    /** 小时是否落在 [from, to) 区间内（支持跨零点，例如 22-5）。 */
    private static boolean inHourRange(int h, int from, int to) {
        if (from == to) return false;
        if (from < to) return h >= from && h < to;
        // 跨零点：22-5 表示 22,23,0,1,2,3,4
        return h >= from || h < to;
    }

    /* ------------------------------ 写入 / 清除 ------------------------------ */

    public static void save(String phone, String token, String signKey) {
        if (p() == null) return;
        p().edit().putString("phone", phone).putString("token", token).putString("sign_key", signKey).apply();
    }

    /**
     * 用 /auth/status、/auth/login、/auth/register、/auth/profile 返回的 user 对象刷新本地状态。
     * 注意 `/auth/status` 的用户字段是**平铺在 data 上**的，所以两种信封都要能喂进来（见 DoutuApi.Result#user）。
     */
    public static void updateUser(org.json.JSONObject user) {
        if (p() == null || user == null) return;
        SharedPreferences.Editor e = p().edit();
        if (user.has("username")) {
            String name = user.optString("username", "");
            if (!name.isEmpty()) e.putString("username", name);
        }
        if (user.has("nickname")) e.putString("nickname", clean(user.optString("nickname", "")));
        // 写入侧也规范化：服务端没头像时下发 JSON null，optString 会把它变成字符串 "null"，
        // 存进去以后每次读都要再挡一次（曾经拼出 https://域名null 去打网络）。存的时候就存干净值。
        if (user.has("avatar_url")) {
            e.putString("avatar_url", normalizeAvatarUrl(user.optString("avatar_url", "")));
        }
        if (user.has("phone")) {
            // 服务端 phone 可能显式下发 null（没绑手机号的账号），optString 会给出 "null"
            String ph = clean(user.optString("phone", ""));
            if (!ph.isEmpty()) e.putString("phone", ph);
        }
        if (user.has("expire_at")) e.putString("expire_at", clean(user.optString("expire_at", "")));
        if (user.has("status_text")) e.putString("status_text", clean(user.optString("status_text", "")));
        if (user.has("status")) e.putInt("status", user.optInt("status", -1));
        if (user.has("quota_day")) e.putInt("quota_day", user.optInt("quota_day", 0));
        e.putBoolean("activated", user.optBoolean("activated", false));
        e.apply();
    }

    /** 改用户名成功后同步本地（登录标识变了，界面与下次登录都要用新名字）。 */
    public static void setUsername(String name) {
        if (p() != null && name != null && !name.isEmpty()) p().edit().putString("username", name.trim()).apply();
    }

    public static void clear() {
        if (p() == null) return;
        // 只清会话相关字段，保留调试包里的服务端地址，免得每次退出登录都要重填
        String base = baseUrl();
        p().edit().clear().apply();
        if (BuildConfig.IS_DEV_BUILD) setBaseUrl(base);
    }
}
