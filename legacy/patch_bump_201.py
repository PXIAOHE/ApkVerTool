# -*- coding: utf-8 -*-
"""v2.0.1：安卓SDK版本折叠区。"""
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
sub(v, [('VERSION_NAME = "2.0.0"\nVERSION_CODE = 23\n',
         'VERSION_NAME = "2.0.1"\nVERSION_CODE = 24\n')])

s = io.open(v, encoding='utf-8').read()
anchor = '    ("2.0.0", "2026-10-10", [\n'
entry = (
    '    ("2.0.1", "2026-10-10", [\n'
    '        "主页新增「📱 安卓SDK版本」折叠区：标题整行可点，展开后 最小SDK / 目标SDK 各占一整行",\n'
    '        "折叠收起时 minSdk / targetSdk 完全不参与改写；展开后留空同样视为不改",\n'
    '        "两个输入框的提示词由「新 minSdk / 新 targetSdk」改为「最小SDK / 目标SDK」",\n'
    '    ]),\n'
)
if s.count(anchor) != 1:
    print('!! 锚点匹配 %d 次' % s.count(anchor))
    sys.exit(1)
s = s.replace(anchor, entry + anchor)
io.open(v, 'w', encoding='utf-8').write(s)
print('ok changelog 2.0.1')

sub(AVT + 'README.md', [
    ("# ApkVerTool v2.0.0 — APK 版本号修改器 + 体检工具",
     "# ApkVerTool v2.0.1 — APK 版本号修改器 + 体检工具"),
    ("- 包名: `com.seagull.apkvertool`  versionName: 2.0.0  versionCode: 23",
     "- 包名: `com.seagull.apkvertool`  versionName: 2.0.1  versionCode: 24"),
    ("cd .. && sh build.sh    # 路线 B（纯 framework，无 Compose）：产物 build/ApkVerTool-v2.0.0.apk",
     "cd .. && sh build.sh    # 路线 B（纯 framework，无 Compose）：产物 build/ApkVerTool-v2.0.1.apk"),
    ("## 验证记录（v2.0.0）",
     """## 验证记录（v2.0.1）

- 主页「📱 安卓SDK版本」折叠区：默认收起（两个框 GONE，提交时直接跳过 minSdk / targetSdk），
  点标题展开成两行（各占一整行，长数字不再被并排挤成半宽），右侧文字在「展开 ▾ / 收起 ▴」间切换
- 选中 APK 后仍会自动把清单里的现值填进这两个框，展开即可看到并按需修改；收起则完全不参与改写

## 验证记录（v2.0.0）"""),
])
print('done')
