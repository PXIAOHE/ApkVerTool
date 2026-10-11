# Miuix 参考源码留档

来源：https://github.com/compose-miuix-ui/miuix （main 分支，2026-10-09 拉取）

| 文件 | 说明 |
|---|---|
| `NavigationBar.kt` | `NavigationBar` / `FloatingNavigationBar` / `NavigationBarItem` / `NavigationBarDefaults` / `FloatingNavigationBarDefaults` 原始实现 |
| `LiquidGlassNavigationBar.kt` | 官方示例里的液态玻璃底栏实现，可作为后续升级参考 |
| `navigationbar.zh_CN.md` | 官方中文组件文档 |

本工程的底栏是**按这些源码的规格在纯 framework 栈里重新实现**的，没有引入 Kotlin / Compose / Gradle。

## 被采纳的规格

| 规格 | 取值 | 出处 |
|---|---|---|
| 底栏高度 | 64dp | `NavigationBarDefaults.ItemHeight` |
| 圆角 | 高度的一半（胶囊） | `FloatingToolbarDefaults.CornerRadius = 50.dp` |
| 距底部 | 26dp + 导航栏 inset | `FloatingNavigationBar` 的 `bottomPaddingValue` |
| 内边距 | 2dp | `FloatingNavigationBar` 的 `0.75.dp` 内边距 |
| 投影 | elevation 6dp（对应 radius 10dp 软投影） | `FloatingNavigationBar` 的 `dropShadow(radius = 10.dp)` |
| 图标尺寸 | 26dp | `NavigationBarDefaults.IconSize` |
| 图标上边距 | 8dp | `NavigationBarDefaults.IconTopPadding` |
| 标签字号 | 12sp | `NavigationBarDefaults.LabelFontSize` |
| 标签下边距 | 8dp | `NavigationBarDefaults.BottomPadding` |
| 未选中透明度 | 0.4 | `NavigationBarDefaults.UnselectedAlpha` |
| 选中 / 未选中同色、无胶囊底 | — | `NavigationBarItemColors.contentColor()` |

唯一偏离：Miuix 默认选中色是 `onSurfaceContainer` 满透明度，本工程改用主题色以对齐酷安底栏观感。
