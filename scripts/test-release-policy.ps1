[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$ci = Get-Content -LiteralPath (Join-Path $root '.github/workflows/host-tests.yml') -Raw
$testSigner = Get-Content -LiteralPath (Join-Path $root '.github/workflows/sign-test-candidate.yml') -Raw
$publisher = Get-Content -LiteralPath (Join-Path $root '.github/workflows/release.yml') -Raw

foreach ($workflow in @($ci, $testSigner, $publisher)) {
    if ($workflow -notmatch "(?m)^    if: .*github\.actor == 'SirJesty'.*github\.triggering_actor == 'SirJesty'" -or
        $workflow -notmatch 'ORIGINAL_ACTOR: \$\{\{ github.actor \}\}' -or
        $workflow -notmatch 'TRIGGERING_ACTOR: \$\{\{ github.triggering_actor \}\}') {
        throw 'Privileged jobs must reject unauthorized original/rerun actors before environment approval and recheck at runtime.'
    }
}
if ($ci -match '(?m)^\s+contents: write\s*$' -or $testSigner -match '(?m)^\s+contents: write\s*$' -or
    $publisher -notmatch '(?ms)^permissions:\s*\r?\n  actions: read\s*\r?\n  contents: read\s*\r?\n' -or
    $publisher -notmatch '(?ms)^    permissions:\s*\r?\n      actions: read\s*\r?\n      contents: write\s*\r?\n') {
    throw 'Only the environment-protected publication job may request contents: write.'
}

# Regression-only structural checks. GitHub Environment access rules and secrets
# MUST still be verified in Settings; this script cannot enforce administration.
$sections = [regex]::Match($ci, '(?ms)^  build:\s*\r?\n(?<build>.*?)(?=^  sign_main_candidate:)')
if (-not $sections.Success) {
    throw 'CI must keep a separate required build job before signing.'
}
$build = $sections.Groups['build'].Value
$sign = $ci.Substring($sections.Index + $sections.Length)
if ($build -match 'THOR_KEYSTORE_B64|THOR_KEYSTORE_PASSWORD|secrets\.|environment:\s*thor-signing|apksigner sign') {
    throw 'Unsigned build job must not use signing secrets or environment.'
}
if ($sign -notmatch 'needs: build' -or
    $sign -notmatch 'environment: thor-signing' -or
    $sign -notmatch 'THOR_SIGNING_ENV_READY' -or
    $sign -notmatch 'actions/download-artifact@v4' -or
    $sign -notmatch 'signed-candidate-v') {
    throw 'Main candidate signing must depend on build and the protected signing environment.'
}
if ($testSigner -notmatch 'environment: thor-signing' -or
    $testSigner -notmatch 'THOR_SIGNING_ENV_READY' -or
    $testSigner -notmatch "github.ref == 'refs/heads/main'" -or
    $testSigner -notmatch '\$run\.event -ne ''pull_request''' -or
    $testSigner -notmatch '\$run\.path -ne ''.github/workflows/host-tests.yml''') {
    throw 'Test candidate signing must validate source and require protected signing environment.'
}
if ($publisher -notmatch 'environment: thor-publication' -or
    $publisher -notmatch 'THOR_PUBLICATION_ENV_READY' -or
    $publisher -notmatch 'contents: write' -or
    $publisher -notmatch "github.ref == 'refs/heads/main'") {
    throw 'Publication must use the protected main-only publication environment.'
}
Write-Host 'Release workflow structural checks passed (GitHub settings still need independent audit).'
