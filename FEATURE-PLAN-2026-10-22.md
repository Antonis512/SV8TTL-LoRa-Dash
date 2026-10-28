# Feature report — 22 October 2026

Status: agreed feature scope, recorded before implementation. This report does not claim the features are shipped.

Implementation update, 24 October: [0.10.0 implementation and validation notes](IMPLEMENTATION-0.10.0.md). The original scope below is retained as a design record; consult the implementation notes for current behavior and physical-test limits.

## History comparison and upstream reference

Inspected Slofi/overmesh main commit ea5ba82b2d468d84ed58a45430d49118de51ad71. Upstream saves messages/status per radio in local SQLite databases on its host. Its save paths retain 1000 MT / 2000 MC messages per radio; initial history loads normally retrieve 500. Our ChatStore uses phone-local SQLite keyed by radio family and radio node identity, with channels and DM peers separating conversations. Its current save/load paths have no equivalent count cap. Preserve existing history, offline browsing, deletion and backup behavior; do not introduce upstream pruning as part of this work.

Upstream static/js/app.js splits long MC messages into chunks for one destination and waits 1800 ms between chunks. This is not a persistent, pausable multi-destination batch. No such batch implementation was found in the inspected main branch.

References:
- https://github.com/Slofi/overmesh/blob/ea5ba82b2d468d84ed58a45430d49118de51ad71/db.py
- https://github.com/Slofi/overmesh/blob/ea5ba82b2d468d84ed58a45430d49118de51ad71/static/js/app.js
- https://github.com/meshtastic/protobufs/blob/master/meshtastic/config.proto
- https://github.com/meshcore-dev/MeshCore/blob/main/src/helpers/BaseChatMesh.cpp

## Planned changes

1. Roles: all eleven current Meshtastic roles already have the correct wire mapping. Verify coverage and explicitly identify deprecated Router Client and Repeater when received. Never renumber legacy IDs or invent a reported role.
2. Nodes sorting: Last Heard newest-first and oldest-first, using full timestamps, stable tie handling and unknown timestamps last.
3. My Nodes: Connect All Available scans known BLE devices and enumerates attached supported USB serial devices. Obtain OS permissions, verify radio identity, skip active/in-progress connections, avoid duplicate physical radios, and prefer USB when both transports are available. Show progress and individual outcomes. USB identity matching must not assume identical adapter models identify the same radio.
4. MC DM attempts: independently configurable known-route and flood counts, with 4 + 4 defaults for extended-retry firmware. Counts include the initial transmission. Preserve older firmware limits, acknowledgment matching, timeout handling and stop controls. Validate allowed ranges against attempt-field/protocol capabilities. Keep the 150-byte per-packet DM policy; no agreed increase to 160 bytes.
5. Send to multiple: enable/disable in Config, off initially, with a short explanation. Picker includes contacts from every connected radio and radio-reported channels. Every destination retains its chosen source radio and peer/channel identity. Same names must not merge identities. No automatic substitution of sending radios. Explicitly identify duplicate destinations through multiple radios.
6. Modes: sequential by default; optional concurrent per-radio queues. Sequential waits for the applicable delivery/repeat outcome or bounded timeout, including retries, before advancing. Local BLE/USB write acceptance alone is insufficient. Coordinate app-managed retries with the batch. Firmware traffic cannot be guaranteed silent.
7. Pause: prevent the next item from starting, let the current item finish, then retain the pending queue. Incoming messages and notifications continue. Resume starts only pending items. Cancel remaining ends pending sends while retaining results.
8. MC DM active controls: Stop retries and continue / Stop retries and retry later. Stop future app-issued attempts, allow the current attempt's response window to finish, and handle late acknowledgments. MC channels have no automatic retries to stop. Do not offer active retry cancellation for MT without a verified supported implementation.
9. Persistence: save text, original source radio, destinations, chunk positions, outcomes, attempts and batch state. Reopen through Send to multiple to continue, review or cancel. After process death/restart, never automatically transmit; interrupted active items are unconfirmed and need review. Disconnected source radios wait for reconnection. Ensure storage migration preserves existing chats. Record updates before issuing commands so crashes do not silently resend an uncertain item.
10. Retry review: list failed, unconfirmed and deferred items with distinct labels; user selects what to retry. No repeat heard on a channel is not proof of failure. Late acknowledgments update outcomes. Completed items must not be silently resent.
11. MC long messages: add UTF-8-safe splitting for ordinary channel/DM sends as well as multi-destination sends. Preserve all text and emoji sequences where possible; never split a UTF-8 encoding. Use 150 bytes per DM chunk and the actual selected radio's channel budget (160 minus sender-name UTF-8 bytes minus 2). Preview that multiple radio messages will be sent. Track each chunk and retain unsent chunks across pauses/restarts. Do not assume automatic reassembly in other apps. Reuse the persistent queue even when multi-destination selection is disabled. Any visible part numbering consumes the same byte budget.
12. Feature notes: maintain this report/TODO and release notes as work lands, distinguishing implemented behavior, test results and outstanding phone checks.

## Timing and cancellation limitations

Proposed extra settling pause: configurable 3 seconds after the relevant completion event. This is a provisional application policy for device testing, not a protocol guarantee. Upstream's 1.8-second chunk spacing does not establish a universally sufficient RF quiet period. Actual ACK deadlines depend on packet airtime, routes and congestion. MC channels require a separate bounded observation window; determine its policy during implementation rather than treating a 3-second delay after acceptance as delivery evidence.

When Config disables multi-send, stop further batch dispatch and retain results. A paused queue must remain accessible for review/cancellation even if new multi-send creation is disabled. An already issued transmission cannot be recalled. Pausing/cancelling must account for this without misleading labels.

## Verification and implementation order

- Implement small changes first: role coverage, Last Heard, retry settings, available-device connection workflow.
- Add persistent batch/chunk storage and scheduler, then UI, controls and restart recovery.
- Test Unicode byte boundaries, long-message text preservation, per-radio identity, duplicate selection, late ACKs, attempt budgets, cancellation, disconnection and restart without duplicate sends. Verify old chat databases migrate intact.
- Test multiple radios, BLE plus USB, permissions and transport deduplication on the phone. Verify notification/RX behavior while paused and ensure real delivery/timeout events advance the queue correctly.
- Check gesture and three-button navigation plus keyboard insets. Preserve Chat branding and standalone phone operation.
- Run gradlew.bat assembleDebug after implementation. A successful build does not establish RF reliability or a safe simultaneous-device maximum.

## Already completed during debug-log investigation

- Removed false failure wording from successful BLE write logs.
- MC live queue drains no longer repeat initial-sync completion.
- Failed BLE writes close the uncertain connection rather than dispatch later commands.
- Added a bundled favicon and sanitized WebView resource-location diagnostics; original resource-error cause still requires phone verification.
- Debug build and four protocol smoke suites passed. Latest log session's connection failures were explained by the user's power failure.

No third-party implementation is copied by this report. Read LICENSING.md before copying code/assets and preserve all provenance and notices.
