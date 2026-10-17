package com.luoyun.doutu.features.zhi.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * The 收款记录 page's records: the one seeded row the reference capture shows, plus every record the
 * user adds from the 批量添加 sheet, kept per account in the app's own preferences.
 *
 * <p>The reference was taken on a day with nothing in it yet — the 今日 card says 今天暂无收款记录 —
 * and shows a single 20.00 collection from 5月4日. That one record is what this store starts with, so
 * the page reads exactly like the capture the first time it is opened; everything after that is the
 * user's: the sheet adds records to a month of his choosing, the detail page edits them, and both
 * survive the page and the process.
 *
 * <p>Nothing here is a real payment, a real customer or a real amount.
 */
public final class ReceiptStore {
    private static final String PREFS = "receipt_store";
    private static final String KEY_RECORDS = "records";
    /** The picture a payer's row may carry is kept at most this large. */
    private static final int AVATAR_MAX = 512;

    /** How many records one run of the 批量添加 sheet may add, the cap the original app also keeps. */
    public static final int MAX_BATCH = 100;

    /**
     * How many payers' pictures one round asks the random-avatar service for. A batch may add a
     * hundred records at once, so the round is capped rather than firing a hundred requests: the
     * next time the page is opened the ones still showing the grey default are asked for again.
     */
    public static final int PRIME_MAX = 60;

    /** The size a fetched payer picture is decoded at: the row and the card never show more. */
    private static final int PRIME_PX = 192;

    /** The five spreads the sheet offers, 自定义 left out. */
    public static final String[] TIER_NAMES = {"小额", "正常", "较大", "大额", "老板"};
    public static final double[][] TIER_RANGES = {
            {0.1, 50}, {51, 300}, {301, 1000}, {1001, 50000}, {50001, 100000}};

    /** The payers the sheet draws from; the reference's own capture shows the first of them. */
    private static final String[] PAYERS = {
            "**峻", "**华", "**明", "**丽", "**涛", "**静", "**芳", "**磊", "**伟", "**敏",
            "**强", "**霞", "**鹏", "**婷", "**军", "**洋"};

    private static final String[] PRIZES = {
            "10积分未领取已过期", "5积分已领取", "20积分未领取已过期", "3积分已领取"};

    private static Context context;
    private static boolean loaded;
    private static final List<Receipt> records = new ArrayList<>();
    private static final Random random = new Random();
    /** The records a round of {@link #primeAvatars} has already asked the service for. */
    private static final Set<Long> avatarWanted = new HashSet<>();

    private ReceiptStore() {
    }

    /** Hands the store the context it persists through and reads the last run's records back. */
    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    /** The records, newest first. */
    public static List<Receipt> records() {
        load();
        sort();
        return records;
    }

    public static Receipt byId(long id) {
        load();
        for (Receipt r : records) if (r.id == id) return r;
        return null;
    }

    /** Writes a record back after the detail page has edited it. */
    public static void save(Receipt receipt) {
        load();
        if (receipt != null && !records.contains(receipt)) records.add(receipt);
        sort();
        persist();
    }

    /** 换账号时把这一份缓存丢掉，下一次问它读到的就是新账号的存档。 */
    public static void reset() {
        loaded = false;
        records.clear();
        avatarWanted.clear();
    }

    // ------------------------------------------------------------------ the payers' pictures

    /**
     * Asks the module's own random-avatar service for the payers that are still showing the grey
     * default, {@code max} of them per round, and keeps each picture as it arrives.
     *
     * <p>The original app fills its merchant records the same way, from the same service the 个人信息
     * 头像 sheet and the 财富黑卡理财师 card use — so an incoming 转账 carries a face rather than a
     * placeholder. A picture the user picks himself (from the sheet the row's avatar opens, which also
     * offers 刷新 and 从本地上传) is never overwritten: only records with no picture at all are asked
     * for. One that fails is left for the next round.
     *
     * @param max       how many records this round may ask for
     * @param onChanged run on the main thread each time a picture lands, so the page can redraw
     */
    public static void primeAvatars(int max, Runnable onChanged) {
        load();
        if (context == null) return;
        final List<Receipt> pending = new ArrayList<>();
        for (Receipt r : records) {
            if (r.avatar != null || avatarWanted.contains(r.id)) continue;
            pending.add(r);
            if (pending.size() >= max) break;
        }
        if (pending.isEmpty()) return;
        for (Receipt r : pending) avatarWanted.add(r.id);
        AvatarApi.fetchBatch(pending.size(), PRIME_PX, (index, bitmap) -> {
            Receipt target = pending.get(index);
            if (bitmap == null) {
                // nothing came back: let a later round try this one again
                avatarWanted.remove(target.id);
                return;
            }
            avatar(target, bitmap);
            if (onChanged != null) onChanged.run();
        });
    }

