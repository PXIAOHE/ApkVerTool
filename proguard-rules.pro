# ApkVerTool 混淆规则（R8 full-mode）
# ── 类名锚定（本工具链对 extends/implements 条件 keep 不可靠，只用命名锚定）──
# Activity 本体与全部匿名回调嵌套类
-keep class com.seagull.avt.app.MainActivity { *; }
-keep class com.seagull.avt.app.SettingsActivity { *; }
-keep class com.seagull.avt.app.ChangelogActivity { *; }
-keep class com.seagull.avt.app.PagedActivity { *; }
-keep class com.seagull.avt.app.SwipePager { *; }
-keep class com.seagull.avt.app.SwipePager$* { *; }
-keep class com.seagull.avt.app.ApkProvider { *; }
-keep class com.seagull.avt.app.AvtVersion { *; }
-keep class com.seagull.avt.app.AvtChangelog { *; }
-keep class com.seagull.avt.app.MainActivity$* { *; }
-keep class com.seagull.avt.app.SettingsActivity$* { *; }
-keep class com.seagull.avt.app.ChangelogActivity$* { *; }
-keep class com.seagull.avt.app.PagedActivity$* { *; }
# core 包成员全保（类名仍混淆）：R8 shrink 剥离"仅被库内部调用的 override 方法"
# （OutputStream.write(int) 模板回调等），保成员名是唯一可靠手段
-keepclassmembers class com.seagull.avt.core.** { *; }

# 混淆类全部重打包到单字母包，最大化逆向阻力
-repackageclasses 'o'
-allowaccessmodification

# ── 库模型容忍（完整模型经 android.jar 提供，此处兜底）──
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn java.net.**
-dontwarn java.util.**
-dontwarn java.lang.**
-dontwarn java.io.**
-dontwarn java.nio.**
-dontwarn java.math.**
-dontwarn java.text.**
-dontwarn java.time.**
-dontwarn java.util.function.**
-dontwarn java.util.concurrent.**
-dontwarn javax.security.auth.x500.**
-dontwarn javax.crypto.**
-dontwarn javax.security.**
-dontwarn java.security.**
