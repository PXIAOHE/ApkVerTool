package com.seagull.avt.core;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * resources.arsc 读写器。
 *
 * 能力：
 *  - 解析全局值字符串池 / 包 / 类型 / 条目
 *  - 按资源 ID 解析出真实字符串（用于把 android:label 的 @0x7f… 还原成应用名）
 *  - 改写全局字符串池（包名联动），池重建后整段后移，索引保持稳定
 */
public final class Arsc {

    private static final int CHUNK_STRING_POOL = 0x0001;
    private static final int CHUNK_TABLE = 0x0002;
    private static final int CHUNK_PACKAGE = 0x0200;
    private static final int CHUNK_TYPE = 0x0201;

    private static final int TYPE_STRING = 0x03;

    private Arsc() {}

    public static final class Info {
        public String packageName;
        public int packageId;
        public int globalStringCount;
        public int typeCount;
        public boolean utf8Pool;
    }

    public static final class Result {
        public byte[] arsc;
        public int changed;
        public List<String> samples = new ArrayList<String>();

        public boolean isChanged() {
            return changed > 0;
        }
    }

    public static boolean looksLikeArsc(byte[] a) {
        return a != null && a.length >= 12 && u16(a, 0) == CHUNK_TABLE;
    }

    // ================= 读 =================

    public static Info read(byte[] a) throws Exception {
        Info info = new Info();
        int pos = 12;
        while (pos + 8 <= a.length) {
            int type = u16(a, pos);
            int size = u32(a, pos + 4);
            if (size <= 0 || pos + size > a.length) break;
            if (type == CHUNK_STRING_POOL) {
                info.globalStringCount = u32(a, pos + 8);
                info.utf8Pool = (u32(a, pos + 16) & 0x100) != 0;
            } else if (type == CHUNK_PACKAGE) {
                info.packageId = u32(a, pos + 8);
                info.packageName = readPackageName(a, pos);
                info.typeCount++;
            }
            pos += size;
        }
        return info;
    }

