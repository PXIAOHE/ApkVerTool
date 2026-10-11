# -*- coding: utf-8 -*-
"""主页改写页：「安卓SDK版本」折叠区（最小SDK / 目标SDK 各占一行，收起不参与改写）。"""
import io, sys

AVT = '/workspace/avt/'
ma = AVT + 'src/com/seagull/avt/app/MainActivity.java'
s = io.open(ma, encoding='utf-8').read()

reps = []

# ---- 1) 字段 ----
reps.append((
    """    private EditText etName, etCode, etCodeMajor, etMinSdk, etTargetSdk, etLabel, etPackage, etDrop;
""",
    """    private EditText etName, etCode, etCodeMajor, etMinSdk, etTargetSdk, etLabel, etPackage, etDrop;
    /** 「安卓SDK版本」折叠区：收起 = minSdk / targetSdk 完全不参与改写。 */
    private LinearLayout sdkBody;
    private TextView sdkToggle;
    private boolean sdkExpanded;
"""))

# ---- 2) 布局：原来并排的两个框 → 折叠区 ----
reps.append((
    """        LinearLayout rSdk = row();
        etMinSdk = fieldNum("新 minSdk");
        etTargetSdk = fieldNum("新 targetSdk");
        addPair(rSdk, etMinSdk, etTargetSdk, dp(8));
        c2.addView(rSdk);
        c2.addView(space(dp(6)));
""",
    """        // 安卓 SDK 版本：默认收起，收起状态下这两项完全不参与改写；
        // 展开后两个框各占一整行（android:minSdkVersion / targetSdkVersion 是长数字，并排容易撑爆）
        LinearLayout sdkHead = row();
        sdkHead.setGravity(Gravity.CENTER_VERTICAL);
        sdkHead.setMinimumHeight(dp(44));
        sdkHead.setPadding(0, dp(2), 0, dp(2));
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
        View sdkDivider = new View(this);
        sdkDivider.setBackgroundColor(cLine);
        sdkBody.addView(sdkDivider, new LinearLayout.LayoutParams(-1, dp(1)));
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
"""))

# ---- 3) 提交：收起就不读 ----
reps.append((
    """        final String nms = etMinSdk.getText().toString().trim();
        final String nts = etTargetSdk.getText().toString().trim();
""",
    """        // 「安卓SDK版本」收起 = 这两项一律不动；展开后才读输入框（留空仍视为不改）
        final String nms = sdkExpanded ? etMinSdk.getText().toString().trim() : "";
        final String nts = sdkExpanded ? etTargetSdk.getText().toString().trim() : "";
"""))

# ---- 4) 折叠切换 ----
reps.append((
    """    // ---------------- 执行 ----------------
""",
    """    // ---------------- 折叠：安卓SDK版本 ----------------

    /** 展开 / 收起「安卓SDK版本」；收起状态下 minSdk / targetSdk 不参与改写。 */\n    private void toggleSdk() {
        sdkExpanded = !sdkExpanded;
        if (sdkBody != null) {
            sdkBody.setVisibility(sdkExpanded ? View.VISIBLE : View.GONE);
        }
        if (sdkToggle != null) {
            sdkToggle.setText(sdkExpanded ? "收起 ▴" : "展开 ▾");
        }
    }

    // ---------------- 执行 ----------------
"""))

for old, new in reps:
    n = s.count(old)
    if n != 1:
        print('!! 匹配 %d 次: %r' % (n, old.strip().splitlines()[0][:70]))
        sys.exit(1)
    s = s.replace(old, new)

io.open(ma, 'w', encoding='utf-8').write(s)
print('ok MainActivity.java')
