# -*- coding: utf-8 -*-
"""打包 2.0.0：合并 1.8.1~1.8.4 的改动日志，同步 README 与页内文案。"""
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
sub(v, [('VERSION_NAME = "1.8.4"\nVERSION_CODE = 22\n',
         'VERSION_NAME = "2.0.0"\nVERSION_CODE = 23\n')])

s = io.open(v, encoding='utf-8').read()
start = s.index('    ("1.8.4", "2026-10-10", [')
end = s.index('    ("1.8.0", "2026-10-09", [')
entry = (
    '    ("2.0.0", "2026-10-10", [\n'
    '        "自 1.8.0 以来的全部改动汇总：底栏 / 签名 / 输出链路大修",\n'
    '        "更新日志页去掉底栏：只留顶部标题栏 + 左右滑动翻页，页码写在标题栏右侧（3/19）",\n'
    '        "「不重签」输出真保留原签名：V1 的 META-INF 条目 + V2/V3 签名块一起原样搬运"\n'
    '        "（旧版只搬 V1 条目、开关还没打开，产物看起来一条签名都没留下）",\n'
    '        "验签详情 / 体检：不重签产物也能显示原签名者（直接解析签名块里的证书，"\n'
    '        "按 SHA-256 去重，V2 与 V3 不再列两遍）",\n'
    '        "输出位置修复：SAF 授权的持久化位补齐（重启 App 后不再静默失效）；"\n'
    '        "导出改成 createDocument 优先、insert 兜底（修「Insert not supported」）；同名文件先删再写",\n'
    '        "设置页选输出目录时先建探针文件试写，不能写就当场提示换目录（推荐 内部存储 / Download）",\n'
    '        "指定输出目录后不再留两份：导出成功的当下就删掉应用私有目录里的中转副本，"\n'
    '        "产物只留在指定目录；「安装 / 分享」自动改用指定目录里那份",\n'
    '        "执行日志自动滚到最新一行；pm install 失败的几十行 Java 堆栈压成几行并给出结论",\n'
    '        "root 静默安装结果显示对话框（成功 / 被系统拒绝 / 失败三种），"\n'
    '        "成功判定同时看 Success 与退出码 (exit=0)；产物是本应用自身时提示装完会被系统重启",\n'
    '    ]),\n'
)
s = s[:start] + entry + s[end:]
io.open(v, 'w', encoding='utf-8').write(s)
print('ok version.py 2.0.0（1.8.1~1.8.4 已合并进 1.8.0 之后的这一条）')

# ---------------- MainActivity：提示文案 ----------------
sub(AVT + 'src/com/seagull/avt/app/MainActivity.java', [(
    '· 输出位置在「设置 → 输出」里指定，默认应用私有目录；\\n',
    '· 输出位置在「设置 → 输出」里指定；指定后产物只留那一份（中转文件导出成功即删）；\\n')])

# ---------------- README ----------------
sub(AVT + 'README.md', [
    ("# ApkVerTool v1.8.4 — APK 版本号修改器 + 体检工具",
     "# ApkVerTool v2.0.0 — APK 版本号修改器 + 体检工具"),
    ("- 包名: `com.seagull.apkvertool`  versionName: 1.8.4  versionCode: 22",
     "- 包名: `com.seagull.apkvertool`  versionName: 2.0.0  versionCode: 23"),
    ("cd .. && sh build.sh    # 路线 B（纯 framework，无 Compose）：产物 build/ApkVerTool-v1.8.4.apk",
     "cd .. && sh build.sh    # 路线 B（纯 framework，无 Compose）：产物 build/ApkVerTool-v2.0.0.apk"),
    ("## 验证记录（v1.8.4）",
     """## 验证记录（v2.0.0）

2.0.0 = **1.8.0 以来所有修复的汇总版**（底栏 / 签名 / 输出链路），逐项记录见下面各版本小节，关键几条：

- 不重签产物保留原签名：CLI `repack` 产物签名块与原包**逐字节一致**（4096 字节，SHA-256 相同），
  `META-INF/AVT.SF` / `AVT.RSA` / `MANIFEST.MF` 都在
- 不重签产物的验签详情能列出「签名者 1（原签名，未重签）」，不再显示「无签名」
- SAF 导出：真机复现 `Insert not supported` 失败 → 改成 `createDocument` 优先后导出成功
- 指定输出目录后不再留两份：`files/out` 为空，产物只在 Download
- 更新日志页无底栏、页码与左右滑动正常（真机，18 页）
- 32.9MB / 4078 条目的系统 APK 在 CLI 与 App 两端跑通

## 验证记录（v1.8.4）"""),
])
print('done')
