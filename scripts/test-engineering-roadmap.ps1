[CmdletBinding()]
param()

# Structural fail-fast check: prevent accidental removal/uncoupling of the
# quality programme's contributor entry points and safety-gate documents.
# It cannot inspect contributor intent, enforce branch rules, or prove hardware behavior.
$ErrorActionPreference = 'Stop'
$checks = [ordered]@{
    'AGENTS.md' = @('JTF-QUALITY-2026-10-17', 'ENGINEERING-QUALITY-EXECUTION-CONTRACT.md', 'No feature drift', 'No speculative display patches', 'PARALLEL-WORK-INTEGRATION-2026-10-17.md')
    'docs/ENGINEERING-QUALITY-ROADMAP-2026-10-17.md' = @('JTF-QUALITY-2026-10-17', 'S0 —', 'S1 —', 'S2 —', 'S3 —', 'S4 —', 'S5 —', 'S6 —', 'No target date', 'v1.7.3/code83', 'issues/95', 'pull/94', 'pull/96', 'pull/98')
    'docs/ENGINEERING-QUALITY-EXECUTION-CONTRACT.md' = @('JTF-QUALITY-2026-10-17', 'Stop', 'HOST', 'EMULATOR', 'PHYSICAL', 'Enforcement limitations', 'issues/95')
    'docs/quality/S1-SLEEP-WAKE-OFFLINE-MATRIX.md' = @('S1-01', 'S1-16', 'NOT EXECUTED', 'No runtime change', 'pull/98')
    'docs/quality/PARALLEL-WORK-INTEGRATION-2026-10-17.md' = @('JTF-QUALITY-2026-10-17', 'pull/94', 'pull/96', 'pull/97', 'pull/98', 'issues/95', '403')
    'docs/ROADMAP.md' = @('ENGINEERING-QUALITY-ROADMAP-2026-10-17.md')
    'CONTRIBUTING.md' = @('AGENTS.md', 'ENGINEERING-QUALITY-EXECUTION-CONTRACT.md')
    '.github/PULL_REQUEST_TEMPLATE.md' = @('Roadmap execution', 'JTF-QUALITY-2026-10-17', 'NOT RUN')
    '.github/CODEOWNERS' = @('/AGENTS.md', '/docs/ENGINEERING-QUALITY-ROADMAP-2026-10-17.md')
    '.github/workflows/host-tests.yml' = @('test-engineering-roadmap.ps1')
}
foreach ($entry in $checks.GetEnumerator()) {
    if (-not (Test-Path -LiteralPath $entry.Key -PathType Leaf)) {
        throw "Engineering programme contract missing: $($entry.Key)"
    }
    $body = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $entry.Key).Path)
    foreach ($marker in $entry.Value) {
        if (-not $body.Contains($marker)) {
            throw "Engineering programme contract drift at $($entry.Key): '$marker' missing"
        }
    }
}
Write-Host 'Engineering programme structural contract passed (not a semantic/hardware compliance proof).'
