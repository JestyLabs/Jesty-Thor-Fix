[CmdletBinding()]
param(
    [ValidateRange(15, 600)]
    [int] $Seconds = 60,
    [string] $Label = 'watcher-sample',
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $Serial,
    [string] $OutputPath
)

$ErrorActionPreference = 'Stop'

function Invoke-AdbText {
    param([string[]] $Arguments)
    # Android sh cannot parse CRLF here-strings from a Windows checkout.
    $Arguments = @($Arguments | ForEach-Object { $_.Replace("`r", '') })
    $output = & adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed: adb $($Arguments -join ' ') $([Environment]::NewLine)$($output -join [Environment]::NewLine)"
    }
    return ($output -join [Environment]::NewLine).Trim()
}

function Get-DaemonProcessId {
    $remote = 'for P in $(pidof app_process); do U=$(stat -c %u /proc/$P 2>/dev/null); C=$(tr "\000" " " </proc/$P/cmdline 2>/dev/null); if [ "$U" = 0 ] && echo "$C" | grep -Eq "^app_process / D [01] (hold|run) [01] [01]( |$)"; then echo $P; fi; done'
    $raw = Invoke-AdbText -Arguments @('shell', $remote)
    $processIds = @($raw -split '\s+' | Where-Object { $_ -match '^[1-9][0-9]*$' })
    if ($processIds.Count -ne 1) {
        throw "Expected exactly one root Thor daemon; found: $raw"
    }
    return [int]$processIds[0]
}

function Get-OptionalProcessId {
    param([string] $Name)
    $raw = (& adb -s $Serial shell pidof $Name 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $raw) { return $null }
    $processIds = @($raw -split '\s+' | Where-Object { $_ -match '^[1-9][0-9]*$' })
    if ($processIds.Count -eq 1) { return [int]$processIds[0] }
    return $null
}

function Get-SettingsProviderIdentity {
    $dump = Invoke-AdbText -Arguments @('shell', 'dumpsys', 'activity', 'providers')
    $match = [regex]::Match($dump,
        '(?ms)ContentProviderRecord\{[^\r\n]* com\.android\.providers\.settings/\.SettingsProvider\}[^*]*?proc=ProcessRecord\{\S+ (\d+):([^/\r\n]+)/')
    if ($match.Success) {
        return [pscustomobject]@{ ProcessId = [int]$match.Groups[1].Value; Name = $match.Groups[2].Value }
    }
    return $null
}

