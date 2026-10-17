package com.luoyun.doutu.features.zhi.data;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The figures of the 理财 tab's 成长版.
 *
 * <p>Two numbers are the inputs: 总资产 and the annual rate. Everything the page shows is worked out
 * from them and rides on the reference's own ratios, so at 258,276.87 and 3.91% every figure is the
 * one the reference draws: 余额宝, 基金 and 储蓄|养老 are the three shares the total is split into, the
 * two holdings' day's profits follow the rate, and 昨日收益 is their sum — 0.34 + 276.18 = 276.52.
 * The row under the grid carries 纳斯达克's own points, move and change, and the section under that
 * row — the second reference's — carries the three indexes, the three 资金流入 amounts, 今日风口's
 * name and change and the three products' returns; none of those follow the rate, so each of them
 * keeps whatever it is set to.
 *
 * <p>Every figure can be typed over. One the user has set is kept until they change 总资产 or the
 * rate again, at which point it follows the inputs once more. The card's 持卡人 signature is the
 * user's own name and never the reference's: the blurred one the reference carries is painted out
 * of the artwork entirely.
 *
 * <p>Nothing here is an account: the page is a local mock.
 */
public final class GrowthStore {
    /** The rate every anchored figure is scaled from, and the shares the total is split into. */
    private static final double RATE_REF = 3.91;
    private static final double SHARE_RESERVE = 0.4264811246938218;
    private static final double SHARE_FUND = 0.5309281857101644;
    private static final double RESERVE_DAY = 3.0866940698524315e-06;
    private static final double FUND_DAY = 0.002014053158372705;
    /** The signature the card carries until the user types one of their own. */
    private static final String DEFAULT_HOLDER = "ZhangXinYu";

    private static final String[] KEYS = {
            "total", "rate", "yesterday", "family", "holder",
            "reserve", "reserveProfit", "fund", "fundProfit", "savings",
            "huabei", "idx2", "move2", "idxp2",
            "fundSelfPct", "fundSelfTag", "rankPct0", "rankPct1", "windMove", "allocPct",
            "bidx0", "bidx1", "bidx2", "bpct0", "bpct1", "bpct2",
            "flow0", "flow1", "flow2", "windName", "windPct", "prod0", "prod1", "prod2"};
    private static final String[] TITLES = {
            "编辑总资产", "编辑年化利率(%)", "编辑昨日收益", "编辑家庭保障份数", "编辑持卡人签名",
            "编辑余额宝金额", "编辑余额宝昨日收益", "编辑基金金额", "编辑基金昨日收益",
            "编辑储蓄|养老", "编辑花呗可用额度",
            "编辑纳斯达克点数", "编辑纳斯达克涨跌", "编辑纳斯达克涨跌幅",
            "编辑基金自选涨幅", "编辑基金自选标签", "编辑基金排行第1名涨幅", "编辑基金排行第2名涨幅",
            "编辑看行情涨跌", "编辑配资产涨幅",
            "编辑上证指数", "编辑深证成指", "编辑创业板指",
            "编辑上证涨跌幅", "编辑深证涨跌幅", "编辑创业板涨跌幅",
            "编辑资金流入第1名", "编辑资金流入第2名", "编辑资金流入第3名",
            "编辑风口名称", "编辑风口涨幅",
            "编辑第1个产品涨幅", "编辑第2个产品涨幅", "编辑第3个产品涨幅"};

