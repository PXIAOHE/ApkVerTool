package com.seagull.avt.core;

/**
 * 包名替换规则。dex / resources.arsc / .so / AndroidManifest 共用同一套匹配逻辑。
 *
 * 斜杠形式（com/old/name）匹配：后面必须是 '/'、';' 或字符串结尾
 * 点号形式（com.old.name）匹配：后面必须是 '.' 或字符串结尾
 *
 * 这样 Lcom/old/name/Foo; 、com.old.name.Foo、com.old.name 都能命中，
 * 而 com.old.nameX 不会被误伤。
 */
public final class PkgRename {

    public final String oldPkg;
    public final String newPkg;
    public final String oldSlash;
    public final String newSlash;
    public final String oldDot;
    public final String newDot;

    public PkgRename(String oldPkg, String newPkg) {
        this.oldPkg = oldPkg;
        this.newPkg = newPkg;
        this.oldSlash = oldPkg.replace('.', '/');
        this.newSlash = newPkg.replace('.', '/');
        this.oldDot = oldPkg;
        this.newDot = newPkg;
    }

    public boolean isValid() {
        return oldPkg != null && newPkg != null && oldPkg.length() > 0
                && newPkg.length() > 0 && !oldPkg.equals(newPkg);
    }

    /** 命中斜杠形式时要求的新长度差（用于 .so 等长校验）。 */
    public int slashDelta() {
        return newSlash.length() - oldSlash.length();
    }

    public int dotDelta() {
        return newDot.length() - oldDot.length();
    }

    /** 返回替换后的字符串；无改动返回 null。 */
    public String apply(String s) {
        if (s == null || s.length() == 0 || !isValid()) return null;
        if (s.indexOf(oldSlash) < 0 && s.indexOf(oldDot) < 0) return null;
        String out = replaceSlash(s);
        out = replaceDot(out);
        return out.equals(s) ? null : out;
    }

    /** 字符串里是否含旧包名。 */
    public boolean contains(String s) {
        if (s == null) return false;
        return s.indexOf(oldSlash) >= 0 || s.indexOf(oldDot) >= 0;
    }

    private String replaceSlash(String s) {
        StringBuilder sb = null;
        int from = 0;
        while (true) {
            int i = s.indexOf(oldSlash, from);
            if (i < 0) break;
            int after = i + oldSlash.length();
            char c = after < s.length() ? s.charAt(after) : 0;
            if (c == '/' || c == ';' || after == s.length()) {
                if (sb == null) sb = new StringBuilder(s.length() + 32);
                sb.append(s, 0, i).append(newSlash);
                from = after;
            } else {
                if (sb == null) sb = new StringBuilder(s.length() + 32);
                sb.append(s, 0, after);
                from = after;
            }
        }
        if (sb == null) return s;
        sb.append(s, from, s.length());
        return sb.toString();
    }

    private String replaceDot(String s) {
        StringBuilder sb = null;
        int from = 0;
        while (true) {
            int i = s.indexOf(oldDot, from);
            if (i < 0) break;
            int after = i + oldDot.length();
            char c = after < s.length() ? s.charAt(after) : 0;
            if (c == '.' || after == s.length()) {
                if (sb == null) sb = new StringBuilder(s.length() + 32);
                sb.append(s, 0, i).append(newDot);
                from = after;
            } else {
                if (sb == null) sb = new StringBuilder(s.length() + 32);
                sb.append(s, 0, after);
                from = after;
            }
        }
        if (sb == null) return s;
        sb.append(s, from, s.length());
        return sb.toString();
    }
}
