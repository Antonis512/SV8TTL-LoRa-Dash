package org.overmesh.android;
import java.util.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

public class ChannelAdminSmoke {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static byte[] bytes(byte[] data,int field){ProtoWire.Reader r=new ProtoWire.Reader(data);while(r.next()){if(r.field==field)return r.bytes();r.skip();}return new byte[0];}
    static long number(byte[] data,int field){ProtoWire.Reader r=new ProtoWire.Reader(data);while(r.next()){if(r.field==field)return r.number();r.skip();}return 0;}
    static long request(byte[] frame){return number(bytes(frame,1),6);}
    static byte[] channel(int slot,int role,String name,byte[] key){return new ProtoWire.Writer().varint(1,slot).bytes(2,new ProtoWire.Writer().bytes(2,key).bytes(3,name.getBytes(StandardCharsets.UTF_8)).build()).varint(3,role).build();}
    static byte[] response(long id,int port,byte[] body){return new ProtoWire.Writer().bytes(2,new ProtoWire.Writer().fixed32(1,123).bytes(4,new ProtoWire.Writer().varint(1,port).bytes(2,body).fixed32(6,id).build()).build()).build();}
    static byte[] mc(int slot,String name,byte[] key){byte[] p=new byte[50];p[0]=18;p[1]=(byte)slot;byte[] n=name.getBytes(StandardCharsets.UTF_8);System.arraycopy(n,0,p,2,n.length);System.arraycopy(key,0,p,34,16);return p;}
    static void rejects(Runnable run){try{run.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        long[] clock={1000};List<String> results=new ArrayList<>(),channels=new ArrayList<>();
        RadioProtocol.Events events=(RadioProtocol.Events)Proxy.newProxyInstance(RadioProtocol.Events.class.getClassLoader(),new Class[]{RadioProtocol.Events.class},(p,m,v)->{if(m.getName().equals("channelSetupResult"))results.add((String)v[0]);if(m.getName().equals("channel"))channels.add(v[0]+":"+v[1]);return null;});
        ChannelAdmin removal=new ChannelAdmin(true,()->clock[0]);removal.observeMc(1,"Test",new byte[]{1});
        removal.remove(1,"Test");removal.receiveMc(mc(1,"Test",new byte[16]),events);
        byte[] clear=removal.next();check(clear!=null&&clear[0]==32&&clear[1]==1,"Remove writes the selected slot");
        for(int i=2;i<clear.length;i++)check(clear[i]==0,"Removal clears name and secret");
        removal.receiveMc(new byte[]{0},events);check(removal.next()[0]==31,"Removal requires read-back");
        removal.receiveMc(mc(1,"",new byte[16]),events);check(!removal.active()&&removal.freeSlot()==1,"Verified removal frees slot");
        boolean primaryProtected=false;try{removal.remove(0,"Public");}catch(IllegalArgumentException expected){primaryProtected=true;}check(primaryProtected,"Primary protected");
        results.clear();channels.clear();
        ChannelAdmin mtRemoval=new ChannelAdmin(false,()->clock[0]);mtRemoval.ownNode(123);mtRemoval.observe(2,"Secondary");
        byte[] removeRead=mtRemoval.remove(2,"Secondary");
        mtRemoval.receiveMt(123,6,request(removeRead),new ProtoWire.Writer().bytes(2,channel(2,2,"Secondary",new byte[]{1})).bytes(101,new byte[]{9}).build(),events);
        byte[] removeWrite=mtRemoval.next(),removedChannel=bytes(bytes(bytes(bytes(removeWrite,1),4),2),33);
        check(number(removedChannel,1)==2&&number(removedChannel,3)==0,"MT removal disables only selected secondary slot");
        check(results.isEmpty(),"MT removal write is not confirmation");
        mtRemoval.receiveMt(123,5,request(removeWrite),new byte[0],events);byte[] removeVerify=mtRemoval.next();
        check(removeVerify!=null&&results.isEmpty(),"MT removal ACK still requires read-back");
        mtRemoval.receiveMt(123,6,request(removeVerify),new ProtoWire.Writer().bytes(2,channel(2,0,"",new byte[0])).build(),events);
        check(!mtRemoval.active()&&mtRemoval.freeSlot()==2&&results.get(0).contains("removed and verified"),"MT removal verified disabled");
        mtRemoval.observe(2,"Secondary");removeRead=mtRemoval.remove(2,"Secondary");
        mtRemoval.receiveMt(123,6,request(removeRead),new ProtoWire.Writer().bytes(2,channel(2,1,"Secondary",new byte[]{1})).bytes(101,new byte[]{9}).build(),events);
        check(mtRemoval.next()==null&&!mtRemoval.active(),"MT role changed to primary is protected even outside slot zero");
        results.clear();channels.clear();
        MeshtasticProtocol mt=new MeshtasticProtocol(()->clock[0]);
        mt.receive(new ProtoWire.Writer().bytes(3,new ProtoWire.Writer().varint(1,123).build()).build(),events);
        mt.receive(new ProtoWire.Writer().bytes(10,channel(0,1,"Primary",new byte[]{1})).build(),events);
        mt.receive(new ProtoWire.Writer().bytes(10,channel(1,0,"",new byte[0])).build(),events);
        check(mt.channelAdmin().freeSlot()==1,"Use only reported disabled secondary slots");
        byte[] read=mt.addChannel("Rescue","default","");
        byte[] admin=bytes(bytes(bytes(read,1),4),2);
        check(number(admin,1)==2,"MT GET uses one-based slot");
        byte[] pass={9,8,7};
        mt.receive(response(request(read),6,new ProtoWire.Writer().bytes(2,channel(1,0,"",new byte[0])).bytes(101,pass).build()),events);
        byte[] write=mt.nextCommand(),writeAdmin=bytes(bytes(bytes(write,1),4),2),saved=bytes(writeAdmin,33);
        check(number(saved,1)==1&&number(saved,3)==2,"Addition is secondary and preserves primary");
        check(Arrays.equals(bytes(writeAdmin,101),pass),"Fresh session key accompanies write");
        check(mt.channelAdmin().active()&&results.isEmpty(),"Write is not success");
        mt.receive(response(request(write),5,new byte[0]),events);byte[] verify=mt.nextCommand();
        check(verify!=null,"ACK requests read-back");
        mt.receive(response(request(verify),6,new ProtoWire.Writer().bytes(2,channel(1,2,"Rescue",new byte[]{1})).build()),events);
        check(!mt.channelAdmin().active()&&channels.contains("1:Rescue")&&results.get(0).contains("verified"),"MT read-back confirms saved channel");
        rejects(()->mt.addChannel("Other","default",""));
        ChannelAdmin a=new ChannelAdmin(true,()->clock[0]);a.observe(0,"Public");a.observe(3,"");
        byte[] hash=ChannelAdmin.key(true,"hashtag","#test","");
        check(java.util.HexFormat.of().formatHex(hash).equals("9cd8fcf22a47333b591d96a2b848b73f"),"MC hashtag interoperability vector");
        check(Arrays.equals(a.start("#test","hashtag",""),new byte[]{31,3}),"Reported MC empty slot, not invented index");
        a.receiveMc(mc(3,"",new byte[16]),events);write=a.next();
        check(write.length==50&&write[0]==32&&write[1]==3&&Arrays.equals(Arrays.copyOfRange(write,34,50),hash),"MC wire payload");
        a.receiveMc(new byte[]{0},events);check(Arrays.equals(a.next(),new byte[]{31,3}),"MC OK requires read-back");
        a.receiveMc(mc(3,"#test",hash),events);check(!a.active()&&channels.contains("3:#test"),"MC verified channel");
        a.observe(4,"");a.start("Private","private","01020304050607080910111213141516");a.receiveMc(mc(4,"Occupied",new byte[16]),events);
        check(a.next()==null&&!a.active(),"Do not overwrite occupied slot");
        a.observe(5,"");a.start("Next","private","01020304050607080910111213141516");clock[0]+=16000;a.poll(events);
        check(a.stalled&&!a.active(),"Timeout blocks uncorrelated MC retries");
        rejects(()->a.start("Again","hashtag",""));
        rejects(()->ChannelAdmin.key(true,"private","x","bad"));
        rejects(()->ChannelAdmin.key(false,"private","x","AQ=="));
        rejects(()->ChannelAdmin.key(true,"private","x","00000000000000000000000000000000"));
        ChannelAdmin reserved=new ChannelAdmin(true,()->clock[0]);reserved.observeMc(2,"",hash);
        check(reserved.freeSlot()==-1,"Unnamed nonzero-key MC slots are not empty");
        ChannelAdmin denied=new ChannelAdmin(true,()->clock[0]);denied.observe(2,"");denied.start("#test","hashtag","");
        denied.receiveMc(new byte[]{1,3},events);check(!denied.active()&&denied.next()==null,"Rejected read cannot proceed to a write");
        denied.start("#test","hashtag","");denied.receiveMc(mc(2,"",new byte[16]),events);denied.next();denied.receiveMc(new byte[]{0},events);denied.next();
        denied.receiveMc(mc(2,"#wrong",hash),events);check(results.get(results.size()-1).contains("did not match"),"Mismatch is not success");
        MeshCoreProtocol live=new MeshCoreProtocol(()->clock[0],()->1000);live.initialCommand();
        live.receive(mc(0,"",new byte[16]),events);live.nextCommand();live.receive(new byte[]{10},events);
        live.addChannel("#test","hashtag","");rejects(()->live.advert("zero"));
        live.receive(new byte[]{(byte)131},events);check(live.nextCommand()==null,"Queued-message push cannot interleave channel transaction");
        live.receive(mc(0,"",new byte[16]),events);check(live.nextCommand()[0]==32,"MC transaction integrated with receive loop");
        clock[0]+=16000;live.poll(events);live.receive(mc(0,"#late",hash),events);
        check(live.channelAdmin().stalled&&live.nextCommand()==null,"Late reply after timeout cannot restart channel loading");
        System.out.println("Channel management protocol checks passed");
    }
}
