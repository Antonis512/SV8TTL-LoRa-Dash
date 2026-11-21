package org.overmesh.android;

import java.lang.reflect.Proxy;
import java.util.*;

public class IgnoreProtocolSmoke {
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static void rejects(Runnable call){try{call.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException expected){}}
    static byte[] owner(){return new ProtoWire.Writer().bytes(4,new byte[0]).bytes(101,new byte[]{4,5,6}).build();}
    static byte[] response(long from,long id,int port,byte[] body){return new ProtoWire.Writer().bytes(2,new ProtoWire.Writer().fixed32(1,from).bytes(4,new ProtoWire.Writer().varint(1,port).bytes(2,body).fixed32(6,id).build()).build()).build();}
    public static void main(String[] args){
        List<String> states=new ArrayList<>(),results=new ArrayList<>();long[] now={0};
        RadioProtocol.Events events=(RadioProtocol.Events)Proxy.newProxyInstance(RadioProtocol.Events.class.getClassLoader(),new Class[]{RadioProtocol.Events.class},(p,m,v)->{
            if(m.getName().equals("ignored"))states.add(v[0]+":"+v[1]);
            if(m.getName().equals("ignoreResult"))results.add(v[2]+":"+v[3]);
            if(m.getName().equals("favoriteResult"))throw new AssertionError("Ignore reported as favorite");return null;
        });
        MeshtasticProtocol mt=new MeshtasticProtocol(()->now[0]);
        mt.receive(new ProtoWire.Writer().bytes(3,new ProtoWire.Writer().varint(1,123).build()).build(),events);
        mt.receive(new ProtoWire.Writer().bytes(4,new ProtoWire.Writer().varint(1,456).varint(11,1).build()).build(),events);
        check(states.contains("!000001c8:true"),"Read ignored state from radio snapshot");
        mt.receive(new ProtoWire.Writer().bytes(4,new ProtoWire.Writer().varint(1,456).build()).build(),events);
        check(states.contains("!000001c8:false"),"Missing proto3 ignore field clears state");states.clear();
        byte[] read=mt.setIgnored("!000001c8",true);long request=FavoriteProtocolSmoke.request(read);
        check(FavoriteProtocolSmoke.number(FavoriteProtocolSmoke.bytes(read,1),2)==123,"Ignore admin command targets local radio");
        rejects(()->mt.setFavorite("!000001c8",true));rejects(()->mt.addChannel("x","default",""));
        mt.receive(response(124,request,6,owner()),events);check(mt.nextCommand()==null,"Ignore wrong-radio response");
        mt.receive(response(123,request+1,6,owner()),events);check(mt.nextCommand()==null,"Ignore wrong request ID");
        mt.receive(response(123,request,6,owner()),events);byte[] write=mt.nextCommand();
        check(FavoriteProtocolSmoke.number(FavoriteProtocolSmoke.body(write),47)==456,"Set ignored field 47");
        check(Arrays.equals(FavoriteProtocolSmoke.bytes(FavoriteProtocolSmoke.body(write),101),new byte[]{4,5,6}),"Authenticated admin write");
        check(states.isEmpty(),"No optimistic ignored state");
        mt.receive(response(123,FavoriteProtocolSmoke.request(write),5,new byte[0]),events);
        check(states.contains("!000001c8:true")&&results.get(0).startsWith("true:"),"Matching ACK confirms ignore");
        read=mt.setIgnored("!000001c8",false);mt.receive(response(123,FavoriteProtocolSmoke.request(read),6,owner()),events);write=mt.nextCommand();
        check(FavoriteProtocolSmoke.number(FavoriteProtocolSmoke.body(write),48)==456,"Remove ignored field 48");
        mt.receive(response(123,FavoriteProtocolSmoke.request(write),5,new ProtoWire.Writer().varint(3,3).build()),events);
        check(!states.contains("!000001c8:false")&&results.get(results.size()-1).startsWith("false:"),"Rejection preserves ignored state");
        read=mt.setIgnored("!000001c8",false);mt.receive(response(123,FavoriteProtocolSmoke.request(read),6,owner()),events);write=mt.nextCommand();
        mt.receive(response(123,FavoriteProtocolSmoke.request(write),5,new byte[0]),events);check(states.contains("!000001c8:false"),"Unignore confirmed");
        read=mt.setIgnored("!000001c8",true);mt.receive(response(123,FavoriteProtocolSmoke.request(read),6,new ProtoWire.Writer().bytes(4,new byte[0]).build()),events);
        check(!mt.favoriteAdmin().active()&&mt.nextCommand()==null,"Missing credentials do not write");
        read=mt.setIgnored("!000001c8",true);now[0]+=16000;mt.poll(events);int count=states.size();
        mt.receive(response(123,FavoriteProtocolSmoke.request(read),6,owner()),events);check(mt.nextCommand()==null&&states.size()==count,"Late reply after timeout ignored");
        mt.setIgnored("!000001c8",true);mt.cancelAll();check(!mt.favoriteAdmin().active(),"Disconnect cancels transaction");
        rejects(()->mt.setIgnored("!0000007b",true));rejects(()->new MeshCoreProtocol().setIgnored("abcdefabcdef",true));
        System.out.println("Ignore protocol checks passed");
    }
}
