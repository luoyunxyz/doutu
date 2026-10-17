package com.luoyun.doutu.features.sound;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 金额 → 播报音频序列。
 *
 * <p>这是「模拟收款声音」的核心：原包把金额拆成一串音素，每个音素对应一个 mp3，依次播出来
 * （`00_资料包/README.md` §4.2）。音素表照抄原包 —— 数字 0-9、点、十、百、千、万、亿各一个文件，
 * 起手是到账提示音 `diaoluo_da.mp3`，金额后面接 `tts_yuan.mp3`（"元"），收尾按风格选
 * `tts_success.mp3`（小支/支付宝风格）或 `tts_wxsuccess.mp3`（小微/微信风格）。
 *
 * <p>中文数字读法（"十五"而不是"一十五"、"一千零一"里的那个零）在这里实现，
 * 因为原包只给了枚举表和文件名映射、没有可运行的实现（资料包 §8 明确不提供）。
 *
 * <p>整个类不碰 Android API，就是为了能在桌面上单跑验证：
 * {@code java SoundScript.java} 会把一批金额的播报串打出来。
 */
public final class SoundScript {
    /** 资源目录（原包路径，assets/ 之下）。 */
    public static final String DIR = "audio/account_sound/";

    public static final String LEAD = "diaoluo_da.mp3";      // 起手：到账提示音（"叮"）
    public static final String YUAN = "tts_yuan.mp3";        // "元"
    /** 固定那句「支付宝到账」——排在合成金额之前。 */
    public static final String TAIL_ZHI = "tts_success.mp3";
    /** 固定那句「微信支付收款」——同样排在合成金额之前。 */
    public static final String TAIL_WEI = "tts_wxsuccess.mp3";

    private static final String ZERO = "tts_0.mp3";
    private static final String DOT = "tts_dot.mp3";
    private static final String SHI = "tts_ten.mp3";
    private static final String BAI = "tts_hundred.mp3";
    private static final String QIAN = "tts_thousand.mp3";
    private static final String WAN = "tts_ten_thousand.mp3";
    private static final String YI = "tts_ten_million.mp3";

    /** 金额上限：超过就不播（原包同样会判「无效数字」）。 */
    private static final long MAX = 999999999999L;

    private SoundScript() {
    }

    /** 解析结果：能播就带着片段表，不能播就带着给用户看的那句话。 */
    public static final class Result {
        public final boolean ok;
        public final List<String> clips;
        public final String error;

        private Result(boolean ok, List<String> clips, String error) {
            this.ok = ok;
            this.clips = clips;
            this.error = error;
        }
    }

    /**
     * 把用户输入的金额变成一条播报串。
     *
     * @param amount 用户输入（允许带 ￥/¥、千分位逗号、前后空格）
     * @param wechat true = 小微风格收尾，false = 小支风格
     */
    public static Result build(String amount, boolean wechat) {
        String text = normalize(amount);
        if (text.isEmpty()) return new Result(false, null, "没有输入内容");
        if (!text.matches("\\d{1,12}(\\.\\d{1,2})?")) return new Result(false, null, "输入格式不对");
        String[] parts = text.split("\\.");
        long integer;
        try {
            integer = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            return new Result(false, null, "无效数字: " + parts[0]);
        }
        if (integer > MAX) return new Result(false, null, "无效数字: " + parts[0]);

        List<String> clips = new ArrayList<>();
        // 顺序：先固定录音，再拼出来的金额。
        //   ① diaoluo_da.mp3   到账提示音（"叮"）
        //   ② tts_success / tts_wxsuccess   固定那句「支付宝到账」/「微信支付收款」
        //   ③ 金额（数字 + 十百千万亿 + 点，都是逐字拼的）
        //   ④ tts_yuan.mp3     "元"
        // 2026-10-16：这两句固定录音原来被我放在最后当「尾音」了，听着是「88 元，支付宝到账」——
        // 方向反了。真机上的到账播报是「叮 → 支付宝到账 → 88 元」，所以固定录音要在合成音之前。
        clips.add(LEAD);
        clips.add(wechat ? TAIL_WEI : TAIL_ZHI);
        clips.addAll(readInteger(integer));
        if (parts.length > 1) {
            clips.add(DOT);
            for (int i = 0; i < parts[1].length(); i++) {
                clips.add(digit(parts[1].charAt(i) - '0'));
            }
        }
        clips.add(YUAN);
        return new Result(true, clips, null);
    }

    /** 完整资源路径（assets 之下）。 */
    public static String path(String clip) {
        return DIR + clip;
    }

