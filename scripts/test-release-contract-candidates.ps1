[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$sandbox = [IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetTempPath()) ('jesty-release-contract-' + [guid]::NewGuid().ToString('N'))))
$files = @(
    'scripts/test-current-release-contract.ps1', 'apk/AndroidManifest.xml',
    'src/com/thor/displaypowertest/DaemonIdentity.java', 'README.md',
    'docs/RELEASE-INTEGRITY.md', 'docs/VALIDATION-EVIDENCE.md',
    'docs/COMPATIBILITY.md', 'docs/RELEASE-READINESS-1.7.1.md'
)
$checks = 0

function Test-Scenario {
    param([string] $Name, [string] $Version = '1.7.1', [string] $Code = '76',
        [string] $Identity = '', [string] $Role = '', [scriptblock] $Mutate, [string] $ExpectedError = '')
    $caseRoot = Join-Path $sandbox $Name
    foreach ($relative in $files) {
        $target = Join-Path $caseRoot $relative
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path $repository $relative) -Destination $target
    }
    # Synthetic values characterize the contract; they do not reserve release identities.
    $manifestPath = Join-Path $caseRoot 'apk/AndroidManifest.xml'
    $manifest = Get-Content -LiteralPath $manifestPath -Raw
    $manifest = [regex]::Replace($manifest, 'android:versionName="[^"]+"', ('android:versionName="' + $Version + '"'))
    $manifest = [regex]::Replace($manifest, 'android:versionCode="[^"]+"', ('android:versionCode="' + $Code + '"'))
    Set-Content -LiteralPath $manifestPath -Value $manifest -Encoding utf8
    if (-not $Identity) { $Identity = $Version }
    $identityPath = Join-Path $caseRoot 'src/com/thor/displaypowertest/DaemonIdentity.java'
    $identityText = Get-Content -LiteralPath $identityPath -Raw
    $identityText = [regex]::Replace($identityText, 'String VERSION = "[^"]+"', ('String VERSION = "' + $Identity + '"'))
    Set-Content -LiteralPath $identityPath -Value $identityText -Encoding utf8
    if ($Role) {
        @{ role = $Role; versionName = $Version; versionCode = [int] $Code } |
            ConvertTo-Json | Set-Content -LiteralPath (Join-Path $caseRoot 'release-source-intent.json') -Encoding utf8
    }
    if ($Mutate) { & $Mutate $caseRoot }
    $failure = ''
    try { & (Join-Path $caseRoot 'scripts/test-current-release-contract.ps1') *> $null }
    catch { $failure = $_.Exception.Message }
    if ($ExpectedError) {
        if (-not $failure -or -not $failure.Contains($ExpectedError)) {
            throw "Scenario $Name did not reject for '$ExpectedError'; actual='$failure'"
        }
    } elseif ($failure) { throw "Scenario $Name should pass: $failure" }
    $script:checks++
    Write-Host "PASS release-contract $Name"
}

