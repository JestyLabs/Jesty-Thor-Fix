# Archived CPU pinning evidence model

Source branch: `work/cpu-diagnostic-evidence-model`  
Head: `86f460bece2c1b197fd3bf8f53d6b87361004ac3`

This branch had no pull request and therefore contained project information that would otherwise exist only on the branch ref. It is archived here before branch pruning.

The model was intentionally not wired into the dashboard. It explored replacing fixed sample-count diagnostics with elapsed-time CPU residency evidence, including busy-workload suppression, dropped polling cadence, counter resets, and non-monotonic time.

## Original model

```java
package com.thor.displaypowertest;

/**
 * Time-based CPU pinning evidence model.
 *
 * This is intentionally not wired into the dashboard yet. It exists so the
 * current fixed sample-count diagnostic can be replaced later without tying
 * confidence to a 1 Hz UI polling cadence.
 */
public final class CpuPinningEvidenceModel {
    public enum State { CHECKING, NORMAL, PINNED, BUSY }

    public static final class Result {
        public final State state;
        public final long pinnedEvidenceMs;
        public final long normalEvidenceMs;

        Result(State state, long pinnedEvidenceMs, long normalEvidenceMs) {
            this.state = state;
            this.pinnedEvidenceMs = pinnedEvidenceMs;
            this.normalEvidenceMs = normalEvidenceMs;
        }
    }

    private final long pinnedQualifyMs;
    private final long normalQualifyMs;
    private final double pinnedResidency;
    private final int busyUtilization;

    private boolean haveBaseline;
    private long previousAtMs;
    private long previousLittleHigh;
    private long previousLittleTotal;
    private long previousBigHigh;
    private long previousBigTotal;
    private long pinnedEvidenceMs;
    private long normalEvidenceMs;
    private State confirmed = State.CHECKING;

    public CpuPinningEvidenceModel(long pinnedQualifyMs, long normalQualifyMs,
            double pinnedResidency, int busyUtilization) {
        if (pinnedQualifyMs <= 0L || normalQualifyMs <= 0L)
            throw new IllegalArgumentException("qualification time must be positive");
        if (!(pinnedResidency > 0d && pinnedResidency <= 1d))
            throw new IllegalArgumentException("residency threshold out of range");
        if (busyUtilization < 0 || busyUtilization > 100)
            throw new IllegalArgumentException("busy utilization out of range");
        this.pinnedQualifyMs = pinnedQualifyMs;
        this.normalQualifyMs = normalQualifyMs;
        this.pinnedResidency = pinnedResidency;
        this.busyUtilization = busyUtilization;
    }

    public Result observe(long nowMs,
            long littleHighTicks, long littleTotalTicks,
            long bigHighTicks, long bigTotalTicks,
            int utilization) {
        if (!validCounters(littleHighTicks, littleTotalTicks, bigHighTicks, bigTotalTicks)
                || utilization < 0 || utilization > 100 || nowMs < 0L) {
            reset();
            return result(State.CHECKING);
        }

        if (!haveBaseline) {
            setBaseline(nowMs, littleHighTicks, littleTotalTicks, bigHighTicks, bigTotalTicks);
            return result(State.CHECKING);
        }

        long elapsedMs = nowMs - previousAtMs;
        long littleHighDelta = littleHighTicks - previousLittleHigh;
        long littleTotalDelta = littleTotalTicks - previousLittleTotal;
        long bigHighDelta = bigHighTicks - previousBigHigh;
        long bigTotalDelta = bigTotalTicks - previousBigTotal;

        setBaseline(nowMs, littleHighTicks, littleTotalTicks, bigHighTicks, bigTotalTicks);

        if (elapsedMs <= 0L || littleHighDelta < 0L || littleTotalDelta <= 0L
                || bigHighDelta < 0L || bigTotalDelta <= 0L) {
            clearEvidence();
            confirmed = State.CHECKING;
            return result(State.CHECKING);
        }

        if (utilization >= busyUtilization) {
            clearEvidence();
            return result(State.BUSY);
        }

        boolean pinned = ratio(littleHighDelta, littleTotalDelta) >= pinnedResidency
                && ratio(bigHighDelta, bigTotalDelta) >= pinnedResidency;
        if (pinned) {
            pinnedEvidenceMs = saturatingAdd(pinnedEvidenceMs, elapsedMs);
            normalEvidenceMs = 0L;
            if (pinnedEvidenceMs >= pinnedQualifyMs) confirmed = State.PINNED;
        } else {
            normalEvidenceMs = saturatingAdd(normalEvidenceMs, elapsedMs);
            pinnedEvidenceMs = 0L;
            if (normalEvidenceMs >= normalQualifyMs) confirmed = State.NORMAL;
        }

        return result(confirmed);
    }

    public void reset() {
        haveBaseline = false;
        previousAtMs = 0L;
        previousLittleHigh = previousLittleTotal = 0L;
        previousBigHigh = previousBigTotal = 0L;
        clearEvidence();
        confirmed = State.CHECKING;
    }

    private Result result(State state) {
        return new Result(state, pinnedEvidenceMs, normalEvidenceMs);
    }

    private void setBaseline(long atMs, long littleHigh, long littleTotal,
            long bigHigh, long bigTotal) {
        previousAtMs = atMs;
        previousLittleHigh = littleHigh;
        previousLittleTotal = littleTotal;
        previousBigHigh = bigHigh;
        previousBigTotal = bigTotal;
        haveBaseline = true;
    }

    private void clearEvidence() {
        pinnedEvidenceMs = 0L;
        normalEvidenceMs = 0L;
    }

    private static boolean validCounters(long littleHigh, long littleTotal,
            long bigHigh, long bigTotal) {
        return littleHigh >= 0L && littleTotal >= 0L
                && bigHigh >= 0L && bigTotal >= 0L;
    }

    private static double ratio(long numerator, long denominator) {
        return denominator > 0L ? (double) numerator / denominator : 0d;
    }

    private static long saturatingAdd(long a, long b) {
        return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
    }
}

```

