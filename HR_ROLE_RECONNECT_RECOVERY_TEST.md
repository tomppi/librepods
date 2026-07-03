# HR role/reconnect recovery test

This build changes role/swap recovery from an immediate stop/start on the old AACP socket to a passive reconnect-based recovery.

## Why

The first role-recovery log showed:

- incoming Bud Role changed from left primary to right primary while HR was requested
- the old immediate recovery sent HR stop/off and HR start successfully
- about 4.5 seconds later the classic/AACP socket still closed
- LibrePods then recovered only because the normal auto-start preference was enabled

That suggests the AirPods may still be moving host/primary responsibility when the role packet arrives. Restarting HR on the old socket may be too early.

## Behavior

When a Bud Role or Bud Swap packet arrives while HR is requested:

- LibrePods arms a pending HR reconnect recovery
- LibrePods does not send an immediate Bud Role command
- LibrePods does not immediately stop/start HR on the old socket
- if the AACP socket drops, the pending recovery survives the local disconnect reset
- after the next AACP reconnect and handshake, LibrePods waits 1500 ms, sends HR stop/off, waits 500 ms, then starts HR again

This recovery is independent of the normal "Start when AirPods connect safely" toggle and does not use its 10 second delay.

## Log markers

- `HR-ROLE-STATE bud_role ... pendingReconnectRecovery=...`
- `HR-RECONNECT-RECOVERY armed: ...`
- `HR-ROLE-RECOVERY passive mode: waiting for AACP reconnect instead of immediate stop/start ...`
- `HR-RECONNECT-RECOVERY scheduled in 1500ms after reconnect ...`
- `HR-RECONNECT-RECOVERY restart result started=...`
