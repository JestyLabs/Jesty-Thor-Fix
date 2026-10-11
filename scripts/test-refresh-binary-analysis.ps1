$ErrorActionPreference = 'Stop'
$root = Join-Path ([IO.Path]::GetTempPath()) ('thor-refresh-binary-test-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
try {
    # Minimal ELF-magic fixture tests string triage only, not ELF validity.
    $bytes = [byte[]](0x7f, 0x45, 0x4c, 0x46, 0x00) + [Text.Encoding]::ASCII.GetBytes('SetRefreshRate') + [byte[]](0)
    [IO.File]::WriteAllBytes((Join-Path $root 'fixture.so'), $bytes)
    'SetRefreshRate invalid mode' | Set-Content -LiteralPath (Join-Path $root 'README.txt')
    $report = Join-Path $root 'refresh-string-report.txt'
    & (Join-Path $PSScriptRoot 'analyze-thor-refresh-binaries.ps1') -BinaryDir $root
    $first = Get-Content -LiteralPath $report -Raw
    if ($first -notmatch 'file_offset=0x5 SetRefreshRate' -or
        $first -notmatch 'sha256=[A-F0-9]{64}' -or $first -match 'README.txt') {
        throw 'Binary strings need offsets/hashes and must exclude text documents.'
    }
    & (Join-Path $PSScriptRoot 'analyze-thor-refresh-binaries.ps1') -BinaryDir $root
    if ((Get-Content -LiteralPath $report -Raw) -ne $first) { throw 'Binary report fed itself into a repeat run.' }
    Write-Host 'Refresh binary analyzer tests passed.'
} finally {
    $cleanup = [IO.Path]::GetFullPath($root)
    if (-not $cleanup.StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath()), [StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path -Leaf $cleanup) -notlike 'thor-refresh-binary-test-*') { throw 'Unsafe binary-test cleanup path.' }
    Remove-Item -LiteralPath $cleanup -Recurse -Force
}
