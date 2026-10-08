# Thor .377: exact kernel RAM command path, 2026-10-08

Local module analysis, followed by an owner-authorized wake and one bounded
three-second passive Perfetto attempt at 60/60. No refresh, brightness or display
power writes; no APK installation, reboot or manual service restart.

## Identity and address convention

The kernel reports 5.15.123-android13-8-gafd857749d1f. `/proc/kallsyms` attributes
the RAM handlers to `msm_drm`. The file copied from `/vendor/lib/modules/msm_drm.ko`
has device/host SHA-256:

`fe9b1db10f0c6e0bc09c156448f82de913bb54477bbd24fc188aa5b5b1503de1`

ELF Build ID `9db0622654ede32a74eb79dced22d64c5e4a68b1` matches the note read
from `/sys/module/msm_drm/notes/.note.gnu.build-id`. Vermagic/scmversion identifies
the same kernel revision. This binds the inspected file to the loaded module
identity, without authenticating a firmware image or comparing relocated memory.

This is AArch64 ET_REL. Offsets below are within `.text`, whose file offset is
`0x95000`; they are not runtime addresses or linked virtual addresses. Relocation
records resolve callee/global/string references. An unresolved self-branching
`bl` instruction is not interpreted as recursion. Raw files and disassembly stay
outside Git in `C:\Temp\thor-refresh-kernel-20261008`.

## PROVEN: sysfs reads a shadow byte

`bypass_ram_show` (`.text+0x170ef0`, size 0x3c) loads global `bypass_ram` at
`0x170f00/0x170f10`, then formats `%d\n` with sprintf. It never queries the panel.
The global occupies `.bss+0x5068`, size1, initially zero. Relocation references
identify reads in show/prepare and writes in store; this scan does not exclude
all possible indirect writes.

A read of 0 therefore reports a stored software selection/default, not controller
readback, successful PASS execution, optical brightness, cadence or consumption.
It need not track the framework's current refresh policy.

## PROVEN: store discards transfer failure

`bypass_ram_store` (`0x170f2c`, size 0xe4):

| Offset | Operation |
|---|---|
| `0x170f48..0x170f70` | parse literal `0x%x`; failed conversion logs and returns -22 |
| `0x170f74..0x170f80` | select parsed 0/1; other parsed values return byte count without switching |
| `0x170fc8/0x170fcc` | call switch 0 |
| `0x170fd0..0x170fe0` | log store 0 and save byte 0 without testing switch result |
| `0x170fe8..0x171004` | call switch 1, log store 1, save byte 1 without testing switch result |
| `0x170f98` | return input byte count |

The exact services.jar callback constructs `echo 0x%1$s > %2$s`, compatible with
the parser, choosing 0 for rounded peak>=110 and 1 otherwise. These command strings
were inspected as data; no such command was executed in this session.

Successful echo/sysfs status plus matching readback does not prove DSI success.
This is an observability limit, not evidence of an error in the historical probe.

## PROVEN: secondary identity, support guard and command mapping

`dsi_panel_get` compares the type string to `secondary` and saves that panel in
global `sec_panel` (`0x16b9f4..0x16ba4c`). `dsi_panel_parse_misc_features` reads
`qcom,mdss-dsi-bypass-ram-switch` and stores its boolean at panel +0x848
(`0x16f8d8..0x16f8f4`). The property exists in the live CH13726A node.

`dsi_panel_switch_bypass_ram` (`0x171010`, size 0xec):

- missing secondary panel returns -22;
- otherwise locks panel +0x3f0 and checks support byte +0x848;
- false support returns 0 without transfer;
- value 1 selects command 25 at `0x171054/0x171058`;
- value 0 selects command 26 at `0x1710a4/0x1710a8`;
- nonzero transfer results are retained/logged; the helper unlocks and returns
  its result, which store above discards.

The `.data` object `cmd_set_prop_map` starts at 0x6e28. Relocations at 0x6ef0/0x6ef8
are entries 25/26, resolving to the properties below. Payloads were read from the
live CH13726A node; both command states specify `dsi_lp_mode`.

| Selection | Set | Property | Configured payload sequence |
|---|---|---|---|
| 1 | 25 / BYPASS | `qcom,mdss-dsi-bypass-ram-command` | `28 00`, `f0 50`, `b9 00`, `29 00` |
| 0 | 26 / PASS | `qcom,mdss-dsi-ram-command` | `28 00`, `f0 50`, `b9 11`, `29 00` |

`dsi_panel_tx_cmd_set` (`0x170b80`, size 0x370) indexes the current command table,
calls `dsi_host_transfer_sub` at 0x170db4 and checks its sign at 0x170db8. Invalid
panel/current-mode pointers return-22; zero command count returns 0 without
transfer. Software success is therefore not an optical measurement or, by
itself, proof of a nonempty transfer. No controller datasheet semantics are assumed.

## PROVEN: panel preparation replays selection and also masks failure

