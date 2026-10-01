[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$output = Join-Path $env:TEMP 'jesty-thor-boot-lid-tests'

$manifest = Get-Content -LiteralPath (Join-Path $repository 'apk\AndroidManifest.xml') -Raw
$identity = Get-Content -LiteralPath (Join-Path $repository 'src\com\thor\displaypowertest\DaemonIdentity.java') -Raw
$versionName = [regex]::Match($manifest, 'android:versionName="([^"]+)"').Groups[1].Value
$daemonVersion = [regex]::Match($identity, 'VERSION = "([^"]+)"').Groups[1].Value
if (-not $versionName -or $versionName -ne $daemonVersion) {
    throw "Manifest/daemon version mismatch: $versionName / $daemonVersion"
}
$buildScript = Get-Content -LiteralPath (Join-Path $repository 'build.ps1') -Raw
$artifactVersion = [regex]::Match($buildScript,
    "ArtifactBaseName = 'Jesty-Thor-Fix-([^']+)'").Groups[1].Value
if ($artifactVersion -ne $versionName) {
    throw "Manifest/build artifact version mismatch: $versionName / $artifactVersion"
}

# The watcher is only a Settings reader. All display decisions and writes
# belong to DisplayActionCoordinator and DisplayHardware respectively.
$watcher = Get-Content -LiteralPath (Join-Path $repository 'apk\smali\com\thor\displaypowertest\DaemonWatchThread.smali') -Raw
if ($watcher -notmatch 'DisplayActionCoordinator;->onWatcherSample\(Ljava/lang/String;\)V') {
    throw 'Mode watcher must hand samples to the coordinator.'
}
if ($watcher -match 'setDisplayPowerMode|SystemProperties;->set|WakeRepairScheduler;->') {
    throw 'Mode watcher must not change hardware, properties, or wake-repair state directly.'
}
$displayWriters = @(Get-ChildItem -LiteralPath (Join-Path $repository 'src') -Filter '*.java' -Recurse |
    Where-Object { $_.Name -ne 'DisplayHardware.java' } |
    Select-String -Pattern 'setDisplayPowerMode|SystemProperties.*set\(|"display.power.state"' |
    Where-Object { $_.Line -notmatch 'getDeclaredMethod\("setDisplayPowerMode"|getProperty\(|display.power.state.*get' })
if ($displayWriters.Count -gt 0) {
    throw 'Only DisplayHardware may write the lower panel state.'
}
$manifest = Get-Content -LiteralPath (Join-Path $repository 'apk\AndroidManifest.xml') -Raw
if ($manifest -match 'OnReceiver|OffReceiver|ApplyReceiver') {
    throw 'Legacy unauthenticated receivers must not be packaged.'
}
$autoService = Get-Content -LiteralPath (Join-Path $repository 'src\com\thor\displaypowertest\AutoService.java') -Raw
if ($autoService -notmatch 'daemon_protocol_unix_v2') {
    throw 'In-place updates must migrate the running privileged daemon to the authenticated channel.'
}
if ($autoService -match 'secureSocketExists\(') {
    throw 'AutoService must classify a socket inode by boot ID, not wait on its mere existence.'
}
$daemonSource = Get-Content -LiteralPath (Join-Path $repository 'src\D.java') -Raw
if ($daemonSource -notmatch 'setSoTimeout\(1500\)' -or
    $daemonSource -notmatch 'SecureChannel\.isTrustedApp\(client\)' -or
    $daemonSource -match 'ServerSocket\(') {
    throw 'Daemon IPC must be authenticated, bounded and Unix-only.'
}
# Keep bounded command waits on ProcessWait's 5 ms poll. The v1.5.16 trace
# suggests a coarser wait; the physical timing benefit remains unverified.
$timedWaits = @(Get-ChildItem -LiteralPath (Join-Path $repository 'src') -Filter '*.java' -Recurse |
    Select-String -Pattern '\.waitFor\(\s*[^)\s]')
if ($timedWaits.Count -gt 0) {
    throw "Timed Process.waitFor must use ProcessWait: $($timedWaits[0].Path):$($timedWaits[0].LineNumber)"
}
$bootReceiver = Get-Content -LiteralPath (Join-Path $repository 'apk\smali\com\thor\displaypowertest\BootReceiver.smali') -Raw
if ($bootReceiver -notmatch 'const-string v1, "android\.intent\.action\.BOOT_COMPLETED"' -or
    $bootReceiver -notmatch 'Landroid/content/Intent;->getAction\(\)') {
    throw 'The exported BootReceiver must ignore any action other than BOOT_COMPLETED.'
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
    (Join-Path $repository 'src\com\thor\displaypowertest\DaemonLaunchModel.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\LidGuardModel.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\ExternalDisplayModel.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\DisplayDecisionModel.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\DisplayGenerationModel.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\IpcPeerPolicy.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\LegacyDaemonIdentity.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\PreviousSecureDaemonIdentity.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\PropertyState.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\ProcessWait.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\Telemetry.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\DashboardStateModel.java'),
    (Join-Path $repository 'tests\BootAndLidModelTest.java'),
    (Join-Path $repository 'tests\BootLatencyTest.java'),
    (Join-Path $repository 'tests\DaemonLaunchModelTest.java'),
    (Join-Path $repository 'tests\DisplayDecisionModelTest.java'),
    (Join-Path $repository 'tests\DisplayGenerationModelTest.java'),
    (Join-Path $repository 'tests\IpcPeerPolicyTest.java'),
    (Join-Path $repository 'tests\LegacyDaemonIdentityTest.java'),
    (Join-Path $repository 'tests\PreviousSecureDaemonIdentityTest.java'),
    (Join-Path $repository 'tests\PropertyStateTest.java')
)
& javac -source 8 -target 8 -d $output $sources
if ($LASTEXITCODE -ne 0) { throw 'Boot/lid test compilation failed.' }
& java -cp $output BootAndLidModelTest
if ($LASTEXITCODE -ne 0) { throw 'Boot/lid tests failed.' }
& java -cp $output BootLatencyTest
if ($LASTEXITCODE -ne 0) { throw 'Boot latency tests failed.' }
& java -cp $output DaemonLaunchModelTest
if ($LASTEXITCODE -ne 0) { throw 'Daemon launch model tests failed.' }
& java -cp $output PropertyStateTest
if ($LASTEXITCODE -ne 0) { throw 'Property-state tests failed.' }
& java -cp $output DisplayDecisionModelTest
if ($LASTEXITCODE -ne 0) { throw 'Display decision tests failed.' }
& java -cp $output DisplayGenerationModelTest
if ($LASTEXITCODE -ne 0) { throw 'Display generation tests failed.' }
& java -cp $output IpcPeerPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'IPC peer policy tests failed.' }
& java -cp $output LegacyDaemonIdentityTest
if ($LASTEXITCODE -ne 0) { throw 'Legacy daemon identity tests failed.' }
& java -cp $output PreviousSecureDaemonIdentityTest
if ($LASTEXITCODE -ne 0) { throw 'Previous secure daemon identity tests failed.' }
