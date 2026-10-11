package com.seagull.avt.app;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.widget.Toast;

import com.seagull.avt.core.ApkInfo;
import com.seagull.avt.core.ApkPatcher;
import com.seagull.avt.core.AxmlPatch;
import com.seagull.avt.core.SignUtil;
import com.seagull.avt.core.SplitBundle;
import com.seagull.avt.core.ZipAlignWriter;
import com.seagull.avt.ui.AvtUi;
import com.seagull.avt.ui.MainHost;
import com.seagull.avt.ui.MainUiState;
import com.seagull.avt.ui.ReplaceEntry;
import com.seagull.avt.ui.UiStateKt;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主页宿主。
 *
 * <p>重构后这里只剩<b>业务</b>：队列、待替换表、折叠判定、改写流程、体检、安装、分享。
 * 所有视图代码（手写 LinearLayout、自研 SwipePager、三套底栏）已整体搬到 Compose，
 * 交互入口改成实现 {@link MainHost}，界面文字改成读写 {@link MainUiState}。
 * 业务方法（pick / onActivityResult / startRun / doRun / processOne / exportToSaf /
 * install / share / execSu … ）与重构前逐行等价。
 */
public class MainActivity extends AvtBaseActivity implements MainHost {

    private static final int REQ_PICK = 11;
    private static final int REQ_PUT_FILE = 12;

    /** 底栏页序：改写 0 / 条目 1 / 体检 2 / 日志 3。 */
    private static final int PAGE_LOG = 3;

    /** 所有界面状态写回都在主线程做，避免 Compose 快照被后台线程写。 */
    private final Handler main = new Handler(Looper.getMainLooper());

    private final MainUiState ui = new MainUiState();

    // ── 选中队列（业务真源，不放 UI 状态里） ──
    private final List<File> queue = new ArrayList<>();
    private final List<String> queueNames = new ArrayList<>();
    private final Map<String, byte[]> putMap = new LinkedHashMap<>();

    private String pendingEntry;

    // ── APK ──
    private File lastOutput;
    private Uri lastSafUri;
    private boolean lastOutputSigned = true;

    private Boolean rootState;
    private boolean busy;

