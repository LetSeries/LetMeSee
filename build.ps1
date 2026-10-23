param([switch]$Clean)

$ErrorActionPreference = "Stop"
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
# 版本号唯一来源：build.gradle.kts，避免两处手动同步遗漏
$VersionLine = Select-String -Path (Join-Path $ProjectRoot "build.gradle.kts") -Pattern '^version = "([^"]+)"' | Select-Object -First 1
if (-not $VersionLine) { throw "Cannot find version in build.gradle.kts" }
$Version = $VersionLine.Matches[0].Groups[1].Value
$BuildDir = Join-Path $ProjectRoot "build"
$ClassesDir = Join-Path $BuildDir "classes"
$LibsDir = Join-Path $BuildDir "libs"
$DepsDir = Join-Path $BuildDir "deps"
$TargetJar = Join-Path $LibsDir "letmesee-$Version.jar"

if ($Clean) {
    # 只清理编译产物，保留 deps 缓存，避免 -Clean 触发整包重新下载
    foreach ($dir in @($ClassesDir, $LibsDir)) {
        if (Test-Path $dir) { Remove-Item -LiteralPath $dir -Recurse -Force }
    }
}
# 每次都重建 classes：增量 javac 不会删除已删源文件留下的 .class，
# 否则它们会被打进 jar，产出与 Gradle 构建不一致的旧类。
if (Test-Path $ClassesDir) { Remove-Item -LiteralPath $ClassesDir -Recurse -Force }
@($ClassesDir, $LibsDir, $DepsDir) | ForEach-Object {
    New-Item -ItemType Directory -Path $_ -Force | Out-Null
}

function Test-JarFile {
    param($Path)
    try {
        $stream = [System.IO.File]::OpenRead($Path)
        try {
            $header = New-Object byte[] 2
            if ($stream.Read($header, 0, 2) -ne 2) { return $false }
            # ZIP/JAR 魔数 "PK"
            return ($header[0] -eq 0x50 -and $header[1] -eq 0x4B)
        } finally { $stream.Dispose() }
    } catch { return $false }
}

function Download-Jar {
    param($GroupId, $ArtifactId, $Version, $DirVersion)
    if (-not $DirVersion) { $DirVersion = $Version }

    $groupIdPath = $GroupId.Replace('.', '/')
    $jarName = "$ArtifactId-$Version.jar"
    $jarPath = Join-Path $DepsDir $jarName
    # 缓存命中也校验：中断下载会留下带 "PK" 魔数的半截文件，
    # 若不再校验就会一直拿坏包去编译
    if (Test-Path $jarPath) {
        if (Test-JarFile $jarPath) { return $jarPath }
        Write-Host "  Cached $jarName is corrupt, re-downloading ..."
        Remove-Item -LiteralPath $jarPath -Force
    }

    # Try PaperMC repo first, then Maven Central
    $urls = @(
        "https://repo.papermc.io/repository/maven-public/$groupIdPath/$ArtifactId/$DirVersion/$jarName",
        "https://repo1.maven.org/maven2/$groupIdPath/$ArtifactId/$DirVersion/$jarName"
    )
    foreach ($url in $urls) {
        try {
            Write-Host "  Downloading $jarName ..."
            $wc = New-Object System.Net.WebClient
            $wc.DownloadFile($url, $jarPath)
            if (-not (Test-JarFile $jarPath)) {
                Remove-Item -LiteralPath $jarPath -Force
                Write-Host "    (not a valid JAR from $url)"
                continue
            }
            return $jarPath
        } catch {
            # 半截文件必须删掉，否则下次运行会当成有效缓存
            if (Test-Path $jarPath) { Remove-Item -LiteralPath $jarPath -Force -ErrorAction SilentlyContinue }
            Write-Host "    (failed from $url)"
        }
    }
    throw "Failed to download $jarName"
}

# 手工构建产物不含 bStats（无 shade 重定位能力），统计会在运行时自动跳过；
# 正式发布请使用 gradle build / CI 产物。
Write-Host "=== Downloading dependencies ==="
$jars = @()

# Paper API (SNAPSHOT - file uses timestamp, directory uses SNAPSHOT)
$jars += Download-Jar "io.papermc.paper" "paper-api" "1.21.1-R0.1-20250328.161643-128" "1.21.1-R0.1-SNAPSHOT"