function Get-ProcSnapshot {
    param([int] $ProcessId)

    $stat = Invoke-AdbText -Arguments @('shell', "cat /proc/$ProcessId/stat")
    $close = $stat.LastIndexOf(')')
    if ($close -lt 0) { throw 'Could not parse /proc/PID/stat' }
    $fields = @($stat.Substring($close + 2) -split '\s+')
    if ($fields.Count -lt 22) { throw 'Short /proc/PID/stat' }

    # After stripping pid/comm, index 0 is field 3. utime/stime are fields 14/15.
    $ticks = [int64]$fields[11] + [int64]$fields[12]

    $status = Invoke-AdbText -Arguments @('shell', "cat /proc/$ProcessId/status")
    $vol = [regex]::Match($status, '(?m)^voluntary_ctxt_switches:\s+(\d+)').Groups[1].Value
    $nonVol = [regex]::Match($status, '(?m)^nonvoluntary_ctxt_switches:\s+(\d+)').Groups[1].Value

    # /proc/PID/io can be restricted on some builds; treat it as optional.
    $ioRaw = (& adb -s $Serial shell "cat /proc/$ProcessId/io" 2>&1 | Out-String).Trim()
    $rchar = [regex]::Match($ioRaw, '(?m)^rchar:\s+(\d+)').Groups[1].Value
    $syscr = [regex]::Match($ioRaw, '(?m)^syscr:\s+(\d+)').Groups[1].Value

    [pscustomobject]@{
        At = [Diagnostics.Stopwatch]::GetTimestamp()
        Ticks = $ticks
        StartTime = [int64]$fields[19]
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


function Get-ThreadSnapshots {
    param([int] $ProcessId)

    # Capture the whole thread group in one adb round-trip. This only reads
    # procfs and keeps the measurement endpoints outside the timed window.
    $remote = @'
for T in $(ls /proc/__PID__/task 2>/dev/null); do
  echo "THREAD|$T"
  cat /proc/__PID__/task/$T/comm 2>/dev/null || echo "?"
  cat /proc/__PID__/task/$T/stat 2>/dev/null || echo ""
  grep '^voluntary_ctxt_switches:' /proc/__PID__/task/$T/status 2>/dev/null | awk '{print $2}'
  grep '^nonvoluntary_ctxt_switches:' /proc/__PID__/task/$T/status 2>/dev/null | awk '{print $2}'
done
'@
    $remote = $remote.Replace('__PID__', [string]$ProcessId)
    $raw = Invoke-AdbText -Arguments @('shell', $remote)
    $lines = @($raw -split "\r?\n")
    $result = @{}

    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -notmatch '^THREAD\|(\d+)$' -or $i + 4 -ge $lines.Count) {
            continue
        }
        $tid = [int]$Matches[1]
        $name = $lines[$i + 1].Trim()
        $stat = $lines[$i + 2]
        $close = $stat.LastIndexOf(')')
        if ($close -lt 0) {
            $i += 4
            continue
        }
        $fields = @($stat.Substring($close + 2) -split '\s+')
        if ($fields.Count -lt 20) {
            $i += 4
            continue
        }
        $vol = $lines[$i + 3].Trim()
        $nonVol = $lines[$i + 4].Trim()
        $result[$tid] = [pscustomobject]@{
            Tid = $tid
            Name = $name
            Ticks = [int64]$fields[11] + [int64]$fields[12]
            StartTime = [int64]$fields[19]
            Voluntary = if ($vol -match '^\d+$') { [int64]$vol } else { $null }
            NonVoluntary = if ($nonVol -match '^\d+$') { [int64]$nonVol } else { $null }
        }
        $i += 4
    }
    return $result
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

    if ($Before.StartTime -ne $After.StartTime) {
        throw "$Target PID $ProcessId was recycled during measurement; refusing misleading deltas."
    }
    $tickDelta = Delta-OrUnknown $After.Ticks $Before.Ticks
    [pscustomobject]@{
        Target = $Target
        Label = $SampleLabel
        Seconds = [Math]::Round($Elapsed, 3)
        ProcessId = $ProcessId
        CpuTicks = $tickDelta
        CpuCorePercent = if ($clockTicks -match '^[1-9][0-9]*$') {
            [Math]::Round(100.0 * $tickDelta / ([double]$clockTicks * $Elapsed), 3)
        } else { $null }
        CpuTicksPerSecond = if ($null -ne $tickDelta) {
            [Math]::Round($tickDelta / $Elapsed, 3)
        } else { $null }
        LeaderVoluntaryContextSwitches = Delta-OrUnknown $After.Voluntary $Before.Voluntary
        LeaderNonVoluntaryContextSwitches = Delta-OrUnknown $After.NonVoluntary $Before.NonVoluntary
        ReadSyscalls = Delta-OrUnknown $After.Syscr $Before.Syscr
        ReadChars = Delta-OrUnknown $After.Rchar $Before.Rchar
    } | Format-List
}


function Show-ThreadDeltas {
    param(
        [string] $Target,
        [double] $Elapsed,
        $Before,
        $After
    )

    $rows = @()
    foreach ($tid in @($Before.Keys | Sort-Object)) {
        if (-not $After.ContainsKey($tid)) { continue }
        $start = $Before[$tid]
        $end = $After[$tid]
        if ($start.StartTime -ne $end.StartTime) {
            Write-Warning "$Target TID $tid was reused during sample; excluding its deltas."
            continue
        }
        $ticks = Delta-OrUnknown $end.Ticks $start.Ticks
        $voluntary = Delta-OrUnknown $end.Voluntary $start.Voluntary
        $nonVoluntary = Delta-OrUnknown $end.NonVoluntary $start.NonVoluntary
        $rows += [pscustomobject]@{
            Tid = $tid
            Thread = $end.Name
            CpuTicks = $ticks
            CpuTicksPerSecond = if ($null -ne $ticks) {
                [Math]::Round($ticks / $Elapsed, 3)
            } else { $null }
            VoluntaryContextSwitches = $voluntary
            VoluntaryPerSecond = if ($null -ne $voluntary) {
                [Math]::Round($voluntary / $Elapsed, 3)
            } else { $null }
            NonVoluntaryContextSwitches = $nonVoluntary
            NonVoluntaryPerSecond = if ($null -ne $nonVoluntary) {
                [Math]::Round($nonVoluntary / $Elapsed, 3)
            } else { $null }
        }
    }

    if ($rows.Count -eq 0) {
        Write-Host "$Target per-thread metrics unavailable."
        return
    }

    Write-Host "$Target per-thread deltas (TIDs present at both endpoints):"
    $rows | Sort-Object CpuTicks -Descending | Format-Table -AutoSize | Out-String -Width 240 | Write-Host

    $started = @($After.Keys | Where-Object { -not $Before.ContainsKey($_) })
    $ended = @($Before.Keys | Where-Object { -not $After.ContainsKey($_) })
    if ($started.Count -gt 0 -or $ended.Count -gt 0) {
        Write-Warning "$Target thread set changed during sample; started=$($started -join ',') ended=$($ended -join ',')."
    }
}

