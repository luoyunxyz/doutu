package com.luoyun.doutu.features.zhi.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.luoyun.doutu.features.pay.data.CollectStore;
import com.luoyun.doutu.features.travel.data.TravelStore;
import com.luoyun.doutu.features.zhima.data.ZhimaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 支模块的账号表：切换账号页上那一排账号就是这份数据。
 *
 * <p>每个账号一套自己的存档，互不相通：账号 0 沿用原来的 preferences 名字（升级前填过的数据还在
 * 它名下），其它账号各自加一个后缀 —— 各个仓库（{@code ProfileStore}、{@code AssetStore}、
 * {@code BalanceStore}、{@code YuebaoStore}、{@code HuabeiStore}、{@code PayMsgStore}）问
 * {@link #suffix()} 拿这个后缀，所以「总资产」「余额」「个人信息」这些页在哪个账号下就是哪个
 * 账号自己的数。换账号时先把每个仓库的内存缓存清掉，下一次读它读到的就是新账号的存档。
 *
 * <p>新开一个账号时它当场就有自己的身份：名字从这一页的名字里随机取一个，手机号随机一个（格式跟
 * 参考图卡片上那个一样：三位号段 + 六个星号 + 两位尾号），头像跟「个人信息 / 账单」用的是同一个
 * 随机头像接口，取回来之后写进这个账号自己的「个人信息」，所以「我的」「个人信息」和切换账号页
 * 看到的是同一张脸。默认那个账号不动：它头上那张就是参考图里抠出来的那张，卡片也照参考图画。
 *
 * <p>只有这张表本身（有哪些账号、现在是哪个）存在一个固定的 preferences 里。这里的东西都不是
 * 真的：没有登录，没有密码，没有一个真实手机号；除了取头像那一次请求，不会联网。
 */
public final class Accounts {
    private static final String PREFS = "accounts_store";
    private static final String KEY_CURRENT = "current";
    private static final String KEY_NEXT = "next";
    private static final String KEY_PHONE = "phone:";
    private static final String KEY_NAME = "name:";
    private static final String AVATAR_FILE = "account_avatar_%d.png";

    /** 原来那个账号：它的存档不带后缀，换了账号也一直留着。 */
    public static final int DEFAULT_ID = 0;
    /** 默认账号的身份就是参考图上那个 —— 卡片照参考图原样贴，不加名字那一行。 */
    public static final String DEFAULT_PHONE = "159******20";
    public static final String DEFAULT_NAME = "落陨";

    /** 页面上被点到的账号变了（新建、改名、换头像、换账号）时叫一声。 */
    public interface Watcher {
        void onAccountsChanged();
    }

    public static final class Account {
        public final int id;
        public String phone;
        public String name;
        /** 自己那张头像；null 的时候页面用参考图里抠出来的那张。 */
        public Bitmap avatar;

        Account(int id, String phone, String name) {
            this.id = id;
            this.phone = phone;
            this.name = name;
        }

        /** 有没有自己的名字和号码：默认那个没动过的账号没有，它的卡片就照参考图画。 */
        public boolean ownIdentity() {
            return !DEFAULT_NAME.equals(name) || !DEFAULT_PHONE.equals(phone);
        }
    }

    private static final String[] PREFIX = {
            "130", "131", "132", "133", "135", "136", "137", "138", "139",
            "150", "151", "152", "155", "156", "157", "158", "159",
            "170", "171", "173", "175", "176", "177", "178",
            "180", "181", "182", "183", "184", "185", "186", "187", "188", "189"};

    /** 新账号随机取的名字（和默认那个「落陨」分开，一眼就能看出不是同一个号）。 */
    private static final String[] NICKNAME = {
            "林知夏", "江照野", "陈小满", "苏晚舟", "沈砚青", "温以宁", "顾青野", "许星野",
            "白露", "郁南枝", "周牧遥", "叶疏影", "程野", "秦昭", "谢云舟", "黎见月",
            "花城", "祁言", "陌上", "南栀"};

    private static Context context;
    private static boolean loaded;
    private static int current = DEFAULT_ID;
    private static int next = 1;
    private static final LinkedHashMap<Integer, Account> accounts = new LinkedHashMap<>();
    private static final Set<Integer> fetching = new HashSet<>();
    private static final Random random = new Random();
    private static Watcher watcher;

    private Accounts() {
    }

    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    /** 页面在的时候挂上来，账号表一变就重画。 */
    public static void watch(Watcher value) {
        watcher = value;
    }

    private static void notifyChanged() {
        Watcher w = watcher;
        if (w != null) w.onAccountsChanged();
    }

    /**
     * 当前账号的存档后缀。默认那个账号是空串 —— 它用的还是升级前那套 preferences，
     * 老用户的数据不会丢。
     */
    public static String suffix() {
        load();
        return current == DEFAULT_ID ? "" : "@a" + current;
    }

    public static int currentId() {
        load();
        return current;
    }

    public static List<Account> all() {
        load();
        return new ArrayList<>(accounts.values());
    }

    public static Account account(int id) {
        load();
        return accounts.get(id);
    }

    public static boolean isCurrent(int id) {
        return currentId() == id;
    }

    /** 换到另一个账号：记住它，再把每个仓库的缓存清掉。 */
    public static void switchTo(int id) {
        load();
        if (!accounts.containsKey(id)) return;
        current = id;
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(KEY_CURRENT, current).apply();
        forgetStores();
        notifyChanged();
    }

    // ------------------------------------------------------------------ 新建账号

    /**
     * 新开一个账号：名字和号码当场随机一个（号码照参考图卡片上那个格式），头像问随机头像接口
     * 要，回来之后再落到这个账号自己的「个人信息」上。返回时切换账号页可以先把它画出来。
     */
    public static Account create() {
        load();
        int id = next++;
        Account account = new Account(id, randomPhone(), randomName());
        accounts.put(id, account);
        save(account);
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putInt(KEY_NEXT, next).apply();
        notifyChanged();
        return account;
    }

    /**
     * 把当前账号的名字和号码写进它自己的「个人信息」存档（新账号建好、换过去之后叫一次），
     * 头像已经取回来的话也一起写 —— 「我的」「个人信息」和这一页就是同一套身份。
     */
    public static void applyProfile() {
        load();
        Account account = accounts.get(current);
        if (account == null || account.id == DEFAULT_ID) return;
        ProfileStore.name(account.name);
        ProfileStore.phone(account.phone);
        if (account.avatar != null) ProfileStore.avatar(account.avatar);
    }

    /**
     * 把当前账号在「个人信息」里改过的名字 / 号码 / 头像搬回这张表，页面上就能同步看到。
     * 打开切换账号页时叫一次。
     */
    public static void syncCurrent() {
        load();
        Account account = accounts.get(current);
        if (account == null || context == null) return;
        String phone = ProfileStore.phone();
        String name = ProfileStore.name();
        Bitmap avatar = ProfileStore.avatar();
        boolean changed = false;
        if (phone != null && !phone.equals(account.phone)) {
            account.phone = phone;
            changed = true;
        }
        if (name != null && !name.equals(account.name)) {
            account.name = name;
            changed = true;
        }
        if (avatar != null && avatar != account.avatar) {
            account.avatar = avatar;
            changed = true;
        }
        if (changed) save(account);
    }

    /** 还没抽到头像的账号（默认那个除外）在页面打开时补一张。 */
    public static void ensureAvatars() {
        load();
        for (Account account : accounts.values()) {
            if (account.id == DEFAULT_ID || account.avatar != null
                    || fetching.contains(account.id)) {
                continue;
            }
            fetchAvatar(account, null);
        }
    }

    /** 头像：用「个人信息 / 账单」那张随机头像接口抽一张，回来了存下来并叫页面重画。 */
    public static void fetchAvatar(final Account account, final Runnable done) {
        if (account == null || fetching.contains(account.id)) {
            if (done != null) done.run();
            return;
        }
        fetching.add(account.id);
        AvatarApi.fetch(bitmap -> {
            fetching.remove(account.id);
            if (bitmap != null) {
                account.avatar = bitmap;
                write(account.id, bitmap);
                if (current == account.id) ProfileStore.avatar(bitmap);
                save(account);
            }
            notifyChanged();
            if (done != null) done.run();
        }, 512);
    }

    /** 改手机号：只改这个账号自己的那一行，「个人信息」里的那一行也跟着改。 */
    public static void rename(int id, String phone) {
        load();
        Account account = accounts.get(id);
        if (account == null) return;
        String value = phone == null ? "" : phone.trim();
        if (value.isEmpty()) return;
        account.phone = value;
        save(account);
        if (id == current) ProfileStore.phone(value);
        notifyChanged();
    }

    /** 删掉一个除当前账号之外的账号（连它那份存档一起清掉）。 */
    public static void remove(int id) {
        load();
        if (id == DEFAULT_ID || id == current || !accounts.containsKey(id)) return;
        accounts.remove(id);
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().remove(KEY_PHONE + id).remove(KEY_NAME + id).apply();
        File file = avatarFile(id);
        if (file != null && file.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        clearStoreFiles(id);
        notifyChanged();
    }

    /** 「查看你实名认证的账号」用一次的那个假名字，按账号取，看着像每个号都不一样。 */
    public static String realName(int id) {
        String[] names = {"落陨", "江屿", "陈小满", "林知夏"};
        return names[Math.floorMod(id, names.length)];
    }

    // ------------------------------------------------------------------ 存档

    private static void forgetStores() {
        ProfileStore.reset();
        AssetStore.reset();
        BalanceStore.reset();
        YuebaoStore.reset();
        HuabeiStore.reset();
        PayMsgStore.reset();
        PaySuccessStore.reset();
        ReceiptStore.reset();
        WithdrawStore.reset();
        GrowthStore.reset();
        LicaiVersion.reset();
        // 2026-10-16：这几个页面原来把用户改的东西只放在内存里，现在各留了一份存档
        ZhiHomeStore.reset();
        MeStore.reset();
        ZhimaStore.reset();
        TravelStore.reset();
        CollectStore.reset();
        com.luoyun.doutu.features.call.CallStore.reset();
        com.luoyun.doutu.features.sound.SoundStore.reset();
        com.luoyun.doutu.features.order.OrderStore.reset();
        com.luoyun.doutu.features.play.PlayStore.reset();
    }

    /** 删账号时把它那几份 preferences 一并丢掉。 */
    private static void clearStoreFiles(int id) {
        if (context == null) return;
        String suffix = "@a" + id;
        for (String name : new String[]{"profile_store", "asset_store", "balance_store",
                "yuebao_store", "huabei_store", "pay_msg_store", "pay_success_store",
                "receipt_store", "withdraw_store", "purse_store", "licai_version_store",
                "zhi_home_store", "me_store", "zhima_store", "travel_store", "collect_store",
                "call_store", "sound_store", "order_store", "play_store"}) {
            SharedPreferences prefs =
                    context.getSharedPreferences(name + suffix, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();
        }
        File picture = new File(context.getFilesDir(), "profile_avatar" + suffix + ".png");
        if (picture.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            picture.delete();
        }
        // 收钱页换成相册图片的那张码
        File collectQr = new File(context.getFilesDir(), "collect_qr" + suffix + ".png");
        if (collectQr.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            collectQr.delete();
        }
        // 模拟来电自己挑的头像与铃声（都在 filesDir/call_assets 下，文件名带账号后缀）
        File callAssets = new File(context.getFilesDir(), "call_assets");
        File[] callFiles = callAssets.listFiles();
        if (callFiles != null) {
            for (File file : callFiles) {
                String name = file.getName();
                if (name.equals("caller" + suffix + ".png") || name.startsWith("ringtone" + suffix + ".")) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        }
        // 淘宝订单详情里换过的商品图（filesDir/order_assets/goods<suffix>.png）
        File orderImage = new File(new File(context.getFilesDir(), "order_assets"),
                "goods" + suffix + ".png");
        if (orderImage.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            orderImage.delete();
        }
        // 小荷包的头像是一排，一个一个删
        for (int i = 0; i <= 8; i++) {
            File purse = new File(context.getFilesDir(), "purse_avatar_" + i + suffix + ".png");
            if (purse.isFile()) {
                //noinspection ResultOfMethodCallIgnored
                purse.delete();
            }
        }
    }

    /** 三位号段 + 六个星号 + 两位尾号，和参考图卡片上那个格式一样。 */
    private static String randomPhone() {
        return PREFIX[random.nextInt(PREFIX.length)] + "******" + (10 + random.nextInt(90));
    }

    private static String randomName() {
        return NICKNAME[random.nextInt(NICKNAME.length)];
    }

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences prefs = prefs();
        if (prefs != null) {
            current = prefs.getInt(KEY_CURRENT, DEFAULT_ID);
            next = Math.max(1, prefs.getInt(KEY_NEXT, 1));
        }
        Account first = new Account(DEFAULT_ID, DEFAULT_PHONE, DEFAULT_NAME);
        first.avatar = read(DEFAULT_ID);
        accounts.put(DEFAULT_ID, first);
        for (int id = 1; id < next; id++) {
            String phone = prefs == null ? null : prefs.getString(KEY_PHONE + id, null);
            if (phone == null) continue;
            String name = prefs.getString(KEY_NAME + id, null);
            Account account = new Account(id, phone,
                    name == null || name.trim().isEmpty() ? randomName() : name);
            account.avatar = read(id);
            accounts.put(id, account);
        }
        if (!accounts.containsKey(current)) current = DEFAULT_ID;
    }

    private static void save(Account account) {
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        prefs.edit()
                .putString(KEY_PHONE + account.id, account.phone)
                .putString(KEY_NAME + account.id, account.name)
                .apply();
    }

    private static SharedPreferences prefs() {
        return context == null
                ? null : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File avatarFile(int id) {
        return context == null ? null
                : new File(context.getFilesDir(), String.format(AVATAR_FILE, id));
    }

    private static void write(int id, Bitmap bitmap) {
        File target = avatarFile(id);
        if (target == null) return;
        try (OutputStream out = new FileOutputStream(target)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            // 这一次运行里还是看得到，只是留不到下一次
        }
    }

    private static Bitmap read(int id) {
        File source = avatarFile(id);
        if (source == null || !source.isFile()) return null;
        try {
            return BitmapFactory.decodeFile(source.getAbsolutePath());
        } catch (Exception e) {
            return null;
        }
    }
}
