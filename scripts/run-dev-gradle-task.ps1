param(
    [Parameter(Mandatory = $true)]
    [string] $Task,

    [int] $DelaySeconds = 0,

    [switch] $WaitForServer,

    [int] $ServerPort = 25565,

    [int] $ServerTimeoutSeconds = 180,

    [ValidateSet('None', 'Left', 'Right')]
    [string] $WindowPlacement = 'None',

    [int] $WindowOrdinal = 1
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $root

$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'

if ($DelaySeconds -gt 0) {
    Write-Host "Waiting $DelaySeconds seconds before starting $Task..."
    Start-Sleep -Seconds $DelaySeconds
}

if ($WaitForServer) {
    $deadline = (Get-Date).AddSeconds($ServerTimeoutSeconds)
    Write-Host "Waiting for localhost:$ServerPort before starting $Task..."

    while ((Get-Date) -lt $deadline) {
        $client = [System.Net.Sockets.TcpClient]::new()
        try {
            $connect = $client.BeginConnect('127.0.0.1', $ServerPort, $null, $null)
            if ($connect.AsyncWaitHandle.WaitOne(1000) -and $client.Connected) {
                $client.EndConnect($connect)
                Write-Host "Server is listening on localhost:$ServerPort."
                break
            }
        } catch {
        } finally {
            $client.Close()
        }

        Start-Sleep -Seconds 2
    }

    if ((Get-Date) -ge $deadline) {
        throw "Server did not start listening on localhost:$ServerPort within $ServerTimeoutSeconds seconds. Not starting $Task."
    }
}

if ($Task -eq 'server') {
    & (Join-Path $PSScriptRoot 'prepare-dev-towny.ps1')
}

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

gradle $Task
