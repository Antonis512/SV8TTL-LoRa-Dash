# To Do

- [ ] Use options from ATAK apps
- [ ] Add a search bar in the Chat column.

25 October update (debug APK built on request): My Nodes/Sending options help toggles, Connect All Available busy indicator, Message Split enable/interval, “Pause before next message (seconds)”, 5-second default MC observation starting at acceptance, late-repeat report reconciliation, active-only progress bubbles above Send Next and Finished heading. Java/app-test compilation and browser regression checks pass; phone/radio verification remains pending. Existing saved observation timeout values are preserved.

Source-only follow-up: manual split-chain controls added (Cancel remaining, resend previous finished part with confirmation, Send Next skips the remaining MeshCore channel observation and continues automatically). Chat action/font-bar placement verified. No further APK builds unless the user requests one; native compilation and browser tests are permitted. Phone checks remain pending.

## 0.10.0 implementation — 24 October 2026

Implemented the agreed feature set and history changes; see [implementation notes](IMPLEMENTATION-0.10.0.md) for scope and limits. Host regression checks and APK builds pass. Device instrumentation and actual BLE/USB/RF behavior remain unverified; run the new PHONE-TESTS.md checklist. CSV export still builds output in memory; measure very large exports and live-RX import contention on the phone.

## Agreed feature scope — 22 October 2026

Original design report: [FEATURE-PLAN-2026-10-22.md](FEATURE-PLAN-2026-10-22.md). Current implementation: [IMPLEMENTATION-0.10.0.md](IMPLEMENTATION-0.10.0.md).

Maintain [UPSTREAM-DIFFERENCES.md](UPSTREAM-DIFFERENCES.md) as features land. Recheck relevant upstream behavior during development and perform a full comparison before release; do not conflate upstream OverMesh with the official MeshCore/Meshtastic apps.

- [x] Verify Meshtastic roles; preserve deprecated wire values and label legacy roles clearly.
- [x] Add Last Heard ascending/descending sorting with unknown timestamps last.
- [x] Add Connect All Available for discovered known BLE radios and attached USB serial radios, avoiding duplicate connections.
- [x] Add configurable MeshCore DM known-route and flood attempt counts; retain 4 + 4 defaults on supported firmware and legacy constraints.
- [x] Add Config-gated Send to multiple with per-radio contacts/channels and sequential/concurrent modes.
- [x] Persist batch progress, pause/resume/cancel, deferred items, explicit unsuccessful-item retry review, and restart recovery without automatic sends.
- [x] Add MeshCore long-message splitting for channels and DMs with UTF-8-safe limits and ordered per-chunk progress; reuse persistent sending machinery.
- [ ] Add meaningful automated regression checks and phone checklist; build debug APK and document completed changes and remaining hardware verification.

## MC ambiguous path follow-up — 21 October 2026

- Confirmed partial earlier fix: Message Paths filtered to repeaters, but native trace hop matching still included non-repeater contacts. Corrected trace lookup while preserving explicitly identified discovery endpoints.
- True collisions now list matching repeater names and IDs in Message Paths and traces. Do not select a candidate arbitrarily or claim the user's original `08` case is resolved without phone evidence.
- Still needed: the affected path and matching repeater details, preferably with the same path shown in the official app, to determine why the original user-reported case differs.

## Implemented in 0.9.9 — awaiting phone verification

