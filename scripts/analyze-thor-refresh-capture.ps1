
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

$captureRoot = (Resolve-Path -LiteralPath $CaptureDir).Path
$outputRoot = [IO.Path]::GetFullPath($OutputDir)

$allFiles = @(
    Get-ChildItem -LiteralPath $CaptureDir -File -Recurse |
        Where-Object {
            -not [IO.Path]::GetFullPath($_.FullName).StartsWith(
                $outputRoot + [IO.Path]::DirectorySeparatorChar,
                [StringComparison]::OrdinalIgnoreCase) -and
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

function Has-Text {
    param([object[]]$Rows, [string]$Pattern)
    return [bool](@($Rows | Where-Object { $_.text -match $Pattern }).Count)
}

function Evidence-Files {
    param([object[]]$Rows)
    return @($Rows | ForEach-Object {
        try { [IO.Path]::GetRelativePath($captureRoot, $_.file) }
        catch { $_.file }
    } | Sort-Object -Unique)
}

function Get-PolicyTransitions {
    $out = @()
    foreach ($file in $allFiles) {
        $lines = @(Get-Content -LiteralPath $file.FullName -ErrorAction SilentlyContinue)
        for ($i = 0; $i -lt $lines.Count; $i++) {
            $header = [string]$lines[$i]
            $headerMatch = [regex]::Match($header, 'Display\s+(.+?)\s+policy changed')
            if (-not $headerMatch.Success) { continue }

            $last = [Math]::Min($lines.Count - 1, $i + 10)
            $previous = $null
            $current = $null
            $modeChanges = $null

            for ($j = $i; $j -le $last; $j++) {
                $candidate = [string]$lines[$j]

                if ($null -eq $previous) {
                    $m = [regex]::Match($candidate, 'Previous:\s*(.+)
    policy = @(
        'DesiredDisplayModeSpecs','Setting desired display mode specs',
        'primaryRefreshRateRange','appRequestRefreshRateRange',
        'min_refresh_rate','peak_refresh_rate'
    )
    activeDisplay = @(
        'Active Display','active display','mActiveDisplay','activeDisplay','Inactive display'
    )
    desiredMode = @(
        'trying to switch to Scheduler preferred mode',
        'switching to Scheduler preferred display mode',
        'changing active mode to','desired active mode','upcoming active mode',
        'DesiredActiveMode','UpcomingActiveMode'
    )
    frameworkFailure = @(
        'initiateModeChange failed','Desired display mode is no longer supported',
        'Desired display mode not allowed'
    )
    hwcRequest = @(
        'setActiveConfigWithConstraints','SetActiveConfigWithConstraints',
        'VsyncPeriodChange','refreshRequired'
    )
    vendorFailure = @(
        'Invalid config','Not allowed to switch to mode','Seamless switch to the config',
        'Failed to set .* config','BAD_CONFIG','BadConfig'
    )
    vendorSuccess = @('Active configuration changed to','SetActiveConfig','active config')
    refresh = @(
        'SetRefreshRate','GetRefreshRate','dynamic_fps','qsync',
        'cur:60','cur:120','16666666','8333333'
    )
    drm = @(
        '1080x1920x(60|120)cmd','1080x1240x(60|120)vid',
        'crtc=181','crtc=243','CRTC 181','CRTC 243'
    )
    aynLowerPath = @(
        'bypass_ram','VID_BYPASS_RAM','VID_PASS_RAM',
        'panel1-backlight','ch13726a'
    )
}

$results = [ordered]@{}
foreach ($name in $patterns.Keys) {
    $results[$name] = @(Get-MatchingLines -Patterns $patterns[$name])
}

$flags = [ordered]@{
    has120PolicyMention = ((Has-Text $results.policy '120') -or (Has-Text $results.desiredMode '120'))
    inactiveDisplayEvidence = Has-Text $results.activeDisplay 'Inactive display'
    desired120Evidence = Has-Text $results.desiredMode '120'
    frameworkModeChangeFailure = [bool]$results.frameworkFailure.Count
    hwcConstraintEvidence = [bool]$results.hwcRequest.Count
    vendorConfigFailure = [bool]$results.vendorFailure.Count
    vendorConfigSuccess = Has-Text $results.vendorSuccess 'Active configuration changed'
    drm60Evidence = ((Has-Text $results.drm '1080x1920x60cmd') -or
        (Has-Text $results.drm '1080x1240x60vid'))
    drm120Mentioned = ((Has-Text $results.drm '1080x1920x120cmd') -or
        (Has-Text $results.drm '1080x1240x120vid'))
    vsync60Evidence = Has-Text $results.refresh '16666666'
    vsync120Evidence = Has-Text $results.refresh '8333333'
    aynLowerPathEvidence = [bool]$results.aynLowerPath.Count
}

# This analyzer searches heterogeneous snapshots and logs. Presence/absence of a
# string across the bundle is useful triage, but is not a timestamp-correlated
# proof that one event preceded or caused another. Keep classifications as hints.
$stage = 'UNRESOLVED_EVIDENCE'
$reason = 'No single request handoff boundary is proven by bundle-wide text matches.'

if ($flags.frameworkModeChangeFailure) {
    $stage = 'FRAMEWORK_MODE_CHANGE_FAILURE_EVIDENCE'
    $reason = 'A framework mode-change failure marker is present; correlate display ID and timestamp manually.'
} elseif ($flags.vendorConfigFailure) {
    $stage = 'QUALCOMM_CONFIG_REJECTION_EVIDENCE'
    $reason = 'A Qualcomm/HWC rejection marker is present; correlate it with the target display and probe window manually.'
} elseif ($flags.vendorConfigSuccess -and $flags.drm60Evidence) {
    $stage = 'VENDOR_SUCCESS_AND_DRM60_EVIDENCE'
    $reason = 'Vendor-success and 60 Hz DRM markers both exist in the bundle; they are not assumed to describe the same display or instant.'
} elseif ($flags.desired120Evidence -and -not $flags.hwcConstraintEvidence) {
    $stage = 'DESIRED_120_WITHOUT_HWC_EVIDENCE'
    $reason = 'Desired-120 evidence exists and no HWC handoff marker was found in the searched files; absence is a hint, not proof.'
} elseif ($flags.has120PolicyMention -and -not $flags.desired120Evidence) {
    $stage = 'POLICY_120_MENTION_WITHOUT_DESIRED_MODE_EVIDENCE'
    $reason = 'A 120 value appears in policy-related evidence without a desired-120 marker in the searched files; this may be a fixed-120 request or only a range containing 120, and does not establish chronological suppression.'
} elseif ($flags.hwcConstraintEvidence -and -not $flags.vendorConfigSuccess -and
        -not $flags.vendorConfigFailure) {
    $stage = 'HWC_EVIDENCE_WITHOUT_VENDOR_OUTCOME'
    $reason = 'HWC-path evidence exists without a vendor completion/rejection marker in the searched files.'
}

$evidenceFiles = [ordered]@{}
foreach ($name in $results.Keys) {
    $evidenceFiles[$name] = @(Evidence-Files $results[$name])
}

$policyTransitions = @(Get-PolicyTransitions)

$summary = [ordered]@{
    schema = 'THOR_REFRESH_CAPTURE_ANALYSIS_V2'
    captureDir = $captureRoot
    analyzedFiles = $allFiles.Count
    stage = $stage
    reason = $reason
    causalConclusion = $false
    correlationScope = 'capture-wide uncorrelated text matches; manually correlate timestamp and display before causal claims'
    policyTransitions = $policyTransitions
    flags = $flags
    counts = [ordered]@{}
    evidenceFiles = $evidenceFiles
}
foreach ($name in $results.Keys) {
    $summary.counts[$name] = $results[$name].Count
}

$jsonPath = Join-Path $OutputDir 'refresh-capture-summary.json'
$summary | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $jsonPath -Encoding utf8

$report = New-Object System.Collections.Generic.List[string]
$report.Add('# Thor refresh capture analysis')
$report.Add('')
$report.Add('Schema: THOR_REFRESH_CAPTURE_ANALYSIS_V2')
$report.Add('')
$report.Add("Evidence stage: **$stage**")
$report.Add('')
$report.Add($reason)
$report.Add('')
$report.Add('**Causality:** not inferred automatically. Correlate display identity and timestamps manually.')
$report.Add('')
$report.Add('## Ordered SurfaceFlinger policy transitions')
if ($policyTransitions.Count -eq 0) {
    $report.Add('- none found')
} else {
    foreach ($transition in $policyTransitions) {
        $count = if ($null -eq $transition.previousPolicyModeChanges) {
            '?'
        } else {
            [string]$transition.previousPolicyModeChanges
        }
        $when = if ($transition.timestamp) { $transition.timestamp } else { 'time=?' }
        $previous = if ($transition.previous) { $transition.previous } else { '?' }
        $current = if ($transition.current) { $transition.current } else { '?' }
        $report.Add(
            '- ' + $transition.file + ':' + $transition.line +
            ' [' + $when + '] display=' + $transition.display +
            ' previous=' + $previous +
            ' current=' + $current +
            ' previous_policy_mode_changes=' + $count)
    }
}
$report.Add('')
$report.Add('These transitions preserve order within each source file. They still require display/timestamp correlation with other logs before a causal claim.')
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
        try { $relative = [IO.Path]::GetRelativePath($captureRoot, $row.file) } catch {}
        $text = $row.text.Replace('|', '/')
        $report.Add("- " + $relative + ":" + $row.line + " - " + $text)
    }
    if ($rows.Count -gt 120) {
        $report.Add("- ... truncated; $($rows.Count - 120) additional matches are in the source files.")
    }
}

$mdPath = Join-Path $OutputDir 'refresh-capture-report.md'
$report | Set-Content -LiteralPath $mdPath -Encoding utf8

Write-Host "Capture evidence stage: $stage"
Write-Host "JSON: $jsonPath"
Write-Host "Report: $mdPath"
)
                    if ($m.Success) { $previous = $m.Groups[1].Value.Trim() }
                }

                if ($null -eq $current) {
                    $m = [regex]::Match($candidate, 'Current:\s*(.+)
    policy = @(
        'DesiredDisplayModeSpecs','Setting desired display mode specs',
        'primaryRefreshRateRange','appRequestRefreshRateRange',
        'min_refresh_rate','peak_refresh_rate'
    )
    activeDisplay = @(
        'Active Display','active display','mActiveDisplay','activeDisplay','Inactive display'
    )
    desiredMode = @(
        'trying to switch to Scheduler preferred mode',
        'switching to Scheduler preferred display mode',
        'changing active mode to','desired active mode','upcoming active mode',
        'DesiredActiveMode','UpcomingActiveMode'
    )
    frameworkFailure = @(
        'initiateModeChange failed','Desired display mode is no longer supported',
        'Desired display mode not allowed'
    )
    hwcRequest = @(
        'setActiveConfigWithConstraints','SetActiveConfigWithConstraints',
        'VsyncPeriodChange','refreshRequired'
    )
    vendorFailure = @(
        'Invalid config','Not allowed to switch to mode','Seamless switch to the config',
        'Failed to set .* config','BAD_CONFIG','BadConfig'
    )
    vendorSuccess = @('Active configuration changed to','SetActiveConfig','active config')
    refresh = @(
        'SetRefreshRate','GetRefreshRate','dynamic_fps','qsync',
        'cur:60','cur:120','16666666','8333333'
    )
    drm = @(
        '1080x1920x(60|120)cmd','1080x1240x(60|120)vid',
        'crtc=181','crtc=243','CRTC 181','CRTC 243'
    )
    aynLowerPath = @(
        'bypass_ram','VID_BYPASS_RAM','VID_PASS_RAM',
        'panel1-backlight','ch13726a'
    )
}

$results = [ordered]@{}
foreach ($name in $patterns.Keys) {
    $results[$name] = @(Get-MatchingLines -Patterns $patterns[$name])
}

$flags = [ordered]@{
    has120PolicyMention = ((Has-Text $results.policy '120') -or (Has-Text $results.desiredMode '120'))
    inactiveDisplayEvidence = Has-Text $results.activeDisplay 'Inactive display'
    desired120Evidence = Has-Text $results.desiredMode '120'
    frameworkModeChangeFailure = [bool]$results.frameworkFailure.Count
    hwcConstraintEvidence = [bool]$results.hwcRequest.Count
    vendorConfigFailure = [bool]$results.vendorFailure.Count
    vendorConfigSuccess = Has-Text $results.vendorSuccess 'Active configuration changed'
    drm60Evidence = ((Has-Text $results.drm '1080x1920x60cmd') -or
        (Has-Text $results.drm '1080x1240x60vid'))
    drm120Mentioned = ((Has-Text $results.drm '1080x1920x120cmd') -or
        (Has-Text $results.drm '1080x1240x120vid'))
    vsync60Evidence = Has-Text $results.refresh '16666666'
    vsync120Evidence = Has-Text $results.refresh '8333333'
    aynLowerPathEvidence = [bool]$results.aynLowerPath.Count
}

# This analyzer searches heterogeneous snapshots and logs. Presence/absence of a
# string across the bundle is useful triage, but is not a timestamp-correlated
# proof that one event preceded or caused another. Keep classifications as hints.
$stage = 'UNRESOLVED_EVIDENCE'
$reason = 'No single request handoff boundary is proven by bundle-wide text matches.'

if ($flags.frameworkModeChangeFailure) {
    $stage = 'FRAMEWORK_MODE_CHANGE_FAILURE_EVIDENCE'
    $reason = 'A framework mode-change failure marker is present; correlate display ID and timestamp manually.'
} elseif ($flags.vendorConfigFailure) {
    $stage = 'QUALCOMM_CONFIG_REJECTION_EVIDENCE'
    $reason = 'A Qualcomm/HWC rejection marker is present; correlate it with the target display and probe window manually.'
} elseif ($flags.vendorConfigSuccess -and $flags.drm60Evidence) {
    $stage = 'VENDOR_SUCCESS_AND_DRM60_EVIDENCE'
    $reason = 'Vendor-success and 60 Hz DRM markers both exist in the bundle; they are not assumed to describe the same display or instant.'
} elseif ($flags.desired120Evidence -and -not $flags.hwcConstraintEvidence) {
    $stage = 'DESIRED_120_WITHOUT_HWC_EVIDENCE'
    $reason = 'Desired-120 evidence exists and no HWC handoff marker was found in the searched files; absence is a hint, not proof.'
} elseif ($flags.has120PolicyMention -and -not $flags.desired120Evidence) {
    $stage = 'POLICY_120_MENTION_WITHOUT_DESIRED_MODE_EVIDENCE'
    $reason = 'A 120 value appears in policy-related evidence without a desired-120 marker in the searched files; this may be a fixed-120 request or only a range containing 120, and does not establish chronological suppression.'
} elseif ($flags.hwcConstraintEvidence -and -not $flags.vendorConfigSuccess -and
        -not $flags.vendorConfigFailure) {
    $stage = 'HWC_EVIDENCE_WITHOUT_VENDOR_OUTCOME'
    $reason = 'HWC-path evidence exists without a vendor completion/rejection marker in the searched files.'
}

$evidenceFiles = [ordered]@{}
foreach ($name in $results.Keys) {
    $evidenceFiles[$name] = @(Evidence-Files $results[$name])
}

$summary = [ordered]@{
    schema = 'THOR_REFRESH_CAPTURE_ANALYSIS_V2'
    captureDir = $captureRoot
    analyzedFiles = $allFiles.Count
    stage = $stage
    reason = $reason
    causalConclusion = $false
    correlationScope = 'capture-wide uncorrelated text matches; manually correlate timestamp and display before causal claims'
    flags = $flags
    counts = [ordered]@{}
    evidenceFiles = $evidenceFiles
}
foreach ($name in $results.Keys) {
    $summary.counts[$name] = $results[$name].Count
}

$jsonPath = Join-Path $OutputDir 'refresh-capture-summary.json'
$summary | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $jsonPath -Encoding utf8

$report = New-Object System.Collections.Generic.List[string]
$report.Add('# Thor refresh capture analysis')
$report.Add('')
$report.Add('Schema: THOR_REFRESH_CAPTURE_ANALYSIS_V2')
$report.Add('')
$report.Add("Evidence stage: **$stage**")
$report.Add('')
$report.Add($reason)
$report.Add('')
$report.Add('**Causality:** not inferred automatically. Correlate display identity and timestamps manually.')
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
        try { $relative = [IO.Path]::GetRelativePath($captureRoot, $row.file) } catch {}
        $text = $row.text.Replace('|', '/')
        $report.Add("- " + $relative + ":" + $row.line + " - " + $text)
    }
    if ($rows.Count -gt 120) {
        $report.Add("- ... truncated; $($rows.Count - 120) additional matches are in the source files.")
    }
}

$mdPath = Join-Path $OutputDir 'refresh-capture-report.md'
$report | Set-Content -LiteralPath $mdPath -Encoding utf8

Write-Host "Capture evidence stage: $stage"
Write-Host "JSON: $jsonPath"
Write-Host "Report: $mdPath"
)
                    if ($m.Success) { $current = $m.Groups[1].Value.Trim() }
                }

                if ($null -eq $modeChanges) {
                    $m = [regex]::Match(
                        $candidate,
                        '(\d+)\s+mode changes were performed under the previous policy')
                    if ($m.Success) { $modeChanges = [int]$m.Groups[1].Value }
                }
            }

            $timestamp = $null
            $timeMatch = [regex]::Match(
                $header,
                '\b(?:\d{2}-\d{2}\s+)?\d{2}:\d{2}:\d{2}\.\d+\b')
            if ($timeMatch.Success) { $timestamp = $timeMatch.Value }

            $relative = $file.FullName
            try { $relative = [IO.Path]::GetRelativePath($captureRoot, $file.FullName) } catch {}

            $out += [pscustomobject]@{
                file = $relative
                line = $i + 1
                timestamp = $timestamp
                display = $headerMatch.Groups[1].Value.Trim()
                previous = $previous
                current = $current
                previousPolicyModeChanges = $modeChanges
            }
        }
    }
    return $out
}

