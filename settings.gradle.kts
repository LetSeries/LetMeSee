pluginManagement {
    repositories {
        // Gradle Plugin Portal 在部分网络环境不可达（TLS 握手被切断），
        // 阿里云镜像是同一份内容的公开镜像；本地与 CI 都先走镜像，
        // 缺 artifact 时再回退官方 Portal。
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "letmesee"