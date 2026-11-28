# SV8TTL LoRa Dash Beta 0.10.39 (66)

28 November 2026: APK and GitHub publication requested by the user.

- Corrected Greek labels in Config, persistent Chat controls, multi-send destination headings and contact editing. Dynamic labels retain the selected language.
- Config uses Connect known nodes; the inner Connect All Available action retains its name.
- Chat uses P. Msg, P.Psn and Send Psn in one button row. Long Greek text wraps inside the buttons.
- Passive activity counters refresh when map snapshots arrive. Previously the startup panel retained zero counts until reopened. The user confirmed that reopening revealed saved counts on their phone; the new automatic refresh still needs phone confirmation.

Validation: language and persistent-control browser tests passed, including Greek labels and narrow layouts. Map regression checks passed, including initial asynchronous counts, subsequent updates and clearing counts. gradlew.bat assembleDebug passed. Existing SDK/Gradle warnings remain.

Package org.overmesh.android; versionCode 66; versionName 0.10.39. APK signature verified with the original certificate SHA-256 5f5f9e0071e32ff2702567aa27940645dadda37df9183b1ceb16b8e09d6041b0.

APK size: 10596040 bytes. SHA-256: f322ed140c09fac664c229c674c3f8316d7d0cda3b9829afd2597952c8d87472.

Existing attribution, application identity and signing configuration are retained. Independent Android adaptation based on OverMesh Lite; not an official OverMesh release. Production signing and replacement licensing remain undecided. MC/MT connect/reconnect, initial nodes/channels and automatic Chat selection, simultaneous connections, Contacts scrolling and gesture/three-button/keyboard insets remain pending physical checks.
