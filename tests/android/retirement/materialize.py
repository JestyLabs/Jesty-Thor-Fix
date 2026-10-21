#!/usr/bin/env python3
"""Source-only owner fixture preparation. Never builds or executes Android/root.

Copies an explicit real-source allowlist and records hashes. The only product
source substitutions are the disposable package/path/JNI namespace. Synthetic
hardware/boot/main boundaries are separate, explicitly named fixture sources.
No whole LiveRetirement, PServer, server or producer replacement is permitted.
"""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import uuid


HERE = Path(__file__).resolve().parent
WORK = HERE.parents[2]
BUNDLE = WORK.parent
PUBLISHED = WORK / "tests/retirement/published"
ORIGINAL = "com.thor.displaypowertest"
DISPOSABLE = "org.example.rfixture"
PACKAGE = Path("com/thor/displaypowertest")
SERVER = (
    "DaemonState", "DaemonCommandHandler", "DaemonIdentity", "DisplayDecisionModel",
    "DaemonIpcServer", "SecureChannel", "IpcPeerPolicy", "DaemonLaunchModel",
)
APP = (
    "AutoService", "PServer", "LiveRetirement", "RetirementNative",
    "LiveHandoverPolicy", "PreviousSecureDaemonIdentity", "RebootActivation",
    "RebootActivationPolicy", "DaemonLaunchScript", "SocketClient",
    "DaemonResponsePolicy", "LegacyDaemonIdentity", "BridgeFailureDiagnostic",
    "DashboardRenderer", "DashboardStateModel", "CpuWarningModel", "TelemetryValues",
    "DashboardLayout", "DashboardViews", "DashboardStyle", "DashboardSettingsController",
)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def materialize(version, destination):
    if destination.exists() or destination.is_symlink():
        raise ValueError("Refusing an existing materialization destination")
    if WORK.resolve() not in destination.resolve().parents:
        raise ValueError("Source output must remain beneath work/")
    entries = []
    destination.mkdir(parents=True)
    try:
        def copy(source, target, *, native=False):
            original = source.read_bytes()
            text = original.decode("utf-8").replace(ORIGINAL, DISPOSABLE)
            if native:
                text = text.replace("Java_com_thor_displaypowertest_RetirementNative_",
                                    "Java_org_example_rfixture_RetirementNative_")
            data = text.encode("utf-8")
            output = destination / target
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(data)
            entries.append({
                "input": source.relative_to(BUNDLE).as_posix(),
                "output": target.as_posix(),
                "input_sha256": sha256(original),
                "output_sha256": sha256(data),
                "namespace_only": True,
            })

        output_package = Path("org/example/rfixture")
        for name in SERVER:
            copy(PUBLISHED / version / "src" / PACKAGE / (name + ".java"),
                 Path("predecessor/src") / output_package / (name + ".java"))
        for name in dict.fromkeys((*SERVER, *APP)):
            copy(WORK / "src" / PACKAGE / (name + ".java"),
                 Path("candidate/src") / output_package / (name + ".java"))
        copy(WORK / "src/R.java", Path("candidate/src/R.java"))
        copy(HERE / "src/D.java", Path("candidate/src/D.java"))
        copy(HERE / "src/D.java", Path("predecessor/src/D.java"))
        copy(HERE / "src" / PACKAGE / "RetirementFixtureHardware.java",
             Path("predecessor/src") / output_package / "RetirementFixtureHardware.java")
        for name in ("RetirementFixtureHardware", "RetirementFixtureActivity", "RetirementFixtureMailbox",
                     "RetirementFixtureInstrumentation"):
            copy(HERE / "src" / PACKAGE / (name + ".java"),
                 Path("candidate/src") / output_package / (name + ".java"))
        copy(HERE / "AndroidManifest.xml", Path("candidate/AndroidManifest.xml"))
        copy(WORK / "native/retirement.c", Path("candidate/native/retirement.c"), native=True)
        provenance = {
            "status": "SOURCE_MATERIALIZED_PLATFORM_NOT_RUN",
            "predecessor": version,
            "disposable_package": DISPOSABLE,
            "product_transformations": [
                ORIGINAL + " -> " + DISPOSABLE + " (Java package/import/path/fixed helper command)",
                "Java_com_thor_displaypowertest_RetirementNative_ -> "
                "Java_org_example_rfixture_RetirementNative_ (JNI symbols)",
            ],
            "synthetic_boundaries": [
                "hardware/CPU/Guard/watcher and boot phase",
                "default-package D entry, old 1 run 0 0 / successor 1 hold 0 0",
                "RootLogFiles uses /dev/null; EarlyCpuOptIn disabled",
            ],
            "not_run": ["Android compilation", "ABI/JNI loading", "Binder/root helper",
                        "actual old server", "process exit", "successor READY", "device"],
            "files": entries,
        }
        (destination / "SOURCE-MAP.json").write_text(json.dumps(provenance, indent=2) + "\n",
                                                    encoding="utf-8")
        return provenance
    except BaseException:
        shutil.rmtree(destination)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--predecessor", required=True, choices=("1.7.1", "1.7.2", "1.7.3", "1.7.4"))
    parser.add_argument("--output-relative-to-work")
    parser.add_argument("--verify-only", action="store_true",
                        help="verify namespace-only source copies, then remove generated files")
    args = parser.parse_args()
    relative = args.output_relative_to_work or (".owner-retirement-source-" + uuid.uuid4().hex)
    if Path(relative).is_absolute():
        parser.error("Use a new relative output beneath work/")
    destination = WORK / relative
    provenance = materialize(args.predecessor, destination)
    if args.verify_only:
        try:
            for entry in provenance["files"]:
                original = (BUNDLE / entry["input"]).read_bytes()
                produced = (destination / entry["output"]).read_bytes()
                expected = original.decode("utf-8").replace(ORIGINAL, DISPOSABLE)
                if entry["output"].endswith("/retirement.c"):
                    expected = expected.replace("Java_com_thor_displaypowertest_RetirementNative_",
                                                "Java_org_example_rfixture_RetirementNative_")
                if (sha256(original) != entry["input_sha256"]
                        or sha256(produced) != entry["output_sha256"]
                        or produced != expected.encode("utf-8")):
                    raise ValueError("Non-namespace source difference: " + entry["output"])
            producer = (destination / "predecessor/src/org/example/rfixture/DaemonState.java").read_text()
            if (";runtime_version=" in producer) != (args.predecessor != "1.7.1"):
                raise ValueError("Unexpected actual published Q format")
            if not (destination / "predecessor/src/D.java").is_file():
                raise ValueError("Old fixture must retain the default D entry/image guard")
        finally:
            shutil.rmtree(destination)
        print("PASS SOURCE_ONLY predecessor=" + args.predecessor
              + " exact-source-files=" + str(len(provenance["files"]))
              + "; generated source copies removed")
        print("NOT RUN: Android/build/APK/ABI/JNI/Binder/root/old server/READY/device")
        return 0
    print("SOURCE ONLY: " + str(destination.relative_to(WORK)))
    print("Exact-source hash map: SOURCE-MAP.json; files=" + str(len(provenance["files"])))
    print("NOT RUN: Android/build/APK/native/root/Binder/device; no installations/network")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
