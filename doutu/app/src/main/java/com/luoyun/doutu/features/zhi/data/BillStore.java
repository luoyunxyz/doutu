package com.luoyun.doutu.features.zhi.data;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The 账单 page's rows, generated fresh every time the page is opened.
 *
 * <p>The months the page shows are worked out from today's date — the month the app is opened in
 * comes first, then the two before it — and each of them is filled with orders placed inside it, so
 * the page always reads as "the current month, and what came before". Every order is made up here:
 * the merchant, its mark, the amount, the time, the two order numbers and the rest. Only the shapes
 * are the reference's, never its figures — the amounts are drawn from a spread of plausible ones
 * and the order numbers keep the reference's lengths (the 订单号 is 28 digits and the 商家订单号 15
 * characters) without ever repeating one.
 *
 * <p>The 账单详情 page edits the very {@link Bill} it was opened with, so a change shows on the list
 * behind it as soon as it comes back. Nothing here is an account, a merchant or a real order.
 */
public final class BillStore {
    /** How many months the page carries, the current one first. */
    private static final int MONTHS = 3;
    /** How many orders a month holds; the current month gets no more than the days it has run. */
    private static final int ORDERS_MIN = 5;
    private static final int ORDERS_MAX = 9;

    /** 交易名称, 分类, the mark's characters, its disc, its ink, and the 收款方全称 behind it. */
    private static final String[][] SHOPS = {
            {"花呗", "信用借还", "花呗", "ff1677ff", "ffffffff", "蚂蚁智信（杭州）信息技术有限公司"},
            {"闲鱼", "日用百货", "闲鱼", "fff5d003", "ff1f1f1f", "浙江阿里巴巴闲鱼网络科技有限公司"},
            {"中国移动", "充值缴费", "移动", "ff2fa37a", "ffffffff", "中国移动通信集团湖北有限公司"},
            {"恩施公交", "交通出行", "公交", "ff1677ff", "ffffffff", "恩施市公共交通有限责任公司"},
            {"湖北交管", "公共服务", "交管", "ffe8452b", "ffffffff", "湖北省公安厅交通管理局"},
            {"云猫寄售", "日用百货", "云猫", "ff2f8fe0", "ffffffff", "南京爱维云网络科技有限公司"},
            {"抖音电商", "日用百货", "抖音", "ff1f1f1f", "ffffffff", "上海格物致品网络科技有限公司"},
            {"小米商城", "日用百货", "小米", "ffff6a00", "ffffffff", "小米通讯技术有限公司"},
            {"Token Plan", "商业服务", "T", "ff2b2b33", "ffffffff", "上海稀宇科技有限公司"},
            {"淘宝", "日用百货", "淘宝", "ffff5000", "ffffffff", "浙江淘宝网络有限公司"},
            {"美团", "日用百货", "美团", "ffffc300", "ff1f1f1f", "北京三快在线科技有限公司"},
            {"京东", "日用百货", "京东", "ffe2231a", "ffffffff", "北京京东世纪贸易有限公司"},
            {"滴滴出行", "交通出行", "滴滴", "ffff7d41", "ffffffff", "滴滴出行科技有限公司"},
            {"肯德基", "餐饮美食", "KFC", "ffe4002b", "ffffffff", "百胜餐饮（武汉）有限公司"},
            {"瑞幸咖啡", "餐饮美食", "瑞幸", "ff002d62", "ffffffff", "瑞幸咖啡（中国）有限公司"},
            {"中国联通", "充值缴费", "联通", "ffe60012", "ffffffff", "中国联合网络通信有限公司"},
            {"哔哩哔哩", "文化娱乐", "B站", "fffb7299", "ffffffff", "上海幻电信息科技有限公司"},
            {"网易云音乐", "文化娱乐", "网易", "ffc20c0c", "ffffffff", "杭州网易云音乐科技有限公司"},
            {"哈啰单车", "交通出行", "单车", "ff00b578", "ffffffff", "上海钧正网络科技有限公司"},
            {"永辉超市", "日用百货", "永辉", "ff009e4f", "ffffffff", "永辉超市股份有限公司"},
            {"屈臣氏", "日用百货", "屈臣", "ff00a0a0", "ffffffff", "广州屈臣氏个人用品商店有限公司"},
            {"顺丰速运", "生活服务", "顺丰", "ff1f1f1f", "ffffffff", "顺丰速运有限公司"},
            // ——— 以下为扩展的商家池，账单随机从这里抽 ———
            {"麦当劳", "餐饮美食", "麦", "ffffbc00", "ff1f1f1f", "金拱门（中国）有限公司"},
            {"星巴克", "餐饮美食", "星", "ff00704a", "ffffffff", "上海星巴克咖啡经营有限公司"},
            {"喜茶", "餐饮美食", "喜", "ff1f1f1f", "ffffffff", "深圳美西西餐饮管理有限公司"},
            {"蜜雪冰城", "餐饮美食", "蜜", "ffe60012", "ffffffff", "蜜雪冰城股份有限公司"},
            {"海底捞", "餐饮美食", "海", "ffd92b2b", "ffffffff", "四川海底捞餐饮股份有限公司"},
            {"必胜客", "餐饮美食", "必", "ff158a44", "ffffffff", "上海必胜客有限公司"},
            {"汉堡王", "餐饮美食", "汉", "ffe8452b", "ffffffff", "汉堡王（中国）投资有限公司"},
            {"塔斯汀", "餐饮美食", "塔", "ffd92b2b", "ffffffff", "上海塔斯汀餐饮管理有限公司"},
            {"美团外卖", "餐饮美食", "外卖", "ffffc300", "ff1f1f1f", "北京三快在线科技有限公司"},
            {"饿了么", "餐饮美食", "饿了", "ff1677ff", "ffffffff", "拉扎斯网络科技（上海）有限公司"},
            {"天猫超市", "日用百货", "天猫", "ffe2231a", "ffffffff", "浙江天猫技术有限公司"},
            {"拼多多", "日用百货", "拼", "ffe8452b", "ffffffff", "上海寻梦信息技术有限公司"},
            {"得物", "日用百货", "得物", "ff1f1f1f", "ffffffff", "上海识装信息科技有限公司"},
            {"苏宁易购", "日用百货", "苏宁", "ff1c62d6", "ffffffff", "苏宁易购集团股份有限公司"},
            {"华为商城", "日用百货", "华为", "ffcf0a2c", "ffffffff", "华为终端有限公司"},
            {"苹果商店", "日用百货", "苹果", "ff1f1f1f", "ffffffff", "苹果电脑贸易（上海）有限公司"},
            {"盒马鲜生", "日用百货", "盒马", "ff1f9bd1", "ffffffff", "上海盒马网络科技有限公司"},
            {"沃尔玛", "日用百货", "沃尔", "ff0071ce", "ffffffff", "沃尔玛（中国）投资有限公司"},
            {"7-ELEVEN", "日用百货", "7-11", "ffec6c00", "ffffffff", "柒一拾壹（中国）商业有限公司"},
            {"美团单车", "交通出行", "单车", "ffffc300", "ff1f1f1f", "北京摩拜科技有限公司"},
            {"高德打车", "交通出行", "高德", "ff1677ff", "ffffffff", "高德软件有限公司"},
            {"中国铁路", "交通出行", "铁路", "ff1f6fb2", "ffffffff", "中国国家铁路集团有限公司"},
            {"携程旅行", "交通出行", "携程", "ff2577e3", "ffffffff", "上海携程商务有限公司"},
            {"飞猪旅行", "交通出行", "飞猪", "ffff6a00", "ffffffff", "浙江飞猪网络技术有限公司"},
            {"中国电信", "充值缴费", "电信", "ff0079c8", "ffffffff", "中国电信股份有限公司"},
            {"腾讯视频", "文化娱乐", "腾讯", "ff00a4ff", "ffffffff", "深圳市腾讯计算机系统有限公司"},
            {"爱奇艺", "文化娱乐", "爱奇", "ff00be06", "ffffffff", "北京爱奇艺科技有限公司"},
            {"优酷视频", "文化娱乐", "优酷", "ff1f9bff", "ffffffff", "优酷信息技术（北京）有限公司"},
            {"芒果TV", "文化娱乐", "芒果", "ffff7d00", "ffffffff", "湖南快乐阳光互动娱乐传媒有限公司"},
            {"QQ音乐", "文化娱乐", "QQ", "ff31c27c", "ffffffff", "深圳市腾讯音乐娱乐科技有限公司"},
            {"Steam", "文化娱乐", "S", "ff1b2838", "ffffffff", "Valve Corporation"},
            {"王者荣耀", "文化娱乐", "王者", "ff2b6cb0", "ffffffff", "深圳市腾讯计算机系统有限公司"},
            {"京东健康", "医疗健康", "健康", "ff16a1a1", "ffffffff", "北京京东健康有限公司"},
            {"叮当快药", "医疗健康", "叮当", "ff00a0e9", "ffffffff", "叮当快药科技集团有限公司"},
            {"菜鸟驿站", "生活服务", "菜鸟", "ff2f8fe0", "ffffffff", "浙江菜鸟供应链管理有限公司"},
            {"中国邮政", "生活服务", "邮政", "ff007a33", "ffffffff", "中国邮政集团有限公司"},
            {"京东到家", "生活服务", "到家", "ffe2231a", "ffffffff", "达疆网络科技（上海）有限公司"},
            {"腾讯云", "商业服务", "云", "ff00a4ff", "ffffffff", "腾讯云计算（北京）有限责任公司"},
            {"百度网盘", "商业服务", "百度", "ff2932e1", "ffffffff", "北京百度网讯科技有限公司"},
            {"信用卡还款", "信用借还", "信用", "ff1c62d6", "ffffffff", "招商银行股份有限公司信用卡中心"},
            {"顺丰同城急送", "生活服务", "急送", "ff1f1f1f", "ffffffff", "顺丰同城科技（深圳）有限公司"},
            {"京东健康", "医疗健康", "健康", "ff16a1a1", "ffffffff", "北京京东健康有限公司"},
            {"叮当快药", "医疗健康", "叮当", "ff00a0e9", "ffffffff", "叮当快药科技集团有限公司"},
            {"屈臣氏", "日用百货", "屈臣", "ff00a0a0", "ffffffff", "广州屈臣氏个人用品商店有限公司"},
            {"哈啰单车", "交通出行", "单车", "ff00b578", "ffffffff", "上海钧正网络科技有限公司"},
            {"携程旅行", "交通出行", "携程", "ff2577e3", "ffffffff", "上海携程商务有限公司"},
    };

