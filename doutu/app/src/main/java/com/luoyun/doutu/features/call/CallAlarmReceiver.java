package com.luoyun.doutu.features.call;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 到点了系统叫醒的就是它。
 *
 * <p>两件事，各做一遍：
 *   1. 直接把「小微主页」的来电界面拉起来 —— 应用还活着、在前台或最近任务里时，这一步就成了；
 *   2. 再发一条全屏通知 —— 后台启动界面被系统/ROM 拦掉时，靠它把界面顶上来（见 CallNotifications）。
 *
 * <p>为什么闹钟的 PendingIntent 落在广播上而不是直接落在界面上：直接落在界面上的话，被拦掉就是
 * 彻底没动静，连个通知都没有；先经过广播，至少还有第二次机会。
 */
public final class CallAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String payload = intent.getStringExtra(CallScheduler.EXTRA_CALL);
        if (payload == null) payload = "{}";
        android.util.Log.e("DoutuCall", "闹钟到点，收到广播 payload=" + payload);
        try {
            context.startActivity(CallScheduler.callIntent(context, payload));
            android.util.Log.e("DoutuCall", "已尝试直接起来电界面");
        } catch (Exception e) {
            // 后台启动被拦：下面的通知兜底
            android.util.Log.e("DoutuCall", "直接起界面失败：" + e);
        }
        CallNotifications.show(context, payload);
    }
}
