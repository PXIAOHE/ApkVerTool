# -*- coding: utf-8 -*-
"""执行日志三处修复：
   1) SAF 导出：insert 不支持时退回 createDocument（MIUI 安全访问 provider 会拒 insert）
   2) root 安装：日志压掉 Java 堆栈，失败时给明确结论
   3) 日志框自动滚到底（不然永远看到开头几行）
   4) 设置页选输出目录时先探一次可写，别等输出时报 Insert not supported
"""
import io, sys

AVT = '/workspace/avt/'

def edit(path, reps):
    s = io.open(path, encoding='utf-8').read()
    for old, new in reps:
        n = s.count(old)
        if n != 1:
            print('!! %s 匹配 %d 次: %r' % (path, n, old.strip().splitlines()[0][:70]))
            sys.exit(1)
        s = s.replace(old, new)
    io.open(path, 'w', encoding='utf-8').write(s)
    print('ok', path)


# ================= MainActivity =================
ma = AVT + 'src/com/seagull/avt/app/MainActivity.java'

# 1) SAF 导出：createDocument 优先，insert 兜底
SAF_old = """            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME, base);
            cv.put(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                    "application/octet-stream");
            Uri target = cr.insert(dir, cv);
            if (target == null) {
                log("SAF 导出失败：目标目录拒绝创建文件，去「设置 → 输出位置」重选一次");
                return;
            }
"""
SAF_new = """            // 两条创建路径都要试：不同 provider 支持的不一样。
            // MIUI「安全访问」这类 provider 对 insert 直接抛 "Insert not supported"，
            // 但 createDocument 是通的；反过来也有 provider 只认 insert。
            Uri target = null;
            String lastErr = null;
            try {
                target = android.provider.DocumentsContract.createDocument(
                        cr, dir, "application/octet-stream", base);
            } catch (Throwable t) {
                lastErr = t.getMessage();
            }
            if (target == null) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME, base);
                cv.put(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                        "application/octet-stream");
                try {
                    target = cr.insert(dir, cv);
                } catch (Throwable t) {
                    if (lastErr == null) lastErr = t.getMessage();
                }
            }
            if (target == null) {
                log("SAF 导出失败：" + lastErr + " —— 这个目录的 provider 不支持创建文件，"
                        + "去「设置 → 输出位置」换成「内部存储 / Download」这类普通目录");
                return;
            }
"""

# 2) root 安装：压掉堆栈 + 结论
RI_old = """                log("$ su -c " + cmd);
                String out = execSu(cmd, 120000);
                log(out.trim());
"""
RI_new = """                log("$ su -c " + cmd);
                String out = execSu(cmd, 120000);
                // pm install 失败会甩出几十行 Java 堆栈，日志页塞不下也没意义，
                // 只留首行 / Caused by / 退出码，堆栈行统一省略
                log(condense(out));
"""

RI2_old = """                } else if (low.contains("no_certificates") || low.contains("no signatures")
                        || low.contains("signatures") || low.contains("parse_failed")) {
                    log("✗ 被签名校验拒绝。请确认已装核心破解（CorePatch）并禁用了 APK 签名校验。");
                    toast("安装被签名校验拒绝：需要核心破解");
                } else {
                    toast("安装失败，详见日志");
                }
"""
RI2_new = """                } else if (low.contains("no_certificates") || low.contains("no signatures")
                        || low.contains("signature") || low.contains("parse_failed")
                        || low.contains("failed to parse") || low.contains("failed to load asset path")) {
                    log("✗ 系统拒绝了这个产物：内容改过 → 搬过来的原签名摘要必然失效，"
                            + "不重签就过不了系统的解析 / 签名校验。");
                    log("  想直接装：设置 → 签名 里打开「重签名」；"
                            + "或者给系统装核心破解类模块（CorePatch / 解锁签名校验）后再用 root 装。");
                    toast("安装被拒：需要重签名或核心破解");
                } else {
                    toast("安装失败，详见日志");
                }
"""

