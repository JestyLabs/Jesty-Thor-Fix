[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$output = Join-Path ([IO.Path]::GetTempPath()) ('thor-diagnostics-tests-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $output | Out-Null
$sources = @('AppDiagnosticReport.java', 'AppDiagnostics.java', 'AppTelemetryTransitions.java',
    'PassiveSleepMonitor.java', 'PassiveSleepTrial.java') | ForEach-Object {
    Get-Content -LiteralPath (Join-Path $repository "src/com/thor/displaypowertest/$_") -Raw
}
if (($sources -join "`n") -match 'SocketClient|PServer|Runtime\.getRuntime|ProcessBuilder|getDescription\(|getTraceInputStream\(|getProcessName\(|getPid\(') {
    throw 'App diagnostics must not run commands, query the daemon or export raw exit details.'
}
& javac -source 8 -target 8 -d $output `
    (Join-Path $repository 'src/com/thor/displaypowertest/AppTelemetryTransitions.java') `
    (Join-Path $repository 'src/com/thor/displaypowertest/AppDiagnosticReport.java') `
    (Join-Path $repository 'src/com/thor/displaypowertest/PassiveSleepTrial.java') `
    (Join-Path $repository 'tests/AppDiagnosticReportTest.java') `
    (Join-Path $repository 'tests/PassiveSleepTrialTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Diagnostics tests did not compile.' }
& java -cp $output AppDiagnosticReportTest
if ($LASTEXITCODE -ne 0) { throw 'Diagnostics tests failed.' }
& java -cp $output PassiveSleepTrialTest
if ($LASTEXITCODE -ne 0) { throw 'Passive sleep model tests failed.' }
