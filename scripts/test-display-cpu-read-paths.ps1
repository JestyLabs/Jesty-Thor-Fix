[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$temp = Join-Path $env:TEMP ('jesty-thor-display-cpu-host-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp | Out-Null

try {
    $src = Join-Path $repo 'src\com\thor\displaypowertest'
    $tests = Join-Path $repo 'tests'
    $sources = @(
        'ThorHardwareProfile.java',
        'DashboardStateModel.java',
        'PropertyState.java',
        'ProcessWait.java',
        'Telemetry.java',
        'DisplayDecisionModel.java',
        'DisplayGenerationModel.java',
        'WatcherCadenceModel.java'
    ) | ForEach-Object { Join-Path $src $_ }
    $sources += @(
        (Join-Path $tests 'fixtures\android\os\SystemProperties.java'),
        (Join-Path $tests 'TelemetrySnapshotTest.java'),
        (Join-Path $tests 'DisplayOptimizationSafetyTest.java'),
        (Join-Path $tests 'DisplayDecisionModelTest.java'),
        (Join-Path $tests 'DisplayGenerationModelTest.java'),
        (Join-Path $tests 'WatcherCadenceModelTest.java'),
        (Join-Path $tests 'PropertyStateTest.java')
    )
    & javac -d $temp @sources
    if ($LASTEXITCODE -ne 0) { throw 'Java host compilation failed' }

    foreach ($name in @(
        'TelemetrySnapshotTest',
        'DisplayOptimizationSafetyTest',
        'DisplayDecisionModelTest',
        'DisplayGenerationModelTest',
        'WatcherCadenceModelTest',
        'PropertyStateTest'
    )) {
        & java -cp $temp $name
        if ($LASTEXITCODE -ne 0) { throw "$name failed" }
    }
    Write-Host 'Display/CPU read path host checks PASS'
}
finally {
    if (Test-Path -LiteralPath $temp) {
        Remove-Item -LiteralPath $temp -Recurse -Force
    }
}
