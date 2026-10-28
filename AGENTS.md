# Working on SV8TTL LoRa Dash

- Keep this a standalone Android phone app. Both Meshtastic and MeshCore radios must work without a Windows computer or a TCP radio connection.
- Preserve the browser-based OverMesh Lite look and the **Chat** label. Match upstream behavior as features are added.
- Keep the WebView inside Android status, navigation, display cutout, and keyboard insets. Check both gesture and three-button navigation layouts.
- Show channel choices only for the selected connected radio, using channel names reported by that radio. Do not invent `Channel 0` through `Channel 7` choices before radio data arrives.
- Keep radio events on the Android main thread before updating the WebView.
- Preserve upstream MIT attribution for copied assets and distinguish this preview from the official OverMesh project.
- Read `LICENSING.md` before adding copied third-party code or preparing a fork/release. Preserve the provenance notes and all existing notices. The user selected SV8TTL LoRa Dash as the independent app name, with Beta as a separate label. Describe it as an Android adaptation based on OverMesh Lite, not an official release. Public source publication was authorized on 28 October 2026. Replacement license and production release signing remain undecided. Preserve the existing application ID and signing configuration for update compatibility.
- Build with `gradlew.bat assembleDebug` after changes. Physical radio behavior still needs device testing; do not infer it from a successful build.
