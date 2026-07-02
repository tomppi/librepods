# LibrePods HR passive ear-removal test

This build is based on the HR AACP quarantine build, but changes the ear-removal HR shutdown strategy.

## Goal

Test whether sending HR stop packets during AirPods bud-role switching is what keeps the classic Bluetooth reconnect loop alive.

## Behavior

When HR is active and one or both earbuds leave the ear:

- LibrePods enters HR/AACP quarantine.
- The HR toggle/UI is forced off.
- `heartRateStreamingRequested` is cleared locally.
- Late HR samples are ignored.
- Automatic AACP socket reconnect is blocked during quarantine.
- LibrePods does **not** send the RTBuddy HR stop packet.
- LibrePods does **not** send the `HRM_STATE = 0` control command from the ear-removal path.

Manual HR-off from the Heart Rate page still uses the normal stop commands. This passive behavior is only for out-of-ear safety shutdown.

## Useful log markers

- `Passively stopping heart-rate stream because at least one earbud is out-of-ear`
- `reason=earbud_removed_passive_ble`
- `reason=earbud_removed_passive_aacp`
- `late_hr_sample_out_of_ear_passive`
- `Skipping automatic AACP socket connect during HR quarantine`

## Test meaning

If this stops the reconnect storm, the HR stop command path is probably racing the AirPods firmware during host/role handoff.

If it still reconnects, the AirPods/Android link is probably dropping before LibrePods can act, or another profile/socket path is still poking the link.