    private static String readPackageName(byte[] a, int pkgOff) {
        StringBuilder sb = new StringBuilder(64);
        for (int i = 0; i < 128; i++) {
            int o = pkgOff + 12 + i * 2;
            if (o + 1 >= a.length) break;
            char c = (char) u16(a, o);
            if (c == 0) break;
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 按资源 ID 解析字符串值。支持 default 配置；返回 null 表示不是字符串型或找不到。
     */
    public static String resolve(byte[] a, int resId) {
        try {
            return resolve(a, resId, null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String resolve(byte[] a, int resId, String locale) throws Exception {
        int pkgId = (resId >>> 24) & 0xFF;
        int typeId = (resId >>> 16) & 0xFF;
        int entryId = resId & 0xFFFF;
        int globalPoolOff = findChunk(a, 12, CHUNK_STRING_POOL, a.length);

        int pos = 12;
        while (pos + 8 <= a.length) {
            int type = u16(a, pos);
            int size = u32(a, pos + 4);
            if (size <= 0 || pos + size > a.length) break;
            if (type == CHUNK_PACKAGE && u32(a, pos + 8) == pkgId) {
                String v = resolveInPackage(a, pos, size, globalPoolOff, typeId, entryId);
                if (v != null) return v;
            }
            pos += size;
        }
        return null;
    }

    private static String resolveInPackage(byte[] a, int pkgOff, int pkgSize, int globalPoolOff,
                                           int typeId, int entryId) throws Exception {
        int pos = pkgOff + u16(a, pkgOff + 2);
        int end = pkgOff + pkgSize;
        int best = -1;
        while (pos + 8 <= end && pos + 8 <= a.length) {
            int type = u16(a, pos);
            int size = u32(a, pos + 4);
            if (size <= 0 || pos + size > a.length) break;
            if (type == CHUNK_TYPE && (a[pos + 8] & 0xFF) == typeId && isDefaultConfig(a, pos)) {
                best = pos;
                break;
            }
            pos += size;
        }
        if (best < 0) return null;
        int value = entryValue(a, best, entryId);
        if (value < 0) return null;
        int dataType = a[value + 3] & 0xFF;
        int data = u32(a, value + 4);
        if (dataType == TYPE_STRING) return poolString(a, globalPoolOff, data);
        if (dataType == 0x01) {
            // 引用型：再解一层
            return resolve(a, data, null);
        }
        return null;
    }

    /** 返回 Res_value 的绝对偏移；-1 表示条目不存在或非简单值。 */
    private static int entryValue(byte[] a, int typeChunkOff, int entryId) {
        int entry = entryHeader(a, typeChunkOff, entryId);
        if (entry < 0) return -1;
        int entryFlags = u16(a, entry + 2);
        if ((entryFlags & 0x0001) != 0) return -1;   // 复杂值（bag）
        int val = entry + u16(a, entry);
        if (val + 8 > a.length) return -1;
        return val;
    }

    /** 返回 ResTable_entry 头的绝对偏移；-1 表示没有这个条目。 */
    private static int entryHeader(byte[] a, int typeChunkOff, int entryId) {
        int headerSize = u16(a, typeChunkOff + 2);
        int flags = a[typeChunkOff + 9] & 0xFF;
        int entryCount = u32(a, typeChunkOff + 12);
        int entriesStart = u32(a, typeChunkOff + 16);
        if (entryId < 0 || entryId >= entryCount) return -1;
        int entryOff = -1;
        if ((flags & 0x01) != 0) {
            // 稀疏条目：u16 idx + u16 offset
            for (int i = 0; i < entryCount; i++) {
                int p = typeChunkOff + headerSize + i * 4;
                if (p + 4 > a.length) return -1;
                if (u16(a, p) == entryId) {
                    entryOff = u16(a, p + 2) * 4;
                    break;
                }
            }
        } else if ((flags & 0x02) != 0) {
            int p = typeChunkOff + headerSize + entryId * 2;
            if (p + 2 > a.length) return -1;
            entryOff = u16(a, p) * 4;
        } else {
            int p = typeChunkOff + headerSize + entryId * 4;
            if (p + 4 > a.length) return -1;
            entryOff = u32(a, p);
        }
        if (entryOff < 0) return -1;
        int entry = typeChunkOff + entriesStart + entryOff;
        if (entry + 8 > a.length) return -1;
        return entry;
    }

    private static boolean isDefaultConfig(byte[] a, int typeChunkOff) {
        int headerSize = u16(a, typeChunkOff + 2);
        int cfgStart = typeChunkOff + 20;
        int cfgEnd = typeChunkOff + headerSize;
        if (cfgEnd > a.length) return false;
        for (int i = cfgStart + 4; i < cfgEnd; i++) {
            if (a[i] != 0) return false;
        }
        return true;
    }

    // ================= 字符串池 =================

    private static int findChunk(byte[] a, int from, int wantType, int limit) {
        int pos = from;
        while (pos + 8 <= Math.min(limit, a.length)) {
            int type = u16(a, pos);
            int size = u32(a, pos + 4);
            if (size <= 0) return -1;
            if (type == wantType) return pos;
            pos += size;
        }
        return -1;
    }

    private static String poolString(byte[] a, int poolOff, int idx) {
        if (poolOff < 0) return null;
        int count = u32(a, poolOff + 8);
        if (idx < 0 || idx >= count) return null;
        boolean utf8 = (u32(a, poolOff + 16) & 0x100) != 0;
        int stringsStart = u32(a, poolOff + 20);
        int off = poolOff + stringsStart + u32(a, poolOff + 28 + 4 * idx);
        return utf8 ? readUtf8(a, off) : readUtf16(a, off);
    }

    private static String readUtf8(byte[] a, int off) {
        int[] r1 = var8(a, off);
        int[] r2 = var8(a, r1[1]);
        int start = r2[1];
        int len = r2[0];
        if (start < 0 || start + len > a.length) return "";
        return Dex.decodeMutf8(a, start, len);
    }

    private static String readUtf16(byte[] a, int off) {
        int first = u16(a, off);
        int len, dataOff;
        if ((first & 0x8000) != 0) {
            len = ((first & 0x7FFF) << 16) | u16(a, off + 2);
            dataOff = off + 4;
        } else {
            len = first;
            dataOff = off + 2;
        }
        if (len < 0 || dataOff + 2 * (long) len > a.length) {
            int end = dataOff;
            while (end + 1 < a.length && !(a[end] == 0 && a[end + 1] == 0)) end += 2;
            len = (end - dataOff) / 2;
        }
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append((char) u16(a, dataOff + 2 * i));
        return sb.toString();
    }

    private static int[] var8(byte[] a, int off) {
        int b0 = a[off] & 0xFF;
        if ((b0 & 0x80) != 0) return new int[]{((b0 & 0x7F) << 8) | (a[off + 1] & 0xFF), off + 2};
        return new int[]{b0, off + 1};
    }

    // ================= 条目取值（图标替换等按资源 ID 取全配置） =================

    /** 某个资源 ID 在某个配置下的取值。 */
    public static final class Value {
        public int type;
        public int data;
        /** type == TYPE_STRING 时为全局池里的字符串（资源文件路径）。 */
        public String string;
        /** 该配置的 density（ResTable_config 里的 density 字段，0 = 未指定）。 */
        public int density;
    }

    /** 收集该资源 ID 在所有配置下的取值（不跟随引用，引用由调用方决定是否展开）。 */
    public static List<Value> allValues(byte[] a, int resId) {
        List<Value> out = new ArrayList<Value>();
        if (a == null || !looksLikeArsc(a)) return out;
        int pkgId = (resId >>> 24) & 0xFF;
        int typeId = (resId >>> 16) & 0xFF;
        int entryId = resId & 0xFFFF;
        int globalPool = findChunk(a, 12, CHUNK_STRING_POOL, a.length);
        int pos = 12;
        while (pos + 8 <= a.length) {
            int type = u16(a, pos);
            int size = u32(a, pos + 4);
            if (size <= 0 || pos + size > a.length) break;
            if (type == CHUNK_PACKAGE && u32(a, pos + 8) == pkgId) {
                int p = pos + u16(a, pos + 2);
                int end = pos + size;
                while (p + 8 <= end && p + 8 <= a.length) {
                    int t2 = u16(a, p);
                    int s2 = u32(a, p + 4);
                    if (s2 <= 0 || p + s2 > end) break;
                    if (t2 == CHUNK_TYPE && (a[p + 8] & 0xFF) == typeId) {
                        int val = entryValue(a, p, entryId);
                        if (val >= 0) {
                            Value v = new Value();
                            v.type = a[val + 3] & 0xFF;
                            v.data = u32(a, val + 4);
                            v.density = densityOf(a, p);
                            if (v.type == TYPE_STRING) v.string = poolString(a, globalPool, v.data);
                            out.add(v);
                        }
                    }
                    p += s2;
                }
            }
            pos += size;
        }
        return out;
    }

    /**
     * 按「类型名 + 条目名」反查资源 ID（清单里写 @mipmap/ic_launcher 这种字面量时用）。
     * 找不到返回 0。
     */
    public static int findByName(byte[] a, String typeName, String entryName) {
        if (a == null || typeName == null || entryName == null) return 0;
        int pos = 12;
        while (pos + 8 <= a.length) {
            int type = u16(a, pos);
            int size = u32(a, pos + 4);
            if (size <= 0 || pos + size > a.length) break;
            if (type == CHUNK_PACKAGE) {
                int pkgId = u32(a, pos + 8);
                int typeStrings = pos + u32(a, pos + 268);
                int keyStrings = pos + u32(a, pos + 276);
                int p = pos + u16(a, pos + 2);
                int end = pos + size;
                while (p + 8 <= end && p + 8 <= a.length) {
                    int t2 = u16(a, p);
                    int s2 = u32(a, p + 4);
                    if (s2 <= 0 || p + s2 > end) break;
                    if (t2 == CHUNK_TYPE) {
                        int tid = a[p + 8] & 0xFF;
                        if (typeName.equals(poolString(a, typeStrings, tid - 1))) {
                            int count = u32(a, p + 12);
                            for (int e = 0; e < count && e <= 0xFFFF; e++) {
                                int eh = entryHeader(a, p, e);
                                if (eh < 0) continue;
                                String key = poolString(a, keyStrings, u32(a, eh + 4));
                                if (entryName.equals(key)) return (pkgId << 24) | (tid << 16) | e;
                            }
                        }
                    }
                    p += s2;
                }
            }
            pos += size;
        }
        return 0;
    }

    /** 该资源 ID 在所有配置下的 Res_value 绝对偏移（用于原地改写值，长度不变）。 */
    public static List<Integer> valueOffsets(byte[] a, int resId) {
        List<Integer> out = new ArrayList<Integer>();
        if (a == null || !looksLikeArsc(a)) return out;
        int pkgId = (resId >>> 24) & 0xFF;
        int typeId = (resId >>> 16) & 0xFF;
        int entryId = resId & 0xFFFF;
        int pos = 12;
        while (pos + 8 <= a.length) {
            int type = u16(a, pos);
            int size = u32(a, pos + 4);
            if (size <= 0 || pos + size > a.length) break;
            if (type == CHUNK_PACKAGE && u32(a, pos + 8) == pkgId) {
                int p = pos + u16(a, pos + 2);
                int end = pos + size;
                while (p + 8 <= end && p + 8 <= a.length) {
                    int t2 = u16(a, p);
                    int s2 = u32(a, p + 4);
                    if (s2 <= 0 || p + s2 > end) break;
                    if (t2 == CHUNK_TYPE && (a[p + 8] & 0xFF) == typeId) {
                        int val = entryValue(a, p, entryId);
                        if (val >= 0) out.add(val);
                    }
                    p += s2;
                }
            }
            pos += size;
        }
        return out;
    }

    /**
     * 把字符串追加到全局池尾部：**已有索引全部不变**，新增项从 firstIndexOut[0] 开始。
     * 池变大后整段后移，这里同步修正 ResTable_header.size。
     */
    public static byte[] appendGlobalStrings(byte[] arsc, List<String> extra, int[] firstIndexOut) throws Exception {
        if (extra == null || extra.isEmpty()) {
            if (firstIndexOut != null && firstIndexOut.length > 0) firstIndexOut[0] = -1;
            return arsc;
        }
        int poolOff = findChunk(arsc, 12, CHUNK_STRING_POOL, arsc.length);
        if (poolOff < 0) throw new Exception("resources.arsc 里找不到全局字符串池");
        int count = u32(arsc, poolOff + 8);
        int styleCount = u32(arsc, poolOff + 12);
        boolean utf8 = (u32(arsc, poolOff + 16) & 0x100) != 0;
        int poolSize = u32(arsc, poolOff + 4);
        int stylesStart = u32(arsc, poolOff + 24);

        String[] strings = new String[count + extra.size()];
        for (int i = 0; i < count; i++) {
            String s = poolString(arsc, poolOff, i);
            strings[i] = s == null ? "" : s;
        }
        for (int i = 0; i < extra.size(); i++) strings[count + i] = extra.get(i);

        byte[] styles = new byte[0];
        if (styleCount > 0 && stylesStart > 0 && stylesStart < poolSize) {
            styles = Arrays.copyOfRange(arsc, poolOff + stylesStart, poolOff + poolSize);
        }
        byte[] newPool = encodePool(strings, utf8, styleCount, styles);

        int tailStart = poolOff + poolSize;
        int tailLen = arsc.length - tailStart;
        byte[] out = new byte[arsc.length - poolSize + newPool.length];
        System.arraycopy(arsc, 0, out, 0, poolOff);
        System.arraycopy(newPool, 0, out, poolOff, newPool.length);
        if (tailLen > 0) System.arraycopy(arsc, tailStart, out, poolOff + newPool.length, tailLen);
        put32(out, 4, out.length);
        if (firstIndexOut != null && firstIndexOut.length > 0) firstIndexOut[0] = count;
        return out;
    }

    /** ResTable_config 里的 density（偏移 14），取不到返回 0。 */
    private static int densityOf(byte[] a, int typeChunkOff) {
        int cfg = typeChunkOff + 20;
        if (cfg + 16 > a.length) return 0;
        if (u32(a, cfg) < 16) return 0;
        return u16(a, cfg + 14);
    }

    // ================= 写 =================

    /** 改写全局字符串池中命中包名的条目（保持顺序 → 索引稳定）。 */
    public static Result patchGlobalStrings(byte[] arsc, PkgRename rename) throws Exception {
        Result r = new Result();
        if (!looksLikeArsc(arsc)) {
            r.arsc = arsc;
            return r;
        }
        int poolOff = findChunk(arsc, 12, CHUNK_STRING_POOL, arsc.length);
        if (poolOff < 0) {
            r.arsc = arsc;
            return r;
        }
        int count = u32(arsc, poolOff + 8);
        int styleCount = u32(arsc, poolOff + 12);
        int flags = u32(arsc, poolOff + 16);
        boolean utf8 = (flags & 0x100) != 0;
        int poolSize = u32(arsc, poolOff + 4);
        int stylesStart = u32(arsc, poolOff + 24);

        String[] strings = new String[count];
        int changed = 0;
        for (int i = 0; i < count; i++) {
            String s = poolString(arsc, poolOff, i);
            strings[i] = s == null ? "" : s;
            String n = rename.apply(strings[i]);
            if (n != null) {
                changed++;
                if (r.samples.size() < 8) r.samples.add(strings[i] + "  →  " + n);
                strings[i] = n;
            }
        }
        r.changed = changed;
        if (changed == 0) {
            r.arsc = arsc;
            return r;
        }

        byte[] styles = new byte[0];
        if (styleCount > 0 && stylesStart > 0 && stylesStart < poolSize) {
            styles = Arrays.copyOfRange(arsc, poolOff + stylesStart, poolOff + poolSize);
        }
        byte[] newPool = encodePool(strings, utf8, styleCount, styles);

        int tailStart = poolOff + poolSize;
        int tailLen = arsc.length - tailStart;
        byte[] out = new byte[arsc.length - poolSize + newPool.length];
        System.arraycopy(arsc, 0, out, 0, poolOff);
        System.arraycopy(newPool, 0, out, poolOff, newPool.length);
        if (tailLen > 0) System.arraycopy(arsc, tailStart, out, poolOff + newPool.length, tailLen);
        put32(out, 4, out.length);   // ResTable_header.size
        r.arsc = out;
        return r;
    }

    private static byte[] encodePool(String[] strings, boolean utf8, int styleCount, byte[] styles) {
        int n = strings.length;
        ByteArrayOutputStream blob = new ByteArrayOutputStream();
        int[] offs = new int[n];
        for (int i = 0; i < n; i++) {
            offs[i] = blob.size();
            if (utf8) {
                byte[] b8 = Dex.encodeMutf8(strings[i]);
                writeVar8(blob, strings[i].length());
                writeVar8(blob, b8.length);
                blob.write(b8, 0, b8.length);
                blob.write(0);
            } else {
                int len = strings[i].length();
                if (len > 0x7FFF) {
                    write16(blob, 0x8000 | (len >>> 16));
                    write16(blob, len & 0xFFFF);
                } else {
                    write16(blob, len);
                }
                for (int j = 0; j < len; j++) write16(blob, strings[i].charAt(j));
                write16(blob, 0);
            }
        }
        while (blob.size() % 4 != 0) blob.write(0);
        byte[] sb = blob.toByteArray();
        byte[] st = styles == null ? new byte[0] : styles;
        int stringsStart = 28 + 4 * n;
        int stylesStart = stringsStart + sb.length;
        int size = stylesStart + st.length;
        int padded = (size + 3) & ~3;
        ByteArrayOutputStream p = new ByteArrayOutputStream(padded);
        write16(p, CHUNK_STRING_POOL);
        write16(p, 28);
        write32(p, padded);
        write32(p, n);
        write32(p, styleCount);
        write32(p, utf8 ? 0x100 : 0x000);
        write32(p, stringsStart);
        write32(p, stylesStart);
        for (int i = 0; i < n; i++) write32(p, offs[i]);
        p.write(sb, 0, sb.length);
        if (st.length > 0) p.write(st, 0, st.length);
        while (p.size() < padded) p.write(0);
        return p.toByteArray();
    }

    private static void writeVar8(ByteArrayOutputStream o, int n) {
        if (n <= 0x7F) o.write(n);
        else { o.write(0x80 | (n >> 8)); o.write(n & 0xFF); }
    }

    // ---------------- 基础工具 ----------------

    static int u16(byte[] a, int off) { return (a[off] & 0xFF) | ((a[off + 1] & 0xFF) << 8); }

    static int u32(byte[] a, int off) {
        return (a[off] & 0xFF) | ((a[off + 1] & 0xFF) << 8)
                | ((a[off + 2] & 0xFF) << 16) | ((a[off + 3] & 0xFF) << 24);
    }

    private static void write16(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
    }

    private static void write32(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
        o.write((v >> 16) & 0xFF);
        o.write((v >> 24) & 0xFF);
    }

    private static void put32(byte[] a, int off, int v) {
        a[off] = (byte) v;
        a[off + 1] = (byte) (v >> 8);
        a[off + 2] = (byte) (v >> 16);
        a[off + 3] = (byte) (v >> 24);
    }
}
