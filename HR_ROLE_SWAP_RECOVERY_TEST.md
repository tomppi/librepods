# Heart-rate role/swap recovery test

This build removes the experimental "Make remaining earbud host" command sender. LibrePods no longer sends Bud Role control `0x08` to force left/right primary.

Instead it passively watches AirPods role/swap traffic and recovers heart-rate streaming when the primary/host bud appears to change.

## Kept

- HRM `0x30` OFF sends `02 00 00 00`.
- Heart-rate auto-start toggle remains.
- Normal auto-start delay remains 10 seconds.
- Heart rate is not stopped just because an earbud is removed.
- HR/AACP quarantine remains removed/no-op.

## Removed

- Heart Rate menu toggle: `Make remaining earbud host`.
- Preference key handling for `heart_rate_host_remaining_bud`.
- Outgoing Bud Role control requests:
  - `04 00 04 00 09 00 08 01 00 00 00`
  - `04 00 04 00 09 00 08 02 00 00 00`

## Added

LibrePods now logs and reacts to incoming role/swap packets:

- AACP `0x08` Bud Role
- AACP `0x47` Bud Swap 2.0 Procedure
- AACP `0x48` Swap Imminent Confirm
- AACP `0x49` Bud Swap 2.0 Completion
- AACP `0x4A` Swap Complete Confirm

When HR is requested and a primary-role change or bud-swap event is seen, LibrePods schedules a quick recovery:

1. Wait 1500 ms for the AirPods role swap to settle.
2. Send RTBuddy HR stop + HRM `0x30` OFF.
3. Wait 500 ms.
4. Send HRM `0x30` ON + RTBuddy HR start.

This recovery path deliberately does **not** use the normal 10-second auto-start delay.

## Log markers

```text
HR-ROLE-STATE RX bud_role ...
HR-ROLE-STATE RX bud_swap ...
HR-ROLE-STATE bud_role previous=... current=...
HR-ROLE-STATE bud_swap_event name=...
HR-ROLE-RECOVERY scheduled in 1500ms: reason=...
HR-ROLE-RECOVERY restarting heart-rate stream after role/swap event: reason=...
HR-ROLE-RECOVERY stop/off sent before restart: stopped=...
HR-ROLE-RECOVERY restart result started=...
```
