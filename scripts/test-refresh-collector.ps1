$ErrorActionPreference = 'Stop'
$collector = Join-Path $PSScriptRoot 'inspect-thor-refresh.ps1'
$root = Join-Path ([IO.Path]::GetTempPath()) ('thor-refresh-collector-test-' + [Guid]::NewGuid().ToString('N'))
$global:thorRefreshTestCalls = [Collections.Generic.List[string]]::new()
$global:thorRefreshTestState = 'offline'
function adb {
    $global:thorRefreshTestCalls.Add(($args -join ' '))
    $global:LASTEXITCODE = 0
    if ($args[-1] -eq 'get-state') { return $global:thorRefreshTestState }
    return '<fixture: no hardware accessed>'
}

try {
    $parameter = (Get-Command $collector).Parameters['Serial']
    if (-not @($parameter.Attributes | Where-Object { $_ -is [Management.Automation.ParameterAttribute] -and $_.Mandatory }).Count) {
        throw 'Collector must require an explicit serial.'
    }
    $rejected = $false
    try { & $collector -Serial 'fixture' -OutputDir $root } catch {
        if ($_.Exception.Message -notmatch 'not connected and authorized') { throw }
        $rejected = $true
    }
    if (-not $rejected -or (Test-Path -LiteralPath $root)) {
        throw 'Offline selection must fail before creating output.'
    }
    $global:thorRefreshTestCalls.Clear()
    $rejected = $false
    try { & $collector -Serial 'fixture' -OutputDir (Join-Path $PSScriptRoot '..\raw-evidence') } catch {
        if ($_.Exception.Message -notmatch 'outside the repository') { throw }
        $rejected = $true
    }
    if (-not $rejected -or $global:thorRefreshTestCalls.Count -ne 0) {
        throw 'Repository output must be rejected before any device access.'
    }
    $global:thorRefreshTestState = 'device'
    & $collector -Serial 'fixture' -OutputDir $root
    $summary = Get-Content (Join-Path $root '00-capture-summary.json') -Raw | ConvertFrom-Json
    if (-not $summary.captureComplete -or $global:thorRefreshTestCalls[0] -ne '-s fixture get-state') {
        throw 'Authorized selection must preflight and produce a complete mock capture.'
    }
    foreach ($call in $global:thorRefreshTestCalls) {
        if (-not $call.StartsWith('-s fixture ')) { throw 'Every ADB call must pin the selected serial.' }
    }
    Write-Host 'Refresh collector selection and output tests passed (mock ADB only).'
} finally {
    Remove-Variable -Name thorRefreshTestCalls,thorRefreshTestState -Scope Global -ErrorAction SilentlyContinue
    $cleanup = [IO.Path]::GetFullPath($root)
    if (-not $cleanup.StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath()), [StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path -Leaf $cleanup) -notlike 'thor-refresh-collector-test-*') { throw 'Unsafe collector-test cleanup path.' }
    if (Test-Path -LiteralPath $cleanup) { Remove-Item -LiteralPath $cleanup -Recurse -Force }
}
