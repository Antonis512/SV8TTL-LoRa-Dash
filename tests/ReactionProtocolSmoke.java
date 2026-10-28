package org.overmesh.android;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class ReactionProtocolSmoke {
 static void check(boolean x,String why){if(!x)throw new AssertionError(why);}
 static byte[] bytes(byte[] input,int field){ProtoWire.Reader r=new ProtoWire.Reader(input);while(r.next()){if(r.field==field)return r.bytes();r.skip();}return null;}
 static long number(byte[] input,int field){ProtoWire.Reader r=new ProtoWire.Reader(input);while(r.next()){if(r.field==field)return r.number();r.skip();}return 0;}
 public static void main(String[] args){
  MeshtasticProtocol mt=new MeshtasticProtocol();
  for(int i=0;i<8;i++){
   byte[] packet=bytes(mt.sendReaction("",2,String.valueOf(i),0xf1234567L),1),data=bytes(packet,4);
   check(number(packet,2)==0xffffffffL&&number(packet,3)==2,"Channel stays broadcast");
   check(number(data,7)==0xf1234567L&&number(data,8)==1,"Reference and reaction marker");
   check(new String(bytes(data,2),StandardCharsets.UTF_8).equals(String.valueOf(i)),"Digit payload");
  }
  byte[] dm=bytes(mt.sendReaction("!abcdef12",3,"7",22),1);check(number(dm,2)==0xabcdef12L&&number(dm,3)==3,"DM reaction recipient/channel");
  byte[] thumbs=bytes(bytes(mt.sendReaction("",2,"👍",22),1),4);
  check(new String(bytes(thumbs,2),StandardCharsets.UTF_8).equals("👍")&&number(thumbs,8)==1,"Thumbs up is an allowed reaction");
  for(String bad:List.of("8","-1","❤️","00","")){boolean rejected=false;try{mt.sendReaction("",0,bad,1);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Reject "+bad);}
  List<Object[]> reactions=new ArrayList<>(),directs=new ArrayList<>();
  RadioProtocol.Events events=(RadioProtocol.Events)Proxy.newProxyInstance(RadioProtocol.Events.class.getClassLoader(),new Class[]{RadioProtocol.Events.class},(p,m,v)->{if(m.getName().equals("reaction"))reactions.add(v);if(m.getName().equals("directPacket"))directs.add(v);return null;});
  mt.receive(new ProtoWire.Writer().bytes(3,new ProtoWire.Writer().varint(1,0x12345678L).build()).build(),events);
  for(String emoji:List.of("❤️","👨‍👩‍👧‍👦","0")){
   byte[] data=new ProtoWire.Writer().varint(1,1).bytes(2,emoji.getBytes(StandardCharsets.UTF_8)).fixed32(7,55).fixed32(8,1).build();
   byte[] packet=new ProtoWire.Writer().fixed32(1,0xabcdef12L).fixed32(2,0xffffffffL).varint(3,2).bytes(4,data).fixed32(6,99).build();
   mt.receive(new ProtoWire.Writer().bytes(2,packet).build(),events);
   check(reactions.get(reactions.size()-1)[3].equals(emoji),"Unicode payload retained");
  }
  byte[] data=new ProtoWire.Writer().varint(1,1).bytes(2,"👍".getBytes(StandardCharsets.UTF_8)).fixed32(7,55).fixed32(8,1).build();
  byte[] packet=new ProtoWire.Writer().fixed32(1,0xabcdef12L).fixed32(2,0x12345678L).bytes(4,data).fixed32(6,100).build();
  mt.receive(new ProtoWire.Writer().bytes(2,packet).build(),events);check(reactions.get(3)[2].equals("!abcdef12"),"DM reaction scope");check(directs.isEmpty(),"Reactions never become standalone DMs");
  data=new ProtoWire.Writer().varint(1,1).bytes(2,"normal DM".getBytes(StandardCharsets.UTF_8)).build();
  packet=new ProtoWire.Writer().fixed32(1,0xabcdef12L).fixed32(2,0x12345678L).bytes(4,data).fixed32(6,101).build();
  mt.receive(new ProtoWire.Writer().bytes(2,packet).build(),events);check(directs.size()==1&&(long)directs.get(0)[4]==101,"DM packet reference preserved");
  System.out.println("Reaction protocol checks passed: Unicode, numeric restriction, channel/DM scope and packet references");
 }
}
