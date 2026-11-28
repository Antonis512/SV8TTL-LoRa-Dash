package org.overmesh.android;
import android.content.Context;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** UI-only translation. Never use for protocol values, identifiers or stored user content. */
final class AppLanguage {
    private static JSONObject greek;
    static String language(Context c){return c.getSharedPreferences("MainActivity",0).getString("app_language",c.getSharedPreferences("MainActivity",0).getString("member_language","en"));}
    static synchronized String text(Context c,String source){
        if(source==null||!"el".equals(language(c)))return source;
        try{
            if(greek==null)try(InputStream in=c.getAssets().open("greek.json")){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);greek=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}
            String key=source.trim();if(key.isEmpty()||!greek.has(key))return source;
            int start=source.indexOf(key);return source.substring(0,start)+greek.getString(key)+source.substring(start+key.length());
        }catch(Exception e){return source;}
    }
}
