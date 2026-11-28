package org.overmesh.android;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import org.json.*;
public class MapLocationHistorySmoke {
 static int checks;
 static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
 static long count(SQLiteDatabase db,String table){try(Cursor c=db.rawQuery("SELECT count(*) FROM "+table,null)){c.moveToFirst();return c.getLong(0);}}
 public static void main(String[] args)throws Exception{
  SQLiteDatabase db=SQLiteDatabase.create(null);MapLocationHistory.create(db);
  long now=System.currentTimeMillis(),cut=now-MapLocationHistory.RETENTION;
  MapLocationHistory.record(db,"mt","r","s","n","Walker",0,38,23,now,now);
  MapLocationHistory.record(db,"mt","r","s","n","Walker",0,38,23,now,now);
  check(count(db,"map_shared_positions")==1);
  MapLocationHistory.record(db,"mt","r","s","n","Walker",0,37,23,cut-1,now);
  MapLocationHistory.record(db,"mt","r","s","n","Walker",0,37,23,now+300001,now);
  check(count(db,"map_shared_positions")==1);
  MapLocationHistory.record(db,"mt","r","s","n","Walker",0,37,23,cut,now);
  MapLocationHistory.record(db,"mt","other","s","n","Walker",0,38,23,now,now);
  check(count(db,"map_shared_positions")==3);
  check(MapLocationHistory.read(db).getJSONObject(0).getLong("time")==cut);
  db.execSQL("CREATE TABLE map_positions(gps_time INTEGER,position_time INTEGER,reported_at INTEGER)");
  db.execSQL("CREATE TABLE map_heard(heard_at INTEGER)");
  db.execSQL("CREATE TABLE map_alerts(time INTEGER)");
  db.execSQL("CREATE TABLE map_items(kind TEXT,radio TEXT,edited INTEGER)");
  db.execSQL("CREATE TABLE map_nodes(radio TEXT,device TEXT,id TEXT,lat REAL,lon REAL,position_at INTEGER,position_time INTEGER,gps_time INTEGER,position_received INTEGER,precision_bits INTEGER)");
  db.execSQL("CREATE TABLE map_geofence_state(radio TEXT,device TEXT,node TEXT)");
  db.execSQL("INSERT INTO map_positions VALUES("+(cut-1)+",0,"+now+"),(0,"+cut+","+now+")");
  db.execSQL("INSERT INTO map_heard VALUES("+(cut-1)+"),("+now+")");
  db.execSQL("INSERT INTO map_alerts VALUES("+(cut-1)+"),("+now+")");
  db.execSQL("INSERT INTO map_items VALUES('mark','mt',"+(cut-1)+"),('mark','',1),('geofence','mt',1),('overlay','',1)");
  db.execSQL("INSERT INTO map_nodes VALUES('mt','r','n',38,23,"+now+",0,"+(cut-1)+","+now+",32)");
  db.execSQL("INSERT INTO map_geofence_state VALUES('mt','r','n')");
  MapLocationHistory.prune(db,now);
  check(count(db,"map_positions")==1);check(count(db,"map_heard")==1);check(count(db,"map_alerts")==1);
  check(count(db,"map_items")==3);check(count(db,"map_geofence_state")==0);
  try(Cursor c=db.rawQuery("SELECT lat,position_received FROM map_nodes",null)){c.moveToFirst();check(c.isNull(0)&&c.getLong(1)==0);}
  check(count(db,"map_nodes")==1);
  MapLocationHistory.prune(db,now+1);check(count(db,"map_shared_positions")==2);
  JSONArray old=new JSONArray().put(new JSONObject().put("radio","mt").put("device","r").put("stream","s").put("sender","n").put("label","Walker").put("channel",0).put("lat",38).put("lon",23).put("time",cut-1));
  MapLocationHistory.merge(db,old);check(count(db,"map_shared_positions")==2);
  MapLocationHistory.prune(db,now+1);check(count(db,"map_shared_positions")==2);
  db.close();System.out.println("MapLocationHistorySmoke: "+checks+" assertions passed");
 }
}
