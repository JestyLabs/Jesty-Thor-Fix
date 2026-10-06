[CmdletBinding()]
param(
    [int]$WaitSeconds = 9
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

$apk = (& adb shell "pm path com.thor.displaypowertest 2>/dev/null | head -1").Trim()
if ($LASTEXITCODE -ne 0 -or $apk -notmatch '^package:/data/app/.+/base\.apk$') {
    throw "Installed package path is unavailable or unexpected: $apk"
}
$apk = $apk.Substring('package:'.Length)

$composer = (& adb shell "pidof vendor.qti.hardware.display.composer-service").Trim()
$sf = (& adb shell "pidof surfaceflinger").Trim()
Write-Host "Current composer PID: $composer"
Write-Host "Current SurfaceFlinger PID: $sf"
Write-Host 'Launching no-reboot splash probe. This must NOT restart Android.'

$command = "CLASSPATH=$apk app_process / com.thor.displaypowertest.RecoverySplashProbe"
Invoke-Adb shell $command

Start-Sleep -Seconds $WaitSeconds

Write-Host ''
Write-Host 'Recent splash trace:'
& adb shell "grep 'SPLASH_' /data/local/tmp/jesty-thor-boot-trace.log 2>/dev/null | tail -n 30"
if ($LASTEXITCODE -ne 0) {
    Write-Warning 'No splash trace was available.'
}

Write-Host ''
Write-Host 'Post-probe process identity (must be unchanged):'
Write-Host "composer=$((& adb shell 'pidof vendor.qti.hardware.display.composer-service').Trim())"
Write-Host "surfaceflinger=$((& adb shell 'pidof surfaceflinger').Trim())"
