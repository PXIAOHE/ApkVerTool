plugins {
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

application {
    mainClass.set("com.seagull.avt.cli.AvtCli")
    // Windows 控制台默认 GBK，CLI 输出全是中文，不钉死编码会乱码
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
}

dependencies {
    implementation(project(":core"))
}
