
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
'@ | Set-Content -LiteralPath (Join-Path $root 'lower-panel.txt') -Encoding utf8

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

    Write-Host 'Refresh capture analyzer tests passed.'
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
