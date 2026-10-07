[CmdletBinding()]
param(
    [ValidateRange(15, 600)]
    [int] $Seconds = 60,
    [string] $Label = 'watcher-sample'
)

$ErrorActionPreference = 'Stop'

function Invoke-AdbText {
    param([string[]] $Arguments)
    $output = & adb @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed: adb $($Arguments -join ' ') $([Environment]::NewLine)$($output -join [Environment]::NewLine)"
    }
    return ($output -join [Environment]::NewLine).Trim()
}

function Get-DaemonPid {
    $remote = 'for P in $(pidof app_process); do U=$(stat -c %u /proc/$P 2>/dev/null); C=$(tr "\000" " " </proc/$P/cmdline 2>/dev/null); if [ "$U" = 0 ] && echo "$C" | grep -Eq "^app_process / D [01] (hold|run) [01] [01]( |$)"; then echo $P; fi; done'
    $raw = Invoke-AdbText @('shell', $remote)
    $pids = @($raw -split '\s+' | Where-Object { $_ -match '^[1-9][0-9]*$' })
    if ($pids.Count -ne 1) {
        throw "Expected exactly one root Thor daemon; found: $raw"
    }
    return [int]$pids[0]
}

function Get-OptionalPid {
    param([string] $Name)
    $raw = (& adb shell pidof $Name 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $raw) { return $null }
    $pids = @($raw -split '\s+' | Where-Object { $_ -match '^[1-9][0-9]*

    $stat = Invoke-AdbText @('shell', "cat /proc/$Pid/stat")
    $close = $stat.LastIndexOf(')')
    if ($close -lt 0) { throw 'Could not parse /proc/PID/stat' }
    $fields = @($stat.Substring($close + 2) -split '\s+')
    if ($fields.Count -lt 22) { throw 'Short /proc/PID/stat' }

    # After stripping pid/comm, index 0 is field 3. utime/stime are fields 14/15.
    $ticks = [int64]$fields[11] + [int64]$fields[12]

    $status = Invoke-AdbText @('shell', "cat /proc/$Pid/status")
    $vol = [regex]::Match($status, '(?m)^voluntary_ctxt_switches:\s+(\d+)').Groups[1].Value
    $nonVol = [regex]::Match($status, '(?m)^nonvoluntary_ctxt_switches:\s+(\d+)').Groups[1].Value

    $ioRaw = (& adb shell "cat /proc/$Pid/io" 2>&1 | Out-String).Trim()
    $rchar = [regex]::Match($ioRaw, '(?m)^rchar:\s+(\d+)').Groups[1].Value
    $syscr = [regex]::Match($ioRaw, '(?m)^syscr:\s+(\d+)').Groups[1].Value

    [pscustomobject]@{
        At = [DateTimeOffset]::Now
        Ticks = $ticks
        Voluntary = if ($vol) { [int64]$vol } else { $null }
        NonVoluntary = if ($nonVol) { [int64]$nonVol } else { $null }
        Rchar = if ($rchar) { [int64]$rchar } else { $null }
        Syscr = if ($syscr) { [int64]$syscr } else { $null }
    }
}

function Delta-OrUnknown($after, $before) {
    if ($null -eq $after -or $null -eq $before) { return $null }
    return [int64]$after - [int64]$before
}

Invoke-AdbText @('wait-for-device') | Out-Null
if ((Invoke-AdbText @('shell', 'getprop', 'sys.boot_completed')) -ne '1') {
    throw 'Android is not fully booted.'
}

$pid = Get-DaemonPid
$settingsPid = Get-OptionalPid 'com.android.providers.settings'
$modeBefore = Invoke-AdbText @('shell', 'settings', 'get', 'system', 'dual_screen_display_mode')
$powerBefore = Invoke-AdbText @('shell', 'getprop', 'display.power.state')
$start = Get-ProcSnapshot $pid
$settingsStart = if ($settingsPid) { Get-ProcSnapshot $settingsPid } else { $null }

Write-Host "[$Label] daemon=$pid settingsProvider=$settingsPid mode=$modeBefore power=$powerBefore seconds=$Seconds"
Write-Host 'Keep the dashboard closed and do not touch the Thor during the idle sample.'
Start-Sleep -Seconds $Seconds

$pidAfter = Get-DaemonPid
if ($pidAfter -ne $pid) {
    throw "Daemon changed during sample: $pid -> $pidAfter"
}
$end = Get-ProcSnapshot $pid
$settingsEnd = $null
if ($settingsPid) {
    $settingsPidAfter = Get-OptionalPid 'com.android.providers.settings'
    if ($settingsPidAfter -eq $settingsPid) {
        $settingsEnd = Get-ProcSnapshot $settingsPid
    } else {
        Write-Warning "SettingsProvider changed during sample: $settingsPid -> $settingsPidAfter; provider deltas unavailable."
    }
}
$modeAfter = Invoke-AdbText @('shell', 'settings', 'get', 'system', 'dual_screen_display_mode')
$powerAfter = Invoke-AdbText @('shell', 'getprop', 'display.power.state')

$elapsed = [Math]::Max(0.001, ($end.At - $start.At).TotalSeconds)
$tickDelta = Delta-OrUnknown $end.Ticks $start.Ticks
$volDelta = Delta-OrUnknown $end.Voluntary $start.Voluntary
$nonVolDelta = Delta-OrUnknown $end.NonVoluntary $start.NonVoluntary
$rcharDelta = Delta-OrUnknown $end.Rchar $start.Rchar
$syscrDelta = Delta-OrUnknown $end.Syscr $start.Syscr

$daemonResult = [pscustomobject]@{
    Target = 'Thor daemon'
    Label = $Label
    Seconds = [Math]::Round($elapsed, 3)
    Pid = $pid
    ModeBefore = $modeBefore
    ModeAfter = $modeAfter
    PowerBefore = $powerBefore
    PowerAfter = $powerAfter
    CpuTicks = $tickDelta
    CpuTicksPerSecond = if ($null -ne $tickDelta) { [Math]::Round($tickDelta / $elapsed, 3) } else { $null }
    VoluntaryContextSwitches = $volDelta
    NonVoluntaryContextSwitches = $nonVolDelta
    ReadSyscalls = $syscrDelta
    ReadChars = $rcharDelta
}
$daemonResult | Format-List

if ($settingsStart -and $settingsEnd) {
    $providerTicks = Delta-OrUnknown $settingsEnd.Ticks $settingsStart.Ticks
    [pscustomobject]@{
        Target = 'SettingsProvider'
        Label = $Label
        Seconds = [Math]::Round($elapsed, 3)
        Pid = $settingsPid
        CpuTicks = $providerTicks
        CpuTicksPerSecond = if ($null -ne $providerTicks) { [Math]::Round($providerTicks / $elapsed, 3) } else { $null }
        VoluntaryContextSwitches = Delta-OrUnknown $settingsEnd.Voluntary $settingsStart.Voluntary
        NonVoluntaryContextSwitches = Delta-OrUnknown $settingsEnd.NonVoluntary $settingsStart.NonVoluntary
        ReadSyscalls = Delta-OrUnknown $settingsEnd.Syscr $settingsStart.Syscr
        ReadChars = Delta-OrUnknown $settingsEnd.Rchar $settingsStart.Rchar
    } | Format-List
} else {
    Write-Host 'SettingsProvider process metrics unavailable; daemon measurement is still valid.'
}

$metrics = (& adb logcat -d -v brief 2>&1 |
    Select-String -Pattern 'WATCHER_METRICS' |
    Select-Object -Last 1)
if ($metrics) {
    Write-Host 'Latest candidate metrics:'
    Write-Host $metrics.Line
} else {
    Write-Host 'No WATCHER_METRICS line found (expected on stable v1.6.0; candidate logs one about once per minute).'
}
 })
    return $pids.Count -eq 1 ? [int]$pids[0] : $null
}

