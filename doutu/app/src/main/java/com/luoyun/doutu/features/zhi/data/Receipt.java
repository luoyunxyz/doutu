package com.luoyun.doutu.features.zhi.data;

import android.graphics.Bitmap;

/**
 * One 收款 record: the row the 收款记录 list draws and everything its 收款详情 page shows.
 *
 * <p>Every figure here is the page's own and every one of them can be typed over from the detail
 * page — the amount, what actually arrived, the payer's name and picture, the 积分 line, the free
 * withdrawal quota and the 交易说明. The 收款笔数 / 收款总额 / 实际到账 lines of a day are added up
 * from the records of that day, so they follow whatever the user does to them.
 *
 * <p>The three 顾客画像 columns (本月消费 / 累计消费 / 平均单价) are counted from the records of the
 * same payer. The user may pin any of them to his own figure instead; a pinned column keeps its
 * value until it is handed back with 恢复自动, which is what the {@code *Override} fields are for
 * (null means "keep counting").
 *
 * <p>Nothing here is a real payment, customer or amount.
 */
public final class Receipt {
    /** Also the key its own avatar file is named after. */
    public long id;
    /** When the money arrived, in milliseconds. */
    public long time;
    /** 收款金额. */
    public double amount;
    /** 实际到账, and the 新增 figure the 提现免费额度 row shows. */
    public double actual;
    /** 提现免费额度 新增; normally the same as {@link #actual}. */
    public double quota;
    /** The payer, masked the way the reference masks him: **峻. */
    public String payer = "**峻";
    /** The 收钱有奖 line. */
    public String prize = "10积分未领取已过期";
    /** The 交易说明 line. */
    public String note = "收钱码收款";
    /** The picture picked for this payer, or null for the grey one cut out of the reference. */
    public Bitmap avatar;

    /** Pinned 本月消费 and 本月笔数; null keeps counting the payer's own records. */
    public Double monthSpend;
    public Integer monthCount;
    /** Pinned 累计消费 and 累计笔数. */
    public Double totalSpend;
    public Integer totalCount;
    /** Pinned 平均单价. */
    public Double avgPrice;

    /** 收款总额 of the record, the way the list prints it: two decimals. */
    public String amountText() {
        return ReceiptStore.money(amount);
    }

    /** The row's own figure, headed with the sign the reference uses. */
    public String incomeText() {
        return "+" + ReceiptStore.money(amount);
    }
}
