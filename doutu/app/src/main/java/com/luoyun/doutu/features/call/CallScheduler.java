package com.luoyun.doutu.features.call;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.luoyun.doutu.features.wei.WeiHomeActivity;

import org.json.JSONObject;

/**
 * 什么时候开始来电。
 *
 * <p>「立刻」就是把「小微主页」带着来电参数直接起起来；「N 分钟后 / 指定钟点」交给
 * {@link AlarmManager#setAlarmClock}。用闹钟这一档而不是普通定时，是因为它正好是为「到点了要让用户
 * 看见一个界面」设计的：不需要 SCHEDULE_EXACT_ALARM 之类的特殊权限，打瞌睡（Doze）时也会准时响，
 * 而且后台启动 Activity 的限制对它放行 —— 普通定时在 Android 10 以后经常起不来界面。
 *
 * <p>同一时刻只留一个待触发的来电：请求码固定，重新设置会覆盖上一份。
 */
public final class CallScheduler {
    /** 「小微主页」从哪个 extra 里读这次来电要显示谁、放哪首歌。 */
    public static final String EXTRA_CALL = "doutu_call_payload";
    /** 闹钟广播的动作（同一时刻只留一个待触发的来电）。 */
    public static final String ACTION_ALARM = "com.luoyun.doutu.action.SIMULATED_CALL";

    private static final String PREFS = "call_alarm";
    private static final String KEY_AT = "at";
    private static final int REQUEST_CODE = 0x2c11;

    private CallScheduler() {
    }

    /** 这次来电要传给页面的参数（谁打来的 / 头像 / 铃声）。 */
    public static String payload() {
        try {
            JSONObject json = new JSONObject();
            json.put("name", CallStore.name());
            json.put("avatar", CallStore.avatarForPage());
            json.put("ringtone", CallStore.ringtoneForPage());
            return json.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    static Intent callIntent(Context context, String payload) {
        Intent intent = WeiHomeActivity.intentFor(context, WeiHomeActivity.FEATURE_CALL);
        intent.putExtra(EXTRA_CALL, payload);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    /** 闹钟到点时先叫醒的广播（它再去起界面、发兜底通知）。 */
    private static Intent alarmIntent(Context context, String payload) {
        Intent intent = new Intent(context, CallAlarmReceiver.class);
        intent.setAction(ACTION_ALARM);
        intent.putExtra(EXTRA_CALL, payload);
        return intent;
    }

    /** 立刻响：直接起页面。 */
    public static void startNow(Context context, String payload) {
        cancel(context);
        CallNotifications.cancel(context);
        context.startActivity(callIntent(context, payload));
    }

    /** 到点响：挂一个闹钟，界面上会同时出现系统的闹钟图标（这是它该有的样子）。 */
    public static void scheduleAt(Context context, long triggerAtMillis, String payload) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            startNow(context, payload);
            return;
        }
        PendingIntent operation = PendingIntent.getBroadcast(
                context, REQUEST_CODE, alarmIntent(context, payload),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // 先普通精确定时，再退到闹钟档、最后退到不精确的定时。
        // 顺序有讲究：这台 vivo 上实测 setAlarmClock 挂广播时，到点广播根本没送到
        // （闹钟被系统吃掉了，CallAlarmReceiver 一行日志都没有），而 setExactAndAllowWhileIdle
        // 是通的。既然 USE_EXACT_ALARM 已经装上了，就以它为主。
        try {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
        } catch (Exception first) {
            try {
                manager.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAtMillis, operation), operation);
            } catch (Exception second) {
                manager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            }
        }
        prefs(context).edit().putLong(KEY_AT, triggerAtMillis).apply();
    }

    /** 取消待触发的来电；没有也当成功。 */
    public static void cancel(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent existing = existing(context);
        if (manager != null && existing != null) manager.cancel(existing);
        CallNotifications.cancel(context);
        prefs(context).edit().remove(KEY_AT).apply();
    }

    /** 还没到点的那个来电是什么时候（毫秒）；没有就是 0。 */
    public static long pendingAt(Context context) {
        long at = prefs(context).getLong(KEY_AT, 0L);
        if (at <= 0L) return 0L;
        if (existing(context) == null) {
            prefs(context).edit().remove(KEY_AT).apply();
            return 0L;
        }
        if (at <= System.currentTimeMillis()) {
            // 已经过点：系统随时会把它送过来，界面上就不再显示「待触发」
            return 0L;
        }
        return at;
    }

    private static PendingIntent existing(Context context) {
        return PendingIntent.getBroadcast(
                context, REQUEST_CODE, alarmIntent(context, ""),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
