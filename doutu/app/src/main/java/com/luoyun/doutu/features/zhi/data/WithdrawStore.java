package com.luoyun.doutu.features.zhi.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.luoyun.doutu.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The 提现 page's 到账银行卡 list and the two figures under the amount.
 *
 * <p>It opens on exactly what the reference capture shows: 招商银行储蓄卡 (4646) selected,
 * 招商银行储蓄卡 (5021) under it and 网商银行储蓄卡 (6628) last, which the page writes off as
 * 此卡不可用于提现. Everything after that is the user's — 添加储蓄卡提现 puts another 储蓄卡 in
 * right above the 网商 one, a long press on a row replaces its logo or renames it, and the chosen
 * card is what the page's own 到账银行卡 row wears.
 *
 * <p>A card's logo is either one of the banks that came with the project
 * ({@code res/drawable-nodpi/bank_*.jpg}, the folder the user asked to move in) or a picture the
 * user picked from his own album; the latter is kept as a PNG beside the other per-account files.
 *
 * <p>None of this is a real bank account: the tails are made up, the 28404.47 is the reference's own
 * figure, and nothing here talks to a bank.
 */
public final class WithdrawStore {
    private static final String PREFS = "withdraw_store";
    private static final String KEY_CARDS = "cards";
    private static final String KEY_SELECTED = "selected";
    private static final String KEY_QUOTA = "quota";
    private static final String KEY_AVAIL = "avail";

    /** A logo of the user's own instead of one of the banks below. */
    public static final String BESPOKE = "bespoke";
    /** The picture a bespoke logo is kept at, at most. */
    private static final int ICON_MAX = 512;

    /** What the page shows the first time it is opened, all three straight off the reference. */
    public static final String ARRIVAL_SOON = "2小时内到账";
    public static final String ARRIVAL_BLOCKED = "此卡不可用于提现";
    public static final double QUOTA_SEED = 28404.47;

    public static final class Card {
        public long id;
        /** One of {@link #BANKS}' keys, or {@link #BESPOKE} when the picture is the user's own. */
        public String bank;
        public String name;
        public String tail;
        /** The line under the name: 2小时内到账, or the reason the card cannot be used. */
        public String arrival;
        public boolean usable;

        public String title() {
            return name + " (" + tail + ")";
        }
    }

    /** One logo the page can wear: a key of the project's own bank folder, and what it is called. */
    public static final class Bank {
        public final String key;
        public final String label;