    /** 去掉货币符号、千分位和空格。 */
    private static String normalize(String amount) {
        if (amount == null) return "";
        String text = amount.trim().replace("￥", "").replace("¥", "").replace(",", "").replace(" ", "");
        return text;
    }

    /** 整数部分 → 音素串。 */
    static List<String> readInteger(long n) {
        List<String> out = new ArrayList<>();
        if (n == 0) {
            out.add(ZERO);
            return out;
        }
        long yi = n / 100000000L;
        long wan = (n / 10000L) % 10000L;
        long rest = n % 10000L;

        if (yi > 0) {
            out.addAll(readGroup(yi, true));                   // 亿总是最高那一段
            out.add(YI);
        }
        if (wan > 0) {
            if (!out.isEmpty() && wan < 1000) out.add(ZERO);   // 一亿零一万
            out.addAll(readGroup(wan, out.isEmpty()));
            out.add(WAN);
        } else if (!out.isEmpty() && rest > 0) {
            out.add(ZERO);                                     // 一亿零一
        }
        if (rest > 0) {
            if (!out.isEmpty() && rest < 1000 && !ZERO.equals(out.get(out.size() - 1))) out.add(ZERO);
            out.addAll(readGroup(rest, out.isEmpty()));
        }
        return out;
    }

    /**
     * 四位一组的中文读法。
     *
     * @param n         1..9999
     * @param isLeading 这一组是不是整个数最前面那段 —— 只有这种情况下 15 读「十五」而不是「一十五」，
     *                  十万也不会读成「一十万」
     */
    private static List<String> readGroup(long n, boolean isLeading) {
        List<String> out = new ArrayList<>();
        int[] units = {1000, 100, 10, 1};
        String[] names = {QIAN, BAI, SHI, null};
        boolean started = false;
        boolean pendingZero = false;
        for (int i = 0; i < 4; i++) {
            int d = (int) ((n / units[i]) % 10);
            if (d == 0) {
                if (started) pendingZero = true;   // 中间的零先记着，后面真有数字才补
                continue;
            }
            if (pendingZero) {
                out.add(ZERO);
                pendingZero = false;
            }
            // 「十五」：十位上的一，在整串的最前面时省掉
            if (!(i == 2 && d == 1 && !started && isLeading)) out.add(digit(d));
            if (names[i] != null) out.add(names[i]);
            started = true;
        }
        return out;
    }

    private static String digit(int value) {
        return "tts_" + value + ".mp3";
    }

    /* ---------------------------- 桌面自测 ---------------------------- */

    /** 直接跑：{@code java SoundScript.java}，把一批金额的播报串打出来。 */
    public static void main(String[] args) {
        String[][] cases = {
                {"1", "一"}, {"9", "九"}, {"10", "十"}, {"15", "十五"}, {"20", "二十"},
                {"88", "八十八"}, {"100", "一百"}, {"101", "一百零一"}, {"110", "一百一十"},
                {"115", "一百一十五"}, {"1000", "一千"}, {"1001", "一千零一"}, {"1010", "一千零一十"},
                {"1100", "一千一百"}, {"1888", "一千八百八十八"}, {"10000", "一万"},
                {"10001", "一万零一"}, {"10500", "一万零五百"}, {"12345", "一万二千三百四十五"},
                {"100000", "十万"}, {"1000000", "一百万"}, {"10000000", "一千万"},
                {"100000000", "一亿"}, {"100000001", "一亿零一"}, {"100010000", "一亿零一万"},
                {"123456789", "一亿二千三百四十五万六千七百八十九"},
                {"0.01", "零点零一"}, {"88.88", "八十八点八八"}, {"188.5", "一百八十八点五"},
                {"6.66", "六点六六"}, {"￥1,000", "一千"}, {"", "(空)"}, {"abc", "(非法)"},
        };
        for (String[] item : cases) {
            Result result = build(item[0], false);
            String expect = item[1];
            String actual = result.ok ? join(result.clips) : result.error;
            System.out.printf(Locale.US, "%-14s -> %-58s %s%n", item[0].isEmpty() ? "(空)" : item[0],
                    actual, expect.startsWith("(") ? "" : expect);
        }
        System.out.println();
        System.out.println("小微风格（固定录音换成微信那句）：");
        Result wechat = build("1888", true);
        System.out.println("  1888 -> " + join(wechat.clips));
    }

    private static String join(List<String> clips) {
        StringBuilder out = new StringBuilder();
        for (String clip : clips) {
            if (out.length() > 0) out.append(' ');
            out.append(clip.replace("tts_", "").replace(".mp3", ""));
        }
        return out.toString();
    }
}
