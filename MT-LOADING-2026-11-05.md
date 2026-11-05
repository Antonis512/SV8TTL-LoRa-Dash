# Meshtastic loading and UI responsiveness — 5 November 2026

## Verified causes

Before these changes, `RadioSession.node`, `lastHeard`, `position`, and signal samples wrote SQLite on Android's main thread. Every node also called `sendMapData`, which read and encoded the entire Map snapshot on main, including while Map was hidden. `onRadioState` rebuilt Chat, Nodes, My Nodes and sound settings regardless of the active tab. Session JSON queried Last Heard separately for each node and repeated the same selected session's encoding for its Chat pane. Meshtastic accepted any configuration completion ID. The activity's loading watchdog reset on every received packet, including unrelated mesh traffic.

## Official behavior consulted

Compared against Meshtastic Android commit `0ddf09db3bed50c653fdcd58559ad728b2dc6e53`:

- [Handshake constants](https://github.com/meshtastic/Meshtastic-Android/blob/0ddf09db3bed50c653fdcd58559ad728b2dc6e53/core/repository/src/commonMain/kotlin/org/meshtastic/core/repository/HandshakeConstants.kt): config-only `69420`, node-only `69421`.
- [Configuration flow](https://github.com/meshtastic/Meshtastic-Android/blob/0ddf09db3bed50c653fdcd58559ad728b2dc6e53/core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/MeshConfigFlowManagerImpl.kt): stage/session validation, early-node buffering, inter-stage heartbeat and asynchronous node installation before publishing readiness.
- [Connection management](https://github.com/meshtastic/Meshtastic-Android/blob/0ddf09db3bed50c653fdcd58559ad728b2dc6e53/core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/MeshConnectionManagerImpl.kt): 30-second/60-second BLE inactivity budgets and ten-budget total caps, avoiding in-session retries that can restart a firmware dump.
- Firmware `PhoneAPI.cpp` and `mesh.proto` were also checked for the request selectors and heartbeat wire field.

The implementation is independently authored for this app's Java/SQLite/WebView architecture. It does not copy the official Kotlin implementation or introduce Room, a TCP connection, or a Windows dependency. The official client's additional automatic transport-recovery machinery is not ported: this app reports a stalled stage and requires reconnecting.

## Resulting behavior

On BLE, MT sends a heartbeat and waits at least 100 ms before requesting configuration. MT requests configuration first and requires its own-node identity plus the matching completion ID before requesting nodes. It waits at least 100 ms, sends a heartbeat, then waits at least another 100 ms before requesting the node database. Wrong, duplicate, cancelled and out-of-order completion signals cannot complete loading. Relevant stage traffic extends that stage's inactivity window; ordinary packets cannot. A bounded total deadline still prevents endless loading. There are no automatic mid-session configuration replays.

Radio callbacks and in-memory session state remain on Android's main thread. Node, Last Heard, channel, signal and contact-name storage is buffered during MT loading and installed in a background SQLite transaction. The node snapshot contains each node once, including early nodes and later replacements. Early node information and heard timestamps are retained. Contact names use one preference editor for the batch. Readiness is published after the installation barrier succeeds; disconnect/reconnect generations reject stale callbacks and queued operations. A storage failure reports an error instead of marking the session ready.

Live metadata updates are batched for up to 150 ms or 128 operations. MT message/reaction deduplication and persistence use the same dedicated background queue, so packets arriving during node installation do not wait on SQLite on the UI thread. Geofence UI/notification delivery follows the successful transaction commit. Map snapshots are coalesced, generated on a separate background reader after pending writes complete and requested by radio events only while Map is active. Last Heard values use one query per session, and each selected session is encoded once per publication.

The WebView rebuilds the active radio screen only; opening another tab renders the latest state. Loading shows the configuration/node/saving stage and node/channel counts. Partial MT node lists are withheld until installation completes. SQLite schema, application ID, update manifest and signing configuration are unchanged. The pending GitHub update is version 0.10.26 (code 53); publication requires the original signing key.

## Validation

Passed:

- `MeshtasticLoadingSmoke`: 1,500 nodes, early/duplicate nodes, both request selectors, heartbeat pacing, wrong/out-of-order/duplicate completion, missing identity, cancellation, slow valid progress, inactivity and total deadlines, and unrelated traffic.
- Existing `SessionProtocolSmoke`, `LoadingProtocolSmoke` (MeshCore), `NameProtocolSmoke`, `SignalQualitySmoke`, `ReactionProtocolSmoke`, `RadioSettingsSmoke`, `ble-transport-smoke.py` and `activity-result-codes.py`.
- Browser `loading-ui.cjs`: 100 updates while screens are hidden produce no Chat/Nodes/My Nodes/sound rebuilds; navigation renders current data and loading/saving progress. Existing Contacts scrolling, geofence history, multi-device, offline messaging, reactions and session browser regressions also passed.
- Debug app and instrumentation APK compilation. The instrumentation suite was not executed: no Android device/emulator is attached. Browser and fake-GATT tests do not measure physical radio throughput or native Android scheduling.

Cloud build commands use `source /workspace/.cloud-setup/activate.sh` followed by `./gradlew --offline --init-script /workspace/.cloud-setup/local-usb.gradle assembleDebug assembleDebugAndroidTest --no-daemon --max-workers=4`. JitPack is blocked by the current cloud network policy, so the local repository contains USB serial `3.11.0` built from the unchanged official GitHub tag `8e694372dee4531d140766df7af3189d3d9531c5`; the app's dependency declaration is unchanged. Normal environments with JitPack access use `./gradlew assembleDebug` (or `gradlew.bat assembleDebug` on Windows).

Physical follow-up remains required: large MT node downloads with simultaneous live traffic, reconnect/disconnect during each stage and storage installation, MC/MT concurrent connections, message counts/history/reactions, geofence alerts, Contacts scrolling and native keyboard/system insets. No measured hardware speedup is claimed. APK signature verification passed. Its signing certificate differs from the published APK under `updates/`, so it cannot update that installation in place. Rebuild with the original development key for an in-place update; preserve app data.
