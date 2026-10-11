package com.seagull.avt.core;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Adler32;

/**
 * DEX 字符串表改写器。支持**任意长度**替换：
 *
 * 思路是「追加 + 重指向」——把整张字符串数据表重建后追加到文件末尾，
 * 只改 string_ids 里的 4 字节绝对偏移。dex 其余部分（type_ids / proto_ids /
 * field_ids / method_ids / class_defs / code）全部通过**字符串索引**引用字符串，
 * 不涉及偏移，所以不需要搬动任何代码。
 *
 * 同时更新 map_list 的 STRING_DATA_ITEM 项（重排序保持 offset 递增）与
 * 头部 file_size / data_size，并重算 SHA-1 签名与 Adler-32 校验和。
 */
public final class Dex {

    private static final int TYPE_STRING_ID_ITEM = 0x0001;
    private static final int TYPE_STRING_DATA_ITEM = 0x2002;

    private Dex() {}

    /** 改写结果。 */
    public static final class Result {
        public byte[] dex;
        public int changed;
        public int total;
        public List<String> samples = new ArrayList<String>();

        public boolean isChanged() {
            return changed > 0;
        }
    }

    public static boolean looksLikeDex(byte[] d) {
        return d != null && d.length >= 112
                && d[0] == 'd' && d[1] == 'e' && d[2] == 'x' && d[3] == '\n';
    }

    /** 读取全部字符串（供检索/统计）。 */
    public static List<String> strings(byte[] dex) throws Exception {
        List<String> out = new ArrayList<String>();
        if (!looksLikeDex(dex)) return out;
        int count = u32(dex, 56);
        int off = u32(dex, 60);
        if (count < 0 || off < 0 || off + 4 * (long) count > dex.length) return out;
        for (int i = 0; i < count; i++) {
            int p = u32(dex, off + 4 * i);
            out.add(readString(dex, p));
        }
        return out;
    }

    public static Result rewrite(byte[] dex, PkgRename rename) throws Exception {
        Result r = new Result();
        if (!looksLikeDex(dex)) {
            r.dex = dex;
            return r;
        }
        int stringCount = u32(dex, 56);
        int stringOff = u32(dex, 60);
        int mapOff = u32(dex, 52);
        int dataOff = u32(dex, 108);
        if (stringCount < 0 || stringOff < 0 || stringOff + 4L * stringCount > dex.length) {
            r.dex = dex;
            return r;
        }

        String[] old = new String[stringCount];
        String[] now = new String[stringCount];
        int changed = 0;
        for (int i = 0; i < stringCount; i++) {
            int p = u32(dex, stringOff + 4 * i);
            String s = readString(dex, p);
            old[i] = s;
            String n = rename.apply(s);
            now[i] = n == null ? s : n;
            if (n != null) {
                changed++;
                if (r.samples.size() < 12) r.samples.add(s + "  →  " + n);
            }
        }
        r.total = stringCount;
        r.changed = changed;
        if (changed == 0) {
            r.dex = dex;
            return r;
        }

        // 重建整张字符串数据表
        ByteArrayOutputStream blob = new ByteArrayOutputStream(dex.length / 8 + 1024);
        int[] newOffsets = new int[stringCount];
        for (int i = 0; i < stringCount; i++) {
            newOffsets[i] = blob.size();
            byte[] b8 = encodeMutf8(now[i]);
            writeUleb128(blob, now[i].length());
            blob.write(b8, 0, b8.length);
            blob.write(0);
        }
        byte[] blobBytes = blob.toByteArray();

        int blobStart = (dex.length + 3) & ~3;
        int newLen = blobStart + blobBytes.length;
        byte[] out = Arrays.copyOf(dex, newLen);
        System.arraycopy(blobBytes, 0, out, blobStart, blobBytes.length);

        // string_ids 重指向
        for (int i = 0; i < stringCount; i++) {
            put32(out, stringOff + 4 * i, blobStart + newOffsets[i]);
        }

        // map_list：更新 STRING_DATA_ITEM 并保持 offset 递增
        if (mapOff > 0 && mapOff + 4 <= out.length) {
            int n = u32(out, mapOff);
            int base = mapOff + 4;
            if (n > 0 && n < 4096 && base + 12L * n <= out.length) {
                List<int[]> items = new ArrayList<int[]>();
                for (int i = 0; i < n; i++) {
                    int t = u16(out, base + 12 * i);
                    int sz = u32(out, base + 12 * i + 4);
                    int of = u32(out, base + 12 * i + 8);
                    if (t == TYPE_STRING_DATA_ITEM) {
                        sz = blobBytes.length;
                        of = blobStart;
                    }
                    items.add(new int[]{t, sz, of});
                }
                // 按 offset 稳定排序
                for (int i = 1; i < items.size(); i++) {
                    int[] cur = items.get(i);
                    int j = i - 1;
                    while (j >= 0 && items.get(j)[2] > cur[2]) {
                        items.set(j + 1, items.get(j));
                        j--;
                    }
                    items.set(j + 1, cur);
                }
                for (int i = 0; i < n; i++) {
                    int[] it = items.get(i);
                    put16(out, base + 12 * i, it[0]);
                    put16(out, base + 12 * i + 2, 0);
                    put32(out, base + 12 * i + 4, it[1]);
                    put32(out, base + 12 * i + 8, it[2]);
                }
            }
        }

        // 头部
        put32(out, 32, newLen);
        put32(out, 104, newLen - dataOff);
        fixDigests(out);

        r.dex = out;
        return r;
    }

