[CmdletBinding()]
param()

# CI checks tracked text and pull-request metadata without embedding the
# disallowed phrase in this repository.
$ErrorActionPreference = 'Stop'
$prefix = -join ([char[]]@(83,108,101,101,112))
$suffix = -join ([char[]]@(77,97,110,97,103,101,114))
$pattern = '(?i)' + [regex]::Escape($prefix) + '[\s_-]*' + [regex]::Escape($suffix)
$extensions = @('.md','.txt','.java','.smali','.ps1','.psm1','.sh',
    '.yml','.yaml','.json','.xml','.py','.gradle','.kts','.properties','.kt')
$paths = @(git ls-files)
if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate tracked files.' }
foreach ($path in $paths) {
    $ext = [IO.Path]::GetExtension($path).ToLowerInvariant()
    if ($ext -notin $extensions -and $path -notin @('README','Makefile','LICENSE')) { continue }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
    $source = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $path).Path)
    if ([regex]::IsMatch($source, $pattern)) {
        throw "Repository content policy failed for tracked file: $path"
    }
}
if ($env:GITHUB_EVENT_PATH -and (Test-Path -LiteralPath $env:GITHUB_EVENT_PATH)) {
    $event = Get-Content -LiteralPath $env:GITHUB_EVENT_PATH -Raw | ConvertFrom-Json
    if ($null -ne $event.pull_request) {
        foreach ($field in @('title','body')) {
            if ([regex]::IsMatch([string]($event.pull_request.$field), $pattern)) {
                throw "Repository content policy failed for PR $field."
            }
        }
    }
}
Write-Host 'Repository content check passed.'
