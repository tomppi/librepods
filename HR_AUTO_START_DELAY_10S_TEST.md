# HR auto-start 10 second delay test

This build changes the Heart Rate menu option `Start when AirPods connect safely` so the automatic heart-rate start waits 10 seconds after the AACP socket has connected and the normal setup path has run.

This is intended to let the AirPods finish model/host-bud/role settling before LibrePods sends the HRM enable + RTBuddy heart-rate start packets.

Expected log marker:

```text
HR auto-start when safe scheduled in 10000ms
HR auto-start when safe: starting heart-rate streaming after safe connection
```

Manual heart-rate start is unchanged.
