package com.seagull.avt.core;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * APK 重打包引擎。手写 ZIP 写入器，不依赖 zipflinger / apksig 内部实现。
 *
 * 相对 v1 的能力提升：
 *  - 任意条目批量替换 / 新增 / 删除（不再只能换 AndroidManifest.xml）
 *  - 完整 Zip64 支持：条目数 &gt; 65535、单条目或总大小 &gt; 4GB 不再溢出
 *  - 保留原条目的 extra field（过滤掉旧的 0xd935 对齐字段后重算）
 *  - 对齐体检报告：逐条目真实数据偏移与对齐状态
 *  - 压缩级别可调
 */
public final class ZipAlignWriter {

    /** lib/ 下 .so 按 16KB 页对齐（同时满足 4K 与 16K 页设备）。 */
    public static final int ALIGN_SO = 16384;
    /** 其余 STORED 条目 4 字节对齐。 */
    public static final int ALIGN_DEFAULT = 4;

    private static final int SIG_LOCAL = 0x04034b50;
    private static final int SIG_CD = 0x02014b50;
    private static final int SIG_EOCD = 0x06054b50;
    private static final int SIG_EOCD64 = 0x06064b50;
    private static final int SIG_EOCD64_LOC = 0x07064b50;

    private static final int EXTRA_ZIP64 = 0x0001;
    private static final int EXTRA_ANDROID_ALIGN = 0xd935;

    private ZipAlignWriter() {}

    // ================= 对外数据结构 =================

    /** 一次重打包的全部改动。 */
    public static final class Edit {
        /** 条目名 → 新内容（已存在则替换，不存在则新增）。 */
        public final Map<String, byte[]> replace = new LinkedHashMap<String, byte[]>();
        /** 要删除的条目名，或目录前缀（以 / 结尾）。 */
        public final Set<String> delete = new LinkedHashSet<String>();
        /** true = 保留原 V1 签名条目（MT 式不重签输出）。 */
        public boolean keepOldSignature = false;
        /** true = 原样搬运原 APK 的签名块（V2/V3 证书链），MT 式不重签输出用。 */
        public boolean keepSigningBlock = false;
        /** 保留原条目 extra field（默认 true）。 */
        public boolean preserveExtra = true;
        /**
         * deflate 压缩级别 0-9。
         * 默认最高压缩（体积优先）：dex 用 level 1 压出来 8.65MB，level 9 是 7.62MB，
         * 整包差整整 1MB —— 重打包多花几秒完全值。
         */
        public int level = Deflater.BEST_COMPRESSION;

        public Edit put(String name, byte[] data) {
            replace.put(name, data);
            return this;
        }

        public Edit remove(String name) {
            delete.add(name);
            return this;
        }
    }

    /** 单个条目的体检数据。 */
    public static final class EntryStat {
        public String name;
        public int method;
        public long dataOffset;
        public long compSize;
        public long size;
        public int requiredAlign;
        public boolean aligned;

        public String describe() {
            return (aligned ? "  OK  " : "  !   ") + name
                    + "  偏移 " + dataOffset
                    + (requiredAlign > 4 ? "  需 " + requiredAlign + " 对齐" : "")
                    + "  " + (method == ZipEntry.STORED ? "STORED" : "DEFLATE");
        }
    }

