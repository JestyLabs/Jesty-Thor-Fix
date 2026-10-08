[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9._:-]+$')][string]$Serial,
    [Parameter(Mandatory=$true)][ValidateNotNullOrEmpty()][string]$OutputDir,
    [ValidateRange(1, 20)][int]$Samples = 1,
    [ValidateRange(1, 300)][int]$IntervalSeconds = 5,
    [string]$LocalPservice = "",
    [ValidatePattern('^(|[A-Fa-f0-9]{64})$')][string]$ExpectedPserviceSha256 = "",
    [string]$AdbPath = 'adb'
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($OutputDir)) { throw 'Choose a private output directory outside Git.' }
# Reject Git checkouts (including worktrees) and redirected ancestors before
# creating any capture. Require a new directory to avoid overwriting evidence.
$OutputDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDir)
$ancestor = $OutputDir
while ($ancestor) {
    if (Test-Path -LiteralPath $ancestor) {
        $item = Get-Item -LiteralPath $ancestor -Force
        if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw 'Output directory must not traverse a symlink or junction.'
        }
        if (Test-Path -LiteralPath (Join-Path $ancestor '.git')) {
            throw 'Raw captures must be outside every Git checkout.'
        }
    }
    $parent = Split-Path -Parent $ancestor
    if ($parent -eq $ancestor) { break }
    $ancestor = $parent
}
if (Test-Path -LiteralPath $OutputDir) { throw 'Choose a new output directory; existing evidence is never overwritten.' }
Get-Command $AdbPath -ErrorAction Stop | Out-Null
New-Item -ItemType Directory -Path $OutputDir | Out-Null

# Every command is recorded even when a shell probe has no matching logs,
# permissions are restricted, or an optional source is absent.
$CaptureStatusPath = Join-Path $OutputDir '00-capture-status.tsv'
"name`tstarted_utc`tfinished_utc`telapsed_ms`texit_code" | Set-Content -LiteralPath $CaptureStatusPath -Encoding utf8

$adbBase = @('-s', $Serial)

function Invoke-AdbText {
    param(
        [Parameter(Mandatory=$true)][string]$Path,
        [Parameter(Mandatory=$true)][string[]]$Arguments,
        [switch]$RequireSuccess
    )
    $output = @()
    $exit = 127
    $started = [DateTimeOffset]::UtcNow
    $timer = [Diagnostics.Stopwatch]::StartNew()
    try {
        $output = @(& $AdbPath @adbBase @Arguments 2>&1)
        $exit = [int]$LASTEXITCODE
    } catch {
        $output += "ADB_INVOCATION_ERROR: $($_.Exception.Message)"
        $exit = 127
    }

    $timer.Stop()
    $finished = [DateTimeOffset]::UtcNow
    ($output -join [Environment]::NewLine) |
        Set-Content -LiteralPath $Path -Encoding utf8
    (@($Path, $started.ToString('o'), $finished.ToString('o'), $timer.ElapsedMilliseconds, $exit) -join [char]9) |
        Add-Content -LiteralPath $CaptureStatusPath -Encoding utf8

    # A missing service is a legitimate negative observation. Do not throw
    # for individual read-only service/log probes; retain their exit codes.
    if ($RequireSuccess -and $exit -ne 0) {
        throw "Required adb command failed (exit $exit). Evidence saved: $Path"
    }
}

function Invoke-ShellText {
    param(
        [Parameter(Mandatory=$true)][string]$Path,
        [Parameter(Mandatory=$true)][string]$Command
    )
    Invoke-AdbText -Path $Path -Arguments @('shell', $Command)
}

function Write-HostMetadata {
    $path = Join-Path $OutputDir '00-host.txt'
    @(
        "schema=THOR_PSERVERBINDER_READONLY_V2"
        "host_time_iso=$((Get-Date).ToString('o'))"
        "serial=$Serial"
        "samples=$Samples"
        "interval_seconds=$IntervalSeconds"
        "local_pservice=$LocalPservice"
        "expected_pservice_sha256=$ExpectedPserviceSha256"
    ) | Set-Content -LiteralPath $path -Encoding utf8

    $hashPath = Join-Path $OutputDir '00a-local-pservice-hash.txt'
    if ($LocalPservice -and (Test-Path -LiteralPath $LocalPservice -PathType Leaf)) {
        $hash = (Get-FileHash -LiteralPath $LocalPservice -Algorithm SHA256).Hash.ToLowerInvariant()
        @(
            "path=$LocalPservice"
            "sha256=$hash"
            "matches_expected=$(if ($ExpectedPserviceSha256) { $hash -eq $ExpectedPserviceSha256.ToLowerInvariant() } else { 'UNKNOWN' })"
        ) | Set-Content -LiteralPath $hashPath -Encoding utf8
    } else {
        @(
            "path=$LocalPservice"
            "status=not_provided_or_missing"
            "matches_expected=UNKNOWN"
        ) | Set-Content -LiteralPath $hashPath -Encoding utf8
    }
}

