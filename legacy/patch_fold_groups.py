# -*- coding: utf-8 -*-
"""主页改成多个折叠分组：版本号（默认展开）/ 安卓SDK版本 / 应用标识（默认收起）。"""
import io, sys

AVT = '/workspace/avt/'
ma = AVT + 'src/com/seagull/avt/app/MainActivity.java'
s = io.open(ma, encoding='utf-8').read()

reps = []

# ---------- 1) 字段 ----------
old1 = """    /** 「安卓SDK版本」折叠区：收起 = minSdk / targetSdk 完全不参与改写。 */
    private LinearLayout sdkBody;
    private TextView sdkToggle;
    private boolean sdkExpanded;
"""
new1 = """    /** 折叠分组状态：key -> 是否展开；收起的分组整组字段都不参与改写。 */
    private final Map<String, Boolean> foldOpenState = new LinkedHashMap<String, Boolean>();
    private final Map<String, LinearLayout> foldBodies = new LinkedHashMap<String, LinearLayout>();
    private final Map<String, TextView> foldToggles = new LinkedHashMap<String, TextView>();
"""
reps.append((old1, new1))

# ---------- 2) 布局 ----------
old2 = """        etName = field("新 versionName", false);
        c2.addView(etName);
        c2.addView(space(dp(6)));
        LinearLayout rCode = row();
        etCode = fieldNum("新 versionCode");
        etCodeMajor = fieldNum("versionCodeMajor");
        addPair(rCode, etCode, etCodeMajor, dp(8));
        c2.addView(rCode);
        c2.addView(space(dp(6)));
        // 安卓 SDK 版本：默认收起，收起状态下这两项完全不参与改写；
        // 展开后两个框各占一整行（android:minSdkVersion / targetSdkVersion 是长数字，并排容易撑爆）
        LinearLayout sdkHead = row();
        sdkHead.setGravity(Gravity.CENTER_VERTICAL);
        sdkHead.setMinimumHeight(dp(46));
        int sdkHeadPad = dp(10);
        sdkHead.setPadding(sdkHeadPad, dp(8), sdkHeadPad, dp(8));
        android.graphics.drawable.GradientDrawable sdkHeadBg = new android.graphics.drawable.GradientDrawable();
        sdkHeadBg.setColor(cFieldBg);
        sdkHeadBg.setCornerRadius(dp(12));
        sdkHeadBg.setStroke(dp(1), cFieldStroke);
        sdkHead.setBackground(sdkHeadBg);
        sdkHead.setClickable(true);
        sdkHead.setFocusable(true);
        TextView sdkTitle = new TextView(this);
        sdkTitle.setText("📱 安卓SDK版本");
        sdkTitle.setTextSize(15f);
        sdkTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        sdkTitle.setTextColor(cInk);
        sdkHead.addView(sdkTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        sdkToggle = new TextView(this);
        sdkToggle.setText("展开 ▾");
        sdkToggle.setTextSize(13f);
        sdkToggle.setTextColor(cAccent);
        sdkToggle.setGravity(Gravity.CENTER_VERTICAL);
        sdkHead.addView(sdkToggle, new LinearLayout.LayoutParams(-2, -2));
        sdkHead.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { toggleSdk(); }
        });
        c2.addView(sdkHead);

        sdkBody = new LinearLayout(this);
        sdkBody.setOrientation(LinearLayout.VERTICAL);
        sdkBody.setVisibility(sdkExpanded ? View.VISIBLE : View.GONE);
        sdkBody.addView(space(dp(8)));
        etMinSdk = fieldNum("最小SDK");
        etTargetSdk = fieldNum("目标SDK");
        sdkBody.addView(etMinSdk);
        sdkBody.addView(space(dp(6)));
        sdkBody.addView(etTargetSdk);
        sdkBody.addView(space(dp(6)));
        sdkBody.addView(note("收起时这两项不参与改写；展开后留空同样视为不改。"));
        c2.addView(sdkBody);
        c2.addView(space(dp(6)));
        etLabel = field("新应用名（android:label）", false);
        c2.addView(etLabel);
        c2.addView(space(dp(6)));
        etPackage = field("新包名（联动 dex / arsc / so）", false);
        c2.addView(etPackage);
        c2.addView(space(dp(6)));
        c2.addView(note("改包名会同步改写 AndroidManifest 字符串池、dex 字符串表、resources.arsc 全局池；"
                + "lib 下的 .so 仅在长度相等时原地替换。"));
        body.addView(c2);
"""
new2 = """        // 三组折叠：版本号最常用所以默认展开，另外两组默认收起（收起 = 整组字段不参与改写）
        LinearLayout gVer = addFoldGroup(c2, "ver", "📦 版本号", true);
        etName = field("新 versionName", false);
        gVer.addView(etName);
        gVer.addView(space(dp(6)));
        LinearLayout rCode = row();
        etCode = fieldNum("新 versionCode");
        etCodeMajor = fieldNum("versionCodeMajor");
        addPair(rCode, etCode, etCodeMajor, dp(8));
        gVer.addView(rCode);
        gVer.addView(space(dp(8)));

        c2.addView(space(dp(6)));
        LinearLayout gSdk = addFoldGroup(c2, "sdk", "📱 安卓SDK版本", false);
        etMinSdk = fieldNum("最小SDK");
        etTargetSdk = fieldNum("目标SDK");
        gSdk.addView(etMinSdk);
        gSdk.addView(space(dp(6)));
        gSdk.addView(etTargetSdk);
        gSdk.addView(space(dp(6)));
        gSdk.addView(note("收起时这两项不参与改写；展开后留空同样视为不改。"));
        gSdk.addView(space(dp(8)));

        c2.addView(space(dp(6)));
        LinearLayout gId = addFoldGroup(c2, "id", "🏷 应用标识（应用名 / 包名）", false);
        etLabel = field("新应用名（android:label）", false);
        etPackage = field("新包名（联动 dex / arsc / so）", false);
        gId.addView(etLabel);
        gId.addView(space(dp(6)));
        gId.addView(etPackage);
        gId.addView(space(dp(6)));
        gId.addView(note("改包名会同步改写 AndroidManifest 字符串池、dex 字符串表、resources.arsc 全局池；"
                + "lib 下的 .so 仅在长度相等时原地替换。"));
        gId.addView(space(dp(8)));
        body.addView(c2);
"""
reps.append((old2, new2))

