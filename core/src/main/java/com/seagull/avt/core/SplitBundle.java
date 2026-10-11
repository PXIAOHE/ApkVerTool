package com.seagull.avt.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 多包容器处理：XAPK / APKS / 自建 split 压缩包。
 *
 * 逐个子 APK 走 {@link ApkPatcher} 同一条流水线（清单改写 → 包名联动 → 条目增删 →
 * 对齐重打包 → 签名），容器内其余条目原样保留。
 *
 * 额外做两件事：
 *  - APKS 的 toc.pb：按「旧内包 SHA-256 → 新内包 SHA-256」等长回填，避免摘要过期
 *  - XAPK 的 manifest.json：同步 version_name / version_code / package_name
 */
public final class SplitBundle {

    private SplitBundle() {}

    public static final class Report {
        public int innerApks;
        public int patched;
        public int failed;
        public int copiedEntries;
        public int tocDigests;
        public boolean manifestJsonPatched;
        public final List<String> notes = new ArrayList<String>();
        public final List<String> details = new ArrayList<String>();

        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("容器内 APK: ").append(innerApks).append(" 个，成功 ").append(patched)
              .append("，失败 ").append(failed).append('\n');
            sb.append("原样保留条目: ").append(copiedEntries).append(" 个").append('\n');
            if (tocDigests > 0) sb.append("toc.pb 摘要回填: ").append(tocDigests).append(" 处").append('\n');
            if (manifestJsonPatched) sb.append("manifest.json: 已同步版本号").append('\n');
            for (int i = 0; i < details.size(); i++) sb.append("  · ").append(details.get(i)).append('\n');
            for (int i = 0; i < notes.size(); i++) sb.append("注意: ").append(notes.get(i)).append('\n');
            return sb.toString().trim();
        }
    }

    /** 是不是多包容器：是个 zip，且里面有 *.apk，且自己不是 APK。 */
    public static boolean isBundle(File f) {
        String lower = f.getName().toLowerCase();
        if (lower.endsWith(".apk")) return false;
        try {
            ZipFile zf = new ZipFile(f);
            try {
                if (zf.getEntry("AndroidManifest.xml") != null) return false;
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    String n = en.nextElement().getName();
                    if (n.toLowerCase().endsWith(".apk")) return true;
                }
            } finally {
                zf.close();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static Report patch(File src, File dst, ApkPatcher.Job job) throws Exception {
        Report rep = new Report();
        File tmpDir = new File(dst.getAbsoluteFile().getParentFile(), "avt_bundle_" + System.currentTimeMillis());
        tmpDir.mkdirs();
        List<Item> items = new ArrayList<Item>();
        List<byte[]> oldDigests = new ArrayList<byte[]>();
        List<byte[]> newDigests = new ArrayList<byte[]>();
        AxmlPatch.Info baseAfter = null;

        try {
            ZipFile zf = new ZipFile(src);
            try {
                Enumeration<? extends ZipEntry> en = zf.entries();
                int idx = 0;
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String name = e.getName();
                    if (e.isDirectory()) continue;
                    Item it = new Item();
                    it.name = name;
                    it.method = e.getMethod() == ZipEntry.STORED ? ZipEntry.STORED : ZipEntry.DEFLATED;
                    if (name.toLowerCase().endsWith(".apk")) {
                        rep.innerApks++;
                        File in = new File(tmpDir, "in_" + idx + ".apk");
                        File outFile = new File(tmpDir, "out_" + idx + ".apk");
                        copy(zf.getInputStream(e), in);
                        byte[] oldDig = Sha256.ofFile(in);
                        try {
                            ApkPatcher.Outcome oc = ApkPatcher.patch(in, outFile, job);
                            rep.patched++;
                            if (baseAfter == null && oc.after != null && oc.after.versionCode != null) {
                                baseAfter = oc.after;
                            }
                            rep.details.add(name + "  →  " + oc.after.versionName + " (" + oc.after.versionCode + ")");
                            it.file = outFile;
                            oldDigests.add(oldDig);
                            newDigests.add(Sha256.ofFile(outFile));
                        } catch (Throwable t) {
                            rep.failed++;
                            rep.details.add(name + "  →  失败: " + t);
                            it.file = in;
                        }
                    } else {
                        rep.copiedEntries++;
                        it.blob = readAll(zf.getInputStream(e));
                    }
                    items.add(it);
                    idx++;
                }
            } finally {
                zf.close();
            }

            // 回填容器元数据
            for (int i = 0; i < items.size(); i++) {
                Item it = items.get(i);
                if (it.blob == null) continue;
                String lower = it.name.toLowerCase();
                if (lower.endsWith("toc.pb") && !oldDigests.isEmpty()) {
                    int hits = 0;
                    for (int k = 0; k < oldDigests.size(); k++) {
                        hits += Sha256.replaceAll(it.blob, oldDigests.get(k), newDigests.get(k));
                    }
                    if (hits > 0) rep.tocDigests = hits;
                } else if (lower.endsWith("manifest.json")) {
                    byte[] patched = patchManifestJson(it.blob, job, baseAfter);
                    if (patched != it.blob) {
                        it.blob = patched;
                        rep.manifestJsonPatched = true;
                    }
                }
            }

            // 写出容器
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(dst));
            try {
                for (int i = 0; i < items.size(); i++) {
                    Item it = items.get(i);
                    ZipEntry ne = new ZipEntry(it.name);
                    ne.setMethod(it.method);
                    if (it.method == ZipEntry.STORED) {
                        long len = it.blob != null ? it.blob.length : it.file.length();
                        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                        if (it.blob != null) {
                            crc.update(it.blob);
                        } else {
                            InputStream cin = new java.io.FileInputStream(it.file);
                            try {
                                byte[] buf = new byte[1 << 16];
                                int n;
                                while ((n = cin.read(buf)) > 0) crc.update(buf, 0, n);
                            } finally {
                                try { cin.close(); } catch (Throwable ignored) {}
                            }
                        }
                        ne.setCrc(crc.getValue());
                        ne.setSize(len);
                        ne.setCompressedSize(len);
                    }
                    zos.putNextEntry(ne);
                    if (it.blob != null) {
                        zos.write(it.blob);
                    } else {
                        copy(new java.io.FileInputStream(it.file), zos);
                    }
                    zos.closeEntry();
                }
            } finally {
                zos.close();
            }
        } finally {
            deleteTree(tmpDir);
        }
        if (rep.failed > 0) {
            rep.notes.add("有子包处理失败，输出仅供检查，不建议直接分发");
        }
        return rep;
    }

    private static byte[] patchManifestJson(byte[] data, ApkPatcher.Job job, AxmlPatch.Info baseAfter) {
        String s = new String(data, java.nio.charset.Charset.forName("UTF-8"));
        String orig = s;
        if (job.manifest.versionName != null) {
            s = s.replaceAll("(\"version_name\"\\s*:\\s*\")[^\"]*(\")",
                    "$1" + java.util.regex.Matcher.quoteReplacement(job.manifest.versionName) + "$2");
        }
        if (job.manifest.versionCode != null) {
            String v = String.valueOf(job.manifest.versionCode);
            s = s.replaceAll("(\"version_code\"\\s*:\\s*\"?)[0-9]+(\"?\\s*[,}])", "$1" + v + "$2");
        }
        if (job.newPackage != null && job.newPackage.trim().length() > 0) {
            s = s.replaceAll("(\"package_name\"\\s*:\\s*\")[^\"]*(\")",
                    "$1" + java.util.regex.Matcher.quoteReplacement(job.newPackage.trim()) + "$2");
        }
        if (baseAfter != null && job.manifest.targetSdk != null) {
            s = s.replaceAll("(\"target_sdk_version\"\\s*:\\s*\"?)[0-9]+(\"?\\s*[,}])",
                    "$1" + job.manifest.targetSdk + "$2");
        }
        if (s.equals(orig)) return data;
        return s.getBytes(java.nio.charset.Charset.forName("UTF-8"));
    }

    private static final class Item {
        String name;
        int method;
        byte[] blob;
        File file;
    }

    // ---------------- IO ----------------

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            return ZipAlignWriter.readAll(in);
        } finally {
            try { in.close(); } catch (Throwable ignored) {}
        }
    }

    private static void copy(InputStream in, File out) throws IOException {
        OutputStream os = new FileOutputStream(out);
        try {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.flush();
        } finally {
            try { in.close(); } catch (Throwable ignored) {}
            try { os.close(); } catch (Throwable ignored) {}
        }
    }

    private static void copy(InputStream in, OutputStream os) throws IOException {
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        os.flush();
        try { in.close(); } catch (Throwable ignored) {}
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (int i = 0; i < kids.length; i++) deleteTree(kids[i]);
        }
        f.delete();
    }
}
