[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$output = Join-Path ([IO.Path]::GetTempPath()) ('jesty-thor-dashboard-tests-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $output | Out-Null
try {

# The updater is an ordinary app install; it must never reach the root daemon or a shell.
$updaterSources = @('AppUpdater.java', 'UpdateArchiveVerifier.java', 'UpdateReleaseCatalog.java', 'UpdateInstallReceiver.java', 'UpdateVersion.java',
    'UpdateCommitGate.java') |
    ForEach-Object { Get-Content -LiteralPath (Join-Path $repository "src\com\thor\displaypowertest\$_") -Raw }
if (($updaterSources -join "`n") -match 'SocketClient|PServer|Runtime\.getRuntime|ProcessBuilder|"su"') {
    throw 'The in-app updater must not use the root daemon, su or a shell.'
}

# MainActivity coordinates lifecycle only: no daemon requests and no view building.
$activity = Get-Content -LiteralPath (Join-Path $repository 'src\com\thor\displaypowertest\MainActivity.java') -Raw
if ($activity -match 'SocketClient|new (TextView|LinearLayout|Switch|MediaPlayer)\(') {
    throw 'MainActivity must delegate daemon commands, telemetry, layout and media.'
}

$idleSettings = Join-Path $repository 'src\com\thor\displaypowertest\BottomIdleSettings.java'
$idlePolicy = Join-Path $repository 'src\com\thor\displaypowertest\BottomIdlePolicy.java'
$idleTest = Join-Path $repository 'tests\BottomIdlePolicyTest.java'
$model = Join-Path $repository 'src\com\thor\displaypowertest\DashboardStateModel.java'
$test = Join-Path $repository 'tests\DashboardStateModelTest.java'
$warningModel = Join-Path $repository 'src\com\thor\displaypowertest\CpuWarningModel.java'
$warningTest = Join-Path $repository 'tests\CpuWarningModelTest.java'
$updateModel = Join-Path $repository 'src\com\thor\displaypowertest\UpdateVersion.java'
$updateTest = Join-Path $repository 'tests\UpdateVersionTest.java'
$commitGateModel = Join-Path $repository 'src\com\thor\displaypowertest\UpdateCommitGate.java'
$commitGateTest = Join-Path $repository 'tests\UpdateCommitGateTest.java'
$dispatchModel = Join-Path $repository 'src\com\thor\displaypowertest\UpdateTaskDispatch.java'
$dispatchTest = Join-Path $repository 'tests\com\thor\displaypowertest\UpdateTaskDispatchTest.java'
$confirmationModel = Join-Path $repository 'src\com\thor\displaypowertest\UpdateConfirmationStore.java'
$confirmationTest = Join-Path $repository 'tests\com\thor\displaypowertest\UpdateConfirmationStoreTest.java'
$sessionModel = Join-Path $repository 'src\com\thor\displaypowertest\UpdateSessionOwnership.java'
$sessionTest = Join-Path $repository 'tests\com\thor\displaypowertest\UpdateSessionOwnershipTest.java'
$replacementModel = Join-Path $repository 'src\com\thor\displaypowertest\ReplacementSessionPolicy.java'
$replacementTest = Join-Path $repository 'tests\com\thor\displaypowertest\ReplacementSessionPolicyTest.java'
$readiness = Join-Path $repository 'src\com\thor\displaypowertest\UpdateReadiness.java'
$readinessTest = Join-Path $repository 'tests\com\thor\displaypowertest\UpdateReadinessTest.java'
$activationPolicy = Join-Path $repository 'src\com\thor\displaypowertest\RebootActivationPolicy.java'
$identity = Join-Path $repository 'src\com\thor\displaypowertest\DaemonIdentity.java'
$activationTest = Join-Path $repository 'tests\RebootActivationPolicyTest.java'
$clockFixture = Join-Path $repository 'tests\fixtures\android\os\SystemClock.java'

& javac -source 8 -target 8 -d $output $idleSettings $idlePolicy $idleTest $model $test $warningModel $warningTest $updateModel $updateTest $commitGateModel $commitGateTest $readiness $readinessTest $clockFixture $confirmationModel $confirmationTest $sessionModel $sessionTest $replacementModel $replacementTest $dispatchModel $dispatchTest $activationPolicy $identity $activationTest
if ($LASTEXITCODE -ne 0) { throw 'Dashboard test compilation failed.' }

& java -cp $output BottomIdlePolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Bottom idle policy tests failed.' }
& java -cp $output DashboardStateModelTest
if ($LASTEXITCODE -ne 0) { throw 'Dashboard tests failed.' }
& java -cp $output CpuWarningModelTest
if ($LASTEXITCODE -ne 0) { throw 'CPU warning tests failed.' }
& java -cp $output RebootActivationPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Reboot activation policy tests failed.' }
& java -cp $output UpdateVersionTest
if ($LASTEXITCODE -ne 0) { throw 'Update version tests failed.' }
& java -cp $output UpdateCommitGateTest
if ($LASTEXITCODE -ne 0) { throw 'Update commit cancellation tests failed.' }
& java -cp $output com.thor.displaypowertest.UpdateTaskDispatchTest
if ($LASTEXITCODE -ne 0) { throw 'Update queued task ownership tests failed.' }

& java -cp $output com.thor.displaypowertest.UpdateReadinessTest
if ($LASTEXITCODE -ne 0) { throw 'Updater readiness lifecycle tests failed.' }

& java -cp $output com.thor.displaypowertest.UpdateConfirmationStoreTest
if ($LASTEXITCODE -ne 0) { throw 'Updater confirmation lifecycle characterization failed.' }
& java -cp $output com.thor.displaypowertest.UpdateSessionOwnershipTest
if ($LASTEXITCODE -ne 0) { throw 'Updater session ownership tests failed.' }
& java -cp $output com.thor.displaypowertest.ReplacementSessionPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Android session replacement policy failed.' }

# One real event mutates the policy twice (touch + pointer edge). A queued
# restoration must capture its lease only AFTER both changes, otherwise the
# first ACTION_DOWN / ACTION_UP instantly invalidates its own restore.
$runtimeSource = Get-Content -LiteralPath (Join-Path $repository 'src/com/thor/displaypowertest/BottomIdleRuntime.java') -Raw
$touchStart = $runtimeSource.IndexOf('public static void onBottomTouch(')
$touchEnd = $runtimeSource.IndexOf('public static void onInputFailure(', $touchStart)
if ($touchStart -lt 0 -or $touchEnd -le $touchStart) {
    throw 'Missing production idle touch callback; restore ordering unverified.'
}
$touchBody = $runtimeSource.Substring($touchStart, $touchEnd - $touchStart)
$touchMutation = $touchBody.IndexOf('POLICY.pointerDown(false);')
$leaseCapture = $touchBody.IndexOf('restoreLease = restore ? POLICY.generation() : -1L;')
if ($touchMutation -lt 0 -or $leaseCapture -le $touchMutation) {
    throw 'Idle touch restore lease must be captured after the DOWN/UP policy mutation.'
}

# Static UI contract: when switches have no visible text, their screen-reader
# labels must come from their adjacent feature heading; do not couple a TalkBack
# fix to the daemon, updater installer or hardware.
$viewsSource = Get-Content -LiteralPath (Join-Path $repository 'src\com\thor\displaypowertest\DashboardViews.java') -Raw
$layoutSource = Get-Content -LiteralPath (Join-Path $repository 'src\com\thor\displaypowertest\DashboardLayout.java') -Raw
if ($viewsSource -notmatch 'toggle\.setContentDescription\(title\)' -or
    $viewsSource -notmatch 'action\.setFocusable\(true\)' -or
    $layoutSource -notmatch 'github\.setContentDescription\("GitHub project\. Long press for update settings"\)' -or
    $layoutSource -notmatch 'updateAction\.setContentDescription\("View available software update"\)' -or
    $layoutSource -notmatch 'backgroundImage\.setImportantForAccessibility\(View\.IMPORTANT_FOR_ACCESSIBILITY_NO\)' -or
    $layoutSource -notmatch 'videoTexture\.setImportantForAccessibility\(View\.IMPORTANT_FOR_ACCESSIBILITY_NO\)') {
    throw 'Dashboard accessibility baseline regressed (static contract only; TalkBack not yet run).'
}

& (Join-Path $PSScriptRoot 'test-build-source-boundary.ps1')

# The emulator checks PackageInstaller; this host harness covers the actual
# UpdateInstallReceiver callbacks, with disposable Android test-only placeholders.
& (Join-Path $PSScriptRoot 'test-updater-receiver-host.ps1')

# The production AppUpdater and receiver are compiled together with Android 34
# and exercised through disposable host UI/PackageInstaller adapters. This is
# separate from the Android emulator's actual PackageInstaller session harness.
& (Join-Path $PSScriptRoot 'test-updater-coupling.ps1')
} finally {
    if (Test-Path -LiteralPath $output) {
        Remove-Item -LiteralPath $output -Recurse -Force
    }
}
