package org.overmesh.android;
import java.lang.reflect.Proxy;
import java.util.*;
public class LoadingProtocolSmoke {
 static void check(boolean x,String why){if(!x)throw new AssertionError(why);}
 public static void main(String[] args){
  long[] clock={0};int[] completed={0},failed={0},messages={0};
  RadioProtocol.Events events=(RadioProtocol.Events)Proxy.newProxyInstance(RadioProtocol.Events.class.getClassLoader(),new Class[]{RadioProtocol.Events.class},(p,m,v)->{
   if(m.getName().equals("syncComplete"))completed[0]++;if(m.getName().equals("syncFailed"))failed[0]++;if(m.getName().equals("message"))messages[0]++;return null;
  });
  MeshCoreProtocol mc=new MeshCoreProtocol(()->clock[0],()->1000);mc.initialCommand();
  mc.receive(new byte[]{(byte)131},events);check(mc.nextCommand()==null,"Push cannot interrupt initial configuration");
  byte[] channel=new byte[50];channel[0]=18;channel[2]='A';mc.receive(channel,events);
  check(Arrays.equals(mc.nextCommand(),new byte[]{10}),"Initial message request");
  mc.receive(new byte[]{(byte)131},events);check(mc.nextCommand()==null,"No duplicate request while waiting");
  for(int i=0;i<120;i++) {
   byte[] packet=new byte[9];packet[0]=8;packet[8]='a';mc.receive(packet,events);
   check(Arrays.equals(mc.nextCommand(),new byte[]{10}),"Continue beyond 50, message "+i);
   check(completed[0]==0,"Do not infer completion from a count");
  }
  mc.receive(new byte[]{27,0,0,0,0,0,0,0},events);
  check(Arrays.equals(mc.nextCommand(),new byte[]{10}),"Unsupported binary queue item must not stall later text");
  check(messages[0]==120,"Binary data must not become chat text");
  check(mc.nextCommand()==null,"Only one next-message request may be in flight");
  byte[] afterBinary=new byte[9];afterBinary[0]=8;afterBinary[8]='b';mc.receive(afterBinary,events);
  check(messages[0]==121,"Text after binary data is still delivered");
  check(Arrays.equals(mc.nextCommand(),new byte[]{10}),"Continue draining after subsequent text");
  mc.receive(new byte[]{10},events);check(completed[0]==1&&messages[0]==121,"Only no-more-messages completes loading");check(mc.nextCommand()==null,"Queue drained");
  mc.receive(new byte[]{(byte)131},events);check(Arrays.equals(mc.nextCommand(),new byte[]{10}),"New message push still works");
  byte[] liveMessage=new byte[9];liveMessage[0]=8;liveMessage[8]='c';mc.receive(liveMessage,events);
  check(messages[0]==122,"Live message delivered after initial sync");
  check(Arrays.equals(mc.nextCommand(),new byte[]{10}),"Live queue continues draining");
  mc.receive(new byte[]{10},events);check(completed[0]==1,"Live queue drain must not repeat initial sync completion");
  mc.receive(new byte[]{(byte)131},events);check(Arrays.equals(mc.nextCommand(),new byte[]{10}),"Subsequent queue check starts");
  clock[0]=31000;mc.poll(events);check(failed[0]==1,"Stalled response reports an error");mc.poll(events);check(failed[0]==1,"Timeout reported once");
  mc.receive(new byte[]{10},events);check(completed[0]==1,"Late response cannot declare timed-out session loaded");
  boolean blocked=false;try{mc.advert("zero");}catch(IllegalArgumentException e){blocked=true;}check(blocked,"Reconnect required after untagged timeout");
  MeshCoreProtocol empty=new MeshCoreProtocol();empty.initialCommand();empty.receive(channel,events);empty.nextCommand();empty.receive(new byte[]{10},events);check(completed[0]==2,"Empty queue completes normally");
  System.out.println("Loading checks passed: 120 queued messages, empty queue, push coalescing and timeout");
 }
}
