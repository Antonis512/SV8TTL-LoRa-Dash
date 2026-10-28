package org.overmesh.android;

import java.lang.reflect.Proxy;
import java.util.*;

public final class RetryProtocolSmoke {
    static void check(boolean value,String why) {if(!value) throw new AssertionError(why);}
    static final class Rig {
        long now; final MeshCoreProtocol radio=new MeshCoreProtocol(()->now,()->1000);
        final List<String> states=new ArrayList<>();
        final RadioProtocol.Events events=(RadioProtocol.Events)Proxy.newProxyInstance(RadioProtocol.Events.class.getClassLoader(),new Class<?>[]{RadioProtocol.Events.class},(p,m,a)->{
            if(m.getName().equals("delivery")) states.add(a[0]+":"+a[1]); return null;
        });
        Rig(int version,boolean route) {
            radio.receive(new byte[]{13,(byte)version,0,1},events);
            byte[] self=new byte[60];self[0]=5;radio.receive(self,events);
            byte[] contact=new byte[148];contact[0]=3;Arrays.fill(contact,1,33,(byte)0xbb);contact[35]=(byte)(route?0:255);radio.receive(contact,events);
        }
        byte[] send() {return radio.nodeCommand("dm","bbbbbbbbbbbb","hello",0);}
        void accepted(int ack) {byte[] p=new byte[10];p[0]=6;le(p,2,ack);le(p,6,2000);radio.receive(p,events);}
        void ack(int ack) {byte[] p=new byte[9];p[0]=(byte)0x82;le(p,1,ack);radio.receive(p,events);}
        byte[] poll() {return radio.poll(events);}
        byte[] due() {now+=3000;return poll();}
        void ok() {radio.receive(new byte[]{0},events);}
    }
    static void le(byte[] p,int at,int value) {for(int i=0;i<4;i++)p[at+i]=(byte)(value>>(8*i));}
    static void budget(int version,boolean route,int direct,int count) {
        Rig r=new Rig(version,route);byte[] original=r.send();long token=r.radio.lastRequestId();
        for(int i=0;i<count;i++) {
            r.accepted(100+i);
            r.now+=2999;check(r.poll()==null,"Never retry before suggested timeout plus jitter");r.now++;
            byte[] next=r.poll();
            if(i==count-1) {check(next==null&&!r.radio.automaticDeliveryPending(token),"Budget exhausted");break;}
            if(i+1>=direct) {check(next!=null&&next[0]==13,"Reset route before every flood attempt");r.ok();next=r.poll();}
            check(next!=null&&next[0]==2&&(next[2]&255)==i+1,"Incremented attempt frame");
            check(Arrays.equals(Arrays.copyOfRange(original,3,original.length),Arrays.copyOfRange(next,3,next.length)),"Stable timestamp, peer and text");
            check(r.radio.lastRequestId()==token,"One logical message token");
        }
        check(r.states.get(r.states.size()-1).contains("after "+count+" attempts"),"Explicit unconfirmed exhaustion");
        r.ack(100);check(r.states.get(r.states.size()-1).endsWith(":delivered"),"Late first-attempt ACK still confirms");
        int size=r.states.size();r.ack(101);check(r.states.size()==size,"Sibling ACKs removed");
        r.now+=999999;check(r.poll()==null,"No resend after delivery");
    }
    public static void main(String[] args) {
        Rig split=new Rig(4,true);split.send();check(split.radio.splitCommandBusy(),"Split parts wait for command acceptance");split.accepted(123);
        check(!split.radio.splitCommandBusy()&&split.radio.busyForSend(),"Split parts do not wait for recipient ACK or automatic retries");
        split.radio.sendChannel(0,"next part");check(split.radio.splitCommandBusy(),"Next command serialized");split.ok();check(!split.radio.splitCommandBusy(),"Accepted channel does not wait for repeats");
        budget(3,true,2,3);budget(3,false,0,3);budget(4,true,4,8);budget(4,false,0,4);
        Rig configured=new Rig(4,true);configured.radio.setAttempts(1,1);configured.send();long configuredId=configured.radio.lastRequestId();configured.accepted(80);
        check(configured.due()[0]==13,"Configured single route attempt enters flood");configured.ok();check(configured.poll()[2]==1,"Second total attempt is flood");configured.accepted(81);check(configured.due()==null&&!configured.radio.automaticDeliveryPending(configuredId),"Configured 1+1 exhausted");
        Rig stopped=new Rig(4,true);stopped.send();long stoppedId=stopped.radio.lastRequestId();stopped.accepted(90);stopped.radio.stopFutureAttempts(stoppedId);
        check(stopped.radio.automaticDeliveryPending(stoppedId),"Stop retains current response window");stopped.ack(90);check(stopped.states.get(stopped.states.size()-1).endsWith(":delivered"),"ACK still succeeds after stopping future attempts");
        Rig resetting=new Rig(4,true);resetting.radio.setAttempts(1,2);resetting.send();long resetId=resetting.radio.lastRequestId();resetting.accepted(91);check(resetting.due()[0]==13,"Flood reset starts");resetting.radio.stopFutureAttempts(resetId);resetting.ok();check(resetting.poll()==null&&!resetting.radio.automaticDeliveryPending(resetId),"Stopping during reset prevents next transmission");
        Rig r=new Rig(4,true);r.send();r.accepted(42);r.ack(42);r.now=99999;check(r.poll()==null,"ACK cancels timer");
        r=new Rig(4,true);byte[] first=r.send();long firstId=r.radio.lastRequestId();r.accepted(42);byte[] second=r.send();long secondId=r.radio.lastRequestId();r.accepted(43);
        check(!Arrays.equals(Arrays.copyOfRange(first,3,7),Arrays.copyOfRange(second,3,7)),"Distinct logical sends use distinct timestamps");
        r.ack(42);check(!r.radio.automaticDeliveryPending(firstId)&&r.radio.automaticDeliveryPending(secondId),"ACK cancels only its own message");
        check(r.due()[2]==1&&r.radio.lastRequestId()==secondId,"Other message continues retrying");
        r=new Rig(3,true);r.send();r.accepted(42);r.due();r.accepted(43);byte[] reset=r.due();check(reset[0]==13,"Reset pending");r.ack(42);r.ok();check(r.poll()==null,"ACK during reset prevents send");
        r=new Rig(4,true);r.send();r.accepted(42);r.radio.advert("zero");check(r.due()==null,"Retry waits for other command");r.ok();check(r.poll()[0]==2,"Retry resumes after other command");
        r=new Rig(4,true);r.send();r.accepted(42);r.radio.cancelAll();r.now=99999;check(r.poll()==null,"Disconnect cancels retries");
        r=new Rig(4,true);r.send();long token=r.radio.lastRequestId();r.accepted(42);r.radio.expireRequest(token);r.now=99999;check(r.poll()==null,"User cancellation stops retries");
        r=new Rig(4,true);r.send();r.radio.receive(new byte[]{1,3},r.events);r.now=99999;check(r.poll()==null&&!r.radio.automaticDeliveryPending(r.radio.lastRequestId()),"Radio rejection stops retries");
        r=new Rig(4,true);r.send();r.now=15000;check(r.poll()==null,"No response does not blindly resend");
        try {r.radio.advert("zero");throw new AssertionError("Must reconnect after untagged response timeout");}catch(IllegalArgumentException expected) {}
        r=new Rig(4,true);r.send();firstId=r.radio.lastRequestId();r.accepted(42);r.send();secondId=r.radio.lastRequestId();r.now=15000;r.poll();
        check(!r.radio.automaticDeliveryPending(firstId)&&!r.radio.automaticDeliveryPending(secondId),"A stalled command resolves all waiting sends");
        r=new Rig(4,true);r.radio.nodeCommand("dm","bbbbbbbbbbbb","a".repeat(150),0);r.accepted(42);
        check(r.states.get(0).contains("1/8"),"Maximum composer length retains full retry budget");
        r=new Rig(4,true);r.radio.sendChannel(0,"channel");r.ok();r.now=999999;check(r.poll()==null,"MC channel is not automatically repeated");
        MeshtasticProtocol mt=new MeshtasticProtocol();mt.sendChannel(0,"hello");check(mt.poll(r.events)==null,"MT retransmissions remain firmware-owned");
        System.out.println("Retry tests passed: legacy/modern budgets, timing, route reset, stable identity, late ACK, cancellation, command serialization, errors and channel policy.");
    }
}
