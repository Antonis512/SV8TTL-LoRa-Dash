package org.overmesh.android;

public final class RadioDetectionSmoke {
    private static void check(boolean value) {if(!value)throw new AssertionError();}
    public static void main(String[] args) {
        byte[] mt=new ProtoWire.Writer().bytes(3,new ProtoWire.Writer().varint(1,1234).build()).build();
        byte[] mc=new byte[36];mc[0]=5;
        check(RadioDetection.isIdentity(false,mt));
        check(RadioDetection.isIdentity(true,mc));
        check(!RadioDetection.isIdentity(true,mt));
        check(!RadioDetection.isIdentity(false,mc));
        check(!RadioDetection.isIdentity(true,new byte[]{5}));
        check(!RadioDetection.isIdentity(false,new byte[]{26,99}));
        check(!RadioDetection.isIdentity(false,new ProtoWire.Writer().varint(7,123).build()));
        // Both serial framing parsers must retain identities across read boundaries.
        byte[] mtFrame=new SerialFraming(SerialFraming.Kind.MESHTASTIC).encode(mt);
        byte[] mcFrame=new SerialFraming(SerialFraming.Kind.MESHCORE).encode(mc);mcFrame[0]=0x3e;
        for(boolean meshcore:new boolean[]{false,true}) {
            byte[] frame=meshcore?mcFrame:mtFrame;
            for(int split=1;split<frame.length;split++) {
                SerialFraming parser=new SerialFraming(meshcore?SerialFraming.Kind.MESHCORE:SerialFraming.Kind.MESHTASTIC);
                int[] found={0};
                java.util.function.Consumer<byte[]> receive=packet->{if(RadioDetection.isIdentity(meshcore,packet))found[0]++;};
                parser.feed(java.util.Arrays.copyOfRange(frame,0,split),receive);
                parser.feed(java.util.Arrays.copyOfRange(frame,split,frame.length),receive);
                check(found[0]==1);
            }
        }
        System.out.println("Radio detection passed: identities, wrong protocols, malformed traffic, fragmented serial frames.");
    }
}
