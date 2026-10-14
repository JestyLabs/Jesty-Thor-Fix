[CmdletBinding()]
param([string] $ApprovedArchiveDirectory = '', [string] $TestSignedApk = '')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$workflow = Get-Content -LiteralPath (Join-Path $root '.github/workflows/sign-approved-physical-pair.yml') -Raw
$legacy = Get-Content -LiteralPath (Join-Path $root '.github/workflows/sign-test-candidate.yml') -Raw
$sandbox = [IO.Path]::GetFullPath((Join-Path ([IO.Path]::GetTempPath()) ('jesty-signing-policy-' + [guid]::NewGuid().ToString('N'))))
$savedExitCode = $global:LASTEXITCODE
$savedEnv = @{}
$checks = 0
function Set-TestEnv([string] $Name, [string] $Value) {
    if (-not $script:savedEnv.ContainsKey($Name)) { $script:savedEnv[$Name] = [Environment]::GetEnvironmentVariable($Name) }
    [Environment]::SetEnvironmentVariable($Name,$Value)
}
function Block([string] $Name) {
    $lines = $workflow -split '\r?\n'
    $start = [Array]::IndexOf($lines, "      - name: $Name")
    if ($start -lt 0) { throw "Missing actual step: $Name" }
    while ($lines[$start] -cne '        run: |') { $start++; if ($start -ge $lines.Length) { throw 'Missing run block.' } }
    $body = @()
    for ($i=$start+1; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match '^      - ') { break }
        if ($lines[$i] -match '^          ') { $body += $lines[$i].Substring(10) }
        elseif (-not $lines[$i].Trim()) { $body += '' }
        else { break }
    }
    return $body -join "`n"
}
function Check([string] $Name, [scriptblock] $Action, [string] $Reject = '') {
    $errorText = ''
    try { & $Action *> $null } catch { $errorText = $_.Exception.Message }
    if ($Reject) { if (-not $errorText.Contains($Reject)) { throw "$Name did not reject specifically: $errorText" } }
    elseif ($errorText) { throw "$Name failed: $errorText" }
    $script:checks++; Write-Host "PASS $Name"
}
function Reset-Source([string] $Role) {
    $script:role = $Role
    $candidate = $Role -ceq 'candidate'
    $script:runId = if ($candidate) { 38596116146 } else { 38596899951 }
    $head = if ($candidate) { '8d2935736080567c6c538035d8925519a083ac41' } else { '8746ea1fbdfe122c2a54064a9b019d590a3968a7' }
    $producer = if ($candidate) { '5661b3a56402060b5b6eb82600307db971680a79' } else { $head }
    $branch = if ($candidate) { 'work/engineering-hardening-post-rc3-20261012' } else { 'recovery/pr67-stable-code79-20261014' }
    $version = if ($candidate) { '1.7.2-rc1' } else { '1.7.2-recovery' }
    $script:source = [ordered]@{ id=$runId; run_attempt=1; event=$(if ($candidate) {'pull_request'}else{'workflow_dispatch'});
        path='.github/workflows/host-tests.yml'; name='CI'; status='completed'; conclusion='success'; head_sha=$head; head_branch=$branch;
        repository=@{full_name='JestyLabs/Jesty-Thor-Fix'; id=1389567045}; head_repository=@{full_name='JestyLabs/Jesty-Thor-Fix'; id=1389567045} }
    $script:artifact = [ordered]@{id=$(if($candidate){11909283765}else{11909164693}); name="unsigned-build-v$version-$producer"; expired=$false;
        digest=$(if($candidate){'sha256:1dd0c37ee1b8104694a27e13f12f13213cd77c1dd922e0a5b703a184a3bfcf1b'}else{'sha256:f76f725490e9291678a0508c1e403d0965a575f24be04191ccbe6b7eb235d2c3'});
        workflow_run=@{id=$runId;head_sha=$head;head_branch=$branch;repository_id=1389567045;head_repository_id=1389567045} }
    $script:total = 1; $script:extraArtifact = $false; $script:lookupFailure = $false
    Set-TestEnv 'APPROVED_ROLE' $Role
    Set-TestEnv 'GITHUB_ENV' (Join-Path $sandbox 'gate.env')
    [IO.File]::WriteAllText($env:GITHUB_ENV,'')
}
function gh([string] $Command, [string] $Endpoint) {
    if ($Command -cne 'api') { throw 'Unexpected mock command.' }
    $global:LASTEXITCODE = if ($script:lookupFailure) { 1 } else { 0 }
    if ($Endpoint -ceq "repos/JestyLabs/Jesty-Thor-Fix/actions/runs/$script:runId") { return $script:source | ConvertTo-Json -Depth 8 -Compress }
    if ($Endpoint -ceq "repos/JestyLabs/Jesty-Thor-Fix/actions/runs/$script:runId/artifacts?per_page=100") {
        $items=@($script:artifact); if($script:extraArtifact){$items+=@{id=1}}
        return @{total_count=$script:total;artifacts=$items}|ConvertTo-Json -Depth 8 -Compress
    }
    throw 'Unexpected source lookup; frozen identifier changed.'
}
try {
    New-Item -ItemType Directory -Path $sandbox | Out-Null
    $caller = [scriptblock]::Create((Block 'Authorize protected caller'))
    $sourceGate = [scriptblock]::Create((Block 'Validate frozen source and artefact'))
    $download = Block 'Download and inspect exact unsigned input'
    $verify = Block 'Verify certificate identity and functional payload'
    foreach ($text in @($workflow)) {
        if ($text -notmatch 'environment: thor-signing' -or $text -notmatch "github.ref == 'refs/heads/main'" -or
            $text -notmatch "github.actor == 'SirJesty' && github.triggering_actor == 'SirJesty'" -or
            $text -notmatch 'group: thor-release-keystore-' -or $text -notmatch 'cancel-in-progress: false' -or
            $text -match '(?m)^\s*contents: write' -or $text -match 'pull_request_target:|workflow_call:') { throw 'Protected caller policy widened.' }
    }
    if ($legacy -notmatch '\$run.event -ne ''pull_request''') { throw 'Legacy pull-request-only restriction changed.' }
    if ($workflow.IndexOf('Download and inspect exact unsigned input') -gt $workflow.IndexOf('Prepare protected signing material') -or
        $workflow.Substring(0,$workflow.IndexOf('Prepare protected signing material')).Contains('secrets.')) { throw 'Secret exposure precedes frozen input gates.' }
    $checks++
    foreach ($pair in @{ORIGINAL_ACTOR='SirJesty';TRIGGERING_ACTOR='SirJesty';CALLER_REF='refs/heads/main';CALLER_REPOSITORY='JestyLabs/Jesty-Thor-Fix';CALLER_EVENT='workflow_dispatch';SIGNING_ENV_READY='true'}.GetEnumerator()) { Set-TestEnv $pair.Key $pair.Value }
    Check 'protected-caller' { & $caller }
    foreach ($field in @('ORIGINAL_ACTOR','TRIGGERING_ACTOR','CALLER_REF','CALLER_REPOSITORY','CALLER_EVENT','SIGNING_ENV_READY')) {
        $before=[Environment]::GetEnvironmentVariable($field); Set-TestEnv $field 'unapproved'
        Check "caller-reject-$field" { & $caller } 'Protected caller'
        Set-TestEnv $field $before
    }
    foreach ($candidateRole in @('candidate','recovery')) {
        Reset-Source $candidateRole
        Check "$candidateRole-approved" { & $sourceGate }
        foreach($line in Get-Content -LiteralPath $env:GITHUB_ENV){$key,$value=$line -split '=',2;Set-TestEnv $key $value}
        if ($env:APPROVED_ROLE -cne $candidateRole) { throw 'Role output drift.' }
        $mutations=@(
            @{name='different-run';edit={$script:source.id++}}, @{name='rerun-attempt';edit={$script:source.run_attempt=2}},
            @{name='different-event';edit={$script:source.event='push'}}, @{name='different-workflow';edit={$script:source.path='unapproved.yml'}},
            @{name='different-name';edit={$script:source.name='Other'}}, @{name='unfinished';edit={$script:source.status='in_progress'}},
            @{name='failed';edit={$script:source.conclusion='failure'}}, @{name='different-head';edit={$script:source.head_sha='0'*40}},
            @{name='different-branch';edit={$script:source.head_branch='unapproved'}}, @{name='foreign-repo';edit={$script:source.repository.full_name='other/repo'}},
            @{name='fork';edit={$script:source.head_repository.full_name='other/repo'}}, @{name='repo-id';edit={$script:source.repository.id=1}},
            @{name='head-repo-id';edit={$script:source.head_repository.id=1}}
        )
        foreach($mutation in $mutations){Reset-Source $candidateRole;& $mutation.edit;Check "$candidateRole-$($mutation.name)" {& $sourceGate} 'Source is not the exact'}
        $mutations=@(
            @{name='different-artifact';edit={$script:artifact.id++}}, @{name='different-artifact-name';edit={$script:artifact.name='unsigned-build-v1.7.2-'+('0'*40)}},
            @{name='expired';edit={$script:artifact.expired=$true}}, @{name='missing-expiry';edit={$script:artifact.Remove('expired')}},
            @{name='digest';edit={$script:artifact.digest='sha256:'+('0'*64)}}, @{name='artifact-run';edit={$script:artifact.workflow_run.id=1}},
            @{name='artifact-head';edit={$script:artifact.workflow_run.head_sha='0'*40}}, @{name='artifact-branch';edit={$script:artifact.workflow_run.head_branch='unapproved'}},
            @{name='artifact-repo';edit={$script:artifact.workflow_run.repository_id=1}}, @{name='artifact-fork';edit={$script:artifact.workflow_run.head_repository_id=1}}
        )
        foreach($mutation in $mutations){Reset-Source $candidateRole;& $mutation.edit;Check "$candidateRole-$($mutation.name)" {& $sourceGate} 'Artefact identity'}
        Reset-Source $candidateRole;$script:total=2;Check "$candidateRole-extra-count" {& $sourceGate} 'exactly one artefact'
        Reset-Source $candidateRole;$script:extraArtifact=$true;Check "$candidateRole-extra-item" {& $sourceGate} 'exactly one artefact'
        Reset-Source $candidateRole;$script:lookupFailure=$true;Check "$candidateRole-api-failure" {& $sourceGate} 'Source lookup failed'
    }
    Reset-Source 'recovery';Set-TestEnv 'APPROVED_ROLE' 'unknown';Check 'unknown-role' {& $sourceGate} 'Role is not'
    Set-TestEnv 'APPROVED_ROLE' 'Recovery';Check 'role-case-drift' {& $sourceGate} 'Role is not'
    $payloadFunction = $verify.Substring($verify.IndexOf('function Get-FunctionalPayload'), $verify.IndexOf('if ((Get-FileHash')-$verify.IndexOf('function Get-FunctionalPayload'))
    . ([scriptblock]::Create($payloadFunction))
    $compare = [scriptblock]::Create($verify.Substring($verify.IndexOf('$before = Get-FunctionalPayload'),$verify.IndexOf('$metadata =')-$verify.IndexOf('$before = Get-FunctionalPayload')))
    function Create-Zip([string]$Path,[hashtable]$Entries){
        $zip=[IO.Compression.ZipFile]::Open($Path,[IO.Compression.ZipArchiveMode]::Create)
        try{foreach($entry in $Entries.GetEnumerator()){$s=$zip.CreateEntry($entry.Key).Open();try{$b=[Text.Encoding]::UTF8.GetBytes($entry.Value);$s.Write($b,0,$b.Length)}finally{$s.Dispose()}}}finally{$zip.Dispose()}
    }
    Create-Zip (Join-Path $sandbox 'plain.apk') @{'classes.dex'='original';'assets/data'='preserved';'META-INF/required-notice.txt'='preserve this notice'}
    Create-Zip (Join-Path $sandbox 'signed.apk') @{'classes.dex'='original';'assets/data'='preserved';'META-INF/required-notice.txt'='preserve this notice';'META-INF/CERT.SF'='signature'}
    Set-TestEnv 'UNSIGNED_APK' (Join-Path $sandbox 'plain.apk');Set-TestEnv 'SIGNED_APK' (Join-Path $sandbox 'signed.apk')
    Check 'payload-signature-only' {& $compare}
    foreach($case in @(@{name='changed-dex';map=@{'classes.dex'='tampered';'assets/data'='preserved';'META-INF/required-notice.txt'='preserve this notice'}},
        @{name='lost-notice';map=@{'classes.dex'='original';'assets/data'='preserved'}},@{name='extra-data';map=@{'classes.dex'='original';'assets/data'='preserved';'META-INF/required-notice.txt'='preserve this notice';'new.file'='unexpected'}})){
        $path=Join-Path $sandbox ($case.name+'.apk');Create-Zip $path $case.map;Set-TestEnv 'SIGNED_APK' $path
        Check $case.name {& $compare} 'Signed functional payload'
    }
    if ($ApprovedArchiveDirectory) {
        foreach($candidateRole in @('candidate','recovery')){
            Reset-Source $candidateRole;& $sourceGate
            foreach($line in Get-Content -LiteralPath $env:GITHUB_ENV){$key,$value=$line -split '=',2;Set-TestEnv $key $value}
            $inputDir=Join-Path $sandbox $candidateRole;New-Item -ItemType Directory -Path $inputDir|Out-Null
            $archive=Join-Path $ApprovedArchiveDirectory ($candidateRole+'.zip')
            $body=$download.Substring($download.IndexOf('if ((Get-FileHash'),$download.IndexOf('$aapt =')-$download.IndexOf('if ((Get-FileHash'))
            Check "$candidateRole-real-archive-and-apk" {& ([scriptblock]::Create($body))}
            $old=$env:APPROVED_ARCHIVE_SHA;Set-TestEnv 'APPROVED_ARCHIVE_SHA' ('0'*64)
            Check "$candidateRole-wrong-archive-hash" {& ([scriptblock]::Create($body))} 'Archive SHA256'
            Set-TestEnv 'APPROVED_ARCHIVE_SHA' $old
            $apkPath=Join-Path $inputDir $env:APPROVED_FILENAME
            Remove-Item -LiteralPath $apkPath -Force
            $old=$env:APPROVED_APK_SHA;Set-TestEnv 'APPROVED_APK_SHA' ('0'*64)
            Check "$candidateRole-wrong-apk-hash" {& ([scriptblock]::Create($body))} 'Unsigned APK SHA256'
            Set-TestEnv 'APPROVED_APK_SHA' $old
        }
    }
    $packageGate=[scriptblock]::Create($download.Substring($download.IndexOf('$package ='),$download.IndexOf('"UNSIGNED_APK=')-$download.IndexOf('$package =')))
    foreach($candidateRole in @('candidate','recovery')){
        Reset-Source $candidateRole;& $sourceGate
        foreach($line in Get-Content -LiteralPath $env:GITHUB_ENV){$key,$value=$line -split '=',2;Set-TestEnv $key $value}
        $good="package: name='com.thor.displaypowertest' versionCode='$env:APPROVED_CODE' versionName='$env:APPROVED_VERSION'`ntargetSdkVersion:'25'"
        $badging=$good;$apk='checked.apk';Check "$candidateRole-package-identity" {& $packageGate}
        foreach($bad in @($good.Replace('com.thor.displaypowertest','invalid.package'),$good.Replace("versionCode='$env:APPROVED_CODE'","versionCode='76'"),
            $good.Replace("versionName='$env:APPROVED_VERSION'","versionName='1.7.2'"),$good.Replace("targetSdkVersion:'25'","targetSdkVersion:'34'"))){
            $badging=$bad;Check "$candidateRole-package-identity-negative" {& $packageGate} 'Unsigned package/version/code/SDK'
        }
    }
    $certificate = [scriptblock]::Create($verify.Substring($verify.IndexOf('$signer ='),$verify.IndexOf('$aapt =')-$verify.IndexOf('$signer =')))
    $expected=[regex]::Match($workflow,"EXPECTED_SIGNER_SHA256: '([^']+)'").Groups[1].Value
    Set-TestEnv 'EXPECTED_SIGNER_SHA256' $expected
    $verify="Number of signers: 1`nSigner #1 certificate SHA-256 digest: $expected"
    Check 'certificate-approved-parser' {& $certificate}
    $verify="Number of signers: 2`nSigner #1 certificate SHA-256 digest: $expected"
    Check 'certificate-extra-signer' {& $certificate} 'exactly the established'
    if($TestSignedApk){
        $signerTool=Join-Path $env:ANDROID_SDK_ROOT 'build-tools/35.0.0/apksigner.bat'
        $verify=(& $signerTool verify --verbose --print-certs $TestSignedApk|Out-String)
        if($LASTEXITCODE -ne 0){throw 'Test APK signature verification failed.'}
        Check 'actual-test-certificate-rejected' {& $certificate} 'exactly the established'
    }
    # Additional findings: use actual dispatch guard and actual encrypted-output block.
    $ci = Get-Content -LiteralPath (Join-Path $root '.github/workflows/host-tests.yml') -Raw
    $condition = [regex]::Match($ci,'(?ms)^  sign_main_candidate:\r?\n.*?^    if: ([^\r\n]+)').Groups[1].Value
    $required = "github.event_name == 'workflow_dispatch' && inputs.sign_main_candidate == true && github.ref == 'refs/heads/main' && github.actor == 'SirJesty' && github.triggering_actor == 'SirJesty'"
    if($condition -cne $required -or $ci -notmatch '(?ms)workflow_dispatch:\s*inputs:\s*sign_main_candidate:.*?type: boolean\s*default: false'){throw 'Main signing must be manual, explicitly opted in and default off.'}
    function Evaluate-MainCondition([string]$Event,[bool]$OptIn,[string]$Ref='refs/heads/main',[string]$Actor='SirJesty',[string]$Trigger='SirJesty'){
        $expression=$condition.Replace('github.event_name','$Event').Replace('inputs.sign_main_candidate','$OptIn').Replace('github.ref','$Ref').Replace('github.actor','$Actor').Replace('github.triggering_actor','$Trigger').Replace(' == ',' -eq ').Replace(' && ',' -and ').Replace('true','$true')
        return & ([scriptblock]::Create($expression))
    }
    Check 'main-dispatch-opt-in-positive' {if(-not(Evaluate-MainCondition 'workflow_dispatch' $true)){throw 'Valid opt-in rejected.'}}
    foreach($event in @('push','pull_request','workflow_run','merge_group')){
        foreach($optIn in @($true,$false)){Check "main-reject-$event-$optIn" {if(Evaluate-MainCondition $event $optIn){throw 'Unrelated event reached environment.'}}}
    }
    Check 'main-default-no-opt-in' {if(Evaluate-MainCondition 'workflow_dispatch' $false){throw 'Default requested signing.'}}
    Check 'main-ref-negative' {if(Evaluate-MainCondition 'workflow_dispatch' $true 'refs/heads/proposal'){throw 'Branch accepted.'}}
    Check 'main-actor-negative' {if(Evaluate-MainCondition 'workflow_dispatch' $true 'refs/heads/main' 'other'){throw 'Actor accepted.'}}
    Check 'main-rerun-actor-negative' {if(Evaluate-MainCondition 'workflow_dispatch' $true 'refs/heads/main' 'SirJesty' 'other'){throw 'Rerun actor accepted.'}}
    $mainBody=[regex]::Match($ci,'(?ms)^      - name: Verify signing environment and authorized operator.*?^        run: \|\r?\n(?<body>.*?)(?=^      - uses:)').Groups['body'].Value
    $mainGuard=[scriptblock]::Create((($mainBody -split '\r?\n'|Where-Object{$_ -match '^          '}|ForEach-Object{$_.Substring(10)}) -join "`n"))
    Set-TestEnv 'ORIGINAL_ACTOR' 'SirJesty';Set-TestEnv 'TRIGGERING_ACTOR' 'SirJesty';Set-TestEnv 'SIGNING_ENV_READY' 'true'
    Set-TestEnv 'CALLER_EVENT' 'push';Set-TestEnv 'MAIN_SIGNING_OPT_IN' 'true'
    Check 'main-runtime-push-rejected' {& $mainGuard} 'explicit manual opt-in'
    Set-TestEnv 'CALLER_EVENT' 'workflow_dispatch';Set-TestEnv 'MAIN_SIGNING_OPT_IN' 'false'
    Check 'main-runtime-default-rejected' {& $mainGuard} 'explicit manual opt-in'
    Set-TestEnv 'MAIN_SIGNING_OPT_IN' 'true';Check 'main-runtime-positive' {& $mainGuard}
    $uses=[regex]::Matches($workflow,'(?m)^\s+(?:- )?uses: ([^\r\n]+)')
    if($uses.Count -ne 3){throw 'Unexpected privileged action set.'}; foreach($use in $uses){if($use.Groups[1].Value -notmatch '^[^@\s]+@[0-9a-f]{40}(?:\s+#.*)?$'){throw 'New privileged action is not pinned to a full SHA.'}}
    $checks++;Write-Host 'PASS pinned-privileged-actions'
    $upload=[regex]::Match($workflow,'(?ms)^      - name: Store only encrypted owner delivery.*?(?=^      - name:)').Value
    if(-not $upload -or $upload -notmatch '(?m)^          path: \$\{\{ env.SEALED_DELIVERY \}\}\s*$' -or
        [regex]::Matches($workflow,'uses: actions/upload-artifact@').Count -ne 1 -or $upload -match 'SIGNED_APK|output/|\.apk|approved-provenance.json'){throw 'Upload can expose plaintext signed output.'}
    if($workflow.IndexOf('Seal verified output for owner-only delivery') -gt $workflow.IndexOf('Store only encrypted owner delivery') -or
        $workflow.IndexOf('Validate pinned delivery recipient') -gt $workflow.IndexOf('Prepare protected signing material')){throw 'Recipient/encryption ordering invalid.'}
    $checks++;Write-Host 'PASS upload-only-sealed-output'
    $recipient=[scriptblock]::Create((Block 'Validate pinned delivery recipient'))
    $seal=[scriptblock]::Create((Block 'Seal verified output for owner-only delivery'))
    $pinnedPublic=[regex]::Match($workflow,"DELIVERY_RECIPIENT_SPKI_BASE64: '([^']+)'").Groups[1].Value
    $pinnedFingerprint=[regex]::Match($workflow,"DELIVERY_RECIPIENT_SHA256: '([^']+)'").Groups[1].Value
    Set-TestEnv 'DELIVERY_RECIPIENT_SPKI_BASE64' $pinnedPublic;Set-TestEnv 'DELIVERY_RECIPIENT_SHA256' $pinnedFingerprint
    Check 'pinned-recipient-valid' {& $recipient}
    Set-TestEnv 'DELIVERY_RECIPIENT_SHA256' ('0'*64);Check 'recipient-hash-negative' {& $recipient} 'Delivery recipient differs'
    Set-TestEnv 'DELIVERY_RECIPIENT_SHA256' $pinnedFingerprint
    $deliveryRsa=[Security.Cryptography.RSA]::Create(3072)
    $wrongRsa=[Security.Cryptography.RSA]::Create(3072)
    $oldLocation=Get-Location; $oldProcessDirectory=[Environment]::CurrentDirectory
    try{
        # Default tests use a transient delivery key; the fixed public recipient is checked above.
        $public=$deliveryRsa.ExportSubjectPublicKeyInfo()
        Set-TestEnv 'DELIVERY_RECIPIENT_SPKI_BASE64' ([Convert]::ToBase64String($public))
        Set-TestEnv 'DELIVERY_RECIPIENT_SHA256' ([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($public)).ToLowerInvariant())
        Check 'transient-recipient-valid' {& $recipient}
        $fixture=Join-Path $sandbox 'sealed-fixture';New-Item -ItemType Directory -Path $fixture|Out-Null;Set-Location -LiteralPath $fixture
        [Environment]::CurrentDirectory=$fixture; New-Item -ItemType Directory -Path output|Out-Null
        $actualSigned=if($TestSignedApk){[IO.Path]::GetFullPath($TestSignedApk)}else{Join-Path $sandbox 'signed.apk'}
        Set-TestEnv 'SIGNED_APK' $actualSigned;Set-TestEnv 'APPROVED_ROLE' 'candidate';Set-TestEnv 'APPROVED_RUN' '38596116146'
        Set-TestEnv 'GITHUB_ENV' (Join-Path $fixture 'delivery.env')
        [IO.File]::WriteAllText((Join-Path $fixture 'output/approved-provenance.json'),'{"privateMarker":"owner-only","functionalPayloadIdentical":true}')
        Check 'actual-encryption-block' {& $seal}
        $sealedPath=Join-Path $fixture 'delivery-output/approved-delivery.sealed.json'
        $envelope=Get-Content -LiteralPath $sealedPath -Raw|ConvertFrom-Json
        function Decrypt-Fixture($Envelope,[Security.Cryptography.RSA]$PrivateRsa){
            $aad=[Convert]::FromBase64String($Envelope.header)
            $key=$PrivateRsa.Decrypt([Convert]::FromBase64String($Envelope.wrappedKey),[Security.Cryptography.RSAEncryptionPadding]::OaepSHA256)
            $cipher=[Convert]::FromBase64String($Envelope.ciphertext);$plain=[byte[]]::new($cipher.Length)
            $aes=[Security.Cryptography.AesGcm]::new($key,16)
            try{$aes.Decrypt([Convert]::FromBase64String($Envelope.nonce),$cipher,[Convert]::FromBase64String($Envelope.tag),$plain,$aad);return ,$plain}
            finally{$aes.Dispose();[Array]::Clear($key,0,$key.Length)}
        }
        $clear=Decrypt-Fixture $envelope $deliveryRsa
        $zipStream=[IO.MemoryStream]::new($clear);$zip=[IO.Compression.ZipArchive]::new($zipStream,[IO.Compression.ZipArchiveMode]::Read)
        try{
            Check 'sealed-plaintext-content-preserved' {
                if($zip.Entries.Count -ne 2 -or @($zip.Entries.FullName|Sort-Object) -join ',' -cne 'approved-provenance.json,approved.apk'){throw 'Bundle entry set differs.'}
                $apkStream=$zip.GetEntry('approved.apk').Open();$hash=[Security.Cryptography.SHA256]::Create()
                try{if([Convert]::ToHexString($hash.ComputeHash($apkStream)) -cne (Get-FileHash -LiteralPath $actualSigned -Algorithm SHA256).Hash){throw 'Signed APK bytes differ.'}}finally{$hash.Dispose();$apkStream.Dispose()}
                $reader=[IO.StreamReader]::new($zip.GetEntry('approved-provenance.json').Open())
                try{if($reader.ReadToEnd() -cne [IO.File]::ReadAllText('output/approved-provenance.json')){throw 'Private provenance differs.'}}finally{$reader.Dispose()}
            }
        }finally{$zip.Dispose();$zipStream.Dispose();[Array]::Clear($clear,0,$clear.Length)}
        Check 'sealed-public-file-has-no-plaintext' {
            $text=[IO.File]::ReadAllText($sealedPath)
            if($text.Contains('owner-only') -or $text.Contains('functionalPayloadIdentical') -or $text.Contains('approved.apk') -or
                @((Get-ChildItem -LiteralPath delivery-output -File).Name).Count -ne 1){throw 'Plaintext uploaded.'}
        }
        Check 'sealed-wrong-private-key' {Decrypt-Fixture $envelope $wrongRsa} 'Decrypt'
        foreach($field in @('header','wrappedKey','nonce','tag','ciphertext')){
            $mutated=$envelope|ConvertTo-Json -Compress|ConvertFrom-Json
            $bytes=[Convert]::FromBase64String($mutated.$field);$bytes[0]=$bytes[0] -bxor 1;$mutated.$field=[Convert]::ToBase64String($bytes)
            Check "sealed-tamper-$field" {Decrypt-Fixture $mutated $deliveryRsa} 'Decrypt'
        }
        # Recipient failure rejects sealing; upload retains the default success-only condition.
        Set-TestEnv 'DELIVERY_RECIPIENT_SHA256' ('0'*64)
        Check 'seal-recipient-mismatch' {& $seal} 'Delivery recipient differs'
        Set-TestEnv 'DELIVERY_RECIPIENT_SHA256' $pinnedFingerprint
        Set-TestEnv 'DELIVERY_RECIPIENT_SPKI_BASE64' $pinnedPublic
    }finally{[Environment]::CurrentDirectory=$oldProcessDirectory;Set-Location -LiteralPath $oldLocation.Path;$deliveryRsa.Dispose();$wrongRsa.Dispose()}
    Write-Host "Frozen signing policy checks passed: $checks; real workflow blocks, no production signing or deployment."
} finally {
    foreach($name in $savedEnv.Keys){[Environment]::SetEnvironmentVariable($name,$savedEnv[$name])}
    $global:LASTEXITCODE=$savedExitCode
    if(Test-Path -LiteralPath $sandbox){
        $resolved=[IO.Path]::GetFullPath((Resolve-Path -LiteralPath $sandbox).Path)
        $items=@(Get-Item -LiteralPath $sandbox)+@(Get-ChildItem -LiteralPath $sandbox -Recurse -Force)
        if($resolved -ne $sandbox -or @($items|Where-Object{$_.Attributes -band [IO.FileAttributes]::ReparsePoint}).Count){throw 'Unexpected signing fixture cleanup path.'}
        Remove-Item -LiteralPath $sandbox -Recurse -Force
    }
}
