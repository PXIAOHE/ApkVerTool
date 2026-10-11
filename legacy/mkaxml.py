#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""最小 AXML 编译器：把固定的 AndroidManifest XML 结构编码成二进制 AXML。无需 aapt2。

版本号 / 包名 / 目标 SDK 全部来自 version.py，避免多处硬编码漂移。
"""
import struct
import sys

import version as V

# ---- 字符串池（顺序即索引） ----
S = [
    "http://schemas.android.com/apk/res/android",     # android ns uri
    "android",                                        # ns prefix
    "manifest",
    "package",
    V.PACKAGE,
    "versionCode",
    "versionName",
    V.VERSION_NAME,
    "uses-sdk",
    "minSdkVersion",
    "targetSdkVersion",
    "uses-permission",
    "name",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.REQUEST_INSTALL_PACKAGES",
    "application",
    "label",
    V.APP_LABEL,
    "icon",
    "activity",
    "com.seagull.avt.app.MainActivity",
    "exported",
    "com.seagull.avt.app.SettingsActivity",
    "intent-filter",
    "action",
    "android.intent.action.MAIN",
    "category",
    "android.intent.category.LAUNCHER",
    "provider",
    "com.seagull.avt.app.ApkProvider",
    "authorities",
    V.PROVIDER_AUTHORITY,
    "grantUriPermissions",
    "com.seagull.avt.app.ChangelogActivity",
]
IDX = {s: i for i, s in enumerate(S)}

NS = IDX["http://schemas.android.com/apk/res/android"]
NO_NS = 0xFFFFFFFF

# typedValue 类型
T_STRING, T_INT_DEC, T_BOOLEAN, T_REFERENCE = 0x03, 0x10, 0x12, 0x01
ICON_RES = 0x7F010000  # package 0x7F / type 1 (mipmap) / entry 0

FRAMEWORK_ATTR_IDS = {
    "label": 0x01010001,
    "icon": 0x01010002,
    "name": 0x01010003,
    "exported": 0x01010010,
    "authorities": 0x01010018,
    "grantUriPermissions": 0x0101001B,
    "minSdkVersion": 0x0101020C,
    "versionCode": 0x0101021B,
    "versionName": 0x0101021C,
    "targetSdkVersion": 0x01010270,
}


def mutf8(s: str) -> bytes:
    out = bytearray()
    for ch in s:
        c = ord(ch)
        if c == 0:
            out += b"\xc0\x80"
        elif c < 0x80:
            out.append(c)
        elif c < 0x800:
            out.append(0xC0 | (c >> 6))
            out.append(0x80 | (c & 0x3F))
        else:
            out.append(0xE0 | (c >> 12))
            out.append(0x80 | ((c >> 6) & 0x3F))
            out.append(0x80 | (c & 0x3F))
    return bytes(out)


def var8(n: int) -> bytes:
    if n <= 0x7F:
        return bytes([n])
    return bytes([0x80 | (n >> 8), n & 0xFF])


def build_pool() -> bytes:
    blob = bytearray()
    offs = []
    for s in S:
        offs.append(len(blob))
        b8 = mutf8(s)
        blob += var8(len(s))
        blob += var8(len(b8))
        blob += b8
        blob += b"\x00"
    while len(blob) % 4 != 0:
        blob += b"\x00"
    strings_start = 28 + 4 * len(S)
    pool = bytearray()
    pool += struct.pack("<HHIIIIII", 0x0001, 28, 0, len(S), 0, 0x100, strings_start, strings_start + len(blob))
    for o in offs:
        pool += struct.pack("<I", o)
    pool += blob
    while len(pool) % 4 != 0:
        pool += b"\x00"
    pool[4:8] = struct.pack("<I", len(pool))
    return bytes(pool)


def build_resmap() -> bytes:
    """RES_XML_RESOURCE_MAP：池字符串索引 -> 框架 attr 资源 ID。PM 的 obtainAttributes 靠它解析。"""
    entries = b"".join(struct.pack("<I", FRAMEWORK_ATTR_IDS.get(s, 0)) for s in S)
    return struct.pack("<HHI", 0x0180, 8, 8 + len(entries)) + entries


def attr(ns, name, raw, vtype, data) -> bytes:
    # Res_value: size u16 / res0 u8 / dataType u8 / data u32
    return struct.pack("<IIIHBBI", ns, name, raw, 8, 0, vtype, data)


def start_elem(name_idx, ns_idx, attrs, line=1) -> bytes:
    size = 16 + 20 + 20 * len(attrs)
    out = struct.pack("<HHI", 0x0102, 16, size)
    out += struct.pack("<II", line, 0xFFFFFFFF)
    out += struct.pack("<II", ns_idx, name_idx)
    out += struct.pack("<HHHHHH", 20, 20, len(attrs), 0, 0, 0)
    for a in attrs:
        out += a
    assert len(out) == size
    return out


def end_elem(name_idx, ns_idx, line=1) -> bytes:
    return struct.pack("<HHI", 0x0103, 16, 24) + struct.pack("<II", line, 0xFFFFFFFF) + struct.pack("<II", ns_idx, name_idx)


def ns_chunk(start: bool, prefix_idx, uri_idx) -> bytes:
    return struct.pack("<HHI", 0x0100 if start else 0x0101, 16, 24) + struct.pack("<II", 1, 0xFFFFFFFF) + struct.pack("<II", prefix_idx, uri_idx)


def main() -> None:
    manifest_attrs = [
        attr(NO_NS, IDX["package"], IDX[V.PACKAGE], T_STRING, IDX[V.PACKAGE]),
        attr(NS, IDX["versionCode"], 0xFFFFFFFF, T_INT_DEC, V.VERSION_CODE),
        attr(NS, IDX["versionName"], IDX[V.VERSION_NAME], T_STRING, IDX[V.VERSION_NAME]),
    ]
    uses_sdk_attrs = [
        attr(NS, IDX["minSdkVersion"], 0xFFFFFFFF, T_INT_DEC, V.MIN_SDK),
        attr(NS, IDX["targetSdkVersion"], 0xFFFFFFFF, T_INT_DEC, V.TARGET_SDK),
    ]
    perm_write = [attr(NS, IDX["name"], IDX["android.permission.WRITE_EXTERNAL_STORAGE"], T_STRING,
                       IDX["android.permission.WRITE_EXTERNAL_STORAGE"])]
    perm_install = [attr(NS, IDX["name"], IDX["android.permission.REQUEST_INSTALL_PACKAGES"], T_STRING,
                         IDX["android.permission.REQUEST_INSTALL_PACKAGES"])]
    app_attrs = [
        attr(NS, IDX["label"], IDX[V.APP_LABEL], T_STRING, IDX[V.APP_LABEL]),
        attr(NS, IDX["icon"], 0xFFFFFFFF, T_REFERENCE, ICON_RES),
    ]
    activity_attrs = [
        attr(NS, IDX["name"], IDX["com.seagull.avt.app.MainActivity"], T_STRING,
             IDX["com.seagull.avt.app.MainActivity"]),
        attr(NS, IDX["exported"], 0xFFFFFFFF, T_BOOLEAN, 1),
    ]
    settings_attrs = [
        attr(NS, IDX["name"], IDX["com.seagull.avt.app.SettingsActivity"], T_STRING,
             IDX["com.seagull.avt.app.SettingsActivity"]),
        attr(NS, IDX["exported"], 0xFFFFFFFF, T_BOOLEAN, 0),
    ]
    changelog_attrs = [
        attr(NS, IDX["name"], IDX["com.seagull.avt.app.ChangelogActivity"], T_STRING,
             IDX["com.seagull.avt.app.ChangelogActivity"]),
        attr(NS, IDX["exported"], 0xFFFFFFFF, T_BOOLEAN, 0),
    ]
    provider_attrs = [
        attr(NS, IDX["name"], IDX["com.seagull.avt.app.ApkProvider"], T_STRING,
             IDX["com.seagull.avt.app.ApkProvider"]),
        attr(NS, IDX["authorities"], IDX[V.PROVIDER_AUTHORITY], T_STRING, IDX[V.PROVIDER_AUTHORITY]),
        attr(NS, IDX["exported"], 0xFFFFFFFF, T_BOOLEAN, 0),
        attr(NS, IDX["grantUriPermissions"], 0xFFFFFFFF, T_BOOLEAN, 1),
    ]
    action_attrs = [attr(NS, IDX["name"], IDX["android.intent.action.MAIN"], T_STRING,
                         IDX["android.intent.action.MAIN"])]
    category_attrs = [attr(NS, IDX["name"], IDX["android.intent.category.LAUNCHER"], T_STRING,
                           IDX["android.intent.category.LAUNCHER"])]

    body = b""
    body += ns_chunk(True, IDX["android"], NS)
    body += start_elem(IDX["manifest"], NO_NS, manifest_attrs, 1)
    body += start_elem(IDX["uses-sdk"], NS, uses_sdk_attrs, 2) + end_elem(IDX["uses-sdk"], NS, 2)
    body += start_elem(IDX["uses-permission"], NS, perm_write, 3) + end_elem(IDX["uses-permission"], NS, 3)
    body += start_elem(IDX["uses-permission"], NS, perm_install, 4) + end_elem(IDX["uses-permission"], NS, 4)
    body += start_elem(IDX["application"], NS, app_attrs, 5)
    body += start_elem(IDX["activity"], NS, activity_attrs, 6)
    body += start_elem(IDX["intent-filter"], NS, [], 7)
    body += start_elem(IDX["action"], NS, action_attrs, 8) + end_elem(IDX["action"], NS, 8)
    body += start_elem(IDX["category"], NS, category_attrs, 9) + end_elem(IDX["category"], NS, 9)
    body += end_elem(IDX["intent-filter"], NS, 7)
    body += end_elem(IDX["activity"], NS, 6)
    body += start_elem(IDX["activity"], NS, settings_attrs, 10) + end_elem(IDX["activity"], NS, 10)
    body += start_elem(IDX["activity"], NS, changelog_attrs, 11) + end_elem(IDX["activity"], NS, 11)
    body += start_elem(IDX["provider"], NS, provider_attrs, 12) + end_elem(IDX["provider"], NS, 12)
    body += end_elem(IDX["application"], NS, 5)
    body += end_elem(IDX["manifest"], NO_NS, 1)
    body += ns_chunk(False, IDX["android"], NS)

    pool = build_pool()
    resmap = build_resmap()
    total = 8 + len(pool) + len(resmap) + len(body)
    out = struct.pack("<HHI", 0x0003, 8, total) + pool + resmap + body
    assert len(out) == total
    with open(sys.argv[1], "wb") as f:
        f.write(out)
    print("AXML 生成: %d 字节 -> %s  (v%s / code %s / target %s)"
          % (len(out), sys.argv[1], V.VERSION_NAME, V.VERSION_CODE, V.TARGET_SDK))


if __name__ == "__main__":
    main()