    /** What the 商品说明 line says when it is not one of the order numbers. */
    private static final String[] GOODS = {
            "csdn代下，csdn下载，csdn代码可运行项目生成，cs",
            "红米 Buds 6Pro 耳机补配 单只左耳 白色",
            "超级会员日卡一天，解锁vip专属权益",
            "为15972450520话费充值",
            "城市公交乘车码扣费",
            "月度会员服务（自动续费）",
            "寄售服务费结算",
            "充电宝租借费用",
            "外卖订单（含配送费）",
            "云服务资源包—标准版",
            "饮用水 500ml×24 整箱",
            "打印服务 双面黑白 A4",
            "地铁乘车码扣费",
            "高铁票 二等座",
            "电影票 2 张",
            "咖啡中杯拿铁 少冰",
            "奶茶三兄弟 大杯",
            "手机屏幕贴膜服务",
            "健身房月卡续费",
            "网约车行程费用",
            "共享单车骑行 30 分钟",
            "生鲜水果 混合装 5 斤",
            "抽纸 3 层 24 包",
            "洗发水 750ml",
            "猫粮 冻干全价 2kg",
            "电费充值 200 元",
            "燃气费充值 100 元",
            "宽带续费 一年",
            "软件年度订阅",
            "视频会员 12 个月",
            "音乐会员 连续包月",
            "云存储扩容 200G",
            "流量包 10G 月包",
            "充电线 Type-C 1.5m",
            "机械键盘 青轴",
            "显示器支架 单臂",
            "办公椅 人体工学",
            "保温杯 316 不锈钢",
            "运动鞋 42 码",
            "羽绒服 冬季新款",
            "包子豆浆套餐",
            "黄焖鸡米饭 大份",
            "螺蛳粉 袋装 3 包",
            "火锅双人套餐",
            "药品代购服务",
            "眼镜镜片更换",
            "洗车服务 精洗",
            "停车费 2 小时",
            "高速通行费",
            "酒店住宿 1 晚",
    };

