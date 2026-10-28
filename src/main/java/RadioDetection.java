package org.overmesh.android;

/** Accept an identity response, rather than treating arbitrary serial traffic as a radio. */
final class RadioDetection {
    static boolean isIdentity(boolean meshcore, byte[] packet) {
        if(meshcore) return packet.length>=36 && packet[0]==5;
        try {
            boolean identity=false;
            ProtoWire.Reader root=new ProtoWire.Reader(packet);
            while(root.next()) {
                if(root.field==3 && root.wire==2) {
                    ProtoWire.Reader info=new ProtoWire.Reader(root.bytes());
                    while(info.next()) {
                        if(info.field==1) identity=info.number()!=0;
                        else info.skip();
                    }
                } else root.skip();
            }
            return identity;
        } catch(IllegalArgumentException malformed) { return false; }
    }
}
