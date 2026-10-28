# Differences from upstream OverMesh

**Current targeted audit — 25 October 2026:** see [UPSTREAM-AUDIT-2026-10-25.md](UPSTREAM-AUDIT-2026-10-25.md), against current upstream `main` commit `0feb0bae1da9ee6df52e3981ccfade01f1128b8e` (2026.10.24.2). It supersedes the classifications below for the reviewed features: Send to multiple, persistent chains and Connect All Available remain Android additions; MC splitting, Last Seen sorting and disconnect sounds are shared. MT splitting and configurable split/chain controls are Android additions/enhancements in the inspected paths. Current MC observation default is **5 seconds**, and the debug APK containing the latest changes has been built.

The remaining entries are historical implementation/comparison notes. Their dates, old defaults and “source only” statements describe their original checkpoints, not the current APK.

Source-only follow-up: the persistent chain monitor now offers early completion of MeshCore channel observation with automatic continuation and confirmation-based resend of the previous finished part, alongside Cancel remaining. This extends the previously documented Android chain workflow; upstream absence/parity still refers only to the inspected baselines, not a new full comparison. No APK was produced for this follow-up.

Local update 0.10.1, 24 October: the multi-send action is in Chat between Users pills, gated by Config. MeshCore channel-chain observation is configurable (1–300 seconds, default 2), separately from the post-result gap (default 3). These are local UI/pacing choices; no new upstream parity claim is made.

Last checked: 22 October 2026.
Upstream baseline: Slofi/overmesh main, commit ea5ba82b2d468d84ed58a45430d49118de51ad71.
This is a growing record, not a completed full-app comparison. Absence means not found in the inspected baseline, not a claim about every branch or future release.

History-specific review updated on 23 October against upstream commit 9c65835c2dda416b4c1154cd773bffeb774724c5: [HISTORY-COMPARISON-2026-10-23.md](HISTORY-COMPARISON-2026-10-23.md). Other upstream comparisons retain the baseline above. Local implementation status updated 24 October in [0.10.0 notes](IMPLEMENTATION-0.10.0.md); this is not a fresh full upstream audit.

## Confirmed implemented differences

| Feature | Our Android app | Upstream baseline | Evidence |
| --- | --- | --- | --- |
| Standalone Android operation | Android WebView UI with native phone BLE and USB serial adapters; no computer or TCP radio required | Self-hosted Python dashboard on a host computer | README.md; MainActivity.java, BleRadio.java, UsbRadio.java; upstream README.md |
| Message history storage and retention | Phone SQLite, separated by radio family/node identity, channel and DM peer; no equivalent automatic count cap in save/load paths | Host SQLite per radio; save paths prune to 1000 MT / 2000 MC messages per radio | src/main/java/ChatStore.java; upstream db.py |

## Implemented in Android 0.10.0 — awaiting phone tests

| Feature | Implemented Android behavior | Upstream baseline |
| --- | --- | --- |
| Connect All Available (originally requested as Connect All in Range) | Discover known BLE radios and enumerate attached USB serial radios; connect available known devices, avoid duplicate transports, show outcomes | No equivalent user-facing action found in app.js, lite.js, templates/index.html, templates/lite.html, routes/radio.py or routes/mc.py. Upstream does have enable/disable controls for configured radios; those are distinct from this discovery-and-connect action. |
| Send to multiple | Config-gated contact/channel picker across connected radios; per-destination source radio and outcomes; sequential or concurrent mode | No equivalent general multi-destination Chat workflow found. Upstream has unrelated multi-radio operations such as GPS pushes and MOTD broadcasts. |
| Persistent send batches | Pause, continue, cancel remaining, deferred items, review/select unsuccessful items, crash-safe saved progress and explicit restart resumption | No equivalent persistent send-batch UI/storage found in inspected source. Saved chat history is not a saved pending batch. |

## Shared features or enhancements — do not label unique

- Multi-radio support, channel/DM messaging and saved history exist upstream too.
- MeshCore long-message splitting exists upstream: sequential chunks to one destination, with a 1800 ms inter-chunk delay. Splitting itself is not unique. Current ordinary Android Chat splitting uses a separate configurable interval; result/observation waits and the post-result pause apply to Send to multiple. See the 25 October audit for the current distinction.
- Meshtastic roles are protocol data, not an Android-only feature.
- Last Heard sort and configurable MC retry settings were rechecked on 25 October: upstream has Last Seen sorting; equivalent user-configurable known-route/flood DM attempt controls were not found. See the current audit for source evidence and limits.
- MC retry defaults were compared with the official MeshCore app. That app is a separate reference from upstream OverMesh; do not conflate the two.

## Maintenance policy

