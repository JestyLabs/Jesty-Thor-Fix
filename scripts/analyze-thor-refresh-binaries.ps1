[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$BinaryDir,
    [string]$OutputFile = ""
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $BinaryDir)) {
    throw "Binary directory not found: $BinaryDir"
}
if (-not $OutputFile) {
    $OutputFile = Join-Path $BinaryDir 'refresh-string-report.txt'
}

$needles = @(
    'refresh', 'fps', 'vsync', 'qsync', 'dynamic_fps',
    'SetRefreshRate', 'GetRefreshRate', 'SetActiveConfig', 'GetConfig',
    'DisplayBuiltIn', 'DisplayBase', 'HWDisplayAttributes',
    'active_config', 'display_attributes', 'dsi_display0', 'dsi_display1'
)

function Get-AsciiStrings {
    param([Parameter(Mandatory=$true)][string]$Path)

    $bytes = [IO.File]::ReadAllBytes($Path)
    $latin1 = [Text.Encoding]::GetEncoding(28591).GetString($bytes)
    foreach ($m in [regex]::Matches($latin1, '[\x20-\x7E]{4,}')) {
        $m.Value
    }
}

$report = New-Object System.Collections.Generic.List[string]

foreach ($file in Get-ChildItem -LiteralPath $BinaryDir -File -Recurse | Sort-Object FullName) {
    $matches = @(Get-AsciiStrings -Path $file.FullName |
        Where-Object {
            $line = $_
            $needles | Where-Object { $line.IndexOf($_, [StringComparison]::OrdinalIgnoreCase) -ge 0 }
        } |
        Select-Object -Unique)

    if ($matches.Count -eq 0) { continue }

    $report.Add("### $($file.FullName)")
    foreach ($line in $matches) {
        $report.Add($line)
    }
    $report.Add("")
}

$report | Set-Content -LiteralPath $OutputFile -Encoding utf8
Write-Host "Refresh-related binary strings saved to: $OutputFile"
