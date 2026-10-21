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
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(20));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        build();
    }

    private void build() {
        if ("home".equals(key)) buildHome();
        else if ("chats".equals(key)) buildChats();
        else if ("broadcast".equals(key)) buildBroadcast();
        else if ("banktransfer".equals(key)) buildBankTransfer();
        else if ("profile".equals(key)) buildProfile();
        else if ("chat".equals(key)) buildChat();
        else if ("wallet".equals(key)) buildWallet();
        else if ("redpacket".equals(key) || "transfer".equals(key)) buildPay();
        else if ("contacts".equals(key)) buildContacts();
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
        chatList = new LinearLayout(this);
        chatList.setOrientation(LinearLayout.VERTICAL);
        content.addView(chatList);
        for (String m : messages) addBubble(m, true);
        if (messages.isEmpty()) addBubble("你好，这是本地聊天工具，消息只保存在本机。", false);
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(0, dp(12), 0, 0);
        EditText input = new EditText(this);
        input.setHint("输入消息");
        input.setTextSize(14);
        inputRow.addView(input, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button send = new Button(this);
        send.setText("发送");
        send.setAllCaps(false);
        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            messages.add(text);
            save();
            addBubble(text, true);
            input.setText("");
        });
        inputRow.addView(send, new LinearLayout.LayoutParams(dp(76), dp(48)));
        content.addView(inputRow);
    }

    private void addBubble(String text, boolean mine) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(mine ? Color.WHITE : 0xff1d1f26);
        tv.setBackgroundColor(mine ? 0xff4a7bf7 : Color.WHITE);
        tv.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        row.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (chatList != null) chatList.addView(row, lp);
    }

    private void buildWallet() {
        final TextView card = new TextView(this);
        card.setText("本机零钱\n¥ " + getBalance());
        card.setTextSize(18);
        card.setTextColor(Color.WHITE);
        card.setBackgroundColor(0xff4a7bf7);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        content.addView(card);
        Button recharge = new Button(this);
        recharge.setText("＋ 充值 ¥100（本地）");
        recharge.setAllCaps(false);
        recharge.setOnClickListener(v -> {
            try {
                double bal = Double.parseDouble(getBalance()) + 100;
                getSharedPreferences(PREFS, 0).edit().putString("balance", String.format("%.2f", bal)).apply();
                bills.add(0, "充值 +¥100.00");
                saveList("bills", bills);
                card.setText("本机零钱\n¥ " + getBalance());
                renderBills();
            } catch (Exception ignored) { }
        });
        content.addView(recharge, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        TextView t = new TextView(this);
        t.setText("收支明细");
        t.setTextSize(14);
        t.setTextColor(0xff5b6070);
        t.setPadding(0, dp(18), 0, dp(8));
        content.addView(t);
        billList = new LinearLayout(this);
        billList.setOrientation(LinearLayout.VERTICAL);
        content.addView(billList);
        renderBills();
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
        boolean red = "redpacket".equals(key);
        TextView tip = new TextView(this);
        tip.setText(red ? "发红包（本地模拟）" : "转账（本地模拟）");
        tip.setTextSize(15);
        tip.setTextColor(0xff1d1f26);
        content.addView(tip);
        EditText name = new EditText(this);
        name.setHint(red ? "红包祝福语" : "对方姓名");
        name.setTextSize(14);
        content.addView(name);
        EditText amount = new EditText(this);
        amount.setHint("金额（元）");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setTextSize(14);
        content.addView(amount);
        Button ok = new Button(this);
        ok.setText(red ? "塞钱进红包" : "确认转账");
        ok.setAllCaps(false);
        ok.setOnClickListener(v -> {
            String a = amount.getText().toString().trim();
            if (a.isEmpty()) { toast("请输入金额"); return; }
            try {
                double v2 = Double.parseDouble(a);
                double bal = Double.parseDouble(getBalance());
                bal -= v2;
                getSharedPreferences(PREFS, 0).edit().putString("balance", String.format("%.2f", bal)).apply();
                bills.add(0, (red ? "红包 -¥" : "转账 -¥") + String.format("%.2f", v2) + "  " + name.getText().toString().trim());
                saveList("bills", bills);
                getSharedPreferences(PREFS, 0).edit().putString("last_pay", (red ? "红包 · " : "转账 · ") + name.getText().toString().trim() + "|" + String.format("%.2f", v2)).apply();
                toast(red ? "红包已发出（本地）" : "转账成功（本地）");
                finish();
            } catch (Exception e) { toast("金额格式不正确"); }
        });
        content.addView(ok, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        TextView bal = new TextView(this);
        bal.setText("当前零钱：¥ " + getBalance());
        bal.setTextSize(13);
        bal.setTextColor(0xff8b90a0);
        bal.setPadding(0, dp(10), 0, 0);
        content.addView(bal);
    }

    private void buildContacts() {
        contactList = new LinearLayout(this);
        contactList.setOrientation(LinearLayout.VERTICAL);
        content.addView(contactList);
        Button add = new Button(this);
        add.setText("＋ 添加联系人");
        add.setAllCaps(false);
        add.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setHint("姓名");
            new AlertDialog.Builder(this).setTitle("添加联系人").setView(input)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("添加", (d, w) -> {
                        String n = input.getText().toString().trim();
                        if (!n.isEmpty()) { contacts.add(n); saveList("contacts", contacts); renderContacts(); }
                    }).show();
        });
        content.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        if (contacts.isEmpty()) { contacts.add("文件传输助手"); contacts.add("本地好友"); }
        renderContacts();
    }

    private void renderContacts() {
        if (contactList == null) return;
        contactList.removeAllViews();
        for (String c : contacts) {
            TextView tv = new TextView(this);
            tv.setText(c);
            tv.setTextSize(15);
            tv.setTextColor(0xff1d1f26);
            tv.setBackgroundColor(Color.WHITE);
            tv.setPadding(dp(14), dp(14), dp(14), dp(14));
            tv.setOnClickListener(v -> LocalToolActivity.open(this, "chat", c));
            tv.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setTitle("删除联系人").setMessage(c)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (d, w) -> {
                            contacts.remove(c);
                            saveList("contacts", contacts);
                            renderContacts();
                        }).show();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            contactList.addView(tv, lp);
        }
    }

    private void buildMoments() {
        EditText post = new EditText(this);
        post.setHint("这一刻的想法…");
        post.setTextSize(14);
        content.addView(post);
        Button send = new Button(this);
        send.setText("发表");
        send.setAllCaps(false);
        send.setOnClickListener(v -> {
            String t = post.getText().toString().trim();
            if (t.isEmpty()) return;
            moments.add(0, t);
            saveList("moments", moments);
            post.setText("");
            renderMoments();
        });
        content.addView(send, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        momentList = new LinearLayout(this);
        momentList.setOrientation(LinearLayout.VERTICAL);
        content.addView(momentList);
        renderMoments();
    }

    private void renderMoments() {
        if (momentList == null) return;
        momentList.removeAllViews();
        if (moments.isEmpty()) moments.add("本地朋友圈：发一条只保存在本机的动态。");
        for (String m : moments) {
            TextView tv = new TextView(this);
            tv.setText(m);
            tv.setTextSize(14);
            tv.setTextColor(0xff1d1f26);
            tv.setBackgroundColor(Color.WHITE);
            tv.setPadding(dp(14), dp(14), dp(14), dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            momentList.addView(tv, lp);
        }
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

    private void buildPayPage() {
        TextView qr = new TextView(this);
        qr.setText("▦ 收款码\n\n本地收款 · 不联网");
        qr.setTextSize(18);
        qr.setTextColor(0xff1d1f26);
        qr.setGravity(Gravity.CENTER);
        qr.setBackgroundColor(Color.WHITE);
        qr.setPadding(0, dp(60), 0, dp(60));
        content.addView(qr);
        TextView bal = new TextView(this);
        bal.setText("当前零钱：¥ " + getBalance());
        bal.setTextSize(14);
        bal.setTextColor(0xff5b6070);
        bal.setPadding(0, dp(14), 0, 0);
        content.addView(bal);
        Button receive = new Button(this);
        receive.setText("模拟收款 ¥50（本地）");
        receive.setAllCaps(false);
        receive.setOnClickListener(v -> {
            try {
                double newBal = Double.parseDouble(getBalance()) + 50;
                getSharedPreferences(PREFS, 0).edit().putString("balance", String.format("%.2f", newBal)).apply();
                bills.add(0, "收款 +¥50.00");
                saveList("bills", bills);
                bal.setText("当前零钱：¥ " + getBalance());
            } catch (Exception ignored) { }
        });
        content.addView(receive, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        Button save = new Button(this);
        save.setText("保存收款码");
        save.setAllCaps(false);
        save.setOnClickListener(v -> toast("收款码已保存到本机（演示）"));
        content.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
    }

    private void buildBillStats() {
        double total = 0;
        for (String b : bills) {
            int i = b.indexOf("¥");
            if (i >= 0) {
                int j = i + 1;
                while (j < b.length() && (Character.isDigit(b.charAt(j)) || b.charAt(j) == '.')) j++;
                try { total += Double.parseDouble(b.substring(i + 1, j)); } catch (Exception ignored) { }
            }
        }
        TextView tv = new TextView(this);
        tv.setText("年度账单（本地）\n\n共 " + bills.size() + " 笔记录\n合计 ¥" + String.format("%.2f", total));
        tv.setTextSize(18);
        tv.setTextColor(0xff1d1f26);
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundColor(Color.WHITE);
        tv.setPadding(0, dp(50), 0, dp(50));
        content.addView(tv);
    }

    private void buildRedPackets() {
        List<String> red = new ArrayList<>();
        for (String b : bills) if (b.contains("红包")) red.add(b);
        if (red.isEmpty()) red.add("还没有发出红包");
        buildInfo("已发红包", red.toArray(new String[0]));
    }

    private void buildSuccess() {
        String raw = getSharedPreferences(PREFS, 0).getString("last_pay", "本地支付|0.00");
        String[] parts = raw.split("\\|");
        String merchant = parts.length > 0 ? parts[0] : "本地支付";
        String amount = parts.length > 1 ? parts[1] : "0.00";
        TextView tv = new TextView(this);
        tv.setText("✓\n\n¥ " + amount + "\n\n" + merchant + "\n\n支付成功（本地）");
        tv.setTextSize(20);
        tv.setTextColor(0xff07c160);
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundColor(Color.WHITE);
        tv.setPadding(0, dp(45), 0, dp(45));
        content.addView(tv);
        Button done = new Button(this);
        done.setText("完成");
        done.setAllCaps(false);
        done.setOnClickListener(v -> finish());
        content.addView(done, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
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
        TextView head = new TextView(this);
        head.setText(title + "\n本地独立模块");
        head.setTextSize(18);
        head.setTextColor(0xff1d1f26);
        head.setPadding(0, dp(6), 0, dp(14));
        content.addView(head);
        String[][] rows = {{"聊天", "chat"}, {"通讯录", "contacts"}, {"朋友圈", "moments"}, {"零钱", "wallet"}, {"账单", "billstats"}, {"设置", "settings"}};
        for (String[] r : rows) {
            TextView tv = new TextView(this);
            tv.setText(r[0] + "  ›");
            tv.setTextSize(15);
            tv.setTextColor(0xff1d1f26);
            tv.setBackgroundColor(Color.WHITE);
            tv.setPadding(dp(16), dp(16), dp(16), dp(16));
            tv.setOnClickListener(v -> LocalToolActivity.open(this, r[1], r[0]));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            content.addView(tv, lp);
        }
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
        final List<String> selected = new ArrayList<>();
        TextView head = new TextView(this);
        head.setText("选择要群发的联系人和内容（本地）");
        head.setTextSize(14);
        head.setTextColor(0xff5b6070);
        content.addView(head);
        if (contacts.isEmpty()) { contacts.add("文件传输助手"); contacts.add("本地好友"); }
        for (String c : contacts) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(c);
            cb.setTextSize(14);
            cb.setOnCheckedChangeListener((b, checked) -> {
                if (checked) { if (!selected.contains(c)) selected.add(c); }
                else selected.remove(c);
            });
            content.addView(cb);
        }
        EditText msg = new EditText(this);
        msg.setHint("群发内容");
        msg.setTextSize(14);
        content.addView(msg);
        Button send = new Button(this);
        send.setText("发送群发");
        send.setAllCaps(false);
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
        content.addView(send, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        List<String> log = split("broadcast");
        if (!log.isEmpty()) {
            TextView h = new TextView(this);
            h.setText("群发记录");
            h.setTextSize(13);
            h.setTextColor(0xff8b90a0);
            h.setPadding(0, dp(14), 0, dp(6));
            content.addView(h);
            for (String l : log) {
                TextView tv = new TextView(this);
                tv.setText(l);
                tv.setTextSize(13);
                tv.setTextColor(0xff333333);
                tv.setBackgroundColor(Color.WHITE);
                tv.setPadding(dp(10), dp(8), dp(10), dp(8));
                content.addView(tv);
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
        TextView card = new TextView(this);
        card.setText("本地用户\n\n零钱  ¥" + getBalance() + "\n手机号  138****0000");
        card.setTextSize(17);
        card.setTextColor(0xff1d1f26);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        content.addView(card);
        String[] rows = {"我的账单", "总资产", "设置"};
        for (String r : rows) {
            TextView tv = new TextView(this);
            tv.setText(r + "  ›");
            tv.setTextSize(15);
            tv.setTextColor(0xff1d1f26);
            tv.setBackgroundColor(Color.WHITE);
            tv.setPadding(dp(16), dp(16), dp(16), dp(16));
            String k = r.contains("账单") ? "billstats" : r.contains("资产") ? "assets" : "settings";
            tv.setOnClickListener(v -> LocalToolActivity.open(this, k, r));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            content.addView(tv, lp);
        }
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
            orders.add(0, "订单 " + System.currentTimeMillis() + "\\n" + name + " · 待发货");
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
        card.setText("商城钱包（本地）\\n\\n可用余额  ¥" + getBalance() + "\\n订单数量  " + orders.size() + " 笔\\n积分  0");
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
            orders.add("订单 20261021001\\n\\n本地示例订单 · 待发货");
            orders.add("订单 20261021002\\n\\n本地示例订单 · 已完成");
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}