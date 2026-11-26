package org.overmesh.android;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.File;
public class ContactRenameRuntime {
 static int checks=0;
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;System.out.println("PASS "+why);}
 static class C extends ContextWrapper {
  final File dir; C(String path){super(null);dir=new File(path);dir.mkdirs();}
  public Context getApplicationContext(){return this;}
  public ApplicationInfo getApplicationInfo(){ApplicationInfo a=new ApplicationInfo();a.targetSdkVersion=30;return a;}
  public String getPackageName(){return "org.overmesh.android.test";}
  public File getDatabasePath(String name){return new File(dir,name);}
  public SQLiteDatabase openOrCreateDatabase(String n,int mode,SQLiteDatabase.CursorFactory f){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(n),f);}
  public SQLiteDatabase openOrCreateDatabase(String n,int mode,SQLiteDatabase.CursorFactory f,DatabaseErrorHandler h){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(n).getPath(),f,h);}
 }
 static JSONObject person(String id,String radio,String node,String name)throws Exception{return new JSONObject().put("id",id).put("name","Personal name").put("displayName","Custom person").put("edited",1).put("nodes",new JSONArray().put(new JSONObject().put("radio",radio).put("id",node).put("name",name).put("label","My label").put("assignmentRole","Owner")));}
 static JSONObject find(ChatStore s,String id)throws Exception{JSONArray a=ContactsStore.all(s);for(int i=0;i<a.length();i++)if(a.getJSONObject(i).getString("id").equals(id))return a.getJSONObject(i);throw new AssertionError(id);}
 static String name(ChatStore s,String id)throws Exception{return find(s,id).getJSONArray("nodes").getJSONObject(0).getString("name");}
 public static void main(String[] args)throws Exception{
  C context=new C(args[0]+"/a");ChatStore s=new ChatStore(context);
  JSONArray original=new JSONArray().put(person("mt","mt","!12345678","Old")).put(person("mc","mc","aabbccddeeff","MC old")).put(person("deleted","mt","!12345678","Deleted old").put("deletedAt",1));
  ContactsStore.merge(s,original,false);
  ContactsStore.updateNodeName(s,"mt","!12345678","New",2000);
  check(name(s,"mt").equals("New"),"assigned name updated");JSONObject p=find(s,"mt");
  check(p.getString("name").equals("Personal name")&&p.getString("displayName").equals("Custom person")&&p.getJSONArray("nodes").getJSONObject(0).getString("label").equals("My label"),"person and custom label preserved");
  check(name(s,"mc").equals("MC old")&&name(s,"deleted").equals("Deleted old"),"network separation and deleted contacts preserved");
  ContactsStore.updateNodeName(s,"mt","!12345678","",3000);ContactsStore.updateNodeName(s,"mt","!99999999","Other",3000);check(name(s,"mt").equals("New"),"blank and unrelated reports ignored");
  ContactsStore.updateNodeName(s,"mc","aabbccddeeff"+"00".repeat(26),"MC new",2000);check(name(s,"mc").equals("MC new"),"MC full-key report matches assigned short ID");
  s.close();s=new ChatStore(context);check(name(s,"mt").equals("New"),"database close and reopen preserves rename");
  JSONArray backup=new JSONArray(ContactsStore.all(s).toString());ChatStore restored=new ChatStore(new C(args[0]+"/b"));ContactsStore.merge(restored,backup,true);check(name(restored,"mt").equals("New"),"serialized contact backup restored into empty database");
  ContactsStore.merge(restored,original,true);check(name(restored,"mt").equals("New"),"older backup does not replace newer record");
  ContactsStore.updateNodeName(s,"mt","!12345678","Old",1000);
  check(name(s,"mt").equals("New"),"older receiver report rejected after reopen");
  ContactsStore.updateNodeName(restored,"mt","!12345678","Old",1000);
  check(name(restored,"mt").equals("New"),"restored freshness rejects stale report");
  ContactsStore.updateNodeName(s,"mt","!12345678","Conflict",2000);
  ContactsStore.updateNodeName(s,"mt","!12345678","Unknown age",0);
  check(name(s,"mt").equals("New"),"equal-time and unknown-age reports cannot overwrite name");
  JSONObject staleEditor=new JSONObject(find(s,"mt").toString());
  ContactsStore.updateNodeName(s,"mt","!12345678","New",4000);
  ContactsStore.updateNodeName(s,"mt","!12345678","Intermediate",3000);
  check(name(s,"mt").equals("New"),"matching newer report advances freshness");
  ContactsStore.updateNodeName(s,"mt","!12345678","Newest",5000);
  check(name(s,"mt").equals("Newest"),"newer rename accepted");
  staleEditor.put("name","Edited person").put("edited",System.currentTimeMillis()+100);
  ContactsStore.merge(s,new JSONArray().put(staleEditor),false);
  check(name(s,"mt").equals("Newest")&&find(s,"mt").getString("name").equals("Edited person"),"stale contact editor preserves newest node name and personal edit");
  staleEditor.getJSONArray("nodes").getJSONObject(0).put("name","Old backup").remove("nameObservedAt");
  staleEditor.put("edited",System.currentTimeMillis()+200);
  ContactsStore.merge(s,new JSONArray().put(staleEditor),true);
  check(name(s,"mt").equals("Newest"),"newer personal backup cannot regress node-name freshness");
  ContactsStore.updateNodeName(s,"mc","aabbccddeeff"+"00".repeat(26),"MC stale",1000);
  ContactsStore.updateNodeName(s,"mc","aabbccddeeff"+"00".repeat(26),"MC newest",3000);
  check(name(s,"mc").equals("MC newest"),"MC advert timestamps order renames");
  ContactsStore.merge(s,new JSONArray().put(person("empty","mt","!87654321","")),false);
  ContactsStore.updateNodeName(s,"mt","!87654321","Initial",0);
  ContactsStore.updateNodeName(s,"mt","!87654321","Cached conflict",0);
  check(name(s,"empty").equals("Initial"),"unknown snapshot fills only an empty name");
  ContactsStore.updateNodeName(s,"mt","!12345678","Future",System.currentTimeMillis()+3600000);
  check(name(s,"mt").equals("Newest"),"invalid future time cannot poison saved freshness");
  s.close();restored.close();System.out.println("Completed "+checks+" assertions.");
 }
}
