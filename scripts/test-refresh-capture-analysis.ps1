
$ErrorActionPreference = 'Stop'

$scriptPath = Join-Path $PSScriptRoot 'analyze-thor-refresh-capture.ps1'
if (-not (Test-Path -LiteralPath $scriptPath)) {
    throw 'Capture analyzer is missing.'
}

$root = Join-Path ([IO.Path]::GetTempPath()) ("thor-refresh-capture-test-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null

try {
    @'
system/min_refresh_rate=120.0
system/peak_refresh_rate=120.0
DesiredDisplayModeSpecs primaryRefreshRateRange=[120.0 120.0]
'@ | Set-Content -LiteralPath (Join-Path $root '04-dumpsys-display.txt') -Encoding utf8

    @'
Setting desired display mode specs: defaultMode=2 primaryRange=[120,120]
Display 4630946482288158084 (inactive) HWC layers:
'@ | Set-Content -LiteralPath (Join-Path $root '06-surfaceflinger.txt') -Encoding utf8

    @'
crtc=181 mode=1080x1920x60cmd
crtc=243 mode=1080x1240x60vid
'@ | Set-Content -LiteralPath (Join-Path $root '11-drm-state.txt') -Encoding utf8

    @'
display0 cur:60 vsync_period=16666666
display1 cur:60 vsync_period=16666666
'@ | Set-Content -LiteralPath (Join-Path $root 'vendor.log') -Encoding utf8

    @'
bypass_ram=0
panel=ch13726a
'@ | Set-Content -LiteralPath (Join-Path $root '03a-lower-panel.txt') -Encoding utf8

    @'
10-07 12:28:38.900  100  200 I SurfaceFlinger: Display PhysicalDisplayId{1} policy changed
10-07 12:28:38.900  100  200 I SurfaceFlinger: Previous: {defaultModeId=1, primaryRange=[60,60]}
10-07 12:28:38.900  100  200 I SurfaceFlinger: Current: {defaultModeId=2, primaryRange=[60,120]}
10-07 12:28:38.900  100  200 I SurfaceFlinger: 0 mode changes were performed under the previous policy
10-07 12:28:39.059  100  200 I SurfaceFlinger: Display PhysicalDisplayId{1} policy changed
10-07 12:28:39.059  100  200 I SurfaceFlinger: Previous: {defaultModeId=2, primaryRange=[60,120]}
10-07 12:28:39.059  100  200 I SurfaceFlinger: Current: {defaultModeId=2, primaryRange=[120,120]}
10-07 12:28:39.059  100  200 I SurfaceFlinger: 1 mode changes were performed under the previous policy
'@ | Set-Content -LiteralPath (Join-Path $root 'surfaceflinger-policy.log') -Encoding utf8

    & $scriptPath -CaptureDir $root
    if (-not $?) { throw 'Capture analyzer failed.' }

    $summary = Get-Content -LiteralPath (Join-Path $root 'analysis/refresh-capture-summary.json') -Raw | ConvertFrom-Json

    if ($summary.schema -ne 'THOR_REFRESH_CAPTURE_ANALYSIS_V2') { throw 'Unexpected capture-analysis schema.' }
    if ($summary.causalConclusion -ne $false) { throw 'Bundle-wide text analysis must never claim causality.' }
    if (-not $summary.flags.has120PolicyMention) { throw 'Synthetic 120 policy mention was not detected.' }
    if (-not $summary.flags.inactiveDisplayEvidence) { throw 'Inactive-display evidence was not detected.' }
    if (-not $summary.flags.drm60Evidence) { throw '60 Hz DRM evidence was not detected.' }
    if (-not $summary.flags.aynLowerPathEvidence) { throw 'AYN lower-panel path evidence was not detected.' }
    if ($summary.flags.vendorConfigFailure) { throw 'Synthetic fixture must not invent a vendor failure.' }
    if ($summary.stage -ne 'POLICY_120_MENTION_WITHOUT_DESIRED_MODE_EVIDENCE') {
        throw "Unexpected synthetic evidence stage: $($summary.stage)"
    }
    if ($summary.reason -match '(?i)proves|therefore.*before|caused') {
        throw 'Analyzer reason must not turn uncorrelated text matches into a causal conclusion.'
    }

    if (@($summary.policyTransitions).Count -ne 2) {
        throw "Expected two ordered policy transitions, got $(@($summary.policyTransitions).Count)."
    }
    $final120 = @($summary.policyTransitions)[1]
    if ($final120.timestamp -ne '10-07 12:28:39.059') {
        throw "Unexpected final-policy timestamp: $($final120.timestamp)"
    }
    if ($final120.previous -notmatch 'primaryRange=\[60,120\]' -or
        $final120.current -notmatch 'primaryRange=\[120,120\]') {
        throw 'Policy transition parser did not preserve the 60-120 -> 120-120 boundary.'
    }
    if ($final120.previousPolicyModeChanges -ne 1) {
        throw 'Policy transition parser did not preserve the previous-policy mode-change count.'
    }

    # A policy range that merely contains 120 must not be described as a proven
    # fixed-120 request. It is still useful evidence, but only as a 120 mention.
    @'
system/min_refresh_rate=60.0
system/peak_refresh_rate=120.0
DesiredDisplayModeSpecs primaryRefreshRateRange=[60.0 120.0]
'@ | Set-Content -LiteralPath (Join-Path $root '04-dumpsys-display.txt') -Encoding utf8

    @'
Setting desired display mode specs: defaultMode=1 primaryRange=[60,120]
Display 4630946482288158084 (inactive) HWC layers:
'@ | Set-Content -LiteralPath (Join-Path $root '06-surfaceflinger.txt') -Encoding utf8

    & $scriptPath -CaptureDir $root
    if (-not $?) { throw 'Range-only capture analyzer run failed.' }

    $summary = Get-Content -LiteralPath (Join-Path $root 'analysis/refresh-capture-summary.json') -Raw | ConvertFrom-Json
    if ($summary.stage -ne 'POLICY_120_MENTION_WITHOUT_DESIRED_MODE_EVIDENCE') {
        throw "Unexpected range-only evidence stage: $($summary.stage)"
    }
    if ($summary.reason -notmatch 'range containing 120') {
        throw 'Range-only policy evidence must stay explicitly ambiguous.'
    }

    # A vendor-success marker and a DRM-60 snapshot in different files must stay
    # explicitly uncorrelated. The analyzer may surface the pair, but not claim a
    # post-HWC physical mismatch.
    @'
Active configuration changed to: 7
'@ | Set-Content -LiteralPath (Join-Path $root 'vendor-success.log') -Encoding utf8

    & $scriptPath -CaptureDir $root
    if (-not $?) { throw 'Second capture analyzer run failed.' }

    $summary = Get-Content -LiteralPath (Join-Path $root 'analysis/refresh-capture-summary.json') -Raw | ConvertFrom-Json
    if ($summary.stage -ne 'VENDOR_SUCCESS_AND_DRM60_EVIDENCE') {
        throw "Unexpected uncorrelated vendor/DRM evidence stage: $($summary.stage)"
    }
    if ($summary.causalConclusion -ne $false) {
        throw 'Vendor/DRM evidence from heterogeneous files must remain non-causal.'
    }


    # Synthetic logcat: two invalid SF attempts plus a *separate* successful
    # vendor marker. Neither the SF mode IDs nor the unrelated HWC success
    # may be collapsed into one rejected or successful physical transition.
    @'
10-07 23:42:17.252  2319  2355 E SurfaceFlinger: Trying to initiate a mode change to invalid mode 1 on display PhysicalDisplayId{4630946482288158084}
10-07 23:42:22.660  2319  2355 E SurfaceFlinger: Trying to initiate a mode change to invalid mode 0 on display PhysicalDisplayId{4630946482288158084}
10-07 23:42:22.665  2319  2355 E SurfaceFlinger: Trying to initiate a mode change to invalid mode null on display PhysicalDisplayId{4630946482288158084}
'@ | Set-Content -LiteralPath (Join-Path $root 'sf-invalid.log') -Encoding utf8

    & $scriptPath -CaptureDir $root
    if (-not $?) { throw 'Invalid-mode fixture analyzer run failed.' }

    $summary = Get-Content -LiteralPath (Join-Path $root 'analysis/refresh-capture-summary.json') -Raw | ConvertFrom-Json
    if ($summary.stage -ne 'SURFACEFLINGER_INVALID_MODE_EVIDENCE') {
        throw "Invalid SF mode should be classified separately from HWC: $($summary.stage)"
    }
    if (-not $summary.flags.sfInvalidModeEvidence) { throw 'SF invalid-mode evidence flag missing.' }
    if ($summary.flags.vendorConfigFailure) { throw 'SF invalid mode must not be counted as vendor rejection.' }
    if (-not $summary.flags.vendorConfigSuccess) { throw 'Unrelated vendor-success marker should remain a separate finding.' }
    if ($summary.causalConclusion -ne $false) { throw 'Invalid-mode fixture must not assert a causal conclusion.' }

    $events = @($summary.sfInvalidModeEvents)
    if ($events.Count -ne 3) { throw "Expected 3 individual SF errors, got $($events.Count)." }
    if ($events[0].reportedModeId -ne 1 -or $events[1].reportedModeId -ne 0) {
        throw 'SF mode IDs must be preserved as logged, not mapped to global FPS.'
    }
    if ($events[2].reportedModeToken -ne 'null' -or $null -ne $events[2].reportedModeId) {
        throw 'Null SF mode must remain distinct from numeric mode ID zero.'
    }
    if ($events[0].timestamp -ne '10-07 23:42:17.252' -or
        $events[1].timestamp -ne '10-07 23:42:22.660') {
        throw 'Source wall-clock event times were not retained.'
    }
    if ($events[0].reportedDisplay -ne 'PhysicalDisplayId{4630946482288158084}') {
        throw 'Source physical-display label must remain attached to the invalid event.'
    }
    if (@($events | Where-Object { $_.sameInvocationAsHwcRequestProven }).Count -ne 0) {
        throw 'Never automatically attribute an invalid SF event to an HWC request.'
    }

    if (@($events | Where-Object { $_.rejectionProven }).Count -ne 0) {
        throw 'The exact vendor mismatch log is not proof of rejection.'
    }

    # Old reports, documentation and string inventories must not amplify evidence
    # on repeat runs, even when they include exact-looking logcat examples.
    $docsOnly = Join-Path $root 'documentation-only'
    New-Item -ItemType Directory -Path $docsOnly -Force | Out-Null
    $fake = @'
10-07 23:42:17.252 100 100 E DisplayDevice: Trying to initiate a mode change to invalid mode 1 on display 20
Active configuration changed to: 7
1080x1240x120vid
'@
    foreach ($name in @('README.md', 'README.txt', 'refresh-capture-report.txt', 'summary.json', 'notes.txt')) {
        $fake | Set-Content -LiteralPath (Join-Path $docsOnly $name) -Encoding utf8
    }
    & $scriptPath -CaptureDir $docsOnly
    $clean = Get-Content -LiteralPath (Join-Path $docsOnly 'analysis/refresh-capture-summary.json') -Raw | ConvertFrom-Json
    if ($clean.analyzedFiles -ne 0 -or $clean.flags.sfInvalidModeEvidence -or
        $clean.flags.vendorConfigSuccess -or $clean.flags.drm120Mentioned) {
        throw 'Documentation-only bundle generated false runtime evidence.'
    }

    'Example: Trying to initiate a mode change to invalid mode 1 on display 20' |
        Set-Content -LiteralPath (Join-Path $docsOnly 'untimed.log') -Encoding utf8
    & $scriptPath -CaptureDir $docsOnly
    $untimed = Get-Content -LiteralPath (Join-Path $docsOnly 'analysis/refresh-capture-summary.json') -Raw | ConvertFrom-Json
    if ($untimed.flags.sfInvalidModeEvidence -or @($untimed.sfInvalidModeEvents).Count -ne 0 -or
        $untimed.counts.sfInvalidMode -ne 1) {
        throw 'Untimed text matches must remain hints, not SF runtime events.'
    }

    Write-Host 'Refresh capture analyzer tests passed.'
} finally {
    $cleanupPath = [IO.Path]::GetFullPath($root)
    $tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if (-not $cleanupPath.StartsWith($tempParent, [StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path -Leaf $cleanupPath) -notlike 'thor-refresh-capture-test-*') { throw 'Unsafe test cleanup path.' }
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
