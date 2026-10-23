package com.seagull.avt.core;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Android 二进制 AXML（AndroidManifest.xml）通用读写引擎。纯 JDK，无 Android 依赖。
 *
 * 相对 v1 的能力提升：
 *  - 完整块级解析（字符串池 / 资源映射 / 命名空间 / 起止元素 / CDATA），未知块原样保留
 *  - 任意整型属性、字符串属性改写，属性缺失时自动新增（含资源映射表补 ID）
 *  - 元素插入 / 删除（uses-sdk、uses-permission）
 *  - 字符串池按原始编码重建（UTF-8 或 UTF-16），不再强制转 UTF-8
 *  - versionCode / versionCodeMajor 64 位版本号支持
 */
public final class AxmlPatch {

    public static final int TYPE_REFERENCE = 0x01;
    public static final int TYPE_STRING = 0x03;
    public static final int TYPE_FLOAT = 0x04;
    public static final int TYPE_INT_DEC = 0x10;
    public static final int TYPE_INT_HEX = 0x11;
    public static final int TYPE_INT_BOOLEAN = 0x12;

    private static final int CHUNK_STRING_POOL = 0x0001;
    private static final int CHUNK_RES_MAP = 0x0180;
    private static final int CHUNK_START_NS = 0x0100;
    private static final int CHUNK_END_NS = 0x0101;
    private static final int CHUNK_START_EL = 0x0102;
    private static final int CHUNK_END_EL = 0x0103;

    private static final int NO_INDEX = 0xFFFFFFFF;
    private static final String ANDROID_NS_URI = "http://schemas.android.com/apk/res/android";

    /** android 命名空间框架属性 ID：新增属性时必须同时写入 RES_XML_RESOURCE_MAP，否则 PM 认不出来。 */
    public static final Map<String, Integer> FW_ATTR = new HashMap<String, Integer>();
    static {
        FW_ATTR.put("theme", 0x01010000);
        FW_ATTR.put("label", 0x01010001);
        FW_ATTR.put("icon", 0x01010002);
        FW_ATTR.put("name", 0x01010003);
        FW_ATTR.put("debuggable", 0x0101000F);
        FW_ATTR.put("exported", 0x01010010);
        FW_ATTR.put("largeHeap", 0x0101021A);
        FW_ATTR.put("minSdkVersion", 0x0101020C);
        FW_ATTR.put("versionCode", 0x0101021B);
        FW_ATTR.put("versionName", 0x0101021C);
        FW_ATTR.put("targetSdkVersion", 0x01010270);
        FW_ATTR.put("maxSdkVersion", 0x01010271);
        FW_ATTR.put("allowBackup", 0x01010280);
        FW_ATTR.put("extractNativeLibs", 0x010104EA);
        FW_ATTR.put("versionCodeMajor", 0x01010576);
    }

    private AxmlPatch() {}

    // ================= 对外数据结构 =================

    /** 清单摘要。 */
    public static final class Info {
        public String packageName;
        public String versionName;
        public Long versionCode;
        public Long versionCodeMajor;
        public Integer minSdk;
        public Integer targetSdk;
        public String label;
        public Boolean labelIsRef;
        public Boolean debuggable;
        public Boolean extractNativeLibs;
        public List<String> permissions = new ArrayList<String>();
        public int poolEncoding;   // 0x100 = UTF-8，0 = UTF-16
        public int attrCount;

        /** Android 9+ 的 64 位 versionCode。 */
        public long fullVersionCode() {
            long lo = versionCode == null ? 0L : (versionCode.longValue() & 0xFFFFFFFFL);
            long hi = versionCodeMajor == null ? 0L : (versionCodeMajor.longValue() & 0xFFFFFFFFL);
            return (hi << 32) | lo;
        }

        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("包名      : ").append(packageName == null ? "-" : packageName).append('\n');
            sb.append("versionName: ").append(versionName == null ? "-" : versionName).append('\n');
            sb.append("versionCode: ").append(versionCode == null ? "-" : String.valueOf(versionCode));
            if (versionCodeMajor != null && versionCodeMajor.longValue() != 0L) {
                sb.append("  (major=").append(versionCodeMajor).append("，合计 ")
                  .append(fullVersionCode()).append(')');
            }
            sb.append('\n');
            sb.append("minSdk    : ").append(minSdk == null ? "-" : String.valueOf(minSdk)).append('\n');
            sb.append("targetSdk : ").append(targetSdk == null ? "-" : String.valueOf(targetSdk)).append('\n');
            sb.append("应用名    : ").append(label == null ? "-" : label)
              .append(Boolean.TRUE.equals(labelIsRef) ? "（资源引用）" : "").append('\n');
            if (debuggable != null) sb.append("debuggable: ").append(debuggable).append('\n');
            if (extractNativeLibs != null) sb.append("extractNativeLibs: ").append(extractNativeLibs).append('\n');
            sb.append("权限      : ").append(permissions.size()).append(" 条");
            if (!permissions.isEmpty()) {
                int show = Math.min(6, permissions.size());
                for (int i = 0; i < show; i++) sb.append('\n').append("  · ").append(shortName(permissions.get(i)));
                if (permissions.size() > show) sb.append('\n').append("  · …还有 ").append(permissions.size() - show).append(" 条");
            }
            return sb.toString();
        }

