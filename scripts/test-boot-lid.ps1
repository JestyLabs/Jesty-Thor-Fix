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
$daemonPackage = Join-Path $repository 'src\com\thor\displaypowertest'
$ipcSource = Get-Content -LiteralPath (Join-Path $daemonPackage 'DaemonIpcServer.java') -Raw
# No TCP listener anywhere; LocalServerSocket is the private Unix socket.
$tcpListeners = @(Get-ChildItem -LiteralPath (Join-Path $repository 'src') -Filter '*.java' -Recurse |
    Select-String -Pattern '(?<!Local)ServerSocket\(')
if ($ipcSource -notmatch 'setSoTimeout\(1500\)' -or
    $ipcSource -notmatch 'SecureChannel\.isTrustedApp\(client\)' -or
    $tcpListeners.Count -gt 0) {
    throw 'Daemon IPC must be authenticated, bounded and Unix-only.'
}
# D is only the app_process entry point; behavior lives in DaemonRuntime's objects.
$entryPoint = Get-Content -LiteralPath (Join-Path $repository 'src\D.java') -Raw
if ($entryPoint -notmatch 'new DaemonRuntime\(DaemonArgs\.parse\(args\)\)\.run\(\)' -or
    $entryPoint -match 'ProcessBuilder|SocketClient|static volatile|SecureChannel') {
    throw 'D.java must stay a thin entry point.'
}
# Keep bounded command waits on ProcessWait's 5 ms poll. The v1.5.16 trace
# suggests a coarser wait; the physical timing benefit remains unverified.
$timedWaits = @(Get-ChildItem -LiteralPath (Join-Path $repository 'src') -Filter '*.java' -Recurse |
    Select-String -Pattern '\.waitFor\(\s*[^)\s]')
if ($timedWaits.Count -gt 0) {
    throw "Timed Process.waitFor must use ProcessWait: $($timedWaits[0].Path):$($timedWaits[0].LineNumber)"
}
# Root-written diagnostics live in a shell-writable directory: never follow links there.
$rootRedirects = @(Get-ChildItem -LiteralPath (Join-Path $repository 'src') -Filter '*.java' -Recurse |
    Select-String -Pattern '>>\s*/data/local/tmp|FileWriter\(|setReadable\(true,\s*false\)')
if ($rootRedirects.Count -gt 0) {
    throw "Root diagnostics must use RootLogFiles: $($rootRedirects[0].Path):$($rootRedirects[0].LineNumber)"
}
$cpuFixSource = Get-Content -LiteralPath (Join-Path $daemonPackage 'CpuFixController.java') -Raw
$commandSource = Get-Content -LiteralPath (Join-Path $daemonPackage 'DaemonCommandHandler.java') -Raw
if ($cpuFixSource -notmatch 'observeHelper\(' -or $commandSource -notmatch '";phase_ms="') {
    throw 'The daemon must observe its compositor helper and report its phase age.'
}
# Thor identifiers belong to ThorHardwareProfile; smali repeats one of them.
$hardwareLiterals = @(Get-ChildItem -LiteralPath (Join-Path $repository 'src') -Filter '*.java' -Recurse |
    Where-Object { $_.Name -ne 'ThorHardwareProfile.java' } |
    Select-String -Pattern '0x40446d4a32a16584|crtc\[(181|243)\]|"(181|243)"|"hall_switch"|dri/0/state')
if ($hardwareLiterals.Count -gt 0) {
    throw "Thor hardware IDs must come from ThorHardwareProfile: $($hardwareLiterals[0].Path):$($hardwareLiterals[0].LineNumber)"
}
$profile = Get-Content -LiteralPath (Join-Path $repository 'src\com\thor\displaypowertest\ThorHardwareProfile.java') -Raw
$logicalId = [int][regex]::Match($profile, 'BOTTOM_LOGICAL_DISPLAY_ID = (\d+);').Groups[1].Value
$callback = Get-Content -LiteralPath (Join-Path $repository 'apk\smali\com\thor\displaypowertest\DisplayEventCallback.smali') -Raw
$smaliIds = [regex]::Matches($callback, 'const/4 v[01], 0x([0-9a-f]+)\s+(?:if-ne p1|invoke-interface \{v0, v1\})')
if ($smaliIds.Count -ne 3 -or @($smaliIds | Where-Object { [Convert]::ToInt32($_.Groups[1].Value, 16) -ne $logicalId }).Count -gt 0) {
    throw 'DisplayEventCallback.smali must use ThorHardwareProfile.BOTTOM_LOGICAL_DISPLAY_ID.'
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
    (Join-Path $repository 'src\com\thor\displaypowertest\DaemonLaunchScript.java'),
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
    (Join-Path $repository 'src\com\thor\displaypowertest\ThorHardwareProfile.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\HallNodeModel.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\HandoffRecoveryModel.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\DaemonArgs.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\CpuBootAttemptModel.java'),
    (Join-Path $repository 'tests\BootAndLidModelTest.java'),
    (Join-Path $repository 'tests\BootLatencyTest.java'),
    (Join-Path $repository 'tests\DaemonLaunchModelTest.java'),
    (Join-Path $repository 'tests\DaemonLaunchScriptTest.java'),
    (Join-Path $repository 'tests\DisplayDecisionModelTest.java'),
    (Join-Path $repository 'tests\DisplayGenerationModelTest.java'),
    (Join-Path $repository 'tests\IpcPeerPolicyTest.java'),
    (Join-Path $repository 'tests\LegacyDaemonIdentityTest.java'),
    (Join-Path $repository 'tests\PreviousSecureDaemonIdentityTest.java'),
    (Join-Path $repository 'tests\PropertyStateTest.java'),
    (Join-Path $repository 'tests\HallNodeModelTest.java'),
    (Join-Path $repository 'tests\HandoffRecoveryModelTest.java'),
    (Join-Path $repository 'tests\DaemonArgsTest.java'),
    (Join-Path $repository 'tests\CpuBootAttemptModelTest.java')
)
& javac -source 8 -target 8 -d $output $sources
if ($LASTEXITCODE -ne 0) { throw 'Boot/lid test compilation failed.' }
& java -cp $output BootAndLidModelTest
if ($LASTEXITCODE -ne 0) { throw 'Boot/lid tests failed.' }
& java -cp $output CpuBootAttemptModelTest
if ($LASTEXITCODE -ne 0) { throw 'CPU boot attempt model tests failed.' }
& java -cp $output BootLatencyTest
if ($LASTEXITCODE -ne 0) { throw 'Boot latency tests failed.' }
& java -cp $output DaemonLaunchModelTest
if ($LASTEXITCODE -ne 0) { throw 'Daemon launch model tests failed.' }
& java -cp $output DaemonLaunchScriptTest
if ($LASTEXITCODE -ne 0) { throw 'Daemon launch script tests failed.' }
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
& java -cp $output HallNodeModelTest
if ($LASTEXITCODE -ne 0) { throw 'Hall node model tests failed.' }
& java -cp $output HandoffRecoveryModelTest
if ($LASTEXITCODE -ne 0) { throw 'Handoff recovery model tests failed.' }
& java -cp $output DaemonArgsTest
if ($LASTEXITCODE -ne 0) { throw 'Daemon argument tests failed.' }
