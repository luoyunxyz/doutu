package com.luoyun.doutu.features.icon;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.widget.Toast;

/**
 * 真正换图标的那一下。
 *
 * <p>Android 不允许一个应用在运行时改自己清单里的 {@code android:icon} / {@code android:label}，
 * 能改的只有「哪一个组件是启用的」。所以四款图标在清单里各是一个 {@code activity-alias}，指向
 * 同一个 {@link com.luoyun.doutu.MainActivity}，各自带着自己那张图和自己那个名字，彼此互斥：
 * 一次只留一个启用，启动器显示的就是启用了的那一个 —— 图是它的，名字也是它的，这就把图和名字
 * 绑在了一起。
 *
 * <p>换的时候先关掉其余三个再打开选中的那一个，并且对选中的那个先关后开一次：启动器（以及
 * 各家桌面的图标缓存）都是跟着组件状态的变化走的，这样写能让它当场重新取一次图，而不是等下次
 * 开机。
 *
 * <p>各家的做法都一样 —— 这个把图标和名称换掉的权限属于系统，不需要任何额外权限，也不用重启。
 */
public final class AppIconSwitcher {
    /** 换完给用户的一句话，各页面共用同一句。 */
    private static final String DONE = "已切换为";

    private AppIconSwitcher() {
    }

    /**
     * 把应用图标和名字换成 {@code icon}。换成功返回 true，并存下用户这一次的挑选。
     *
     * <p>换了也没什么可回滚的，所以任何一个别名没开关成功都只报一句话，不抛给调用方。
     */
    public static boolean apply(Activity activity, AppIcon icon) {
        PackageManager manager = activity.getPackageManager();
        String pkg = activity.getPackageName();
        try {
            for (AppIcon other : AppIcon.values()) {
                if (other == icon) continue;
                manager.setComponentEnabledSetting(alias(pkg, other),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
            ComponentName target = alias(pkg, icon);
            // 先关后开：桌面对一个「本来就是启用」的组件不会重新取图标，走这一遭它才会当场换。
            // 关掉之后必须马上开回来 —— 中间不留没有入口的窗口，否则桌面会把图标从原位置挪走。
            manager.setComponentEnabledSetting(target,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            manager.setComponentEnabledSetting(target,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (IllegalArgumentException | SecurityException error) {
            // 别名不在清单里 —— 那是打错了名字，说清楚比默默什么都不做强。
            Toast.makeText(activity, "切换失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
        IconPrefs.save(activity, icon);
        Toast.makeText(activity, DONE + icon.label, Toast.LENGTH_SHORT).show();
        return true;
    }

    /**
     * 当前真正在启动器上生效的那一款。
     *
     * <p>以**系统里启用的别名**为准，存档只当兜底：两者可能不一致（用户清过应用数据、
     * 换机恢复、或者组件状态被系统改过），而启动器上挂的永远只是启用的那一个别名。
     * 之前只看存档，会出现「弹窗勾着豆图、桌面上却挂着微信」这种自相矛盾的状态。
     */
    public static AppIcon current(Context context) {
        AppIcon enabled = enabledAlias(context.getPackageManager(), context.getPackageName());
        return enabled != null ? enabled : IconPrefs.current(context);
    }

    /** 系统里启用了的那个别名；一个都没启用（异常状态）返回 null。 */
    private static AppIcon enabledAlias(PackageManager manager, String pkg) {
        for (AppIcon icon : AppIcon.values()) {
            if (manager.getComponentEnabledSetting(alias(pkg, icon)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return icon;
            }
        }
        return null;
    }

    /**
     * 没有会员就只允许用**默认图标**（豆图）和默认应用名。
     *
     * <p>为什么放在这里而不是只拦弹窗：用户可能是「先开了会员换的图标、后来会员到期了」，
     * 光拦弹窗拦不住这种情况 —— 到期之后启动器上还挂着支付宝/微信的图标，
     * 和我们自己的功能也对不上。所以每次进主页都核对一次，不符合就悄悄换回默认款。
     *
     * @param notify true 时换完弹一句话（用户主动触发的场景用），false 时静默（启动自检用）
     * @return 这次有没有真的把它换回默认图标
     */
    public static boolean enforceMembership(Activity activity, boolean notify) {
        if (com.luoyun.doutu.account.Session.isActivated()) return false;
        if (current(activity) == AppIcon.DOUTU) return false;
        PackageManager manager = activity.getPackageManager();
        String pkg = activity.getPackageName();
        try {
            for (AppIcon icon : AppIcon.values()) {
                manager.setComponentEnabledSetting(alias(pkg, icon),
                        icon == AppIcon.DOUTU
                                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
        } catch (IllegalArgumentException | SecurityException error) {
            return false;
        }
        IconPrefs.save(activity, AppIcon.DOUTU);
        if (notify) {
            Toast.makeText(activity, "未开通会员，已恢复默认图标和名称", Toast.LENGTH_LONG).show();
        }
        return true;
    }

    /**
     * 启动器现在给这个应用显示的名字：启用了的那个别名自己的名字。
     *
     * <p>读的就是系统给启动器的那一份，所以弹窗里写出来的和桌面上挂着的必然一致，不会各说各话。
     * 一个别名都没启用时（比如被人从设置里关掉了）退回应用自己的名字。
     */
    public static String launcherLabel(Context context) {
        PackageManager manager = context.getPackageManager();
        AppIcon enabled = enabledAlias(manager, context.getPackageName());
        if (enabled != null) {
            try {
                return manager.getActivityInfo(alias(context.getPackageName(), enabled), 0).loadLabel(manager).toString();
            } catch (PackageManager.NameNotFoundException ignored) {
                // 掉到下面用应用自己的名字
            }
        }
        return context.getApplicationInfo().loadLabel(manager).toString();
    }

    /**
     * 别名自己的名字。{@code new ComponentName(String, String)} 直接给包名和类名，
     * 两个参数都在，不用管 API 33 起才有的那个三参数重载。
     */
    private static ComponentName alias(String pkg, AppIcon icon) {
        return new ComponentName(pkg, icon.aliasName());
    }
}
