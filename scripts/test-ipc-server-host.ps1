[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$fixture = Join-Path $repository 'tests\fixtures\ipcserver'
$output = Join-Path ([IO.Path]::GetTempPath()) ('jesty-ipc-server-host-' + [Guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path $output | Out-Null
    $sources = @(
        (Join-Path $repository 'src\com\thor\displaypowertest\DaemonIpcServer.java'),
        (Join-Path $repository 'tests\com\thor\displaypowertest\DaemonIpcServerHostTest.java')
    ) + @(Get-ChildItem -LiteralPath $fixture -Filter '*.java' -Recurse -File |
            ForEach-Object FullName)
    & javac -encoding UTF-8 -source 8 -target 8 -d $output $sources
    if ($LASTEXITCODE -ne 0) { throw 'Production IPC server host harness compilation failed.' }
    & java -cp $output com.thor.displaypowertest.DaemonIpcServerHostTest
    if ($LASTEXITCODE -ne 0) { throw 'Production IPC server host harness failed.' }
} finally {
    if (Test-Path -LiteralPath $output) {
        Remove-Item -LiteralPath $output -Recurse -Force
    }
}
