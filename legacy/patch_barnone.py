# -*- coding: utf-8 -*-
"""给 PagedActivity 加 BAR_NONE（无底栏）形态，更新日志页改用它。"""
import io, sys

BASE = '/workspace/avt/src/com/seagull/avt/app/'


def edit(path, reps):
    s = io.open(path, encoding='utf-8').read()
    for old, new in reps:
        n = s.count(old)
        if n != 1:
            print('!! 匹配 %d 次: %s' % (n, old.strip().splitlines()[0][:80]))
            sys.exit(1)
        s = s.replace(old, new)
    io.open(path, 'w', encoding='utf-8').write(s)
    print('ok', path)


# ---------------- PagedActivity ----------------
p = BASE + 'PagedActivity.java'

R1_old = """    /** 底栏形态：Miuix 标准 NavigationBar（通栏 + 顶部细分割线 + 图标/文字 + 角标）。 */
    protected static final int BAR_MIUIX = 2;
"""
R1_new = """    /** 底栏形态：Miuix 标准 NavigationBar（通栏 + 顶部细分割线 + 图标/文字 + 角标）。 */
    protected static final int BAR_MIUIX = 2;
    /** 无底栏：只要顶部标题栏 + 左右滑动分页，不建任何底栏（更新日志页用）。 */
    protected static final int BAR_NONE = 3;
"""

R2_old = """ * 底栏三种形态：{@link #BAR_MIUIX}（默认，Miuix 标准 NavigationBar）、
 * {@link #BAR_ICON}（悬浮底栏）、{@link #BAR_CHIP}（文字胶囊合并条）。
"""
R2_new = """ * 底栏四种形态：{@link #BAR_MIUIX}（默认，Miuix 标准 NavigationBar）、
 * {@link #BAR_ICON}（悬浮底栏）、{@link #BAR_CHIP}（文字胶囊合并条）、
 * {@link #BAR_NONE}（不要底栏，只靠左右滑动翻页）。
"""

R3_old = """    private int barHeight;
    private int barMode = BAR_ICON;
    private int contentBottomPad;
"""
R3_new = """    private int barHeight;
    private int barMode = BAR_ICON;
    private int contentBottomPad;
    /** true = 本次 buildShell 不建底栏（{@link #BAR_NONE}）。 */
    private boolean noBar;
"""

R4_old = """        barMode = mode;
        barHeight = dp(mode == BAR_CHIP ? BAR_HEIGHT_CHIP : BAR_HEIGHT_ICON);
        // Miuix 标准底栏是通栏贴底，没有悬浮留白；导航栏 inset 由底栏内部占位撑开
        barBottomMargin = mode == BAR_MIUIX ? 0 : dp(BAR_BOTTOM_MARGIN);
"""
R4_new = """        barMode = mode;
        // BAR_NONE：一个底栏都不建，页面只有顶部标题栏 + 左右滑动分页
        noBar = mode == BAR_NONE;
        barHeight = noBar ? 0 : dp(mode == BAR_CHIP ? BAR_HEIGHT_CHIP : BAR_HEIGHT_ICON);
        // Miuix 标准底栏是通栏贴底，没有悬浮留白；导航栏 inset 由底栏内部占位撑开
        barBottomMargin = (noBar || mode == BAR_MIUIX) ? 0 : dp(BAR_BOTTOM_MARGIN);
"""

R5_old = """        contentBottomPad = mode == BAR_ICON
                ? 0
                : (barHeight + barBottomMargin + dp(10));
"""
R5_new = """        contentBottomPad = (mode == BAR_ICON || noBar)
                ? 0
                : (barHeight + barBottomMargin + dp(10));
"""

R6_old = """        final SwipePager touchPager = pager;
        bar = new LinearLayout(this) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (touchPager != null && touchPager.handleExternalTouch(ev)) return true;
                return super.dispatchTouchEvent(ev);
            }
        };
        if (mode == BAR_MIUIX) {
            buildMiuixBar();
        } else {
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            int inner = dp(BAR_INNER_PADDING);
            bar.setPadding(inner, inner, inner, inner);
            bar.setBackground(glassBar());
            bar.setElevation(dp(BAR_ELEVATION));
            bar.setClipToPadding(false);
            applyBackgroundBlur(bar, BAR_BLUR_RADIUS);
        }
        if (mode == BAR_CHIP) {
            chipScroll = new HorizontalScrollView(this);
            chipScroll.setHorizontalScrollBarEnabled(false);
            chipScroll.setClipToPadding(false);
            chipScroll.setFillViewport(true);
            chipRow = new LinearLayout(this);
            chipRow.setOrientation(LinearLayout.HORIZONTAL);
            chipRow.setGravity(Gravity.CENTER_VERTICAL);
            chipScroll.addView(chipRow,
                    new FrameLayout.LayoutParams(-2, -1, Gravity.CENTER_VERTICAL));
            bar.addView(chipScroll, new LinearLayout.LayoutParams(-1, -1));
        }
        // 通栏底栏高度 = wrap_content（分割线 + 64dp 行 + inset 占位）；其余形态固定高度
        barLp = new FrameLayout.LayoutParams(-1, mode == BAR_MIUIX ? -2 : barHeight, Gravity.BOTTOM);
        int sideMargin = mode == BAR_MIUIX ? 0 : dp(BAR_SIDE_MARGIN);
        barLp.setMargins(sideMargin, 0, sideMargin, barBottomMargin);
        if (mode == BAR_CHIP) {
            rootView.addView(bar, barLp);
        } else if (!buildComposeBar(rootView, mode)) {
            // Compose 底栏起不来（缺类 / 异常）→ 回退到手写实现，不能让底栏挂了整个 App
            rootView.addView(bar, barLp);
        }
"""

