package com.luoyun.doutu.features.zhi.ui;

import android.content.res.Resources;
import com.luoyun.doutu.R;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.luoyun.doutu.features.zhi.data.Bill;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Native rendition of the 账单详情 page a row of {@link BillScreen} slides in.
 *
 * <p>Reference artwork is combined with live Canvas content: the cards, merchant mark, amount and
 * the rows under it, and the 账单管理 card with its category, tag, switch and shortcuts. The figures
 * are the {@link Bill} the list handed over, and the ones the user can change — the mark, the
 * merchant, the time, the amount, the description and the two order numbers — each answer a tap
 * with a dialog the activity puts up, so an edit shows here and on the list behind at once.
 *
 * <p>The page, like the list, is a local mock: no account, merchant or order exists.
 */
public final class BillDetailScreen {
    public static final float W = 1280f;
    public static final float H = 2816f;

    private static final float BAR_H = 290f;
    private static final RectF BACK = new RectF(40, 180, 125, 270);
    private static final float CARD_L = 40f;
    private static final float CARD_R = 1240f;
    private static final float CARD_RADIUS = 46f;

    private static final float C1_TOP = 326f;
    private static final float MARK_CY = 140f;
    private static final float MARK_R = 67f;
    private static final float NAME_BASE = 298f;
    private static final float AMOUNT_BASE = 438f;
    private static final float STATUS_BASE = 536f;
    private static final float ROWS_TOP = 610f;
    private static final float ROW_H = 109f;
    private static final float LABEL_X = 82f;
    private static final float VALUE_X = 402f;
    private static final float VALUE_RIGHT = 1195f;
    private String[] LABELS = {
            "支付时间", "付款方式", "商品说明", "支付奖励", "收款方全称", "订单号", "商家订单号"};

    private static final float CARD_GAP = 26f;
    private static final float M_TITLE = 150f;
    private static final float M_ROW = 138f;
    private static final float M_BOOK = 160f;
    private static final float M_RULE = 18f;
    private static final float M_ACTION = 116f;

    private static final int PAGE_BG = 0xfff5f5f5;
    private static final int CARD = 0xffffffff;
    private static final int INK = 0xff303030;
    private static final int GREY = 0xffa5a5a5;
    private static final int BLUE = 0xff1677ff;
    private static final int DIVIDER = 0xfff0f1f5;
    private static final int PILL = 0xffd9f7fd;
    private static final float SX = 1f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final SimpleDateFormat full = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    /** Mutable presentation state owned by the hosting activity. */
    public static final class State {
        public float scroll;
    }

    /** Where this frame put the things a tap has to land on, in content coordinates. */
    private float card1Bottom;
    private float card2Top;
    private float card2Bottom;
    private float contentH;
    private float goodsLines = 1f;

    private Bitmap manageArt, switchArt, backArt, catArt, fishArt;
    public void load(Resources res) {
        manageArt=BillMarks.art(res,R.drawable.bill_art_manage);
        switchArt=BillMarks.art(res,R.drawable.bill_art_switch_on);
        backArt=BillMarks.art(res,R.drawable.bill_art_back_detail);
        catArt=BillMarks.art(res,R.drawable.bill_art_cat_detail);
        fishArt=BillMarks.art(res,R.drawable.bill_art_xianyu_detail);
        BillMarks.load(res);
    }

    public float maxScroll(State s, Bill bill) {
        layout(bill);
        return Math.max(0f, contentH + 40 - H);
    }

    /**
     * Works out how tall the two cards stand this frame. The 商品说明 row takes a second line when
     * the description is longer than the room beside its label, which is what the reference does
     * with the longer ones.
     */
    private void layout(Bill bill) {
        automatic = bill != null && "自动扣款成功".equals(bill.status);
        LABELS = automatic ? new String[]{"支付时间","付款方式","商品说明","支付奖励","订单号","商家订单号"}
            : new String[]{"支付时间","付款方式","商品说明","支付奖励","收款方全称","订单号","商家订单号"};
        goodsLines = bill == null ? 1 : Math.max(1, wrap(bill.goods, 46, VALUE_RIGHT - VALUE_X).size());
        card1Bottom = C1_TOP + rowsStart() + LABELS.length*ROW_H + (goodsLines-1)*64 + (automatic?90:0) + 60;
        card2Top = card1Bottom + CARD_GAP;
        card2Bottom = card2Top + 1152;
        contentH = card2Bottom + 75;
    }
    private boolean automatic;
    private float rowsStart() { return ROWS_TOP + (automatic?170:0); }

    // ------------------------------------------------------------------ drawing

    public void draw(Canvas c, State s, Bill bill) {
        c.drawColor(PAGE_BG);
        if (bill == null) return;
        layout(bill);
        c.save();
        c.translate(0, -s.scroll);
        drawDetailCard(c, bill);
        drawManageCard(c, bill);
        c.restore();
        drawBar(c);
    }

