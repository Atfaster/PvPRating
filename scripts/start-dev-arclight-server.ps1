param(
    [string] $RuntimeDir = 'run\arclight-server',
    [string] $ServerJar = 'local-runtime\arclight\server.jar',
    [string] $JavaExe = ''
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$runtimePath = if ([System.IO.Path]::IsPathRooted($RuntimeDir)) { $RuntimeDir } else { Join-Path $root $RuntimeDir }
$serverJarPath = if ([System.IO.Path]::IsPathRooted($ServerJar)) { $ServerJar } else { Join-Path $root $ServerJar }
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'

function Get-Java21Exe {
    param([string] $ConfiguredJavaExe)

    $candidates = @()

    if ($ConfiguredJavaExe) {
        $candidates += $ConfiguredJavaExe
    }

    if ($env:JAVA21_HOME) {
        $candidates += (Join-Path $env:JAVA21_HOME 'bin\java.exe')
    }

    $candidates += Get-ChildItem -Path 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'bin\java.exe' }

    $candidates += Get-ChildItem -Path (Join-Path $env:USERPROFILE '.vscode\extensions') -Directory -Filter 'redhat.java-*' -ErrorAction SilentlyContinue |
        ForEach-Object {
            Get-ChildItem -Path (Join-Path $_.FullName 'jre') -Directory -Filter '21*' -ErrorAction SilentlyContinue |
                ForEach-Object { Join-Path $_.FullName 'bin\java.exe' }
        }

    foreach ($candidate in $candidates) {
        if (-not $candidate -or -not (Test-Path -LiteralPath $candidate)) {
            continue
        }

        $processInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $processInfo.FileName = $candidate
        $processInfo.Arguments = '-version'
        $processInfo.RedirectStandardOutput = $true
        $processInfo.RedirectStandardError = $true
        $processInfo.UseShellExecute = $false

        $process = [System.Diagnostics.Process]::Start($processInfo)
        $versionOutput = $process.StandardOutput.ReadToEnd() + $process.StandardError.ReadToEnd()
        $process.WaitForExit()

        if ($versionOutput -match 'version "21\.') {
            return $candidate
        }
    }

    throw @"
Java 21 was not found.

Towny-0.101.2.0 requires Java 21. Install Temurin/OpenJDK 21 or set JAVA21_HOME to a Java 21 installation.
"@
}

$java21 = Get-Java21Exe -ConfiguredJavaExe $JavaExe

if (-not (Test-Path -LiteralPath $serverJarPath)) {
    throw @"
Missing Arclight server jar:
$serverJarPath

Copy the server jar from the real server or an Arclight 1.20.1 jar into:
local-runtime\arclight\server.jar
"@
}

New-Item -ItemType Directory -Force -Path $runtimePath | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $runtimePath 'mods') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $runtimePath 'plugins') | Out-Null

Set-Content -LiteralPath (Join-Path $runtimePath 'eula.txt') -Value 'eula=true' -Encoding ASCII

$serverPropertiesPath = Join-Path $runtimePath 'server.properties'
if (-not (Test-Path -LiteralPath $serverPropertiesPath)) {
    @(
        'server-port=25565',
        'online-mode=false',
        'enforce-secure-profile=false',
        'motd=PvPRating Arclight Dev',
        'allow-flight=true',
        'difficulty=normal',
        'gamemode=survival',
        'spawn-protection=0'
    ) | Set-Content -LiteralPath $serverPropertiesPath -Encoding ASCII
}

Write-Host 'Building PvPRating jar...'
& (Join-Path $root 'gradlew.bat') build
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed with exit code $LASTEXITCODE."
}

$builtJar = Get-ChildItem -LiteralPath (Join-Path $root 'build\libs') -Filter 'pvprating-*.jar' |
    Where-Object { $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-dev.jar' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if ($null -eq $builtJar) {
    throw 'Could not find built PvPRating jar in build\libs.'
}

$modsDir = Join-Path $runtimePath 'mods'
Get-ChildItem -LiteralPath $modsDir -Filter 'pvprating-*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force
Copy-Item -LiteralPath $builtJar.FullName -Destination (Join-Path $modsDir $builtJar.Name) -Force
Write-Host "Prepared dev mod: $($builtJar.Name)"

& (Join-Path $PSScriptRoot 'prepare-dev-towny.ps1') -ServerDir $runtimePath

Push-Location $runtimePath
try {
    Write-Host "Starting Arclight dev server from $runtimePath"
    Write-Host "Using Java: $java21"
    & $java21 -jar $serverJarPath nogui
} finally {
    Pop-Location
}
