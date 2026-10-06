[CmdletBinding()]
param(
    [string]$Serial = "",
    [string]$OutputDir = ""
)

$ErrorActionPreference = 'Stop'

if (-not $OutputDir) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutputDir = Join-Path $PWD "thor-precomposer-inspect-$stamp"
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

$adbBase = @()
if ($Serial) {
    $adbBase += @('-s', $Serial)
}

function Invoke-AdbText {
    param(
        [Parameter(Mandatory=$true)][string]$Name,
        [Parameter(Mandatory=$true)][string[]]$Args
    )
    $path = Join-Path $OutputDir $Name
    $output = & adb @adbBase @Args 2>&1
    $output | Set-Content -LiteralPath $path -Encoding utf8
    return $output
}

function Invoke-ShellText {
    param(
        [Parameter(Mandatory=$true)][string]$Name,
        [Parameter(Mandatory=$true)][string]$Command
    )
    return Invoke-AdbText -Name $Name -Args @('shell', $Command)
}

# This collector is intentionally READ-ONLY. It does not set properties,
# restart services, install files, use PServer to execute commands, or reboot.

Invoke-AdbText -Name '00-device.txt' -Args @('shell',
    'getprop ro.build.fingerprint; getprop ro.vendor.build.fingerprint; getprop ro.product.device; getprop ro.build.version.sdk')

Invoke-ShellText -Name '01-properties.txt' -Command @'
echo "=== CPU property ==="
getprop vendor.display.disable_system_load_check
echo "=== init states ==="
getprop | grep -E '^[(init.svc|ro.boottime).' | sort
echo "=== boot identity / subtype candidates ==="
getprop | grep -E '^[(ro.boot|ro.hardware|ro.product).*' | sort
'@

Invoke-ShellText -Name '02-processes.txt' -Command @'
echo "=== relevant processes ==="
ps -A -o USER,PID,PPID,NAME,ARGS 2>/dev/null | grep -E 'pservice|PServer|composer-service|surfaceflinger' || true
echo "=== SELinux process domains ==="
ps -AZ 2>/dev/null | grep -E 'pservice|composer-service|surfaceflinger' || true
'@

Invoke-ShellText -Name '03-pserver.txt' -Command @'
echo "=== binder service ==="
service check PServerBinder 2>&1
echo "=== service list ==="
service list 2>/dev/null | grep -i -E 'PServer|pservice' || true
echo "=== init properties containing pservice/server ==="
getprop | grep -i -E 'pservice|pserver' || true
'@

Invoke-ShellText -Name '04-init-declarations.txt' -Command @'
for D in /system/etc/init /system_ext/etc/init /product/etc/init /vendor/etc/init /odm/etc/init; do
  [ -d "$D" ] || continue
  echo "===== $D ====="
  grep -R -n -E 'PServerBinder|pservice|qti_display_boot|display.*composer|composer-service' "$D" 2>/dev/null || true
done
'@

Invoke-ShellText -Name '05-display-boot-files.txt' -Command @'
echo "=== likely display boot files ==="
find /vendor /odm /product /system_ext -maxdepth 5 -type f   ( -iname '*display*boot*' -o -iname '*qti*display*' -o -iname '*pservice*' -o -iname '*pserver*' )   -exec ls -lZ {} ; 2>/dev/null
echo "=== scripts containing target property ==="
for D in /vendor/bin /vendor/etc /odm/bin /odm/etc /product/bin /product/etc; do
  [ -d "$D" ] || continue
  grep -R -I -n 'vendor.display.disable_system_load_check' "$D" 2>/dev/null || true
done
'@

Invoke-ShellText -Name '06-composer-order.txt' -Command @'
C=$(pidof vendor.qti.hardware.display.composer-service | awk '{print $1}')
echo "composer_pid=$C"
echo -n "uptime="; cat /proc/uptime
echo -n "clk_tck="; getconf CLK_TCK 2>/dev/null || true
if [ -n "$C" ] && [ -r "/proc/$C/stat" ]; then
  echo -n "composer_proc_stat="
  cat "/proc/$C/stat"
fi
echo "=== composer ro.boottime ==="
getprop | grep -E '^[ro.boottime..*(composer|display)' | sort || true
echo "=== pservice ro.boottime ==="
getprop | grep -i -E '^[ro.boottime..*(pservice|pserver)' | sort || true
'@

Invoke-ShellText -Name '07-mounts-and-hook-locations.txt' -Command @'
echo "=== mount points ==="
mount | grep -E ' /(data|vendor|odm|product|system_ext) ' || true
echo "=== candidate data hook dirs (metadata only) ==="
ls -ldZ /data/adb /data/adb/post-fs-data.d /data/adb/service.d /data/local/tmp 2>&1 || true
'@

Invoke-ShellText -Name '08-qti-script-preview.txt' -Command @'
for F in $(find /vendor /odm /product /system_ext -maxdepth 5 -type f   ( -iname '*display*boot*.sh' -o -iname '*qti*display*.sh' ) 2>/dev/null); do
  echo "===== $F ====="
  ls -lZ "$F" 2>/dev/null
  sed -n '1,240p' "$F" 2>/dev/null
done
'@

Write-Host "Read-only pre-composer inspection saved to: $OutputDir"
