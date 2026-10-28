# Phone checks

## 25 October changes — debug APK built; phone checks pending

- Toggle each circled-i in My Nodes and Sending options twice; check independent show/hide, including saved history help. Start Connect All Available and verify immediate busy indication through USB permission, Bluetooth activation, scanning and connections; completion/rejection must clear it.
- Disable Message Split: oversized Chat and multi-send submissions must be rejected with drafts retained. Enable it and change the 1–300 second interval; check ordinary Chat spacing and minimum multi-send split spacing. Already queued parts may finish after disabling.
- MC Public multi-send: queued messages must appear in Chat when dispatched. The observation window starts at radio acceptance (default 5 seconds only if no saved override). Hear a repeat before/after expiry: progress must reconcile both, without a late repeat restarting another message's pause. Recipient delivery must not be claimed.
- Progress shows only active message bubbles immediately above Send Next (one per active radio in concurrent mode), with body/status/repeat count. Completed messages remain in the textual report. Once all work ends, show Finished even with unconfirmed results. Check pause/resume, Send Next, DM retries and MT acknowledgments.
- Repeat keyboard-open/closed checks in portrait/landscape with gesture and three-button navigation. Short landscape composer height is capped to avoid the bottom navigation.

## 0.9.8 priority checks

1. Connect one MC and two MT radios concurrently. Both Chat device selectors must offer all three. Select the two MT radios on opposite sides, then move MC to the right. Check each pill's letters, device name, reported channels, history and send target. Switching a Chat slot must not disconnect or replace another radio session.
2. Disconnect one selected device: its pill turns red and its saved channel/DM messages remain visible, while sending is disabled. Restart and select a saved offline device; saved channel names and available peer names remain visible. Clear a selector: its pill has no letters. Reconnect the same device and verify its existing selection resumes sending only when ready.
3. Long-press DM: each side shows only peers with saved conversations for that side's selected device. Tap peers, including the same peer on two local radios, and verify the middle conversation and outgoing device. Drafts must not carry into a different device/peer. Long-press a saved name: only Delete this Conversation and Cancel appear. Cancel preserves history; Delete removes only that device/peer's local messages, including offline history. Verify deletion survives restart and does not affect another device's conversation.
4. Repeat portrait/landscape, gesture/three-button navigation and keyboard open/closed. Verify selectors, chat, bottom navigation and touch targets stay inside Android insets. My Nodes still has View Messages; saved DM browsing requires no Config shortcut.

Five browser suites cover the UI regression scenarios, including a three-radio fixture. Real BLE/USB concurrency and Android lifecycle/insets still require physical device verification.

## 0.9.7 priority checks

1. Upgrade without clearing history. On both MC and MT, in channel chat and DMs, focus the composer and send: after queueing, the keyboard closes and the original conversation and side lists return. Oversized/rejected drafts stay editable. Repeat portrait/landscape with gesture and three-button Android navigation.
2. Exchange MT channel and DM reactions with the official app. Receive simple emoji, variation-selector hearts and multi-character emoji; check the lower-left chips attach to the original message rather than becoming standalone messages. Tap a message here and confirm 👍 appears first, then send 👍 and each digit 0–7. Confirm the official app attaches it correctly and that delivery/error state is honest. MC must not offer the reaction chooser.
3. Restart/reconnect and export/import JSON backup: reactions remain associated with the right device, channel or DM peer. Test two devices with separate histories, repeated received packets, a reaction arriving before its original, and old messages lacking saved packet IDs. Older missing references cannot be reconstructed.
4. Connect MC with an empty queue, then a queue containing at least 50 messages. The loader ends only on the radio's no-more-messages response. If a response stops arriving for 30 seconds, show a loading/reconnect error rather than claiming success; reconnect and finish loading. New-message notifications during loading must not cause overlapping message reads.

