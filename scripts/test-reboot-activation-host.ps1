[CmdletBinding()]
param([switch]$RunNegativeControls)
$ErrorActionPreference = 'Stop'

# One portable runner owns the source allowlist and compiled negative controls,
# preventing PowerShell/Python fixture drift. It uses existing Python + JDK17
# only and keeps disposable compilation files under work/, never system temp.
$python = $null
foreach ($name in @('python', 'python3')) {
    foreach ($candidate in @(Get-Command $name -CommandType Application -All -ErrorAction SilentlyContinue)) {
        try {
            $version = & $candidate.Source --version 2>&1
            if ($LASTEXITCODE -eq 0 -and "$version" -match '^Python 3\.') {
                $python = $candidate
                break
            }
        } catch { continue }
    }
    if ($python) { break }
}
if (-not $python) {
    Write-Output 'NOT RUN: working existing Python 3 unavailable; no installation attempted'
    exit 2
}
$runner = Join-Path $PSScriptRoot 'test-retirement-activation-host.py'
$arguments = @($runner)
if ($RunNegativeControls) { $arguments += '--run-negative-controls' }
& $python.Source @arguments
if ($LASTEXITCODE -eq 2) { exit 2 }
if ($LASTEXITCODE -ne 0) { throw 'Activation/retirement HOST fixture failed or unavailable' }
$global:LASTEXITCODE = 0
