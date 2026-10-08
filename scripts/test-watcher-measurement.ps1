[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$path = Join-Path $PSScriptRoot 'measure-watcher-load.ps1'
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw $errors[0] }
# Load just the functions. Never run the collector or contact a device.
foreach ($function in $ast.FindAll({ param($n) $n -is [Management.Automation.Language.FunctionDefinitionAst] }, $false)) {
    . ([scriptblock]::Create($function.Extent.Text))
}
$Serial = 'fixture-thor'
$script:reply = ''
function adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Tokens)
    $script:seen = $Tokens
    $global:LASTEXITCODE = 0
    $script:reply
}
Invoke-AdbText -Arguments @('shell', "echo first`r`necho second") | Out-Null
if ($script:seen[0] -ne '-s' -or $script:seen[1] -ne $Serial -or
        $script:seen[3].Contains("`r") -or -not $script:seen[3].Contains("`n")) {
    throw 'Serial selection or Android line-ending normalization failed.'
}
$script:reply = @'
  * ContentProviderRecord{85cb71c u0 com.android.providers.settings/.SettingsProvider}
    package=com.android.providers.settings process=system
    proc=ProcessRecord{1f6ed6 2185:system/1000}
    authority=settings
'@
$provider = Get-SettingsProviderIdentity
if ($provider.ProcessId -ne 2185 -or $provider.Name -ne 'system') {
    throw 'Must resolve the actual SettingsProvider host, including system_server.'
}
$script:reply = 'provider not running'
if ($null -ne (Get-SettingsProviderIdentity)) { throw 'Missing provider must remain unavailable.' }
$before = [pscustomobject]@{ StartTime = 100 }
$after = [pscustomobject]@{ StartTime = 101 }
$rejected = $false
try { Show-ProcessDelta 'fixture' 'reused' 1 42 $before $after | Out-Null }
catch { $rejected = $_.Exception.Message -match 'recycled' }
if (-not $rejected) { throw 'Recycled PID must reject the measurement.' }
if ($null -ne (Delta-OrUnknown $null 3)) { throw 'Unknown counters must stay unknown.' }
Write-Host 'Watcher measurement fixture tests passed (no ADB/device calls).'
