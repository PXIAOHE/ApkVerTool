# -*- coding: utf-8 -*-
"""v1.8.2：执行日志 / 输出位置修复。"""
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


# ---------------- version.py ----------------
v = AVT + 'version.py'
sub(v, [('VERSION_NAME = "1.8.1"\nVERSION_CODE = 19\n',
         'VERSION_NAME = "1.8.2"\nVERSION_CODE = 20\n')])

s = io.open(v, encoding='utf-8').read()
anchor = '    ("1.8.1", "2026-10-10", [\n'
entry = (
    '    ("1.8.2", "2026-10-10", [\n'
    '        "修复：导出到指定目录报「Insert not supported」—— 改成 createDocument 优先、insert 兜底"\n'
    '        "（MIUI「安全访问」这类 provider 只认前者），失败文案写清是「该目录不支持写入」",\n'
    '        "新增：设置页选输出目录时先建探针文件试写，不能写就当场提示换目录"\n'
    '        "（推荐 内部存储 / Download），不再等输出时才发现",\n'
    '        "执行日志自动滚到最新一行；pm install 失败的几十行 Java 堆栈压成几行，"\n'
    '        "并给出结论：内容改过的包必须重签名，或用核心破解类模块 + root 安装",\n'
    '    ]),\n'
)
if s.count(anchor) != 1:
    print('!! version.py CHANGELOG 锚点匹配 %d 次' % s.count(anchor))
    sys.exit(1)
s = s.replace(anchor, entry + anchor)
io.open(v, 'w', encoding='utf-8').write(s)
print('ok changelog 1.8.2')

# ---------------- README.md ----------------
sub(AVT + 'README.md', [
    ("# ApkVerTool v1.8.1 — APK 版本号修改器 + 体检工具",
     "# ApkVerTool v1.8.2 — APK 版本号修改器 + 体检工具"),
    ("- 包名: `com.seagull.apkvertool`  versionName: 1.8.1  versionCode: 19",
     "- 包名: `com.seagull.apkvertool`  versionName: 1.8.2  versionCode: 20"),
    ("cd .. && sh build.sh    # 路线 B（纯 framework，无 Compose）：产物 build/ApkVerTool-v1.8.1.apk",
     "cd .. && sh build.sh    # 路线 B（纯 framework，无 Compose）：产物 build/ApkVerTool-v1.8.2.apk"),
    ("## 验证记录（v1.8.1）",
     """## 验证记录（v1.8.2）

- 真机复现「输出位置」问题：32.9MB / 4078 条目的系统 APK 做不重签输出时报
  `SAF 导出失败: Insert not supported` —— 选中的目录 provider 不认 `insert`；
  改成 `createDocument` 优先后导出成功（两条都失败才提示换目录）
- 设置页选目录加了探针写入（建一个 `.avt_write_probe` 再删），不可写的目录当场提示、不写进配置
- 日志页自动滚到最新行；`pm install` 失败的 30 行 Java 堆栈压成 3 行 + 结论

## 验证记录（v1.8.1）"""),
    ("""24. 页面改成 BAR_NONE（不建底栏）后，`bar` 字段就是 null，`addPage` 里所有碰 `bar` 的分支都要先判空""",
     """24. 页面改成 BAR_NONE（不建底栏）后，`bar` 字段就是 null，`addPage` 里所有碰 `bar` 的分支都要先判空
25. `ContentResolver.insert()` 到 SAF 目录**不是**所有 provider 都支持：MIUI「安全访问」直接抛
    `Insert not supported`，得用 `DocumentsContract.createDocument()`（反过来也有只认 insert 的 provider，
    所以两条都要试）
26. 日志 TextView 要自己滚到底（`Layout.getLineTop(lineCount) - height`），否则内容一多，
    看到的永远是开头那三行"""),
])
print('done')
