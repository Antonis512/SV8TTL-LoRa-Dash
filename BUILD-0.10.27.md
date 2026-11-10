# 0.10.27 cloud test APK — 10 November 2026

The user authorized building the current changes. App and instrumentation APK assembly succeeded with the existing cloud debug signing configuration. This supersedes the earlier source-only/no-APK status of the November 10 fix notes; no production signing change was made.

- Package: org.overmesh.android
- Version: 0.10.27 (54)
- Minimum Android API: 26
- APK bytes: 10330042
- APK SHA-256: b9a6e322fe409a8824bb7ce45780da7a66693d766a3c60b9bb2d328693219a52
- Signing certificate SHA-256: 8984ddd3bad1e7f7d420256b5096e0ddcde49a29e78f302f22231dbf6deda254

The certificate matches the prior 0.10.26 cloud test download. It differs from the original published 0.10.25 APK. The version code is higher than both. Main's original update feed and published APK remain unchanged.

Packaged index.html, android.js, map.js and map.css were compared byte-for-byte with current source. Relevant automated protocol, map UI, schema-migration, notification-permission/lifecycle, ZIP integrity and document-provider tests passed during implementation; the ZIP checks also passed with a synthetic 100 MiB map entry. The separate real-Shortbread-pack test remains unavailable without its MAP_SMOKE_PACK fixture. Instrumentation compiled and packaged, but was not executed on an Android device.

The APK contains the documented MT loading, Map, notification, themed-button and full-backup fixes. See MAP-FIXES-2026-11-10.md, NOTIFICATION-BACKUP-FIXES-2026-11-10.md and MT-LOADING-2026-11-05.md. Previously discarded precise coordinates or a deleted damaged backup cannot be reconstructed by this build. Physical radio/phone validation remains pending.

Cloud build: source /workspace/.cloud-setup/activate.sh, then ./gradlew --offline --init-script /workspace/.cloud-setup/local-usb.gradle assembleDebug assembleDebugAndroidTest --no-daemon --max-workers=4. The local USB serial dependency comes from the unchanged official 3.11.0 GitHub tag, as documented in the loading notes; app dependency declarations are unchanged.
