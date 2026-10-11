# -*- coding: utf-8 -*-
"""不重签输出保留原签名（V1 条目 + V2/V3 签名块）；输出位置授权/覆盖加固。"""
import io, sys

AVT = '/workspace/avt/'


def edit(path, reps, count_only=None):
    s = io.open(path, encoding='utf-8').read()
    for old, new in reps:
        n = s.count(old)
        if n != 1:
            print('!! %s 匹配 %d 次: %r' % (path, n, old.strip().splitlines()[0][:70]))
            sys.exit(1)
        s = s.replace(old, new)
    io.open(path, 'w', encoding='utf-8').write(s)
    print('ok', path)


# ================= 1) ZipAlignWriter：签名块搬运 =================
zw = AVT + 'src/com/seagull/avt/core/ZipAlignWriter.java'

E_old = """        /** true = 保留原 V1 签名条目（MT 式不重签输出）。 */
        public boolean keepOldSignature = false;
"""
E_new = """        /** true = 保留原 V1 签名条目（MT 式不重签输出）。 */
        public boolean keepOldSignature = false;
        /** true = 原样搬运原 APK 的签名块（V2/V3 证书链），MT 式不重签输出用。 */
        public boolean keepSigningBlock = false;
"""

R_old = """        long cdStart = out.count;
        for (int i = 0; i < cds.size(); i++) writeCd(out, cds.get(i));
"""
R_new = """        // MT 式不重签：把原 APK 的签名块原样搬到中央目录之前。
        // 这样 V2/V3 的证书链（签名者 / 算法 / 指纹）还在，验签详情和 App 自身读 PackageInfo.signatures
        // 拿到的还是原签名；内容被改过，V2/V3 摘要必然对不上、签名实际无效，这是预期的 MT 式行为。
        if (edit.keepSigningBlock) {
            byte[] sigBlock = extractSigningBlock(src);
            if (sigBlock != null) {
                out.write(sigBlock);
                report.signingBlockKept = sigBlock.length;
            }
        }
        long cdStart = out.count;
        for (int i = 0; i < cds.size(); i++) writeCd(out, cds.get(i));
"""

F_old = """        /** 顺序扫描时是否撞到了 APK 签名块（说明签名块之前的文件条目已全部覆盖）。 */
        public boolean signingBlockSeen;
"""
F_new = """        /** 顺序扫描时是否撞到了 APK 签名块（说明签名块之前的文件条目已全部覆盖）。 */
        public boolean signingBlockSeen;
        /** 本次重打包搬运的原 APK 签名块字节数，0 = 没有（原本就没签名块）。 */
        public int signingBlockKept;
"""

D_old = """            if (signingBlockSeen) {
                sb.append('\\n').append("说明：文件条目已全部覆盖，其后是 APK 签名块与中央目录。");
            }
"""
D_new = """            if (signingBlockSeen) {
                sb.append('\\n').append("说明：文件条目已全部覆盖，其后是 APK 签名块与中央目录。");
            }
            if (signingBlockKept > 0) {
                sb.append('\\n').append("签名块：已保留原 APK 签名块（V2/V3 证书链，")
                  .append(signingBlockKept).append(" 字节，不重签）");
            }
"""

