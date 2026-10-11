#! /usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 aar-cache/*.aar 里把 jni/<abi>/*.so 提出来，落到 build/jni/<abi>/。

deps_fetch3.py 解 aar 时只取了 classes.jar（见它的 `z.read("classes.jar")`），
jni/ 目录整个丢掉 —— androidx.compose.ui:ui-graphics 拉进来的
androidx.graphics:graphics-path 就带一个 libandroidx.graphics.path.so，
不补的话 PathParser 只能退回 Java 实现（不崩，但白丢一份加速）。

同名 so 只留第一次出现的那份（不同 aar 可能带同一个库的副本）。
"""
import glob
import os
import shutil
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(ROOT, "aar-cache")
OUT = os.path.join(ROOT, "build", "jni")

# 只打 arm64-v8a：目标就是 arm64 真机，别的 abi 纯占体积
ABIS = ("arm64-v8a",)


def main():
    if not os.path.isdir(CACHE):
        print("没有 aar-cache/，跳过 native 库提取")
        return 0
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)

    seen = {}
    for aar in sorted(glob.glob(os.path.join(CACHE, "*.aar"))):
        try:
            with zipfile.ZipFile(aar) as z:
                for name in z.namelist():
                    if not name.startswith("jni/") or not name.endswith(".so"):
                        continue
                    rel = name[len("jni/"):]          # <abi>/xxx.so
                    if rel.split("/")[0] not in ABIS:
                        continue
                    if rel in seen:
                        continue
                    seen[rel] = os.path.basename(aar)
                    dst = os.path.join(OUT, rel)
                    os.makedirs(os.path.dirname(dst), exist_ok=True)
                    with open(dst, "wb") as f:
                        f.write(z.read(name))
                    print("  %-42s <- %s" % (rel, os.path.basename(aar)))
        except Exception as e:
            print("  跳过 %s (%s)" % (os.path.basename(aar), e))

    print("提取 %d 个 native 库 -> build/jni" % len(seen))
    return len(seen)


if __name__ == "__main__":
    main()