5. With Bluetooth off, select multiple saved BLE radios and tap Connect Selected. Grant Nearby devices permissions and accept Android's enable-Bluetooth prompt: all selected radios should attempt connection only after Bluetooth is on. Repeat with permission denied and activation cancelled: none should appear connected. Toggle Bluetooth off during active sessions and verify BLE connections clear while USB remains usable. Repeat via Add Radio Connections, including Android versions before and after 12.
6. With active radios, background the app, then use Recents → Close all. Confirm the status icon/notification disappears and connections stop. Repeat without connected radios and using the app's X button. Reopening should start at Config without stale connected states. Verify My Nodes still offers working View Messages for saved histories.

Local regression coverage: seven Java protocol suites and four browser suites. Native IME dismissal, Bluetooth permission/activation dialogs, Recents lifecycle, schema upgrade and official-app interoperability still require a phone/radio test.

## 0.9.6 priority checks

1. Upgrade without clearing data. Launch with no connected radios: Config should be the initial screen. Switch to Chat; subsequent radio updates must not force Config again.
2. In Chat's MC/MT device selectors, choose each offline known radio and open both received and outgoing-only DM conversations. Reconnect the corresponding radio in Config and continue the conversation. A different radio must never receive that draft. In 0.9.8, long-press DM in Chat for saved conversations; the old Config shortcut is removed.
3. Check MT/MC channel and DM bubbles: sender/time above, body next, all delivery/retry/error status below. No persistent sent notice should appear above the composer or leak into Nodes/MC Adverts.
4. Reply to channel traffic: MC inserts `@[name]`; MT stays on the same channel and sends the original packet's reply reference. Check against official apps. Old MT history without recorded packet IDs explicitly cannot supply that reference. Direct message is a separate action.
5. Compare MC Heard Repeats with the same radio's official app: only repeater contacts qualify; duplicate identities do not create collisions. One-, two- and three-byte hashes must use their full width. Two distinct matching repeaters remain ambiguous. Verify the reported `08` case on the actual contact data.
6. Nodes shows contacts heard in the last 30 days. Old records and DMs remain stored; unknown timestamps are hidden unless Show unknown last heard is checked. Reconnect/restart and confirm an old record does not become recent merely by loading it. Receive a fresh advert/packet and check reappearance.
7. Check dark themed app dialogs (disconnect warning, delete, BLE/USB chooser, import results). System permissions and document/sound pickers follow Android's theme. Repeat with gesture and three-button navigation, portrait/landscape and the keyboard open; check header, tabs, reply strip and composer insets.
8. MC Advert: Cancel remains during sending, rejection and timeout; confirmed radio success changes it to OK. Switch dialogs/devices during an operation and check results do not leak.
9. Config → About shows Beta edition 0.9.6, By SV8TTL, the upstream fork/development statement, MIT notice and dependency notices. Open the upstream link and scroll the licenses.

Local validation includes five Java protocol suites and three browser suites. No phone or emulator was connected; native theme, database upgrade, Android navigation insets and physical BLE/USB radio behavior require these device checks.

## 0.9.5 priority checks

1. Upgrade over 0.9.4 without clearing data. Confirm histories, heard counts, and font preferences survive the schema-8 upgrade.
2. With the keyboard open in both channel and DM chat, tap Send once. Confirm one logical message appears. Tap composer A−/A+ while typing; keyboard, cursor and expanded layout should stay in place. Repeat with gesture and three-button navigation, portrait/landscape.
3. Send MC channel traffic through repeaters; inspect Heard Repeats below the text and in the short-tap menu. Outgoing messages should not offer View Message Paths. Receive new channel traffic; inspect path hashes, matched names and SNR/RSSI. Compare against official app observations on the same radio. Restart and verify saved paths remain; old messages may have no captured paths.
4. Interrupt BLE/USB while a channel or DM draft is open. Confirm an OK warning, unchanged draft/conversation, and disabled Send until reconnected. Deliberate Disconnect should not warn. Interrupt a background connection and confirm the warning appears on return.
5. On Android 13+, allow notifications and verify the running status icon and shade notification; tap it to return. Verify connected radios remain usable after briefly backgrounding. Disconnect all, then close with X; notification should update then disappear. Test denial and re-enable through system settings. Test a saved BLE connection after revoking Bluetooth permission; it should request permission again.
6. Verify green CONNECTED in My Nodes, unnamed MT contacts last, and hardware/role labels against actual node information. A node without user information cannot supply a model or role.

