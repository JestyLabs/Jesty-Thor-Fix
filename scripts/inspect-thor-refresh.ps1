[CmdletBinding()]
param(
    [string]$Serial = "",
    [string]$OutputDir = "",
    [switch]$PullBinaries
)

$ErrorActionPreference = 'Stop'
$CollectorVersion = 'THOR_REFRESH_INVESTIGATION_V1'

if (-not $OutputDir) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutputDir = Join-Path $PWD "thor-refresh-inspect-$stamp"
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

$adbBase = @()
if ($Serial) {
    $adbBase += @('-s', $Serial)
}

function Invoke-AdbCapture {
    param(
        [Parameter(Mandatory=$true)][string]$Name,
        [Parameter(Mandatory=$true)][string[]]$Args
    )

    $path = Join-Path $OutputDir $Name
    $output = & adb @adbBase @Args 2>&1
    $exit = $LASTEXITCODE
    $output | Set-Content -LiteralPath $path -Encoding utf8
    if ($exit -ne 0) {
        Write-Warning "$Name exited with code $exit; evidence was still saved."
    }
    return @($output)
}

function Invoke-ShellCapture {
    param(
        [Parameter(Mandatory=$true)][string]$Name,
        [Parameter(Mandatory=$true)][string]$Command
    )
    # PowerShell reads this CRLF file on Windows, while Android sh expects LF.
    $androidCommand = $Command.Replace("`r`n", "`n").Replace("`r", "`n")
    return Invoke-AdbCapture -Name $Name -Args @('shell', $androidCommand)
}

# Evidence collector only. Every device-side operation below is a read.
# Optional binary collection uses adb pull, which copies files from the Thor
# to the host and does not modify the device.

$CollectorVersion | Set-Content -LiteralPath (Join-Path $OutputDir '00-collector-version.txt') -Encoding ascii

Invoke-ShellCapture -Name '01-device.txt' -Command @'
echo "boot_id=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)"
echo "uptime=$(cat /proc/uptime 2>/dev/null)"
for P in   ro.product.manufacturer ro.product.brand ro.product.model ro.product.device   ro.product.name ro.board.platform ro.hardware ro.soc.model ro.soc.manufacturer   ro.build.version.sdk ro.build.id ro.build.display.id ro.build.fingerprint   ro.vendor.build.fingerprint ro.boot.hardware; do
    echo "$P=$(getprop $P)"
done
uname -a
'@

Invoke-ShellCapture -Name '02-refresh-settings.txt' -Command @'
for K in min_refresh_rate peak_refresh_rate user_refresh_rate; do
    echo "system/$K=$(settings get system $K 2>/dev/null)"
done
'@

Invoke-ShellCapture -Name '03-display-properties.txt' -Command @'
getprop | grep -iE "display|refresh|fps|vsync|qsync|surface_flinger|sf."
'@

Invoke-ShellCapture -Name '04-dumpsys-display.txt' -Command 'dumpsys display'
Invoke-ShellCapture -Name '05-surfaceflinger-display-id.txt' -Command 'dumpsys SurfaceFlinger --display-id'
Invoke-ShellCapture -Name '06-surfaceflinger.txt' -Command 'dumpsys SurfaceFlinger'
Invoke-ShellCapture -Name '07-window-displays.txt' -Command 'dumpsys window displays'
Invoke-ShellCapture -Name '08-display-cmd-help.txt' -Command 'cmd display help'

Invoke-ShellCapture -Name '09-wm-display-0.txt' -Command @'
wm size -d 0
wm density -d 0
'@
Invoke-ShellCapture -Name '10-wm-display-4.txt' -Command @'
wm size -d 4
wm density -d 4
'@

Invoke-ShellCapture -Name '11-drm-state.txt' -Command 'cat /sys/kernel/debug/dri/0/state'
Invoke-ShellCapture -Name '12-drm-directory.txt' -Command 'ls -la /sys/kernel/debug/dri/0'
Invoke-ShellCapture -Name '13-drm-connectors.txt' -Command @'
for D in /sys/class/drm/card*-*; do
    [ -d "$D" ] || continue
    echo "### $D"
    for F in status enabled modes mode vrr_capable; do
        if [ -r "$D/$F" ]; then
            echo "-- $F"
            cat "$D/$F"
        fi
    done
done
'@

Invoke-ShellCapture -Name '14-framebuffer-modes.txt' -Command @'
for F in /sys/class/graphics/fb*/modes /sys/class/graphics/fb*/mode; do
    [ -r "$F" ] || continue
    echo "### $F"
    cat "$F"
