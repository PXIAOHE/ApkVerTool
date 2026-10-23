package com.seagull.avt.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;

/**
 * 单个 APK 的完整处理流水线：清单改写 → 包名联动 → 条目增删 → 对齐重打包 → 签名 → 自验。
 * CLI、App、多包容器都走这一条路径，避免三处实现漂移。
 */
public final class ApkPatcher {

    private ApkPatcher() {}

    /** 一次处理的全部输入。 */
    public static final class Job {
        public AxmlPatch.Patch manifest = new AxmlPatch.Patch();
        public ZipAlignWriter.Edit edit = new ZipAlignWriter.Edit();
        /** 新包名；null / 空 = 不改。 */
        public String newPackage;
        public boolean resign = true;
        public byte[] p12;
        public char[] pass = SignUtil.PASS;
        public SignUtil.Options opt = new SignUtil.Options();
        public int level = Deflater.BEST_SPEED;
        /** 清单里没有 versionCode 的包（split APK）跳过版本字段，避免给分片写入无意义属性。 */
        public boolean skipVersionIfAbsent = true;
        /** 新图标（用户选的图片原始字节）；null = 不改图标。 */
        public byte[] iconImage;
        /** 图标缩放实现：App 用 BitmapFactory，CLI 用 ImageIO。 */
        public IconReplacer.Scaler iconScaler;

        public Job copy() {
            Job j = new Job();
            j.manifest = manifest;
            j.edit = edit;
            j.newPackage = newPackage;
            j.resign = resign;
            j.p12 = p12;
            j.pass = pass;
            j.opt = opt;
            j.level = level;
            j.skipVersionIfAbsent = skipVersionIfAbsent;
            j.iconImage = iconImage;
            j.iconScaler = iconScaler;
            return j;
        }
    }

    /** 处理结果与逐阶段报告。 */
    public static final class Outcome {
        public File output;
        public AxmlPatch.Info before;
        public AxmlPatch.Info after;
        public PackageRenamer.Report rename;
        public ZipAlignWriter.Report pack;
        public SignUtil.VerifyDetail verify;
        public String sha256;
        public boolean changed;
        public final StringBuilder log = new StringBuilder();

        public String logText() {
            return log.toString();
        }

        private void say(String s) {
            log.append(s).append('\n');
        }
    }

