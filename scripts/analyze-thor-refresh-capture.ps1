
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$CaptureDir,
    [string[]]$ExtraLog = @(),
    [string]$OutputDir = ""
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $CaptureDir -PathType Container)) {
    throw "Capture directory not found: $CaptureDir"
}
if (-not $OutputDir) {
    $OutputDir = Join-Path $CaptureDir 'analysis'
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

$allFiles = @(
    Get-ChildItem -LiteralPath $CaptureDir -File -Recurse |
        Where-Object {
            $_.FullName -notlike (Join-Path $OutputDir '*') -and
            $_.Extension -in @('.txt', '.log', '.md', '.json', '.xml')
        }
)
foreach ($path in $ExtraLog) {
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        $allFiles += Get-Item -LiteralPath $path
    } else {
        Write-Warning "Extra log not found: $path"
    }
}
$allFiles = @($allFiles | Sort-Object FullName -Unique)

function Get-MatchingLines {
    param([Parameter(Mandatory=$true)][object[]]$Patterns)

    $out = @()
    foreach ($file in $allFiles) {
        $lines = @(Get-Content -LiteralPath $file.FullName -ErrorAction SilentlyContinue)
        for ($i = 0; $i -lt $lines.Count; $i++) {
            $line = [string]$lines[$i]
            $hit = $false
            foreach ($pattern in $Patterns) {
                if ($line -match $pattern) {
                    $hit = $true
                    break
                }
            }
            if (-not $hit) { continue }
            $out += [pscustomobject]@{
                file = $file.FullName
                line = $i + 1
                text = $line.Trim()
            }
        }
    }
    return $out
}

$patterns = [ordered]@{
    policy = @('DesiredDisplayModeSpecs','Setting desired display mode specs','primaryRefreshRateRange','appRequestRefreshRateRange','min_refresh_rate','peak_refresh_rate')
    activeDisplay = @('Active Display','active display','mActiveDisplay','activeDisplay','Inactive display')
    desiredMode = @('trying to switch to Scheduler preferred mode','switching to Scheduler preferred display mode','changing active mode to','desired active mode','upcoming active mode','DesiredActiveMode','UpcomingActiveMode')
    frameworkFailure = @('initiateModeChange failed','Desired display mode is no longer supported','Desired display mode not allowed')
    hwcRequest = @('setActiveConfigWithConstraints','SetActiveConfigWithConstraints','VsyncPeriodChange','refreshRequired')
    vendorFailure = @('Invalid config','Not allowed to switch to mode','Seamless switch to the config','Failed to set .* config','BAD_CONFIG','BadConfig')
    vendorSuccess = @('Active configuration changed to','SetActiveConfig','active config')
    refresh = @('SetRefreshRate','GetRefreshRate','dynamic_fps','qsync','cur:60','cur:120','16666666','8333333')
    drm = @('1080x1920x(60|120)cmd','1080x1240x(60|120)vid','crtc=181','crtc=243','CRTC 181','CRTC 243')
}

$results = [ordered]@{}
foreach ($name in $patterns.Keys) {
    $results[$name] = @(Get-MatchingLines -Patterns $patterns[$name])
}

function Has-Text {
    param([object[]]$Rows, [string]$Pattern)
    return [bool](@($Rows | Where-Object { $_.text -match $Pattern }).Count)
}

$flags = [ordered]@{
    has120Policy = ((Has-Text $results.policy '120') -or (Has-Text $results.desiredMode '120'))
    inactiveDisplayEvidence = Has-Text $results.activeDisplay 'Inactive display'
    desired120Evidence = Has-Text $results.desiredMode '120'
    frameworkModeChangeFailure = [bool]$results.frameworkFailure.Count
    hwcConstraintEvidence = [bool]$results.hwcRequest.Count
    vendorConfigFailure = [bool]$results.vendorFailure.Count
    vendorConfigSuccess = Has-Text $results.vendorSuccess 'Active configuration changed'
    drm60Evidence = ((Has-Text $results.drm '1080x1920x60cmd') -or (Has-Text $results.drm '1080x1240x60vid'))
    drm120Mentioned = ((Has-Text $results.drm '1080x1920x120cmd') -or (Has-Text $results.drm '1080x1240x120vid'))
    vsync60Evidence = Has-Text $results.refresh '16666666'
    vsync120Evidence = Has-Text $results.refresh '8333333'
}