function Get-ProcSnapshot {
    param([int] $Pid)

    $stat = Invoke-AdbText @('shell', "cat /proc/$Pid/stat")
    $close = $stat.LastIndexOf(')')
    if ($close -lt 0) { throw 'Could not parse /proc/PID/stat' }
    $fields = @($stat.Substring($close + 2) -split '\s+')
    if ($fields.Count -lt 22) { throw 'Short /proc/PID/stat' }

    # After stripping pid/comm, index 0 is field 3. utime/stime are fields 14/15.
    $ticks = [int64]$fields[11] + [int64]$fields[12]

    $status = Invoke-AdbText @('shell', "cat /proc/$Pid/status")
    $vol = [regex]::Match($status, '(?m)^voluntary_ctxt_switches:\s+(\d+)').Groups[1].Value
    $nonVol = [regex]::Match($status, '(?m)^nonvoluntary_ctxt_switches:\s+(\d+)').Groups[1].Value

    $ioRaw = (& adb shell "cat /proc/$Pid/io" 2>&1 | Out-String).Trim()
    $rchar = [regex]::Match($ioRaw, '(?m)^rchar:\s+(\d+)').Groups[1].Value
    $syscr = [regex]::Match($ioRaw, '(?m)^syscr:\s+(\d+)').Groups[1].Value

    [pscustomobject]@{
        At = [DateTimeOffset]::Now
        Ticks = $ticks
        Voluntary = if ($vol) { [int64]$vol } else { $null }
        NonVoluntary = if ($nonVol) { [int64]$nonVol } else { $null }
        Rchar = if ($rchar) { [int64]$rchar } else { $null }
        Syscr = if ($syscr) { [int64]$syscr } else { $null }
    }
}

