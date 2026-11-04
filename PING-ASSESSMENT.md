# Meshtastic low-airtime ping assessment — 4 November 2026

This is a proposal, not an implemented feature in 0.10.22.

A small, directed packet with `want_ack=true` is a useful way to check recent reachability before a two- or three-part direct message. It uses firmware delivery machinery and does not need to collect traceroute hop lists. The existing application already distinguishes a Meshtastic DM's destination acknowledgment from relay-only evidence in `MeshtasticProtocol.readPacket`.

`ROUTING_APP` carries routing acknowledgment/error messages; naming that port alone does not specify a complete ping protocol. Implementation still needs a valid minimal probe payload and tests against the actual radio firmware. Do not assume the example `REPLY_APP` echo service is present: the current upstream module setup leaves `new ReplyModule()` commented out.

Suggested manual action: **Ping Node**, available for a loaded Meshtastic node through a connected receiver. Keep it out of channel broadcasts and automatic scans of the node list. Allow one pending probe per receiver, suppress repeated taps, and retain the observation time and the receiver used. The firmware should manage retransmissions; the app should not create additional probe packets while that request remains pending. Use an appropriate mesh hop allowance; a zero-hop check only tests direct reachability.

| Result | Meaning |
| --- | --- |
| Destination confirmed | A matching acknowledgment came from the target. The node responded at that time; this is not a human read receipt. |
| Relay heard only | A repeat/implicit acknowledgment was observed, but there is no destination confirmation. Continue waiting for a matching destination result until the observation window ends. |
| NAK | The firmware reported an error. Show the reason and source when available; a local queue or channel error is not proof that the remote node is offline. |
| Timeout / retries exhausted | Reachability could not be confirmed. The probe or its return acknowledgment may have been lost. Distinguish a phone-side observation timeout from a radio-reported retry exhaustion. |

The packet ID and originating receiver must match the pending request, and success must identify the intended remote sender. MQTT-delivered responses should not be presented as proof of an RF-only path. Firmware versions differ in what delivery/proof metadata they expose; use an unconfirmed result where evidence is insufficient.

This can save airtime when an unavailable node would otherwise receive a long send attempt. It adds traffic when a node is already reachable, so make it optional. A short probe succeeding also cannot guarantee that a longer packet, or all three subsequent parts, will arrive. No airtime percentage is promised without measuring packet size, modem preset, path, acknowledgments and retries on real radios.

Primary references inspected:

- [Meshtastic mesh algorithm](https://meshtastic.org/docs/overview/mesh-algo/)
- [ReliableRouter.cpp](https://github.com/meshtastic/firmware/blob/develop/src/mesh/ReliableRouter.cpp): directed acknowledgments, retransmissions and implicit relay acknowledgments.
- [RoutingModule.cpp](https://github.com/meshtastic/firmware/blob/develop/src/modules/RoutingModule.cpp): routing ACK/NAK handling.
- [Modules.cpp](https://github.com/meshtastic/firmware/blob/develop/src/modules/Modules.cpp): example ReplyModule is not enabled in the inspected setup.

These are upstream development sources, not evidence of the firmware version installed on the user's radios. No source was copied into the application.