    private static double total = 258276.87;
    private static double rate = RATE_REF;
    private static String totalText = "258,276.87";
    private static String family = "1";
    private static String holder = DEFAULT_HOLDER;
    /** 花呗's own line: the credit that is available, which nothing else on the page follows. */
    private static String huabei = "可用1,577.93";
    /** 纳斯达克's own row: the points, the move of the day and the day's change. */
    private static String idx2 = "21020.02";
    private static String move2 = "+127.33";
    private static String idxp2 = "+0.61%";
    /**
     * The figures the sheet's two lower bands are cut with painted out of them: 基金自选's return
     * and the tag beside it, 基金排行's two returns, 看行情's index move and 配资产's return. They
     * follow nothing else, so each keeps whatever it is set to.
     */
    private static String fundSelfPct = "+2.63%";
    private static String fundSelfTag = "连涨4个月";
    private static String rankPct0 = "+65.04%";
    private static String rankPct1 = "+64.39%";
    private static String windMove = "+0.6%";
    private static String allocPct = "+2.78%";
    /** The lower section's own figures: the index row, 资金流入, 今日风口 and the three products. */
    private static final String[] board = {"3797.24", "12384.87", "2963.82"};
    private static final String[] boardPct = {"\u22120.43%", "\u22122.03%", "\u22122.61%"};
    private static final String[] flow = {"+14.4亿元", "+12.4亿元", "+10.7亿元"};
    private static String windName = "创新药";
    private static String windPct = "\u22121.74%";
    private static final String[] prodPct = {"+4.49%", "+6.56%", "+4.97%"};
    /** The figures the user has typed over; each follows the inputs again once it is cleared. */
    private static final Map<String, String> overrides = new LinkedHashMap<>();
    /**
     * The eye that hides the money figures. The 成长版's own reference is captured with it shut, so
     * the page opens the way the reference draws it; tapping the eye shows what the figures are.
     */
    public static boolean hidden = true;

    private GrowthStore() {
    }

    /**
     * 换账号时把这一页自己记住的那些数字抹回去：成长版上的值只活在这次运行里（没有落盘），
     * 但换到别的账号再进来时不该还留着上一个账号填的东西。
     */
    public static void reset() {
        total = 258276.87;
        rate = RATE_REF;
        totalText = "258,276.87";
        family = "1";
        holder = DEFAULT_HOLDER;
        huabei = "可用1,577.93";
        idx2 = "21020.02";
        move2 = "+127.33";
        idxp2 = "+0.61%";
        fundSelfPct = "+2.63%";
        fundSelfTag = "连涨4个月";
        rankPct0 = "+65.04%";
        rankPct1 = "+64.39%";
        windMove = "+0.6%";
        allocPct = "+2.78%";
        System.arraycopy(new String[]{"3797.24", "12384.87", "2963.82"}, 0, board, 0, 3);
        System.arraycopy(new String[]{"\u22120.43%", "\u22122.03%", "\u22122.61%"}, 0, boardPct, 0, 3);
        System.arraycopy(new String[]{"+14.4亿元", "+12.4亿元", "+10.7亿元"}, 0, flow, 0, 3);
        windName = "创新药";
        windPct = "\u22121.74%";
        System.arraycopy(new String[]{"+4.49%", "+6.56%", "+4.97%"}, 0, prodPct, 0, 3);
        overrides.clear();
        hidden = true;
    }

    /** Current text of a field, computed from the inputs when the user has not set one. */
    public static String value(String key) {
        String typed = overrides.get(key);
        if (typed != null) return typed;
        switch (key) {
            case "total": return totalText;
            case "rate": return trim(rate);
            case "yesterday": return signed(reserveDay() + fundDay(), 2);
            case "family": return family;
            case "holder": return holder;
            case "reserve": return money(total * SHARE_RESERVE);
            case "fund": return money(total * SHARE_FUND);
            case "savings": return money(total - total * SHARE_RESERVE - total * SHARE_FUND);
            case "huabei": return huabei;
            case "reserveProfit": return signed(reserveDay(), 2);
            case "fundProfit": return signed(fundDay(), 2);
            case "idx2": return idx2;
            case "move2": return move2;
            case "idxp2": return idxp2;
            case "fundSelfPct": return fundSelfPct;
            case "fundSelfTag": return fundSelfTag;
            case "rankPct0": return rankPct0;
            case "rankPct1": return rankPct1;
            case "windMove": return windMove;
            case "allocPct": return allocPct;
            case "bidx0": return board[0];
            case "bidx1": return board[1];
            case "bidx2": return board[2];
            case "bpct0": return boardPct[0];
            case "bpct1": return boardPct[1];
            case "bpct2": return boardPct[2];
            case "flow0": return flow[0];
            case "flow1": return flow[1];
            case "flow2": return flow[2];
            case "windName": return windName;
            case "windPct": return windPct;
            case "prod0": return prodPct[0];
            case "prod1": return prodPct[1];
            case "prod2": return prodPct[2];
            default: return "";
        }
    }

