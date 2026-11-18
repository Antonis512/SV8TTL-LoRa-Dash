package org.overmesh.android;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class UsageProtocolSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static byte[] stats(int tx,int rx){
        byte[] packet=new byte[14];packet[0]=24;packet[1]=1;
        for(int i=0;i<4;i++){packet[6+i]=(byte)(tx>>>(i*8));packet[10+i]=(byte)(rx>>>(i*8));}
        return packet;
    }
    public static void main(String[] args){
        long[] time={0};List<String> samples=new ArrayList<>();
        RadioProtocol.Events events=(RadioProtocol.Events)Proxy.newProxyInstance(RadioProtocol.Events.class.getClassLoader(),new Class[]{RadioProtocol.Events.class},
            (proxy,method,values)->{if(method.getName().equals("usageSample"))samples.add(values[0]+"/"+values[1]);return null;});
        MeshCoreProtocol protocol=new MeshCoreProtocol(()->time[0],()->1000);
        protocol.receive(stats(100,200),events);
        check(samples.get(0).equals("null/null"),"First report must not invent utilization");
        time[0]+=60000;protocol.receive(stats(106,212),events);
        check(samples.get(1).equals("10.0/20.0"),"TX and RX percentages use elapsed seconds");
        time[0]+=60000;protocol.receive(stats(106,212),events);
        check(samples.get(2).equals("0.0/0.0"),"True idle reports retain zero");
        time[0]+=180000;protocol.receive(stats(112,224),events);
        check(samples.get(3).equals("null/null"),"Missed reports create a gap");
        time[0]+=60000;protocol.receive(stats(1,2),events);
        check(samples.get(4).equals("null/null"),"Counter reset creates a gap");
        time[0]+=60000;protocol.receive(stats(7,14),events);
        check(samples.get(5).equals("10.0/20.0"),"Rate resumes after reset");
        protocol.initialCommand();time[0]+=60000;protocol.receive(stats(13,26),events);
        check(samples.get(6).equals("null/null"),"Reconnect discards old baseline");
        System.out.println("MeshCore utilization checks passed");
    }
}
