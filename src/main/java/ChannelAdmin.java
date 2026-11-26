package org.overmesh.android;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/** Local-radio channel transactions. Keep credentials out of UI state and logs. */
final class ChannelAdmin {
    final boolean meshcore;
    final Map<Integer,String> slots=new TreeMap<>();
    final Map<Integer,byte[]> secrets=new TreeMap<>();
    private final LongSupplier clock;
    private int stage,slot;
    private long deadline,requestId,ownNode;
    private byte[] key,queued;
    private String name;
    boolean stalled;
    private boolean removing;
    ChannelAdmin(boolean meshcore,LongSupplier clock){this.meshcore=meshcore;this.clock=clock;}
    boolean active(){return stage!=0;}
    boolean blocked(){return active()||stalled;}
    void ownNode(long id){ownNode=id;}
    void reset(){stage=0;stalled=false;queued=null;key=null;slots.clear();secrets.clear();}
    void observe(int index,String title){if(index>=0 && index<(meshcore?40:8))slots.put(index,title);}
    private static boolean zero(byte[] bytes){for(byte b:bytes)if(b!=0)return false;return true;}
    void observeMc(int index,String title,byte[] secret){secrets.put(index,secret.clone());observe(index,title.isEmpty()&&!zero(secret)?"\0":title);}
    int freeSlot(){for(Map.Entry<Integer,String> item:slots.entrySet())if(item.getValue().isEmpty() && (meshcore||item.getKey()!=0))return item.getKey();return -1;}
    static byte[] key(boolean mc,String mode,String name,String text) {
        try {
            if(mc && mode.equals("hashtag")){
                if(!name.startsWith("#")||name.length()<2)throw new IllegalArgumentException("Enter a channel name beginning with #");
                return Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8)),16);
            }
            if(!mc && mode.equals("default"))return new byte[]{1};
            if(!mc && mode.equals("none"))return new byte[0];
            if(!mode.equals("private"))throw new IllegalArgumentException("Choose a channel key type");
            byte[] result;
            if(mc){
                if(!text.matches("[0-9a-fA-F]{32}"))throw new IllegalArgumentException("MeshCore needs a 32-character hexadecimal key");
                result=new byte[16];for(int i=0;i<16;i++)result[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);
                if(zero(result))throw new IllegalArgumentException("Generate or enter a non-zero private key");
            } else {
                result=java.util.Base64.getDecoder().decode(text);
                if(result.length!=16&&result.length!=32)throw new IllegalArgumentException("Meshtastic needs a Base64 key containing 16 or 32 bytes");
            }
            return result;
        } catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
        catch(IllegalArgumentException invalid){throw new IllegalArgumentException("Invalid channel key. "+invalid.getMessage());}
    }
    byte[] start(String title,String mode,String secret) {
        if(blocked())throw new IllegalArgumentException("Wait for channel setup, or reconnect after a timeout");
        byte[] bytes=title.getBytes(StandardCharsets.UTF_8);
        if(title.isEmpty()||title.indexOf('\0')>=0||bytes.length>(meshcore?31:11))throw new IllegalArgumentException("Channel name must be 1–"+(meshcore?31:11)+" UTF-8 bytes");
        for(String existing:slots.values())if(existing.equals(title))throw new IllegalArgumentException("A channel with this name already exists on this radio");
        int available=freeSlot();if(available<0)throw new IllegalArgumentException("No reported empty channel slots. Reconnect to refresh the radio's channels.");
        if(!meshcore&&ownNode==0)throw new IllegalArgumentException("Wait for the radio identity");
        byte[] decoded=key(meshcore,mode,title,secret);
        removing=false;slot=available;name=title;key=decoded;stage=1;deadline=clock.getAsLong()+15000;
        return read();
    }
    byte[] remove(int index,String expectedName){
        if(blocked())throw new IllegalArgumentException("Wait for channel setup, or reconnect after a timeout");
        if(index<=0)throw new IllegalArgumentException("The primary/default channel cannot be removed here");
        if(!slots.containsKey(index)||slots.get(index).isEmpty()||!slots.get(index).equals(expectedName))throw new IllegalArgumentException("Channel changed. Refresh before removing it");
        if(!meshcore&&ownNode==0)throw new IllegalArgumentException("Wait for the radio identity");
        removing=true;slot=index;name=expectedName;key=meshcore?new byte[16]:new byte[0];stage=1;deadline=clock.getAsLong()+15000;return read();
    }
    byte[] next(){byte[] result=queued;queued=null;return result;}
    byte[] poll(RadioProtocol.Events events){
        if(active()&&clock.getAsLong()>=deadline){stalled=true;finish(events,"Channel setup unconfirmed. Reconnect to check the radio before trying again.");}
        return next();
    }
    private void finish(RadioProtocol.Events events,String message){stage=0;queued=null;key=null;events.channelSetupResult(message);}
    private byte[] admin(byte[] payload,boolean response){
        requestId=Integer.toUnsignedLong(new SecureRandom().nextInt());if(requestId==0)requestId=1;
        byte[] data=new ProtoWire.Writer().varint(1,6).bytes(2,payload).varint(3,response?1:0).build();
        return new ProtoWire.Writer().bytes(1,new ProtoWire.Writer().fixed32(2,ownNode).bytes(4,data).fixed32(6,requestId).varint(9,0).varint(10,1).build()).build();
    }
    private byte[] read(){return meshcore?new byte[]{31,(byte)slot}:admin(new ProtoWire.Writer().varint(1,slot+1).build(),true);}
    private void received(int index,String title,byte[] secret,int role,byte[] passkey,RadioProtocol.Events events){
        if(index!=slot)return;
        secrets.put(index,secret.clone());
        observe(index,role==0?"":title.isEmpty()?"\0":title);
        if(stage==1){
            if(removing?(role==0||!title.equals(name)||(!meshcore&&role==1)):role!=0){finish(events,"The empty slot is now occupied. Refresh and try again.");return;}
            if(removing)name="";
            stage=2;deadline=clock.getAsLong()+15000;
            if(meshcore){queued=new byte[50];queued[0]=32;queued[1]=(byte)slot;byte[] bytes=name.getBytes(StandardCharsets.UTF_8);System.arraycopy(bytes,0,queued,2,bytes.length);System.arraycopy(key,0,queued,34,16);}
            else {
                if(passkey.length==0){finish(events,"Radio did not provide an admin session key. Channel was not changed.");return;}
                byte[] settings=new ProtoWire.Writer().bytes(2,key).bytes(3,name.getBytes(StandardCharsets.UTF_8)).build();
                byte[] channel=new ProtoWire.Writer().varint(1,slot).bytes(2,settings).varint(3,removing?0:2).build();
                queued=admin(new ProtoWire.Writer().bytes(33,channel).bytes(101,passkey).build(),false);
            }
        } else if(stage==3){
            if(removing?role!=0:(role!=(meshcore?1:2)||!title.equals(name)||!Arrays.equals(secret,key))){finish(events,"Channel read-back did not match. Reconnect to check the radio.");return;}
            if(removing){slots.put(slot,"");events.channelRemoved(slot);}else events.channel(slot,title);finish(events,removing?"Channel removed and verified on the radio.":"Channel added and verified on the radio.");
        }
    }
    boolean receiveMc(byte[] packet,RadioProtocol.Events events){
        if(!active()||packet.length==0)return false;
        int type=packet[0]&255;
        if(type==1){finish(events,"Radio rejected channel setup (code "+(packet.length>1?(packet[1]&255):"unknown")+").");return true;}
        if(type==0&&stage==2){stage=3;deadline=clock.getAsLong()+15000;queued=read();return true;}
        if(type==18&&packet.length>=50&&(packet[1]&255)==slot){
            int end=2;while(end<34&&packet[end]!=0)end++;
            String title=new String(packet,2,end-2,StandardCharsets.UTF_8);
            byte[] secret=Arrays.copyOfRange(packet,34,50);
            received(slot,title,secret,title.isEmpty()&&zero(secret)?0:1,new byte[0],events);return true;
        }
        return false;
    }
    void receiveMt(long from,int port,long reply,byte[] body,RadioProtocol.Events events){
        if(!active()||from!=ownNode||reply!=requestId||body==null)return;
        if(port==5){
            int error=0;ProtoWire.Reader r=new ProtoWire.Reader(body);while(r.next()){if(r.field==3)error=(int)r.number();else r.skip();}
            if(error!=0){finish(events,"Radio rejected channel setup (code "+error+").");return;}
            if(stage==2){stage=3;deadline=clock.getAsLong()+15000;queued=read();}
        } else if(port==6){
            byte[] channel=null,passkey=new byte[0];ProtoWire.Reader r=new ProtoWire.Reader(body);
            while(r.next()){if(r.field==2)channel=r.bytes();else if(r.field==101)passkey=r.bytes();else r.skip();}
            if(channel==null)return;
            int index=0,role=0;String title="";byte[] secret=new byte[0];r=new ProtoWire.Reader(channel);
            while(r.next()){if(r.field==1)index=(int)r.number();else if(r.field==3)role=(int)r.number();else if(r.field==2){ProtoWire.Reader s=new ProtoWire.Reader(r.bytes());while(s.next()){if(s.field==2)secret=s.bytes();else if(s.field==3)title=new String(s.bytes(),StandardCharsets.UTF_8);else s.skip();}}else r.skip();}
            received(index,title,secret,role,passkey,events);
        }
    }
}
