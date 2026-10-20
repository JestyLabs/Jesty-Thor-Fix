#!/usr/bin/env python3
"""Offline macOS JNI compilation/guard tests: no sockets, process launch helpers or signals."""
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import time


def main():
    bundle = Path(__file__).resolve().parents[2]
    work = bundle / "work"
    build = work / "build" / "native-host"
    logs = bundle / "results" / "logs"
    logs.mkdir(parents=True, exist_ok=True)
    log_path = logs / "native-host.log"
    results_path = logs / "native-host.json"
    results = []
    replacements = [(str(build), "[HOST_TEMP]"), (str(bundle), "[BUNDLE_ROOT]")]
    if os.environ.get("JAVA_HOME"):
        replacements.append((os.environ["JAVA_HOME"], "[HOST_JDK]"))
    if os.environ.get("TMPDIR"):
        replacements.append((os.environ["TMPDIR"].rstrip("/"), "[HOST_TEMP]"))
    replacements.append((str(Path.home()), "[HOST_HOME]"))
    replacements.sort(key=lambda item: len(item[0]), reverse=True)

    def sanitize(value):
        for original, replacement in replacements:
            value = value.replace(original, replacement)
        return value

    with log_path.open("w", encoding="utf-8") as log:
        def run(arguments, label, environment=None, expected_failure=None):
            command = sanitize(shlex.join(map(str, arguments)))
            log.write("$ " + command + "\n")
            log.flush()
            started = time.monotonic()
            completed = subprocess.run(list(map(str, arguments)), cwd=bundle,
                                       env=environment, text=True, stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, timeout=90)
            log.write(sanitize(completed.stdout))
            log.write("exit=" + str(completed.returncode) + "\n")
            log.flush()
            success = completed.returncode == 0
            if expected_failure is not None:
                success = completed.returncode != 0 and expected_failure in completed.stdout
            results.append({"name": label, "command": command,
                            "exit": completed.returncode, "tier": "HOST",
                            "outcome": "PASS" if success else "FAIL",
                            "negative_control": expected_failure is not None,
                            "seconds": round(time.monotonic() - started, 3)})
            if not success:
                raise RuntimeError(label + " failed; see " + sanitize(str(log_path)))

        try:
            if sys.platform != "darwin":
                raise RuntimeError("This focused unsupported-platform runner requires macOS")
            java_home = Path(os.environ["JAVA_HOME"])
            java = java_home / "bin" / "java"
            javac = java_home / "bin" / "javac"
            run(["cc", "--version"], "existing-c-compiler")
            run([java, "-version"], "existing-jdk")
            if build.exists():
                shutil.rmtree(build)
            build.mkdir(parents=True)
            classes = build / "classes"
            classes.mkdir()
            compiler = ["cc", "-std=c11", "-Wall", "-Wextra", "-Werror", "-O2",
                        "-fPIC", "-dynamiclib", "-pthread",
                        "-I" + str(java_home / "include"),
                        "-I" + str(java_home / "include" / "darwin")]
            source = work / "native" / "retirement.c"
            run(compiler + [source, "-o", build / "libretirement.dylib"],
                "actual-native-strict-compile")
            sources = [work / "src" / "org" / "example" / "panelservice" / "RetirementNative.java"]
            sources += sorted((work / "native" / "tests").rglob("*.java"))
            run([javac, "-encoding", "UTF-8", "-Xlint:all", "-Werror",
                 "-classpath", classes, "-d", classes] + sources,
                "actual-java-adapter-compile-without-android-stubs")
            entry = "com.thor.displaypowertest.RetirementNativeHostTest"
            run([java, "-Djava.library.path=" + str(build), "-cp", classes, entry, "native"],
                "actual-jni-unsupported-and-input-guards")
            run([java, "-Djava.library.path=" + str(build / "missing"),
                 "-cp", classes, entry, "missing-library"], "loader-fails-closed")
            environment = os.environ.copy()
            environment.pop("CLASSPATH", None)
            run([java, "-cp", classes, entry, "invalid-helper"],
                "helper-missing-classpath", environment)
            environment["CLASSPATH"] = "relative.apk"
            run([java, "-cp", classes, entry, "relative-helper"],
                "helper-relative-classpath", environment)
            environment["CLASSPATH"] = "first.apk:second.apk"
            run([java, "-cp", classes, entry, "invalid-helper"],
                "helper-multiple-classpath", environment)

            boundary = build / "request-boundary"
            boundary.mkdir()
            run(compiler + [work / "native" / "tests" / "request_boundary.c",
                            "-o", boundary / "libretirement.dylib"],
                "actual-native-request-inert-os-compile")
            run([java, "-Djava.library.path=" + str(boundary), "-cp", classes,
                 "com.thor.displaypowertest.RetirementRequestHostTest"],
                "actual-native-request-byte-framing-shared-deadline-inert-os")
            run([java, "-Djava.library.path=" + str(boundary), "-cp", classes,
                 "com.thor.displaypowertest.RetirementImageHostTest"],
                "actual-native-cmdline-prefix-parser-inert-input")
            run([java, "-Djava.library.path=" + str(boundary), "-cp", classes,
                 "com.thor.displaypowertest.RetirementConnectHostTest"],
                "actual-native-connect-two-roles-inert-metadata")

            mutant = build / "ascii-negative-control.c"
            text = source.read_text(encoding="utf-8")
            original = "characters[i] < 0x20 || characters[i] > 0x7e"
            if text.count(original) != 1:
                raise RuntimeError("Negative-control anchor changed")
            mutant.write_text(text.replace(original, "characters[i] > 0x7e"), encoding="utf-8")
            mutant_directory = build / "negative-control"
            mutant_directory.mkdir()
            run(compiler + [mutant, "-o", mutant_directory / "libretirement.dylib"],
                "ascii-negative-control-compiles")
            run([java, "-Djava.library.path=" + str(mutant_directory), "-cp", classes, entry, "native"],
                "ascii-negative-control-detected",
                expected_failure="Expected guard 'Only printable ASCII'")
            image_anchor = "if (argument == 2 && (size != 1 || value[0] != 'D')) return 0;"
            if text.count(image_anchor) != 1:
                raise RuntimeError("Image negative-control anchor changed")
            image_mutant = build / "image-negative-control.c"
            image_mutant.write_text(text.replace(image_anchor, "/* Deliberate HOST negative control. */"),
                                    encoding="utf-8")
            boundary_text = (work / "native" / "tests" / "request_boundary.c").read_text(encoding="utf-8")
            include_anchor = '#include "../retirement.c"'
            if boundary_text.count(include_anchor) != 1:
                raise RuntimeError("Image negative-control include anchor changed")
            image_boundary = build / "image-negative-boundary.c"
            image_boundary.write_text(boundary_text.replace(include_anchor,
                                      '#include "' + str(image_mutant) + '"'), encoding="utf-8")
            image_directory = build / "image-negative-control"
            image_directory.mkdir()
            run(compiler + [image_boundary, "-o", image_directory / "libretirement.dylib"],
                "cmdline-negative-control-compiles")
            run([java, "-Djava.library.path=" + str(image_directory), "-cp", classes,
                 "com.thor.displaypowertest.RetirementImageHostTest"],
                "cmdline-negative-control-detected",
                expected_failure="Root helper is not daemon D")
            connect_anchor = "&& strcmp(path, LEGACY_ENDPOINT) == 0"
            if text.count(connect_anchor) != 1:
                raise RuntimeError("Connect negative-control anchor changed")
            connect_mutant = build / "connect-negative-control.c"
            connect_mutant.write_text(text.replace(connect_anchor, "&& path[0] != '\\0'"),
                                      encoding="utf-8")
            connect_boundary = build / "connect-negative-boundary.c"
            connect_boundary.write_text(boundary_text.replace(include_anchor,
                                        '#include "' + str(connect_mutant) + '"'), encoding="utf-8")
            connect_directory = build / "connect-negative-control"
            connect_directory.mkdir()
            run(compiler + [connect_boundary, "-o", connect_directory / "libretirement.dylib"],
                "connect-negative-control-compiles")
            run([java, "-Djava.library.path=" + str(connect_directory), "-cp", classes,
                 "com.thor.displaypowertest.RetirementConnectHostTest"],
                "connect-negative-control-detected",
                expected_failure="App path must be exact legacy socket")
            results.extend([
                {"name": "Linux-Android-SO_PEERCRED-SCM_RIGHTS-pidfd",
                 "tier": "ANDROID", "outcome": "NOT RUN",
                 "reason": "Forbidden Android execution; macOS cannot prove Linux syscalls"},
                {"name": "NDK-four-ABI-APK-packaging",
                 "tier": "ANDROID-BUILD", "outcome": "NOT RUN",
                 "reason": "No Android build, NDK setup, PowerShell or APK execution permitted"},
            ])
            print("PASS native HOST checks; actual JNI guards and compiled negative control")
        except Exception as failure:
            log.write("FAIL: " + sanitize(str(failure)) + "\n")
            if not results or results[-1]["outcome"] != "FAIL":
                results.append({"name": "host-runner", "tier": "HOST",
                                "outcome": "FAIL", "reason": sanitize(str(failure))})
            raise
        finally:
            results_path.write_text(sanitize(json.dumps(results, indent=2)) + "\n", encoding="utf-8")
            if build.exists():
                shutil.rmtree(build)


if __name__ == "__main__":
    main()
