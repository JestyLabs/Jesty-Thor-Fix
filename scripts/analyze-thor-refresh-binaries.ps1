[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$BinaryDir,
    [string]$OutputFile = ""
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $BinaryDir -PathType Container)) {
    throw "Binary directory not found: $BinaryDir"
}
if (-not $OutputFile) {
    $OutputFile = Join-Path $BinaryDir 'refresh-string-report.txt'
}

$binaryRoot = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $BinaryDir).Path)
$outputFullPath = [IO.Path]::GetFullPath($OutputFile)

$needles = @(
    'refresh', 'fps', 'vsync', 'qsync', 'dynamic_fps',
    'SetRefreshRate', 'GetRefreshRate', 'SetActiveConfig', 'GetConfig',
    'DisplayBuiltIn', 'DisplayBase', 'HWDisplayAttributes',
    'active_config', 'display_attributes', 'dsi_display0', 'dsi_display1',
    'bypass_ram', 'VID_BYPASS_RAM', 'VID_PASS_RAM'
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
$report.Add('THOR_REFRESH_BINARY_STRINGS_V2')
$report.Add("binary_root=$binaryRoot")
$report.Add('')

$inputs = @(
    Get-ChildItem -LiteralPath $BinaryDir -File -Recurse |
        Where-Object {
            # The default report lives inside BinaryDir. Never feed a previous
            # report back into the next run or string matches amplify themselves.
            [IO.Path]::GetFullPath($_.FullName) -ne $outputFullPath
        } |
        Sort-Object FullName
)

foreach ($file in $inputs) {
    $matches = @(Get-AsciiStrings -Path $file.FullName |
        Where-Object {
            $line = $_
            [bool]($needles | Where-Object {
                $line.IndexOf($_, [StringComparison]::OrdinalIgnoreCase) -ge 0
            })
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
