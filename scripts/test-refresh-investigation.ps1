$ErrorActionPreference = 'Stop'

$collectorPath = Join-Path $PSScriptRoot 'inspect-thor-refresh.ps1'
$analyzerPath = Join-Path $PSScriptRoot 'analyze-thor-refresh-binaries.ps1'
$captureAnalyzerPath = Join-Path $PSScriptRoot 'analyze-thor-refresh-capture.ps1'

$collector = Get-Content -LiteralPath $collectorPath -Raw
$analyzer = Get-Content -LiteralPath $analyzerPath -Raw
$captureAnalyzer = Get-Content -LiteralPath $captureAnalyzerPath -Raw

$forbidden = @(
    '(?im)\bsettings\s+(put|delete)\b',
    '(?im)\bsetprop\b',
    '(?im)\bctl\.(start|stop|restart)\b',
    '(?im)\bservice\s+call\b',
    '(?im)\bcmd\s+display\s+set',
    '(?im)\badb\s+(reboot|install|push|root|remount)\b',
    '(?im)(^|[;&|]\s*)\b(rm|mv|cp|chmod|chown|touch)\s+'
)

foreach ($pattern in $forbidden) {
    if ($collector -match $pattern) {
        throw "Refresh collector contains a forbidden device mutation pattern: $pattern"
    }
}

$required = @(
    'THOR_REFRESH_INVESTIGATION_V1',
    'settings get system',
    'peak_refresh_rate',
    'min_refresh_rate',
    'dumpsys display',
    'dumpsys SurfaceFlinger --display-id',
    'dumpsys SurfaceFlinger',
    '/sys/kernel/debug/dri/0/state',
    '/sys/class/drm/card',
    '/sys/module/msm_drm/parameters',
    'PServerBinder',
    'PullBinaries',
    'adb @adbBase pull',
    'Get-FileHash'
)

foreach ($needle in $required) {
    if (-not $collector.Contains($needle)) {
        throw "Refresh collector is missing required read-only evidence: $needle"
    }
}

foreach ($needle in @('THOR_REFRESH_CAPTURE_ANALYSIS_V1', 'Inactive display', 'initiateModeChange failed', 'Active configuration changed', '1080x1240x120vid')) {
    if (-not $captureAnalyzer.Contains($needle)) {
        throw "Capture analyzer is missing required call-path evidence: $needle"
    }
}

if (-not $analyzer.Contains('SetRefreshRate') -or
    -not $analyzer.Contains('dynamic_fps') -or
    -not $analyzer.Contains('qsync')) {
    throw 'Binary analyzer is missing required refresh-rate search terms.'
}

$root = Split-Path -Parent $PSScriptRoot
$classes = Join-Path ([IO.Path]::GetTempPath()) ("thor-refresh-model-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $classes -Force | Out-Null

try {
    & javac -d $classes (Join-Path $root 'research/refresh/RefreshRateEvidenceModel.java') (Join-Path $root 'research/refresh/RefreshRateEvidenceModelTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Refresh evidence model compilation failed.' }

    & java -cp $classes RefreshRateEvidenceModelTest
    if ($LASTEXITCODE -ne 0) { throw 'Refresh evidence model tests failed.' }
} finally {
    Remove-Item -LiteralPath $classes -Recurse -Force -ErrorAction SilentlyContinue
}

& (Join-Path $PSScriptRoot 'test-refresh-capture-analysis.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Refresh capture analyzer tests failed.' }

Write-Host 'Refresh investigation contract tests passed.'
