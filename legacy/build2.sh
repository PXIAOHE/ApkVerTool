#! /bin/sh
# ApkVerTool 构建脚本（路线 A：真把 miuix / Compose 编进来）
# 与 build.sh 的区别：
#   * 底栏用 miuix 官方 Compose 实现 → 需要 kotlinc + Compose 编译器插件
#   * 依赖闭包由 deps_fetch3.py 解析下载（Gradle 不在，自己按 POM 建图）
#   * 资源链换成 aapt2（手搓 AXML / 584 字节 arsc 退役），并生成各库的 R 类
#   * 多 dex 由 D8 输出，pack2.py 组包
# 用法: sh build2.sh            （全量）
#       sh build2.sh fast       （跳过依赖解析，复用 aar/）
set -e
cd "$(dirname "$0")"

FAST=""
[ "$1" = "fast" ] && FAST=1

# 类路径分隔符：Windows 上的 java.exe 只认 ';'，Git Bash 不会替你把 ':' 转过来
case "$(uname -s 2>/dev/null)" in
    MINGW*|MSYS*|CYGWIN*|Windows*) SEP=';' ;;
    *)                             SEP=':' ;;
esac

# Git Bash 的 MSYS 层会把"看着像 POSIX 路径列表"的参数重新改写（`D:/x/y.jar` 会被
# 拆成 `D;/x/y.jar`），于是 kotlinc 的 -classpath 里凭空多出一个不存在的 "D"。
# 本脚本的参数要么是 Windows 风格路径、要么是相对路径，关掉自动转换最稳。
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

VER=$(python3 version.py name)
CODE=$(python3 version.py code)
echo "== ApkVerTool v$VER (code $CODE) — Compose/miuix 链路 =="

mkdir -p build/appclasses build/ktclasses build/cli build/dexall

echo "[0/8] version.py → AvtVersion.java / AvtChangelog.java"
python3 version.py genjava src/com/seagull/avt/app

if [ -z "$FAST" ] || [ ! -d aar ] || [ -z "$(ls aar 2>/dev/null)" ]; then
    echo "[1/8] 解析并下载依赖闭包（miuix + androidx Compose）"
    python3 deps_fetch3.py
else
    echo "[1/8] 复用已有依赖闭包（aar/）"
fi

echo "[2/8] 合并库 jar（按版本高者优先做类级去重）"
python3 merge_jars.py

echo "[2b/8] 生成应用图标 res/mipmap/ic_launcher.png（工程只存生成器，不存 PNG）"
if [ ! -f res/mipmap/ic_launcher.png ]; then
    python3 mkicon.py build >/dev/null
    mkdir -p res/mipmap
    cp build/res/mipmap/ic_launcher.png res/mipmap/ic_launcher.png
fi
ls -l res/mipmap/ic_launcher.png

echo "[3/8] 资源链：aapt2 compile + link（含各库 R 类）"
python3 mkres.py

ANDROID_JAR=$(cat tools/android-jar.txt)
# kotlinc 自身的运行类路径：显式列出，避免 kotlin-compose-compiler-plugin 混进 classpath
# （它只该走 -Xplugin 加载）。原来的 tools/kotlinc-cp.txt 写死了 POSIX 分隔符，换平台会废。
KOTLINC_CP="tools/kotlin-compiler-embeddable-2.4.20.jar${SEP}tools/kotlin-build-tools-api-2.4.20.jar${SEP}tools/kotlin-stdlib-2.4.20.jar${SEP}tools/kotlin-script-runtime-2.4.20.jar${SEP}tools/kotlin-reflect-1.6.10.jar${SEP}tools/kotlin-daemon-embeddable-2.4.20.jar${SEP}tools/kotlinx-coroutines-core-jvm-1.11.0.jar${SEP}tools/annotations-26.0.2.jar"
LIBS_CP="tools/kotlin-stdlib-2.4.20.jar${SEP}tools/annotations-26.0.2.jar${SEP}tools/kotlinx-coroutines-core-jvm-1.11.0.jar${SEP}$(ls tools/kotlinx-coroutines-android-*.jar | tr '\n' "$SEP")$ANDROID_JAR${SEP}$(ls aar/*.jar | tr '\n' "$SEP")"

# bar 包是纯 Java（LiquidGlassView / GlassShaders），Kotlin 侧要引用它，
# 所以必须先单独编出来塞进 kotlinc 的 classpath（kotlinc 跑在 javac 之前）
echo "[3b/8] javac 编译 bar 包（液态玻璃 View + AGSL 着色器）"
rm -rf build/barclasses && mkdir -p build/barclasses
javac --release 11 -nowarn -encoding UTF-8 \
    -cp "$LIBS_CP" \
    -d build/barclasses \
    src/com/seagull/avt/bar/*.java
jar cf build/bar.jar -C build/barclasses .

echo "[4/8] kotlinc 编译 Compose 底栏（miuix NavigationBar / FloatingNavigationBar / 液态玻璃）"
rm -rf build/ktclasses && mkdir -p build/ktclasses
java -cp "$KOTLINC_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
    -no-stdlib -jvm-target 11 \
    -Xplugin=tools/kotlin-compose-compiler-plugin-embeddable-2.4.20.jar \
    -classpath "build/barclasses${SEP}$LIBS_CP" -d build/ktclasses src-kotlin/com/seagull/avt/bar/*.kt

echo "[5/8] javac 编译 App（含 aapt2 生成的 R 类）"
rm -rf build/appclasses && mkdir -p build/appclasses
javac --release 11 -nowarn -encoding UTF-8 \
    -cp "libs/android28.jar${SEP}libs/apksig.jar${SEP}build/ktclasses${SEP}build/barclasses${SEP}$LIBS_CP" \
    -d build/appclasses \
    $(find build/gen -name "R.java") \
    src/com/seagull/avt/core/*.java src/com/seagull/avt/app/*.java

echo "[6/8] D8 打多 dex（库代码只 D8 不 R8：Compose × R8 是崩溃重灾区）"
rm -rf build/dexall && mkdir -p build/dexall
jar cf build/app.jar -C build/appclasses .
jar cf build/kt.jar -C build/ktclasses .
java -Xmx3g -cp libs/r8.jar com.android.tools.r8.D8 --release --min-api 26 \
    --lib libs/android.jar --lib libs/android28.jar --lib "$ANDROID_JAR" \
    --output build/dexall build/app.jar build/kt.jar build/bar.jar build/libs-merged.jar
ls -la build/dexall | tail -n +2

echo "[7/8] 编译 CLI（自检与签名用）"
rm -rf build/cli && mkdir -p build/cli
javac --release 8 -nowarn -encoding UTF-8 -cp libs/apksig.jar -d build/cli \
    src/com/seagull/avt/core/*.java src/com/seagull/avt/cli/AvtCli.java

echo "[7b/8] 提取 native 库（aar 里的 jni/*.so，deps_fetch3 只取 classes.jar 会漏）"
python3 extract_jni.py

echo "[8/8] 组包 + 对齐 + 签名"
python3 pack2.py

echo "完成: build/ApkVerTool-v$VER.apk"