function Collect-Snapshot {
    param([int]$Index)

    $prefix = '{0:D2}' -f $Index
    $dir = Join-Path $OutputDir "sample-$prefix"
    New-Item -ItemType Directory -Path $dir -Force | Out-Null

    Invoke-ShellText -Path (Join-Path $dir '00-time-boot.txt') -Command @'
date -Ins 2>/dev/null || date
cat /proc/uptime
cat /proc/sys/kernel/random/boot_id
'@

    Invoke-ShellText -Path (Join-Path $dir '01-device-build.txt') -Command @'
getprop ro.build.fingerprint
getprop ro.vendor.build.fingerprint
getprop ro.product.model
getprop ro.build.version.sdk
'@

    Invoke-ShellText -Path (Join-Path $dir '02-pservice-identity.txt') -Command @'
sha256sum /system/bin/pservice 2>&1
ls -lZ /system/bin/pservice 2>&1
'@

    Invoke-ShellText -Path (Join-Path $dir '03-init-state.txt') -Command @'
getprop init.svc.pservice
getprop ro.boottime.pservice
getprop init.svc.servicemanager
getprop ro.boottime.servicemanager
getprop init.svc.vendor.qti.hardware.display.composer
getprop ro.boottime.vendor.qti.hardware.display.composer
getprop init.svc.surfaceflinger
getprop ro.boottime.surfaceflinger
'@

    Invoke-ShellText -Path (Join-Path $dir '04-pserver-check.txt') -Command 'service check PServerBinder'
    Invoke-ShellText -Path (Join-Path $dir '05-service-list.txt') -Command 'service list'
    Invoke-ShellText -Path (Join-Path $dir '06-cmd-services.txt') -Command 'cmd -l'

    Invoke-ShellText -Path (Join-Path $dir '07-processes.txt') -Command 'ps -AZ -o USER,PID,PPID,NAME,LABEL 2>&1 || ps -AZ'

    Invoke-ShellText -Path (Join-Path $dir '08-process-identity.txt') -Command @'
for N in pservice servicemanager surfaceflinger vendor.qti.hardware.display.composer-service system_server; do
  echo "=== $N ==="
  P="$(pidof "$N" 2>/dev/null)"
  echo "pid=$P"
  for I in $P; do
    echo "--- pid $I ---"
    cat "/proc/$I/stat" 2>&1
    cat "/proc/$I/status" 2>&1
    cat "/proc/$I/cgroup" 2>&1
    cat "/proc/$I/oom_score_adj" 2>&1
    printf "cmdline="
    tr '\000' ' ' <"/proc/$I/cmdline" 2>/dev/null
    echo
    ls -ldZ "/proc/$I" 2>&1
  done
done
'@

    Invoke-ShellText -Path (Join-Path $dir '09-selinux.txt') -Command @'
getenforce 2>&1
getprop ro.boot.selinux 2>&1
getprop ro.build.selinux 2>&1
'@

    Invoke-ShellText -Path (Join-Path $dir '10-binder-state.txt') -Command @'
P="$(pidof pservice 2>/dev/null)"
echo "pservice_pid=$P"
for D in /dev/binderfs /sys/kernel/debug/binder; do
  echo "=== $D ==="
  ls -laZ "$D" 2>&1
  for I in $P; do
    for F in "$D/proc/$I" "$D/stats" "$D/state"; do
      if [ -r "$F" ]; then
        echo "--- $F ---"
        cat "$F" 2>&1
      else
        echo "READ_UNAVAILABLE $F (missing or denied; UNKNOWN)"
      fi
    done
  done
done
'@

    Invoke-ShellText -Path (Join-Path $dir '11-logcat-full.txt') -Command 'logcat -b all -d -v threadtime'
    Invoke-ShellText -Path (Join-Path $dir '12-logcat-filtered.txt') -Command @'
logcat -b all -d -v threadtime 2>&1 | grep -Ei 'PServerBinder|pservice|servicemanager|ServiceManager|binderDied|addService|service_manager|avc:|denied|ThorDisplayAuto'
'@

    Invoke-ShellText -Path (Join-Path $dir '13-kernel-audit.txt') -Command @'
dmesg 2>&1 | grep -Ei 'avc:|binder|servicemanager|pservice|service_manager'
'@
}

Write-HostMetadata

# Connectivity check only. No device state is changed.
$adbStateFile = Join-Path $OutputDir '00b-adb-state.txt'
Invoke-AdbText -Path $adbStateFile -Arguments @('get-state') -RequireSuccess
$adbState = (Get-Content -LiteralPath $adbStateFile -Raw).Trim()
if ($adbState -ne 'device') {
    throw "ADB did not report a ready device (state: $adbState). Capture stopped."
}
$modelFile = Join-Path $OutputDir '00c-device-model.txt'
Invoke-AdbText -Path $modelFile -Arguments @('shell', 'getprop ro.product.model') -RequireSuccess
if ((Get-Content -LiteralPath $modelFile -Raw).Trim() -notmatch '(?i)\bThor\b') {
    throw 'Selected device is not a Thor; capture stopped before lifecycle probes.'
}

for ($i = 1; $i -le $Samples; $i++) {
    Collect-Snapshot -Index $i
    if ($i -lt $Samples) {
        Start-Sleep -Seconds $IntervalSeconds
    }
}

$statusRows = @(Import-Csv -LiteralPath $CaptureStatusPath -Delimiter ([char]9))
$nonzero = @($statusRows | Where-Object { [int]$_.exit_code -ne 0 })
@(
    "Completed read-only PServerBinder collection."
    "Output: $OutputDir"
    "schema=THOR_PSERVERBINDER_READONLY_V2"
    "commands=$($statusRows.Count)"
    "nonzero_exit_codes=$($nonzero.Count)"
    "Some optional probes legitimately return nonzero when no matches exist or permissions deny access; review 00-capture-status.tsv."
    "ServiceManager lookups are read-only Binder IPC; no command was transacted on the PServerBinder service."
    "No service restart, process signal, property write, reboot, or device-side file write was requested by this script."
) | Set-Content -LiteralPath (Join-Path $OutputDir '99-summary.txt') -Encoding utf8

if ($nonzero.Count -gt 0) {
    Write-Warning "$($nonzero.Count) read-only probe(s) returned nonzero. Inspect 00-capture-status.tsv before drawing conclusions."
}
Write-Host "Read-only PServerBinder collection saved to: $OutputDir"
