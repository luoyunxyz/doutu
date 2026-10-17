# 项目专用 R8 / ProGuard 规则（2026-10-17 开启混淆时整理）
#
# 背景：这个 App 防逆向的最后一块就是「抄走也看不懂」——包里的类名/方法名先混淆掉。
# 开启前审计过三类高风险点，结论都还好：
#   · 没有 Class.forName / getDeclaredMethod / getIdentifier 之类的按名反射；
#   · 没有 Serializable / Parcelable（那种靠字段名反射的）；
#   · 运行时零第三方依赖（只有测试依赖），没有第三方库的 keep 包袱。
# 唯一必须显式保住的是**WebView 的 JS 桥**：前端是按方法名调的，混淆掉就直接失效。

# ---------------------------------------------------------------- JS ↔ Java 桥
# 30+ 个 @JavascriptInterface 方法（微/支模块的全部桥接口）都靠名字被前端调用。
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# 桥对象本身是通过 addJavascriptInterface 传出去的，类也不能被裁掉或改名
-keep class com.luoyun.doutu.features.wei.WeiHomeActivity$* { *; }
-keep class com.luoyun.doutu.features.zhi.** { *; }

# ---------------------------------------------------------------- 清单里的组件
# 本项目有 40+ 个 Activity 和多个 activity-alias（四个桌面图标都指向 TermsActivity），
# AGP 一般会自动保留，这里显式写一条，避免哪天换构建版本被优化掉。
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# 自定义 View 会被 XML/代码直接 new，构造器签名要保持
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ---------------------------------------------------------------- 协议与安全模块
# DTW 私有协议 + 防二次打包：走的是标准 JCE / PackageManager API，没有按名反射，
# 但这两块出错很难排查，保留它们的类名与成员方便线上定位（体积代价可忽略）。
-keep class com.luoyun.doutu.account.dtw.** { *; }
-keep class com.luoyun.doutu.account.DoutuApi { *; }
-keep class com.luoyun.doutu.account.Session { *; }

# ---------------------------------------------------------------- 通用安全项
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# android.R 里有些字段是给反射/资源表用的，保一下（惯例）
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ---------------------------------------------------------------- 不报警告
# 可选依赖（没有打进包里的东西）不要因为缺类把构建打断
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
-dontwarn android.support.**
-dontwarn kotlin.**
-dontwarn kotlinx.**