    /** 重算 SHA-1 签名（[32,end)）与 Adler-32 校验和（[12,end)）。 */
    public static void fixDigests(byte[] dex) throws Exception {
        int len = dex.length;
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(dex, 32, len - 32);
        byte[] sig = sha1.digest();
        System.arraycopy(sig, 0, dex, 12, 20);
        Adler32 adler = new Adler32();
        adler.update(dex, 12, len - 12);
        put32(dex, 8, (int) adler.getValue());
    }

    /** 校验 dex 头部摘要是否正确（自检用）。 */
    public static boolean verifyDigests(byte[] dex) throws Exception {
        if (!looksLikeDex(dex)) return false;
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(dex, 32, dex.length - 32);
        byte[] sig = sha1.digest();
        for (int i = 0; i < 20; i++) if (sig[i] != dex[12 + i]) return false;
        Adler32 adler = new Adler32();
        adler.update(dex, 12, dex.length - 12);
        return (int) adler.getValue() == u32(dex, 8);
    }

    // ---------------- 字符串编解码 ----------------

    private static String readString(byte[] d, int off) {
        if (off <= 0 || off >= d.length) return "";
        int[] r = readUleb128(d, off);
        int size = r[0];
        int p = r[1];
        int end = p;
        while (end < d.length && d[end] != 0) end++;
        String s = decodeMutf8(d, p, end - p);
        if (size >= 0 && size <= s.length()) return s;
        return s;
    }

    static int[] readUleb128(byte[] d, int off) {
        int result = 0, shift = 0, p = off;
        while (p < d.length) {
            int b = d[p++] & 0xFF;
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return new int[]{result, p};
    }

    static void writeUleb128(ByteArrayOutputStream o, int v) {
        do {
            int b = v & 0x7F;
            v >>>= 7;
            if (v != 0) b |= 0x80;
            o.write(b);
        } while (v != 0);
    }

    static String decodeMutf8(byte[] b, int off, int len) {
        StringBuilder sb = new StringBuilder(len);
        int i = off, end = off + len;
        while (i < end) {
            int x = b[i] & 0xFF;
            char c;
            if ((x & 0x80) == 0) { c = (char) x; i += 1; }
            else if ((x & 0xE0) == 0xC0) {
                if (i + 1 >= end) break;
                c = (char) (((x & 0x1F) << 6) | (b[i + 1] & 0x3F)); i += 2;
            } else {
                if (i + 2 >= end) break;
                c = (char) (((x & 0x0F) << 12) | ((b[i + 1] & 0x3F) << 6) | (b[i + 2] & 0x3F)); i += 3;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    static byte[] encodeMutf8(String s) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 0) { o.write(0xC0); o.write(0x80); }
            else if (c < 0x80) o.write(c);
            else if (c < 0x800) { o.write(0xC0 | (c >> 6)); o.write(0x80 | (c & 0x3F)); }
            else { o.write(0xE0 | (c >> 12)); o.write(0x80 | ((c >> 6) & 0x3F)); o.write(0x80 | (c & 0x3F)); }
        }
        return o.toByteArray();
    }

    // ---------------- 基础工具 ----------------

    static int u16(byte[] a, int off) { return (a[off] & 0xFF) | ((a[off + 1] & 0xFF) << 8); }

    static int u32(byte[] a, int off) {
        return (a[off] & 0xFF) | ((a[off + 1] & 0xFF) << 8)
                | ((a[off + 2] & 0xFF) << 16) | ((a[off + 3] & 0xFF) << 24);
    }

    static void put16(byte[] a, int off, int v) {
        a[off] = (byte) v;
        a[off + 1] = (byte) (v >> 8);
    }

    static void put32(byte[] a, int off, int v) {
        a[off] = (byte) v;
        a[off + 1] = (byte) (v >> 8);
        a[off + 2] = (byte) (v >> 16);
        a[off + 3] = (byte) (v >> 24);
    }
}
