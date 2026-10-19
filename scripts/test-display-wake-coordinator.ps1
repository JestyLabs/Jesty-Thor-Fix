[CmdletBinding()]
param([string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path ([IO.Path]::GetTempPath()) ('thor-wake-host-' + [guid]::NewGuid().ToString('N'))
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$source = Join-Path $repo 'src/com/thor/displaypowertest'
$test = Join-Path $repo 'tests/integration/display-wake'
$callback = Get-Content -LiteralPath (Join-Path $repo 'apk/smali/com/thor/displaypowertest/DisplayEventCallback.smali') -Raw
if ($callback -notmatch 'DisplayActionCoordinator;->isWakeRepairPending\(\)Z' -or
    $callback -match 'display.power.state|SystemProperties;->get') {
    throw 'Display callback must use internal pending state instead of the firmware power flag'
}
$sources = @('DisplayActionCoordinator.java','DisplayHardware.java','DisplayDecisionModel.java',
    'DisplayGenerationModel.java','ThorHardwareProfile.java') | ForEach-Object { Join-Path $source $_ }
$sources += @(Get-ChildItem (Join-Path $test 'fixtures') -Recurse -File -Filter '*.java' | Select-Object -ExpandProperty FullName)
$sources += Join-Path $test 'DisplayWakeCoordinatorTest.java'
& javac -d $OutputDirectory @sources
if ($LASTEXITCODE -ne 0) { throw 'Coordinator host compilation failed' }
& java -cp $OutputDirectory com.thor.displaypowertest.DisplayWakeCoordinatorTest
if ($LASTEXITCODE -ne 0) { throw 'Coordinator host regression failed' }
