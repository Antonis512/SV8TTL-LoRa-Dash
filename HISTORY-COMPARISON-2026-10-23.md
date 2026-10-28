# Message-history comparison — 23 October 2026

Read-only application review; findings below are not implemented changes. Upstream main was checked at 9c65835c2dda416b4c1154cd773bffeb774724c5 (newer than the 22 October baseline). Source snapshots are in ignored build/reference/history-review, not shipped assets. This is source inspection, not a live upstream UI or migration test.

## Comparison

| Area | Upstream OverMesh | Android preview |
| --- | --- | --- |
| Storage | Local SQLite on the host, separate message DB files per radio. MT file derives from local node number; MC resolves its radio DB path. | One phone-local chat-history.db, schema version 10. Rows partitioned by radio family and own node identity, then channel/DM peer. BLE address is separate from history identity. |
| Retention | save_message prunes beyond 1000 MT rows; save_mc_message beyond 2000 MC rows, per radio. Message retention is by count, not the passive-observation age rule. | No count/age pruning on ordinary message save. Manual deletion and erase-older controls exist. |
| Load/display limits | MT load_messages loads latest 500, sorted chronologically; connection load additionally trims shared in-memory MT list to 500. MC archive endpoint normally requests 500. These are not the retention caps. | ChatStore.load retrieves all rows for the selected radio in insertion-ID order. MainActivity serializes loaded conversations to WebView. No pagination found in these paths. Large histories can increase main-thread work, memory and WebView update size. |
| Browser cache | MC localStorage cache saves last 300 messages; initial cache reader accepts up to 500. Server archive is separate. Browser unread state is saved in localStorage. | Message unread flags live in SQLite; no dependency on browser storage for message history. |
| Offline access | Cached MC history and host archives exist, but browser still depends on the host for normal app service. MT archive initialization/loading is tied to radio connection in the inspected flow; do not equate this with phone-local offline browsing. | Saved radios/conversations can be loaded without a radio connection through Chat/My Nodes. App runs locally on phone. |
| Delivery state | MT persists ACK outcomes; pending ACK correlation is in memory, and stale entries are discarded after 300 seconds in health_check_loop. No conversion to unconfirmed is present in that stale-entry loop. | Delivery strings persist. At app startup, pending own-message states are changed to unconfirmed by markUnconfirmedMeshCoreSends (despite the method name, the SQL is not limited to MC). Active retry/ACK correlation is not persisted as a resumable queue. |
| Duplicate handling | MT uses INSERT OR IGNORE by message ID. MC uses a dedupe key (explicit key/ID, otherwise composed fields), with receive-copy/path enrichment. | Ordinary incoming/backup messages use radio/peer/channel/sender/text/time/own matching; MT reactions also check packet ID. Different strategy, not equivalent guarantees. Same-content/time legitimate messages warrant regression cases. |
| Deletion | Channel/per-radio clearing; inspected MC routes also offer DM clearing. | Individual-message deletion, per-peer DM clearing, manual erase-older and whole saved-radio deletion. Radio deletion is transactional and removes associated message/channel/Last Heard rows. |
| Backup/import | No equivalent all-radio chat-and-settings JSON backup/merge workflow found in inspected app.js and chat/MC/settings routes. Log-entry export/import is a separate feature; not a chat backup. Host DB files remain the underlying archive. | Per-radio JSON backup, full radio/message/settings JSON backup, CSV exports, additive import with duplicate checks. Existing settings/channel names are kept. |
| Pending batches | No persistent multi-destination send-batch structure found in prior review. | Also not implemented yet; agreed addition. Chat-history persistence alone cannot resume unsent batches. |

## Android follow-ups discovered

1. **Keep storage separate from UI loading.** Retaining history does not require loading and serializing every message. Recommend paged recent-message loading and a Load older action before adopting any destructive retention cap. This is a recommendation, not authorization to change retention.
2. **Imported chronology.** ChatStore.load orders by id ASC; merging older backups inserts new rows with later IDs. Older messages can consequently appear after newer messages. Recommend received_at ordering with stable ID ties and an explicit policy for missing timestamps; verify UI behavior with imports.
3. **Backup completeness.** nodeJson exports messages, saved channels and known-radio metadata; appBackupJson adds preferences. It does not export node_heard, so Last Heard values are not restored by this JSON format. External sound content is not embedded merely by saving preference URIs. Do not describe this as a byte-for-byte device/app backup.
4. **Import recovery.** Validation precedes import, but mergeNodeBackup performs multiple writes without an encompassing transaction. A storage failure can leave a partially merged backup. Duplicate checks help a later retry but do not provide atomic recovery.
5. **Import limits versus unlimited retention.** Per-radio import rejects more than 100000 messages and full import more than 300000, while export and retention are not capped equivalently. Very large exports could become non-importable. This needs an explicit policy, not silent message removal.
6. **Old migrations.** Existing pre-v5 upgrade logic deletes unassigned legacy rows with empty node_id. Current v10 history should be preserved by future changes; test supported upgrade paths and document this older behavior rather than promising every historical schema was lossless.
7. **Restart status normalization.** Review the precise terminal/nonterminal state list, including MC sent_radio DMs, before adding persistent batches. Do not convert genuine channel acceptance into an invented delivery confirmation or automatically resend an uncertain item.

## Recommended direction for discussion

- Preserve phone-local per-radio history, offline access and current retention policy.
- Add pagination instead of copying upstream's automatic 1000/2000-message deletion.
- Address chronology, backup completeness, import atomicity/limits and restart-status cases with focused tests.
- Keep persistent batch/chunk state separate from history, linking each actual transmission record to its batch item. User explicitly resumes/retries after interruption.
- No changes to retention or application behavior were made by this comparison.

## Evidence

Upstream: https://github.com/Slofi/overmesh/tree/9c65835c2dda416b4c1154cd773bffeb774724c5
- db.py: get_msgs_db, get_mc_msgs_db, save_message, load_messages, save_mc_message, load_mc_messages, _mc_message_dedupe_key.
- mesh.py: connect_node history load; health_check_loop pending ACK expiry.
- routes/chat.py and routes/mc.py: message APIs/deletion.
- static/js/app.js: _saveMcMessages, _loadMcMessageArchive, saveUnreadState and loadUnreadState.

Android: src/main/java/ChatStore.java; MainActivity.java loadSessionHistory, sessionJson, nodeJson, appBackupJson, mergeNodeBackup and importFullBackup; src/main/assets/android.js conversation rendering.