$patterns = [ordered]@{
    policy = @(
        'DesiredDisplayModeSpecs','Setting desired display mode specs',
        'primaryRefreshRateRange','appRequestRefreshRateRange',
        'min_refresh_rate','peak_refresh_rate'
    )
    activeDisplay = @(
        'Active Display','active display','mActiveDisplay','activeDisplay','Inactive display'
    )
    desiredMode = @(
        'trying to switch to Scheduler preferred mode',
        'switching to Scheduler preferred display mode',
        'changing active mode to','desired active mode','upcoming active mode',
        'DesiredActiveMode','UpcomingActiveMode'
    )
    frameworkFailure = @(
        'initiateModeChange failed','Desired display mode is no longer supported',
        'Desired display mode not allowed'
    )
    hwcRequest = @(
        'setActiveConfigWithConstraints','SetActiveConfigWithConstraints',
        'VsyncPeriodChange','refreshRequired'
    )
    vendorFailure = @(
        'Invalid config','Not allowed to switch to mode','Seamless switch to the config',
        'Failed to set .* config','BAD_CONFIG','BadConfig'
    )
    vendorSuccess = @('Active configuration changed to','SetActiveConfig','active config')
    refresh = @(
        'SetRefreshRate','GetRefreshRate','dynamic_fps','qsync',
        'cur:60','cur:120','16666666','8333333'
    )
    drm = @(
        '1080x1920x(60|120)cmd','1080x1240x(60|120)vid',
        'crtc=181','crtc=243','CRTC 181','CRTC 243'
    )
    aynLowerPath = @(
        'bypass_ram','VID_BYPASS_RAM','VID_PASS_RAM',
        'panel1-backlight','ch13726a'
    )
}

