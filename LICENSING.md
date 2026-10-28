# Licensing and provenance for SV8TTL LoRa Dash

## 28 October 2026 — public source repository preparation

The user authorized a public GitHub repository named SV8TTL-LoRa-Dash. This supersedes earlier statements that publication had not been authorized. Existing license texts and notices are retained unchanged; no replacement license or production signing scheme is introduced. Debug binaries, signing credentials, app data and research downloads are excluded from the source repository.

Recovered upstream checkout: Slofi/overmesh revision `91e1ec799fad69a3d67674e03ae920d94c8e6b3d`. The bundled `background-topo.png` is byte-identical to `static/background-topo.png` in that checkout (SHA-256 `f30096d15c5beaaa468cd4d16133284545d397c85322fd2a5eec65e4ab50441b`). The bundled `overmesh-lite.css` matches the inline style in `templates/lite.html` at that revision except for the local background image URL and trailing newline. These comparisons establish a matching source revision, not the original copy date or independent authorship of the upstream image. Original MIT notices remain in LICENSE and the APK license assets.

The Android adapter, bridge and UI have accumulated the modifications recorded below. Runtime dependency notices are bundled under `src/main/assets/licenses/`; the Gradle wrapper retains its Apache-2.0 headers. Earlier audit questions remain documented rather than represented as legal clearance. No APK release is part of this source publication.


## 27 October 2026 — independent app name and attribution correction

The user selected **SV8TTL LoRa Dash**, with **Beta** shown separately. The current description is: “An independently developed Android adaptation based on OverMesh Lite, incorporating original OverMesh assets. Not an official OverMesh release.” This replaces the overly broad “fork” claim in About. Earlier names and fork wording below are historical records, not the current product description. No GitHub fork has been published.

The launcher/header, notifications, export filenames, MeshCore client identification and APK filename now use the new name. The locally authored launcher lettering is LD above SV8TTL. The application ID, namespace, signing configuration, storage and backup format identifiers remain unchanged for compatibility. Original MIT copyright, bundled notices and provenance records are retained. No replacement license or release-signing/publication decision is implied by this rename.

## 0.10.9 behavior references — 27 October 2026

