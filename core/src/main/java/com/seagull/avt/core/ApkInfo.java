package com.seagull.avt.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** APK 体检：清单摘要 + 结构统计 + 加固/框架识别 + 签名者。 */
public final class ApkInfo {

    public String path;
    public String packageName;
    public String versionName;
    public String label;
    public String labelResolved;
    public Boolean labelIsRef;
    public Long versionCode;
    public Long versionCodeMajor;
    public Integer minSdk;
    public Integer targetSdk;
    public Boolean debuggable;
    public Boolean extractNativeLibs;

    public long fileSize;
    public int entryCount;
    public int soCount;
    public int dexCount;
    public int permissionCount;
    public boolean hasArsc;
    public boolean hasV1Signature;

    public final List<String> abis = new ArrayList<String>();
    public final List<String> dexNames = new ArrayList<String>();
    public final List<String> hiddenDex = new ArrayList<String>();
    public final List<String> frameworks = new ArrayList<String>();
    public final List<String> packers = new ArrayList<String>();
    public final List<SignUtil.CertInfo> signers = new ArrayList<SignUtil.CertInfo>();
    public String signSchemes = "";
    public boolean signed;
    public String manifestError;

    /** 加固特征：so 名 / assets 名 → 厂商。 */
    private static final String[][] PACKER_RULES = {
            {"lib/arm64-v8a/libjiagu.so", "360 加固"},
            {"lib/armeabi-v7a/libjiagu.so", "360 加固"},
            {"lib/arm64-v8a/libjiagu_art.so", "360 加固"},
            {"assets/libjiagu.so", "360 加固"},
            {"lib/arm64-v8a/libDexHelper.so", "梆梆安全"},
            {"lib/armeabi-v7a/libDexHelper.so", "梆梆安全"},
            {"lib/arm64-v8a/libDexHelper-x86.so", "梆梆安全"},
            {"lib/arm64-v8a/libshella-2.10.6.6.so", "腾讯乐固"},
            {"lib/arm64-v8a/libshellx-super.2019.so", "腾讯乐固"},
            {"lib/arm64-v8a/libtup.so", "腾讯乐固"},
            {"lib/arm64-v8a/libmobisec.so", "阿里聚安全"},
            {"lib/arm64-v8a/libsgmain.so", "阿里安全"},
            {"lib/arm64-v8a/libsgsecuritybody.so", "阿里安全"},
            {"lib/arm64-v8a/libnesec.so", "娜迦"},
            {"lib/arm64-v8a/libchaosvmp.so", "娜迦 VMP"},
            {"lib/arm64-v8a/libddog.so", "娜迦"},
            {"lib/arm64-v8a/libfdog.so", "娜迦"},
            {"lib/arm64-v8a/libkwscmm.so", "几维安全"},
            {"lib/arm64-v8a/libkwscr.so", "几维安全"},
            {"lib/arm64-v8a/libexec.so", "爱加密"},
            {"lib/arm64-v8a/libexecmain.so", "爱加密"},
            {"lib/armeabi-v7a/libexec.so", "爱加密"},
            {"assets/ijiami.ajm", "爱加密"},
            {"lib/arm64-v8a/libbaiduprotect.so", "百度加固"},
            {"lib/arm64-v8a/libSecShell.so", "顶像加固"},
            {"lib/arm64-v8a/libnqshield.so", "网秦"},
            {"lib/arm64-v8a/libapktool.so", "可疑加固"},
            {"lib/arm64-v8a/libprotect.so", "通用加固"},
            {"lib/armeabi-v7a/libprotect.so", "通用加固"},
    };