    public static void value(String key, String text) {
        String typed = text == null ? "" : text.trim();
        switch (key) {
            case "total": {
                if (typed.isEmpty()) return;
                totalText = typed;
                double parsed = number(typed);
                if (parsed > 0) {
                    total = parsed;
                    overrides.clear();
                }
                return;
            }
            case "rate": {
                if (typed.isEmpty()) return;
                double percent = number(typed);
                if (percent > 0) {
                    rate = percent;
                    overrides.clear();
                }
                return;
            }
            case "family":
                family = typed.isEmpty() ? "1" : typed;
                return;
            case "holder":
                holder = typed.isEmpty() ? DEFAULT_HOLDER : typed;
                return;
            case "huabei":
                if (!typed.isEmpty()) huabei = typed;
                return;
            case "idx2":
                if (!typed.isEmpty()) idx2 = typed;
                return;
            case "move2":
                if (!typed.isEmpty()) move2 = typed;
                return;
            case "idxp2":
                if (!typed.isEmpty()) idxp2 = typed;
                return;
            case "fundSelfPct":
                if (!typed.isEmpty()) fundSelfPct = typed;
                return;
            case "fundSelfTag":
                if (!typed.isEmpty()) fundSelfTag = typed;
                return;
            case "rankPct0":
                if (!typed.isEmpty()) rankPct0 = typed;
                return;
            case "rankPct1":
                if (!typed.isEmpty()) rankPct1 = typed;
                return;
            case "windMove":
                if (!typed.isEmpty()) windMove = typed;
                return;
            case "allocPct":
                if (!typed.isEmpty()) allocPct = typed;
                return;
            case "bidx0": case "bidx1": case "bidx2":
                if (!typed.isEmpty()) board[key.charAt(4) - '0'] = typed;
                return;
            case "bpct0": case "bpct1": case "bpct2":
                if (!typed.isEmpty()) boardPct[key.charAt(4) - '0'] = typed;
                return;
            case "flow0": case "flow1": case "flow2":
                if (!typed.isEmpty()) flow[key.charAt(4) - '0'] = typed;
                return;
            case "windName":
                if (!typed.isEmpty()) windName = typed;
                return;
            case "windPct":
                if (!typed.isEmpty()) windPct = typed;
                return;
            case "prod0": case "prod1": case "prod2":
                if (!typed.isEmpty()) prodPct[key.charAt(4) - '0'] = typed;
                return;
            default:
                if (typed.isEmpty()) {
                    overrides.remove(key);
                } else {
                    overrides.put(key, typed);
                }
        }
    }

    public static String[] keys() {
        return KEYS;
    }

    public static String titleOf(String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) return TITLES[i];
        }
        return key;
    }

    /** What 余额宝's holding earns in a day, at the reference's own ratio, carried to the rate. */
    private static double reserveDay() {
        return total * SHARE_RESERVE * RESERVE_DAY * rate / RATE_REF;
    }

    /** The same for 基金. */
    private static double fundDay() {
        return total * SHARE_FUND * FUND_DAY * rate / RATE_REF;
    }

    /** The reference's own figure, carried to the current rate. */
    private static double scaled(double reference) {
        return reference * rate / RATE_REF;
    }

    private static String money(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    private static String signed(double value, int decimals) {
        String text = String.format(Locale.US, "%,." + decimals + "f", value);
        return value < 0 ? text.replace("-", "\u2212") : "+" + text;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value)
                : String.format(Locale.US, "%.2f", value);
    }

    /** Digits only, so a typed "258,276.87" or "3.91%" both parse. */
    private static double number(String text) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c) || c == '.' || (c == '-' && digits.length() == 0)) {
                digits.append(c);
            }
        }
        try {
            return Double.parseDouble(digits.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