    public static Outcome patch(File src, File out, Job job) throws Exception {
        Outcome o = new Outcome();
        byte[] manifest = ZipAlignWriter.extractEntry(src, "AndroidManifest.xml");
        AxmlPatch.Info before = AxmlPatch.read(manifest);
        o.before = before;
        o.say("原: " + before.versionName + " (" + before.versionCode + ")  "
                + before.packageName + "  minSdk " + before.minSdk + " / target " + before.targetSdk
                + "  池 " + (before.poolEncoding == 0x100 ? "UTF-8" : "UTF-16"));

        ZipAlignWriter.Edit edit = new ZipAlignWriter.Edit();
        edit.delete.addAll(job.edit.delete);
        edit.replace.putAll(job.edit.replace);
        // 不重签（MT 式输出）= 原样保留原签名：V1 的 META-INF 条目 + V2/V3 签名块一起搬运。
        // 之前只搬了 V1 条目、签名块被丢掉，但 App 里从来没把 keepOldSignature 打开，
        // 结果「不签名输出」的APK是一条签名都没留下。
        edit.keepOldSignature = job.edit.keepOldSignature || !job.resign;
        edit.keepSigningBlock = !job.resign;
        edit.preserveExtra = job.edit.preserveExtra;
        edit.level = job.edit.level;

        // 1) 包名联动（会顺带改写清单字符串池）
        if (job.newPackage != null && job.newPackage.trim().length() > 0) {
            o.rename = PackageRenamer.apply(src, job.newPackage.trim(), edit);
            o.say(o.rename.describe());
        }

        // 2) 其余清单字段
        AxmlPatch.Patch eff = effective(job.manifest, before, o);
        if (!eff.isEmpty()) {
            byte[] base = edit.replace.containsKey("AndroidManifest.xml")
                    ? edit.replace.get("AndroidManifest.xml") : manifest;
            edit.replace.put("AndroidManifest.xml", AxmlPatch.apply(base, eff));
            o.changed = true;
        }
        if (o.rename != null && o.rename.manifestChanged) o.changed = true;
        if (!edit.delete.isEmpty() || !job.edit.replace.isEmpty()) o.changed = true;

        // 2.5) 图标替换：清单 android:icon → arsc 全部配置 → res/ 下的图片条目
        //      失败必须抛出去：选了图标却没换上还报「成功」是假话
        if (job.iconImage != null) {
            if (job.iconScaler == null) throw new Exception("图标替换缺少图片缩放实现");
            byte[] arscNow = edit.replace.containsKey("resources.arsc")
                    ? edit.replace.get("resources.arsc") : null;
            try {
                IconReplacer.Result ir = IconReplacer.build(src, job.iconImage, job.iconScaler, arscNow);
                for (java.util.Map.Entry<String, byte[]> e : ir.replace.entrySet()) {
                    edit.replace.put(e.getKey(), e.getValue());
                }
                o.say(ir.summary());
                o.changed = true;
            } catch (IconReplacer.NoIcon noIcon) {
                // 分包（config.*.apk 之类）本来就没有 icon，不算失败
                o.say("图标: 该包里没有 android:icon（分包正常现象），跳过");
            }
        }

        // 3) 重打包
        File stage = File.createTempFile("avt_stage", ".apk", out.getAbsoluteFile().getParentFile());
        try {
            o.pack = ZipAlignWriter.rewrite(src, stage, edit);
            o.say("重打包: " + o.pack.describe().replace("\n", " | "));

            // 4) 签名 / 直出
            if (job.resign && job.p12 != null) {
                int minSdk = before.minSdk == null ? 26 : before.minSdk.intValue();
                SignUtil.sign(stage, out, job.p12, job.pass, minSdk, job.opt);
                o.verify = SignUtil.verifyDetail(out, minSdk);
                o.say("签名: " + job.opt.describe() + " → "
                        + (o.verify.verified ? "通过 " : "失败 ") + o.verify.schemes());
                for (int i = 0; i < o.verify.errors.size(); i++) o.say("  ERR " + o.verify.errors.get(i));
            } else {
                copy(stage, out);
                o.say("未重签输出（已保留原签名：V1 条目 + V2/V3 签名块，摘要已失效不可直接安装）");
            }
        } finally {
            stage.delete();
        }

        // 5) 回读
        o.after = AxmlPatch.read(ZipAlignWriter.extractEntry(out, "AndroidManifest.xml"));
        o.say("新: " + o.after.versionName + " (" + o.after.versionCode + ")  "
                + o.after.packageName + "  minSdk " + o.after.minSdk + " / target " + o.after.targetSdk
                + "  池 " + (o.after.poolEncoding == 0x100 ? "UTF-8" : "UTF-16"));
        o.sha256 = Sha256.ofFileHex(out);
        return o;
    }

    /**
     * 按目标包清单的实际情况裁剪请求：split APK 没有 versionCode，
     * 硬写会造出无意义属性，这里直接跳过并记录。
     */
    private static AxmlPatch.Patch effective(AxmlPatch.Patch req, AxmlPatch.Info before, Outcome o) {
        AxmlPatch.Patch p = new AxmlPatch.Patch();
        p.packageName = null;   // 包名统一走 PackageRenamer
        p.versionName = req.versionName;
        p.versionCode = req.versionCode;
        p.versionCodeMajor = req.versionCodeMajor;
        p.minSdk = req.minSdk;
        p.targetSdk = req.targetSdk;
        p.label = req.label;
        p.debuggable = req.debuggable;
        p.extractNativeLibs = req.extractNativeLibs;
        p.allowBackup = req.allowBackup;
        p.addPermissions.addAll(req.addPermissions);
        p.removePermissions.addAll(req.removePermissions);
        p.forceUtf8 = req.forceUtf8;

        if (before.versionCode == null) {
            if (p.versionCode != null) o.say("  · 该包清单无 versionCode（split），已跳过 versionCode");
            if (p.versionCodeMajor != null) o.say("  · 该包清单无 versionCode（split），已跳过 versionCodeMajor");
            p.versionCode = null;
            p.versionCodeMajor = null;
        }
        if (before.versionName == null && p.versionName != null) {
            o.say("  · 该包清单无 versionName（split），已跳过 versionName");
            p.versionName = null;
        }
        return p;
    }

    static void copy(File src, File dst) throws Exception {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);
        try {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } finally {
            try { in.close(); } catch (Throwable ignored) {}
            try { out.close(); } catch (Throwable ignored) {}
        }
    }
}
