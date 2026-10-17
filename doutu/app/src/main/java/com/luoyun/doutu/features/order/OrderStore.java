package com.luoyun.doutu.features.order;

import android.content.Context;
import android.content.SharedPreferences;

import com.luoyun.doutu.features.zhi.data.Accounts;

import java.io.File;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Random;

/**
 * 「淘宝订单详情」这一页的数据：店铺、商品、金额、收货信息、订单号、交易号、时间、底下的推荐位。
 * 每个账号存一份，规则和别的仓库一样（后缀取自 {@link Accounts#suffix()}）。
 *
 * <p>页面上的每一项都能改，改完就落在这份存档里 —— 杀掉后台再打开还是用户改过的那一版
 * （产品要求：客户端里用户做过的一切修改都要留住）。
 *
 * <p><b>首次打开时随机一套</b>：收货人、手机号、地址、订单号、支付宝交易号、创建/成交时间
 * 都是当场随机出来的，不沿用任何真实信息；店铺名、商品、价格、推荐位这些照用户给的参考图来
 * （那些不是隐私）。
 */
public final class OrderStore {
    private static final String PREFS = "order_store";
    private static final String KEY_INIT = "init";

    /* 顶部标题栏 */
    public static final String TITLE = "order_title";                // 交易成功

    /* 顶部地址卡 */
    public static final String ADDRESS = "address";                 // 短地址：申汉大厦c座1401
    public static final String NAME = "name";                       // 收货人
    public static final String PHONE = "phone";                     // 11 位，不带 86-
    public static final String FULL_ADDRESS = "full_address";       // 收货信息那一整行

    /* 店铺 */
    public static final String SHOP = "shop";
    public static final String SHOP_NOTE = "shop_note";

    /* 商品 */
    public static final String GOOD_TITLE = "good_title";
    public static final String GOOD_SPEC = "good_spec";
    public static final String GOOD_PRICE = "good_price";           // 商品原价
    public static final String GOOD_COUNT = "good_count";
    public static final String PAID_PRICE = "paid_price";           // 实付价
    public static final String REFUND_NOTE = "refund_note";

    /* 金额 */
    public static final String TOTAL_PRICE = "total_price";
    public static final String DISCOUNT = "discount";
    public static final String PAYABLE = "payable";

    /* 订单 */
    public static final String ORDER_NO = "order_no";
    public static final String INVOICE = "invoice";
    public static final String TRADE_NO = "trade_no";
    public static final String CREATE_TIME = "create_time";
    public static final String DEAL_TIME = "deal_time";
    public static final String GUARANTEE = "guarantee";
    public static final String GUARANTEE_COUNT = "guarantee_count";

    /* 推荐位：6 个，每个四行 */
    public static final int REC_COUNT = 6;
    private static final String REC_TITLE = "rec%d_title";
    private static final String REC_PRICE = "rec%d_price";
    private static final String REC_TAG = "rec%d_tag";
    private static final String REC_SOLD = "rec%d_sold";

    /** 自己换的商品图存这儿；没换过就用 drawable 里那几张。 */
    private static final String CUSTOM_IMAGE = "custom_image";
    private static final String IMAGE_DIR = "order_assets";
    private static final String IMAGE_NAME = "goods";

    /* 参考图上的默认值（这些不是隐私，照原样） */
    private static final String[][] REC_DEFAULTS = {
            {"枪神ROG 枪神8 Plus超", "7399", "券后价", "已售40+"},
            {"巧克力脆脆杯饼干脆皮", "11.8", "首单价", "已售1万+"},
            {"爱兰纯一水肌酸粉300g", "18.9", "补贴价", "全网热销6万+"},
            {"ChirpyKnight男士内裤", "40", "补贴价", "已售3万+"},
            {"欧路得美式baggy丹宁", "74.3", "直降价", "已售1万+"},
            {"【预售开始-立即付】", "598", "券后价", "已售5000+"},
    };

