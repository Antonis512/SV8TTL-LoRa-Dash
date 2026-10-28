# SV8TTL LoRa Dash Android project

Current identity (0.10.10): **SV8TTL LoRa Dash**, Beta. Independently developed Android adaptation based on OverMesh Lite, incorporating original OverMesh assets; not an official OverMesh release. Earlier milestones below describe their recorded versions. The existing application ID and signing configuration are retained so this build updates the previous preview.

## Goal

Run an Android interface adapted from OverMesh Lite entirely on an Android phone, connected directly to multiple Meshtastic and MeshCore radios over Bluetooth Low Energy or USB serial. The phone must work while the Windows computer is off. TCP radio connections are out of scope.

## Current state (0.9.8)

0.9.8 adds independent Chat device slots, same-family devices on both sides, red offline/blank empty pills, offline history, and DM long-press conversation browsing/deletion. View Messages remains in My Nodes. Previous Send restoration, reactions and MC loading fixes remain. See PHONE-TESTS.md for device checks.

- Gradle/Android Studio project builds a debug APK for Android 8.0+.
- The bundled WebView UI uses OverMesh Lite styling and shows Chat, Nodes, Map, Log, and Config tabs.
- Chat and Nodes use the phone's native radio sessions. MC and MT can stay connected at the same time.
- The WebView fits within Android system bars so the top header and bottom tabs remain visible.
- Channel pills appear only for a connected selected radio and use names reported by that radio.
- Disabled Meshtastic channel slots are hidden. Config can disconnect each radio, Nodes shows reported roles/types, and MeshCore send responses update the corresponding queued message.
- USB writes run in order for each radio, and queued callbacks from an older connection are ignored after reconnecting.
- BLE scanning shows progress, can be stopped, and restores status if the picker is cancelled.
- Chat history is stored in a phone-local SQLite database. Nodes are reloaded from radios on connection. Map and Log remain placeholders.
- Tapping a node opens its details and actions. Favorites are stored in this app. Meshtastic hardware model, radio-reported hops, and positions are shown when present; MeshCore contacts show type, decoded route hops, and advertised position. Distance appears when both the local radio and contact report coordinates.
- Meshtastic nodes can send direct messages, trace and information/position requests. MeshCore contacts can send direct messages and discover receiver paths in both directions or trace a compatible learned repeater route; repeaters/sensors can receive a status request. Direct message history is saved and is readable offline in Nodes. Position opens a phone map app; node details can be shared as text.

0.9.6 implements the saved next-session checklist; see README.md and PHONE-TESTS.md. Config is the initial screen without connected radios. Saved DM conversations remain available offline by local device; Nodes uses persisted radio timestamps and a 30-day visibility filter.

## Next milestones

1. Validate BLE scan controls, saved Chat and direct message history, links, connection, active channel names, node roles/types, hops, positions, requests, receive, and send status on physical Meshtastic and MeshCore radios using the 0.9.0 APK. Record hardware, Android version, transport, and observed behavior.
2. Validate the 0.9.0 resume checklist using PHONE-TESTS.md. Saved radio connection controls and per-device message histories are implemented.
3. Wire Map to actual radio position data; direct messages, node details and reported positions are implemented.
4. Implement Log and remaining Lite settings on the phone.
5. Review the Android module with upstream OverMesh before opening a contribution PR.

## Source and licensing

The upstream project is [Slofi/overmesh](https://github.com/Slofi/overmesh). SV8TTL LoRa Dash is an independently developed Android adaptation incorporating original OverMesh assets, not an official OverMesh release. `LICENSE` contains the upstream MIT license. `src/main/assets/overmesh-lite.css` and `background-topo.png` were copied from upstream; the Android adapter and UI files are local work.

## Build and package

Open this directory in Android Studio or run `gradlew.bat assembleDebug` with JDK 17 and Android SDK 36. The installable debug APK appears in the project root as `SV8TTL-LoRa-Dash-debug.apk`. Build output is ignored by Git.


## Independent release planning

Read LICENSING.md before reusing source or preparing a public fork. It records attribution, dependencies, official-app reference boundaries, branding and release decisions. No independent fork has been published.