done
'@

Invoke-ShellCapture -Name '15-msm-drm-parameters.txt' -Command @'
for F in /sys/module/msm_drm/parameters/*; do
    [ -e "$F" ] || continue
    echo "### $F"
    if [ -r "$F" ]; then cat "$F"; else echo "<not readable>"; fi
done
'@

Invoke-ShellCapture -Name '16-display-services.txt' -Command @'
service check SurfaceFlinger
service check PServerBinder
service list | grep -iE "display|surface|composer|qservice"
'@

Invoke-ShellCapture -Name '17-display-processes.txt' -Command @'
ps -AZ | grep -iE "surfaceflinger|composer|pservice|display"
'@

Invoke-ShellCapture -Name '18-display-hal-list.txt' -Command @'
lshal 2>/dev/null | grep -iE "display|composer|graphics" || true
'@

Invoke-ShellCapture -Name '19-display-config-files.txt' -Command @'
find /vendor/etc /odm/etc /product/etc -maxdepth 4 -type f 2>/dev/null   | grep -iE "display|panel|qdcm|qsync|fps|refresh"   | sort
'@

Invoke-ShellCapture -Name '20-interrupts.txt' -Command @'
cat /proc/interrupts 2>/dev/null | grep -iE "dsi|drm|sde|mdp|vsync|display"
'@

Invoke-ShellCapture -Name '21-init-display-script.txt' -Command 'cat /vendor/bin/init.qti.display_boot.sh'
Invoke-ShellCapture -Name '22-composer-init.txt' -Command 'cat /vendor/etc/init/vendor.qti.hardware.display.composer-service.rc'

if ($PullBinaries) {
    $binaryRoot = Join-Path $OutputDir 'binaries'
    New-Item -ItemType Directory -Path $binaryRoot -Force | Out-Null

    $remoteListCommand = @'
for F in   /system/bin/surfaceflinger   /vendor/bin/hw/vendor.qti.hardware.display.composer-service   /vendor/lib/libsdm*.so /vendor/lib64/libsdm*.so   /vendor/lib/libdisplayconfig*.so /vendor/lib64/libdisplayconfig*.so   /vendor/lib/hw/hwcomposer*.so /vendor/lib64/hw/hwcomposer*.so   /vendor/lib/libqdMetaData*.so /vendor/lib64/libqdMetaData*.so   /vendor/lib/libqservice*.so /vendor/lib64/libqservice*.so; do
    [ -f "$F" ] && echo "$F"
done | sort -u
'@

    $remoteFiles = @(Invoke-ShellCapture -Name '30-binary-candidates.txt' -Command $remoteListCommand |
        ForEach-Object { "$_".Trim() } |
        Where-Object { $_ -match '^/' } |
        Select-Object -Unique)

    $remoteHashes = New-Object System.Collections.Generic.List[string]
    $localHashes = New-Object System.Collections.Generic.List[string]

    foreach ($remote in $remoteFiles) {
        $remoteHashOutput = & adb @adbBase shell "sha256sum '$remote' 2>/dev/null" 2>&1
        if ($LASTEXITCODE -eq 0 -and $remoteHashOutput) {
            $remoteHashes.Add(($remoteHashOutput | Select-Object -First 1).ToString().Trim())
        } else {
            $remoteHashes.Add("UNKNOWN  $remote")
        }

        $relative = $remote.TrimStart('/') -replace '/', [IO.Path]::DirectorySeparatorChar
        $local = Join-Path $binaryRoot $relative
        $localParent = Split-Path -Parent $local
        New-Item -ItemType Directory -Path $localParent -Force | Out-Null

        $pullOutput = & adb @adbBase pull $remote $local 2>&1
        $pullOutput | Add-Content -LiteralPath (Join-Path $OutputDir '31-binary-pull.log') -Encoding utf8

        if ($LASTEXITCODE -eq 0 -and (Test-Path -LiteralPath $local)) {
            $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $local).Hash.ToUpperInvariant()
            $localHashes.Add("$hash  $remote")
        } else {
            $localHashes.Add("PULL_FAILED  $remote")
        }
    }

    $remoteHashes | Set-Content -LiteralPath (Join-Path $OutputDir '32-device-sha256.txt') -Encoding ascii
    $localHashes | Set-Content -LiteralPath (Join-Path $OutputDir '33-host-sha256.txt') -Encoding ascii
}

Write-Host "Read-only refresh evidence saved to: $OutputDir"
if ($PullBinaries) {
    Write-Host "Display binaries were copied to the host under: $(Join-Path $OutputDir 'binaries')"
}