Invoke-AdbText -Arguments @('wait-for-device') | Out-Null
if ((Invoke-AdbText -Arguments @('shell', 'getprop', 'sys.boot_completed')) -ne '1') {
    throw 'Android is not fully booted.'
}

$daemonProcessId = Get-DaemonProcessId
$provider = Get-SettingsProviderIdentity
$settingsProcessId = if ($null -ne $provider) { $provider.ProcessId } else { $null }
$settingsProcessName = if ($null -ne $provider) { $provider.Name } else { '?' }
$bootBefore = Invoke-AdbText -Arguments @('shell', 'cat', '/proc/sys/kernel/random/boot_id')
$clockTicks = Invoke-AdbText -Arguments @('shell', 'getconf', 'CLK_TCK')
$conditionBefore = Invoke-AdbText -Arguments @('shell', 'dumpsys power | grep mWakefulness=; cat /sys/kernel/debug/dri/0/state')
$modeBefore = Invoke-AdbText -Arguments @('shell', 'settings', 'get', 'system', 'dual_screen_display_mode')
$powerBefore = Invoke-AdbText -Arguments @('shell', 'getprop', 'display.power.state')
$daemonStart = Get-ProcSnapshot $daemonProcessId
$daemonThreadsStart = Get-ThreadSnapshots $daemonProcessId
$daemonThreadsStartAt = [Diagnostics.Stopwatch]::GetTimestamp()
$settingsStart = if ($null -ne $settingsProcessId) {
    Get-OptionalProcSnapshot $settingsProcessId 'SettingsProvider'
} else { $null }

Write-Host "[$Label] serial=$Serial daemon=$daemonProcessId settingsProvider=$settingsProcessId host=$settingsProcessName mode=$modeBefore power=$powerBefore seconds=$Seconds"
Write-Host 'Keep the dashboard closed and leave the Thor in the requested test condition during the sample.'
Start-Sleep -Seconds $Seconds

$daemonProcessIdAfter = Get-DaemonProcessId
if ($daemonProcessIdAfter -ne $daemonProcessId) {
    throw "Daemon changed during sample: $daemonProcessId -> $daemonProcessIdAfter"
}
$daemonEnd = Get-ProcSnapshot $daemonProcessId
if ($daemonEnd.StartTime -ne $daemonStart.StartTime) {
    throw "Thor daemon PID $daemonProcessId was recycled during the sample."
}
$daemonThreadsEnd = Get-ThreadSnapshots $daemonProcessId
$daemonThreadsEndAt = [Diagnostics.Stopwatch]::GetTimestamp()

$settingsEnd = $null
if ($null -ne $settingsProcessId -and $null -ne $settingsStart) {
    $providerAfter = Get-SettingsProviderIdentity
    $settingsProcessIdAfter = if ($null -ne $providerAfter) { $providerAfter.ProcessId } else { $null }
    if ($settingsProcessIdAfter -eq $settingsProcessId) {
        $settingsEnd = Get-OptionalProcSnapshot $settingsProcessId 'SettingsProvider'
        if ($null -ne $settingsEnd -and
                $settingsEnd.StartTime -ne $settingsStart.StartTime) {
            Write-Warning 'SettingsProvider PID was reused during sample; provider deltas unavailable.'
            $settingsEnd = $null
        }
    } else {
        Write-Warning "SettingsProvider changed during sample: $settingsProcessId -> $settingsProcessIdAfter; provider deltas unavailable."
    }
}

