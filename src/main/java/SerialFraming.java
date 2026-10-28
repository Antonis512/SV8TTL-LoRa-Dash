package org.overmesh.android;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.function.Consumer;

/** Meshtastic and MeshCore use different headers over the same USB serial transport. */
final class SerialFraming {
    enum Kind { MESHTASTIC, MESHCORE }
    private final Kind kind;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    SerialFraming(Kind kind) { this.kind = kind; }

    byte[] encode(byte[] payload) {
        if (payload.length > 512) throw new IllegalArgumentException("Serial frame too large");
        byte[] out = new byte[payload.length + 4];
        if (kind == Kind.MESHTASTIC) {
            out[0] = (byte)0x94; out[1] = (byte)0xc3;
            out[2] = (byte)(payload.length >>> 8); out[3] = (byte)payload.length;
        } else {
            out = new byte[payload.length + 3]; out[0] = 0x3c;
            out[1] = (byte)payload.length; out[2] = (byte)(payload.length >>> 8);
        }
        System.arraycopy(payload, 0, out, out.length - payload.length, payload.length);
        return out;
    }

    void feed(byte[] bytes, Consumer<byte[]> receiver) {
        pending.writeBytes(bytes); byte[] all = pending.toByteArray(); int pos = 0;
        while (pos < all.length) {
            int mark = findMarker(all, pos);
            if (mark < 0) {
                // Keep a possible first magic byte that arrived alone at a chunk boundary.
                pos = kind == Kind.MESHTASTIC && all.length > 0 && (all[all.length - 1] & 255) == 0x94
                        ? all.length - 1 : all.length;
                break;
            }
            int header = kind == Kind.MESHTASTIC ? 4 : 3;
            if (all.length - mark < header) { pos = mark; break; }
            int length = kind == Kind.MESHTASTIC
                    ? ((all[mark + 2] & 255) << 8) | (all[mark + 3] & 255)
                    : (all[mark + 1] & 255) | ((all[mark + 2] & 255) << 8);
            if (length > 512 || length == 0) { pos = mark + 1; continue; }
            if (all.length - mark < header + length) { pos = mark; break; }
            receiver.accept(Arrays.copyOfRange(all, mark + header, mark + header + length));
            pos = mark + header + length;
        }
        pending.reset(); if (pos < all.length) pending.write(all, pos, all.length - pos);
        if (pending.size() > 1024) pending.reset();
    }

    private int findMarker(byte[] bytes, int start) {
        for (int i = start; i < bytes.length; i++) {
            if (kind == Kind.MESHCORE && (bytes[i] & 255) == 0x3e) return i;
            if (kind == Kind.MESHTASTIC && i + 1 < bytes.length && (bytes[i] & 255) == 0x94 && (bytes[i + 1] & 255) == 0xc3) return i;
        }
        return -1;
    }
}
