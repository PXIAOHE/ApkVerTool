# -*- coding: utf-8 -*-
"""依赖解析 v3（自造 Gradle 的最小替代，修掉"同一 artifact 两个版本"的坑）

关键点：版本冲突必须按 **解析后的真实 artifact 名** 归并 ——
`androidx.compose.runtime:runtime` 和 `androidx.compose.runtime:runtime-android`
是同一个东西（前者是 KMP 根，后者是 android 变体），按请求名做键会各下一份，
最后 D8 里同一批类出现两个版本，必然炸。
"""
import io, os, re, sys, json, zipfile, urllib.request, urllib.error
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.dirname(os.path.abspath(__file__))   # 工程根目录（原来是写死的 /workspace/avt）
OUT, RES, MANI, CACHE = ROOT + "/aar", ROOT + "/aar-res", ROOT + "/aar-manifest", ROOT + "/aar-cache"
POMCACHE = ROOT + "/pom-cache"
for d in (OUT, RES, MANI, CACHE, POMCACHE):
    os.makedirs(d, exist_ok=True)

# 仓库列表按顺序试。
# ⚠️ 不要用 repo1.maven.org —— 本机访问它 40s 超时、0 字节（2026-10-10 实测），
#    而这个脚本把"超时"和"不存在"都当成 not found，会一个 artifact 白等 40 秒。
#    阿里云镜像实测 1.3~1.7 MB/s，两个仓库都是 pull-through 代理。
REPOS = [("aliyun-google", "https://maven.aliyun.com/repository/google"),
         ("aliyun-central", "https://maven.aliyun.com/repository/central"),
         ("google", "https://dl.google.com/dl/android/maven2")]
ROOTS = [("top.yukonga.miuix.kmp", "miuix-ui-android", "0.9.4"),
         ("top.yukonga.miuix.kmp", "miuix-blur-android", "0.9.4"),
         ("top.yukonga.miuix.kmp", "miuix-icons-android", "0.9.4"),
         ("androidx.activity", "activity-compose", "1.13.0")]
SKIP_GROUPS = {"junit", "org.junit", "org.robolectric", "com.google.truth", "androidx.test",
               "org.jetbrains.kotlinx", "org.jetbrains.kotlin"}   # kotlin 运行时单独用 tools/ 里的
SKIP_ART = {"androidx.compose.material3:material3-window-size-class",
            "androidx.compose.material3:material3-window-size-class-android"}
LINT = re.compile(r"-lint$|:lint|compose\.material:material")


def lname(t):
    return t.split("}")[-1]


def cache_path(url):
    return POMCACHE + "/" + url.replace("https://", "").replace("/", "_")


def fetch_opt(url, timeout=45):
    cp = cache_path(url)
    if os.path.exists(cp + ".404"):
        return None
    if os.path.exists(cp):
        return open(cp, "rb").read()
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "avt-build/1.0"})
        data = urllib.request.urlopen(req, timeout=timeout).read()
        try:
            open(cp, "wb").write(data)
        except Exception:
            pass
        return data
    except urllib.error.HTTPError as e:
        if e.code == 404:
            try:
                open(cp + ".404", "w").write("1")
            except Exception:
                pass
        return None
    except Exception:
        return None


def fetch_big(url, dest, timeout=300):
    if os.path.exists(dest) and os.path.getsize(dest) > 512:
        return True
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "avt-build/1.0"})
        data = urllib.request.urlopen(req, timeout=timeout).read()
        open(dest + ".part", "wb").write(data)
        os.replace(dest + ".part", dest)
        return True
    except Exception as e:
        print("   下载失败 %s (%s)" % (url.split("/")[-1], e), flush=True)
        return False


def pom_url(repo, g, a, v):
    return "%s/%s/%s/%s/%s-%s.pom" % (repo, g.replace(".", "/"), a, v, a, v)


def art_url(repo, g, a, v, ext):
    return "%s/%s/%s/%s/%s-%s.%s" % (repo, g.replace(".", "/"), a, v, a, v, ext)


