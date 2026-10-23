package com.seagull.avt.core;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * APK 图标替换。
 *
 * 链路：AndroidManifest 里 &lt;application android:icon&gt; 的资源 ID
 * → resources.arsc 里该 ID 在**所有配置**下的取值
 * → res/ 下真实的图片条目（mipmap-hdpi-v4/ic_launcher.png 之类）
 * → 自适应图标（mipmap-anydpi-v26/ic_launcher.xml）继续跟 foreground / background /
 *   monochrome 引用，直到拿到真正要换的图片。
 *
 * 本类不解码图片：PNG / WebP / JPEG 只读头部拿原始宽高，缩放编码交给调用方的
 * {@link Scaler}（App 用 BitmapFactory，桌面 CLI 用 ImageIO），core 保持纯 JDK。
 *
 * 替换走 ZipAlignWriter.Edit.replace，和别的条目替换同一条重打包路径。
 */
public final class IconReplacer {

    /** 清单里根本没有 android:icon（分包 APK 的正常情况）：调用方可以只记日志、不算失败。 */
    public static final class NoIcon extends Exception {
        public NoIcon(String msg) {
            super(msg);
        }
    }

    /** 把新图标缩放到指定尺寸并编码成 PNG。实现方负责解码 / 编码。 */
    public interface Scaler {
        byte[] scale(byte[] image, int width, int height) throws Exception;
    }

    /** 一次图标替换的结果：条目名 → 新字节。 */
    public static final class Result {
        public final Map<String, byte[]> replace = new LinkedHashMap<String, byte[]>();
        public final List<String> notes = new ArrayList<String>();
        public int iconResId;
        public int images;
        public int skipped;

        public boolean isEmpty() {
            return replace.isEmpty();
        }

        public String summary() {
            StringBuilder sb = new StringBuilder(128);
            sb.append("图标: 0x").append(Integer.toHexString(iconResId))
                    .append(" → 替换 ").append(images).append(" 个图片条目");
            if (skipped > 0) sb.append("，跳过 ").append(skipped);
            int shown = Math.min(notes.size(), 12);
            for (int i = 0; i < shown; i++) sb.append('\n').append("  · ").append(notes.get(i));
            if (notes.size() > shown) sb.append('\n').append("  · …还有 ").append(notes.size() - shown).append(" 条");
            return sb.toString();
        }
    }

    private static final int TYPE_REFERENCE = 0x01;
    private static final int TYPE_STRING = 0x03;

    private static final int CHUNK_STRING_POOL = 0x0001;
    private static final int CHUNK_START_EL = 0x0102;

    private IconReplacer() {}

    // ================= 入口 =================

    /**
     * 算出要替换的条目与各自的新字节。
     *
     * @param apk    源 APK
     * @param image  用户选的新图标（PNG / WebP / JPEG 都行，Scaler 负责解码）
     * @param scaler 缩放实现
     */
    public static Result build(File apk, byte[] image, Scaler scaler) throws Exception {
        return build(apk, image, scaler, null);
    }

