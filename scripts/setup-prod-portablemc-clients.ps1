param(
    [string] $PortableRoot = 'run\portablemc',
    [string] $ServerRuntimeDir = 'run\arclight-server',
    [string] $PythonVenvDir = '.venv-portablemc'
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$portableRootPath = if ([System.IO.Path]::IsPathRooted($PortableRoot)) { $PortableRoot } else { Join-Path $root $PortableRoot }
$serverRuntimePath = if ([System.IO.Path]::IsPathRooted($ServerRuntimeDir)) { $ServerRuntimeDir } else { Join-Path $root $ServerRuntimeDir }
$venvPath = if ([System.IO.Path]::IsPathRooted($PythonVenvDir)) { $PythonVenvDir } else { Join-Path $root $PythonVenvDir }

function Copy-DirectoryContents {
    param(
        [string] $Source,
        [string] $Destination
    )

    if (-not (Test-Path -LiteralPath $Source)) {
        throw "Missing source directory: $Source"
    }

    if (Test-Path -LiteralPath $Destination) {
        Remove-Item -LiteralPath $Destination -Recurse -Force
    }

    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    Get-ChildItem -LiteralPath $Source -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $Destination -Recurse -Force
    }
}

function Ensure-PortableMc {
    $python = Join-Path $venvPath 'Scripts\python.exe'
    $portablemc = Join-Path $venvPath 'Scripts\portablemc.exe'

    if (-not (Test-Path -LiteralPath $python)) {
        Write-Host "Creating Python venv: $venvPath"
        py -m venv $venvPath
    }

    if (-not (Test-Path -LiteralPath $portablemc)) {
        Write-Host 'Installing portablemc into local venv...'
        & $python -m pip install portablemc
    }

    if (-not (Test-Path -LiteralPath $portablemc)) {
        throw "portablemc was not installed: $portablemc"
    }

    return $portablemc
}

function Write-PortableClient {
    param(
        [string] $ClientId,
        [string] $PvPRatingJar
    )

    $clientDir = Join-Path $portableRootPath $ClientId
    $modsDir = Join-Path $clientDir 'mods'
    $configDir = Join-Path $clientDir 'config'

    New-Item -ItemType Directory -Force -Path $clientDir, $modsDir, $configDir | Out-Null

    Copy-DirectoryContents -Source (Join-Path $serverRuntimePath 'mods') -Destination $modsDir
    Copy-DirectoryContents -Source (Join-Path $serverRuntimePath 'config') -Destination $configDir

    Get-ChildItem -LiteralPath $modsDir -Filter 'pvprating-*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force
    Copy-Item -LiteralPath $PvPRatingJar -Destination (Join-Path $modsDir (Split-Path -Leaf $PvPRatingJar)) -Force

    Write-Host "Prepared portablemc client: $ClientId"
}

if (-not (Test-Path -LiteralPath (Join-Path $serverRuntimePath 'mods'))) {
    throw "Missing server mods directory: $(Join-Path $serverRuntimePath 'mods')"
}

if (-not (Test-Path -LiteralPath (Join-Path $serverRuntimePath 'config'))) {
    throw "Missing server config directory: $(Join-Path $serverRuntimePath 'config')"
}

Ensure-PortableMc | Out-Null

Write-Host 'Building PvPRating jar...'
& (Join-Path $root 'gradlew.bat') build
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed with exit code $LASTEXITCODE."
}

$builtJar = Get-ChildItem -LiteralPath (Join-Path $root 'build\libs') -Filter 'pvprating-*.jar' |
    Where-Object { $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-dev.jar' -and $_.Name -like '*&*' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if ($null -eq $builtJar) {
    $builtJar = Get-ChildItem -LiteralPath (Join-Path $root 'build\libs') -Filter 'pvprating-*.jar' |
        Where-Object { $_.Name -notlike '*-sources.jar' -and $_.Name -notlike '*-dev.jar' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

if ($null -eq $builtJar) {
    throw 'Could not find built PvPRating jar in build\libs.'
}

New-Item -ItemType Directory -Force -Path (Join-Path $portableRootPath 'main') | Out-Null

Write-PortableClient -ClientId 'main_client' -PvPRatingJar $builtJar.FullName
Write-PortableClient -ClientId 'off_client' -PvPRatingJar $builtJar.FullName

Write-Host "portablemc root: $portableRootPath"
Write-Host 'Production portablemc clients are prepared.'
