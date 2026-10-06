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
$splashSource = Get-Content -LiteralPath (Join-Path $daemonPackage 'RecoverySplashModel.java') -Raw
if ($splashSource -match 'CpuFixController|ctl\.restart|vendor\.qti\.hardware\.display\.composer|setprop|ProcessBuilder') {
    throw 'Recovery splash model must stay isolated from CPU restart and runtime side effects.'
}
if ($splashSource -notmatch 'SPLASH_ARMED' -or
    $splashSource -notmatch 'SPLASH_WAIT_SF' -or
    $splashSource -notmatch 'SPLASH_SHOW_REQUESTED' -or
    $splashSource -notmatch 'SPLASH_SHOWN' -or
    $splashSource -notmatch 'SPLASH_BOOTANIM_EXIT' -or
    $splashSource -notmatch 'SPLASH_HOME_VISIBLE' -or
    $splashSource -notmatch 'SPLASH_REMOVE_REQUESTED' -or
    $splashSource -notmatch 'SPLASH_REMOVED' -or
    $splashSource -notmatch 'SPLASH_TIMEOUT' -or
    $splashSource -notmatch 'SPLASH_FAIL_OPEN') {
    throw 'Recovery splash trace schema is incomplete.'
}
$successorPidSource = Get-Content -LiteralPath (Join-Path $daemonPackage 'SuccessorPidModel.java') -Raw
if ($successorPidSource -match 'ProcessBuilder|Runtime\.getRuntime|ctl\.restart|setprop|SurfaceControl|DisplayActionCoordinator') {
    throw 'Successor PID resolver must remain a pure observation model.'
}
if ($successorPidSource -notmatch 'AMBIGUOUS' -or
    $successorPidSource -notmatch 'baseline' -or
    $successorPidSource -notmatch 'successors\.size\(\) > 1') {
    throw 'Successor PID resolver must reject ambiguous multi-successor observations.'
}

# Surface runtime prototype: explicit opt-in, app-window-independent and fail-open.
$recoverySplashRuntime = Get-Content -LiteralPath (Join-Path $daemonPackage 'RecoverySplash.java') -Raw
$recoverySplashProbe = Get-Content -LiteralPath (Join-Path $daemonPackage 'RecoverySplashProbe.java') -Raw
if ($recoverySplashRuntime -match 'import\s+android\.app\.Activity|import\s+android\.view\.WindowManager|ctl\.restart|CpuBootAttemptStore|BootSafety|DisplayHardware\.apply') {
    throw 'Recovery splash runtime must not acquire app-window or restart/display authority.'
}
if ($recoverySplashRuntime -match 'import\s+android\.view\.SurfaceControl') {
    throw 'Recovery splash must access hidden SurfaceControl APIs reflectively.'
}
if ($recoverySplashRuntime -notmatch 'TARGET_WAIT_MS = 4500L' -or
    $recoverySplashRuntime -notmatch 'Class\.forName\(SURFACE_CONTROL\)' -or
    $recoverySplashRuntime -notmatch 'SuccessorPidModel\.resolve' -or
    $recoverySplashRuntime -notmatch 'service\.bootanim\.exit' -or
    $recoverySplashRuntime -notmatch 'SPLASH_TARGET_READY' -or
    $recoverySplashRuntime -notmatch 'native_width=' -or
    $recoverySplashRuntime -notmatch 'surface_width=' -or
    $recoverySplashRuntime -notmatch 'SPLASH_TARGET_STEP' -or
    $recoverySplashRuntime -notmatch 'SPLASH_CURTAIN_DRAW_READY' -or
    $recoverySplashRuntime -notmatch 'SPLASH_CURTAIN_SHOWN' -or
    $recoverySplashRuntime -notmatch 'RECOVERY_CURTAIN_SIZE' -or
    $recoverySplashRuntime -notmatch 'TOP_RECOVERY_WIDTH' -or
    $recoverySplashRuntime -notmatch 'SPLASH_DRAW_BEGIN' -or
    $recoverySplashRuntime -notmatch 'SPLASH_DRAW_READY' -or
    $recoverySplashRuntime -notmatch 'PACKAGED_LOCKUP' -or
    $recoverySplashRuntime -notmatch 'BitmapFactory\.decodeStream' -or
    $recoverySplashRuntime -notmatch 'SPLASH_BOOTANIM_EXIT' -or
    $recoverySplashRuntime -notmatch 'transactionReparentToNull' -or
    $recoverySplashRuntime -notmatch 'TRANSACTION_COMMITTED_LISTENER') {
    throw 'Recovery splash runtime lost bounded target wait, early curtain, Thor geometry, font-free draw path, exact successor or cleanup semantics.'
}
if ($recoverySplashRuntime -match 'setDisplayProjection|setMatrix|setBufferTransform') {
    throw 'Recovery splash geometry fix must not mutate the display projection or add an unproven layer transform.'
}
if ($recoverySplashRuntime -match 'drawText\(' -or
    $recoverySplashRuntime -match 'setTypeface\(' -or
    $recoverySplashRuntime -match 'android\.graphics\.Typeface') {
    throw 'Standalone recovery splash must remain font-free; Thor app_process aborts in Typeface resolution.'
}
if ($cpuFixSource -notmatch '/data/local/tmp/thor-recovery-splash-prototype' -or
    $cpuFixSource -notmatch 'succ\(\)\{' -or
    $cpuFixSource -notmatch 'RecoverySplash' -or
    $cpuFixSource -notmatch 'start_splash' -or
    $cpuFixSource -notmatch 'pid=\$\$;') {
    throw 'Recovery splash prototype must stay explicit and preserve helper trace identity.'
}
$sfTraceAt = $cpuFixSource.IndexOf('HELPER_SF_NEW_PID')
$splashCallAt = $cpuFixSource.LastIndexOf('start_splash')
if ($sfTraceAt -lt 0 -or $splashCallAt -le $sfTraceAt) {
    throw 'Recovery splash may only launch after replacement SurfaceFlinger observation.'
}
if ($recoverySplashProbe -notmatch 'args\.length != 0' -or
    $recoverySplashProbe -notmatch 'PServerBinder' -or
    $recoverySplashProbe -notmatch 'RecoverySplash' -or
    $recoverySplashProbe -notmatch 'DaemonLaunchScript\.MAX_COMMAND_CHARS' -or
    $recoverySplashProbe -notmatch 'COMMAND_TOO_LONG' -or
    $recoverySplashProbe -match 'args\[[0-9]+\].*command|Runtime\.getRuntime\(\)\.exec') {
    throw 'No-reboot splash probe must remain argument-free, fixed-command and vendor-limit bounded.'
}