Local validation: assembleDebug succeeded; four Java protocol suites and both Playwright suites passed, including first-tap sending, focused font changes, draft retention and authenticated channel-path parsing. Native permission dialogs, foreground service lifecycle, database upgrade and actual radio receptions remain phone checks.

## Earlier checks

The build and simulated browser/protocol tests do not verify physical radios. No Android phone or configured emulator was available during this session.

1. Install the new debug APK over the existing preview. Confirm saved histories and settings remain available.
2. In My Nodes, tick several MC/MT devices. Confirm ticking alone does not connect; Connect Selected attempts each selected device. Check individual Connect and Disconnect buttons as well. Verify BLE and USB on the phone with the computer off.
3. Connect MT and wait for node information. Check the local device's long name in the channel title and selectors. Check contact names in Nodes, channel user lists, DM lists and message sender labels. A node without reported names should retain its ID. Test a name update arriving after connection.
4. Long press MC and MT users in both Chat modes, including enlarged lists. Compare actions with Nodes. MC TR needs a compatible learned route; missing-route and unsupported-route errors should appear in the popup. Check no text is selected, outside taps dismiss, and a drag to scroll does not open actions.
5. Check CSV appears only in DM contact menus. In Config → Channel backup, choose each radio and a reported channel; export and open the CSV. Confirm it contains only that device/channel's messages, preserves commas, quotes and multiline text, and excludes DMs. Disconnect the chosen device and confirm export becomes unavailable or moves to another available selection.
6. Receive channel messages and DMs from both networks while viewing Chat, Nodes and Config. Confirm no message preview banner appears. In Config → Notifications, background message notifications must initially be off. Enable them on Android 13+ and test granting, denying, and denying again; denial must leave the switch off. With permission granted, background the app and receive MC/MT channel messages and DMs: verify native sender/text previews, tap to return, and clearing on return. Turn the switch off and confirm no background previews or message sounds; foreground sound settings remain independent. Disable the app or message channel in Android settings and verify the blocked status on return. Re-enable through the settings button. Verify radio service notifications continue separately, and closing the app clears previews. Repeat messages already saved should not generate another arrival event. These native checks remain pending device testing.
7. Navigate Chat → Nodes → contact details → DM → Config subpages. Use Android Back through the previous screens and dismiss popups first. At the initial screen, Back should stay in the app. The X beside the date should close the activity and its radio connections.
8. Repeat with gesture navigation and three-button navigation, portrait and landscape, and keyboard open/closed. Verify header/date/X, bottom tabs and composer stay inside status/navigation/cutout/keyboard insets. Browser viewport checks are only approximations of available screen space.

## Local regression checks

- `gradlew.bat assembleDebug`
- `tests/ui-smoke.cjs` with Playwright: menu actions, navigation, multi-selection, channel backup bridge arguments, background notification controls and blocked status, removal of the message banner, action notice forwarding, narrow/reduced viewports and close control.
- Compile `tests/NameProtocolSmoke.java` with `RadioProtocol.java` and `ProtoWire.java`, then run `org.overmesh.android.NameProtocolSmoke`: short-name fallback, long-name preference, rename, partial updates and live node-info packets.

## Version 0.9.0 radio and touch acceptance

Record phone, Android/WebView version, radio firmware, BLE/USB transport and outcome for each test.

