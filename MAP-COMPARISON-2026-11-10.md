# Official map behavior comparison — 10 November 2026

This is a source/documentation review, not a physical-radio test. No positioning implementation or APK was changed during this review.

## Meshtastic Android

Inspected revision `0ddf09db3bed50c653fdcd58559ad728b2dc6e53` of [Meshtastic-Android](https://github.com/meshtastic/Meshtastic-Android/tree/0ddf09db3bed50c653fdcd58559ad728b2dc6e53).

- `core/data/.../manager/NodeManagerImpl.kt`: retains `precision_bits`. If a more precise known coordinate falls in the same cell as an incoming reduced-precision report, it retains the precise coordinate and precision. A coarse report in a different cell can still move the node; it does not permanently pin nodes to old precise locations. The policy applies to both live positions and downloaded NodeInfo.
- `core/model/.../util/PositionPrecision.kt`: position-fix time uses the GPS timestamp when available, otherwise the position time. Reduced-precision values 10–19 have uncertainty radii; 13 bits corresponds to approximately 2.9 km. Zero/32 do not produce a degraded-position circle.
- `feature/map/.../NodeStack.kt`: groups nodes within 5 metres into a selectable stack. Own node and favorites receive priority for the top item; stable identity ordering avoids a changing top marker. This represents shared or near-shared coordinates, not inferred separate physical locations.
- `androidApp/src/google/.../map/component/NodeClusterMarkers.kt`: zoom-level clustering plus optional precision circles. The circles explain where a node could be; cluster counts explain how many nodes overlap.
- `feature/map/.../MapNodePolicy.kt`: positions must be valid before they are admitted to the map. Map filters and marker priority are separate from coordinates.

## MeshCore

The [official firmware README](https://github.com/meshcore-dev/MeshCore#readme) identifies `app.meshcore.nz` as the companion web app and links the Android/iOS apps. Direct requests to that web app were denied by this environment's network policy. Its current rendering, overlap controls and exact UI behavior therefore remain unverified; the following sources must not be represented as a complete companion-app UI inspection.

Verified official companion protocol and [meshcore.js revision `26193c059fcd1d0524f1ad5b19fd88287ecc5c61`](https://github.com/meshcore-dev/meshcore.js/tree/26193c059fcd1d0524f1ad5b19fd88287ecc5c61):

- A contact supplies its public key, advertised name/type, signed advertised latitude/longitude, last-advert time and last-modified value. Coordinates are degrees scaled by 1,000,000. Our decoder's contact offsets and scale agree.
- Advertised coordinates can be configured manually, so an advertised location is not necessarily a current GPS fix. A contact's last-advert timestamp is not proof of when its GPS position was measured.
- The examined contact record has no Meshtastic-style `precision_bits` field. Meshtastic precision rules must not be blindly applied to MeshCore contacts.

Separately inspected the **official MeshCore public map**, not the companion app: [map.meshcore.io revision `d77851401d81ef16ac4816ddc2d3c88457e5dfd7`](https://github.com/meshcore-dev/map.meshcore.io/tree/d77851401d81ef16ac4816ddc2d3c88457e5dfd7). It uses node API data, zoom-level cluster counts, and spreads overlapping markers visually with connecting legs at close zoom. Popups expose coordinates and freshness/update dates. Visual spreading does not recover more accurate geographic coordinates, and public-map update dates are not local-radio receipt times.

## Gaps in our app and recommended order

1. Carry MT coordinate presence, precision and both position time fields through decoding, session state, storage and backup. Keep phone receipt time as a separate value.
2. Preserve better-known MT coordinates for a same-cell degraded report, while accepting genuine movement to another cell. Validate coordinate pairs together, rather than combining a new axis with an old axis.
3. Rank per-receiver observations using known position metadata. The current All devices selector compares phone receipt timestamps, and equal/unknown values can leave the first saved receiver record selected. Unknown values cannot establish which report is newest.
4. Show precision/uncertainty and the selected receiver in the popup. Add a visible overlap count and chooser; optional visual spreading must keep a clear anchor to the reported coordinate. Do not fabricate separate locations to make markers look distinct.
5. Keep MC advertised-position provenance separate from MT position-fix precision and timestamps.

Affected case: Hermesh 0 `!9ea1a414`, Keep Chili `!ea8ee2b8`, Klif Aegina `!291b406e`. These are Meshtastic IDs. Shared reduced-precision coordinates or selection of an older receiver record can explain the symptoms, but the particular cause remains unconfirmed without the app's stored observations and the compared website's reports. No node was assigned guessed coordinates.

The environment draft now includes `app.meshcore.nz` in addition to the previously requested destinations. This is a saved proposal, not applied access. Review/save it in environment settings to permit direct companion-app inspection; publish the environment when the reusable configuration is ready.