# Boot-animation suppression is transient, boot-scoped, best-effort and
# independently restored by the helper on every exit path.
if ($cpuFixSource -notmatch 'BOOT_ANIMATION_DISABLE_PROPERTY = "debug\.sf\.nobootanimation"' -or
    $cpuFixSource -notmatch 'previousBootAnimation = armBootAnimationSuppression\(bootTrace\)' -or
    $cpuFixSource -notmatch 'if \(!bootTrace\) return null;' -or
    $cpuFixSource -notmatch 'trap restore_ba EXIT' -or
    $cpuFixSource -notmatch '"restore_ba"' -or
    $cpuFixSource -notmatch '"trap - EXIT"') {
    throw 'Boot-animation suppression must remain boot-scoped and restore its property.'
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
    Select-String -Pattern '0x40446d40c8d6b683|0x40446d4a32a16584|crtc\[(181|243)\]|"(181|243)"|"hall_switch"|dri/0/state')
if ($hardwareLiterals.Count -gt 0) {
    throw "Thor hardware IDs must come from ThorHardwareProfile: $($hardwareLiterals[0].Path):$($hardwareLiterals[0].LineNumber)"
}
$profile = Get-Content -LiteralPath (Join-Path $repository 'src\com\thor\displaypowertest\ThorHardwareProfile.java') -Raw
if ($profile -notmatch 'TOP_LAYER_STACK_SWAPS_MODE_AXES = true' -or
    $profile -notmatch 'TOP_NATIVE_WIDTH = 1080' -or
    $profile -notmatch 'TOP_NATIVE_HEIGHT = 1920' -or
    $profile -notmatch 'TOP_RECOVERY_WIDTH = 1920' -or
    $profile -notmatch 'TOP_RECOVERY_HEIGHT = 1080' -or
    $profile -notmatch 'RECOVERY_CURTAIN_SIZE = 1920') {
    throw 'Thor profile must retain the measured recovery geometry used by the splash prototype.'
}
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
    (Join-Path $repository 'src\com\thor\displaypowertest\EarlyCpuGateModel.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\RecoverySplashModel.java'),
    (Join-Path $repository 'src\com\thor\displaypowertest\SuccessorPidModel.java'),
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
    (Join-Path $repository 'tests\CpuBootAttemptModelTest.java'),
    (Join-Path $repository 'tests\EarlyCpuGateModelTest.java'),
    (Join-Path $repository 'tests\RecoverySplashModelTest.java'),
    (Join-Path $repository 'tests\SuccessorPidModelTest.java')
)
& javac -source 8 -target 8 -d $output $sources
if ($LASTEXITCODE -ne 0) { throw 'Boot/lid test compilation failed.' }
& java -cp $output BootAndLidModelTest
if ($LASTEXITCODE -ne 0) { throw 'Boot/lid tests failed.' }
& java -cp $output CpuBootAttemptModelTest
if ($LASTEXITCODE -ne 0) { throw 'CPU boot attempt model tests failed.' }
& java -cp $output EarlyCpuGateModelTest
if ($LASTEXITCODE -ne 0) { throw 'Early CPU gate model tests failed.' }
& java -cp $output RecoverySplashModelTest
if ($LASTEXITCODE -ne 0) { throw 'Recovery splash model tests failed.' }
& java -cp $output SuccessorPidModelTest
if ($LASTEXITCODE -ne 0) { throw 'Successor PID model tests failed.' }
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
