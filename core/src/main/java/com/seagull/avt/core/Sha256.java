package com.seagull.avt.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/** SHA-256 摘要工具（APKS 的 toc.pb 摘要回填、APK指纹都用它）。 */
public final class Sha256 {

    private Sha256() {}

    public static byte[] of(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(data);
    }

    public static byte[] ofFile(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        } finally {
            try { in.close(); } catch (Throwable ignored) {}
        }
        return md.digest();
    }

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xFF;
            if (v < 16) sb.append('0');
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }

    public static String ofFileHex(File f) {
        try { return hex(ofFile(f)); } catch (Throwable t) { return "-"; }
    }

    /** 在 haystack 中把 needle 的每一处替换成 repl（等长），返回替换次数。 */
    public static int replaceAll(byte[] haystack, byte[] needle, byte[] repl) {
        if (needle.length == 0 || needle.length != repl.length) return 0;
        int hits = 0;
        int last = haystack.length - needle.length;
        for (int i = 0; i <= last; i++) {
            boolean ok = true;
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) { ok = false; break; }
            }
            if (ok) {
                System.arraycopy(repl, 0, haystack, i, repl.length);
                hits++;
                i += needle.length - 1;
            }
        }
        return hits;
    }
}
