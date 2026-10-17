
package com.luoyun.doutu.features.zhi.ui;

import android.content.res.Resources;
import android.graphics.*;
import com.luoyun.doutu.R;
import com.luoyun.doutu.features.zhi.data.Bill;
import com.luoyun.doutu.features.zhi.data.BillStore;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 收支统计 — 周度 / 月度 / 年度 三页签，全部由 Canvas 原生绘制。
 * 所有金额均可点击修改，环形图与占比由数据自动计算，日期随系统当前时间自动同步。
 */
public final class BillAnalyticsScreen {

    public static final float W = 1280f, H = 2816f;
    /** 固定顶栏高度（状态栏 + 周度/月度/年度 + 更多）。 */
    public static final float NAV_H = 300f;

    // ── 调色板 ──────────────────────────────────────────────────────────
    static final int PAGE_BG  = 0xffeff2f7;
    static final int CARD     = 0xffffffff;
    static final int INK      = 0xff2b2f36;
    static final int GREY     = 0xff9aa2ad;
    static final int SUB      = 0xffaab1bc;
    static final int BLUE     = 0xff1777ff;
    static final int LBLUE    = 0xffaacbf6;
    static final int PILL     = 0xffeef3fa;
    static final int SLATE    = 0xff7d97b6;
    static final int DIV      = 0xfff1f3f6;
    static final int RED      = 0xffe2493c;
    static final int TILE     = 0xffeef4fb;
    static final int GRAYTILE = 0xfff2f3f7;
    static final int HILITE   = 0xff9cbdea;
    /** 手动填进日历格子的数字用绿色，和参考图里的收入数字一致。 */
    static final int GREEN    = 0xff2fa37a;
    static final int[] CAT = {
        0xff1777ff, 0xff5aa0ff, 0xff8dbcff, 0xffb3d3ff, 0xffcfe3ff, 0xffe0eeff, 0xffedf5ff
    };

    // ── 状态 ────────────────────────────────────────────────────────────
    public static final class State {
        public float scroll;
        public int tab;                 // 0 周度, 1 月度, 2 年度
        public boolean income;          // false=支出
        public int weekOffset;          // 0 = 本周
        public int monthOffset;         // 0 = 本月
        public int yearOffset;          // 0 = 今年
        public boolean showAll;         // 展开全部
        public int expanded = 0;        // 展开的分类下标
        public boolean compare;         // 对比上周 / 对比上月
        public boolean noticeClosed;    // 年度提示条
        public boolean investIncluded;  // 投资理财计入
        public final Map<String,Double> edits = new LinkedHashMap<>();
        public final Map<String,Integer> cnt = new LinkedHashMap<>();
        /** 文字项（城市名、时间、交易名称…）改过的值，改过就存在这里。 */
        public final Map<String,String> texts = new LinkedHashMap<>();
        /** 支出方式里从相册选来的图标。 */
        public final Map<Integer,Bitmap> payIcons = new LinkedHashMap<>();
    }

    static final class Slice {
        String name; double amount; int count; int color;
        float a0, sweep, pct;
    }

    private static final class Hot { String id; float l,t,r,b;
        Hot(String i,float l,float t,float r,float b){this.id=i;this.l=l;this.t=t;this.r=r;this.b=b;} }
    private final List<Hot> hots = new ArrayList<>();
    private void hot(String id, float l, float t, float r, float b){ hots.add(new Hot(id,l,t,r,b)); }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Path ph = new Path();
    private final Map<String,Bitmap> art = new HashMap<>();
    private final SimpleDateFormat fullYmd = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    /** 每次绘制时从账单数据算出来的默认值；用户手动改过的值优先于它。 */
    private final Map<String,Double> base = new LinkedHashMap<>();
    private final Map<String,Integer> baseCnt = new LinkedHashMap<>();

