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
$attemptStore = Get-Content -LiteralPath (Join-Path $daemonPackage 'CpuBootAttemptStore.java') -Raw
if ($attemptStore -notmatch 'DATA_DIR\s*=\s*"/data/user/0/com\.thor\.displaypowertest"' -or
    $attemptStore -notmatch 'FILES_DIR\s*=\s*DATA_DIR\s*\+\s*"/files"' -or
    $attemptStore -notmatch 'jesty-thor-cpu-boot-attempt-v1' -or
    $attemptStore -notmatch 'O_NOFOLLOW' -or
    $attemptStore -notmatch 'O_EXCL' -or
    $attemptStore -notmatch 'Os\.fsync\(' -or
    $attemptStore -notmatch 'Os\.rename\(' -or
    $attemptStore -notmatch 'st_nlink != 1' -or
    $attemptStore -notmatch '& 0777\) != 0600') {
    throw 'CPU boot attempt persistence must stay app-private, no-follow, atomic and durable.'
}
if ($attemptStore -match '/data/local/tmp|/data/adb') {
    throw 'CPU boot attempt marker must never live in a shell-writable or persistent root-hook directory.'
}
$cpuFixSource = Get-Content -LiteralPath (Join-Path $daemonPackage 'CpuFixController.java') -Raw
$commandSource = Get-Content -LiteralPath (Join-Path $daemonPackage 'DaemonCommandHandler.java') -Raw
$runtimeSource = Get-Content -LiteralPath (Join-Path $daemonPackage 'DaemonRuntime.java') -Raw
if ($cpuFixSource -notmatch 'observeHelper\(' -or $commandSource -notmatch '";phase_ms="') {
    throw 'The daemon must observe its compositor helper and report its phase age.'
}
if ($cpuFixSource -notmatch 'CpuBootAttemptStore\.read\(' -or
    $cpuFixSource -notmatch 'CpuBootAttemptStore\.write\(' -or
    $cpuFixSource -notmatch 'Phase\.RESTART_REQUESTED' -or
    $cpuFixSource -notmatch 'CPU_ATTEMPT_APPLIED' -or
    $runtimeSource -notmatch 'if \(!coordinating\) cpuFix\.resumePersistedAttempt\(\)') {
    throw 'CPU restart provenance must be integrated into the runtime and normal-daemon recovery.'
}
$preComposerInspect = Get-Content -LiteralPath (Join-Path $repository 'scripts\inspect-thor-precomposer.ps1') -Raw
if ($preComposerInspect -match '(?im)\badb\s+(?:reboot|install|push|root|remount)\b' -or
    $preComposerInspect -match '(?im)\bsetprop\b' -or
    $preComposerInspect -match '(?im)\bctl\.(?:start|stop|restart)\b' -or
    $preComposerInspect -match '(?im)\b(?:rm|mv|cp|chmod|chown|mkdir|touch)\s') {
    throw 'Pre-composer collector must remain read-only and must not mutate the Thor.'
}
$preComposerProofSource = Get-Content -LiteralPath (Join-Path $daemonPackage 'PreComposerCpuProofModel.java') -Raw
if ($preComposerProofSource -match 'ProcessBuilder|setprop|ctl\.restart|DisplayActionCoordinator|SurfaceControl|LidGuard') {
    throw 'Pre-composer proof model must stay pure and side-effect free.'
}
if ($preComposerProofSource -notmatch 'WRITE_NOT_BEFORE_COMPOSER' -or
    $preComposerProofSource -notmatch 'COMPOSER_PRESENT_DURING_WRITE' -or
    $preComposerProofSource -notmatch 'readbackVerified' -or
    $preComposerProofSource -notmatch 'currentBootId\.equalsIgnoreCase\(proof\.bootId\)') {
    throw 'Pre-composer proof must require boot identity, ordering, composer absence and readback.'
}
$earlyHookModelSource = Get-Content -LiteralPath (Join-Path $daemonPackage 'EarlyCpuBootHookModel.java') -Raw
if ($earlyHookModelSource -match 'ProcessBuilder|setprop|ctl\.restart|/data/boot_start\.sh|DisplayActionCoordinator|SurfaceControl|LidGuard') {
    throw 'Early CPU boot-hook model must stay pure and must not install or execute the hook.'
}
if ($earlyHookModelSource -notmatch 'OCCUPIED_UNKNOWN' -or
    $earlyHookModelSource -notmatch 'REFUSE_OCCUPIED' -or
    $earlyHookModelSource -notmatch 'FALLBACK_AFTER_PROVEN_FAILURE' -or
    $earlyHookModelSource -notmatch 'attempt\.previous\.equals\(observedProperty\)') {
    throw 'Early CPU hook policy must preserve global-hook ownership and proven-failure fallback.'
}
$earlyHookScriptSource = Get-Content -LiteralPath (Join-Path $daemonPackage 'EarlyCpuBootHookScript.java') -Raw
if ($earlyHookScriptSource -notmatch 'JESTY_THOR_EARLY_CPU_HOOK_V1' -or
    $earlyHookScriptSource -notmatch 'jesty-thor-early-cpu-optin-v1' -or
    $earlyHookScriptSource -notmatch '/dev/jesty-thor-early-cpu-attempt-v1' -or
    $earlyHookScriptSource -notmatch 'write_attempt RESTART_REQUESTED' -or
    $earlyHookScriptSource -notmatch 'setprop ctl\.restart' -or
    $earlyHookScriptSource -match 'thor-pservice-early-cpu-restart-prototype') {
    throw 'Early CPU hook script must require private opt-in, boot-scoped no-repeat provenance, and no prototype gate.'
}
$earlyImportModelSource = Get-Content -LiteralPath (Join-Path $daemonPackage 'EarlyCpuAttemptImportModel.java') -Raw
if ($earlyImportModelSource -match 'ProcessBuilder|setprop|ctl\.restart|SurfaceControl|LidGuard' -or
    $earlyImportModelSource -notmatch 'REPLACE_STALE_DURABLE' -or
    $earlyImportModelSource -notmatch 'KEEP_MATCHING' -or
    $earlyImportModelSource -notmatch 'allowedEarlyPhase') {
    throw 'Early CPU attempt import policy must stay pure and reject ambiguous provenance.'
}
$bootCoordinatorSource = Get-Content -LiteralPath (Join-Path $daemonPackage 'BootCoordinator.java') -Raw
$earlyGateSource = Get-Content -LiteralPath (Join-Path $daemonPackage 'EarlyCpuGateModel.java') -Raw
if ($bootCoordinatorSource -notmatch 'reconcileCpuPhase\(afterComposerRestart\)' -or
    $bootCoordinatorSource -notmatch 'currentComposerHasAppliedAttempt\(\)' -or
    $bootCoordinatorSource -notmatch 'reconcileDisplayPhase\(effectiveAfterComposerRestart\)') {
    throw 'BootCoordinator must run the CPU-only phase before the independent display gate.'
}
$displayStart = $bootCoordinatorSource.IndexOf('private boolean reconcileDisplayPhase')
$displayEnd = $bootCoordinatorSource.IndexOf('private Boolean gateEdge', $displayStart)
if ($displayStart -lt 0 -or $displayEnd -le $displayStart -or
    $bootCoordinatorSource.Substring($displayStart, $displayEnd - $displayStart) -match 'cpuFix\.apply\(') {
    throw 'The display readiness phase must not perform CPU property/restart work.'
}
if ($earlyGateSource -match 'Telemetry\.crtc|DaemonState\.getMode|DisplayActionCoordinator|LidGuard') {
    throw 'The early CPU gate must stay independent from display and lid readiness.'
}
$restartPersistAt = $cpuFixSource.IndexOf('persistAttempt(requested, "CPU_ATTEMPT_RESTART_REQUESTED"')
$restartStartAt = $cpuFixSource.IndexOf('restartThread.start()')
if ($restartPersistAt -lt 0 -or $restartStartAt -lt 0 -or
    $restartPersistAt -ge $restartStartAt) {
    throw 'RESTART_REQUESTED must be durably persisted before the restart thread starts.'
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
    (Join-Path $repository 'src\com\thor\displaypowertest\BridgeFailureDiagnostic.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\HandoffRecoveryModel.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\DaemonArgs.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\CpuBootAttemptModel.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\PreComposerCpuProofModel.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\EarlyCpuBootHookModel.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\EarlyCpuBootHookScript.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\EarlyCpuAttemptImportModel.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\EarlyCpuGateModel.java'),
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
    (Join-Path $repository 'tests\BridgeFailureDiagnosticTest.java'),
    (Join-Path $repository 'tests\HandoffRecoveryModelTest.java'),
    (Join-Path $repository 'tests\DaemonArgsTest.java'),
    (Join-Path $repository 'tests\CpuBootAttemptModelTest.java'),
    (Join-Path $repository 'tests\PreComposerCpuProofModelTest.java'),
    (Join-Path $repository 'tests\EarlyCpuBootHookModelTest.java'),
    (Join-Path $repository 'tests\EarlyCpuBootHookScriptTest.java'),
    (Join-Path $repository 'tests\EarlyCpuAttemptImportModelTest.java'),
    (Join-Path $repository 'tests\EarlyCpuGateModelTest.java')
)
& javac -source 8 -target 8 -d $output $sources
if ($LASTEXITCODE -ne 0) { throw 'Boot/lid test compilation failed.' }
& java -cp $output BootAndLidModelTest
if ($LASTEXITCODE -ne 0) { throw 'Boot/lid tests failed.' }
& java -cp $output CpuBootAttemptModelTest
if ($LASTEXITCODE -ne 0) { throw 'CPU boot attempt model tests failed.' }
& java -cp $output PreComposerCpuProofModelTest
if ($LASTEXITCODE -ne 0) { throw 'Pre-composer CPU proof model tests failed.' }
& java -cp $output EarlyCpuBootHookModelTest
if ($LASTEXITCODE -ne 0) { throw 'Early CPU boot-hook model tests failed.' }
& java -cp $output EarlyCpuBootHookScriptTest
if ($LASTEXITCODE -ne 0) { throw 'Early CPU boot-hook script tests failed.' }
& java -cp $output EarlyCpuAttemptImportModelTest
if ($LASTEXITCODE -ne 0) { throw 'Early CPU attempt import model tests failed.' }
& java -cp $output EarlyCpuGateModelTest
if ($LASTEXITCODE -ne 0) { throw 'Early CPU gate model tests failed.' }
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
& java -cp $output BridgeFailureDiagnosticTest
if ($LASTEXITCODE -ne 0) { throw 'PServer bridge diagnostic tests failed.' }
& java -cp $output DaemonArgsTest
if ($LASTEXITCODE -ne 0) { throw 'Daemon argument tests failed.' }
