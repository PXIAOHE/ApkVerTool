#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""组装未签名 APK：resources.arsc(STORED+对齐) + AndroidManifest.xml(AXML) + classes.dex
+ classes2.dex + 图标 + assets/signkey.p12，然后调用 AvtCli sign 签名。"""
import os
import subprocess
import sys
import zipfile

import version as V

ROOT = os.path.dirname(os.path.abspath(__file__))


def main():
    build = os.path.join(ROOT, "build")
    unsigned = os.path.join(build, "unsigned.apk")
    signed = os.path.join(build, V.apk_name())
    with zipfile.ZipFile(unsigned, "w", zipfile.ZIP_DEFLATED) as z:
        # resources.arsc 必须第一个写：首条目数据偏移 44%4==0 自动 4 字节对齐
        zi = zipfile.ZipInfo("resources.arsc")
        zi.compress_type = zipfile.ZIP_STORED
        with open(os.path.join(build, "resources.arsc"), "rb") as f:
            z.writestr(zi, f.read())
        z.write(os.path.join(build, "AndroidManifest.xml"), "AndroidManifest.xml")
        z.write(os.path.join(build, "dex", "classes.dex"), "classes.dex")
        z.write(os.path.join(build, "dex2", "classes.dex"), "classes2.dex")
        z.write(os.path.join(build, "res", "mipmap", "ic_launcher.png"), "res/mipmap/ic_launcher.png")
        z.write(os.path.join(ROOT, "signkey.p12"), "assets/signkey.p12")
    print("未签名 APK:", unsigned, os.path.getsize(unsigned), "字节")
    subprocess.check_call([
        "java", "-cp", os.path.join(ROOT, "libs", "apksig.jar") + ":" + os.path.join(build, "cli"),
        "com.seagull.avt.cli.AvtCli", "sign",
        unsigned, signed,
        os.path.join(ROOT, "signkey.p12"), str(V.MIN_SDK),
    ])
    print("签名 APK:", signed, os.path.getsize(signed), "字节")
    print("版本:", V.VERSION_NAME, "code", V.VERSION_CODE)


if __name__ == "__main__":
    main()
