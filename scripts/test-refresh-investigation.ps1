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
    'THOR_REFRESH_INVESTIGATION_V2',
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
    'Get-FileHash',
    '00-capture-status.tsv',
    '00-capture-summary.json',
    'THOR_REFRESH_CAPTURE_STATUS_V1',
    'captureComplete',
    'requiredFailures',
    '/sys/class/bypass_ram_class/bypass_ram_device/bypass_ram',
    'panel1-backlight',
    'dsi-supported-dfps-list',
    'logcat -b all -d',
    'dmesg'
)

foreach ($needle in $required) {
    if (-not $collector.Contains($needle)) {
        throw "Refresh collector is missing required read-only evidence: $needle"
    }
}

foreach ($needle in @('THOR_REFRESH_CAPTURE_ANALYSIS_V2', 'causalConclusion', 'policyTransitions', 'mode changes were performed under the previous policy', 'Inactive display', 'initiateModeChange failed', 'Active configuration changed', '1080x1240x120vid', 'bypass_ram')) {
    if (-not $captureAnalyzer.Contains($needle)) {
        throw "Capture analyzer is missing required call-path evidence: $needle"
    }
}

if (-not $analyzer.Contains('THOR_REFRESH_BINARY_STRINGS_V2') -or
    -not $analyzer.Contains('SetRefreshRate') -or
    -not $analyzer.Contains('dynamic_fps') -or
    -not $analyzer.Contains('qsync') -or
    -not $analyzer.Contains('bypass_ram')) {
    throw 'Binary analyzer is missing required refresh-rate / AYN lower-panel search terms.'
}
if ($analyzer -notmatch '\[IO\.Path\]::GetFullPath\(\$_\.FullName\) -ne \$outputFullPath') {
    throw 'Binary analyzer must exclude its own report from recursive input.'
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
