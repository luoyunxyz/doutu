package com.luoyun.doutu.features.icon;

import com.luoyun.doutu.R;

/**
 * 可选的四款应用图标。每一款都是一对：底部弹窗里预览的那张图 + 那一款自己的名字，以及清单里
 * 负责真正把它挂到启动器上的那个 {@code activity-alias}。图和名字是绑在一起的，挑了一款就是
 * 连图带名字一起换掉。
 *
 * <p>别名不写 {@code R.id}：{@code activity-alias} 不会生成 id 资源，所以照它清单里的全名找
 * （见 {@link #aliasName()}）。
 *
 * <p>这四张图 {@code alipay.png} / {@code hkalipay.png} / {@code wechat.png} / {@code logo.png}
 * 来自 {@code D:\AAA\pojia\export\02_flutter_assets\flutter_assets\assets\tupian}，放进
 * {@code res/drawable-nodpi} 当资源用，弹窗里预览的就是这几张原图。
 */
enum AppIcon {
    ALIPAY("alipay", "支付宝", R.drawable.alipay),
    ALIPAY_HK("hkalipay", "支付宝", R.drawable.hkalipay),
    WECHAT("wechat", "微信", R.drawable.wechat),
    DOUTU("doutu", "豆图", R.drawable.logo);

    /** 存档里的键，也是清单里那个别名的最后一段。 */
    final String key;
    /** 启动器上显示的名字。 */
    final String label;
    /** 弹窗里预览的那张图。 */
    final int preview;

    AppIcon(String key, String label, int preview) {
        this.key = key;
        this.label = label;
        this.preview = preview;
    }

    /** 清单里的全名，报错时说得清楚一点。 */
    String aliasName() {
        return "com.luoyun.doutu.Icon." + key;
    }

    /** 存档里读回来的那一款；认不出来就退回豆图。 */
    static AppIcon byKey(String key) {
        for (AppIcon icon : values())
            if (icon.key.equals(key)) return icon;
        return DOUTU;
    }
}
