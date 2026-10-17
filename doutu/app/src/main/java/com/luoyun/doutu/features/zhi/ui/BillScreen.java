package com.luoyun.doutu.features.zhi.ui;
import android.content.res.Resources;
import com.luoyun.doutu.R;
import android.graphics.*;
import com.luoyun.doutu.features.zhi.data.Bill;
import com.luoyun.doutu.features.zhi.data.BillStore;
import java.text.SimpleDateFormat;
import java.util.*;

/** The small-Zhi bill list. All financial values are local, generated demo data. */
public final class BillScreen {
    public static final float W=1280f, H=2816f;
    private static final float HEAD_H=462, ROW_H=348, CARD_L=36, CARD_R=1244,
        CARD_RADIUS=52, TEXT_X=224, VALUE_RIGHT=1200, MARK_CX=130, MARK_R=52;
    private static final int PAGE_BG=0xfff5f5f5, CARD=Color.WHITE, INK=0xff333333,
        GREY=0xffaaaaaa, BLUE=0xff1677ff, DIVIDER=0xfffafafa;
    private static final String[] CHIPS={"全部","支出","转账","退款","订单"};
    public static final class State { public float scroll; public String kind="全部", query=""; }
    private final Paint paint=new Paint(3);
    private final RectF r=new RectF();
    private final Path path=new Path();
    private final SimpleDateFormat clock=new SimpleDateFormat("HH:mm",Locale.US);
    private final SimpleDateFormat date=new SimpleDateFormat("MM-dd HH:mm",Locale.US);
    private List<BillStore.Month> laid=new ArrayList<>();
    private float[] tops=new float[0];
    private float contentH;
    private Bitmap savingArt, backArt, searchArt, moreArt;
    /** 商家的真实品牌图标：按交易名称查，查到就用它替掉字母圆盘。 */
    private final Map<String,Bitmap> logos = new HashMap<>();
    public void load(Resources res) {
        BillMarks.load(res);
        savingArt=BillMarks.art(res,R.drawable.bill_art_saving);
        backArt=BillMarks.art(res,R.drawable.bill_art_back_list);
        searchArt=BillMarks.art(res,R.drawable.bill_art_search);
        moreArt=BillMarks.art(res,R.drawable.bill_art_more);
        loadMerchantLogos(res);
    }
    private void loadMerchantLogos(Resources res) {
        if(!logos.isEmpty()) return;
        putLogo(res,"麦当劳",R.drawable.mch_mcdonalds);
        putLogo(res,"星巴克",R.drawable.mch_starbucks);
        putLogo(res,"肯德基",R.drawable.mch_kfc);
        putLogo(res,"汉堡王",R.drawable.mch_burgerking);
        putLogo(res,"必胜客",R.drawable.mch_pizzahut);
        putLogo(res,"塔斯汀",R.drawable.mch_tasiting);
        putLogo(res,"蜜雪冰城",R.drawable.mch_mixue);
        putLogo(res,"瑞幸咖啡",R.drawable.mch_luckin);
        putLogo(res,"喜茶",R.drawable.mch_heytea);
        putLogo(res,"海底捞",R.drawable.mch_haidilao);
        putLogo(res,"永辉超市",R.drawable.mch_yonghui);
        putLogo(res,"盒马鲜生",R.drawable.mch_hema);
        putLogo(res,"京东到家",R.drawable.mch_jddj);
        putLogo(res,"美团",R.drawable.mch_meituan);
        putLogo(res,"美团外卖",R.drawable.mch_meituan);
        putLogo(res,"饿了么",R.drawable.mch_eleme);
        putLogo(res,"淘宝",R.drawable.mch_taobao);
        putLogo(res,"天猫超市",R.drawable.mch_tmall);
        putLogo(res,"拼多多",R.drawable.mch_pdd);
        putLogo(res,"得物",R.drawable.mch_dewu);
        putLogo(res,"苏宁易购",R.drawable.mch_suning);
        putLogo(res,"华为商城",R.drawable.mch_huawei);
        putLogo(res,"小米商城",R.drawable.mch_xiaomi);
        putLogo(res,"苹果商店",R.drawable.mch_apple);
        putLogo(res,"沃尔玛",R.drawable.mch_walmart);
        putLogo(res,"滴滴出行",R.drawable.mch_didi);
        putLogo(res,"高德打车",R.drawable.mch_amap);
        putLogo(res,"飞猪旅行",R.drawable.mch_fliggy);
        putLogo(res,"中国铁路",R.drawable.mch_rail);
        putLogo(res,"中国联通",R.drawable.mch_cunicom);
        putLogo(res,"中国电信",R.drawable.mch_ctelecom);
        putLogo(res,"腾讯视频",R.drawable.mch_tencentvideo);
        putLogo(res,"爱奇艺",R.drawable.mch_iqiyi);
        putLogo(res,"优酷视频",R.drawable.mch_youku);
        putLogo(res,"芒果TV",R.drawable.mch_mgtv);
        putLogo(res,"哔哩哔哩",R.drawable.mch_bilibili);
        putLogo(res,"网易云音乐",R.drawable.mch_netease);
        putLogo(res,"QQ音乐",R.drawable.mch_qqmusic);
        putLogo(res,"Steam",R.drawable.mch_steam);
        putLogo(res,"菜鸟驿站",R.drawable.mch_cainiao);
        putLogo(res,"百度网盘",R.drawable.mch_baidu);
        putLogo(res,"信用卡还款",R.drawable.mch_credit);
        putLogo(res,"京东健康",R.drawable.mch_jd);
        putLogo(res,"大润发",R.drawable.mch_rtmart);
        putLogo(res,"7-ELEVEN",R.drawable.mch_seven);
        putLogo(res,"哈啰单车",R.drawable.mch_hellobike);
        putLogo(res,"携程旅行",R.drawable.mch_ctrip);
        putLogo(res,"腾讯云",R.drawable.mch_qcloud);
        putLogo(res,"叮当快药",R.drawable.mch_dingdang);
        putLogo(res,"中国邮政",R.drawable.mch_chinapost);
        putLogo(res,"屈臣氏",R.drawable.mch_watsons);
        putLogo(res,"顺丰速运",R.drawable.mch_sf);
        putLogo(res,"顺丰同城急送",R.drawable.mch_sf);
        putLogo(res,"美团单车",R.drawable.mch_meituan);
        putLogo(res,"京东",R.drawable.mch_jd);
        putLogo(res,"王者荣耀",R.drawable.mch_hok);
    }
    private void putLogo(Resources res,String name,int id) {
        Bitmap bm=BitmapFactory.decodeResource(res,id);
        if(bm!=null) logos.put(name,bm);
    }
    private boolean current(BillStore.Month m) {
        Calendar now=Calendar.getInstance();
        return m.year==now.get(Calendar.YEAR) && m.month==now.get(Calendar.MONTH)+1;
    }
    private float header(BillStore.Month m) { return current(m)?480:340; }
    private void layout(State state) {
        laid=new ArrayList<>();
        for(BillStore.Month month:BillStore.months(state.kind)) {
            BillStore.Month copy=new BillStore.Month(month.year,month.month);
            for(Bill b:month.bills) {
                String hay=(b.merchant+" "+b.goods+" "+b.payee+" "+b.orderNo+" "+b.merchantOrderNo).toLowerCase(Locale.ROOT);
                if(state.query.isEmpty() || hay.contains(state.query.toLowerCase(Locale.ROOT))) copy.bills.add(b);
            }
            if(!copy.bills.isEmpty()) laid.add(copy);
        }
        // 账单被清空之后，本月这张卡片（连同上面的“收支分析”入口）还是要留着
        boolean hasCurrent=false;
        for(BillStore.Month m:laid) if(current(m)) hasCurrent=true;
        if(!hasCurrent) {
            Calendar now=Calendar.getInstance();
            laid.add(0,new BillStore.Month(now.get(Calendar.YEAR),now.get(Calendar.MONTH)+1));
        }
        tops=new float[laid.size()]; float top=16;
        for(int i=0;i<laid.size();i++) { tops[i]=top; top+=header(laid.get(i))+laid.get(i).bills.size()*ROW_H+32+40; }
        contentH=top;
    }
    public float maxScroll(State state) { layout(state); return Math.max(0,contentH-(H-HEAD_H)+70); }
    public void draw(Canvas c,State state) {
        c.drawColor(PAGE_BG); layout(state);
        c.save(); c.clipRect(0,HEAD_H,W,H-36); c.translate(0,HEAD_H-state.scroll);
        for(int i=0;i<laid.size();i++) drawMonth(c,laid.get(i),tops[i]);
        int rows=0; for(BillStore.Month m:laid) rows+=m.bills.size();
        if(rows==0 && !laid.isEmpty())
            text(c,"没有符合条件的账单",W/2,tops[0]+header(laid.get(0))+120,44,GREY,false,Paint.Align.CENTER);
        c.restore(); drawBar(c,state);
    }
    private void drawMonth(Canvas c,BillStore.Month month,float top) {
        float h=header(month);
        round(c,CARD_L,top,CARD_R,top+h+ROW_H*month.bills.size()+32,CARD_RADIUS,CARD);
        text(c,""+month.month,86,top+142,106,INK,true);
        float end=86+width(""+month.month,106);
        text(c,"月",end+5,top+145,40,INK,false); caret(c,end+84,top+126,15,INK);
        text(c,"支出",96,top+223,39,GREY,false);
        text(c,"收入",524,top+223,39,GREY,false);
        // 秒变富豪之后金额会很长，放不下就自动缩一点，别顶到右边的徽标
        amountText(c,"¥ "+money(month.paid()),96,top+292,50,505,INK);
        amountText(c,"¥ "+money(month.earned()),524,top+292,50,742,INK);
        float ay=current(month)?top+340:top+198;
        if(current(month)) {
            paint.setShader(new LinearGradient(0,top+320,W,top+470,Color.WHITE,0xffeaf6ff,Shader.TileMode.CLAMP));
            c.drawRect(CARD_L,top+320,CARD_R,top+478,paint); paint.setShader(null);
            text(c,"本月已省",96,top+410,42,INK,false);
            text(c,"0.00元",285,top+410,42,0xffff6666,true); chevron(c,453,top+393,GREY);
            c.drawBitmap(savingArt,750,top+138,paint);
            text(c,Math.round(BillStore.savedThisYear())+"笔",892,top+247,35,0xff385d7c,false);
        }
        round(c,950,ay,1200,ay+108,54,0xff168bff);
        text(c,"收支分析",1075,ay+71,39,Color.WHITE,false,Paint.Align.CENTER);
        for(int i=0;i<month.bills.size();i++) drawRow(c,month.bills.get(i),top+h+i*ROW_H,i==month.bills.size()-1);
    }
    private void drawRow(Canvas c,Bill b,float top,boolean last) {
        // 只用真实品牌图标，不再画“纯色底 + 中间文字”的那种
        Bitmap logo=logos.get(b.merchant);
        if(logo!=null) drawLogo(c,logo,top);
        else {
            Bitmap icon=BillMarks.image(b);
            if(icon!=null) { r.set(MARK_CX-MARK_R,top+48,MARK_CX+MARK_R,top+48+2*MARK_R); c.drawBitmap(icon,null,r,paint); }
        }
        String amount=(b.income()?"+":"-")+plain(Math.abs(b.amount));
        float aw=width(amount,53);
        textEllipsis(c,b.merchant,TEXT_X,top+100,47,INK,false,VALUE_RIGHT-aw-40);
        text(c,b.category,TEXT_X,top+185,39,GREY,false);
        text(c,time(b.time),TEXT_X,top+264,37,GREY,false);
        text(c,amount,VALUE_RIGHT,top+100,53,b.income()?0xffff5364:INK,true,Paint.Align.RIGHT);
        if(!b.status.isEmpty()) text(c,b.status,VALUE_RIGHT,top+185,38,GREY,false,Paint.Align.RIGHT);
        if(!last) line(c,TEXT_X,top+ROW_H,VALUE_RIGHT,top+ROW_H,DIVIDER,2);
    }

