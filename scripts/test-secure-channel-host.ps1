[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$fixture = Join-Path $repository 'tests/fixtures/securechannel'
$tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)
$output = Join-Path $tempParent ('jesty-secure-channel-host-' + [guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path $output | Out-Null
    $sources = @(
        (Join-Path $repository 'src/com/thor/displaypowertest/SecureChannel.java'),
        (Join-Path $repository 'src/com/thor/displaypowertest/IpcPeerPolicy.java'),
        (Join-Path $repository 'src/com/thor/displaypowertest/SocketClient.java'),
        (Join-Path $repository 'src/com/thor/displaypowertest/DaemonResponsePolicy.java'),
        (Join-Path $repository 'tests/com/thor/displaypowertest/SecureChannelHostTest.java'),
        (Join-Path $repository 'tests/com/thor/displaypowertest/SocketClientHostTest.java')
    ) + @(Get-ChildItem -LiteralPath $fixture -Filter '*.java' -Recurse -File |
            ForEach-Object FullName)
    & javac -encoding UTF-8 -source 8 -target 8 -d $output @sources
    if ($LASTEXITCODE -ne 0) { throw 'Real SecureChannel host harness compilation failed.' }
    & java -cp $output com.thor.displaypowertest.SecureChannelHostTest
    if ($LASTEXITCODE -ne 0) { throw 'Real SecureChannel host harness failed.' }
    & java -cp $output com.thor.displaypowertest.SocketClientHostTest
    if ($LASTEXITCODE -ne 0) { throw 'S3 real socket-client host harness failed.' }
    $PSNativeCommandUseErrorActionPreference = $false
    foreach ($mutant in @(
        @{File='SecureChannel.java'; Before='if (peer == null || !IpcPeerPolicy.trustedDaemon(peer.getUid()))'; After='if (false)'; Argument='peer'; Expected='S3-client-untrusted rejects success'},
        @{File='SocketClient.java'; Before='if (!DaemonResponsePolicy.isPositiveAck(response))'; After='if (false)'; Argument='ack'; Expected='S3-client-malformed-ack rejects success'}
    )) {
        $originalPath = Join-Path $repository ('src/com/thor/displaypowertest/' + $mutant.File)
        $original = [IO.File]::ReadAllText($originalPath)
        if ([regex]::Matches($original, [regex]::Escape($mutant.Before)).Count -ne 1) { throw 'S3 mutation target changed' }
        $temporary = Join-Path $output $mutant.File
        [IO.File]::WriteAllText($temporary, $original.Replace($mutant.Before, $mutant.After), [Text.UTF8Encoding]::new($false))
        $mutantSources = @($sources | Where-Object { $_ -ne $originalPath }) + @($temporary)
        & javac -encoding UTF-8 -source 8 -target 8 -d $output @mutantSources
        if ($LASTEXITCODE -ne 0) { throw 'S3 negative control must compile' }
        $observed = (& java -cp $output com.thor.displaypowertest.SocketClientHostTest $mutant.Argument 2>&1 |
            ForEach-Object { "$_" }) -join "`n"
        if ($LASTEXITCODE -eq 0 -or -not $observed.Contains($mutant.Expected)) {
            throw "S3 negative control failed: $($mutant.File): $observed"
        }
        Write-Host "S3 NEGATIVE_CONTROL PASS: $($mutant.File), rejected at $($mutant.Expected)"
        # Restore compiled classes before the next independent mutation.
        & javac -encoding UTF-8 -source 8 -target 8 -d $output @sources
        if ($LASTEXITCODE -ne 0) { throw 'S3 baseline restoration failed' }
    }
} finally {
    if (Test-Path -LiteralPath $output) {
        $resolved = (Resolve-Path -LiteralPath $output).Path
        if ([IO.Path]::GetDirectoryName($resolved) -ne $tempParent -or
                (Get-Item -LiteralPath $output).Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw 'Refusing unexpected secure-channel fixture cleanup target'
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
$global:LASTEXITCODE = 0