function Delta-OrUnknown($after, $before) {
    if ($null -eq $after -or $null -eq $before) { return $null }
    return [int64]$after - [int64]$before
}

Invoke-AdbText @('wait-for-device') | Out-Null
if ((Invoke-AdbText @('shell', 'getprop', 'sys.boot_completed')) -ne '1') {
    throw 'Android is not fully booted.'
}

$pid = Get-DaemonPid
$modeBefore = Invoke-AdbText @('shell', 'settings', 'get', 'system', 'dual_screen_display_mode')
$powerBefore = Invoke-AdbText @('shell', 'getprop', 'display.power.state')
$start = Get-ProcSnapshot $pid

Write-Host "[$Label] daemon=$pid mode=$modeBefore power=$powerBefore seconds=$Seconds"
Write-Host 'Keep the dashboard closed and do not touch the Thor during the idle sample.'
Start-Sleep -Seconds $Seconds

$pidAfter = Get-DaemonPid
if ($pidAfter -ne $pid) {
    throw "Daemon changed during sample: $pid -> $pidAfter"
}
$end = Get-ProcSnapshot $pid
$modeAfter = Invoke-AdbText @('shell', 'settings', 'get', 'system', 'dual_screen_display_mode')
$powerAfter = Invoke-AdbText @('shell', 'getprop', 'display.power.state')

$elapsed = [Math]::Max(0.001, ($end.At - $start.At).TotalSeconds)
$tickDelta = Delta-OrUnknown $end.Ticks $start.Ticks
$volDelta = Delta-OrUnknown $end.Voluntary $start.Voluntary
$nonVolDelta = Delta-OrUnknown $end.NonVoluntary $start.NonVoluntary
$rcharDelta = Delta-OrUnknown $end.Rchar $start.Rchar
$syscrDelta = Delta-OrUnknown $end.Syscr $start.Syscr

[pscustomobject]@{
    Label = $Label
    Seconds = [Math]::Round($elapsed, 3)
    DaemonPid = $pid
    ModeBefore = $modeBefore
    ModeAfter = $modeAfter
    PowerBefore = $powerBefore
    PowerAfter = $powerAfter
    CpuTicks = $tickDelta
    CpuTicksPerSecond = if ($null -ne $tickDelta) { [Math]::Round($tickDelta / $elapsed, 3) } else { $null }
    VoluntaryContextSwitches = $volDelta
    NonVoluntaryContextSwitches = $nonVolDelta
    ReadSyscalls = $syscrDelta
    ReadChars = $rcharDelta
} | Format-List

$metrics = (& adb logcat -d -v brief 2>&1 |
    Select-String -Pattern 'WATCHER_METRICS' |
    Select-Object -Last 1)
if ($metrics) {
    Write-Host 'Latest candidate metrics:'
    Write-Host $metrics.Line
} else {
    Write-Host 'No WATCHER_METRICS line found (expected on stable v1.6.0; candidate logs one about once per minute).'
}
