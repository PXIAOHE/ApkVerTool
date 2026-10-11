package com.seagull.avt.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.seagull.avt.ui.AvtUi;
import com.seagull.avt.ui.ChangelogHost;

/**
 * 更新日志宿主：一个版本一页，左右滑动翻页，不建底栏（页码写在顶栏标题上）。
 *
 * <p>内容来自 {@code version.py} 自动生成的 {@link AvtChangelog}，
 * 渲染整段交给 Compose（{@code ChangelogScreen}）。
 */
public class ChangelogActivity extends AvtBaseActivity implements ChangelogHost {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideActionBar();
        if (applyForceTheme()) return;
        initTheme();
        setupChrome();
        AvtUi.attachChangelog(this, this);
    }

    @Override
    public void closeChangelog() {
        finish();
    }

    public static Intent intent(Context context) {
        return new Intent(context, ChangelogActivity.class);
    }
}
