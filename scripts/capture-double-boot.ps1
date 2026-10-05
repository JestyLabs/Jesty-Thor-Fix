[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path (Get-Location) 'thor-double-boot-capture'),
    [int]$WaitSeconds = 70
)

$ErrorActionPreference = 'Stop'

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & adb @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed: adb $($Arguments -join ' ')"
    }
}

$state = (& adb get-state 2>$null)
if ($LASTEXITCODE -ne 0 -or $state.Trim() -ne 'device') {
    throw 'No authorized ADB device is connected.'
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)

$baseline = @(
    "captured_at=$([DateTimeOffset]::Now.ToString('o'))"
    "model=$(& adb shell getprop ro.product.model)"
    "fingerprint=$(& adb shell getprop ro.build.fingerprint)"
    "boot_id=$(& adb shell cat /proc/sys/kernel/random/boot_id)"
    "debug.sf.nobootanimation=$(& adb shell getprop debug.sf.nobootanimation)"
    "init.svc.bootanim=$(& adb shell getprop init.svc.bootanim)"
    "service.bootanim.exit=$(& adb shell getprop service.bootanim.exit)"
    "service.bootanim.progress=$(& adb shell getprop service.bootanim.progress)"
    "sys.boot_completed=$(& adb shell getprop sys.boot_completed)"
)
$baseline | Set-Content -LiteralPath (Join-Path $OutputDirectory 'baseline.txt') -Encoding utf8

# Best-effort firmware capability fingerprint. Failure is diagnostic, not fatal.
$strings = & adb shell "toybox strings /system/bin/bootanimation 2>/dev/null | grep -F debug.sf.nobootanimation | head -1" 2>$null
"bootanimation_has_debug.sf.nobootanimation=$([bool]($strings -match 'debug\.sf\.nobootanimation'))" |
    Set-Content -LiteralPath (Join-Path $OutputDirectory 'binary-capabilities.txt') -Encoding utf8
$sfStrings = & adb shell "toybox strings /system/bin/surfaceflinger 2>/dev/null | grep -E 'debug\.sf\.(boot_animation|nobootanimation)' | head -10" 2>$null
$sfStrings | Add-Content -LiteralPath (Join-Path $OutputDirectory 'binary-capabilities.txt') -Encoding utf8

Invoke-Adb logcat -c
& adb shell rm -f /data/local/tmp/jesty-thor-boot-trace.log | Out-Null

Write-Host 'Rebooting. Do not interact with the Thor during the capture.'
Invoke-Adb reboot
Invoke-Adb wait-for-device

# logcat -v monotonic uses the same boot-relative time domain as the app trace,
# which makes the SurfaceFlinger/bootanim sequence easy to correlate.
Start-Sleep -Seconds $WaitSeconds

& adb logcat -b all -v monotonic -d |
    Set-Content -LiteralPath (Join-Path $OutputDirectory 'logcat-monotonic.txt') -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw 'Could not dump logcat.' }

& adb shell cat /proc/sys/kernel/random/boot_id |
    Set-Content -LiteralPath (Join-Path $OutputDirectory 'boot-id.txt') -Encoding utf8

& adb shell getprop |
    Set-Content -LiteralPath (Join-Path $OutputDirectory 'properties-after.txt') -Encoding utf8

& adb pull /data/local/tmp/jesty-thor-boot-trace.log (Join-Path $OutputDirectory 'boot-trace.log')
if ($LASTEXITCODE -ne 0) {
    Write-Warning 'boot trace was not available'
}

$log = Get-Content -LiteralPath (Join-Path $OutputDirectory 'logcat-monotonic.txt')
$pattern = 'SurfaceFlinger|BootAnimation|bootanim|surfaceflinger|vendor\.qti\.hardware\.display\.composer|ThorDisplayDaemon'
$log | Select-String -Pattern $pattern |
    ForEach-Object { $_.Line } |
    Set-Content -LiteralPath (Join-Path $OutputDirectory 'double-boot-filtered.txt') -Encoding utf8

$summary = @(
    "debug.sf.nobootanimation=$(& adb shell getprop debug.sf.nobootanimation)"
    "init.svc.bootanim=$(& adb shell getprop init.svc.bootanim)"
    "service.bootanim.exit=$(& adb shell getprop service.bootanim.exit)"
    "service.bootanim.progress=$(& adb shell getprop service.bootanim.progress)"
    "composer_pid=$(& adb shell pidof vendor.qti.hardware.display.composer-service)"
    "surfaceflinger_pid=$(& adb shell pidof surfaceflinger)"
    "zygote64_pid=$(& adb shell pidof zygote64)"
    "system_server_pid=$(& adb shell pidof system_server)"
)
$summary | Set-Content -LiteralPath (Join-Path $OutputDirectory 'summary-after.txt') -Encoding utf8

Write-Host "Capture complete: $OutputDirectory"
Write-Host 'Useful files: boot-trace.log, double-boot-filtered.txt, logcat-monotonic.txt, binary-capabilities.txt'
