[CmdletBinding()]
param(
    [string]$Repository = (Split-Path -Parent $PSScriptRoot),
    [string]$AndroidSdk = $env:ANDROID_SDK_ROOT,
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
if (-not $AndroidSdk) { $AndroidSdk = $env:ANDROID_HOME }
if (-not $AndroidSdk) { throw 'Android SDK is required for real updater coupling host tests' }
$root = [IO.Path]::GetFullPath($Repository)
$jar = Join-Path $AndroidSdk 'platforms/android-34/android.jar'
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { (Get-Command javac).Source }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { (Get-Command java).Source }
foreach ($file in @($jar, $javac, $java)) {
    if (-not (Test-Path -LiteralPath $file)) { throw 'Missing Android 34 / JDK 17 prerequisite' }
}
$dir = Join-Path $root 'tests/integration/updater-coupling'
$tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
$work = Join-Path $tempParent ('jesty-updater-coupling-' + [guid]::NewGuid().ToString('N'))
if ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($work)) -ne $tempParent) {
    throw 'Unsafe test output path'
}
try {
    New-Item -ItemType Directory -Path $work | Out-Null
    $production = Join-Path $work 'production'
    $fakes = Join-Path $work 'fakes'
    New-Item -ItemType Directory -Path $production, $fakes | Out-Null
    $names = @('AppUpdater','UpdateInstallReceiver','UpdateVersion','UpdateCommitGate',
        'UpdateConfirmationStore','UpdateSessionOwnership','ReplacementSessionPolicy',
        'UpdateTaskDispatch')
    $classes = @($names | ForEach-Object { Join-Path $root "src/com/thor/displaypowertest/$_.java" })
    & $javac -encoding UTF-8 -source 8 -target 8 -classpath $jar -d $production @classes
    if ($LASTEXITCODE -ne 0) { throw 'Production updater classes did not compile' }
    $fixtures = @(Get-ChildItem -LiteralPath (Join-Path $dir 'fixtures') -Recurse -Filter '*.java' -File | ForEach-Object FullName)
    $regression = Join-Path $dir 'UpdaterOwnershipRegression.java'
    & $javac -encoding UTF-8 -source 8 -target 8 -classpath "$production;$jar" -d $fakes @fixtures $regression
    if ($LASTEXITCODE -ne 0) { throw 'Host Android updater adapters did not compile' }
    & $java -classpath "$fakes;$production;$jar" com.thor.displaypowertest.UpdaterOwnershipRegression (Join-Path $work 'cache')
    if ($LASTEXITCODE -ne 0) { throw 'Real production updater callback host regression failed' }
} finally {
    if (Test-Path -LiteralPath $work) {
        $resolved = (Resolve-Path -LiteralPath $work).Path
        if ([IO.Path]::GetDirectoryName($resolved) -ne $tempParent -or
            ((Get-Item -LiteralPath $work).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Refusing unexpected fixture cleanup target'
        }
        Remove-Item -LiteralPath $work -Recurse -Force
    }
}
