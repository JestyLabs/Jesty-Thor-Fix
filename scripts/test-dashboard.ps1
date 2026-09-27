[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$output = Join-Path $env:TEMP 'jesty-thor-dashboard-tests'

if (Test-Path -LiteralPath $output) {
    Remove-Item -LiteralPath $output -Recurse -Force
}
New-Item -ItemType Directory -Path $output | Out-Null

$model = Join-Path $repository 'src\com\thor\displaypowertest\DashboardStateModel.java'
$test = Join-Path $repository 'tests\DashboardStateModelTest.java'

& javac -source 8 -target 8 -d $output $model $test
if ($LASTEXITCODE -ne 0) { throw 'Dashboard test compilation failed.' }

& java -cp $output DashboardStateModelTest
if ($LASTEXITCODE -ne 0) { throw 'Dashboard tests failed.' }