try {
    Test-Scenario 'published-baseline'
    Test-Scenario 'proposed-candidate' -Version '1.7.2-rc1' -Code '78' -Role 'candidate'
    Test-Scenario 'proposed-recovery' -Version '1.7.2-recovery' -Code '79' -Role 'recovery'
    Test-Scenario 'proposed-stable' -Version '1.7.2' -Code '80' -Role 'stable'
    Test-Scenario 'future-source-without-publication' -Version '1.7.2' -Code '77'
    Test-Scenario 'forward-recovery-source' -Version '1.7.2' -Code '78'
    Test-Scenario 'same-version-higher-code' -Code '77' -ExpectedError 'Source identity must match the published baseline or advance both version and code'
    Test-Scenario 'future-same-code' -Version '1.7.2' -ExpectedError 'Source identity must match the published baseline or advance both version and code'
    Test-Scenario 'future-lower-code' -Version '1.7.2' -Code '75' -ExpectedError 'Source identity must match the published baseline or advance both version and code'
    Test-Scenario 'older-version-higher-code' -Version '1.7.0' -Code '77' -ExpectedError 'Source identity must match the published baseline or advance both version and code'
    Test-Scenario 'invalid-version' -Version 'invalid' -Code '77' -ExpectedError 'Expected a numeric current app manifest versionName and versionCode'
    Test-Scenario 'daemon-identity-mismatch' -Version '1.7.2' -Code '77' -Identity '1.7.1' -ExpectedError 'Manifest and daemon identities must remain synchronized'
    Test-Scenario 'runtime-alias-drift' -Version '1.7.2' -Code '77' -Mutate {
        param($caseRoot)
        $path = Join-Path $caseRoot 'src/com/thor/displaypowertest/DaemonIdentity.java'
        (Get-Content -LiteralPath $path -Raw).Replace('String RUNTIME_ID = VERSION;', 'String RUNTIME_ID = "1.7.1";') | Set-Content -LiteralPath $path -Encoding utf8
    } -ExpectedError 'Manifest and daemon identities must remain synchronized'
    Test-Scenario 'wrong-package' -Version '1.7.2' -Code '77' -Mutate {
        param($caseRoot)
        $path = Join-Path $caseRoot 'apk/AndroidManifest.xml'
        (Get-Content -LiteralPath $path -Raw).Replace('package="com.thor.displaypowertest"', 'package="invalid.package"') | Set-Content -LiteralPath $path -Encoding utf8
    } -ExpectedError 'Package identity, reviewed SDK-25 baseline or Android installer permission drifted'
    Test-Scenario 'exported-receiver' -Version '1.7.2' -Code '77' -Mutate {
        param($caseRoot)
        $path = Join-Path $caseRoot 'apk/AndroidManifest.xml'
        $text = Get-Content -LiteralPath $path -Raw
        $text = [regex]::Replace($text, '(android:name="com\.thor\.displaypowertest\.UpdateInstallReceiver"\s+android:exported=")false', '${1}true')
        Set-Content -LiteralPath $path -Value $text -Encoding utf8
    } -ExpectedError 'Installer status receiver and root-handoff service must remain non-exported'
    Test-Scenario 'sdk-drift' -Version '1.7.2' -Code '77' -Mutate {
        param($caseRoot)
        $path = Join-Path $caseRoot 'apk/AndroidManifest.xml'
        (Get-Content -LiteralPath $path -Raw).Replace('android:targetSdkVersion="25"', 'android:targetSdkVersion="33"') | Set-Content -LiteralPath $path -Encoding utf8
    } -ExpectedError 'Package identity, reviewed SDK-25 baseline or Android installer permission drifted'
    Test-Scenario 'missing-installer-permission' -Version '1.7.2' -Code '77' -Mutate {
        param($caseRoot)
        $path = Join-Path $caseRoot 'apk/AndroidManifest.xml'
        (Get-Content -LiteralPath $path -Raw).Replace('android.permission.REQUEST_INSTALL_PACKAGES', 'invalid.permission') | Set-Content -LiteralPath $path -Encoding utf8
    } -ExpectedError 'Package identity, reviewed SDK-25 baseline or Android installer permission drifted'
    Test-Scenario 'missing-published-link' -Version '1.7.2' -Code '77' -Mutate {
        param($caseRoot)
        $path = Join-Path $caseRoot 'README.md'
        (Get-Content -LiteralPath $path -Raw).Replace('/releases/tag/v1.7.4', '/releases/tag/v1.7.3') | Set-Content -LiteralPath $path -Encoding utf8
    } -ExpectedError 'README must point to public v1.7.4 while integrity/evidence retain the pinned historical v1.7.1'
    Test-Scenario 'missing-published-digest' -Version '1.7.2' -Code '77' -Mutate {
        param($caseRoot)
        $path = Join-Path $caseRoot 'docs/RELEASE-INTEGRITY.md'
        $text = [regex]::Replace((Get-Content -LiteralPath $path -Raw), '(?m)^- Release asset:.*\r?\n', '')
        Set-Content -LiteralPath $path -Value $text -Encoding utf8
    } -ExpectedError 'Published v1.7.1 integrity must distinguish digest/source'
    Test-Scenario 'wrong-published-source' -Version '1.7.2' -Code '77' -Mutate {
        param($caseRoot)
        $path = Join-Path $caseRoot 'docs/RELEASE-INTEGRITY.md'
        (Get-Content -LiteralPath $path -Raw).Replace('f929ff910bfa8e5179a2c6a63948a18fc3d6b252', ('0' * 40)) | Set-Content -LiteralPath $path -Encoding utf8
    } -ExpectedError 'Published v1.7.1 integrity must distinguish digest/source'
    Test-Scenario 'lost-guard-caveat' -Version '1.7.2' -Code '77' -Mutate {
        param($caseRoot)
        $path = Join-Path $caseRoot 'docs/RELEASE-INTEGRITY.md'
        (Get-Content -LiteralPath $path -Raw).Replace('NOT OBSERVED', 'PASS') | Set-Content -LiteralPath $path -Encoding utf8
    } -ExpectedError 'Published v1.7.1 integrity must distinguish digest/source'
    Test-Scenario 'candidate-no-intent' -Version '1.7.2-rc1' -Code '78' -ExpectedError 'Suffixed source identity requires explicit release-source-intent.json'
    Test-Scenario 'recovery-no-intent' -Version '1.7.2-recovery' -Code '79' -ExpectedError 'Suffixed source identity requires explicit release-source-intent.json'
    Test-Scenario 'candidate-wrong-role' -Version '1.7.2-rc1' -Code '78' -Role 'recovery' -ExpectedError 'Source intent role does not match the complete versionName'
    Test-Scenario 'recovery-wrong-role' -Version '1.7.2-recovery' -Code '79' -Role 'candidate' -ExpectedError 'Source intent role does not match the complete versionName'
    Test-Scenario 'suffix-with-stable-role' -Version '1.7.2-rc1' -Code '78' -Role 'stable' -ExpectedError 'Source intent role does not match the complete versionName'
    Test-Scenario 'numeric-with-candidate-role' -Version '1.7.2' -Code '80' -Role 'candidate' -ExpectedError 'Source intent role does not match the complete versionName'
    Test-Scenario 'candidate-same-code' -Version '1.7.2-rc1' -Code '76' -Role 'candidate' -ExpectedError 'Source identity must match the published baseline or advance both version and code'
    Test-Scenario 'recovery-lower-code' -Version '1.7.2-recovery' -Code '75' -Role 'recovery' -ExpectedError 'Source identity must match the published baseline or advance both version and code'
    Test-Scenario 'old-candidate-base' -Version '1.7.1-rc1' -Code '78' -Role 'candidate' -ExpectedError 'Source identity must match the published baseline or advance both version and code'
    Test-Scenario 'truncated-daemon-suffix' -Version '1.7.2-rc1' -Code '78' -Identity '1.7.2' -Role 'candidate' -ExpectedError 'Manifest and daemon identities must remain synchronized'
    Test-Scenario 'wrong-daemon-suffix' -Version '1.7.2-recovery' -Code '79' -Identity '1.7.2-rc1' -Role 'recovery' -ExpectedError 'Manifest and daemon identities must remain synchronized'
    Test-Scenario 'intent-version-mismatch' -Version '1.7.2-rc1' -Code '78' -Role 'candidate' -Mutate {
        param($caseRoot)
        @{ role = 'candidate'; versionName = '1.7.2'; versionCode = 78 } | ConvertTo-Json |
            Set-Content -LiteralPath (Join-Path $caseRoot 'release-source-intent.json') -Encoding utf8
    } -ExpectedError 'Source intent must exactly match manifest versionName and versionCode'
    Test-Scenario 'intent-code-mismatch' -Version '1.7.2-recovery' -Code '79' -Role 'recovery' -Mutate {
        param($caseRoot)
        @{ role = 'recovery'; versionName = '1.7.2-recovery'; versionCode = 78 } | ConvertTo-Json |
            Set-Content -LiteralPath (Join-Path $caseRoot 'release-source-intent.json') -Encoding utf8
    } -ExpectedError 'Source intent must exactly match manifest versionName and versionCode'
    Test-Scenario 'unknown-intent-role' -Version '1.7.2-rc1' -Code '78' -Role 'preview' -ExpectedError 'Source intent role does not match the complete versionName'
    Test-Scenario 'intent-extra-field' -Version '1.7.2-rc1' -Code '78' -Role 'candidate' -Mutate {
        param($caseRoot)
        @{ role = 'candidate'; versionName = '1.7.2-rc1'; versionCode = 78; bypass = $true } | ConvertTo-Json |
            Set-Content -LiteralPath (Join-Path $caseRoot 'release-source-intent.json') -Encoding utf8
    } -ExpectedError 'Source intent must be an object with only role, versionName and integer versionCode'
    Test-Scenario 'intent-string-code' -Version '1.7.2-rc1' -Code '78' -Role 'candidate' -Mutate {
        param($caseRoot)
        '{"role":"candidate","versionName":"1.7.2-rc1","versionCode":"78"}' |
            Set-Content -LiteralPath (Join-Path $caseRoot 'release-source-intent.json') -Encoding utf8
    } -ExpectedError 'Source intent must be an object with only role, versionName and integer versionCode'
    Test-Scenario 'intent-invalid-json' -Version '1.7.2-rc1' -Code '78' -Role 'candidate' -Mutate {
        param($caseRoot)
        '{' | Set-Content -LiteralPath (Join-Path $caseRoot 'release-source-intent.json') -Encoding utf8
    } -ExpectedError 'Source intent must be valid JSON'
    Test-Scenario 'unreviewed-suffix' -Version '1.7.2-preview' -Code '78' -Role 'candidate' -ExpectedError 'Expected a numeric current app manifest versionName and versionCode'
    Test-Scenario 'suffix-case-drift' -Version '1.7.2-RC1' -Code '78' -Role 'candidate' -ExpectedError 'Expected a numeric current app manifest versionName and versionCode'
    Test-Scenario 'intent-single-object-array' -Version '1.7.2-rc1' -Code '78' -Role 'candidate' -Mutate {
        param($caseRoot)
        '[{"role":"candidate","versionName":"1.7.2-rc1","versionCode":78}]' |
            Set-Content -LiteralPath (Join-Path $caseRoot 'release-source-intent.json') -Encoding utf8
    } -ExpectedError 'Source intent must be an object with only role, versionName and integer versionCode'
    Test-Scenario 'intent-null' -Version '1.7.2-rc1' -Code '78' -Role 'candidate' -Mutate {
        param($caseRoot)
        'null' | Set-Content -LiteralPath (Join-Path $caseRoot 'release-source-intent.json') -Encoding utf8
    } -ExpectedError 'Source intent must be an object with only role, versionName and integer versionCode'
    Test-Scenario 'intent-missing-role' -Version '1.7.2-rc1' -Code '78' -Role 'candidate' -Mutate {
        param($caseRoot)
        '{"versionName":"1.7.2-rc1","versionCode":78}' |
            Set-Content -LiteralPath (Join-Path $caseRoot 'release-source-intent.json') -Encoding utf8
    } -ExpectedError 'Source intent must be an object with only role, versionName and integer versionCode'
    Test-Scenario 'intent-floating-code' -Version '1.7.2-rc1' -Code '78' -Role 'candidate' -Mutate {
        param($caseRoot)
        '{"role":"candidate","versionName":"1.7.2-rc1","versionCode":78.0}' |
            Set-Content -LiteralPath (Join-Path $caseRoot 'release-source-intent.json') -Encoding utf8
    } -ExpectedError 'Source intent must be an object with only role, versionName and integer versionCode'
    Test-Scenario 'unreviewed-rc-suffix' -Version '1.7.2-rc2' -Code '78' -Role 'candidate' -ExpectedError 'Expected a numeric current app manifest versionName and versionCode'
    Write-Host "Release contract candidate regression passed: $checks scenarios; source fixtures only, no build/sign/device."
} finally {
    if (Test-Path -LiteralPath $sandbox) {
        $resolved = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $sandbox).Path)
        if ($resolved -ne $sandbox -or (Get-Item -LiteralPath $sandbox).Attributes -band [IO.FileAttributes]::ReparsePoint -or
            @(Get-ChildItem -LiteralPath $sandbox -Recurse -Force | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count -ne 0) {
            throw 'Refusing unexpected/reparse release-contract sandbox cleanup.'
        }
        Remove-Item -LiteralPath $sandbox -Recurse -Force
    }
}
