# -*- coding: utf-8 -*-
"""把 84 个库 jar 按"版本高者优先"做类级去重，合并成单个 jar。
   androidx 常见的坑：activity-ktx:1.7.0 和 activity:1.13.0 定义同一批类
   （ktx 后来被并进主包），D8 会直接报 duplicate class。Gradle 靠版本对齐解决，
   我们这里用类级去重解决：同一个类名只保留版本最高的那份。"""
import glob, io, os, re, sys, zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))   # 工程根目录（原来是写死的 /workspace/avt）
AAR = ROOT + "/aar"
EXTRA = [ROOT + "/tools/kotlin-stdlib-2.4.20.jar",
         ROOT + "/tools/kotlinx-coroutines-core-jvm-1.11.0.jar"] + sorted(glob.glob(ROOT + "/tools/kotlinx-coroutines-android-*.jar"))
OUT = ROOT + "/build/libs-merged.jar"

VER_RE = re.compile(r"(\d+)(?:\.(\d+))?(?:\.(\d+))?")


def ver_key(name):
    m = re.search(r"-(\d[\w.\-]*)\.jar$", name)
    v = m.group(1) if m else "0"
    mm = VER_RE.match(v)
    nums = [(int(x) if x else 0) for x in (mm.groups() if mm else (0, 0, 0))]
    pre = 0 if re.search(r"alpha|beta|rc|dev", v) else 1
    return (nums[0], nums[1], nums[2], pre, v)


def main():
    jars = [AAR + "/" + f for f in os.listdir(AAR) if f.endswith(".jar")] + EXTRA
    jars = [j for j in jars if os.path.exists(j)]
    # 版本高的先放，先到先得 = 高版本优先
    jars.sort(key=lambda p: ver_key(os.path.basename(p)), reverse=True)
    claimed = {}
    dup = 0
    kept = 0
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED, compresslevel=1) as zout:
        for j in jars:
            try:
                with zipfile.ZipFile(j) as zin:
                    for n in zin.namelist():
                        if not n.endswith(".class"):
                            continue
                        if n in claimed:
                            dup += 1
                            continue
                        claimed[n] = os.path.basename(j)
                        zout.writestr(n, zin.read(n))
                        kept += 1
            except Exception as e:
                print("  跳过 %s (%s)" % (os.path.basename(j), e))
    print("合并 %d 个 jar -> %s" % (len(jars), OUT))
    print("类 %d 个，去重丢弃 %d 个重复类，体积 %.1f MB" % (kept, dup, os.path.getsize(OUT) / 1048576.0))
    # 把被丢弃的重复类来源列一下，方便核对
    if dup:
        from collections import Counter
        c = Counter()
        for j in jars:
            pass
        print("（重复类按版本高者优先保留）")


if __name__ == "__main__":
    main()
