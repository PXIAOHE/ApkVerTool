# -*- coding: utf-8 -*-
"""v2.0.3：主页三组折叠。"""
import io, sys

AVT = '/workspace/avt/'


def sub(path, pairs):
    s = io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        n = s.count(old)
        if n != 1:
            print('!! %s 匹配 %d 次: %r' % (path, n, old.strip().splitlines()[0][:70]))
            sys.exit(1)
        s = s.replace(old, new)
    io.open(path, 'w', encoding='utf-8').write(s)
    print('ok', path)


v = AVT + 'version.py'
sub(v, [('VERSION_NAME = "2.0.2"\nVERSION_CODE = 25\n',
         'VERSION_NAME = "2.0.3"\nVERSION_CODE = 26\n')])

s = io.open(v, encoding='utf-8').read()
anchor = '    ("2.0.2", "2026-10-10", [\n'
entry = (
    '    ("2.0.3", "2026-10-10", [\n'
    '        "主页「清单改写」改成三组折叠：📦 版本号（默认展开，最常用）、"\n'
    '        "📱 安卓SDK版本（最小SDK / 目标SDK）、🏷 应用标识（应用名 / 包名），后两组默认收起",\n'
    '        "折叠规则统一：收起的分组整组字段都不参与改写；展开后留空同样视为不改",\n'
    '        "标题行做成可点的圆角条目（浅底 + 描边），右侧「展开 ▾ / 收起 ▴」随状态切换",\n'
    '    ]),\n'
)
if s.count(anchor) != 1:
    print('!! 锚点匹配 %d 次' % s.count(anchor))
    sys.exit(1)
s = s.replace(anchor, entry + anchor)
io.open(v, 'w', encoding='utf-8').write(s)
print('ok changelog 2.0.3')

sub(AVT + 'README.md', [
    ("# ApkVerTool v2.0.2 — APK 版本号修改器 + 体检工具",
     "# ApkVerTool v2.0.3 — APK 版本号修改器 + 体检工具"),
    ("- 包名: `com.seagull.apkvertool`  versionName: 2.0.2  versionCode: 25",
     "- 包名: `com.seagull.apkvertool`  versionName: 2.0.3  versionCode: 26"),
    ("cd .. && sh build.sh    # 路线 B（纯 framework，无 Compose）：产物 build/ApkVerTool-v2.0.2.apk",
     "cd .. && sh build.sh    # 路线 B（纯 framework，无 Compose）：产物 build/ApkVerTool-v2.0.3.apk"),
    ("## 验证记录（v2.0.2）",
     """## 验证记录（v2.0.3）

- 主页三组折叠（`addFoldGroup(key, title, defaultOpen)` + `toggleFold` + `isFoldOpen`）：
  📦 版本号（versionName / versionCode / versionCodeMajor，默认展开）、
  📱 安卓SDK版本（最小SDK / 目标SDK，默认收起）、🏷 应用标识（应用名 / 包名，默认收起）
- 提交时按 `isFoldOpen(key)` 决定读不读整组输入框：收起 = 整组不参与改写

## R8 shrink 实验记录（未启用）

想进一步压 dex 试过 R8，结论是**当前不可用**：R8 9.5.22 / 9.5.23 在本工程输入上直接内部崩溃
（`java.lang.NullPointerException: Cannot read field "n"`，堆栈在 `internal.v6.a`），
且与 JDK 版本（21 / 25 都崩）、`-dontshrink -dontoptimize -dontobfuscate`、`--no-desugaring` 都无关。
已定位到 `com.seagull.avt.bar.LiquidGlassView` 和 `libs-merged.jar` 里都有触发点，
属于 R8 自身 bug，不是配置问题。要再压体积只能走「不引 Compose」那条路（产物 471KB）。

## 验证记录（v2.0.2）"""),
])
print('done')
