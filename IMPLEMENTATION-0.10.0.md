# Android preview 0.10.0 (27) — 24 October 2026

Source-only follow-up (no new APK, at the user's request): the sending monitor includes Cancel remaining, Send again the previous, and Send Next. Previous means the most recently dispatched part whose active response/retry cycle has ended. Resend confirmation displays its text, part and radio/destination, warns about duplicate reception and creates a separate queue/history item without inheriting its old ACK token. Send Next ends the remaining MeshCore channel observation after radio acceptance, keeps the settling gap, and continues automatically. It does not interrupt active DM retries or Meshtastic transmissions. Resending the previous part waits for active sends and the settling gap, sends one item, then pauses. Repeated taps do not queue multiple Next operations. Pause/cancel clears a manual-step request; restarting always restores paused without executing it. Existing Chat button/font-bar placement remains verified by browser geometry tests. Java/application-test compilation passed; device execution remains pending.

Update in 0.10.1 (28): channel-chain observation is configurable from 1–300 seconds with a **2-second default**, superseding the original 120-second channel policy below. DM/MT timing is unchanged. The separate settling gap remains configurable with a 3-second default. Config retains the enable switch; the Send to multiple action moves between Chat's Users pills. A−/A+ moves into a separate bar immediately below within the centre column in DM and channel views. New values apply to newly dispatched messages; active deadlines are preserved.

Implemented locally; physical radio and Android device validation remain pending.

## History and backups

- Schema 12 adds a conversation/time index and a settings-import recovery journal. Existing message retention and age-based deletion are preserved. Unknown message dates remain undeleted by age filters.
- Chat initially queries the newest 100 records per conversation on a background worker. Scrolling to the beginning or pressing Load older messages retrieves the preceding page. Ordering uses reception timestamp, then database ID; unknown dates come first. Prepending preserves reading position. Unread summaries query the full database.
- My Nodes offers a bounded history preview and Open Chat for paging through the full conversation. Full CSV/JSON exports and age-based deletion still include unloaded records.
- JSON backups stream records in bounded batches. Node/app backup version 2 includes Last Heard; version 1 remains readable. Import copies the selected file to private temporary storage, reads metadata, then streams messages into one database transaction. Failure rolls back that transaction. Temporary staging is removed afterward.
- Settings are journaled with the database import, merged after commit, and recovered after an interruption. Existing preferences are preserved. Sound URIs are included; sound files are not embedded, and unavailable sounds need reselection.
- Duplicate messages fill missing packet/reply references, hops, repeat counts and paths without replacing existing values. Last Heard retains the newest valid timestamp. Imported unfinished sends are unconfirmed; import never retransmits.
- Streaming removes the previous JSON file/message-count caps. Available phone storage, individual record size and database transaction duration remain practical constraints. CSV construction remains in memory; test especially large CSV exports separately.

## Radios and sending

- Nodes: Last Heard newest/oldest sorting, unknown dates last. Meshtastic wire roles 0–12 retained; deprecated 3/4 explicitly labeled. Verified against official config.proto on 24 October: https://github.com/meshtastic/protobufs/blob/master/meshtastic/config.proto .
- Config → My Nodes: Connect All Available probes attached supported USB radios first, then scans BLE for 8 seconds and connects saved, discoverable radios. Unknown identities and mismatched saved identities are disconnected. Existing active connections are preserved; USB-first preference applies when opening new connections. Duplicate USB ports and duplicate discovered radio identities are rejected. OS permission decisions remain user-controlled.
- Config → Add Radio Connections: MeshCore DM known-route/flood attempts independently configurable from 1–10, default 4 + 4 for protocol 4+. Counts include initial transmission. Older firmware retains 2 + 1 with a route, or 3 floods without one. New settings affect new messages, not an active retry budget.
- Config → Sending options: Send to multiple is disabled initially. Pick contacts/channels from connected radios; each target retains its original radio identity. Sequential default, optional concurrent sends on different radios. One saved chain is managed at a time; finish or cancel/review it before starting another.
- Ordinary oversized MeshCore DMs/channel messages use the same persistent chain. Preview shows per-destination part counts. Splitting respects UTF-8/grapheme boundaries and per-radio limits; parts arrive separately, without automatic recipient reassembly. MT oversize messages remain rejected.
- A saved chain records original text, parts, destinations, radio identities, message IDs, attempt/result text, completed and remaining work. Pause stops future dispatch while allowing current sends to finish. Resume sends pending items only. Cancel remaining preserves active transmission processing. Failure review offers unchecked items for explicit requeue. A late ACK can complete a pending retry before it is dispatched.
- On restart, active items become unconfirmed and every restored chain is paused. No automatic replay. An unavailable original radio leaves pending work waiting; the app does not substitute another radio.
- MC DM Stop retries and continue / retry later stops future attempts while retaining the current response window, including stopping during route reset. MT and MC channels do not expose this control. Pause/Cancel remaining never claim to recall a radio transmission.

## Pacing and validation limits

- Default settling gap is 3 seconds after an observed result/timeout, configurable 1–30 seconds. This is provisional, not a measured guarantee of RF silence.
- MC channels wait for a heard repeat or a 120-second observation window, not merely local acceptance. A repeat is labeled as such, not recipient delivery. MC DMs wait for protocol retry completion; MT waits for terminal status or the existing 120-second timeout.
- Interrupted/disconnected active sends retain a bounded response window before advancing. Firmware traffic can outlive app observations. Nearby-radio interference and suitable timing still require physical measurements.
- Host Java regression tests cover retry budgets, route resets, cancellation/late ACKs, loading and protocol actions, plus Unicode chunk boundaries. Browser tests cover existing device selection/reactions and new settings, paging, scroll position and sorting.
- Separate Android instrumentation tests exercise isolated SQLite paging, retention, v10→v12 migration, streaming backup and saved-chain restart. The test APK builds, but these tests have not run: no Android phone/emulator is attached.
- Physical BLE/USB discovery, simultaneous radios, chain delivery behavior, long histories under live RX, and both Android navigation layouts still require the PHONE-TESTS.md checklist. Build success is not evidence that these pass.

Existing attribution is preserved; new code is independently authored. No publication, signing change or new product identity was performed.