def parse_pom(x):
    root = ET.fromstring(x)
    props, deps, parent, packaging = {}, [], None, "jar"
    for el in root:
        n = lname(el.tag)
        if n == "packaging":
            packaging = (el.text or "jar").strip()
        elif n == "properties":
            for p in el:
                props[lname(p.tag)] = (p.text or "").strip()
        elif n == "parent":
            parent = {lname(c.tag): (c.text or "").strip() for c in el}
        elif n == "dependencies":
            for d in el:
                if lname(d.tag) != "dependency":
                    continue
                it = {lname(c.tag): (c.text or "").strip() for c in d}
                if it.get("artifactId"):
                    deps.append(it)
    return packaging, props, deps, parent


def sub(v, props, proj):
    if not v:
        return v
    for _ in range(4):
        m = re.search(r"\$\{([^}]+)\}", v)
        if not m:
            break
        k = m.group(1)
        v = v.replace(m.group(0), props.get(k) or proj.get(k.split(".")[-1]) or "")
    return v


VER_RE = re.compile(r"(\d+)(?:\.(\d+))?(?:\.(\d+))?")


def ver_key(v):
    m = VER_RE.match(v or "")
    nums = [(int(x) if x else 0) for x in (m.groups() if m else (0, 0, 0))]
    pre = 0 if re.search(r"alpha|beta|rc|dev", v or "") else 1
    return (nums[0], nums[1], nums[2], pre, v or "")


def base_name(a):
    """KMP 迁移会把 lifecycle-viewmodel 拆成 lifecycle-viewmodel-android，
    同一个东西两个名字 —— 归并键必须用去掉变体后缀的基名，否则两份都进来。"""
    return re.sub(r"-(android|jvm)$", "", a)


def variants(a):
    if re.search(r"-(android|jvm|desktop|ios\w*|js|wasm\w*|macos\w*|linux\w*)$", a):
        return [a]
    return [a + "-android", a + "-jvm", a]


def find_pom(g, a, v):
    for name in variants(a):
        for rname, rurl in REPOS:
            x = fetch_opt(pom_url(rurl, g, name, v))
            if x is None:
                continue
            try:
                packaging, props, deps, parent = parse_pom(x)
            except Exception:
                continue
            return dict(repo=rurl, rname=rname, req=a, art=name, ver=v, packaging=packaging,
                        props=props, deps=deps, parent=parent)
    return None


def prefetch(items, workers=24):
    def probe(it):
        g, a, v = it
        for name in variants(a):
            for _rn, rurl in REPOS:
                fetch_opt(pom_url(rurl, g, name, v))
    items = list(items)
    if not items:
        return
    with ThreadPoolExecutor(max_workers=workers) as ex:
        list(ex.map(probe, items))


def parent_props(info):
    par = info.get("parent") or {}
    if not par.get("artifactId"):
        return info["props"]
    px = fetch_opt(pom_url(info["repo"], par["groupId"], par["artifactId"], par["version"]))
    if not px:
        return info["props"]
    try:
        _, pprops, _, _ = parse_pom(px)
    except Exception:
        return info["props"]
    merged = dict(pprops)
    merged.update(info["props"])
    return merged


# ================= 阶段 1：建图 + 定版本（按真实 artifact 名归并） =================
chosen = {}          # (group, 基名) -> 版本
chosen_art = {}      # (group, 基名) -> 该版本真实 artifact 名
infos = {}           # (group, 真实artifact名, 版本) -> POM 信息
failures = []
pending = list(ROOTS)
seen_req = set()

