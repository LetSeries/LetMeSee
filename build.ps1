param([switch]$Clean)

$ErrorActionPreference = "Stop"
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

$Version = "1.0.2"
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$BuildDir = Join-Path $ProjectRoot "build"
$ClassesDir = Join-Path $BuildDir "classes"
$LibsDir = Join-Path $BuildDir "libs"
$DepsDir = Join-Path $BuildDir "deps"
$TargetJar = Join-Path $LibsDir "letmesee-$Version.jar"

if ($Clean -and (Test-Path $BuildDir)) {
    Remove-Item -LiteralPath $BuildDir -Recurse -Force
}
@($ClassesDir, $LibsDir, $DepsDir) | ForEach-Object {
    New-Item -ItemType Directory -Path $_ -Force | Out-Null
}

function Download-Jar {
    param($GroupId, $ArtifactId, $Version, $DirVersion)
    if (-not $DirVersion) { $DirVersion = $Version }

    $groupIdPath = $GroupId.Replace('.', '/')
    $jarName = "$ArtifactId-$Version.jar"
    $jarPath = Join-Path $DepsDir $jarName
    if (Test-Path $jarPath) { return $jarPath }

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
            return $jarPath
        } catch { Write-Host "    (failed from $url)" }
    }
    throw "Failed to download $jarName"
}

Write-Host "=== Downloading dependencies ==="
$jars = @()

# Paper API (SNAPSHOT - file uses timestamp, directory uses SNAPSHOT)
$jars += Download-Jar "io.papermc.paper" "paper-api" "1.21.1-R0.1-20250328.161643-128" "1.21.1-R0.1-SNAPSHOT"

# Direct compile dependencies
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
$classpath = ($jars | Select-Object -Unique) -join ";"
$srcDir = Join-Path $ProjectRoot "src\main\java"
$javaFiles = Get-ChildItem -Path $srcDir -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }

& javac -d $ClassesDir -cp $classpath -Xlint:-deprecation --release 21 @javaFiles 2>&1
if ($LASTEXITCODE -ne 0) { Write-Host "Compilation failed!" -ForegroundColor Red; exit 1 }
Write-Host "Compilation successful!" -ForegroundColor Green

Write-Host "=== Packaging ==="
$pluginYml = [System.IO.File]::ReadAllText((Join-Path $ProjectRoot "src\main\resources\plugin.yml"))
$pluginYml = $pluginYml.Replace('${version}', $Version)
$utf8 = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText((Join-Path $ClassesDir "plugin.yml"), $pluginYml, $utf8)
Push-Location $ClassesDir
try {
    jar -cf $TargetJar *
    Write-Host "Plugin JAR created: $TargetJar" -ForegroundColor Green
    Write-Host "Size: $((Get-Item $TargetJar).Length / 1KB) KB"
} finally { Pop-Location }
Write-Host "=== Done ===" -ForegroundColor Green