# Android additions compared with OverMesh — 25 October 2026

Compared the local Android 0.10.6 (33) source, including the latest packaged sending changes, against Slofi/overmesh `main` at **0feb0bae1da9ee6df52e3981ccfade01f1128b8e**, version **2026.10.24.2**. This was the current upstream head when checked. This is a targeted source comparison, not a runtime or all-branches audit. “Not found” means no equivalent workflow was found in the inspected desktop/Lite UI and relevant backend paths; it does not establish originality across other applications.

## Findings

| Feature | Classification | Verified distinction |
| --- | --- | --- |
| Send to multiple | Android addition; equivalent workflow not found upstream | Our composer selects channels and DMs across radios, saves destinations within the draft, and sends sequentially or concurrently. Upstream normal Chat sends to one selected destination through one radio. Specialized broadcasts/sharing elsewhere are not this general mixed-destination composer. |
| Persistent sending chain | Android addition; equivalent workflow not found upstream | Saved queue, pause/continue, Cancel remaining, Cancel Chain, review/requeue unsuccessful items, and paused recovery after app restart. Upstream saved message history is not a pending-send queue. |
| Send Next / resend previous / progress bubbles | Android additions to that chain | Active-only bubbles, per-item report, early MC channel observation completion, confirmation before resending, and Finished status. Upstream MC chunk loops do not expose this monitor. |
| Connect All Available | Android addition; equivalent action not found upstream | Enumerates attached USB radios, discovers saved BLE radios and attempts connections, with duplicate avoidance and a busy indicator. Upstream already connects/reconnects configured radios and provides per-radio configuration; automatic connection itself is shared. |
| MeshCore message splitting | **Shared, not Android-only** | Upstream desktop and backend split channel messages and DMs. Backend support also applies to requests from Lite. Upstream uses a fixed 1.8-second inter-part delay and a maximum of six parts. |
| Meshtastic message splitting | Android addition in inspected send paths | Android splits MT channel messages and DMs too. Upstream desktop MT Chat and `/api/chat/send` submit one complete text; no corresponding MT Chat splitter was found. |
| Message Split switch and interval | Android enhancement | Android has an enable/disable setting and 1–300 second interval, default 1 second. No equivalent user-configurable controls were found upstream; its MC splitting delay is a source constant. |
| Result-based multi-send pacing | Android addition to the chain | Separate “Pause before next message (seconds)” (default 3), configurable MC channel observation (default 5), observation from acceptance and late-repeat reconciliation. Ordinary Android Chat splitting is separate and does **not** wait for a result per part. |
| Configurable MC DM attempt budgets | Android setting; equivalent controls not found upstream | Separate known-route/flood attempt settings, 1–10, with firmware-version constraints. Upstream delegates DM sending to `mc.commands.send_msg`; this finding is about exposed controls, not a claim that its dependency/firmware never retries. |
| Last Heard / Last Seen sorting | **Shared, not Android-only** | Upstream desktop has a sortable Last Seen column and reverses the direction on repeated selection. Lite also sorts nodes by recency. Android has its own explicit newest/oldest choices and unknown-value handling. |
| Disconnect sounds | **Shared concept, not Android-only** | Upstream desktop plays distinct connect/disconnect notification sounds. Android provides a separate saved, user-selected unexpected-disconnection sound and intentional-disconnect suppression. |
| Standalone Android BLE/USB operation | Android platform addition | This application talks directly to phone-connected radios. Upstream is a self-hosted application; its own BLE/serial support must not be described as absent. |
| History retention | Different policy, not a unique history feature | Android retains stored messages until explicit cleanup and pages conversations. Current upstream save paths still prune to 1,000 MT / 2,000 MC messages in their radio databases. Both products have persistent history. |

Multi-radio support, channel/DM messaging, unread indicators, saved history and notification sounds must not be advertised as Android-only features. Independent left/right Chat selectors, circled-i help placement and the native progress presentation are local UI choices; this review does not claim the underlying UI concepts are unique.

## Evidence at the pinned upstream revision

- [Upstream commit/version](https://github.com/Slofi/overmesh/commit/0feb0bae1da9ee6df52e3981ccfade01f1128b8e).
- [Desktop JavaScript](https://github.com/Slofi/overmesh/blob/0feb0bae1da9ee6df52e3981ccfade01f1128b8e/static/js/app.js): `sendChat` around 2737–2765; `setLiveSort` around 3022; MC constants/splitter around 15655–15732; MC chunk sender/loops around 15924–16008; MT and MC disconnect sounds around 1482 and 11976. The full file was inspected; retained reference is `build/reference/audit-2026-10-25/app.js`.
- [Lite JavaScript](https://github.com/Slofi/overmesh/blob/0feb0bae1da9ee6df52e3981ccfade01f1128b8e/static/js/lite.js): recency sort around 1769; single-destination send routing around 2282–2313; per-radio settings around 2901 onward.
- [MeshCore backend](https://github.com/Slofi/overmesh/blob/0feb0bae1da9ee6df52e3981ccfade01f1128b8e/mesh_mc.py): six-part/1.8-second constants around 1436; byte splitting around 1948; reconnect loop around 4023; `send_msg` delegation around 4128; channel/DM splitting around 4610–4635.
- [MT Chat route](https://github.com/Slofi/overmesh/blob/0feb0bae1da9ee6df52e3981ccfade01f1128b8e/routes/chat.py): `/api/chat/send`, one radio/destination and one `sendText` call.
- [Settings routes](https://github.com/Slofi/overmesh/blob/0feb0bae1da9ee6df52e3981ccfade01f1128b8e/routes/settings.py), [radio routes](https://github.com/Slofi/overmesh/blob/0feb0bae1da9ee6df52e3981ccfade01f1128b8e/routes/radio.py), [MC routes](https://github.com/Slofi/overmesh/blob/0feb0bae1da9ee6df52e3981ccfade01f1128b8e/routes/mc.py), and both templates were checked for connection actions, send controls, split controls and attempt settings.
- [Desktop template](https://github.com/Slofi/overmesh/blob/0feb0bae1da9ee6df52e3981ccfade01f1128b8e/templates/index.html#L360): clickable Last Seen sorting.
- [Database](https://github.com/Slofi/overmesh/blob/0feb0bae1da9ee6df52e3981ccfade01f1128b8e/db.py): save-time pruning around 1219–1223 and 1347–1351.

Android evidence: `MainActivity.java` (`startSplit`, `tickSplit`, `MultiSendComposer`, `tickChain`, `showChain`, `connectAllAvailable`, settings bridge), `SendChain.java`, `MessageChunks.java`, `ChatStore.java`, `src/main/assets/index.html` and `android.js`.

## Limits

This review does not test real radios, assess protocol interoperability, or establish feature parity with the official MeshCore/Meshtastic apps. Backups, maps, TOC/Log, full radio administration and all upstream integrations were not comprehensively re-audited. No application code or APK changed during this comparison. Reference code stays outside the product; existing attribution remains unchanged.