    /** 框架/引擎特征。 */
    private static final String[][] FW_RULES = {
            {"lib/arm64-v8a/libflutter.so", "Flutter"},
            {"lib/armeabi-v7a/libflutter.so", "Flutter"},
            {"assets/flutter_assets/", "Flutter"},
            {"lib/arm64-v8a/libil2cpp.so", "Unity IL2CPP"},
            {"lib/armeabi-v7a/libil2cpp.so", "Unity IL2CPP"},
            {"assets/bin/Data/", "Unity"},
            {"lib/arm64-v8a/libmono.so", "Unity Mono"},
            {"lib/arm64-v8a/libreactnativejni.so", "React Native"},
            {"assets/index.android.bundle", "React Native"},
            {"lib/arm64-v8a/libhermes.so", "Hermes (RN)"},
            {"lib/arm64-v8a/libUE4.so", "Unreal Engine"},
            {"lib/arm64-v8a/libUnreal.so", "Unreal Engine"},
            {"lib/arm64-v8a/libcocos2djs.so", "Cocos2d-x JS"},
            {"lib/arm64-v8a/libcocos2d.so", "Cocos2d-x"},
            {"lib/arm64-v8a/libgame.so", "Cocos/自研游戏引擎"},
            {"lib/arm64-v8a/libmonochrome.so", "系统 WebView"},
            {"lib/arm64-v8a/libxwalkcore.so", "Crosswalk"},
            {"assets/www/", "Cordova / H5"},
            {"assets/app.js", "H5 混合应用"},
            {"lib/arm64-v8a/libnode.so", "Node.js 内嵌"},
            {"lib/arm64-v8a/librive.so", "Rive"},
    };

