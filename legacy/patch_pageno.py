# -*- coding: utf-8 -*-
"""无底栏页面：标题栏显示页码（更新日志 3/17），靠 onPageChanged 钩子同步。"""
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


p = AVT + 'src/com/seagull/avt/app/PagedActivity.java'

F_old = """    private LinearLayout nbRow;
    private View nbInset;
"""
F_new = """    private LinearLayout nbRow;
    private View nbInset;
    /** 顶部标题 TextView，{@link #setShellTitle} 用它（无底栏页显示页码）。 */
    private TextView shellTitle;
"""

T_old = """        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
"""
T_new = """        TextView tvTitle = new TextView(this);
        shellTitle = tvTitle;
        tvTitle.setText(title);
"""

L_old = """            public void onPageSelected(int index) {
                applyBarState(index);
            }
"""
L_new = """            public void onPageSelected(int index) {
                applyBarState(index);
                // 无底栏页面靠这个钩子自己更新标题（比如更新日志的 3/17）
                onPageChanged(index);
            }
"""

M_old = """    protected int currentPage() {
        return pager == null ? 0 : pager.getCurrentItem();
    }
"""
M_new = """    protected int currentPage() {
        return pager == null ? 0 : pager.getCurrentItem();
    }

    /** 更新顶部标题栏文案（无底栏页面用它显示页码）。 */
    protected void setShellTitle(String t) {
        if (shellTitle != null) shellTitle.setText(t);
    }

    /** 翻页钩子：默认什么都不做，无底栏页面可覆盖它同步标题。 */
    protected void onPageChanged(int index) {
    }
"""

edit(p, [(F_old, F_new), (T_old, T_new), (L_old, L_new), (M_old, M_new)])

c = AVT + 'src/com/seagull/avt/app/ChangelogActivity.java'
C_old = """        selectPage(0);
    }
"""
C_new = """        selectPage(0);
        onPageChanged(0);
    }

    /**
     * 这页没有底栏，页码只能写在标题栏上（第几页 / 共几页），
     * 不然左右滑动时完全不知道滑到哪儿了。
     */
    @Override
    protected void onPageChanged(int index) {
        if (AvtChangelog.COUNT <= 0) return;
        int page = Math.min(Math.max(index, 0), AvtChangelog.COUNT - 1);
        setShellTitle("📜 更新日志  " + (page + 1) + "/" + AvtChangelog.COUNT);
    }
"""
edit(c, [(C_old, C_new)])
print('done')
