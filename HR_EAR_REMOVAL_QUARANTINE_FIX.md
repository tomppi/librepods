# HR ear-removal AACP quarantine test build

This test build keeps the existing HR ear-removal safety fixes and adds a more aggressive isolation patch for the reconnect loop.

## Main difference from the earlier cooldown attempt

The earlier cooldown still allowed LibrePods to open the AACP socket, then tried to suppress takeover/head-tracking/audio commands after that.

This version blocks automatic `connectToSocket()` itself while HR ear-removal quarantine is active. That should tell us whether the reconnect loop is caused by LibrePods opening AACP during the AirPods bud-role switch.

## Behavior

When HR is active and BLE or AACP reports one/both earbuds out-of-ear:

- HR is stopped immediately.
- HR UI state is forced off through the existing broadcast.
- Pending Health Connect samples are flushed by the ViewModel on forced-off/disconnect.
- LibrePods enters HR AACP quarantine for up to 30 seconds.

During HR AACP quarantine:

- Automatic AACP socket reconnects are skipped.
- `takeOver(...)` is skipped.
- `startHeadTracking()` is skipped.
- Immediate LibrePods `connectAudio()` on earbud reinsertion is skipped.
- Manual reconnect can still bypass the socket block.
- BLE battery/in-ear updates still continue.
- Android's own A2DP/HFP reconnect behavior is not blocked.

Quarantine clears when:

- both earbuds are reported in-ear and remain stable for 5 seconds, or
- the 30-second safety timeout expires.

## Useful log strings

Look for these in logcat:

- `HR AACP quarantine active for automatic reconnects`
- `Skipping automatic AACP socket connect during HR quarantine`
- `Skipping AACP socket connect because another connect is already in progress`
- `HR AACP quarantine release scheduled after stable both-in-ear state`
- `HR AACP quarantine cleared`

## Scope

This patch intentionally does not change the head-gesture call accept/reject code path.
