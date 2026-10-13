[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$manifest = Get-Content -LiteralPath (Join-Path $root 'apk\AndroidManifest.xml') -Raw
$readme = Get-Content -LiteralPath (Join-Path $root 'README.md') -Raw
$integrity = Get-Content -LiteralPath (Join-Path $root 'docs\RELEASE-INTEGRITY.md') -Raw
$evidence = Get-Content -LiteralPath (Join-Path $root 'docs\VALIDATION-EVIDENCE.md') -Raw
$compat = Get-Content -LiteralPath (Join-Path $root 'docs\COMPATIBILITY.md') -Raw
$readiness = Get-Content -LiteralPath (Join-Path $root 'docs\RELEASE-READINESS-1.7.1.md') -Raw

$versionMatch = [regex]::Match($manifest, 'android:versionName="([0-9]+\.[0-9]+\.[0-9]+)"')
$codeMatch = [regex]::Match($manifest, 'android:versionCode="([0-9]+)"')
if (-not $versionMatch.Success -or -not $codeMatch.Success) {
    throw 'Expected a numeric current app manifest versionName and versionCode.'
}
$version = $versionMatch.Groups[1].Value
$code = $codeMatch.Groups[1].Value
$releaseTag = '/releases/tag/v' + $version
if ($manifest -notmatch 'package="com\.thor\.displaypowertest"' -or
    $manifest -notmatch 'android:targetSdkVersion="25"' -or
    $manifest -notmatch 'android:name="android\.permission\.REQUEST_INSTALL_PACKAGES"') {
    throw 'Package identity, reviewed SDK-25 baseline or Android installer permission drifted; review migration separately.'
}
if ($manifest -notmatch '(?s)<receiver\s+android:name="com\.thor\.displaypowertest\.UpdateInstallReceiver"\s+android:exported="false"\s*/>' -or
    $manifest -notmatch '(?s)<service\s+android:name="com\.thor\.displaypowertest\.AutoService"\s+android:exported="false"\s*/>') {
    throw 'Installer status receiver and root-handoff service must remain non-exported.'
}
if (-not $readme.Contains($releaseTag) -or
    -not $integrity.Contains($releaseTag) -or
    -not $evidence.Contains($releaseTag)) {
    throw "README/release integrity/evidence index must all link current v$version."
}
# Only constrain the current release section; historical 1.7.0 and earlier
# measurements deliberately stay untouched.
$integrityHeaderPattern = '(?m)^## ' + [regex]::Escape($version) + ' stable publication\b[^\r\n]*'
$integrityHeading = [regex]::Match($integrity, $integrityHeaderPattern)
if (-not $integrityHeading.Success) {
    throw "Release integrity lacks a current stable-publication section for v$version."
}
$afterHeading = $integrity.Substring($integrityHeading.Index + $integrityHeading.Length)
$nextHeading = [regex]::Match($afterHeading, '(?m)^## ')
$currentRelease = if ($nextHeading.Success) {
    $afterHeading.Substring(0, $nextHeading.Index)
} else { $afterHeading }
if ($currentRelease -notmatch ('versionCode\s+[^0-9\r\n]*' + [regex]::Escape($code) + '\b') -or
    $currentRelease -notmatch '(?im)^- Release asset:.*SHA-256.*[0-9a-f]{64}' -or
    $currentRelease -notmatch 'f929ff910bfa8e5179a2c6a63948a18fc3d6b252' -or
    $currentRelease -notmatch '(?i)NOT OBSERVED' -or
    $currentRelease -notmatch 'not asserted byte-identical') {
    throw "Current v$version integrity must distinguish digest/source, separate physical candidate and Guard limits."
}
if ($readiness -notmatch 'Historical pre-publication snapshot' -or
    $readiness -notmatch 'NOT OBSERVED' -or
    $compat -notmatch '(?s)Closed-Lid Wake Guard.*?NOT OBSERVED' -or
    $compat -notmatch 'Dock / external-display use') {
    throw 'Historical readiness/compatibility must not upgrade unobserved Guard or dock cases to PASS.'
}
Write-Host "Current source/release provenance contract passed (v$version, code $code, targetSdk=25; static check only)."
