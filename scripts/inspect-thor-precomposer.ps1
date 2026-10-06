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

Invoke-AdbText -Name '01-properties.txt' -Args @('shell', 'getprop')
Invoke-AdbText -Name '01a-cpu-property.txt' -Args @('shell', 'getprop vendor.display.disable_system_load_check')
Invoke-AdbText -Name '01b-board-platform.txt' -Args @('shell', 'getprop ro.board.platform')
Invoke-AdbText -Name '01c-composer-boottime.txt' -Args @('shell', 'getprop ro.boottime.vendor.qti.hardware.display.composer')
Invoke-AdbText -Name '01d-qti-display-boot-boottime.txt' -Args @('shell', 'getprop ro.boottime.qti_display_boot')
Invoke-AdbText -Name '01e-pservice-boottime.txt' -Args @('shell', 'getprop ro.boottime.pservice')
Invoke-AdbText -Name '01f-soc-id.txt' -Args @('shell', 'cat /sys/devices/soc0/soc_id')
Invoke-AdbText -Name '01g-platform-subtype-id.txt' -Args @('shell', 'cat /sys/devices/soc0/platform_subtype_id')

Invoke-AdbText -Name '02-processes.txt' -Args @('shell', 'ps -AZ')

Invoke-AdbText -Name '03-pserver-check.txt' -Args @('shell', 'service check PServerBinder')
Invoke-AdbText -Name '03a-service-list.txt' -Args @('shell', 'service list')

Invoke-AdbText -Name '04-qti-display-boot-rc.txt' -Args @('shell', 'cat /vendor/etc/init/init.qti.display_boot.rc')
Invoke-AdbText -Name '04a-composer-rc.txt' -Args @('shell', 'cat /vendor/etc/init/vendor.qti.hardware.display.composer-service.rc')
Invoke-AdbText -Name '04b-init-qcom-rc.txt' -Args @('shell', 'cat /vendor/etc/init/hw/init.qcom.rc')

Invoke-AdbText -Name '05-qti-display-boot-script.txt' -Args @('shell', 'cat /vendor/bin/init.qti.display_boot.sh')
Invoke-AdbText -Name '05a-qti-display-boot-script-metadata.txt' -Args @('shell', 'ls -lZ /vendor/bin/init.qti.display_boot.sh')

Invoke-AdbText -Name '06-composer-pid.txt' -Args @('shell', 'pidof vendor.qti.hardware.display.composer-service')
Invoke-AdbText -Name '06a-surfaceflinger-pid.txt' -Args @('shell', 'pidof surfaceflinger')
Invoke-AdbText -Name '06b-pservice-pid.txt' -Args @('shell', 'pidof pservice')
Invoke-AdbText -Name '06c-uptime.txt' -Args @('shell', 'cat /proc/uptime')

Invoke-AdbText -Name '07-mounts.txt' -Args @('shell', 'mount')
Invoke-AdbText -Name '07a-data-adb.txt' -Args @('shell', 'ls -ldZ /data/adb')
Invoke-AdbText -Name '07b-post-fs-data-dir.txt' -Args @('shell', 'ls -ldZ /data/adb/post-fs-data.d')
Invoke-AdbText -Name '07c-service-dir.txt' -Args @('shell', 'ls -ldZ /data/adb/service.d')
Invoke-AdbText -Name '07d-local-tmp.txt' -Args @('shell', 'ls -ldZ /data/local/tmp')

# Intentionally no broad remote find/grep here. Device-specific init trees can
# be pulled separately for local PowerShell inspection without shell quoting.

Write-Host "Read-only pre-composer inspection saved to: $OutputDir"