    /** 重打包 / 体检报告。 */
    public static final class Report {
        public long size;
        public int entryCount;
        public boolean zip64;
        public int misaligned;
        public int soCount;
        /** 顺序扫描时是否撞到了 APK 签名块（说明签名块之前的文件条目已全部覆盖）。 */
        public boolean signingBlockSeen;
        /** 本次重打包搬运的原 APK 签名块字节数，0 = 没有（原本就没签名块）。 */
        public int signingBlockKept;
        public final List<EntryStat> entries = new ArrayList<EntryStat>();

        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("条目 ").append(entryCount).append(" 个")
              .append("，大小 ").append(size).append(" 字节")
              .append(zip64 ? "，Zip64 已启用" : "")
              .append('\n');
            sb.append("原生库 ").append(soCount).append(" 个");
            if (misaligned > 0) {
                sb.append("，对齐异常 ").append(misaligned).append(" 处：");
                int shown = 0;
                for (int i = 0; i < entries.size() && shown < 8; i++) {
                    EntryStat e = entries.get(i);
                    if (e.aligned) continue;
                    sb.append('\n').append("  ! ").append(e.name)
                      .append(" @ ").append(e.dataOffset)
                      .append(e.requiredAlign > 1 ? "（需 " + e.requiredAlign + " 对齐）" : "");
                    shown++;
                }
                if (misaligned > shown) sb.append('\n').append("  …还有 ").append(misaligned - shown).append(" 处");
            } else {
                sb.append("，对齐正常");
            }
            if (signingBlockSeen) {
                sb.append('\n').append("说明：文件条目已全部覆盖，其后是 APK 签名块与中央目录。");
            }
            if (signingBlockKept > 0) {
                sb.append('\n').append("签名块：已保留原 APK 签名块（V2/V3 证书链，")
                  .append(signingBlockKept).append(" 字节，不重签）");
            }
            return sb.toString();
        }
    }

    // ================= 读 =================

    public static byte[] extractEntry(File zip, String name) throws IOException {
        ZipFile zf = new ZipFile(zip);
        try {
            ZipEntry e = zf.getEntry(name);
            if (e == null) throw new IOException("ZIP 中缺少条目: " + name);
            InputStream in = zf.getInputStream(e);
            try { return readAll(in); } finally { in.close(); }
        } finally {
            zf.close();
        }
    }

    /** 列出全部条目名（保持 ZIP 内顺序）。 */
    public static List<String> listNames(File zip) throws IOException {
        List<String> out = new ArrayList<String>();
        ZipFile zf = new ZipFile(zip);
        try {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) out.add(en.nextElement().getName());
        } finally {
            zf.close();
        }
        return out;
    }

    /**
     * 对齐体检：顺序扫描 local file header，给出每个条目的真实数据偏移与对齐状态。
     * 只读，不修改文件。
     */
    public static Report alignReport(File apk) throws IOException {
        Report r = new Report();
        r.size = apk.length();
        RandomAccessFile raf = new RandomAccessFile(apk, "r");
        try {
            byte[] hdr = new byte[30];
            long pos = 0;
            while (pos + 30 <= raf.length()) {
                raf.seek(pos);
                if (raf.read(hdr, 0, 30) < 30) break;
                if (le32(hdr, 0) != SIG_LOCAL) {
                    if (isSigningBlock(raf, pos)) r.signingBlockSeen = true;
                    break;
                }
                int flags = le16(hdr, 6);
                int method = le16(hdr, 8);
                long compSize = le32u(hdr, 18);
                long size = le32u(hdr, 22);
                int nameLen = le16(hdr, 26);
                int extraLen = le16(hdr, 28);
                if (nameLen <= 0 || nameLen > 4096) break;
                byte[] nb = new byte[nameLen];
                raf.seek(pos + 30);
                raf.readFully(nb);
                String name = new String(nb, "UTF-8");
                long dataOffset = pos + 30 + nameLen + extraLen;

                if (compSize == 0xFFFFFFFFL || size == 0xFFFFFFFFL) {
                    r.zip64 = true;
                    byte[] eb = new byte[extraLen];
                    raf.seek(pos + 30 + nameLen);
                    raf.readFully(eb);
                    long[] z = zip64Local(eb, size, compSize);
                    if (z != null) { size = z[0]; compSize = z[1]; }
                }
                if ((flags & 0x08) != 0) {
                    // 有 data descriptor，无法靠 local header 可靠推进；退回扫描下一个签名
                    long next = findNextLocal(raf, dataOffset);
                    if (next < 0) break;
                    pos = next;
                } else {
                    pos = dataOffset + compSize;
                }

                EntryStat st = new EntryStat();
                st.name = name;
                st.method = method;
                st.dataOffset = dataOffset;
                st.compSize = compSize;
                st.size = size;
                st.requiredAlign = requiredAlign(name, method);
                st.aligned = st.requiredAlign <= 1 || (dataOffset % st.requiredAlign == 0);
                r.entries.add(st);
                if (!st.aligned) r.misaligned++;
                if (isSo(name)) r.soCount++;
            }
        } finally {
            raf.close();
        }
        r.entryCount = r.entries.size();
        return r;
    }

    /**
     * 从 APK 里取出完整的 APK Signing Block（含首尾 size 字段与 magic），没有则返回 null。
     * 结构：size(8) | ID-value pairs | size(8) | "APK Sig Block 42"(16)，整块紧贴中央目录之前。
     */
    public static byte[] extractSigningBlock(File apk) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(apk, "r");
            long len = raf.length();
            if (len < 64) return null;
            // 1) 从尾部找 EOCD（注释最长 65535）
            long scanFrom = Math.max(0, len - 65557);
            long eocd = -1;
            byte[] sig = new byte[4];
            for (long p = len - 22; p >= scanFrom; p--) {
                raf.seek(p);
                if (raf.read(sig, 0, 4) < 4) break;
                if (le32(sig, 0) == SIG_EOCD) { eocd = p; break; }
            }
            if (eocd < 0) return null;
            byte[] e = new byte[22];
            raf.seek(eocd);
            if (raf.read(e, 0, 22) < 22) return null;
            long cdOffset = le32u(e, 16);
            if (cdOffset == 0xFFFFFFFFL) {
                // Zip64：EOCD64 定位器（20 字节）紧跟 EOCD 之前
                if (eocd < 20) return null;
                byte[] loc = new byte[20];
                raf.seek(eocd - 20);
                if (raf.read(loc, 0, 20) < 20) return null;
                if (le32(loc, 0) != SIG_EOCD64_LOC) return null;
                long e64 = le64(loc, 8);
                byte[] b = new byte[56];
                raf.seek(e64);
                if (raf.read(b, 0, 56) < 56) return null;
                if (le32(b, 0) != SIG_EOCD64) return null;
                cdOffset = le64(b, 48);
            }
            if (cdOffset < 32 || cdOffset > len) return null;
            // 2) 中央目录前 16 字节应为 magic，前 24 字节处是块尾 size
            byte[] magic = new byte[16];
            raf.seek(cdOffset - 16);
            if (raf.read(magic, 0, 16) < 16) return null;
            if (!"APK Sig Block 42".equals(new String(magic, "US-ASCII"))) return null;
            byte[] tail = new byte[8];
            raf.seek(cdOffset - 24);
            if (raf.read(tail, 0, 8) < 8) return null;
            long a = le64(tail, 0);
            long start = cdOffset - 8 - a;
            if (start < 0 || start + 8 > cdOffset) return null;
            byte[] head = new byte[8];
            raf.seek(start);
            if (raf.read(head, 0, 8) < 8) return null;
            if (le64(head, 0) != a) return null;
            long size = cdOffset - start;
            if (size <= 0 || size > (8L << 20)) return null;
            byte[] out = new byte[(int) size];
            raf.seek(start);
            raf.readFully(out);
            return out;
        } catch (Throwable t) {
            return null;
        } finally {
            try { if (raf != null) raf.close(); } catch (Throwable ignored) {}
        }
    }

    /**
     * 顺序扫描停止处是否为「文件区结束」：其后可能是 apksig 插入的 4KB 对齐零填充，
     * 再往后才是 APK 签名块，所以这里向后有界搜索签名块 magic。
     */
    private static boolean isSigningBlock(RandomAccessFile raf, long pos) {
        try {
            long remaining = raf.length() - pos;
            int want = (int) Math.min(remaining, 1 << 20);
            if (want < 16) return false;
            byte[] buf = new byte[want];
            raf.seek(pos);
            int got = raf.read(buf, 0, want);
            if (got < 16) return false;
            byte[] magic = "APK Sig Block 42".getBytes("US-ASCII");
            for (int i = 0; i + magic.length <= got; i++) {
                boolean ok = true;
                for (int j = 0; j < magic.length; j++) {
                    if (buf[i + j] != magic[j]) { ok = false; break; }
                }
                if (ok) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static long findNextLocal(RandomAccessFile raf, long from) throws IOException {
        raf.seek(from);
        long max = raf.length() - 4;
        for (long p = from; p <= max; p++) {
            raf.seek(p);
            if (raf.read() == 0x50 && raf.read() == 0x4b && raf.read() == 0x03 && raf.read() == 0x04) return p;
        }
        return -1;
    }

    private static long[] zip64Local(byte[] extra, long size, long compSize) {
        int i = 0;
        while (i + 4 <= extra.length) {
            int id = le16(extra, i);
            int sz = le16(extra, i + 2);
            int body = i + 4;
            if (id == EXTRA_ZIP64 && body + sz <= extra.length) {
                long s = size, c = compSize;
                int p = body;
                if (size == 0xFFFFFFFFL && p + 8 <= body + sz) { s = le64(extra, p); p += 8; }
                if (compSize == 0xFFFFFFFFL && p + 8 <= body + sz) { c = le64(extra, p); p += 8; }
                return new long[]{s, c};
            }
            i = body + sz;
        }
        return null;
    }

    // ================= 写 =================

    /** 兼容旧 API：只替换一个条目。 */
    public static void rewrite(File src, File dst, String replaceName, byte[] replaceBytes) throws IOException {
        rewrite(src, dst, replaceName, replaceBytes, false);
    }

    public static void rewrite(File src, File dst, String replaceName, byte[] replaceBytes,
                               boolean keepOldSignature) throws IOException {
        Edit e = new Edit();
        e.keepOldSignature = keepOldSignature;
        if (replaceName != null) e.replace.put(replaceName, replaceBytes);
        rewrite(src, dst, e);
    }

    public static Report rewrite(File src, File dst, Edit edit) throws IOException {
        Report report = new Report();
        CountingOutputStream out = new CountingOutputStream(
                new BufferedOutputStream(new FileOutputStream(dst), 1 << 16));
        List<Cd> cds = new ArrayList<Cd>();
        Set<String> written = new LinkedHashSet<String>();
        try {
            ZipFile zf = new ZipFile(src);
            try {
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String name = e.getName();
                    if (isV1SignatureEntry(name) && !edit.keepOldSignature) continue;
                    if (isDeleted(name, edit.delete)) continue;
                    byte[] data = readAll(zf.getInputStream(e));
                    if (edit.replace.containsKey(name)) data = edit.replace.get(name);
                    boolean stored = e.getMethod() == ZipEntry.STORED;
                    if (edit.replace.containsKey(name) && isSo(name)) stored = true;
                    byte[] extra = edit.preserveExtra ? filterExtra(e.getExtra()) : null;
                    cds.add(writeEntry(out, name, data, stored, e.getTime(), extra, edit.level, report));
                    written.add(name);
                }
            } finally {
                zf.close();
            }
            for (Map.Entry<String, byte[]> kv : edit.replace.entrySet()) {
                String name = kv.getKey();
                if (written.contains(name)) continue;
                boolean stored = isSo(name) || "resources.arsc".equals(name);
                cds.add(writeEntry(out, name, kv.getValue(), stored, 0L, null, edit.level, report));
                written.add(name);
            }
        } finally {
            // 无论成功与否都要收尾
        }
        // MT 式不重签：把原 APK 的签名块原样搬到中央目录之前。
        // 这样 V2/V3 的证书链（签名者 / 算法 / 指纹）还在，验签详情和 App 自身读 PackageInfo.signatures
        // 拿到的还是原签名；内容被改过，V2/V3 摘要必然对不上、签名实际无效，这是预期的 MT 式行为。
        if (edit.keepSigningBlock) {
            byte[] sigBlock = extractSigningBlock(src);
            if (sigBlock != null) {
                out.write(sigBlock);
                report.signingBlockKept = sigBlock.length;
            }
        }
        long cdStart = out.count;
        for (int i = 0; i < cds.size(); i++) writeCd(out, cds.get(i));
        long cdSize = out.count - cdStart;

        boolean needZip64 = cds.size() > 0xFFFF || cdStart > 0xFFFFFFFFL || cdSize > 0xFFFFFFFFL;
        if (needZip64) report.zip64 = true;
        if (needZip64) {
            long eocd64Pos = out.count;
            write32(out, SIG_EOCD64);
            write64(out, 44);
            write16(out, 45);
            write16(out, 45);
            write32(out, 0);
            write32(out, 0);
            write64(out, cds.size());
            write64(out, cds.size());
            write64(out, cdSize);
            write64(out, cdStart);
            write32(out, SIG_EOCD64_LOC);
            write32(out, 0);
            write64(out, eocd64Pos);
            write32(out, 1);
        }
        write32(out, SIG_EOCD);
        write16(out, 0);
        write16(out, 0);
        write16(out, Math.min(cds.size(), 0xFFFF));
        write16(out, Math.min(cds.size(), 0xFFFF));
        write32(out, (int) Math.min(cdSize, 0xFFFFFFFFL));
        write32(out, (int) Math.min(cdStart, 0xFFFFFFFFL));
        write16(out, 0);
        out.close();
        report.size = dst.length();
        report.entryCount = cds.size();
        return report;
    }

    private static Cd writeEntry(CountingOutputStream out, String name, byte[] data, boolean stored,
                                 long time, byte[] baseExtra, int level, Report report) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(data);
        byte[] payload = stored ? data : deflate(data, level);
        byte[] nameBytes = name.getBytes("UTF-8");
        byte[] extraBase = baseExtra == null ? new byte[0] : baseExtra;

        long localPos = out.count;
        boolean zip64 = payload.length >= 0xFFFFFFFFL || data.length >= 0xFFFFFFFFL;

        byte[] zip64Extra = null;
        if (zip64) {
            zip64Extra = new byte[4 + 16];
            put16(zip64Extra, 0, EXTRA_ZIP64);
            put16(zip64Extra, 2, 16);
            put64(zip64Extra, 4, data.length);
            put64(zip64Extra, 12, payload.length);
        }
        int baseLen = extraBase.length + (zip64Extra == null ? 0 : zip64Extra.length);

        int align = stored ? requiredAlign(name, ZipEntry.STORED) : 1;
        int extraLen = baseLen;
        int padFieldLen = 0;
        if (align > 1) {
            long x = localPos + 30 + nameBytes.length + baseLen;
            int need = (int) ((align - (x % align)) % align);
            if (need == 0) {
                extraLen = baseLen;
            } else if (need >= 4) {
                extraLen = baseLen + need;
                padFieldLen = need;
            } else {
                long y = x + 4;
                int need2 = (int) ((align - (y % align)) % align);
                extraLen = baseLen + 4 + need2;
                padFieldLen = 4 + need2;
            }
        }

        write32(out, SIG_LOCAL);
        write16(out, zip64 ? 45 : 20);
        write16(out, 0);
        write16(out, stored ? ZipEntry.STORED : ZipEntry.DEFLATED);
        int[] dos = toDosTime(time);
        write16(out, dos[0]);
        write16(out, dos[1]);
        write32(out, (int) crc.getValue());
        write32(out, zip64 ? 0xFFFFFFFF : (int) payload.length);
        write32(out, zip64 ? 0xFFFFFFFF : (int) data.length);
        write16(out, nameBytes.length);
        write16(out, extraLen);
        out.write(nameBytes);
        if (extraBase.length > 0) out.write(extraBase);
        if (zip64Extra != null) out.write(zip64Extra);
        if (padFieldLen > 0) {
            // 合法的空对齐字段：{id=0x0000, size} + 零填充
            write16(out, 0x0000);
            write16(out, padFieldLen - 4);
            for (int i = 0; i < padFieldLen - 4; i++) out.write(0);
        }
        long dataOffset = out.count;
        out.write(payload);

        Cd cd = new Cd();
        cd.name = nameBytes;
        cd.crc = (int) crc.getValue();
        cd.compSize = payload.length;
        cd.size = data.length;
        cd.method = stored ? ZipEntry.STORED : ZipEntry.DEFLATED;
        cd.localPos = localPos;
        cd.dosTime = dos[0];
        cd.dosDate = dos[1];
        cd.zip64 = zip64;

        EntryStat st = new EntryStat();
        st.name = name;
        st.method = cd.method;
        st.dataOffset = dataOffset;
        st.compSize = payload.length;
        st.size = data.length;
        st.requiredAlign = align;
        st.aligned = align <= 1 || (dataOffset % align == 0);
        report.entries.add(st);
        if (!st.aligned) report.misaligned++;
        if (isSo(name)) report.soCount++;
        return cd;
    }

    private static void writeCd(CountingOutputStream out, Cd cd) throws IOException {
        boolean zip64 = cd.zip64 || cd.localPos > 0xFFFFFFFFL;
        byte[] z64 = null;
        if (zip64) {
            int n = 0;
            if (cd.size >= 0xFFFFFFFFL) n += 8;
            if (cd.compSize >= 0xFFFFFFFFL) n += 8;
            if (cd.localPos > 0xFFFFFFFFL) n += 8;
            z64 = new byte[4 + n];
            put16(z64, 0, EXTRA_ZIP64);
            put16(z64, 2, n);
            int p = 4;
            if (cd.size >= 0xFFFFFFFFL) { put64(z64, p, cd.size); p += 8; }
            if (cd.compSize >= 0xFFFFFFFFL) { put64(z64, p, cd.compSize); p += 8; }
            if (cd.localPos > 0xFFFFFFFFL) { put64(z64, p, cd.localPos); p += 8; }
        }
        write32(out, SIG_CD);
        write16(out, 0x031E);
        write16(out, zip64 ? 45 : 20);
        write16(out, 0);
        write16(out, cd.method);
        write16(out, cd.dosTime);
        write16(out, cd.dosDate);
        write32(out, cd.crc);
        write32(out, cd.compSize >= 0xFFFFFFFFL ? 0xFFFFFFFF : (int) cd.compSize);
        write32(out, cd.size >= 0xFFFFFFFFL ? 0xFFFFFFFF : (int) cd.size);
        write16(out, cd.name.length);
        write16(out, z64 == null ? 0 : z64.length);
        write16(out, 0);
        write16(out, 0);
        write16(out, 0);
        write32(out, 0);
        write32(out, cd.localPos > 0xFFFFFFFFL ? 0xFFFFFFFF : (int) cd.localPos);
        out.write(cd.name);
        if (z64 != null) out.write(z64);
    }

    // ================= 规则 =================

    /** 该条目要求的对齐字节数。 */
    public static int requiredAlign(String name, int method) {
        if (isSo(name)) return ALIGN_SO;
        if (method == ZipEntry.STORED) return ALIGN_DEFAULT;
        return 1;
    }

    public static boolean isSo(String name) {
        return name.startsWith("lib/") && name.endsWith(".so");
    }

    private static boolean isDeleted(String name, Set<String> delete) {
        if (delete.isEmpty()) return false;
        if (delete.contains(name)) return true;
        for (String d : delete) {
            if (d.endsWith("/") && name.startsWith(d)) return true;
        }
        return false;
    }

    static boolean isV1SignatureEntry(String name) {
        String u = name.toUpperCase();
        if (!u.startsWith("META-INF/")) return false;
        String f = u.substring(9);
        return f.equals("MANIFEST.MF") || f.endsWith(".SF") || f.endsWith(".RSA")
                || f.endsWith(".DSA") || f.endsWith(".EC");
    }

    /** 丢掉旧对齐字段与旧 zip64 字段（重算），其余 extra 原样保留。 */
    private static byte[] filterExtra(byte[] extra) {
        if (extra == null || extra.length < 4) return null;
        ByteArrayOutputStream o = new ByteArrayOutputStream(extra.length);
        int i = 0;
        while (i + 4 <= extra.length) {
            int id = le16(extra, i);
            int sz = le16(extra, i + 2);
            int end = i + 4 + sz;
            if (end > extra.length) break;
            if (id != EXTRA_ZIP64 && id != EXTRA_ANDROID_ALIGN) {
                o.write(extra, i, 4 + sz);
            }
            i = end;
        }
        byte[] r = o.toByteArray();
        return r.length == 0 ? null : r;
    }

    private static byte[] deflate(byte[] data, int level) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length / 2 + 16);
        Deflater d = new Deflater(level, true);
        DeflaterOutputStream dos = new DeflaterOutputStream(bos, d);
        try {
            dos.write(data);
            dos.close();
        } catch (IOException ignored) {}
        d.end();
        return bos.toByteArray();
    }

    private static int[] toDosTime(long javaTime) {
        Calendar c = Calendar.getInstance();
        if (javaTime <= 0) c.set(2026, Calendar.JANUARY, 1, 0, 0, 0);
        else c.setTimeInMillis(javaTime);
        int year = Math.max(1980, c.get(Calendar.YEAR));
        int dosDate = ((year - 1980) << 9) | ((c.get(Calendar.MONTH) + 1) << 5) | c.get(Calendar.DAY_OF_MONTH);
        int dosTime = (c.get(Calendar.HOUR_OF_DAY) << 11) | (c.get(Calendar.MINUTE) << 5) | (c.get(Calendar.SECOND) / 2);
        return new int[]{dosTime, dosDate};
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 16);
        byte[] buf = new byte[1 << 15];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    // ================= 小工具 =================

    private static int le16(byte[] a, int off) { return (a[off] & 0xFF) | ((a[off + 1] & 0xFF) << 8); }

    private static int le32(byte[] a, int off) {
        return (a[off] & 0xFF) | ((a[off + 1] & 0xFF) << 8)
                | ((a[off + 2] & 0xFF) << 16) | ((a[off + 3] & 0xFF) << 24);
    }

    private static long le32u(byte[] a, int off) { return le32(a, off) & 0xFFFFFFFFL; }

    private static long le64(byte[] a, int off) {
        long v = 0;
        for (int i = 7; i >= 0; i--) v = (v << 8) | (a[off + i] & 0xFFL);
        return v;
    }

    private static void put16(byte[] a, int off, int v) {
        a[off] = (byte) v;
        a[off + 1] = (byte) (v >> 8);
    }

    private static void put64(byte[] a, int off, long v) {
        for (int i = 0; i < 8; i++) a[off + i] = (byte) (v >>> (8 * i));
    }

    static void write16(OutputStream o, int v) throws IOException {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
    }

    static void write32(OutputStream o, int v) throws IOException {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
        o.write((v >> 16) & 0xFF);
        o.write((v >> 24) & 0xFF);
    }

    static void write64(OutputStream o, long v) throws IOException {
        for (int i = 0; i < 8; i++) o.write((int) ((v >>> (8 * i)) & 0xFF));
    }

    private static final class Cd {
        byte[] name;
        int crc, dosTime, dosDate, method;
        long compSize, size, localPos;
        boolean zip64;
    }

    private static final class CountingOutputStream extends OutputStream {
        private final OutputStream inner;
        long count = 0;
        CountingOutputStream(OutputStream inner) { this.inner = inner; }
        public void write(int b) throws IOException { inner.write(b); count++; }
        public void write(byte[] b, int off, int len) throws IOException { inner.write(b, off, len); count += len; }
        public void flush() throws IOException { inner.flush(); }
        public void close() throws IOException { inner.close(); }
    }
}