        Bank(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    /** The folder's thirteen logos, the 网商 one cut out of the reference, and a blank card. */
    public static final Bank[] BANKS = {
            new Bank("gongshangyh", "工商银行"),
            new Bank("nongyeyh", "农业银行"),
            new Bank("zhongguoyh", "中国银行"),
            new Bank("jiansheyh", "建设银行"),
            new Bank("jiaotongyh", "交通银行"),
            new Bank("zhaoshangyh", "招商银行"),
            new Bank("pufayh", "浦发银行"),
            new Bank("xingyeyh", "兴业银行"),
            new Bank("guangfayh", "广发银行"),
            new Bank("minshengyh", "民生银行"),
            new Bank("youchuyh", "邮储银行"),
            new Bank("zhongxinyh", "中信银行"),
            new Bank("wangshang", "网商银行"),
            new Bank("not", "银行卡"),
    };

    private static Context context;
    private static boolean loaded;
    private static final List<Card> cards = new ArrayList<>();
    private static long selected;
    private static double quota = QUOTA_SEED;
    private static double avail;
    private static final Random random = new Random();
    /** Decoded logos, so a redraw does not decode a PNG every frame. */
    private static final Map<String, Bitmap> icons = new LinkedHashMap<>();

    private WithdrawStore() {
    }

    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    /** 换账号时把这一份缓存丢掉，下一次问它读到的就是新账号的存档。 */
    public static void reset() {
        loaded = false;
        cards.clear();
        icons.clear();
        selected = 0;
        quota = QUOTA_SEED;
        avail = 0;
    }

    // ------------------------------------------------------------------ the cards

    public static List<Card> cards() {
        load();
        return cards;
    }

    public static Card selected() {
        load();
        Card first = null;
        for (Card card : cards) {
            if (first == null) first = card;
            if (card.id == selected) return card;
        }
        return first;
    }

    /** The chosen card decides what the page's 到账银行卡 row shows. */
    public static void select(long id) {
        load();
        for (Card card : cards) {
            if (card.id == id) {
                if (!card.usable) return;
                selected = id;
                persist();
                return;
            }
        }
    }

    public static Card byId(long id) {
        load();
        for (Card card : cards) if (card.id == id) return card;
        return null;
    }

    /**
     * Where 添加储蓄卡提现 drops the new card: directly above the first card that cannot be used
     * for a withdrawal — the 网商 one the reference ends its list with — and at the end when there
     * is no such card.
     */
    public static int addIndex() {
        load();
        for (int i = 0; i < cards.size(); i++) if (!cards.get(i).usable) return i;
        return cards.size();
    }

    /** Makes a fresh 储蓄卡 for the given logo and tail, and puts it where {@link #addIndex} says. */
    public static Card add(String bank, String name, String tail) {
        load();
        Card card = new Card();
        card.id = System.nanoTime();
        card.bank = bank;
        card.name = name;
        card.tail = tail;
        card.arrival = ARRIVAL_SOON;
        card.usable = true;
        cards.add(Math.max(0, Math.min(cards.size(), addIndex())), card);
        selected = card.id;
        persist();
        return card;
    }

    public static void rename(Card card, String name) {
        if (card == null || name == null) return;
        String value = name.trim();
        if (value.isEmpty()) return;
        card.name = value;
        persist();
    }

    /** Gives the card another logo, either a bank of the project's own or the user's picture. */
    public static void logo(Card card, String bank, Bitmap bespoke) {
        if (card == null) return;
        card.bank = bank;
        icons.remove(Long.toString(card.id));
        if (BESPOKE.equals(bank)) write(card, bespoke);
        persist();
    }

    /** The card's own name for the logo, so 添加储蓄卡提现 can name a card as soon as one is picked. */
    public static String bankName(String key) {
        for (Bank bank : BANKS) if (bank.key.equals(key)) return bank.label;
        return "储蓄卡";
    }

    /**
     * What a card wearing this logo is called: 招商银行 → 招商银行储蓄卡. A picture of the user's own
     * has no name of its own — the card keeps whatever it was called — and the blank card is just
     * 银行卡.
     */
    public static String bankCardName(String key) {
        if (BESPOKE.equals(key)) return null;
        if ("not".equals(key)) return "银行卡";
        return bankName(key) + "储蓄卡";
    }

    /** 这个名字是不是还跟着图标走：是的话换图标就顺手改掉，被人改过就不动。 */
    public static boolean autoName(String name) {
        if (name == null) return false;
        for (Bank bank : BANKS) {
            String auto = bankCardName(bank.key);
            if (auto != null && auto.equals(name)) return true;
        }
        return false;
    }

    /** A four digit tail none of the other cards is wearing. */
    public static String newTail() {
        load();
        for (int attempt = 0; attempt < 40; attempt++) {
            String tail = String.format(Locale.CHINA, "%04d", 1000 + random.nextInt(9000));
            boolean taken = false;
            for (Card card : cards) if (tail.equals(card.tail)) taken = true;
            if (!taken) return tail;
        }
        return String.format(Locale.CHINA, "%04d", 1000 + random.nextInt(9000));
    }

    // ------------------------------------------------------------------ the two figures

    public static double quota() {
        load();
        return quota;
    }

    public static void quota(double value) {
        load();
        quota = Math.max(0, ReceiptStore.round2(value));
        persist();
    }

    /** 可用余额: what the amount may be withdrawn against. */
    public static double avail() {
        load();
        return avail;
    }

    public static void avail(double value) {
        load();
        avail = Math.max(0, ReceiptStore.round2(value));
        persist();
    }

    // ------------------------------------------------------------------ the logos

    /** The card's logo, decoded once and kept until the card is given another one. */
    public static Bitmap icon(Card card) {
        load();
        if (card == null || context == null) return null;
        String key = Long.toString(card.id);
        Bitmap kept = icons.get(key);
        if (kept != null) return kept;
        Bitmap bitmap = null;
        if (BESPOKE.equals(card.bank)) {
            File file = file(card);
            if (file != null && file.isFile()) bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        } else {
            int id = drawable(card.bank);
            if (id != 0) bitmap = BitmapFactory.decodeResource(context.getResources(), id);
        }
        if (bitmap != null) icons.put(key, bitmap);
        return bitmap;
    }

    /** The project's own logo for a bank key; 0 when the key is the user's own picture. */
    public static int drawable(String key) {
        if (key == null) return 0;
        switch (key) {
            case "gongshangyh": return R.drawable.bank_gongshangyh;
            case "nongyeyh": return R.drawable.bank_nongyeyh;
            case "zhongguoyh": return R.drawable.bank_zhongguoyh;
            case "jiansheyh": return R.drawable.bank_jiansheyh;
            case "jiaotongyh": return R.drawable.bank_jiaotongyh;
            case "zhaoshangyh": return R.drawable.bank_zhaoshangyh;
            case "pufayh": return R.drawable.bank_pufayh;
            case "xingyeyh": return R.drawable.bank_xingyeyh;
            case "guangfayh": return R.drawable.bank_guangfayh;
            case "minshengyh": return R.drawable.bank_minshengyh;
            case "youchuyh": return R.drawable.bank_youchuyh;
            case "zhongxinyh": return R.drawable.bank_zhongxinyh;
            case "wangshang": return R.drawable.wd_bank_wangshang;
            case "not": return R.drawable.bank_not;
            default: return 0;
        }
    }

    // ------------------------------------------------------------------ the copy that outlives the run

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        cards.clear();
        SharedPreferences prefs = prefs();
        String kept = prefs == null ? null : prefs.getString(KEY_CARDS, null);
        if (kept == null || kept.isEmpty()) {
            seed();
            persist();
            return;
        }
        try {
            JSONArray array = new JSONArray(kept);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Card card = new Card();
                card.id = o.optLong("id");
                card.bank = o.optString("bank", "not");
                card.name = o.optString("name", "储蓄卡");
                card.tail = o.optString("tail", "0000");
                card.arrival = o.optString("arrival", ARRIVAL_SOON);
                card.usable = o.optBoolean("usable", true);
                cards.add(card);
            }
            selected = prefs.getLong(KEY_SELECTED, cards.isEmpty() ? 0 : cards.get(0).id);
            quota = prefs.getFloat(KEY_QUOTA, (float) QUOTA_SEED);
            avail = prefs.getFloat(KEY_AVAIL, 0f);
        } catch (Exception e) {
            cards.clear();
            seed();
            persist();
        }
    }

