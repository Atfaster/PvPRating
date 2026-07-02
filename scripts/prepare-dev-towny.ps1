param(
    [string] $ServerDir,
    [switch] $OverwriteConfigs
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$sourceDir = Join-Path $root 'libs\towny'
$serverRoot = if ($ServerDir) {
    if ([System.IO.Path]::IsPathRooted($ServerDir)) { $ServerDir } else { Join-Path $root $ServerDir }
} else {
    Join-Path $root 'run\server'
}
$pluginsDir = Join-Path $serverRoot 'plugins'
$townySettingsSource = Join-Path $root 'server-reference\Towny\settings'
$townySettingsTarget = Join-Path $pluginsDir 'Towny\settings'

$pluginJars = @(
    'Towny-0.101.2.0.jar',
    'TownyChat-0.119.jar',
    'FlagWar-0.7.0.jar'
)
$managedPluginPrefixes = @('Towny', 'TownyChat', 'FlagWar')

if (-not (Test-Path -LiteralPath $sourceDir)) {
    throw "Missing Towny plugin source directory: $sourceDir"
}

New-Item -ItemType Directory -Force -Path $pluginsDir | Out-Null

foreach ($pluginPrefix in $managedPluginPrefixes) {
    Get-ChildItem -LiteralPath $pluginsDir -Filter "$pluginPrefix-*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $pluginJars -notcontains $_.Name } |
        ForEach-Object {
            Remove-Item -LiteralPath $_.FullName -Force
            Write-Host "Removed stale dev plugin: $($_.Name)"
        }
}

foreach ($pluginJar in $pluginJars) {
    $source = Join-Path $sourceDir $pluginJar
    if (-not (Test-Path -LiteralPath $source)) {
        throw "Missing Towny plugin jar: $source"
    }

    Copy-Item -LiteralPath $source -Destination (Join-Path $pluginsDir $pluginJar) -Force
    Write-Host "Prepared dev plugin: $pluginJar"
}

if (Test-Path -LiteralPath $townySettingsSource) {
    New-Item -ItemType Directory -Force -Path $townySettingsTarget | Out-Null

    foreach ($configName in @('config.yml', 'townyperms.yml')) {
        $source = Join-Path $townySettingsSource $configName
        $target = Join-Path $townySettingsTarget $configName

        if (-not (Test-Path -LiteralPath $source)) {
            continue
        }

        if ($OverwriteConfigs -or -not (Test-Path -LiteralPath $target)) {
            Copy-Item -LiteralPath $source -Destination $target -Force
            Write-Host "Prepared dev Towny config: $configName"
        }
    }
}

$townyConfigTarget = Join-Path $townySettingsTarget 'config.yml'
if (Test-Path -LiteralPath $townyConfigTarget) {
    $config = Get-Content -Raw -LiteralPath $townyConfigTarget
    $updatedConfig = $config -replace "(?m)^(\s*using_economy:\s*)'.*'", "`${1}'false'"

    if ($updatedConfig -ne $config) {
        Set-Content -LiteralPath $townyConfigTarget -Value $updatedConfig -Encoding UTF8
        Write-Host "Disabled Towny economy for dev config."
    }
}

Write-Host "Towny dev plugins are ready in $pluginsDir"
