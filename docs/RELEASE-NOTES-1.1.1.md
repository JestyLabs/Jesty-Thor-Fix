# Jesty Thor Fix 1.1.1

This hotfix makes the required Dashboard CPU Fix restart predictable.

## What changed

- Holds a timed kernel wake-lock while Qualcomm's display stack and the Android
  framework restart, preventing the device from suspending behind two black
  screens.
- Releases the wake-lock automatically after recovery, with both an explicit
  cleanup and a kernel timeout safety net.
- Corrects the in-app warning: changing AYN Dashboard CPU Fix restarts Android
  and closes open apps.

The True Bottom Display Fix, wake repair, display modes, clock logic, and
hardware-off implementation are unchanged.

## Validation

The exact signed APK was installed in place on the physical Thor.

- CPU Fix OFF and ON each completed one Android framework restart.
- Device uptime continued throughout both transitions: the Thor did not enter
  suspend while its displays were black.
- The transition wake-lock was released after Android recovered.
- The privileged daemon returned after each transition.
- A normal reboot restored the saved CPU Fix setting, performed its one
  expected framework restart, and returned the daemon without a suspend delay.
- True Bottom Display Fix code and wake-repair timing are unchanged.

SHA-256:

```text
C72019B4AD1C3AB0717AC5B963C206960754EDDC362D55D491EACAE8DA04B82F
```
