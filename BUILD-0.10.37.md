# SV8TTL LoRa Dash Beta 0.10.37 (64)

## 26 November APK publication

The user subsequently authorized building and publishing the APK. assembleDebug passed with the latest source, including per-radio Manage channel copying and Android contact-detail importing. Package org.overmesh.android, version 0.10.37 (64), and the original signing certificate were verified. The APK declares READ_CONTACTS for the selected-contact permission fallback. Existing compileSdk/Gradle warnings remain; physical phone/radio checks are pending.

Published APK size: 10748953 bytes. SHA-256: 34951821e38cfe4363bfc82d88957a9106096d7d69709a94b46ac5550903e1dc. Signing certificate SHA-256: 5f5f9e0071e32ff2702567aa27940645dadda37df9183b1ceb16b8e09d6041b0. The updates APK and latest.json now describe this build; earlier hashes below are historical local builds.

## 26 November source update

Published source includes the subsequent Manage-menu and Android contact-linking fixes. Copy channels now starts from the managed radio. Android contact details use the selected contact's data directory, request Contacts permission if access is denied, and merge phone numbers, emails, addresses and notes without duplicates after review/save. Existing personal fields and node assignments are retained.

Bundled Playwright Contacts and Manage-menu checks passed; Java compilation passed. No APK was built for the contact-linking update. The earlier local APK build below predates these latest changes. This GitHub update does not publish an APK or change the update feed; the downloadable version remains 0.10.36. Android picker/permission and physical radio/inset checks remain pending.

## Earlier local build record

Prepared 25 November 2026 at the user's request to update GitHub and the manuals. Independent Android adaptation based on OverMesh Lite; not an official OverMesh release.

## Included

- Contacts shows one assigned-node card per network/node ID across receiving devices. Expanded Contacts and Nodes list receiving-device identities, status, last-heard times and favorites. Per-device action selection and filters are retained; person records are not merged or deleted.
- Assigned-node names retain report freshness across database reopen and Contacts JSON restore. Older/equal-time conflicting reports, unknown-age replacements, and stale editor/backup data cannot overwrite a newer timestamped name. Personal names and assignment labels are preserved. Remote MT snapshots do not establish name freshness from last_heard; a timestamped name packet is needed for a later rename.
- Connect Selected synchronizes visible checkboxes on input/change and before list refresh/action submission. It only queues selected disconnected radios not already connecting.
- Single-channel copying between connected radios of the same family uses reported keys, empty-slot checks, confirmation snapshots and radio read-back. Secondary-channel removal protects the primary/default channel and retains message history.
- Own-node visibility/local statistics, phone-local MC self favorites, composer A−/A+ visibility at short keyboard viewports, and clearer timed-silence wording.
- English and Greek manuals updated with usage instructions and limitations.

## Validation

assembleDebug passed. Channel management protocol checks (including MT disabled-slot read-back and primary-role protection) and 28 channel-copy assertions passed. NameFreshnessSmoke and NameProtocolSmoke passed. ContactRenameRuntime passed 18 assertions on the API 30 emulator; these exercise database close/reopen and Contacts JSON restoration, not the full Android process-restart or backup-file pipeline. Browser checks passed for Connect Selected, grouped nodes/receivers/action routing, Contacts, Contacts scrolling, node reception, node management, DM position/layout and notification silence.

The broader ui-smoke test passed its connection section but failed its pre-existing MC TR-menu expectation at line 79. This unrelated test mismatch remains unresolved. Existing compileSdk/Gradle warnings remain.

Verified package org.overmesh.android, versionCode 64, versionName 0.10.37. APK signing certificate matches the previous published APK: SHA-256 5f5f9e0071e32ff2702567aa27940645dadda37df9183b1ceb16b8e09d6041b0. Existing notices and application identity are retained. Production signing and replacement licensing remain undecided.

## Pending physical checks and limitations

Test MC and MT Bluetooth connect/reconnect, discovery, initial nodes/channels and automatic Chat selection, simultaneous connections, Connect Selected on the phone, multi-receiver Contacts/Nodes and rename updates, channel copy/removal on compatible firmware, Contacts scrolling, and gesture/three-button/keyboard insets. Successful builds and simulated protocol tests do not establish hardware correctness. Node-list copying is not implemented; Contacts copying is not included. No physical-radio results are claimed.

APK size: 10415041 bytes. SHA-256: 6bd9be075bcd2c1a54ecc4843d9dd5f6774a07546aa009441422ddf43d5658b5.