for rnd in range(10):
    pending = [p for p in pending if (p[0], p[1], p[2]) not in seen_req]
    if not pending:
        break
    for p in pending:
        seen_req.add((p[0], p[1], p[2]))
    prefetch(pending)
    nxt = []
    for g, a, v in pending:
        info = find_pom(g, a, v)
        if not info:
            failures.append("解析不到 POM: %s:%s:%s" % (g, a, v))
            continue
        key = (g, base_name(info["art"]))
        old = chosen.get(key)
        if old is not None and ver_key(v) <= ver_key(old):
            continue                      # 已有更高版本，丢弃
        chosen[key] = v
        chosen_art[key] = info["art"]
        info["props"] = parent_props(info)
        infos[(g, info["art"], v)] = info
        proj = {"groupId": g, "artifactId": info["art"], "version": v}
        for d in info["deps"]:
            if d.get("optional", "").lower() == "true":
                continue
            if d.get("scope", "compile") in ("test", "provided", "system", "import"):
                continue
            dg, da = d.get("groupId", ""), d.get("artifactId", "")
            dv = sub(d.get("version", ""), info["props"], proj)
            if dv:
                # Maven 版本区间：[1.3.1] / [1.0,2.0) —— 取区间内的具体版本
                dv = dv.strip().strip("[]()").split(",")[0].strip()
            if not dg or not da or not dv or "${" in dv:
                continue
            if dg in SKIP_GROUPS or da.endswith(("-sources", "-javadoc")) or LINT.search(da):
                continue
            if ("%s:%s" % (dg, da)) in SKIP_ART:
                continue
            nxt.append((dg, da, dv))
    print("阶段1 第 %d 轮：pending %d，已定版本 %d" % (rnd + 1, len(nxt), len(chosen)), flush=True)
    pending = nxt

print("阶段1 完成：%d 个 artifact（按真实名归并后）" % len(chosen), flush=True)

# ================= 阶段 2：下载 + 拆包 =================
keep = set()
for (g, base), v in chosen.items():
    keep.add("%s-%s-%s.jar" % (g, chosen_art[(g, base)], v))
for f in os.listdir(OUT):
    if f.endswith(".jar") and f not in keep:
        os.remove(OUT + "/" + f)

total = 0
downloaded = 0
for (g, base), v in sorted(chosen.items()):
    art = chosen_art[(g, base)]
    info = infos.get((g, art, v))
    if info is None:
        info = find_pom(g, art, v)
        if info is None:
            failures.append("阶段2 找不到: %s:%s:%s" % (g, art, v))
            continue
        info["props"] = parent_props(info)
        infos[(g, art, v)] = info
    if info["packaging"] == "pom":
        continue
    ext = "aar" if info["packaging"] == "aar" else "jar"
    dest = "%s/%s-%s-%s.%s" % (CACHE, g, art, v, ext)
    if not fetch_big(art_url(info["repo"], g, art, v, ext), dest):
        failures.append("%s:%s:%s" % (g, art, v))
        continue
    size = os.path.getsize(dest)
    total += size
    downloaded += 1
    nres = 0
    try:
        if ext == "aar":
            with zipfile.ZipFile(dest) as z:
                names = z.namelist()
                if "classes.jar" in names:
                    open("%s/%s-%s-%s.jar" % (OUT, g, art, v), "wb").write(z.read("classes.jar"))
                for n in names:
                    if n.startswith("res/") and not n.endswith("/"):
                        p = "%s/%s/%s-%s/%s" % (RES, g, art, v, n)
                        os.makedirs(os.path.dirname(p), exist_ok=True)
                        open(p, "wb").write(z.read(n))
                        nres += 1
                if "AndroidManifest.xml" in names:
                    open("%s/%s-%s-%s.xml" % (MANI, g, art, v), "wb").write(z.read("AndroidManifest.xml"))
        else:
            open("%s/%s-%s-%s.jar" % (OUT, g, art, v), "wb").write(open(dest, "rb").read())
    except Exception as e:
        failures.append("%s:%s:%s 拆包失败 %s" % (g, art, v, e))
        continue
    print("%-56s %-10s %7d KB res=%d" % ("%s:%s" % (g, art), v, size // 1024, nres), flush=True)

print("\n=== 汇总 ===", flush=True)
print("下载 %d 个，原始 %.1f MB，classes.jar %d 个" % (
    downloaded, total / 1048576.0, len([f for f in os.listdir(OUT) if f.endswith(".jar")])), flush=True)
if failures:
    print("失败 %d：" % len(failures), flush=True)
    for f in sorted(set(failures)):
        print("   ", f, flush=True)
json.dump({"%s:%s" % k: v for k, v in chosen.items()}, open(ROOT + "/deps.json", "w"), indent=1, sort_keys=True)
