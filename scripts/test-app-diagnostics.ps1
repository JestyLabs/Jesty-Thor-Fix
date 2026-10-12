[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$output = Join-Path ([IO.Path]::GetTempPath()) ('thor-diagnostics-tests-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $output | Out-Null
$sources = @('AppDiagnosticReport.java', 'AppDiagnostics.java', 'AppTelemetryTransitions.java', 'AppDiagnosticPreview.java', 'PassiveSleepMonitor.java', 'PassiveSleepTrial.java') | ForEach-Object {
    Get-Content -LiteralPath (Join-Path $repository "src/com/thor/displaypowertest/$_") -Raw
}
if (($sources -join "`n") -match 'SocketClient|PServer|Runtime\.getRuntime|ProcessBuilder|getDescription\(|getTraceInputStream\(|getProcessName\(|getPid\(') {
    throw 'App diagnostics must not run commands, query the daemon or export raw exit details.'
}
# Diagnostics are on-demand UI only: one gear action, existing foreground Q
# consumer, no independent scheduler; optional one-shot Activity sleep endpoints.
$layout = Get-Content -LiteralPath (Join-Path $repository 'src/com/thor/displaypowertest/DashboardLayout.java') -Raw
$main = Get-Content -LiteralPath (Join-Path $repository 'src/com/thor/displaypowertest/MainActivity.java') -Raw
$diagnostics = Get-Content -LiteralPath (Join-Path $repository 'src/com/thor/displaypowertest/AppDiagnostics.java') -Raw
if ($layout -notmatch 'android\.R\.drawable\.ic_menu_manage' -or
    $layout -notmatch 'setContentDescription\("App diagnostics"\)' -or
    $layout -match 'topAction\("APP DIAGNOSTICS"\)') {
    throw 'Expected an accessible cogwheel instead of a text diagnostics button.'
}
if ($main -notmatch 'onSampleRecorded\(Map<String, String> values\)' -or
    $main -notmatch 'diagnostics\.sample\(values\)' -or
    $main -notmatch 'diagnostics\.onPause\(\)' -or
    $main -notmatch 'diagnostics\.onResume\(\)') {
    throw 'Diagnostics must reuse the existing foreground Q listener without sleep polling.'
}
if ($diagnostics -match 'ScheduledExecutorService|scheduleAtFixedRate|new Timer\(' -or
    $diagnostics -notmatch 'AppDiagnosticPreview\.format\(text\)' -or
    $diagnostics -notmatch 'setNeutralButton\("Sleep trial"') {
    throw 'App diagnostics must not add a timer or periodic telemetry request.'
}

$build = Get-Content -LiteralPath (Join-Path $repository 'build.ps1') -Raw
$compilers = [regex]::Matches($build, '(?m)^& \$javac\b[^\r\n]*')
if ($compilers.Count -ne 2 -or @($compilers | Where-Object {
        $_.Value -notmatch '-encoding\s+UTF-8\b'
    }).Count -ne 0) {
    throw 'Every production javac invocation must use explicit UTF-8 source encoding.'
}

& javac -encoding UTF-8 -source 8 -target 8 -d $output `
    (Join-Path $repository 'src/com/thor/displaypowertest/AppTelemetryTransitions.java') `
    (Join-Path $repository 'src/com/thor/displaypowertest/AppDiagnosticReport.java') `
    (Join-Path $repository 'src/com/thor/displaypowertest/AppDiagnosticPreview.java') `
    (Join-Path $repository 'src/com/thor/displaypowertest/PassiveSleepTrial.java') `
        (Join-Path $repository 'tests/AppDiagnosticReportTest.java') `
    (Join-Path $repository 'tests/AppDiagnosticPreviewTest.java') `
    (Join-Path $repository 'tests/PassiveSleepTrialTest.java') `
    (Join-Path $repository 'tests/PassiveSleepReportCompatibilityTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Diagnostics tests did not compile.' }
& java -cp $output AppDiagnosticReportTest
if ($LASTEXITCODE -ne 0) { throw 'Diagnostics tests failed.' }
& java -cp $output AppDiagnosticPreviewTest
if ($LASTEXITCODE -ne 0) { throw 'Diagnostics preview tests failed.' }

& java -cp $output PassiveSleepTrialTest
if ($LASTEXITCODE -ne 0) { throw 'Passive sleep model tests failed.' }
& java -cp $output PassiveSleepReportCompatibilityTest
if ($LASTEXITCODE -ne 0) { throw 'Passive sleep V1 export compatibility tests failed.' }
