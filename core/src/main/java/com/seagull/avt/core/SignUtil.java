package com.seagull.avt.core;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/** 基于 apksig 的 V1+V2+V3(+V4) 重签名、验签详情与密钥生成。 */
public final class SignUtil {

    public static final char[] PASS = "avtSignKey2026".toCharArray();
    public static final String ALIAS = "avt";

    private SignUtil() {}

    /** 签名方案开关。 */
    public static final class Options {
        public boolean v1 = true;
        public boolean v2 = true;
        public boolean v3 = true;
        public boolean v4 = false;

        public String describe() {
            StringBuilder sb = new StringBuilder();
            if (v1) sb.append("V1 ");
            if (v2) sb.append("V2 ");
            if (v3) sb.append("V3 ");
            if (v4) sb.append("V4 ");
            return sb.toString().trim();
        }
    }

    /** 单个签名者证书摘要。 */
    public static final class CertInfo {
        public String subject;
        public String issuer;
        public String serial;
        public String sigAlg;
        public String sha256;
        public String sha1;
        public Date notBefore;
        public Date notAfter;
        public boolean expired;
        public boolean notYetValid;
        public boolean debugCert;

        public String describe() {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
            StringBuilder sb = new StringBuilder();
            sb.append("主体    : ").append(subject).append('\n');
            sb.append("颁发者  : ").append(issuer).append('\n');
            sb.append("序列号  : ").append(serial).append('\n');
            sb.append("算法    : ").append(sigAlg).append('\n');
            sb.append("有效期  : ").append(f.format(notBefore)).append(" → ").append(f.format(notAfter));
            if (expired) sb.append("  [已过期]");
            else if (notYetValid) sb.append("  [尚未生效]");
            sb.append('\n');
            sb.append("SHA-256 : ").append(sha256).append('\n');
            sb.append("SHA-1   : ").append(sha1);
            if (debugCert) sb.append('\n').append("⚠ 这是 Android 调试证书，不能用于正式分发");
            return sb.toString();
        }
    }

    /** 验签结果详情。 */
    public static final class VerifyDetail {
        public boolean verified;
        public boolean v1, v2, v3, v31, v4, sourceStamp;
        /** APK 内是否存在 V1 签名条目（minSdk≥24 时 apksig 不校验 V1，但文件确实写入了）。 */
        public boolean v1Present;
        /** true = signers 是从签名块直接解析出来的（未重签APK，apksig 因摘要不匹配不回报证书）。 */
        public boolean signersFromBlock;
        public final List<CertInfo> signers = new ArrayList<CertInfo>();
        public final List<String> errors = new ArrayList<String>();
        public final List<String> warnings = new ArrayList<String>();

        public String schemes() {
            StringBuilder sb = new StringBuilder("[");
            if (v1) sb.append("V1 ");
            else if (v1Present) sb.append("V1* ");
            if (v2) sb.append("V2 ");
            if (v3) sb.append("V3 ");
            if (v31) sb.append("V3.1 ");
            if (v4) sb.append("V4 ");
            String s = sb.toString().trim();
            if (s.length() == 1) s = "[无";
            return s + "]";
        }

        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append(verified ? "验签通过 " : "验签失败 ").append(schemes()).append('\n');
            if (sourceStamp) sb.append("源码戳: 已签名\n");
            for (int i = 0; i < signers.size(); i++) {
                sb.append("签名者 ").append(i + 1)
                  .append(signersFromBlock ? "（原签名，未重签）:" : ":").append('\n');
                sb.append(indent(signers.get(i).describe())).append('\n');
            }
            if (!errors.isEmpty()) {
                sb.append("错误:\n");
                for (int i = 0; i < errors.size(); i++) sb.append("  ERR ").append(errors.get(i)).append('\n');
            }
            if (!warnings.isEmpty()) {
                sb.append("警告:\n");
                for (int i = 0; i < warnings.size(); i++) sb.append("  WARN ").append(warnings.get(i)).append('\n');
            }
            return sb.toString().trim();
        }