    /** The plausible amounts an order is drawn from, so a month never reads the same twice. */
    private static final double[] AMOUNTS = {
            0.99, 1.40, 2.00, 3.50, 5.00, 6.00, 8.80, 9.90, 12.50, 14.90, 15.80, 19.90,
            20.00, 25.00, 28.00, 32.00, 36.00, 45.00, 50.00, 61.60, 68.00, 77.00, 88.00,
            99.00, 128.00, 147.87, 168.00, 199.00, 258.00, 299.00,
    };

    /** One month of the page: its heading, its rows and what the two of them add up to. */
    public static final class Month {
        public final int year;
        public final int month;
        public final List<Bill> bills = new ArrayList<>();

        public Month(int year, int month) {
            this.year = year;
            this.month = month;
        }

        /** Money out, as the heading's 支出 reads it. */
        public double paid() {
            double sum = 0;
            for (Bill bill : bills) if (bill.counted && !bill.income()) sum -= bill.amount;
            return sum;
        }

        /** Money in, as the heading's 收入 reads it. */
        public double earned() {
            double sum = 0;
            for (Bill bill : bills) if (bill.counted && bill.income()) sum += bill.amount;
            return sum;
        }
    }

    private static final List<Month> months = new ArrayList<>();
    private static Month selectedMonth;
    private static Bill selected;
    private static final Random random = new Random();
    /** 用户点过“清空”之后就别再自动生成了，直到他自己再要一版。 */
    private static boolean cleared = false;
    /** What the page's 今年累计已省 badge reads; it is the page's own figure, not a sum. */
    private static double savedThisYear = 53;

