[CmdletBinding()]
param()

# S1: real DisplayActionCoordinator source with strictly inert host boundaries.
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$out = Join-Path ([IO.Path]::GetTempPath()) ('jesty-thor-s1-coordinator-' + [Guid]::NewGuid().ToString('N'))
$tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)
New-Item -ItemType Directory -Path $out -Force | Out-Null
try {
    $src = Join-Path $repo 'src/com/thor/displaypowertest'
    $fixture = Join-Path $repo 'tests/coordinator'
    $sources = @(
        (Join-Path $src 'DisplayActionCoordinator.java'),
        (Join-Path $src 'DisplayModeReader.java'),
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

    # Main #82 deliberately removed the firmware-property pending marker.
    # Reinserting that old write must fail the retained stable-wake case.
    $coordinator = [IO.File]::ReadAllText((Join-Path $src 'DisplayActionCoordinator.java'))
    $schedule = 'private static void scheduleRepairLocked() {'
    if ([regex]::Matches($coordinator, [regex]::Escape($schedule)).Count -ne 1) {
        throw 'Wake marker negative control target changed'
    }
    $wakeDir = Join-Path $out 'wake-marker-source'
    $wakeClasses = Join-Path $out 'wake-marker-classes'
    New-Item -ItemType Directory -Path $wakeDir, $wakeClasses | Out-Null
    $wakeSource = Join-Path $wakeDir 'DisplayActionCoordinator.java'
    [IO.File]::WriteAllText($wakeSource,
        $coordinator.Replace($schedule, $schedule + ' DisplayHardware.markWakePending();'),
        [Text.UTF8Encoding]::new($false))
    $wakeSources = @($sources | Where-Object { $_ -ne (Join-Path $src 'DisplayActionCoordinator.java') }) + @($wakeSource)
    & javac -encoding UTF-8 -source 8 -target 8 -d $wakeClasses @wakeSources
    if ($LASTEXITCODE -ne 0) { throw 'Wake marker negative control must compile' }
    $PSNativeCommandUseErrorActionPreference = $false
    $observed = (& java -cp $wakeClasses com.thor.displaypowertest.DisplayActionCoordinatorHostTest 2>&1 |
        ForEach-Object { "$_" }) -join "`n"
    if ($LASTEXITCODE -eq 0 -or -not $observed.Contains('stable TOP wake preserves firmware property')) {
        throw "Wake marker negative control not detected: $observed"
    }
    Write-Host 'S1 NEGATIVE_CONTROL PASS: old firmware pending write rejected by preserved-property assertion'

    # The unchanged suite above deliberately cannot reach Settings on a host.
    # For S1-09/10, replace that OS process-read boundary in a temporary copy.
    # Admission, CRTC reads, applyConfirmed, generation and receipts stay real.
    $original = [IO.File]::ReadAllText((Join-Path $src 'DisplayActionCoordinator.java'))
    $signature = '    private static String readCurrentMode() {'
    $start = $original.IndexOf($signature)
    if ($start -lt 0 -or $original.LastIndexOf($signature) -ne $start) {
        throw 'Expected exactly one Settings read boundary'
    }
    $open = $original.IndexOf('{', $start)
    $depth = 1; $end = $open + 1
    while ($depth -gt 0 -and $end -lt $original.Length) {
        if ($original[$end] -eq '{') { $depth++ }
        if ($original[$end] -eq '}') { $depth-- }
        $end++
    }
    if ($depth -ne 0 -or $original.Substring($start, $end - $start) -notmatch 'return DisplayModeReader\.current\(\);') {
        throw 'Settings adapter boundary changed; review before adapting'
    }
    $adapted = $original.Substring(0, $start) +
        '    private static String readCurrentMode() { return ReadbackMode.next(); }' +
        $original.Substring($end)
    $adapterDir = Join-Path $out 'readback-source'
    $classes = Join-Path $out 'readback-classes'
    New-Item -ItemType Directory -Path $adapterDir, $classes | Out-Null
    $adapter = Join-Path $adapterDir 'DisplayActionCoordinator.java'
    [IO.File]::WriteAllText($adapter, $adapted, [Text.UTF8Encoding]::new($false))
    $readbackSources = @($sources | Where-Object {
        $_ -ne (Join-Path $src 'DisplayActionCoordinator.java') -and
        $_ -ne (Join-Path $fixture 'DisplayActionCoordinatorHostTest.java')
    }) + @($adapter, (Join-Path $fixture 'CoordinatorReadbackHostTest.java'),
        (Join-Path $fixture 'CoordinatorFailureHostTest.java'))
    & javac -encoding UTF-8 -source 8 -target 8 -d $classes @readbackSources
    if ($LASTEXITCODE -ne 0) { throw 'S1 readback adapter compilation failed' }
    & java -cp $classes com.thor.displaypowertest.CoordinatorReadbackHostTest
    if ($LASTEXITCODE -ne 0) { throw 'S1 readback characterization failed' }
    Write-Host 'S1-09 adapter: Settings reads scripted in temporary copy; no Android process or vendor execution.'
    & java -cp $classes com.thor.displaypowertest.CoordinatorFailureHostTest
    if ($LASTEXITCODE -ne 0) { throw 'S1 failure characterization failed' }

    $PSNativeCommandUseErrorActionPreference = $false
    foreach ($mutant in @(
        @{Name='missing-final-bottom'; Before='&& expected.equals(finalBottom)'; After='&& true'; Expected='S1-RB06-final-bottom'},
        @{Name='missing-final-mode'; Before='boolean confirmed = expectedMode.equals(readCurrentMode())'; After='boolean confirmed = true'; Expected='S1-RB05-final-mode'; Argument='final-mode'}
    )) {
        if ([regex]::Matches($adapted, [regex]::Escape($mutant.Before)).Count -ne 1) {
            throw 'Negative control target changed'
        }
        try {
            [IO.File]::WriteAllText($adapter, $adapted.Replace($mutant.Before, $mutant.After), [Text.UTF8Encoding]::new($false))
            & javac -encoding UTF-8 -source 8 -target 8 -d $classes @readbackSources
            if ($LASTEXITCODE -ne 0) { throw 'Readback negative control must compile' }
            $testArguments = @()
            if ($mutant.Argument) { $testArguments = @($mutant.Argument) }
            $observed = (& java -cp $classes com.thor.displaypowertest.CoordinatorReadbackHostTest @testArguments 2>&1 |
                ForEach-Object { "$_" }) -join "`n"
            if ($LASTEXITCODE -eq 0 -or -not $observed.Contains($mutant.Expected)) {
                throw "Readback negative control failed: $($mutant.Name): $observed"
            }
            Write-Host "S1-09 NEGATIVE_CONTROL PASS: $($mutant.Name), rejected at $($mutant.Expected)"
        } finally { [IO.File]::WriteAllText($adapter, $adapted, [Text.UTF8Encoding]::new($false)) }
    }
    foreach ($mutant in @(
        @{Name='ignored-apply-failure'; Before='!DisplayHardware.apply(on, reason)'; After='(!DisplayHardware.apply(on, reason) && false)'; Expected='CF-apply-failure status'; Argument='apply'},
        @{Name='missing-retry-admission'; Before='if (key.equals(lastAttemptKey) && now - lastAttemptAt < 1500L) return false;'; After='/* negative control: admission removed */'; Expected='CF-retry suppressed at 1499ms'; Argument='retry'}
    )) {
        if ([regex]::Matches($adapted, [regex]::Escape($mutant.Before)).Count -ne 1) {
            throw 'Failure negative control target changed'
        }
        try {
            [IO.File]::WriteAllText($adapter, $adapted.Replace($mutant.Before, $mutant.After), [Text.UTF8Encoding]::new($false))
            & javac -encoding UTF-8 -source 8 -target 8 -d $classes @readbackSources
            if ($LASTEXITCODE -ne 0) { throw 'Failure negative control must compile' }
            $observed = (& java -cp $classes com.thor.displaypowertest.CoordinatorFailureHostTest $mutant.Argument 2>&1 |
                ForEach-Object { "$_" }) -join "`n"
            if ($LASTEXITCODE -eq 0 -or -not $observed.Contains($mutant.Expected)) {
                throw "Failure negative control failed: $($mutant.Name): $observed"
            }
            Write-Host "S1-10 NEGATIVE_CONTROL PASS: $($mutant.Name), rejected at $($mutant.Expected)"
        } finally { [IO.File]::WriteAllText($adapter, $adapted, [Text.UTF8Encoding]::new($false)) }
    }

    # Compile the actual reflection wrapper, independently of the coordinator's
    # DisplayHardware double. These endpoints cannot reach Android or a vendor.
    $hardwareFixture = Join-Path $repo 'tests/displayhardware'
    $hardwareClasses = Join-Path $out 'hardware-classes'
    $hardwareDir = Join-Path $out 'hardware-source'
    New-Item -ItemType Directory -Path $hardwareClasses, $hardwareDir | Out-Null
    $hardware = Join-Path $hardwareDir 'DisplayHardware.java'
    $hardwareOriginal = [IO.File]::ReadAllText((Join-Path $src 'DisplayHardware.java'))
    [IO.File]::WriteAllText($hardware, $hardwareOriginal, [Text.UTF8Encoding]::new($false))
    $hardwareSources = @($hardware, (Join-Path $src 'ThorHardwareProfile.java'),
        (Join-Path $fixture 'android/util/Log.java')) + @(
        Get-ChildItem -LiteralPath $hardwareFixture -Recurse -Filter '*.java' | ForEach-Object FullName
    )
    & javac -encoding UTF-8 -source 8 -target 8 -d $hardwareClasses @hardwareSources
    if ($LASTEXITCODE -ne 0) { throw 'Real DisplayHardware host compilation failed' }
    & java -cp $hardwareClasses com.thor.displaypowertest.DisplayHardwareFailureHostTest
    if ($LASTEXITCODE -ne 0) { throw 'Real DisplayHardware characterization failed' }
    $rollback = 'setProperty(previousProperty);'
    if ([regex]::Matches($hardwareOriginal, [regex]::Escape($rollback)).Count -ne 1) {
        throw 'Hardware rollback negative control target changed'
    }
    [IO.File]::WriteAllText($hardware, $hardwareOriginal.Replace($rollback, '/* negative control: rollback omitted */'), [Text.UTF8Encoding]::new($false))
    & javac -encoding UTF-8 -source 8 -target 8 -d $hardwareClasses @hardwareSources
    if ($LASTEXITCODE -ne 0) { throw 'Hardware negative control must compile' }
    $observed = (& java -cp $hardwareClasses com.thor.displaypowertest.DisplayHardwareFailureHostTest rollback 2>&1 |
        ForEach-Object { "$_" }) -join "`n"
    if ($LASTEXITCODE -eq 0 -or -not $observed.Contains('HW-rollback writes')) {
        throw "Hardware rollback negative control failed: $observed"
    }
    Write-Host 'S1-10 NEGATIVE_CONTROL PASS: missing rollback, rejected at HW-rollback writes'
    & (Join-Path $PSScriptRoot 'test-watcher-lifecycle-host.ps1')
    Write-Host 'S1 real coordinator host-only characterization PASS'
} finally {
    if (Test-Path -LiteralPath $out) {
        $resolved = (Resolve-Path -LiteralPath $out).Path
        if ([IO.Path]::GetDirectoryName($resolved) -ne $tempParent -or
                (Get-Item -LiteralPath $out).Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw 'Refusing unexpected fixture cleanup target'
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
# All expected mutant failures have been checked; do not leak their exit codes.
$global:LASTEXITCODE = 0
