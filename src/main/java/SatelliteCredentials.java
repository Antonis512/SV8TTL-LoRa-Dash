package org.overmesh.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Optional, device-bound Copernicus OAuth client credentials. Not part of App Backup. */
final class SatelliteCredentials {
    static final class Account {final String id,secret;Account(String id,String secret){this.id=id;this.secret=secret;}}
    private static final String ALIAS="sv8ttl-copernicus-oauth-v1";
    private final SharedPreferences storage;
    SatelliteCredentials(Context context){storage=context.getSharedPreferences("satellite-credentials",Context.MODE_PRIVATE);}
    private SecretKey key(boolean create)throws Exception {
        KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);
        SecretKey existing=(SecretKey)keys.getKey(ALIAS,null);if(existing!=null||!create)return existing;
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());return generator.generateKey();
    }
    synchronized void remember(String id,String secret)throws Exception {
        if(id==null||secret==null||id.trim().isEmpty()||secret.trim().isEmpty()||id.length()>1000||secret.length()>2000)
            throw new IllegalArgumentException("Enter both Copernicus credentials");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));
        byte[] plaintext=new JSONObject().put("id",id.trim()).put("secret",secret).toString().getBytes(StandardCharsets.UTF_8);
        String value=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+
                Base64.encodeToString(cipher.doFinal(plaintext),Base64.NO_WRAP);
        if(!storage.edit().putString("oauth",value).putBoolean("logged_off",false).commit())throw new IllegalStateException("Could not remember Copernicus account");
    }
    synchronized boolean loggedOff(){return storage.getBoolean("logged_off",false);}
    synchronized void setLoggedOff(boolean value){storage.edit().putBoolean("logged_off",value).commit();}
    synchronized Account load(){
        String value=storage.getString("oauth",null);if(value==null)return null;
        try{String[] parts=value.split(":",2);if(parts.length!=2)throw new IllegalArgumentException("Invalid saved credentials");
            SecretKey secretKey=key(false);if(secretKey==null)throw new IllegalStateException("Device key unavailable");
            byte[] iv=Base64.decode(parts[0],Base64.DEFAULT);if(iv.length!=12)throw new IllegalArgumentException("Invalid saved credentials");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,secretKey,new GCMParameterSpec(128,iv));
            JSONObject data=new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1],Base64.DEFAULT)),StandardCharsets.UTF_8));
            return new Account(data.getString("id"),data.getString("secret"));
        }catch(Exception error){storage.edit().remove("oauth").apply();return null;}
    }
    synchronized void forget(){
        storage.edit().clear().commit();
        try{KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);if(keys.containsAlias(ALIAS))keys.deleteEntry(ALIAS);}
        catch(Exception ignored){}
    }
}
