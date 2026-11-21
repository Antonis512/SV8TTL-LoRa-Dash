package org.overmesh.android;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.function.LongSupplier;

/** Serialized node flags: favorites on MT/MC and ignore on MT. MC timeouts lock the session. */
final class FavoriteAdmin {
    private final boolean mc;
    private final LongSupplier clock;
    private int stage;
    private long ownNode, requestId, deadline;
    private String target;
    private boolean desired, stalled, ignore;
    private byte[] key, queued;
    FavoriteAdmin(boolean mc, LongSupplier clock) { this.mc=mc;this.clock=clock; }
    boolean active(){return stage!=0;}
    boolean blocked(){return active()||stalled;}
    void ownNode(long id){ownNode=id;}
    void reset(){stage=0;stalled=false;queued=null;key=null;}
    byte[] start(String id,boolean value,byte[] publicKey){
        if(blocked())throw new IllegalArgumentException("Wait for the current node update, or reconnect after its timeout");
        if(mc&&(publicKey==null||publicKey.length!=32))throw new IllegalArgumentException("Reload this contact from the radio first");
        if(!mc&&(ownNode==0||!id.matches("![0-9a-fA-F]{8}")))throw new IllegalArgumentException("Wait for the radio identity and node data");
        ignore=false;target=id;desired=value;key=publicKey==null?null:publicKey.clone();stage=1;deadline=clock.getAsLong()+15000;
        return mc?readContact():admin(new ProtoWire.Writer().varint(3,1).build(),true);
    }
    byte[] startIgnored(String id,boolean value){
        if(mc)throw new IllegalArgumentException("Ignore on radio requires Meshtastic");
        if(id.equals(String.format("!%08x",ownNode)))throw new IllegalArgumentException("Cannot ignore your own radio");
        byte[] frame=start(id,value,null);ignore=true;return frame;
    }
    private byte[] readContact(){byte[] frame=new byte[33];frame[0]=30;System.arraycopy(key,0,frame,1,32);return frame;}
    private byte[] admin(byte[] payload,boolean response){
        requestId=Integer.toUnsignedLong(new SecureRandom().nextInt());if(requestId==0)requestId=1;
        byte[] data=new ProtoWire.Writer().varint(1,6).bytes(2,payload).varint(3,response?1:0).build();
        return new ProtoWire.Writer().bytes(1,new ProtoWire.Writer().fixed32(2,ownNode).bytes(4,data).fixed32(6,requestId).varint(9,0).varint(10,1).build()).build();
    }
    byte[] next(){byte[] frame=queued;queued=null;return frame;}
    byte[] poll(RadioProtocol.Events events){
        if(active()&&clock.getAsLong()>=deadline){stalled=mc;finish(events,false,"Favorite update unconfirmed. Reconnect to check the radio.");}
        return next();
    }
    private void finish(RadioProtocol.Events events,boolean success,String message){
        stage=0;queued=null;
        if(ignore){
            if(success)events.ignored(target,desired);
            events.ignoreResult(target,desired,success,success?(desired?"Node ignored on radio.":"Node unignored on radio."):message.replace("Favorite","Ignore").replace("favorite","ignore"));
            return;
        }
        if(success)events.favorite(target,desired);
        events.favoriteResult(target,desired,success,message);
    }
    boolean receiveMc(byte[] frame,RadioProtocol.Events events){
        if(frame.length==0)return false;
        int type=frame[0]&255;
        if(stalled)return type==0||type==1||type==3;
        if(!active())return false;
        if(type==1){finish(events,false,"Radio rejected favorite update (code "+(frame.length>1?(frame[1]&255):"unknown")+").");return true;}
        if(type==0&&stage==2){stage=3;deadline=clock.getAsLong()+15000;queued=readContact();return true;}
        if(type!=3||frame.length<144||!Arrays.equals(key,Arrays.copyOfRange(frame,1,33)))return false;
        boolean value=(frame[34]&1)!=0;
        events.favorite(target,value);
        if(stage==1){
            if(value==desired){finish(events,true,"Favorite confirmed on radio.");return true;}
            // Omit lastmod: firmware assigns its own modification time. Preserve every other byte.
            queued=Arrays.copyOf(frame,144);queued[0]=9;
            queued[34]=(byte)((queued[34]&0xfe)|(desired?1:0));
            stage=2;deadline=clock.getAsLong()+15000;
        }else if(stage==3)finish(events,value==desired,value==desired?"Favorite saved on radio.":"Radio favorite read-back did not match. Reconnect to check it.");
        return true;
    }
    void receiveMt(long from,int port,long reply,byte[] body,RadioProtocol.Events events){
        if(!active()||from!=ownNode||reply!=requestId||body==null)return;
        ProtoWire.Reader r=new ProtoWire.Reader(body);
        if(port==5){
            int error=0;while(r.next()){if(r.field==3)error=(int)r.number();else r.skip();}
            if(error!=0)finish(events,false,"Radio rejected favorite update (code "+error+").");
            else if(stage==2)finish(events,true,"Favorite saved on radio.");
        }else if(port==6&&stage==1){
            byte[] passkey=null;boolean owner=false;
            while(r.next()){if(r.field==101)passkey=r.bytes();else if(r.field==4){r.bytes();owner=true;}else r.skip();}
            if(!owner)return;
            if(passkey==null||passkey.length==0){finish(events,false,"Radio did not provide an admin session key. Favorite was not changed.");return;}
            stage=2;deadline=clock.getAsLong()+15000;
            queued=admin(new ProtoWire.Writer().varint(ignore?(desired?47:48):(desired?39:40),Long.parseUnsignedLong(target.substring(1),16)).bytes(101,passkey).build(),false);
        }
    }
}
