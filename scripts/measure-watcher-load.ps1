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

function Get-DaemonProcessId {
    $remote = 'for P in $(pidof app_process); do U=$(stat -c %u /proc/$P 2>/dev/null); C=$(tr "\000" " " </proc/$P/cmdline 2>/dev/null); if [ "$U" = 0 ] && echo "$C" | grep -Eq "^app_process / D [01] (hold|run) [01] [01]( |$)"; then echo $P; fi; done'
    $raw = Invoke-AdbText @('shell', $remote)
    $processIds = @($raw -split '\s+' | Where-Object { $_ -match '^[1-9][0-9]*$' })
    if ($processIds.Count -ne 1) {
        throw "Expected exactly one root Thor daemon; found: $raw"
    }
    return [int]$processIds[0]
}

function Get-OptionalProcessId {
    param([string] $Name)
    $raw = (& adb shell pidof $Name 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $raw) { return $null }
    $processIds = @($raw -split '\s+' | Where-Object { $_ -match '^[1-9][0-9]*$' })
    if ($processIds.Count -eq 1) { return [int]$processIds[0] }
    return $null
}

function Get-ProcSnapshot {
    param([int] $ProcessId)

    $stat = Invoke-AdbText @('shell', "cat /proc/$ProcessId/stat")
    $close = $stat.LastIndexOf(')')
    if ($close -lt 0) { throw 'Could not parse /proc/PID/stat' }
    $fields = @($stat.Substring($close + 2) -split '\s+')
    if ($fields.Count -lt 22) { throw 'Short /proc/PID/stat' }

    # After stripping pid/comm, index 0 is field 3. utime/stime are fields 14/15.
    $ticks = [int64]$fields[11] + [int64]$fields[12]

    $status = Invoke-AdbText @('shell', "cat /proc/$ProcessId/status")
    $vol = [regex]::Match($status, '(?m)^voluntary_ctxt_switches:\s+(\d+)').Groups[1].Value
    $nonVol = [regex]::Match($status, '(?m)^nonvoluntary_ctxt_switches:\s+(\d+)').Groups[1].Value

    # /proc/PID/io can be restricted on some builds; treat it as optional.
    $ioRaw = (& adb shell "cat /proc/$ProcessId/io" 2>&1 | Out-String).Trim()
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

function Get-OptionalProcSnapshot {
    param([int] $ProcessId, [string] $Name)
    try {
        return Get-ProcSnapshot $ProcessId
    } catch {
        Write-Warning "$Name /proc metrics unavailable: $($_.Exception.Message)"
        return $null
    }
}

function Delta-OrUnknown($after, $before) {
    if ($null -eq $after -or $null -eq $before) { return $null }
    return [int64]$after - [int64]$before
}

function Show-ProcessDelta {
    param(
        [string] $Target,
        [string] $SampleLabel,
        [double] $Elapsed,
        [int] $ProcessId,
        $Before,
        $After
    )

    $tickDelta = Delta-OrUnknown $After.Ticks $Before.Ticks
    [pscustomobject]@{
        Target = $Target
        Label = $SampleLabel
        Seconds = [Math]::Round($Elapsed, 3)
        ProcessId = $ProcessId
        CpuTicks = $tickDelta
        CpuTicksPerSecond = if ($null -ne $tickDelta) {
            [Math]::Round($tickDelta / $Elapsed, 3)
        } else { $null }
        VoluntaryContextSwitches = Delta-OrUnknown $After.Voluntary $Before.Voluntary
        NonVoluntaryContextSwitches = Delta-OrUnknown $After.NonVoluntary $Before.NonVoluntary
        ReadSyscalls = Delta-OrUnknown $After.Syscr $Before.Syscr
        ReadChars = Delta-OrUnknown $After.Rchar $Before.Rchar
    } | Format-List
}

Invoke-AdbText @('wait-for-device') | Out-Null
if ((Invoke-AdbText @('shell', 'getprop', 'sys.boot_completed')) -ne '1') {
    throw 'Android is not fully booted.'
}

$daemonProcessId = Get-DaemonProcessId
$settingsProcessId = Get-OptionalProcessId 'com.android.providers.settings'
$modeBefore = Invoke-AdbText @('shell', 'settings', 'get', 'system', 'dual_screen_display_mode')
$powerBefore = Invoke-AdbText @('shell', 'getprop', 'display.power.state')
$daemonStart = Get-ProcSnapshot $daemonProcessId
$settingsStart = if ($null -ne $settingsProcessId) {
    Get-OptionalProcSnapshot $settingsProcessId 'SettingsProvider'
} else { $null }

Write-Host "[$Label] daemon=$daemonProcessId settingsProvider=$settingsProcessId mode=$modeBefore power=$powerBefore seconds=$Seconds"
Write-Host 'Keep the dashboard closed and do not touch the Thor during the sample.'
Start-Sleep -Seconds $Seconds

$daemonProcessIdAfter = Get-DaemonProcessId
if ($daemonProcessIdAfter -ne $daemonProcessId) {
    throw "Daemon changed during sample: $daemonProcessId -> $daemonProcessIdAfter"
}
$daemonEnd = Get-ProcSnapshot $daemonProcessId

$settingsEnd = $null
if ($null -ne $settingsProcessId -and $null -ne $settingsStart) {
    $settingsProcessIdAfter = Get-OptionalProcessId 'com.android.providers.settings'
    if ($settingsProcessIdAfter -eq $settingsProcessId) {
        $settingsEnd = Get-OptionalProcSnapshot $settingsProcessId 'SettingsProvider'
    } else {
        Write-Warning "SettingsProvider changed during sample: $settingsProcessId -> $settingsProcessIdAfter; provider deltas unavailable."
    }
}

$modeAfter = Invoke-AdbText @('shell', 'settings', 'get', 'system', 'dual_screen_display_mode')
$powerAfter = Invoke-AdbText @('shell', 'getprop', 'display.power.state')
$elapsed = [Math]::Max(0.001, ($daemonEnd.At - $daemonStart.At).TotalSeconds)

[pscustomobject]@{
    Label = $Label
    Seconds = [Math]::Round($elapsed, 3)
    ModeBefore = $modeBefore
    ModeAfter = $modeAfter
    PowerBefore = $powerBefore
    PowerAfter = $powerAfter
} | Format-List

Show-ProcessDelta 'Thor daemon' $Label $elapsed $daemonProcessId $daemonStart $daemonEnd

if ($null -ne $settingsStart -and $null -ne $settingsEnd) {
    Show-ProcessDelta 'SettingsProvider' $Label $elapsed $settingsProcessId $settingsStart $settingsEnd
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
