[CmdletBinding()]
param([switch]$RunNegativeControls)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)
$out = Join-Path $tempParent ('jesty-cpu-resume-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $out | Out-Null
try {
    $src = Join-Path $repo 'src/com/thor/displaypowertest'
    $names = @('CpuFixController', 'CpuBootAttemptModel', 'EarlyCpuAttemptImportModel',
        'PropertyState', 'BootSession', 'HandoffRecoveryModel')
    $extracted = Test-Path -LiteralPath (Join-Path $src 'CpuAttemptResumer.java')
    if ($extracted) { $names += 'CpuAttemptResumer' }
    $waitOwner = if ($extracted) { 'CpuAttemptResumer' } else { 'CpuFixController' }
    $sources = @()
    foreach ($name in $names) {
        $source = [IO.File]::ReadAllText((Join-Path $src ($name + '.java')))
        if ($name -eq $waitOwner) {
            if ([regex]::Matches($source, [regex]::Escape('Thread.sleep(200L);')).Count -ne 1) {
                throw 'Expected one resume wait boundary; review adapter'
            }
            $source = $source.Replace('Thread.sleep(200L);', 'ResumeWait.pause();')
        }
        $path = Join-Path $out ($name + '.java')
        [IO.File]::WriteAllText($path, $source, [Text.UTF8Encoding]::new($false))
        $sources += $path
    }
    $sources += @((Join-Path $repo 'tests/coordinator/android/os/SystemClock.java'))
    $sources += @(Get-ChildItem -LiteralPath (Join-Path $repo 'tests/cpu-resume') -Filter '*.java' -Recurse | ForEach-Object FullName)
    & javac -encoding UTF-8 -source 8 -target 8 -d $out @sources
    if ($LASTEXITCODE -ne 0) { throw 'Real CPU controller resume fixture compilation failed' }
    & java -cp $out com.thor.displaypowertest.ResumeControllerHostTest
    if ($LASTEXITCODE -ne 0) { throw 'Real CPU controller resume fixture failed' }
    if ($RunNegativeControls) {
        $path = Join-Path $out ($waitOwner + '.java')
        $original = [IO.File]::ReadAllText($path)
        $PSNativeCommandUseErrorActionPreference = $false
        foreach ($mutant in @(
            @{Name='extra-attempt'; Before='poll < 130'; After='poll < 131'; Argument='bound'; Expected='CR-exhausted attempts'},
            @{Name='missing-terminal-release'; Before='releaseWakeLockUnlessRestartScheduled();'; After='/* negative control: omitted release */'; Argument='terminal'; Expected='CR-terminal wake release'}
        )) {
            if ($extracted -and $mutant.Name -eq 'missing-terminal-release') {
                $mutant.Before = 'releaseWakeLock.run();'
            }
            # Both success and terminal branches share release behavior. This
            # mutation deliberately removes both; the terminal case must catch it.
            if (-not $original.Contains($mutant.Before)) { throw 'Resume mutation target changed' }
            [IO.File]::WriteAllText($path, $original.Replace($mutant.Before, $mutant.After), [Text.UTF8Encoding]::new($false))
            & javac -encoding UTF-8 -source 8 -target 8 -d $out @sources
            if ($LASTEXITCODE -ne 0) { throw 'Resume negative control must compile' }
            $observed = (& java -cp $out com.thor.displaypowertest.ResumeControllerHostTest $mutant.Argument 2>&1 |
                ForEach-Object { "$_" }) -join "`n"
            if ($LASTEXITCODE -eq 0 -or -not $observed.Contains($mutant.Expected)) {
                throw "Resume negative control failed: $($mutant.Name): $observed"
            }
            Write-Host "S4 NEGATIVE_CONTROL PASS: $($mutant.Name), rejected at $($mutant.Expected)"
            [IO.File]::WriteAllText($path, $original, [Text.UTF8Encoding]::new($false))
        }
    }
    Write-Host 'S4 wait adapter records 200ms wait requests; wall-clock cadence/native scheduling NOT VERIFIED.'
} finally {
    if (Test-Path -LiteralPath $out) {
        $resolved = (Resolve-Path -LiteralPath $out).Path
        if ([IO.Path]::GetDirectoryName($resolved) -ne $tempParent -or
                (Get-Item -LiteralPath $out).Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw 'Refusing unexpected CPU fixture cleanup target'
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
$global:LASTEXITCODE = 0