# Direct compile dependencies
# bStats 分两个 artifact：bukkit 是入口类，base 是实现类，编译期两者都要在 classpath 上
$jars += Download-Jar "org.bstats" "bstats-bukkit" "3.2.1"
$jars += Download-Jar "org.bstats" "bstats-base" "3.2.1"
$jars += Download-Jar "com.mojang" "brigadier" "1.2.9"
$jars += Download-Jar "com.google.guava" "guava" "32.1.2-jre"
$jars += Download-Jar "com.google.code.gson" "gson" "2.10.1"
$jars += Download-Jar "net.md-5" "bungeecord-chat" "1.20-R0.2-deprecated+build.18"
$jars += Download-Jar "org.yaml" "snakeyaml" "2.2"
$jars += Download-Jar "org.joml" "joml" "1.10.5"
$jars += Download-Jar "com.googlecode.json-simple" "json-simple" "1.1.1"
$jars += Download-Jar "it.unimi.dsi" "fastutil" "8.5.6"
$jars += Download-Jar "org.apache.logging.log4j" "log4j-api" "2.17.1"
$jars += Download-Jar "org.slf4j" "slf4j-api" "2.0.9"
$jars += Download-Jar "org.jspecify" "jspecify" "1.0.0"
$jars += Download-Jar "org.checkerframework" "checker-qual" "3.33.0"

# Adventure libraries (from adventure-bom 4.17.0)
$jars += Download-Jar "net.kyori" "adventure-api" "4.17.0"
$jars += Download-Jar "net.kyori" "adventure-key" "4.17.0"
$jars += Download-Jar "net.kyori" "adventure-nbt" "4.17.0"
$jars += Download-Jar "net.kyori" "adventure-text-minimessage" "4.17.0"
$jars += Download-Jar "net.kyori" "adventure-text-serializer-gson" "4.17.0"
$jars += Download-Jar "net.kyori" "adventure-text-serializer-legacy" "4.17.0"
$jars += Download-Jar "net.kyori" "adventure-text-serializer-plain" "4.17.0"
$jars += Download-Jar "net.kyori" "adventure-text-logger-slf4j" "4.17.0"
$jars += Download-Jar "net.kyori" "examination-api" "1.3.0"
$jars += Download-Jar "net.kyori" "examination-string" "1.3.0"

Write-Host "=== Compiling ==="
# 分隔符按平台取，脚本可在 Linux/macOS 的 pwsh 下运行
$classpath = ($jars | Select-Object -Unique) -join [IO.Path]::PathSeparator
$srcDir = Join-Path $ProjectRoot (Join-Path "src" (Join-Path "main" "java"))
$javaFiles = Get-ChildItem -Path $srcDir -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }

# -g 与 Gradle 的调试信息保持一致，-encoding 固定 UTF-8（源码含大量中文提示语）
& javac -d $ClassesDir -cp $classpath -g -encoding UTF-8 -Xlint:-deprecation --release 21 @javaFiles 2>&1
if ($LASTEXITCODE -ne 0) { Write-Host "Compilation failed!" -ForegroundColor Red; exit 1 }
Write-Host "Compilation successful!" -ForegroundColor Green

Write-Host "=== Packaging ==="
$ResourcesDir = Join-Path $ProjectRoot "src\main\resources"
Copy-Item -Path (Join-Path $ResourcesDir "*") -Destination $ClassesDir -Recurse -Force

$pluginYml = [System.IO.File]::ReadAllText((Join-Path $ResourcesDir "plugin.yml"))
$pluginYml = $pluginYml.Replace('${version}', $Version)
$utf8 = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText((Join-Path $ClassesDir "plugin.yml"), $pluginYml, $utf8)
Push-Location $ClassesDir
try {
    jar -cf $TargetJar *
    $jarEntries = & jar -tf $TargetJar
    foreach ($requiredEntry in @("plugin.yml", "config.yml")) {
        if ($jarEntries -notcontains $requiredEntry) {
            throw "Packaged JAR is missing required resource: $requiredEntry"
        }
    }
    Write-Host "Plugin JAR created: $TargetJar" -ForegroundColor Green
    Write-Host "Size: $((Get-Item $TargetJar).Length / 1KB) KB"
} finally { Pop-Location }
Write-Host "=== Done ===" -ForegroundColor Green