    // ------------------------------------------------------------------ the sheet

    /** One line of the 批量添加 sheet: a spread, how many to make and — for 自定义 — the bounds. */
    public static final class Spec {
        public final String name;
        public final double min;
        public final double max;
        public final int count;

        public Spec(String name, double min, double max, int count) {
            this.name = name;
            this.min = min;
            this.max = max;
            this.count = count;
        }
    }

    /**
     * Adds what the sheet asked for: {@code count} records per spec, each amount drawn uniformly from
     * its spread and rounded to two decimals, each time dropped somewhere inside {@code year}-{@code month}.
     *
     * @return how many records were made
     */
    public static int add(List<Spec> specs, int year, int month) {
        load();
        int made = 0;
        for (Spec spec : specs) {
            for (int i = 0; i < spec.count && made < MAX_BATCH; i++) {
                Receipt r = new Receipt();
                r.amount = round2(spec.min + random.nextDouble() * Math.max(0.01, spec.max - spec.min));
                r.actual = r.amount;
                r.quota = r.amount;
                r.payer = PAYERS[random.nextInt(PAYERS.length)];
                r.prize = PRIZES[random.nextInt(PRIZES.length)];
                r.time = timeIn(year, month);
                r.id = r.time * 1000L + made;
                records.add(r);
                made++;
            }
        }
        sort();
        persist();
        return made;
    }

