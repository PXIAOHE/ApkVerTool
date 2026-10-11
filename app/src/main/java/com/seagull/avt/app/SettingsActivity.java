package com.seagull.avt.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.widget.Toast;

import com.seagull.avt.core.SignUtil;
import com.seagull.avt.ui.AvtUi;
import com.seagull.avt.ui.bars.BottomBarStyle;
import com.seagull.avt.ui.SettingsHost;
import com.seagull.avt.ui.SettingsUiState;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 设置页宿主。
 *
 * <p>4 页：外观 / 签名 / 输出 / 关于。视图整体搬去 Compose，
 * 业务（密钥导入导出生成、输出目录 SAF 授权与可写探针）与重构前逐行等价。
 *
 * <p>已按 Q2/Q3 决策删除「底栏样式」「底栏显示模式」两组设置，
 * 关于页里的 Miuix 条目也一并移除。
 */
public class SettingsActivity extends AvtBaseActivity implements SettingsHost {

    private static final int REQ_KEY = 21;
    private static final int REQ_OUTPUT_DIR = 22;

    private final SettingsUiState ui = new SettingsUiState();
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideActionBar();
        if (applyForceTheme()) return;
        sp = getSharedPreferences("settings", MODE_PRIVATE);
        initTheme();
        setupChrome();
        syncState();
        AvtUi.attachSettings(this, ui, this);
    }

    /** 把 SharedPreferences 里的设置镜像到 Compose 状态。 */
    private void syncState() {
        boolean followSystem = "auto".equals(sp.getString("theme_mode", "auto"));
        ui.setFollowSystem(followSystem);
        ui.setDarkMode(night);

        ui.setBarStyle(BottomBarStyle.read(sp));

        ui.setResign(sp.getBoolean("resign", true));
        ui.setV1(sp.getBoolean("scheme_v1", true));
        ui.setV2(sp.getBoolean("scheme_v2", true));
        ui.setV3(sp.getBoolean("scheme_v3", true));
        ui.setV4(sp.getBoolean("scheme_v4", false));

        ui.setKeyPass(sp.getString("key_pass", ""));
        refreshKeyStatus();
        refreshOutputPath();
    }

    // ================================================================= SettingsHost

    @Override
    public void closeSettings() {
        finish();
    }

    @Override
    public void setFollowSystem(boolean on) {
        sp.edit().putString("theme_mode", on ? "auto" : (night ? "dark" : "light")).apply();
        recreate();
    }

    @Override
    public void setDarkMode(boolean on) {
        sp.edit().putString("theme_mode", on ? "dark" : "light").apply();
        recreate();
    }

    @Override
    public void setBarStyle(BottomBarStyle style) {
        BottomBarStyle.write(sp, style);
        // 本页的底栏已经跟着 state.barStyle 换了（SettingsScreen 显式传了），
        // 这里把 appliedBarStyle 一起对齐，免得 onResume 又 recreate 一次、
        // 把刚点下去的那一下动画打断。
        appliedBarStyle = style.getId();
        ui.setBarStyle(style);
        toast("底栏样式：" + style.getLabel());
    }

    @Override
    public void setResign(boolean on) {
        sp.edit().putBoolean("resign", on).apply();
        toast(on ? "已开启重签名" : "已切换为 MT 式不重签输出");
    }

    @Override
    public void setScheme(int index, boolean on) {
        String[] keys = {"scheme_v1", "scheme_v2", "scheme_v3", "scheme_v4"};
        if (index < 0 || index >= keys.length) return;
        sp.edit().putBoolean(keys[index], on).apply();
    }

    @Override
    public void pickKey() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("*/*");
        startActivityForResult(it, REQ_KEY);
    }

    @Override
    public void exportKey() {
        try {
            boolean custom = sp.getBoolean("use_custom", false);
            File customFile = custom ? new File(getFilesDir(), "custom_key.p12") : null;
            byte[] bytes = (customFile != null && customFile.exists())
                    ? readAll(new FileInputStream(customFile))
                    : readAll(getAssets().open("signkey.p12"));

            File dst = new File(exportDir(), "ApkVerTool-key.p12");
            try (FileOutputStream fos = new FileOutputStream(dst)) {
                fos.write(bytes);
                fos.flush();
            }
            toast("已导出: " + dst.getAbsolutePath());
        } catch (Throwable t) {
            toast("导出失败: " + t.getMessage());
        }
    }

    @Override
    public void generateKey() {
        final String alias = "ApkVerTool-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
        toast("正在生成密钥…");
        new Thread(() -> {
            try {
                File dst = new File(getFilesDir(), "custom_key.p12");
                SignUtil.generateKey(dst, SignUtil.PASS, alias, "Seagull", "Seagull", 10950, 2048);
                sp.edit().putBoolean("use_custom", true)
                        .putString("key_pass", new String(SignUtil.PASS)).apply();
                runOnUiThread(() -> {
                    ui.setKeyPass(new String(SignUtil.PASS));
                    refreshKeyStatus();
                    toast("已生成并启用新密钥：" + alias);
                });
            } catch (Throwable t) {
                runOnUiThread(() -> toast("生成失败: " + t.getMessage()));
            }
        }, "avt-genkey").start();
    }

    @Override
    public void clearKey() {
        File f = new File(getFilesDir(), "custom_key.p12");
        if (f.exists()) f.delete();
        sp.edit().putBoolean("use_custom", false).apply();
        refreshKeyStatus();
        toast("已恢复内置密钥");
    }

    @Override
    public void saveKeyPass(String pass) {
        sp.edit().putString("key_pass", pass == null ? "" : pass).apply();
        toast("已保存密钥密码");
    }

    @Override
    public void pickOutputDir() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(it, REQ_OUTPUT_DIR);
    }

    @Override
    public void resetOutputDir() {
        sp.edit().remove("output_uri").apply();
        refreshOutputPath();
        toast("已恢复默认输出位置");
    }

    @Override
    public void openChangelog() {
        startActivity(ChangelogActivity.intent(this));
    }

    @Override
    public void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("无法打开链接");
        }
    }

    // ================================================================= 选文件回调

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_KEY) {
            try {
                byte[] bytes = readAll(getContentResolver().openInputStream(data.getData()));
                if (bytes.length < 64) {
                    toast("文件太小，不是有效的 PKCS#12");
                    return;
                }
                File dst = new File(getFilesDir(), "custom_key.p12");
                try (FileOutputStream fos = new FileOutputStream(dst)) {
                    fos.write(bytes);
                    fos.flush();
                }
                sp.edit().putBoolean("use_custom", true).apply();
                refreshKeyStatus();
                toast("已导入自定义密钥（记得填密码并保存）");
            } catch (Throwable t) {
                toast("导入失败: " + t.getMessage());
            }
        } else if (requestCode == REQ_OUTPUT_DIR) {
            try {
                Uri uri = data.getData();
                int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if (flags == 0) {
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                }
                getContentResolver().takePersistableUriPermission(uri, flags);

                String err = probeWritable(uri);
                if (err != null) {
                    toast("这个目录不能写入（" + err + "），请换一个，比如 内部存储 / Download");
                    refreshOutputPath();
                    return;
                }
                sp.edit().putString("output_uri", uri.toString()).apply();
                refreshOutputPath();
                toast("输出位置已设置");
            } catch (Throwable t) {
                toast("设置失败: " + t.getMessage());
            }
        }
    }

    /** 目录可写探针：真的建一个临时文件再删掉。返回 null 表示可写。 */
    private String probeWritable(Uri uri) {
        Uri probe = null;
        try {
            ContentResolver cr = getContentResolver();
            String docId = DocumentsContract.getTreeDocumentId(uri);
            Uri dir = DocumentsContract.buildDocumentUriUsingTree(uri, docId);
            String name = ".avt_write_probe";
            try {
                probe = DocumentsContract.createDocument(cr, dir, "application/octet-stream", name);
            } catch (Throwable ignored) {
                // 部分 provider 不支持 createDocument，落到 insert
            }
            if (probe == null) {
                ContentValues cv = new ContentValues();
                cv.put("_display_name", name);
                cv.put("mime_type", "application/octet-stream");
                probe = cr.insert(dir, cv);
            }
            if (probe == null) return "provider 不支持创建文件";
            return null;
        } catch (Throwable t) {
            String msg = t.getMessage();
            return (msg == null || msg.isEmpty()) ? t.toString() : msg;
        } finally {
            if (probe != null) {
                try {
                    DocumentsContract.deleteDocument(getContentResolver(), probe);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ================================================================= 状态文本

    private void refreshKeyStatus() {
        boolean custom = sp.getBoolean("use_custom", false);
        File f = new File(getFilesDir(), "custom_key.p12");
        final String text = (custom && f.exists())
                ? "当前使用：自定义密钥（" + (f.length() / 1024L) + " KB）"
                : "当前使用：内置密钥";
        runOnUiThread(() -> ui.setKeyStatus(text));
    }

    private void refreshOutputPath() {
        String s = sp.getString("output_uri", "");
        String text;
        if (s != null && !s.isEmpty()) {
            try {
                String seg = Uri.parse(s).getLastPathSegment();
                if (seg != null) seg = seg.replace("primary:", "内部存储/");
                text = "当前：" + (seg != null ? seg : "自定义目录");
            } catch (Exception e) {
                text = "当前：默认（自动选择）";
            }
        } else {
            text = "当前：默认（应用私有目录 + 手动导出）";
        }
        final String t = text;
        runOnUiThread(() -> ui.setOutputPath(t));
    }

    private File exportDir() {
        File made = null;
        try {
            File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dl != null) {
                dl.mkdirs();
                File probe = new File(dl, "ApkVerTool-key.p12");
                try (FileOutputStream fos = new FileOutputStream(probe)) {
                    fos.flush();
                }
                made = probe;
            }
        } catch (Throwable ignored) {
            made = null;
        }
        if (made == null) {
            File dir = getExternalFilesDir(null);
            if (dir == null) dir = getFilesDir();
            dir.mkdirs();
            made = new File(dir, "ApkVerTool-key.p12");
        }
        return made;
    }

    private void toast(final String s) {
        runOnUiThread(() -> Toast.makeText(SettingsActivity.this, s, Toast.LENGTH_SHORT).show());
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
}
