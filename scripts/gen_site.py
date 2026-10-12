#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 version.py + dist/latest.json 生成官网首页与 README 的下载区。

用法：
    python3 scripts/gen_site.py

为什么生成而不手写：
  * 版本号、更新日志的单一真源是 version.py；
  * 下载直链要带 commit 锁定（jsDelivr 对 @main 有最长 12 小时分支缓存，实测发新 commit 后仍返回旧内容）；
  * 每发一版重跑一次，README 与官网不会各写一份版本号然后走偏。

README 里被替换的是 <!-- DL:BEGIN --> 与 <!-- DL:END --> 之间的内容。
"""

import html
import importlib.util
import io
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = "PXIAOHE/ApkVerTool"
SITE = "https://pxiaohe.github.io/ApkVerTool/"
MIN_ANDROID = "Android 13（API 33）"


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


def build_page(v, dist):
    name = v.VERSION_NAME
    code = v.VERSION_CODE
    sha = dist.get("sha256") or ""
    size = dist.get("size_bytes") or 0
    gh = dist.get("github_release") or "https://github.com/%s/releases/latest" % REPO
    cdn = cdn_url(dist)

    cdn_btn = ('<a class="btn primary" href="%s">国内直链下载（jsDelivr）</a>' % html.escape(cdn)
               if cdn else '<a class="btn primary" href="%s">下载 APK</a>' % html.escape(gh))
    gh_btn = ('<a class="btn" href="%s">GitHub Release 下载</a>' % html.escape(gh)) if cdn else ""

    log_rows = []
    for ver, date, items in v.CHANGELOG[:4]:
        lis = "\n".join("        <li>%s</li>" % html.escape(str(i)) for i in items)
        log_rows.append(
            '      <section class="ver">\n'
            '        <h3>v%s <span class="date">%s</span></h3>\n'
            '        <ul>\n%s\n        </ul>\n'
            '      </section>' % (html.escape(ver), html.escape(date), lis)
        )
    changelog = "\n".join(log_rows)
    sha_line = ('<div class="kv"><span>SHA-256</span><code>%s</code></div>' % html.escape(sha)) if sha else ""

    tpl = io.open(os.path.join(ROOT, "scripts", "site_template.html"), encoding="utf-8").read()
    return tpl.replace("{{NAME}}", html.escape(name)) \
              .replace("{{CODE}}", str(code)) \
              .replace("{{CDN_BTN}}", cdn_btn) \
              .replace("{{GH_BTN}}", gh_btn) \
              .replace("{{SHA_LINE}}", sha_line) \
              .replace("{{SIZE_PART}}", ('<span>· %s</span>' % human_size(size)) if size else "") \
              .replace("{{SHA_HINT}}", " 下载后可自行核对上面的 SHA-256。" if sha else "") \
              .replace("{{MIN_ANDROID}}", MIN_ANDROID) \
              .replace("{{CHANGELOG}}", changelog) \
              .replace("{{REPO}}", REPO) \
              .replace("{{SITE}}", SITE)


def build_readme_block(v, dist):
    name = v.VERSION_NAME
    code = v.VERSION_CODE
    sha = dist.get("sha256") or ""
    size = dist.get("size_bytes") or 0
    gh = dist.get("github_release") or "https://github.com/%s/releases/latest" % REPO
    cdn = cdn_url(dist)
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
    lines.append("| 官网 / 隐私政策 | [官网](%s) · [隐私政策](%sprivacy.html) |" % (SITE, SITE))
    lines.append("")
    if sha:
        lines.append("产物校验：`%s`" % sha)
        lines.append("")
    if size:
        lines.append("体积 %s · 系统要求 %s 及以上 · 下载后可直接安装（首次需允许「未知来源」）" % (human_size(size), MIN_ANDROID))
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
    print("已生成 %s + README 下载区（v%s · code %s · 直链 %s）"
          % (out, v.VERSION_NAME, v.VERSION_CODE, "commit 锁定" if dist.get("cdn_commit") else "未锁定"))


if __name__ == "__main__":
    main()
