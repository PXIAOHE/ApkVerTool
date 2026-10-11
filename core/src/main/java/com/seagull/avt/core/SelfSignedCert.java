package com.seagull.avt.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.SimpleTimeZone;

/**
 * 最小 X.509 v3 自签证书生成器。纯 JDK 手写 DER 编码，不依赖 BouncyCastle，
 * 也不依赖 Android 上不存在的 sun.security.x509。
 */
public final class SelfSignedCert {

    private static final int[] OID_CN = {2, 5, 4, 3};
    private static final int[] OID_OU = {2, 5, 4, 11};
    private static final int[] OID_O = {2, 5, 4, 10};
    private static final int[] OID_C = {2, 5, 4, 6};
    private static final int[] OID_SHA256_RSA = {1, 2, 840, 113549, 1, 1, 11};

    private SelfSignedCert() {}

    /** 用给定密钥对生成自签证书；days 为有效期天数。 */
    public static X509Certificate create(KeyPair kp, String cn, String ou, String o, int days)
            throws Exception {
        SecureRandom rnd = new SecureRandom();
        byte[] serialBytes = new byte[8];
        rnd.nextBytes(serialBytes);
        serialBytes[0] &= 0x7F;
        if (serialBytes[0] == 0) serialBytes[0] = 1;
        BigInteger serial = new BigInteger(1, serialBytes);

        Calendar notBefore = new GregorianCalendar(new SimpleTimeZone(0, "UTC"));
        notBefore.add(Calendar.DAY_OF_MONTH, -1);
        Calendar notAfter = new GregorianCalendar(new SimpleTimeZone(0, "UTC"));
        notAfter.add(Calendar.DAY_OF_MONTH, Math.max(1, days));

        ByteArrayOutputStream name = new ByteArrayOutputStream();
        if (o != null && o.length() > 0) name.write(rdn(OID_O, o));
        if (ou != null && ou.length() > 0) name.write(rdn(OID_OU, ou));
        name.write(rdn(OID_CN, cn == null || cn.length() == 0 ? "ApkVerTool" : cn));
        byte[] nameDer = tlv(0x30, name.toByteArray());

        byte[] algo = tlv(0x30, concat(oid(OID_SHA256_RSA), tlv(0x05, new byte[0])));
        byte[] validity = tlv(0x30, concat(time(notBefore), time(notAfter)));
        byte[] spki = kp.getPublic().getEncoded();

        ByteArrayOutputStream tbsBody = new ByteArrayOutputStream();
        tbsBody.write(tlv(0xA0, tlv(0x02, new byte[]{2})));      // version v3
        tbsBody.write(tlv(0x02, bigIntBytes(serial)));
        tbsBody.write(algo);
        tbsBody.write(nameDer);
        tbsBody.write(validity);
        tbsBody.write(nameDer);
        tbsBody.write(spki);
        byte[] tbs = tlv(0x30, tbsBody.toByteArray());

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(kp.getPrivate());
        sig.update(tbs);
        byte[] signature = sig.sign();

        byte[] cert = tlv(0x30, concat(tbs, algo, tlv(0x03, concat(new byte[]{0}, signature))));
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(cert));
    }

    // ---------------- DER 基础编码 ----------------

    private static byte[] rdn(int[] oidValue, String value) {
        byte[] atv = tlv(0x30, concat(oid(oidValue), utf8(value)));
        return tlv(0x31, atv);
    }

    private static byte[] oid(int[] v) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(v[0] * 40 + v[1]);
        for (int i = 2; i < v.length; i++) {
            int x = v[i];
            if (x < 128) {
                b.write(x);
            } else {
                int[] tmp = new int[6];
                int n = 0;
                while (x > 0) { tmp[n++] = x & 0x7F; x >>= 7; }
                for (int j = n - 1; j >= 0; j--) b.write(j == 0 ? tmp[j] : (tmp[j] | 0x80));
            }
        }
        return tlv(0x06, b.toByteArray());
    }

    private static byte[] time(Calendar c) {
        int year = c.get(Calendar.YEAR);
        String s;
        int tag;
        if (year >= 1950 && year <= 2049) {
            tag = 0x17;
            s = two(year % 100) + two(c.get(Calendar.MONTH) + 1) + two(c.get(Calendar.DAY_OF_MONTH))
                    + two(c.get(Calendar.HOUR_OF_DAY)) + two(c.get(Calendar.MINUTE))
                    + two(c.get(Calendar.SECOND)) + "Z";
        } else {
            tag = 0x18;
            s = four(year) + two(c.get(Calendar.MONTH) + 1) + two(c.get(Calendar.DAY_OF_MONTH))
                    + two(c.get(Calendar.HOUR_OF_DAY)) + two(c.get(Calendar.MINUTE))
                    + two(c.get(Calendar.SECOND)) + "Z";
        }
        return tlv(tag, asciiRaw(s));
    }

    private static byte[] bigIntBytes(BigInteger v) {
        byte[] b = v.toByteArray();
        if (b.length > 1 && b[0] == 0 && (b[1] & 0x80) == 0) {
            byte[] t = new byte[b.length - 1];
            System.arraycopy(b, 1, t, 0, t.length);
            return t;
        }
        return b;
    }

    private static byte[] utf8(String s) {
        try { return tlv(0x0C, s.getBytes("UTF-8")); }
        catch (Exception e) { return tlv(0x0C, new byte[0]); }
    }

    private static byte[] asciiRaw(String s) {
        try { return s.getBytes("US-ASCII"); }
        catch (Exception e) { return new byte[0]; }
    }

    private static byte[] tlv(int tag, byte[] content) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(content.length + 6);
        o.write(tag);
        int len = content.length;
        if (len < 0x80) {
            o.write(len);
        } else if (len <= 0xFF) {
            o.write(0x81);
            o.write(len);
        } else if (len <= 0xFFFF) {
            o.write(0x82);
            o.write((len >> 8) & 0xFF);
            o.write(len & 0xFF);
        } else {
            o.write(0x83);
            o.write((len >> 16) & 0xFF);
            o.write((len >> 8) & 0xFF);
            o.write(len & 0xFF);
        }
        o.write(content, 0, content.length);
        return o.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int off = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        return out;
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    private static String four(int v) {
        if (v < 10) return "000" + v;
        if (v < 100) return "00" + v;
        if (v < 1000) return "0" + v;
        return String.valueOf(v);
    }
}
