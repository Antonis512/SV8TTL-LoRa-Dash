# 0.10.28 — 11 November 2026

SV8TTL LoRa Dash Beta is an independent Android adaptation based on OverMesh Lite.

This version combines the 0.10.26 and 0.10.27 cloud source changes with the local Bluetooth activation sequence, geofence sound selection and first-unread Chat navigation changes. Geofence sound is initially disabled and has its own system tone and repeat controls in Config → Notifications. Chat loads older history when needed and places an Unread messages divider before the oldest unread channel or DM message.

The user authorized APK assembly and GitHub publication. The original application ID and local debug signing configuration are used.

Java compilation, simulated BLE transport and activation checks, notification permission tests, map position, loading UI, session UI and multi-device Chat checks passed. The Chat regression covers a 205-message channel and DM unread positioning. The settings-windows test still references a Saved messages footer button removed in the cloud source. The online map renderer test previously timed out in headless Chrome.

Phone testing is pending for both Bluetooth radio families, simultaneous connections, notification sounds, gesture/three-button navigation and keyboard insets. The reported MT identities are Klif Aegina !291b406e, Hermesh 0 !9ea1a414 and Keep Chili !ea8ee2b8. Actual geofence/position discrepancies require stored observations and comparison details.

The historical cloud APK in downloads uses a different signing certificate. Android requires a matching certificate for an in-place update. Preserve app data when checking an installation's signing identity.

assembleDebug passed. APK signature verification passed and the certificate matches published 0.10.25 (SHA-256 5f5f9e0071e32ff2702567aa27940645dadda37df9183b1ceb16b8e09d6041b0). Package metadata is org.overmesh.android, version 0.10.28 (55). Packaged UI assets match source byte for byte. APK size is 10344153 bytes, SHA-256 5428806b7bff8fa3704beb752cb47142d39102e4c792dbd0176acf2ea2db5416.
