[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$out = Join-Path $env:TEMP 'jesty-thor-watcher-runtime-tests'
New-Item -ItemType Directory -Force $out | Out-Null
@'
package android.os;
public final class SystemClock {
    public static long elapsedRealtime() { return System.nanoTime() / 1000000L; }
}
'@ | Set-Content -LiteralPath (Join-Path $out 'SystemClock.java') -Encoding ascii
@'
package android.util;
public final class Log {
    public static int d(String tag, String message) { return 0; }
}
'@ | Set-Content -LiteralPath (Join-Path $out 'Log.java') -Encoding ascii
& javac -source 8 -target 8 -d $out (Join-Path $out 'SystemClock.java') (Join-Path $out 'Log.java') `
    (Join-Path $repo 'src\com\thor\displaypowertest\WatcherCadenceModel.java') `
    (Join-Path $repo 'src\com\thor\displaypowertest\WatcherCadence.java') `
    (Join-Path $repo 'tests\WatcherCadenceRuntimeTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Watcher runtime test compilation failed.' }
& java -cp $out WatcherCadenceRuntimeTest
if ($LASTEXITCODE -ne 0) { throw 'Watcher runtime tests failed.' }