        private static String shortName(String p) {
            int i = p.lastIndexOf('.');
            return i < 0 ? p : p.substring(i + 1);
        }
    }

    /** 一次写入的全部改动；null 字段表示不改。 */
    public static final class Patch {
        public String packageName;
        public String versionName;
        public Long versionCode;
        public Long versionCodeMajor;
        public Integer minSdk;
        public Integer targetSdk;
        public String label;
        public Boolean debuggable;
        public Boolean extractNativeLibs;
        public Boolean allowBackup;
        public List<String> addPermissions = new ArrayList<String>();
        public List<String> removePermissions = new ArrayList<String>();
        /** true = 池重建为 UTF-8；false（默认）= 保持原编码。 */
        public boolean forceUtf8;

        public boolean isEmpty() {
            return packageName == null && versionName == null && versionCode == null
                    && versionCodeMajor == null && minSdk == null && targetSdk == null
                    && label == null && debuggable == null && extractNativeLibs == null
                    && allowBackup == null && addPermissions.isEmpty() && removePermissions.isEmpty();
        }
    }

    // ================= 兼容旧 API =================

    /** 返回 {versionName, versionCode}，任一可为 null。 */
    public static String[] readVersion(byte[] axml) throws Exception {
        Info i = read(axml);
        return new String[]{i.versionName, i.versionCode == null ? null : String.valueOf(i.versionCode)};
    }

    /** 读 uses-sdk 的 minSdkVersion，找不到返回 24。 */
    public static int readMinSdk(byte[] axml) throws Exception {
        try {
            Info i = read(axml);
            return i.minSdk == null ? 24 : i.minSdk.intValue();
        } catch (Throwable ignored) {
            return 24;
        }
    }

    /** 旧签名兼容：只改 versionName / versionCode。 */
    public static byte[] patch(byte[] axml, String newName, Integer newCode) throws Exception {
        Patch p = new Patch();
        p.versionName = newName;
        if (newCode != null) p.versionCode = Long.valueOf(newCode.longValue() & 0xFFFFFFFFL);
        if (p.isEmpty()) return axml;
        return apply(axml, p);
    }

    // ================= 读 =================

    public static Info read(byte[] axml) throws Exception {
        Doc d = parse(axml);
        Info info = new Info();
        info.poolEncoding = d.utf8 ? 0x100 : 0;
        info.attrCount = d.strings.size();

        Chunk m = findElement(d, "manifest");
        if (m == null) throw new Exception("AXML 中找不到 <manifest> 元素");
        info.packageName = attrString(d, m, "package", false);
        info.versionName = attrString(d, m, "versionName", true);
        info.versionCode = attrLong(d, m, "versionCode");
        info.versionCodeMajor = attrLong(d, m, "versionCodeMajor");

        Chunk sdk = findElement(d, "uses-sdk");
        if (sdk != null) {
            info.minSdk = attrInt(d, sdk, "minSdkVersion");
            info.targetSdk = attrInt(d, sdk, "targetSdkVersion");
        }

        Chunk app = findElement(d, "application");
        if (app != null) {
            Attr la = findAttr(d, app, "label", androidNs(d, false));
            if (la != null) {
                if (la.type == TYPE_STRING) {
                    info.label = str(d, la.data);
                } else if (la.type == TYPE_REFERENCE) {
                    info.label = "@0x" + Integer.toHexString(la.data);
                    info.labelIsRef = Boolean.TRUE;
                }
            }
            info.debuggable = attrBool(d, app, "debuggable");
            info.extractNativeLibs = attrBool(d, app, "extractNativeLibs");
        }

        for (int i = 0; i < d.chunks.size(); i++) {
            Chunk c = d.chunks.get(i);
            if (c.type != CHUNK_START_EL) continue;
            if (!"uses-permission".equals(str(d, c.nameIdx))) continue;
            String n = attrString(d, c, "name", true);
            if (n != null) info.permissions.add(n);
        }
        return info;
    }

    // ================= 写 =================

    /**
     * 按索引改写字符串池（保持顺序 → 所有索引引用自动跟随），用于包名联动。
     * 不改变任何块结构，池编码保持不变。
     */
    public static byte[] rewritePool(byte[] axml, PkgRename rename) throws Exception {
        Doc d = parse(axml);
        int changed = 0;
        for (int i = 0; i < d.strings.size(); i++) {
            String s = d.strings.get(i);
            String n = rename.apply(s);
            if (n != null) {
                d.strings.set(i, n);
                changed++;
            }
        }
        if (changed == 0) return axml;
        return emit(d);
    }

    public static byte[] apply(byte[] axml, Patch p) throws Exception {
        Doc d = parse(axml);
        if (p.forceUtf8) d.utf8 = true;

        Chunk manifest = findElement(d, "manifest");
        if (manifest == null) throw new Exception("AXML 中找不到 <manifest> 元素");

        if (p.packageName != null) setStringNoNs(d, manifest, "package", p.packageName);
        if (p.versionName != null) setString(d, manifest, "versionName", p.versionName);
        if (p.versionCode != null) setInt(d, manifest, "versionCode", (int) (p.versionCode.longValue() & 0xFFFFFFFFL), TYPE_INT_DEC);
        if (p.versionCodeMajor != null) setInt(d, manifest, "versionCodeMajor", (int) (p.versionCodeMajor.longValue() & 0xFFFFFFFFL), TYPE_INT_DEC);

        if (p.minSdk != null || p.targetSdk != null) {
            Chunk sdk = findElement(d, "uses-sdk");
            if (sdk == null) sdk = insertElementAfter(d, manifest, "uses-sdk");
            if (p.minSdk != null) setInt(d, sdk, "minSdkVersion", p.minSdk.intValue(), TYPE_INT_DEC);
            if (p.targetSdk != null) setInt(d, sdk, "targetSdkVersion", p.targetSdk.intValue(), TYPE_INT_DEC);
        }

        Chunk app = findElement(d, "application");
        if (app != null) {
            if (p.label != null) setString(d, app, "label", p.label);
            if (p.debuggable != null) setInt(d, app, "debuggable", p.debuggable.booleanValue() ? 1 : 0, TYPE_INT_BOOLEAN);
            if (p.extractNativeLibs != null) setInt(d, app, "extractNativeLibs", p.extractNativeLibs.booleanValue() ? 1 : 0, TYPE_INT_BOOLEAN);
            if (p.allowBackup != null) setInt(d, app, "allowBackup", p.allowBackup.booleanValue() ? 1 : 0, TYPE_INT_BOOLEAN);
        } else if (p.label != null || p.debuggable != null) {
            throw new Exception("AXML 中找不到 <application> 元素");
        }

        if (!p.removePermissions.isEmpty()) removePermissions(d, p.removePermissions);
        if (!p.addPermissions.isEmpty()) addPermissions(d, p.addPermissions);

        return emit(d);
    }

    private static void removePermissions(Doc d, List<String> names) {
        for (int i = 0; i < d.chunks.size(); i++) {
            Chunk c = d.chunks.get(i);
            if (c.type != CHUNK_START_EL || !"uses-permission".equals(str(d, c.nameIdx))) continue;
            String n = attrString(d, c, "name", true);
            if (n == null || !names.contains(n)) continue;
            d.chunks.remove(i);
            if (i < d.chunks.size() && d.chunks.get(i).type == CHUNK_END_EL
                    && "uses-permission".equals(str(d, d.chunks.get(i).nameIdx))) {
                d.chunks.remove(i);
            }
            i--;
        }
    }

    private static void addPermissions(Doc d, List<String> names) {
        List<String> existing = new ArrayList<String>();
        for (int i = 0; i < d.chunks.size(); i++) {
            Chunk c = d.chunks.get(i);
            if (c.type == CHUNK_START_EL && "uses-permission".equals(str(d, c.nameIdx))) {
                String n = attrString(d, c, "name", true);
                if (n != null) existing.add(n);
            }
        }
        int anchor = -1;
        for (int i = 0; i < d.chunks.size(); i++) {
            Chunk c = d.chunks.get(i);
            if (c.type == CHUNK_START_EL && "application".equals(str(d, c.nameIdx))) { anchor = i; break; }
        }
        if (anchor < 0) return;
        for (int k = 0; k < names.size(); k++) {
            String name = names.get(k);
            if (existing.contains(name)) continue;
            int ns = androidNs(d, true);
            Chunk st = new Chunk();
            st.type = CHUNK_START_EL;
            st.line = 1;
            st.nsIdx = ns;
            st.nameIdx = intern(d, "uses-permission");
            st.attrs = new ArrayList<Attr>();
            Attr a = new Attr();
            a.ns = ns;
            a.name = internAttr(d, "name");
            a.raw = intern(d, name);
            a.type = TYPE_STRING;
            a.data = a.raw;
            st.attrs.add(a);
            Chunk en = new Chunk();
            en.type = CHUNK_END_EL;
            en.line = 1;
            en.nsIdx = ns;
            en.nameIdx = st.nameIdx;
            d.chunks.add(anchor, st);
            d.chunks.add(anchor + 1, en);
            anchor += 2;
            existing.add(name);
        }
    }

    // ================= 属性工具 =================

    private static void setInt(Doc d, Chunk c, String name, int value, int type) {
        int ns = androidNs(d, true);
        Attr a = findAttr(d, c, name, ns);
        if (a == null) {
            a = new Attr();
            a.ns = ns;
            a.name = internAttr(d, name);
            a.raw = NO_INDEX;
            a.type = type;
            a.data = value;
            c.attrs.add(a);
        } else {
            a.type = type;
            a.data = value;
        }
    }

    private static void setString(Doc d, Chunk c, String name, String value) {
        int ns = androidNs(d, true);
        Attr a = findAttr(d, c, name, ns);
        int idx = intern(d, value);
        if (a == null) {
            a = new Attr();
            a.ns = ns;
            a.name = internAttr(d, name);
            a.raw = idx;
            a.type = TYPE_STRING;
            a.data = idx;
            c.attrs.add(a);
        } else {
            a.raw = idx;
            a.type = TYPE_STRING;
            a.data = idx;
        }
    }

    private static void setStringNoNs(Doc d, Chunk c, String name, String value) {
        Attr a = findAttrNoNs(d, c, name);
        int idx = intern(d, value);
        if (a == null) {
            a = new Attr();
            a.ns = NO_INDEX;
            a.name = intern(d, name);
            a.raw = idx;
            a.type = TYPE_STRING;
            a.data = idx;
            c.attrs.add(a);
        } else {
            a.raw = idx;
            a.type = TYPE_STRING;
            a.data = idx;
        }
    }

    private static Attr findAttr(Doc d, Chunk c, String name, int ns) {
        for (int i = 0; i < c.attrs.size(); i++) {
            Attr a = c.attrs.get(i);
            if (a.ns != ns) continue;
            if (name.equals(str(d, a.name))) return a;
        }
        return null;
    }

    private static Attr findAttrNoNs(Doc d, Chunk c, String name) {
        for (int i = 0; i < c.attrs.size(); i++) {
            Attr a = c.attrs.get(i);
            if (a.ns != NO_INDEX && a.ns != 0) continue;
            if (name.equals(str(d, a.name))) return a;
        }
        return null;
    }

    /** 元素上一个属性的原始取值（引用型返回 0x7f… 资源 ID，不做解析）。 */
    public static final class AttrValue {
        public int type;
        public int data;
        /** 字符串型时的文本，其他类型为 null。 */
        public String text;
    }

    /**
     * 读某个元素上某个属性的原始值；元素或属性不存在返回 null。
     *
     * @param androidNs 是否限定在 android 命名空间（icon / roundIcon 这类框架属性为 true）
     */
    public static AttrValue readAttr(byte[] axml, String element, String attrName, boolean androidNs)
            throws Exception {
        Doc d = parse(axml);
        Chunk c = findElement(d, element);
        if (c == null) return null;
        int ns = androidNs ? androidNs(d, false) : -1;
        for (int i = 0; i < c.attrs.size(); i++) {
            Attr a = c.attrs.get(i);
            if (ns >= 0 && a.ns != ns) continue;
            if (!attrName.equals(str(d, a.name))) continue;
            AttrValue v = new AttrValue();
            v.type = a.type;
            v.data = a.data;
            if (a.type == TYPE_STRING) v.text = str(d, a.data);
            else if (a.raw != NO_INDEX) v.text = str(d, a.raw);
            return v;
        }
        return null;
    }

    private static String attrString(Doc d, Chunk c, String name, boolean androidNs) {
        int ns = androidNs ? androidNs(d, false) : -1;
        for (int i = 0; i < c.attrs.size(); i++) {
            Attr a = c.attrs.get(i);
            if (ns >= 0 && a.ns != ns) continue;
            if (!name.equals(str(d, a.name))) continue;
            if (a.type == TYPE_STRING) return str(d, a.data);
            if (a.raw != NO_INDEX) return str(d, a.raw);
        }
        return null;
    }

    private static Long attrLong(Doc d, Chunk c, String name) {
        int ns = androidNs(d, false);
        for (int i = 0; i < c.attrs.size(); i++) {
            Attr a = c.attrs.get(i);
            if (ns >= 0 && a.ns != ns) continue;
            if (!name.equals(str(d, a.name))) continue;
            if (a.type == TYPE_INT_DEC || a.type == TYPE_INT_HEX) return Long.valueOf(a.data & 0xFFFFFFFFL);
            if (a.type == TYPE_STRING) {
                try { return Long.valueOf(Long.parseLong(str(d, a.data))); } catch (Throwable ignored) { return null; }
            }
        }
        return null;
    }

    private static Integer attrInt(Doc d, Chunk c, String name) {
        int ns = androidNs(d, false);
        for (int i = 0; i < c.attrs.size(); i++) {
            Attr a = c.attrs.get(i);
            if (ns >= 0 && a.ns != ns) continue;
            if (!name.equals(str(d, a.name))) continue;
            if (a.type == TYPE_INT_DEC || a.type == TYPE_INT_HEX) return Integer.valueOf(a.data);
            if (a.type == TYPE_STRING) return apiLevelFromCodename(str(d, a.data));
        }
        return null;
    }

    private static Boolean attrBool(Doc d, Chunk c, String name) {
        int ns = androidNs(d, false);
        for (int i = 0; i < c.attrs.size(); i++) {
            Attr a = c.attrs.get(i);
            if (ns >= 0 && a.ns != ns) continue;
            if (!name.equals(str(d, a.name))) continue;
            if (a.type == TYPE_INT_BOOLEAN || a.type == TYPE_INT_DEC) return Boolean.valueOf(a.data != 0);
            if (a.type == TYPE_STRING) return Boolean.valueOf("true".equalsIgnoreCase(str(d, a.data)));
        }
        return null;
    }

    /** minSdk 偶尔写成代号（"O"、"P" 等），映射回 API 级别。 */
    private static Integer apiLevelFromCodename(String s) {
        if (s == null) return null;
        try { return Integer.valueOf(Integer.parseInt(s.trim())); } catch (Throwable ignored) {}
        String[] codes = {"BASE", "BASE_1_1", "CUPCAKE", "DONUT", "ECLAIR", "ECLAIR_0_1",
                "ECLAIR_MR1", "FROYO", "GINGERBREAD", "GINGERBREAD_MR1", "HONEYCOMB",
                "HONEYCOMB_MR1", "HONEYCOMB_MR2", "ICE_CREAM_SANDWICH", "ICE_CREAM_SANDWICH_MR1",
                "JELLY_BEAN", "JELLY_BEAN_MR1", "JELLY_BEAN_MR2", "KITKAT", "KITKAT_WATCH",
                "LOLLIPOP", "LOLLIPOP_MR1", "M", "N", "N_MR1", "O", "O_MR1", "P", "Q", "R",
                "S", "S_V2", "TIRAMISU", "UPSIDE_DOWN_CAKE", "VANILLA_ICE_CREAM"};
        String u = s.trim().toUpperCase();
        for (int i = 0; i < codes.length; i++) if (codes[i].equals(u)) return Integer.valueOf(i + 1);
        return null;
    }

    private static int androidNs(Doc d, boolean create) {
        int idx = d.strings.indexOf(ANDROID_NS_URI);
        if (idx >= 0) return idx;
        if (!create) return -1;
        return intern(d, ANDROID_NS_URI);
    }

    private static int intern(Doc d, String s) {
        int i = d.strings.indexOf(s);
        if (i >= 0) return i;
        d.strings.add(s);
        return d.strings.size() - 1;
    }

    /** 新增属性名时同步补 RES_XML_RESOURCE_MAP，否则框架 obtainAttributes 匹配不上。 */
    private static int internAttr(Doc d, String name) {
        int idx = intern(d, name);
        while (d.resMap.size() <= idx) d.resMap.add(Integer.valueOf(0));
        Integer id = FW_ATTR.get(name);
        if (id != null && d.resMap.get(idx).intValue() == 0) d.resMap.set(idx, id);
        return idx;
    }

    private static String str(Doc d, int idx) {
        if (idx < 0 || idx >= d.strings.size()) return "";
        return d.strings.get(idx);
    }

    // ================= 文档模型 =================

    private static final class Attr {
        int ns, name, raw, type, data;
    }

    private static final class Chunk {
        int type;
        byte[] raw;
        int line, nsIdx, nameIdx;
        int idIndex, classIndex, styleIndex;
        List<Attr> attrs;
    }

    private static final class Doc {
        List<String> strings = new ArrayList<String>();
        List<Integer> resMap = new ArrayList<Integer>();
        List<Chunk> chunks = new ArrayList<Chunk>();
        int styleCount;
        byte[] styles = new byte[0];
        boolean utf8;
    }

    private static Doc parse(byte[] a) throws Exception {
        if (a.length < 8) throw new Exception("AXML 太短");
        if (u16(a, 0) != 0x0003) throw new Exception("不是有效的 AXML（magic 错误）");
        Doc d = new Doc();
        int pos = 8;
        boolean poolSeen = false;
        while (pos + 8 <= a.length) {
            int type = u16(a, pos);
            int hdrSize = u16(a, pos + 2);
            int size = u32(a, pos + 4);
            if (size <= 0 || pos + size > a.length) {
                throw new Exception("AXML 块越界: type=0x" + Integer.toHexString(type) + " size=" + size);
            }
            if (type == CHUNK_STRING_POOL) {
                parsePool(a, pos, d);
                poolSeen = true;
            } else if (type == CHUNK_RES_MAP) {
                int n = (size - hdrSize) / 4;
                for (int i = 0; i < n; i++) d.resMap.add(Integer.valueOf(u32(a, pos + hdrSize + i * 4)));
            } else if (type == CHUNK_START_EL) {
                d.chunks.add(parseStartElem(a, pos, size));
            } else if (type == CHUNK_END_EL) {
                Chunk c = new Chunk();
                c.type = type;
                c.line = u32(a, pos + 8);
                c.nsIdx = u32(a, pos + 16);
                c.nameIdx = u32(a, pos + 20);
                d.chunks.add(c);
            } else if (type == CHUNK_START_NS || type == CHUNK_END_NS) {
                Chunk c = new Chunk();
                c.type = type;
                c.line = u32(a, pos + 8);
                c.nameIdx = u32(a, pos + 16);   // prefix
                c.nsIdx = u32(a, pos + 20);     // uri
                d.chunks.add(c);
            } else {
                Chunk c = new Chunk();
                c.type = type;
                c.raw = Arrays.copyOfRange(a, pos, pos + size);
                d.chunks.add(c);
            }
            pos += size;
        }
        if (!poolSeen) throw new Exception("AXML 缺少字符串池");
        return d;
    }

    private static void parsePool(byte[] a, int off, Doc d) throws Exception {
        if (u16(a, off) != CHUNK_STRING_POOL) throw new Exception("AXML 字符串池块类型错误");
        int count = u32(a, off + 8);
        d.styleCount = u32(a, off + 12);
        int flags = u32(a, off + 16);
        int stringsStart = u32(a, off + 20);
        int stylesStart = u32(a, off + 24);
        int poolSize = u32(a, off + 4);
        d.utf8 = (flags & 0x100) != 0;
        if (count < 0 || count > 2000000) throw new Exception("字符串池数量异常: " + count);
        d.strings.clear();
        for (int i = 0; i < count; i++) {
            int o = off + stringsStart + u32(a, off + 28 + 4 * i);
            d.strings.add(d.utf8 ? readUtf8Str(a, o) : readUtf16Str(a, o));
        }
        if (d.styleCount > 0 && stylesStart > 0 && stylesStart < poolSize) {
            d.styles = Arrays.copyOfRange(a, off + stylesStart, off + poolSize);
        }
    }

    private static Chunk parseStartElem(byte[] a, int off, int size) throws Exception {
        Chunk c = new Chunk();
        c.type = CHUNK_START_EL;
        c.line = u32(a, off + 8);
        c.nsIdx = u32(a, off + 16);
        c.nameIdx = u32(a, off + 20);
        int attrStart = u16(a, off + 24);
        int attrSize = u16(a, off + 26);
        int attrCount = u16(a, off + 28);
        c.idIndex = u16(a, off + 30);
        c.classIndex = u16(a, off + 32);
        c.styleIndex = u16(a, off + 34);
        if (attrSize <= 0) attrSize = 20;
        if (attrStart <= 0) attrStart = 20;
        c.attrs = new ArrayList<Attr>();
        for (int i = 0; i < attrCount; i++) {
            int b = off + 16 + attrStart + i * attrSize;
            if (b + 20 > off + size) break;
            Attr at = new Attr();
            at.ns = u32(a, b);
            at.name = u32(a, b + 4);
            at.raw = u32(a, b + 8);
            at.type = a[b + 15] & 0xFF;
            at.data = u32(a, b + 16);
            c.attrs.add(at);
        }
        return c;
    }

    private static Chunk findElement(Doc d, String name) {
        for (int i = 0; i < d.chunks.size(); i++) {
            Chunk c = d.chunks.get(i);
            if (c.type == CHUNK_START_EL && name.equals(str(d, c.nameIdx))) return c;
        }
        return null;
    }

    /** 在指定元素之后插入一个空元素（start + end），返回其 start 块。 */
    private static Chunk insertElementAfter(Doc d, Chunk anchor, String name) {
        int at = d.chunks.indexOf(anchor);
        if (at < 0) at = d.chunks.size() - 1;
        int ns = androidNs(d, true);
        Chunk st = new Chunk();
        st.type = CHUNK_START_EL;
        st.line = 1;
        st.nsIdx = ns;
        st.nameIdx = intern(d, name);
        st.attrs = new ArrayList<Attr>();
        Chunk en = new Chunk();
        en.type = CHUNK_END_EL;
        en.line = 1;
        en.nsIdx = ns;
        en.nameIdx = st.nameIdx;
        d.chunks.add(at + 1, st);
        d.chunks.add(at + 2, en);
        return st;
    }

    // ================= 序列化 =================

    private static byte[] emit(Doc d) throws Exception {
        byte[] pool = encodePool(d.strings, d.utf8, d.styleCount, d.styles);
        byte[] resmap = encodeResMap(d.resMap, d.strings.size());
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (int i = 0; i < d.chunks.size(); i++) body.write(emitChunk(d.chunks.get(i)));
        byte[] bodyBytes = body.toByteArray();
        int total = 8 + pool.length + resmap.length + bodyBytes.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(total);
        write16(out, 0x0003);
        write16(out, 8);
        write32(out, total);
        out.write(pool, 0, pool.length);
        out.write(resmap, 0, resmap.length);
        out.write(bodyBytes, 0, bodyBytes.length);
        return out.toByteArray();
    }

    private static byte[] encodePool(List<String> strings, boolean utf8, int styleCount, byte[] styles) {
        int n = strings.size();
        ByteArrayOutputStream blob = new ByteArrayOutputStream();
        int[] offs = new int[n];
        for (int i = 0; i < n; i++) {
            offs[i] = blob.size();
            String s = strings.get(i);
            if (utf8) {
                byte[] b8 = encodeMutf8(s);
                writeVar8(blob, s.length());
                writeVar8(blob, b8.length);
                blob.write(b8, 0, b8.length);
                blob.write(0);
            } else {
                int len = s.length();
                if (len > 0x7FFF) {
                    write16(blob, 0x8000 | (len >>> 16));
                    write16(blob, len & 0xFFFF);
                } else {
                    write16(blob, len);
                }
                for (int j = 0; j < len; j++) write16(blob, s.charAt(j));
                write16(blob, 0);
            }
        }
        while (blob.size() % 4 != 0) blob.write(0);
        byte[] sb = blob.toByteArray();
        byte[] st = styles == null ? new byte[0] : styles;
        int stringsStart = 28 + 4 * n;
        int stylesStart = stringsStart + sb.length;
        int size = stylesStart + st.length;
        int padded = (size + 3) & ~3;
        ByteArrayOutputStream p = new ByteArrayOutputStream(padded);
        write16(p, CHUNK_STRING_POOL);
        write16(p, 28);
        write32(p, padded);
        write32(p, n);
        write32(p, styleCount);
        write32(p, utf8 ? 0x100 : 0x000);
        write32(p, stringsStart);
        write32(p, stylesStart);
        for (int i = 0; i < n; i++) write32(p, offs[i]);
        p.write(sb, 0, sb.length);
        if (st.length > 0) p.write(st, 0, st.length);
        while (p.size() < padded) p.write(0);
        return p.toByteArray();
    }

    private static byte[] encodeResMap(List<Integer> map, int stringCount) {
        int n = Math.max(map.size(), stringCount);
        ByteArrayOutputStream p = new ByteArrayOutputStream(8 + 4 * n);
        write16(p, CHUNK_RES_MAP);
        write16(p, 8);
        write32(p, 8 + 4 * n);
        for (int i = 0; i < n; i++) {
            write32(p, i < map.size() ? map.get(i).intValue() : 0);
        }
        return p.toByteArray();
    }

    private static byte[] emitChunk(Chunk c) throws Exception {
        if (c.raw != null) return c.raw;
        ByteArrayOutputStream o = new ByteArrayOutputStream(64);
        if (c.type == CHUNK_START_EL) {
            int n = c.attrs == null ? 0 : c.attrs.size();
            write16(o, CHUNK_START_EL);
            write16(o, 16);
            write32(o, 16 + 20 + 20 * n);
            write32(o, c.line);
            write32(o, NO_INDEX);
            write32(o, c.nsIdx);
            write32(o, c.nameIdx);
            write16(o, 20);
            write16(o, 20);
            write16(o, n);
            write16(o, c.idIndex);
            write16(o, c.classIndex);
            write16(o, c.styleIndex);
            for (int i = 0; i < n; i++) {
                Attr a = c.attrs.get(i);
                write32(o, a.ns);
                write32(o, a.name);
                write32(o, a.raw);
                write16(o, 8);
                o.write(0);
                o.write(a.type);
                write32(o, a.data);
            }
        } else if (c.type == CHUNK_END_EL) {
            write16(o, CHUNK_END_EL);
            write16(o, 16);
            write32(o, 24);
            write32(o, c.line);
            write32(o, NO_INDEX);
            write32(o, c.nsIdx);
            write32(o, c.nameIdx);
        } else {
            write16(o, c.type);
            write16(o, 16);
            write32(o, 24);
            write32(o, c.line);
            write32(o, NO_INDEX);
            write32(o, c.nameIdx);
            write32(o, c.nsIdx);
        }
        return o.toByteArray();
    }

    // ================= 字符串编解码 =================

    private static String readUtf8Str(byte[] a, int off) {
        int[] r1 = var8(a, off);
        int[] r2 = var8(a, r1[1]);
        int start = r2[1];
        int len = r2[0];
        if (start < 0 || start + len > a.length) return "";
        return decodeMutf8(Arrays.copyOfRange(a, start, start + len));
    }

    private static String readUtf16Str(byte[] a, int off) {
        int first = u16(a, off);
        int len, dataOff;
        if ((first & 0x8000) != 0) {
            len = ((first & 0x7FFF) << 16) | u16(a, off + 2);
            dataOff = off + 4;
        } else {
            len = first;
            dataOff = off + 2;
        }
        if (len < 0 || dataOff + 2 * len > a.length) {
            int end = dataOff;
            while (end + 1 < a.length && !(a[end] == 0 && a[end + 1] == 0)) end += 2;
            len = (end - dataOff) / 2;
        }
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append((char) u16(a, dataOff + 2 * i));
        return sb.toString();
    }

    private static int[] var8(byte[] a, int off) {
        int b0 = a[off] & 0xFF;
        if ((b0 & 0x80) != 0) return new int[]{((b0 & 0x7F) << 8) | (a[off + 1] & 0xFF), off + 2};
        return new int[]{b0, off + 1};
    }

    private static String decodeMutf8(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length);
        int i = 0;
        while (i < b.length) {
            int x = b[i] & 0xFF;
            char c;
            if ((x & 0x80) == 0) { c = (char) x; i += 1; }
            else if ((x & 0xE0) == 0xC0) {
                if (i + 1 >= b.length) break;
                c = (char) (((x & 0x1F) << 6) | (b[i + 1] & 0x3F)); i += 2;
            } else {
                if (i + 2 >= b.length) break;
                c = (char) (((x & 0x0F) << 12) | ((b[i + 1] & 0x3F) << 6) | (b[i + 2] & 0x3F)); i += 3;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    static byte[] encodeMutf8(String s) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 0) { o.write(0xC0); o.write(0x80); }
            else if (c < 0x80) o.write(c);
            else if (c < 0x800) { o.write(0xC0 | (c >> 6)); o.write(0x80 | (c & 0x3F)); }
            else { o.write(0xE0 | (c >> 12)); o.write(0x80 | ((c >> 6) & 0x3F)); o.write(0x80 | (c & 0x3F)); }
        }
        return o.toByteArray();
    }

    private static void writeVar8(ByteArrayOutputStream o, int n) {
        if (n <= 0x7F) o.write(n);
        else { o.write(0x80 | (n >> 8)); o.write(n & 0xFF); }
    }

    // ================= 基础工具 =================

    static int u16(byte[] a, int off) { return (a[off] & 0xFF) | ((a[off + 1] & 0xFF) << 8); }

    static int u32(byte[] a, int off) {
        return (a[off] & 0xFF) | ((a[off + 1] & 0xFF) << 8)
                | ((a[off + 2] & 0xFF) << 16) | ((a[off + 3] & 0xFF) << 24);
    }

    static void write16(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
    }

    static void write32(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
        o.write((v >> 16) & 0xFF);
        o.write((v >> 24) & 0xFF);
    }
}
