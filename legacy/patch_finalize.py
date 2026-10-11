# -*- coding: utf-8 -*-
"""收尾：addPage 诊断日志只在第一页打；changelog 补一条。"""
import io, sys

AVT = '/workspace/avt/'

def edit(path, reps):
    s = io.open(path, encoding='utf-8').read()
    for old, new in reps:
        n = s.count(old)
        if n != 1:
            print('!! %s 匹配 %d 次: %r' % (path, n, old.strip().splitlines()[0][:70]))
            sys.exit(1)
        s = s.replace(old, new)
    io.open(path, 'w', encoding='utf-8').write(s)
    print('ok', path)


p = AVT + 'src/com/seagull/avt/app/PagedActivity.java'
edit(p, [(
    """        // 诊断 + 兜底：无底栏模式下 bar 就是 null，绝不能再去碰它
        android.util.Log.i("AVT",
                "addPage idx=" + index + " ver=" + AvtVersion.NAME + " mode=" + barMode
                        + " noBar=" + noBar + " bar=" + (bar == null ? "null" : "obj"));

        if (noBar || bar == null) {""",
    """        // 无底栏模式（BAR_NONE）下 bar 就是 null，绝不能再去碰它；
        // 第一页打一行诊断，底栏相关的问题一眼能看出来
        if (index == 0) {
            android.util.Log.i("AVT", "addPage ver=" + AvtVersion.NAME + " mode=" + barMode
                    + " noBar=" + noBar + " bar=" + (bar == null ? "null" : "obj"));
        }
        if (noBar || bar == null) {""")])

v = AVT + 'version.py'
edit(v, [(
    """        "修复：输出位置授权不持久 —— SAF 选目录补上 PERSISTABLE 授权位，重启 App 后不再静默失效",""",
    """        "验签详情 / 体检：不重签产物也能显示原签名者（直接解析签名块里的证书，按 SHA-256 去重，V2 与 V3 不再列两遍）",
        "修复：输出位置授权不持久 —— SAF 选目录补上 PERSISTABLE 授权位，重启 App 后不再静默失效",""")])
print('done')
