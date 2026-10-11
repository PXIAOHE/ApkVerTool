# -*- coding: utf-8 -*-
"""v2.0.2：重打包改成最高压缩级别（整包小 1MB）。"""
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
sub(v, [('VERSION_NAME = "2.0.1"\nVERSION_CODE = 24\n',
         'VERSION_NAME = "2.0.2"\nVERSION_CODE = 25\n')])

s = io.open(v, encoding='utf-8').read()
anchor = '    ("2.0.1", "2026-10-10", [\n'
entry = (
    '    ("2.0.2", "2026-10-10", [\n'
    '        "打包改用最高压缩级别（原来是 deflate level 1「最快」）：两个 dex 压缩后 8.65MB → 7.62MB，"\n'
    '        "整个 APK 8.84MB → 约 7.8MB",\n'
    '        "App 里改包 / 重打包走的同一套引擎，输出同样变小（代价是打包多花几秒）",\n'
    '    ]),\n'
)
if s.count(anchor) != 1:
    print('!! 锚点匹配 %d 次' % s.count(anchor))
    sys.exit(1)
s = s.replace(anchor, entry + anchor)
io.open(v, 'w', encoding='utf-8').write(s)
print('ok changelog 2.0.2')

sub(AVT + 'README.md', [
    ("# ApkVerTool v2.0.1 — APK 版本号修改器 + 体检工具",
     "# ApkVerTool v2.0.2 — APK 版本号修改器 + 体检工具"),
    ("- 包名: `com.seagull.apkvertool`  versionName: 2.0.1  versionCode: 24",
     "- 包名: `com.seagull.apkvertool`  versionName: 2.0.2  versionCode: 25"),
    ("cd .. && sh build.sh    # 路线 B（纯 framework，无 Compose）：产物 build/ApkVerTool-v2.0.1.apk",
     "cd .. && sh build.sh    # 路线 B（纯 framework，无 Compose）：产物 build/ApkVerTool-v2.0.2.apk"),
    ("## 验证记录（v2.0.1）",
     """## 验证记录（v2.0.2）

- `ZipAlignWriter.Edit.level` 默认值 `Deflater.BEST_SPEED(1)` → `BEST_COMPRESSION(9)`：
  实测两个 dex 压缩后 8.65MB → 7.62MB（deflate 对 dex 的压缩率 39% → 35%），整包省约 1.0MB
- 体积构成（v2.0.1，8.83MB）：dex 8.65MB（98%）、resources.arsc 146KB、res 13KB、
  `lib/arm64-v8a/libandroidx.graphics.path.so` 4.5KB、assets/signkey.p12 2.6KB、V1 签名 5KB
  → 想再小只能动 dex：Compose 全家桶（foundation 3556 类 + ui 3086 类 + runtime 1081 类）+ miuix 1626 类，
  自己的代码只有 132 个类

## 验证记录（v2.0.1）"""),
])
print('done')
