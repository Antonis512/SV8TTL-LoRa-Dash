# Geofence position follow-up — 18 November 2026

## Updated behavior requested by the user

The user rejected suppression of uncertain reports. Any part of a visible uncertainty circle touching or overlapping an enabled geofence now produces a **possible entrance** alert, including when the centre is outside. The native radius matches the map's explicit or inferred MT radius. Circle intersection checks polygon edges/corners and containment; exact positions retain entry/exit behavior. Historical map positions are not used as new reports: when the map retains a previous precise fix, geofence checks use the accepted incoming report and its circle.

State 2 records possible overlap alongside the existing 0 outside/1 exact-inside states. Repeated overlap is silent; complete separation resets it, so re-entry can alert again. Separation is labelled “uncertainty area left,” not confirmed node exit. A subsequent exact inside report confirms entry. State and radius survive history/backup, and the history UI labels the uncertainty radius. Hiding map circles does not disable notifications.

The pure Java regression covers partial edge overlap with an outside centre, corner overlap, exact tangency, gaps, full containment, polygon holes, date-line geometry and notification transitions. Android instrumentation includes persisted possible alerts, restart/backup deduplication and re-entry; execution still requires a device.

Updated validation passed: `GeofencePositionSmoke`, `map-position.cjs`, `map-alerts.cjs`, `map-geofence.cjs`, `assembleDebug`, and Android instrumentation source compilation. The local APK includes the new overlap behavior; version/signing configuration is unchanged and nothing was published. Phone/radio verification remains pending.

The earlier suppression fix described below is historical and superseded by this behavior.

## Earlier investigation

Resumed investigation using the already supplied Klif Aegina `!291b406e`, Hermesh 0 `!9ea1a414`, and Keep Chili `!ea8ee2b8` identities. The 11 November chat also contains these IDs. Their actual radio position reports are not reproduced by the synthetic fixtures.

Found a mismatch: `mapCellPrecision` recognizes possible Meshtastic coarse-cell centres with absent precision metadata, while the native geofence gate previously accepted precision zero as eligible for an exact crossing. A coordinate could therefore be displayed with an uncertainty circle and still create an enter/exit alert as if its centre were exact.

`NodePosition.canTriggerGeofence` now also checks the map's two-axis cell-centre pattern for MT positions with unspecified precision, covering bits 10–19 and signed coordinates. A possible coarse centre is conservatively excluded from exact crossing evaluation. Explicit full precision overrides the heuristic; MeshCore coordinates do not acquire MT precision rules. No coordinates or reported metadata are rewritten. Existing preserved-position and explicit coarse-position exclusions remain.

This is a heuristic: a genuinely exact position coincidentally lying on both grid centres and lacking precision metadata is also excluded. It prevents a possible false alert; it cannot recover a node's exact position. Historical alerts remain unchanged.

Validation: `GeofencePositionSmoke` failed with the original gate on a missing-metadata cell centre, then passed after the fix. It covers signed coordinates, each inferred grid size, explicit full precision, MC isolation, legacy MT records, invalid/preserved positions and one-axis false matches. `map-position.cjs`, `map-alerts.cjs` and `map-geofence.cjs` passed, including matching map inference fixtures.

No Android device was attached (`adb devices -l`). Actual radio observations and phone geofence behavior remain unverified. This finding is not proof of the cause of the three reported nodes' map discrepancy.

`gradlew.bat assembleDebug` passed and generated the local `SV8TTL-LoRa-Dash-debug.apk`. Existing SDK/Gradle compatibility and deprecated-API warnings remain. Version and signing configuration were unchanged; this is a local development build of the current working tree, including pre-existing edits. No APK or source was published. `git diff --check` passed.