    /**
     * @param arscOverride 传 null 表示从 APK 里读；若前一步（包名联动）已经改过 resources.arsc，
     *                     必须把改过的那份传进来，否则图标这一步会把包名改动覆盖回去
     */
    public static Result build(File apk, byte[] image, Scaler scaler, byte[] arscOverride) throws Exception {
        if (image == null || image.length == 0) throw new Exception("没有选择图标图片");
        if (scaler == null) throw new Exception("缺少图片缩放实现");

        byte[] manifest = ZipAlignWriter.extractEntry(apk, "AndroidManifest.xml");
        if (manifest == null) throw new Exception("APK 里没有 AndroidManifest.xml");
        byte[] arsc = arscOverride != null ? arscOverride : ZipAlignWriter.extractEntry(apk, "resources.arsc");
        if (arsc == null) throw new Exception("APK 里没有 resources.arsc");

        Result r = new Result();
        int iconId = resolveIconId(manifest, arsc, r);
        if (iconId == 0) throw new NoIcon("清单里没有可解析的 android:icon");
        r.iconResId = iconId;

        Set<String> files = new LinkedHashSet<String>();
        collect(apk, arsc, iconId, files, r, 0);
        int roundId = readRef(manifest, "roundIcon");
        if (roundId != 0 && roundId != iconId) collect(apk, arsc, roundId, files, r, 0);
        if (files.isEmpty()) {
            // 纯矢量/自适应图标：没有位图可换，就在 arsc 里把图标直接指到新加的 PNG
            if (!vectorFallback(apk, arsc, iconId, image, scaler, r)) {
                throw new Exception("图标既没有位图条目、也没有可改写的 XML 值，换不了");
            }
            return r;
        }

        int count = 0;
        for (String path : files) {
            if (count++ > 400) {
                r.notes.add("图标条目超过 400 个，后面的不再处理");
                break;
            }
            byte[] old = ZipAlignWriter.extractEntry(apk, path);
            if (old == null || old.length == 0) {
                r.skipped++;
                r.notes.add(path + "：条目读不到，跳过");
                continue;
            }
            int[] wh = imageSize(old);
            String sizeFrom = "原图";
            if (wh == null) {
                wh = sizeFromPath(path);
                sizeFrom = "路径密度";
            }
            if (wh == null || wh[0] <= 0 || wh[1] <= 0) {
                r.skipped++;
                r.notes.add(path + "：读不出尺寸，跳过");
                continue;
            }
            byte[] png = scaler.scale(image, wh[0], wh[1]);
            if (png == null || png.length == 0) {
                r.skipped++;
                r.notes.add(path + "：缩放结果为空，跳过");
                continue;
            }
            r.replace.put(path, png);
            r.images++;
            r.notes.add(path + " → " + wh[0] + "×" + wh[1] + "（" + sizeFrom + "）");
        }
        if (r.replace.isEmpty()) throw new Exception("没有可替换的图片条目");
        return r;
    }

    // ================= 清单 → 资源 ID =================

