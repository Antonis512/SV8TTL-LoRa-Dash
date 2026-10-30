# SV8TTL LoRa Dash

**Beta · User manual · First draft**  
For version **0.10.16 (43)** · 30 October 2026

Language: **English** · [Ελληνικά](USER-MANUAL-el.md)

SV8TTL LoRa Dash is a standalone Android app for connecting to MeshCore and Meshtastic radios through Bluetooth Low Energy or USB serial. Multiple radios can be connected simultaneously, including more than two and a mix of both families: the app sets no fixed device-count limit, but the practical maximum depends on the phone and connections and has not yet been established by physical testing. Chat provides two device viewing slots, with selectors for switching between all connected radios. Core radio messaging works without internet, a Windows computer, or a TCP radio connection.

This is an independent Android adaptation based on OverMesh Lite, incorporating original OverMesh assets. It is not an official OverMesh release. Credits and notices are available in **Config → About** and [LICENSE](LICENSE).

This draft describes the current implementation. Physical radio behavior and Android layouts still require the checks in [PHONE-TESTS.md](PHONE-TESTS.md). Screenshots and a walkthrough verified on a phone remain to be added.

## Contents

1. [Install and connect](#1-install-and-connect)
2. [Find your way around](#2-find-your-way-around)
   - [Config buttons, from top to bottom](#config-buttons-from-top-to-bottom)
3. [Use Chat](#3-use-chat)
4. [Save preset messages](#4-save-preset-messages)
5. [Understand sending and delivery](#5-understand-sending-and-delivery)
6. [Split messages and send to multiple destinations](#6-split-messages-and-send-to-multiple-destinations)
7. [Explore Nodes and signal information](#7-explore-nodes-and-signal-information)
8. [Notifications and background use](#8-notifications-and-background-use)
9. [Back up and restore](#9-back-up-and-restore)
10. [Update the app](#10-update-the-app)
11. [Troubleshooting](#11-troubleshooting)

## 1. Install and connect

You need an Android 8.0 or newer phone with a working Android WebView provider, a compatible MeshCore or Meshtastic radio, and either Bluetooth or a USB data connection. For USB, use a phone-compatible USB host adapter or cable as needed.

The GitHub repository is **Antonis512/SV8TTL-LoRa-Dash**: [open the repository](https://github.com/Antonis512/SV8TTL-LoRa-Dash). Open its **updates** folder and select **SV8TTL-LoRa-Dash-debug.apk**, or use this [direct APK download](https://raw.githubusercontent.com/Antonis512/SV8TTL-LoRa-Dash/main/updates/SV8TTL-LoRa-Dash-debug.apk). On your phone, open the downloaded APK from **Downloads** to install it.

Android may ask you to permit installation from the app opening the APK. The current package uses development signing. Installing a compatible update preserves app data; uninstalling removes it, so export a backup before considering removal.

### Connect a new Bluetooth radio

1. Power on the radio and enable Bluetooth on the phone.
2. Open **Config → Add Radio Connections**.
3. Under **MeshCore** or **Meshtastic**, tap **Scan new**.
4. Allow the Bluetooth permissions and activation prompts requested by Android.
5. Select your radio and complete any Android pairing prompt.
6. Wait for connection and initial radio data loading to finish. Use **Stop scanning** if you need to cancel discovery.

### Connect by USB

1. Connect the powered radio to the phone with a USB data cable and any required adapter.
2. Open **Config → Add Radio Connections**.
3. Tap **USB serial** under the correct radio family.
4. Select the device when prompted and grant Android's USB permission.
5. Wait for the radio's contacts and channels to load.

### Reconnect saved radios

Open **Config → My Nodes**. Connect an individual radio, or tick several and tap **Connect Selected**. A radio may show as queued while another completes its initial loading.

**Connect All Available** checks attached USB devices first, then discovers saved Bluetooth radios. It connects known identities; use **Add Radio Connections** for a new radio. Discovery does not guarantee a connection. If some connections fail, review the failed-device list and use its retry option.

Multiple radios can remain connected. Use the individual disconnect controls or **Disconnect all** when finished.

## 2. Find your way around

| Tab | Purpose |
| --- | --- |
| **Chat** | Channel messages and direct messages, with independent left and right radio selections. |
| **Nodes** | Contacts reported by the selected radio, filters, details and available actions. |
| **Map** | Under construction |
| **Log** | Under construction |
| **Config** | Radio connections, saved radios, presets, sending settings, notifications, backups, updates and About. |

**MC** means MeshCore. **MT** means Meshtastic. **DM** means direct message.

### Config buttons, from top to bottom

The following sections follow the order of the buttons on the main **Config** screen. Inside a section, **← Config** returns to that screen. A round **i** button shows or hides the explanation beside it. **×** and **Close** dismiss a settings window; use any explicit Save button before leaving an editor.

#### 1. My Nodes

Opens your saved radios and their locally stored messages. From top to bottom, its controls are:

| Control | What it does |
| --- | --- |
| **Connect All Available** | Looks for saved radios, checking attached USB devices before Bluetooth discovery, and attempts to connect them. |
| **i** buttons | Explain automatic discovery and individual/selected-radio connections. |
| Radio selection checkbox | Selects a saved radio for the batch connection buttons below the list. Selecting the checkbox alone does not connect it. |
| **Connect / Disconnect** on each radio | Connects that saved radio, or disconnects/cancels its current connection attempt. |
| **View messages** on each radio | Opens that radio's saved-message preview, including offline history. |
| **Remove** on each radio | Requests confirmation to remove the saved radio, its messages and channel names from this phone. A connected session for it is disconnected. Export a backup first if you want to keep the history. |
| **Connect Selected** | Connects the radios whose checkboxes you ticked. Initial loading may be queued. |
| **Disconnect Selected** | Disconnects the checked radios. |
| **Disconnect all** | Stops all radio connections. |
| **Export node backup** | Lets you choose a saved radio and export its JSON backup. |
| **Import node backup** | Opens the file picker to import a radio's JSON backup. |

Within **View messages**, the **i** button explains the preview limit. **Open Chat · browse all messages** opens the selected history in Chat, where older messages can be loaded. **Export backup** saves the radio's JSON history; **Export CSV** saves it for reading or analysis. Enter an age in days and press **Erase** to remove older stored messages through the confirmation flow. Exports and deletion apply to stored history beyond the preview. Channel-name buttons and **Direct** switch the preview between channel and DM messages. **× / Close** returns to My Nodes.

#### 2. Add Radio Connections

Opens connection controls for new radios. The **MeshCore** group comes first, followed by **Meshtastic**, with the same buttons in each:

| Button | What it does |
| --- | --- |
| **Scan new** | Starts Bluetooth discovery for that radio family and lets you choose a radio. Android may request permission or Bluetooth activation. |
| **USB serial** | Starts a USB connection for that family; select the attached device and allow USB access when prompted. |
| **Disconnect** | Appears when applicable and disconnects the session represented by that family panel. Use My Nodes for individual saved-radio control. |
| **Stop scanning** | Appears during scanning and stops discovery. |

#### 3. Preset Messages

Opens the reusable-message editor. Each message has a **Remove** button that removes its entry from the editor. Below the entries, **Add new** adds an empty message field and **Save** stores your additions, edits and removals. Up to 50 nonempty messages can be saved. To use them, open **Presets** in an expanded Chat or Reply editor; choosing a preset inserts it into the draft without sending it.

#### 4. Sending options

Opens three buttons, in this order:

| Button | What it opens |
| --- | --- |
| **Send to Multiple** | The enable switch, pacing settings, destination sets and saved-chain monitor. |
| **Message Split** | The switch for splitting oversized messages and the interval between parts. |
| **MeshCore DM attempts** | Known-route and flood attempt counts for supported MeshCore firmware. |

In **Send to Multiple**, controls run from **Enable Send to multiple**, through **Pause before next message** (1–30 seconds, default 3) and **MeshCore channel observation timeout** (1–300 seconds, default 5), to **Destination sets** and **Sending chain · pause / continue**. The **i** buttons explain the adjacent settings. **Destination sets** opens management of named destination groups. **Sending chain · pause / continue** opens the saved chain so you can inspect progress and control pending sends. **Close / ×** dismisses the window. Start a new multi-send from the button in Chat.

In **Message Split**, **Enable Message Split** allows oversized drafts to become separate messages. **Message Split interval** sets the delay after the previous part's result or observation window (1–300 seconds, default 5). The two **i** buttons explain these controls. **Close / ×** dismisses the window.

In **MeshCore DM attempts**, enter **Known route** and **Flood** counts, then press **Save attempts**. Each accepts 1–10 and includes the first transmission. The settings apply to new DMs on supported firmware. **Close / ×** dismisses the window. See [sending and delivery](#5-understand-sending-and-delivery) and [splitting and multi-send](#6-split-messages-and-send-to-multiple-destinations) for their effects.

#### 5. Request timeouts

Opens three numeric fields, from top to bottom: **Signal Quality**, **Ping**, and **Traceroute**. Each sets how long a new request waits for a response, from 5–600 seconds, with a default of 60. Changes save when a field changes; there is no separate Save button. A request already running retains its original timeout.

#### 6. Notifications

Opens three groups in this order:

| Group and controls | What they do |
| --- | --- |
| **Background message notifications** | **Show message previews when the app is in the background** enables background previews and background message sounds, subject to Android permission. **Android notification settings** opens the phone's notification settings for the app. |
| **Message Notification sounds** | **Play a sound for new messages** enables message audio. **Sound setup** selects separate MC/MT/DM sounds or one shared sound. **Select system sound / Change system sound** opens Android's sound picker for each displayed category. Its **Play** selector sets one, two or three plays. |
| **Unexpected Disconnection of Device** | **Play a sound when a device disconnects unexpectedly** enables disconnect audio. **Select system sound / Change system sound** chooses that sound, and **Play** sets its repeat count. Intentional disconnects and app closure do not trigger it. |

#### 7. App backup

Opens **Export full backup**, followed by **Import full backup**. Export saves all known radios, messages and app settings to a JSON file through Android's file picker. Import merges missing data while keeping existing settings and histories. Presets and destination sets are included; Signal Quality history is excluded. See [Back up and restore](#9-back-up-and-restore) for migration details.

#### 8. Channel backup

Opens a **Connected device** selector, followed by a **Channel** selector. Choose the radio and one of its reported channels, then press **Export channel as CSV**. The button becomes available when the required selection is ready. This exports that channel's messages stored on the phone, not a radio-configuration backup.

#### 9. Export debug log

Opens the export flow for the app's diagnostic log, up to 10 MB. Use it when reporting connection or app problems. Message bodies and keys are excluded. Review the exported file before sharing it.

#### 10. Check for Updates

Checks **Antonis512/SV8TTL-LoRa-Dash** on GitHub for a newer APK and displays the result. When offered, **Update** downloads and verifies the APK before opening Android's installer. **Close / ×** dismisses the window. Internet access is required for this check and download; Android asks separately for installation approval.

#### 11. About

Displays the installed version, **Beta** designation, **By SV8TTL**, the independent adaptation description, and open-source licenses and notices. The **OverMesh Lite by Slofi** link opens the upstream project. Use **← Config** to return.

## 3. Use Chat

### Choose between connected radios

At the top of Chat, the left and right device dropdowns sit beside their **MC / MT** buttons. Each dropdown lists connected radios and saved offline radios. Either side can show either family; the two slots are views, not a two-radio connection limit.

If more than two radios are connected:

1. Tap the device dropdown on the side you want to change.
2. Scroll through the choices if necessary and select the intended radio by its name and **MC / MT** family. Saved disconnected choices are marked **offline**; choose a connected radio to send.
3. For a channel message, choose that radio's channel in the selector immediately below. To activate a side whose channel is already selected, tap its **MC / MT** button.
4. For a DM, open **DM** and select a contact or saved conversation belonging to that radio.
5. Check the conversation title and destination before sending from the central editor.

For example, with radios A, B and C connected, you can display A on the left and B on the right, then choose C from the right dropdown. B remains connected and can still receive messages. Select B again from either dropdown to return to its messages. Changing a viewing slot does not disconnect the radio it previously showed. A **●** beside a radio in the dropdown indicates unread messages.

### Send a channel message

1. Open **Chat** and choose **Channels**.
2. Select the intended radio in a left or right device dropdown. If it is not currently displayed, open either dropdown and choose it from the connected devices, as described above.
3. Choose a channel reported by that radio in the selector below it. Tap that side's **MC / MT** button if you need to activate its already-selected channel.
4. Enter a short message in the central composer. Check the conversation title, selected radio and channel before sending.
5. Tap **Send** and review the status beneath the message.

Channel names come from the selected radio. If its data has not arrived, wait for loading to finish; the app does not create numbered channel choices. A saved offline radio can show previously saved channel names and history, but sending requires a connection.

### Start or reopen a direct message

Open **Nodes**, select the radio, tap a contact and choose its direct-message action. Chat opens for that contact. You can also open **DM** in Chat, choose the intended radio from either device dropdown and select a contact from that side's list. With more than two radios connected, use the dropdown to bring the third or later radio into either viewing slot first; then choose its contact. Check the recipient in the central conversation, enter your message and tap **Reply**.

Long-press **DM** to browse saved conversations for the selected side. Tap a name to reopen it. Long-press a name for **Delete this Conversation**; this removes the local conversation. Offline selections retain saved history, and drafts stay with their radio and peer.

### Read and manage messages

- Use **Users** to hide or show each side's contact list and make more room for messages.
- Use **A− / A+** to adjust message text size. **Enlarge** opens a larger contact-list panel.
- Tap a message for its available actions, such as copying text, replying or deleting it. Available actions depend on the network and saved message data.
- Long-press a message from a loaded contact to open node actions.
- Load older history when you reach the history paging control.
- Glowing Chat, Channels or DM indicators mark unread messages.

Meshtastic reactions require a message with a saved packet reference. The app offers 👍 and digits 0–7; received reactions may contain other emoji. Tap a reaction to see its sender information.

## 4. Save preset messages

1. Open **Config → Preset Messages**.
2. Tap **Add new** and enter reusable text. You can save up to 50 messages.
3. Tap **Save** after adding, editing or removing entries.
4. Open the expanded Chat or Reply editor and tap **Presets**.
5. Choose an entry, review the resulting draft and send when ready.

Choosing a preset inserts text without replacing the existing draft or sending it. Normal byte limits and splitting rules still apply. Full App Backup includes saved presets.

## 5. Understand sending and delivery

| Indication | What it establishes |
| --- | --- |
| Queued | The app is waiting to submit the message. |
| MeshCore channel Sent | The local radio accepted the message. This does not establish recipient delivery. |
| Heard Repeats | The receiving radio observed matching rebroadcast packets. Counts are not necessarily unique repeaters or recipient acknowledgments. |
| DM delivery acknowledgment | A matching acknowledgment was received for the message. |
| Timeout or unconfirmed | Expected feedback did not arrive in the allowed time. The message may still have been received remotely. |
| Failed | Review the error and connection before deciding whether to retry. |

For MeshCore channel messages, the message menu can show **Heard Repeats** or **Message Paths** when observations are available. Old messages and missing raw observations cannot reconstruct paths. Unknown or ambiguous path names remain unresolved.

MeshCore DMs support automatic retry attempts. Configure them in **Config → Sending options → MeshCore DM attempts**, then tap **Save attempts**. Counts include the first transmission. Configurable budgets apply to supported protocol versions; older firmware retains its supported behavior. **Stop retries** prevents further attempts but cannot recall a transmission already issued. Meshtastic retries are managed by its firmware.

## 6. Split messages and send to multiple destinations

### Long messages

The composer counts **UTF-8 bytes**, not characters. Greek letters and emoji may use several bytes each. Use the displayed limit for the selected radio and destination: Meshtastic uses 200 bytes; this version caps MeshCore DMs at 138 bytes, while MeshCore channel budgets also account for the sender name and packet overhead and may be smaller.

Open **Config → Sending options → Message Split** to enable splitting and set the interval between parts. The default interval is 5 seconds, with a range of 1–300 seconds. With splitting disabled, an oversized draft remains available for editing.

With splitting enabled, an ordinary oversized Chat or Reply submission produces separate messages. Queued bubbles may appear immediately, but each part waits for the previous result or observation window and then the configured interval. The receiver should not be assumed to combine them automatically.

If a part fails or remains unconfirmed, sending pauses for **Retry / Cancel**. Retry resends that part; Cancel stops the remaining parts of that message. Missing confirmation does not prove non-delivery, so a retry can produce a duplicate at the receiver. Ordinary split queues do not survive app closure as resumable multi-send chains.

### Send to multiple destinations

1. Enable **Config → Sending options → Send to Multiple → Enable Send to multiple**.
2. Return to **Chat** and tap **Send to multiple** between the Users controls.
3. Select a radio and choose channel or DM destinations. Use search for contacts as needed.
4. Tap **Save Destinations** for your choices, then repeat for other radios.
5. Enter the text, review the destinations and tap **Send Chain**.

Each destination uses its selected source radio. Required radios must be connected. Named **Destination sets**, managed under Send to Multiple settings, let you reuse groups and are included in App Backup.

Sequential sending waits for results; concurrent mode is also available. The default **Pause before next message** is 3 seconds after the result. MeshCore channel observation defaults to 5 seconds, waiting for a repeat or timeout. These are separate from the Message Split interval; existing saved preferences may differ.

Use the chain monitor to pause, continue and review individual outcomes. **Cancel remaining** stops pending items without recalling active transmissions. **Cancel Chain** clears the saved chain while retaining Chat history. An interrupted saved multi-send chain reopens paused after restart; inspect its results before continuing.

## 7. Explore Nodes and signal information

Choose a radio in **Nodes**, then search by name, ID or role. Filters include network, role, infrastructure/users, messageability and local favorites. A Last Heard visibility filter can hide older contacts; unknown timestamps remain unknown.

Tap a contact to inspect the details and supported actions. **Enter Name** adds a private app-local label; it does not rename the remote radio. Clear the label to remove it. Position and hardware details appear only when supplied by the network. MeshCore contact advertisements provide contact type rather than a hardware model.

Available actions depend on the contact and network:

- **Meshtastic:** DM, traceroute, node information, position requests and Signal Quality.
- **MeshCore:** DM, receiver path discovery, compatible learned-repeater trace, and status requests for supported contacts. Ping is available for supported repeaters and repeating room servers.
- **Map / Share:** Open a reported position in a phone map app or share node details through Android.

Meshtastic **Signal Quality** stores reception history separately for each receiving radio and remote node. Direct SNR/RSSI graphs exclude relayed and unknown-route receptions. Node statistics are separate and can be requested explicitly; unsupported requests may time out. The latest 1,000 records per pair are retained. **Signal history is not currently included in App Backup.**

Under **Config → Request timeouts**, set separate waits for Signal Quality, Ping and traceroute. Changes apply to new requests. A timeout is not a measurement of poor signal.

## 8. Notifications and background use

Open **Config → Notifications** to select shared or separate MC, MT and DM sounds, configure repeats, and enable an unexpected-disconnection sound.

Background message previews are off by default. Enable **Show message previews when the app is in the background** and grant Android notification permission when requested. Background message sounds also follow this switch. Foreground message sounds have their own setting.

Active connections use an Android foreground service with a separate running notification. Android permission and system settings affect notification visibility. Removing the app from Recents stops its radio sessions. The service does not automatically restore connections after app closure or process death. Keep the app in the foreground for initial radio checks.

## 9. Back up and restore

### Full backup

1. Open **Config → App backup → Export full backup**.
2. Choose a location using Android's file picker and keep the JSON file somewhere you can retrieve it.
3. To restore or merge, open **Import full backup** and select the file.

Full backup includes saved radio identities, channel names, messages and app settings, including presets and destination sets. Import adds missing data while retaining existing histories and settings. It is a merge, not a reset of the receiving phone.

Bluetooth permissions and pairing may need to be set up again. A selected sound can only be used if available on the receiving phone. Signal Quality history is excluded. The backup is not a firmware or complete radio-configuration backup.

### Selected history and CSV

Use the node backup controls around **My Nodes** for individual saved-radio JSON backups. **My Nodes → View messages** opens saved history.

For a channel CSV, open **Config → Channel backup**, select a connected device and one of its reported channels, then tap **Export channel as CSV**. It exports messages stored on this phone. DM history also offers CSV export. CSV is intended for reading or analysis; use JSON for app restoration.

## 10. Update the app

1. Connect the phone to the internet.
2. Open **Config → Check for Updates**.
3. If a newer version is offered, review it and tap **Update**.
4. Allow installation from this app if Android requests it, then approve Android's installer.

The updater checks the repository's version information and verifies the downloaded APK before opening the installer. An in-place update requires a higher version code and a compatible signing certificate. A signature mismatch cannot be repaired by the updater. Export a backup before removing an installed version.

## 11. Troubleshooting

| Symptom | What to check |
| --- | --- |
| Bluetooth radio does not appear | Confirm power, Bluetooth and requested permissions. Scan under the correct family; the discovery list filters for recognizable radios. |
| USB radio does not connect | Check the data cable/adapter and Android USB permission. Select USB serial under the correct family. |
| No channels appear | Confirm the intended radio is connected and loading has finished. Choices depend on its reported channel data. |
| Radio is red in Chat | The selected saved radio is offline. Reconnect it in My Nodes to send. |
| Expected node is missing | Check the selected radio, search, role and Last Heard filters. Your own saved radios are excluded from the Nodes contact list. |
| Message exceeds the limit | Read the byte counter, shorten the text or enable Message Split. Emoji and non-ASCII text may consume extra bytes. |
| Sent message has no delivery confirmation | Check whether it is a channel message or DM, and read its status. Radio acceptance and heard repeats do not establish recipient delivery. |
| Split message stops | Review the failed or unconfirmed part and choose Retry or Cancel. Reconnect its original radio if required. |
| Signal request times out | Check connectivity and firmware support. Increase the relevant request timeout only if appropriate; no response is not a signal measurement. |
| No background alert | Check the app's background-preview switch and Android notification permission/settings. Confirm the radio session is still connected. |
| Update will not install | Read the installer error and check version/signature compatibility. Preserve a backup before any uninstall. |

For a reproducible problem, record the app version from **Config → About**, phone/Android version, radio firmware, Bluetooth or USB transport, and the steps that led to the problem. Use **Config → Export debug log** to save diagnostics. This log excludes message bodies and keys; review exported files before sharing them.

### Remaining manual work

- Add screenshots from the actual Android app for connection, Chat, presets and backup workflows.
- Walk through the instructions with physical MeshCore and Meshtastic radios over Bluetooth and USB.
- Check narrow screens, keyboard-open layouts, display cutouts, gesture navigation and three-button navigation.
- Refine instructions from those observations without treating a successful build as proof of radio delivery.
