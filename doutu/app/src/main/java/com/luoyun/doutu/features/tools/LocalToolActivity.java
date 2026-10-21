package com.luoyun.doutu.features.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.luoyun.doutu.account.AvatarUpload;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具页的独立本地工具页。每个入口都有本地数据与交互，不调用本软件其它模块。
 * key: chat / wallet / redpacket / transfer / contacts / moments / album / team / video / call / notice / profile
 */
public final class LocalToolActivity extends Activity {
    private static final String PREFS = "doutu_tools_local";
    private String key, title;
    private String chatName = "文件传输助手";
    private LinearLayout content;
    private TextView balanceView;
    private LinearLayout chatList, billList, contactList, momentList, albumGrid, teamList;
    private final List<String> messages = new ArrayList<>();
    private final List<String> bills = new ArrayList<>();
    private final List<String> contacts = new ArrayList<>();
    private final List<String> moments = new ArrayList<>();
    private final List<String> teams = new ArrayList<>();

    public static void open(Activity a, String key, String title) {
        Intent i = new Intent(a, LocalToolActivity.class);
        i.putExtra("key", key);
        i.putExtra("title", title);
        a.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        key = getIntent().getStringExtra("key");
        title = getIntent().getStringExtra("title");
        if (key == null) key = "chat";
        if (title == null) title = "工具";
        String n = getIntent().getStringExtra("name");
        if (n != null && !n.isEmpty()) chatName = n;
        else if ("chat".equals(key) && title != null && !title.isEmpty() && !"工具".equals(title)) chatName = title;
        load();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff5f6fa);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.WHITE);
        Button back = new Button(this);
        back.setText("‹ 返回");
        back.setAllCaps(false);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(88), dp(50)));
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(18);
        tv.setGravity(Gravity.CENTER);
        bar.addView(tv, new LinearLayout.LayoutParams(0, dp(50), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(88), dp(50)));
        if (!("home".equals(key) || "contacts".equals(key) || "moments".equals(key) || "profile".equals(key) || "discover".equals(key) || "chat".equals(key) || "wallet".equals(key) || "transfer".equals(key) || "redpacket".equals(key) || "paysuccess".equals(key) || "pay".equals(key) || "billstats".equals(key) || "service".equals(key) || "favorites".equals(key) || "cards".equals(key) || "stickers".equals(key) || "redpackets".equals(key) || "broadcast".equals(key) || "payee".equals(key) || "walletdetail".equals(key) || "newfriends".equals(key) || "pickchat".equals(key) || "pickgroup".equals(key) || "official".equals(key) || "withdraw".equals(key) || "group".equals(key) || "upload".equals(key))) root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        build();
        if (getIntent().getBooleanExtra("selftest", false)) finish();
    }

    private void build() {
        if ("home".equals(key)) buildHome();
        else if ("chats".equals(key)) buildChats();
        else if ("broadcast".equals(key)) buildBroadcast();
        else if ("banktransfer".equals(key)) buildBankTransfer();
        else if ("profile".equals(key)) buildProfile();
        else if ("chat".equals(key)) buildChat();
        else if ("group".equals(key)) buildGroup();
        else if ("upload".equals(key)) buildUpload();
        else if ("wallet".equals(key)) buildWallet();
        else if ("official".equals(key)) buildOfficial();
        else if ("withdraw".equals(key)) buildWithdraw();
        else if ("pickchat".equals(key)) buildPickContacts(false);
        else if ("pickgroup".equals(key)) buildPickContacts(true);
        else if ("walletdetail".equals(key)) buildWalletDetail();
        else if ("newfriends".equals(key)) buildNewFriends();
        else if ("payee".equals(key)) buildPayee();
        else if ("favorites".equals(key)) buildFavorites();
        else if ("cards".equals(key)) buildCards();
        else if ("stickers".equals(key)) buildStickers();
        else if ("service".equals(key)) buildService();
        else if ("redpacket".equals(key) || "transfer".equals(key)) buildPay();
        else if ("contacts".equals(key)) buildContacts();
        else if ("discover".equals(key)) buildDiscover();
        else if ("moments".equals(key)) buildMoments();
        else if ("album".equals(key)) buildAlbum();
        else if ("team".equals(key)) buildTeam();
        else if ("video".equals(key)) buildVideo();
        else if ("call".equals(key)) buildCall();
        else if ("articles".equals(key)) buildInfo("公众号", new String[]{"本地公众号\n\n这是一篇只保存在本机的示例文章，不联网。", "工具更新说明\n\n工具页全部功能均为本地处理。"});
        else if ("shop".equals(key)) buildShop();
        else if ("mallwallet".equals(key)) buildMallWallet();
        else if ("pay".equals(key)) buildPayPage();
        else if ("billstats".equals(key)) buildBillStats();
        else if ("redpackets".equals(key)) buildRedPackets();
        else if ("paysuccess".equals(key)) buildSuccess();
        else if ("settings".equals(key)) buildSettings();
        else if ("status".equals(key)) buildSwitches("状态与显示", new String[]{"显示未读红点", "在线状态", "悬浮窗快捷入口"});
        else if ("voice".equals(key)) buildVoice();
        else if ("order".equals(key)) buildOrders();
        else if ("assets".equals(key)) buildAssets();
        else if ("comment".equals(key)) buildComment();
        else if ("login".equals(key)) buildLogin();
        else if ("notices".equals(key)) buildNotices();
        else buildGeneric();
    }

    private void buildChat() {
        content.setBackgroundColor(0xffededed);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 22);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(40), dp(43)));
        TextView title = new TextView(this);
        title.setText(chatName);
        title.setTextSize(15);
        title.setTextColor(0xff181818);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        ImageView more = assetIcon("ic_wx_chat_title_more", 22);
        header.addView(more, new LinearLayout.LayoutParams(dp(40), dp(43)));
        content.addView(header);
        View line = new View(this);
        line.setBackgroundColor(0xffd9d9d9);
        content.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        chatList = new LinearLayout(this);
        chatList.setOrientation(LinearLayout.VERTICAL);
        chatList.setPadding(dp(4), dp(6), dp(4), dp(6));
        chatList.setMinimumHeight(dp(380));
        content.addView(chatList);
        for (String m : messages) addBubble(m, true);
        if (messages.isEmpty()) addBubble("你好，这是本地聊天工具，消息只保存在本机。", false);

        View line2 = new View(this);
        line2.setBackgroundColor(0xffd2d2d2);
        content.addView(line2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setBackgroundColor(0xfff7f7f7);
        footer.setPadding(dp(9), 0, dp(8), 0);
        footer.addView(assetIcon("ic_wx_chat_footer_voice", 25), new LinearLayout.LayoutParams(dp(25), dp(25)));
        EditText input = new EditText(this);
        input.setTextSize(14);
        input.setSingleLine(true);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(6));
        input.setBackground(bg);
        input.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        ilp.leftMargin = dp(9);
        ilp.rightMargin = dp(9);
        footer.addView(input, ilp);
        footer.addView(assetIcon("ic_wx_chat_footer_sticker", 25), new LinearLayout.LayoutParams(dp(25), dp(25)));
        LinearLayout rightBox = new LinearLayout(this);
        rightBox.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(50));
        rlp.leftMargin = dp(8);
        ImageView plus = assetIcon("ic_wx_chat_footer_add", 25);
        TextView send = new TextView(this);
        send.setText("发送");
        send.setTextSize(14);
        send.setTextColor(Color.WHITE);
        send.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable sbg = new android.graphics.drawable.GradientDrawable();
        sbg.setColor(0xff07c160);
        sbg.setCornerRadius(dp(4));
        send.setBackground(sbg);
        send.setVisibility(View.GONE);
        rightBox.addView(plus, new LinearLayout.LayoutParams(dp(25), dp(25)));
        rightBox.addView(send, new LinearLayout.LayoutParams(dp(54), dp(29)));
        footer.addView(rightBox, rlp);
        footer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        content.addView(footer);

        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a2) { }
            public void onTextChanged(CharSequence s, int st, int b2, int c) {
                boolean has = s.toString().trim().length() > 0;
                plus.setVisibility(has ? View.GONE : View.VISIBLE);
                send.setVisibility(has ? View.VISIBLE : View.GONE);
            }
            public void afterTextChanged(android.text.Editable s) { }
        });
        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            messages.add(text);
            save();
            addBubble(text, true);
            input.setText("");
        });
    }

    private void addBubble(String text, boolean mine) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(0xff191919);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(mine ? 0xff95ec69 : Color.WHITE);
        bg.setCornerRadius(dp(4));
        tv.setBackground(bg);
        tv.setPadding(dp(10), dp(8), dp(10), dp(8));
        tv.setMaxWidth(dp(250));
        ImageView avatar = new ImageView(this);
        try (java.io.InputStream in = getAssets().open("wx_icons/ic_wx_default_image.png")) {
            avatar.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));
        } catch (Exception ignored) { }
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        row.setPadding(dp(6), dp(6), dp(6), dp(6));
        if (mine) {
            row.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(36), dp(36));
            alp.leftMargin = dp(8);
            row.addView(avatar, alp);
        } else {
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(36), dp(36));
            alp.rightMargin = dp(8);
            row.addView(avatar, alp);
            row.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (chatList != null) chatList.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final String msg = text;
        tv.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setItems(new String[]{"收藏", "复制", "删除"}, (d, w) -> {
                if (w == 0) {
                    List<String> favs = split("favorites");
                    if (!favs.contains(msg)) favs.add(0, msg);
                    saveList("favorites", favs);
                    toast("已收藏");
                } else if (w == 1) {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("msg", msg));
                    toast("已复制");
                } else {
                    messages.remove(msg);
                    save();
                    chatList.removeAllViews();
                    for (String m : messages) addBubble(m, true);
                    toast("已删除");
                }
            }).show();
            return true;
        });
    }

    private void buildWallet() {
        content.setBackgroundColor(Color.WHITE);
        content.setPadding(0, 0, 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(21), dp(21));
        blp.leftMargin = dp(13);
        bar.addView(back, blp);
        TextView title = new TextView(this);
        title.setText("零钱");
        title.setTextSize(16);
        title.setTextColor(0xff2b2b2b);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(34), dp(43)));
        content.addView(bar);

        ImageView diamond = assetIcon("ic_wx_pay_diamond", 64);
        LinearLayout dl = new LinearLayout(this);
        dl.setGravity(Gravity.CENTER);
        dl.setPadding(0, dp(24), 0, dp(10));
        dl.addView(diamond, new LinearLayout.LayoutParams(dp(64), dp(64)));
        content.addView(dl);

        TextView my = new TextView(this);
        my.setText("我的零钱");
        my.setTextSize(15.45f);
        my.setTextColor(Color.BLACK);
        my.setGravity(Gravity.CENTER);
        content.addView(my);

        LinearLayout amountRow = new LinearLayout(this);
        amountRow.setGravity(Gravity.CENTER);
        amountRow.setPadding(0, dp(8), 0, 0);
        TextView yuan = new TextView(this);
        yuan.setText("¥");
        yuan.setTextSize(44);
        yuan.setTextColor(0xff212121);
        amountRow.addView(yuan);
        TextView bal = new TextView(this);
        bal.setText(getBalance());
        bal.setTextSize(44);
        bal.setTextColor(0xff212121);
        amountRow.addView(bal);
        content.addView(amountRow);

        TextView tip = new TextView(this);
        tip.setText("免费开通零钱通，给自己加加薪");
        tip.setTextSize(12.7f);
        tip.setTextColor(0xfffe9e32);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(14), 0, dp(18));
        content.addView(tip);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.VERTICAL);
        btns.setGravity(Gravity.CENTER);
        TextView recharge = new TextView(this);
        recharge.setText("充值");
        recharge.setTextSize(14.5f);
        recharge.setTextColor(Color.WHITE);
        recharge.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g1 = new android.graphics.drawable.GradientDrawable();
        g1.setColor(0xff07c160);
        g1.setCornerRadius(dp(4));
        recharge.setBackground(g1);
        btns.addView(recharge, new LinearLayout.LayoutParams(dp(166), dp(36)));
        TextView withdraw = new TextView(this);
        withdraw.setText("提现");
        withdraw.setTextSize(14.5f);
        withdraw.setTextColor(Color.BLACK);
        withdraw.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g2 = new android.graphics.drawable.GradientDrawable();
        g2.setColor(Color.WHITE);
        g2.setStroke(dp(1), 0xffdddddd);
        g2.setCornerRadius(dp(4));
        withdraw.setBackground(g2);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(dp(166), dp(36));
        wlp.topMargin = dp(14);
        btns.addView(withdraw, wlp);
        content.addView(btns);

        TextView help = new TextView(this);
        help.setText("常见问题");
        help.setTextSize(12);
        help.setTextColor(0xff576b95);
        help.setGravity(Gravity.CENTER);
        help.setPadding(0, dp(20), 0, dp(4));
        content.addView(help);
        TextView foot = new TextView(this);
        foot.setText("本服务由财付通提供");
        foot.setTextSize(10);
        foot.setTextColor(0xff7f7f7f);
        foot.setGravity(Gravity.CENTER);
        content.addView(foot);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(16), dp(16), dp(16));
        billList = list;
        content.addView(list);
        renderBills();

        recharge.setOnClickListener(v -> {
            try {
                double nb = Double.parseDouble(getBalance()) + 100;
                getSharedPreferences(PREFS, 0).edit().putString("balance", String.format("%.2f", nb)).apply();
                bills.add(0, "充值 +¥100.00");
                saveList("bills", bills);
                bal.setText(getBalance());
                renderBills();
            } catch (Exception ignored) { }
        });
        withdraw.setOnClickListener(v -> LocalToolActivity.open(this, "transfer", "提现"));
    }

    private void renderBills() {
        if (billList == null) return;
        billList.removeAllViews();
        if (bills.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("暂无记录，去「红包 / 转账」试试");
            e.setTextColor(0xff8b90a0);
            e.setTextSize(13);
            billList.addView(e);
            return;
        }
        for (String b : bills) {
            TextView tv = new TextView(this);
            tv.setText(b);
            tv.setTextSize(13);
            tv.setTextColor(0xff333333);
            tv.setBackgroundColor(Color.WHITE);
            tv.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            billList.addView(tv, lp);
        }
    }

    private void buildPay() {
        final boolean red = "redpacket".equals(key);
        content.setBackgroundColor(0xffededed);
        content.setPadding(0, 0, 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(21), dp(21));
        blp.leftMargin = dp(13);
        bar.addView(back, blp);
        TextView title = new TextView(this);
        title.setText(red ? "红包" : "转账");
        title.setTextSize(16);
        title.setTextColor(0xff2b2b2b);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(34), dp(43)));
        content.addView(bar);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(this);
        label.setText(red ? "红包金额" : "转账金额");
        label.setTextSize(13);
        label.setTextColor(0xff888888);
        card.addView(label);

        LinearLayout amountRow = new LinearLayout(this);
        amountRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView yuan = new TextView(this);
        yuan.setText("¥");
        yuan.setTextSize(30);
        yuan.setTextColor(0xff191919);
        amountRow.addView(yuan);
        EditText amount = new EditText(this);
        amount.setHint("0.00");
        amount.setTextSize(30);
        amount.setTextColor(0xff191919);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setBackground(null);
        amountRow.addView(amount, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(amountRow);

        View line = new View(this);
        line.setBackgroundColor(0xffe5e5e5);
        card.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        EditText name = new EditText(this);
        name.setHint(red ? "红包祝福语" : "对方姓名");
        name.setTextSize(14);
        name.setBackground(null);
        name.setPadding(0, dp(12), 0, dp(12));
        card.addView(name);

        content.addView(card);

        TextView bal = new TextView(this);
        bal.setText("当前零钱：¥ " + getBalance());
        bal.setTextSize(13);
        bal.setTextColor(0xff888888);
        bal.setPadding(dp(18), dp(12), dp(18), dp(12));
        content.addView(bal);

        TextView ok = new TextView(this);
        ok.setText(red ? "塞钱进红包" : "确认转账");
        ok.setTextSize(16);
        ok.setTextColor(Color.WHITE);
        ok.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0xff07c160);
        g.setCornerRadius(dp(4));
        ok.setBackground(g);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        olp.leftMargin = dp(18);
        olp.rightMargin = dp(18);
        content.addView(ok, olp);

        ok.setOnClickListener(v -> {
            String a2 = amount.getText().toString().trim();
            if (a2.isEmpty()) { toast("请输入金额"); return; }
            try {
                double v2 = Double.parseDouble(a2);
                double b2 = Double.parseDouble(getBalance());
                b2 -= v2;
                getSharedPreferences(PREFS, 0).edit().putString("balance", String.format("%.2f", b2)).apply();
                bills.add(0, (red ? "红包 -¥" : "转账 -¥") + String.format("%.2f", v2) + "  " + name.getText().toString().trim());
                saveList("bills", bills);
                getSharedPreferences(PREFS, 0).edit().putString("last_pay", (red ? "红包 · " : "转账 · ") + name.getText().toString().trim() + "|" + String.format("%.2f", v2)).apply();
                toast(red ? "红包已发出（本地）" : "转账成功（本地）");
                finish();
            } catch (Exception e) { toast("金额格式不正确"); }
        });
    }

    private LinearLayout wechatHeader(String title) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(0xffededed);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(0xff191919);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView h = new TextView(this);
        h.setText(title);
        h.setTextSize(17);
        h.setTextColor(0xff191919);
        h.setGravity(Gravity.CENTER);
        header.addView(h, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView search = new TextView(this);
        search.setText("🔍");
        search.setTextSize(18);
        search.setGravity(Gravity.CENTER);
        header.addView(search, new LinearLayout.LayoutParams(dp(44), dp(48)));
        TextView plus = new TextView(this);
        plus.setText("＋");
        plus.setTextSize(24);
        plus.setTextColor(0xff191919);
        plus.setGravity(Gravity.CENTER);
        header.addView(plus, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return header;
    }

    private LinearLayout wechatRow(String glyph, String color, String label, String key) {
        return wechatRow(glyph, color, label, key, label);
    }

    private LinearLayout wechatRow(String glyph, String color, String label, String key, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.WHITE);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        TextView icon = new TextView(this);
        icon.setText(glyph);
        icon.setTextSize(16);
        icon.setTextColor(Color.WHITE);
        icon.setGravity(Gravity.CENTER);
        icon.setBackgroundColor(Color.parseColor(color.replace("0x", "#")));
        row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(16);
        tv.setTextColor(0xff191919);
        tv.setPadding(dp(12), 0, 0, 0);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setOnClickListener(v -> {
            if ("chat".equals(key)) LocalToolActivity.open(this, "chat", title);
            else LocalToolActivity.open(this, key, title);
        });
        return row;
    }

    private LinearLayout wechatTabBar(String active) {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER);
        tabs.setBackgroundColor(0xfff7f7f7);
        tabs.setPadding(0, dp(4), 0, dp(4));
        String[][] defs = {{"💬", "微信", "home"}, {"👥", "通讯录", "contacts"}, {"🧭", "发现", "discover"}, {"👤", "我", "profile"}};
        for (String[] d : defs) {
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            boolean on = active.equals(d[2]);
            TextView icon = new TextView(this);
            icon.setText(d[0]);
            icon.setTextSize(20);
            icon.setGravity(Gravity.CENTER);
            tab.addView(icon);
            TextView label = new TextView(this);
            label.setText(d[1]);
            label.setTextSize(10);
            label.setTextColor(on ? 0xff07c160 : 0xff191919);
            label.setGravity(Gravity.CENTER);
            tab.addView(label);
            tab.setOnClickListener(v -> {
                if (!on) LocalToolActivity.open(this, d[2], d[1]);
            });
            tabs.addView(tab, new LinearLayout.LayoutParams(0, dp(47), 1f));
        }
        return tabs;
    }

    private void buildContacts() {
        content.setBackgroundColor(0xfff5f5f8);
        content.addView(contactTitle());

        LinearLayout hwrap = new LinearLayout(this);
        hwrap.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        left.addView(contactFeatureRow("ic_wx_contact_new_friend", "新的朋友", "newfriends", "新的朋友"));
        left.addView(contactFeatureRow("ic_wx_contact_group_chat", "群聊", "chats", "群聊"));
        left.addView(contactFeatureRow("ic_wx_contact_label", "标签", "articles", "标签"));
        left.addView(contactFeatureRow("ic_wx_contact_public_number", "公众号", "articles", "公众号"));
        left.addView(contactSection("我的企业及企业联系人", 13, 0xff767676));
        left.addView(contactFeatureRow("ic_wx_contact_publick_enterprise", "企业微信联系人", "articles", "企业微信联系人"));

        if (!contacts.contains("文件传输助手")) contacts.add("文件传输助手");
        if (!contacts.contains("本地好友")) contacts.add("本地好友");
        java.util.TreeMap<String, java.util.List<String>> groups = new java.util.TreeMap<>();
        for (String c : contacts) {
            String letter = pinyinLetter(c);
            groups.computeIfAbsent(letter, k -> new java.util.ArrayList<>()).add(c);
        }
        for (String letter : groups.keySet()) {
            left.addView(contactSection(letter, 11, 0xff888888));
            for (String c : groups.get(letter)) left.addView(contactRow(c));
        }
        left.addView(contactFooter(contacts.size() + "位联系人"));

        hwrap.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView alphabet = new TextView(this);
        StringBuilder sb = new StringBuilder();
        for (char ch = 'A'; ch <= 'Z'; ch++) sb.append(ch).append('\n');
        sb.append('#');
        alphabet.setText(sb.toString());
        alphabet.setTextSize(9);
        alphabet.setTextColor(0xff666666);
        alphabet.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        alphabet.setPadding(0, dp(6), 0, dp(6));
        hwrap.addView(alphabet, new LinearLayout.LayoutParams(dp(20), ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(hwrap);
        content.addView(wechatTabBar("contacts"));
    }

    private String pinyinLetter(String name) {
        if (name == null || name.isEmpty()) return "#";
        char c0 = name.charAt(0);
        if (c0 >= 'A' && c0 <= 'Z') return String.valueOf(c0);
        if (c0 >= 'a' && c0 <= 'z') return String.valueOf(Character.toUpperCase(c0));
        switch (c0) {
            case '文': return "W";
            case '本': return "B";
            case '陈': return "C";
            case '李': return "L";
            case '王': return "W";
            case '张': return "Z";
            case '刘': return "L";
            case '杨': return "Y";
            case '黄': return "H";
            case '赵': return "Z";
            case '周': return "Z";
            case '吴': return "W";
            default: return "#";
        }
    }

    private LinearLayout contactTitle() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(21), dp(21));
        blp.leftMargin = dp(13);
        bar.addView(back, blp);
        TextView title = new TextView(this);
        title.setText("通讯录");
        title.setTextSize(16);
        title.setTextColor(0xff2b2b2b);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        ImageView search = assetIcon("ic_wx_outlined_search", 21);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(21), dp(21));
        slp.rightMargin = dp(15);
        bar.addView(search, slp);
        ImageView add = assetIcon("ic_wx_outlined_add", 21);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(21), dp(21));
        alp.rightMargin = dp(14);
        bar.addView(add, alp);
        return bar;
    }

    private LinearLayout contactFeatureRow(String asset, String label, String key, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.WHITE);
        ImageView icon = assetIcon(asset, 36);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(36), dp(36));
        ilp.leftMargin = dp(14);
        row.addView(icon, ilp);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(15.45f);
        tv.setTextColor(0xff303030);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(14.54f);
        row.addView(tv, tlp);
        row.setOnClickListener(v -> LocalToolActivity.open(this, key, title));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(47)));
        return row;
    }

    private TextView contactSection(String text, float size, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setBackgroundColor(0xffededed);
        tv.setPadding(dp(14.54f), 0, 0, 0);
        tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(29)));
        return tv;
    }

    private LinearLayout contactRow(String name) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.WHITE);
        ImageView av = assetIcon("ic_wx_default_image", 36);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(36.36f), dp(36.36f));
        alp.leftMargin = dp(14);
        row.addView(av, alp);
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(14.54f);
        tv.setTextColor(0xff404040);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(14.54f);
        row.addView(tv, tlp);
        row.setOnClickListener(v -> LocalToolActivity.open(this, "chat", name));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return row;
    }

    private TextView contactFooter(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(0xff717171);
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundColor(Color.WHITE);
        tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(51)));
        return tv;
    }

    private ImageView assetIcon(String name, int sizeDp) {
        ImageView iv = new ImageView(this);
        try (java.io.InputStream in = getAssets().open("wx_icons/" + name + ".png")) {
            iv.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));
        } catch (Exception ignored) { }
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    private void buildDiscover() {
        content.setBackgroundColor(0xffededed);
        content.addView(wechatHeader("发现"));
        int[] gaps = {1, 3, 5, 7, 10};
        String[][] rows = {
                {"pyq", "圈", "0xff07c160", "朋友圈", "moments", "朋友圈"},
                {"sph", "视", "0xffff9f1c", "视频号", "video", "视频号"},
                {"live", "播", "0xffff5a5f", "直播", "video", "直播"},
                {"sao", "扫", "0xff2f7bff", "扫一扫", "pay", "扫一扫"},
                {"yao", "摇", "0xff2f7bff", "摇一摇", "generic", "摇一摇"},
                {"kan", "看", "0xffffb400", "看一看", "articles", "看一看"},
                {"sou", "搜", "0xffff5a5f", "搜一搜", "articles", "搜一搜"},
                {"nearby", "近", "0xff2f7bff", "附近", "generic", "附近"},
                {"shoping", "购", "0xffff5a5f", "购物", "shop", "购物"},
                {"game", "游", "0xff07c160", "游戏", "generic", "游戏"},
                {"xcx", "程", "0xff7c5cf6", "小程序", "generic", "小程序"}
        };
        for (int i = 0; i < rows.length; i++) {
            boolean gap = false;
            for (int g : gaps) if (g == i) gap = true;
            if (gap) {
                View g = new View(this);
                g.setBackgroundColor(0xffededed);
                content.addView(g, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
            }
            content.addView(discoverRow(rows[i][0], rows[i][1], rows[i][2], rows[i][3], rows[i][4], rows[i][5]));
        }
        content.addView(wechatTabBar("discover"));
    }

    private LinearLayout discoverRow(String asset, String glyph, String color, String label, String key, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.WHITE);
        row.setPadding(dp(15), 0, dp(12), 0);
        View icon;
        if (asset != null && !asset.isEmpty()) {
            icon = assetIcon("ic_wx_find_" + asset, 35);
        } else {
            TextView tv = new TextView(this);
            tv.setText(glyph);
            tv.setTextSize(18);
            tv.setTextColor(Color.parseColor(color.replace("0x", "#")));
            tv.setGravity(Gravity.CENTER);
            icon = tv;
        }
        row.addView(icon, new LinearLayout.LayoutParams(dp(35), dp(35)));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(16);
        tv.setTextColor(0xff191919);
        tv.setPadding(dp(15), 0, 0, 0);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if ("pyq".equals(asset) || "sph".equals(asset)) {
            FrameLayout thumb = new FrameLayout(this);
            ImageView th = assetIcon("ic_wx_default_image", 29);
            thumb.addView(th, new FrameLayout.LayoutParams(dp(29), dp(29)));
            View red = new View(this);
            android.graphics.drawable.GradientDrawable rg = new android.graphics.drawable.GradientDrawable();
            rg.setColor(0xffff3b30);
            rg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            red.setBackground(rg);
            FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(dp(7), dp(7), Gravity.TOP | Gravity.END);
            thumb.addView(red, rlp);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(29), dp(29));
            tlp.rightMargin = dp(8);
            row.addView(thumb, tlp);
        }
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(20);
        arrow.setTextColor(0xffc7c7cc);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(22), dp(35)));
        row.setOnClickListener(v -> LocalToolActivity.open(this, key, title));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        return row;
    }

    private void buildMoments() {
        content.setBackgroundColor(0xfff7f7f7);
        content.setPadding(0, 0, 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(43), dp(43)));
        TextView title = new TextView(this);
        title.setText("朋友圈");
        title.setTextSize(16);
        title.setTextColor(0xff191919);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        TextView cam = new TextView(this);
        cam.setText("📷");
        cam.setTextSize(20);
        cam.setGravity(Gravity.CENTER);
        bar.addView(cam, new LinearLayout.LayoutParams(dp(43), dp(43)));
        content.addView(bar);

        LinearLayout cover = new LinearLayout(this);
        cover.setBackgroundColor(0xff3b3b3b);
        cover.setGravity(Gravity.BOTTOM | Gravity.END);
        cover.setPadding(0, 0, dp(14), dp(12));
        cover.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));
        LinearLayout who = new LinearLayout(this);
        who.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText("本地用户");
        name.setTextSize(16);
        name.setTextColor(Color.WHITE);
        who.addView(name);
        ImageView av = assetIcon("ic_wx_default_image", 56);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(56), dp(56));
        alp.leftMargin = dp(10);
        who.addView(av, alp);
        cover.addView(who);
        content.addView(cover);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        if (moments.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有朋友圈内容\n\n点击右上角相机发布一条");
            empty.setTextSize(14);
            empty.setTextColor(0xff999999);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(60), dp(20), dp(20));
            list.addView(empty);
        }
        for (String m : moments) {
            LinearLayout post = new LinearLayout(this);
            post.setBackgroundColor(Color.WHITE);
            post.setPadding(dp(11), dp(11), dp(11), dp(11));
            ImageView pav = assetIcon("ic_wx_default_image", 36);
            post.addView(pav, new LinearLayout.LayoutParams(dp(36), dp(36)));
            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(8), 0, 0, 0);
            TextView pname = new TextView(this);
            pname.setText("本地用户");
            pname.setTextSize(16);
            pname.setTextColor(0xff586b95);
            body.addView(pname);
            TextView text = new TextView(this);
            text.setText(m);
            text.setTextSize(15);
            text.setTextColor(0xff191919);
            text.setPadding(0, dp(5), 0, 0);
            body.addView(text);
            TextView time = new TextView(this);
            time.setText("刚刚");
            time.setTextSize(10);
            time.setTextColor(0xff808080);
            time.setPadding(0, dp(10), 0, 0);
            body.addView(time);

            final String postText = m;
            List<String> likes = split("likes");
            boolean liked = likes.contains(postText);
            LinearLayout actions = new LinearLayout(this);
            actions.setPadding(0, dp(6), 0, 0);
            TextView like = new TextView(this);
            like.setText(liked ? "取消赞" : "赞");
            like.setTextSize(12);
            like.setTextColor(0xff576b95);
            like.setPadding(0, 0, dp(18), 0);
            like.setOnClickListener(v -> {
                List<String> ls = split("likes");
                if (ls.contains(postText)) ls.remove(postText); else ls.add(postText);
                saveList("likes", ls);
                recreate();
            });
            actions.addView(like);
            TextView commentBtn = new TextView(this);
            commentBtn.setText("评论");
            commentBtn.setTextSize(12);
            commentBtn.setTextColor(0xff576b95);
            commentBtn.setOnClickListener(v -> {
                EditText input = new EditText(this);
                input.setHint("写评论…");
                new AlertDialog.Builder(this).setTitle("评论").setView(input)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("发送", (d, w) -> {
                            String t = input.getText().toString().trim();
                            if (t.isEmpty()) return;
                            List<String> cs = split("comments_" + Math.abs(postText.hashCode()));
                            cs.add(t);
                            saveList("comments_" + Math.abs(postText.hashCode()), cs);
                            recreate();
                        }).show();
            });
            actions.addView(commentBtn);
            body.addView(actions);

            List<String> postComments = split("comments_" + Math.abs(postText.hashCode()));
            if (!postComments.isEmpty()) {
                LinearLayout cbox = new LinearLayout(this);
                cbox.setOrientation(LinearLayout.VERTICAL);
                cbox.setBackgroundColor(0xfff7f7f7);
                cbox.setPadding(dp(8), dp(5), dp(8), dp(5));
                LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cblp.topMargin = dp(6);
                for (String c : postComments) {
                    TextView ct = new TextView(this);
                    ct.setText("本地用户：" + c);
                    ct.setTextSize(12);
                    ct.setTextColor(0xff333333);
                    cbox.addView(ct);
                }
                body.addView(cbox, cblp);
            }
            post.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            final String mm = m;
            post.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setItems(new String[]{"删除这条朋友圈"}, (d, w) -> {
                    moments.remove(mm);
                    saveList("moments", moments);
                    recreate();
                }).show();
                return true;
            });
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            plp.topMargin = dp(8);
            list.addView(post, plp);
        }

        cam.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setHint("这一刻的想法…");
            new AlertDialog.Builder(this).setTitle("发表朋友圈").setView(input)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("发表", (d, w) -> {
                        String t = input.getText().toString().trim();
                        if (!t.isEmpty()) {
                            moments.add(0, t);
                            saveList("moments", moments);
                            recreate();
                        }
                    }).show();
        });
    }

    private void buildAlbum() {
        albumGrid = new LinearLayout(this);
        albumGrid.setOrientation(LinearLayout.VERTICAL);
        content.addView(albumGrid);
        Button add = new Button(this);
        add.setText("＋ 添加图片到本地相册");
        add.setAllCaps(false);
        add.setOnClickListener(v -> pickAlbum());
        content.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        renderAlbum();
    }

    private void renderAlbum() {
        if (albumGrid == null) return;
        albumGrid.removeAllViews();
        TextView e = new TextView(this);
        e.setText(albumImages.isEmpty() ? "还没有图片" : "已保存 " + albumImages.size() + " 张图片（仅本机路径）");
        e.setTextSize(13);
        e.setTextColor(0xff8b90a0);
        albumGrid.addView(e);
        for (String p : albumImages) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(0xffeef0f6);
            try {
                Bitmap bmp = AvatarUpload.decode(this, android.net.Uri.parse(p));
                if (bmp != null) iv.setImageBitmap(bmp);
            } catch (Exception ignored) { }
            iv.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setTitle("删除图片").setMessage("只从本地相册移除")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (d, w) -> {
                            albumImages.remove(p);
                            saveList("album", albumImages);
                            renderAlbum();
                        }).show();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(96), dp(96));
            lp.bottomMargin = dp(8);
            albumGrid.addView(iv, lp);
        }
    }

    private final List<String> albumImages = new ArrayList<>();
    private int favFilter = 0;
    private List<String> favItems = new ArrayList<>();
    private LinearLayout favList;

    private void pickAlbum() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "选择图片"), 71);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 71 && result == RESULT_OK && data != null && data.getData() != null) {
            albumImages.add(data.getData().toString());
            saveList("album", albumImages);
            renderAlbum();
        }
    }

    private void buildTeam() {
        teamList = new LinearLayout(this);
        teamList.setOrientation(LinearLayout.VERTICAL);
        content.addView(teamList);
        Button add = new Button(this);
        add.setText("＋ 创建团队");
        add.setAllCaps(false);
        add.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setHint("团队名称");
            new AlertDialog.Builder(this).setTitle("创建团队").setView(input)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("创建", (d, w) -> {
                        String n = input.getText().toString().trim();
                        if (!n.isEmpty()) { teams.add(n); saveList("teams", teams); renderTeams(); }
                    }).show();
        });
        content.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        renderTeams();
    }

    private void renderTeams() {
        if (teamList == null) return;
        teamList.removeAllViews();
        if (teams.isEmpty()) teams.add("我的团队（本地）");
        for (String t : teams) {
            TextView tv = new TextView(this);
            java.util.List<String> members = split("members_" + t);
            tv.setText(t + "   成员 " + members.size() + " 人");
            tv.setTextSize(15);
            tv.setTextColor(0xff1d1f26);
            tv.setBackgroundColor(Color.WHITE);
            tv.setPadding(dp(14), dp(14), dp(14), dp(14));
            tv.setOnClickListener(v -> {
                EditText input = new EditText(this);
                input.setHint("成员名称");
                new AlertDialog.Builder(this).setTitle("添加成员到 " + t).setView(input)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("添加", (d, w) -> {
                            String n = input.getText().toString().trim();
                            if (!n.isEmpty()) {
                                java.util.List<String> mm = split("members_" + t);
                                mm.add(n);
                                saveList("members_" + t, mm);
                                renderTeams();
                            }
                        }).show();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            teamList.addView(tv, lp);
        }
    }

    private void buildVideo() {
        String[] names = {"本地视频 1", "本地视频 2", "本地视频 3"};
        for (String n : names) {
            TextView tv = new TextView(this);
            tv.setText("▶ " + n + "\n短视频演示 · 仅本地展示");
            tv.setTextSize(14);
            tv.setTextColor(0xff1d1f26);
            tv.setBackgroundColor(Color.WHITE);
            tv.setPadding(dp(14), dp(22), dp(14), dp(22));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            content.addView(tv, lp);
        }
    }

    private void buildCall() {
        TextView tv = new TextView(this);
        tv.setText("语音 / 视频通话（本地）\n\n通话不会联网，仅展示本地界面。");
        tv.setTextSize(15);
        tv.setTextColor(0xff1d1f26);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(60), 0, dp(30));
        content.addView(tv);
        Button end = new Button(this);
        end.setText("结束通话");
        end.setAllCaps(false);
        end.setOnClickListener(v -> finish());
        content.addView(end, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
    }

    private void buildGeneric() {
        TextView tv = new TextView(this);
        tv.setText(title + "\n\n这是一个独立的本地工具页，数据只保存在本机。");
        tv.setTextSize(15);
        tv.setTextColor(0xff5b6070);
        tv.setPadding(0, dp(30), 0, dp(20));
        content.addView(tv);
    }

    private String getBalance() {
        return getSharedPreferences(PREFS, 0).getString("balance", "1000.00");
    }

    private void load() {
        String rawChat = getSharedPreferences(PREFS, 0).getString(chatKey(), "");
        if (!rawChat.isEmpty()) messages.addAll(java.util.Arrays.asList(rawChat.split("\n")));
        bills.addAll(split("bills"));
        contacts.addAll(split("contacts"));
        moments.addAll(split("moments"));
        teams.addAll(split("teams"));
        albumImages.addAll(split("album"));
    }

    private List<String> split(String k) {
        String raw = getSharedPreferences(PREFS, 0).getString(k, "");
        List<String> l = new ArrayList<>();
        if (!raw.isEmpty()) l.addAll(java.util.Arrays.asList(raw.split("\n")));
        return l;
    }

    private void save() {
        getSharedPreferences(PREFS, 0).edit().putString(chatKey(), join(messages)).apply();
    }
    private String chatKey() { return "chat_" + chatName; }

    private void saveList(String key, List<String> list) {
        getSharedPreferences(PREFS, 0).edit().putString(key, join(list)).apply();
    }

    private String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append('\n'); sb.append(l.get(i)); }
        return sb.toString();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }


    private void buildInfo(String head, String[] lines) {
        TextView h = new TextView(this);
        h.setText(head);
        h.setTextSize(15);
        h.setTextColor(0xff5b6070);
        h.setPadding(0, 0, 0, dp(10));
        content.addView(h);
        for (String line : lines) {
            TextView tv = new TextView(this);
            tv.setText(line);
            tv.setTextSize(14);
            tv.setTextColor(0xff1d1f26);
            tv.setBackgroundColor(Color.WHITE);
            tv.setPadding(dp(14), dp(14), dp(14), dp(14));
            tv.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(head).setMessage(line).setPositiveButton("关闭", null).show());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            content.addView(tv, lp);
        }
    }

    private void buildFavorites() {
        content.setBackgroundColor(0xfff7f7f7);
        content.setPadding(0, 0, 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xfff7f7f7);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(43), dp(43)));
        TextView title = new TextView(this);
        title.setText("我的收藏");
        title.setTextSize(16);
        title.setTextColor(0xff191919);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(43), dp(43)));
        content.addView(bar);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setBackgroundColor(Color.WHITE);
        String[] tabs = {"全部", "图片", "链接", "文件", "语音"};
        for (int i = 0; i < tabs.length; i++) {
            TextView tab = new TextView(this);
            tab.setText(tabs[i]);
            tab.setTextSize(14);
            tab.setTextColor(0xff576b95);
            tab.setGravity(Gravity.CENTER);
            final int idx2 = i;
            tab.setOnClickListener(v -> {
                favFilter = idx2;
                for (int k = 0; k < tabRow.getChildCount(); k++) {
                    TextView t = (TextView) tabRow.getChildAt(k);
                    t.setTypeface(null, k == idx2 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                }
                renderFavorites();
            });
            if (i == 0) tab.setTypeface(null, android.graphics.Typeface.BOLD);
            tabRow.addView(tab, new LinearLayout.LayoutParams(0, dp(52), 1f));
        }
        content.addView(tabRow);
        View line = new View(this);
        line.setBackgroundColor(0xffe5e5e5);
        content.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        favItems = split("favorites");
        favList = new LinearLayout(this);
        favList.setOrientation(LinearLayout.VERTICAL);
        content.addView(favList);
        renderFavorites();
    }

    private boolean favMatch(String f) {
        String lower = f.toLowerCase();
        switch (favFilter) {
            case 1: return lower.contains("[图片]") || lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg");
            case 2: return lower.contains("http://") || lower.contains("https://");
            case 3: return lower.contains("[文件]") || lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".zip");
            case 4: return lower.contains("[语音]") || lower.endsWith(".mp3") || lower.endsWith(".amr");
            default: return true;
        }
    }

    private void renderFavorites() {
        if (favList == null) return;
        favList.removeAllViews();
        int shown = 0;
        for (String f : favItems) {
            if (!favMatch(f)) continue;
            shown++;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(Color.WHITE);
            row.setPadding(dp(15), dp(14), dp(15), dp(14));
            row.addView(assetIcon("ic_wx_me_favor", 24), new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView tv = new TextView(this);
            tv.setText(f);
            tv.setTextSize(15);
            tv.setTextColor(0xff191919);
            tv.setPadding(dp(12), 0, 0, 0);
            row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setTitle("删除收藏").setMessage("只从本地收藏移除")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (d, w) -> {
                            favItems.remove(f);
                            saveList("favorites", favItems);
                            renderFavorites();
                        }).show();
                return true;
            });
            favList.addView(row);
            View d = new View(this);
            d.setBackgroundColor(0xffe5e5e5);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            dlp.leftMargin = dp(51);
            favList.addView(d, dlp);
        }
        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText("暂无收藏\n\n在聊天中长按消息即可收藏到这里");
            empty.setTextSize(14);
            empty.setTextColor(0xff999999);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(60), dp(20), dp(20));
            favList.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private void buildCards() {
        content.setBackgroundColor(0xfff5f5f5);
        content.setPadding(0, 0, 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(43), dp(43)));
        TextView title = new TextView(this);
        title.setText("卡包");
        title.setTextSize(16);
        title.setTextColor(0xff191919);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(43), dp(43)));
        content.addView(bar);

        List<String> cards = split("cards");
        if (cards.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无卡券\n\n点击下方按钮添加一张本地卡券");
            empty.setTextSize(14);
            empty.setTextColor(0xff999999);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(70), dp(20), dp(20));
            content.addView(empty);
        } else {
            for (String c : cards) {
                LinearLayout card = new LinearLayout(this);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setBackgroundColor(Color.WHITE);
                card.setPadding(dp(16), dp(18), dp(16), dp(18));
                card.addView(assetIcon("ic_wx_me_card_bg", 36), new LinearLayout.LayoutParams(dp(36), dp(36)));
                TextView tv = new TextView(this);
                tv.setText(c);
                tv.setTextSize(15);
                tv.setTextColor(0xff191919);
                tv.setPadding(dp(14), 0, 0, 0);
                card.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                card.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle(c)
                        .setMessage("本地卡券\n\n有效期：长期有效\n使用范围：仅本机演示")
                        .setNegativeButton("关闭", null)
                        .setPositiveButton("使用", (d, w) -> toast("已使用：" + c))
                        .show());
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.leftMargin = dp(8);
                clp.rightMargin = dp(8);
                clp.topMargin = dp(8);
                content.addView(card, clp);
            }
        }
        TextView add = new TextView(this);
        add.setText("＋ 添加卡券");
        add.setTextSize(15);
        add.setTextColor(Color.WHITE);
        add.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0xff07c160);
        g.setCornerRadius(dp(4));
        add.setBackground(g);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        alp.leftMargin = dp(16);
        alp.rightMargin = dp(16);
        alp.topMargin = dp(20);
        content.addView(add, alp);
        add.setOnClickListener(v -> {
            List<String> cs = split("cards");
            cs.add(0, "本地卡券 " + (cs.size() + 1));
            saveList("cards", cs);
            recreate();
        });
    }

    private void buildStickers() {
        content.setBackgroundColor(0xfff5f5f5);
        content.setPadding(0, 0, 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(43), dp(43)));
        TextView title = new TextView(this);
        title.setText("表情");
        title.setTextSize(16);
        title.setTextColor(0xff191919);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(43), dp(43)));
        content.addView(bar);

        String[] emojis = {"😀", "😁", "😂", "🤣", "😊", "😍", "😘", "😎", "🤔", "😭", "😡", "👍", "👏", "🙏", "🎉", "❤️", "🌹", "☕", "🐶", "🐱"};
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(12), dp(12), dp(12), dp(12));
        content.addView(grid);
        LinearLayout row = null;
        for (int i = 0; i < emojis.length; i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            TextView tv = new TextView(this);
            tv.setText(emojis[i]);
            tv.setTextSize(28);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundColor(Color.WHITE);
            final String e = emojis[i];
            tv.setOnClickListener(v -> toast("已选择 " + e));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(64), 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(tv, lp);
        }
    }

    private void buildNewFriends() {
        content.setBackgroundColor(0xfff5f5f8);
        content.setPadding(0, 0, 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(43), dp(43)));
        TextView title = new TextView(this);
        title.setText("新的朋友");
        title.setTextSize(16);
        title.setTextColor(0xff2b2b2b);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(43), dp(43)));
        content.addView(bar);

        EditText search = new EditText(this);
        search.setHint("搜索手机号 / 微信号");
        search.setTextSize(14);
        search.setSingleLine(true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        slp.leftMargin = dp(10);
        slp.rightMargin = dp(10);
        slp.topMargin = dp(10);
        content.addView(search, slp);

        LinearLayout addRow = new LinearLayout(this);
        addRow.setGravity(Gravity.CENTER_VERTICAL);
        addRow.setBackgroundColor(Color.WHITE);
        addRow.setPadding(dp(14), dp(12), dp(14), dp(12));
        addRow.addView(assetIcon("ic_wx_contact_new_friend", 36), new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView addTv = new TextView(this);
        addTv.setText("添加朋友");
        addTv.setTextSize(15.45f);
        addTv.setTextColor(0xff303030);
        addTv.setPadding(dp(14), 0, 0, 0);
        addRow.addView(addTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(addRow);
        addRow.setOnClickListener(v -> toast("本地添加朋友"));

        List<String> reqs = split("friend_requests");
        if (reqs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无新的朋友\n\n可通过手机号或微信号添加");
            empty.setTextSize(14);
            empty.setTextColor(0xff999999);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(60), dp(20), dp(20));
            content.addView(empty);
        } else {
            for (String r : reqs) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackgroundColor(Color.WHITE);
                row.setPadding(dp(14), dp(10), dp(14), dp(10));
                row.addView(assetIcon("ic_wx_default_image", 40), new LinearLayout.LayoutParams(dp(40), dp(40)));
                TextView name = new TextView(this);
                name.setText(r);
                name.setTextSize(15);
                name.setTextColor(0xff191919);
                name.setPadding(dp(12), 0, 0, 0);
                row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView ok = new TextView(this);
                ok.setText("接受");
                ok.setTextSize(13);
                ok.setTextColor(Color.WHITE);
                ok.setGravity(Gravity.CENTER);
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setColor(0xff07c160);
                g.setCornerRadius(dp(3));
                ok.setBackground(g);
                row.addView(ok, new LinearLayout.LayoutParams(dp(54), dp(28)));
                content.addView(row);
            }
        }
    }

    private void buildWalletDetail() {
        content.setBackgroundColor(0xffededed);
        content.setPadding(0, 0, 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(43), dp(43)));
        TextView title = new TextView(this);
        title.setText("零钱明细");
        title.setTextSize(16);
        title.setTextColor(0xff191919);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(43), dp(43)));
        content.addView(bar);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(8);
        content.addView(card, clp);
        if (bills.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无零钱明细");
            empty.setTextSize(14);
            empty.setTextColor(0xff999999);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, dp(40));
            card.addView(empty);
        } else {
            for (String b : bills) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(15), dp(14), dp(15), dp(14));
                TextView tv = new TextView(this);
                tv.setText(b);
                tv.setTextSize(14);
                tv.setTextColor(0xff191919);
                row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                card.addView(row);
                View line = new View(this);
                line.setBackgroundColor(0xffe5e5e5);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                llp.leftMargin = dp(15);
                card.addView(line, llp);
            }
        }
    }

    private void buildPickContacts(boolean group) {
        content.setBackgroundColor(0xffededed);
        content.setPadding(0, 0, 0, 0);
        final List<String> picked = new ArrayList<>();

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(43), dp(43)));
        TextView title = new TextView(this);
        title.setText(group ? "选择联系人" : "选择联系人");
        title.setTextSize(16);
        title.setTextColor(0xff191919);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        TextView done = new TextView(this);
        done.setText("确定");
        done.setTextSize(15);
        done.setTextColor(0xff07c160);
        done.setGravity(Gravity.CENTER);
        bar.addView(done, new LinearLayout.LayoutParams(dp(60), dp(43)));
        content.addView(bar);

        EditText search = new EditText(this);
        search.setHint("搜索联系人");
        search.setTextSize(14);
        search.setSingleLine(true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        slp.leftMargin = dp(10);
        slp.rightMargin = dp(10);
        slp.topMargin = dp(8);
        content.addView(search, slp);

        if (contacts.isEmpty()) { contacts.add("文件传输助手"); contacts.add("本地好友"); }
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundColor(Color.WHITE);
        content.addView(list);
        for (String c : contacts) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(8), dp(14), dp(8));
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setOnCheckedChangeListener((b, checked) -> {
                if (checked) { if (!picked.contains(c)) picked.add(c); }
                else picked.remove(c);
            });
            row.addView(cb, new LinearLayout.LayoutParams(dp(36), dp(36)));
            row.addView(assetIcon("ic_wx_default_image", 40), new LinearLayout.LayoutParams(dp(40), dp(40)));
            TextView name = new TextView(this);
            name.setText(c);
            name.setTextSize(15);
            name.setTextColor(0xff191919);
            name.setPadding(dp(12), 0, 0, 0);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            list.addView(row);
        }

        done.setOnClickListener(v -> {
            if (picked.isEmpty()) { toast("请选择联系人"); return; }
            if (group) {
                List<String> groups = split("groups");
                String g = "本地群聊(" + picked.size() + ")";
                groups.add(0, g);
                saveList("groups", groups);
                toast("已创建 " + g);
                finish();
            } else {
                LocalToolActivity.open(this, "chat", picked.get(0));
                finish();
            }
        });
    }

    private void buildOfficial() {
        content.setBackgroundColor(0xffededed);
        content.setPadding(0, 0, 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(43), dp(43)));
        TextView title = new TextView(this);
        title.setText("公众号");
        title.setTextSize(16);
        title.setTextColor(0xff191919);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(43), dp(43)));
        content.addView(bar);

        LinearLayout sub = new LinearLayout(this);
        sub.setGravity(Gravity.CENTER_VERTICAL);
        sub.setBackgroundColor(Color.WHITE);
        sub.setPadding(dp(14), dp(12), dp(14), dp(12));
        sub.addView(assetIcon("ic_wx_contact_public_number", 36), new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView st = new TextView(this);
        st.setText("订阅号消息");
        st.setTextSize(15);
        st.setTextColor(0xff191919);
        st.setPadding(dp(12), 0, 0, 0);
        sub.addView(st, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(sub);
        sub.setOnClickListener(v -> LocalToolActivity.open(this, "articles", "订阅号消息"));

        String[] accounts = {"本地公众号", "豆图教程", "微信派"};
        for (String a : accounts) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(Color.WHITE);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.addView(assetIcon("ic_wx_default_image", 40), new LinearLayout.LayoutParams(dp(40), dp(40)));
            TextView name = new TextView(this);
            name.setText(a);
            name.setTextSize(15);
            name.setTextColor(0xff191919);
            name.setPadding(dp(12), 0, 0, 0);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(v -> LocalToolActivity.open(this, "articles", a));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(1);
            content.addView(row, rlp);
        }
    }

    private void buildWithdraw() {
        content.setBackgroundColor(0xffededed);
        content.setPadding(0, 0, 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(43), dp(43)));
        TextView title = new TextView(this);
        title.setText("零钱提现");
        title.setTextSize(16);
        title.setTextColor(0xff191919);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(43), dp(43)));
        content.addView(bar);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(8);
        content.addView(card, clp);
        TextView to = new TextView(this);
        to.setText("提现到 零钱");
        to.setTextSize(13);
        to.setTextColor(0xff888888);
        card.addView(to);
        EditText amount = new EditText(this);
        amount.setHint("提现金额");
        amount.setTextSize(26);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setBackground(null);
        amount.setPadding(0, dp(10), 0, dp(10));
        card.addView(amount, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView bal = new TextView(this);
        bal.setText("当前零钱 ¥" + getBalance() + "，提现手续费 0.1%");
        bal.setTextSize(12);
        bal.setTextColor(0xff888888);
        card.addView(bal);

        TextView ok = new TextView(this);
        ok.setText("提现");
        ok.setTextSize(16);
        ok.setTextColor(Color.WHITE);
        ok.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0xff07c160);
        g.setCornerRadius(dp(4));
        ok.setBackground(g);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        olp.leftMargin = dp(18);
        olp.rightMargin = dp(18);
        olp.topMargin = dp(18);
        content.addView(ok, olp);
        ok.setOnClickListener(v -> {
            String a = amount.getText().toString().trim();
            if (a.isEmpty()) { toast("请输入提现金额"); return; }
            try {
                double v2 = Double.parseDouble(a);
                double b = Double.parseDouble(getBalance());
                if (v2 <= 0 || v2 > b) { toast("金额超出零钱余额"); return; }
                double nb = b - v2;
                getSharedPreferences(PREFS, 0).edit().putString("balance", String.format("%.2f", nb)).apply();
                bills.add(0, "提现 -¥" + String.format("%.2f", v2));
                saveList("bills", bills);
                toast("提现成功（本地）");
                finish();
            } catch (Exception e) { toast("金额格式不正确"); }
        });
    }

    private void buildGroup() {
        content.setBackgroundColor(0xffededed);
        content.setPadding(0, 0, 0, 0);
        final List<String> msgs = split("chat_" + chatName);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(43), dp(43)));
        TextView title = new TextView(this);
        title.setText(chatName);
        title.setTextSize(15);
        title.setTextColor(0xff181818);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(assetIcon("ic_wx_chat_title_more", 22), new LinearLayout.LayoutParams(dp(43), dp(43)));
        content.addView(bar);
        View line = new View(this);
        line.setBackgroundColor(0xffd9d9d9);
        content.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(6), dp(8), dp(6), dp(8));
        list.setMinimumHeight(dp(360));
        content.addView(list);
        if (msgs.isEmpty()) msgs.add("大家好，这是本地群聊");
        for (String m : msgs) {
            TextView tv = new TextView(this);
            tv.setText(m);
            tv.setTextSize(15);
            tv.setTextColor(0xff191919);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xffffffff);
            bg.setCornerRadius(dp(4));
            tv.setBackground(bg);
            tv.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.START);
            row.setPadding(dp(6), dp(6), dp(6), dp(6));
            row.addView(assetIcon("ic_wx_default_image", 36), new LinearLayout.LayoutParams(dp(36), dp(36)));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.leftMargin = dp(8);
            row.addView(tv, tlp);
            list.addView(row);
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setBackgroundColor(0xfff7f7f7);
        footer.setPadding(dp(9), 0, dp(8), 0);
        EditText input = new EditText(this);
        input.setHint("群聊消息");
        input.setTextSize(14);
        android.graphics.drawable.GradientDrawable ibg = new android.graphics.drawable.GradientDrawable();
        ibg.setColor(Color.WHITE);
        ibg.setCornerRadius(dp(6));
        input.setBackground(ibg);
        footer.addView(input, new LinearLayout.LayoutParams(0, dp(36), 1f));
        TextView send = new TextView(this);
        send.setText("发送");
        send.setTextSize(14);
        send.setTextColor(Color.WHITE);
        send.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable sg = new android.graphics.drawable.GradientDrawable();
        sg.setColor(0xff07c160);
        sg.setCornerRadius(dp(4));
        send.setBackground(sg);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(54), dp(29));
        slp.leftMargin = dp(8);
        footer.addView(send, slp);
        content.addView(footer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        send.setOnClickListener(v -> {
            String t = input.getText().toString().trim();
            if (t.isEmpty()) return;
            List<String> mm = split("chat_" + chatName);
            mm.add(t);
            saveList("chat_" + chatName, mm);
            recreate();
        });
    }

    private void buildUpload() {
        content.setBackgroundColor(0xffededed);
        content.setPadding(0, 0, 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(43), dp(43)));
        TextView title = new TextView(this);
        title.setText("上传截图");
        title.setTextSize(16);
        title.setTextColor(0xff191919);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(43), dp(43)));
        content.addView(bar);

        TextView tip = new TextView(this);
        tip.setText("选择截图上传到本地素材库");
        tip.setTextSize(13);
        tip.setTextColor(0xff888888);
        tip.setPadding(dp(15), dp(10), 0, dp(6));
        content.addView(tip);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setBackgroundColor(Color.WHITE);
        grid.setPadding(dp(10), dp(10), dp(10), dp(10));
        content.addView(grid);
        List<String> imgs = split("album");
        LinearLayout row = null;
        for (int i = 0; i < imgs.size(); i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(0xffeef0f6);
            try {
                android.graphics.Bitmap bmp = AvatarUpload.decode(this, android.net.Uri.parse(imgs.get(i)));
                if (bmp != null) iv.setImageBitmap(bmp);
            } catch (Exception ignored) { }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(110), 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(iv, lp);
        }
        if (imgs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有上传截图");
            empty.setTextSize(14);
            empty.setTextColor(0xff999999);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, dp(40));
            grid.addView(empty);
        }

        TextView pick = new TextView(this);
        pick.setText("＋ 从相册选择");
        pick.setTextSize(15);
        pick.setTextColor(Color.WHITE);
        pick.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0xff07c160);
        g.setCornerRadius(dp(4));
        pick.setBackground(g);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        plp.leftMargin = dp(15);
        plp.rightMargin = dp(15);
        plp.topMargin = dp(16);
        content.addView(pick, plp);
        pick.setOnClickListener(v -> pickAlbum());
    }

    private void buildService() {
        content.setBackgroundColor(0xffededed);
        content.setPadding(0, 0, 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(21), dp(21));
        blp.leftMargin = dp(13);
        bar.addView(back, blp);
        TextView title = new TextView(this);
        title.setText("服务");
        title.setTextSize(16);
        title.setTextColor(0xff2b2b2b);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(34), dp(43)));
        content.addView(bar);

        LinearLayout green = new LinearLayout(this);
        green.setOrientation(LinearLayout.HORIZONTAL);
        android.graphics.drawable.GradientDrawable gg = new android.graphics.drawable.GradientDrawable();
        gg.setColor(0xff3bad6a);
        gg.setCornerRadius(dp(8));
        green.setBackground(gg);
        green.setPadding(dp(6), dp(20), dp(6), dp(26));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.leftMargin = dp(7);
        glp.rightMargin = dp(7);
        glp.topMargin = dp(7);
        content.addView(green, glp);

        String[][] heads = {{"ic_wx_payment", "收付款"}, {"ic_wx_wallet", "钱包"}};
        for (String[] h : heads) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            col.addView(assetIcon(h[0], 30), new LinearLayout.LayoutParams(dp(30), dp(24)));
            TextView t = new TextView(this);
            t.setText(h[1]);
            t.setTextSize(15);
            t.setTextColor(Color.WHITE);
            t.setPadding(0, dp(14), 0, 0);
            col.addView(t);
            if ("钱包".equals(h[1])) {
                TextView b = new TextView(this);
                b.setText("¥" + getBalance());
                b.setTextSize(13);
                b.setTextColor(0xff9bd8b2);
                b.setPadding(0, dp(3), 0, 0);
                col.addView(b);
            }
            col.setOnClickListener(v -> LocalToolActivity.open(this, "钱包".equals(h[1]) ? "wallet" : "pay", h[1]));
            green.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(7);
        clp.rightMargin = dp(7);
        clp.topMargin = dp(8);
        content.addView(card, clp);

        TextView sec = new TextView(this);
        sec.setText("第三方服务");
        sec.setTextSize(12);
        sec.setTextColor(0xff707075);
        sec.setPadding(dp(15), dp(22), 0, dp(18));
        card.addView(sec);

        String[] names = {"手机充值", "生活缴费", "信用卡还款", "理财通", "保险服务", "腾讯公益", "城市服务", "医疗健康", "火车票机票", "酒店", "电影演出", "美团外卖"};
        String[] keys = {"pay", "pay", "transfer", "wallet", "articles", "articles", "generic", "generic", "video", "shop", "video", "shop"};
        LinearLayout row = null;
        for (int i = 0; i < names.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                card.addView(row);
            }
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, dp(12), 0, dp(12));
            cell.addView(assetIcon("ic_wx_wallet_other", 30), new LinearLayout.LayoutParams(dp(30), dp(30)));
            TextView tv = new TextView(this);
            tv.setText(names[i]);
            tv.setTextSize(12);
            tv.setTextColor(0xff666666);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, dp(8), 0, 0);
            cell.addView(tv);
            final int ii = i;
            cell.setOnClickListener(v -> LocalToolActivity.open(this, keys[ii], names[ii]));
            row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private void buildPayee() {
        content.setBackgroundColor(0xffededed);
        content.setPadding(0, 0, 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(43), dp(43)));
        TextView title = new TextView(this);
        title.setText("微信收款助手");
        title.setTextSize(16);
        title.setTextColor(0xff191919);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(43), dp(43)));
        content.addView(bar);

        double total = 0;
        int count = 0;
        for (String b : bills) {
            if (b.contains("收款")) {
                count++;
                int i = b.indexOf("+¥");
                if (i >= 0) {
                    int j = i + 2;
                    while (j < b.length() && (Character.isDigit(b.charAt(j)) || b.charAt(j) == '.')) j++;
                    try { total += Double.parseDouble(b.substring(i + 2, j)); } catch (Exception ignored) { }
                }
            }
        }

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER);
        head.setBackgroundColor(Color.WHITE);
        head.setPadding(0, dp(24), 0, dp(24));
        head.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView tip = new TextView(this);
        tip.setText("收款记录");
        tip.setTextSize(13);
        tip.setTextColor(0xff888888);
        tip.setGravity(Gravity.CENTER);
        head.addView(tip);
        TextView sum = new TextView(this);
        sum.setText("¥" + String.format("%.2f", total));
        sum.setTextSize(36);
        sum.setTextColor(0xff191919);
        sum.setGravity(Gravity.CENTER);
        sum.setPadding(0, dp(8), 0, 0);
        head.addView(sum);
        TextView cnt = new TextView(this);
        cnt.setText("共 " + count + " 笔收款");
        cnt.setTextSize(12);
        cnt.setTextColor(0xff888888);
        cnt.setGravity(Gravity.CENTER);
        cnt.setPadding(0, dp(4), 0, 0);
        head.addView(cnt);
        content.addView(head);

        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText("还没有收款记录\n\n在「收付款」页模拟收款试试");
            empty.setTextSize(14);
            empty.setTextColor(0xff999999);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(50), dp(20), dp(20));
            content.addView(empty);
        } else {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(Color.WHITE);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.topMargin = dp(8);
            content.addView(card, clp);
            for (String b : bills) {
                if (!b.contains("收款")) continue;
                TextView row = new TextView(this);
                row.setText(b);
                row.setTextSize(14);
                row.setTextColor(0xff191919);
                row.setPadding(dp(15), dp(14), dp(15), dp(14));
                card.addView(row);
                View line = new View(this);
                line.setBackgroundColor(0xffe5e5e5);
                card.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            }
        }
    }

    private void buildPayPage() {
        content.setBackgroundColor(0xffededed);
        content.setPadding(0, 0, 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.WHITE);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(21), dp(21));
        blp.leftMargin = dp(13);
        bar.addView(back, blp);
        TextView title = new TextView(this);
        title.setText("收付款");
        title.setTextSize(16);
        title.setTextColor(0xff2b2b2b);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(34), dp(43)));
        content.addView(bar);

        LinearLayout green = new LinearLayout(this);
        green.setOrientation(LinearLayout.HORIZONTAL);
        android.graphics.drawable.GradientDrawable gg = new android.graphics.drawable.GradientDrawable();
        gg.setColor(0xff3bad6a);
        gg.setCornerRadius(dp(8));
        green.setBackground(gg);
        green.setPadding(dp(6), dp(24), dp(6), dp(20));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.leftMargin = dp(7);
        glp.rightMargin = dp(7);
        glp.topMargin = dp(7);
        content.addView(green, glp);

        LinearLayout col1 = new LinearLayout(this);
        col1.setOrientation(LinearLayout.VERTICAL);
        col1.setGravity(Gravity.CENTER);
        col1.addView(assetIcon("ic_wx_payment", 30), new LinearLayout.LayoutParams(dp(30), dp(24)));
        TextView t1 = new TextView(this);
        t1.setText("收付款");
        t1.setTextSize(15);
        t1.setTextColor(Color.WHITE);
        t1.setPadding(0, dp(16), 0, 0);
        col1.addView(t1);
        green.addView(col1, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout col2 = new LinearLayout(this);
        col2.setOrientation(LinearLayout.VERTICAL);
        col2.setGravity(Gravity.CENTER);
        col2.addView(assetIcon("ic_wx_wallet", 30), new LinearLayout.LayoutParams(dp(30), dp(24)));
        TextView t2 = new TextView(this);
        t2.setText("钱包");
        t2.setTextSize(15);
        t2.setTextColor(Color.WHITE);
        t2.setPadding(0, dp(16), 0, 0);
        col2.addView(t2);
        TextView tb = new TextView(this);
        tb.setText("¥" + getBalance());
        tb.setTextSize(13);
        tb.setTextColor(0xff9bd8b2);
        tb.setPadding(0, dp(3), 0, 0);
        col2.addView(tb);
        green.addView(col2, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout codeCard = new LinearLayout(this);
        codeCard.setOrientation(LinearLayout.VERTICAL);
        codeCard.setGravity(Gravity.CENTER);
        codeCard.setBackgroundColor(Color.WHITE);
        codeCard.setPadding(dp(20), dp(22), dp(20), dp(22));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(7);
        clp.rightMargin = dp(7);
        clp.topMargin = dp(8);
        content.addView(codeCard, clp);

        ImageView qr = new ImageView(this);
        qr.setImageBitmap(makeQr(210));
        codeCard.addView(qr, new LinearLayout.LayoutParams(dp(210), dp(210)));
        TextView payTip = new TextView(this);
        payTip.setText("向商家付款");
        payTip.setTextSize(15);
        payTip.setTextColor(0xff191919);
        payTip.setPadding(0, dp(12), 0, 0);
        codeCard.addView(payTip);
        TextView sub = new TextView(this);
        sub.setText("本功能仅本地演示，不会联网");
        sub.setTextSize(12);
        sub.setTextColor(0xff999999);
        sub.setPadding(0, dp(4), 0, 0);
        codeCard.addView(sub);

        TextView receive = new TextView(this);
        receive.setText("模拟收款 ¥50（本地）");
        receive.setTextSize(15);
        receive.setTextColor(Color.WHITE);
        receive.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable rg = new android.graphics.drawable.GradientDrawable();
        rg.setColor(0xff07c160);
        rg.setCornerRadius(dp(4));
        receive.setBackground(rg);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        rlp.leftMargin = dp(7);
        rlp.rightMargin = dp(7);
        rlp.topMargin = dp(10);
        content.addView(receive, rlp);
        receive.setOnClickListener(v -> {
            try {
                double nb = Double.parseDouble(getBalance()) + 50;
                getSharedPreferences(PREFS, 0).edit().putString("balance", String.format("%.2f", nb)).apply();
                bills.add(0, "收款 +¥50.00");
                saveList("bills", bills);
                tb.setText("¥" + getBalance());
                toast("收款 ¥50.00（本地）");
            } catch (Exception ignored) { }
        });
    }

    private android.graphics.Bitmap makeQr(int size) {
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(bmp);
        c.drawColor(Color.WHITE);
        android.graphics.Paint pt = new android.graphics.Paint();
        pt.setColor(0xff111111);
        int n = 21;
        float cell = size / (float) n;
        java.util.Random rnd = new java.util.Random(20261021L);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                boolean finder = (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7);
                boolean on;
                if (finder) {
                    int lx = x < 7 ? x : x - (n - 7);
                    int ly = y < 7 ? y : y - (n - 7);
                    on = lx == 0 || lx == 6 || ly == 0 || ly == 6 || (lx >= 2 && lx <= 4 && ly >= 2 && ly <= 4);
                } else {
                    on = rnd.nextBoolean();
                }
                if (on) c.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, pt);
            }
        }
        return bmp;
    }

    private void buildBillStats() {
        content.setBackgroundColor(0xff3cb371);
        content.setPadding(0, 0, 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xff3cb371);
        ImageView back = assetIcon("ic_wx_bill_back_dark", 16);
        back.setColorFilter(Color.WHITE);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(13), dp(13));
        blp.leftMargin = dp(12);
        bar.addView(back, blp);
        TextView title = new TextView(this);
        title.setText("账单");
        title.setTextSize(14);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(37), dp(43)));
        content.addView(bar);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(0, dp(12), 0, 0);
        TextView month = new TextView(this);
        month.setText("月账单");
        month.setTextSize(14.5f);
        month.setTextColor(0xff9dd9b8);
        tabs.addView(month);
        TextView year = new TextView(this);
        year.setText("年账单");
        year.setTextSize(14.5f);
        year.setTextColor(Color.WHITE);
        year.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams ylp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ylp.leftMargin = dp(50);
        tabs.addView(year, ylp);
        content.addView(tabs);

        LinearLayout info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(24), dp(22), dp(15), 0);
        TextView y = new TextView(this);
        y.setText("2026年");
        y.setTextSize(14.5f);
        y.setTextColor(Color.WHITE);
        info.addView(y);
        ImageView arrow = assetIcon("icon_wx_bill_statisc_choose_arrow", 12);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(12), dp(12));
        alp.leftMargin = dp(4);
        info.addView(arrow, alp);
        info.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView out = new TextView(this);
        out.setText("支出");
        out.setTextSize(12);
        out.setTextColor(0xff3cb371);
        out.setPadding(dp(10), dp(4), dp(10), dp(4));
        android.graphics.drawable.GradientDrawable og = new android.graphics.drawable.GradientDrawable();
        og.setColor(Color.WHITE);
        og.setCornerRadius(dp(3));
        out.setBackground(og);
        info.addView(out);
        TextView in = new TextView(this);
        in.setText("收入");
        in.setTextSize(12);
        in.setTextColor(0xff9dd9b8);
        in.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.leftMargin = dp(14);
        info.addView(in, ilp);
        content.addView(info);

        double spent = 0;
        for (String b : bills) {
            int i = b.indexOf("-¥");
            if (i >= 0) {
                int j = i + 2;
                while (j < b.length() && (Character.isDigit(b.charAt(j)) || b.charAt(j) == '.')) j++;
                try { spent += Double.parseDouble(b.substring(i + 2, j)); } catch (Exception ignored) { }
            }
        }
        TextView total = new TextView(this);
        total.setText("¥" + String.format("%.2f", spent));
        total.setTextSize(36);
        total.setTextColor(Color.WHITE);
        total.setGravity(Gravity.CENTER);
        total.setPadding(0, dp(18), 0, 0);
        content.addView(total);
        TextView lbl = new TextView(this);
        lbl.setText("支出 · 共 " + bills.size() + " 笔");
        lbl.setTextSize(12);
        lbl.setTextColor(0xff9dd9b8);
        lbl.setGravity(Gravity.CENTER);
        lbl.setPadding(0, dp(4), 0, dp(16));
        content.addView(lbl);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(7);
        clp.rightMargin = dp(7);
        clp.bottomMargin = dp(20);
        content.addView(card, clp);
        if (bills.isEmpty()) {
            TextView e = new TextView(this);
            e.setText("暂无账单，去「转账 / 红包」试试");
            e.setTextSize(13);
            e.setTextColor(0xff999999);
            e.setPadding(0, dp(12), 0, dp(12));
            card.addView(e);
        } else {
            for (String b : bills) {
                TextView row = new TextView(this);
                row.setText(b);
                row.setTextSize(14);
                row.setTextColor(0xff191919);
                row.setPadding(0, dp(12), 0, dp(12));
                card.addView(row);
            }
        }
    }

    private void buildRedPackets() {
        content.setBackgroundColor(Color.WHITE);
        content.setPadding(0, 0, 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xfff55e4d);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setColorFilter(Color.WHITE);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(43), dp(43)));
        TextView title = new TextView(this);
        title.setText("发出的红包");
        title.setTextSize(15);
        title.setTextColor(0xffffe2b1);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(43), dp(43)));
        content.addView(bar);

        List<String> reds = new ArrayList<>();
        double total = 0;
        for (String b : bills) {
            if (b.contains("红包")) {
                reds.add(b);
                int i = b.indexOf("-¥");
                if (i >= 0) {
                    int j = i + 2;
                    while (j < b.length() && (Character.isDigit(b.charAt(j)) || b.charAt(j) == '.')) j++;
                    try { total += Double.parseDouble(b.substring(i + 2, j)); } catch (Exception ignored) { }
                }
            }
        }

        LinearLayout sum = new LinearLayout(this);
        sum.setOrientation(LinearLayout.VERTICAL);
        sum.setGravity(Gravity.CENTER);
        sum.setBackgroundColor(0xffefefef);
        sum.setPadding(0, dp(24), 0, dp(24));
        sum.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sum.addView(assetIcon("ic_wx_default_image", 72), new LinearLayout.LayoutParams(dp(72), dp(72)));
        TextView name = new TextView(this);
        name.setText("本地用户");
        name.setTextSize(15);
        name.setTextColor(0xff181818);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(12), 0, 0);
        sum.addView(name);
        TextView money = new TextView(this);
        money.setText(String.format("%.2f", total));
        money.setTextSize(54);
        money.setTextColor(0xff181818);
        money.setGravity(Gravity.CENTER);
        sum.addView(money);
        TextView count = new TextView(this);
        count.setText("发出红包总数 " + reds.size() + " 个");
        count.setTextSize(15);
        count.setTextColor(0xff979797);
        count.setGravity(Gravity.CENTER);
        count.setPadding(0, dp(10), 0, 0);
        sum.addView(count);
        content.addView(sum);

        if (reds.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有发出红包\n\n在「红包」页发一个试试");
            empty.setTextSize(14);
            empty.setTextColor(0xff999999);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(50), dp(20), dp(20));
            content.addView(empty);
        } else {
            for (String r : reds) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackgroundColor(Color.WHITE);
                row.setPadding(dp(14), dp(12), dp(14), dp(12));
                LinearLayout texts = new LinearLayout(this);
                texts.setOrientation(LinearLayout.VERTICAL);
                TextView type = new TextView(this);
                type.setText("本地红包");
                type.setTextSize(15);
                type.setTextColor(0xff191919);
                texts.addView(type);
                TextView time = new TextView(this);
                time.setText("刚刚");
                time.setTextSize(12.5f);
                time.setTextColor(0xff979797);
                time.setPadding(0, dp(3), 0, 0);
                texts.addView(time);
                row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView amount = new TextView(this);
                int i = r.indexOf("-¥");
                amount.setText(i >= 0 ? r.substring(i + 2) : "");
                amount.setTextSize(15);
                amount.setTextColor(0xff191919);
                row.addView(amount);
                content.addView(row);
                View line = new View(this);
                line.setBackgroundColor(0xffe5e5e5);
                content.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            }
        }
    }

    private void buildSuccess() {
        content.setBackgroundColor(Color.WHITE);
        content.setPadding(0, 0, 0, 0);
        String raw = getSharedPreferences(PREFS, 0).getString("last_pay", "本地支付|0.00");
        String[] parts = raw.split("\\|");
        String merchant = parts.length > 0 ? parts[0] : "本地支付";
        String amount = parts.length > 1 ? parts[1] : "0.00";

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER);
        top.setPadding(0, dp(24), 0, 0);
        top.addView(assetIcon("ic_wx_pay_success", 19), new LinearLayout.LayoutParams(dp(19), dp(17)));
        TextView tip = new TextView(this);
        tip.setText("支付成功");
        tip.setTextSize(15);
        tip.setTextColor(0xff07c160);
        tip.setPadding(dp(7), 0, 0, 0);
        top.addView(tip);
        content.addView(top);

        LinearLayout avatarRow = new LinearLayout(this);
        avatarRow.setGravity(Gravity.CENTER);
        avatarRow.setPadding(0, dp(35), 0, 0);
        avatarRow.addView(assetIcon("ic_wx_default_image", 44), new LinearLayout.LayoutParams(dp(44), dp(44)));
        content.addView(avatarRow);

        TextView name = new TextView(this);
        name.setText("本地用户");
        name.setTextSize(15);
        name.setTextColor(0xff1a1a1a);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(7), 0, 0);
        content.addView(name);

        TextView shop = new TextView(this);
        shop.setText(merchant);
        shop.setTextSize(15);
        shop.setTextColor(0xff353535);
        shop.setGravity(Gravity.CENTER);
        shop.setPadding(0, dp(34), 0, 0);
        content.addView(shop);

        TextView money = new TextView(this);
        money.setText("¥" + amount);
        money.setTextSize(43);
        money.setTextColor(0xff1b1b1b);
        money.setGravity(Gravity.CENTER);
        money.setPadding(0, dp(24), 0, dp(30));
        content.addView(money);

        TextView done = new TextView(this);
        done.setText("完成");
        done.setTextSize(15);
        done.setTextColor(0xff181818);
        done.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0xfff2f2f2);
        g.setCornerRadius(dp(6));
        done.setBackground(g);
        done.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(166), dp(46));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(20);
        content.addView(done, lp);
    }

    private void buildAssets() {
        TextView tv = new TextView(this);
        tv.setText("总资产（本地）\n\n零钱  ¥" + getBalance() + "\n理财  ¥0.00\n红包  ¥0.00\n\n合计  ¥" + getBalance());
        tv.setTextSize(17);
        tv.setTextColor(0xff1d1f26);
        tv.setBackgroundColor(Color.WHITE);
        tv.setPadding(dp(18), dp(18), dp(18), dp(18));
        content.addView(tv);
        Button wallet = new Button(this);
        wallet.setText("查看零钱明细");
        wallet.setAllCaps(false);
        wallet.setOnClickListener(v -> LocalToolActivity.open(this, "wallet", "零钱明细"));
        content.addView(wallet, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        Button bills = new Button(this);
        bills.setText("查看账单统计");
        bills.setAllCaps(false);
        bills.setOnClickListener(v -> LocalToolActivity.open(this, "billstats", "年度账单"));
        content.addView(bills, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
    }

    private void buildSettings() {
        buildSwitches("状态与显示", new String[]{"显示未读红点", "在线状态", "悬浮窗快捷入口"});
        Button clear = new Button(this);
        clear.setText("清除本地聊天记录");
        clear.setAllCaps(false);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("清除聊天记录").setMessage("只清除本机工具页的聊天记录")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (d, w) -> {
                    getSharedPreferences(PREFS, 0).edit().remove("chat_文件传输助手").remove("chat_本地好友").apply();
                    toast("已清除");
                }).show());
        content.addView(clear, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
    }

    private void buildSwitches(String head, String[] labels) {
        TextView h = new TextView(this);
        h.setText(head);
        h.setTextSize(15);
        h.setTextColor(0xff5b6070);
        h.setPadding(0, 0, 0, dp(8));
        content.addView(h);
        for (String label : labels) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(label);
            cb.setTextSize(14);
            cb.setChecked(getSharedPreferences(PREFS, 0).getBoolean("sw_" + label, true));
            cb.setOnCheckedChangeListener((b, checked) -> getSharedPreferences(PREFS, 0).edit().putBoolean("sw_" + label, checked).apply());
            cb.setBackgroundColor(Color.WHITE);
            cb.setPadding(dp(12), dp(12), dp(12), dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            content.addView(cb, lp);
        }
    }

    private void buildComment() {
        EditText input = new EditText(this);
        input.setHint("写评论…");
        input.setTextSize(14);
        content.addView(input);
        final List<String> comments = new ArrayList<>(split("comments"));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
        Button send = new Button(this);
        send.setText("发送评论");
        send.setAllCaps(false);
        send.setOnClickListener(v -> {
            String t = input.getText().toString().trim();
            if (t.isEmpty()) return;
            comments.add(0, t);
            saveList("comments", comments);
            TextView tv = new TextView(this);
            tv.setText(t);
            tv.setTextSize(14);
            tv.setTextColor(0xff1d1f26);
            tv.setBackgroundColor(Color.WHITE);
            tv.setPadding(dp(12), dp(10), dp(12), dp(10));
            list.addView(tv, 0);
            input.setText("");
        });
        content.addView(send, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        for (String c : comments) {
            TextView tv = new TextView(this);
            tv.setText(c);
            tv.setTextSize(14);
            tv.setTextColor(0xff1d1f26);
            tv.setBackgroundColor(Color.WHITE);
            tv.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            list.addView(tv, lp);
        }
    }

    private void buildLogin() {
        EditText phone = new EditText(this);
        phone.setHint("手机号");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        content.addView(phone);
        EditText pwd = new EditText(this);
        pwd.setHint("密码");
        pwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView(pwd);
        Button login = new Button(this);
        login.setText("本地登录");
        login.setAllCaps(false);
        login.setOnClickListener(v -> {
            if (phone.getText().toString().trim().isEmpty()) { toast("请输入手机号"); return; }
            toast("登录成功（本地演示）");
            finish();
        });
        content.addView(login, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
    }

    private void buildNotices() {
        List<String> list = new ArrayList<>(bills);
        if (list.isEmpty()) list.add("暂无支付消息");
        buildInfo("支付消息", list.toArray(new String[0]));
    }


    private void buildHome() {
        boolean wechat = title.contains("微");
        content.setBackgroundColor(0xffededed);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(0xffededed);
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(0xff191919);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView h = new TextView(this);
        h.setText(wechat ? "微信" : title);
        h.setTextSize(17);
        h.setTextColor(0xff191919);
        h.setGravity(Gravity.CENTER);
        header.addView(h, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView search = new TextView(this);
        search.setText("🔍");
        search.setTextSize(18);
        search.setGravity(Gravity.CENTER);
        header.addView(search, new LinearLayout.LayoutParams(dp(44), dp(48)));
        TextView plus = new TextView(this);
        plus.setText("＋");
        plus.setTextSize(24);
        plus.setTextColor(0xff191919);
        plus.setGravity(Gravity.CENTER);
        header.addView(plus, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(header);
        View line = new View(this);
        line.setBackgroundColor(0xffd9d9d9);
        content.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        if (contacts.isEmpty()) { contacts.add("文件传输助手"); contacts.add("本地好友"); }
        for (int i = 0; i < contacts.size(); i++) {
            String c = contacts.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(Color.WHITE);
            row.setPadding(dp(12), dp(9), dp(12), dp(9));
            TextView avatar = new TextView(this);
            avatar.setText(c.substring(0, 1));
            avatar.setTextSize(18);
            avatar.setTextColor(Color.WHITE);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackgroundColor(i == 0 ? 0xff07c160 : 0xff5b8ff9);
            row.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(12), 0, 0, 0);
            TextView name = new TextView(this);
            name.setText(c);
            name.setTextSize(16);
            name.setTextColor(0xff191919);
            texts.addView(name);
            TextView preview = new TextView(this);
            preview.setText(i == 0 ? "你好，这是本地独立工具" : "消息只保存在本机");
            preview.setTextSize(13);
            preview.setTextColor(0xff999999);
            texts.addView(preview);
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView time = new TextView(this);
            time.setText("刚刚");
            time.setTextSize(11);
            time.setTextColor(0xffb2b2b2);
            row.addView(time);
            row.setOnClickListener(v -> LocalToolActivity.open(this, "chat", c));
            content.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View d = new View(this);
            d.setBackgroundColor(0xffe5e5e5);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            dlp.leftMargin = dp(72);
            content.addView(d, dlp);
        }
        for (String g : split("groups")) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(Color.WHITE);
            row.setPadding(dp(12), dp(9), dp(12), dp(9));
            TextView avatar = new TextView(this);
            avatar.setText("群");
            avatar.setTextSize(17);
            avatar.setTextColor(Color.WHITE);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackgroundColor(0xff07c160);
            row.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(12), 0, 0, 0);
            TextView name = new TextView(this);
            name.setText(g);
            name.setTextSize(16);
            name.setTextColor(0xff191919);
            texts.addView(name);
            TextView preview = new TextView(this);
            preview.setText("群聊消息只保存在本机");
            preview.setTextSize(13);
            preview.setTextColor(0xff999999);
            texts.addView(preview);
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(v -> LocalToolActivity.open(this, "group", g));
            content.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View d = new View(this);
            d.setBackgroundColor(0xffe5e5e5);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            dlp.leftMargin = dp(72);
            content.addView(d, dlp);
        }

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER);
        tabs.setBackgroundColor(0xfff7f7f7);
        tabs.setPadding(0, dp(6), 0, dp(4));
        String[][] tabDefs = {{"💬", "微信", "home"}, {"👥", "通讯录", "contacts"}, {"🧭", "发现", "discover"}, {"👤", "我", "profile"}};
        for (String[] td : tabDefs) {
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            boolean active = "微信".equals(td[1]);
            TextView icon = new TextView(this);
            icon.setText(td[0]);
            icon.setTextSize(20);
            icon.setGravity(Gravity.CENTER);
            tab.addView(icon);
            TextView label = new TextView(this);
            label.setText(td[1]);
            label.setTextSize(10);
            label.setTextColor(active ? 0xff07c160 : 0xff666666);
            label.setGravity(Gravity.CENTER);
            tab.addView(label);
            tab.setOnClickListener(v -> {
                if (!"home".equals(td[2])) LocalToolActivity.open(this, td[2], td[1]);
            });
            tabs.addView(tab, new LinearLayout.LayoutParams(0, dp(52), 1f));
        }
        content.addView(tabs);
    }

    private void buildChats() {
        TextView head = new TextView(this);
        head.setText("选择联系人开始聊天");
        head.setTextSize(14);
        head.setTextColor(0xff5b6070);
        head.setPadding(0, 0, 0, dp(8));
        content.addView(head);
        if (contacts.isEmpty()) { contacts.add("文件传输助手"); contacts.add("本地好友"); }
        for (String c : contacts) {
            TextView tv = new TextView(this);
            tv.setText(c);
            tv.setTextSize(15);
            tv.setTextColor(0xff1d1f26);
            tv.setBackgroundColor(Color.WHITE);
            tv.setPadding(dp(14), dp(16), dp(14), dp(16));
            tv.setOnClickListener(v -> LocalToolActivity.open(this, "chat", c));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            content.addView(tv, lp);
        }
        Button add = new Button(this);
        add.setText("＋ 新建聊天");
        add.setAllCaps(false);
        add.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setHint("联系人名称");
            new AlertDialog.Builder(this).setTitle("新建聊天").setView(input)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("开始", (d, w) -> {
                        String n = input.getText().toString().trim();
                        if (!n.isEmpty()) {
                            if (!contacts.contains(n)) { contacts.add(n); saveList("contacts", contacts); }
                            LocalToolActivity.open(this, "chat", n);
                        }
                    }).show();
        });
        content.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
    }

    private void buildBroadcast() {
        content.setBackgroundColor(0xffededed);
        content.setPadding(0, 0, 0, 0);
        final List<String> selected = new ArrayList<>();

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xffededed);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(43), dp(43)));
        TextView title = new TextView(this);
        title.setText("群发助手");
        title.setTextSize(16);
        title.setTextColor(0xff191919);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(43), 1f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(dp(43), dp(43)));
        content.addView(bar);

        if (contacts.isEmpty()) { contacts.add("文件传输助手"); contacts.add("本地好友"); }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        content.addView(card);
        TextView sec = new TextView(this);
        sec.setText("选择联系人");
        sec.setTextSize(12);
        sec.setTextColor(0xff888888);
        sec.setPadding(dp(15), dp(10), 0, dp(6));
        card.addView(sec);
        for (String c : contacts) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(15), dp(8), dp(15), dp(8));
            row.addView(assetIcon("ic_wx_default_image", 36), new LinearLayout.LayoutParams(dp(36), dp(36)));
            TextView name = new TextView(this);
            name.setText(c);
            name.setTextSize(15);
            name.setTextColor(0xff191919);
            name.setPadding(dp(12), 0, 0, 0);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setOnCheckedChangeListener((b, checked) -> {
                if (checked) { if (!selected.contains(c)) selected.add(c); }
                else selected.remove(c);
            });
            row.addView(cb, new LinearLayout.LayoutParams(dp(40), dp(40)));
            card.addView(row);
            View line = new View(this);
            line.setBackgroundColor(0xffe5e5e5);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            llp.leftMargin = dp(63);
            card.addView(line, llp);
        }

        LinearLayout msgCard = new LinearLayout(this);
        msgCard.setBackgroundColor(Color.WHITE);
        msgCard.setPadding(dp(15), dp(12), dp(15), dp(12));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(8);
        content.addView(msgCard, mlp);
        EditText msg = new EditText(this);
        msg.setHint("群发内容");
        msg.setTextSize(14);
        msg.setMinLines(2);
        msgCard.addView(msg, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView send = new TextView(this);
        send.setText("发送");
        send.setTextSize(16);
        send.setTextColor(Color.WHITE);
        send.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0xff07c160);
        g.setCornerRadius(dp(4));
        send.setBackground(g);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        slp.leftMargin = dp(15);
        slp.rightMargin = dp(15);
        slp.topMargin = dp(16);
        content.addView(send, slp);
        send.setOnClickListener(v -> {
            if (selected.isEmpty()) { toast("请选择联系人"); return; }
            String text = msg.getText().toString().trim();
            if (text.isEmpty()) { toast("请输入内容"); return; }
            List<String> log = new ArrayList<>(split("broadcast"));
            log.add(0, "群发给 " + selected.size() + " 人：" + text);
            saveList("broadcast", log);
            toast("群发完成（本地）");
            finish();
        });

        List<String> log = split("broadcast");
        if (!log.isEmpty()) {
            LinearLayout hist = new LinearLayout(this);
            hist.setOrientation(LinearLayout.VERTICAL);
            hist.setBackgroundColor(Color.WHITE);
            hist.setPadding(dp(15), dp(10), dp(15), dp(10));
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hlp.topMargin = dp(16);
            content.addView(hist, hlp);
            TextView h = new TextView(this);
            h.setText("群发记录");
            h.setTextSize(12);
            h.setTextColor(0xff888888);
            hist.addView(h);
            for (String l : log) {
                TextView tv = new TextView(this);
                tv.setText(l);
                tv.setTextSize(13);
                tv.setTextColor(0xff333333);
                tv.setPadding(0, dp(8), 0, dp(8));
                hist.addView(tv);
            }
        }
    }

    private void buildBankTransfer() {
        EditText card = new EditText(this);
        card.setHint("银行卡号");
        card.setInputType(InputType.TYPE_CLASS_NUMBER);
        content.addView(card);
        EditText name = new EditText(this);
        name.setHint("收款人姓名");
        content.addView(name);
        EditText amount = new EditText(this);
        amount.setHint("金额（元）");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        content.addView(amount);
        Button ok = new Button(this);
        ok.setText("确认转账到银行卡");
        ok.setAllCaps(false);
        ok.setOnClickListener(v -> {
            if (card.getText().toString().trim().length() < 8) { toast("请输入正确卡号"); return; }
            String a = amount.getText().toString().trim();
            if (a.isEmpty()) { toast("请输入金额"); return; }
            try {
                double v2 = Double.parseDouble(a);
                double bal = Double.parseDouble(getBalance());
                if (bal < v2) { toast("余额不足"); return; }
                getSharedPreferences(PREFS, 0).edit().putString("balance", String.format("%.2f", bal - v2)).apply();
                bills.add(0, "转账到银行卡 -¥" + String.format("%.2f", v2) + "  " + name.getText().toString().trim());
                saveList("bills", bills);
                getSharedPreferences(PREFS, 0).edit().putString("last_pay", "银行卡 · " + name.getText().toString().trim() + "|" + String.format("%.2f", v2)).apply();
                toast("转账成功（本地）");
                finish();
            } catch (Exception e) { toast("金额格式不正确"); }
        });
        content.addView(ok, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
    }

    private void buildProfile() {
        content.setBackgroundColor(0xffededed);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.WHITE);
        ImageView back = assetIcon("ic_wx_chat_title_back", 21);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(21), dp(21));
        blp.leftMargin = dp(13);
        bar.addView(back, blp);
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(43), 1f));
        content.addView(bar);

        LinearLayout user = new LinearLayout(this);
        user.setGravity(Gravity.CENTER_VERTICAL);
        user.setBackgroundColor(Color.WHITE);
        user.setPadding(dp(22), dp(15), dp(12), dp(15));
        ImageView avatar = assetIcon("ic_wx_default_image", 58);
        user.addView(avatar, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(13), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText("本地用户");
        name.setTextSize(20);
        name.setTextColor(0xff191919);
        info.addView(name);
        TextView wx = new TextView(this);
        wx.setText("微信号： local_user");
        wx.setTextSize(14);
        wx.setTextColor(0xff7f7f7f);
        wx.setPadding(0, dp(6), 0, 0);
        info.addView(wx);
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, dp(8), 0, 0);
        TextView status = new TextView(this);
        status.setText("+ 状态");
        status.setTextSize(13);
        status.setTextColor(0xff7f7f7f);
        status.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable st = new android.graphics.drawable.GradientDrawable();
        st.setColor(Color.WHITE);
        st.setCornerRadius(dp(12));
        st.setStroke(dp(1), 0xff7f7f7f);
        status.setBackground(st);
        statusRow.addView(status, new LinearLayout.LayoutParams(dp(64), dp(24)));
        TextView dots = new TextView(this);
        dots.setText("⋯");
        dots.setTextSize(20);
        dots.setTextColor(0xff7f7f7f);
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(30), dp(24));
        dlp.leftMargin = dp(7);
        statusRow.addView(dots, dlp);
        info.addView(statusRow);
        user.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView qr = assetIcon("ic_wx_qr", 15);
        user.addView(qr, new LinearLayout.LayoutParams(dp(15), dp(15)));
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(20);
        arrow.setTextColor(0xffc7c7cc);
        arrow.setGravity(Gravity.CENTER);
        user.addView(arrow, new LinearLayout.LayoutParams(dp(22), dp(58)));
        user.setOnClickListener(v -> LocalToolActivity.open(this, "profile", "我"));
        content.addView(user);

        addMeGap();
        content.addView(meRow("ic_wx_me_wallet", "服务", "service", true));
        addMeGap();
        content.addView(meRow("ic_wx_me_favor", "收藏", "favorites", false));
        content.addView(meDivider());
        content.addView(meRow("ic_wx_me_album", "朋友圈", "moments", false));
        content.addView(meDivider());
        content.addView(meRow("ic_wx_me_card_bg", "卡包", "cards", false));
        content.addView(meDivider());
        content.addView(meRow("ic_wx_me_brow", "表情", "stickers", true));
        addMeGap();
        content.addView(meRow("ic_wx_me_setting", "设置", "settings", true));
        content.addView(wechatTabBar("profile"));
    }

    private void addMeGap() {
        View line = new View(this);
        line.setBackgroundColor(0xffededed);
        content.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        View gap = new View(this);
        gap.setBackgroundColor(0xffededed);
        content.addView(gap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
    }

    private View meDivider() {
        View v = new View(this);
        v.setBackgroundColor(0xffe5e5e5);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.leftMargin = dp(54);
        v.setLayoutParams(lp);
        return v;
    }

    private LinearLayout meRow(String asset, String label, String key, boolean bottomGap) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.WHITE);
        row.setPadding(dp(15), 0, dp(12), 0);
        row.addView(assetIcon(asset, 25), new LinearLayout.LayoutParams(dp(25), dp(25)));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(16);
        tv.setTextColor(0xff191919);
        tv.setPadding(dp(14), 0, 0, 0);
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(20);
        arrow.setTextColor(0xffc7c7cc);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(22), dp(25)));
        row.setOnClickListener(v -> LocalToolActivity.open(this, key, label));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        return row;
    }

    private void buildShop() {
        final String[][] products = {{"示例商品 A", "9.90"}, {"示例商品 B", "19.90"}, {"示例商品 C", "29.90"}};
        TextView head = new TextView(this);
        head.setText("本地商城 · 下单只记账不联网");
        head.setTextSize(13);
        head.setTextColor(0xff8b90a0);
        head.setPadding(0, 0, 0, dp(8));
        content.addView(head);
        for (String[] pr : products) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(Color.WHITE);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);
            TextView name = new TextView(this);
            name.setText(pr[0] + "\n¥" + pr[1]);
            name.setTextSize(14);
            name.setTextColor(0xff1d1f26);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button buy = new Button(this);
            buy.setText("购买");
            buy.setAllCaps(false);
            buy.setOnClickListener(v -> buyProduct(pr[0], pr[1]));
            row.addView(buy, new LinearLayout.LayoutParams(dp(80), dp(44)));
            content.addView(row);
        }
    }

    private void buyProduct(String name, String price) {
        try {
            double p = Double.parseDouble(price);
            double bal = Double.parseDouble(getBalance());
            if (bal < p) { toast("余额不足"); return; }
            getSharedPreferences(PREFS, 0).edit().putString("balance", String.format("%.2f", bal - p)).apply();
            bills.add(0, "购买 " + name + " -¥" + String.format("%.2f", p));
            saveList("bills", bills);
            List<String> orders = new ArrayList<>(split("orders"));
            orders.add(0, "订单 " + System.currentTimeMillis() + "\n" + name + " · 待发货");
            saveList("orders", orders);
            getSharedPreferences(PREFS, 0).edit().putString("last_pay", "本地商城 · " + name + "|" + String.format("%.2f", p)).apply();
            toast("下单成功（本地）");
        } catch (Exception e) {
            toast("下单失败");
        }
    }

    private void buildMallWallet() {
        List<String> orders = split("orders");
        TextView card = new TextView(this);
        card.setText("商城钱包（本地）\n\n可用余额  ¥" + getBalance() + "\n订单数量  " + orders.size() + " 笔\n积分  0");
        card.setTextSize(17);
        card.setTextColor(0xff1d1f26);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        content.addView(card);
        Button ordersBtn = new Button(this);
        ordersBtn.setText("查看淘订单");
        ordersBtn.setAllCaps(false);
        ordersBtn.setOnClickListener(v -> LocalToolActivity.open(this, "order", "淘订单"));
        content.addView(ordersBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
    }

    private void buildOrders() {
        List<String> orders = new ArrayList<>(split("orders"));
        if (orders.isEmpty()) {
            orders.add("订单 20261021001\n\n本地示例订单 · 待发货");
            orders.add("订单 20261021002\n\n本地示例订单 · 已完成");
        }
        buildInfo("淘订单", orders.toArray(new String[0]));
    }


    private void buildVoice() {
        buildSwitches("语音播报", new String[]{"收款语音播报", "红包到账播报", "转账到账播报"});
        Button tryBtn = new Button(this);
        tryBtn.setText("试听语音播报");
        tryBtn.setAllCaps(false);
        tryBtn.setOnClickListener(v -> {
            try {
                final android.speech.tts.TextToSpeech tts = new android.speech.tts.TextToSpeech(this, status -> { });
                tts.setOnUtteranceProgressListener(null);
                tts.speak("微信收款五十元", android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "doutu_voice");
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> { try { tts.shutdown(); } catch (Exception ignored) { } }, 4000);
            } catch (Exception e) {
                toast("本机没有可用的语音引擎");
            }
        });
        content.addView(tryBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}