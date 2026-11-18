# SV8TTL LoRa Dash Beta 0.10.33 (60)

Independent Android adaptation based on OverMesh Lite, published at the user's request on 18 November 2026.

- Config → My Nodes → Manage: create MeshCore and Meshtastic channels using reported slots and verify writes by reading the channel back. See NODE-MANAGEMENT-2026-11-17.md.
- MeshCore TX/RX utilization history alongside Meshtastic history; graph gaps preserve missing samples and connection boundaries.
- Bold green highlighted channel name in Chat.
- Any partial uncertainty-circle overlap with a geofence triggers “possible entrance into.” Repeated overlap is silent; complete separation and re-entry can alert again. Exact positions retain normal entry/exit events. History retains the uncertainty radius. See GEOFENCE-FIX-2026-11-18.md.

Automatic location sharing is not included. The existing manual Send my location remains available.

Package ID and original debug signing key are preserved for update compatibility. This build includes the current local source changes. Physical MC/MT operation and Android insets still need device testing; successful host tests and APK assembly do not establish radio correctness.

Validation passed: `assembleDebug`; `ChannelAdminSmoke`, `UsageProtocolSmoke`, `GeofencePositionSmoke`; `node-management.cjs`, `usage-history.cjs`; and the map position/history/fence tests run during the geofence change. Android instrumentation sources compile, but device instrumentation has not run.

APK signature matches the previously published certificate (SHA-256 `5f5f9e0071e32ff2702567aa27940645dadda37df9183b1ceb16b8e09d6041b0`). APK size: 10,387,088 bytes. APK SHA-256: `8eb1bfc8f707a91936d762e9bd7721c5ba265da80cb1265766728b74ae99bf66`. Update metadata identifies `org.overmesh.android`, version 0.10.33, code 60.