## Original host test

```java
import com.thor.displaypowertest.CpuPinningEvidenceModel;

public final class CpuPinningEvidenceModelTest {
    private static int assertions;

    public static void main(String[] args) {
        qualifiesByElapsedTimeNotSampleCount();
        normalUsesIndependentEvidence();
        busyBreaksQualification();
        droppedCadenceStillUsesElapsedTime();
        counterResetFailsClosed();
        nonMonotonicTimeFailsClosed();
        System.out.println("CpuPinningEvidenceModelTest passed: " + assertions + " assertions");
    }

    private static CpuPinningEvidenceModel model() {
        return new CpuPinningEvidenceModel(3000L, 2000L, 0.85d, 25);
    }

    private static void qualifiesByElapsedTimeNotSampleCount() {
        CpuPinningEvidenceModel m = model();
        eq(CpuPinningEvidenceModel.State.CHECKING, sample(m, 0, 0, 0, 0, 0, 5).state);
        eq(CpuPinningEvidenceModel.State.CHECKING, sample(m, 500, 9, 10, 9, 10, 5).state);
        eq(CpuPinningEvidenceModel.State.CHECKING, sample(m, 1500, 27, 30, 27, 30, 5).state);
        CpuPinningEvidenceModel.Result r = sample(m, 3000, 54, 60, 54, 60, 5);
        eq(CpuPinningEvidenceModel.State.PINNED, r.state);
        truth(r.pinnedEvidenceMs == 3000L);
    }

    private static void normalUsesIndependentEvidence() {
        CpuPinningEvidenceModel m = model();
        sample(m, 0, 0, 0, 0, 0, 5);
        sample(m, 1000, 9, 10, 9, 10, 5);
        CpuPinningEvidenceModel.Result r = sample(m, 2000, 10, 20, 10, 20, 5);
        eq(CpuPinningEvidenceModel.State.CHECKING, r.state);
        truth(r.pinnedEvidenceMs == 0L);
        r = sample(m, 3000, 11, 30, 11, 30, 5);
        eq(CpuPinningEvidenceModel.State.NORMAL, r.state);
    }

    private static void busyBreaksQualification() {
        CpuPinningEvidenceModel m = model();
        sample(m, 0, 0, 0, 0, 0, 5);
        sample(m, 2000, 18, 20, 18, 20, 5);
        CpuPinningEvidenceModel.Result busy = sample(m, 2500, 23, 25, 23, 25, 60);
        eq(CpuPinningEvidenceModel.State.BUSY, busy.state);
        truth(busy.pinnedEvidenceMs == 0L);
        CpuPinningEvidenceModel.Result after = sample(m, 5000, 45, 50, 45, 50, 5);
        eq(CpuPinningEvidenceModel.State.CHECKING, after.state);
    }

    private static void droppedCadenceStillUsesElapsedTime() {
        CpuPinningEvidenceModel m = model();
        sample(m, 100, 0, 0, 0, 0, 5);
        CpuPinningEvidenceModel.Result r = sample(m, 4100, 90, 100, 90, 100, 5);
        eq(CpuPinningEvidenceModel.State.PINNED, r.state);
        truth(r.pinnedEvidenceMs == 4000L);
    }

    private static void counterResetFailsClosed() {
        CpuPinningEvidenceModel m = model();
        sample(m, 0, 100, 100, 100, 100, 5);
        sample(m, 2000, 118, 120, 118, 120, 5);
        CpuPinningEvidenceModel.Result reset = sample(m, 2500, 1, 2, 1, 2, 5);
        eq(CpuPinningEvidenceModel.State.CHECKING, reset.state);
        truth(reset.pinnedEvidenceMs == 0L);
    }

    private static void nonMonotonicTimeFailsClosed() {
        CpuPinningEvidenceModel m = model();
        sample(m, 1000, 0, 0, 0, 0, 5);
        CpuPinningEvidenceModel.Result r = sample(m, 900, 9, 10, 9, 10, 5);
        eq(CpuPinningEvidenceModel.State.CHECKING, r.state);
    }

    private static CpuPinningEvidenceModel.Result sample(CpuPinningEvidenceModel m,
            long at, long lh, long lt, long bh, long bt, int util) {
        return m.observe(at, lh, lt, bh, bt, util);
    }

    private static void eq(Object expected, Object actual) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }

    private static void truth(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("expected true");
    }
}

```

