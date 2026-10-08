[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$output = Join-Path $env:TEMP 'jesty-thor-dashboard-tests'

if (Test-Path -LiteralPath $output) {
    Remove-Item -LiteralPath $output -Recurse -Force
}
New-Item -ItemType Directory -Path $output | Out-Null

# The updater is an ordinary app install; it must never reach the root daemon or a shell.
$updaterSources = @('AppUpdater.java', 'UpdateInstallReceiver.java', 'UpdateVersion.java',
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

$model = Join-Path $repository 'src\com\thor\displaypowertest\DashboardStateModel.java'
$test = Join-Path $repository 'tests\DashboardStateModelTest.java'
$warningModel = Join-Path $repository 'src\com\thor\displaypowertest\CpuWarningModel.java'
$warningTest = Join-Path $repository 'tests\CpuWarningModelTest.java'
$updateModel = Join-Path $repository 'src\com\thor\displaypowertest\UpdateVersion.java'
$updateTest = Join-Path $repository 'tests\UpdateVersionTest.java'
$commitGateModel = Join-Path $repository 'src\com\thor\displaypowertest\UpdateCommitGate.java'
$commitGateTest = Join-Path $repository 'tests\UpdateCommitGateTest.java'

& javac -source 8 -target 8 -d $output $model $test $warningModel $warningTest $updateModel $updateTest $commitGateModel $commitGateTest
if ($LASTEXITCODE -ne 0) { throw 'Dashboard test compilation failed.' }

& java -cp $output DashboardStateModelTest
if ($LASTEXITCODE -ne 0) { throw 'Dashboard tests failed.' }
& java -cp $output CpuWarningModelTest
if ($LASTEXITCODE -ne 0) { throw 'CPU warning tests failed.' }
& java -cp $output UpdateVersionTest
if ($LASTEXITCODE -ne 0) { throw 'Update version tests failed.' }
& java -cp $output UpdateCommitGateTest
if ($LASTEXITCODE -ne 0) { throw 'Update commit cancellation tests failed.' }
