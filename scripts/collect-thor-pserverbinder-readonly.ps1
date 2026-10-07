[CmdletBinding()]
param(
    [string]$Serial = "",
    [string]$OutputDir = "",
    [ValidateRange(1, 20)][int]$Samples = 1,
    [ValidateRange(1, 300)][int]$IntervalSeconds = 5,
    [string]$LocalPservice = "C:\Temp\jesty-pservice-readonly.bin"
)

$ErrorActionPreference = 'Stop'
$ExpectedPserviceSha256 = '8a0b75b44f0139843f2608f1ac7946ed1184cb126ed2777ee2bc2fb509357be4'

if (-not $OutputDir) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutputDir = Join-Path $PWD "thor-pserverbinder-readonly-$stamp"
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

$adbBase = @()
if ($Serial) {
    $adbBase += @('-s', $Serial)
}

function Invoke-AdbText {
    param(
        [Parameter(Mandatory=$true)][string]$Path,
        [Parameter(Mandatory=$true)][string[]]$Args
    )
    $output = & adb @adbBase @Args 2>&1
    $output | Set-Content -LiteralPath $Path -Encoding utf8
    return $output
}

function Invoke-ShellText {
    param(
        [Parameter(Mandatory=$true)][string]$Path,
        [Parameter(Mandatory=$true)][string]$Command
    )
    return Invoke-AdbText -Path $Path -Args @('shell', $Command)
}

function Write-HostMetadata {
    $path = Join-Path $OutputDir '00-host.txt'
    @(
        "schema=THOR_PSERVERBINDER_READONLY_V1"
        "host_time_iso=$((Get-Date).ToString('o'))"
        "serial=$Serial"
        "samples=$Samples"
        "interval_seconds=$IntervalSeconds"
        "local_pservice=$LocalPservice"
        "expected_pservice_sha256=$ExpectedPserviceSha256"
    ) | Set-Content -LiteralPath $path -Encoding utf8

    $hashPath = Join-Path $OutputDir '00a-local-pservice-hash.txt'
    if (Test-Path -LiteralPath $LocalPservice -PathType Leaf) {
        $hash = (Get-FileHash -LiteralPath $LocalPservice -Algorithm SHA256).Hash.ToLowerInvariant()
        @(
            "path=$LocalPservice"
            "sha256=$hash"
            "matches_expected=$($hash -eq $ExpectedPserviceSha256)"
        ) | Set-Content -LiteralPath $hashPath -Encoding utf8
    } else {
        @(
            "path=$LocalPservice"
            "status=missing"
            "matches_expected=false"
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
Invoke-AdbText -Path (Join-Path $OutputDir '00b-adb-state.txt') -Args @('get-state') | Out-Null

for ($i = 1; $i -le $Samples; $i++) {
    Collect-Snapshot -Index $i
    if ($i -lt $Samples) {
        Start-Sleep -Seconds $IntervalSeconds
    }
}

@(
    "Completed read-only PServerBinder collection."
    "Output: $OutputDir"
    "No service restart, process signal, property write, reboot, Binder transaction, or device-side file write was requested by this script."
) | Set-Content -LiteralPath (Join-Path $OutputDir '99-summary.txt') -Encoding utf8

Write-Host "Read-only PServerBinder collection saved to: $OutputDir"
