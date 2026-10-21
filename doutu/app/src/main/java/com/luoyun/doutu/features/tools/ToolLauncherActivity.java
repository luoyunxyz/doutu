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
        } else if ("replacetest".equals(key)) {
            // 测试用：拼一张「同一个头像重复 4 次」的底图，直接进替换页验证自动全部替换
            try {
                android.graphics.Bitmap src = android.graphics.BitmapFactory.decodeStream(
                        getAssets().open("video_icons/avatar.png"));
                android.graphics.Bitmap dst = android.graphics.BitmapFactory.decodeStream(
                        getAssets().open("tools_icons/me.png"));
                android.graphics.Bitmap canvasBmp = android.graphics.Bitmap.createBitmap(600, 600,
                        android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas cv = new android.graphics.Canvas(canvasBmp);
                cv.drawColor(0xfff2f2f2);
                int[] xs = {20, 320, 20, 320};
                int[] ys = {20, 20, 320, 320};
                for (int i = 0; i < 4; i++) {
                    cv.drawBitmap(src, null,
                            new android.graphics.Rect(xs[i], ys[i], xs[i] + 240, ys[i] + 240), null);
                }
                com.luoyun.doutu.features.tools.ReplaceHeaderListActivity.base = canvasBmp;
                com.luoyun.doutu.features.tools.ReplaceHeaderListActivity.avatar = dst;
                com.luoyun.doutu.features.tools.ReplaceHeaderListActivity.initialSlot =
                        new android.graphics.RectF(20, 20, 260, 260);
                intent = new Intent(this, com.luoyun.doutu.features.tools.ReplaceHeaderListActivity.class);
            } catch (Exception e) {
                intent = new Intent(this, com.luoyun.doutu.features.tools.ReplaceHeaderListActivity.class);
            }
        } else if ("avataradjust".equals(key)) {
            // 测试用：直接进「替换头像」选框页（用内置示例图当底图）
            try (java.io.InputStream in = getAssets().open("video_icons/avatar.png")) {
                com.luoyun.doutu.features.tools.AvatarAdjustActivity.source =
                        android.graphics.BitmapFactory.decodeStream(in);
            } catch (Exception ignored) { }
            intent = new Intent(this, com.luoyun.doutu.features.tools.AvatarAdjustActivity.class);
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