- Expand diagnostics from radio-focused logging to app-wide debugging: user actions/navigation, lifecycle, JavaScript/WebView errors and handled operation failures. Preserve timestamps and useful crash details; exclude message bodies and keys. Keep 10 MB total as five rotating 2 MB files, surviving restarts, with chronological export.
- Put **Export debug log** directly under **Config**. Remove it from **Config → My Nodes** and from **Log**.
- When new unread messages arrive, visibly highlight the app's Android status-bar icon and its notification card in the pull-down notification shade. User requests a glow for both. Check Android-supported presentation before implementation; make the unread state distinguishable and clear it when the relevant messages are read.
- Play a sound when a connected device disconnects unexpectedly. Do not alert for intentional Disconnect Selected/Disconnect all actions or deliberate app shutdown.
- Under **Notification sounds**, add two clearly distinguishable framed groups: **Message Notification sounds** and **Unexpected Disconnection of Device**. Preserve existing message-sound settings and add a separately saved user-selectable sound for unexpected disconnections.
- Added MT reaction sender dialog, MeshCore zero-hop Ping (repeaters/repeating room servers, selectable 1/2/4-byte IDs), and ascending/descending hop sorting with unknown counts last.
- Android controls status icon color and notification rendering: use a distinct unread icon, green large-icon glow and requested notification colorization; verify on the phone.

## Next session — discuss before writing code

- [x] Compare upstream OverMesh and Android message-history implementations before deciding changes. Source review completed 23 October: [HISTORY-COMPARISON-2026-10-23.md](HISTORY-COMPARISON-2026-10-23.md). Current application behavior is unchanged.
- [x] Agree and implement history-review changes: pagination without automatic deletion, imported chronology, Last Heard backups, atomic streaming JSON import/export and restart-state normalization. Phone validation remains pending.

- Add filters in **Nodes** to show nodes according to the number of hops or repeaters needed to reach them. Discuss the filter choices and how available MT/MC route information maps to them before implementation. Keep unknown route lengths explicit rather than treating them as zero hops.


## Implemented in 0.9.8 — awaiting phone verification

- [x] Independent left/right Chat device selectors for all connected/saved radios; same-family selections supported.
- [x] Dynamic MC/MT pills, blank when unselected, red when offline; saved channel and DM history stays available offline.
- [x] Device selectors next to the pills; channel selectors in the second row using radio-reported names.
- [x] Long-press DM for saved conversations; tap to open; long-press names for Delete this Conversation / Cancel. Scope deletion and drafts to the device and peer.
- [x] Remove the Config DM shortcut while retaining View Messages.
- [ ] Run the three-radio, reconnect, offline history, deletion and Android inset checklist in PHONE-TESTS.md.

## Implemented in 0.9.7 — awaiting phone verification

- [x] After successful send queueing, close the keyboard and restore the same channel/DM view; preserve drafts on rejection.
- [x] Render incoming MT Unicode reactions below the original message, aligned left; offer outgoing reactions 👍 first, followed by 0–7. Persist MT DM packet references and reaction metadata.
- [x] Remove the MC 50-message loading cutoff, coalesce message reads, and report stalled loading as an error requiring reconnect.
- [x] Gate BLE connections on permissions and Bluetooth activation, resume selected radios after acceptance, and clear stale BLE states when Bluetooth turns off.
- [x] Stop sessions and remove the notification when removed from Recents; retain My Nodes → View Messages.
- [ ] Run the 0.9.7 phone checklist for actual keyboard/navigation behavior, Bluetooth prompts, Recents cleanup, schema upgrade, MC backlog loading and official MT reaction interoperability.

## Implemented in 0.9.6 — awaiting phone verification

