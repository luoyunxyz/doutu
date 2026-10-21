package com.luoyun.doutu.features.wx;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.io.InputStream;
import java.util.Random;

/**
 * 「创建单聊」弹窗 —— 覆盖在微信首页之上：
 * 自动获取 / 头像预览 / 从相册获取 + 昵称输入 + 「是否为新好友」开关 + 取消 / 确定。
 * 确定后进入打招呼的聊天页。
 */
public final class WxCreateChatActivity extends Activity {
    private static final String[] NAMES = {"残阳€", "北方佳人.", "明晨紫月", "淡紫姑娘！", "云边小卖部", "日落收藏家"};
    private static final String[] AVATARS = {
            "fake_world/assets/user-avatar-1-CKPDcH57.jpg",
            "fake_world/assets/user-avatar-4-Cmbgep_s.jpg",
            "fake_world/assets/user-avatar-5-DMigR-cn.jpg",
            "fake_world/assets/user-avatar-6-Cq95zrQT.jpg",
            "fake_world/assets/user-avatar-7-C4bh6bYx.jpg",
            "fake_world/assets/user-avatar-8-BysMeksY.jpg",
    };

    private final Random random = new Random();
    private ImageView preview;
    private EditText nameInput;
    private String avatarPath = AVATARS[0];
    /** 接口返回的头像网络地址；为空时用内置头像。 */
    private String avatarUrl;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(0x66000000);

        FrameLayout root = new FrameLayout(this);
        View scrim = new View(this);
        scrim.setBackgroundColor(0x8c000000);
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(Color.WHITE);
        cbg.setCornerRadius(dp(12));
        card.setBackground(cbg);
        card.setPadding(dp(20), dp(24), dp(20), dp(0));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(24);
        clp.rightMargin = dp(24);
        clp.topMargin = dp(150);
        root.addView(card, clp);

