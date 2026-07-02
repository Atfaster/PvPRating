param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Left', 'Right')]
    [string] $Placement,

    [int] $WindowOrdinal = 1,

    [int] $TimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Windows.Forms

Add-Type @'
using System;
using System.Text;
using System.Runtime.InteropServices;

public static class WindowPlacementApi
{
    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern bool IsWindowVisible(IntPtr hWnd);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern int GetWindowTextLength(IntPtr hWnd);

    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool MoveWindow(IntPtr hWnd, int X, int Y, int nWidth, int nHeight, bool bRepaint);
}
'@

function Get-TopLevelJavaWindows {
    $windows = New-Object System.Collections.Generic.List[object]

    [WindowPlacementApi]::EnumWindows({
        param([IntPtr] $handle, [IntPtr] $param)

        if (-not [WindowPlacementApi]::IsWindowVisible($handle)) {
            return $true
        }

        $titleLength = [WindowPlacementApi]::GetWindowTextLength($handle)
        if ($titleLength -le 0) {
            return $true
        }

        $processId = 0
        [WindowPlacementApi]::GetWindowThreadProcessId($handle, [ref] $processId) | Out-Null

        try {
            $process = Get-Process -Id $processId -ErrorAction Stop
        } catch {
            return $true
        }

        if ($process.ProcessName -notin @('java', 'javaw')) {
            return $true
        }

        $titleBuilder = [System.Text.StringBuilder]::new($titleLength + 1)
        [WindowPlacementApi]::GetWindowText($handle, $titleBuilder, $titleBuilder.Capacity) | Out-Null

        $windows.Add([PSCustomObject]@{
            Handle = $handle
            ProcessId = [int] $processId
            Title = $titleBuilder.ToString()
        })

        return $true
    }, [IntPtr]::Zero) | Out-Null

    return $windows
}

$knownHandles = @{}
foreach ($window in Get-TopLevelJavaWindows) {
    $knownHandles[$window.Handle.ToInt64()] = $true
}

$firstSeen = @{}
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)

while ((Get-Date) -lt $deadline) {
    foreach ($window in Get-TopLevelJavaWindows) {
        if ($knownHandles.ContainsKey($window.Handle.ToInt64())) {
            continue
        }

        $key = $window.Handle.ToInt64()
        if (-not $firstSeen.ContainsKey($key)) {
            $firstSeen[$key] = Get-Date
        }
    }

    $candidates = @(Get-TopLevelJavaWindows |
        Where-Object { -not $knownHandles.ContainsKey($_.Handle.ToInt64()) -and $firstSeen.ContainsKey($_.Handle.ToInt64()) } |
        Sort-Object { $firstSeen[$_.Handle.ToInt64()] })

    if ($candidates.Count -ge $WindowOrdinal) {
        $targetWindow = @($candidates)[$WindowOrdinal - 1]
        $moveUntil = (Get-Date).AddSeconds(15)

        while ((Get-Date) -lt $moveUntil) {
            $area = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea
            $halfWidth = [Math]::Floor($area.Width / 2)
            $x = if ($Placement -eq 'Left') { $area.Left } else { $area.Left + $halfWidth }
            $width = if ($Placement -eq 'Left') { $halfWidth } else { $area.Width - $halfWidth }

            [WindowPlacementApi]::MoveWindow(
                $targetWindow.Handle,
                [int] $x,
                [int] $area.Top,
                [int] $width,
                [int] $area.Height,
                $true
            ) | Out-Null

            Start-Sleep -Milliseconds 250
        }

        return
    }

    Start-Sleep -Milliseconds 100
}
