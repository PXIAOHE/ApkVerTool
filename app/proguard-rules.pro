# ApkVerTool R8 规则
#
# 引擎（core/）是纯 Java 二进制解析（AXML/ARSC/ZIP/DEX），零反射、零序列化库；
# UI 全量 miuix + Compose，两者自带 consumer proguard 规则。
# 这里只保留：Manifest 引用的组件（AAPT 本会 keep，双保险）与被 JNI/AssetManager
# 间接访问的入口。其余全部交给 R8 裁剪。

# ── Manifest 组件（AAPT 会 keep，显式声明双保险）──
-keep class com.seagull.avt.app.MainActivity
-keep class com.seagull.avt.app.ApkProvider { *; }

# ── apksig（SignUtil 使用）：库内自带规则，这里兜底防止错误回调被裁 ──
-keep class com.android.apksig.** { *; }

# ── 引擎入口被 Kotlin 侧（AvtRunner/AvtIo）直接调用，非反射，无需 keep ──
# 但 ApkInfo / AxmlPatch$Info 等的公开字段被 Kotlin 直接读，R8 能追踪到，不 keep。

# ── 崩溃堆栈可读性：保留源码行号，崩溃时反混淆有据可查 ──
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
