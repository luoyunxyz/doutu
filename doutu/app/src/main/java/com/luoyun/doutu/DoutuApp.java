package com.luoyun.doutu;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.luoyun.doutu.account.AvatarStore;
import com.luoyun.doutu.account.Session;
import com.luoyun.doutu.account.UpdatePrompt;
import com.luoyun.doutu.account.WatermarkOverlay;
import com.luoyun.doutu.features.zhi.data.AdvisorStore;
import com.luoyun.doutu.features.zhi.data.AssetStore;
import com.luoyun.doutu.features.zhi.data.Accounts;
import com.luoyun.doutu.features.zhi.data.BalanceStore;
import com.luoyun.doutu.features.zhi.data.HuabeiStore;
import com.luoyun.doutu.features.zhi.data.LicaiVersion;
import com.luoyun.doutu.features.zhi.data.MeStore;
import com.luoyun.doutu.features.zhi.data.ProfileStore;
import com.luoyun.doutu.features.zhi.data.PurseStore;
import com.luoyun.doutu.features.zhi.data.PayMsgStore;
import com.luoyun.doutu.features.zhi.data.PaySuccessStore;
import com.luoyun.doutu.features.zhi.data.ReceiptStore;
import com.luoyun.doutu.features.zhi.data.WithdrawStore;
import com.luoyun.doutu.features.zhi.data.YuebaoStore;
import com.luoyun.doutu.features.zhi.data.ZhiHomeStore;
import com.luoyun.doutu.features.pay.data.CollectStore;
import com.luoyun.doutu.features.travel.data.TravelStore;
import com.luoyun.doutu.features.zhima.data.ZhimaStore;

/**
 * The process entry point. The pages draw from their stores rather than carrying state of their
 * own, so the stores that keep something for longer than a run are handed the context they persist
 * through here, before any page is opened — the 我的 tab's profile and the 总资产 page's edited
 * figures are the ones that do.
 */
public final class DoutuApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        guardAgainstRepackaging();
        // 账号表要第一个挂上：别的仓库问它要存档后缀，谁在哪个账号下得先定下来
        Accounts.attach(this);
        ProfileStore.attach(this);
        AdvisorStore.attach(this);
        AssetStore.attach(this);
        BalanceStore.attach(this);
        YuebaoStore.attach(this);
        HuabeiStore.attach(this);
        PayMsgStore.attach(this);
        PaySuccessStore.attach(this);
        ReceiptStore.attach(this);
        WithdrawStore.attach(this);
        PurseStore.attach(this);
        LicaiVersion.attach(this);
        // 2026-10-16：这几个页面原来把用户改的东西只放在内存里（杀掉后台再打开就没了），
        // 现在各留一份存档：小支主页的可编辑文案 / 我的页的完成身份条 / 芝麻信用的文案与模块板 /
        // 出行页的地址与两座城市 / 收钱页的名字与相册换来的码。
        ZhiHomeStore.attach(this);
        MeStore.attach(this);
        ZhimaStore.attach(this);
        TravelStore.attach(this);
        CollectStore.attach(this);
        // 模拟来电的设置（来电人 / 头像 / 铃声 / 什么时候响）
        com.luoyun.doutu.features.call.CallStore.attach(this);
        // 模拟收款声音的设置（小支/小微、金额、倍速、延时、随机区间）
        com.luoyun.doutu.features.sound.SoundStore.attach(this);
        // 淘宝订单详情那一页的数据（地址 / 收货人 / 店铺 / 商品 / 金额 / 订单号 / 推荐位）
        com.luoyun.doutu.features.order.OrderStore.attach(this);
        // 娱乐页那几个开关（「撩妹加V」= 扫一扫装作摄像头坏了）
        com.luoyun.doutu.features.play.PlayStore.attach(this);

        // 账号会话：登录态与「是否已激活」都来自服务端，这里只把持久化入口交给它
        Session.attach(this);
        AvatarStore.attach(this);

        // 未激活会员时，给**每一个**页面铺一层斜向水印。
        // 放在 Application 里而不是各个 Activity 里，是为了不遗漏任何子页面（本项目有 20 多个 Activity）。
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle saved) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                // 首次启动的《用户须知与免责声明》不铺水印：
                // 那是用户还没同意条款之前的法律告知页，压一层「开通会员去除水印」既不合适也不严肃。
                if (!(activity instanceof TermsActivity)) {
                    WatermarkOverlay.attachIfNeeded(activity);
                }
                // 版本推送：挂在同一个地方，理由也一样 —— 不遗漏任何页面。
                // 只在 MainActivity 检查的话，落地页是微信/支付宝时（回前台的是那一页）
                // 用户切回 App 永远看不到升级弹窗，运营那边就是「推送不起作用」。
                //
                // 2026-10-17：**但用户须知页上不弹** —— 页面要求「滑动阅读至最底部」，
                // 弹窗会盖在正文上把滚动手势一起吃掉，用户卡在这一页出不去（实测踩到过）。
                // 用户还没同意条款，也还谈不上「升级」。等进了主界面自然会检查。
                if (!(activity instanceof TermsActivity)) {
                    UpdatePrompt.maybeCheck(activity);
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    /**
     * 防二次打包（2026-10-17）。
     *
     * <p>包被重新签过 = 它被拆开改过：直接拒绝启动，没有误伤可言（正常用户不会重签）。
     * 签名证书的 SHA-256 是编译期注入的**公钥信息**，改包的人没有我们的私钥，换不了。
     *
     * <p>root / 调试 / 模拟器这些**只记日志**，不拦：不少正常用户就是 root 机，
     * 因为环境信号拒绝服务会误伤真实用户，得不偿失。
     */
    private void guardAgainstRepackaging() {
        try {
            java.util.List<String> signals = com.luoyun.doutu.account.dtw.TamperGuard.environmentSignals(this);
            if (!signals.isEmpty()) {
                android.util.Log.i("DoutuApp", "环境信号（仅记录，不拦截）：" + signals);
            }
            String reason = com.luoyun.doutu.account.dtw.TamperGuard.verifySignature(this);
            if (reason != null) {
                android.util.Log.e("DoutuApp", "签名校验未通过：" + reason);
                android.widget.Toast.makeText(this, "应用未通过完整性校验，请从官方渠道重新安装", android.widget.Toast.LENGTH_LONG).show();
                // 进程直接退出：被改过的包不该继续跑我们的业务逻辑
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
        } catch (Throwable err) {
            // 校验自身出问题（个别 ROM 的 PackageManager 行为差异）时不能把正常用户挡在门外
            android.util.Log.w("DoutuApp", "签名校验异常，按放行处理：" + err);
        }
    }
}
