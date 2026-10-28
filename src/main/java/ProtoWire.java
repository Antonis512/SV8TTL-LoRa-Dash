package org.overmesh.android;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Small protobuf wire reader/writer for the PhoneAPI fields used by this prototype. */
final class ProtoWire {
    private ProtoWire() {}

    static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        Writer varint(int field, long value) { rawVarint((field << 3)); rawVarint(value); return this; }
        Writer fixed32(int field, long value) {
            rawVarint((field << 3) | 5);
            for (int i = 0; i < 4; i++) out.write((int)(value >>> (8 * i)) & 255);
            return this;
        }
        Writer bytes(int field, byte[] value) {
            rawVarint((field << 3) | 2); rawVarint(value.length); out.writeBytes(value); return this;
        }
        byte[] build() { return out.toByteArray(); }
        private void rawVarint(long n) {
            while ((n & ~127L) != 0) { out.write(((int)n & 127) | 128); n >>>= 7; }
            out.write((int)n);
        }
    }

    static final class Reader {
        private final byte[] data;
        private int pos;
        int field;
        int wire;
        Reader(byte[] data) { this.data = data; }
        boolean next() {
            if (pos >= data.length) return false;
            long tag = rawVarint(); field = (int)(tag >>> 3); wire = (int)(tag & 7);
            if (field == 0) throw new IllegalArgumentException("Invalid protobuf field");
            return true;
        }
        long number() {
            if (wire == 0) return rawVarint();
            if (wire == 5) {
                require(4); long value = 0;
                for (int i = 0; i < 4; i++) value |= (long)(data[pos++] & 255) << (8 * i);
                return value;
            }
            throw new IllegalArgumentException("Not an integer field");
        }
        byte[] bytes() {
            if (wire != 2) throw new IllegalArgumentException("Not a bytes field");
            long size = rawVarint();
            if (size < 0 || size > data.length - pos) throw new IllegalArgumentException("Invalid protobuf length");
            byte[] value = Arrays.copyOfRange(data, pos, pos + (int)size); pos += (int)size;
            return value;
        }
        void skip() {
            if (wire == 0 || wire == 5) { number(); return; }
            if (wire == 2) { bytes(); return; }
            if (wire == 1) { require(8); pos += 8; return; }
            throw new IllegalArgumentException("Unsupported protobuf wire type " + wire);
        }
        private long rawVarint() {
            long result = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                require(1); int b = data[pos++] & 255;
                result |= (long)(b & 127) << shift;
                if ((b & 128) == 0) return result;
            }
            throw new IllegalArgumentException("Varint too long");
        }
        private void require(int n) {
            if (data.length - pos < n) throw new IllegalArgumentException("Truncated protobuf");
        }
    }
}