    /** 品牌真图标：白底圆角方块，图等比缩放居中。 */
    private void drawLogo(Canvas c,Bitmap bm,float top) {
        float l=MARK_CX-MARK_R, t=top+48, rr=MARK_CX+MARK_R, bb=t+2*MARK_R;
        round(c,l,t,rr,bb,26,Color.WHITE);
        float pad=9, availW=(rr-l)-pad*2, availH=(bb-t)-pad*2;
        float s=Math.min(availW/bm.getWidth(), availH/bm.getHeight());
        float w=bm.getWidth()*s, h=bm.getHeight()*s;
        float x=l+pad+(availW-w)/2f, y=t+pad+(availH-h)/2f;
        paint.setShader(null);
        r.set(x,y,x+w,y+h);
        c.drawBitmap(bm,null,r,paint);
    }
    private void drawBar(Canvas c,State s) {
        round(c,0,0,W,HEAD_H,0,PAGE_BG);
        c.drawBitmap(backArt,38,186,paint);
        round(c,140,166,1098,278,56,CARD);
        c.drawBitmap(searchArt,168,192,paint);
        textEllipsis(c,s.query.isEmpty()?"搜索交易记录":s.query,244,242,47,0xffb0b0b0,false,890);
        line(c,914,184,914,258,0xffeeeeee,2); text(c,"搜索",953,242,43,0xffaaaaaa,false);
        c.drawBitmap(moreArt,1150,201,paint);
        for(int i=0;i<CHIPS.length;i++) {
            float left=40+i*207;
            round(c,left,333,left+179,435,51,Color.WHITE);
            text(c,CHIPS[i],left+89,403,45,s.kind.equals(CHIPS[i])?BLUE:0xff999999,s.kind.equals(CHIPS[i]),Paint.Align.CENTER);
        }
        text(c,"筛选",1088,403,43,0xff666666,false); caret(c,1220,383,17,INK);
    }
    public String hitTest(State s,float x,float y) {
        if(y<HEAD_H) {
            if(y<310) return x<125?"back":x<1120?"search":"more";
            for(int i=0;i<CHIPS.length;i++) if(x>=40+i*207 && x<=219+i*207) return "chip:"+CHIPS[i];
            return "filter";
        }
        layout(s); float cy=y-HEAD_H+s.scroll;
        for(int i=0;i<laid.size();i++) {
            float local=cy-tops[i], h=header(laid.get(i));
            if(local<0) continue;
            if(local<h) {
                float ay=current(laid.get(i))?340:198;
                if(local>=ay && local<=ay+108 && x>=950 && x<=1200) return "analytics";
                return "toast:"+laid.get(i).month+"月支出 ¥"+money(laid.get(i).paid())+" · 收入 ¥"+money(laid.get(i).earned());
            }
            int row=(int)((local-h)/ROW_H);
            if(row<laid.get(i).bills.size()) return "bill:"+i+":"+row;
        }
        return "noop";
    }
    public Bill billAt(int m,int row) { return m>=0 && m<laid.size() && row>=0 && row<laid.get(m).bills.size()?laid.get(m).bills.get(row):null; }
    public BillStore.Month monthAt(int m) { return m>=0 && m<laid.size()?laid.get(m):null; }
    private String time(long when) {
        Calendar a=Calendar.getInstance(),b=Calendar.getInstance(); a.setTimeInMillis(when);
        if(a.get(Calendar.YEAR)==b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR)==b.get(Calendar.DAY_OF_YEAR)) return "今天 "+clock.format(new Date(when));
        b.add(Calendar.DAY_OF_MONTH,-1);
        if(a.get(Calendar.YEAR)==b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR)==b.get(Calendar.DAY_OF_YEAR)) return "昨天 "+clock.format(new Date(when));
        return date.format(new Date(when));
    }
    private static String money(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    private static String plain(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    private float width(String value, float size) {
        font(null, size, INK, true, Paint.Align.LEFT);
        return paint.measureText(value) * SX;
    }

    /** The page's own horizontal squeeze, so a line of type lands on the reference's width. */
    private static final float SX = 0.94f;

    private float text(Canvas c, String value, float x, float baseline, float size, int color,
                       boolean bold) {
        return text(c, value, x, baseline, size, color, bold, Paint.Align.LEFT);
    }

    private float text(Canvas c, String value, float x, float baseline, float size, int color,
                       boolean bold, Paint.Align align) {
        font(c, size, color, bold, align);
        c.save();
        c.scale(SX, 1f);
        c.drawText(value, x / SX, baseline, paint);
        c.restore();
        float w = paint.measureText(value) * SX;
        return align == Paint.Align.LEFT ? x + w : x - w;
    }

    /** 金额：整行放不下就一点点缩小字号，直到塞进 {@code maxRight} 为止。 */
    private void amountText(Canvas c, String value, float x, float baseline, float size,
                            float maxRight, int color) {
        float s = size;
        font(c, s, color, true, Paint.Align.LEFT);
        while (s > 26 && x + paint.measureText(value) * SX > maxRight) {
            s -= 2;
            font(c, s, color, true, Paint.Align.LEFT);
        }
        text(c, value, x, baseline, s, color, true);
    }

    /** Writes as much of a line as fits before {@code limit}, ending it with an ellipsis. */
    private void textEllipsis(Canvas c, String value, float x, float baseline, float size,
                              int color, boolean bold, float limit) {
        font(c, size, color, bold, Paint.Align.LEFT);
        if (x + paint.measureText(value) * SX <= limit) {
            text(c, value, x, baseline, size, color, bold);
            return;
        }
        String cut = value;
        while (!cut.isEmpty() && x + paint.measureText(cut + "…") * SX > limit) {
            cut = cut.substring(0, cut.length() - 1);
        }
        text(c, cut + "…", x, baseline, size, color, bold);
    }

    private void font(Canvas c, float size, int color, boolean bold, Paint.Align align) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        paint.setTextAlign(align);
    }

    private void round(Canvas c, float l, float t, float rr, float b, float radius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRoundRect(l, t, rr, b, radius, radius, paint);
    }

    private void strokeRect(Canvas c, float l, float t, float rr, float b, float radius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        paint.setColor(color);
        c.drawRoundRect(l, t, rr, b, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void disc(Canvas c, float cx, float cy, float radius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawCircle(cx, cy, radius, paint);
    }

    /** The back arrow of the bar. */
    private void stroke(Canvas c, float x, float y) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xff23252b);
        c.drawLine(x, y + 32, x + 30, y + 2, paint);
        c.drawLine(x, y + 32, x + 30, y + 62, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void chevron(Canvas c, float x, float y, int color) {
        line(c, x - 9, y - 12, x + 3, y, color, 4);
        line(c, x + 3, y, x - 9, y + 12, color, 4);
    }

    private void caret(Canvas c, float x, float y, float half, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        path.reset();
        path.moveTo(x - half, y - half / 2);
        path.lineTo(x + half, y - half / 2);
        path.lineTo(x, y + half);
        path.close();
        c.drawPath(path, paint);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2, int color, float w) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(w);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color);
        c.drawLine(x1, y1, x2, y2, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
