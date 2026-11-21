# Per-node geofence alert exclusions

- Nodes now offers Exclude from geofence alerts / Allow geofence alerts for this node, including saved/offline nodes.
- Android geofence notifications include Exclude node. The action opens alert history, where the choice can be reversed.
- In-app geofence notices and saved alerts offer the same reversible action. Alert history also lists excluded nodes, so re-enabling remains available after deleting old alerts.
- Exclusions apply to a network/node across all receivers and fences. MC full public keys and 12-character node keys match the same setting. MT and MC identities remain separate.
- Excluded nodes continue to update their map positions and fence state, without adding new alerts, notifications or sounds. Re-enabling applies to future transitions and does not replay suppressed crossings. Previous alerts remain available.
- Settings persist in SQLite schema 22 and are included in app backups. Timestamped explicit allow entries prevent an older backup from restoring a previous exclusion.
- Removed Heard directly (0 hops) from Nodes. Hop-count sorting remains; sorting orders nodes and does not filter relayed nodes. MC route length is distinct from incoming reception hops.

Validation: Nodes/reception, geofence alert/history and map-position browser checks passed. assembleDebug and compileDebugAndroidTestJavaWithJavac passed. Packaged changed UI assets match final source. Added Android instrumentation coverage for cross-receiver exclusions, restart persistence, network separation, backup merge, no replay and reversible suppression; it compiled but was not executed because no Android device was attached. Physical notification taps, alerts, restart persistence and radio behavior remain pending.

Local debug build only; application ID, version and signing configuration preserved. No GitHub publication or update-feed replacement performed for these changes.
