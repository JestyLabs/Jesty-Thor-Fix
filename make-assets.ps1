[CmdletBinding()]
param(
    [string] $FfmpegPath = $env:FFMPEG_PATH,
    [string] $OnBackground,
    [string] $OffBackground
)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not $FfmpegPath) {
    $ffmpeg = Get-Command ffmpeg.exe -ErrorAction SilentlyContinue
    if (-not $ffmpeg) { throw 'Install ffmpeg or pass -FfmpegPath.' }
    $FfmpegPath = $ffmpeg.Source
}

$background = if ($OnBackground) { $OnBackground } else {
    Join-Path $project 'assets\jesty_thor_background.png'
}
$backgroundOff = if ($OffBackground) { $OffBackground } else {
    Join-Path $project 'assets\jesty_thor_background_off.png'
}
$static = Join-Path $project 'apk\res\drawable-nodpi\jesty_thor_background.png'
$staticOff = Join-Path $project 'apk\res\drawable-nodpi\jesty_thor_background_off.png'
$video = Join-Path $project 'assets\jesty_thor_background_loop.mp4'
foreach ($path in @($FfmpegPath, $background, $backgroundOff)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing asset dependency: $path" }
}

Copy-Item -LiteralPath $background -Destination $static -Force
Copy-Item -LiteralPath $backgroundOff -Destination $staticOff -Force

& $FfmpegPath -loglevel error -y -loop 1 -framerate 30 -i $background `
    -loop 1 -framerate 30 -i $backgroundOff `
    -filter_complex "[0:v]crop=trunc(iw/2)*2:trunc(ih/2)*2[on];[1:v]crop=trunc(iw/2)*2:trunc(ih/2)*2[off];[on][off]blend=all_expr='if(between(T,2.85,2.94)+between(T,3.03,3.10)+between(T,6.25,6.48),B,A)',format=yuv420p[out]" `
    -map '[out]' -t 8 -r 30 -an `
    -c:v libx264 -preset medium -crf 23 -pix_fmt yuv420p -movflags +faststart $video
if ($LASTEXITCODE -ne 0) { throw 'Animated background generation failed' }

Copy-Item -LiteralPath $video -Destination (Join-Path $project 'apk\res\raw\jesty_thor_background_loop.mp4') -Force
Get-Item -LiteralPath $static, $staticOff, $video | Select-Object Name, Length
