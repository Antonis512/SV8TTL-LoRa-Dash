package org.overmesh.android;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;

public final class RepeatProtocolSmoke {
    static byte[] hex(String s) {return java.util.HexFormat.of().parseHex(s);}
    static void check(boolean b,String reason) {if(!b) throw new AssertionError(reason);}
    static byte[] log(int route,int encoded,byte[] path,byte[] payload) {
        int transport=route==0?4:0;
        byte[] out=new byte[5+transport+path.length+payload.length];out[0]=(byte)0x88;out[1]=8;out[2]=-90;out[3]=(byte)(20+route);out[4+transport]=(byte)encoded;
        System.arraycopy(path,0,out,5+transport,path.length);System.arraycopy(payload,0,out,5+transport+path.length,payload.length);return out;
    }
    public static void main(String[] args) {
        byte[] key=hex("000102030405060708090a0b0c0d0e0f");
        byte[] command=hex("0300000403020168656c6c6f");
        // Independent Node.js crypto vector: timestamp 0x01020304, Alice: hello, AES128 ECB + truncated HMAC-SHA256.
        byte[] payload=hex("be6239b0a064a88955ef480b964659bd8cfe406c0bdf9ac27cd83cc7396849eea7bfc2");
        MeshCoreRepeatTracker tracker=new MeshCoreRepeatTracker();tracker.remember(7,command,"Alice".getBytes(StandardCharsets.UTF_8),key);
        byte[] raw=log(1,1,hex("aa"),payload);
        MeshCoreRepeatTracker.Observation observation=MeshCoreRepeatTracker.observe(raw);
        check(Arrays.equals(observation.path,new String[]{"aa"})&&observation.snr==8&&observation.rssi==-90,"Capture received path and signal");
        byte[] decoded=MeshCoreRepeatTracker.decode(observation,key);
        check(new String(decoded,5,decoded.length-5,StandardCharsets.UTF_8).equals("Alice: hello"),"Authenticate and decode incoming channel text");
        check(MeshCoreRepeatTracker.decode(observation,new byte[16])==null,"Never associate a foreign channel path");
        check(tracker.match(raw)==7,"Match exact outgoing encrypted payload");
        check(tracker.match(log(0,130,hex("aabbccddeeff"),payload))==7,"Scoped flood and 3-byte hashes");
        check(tracker.match(log(1,65,hex("aabb"),payload))==7,"2-byte path hash");
        check(tracker.match(log(1,0,new byte[0],payload))==0,"Original zero-hop reception is not a repeater");
        check(tracker.match(log(2,1,hex("aa"),payload))==0,"Direct packets are not channel repeats");
        byte[] damaged=raw.clone();damaged[damaged.length-1]^=1;check(tracker.match(damaged)==0,"Same channel hash is insufficient");
        damaged=raw.clone();damaged[3]|=64;check(tracker.match(damaged)==0,"Unknown packet version ignored");
        for(int i=0;i<raw.length;i++)check(tracker.match(Arrays.copyOf(raw,i))==0,"Truncated frame ignored");
        check(tracker.match(log(1,193,hex("aabbccdd"),payload))==0,"Reserved hash width ignored");
        tracker.remember(8,command,"Alice".getBytes(StandardCharsets.UTF_8),key);check(tracker.match(raw)==0,"Ambiguous sends not guessed");
        tracker.clear();check(tracker.match(raw)==0,"Reconnect clears matching state");
        List<Long> heard=new ArrayList<>();
        RadioProtocol.Events events=(RadioProtocol.Events)Proxy.newProxyInstance(RadioProtocol.Events.class.getClassLoader(),new Class<?>[]{RadioProtocol.Events.class},(p,m,a)->{if(m.getName().equals("heardRepeat"))heard.add((Long)a[0]);return null;});
        MeshCoreProtocol mc=new MeshCoreProtocol();byte[] self=new byte[63];self[0]=5;System.arraycopy("Alice".getBytes(StandardCharsets.UTF_8),0,self,58,5);mc.receive(self,events);
        byte[] channel=new byte[50];channel[0]=18;channel[2]='P';System.arraycopy(key,0,channel,34,16);mc.receive(channel,events);
        byte[] sent=mc.sendChannel(0,"hello");long id=mc.lastRequestId();check(mc.tracksRepeats(id),"Key/name loaded without exposing them in UI");
        MeshCoreRepeatTracker fixture=new MeshCoreRepeatTracker();fixture.remember(id,sent,"Alice".getBytes(StandardCharsets.UTF_8),key);
        // Fixture extraction is limited to the test: production does not expose encrypted matching material.
        try {
            java.lang.reflect.Field f=MeshCoreRepeatTracker.class.getDeclaredField("sent");f.setAccessible(true);
            byte[] actual=(byte[])((Map<?,?>)f.get(fixture)).get(id);
            byte[] repeat=log(1,1,hex("aa"),actual);mc.receive(repeat,events);mc.receive(repeat,events);
            check(heard.equals(List.of(id,id)),"Count repeat receptions, not unique paths or delivery ACKs");
            mc.cancelAll();mc.receive(repeat,events);check(heard.size()==2,"Old connection repeats ignored");
            // Reproduce the reported 17-byte name / 138-byte text overflow.
            byte[] reportedName="MC-Device123456789".getBytes(StandardCharsets.UTF_8);
            byte[] oldCommand=new byte[7+138];oldCommand[0]=3;Arrays.fill(oldCommand,7,oldCommand.length,(byte)'a');
            fixture.remember(99,oldCommand,reportedName,key);
            byte[] oldPayload=(byte[])((Map<?,?>)f.get(fixture)).get(99L);
            check(log(1,1,hex("aa"),oldPayload).length>176,"Old 138-byte part cannot return through companion RX log");
            for(int nameLength=0;nameLength<=32;nameLength++) {
                byte[] sender=new byte[nameLength];Arrays.fill(sender,(byte)'x');
                byte[] identity=new byte[58+nameLength];identity[0]=5;System.arraycopy(sender,0,identity,58,nameLength);
                mc.receive(identity,events);
                String original="α😀Test ".repeat(70);
                List<String> parts=MessageChunks.split(original,mc.textLimit(false));
                check(String.join("",parts).equals(original.trim()),"Splitting preserves Unicode text");
                for(String part:parts) {
                    byte[] outgoing=mc.sendChannel(0,part);long token=mc.lastRequestId();
                    fixture.remember(token,outgoing,sender,key);
                    byte[] encrypted=(byte[])((Map<?,?>)f.get(fixture)).get(token);
                    byte[] scoped=log(0,74,new byte[20],encrypted); // ten 2-byte hashes
                    check(scoped.length<=176,"Scoped repeat must fit companion frame at every name length");
                    check(fixture.match(scoped)==token,"Maximum-size split part still matches its repeat");
                    mc.receive(new byte[]{0},events);
                }
            }
        } catch(ReflectiveOperationException e) {throw new AssertionError(e);}
        System.out.println("Heard-repeat tests passed: independent cipher vector, exact payload matching, path widths, scoped floods, malformed/foreign frames, lifecycle and protocol callback.");
    }
}