    private BillStore() {
    }

    /**
     * Builds the page again for today: the current month first, then the two before it, each filled
     * with orders placed inside its own days.
     */
    public static void generate() {
        if (cleared) return;
        months.clear();
        selected = null;
        selectedMonth = null;
        Calendar now = Calendar.getInstance();
        for (int back = 0; back < MONTHS; back++) {
            Calendar when = (Calendar) now.clone();
            when.add(Calendar.MONTH, -back);
            months.add(fill(when, back == 0));
        }
        savedThisYear = 30 + random.nextInt(90) + random.nextDouble();
    }

    /** 清空：把账单全部删掉，之后不再自动重生，直到用户主动再要一版。 */
    public static void clear() {
        months.clear();
        selected = null;
        selectedMonth = null;
        cleared = true;
    }

    /** 秒变富豪：清掉旧账，塞满大额随机的进出账。 */
    public static void becomeRich() {
        cleared = false;
        months.clear();
        selected = null;
        selectedMonth = null;
        Calendar now = Calendar.getInstance();
        for (int back = 0; back < MONTHS; back++) {
            Calendar when = (Calendar) now.clone();
            when.add(Calendar.MONTH, -back);
            months.add(fillRich(when, back == 0));
        }
        savedThisYear = 2000 + random.nextInt(8000) + random.nextDouble();
    }

    /** 大额版本的一个月：条目多、金额大，进账偏多。 */
    private static Month fillRich(Calendar when, boolean current) {
        Month month = new Month(when.get(Calendar.YEAR), when.get(Calendar.MONTH) + 1);
        Calendar now = Calendar.getInstance();
        int lastDay = current ? now.get(Calendar.DAY_OF_MONTH) : when.getActualMaximum(Calendar.DAY_OF_MONTH);
        int count = current ? 16 + random.nextInt(10) : 26 + random.nextInt(14);
        for (int i = 0; i < count; i++) {
            Bill bill = order(month, lastDay, current, i);
            boolean income = random.nextInt(10) < 6;
            double amount = amountFor(bill.category, true);
            bill.amount = round2(income ? amount : -amount);
            bill.kind = income ? "退款" : random.nextInt(10) < 7 ? "支出" : "转账";
            bill.status = "";
            bill.payee = "大额交易 " + (1000 + random.nextInt(9000));
            month.bills.add(bill);
        }
        month.bills.sort((a, b) -> Long.compare(b.time, a.time));
        return month;
    }

    /** 删掉一条账单（按对象身份找）。 */
    public static boolean remove(Bill bill) {
        if (bill == null) return false;
        for (Month month : months) {
            for (int i = 0; i < month.bills.size(); i++) {
                if (month.bills.get(i) == bill) {
                    month.bills.remove(i);
                    return true;
                }
            }
        }
        return false;
    }

