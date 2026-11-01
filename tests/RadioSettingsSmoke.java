package org.overmesh.android;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class RadioSettingsSmoke {
    static void check(boolean result,String message){if(!result)throw new AssertionError(message);}
    static void little(byte[] bytes,int offset,int value){for(int i=0;i<4;i++)bytes[offset+i]=(byte)(value>>>(8*i));}
    public static void main(String[] args){
        List<String> labels=new ArrayList<>(),order=new ArrayList<>();
        RadioProtocol.Events events=(RadioProtocol.Events)Proxy.newProxyInstance(RadioProtocol.Events.class.getClassLoader(),new Class[]{RadioProtocol.Events.class},
                (proxy,method,values)->{if(method.getName().equals("radioSettings")){labels.add((String)values[0]);order.add("rf");}if(method.getName().equals("ownNode"))order.add("own");return null;});
        MeshtasticProtocol mt=new MeshtasticProtocol();
        byte[] preset=new ProtoWire.Writer().varint(1,1).varint(2,0).build();
        mt.receive(new ProtoWire.Writer().bytes(5,new ProtoWire.Writer().bytes(6,preset).build()).build(),events);
        check(labels.get(0).equals("Long Fast"),"Meshtastic preset");
        byte[] custom=new ProtoWire.Writer().varint(3,125).varint(4,11).varint(5,5).build();
        mt.receive(new ProtoWire.Writer().bytes(5,new ProtoWire.Writer().bytes(6,custom).build()).build(),events);
        check(labels.get(1).contains("BW 125 kHz · SF11 · CR4/5"),"Meshtastic custom RF");
        order.clear();
        byte[] self=new byte[58];self[0]=5;little(self,48,869618);little(self,52,62500);self[56]=8;self[57]=5;
        new MeshCoreProtocol().receive(self,events);
        check(labels.get(2).equals("869.618 MHz · BW 62.5 kHz · SF8 · CR4/5"),"MeshCore companion RF");
        check(order.equals(List.of("own","rf")),"MeshCore identity precedes RF settings");
        System.out.println("Radio settings parser checks passed");
    }
}
