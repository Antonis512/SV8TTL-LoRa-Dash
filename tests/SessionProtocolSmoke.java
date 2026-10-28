package org.overmesh.android;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class SessionProtocolSmoke {
    static void check(boolean value,String why) {if(!value)throw new AssertionError(why);}
    public static void main(String[] args) {
        List<String> heard=new ArrayList<>();List<Object[]> messages=new ArrayList<>();List<Boolean> adverts=new ArrayList<>();
        RadioProtocol.Events events=(RadioProtocol.Events)Proxy.newProxyInstance(RadioProtocol.Events.class.getClassLoader(),new Class[]{RadioProtocol.Events.class},(proxy,m,v)->{
            if(m.getName().equals("lastHeard"))heard.add(v[0]+":"+v[1]);
            if(m.getName().equals("channelMessage"))messages.add(v);
            if(m.getName().equals("advertResult"))adverts.add((Boolean)v[1]);return null;
        });
        MeshtasticProtocol mt=new MeshtasticProtocol();
        byte[] command=mt.sendChannelReply(3,"Channel reply",0xf1234567L);
        ProtoWire.Reader to=new ProtoWire.Reader(command);byte[] packet=null;
        while(to.next()) {if(to.field==1)packet=to.bytes();else to.skip();}
        ProtoWire.Reader mesh=new ProtoWire.Reader(packet);long destination=0,channel=0;byte[] data=null;
        while(mesh.next()) {if(mesh.field==2)destination=mesh.number();else if(mesh.field==3)channel=mesh.number();else if(mesh.field==4)data=mesh.bytes();else mesh.skip();}
        check(destination==0xffffffffL&&channel==3,"Reply stays broadcast on selected channel");
        ProtoWire.Reader payload=new ProtoWire.Reader(data);long reply=0;String body="";
        while(payload.next()){if(payload.field==7)reply=payload.number();else if(payload.field==2)body=new String(payload.bytes(),StandardCharsets.UTF_8);else payload.skip();}
        check(reply==0xf1234567L&&body.equals("Channel reply"),"Reply reference and body encoded separately");
        byte[] incoming=new ProtoWire.Writer().fixed32(1,0xabcdef12L).fixed32(2,0xffffffffL).varint(3,3).bytes(4,data).fixed32(6,7654).fixed32(7,1700000000).build();
        mt.receive(new ProtoWire.Writer().bytes(2,incoming).build(),events);
        check(messages.size()==1&&(long)messages.get(0)[5]==7654&&(long)messages.get(0)[6]==0xf1234567L,"Incoming references retained");
        check(heard.contains("!abcdef12:1700000000"),"Live MT rx timestamp");
        byte[] node=new ProtoWire.Writer().varint(1,0xabcdef13L).fixed32(5,1600000000).build();
        mt.receive(new ProtoWire.Writer().bytes(4,node).build(),events);
        check(heard.contains("!abcdef13:1600000000"),"Old MT node keeps actual last heard");
        MeshCoreProtocol mc=new MeshCoreProtocol();byte[] contact=new byte[144];contact[0]=3;contact[1]=8;contact[33]=2;
        long stamp=1600000000L;for(int i=0;i<4;i++)contact[132+i]=(byte)(stamp>>>(8*i));mc.receive(contact,events);
        check(heard.contains("080000000000:1600000000"),"MC advert timestamp, not connection time");
        mc.advert("zero");check(adverts.isEmpty(),"Sending is not success");mc.receive(new byte[]{1,2},events);
        check(adverts.equals(List.of(false)),"Advert rejection stays failure");
        mc.advert("flood");mc.receive(new byte[]{0},events);check(adverts.equals(List.of(false,true)),"Advert radio ACK confirms success");
        System.out.println("Session protocol checks passed: channel reply references, real timestamps, advert result");
    }
}
