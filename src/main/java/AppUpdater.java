package org.overmesh.android;

import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

final class AppUpdater {
    private static final String BASE = "https://raw.githubusercontent.com/Antonis512/SV8TTL-LoRa-Dash/main/updates/";
    private static final String APK = "SV8TTL-LoRa-Dash-debug.apk";
    private static final long MAX_APK_BYTES = 30L * 1024 * 1024;

    static final class Version {
        final int code;
        final String name, sha256;
        final long size;
        Version(int code, String name, String sha256, long size) {
            this.code=code; this.name=name; this.sha256=sha256; this.size=size;
        }
    }

    static Version check() throws Exception {
        HttpURLConnection connection=open(BASE+"latest.json?check="+System.currentTimeMillis());
        try(InputStream input=connection.getInputStream()) {
            if(connection.getResponseCode()!=200)throw new IllegalStateException("GitHub returned "+connection.getResponseCode());
            java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[2048];int count;
            while((count=input.read(buffer))!=-1) {
                if(data.size()+count>8192)throw new IllegalStateException("Update information is too large");
                data.write(buffer,0,count);
            }
            JSONObject json=new JSONObject(new String(data.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));
            int code=json.getInt("versionCode");
            String name=json.getString("versionName");
            String hash=json.getString("sha256").toLowerCase(java.util.Locale.ROOT);
            long size=json.getLong("size");
            if(code<1||name.length()<1||name.length()>32||!hash.matches("[0-9a-f]{64}")||size<1024||size>MAX_APK_BYTES)
                throw new IllegalStateException("Invalid update information");
            return new Version(code,name,hash,size);
        } finally {connection.disconnect();}
    }

    static File download(Version version, File cacheDir) throws Exception {
        File directory=new File(cacheDir,"updates");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IllegalStateException("Could not prepare update storage");
        File temporary=new File(directory,"update.part");
        File ready=new File(directory,APK);
        if(temporary.exists()&&!temporary.delete())throw new IllegalStateException("Could not replace partial update");
        HttpURLConnection connection=open(BASE+APK+"?v="+version.code);
        try(InputStream input=connection.getInputStream();FileOutputStream output=new FileOutputStream(temporary)) {
            if(connection.getResponseCode()!=200)throw new IllegalStateException("GitHub returned "+connection.getResponseCode());
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] buffer=new byte[32*1024]; int n;long count=0;
            while((n=input.read(buffer))!=-1) {
                count+=n;
                if(count>version.size||count>MAX_APK_BYTES)throw new IllegalStateException("Update size does not match");
                digest.update(buffer,0,n);output.write(buffer,0,n);
            }
            if(count!=version.size)throw new IllegalStateException("Update size does not match");
            StringBuilder actual=new StringBuilder(64);
            for(byte b:digest.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(!actual.toString().equals(version.sha256))throw new IllegalStateException("Update checksum does not match");
        } catch(Exception error) {temporary.delete();throw error;}
        finally {connection.disconnect();}
        if(ready.exists()&&!ready.delete())throw new IllegalStateException("Could not replace old update");
        if(!temporary.renameTo(ready))throw new IllegalStateException("Could not save update");
        return ready;
    }

    private static HttpURLConnection open(String address) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Accept","application/octet-stream, application/json");
        connection.setRequestProperty("User-Agent","SV8TTL-LoRa-Dash-Android");
        return connection;
    }
}
