[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string] $NdkPath,
    [Parameter(Mandatory = $true)] [string] $OutputDirectory
)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$properties = Join-Path $NdkPath 'source.properties'
if (-not (Test-Path -LiteralPath $properties -PathType Leaf)) {
    throw 'Supply an already installed Android NDK 27.2.12479018; no download is performed.'
}
$revision = Get-Content -LiteralPath $properties | Where-Object { $_ -match '^Pkg\.Revision\s*=' }
if (@($revision).Count -ne 1 -or $revision -notmatch '^Pkg\.Revision\s*=\s*27\.2\.12479018\s*$') {
    throw 'Retirement JNI requires exactly Android NDK 27.2.12479018.'
}
$toolchain = Join-Path $NdkPath 'toolchains\llvm\prebuilt\windows-x86_64'
$compiler = Join-Path $toolchain 'bin\clang.exe'
if (-not (Test-Path -LiteralPath $compiler -PathType Leaf)) {
    throw "Missing existing NDK compiler: $compiler"
}
$source = Join-Path $project 'native\retirement.c'
$targets = [ordered]@{
    'arm64-v8a' = 'aarch64-linux-android25'
    'armeabi-v7a' = 'armv7a-linux-androideabi25'
    'x86' = 'i686-linux-android25'
    'x86_64' = 'x86_64-linux-android25'
}
foreach ($abi in $targets.Keys) {
    $directory = Join-Path $OutputDirectory $abi
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $library = Join-Path $directory 'libretirement.so'
    & $compiler "--target=$($targets[$abi])" "--sysroot=$(Join-Path $toolchain 'sysroot')" `
        -std=c11 -O2 -fPIC -fvisibility=hidden -Wall -Wextra -Werror `
        -shared -pthread '-Wl,--no-undefined' '-Wl,-z,relro,-z,now' `
        '-Wl,-soname,libretirement.so' $source -o $library
    if ($LASTEXITCODE -ne 0) { throw "Retirement native compilation failed: $abi" }
    if (-not (Test-Path -LiteralPath $library -PathType Leaf)) {
        throw "Retirement library was not produced: $abi"
    }
}
