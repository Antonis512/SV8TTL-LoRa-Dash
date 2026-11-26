package org.overmesh.android;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class NameFreshnessSmoke {
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static byte[] user(String name){return new ProtoWire.Writer().bytes(2,name.getBytes(StandardCharsets.UTF_8)).build();}
    static void little(byte[] bytes,int offset,int value){for(int i=0;i<4;i++)bytes[offset+i]=(byte)(value>>>(8*i));}
    public static void main(String[] args){
        List<Object[]> reports=new ArrayList<>();
        RadioProtocol.Events events=(RadioProtocol.Events)Proxy.newProxyInstance(RadioProtocol.Events.class.getClassLoader(),new Class[]{RadioProtocol.Events.class},
                (p,m,v)->{if(m.getName().equals("nodeName"))reports.add(v);return null;});
        MeshtasticProtocol mt=new MeshtasticProtocol();
        mt.receive(new ProtoWire.Writer().bytes(3,new ProtoWire.Writer().varint(1,123).build()).build(),events);
        check(reports.isEmpty(),"synthetic own-node name must not be persisted");
        mt.receive(new ProtoWire.Writer().bytes(4,new ProtoWire.Writer().varint(1,456).bytes(2,user("Cached")).varint(5,1900000000).build()).build(),events);
        check((long)reports.get(0)[2]==0,"last heard is not name freshness");
        byte[] data=new ProtoWire.Writer().varint(1,4).bytes(2,user("Live")).build();
        mt.receive(new ProtoWire.Writer().bytes(2,new ProtoWire.Writer().fixed32(1,456).bytes(4,data).fixed32(7,1700000000).build()).build(),events);
        check(reports.get(1)[1].equals("Live")&&(long)reports.get(1)[2]==1700000000000L,"live name retains packet timestamp");
        mt.receive(new ProtoWire.Writer().bytes(2,new ProtoWire.Writer().fixed32(1,456).bytes(4,data).build()).build(),events);
        check((long)reports.get(2)[2]==0,"missing packet timestamp stays unknown");
        long before=System.currentTimeMillis();
        mt.receive(new ProtoWire.Writer().bytes(4,new ProtoWire.Writer().varint(1,123).bytes(2,user("Self")).build()).build(),events);
        check((long)reports.get(3)[2]>=before&&reports.get(3)[1].equals("Self"),"direct self identity has observation time");
        mt.receive(new ProtoWire.Writer().bytes(4,new ProtoWire.Writer().varint(1,456).bytes(2,user("")).build()).build(),events);
        check(reports.get(4)[1].equals(""),"cached display fallback does not become a new name report");
        MeshCoreProtocol mc=new MeshCoreProtocol();
        byte[] contact=new byte[148];contact[0]=3;contact[1]=7;contact[33]=1;contact[35]=(byte)255;
        System.arraycopy("MC name".getBytes(StandardCharsets.UTF_8),0,contact,100,7);little(contact,132,1700000001);
        mc.receive(contact,events);
        check((long)reports.get(5)[2]==1700000001000L&&reports.get(5)[1].equals("MC name"),"MC snapshot retains advert time");
        contact[0]=(byte)0x8a;little(contact,132,1700000002);mc.receive(contact,events);
        check((long)reports.get(6)[2]==1700000002000L,"MC pushed contact retains advert time");
        System.out.println("Name freshness protocol checks passed");
    }
}
