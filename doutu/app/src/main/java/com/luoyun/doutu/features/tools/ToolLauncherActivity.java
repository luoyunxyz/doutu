package com.luoyun.doutu.features.tools;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * 回归用深链接：am start 指定 key 直接打开对应本地工具页，
 * 避免在 Canvas 工具页上按坐标滚动导致误点。
 */
public final class ToolLauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String key = getIntent().getStringExtra("key");
        String title = getIntent().getStringExtra("title");
        if (key == null) key = "chat";
        if (title == null) title = key;
        Intent intent;
        String route = getIntent().getStringExtra("route");
        if (route != null) {
            // 回归用：直接给小微克隆页传站内路由，例如 --es route "#/wechat/wallet"
            intent = new Intent(this, com.luoyun.doutu.features.wei.WeiHomeActivity.class);
            intent.putExtra("fw_route", route);
        } else if ("wechat".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.wei.WeiHomeActivity.class);
        } else if ("wxbalance".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.wei.WeiHomeActivity.class);
            intent.putExtra("fw_route", "#/wechat/wallet/balance");
        } else if ("wxannual".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.wx.WxAnnualBillActivity.class);
        } else if ("wxservice".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.wx.WxReceiptAssistantActivity.class);
        } else if ("avatar".equals(key) || "auto".equals(key) || "ai".equals(key)) {
            intent = new Intent(this, ToolImageActivity.class);
            intent.putExtra("mode", key);
        } else if ("localavatars".equals(key)) {
            intent = new Intent(this, LocalAvatarsActivity.class);
        } else if ("edit".equals(key)) {
            intent = new Intent(this, EditPanelActivity.class);
        } else if ("tutorial".equals(key)) {
            intent = new Intent(this, ToolsTutorialActivity.class);
        } else if ("qqhome".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.qq.QqHomeActivity.class);
        } else if ("albumhome".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.album.AlbumHomeActivity.class);
        } else if ("teamcreate".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.album.CreateTeamActivity.class);
        } else if ("teammine".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.album.MyTeamActivity.class);
        } else if ("videohome".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.video.VideoHomeActivity.class);
        } else if ("dycomment".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.video.DyCommentActivity.class);
        } else if ("mass".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.wx.MassAssistantActivity.class);
        } else if ("uploadimg".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.wx.UploadImageActivity.class);
        } else if ("sentrp".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.wx.SentRedPacketActivity.class);
        } else if ("qqrp".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.qq.QqRedPacketActivity.class);
        } else if ("qqedit".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.qq.QqEditProfileActivity.class);
        } else if ("qqunread".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.qq.QqHomeActivity.class);
            intent.putExtra("unread", true);
        } else if ("qqnewchat".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.qq.QqHomeActivity.class);
            intent.putExtra("newchat", true);
        } else if ("qqtransfer".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.qq.QqTransferActivity.class);
        } else if ("alitransfer".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.qq.QqTransferActivity.class);
            intent.putExtra("ali", true);
        } else if ("alicontacts".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.ali.AliContactsActivity.class);
        } else if ("aliassets".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.ali.AliAssetsActivity.class);
        } else if ("alibalance".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.ali.AliBalanceActivity.class);
        } else if ("aliwithdraw".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.ali.AliWithdrawActivity.class);
        } else if ("alimine".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.ali.AliMineActivity.class);
        } else if ("alired".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.ali.AliRedPacketActivity.class);
        } else if ("alimsg".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.ali.AliMsgActivity.class);
        } else if ("qqgz".equals(key)) {
            intent = new Intent(this, com.luoyun.doutu.features.qq.QqHomeActivity.class);
            intent.putExtra("gzh", true);
        } else if ("adjust".equals(key)) {
            try (java.io.InputStream in = getAssets().open("fake_world/assets/user-avatar-1-CKPDcH57.jpg")) {
                AvatarAdjustActivity.source = android.graphics.BitmapFactory.decodeStream(in);
            } catch (Exception ignored) { }
            intent = new Intent(this, AvatarAdjustActivity.class);
        } else {
            intent = new Intent(this, LocalToolActivity.class);
            intent.putExtra("key", key);
            intent.putExtra("title", title);
        }
        startActivity(intent);
        finish();
    }
}
