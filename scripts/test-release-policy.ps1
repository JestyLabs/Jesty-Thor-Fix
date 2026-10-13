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
# A branch-protection setting can only require a check that is created on
# EVERY ordinary PR. Protect the future admin-required emulator job from
# being accidentally path-filtered, dependent on signing secrets, or skipped.
#
# This is a static CI contract, not proof that branch protection currently
# requires the job or that its emulator tests passed on physical hardware.
$emulatorHeader = [regex]::Match($ci, '(?m)^  android_emulator_smoke:\s*
)
if (-not $emulatorHeader.Success) {
    throw 'CI is missing the stable PR Android emulator job.'
}
$emulator = $ci.Substring($emulatorHeader.Index)
$prTrigger = [regex]::Match($ci, '(?m)^  pull_request:\s*
)
if (-not $prTrigger.Success -or $prTrigger.Index -gt $emulatorHeader.Index) {
    throw 'The PR trigger must exist without a path-specific filter.'
}
$prTriggersBlock = $ci.Substring($prTrigger.Index, $emulatorHeader.Index - $prTrigger.Index)
# Avoid accidental false positives from YAML-level PR filters in the global
# event trigger, which could leave a required GitHub check pending forever.
$prOnly = [regex]::Match($ci, '(?ms)^on:\s*\r?\n(?<events>.*?)(?=^permissions:)', 'IgnoreCase')
if (-not $prOnly.Success -or
    $prOnly.Groups['events'].Value -notmatch '(?m)^  pull_request:\s*
 -or
    $prOnly.Groups['events'].Value -match '(?m)^\s{4}(?:paths|paths-ignore|branches|branches-ignore):') {
    throw 'PR required-check workflow must not have path/branch filters.'
}
if ($emulator -notmatch "if: github\.event_name == 'pull_request' \|\| github\.event_name == 'workflow_dispatch'" -or
    $emulator -notmatch '(?m)^    needs: build\s*
 -or
    $emulator -notmatch '(?m)^    name: Android emulator lifecycle and installer sessions \(no vendor bridge\)\s*
) {
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
# The supplemental updater-specific emulator uses paths: and is not an
# acceptable globally required context. An admin should require the stable
# android_emulator_smoke job only after checking live settings and flakiness.

Write-Host 'Release workflow workflow and PR emulator CI contracts passed (live required GitHub settings still need admin verification).'
