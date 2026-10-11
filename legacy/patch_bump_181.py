# -*- coding: utf-8 -*-
"""v1.8.1：移除更新日志底栏 + 不重签保留原签名 + 输出位置修复。"""
import io, sys

p = '/workspace/avt/version.py'
s = io.open(p, encoding='utf-8').read()

reps = [
    ('VERSION_NAME = "1.8.0"\nVERSION_CODE = 18\n',
     'VERSION_NAME = "1.8.1"\nVERSION_CODE = 19\n'),
    ('''CHANGELOG = [
    ("1.8.0", "2026-10-09", [''',
     '''CHANGELOG = [
    ("1.8.1", "2026-10-10", [
        "更新日志页移除底部玻璃栏：只留顶部标题栏 + 左右滑动翻页，标题栏右侧显示页码（3/18）",
        "修复：不签名输出没有保留原签名 —— 以前只搬 V1 的 META-INF 条目、开关还是关着的，"
        "现在 V1 条目 + V2/V3 签名块一起原样保留（内容改过，摘要已失效，安装仍需核心破解）",
        "修复：输出位置授权不持久 —— SAF 选目录补上 PERSISTABLE 授权位，重启 App 后不再静默失效",
        "修复：导出到指定目录重名直接失败 —— 同名旧文件先删再写，失败提示改成可操作的「去设置里重选一次」",
    ]),
    ("1.8.0", "2026-10-09", ['''),
]

for old, new in reps:
    n = s.count(old)
    if n != 1:
        print('!! 匹配 %d 次: %r' % (n, old[:60]))
        sys.exit(1)
    s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8').write(s)
print('version.py -> v1.8.1 (code 19)')