    /** 读清单 application 上某个引用型属性，返回资源 ID；不是引用返回 0。 */
    private static int readRef(byte[] manifest, String attr) {
        try {
            AxmlPatch.AttrValue v = AxmlPatch.readAttr(manifest, "application", attr, true);
            return (v != null && v.type == TYPE_REFERENCE) ? v.data : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int resolveIconId(byte[] manifest, byte[] arsc, Result r) {
        try {
            AxmlPatch.AttrValue v = AxmlPatch.readAttr(manifest, "application", "icon", true);
            if (v == null) {
                v = AxmlPatch.readAttr(manifest, "application", "roundIcon", true);
                if (v != null) r.notes.add("清单只有 roundIcon，按它换");
            }
            if (v == null) return 0;
            if (v.type == TYPE_REFERENCE) return v.data;
            if (v.type == TYPE_STRING && v.text != null) {
                String t = v.text.trim();
                if (t.startsWith("@") && t.length() > 1) {
                    String body = t.substring(1);
                    int slash = body.indexOf('/');
                    if (slash > 0) {
                        int id = Arsc.findByName(arsc, body.substring(0, slash), body.substring(slash + 1));
                        if (id != 0) return id;
                    }
                    if (body.startsWith("0x") || body.startsWith("0X")) {
                        try {
                            return (int) Long.parseLong(body.substring(2), 16);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
            return 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    // ================= 资源 ID → 图片条目 =================

    private static void collect(File apk, byte[] arsc, int resId, Set<String> out,
                                Result r, int depth) throws Exception {
        if (depth > 4 || resId == 0) return;
        List<Arsc.Value> values = Arsc.allValues(arsc, resId);
        if (values.isEmpty()) return;
        for (int i = 0; i < values.size(); i++) {
            Arsc.Value v = values.get(i);
            if (v.type == TYPE_STRING && v.string != null) {
                String p = v.string;
                if (!p.startsWith("res/")) continue;
                if (p.toLowerCase().endsWith(".xml")) {
                    byte[] xml = ZipAlignWriter.extractEntry(apk, p);
                    if (xml == null) continue;
                    List<Integer> refs = xmlRefs(xml);
                    for (int k = 0; k < refs.size(); k++) collect(apk, arsc, refs.get(k), out, r, depth + 1);
                } else if (isRaster(p)) {
                    out.add(p);
                }
            } else if (v.type == TYPE_REFERENCE) {
                collect(apk, arsc, v.data, out, r, depth + 1);
            }
        }
    }

    /**
     * 矢量 / 自适应图标的兜底：把 PNG 作为新条目塞进包里，再把 arsc 里该图标资源
     * 全部配置的值改成这个新路径（新路径字符串追加在全局池尾部，已有索引不变）。
     */
    private static boolean vectorFallback(File apk, byte[] arsc, int resId, byte[] image,
                                          Scaler scaler, Result r) throws Exception {
        boolean anyXml = false;
        for (Arsc.Value v : Arsc.allValues(arsc, resId)) {
            if (v.type == TYPE_STRING && v.string != null && v.string.toLowerCase().endsWith(".xml")) anyXml = true;
        }
        if (!anyXml) return false;

        String path = "res/avticon.png";
        for (int i = 2; apkHasEntry(apk, path); i++) path = "res/avticon" + i + ".png";

        byte[] png = scaler.scale(image, 512, 512);
        if (png == null || png.length == 0) return false;

        int[] first = new int[1];
        byte[] out = Arsc.appendGlobalStrings(arsc, java.util.Collections.singletonList(path), first);
        if (first[0] < 0) return false;

        int patched = 0;
        for (Integer offObj : Arsc.valueOffsets(out, resId)) {
            int off = offObj.intValue();
            if ((out[off + 3] & 0xFF) != TYPE_STRING) continue;
            writeU32(out, off + 4, first[0]);
            patched++;
        }
        if (patched == 0) return false;

        r.replace.put("resources.arsc", out);
        r.replace.put(path, png);
        r.images++;
        r.notes.add("矢量图标 → arsc 里 " + patched + " 个配置改指 " + path + "（512×512）");
        return true;
    }

    private static boolean apkHasEntry(File apk, String name) {
        try {
            return ZipAlignWriter.extractEntry(apk, name) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void writeU32(byte[] a, int o, int v) {
        a[o] = (byte) v;
        a[o + 1] = (byte) (v >>> 8);
        a[o + 2] = (byte) (v >>> 16);
        a[o + 3] = (byte) (v >>> 24);
    }

    private static boolean isRaster(String path) {
        String p = path.toLowerCase();
        return p.endsWith(".png") || p.endsWith(".webp") || p.endsWith(".jpg") || p.endsWith(".jpeg");
    }

    /** 从二进制 AXML 里捞出所有引用型属性值（@mipmap/… @drawable/… 这类）。 */
    private static List<Integer> xmlRefs(byte[] a) {
        List<Integer> out = new ArrayList<Integer>();
        try {
            if (a.length < 12 || u16(a, 0) != 0x0003) return out;
            List<String> pool = null;
            int pos = u16(a, 2);
            if (pos <= 0) pos = 8;
            while (pos + 8 <= a.length) {
                int type = u16(a, pos);
                int size = u32(a, pos + 4);
                if (size <= 0 || pos + size > a.length) break;
                if (type == CHUNK_STRING_POOL) {
                    pool = parsePool(a, pos);
                } else if (type == CHUNK_START_EL && pool != null) {
                    int attrStart = u16(a, pos + 24);
                    int attrSize = u16(a, pos + 26);
                    int attrCount = u16(a, pos + 28);
                    if (attrSize <= 0) attrSize = 20;
                    if (attrStart <= 0) attrStart = 20;
                    for (int i = 0; i < attrCount; i++) {
                        int b = pos + 16 + attrStart + i * attrSize;
                        if (b + 20 > pos + size) break;
                        int dataType = a[b + 15] & 0xFF;
                        int data = u32(a, b + 16);
                        if (dataType == TYPE_REFERENCE && data != 0 && (data >>> 24) != 0) out.add(data);
                    }
                }
                pos += size;
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static List<String> parsePool(byte[] a, int off) {
        int count = u32(a, off + 8);
        boolean utf8 = (u32(a, off + 16) & 0x100) != 0;
        int stringsStart = u32(a, off + 20);
        List<String> list = new ArrayList<String>(Math.max(0, Math.min(count, 4096)));
        for (int i = 0; i < count; i++) {
            int o = off + stringsStart + u32(a, off + 28 + 4 * i);
            list.add(utf8 ? readUtf8(a, o) : readUtf16(a, o));
        }
        return list;
    }

    private static String readUtf8(byte[] a, int off) {
        try {
            int[] r1 = var8(a, off);
            int[] r2 = var8(a, r1[1]);
            int start = r2[1];
            int len = r2[0];
            if (start < 0 || len < 0 || start + len > a.length) return "";
            return Dex.decodeMutf8(a, start, len);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String readUtf16(byte[] a, int off) {
        StringBuilder sb = new StringBuilder();
        int p = off;
        while (p + 1 < a.length) {
            char c = (char) u16(a, p);
            if (c == 0) break;
            sb.append(c);
            p += 2;
        }
        return sb.toString();
    }

    private static int[] var8(byte[] a, int off) {
        int b0 = a[off] & 0xFF;
        if ((b0 & 0x80) != 0) return new int[]{((b0 & 0x7F) << 8) | (a[off + 1] & 0xFF), off + 2};
        return new int[]{b0, off + 1};
    }

    // ================= 尺寸 =================

    /** 读图片头部拿宽高；认不出返回 null。 */
    static int[] imageSize(byte[] a) {
        if (a == null || a.length < 32) return null;
        // PNG：89 50 4E 47 …，IHDR 宽高在 16 / 20
        if ((a[0] & 0xFF) == 0x89 && a[1] == 'P' && a[2] == 'N' && a[3] == 'G') {
            int w = be32(a, 16);
            int h = be32(a, 20);
            return (w > 0 && h > 0) ? new int[]{w, h} : null;
        }
        // WebP：RIFF....WEBP
        if (a[0] == 'R' && a[1] == 'I' && a[2] == 'F' && a[3] == 'F'
                && a[8] == 'W' && a[9] == 'E' && a[10] == 'B' && a[11] == 'P') {
            String tag = new String(new char[]{(char) a[12], (char) a[13], (char) a[14], (char) a[15]});
            if ("VP8X".equals(tag)) {
                int w = le24(a, 24) + 1;
                int h = le24(a, 27) + 1;
                return (w > 0 && h > 0) ? new int[]{w, h} : null;
            }
            if ("VP8L".equals(tag)) {
                int b = le32(a, 21);
                int w = (b & 0x3FFF) + 1;
                int h = ((b >>> 14) & 0x3FFF) + 1;
                return (w > 0 && h > 0) ? new int[]{w, h} : null;
            }
        }
        // JPEG：扫 SOF0..SOF3
        if ((a[0] & 0xFF) == 0xFF && (a[1] & 0xFF) == 0xD8) {
            int p = 2;
            while (p + 9 < a.length) {
                if ((a[p] & 0xFF) != 0xFF) {
                    p++;
                    continue;
                }
                int m = a[p + 1] & 0xFF;
                if (m >= 0xC0 && m <= 0xC3) {
                    int h = be16(a, p + 5);
                    int w = be16(a, p + 7);
                    return (w > 0 && h > 0) ? new int[]{w, h} : null;
                }
                int len = be16(a, p + 2);
                if (len <= 0) return null;
                p += 2 + len;
            }
        }
        return null;
    }

    /** 原图读不出尺寸时，按 res/mipmap-xxhdpi-v4/… 里的密度限定词给个标准尺寸。 */
    static int[] sizeFromPath(String path) {
        String p = path.toLowerCase();
        int size;
        if (p.contains("-ldpi")) size = 36;
        else if (p.contains("-mdpi")) size = 48;
        else if (p.contains("-hdpi")) size = 72;
        else if (p.contains("-xhdpi") && !p.contains("-xxhdpi") && !p.contains("-xxxhdpi")) size = 96;
        else if (p.contains("-xxhdpi") && !p.contains("-xxxhdpi")) size = 144;
        else if (p.contains("-xxxhdpi")) size = 192;
        else return null;
        return new int[]{size, size};
    }

    // ================= 小工具 =================

    private static int u16(byte[] a, int o) {
        return (a[o] & 0xFF) | ((a[o + 1] & 0xFF) << 8);
    }

    private static int u32(byte[] a, int o) {
        return (a[o] & 0xFF) | ((a[o + 1] & 0xFF) << 8) | ((a[o + 2] & 0xFF) << 16) | ((a[o + 3] & 0xFF) << 24);
    }

    private static int be16(byte[] a, int o) {
        return ((a[o] & 0xFF) << 8) | (a[o + 1] & 0xFF);
    }

    private static int be32(byte[] a, int o) {
        return ((a[o] & 0xFF) << 24) | ((a[o + 1] & 0xFF) << 16) | ((a[o + 2] & 0xFF) << 8) | (a[o + 3] & 0xFF);
    }

    private static int le24(byte[] a, int o) {
        return (a[o] & 0xFF) | ((a[o + 1] & 0xFF) << 8) | ((a[o + 2] & 0xFF) << 16);
    }

    private static int le32(byte[] a, int o) {
        return (a[o] & 0xFF) | ((a[o + 1] & 0xFF) << 8) | ((a[o + 2] & 0xFF) << 16) | ((a[o + 3] & 0xFF) << 24);
    }
}