$results = [ordered]@{}
foreach ($name in $patterns.Keys) {
    $results[$name] = @(Get-MatchingLines -Patterns $patterns[$name])
}

$flags = [ordered]@{
    has120PolicyMention = ((Has-Text $results.policy '120') -or (Has-Text $results.desiredMode '120'))
    inactiveDisplayEvidence = Has-Text $results.activeDisplay 'Inactive display'
    desired120Evidence = Has-Text $results.desiredMode '120'
    frameworkModeChangeFailure = [bool]$results.frameworkFailure.Count
    hwcConstraintEvidence = [bool]$results.hwcRequest.Count
    vendorConfigFailure = [bool]$results.vendorFailure.Count
    vendorConfigSuccess = Has-Text $results.vendorSuccess 'Active configuration changed'
    drm60Evidence = ((Has-Text $results.drm '1080x1920x60cmd') -or
        (Has-Text $results.drm '1080x1240x60vid'))
    drm120Mentioned = ((Has-Text $results.drm '1080x1920x120cmd') -or
        (Has-Text $results.drm '1080x1240x120vid'))
    vsync60Evidence = Has-Text $results.refresh '16666666'
    vsync120Evidence = Has-Text $results.refresh '8333333'
    aynLowerPathEvidence = [bool]$results.aynLowerPath.Count
}

