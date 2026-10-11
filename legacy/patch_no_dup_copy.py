# -*- coding: utf-8 -*-
"""输出到指定目录后不再在应用私有目录留第二份：
   导出成功就把 files/out 里的中转副本删掉，安装 / 分享改用指定目录里的那份。
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


ma = AVT + 'src/com/seagull/avt/app/MainActivity.java'

reps = []

# 1) 字段
reps.append((
    """    private File lastOutput;
""",
    """    private File lastOutput;
    /** 导出到用户指定目录后的 uri；中转副本清理掉之后，安装 / 分享就走它。 */
    private Uri lastSafUri;
"""))

# 2) processOne 收尾：导出成功 → 删中转副本
reps.append((
    """        log("输出: " + out.getAbsolutePath() + "  (" + out.length() + " 字节)");
        exportToSaf(out, base);
        lastOutput = out;
        lastOutputSigned = job.resign;
        refreshInstallHint();
        return true;
""",
    """        log("输出: " + out.getAbsolutePath() + "  (" + out.length() + " 字节)");
        Uri saf = exportToSaf(out, base);
        lastOutput = out;
        lastSafUri = saf;
        lastOutputSigned = job.resign;
        // 用户指定了输出目录、而且已经导出成功 —— 应用私有目录里那份只是中转，
        // 留着白占空间（之前会在这里多留一份），删掉；安装 / 分享改用指定目录里的那份
        if (saf != null && out.delete()) {
            lastOutput = null;
            log("中转副本已清理（产物只留在指定目录里）");
        }
        refreshInstallHint();
        return true;
"""))

# 3) 按钮可用性
reps.append((
    """        final boolean hasOut = lastOutput != null;
""",
    """        final boolean hasOut = lastOutput != null || lastSafUri != null;
"""))

# 4) 新任务开始时重置
reps.append((
    """        busy = true;
        tvLog.setText("");
        btnInstall.setEnabled(false);
        btnShare.setEnabled(false);
""",
    """        busy = true;
        tvLog.setText("");
        lastSafUri = null;
        btnInstall.setEnabled(false);
        btnShare.setEnabled(false);
"""))

# 5) exportToSaf：返回 Uri
reps.append((
    """    private void exportToSaf(File apk, String base) {
        String oUri = getSharedPreferences("settings", MODE_PRIVATE).getString("output_uri", "");
        if (oUri == null || oUri.isEmpty()) return;
""",
    """    private Uri exportToSaf(File apk, String base) {
        String oUri = getSharedPreferences("settings", MODE_PRIVATE).getString("output_uri", "");
        if (oUri == null || oUri.isEmpty()) return null;
"""))

reps.append((
    """                log("SAF 导出失败：" + lastErr + " —— 这个目录的 provider 不支持创建文件，"
                        + "去「设置 → 输出位置」换成「内部存储 / Download」这类普通目录");
                return;
            }
            copy(new FileInputStream(apk), cr.openOutputStream(target));
            log("已导出到指定目录: " + base);
        } catch (Throwable t) {
            log("SAF 导出失败: " + t.getMessage() + "，去「设置 → 输出位置」重选一次即可恢复");
        }
    }
""",
    """                log("SAF 导出失败：" + lastErr + " —— 这个目录的 provider 不支持创建文件，"
                        + "去「设置 → 输出位置」换成「内部存储 / Download」这类普通目录");
                return null;
            }
            copy(new FileInputStream(apk), cr.openOutputStream(target));
            log("已导出到指定目录: " + base);
            return target;
        } catch (Throwable t) {
            log("SAF 导出失败: " + t.getMessage() + "，去「设置 → 输出位置」重选一次即可恢复");
            return null;
        }
    }
"""))

# 6) 安装 / 分享：产物可能只剩指定目录里的那份
reps.append((
    """    private Uri publishForExternal(File apk) throws Exception {
        File dst = new File(getCacheDir(), "share_" + sanitize(apk.getName()));
        copy(new FileInputStream(apk), new FileOutputStream(dst));
        return Uri.parse("content://" + AvtVersion.PROVIDER_AUTHORITY + "/" + dst.getName());
    }

    private void install() {
        if (lastOutput == null) { toast("还没有输出文件"); return; }
        if (lastOutputSigned) systemInstall();
        else askRootInstall();
    }
""",
    """    private Uri publishForExternal(File apk) throws Exception {
        File dst = new File(getCacheDir(), "share_" + sanitize(apk.getName()));
        copy(new FileInputStream(apk), new FileOutputStream(dst));
        return Uri.parse("content://" + AvtVersion.PROVIDER_AUTHORITY + "/" + dst.getName());
    }

    /** root 安装要本地文件：私有产物还在就用它，已经被清理就把指定目录里的拷回 cache。 */
    private File localCopyForRoot() {
        if (lastOutput != null && lastOutput.exists()) return lastOutput;
        if (lastSafUri == null) return null;
        try {
            File dst = new File(getCacheDir(), "install_" + System.currentTimeMillis() + ".apk");
            copy(getContentResolver().openInputStream(lastSafUri), new FileOutputStream(dst));
            return dst;
        } catch (Throwable t) {
            log("读取指定目录里的产物失败: " + t.getMessage());
            return null;
        }
    }

    private void install() {
        if (lastOutput == null && lastSafUri == null) { toast("还没有输出文件"); return; }
        if (lastOutputSigned) systemInstall();
        else askRootInstall();
    }
"""))

reps.append((
    """    private void systemInstall() {
        try {
            Uri u = publishForExternal(lastOutput);
            Intent i = new Intent(Intent.ACTION_VIEW);
""",
    """    private void systemInstall() {
        try {
            Uri u = lastOutput != null ? publishForExternal(lastOutput) : lastSafUri;
            if (u == null) { toast("产物不在本地，请到输出目录手动安装"); return; }
            Intent i = new Intent(Intent.ACTION_VIEW);
"""))

reps.append((
    """                    public void onClick(DialogInterface d, int w) {
                        doRootInstall(lastOutput);
                    }
""",
    """                    public void onClick(DialogInterface d, int w) {
                        doRootInstall();
                    }
"""))

reps.append((
    """    private void doRootInstall(final File apk) {
        log("准备 root 安装: " + apk.getName());
""",
    """    private void doRootInstall() {
        final File apk = localCopyForRoot();
        if (apk == null) {
            log("✗ 拿不到产物文件（产物只在指定目录里），请到输出目录手动安装");
            toast("拿不到产物文件");
            return;
        }
        log("准备 root 安装: " + apk.getName()
                + (lastOutput == null ? "（从指定目录取回）" : ""));
"""))

reps.append((
    """    private void share() {
        if (lastOutput == null) { toast("还没有输出文件"); return; }
        try {
            Uri u = publishForExternal(lastOutput);
            Intent i = new Intent(Intent.ACTION_SEND);
""",
    """    private void share() {
        if (lastOutput == null && lastSafUri == null) { toast("还没有输出文件"); return; }
        try {
            Uri u = lastOutput != null ? publishForExternal(lastOutput) : lastSafUri;
            if (u == null) { toast("产物不在本地，请到输出目录手动分享"); return; }
            Intent i = new Intent(Intent.ACTION_SEND);
"""))

edit(ma, reps)
print('done')
