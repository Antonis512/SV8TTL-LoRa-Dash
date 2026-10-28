package org.overmesh.android;

import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import android.content.ContextWrapper;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import org.json.JSONObject;
import org.json.JSONArray;

/** Device tests use a separate database and chain directory, never the user's history. */
public final class HistoryInstrumentation extends Instrumentation {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle report=new Bundle();ChatStore store=null;
        try {
            ContextWrapper context=new ContextWrapper(getTargetContext()){
                @Override public File getDatabasePath(String name){return super.getDatabasePath("regression-"+name);}
                @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory);}
                @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,android.database.DatabaseErrorHandler handler){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(),factory,handler);}
            };
            File dbFile=context.getDatabasePath("chat-history.db");dbFile.getParentFile().mkdirs();SQLiteDatabase.deleteDatabase(dbFile);
            store=new ChatStore(context);store.saveRadio("mc","radio-a","Test","");
            for(int i=0;i<250;i++)store.save("mc","radio-a",0,"sender","m"+i,"",1000+i,false,"","");
            long oldest=store.save("mc","radio-a",0,"sender","Imported old","",1,false,"","");
            long unknown=store.save("mc","radio-a",0,"sender","Unknown","",0,false,"","");
            store.setUnread(oldest,true);
            List<ChatStore.Message> page=store.page("mc","radio-a","",0,0);check(page.size()==100,"Initial page size");
            check(page.get(99).receivedAt==1249,"Timestamp order, not insertion ID");
            List<ChatStore.Message> second=store.page("mc","radio-a","",0,page.get(0).id);check(second.size()==100,"Second page");
            List<ChatStore.Message> third=store.page("mc","radio-a","",0,second.get(0).id);check(third.size()==52&&third.get(0).id==unknown,"Remaining page and unknown date");
            check(store.unreadConversations("mc","radio-a").containsKey("c:0"),"Unloaded unread messages counted");
            store.heard("mc","radio-a","peer",100);store.heard("mc","radio-a","peer",90);check(store.lastHeard("mc","radio-a","peer")==100,"Last Heard merge preserves newer value");
            StringWriter output=new StringWriter();HistoryBackup.export(output,store,"mc","radio-a",null);
            String backup=output.toString();HistoryBackup.ReaderSource source=()->new StringReader(backup);
            HistoryBackup.Headers headers=HistoryBackup.read(source,false,null,null);int[] read={0};
            HistoryBackup.read(source,false,headers,node->read[0]+=node.getJSONArray("messages").length());check(read[0]==252,"Streaming export includes unloaded records");
            check(headers.nodes.get(0).getJSONObject("lastHeard").getLong("peer")==100,"Backup includes heard timestamps");
            String legacy=backup.replaceFirst("\"version\":2","\"version\":1");HistoryBackup.read(()->new StringReader(legacy),false,null,null);
            SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
            try{store.save("mc","radio-a",0,"test","Must roll back","",5,false,"","");}finally{db.endTransaction();}
            check(store.load("mc","radio-a").size()==252,"Failed transaction rolls back");
            db.setVersion(10);store.close();store=new ChatStore(context);check(store.load("mc","radio-a").size()==252&&store.getReadableDatabase().getVersion()==12,"v10 migration preserves history");
            check(store.eraseOlderThan("mc","radio-a",1100)==101,"Deletion includes unloaded records");check(store.load("mc","radio-a").get(0).id==unknown,"Unknown dates retained");
            File directory=new File(getTargetContext().getCacheDir(),"chain-regression");directory.mkdirs();
            SendChain chain=new SendChain(directory);chain.data=SendChain.empty().put("multiple",true).put("paused",false).put("items",new JSONArray().put(new JSONObject().put("state","active")).put(new JSONObject().put("state","pending")));chain.save();
            chain=new SendChain(directory);check(chain.paused(),"Restored chain is paused");check(chain.items().getJSONObject(0).getString("state").equals("unconfirmed"),"Interrupted send not replayed");check(chain.items().getJSONObject(1).getString("state").equals("pending"),"Unsent item preserved");
            JSONObject previous=chain.items().getJSONObject(0);previous.put("messageId",42).put("token",123).put("part",1).put("parts",2).put("text","First part").put("radio","mc").put("nodeId","radio-a").put("peer","peer-a").put("sendOrder",1);
            check(chain.previousFinished()==previous,"Previous is the last finished transmission");
            chain.sendNext();check(!chain.paused()&&!chain.data.optBoolean("stepOnce"),"Send Next resumes automatic continuation");
            chain.starting(chain.items().getJSONObject(1));check(!chain.paused(),"Next dispatch must not pause the chain");
            previous.put("state","active").put("peer","");chain.sendNext();check(previous.optBoolean("advanceRequested"),"Send Next requests early channel observation completion");
            previous.remove("advanceRequested");previous.put("peer","peer-a");chain.sendNext();check(!previous.optBoolean("advanceRequested"),"Send Next must not bypass a DM response window");previous.put("state","unconfirmed");
            chain.repeatPrevious(previous);JSONObject repeat=chain.items().getJSONObject(0);
            check(chain.items().length()==2&&repeat==previous&&repeat.optBoolean("manualRepeat"),"Failed resend preserves chain and its original row");
            check(!repeat.has("messageId")&&!repeat.has("token")&&repeat.getJSONArray("attempts").getJSONObject(0).getLong("messageId")==42,"Resend cannot inherit old ACK and preserves attempt history");
            chain.starting(repeat);check(chain.paused(),"Explicit previous-part resend retains its pause behavior");
            check(repeat.getString("nodeId").equals("radio-a")&&repeat.getString("peer").equals("peer-a")&&repeat.getString("text").equals("First part"),"Resend preserves payload and original radio/destination");
            check(!chain.completedSuccessfully(),"Pending retry is not successful");
            repeat.put("state","complete");chain.items().getJSONObject(1).put("state","complete");
            check(chain.completedSuccessfully(),"Successful resend completes original chain despite failed earlier attempt");
            chain.items().getJSONObject(1).put("state","pending");
            JSONObject settings=new JSONObject().put("destination_set_test",new JSONObject().put("name","Friends").put("targets",new JSONArray().put(new JSONObject().put("nodeId","radio-a").put("peer","peer-a").put("radio","mc").put("channel",-1))).toString());
            StringWriter fullBackup=new StringWriter();HistoryBackup.export(fullBackup,store,null,null,settings);
            HistoryBackup.Headers fullHeaders=HistoryBackup.read(()->new StringReader(fullBackup.toString()),true,null,null);
            check(fullHeaders.settings.getString("destination_set_test").equals(settings.getString("destination_set_test")),"App backup preserves named destination sets and stable radio identities");
            chain.pause();check(!chain.data.optBoolean("stepOnce"),"Pause cancels a queued manual step");
            chain.sendNext();chain=new SendChain(directory);check(chain.paused()&&!chain.data.optBoolean("stepOnce"),"Restart never executes an armed manual send");chain.data.put("multiple",false);chain.save();chain=new SendChain(directory);check(chain.items().length()==0,"Legacy split resume instructions removed from multi-send");chain.file.delete();
            int historyBeforeCancel=store.load("mc","radio-a").size();
            chain.data=SendChain.empty().put("multiple",true).put("paused",false).put("stepOnce",true)
                .put("items",new JSONArray().put(new JSONObject().put("state","active")).put(new JSONObject().put("state","pending")));
            chain.save();
            check(chain.cancelRemaining()==1&&chain.paused()&&!chain.hasPending(),"Cancel remaining stops queued dispatch");
            check(chain.items().getJSONObject(0).getString("state").equals("active"),"Cancel remaining preserves the active radio transmission");
            check(chain.items().getJSONObject(1).getString("state").equals("cancelled"),"Queued message is visibly cancelled");
            check(chain.cancelRemaining()==0,"Repeated cancellation does not count completed cancellations");
            chain=new SendChain(directory);check(chain.items().getJSONObject(1).getString("state").equals("cancelled"),"Cancelled messages stay cancelled after restart");
            chain.cancelChain();
            check(chain.paused()&&!chain.hasPending()&&chain.items().length()==0,"Cancel Chain empties active and pending queue entries");
            chain=new SendChain(directory);check(chain.items().length()==0&&!chain.data.optBoolean("stepOnce"),"Cancelled chain stays empty after restart");
            check(store.load("mc","radio-a").size()==historyBeforeCancel,"Cancel Chain preserves history");chain.file.delete();
            report.putString("stream","History paging, migration, retention, streaming backup and chain restart tests passed\n");finish(Activity.RESULT_OK,report);
        }catch(Throwable error){report.putString("stream",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,report);}
        finally{if(store!=null)store.close();}
    }
}
