[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9._:-]+$')]
    [string] $Serial,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-fA-F-]{36}$')]
    [string] $PreviousBootId,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_-]+$')]
    [string] $Label,

    [string] $OutputRoot = 'C:\Temp\jesty-thor-1517-evidence',
    [string] $AdbPath,
    [switch] $GraphicsCrash,
    [ValidateRange(10, 300)] [int] $WaitSeconds = 180,
    [ValidateRange(15, 150)] [int] $CaptureSeconds = 105
)

$ErrorActionPreference = 'Stop'
$adb = if ($AdbPath) { $AdbPath } else { (Get-Command adb -ErrorAction Stop).Source }
$previous = $PreviousBootId.ToLowerInvariant()
$deadline = [DateTime]::UtcNow.AddSeconds($WaitSeconds)
$newBootId = $null

# Poll only for a different kernel boot ID. A wake or a compositor restart must
# not be mistaken for a cold boot. Failed ADB reads while powered off are normal.
while ([DateTime]::UtcNow -lt $deadline) {
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $value = (& $adb -s $Serial shell 'cat /proc/sys/kernel/random/boot_id' 2>&1 |
            Out-String).Trim().ToLowerInvariant()
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $oldPreference
    }
    if ($exitCode -eq 0 -and $value -match '^[0-9a-f-]{36}$' -and $value -ne $previous) {
        $newBootId = $value
        break
    }
    Start-Sleep -Seconds 1
}
if (-not $newBootId) { throw 'No new kernel boot ID appeared before the deadline.' }

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$folder = Join-Path $OutputRoot "$stamp-$Label-early"
New-Item -ItemType Directory -Path $folder -Force | Out-Null
Set-Content -LiteralPath (Join-Path $folder 'boot-ids.txt') -Encoding UTF8 -Value @(
    "previous=$previous", "current=$newBootId", "first_adb_local=$((Get-Date).ToString('o'))")

function Save-Read {
    param([string] $Name, [string[]] $Arguments)
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $lines = @(& $adb -s $Serial @Arguments 2>&1 | ForEach-Object { [string]$_ })
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $oldPreference
    }
    if ($Name -match '-events$') {
        $lines = @($lines | Where-Object {
            $_ -match 'boot_progress|sf_stop_bootanim|wm_boot_animation_done'
        })
    }
    Set-Content -LiteralPath (Join-Path $folder "$Name.txt") -Encoding UTF8 -Value $lines
    Add-Content -LiteralPath (Join-Path $folder 'capture-index.txt') -Encoding UTF8 `
        -Value "$((Get-Date).ToString('o')) $Name exit=$exitCode"
}

$started = [DateTime]::UtcNow
$sample = 0
do {
    $sample++
    $prefix = '{0:D2}' -f $sample
    Save-Read "$prefix-uptime" @('shell', 'cat /proc/uptime')
    Save-Read "$prefix-clock-pair" @('shell', 'cat /proc/uptime; date +%s.%N')
    Save-Read "$prefix-events" @('logcat', '-b', 'events', '-v', 'monotonic', '-d')
    Save-Read "$prefix-startup" @('shell', 'getprop sys.boot_completed; getprop service.bootanim.exit; pidof system_server surfaceflinger vendor.qti.hardware.display.composer-service')
    if ($GraphicsCrash -and $sample -le 3) {
        # Keep full early buffers outside Git. Two renderings of the same entries
        # expose timestamp conversion changes without resetting a log buffer.
        Save-Read "$prefix-graphics-epoch" @('logcat', '-b', 'main', '-b', 'system', '-b', 'crash', '-v', 'epoch', '-d')
        Save-Read "$prefix-graphics-monotonic" @('logcat', '-b', 'main', '-b', 'system', '-b', 'crash', '-v', 'monotonic', '-d')
        Save-Read "$prefix-events-epoch" @('logcat', '-b', 'events', '-v', 'epoch', '-d')
        if ($sample -eq 1) { Save-Read 'early-dmesg' @('shell', 'dmesg') }
    }
    if (([DateTime]::UtcNow - $started).TotalSeconds -ge $CaptureSeconds) { break }
    Start-Sleep -Seconds 15
} while ($true)

Get-ChildItem -LiteralPath $folder -File | Where-Object { $_.Name -ne 'SHA256SUMS.txt' } |
    ForEach-Object { '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash, $_.Name } |
    Set-Content -LiteralPath (Join-Path $folder 'SHA256SUMS.txt') -Encoding UTF8
Write-Output "Early boot evidence saved outside Git: $folder"
