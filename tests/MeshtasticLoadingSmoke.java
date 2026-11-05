package org.overmesh.android;
import java.lang.reflect.Proxy;
import java.util.*;

/** Deterministic PhoneAPI handshake: no radio or wall-clock sleeps. */
public final class MeshtasticLoadingSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static long field(byte[] bytes,int field){
        if(bytes==null)return -1;
        ProtoWire.Reader r=new ProtoWire.Reader(bytes);
        while(r.next()){if(r.field==field)return r.number();r.skip();}return -1;
    }
    static byte[] complete(long id){return new ProtoWire.Writer().varint(7,id).build();}
    static byte[] own(){return new ProtoWire.Writer().bytes(3,new ProtoWire.Writer().varint(1,123).build()).build();}
    static byte[] node(int id){return new ProtoWire.Writer().bytes(4,new ProtoWire.Writer().varint(1,id).build()).build();}
    public static void main(String[] args){
        long[] clock={0};int[] done={0},failed={0},count={0};
        RadioProtocol.Events events=(RadioProtocol.Events)Proxy.newProxyInstance(RadioProtocol.Events.class.getClassLoader(),new Class[]{RadioProtocol.Events.class},(p,m,v)->{
            if(m.getName().equals("syncComplete"))done[0]++;
            if(m.getName().equals("syncFailed"))failed[0]++;
            if(m.getName().equals("loadingProgress"))count[0]=(int)v[1];
            return null;
        });
        MeshtasticProtocol mt=new MeshtasticProtocol(()->clock[0]);
        mt.receive(complete(MeshtasticProtocol.CONFIG_ONLY),events);check(done[0]==0,"Unsolicited completion ignored");
        check(field(mt.initialCommand(),3)==69420,"Config-only selector");
        mt.receive(complete(69420),events);check(mt.poll(events)==null,"Identity required before stage transition");
        mt.receive(own(),events);mt.receive(node(9),events);
        mt.receive(complete(999),events);check(done[0]==0&&mt.poll(events)==null,"Mismatched completion ignored");
        mt.receive(complete(69421),events);check(mt.poll(events)==null,"Out-of-order node completion ignored");
        mt.receive(complete(69420),events);mt.receive(complete(69420),events);
        clock[0]=99;check(mt.poll(events)==null,"Inter-stage settle");
        clock[0]=100;byte[] heartbeat=mt.poll(events);check(heartbeat!=null&&heartbeat[0]==58,"Inter-stage heartbeat uses field 7");
        clock[0]=199;check(mt.poll(events)==null,"Heartbeat settle before requesting nodes");
        clock[0]=200;check(field(mt.poll(events),3)==69421,"Node-only selector sent once");check(mt.poll(events)==null,"No overlapping node request");
        for(int i=1;i<=1500;i++){clock[0]+=500;mt.receive(node(i),events);mt.poll(events);}
        // The total cap intentionally bounds even a continuous dump; use a separate normal run below.
        check(failed[0]==1&&done[0]==0,"Absolute stage deadline bounds continuous traffic");
        mt.receive(complete(69421),events);check(done[0]==0,"Late completion after timeout ignored");
        clock[0]=0;mt=new MeshtasticProtocol(()->clock[0]);mt.initialCommand();mt.receive(own(),events);mt.receive(node(9),events);mt.receive(complete(69420),events);
        clock[0]=100;mt.poll(events);clock[0]=200;mt.poll(events);
        for(int i=1;i<=1500;i++){clock[0]+=100;mt.receive(node(i),events);mt.poll(events);}
        check(count[0]==1500&&failed[0]==1,"Large node dump progresses beyond a single timeout window; duplicate early node counted once");
        mt.receive(complete(69420),events);check(done[0]==0,"Stage 1 duplicate cannot complete stage 2");
        mt.receive(complete(69421),events);mt.receive(complete(69421),events);check(done[0]==1,"Node completion delivered once");
        clock[0]+=1000000;mt.poll(events);check(failed[0]==1,"Completed transfer has no active radio watchdog");
        mt=new MeshtasticProtocol(()->clock[0]);mt.initialCommand();mt.receive(own(),events);
        for(int i=0;i<4;i++){clock[0]+=10000;mt.receive(new ProtoWire.Writer().bytes(2,new byte[0]).build(),events);mt.poll(events);}
        check(failed[0]==2,"Unrelated mesh traffic cannot keep config loading alive");
        mt=new MeshtasticProtocol(()->clock[0]);mt.initialCommand();mt.receive(own(),events);mt.receive(complete(69420),events);
        clock[0]+=100;mt.poll(events);clock[0]+=100;mt.poll(events);
        for(int i=0;i<7;i++){
            clock[0]+=10000;
            mt.receive(new ProtoWire.Writer().bytes(2,new byte[0]).build(),events);
            mt.receive(new ProtoWire.Writer().bytes(4,new byte[0]).build(),events);
            mt.receive(new ProtoWire.Writer().bytes(5,new byte[0]).build(),events);
            mt.poll(events);
        }
        check(failed[0]==3,"Mesh traffic, invalid nodes and config packets cannot extend the node stage");
        mt=new MeshtasticProtocol(()->clock[0]);mt.initialCommand();mt.cancelAll();mt.receive(own(),events);mt.receive(complete(69420),events);clock[0]+=100000;check(mt.poll(events)==null&&done[0]==1&&failed[0]==3,"Cancelled session cannot complete or time out again");
        System.out.println("MT handshake checks passed: two stages, pacing, early/1500 nodes, IDs, duplicate completion, deadlines, unrelated traffic and cancellation");
    }
}
