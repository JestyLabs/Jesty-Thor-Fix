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
    'bypass_ram', 'VID_BYPASS_RAM', 'VID_PASS_RAM', 'invalid mode'
)

function Get-AsciiStrings {
    param([Parameter(Mandatory=$true)][string]$Path)

    $bytes = [IO.File]::ReadAllBytes($Path)
    $latin1 = [Text.Encoding]::GetEncoding(28591).GetString($bytes)
    foreach ($m in [regex]::Matches($latin1, '[\x20-\x7E]{4,}')) {
        [pscustomobject]@{ offset = $m.Index; text = $m.Value }
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
    # A text report, README or JSON with symbol names is not a binary hit.
    $stream = [IO.File]::OpenRead($file.FullName)
    try {
        $magic = New-Object byte[] 4
        $count = $stream.Read($magic, 0, 4)
    } finally { $stream.Dispose() }
    if ($count -ne 4 -or $magic[0] -ne 0x7f -or
        $magic[1] -ne 0x45 -or $magic[2] -ne 0x4c -or $magic[3] -ne 0x46) { continue }
    $matches = @(Get-AsciiStrings -Path $file.FullName |
        Where-Object {
            $line = $_.text
            [bool]($needles | Where-Object {
                $line.IndexOf($_, [StringComparison]::OrdinalIgnoreCase) -ge 0
            })
        } |
        Sort-Object offset)

    if ($matches.Count -eq 0) { continue }

    $report.Add("### $($file.FullName)")
    $report.Add('sha256=' + (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash)
    $report.Add('String presence is not execution. Offsets below are file offsets, not ELF virtual addresses.')
    foreach ($line in $matches) {
        $report.Add(('file_offset=0x{0:x} {1}' -f $line.offset, $line.text))
    }
    $report.Add("")
}

$report | Set-Content -LiteralPath $OutputFile -Encoding utf8
Write-Host "Refresh-related binary strings saved to: $OutputFile"