    /** Fills one month, newest order first. */
    private static Month fill(Calendar when, boolean current) {
        Month month = new Month(when.get(Calendar.YEAR), when.get(Calendar.MONTH) + 1);
        Calendar now = Calendar.getInstance();
        int lastDay = current ? now.get(Calendar.DAY_OF_MONTH) : when.getActualMaximum(Calendar.DAY_OF_MONTH);
        int count = current ? 8 : 18 + random.nextInt(7);
        for (int i = 0; i < count; i++) {
            month.bills.add(order(month, lastDay, current, i));
        }
        month.bills.sort((a, b) -> Long.compare(b.time, a.time));
        return month;
    }

    private static Bill order(Month month, int lastDay, boolean current, int index) {
        Bill bill = new Bill();
        // 完全随机：商家、图标、分类、收款公司都从整张表里抽
        String[] shop = SHOPS[random.nextInt(SHOPS.length)];
        bill.merchant = shop[0];
        bill.category = shop[1];
        bill.mark = shop[2];
        bill.markColor = (int) Long.parseLong(shop[3], 16);
        bill.markInk = (int) Long.parseLong(shop[4], 16);

        // how far back in the month the order sits: the current month's newest ones are yesterday
        // and the days before it, an older month's run over its own last days
        int day = current
                ? Math.max(1, lastDay - index * (1 + random.nextInt(3)))
                : Math.max(1, lastDay - index * (1 + random.nextInt(4)));
        Calendar at = Calendar.getInstance();
        at.set(Calendar.YEAR, month.year);
        at.set(Calendar.MONTH, month.month - 1);
        at.set(Calendar.DAY_OF_MONTH, Math.min(day, lastDay));
        at.set(Calendar.HOUR_OF_DAY, 7 + random.nextInt(16));
        at.set(Calendar.MINUTE, random.nextInt(60));
        at.set(Calendar.SECOND, random.nextInt(60));
        if (current && (index == 0 || at.getTimeInMillis() > System.currentTimeMillis())) {
            at.setTimeInMillis(System.currentTimeMillis() - random.nextInt(1000));
        }
        bill.time = at.getTimeInMillis();

        boolean income = random.nextInt(10) == 0;
        double amount = amountFor(bill.category, false);
        bill.amount = income ? amount : -amount;
        if (income) {
            bill.kind = "退款";
            bill.status = "";
        } else {
            int roll = random.nextInt(10);
            bill.kind = roll < 7 ? "支出" : roll == 7 ? "转账" : roll == 8 ? "订单" : "退款";
            bill.status = "";
        }
        bill.payMethod = PAY_METHODS[random.nextInt(PAY_METHODS.length)];
        bill.goods = GOODS[random.nextInt(GOODS.length)];
        bill.reward = "已领取" + (1 + random.nextInt(20)) + "积分";
        bill.payee = shop[5];
        bill.orderNo = orderNo(at);
        bill.merchantOrderNo = merchantOrderNo(at);
        if (bill.mark.equals("闲鱼")) {
            bill.status = "自动扣款成功";
            bill.kind = "订单";
            bill.payee = "x***" + random.nextInt(10);
            bill.amount = -(0.5 + random.nextInt(9900)/100.0);
            StringBuilder merchantId = new StringBuilder("T200P");
            while (merchantId.length() < 24) merchantId.append(random.nextInt(10));
            bill.merchantOrderNo = merchantId.toString();
            bill.goods = GOODS[random.nextInt(GOODS.length)];
            bill.merchant = bill.goods;
        } else if (bill.mark.equals("云猫")) {
            bill.goods = bill.merchantOrderNo;
            bill.merchant = bill.goods;
            bill.category = "其他";
        } else if (bill.mark.equals("花呗")) {
            bill.goods = "花呗自动还款-" + month.year + "年" + month.month + "月账单";
            bill.merchant = bill.goods;
        }
        bill.amount = (bill.income() ? 1 : -1) * round2(Math.abs(bill.amount) + random.nextInt(87) / 100.0);
        bill.billCategory = bill.category;
        return bill;
    }

