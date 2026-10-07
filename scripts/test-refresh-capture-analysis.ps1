
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
Inactive display
'@ | Set-Content -LiteralPath (Join-Path $root '06-surfaceflinger.txt') -Encoding utf8

    @'
crtc=181 mode=1080x1920x60cmd
crtc=243 mode=1080x1240x60vid
'@ | Set-Content -LiteralPath (Join-Path $root '11-drm-state.txt') -Encoding utf8

    @'
display0 cur:60 vsync_period=16666666
display1 cur:60 vsync_period=16666666
'@ | Set-Content -LiteralPath (Join-Path $root 'vendor.log') -Encoding utf8

    & $scriptPath -CaptureDir $root
    if (-not $?) { throw 'Capture analyzer failed.' }

    $summary = Get-Content -LiteralPath (Join-Path $root 'analysis/refresh-capture-summary.json') -Raw | ConvertFrom-Json

    if ($summary.schema -ne 'THOR_REFRESH_CAPTURE_ANALYSIS_V1') { throw 'Unexpected capture-analysis schema.' }
    if (-not $summary.flags.has120Policy) { throw 'Synthetic 120 policy was not detected.' }
    if (-not $summary.flags.inactiveDisplayEvidence) { throw 'Inactive-display evidence was not detected.' }
    if (-not $summary.flags.drm60Evidence) { throw '60 Hz DRM evidence was not detected.' }
    if ($summary.flags.vendorConfigFailure) { throw 'Synthetic fixture must not invent a vendor failure.' }
    if ($summary.stage -ne 'POLICY_120_BEFORE_DESIRED_MODE') {
        throw "Unexpected synthetic stage: $($summary.stage)"
    }

    Write-Host 'Refresh capture analyzer tests passed.'
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
