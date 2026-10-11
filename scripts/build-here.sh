#!/bin/sh
# 本机（Android/aarch64 + Debian 容器）构建入口。
#
# 为什么需要这个脚本而不是直接 ./gradlew：
#   1) 本机 JDK 21 是 jre-only，构建前需 apt install openjdk-21-jdk-headless；
#   2) Android 的 aapt2 只有 x86_64 二进制，本机是 aarch64 —— 用 qemu-user 跑，
#      并通过 -Pandroid.aapt2FromMavenOverride 指到 wrapper。
# 在普通 x86_64 机器上直接用 ./gradlew :app:assembleRelease 即可，不需要本脚本。
set -e
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-arm64}
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_HOME=${ANDROID_HOME:-/opt/toolchain/android-sdk}
export ANDROID_SDK_ROOT=$ANDROID_HOME
export GRADLE_USER_HOME=${GRADLE_USER_HOME:-/opt/gradle-home}

HERE=$(cd "$(dirname "$0")/.." && pwd)
cd "$HERE"

AAPT2_OVERRIDE=""
if [ "$(uname -m)" != "x86_64" ] && [ -x /opt/toolchain/bin/aapt2 ]; then
  AAPT2_OVERRIDE="-Pandroid.aapt2FromMavenOverride=/opt/toolchain/bin/aapt2"
fi

exec sh ./gradlew $AAPT2_OVERRIDE "$@"