- [x] In both MT and MC channel and DM chat, place all delivery/status information below the message text, including MC “Sent”, MT “Delivered to mesh”, queued/retrying, and “Failed: all radio retries exhausted…” explanations. Keep sender information above the message. Remove “Channel broadcasts have no delivery ACK” from MC chat; do not replace it with an unsupported delivery-confirmed claim.
- [x] Investigate MC Heard Repeats displaying the user's own repeater as “08 ambiguous hash” although other MC apps resolve its name. Compare official resolution behavior and actual contact/path data, including repeater filtering, duplicate records and hash widths. Resolve names from available evidence without inventing an identity when a real collision remains.
- [x] Provide accessible lists of stored MT and MC DM conversations for every known local radio/device. Users must be able to open saved conversations and continue them using the corresponding connected radio. Retain offline access to history and clearly handle reconnecting before sending.
- [x] Remove unnecessary “Direct message sent to radio for …” notices above the DM composer. Fix shared/stale action-result text leaking into Nodes (below its node count), Config → MC Adverts, and unrelated screens. Keep relevant delivery state below the message instead.
- [x] In Nodes, place “Hide unmessageable nodes” alone on its own line; move the remaining controls to the next line.
- [x] Reply to a channel message must stay in that channel rather than opening a DM. Check the official MC and MT apps and match their channel-reply methodology. Preserve a separate explicit DM action where appropriate.
- [x] Apply the main app theme consistently to all app-owned windows/dialogs, including native dialogs currently using black text on white. Respect system-owned permission/picker UI boundaries.
- [x] After a successful MC Advert action, change the dialog's Cancel button to OK and show the confirmed result. Wait for actual command success; failure must not appear as success.

- [x] Add Config → About. Identify this app as a fork of Slofi/OverMesh, link the upstream project, distinguish it from the official project, say it is under development, show “Beta edition” with the actual installed version, and credit “By SV8TTL”. Show the existing MIT license and upstream copyright attribution plus applicable dependency notices. Read LICENSING.md before implementation; the author credit must not replace inherited notices or imply a new license has been chosen.
- [x] Hide nodes not heard in the last 30 days from the Nodes lists for both MT and MC. Treat this as a visibility filter, preserving saved messages and records. Use real last-heard timestamps, persist observations across restarts, and let nodes reappear when heard again. Handle missing timestamps explicitly rather than inventing recent activity or confusing connection time with last-heard time.

- [x] Start on Config when the initial state has no connected radios; later disconnects do not force navigation.

The reported physical `08` contact/path data was not available locally. Repeater-only candidate matching, duplicate-identity removal and 1/2/3-byte hash tests pass; compare that specific case on the phone. Missing timestamps stay explicitly unknown.

## Phone feedback — implemented in 0.9.5, awaiting device verification

- [x] Keep editor focus for Send/A−/A+; preserve drafts and conversation on unexpected disconnect and show an OK warning.
- [x] Green CONNECTED indicator, unnamed contacts last, complete known MT model-ID labels and correct proto3 default Client role.
- [x] MC broadcast status explains absence of delivery ACK; Heard Repeats below text and in own-message menu; incoming channel paths captured from authenticated raw logs and persisted.
- [x] Notification permission, saved-device Bluetooth permission request, status icon and connected-device foreground service.
- [ ] Verify the 0.9.5 phone checklist, including both Android navigation modes and real BLE/USB loss.
- [ ] Incoming MC DM path details are not captured: encrypted raw DM packets need additional protocol support. Old channel messages without captured logs cannot recover paths retroactively.

## Heard repeats — implemented in 0.9.3, awaiting radio verification

- [x] Match raw MC channel flood copies against exact encrypted outgoing payloads; support scoped floods and 1/2/3-byte hashes.
- [x] Persist counts in SQLite and JSON backup; show Heard repeats in outgoing channel bubbles and explain their meaning in the message menu. Counts are reception events, not unique repeaters or recipient ACKs.
- [x] Preserve unavailable for old messages; do not invent observations across disconnected periods.

## Automatic retries — implemented in 0.9.2, awaiting radio verification

