#!/bin/sh
# ApkVerTool 一键构建脚本（依赖见 README.md 的 libs/ 说明）
# 版本号来自 version.py，本脚本不再硬编码版本
set -e
cd "$(dirname "$0")"

VER=$(python3 version.py name)
CODE=$(python3 version.py code)
APK=$(python3 version.py apk)
echo "== ApkVerTool v$VER (code $CODE) =="

mkdir -p build/dex build/dex2 build/appclasses build/cli

echo "[0/8] 从 version.py 生成 AvtVersion.java / AvtChangelog.java"
python3 version.py genjava src/com/seagull/avt/app

echo "[1/8] javac 编译 App"
rm -rf build/appclasses
mkdir -p build/appclasses
javac --release 8 -nowarn -cp libs/android28.jar:libs/apksig.jar -d build/appclasses \
    src/com/seagull/avt/core/*.java src/com/seagull/avt/app/*.java

echo "[2/8] javac 编译 CLI"
rm -rf build/cli
mkdir -p build/cli
javac --release 8 -nowarn -cp libs/apksig.jar -d build/cli \
    src/com/seagull/avt/core/*.java src/com/seagull/avt/cli/AvtCli.java

echo "[3/8] 核心引擎自检"
SAMPLE=$(ls -1t build/ApkVerTool-v*.apk 2>/dev/null | head -1)
if [ -n "$SAMPLE" ]; then
    echo "      样本: $SAMPLE"
    java -cp libs/apksig.jar:build/cli com.seagull.avt.cli.AvtCli selftest "$SAMPLE"
else
    echo "      跳过自检（还没有历史产物可当样本）"
fi

echo "[4/8] 打 jar"
jar cf build/app.jar -C build/appclasses .

echo "[5/8] R8 混淆 app 代码（core/类名混淆，apksig 走 classpath 不进混淆）"
rm -rf build/dex
mkdir -p build/dex
java -cp libs/r8.jar com.android.tools.r8.R8 --release \
    --lib libs/android.jar --lib libs/android28.jar \
    --classpath libs/apksig.jar \
    --min-api 26 --pg-conf proguard-rules.pro \
    --output build/dex build/app.jar

echo "[6/8] D8 编译 apksig（独立 classes2.dex，不混淆）"
rm -rf build/dex2
mkdir -p build/dex2
java -cp libs/r8.jar com.android.tools.r8.D8 --release \
    --lib libs/android.jar --lib libs/core-oj.jar --lib libs/core-libart.jar \
    --min-api 26 --output build/dex2 libs/apksig.jar

echo "[7/8] 生成图标 + resources.arsc + AndroidManifest.xml(AXML)"
python3 mkicon.py build
python3 mkaxml.py build/AndroidManifest.xml

echo "[8/8] 组包 + 签名"
python3 pack.py

echo "完成: build/$APK"