R6_body = """        final SwipePager touchPager = pager;
        bar = new LinearLayout(this) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (touchPager != null && touchPager.handleExternalTouch(ev)) return true;
                return super.dispatchTouchEvent(ev);
            }
        };
        if (mode == BAR_MIUIX) {
            buildMiuixBar();
        } else {
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            int inner = dp(BAR_INNER_PADDING);
            bar.setPadding(inner, inner, inner, inner);
            bar.setBackground(glassBar());
            bar.setElevation(dp(BAR_ELEVATION));
            bar.setClipToPadding(false);
            applyBackgroundBlur(bar, BAR_BLUR_RADIUS);
        }
        if (mode == BAR_CHIP) {
            chipScroll = new HorizontalScrollView(this);
            chipScroll.setHorizontalScrollBarEnabled(false);
            chipScroll.setClipToPadding(false);
            chipScroll.setFillViewport(true);
            chipRow = new LinearLayout(this);
            chipRow.setOrientation(LinearLayout.HORIZONTAL);
            chipRow.setGravity(Gravity.CENTER_VERTICAL);
            chipScroll.addView(chipRow,
                    new FrameLayout.LayoutParams(-2, -1, Gravity.CENTER_VERTICAL));
            bar.addView(chipScroll, new LinearLayout.LayoutParams(-1, -1));
        }
        // 通栏底栏高度 = wrap_content（分割线 + 64dp 行 + inset 占位）；其余形态固定高度
        barLp = new FrameLayout.LayoutParams(-1, mode == BAR_MIUIX ? -2 : barHeight, Gravity.BOTTOM);
        int sideMargin = mode == BAR_MIUIX ? 0 : dp(BAR_SIDE_MARGIN);
        barLp.setMargins(sideMargin, 0, sideMargin, barBottomMargin);
        if (mode == BAR_CHIP) {
            rootView.addView(bar, barLp);
        } else if (!buildComposeBar(rootView, mode)) {
            // Compose 底栏起不来（缺类 / 异常）→ 回退到手写实现，不能让底栏挂了整个 App
            rootView.addView(bar, barLp);
        }
"""
R6_new = "        // BAR_NONE：整段底栏都不建，bar / barLp / barHandle 全为 null，\n" \
         "        // 左右滑动由 SwipePager 自己处理，不需要底栏转发触摸\n" \
         "        if (noBar) {\n" \
         "            bar = null;\n" \
         "            barLp = null;\n" \
         "        } else {\n" + \
         "".join("    " + ln + "\n" for ln in R6_body.rstrip("\n").split("\n")) + \
         "        }\n"

R7_old = """                // Compose 底栏自己处理 inset，只有手写底栏才需要这里摆位置
                if (barHandle == null) {
"""
R7_new = """                // 无底栏时不用摆位；Compose 底栏自己处理 inset，只有手写底栏才需要这里摆位置
                if (!noBar && barHandle == null) {
"""

edit(p, [
    (R1_old, R1_new),
    (R2_old, R2_new),
    (R3_old, R3_new),
    (R4_old, R4_new),
    (R5_old, R5_new),
    (R6_old, R6_new),
    (R7_old, R7_new),
])

# ---------------- ChangelogActivity ----------------
c = BASE + 'ChangelogActivity.java'
C_old = """        // 跟随设置里的「底栏样式」，跟主页/设置页保持一致；
        // 版本多，靠底栏横滚翻页（悬浮底栏已支持横滚）
        buildShell("📜 更新日志", "完成", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }, barStyle(this));
"""
C_new = """        // 更新日志页不要底栏：只留顶部标题栏 + 左右滑动翻页
        buildShell("📜 更新日志", "完成", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }, BAR_NONE);
"""
edit(c, [(C_old, C_new)])
print('done')