- Hold MC and MT users/messages while traffic refreshes Chat. Actions must open without selecting the MAP icon or other text. Test finger drift, scrolling and cancel/outside tap.
- Tap MT channel messages for Reply/Cancel. Tap MC channel and DM messages for message actions/Cancel; test copy, reply and confirmed deletion.
- Send MT channel and DM messages: distinguish local write, radio queue acceptance, mesh acknowledgment and recipient acknowledgment. Disconnect, provoke an unreachable destination, inspect explanations and retry. Verify drafts survive rejected sends and retries stay on the original radio/channel/peer.
- Run MT TR and verify outbound/return nodes against official-app results. Missing return data must stay unavailable. Disconnect during TR; the pending popup must end.
- Run MC TR to a client and repeater; compare both discovered directions with official results. Test direct routes, unknown/ambiguous hashes, mixed hash widths and timeout. Test the separate learned-repeater trace and its SNR; it must not claim a client's final link was measured.
- Expand the composer while typing; change message/composer/MC-list/MT-list A−/A+, restart the app and verify all sizes. Sender names should remain bold purple.
- Check the keyboard, portrait/landscape, gesture navigation and three-button navigation on real Android: inputs and buttons must remain reachable above system bars.
- Config order begins My Nodes, Add Radio Connections, Notifications. In MC Adverts select each connected MC radio, send Zero Hop and Flood Routed, and verify reception/routing with another radio. Copy to Clipboard must produce a usable meshcore:// advert link that the official app can import.

Build and simulated checks do not establish these physical results.

## Version 0.9.1 message limits

Test exact-limit and one-byte-over drafts in MT channel/DM (200), MC DM (150 in 0.9.4), and MC channel (160 minus UTF-8 radio-name length minus 2). Include Greek text, emoji and pasted multiline text. Change selected devices and confirm the counter changes. Verify excess text is retained and cannot be sent; confirm the received MC channel message is not truncated. Use the 0.9.2 retry checks below for MC.

## Version 0.9.2 retry acceptance

- Compare against the official MC app with default retry preferences. Record firmware protocol version, known/unknown path, suggested timeout and observed transmissions. Known zero-hop paths count as known routes.
- With recipient offline, verify old firmware: two known-route attempts then one flood, or three floods without a known path. Protocol v4+: four known-route plus four flood attempts, or four floods without a path. Each waits suggested timeout plus 1–8 seconds jitter. At 150 bytes, verify the same full budget; 151-byte drafts must be blocked without truncation.
- Bring receiver online during each phase: its acknowledgment must stop retries and update the original bubble to Delivered to recipient. Test delayed ACK from an earlier attempt, including while a reset response is pending. Check timestamp/text stay identical across automatic attempts.
- Verify exhausted attempts show unconfirmed, with no new bubble per attempt. Test Stop retries, deletion, disconnect, restart and two concurrent DMs. Confirm a manual resend cannot start while that message is still automatically retrying.
- During a pending DM, send an advert/channel message: command replies must not acknowledge the wrong operation. Simulate missing radio-command response: all automatic sends should stop and request reconnect.
- MC channel sends should not automatically repeat or claim recipient delivery. MT channel/DM retransmissions should be performed by firmware without duplicate app sends; compare routing error/ACK results with the official app.
- Keep the preview running for these tests. Active sessions have a foreground service; removing the app from Recents stops those sessions.

## Version 0.9.3 heard repeats

- Send an MC channel message with a nearby repeater. Compare Heard repeats with the official app under equivalent conditions. Counts increment for received copies, including repeated receptions from one path; they do not imply recipient delivery.
- Test two channels/devices, different sender names, UTF-8 names/text, rapid identical sends, scoped floods and 1/2/3-byte repeater hashes. Unrelated packets must not increase the count.
- Restart the app and reload saved history; the recorded count must persist. Export/import JSON backup and confirm the count survives. Pre-0.9.3 messages should show unavailable, not a fabricated zero.
- No extra RF packet is sent to measure this. Raw receive logs are required; repeats missed while disconnected cannot be reconstructed. Matching is limited to the most recent 256 sends in the current connection. A saved count remains visible after reconnect, but the old connection's matching state is cleared.


## Pending changes — 20 October 2026 (APK build deferred at user's request)

