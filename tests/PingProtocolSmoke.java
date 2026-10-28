package org.overmesh.android;
import java.util.*;
import java.lang.reflect.Proxy;
public class PingProtocolSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  List<TraceResult> results=new ArrayList<>();
  RadioProtocol.Events events=(RadioProtocol.Events)Proxy.newProxyInstance(RadioProtocol.Events.class.getClassLoader(),new Class[]{RadioProtocol.Events.class},(p,m,v)->{if(m.getName().equals("trace"))results.add((TraceResult)v[0]);return null;});
  for(int width:new int[]{1,2,4}){
   MeshCoreProtocol mc=new MeshCoreProtocol();
   byte[] contact=new byte[148];contact[0]=3;Arrays.fill(contact,1,33,(byte)0xbb);contact[33]=2;contact[35]=1;contact[36]=(byte)0xaa;mc.receive(contact,events);
   byte[] ping=mc.nodeCommand("ping","bbbbbbbbbbbb",""+width,0);long token=mc.lastRequestId();
   check(ping[0]==36&&ping.length==10+width,"Zero hop ignores learned route");
   check(ping[9]==(width==1?0:width==2?1:2),"Width flags");
   for(int i=10;i<ping.length;i++)check(ping[i]==(byte)0xbb,"Destination prefix");
   byte[] response=new byte[14+width];response[0]=(byte)0x89;response[2]=(byte)width;response[3]=ping[9];System.arraycopy(ping,1,response,4,4);System.arraycopy(ping,10,response,12,width);response[12+width]=-8;response[13+width]=16;
   int before=results.size();response[4]^=1;mc.receive(response,events);check(results.size()==before,"Ignore wrong token");response[4]^=1;
   mc.receive(Arrays.copyOf(response,response.length-1),events);check(results.size()==before,"Ignore truncated reply");
   mc.receive(response,events);TraceResult result=results.get(before);
   check(result.requestId==token&&result.forward.size()==2&&result.backward.size()==2,"Direct round trip");
   check(result.forwardSnr.equals(List.of(-8))&&result.backwardSnr.equals(List.of(16)),"SNR each way");
   check(result.note.startsWith("Zero-hop Ping reply in"),"Elapsed time");
  }
  MeshCoreProtocol mc=new MeshCoreProtocol();byte[] client=new byte[148];client[0]=3;client[33]=1;mc.receive(client,events);
  try{mc.nodeCommand("ping","000000000000","1",0);throw new AssertionError("Client ping accepted");}catch(IllegalArgumentException expected){}
  System.out.println("Ping protocol checks passed");
 }
}
