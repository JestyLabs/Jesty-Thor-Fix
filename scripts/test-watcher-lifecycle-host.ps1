[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)
$out = Join-Path $tempParent ('jesty-thor-watcher-lifecycle-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $out | Out-Null
try {
    $source = Join-Path $out 'WatcherSupervisor.java'
    $original = [IO.File]::ReadAllText((Join-Path $repo 'src/com/thor/displaypowertest/WatcherSupervisor.java'))
    [IO.File]::WriteAllText($source, $original, [Text.UTF8Encoding]::new($false))
    $sources = @($source,
        (Join-Path $repo 'tests/coordinator/android/os/SystemClock.java'),
        (Join-Path $repo 'tests/coordinator/android/util/Log.java'),
        (Join-Path $repo 'tests/watcher-lifecycle/WatcherSupervisorLifecycleHostTest.java'))
    & javac -encoding UTF-8 -source 8 -target 8 -d $out @sources
    if ($LASTEXITCODE -ne 0) { throw 'Watcher lifecycle compilation failed' }
    & java -cp $out com.thor.displaypowertest.WatcherSupervisorLifecycleHostTest
    if ($LASTEXITCODE -ne 0) { throw 'Watcher lifecycle characterization failed' }
    $PSNativeCommandUseErrorActionPreference = $false
    foreach ($mutant in @(
        @{Name='missing-join'; Before='watcher.join();'; After='Thread.sleep(0L); /* negative control: no join */'; Argument='join'; Expected='WL-join replacement before exit'},
        @{Name='extra-rapid-retry'; Before='while (fastFailures < 3)'; After='while (fastFailures < 4)'; Argument='bound'; Expected='WL-fast-failures count'}
    )) {
        if ([regex]::Matches($original, [regex]::Escape($mutant.Before)).Count -ne 1) {
            throw 'Lifecycle mutation target changed'
        }
        [IO.File]::WriteAllText($source, $original.Replace($mutant.Before, $mutant.After), [Text.UTF8Encoding]::new($false))
        & javac -encoding UTF-8 -source 8 -target 8 -d $out @sources
        if ($LASTEXITCODE -ne 0) { throw 'Lifecycle negative control must compile' }
        $observed = (& java -cp $out com.thor.displaypowertest.WatcherSupervisorLifecycleHostTest $mutant.Argument 2>&1 |
            ForEach-Object { "$_" }) -join "`n"
        if ($LASTEXITCODE -eq 0 -or -not $observed.Contains($mutant.Expected)) {
            throw "Lifecycle negative control failed: $($mutant.Name): $observed"
        }
        Write-Host "S1-11 NEGATIVE_CONTROL PASS: $($mutant.Name), rejected at $($mutant.Expected)"
    }
} finally {
    if (Test-Path -LiteralPath $out) {
        $resolved = (Resolve-Path -LiteralPath $out).Path
        if ([IO.Path]::GetDirectoryName($resolved) -ne $tempParent -or
                (Get-Item -LiteralPath $out).Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw 'Refusing unexpected lifecycle fixture cleanup target'
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
$global:LASTEXITCODE = 0
