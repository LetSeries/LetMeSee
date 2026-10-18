import java.util.Collections
import java.util.zip.ZipFile

plugins {
    java
}

group = "com.letmesee"
version = "1.2.3"

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

// 打包产物必须包含运行时需要的资源；漏掉 config.yml 时让构建直接失败，
// 而不是等服主安装后才发现默认配置缺失。
val verifyJarContents by tasks.registering {
    val jarTask = tasks.named<Jar>("jar")
    dependsOn(jarTask)
    val archive = jarTask.flatMap { it.archiveFile }
    inputs.file(archive)
    doLast {
        val required = listOf("plugin.yml", "config.yml")
        val entries = ZipFile(archive.get().asFile).use { zip ->
            Collections.list(zip.entries()).map { it.name }
        }
        required.forEach { entry ->
            check(entries.contains(entry)) { "打包产物缺少必需资源: $entry" }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyJarContents)
}
