package com.seagull.avt.cli;

import com.seagull.avt.core.ApkInfo;
import com.seagull.avt.core.ApkPatcher;
import com.seagull.avt.core.Arsc;
import com.seagull.avt.core.AxmlPatch;
import com.seagull.avt.core.Dex;
import com.seagull.avt.core.PackageRenamer;
import com.seagull.avt.core.PkgRename;
import com.seagull.avt.core.Sha256;
import com.seagull.avt.core.SignUtil;
import com.seagull.avt.core.SplitBundle;
import com.seagull.avt.core.ZipAlignWriter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** 桌面端命令行：构建机上的核心逻辑验证与批处理入口（不依赖 Android）。 */
public class AvtCli {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) { usage(); return; }
        String cmd = args[0];
        if ("info".equals(cmd)) {
            System.out.println(ApkInfo.analyze(new File(args[1])).describe());
        } else if ("read".equals(cmd)) {
            byte[] m = ZipAlignWriter.extractEntry(new File(args[1]), "AndroidManifest.xml");
            AxmlPatch.Info i = AxmlPatch.read(m);
            System.out.println("versionName=" + i.versionName);
            System.out.println("versionCode=" + i.versionCode);
            System.out.println("versionCodeMajor=" + i.versionCodeMajor);
            System.out.println("minSdk=" + i.minSdk);
            System.out.println("targetSdk=" + i.targetSdk);
            System.out.println("package=" + i.packageName);
        } else if ("manifest".equals(cmd)) {
            byte[] m = ZipAlignWriter.extractEntry(new File(args[1]), "AndroidManifest.xml");
            System.out.println(AxmlPatch.read(m).describe());
        } else if ("align".equals(cmd)) {
            System.out.println(ZipAlignWriter.alignReport(new File(args[1])).describe());
        } else if ("list".equals(cmd)) {
            List<String> names = ZipAlignWriter.listNames(new File(args[1]));
            for (int i = 0; i < names.size(); i++) System.out.println(names.get(i));
            System.out.println("-- 共 " + names.size() + " 个条目");
        } else if ("res".equals(cmd)) {
            // res <apk> <0x7f11003e>
            byte[] arsc = ZipAlignWriter.extractEntry(new File(args[1]), "resources.arsc");
            Arsc.Info ri = Arsc.read(arsc);
            System.out.println("arsc 包名: " + ri.packageName + "  id=0x" + Integer.toHexString(ri.packageId)
                    + "  全局字符串 " + ri.globalStringCount + " 条  池编码 "
                    + (ri.utf8Pool ? "UTF-8" : "UTF-16"));
            int id = (int) Long.parseLong(args[2].replace("0x", ""), 16);
            System.out.println("解析 0x" + Integer.toHexString(id) + " = " + Arsc.resolve(arsc, id));
        } else if ("dexstrings".equals(cmd)) {
            byte[] dex = ZipAlignWriter.extractEntry(new File(args[1]), args.length > 2 ? args[2] : "classes.dex");
            List<String> ss = Dex.strings(dex);
            String needle = args.length > 3 ? args[3] : "";
            int shown = 0;
            for (int i = 0; i < ss.size(); i++) {
                if (needle.length() > 0 && ss.get(i).indexOf(needle) < 0) continue;
                System.out.println(ss.get(i));
                if (++shown >= 200) { System.out.println("…"); break; }
            }
            System.out.println("-- 共 " + ss.size() + " 条字符串");
        } else if ("patch".equals(cmd)) {
            patch(args, false);
        } else if ("bundle".equals(cmd)) {
            patch(args, true);
        } else if ("renamepkg".equals(cmd)) {
            renamePkg(args);
        } else if ("repack".equals(cmd)) {
            repack(args);
        } else if ("run".equals(cmd)) {
            run(args);
        } else if ("sign".equals(cmd)) {
            byte[] p12 = readAll(new FileInputStream(args[3]));
            SignUtil.sign(new File(args[1]), new File(args[2]), p12, SignUtil.PASS,
                    Integer.parseInt(args[4]));
            System.out.println("已签名: " + args[2]);
            System.out.println(SignUtil.verify(new File(args[2]), Integer.parseInt(args[4])));
        } else if ("verify".equals(cmd)) {
            byte[] mm = ZipAlignWriter.extractEntry(new File(args[1]), "AndroidManifest.xml");
            int ms = AxmlPatch.readMinSdk(mm);
            System.out.println(SignUtil.verifyDetail(new File(args[1]), ms).describe());
        } else if ("keygen".equals(cmd)) {
            String cn = args.length > 2 ? args[2] : "ApkVerTool";
            int days = args.length > 3 ? Integer.parseInt(args[3]) : 10950;
            String alias = SignUtil.generateKey(new File(args[1]), SignUtil.PASS, cn,
                    "Seagull", "Seagull", days, 2048);
            System.out.println("已生成密钥: " + args[1] + "  alias=" + alias
                    + "  口令=" + new String(SignUtil.PASS));
        } else if ("selftest".equals(cmd)) {
            selftest(args);
        } else {
            usage();
        }
    }

    private static void usage() {
        System.out.println("AvtCli <命令> ...");
        System.out.println("  info <apk>                            APK 体检（应用名会从 arsc 解析）");
        System.out.println("  read <apk>                            只读版本号 / SDK / 包名");
        System.out.println("  manifest <apk>                        清单摘要");
        System.out.println("  align <apk>                           对齐体检");
        System.out.println("  list <apk>                            列出全部条目");
        System.out.println("  res <apk> <0x7f11003e>                解析 resources.arsc 资源 ID");
        System.out.println("  dexstrings <apk> [dex] [关键字]        导出 dex 字符串表");
        System.out.println("  patch <in> <out> [选项]               通用改写（自动识别多包容器）");
        System.out.println("  bundle <in> <out> [选项]              强制按 XAPK / APKS 容器处理");
        System.out.println("  renamepkg <in> <out> <新包名> [--sign <p12>]   包名联动改写");
        System.out.println("  repack <in> <out> <name|-> <code|->  仅重打包（不签名）");
        System.out.println("  run <in> <out> <name|-> <code|-> <p12> <minSdk>");
        System.out.println("  sign <in> <out> <p12> <minSdk>        仅签名");
        System.out.println("  verify <apk>                          验签详情");
        System.out.println("  keygen <out.p12> [CN] [天数]          生成自签密钥");
        System.out.println("  selftest <apk>                        端到端自检");
        System.out.println();
        System.out.println("patch / bundle 选项：");
        System.out.println("  --name V            新 versionName");
        System.out.println("  --code N            新 versionCode");
        System.out.println("  --code-major N      新 versionCodeMajor（64 位版本号高位）");
        System.out.println("  --min-sdk N         新 minSdkVersion");
        System.out.println("  --target-sdk N      新 targetSdkVersion");
        System.out.println("  --package P         新包名（联动 dex / arsc / so / 清单）");
        System.out.println("  --label L           新应用名");
        System.out.println("  --debuggable B      true/false");
        System.out.println("  --extract-native-libs B");
        System.out.println("  --utf8-pool         强制把字符串池重建为 UTF-8");
        System.out.println("  --add-perm P        追加权限（可重复）");
        System.out.println("  --del-perm P        删除权限（可重复）");
        System.out.println("  --drop ENTRY        删除条目或目录前缀（可重复）");
        System.out.println("  --put ENTRY=FILE    用本地文件替换/新增条目（可重复）");
        System.out.println("  --keep-signature    MT 式保留旧 V1 签名条目");
        System.out.println("  --sign <p12>        输出前签名");
    }

    // ---------------- patch / bundle ----------------

    private static void patch(String[] args, boolean forceBundle) throws Exception {
        File in = new File(args[1]);
        File out = new File(args[2]);
        ApkPatcher.Job job = new ApkPatcher.Job();
        job.resign = false;
        String p12 = null;

        for (int i = 3; i < args.length; i++) {
            String k = args[i];
            if ("--name".equals(k)) job.manifest.versionName = args[++i];
            else if ("--code".equals(k)) job.manifest.versionCode = Long.valueOf(Long.parseLong(args[++i]));
            else if ("--code-major".equals(k)) job.manifest.versionCodeMajor = Long.valueOf(Long.parseLong(args[++i]));
            else if ("--min-sdk".equals(k)) job.manifest.minSdk = Integer.valueOf(Integer.parseInt(args[++i]));
            else if ("--target-sdk".equals(k)) job.manifest.targetSdk = Integer.valueOf(Integer.parseInt(args[++i]));
            else if ("--package".equals(k) || "--rename-pkg".equals(k)) job.newPackage = args[++i];
            else if ("--label".equals(k)) job.manifest.label = args[++i];
            else if ("--debuggable".equals(k)) job.manifest.debuggable = Boolean.valueOf(Boolean.parseBoolean(args[++i]));
            else if ("--extract-native-libs".equals(k)) job.manifest.extractNativeLibs = Boolean.valueOf(Boolean.parseBoolean(args[++i]));
            else if ("--utf8-pool".equals(k)) job.manifest.forceUtf8 = true;
            else if ("--add-perm".equals(k)) job.manifest.addPermissions.add(args[++i]);
            else if ("--del-perm".equals(k)) job.manifest.removePermissions.add(args[++i]);
            else if ("--drop".equals(k)) job.edit.delete.add(args[++i]);
            else if ("--put".equals(k)) {
                String v = args[++i];
                int eq = v.indexOf('=');
                job.edit.replace.put(v.substring(0, eq), readAll(new FileInputStream(v.substring(eq + 1))));
            } else if ("--keep-signature".equals(k)) {
                job.edit.keepOldSignature = true;
                job.edit.keepSigningBlock = true;
            }
            else if ("--sign".equals(k)) p12 = args[++i];
            else System.out.println("未知选项: " + k);
        }

        if (p12 != null) {
            job.resign = true;
            job.p12 = readAll(new FileInputStream(p12));
        }

        boolean bundle = forceBundle || SplitBundle.isBundle(in);
        if (bundle) {
            System.out.println("检测到多包容器，按容器模式处理: " + in.getName());
            SplitBundle.Report rep = SplitBundle.patch(in, out, job);
            System.out.println(rep.describe());
        } else {
            ApkPatcher.Outcome oc = ApkPatcher.patch(in, out, job);
            System.out.print(oc.logText());
            System.out.println("输出: " + out.getAbsolutePath() + "  " + out.length() + " 字节");
            System.out.println("SHA-256: " + oc.sha256);
            if (job.resign && oc.verify != null && !oc.verify.verified) System.exit(1);
        }
    }

    private static void renamePkg(String[] args) throws Exception {
        File in = new File(args[1]);
        File out = new File(args[2]);
        String newPkg = args[3];
        ApkPatcher.Job job = new ApkPatcher.Job();
        job.newPackage = newPkg;
        job.resign = false;
        for (int i = 4; i < args.length; i++) {
            if ("--sign".equals(args[i])) {
                job.resign = true;
                job.p12 = readAll(new FileInputStream(args[++i]));
            }
        }
        ApkPatcher.Outcome oc = ApkPatcher.patch(in, out, job);
        System.out.print(oc.logText());
        System.out.println("输出: " + out.getAbsolutePath());
    }

    // ---------------- 旧命令 ----------------

    private static void repack(String[] args) throws Exception {
        File in = new File(args[1]);
        File out = new File(args[2]);
        ApkPatcher.Job job = new ApkPatcher.Job();
        job.resign = false;
        if (!"-".equals(args[3])) job.manifest.versionName = args[3];
        if (!"-".equals(args[4])) job.manifest.versionCode = Long.valueOf(Long.parseLong(args[4]));
        ApkPatcher.Outcome oc = ApkPatcher.patch(in, out, job);
        System.out.print(oc.logText());
        System.out.println("repack 完成: " + out.getAbsolutePath() + " " + out.length() + " 字节");
    }

    private static void run(String[] args) throws Exception {
        File in = new File(args[1]);
        File out = new File(args[2]);
        ApkPatcher.Job job = new ApkPatcher.Job();
        if (!"-".equals(args[3])) job.manifest.versionName = args[3];
        if (!"-".equals(args[4])) job.manifest.versionCode = Long.valueOf(Long.parseLong(args[4]));
        job.resign = true;
        job.p12 = readAll(new FileInputStream(args[5]));
        ApkPatcher.Outcome oc = ApkPatcher.patch(in, out, job);
        System.out.print(oc.logText());
        System.out.println("输出: " + out.getAbsolutePath());
    }

    // ---------------- 自检 ----------------

    private static void selftest(String[] args) throws Exception {
        File apk = new File(args[1]);
        int pass = 0, fail = 0;
        System.out.println("=== ApkVerTool 核心引擎自检 ===");
        System.out.println("样本: " + apk.getAbsolutePath() + " (" + apk.length() + " 字节)");

        // 1. 体检
        ApkInfo info = ApkInfo.analyze(apk);
        System.out.println("\n[1] 体检");
        System.out.println(info.describe());
        if (info.packageName != null) { pass++; System.out.println("    → 清单解析 OK"); }
        else { fail++; System.out.println("    → 清单解析 FAIL"); }

        // 2. 清单改写往返
        System.out.println("\n[2] 清单改写往返（versionName / Code / CodeMajor / SDK / 权限 / 池编码）");
        byte[] m = ZipAlignWriter.extractEntry(apk, "AndroidManifest.xml");
        AxmlPatch.Info b = AxmlPatch.read(m);
        AxmlPatch.Patch p = new AxmlPatch.Patch();
        p.versionName = "9.9.9-avt";
        p.versionCode = Long.valueOf(b.versionCode == null ? 12345L : b.versionCode.longValue() + 1);
        p.versionCodeMajor = Long.valueOf(1L);
        p.minSdk = Integer.valueOf(26);
        p.targetSdk = Integer.valueOf(36);
        p.addPermissions.add("android.permission.INTERNET");
        p.addPermissions.add("android.permission.CAMERA");
        byte[] nm = AxmlPatch.apply(m, p);
        AxmlPatch.Info a = AxmlPatch.read(nm);
        pass += check("versionName", "9.9.9-avt", a.versionName);
        fail += checkFail("versionName", "9.9.9-avt", a.versionName);
        pass += check("versionCode", String.valueOf(b.versionCode.longValue() + 1), String.valueOf(a.versionCode));
        fail += checkFail("versionCode", String.valueOf(b.versionCode.longValue() + 1), String.valueOf(a.versionCode));
        pass += check("versionCodeMajor", "1", String.valueOf(a.versionCodeMajor));
        fail += checkFail("versionCodeMajor", "1", String.valueOf(a.versionCodeMajor));
        pass += check("minSdk", "26", String.valueOf(a.minSdk));
        fail += checkFail("minSdk", "26", String.valueOf(a.minSdk));
        pass += check("targetSdk", "36", String.valueOf(a.targetSdk));
        fail += checkFail("targetSdk", "36", String.valueOf(a.targetSdk));
        pass += check("包名未变", b.packageName, a.packageName);
        fail += checkFail("包名未变", b.packageName, a.packageName);
        boolean perms = a.permissions.contains("android.permission.INTERNET")
                && a.permissions.contains("android.permission.CAMERA");
        System.out.println("    " + (perms ? "OK  " : "!   ") + "新增权限 " + a.permissions.size() + " 条");
        if (perms) pass++; else fail++;
        System.out.println("    池编码: 原 " + enc(b) + " → 新 " + enc(a) + "（保持）");
        if (b.poolEncoding == a.poolEncoding) pass++; else fail++;

        // 3. 重打包 + 对齐
        System.out.println("\n[3] 重打包 + 对齐体检");
        File repacked = File.createTempFile("avt_selftest", ".apk");
        ZipAlignWriter.Edit edit = new ZipAlignWriter.Edit();
        edit.replace.put("AndroidManifest.xml", nm);
        ZipAlignWriter.Report rep = ZipAlignWriter.rewrite(apk, repacked, edit);
        System.out.println("    " + rep.describe().replace("\n", "\n    "));
        if (rep.misaligned == 0) { pass++; System.out.println("    → 对齐 OK"); }
        else { fail++; System.out.println("    → 对齐 FAIL"); }

        // 4. 签名 + 验签
        System.out.println("\n[4] 签名（含新生成密钥）+ 验签");
        File key = File.createTempFile("avt_key", ".p12");
        SignUtil.generateKey(key, SignUtil.PASS, "AvtSelftest", "Seagull", "Seagull", 3650, 2048);
        System.out.println("    " + (key.length() > 0 ? "OK  " : "!   ") + "自签密钥生成 " + key.length() + " 字节");
        if (key.length() > 0) pass++; else fail++;
        File signed = File.createTempFile("avt_signed", ".apk");
        SignUtil.sign(repacked, signed, readAll(new FileInputStream(key)), SignUtil.PASS, 26);
        SignUtil.VerifyDetail vd = SignUtil.verifyDetail(signed, 26);
        System.out.println(vd.describe().replace("\n", "\n    "));
        if (vd.verified) { pass++; System.out.println("    → 验签 OK"); }
        else { fail++; System.out.println("    → 验签 FAIL"); }

        // 5. 签后回读
        System.out.println("\n[5] 签名APK回读");
        AxmlPatch.Info f = AxmlPatch.read(ZipAlignWriter.extractEntry(signed, "AndroidManifest.xml"));
        pass += check("回读 versionName", "9.9.9-avt", f.versionName);
        fail += checkFail("回读 versionName", "9.9.9-avt", f.versionName);
        pass += check("回读 targetSdk", "36", String.valueOf(f.targetSdk));
        fail += checkFail("回读 targetSdk", "36", String.valueOf(f.targetSdk));

        // 6. 条目增删
        System.out.println("\n[6] 条目增删");
        ZipAlignWriter.Edit e2 = new ZipAlignWriter.Edit();
        e2.replace.put("assets/avt_probe.bin", new byte[1 << 20]);
        File withBig = File.createTempFile("avt_big", ".apk");
        ZipAlignWriter.rewrite(repacked, withBig, e2);
        boolean hasProbe = ZipAlignWriter.listNames(withBig).contains("assets/avt_probe.bin");
        System.out.println("    " + (hasProbe ? "OK  " : "!   ") + "新增条目可读回");
        if (hasProbe) pass++; else fail++;
        ZipAlignWriter.Edit e3 = new ZipAlignWriter.Edit();
        e3.remove("assets/avt_probe.bin");
        File dropped = File.createTempFile("avt_drop", ".apk");
        ZipAlignWriter.rewrite(withBig, dropped, e3);
        boolean gone = !ZipAlignWriter.listNames(dropped).contains("assets/avt_probe.bin");
        System.out.println("    " + (gone ? "OK  " : "!   ") + "条目已删除");
        if (gone) pass++; else fail++;

        // 7. dex 字符串表任意长度改写
        System.out.println("\n[7] DEX 字符串表改写（任意长度 + 摘要重算）");
        String pkgReal = b.packageName == null ? "com.seagull.apkvertool" : b.packageName;
        String fakeOld = pkgReal;                                    // 候选就是样本真实包名，不再猜
        String fakeNew = "zz" + pkgReal.replace(".", "") + "deep9";   // 去点拼接 ⇒ 新名里不含旧的斜杠/点号形式
        String dexName = null;
        byte[] dex = null;
        for (String n : ZipAlignWriter.listNames(apk)) {              // 包名可能只出现在 classes2.dex
            if (!isRootDex(n)) continue;
            byte[] d = ZipAlignWriter.extractEntry(apk, n);
            if (countPkgRefs(Dex.strings(d), fakeOld) > 0) { dexName = n; dex = d; break; }
        }
        if (dex == null) {
            System.out.println("    !   没有任何 classes*.dex 引用包名 " + fakeOld);
            fail++;
        } else {
            List<String> src0 = Dex.strings(dex);
            int before = countPkgRefs(src0, fakeOld);
            System.out.println("    样本 " + dexName + "：引用该包名 " + before + " 条字符串");
            System.out.println("    候选包名: " + fakeOld + "  →  " + fakeNew
                    + "（长度 " + fakeOld.length() + " → " + fakeNew.length() + "）");
            Dex.Result dr = Dex.rewrite(dex, new PkgRename(fakeOld, fakeNew));
            System.out.println("    改写 " + dr.changed + " / " + dr.total + " 条字符串");
            for (int i = 0; i < dr.samples.size() && i < 4; i++) System.out.println("      " + dr.samples.get(i));
            if (dr.changed > 0) { pass++; System.out.println("    → 字符串改写 OK"); }
            else { fail++; System.out.println("    → 字符串改写 FAIL"); }
            boolean digOk = Dex.verifyDigests(dr.dex);
            System.out.println("    " + (digOk ? "OK  " : "!   ") + "dex SHA-1 / Adler-32 摘要正确");
            if (digOk) pass++; else fail++;
            List<String> after = Dex.strings(dr.dex);
            int hit = countPkgRefs(after, fakeNew);       // 含新包名
            int left = countPkgRefs(after, fakeOld);      // 残留旧包名
            System.out.println("    改后含新包名 " + hit + " 条，残留旧包名 " + left + " 条（改前 " + before + " 条）");
            if (left == 0 && hit == before) { pass++; System.out.println("    → 全量替换 OK"); }
            else { fail++; System.out.println("    → 全量替换 FAIL"); }
        }

        // 8. resources.arsc 解析 + 改写
        System.out.println("\n[8] resources.arsc 解析与改写");
        byte[] arsc = ZipAlignWriter.extractEntry(apk, "resources.arsc");
        Arsc.Info ri = Arsc.read(arsc);
        System.out.println("    arsc 包名=" + ri.packageName + " 全局字符串 " + ri.globalStringCount
                + " 条 池 " + (ri.utf8Pool ? "UTF-8" : "UTF-16"));
        if (ri.globalStringCount > 0) { pass++; System.out.println("    → arsc 解析 OK"); }
        else { fail++; System.out.println("    → arsc 解析 FAIL"); }
        Arsc.Result ar = Arsc.patchGlobalStrings(arsc, new PkgRename(fakeOld, fakeNew));
        System.out.println("    改写全局字符串 " + ar.changed + " 条");
        Arsc.Info ri2 = Arsc.read(ar.arsc);
        if (ar.isChanged() && ri2.globalStringCount == ri.globalStringCount) {
            pass++; System.out.println("    → arsc 改写 OK（字符串条数不变）");
        } else if (!ar.isChanged()) {
            System.out.println("    · arsc 无命中（正常，跳过）");
        } else {
            fail++; System.out.println("    → arsc 改写 FAIL");
        }

        // 9. 应用名资源解析
        System.out.println("\n[9] android:label 资源引用解析");
        System.out.println("    应用名: " + info.label
                + (info.labelResolved != null ? "  (来自 resources.arsc)" : ""));
        if (info.label != null && !info.label.startsWith("@0x")) {
            pass++; System.out.println("    → 应用名已解出 OK");
        } else {
            System.out.println("    · 样本本身不是资源引用型，跳过");
        }

        // 10. 多包容器
        System.out.println("\n[10] XAPK / APKS 容器处理");
        File xapk = File.createTempFile("avt_bundle", ".xapk");
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(xapk));
        try {
            ZipEntry be = new ZipEntry("base.apk");
            zos.putNextEntry(be);
            zos.write(readAll(new FileInputStream(repacked)));
            zos.closeEntry();
            ZipEntry se = new ZipEntry("split_config.arm64_v8a.apk");
            zos.putNextEntry(se);
            zos.write(readAll(new FileInputStream(repacked)));
            zos.closeEntry();
            ZipEntry je = new ZipEntry("manifest.json");
            zos.putNextEntry(je);
            zos.write(("{\"package_name\":\"" + b.packageName + "\",\"version_name\":\""
                    + b.versionName + "\",\"version_code\":\"" + b.versionCode
                    + "\",\"target_sdk_version\":\"36\"}").getBytes("UTF-8"));
            zos.closeEntry();
            ZipEntry te = new ZipEntry("toc.pb");
            zos.putNextEntry(te);
            zos.write(Sha256.ofFile(repacked));
            zos.closeEntry();
        } finally {
            zos.close();
        }
        boolean isBundle = SplitBundle.isBundle(xapk);
        System.out.println("    " + (isBundle ? "OK  " : "!   ") + "容器识别");
        if (isBundle) pass++; else fail++;
        File xapkOut = File.createTempFile("avt_bundle_out", ".xapk");
        ApkPatcher.Job bj = new ApkPatcher.Job();
        bj.resign = false;
        bj.manifest.versionName = "7.7.7-xapk";
        bj.manifest.versionCode = Long.valueOf(777L);
        SplitBundle.Report br = SplitBundle.patch(xapk, xapkOut, bj);
        System.out.println("    " + br.describe().replace("\n", "\n    "));
        if (br.patched == 2 && br.failed == 0) { pass++; System.out.println("    → 子包全部改写 OK"); }
        else { fail++; System.out.println("    → 子包改写 FAIL"); }
        if (br.manifestJsonPatched) { pass++; System.out.println("    → manifest.json 同步 OK"); }
        else { fail++; System.out.println("    → manifest.json 未同步 FAIL"); }
        if (br.tocDigests > 0) { pass++; System.out.println("    → toc.pb 摘要回填 OK"); }
        else { fail++; System.out.println("    → toc.pb 摘要回填 FAIL"); }

        // 11. .so 等长原地替换（含边界规则）
        System.out.println("\n[11] .so 等长原地替换 + 边界规则");
        String soOld = pkgReal;
        char lastCh = soOld.charAt(soOld.length() - 1);
        String eqNew = soOld.substring(0, soOld.length() - 1) + (lastCh == 'x' ? 'q' : 'x');
        System.out.println("    等长替换: " + soOld + "  →  " + eqNew);
        PkgRename eq = new PkgRename(soOld, eqNew);
        byte[] soData = ("ELFprobe\0"
                + "L" + soOld.replace('.', '/') + "/Foo;\0"
                + soOld + ".Sub\0"
                + soOld + "YShouldNotMatch\0").getBytes("UTF-8");
        int hits = PackageRenamer.patchBytes(soData, eq);
        String soStr = new String(soData, "UTF-8");
        String eqSlash = eqNew.replace('.', '/');
        boolean replacedSlash = soStr.contains("L" + eqSlash + "/Foo;");
        boolean replacedDot = soStr.contains(eqNew + ".Sub");
        boolean boundaryKept = soStr.contains(soOld + "YShouldNotMatch");
        boolean noResidue = !soStr.contains("L" + soOld.replace('.', '/') + "/Foo;")
                && !soStr.contains(soOld + ".Sub");
        System.out.println("    命中 " + hits + " 处；斜杠形式=" + replacedSlash + " 点号形式=" + replacedDot
                + " 边界保护=" + boundaryKept + " 无残留=" + noResidue);
        if (replacedSlash && replacedDot && boundaryKept && noResidue && hits == 2) {
            pass++; System.out.println("    → .so 原地替换 OK");
        } else {
            fail++; System.out.println("    → .so 原地替换 FAIL");
        }

        // 12. 带 .so 的整包包名联动
        System.out.println("\n[12] 带 .so 的整包包名联动");
        ZipAlignWriter.Edit soEdit = new ZipAlignWriter.Edit();
        soEdit.replace.put("lib/arm64-v8a/libprobe.so", ("ELF\0"
                + "L" + soOld.replace('.', '/') + "/Native;\0" + soOld + ".Jni\0").getBytes("UTF-8"));
        File withSo = File.createTempFile("avt_so", ".apk");
        ZipAlignWriter.rewrite(repacked, withSo, soEdit);
        int expectSo = 0;      // 包里本来有几个 .so 引用旧包名，加上探针这 1 个（不写死，免得换个样本就假失败）
        for (String n : ZipAlignWriter.listNames(withSo)) {
            if (!ZipAlignWriter.isSo(n)) continue;
            if (containsPkg(ZipAlignWriter.extractEntry(withSo, n), soOld)) expectSo++;
        }
        File soOut = File.createTempFile("avt_so_out", ".apk");
        ApkPatcher.Job sj = new ApkPatcher.Job();
        sj.resign = false;
        sj.newPackage = eqNew;
        ApkPatcher.Outcome soOc = ApkPatcher.patch(withSo, soOut, sj);
        byte[] soAfter = ZipAlignWriter.extractEntry(soOut, "lib/arm64-v8a/libprobe.so");
        String soAfterStr = new String(soAfter, "UTF-8");
        boolean soOk = soAfterStr.contains(eqSlash + "/Native;") && !soAfterStr.contains(soOld.replace('.', '/'));
        System.out.println("    原生库改写 " + soOc.rename.soChanged + "/" + soOc.rename.soTotal
                + "（应改写 " + expectSo + " 个），包名 " + soOc.after.packageName);
        if (soOk && soOc.rename.soChanged == expectSo && eqNew.equals(soOc.after.packageName)) {
            pass++; System.out.println("    → 整包联动 OK");
        } else {
            fail++; System.out.println("    → 整包联动 FAIL");
        }
        withSo.delete(); soOut.delete();

        repacked.delete(); signed.delete(); key.delete(); withBig.delete(); dropped.delete();
        xapk.delete(); xapkOut.delete();

        System.out.println("\n=== 结果: " + pass + " 项通过, " + fail + " 项失败 ===");
        if (fail > 0) System.exit(1);
    }

    /** classes*.dex 判定（只认根目录下的 classesN.dex）。 */
    private static boolean isRootDex(String name) {
        if (!name.endsWith(".dex")) return false;
        String base = name.indexOf('/') < 0 ? name : name.substring(name.lastIndexOf('/') + 1);
        return base.startsWith("classes");
    }

    /** 有多少条字符串引用了该包名（斜杠形式或点号形式都算）。 */
    private static int countPkgRefs(List<String> strings, String pkg) {
        String slash = pkg.replace('.', '/');
        int n = 0;
        for (int i = 0; i < strings.size(); i++) {
            String s = strings.get(i);
            if (s != null && (s.indexOf(slash) >= 0 || s.indexOf(pkg) >= 0)) n++;
        }
        return n;
    }

    /** .so 没有字符串表，直接在原始字节里找包名。 */
    private static boolean containsPkg(byte[] data, String pkg) {
        return indexOf(data, pkg.getBytes(java.nio.charset.Charset.forName("UTF-8"))) >= 0
                || indexOf(data, pkg.replace('.', '/').getBytes(java.nio.charset.Charset.forName("UTF-8"))) >= 0;
    }

    private static int indexOf(byte[] data, byte[] pat) {
        outer:
        for (int i = 0; i + pat.length <= data.length; i++) {
            for (int j = 0; j < pat.length; j++) if (data[i + j] != pat[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static String enc(AxmlPatch.Info i) {
        return i.poolEncoding == 0x100 ? "UTF-8" : "UTF-16";
    }

    private static int check(String label, String expect, String actual) {
        if (expect == null ? actual == null : expect.equals(actual)) {
            System.out.println("    OK  " + label + " = " + actual);
            return 1;
        }
        System.out.println("    !   " + label + " 期望 " + expect + " 实际 " + actual);
        return 0;
    }

    private static int checkFail(String label, String expect, String actual) {
        return (expect == null ? actual == null : expect.equals(actual)) ? 0 : 1;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 16);
        byte[] buf = new byte[1 << 15];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }
}
