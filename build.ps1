[CmdletBinding()]
param(
    [string] $AndroidSdk = $env:ANDROID_SDK_ROOT,
    [string] $BuildToolsVersion = '35.0.0',
    [string] $ApktoolJar = $env:APKTOOL_JAR,
    [string] $JavaHome = $env:JAVA_HOME,
    [switch] $Sign,
    [string] $KeystorePath = $env:JESTY_KEYSTORE,
    [string] $KeyAlias = 'thor-display-power-auto'
)

$ErrorActionPreference = 'Stop'

$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$work = Join-Path $project 'build'
$apkSource = Join-Path $project 'apk'
$dist = Join-Path $project 'dist'

if (-not $AndroidSdk) {
    if (-not $env:LOCALAPPDATA) { throw 'Set ANDROID_SDK_ROOT or pass -AndroidSdk.' }
    $AndroidSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
if (-not $ApktoolJar) {
    $ApktoolJar = Join-Path $project 'tools\apktool.jar'
}

$javaBin = if ($JavaHome) { Join-Path $JavaHome 'bin' } else { $null }
if (-not $javaBin -or -not (Test-Path -LiteralPath (Join-Path $javaBin 'java.exe'))) {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if (-not $javaCommand) { throw 'Set JAVA_HOME to a JDK 17 installation.' }
    $javaBin = Split-Path -Parent $javaCommand.Source
}

$androidJar = Join-Path $AndroidSdk 'platforms\android-34\android.jar'
$buildTools = Join-Path $AndroidSdk "build-tools\$BuildToolsVersion"
$java = Join-Path $javaBin 'java.exe'
$javac = Join-Path $javaBin 'javac.exe'
$jar = Join-Path $javaBin 'jar.exe'
$d8 = Join-Path $buildTools 'd8.bat'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'

$required = @($androidJar, $ApktoolJar, $java, $javac, $jar, $d8, $zipalign, $apksigner)
foreach ($path in $required) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing build dependency: $path" }
}

$expectedWork = [IO.Path]::GetFullPath((Join-Path $project 'build'))
if (Test-Path -LiteralPath $work) {
    $resolvedWork = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $work).Path)
    if (-not [String]::Equals($resolvedWork, $expectedWork, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to clean unexpected path: $resolvedWork"
    }
    Remove-Item -LiteralPath $work -Recurse -Force
}
New-Item -ItemType Directory -Path $work, $dist -Force | Out-Null

$baseApk = Join-Path $work 'base.apk'
$frameworkPath = Join-Path $work 'apktool-framework'
New-Item -ItemType Directory -Path $frameworkPath -Force | Out-Null
& $java -jar $ApktoolJar b $apkSource -p $frameworkPath -o $baseApk
if ($LASTEXITCODE -ne 0) { throw 'apktool build failed' }

$stubClasses = Join-Path $work 'stub-classes'
$javaClasses = Join-Path $work 'java-classes'
New-Item -ItemType Directory -Path $stubClasses, $javaClasses | Out-Null
$stubSources = @(Get-ChildItem -LiteralPath (Join-Path $project 'stubs') -Filter '*.java' -Recurse | ForEach-Object FullName)
& $javac -source 8 -target 8 -classpath $androidJar -d $stubClasses $stubSources
if ($LASTEXITCODE -ne 0) { throw 'Stub compilation failed' }

$sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $project 'src') -Filter '*.java' -Recurse | ForEach-Object FullName)
$compilePath = "$androidJar;$stubClasses"
& $javac -source 8 -target 8 -classpath $compilePath -d $javaClasses $sourceFiles
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }

$javaJar = Join-Path $work 'java.jar'
& $jar cf $javaJar -C $javaClasses .
if ($LASTEXITCODE -ne 0) { throw 'Java archive failed' }

$extract = Join-Path $work 'extract'
$dexOut = Join-Path $work 'dex'
New-Item -ItemType Directory -Path $extract, $dexOut | Out-Null
Push-Location $extract
try { & $jar xf $baseApk classes.dex }
finally { Pop-Location }
& $d8 --min-api 25 --lib $androidJar --output $dexOut (Join-Path $extract 'classes.dex') $javaJar
if ($LASTEXITCODE -ne 0) { throw 'DEX merge failed' }

$merged = Join-Path $work 'merged-unsigned.apk'
Copy-Item -LiteralPath $baseApk -Destination $merged
Push-Location $dexOut
try { & $jar uf $merged classes.dex }
finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { throw 'APK DEX replacement failed' }

$aligned = Join-Path $work 'aligned-unsigned.apk'
& $zipalign -f -p 4 $merged $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
& $zipalign -c -v 4 $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign verification failed' }

$output = Join-Path $dist 'Jesty-Thor-Fix-0.33-unsigned.apk'
Copy-Item -LiteralPath $aligned -Destination $output -Force

if ($Sign) {
    if (-not $KeystorePath) { throw 'Pass -KeystorePath or set JESTY_KEYSTORE.' }
    if (-not (Test-Path -LiteralPath $KeystorePath)) { throw "Keystore not found: $KeystorePath" }

    $securePassword = Read-Host 'Keystore password' -AsSecureString
    $passwordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr)
        $signedOutput = Join-Path $dist 'Jesty-Thor-Fix-0.33.apk'
        $passwordInput = "$plainPassword`n$plainPassword"
        $passwordInput | & $apksigner sign `
            --ks $KeystorePath --ks-key-alias $KeyAlias `
            --ks-pass stdin --key-pass stdin `
            --out $signedOutput $aligned
        if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
        & $apksigner verify --verbose --print-certs $signedOutput
        if ($LASTEXITCODE -ne 0) { throw 'APK verification failed' }
        $output = $signedOutput
    }
    finally {
        if ($passwordPtr -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr)
        }
        $plainPassword = $null
        $passwordInput = $null
    }
}

Get-FileHash -Algorithm SHA256 -LiteralPath $output | Format-List