- Export diagnostics from Log or Config → My Nodes after reproducing a failure; confirm timestamps, connection stages, packet lengths and write results. Restart the app and export again to check persistence. Logs rotate at about 1 MiB per file (two files); raw packets, message text and channel keys are excluded.
- My Nodes: connected radios remain selectable, CONNECTED is green, MT/MC badge sits at top right, and an indeterminate progress bar runs during connection and loading. Test Disconnect Selected and Disconnect all, including while permission/activation is pending and during loading.
- Reflash a saved radio between MT and MC, reconnect by BLE and USB, and verify the detected family, channels, histories and selectors. Old-family saved history should remain available offline. Unsupported services, denied permission, cancelled USB selection and stalled handshakes must stop progress. USB detection uses framed identity replies to alternating read-only initialization probes.
- Nodes: Show unknown last heard and Hide unmessageable nodes appear below device selectors. Check gesture navigation, three-button navigation, display cutouts and open keyboard on a phone.
- Short tap an MT message → Emoji → 👍 or 0–7. Verify correct radio/message reference, closed popup and preserved chat scroll position for channels and DMs. MC must not offer Emoji. Offline/initializing MT radios cannot send reactions.
- Browser smoke and pure Java detection checks are automated; neither proves radio behavior. Full Android compilation/APK build remains pending after the latest changes.


### MeshCore loading follow-up

- Compare cold reconnect with the same contact/channel counts and a comparable queued-message backlog. Export logs immediately after loading finishes. `sync complete` includes elapsedMs, message count, contacts and channels; MC TX/RX entries include protocol type without packet contents.
- Unsupported binary channel data (response 27) must not block later text. It is skipped as in the official web app, with a diagnostic status, rather than rendered as text.
- Initial loading coalesces receive-driven WebView refreshes into 100 ms windows; completion and user actions still refresh immediately. Incoming text records now insert hops, unread state, packet/reply references and captured paths in one database write. Verify those fields survive restart for channel messages and DMs.
- LoadingProtocolSmoke covers a backlog over 120 items, a binary item followed by text, single in-flight pulls, completion, pushes and stalled responses. Hardware throughput and full Android compilation remain pending; do not claim a measured speedup from these tests.

### Build confirmation — 20 October 2026
User authorized the build after discussion. gradlew.bat assembleDebug completed successfully, including Android Java compilation, debug signing and copyDebugApk. The root OverMesh-debug.apk contains the pending changes above. Physical-radio and system-inset checks remain pending.



## MC path names — 21 October 2026

- Verify a trace hash shared by a User and one Repeater resolves to the repeater; path discovery's explicit user endpoint must still show that user's name.
- Genuine short-ID collisions show all candidate repeater names/IDs. Check the reported `08` case against the official app using the same radio and loaded contacts; it remains unverified.
- Regression checks cover duplicate identities, case, 1/2/3-byte matching, unknown hashes, and selection of the correct radio's contacts. No replacement of actual path data or guessed repeater identity.


## 0.9.9

- Export debug log directly from Config; verify absent from My Nodes and Log. Use app navigation, trigger a recoverable connection failure, restart, then export and inspect chronological diagnostics.
- With the app backgrounded, receive a message. Verify distinct unread status icon and highlighted shade card; read the conversation and verify normal appearance returns. Repeat after last radio disconnects. Actual Android/OEM appearance may differ.
- Select a distinct disconnect sound and repeat count in its orange frame; message sounds remain independent. Verify unexpected BLE loss, Bluetooth off and USB removal sound once per affected connected device. Intentional disconnect and app closure must stay silent.
- Tap an MT reaction in channel and DM history: verify sender name/ID and Close returns to the same conversation.
- Ping a directly reachable MC repeater with each identifier length, then a repeating room server. Compare timing and both SNR values to the official app. Test no-response timeout and duplicate-prefix warning.
- Sort Nodes by increasing/decreasing hops across MC and MT; unknown hop counts remain last. Check the controls with gesture and three-button navigation, keyboard open/closed and display cutouts.
# 0.10.0 — required phone validation

