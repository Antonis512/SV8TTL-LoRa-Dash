package org.overmesh.android;

import java.lang.reflect.Proxy;
import java.util.*;

public class FavoriteProtocolSmoke {
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static byte[] bytes(byte[] data,int field){ProtoWire.Reader r=new ProtoWire.Reader(data);while(r.next()){if(r.field==field)return r.bytes();r.skip();}return new byte[0];}
    static long number(byte[] data,int field){ProtoWire.Reader r=new ProtoWire.Reader(data);while(r.next()){if(r.field==field)return r.number();r.skip();}return 0;}
    static long request(byte[] frame){return number(bytes(frame,1),6);}
    static byte[] body(byte[] frame){return bytes(bytes(bytes(frame,1),4),2);}
    static void rejects(Runnable r){try{r.run();throw new AssertionError("Expected busy rejection");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        long[] now={0};List<String> states=new ArrayList<>(),results=new ArrayList<>();
        RadioProtocol.Events e=(RadioProtocol.Events)Proxy.newProxyInstance(RadioProtocol.Events.class.getClassLoader(),new Class[]{RadioProtocol.Events.class},(p,m,v)->{
            if(m.getName().equals("favorite"))states.add(v[0]+":"+v[1]);
            if(m.getName().equals("favoriteResult"))results.add(v[2]+":"+v[3]);return null;
        });
        FavoriteAdmin mt=new FavoriteAdmin(false,()->now[0]);mt.ownNode(123);
        byte[] read=mt.start("!abcdef12",true,null);
        check(number(bytes(read,1),2)==123&&number(body(read),3)==1,"MT requests credentials from local radio");
        byte[] owner=new ProtoWire.Writer().bytes(4,new byte[0]).bytes(101,new byte[]{7,8}).build();
        mt.receiveMt(456,6,request(read),owner,e);check(mt.next()==null,"Reject foreign radio response");
        mt.receiveMt(123,6,request(read)+1,owner,e);check(mt.next()==null,"Reject unrelated response");
        mt.receiveMt(123,6,request(read),owner,e);byte[] write=mt.next();
        check(number(body(write),39)==0xabcdef12L&&Arrays.equals(bytes(body(write),101),new byte[]{7,8}),"MT favorite command and passkey");
        check(states.isEmpty(),"No optimistic favorite before ACK");
        mt.receiveMt(123,5,request(write),new byte[0],e);
        check(states.contains("!abcdef12:true")&&!mt.active(),"MT success after local ACK");
        read=mt.start("!abcdef12",false,null);mt.receiveMt(123,6,request(read),owner,e);write=mt.next();
        check(number(body(write),40)==0xabcdef12L,"MT unfavorite command");
        mt.receiveMt(123,5,request(write),new ProtoWire.Writer().varint(3,3).build(),e);
        check(!states.contains("!abcdef12:false")&&results.get(results.size()-1).startsWith("false:"),"MT rejection retains confirmed value");
        read=mt.start("!abcdef12",false,null);mt.receiveMt(123,6,request(read),new ProtoWire.Writer().bytes(4,new byte[0]).build(),e);
        check(!mt.active()&&mt.next()==null,"Missing passkey must not write");
        mt.start("!abcdef12",false,null);now[0]+=16000;mt.poll(e);check(!mt.active(),"MT timeout completes without success");

        FavoriteAdmin mc=new FavoriteAdmin(true,()->now[0]);byte[] contact=new byte[148];new Random(42).nextBytes(contact);contact[0]=3;contact[34]=(byte)0xa6;
        byte[] key=Arrays.copyOfRange(contact,1,33);String id="abcdefabcdef";
        check(mc.start(id,true,key)[0]==30,"MC fetches fresh contact before editing");
        mc.receiveMc(contact,e);byte[] update=mc.next();
        check(update.length==144&&update[0]==9&&update[34]==(byte)0xa7,"MC only changes favorite bit and omits lastmod");
        for(int i=1;i<144;i++)if(i!=34)check(update[i]==contact[i],"Preserve MC contact byte "+i);
        mc.receiveMc(new byte[]{0},e);check(mc.next()[0]==30&&mc.active(),"MC OK requires read-back");
        contact[34]|=1;mc.receiveMc(contact,e);check(!mc.active()&&states.contains(id+":true"),"MC verified favorite");
        mc.start(id,false,key);mc.receiveMc(contact,e);update=mc.next();check(update[34]==(byte)0xa6,"Unfavorite preserves permission bits");
        mc.receiveMc(new byte[]{0},e);mc.next();mc.receiveMc(contact,e);
        check(results.get(results.size()-1).startsWith("false:"),"Mismatched read-back fails");
        mc.start(id,false,key);now[0]+=16000;mc.poll(e);check(mc.blocked()&&!mc.active(),"MC timeout locks ambiguous response stream");
        rejects(()->mc.start(id,false,key));check(mc.receiveMc(new byte[]{0},e),"Ignore late MC ACK");
        mc.reset();mc.start(id,false,key);mc.receiveMc(new byte[]{1,2},e);check(!mc.active(),"MC rejection");

        MeshtasticProtocol protocol=new MeshtasticProtocol();
        protocol.receive(new ProtoWire.Writer().bytes(4,new ProtoWire.Writer().varint(1,0xabcdef12L).varint(10,1).build()).build(),e);
        check(states.contains("!abcdef12:true"),"MT snapshot reads favorites");
        protocol.receive(new ProtoWire.Writer().bytes(4,new ProtoWire.Writer().varint(1,0xabcdef12L).build()).build(),e);
        check(states.contains("!abcdef12:false"),"MT absent proto3 flag means false");
        MeshCoreProtocol live=new MeshCoreProtocol(()->now[0],()->1000);live.initialCommand();
        byte[] channel=new byte[50];channel[0]=18;live.receive(channel,e);live.nextCommand();live.receive(new byte[]{10},e);
        Arrays.fill(contact,1,33,(byte)0xab);contact[34]=(byte)0xa7;live.receive(contact,e);
        check(states.contains("abababababab:true"),"MC snapshot reads favorite bit");
        live.setFavorite("abababababab",false);rejects(()->live.advert("zero"));rejects(()->live.addChannel("x","private","bad"));
        live.receive(new byte[]{(byte)131},e);check(live.nextCommand()==null,"Message push cannot interleave favorite transaction");
        live.receive(contact,e);check(live.nextCommand()[0]==9,"MC receive loop emits update");
        live.receive(new byte[]{0},e);check(live.nextCommand()[0]==30,"MC receive loop emits verification");
        contact[34]&=0xfe;live.receive(contact,e);check(states.contains("abababababab:false"),"MC receive loop reports confirmed removal");
        System.out.println("Favorite protocol checks passed");
    }
}
