#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CI 发版后的收尾：把产物同步进 dist/，刷新官网与 README，并把改动提交回 main。

由 .github/workflows/build_release.yml 在「push 到 main」且 Release 已发布之后调用。

为什么要两次提交：
  * jsDelivr 对 @main 有最长 12 小时分支缓存，所以下载直链必须锁 commit；
  * 而 APK 要先落进某个 commit 才存在，那个 commit 的 sha 又得写回 latest.json。
  因此：先提交 APK + latest.json（cdn_commit 留空），取到 sha 后再写回并重生成官网。

用法（CI 里由 workflow 调用）：
    python3 scripts/ci_publish.py --apk app/build/outputs/apk/release/ApkVerTool-v2.4.0.apk \
                                  --name 2.4.0 --code 35
本地想看看效果而不提交：
    python3 scripts/ci_publish.py ... --dry-run

任何一步失败都以非 0 退出，让 CI 明确变红，不静默吞掉。
"""

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = "PXIAOHE/ApkVerTool"
DIST = os.path.join(ROOT, "dist")
LATEST = os.path.join(DIST, "latest.json")


def run(*cmd):
    print("+ " + " ".join(cmd))
    subprocess.run(cmd, cwd=ROOT, check=True)


def out(*cmd):
    return subprocess.run(cmd, cwd=ROOT, check=True, capture_output=True, text=True).stdout.strip()


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def read_latest():
    try:
        with open(LATEST, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def write_latest(name, code, apk_file, digest, size, cdn_commit):
    data = {
        "version_name": name,
        "version_code": code,
        "apk_file": apk_file,
        "sha256": digest,
        "size_bytes": size,
        "cdn_commit": cdn_commit,
        "github_release": "https://github.com/%s/releases/download/v%s/%s" % (REPO, name, apk_file),
    }
    with open(LATEST, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    return data


def commit_and_push(message, paths):
    """有变化才提交；返回新 commit 的 sha，没有变化返回 None。"""
    run("git", "add", *paths)
    if not out("git", "status", "--porcelain", "--", *paths):
        print("· 没有变化，跳过提交：%s" % message.splitlines()[0])
        return None
    run("git", "commit", "-m", message)
    push_with_retry()
    return out("git", "rev-parse", "HEAD")


def push_with_retry():
    """main 可能被别的事件同时推进：rebase 后重试一次，仍失败就抛错让 CI 变红。"""
    try:
        run("git", "push", "origin", "HEAD:main")
        return
    except subprocess.CalledProcessError:
        print("· push 被拒，rebase 后重试一次")
    subprocess.run(["git", "pull", "--rebase", "--quiet", "origin", "main"], cwd=ROOT, check=True)
    run("git", "push", "origin", "HEAD:main")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", required=True, help="构建出的 release APK 路径")
    ap.add_argument("--name", required=True, help="versionName，例如 2.4.0")
    ap.add_argument("--code", required=True, type=int, help="versionCode，例如 35")
    ap.add_argument("--dry-run", action="store_true", help="只改文件，不提交不推送")
    args = ap.parse_args()

    if not os.path.isfile(args.apk):
        sys.exit("找不到 APK：%s" % args.apk)
    if not os.path.isdir(DIST):
        sys.exit("找不到 dist/ 目录：%s" % DIST)

    apk_name = "ApkVerTool-v%s.apk" % args.name
    apk_dst = os.path.join(DIST, apk_name)
    digest = sha256(args.apk)
    size = os.path.getsize(args.apk)

    # 幂等按版本号判断：同一版本重复构建（每次字节都不同）不该反复刷 dist/ 与官网
    old = read_latest()
    if (old.get("version_name") == args.name
            and old.get("version_code") == args.code
            and os.path.isfile(apk_dst)):
        print("· dist/ 已是 v%s（code %s），跳过" % (args.name, args.code))
        return

    # ── 第 1 步：APK 落进 dist/，latest.json 先写不带 cdn_commit 的版本 ──
    shutil.copyfile(args.apk, apk_dst)
    write_latest(args.name, args.code, apk_name, digest, size, "")
    print("· dist/%s  %.2f MB  sha256=%s" % (apk_name, size / 1048576.0, digest))

    if args.dry_run:
        print("· dry-run：跳过提交，用占位 cdn_commit 重生成官网")
        write_latest(args.name, args.code, apk_name, digest, size, "0" * 40)
        run(sys.executable, "scripts/gen_site.py")
        return

    sha1 = commit_and_push(
        "chore(dist): v%s 产物入库（%s）\n\n[skip ci]" % (args.name, digest[:12]),
        ["dist"],
    )
    if sha1 is None:
        print("· dist 提交没有产生新 commit，用当前 HEAD 当 cdn_commit")
        sha1 = out("git", "rev-parse", "HEAD")
    print("· dist commit: %s" % sha1)

    # ── 第 2 步：cdn_commit 锁定到刚才那次提交，重生成官网与 README ──
    write_latest(args.name, args.code, apk_name, digest, size, sha1)
    run(sys.executable, "scripts/gen_site.py")
    sha2 = commit_and_push(
        "chore(site): v%s 下载区与官网刷新（cdn 锁定 %s）\n\n[skip ci]" % (args.name, sha1[:7]),
        ["dist", "docs", "README.md"],
    )
    print("· site commit: %s" % (sha2 or "（无变化）"))
    print("· 完成：Release v%s 已发布，dist/ 与官网已更新" % args.name)


if __name__ == "__main__":
    main()
