package org.overmesh.android;
import java.util.*;
import java.lang.reflect.Proxy;
public class SignalQualitySmoke {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static byte[] response(long token,long from,byte[] stats){
  byte[] data=new ProtoWire.Writer().varint(1,67).fixed32(6,token).bytes(2,new ProtoWire.Writer().bytes(6,stats).build()).build();
  return new ProtoWire.Writer().bytes(2,new ProtoWire.Writer().fixed32(1,from).bytes(4,data).fixed32(8,Float.floatToIntBits(0f)).varint(12,-102).varint(9,2).varint(15,3).build()).build();
 }
 public static void main(String[] args){
  MeshtasticProtocol p=new MeshtasticProtocol();List<String> results=new ArrayList<>();
  RadioProtocol.Events events=(RadioProtocol.Events)Proxy.newProxyInstance(RadioProtocol.Events.class.getClassLoader(),new Class[]{RadioProtocol.Events.class},(o,m,v)->{if(m.getName().equals("signalQuality"))results.add((String)v[2]);return null;});
  byte[] command=p.nodeCommand("signal","!12345678","",2);long token=p.lastRequestId();
  ProtoWire.Reader root=new ProtoWire.Reader(command);root.next();ProtoWire.Reader mesh=new ProtoWire.Reader(root.bytes());byte[] decoded=null;long dest=0;int channel=0;
  while(mesh.next()){if(mesh.field==2)dest=mesh.number();else if(mesh.field==3)channel=(int)mesh.number();else if(mesh.field==4)decoded=mesh.bytes();else mesh.skip();}
  check(dest==0x12345678&&channel==2,"Target and selected contact channel");
  ProtoWire.Reader data=new ProtoWire.Reader(decoded);int port=0;boolean response=false;byte[] body=null;
  while(data.next()){if(data.field==1)port=(int)data.number();else if(data.field==2)body=data.bytes();else if(data.field==3)response=data.number()==1;else data.skip();}
  check(port==67&&response&&Arrays.equals(body,new byte[]{50,0}),"LocalStats request, not traceroute");
  byte[] stats=new ProtoWire.Writer().varint(15,-110).fixed32(2,Float.floatToIntBits(12.5f)).build();
  p.receive(response(token,0x87654321L,stats),events);check(results.isEmpty(),"Wrong sender ignored");
  p.receive(response(token+1,0x12345678,stats),events);check(results.isEmpty(),"Wrong token ignored");
  p.receive(response(token,0x12345678,stats),events);check(results.size()==1&&!p.requestPending(token),"Matching reply completes request");
  check(results.get(0).contains("-110 dBm")&&results.get(0).contains("0.0 dB")&&results.get(0).contains("-102 dBm")&&results.get(0).contains("final radio hop"),"Signed values, zero SNR, relayed reception");
  p.receive(response(token,0x12345678,stats),events);check(results.size()==1,"Duplicate ignored");
  check(SignalQuality.describe(new byte[0],null,null,-1).contains("SNR: not reported"),"Absent is not zero");
  p.nodeCommand("signal","!12345678","",2);token=p.lastRequestId();p.expireRequest(token);p.receive(response(token,0x12345678,stats),events);check(results.size()==1,"Expired response ignored");
  System.out.println("Signal Quality request, correlation, measurements and expiry passed");
 }
}