M_old = """    /**
     * 顺序扫描停止处是否为「文件区结束」：其后可能是 apksig 插入的 4KB 对齐零填充，
     * 再往后才是 APK 签名块，所以这里向后有界搜索签名块 magic。
     */
"""
M_new = """    /**
     * 从 APK 里取出完整的 APK Signing Block（含首尾 size 字段与 magic），没有则返回 null。
     * 结构：size(8) | ID-value pairs | size(8) | "APK Sig Block 42"(16)，整块紧贴中央目录之前。
     */
    public static byte[] extractSigningBlock(File apk) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(apk, "r");
            long len = raf.length();
            if (len < 64) return null;
            // 1) 从尾部找 EOCD（注释最长 65535）
            long scanFrom = Math.max(0, len - 65557);
            long eocd = -1;
            byte[] sig = new byte[4];
            for (long p = len - 22; p >= scanFrom; p--) {
                raf.seek(p);
                if (raf.read(sig, 0, 4) < 4) break;
                if (le32(sig, 0) == SIG_EOCD) { eocd = p; break; }
            }
            if (eocd < 0) return null;
            byte[] e = new byte[22];
            raf.seek(eocd);
            if (raf.read(e, 0, 22) < 22) return null;
            long cdOffset = le32u(e, 16);
            if (cdOffset == 0xFFFFFFFFL) {
                // Zip64：EOCD64 定位器（20 字节）紧跟 EOCD 之前
                if (eocd < 20) return null;
                byte[] loc = new byte[20];
                raf.seek(eocd - 20);
                if (raf.read(loc, 0, 20) < 20) return null;
                if (le32(loc, 0) != SIG_EOCD64_LOC) return null;
                long e64 = le64(loc, 8);
                byte[] b = new byte[56];
                raf.seek(e64);
                if (raf.read(b, 0, 56) < 56) return null;
                if (le32(b, 0) != SIG_EOCD64) return null;
                cdOffset = le64(b, 48);
            }
            if (cdOffset < 32 || cdOffset > len) return null;
            // 2) 中央目录前 16 字节应为 magic，前 24 字节处是块尾 size
            byte[] magic = new byte[16];
            raf.seek(cdOffset - 16);
            if (raf.read(magic, 0, 16) < 16) return null;
            if (!"APK Sig Block 42".equals(new String(magic, "US-ASCII"))) return null;
            byte[] tail = new byte[8];
            raf.seek(cdOffset - 24);
            if (raf.read(tail, 0, 8) < 8) return null;
            long a = le64(tail, 0);
            long start = cdOffset - 8 - a;
            if (start < 0 || start + 8 > cdOffset) return null;
            byte[] head = new byte[8];
            raf.seek(start);
            if (raf.read(head, 0, 8) < 8) return null;
            if (le64(head, 0) != a) return null;
            long size = cdOffset - start;
            if (size <= 0 || size > (8L << 20)) return null;
            byte[] out = new byte[(int) size];
            raf.seek(start);
            raf.readFully(out);
            return out;
        } catch (Throwable t) {
            return null;
        } finally {
            try { if (raf != null) raf.close(); } catch (Throwable ignored) {}
        }
    }

    /**
     * 顺序扫描停止处是否为「文件区结束」：其后可能是 apksig 插入的 4KB 对齐零填充，
     * 再往后才是 APK 签名块，所以这里向后有界搜索签名块 magic。
     */
"""

edit(zw, [(E_old, E_new), (R_old, R_new), (F_old, F_new), (D_old, D_new), (M_old, M_new)])

# ================= 2) ApkPatcher：不重签就保留原签名 =================
ap = AVT + 'src/com/seagull/avt/core/ApkPatcher.java'
P_old = """        edit.keepOldSignature = job.edit.keepOldSignature;
        edit.preserveExtra = job.edit.preserveExtra;
        edit.level = job.edit.level;
"""
P_new = """        // 不重签（MT 式输出）= 原样保留原签名：V1 的 META-INF 条目 + V2/V3 签名块一起搬运。
        // 之前只搬了 V1 条目、签名块被丢掉，但 App 里从来没把 keepOldSignature 打开，
        // 结果「不签名输出」的产物是一条签名都没留下。
        edit.keepOldSignature = job.edit.keepOldSignature || !job.resign;
        edit.keepSigningBlock = !job.resign;
        edit.preserveExtra = job.edit.preserveExtra;
        edit.level = job.edit.level;
"""

S_old = """                copy(stage, out);
                o.say("未重签输出（原签名数据保留，不可直接安装）");
"""
S_new = """                copy(stage, out);
                o.say("未重签输出（已保留原签名：V1 条目 + V2/V3 签名块，摘要已失效不可直接安装）");
"""
edit(ap, [(P_old, P_new), (S_old, S_new)])

# ================= 3) AvtCli：--keep-signature 也保留签名块 =================
cli = AVT + 'src/com/seagull/avt/cli/AvtCli.java'
C_old = """            } else if ("--keep-signature".equals(k)) job.edit.keepOldSignature = true;
"""
C_new = """            } else if ("--keep-signature".equals(k)) {
                job.edit.keepOldSignature = true;
                job.edit.keepSigningBlock = true;
            }
"""
edit(cli, [(C_old, C_new)])

