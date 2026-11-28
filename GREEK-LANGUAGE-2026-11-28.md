# Greek interface and language selection

Config contains the approved Language (Γλώσσα) frame above Check for Updates, with a white heading and an extra blank line before the update button. Select Language (Επιλογή γλώσσας) opens an English / Ελληνικά picker. Applying saves the choice in Android preferences and updates the interface without reloading the WebView. Member mode uses the same preference; leaving member mode does not reset the language.

The catalog imports all 1,924 entries from the supplied SV8TTL_Greek_Translation_COMPLETE.xlsx. Import validates template and formatting placeholders and rejects mismatches; none were rejected. Five additional short UI fragments are supplied locally. Radio and Node use Κόμβος, with plural Κόμβοι. Existing application identity, signing and version are unchanged.

Static HTML labels and explicitly identified JavaScript display strings use the catalog. Native dialog and button literals and notification headings use the same Greek JSON data. User names, channel names, message bodies, drafts, input values and protocol keys are not translated. Unknown or unmapped text falls back to English; importing a catalog does not certify that every dynamically assembled sentence has a translation binding. Entries marked for context review in the supplied workbook remain translation proposals, not independently verified wording.

Language selection persists through the app_language preference; older member_language preferences provide the migration fallback. Both keys are synchronized when the language changes. No radio connection is required to select a language, and selecting a language does not send messages or reconnect radios.

Browser checks cover English/Greek round trips, placeholder values, preservation of user names and drafts, selector widths of 320 and 390 pixels, reduced keyboard-height layouts, member mode, Contacts, signal history and settings. Physical Android navigation/cutout/keyboard checks and both MC/MT radio tests remain pending.

Source import: tools/import-greek.py. Runtime catalogs: src/main/assets/greek.json and greek.js. Source migration helpers translate only UI expressions and must be reviewed before use on additional source files. They are not build requirements.
