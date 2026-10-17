package com.luoyun.doutu.features.call;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.luoyun.doutu.R;

/**
 * 到点响的那一刻，除了直接把来电界面拉起来，还挂一条**全屏通知**兜底。
 *
 * <p>为什么需要兜底：Android 10 以后系统限制应用在后台启动界面，各家 ROM 还各有各的加码
 * （这台 vivo 上实测：即使把「显示在其他应用上层」给上，闹钟到点时 startActivity 仍然被丢掉，
 * 界面一点动静都没有）。全屏通知是系统给闹钟/来电类应用留的正门：应用在后台也能把界面顶上来，
 * 系统不允许顶上来时至少也是一条能点的高优先级通知 —— 总比什么都不发生强。
 *
 * <p>通知本身不出声也不震动（铃声由页面里那场来电自己放），点了就进那场来电。
 */
public final class CallNotifications {
    private static final String CHANNEL_ID = "doutu_incoming_call";
    private static final int NOTIFICATION_ID = 0x2c12;

    private CallNotifications() {
    }

    /** 到点了：发（或刷新）这条来电通知。 */
    public static void show(Context context, String payload) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        ensureChannel(context, manager);

        Intent call = CallScheduler.callIntent(context, payload);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(context, 0x2c13, call, flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(R.mipmap.ic_launcher_doutu)
                .setContentTitle("模拟来电")
                .setContentText("有人打过来了，点一下接起")
                .setCategory(Notification.CATEGORY_CALL)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(true)
                .setContentIntent(open)
                // 关键的一行：系统允许的话，它会直接把来电界面顶到最前面（锁屏、后台都算）
                .setFullScreenIntent(open, true);
        try {
            manager.notify(NOTIFICATION_ID, builder.build());
            android.util.Log.e("DoutuCall", "兜底通知已发出（全屏意图）");
        } catch (Exception e) {
            // 没有通知权限等情况：那就只剩「直接起界面」这一条路
            android.util.Log.e("DoutuCall", "兜底通知发不出去：" + e);
        }
    }

    /** 来电界面已经起来了（或者用户取消了），把兜底通知撤掉。 */
    public static void cancel(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    private static void ensureChannel(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "模拟来电",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("到点自动响起的模拟来电");
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }
}