# ================= 4) SettingsActivity：输出位置授权持久化 =================
st = AVT + 'src/com/seagull/avt/app/SettingsActivity.java'
K_old = """    private void pickOutputDir() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(i, REQ_OUTPUT_DIR);
    }
"""
K_new = """    private void pickOutputDir() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        // 必须带 PERSISTABLE，否则拿不到长期授权：重启 App 后授权失效，
        // 导出会静默失败，表现就是「输出位置设了没用」
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i, REQ_OUTPUT_DIR);
    }
"""

A_old = """                Uri uri = data.getData();
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
"""
A_new = """                Uri uri = data.getData();
                int take = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if (take == 0) {
                    take = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                }
                getContentResolver().takePersistableUriPermission(uri, take);
"""
edit(st, [(K_old, K_new), (A_old, A_new)])

# ================= 5) MainActivity：SAF 导出重名覆盖 + 失败提示 =================
ma = AVT + 'src/com/seagull/avt/app/MainActivity.java'
X_old = """    private void exportToSaf(File apk, String base) {
        String oUri = getSharedPreferences("settings", MODE_PRIVATE).getString("output_uri", "");
        if (oUri == null || oUri.isEmpty()) return;
        try {
            Uri tree = Uri.parse(oUri);
            String tdid = android.provider.DocumentsContract.getTreeDocumentId(tree);
            Uri dir = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, tdid);
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME, base);
            cv.put(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                    "application/octet-stream");
            Uri target = getContentResolver().insert(dir, cv);
            if (target == null) {
                log("SAF 导出失败：无法在目标目录创建文件");
                return;
            }
            copy(new FileInputStream(apk), getContentResolver().openOutputStream(target));
            log("已导出到指定目录: " + base);
        } catch (Throwable t) {
            log("SAF 导出失败: " + t.getMessage());
        }
    }
"""
X_new = """    private void exportToSaf(File apk, String base) {
        String oUri = getSharedPreferences("settings", MODE_PRIVATE).getString("output_uri", "");
        if (oUri == null || oUri.isEmpty()) return;
        try {
            Uri tree = Uri.parse(oUri);
            String tdid = android.provider.DocumentsContract.getTreeDocumentId(tree);
            Uri dir = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, tdid);
            android.content.ContentResolver cr = getContentResolver();
            // 同名文件先删掉：SAF 的 insert 不覆盖同名文档，之前重名直接报错，
            // 第二次输出同一个文件名就会失败，看起来就是「输出位置出问题了」
            Uri old = findChildByName(cr, tree, tdid, base);
            if (old != null) {
                try {
                    android.provider.DocumentsContract.deleteDocument(cr, old);
                } catch (Throwable t) {
                    log("同名旧文件删不掉（将另存）: " + t.getMessage());
                }
            }
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME, base);
            cv.put(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                    "application/octet-stream");
            Uri target = cr.insert(dir, cv);
            if (target == null) {
                log("SAF 导出失败：目标目录拒绝创建文件，去「设置 → 输出位置」重选一次");
                return;
            }
            copy(new FileInputStream(apk), cr.openOutputStream(target));
            log("已导出到指定目录: " + base);
        } catch (Throwable t) {
            log("SAF 导出失败: " + t.getMessage() + "，去「设置 → 输出位置」重选一次即可恢复");
        }
    }

    /** 在 SAF 目标目录里按显示名找同名文档。 */
    private static Uri findChildByName(android.content.ContentResolver cr, Uri tree,
                                       String treeDocId, String name) {
        Uri children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeDocId);
        android.database.Cursor c = null;
        try {
            c = cr.query(children, new String[]{
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
            }, null, null, null);
            while (c != null && c.moveToNext()) {
                if (name.equals(c.getString(1))) {
                    return android.provider.DocumentsContract
                            .buildDocumentUriUsingTree(tree, c.getString(0));
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        return null;
    }
"""
edit(ma, [(X_old, X_new)])
print('done')
