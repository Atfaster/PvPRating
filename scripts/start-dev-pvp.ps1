param(
    [int] $ClientDelaySeconds = 25,
    [switch] $ArclightServer
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$gradleUserHome = Join-Path $env:USERPROFILE '.gradle'

function Start-DevWindow {
    param(
        [string] $Title,
        [string] $GradleTask,
        [int] $DelaySeconds = 0,
        [switch] $WaitForServer,
        [int] $ServerTimeoutSeconds = 240,
        [ValidateSet('None', 'Left', 'Right')]
        [string] $WindowPlacement = 'None',
        [int] $WindowOrdinal = 1
    )

    $waitForServerBlock = ''
    if ($WaitForServer) {
        $waitForServerBlock = @"
Write-Host 'Waiting for localhost:25565 before starting $GradleTask...'
`$deadline = (Get-Date).AddSeconds($ServerTimeoutSeconds)
while ((Get-Date) -lt `$deadline) {
    `$client = [System.Net.Sockets.TcpClient]::new()
    try {
        `$connect = `$client.BeginConnect('127.0.0.1', 25565, `$null, `$null)
        if (`$connect.AsyncWaitHandle.WaitOne(1000) -and `$client.Connected) {
            `$client.EndConnect(`$connect)
            Write-Host 'Server is listening on localhost:25565.'
            break
        }
    } catch {
    } finally {
        `$client.Close()
    }
    Start-Sleep -Seconds 2
}
if ((Get-Date) -ge `$deadline) {
    throw 'Server did not start listening on localhost:25565. Not starting $GradleTask.'
}
"@
    }

    $command = @"
`$Host.UI.RawUI.WindowTitle = '$Title'
Set-Location -LiteralPath '$root'
`$env:GRADLE_USER_HOME = '$gradleUserHome'
if ($DelaySeconds -gt 0) {
    Write-Host 'Waiting $DelaySeconds seconds before starting $GradleTask...'
    Start-Sleep -Seconds $DelaySeconds
}
$waitForServerBlock
if ('$WindowPlacement' -ne 'None') {
    Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -ArgumentList @(
        '-NoProfile',
        '-ExecutionPolicy',
        'Bypass',
        '-File',
        '$PSScriptRoot\position-minecraft-window.ps1',
        '-Placement',
        '$WindowPlacement',
        '-WindowOrdinal',
        '$WindowOrdinal'
    )
}
gradle $GradleTask
"@

    if ($GradleTask -eq 'server') {
        $command = $command.Replace("gradle $GradleTask", "& '$PSScriptRoot\prepare-dev-towny.ps1'`r`ngradle $GradleTask")
    }

    Start-Process `
        -FilePath 'powershell.exe' `
        -WorkingDirectory $root `
        -ArgumentList @('-NoExit', '-ExecutionPolicy', 'Bypass', '-Command', $command)
}

if ($ArclightServer) {
    $serverCommand = @"
`$Host.UI.RawUI.WindowTitle = 'PvPRating Arclight Server'
Set-Location -LiteralPath '$root'
`$env:GRADLE_USER_HOME = '$gradleUserHome'
& '$PSScriptRoot\start-dev-arclight-server.ps1'
"@

    Start-Process `
        -FilePath 'powershell.exe' `
        -WorkingDirectory $root `
        -ArgumentList @('-NoExit', '-ExecutionPolicy', 'Bypass', '-Command', $serverCommand)
} else {
    Start-DevWindow -Title 'PvPRating Server' -GradleTask 'server'
}

Start-DevWindow -Title 'PvPRating MainDev' -GradleTask 'MainDev' -DelaySeconds 5 -WaitForServer -WindowPlacement 'Left' -WindowOrdinal 1
Start-DevWindow -Title 'PvPRating OffDev' -GradleTask 'OffDev' -DelaySeconds 10 -WaitForServer -WindowPlacement 'Right' -WindowOrdinal 2

Write-Host 'Started server, MainDev, and OffDev in separate PowerShell windows.'