1. On implementing or materially changing a feature, update its status and record the difference, upstream source/commit, rationale and validation limits.
2. Keep planned, implemented, shared, deliberately different and not-yet-verified items distinct. Never describe planned work as shipped.
3. Recheck upstream when adopting its changes or when a feature depends on parity. A feature added upstream later is no longer exclusive; preserve the historical note and update the current classification.
4. Perform a full feature-by-feature comparison before a public preview/release, covering Chat, Nodes, Config, connectivity, history/backups, notifications, delivery behavior and unsupported areas.
5. Record missing upstream features as well as Android additions. This initial document is incomplete in that direction; PROJECT.md and README.md describe current limitations.
6. Preserve upstream attribution and follow LICENSING.md before copying third-party code/assets or preparing a fork/release.

Sources: https://github.com/Slofi/overmesh/tree/ea5ba82b2d468d84ed58a45430d49118de51ad71

Detailed agreed work: [FEATURE-PLAN-2026-10-22.md](FEATURE-PLAN-2026-10-22.md).

### 25 October 2026 — My Nodes and connection setup (source only)
- Connect All Available uses the same action-button styling as other My Nodes actions. Connected sits alongside the radio name when space permits and wraps on narrow screens.
- Config ends with Export debug log, then About.
- Saved-radio connection requests (including Connect Selected) now queue on the Android main thread. Each waits for existing connection setup/initial sync before starting; connected radios remain active. Queued requests are shown in My Nodes and can be cancelled individually or with Disconnect all. Permission rejection cancels the remaining batch to avoid repeated prompts.
- Fixed an empty USB discovery result leaving loading set. Overlapping initial connections were a plausible source of the reported intermittent second-radio failure; confirmation requires physical-radio testing. No new upstream comparison claim.
- Java compilation and browser layout/config checks passed. No APK built.

25 October 2026 — Message menus (source only): Meshtastic channel short taps now use the full Message actions menu, including Copy Text, Reply, available Emoji reactions, Direct message, Node actions and Delete Message. Long-press node access remains. MeshCore sender matching is unchanged: unmatched or duplicate display names cannot safely identify a contact. Browser regression coverage checks MT copy/delete/source routing and node actions, plus unmatched/ambiguous MC names. No APK built; installed v0.10 does not contain these source changes.

### 0.10.2 (29) — Meshtastic splitting and live part counts
Long MT channel messages and DMs now split through the persistent single-destination chain independently of the Send to multiple setting. Multi-destination MT targets can also split. Each part respects the radio's text byte limit and Unicode grapheme boundaries; MT channel replies retain the original reply reference on every part. Firmware-managed retries and the existing MT response wait remain unchanged. Both MC and MT composers show the number of split messages beside the byte count when oversized, using the native splitter on Android. Parts arrive separately without automatic recipient reassembly. This extends local functionality; upstream feature parity was not re-audited.

### 0.10.3 (30) — Split preview recovery
Fixed the composer remaining pending after a cancelled/dismissed split preview or when an existing unfinished chain is shown instead. A scoped callback releases the pending request without deleting its draft. Successful queueing still clears the submitted draft. Split progress now has its own title and hides New multi-send. MeshCore queued parts display why protocol work is blocking dispatch, including reconnect-required timeouts; no unsafe bypass of outstanding operations was added. Browser tests cover cancellation recovery and unrelated callbacks. The exact cause of the user's original v0.10 radio stall remains unconfirmed without its debug log.

### 0.10.4 (31) — Standalone automatic split sending
Supersedes earlier single-destination chain behavior: MC/MT Send and Reply now queue split parts directly in Chat, submit the first immediately when the companion is ready, and submit subsequent parts at least one second apart. No split preview, persistent chain, delivery observation wait or configurable multi-send settling gap is used. Only companion command readiness can delay dispatch; a prolonged busy state fails unsent parts visibly. Closing/disconnecting cancels unsent parts, retaining their text/status in Chat. Upgrading discards obsolete saved single-destination split resume instructions while preserving chat history and genuine multi-destination chains. Live part counters remain. Firmware/MC retry handling remains independent of submission pacing.

### 0.10.5 (32) — Cancel Chain
Added Cancel Chain separately from Cancel remaining. It clears the saved multi-send queue and progress, preserves chat messages and their late delivery updates, and stops future app-managed attempts for active MeshCore DMs. Already-issued transmissions and Meshtastic firmware retries may finish. The empty monitor shows No saved sending chain and offers New multi-send. Queue cancellation is persisted; a persistence failure leaves the prior chain paused rather than continuing. Added device regression coverage for clearing active/pending items, restart and history preservation.

### 0.10.6 (33) — Per-chain destination composer
Replaced the flat multi-send picker with a Select Radio dropdown, Channel/DM choice and radio-scoped checkboxes. DM Select Nodes includes name/ID search; hidden search results retain selections. Each radio can combine channels and DMs. Save Destinations commits both sets for that radio; users can revisit radios, remove selections, and review the saved destination summary. Send Chain validates saved choices, message, connected radios and current destinations before starting directly. Cancel dismisses the local draft. No destination selection is stored globally or reused by a new chain. Existing sequential-default and optional concurrent sending remain.
Selection model smoke tests passed for mixed types, independent radios, editing, saved snapshots and fresh drafts. Native UI/radio testing remains pending.
