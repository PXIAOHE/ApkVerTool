package com.seagull.avt.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.Window;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;

import com.seagull.avt.ui.bars.BottomBarStyle;

/**
 * 三个页面的共同基类。
 *
 * 只负责两件与 Compose 无关的事：**主题模式**（沿用旧的 uiMode 改写机制）与**沉浸式系统栏**。
 * 界面本身全部交给 Compose（见 {@code com.seagull.avt.ui}），这里不再有任何 View 代码 ——
 * 原先那个 1184 行的 {@code PagedActivity}（手写顶栏 / 自研 SwipePager / 三套手写底栏 /
 * miuix Compose 桥 / 液态玻璃）已整体废弃。
 */
public abstract class AvtBaseActivity extends ComponentActivity {

    /** 首帧底色，与 Compose 侧 colorScheme 的 background 对齐，避免冷启动闪白 / 闪黑。 */
    private static final int BG_LIGHT = 0xFFF7FAFD;
    private static final int BG_DARK = 0xFF0F1418;

    /** 当前是否深色（由 uiMode 决定，attachBaseContext 里已经按 theme_mode 强制过）。 */
    protected boolean night;

    /** attachBaseContext 时定下的主题模式，用来在 onResume 里发现「设置页改了主题」。 */
    protected String appliedMode;

    /**
     * attachBaseContext 时定下的底栏样式 id。
     *
     * <p>和 {@link #appliedMode} 同一套机制：设置页改了底栏样式只落盘、不主动去动别的页面，
     * 回到本页时 onResume 发现对不上再 recreate —— 这样设置页里点选是即时生效的，
     * 而主页不用为了一个底栏样式常驻监听。
     */
    protected String appliedBarStyle;

    /**
     * 沿用旧机制：先按 theme_mode 改写 uiMode，再交给 Compose。
     * 这样 Compose 的 {@code isSystemInDarkTheme()} 拿到的就是「强制后」的值，
     * 主题切换不需要任何 Compose 侧的额外接线。
     */
    @Override
    protected void attachBaseContext(Context newBase) {
        ensureThemeDefault(newBase);
        appliedMode = themeMode(newBase);
        appliedBarStyle = BottomBarStyle.read(newBase).getId();
        super.attachBaseContext(newBase.createConfigurationContext(themed(newBase)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        String mode = themeMode(this);
        if (!mode.equals(appliedMode)) {
            appliedMode = mode;
            recreate();
            return;
        }
        String barStyle = BottomBarStyle.read(this).getId();
        if (!barStyle.equals(appliedBarStyle)) {
            appliedBarStyle = barStyle;
            recreate();
        }
    }

    /**
     * 处理 {@code force_theme} Intent extra（外部拉起时强制某个主题）。
     *
     * @return true 表示已经触发 recreate，调用方应当立即 return。
     */
    protected boolean applyForceTheme() {
        String force = getIntent() == null ? null : getIntent().getStringExtra("force_theme");
        if (force == null) return false;
        String mode = ("dark".equals(force) || "light".equals(force) || "auto".equals(force)) ? force : "auto";
        if (mode.equals(appliedMode)) return false;
        getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putString("theme_mode", mode).apply();
        appliedMode = mode;
        recreate();
        return true;
    }

    /** 隐藏系统 ActionBar（主题是 NoActionBar，这里只是兜底）。 */
    protected void hideActionBar() {
        if (getActionBar() != null) getActionBar().hide();
    }

    protected void initTheme() {
        night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    protected void setupChrome() {
        // 走 androidx 的 enableEdgeToEdge：内部是 setDecorFitsSystemWindows(false)，
        // Compose 侧拿到的 WindowInsets 才是真的（旧的手写 FLAG_LAYOUT_HIDE_NAVIGATION 会报假 inset）
        EdgeToEdge.enable(this,
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT));
        Window w = getWindow();
        w.getDecorView().setBackgroundColor(night ? BG_DARK : BG_LIGHT);
    }

    /** 按 theme_mode 改写 uiMode：auto 跟随系统，dark / light 强制。 */
    protected static Configuration themed(Context newBase) {
        Configuration conf = new Configuration(newBase.getResources().getConfiguration());
        String mode = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("theme_mode", "auto");
        boolean nightNow;
        if ("auto".equals(mode)) {
            nightNow = (conf.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
        } else {
            nightNow = "dark".equals(mode);
        }
        conf.uiMode = conf.uiMode & ~Configuration.UI_MODE_NIGHT_MASK
                | (nightNow ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return conf;
    }

    protected static String themeMode(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("theme_mode", "auto");
    }

    /** 首次进入时把 theme_mode 落一个默认值，避免别处读到 null。 */
    protected static void ensureThemeDefault(Context newBase) {
        SharedPreferences p = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE);
        if (!p.contains("theme_mode")) p.edit().putString("theme_mode", "auto").apply();
    }
}