    public static ApkInfo analyze(File apk) throws Exception {
        ApkInfo info = new ApkInfo();
        info.path = apk.getAbsolutePath();
        info.fileSize = apk.length();

        Set<String> names = new LinkedHashSet<String>();
        ZipFile zf = new ZipFile(apk);
        try {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                names.add(n);
                info.entryCount++;
                if (ZipAlignWriter.isSo(n)) info.soCount++;
                if (n.startsWith("lib/")) {
                    String[] parts = n.split("/");
                    if (parts.length > 2 && !info.abis.contains(parts[1])) info.abis.add(parts[1]);
                }
                String base = n.indexOf('/') < 0 ? n : n.substring(n.lastIndexOf('/') + 1);
                if (base.startsWith("classes") && base.endsWith(".dex")) {
                    info.dexCount++;
                    info.dexNames.add(n);
                } else if (n.endsWith(".dex")) {
                    info.hiddenDex.add(n);
                }
                if ("resources.arsc".equals(n)) info.hasArsc = true;
                if (ZipAlignWriter.isV1SignatureEntry(n)) info.hasV1Signature = true;
            }

            for (String[] rule : PACKER_RULES) {
                if (names.contains(rule[0]) && !info.packers.contains(rule[1])) info.packers.add(rule[1]);
            }
            for (String[] rule : FW_RULES) {
                if (rule[0].endsWith("/")) {
                    for (String n : names) {
                        if (n.startsWith(rule[0]) && !info.frameworks.contains(rule[1])) info.frameworks.add(rule[1]);
                    }
                } else if (names.contains(rule[0]) && !info.frameworks.contains(rule[1])) {
                    info.frameworks.add(rule[1]);
                }
            }

            try {
                byte[] m = ZipAlignWriter.readAll(zf.getInputStream(zf.getEntry("AndroidManifest.xml")));
                AxmlPatch.Info mi = AxmlPatch.read(m);
                info.packageName = mi.packageName;
                info.versionName = mi.versionName;
                info.versionCode = mi.versionCode;
                info.versionCodeMajor = mi.versionCodeMajor;
                info.minSdk = mi.minSdk;
                info.targetSdk = mi.targetSdk;
                info.label = mi.label;
                info.debuggable = mi.debuggable;
                info.extractNativeLibs = mi.extractNativeLibs;
                info.permissionCount = mi.permissions.size();
                // android:label 是资源引用时，去 resources.arsc 里把真实应用名解出来
                if (Boolean.TRUE.equals(mi.labelIsRef) && mi.label != null && mi.label.startsWith("@0x")) {
                    try {
                        int resId = (int) Long.parseLong(mi.label.substring(3), 16);
                        ZipEntry ae = zf.getEntry("resources.arsc");
                        if (ae != null) {
                            byte[] arsc = ZipAlignWriter.readAll(zf.getInputStream(ae));
                            String v = Arsc.resolve(arsc, resId);
                            if (v != null && v.length() > 0) {
                                info.labelResolved = v;
                                info.label = v;
                                info.labelIsRef = Boolean.FALSE;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                info.manifestError = String.valueOf(t.getMessage());
            }
        } finally {
            zf.close();
        }

        try {
            SignUtil.VerifyDetail vd = SignUtil.verifyDetail(apk, info.minSdk == null ? 26 : info.minSdk.intValue());
            info.signed = vd.verified;
            info.signSchemes = vd.schemes();
            info.signers.addAll(vd.signers);
        } catch (Throwable ignored) {}

        return info;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("文件      : ").append(new File(path).getName()).append('\n');
        sb.append("大小      : ").append(human(fileSize)).append('\n');
        if (manifestError != null) sb.append("清单解析失败: ").append(manifestError).append('\n');
        sb.append("包名      : ").append(nz(packageName)).append('\n');
        sb.append("应用名    : ").append(nz(label)).append('\n');
        sb.append("版本      : ").append(nz(versionName)).append("  (code ").append(nz(versionCode)).append(")").append('\n');
        if (versionCodeMajor != null && versionCodeMajor.longValue() != 0L) {
            long full = ((versionCodeMajor.longValue() & 0xFFFFFFFFL) << 32)
                    | (versionCode == null ? 0L : (versionCode.longValue() & 0xFFFFFFFFL));
            sb.append("64 位版本 : ").append(full).append("  (major ").append(versionCodeMajor).append(")").append('\n');
        }
        sb.append("SDK       : min ").append(nz(minSdk)).append(" / target ").append(nz(targetSdk)).append('\n');
        sb.append("条目      : ").append(entryCount).append(" 个");
        if (hasArsc) sb.append("，含 resources.arsc");
        sb.append('\n');
        sb.append("DEX       : ").append(dexCount).append(" 个");
        if (!hiddenDex.isEmpty()) sb.append("，另有隐藏 dex ").append(hiddenDex.size()).append(" 个（可疑）");
        sb.append('\n');
        sb.append("原生库    : ").append(soCount).append(" 个");
        if (!abis.isEmpty()) sb.append("  ABI: ").append(join(abis));
        sb.append('\n');
        sb.append("权限      : ").append(permissionCount).append(" 条").append('\n');
        sb.append("加固      : ").append(packers.isEmpty() ? "未识别到壳" : join(packers)).append('\n');
        sb.append("框架      : ").append(frameworks.isEmpty() ? "-" : join(frameworks)).append('\n');
        sb.append("签名      : ");
        if (signers.isEmpty()) {
            sb.append("未签名 / 签名块不可读");
        } else {
            sb.append(signSchemes).append("，签名者 ").append(signers.size()).append(" 个").append('\n');
            SignUtil.CertInfo c = signers.get(0);
            sb.append("  ").append(c.subject).append('\n');
            sb.append("  SHA-256 ").append(c.sha256);
            if (c.debugCert) sb.append("  [调试证书]");
        }
        if (debuggable != null) sb.append('\n').append("debuggable: ").append(debuggable);
        if (extractNativeLibs != null) sb.append('\n').append("extractNativeLibs: ").append(extractNativeLibs);
        return sb.toString();
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(l.get(i));
        }
        return sb.toString();
    }

    private static String nz(Object o) { return o == null ? "-" : String.valueOf(o); }

    public static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / 1048576.0);
        return String.format("%.2f GB", bytes / 1073741824.0);
    }
}
