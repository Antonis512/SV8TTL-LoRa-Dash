# Working on SV8TTL LoRa Dash

- Keep this a standalone Android phone app. Both Meshtastic and MeshCore radios must work without a Windows computer or a TCP radio connection.
- Preserve the browser-based OverMesh Lite look and the **Chat** label. Match upstream behavior as features are added.
- Keep the WebView inside Android status, navigation, display cutout, and keyboard insets. Check both gesture and three-button navigation layouts.
- Show channel choices only for the selected connected radio, using channel names reported by that radio. Do not invent `Channel 0` through `Channel 7` choices before radio data arrives.
- Keep radio events on the Android main thread before updating the WebView.
- Preserve upstream MIT attribution for copied assets and distinguish this preview from the official OverMesh project.
- Read `LICENSING.md` before adding copied third-party code or preparing a fork/release. Preserve the provenance notes and all existing notices. The user selected SV8TTL LoRa Dash as the independent app name, with Beta as a separate label. Describe it as an Android adaptation based on OverMesh Lite, not an official release. Public source publication was authorized on 28 October 2026. Replacement license and production release signing remain undecided. Preserve the existing application ID and signing configuration for update compatibility.
- Build with `gradlew.bat assembleDebug` after changes. Physical radio behavior still needs device testing; do not infer it from a successful build.
- Next session reminder requested on 5 November 2026: remind the user to test 0.10.25 with both MC and MT Bluetooth radios when available (known-device connect/reconnect, initial nodes/channels and automatic Chat selection, simultaneous connections), Contacts scrolling and Android insets. Ask for the geofence node name/ID or alert details to compare with the official app. Devices were unavailable during this fix; keep these checks pending until the user reports results. See TODO.md and FIXES-2026-11-05.md.
