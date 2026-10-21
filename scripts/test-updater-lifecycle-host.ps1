[CmdletBinding()]
param(
    [string]$Repository = (Split-Path -Parent $PSScriptRoot),
    [string]$AndroidSdk = $env:ANDROID_SDK_ROOT,
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
if (-not $AndroidSdk) { $AndroidSdk = $env:ANDROID_HOME }
if (-not $AndroidSdk) { throw 'Android SDK is required for updater lifecycle host tests' }
$root = [IO.Path]::GetFullPath($Repository)
$jar = Join-Path $AndroidSdk 'platforms/android-34/android.jar'
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { (Get-Command javac).Source }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { (Get-Command java).Source }
foreach ($file in @($jar, $javac, $java)) {
    if (-not (Test-Path -LiteralPath $file)) { throw 'Missing Android 34 / JDK 17 prerequisite' }
}
$dir = Join-Path $root 'tests/integration/updater-lifecycle'
$tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
$work = Join-Path $tempParent ('jesty-updater-lifecycle-' + [guid]::NewGuid().ToString('N'))
if ([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($work)) -ne $tempParent) { throw 'Unexpected host output path' }
try {
    New-Item -ItemType Directory -Path $work | Out-Null
    $production = Join-Path $work 'production'
    $fakes = Join-Path $work 'fakes'
    New-Item -ItemType Directory -Path $production, $fakes | Out-Null
    $names = @('AppUpdater','UpdateArchiveVerifier','UpdateReleaseCatalog','UpdateInstallReceiver','UpdateVersion','UpdateCommitGate',
        'UpdateConfirmationStore','UpdateSessionOwnership','ReplacementSessionPolicy','UpdateTaskDispatch')
    $classes = @($names | ForEach-Object { Join-Path $root "src/com/thor/displaypowertest/$_.java" })
    & $javac -encoding UTF-8 -source 8 -target 8 -classpath $jar -d $production @classes
    if ($LASTEXITCODE -ne 0) { throw 'Production updater lifecycle classes did not compile' }
    $fixtures = @(Get-ChildItem -LiteralPath (Join-Path $dir 'fixtures') -Filter '*.java' -Recurse -File | ForEach-Object FullName)
    & $javac -encoding UTF-8 -source 8 -target 8 -classpath "$production;$jar" -d $fakes @fixtures (Join-Path $dir 'UpdaterLifecycleHostTest.java')
    if ($LASTEXITCODE -ne 0) { throw 'Updater lifecycle host adapters did not compile' }
    # CI always checks the fixed contract; historical baseline modes remain in the source for research.
    & $java -classpath "$fakes;$production;$jar" com.thor.displaypowertest.UpdaterLifecycleHostTest (Join-Path $work 'cache') full
    if ($LASTEXITCODE -ne 0) { throw 'Production updater lifecycle host regression failed' }
} finally {
    if (Test-Path -LiteralPath $work) {
        $resolved = (Resolve-Path -LiteralPath $work).Path
        if ([IO.Path]::GetDirectoryName($resolved) -ne $tempParent -or
            ((Get-Item -LiteralPath $work).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Refusing unexpected fixture cleanup target' }
        Remove-Item -LiteralPath $work -Recurse -Force
    }
}
# Archive characterization calls the same production entrypoint with metadata adapters.
$resolvedJavaHome = Split-Path -Parent (Split-Path -Parent $javac)
& python (Join-Path $root 'scripts/test-updater-archive-host.py') --sdk $AndroidSdk --java $resolvedJavaHome --negative-controls
if ($LASTEXITCODE -ne 0) { throw 'Production updater archive host regression failed' }