    private void drawBar(Canvas c) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PAGE_BG);
        c.drawRect(0, 0, W, BAR_H, paint);
        c.drawBitmap(backArt,42,180,paint);
        text(c, "账单详情", 134, 244, 62, INK, false, Paint.Align.LEFT);
    }

    private void drawDetailCard(Canvas c, Bill bill) {
        round(c, CARD_L, C1_TOP, CARD_R, card1Bottom, CARD_RADIUS, CARD);

        float markCy = C1_TOP + MARK_CY;
        Bitmap icon = bill.icon != null ? bill.icon : "云猫".equals(bill.mark) ? catArt : "闲鱼".equals(bill.mark) ? fishArt : BillMarks.image(bill);
        if (icon != null) {
            float scale = 2*MARK_R/Math.max(icon.getWidth(),icon.getHeight());
            float iw=icon.getWidth()*scale, ih=icon.getHeight()*scale;
            r.set(W/2-iw/2,markCy-ih/2,W/2+iw/2,markCy+ih/2);
            c.drawBitmap(icon,null,r,paint);
        } else {
            disc(c,W/2,markCy,MARK_R,bill.markColor);
            text(c,bill.mark,W/2,markCy+18,44,bill.markInk,true,Paint.Align.CENTER);
        }
        text(c,bill.payee,W/2,C1_TOP+NAME_BASE,46,INK,false,Paint.Align.CENTER);
        text(c,(bill.income()?"+":"-")+plain(Math.abs(bill.amount)),W/2,C1_TOP+AMOUNT_BASE,104,INK,true,Paint.Align.CENTER);
        text(c,bill.status.isEmpty()?"交易成功":bill.status,W/2,C1_TOP+STATUS_BASE,46,INK,false,Paint.Align.CENTER);
        if(automatic) {
            text(c,"管理免密支付",LABEL_X,C1_TOP+ROWS_TOP+70,44,GREY,false,Paint.Align.LEFT);
            text(c,"闲鱼100元以下免密支付",VALUE_X,C1_TOP+ROWS_TOP+70,44,0xff245b85,false,Paint.Align.LEFT);
            line(c,LABEL_X,C1_TOP+ROWS_TOP+130,VALUE_RIGHT,C1_TOP+ROWS_TOP+130,0xffeeeeee,2);
        }
        String[] values = automatic ? new String[]{full.format(new Date(bill.time)),bill.payMethod,bill.goods,bill.reward,bill.orderNo,bill.merchantOrderNo}
            : new String[]{full.format(new Date(bill.time)),bill.payMethod,bill.goods,bill.reward,bill.payee,bill.orderNo,bill.merchantOrderNo};
        for(int i=0;i<LABELS.length;i++) {
            float top=C1_TOP+rowsStart()+rowOffset(i);
            text(c,LABELS[i],LABEL_X,top+74,44,GREY,false,Paint.Align.LEFT);
            float baseline=top+74;
            if(i==2) {
                for(String line:wrap(values[i],46,VALUE_RIGHT-VALUE_X)) {
                    text(c,line,VALUE_X,baseline,46,INK,false,Paint.Align.LEFT); baseline+=64;
                }
            } else {
                float size=46;
                font(size,INK,false,Paint.Align.LEFT);
                if(paint.measureText(values[i])>VALUE_RIGHT-VALUE_X) size*= (VALUE_RIGHT-VALUE_X)/paint.measureText(values[i]);
                text(c,values[i],VALUE_X,baseline,size,INK,false,Paint.Align.LEFT);
                if(i==1 || i==3) {
                    font(size,INK,false,Paint.Align.LEFT);
                    chevron(c,Math.min(VALUE_RIGHT,VALUE_X+paint.measureText(values[i])+35),top+57,0xffb4b4b4);
                }
            }
            if(automatic && i==1) text(c,"查看扣款顺序",VALUE_X,top+154,44,0xff245b85,false,Paint.Align.LEFT);
        }
    }
    private float rowOffset(int index) {
        return index*ROW_H + (index>=3?(goodsLines-1)*64:0) + (automatic && index>=2?90:0);
    }

    private void drawManageCard(Canvas c, Bill bill) {
        paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
        c.drawBitmap(manageArt,CARD_L,card2Top,paint);
        manageValue(c,bill.billCategory,card2Top+240);
        manageValue(c,bill.tag.isEmpty()?"请选择":bill.tag,card2Top+378);
        manageValue(c,bill.note.isEmpty()?"添加":bill.note,card2Top+809);
        if(bill.counted) c.drawBitmap(switchArt,1096,card2Top+622,paint);
        else switchMark(c,1150,card2Top+661,false);
    }

    private void manageValue(Canvas c,String value,float baseline) {
        // Clear only the value lane, keeping the reference chevron and row label intact.
        paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.FILL);
        c.drawRect(690,baseline-54,1158,baseline+12,paint);
        font(46,GREY,false,Paint.Align.RIGHT);
        String clipped=value;
        while(clipped.length()>0 && paint.measureText(clipped)>440)
            clipped=clipped.substring(0,clipped.length()-1);
        if(!clipped.equals(value)) {
            while(clipped.length()>0 && paint.measureText(clipped+"…")>440)
                clipped=clipped.substring(0,clipped.length()-1);
            clipped+="…";
        }
        text(c,clipped,1144,baseline,46,GREY,false,Paint.Align.RIGHT);
    }

    private void switchMark(Canvas c, float cx, float cy, boolean on) {
        round(c, cx - 56, cy - 30, cx + 56, cy + 30, 30, on ? BLUE : 0xffd7dbe3);
        disc(c, on ? cx + 26 : cx - 26, cy, 24, 0xffffffff);
    }

    private void glyph(Canvas c, float x, float y, boolean receipt) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.5f);
        paint.setColor(0xff245b85);
        if (receipt) {
            c.drawRoundRect(x, y - 22, x + 38, y + 22, 6, 6, paint);
            c.drawLine(x + 8, y - 8, x + 30, y - 8, paint);
            c.drawLine(x + 8, y + 2, x + 30, y + 2, paint);
            c.drawLine(x + 8, y + 12, x + 22, y + 12, paint);
        } else {
            c.drawCircle(x + 19, y, 20, paint);
            c.drawLine(x + 12, y - 2, x + 17, y + 4, paint);
            c.drawLine(x + 17, y + 4, x + 27, y - 8, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    // ------------------------------------------------------------------ hit test

    public String hitTest(State s, Bill bill, float x, float y) {
        layout(bill);
        float cy = y + s.scroll;
        if (y < BAR_H) {
            if (x < 130) return "back";
            return "noop";
        }
        if (bill == null) return "noop";
        if (cy >= C1_TOP && cy <= card1Bottom) {
            float local = cy - C1_TOP;
            if (local < 230) return "icon";
            if (local >= 230 && local <= 335) return "edit:payee";
            if (local >= AMOUNT_BASE - 70 && local <= AMOUNT_BASE + 30) return "edit:amount";
            for (int i = 0; i < LABELS.length; i++) {
                float top = rowsStart() + rowOffset(i);
                float h = ROW_H + (i == 2 ? (goodsLines-1)*64 : 0) + (automatic && i==1?90:0);
                if (local >= top - 12 && local < top + h) {
                    switch (i) {
                        case 0: return "edit:time";
                        case 2: return "edit:goods";
                        case 1: return "edit:payMethod";
                        case 3: return "edit:reward";
                        case 4: return automatic?"edit:orderNo":"edit:payee";
                        case 5: return automatic?"edit:merchantOrderNo":"edit:orderNo";
                        case 6: return "edit:merchantOrderNo";
                        default: return "noop";
                    }
                }
            }
            return "noop";
        }
        if (cy >= card2Top && cy <= card2Bottom) {
            float local = cy - card2Top;
            float y2 = M_TITLE;
            if (local < y2) return "noop";
            if (local < y2 + M_ROW) return "edit:category";
            y2 += M_ROW;
            if (local < y2 + M_ROW) return "edit:tag";
            y2 += M_ROW;
            if (local < y2 + M_BOOK) return "noop";
            y2 += M_BOOK;
            if (local < y2 + M_ROW) return "toggle:counted";
            y2 += M_ROW;
            if (local < y2 + M_ROW) return "edit:note";
            y2 += M_ROW + M_RULE;
            if (local < y2 + M_ACTION) return "toast:" + (x < W / 2 ? "查看往来记录" : "往来流水证明");
            return "toast:" + (x < W / 2 ? "申请电子回单" : "对此订单有疑问");
        }
        return "noop";
    }

    // ------------------------------------------------------------------ text and shapes

    private static String plain(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    /** Breaks a line onto as many lines of the given size as it needs before {@code width}. */
    private java.util.List<String> wrap(String value, float size, float width) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        font(size, INK, false, Paint.Align.LEFT);
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (paint.measureText(line.toString() + ch) * SX > width && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(ch);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private void text(Canvas c, String value, float x, float baseline, float size, int color,
                      boolean bold, Paint.Align align) {
        font(size, color, bold, align);
        c.save();
        c.scale(SX, 1f);
        c.drawText(value, x / SX, baseline, paint);
        c.restore();
    }

    private void font(float size, int color, boolean bold, Paint.Align align) {
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

    private void disc(Canvas c, float cx, float cy, float radius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawCircle(cx, cy, radius, paint);
    }

    private void stroke(Canvas c, float x, float y) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(INK);
        c.drawLine(x, y + 32, x + 30, y + 2, paint);
        c.drawLine(x, y + 32, x + 30, y + 62, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void chevron(Canvas c, float x, float y, int color) {
        line(c, x - 9, y - 12, x + 3, y, color, 4);
        line(c, x + 3, y, x - 9, y + 12, color, 4);
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