    /**
     * 每个分类的金额区间 {最低, 最高, 秒变富豪时的倍数}。
     * 奶茶、小吃这类单价低的品类就算开了“秒变富豪”也不会出现几万块。
     */
    private static double[] categoryRange(String category) {
        if (category == null) return new double[]{10, 300, 20};
        switch (category) {
            case "餐饮美食": return new double[]{6, 120, 10};
            case "交通出行": return new double[]{3, 300, 10};
            case "文化娱乐": return new double[]{6, 200, 10};
            case "医疗健康": return new double[]{15, 500, 8};
            case "生活服务": return new double[]{8, 200, 10};
            case "充值缴费": return new double[]{20, 300, 10};
            case "生活缴费": return new double[]{30, 600, 10};
            case "日用百货": return new double[]{15, 400, 20};
            case "商业服务": return new double[]{20, 2000, 30};
            case "信用借还": return new double[]{50, 5000, 30};
            case "公共服务": return new double[]{20, 500, 10};
            default: return new double[]{10, 300, 20};
        }
    }

    /** 按分类抽一个金额（{@code rich} 为“秒变富豪”那档）。 */
    private static double amountFor(String category, boolean rich) {
        double[] range = categoryRange(category);
        double min = range[0], max = range[1];
        if (rich) { min *= range[2]; max *= range[2]; }
        return round2(min + random.nextDouble() * (max - min));
    }

    private static final String[] PAY_METHODS = {
            "招商银行储蓄卡(4646)", "余额宝", "花呗", "账户余额", "建设银行储蓄卡(8812)",
            "工商银行储蓄卡(3390)", "中国银行信用卡(6271)",
    };

    /** The 订单号: the day it was paid, then twenty digits — twenty-eight of them, as before. */
    private static String orderNo(Calendar at) {
        StringBuilder out = new StringBuilder(stamp(at, "yyyyMMdd"));
        out.append("22");
        while (out.length() < 28) out.append(random.nextInt(10));
        return out.toString();
    }

    /** The 商家订单号: three letters, the month and day, then digits — fifteen of them, as before. */
    private static String merchantOrderNo(Calendar at) {
        StringBuilder out = new StringBuilder();
        String letters = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        for (int i = 0; i < 3; i++) out.append(letters.charAt(random.nextInt(letters.length())));
        out.append(stamp(at, "MMdd"));
        String pool = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        while (out.length() < 15) out.append(pool.charAt(random.nextInt(pool.length())));
        return out.toString();
    }

    private static String stamp(Calendar at, String pattern) {
        return new java.text.SimpleDateFormat(pattern, Locale.US).format(at.getTime());
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /** The months to draw: everything, or only the orders one of the chips asks for. */
    public static List<Month> months(String kind) {
        if (kind == null || kind.isEmpty() || kind.equals("全部")) return months;
        if (kind.equals("支出")) {
            // 支出 is everything that left the account, whatever the row is filed as
            List<Month> out = new ArrayList<>();
            for (Month month : months) {
                Month copy = new Month(month.year, month.month);
                for (Bill bill : month.bills) if (!bill.income()) copy.bills.add(bill);
                if (!copy.bills.isEmpty()) out.add(copy);
            }
            return out;
        }
        List<Month> out = new ArrayList<>();
        for (Month month : months) {
            Month copy = new Month(month.year, month.month);
            for (Bill bill : month.bills) if (bill.kind.equals(kind)) copy.bills.add(bill);
            if (!copy.bills.isEmpty()) out.add(copy);
        }
        return out;
    }

    /** Re-index edited dates, preserving the same bill instances and edits on return. */
    public static void reconcile() {
        java.util.Map<Integer, Month> grouped = new java.util.TreeMap<>(java.util.Collections.reverseOrder());
        for (Month old : months) for (Bill bill : old.bills) {
            Calendar at = Calendar.getInstance(); at.setTimeInMillis(bill.time);
            int year = at.get(Calendar.YEAR), month = at.get(Calendar.MONTH)+1;
            int key = year*12+month;
            Month group = grouped.get(key);
            if (group == null) { group = new Month(year,month); grouped.put(key,group); }
            group.bills.add(bill);
            if (bill == selected) selectedMonth=group;
        }
        months.clear(); months.addAll(grouped.values());
        for (Month month : months) month.bills.sort((a,b)->Long.compare(b.time,a.time));
    }

    public static double savedThisYear() {
        return savedThisYear;
    }

    /** The 本月已省 line: the page's own figure again, worked out from the month it shows. */
    public static double savedThisMonth() {
        return 0;
    }

    /** The row a 账单详情 page was opened for, and the month card it came out of. */
    public static void select(Month month, Bill bill) {
        selectedMonth = month;
        selected = bill;
    }

    public static Bill selected() {
        return selected;
    }

    public static Month selectedMonth() {
        return selectedMonth;
    }
}
