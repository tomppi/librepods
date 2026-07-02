# HR no ear-removal stop test

This build removes the LibrePods-side logic that turns off heart-rate streaming just because
ear detection reports one or both earbuds out-of-ear.

Kept from the previous test base:

- HRM `0x30` OFF uses `02 00 00 00`.
- Heart Rate menu includes `Start when AirPods connect safely`.
- Active HR/AACP quarantine behavior remains removed/no-op.

Changed here:

- BLE ear-state changes no longer stop HR.
- AACP ear-detection changes no longer stop HR.
- Late HR samples while an earbud is reported out no longer force HR off.
- The UI no longer clears the HR toggle/latest sample just because ear detection changes.
- `heartRateEarbudsInEar` now means at least one earbud is in-ear for HR gating.

Manual HR off still sends the normal RTBuddy stop packet and `HRM_STATE 0x30 = 02 00 00 00`.
