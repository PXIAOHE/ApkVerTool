plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // apksig 是纯 Java 单 jar（已核实不含 kotlin），沿用工程自带的 9.5.0-alpha08，
    // 与既有出货链路的签名 / 验签行为保持完全一致。
    api(files(rootProject.file("libs/apksig.jar")))
}
