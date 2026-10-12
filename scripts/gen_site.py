#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 version.py + dist/latest.json 生成官网首页与 README 的下载区。

用法：
    python3 scripts/gen_site.py

为什么生成而不手写：
  * 版本号、更新日志的单一真源是 version.py（SITE_URL 也在那里，换域名只改一处）；
  * 下载直链要带 commit 锁定（jsDelivr 对 @main 有最长 12 小时分支缓存，实测发新 commit 后仍返回旧内容）；
  * 每发一版重跑一次，README 与官网不会各写一份版本号然后走偏。

截图：把 docs/assets/screen-*.jpg 放进去就会自动出现在官网「界面」一节，
文件名与说明的对应关系见 SHOT_CAPTIONS。
"""

import glob
import html
import importlib.util
import io
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = "PXIAOHE/ApkVerTool"
MIN_ANDROID = "Android 13（API 33）"

# 截图文件名 → 说明（顺序即展示顺序）
SHOT_CAPTIONS = {
    "screen-write.jpg": ("改写", "版本号 / versionCode / SDK / 包名，一处改，dex 与 arsc 一起联动"),
    "screen-look.jpg": ("外观", "MiuixX 与 Material 3 两套界面热切换，莫奈取色 + 液态玻璃栏体"),
    "screen-about.jpg": ("关于", "内置更新检查与官网入口，点一下就能拿到国内直链新版本"),
}


def load_version():
    spec = importlib.util.spec_from_file_location("version", os.path.join(ROOT, "version.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def load_dist():
    p = os.path.join(ROOT, "dist", "latest.json")
    if os.path.exists(p):
        with io.open(p, encoding="utf-8") as f:
            return json.load(f)
    return {}


def human_size(n):
    return "%.2f MB" % (n / 1024.0 / 1024.0)


def cdn_url(dist):
    commit = dist.get("cdn_commit") or ""
    apk = dist.get("apk_file") or ""
    if commit and apk:
        return "https://cdn.jsdelivr.net/gh/%s@%s/dist/%s" % (REPO, commit, apk)
    return ""


def build_shots():
    # 顺序按 SHOT_CAPTIONS 的声明来（改写 → 外观 → 关于），多余的放后面
    ordered = [os.path.join(ROOT, "docs", "assets", n) for n in SHOT_CAPTIONS]
    files = [f for f in ordered if os.path.exists(f)]
    files += [f for f in sorted(glob.glob(os.path.join(ROOT, "docs", "assets", "screen-*.jpg")))
              if f not in files]
    if not files:
        return ""
    figs = []
    for f in files:
        name = os.path.basename(f)
        title, desc = SHOT_CAPTIONS.get(name, (name, ""))
        figs.append(
            '      <figure class="shot reveal">\n'
            '        <img src="./assets/%s" alt="%s 界面截图" loading="lazy">\n'
            '        <figcaption><strong>%s</strong>%s</figcaption>\n'
            '      </figure>' % (html.escape(name), html.escape(title),
                                  html.escape(title),
                                  ("　" + html.escape(desc)) if desc else "")
        )
    return ('<section id="shots" class="reveal">\n'
            '  <div class="wrap">\n'
            '    <div class="sec-head">\n'
            '      <div class="eyebrow">Screenshots</div>\n'
            '      <h2>界面</h2>\n'
            '      <p>真机截图（MiuixX · 深色）。设置里可热切到 Material 3 界面。</p>\n'
            '    </div>\n'
            '    <div class="shots">\n%s\n    </div>\n'
            '  </div>\n'
            '</section>' % "\n".join(figs))


def build_changelog(v):
    rows = []
    for i, (ver, date, items) in enumerate(v.CHANGELOG[:5]):
        lis = "\n".join("          <li>%s</li>" % html.escape(str(x)) for x in items)
        cls = " class=\"now\"" if i == 0 else ""
        badge = '<span class="chip">最新</span>' if i == 0 else ""
        rows.append(
            '      <article class="ver"%s>\n'
            '        <header><span class="vtag">v%s</span>%s<span class="vdate">%s</span></header>\n'
            '        <ul>\n%s\n        </ul>\n'
            '      </article>' % (cls, html.escape(ver), badge, html.escape(date), lis)
        )
    return "\n".join(rows)


def build_page(v, dist):
    name = v.VERSION_NAME
    code = v.VERSION_CODE
    sha = dist.get("sha256") or ""
    size = dist.get("size_bytes") or 0
    gh = dist.get("github_release") or "https://github.com/%s/releases/latest" % REPO
    cdn = cdn_url(dist)
    site = getattr(v, "SITE_URL", "https://pxiaohe.github.io/ApkVerTool/")

    cdn_btn = ('<a class="btn primary" href="%s">'
               '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3v12m0 0 5-5m-5 5-5-5M5 21h14"/></svg>'
               '国内直链下载<span class="sub">jsDelivr 加速</span></a>' % html.escape(cdn)
               if cdn else
               '<a class="btn primary" href="%s">下载 APK</a>' % html.escape(gh))
    gh_btn = ('<a class="btn ghost" href="%s">'
              '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.4 7.4 0 0 1 2-.27c.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8Z"/></svg>'
              'GitHub Release</a>' % html.escape(gh)) if cdn else ""
    sha_line = ('<details class="sha"><summary>校验值 SHA-256</summary><code>%s</code></details>'
                % html.escape(sha)) if sha else ""
    size_part = ('<span>· %s</span>' % human_size(size)) if size else ""

    tpl = io.open(os.path.join(ROOT, "scripts", "site_template.html"), encoding="utf-8").read()
    gh_url = dist.get("github_release") or gh
    return (tpl.replace("{{NAME}}", html.escape(name))
               .replace("{{CODE}}", str(code))
               .replace("{{CDN_COMMIT}}", html.escape(dist.get("cdn_commit") or ""))
               .replace("{{APK_FILE}}", html.escape(dist.get("apk_file") or ""))
               .replace("{{GH_URL}}", html.escape(gh_url))
               .replace("{{SIZE_HUMAN}}", human_size(size) if size else "—")
               .replace("{{SHA256}}", html.escape(sha or "—"))
               .replace("{{CDN_BTN}}", cdn_btn)
               .replace("{{GH_BTN}}", gh_btn)
               .replace("{{SHA_LINE}}", sha_line)
               .replace("{{SIZE_PART}}", size_part)
               .replace("{{MIN_ANDROID}}", MIN_ANDROID)
               .replace("{{CHANGELOG}}", build_changelog(v))
               .replace("{{SHOTS}}", build_shots())
               .replace("{{REPO}}", REPO)
               .replace("{{SITE}}", site))


def build_readme_block(v, dist):
    name = v.VERSION_NAME
    code = v.VERSION_CODE
    sha = dist.get("sha256") or ""
    size = dist.get("size_bytes") or 0
    gh = dist.get("github_release") or "https://github.com/%s/releases/latest" % REPO
    cdn = cdn_url(dist)
    site = getattr(v, "SITE_URL", "https://pxiaohe.github.io/ApkVerTool/")
    lines = [
        "<!-- DL:BEGIN 自动生成，勿手改：python3 scripts/gen_site.py -->",
        "",
        "### ⬇️ 下载 v%s（`versionCode %d`）" % (name, code),
        "",
        "| 渠道 | 链接 |",
        "| :--- | :--- |",
    ]
    if cdn:
        lines.append("| **国内直链（jsDelivr，推荐）** | [ApkVerTool-v%s.apk](%s) |" % (name, cdn))
    lines.append("| GitHub Release（兜底） | [ApkVerTool-v%s.apk](%s) |" % (name, gh))
    lines.append("| 官网 / 隐私政策 | [官网](%s) · [隐私政策](%sprivacy.html) |" % (site, site))
    lines.append("")
    if sha:
        lines.append("产物校验：`%s`" % sha)
        lines.append("")
    if size:
        lines.append("体积 %s · 系统要求 %s 及以上 · 下载后可直接安装（首次需允许「未知来源」）"
                     % (human_size(size), MIN_ANDROID))
        lines.append("")
    lines.append("<!-- DL:END -->")
    return "\n".join(lines)


def patch_readme(v, dist):
    path = os.path.join(ROOT, "README.md")
    src = io.open(path, encoding="utf-8").read()
    block = build_readme_block(v, dist)
    new, n = re.subn(r"<!-- DL:BEGIN.*?<!-- DL:END -->", lambda _m: block, src, flags=re.S)
    if n != 1:
        raise SystemExit("README 里没找到唯一的 <!-- DL:BEGIN --> / <!-- DL:END --> 区块，先补上标记再跑")
    new = re.sub(r"\| \*\*当前版本\*\* \| `[^`]+`（`versionCode \d+`） \|",
                 "| **当前版本** | `%s`（`versionCode %d`） |" % (v.VERSION_NAME, v.VERSION_CODE),
                 new, count=1)
    io.open(path, "w", encoding="utf-8").write(new)


def main():
    v = load_version()
    dist = load_dist()
    out = os.path.join(ROOT, "docs", "index.html")
    with io.open(out, "w", encoding="utf-8") as f:
        f.write(build_page(v, dist))
    patch_readme(v, dist)
    print("已生成 %s + README 下载区（v%s · code %s · 直链 %s · 截图 %d 张）"
          % (out, v.VERSION_NAME, v.VERSION_CODE,
             "commit 锁定" if dist.get("cdn_commit") else "未锁定",
             len(glob.glob(os.path.join(ROOT, "docs", "assets", "screen-*.jpg")))))


if __name__ == "__main__":
    main()
