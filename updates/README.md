# Repository APK update files

`latest.json` and `SV8TTL-LoRa-Dash-debug.apk` are read by Config → Check for Updates. Keep them together on `main`.

For each update, increase `versionCode` and `versionName` in `build.gradle`, build with `gradlew.bat assembleDebug` using the same debug signing key, copy the resulting APK here, and record its exact byte length and SHA-256 in `latest.json`. Commit the source, APK and manifest together. Android rejects an update signed with a different key. Do not commit the signing key.

This is a beta debug APK distribution. The production signing scheme and replacement license have not been chosen. Preserve the existing APK notices and the provenance record in `LICENSING.md`.
