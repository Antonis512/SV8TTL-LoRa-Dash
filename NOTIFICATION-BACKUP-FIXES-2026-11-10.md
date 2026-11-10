# Background status and verified full backups — 10 November 2026

Reported: no app-running status-bar icon after pressing Home with no radio connected, despite notifications being allowed; a confirmed approximately 100 MB full backup containing an offline map later failed import with a missing-ZIP-end error. The failing file was deleted, so its precise failure cannot be reproduced or diagnosed.

## Background running notification

Confirmed code cause: `onPause()` cancelled notification 201 when there were no unread messages, and the normal notification path was suppressed while the activity was not resumed. It now keeps app-running status while backgrounded with no radio. Radio connections still use their own legitimate connected-device foreground service; no fake connected-device service is started just to display an icon.

A normal status notification is retained until the radio service has actually posted its foreground notification. If a service cannot be started from the background, status remains visible through the normal notification path. Existing close/Recents cleanup remains in place. Permission and channel settings remain under Android/user control; a notification is not a guarantee the OS will keep the process alive indefinitely.

## ZIP export/import

The previous exporter wrote directly to the selected document and reported success after closing the stream. It did not reopen and check the saved document. Normal ZIP completion was not shown to be broken deterministically; the deleted file prevents proving whether its outer archive, an inner satellite ZIP, or later storage/copy damage caused the error.

Full export now:

1. Creates and completes a private staged ZIP before opening the chosen destination for writing.
2. Checks the ZIP directory, required history entry, entry lengths and CRC checksums. Included satellite archives have their own directory/entry checks.
3. Copies the completed archive and closes the document writer.
4. Reopens the actual saved document and compares its size and SHA-256 with the prepared archive.
5. Reports **Backup exported and verified** only after those checks pass. Staging is cleaned up on success/failure. Verification requires temporary free storage and a provider that permits reopening the saved document.

The history-entry wrapper now delegates bulk writes directly rather than using FilterOutputStream's byte-at-a-time default. Closing the JSON writer flushes that entry without closing the enclosing ZIP; normal ZIP finalization still writes its directory and end record.

Full import checks complete ZIP integrity and the map-pack manifest before merging history into the database. Missing end records and corrupt entries give a clear incomplete/damaged-backup error. Legacy standalone JSON imports remain supported. These safeguards do not reconstruct already deleted files or silently import a truncated archive. Storage failure while installing otherwise valid map packs can still leave already-merged history available, consistent with the existing merge/import workflow; this is not an all-files filesystem transaction.

## Validation

Passed: production-method running-notification tests for no-radio background status, transfer status, permission denial, service transitions/failure and closing; existing first-launch permission tests; archive completion/Unicode/multiple-entry tests; missing-end and CRC-corruption rejection; nested satellite ZIP validation; production export-method tests with truncating, unreadable and failing document providers; and activity-result request-code checks. The archive checks also passed with a synthetic 100 MiB map-entry payload; this tests ZIP/container integrity at the reported scale, not real map rendering.

Java compilation was checked without APK packaging. No actual Android storage provider or phone status bar was exercised; device validation remains pending. No APK was built or published, and the existing APK file remains unchanged.
