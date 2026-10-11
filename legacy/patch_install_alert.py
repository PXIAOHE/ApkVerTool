# -*- coding: utf-8 -*-
"""root 安装的结果提示：成功/失败都弹对话框，判定同时看 Success 与 exit code。"""
import io, sys

AVT = '/workspace/avt/'

ma = AVT + 'src/com/seagull/avt/app/MainActivity.java'
s = io.open(ma, encoding='utf-8').read()

reps = []

# 1) install() 先记一行「走哪条安装路径」
reps.append((
    """    private void install() {
        if (lastOutput == null && lastSafUri == null) { toast("还没有输出文件"); return; }
        if (lastOutputSigned) systemInstall();
        else askRootInstall();
    }
""",
    """    private void install() {
        if (lastOutput == null && lastSafUri == null) { toast("还没有输出文件"); return; }
        if (lastOutputSigned) {
            log("安装：产物已签名 → 交给系统安装器");
            systemInstall();
        } else {
            log("安装：产物未重签 → 只能 root 静默安装（需要核心破解）");
            askRootInstall();
        }
    }
"""))

# 2) doRootInstall 开头：识别「装的是不是本应用」
reps.append((
    """        log("准备 root 安装: " + apk.getName()
                + (lastOutput == null ? "（从指定目录取回）" : ""));
""",
    """        log("准备 root 安装: " + apk.getName()
                + (lastOutput == null ? "（从指定目录取回）" : ""));
        final boolean isSelf = getPackageName().equals(apkPackageName(apk));
        if (isSelf) log("注意：产物就是本应用自身，装完系统会重启本应用");
"""))

# 3) 结果判定 + 提示
reps.append((
    """                String low = out.toLowerCase();
                if (low.contains("success")) {
                    log("✓ root 安装成功");
                    toast("root 安装成功");
                } else if (low.contains("no_certificates") || low.contains("no signatures")
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
""",
    """                String low = out.toLowerCase();
                // 成功判定：pm 一般打 Success；有的 ROM 只给退出码，所以 (exit=0) 也算，
                // 但带 Failure / Exception 的输出绝不能算成功
                boolean ok = low.contains("success")
                        || (out.contains("(exit=0)") && !low.contains("failure")
                            && !low.contains("exception"));
                if (ok) {
                    log("✓ root 安装成功");
                    toast("root 安装成功");
                    alert("✓ 安装成功", "已通过 root 静默安装完成。"
                            + (isSelf ? "\\n\\n产物就是本应用自身，系统会重启本应用，属正常现象。" : ""));
                } else if (low.contains("no_certificates") || low.contains("no signatures")
                        || low.contains("signature") || low.contains("parse_failed")
                        || low.contains("failed to parse") || low.contains("failed to load asset path")) {
                    log("✗ 系统拒绝了这个产物：内容改过 → 搬过来的原签名摘要必然失效，"
                            + "不重签就过不了系统的解析 / 签名校验。");
                    log("  想直接装：设置 → 签名 里打开「重签名」；"
                            + "或者给系统装核心破解类模块（CorePatch / 解锁签名校验）后再用 root 装。");
                    toast("安装被拒：需要重签名或核心破解");
                    alert("✗ 安装被系统拒绝", "内容改过的包必须重新签名；\\n"
                            + "或者给系统装核心破解类模块（禁用 APK 签名校验）后再用 root 安装。");
                } else {
                    toast("安装失败，详见日志");
                    alert("✗ 安装失败", "系统返回：\\n\\n" + condense(out));
                }
"""))

# 4) 工具方法：包名读取 + 醒目对话框
reps.append((
    """    /** 把 pm / su 的输出压成可读几行：丢掉 "\\tat ..." 堆栈行，末尾标注省略了多少行。 */
""",
    """    /** 读产物包的 packageName（用来判断装的是不是本应用）。 */
    private static String apkPackageName(File apk) {
        try {
            AxmlPatch.Info i = AxmlPatch.read(
                    ZipAlignWriter.extractEntry(apk, "AndroidManifest.xml"));
            return i.packageName;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 重要结果除了 toast 再给个对话框 —— toast 一闪而过，用户经常没看到。 */
    private void alert(final String title, final String msg) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                try {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(title)
                            .setMessage(msg)
                            .setPositiveButton("知道了", null)
                            .show();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** 把 pm / su 的输出压成可读几行：丢掉 "\\tat ..." 堆栈行，末尾标注省略了多少行。 */
"""))

for old, new in reps:
    n = s.count(old)
    if n != 1:
        print('!! 匹配 %d 次: %r' % (n, old.strip().splitlines()[0][:70]))
        sys.exit(1)
    s = s.replace(old, new)

io.open(ma, 'w', encoding='utf-8').write(s)
print('ok MainActivity.java')
