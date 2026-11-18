# Node management — 17 November 2026

Config → My Nodes → Manage opens a radio-specific menu. Channels is the first section; `nodeManagementSections` in `node-management.js` is the extension point for future preset and owner-name sections. Those future settings are not implemented or shown as inactive controls. Identity is keyed by radio family and full node ID, independently of the Chat radio selection. Member mode does not expose these controls.

## Add channel

- Works through the phone's existing local radio transport. No desktop or TCP connection is introduced.
- Only slots actually reported by the connected radio are considered. No channel choices are fabricated before loading completes. The slot is re-read immediately before writing; occupied slots are not overwritten.
- MT adds SECONDARY channels only, leaving the primary slot and modem preset alone. Names are limited to 11 UTF-8 bytes. Keys can be supplied or generated (Base64, AES-128/256), or users can explicitly choose the public default key or no encryption. A fresh admin session key from the read response accompanies the write. A routing ACK triggers a read-back; success requires matching slot, role, name and key.
- MC supports public hashtag names (the first 16 bytes of SHA-256 of the exact UTF-8 name) and private 16-byte hexadecimal keys. Names are limited to 31 UTF-8 bytes to retain NUL termination. Private keys can be generated on the phone. Empty names with nonzero secrets are treated as occupied. Radio OK triggers read-back; matching name and key are required for success.
- MC configuration serializes with command/message synchronization. Timeouts require reconnecting before further commands because untagged late replies cannot be assigned safely.
- Confirmed channel names enter the existing channel event/storage/Chat path. Keys are not put into published application state or logs; the entry field is cleared when the dialog closes. Generated keys can be revealed and copied manually before closing.
- Future owner/preset operations should use separate transaction types and the same identity, loading, connection, and result safeguards, not reuse an add-channel request.

## Validation

- `gradlew.bat compileDebugJavaWithJavac --offline` passed. No assemble/package task or APK build was run, per the user's instruction.
- `ChannelAdminSmoke`: MT one-based reads, fresh passkey, secondary role, verification; MC hash vector, frame format, occupied slots, errors, mismatches, timeout, command serialization and late replies.
- Existing `LoadingProtocolSmoke`, `MeshtasticLoadingSmoke`, `RadioSettingsSmoke`, and `RetryProtocolSmoke` passed.
- `node-management.cjs`: actual app page, MT/MC form routing, generated keys, busy/disconnected controls, stale callbacks from another radio, secret cleanup, and 320px/390px/landscape dialog overflow. Screenshot inspected at `build/previews/node-management.png`.
- JavaScript syntax and Git whitespace checks passed.

## Pending physical tests

Test MT and MC over Bluetooth: add and verify a channel, exchange messages with a second radio using the same name/key, reconnect and verify persistence, full channel tables, radio rejection, disconnect during setup, and simultaneous radio connections. Verify keyboard, gesture-navigation and three-button-navigation insets on Android. Browser layout checks and compilation do not establish physical radio behavior. The earlier 0.10.25 connection/Contacts/insets and geofence comparison checks remain pending until the user reports results.

Protocol provenance references are in LICENSING.md. This remains the independent SV8TTL LoRa Dash Android adaptation based on OverMesh Lite, with Beta separate from the app name.