    private static final String[] SURNAME_NAME = {
            "何柏玮", "林知夏", "江照野", "陈小满", "苏晚舟", "沈砚青", "温以宁", "顾青野",
            "许星野", "周牧遥", "叶疏影", "程野", "秦昭", "谢云舟", "黎见月", "白露",
            "张一鸣", "王思远", "赵屿", "孟星洲"};
    /** 省市（直辖市省名和市名相同，和参考图「上海 上海市」一个写法）。 */
    private static final String[][] CITIES = {
            {"上海", "上海市"}, {"北京", "北京市"}, {"广东", "深圳市"}, {"广东", "广州市"},
            {"浙江", "杭州市"}, {"四川", "成都市"}, {"湖北", "武汉市"}, {"陕西", "西安市"},
            {"江苏", "南京市"}, {"重庆", "重庆市"}};
    private static final String[] DISTRICTS = {
            "普陀区", "浦东新区", "朝阳区", "海淀区", "南山区", "天河区", "西湖区", "武侯区",
            "洪山区", "雁塔区", "鼓楼区", "渝北区"};
    private static final String[] TOWNS = {
            "长征镇", "张江镇", "望京街道", "中关村街道", "粤海街道", "石牌街道", "文三路街道",
            "桂溪街道", "关山街道", "小寨路街道", "湖南路街道", "龙溪街道"};
    private static final String[] BUILDINGS = {
            "申汉大厦c座1401", "望京SOHO T2 1806", "环球中心B座2201", "未来科技城5号楼903",
            "天河城写字楼A栋1502", "华润大厦2701", "银泰城3号楼1108", "万科金域华府2栋1801",
            "绿地中心A座906", "保利国际广场2栋1503"};

    private static Context context;
    private static boolean loaded;
    private static final LinkedHashMap<String, String> values = new LinkedHashMap<>();
    private static final Random random = new Random();

    private OrderStore() {
    }

    public static void attach(Context value) {
        if (value == null) return;
        context = value.getApplicationContext();
        load();
    }

    public static void reset() {
        loaded = false;
        values.clear();
    }

    /** 读一项；没存过（或存成空串）就回默认值。 */
    public static String get(String key) {
        load();
        String value = values.get(key);
        return value == null ? "" : value;
    }

    /** 写一项：内存和存档一起改，下一次打开这一页就是改过的样子。 */
    public static void set(String key, String value) {
        load();
        String text = value == null ? "" : value.trim();
        values.put(key, text);
        SharedPreferences prefs = prefs();
        if (prefs != null) prefs.edit().putString(key, text).apply();
    }

    /** 一次改好几项（「全部随机」用），只写一次盘。 */
    public static void setAll(LinkedHashMap<String, String> changed) {
        load();
        values.putAll(changed);
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        for (LinkedHashMap.Entry<String, String> entry : changed.entrySet()) {
            editor.putString(entry.getKey(), entry.getValue());
        }
        editor.apply();
    }

    public static String recTitle(int index) {
        return get(String.format(Locale.US, REC_TITLE, index));
    }

    public static String recPrice(int index) {
        return get(String.format(Locale.US, REC_PRICE, index));
    }

    public static String recTag(int index) {
        return get(String.format(Locale.US, REC_TAG, index));
    }

    public static String recSold(int index) {
        return get(String.format(Locale.US, REC_SOLD, index));
    }

    /** 顶部卡片上那个打了码的号码：86-159****0520。 */
    public static String maskedPhone() {
        return "86-" + mask(get(PHONE));
    }

    private static String mask(String phone) {
        if (phone.length() < 7) return phone;
        // 参考图那四个星号是按全角排的（每个占一个汉字宽），半角排出来会短一截，
        // 这一行就和图对不上了；所以这里用全角星号。
        return phone.substring(0, 3) + "\uFF0A\uFF0A\uFF0A\uFF0A"
                + phone.substring(phone.length() - 4);
    }

    /** 「何柏玮，86-15972450520，上海 上海市 ……」——参考图里收货信息那一行的写法。 */
    public static String receiverLine() {
        return get(NAME) + "，86-" + get(PHONE) + "，" + get(FULL_ADDRESS);
    }

    /* ------------------------------------------------------------------ 商品图 */

    public static boolean hasCustomImage() {
        File file = imageFile();
        return file != null && file.isFile();
    }

    public static File imageFile() {
        if (context == null) return null;
        return new File(new File(context.getFilesDir(), IMAGE_DIR),
                IMAGE_NAME + Accounts.suffix() + ".png");
    }

