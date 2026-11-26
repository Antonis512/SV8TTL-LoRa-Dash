package org.overmesh.android;
import java.util.*;
public class ChannelCopySmoke {
 static int checks;static void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 static void rejects(Runnable r,String s){try{r.run();throw new AssertionError(s);}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args){
  for(boolean mc:new boolean[]{false,true}){
   ChannelAdmin source=new ChannelAdmin(mc,()->0),dest=new ChannelAdmin(mc,()->0);dest.ownNode(123);dest.observe(0,"Primary");dest.observe(2,"");
   byte[] key=new byte[16];Arrays.fill(key,(byte)7);source.observe(1,"Test");source.secrets.put(1,key.clone());
   ChannelCopyPlan plan=new ChannelCopyPlan(source,dest,1,"Test",4,5);
   rejects(()->plan.prepare(source,dest,false,true,4,5,"Test"),"source disconnected");
   rejects(()->plan.prepare(source,dest,true,false,4,5,"Test"),"destination disconnected/loading");
   rejects(()->plan.prepare(source,dest,true,true,6,5,"Test"),"source reconnect");
   rejects(()->plan.prepare(source,dest,true,true,4,6,"Test"),"destination reconnect");
   rejects(()->plan.prepare(new ChannelAdmin(mc,()->0),dest,true,true,4,5,"Test"),"protocol replaced");
   rejects(()->plan.prepare(source,dest,true,true,4,5,"Renamed"),"source renamed");
   source.secrets.get(1)[0]=9;rejects(()->plan.prepare(source,dest,true,true,4,5,"Test"),"key changed");source.secrets.put(1,key);
   dest.observe(3,"Test");rejects(()->plan.prepare(source,dest,true,true,4,5,"Test"),"duplicate name");dest.slots.remove(3);
   dest.observe(2,"Occupied");rejects(()->plan.prepare(source,dest,true,true,4,5,"Test"),"full destination");dest.observe(2,"");
   List<String> events=new ArrayList<>();RadioProtocol.Events listener=(RadioProtocol.Events)java.lang.reflect.Proxy.newProxyInstance(RadioProtocol.Events.class.getClassLoader(),new Class[]{RadioProtocol.Events.class},(o,m,v)->{if(m.getName().equals("channel"))events.add("saved");return null;});
   byte[] read=plan.prepare(source,dest,true,true,4,5,"Test");
   byte[] write;
   if(mc){dest.receiveMc(ChannelAdminSmoke.mc(2,"",new byte[16]),listener);write=dest.next();check(Arrays.equals(Arrays.copyOfRange(write,34,50),key),"MC copied key");dest.receiveMc(new byte[]{0},listener);dest.next();check(events.isEmpty(),"MC ack not success");dest.receiveMc(ChannelAdminSmoke.mc(2,"Test",key),listener);}
   else{dest.receiveMt(123,6,ChannelAdminSmoke.request(read),new ProtoWire.Writer().bytes(2,ChannelAdminSmoke.channel(2,0,"",new byte[0])).bytes(101,new byte[]{1}).build(),listener);write=dest.next();byte[] channel=ChannelAdminSmoke.bytes(ChannelAdminSmoke.bytes(ChannelAdminSmoke.bytes(ChannelAdminSmoke.bytes(write,1),4),2),33);check(Arrays.equals(ChannelAdminSmoke.bytes(ChannelAdminSmoke.bytes(channel,2),2),key),"MT copied key");dest.receiveMt(123,5,ChannelAdminSmoke.request(write),new byte[0],listener);byte[] verify=dest.next();check(events.isEmpty(),"MT ack not success");dest.receiveMt(123,6,ChannelAdminSmoke.request(verify),new ProtoWire.Writer().bytes(2,ChannelAdminSmoke.channel(2,2,"Test",key)).build(),listener);}
   check(events.size()==1&&"Primary".equals(dest.slots.get(0)),"verified copy preserves primary");
   rejects(()->new ChannelCopyPlan(source,new ChannelAdmin(!mc,()->0),1,"Test",1,1),"cross-network");rejects(()->new ChannelCopyPlan(source,source,1,"Test",1,1),"same radio");
  }
  System.out.println("Channel copying passed: "+checks+" assertions (MC and MT)");
 }
}