Pending source-only manual-control tests: monitor a split DM and channel send. Press Send Next during an active MeshCore channel part: after radio acceptance, end its remaining observation, retain the settling gap, and continue automatically without pausing. Confirm this does not claim delivery or cancel active DM retries or Meshtastic transmissions. Repeat with concurrent mode, an unavailable original radio/contact, and rapid taps. Send again the previous must identify the most recently finished part and request confirmation, preserve the original result and destination, issue a fresh message/ACK identity, then pause. Cancel the resend confirmation and check the chain remains paused. Cancel remaining must stop pending/manual dispatch without recalling active TX. Restart with a manual step armed: no automatic dispatch. Controls are unavailable when there is no applicable previous/pending part. Compile-only validation has passed; these native controls have not been exercised on a phone.

0.10.1 additions: enable/disable multi-send in Config and verify its Chat button appears between Users pills. Check the message font bar below it in the centre column for DM/channel views, hidden side lists, portrait/landscape, keyboard and both navigation modes. Verify the MC channel-chain timeout defaults to 2 seconds, accepts 1–300, survives restart and does not change a currently active deadline. Without repeats, advancement should occur after the observation timeout plus the separate settling gap (default 3 seconds), subject to radio command availability. Receive a repeat before timeout and verify earlier advancement after the settling gap. DM/MT timing must remain unchanged. The older 120-second channel observations in the original checklist below are superseded.

These checks are pending. No phone/emulator was attached during implementation. Successful builds and browser/protocol fixtures do not establish radio behavior.

1. Upgrade from 0.9.9 without clearing app data. Verify radios, unread counts, reactions, paths and histories remain. Open histories with more than 200 records; load two older pages, preserve scroll position, receive a live message while reading older history, switch conversations during a pending page request, and repeat offline.
2. Export/import a full JSON backup with more than the previous 100,000-per-radio/300,000-total counts, including Unicode, Last Heard, reactions and existing duplicates. Verify complete counts and chronology. Try a malformed late record and low storage: database changes must roll back. Restart between database commit and settings application. Check unavailable sound URI guidance. Measure responsiveness during import while another radio receives.
3. Delete older messages in My Nodes and a DM, including records outside loaded pages. Keep unknown-date messages. Exports must include all retained records.
4. Connect All Available: known discoverable/non-discoverable BLE, attached known/unknown USB, denied permissions, Bluetooth off, unplug during discovery, two adapters of the same model, and the same physical radio available on BLE and USB. USB-first new connection must not duplicate a radio; existing working sessions remain connected.
5. MC modern firmware: test 1+1 and 4+4 attempts, known route and no route, reset before each fallback, late ACK, and change settings while a DM is active. Legacy firmware retains its supported budgets. Test stopping while waiting for ACK and during route reset: no new text transmission after Stop, but the current response window remains.
6. Enable Send to multiple in Config → Sending options. Pick MT/MC DMs and channels across at least three radios, including the same destination through two explicit source radios. Verify sequential default, optional concurrent mode, original-radio reconnection and honest per-target outcomes. Channel acceptance alone must not advance MC; repeat or observation timeout does.
7. Pause during each MC/MT DM/channel case. Verify RX/notifications remain live, active sends finish, pending ones do not start, Continue sends only pending items, and Cancel remaining never implies recalling active TX. Disable the feature while running. Kill/restart during active and paused chains: restore paused, active item unconfirmed, no automatic sends. Review unsuccessful items and select only intended retries; inject a late ACK before resumption.
8. Split MC long channel/DM text containing Greek, combining accents, skin tones, flags and family emoji. Check preview part counts, exact radio-name-dependent channel limits, order and retained pending parts across pause/restart. MT oversize text remains blocked.
9. Measure actual TX duration, repeater traffic, nearby-radio RX loss and collision behavior. Evaluate the provisional 3-second gap and 120-second channel observation window using real radio settings; adjust within Config's 1–30-second gap only from evidence. Test 2, then 4 simultaneous physical connections before making a capacity claim.
10. Check portrait/landscape, keyboard, cutouts, gesture navigation and three-button navigation for Chat, paging controls, Sending options and native chain dialogs.

Device regression harness (separate test database; no user-history deletion): build with `gradlew.bat assembleDebug assembleDebugAndroidTest`, install both APKs, then run `adb shell am instrument -w org.overmesh.android.test/org.overmesh.android.HistoryInstrumentation`. It tests SQLite paging/retention/migration, streaming round-trip and saved-chain interruption. It does not transmit radio packets.