        // 头像行：自动获取 | 预览 | 从相册获取
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER);
        head.addView(sideAction("📅", "自动获取", v -> randomAvatar()));
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(96), dp(96));
        plp.leftMargin = dp(22);
        plp.rightMargin = dp(22);
        head.addView(preview, plp);
        head.addView(sideAction("🖼", "从相册获取", v -> pickAvatar()));
        card.addView(head);

        // 昵称
        nameInput = new EditText(this);
        nameInput.setText(NAMES[random.nextInt(NAMES.length)]);
        nameInput.setTextSize(15);
        nameInput.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable nbg = new GradientDrawable();
        nbg.setColor(Color.WHITE);
        nbg.setStroke(dp(1), 0xffdddddd);
        nbg.setCornerRadius(dp(6));
        nameInput.setBackground(nbg);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        nlp.topMargin = dp(26);
        card.addView(nameInput, nlp);

        // 是否为新好友
        LinearLayout toggleRow = new LinearLayout(this);
        toggleRow.setGravity(Gravity.CENTER_VERTICAL);
        toggleRow.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(Color.WHITE);
        tbg.setStroke(dp(1), 0xffdddddd);
        tbg.setCornerRadius(dp(6));
        toggleRow.setBackground(tbg);
        TextView label = new TextView(this);
        label.setText("是否为新好友");
        label.setTextSize(15);
        label.setTextColor(0xff333333);
        toggleRow.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final boolean[] isNewValue = {true};
        FrameLayout toggle = customToggle(isNewValue);
        toggleRow.addView(toggle);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        tlp.topMargin = dp(14);
        card.addView(toggleRow, tlp);

        // 取消 / 确定
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        blp.topMargin = dp(20);
        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextSize(16);
        cancel.setTextColor(0xff666666);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> finish());
        TextView ok = new TextView(this);
        ok.setText("确定");
        ok.setTextSize(16);
        ok.setTextColor(0xff333333);
        ok.setGravity(Gravity.CENTER);
        ok.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) name = "残阳€";
            String friendId = "wx" + System.currentTimeMillis();
            String avatar = (avatarUrl != null && avatarUrl.startsWith("http"))
                    ? avatarUrl : "https://cdn-fakeworld.azureedge.net/fakeworld/ppf3dl.jpg";
            String safe = name.replace("\\", "\\\\").replace("'", "\\'");
            String js = "(function(){try{"
                    + "var p=JSON.parse(localStorage.getItem('allProfiles')||'[]');"
                    + "p.push({id:'" + friendId + "',nickname:'" + safe + "',avatarInfo:'" + avatar + "',"
                    + "wechat:'" + friendId + "',gender:'female',area:'',signature:'',privacy:'all'});"
                    + "localStorage.setItem('allProfiles',JSON.stringify(p));"
                    + "var s=JSON.parse(localStorage.getItem('fake-world.wechat.chat-actions.v1')||'{}');"
                    + "s.chats=s.chats||{};s.chats['" + safe + "']={unreadCount:0,unread:false,unreadOverride:true};"
                    + "localStorage.setItem('fake-world.wechat.chat-actions.v1',JSON.stringify(s));"
                    + "return true;}catch(e){return false;}})()";
            // 走克隆「添加朋友」同一条落库路径（__fwAddFriend → state.contacts + saveState），
            // 加完后回到通讯录，结果和手动添加好友完全一致
            String addJs = "window.__fwAddFriend && window.__fwAddFriend('" + safe + "','" + avatar + "')";
            startActivity(com.luoyun.doutu.features.wei.WeiHomeActivity
                    .intentFor(this, "#/wechat/contacts"));
            com.luoyun.doutu.features.wei.WeiHomeActivity.scheduleBridge(addJs, "#/wechat/contacts");
            finish();
        });
        buttons.addView(cancel, new LinearLayout.LayoutParams(0, dp(56), 1f));
        buttons.addView(ok, new LinearLayout.LayoutParams(0, dp(56), 1f));
        card.addView(buttons, blp);

        setContentView(root);
        randomAvatar();
    }

    /** 黄色 iOS 风格开关（真机就是这种样式）。 */
    private FrameLayout customToggle(final boolean[] value) {
        final FrameLayout box = new FrameLayout(this);
        final View track = new View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xffffda43);
        bg.setCornerRadius(dp(16));
        track.setBackground(bg);
        box.addView(track, new FrameLayout.LayoutParams(dp(52), dp(32)));
        final View knob = new View(this);
        GradientDrawable kbg = new GradientDrawable();
        kbg.setColor(Color.WHITE);
        kbg.setShape(GradientDrawable.OVAL);
        knob.setBackground(kbg);
        FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(dp(28), dp(28));
        klp.leftMargin = dp(22);
        klp.topMargin = dp(2);
        box.addView(knob, klp);
        box.setOnClickListener(v -> {
            value[0] = !value[0];
            GradientDrawable g = new GradientDrawable();
            g.setColor(value[0] ? 0xffffda43 : 0xffe0e0e0);
            g.setCornerRadius(dp(16));
            track.setBackground(g);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) knob.getLayoutParams();
            lp.leftMargin = dp(value[0] ? 22 : 2);
            knob.setLayoutParams(lp);
        });
        return box;
    }

    private LinearLayout sideAction(String icon, String text, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setOnClickListener(click);
        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(22);
        iconView.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xfffff7d6);
        bg.setShape(GradientDrawable.OVAL);
        iconView.setBackground(bg);
        box.addView(iconView, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(12);
        label.setTextColor(0xff333333);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(8), 0, 0);
        box.addView(label);
        return box;
    }

    /** 自动获取：调用 xxapi 随机头像接口，失败时退回内置头像。 */
    private void randomAvatar() {
        avatarPath = null;
        new Thread(() -> {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                        new java.net.URL("https://v2.xxapi.cn/api/head").openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "okhttp/3.12.0");
                if (conn.getResponseCode() == 200) {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    try (InputStream in = conn.getInputStream()) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                    }
                    String body = bos.toString("UTF-8");
                    String imgUrl = new org.json.JSONObject(body).optString("data", "");
                    if (imgUrl.startsWith("http")) {
                        java.net.HttpURLConnection c2 = (java.net.HttpURLConnection)
                                new java.net.URL(imgUrl).openConnection();
                        c2.setConnectTimeout(8000);
                        c2.setReadTimeout(8000);
                        if (c2.getResponseCode() == 200) {
                            final Bitmap bmp;
                            try (InputStream in2 = c2.getInputStream()) {
                                bmp = BitmapFactory.decodeStream(in2);
                            }
                            if (bmp != null) {
                                avatarUrl = imgUrl;
                                runOnUiThread(() -> preview.setImageBitmap(bmp));
                                return;
                            }
                        }
                    }
                }
            } catch (Exception ignored) { }
            runOnUiThread(this::localRandomAvatar);
        }, "wx-avatar-api").start();
    }

    private void localRandomAvatar() {
        avatarPath = AVATARS[random.nextInt(AVATARS.length)];
        avatarUrl = null;
        loadAvatar();
    }

    private void loadAvatar() {
        try (InputStream in = getAssets().open(avatarPath)) {
            preview.setImageBitmap(BitmapFactory.decodeStream(in));
        } catch (Exception ignored) { }
    }

    private void pickAvatar() {
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("image/*");
        startActivityForResult(Intent.createChooser(pick, "选择头像"), 31);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 31 || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream in = getContentResolver().openInputStream(data.getData())) {
            Bitmap bmp = BitmapFactory.decodeStream(in);
            if (bmp != null) preview.setImageBitmap(bmp);
        } catch (Exception ignored) { }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
