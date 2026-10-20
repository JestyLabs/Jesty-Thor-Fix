# Retirement primitive boundary

This is the bounded S2 adapter, not an independent authorization policy. Java
orchestration must authenticate both control peers, validate fresh identity and
policy replies, commit/cancel the request, and require its fresh EXIT receipt.
Every returned descriptor is caller-owned and must be closed exactly once.
`bindProcess` acquires the descriptor **before** the identity command; callers
must prove the subsequent I.pid against the socket kernel PID. Each later I/Q
connection must pass `requireSameProcess` against that retained descriptor.
`request(socket, command, timeout)` admits only `I` or `Q`, writes exactly one
command byte **without LF**, then reads one printable-ASCII LF reply bounded at
16384 characters. Send and reply share one absolute deadline; this is distinct
from LF-framed control-channel `writeLine`. Bind must precede the first `I`.

The library never changes credentials and has no numeric-PID signal fallback.
Only a root helper can bind/signal a root daemon peer with PID greater than 100.
The existing process-image gate is preserved: `/proc/<authenticated-kernel-pid>`
and its `cmdline` must remain root-owned; bounded NUL-separated argv must begin
`app_process`, `/`, `D`, `[01]`, `hold|run`, `[01]`, `[01]`. Extra trailing argv
retains the old prefix semantics. No process scan or supplied PID is used.
The pidfd is acquired before the first read, checked alive before/after it,
and its original PID is retained in the ownership table. Bind and every
`requireSameProcess` recheck the image (1000ms bound); signal repeats it against
that saved PID within the exit deadline, before issuing any signal. Unreadable,
changed, oversized or mismatched cmdline fails closed.
Direct syscalls are fail-closed when kernel/API/SELinux permissions are missing.
SCM_RIGHTS preserves the original app-authenticated socket peer credentials.
Only SIGTERM on the retained descriptor is available; no signal is retried by PID.
Pending control bytes, cancellation/EOF, pre-existing target exit, or missing
descriptor exit evidence fail rather than producing success. The caller alone
constructs the receipt after native success; native success is not successor READY.

## Endpoint and resource ownership

Integration found and corrected a concrete wiring defect: the initial root-only,
nonce-only native connect rejected every legitimate app-to-legacy socket call.
Io-stub controller passes did not cover that native admission behavior.

`connect` has exactly two roles, both using the same deadline/nonblocking
connect/SO_ERROR and endpoint-inode validation:

- The actual app real/effective UID, derived from `applicationUid()`, may connect
  with expected UID 0 only to
  `/data/user/0/com.thor.displaypowertest/files/jesty-thor-control-v2.sock`.
  Its peer must be root with PID >100. This preserves legacy app-UID admission.
- A real/effective root helper may connect with expected UID equal to that
  derived app UID only to the fixed app files directory's nonce endpoint.

Both socket files must be app-owned, mode 0600, nonsymlink sockets with stable
dev/inode before/after connect; their directory remains private and app-owned.
No other role, arbitrary path, supplied UID or supplied PID is admitted.

The control name is a private app-directory path ending `retire-<32 lowercase hex>`.
`applicationUid()` derives its UID from the fixed neutral installed package
directory `/data/user/0/com.thor.displaypowertest` and its `files` child. Both
must be nonsymlink directories, share a positive owner UID and have no
group/world write bits. Opened descriptors are checked against the lstat
identities. Listener creation additionally requires both current real/effective
UIDs to match that owner; no supplied command-line UID is trusted for discovery.
Its directory is checked with lstat/open/fstat for expected UID and private mode.
An existing path is never unlinked to make bind succeed. Bind saves the
**filesystem** socket dev/inode and keeps its directory descriptor. This is
separate from the socket descriptor's dev/inode. A bounded internal table owns
up to 16 listeners and 16 process descriptors.

