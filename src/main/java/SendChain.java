package org.overmesh.android;

import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Persistent intent and results, independent of chat history and transport sessions. Main thread only. */
final class SendChain {
    final AtomicFile file;
    JSONObject data;
    SendChain(File directory) throws Exception {
        file=new AtomicFile(new File(directory,"send-chain.json"));
        try {data=new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));}
        catch(java.io.FileNotFoundException missing){data=empty();}
        if(data.optJSONArray("items")==null)throw new JSONException("Invalid saved sending chain");
        // Legacy single-destination splits must never reappear in Send to multiple.
        // Chat history remains intact; discard only the obsolete resume instructions.
        if(!data.optBoolean("multiple"))data=empty();
        data.put("paused",true).put("stepOnce",false);
        for(int i=0;i<items().length();i++) {
            JSONObject item=items().getJSONObject(i);
            if(item.optString("state").equals("active"))item.put("state","unconfirmed").put("detail","App interrupted; review before retrying");
        }
        save();
    }
    static JSONObject empty() throws JSONException {return new JSONObject().put("paused",true).put("concurrent",false).put("items",new JSONArray());}
    JSONArray items(){return data.optJSONArray("items");}
    boolean paused(){return data.optBoolean("paused",true);}
    boolean hasPending(){for(int i=0;i<items().length();i++)if("pending".equals(items().optJSONObject(i).optString("state")))return true;return false;}
    JSONObject previousFinished() {
        JSONObject previous=null;long order=-1;
        for(int i=0;i<items().length();i++) {
            JSONObject item=items().optJSONObject(i);String state=item.optString("state");
            long sent=item.optLong("sendOrder",i);
            if(item.optLong("messageId",-1)>0&&!state.equals("active")&&!state.equals("pending")&&sent>=order){previous=item;order=sent;}
        }return previous;
    }
    void pause() throws Exception {data.put("paused",true).put("stepOnce",false);save();}
    int cancelRemaining() throws Exception {
        int count=0;
        for(int i=0;i<items().length();i++) {
            JSONObject item=items().getJSONObject(i);
            if("pending".equals(item.optString("state"))) {
                item.put("state","cancelled").put("detail","Cancelled before sending");count++;
            }
        }
        pause();return count;
    }
    void cancelChain() throws Exception {
        pause();
        JSONObject previous=data;
        data=empty();
        try{save();}catch(Exception error){data=previous;throw error;}
    }
    void sendNext() throws Exception {
        if(!hasPending())return;
        for(int i=0;i<items().length();i++) {
            JSONObject item=items().getJSONObject(i);
            if(item.optString("state").equals("active")&&item.optString("radio").equals("mc")&&item.optString("peer").isEmpty()) {
                item.put("advanceRequested",true);break;
            }
        }
        data.put("stepOnce",false).put("paused",false);save();
    }
    void repeatPrevious(JSONObject previous) throws Exception {
        if(!previous.optString("state").equals("complete")) {
            retry(previous);
            previous.put("manualRepeat",true);
            data.put("stepOnce",true).put("paused",false);save();return;
        }
        JSONObject copy=new JSONObject(previous.toString());
        for(String key:new String[]{"messageId","token","deadline","observationMs","observationStarted","sendOrder","detail","defer","advanceRequested","attempts"})copy.remove(key);
        copy.put("state","pending").put("manualRepeat",true);
        JSONArray next=new JSONArray();boolean inserted=false;
        for(int i=0;i<items().length();i++) {
            JSONObject item=items().getJSONObject(i);
            if(!inserted&&item.optString("state").equals("pending")){next.put(copy);inserted=true;}
            next.put(item);
        }
        if(!inserted)next.put(copy);
        data.put("items",next).put("stepOnce",true).put("paused",false).put("completionNotified",false);save();
    }
    void retry(JSONObject item) throws JSONException {
        JSONArray attempts=item.optJSONArray("attempts");if(attempts==null)attempts=new JSONArray();
        attempts.put(new JSONObject().put("messageId",item.optLong("messageId")).put("state",item.optString("state")).put("detail",item.optString("detail")));
        item.put("attempts",attempts);
        for(String key:new String[]{"messageId","token","deadline","observationMs","observationStarted","defer","advanceRequested"})item.remove(key);
        item.put("state","pending").put("detail","Queued for resend");data.put("completionNotified",false);
    }
    boolean completedSuccessfully() {
        if(items().length()==0)return false;
        for(int i=0;i<items().length();i++)if(!"complete".equals(items().optJSONObject(i).optString("state")))return false;
        return true;
    }
    void starting(JSONObject item) throws JSONException {
        long order=data.optLong("sendOrder",items().length())+1;
        data.put("sendOrder",order);item.put("sendOrder",order);
        item.remove("manualRepeat");
        if(data.optBoolean("stepOnce"))data.put("stepOnce",false).put("paused",true);
    }
    void save() throws Exception {
        FileOutputStream out=null;
        try {out=file.startWrite();out.write(data.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(Exception error){if(out!=null)file.failWrite(out);throw error;}
    }
}
