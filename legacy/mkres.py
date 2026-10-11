# -*- coding: utf-8 -*-
"""路线 A 的资源链：文本 AndroidManifest（由 version.py 生成）+ aapt2 合并各库 res + link。
   取代原来的 mkaxml.py / mkicon.py（手搓 AXML 与 584 字节 arsc 退役）。"""
import io, os, re, sys, glob, shutil, subprocess, tempfile

ROOT = os.path.dirname(os.path.abspath(__file__))   # 工程根目录（原来是写死的 /workspace/avt）
sys.path.insert(0, ROOT)
import version as V   # version.py 单一真源

BUILD = ROOT + "/build"
RES_APP = ROOT + "/res"                 # 应用自己的资源（图标）
RES_LIBS = ROOT + "/aar-res"            # 各库拆出来的 res
# 每次构建都用一个全新的临时目录装"合并后的资源"：
#   * 天然不残留上一轮的旧文件，不怕 values 改名计数错位
#   * 不需要 shutil.rmtree 整目录清理 —— 一个 300+ 文件的目录一次性删除，
#     在开了"批量删除保护"的环境里会被直接拦下，导致构建中断
RES_MERGED = os.path.join(tempfile.mkdtemp(prefix="avt-res-"), "res")
MANI = ROOT + "/aar-manifest"
MANIFEST = ROOT + "/AndroidManifest.xml"


def find_aapt2():
    """aapt2 优先读 tools/aapt2.txt，其次 ANDROID_HOME/build-tools 里最高的那版。"""
    conf = os.path.join(ROOT, "tools", "aapt2.txt")
    if os.path.exists(conf):
        p = io.open(conf, encoding="utf-8").read().strip()
        if p and os.path.exists(p):
            return p
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    bt = os.path.join(sdk, "build-tools")
    if os.path.isdir(bt):
        def key(d):
            return [int(x) if x.isdigit() else 0 for x in re.split(r"[.\-]", d)[:3]]
        for d in sorted(os.listdir(bt), key=key, reverse=True):
            cand = os.path.join(bt, d, "aapt2.exe" if os.name == "nt" else "aapt2")
            if os.path.exists(cand):
                return cand
    return "aapt2"


AAPT2 = find_aapt2()

TEXT_MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
<!-- 由 mkres.py 从 version.py 生成，勿手改 -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="{package}"
    android:versionCode="{code}"
    android:versionName="{name}">

    <uses-sdk android:minSdkVersion="{minsdk}" android:targetSdkVersion="{targetsdk}" />

    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

    <application
        android:label="{label}"
        android:icon="@mipmap/ic_launcher"
        android:hardwareAccelerated="true"
        android:allowBackup="false">

        <activity
            android:name="com.seagull.avt.app.MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name="com.seagull.avt.app.SettingsActivity"
            android:exported="false"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode" />

        <activity
            android:name="com.seagull.avt.app.ChangelogActivity"
            android:exported="false"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode" />

        <provider
            android:name="com.seagull.avt.app.ApkProvider"
            android:authorities="{authority}"
            android:exported="false"
            android:grantUriPermissions="true" />
    </application>
</manifest>
"""


def gen_manifest():
    text = TEXT_MANIFEST.format(
        package=V.PACKAGE, code=V.VERSION_CODE, name=V.VERSION_NAME,
        minsdk=V.MIN_SDK, targetsdk=V.TARGET_SDK,
        label=V.APP_LABEL, authority=V.PROVIDER_AUTHORITY)
    with io.open(MANIFEST, "w", encoding="utf-8") as f:
        f.write(text)
    print("AndroidManifest.xml 已生成（v%s / code %s）" % (V.VERSION_NAME, V.VERSION_CODE))


def merge_res():
    os.makedirs(RES_MERGED)
    n = 0
    val_seq = 0
    # 1) 应用自己的资源优先
    roots = [RES_APP] + sorted(glob.glob(RES_LIBS + "/*/*/res"))
    for root in roots:
        if not os.path.isdir(root):
            continue
        for dirpath, _dirs, files in os.walk(root):
            for fn in files:
                src = os.path.join(dirpath, fn)
                rel = os.path.relpath(src, root)
                parts = rel.split(os.sep)
                if parts[0] == "values" or parts[0].startswith("values-"):
                    # values 文件重名必须改名，否则互相覆盖会丢资源
                    val_seq += 1
                    parts[-1] = "%s_%d_%s" % (os.path.splitext(parts[-1])[0], val_seq, os.path.splitext(parts[-1])[1])
                dst = os.path.join(RES_MERGED, *parts)
                if os.path.exists(dst):
                    continue          # 同名非 values 资源：留第一个
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy2(src, dst)
                n += 1
    print("合并资源 %d 个文件 -> %s" % (n, RES_MERGED))
    return n


def lib_packages():
    """所有带资源的库包名 —— aapt2 link 必须为它们生成 R 类，
       否则运行时会 NoClassDefFoundError: androidx.lifecycle.runtime.R$id 这种。"""
    pkgs = set()
    for f in glob.glob(MANI + "/*.xml"):
        try:
            head = io.open(f, encoding="utf-8", errors="replace").read(3000)
        except Exception:
            continue
        m = re.search(r'package="([^"]+)"', head)
        if m:
            pkgs.add(m.group(1))
    pkgs.discard(V.PACKAGE)
    return sorted(pkgs)


def run(cmd):
    p = subprocess.run(cmd, capture_output=True, text=True)
    if p.returncode != 0:
        print("!! 失败: %s" % " ".join(cmd[:3]))
        print((p.stdout or "")[-3000:])
        print((p.stderr or "")[-3000:])
        return False
    out = (p.stdout or "").strip()
    if out:
        print("   " + out[-500:])
    return True


def main():
    print("aapt2: %s" % AAPT2)
    gen_manifest()
    merge_res()

    compiled = BUILD + "/res-compiled.zip"
    if os.path.exists(compiled):
        os.remove(compiled)
    print("[aapt2 compile]")
    if not run([AAPT2, "compile", "--dir", RES_MERGED, "-o", compiled]):
        return 1

    android_jar = io.open(ROOT + "/tools/android-jar.txt").read().strip()
    out = BUILD + "/res-linked.apk"
    gen = BUILD + "/gen"
    if os.path.exists(out):
        os.remove(out)
    os.makedirs(gen, exist_ok=True)
    pkgs = lib_packages()
    print("[aapt2 link]  -I %s，--extra-packages %d 个库" % (os.path.basename(android_jar), len(pkgs)))
    ok = run([AAPT2, "link", "-o", out,
              "--manifest", MANIFEST,
              "-I", android_jar,
              "--java", gen,
              "--extra-packages", ":".join(pkgs),
              "--min-sdk-version", str(V.MIN_SDK),
              "--target-sdk-version", str(V.TARGET_SDK),
              "--version-code", str(V.VERSION_CODE),
              "--version-name", V.VERSION_NAME,
              "--no-version-vectors",
              compiled])
    if not ok:
        return 1
    import zipfile
    with zipfile.ZipFile(out) as z:
        names = z.namelist()
        arsc = z.getinfo("resources.arsc").file_size if "resources.arsc" in names else 0
        print("link 成功: %s (%.1f MB)，resources.arsc %d 字节，条目 %d" % (
            os.path.basename(out), os.path.getsize(out) / 1048576.0, arsc, len(names)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