`dsi_panel_prepare` (`0x175040`, size 0x130), after successful PRE_ON:

1. compares the panel type to `secondary` (`0x1750f0..0x175100`);
2. reads global `bypass_ram` (`0x17512c/0x175130`);
3. logs `oscar bypass_ram 0` and selects 26 for 0, or logs 1 and selects 25;
4. loads `sec_panel`, calls tx_cmd_set at 0x175168;
5. branches to0x175104, setting success 0 without testing the transfer result.

This direct call bypasses the switch helper's support-byte guard. Preparation can
replay the stored choice independently of a refresh observer callback.

OBSERVED: the existing current-boot kernel log contains the prepare message 0 at
uptime 10.264898, 97.420257 and 8937.201452. Near the last one it reports a lower
1080x1240/fps60 mode and subsequent DSI_CMD_SET_ON. These support execution of the
prepare branch; the log precedes transfer and does not prove result or ACK.

INFERRED: zero initialization and prepare replay explain how selection 0 can
coexist with 60/60 without requiring a new policy write. Every store in this boot
has not been reconstructed. No speculative correction of 0 is justified.

## Sleep observation and authorized wake

The owner confirmed normal sleep, both screens visibly off, with the fix ON.
A fresh read showed Android Asleep, both logical displays OFF, TOP CRTC181
inactive and BOTTOM CRTC243 active, in BOTH mode 0 with policy 60/60. No tracing
had been activated. Sleep was not treated as an awake refresh sample.

The v1.7.0 `DisplayDecisionModel.reconcile` requests bottom OFF only in TOP mode 1
with both CRTCs active. In BOTH/top0/bottom1 it returns NONE. The daemon exists
and its boot ledger reached BOOT_READY. The lower CRTC bit alone is not evidence
of failure of this TOP-only feature.

Unknown: lower electrical scanning, retained image versus black frames, panel
rail state and extra power during sleep. Neither optical black nor active=1 proves
these. Do not label it an AYN black overlay or infer zero consumption. The owner
asked to discuss sleep separately after this investigation; sleep logic is unchanged.

The owner then explicitly authorized wake. KEYCODE_WAKEUP produced Android Awake,
BOTH and both CRTCs active at60, with the same boot/SF/composer identities.

## Passive trace attempt: no usable events

The kernel exposes drm_vblank_event with crtc/seq/time/high_prec and
dma_fence_signaled with driver/timeline/context/seqno. The module's
`sde_crtc_vblank_cb` (`0x6b520`) calls drm_crtc_handle_vblank at 0x6b568. These
are candidate software observations, distinct from optical frames/cadence.

An isolated ftrace instance was planned. Shell lacks write access to the instance
directory. `service call` could not invoke the Binder although check/list found
it. A 2368-byte temporary app_process helper submitted the app's same Parcel
contract (transaction 0, string array); transact returned true, but no output file
or instance appeared. This does not prove execution or identify why it failed.
The temporary helper was removed after checking its hash. No app was installed.

The stock Perfetto CLI then ran one session lasting 3 seconds with a 1 MiB buffer
and four DRM/fence events. It produced 889 bytes. Official local Trace Processor
v58.2 confirmed **zero ftrace events**, so no CRTC cadence or fence result is
claimed. Producer logs contain repeated starts/resets and a translation-table
error for `f2fs_truncate_partial_nodes.nid` (`nid_t nid[3]`). Their causal role
in the empty trace remains unproven; no service restart/workaround was attempted.

Afterward: tracing_on=0, no instances, policy 60/60, same boot/SF/composer identities.
The capture failure does not establish display failure. The old 120 Hz trace still
lacks per-panel returns/ACKs; the cause of tearing and optical cadence remain open.

Raw trace, logs, module and derived reports stay outside Git. The processor was
downloaded via the [official launcher](https://get.perfetto.dev/trace_processor)
manifest and its SHA-256 verified before local execution:
`adfa6bad3d72be3ba9b83fa2b17b69fa13b3ab1cad0f42e52b86188bd5f0f997`.
No trace was uploaded.

## Local tool validation

`inspect-thor-refresh-elf.py --function NAME` supports section-aware AArch64
ET_REL disassembly with relocations/rodata previews; linked-VA --disassemble
rejects ET_REL. Existing SF disassembly was rechecked. The synthetic module test
uses identical function offsets in different sections with different relocation
targets and checks section/file-offset resolution and invalid-input rejection.
It uses the existing offline pyelftools/capstone; CI downloads no new dependencies.

```powershell
python scripts/inspect-thor-refresh-elf.py C:\Temp\thor-refresh-kernel-20261008\msm_drm.ko --deps C:\Temp\thor-re-tools --function bypass_ram_store --output C:\Temp\thor-refresh-kernel-20261008\verified-store.txt
python scripts/test-thor-refresh-elf.py --deps C:\Temp\thor-re-tools
```