    public void load(Resources res) {
        if (!art.isEmpty()) return;
        art.put("mascot",  dec(res, R.drawable.stat_mascot));
        art.put("hero_week",  dec(res, R.drawable.stat_hero_week));
        art.put("hero_month", dec(res, R.drawable.stat_hero_month));
        art.put("hero_year",  dec(res, R.drawable.stat_hero_year));
        art.put("week_sum",  dec(res, R.drawable.stat_week_sum));
        art.put("week_rank", dec(res, R.drawable.stat_week_rank));
        art.put("week_cat",  dec(res, R.drawable.stat_week_cat));
        art.put("month",   dec(res, R.drawable.stat_month_art));
        art.put("year",    dec(res, R.drawable.stat_year_art));
        art.put("cmb",     dec(res, R.drawable.stat_bank_cmb));
        art.put("huabei",  dec(res, R.drawable.stat_bank_huabei));
        // 支出方式那一列用的图标：从参考图上抠下来的花呗 / 招行
        art.put("payHuabei", dec(res, R.drawable.stat_pay_huabei));
        art.put("payCmb",    dec(res, R.drawable.stat_pay_cmb));
        art.put("phone",   dec(res, R.drawable.stat_cat_phone));
        art.put("coin",    dec(res, R.drawable.stat_more_coin));
        art.put("moreIn",  dec(res, R.drawable.stat_more_income));
        art.put("f0", dec(res, R.drawable.stat_food_zhengcan));
        art.put("f1", dec(res, R.drawable.stat_food_qita));
        art.put("f2", dec(res, R.drawable.stat_food_zhaji));
        art.put("f3", dec(res, R.drawable.stat_food_shengxian));
        art.put("f4", dec(res, R.drawable.stat_food_kafei));
        art.put("f5", dec(res, R.drawable.stat_food_lingshi));
    }
    private Bitmap dec(Resources res, int id){ return BitmapFactory.decodeResource(res, id); }
    private void art(Canvas c, String k, float l, float t, float rr, float b){
        Bitmap bm = art.get(k); if (bm==null) return;
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE);
        r.set(l,t,rr,b); c.drawBitmap(bm,null,r,p);
    }
    private void art(Canvas c, Bitmap bm, float l, float t, float rr, float b){
        if (bm == null) return;
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE);
        r.set(l,t,rr,b); c.drawBitmap(bm,null,r,p);
    }


    // ══════════════════════════════════════════════════════════════════════
    //  数据（默认值取自参考图，可点击修改；占比/环形图自动计算）
    // ══════════════════════════════════════════════════════════════════════

    /** 取可编辑值：手动改过 → 用它；否则用账单算出的值；再否则用参考图默认值。 */
    private double d(State s, String key, double def){
        Double v = s.edits.get(key);
        if (v != null) return v;
        Double b = base.get(key);
        return b == null ? def : b;
    }
    private int ci(State s, String key, int def){
        Integer v = s.cnt.get(key);
        if (v != null) return v;
        Integer b = baseCnt.get(key);
        return b == null ? def : b;
    }
    /** 取可编辑的文字：改过就用改过的，否则用默认。 */
    private String t(State s, String key, String def){
        String v = s.texts.get(key);
        return v == null ? def : v;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  与账单数据同步（每次绘制都按当前日期重新读取，用户改过的值仍然优先）
    // ══════════════════════════════════════════════════════════════════════

    /** 用 账单 页里的真实数据刷新各页默认值。 */
    private void syncLedger(State s){
        base.clear();
        baseCnt.clear();
        List<BillStore.Month> all = BillStore.months("全部");
        if (all == null || all.isEmpty()) return;
        Calendar now = Calendar.getInstance();

        // 近五月柱状图（月度 支出 / 收入）
        for (int i = 0; i < 5; i++){
            Calendar mo = (Calendar) now.clone();
            mo.add(Calendar.MONTH, (i - 4) + s.monthOffset);
            BillStore.Month lm = ledger(all, mo.get(Calendar.YEAR), mo.get(Calendar.MONTH) + 1);
            if (lm == null) continue;
            base.put("m.bar." + i, lm.paid());
            base.put("mi.b." + i, lm.earned());
        }

        // 十二个月（年度 支出 / 收入）
        int year = now.get(Calendar.YEAR) + s.yearOffset;
        for (int i = 0; i < 12; i++){
            BillStore.Month lm = ledger(all, year, i + 1);
            if (lm == null) continue;
            base.put("y.m." + i, lm.paid());
            base.put("yi.m." + i, lm.earned());
        }

        // 当前月份的支出分类（月度饼图 / 大卡片合计 / 各类笔数）
        Calendar mc = (Calendar) now.clone();
        mc.add(Calendar.MONTH, s.monthOffset);
        BillStore.Month cm = ledger(all, mc.get(Calendar.YEAR), mc.get(Calendar.MONTH) + 1);
        if (cm != null){
            String[][] monthGroups = {
                    {"充值缴费", "生活缴费"}, {"日用百货"}, {}
            };
            double[] mv = bucket(cm, false, monthGroups);
            if (mv[0] + mv[1] + mv[2] > 0){
                base.put("m.c.0", mv[0]);
                base.put("m.c.1", mv[1]);
                base.put("m.c.2", mv[2]);
            }
            int[] mn = bucketCount(cm, false, monthGroups);
            baseCnt.put("m.n.0", mn[0]);
            baseCnt.put("m.n.1", mn[1]);
            baseCnt.put("m.n.2", mn[2]);
            baseCnt.put("mi.n", count(cm, true));
        }

        // 整年的支出分类（年度饼图 / 各类笔数）
        String[][] yearGroups = {
                {"餐饮美食"}, {"日用百货"}, {"信用借还", "转账红包"}, {"交通出行"},
                {"文化娱乐", "文化休闲"}, {"美容美发"}, {}
        };
        double[] yv = new double[yearGroups.length];
        int[] yn = new int[yearGroups.length];
        for (BillStore.Month m : all){
            if (m.year != year) continue;
            double[] part = bucket(m, false, yearGroups);
            int[] partN = bucketCount(m, false, yearGroups);
            for (int g = 0; g < yv.length; g++){ yv[g] += part[g]; yn[g] += partN[g]; }
        }
        double ysum = 0;
        for (double v : yv) ysum += v;
        if (ysum > 0){
            for (int g = 0; g < yv.length; g++) base.put("y.c." + g, yv[g]);
            for (int g = 0; g < yn.length; g++) baseCnt.put("y.n." + g, yn[g]);
        }

        // 餐饮美食下的六个小类，按餐饮美食的总额等比缩放，和上面的大饼图保持一致
        double[] tileDef = {2013.67, 644.05, 243.00, 20.40, 8.00, 4.00};
        double refFood = 0;
        for (double v : tileDef) refFood += v;
        double k = refFood > 0 ? yv[0] / refFood : 0;
        for (int i = 0; i < tileDef.length; i++) base.put("y.t." + i, tileDef[i] * k);
    }

    private BillStore.Month ledger(List<BillStore.Month> all, int year, int month){
        for (BillStore.Month m : all) if (m.year == year && m.month == month) return m;
        return null;
    }

    /** 把一个月里某一方向的金额按分组归类；未命中任何分组的落到最后一组（“其他”）。 */
    private double[] bucket(BillStore.Month m, boolean income, String[][] groups){
        double[] out = new double[groups.length];
        if (m == null) return out;
        for (Bill b : m.bills){
            if (!b.counted || b.income() != income) continue;
            out[groupOf(b.category, groups)] += Math.abs(b.amount);
        }
        return out;
    }

    private int[] bucketCount(BillStore.Month m, boolean income, String[][] groups){
        int[] out = new int[groups.length];
        if (m == null) return out;
        for (Bill b : m.bills){
            if (!b.counted || b.income() != income) continue;
            out[groupOf(b.category, groups)]++;
        }
        return out;
    }

    private int groupOf(String category, String[][] groups){
        String cat = category == null ? "" : category;
        for (int g = 0; g < groups.length - 1; g++){
            for (String name : groups[g]) if (name.equals(cat)) return g;
        }
        return groups.length - 1;
    }

    private int count(BillStore.Month m, boolean income){
        int n = 0;
        for (Bill b : m.bills) if (b.counted && b.income() == income) n++;
        return n;
    }

    /** 一周：分类 -> 默认金额（其他 20.00）。 */
    private LinkedHashMap<String,Double> weekCat(State s){
        LinkedHashMap<String,Double> m = new LinkedHashMap<>();
        m.put("其他", d(s,"w.c.0", 20.00));
        return m;
    }

    /** 一月：充值缴费 / 日用百货 / 其他。 */
    private LinkedHashMap<String,Double> monthCat(State s){
        LinkedHashMap<String,Double> m = new LinkedHashMap<>();
        m.put("充值缴费", d(s,"m.c.0", 50.00));
        m.put("日用百货", d(s,"m.c.1", 41.19));
        m.put("其他",     d(s,"m.c.2", 20.00));
        return m;
    }

    /** 一年：餐饮美食…等七类。 */
    private LinkedHashMap<String,Double> yearCat(State s){
        LinkedHashMap<String,Double> m = new LinkedHashMap<>();
        m.put("餐饮美食", d(s,"y.c.0", 2933.12));
        m.put("日用百货", d(s,"y.c.1", 1866.58));
        m.put("转账红包", d(s,"y.c.2", 1401.90));
        m.put("交通出行", d(s,"y.c.3", 1063.50));
        m.put("文化休闲", d(s,"y.c.4", 1033.40));
        m.put("美容美发", d(s,"y.c.5", 403.50));
        m.put("其他",     d(s,"y.c.6", 1139.67));
        return m;
    }

    private LinkedHashMap<String,Double> catsFor(State s){
        if (s.tab==0) return weekCat(s);
        if (s.tab==1) return monthCat(s);
        return yearCat(s);
    }

    /** 构造环形图分片：金额求和、占比、角度全部自动计算。 */
    List<Slice> slices(State s){
        LinkedHashMap<String,Double> m = catsFor(s);
        double total = 0; for (double v : m.values()) total += v;
        List<Slice> out = new ArrayList<>();
        float ang = -90f, gap = 0.8f;
        int i = 0;
        for (Map.Entry<String,Double> e : m.entrySet()){
            Slice sl = new Slice();
            sl.name = e.getKey(); sl.amount = e.getValue();
            sl.count = ci(s, cntKey(s,i), defCount(s,i));
            sl.color = CAT[i % CAT.length];
            sl.pct = total>0 ? (float)(e.getValue()/total*100.0) : 0f;
            out.add(sl); i++;
        }
        ang = -90f;
        for (Slice sl : out){
            sl.a0 = ang;
            sl.sweep = Math.max(2.5f, sl.pct*3.6f - gap);
            ang += sl.sweep + gap;
        }
        return out;
    }
    private String cntKey(State s,int i){ return (s.tab==0?"w":s.tab==1?"m":"y")+".n."+i; }
    private int defCount(State s,int i){
        if (s.tab==0) return 1;
        if (s.tab==1) return i==0?1:i==1?5:1;
        int[] c = {133,100,9,0,0,0,31}; return c[Math.min(i,c.length-1)];
    }
    /** 合计（自动计算）。 */
    double totalOf(State s){ double t=0; for (double v : catsFor(s).values()) t += v; return t; }
    /** 笔数合计。 */
    int countOf(State s){
        int n=0,i=0; for (Double v : catsFor(s).values()){ n += ci(s,cntKey(s,i),defCount(s,i)); i++; }
        return n;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  绘制入口
    // ══════════════════════════════════════════════════════════════════════

    public void draw(Canvas c, State s){
        hots.clear();
        syncLedger(s);
        c.drawColor(PAGE_BG);
        s.scroll = Math.max(0, Math.min(maxScroll(s), s.scroll));
        c.save();
        c.clipRect(0, NAV_H, W, H);
        c.translate(0, -s.scroll);
        if (s.tab==0) drawWeek(c, s);
        else if (s.tab==1) drawMonth(c, s);
        else drawYear(c, s);
        c.restore();
        drawNav(c, s);
    }

    public float maxScroll(State s){
        float h = contentHeight(s);
        return Math.max(0, h - (H - NAV_H));
    }
    private float contentHeight(State s){
        if (s.tab==0) return 4030f;
        if (s.tab==1) return s.income ? 4230f : 6710f + monthExtra(s);
        return s.income ? 4210f : 9380f;
    }

    /** 月度小结里那本日历的排数（收起时是“上一周 + 本周”两周）。 */
    private int monthCalRows(State s){
        if (!s.showAll) return 2;
        Calendar now = Calendar.getInstance();
        now.add(Calendar.MONTH, s.monthOffset);
        now.set(Calendar.DAY_OF_MONTH, 1);
        int days = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        int firstDow = now.get(Calendar.DAY_OF_WEEK) - 1;
        return (int) Math.ceil((firstDow + days) / 7.0);
    }
    /** 日历展开后 月度小结 卡片要多长，后面的卡片整体下移同样距离。 */
    private float monthExtra(State s){
        return Math.max(0, monthCalRows(s) - 2) * 166f;
    }

    // ── 顶部固定栏 ──────────────────────────────────────────────────────
    private void drawNav(Canvas c, State s){
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(PAGE_BG);
        c.drawRect(0, 0, W, NAV_H, p);
        // 返回
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(6);
        p.setStrokeCap(Paint.Cap.ROUND); p.setColor(INK);
        c.drawLine(100, 146, 48, 178, p);
        c.drawLine(100, 210, 48, 178, p);
        p.setStyle(Paint.Style.FILL);
        hot("back", 30, 140, 130, 230);
        // 三个页签
        String[] tabs = {"周度","月度","年度"};
        float[] cx = {435f, 690f, 945f};
        for (int i=0;i<3;i++){
            boolean on = s.tab==i;
            txt(c, tabs[i], cx[i], 250, 52, INK, on, Paint.Align.CENTER, 1f);
            if (on){
                p.setColor(INK);
                c.drawRoundRect(cx[i]-48, 274, cx[i]+48, 283, 4, 4, p);
            }
            hot("tab:"+i, cx[i]-80, 160, cx[i]+80, 296);
        }
        // 更多
        p.setColor(INK);
        for (int i=0;i<3;i++) c.drawCircle(1160+i*28, 208, 8, p);
        hot("more", 1120, 165, 1250, 250);
    }

    // ── 期间行（◀ 本周…▶ / 年月 ▾ / 支出收入） ─────────────────────────
    private void periodRow(Canvas c, State s, float top){
        float mid = top + 62;
        if (s.tab==0){
            Calendar now = Calendar.getInstance();
            Calendar mon = mondayOf(now, s.weekOffset);
            Calendar sun = (Calendar) mon.clone(); sun.add(Calendar.DAY_OF_MONTH, 6);
            String label = "本周" + (mon.get(Calendar.MONTH)+1) + "月" + mon.get(Calendar.DAY_OF_MONTH)
                    + "日-" + (sun.get(Calendar.MONTH)+1) + "月" + sun.get(Calendar.DAY_OF_MONTH) + "日";
            p.setShader(null); p.setStyle(Paint.Style.FILL);
            p.setColor(0xffe4e9f0);
            c.drawCircle(116, mid, 40, p);
            tri(c, 116, mid, 22, 0xff3a3f46, false);
            hot("week:prev", 72, mid-44, 160, mid+44);
            p.setColor(0xffe4e9f0);
            c.drawCircle(669, mid, 40, p);
            tri(c, 669, mid, 22, 0xff9aa0a8, true);
            hot("week:next", 625, mid-44, 713, mid+44);
            txt(c, label, 180, mid+16, 38, INK, true, Paint.Align.LEFT, 1f);
        } else if (s.tab==1){
            Calendar now = Calendar.getInstance(); now.add(Calendar.MONTH, s.monthOffset);
            txt(c, (now.get(Calendar.YEAR))+"年"+(now.get(Calendar.MONTH)+1)+"月",
                    88, mid+18, 48, INK, true, Paint.Align.LEFT, 1f);
            caret(c, 372, mid+4, 20, INK);
            hot("month:pick", 80, mid-44, 420, mid+44);
        } else {
            Calendar now = Calendar.getInstance(); now.add(Calendar.YEAR, s.yearOffset);
            txt(c, now.get(Calendar.YEAR)+"年", 88, mid+18, 48, INK, true, Paint.Align.LEFT, 1f);
            caret(c, 300, mid+4, 20, INK);
            hot("year:pick", 80, mid-44, 340, mid+44);
        }
        // 支出 / 收入
        float sw=170, sh=104, x0=880, y0=mid-52;
        String[] lab = {"支出","收入"};
        for (int i=0;i<2;i++){
            boolean on = (i==1)==s.income;
            if (on){
                p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(SLATE);
                c.drawRoundRect(x0+i*sw, y0, x0+(i+1)*sw, y0+sh, sh/2, sh/2, p);
            }
            txt(c, lab[i], x0+i*sw+sw/2, y0+64, 42, on?0xffffffff:INK, true, Paint.Align.CENTER, 1f);
            hot("tab:"+s.tab+":mode:"+i, x0+i*sw, y0, x0+(i+1)*sw, y0+sh);
        }
    }

    private Calendar mondayOf(Calendar any, int weekOffset){
        Calendar d = (Calendar) any.clone();
        d.add(Calendar.DAY_OF_MONTH, weekOffset*7);
        int dow = d.get(Calendar.DAY_OF_WEEK);          // SUN=1
        int back = (dow==Calendar.SUNDAY)?6:(dow-2);    // 周一为起点
        d.add(Calendar.DAY_OF_MONTH, -back);
        zero(d);
        return d;
    }
    private void zero(Calendar d){ d.set(Calendar.HOUR_OF_DAY,0); d.set(Calendar.MINUTE,0);
        d.set(Calendar.SECOND,0); d.set(Calendar.MILLISECOND,0); }

    // ── 大卡片（底图取自参考图抠出的卡片，金额/笔数为实时数据） ────────
    private void bigCard(Canvas c, State s, float top){
        float h = 386, l = 41, rr = 1237;
        String key = s.tab==0 ? "hero_week" : s.tab==1 ? "hero_month" : "hero_year";
        Bitmap plate = art.get(key);
        p.setShader(null); p.setStyle(Paint.Style.FILL);
        if (plate != null){
            p.setColor(Color.WHITE);
            r.set(l, top, rr, top+h);
            ph.reset(); ph.addRoundRect(r, 40, 40, Path.Direction.CW); c.save(); c.clipPath(ph);
            c.drawBitmap(plate, null, r, p); c.restore();
        } else {
            int c1, c2, c3;
            if (s.tab==1){ c1=0xfffbe0bb; c2=0xfff7d6ab; c3=0xfffdf2e3; }
            else { c1=0xffd2e9f9; c2=0xffe8f4fd; c3=0xfffbfdff; }
            p.setShader(new LinearGradient(rr, top, l, top+h, new int[]{c1,c2,c3},
                    new float[]{0f,0.45f,1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(l, top, rr, top+h, 40, 40, p);
            p.setShader(null);
        }
        double tot = totalOf(s);
        int cnt = countOf(s);
        if (s.income && s.tab==1){ tot = d(s, "mi.b.4", 0); cnt = ci(s, "mi.n", 0); }
        else if (s.income && s.tab==2){
            tot = d(s, "yi.total", yearIncomeTotal(s)); cnt = ci(s, "yi.n", 9);
        } else {
            tot = d(s, "big.total." + s.tab, tot);
            cnt = ci(s, "big.cnt." + s.tab, cnt);
        }
        txt(c, s.income?"收入":"支出", 95, top+79, 42, 0xff5a7280, false, Paint.Align.LEFT, 1f);
        txt(c, money(tot), 94, top+242, 80, 0xff1b262c, true, Paint.Align.LEFT, 0.88f);
        String sub = "共" + cnt + "笔  查看统计设置 ›";
        txt(c, sub, 95, top+320, 41, 0xff768485, false, Paint.Align.LEFT, 1f);
        if (s.tab==0){
            txt(c,"较上周", 634, top+128, 40, 0xff3a3f46, false, Paint.Align.LEFT, 1f);
            txt(c,"↑"+trim2(d(s,"w.prev",20.00))+"元", 768, top+127, 40, 0xffc43f42, true, Paint.Align.LEFT, 0.84f);
            hot("w.prev", 620, top+86, 1215, top+160);
        }
        hot("big:total", 90, top+170, 720, top+276);
        hot("big:cnt", 90, top+284, 720, top+344);
    }

    /** 年度收入合计（12 个月求和，自动计算）。 */
    private double yearIncomeTotal(State s){
        double t = 0;
        for (int i = 0; i < 12; i++) t += d(s, "yi.m." + i, i == 3 ? 20.00 : i == 4 ? 21.48
                : i == 5 ? 1666.00 : i == 7 ? 1400.01 : 0);
        return t;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  周度
    // ══════════════════════════════════════════════════════════════════════
    private void drawWeek(Canvas c, State s){
        periodRow(c, s, 340);
        bigCard(c, s, 502);
        List<Slice> sl = slices(s);
        weekSummary(c, s, 944);
        weekRank(c, s, 2035);
        weekCategory(c, s, 2535, sl);
        footer(c, 3966);
    }

    private void weekSummary(Canvas c, State s, float top){
        plate(c, "week_sum", top, 999);
        float base = top+670;                       // 柱状图基线
        p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2);
        p.setColor(0xffd5d8dc);
        c.drawLine(82, base, 1216, base, p);
        p.setStyle(Paint.Style.FILL);
        double[] cur = {d(s,"w.c.0",20.00),0,0,0,0,0,0};
        double mx = 1; for (double v : cur) mx = Math.max(mx, v);
        float plotH = 425;
        for (int i=0;i<7;i++){
            float lx = 138.5f + 159.6f*i, dx = lx + 44f;
            p.setColor(0xffa8cbf5); c.drawCircle(lx, base, 11, p);
            if (cur[i] > 0){
                float bh = (float)(cur[i]/mx*plotH);
                p.setColor(0xff2b86ff);
                c.drawRoundRect(dx-15.5f, base-bh, dx+15.5f, base, 15.5f, 15.5f, p);
                hot("w.bar:"+i, dx-24, base-bh-20, dx+24, base);
            } else {
                p.setColor(0xff2b86ff); c.drawCircle(dx, base, 12, p);
            }
        }
        Calendar mon = mondayOf(Calendar.getInstance(), s.weekOffset);
        Calendar today = Calendar.getInstance(); zero(today);
        float pw=153, step=163.5f, px=82, py=top+845, phh=152;
        for (int i=0;i<7;i++){
            Calendar dc=(Calendar)mon.clone(); dc.add(Calendar.DAY_OF_MONTH,i);
            boolean isToday = dc.get(Calendar.YEAR)==today.get(Calendar.YEAR)
                    && dc.get(Calendar.DAY_OF_YEAR)==today.get(Calendar.DAY_OF_YEAR);
            float x = px + i*step;
            p.setColor(isToday?0xffa0c9ff:0xffecf3fb);
            c.drawRoundRect(x, py, x+pw, py+phh, 26, 26, p);
            txt(c, isToday?"今天":String.format(Locale.US,"%d.%02d",
                            dc.get(Calendar.MONTH)+1, dc.get(Calendar.DAY_OF_MONTH)),
                    x+pw/2f, py+67, 40, 0xff302f34, false, Paint.Align.CENTER, 1f);
            Double dv = s.edits.get("w.day." + i);
            String amt;
            if (dv != null) amt = trim2(dv);
            else if (i == 0) amt = trim2(d(s,"w.c.0",20.00));
            else if (isToday) amt = "0.00";
            else amt = "";
            txt(c, amt, x+pw/2f, py+116, 32, 0xff656c72, false, Paint.Align.CENTER, 1f);
            hot("w.day:"+i, x, py, x+pw, py+phh);
        }
    }

    private void weekRank(Canvas c, State s, float top){
        plate(c, "week_rank", top, 379);
        round(c, 82, top+163, 1197, top+365, 26, 0xfff2f3f7);
        txt(c, "1", 131, top+288, 48, 0xff3a3f46, false, Paint.Align.CENTER, 1f);
        txt(c, "CAT2610125E01AW", 189, top+254, 42, 0xff1f2329, false, Paint.Align.LEFT, 1f);
        txt(c, "其他", 189, top+328, 40, 0xff9aa2ad, false, Paint.Align.LEFT, 1f);
        txt(c, money(d(s,"w.rank.0",20.00)), 1155, top+265, 48, 0xff1f2329, true, Paint.Align.RIGHT, 1f);
        txt(c, "10-12 20:44", 1154, top+324, 40, 0xff9aa2ad, false, Paint.Align.RIGHT, 1f);
        hot("w.rank.0", 82, top+163, 1197, top+365);
    }

    private void weekCategory(Canvas c, State s, float top, List<Slice> sl){
        plate(c, "week_cat", top, 1308);
        toggleW(c, 1101, top+68, s.compare);
        hot("w.compare", 900, top+42, 1215, top+150);
        float cx=636, cy=top+615;
        donutW(c, cx, cy, sl);
        leaderW(c, cx, cy, sl);
        float ry = top+1163;
        for (int i=0;i<sl.size();i++){
            Slice z = sl.get(i);
            txt(c, (i+1)+"."+z.name+"  "+pct(z.pct), 84, ry, 42, 0xff1f2329, true, Paint.Align.LEFT, 1f);
            txt(c, money(z.amount)+"("+z.count+"笔)", 1129, ry, 42, 0xff1f2329, true, Paint.Align.RIGHT, 1f);
            chevron(c, 1180, ry-14, 0xffc3c8d2);
            hot("w.cat:"+i, 60, ry-60, 1215, ry+30);
            ry += 130;
        }
    }

    /** 画一张抠出来的卡片底图（圆角裁切）。 */
    private void plate(Canvas c, String tag, float top, float h){
        Bitmap bm = art.get(tag);
        if (bm == null) return;
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE);
        r.set(41, top, 1237, top+h);
        ph.reset(); ph.addRoundRect(r, 40, 40, Path.Direction.CW);
        c.save(); c.clipPath(ph); c.drawBitmap(bm, null, r, p); c.restore();
    }

    /** 环形图：外浅蓝环 + 数据环 + 白心，半径按参考图。 */
    private void donutW(Canvas c, float cx, float cy, List<Slice> sl){
        p.setShader(null); p.setStyle(Paint.Style.FILL);
        p.setColor(0xffa9c9f6); c.drawCircle(cx, cy, 246, p);
        p.setColor(Color.WHITE); c.drawCircle(cx, cy, 220, p);
        r.set(cx-215, cy-215, cx+215, cy+215);
        float ang=-90f;
        for (Slice z : sl){
            float full = z.pct*3.6f;
            p.setColor(z.color);
            c.drawArc(r, ang+0.35f, Math.max(2f, full-0.7f), true, p);
            ang += full;
        }
        p.setColor(Color.WHITE); c.drawCircle(cx, cy, 130, p);
        if (sl.size()==1){
            txt(c, sl.get(0).name, cx, cy-10, 50, 0xff8b929b, false, Paint.Align.CENTER, 1f);
            txt(c, pct(sl.get(0).pct), cx, cy+58, 62, 0xff4a5059, true, Paint.Align.CENTER, 1f);
        } else {
            txt(c, "合计", cx, cy-10, 44, 0xff8b929b, false, Paint.Align.CENTER, 1f);
            txt(c, pct(100), cx, cy+58, 58, 0xff4a5059, true, Paint.Align.CENTER, 1f);
        }
    }

    /** 环形图右下角的引线标签。 */
    private void leaderW(Canvas c, float cx, float cy, List<Slice> sl){
        Slice z = sl.isEmpty()?null:sl.get(0);
        if (z==null) return;
        float y0 = cy+240, y1 = cy+255, x1 = 1154;
        p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f);
        p.setColor(0xff8f959e);
        c.drawLine(cx, y0, cx, y1, p);
        c.drawLine(cx, y1, x1, y1, p);
        c.drawLine(x1, y1, x1, y1-30, p);
        p.setStyle(Paint.Style.FILL);
        txt(c, z.name+" "+pct(z.pct), x1, y1-36, 42, 0xff5b6470, false, Paint.Align.RIGHT, 1f);
    }

    /** 对比开关（关：白底灰边、圆点在左；开：蓝底、圆点在右）。 */
    private void toggleW(Canvas c, float x, float y, boolean on){
        p.setShader(null); p.setStyle(Paint.Style.FILL);
        p.setColor(on?0xff1777ff:Color.WHITE);
        c.drawRoundRect(x, y, x+97, y+54, 27, 27, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f);
        p.setColor(on?0xff1777ff:0xffdadde2);
        c.drawRoundRect(x, y, x+97, y+54, 27, 27, p);
        p.setStyle(Paint.Style.FILL);
        float kx = on ? x+70 : x+27;
        p.setColor(Color.WHITE); c.drawCircle(kx, y+27, 26, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f);
        p.setColor(on?0xff7fb4ff:0xffdadde2);
        c.drawCircle(kx, y+27, 26, p);
        p.setStyle(Paint.Style.FILL);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  月度
    // ══════════════════════════════════════════════════════════════════════
    private void drawMonth(Canvas c, State s){
        periodRow(c, s, 340);
        bigCard(c, s, 500);
        if (s.income){ drawMonthIncome(c, s); return; }
        subTabs(c, 930, new String[]{"月度小结","支出分类","省钱建议"}, 0, "m");
        float ex = monthExtra(s);
        monthSummary(c, s, 1135);
        monthCategory(c, s, 3300 + ex, slices(s));
        monthPay(c, s, 5400 + ex);
        monthSave(c, s, 6010 + ex);
        footer(c, 6600 + ex);
    }

    private void subTabs(Canvas c, float y, String[] names, int active, String key){
        float third = W/3f;
        for (int i=0;i<names.length;i++){
            float cx = third*i + third/2f;
            txt(c, names[i], cx, y+40, 40, i==active?INK:0xff6b7480, i==active, Paint.Align.CENTER, 1f);
            if (i==active){ p.setColor(INK); c.drawRoundRect(cx-46,y+62,cx+46,y+69,4,4,p); }
            hot(key+".sect:"+i, third*i, y-10, third*(i+1), y+80);
        }
    }

    private void monthSummary(Canvas c, State s, float top){
        float l=40, rr=W-40, h=2115 + monthExtra(s);
        round(c, l, top, rr, top+h, 40, CARD);
        cardTitle(c, "cal", "月度小结", l+55, top+95, 46);
        // 订阅周报
        round(c, 955, top+60, 1210, top+140, 40, PILL);
        txt(c, "+ 订阅周报", 1082, top+112, 36, 0xff5b6470, false, Paint.Align.CENTER, 1f);
        hot("m.subscribe", 950, top+55, 1215, top+145);
        // 提示
        txt(c, "●", 78, top+262, 40, 0xff3a3f46, false, Paint.Align.LEFT, 1f);
        txt(c, "本月目前支出增长不少，对比上月同周期的支出增加了", 108, top+262, 40, 0xff3a3f46, false, Paint.Align.LEFT, 1f);
        txt(c, money(d(s,"m.up",82.92))+"元，", 108, top+340, 40, 0xff3a3f46, false, Paint.Align.LEFT, 1f);
        txt(c, "设置每月预算", 350, top+340, 40, BLUE, true, Paint.Align.LEFT, 1f);
        hot("m.up", 108, top+300, 340, top+360);

        // 四张小卡
        String[] lab = {"大额消费","自动扣款","先用后付","分期付款"};
        double[] val = {d(s,"m.k.0",0), d(s,"m.k.1",4), d(s,"m.k.2",0), d(s,"m.k.3",0)};
        float cw=(1140-3*20)/4f, cx0=75, cy0=top+495, ch=250;
        for (int i=0;i<4;i++){
            float x = cx0 + i*(cw+20);
            round(c, x, cy0, x+cw, cy0+ch, 24, TILE);
            txt(c, lab[i], x+28, cy0+80, 36, 0xff4a5059, false, Paint.Align.LEFT, 1f);
            txt(c, fmtInt(val[i]), x+28, cy0+195, 60, INK, true, Paint.Align.LEFT, 1f);
            chevron(c, x+cw-38, cy0+175, 0xffc3c8d2);
            hot("m.k:"+i, x, cy0, x+cw, cy0+ch);
        }

        // 近五月柱状
        Calendar now=Calendar.getInstance(); now.add(Calendar.MONTH, s.monthOffset);
        String[] ml = new String[5]; double[] mv = new double[5];
        double[] defv = {3063.44,1247.21,1755.18,182.27,d(s,"m.c.0",50.00)+d(s,"m.c.1",41.19)+d(s,"m.c.2",20.00)};
        for (int i=0;i<5;i++){
            Calendar dcur=(Calendar)now.clone(); dcur.add(Calendar.MONTH, i-4);
            ml[i]=(dcur.get(Calendar.MONTH)+1)+"月";
            mv[i]=d(s,"m.bar."+i, defv[i]);
        }
        float bx0=140, bx1=1190, bw=(bx1-bx0)/5f;
        float base=top+1215, plotTop=top+830, max=3400;
        double avg=0; for (double v:mv) avg+=v; avg/=5;
        float ay = base - (float)(avg/max*(base-plotTop));
        p.setColor(0xffe6dba0); p.setStrokeWidth(2);
        dashLine(c, bx0-20, ay, bx1+20, ay);
        txt(c, trim2(avg), 95, ay-18, 34, 0xff5b6470, false, Paint.Align.LEFT, 1f);
        for (int i=0;i<5;i++){
            float x = bx0 + i*bw + bw/2f;
            float bh=(float)(mv[i]/max*(base-plotTop));
            boolean cur = i==4;
            p.setColor(cur?BLUE:LBLUE);
            c.drawRoundRect(x-26, base-bh, x+26, base, 26, 26, p);
            txt(c, "¥"+trim2(mv[i]), x, base-bh-22, 30, 0xff5b6470, false, Paint.Align.CENTER, 1f);
            txt(c, ml[i], x, base+52, 36, 0xff3a3f46, false, Paint.Align.CENTER, 1f);
            hot("m.bar:"+i, x-30, base-bh-40, x+30, base+60);
        }
        p.setColor(0xffe6e9ee); p.setStrokeWidth(2); c.drawLine(bx0-20, base, bx1+20, base, p);
        txt(c, "— 月支出均值", W/2f, top+1390, 38, 0xff8b929b, false, Paint.Align.CENTER, 1f);
        monthCalendar(c, s, top+1520);
        txt(c, (s.showAll?"收起":"展开全部")+"  ⌄", W/2f, top+h-40, 38, 0xff8b929b, false, Paint.Align.CENTER, 1f);
        hot("m.expand", 400, top+h-110, 880, top+h-10);
    }

    private void monthCalendar(Canvas c, State s, float top){
        String[] wk={"日","一","二","三","四","五","六"};
        float x0=75, cw=(1130)/7f;
        for (int i=0;i<7;i++) txt(c, wk[i], x0+cw*i+cw/2f, top+45, 38, 0xff6b7480, false, Paint.Align.CENTER, 1f);
        Calendar now=Calendar.getInstance(); now.add(Calendar.MONTH, s.monthOffset); zero(now);
        Calendar today=Calendar.getInstance(); zero(today);
        int days=now.getActualMaximum(Calendar.DAY_OF_MONTH);
        List<Integer> cells=new ArrayList<>();
        if (s.showAll){
            int firstDow = now.get(Calendar.DAY_OF_WEEK)-1;   // 0=Sun
            for (int i=0;i<firstDow;i++) cells.add(-1);
            for (int d=1; d<=days; d++) cells.add(d);
        } else {
            // 上一周 + 本周（周日起）
            Calendar cur=(Calendar)today.clone();
            int dow=cur.get(Calendar.DAY_OF_WEEK)-1;
            cur.add(Calendar.DAY_OF_MONTH, -dow-7);
            for (int i=0;i<14;i++){ cells.add(cur.get(Calendar.DAY_OF_MONTH)); cur.add(Calendar.DAY_OF_MONTH,1); }
        }
        float cy0=top+80, ch=150;
        for (int i=0;i<cells.size();i++){
            int day=cells.get(i);
            if (day<0) continue;
            int row=i/7, col=i%7;
            float x=x0+cw*col+6, y=cy0+row*(ch+16);
            boolean isToday = day==today.get(Calendar.DAY_OF_MONTH) && now.get(Calendar.MONTH)==today.get(Calendar.MONTH)
                    && now.get(Calendar.YEAR)==today.get(Calendar.YEAR);
            p.setColor(isToday?HILITE:PILL);
            c.drawRoundRect(x, y, x+cw-12, y+ch, 22, 22, p);
            txt(c, isToday?"今天":String.valueOf(day), x+(cw-12)/2f, y+72, 40, 0xff3a3f46, false, Paint.Align.CENTER, 1f);
            Double typed = s.edits.get("m.day." + day);
            if (typed != null){
                txt(c, trim2(typed), x+(cw-12)/2f, y+122, 30, GREEN, true, Paint.Align.CENTER, 1f);
            } else {
                String amt = day==8?"1.40": day==12? money(d(s,"w.c.0",20.00)) : "-";
                txt(c, amt, x+(cw-12)/2f, y+122, 30, 0xff8b929b, false, Paint.Align.CENTER, 1f);
            }
            hot("m.day:" + day, x, y, x+cw-12, y+ch);
        }
    }

    private void monthCategory(Canvas c, State s, float top, List<Slice> sl){
        float l=40, rr=W-40, h=2050;
        round(c, l, top, rr, top+h, 40, CARD);
        cardTitle(c, "pie", "支出分类", l+45, top+95, 46);
        txt(c, "对比上月", 1000, top+92, 38, GREY, false, Paint.Align.LEFT, 1f);
        toggleSwitch(c, 1105, top+56, s.compare);
        hot("m.compare", 1060, top+40, 1215, top+140);
        txt(c, "●", 78, top+245, 38, 0xff3a3f46, false, Paint.Align.LEFT, 1f);
        Slice top1 = sl.isEmpty()?null:sl.get(0);
        txt(c, (top1==null?"":top1.name)+" 分类消费占比最高，", 108, top+245, 40, 0xff3a3f46, false, Paint.Align.LEFT, 1f);
        txt(c, "日用百货", 108, top+325, 40, 0xff3a3f46, false, Paint.Align.LEFT, 1f);
        txt(c, "分类消费较上月变化最大", 300, top+325, 40, 0xff3a3f46, false, Paint.Align.LEFT, 1f);
        // 高亮下划线
        p.setColor(HILITE);
        c.drawRect(112, top+256, 232, top+266, p);
        c.drawRect(112, top+336, 292, top+346, p);

        float cx=640, cy=top+770, ro=250, ri=160;
        donut(c, cx, cy, ro, ri, sl);
        leaders(c, cx, cy, ro, sl, top+770, 0f);
        float ry=top+1290;
        for (int i=0;i<sl.size();i++){
            Slice z=sl.get(i);
            boolean open = i==s.expanded && i==0;
            txt(c, (i+1)+"."+z.name+"  "+pct(z.pct), 85, ry, 42, INK, true, Paint.Align.LEFT, 1f);
            txt(c, money(z.amount)+"("+z.count+"笔)", 1100, ry, 42, INK, true, Paint.Align.RIGHT, 1f);
            if (open) chevronUp(c, 1175, ry-14, 0xffc3c8d2); else chevron(c, 1175, ry-14, 0xffc3c8d2);
            hot("m.cat:"+i, 60, ry-60, 1215, ry+30);
            if (i==0) hot("m.exp:"+i, 1090, ry-55, 1215, ry+25);
            if (open){
                float sy=ry+40;
                round(c, 80, sy, 1200, sy+300, 24, TILE);
                art(c,"phone", 120, sy+35, 250, sy+165);
                txt(c, "手机充值 ›", 120, sy+235, 42, INK, false, Paint.Align.LEFT, 1f);
                txt(c, money(z.amount), 120, sy+295, 40, 0xff5b6470, false, Paint.Align.LEFT, 1f);
                ry += 340;
            }
            ry += 190;
        }
    }

    private void monthPay(Canvas c, State s, float top){
        float l=40, rr=W-40, h=560;
        round(c, l, top, rr, top+h, 40, CARD);
        cardTitle(c, "card", "支出方式", l+55, top+95, 46);
        round(c, 85, top+180, 1195, top+420, 24, TILE);
        art(c,"cmb", 120, top+235, 235, top+350);
        txt(c, "招商银行储蓄卡(4646)", 275, top+310, 42, INK, false, Paint.Align.LEFT, 1f);
        txt(c, money(d(s,"m.pay.0",259.06))+"(8笔)", 1100, top+310, 42, INK, true, Paint.Align.RIGHT, 1f);
        chevron(c, 1165, top+296, 0xffc3c8d2);
        hot("m.pay:0", 85, top+180, 1195, top+420);
    }

    private void monthSave(Canvas c, State s, float top){
        float l=40, rr=W-40, h=500;
        round(c, l, top, rr, top+h, 40, CARD);
        cardTitle(c, "yuan", "省钱建议", l+55, top+95, 46);
        txt(c, "本月已省 ›", 240, top+230, 40, 0xff4a5059, false, Paint.Align.LEFT, 1f);
        txt(c, money(d(s,"m.saved",0.00)), 240, top+330, 66, INK, true, Paint.Align.LEFT, 1f);
        hot("m.saved", 200, top+210, 660, top+380);
        txt(c, "最省日 ›", 820, top+230, 40, 0xff4a5059, false, Paint.Align.LEFT, 1f);
        Calendar now=Calendar.getInstance(); now.add(Calendar.MONTH, s.monthOffset);
        txt(c, (now.get(Calendar.MONTH)+1)+"月1日", 820, top+330, 66, INK, true, Paint.Align.LEFT, 1f);
    }


    // ══════════════════════════════════════════════════════════════════════
    //  年度
    // ══════════════════════════════════════════════════════════════════════
    private void drawYear(Canvas c, State s){
        periodRow(c, s, 340);
        bigCard(c, s, 500);
        if (s.income){ drawYearIncome(c, s); return; }
        if (!s.noticeClosed) noticeBar(c, s, 905);
        float y = s.noticeClosed ? 930 : 1090;
        subTabs(c, y, new String[]{"支出分类","月度对比","省钱建议"}, 1, "y");
        yearCategory(c, s, y+180, slices(s));
        y = y+2745;
        yearCompare(c, s, y);
        y += 1090;
        y += 50;
        yearCity(c, s, y);
        y += 1117;
        y += 50;
        yearRank(c, s, y);
        y += 890;
        y += 50;
        yearPay(c, s, y);
        y += 880;
        y += 50;
        yearSave(c, s, y);
        y += 980;
        y += 58;
        moreBanner(c, y);
        footer(c, y+250);
    }

    private void noticeBar(Canvas c, State s, float top){
        float l=40, rr=W-40;
        // 小尖角
        p.setColor(Color.WHITE);
        ph.reset(); ph.moveTo(120, top); ph.lineTo(150, top-22); ph.lineTo(180, top); ph.close();
        c.drawPath(ph, p);
        c.drawRoundRect(l, top, rr, top+175, 20, 20, p);
        txt(c, "有投资理财交易默认不计入收支统计。如需加入统计，请", 90, top+75, 38, 0xff3a3f46, false, Paint.Align.LEFT, 1f);
        txt(c, s.investIncluded?"已计入统计":"点击修改", 90, top+135, 38, BLUE, true, Paint.Align.LEFT, 1f);
        p.setColor(HILITE); c.drawRect(90, top+145, 190, top+153, p);
        // 关闭
        p.setColor(0xffb6bcc4); p.setStrokeWidth(5); p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(rr-70, top+40, rr-38, top+72, p);
        c.drawLine(rr-38, top+40, rr-70, top+72, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        hot("y.notice.x", rr-100, top+10, rr-10, top+100);
        hot("y.invest", 80, top+100, 260, top+165);
    }

    private void yearCategory(Canvas c, State s, float top, List<Slice> sl){
        float l=40, rr=W-40, h=2565;
        round(c, l, top, rr, top+h, 40, CARD);
        cardTitle(c, "pie", "支出分类", l+45, top+100, 46);
        float cx=640, cy=top+620, ro=207, ri=134;
        donut(c, cx, cy, ro, ri, sl);
        leaders(c, cx, cy, ro, sl, top+620, 0f);
        float ry=top+1020;
        String[] sub={"正餐","其他","炸鸡汉堡","生鲜水果","咖啡奶茶","零食"};
        String[] subArt={"f0","f1","f2","f3","f4","f5"};
        double[] subAmt={d(s,"y.t.0",2013.67), d(s,"y.t.1",644.05), d(s,"y.t.2",243.00),
                         d(s,"y.t.3",20.40), d(s,"y.t.4",8.00), d(s,"y.t.5",4.00)};
        for (int i=0;i<sl.size();i++){
            Slice z=sl.get(i);
            boolean open = (i==s.expanded);
            txt(c, (i+1)+"."+z.name+"  "+pct(z.pct), 85, ry, 42, INK, true, Paint.Align.LEFT, 1f);
            txt(c, money(z.amount)+"("+z.count+"笔)", 1100, ry, 42, INK, true, Paint.Align.RIGHT, 1f);
            if (open) chevronUp(c, 1175, ry-14, 0xffc3c8d2); else chevron(c, 1175, ry-14, 0xffc3c8d2);
            hot("y.cat:"+i, 60, ry-60, 1215, ry+30);
            if (i==0) hot("y.exp:"+i, 1090, ry-55, 1215, ry+25);
            if (open && i==0 && z.amount > 0){
                float tx=80, tw=(1120-2*16)/3f, th=290;
                for (int k=0;k<6;k++){
                    int rr2=k/3, cc=k%3;
                    float x=tx+cc*(tw+16), yy=ry+50+rr2*(th+16);
                    round(c, x, yy, x+tw, yy+th, 20, TILE);
                    art(c, subArt[k], x+35, yy+35, x+150, yy+150);
                    txt(c, sub[k]+" ›", x+35, yy+225, 40, INK, false, Paint.Align.LEFT, 1f);
                    txt(c, money(subAmt[k]), x+35, yy+275, 38, 0xff5b6470, false, Paint.Align.LEFT, 1f);
                    hot("y.tile:"+k, x, yy, x+tw, yy+th);
                }
                ry += 630;
            }
            ry += 210;
        }
        txt(c, (s.showAll?"收起":"展开全部")+"  ⌄", W/2f, ry+30, 38, 0xff8b929b, false, Paint.Align.CENTER, 1f);
        hot("y.expand", 400, ry-30, 880, ry+70);
    }

    private void yearCompare(Canvas c, State s, float top){
        float l=40, rr=W-40, h=1090;
        round(c, l, top, rr, top+h, 40, CARD);
        cardTitle(c, "cal", "月度对比", l+45, top+100, 46);
        double[] mv=new double[12];
        double[] def={520,480,360,760,1700,3063.44,1150,1697,520,470,480,610};
        double max=3400; double avg=0; int maxI=0;
        for (int i=0;i<12;i++){ mv[i]=d(s,"y.m."+i, def[i]); avg+=mv[i]; if (mv[i]>mv[maxI]) maxI=i; }
        avg/=12;
        float base=top+700, plotTop=top+250, x0=110, x1=1180, bw=(x1-x0)/12f;
        for (int i=0;i<12;i++){
            float x=x0+bw*i+bw/2f;
            float bh=(float)(mv[i]/max*(base-plotTop));
            p.setColor(i==maxI?BLUE:LBLUE);
            c.drawRoundRect(x-16, base-bh, x+16, base, 16, 16, p);
            txt(c, String.valueOf(i+1), x, base+62, 40, i==maxI?INK:0xff6b7480, i==maxI, Paint.Align.CENTER, 1f);
            hot("y.m:"+i, x-25, base-bh-30, x+25, base+70);
        }
        float ay=base-(float)(avg/max*(base-plotTop));
        p.setColor(0xffe6dba0); p.setStrokeWidth(2); dashLine(c, x0-40, ay, x1+30, ay);
        txt(c, trim2(avg), 70, ay-20, 36, 0xff5b6470, false, Paint.Align.LEFT, 1f);
        p.setColor(0xffe6e9ee); c.drawLine(x0-40, base, x1+30, base, p);
        // 气泡
        float bx=x0+bw*maxI+bw/2f;
        Calendar now=Calendar.getInstance(); now.add(Calendar.YEAR, s.yearOffset);
        String t1=(now.get(Calendar.YEAR))+"年"+(maxI+1)+"月 最多";
        String t2="¥"+trim2(mv[maxI])+"  ▶";
        float pw=330, px=bx-pw/2f, py=base-(float)(mv[maxI]/max*(base-plotTop))-190;
        p.setColor(BLUE); c.drawRoundRect(px, py, px+pw, py+150, 26, 26, p);
        ph.reset(); ph.moveTo(bx-22, py+150); ph.lineTo(bx, py+185); ph.lineTo(bx+22, py+150); ph.close();
        c.drawPath(ph, p);
        txt(c, t1, bx, py+62, 36, 0xffffffff, false, Paint.Align.CENTER, 1f);
        txt(c, t2, bx, py+118, 40, 0xffffffff, true, Paint.Align.CENTER, 1f);
        hot("y.m:"+maxI, px, py, px+pw, py+185);
        txt(c, "— 月支出均值", W/2f, top+h-60, 38, 0xff8b929b, false, Paint.Align.CENTER, 1f);
    }

    private void yearCity(Canvas c, State s, float top){
        // 尺寸全部对着参考图重新量过：卡片高、两行提示语、天际线、大字城市、时间轴
        float l=41, rr=1237, h=1117;
        round(c, l, top, rr, top+h, 40, CARD);
        cardTitle(c, "map", "城市解锁", 86, top+108, 54);

        String n = fmtInt(d(s, "yc.n", 2));
        String c1 = t(s, "yc.c1", "恩施土家族苗族…");
        String n1 = fmtInt(d(s, "yc.n1", 10));
        String c2 = t(s, "yc.c2", "上海");
        String n2 = fmtInt(d(s, "yc.n2", 69));

        // 提示语：按参考图折成两行，中间两处浅蓝高亮
        float size = 46, sx = 0.86f, x = 148;
        txt(c, "●", 100, top+258, 44, 0xff3a3f46, false, Paint.Align.LEFT, 1f);
        float w1 = tw("已经在", size, sx), w2 = tw(n + "个城市", size, sx);
        float w3 = tw("使用线下支付，", size, sx), w4 = tw(c2, size, sx);
        txt(c, "已经在", x, top+258, size, 0xff3a3f46, false, Paint.Align.LEFT, sx);
        txt(c, n + "个城市", x+w1, top+258, size, 0xff3a3f46, false, Paint.Align.LEFT, sx);
        txt(c, "使用线下支付，", x+w1+w2, top+258, size, 0xff3a3f46, false, Paint.Align.LEFT, sx);
        txt(c, c2, x+w1+w2+w3, top+258, size, 0xff3a3f46, false, Paint.Align.LEFT, sx);
        txt(c, "是你消费最多的城", x+w1+w2+w3+w4, top+258, size, 0xff3a3f46, false, Paint.Align.LEFT, sx);
        txt(c, "市", x, top+320, size, 0xff3a3f46, false, Paint.Align.LEFT, sx);
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(HILITE);
        c.drawRect(x+w1, top+276, x+w1+w2, top+286, p);
        c.drawRect(x+w1+w2+w3, top+276, x+w1+w2+w3+w4, top+286, p);
        hot("yc.n", x+w1-12, top+205, x+w1+w2+12, top+300);
        hot("yc.c2", x+w1+w2+w3-12, top+205, x+w1+w2+w3+w4+12, top+300);

        // 右上角的说明
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3);
        p.setColor(0xffc3c8d2);
        c.drawCircle(1163, top+240, 26, p);
        p.setStyle(Paint.Style.FILL);
        txt(c, "?", 1163, top+254, 32, 0xffc3c8d2, false, Paint.Align.CENTER, 1f);

        // 城市速写：参考图里地名后面那片很淡的雾状高楼群（线描 + 细竖纹）
        float skyB = top+838;
        float[] bw = {54,66,42,84,56,110,70,88,52,96,58,68};
        float[] bh = {140,230,100,290,200,380,250,340,160,300,210,130};
        float bx = 132;
        for (int i = 0; i < bw.length; i++){
            float t2 = skyB - bh[i], r2 = bx + bw[i];
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(bx, t2, r2, skyB,
                    0xfffbfdff, 0xffeaf3fe, Shader.TileMode.CLAMP));
            c.drawRoundRect(bx, t2, r2, skyB, 6, 6, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1f); p.setColor(0xffdfeafb);
            for (float xx = bx+4; xx < r2-2; xx += 4)
                c.drawLine(xx, t2+6, xx, skyB-4, p);
            p.setStrokeWidth(1.5f); p.setColor(0xffd5e4f9);
            c.drawRoundRect(bx, t2, r2, skyB, 6, 6, p);
            p.setStyle(Paint.Style.FILL);
            bx = r2 + 16;
        }

        // 两个城市：左边的小城灰色、右边的大城蓝色
        String[] cw = wrapCity(c1, 4);
        txt(c, cw[0], 178, top+618, 66, 0xff6b7480, true, Paint.Align.LEFT, 1f);
        if (!cw[1].isEmpty()) txt(c, cw[1], 178, top+718, 66, 0xff6b7480, true, Paint.Align.LEFT, 1f);
        txt(c, n1+"笔", 434, top+538, 38, 0xff6b7480, false, Paint.Align.CENTER, 1f);
        hot("yc.c1", 150, top+540, 470, top+760);
        hot("yc.n1", 360, top+485, 508, top+562);

        float bigSx = 1f, need = tw(c2, 162, 1f);
        if (need > 420) bigSx = 420f / need;
        txt(c, c2, 523, top+717, 162, BLUE, true, Paint.Align.LEFT, bigSx);
        txt(c, n2+"笔", 813, top+556, 38, BLUE, false, Paint.Align.CENTER, 1f);
        hot("yc.c2", 500, top+540, 900, top+760);
        hot("yc.n2", 740, top+500, 886, top+580);

        // 时间轴
        float tl = top+1014;
        p.setColor(0xffd8dde4); p.setStrokeWidth(3);
        c.drawLine(150, tl, 1058, tl, p);
        p.setColor(0xffb9bfc8);
        c.drawCircle(415, tl, 8, p);
        c.drawCircle(815, tl, 8, p);
        // 起始定位针
        p.setColor(0xffc3c8d2);
        ph.reset();
        ph.moveTo(101, tl-26); ph.lineTo(121, tl-26); ph.lineTo(111, tl-2); ph.close();
        c.drawPath(ph, p);
        c.drawCircle(111, tl-30, 14, p);
        p.setColor(Color.WHITE); c.drawCircle(111, tl-30, 6, p);
        txt(c, wrapCity(c2, 4)[0], 415, tl-80, 34, 0xff8b929b, false, Paint.Align.CENTER, 1f);
        txt(c, t(s, "yc.t1d", "01/03"), 415, tl-37, 32, 0xff8b929b, false, Paint.Align.CENTER, 1f);
        txt(c, t(s, "yc.t2n", "恩施土…"), 802, tl-80, 34, 0xff8b929b, false, Paint.Align.CENTER, 1f);
        txt(c, t(s, "yc.t2d", "08/17"), 802, tl-37, 32, 0xff8b929b, false, Paint.Align.CENTER, 1f);
        txt(c, t(s, "yc.year", "2026"), 1196, tl+16, 40, 0xff8b929b, false, Paint.Align.RIGHT, 1f);
        hot("yc.t1d", 350, tl-95, 480, tl-20);
        hot("yc.t2n", 738, tl-110, 868, tl-55);
        hot("yc.t2d", 738, tl-95, 868, tl-20);
        hot("yc.year", 1090, tl-20, 1210, tl+30);
    }

    private void yearRank(Canvas c, State s, float top){
        float l=40, rr=W-40, h=890;
        round(c, l, top, rr, top+h, 40, CARD);
        cardTitle(c, "bar", "支出排行", l+45, top+95, 46);
        String[] nm={"转账-孝文迪(***迪)","机票","美团-tomorrow·明日美发(…"};
        String[] ct={"转账红包","交通出行","美容美发"};
        String[] tm={"06-19 19:04","08-10 22:15","08-15 16:20"};
        double[] am={1000.00, 669.42, 374.00};
        float y=top+180;
        for (int i=0;i<3;i++){
            round(c, 80, y, 1200, y+190, 24, GRAYTILE);
            txt(c, String.valueOf(i+1), 140, y+110, 40, 0xff3a3f46, false, Paint.Align.CENTER, 1f);
            txt(c, t(s,"y.rn."+i,nm[i]), 195, y+85, 44, INK, false, Paint.Align.LEFT, 1f);
            txt(c, t(s,"y.rc."+i,ct[i]), 195, y+150, 38, GREY, false, Paint.Align.LEFT, 1f);
            txt(c, money(d(s,"y.r."+i,am[i])), 1155, y+85, 44, INK, true, Paint.Align.RIGHT, 1f);
            txt(c, t(s,"y.rt."+i,tm[i]), 1155, y+150, 36, GREY, false, Paint.Align.RIGHT, 1f);
            // 名称 / 分类 / 时间 / 金额都能单独点开改
            hot("y.rn:"+i, 185, y+22, 900, y+108);
            hot("y.rc:"+i, 185, y+112, 900, y+186);
            hot("y.r:"+i,  900, y+14, 1205, y+108);
            hot("y.rt:"+i, 900, y+112, 1205, y+188);
            y += 210;
        }
        txt(c, "展开全部  ⌄", W/2f, y+40, 38, 0xff8b929b, false, Paint.Align.CENTER, 1f);
    }

    private void yearPay(Canvas c, State s, float top){
        float l=40, rr=W-40, h=880;
        round(c, l, top, rr, top+h, 40, CARD);
        cardTitle(c, "card", "支出方式", l+55, top+95, 46);
        String[] nm={"花呗","招商银行储蓄卡(4646)","招商银行储蓄卡(5021)"};
        double[] am={1902.37, 19526.35, 5.00};
        int[] ct={33,183,1};
        float y=top+180;
        for (int i=0;i<3;i++){
            round(c, 80, y, 1200, y+192, 24, TILE);
            // 图标跟参考图一样 94 见方、垂直居中；默认是抠出来的花呗/招行，也能换成相册里选的图
            float il=122, it=y+49, ir=216, ib=y+143;
            Bitmap own = s.payIcons.get(i);
            if (own != null) art(c, own, il, it, ir, ib);
            else art(c, i==0 ? "payHuabei" : "payCmb", il, it, ir, ib);
            txt(c, t(s,"y.pn."+i,nm[i]), 258, y+115, 42, INK, false, Paint.Align.LEFT, 1f);
            txt(c, money(d(s,"y.p."+i,am[i]))+"("+ci(s,"y.pc."+i,ct[i])+"笔)",
                    1120, y+115, 42, INK, true, Paint.Align.RIGHT, 1f);
            chevron(c, 1180, y+101, 0xffc3c8d2);
            hot("y.pi:"+i, 100, y+35, 240, y+165);
            hot("y.pn:"+i, 250, y+45, 820, y+172);
            hot("y.p:"+i,  820, y+30, 1210, y+176);
            y += 220;
        }
    }

    private void yearSave(Canvas c, State s, float top){
        float l=40, rr=W-40, h=980;
        round(c, l, top, rr, top+h, 40, CARD);
        cardTitle(c, "yuan", "省钱建议", l+55, top+95, 46);
        txt(c, "今年已省 ›", 220, top+230, 40, 0xff4a5059, false, Paint.Align.LEFT, 1f);
        txt(c, money(d(s,"y.saved",53.66)), 220, top+330, 66, INK, true, Paint.Align.LEFT, 1f);
        hot("y.saved", 180, top+210, 700, top+380);
        txt(c, "已省40笔", 220, top+390, 34, GREY, false, Paint.Align.LEFT, 1f);
        txt(c, "最省月份 ›", 800, top+230, 40, 0xff4a5059, false, Paint.Align.LEFT, 1f);
        txt(c, "6月", 800, top+330, 66, INK, true, Paint.Align.LEFT, 1f);
        txt(c, "省下30.49元", 800, top+390, 34, GREY, false, Paint.Align.LEFT, 1f);
        round(c, 80, top+440, 1200, top+880, 24, TILE);
        String[] nm={"千问新客专属立减券","千问每日必减","福利抵扣"};
        String[] val={"已省17.40元","已省16.80元","已省10.00元"};
        float y=top+500;
        for (int i=0;i<3;i++){
            txt(c, "TOP"+(i+1), 120, y+50, 36, 0xff8ea6c4, true, Paint.Align.LEFT, 1f);
            txt(c, nm[i], 260, y+50, 40, 0xff3a3f46, false, Paint.Align.LEFT, 1f);
            txt(c, val[i], 1150, y+50, 40, RED, false, Paint.Align.RIGHT, 1f);
            y += 120;
        }
    }

    private void moreBanner(Canvas c, float top){
        float l=40, rr=W-40, h=200;
        p.setShader(new LinearGradient(l,top,rr,top+h, 0xfffdf3ec, 0xfffdeee2, Shader.TileMode.CLAMP));
        c.drawRoundRect(l, top, rr, top+h, 24, 24, p); p.setShader(null);
        art(c, "coin", rr-280, top+20, rr-20, top+180);
        txt(c, "更多分析", l+55, top+125, 52, INK, true, Paint.Align.LEFT, 1f);
        txt(c, "理财分析", l+280, top+122, 40, 0xff8b929b, false, Paint.Align.LEFT, 1f);
        hot("more", l, top, rr, top+h);
    }

    private void footer(Canvas c, float y){
        txt(c, "- 列表到底了 -", W/2f, y, 38, 0xffb6bcc4, false, Paint.Align.CENTER, 1f);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  月度 · 收入
    // ══════════════════════════════════════════════════════════════════════
    private void drawMonthIncome(Canvas c, State s){
        // ── 月度小结 ─────────────────────────────────────────────────────
        round(c, 41, 947, 1237, 3379, 40, CARD);
        cardTitle(c, "cal", "月度小结", 87, 1062, 54);
        round(c, 956, 1007, 1196, 1081, 37, PILL);
        txt(c, "+ 订阅周报", 1076, 1053, 35, 0xff5b6470, false, Paint.Align.CENTER, 1f);
        hot("m.subscribe", 946, 1002, 1206, 1086);

        String[] lab = {"大额收入", "退款", "薪酬"};
        float[] tx = {81f, 461f, 840f};
        for (int i = 0; i < 3; i++){
            round(c, tx[i], 1219, tx[i] + 357, 1446, 28, TILE);
            txt(c, lab[i], tx[i] + 30, 1280, 44, 0xff4a5059, false, Paint.Align.LEFT, 0.92f);
            txt(c, fmtInt(d(s, "mi.k." + i, 0)), tx[i] + 28, 1388, 80, INK,
                    true, Paint.Align.LEFT, 0.85f);
            chevron(c, tx[i] + 322, 1368, 0xffc3c8d2);
            hot("mi.k:" + i, tx[i], 1219, tx[i] + 357, 1446);
        }

        // ── 近五月柱状图 ─────────────────────────────────────────────────
        Calendar now = Calendar.getInstance(); now.add(Calendar.MONTH, s.monthOffset);
        double[] defv = {1666.00, 0, 1400.01, 0, 0};
        double[] mv = new double[5];
        String[] ml = new String[5];
        for (int i = 0; i < 5; i++){
            Calendar dc = (Calendar) now.clone(); dc.add(Calendar.MONTH, i - 4);
            ml[i] = (dc.get(Calendar.MONTH) + 1) + "月";
            mv[i] = d(s, "mi.b." + i, defv[i]);
        }
        double sum = 0, mx = 1;
        for (double v : mv){ sum += v; mx = Math.max(mx, v); }
        float axis = 1890, plotH = 305;
        float[] bxc = {333f, 524.5f, 716f, 907.5f, 1099f};
        float ay = axis - (float) ((sum / 5.0) / mx * plotH);
        p.setShader(null); p.setStyle(Paint.Style.FILL);
        p.setColor(0xffdcdcd2); p.setStrokeWidth(2);
        dashLine(c, 96, ay, 1197, ay);
        txt(c, trim2(sum / 5.0), 96, ay - 20, 28, 0xff5b6470, false, Paint.Align.LEFT, 1f);
        for (int i = 0; i < 5; i++){
            float bh = (float) (mv[i] / mx * plotH);
            if (mv[i] > 0){
                p.setShader(new LinearGradient(0, axis - bh, 0, axis,
                        0xff9fc9fd, 0xffaadeff, Shader.TileMode.CLAMP));
                c.drawRoundRect(bxc[i] - 40.5f, axis - bh, bxc[i] + 40.5f, axis,
                        40.5f, 40.5f, p);
                p.setShader(null);
                txt(c, money(mv[i]), bxc[i], axis - bh - 20, 28, 0xff6b7480,
                        false, Paint.Align.CENTER, 1f);
            } else {
                txt(c, money(0), bxc[i], axis - 20, 28, 0xff6b7480,
                        false, Paint.Align.CENTER, 1f);
            }
            txt(c, ml[i], bxc[i], axis + 32, 30, 0xff3a3f46, false, Paint.Align.CENTER, 1f);
            hot("mi.b:" + i, bxc[i] - 46, axis - bh - 62, bxc[i] + 46, axis + 60);
        }
        p.setColor(0xffe1e1e1); p.setStrokeWidth(2); c.drawLine(96, axis, 1197, axis, p);
        txt(c, "— 月收入均值", 668f, 2072, 34, 0xff8b929b, false, Paint.Align.CENTER, 1f);

        // ── 整月日历 ─────────────────────────────────────────────────────
        calendarFull(c, s, 2335);
        txt(c, "收起", 610, 3315, 34, 0xff8b929b, false, Paint.Align.CENTER, 1f);
        chevronUp(c, 688, 3303, 0xff8b929b);
        hot("m.expand", 400, 3260, 880, 3345);

        // ── 收入排行 ─────────────────────────────────────────────────────
        round(c, 41, 3429, 1237, 3921, 40, CARD);
        cardTitle(c, "bar", "收入排行", 87, 3525, 54);
        txt(c, "暂无数据", 640, 3751, 46, 0xffb6bcc4, false, Paint.Align.CENTER, 0.95f);
        footer(c, 4157);
    }

    /** 整月日历（收入月度）：7 列，1 号按星期对齐，今天高亮。 */
    private void calendarFull(Canvas c, State s, float top){
        String[] wk = {"日","一","二","三","四","五","六"};
        float colW = 153f, pitch = 163.7f, x0 = 81f;
        for (int i = 0; i < 7; i++)
            txt(c, wk[i], x0 + pitch * i + colW / 2f, top - 82, 40, 0xff6b7480,
                    false, Paint.Align.CENTER, 1f);
        Calendar now = Calendar.getInstance(); now.add(Calendar.MONTH, s.monthOffset); zero(now);
        now.set(Calendar.DAY_OF_MONTH, 1);
        Calendar today = Calendar.getInstance(); zero(today);
        int days = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        int firstDow = now.get(Calendar.DAY_OF_WEEK) - 1;      // 0 = 周日（按 1 号对齐）
        float rowH = 150f, rowPitch = 182f;
        for (int d = 1; d <= days; d++){
            int idx = firstDow + d - 1;
            float x = x0 + pitch * (idx % 7), y = top + (idx / 7) * rowPitch;
            boolean isToday = now.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    && now.get(Calendar.MONTH) == today.get(Calendar.MONTH)
                    && d == today.get(Calendar.DAY_OF_MONTH);
            p.setShader(null); p.setStyle(Paint.Style.FILL);
            p.setColor(isToday ? 0xffa0c9ff : 0xffecf3fb);
            c.drawRoundRect(x, y, x + colW, y + rowH, 26, 26, p);
            txt(c, isToday ? "今天" : String.valueOf(d), x + colW / 2f, y + 47, 44,
                    0xff302f34, true, Paint.Align.CENTER, 0.9f);
            Double typed = s.edits.get("mi.day." + d);
            if (typed != null){
                txt(c, trim2(typed), x + colW / 2f, y + 103, 30, GREEN,
                        true, Paint.Align.CENTER, 1f);
            } else {
                txt(c, "-", x + colW / 2f, y + 103, 30, 0xff9aa2ad,
                        false, Paint.Align.CENTER, 1f);
            }
            hot("mi.day:" + d, x, y, x + colW, y + rowH);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  年度 · 收入
    // ══════════════════════════════════════════════════════════════════════
    private void drawYearIncome(Canvas c, State s){
        float shift = s.noticeClosed ? -196f : 0f;
        if (!s.noticeClosed) noticeBar(c, s, 931);
        float top = 1181f + shift;

        // ── 月度对比 ─────────────────────────────────────────────────────
        round(c, 41, top, 1237, top + 1565, 40, CARD);
        cardTitle(c, "cal", "月度对比", 87, top + 89, 54);

        double[] def = {0, 0, 0, 20.00, 21.48, 1666.00, 0, 1400.01, 0, 0, 0, 0};
        double[] mv = new double[12];
        double sum = 0;
        int maxI = 0;
        for (int i = 0; i < 12; i++){
            mv[i] = d(s, "yi.m." + i, def[i]);
            sum += mv[i];
            if (mv[i] > mv[maxI]) maxI = i;
        }
        int elapsed = s.yearOffset == 0 ? Calendar.getInstance().get(Calendar.MONTH) + 1 : 12;
        if (elapsed < 1) elapsed = 1;
        double avg = sum / elapsed;
        double mx = Math.max(1.0, mv[maxI]);

        float axis = top + 1273, plotH = 709f;
        float x0 = 370f, pitch = 67.6f;
        for (int i = 0; i < 12; i++){
            float bx = x0 + pitch * i;
            float bh = (float) (mv[i] / mx * plotH);
            if (mv[i] > 0){
                p.setShader(null); p.setStyle(Paint.Style.FILL);
                p.setColor(i == maxI ? BLUE : 0xffd2e8ff);
                c.drawRoundRect(bx - 24.5f, axis - bh, bx + 24.5f, axis, 24.5f, 24.5f, p);
            }
            txt(c, String.valueOf(i + 1), bx, axis + 66, 56,
                    i == maxI ? INK : 0xff6b7480, i == maxI, Paint.Align.CENTER, 1f);
            hot("yi.m:" + i, bx - 32, axis - bh - 40, bx + 32, axis + 92);
        }
        float ay = axis - (float) (avg / mx * plotH);
        p.setColor(0xffdcdcd2); p.setStrokeWidth(2);
        dashLine(c, 334, ay, 1197, ay);
        txt(c, trim2(avg), 195, ay + 1, 35, 0xff3a3f46, false, Paint.Align.LEFT, 1f);
        p.setColor(0xffe1e1e1); p.setStrokeWidth(2); c.drawLine(334, axis, 1197, axis, p);

        // 最高月气泡
        Calendar now = Calendar.getInstance(); now.add(Calendar.YEAR, s.yearOffset);
        float bx = x0 + pitch * maxI;
        float bw = 331, btop = top + 226;
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(BLUE);
        c.drawRoundRect(bx - bw / 2f, btop, bx + bw / 2f, btop + 140, 26, 26, p);
        ph.reset();
        ph.moveTo(bx - 22, btop + 140); ph.lineTo(bx, btop + 175); ph.lineTo(bx + 22, btop + 140);
        ph.close(); c.drawPath(ph, p);
        p.setColor(0xffdbe9ff); p.setStrokeWidth(3);
        c.drawLine(bx, btop + 175, bx, axis - (float) (mv[maxI] / mx * plotH) - 6, p);
        txt(c, now.get(Calendar.YEAR) + "年" + (maxI + 1) + "月 最多", bx, btop + 56, 40,
                0xffffffff, false, Paint.Align.CENTER, 1f);
        txt(c, money(mv[maxI]) + "  ▶", bx, btop + 110, 36, 0xffffffff, true,
                Paint.Align.CENTER, 1f);
        // 气泡里的金额就是最高月的值，点它等于改那个月的数字
        hot("yi.m:" + maxI, bx - bw / 2f, btop, bx + bw / 2f, btop + 180);

        txt(c, "— 月收入均值", 668f, top + 1480, 34, 0xff8b929b,
                false, Paint.Align.CENTER, 1f);

        // ── 收入排行 ─────────────────────────────────────────────────────
        float ry = top + 1614;
        round(c, 41, ry, 1237, ry + 1020, 40, CARD);
        cardTitle(c, "bar", "收入排行", 87, ry + 81, 54);
        String[] nm = {"转账-转转循环科技有限公司", "转账-染", "转账-转转循环科技有限公司"};
        String[] ct = {"转账红包", "转账红包", "转账红包"};
        String[] tm = {"06-27 13:20", "08-13 19:44", "06-27 13:29"};
        double[] am = {d(s, "yi.r.0", 1452.00), d(s, "yi.r.1", 1400.00), d(s, "yi.r.2", 214.00)};
        float y = ry + 161;
        for (int i = 0; i < 3; i++){
            round(c, 82, y, 1197, y + 217, 26, GRAYTILE);
            txt(c, String.valueOf(i + 1), 132, y + 113, 44, 0xff3a3f46, false, Paint.Align.CENTER, 1f);
            txt(c, nm[i], 189, y + 85, 44, INK, false, Paint.Align.LEFT, 0.88f);
            txt(c, ct[i], 189, y + 154, 36, GREY, false, Paint.Align.LEFT, 1f);
            txt(c, money(am[i]), 1155, y + 90, 40, INK, true, Paint.Align.RIGHT, 1f);
            txt(c, tm[i], 1155, y + 150, 32, GREY, false, Paint.Align.RIGHT, 1f);
            hot("yi.r:" + i, 82, y, 1197, y + 217);
            y += 246;
        }
        txt(c, "展开全部", 610, ry + 946, 36, 0xff8b929b, false, Paint.Align.CENTER, 1f);
        chevron(c, 715, ry + 934, 0xff8b929b);
        hot("yi.rank.expand", 400, ry + 910, 880, ry + 990);

        // ── 更多分析 ─────────────────────────────────────────────────────
        moreBannerI(c, ry + 1065);
        footer(c, ry + 1293);
    }

    /** 收入年度的“更多分析”条（右侧金币为参考图抠图）。 */
    private void moreBannerI(Canvas c, float top){
        float l = 41, rr = 1237, h = 170;
        p.setShader(new LinearGradient(l, top, rr, top + h, 0xfffff8f5, 0xfffff3e6,
                Shader.TileMode.CLAMP));
        c.drawRoundRect(l, top, rr, top + h, 24, 24, p);
        p.setShader(null);
        Bitmap bm = art.get("moreIn");
        if (bm != null){
            r.set(rr - 330, top, rr, top + h);
            ph.reset(); ph.addRoundRect(r, 24, 24, Path.Direction.CW);
            c.save(); c.clipPath(ph); c.drawBitmap(bm, null, r, p); c.restore();
        }
        txt(c, "更多分析", l + 60, top + 92, 56, INK, true, Paint.Align.LEFT, 1f);
        txt(c, "理财分析", l + 298, top + 90, 36, 0xff8b929b, false, Paint.Align.LEFT, 1f);
        hot("more", l, top, rr, top + h);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  底层绘图工具
    // ══════════════════════════════════════════════════════════════════════

    private void txt(Canvas c, String v, float x, float base, float size, int color,
                     boolean bold, Paint.Align align, float sx){
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        p.setTextAlign(align);
        if (sx == 1f){
            c.drawText(v, x, base, p);
        } else {
            c.save();
            c.scale(sx, 1f);
            c.drawText(v, x / sx, base, p);
            c.restore();
        }
    }

    private void round(Canvas c, float l, float t, float rr, float b, float radius, int color){
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawRoundRect(l, t, rr, b, radius, radius, p);
    }

    /** 量一段文字按当前字号、横向压缩后的宽度（和 {@link #txt} 的排版一致）。 */
    private float tw(String v, float size, float sx){
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(size);
        p.setTextScaleX(1f);
        return p.measureText(v) * sx;
    }
    /** 城市名按参考图折成两行，放不下的用省略号。 */
    private String[] wrapCity(String v, int per){
        String name = v == null ? "" : v;
        if (name.length() <= per) return new String[]{name, ""};
        String first = name.substring(0, per);
        String rest = name.substring(per);
        String second = rest.length() > per ? rest.substring(0, per) + "…" : rest;
        return new String[]{first, second};
    }

    private void strokeR(Canvas c, float l, float t, float rr, float b, float radius,
                         int color, float w){
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setColor(color);
        c.drawRoundRect(l, t, rr, b, radius, radius, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void disc(Canvas c, float cx, float cy, float radius, int color){
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawCircle(cx, cy, radius, p);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2, int color, float w){
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        c.drawLine(x1, y1, x2, y2, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void dashLine(Canvas c, float x1, float y1, float x2, float y2){
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setPathEffect(new DashPathEffect(new float[]{18f, 14f}, 0f));
        c.drawLine(x1, y1, x2, y2, p);
        p.setPathEffect(null);
        p.setStyle(Paint.Style.FILL);
    }

    /** 圆角的下箭头（下拉）。 */
    private void caret(Canvas c, float x, float y, float half, int color){
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        ph.reset();
        ph.moveTo(x - half, y - half / 2f);
        ph.lineTo(x + half, y - half / 2f);
        ph.lineTo(x, y + half);
        ph.close();
        c.drawPath(ph, p);
    }

    /** 向右的箭头。 */
    private void chevron(Canvas c, float x, float y, int color){
        line(c, x - 9, y - 13, x + 3, y, color, 4);
        line(c, x + 3, y, x - 9, y + 13, color, 4);
    }

    /** 向上的箭头（收起）。 */
    private void chevronUp(Canvas c, float x, float y, int color){
        line(c, x - 9, y + 6, x + 3, y - 7, color, 4);
        line(c, x + 3, y - 7, x + 15, y + 6, color, 4);
    }

    /** 小三角。right=true 指右，false 指左。 */
    private void tri(Canvas c, float cx, float cy, float half, int color, boolean right){
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        ph.reset();
        if (right){
            ph.moveTo(cx - half / 2f, cy - half);
            ph.lineTo(cx - half / 2f, cy + half);
            ph.lineTo(cx + half / 2f, cy);
        } else {
            ph.moveTo(cx + half / 2f, cy - half);
            ph.lineTo(cx + half / 2f, cy + half);
            ph.lineTo(cx - half / 2f, cy);
        }
        ph.close();
        c.drawPath(ph, p);
    }

    /** 开关：轨道从 (x,y) 起，宽 110 高 58。 */
    private void toggleSwitch(Canvas c, float x, float y, boolean on){
        float w = 94, h = 40, r = h / 2f;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(on ? BLUE : 0xffd6dbe2);
        c.drawRoundRect(x, y, x + w, y + h, r, r, p);
        p.setColor(Color.WHITE);
        c.drawCircle(on ? x + w - r : x + r, y + r, r - 6, p);
    }

    /** 环形图：底环 + 各分片弧（角度由数据自动计算）。 */
    private void donut(Canvas c, float cx, float cy, float ro, float ri, List<Slice> sl){
        float mid = (ro + ri) / 2f, wid = ro - ri;
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.BUTT);
        // 外圈浅蓝光晕
        p.setStrokeWidth(ro * 0.121f);
        p.setColor(0xff9abcea);
        c.drawCircle(cx, cy, ro + ro * 0.106f, p);
        // 光晕与环之间的白缝
        p.setStrokeWidth(ro * 0.043f);
        p.setColor(Color.WHITE);
        c.drawCircle(cx, cy, ro + ro * 0.021f, p);
        // 底环
        p.setStrokeWidth(wid);
        p.setColor(0xffdbe8fb);
        c.drawCircle(cx, cy, mid, p);
        for (Slice z : sl){
            p.setColor(z.color);
            r.set(cx - mid, cy - mid, cx + mid, cy + mid);
            c.drawArc(r, z.a0, z.sweep, false, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    /** 环形图引线标签：单分片走“下方引出右折”，多分片按角度分左右两侧。 */
    private void leaders(Canvas c, float cx, float cy, float ro, List<Slice> sl,
                         float centerY, float extraY){
        int n = sl.size();
        if (n == 0) return;
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        p.setColor(0xffc3c8d2);
        if (n == 1){
            Slice z = sl.get(0);
            float y = cy + ro + 55;
            c.drawLine(cx, cy + ro - 8, cx, y, p);
            c.drawLine(cx, y, 1155, y, p);
            p.setStyle(Paint.Style.FILL);
            txt(c, z.name + " " + pct(z.pct), 1153, y - 14, 34, 0xff3a3f46,
                    false, Paint.Align.RIGHT, 1f);
            return;
        }
        String[] lab = new String[n];
        float[] ly = new float[n];
        boolean[] right = new boolean[n];
        for (int i = 0; i < n; i++){
            Slice z = sl.get(i);
            double mid = Math.toRadians(z.a0 + z.sweep / 2.0);
            right[i] = Math.cos(mid) >= 0;
            ly[i] = cy + (float) (Math.sin(mid) * (ro + 46));
            lab[i] = z.name + " " + pct(z.pct);
        }
        for (int pass = 0; pass < 5; pass++){
            for (int i = 0; i < n; i++){
                for (int j = i + 1; j < n; j++){
                    if (right[i] != right[j]) continue;
                    float d = ly[j] - ly[i];
                    if (Math.abs(d) < 54){
                        float push = (54 - Math.abs(d)) / 2f + 1;
                        ly[i] -= (d >= 0 ? push : -push);
                        ly[j] += (d >= 0 ? push : -push);
                    }
                }
            }
        }
        for (int i = 0; i < n; i++){
            Slice z = sl.get(i);
            double mid = Math.toRadians(z.a0 + z.sweep / 2.0);
            float ex = cx + (float) Math.cos(mid) * (ro + 6);
            float ey = cy + (float) Math.sin(mid) * (ro + 6);
            float bx = right[i] ? cx + ro + 86 : cx - ro - 86;
            p.setColor(0xffc3c8d2);
            c.drawLine(ex, ey, bx, ly[i], p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(z.color);
            c.drawCircle(ex, ey, 6, p);
            txt(c, lab[i], right[i] ? bx + 14 : bx - 14, ly[i] + 12, 34, 0xff3a3f46,
                    false, right[i] ? Paint.Align.LEFT : Paint.Align.RIGHT, 1f);
            p.setStyle(Paint.Style.STROKE);
        }
        p.setStyle(Paint.Style.FILL);
    }

    /** 卡片标题：先画 20×20 图标，再写文字。 */
    private void cardTitle(Canvas c, String kind, String text, float x, float base, float size){
        float s = size * 0.86f, top = base - s;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(INK);
        if (kind.equals("cal")){
            c.drawRoundRect(x, top, x + s, base, s * 0.16f, s * 0.16f, p);
            p.setColor(Color.WHITE);
            c.drawRect(x + s * 0.14f, top + s * 0.30f, x + s * 0.86f, top + s * 0.44f, p);
            p.setColor(BLUE);
            c.drawRoundRect(x + s * 0.18f, top - s * 0.20f, x + s * 0.34f, top + s * 0.10f, 2, 2, p);
            c.drawRoundRect(x + s * 0.66f, top - s * 0.20f, x + s * 0.82f, top + s * 0.10f, 2, 2, p);
        } else if (kind.equals("bar")){
            c.drawRoundRect(x, base - s * 0.42f, x + s * 0.26f, base, 3, 3, p);
            c.drawRoundRect(x + s * 0.37f, base - s * 0.72f, x + s * 0.63f, base, 3, 3, p);
            c.drawRoundRect(x + s * 0.74f, base - s, x + s, base, 3, 3, p);
        } else if (kind.equals("pie")){
            c.drawCircle(x + s / 2f, base - s / 2f, s / 2f, p);
            p.setColor(Color.WHITE);
            ph.reset();
            ph.moveTo(x + s / 2f, base - s / 2f);
            ph.lineTo(x + s, base - s / 2f);
            ph.lineTo(x + s / 2f, base - s / 2f);
            ph.close();
            c.drawCircle(x + s / 2f, base - s / 2f, s * 0.30f, p);
        } else if (kind.equals("card")){
            c.drawRoundRect(x, top + s * 0.14f, x + s, base, s * 0.14f, s * 0.14f, p);
            p.setColor(Color.WHITE);
            c.drawRect(x, top + s * 0.36f, x + s, top + s * 0.50f, p);
        } else if (kind.equals("yuan")){
            c.drawCircle(x + s / 2f, base - s / 2f, s / 2f, p);
            txt(c, "\u00a5", x + s / 2f, base - s * 0.22f, s * 0.72f, Color.WHITE, true,
                    Paint.Align.CENTER, 1f);
        } else if (kind.equals("map")){
            ph.reset();
            ph.moveTo(x, base - s * 0.72f);
            ph.lineTo(x + s * 0.30f, base - s);
            ph.lineTo(x + s * 0.66f, base - s * 0.70f);
            ph.lineTo(x + s, base - s * 0.92f);
            ph.lineTo(x + s, base - s * 0.24f);
            ph.lineTo(x + s * 0.66f, base - s * 0.54f);
            ph.lineTo(x + s * 0.30f, base - s * 0.20f);
            ph.lineTo(x, base - s * 0.44f);
            ph.close();
            c.drawPath(ph, p);
        }
        txt(c, text, x + s + 16, base, size, INK, true, Paint.Align.LEFT, 1f);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  数值格式化（自动计算用）
    // ══════════════════════════════════════════════════════════════════════

    private String money(double v){ return "\u00a5" + String.format(Locale.US, "%.2f", v); }
    private String trim2(double v){ return String.format(Locale.US, "%.2f", v); }
    private String fmtInt(double v){ return String.format(Locale.US, "%.0f", v); }

    private String pct(float v){
        float r = Math.round(v * 10f) / 10f;
        if (Math.abs(r - Math.round(r)) < 0.05f){
            return String.format(Locale.US, "%d%%", Math.round(r));
        }
        return String.format(Locale.US, "%.1f%%", r);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  命中测试
    // ══════════════════════════════════════════════════════════════════════

    /** 顶栏热区用屏幕坐标，内容热区用“内容坐标 + NAV_H - scroll”。 */
    public String hitTest(State s, float x, float y){
        for (int i = hots.size() - 1; i >= 0; i--){
            Hot h = hots.get(i);
            float t, b;
            if (h.b <= NAV_H){ t = h.t; b = h.b; }
            else { t = h.t - s.scroll; b = h.b - s.scroll; }
            if (x >= h.l && x <= h.r && y >= t && y <= b) return h.id;
        }
        return "noop";
    }
}