$stage = 'UNRESOLVED'
$reason = 'The capture does not yet prove the exact request handoff boundary.'

if ($flags.frameworkModeChangeFailure) {
    $stage = 'FRAMEWORK_TO_HWC_IMMEDIATE_FAILURE'
    $reason = 'A framework mode-change failure string is present.'
} elseif ($flags.vendorConfigFailure) {
    $stage = 'QUALCOMM_CONFIG_REJECTION_EVIDENCE'
    $reason = 'Qualcomm/HWC config rejection text is present.'
} elseif ($flags.vendorConfigSuccess -and $flags.drm60Evidence) {
    $stage = 'POST_HWC_SUCCESS_PHYSICAL_MISMATCH'
    $reason = 'A completed vendor config change is logged while DRM evidence still includes 60 Hz; inspect timestamps and config IDs before concluding.'
} elseif ($flags.desired120Evidence -and -not $flags.hwcConstraintEvidence) {
    $stage = 'DESIRED_120_BEFORE_HWC_BOUNDARY'
    $reason = 'A desired 120 mode is visible but no HWC constraint handoff evidence was found.'
} elseif ($flags.has120Policy -and -not $flags.desired120Evidence) {
    $stage = 'POLICY_120_BEFORE_DESIRED_MODE'
    $reason = '120 policy evidence exists without a 120 desired-active-mode trace.'
} elseif ($flags.hwcConstraintEvidence -and -not $flags.vendorConfigSuccess -and -not $flags.vendorConfigFailure) {
    $stage = 'HWC_PENDING_OR_UNCONFIRMED'
    $reason = 'HWC constraint-path evidence exists without a proven Qualcomm completion or rejection.'
}

$summary = [ordered]@{
    schema = 'THOR_REFRESH_CAPTURE_ANALYSIS_V1'
    captureDir = (Resolve-Path -LiteralPath $CaptureDir).Path
    analyzedFiles = $allFiles.Count
    stage = $stage
    reason = $reason
    flags = $flags
    counts = [ordered]@{}
}
foreach ($name in $results.Keys) {
    $summary.counts[$name] = $results[$name].Count
}

$jsonPath = Join-Path $OutputDir 'refresh-capture-summary.json'
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8

$report = New-Object System.Collections.Generic.List[string]
$report.Add('# Thor refresh capture analysis')
$report.Add('')
$report.Add('Schema: THOR_REFRESH_CAPTURE_ANALYSIS_V1')
$report.Add('')
$report.Add("Stage: **$stage**")
$report.Add('')
$report.Add($reason)
$report.Add('')
$report.Add('## Flags')
foreach ($key in $flags.Keys) {
    $report.Add("- $key = $($flags[$key])")
}
foreach ($name in $results.Keys) {
    $rows = @($results[$name])
    $report.Add('')
    $report.Add("## $name ($($rows.Count))")
    foreach ($row in ($rows | Select-Object -First 120)) {
        $relative = $row.file
        try {
            $relative = [IO.Path]::GetRelativePath((Resolve-Path -LiteralPath $CaptureDir).Path, $row.file)
        } catch {}
        $text = $row.text.Replace('|', '/')
        $report.Add("- ${relative}:$($row.line) - $text")
    }
    if ($rows.Count -gt 120) {
        $report.Add("- ... truncated; $($rows.Count - 120) additional matches are in the source files.")
    }
}

$mdPath = Join-Path $OutputDir 'refresh-capture-report.md'
$report | Set-Content -LiteralPath $mdPath -Encoding utf8

Write-Host "Capture analysis: $stage"
Write-Host "JSON: $jsonPath"
Write-Host "Report: $mdPath"