    // ================================================================= 生命周期

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideActionBar();
        if (applyForceTheme()) return;
        initTheme();
        setupChrome();
        AvtUi.attachMain(this, ui, this);
    }

    // ================================================================= MainHost

    @Override
    public void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override
    public void pickFiles() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("*/*");
        it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        it.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.android.package-archive",
                "application/zip",
                "application/octet-stream",
                "application/x-zip-compressed"});
        startActivityForResult(it, REQ_PICK);
    }

    @Override
    public void addReplaceEntry(String name) {
        if (name == null || name.trim().isEmpty()) {
            toast("条目名不能为空");
            return;
        }
        pendingEntry = name.trim();
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("*/*");
        startActivityForResult(it, REQ_PUT_FILE);
    }

    @Override
    public void clearReplaceEntries() {
        putMap.clear();
        refreshEntries();
        log("已清空替换列表");
    }

    @Override
    public void clearLogs() {
        main.post(ui::clearLogs);
    }

    @Override
    public void startRun() {
        run();
    }

    @Override
    public void install() {
        installOutput();
    }

    @Override
    public void confirmRootInstall() {
        doRootInstall();
    }

    @Override
    public void share() {
        shareOutput();
    }

    @Override
    public void showInfo() {
        inspectInfo();
    }

    @Override
    public void showAlign() {
        inspectAlign();
    }

    @Override
    public void showVerify() {
        inspectVerify();
    }

    @Override
    public void showEntries() {
        inspectEntries();
    }

    // ================================================================= 选文件

    @SuppressLint("SetTextI18n")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_PICK) {
            try {
                queue.clear();
                queueNames.clear();

                List<Uri> uris = new ArrayList<>();
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    for (int i = 0; i < n; i++) uris.add(data.getClipData().getItemAt(i).getUri());
                } else if (data.getData() != null) {
                    uris.add(data.getData());
                }

                ContentResolver cr = getContentResolver();
                for (int i = 0; i < uris.size(); i++) {
                    Uri u = uris.get(i);
                    String name = displayName(u);
                    File dst = new File(getCacheDir(), "in_" + i + suffix(name));
                    copy(cr.openInputStream(u), new FileOutputStream(dst));
                    queue.add(dst);
                    queueNames.add(name);
                }

                StringBuilder sb = new StringBuilder();
                sb.append("已选择 ").append(queue.size()).append(" 个文件");
                for (int i = 0; i < queueNames.size() && i < 6; i++) sb.append('\n').append("· ").append(queueNames.get(i));
                if (queueNames.size() > 6) sb.append('\n').append("· …还有 ").append(queueNames.size() - 6).append(" 个");

                if (!queue.isEmpty()) {
                    try {
                        if (SplitBundle.isBundle(queue.get(0))) {
                            sb.append('\n').append("类型: 多包容器");
                        } else {
                            AxmlPatch.Info info = AxmlPatch.read(
                                    ZipAlignWriter.extractEntry(queue.get(0), "AndroidManifest.xml"));
                            sb.append('\n').append("类型: 单 APK");
                            sb.append('\n').append("包名 ").append(info.packageName);
                            sb.append('\n').append("版本 ").append(info.versionName).append(" (").append(info.versionCode).append(")");

                            // 回填输入框：等价于原来的 etName/etCode/etMinSdk/etTargetSdk/etPackage.setText(...)
                            final String vn = info.versionName == null ? "" : info.versionName;
                            final String vc = info.versionCode == null ? "" : String.valueOf(info.versionCode);
                            final String ms = info.minSdk == null ? "" : String.valueOf(info.minSdk);
                            final String ts = info.targetSdk == null ? "" : String.valueOf(info.targetSdk);
                            final String pkg = info.packageName == null ? "" : info.packageName;
                            main.post(() -> {
                                ui.setVersionName(vn);
                                ui.setVersionCode(vc);
                                ui.setMinSdk(ms);
                                ui.setTargetSdk(ts);
                                ui.setPackageName(pkg);
                            });
                        }
                    } catch (Throwable t) {
                        sb.append('\n').append("类型识别失败: ").append(t.getMessage());
                    }
                }

                final String text = sb.toString();
                main.post(() -> {
                    ui.setSourceText(text);
                    ui.setInfoText(text);
                });
                log("已读入 " + queue.size() + " 个文件");
                main.post(this::refreshBarBadges);
            } catch (Throwable t) {
                log("读取失败: " + t);
            }
        } else if (requestCode == REQ_PUT_FILE) {
            try {
                if (pendingEntry == null) return;
                byte[] bytes = readAll(getContentResolver().openInputStream(data.getData()));
                putMap.put(pendingEntry, bytes);
                log("待替换条目: " + pendingEntry + "  (" + bytes.length + " 字节)");
                pendingEntry = null;
                refreshEntries();
            } catch (Throwable t) {
                log("读取替换文件失败: " + t);
            }
        }
    }

    private static String suffix(String name) {
        String low = name.toLowerCase();
        if (low.endsWith(".xapk")) return ".xapk";
        if (low.endsWith(".apks")) return ".apks";
        return ".apk";
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null) {
                int idx = c.getColumnIndex("_display_name");
                if (idx >= 0 && c.moveToFirst()) {
                    String n = c.getString(idx);
                    if (n != null && n.length() > 0) return n;
                }
            }
        } catch (Throwable ignored) {
            // 落到下面的兜底
        }
        String seg = uri.getLastPathSegment();
        return seg == null ? "unknown.apk" : seg;
    }

    // ================================================================= 状态镜像

    private void refreshBarBadges() {
        ui.setFileCount(queue.size());
        ui.setEntryCount(putMap.size());
    }

    private void refreshEntries() {
        List<ReplaceEntry> items = new ArrayList<>(putMap.size());
        for (Map.Entry<String, byte[]> e : putMap.entrySet()) {
            items.add(new ReplaceEntry(e.getKey(), e.getValue().length));
        }
        main.post(() -> {
            ui.setReplaceEntries(items);
            refreshBarBadges();
        });
    }

    private File first() {
        return queue.isEmpty() ? null : queue.get(0);
    }

    private boolean isFoldOpen(String key) {
        Boolean b = ui.getFoldOpen().get(key);
        return b != null && b;
    }

    // ================================================================= 体检

    private void inspectInfo() {
        final File f = first();
        if (f == null) {
            toast("先选择文件");
            return;
        }
        section("体检报告");
        new Thread(() -> {
            try {
                if (SplitBundle.isBundle(f)) {
                    log("这是多包容器，体检请选择内部的单个 APK。");
                    return;
                }
                log(ApkInfo.analyze(f).describe());
            } catch (Throwable t) {
                log("体检失败: " + t);
            }
        }, "avt-inspect").start();
    }

    private void inspectAlign() {
        final File f = first();
        if (f == null) {
            toast("先选择文件");
            return;
        }
        new Thread(() -> {
            try {
                if (SplitBundle.isBundle(f)) {
                    log("多包容器不适用对齐体检，请选内部 APK。");
                    return;
                }
                ZipAlignWriter.Report report = ZipAlignWriter.alignReport(f);
                section("对齐体检");
                log(report.describe());
                int shown = 0;
                for (int i = 0; i < report.entries.size() && shown < 40; i++, shown++) {
                    log(report.entries.get(i).describe());
                }
                if (report.entries.size() > shown) {
                    log("…还有 " + (report.entries.size() - shown) + " 个条目");
                }
            } catch (Throwable t) {
                log("对齐体检失败: " + t);
            }
        }, "avt-align").start();
    }

    private void inspectVerify() {
        final File f = first();
        if (f == null) {
            toast("先选择文件");
            return;
        }
        new Thread(() -> {
            try {
                if (SplitBundle.isBundle(f)) {
                    log("多包容器不适用验签，请选内部 APK。");
                    return;
                }
                byte[] axml = ZipAlignWriter.extractEntry(f, "AndroidManifest.xml");
                section("验签详情");
                log(SignUtil.verifyDetail(f, AxmlPatch.readMinSdk(axml)).describe());
            } catch (Throwable t) {
                log("验签失败: " + t);
            }
        }, "avt-verify").start();
    }

    private void inspectEntries() {
        final File f = first();
        if (f == null) {
            toast("先选择文件");
            return;
        }
        new Thread(() -> {
            try {
                if (SplitBundle.isBundle(f)) {
                    section("容器条目");
                    log(String.join("\n", ZipAlignWriter.listNames(f)));
                    return;
                }
                List<String> names = ZipAlignWriter.listNames(f);
                section("条目列表（" + names.size() + " 条）");
                for (int i = 0; i < names.size() && i < 200; i++) log(names.get(i));
                if (names.size() > 200) log("…还有 " + (names.size() - 200) + " 个条目");
            } catch (Throwable t) {
                log("列条目失败: " + t);
            }
        }, "avt-entries").start();
    }

    // ================================================================= 改写主流程

    private void run() {
        if (busy) {
            toast("任务进行中");
            return;
        }
        if (queue.isEmpty()) {
            toast("先选择文件");
            return;
        }

        ApkPatcher.Job job = new ApkPatcher.Job();

        boolean fVer = isFoldOpen("ver");
        boolean fSdk = isFoldOpen("sdk");
        boolean fId = isFoldOpen("id");

        String sName = fVer ? ui.getVersionName().trim() : "";
        String sCode = fVer ? ui.getVersionCode().trim() : "";
        String sCodeMajor = fVer ? ui.getVersionCodeMajor().trim() : "";
        String sMin = fSdk ? ui.getMinSdk().trim() : "";
        String sTarget = fSdk ? ui.getTargetSdk().trim() : "";
        String sLabel = fId ? ui.getAppLabel().trim() : "";
        String sPkg = fId ? ui.getPackageName().trim() : "";

        try {
            if (!sName.isEmpty()) job.manifest.versionName = sName;
            if (!sCode.isEmpty()) job.manifest.versionCode = Long.parseLong(sCode);
            if (!sCodeMajor.isEmpty()) job.manifest.versionCodeMajor = Long.parseLong(sCodeMajor);
            if (!sMin.isEmpty()) job.manifest.minSdk = Integer.parseInt(sMin);
            if (!sTarget.isEmpty()) job.manifest.targetSdk = Integer.parseInt(sTarget);
            if (!sLabel.isEmpty()) job.manifest.label = sLabel;
            if (!sPkg.isEmpty()) job.newPackage = sPkg;
        } catch (Throwable t) {
            toast("数字格式不对: " + t.getMessage());
            return;
        }

        for (String line : ui.getDropText().split("\n")) {
            String s = line.trim();
            if (!s.isEmpty()) job.edit.delete.add(s);
        }
        job.edit.replace.putAll(putMap);

        if (job.manifest.isEmpty() && job.newPackage == null
                && job.edit.delete.isEmpty() && job.edit.replace.isEmpty()) {
            toast("没有任何改动");
            return;
        }

        SharedPreferences sp = getSharedPreferences("settings", MODE_PRIVATE);
        job.resign = sp.getBoolean("resign", true);
        job.opt.v1 = sp.getBoolean("scheme_v1", true);
        job.opt.v2 = sp.getBoolean("scheme_v2", true);
        job.opt.v3 = sp.getBoolean("scheme_v3", true);
        job.opt.v4 = sp.getBoolean("scheme_v4", false);

        String keyLabel = "内置密钥";
        if (job.resign) {
            try {
                if (sp.getBoolean("use_custom", false)) {
                    File custom = new File(getFilesDir(), "custom_key.p12");
                    if (custom.exists()) {
                        job.p12 = readAll(new FileInputStream(custom));
                        job.pass = sp.getString("key_pass", "").toCharArray();
                        keyLabel = "自定义密钥";
                    } else {
                        job.p12 = readAll(getAssets().open("signkey.p12"));
                        keyLabel = "内置密钥（自定义密钥缺失，已回退）";
                    }
                } else {
                    job.p12 = readAll(getAssets().open("signkey.p12"));
                }
            } catch (Throwable t) {
                toast("读取密钥失败: " + t);
                return;
            }
        }

        final String keyLabelFinal = keyLabel;
        busy = true;
        main.post(() -> {
            ui.clearLogs();
            ui.setBusy(true);
            ui.setCanInstall(false);
            ui.setCanShare(false);
            ui.setPageRequest(PAGE_LOG);
        });
        lastSafUri = null;

        new Thread(() -> {
            try {
                doRun(job, keyLabelFinal);
            } finally {
                busy = false;
                main.post(() -> ui.setBusy(false));
            }
        }, "avt-run").start();
    }

    private void doRun(ApkPatcher.Job job, String keyLabel) {
        log("签名: " + (job.resign ? "开 " + job.opt.describe() + "  密钥: " + keyLabel : "关（MT 式不重签）"));

        int ok = 0;
        int fail = 0;
        for (int i = 0; i < queue.size(); i++) {
            File f = queue.get(i);
            String name = queueNames.size() > i ? queueNames.get(i) : f.getName();
            section("[" + (i + 1) + "/" + queue.size() + "] " + name);
            try {
                if (processOne(f, name, job)) ok++;
                else fail++;
            } catch (Throwable t) {
                fail++;
                log("失败: " + t);
            }
        }

        section("完成：成功 " + ok + " 个，失败 " + fail + " 个");
        snack("完成 " + ok + " 个" + (fail > 0 ? "，失败 " + fail + " 个" : ""));

        final boolean hasOut = lastOutput != null || lastSafUri != null;
        main.post(() -> {
            ui.setCanInstall(hasOut);
            ui.setCanShare(hasOut);
        });
    }

    private boolean processOne(File src, String displayName, ApkPatcher.Job job) throws Exception {
        String base = displayName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);

        String low = displayName.toLowerCase();
        String ext = low.endsWith(".xapk") ? ".xapk" : (low.endsWith(".apks") ? ".apks" : ".apk");
        String outName = "patched_" + sanitize(base)
                + (job.manifest.versionName != null ? "_" + sanitize(job.manifest.versionName) : "")
                + (job.manifest.versionCode != null ? "_" + job.manifest.versionCode : "")
                + ext;
        File out = new File(outDir(), outName);

        if (SplitBundle.isBundle(src)) {
            log("多包容器模式");
            SplitBundle.Report rep = SplitBundle.patch(src, out, job);
            log(rep.describe());
            if (rep.failed > 0) return false;
        } else {
            ApkPatcher.Outcome outcome = ApkPatcher.patch(src, out, job);
            log(outcome.logText());
            if (job.resign && outcome.verify != null && !outcome.verify.verified) return false;
        }

        log("输出: " + out.getAbsolutePath() + "  (" + out.length() + " 字节)");

        Uri saf = exportToSaf(out, outName);
        lastOutput = out;
        lastSafUri = saf;
        lastOutputSigned = job.resign;

        // 导出到指定目录成功 → 私有目录的中转副本删掉（APK只留一份）
        if (saf != null && out.delete()) {
            lastOutput = null;
            log("中转副本已清理（APK只留在指定目录里）");
        }
        refreshInstallHint();
        return true;
    }

    private File outDir() {
        File d = getExternalFilesDir("out");
        if (d == null) d = new File(getFilesDir(), "out");
        d.mkdirs();
        return d;
    }

    private Uri exportToSaf(File src, String name) {
        String uriStr = getSharedPreferences("settings", MODE_PRIVATE).getString("output_uri", "");
        if (uriStr == null || uriStr.isEmpty()) return null;

        try {
            Uri tree = Uri.parse(uriStr);
            String docId = DocumentsContract.getTreeDocumentId(tree);
            Uri dir = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
            ContentResolver cr = getContentResolver();

            // 同名 SAF 文档先删再写（SAF insert 不覆盖同名，部分 ROM 也不认 createDocument）
            Uri old = findChildByName(cr, tree, docId, name);
            if (old != null) {
                try {
                    DocumentsContract.deleteDocument(cr, old);
                } catch (Throwable t) {
                    log("同名旧文件删不掉（将另存）: " + t.getMessage());
                }
            }

            Uri created = null;
            String err = null;
            try {
                created = DocumentsContract.createDocument(cr, dir, "application/octet-stream", name);
            } catch (Throwable t) {
                err = t.getMessage();
            }
            if (created == null) {
                ContentValues cv = new ContentValues();
                cv.put("_display_name", name);
                cv.put("mime_type", "application/octet-stream");
                try {
                    created = cr.insert(dir, cv);
                } catch (Throwable t) {
                    if (err == null) err = t.getMessage();
                }
            }
            if (created == null) {
                log("SAF 导出失败：" + err + " —— 这个目录的 provider 不支持创建文件，去「设置 → 输出位置」换成「内部存储 / Download」这类普通目录");
                return null;
            }

            copy(new FileInputStream(src), cr.openOutputStream(created));
            log("已导出到指定目录: " + name);
            return created;
        } catch (Throwable t) {
            log("SAF 导出失败: " + t.getMessage() + "，去「设置 → 输出位置」重选一次即可恢复");
            return null;
        }
    }

    private static Uri findChildByName(ContentResolver cr, Uri tree, String parentDocId, String name) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId);
        try (Cursor c = cr.query(children, new String[]{"document_id", "_display_name"}, null, null, null)) {
            while (c != null && c.moveToNext()) {
                if (c.getString(1) == null || !c.getString(1).equals(name)) continue;
                return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0));
            }
        } catch (Throwable ignored) {
            // 查不到就当作没有同名文件
        }
        return null;
    }

    private static String sanitize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' ? c : '_');
        }
        return sb.toString();
    }

    // ================================================================= 安装 / 分享

    private void refreshInstallHint() {
        final String hint;
        if (lastOutput == null) hint = UiStateKt.HINT_INSTALL_IDLE;
        else if (lastOutputSigned) hint = UiStateKt.HINT_INSTALL_SIGNED;
        else hint = UiStateKt.HINT_INSTALL_UNSIGNED;
        main.post(() -> ui.setInstallHint(hint));
    }

    private void installOutput() {
        if (lastOutput == null && lastSafUri == null) {
            toast("还没有输出文件");
            return;
        }
        if (lastOutputSigned) {
            log("安装：APK已签名 → 交给系统安装器");
            systemInstall();
        } else {
            log("安装：APK未重签 → 只能 root 静默安装（需要核心破解）");
            askRootInstall();
        }
    }

    private void shareOutput() {
        if (lastOutput == null && lastSafUri == null) {
            toast("还没有输出文件");
            return;
        }
        try {
            Uri uri = lastOutput != null ? publishForExternal(lastOutput) : lastSafUri;
            if (uri == null) {
                toast("APK不在本地，请到输出目录手动分享");
                return;
            }
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("application/vnd.android.package-archive");
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(it, "分享 APK");
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(chooser);
        } catch (Throwable t) {
            log("分享失败: " + t);
        }
    }

    private Uri publishForExternal(File src) throws Exception {
        File dst = new File(getCacheDir(), "share_" + sanitize(src.getName()));
        copy(new FileInputStream(src), new FileOutputStream(dst));
        return Uri.parse("content://com.seagull.apkvertool.files/" + dst.getName());
    }

    private void systemInstall() {
        try {
            Uri uri = lastOutput != null ? publishForExternal(lastOutput) : lastSafUri;
            if (uri == null) {
                toast("APK不在本地，请到输出目录手动安装");
                return;
            }
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(uri, "application/vnd.android.package-archive");
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
        } catch (Throwable t) {
            log("安装失败: " + t);
            toast("安装失败，可到设置页确认系统安装权限");
        }
    }

    private File localCopyForRoot() {
        if (lastOutput != null && lastOutput.exists()) return lastOutput;
        if (lastSafUri == null) return null;
        try {
            File f = new File(getCacheDir(), "install_" + System.currentTimeMillis() + ".apk");
            copy(getContentResolver().openInputStream(lastSafUri), new FileOutputStream(f));
            return f;
        } catch (Throwable t) {
            log("读取指定目录里的APK失败: " + t.getMessage());
            return null;
        }
    }

    private void askRootInstall() {
        ui.setRootPrompt(true);
    }

    private void doRootInstall() {
        final File f = localCopyForRoot();
        if (f == null) {
            log("失败：拿不到APK文件（APK只在指定目录里），请到输出目录手动安装");
            snack("拿不到APK文件");
            return;
        }
        log("准备 root 安装: " + f.getName() + (lastOutput == null ? "（从指定目录取回）" : ""));
        final boolean isSelf = getPackageName().equals(apkPackageName(f));
        if (isSelf) log("注意：APK就是本应用自身，装完系统会重启本应用");

        new Thread(() -> {
            if (!hasRoot()) {
                log("失败：未拿到 root 权限（su 不存在或被拒绝）");
                snack("未拿到 root 权限");
                return;
            }
            String cmd = "pm install -r -d --user 0 " + shQuote(f.getAbsolutePath());
            log("$ su -c " + cmd);
            String out = execSu(cmd, 120000);
            log(condense(out));

            String low = out.toLowerCase();
            boolean ok = low.contains("success")
                    || (out.contains("(exit=0)") && !low.contains("failure") && !low.contains("exception"));

            if (ok) {
                log("成功：root 安装成功");
                snack("root 安装成功");
                alert("安装成功", "已通过 root 静默安装完成。"
                        + (isSelf ? "\n\nAPK就是本应用自身，系统会重启本应用，属正常现象。" : ""));
            } else if (low.contains("no_certificates") || low.contains("no signatures")
                    || low.contains("signature") || low.contains("parse_failed")
                    || low.contains("failed to parse") || low.contains("failed to load asset path")) {
                log("失败：系统拒绝了这个APK：内容改过 → 搬过来的原签名摘要必然失效，不重签就过不了系统的解析 / 签名校验。");
                log("  想直接装：设置 → 签名 里打开「重签名」；或者给系统装核心破解类模块（CorePatch / 解锁签名校验）后再用 root 装。");
                snack("安装被拒：需要重签名或核心破解");
                alert("安装被系统拒绝",
                        "内容改过的包必须重新签名；\n或者给系统装核心破解类模块（禁用 APK 签名校验）后再用 root 安装。");
            } else {
                snack("安装失败，详见日志");
                alert("安装失败", "系统返回：\n\n" + condense(out));
            }
        }, "avt-root-install").start();
    }

    private boolean hasRoot() {
        if (rootState != null) return rootState;
        String out = execSu("id", 15000);
        boolean ok = out != null && out.contains("uid=0");
        rootState = ok;
        log(ok ? "root 可用（su 已授权）" : "root 不可用（su 不存在或未授权）");
        return ok;
    }

    /** 跑 {@code su -c <cmd>}，把 stdout / stderr 合并读回来，超时即 destroy。 */
    private String execSu(String cmd, int timeoutMs) {
        Process proc = null;
        try {
            proc = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            final Process p = proc;
            final ByteArrayOutputStream buf = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try {
                    drain(p.getInputStream(), buf);
                } catch (Throwable ignored) {
                }
                try {
                    drain(p.getErrorStream(), buf);
                } catch (Throwable ignored) {
                }
            }, "avt-su-reader");
            reader.start();

            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                try {
                    int code = proc.exitValue();
                    try {
                        reader.join(2000);
                    } catch (Throwable ignored) {
                    }
                    String out = new String(buf.toByteArray(), "UTF-8") + "\n(exit=" + code + ")";
                    if (proc != null) {
                        try {
                            proc.destroy();
                        } catch (Throwable ignored) {
                        }
                    }
                    return out;
                } catch (IllegalThreadStateException stillRunning) {
                    try {
                        Thread.sleep(120);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            String msg = "超时（" + timeoutMs + " ms）";
            if (proc != null) {
                try {
                    proc.destroy();
                } catch (Throwable ignored) {
                }
            }
            return msg;
        } catch (Throwable t) {
            String msg = "执行失败: " + t;
            if (proc != null) {
                try {
                    proc.destroy();
                } catch (Throwable ignored) {
                }
            }
            return msg;
        }
    }

    private static void drain(InputStream in, ByteArrayOutputStream out) throws Exception {
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static String shQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String apkPackageName(File f) {
        try {
            return AxmlPatch.read(ZipAlignWriter.extractEntry(f, "AndroidManifest.xml")).packageName;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String condense(String s) {
        if (s == null) return "";
        String[] lines = s.split("\n");
        StringBuilder sb = new StringBuilder();
        int skipped = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("at ") || line.startsWith("... ") || line.startsWith("Caused by:")) {
                if (line.startsWith("Caused by:")) sb.append(line).append('\n');
                skipped++;
                continue;
            }
            sb.append(line).append('\n');
        }
        if (skipped > 0) sb.append("…（省略 ").append(skipped).append(" 行堆栈）\n");
        return sb.toString().trim();
    }

    // ================================================================= 提示

    /** 轻提示：保留 Toast（Q6 决策：重要结果走 Snackbar，轻提示仍用 Toast）。 */
    private void toast(String s) {
        main.post(() -> Toast.makeText(MainActivity.this, s, Toast.LENGTH_SHORT).show());
    }

    /** 重要结果：Snackbar。 */
    private void snack(String s) {
        main.post(() -> ui.snack(s));
    }

    /** 必须点掉的重要结果：M3 AlertDialog。 */
    private void alert(String title, String message) {
        main.post(() -> ui.alert(title, message));
    }

    private void log(String line) {
        main.post(() -> ui.log(line));
    }

    /** 日志小节标题（取代旧的 `──── xxx ────` 画线分隔）。 */
    private void section(String title) {
        main.post(() -> ui.section(title));
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(65536);
        try {
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
        return out.toByteArray();
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
            try {
                out.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
