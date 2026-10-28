package org.overmesh.android;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Matches received flood copies to our exact encrypted channel payload, never to a channel hash alone. */
final class MeshCoreRepeatTracker {
    static final class Observation {
        final String[] path; final int snr,rssi; final byte[] payload;
        Observation(String[] path,int snr,int rssi,byte[] payload) {this.path=path;this.snr=snr;this.rssi=rssi;this.payload=payload;}
    }
    static Observation observe(byte[] log) {
        if(log.length<6||(log[0]&255)!=0x88) return null;
        int header=log[3]&255,route=header&3;
        if((header>>>6)!=0||((header>>>2)&15)!=5||(route!=0&&route!=1)) return null;
        int at=route==0?8:4;
        if(at>=log.length) return null;
        int encoded=log[at++]&255,width=(encoded>>>6)+1,count=encoded&63;
        if(width>3||count*width>64||at+count*width+3>=log.length) return null;
        String[] path=new String[count];
        for(int i=0;i<count;i++) {
            StringBuilder hash=new StringBuilder();
            for(int j=0;j<width;j++) hash.append(String.format("%02x",log[at++]&255));
            path[i]=hash.toString();
        }
        return new Observation(path,log[1],log[2],Arrays.copyOfRange(log,at,log.length));
    }
    static byte[] decode(Observation observation,byte[] key) {
        if(observation==null||key==null||key.length!=16) return null;
        byte[] payload=observation.payload;
        if(payload.length<19||(payload.length-3)%16!=0) return null;
        try {
            if(payload[0]!=MessageDigest.getInstance("SHA-256").digest(key)[0]) return null;
            byte[] encrypted=Arrays.copyOfRange(payload,3,payload.length);
            Mac hmac=Mac.getInstance("HmacSHA256");hmac.init(new SecretKeySpec(Arrays.copyOf(key,32),"HmacSHA256"));
            byte[] mac=hmac.doFinal(encrypted);
            if(!MessageDigest.isEqual(Arrays.copyOf(mac,2),Arrays.copyOfRange(payload,1,3))) return null;
            Cipher cipher=Cipher.getInstance("AES/ECB/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"));
            byte[] plain=cipher.doFinal(encrypted);
            if(plain[4]!=0) return null;
            int end=plain.length;while(end>5&&plain[end-1]==0) end--;
            return Arrays.copyOf(plain,end);
        } catch(GeneralSecurityException unavailable) {return null;}
    }
    private final Map<Long,byte[]> sent=new LinkedHashMap<>();
    boolean contains(long id) {return sent.containsKey(id);}
    void clear() {sent.clear();}
    void remember(long id,byte[] command,byte[] name,byte[] key) {
        if(name==null||key==null||key.length!=16) return;
        try {
            byte[] text=new byte[5+name.length+2+command.length-7];
            System.arraycopy(command,3,text,0,4);
            System.arraycopy(name,0,text,5,name.length);
            text[5+name.length]=':';text[6+name.length]=' ';
            System.arraycopy(command,7,text,7+name.length,command.length-7);
            byte[] padded=Arrays.copyOf(text,(text.length+15)/16*16);
            Cipher aes=Cipher.getInstance("AES/ECB/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"));
            byte[] encrypted=aes.doFinal(padded);
            Mac hmac=Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(Arrays.copyOf(key,32),"HmacSHA256"));
            byte[] mac=hmac.doFinal(encrypted),payload=new byte[3+encrypted.length];
            payload[0]=MessageDigest.getInstance("SHA-256").digest(key)[0];
            payload[1]=mac[0];payload[2]=mac[1];
            System.arraycopy(encrypted,0,payload,3,encrypted.length);
            if(sent.size()>=256) sent.remove(sent.keySet().iterator().next());
            sent.put(id,payload);
        } catch(GeneralSecurityException unavailable) { /* Display unavailable instead of inventing a zero count. */ }
    }
    long match(byte[] log) {
        if(log.length<6 || (log[0]&255)!=0x88) return 0;
        int header=log[3]&255,route=header&3;
        if((header>>>6)!=0 || ((header>>>2)&15)!=5 || (route!=0&&route!=1)) return 0;
        int pathAt=route==0?8:4;
        if(pathAt>=log.length) return 0;
        int encoded=log[pathAt]&255,width=(encoded>>>6)+1,hops=encoded&63;
        if(width>3||hops==0||hops*width>64) return 0;
        int payloadAt=pathAt+1+hops*width;
        if(payloadAt>=log.length) return 0;
        byte[] payload=Arrays.copyOfRange(log,payloadAt,log.length);
        long found=0;
        for(Map.Entry<Long,byte[]> item:sent.entrySet()) if(Arrays.equals(payload,item.getValue())) {
            if(found!=0) return 0; // Ambiguous logical sends must not receive an invented count.
            found=item.getKey();
        }
        return found;
    }
}