## Original dashboard-test harness from the branch

```powershell
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$output = Join-Path $env:TEMP 'jesty-thor-dashboard-tests'

if (Test-Path -LiteralPath $output) {
    Remove-Item -LiteralPath $output -Recurse -Force
}
New-Item -ItemType Directory -Path $output | Out-Null

# The updater is an ordinary app install; it must never reach the root daemon or a shell.
$updaterSources = @('AppUpdater.java', 'UpdateInstallReceiver.java', 'UpdateVersion.java') |
    ForEach-Object { Get-Content -LiteralPath (Join-Path $repository "src\com\thor\displaypowertest\$_") -Raw }
if (($updaterSources -join "`n") -match 'SocketClient|PServer|Runtime\.getRuntime|ProcessBuilder|"su"') {
    throw 'The in-app updater must not use the root daemon, su or a shell.'
}

# MainActivity coordinates lifecycle only: no daemon requests and no view building.
$activity = Get-Content -LiteralPath (Join-Path $repository 'src\com\thor\displaypowertest\MainActivity.java') -Raw
if ($activity -match 'SocketClient|new (TextView|LinearLayout|Switch|MediaPlayer)\(') {
    throw 'MainActivity must delegate daemon commands, telemetry, layout and media.'
}

$model = Join-Path $repository 'src\com\thor\displaypowertest\DashboardStateModel.java'
$test = Join-Path $repository 'tests\DashboardStateModelTest.java'
$warningModel = Join-Path $repository 'src\com\thor\displaypowertest\CpuWarningModel.java'
$warningTest = Join-Path $repository 'tests\CpuWarningModelTest.java'
$evidenceModel = Join-Path $repository 'src\com\thor\displaypowertest\CpuPinningEvidenceModel.java'
$evidenceTest = Join-Path $repository 'tests\CpuPinningEvidenceModelTest.java'
$updateModel = Join-Path $repository 'src\com\thor\displaypowertest\UpdateVersion.java'
$updateTest = Join-Path $repository 'tests\UpdateVersionTest.java'

& javac -source 8 -target 8 -d $output $model $test $warningModel $warningTest $evidenceModel $evidenceTest $updateModel $updateTest
if ($LASTEXITCODE -ne 0) { throw 'Dashboard test compilation failed.' }

& java -cp $output DashboardStateModelTest
if ($LASTEXITCODE -ne 0) { throw 'Dashboard tests failed.' }
& java -cp $output CpuWarningModelTest
if ($LASTEXITCODE -ne 0) { throw 'CPU warning tests failed.' }
& java -cp $output CpuPinningEvidenceModelTest
if ($LASTEXITCODE -ne 0) { throw 'CPU evidence tests failed.' }
& java -cp $output UpdateVersionTest
if ($LASTEXITCODE -ne 0) { throw 'Update version tests failed.' }

```

Status: **SALVAGED / NOT RELEASE BEHAVIOR**. Re-evaluate against current telemetry before integrating.
