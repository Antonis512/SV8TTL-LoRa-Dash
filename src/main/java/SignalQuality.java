package org.overmesh.android;

/** Local display of LocalStats wire fields; missing measurements stay unknown. */
final class SignalQuality {
    static String describe(byte[] bytes,Float snr,Integer rssi,int hops) {
        StringBuilder text=new StringBuilder("Node statistics\n");
        ProtoWire.Reader r=new ProtoWire.Reader(bytes);boolean noise=false;
        while(r.next()) {
            int field=r.field;
            if(field==2||field==3) {
                float value=Float.intBitsToFloat((int)r.number());
                if(Float.isFinite(value))text.append(field==2?"Channel utilization: ":"Transmit airtime: ").append(value).append("%\n");
            } else if(field==1||field==4||field==5||field==6||field==7||field==8||field==9||field==10||field==11||field==15) {
                long value=r.number();String label;
                switch(field) {
                    case 1:label="Uptime (seconds)";break;
                    case 4:label="Packets sent";break;case 5:label="Packets received";break;
                    case 6:label="Bad packets";break;case 7:label="Online nodes";break;
                    case 8:label="Total nodes";break;case 9:label="Duplicate packets";break;
                    case 10:label="Relayed packets";break;case 11:label="Cancelled relays";break;
                    default:label="Noise floor";value=(int)value;noise=value!=0;break;
                }
                if(field!=15||noise)text.append(label).append(": ").append(value).append(field==15?" dBm":"").append('\n');
            } else r.skip();
        }
        if(!noise)text.append("Noise floor: not reported\n");
        text.append("\nResponse reception at your radio\nSNR: ").append(snr!=null&&Float.isFinite(snr)?snr+" dB":"not reported");
        text.append("\nRSSI: ").append(rssi!=null?rssi+" dBm":"not reported");
        text.append(hops==0?"\nDirect reception.":"\nThese reception values describe the final radio hop, not the entire route.");
        return text.toString();
    }
}
