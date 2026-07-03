# HR Host Remaining Bud Test

Experimental LibrePods HR/AACP test patch.

Base: `librepods_test_base_hr_no_ear_removal_stop_buildfix_repo.zip`.

Keeps:
- HRM 0x30 OFF = `02 00 00 00`.
- Heart Rate menu auto-start toggle.
- Quarantine removed/no-op.
- Ear-removal does not stop heart-rate streaming.

Adds:
- Heart Rate menu toggle: `Make remaining earbud host`.
- Preference key: `heart_rate_host_remaining_bud`.
- AACP Bud Role control ID `0x08`.
- When enabled and ear detection changes to exactly one earbud in-ear:
  - left remaining => sends `04 00 04 00 09 00 08 01 00 00 00`
  - right remaining => sends `04 00 04 00 09 00 08 02 00 00 00`
- Duplicate requests for the same remaining side are suppressed until both/none state resets the requested role.

Useful log markers:
- `HOST-BUD-ROLE TX request primary=left/right ...`
- `HOST-BUD-ROLE remaining bud requested as host ...`
- `HOST-BUD-ROLE RX raw=... role=left_primary/right_primary`
- `HOST-BUD-ROLE skip duplicate ...`

This is experimental. Apple Wireshark labels Bud Role as left/right primary, but AirPods may require a newer Bud Swap handshake for all cases.