# This analyzer searches heterogeneous snapshots and logs. Presence/absence of a
# string across the bundle is useful triage, but is not a timestamp-correlated
# proof that one event preceded or caused another. Keep classifications as hints.
$stage = 'UNRESOLVED_EVIDENCE'
$reason = 'No single request handoff boundary is proven by bundle-wide text matches.'

if ($flags.frameworkModeChangeFailure) {
    $stage = 'FRAMEWORK_MODE_CHANGE_FAILURE_EVIDENCE'
    $reason = 'A framework mode-change failure marker is present; correlate display ID and timestamp manually.'
} elseif ($flags.vendorConfigFailure) {
    $stage = 'QUALCOMM_CONFIG_REJECTION_EVIDENCE'
    $reason = 'A Qualcomm/HWC rejection marker is present; correlate it with the target display and probe window manually.'
} elseif ($flags.vendorConfigSuccess -and $flags.drm60Evidence) {
    $stage = 'VENDOR_SUCCESS_AND_DRM60_EVIDENCE'
    $reason = 'Vendor-success and 60 Hz DRM markers both exist in the bundle; they are not assumed to describe the same display or instant.'
} elseif ($flags.desired120Evidence -and -not $flags.hwcConstraintEvidence) {
    $stage = 'DESIRED_120_WITHOUT_HWC_EVIDENCE'
    $reason = 'Desired-120 evidence exists and no HWC handoff marker was found in the searched files; absence is a hint, not proof.'
} elseif ($flags.has120PolicyMention -and -not $flags.desired120Evidence) {
    $stage = 'POLICY_120_MENTION_WITHOUT_DESIRED_MODE_EVIDENCE'
    $reason = 'A 120 value appears in policy-related evidence without a desired-120 marker in the searched files; this may be a fixed-120 request or only a range containing 120, and does not establish chronological suppression.'
} elseif ($flags.hwcConstraintEvidence -and -not $flags.vendorConfigSuccess -and
        -not $flags.vendorConfigFailure) {
    $stage = 'HWC_EVIDENCE_WITHOUT_VENDOR_OUTCOME'
    $reason = 'HWC-path evidence exists without a vendor completion/rejection marker in the searched files.'
}

