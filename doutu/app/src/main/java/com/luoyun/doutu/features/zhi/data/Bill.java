package com.luoyun.doutu.features.zhi.data;

import android.graphics.Bitmap;

/**
 * One row of the 账单 page and everything its 账单详情 page shows.
 *
 * <p>Every field is the page's own, and every one the detail page shows can be typed over: the
 * merchant and its mark, the time, the amount, the description and the two order numbers. The
 * values are made up when the list is generated — {@link BillStore} — and nothing here is a real
 * order, a real merchant or a real payment.
 */
public final class Bill {
    /** The 交易名称 the row is headed with. */
    public String merchant;
    /** The 分类 under it, e.g. 日用百货. */
    public String category;
    /** The one or two characters the mark carries when the user has not picked a picture. */
    public String mark;
    public int markColor;
    public int markInk;
    /** The picture the user picked for the mark, or null for the drawn one. */
    public Bitmap icon;

    /** Which of the page's chips this row answers to: 支出, 转账, 退款 or 订单. */
    public String kind;
    /** When it was paid, in milliseconds. */
    public long time;
    /** What moved: negative for money out, positive for money in. */
    public double amount;
    /** 免密支付 rows carry the line the reference prints under the amount. */
    public String status;

    /** The 账单详情 card's own lines. */
    public String payMethod;
    public String goods;
    public String reward;
    public String payee;
    public String orderNo;
    public String merchantOrderNo;

    /** The 账单管理 card's own state. */
    public String billCategory;
    public String tag = "";
    public String note = "";
    public boolean counted = true;

    public boolean income() {
        return amount > 0;
    }
}
