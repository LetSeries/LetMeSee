import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.Collections
import java.util.zip.ZipFile

plugins {
    java
    // 等价于 Maven 的 maven-shade-plugin：把 bStats 打包进插件 jar 并重定位包名。
    // 用 goooler 维护的 shadow fork——原版 8.1.1 已停更，内置 ASM 不认识 Java 21 的
    // class 文件（major 65），重定位时会直接抛 Unsupported class file major version。
    id("io.github.goooler.shadow") version "8.1.8"
}

group = "com.letmesee"
version = "1.2.5"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // 锁定时间戳快照，保证构建可复现（与 build.ps1 保持一致）
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-20250328.161643-128")

    // bStats 匿名统计：implementation 会被 shadowJar 打进产物并重定位
    implementation("org.bstats:bstats-bukkit:3.2.1")

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

// 源码里有大量中文提示语，显式固定编码，避免受运行环境默认编码影响
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// 阴影 jar 即发布产物：bStats 被重定位进自己的包，避免与其它插件的 bStats 冲突或重复统计
tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier = ""
    relocate("org.bstats", "com.letmesee.libs.bstats")
}

// 普通 jar 不再单独产出，build/libs 里只留最终产物，Release/CI 的「恰好一个 jar」校验才成立
tasks.jar {
    enabled = false
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
// 而不是等服主安装后才发现默认配置缺失。同时校验 bStats 的重定位真的发生了。
val verifyJarContents by tasks.registering {
    val jarTask = tasks.named<ShadowJar>("shadowJar")
    dependsOn(jarTask)
    val archive = jarTask.flatMap { it.archiveFile }
    inputs.file(archive)
    doLast {
        val entries = ZipFile(archive.get().asFile).use { zip ->
            Collections.list(zip.entries()).map { it.name }
        }
        listOf("plugin.yml", "config.yml").forEach { entry ->
            check(entries.contains(entry)) { "打包产物缺少必需资源: $entry" }
        }
        check(entries.none { it.startsWith("org/bstats/") }) {
            "bStats 未重定位：产物中仍有 org/bstats/（会与其它插件冲突并重复统计）"
        }
        check(entries.any { it.startsWith("com/letmesee/libs/bstats/") }) {
            "bStats 重定位包缺失: com/letmesee/libs/bstats/"
        }
    }
}

tasks.named("check") {
    dependsOn(verifyJarContents)
}

// build.ps1 自己维护了一份依赖清单，最容易漂移的是 paper-api 版本号：
// 两处不一致会「编译通过但对着另一套 API」。这里强制两边保持同步。
val verifyDependencyPins by tasks.registering {
    val gradleFile = layout.projectDirectory.file("build.gradle.kts").asFile
    val scriptFile = layout.projectDirectory.file("build.ps1").asFile
    inputs.files(gradleFile, scriptFile)
    doLast {
        val pin = Regex("""paper-api:([0-9][^"')\s]*)""")
            .find(gradleFile.readText())?.groupValues?.get(1)
            ?: error("无法从 build.gradle.kts 解析 paper-api 版本")
        check(scriptFile.readText().contains("\"$pin\"")) {
            "build.ps1 中的 paper-api 版本与 build.gradle.kts 不一致（应为 $pin）"
        }
    }
}

tasks.named("check") {
    dependsOn(verifyDependencyPins)
}
