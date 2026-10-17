[CmdletBinding()]
param()

# S1: real DisplayActionCoordinator source with strictly inert host boundaries.
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$out = Join-Path ([IO.Path]::GetTempPath()) ('jesty-thor-s1-coordinator-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $out -Force | Out-Null
try {
    $src = Join-Path $repo 'src/com/thor/displaypowertest'
    $fixture = Join-Path $repo 'tests/coordinator'
    $sources = @(
        (Join-Path $src 'DisplayActionCoordinator.java'),
        (Join-Path $src 'DisplayDecisionModel.java'),
        (Join-Path $src 'DisplayGenerationModel.java'),
        (Join-Path $fixture 'android/os/SystemClock.java'),
        (Join-Path $fixture 'android/util/Log.java'),
        (Join-Path $fixture 'CoordinatorHostDoubles.java'),
        (Join-Path $fixture 'DisplayActionCoordinatorHostTest.java')
    )
    & javac -encoding UTF-8 -source 8 -target 8 -d $out @sources
    if ($LASTEXITCODE -ne 0) { throw 'S1 coordinator host compilation failed' }
    & java -cp $out com.thor.displaypowertest.DisplayActionCoordinatorHostTest
    if ($LASTEXITCODE -ne 0) { throw 'S1 real coordinator characterization failed' }
    Write-Host 'S1 real coordinator host-only characterization PASS'
} finally {
    if (Test-Path -LiteralPath $out) {
        Remove-Item -LiteralPath $out -Recurse -Force
    }
}
