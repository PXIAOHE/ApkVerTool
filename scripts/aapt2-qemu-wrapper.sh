#!/bin/sh
# aarch64 上跑 x86_64 的 aapt2。
# Android SDK / AGP 只发 x86_64 的 aapt2 二进制，ARM 机器直接跑会 Exec format error。
# 用法：sh scripts/build-here.sh :app:assembleRelease
#      （它会把本脚本经 -Pandroid.aapt2FromMavenOverride 交给 AGP）
QEMU=/usr/bin/qemu-x86_64
REAL=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches" -path "*aapt2*" -name aapt2 -type f 2>/dev/null | head -1)
if [ -z "$REAL" ]; then
  REAL=$(find "${ANDROID_HOME:-/opt/toolchain/android-sdk}/build-tools" -name "aapt2.real" -type f 2>/dev/null | head -1)
fi
[ -x "$REAL" ] || { echo "找不到 x86_64 的 aapt2，先跑一次 ./gradlew 让它下到 Gradle 缓存" >&2; exit 1; }
exec "$QEMU" "$REAL" "$@"
