[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9._:-]+$')]
    [string] $Serial,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_-]+$')]
    [string] $Label,

    [string] $OutputRoot = 'C:\Temp\jesty-thor-1517-evidence',
    [string] $AdbPath,
    [switch] $ResourceSnapshot,
    [switch] $InspectInit
)

$ErrorActionPreference = 'Stop'
$adb = if ($AdbPath) { $AdbPath } else { (Get-Command adb -ErrorAction Stop).Source }
$deviceState = (& $adb -s $Serial get-state 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $deviceState -ne 'device') {
    throw "ADB device $Serial is not ready: $deviceState"
}
$model = (& $adb -s $Serial shell getprop ro.product.model 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $model -notmatch '(?i)thor') {
    throw "Refusing collection from unexpected model: $model"
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$folder = Join-Path $OutputRoot "$stamp-$Label"
New-Item -ItemType Directory -Path $folder -Force | Out-Null
$results = [ordered]@{
    label = $Label
    serial = $Serial
    model = $model
    captured_at_local = (Get-Date).ToString('o')
    commands = @()
}

function Save-AdbRead {
    param([string] $Name, [string] $Command)
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $lines = @(& $adb -s $Serial shell $Command 2>&1 | ForEach-Object { [string]$_ })
    } finally {
        $ErrorActionPreference = $oldPreference
    }
    $exitCode = $LASTEXITCODE
    $path = Join-Path $folder "$Name.txt"
    $lines | Set-Content -LiteralPath $path -Encoding UTF8
    $script:results.commands += [ordered]@{
        name = $Name
        command = $Command
        exit_code = $exitCode
        file = [IO.Path]::GetFileName($path)
    }
}

# Every command below reads device state. A failing optional read is recorded,
# never repaired by writing to the Thor. Keep the raw evidence outside Git.
Save-AdbRead 'boot-id' 'cat /proc/sys/kernel/random/boot_id'
Save-AdbRead 'package' 'dumpsys package com.thor.displaypowertest'
Save-AdbRead 'ayn-mode' 'settings get system dual_screen_display_mode'
Save-AdbRead 'cpu-property' 'getprop vendor.display.disable_system_load_check'
Save-AdbRead 'boot-properties' 'getprop | grep ro.boottime'
Save-AdbRead 'startup-properties' 'getprop sys.boot_completed; getprop service.bootanim.exit; getprop init.svc.vendor.qti.hardware.display.composer'
Save-AdbRead 'kernel' 'uname -r'
Save-AdbRead 'processes' 'ps -A -o PID,PPID,USER,ARGS'
Save-AdbRead 'compositor-pids' 'pidof system_server surfaceflinger zygote64 vendor.qti.hardware.display.composer-service'
Save-AdbRead 'crtc-state' "su -c 'cat /sys/kernel/debug/dri/0/state'"
Save-AdbRead 'boot-trace' 'cat /data/local/tmp/jesty-thor-boot-trace.log'
Save-AdbRead 'boot-trace-previous' 'cat /data/local/tmp/jesty-thor-boot-trace.log.1'
Save-AdbRead 'events-boot' 'logcat -b events -v monotonic -d | grep -E "boot_progress|sf_stop_bootanim|wm_boot_animation_done"'
Save-AdbRead 'service-boot' 'logcat -b main -b system -v monotonic -d | grep -E "BootReceiver|trusted daemon healthy|trusted daemon boot coordinator active|ThorDisplayAuto|ThorDisplayDaemon"'
Save-AdbRead 'crash-buffer' 'logcat -b crash -v monotonic -d'
Save-AdbRead 'tombstone-list' "su -c 'ls -l /data/tombstones'"
Save-AdbRead 'broadcast-history' 'dumpsys activity broadcasts history'
Save-AdbRead 'boot-receivers' 'cmd package query-receivers --brief -a android.intent.action.BOOT_COMPLETED'
Save-AdbRead 'package-service' 'service check package; service check settings'

if ($ResourceSnapshot) {
    Save-AdbRead 'threads-top' 'top -H -n 1'
    Save-AdbRead 'app-memory' 'dumpsys meminfo com.thor.displaypowertest'
    Save-AdbRead 'daemon-processes' 'ps -A -T -o PID,TID,USER,ARGS'
}

if ($InspectInit) {
    Save-AdbRead 'composer-init-rules' "su -c 'grep -rn -B2 -A8 display.composer /vendor/etc/init/*.rc'"
    Save-AdbRead 'system-load-writers' "su -c 'grep -rn system_load /vendor/etc/init /odm/etc/init /system/etc/init /vendor/bin/*.sh'"
    Save-AdbRead 'root-manager' "su -c 'magisk -v; ksud -V; ls /data/adb/modules /data/adb/post-fs-data.d'"
}

$manifestPath = Join-Path $folder 'manifest.json'
$results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
Get-ChildItem -LiteralPath $folder -File |
    ForEach-Object { '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash, $_.Name } |
    Set-Content -LiteralPath (Join-Path $folder 'SHA256SUMS.txt') -Encoding UTF8
Write-Output "Evidence saved outside Git: $folder"
