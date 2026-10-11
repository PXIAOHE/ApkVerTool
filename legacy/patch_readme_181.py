# -*- coding: utf-8 -*-
"""README 更新到 v1.8.1。"""
import io, sys

p = '/workspace/avt/README.md'
s = io.open(p, encoding='utf-8').read()

reps = [
    ("# ApkVerTool v1.8.0 — APK 版本号修改器 + 体检工具",
     "# ApkVerTool v1.8.1 — APK 版本号修改器 + 体检工具"),

    ("- 包名: `com.seagull.apkvertool`  versionName: 1.8.0  versionCode: 18",
     "- 包名: `com.seagull.apkvertool`  versionName: 1.8.1  versionCode: 19"),

    ("更新日志独立成 `ChangelogActivity`，一个版本一页、底部玻璃栏横向翻页。",
     "更新日志独立成 `ChangelogActivity`，一个版本一页、**左右滑动翻页（不要底栏）**，\n"
     "页码同步到标题栏右侧（`📜 更新日志 3/19`）——版本多的时候滑到哪页一眼可见。"),

    ("底栏三种形态，由 `PagedActivity` 的 `BAR_MIUIX` / `BAR_ICON` / `BAR_CHIP` 选择：",
     "底栏四种形态，由 `PagedActivity` 的 `BAR_MIUIX` / `BAR_ICON` / `BAR_CHIP` / `BAR_NONE` 选择："),

    ("| `BAR_CHIP` | 更新日志页 | 纯文字胶囊，**全部合并进同一个玻璃容器**横向滚动、垂直居中，选中项 accent 填充 |",
     "| `BAR_CHIP` | 历史形态（当前无页面使用） | 纯文字胶囊，**全部合并进同一个玻璃容器**横向滚动、垂直居中，选中项 accent 填充 |\n"
     "| `BAR_NONE` | 更新日志页 | 一个底栏都不建：只有顶部标题栏 + 左右滑动翻页，内容一直铺到屏幕底部 |"),

    ("        ├── ChangelogActivity.java            # 更新日志（一版本一页）",
     "        ├── ChangelogActivity.java            # 更新日志（一版本一页，无底栏 + 标题栏页码）"),

    ("cd .. && sh build.sh    # 产物 build/ApkVerTool-v1.8.0.apk",
     "cd .. && sh build.sh    # 路线 B（纯 framework，无 Compose）：产物 build/ApkVerTool-v1.8.1.apk\n"
     "sh build2.sh fast       # 路线 A（真 Compose / miuix 底栏，需 kotlinc + aapt2 + aar/）：同一产物名"),

    ("## 验证记录（v1.8.0）",
     """## 验证记录（v1.8.1）

- **更新日志页去底栏**：18 个版本页全部创建成功（`addPage ver=1.8.1 mode=3 noBar=true bar=null` 无异常），
  左右滑动翻页与标题栏页码（`2/18`）双向同步正常
- **不重签输出保留原签名**（CLI `repack`，与 App 共用同一份 `ApkPatcher`）：
  产物里 `META-INF/AVT.SF`、`META-INF/AVT.RSA`、`MANIFEST.MF` 都在，
  且 `APK Sig Block 42` 签名块与原包**逐字节一致**（4096 字节，SHA-256 相同）
- **不重签产物的验签详情**：apksig 因摘要不匹配不回报证书 → 自行解析签名块取证书，
  按 SHA-256 去重后只列一条（`签名者 1（原签名，未重签）` + 说明性 WARN），不再显示「无签名」

## 验证记录（v1.8.0）"""),

    ("""20. 在底栏容器 `dispatchTouchEvent` 里转发触摸时，**拖动中的 UP 也必须吞掉**，
    否则 UP 会落到子项自己的点击逻辑上，一次横滑会变成"跳到那一项对应的页\"""",
     """20. 在底栏容器 `dispatchTouchEvent` 里转发触摸时，**拖动中的 UP 也必须吞掉**，
    否则 UP 会落到子项自己的点击逻辑上，一次横滑会变成"跳到那一项对应的页"
21. 「不重签输出」要管**两个**开关：`keepOldSignature`（V1 的 META-INF 条目）**和**签名块（V2/V3）搬运。
    只留 V1 条目、开关又默认关着时，产物看起来就是"一条签名都没留下"
22. `ACTION_OPEN_DOCUMENT_TREE` 必须带 `FLAG_GRANT_PERSISTABLE_URI_PERMISSION`，
    否则 `takePersistableUriPermission` 拿到的授权重启即失效，SAF 导出静默失败
23. SAF 的 `insert` **不覆盖**同名文档：重名要么先 `deleteDocument`，要么换名，否则第二次输出就报错
24. 页面改成 BAR_NONE（不建底栏）后，`bar` 字段就是 null，`addPage` 里所有碰 `bar` 的分支都要先判空"""),
]

for old, new in reps:
    n = s.count(old)
    if n != 1:
        print('!! 匹配 %d 次: %r' % (n, old.strip().splitlines()[0][:70]))
        sys.exit(1)
    s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8').write(s)
print('ok README.md -> v1.8.1')