    /**
     * A moment anywhere inside the given month: every day it has is fair game, whatever day of it
     * today happens to be, and any hour of that day — the dates are dealt out over the whole month
     * the sheet was pointed at rather than crowded into the part of it that has already run.
     */
    private static long timeIn(int year, int month) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, year);
        c.set(Calendar.MONTH, month - 1);
        c.set(Calendar.DAY_OF_MONTH, 1 + random.nextInt(c.getActualMaximum(Calendar.DAY_OF_MONTH)));
        c.set(Calendar.HOUR_OF_DAY, random.nextInt(24));
        c.set(Calendar.MINUTE, random.nextInt(60));
        c.set(Calendar.SECOND, random.nextInt(60));
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // ------------------------------------------------------------------ the detail page's columns

    /** What the 顾客画像 columns count for one payer. */
    public static final class Stats {
        public double monthSpend;
        public int monthCount;
        public double totalSpend;
        public int totalCount;

        public double avgPrice() {
            return totalCount == 0 ? 0 : totalSpend / totalCount;
        }
    }

    /** Adds up everything the given payer has spent with this account, this month and in total. */
    public static Stats stats(String payer) {
        load();
        Stats s = new Stats();
        Calendar now = Calendar.getInstance();
        for (Receipt r : records) {
            if (r.payer == null || !r.payer.equals(payer)) continue;
            s.totalSpend += r.amount;
            s.totalCount++;
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(r.time);
            if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                    && c.get(Calendar.MONTH) == now.get(Calendar.MONTH)) {
                s.monthSpend += r.amount;
                s.monthCount++;
            }
        }
        return s;
    }

    // ------------------------------------------------------------------ shapes the pages share

    /** Two decimals, the way every figure on these two pages is written. */
    public static String money(double value) {
        return String.format(Locale.CHINA, "%.2f", value);
    }

    /** The amount as the list writes it: ¥20.00. */
    public static String yuan(double value) {
        return "¥" + money(value);
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static long startOfDay(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static boolean sameDay(long a, long b) {
        return startOfDay(a) == startOfDay(b);
    }

    /** M月d日, the day heading the list prints above a card. */
    public static String dayLabel(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        return (c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日";
    }

    /** HH:mm:ss, the time under a payer's name. */
    public static String clock(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        return String.format(Locale.CHINA, "%02d:%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));
    }

    // ------------------------------------------------------------------ the copies that outlive the run

    private static void sort() {
        Collections.sort(records, (a, b) -> Long.compare(b.time, a.time));
    }

    private static String key(Receipt r) {
        return Long.toString(r.id);
    }

    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        records.clear();
        SharedPreferences prefs = prefs();
        String kept = prefs == null ? null : prefs.getString(KEY_RECORDS, null);
        if (kept == null || kept.isEmpty()) {
            seed();
            persist();
            return;
        }
        try {
            JSONArray array = new JSONArray(kept);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Receipt r = new Receipt();
                r.id = o.optLong("id");
                r.time = o.optLong("time");
                r.amount = o.optDouble("amount");
                r.actual = o.optDouble("actual", r.amount);
                r.quota = o.optDouble("quota", r.actual);
                r.payer = o.optString("payer", r.payer);
                r.prize = o.optString("prize", r.prize);
                r.note = o.optString("note", r.note);
                if (o.has("monthSpend")) r.monthSpend = o.optDouble("monthSpend");
                if (o.has("monthCount")) r.monthCount = o.optInt("monthCount");
                if (o.has("totalSpend")) r.totalSpend = o.optDouble("totalSpend");
                if (o.has("totalCount")) r.totalCount = o.optInt("totalCount");
                if (o.has("avgPrice")) r.avgPrice = o.optDouble("avgPrice");
                r.avatar = readAvatar(r);
                records.add(r);
            }
        } catch (Exception e) {
            records.clear();
            seed();
        }
        sort();
    }

    /**
     * What the page opens on: today's own card carries a 30.00 collection, and the reference capture's
     * 20.00 from 5月4日 sits under it as the day before it. The page used to open on an empty 今日 card
     * — the illustration and 今天暂无收款记录 — and that card is gone: a card now says what came in.
     */
    private static void seed() {
        records.add(make(todayEarlier(), 30.00, PAYERS[1]));
        Calendar c = Calendar.getInstance();
        c.set(Calendar.MONTH, Calendar.MAY);
        c.set(Calendar.DAY_OF_MONTH, 4);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 16);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() > System.currentTimeMillis()) c.add(Calendar.YEAR, -1);
        records.add(make(c.getTimeInMillis(), 20.00, PAYERS[0]));
    }

    private static Receipt make(long time, double amount, String payer) {
        Receipt r = new Receipt();
        r.time = time;
        r.id = time;
        r.amount = amount;
        r.actual = amount;
        r.quota = amount;
        r.payer = payer;
        r.prize = PRIZES[0];
        r.note = "收钱码收款";
        return r;
    }

    /** Earlier today, so today's own seed row reads as one that has already happened. */
    private static long todayEarlier() {
        long time = System.currentTimeMillis() - (1 + random.nextInt(3)) * 60L * 60L * 1000L;
        return time - time % 1000L;
    }

    private static void persist() {
        if (context == null) return;
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        JSONArray array = new JSONArray();
        for (Receipt r : records) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", r.id);
                o.put("time", r.time);
                o.put("amount", r.amount);
                o.put("actual", r.actual);
                o.put("quota", r.quota);
                o.put("payer", r.payer);
                o.put("prize", r.prize);
                o.put("note", r.note);
                if (r.monthSpend != null) o.put("monthSpend", (double) r.monthSpend);
                if (r.monthCount != null) o.put("monthCount", (int) r.monthCount);
                if (r.totalSpend != null) o.put("totalSpend", (double) r.totalSpend);
                if (r.totalCount != null) o.put("totalCount", (int) r.totalCount);
                if (r.avgPrice != null) o.put("avgPrice", (double) r.avgPrice);
            } catch (Exception e) {
                // a field that will not go in simply does not survive the next run
            }
            array.put(o);
        }
        prefs.edit().putString(KEY_RECORDS, array.toString()).apply();
    }

    private static SharedPreferences prefs() {
        // 每个账号一套存档，和别的仓库一样
        return context == null ? null
                : context.getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ a payer's picture

    /** The payer's own picture, or null to fall back on the grey one cut out of the reference. */
    public static Bitmap avatar(Receipt receipt) {
        load();
        return receipt == null ? null : receipt.avatar;
    }

    public static void avatar(Receipt receipt, Bitmap value) {
        if (receipt == null) return;
        receipt.avatar = value;
        File target = file(receipt);
        if (target == null) return;
        if (value == null) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            return;
        }
        Bitmap scaled = shrink(value);
        try (OutputStream out = new FileOutputStream(target)) {
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            // the change still shows for this run; it just does not survive the next one
        } finally {
            if (scaled != value) scaled.recycle();
        }
    }

    private static File file(Receipt receipt) {
        return context == null ? null
                : new File(context.getFilesDir(), "receipt_" + key(receipt) + Accounts.suffix() + ".png");
    }

    private static Bitmap readAvatar(Receipt receipt) {
        File source = file(receipt);
        if (source == null || !source.isFile()) return null;
        try {
            return BitmapFactory.decodeFile(source.getAbsolutePath());
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap shrink(Bitmap value) {
        int longest = Math.max(value.getWidth(), value.getHeight());
        if (longest <= AVATAR_MAX) return value;
        float factor = AVATAR_MAX / (float) longest;
        return Bitmap.createScaledBitmap(value, Math.max(1, Math.round(value.getWidth() * factor)),
                Math.max(1, Math.round(value.getHeight() * factor)), true);
    }
}
