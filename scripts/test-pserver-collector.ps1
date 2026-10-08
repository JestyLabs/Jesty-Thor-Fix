[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$collector = Join-Path $PSScriptRoot 'collect-thor-pserverbinder-readonly.ps1'
$root = Join-Path ([IO.Path]::GetTempPath()) ('thor-collector-fixture-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
$global:JestyCollectorFixtureCalls = [Collections.Generic.List[object]]::new()
$global:JestyCollectorFixtureModel = 'Thor'
$global:JestyCollectorFixtureState = 'device'
function Fixture-Adb {
    $global:JestyCollectorFixtureCalls.Add(@($args))
    $global:LASTEXITCODE = 0
    if (($args -join ' ') -match 'get-state$') { return $global:JestyCollectorFixtureState }
    if (($args -join ' ') -match 'getprop ro.product.model$') { return $global:JestyCollectorFixtureModel }
    if (($args -join ' ') -match 'service check PServerBinder$') {
        $global:LASTEXITCODE = 1
        return 'probe unavailable (fixture)'
    }
    return 'fixture read'
}
function Expect-Rejected([scriptblock]$Action) {
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw 'Unsafe collector input was accepted.' }
}
Expect-Rejected { & $collector -Serial '' -OutputDir (Join-Path $root 'empty-serial') -AdbPath Fixture-Adb }
Expect-Rejected { & $collector -Serial fixture-thor -OutputDir ' ' -AdbPath Fixture-Adb }
if ($global:JestyCollectorFixtureCalls.Count) { throw 'Invalid input contacted ADB.' }
$checkout = Join-Path $root 'checkout'
New-Item -ItemType Directory -Path $checkout | Out-Null
# A worktree has a .git file; a normal checkout has a .git directory.
Set-Content -LiteralPath (Join-Path $checkout '.git') -Value 'gitdir: fixture'
Expect-Rejected { & $collector -Serial fixture-thor -OutputDir (Join-Path $checkout 'capture') -AdbPath Fixture-Adb }
Remove-Item -LiteralPath (Join-Path $checkout '.git')
New-Item -ItemType Directory -Path (Join-Path $checkout '.git') | Out-Null
Expect-Rejected { & $collector -Serial fixture-thor -OutputDir (Join-Path $checkout 'nested/capture') -AdbPath Fixture-Adb }
if ($global:JestyCollectorFixtureCalls.Count) { throw 'Git output contacted ADB.' }
$global:JestyCollectorFixtureModel = 'Other handheld'
Expect-Rejected { & $collector -Serial fixture-thor -OutputDir (Join-Path $root 'wrong-device') -AdbPath Fixture-Adb }
if ($global:JestyCollectorFixtureCalls.Count -ne 2) { throw 'Wrong-model device received lifecycle probes.' }
$global:JestyCollectorFixtureModel = 'Thor'
$global:JestyCollectorFixtureState = 'offline'
$global:JestyCollectorFixtureCalls.Clear()
Expect-Rejected { & $collector -Serial fixture-thor -OutputDir (Join-Path $root 'offline') -AdbPath Fixture-Adb }
if ($global:JestyCollectorFixtureCalls.Count -ne 1) { throw 'Offline device received further probes.' }
$global:JestyCollectorFixtureState = 'device'
$global:JestyCollectorFixtureCalls.Clear()
$output = Join-Path $root 'valid'
& $collector -Serial fixture-thor -OutputDir $output -AdbPath Fixture-Adb | Out-Null
foreach ($call in $global:JestyCollectorFixtureCalls) {
    if ($call[0] -ne '-s' -or $call[1] -ne 'fixture-thor') { throw 'Unpinned ADB invocation.' }
}
$status = @(Import-Csv -LiteralPath (Join-Path $output '00-capture-status.tsv') -Delimiter ([char]9))
if (-not ($status | Where-Object { $_.name -like '*04-pserver-check.txt' -and $_.exit_code -eq '1' })) {
    throw 'Failed probe was not preserved.'
}
if ((Get-Content -LiteralPath (Join-Path $output '00a-local-pservice-hash.txt') -Raw) -notmatch 'matches_expected=UNKNOWN') {
    throw 'Absent local comparison must remain UNKNOWN.'
}
$count = $global:JestyCollectorFixtureCalls.Count
Expect-Rejected { & $collector -Serial fixture-thor -OutputDir $output -AdbPath Fixture-Adb }
if ($global:JestyCollectorFixtureCalls.Count -ne $count) { throw 'Overwrite rejection contacted ADB.' }
Write-Host 'PServer collector fixtures passed: required inputs, Git guards, device pin/model, offline stop, failed probes, UNKNOWN hash and no overwrite.'