Consulted official Meshtastic Android `SignalMetrics.kt` and `MetricsViewModel.kt` at revision d0a924d67cb1ab3f532a9e50bc3131e0c81538be for the Signal Quality screen's LocalStats telemetry request, and official `meshtastic/telemetry.proto` for wire fields. Consulted Liam Cottle's MeshCore Quick Start Guide (https://files.liamcottle.net/MeshCore/Documentation/MeshCore_Quick_Start_Guide.pdf) to confirm the existing zero-hop repeater Ping behavior. Signal request encoding, result formatting and UI changes are independently implemented; no reference source, generated code or artwork was copied into the product. Existing provenance/release-audit obligations remain.

## 26 October 2026 — preview display name

The user selected **OverMesh SV8TTL beta** as the app display name. The Android application ID, signing and publication strategy remain unchanged and undecided for an independent release. Earlier notes about no chosen name describe the state at their recorded dates. All inherited attribution and notices remain. Local node labels and UI changes were independently implemented; no third-party code was added.

## 0.10.0 implementation

History paging, streaming backup, connection discovery and persistent sending were independently authored. The previously inspected OverMesh and official MeshCore behavior references informed the design; no new third-party source/assets or dependencies were copied into the product. Official Meshtastic config.proto was rechecked on 24 October 2026 for current/deprecated role wire values. Existing notices and the public-release provenance audit remain unchanged.

0.9.8 independently refactors the existing Android Chat UI and session selection for two unrestricted device slots and saved conversation browsing. No third-party code or assets were added; existing attribution and release-audit requirements remain.

## 0.9.9 behavior references

Consulted official Meshtastic Android `feature/messaging/.../component/Reaction.kt` for tapping reactions to list senders, and the locally retained official MeshCore web bundle for zero-hop Ping, supported repeater/room-server roles, 1/2/4-byte identifiers and round-trip SNR. Consulted Android notification documentation for status icons and foreground notification colorization. All added implementation and icon artwork are locally authored; no official app source or assets were embedded. Existing notices and release audit requirements remain.

## 0.9.7 protocol reference

Also consulted Android's BluetoothAdapter documentation for ACTION_REQUEST_ENABLE and ACTION_STATE_CHANGED, and Service documentation for onTaskRemoved and STOP_FOREGROUND_REMOVE. The permission/activation queue and session/notification cleanup are locally implemented; no reference code was copied.

Consulted the official Meshtastic SDK RadioClient reaction API documentation/source (https://github.com/meshtastic/meshtastic-sdk/blob/main/core/src/commonMain/kotlin/org/meshtastic/sdk/RadioClient.kt) and the retained mesh.proto wire-field reference: TEXT_MESSAGE_APP carries the UTF-8 reaction in payload, emoji=1, and reply_id identifies the original packet. Implemented independent sending restricted to 👍 and digits 0–7 and unrestricted Unicode reaction display; no SDK, generated protobuf or official UI implementation was copied. The retained MeshCore companion source confirms CMD_SYNC_NEXT_MESSAGE drains one message and RESP_CODE_NO_MORE_MESSAGES signals completion. Removed the preview's local 50-read cutoff. Existing provenance/release-audit requirements remain unchanged.

## 0.9.6 implementation notes

Config → About now exposes the installed package version, Beta edition, By SV8TTL, the Slofi/OverMesh fork/development statement, upstream link and inherited notices. No replacement license, product identity, signing strategy or publication was chosen.

The bundled dependency notices were extracted from the exact resolved POMs and AAR/JAR notice entries listed below. USB serial's complete MIT notice was fetched from tag 3.11.0 (`LICENSE.txt`); Kotlin's NOTICE from v2.2.10 (`license/NOTICE.txt`); JSpecify's license from v1.0.0; annotations 13.0 license headers were checked in the Maven Central sources artifact. These are under `src/main/assets/licenses/`. The existing public-release provenance audit remains open.

Behavior references: the locally retained official MC web bundle filters path-name candidates to repeater type 2, counts distinct public keys and uses `@[name]` mentions. This preview deduplicates candidates and deliberately retains ambiguity for distinct matching repeaters rather than selecting the first. Meshtastic's Messages & Channels guide and retained wire definitions were checked for channel replies (`Data.reply_id`), packet IDs and `NodeInfo.last_heard`. MeshCore contact records supply `last_advert` at byte offset 132; modification/connection time is not treated as reception. New implementations and tests are locally written; official app source was not embedded.

Recorded 18 October 2026 at Antonis's request. The intention is to develop an independent product, potentially by forking OverMesh. No new repository, publication, product name, or replacement license has been authorized or created in this session.

## Keep the existing attribution

0.9.5 provenance note: Meshtastic HardwareModel numeric identifiers and names from the referenced mesh.proto are used as protocol data in a locally written display table; no generated protobuf library was added. Default-role handling was checked against config.proto. MC channel-path authentication/parsing uses the already recorded firmware/protocol references and Java cryptography, with no official app bundle copied. The notification vector is locally authored. Android foreground-service and notification-permission documentation was consulted for platform behavior. Include these additions in the pre-release provenance audit; this is not a licensing clearance.

- The local `LICENSE` is MIT, with **Copyright (c) 2026 Filip**. Keep that notice and the full permission/disclaimer text wherever the inherited code or substantial portions are distributed. Do not replace Filip's name with the new project's author.
- MIT permits modification, distribution, sublicensing and commercial sale, subject to its notice condition. A new product name does not remove that condition. [MIT license text](https://opensource.org/license/mit).
- The recorded copied assets are `src/main/assets/overmesh-lite.css` and `background-topo.png`, from [Slofi/overmesh](https://github.com/Slofi/overmesh). The original copy's commit SHA is not recorded here; recover and record it before public release. Check the image's provenance and any asset-specific notices rather than assuming the repository license settles every asset.
- The Android adapter, JavaScript bridge, local storage and Android-specific UI are local implementation work. The HTML adapts the Lite presentation. Audit file history before making a blanket claim that every non-copied file is wholly independent.
- Include inherited notices in source releases **and APK distributions**. The upstream MIT text is now also placed in APK assets under `licenses/OverMesh-MIT.txt`. Add a user-accessible About / Open-source licenses page before public distribution.

## Inventory and reference boundaries

| Item | Current relationship | What to retain/check before release |
| --- | --- | --- |
| OverMesh | Copied styling and background; adapted presentation | Full MIT notice, source URL, original commit and any asset-specific terms |
| `com.github.mik3y:usb-serial-for-android:3.11.0` | Runtime dependency | Its [MIT license](https://github.com/mik3y/usb-serial-for-android/blob/master/LICENSE.txt), Google/Mike Wakerly notices, and any bundled third-party notices; verify against the exact resolved version |
| `androidx.webkit:webkit:1.17.1` | Runtime dependency | Inspect resolved artifact/POM and packaged notices; AndroidX generally uses Apache-2.0, but inventory each transitive artifact rather than guessing |
| Gradle wrapper and Android Gradle Plugin | Build tooling | Keep wrapper/tool notices when distributing those files; distinguish build-only tools from code actually shipped in the APK |
| Meshtastic protocol definitions | Read as a wire-format reference; no generated protobuf library added | Current upstream [protobufs license is GPLv3](https://github.com/meshtastic/protobufs/blob/master/LICENSE). Do not casually copy generated code, `.proto` files, comments or implementations into an MIT-only release |
| Official Meshtastic Android | Read to understand delivery menus and trace endpoint/SNR presentation | Examined Kotlin headers state GPL-3.0-or-later. No Kotlin source dependency was added. Independently written protocol handling is not a blanket licensing clearance: audit possible expressive/structural derivation before choosing a release license |
| MeshCore firmware and meshcore.js | Read for companion command IDs, replies, advert URI and trace behavior | No firmware or JS library was added as a runtime dependency. Verify exact source versions/licenses if any source is copied later |
| Official MeshCore web app | Its publicly served JavaScript was inspected for menu labels and advert-link behavior | Public availability is not permission to reuse its implementation, artwork, branding or UI assets. No app bundle was embedded. Do not assume its license matches the firmware |
| Playwright/browser regression tooling | Local development tests | Not bundled into the Android app; preserve relevant notices if redistributing tooling itself |

The research downloads are under ignored `build/reference/`, outside `src/main/`. Do not commit or redistribute that directory as product source. References read on 18 October 2026 included:

- [Meshtastic RouteDiscovery.kt](https://github.com/meshtastic/Meshtastic-Android/blob/main/core/model/src/commonMain/kotlin/org/meshtastic/core/model/RouteDiscovery.kt), `TracerouteHandlerImpl.kt`, `DeliveryInfoDialog.kt`, `MessageActionsBottomSheet.kt`.
- [Meshtastic mesh.proto](https://github.com/meshtastic/protobufs/blob/master/meshtastic/mesh.proto).
- [MeshCore companion MyMesh.cpp](https://github.com/meshcore-dev/MeshCore/blob/main/examples/companion_radio/MyMesh.cpp), [Mesh.cpp](https://github.com/meshcore-dev/MeshCore/blob/main/src/Mesh.cpp), [companion protocol documentation](https://docs.meshcore.io/companion_protocol/).
- [meshcore.js connection.js](https://github.com/liamcottle/meshcore.js/blob/master/src/connection/connection.js) and the published [official MeshCore web app](https://app.meshcore.nz/).

## Decisions for the fork

1. Choose the license for original contributions deliberately after the provenance/dependency audit. Retain existing third-party terms even if original additions use another license. MIT allows a separate product, but does not relicense unrelated third-party material.
2. If GPL-covered implementation is incorporated or the resulting work is derivative, resolve GPL compatibility and corresponding-source obligations before distributing binaries; a credits entry alone is not a substitute. Check the actual license/version and scope. The current reference inspection should not be described as proof that these questions are settled.
3. For Apache-2.0 components, include the license, retain relevant notices, include required NOTICE material where present, and identify modifications to copied files. [Apache-2.0 sections 4 and 6](https://www.apache.org/licenses/LICENSE-2.0).
4. Pick a distinct app name, icon, Android application ID and repository name. Preserve factual attribution such as “Includes components from OverMesh”; do not imply endorsement by OverMesh, Meshtastic or MeshCore. Check trademark/logo usage separately from copyright licenses.
5. Decide release signing and whether existing preview users should migrate data. A new package ID is a separate app; a new signing key can prevent in-place upgrades. Keep signing keys outside source control. This is a release-planning item, not a license requirement.
6. Keep a third-party manifest with component, version/commit, source URL, license, copyright, modifications, distribution location and required notices. Include fonts, icons, maps/tiles, sounds and future media as well as code.
7. Define contribution ownership/licensing for future collaborators and preserve their notices. Do not retroactively claim ownership of upstream work. AI-assisted implementation does not remove third-party obligations; retain provenance and review suspicious similarities.
8. Check store terms, privacy disclosures, data handling and radio-related claims separately when distribution is planned. No publication or monetization model has been chosen yet.

## Before publishing

- [ ] Recover original OverMesh source revision and asset provenance.
- [ ] Audit the adapter and tests against consulted sources; resolve any GPL/other-license derivation questions.
- [ ] Produce the resolved runtime dependency inventory and collect exact-version license/NOTICE files, including transitive dependencies.
- [ ] Choose the project's license and new identity; document attribution and modification history.
- [ ] Add the in-app license viewer and verify the actual APK/source archive contains all required notices.
- [ ] Exclude research downloads, debug binaries, personal backups, device identifiers and signing secrets from the public repository.
- [ ] Review release-signing and backup migration, then request the user's publication decision.

This file is a working inventory and decision checklist, not a completed clearance of the eventual product.

## Resolved runtime versions recorded for 0.9.0

Gradle debugRuntimeClasspath resolved usb-serial-for-android 3.11.0; AndroidX WebKit 1.17.1; annotation/annotation-jvm 1.10.0; annotation-experimental 1.3.0; core 1.1.0; lifecycle-runtime/common 2.0.0; arch.core core-common 2.0.0; versionedparcelable 1.1.0; collection 1.0.0; Kotlin stdlib 2.2.10; JetBrains annotations 13.0; JSpecify 1.0.0. The local report is build/runtime-dependencies.txt. This inventory does not replace collecting each exact artifact's license and NOTICE files before public release.

For the 0.9.1 message-size/retry audit, consulted the official MeshCore Companion-Radio-Protocol wiki and src/helpers/BaseChatMesh.cpp (wire limits, sender-name prefix, attempt field and suggested timeout), and Meshtastic Messages & Channels / Mesh Broadcast Algorithm documentation. Implemented independent limit validation; no reference implementation was bundled. Existing provenance/clearance questions remain open.

## 0.9.2 retry behavior reference

On 18 October 2026, inspected the published app.meshcore.nz JavaScript bundle already saved under build/reference/mc-app.js to verify observable retry defaults: firmware protocol v4 capability gate, direct/flood budgets, timing jitter, cancellation and channel single-send behavior. Consulted the official Companion-Radio-Protocol wiki and Meshtastic Mesh Broadcast Algorithm documentation. Independently implemented a Java protocol state machine and deterministic tests; no Dart/compiled app implementation was embedded. The reference bundle is excluded from the product. Preserve the earlier source-provenance review requirement before public release; this note is not legal clearance.

## 0.9.3 heard-repeat references

Inspected the official published MeshCore app's raw-receive handler to verify that channel-repeat reception increments the outgoing message counter. Consulted MeshCore Packet.h/Packet.cpp (header, transport and path layout), Mesh.cpp (group payload), Utils.cpp (wire crypto), and BaseChatMesh.cpp (sender prefix and channel key handling). The local implementation uses Java's standard crypto APIs to match exact outgoing encrypted payloads; no source implementation or library was copied into the APK. The regression fixture was independently generated with Node.js crypto. Channel secrets remain within the native protocol session, outside WebView state and backups. Retain the existing provenance review requirements.

0.9.4: Rechecked the official published MC app contact-message composer limit (150 UTF-8 bytes) in the existing reference bundle. Matched that limit and removed the preview-specific long-message retry exception; no reference code was copied.


## 20 October 2026 — MeshCore loading comparison

Inspected the public official web app at https://app.meshcore.nz/ and its main.dart.js on 20 October 2026; retained bundle SHA-256: 250b3e2baec6a69623a0a91692cd42a272ce33f832805d0ef11a53140b94a09d. Reference copies remain under ignored build/reference/. Also checked https://docs.meshcore.io/companion_protocol/ and the previously retained MyMesh.cpp response-code definitions. Observed contacts → channels → queued-message loading and continuation past unsupported channel data response 27. Independently implemented queue continuation, reduced database writes and coalesced UI refreshes; no official application code was copied into the product. Existing provenance-audit obligations remain.
