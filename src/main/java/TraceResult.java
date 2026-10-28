package org.overmesh.android;

import java.util.ArrayList;
import java.util.List;

/** Radio-reported trace data. SNR values are signed quarter-dB, -128 means unknown. */
final class TraceResult {
    final long requestId;
    final String target, note;
    final List<String> forward, backward;
    final List<Integer> forwardSnr, backwardSnr;
    TraceResult(long requestId, String target, List<String> forward, List<String> backward,
                List<Integer> forwardSnr, List<Integer> backwardSnr, String note) {
        this.requestId=requestId; this.target=target; this.forward=forward; this.backward=backward;
        this.forwardSnr=forwardSnr; this.backwardSnr=backwardSnr; this.note=note;
    }
    static List<Long> repeated(ProtoWire.Reader reader, boolean fixed) {
        List<Long> values=new ArrayList<>();
        if(reader.wire!=2) { values.add(reader.number()); return values; }
        byte[] bytes=reader.bytes();
        for(int p=0;p<bytes.length;) {
            long value=0;
            if(fixed) {
                if(bytes.length-p<4) throw new IllegalArgumentException("Truncated route ID");
                for(int k=0;k<4;k++) value|=(long)(bytes[p++]&255)<<(k*8);
            } else {
                boolean complete=false;
                for(int shift=0;shift<64&&p<bytes.length;shift+=7) {
                    int b=bytes[p++]&255; value|=(long)(b&127)<<shift;
                    if((b&128)==0) { complete=true; break; }
                }
                if(!complete) throw new IllegalArgumentException("Truncated route SNR");
            }
            values.add(value);
        }
        return values;
    }
}