$modeAfter = Invoke-AdbText -Arguments @('shell', 'settings', 'get', 'system', 'dual_screen_display_mode')
$powerAfter = Invoke-AdbText -Arguments @('shell', 'getprop', 'display.power.state')
$bootAfter = Invoke-AdbText -Arguments @('shell', 'cat', '/proc/sys/kernel/random/boot_id')
$conditionAfter = Invoke-AdbText -Arguments @('shell', 'dumpsys power | grep mWakefulness=; cat /sys/kernel/debug/dri/0/state')
Write-Host ('Condition endpoints: ' + ($conditionBefore -split "\r?\n")[0] + ' -> ' + ($conditionAfter -split "\r?\n")[0])
if ($modeBefore -ne $modeAfter -or $powerBefore -ne $powerAfter -or
        ($conditionBefore -split "\r?\n")[0] -ne ($conditionAfter -split "\r?\n")[0]) {
    Write-Warning 'Condition changed at endpoints; do not classify this window as steady idle.'
}
if ($bootBefore -ne $bootAfter) { throw 'Kernel boot changed during sample.' }
$elapsed = [Math]::Max(0.001, ($daemonEnd.At - $daemonStart.At) / [double][Diagnostics.Stopwatch]::Frequency)
$threadElapsed = [Math]::Max(0.001, ($daemonThreadsEndAt - $daemonThreadsStartAt) / [double][Diagnostics.Stopwatch]::Frequency)
if ($OutputPath) {
    [pscustomobject]@{
        Label = $Label; Serial = $Serial; CapturedUtc = [DateTimeOffset]::UtcNow.ToString('o')
        BootId = $bootBefore; ClockTicksPerSecond = $clockTicks
        ModeBefore = $modeBefore; ModeAfter = $modeAfter
        PowerBefore = $powerBefore; PowerAfter = $powerAfter
        ConditionBefore = $conditionBefore; ConditionAfter = $conditionAfter
        DaemonProcessId = $daemonProcessId; DaemonBefore = $daemonStart; DaemonAfter = $daemonEnd
        DaemonSeconds = $elapsed; ThreadSeconds = $threadElapsed
        ThreadsBefore = @($daemonThreadsStart.Values); ThreadsAfter = @($daemonThreadsEnd.Values)
        ProviderProcessId = $settingsProcessId; ProviderHost = $settingsProcessName
        ProviderBefore = $settingsStart; ProviderAfter = $settingsEnd
        StopwatchFrequency = [Diagnostics.Stopwatch]::Frequency
    } | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $OutputPath -Encoding UTF8
}

[pscustomobject]@{
    Label = $Label
    Seconds = [Math]::Round($elapsed, 3)
    ModeBefore = $modeBefore
    ModeAfter = $modeAfter
    PowerBefore = $powerBefore
    PowerAfter = $powerAfter
} | Format-List

Show-ProcessDelta 'Thor daemon' $Label $elapsed $daemonProcessId $daemonStart $daemonEnd
Show-ThreadDeltas 'Thor daemon' $threadElapsed $daemonThreadsStart $daemonThreadsEnd

if ($null -ne $settingsStart -and $null -ne $settingsEnd) {
    $providerElapsed = [Math]::Max(0.001, ($settingsEnd.At - $settingsStart.At) / [double][Diagnostics.Stopwatch]::Frequency)
    Write-Host 'SettingsProvider host process totals include unrelated system work; these are not provider-only costs.'
    Show-ProcessDelta 'SettingsProvider host' $Label $providerElapsed $settingsProcessId $settingsStart $settingsEnd
} else {
    Write-Host 'SettingsProvider process metrics unavailable; daemon measurement is still valid.'
}

$metrics = (& adb -s $Serial logcat -d -v brief -s ThorDisplayDaemon 2>&1 |
    Select-String -Pattern 'WATCHER_METRICS' |
    Select-Object -Last 1)
if ($metrics) {
    Write-Host 'Latest candidate metrics:'
    Write-Host $metrics.Line
} else {
    Write-Host 'No WATCHER_METRICS line found (expected on stable v1.6.0; candidate logs one about once per minute).'
}
