package org.overmesh.android;

import java.util.Arrays;

public final class PersistentProtocolSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int version:new int[]{3,4})for(boolean routed:new boolean[]{false,true}){
            RetryProtocolSmoke.Rig r=new RetryProtocolSmoke.Rig(version,routed);
            r.send();long id=r.radio.lastRequestId();r.radio.stopFutureAttempts(id);r.accepted(42);
            check(r.due()==null,"Persistent MC send cannot start a short-interval retry");
            check(!r.radio.automaticDeliveryPending(id),"Single MC attempt finishes its response window");
            r.ack(42);check(r.states.get(r.states.size()-1).endsWith(":delivered"),"Single attempt preserves late ACK correlation");
        }
        MeshtasticProtocol mt=new MeshtasticProtocol();
        byte[] key=new byte[]{1};
        byte[] channel=new ProtoWire.Writer().varint(1,0).bytes(2,new ProtoWire.Writer().bytes(2,key).build()).varint(3,1).build();
        mt.receive(new ProtoWire.Writer().bytes(10,channel).build(),RadioActionsSmoke.events);
        check(Arrays.equals(mt.channelAdmin().secrets.get(0),key),"Unnamed enabled MT default channel retains fingerprint material");
        mt.waypointCommand("persistent-test","Location","",37.9,23.7,0,"!12345678");
        long token=mt.lastRequestId();mt.receive(RadioActionsSmoke.packet(0x12345678L,token,5,new byte[0]),RadioActionsSmoke.events);
        check(RadioActionsSmoke.deliveries.get(RadioActionsSmoke.deliveries.size()-1).endsWith(":delivered"),"MT direct location uses recipient ACK");
        System.out.println("Persistent protocol: MC one-attempt scheduling and late ACKs on legacy/current firmware, MT default channel and direct waypoint passed");
    }
}
