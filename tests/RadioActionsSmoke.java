package org.overmesh.android;

import java.lang.reflect.Proxy;
import java.util.*;
import java.io.ByteArrayOutputStream;

public final class RadioActionsSmoke {
    static final List<String> deliveries=new ArrayList<>();
    static final List<TraceResult> traces=new ArrayList<>();
    static final List<String> exports=new ArrayList<>();
    static final RadioProtocol.Events events=(RadioProtocol.Events)Proxy.newProxyInstance(
        RadioProtocol.Events.class.getClassLoader(),new Class<?>[]{RadioProtocol.Events.class},(proxy,method,values)->{
            if(method.getName().equals("delivery")) deliveries.add(values[0]+":"+values[1]);
            if(method.getName().equals("trace")) traces.add((TraceResult)values[0]);
            if(method.getName().equals("advertExport")) exports.add((String)values[0]);
            return null;
        });
    static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    static byte[] packet(long from,long request,int port,byte[] body) {
        byte[] data=new ProtoWire.Writer().varint(1,port).bytes(2,body).fixed32(6,request).build();
        return new ProtoWire.Writer().bytes(2,new ProtoWire.Writer().fixed32(1,from).fixed32(2,0x12345678L).bytes(4,data).build()).build();
    }
    static void le(byte[] target,int offset,long value) {for(int i=0;i<4;i++)target[offset+i]=(byte)(value>>(i*8));}
    static byte[] packedInts(int... values) {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(int n:values) {byte[] encoded=new ProtoWire.Writer().varint(1,n).build();out.write(encoded,1,encoded.length-1);}
        return out.toByteArray();
    }
    static void rejects(Runnable action,String message) {
        try {action.run();throw new AssertionError(message);} catch(IllegalArgumentException expected) {}
    }
    static void textLimits() {
        MeshtasticProtocol mt=new MeshtasticProtocol();
        mt.sendChannel(0,"α".repeat(100));
        rejects(()->mt.sendChannel(0,"α".repeat(100)+"a"),"MT channel byte overflow");
        mt.nodeCommand("dm","!abcdef12","😀".repeat(50),0);
        rejects(()->mt.nodeCommand("dm","!abcdef12","😀".repeat(50)+"a",0),"MT DM byte overflow");
        MeshCoreProtocol mc=new MeshCoreProtocol();
        rejects(()->mc.sendChannel(0,"hello"),"MC must wait for own radio name");
        byte[] name="ΑΒ😀 ".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] self=new byte[58+name.length];self[0]=5;System.arraycopy(name,0,self,58,name.length);mc.receive(self,events);
        check(mc.textLimit(false)==128&&mc.textLimit(true)==138,"MC limits include UTF-8 name, timestamp, type and separator");
        mc.sendChannel(0,"a".repeat(128));mc.receive(new byte[]{0},events);
        rejects(()->mc.sendChannel(0,"a".repeat(129)),"MC channel must not exceed encrypted feedback budget");
        byte[] contact=new byte[148];contact[0]=3;Arrays.fill(contact,1,33,(byte)0xbb);mc.receive(contact,events);
        mc.nodeCommand("dm","bbbbbbbbbbbb","😀".repeat(34)+"aa",0);mc.expireRequest(mc.lastRequestId());
        rejects(()->mc.nodeCommand("dm","bbbbbbbbbbbb","😀".repeat(34)+"aaa",0),"MC DM byte overflow");
        rejects(()->mc.sendChannel(0,"a\0b"),"MC C-string truncation");
        byte[] longName=new byte[90];longName[0]=5;Arrays.fill(longName,58,90,(byte)'x');mc.receive(longName,events);
        check(mc.textLimit(false)==105,"Long sender names still impose a lower channel budget");
        deliveries.clear();
    }
    public static void main(String[] args) {
        textLimits();
        MeshtasticProtocol mt=new MeshtasticProtocol();
        mt.receive(new ProtoWire.Writer().bytes(3,new ProtoWire.Writer().varint(1,0x12345678L).build()).build(),events);
        byte[] sent=mt.sendChannel(0,"hello");long token=mt.lastRequestId();
        ProtoWire.Reader wrapper=new ProtoWire.Reader(sent);wrapper.next();ProtoWire.Reader mesh=new ProtoWire.Reader(wrapper.bytes());boolean wantAck=false;
        while(mesh.next()) {if(mesh.field==10) wantAck=mesh.number()==1;else mesh.skip();}
        check(wantAck,"Channel messages must request acknowledgments");
        mt.receive(new ProtoWire.Writer().bytes(11,new ProtoWire.Writer().varint(4,token).build()).build(),events);
        check(deliveries.get(0).equals(token+":accepted"),"Queue acceptance correlation");
        mt.receive(packet(0x12345678L,token,5,new byte[0]),events);
        check(deliveries.get(1).equals(token+":delivered_mesh"),"Broadcast confirmation is mesh-level");
        mt.nodeCommand("dm","!abcdef12","test",0);token=mt.lastRequestId();
        mt.receive(packet(0x12345678L,token,5,new byte[0]),events);
        check(deliveries.get(2).equals(token+":relayed"),"Local acknowledgment is not recipient delivery");
        mt.receive(packet(0xabcdef12L,token,5,new byte[0]),events);
        check(deliveries.get(3).equals(token+":delivered"),"Recipient acknowledgment");
        mt.receive(packet(0x12345678L,token,5,new ProtoWire.Writer().varint(3,9).build()),events);
        check(deliveries.get(4).contains("Duty-cycle"),"Delivery reason decoding");
        int count=deliveries.size(); mt.receive(packet(0x12345678L,0,5,new byte[0]),events);
        check(deliveries.size()==count,"Uncorrelated ack ignored");
        mt.nodeCommand("tr","!abcdef12","",0);long traceId=mt.lastRequestId();
        byte[] back=new byte[4];le(back,0,0xeeeeeeeeL);
        byte[] route=new ProtoWire.Writer().fixed32(1,0x11111111).bytes(2,packedInts(-4,16)).bytes(3,back).bytes(4,packedInts(8,-128)).build();
        mt.receive(packet(0xabcdef12L,traceId+1,70,route),events);check(traces.isEmpty(),"Stale trace ignored");
        mt.receive(packet(0xabcdef12L,traceId,70,route),events);
        TraceResult result=traces.get(0);
        check(result.forward.equals(List.of("!12345678","!11111111","!abcdef12")),"MT forward route");
        check(result.backward.equals(List.of("!abcdef12","!eeeeeeee","!12345678")),"MT return route");
        check(result.forwardSnr.equals(List.of(-4,16))&&result.backwardSnr.equals(List.of(8,-128)),"Signed packed SNR");
        mt.nodeCommand("tr","!abcdef12","",0);traceId=mt.lastRequestId();
        mt.receive(packet(0xabcdef12L,traceId,70,new byte[0]),events);
        check(traces.get(1).backward.isEmpty(),"Missing return data must not invent a direct route");

        MeshCoreProtocol mc=new MeshCoreProtocol();
        byte[] self=new byte[60];self[0]=5;Arrays.fill(self,4,36,(byte)0x99);mc.receive(self,events);
        byte[] contact=new byte[148];contact[0]=3;Arrays.fill(contact,1,33,(byte)0xbb);contact[33]=2;contact[35]=1;contact[36]=(byte)0xaa;
        mc.receive(contact,events);
        byte[] trace=mc.nodeCommand("trace-path","bbbbbbbbbbbb","",0);traceId=mc.lastRequestId();
        check(Arrays.equals(Arrays.copyOfRange(trace,10,trace.length),new byte[]{(byte)0xaa,(byte)0xbb,(byte)0xaa}),"MC round-trip path includes destination repeater and return");
        byte[] accepted=new byte[10];accepted[0]=6;le(accepted,2,traceId);mc.receive(accepted,events);
        byte[] reply=new byte[19];reply[0]=(byte)0x89;reply[2]=3;le(reply,4,traceId);
        System.arraycopy(trace,10,reply,12,3);reply[15]=8;reply[16]=-4;reply[17]=12;reply[18]=16;
        mc.receive(reply,events);result=traces.get(2);
        check(result.forward.size()==3&&result.backward.size()==3,"MC split outward/return");
        check(result.forwardSnr.equals(List.of(8,-4))&&result.backwardSnr.equals(List.of(12,16)),"MC signed SNR and final hop");
        byte[] discovery=mc.nodeCommand("tr","bbbbbbbbbbbb","",0);traceId=mc.lastRequestId();
        check(discovery.length==34&&discovery[0]==52&&discovery[1]==0,"MC receiver path discovery command");
        mc.receive(accepted,events);
        byte[] paths=new byte[]{(byte)0x8d,0,(byte)0xbb,(byte)0xbb,(byte)0xbb,(byte)0xbb,(byte)0xbb,(byte)0xbb,1,(byte)0xaa,65,0x11,0x22};
        mc.receive(Arrays.copyOf(paths,11),events);check(traces.size()==3,"Truncated route ignored");
        mc.receive(paths,events);result=traces.get(3);
        check(result.requestId==traceId&&result.forward.get(1).equals("aa")&&result.backward.get(1).equals("1122"),"MC distinct outward and return widths");
        check(result.forward.size()==3&&result.backward.size()==3&&result.forwardSnr.isEmpty(),"MC discovery endpoints without invented SNR");
        check(Arrays.equals(mc.advert("zero"),new byte[]{7,0}),"Zero hop advert command");mc.receive(new byte[]{0},events);
        check(Arrays.equals(mc.advert("flood"),new byte[]{7,1}),"Flood advert command");mc.receive(new byte[]{0},events);
        check(Arrays.equals(mc.advert("copy"),new byte[]{17}),"Self advert export command");mc.receive(new byte[]{11,1,2,(byte)255},events);
        check(exports.equals(List.of("meshcore://0102ff")),"Portable advert URI");
        mc.sendChannel(0,"test");token=mc.lastRequestId();
        try {mc.advert("zero");throw new AssertionError("Concurrent commands must not consume each other's OK");} catch(IllegalArgumentException expected) {}
        mc.receive(new byte[]{0},events);check(deliveries.get(deliveries.size()-1).equals(token+":sent_radio"),"MC channel acceptance");
        mc.nodeCommand("dm","bbbbbbbbbbbb","test",0);token=mc.lastRequestId();accepted[0]=6;le(accepted,2,42);mc.receive(accepted,events);
        byte[] ack=new byte[9];ack[0]=(byte)0x82;le(ack,1,42);mc.receive(ack,events);
        check(deliveries.get(deliveries.size()-1).equals(token+":delivered"),"MC DM acknowledgment correlation");
        System.out.println("Radio action regressions passed: acknowledgments, errors, traces, SNR, adverts, command isolation.");
    }
}