25 October follow-up: select two saved radios and Connect Selected repeatedly across fresh launches and reconnects. Verify the second shows queued until the first completes initial sync; both remain connected afterward. Repeat with first radio unavailable, permission denied, Bluetooth off, USB picker cancelled, and no USB attached. Cancel a queued radio and use Disconnect all while another loads; cancelled entries must not connect later. Confirm short names share a line with green Connected and long names wrap without overflow. Config must end with Export debug log / About. These connection tests remain pending on physical radios; no APK was built.

25 October message menu follow-up: verify short taps on MT channels show Copy Text, Reply, Emoji when available, Direct message, Node actions, Delete Message and Cancel. Long press must still open the sender's node panel. MC unknown/duplicate names must not target an arbitrary contact. Browser menu and reaction checks passed, but the complete chat-actions regression stops at its composer/navigation geometry assertion in the small-viewport sweep; that layout issue remains open. No APK built.

0.10.2: With Send to multiple disabled, send 210 ASCII bytes in MT channel and DM; expect 2 parts, confirmation preview, sequential progress and pause/resume/cancel. Check MC/MT live counters with Greek, emoji, and family emoji; compare preview counts. Verify split MT channel replies retain their original reply reference. Test restart while paused and disconnect/reconnect; firmware retries cannot be recalled. Physical radio delivery is still unverified.

0.10.3: cancel a split preview using Cancel, Back and outside dismissal, then send an edited short message. Repeat for DM. With an unfinished chain present, attempt another split; draft must remain editable and sendable after closing the monitor. Confirm successful acceptance clears only the submitted draft. Check Split message progress explains radio-operation waits and reconnect-required timeouts. Reproduce the original MC stall with a fresh debug log if it persists. Native dialog/radio behavior remains pending on device.

0.10.4 replaces previous single-destination split-chain tests. With multi-send disabled, press Send/Reply on oversized MC/MT channel and DM text: no dialog, all parts visible as queued, first starts immediately and subsequent submissions are at least 1 second apart without waiting for repeats/delivery. Test slow companion responses and disconnection: no command overlap, unsent parts visibly failed, no endless pending state. Close via X midway; restart and open Send to multiple: no split chain should appear. Upgrade with an old saved split chain and separately a genuine multi-send chain; only the latter remains resumable. Phone tests remain pending.

0.10.5: Cancel Chain while pending, paused, completed and while a message is active. Expect No saved sending chain, unchanged Chat history and a new multi-send immediately selectable. Restart and confirm the cancelled chain stays absent. Verify already-issued delivery updates still reach Chat, MC future attempts stop, and no queued destination is sent after cancellation. Native regression tests compile; device execution remains pending.

0.10.6: select a radio in the dropdown, check a channel, switch to DM, search/select several nodes, change/clear the search and verify checks survive. Save Destinations, choose another radio and repeat; return to the first radio and verify both channel/DM sets remain. Deselect an item and save again. Send Chain must reject unsaved edits, empty message, no destinations and a disconnected radio without losing the draft. With all choices saved, Send Chain starts directly. Cancel and reopen must show a fresh selection; actual running chains still retain their own destinations. Verify keyboard, small screens, gesture and three-button navigation on device.

0.10.7 supersedes the 0.10.4 split timing expectations: each part waits for the previous result/retry window (MC channels use repeat observation), then the configured split interval. Check 1, 4 and 10 seconds on MC/MT channels and DMs, delayed writes, failure, disconnect, and a split queued while a multi-send is active. All bubbles may appear immediately as queued; actual submissions must stay paced.

0.10.7 device checks: Connect All Available uses the existing amber/dark palette. Import/Export node backup are below My Nodes; export offers saved nodes. The progress i button is directly below Send Next. Retry a failed middle/final part and verify the whole chain and earlier attempts stay visible, the resend appears active, and all-success shows “Chain Completed Successfully” with OK exactly once. Pending, cancelled or unconfirmed rows must prevent success. Reopen/restart the chain to check persistence.

