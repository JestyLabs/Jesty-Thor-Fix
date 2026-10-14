[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$stableSource = 'f929ff910bfa8e5179a2c6a63948a18fc3d6b252'

function Read-Stable([string] $Path) {
    $lines = & git -C $root show "${stableSource}:$Path"
    if ($LASTEXITCODE -ne 0) { throw "Pinned stable source unavailable: $Path" }
    return ($lines -join "`n").TrimEnd("`n")
}
function Read-Source([string] $Path) {
    return [IO.File]::ReadAllText((Join-Path $root $Path)).Replace("`r`n", "`n").TrimEnd("`n")
}

# This independent recovery permits no candidate product changes. Its CI
# adaptation only checks source identity and obtains the pinned ancestor.
$allowed = @('apk/AndroidManifest.xml', 'src/com/thor/displaypowertest/DaemonIdentity.java',
    'build.ps1', 'release-source-intent.json', 'scripts/test-recovery-source-contract.ps1',
    '.github/workflows/host-tests.yml')
$changed = @(& git -C $root diff --name-only $stableSource --)
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect stable-source differences.' }
$untracked = @(& git -C $root ls-files --others --exclude-standard)
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect untracked source files.' }
foreach ($path in @($changed + $untracked)) {
    if ($allowed -cnotcontains $path) { throw "Recovery has an unapproved source change: $path" }
}

$manifest = Read-Source 'apk/AndroidManifest.xml'
$identity = Read-Source 'src/com/thor/displaypowertest/DaemonIdentity.java'
$build = Read-Source 'build.ps1'
if ($manifest.Replace('android:versionName="1.7.2-recovery"', 'android:versionName="1.7.1"').Replace('android:versionCode="79"', 'android:versionCode="76"') -cne (Read-Stable 'apk/AndroidManifest.xml') -or
    -not $manifest.Contains('android:versionName="1.7.2-recovery"') -or -not $manifest.Contains('android:versionCode="79"')) {
    throw 'Recovery manifest must change only approved versionName/code metadata.'
}
if ($identity.Replace('String VERSION = "1.7.2-recovery"', 'String VERSION = "1.7.1"') -cne (Read-Stable 'src/com/thor/displaypowertest/DaemonIdentity.java') -or
    -not $identity.Contains('String VERSION = "1.7.2-recovery"')) {
    throw 'Recovery daemon identity must preserve stable functionality and full suffix.'
}
if ($build.Replace("ArtifactBaseName = 'Jesty-Thor-Fix-1.7.2-recovery'", "ArtifactBaseName = 'Jesty-Thor-Fix-1.7.1'") -cne (Read-Stable 'build.ps1') -or
    -not $build.Contains("ArtifactBaseName = 'Jesty-Thor-Fix-1.7.2-recovery'")) {
    throw 'Recovery build must change only the approved artifact basename.'
}

$intentJson = Read-Source 'release-source-intent.json'
try { $intent = $intentJson | ConvertFrom-Json }
catch { throw 'Recovery intent must be valid JSON.' }
if (-not $intentJson.TrimStart().StartsWith('{') -or $intent -isnot [pscustomobject] -or
    (($intent.PSObject.Properties.Name | Sort-Object) -join ',') -cne 'role,versionCode,versionName' -or
    $intent.role -cne 'recovery' -or $intent.versionName -cne '1.7.2-recovery' -or
    ($intent.versionCode -isnot [int] -and $intent.versionCode -isnot [long]) -or $intent.versionCode -ne 79) {
    throw 'Recovery intent must exactly declare recovery/1.7.2-recovery/code79.'
}

$expectedWorkflow = Read-Stable '.github/workflows/host-tests.yml'
$expectedWorkflow = [regex]::new('(?m)^          persist-credentials: false$').Replace($expectedWorkflow,
    "          persist-credentials: false`n          fetch-depth: 0", 1)
$expectedWorkflow = $expectedWorkflow.Replace('      - name: Verify public repository text',
    "      - name: Verify recovery source contract`n        shell: pwsh`n        run: ./scripts/test-recovery-source-contract.ps1`n`n      - name: Verify public repository text")
if ((Read-Source '.github/workflows/host-tests.yml') -cne $expectedWorkflow) {
    throw 'Recovery CI must preserve original signing/publication policy and only add the source contract.'
}
Write-Host 'Recovery source contract passed: stable functional source, approved recovery79 identity and isolated unsigned CI adaptation (no device/signing proof).'