    /** 招商 (4646) selected, 招商 (5021) under it, 网商 (6628) last and unusable: the capture's list. */
    private static void seed() {
        cards.add(make(1, "zhaoshangyh", "招商银行储蓄卡", "4646", true));
        cards.add(make(2, "zhaoshangyh", "招商银行储蓄卡", "5021", true));
        cards.add(make(3, "wangshang", "网商银行储蓄卡", "6628", false));
        selected = 1;
        quota = QUOTA_SEED;
        avail = 0;
    }

    private static Card make(long id, String bank, String name, String tail, boolean usable) {
        Card card = new Card();
        card.id = id;
        card.bank = bank;
        card.name = name;
        card.tail = tail;
        card.arrival = usable ? ARRIVAL_SOON : ARRIVAL_BLOCKED;
        card.usable = usable;
        return card;
    }

    private static void persist() {
        if (context == null) return;
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        JSONArray array = new JSONArray();
        for (Card card : cards) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", card.id);
                o.put("bank", card.bank);
                o.put("name", card.name);
                o.put("tail", card.tail);
                o.put("arrival", card.arrival);
                o.put("usable", card.usable);
            } catch (Exception e) {
                // a field that will not go in simply does not survive the next run
            }
            array.put(o);
        }
        prefs.edit()
                .putString(KEY_CARDS, array.toString())
                .putLong(KEY_SELECTED, selected)
                .putFloat(KEY_QUOTA, (float) quota)
                .putFloat(KEY_AVAIL, (float) avail)
                .apply();
    }

    private static SharedPreferences prefs() {
        // 每个账号一套存档，和别的仓库一样
        return context == null ? null
                : context.getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }

    private static File file(Card card) {
        return context == null ? null
                : new File(context.getFilesDir(),
                        "withdraw_" + card.id + Accounts.suffix() + ".png");
    }

    private static void write(Card card, Bitmap value) {
        File target = file(card);
        if (target == null || value == null) return;
        Bitmap scaled = value;
        int longest = Math.max(value.getWidth(), value.getHeight());
        if (longest > ICON_MAX) {
            float factor = ICON_MAX / (float) longest;
            scaled = Bitmap.createScaledBitmap(value, Math.max(1, Math.round(value.getWidth() * factor)),
                    Math.max(1, Math.round(value.getHeight() * factor)), true);
        }
        try (OutputStream out = new FileOutputStream(target)) {
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            // the change still shows for this run; it just does not survive the next one
        } finally {
            if (scaled != value) scaled.recycle();
        }
    }
}