Create/edit/delete named destination sets in Config → Sending options. Combine a saved set with manual selections in Send to multiple; verify no duplicate destinations and no silent omission when a required radio is unavailable. Reconnect the same radios in different slots and confirm targets still resolve by identity. Export/import App Backup and verify sets, names and destinations survive. Use both gesture and three-button navigation, keyboard open/closed and display cutouts on the native composer/progress/set dialogs. These native/device tests remain pending: no phone or emulator was connected. SplitPacingSmoke and browser regression tests passed; instrumentation tests are compiled separately and require a device to execute.

0.10.8 supersedes automatic advance after an ordinary split failure: explicit errors, exhausted retries and unconfirmed channel observation now pause for Retry / Cancel. Verify the prompt shows the exact failed part and reason, no later part is sent while it is open (including in the background), Retry resends only that part after the configured interval, and Cancel stops the remaining parts of that message. Test final-part failure, repeated failure, BLE/USB disconnection then reconnection in another slot, missing channel/contact and radio busy. A retry must only use the original radio identity. Earlier attempts remain in Chat; missing confirmation is not proof of non-delivery. Closing the app cancels these ordinary split queues; they are not persistent multi-send chains.

Sending options now has three buttons opening separate windows: Send to Multiple, Message Split and MeshCore DM attempts. Attempts are absent from Add Radio Connections. My Nodes → View messages opens a scrollable window with X at top right and Close at bottom. Test both controls, Android Back, keyboard resizing, gesture and three-button navigation, channel/DM switching, exports and opening Chat; late history responses must not reopen a closed window. Browser tests cover constrained viewport layouts and dismissal; actual Android navigation/cutout behavior and physical radio behavior remain pending device testing.

26 October UI follow-up (source only; APK build deferred by user): display name is OverMesh SV8TTL beta. Node actions offer Enter Name for a private, network/node-ID-scoped label (blank removes it); labels are stored in app preferences and therefore included in App Backup. Check persistence across restart, radio reconnect and backup restore; verify the label beside the node name in Nodes and in message-tap actions. DM history is absent from node details; DM still opens Chat. Tap card padding/subtitle/facts and ensure actions open once; action buttons must not collapse the card. Browser tests cover label editing/removal, HTML-safe rendering, network separation and card taps. Native preference persistence and launcher display remain pending device verification. The existing radio-notification badge behavior has not been changed.
# 0.10.9 targeted device checks (27 October 2026)

- Set Android to a 12-hour clock. Verify new/old messages, saved history, Nodes last-heard and My Nodes last-connected still show 24-hour time. Unknown timestamps must remain unknown.
- MT node actions → Signal Quality: verify LocalStats response, missing values, routing error and 120-second timeout. Remote LocalStats support depends on firmware; do not treat a timeout as measured poor signal. Reception SNR/RSSI belong to the response's final radio hop.
- MC repeater / repeating room-server → Ping: verify zero-hop response, both SNR directions and timeout. Ordinary companion contacts do not support this repeater Ping.
- Connect All Available: include a failed BLE handshake and failed USB permission/handshake; after all attempts verify the failed-device list and Retry/Cancel. Retry must retain successful connections. Disconnect All during discovery must prevent delayed reconnect attempts. Repeat with an already-loading radio and a device disconnecting before final verification.
- Send to multiple: verify circular lowercase i; Cancel remaining must mark only pending items cancelled and show the count, leaving active attempts and completed results intact. Confirm no cancelled item resumes after app restart. Stop retries acts separately on active MC DMs.
- Nodes: your radios must be hidden even when encountered through another connected radio or when saved in My Nodes. Other contacts stay visible.
- Chat: MC/MT, MT/MC, MC/MC, MT/MT, disconnected/saved choices; Adv must appear on the correct side of A−/A+, target that specific MC radio and disable when disconnected. Test channels and DMs.
- Check portrait/landscape, keyboard open/closed, gesture and three-button navigation. Native insets handling is unchanged; physical-device checks are still required.

