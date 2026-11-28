# Persistent sends — local preview

The Chat channel and DM composers now show **P. Msg**, **P. Location**, **Send position**, in that order above the text box. Untrained member mode has the same persistent buttons to the left of **Send my location**. Existing colors, wrapping, Chat label and Android inset handling are preserved.

## Message behavior

- Start snapshots the original radio identity, destination and message. Channel jobs also retain the reported channel name and a key fingerprint, preventing silent reuse after channel reconfiguration. They never switch to another radio.
- Initial send is followed by retries at the chosen interval, minimum two minutes. Maximum retries is optional; zero means only the initial transmission. Expiry is optional and continues during disconnection or app closure.
- MT mesh/recipient acknowledgement or positive relay evidence completes a job. MC channel jobs stop when the existing encrypted-payload tracker hears a matching repeated packet with a nonempty forwarding path. MC DMs stop on recipient acknowledgement: the current protocol integration does not expose reliable repeater evidence for outgoing encrypted DMs. Local radio acceptance alone is never success.
- A linked reply to any retained attempt stops the job. A later unlinked DM from the same recipient on the same receiving device offers **Mark answered / Stop**. Loading old history and unrelated traffic do not qualify.
- MC persistent DMs use one app-protocol attempt per scheduler transmission; short app-level retry cycles are disabled for those messages, while late ACKs are retained. Meshtastic firmware's own reliability/rebroadcast behavior remains firmware-owned.
- Persistent text currently must fit one radio packet, using the radio-specific UTF-8 limit; ordinary manual messages retain their existing split-message support. The setup dialog states this limitation and rejects overlength persistent text.

## Location behavior

- Send the first qualifying phone fix, then require both at least 100 m straight-line displacement from the last sent fix and the configured interval (minimum two minutes).
- Accept fixes captured within 30 seconds, using Android monotonic capture time, with reported horizontal accuracy of 25 m or better. Android accuracy is an estimate, not a guaranteed error bound. No stale cached coordinates are sent after waiting for manual work or reconnection.
- Failed radio writes do not advance the movement baseline. Location acknowledgements do not stop periodic sharing.
- MT uses the existing waypoint format and recipient channel for DMs; MC uses the app's existing location-message format. This feature does not change the radio's own advertised GPS configuration.
- Precise-location permission is requested when starting. A location foreground service is promoted while the app is visible, with an ongoing notification and **Stop all persistent** action. Disabled GPS or an inadequate fix produces a waiting status.

## Manual priority and recovery

Both automatic features yield to focused nonempty or recently edited message composers, native multi-send composition, pending manual writes, split messages and an active sending chain. A busy check defers the automatic job 45 seconds before checking again; deferrals consume no attempts. An already-submitted radio packet cannot be recalled.

Jobs are durably saved before transmission. Restart restores unfinished jobs **paused**, requiring explicit Resume. Reconnection never sends a catch-up burst. Storage failure pauses jobs. Timing uses Android main-thread callbacks and can be delayed by Android power management; the interval is a minimum, not an exact delivery schedule.

Tracking is bounded to 20 unfinished jobs and 50 recent jobs; each job retains the original and most recent attempt IDs (up to 256). Full ordinary chat history remains governed by the existing history store. New settings do not alter application identity, signing, release version or update feed.

## Validation

- `assembleDebug` passed; existing compileSdk/Gradle warnings remain.
- `PersistentJobsSmoke`: 52 deterministic assertions passed on the API 30 Android runtime, including retry limits, expiry during composing, 45-second deferral, late ACKs, unrelated replies, restart recovery, movement, stale fixes and failed-write baseline recovery.
- `PersistentProtocolSmoke`, `RetryProtocolSmoke`, `RadioActionsSmoke` and `RepeatProtocolSmoke` passed, including single-attempt MC behavior on legacy/current firmware fixtures, exact repeated payload matching, MT default-channel key retention and direct waypoint ACK correlation.
- `persistent.cjs`, `dm-position.cjs` and the activity-result code check passed. Browser coverage includes 320 px width, short keyboard viewports, controls in member mode, destination snapshots, optional limits and preserved drafts.
- Native gesture/three-button visual checks remain unverified: the API 30 emulator has WebView 83, which lacks APIs already required by the existing UI; the API 36 emulator did not complete boot. No UI compatibility shims were added. The reproducible `persistent-android.cjs` check is provided for a working debug emulator. Physical MC/MT behavior is still pending.

## Incoming location trails (27 November)

Incoming shared phone locations now have amber APRS-style trails with arrowheads facing from older reports toward newer reports, plus a named marker at the newest report. Reports are sorted by timestamp and duplicate samples are ignored. Stationary reports and gaps exceeding 30 minutes are not connected. Arrows resize with the map view; network, receiver, contact and time filters apply. The existing node-history action also uses directional trails. Shared phone coordinates remain separate from the radio's advertised position and do not trigger geofences.

MT groups repeated Location waypoints by sender and waypoint ID. New MC persistent channel reports carry a short sender identity in their compatible `Location @<12-hex-ID>` title; direct messages use the sender identity. Old untagged MC channel markers remain separate because advertised names alone cannot reliably identify a sender. Claimed channel identity is identified as such in the popup. Receiving radios remain separate tracks.

Retention is a rolling **30 days**. Cleanup runs at startup, each minute while the app runs, and when reading/exporting/importing map data. It removes expired shared samples, native position history, received map marks, heard events and geofence alert history; stale node coordinates and transition state are cleared while identities remain. Saved manual marks, geofences, overlays and offline packs remain. An old backup cannot revive expired history. Chat message retention is unchanged.

Validation: `MapLocationHistorySmoke` passed 14 assertions using Android API 30 SQLite, covering cutoff boundaries, duplicate samples, separate receivers, stale GPS with fresh receipt time, coordinate clearing, preserved authored items and backup expiry. Browser tests cover arrow direction, ordering, gaps, date-line crossing, filtering, latest markers and expiry. Physical radio/GPS validation remains pending.

## Device checklist

Use both MC and MT Bluetooth radios: known-device connect/reconnect, initial nodes/channels and automatic Chat selection, simultaneous connections; persistent channel and direct messages with ACK loss, late ACK, matching repeat and recipient response; manual typing/sends during due retries; location indoors/outdoors, stationary GPS drift, 100 m movement, screen off, permission revocation, expiry/cancellation and process restart. Also retain the pending Contacts scrolling and gesture/three-button/cutout/keyboard inset checks. The supplied map/geofence node identities are already recorded; no new identities are needed.
