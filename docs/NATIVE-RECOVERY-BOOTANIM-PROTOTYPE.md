# Native recovery bootanimation prototype

Status: **prototype only / no release behavior**

Branch:

`work/thor-recovery-native-bootanim-prototype`

This path exists because the raw SurfaceControl recovery experiments established
two physical limitations:

1. early stack-0 content can be consumed by both Thor panels during framework
   recovery;
2. a Java/app_process SurfaceControl path still did not commit useful pixels
   until roughly three seconds after replacement SurfaceFlinger was observed.

## Device evidence

The tested Thor supplied:

- `/system/bin/bootanimation`
- `/system/lib64/libbootanimation.so`
- `/product/media/bootanimation.zip`

The inspected library is Android 33, ARM64, stripped, with Build ID:

`caf8373d40733cb85550e064221cd81c`

SHA-256:

`96c17523a69335330306c0cca8df395a869c80b69ea447938a26d8089e645bfa`

The Thor library contains and uses all of these strings/entry points:

- `persist.sys.customanim.boot`
- `persist.sys.customanim.shutdown`
- `persist.service.bootanim.displays`
- `/product/media/bootanimation.zip`
- `/apex/com.android.bootanimation/etc/bootanimation.zip`
- `SurfaceComposerClient::getInternalDisplayToken()`
- `SurfaceComposerClient::getPhysicalDisplayIds()`
- `Transaction::setDisplayLayerStack(...)`
- `Transaction::setLayerStack(...)`

Disassembly of `BootAnimation::findBootAnimationFile()` proves that the boot
customization property is read as a path, checked with `access(..., R_OK)`,
and selected as the animation file when readable. This is a vendor capability
on the tested firmware; the prototype does not modify `/product`.

Disassembly of `BootAnimation::readyToRun()` shows that it obtains the
internal display token first. The optional multi-display path is controlled by
`persist.service.bootanim.displays`. The prototype requires that property to
remain empty and never mutates it.

## Stock animation evidence

The supplied stock ZIP is SHA-256:

`7dc3f8327a28181f38557661f06fb3ed08785a901401d2d13e6ffe8c175c5ea5`

Its `desc.txt` is:

```text
1920 1080 30
p 1 1 part0
p 0 0 part1
```

It contains:

- 200 PNG frames in `part0`;
- 1 PNG frame in `part1`;
- 1920x1080 frames;
- every ZIP entry STORED, not deflated.

## Prototype design

Activation marker:

`/data/local/tmp/thor-recovery-native-bootanim-prototype`

Exact contents must be `1`.

When the CPU restart reaches the already-proven durable
`RESTART_REQUESTED` boundary:

1. verify native prototype marker;
2. require `persist.service.bootanim.displays` to be empty;
3. require `debug.sf.nobootanimation` not to be `1`;
4. save the exact previous `persist.sys.customanim.boot` value;
5. build a one-frame 1920x1080 STORED animation at
   `/dev/jesty-thor-recovery-bootanimation.zip`;
6. set `persist.sys.customanim.boot` to that path and verify readback;
7. do **not** arm the ordinary second-animation suppression;
8. perform the existing controlled composer restart unchanged;
9. let SurfaceFlinger/init start the normal native bootanimation service;
10. observe native bootanimation PID timing;
11. helper restores the exact previous custom-animation property and removes
    the `/dev` ZIP on every normal/abnormal helper exit path.

The generated frame uses the packaged Jesty Thor Fix lockup centered on a
1920x1080 dark background. No text/font rendering is used.

## Fail-open rules

Any native-prototype preparation failure falls back to the already physically
validated second-boot-animation suppression path.

The native path is rejected when:

- multi-display bootanimation property is non-empty;
- bootanimation is already explicitly disabled;
- prior custom path cannot be represented safely in the helper;
- asset generation fails;
- property set/readback fails.

No failure in this cosmetic prototype may alter CPU restart provenance.

## Crash containment

The custom animation file lives in `/dev`, not `/product` or persistent app
storage.

A real device reboot recreates `/dev`; therefore even if a process dies after
setting the persistent custom-path property, the next cold boot sees a missing
override file and the tested library's normal readable-file check falls back to
the stock animation paths.

The helper still restores the property and removes the file during the ordinary
same-boot recovery path.

## Not yet proven

A physical boot is still required to determine:

- when the native animation first becomes visible relative to replacement
  SurfaceFlinger;
- whether it appears earlier than the raw SurfaceControl prototype;
- whether the native internal-display route remains TOP-only while the lower
  physical display temporarily consumes stack 0;
- whether the custom frame remains centered at the expected scale;
- whether cleanup leaves no custom property/file behind.

Do not promote this path to release behavior until those points are physically
measured.
