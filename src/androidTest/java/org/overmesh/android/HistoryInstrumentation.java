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
            db.execSQL("DROP TABLE contact_items");db.execSQL("DROP TABLE map_alerts");db.setVersion(16);store.close();store=new ChatStore(context);check(store.load("mc","radio-a").size()==252&&store.getReadableDatabase().getVersion()==19,"v16 migration preserves history and creates contacts");
            store.saveSignal("mt-a","peer-a","{\"snr\":0}");store.saveSignal("mt-b","peer-a","{\"rssi\":-102}");
            store.close();store=new ChatStore(context);
            check(store.signals("mt-a","peer-a").length()==1&&store.signals("mt-a","peer-a").getJSONObject(0).getDouble("snr")==0,"Signal history survives reopen and retains zero");
            check(store.signals("mt-a","peer-b").length()==0,"Signal history isolates remote peers");
            for(int i=0;i<1005;i++)store.saveSignal("mt-a","peer-a","{\"time\":"+i+"}");
            check(store.signals("mt-a","peer-a").length()==1000&&store.signals("mt-a","peer-a").getJSONObject(0).getInt("time")==1004,"Bounded signal history retains newest rows");
            store.clearSignals("mt-a","peer-a");check(store.signals("mt-b","peer-a").length()==1,"Clear isolates receiver");
            store.deleteRadio("mt","mt-b");check(store.signals("mt-b","peer-a").length()==0,"Removing radio removes signal history");
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
            JSONObject person=new JSONObject().put("id","person-roundtrip").put("kind","contact").put("name","Νίκος").put("edited",100)
                .put("photo","data:image/jpeg;base64,/9j/2Q==").put("favorite",true)
                .put("categories",new JSONArray().put("category-club"))
                .put("nodes",new JSONArray().put(new JSONObject().put("radio","mc").put("id","peer").put("assignmentRole","Owner")))
                .put("phones",new JSONArray().put(new JSONObject().put("label","Mobile").put("value","+306900000000")))
                .put("notes",new JSONArray().put(new JSONObject().put("date",50).put("text","Private note")))
                .put("custom",new JSONArray().put(new JSONObject().put("label","Workshop").put("value","Volos").put("type","text")));
            JSONObject category=new JSONObject().put("id","category-club").put("kind","category").put("title","Club").put("edited",100);
            ContactsStore.merge(store,new JSONArray().put(person).put(category),false);
            StringWriter fullBackup=new StringWriter();HistoryBackup.export(fullBackup,store,null,null,settings);
            HistoryBackup.Headers fullHeaders=HistoryBackup.read(()->new StringReader(fullBackup.toString()),true,null,null);
            check(fullHeaders.settings.getString("destination_set_test").equals(settings.getString("destination_set_test")),"App backup preserves named destination sets and stable radio identities");
            check(fullHeaders.contacts.length()==2,"Full App Backup contains contacts and categories");
            store.getWritableDatabase().delete("contact_items",null,null);ContactsStore.merge(store,fullHeaders.contacts,true);
            JSONObject restored=null;JSONArray restoredItems=ContactsStore.all(store);for(int i=0;i<restoredItems.length();i++)if(restoredItems.getJSONObject(i).getString("id").equals(person.getString("id")))restored=restoredItems.getJSONObject(i);
            check(restored!=null&&restored.getString("photo").equals(person.getString("photo")),"Embedded photo survives backup/restore");
            check(restored.getJSONArray("nodes").length()==1&&restored.getJSONArray("notes").getJSONObject(0).getString("text").equals("Private note"),"Assignments and private notes survive backup/restore");
            check(restored.getJSONArray("custom").getJSONObject(0).getString("value").equals("Volos")&&restored.getJSONArray("categories").getString(0).equals("category-club"),"Custom fields and categories survive backup/restore");
            ContactsStore.merge(store,new JSONArray().put(new JSONObject(person.toString()).put("name","Newer").put("edited",200)),false);
            ContactsStore.merge(store,fullHeaders.contacts,true);check(ContactsStore.all(store).getJSONObject(0).getString("name").equals("Newer"),"Older backup does not overwrite newer contact");
            JSONObject oldFull=new JSONObject(fullBackup.toString());oldFull.put("version",4);oldFull.remove("contacts");check(HistoryBackup.read(()->new StringReader(oldFull.toString()),true,null,null).contacts==null,"Old full backups remain readable");
            boolean invalidRejected=false;try{ContactsStore.merge(store,new JSONArray().put(new JSONObject(person.toString()).put("id","should-not-save")).put(new JSONObject(person.toString()).put("id","invalid").put("phones",new JSONArray().put(42))),false);}catch(Exception expected){invalidRejected=true;}
            check(invalidRejected&&ContactsStore.all(store).length()==2,"Malformed contact batches are rejected atomically");
            ContactsStore.forgetNode(store,"mc","peer");check(ContactsStore.all(store).getJSONObject(0).getJSONArray("nodes").length()==0&&ContactsStore.all(store).getJSONObject(0).getJSONArray("phones").length()==1,"Forget Node unassigns without deleting the person");
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
            JSONArray boundedItems=new JSONArray();
            for(int destination=0;destination<5;destination++)for(int part=1;part<=3;part++)
                boundedItems.put(new JSONObject().put("radio","mt").put("nodeId","receiver").put("peer","peer-"+destination).put("channel",-1).put("parts",3).put("part",part).put("state","pending"));
            chain.data=SendChain.empty().put("multiple",true).put("items",boundedItems);chain.validateLimits();chain.save();
            chain=new SendChain(directory);chain.validateLimits();check(chain.items().length()==15,"Five destinations with three parts survive restart");
            chain.items().getJSONObject(0).put("parts",4);
            boolean rejected=false;try{chain.validateLimits();}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Restored four-part chain blocked");
            chain.items().getJSONObject(0).put("parts",3);
            chain.items().put(new JSONObject().put("radio","mt").put("nodeId","receiver").put("peer","sixth").put("channel",-1).put("parts",1));
            rejected=false;try{chain.validateLimits();}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Sixth destination blocked independently of part count");chain.file.delete();
            store.saveRadio("mt","saved-a","Home receiver","");store.saveRadio("mt","saved-b","Field receiver","");
            MapData savedMap=new MapData(store);
            savedMap.node("mt","saved-a","peer","Hilltop","Router",38,23,true);
            savedMap.node("mt","saved-b","peer","Other observation","Client",39,24,true);
            savedMap.node("mt","saved-a","no-position","No GPS","Client",Double.NaN,Double.NaN,false);
            store.heard("mt","saved-a","peer",1000);
            store.close();store=new ChatStore(context);
            JSONArray restoredNodes=new MapData(store).nodes();int observations=0;boolean withoutPosition=false;
            for(int i=0;i<restoredNodes.length();i++){JSONObject n=restoredNodes.getJSONObject(i);if(n.optString("id").equals("peer"))observations++;if(n.optString("id").equals("no-position"))withoutPosition=!n.has("lat");}
            check(observations==2&&withoutPosition,"Offline node cache retains per-device observations and nodes without GPS");
            check(store.lastHeard("mt","saved-a","peer")==1000&&store.getRadio("mt","saved-b").name.equals("Field receiver"),"Device names and heard times survive reopen");
            MapData alertsMap=new MapData(store);
            JSONObject fence=new JSONObject("{\"id\":\"alert-test\",\"kind\":\"geofence\",\"title\":\"Home\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[22,37],[24,37],[24,39],[22,39],[22,37]]]}}");
            alertsMap.put(fence,false);
            check(alertsMap.geofenceChanges("mt","saved-a","peer",38,23).length()==1,"First inside report saves alert");
            check(alertsMap.geofenceChanges("mt","saved-a","peer",38,23).length()==0,"Repeated inside report does not duplicate alert");
            check(alertsMap.geofenceChanges("mt","saved-a","peer",40,25).length()==1,"Leaving saves alert");
            JSONObject alertBackup=alertsMap.backup();alertsMap.mergeBackup(alertBackup);
            check(alertsMap.alerts().length()==2,"Backup merges alerts by stable ID");
            alertsMap.delete("alert-test");store.close();store=new ChatStore(context);alertsMap=new MapData(store);
            check(alertsMap.alerts().length()==2,"Alerts survive restart and fence deletion");
            JSONObject savedAlert=alertsMap.alerts().getJSONObject(0);
            check(savedAlert.getString("node").equals("peer")&&savedAlert.getString("name").equals("Hilltop"),"Alert preserves node identity and name");
            alertsMap.deleteAlert(savedAlert.getString("id"));check(alertsMap.alerts().length()==1,"Explicit deletion removes only requested alert");
            MapData positionMap=new MapData(store);
            long positionTime=System.currentTimeMillis()-120000;
            NodePosition precise=new NodePosition(38,23.74,positionTime,positionTime,positionTime+1000,32,"mt_live");
            positionMap.node("mt","precision-rx","!291b406e","Klif Aegina","Client",precise,true);
            NodePosition coarse=new NodePosition(37.9846656,23.724032,positionTime+30000,positionTime+30000,positionTime+31000,13,"mt_live");
            NodePosition retained=positionMap.node("mt","precision-rx","!291b406e","Klif Aegina","Client",coarse,true);
            check(retained.latitude==38&&retained.longitude==23.74&&retained.precisionBits==32,"Database preserves known precision for the same reported cell");
            NodePosition stale=new NodePosition(39,24,positionTime-1000,positionTime-1000,positionTime+32000,13,"mt_live");
            check(positionMap.node("mt","precision-rx","!291b406e","Klif Aegina","Client",stale,true).latitude==38,"Stale reports do not replace a newer stored position");
            JSONObject positionBackup=positionMap.backup(),positionNode=null;
            for(int i=0;i<positionBackup.getJSONArray("nodes").length();i++){
                JSONObject n=positionBackup.getJSONArray("nodes").getJSONObject(i);
                if(n.getString("device").equals("precision-rx")){positionNode=n;break;}
            }
            check(positionNode!=null&&positionNode.getInt("precisionBits")==32&&positionNode.getLong("fixTime")==positionTime&&positionNode.getString("positionSource").equals("mt_preserved"),"Map backup retains independent fix time, precision and source");
            positionNode.put("device","restored-precision-rx");
            positionMap.mergeBackup(new JSONObject().put("nodes",new JSONArray().put(positionNode)).put("positions",new JSONArray()).put("heard",new JSONArray()).put("items",new JSONArray()));
            store.close();store=new ChatStore(context);positionMap=new MapData(store);
            boolean restoredPrecision=false;
            for(int i=0;i<positionMap.nodes().length();i++){
                JSONObject n=positionMap.nodes().getJSONObject(i);
                if(n.getString("device").equals("restored-precision-rx"))restoredPrecision=n.getInt("precisionBits")==32&&n.getLong("fixTime")==positionTime;
            }
            check(restoredPrecision,"Position metadata survives backup restore and database reopen");
            report.putString("stream","History, contacts, backup, chain limits/restart and offline node/position metadata persistence tests passed\n");finish(Activity.RESULT_OK,report);
        }catch(Throwable error){report.putString("stream",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,report);}
        finally{if(store!=null)store.close();}
    }
}