    /** 把相册里选的那张图存下来当商品图。 */
    public static void saveImage(android.graphics.Bitmap bitmap) {
        File file = imageFile();
        if (file == null || bitmap == null) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (java.io.OutputStream out = new java.io.FileOutputStream(file)) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            set(CUSTOM_IMAGE, "1");
        } catch (Exception e) {
            // 这一次运行里还能看到，只是留不到下一次
        }
    }

    public static void clearImage() {
        File file = imageFile();
        if (file != null && file.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        set(CUSTOM_IMAGE, "");
    }

    /* ------------------------------------------------------------------ 存档 */

    /**
     * 第一次打开（存档里没有 init 标记）时铺一套值：参考图那一套 + 隐私项当场随机。
     */
    private static void load() {
        if (loaded || context == null) return;
        loaded = true;
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        if (!prefs.getBoolean(KEY_INIT, false)) {
            SharedPreferences.Editor editor = prefs.edit();
            LinkedHashMap<String, String> fresh = freshOrder();
            values.putAll(fresh);
            for (LinkedHashMap.Entry<String, String> entry : fresh.entrySet()) {
                editor.putString(entry.getKey(), entry.getValue());
            }
            editor.putBoolean(KEY_INIT, true).apply();
            return;
        }
        for (java.util.Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getValue() instanceof String) values.put(entry.getKey(), (String) entry.getValue());
        }
        // 自愈：参考图那一套里缺了哪一项就补哪一项（比如存档是很早的版本，那时候还没铺这些键）
        LinkedHashMap<String, String> heal = freshOrder();
        SharedPreferences.Editor editor = null;
        for (LinkedHashMap.Entry<String, String> entry : heal.entrySet()) {
            if (values.containsKey(entry.getKey())) continue;
            values.put(entry.getKey(), entry.getValue());
            if (editor == null) editor = prefs.edit();
            editor.putString(entry.getKey(), entry.getValue());
        }
        if (editor != null) editor.apply();
    }

    /** 新建一条订单：参考图那一套打底，隐私项盖成随机的。 */
    private static LinkedHashMap<String, String> freshOrder() {
        LinkedHashMap<String, String> fresh = defaults();
        fresh.putAll(randomize());
        return fresh;
    }

    /** 参考图上的那些值（店铺、商品、金额、推荐位）—— 不是隐私，照原样。 */
    private static LinkedHashMap<String, String> defaults() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put(TITLE, "交易成功");
        map.put(SHOP, "Apple Store 官方旗舰店");
        map.put(SHOP_NOTE, "88VIP好评率98%，平均2天退款");
        map.put(GOOD_TITLE, "【优惠价】 Apple/苹果 iPhone...");
        map.put(GOOD_SPEC, "星宇橙色;256GB");
        map.put(GOOD_PRICE, "9999");
        map.put(GOOD_COUNT, "1");
        map.put(PAID_PRICE, "9699");
        map.put(REFUND_NOTE, "极速退款 7天无理由退换");
        map.put(TOTAL_PRICE, "9999");
        map.put(DISCOUNT, "300");
        map.put(PAYABLE, "9699");
        for (int i = 0; i < REC_COUNT; i++) {
            map.put(String.format(Locale.US, REC_TITLE, i), REC_DEFAULTS[i][0]);
            map.put(String.format(Locale.US, REC_PRICE, i), REC_DEFAULTS[i][1]);
            map.put(String.format(Locale.US, REC_TAG, i), REC_DEFAULTS[i][2]);
            map.put(String.format(Locale.US, REC_SOLD, i), REC_DEFAULTS[i][3]);
        }
        return map;
    }

    /**
     * 重新随机一套（「全部随机」和「删除订单」后用）：只动隐私的那几项和金额以外的编号，
     * 店铺 / 商品 / 价格 / 推荐位保持用户改过的样子。
     */
    public static LinkedHashMap<String, String> randomize() {
        LinkedHashMap<String, String> fresh = new LinkedHashMap<>();
        int cityIndex = random.nextInt(CITIES.length);
        String province = CITIES[cityIndex][0];
        String city = CITIES[cityIndex][1];
        String district = DISTRICTS[random.nextInt(DISTRICTS.length)];
        String town = TOWNS[random.nextInt(TOWNS.length)];
        String building = BUILDINGS[random.nextInt(BUILDINGS.length)];
        String phone = randomPhone();
        String create = randomTime();

        fresh.put(ADDRESS, building);
        fresh.put(NAME, SURNAME_NAME[random.nextInt(SURNAME_NAME.length)]);
        fresh.put(PHONE, phone);
        fresh.put(FULL_ADDRESS, province + " " + city + " " + district + " " + town + " " + building);
        fresh.put(ORDER_NO, randomDigits(19));
        fresh.put(TRADE_NO, create.replace("-", "").substring(0, 8).replace("/", "")
                + "2200" + randomDigits(16));
        fresh.put(CREATE_TIME, create);
        fresh.put(DEAL_TIME, addMinutes(create, 3 + random.nextInt(20)));
        fresh.put(INVOICE, "个人");
        fresh.put(GUARANTEE_COUNT, "2");
        fresh.put(GUARANTEE, "依据：" + dateOf(create) + "下单交易快照");
        return fresh;
    }

    /**
     * 一条全新的订单：隐私项随机 + 店铺商品金额恢复成参考图那一套。
     * 「删除订单」走这条，删掉之后下一次打开是一条新订单。
     */
    public static void resetToNewOrder() {
        load();
        LinkedHashMap<String, String> fresh = freshOrder();
        values.clear();
        values.putAll(fresh);
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        for (LinkedHashMap.Entry<String, String> entry : fresh.entrySet()) {
            editor.putString(entry.getKey(), entry.getValue());
        }
        editor.putBoolean(KEY_INIT, true).apply();
        clearImage();
    }

    /** 「恢复默认」：把这一页全部退回参考图那一套（隐私项重新随机一次）。 */
    public static void restoreDefaults() {
        resetToNewOrder();
    }

    private static String randomPhone() {
        String[] prefix = {"130", "131", "132", "133", "135", "136", "137", "138", "139",
                "150", "151", "152", "155", "156", "157", "158", "159",
                "173", "175", "176", "177", "178", "180", "181", "182", "183", "185", "186",
                "187", "188", "189"};
        StringBuilder out = new StringBuilder(prefix[random.nextInt(prefix.length)]);
        for (int i = 0; i < 8; i++) out.append(random.nextInt(10));
        return out.toString();
    }

    private static String randomDigits(int count) {
        StringBuilder out = new StringBuilder(count);
        out.append(1 + random.nextInt(9));
        for (int i = 1; i < count; i++) out.append(random.nextInt(10));
        return out.toString();
    }

    /** 最近两个月里随便一个时间，写成参考图那个格式。 */
    private static String randomTime() {
        Calendar when = Calendar.getInstance();
        when.add(Calendar.DAY_OF_YEAR, -random.nextInt(60));
        when.set(Calendar.HOUR_OF_DAY, 8 + random.nextInt(14));
        when.set(Calendar.MINUTE, random.nextInt(60));
        when.set(Calendar.SECOND, random.nextInt(60));
        return String.format(Locale.US, "%04d-%02d-%02d %02d:%02d:%02d",
                when.get(Calendar.YEAR), when.get(Calendar.MONTH) + 1,
                when.get(Calendar.DAY_OF_MONTH), when.get(Calendar.HOUR_OF_DAY),
                when.get(Calendar.MINUTE), when.get(Calendar.SECOND));
    }

    private static String dateOf(String time) {
        return time.length() >= 10 ? time.substring(0, 10) : time;
    }

    private static String addMinutes(String time, int minutes) {
        try {
            java.text.SimpleDateFormat format =
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            java.util.Date date = format.parse(time);
            if (date == null) return time;
            Calendar when = Calendar.getInstance();
            when.setTime(date);
            when.add(Calendar.MINUTE, minutes);
            return format.format(when.getTime());
        } catch (Exception e) {
            return time;
        }
    }

    private static SharedPreferences prefs() {
        return context == null
                ? null : context.getSharedPreferences(PREFS + Accounts.suffix(), Context.MODE_PRIVATE);
    }
}