$evidenceFiles = [ordered]@{}
foreach ($name in $results.Keys) {
    $evidenceFiles[$name] = @(Evidence-Files $results[$name])
}

$summary = [ordered]@{
    schema = 'THOR_REFRESH_CAPTURE_ANALYSIS_V2'
    captureDir = $captureRoot
    analyzedFiles = $allFiles.Count
    stage = $stage
    reason = $reason
    causalConclusion = $false
    correlationScope = 'capture-wide uncorrelated text matches; manually correlate timestamp and display before causal claims'
    flags = $flags
    counts = [ordered]@{}
    evidenceFiles = $evidenceFiles
}
foreach ($name in $results.Keys) {
    $summary.counts[$name] = $results[$name].Count
}

$jsonPath = Join-Path $OutputDir 'refresh-capture-summary.json'
$summary | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $jsonPath -Encoding utf8

$report = New-Object System.Collections.Generic.List[string]
$report.Add('# Thor refresh capture analysis')
$report.Add('')
$report.Add('Schema: THOR_REFRESH_CAPTURE_ANALYSIS_V2')
$report.Add('')
$report.Add("Evidence stage: **$stage**")
$report.Add('')
$report.Add($reason)
$report.Add('')
$report.Add('**Causality:** not inferred automatically. Correlate display identity and timestamps manually.')
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
        try { $relative = [IO.Path]::GetRelativePath($captureRoot, $row.file) } catch {}
        $text = $row.text.Replace('|', '/')
        $report.Add("- " + $relative + ":" + $row.line + " - " + $text)
    }
    if ($rows.Count -gt 120) {
        $report.Add("- ... truncated; $($rows.Count - 120) additional matches are in the source files.")
    }
}

$mdPath = Join-Path $OutputDir 'refresh-capture-report.md'
$report | Set-Content -LiteralPath $mdPath -Encoding utf8

Write-Host "Capture evidence stage: $stage"
Write-Host "JSON: $jsonPath"
Write-Host "Report: $mdPath"
