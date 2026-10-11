# -*- coding: utf-8 -*-
"""验签详情：不重签产物也能显示原签名者（直接从签名块解析证书）。"""
import io, sys

p = '/workspace/avt/src/com/seagull/avt/core/SignUtil.java'
s = io.open(p, encoding='utf-8').read()

reps = []

reps.append((
    """        public boolean v1Present;
        public final List<CertInfo> signers = new ArrayList<CertInfo>();
""",
    """        public boolean v1Present;
        /** true = signers 是从签名块直接解析出来的（未重签产物，apksig 因摘要不匹配不回报证书）。 */
        public boolean signersFromBlock;
        public final List<CertInfo> signers = new ArrayList<CertInfo>();
"""))

reps.append((
    """                sb.append("签名者 ").append(i + 1).append(":\\n");
""",
    """                sb.append("签名者 ").append(i + 1)
                  .append(signersFromBlock ? "（原签名，未重签）:" : ":").append('\\n');
"""))

reps.append((
    """        try {
            List<String> names = ZipAlignWriter.listNames(apk);
            for (int i = 0; i < names.size(); i++) {
                if (ZipAlignWriter.isV1SignatureEntry(names.get(i))) { d.v1Present = true; break; }
            }
        } catch (Throwable ignored) {}
        return d;
    }
""",
    """        try {
            List<String> names = ZipAlignWriter.listNames(apk);
            for (int i = 0; i < names.size(); i++) {
                if (ZipAlignWriter.isV1SignatureEntry(names.get(i))) { d.v1Present = true; break; }
            }
        } catch (Throwable ignored) {}
        if (d.signers.isEmpty()) {
            // 不重签产物：签名块原样搬过来的，apksig 因为摘要不匹配不回报证书，
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
     * 用途：MT 式不重签的产物，apksig 会因为改动后摘要不匹配而不回报证书，
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
                            out.add(certInfo(c));
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
"""))

for old, new in reps:
    n = s.count(old)
    if n != 1:
        print('!! 匹配 %d 次: %r' % (n, old.strip().splitlines()[0][:70]))
        sys.exit(1)
    s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8').write(s)
print('ok SignUtil.java')
