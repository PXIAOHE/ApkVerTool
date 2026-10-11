# -*- coding: utf-8 -*-
"""v1.8.4：root 安装结果提示。"""
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
sub(v, [('VERSION_NAME = "1.8.3"\nVERSION_CODE = 21\n',
         'VERSION_NAME = "1.8.4"\nVERSION_CODE = 22\n')])

s = io.open(v, encoding='utf-8').read()
anchor = '    ("1.8.3", "2026-10-10", [\n'
entry = (
    '    ("1.8.4", "2026-10-10", [\n'
    '        "root 静默安装的结果会弹对话框（成功 / 被系统拒绝 / 失败三种），"\n'
    '        "不再只有一个一闪而过的 toast",\n'
    '        "成功判定同时看 pm 的 Success 与退出码 (exit=0)，带 Failure / Exception 的输出不会误判",\n'
    '        "产物就是本应用自身时提示「装完系统会重启本应用」；点「安装」会先在日志写明走的是"\n'
    '        "系统安装器还是 root 静默安装",\n'
    '    ]),\n'
)
if s.count(anchor) != 1:
    print('!! version.py 锚点匹配 %d 次' % s.count(anchor))
    sys.exit(1)
s = s.replace(anchor, entry + anchor)
io.open(v, 'w', encoding='utf-8').write(s)
print('ok changelog 1.8.4')

sub(AVT + 'README.md', [
    ("# ApkVerTool v1.8.3 — APK 版本号修改器 + 体检工具",
     "# ApkVerTool v1.8.4 — APK 版本号修改器 + 体检工具"),
    ("- 包名: `com.seagull.apkvertool`  versionName: 1.8.3  versionCode: 21",
     "- 包名: `com.seagull.apkvertool`  versionName: 1.8.4  versionCode: 22"),
    ("cd .. && sh build.sh    # 路线 B（纯 framework，无 Compose）：产物 build/ApkVerTool-v1.8.3.apk",
     "cd .. && sh build.sh    # 路线 B（纯 framework，无 Compose）：产物 build/ApkVerTool-v1.8.4.apk"),
    ("## 验证记录（v1.8.3）",
     """## 验证记录（v1.8.4）

- root 静默安装的结果改成「日志 + toast + 对话框」三重提示，成功 / 被系统拒绝 / 其他失败分别给不同文案
- 成功判定放宽到退出码：`Success` 或 `(exit=0)` 且无 `failure` / `exception` 才算成功 ——
  Magisk su 下偶尔只回退出码，之前会被判成「安装失败，详见日志」
- 安装前先比对产物包名与本应用包名一致就提示「装完系统会重启本应用」

## 验证记录（v1.8.3）"""),
])
print('done')
