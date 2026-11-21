# SV8TTL LoRa Dash Beta 0.10.36 (63)

Prepared 21 November 2026 at the user's request for APK build, English/Greek manual updates and GitHub publication. Independent Android adaptation based on OverMesh Lite.

Includes DM position sharing, full-size send controls while writing, permanent/timed All/Contact/Node notification silencing, direct-only signal summaries, deferred Copernicus download UI, radio favorites and Contact inheritance, MT Ignore management, and reversible per-node geofence exclusions. See FIXES-2026-11-21.md and GEOFENCE-NODE-EXCLUSIONS-2026-11-20.md.

Validation: assembleDebug and compileDebugAndroidTestJavaWithJavac passed. DM position touch tests, silence controls, MT Ignore UI, Contacts and Member Mode browser tests passed. Favorites and Ignore Java protocol smoke tests passed. Node reception, signal history and geofence browser checks passed during this work. Changed assets in the APK match the final source, and bundled licensing notices are present. Instrumentation compiled but was not run on a device.

An additional Chat regression still expects unknown MeshCore names to lack Node actions; the existing application uses named-sender identities instead. The mismatch is recorded in FIXES-2026-11-21.md; this does not establish radio delivery to an unknown sender.

Verified package org.overmesh.android, versionCode 63, versionName 0.10.36. The signing certificate matches the previously published APK: SHA-256 5f5f9e0071e32ff2702567aa27940645dadda37df9183b1ceb16b8e09d6041b0. Production signing and replacement licensing remain undecided.

APK size: 10406993 bytes. SHA-256: 55142cbd0eb8eddbb6b797163e23c2c7d25c9c068fcbfedd4938cb1213a67613.

Physical MC/MT Bluetooth connections/reconnections, initial nodes/channels and automatic Chat selection, simultaneous radios, DM position transmission and location permissions, silence expiry/restart behavior, radio favorites/Ignore, Contacts scrolling and Android gesture/three-button/keyboard insets remain pending. Existing compileSdk/Gradle warnings remain.
