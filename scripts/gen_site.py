#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 version.py + dist/latest.json 生成官网首页 docs/index.html。

用法：
    python3 scripts/gen_site.py

为什么要生成而不是手写：版本号、更新日志都在 version.py 里（单一真源），
下载直链要带 commit 锁定的 jsDelivr 地址（避免 @main 的 12 小时分支缓存），
每发一版重跑一次就行，别手改 HTML。
"""

import html
import importlib.util
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = "PXIAOHE/ApkVerTool"
SITE = "https://pxiaohe.github.io/ApkVerTool/"


def load_version():
    spec = importlib.util.spec_from_file_location("version", os.path.join(ROOT, "version.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def human_size(n):
    return "%.2f MB" % (n / 1024.0 / 1024.0)


def build_page(v, dist):
    name = v.VERSION_NAME
    code = v.VERSION_CODE
    apk = dist.get("apk_file") or "ApkVerTool-v%s.apk" % name
    sha = dist.get("sha256") or ""
    size = dist.get("size_bytes") or 0
    commit = dist.get("cdn_commit") or ""
    gh = dist.get("github_release") or "https://github.com/%s/releases/latest" % REPO
    cdn = ("https://cdn.jsdelivr.net/gh/%s@%s/dist/%s" % (REPO, commit, apk)) if commit else ""

    cdn_btn = (
        '<a class="btn primary" href="%s">国内直链下载（jsDelivr）</a>' % html.escape(cdn)
        if cdn else
        '<a class="btn primary" href="%s">下载 APK</a>' % html.escape(gh)
    )
    gh_btn = (
        '<a class="btn" href="%s">GitHub Release 下载</a>' % html.escape(gh)
        if cdn else ""
    )

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

    sha_line = (
        '<div class="kv"><span>SHA-256</span><code>%s</code></div>' % html.escape(sha)
        if sha else ""
    )

    return """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>ApkVerTool · 安卓 APK 版本号修改与重打包工具</title>
<meta name="description" content="ApkVerTool：纯自研引擎的安卓 APK 清单查看/修改、条目增删、重打包与重签名工具，不依赖 apktool / aapt2。">
<style>
  :root {{ color-scheme: light dark; --fg:#1b1b1f; --bg:#fdfcff; --muted:#5a5a63; --line:#e3e1e8; --card:#f4f3f8; --accent:#4c6ef5; }}
  @media (prefers-color-scheme: dark) {{
    :root {{ --fg:#e5e2e9; --bg:#131316; --muted:#9d9aa4; --line:#2f2e34; --card:#1d1c21; --accent:#8ea1ff; }}
  }}
  * {{ box-sizing: border-box; }}
  body {{ margin:0; background:var(--bg); color:var(--fg);
    font: 16px/1.75 -apple-system, "Segoe UI", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif; }}
  main {{ max-width: 760px; margin: 0 auto; padding: 48px 20px 80px; }}
  h1 {{ font-size: 30px; margin: 0 0 4px; letter-spacing: .3px; }}
  h2 {{ font-size: 18px; margin: 38px 0 12px; }}
  h3 {{ font-size: 16px; margin: 22px 0 6px; }}
  .date {{ color: var(--muted); font-weight: 400; font-size: 14px; margin-left: 6px; }}
  .sub {{ color: var(--muted); margin: 0 0 24px; }}
  .card {{ background: var(--card); border: 1px solid var(--line); border-radius: 16px; padding: 20px 22px; }}
  .btns {{ display: flex; flex-wrap: wrap; gap: 10px; margin: 18px 0 14px; }}
  .btn {{ display:inline-block; padding: 12px 18px; border-radius: 12px; border: 1px solid var(--line);
    text-decoration: none; color: var(--fg); font-weight: 600; font-size: 15px; }}
  .btn.primary {{ background: var(--accent); border-color: transparent; color: #fff; }}
  .kv {{ display:flex; gap:10px; flex-wrap:wrap; font-size: 14px; color: var(--muted); margin-top: 6px; }}
  code {{ background: rgba(127,127,127,.14); border-radius: 6px; padding: 2px 6px;
    font-size: 13px; word-break: break-all; }}
  ul {{ padding-left: 22px; margin: 6px 0 0; }}
  li {{ margin: 3px 0; }}
  a.plain {{ color: var(--accent); }}
  .warn {{ font-size: 14px; color: var(--muted); }}
  footer {{ color: var(--muted); font-size: 13px; margin-top: 44px; border-top: 1px solid var(--line); padding-top: 16px; }}
</style>
</head>
<body>
<main>
  <h1>ApkVerTool</h1>
  <p class="sub">安卓 APK 清单查看与修改 · 条目增删 · 重打包重签名 —— 引擎全手写，不依赖 apktool / aapt2</p>

  <div class="card">
    <div class="kv"><span>当前版本</span><strong>v{name}</strong><span>（versionCode {code}）</span>{size_part}</div>
    <div class="btns">
      {cdn_btn}
      {gh_btn}
    </div>
    {sha_line}
    <p class="warn">系统要求：Android 8.0（API 26）及以上。下载后直接安装；首次安装需在系统里允许「未知来源」。{sha_hint}</p>
  </div>

  <h2>更新日志</h2>
{changelog}

  <h2>能做什么</h2>
  <ul>
    <li>改 versionName / versionCode / versionCodeMajor / minSdk / targetSdk / 包名 / 应用名</li>
    <li>增删 APK 内任意条目或目录前缀，支持 XAPK / APKS / split 多包容器逐个处理并回填元数据</li>
    <li>体检：版本、SDK、DEX、ABI、权限统计、加固壳识别、签名者证书指纹</li>
    <li>V1 / V2 / V3 / V4 重签名，重打包保持 4 字节与 16KB 对齐，产物可直接安装</li>
    <li>两套界面（MiuixX / Material 3）、莫奈取色、液态玻璃栏体</li>
  </ul>

  <h2>链接</h2>
  <ul>
    <li><a class="plain" href="https://github.com/{repo}">GitHub 仓库（源码 / 问题反馈）</a></li>
    <li><a class="plain" href="./privacy.html">隐私政策</a></li>
    <li><a class="plain" href="https://github.com/{repo}/releases">全部历史版本</a></li>
  </ul>

  <footer>
    Apache-2.0 开源 · 仅用于处理你有权处理的 APK 文件 · 本页由 <code>scripts/gen_site.py</code> 依据 <code>version.py</code> 自动生成
  </footer>
</main>
</body>
</html>
""" % {
        "name": html.escape(name),
        "code": code,
        "cdn_btn": cdn_btn,
        "gh_btn": gh_btn,
        "sha_line": sha_line,
        "size_part": ('<span>· %s</span>' % human_size(size)) if size else "",
        "sha_hint": " 下载后可自行核对上面的 SHA-256。" if sha else "",
        "changelog": changelog,
        "repo": REPO,
    }


def main():
    v = load_version()
    dist_path = os.path.join(ROOT, "dist", "latest.json")
    dist = {}
    if os.path.exists(dist_path):
        with io.open(dist_path, encoding="utf-8") as f:
            dist = json.load(f)
    out = os.path.join(ROOT, "docs", "index.html")
    with io.open(out, "w", encoding="utf-8") as f:
        f.write(build_page(v, dist))
    print("已生成 %s（v%s · code %s · 直链 %s）"
          % (out, v.VERSION_NAME, v.VERSION_CODE,
             "commit 锁定" if dist.get("cdn_commit") else "未锁定 commit"))


if __name__ == "__main__":
    main()
