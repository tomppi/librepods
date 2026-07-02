# HR auto-start when safe test

Base: user-provided `test.zip`.

Changes:

- Adds a Heart Rate screen toggle: `Start when AirPods connect safely`.
- Saves the toggle in SharedPreferences as `heart_rate_auto_start_when_safe`.
- When enabled, the service schedules heart-rate streaming 3 seconds after the AACP socket has connected and the normal startup handshake/stem setup has run.
- Auto-start only runs if:
  - the setting is still enabled,
  - AACP is still connected,
  - at least one earbud is detected in-ear from AACP or BLE,
  - heart-rate streaming is not already requested.
- Removes active HR AACP quarantine behavior from this base:
  - quarantine no longer blocks manual HR start,
  - quarantine no longer blocks automatic AACP connect,
  - quarantine no longer blocks takeOver,
  - quarantine no longer blocks startHeadTracking,
  - ear reinsertion no longer skips connectAudio because of quarantine.
- Keeps HRM 0x30 OFF as `02 00 00 00`.

Log markers:

- `HR auto-start when safe scheduled in 3000ms`
- `HR auto-start when safe: starting heart-rate streaming after safe connection`
- `HR auto-start skipped because ...`
- `HR auto-start cancelled: ...`

Not changed:

- RTBuddy heart-rate start/stop protobuf payloads.
- Head-gesture call accept/reject behavior.
