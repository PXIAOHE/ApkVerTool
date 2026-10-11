package com.seagull.avt.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 包名联动改写：把 APK 里所有引用旧包名的地方一起换掉。
 *
 * 覆盖范围：
 *  - AndroidManifest.xml：整张字符串池按索引改写（package、authorities、组件名、
 *    自定义权限名等全部跟随，且不改变块结构）
 *  - classes*.dex：字符串表全量改写，支持**任意长度**（追加 + 重指向 string_ids）
 *  - resources.arsc：全局值字符串池改写（索引稳定）
 *  - lib/**&#47;*.so：**仅当新包名与旧包名等长**时做原地替换（so 里的字符串按偏移被引用，
 *    改长度会破坏 .rodata 布局），否则跳过并在报告里说明
 */
public final class PackageRenamer {

    private PackageRenamer() {}

    public static final class Report {
        public String oldPkg = "-";
        public String newPkg = "-";
        public boolean manifestChanged;
        public int dexTotal, dexChanged, dexStrings;
        public int soTotal, soChanged, soSkipped;
        public int arscChanged;
        public final List<String> notes = new ArrayList<String>();
        public final List<String> samples = new ArrayList<String>();

        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("包名: ").append(oldPkg).append("  →  ").append(newPkg).append('\n');
            sb.append("清单      : ").append(manifestChanged ? "已改写字符串池" : "无改动").append('\n');
            sb.append("DEX       : ").append(dexChanged).append('/').append(dexTotal).append(" 个改写")
              .append("，共替换 ").append(dexStrings).append(" 条字符串").append('\n');
            sb.append("arsc      : ").append(arscChanged).append(" 条全局字符串").append('\n');
            sb.append("原生库    : ").append(soChanged).append('/').append(soTotal).append(" 个改写");
            if (soSkipped > 0) sb.append("，跳过 ").append(soSkipped).append(" 个");
            if (!notes.isEmpty()) {
                for (int i = 0; i < notes.size(); i++) sb.append('\n').append("注意: ").append(notes.get(i));
            }
            if (!samples.isEmpty()) {
                sb.append('\n').append("样例:");
                for (int i = 0; i < samples.size(); i++) sb.append('\n').append("  ").append(samples.get(i));
            }
            return sb.toString();
        }
    }

    /**
     * 把包名联动改写的结果写进 edit；调用方随后走正常的重打包 + 签名流程。
     */
    public static Report apply(File apk, String newPkg, ZipAlignWriter.Edit edit) throws Exception {
        Report rep = new Report();
        if (newPkg == null || newPkg.trim().length() == 0) {
            rep.notes.add("未指定新包名");
            return rep;
        }
        String target = newPkg.trim();
        byte[] manifest = ZipAlignWriter.extractEntry(apk, "AndroidManifest.xml");
        AxmlPatch.Info mi = AxmlPatch.read(manifest);
        rep.oldPkg = mi.packageName == null ? "-" : mi.packageName;
        rep.newPkg = target;
        if (rep.oldPkg.equals(target)) {
            rep.notes.add("新旧包名相同，未做改动");
            return rep;
        }
        if (rep.oldPkg.equals("-")) {
            rep.notes.add("清单里读不到 package，已跳过");
            return rep;
        }

        PkgRename rename = new PkgRename(rep.oldPkg, target);

        // 1) 清单：整池按索引改写
        byte[] newManifest = AxmlPatch.rewritePool(manifest, rename);
        if (newManifest != manifest) {
            edit.replace.put("AndroidManifest.xml", newManifest);
            rep.manifestChanged = true;
        }
        AxmlPatch.Info check = AxmlPatch.read(newManifest);
        if (!target.equals(check.packageName)) {
            rep.notes.add("清单 package 属性未能改写，已中止以避免半成品");
            edit.replace.remove("AndroidManifest.xml");
            return rep;
        }

        // 2) dex / arsc / so
        boolean soSafe = target.length() == rep.oldPkg.length();
        if (!soSafe) {
            rep.notes.add("新包名长度与旧包名不同，lib 下的 .so 已跳过（so 内字符串按偏移引用，改长度会破坏布局）");
        }
        ZipFile zf = new ZipFile(apk);
        try {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String name = e.getName();
                boolean isDex = isRootDex(name);
                boolean isArsc = "resources.arsc".equals(name);
                boolean isSo = ZipAlignWriter.isSo(name);
                if (!isDex && !isArsc && !isSo) continue;
                if (isSo) rep.soTotal++;
                byte[] data = ZipAlignWriter.readAll(zf.getInputStream(e));
                if (isDex) {
                    rep.dexTotal++;
                    if (!Dex.looksLikeDex(data)) continue;
                    Dex.Result dr = Dex.rewrite(data, rename);
                    if (dr.isChanged()) {
                        rep.dexChanged++;
                        rep.dexStrings += dr.changed;
                        edit.replace.put(name, dr.dex);
                        for (int i = 0; i < dr.samples.size() && rep.samples.size() < 10; i++) {
                            rep.samples.add(name + ": " + dr.samples.get(i));
                        }
                    }
                } else if (isArsc) {
                    if (!Arsc.looksLikeArsc(data)) continue;
                    Arsc.Result ar = Arsc.patchGlobalStrings(data, rename);
                    if (ar.isChanged()) {
                        rep.arscChanged = ar.changed;
                        edit.replace.put(name, ar.arsc);
                        for (int i = 0; i < ar.samples.size() && rep.samples.size() < 10; i++) {
                            rep.samples.add("arsc: " + ar.samples.get(i));
                        }
                    }
                } else if (soSafe) {
                    int n = patchBytes(data, rename);
                    if (n > 0) {
                        rep.soChanged++;
                        edit.replace.put(name, data);
                    }
                } else {
                    rep.soSkipped++;
                }
            }
        } finally {
            zf.close();
        }
        return rep;
    }

    private static boolean isRootDex(String name) {
        if (!name.endsWith(".dex")) return false;
        String base = name.indexOf('/') < 0 ? name : name.substring(name.lastIndexOf('/') + 1);
        return base.startsWith("classes");
    }

    /**
     * 等长原地字节替换（.so 用）。返回替换次数。
     * 边界规则与 PkgRename 一致：斜杠形式后接 '/' 或 ';' 或结束，点号形式后接 '.' 或结束。
     */
    public static int patchBytes(byte[] data, PkgRename r) {
        int n = 0;
        n += replaceBytes(data, r.oldSlash.getBytes(java.nio.charset.Charset.forName("UTF-8")),
                r.newSlash.getBytes(java.nio.charset.Charset.forName("UTF-8")), (byte) '/', (byte) ';');
        n += replaceBytes(data, r.oldDot.getBytes(java.nio.charset.Charset.forName("UTF-8")),
                r.newDot.getBytes(java.nio.charset.Charset.forName("UTF-8")), (byte) '.', (byte) 0);
        return n;
    }

    private static int replaceBytes(byte[] data, byte[] from, byte[] to, byte afterA, byte afterB) {
        if (from.length != to.length || from.length == 0) return 0;
        int hits = 0;
        int i = 0;
        int last = data.length - from.length;
        while (i <= last) {
            if (!match(data, i, from)) { i++; continue; }
            int after = i + from.length;
            boolean ok = after == data.length
                    || data[after] == afterA
                    || (afterB != 0 && data[after] == afterB);
            if (ok) {
                System.arraycopy(to, 0, data, i, to.length);
                hits++;
            }
            i += from.length;
        }
        return hits;
    }

    private static boolean match(byte[] data, int off, byte[] pat) {
        for (int i = 0; i < pat.length; i++) {
            if (data[off + i] != pat[i]) return false;
        }
        return true;
    }
}
