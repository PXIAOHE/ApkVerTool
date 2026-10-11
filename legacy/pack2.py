#! /usr/bin/env python3
# -*- coding: utf-8 -*-
"""路线 A 组包：aapt2 的 link 产物（resources.arsc + AXML + 各库 res） + 多 dex + assets，
   然后用工程自带的 ZipAlignWriter（AvtCli repack）对齐、AvtCli sign 签名。
   取代原来的 pack.py（那份写死了 classes.dex / classes2.dex 与手搓 arsc）。"""
import os
import subprocess
import sys
import zipfile

import version as V

ROOT = os.path.dirname(os.path.abspath(__file__))
BUILD = os.path.join(ROOT, "build")
LINKED = os.path.join(BUILD, "res-linked.apk")
DEXDIR = os.path.join(BUILD, "dexall")
UNSIGNED = os.path.join(BUILD, "unsigned.apk")
ALIGNED = os.path.join(BUILD, "aligned.apk")
SIGNED = os.path.join(BUILD, V.apk_name())
CLI_CP = os.path.join(ROOT, "libs", "apksig.jar") + os.pathsep + os.path.join(BUILD, "cli")


def assemble():
    if not os.path.exists(LINKED):
        raise SystemExit("缺 %s —— 先跑 mkres.py（aapt2）" % LINKED)
    dexes = sorted(f for f in os.listdir(DEXDIR) if f.startswith("classes") and f.endswith(".dex"))
    if not dexes:
        raise SystemExit("缺 dex —— 先跑 D8")
    with zipfile.ZipFile(UNSIGNED, "w", zipfile.ZIP_DEFLATED) as z:
        # resources.arsc 放第一个且 STORED：后面 repack 对齐时首条目 4 字节对齐好算
        with zipfile.ZipFile(LINKED) as lz:
            zi = zipfile.ZipInfo("resources.arsc")
            zi.compress_type = zipfile.ZIP_STORED
            z.writestr(zi, lz.read("resources.arsc"))
            for n in lz.namelist():
                if n == "resources.arsc" or n.endswith("/"):
                    continue
                zi = zipfile.ZipInfo(n)
                zi.compress_type = zipfile.ZIP_DEFLATED
                z.writestr(zi, lz.read(n))
        for f in dexes:
            zi = zipfile.ZipInfo(f)
            zi.compress_type = zipfile.ZIP_DEFLATED
            with open(os.path.join(DEXDIR, f), "rb") as fh:
                z.writestr(zi, fh.read())
        # native 库：lib/<abi>/xxx.so。deps_fetch3.py 只取了 aar 里的 classes.jar，
        # jni/ 下的 so 会漏（androidx.graphics:graphics-path 就带一个），由 extract_jni.py 补出来
        so_count = 0
        jni_root = os.path.join(BUILD, "jni")
        if os.path.isdir(jni_root):
            for abi in sorted(os.listdir(jni_root)):
                abi_dir = os.path.join(jni_root, abi)
                if not os.path.isdir(abi_dir):
                    continue
                for fn in sorted(os.listdir(abi_dir)):
                    if not fn.endswith(".so"):
                        continue
                    zi = zipfile.ZipInfo("lib/%s/%s" % (abi, fn))
                    zi.compress_type = zipfile.ZIP_DEFLATED
                    with open(os.path.join(abi_dir, fn), "rb") as fh:
                        z.writestr(zi, fh.read())
                    so_count += 1
        z.write(os.path.join(ROOT, "signkey.p12"), "assets/signkey.p12")
    print("未签名 APK: %s  %.2f MB (%d 个 dex, %d 个 so)" % (
        os.path.basename(UNSIGNED), os.path.getsize(UNSIGNED) / 1048576.0, len(dexes), so_count))


def run(args):
    subprocess.check_call(["java", "-cp", CLI_CP, "com.seagull.avt.cli.AvtCli"] + args)


def main():
    assemble()
    print("[repack] 用工程自带 ZipAlignWriter 对齐（lib/**/*.so 16KB，STORED 条目 4 字节）")
    run(["repack", UNSIGNED, ALIGNED, "-", "-"])
    print("[sign] V1/V2/V3")
    run(["sign", ALIGNED, SIGNED, os.path.join(ROOT, "signkey.p12"), str(V.MIN_SDK)])
    print("签名 APK: %s  %.2f MB" % (os.path.basename(SIGNED), os.path.getsize(SIGNED) / 1048576.0))
    print("版本: %s code %s" % (V.VERSION_NAME, V.VERSION_CODE))


if __name__ == "__main__":
    main()
