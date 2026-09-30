[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$output = Join-Path $env:TEMP 'jesty-thor-boot-lid-tests'

# The mode watcher must be inactive during BOOT HOLD and active after READY.
# This smali branch is easy to invert and would silently disable true-off
# reconciliation for every later AYN display-mode change.
$watcher = Get-Content -LiteralPath (Join-Path $repository 'apk\smali\com\thor\displaypowertest\DaemonWatchThread.smali') -Raw
if ($watcher -notmatch 'BootSafety;->isHeld\(\)Z\s+move-result p0\s+if-nez p0, :cond_8') {
    throw 'Mode watcher must skip display actions while BOOT HOLD is active.'
}
if ($watcher -notmatch 'BootSafety;->shouldApplyMode\(Ljava/lang/String;\)Z\s+move-result p0\s+if-nez p0, :cond_apply_mode') {
    throw 'Mode watcher must reconcile a changed physical display state after READY.'
}
if ($watcher -notmatch ':check_top_hardware\s+invoke-static \{\}, Lcom/thor/displaypowertest/WakeRepairScheduler;->isPending\(\)Z\s+move-result p0\s+invoke-static \{v14, p0\}, Lcom/thor/displaypowertest/BootSafety;->shouldRepairStableTop\(Ljava/lang/String;Z\)Z') {
    throw 'Stable TOP must repair reactivated lower hardware without pre-empting wake repair.'
}
$autoService = Get-Content -LiteralPath (Join-Path $repository 'src\com\thor\displaypowertest\AutoService.java') -Raw
if ($autoService -notmatch 'daemon_protocol_50') {
    throw 'In-place updates must migrate the running privileged daemon to the new watcher.'
}

if (Test-Path -LiteralPath $output) {
    $resolved = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $output).Path)
    $expected = [IO.Path]::GetFullPath($output)
    if (-not [String]::Equals($resolved, $expected, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean unexpected test path: $resolved"
    }
    Remove-Item -LiteralPath $output -Recurse -Force
}
New-Item -ItemType Directory -Path $output | Out-Null

$sources = @(
    (Join-Path $repository 'src\com\thor\displaypowertest\BootGateModel.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\LidGuardModel.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\ExternalDisplayModel.java'),
    (Join-Path $repository 'tests\BootAndLidModelTest.java')
)
& javac -source 8 -target 8 -d $output $sources
if ($LASTEXITCODE -ne 0) { throw 'Boot/lid test compilation failed.' }
& java -cp $output BootAndLidModelTest
if ($LASTEXITCODE -ne 0) { throw 'Boot/lid tests failed.' }