Use `cleanupServer(listener, originalPath)` in `finally`, without closing the
listener first. With a matching path it consumes the listener even if unlink
fails, reports failure, and never unlinks a replacement inode. A wrong path is
rejected without consuming ownership. `close(listener)` also performs owned
cleanup; do not call both. Ordinary descriptor closes are not retried after EINTR.
Process operations duplicate the retained pidfd under the ownership mutex, so
concurrent cleanup cannot redirect a signal to a recycled descriptor.

All I/O has an absolute monotonic deadline (positive timeout, maximum 12000ms),
with nonblocking sends/receives,
bounded printable-ASCII LF frames (16384 maximum), and no read-ahead into the
next frame. SCM_RIGHTS admits one `F` marker and exactly one connected Unix
stream fd; truncated/extra ancillary descriptors are closed on rejection.
Descriptors are CLOEXEC. No global umask, signal disposition or UID is changed.
The app-private directory is the pathname trust boundary: another actor with
the same UID, or root, must not concurrently manipulate it. Native cleanup
cannot make compare-and-unlink atomic against such a privileged directory owner.

## Owner build prerequisite — NOT RUN here

`build.ps1 -AndroidNdk <existing-ndk-path>` requires an **already installed**
NDK **27.2.12479018**. `scripts/build-retirement-native.ps1` checks its
`source.properties`, compiles source for API25 and arm64-v8a, armeabi-v7a, x86,
x86_64, and stages `lib/<abi>/libretirement.so` without changing `apk/` sources.
No setup/download is performed. Existing Windows PowerShell/SDK/JDK/apktool
build prerequisites remain unchanged. The manifest requests extraction.

App load uses `System.loadLibrary("retirement")`. Helper load accepts only one
absolute existing APK in CLASSPATH, selects installed sibling
`lib/{arm64,arm,x86_64,x86}/libretirement.so` using Android Process.is64Bit and
Build's matching supported ABI list, and trusts only successful `System.load`.
Those Android APIs are reflected only inside `loadHelper`; class lookup,
reflection or runtime ABI failures become IOException. The adapter compiles on
JDK17 without Android classes or inert Android stubs.
No guessed extracted path or success fallback is used. Owner integration must
verify actual package-manager extraction, each runtime ABI and app_process
bitness, SELinux/root contexts, and failure-to-HOLD behavior.

## Focused offline HOST evidence

With the existing macOS compiler, Python and JDK17:

```
python3 work/native/test_host.py
```

It compiles the real JNI source with `-Wall -Wextra -Werror` and the actual Java
adapter without Android classes/stubs. It executes native
unsupported-platform/input guards and loader failure cases, then compiles and
detects an ASCII-guard-removal negative control. It opens no socket, starts no
helper, sends no signal and performs no Android build/execution. Logs are
`results/logs/native-host.{log,json}`; generated libraries/classes are removed.
Saved logs replace bundle paths with `[BUNDLE_ROOT]`, HOST scratch outputs with
`[HOST_TEMP]`, and local JDK/home prefixes with nonidentifying placeholders.
An additional focused library includes the actual native source with inert
send/recv/poll/socket-metadata/clock boundaries. It verifies exactly one I/Q byte
without LF, reply bounds/malformed/EOF, no read-ahead and one deadline spanning
send and reply; this is product-method evidence, not real Unix transport evidence.
The same HOST fixture calls the actual native argv parser with inert bytes.
Removing its D-class guard compiles successfully and fails the intended
root-helper rejection assertion; this negative control is not a compile failure.
Linux `/proc` ownership/read/liveness enforcement remains NOT RUN.
It also tests the actual connect role/path and stat/peer admission helpers with
inert metadata, including successful app-to-legacy and root-to-control roles.
Removing the fixed legacy path guard compiles and fails its intended assertion.
These tests do not execute Linux `connect` or prove real socket credentials.
This does **not** prove Linux credentials, ancillary fd transfer, pidfd syscalls,
Android extraction, APK packaging, or device behavior. Those are NOT RUN.
