[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$build = Get-Content -LiteralPath (Join-Path $root 'build.ps1') -Raw

# Production APK compilation must enumerate only src/; host Android fixture
# classes must never be sent to D8. A build-pipeline rewrite requires explicit
# review and a new test rather than silently bypassing this safety invariant.
$srcEnumerator = '$sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $project ''src'') -Filter ''*.java'' -Recurse | ForEach-Object FullName)'
$javacCompile = '& $javac -encoding UTF-8 -source 8 -target 8 -classpath $compilePath -d $javaClasses $sourceFiles'
$dexCompile = '& $d8 --min-api 25 --lib $androidJar --output $dexOut (Join-Path $extract ''classes.dex'') $javaJar'
if (-not $build.Contains($srcEnumerator) -or -not $build.Contains($javacCompile) -or
    -not $build.Contains($dexCompile)) {
    throw 'Production APK sources must remain scoped to src/ and dex must consume only built application classes.'
}

# These fixtures deliberately shadow Android package names only inside host
# classpaths. None may be stored under the production src tree.
$fixtureDirs = @(
    'tests/fixtures',
    'tests/integration/updater-coupling/fixtures'
)
foreach ($relative in $fixtureDirs) {
    $fixtures = Join-Path $root $relative
    if (-not (Test-Path -LiteralPath $fixtures -PathType Container)) {
        throw "Missing host-only test fixture directory: $relative"
    }
    $java = @(Get-ChildItem -LiteralPath $fixtures -Filter '*.java' -Recurse -File)
    if ($java.Count -eq 0) { throw "Host fixture directory contains no Java tests: $relative" }
}
Write-Host 'Source/fixture build boundary contract passed (static test, not dex inspection).'
