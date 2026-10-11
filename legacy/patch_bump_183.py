# -*- coding: utf-8 -*-
"""v1.8.3：指定输出目录后不再留中转副本。"""
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
sub(v, [('VERSION_NAME = "1.8.2"\nVERSION_CODE = 20\n',
         'VERSION_NAME = "1.8.3"\nVERSION_CODE = 21\n')])

s = io.open(v, encoding='utf-8').read()
anchor = '    ("1.8.2", "2026-10-10", [\n'
entry = (
    '    ("1.8.3", "2026-10-10", [\n'
    '        "修复：选了「设置 → 输出」里的指定目录后，应用私有目录里还会多留一份 —— "\n'
    '        "导出成功的当下就把中转副本删掉，产物只留指定目录那一份",\n'
    '        "中转副本清理后，「安装 / 分享」自动改用指定目录里的产物"\n'
    '        "（系统安装器直接给 uri；root 静默安装会先把文件取回缓存再装）",\n'
    '    ]),\n'
)
if s.count(anchor) != 1:
    print('!! version.py CHANGELOG 锚点匹配 %d 次' % s.count(anchor))
    sys.exit(1)
s = s.replace(anchor, entry + anchor)
io.open(v, 'w', encoding='utf-8').write(s)
print('ok changelog 1.8.3')

sub(AVT + 'README.md', [
    ("# ApkVerTool v1.8.2 — APK 版本号修改器 + 体检工具",
     "# ApkVerTool v1.8.3 — APK 版本号修改器 + 体检工具"),
    ("- 包名: `com.seagull.apkvertool`  versionName: 1.8.2  versionCode: 20",
     "- 包名: `com.seagull.apkvertool`  versionName: 1.8.3  versionCode: 21"),
    ("cd .. && sh build.sh    # 路线 B（纯 framework，无 Compose）：产物 build/ApkVerTool-v1.8.2.apk",
     "cd .. && sh build.sh    # 路线 B（纯 framework，无 Compose）：产物 build/ApkVerTool-v1.8.3.apk"),
    ("## 验证记录（v1.8.2）",
     """## 验证记录（v1.8.3）

- 指定输出目录时不再留两份：产物先落在 `getExternalFilesDir("out")`（App 的工作目录，
  安装 / 分享从这里取），导出到指定目录成功后立刻删掉中转副本，日志写「中转副本已清理」
- 中转副本删掉后，安装 / 分享改用指定目录的 uri：系统安装器直接吃 uri，
  root 静默安装会把 uri 流式拷回 `cacheDir` 再 `pm install`

## 验证记录（v1.8.2）"""),
])
print('done')
