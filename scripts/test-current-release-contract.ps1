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
$identity = Get-Content -LiteralPath (Join-Path $root 'src\com\thor\displaypowertest\DaemonIdentity.java') -Raw
# Published provenance is an independently reviewed historical baseline. A
# newer source/candidate does not create a publication record for itself.
$publishedVersion = '1.7.1'
$publishedCode = 76
$publishedSource = 'f929ff910bfa8e5179a2c6a63948a18fc3d6b252'
# The historical code76 publication evidence above remains pinned independently
# of the latest public download advertised in the README.
$latestPublicVersion = '1.7.3'

$versionMatches = [regex]::Matches($manifest, 'android:versionName="([^"]+)"')
$codeMatches = [regex]::Matches($manifest, 'android:versionCode="([0-9]+)"')
if ($versionMatches.Count -ne 1 -or $codeMatches.Count -ne 1) {
    throw 'Expected a numeric current app manifest versionName and versionCode.'
}
$version = $versionMatches[0].Groups[1].Value
$code = $codeMatches[0].Groups[1].Value
# Keep the complete identity; only the reviewed suffixes are admitted, with
# separately declared source intent. This is not a general SemVer parser.
$versionParts = [regex]::Match($version, '^([0-9]+\.[0-9]+\.[0-9]+)(-rc1|-recovery)?$')
if (-not $versionParts.Success) {
    throw 'Expected a numeric current app manifest versionName and versionCode; only reviewed -rc1/-recovery suffixes are supported.'
}
try {
    $numericVersion = [version] $versionParts.Groups[1].Value
    $numericCode = [int] $code
} catch { throw 'Expected a numeric current app manifest versionName and versionCode.' }
$matchesPublished = $version -eq $publishedVersion -and $numericCode -eq $publishedCode
if (-not $matchesPublished -and
    ($numericVersion -le [version] $publishedVersion -or $numericCode -le $publishedCode)) {
    throw 'Source identity must match the published baseline or advance both version and code.'
}
$intentPath = Join-Path $root 'release-source-intent.json'
$suffix = $versionParts.Groups[2].Value
if (Test-Path -LiteralPath $intentPath) {
    try {
        $intentJson = (Get-Content -LiteralPath $intentPath -Raw).Trim()
        $intent = $intentJson | ConvertFrom-Json
    }
    catch { throw 'Source intent must be valid JSON.' }
    # ConvertFrom-Json can enumerate a one-element array as its sole object.
    if (-not $intentJson.StartsWith('{') -or $intent -isnot [pscustomobject] -or
        (($intent.PSObject.Properties.Name | Sort-Object) -join ',') -cne 'role,versionCode,versionName' -or
        $intent.role -isnot [string] -or $intent.versionName -isnot [string] -or
        ($intent.versionCode -isnot [int] -and $intent.versionCode -isnot [long])) {
        throw 'Source intent must be an object with only role, versionName and integer versionCode.'
    }
    if ($intent.versionName -cne $version -or $intent.versionCode -ne $numericCode) {
        throw 'Source intent must exactly match manifest versionName and versionCode.'
    }
    $expectedRole = if ($suffix -ceq '-rc1') { 'candidate' } elseif ($suffix -ceq '-recovery') { 'recovery' } else { 'stable' }
    if ($intent.role -cne $expectedRole) {
        throw 'Source intent role does not match the complete versionName.'
    }
} elseif ($suffix) {
    throw 'Suffixed source identity requires explicit release-source-intent.json.'
}
$daemonVersion = [regex]::Matches($identity, '(?m)^[ \t]*public static final String VERSION = "([^"]+)";[ \t]*\r?$')
$runtimeAlias = [regex]::Matches($identity, '(?m)^[ \t]*public static final String RUNTIME_ID = VERSION;[ \t]*\r?$')
if ($daemonVersion.Count -ne 1 -or $daemonVersion[0].Groups[1].Value -cne $version -or $runtimeAlias.Count -ne 1) {
    throw 'Manifest and daemon identities must remain synchronized.'
}
$releaseTag = '/releases/tag/v' + $publishedVersion
$latestPublicReleaseTag = '/releases/tag/v' + $latestPublicVersion
if ($manifest -notmatch 'package="com\.thor\.displaypowertest"' -or
    $manifest -notmatch 'android:targetSdkVersion="25"' -or
    $manifest -notmatch 'android:name="android\.permission\.REQUEST_INSTALL_PACKAGES"') {
    throw 'Package identity, reviewed SDK-25 baseline or Android installer permission drifted; review migration separately.'
}
if ($manifest -notmatch '(?s)<receiver\s+android:name="com\.thor\.displaypowertest\.UpdateInstallReceiver"\s+android:exported="false"\s*/>' -or
    $manifest -notmatch '(?s)<service\s+android:name="com\.thor\.displaypowertest\.AutoService"\s+android:exported="false"\s*/>') {
    throw 'Installer status receiver and root-handoff service must remain non-exported.'
}
if (-not $readme.Contains($latestPublicReleaseTag) -or
    -not $readme.Contains('Download v' + $latestPublicVersion) -or
    -not $integrity.Contains($releaseTag) -or
    -not $evidence.Contains($releaseTag)) {
    throw "README must point to public v$latestPublicVersion while integrity/evidence retain the pinned historical v$publishedVersion."
}
# Verify the pinned publication section, independently of candidate identity.
$integrityHeaderPattern = '(?m)^## ' + [regex]::Escape($publishedVersion) + ' stable publication\b[^\r\n]*'
$integrityHeading = [regex]::Match($integrity, $integrityHeaderPattern)
if (-not $integrityHeading.Success) {
    throw "Release integrity lacks the pinned stable-publication section for v$publishedVersion."
}
$afterHeading = $integrity.Substring($integrityHeading.Index + $integrityHeading.Length)
$nextHeading = [regex]::Match($afterHeading, '(?m)^## ')
$currentRelease = if ($nextHeading.Success) {
    $afterHeading.Substring(0, $nextHeading.Index)
} else { $afterHeading }
if ($currentRelease -notmatch ('versionCode\s+[^0-9\r\n]*' + $publishedCode + '\b') -or
    $currentRelease -notmatch '(?im)^- Release asset:.*SHA-256.*[0-9a-f]{64}' -or
    -not $currentRelease.Contains($publishedSource) -or
    $currentRelease -notmatch '(?i)NOT OBSERVED' -or
    $currentRelease -notmatch 'not asserted byte-identical') {
    throw "Published v$publishedVersion integrity must distinguish digest/source, separate physical candidate and Guard limits."
}
if ($readiness -notmatch 'Historical pre-publication snapshot' -or
    $readiness -notmatch 'NOT OBSERVED' -or
    $compat -notmatch '(?s)Closed-Lid Wake Guard.*?NOT OBSERVED' -or
    $compat -notmatch 'Dock / external-display use') {
    throw 'Historical readiness/compatibility must not upgrade unobserved Guard or dock cases to PASS.'
}
Write-Host "Source identity v$version/code$code and published v$publishedVersion/code$publishedCode provenance passed (targetSdk=25; static check only)."
