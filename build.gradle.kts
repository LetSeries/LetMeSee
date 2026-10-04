plugins {
    java
}

group = "com.letmesee"
version = "1.0.9"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // 锁定时间戳快照，保证构建可复现（与 build.ps1 保持一致）
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-20250328.161643-128")

    // compileOnly 不会传递给测试，测试需要显式声明 API
    testCompileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-20250328.161643-128")
    testRuntimeOnly("io.papermc.paper:paper-api:1.21.1-R0.1-20250328.161643-128")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(mapOf("version" to project.version, "name" to rootProject.name))
        }
    }
}