- [x] MT channel/DM: request ACKs and let the connected firmware manage retransmission and route fallback. No additional app-generated automatic sends.
- [x] MC channel: one send, radio acceptance only; official web-app channel send flow has no automatic retry loop.
- [x] MC DM: match official default budgets using the device's protocol version. Before version 4: two known-route attempts then one flood, or three floods if no route. Version 4+: four known-route attempts then four floods, or four floods if no route.
- [x] Wait for each suggested timeout plus 1–8 seconds randomized delay, reset the route before flood fallback, preserve timestamp/text/message identity and correlate ACKs from all attempts.
- [x] Stop on ACK, command rejection, disconnect, deletion or Stop retries. Show attempt count/route. Late ACKs may still confirm an unconfirmed message. Reconnect after an untagged command-response timeout.
- [x] One bubble/database row per logical send; a manual Send Again starts a new logical message only after automatic retries stop.
- MC DMs now use the official 150-byte composer limit (0.9.4); no length-dependent retry exception remains. Official default retry budgets are used; preferences are not imported from the official app.
- [x] MT 200-byte composer limit, MC 150-byte DM limit, MC channel limit of 160 minus exact UTF-8 radio-name bytes minus two. Counters block oversized sends and retain drafts.

## Implemented in 0.9.0 — awaiting phone verification

- [x] MC/MT message and contact holds survive UI updates; suppress native text selection.
- [x] MT channel tap offers reply/cancel; MC message tap offers message actions/cancel.
- [x] Track radio writes, queue acceptance, acknowledgments, errors and unconfirmed delivery; offer explanations and retry.
- [x] Display MT outward/return traces and MC receiver path discovery; expose learned-repeater tracing separately.
- [x] Expand the composer; persist message, composer and side-list text sizes; bold purple sender names.
- [x] Add MC Adverts with connected-device selection, Zero Hop, Flood Routed and clipboard export.
- [x] Reorder Config: My Nodes, Add Radio Connections, Notification Sounds, then remaining menus.
- [x] Save future independent-fork licensing/provenance decisions in LICENSING.md and include upstream MIT notice in APK assets.

Protocol fixtures and browser simulations pass. Physical radio behavior remains unverified. MC message path views show only data actually received; unavailable repeater identities and heard-repeat details are not reconstructed.

## Implemented in 0.8.0 — awaiting phone verification

- [x] Show a visual notification when a new message arrives while the app is running, similar to the banner used by messaging apps.
- [x] Change the top-right clock separator to `HH:MM - DD/MM/YYYY` (24-hour time).
- [x] In Config → My Nodes, provide a Connect button for each known device and checkboxes with a Connect Selected button below the list. Allow any number of devices to be selected.
- [x] Investigate MT contacts that show a node ID even though a user-given name is available. Display the correct name wherever possible.
- [x] Make long pressing a messageable contact in Chat open the same node actions as tapping that contact in Nodes. Compare MC and MT actions and add missing MC actions, including TR where supported.
- [x] In MT channel Chat, show the connected device's long name in the middle-column title instead of its node ID.
- [x] Keep Export chat as CSV in DM Chat only; remove it from the long-press menu for users in Channels Chat.
- [x] Add Config → Channel backup. Let the user choose a connected device and one of its reported channels, then export that channel's chat as CSV.
- [x] Make Android Back return to the previous place in the app instead of exiting immediately. Check navigation through Chat, Nodes, Config subpages, and popups.
- [x] Add an X button to the right of the top-right date to close the app.
- [x] Stop text from being selected when long pressing a user to open a popup. Tapping outside that popup should dismiss it.

These items are implemented. Build and simulated UI/protocol checks passed; use PHONE-TESTS.md for physical-device verification.

## DM lists

- Let users create and name multiple DM lists.
- Allow a list to contain contacts from different connected MC and MT devices. Store each member with its radio type and device identity so contacts with the same name stay distinct.
- Let users send one direct message to every contact in a selected list. Show delivery or failure separately for each recipient.
- Allow lists to be renamed, edited, and removed.

## Forward messages

- Add a Forward action to channel and direct messages.
- Let users choose another channel on a connected device or a DM recipient as the destination.
- Decide how the original sender, source channel, and time should appear in a forwarded message before implementing its final format.

## Common space

- Investigate how users running this app could converse in a shared space, including identity, transport, message history, moderation, and behavior when offline.
- Compare options that keep the app standalone on Android and work with MC and MT radios where feasible. Choose a design before implementation.

