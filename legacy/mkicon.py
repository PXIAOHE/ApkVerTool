#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成应用图标 PNG (res/mipmap/ic_launcher.png) 与最小 resources.arsc。无 aapt2，纯手搓。
布局参考真机 arsc 地面真值：TYPE chunk 经典格式，UTF-16 池 u16 长度前缀。"""
import struct, zlib, os, sys

OUT = sys.argv[1] if len(sys.argv) > 1 else "build"
PKG = "com.seagull.apkvertool"

# ---------------- 图标 512x512 ----------------
W = H = 512
R = 118.0
TOP = (0x4F, 0x46, 0xE5)   # indigo-600
BOT = (0x0E, 0xA5, 0xE9)   # sky-500
AMBER = (0xF5, 0x9E, 0x0B)
WHITE = (255, 255, 255)
SEGS = [((172, 150), (256, 390)), ((340, 150), (256, 390))]
STROKE = 30.0
BADGE = (354, 174)
BADGE_R = 56.0

def seg_dist(px, py, a, b):
    ax, ay = a; bx, by = b
    dx, dy = bx - ax, by - ay
    t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
    t = max(0.0, min(1.0, t))
    return ((px - ax - t * dx) ** 2 + (py - ay - t * dy) ** 2) ** 0.5

def inside_round(x, y):
    cx = min(max(x, R), W - R)
    cy = min(max(y, R), H - R)
    return (x - cx) ** 2 + (y - cy) ** 2 <= R * R

def render():
    SS = 3
    raw = bytearray()
    for y in range(H):
        raw.append(0)
        for x in range(W):
            cr = cg = cb = ca = 0
            for sy in range(SS):
                for sx in range(SS):
                    px = x + (sx + 0.5) / SS
                    py = y + (sy + 0.5) / SS
                    if not inside_round(px, py):
                        continue
                    ca += 1
                    t = (px + py) / (W + H)
                    col = tuple(int(TOP[i] + (BOT[i] - TOP[i]) * t) for i in range(3))
                    d = min(seg_dist(px, py, *SEGS[0]), seg_dist(px, py, *SEGS[1]))
                    if d <= STROKE:
                        col = WHITE
                    bd = ((px - BADGE[0]) ** 2 + (py - BADGE[1]) ** 2) ** 0.5
                    if abs(bd - BADGE_R) <= 3.5:
                        col = WHITE
                    elif bd < BADGE_R:
                        col = AMBER
                    cr += col[0]; cg += col[1]; cb += col[2]
            if ca == 0:
                raw += b"\x00\x00\x00\x00"
            else:
                n = SS * SS
                raw += bytes((cr // ca, cg // ca, cb // ca, 255 * ca // n))
    return bytes(raw)

def png_chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

def make_png():
    raw = render()
    png = b"\x89PNG\r\n\x1a\n"
    png += png_chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0))
    png += png_chunk(b"IDAT", zlib.compress(raw, 9))
    png += png_chunk(b"IEND", b"")
    return png

# ---------------- 字符串池 ----------------
def utf16_pool(strings):
    """UTF-16 池（flags=0），条目 [u16 len][UTF-16LE][0000]，参考真机格式。"""
    blob = bytearray()
    offs = []
    for s in strings:
        offs.append(len(blob))
        blob += struct.pack("<H", len(s)) + s.encode("utf-16-le") + b"\x00\x00"
    while len(blob) % 4:
        blob += b"\x00"
    start = 28 + 4 * len(strings)
    total = start + len(blob)
    hdr = struct.pack("<HHIIIIII", 0x0001, 28, total, len(strings), 0, 0x000, start, 0)
    return hdr + b"".join(struct.pack("<I", o) for o in offs) + bytes(blob)

def utf8_pool(strings):
    blob = bytearray()
    offs = []
    for s in strings:
        offs.append(len(blob))
        u8 = s.encode("utf-8")
        item = bytearray()
        for v in (len(s), len(u8)):
            if v <= 0x7F:
                item.append(v)
            else:
                item += bytes([0x80 | (v >> 8), v & 0xFF])
        item += u8 + b"\x00"
        blob += item
    while len(blob) % 4:
        blob += b"\x00"
    start = 28 + 4 * len(strings)
    total = start + len(blob)
    hdr = struct.pack("<HHIIIIII", 0x0001, 28, total, len(strings), 0, 0x100, start, start)
    return hdr + b"".join(struct.pack("<I", o) for o in offs) + bytes(blob)

# ---------------- resources.arsc（现代格式，逐字段对照真机活样本） ----------------
def make_arsc():
    # 全局池：Res_value 的 STRING 值索引到这里 —— 文件路径放这
    global_pool = utf8_pool(["res/mipmap/ic_launcher.png"])
    type_pool = utf16_pool(["mipmap"])
    key_pool = utf8_pool(["ic_launcher"])

    # RES_TABLE_TYPE_SPEC (0x0202): id=1, res0=0, res1=1(现代), entryCount=1, flags=0
    spec = struct.pack("<HHIBBHI", 0x0202, 16, 20, 1, 0, 1, 1) + b"\x00" * 4

    # RES_TABLE_TYPE (0x0201) 现代布局：
    #   +8 id u8 / +9 res0 / +10 res1 u16 / +12 entryCount u32 / +16 entriesStart u32 / +20 config
    #   headerSize = 20 + configSize；offset 数组在 headerSize 处；entriesStart 指向条目数据
    config = struct.pack("<I", 64) + b"\x00" * 60
    hdr_size = 20 + len(config)          # 84
    entries_start = hdr_size + 4 * 1     # 88 = header + offset 数组
    type_size = entries_start + 8 + 8   # entry(8) + Res_value(8)
    t = struct.pack("<HHI", 0x0201, hdr_size, type_size)
    t += struct.pack("<BBHI", 1, 0, 0, 1)          # id=1, res0=0, res1=0, entryCount=1
    t += struct.pack("<I", entries_start)          # entriesStart
    t += config                                     # 64 字节 config
    t += struct.pack("<I", 0)                       # 条目 offset[0] = 0
    t += struct.pack("<HHI", 8, 0, 0)               # entry: size=8, flags=0(简单), key=0(ic_launcher)
    t += struct.pack("<HBBI", 8, 0, 0x03, 0)        # Res_value: STRING -> 全局池[0]

    # package (0x0200)
    type_off = 288
    key_off = 288 + len(type_pool)
    pkg_content = type_pool + key_pool + spec + t
    pkg_size = 288 + len(pkg_content)
    name16 = PKG.encode("utf-16-le") + b"\x00\x00"
    name16 += b"\x00" * (256 - len(name16))
    pkg = struct.pack("<HHI", 0x0200, 288, pkg_size)
    pkg += struct.pack("<I", 0x7F)
    pkg += name16
    pkg += struct.pack("<IIIII", type_off, 1, key_off, 1, 0)
    pkg += pkg_content

    # table (0x0002)
    total = 12 + len(global_pool) + len(pkg)
    arsc = struct.pack("<HHI", 0x0002, 12, total) + struct.pack("<I", 1)
    arsc += global_pool
    arsc += pkg
    return arsc

def validate(arsc):
    """自检：按现代格式回读。"""
    assert struct.unpack_from("<H", arsc, 0)[0] == 0x0002
    assert struct.unpack_from("<I", arsc, 4)[0] == len(arsc)
    gpsize = struct.unpack_from("<I", arsc, 16)[0]
    gcnt = struct.unpack_from("<I", arsc, 20)[0]
    pos = 12 + gpsize
    pt, phs, ps = struct.unpack_from("<HHI", arsc, pos)
    assert pt == 0x0200 and phs == 288, (hex(pt), phs)
    p2 = pos + 288
    end = pos + ps
    chunks = []
    while p2 < end:
        ct, chs, cs = struct.unpack_from("<HHI", arsc, p2)
        chunks.append(hex(ct))
        if ct == 0x0201:
            ecnt = struct.unpack_from("<I", arsc, p2 + 12)[0]
            est = struct.unpack_from("<I", arsc, p2 + 16)[0]
            assert chs == 84 and est == chs + 4 * ecnt, (chs, est)
            eoff = struct.unpack_from("<I", arsc, p2 + chs)[0]
            esz, efl, ekey = struct.unpack_from("<HHI", arsc, p2 + est + eoff)
            vsz, vr, vt, vd = struct.unpack_from("<HBBI", arsc, p2 + est + eoff + 8)
            assert esz == 8 and efl == 0 and vt == 0x03 and vd < gcnt
        p2 += cs
    assert p2 == end, "chunk 链不闭合"
    print("arsc 自检通过:", len(arsc), "字节, 全局池", gcnt, "条, chunks:", chunks)

if __name__ == "__main__":
    icon_dir = os.path.join(OUT, "res", "mipmap")
    os.makedirs(icon_dir, exist_ok=True)
    png = make_png()
    with open(os.path.join(icon_dir, "ic_launcher.png"), "wb") as f:
        f.write(png)
    print("图标:", len(png), "字节 ->", icon_dir + "/ic_launcher.png")
    arsc = make_arsc()
    validate(arsc)
    with open(os.path.join(OUT, "resources.arsc"), "wb") as f:
        f.write(arsc)
    print("resources.arsc:", len(arsc), "字节 ->", OUT + "/resources.arsc")
