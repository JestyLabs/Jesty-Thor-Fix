[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$ci = Get-Content -LiteralPath (Join-Path $root '.github/workflows/host-tests.yml') -Raw
$testSigner = Get-Content -LiteralPath (Join-Path $root '.github/workflows/sign-test-candidate.yml') -Raw
$publisher = Get-Content -LiteralPath (Join-Path $root '.github/workflows/release.yml') -Raw

# Normalize only in-memory workflow text for identical contract assertions
# on Windows CRLF and repository LF. No workflow file is rewritten.
$ci = $ci -replace "`r`n", "`n"
$testSigner = $testSigner -replace "`r`n", "`n"
$publisher = $publisher -replace "`r`n", "`n"

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

# Signing is intentionally manual. A push CI run cannot contain a signed asset.
# Keep the publisher aligned with the CI signing gate and the protected operator.
if ($ci -notmatch "github.event_name == 'workflow_dispatch' && inputs.sign_main_candidate == true" -or
    -not $publisher.Contains('$run.event -ne ''workflow_dispatch''') -or
    -not $publisher.Contains('$run.actor.login -cne ''SirJesty''') -or
    -not $publisher.Contains('$run.triggering_actor.login -cne ''SirJesty''')) {
    throw 'Candidate signing/publishing provenance must require protected manual CI.'
}

# Reject the exact 2026-10-17 operational failure: a cancelled manual CI ID
# must not proceed, and an unsigned-only successful CI cannot be published.
# These are workflow contract checks, not a simulation of live GitHub permissions.
if (-not $publisher.Contains("$" + 'run.status -ne ''completed'' -or $run.conclusion -ne ''success''') -or
    -not $publisher.Contains('cancelled/running/failed IDs cannot be published') -or
    -not $publisher.Contains('/artifacts?per_page=100') -or
    -not $publisher.Contains('$_.expired -ne $true') -or
    -not $publisher.Contains('[long]$_.size_in_bytes -gt 0') -or
    -not $publisher.Contains('$signed.Count -ne 1') -or
    -not $publisher.Contains('artifact_name=$($signed[0].name)') -or
    -not $publisher.Contains('name: ${{ steps.source.outputs.artifact_name }}') -or
    $publisher.Contains('pattern: signed-candidate-*') -or
    $publisher.Contains('merge-multiple: true')) {
    throw 'Publisher must reject cancelled/unsigned/expired/ambiguous candidates and download only its verified artifact.'
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
    $sign -notmatch 'actions/download-artifact@v8' -or
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
# Protect the always-created PR emulator check from accidental trigger/path
# filters, protected-key dependencies or disabled installer coverage.
# This cannot change or read live required GitHub branch protection settings.
$emulatorHeader = [regex]::Match($ci, '(?m)^  android_emulator_smoke:[ \t]*$')
if (-not $emulatorHeader.Success) {
    throw 'CI is missing the stable PR Android emulator job.'
}
$emulator = $ci.Substring($emulatorHeader.Index)
$eventHeader = [regex]::Match($ci, '(?ms)^on:[ \t]*\r?\n(?<events>.*?)(?=^permissions:)')
if (-not $eventHeader.Success) {
    throw 'Missing CI workflow event trigger block.'
}
$events = $eventHeader.Groups['events'].Value
$pullRequestEvent = [regex]::Match($events, '(?m)^  pull_request:[ \t]*$')
if (-not $pullRequestEvent.Success) {
    throw 'A stable required emulator check needs a pull_request trigger.'
}
$afterPullRequest = $events.Substring($pullRequestEvent.Index + $pullRequestEvent.Length)
$nextEvent = [regex]::Match($afterPullRequest, '(?m)^  [A-Za-z_][A-Za-z_0-9-]*:')
$pullRequestConfig = if ($nextEvent.Success) {
    $afterPullRequest.Substring(0, $nextEvent.Index)
} else {
    $afterPullRequest
}
# A push-to-main branches filter is fine; a PR filter can make the check
# absent for an unrelated pull request and block its merge indefinitely.
if ($pullRequestConfig -match '(?m)^    (?:paths|paths-ignore|branches|branches-ignore):') {
    throw 'PR required-check workflow must not have path/branch filters.'
}
if ($emulator -notmatch "(?m)^    if: github\.event_name == 'pull_request' \|\| github\.event_name == 'workflow_dispatch'[ \t]*\r?$" -or
    $emulator -notmatch '(?m)^    needs: build[ \t]*$' -or
    $emulator -notmatch '(?m)^    name: Android emulator lifecycle and installer sessions \(no vendor bridge\)[ \t]*$') {
    throw 'The expected Android emulator check must be created for every PR after the build.'
}
if ($emulator -match '(?m)^    environment:' -or
    $emulator -match 'secrets\.' -or
    $emulator -match 'THOR_KEYSTORE_B64|THOR_KEYSTORE_PASSWORD|thor-signing|thor-publication') {
    throw 'PR emulator must not access protected signing/publication credentials.'
}
if ($emulator -notmatch 'Build isolated installer harness with disposable test key' -or
    $emulator -notmatch 'test-android-installer-session\.sh' -or
    $emulator -notmatch 'test-android-emulator-smoke\.sh') {
    throw 'PR emulator must keep its disposable-key installer and lifecycle checks.'
}
# The focused updater-gate workflow is path-filtered and must NOT become a
# globally required GitHub status check; use the stable emulator job instead.

# PowerShell child scripts throw on failure; LASTEXITCODE is left over from
# the prior native command and MUST NOT be reused as this script's exit status.
& (Join-Path $PSScriptRoot 'test-current-release-contract.ps1')
& (Join-Path $PSScriptRoot 'test-release-contract-candidates.ps1')

Write-Host 'Release workflow and PR emulator CI contracts passed (live required GitHub settings still need admin verification).'