# ---------- 3) 提交 ----------
old3 = """        final String nn = etName.getText().toString().trim();
        final String nc = etCode.getText().toString().trim();
        final String ncm = etCodeMajor.getText().toString().trim();
        // 「安卓SDK版本」收起 = 这两项一律不动；展开后才读输入框（留空仍视为不改）
        final String nms = sdkExpanded ? etMinSdk.getText().toString().trim() : "";
        final String nts = sdkExpanded ? etTargetSdk.getText().toString().trim() : "";
        final String nl = etLabel.getText().toString().trim();
        final String np = etPackage.getText().toString().trim();
"""
new3 = """        // 只有展开的分组才读输入框：收起的分组整组不动，展开了也允许留空（留空 = 不改）
        final boolean gVer = isFoldOpen("ver");
        final boolean gSdk = isFoldOpen("sdk");
        final boolean gId = isFoldOpen("id");
        final String nn = gVer ? etName.getText().toString().trim() : "";
        final String nc = gVer ? etCode.getText().toString().trim() : "";
        final String ncm = gVer ? etCodeMajor.getText().toString().trim() : "";
        final String nms = gSdk ? etMinSdk.getText().toString().trim() : "";
        final String nts = gSdk ? etTargetSdk.getText().toString().trim() : "";
        final String nl = gId ? etLabel.getText().toString().trim() : "";
        final String np = gId ? etPackage.getText().toString().trim() : "";
"""
reps.append((old3, new3))

# ---------- 4) 折叠逻辑 ----------
old4 = """    // ---------------- 折叠：安卓SDK版本 ----------------

    /** 展开 / 收起「安卓SDK版本」；收起状态下 minSdk / targetSdk 不参与改写。 */
    private void toggleSdk() {
        sdkExpanded = !sdkExpanded;
        if (sdkBody != null) {
            sdkBody.setVisibility(sdkExpanded ? View.VISIBLE : View.GONE);
        }
        if (sdkToggle != null) {
            sdkToggle.setText(sdkExpanded ? "收起 ▴" : "展开 ▾");
        }
    }

"""
new4 = """    // ---------------- 折叠分组 ----------------

    /**
     * 往卡片里加一个折叠分组：标题整行可点，右侧是「展开 ▾ / 收起 ▴」。
     * 返回内容容器，调用方往里塞输入框；展开状态按 key 记在 foldOpenState 里，
     * 提交时只读展开分组 —— 收起的分组整组字段都不参与改写。
     */
    private LinearLayout addFoldGroup(LinearLayout card, final String key, String title,
                                      boolean defaultOpen) {
        Boolean saved = foldOpenState.get(key);
        final boolean open = saved == null ? defaultOpen : saved.booleanValue();
        foldOpenState.put(key, Boolean.valueOf(open));

        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(dp(46));
        int pad = dp(10);
        head.setPadding(pad, dp(8), pad, dp(8));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(cFieldBg);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), cFieldStroke);
        head.setBackground(bg);
        head.setClickable(true);
        head.setFocusable(true);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(15f);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleView.setTextColor(cInk);
        head.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView toggle = new TextView(this);
        toggle.setText(open ? "收起 ▴" : "展开 ▾");
        toggle.setTextSize(13f);
        toggle.setTextColor(cAccent);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(toggle, new LinearLayout.LayoutParams(-2, -2));

        head.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { toggleFold(key); }
        });
        card.addView(head);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        body.addView(space(dp(8)));
        card.addView(body);

        foldBodies.put(key, body);
        foldToggles.put(key, toggle);
        return body;
    }

    /** 展开 / 收起某个分组。 */
    private void toggleFold(String key) {
        boolean open = !isFoldOpen(key);
        foldOpenState.put(key, Boolean.valueOf(open));
        LinearLayout body = foldBodies.get(key);
        if (body != null) body.setVisibility(open ? View.VISIBLE : View.GONE);
        TextView toggle = foldToggles.get(key);
        if (toggle != null) toggle.setText(open ? "收起 ▴" : "展开 ▾");
    }

    /** 分组是否展开：只有展开的分组，字段才参与改写。 */
    private boolean isFoldOpen(String key) {
        Boolean b = foldOpenState.get(key);
        return b != null && b.booleanValue();
    }

"""
reps.append((old4, new4))

for old, new in reps:
    n = s.count(old)
    if n != 1:
        print('!! 匹配 %d 次: %r' % (n, old.strip().splitlines()[0][:70]))
        sys.exit(1)
    s = s.replace(old, new)

io.open(ma, 'w', encoding='utf-8').write(s)
print('ok MainActivity.java -> 三组折叠')
