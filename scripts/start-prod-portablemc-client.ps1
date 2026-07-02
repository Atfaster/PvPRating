param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('main_client', 'off_client')]
    [string] $ClientId,

    [Parameter(Mandatory = $true)]
    [string] $Username,

    [string] $PortableRoot = 'run\portablemc',
    [string] $PythonVenvDir = '.venv-portablemc',
    [string] $JavaExe = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\javaw.exe',
    [string] $ForgeVersion = '1.20.1-47.4.18',
    [switch] $WaitForServer,
    [int] $ServerPort = 25565,
    [int] $ServerTimeoutSeconds = 240,
    [int] $DelaySeconds = 0,

    [ValidateSet('None', 'Left', 'Right')]
    [string] $WindowPlacement = 'None',

    [int] $WindowOrdinal = 1
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$portableRootPath = if ([System.IO.Path]::IsPathRooted($PortableRoot)) { $PortableRoot } else { Join-Path $root $PortableRoot }
$venvPath = if ([System.IO.Path]::IsPathRooted($PythonVenvDir)) { $PythonVenvDir } else { Join-Path $root $PythonVenvDir }
$portablemc = Join-Path $venvPath 'Scripts\portablemc.exe'
$mainDir = Join-Path $portableRootPath 'main'
$workDir = Join-Path $portableRootPath $ClientId

function Wait-ForTcpServer {
    param(
        [string] $HostName,
        [int] $Port,
        [int] $TimeoutSeconds
    )

    Write-Host "Waiting for ${HostName}:${Port}..."
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        $client = [System.Net.Sockets.TcpClient]::new()
        try {
            $connect = $client.BeginConnect($HostName, $Port, $null, $null)
            if ($connect.AsyncWaitHandle.WaitOne(1000) -and $client.Connected) {
                $client.EndConnect($connect)
                Write-Host "Server is listening on ${HostName}:${Port}."
                return
            }
        } catch {
        } finally {
            $client.Close()
        }

        Start-Sleep -Seconds 2
    }

    throw "Server did not start listening on ${HostName}:${Port} within $TimeoutSeconds seconds."
}

if ($DelaySeconds -gt 0) {
    Write-Host "Waiting $DelaySeconds seconds before starting $Username..."
    Start-Sleep -Seconds $DelaySeconds
}

if ($WaitForServer) {
    Wait-ForTcpServer -HostName '127.0.0.1' -Port $ServerPort -TimeoutSeconds $ServerTimeoutSeconds
}

if (-not (Test-Path -LiteralPath $portablemc)) {
    throw "portablemc was not found: $portablemc. Run scripts\setup-prod-portablemc-clients.ps1 first."
}

if (-not (Test-Path -LiteralPath $JavaExe)) {
    throw "Java executable was not found: $JavaExe"
}

if (-not (Test-Path -LiteralPath (Join-Path $workDir 'mods'))) {
    throw "Missing portablemc client directory: $workDir. Run scripts\setup-prod-portablemc-clients.ps1 first."
}

$jvmArgs = '-Xms1024m -Xmx4096m -XX:+UseG1GC -Dfile.encoding=UTF-8'

Write-Host "Starting portablemc Forge client: $Username"
Write-Host "Work dir: $workDir"

if ($WindowPlacement -ne 'None') {
    Start-Process `
        -FilePath 'powershell.exe' `
        -WindowStyle Hidden `
        -ArgumentList @(
            '-NoProfile',
            '-ExecutionPolicy',
            'Bypass',
            '-File',
            (Join-Path $PSScriptRoot 'position-minecraft-window.ps1'),
            '-Placement',
            $WindowPlacement,
            '-WindowOrdinal',
            $WindowOrdinal
        )
}

& $portablemc `
    --main-dir $mainDir `
    --work-dir $workDir `
    start `
    --jvm $JavaExe `
    --jvm-args $jvmArgs `
    --resolution '1280x720' `
    --username $Username `
    --server '127.0.0.1' `
    --server-port $ServerPort `
    "forge:$ForgeVersion"