        private static String indent(String s) {
            return "  " + s.replace("\n", "\n  ");
        }
    }

    // ================= 签名 =================

    public static void sign(File in, File out, byte[] p12, char[] pass, int minSdk) throws Exception {
        sign(in, out, p12, pass, minSdk, new Options());
    }

    public static void sign(File in, File out, byte[] p12, char[] pass, int minSdk, Options opt)
            throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(p12), pass);
        String alias = ks.aliases().nextElement();
        PrivateKey key = (PrivateKey) ks.getKey(alias, pass);
        List<X509Certificate> chain = new ArrayList<X509Certificate>();
        Certificate[] cs = ks.getCertificateChain(alias);
        if (cs == null || cs.length == 0) throw new IllegalStateException("PKCS12 中没有证书链");
        for (Certificate c : cs) chain.add((X509Certificate) c);

        ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder(ALIAS, key, chain).build();
        ApkSigner.Builder b = new ApkSigner.Builder(Collections.singletonList(sc))
                .setInputApk(in)
                .setOutputApk(out)
                .setMinSdkVersion(minSdk)
                .setV1SigningEnabled(opt.v1)
                .setV2SigningEnabled(opt.v2)
                .setV3SigningEnabled(opt.v3)
                .setV4SigningEnabled(opt.v4)
                .setAlignmentPreserved(true)
                .setLibraryPageAlignmentBytes(ZipAlignWriter.ALIGN_SO)
                .setCreatedBy("ApkVerTool");
        if (opt.v4) {
            b.setV4SignatureOutputFile(new File(out.getAbsolutePath() + ".idsig"));
        }
        b.build().sign();
    }

    // ================= 验签 =================

    public static String verify(File apk) throws Exception {
        return verify(apk, 26);
    }

    public static String verify(File apk, int minSdk) throws Exception {
        VerifyDetail d = verifyDetail(apk, minSdk);
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(d.v1 ? "v1=" : "v1=").append(d.v1)
          .append(" v2=").append(d.v2)
          .append(" v3=").append(d.v3).append("] ");
        if (d.errors.isEmpty()) {
            sb.append("验签通过");
        } else {
            sb.append("验签失败:");
            for (int i = 0; i < d.errors.size(); i++) sb.append("\n  ERR ").append(d.errors.get(i));
            for (int i = 0; i < d.warnings.size(); i++) sb.append("\n  WARN ").append(d.warnings.get(i));
        }
        return sb.toString().trim();
    }

    public static VerifyDetail verifyDetail(File apk, int minSdk) throws Exception {
        ApkVerifier.Result r = new ApkVerifier.Builder(apk)
                .setMinCheckedPlatformVersion(Math.min(minSdk, 24))
                .build().verify();
        VerifyDetail d = new VerifyDetail();
        d.verified = r.isVerified();
        d.v1 = r.isVerifiedUsingV1Scheme();
        d.v2 = r.isVerifiedUsingV2Scheme();
        d.v3 = r.isVerifiedUsingV3Scheme();
        d.v31 = r.isVerifiedUsingV31Scheme();
        d.v4 = r.isVerifiedUsingV4Scheme();
        try { d.sourceStamp = r.isSourceStampVerified(); } catch (Throwable ignored) {}
        for (Object o : r.getAllErrors()) d.errors.add(String.valueOf(o));
        for (Object o : r.getWarnings()) d.warnings.add(String.valueOf(o));
        List<X509Certificate> certs = r.getSignerCertificates();
        if (certs != null) {
            for (int i = 0; i < certs.size(); i++) d.signers.add(certInfo(certs.get(i)));
        }
        try {
            List<String> names = ZipAlignWriter.listNames(apk);
            for (int i = 0; i < names.size(); i++) {
                if (ZipAlignWriter.isV1SignatureEntry(names.get(i))) { d.v1Present = true; break; }
            }
        } catch (Throwable ignored) {}
        if (d.signers.isEmpty()) {
            // 不重签APK：签名块原样搬过来的，apksig 因为摘要不匹配不回报证书，
            // 自己从签名块里把证书链扒出来，界面才能显示「原签名者」
            List<CertInfo> block = readBlockSigners(apk);
            if (!block.isEmpty()) {
                d.signers.addAll(block);
                d.signersFromBlock = true;
                d.warnings.add("签名块为原 APK 原样保留（未重签），上面是原签名者；"
                        + "内容已改动，V2/V3 摘要不匹配，签名无效 —— 这是 MT 式不重签输出的正常结果。");
            }
        }
        return d;
    }

    /**
     * 直接从 APK 签名块解析签名者证书，不校验摘要。
     * 用途：MT 式不重签的APK，apksig 会因为改动后摘要不匹配而不回报证书，
     * 这里按 V2/V3 的 ID-value pairs 结构自己把证书扒出来。
     */
    public static List<CertInfo> readBlockSigners(File apk) {
        List<CertInfo> out = new ArrayList<CertInfo>();
        try {
            byte[] b = ZipAlignWriter.extractSigningBlock(apk);
            if (b == null || b.length < 40) return out;
            int end = b.length - 24;   // 尾部 size(8) + magic(16)
            int p = 8;                 // 头部 size(8)
            while (p + 12 <= end) {
                long pairLen = le64(b, p);
                long id = le32u(b, p + 8);
                long vLen = pairLen - 4;
                int vStart = p + 12;
                if (vLen <= 0 || vStart + vLen > end) break;
                // 0x7109871a = V2, 0xf05368c0 = V3, 0x1b93ad61 = V3.1
                if (id == 0x7109871aL || id == 0xf05368c0L || id == 0x1b93ad61L) {
                    long signersLen = le32u(b, vStart);
                    long sp = vStart + 4;
                    long signersEnd = Math.min(sp + signersLen, vStart + vLen);
                    while (sp + 4 <= signersEnd) {
                        long signerLen = le32u(b, (int) sp);
                        if (signerLen <= 0 || sp + 4 + signerLen > signersEnd) break;
                        byte[] der = firstCertInSigner(b, (int) (sp + 4), (int) signerLen);
                        if (der != null) {
                            X509Certificate c = (X509Certificate) java.security.cert.CertificateFactory
                                    .getInstance("X.509")
                                    .generateCertificate(new ByteArrayInputStream(der));
                            CertInfo ci = certInfo(c);
                            // V2 与 V3 各存一份签名者，证书通常一样 —— 按指纹去重，别列两遍
                            boolean dup = false;
                            for (int k = 0; k < out.size(); k++) {
                                if (out.get(k).sha256.equals(ci.sha256)) { dup = true; break; }
                            }
                            if (!dup) out.add(ci);
                        }
                        sp += 4 + signerLen;
                    }
                }
                p = vStart + (int) vLen;
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** V2/V3 的 signer 里，signed data = digests | certificates | …，取证书序列第一张。 */
    private static byte[] firstCertInSigner(byte[] b, int off, int len) {
        try {
            long sdLen = le32u(b, off);
            int sd = off + 4;
            if (sdLen <= 0 || sd + sdLen > off + len) return null;
            long digestsLen = le32u(b, sd);
            int q = sd + 4 + (int) digestsLen;
            if (q + 4 > off + len) return null;
            long certsLen = le32u(b, q);
            int c = q + 4;
            if (certsLen <= 4 || c + certsLen > off + len) return null;
            long first = le32u(b, c);
            if (first <= 0 || first > certsLen - 4) return null;
            byte[] der = new byte[(int) first];
            System.arraycopy(b, c + 4, der, 0, (int) first);
            return der;
        } catch (Throwable t) {
            return null;
        }
    }

    private static long le32u(byte[] a, int off) {
        return (a[off] & 0xFFL) | ((a[off + 1] & 0xFFL) << 8)
                | ((a[off + 2] & 0xFFL) << 16) | ((a[off + 3] & 0xFFL) << 24);
    }

    private static long le64(byte[] a, int off) {
        long v = 0;
        for (int i = 7; i >= 0; i--) v = (v << 8) | (a[off + i] & 0xFFL);
        return v;
    }

    /** 读 APK 里现存的签名者证书（不校验完整性）。 */
    public static List<CertInfo> readSigners(File apk, int minSdk) {
        List<CertInfo> out = new ArrayList<CertInfo>();
        try {
            VerifyDetail d = verifyDetail(apk, minSdk);
            out.addAll(d.signers);
        } catch (Throwable ignored) {}
        return out;
    }

    private static CertInfo certInfo(X509Certificate c) {
        CertInfo i = new CertInfo();
        i.subject = c.getSubjectX500Principal().getName();
        i.issuer = c.getIssuerX500Principal().getName();
        i.serial = c.getSerialNumber().toString(16).toUpperCase();
        i.sigAlg = c.getSigAlgName();
        i.sha256 = hex(c, "SHA-256");
        i.sha1 = hex(c, "SHA-1");
        i.notBefore = c.getNotBefore();
        i.notAfter = c.getNotAfter();
        Date now = new Date();
        i.expired = now.after(i.notAfter);
        i.notYetValid = now.before(i.notBefore);
        String s = i.subject == null ? "" : i.subject;
        i.debugCert = s.contains("CN=Android Debug") || s.contains("O=Android, C=US");
        return i;
    }

    private static String hex(X509Certificate c, String alg) {
        try {
            MessageDigest md = MessageDigest.getInstance(alg);
            byte[] b = md.digest(c.getEncoded());
            StringBuilder sb = new StringBuilder(b.length * 3);
            for (int i = 0; i < b.length; i++) {
                if (i > 0) sb.append(':');
                int v = b[i] & 0xFF;
                if (v < 16) sb.append('0');
                sb.append(Integer.toHexString(v).toUpperCase());
            }
            return sb.toString();
        } catch (Throwable t) {
            return "-";
        }
    }

    // ================= 密钥生成 =================

    /**
     * 生成 RSA 自签密钥并写入 PKCS#12。返回可用的 alias。
     * 纯 JDK 实现，不依赖 keytool / BouncyCastle。
     */
    public static String generateKey(File outP12, char[] pass, String cn, String ou, String o,
                                     int days, int keySize) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(keySize <= 0 ? 2048 : keySize, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();
        X509Certificate cert = SelfSignedCert.create(kp, cn, ou, o, days <= 0 ? 10950 : days);

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(ALIAS, kp.getPrivate(), pass, new Certificate[]{cert});
        File parent = outP12.getParentFile();
        if (parent != null) parent.mkdirs();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ks.store(bos, pass);
        FileOutputStream fos = new FileOutputStream(outP12);
        try {
            fos.write(bos.toByteArray());
            fos.flush();
        } finally {
            fos.close();
        }
        return ALIAS;
    }
}
