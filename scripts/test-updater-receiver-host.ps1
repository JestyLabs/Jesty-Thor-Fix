[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$fixtures = Join-Path $repository 'tests\fixtures\receiver'
$target = Join-Path ([IO.Path]::GetTempPath()) ('jesty-updater-receiver-' + [Guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path $target | Out-Null
    $sources = @(
        (Join-Path $repository 'src\com\thor\displaypowertest\UpdateInstallReceiver.java'),
        (Join-Path $repository 'tests\com\thor\displaypowertest\UpdateInstallReceiverHostTest.java')
    ) + @(Get-ChildItem -LiteralPath $fixtures -Recurse -Filter '*.java' -File | ForEach-Object FullName)
    & javac -encoding UTF-8 -source 8 -target 8 -d $target $sources
    if ($LASTEXITCODE -ne 0) { throw 'Updater receiver host harness compilation failed.' }
    & java -cp $target com.thor.displaypowertest.UpdateInstallReceiverHostTest
    if ($LASTEXITCODE -ne 0) { throw 'Real receiver callback host tests failed.' }
} finally {
    if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
}
