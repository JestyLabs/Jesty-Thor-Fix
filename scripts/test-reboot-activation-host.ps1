[CmdletBinding()]
param([switch]$RunNegativeControls)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$parent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
$work = Join-Path $parent ('jesty-reboot-activation-' + [guid]::NewGuid().ToString('N'))
if ([IO.Path]::GetDirectoryName($work) -ne $parent) { throw 'Unexpected fixture path' }

function Replace-MethodBody([string]$source, [string]$signature, [string]$replacement) {
    $start = $source.IndexOf($signature)
    if ($start -lt 0) { throw 'Fixture method not found' }
    $open = $source.IndexOf('{', $start)
    $depth = 1; $end = $open + 1
    while ($depth -gt 0) {
        if ($source[$end] -eq '{') { $depth++ }
        if ($source[$end] -eq '}') { $depth-- }
        $end++
    }
    return $source.Substring(0, $open) + '{' + $replacement + '}' + $source.Substring($end)
}

try {
    $src = Join-Path $work 'src'
    $classes = Join-Path $work 'classes'
    New-Item -ItemType Directory -Path $src, $classes | Out-Null
    $fixture = Join-Path $root 'tests/integration/reboot-activation'
    Copy-Item -Path (Join-Path $fixture 'fixtures/*') -Destination $src -Recurse
    $package = Join-Path $src 'com/thor/displaypowertest'
    foreach ($name in @('AutoService','PServer','RebootActivation','RebootActivationPolicy',
            'PreviousSecureDaemonIdentity','DaemonLaunchModel','DashboardRenderer',
            'DashboardStateModel','CpuWarningModel','TelemetryValues')) {
        $source = [IO.File]::ReadAllText((Join-Path $root "src/com/thor/displaypowertest/$name.java"))
        if ($name -eq 'AutoService') { $source = $source.Replace('Thread.sleep(200L);', 'Thread.sleep(0L);') }
        if ($name -eq 'PServer') {
            $source = Replace-MethodBody $source 'private static boolean legacyProtocolSeen()' ' return false; '
            $source = Replace-MethodBody $source 'public static boolean legacyTcpOpen()' ' return FixtureState.legacyPresent; '
        }
        [IO.File]::WriteAllText((Join-Path $package "$name.java"), $source, [Text.UTF8Encoding]::new($false))
    }
    $service = [IO.File]::ReadAllText((Join-Path $package 'AutoService.java'))
    if ($service -match 'stopPreviousSecureDaemonIfSafe|stopLegacyDaemon') {
        throw 'Reboot-required activation must not stop an earlier/legacy daemon.'
    }
    Get-ChildItem -LiteralPath $fixture -Filter '*.java' -File | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $src }
    Copy-Item -LiteralPath (Join-Path $root 'tests/RebootActivationPolicyTest.java') -Destination $src
    $sources = @(Get-ChildItem -LiteralPath $src -Recurse -File -Filter '*.java' | ForEach-Object FullName)
    & javac -encoding UTF-8 -source 8 -target 8 -d $classes @sources
    if ($LASTEXITCODE -ne 0) { throw 'Reboot activation fixture compilation failed' }
    & java -cp $classes com.thor.displaypowertest.RebootActivationHostTest
    if ($LASTEXITCODE -ne 0) { throw 'Reboot activation fixture failed' }
    & java -cp $classes com.thor.displaypowertest.RebootDashboardHostTest
    if ($LASTEXITCODE -ne 0) { throw 'Reboot dashboard fixture failed' }
    & java -cp $classes RebootActivationPolicyTest
    if ($LASTEXITCODE -ne 0) { throw 'Reboot policy fixture failed' }

    if ($RunNegativeControls) {
        # Mutants exist only in the temporary fixture copy, never the working tree.
        $PSNativeCommandUseErrorActionPreference = $false
        $mutants = @(
            @{Name='same-kernel-authorized'; File='RebootActivationPolicy.java';
                Before='!boot.equalsIgnoreCase(savedBoot)'; After='true';
                Test='RebootActivationPolicyTest'; Expected='same kernel cannot authorize'},
            @{Name='service-gate-bypassed'; File='RebootActivation.java';
                Before='return ticket(context, preferences).isAuthorized();'; After='return true;';
                Test='com.thor.displaypowertest.RebootActivationHostTest'; Expected='first-open-prepares-only: launches'}
        )
        foreach ($mutant in $mutants) {
            $path = Join-Path $package $mutant.File
            $original = [IO.File]::ReadAllText($path)
            if (-not $original.Contains($mutant.Before)) { throw 'Negative control target missing' }
            try {
                [IO.File]::WriteAllText($path, $original.Replace($mutant.Before, $mutant.After), [Text.UTF8Encoding]::new($false))
                & javac -encoding UTF-8 -source 8 -target 8 -d $classes @sources
                if ($LASTEXITCODE -ne 0) { throw 'Negative control must compile before rejection counts' }
                $observed = (& java -cp $classes $mutant.Test 2>&1 | ForEach-Object { "$_" }) -join "`n"
                if ($LASTEXITCODE -eq 0 -or -not $observed.Contains($mutant.Expected)) {
                    throw "Negative control not rejected as expected: $($mutant.Name): $observed"
                }
                Write-Output "NEGATIVE_CONTROL PASS: $($mutant.Name) rejected by $($mutant.Expected)"
            } finally { [IO.File]::WriteAllText($path, $original, [Text.UTF8Encoding]::new($false)) }
        }
    }
} finally {
    if (Test-Path -LiteralPath $work) {
        $resolved = (Resolve-Path -LiteralPath $work).Path
        if ([IO.Path]::GetDirectoryName($resolved) -ne $parent -or
                (Get-Item -LiteralPath $work).Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw 'Refusing unexpected fixture cleanup target'
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
