#!/bin/sh
set -e
TC=/opt/toolchain
mkdir -p $TC
cd $TC
GRADLE_URL=https://mirrors.aliyun.com/macports/distfiles/gradle/gradle-8.14.2-bin.zip

echo "== [1/2] Gradle 8.14.2 (aliyun) =="
if [ ! -x $TC/gradle-8.14.2/bin/gradle ]; then
  rm -f gradle.zip
  curl -sSL -o gradle.zip "$GRADLE_URL"
  ls -la gradle.zip
  unzip -q -o gradle.zip
  rm -f gradle.zip
fi
echo "-- gradle ok --"

echo "== [2/2] Android cmdline-tools =="
if [ ! -x $TC/android-sdk/cmdline-tools/latest/bin/sdkmanager ]; then
  mkdir -p $TC/android-sdk/cmdline-tools
  curl -sSL -o cmdline.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
  ls -la cmdline.zip
  rm -rf $TC/cmdline-tmp && mkdir -p $TC/cmdline-tmp
  unzip -q -o cmdline.zip -d $TC/cmdline-tmp
  rm -rf $TC/android-sdk/cmdline-tools/latest
  mv $TC/cmdline-tmp/cmdline-tools $TC/android-sdk/cmdline-tools/latest
  rm -rf $TC/cmdline-tmp cmdline.zip
fi
echo "-- sdkmanager ok --"
ls $TC/android-sdk/cmdline-tools/latest/bin/
echo "ALL_DONE"