# 3) condense 工具方法 + 日志自动滚到底
LG_old = """    private void log(final String s) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (tvLog != null) tvLog.setText(tvLog.getText() + s + "\\n");
            }
        });
    }
"""
LG_new = """    private void log(final String s) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (tvLog == null) return;
                tvLog.setText(tvLog.getText() + s + "\\n");
                // 日志一直往下堆，不自动滚到底的话看到的永远是开头那几行
                tvLog.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            android.text.Layout l = tvLog.getLayout();
                            if (l == null) return;
                            int y = l.getLineTop(tvLog.getLineCount()) - tvLog.getHeight();
                            if (y > 0) tvLog.scrollTo(0, y);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        });
    }

    /** 把 pm / su 的输出压成可读几行：丢掉 "\\tat ..." 堆栈行，末尾标注省略了多少行。 */
    private static String condense(String s) {
        if (s == null) return "";
        String[] lines = s.split("\\n");
        StringBuilder sb = new StringBuilder();
        int skipped = 0;
        for (int i = 0; i < lines.length; i++) {
            String ln = lines[i].trim();
            if (ln.length() == 0) continue;
            if (ln.startsWith("at ") || ln.startsWith("... ") || ln.startsWith("Caused by:")) {
                if (ln.startsWith("Caused by:")) sb.append(ln).append('\\n');
                skipped++;
                continue;
            }
            sb.append(ln).append('\\n');
        }
        if (skipped > 0) sb.append("…（省略 ").append(skipped).append(" 行堆栈）\\n");
        return sb.toString().trim();
    }
"""

edit(ma, [(SAF_old, SAF_new), (RI_old, RI_new), (RI2_old, RI2_new), (LG_old, LG_new)])

# ================= SettingsActivity =================
st = AVT + 'src/com/seagull/avt/app/SettingsActivity.java'

P_old = """                getContentResolver().takePersistableUriPermission(uri, take);
                sp.edit().putString("output_uri", uri.toString()).apply();
                refreshOutputPath();
                toast("输出位置已设置");
"""
P_new = """                getContentResolver().takePersistableUriPermission(uri, take);
                // 有些目录能选中但根本写不进去（内部存储根 / 只读 provider），
                // 先探一次再保存，别等输出的时候才报 "Insert not supported"
                String why = probeWritable(uri);
                if (why != null) {
                    toast("这个目录不能写入（" + why + "），请换一个，比如 内部存储 / Download");
                    refreshOutputPath();
                    return;
                }
                sp.edit().putString("output_uri", uri.toString()).apply();
                refreshOutputPath();
                toast("输出位置已设置");
"""

PW_old = """    private void refreshOutputPath() {
"""
PW_new = """    /**
     * 探测 SAF 目录能不能建文件：能写返回 null，不能写返回原因。
     * 探测文件建完立刻删掉，不留垃圾。
     */
    private String probeWritable(Uri tree) {
        Uri created = null;
        try {
            android.content.ContentResolver cr = getContentResolver();
            String tdid = android.provider.DocumentsContract.getTreeDocumentId(tree);
            Uri dir = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, tdid);
            String name = ".avt_write_probe";
            try {
                created = android.provider.DocumentsContract.createDocument(
                        cr, dir, "application/octet-stream", name);
            } catch (Throwable ignored) {
            }
            if (created == null) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME, name);
                cv.put(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                        "application/octet-stream");
                created = cr.insert(dir, cv);
            }
            if (created == null) return "provider 不支持创建文件";
            return null;
        } catch (Throwable t) {
            String m = t.getMessage();
            return m == null || m.length() == 0 ? t.toString() : m;
        } finally {
            if (created != null) {
                try {
                    android.provider.DocumentsContract.deleteDocument(
                            getContentResolver(), created);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void refreshOutputPath() {
"""

edit(st, [(P_old, P_new), (PW_old, PW_new)])
print('done')
