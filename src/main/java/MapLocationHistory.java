package org.overmesh.android;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;

/** Shared phone locations stay separate from the radio's own advertised/GPS position. */
final class MapLocationHistory {
    static final long RETENTION=30L*24*60*60*1000;
    static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS map_shared_positions(id INTEGER PRIMARY KEY AUTOINCREMENT,radio TEXT NOT NULL,device TEXT NOT NULL,stream TEXT NOT NULL,sender TEXT NOT NULL,label TEXT NOT NULL,channel INTEGER NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL,time INTEGER NOT NULL,UNIQUE(radio,device,stream,lat,lon,time))");
        db.execSQL("CREATE INDEX IF NOT EXISTS map_shared_stream ON map_shared_positions(radio,device,stream,time)");
        db.execSQL("CREATE INDEX IF NOT EXISTS map_shared_time ON map_shared_positions(time)");
    }
    static boolean recent(long time,long now){return time>=now-RETENTION&&time<=now+300000;}
    static void record(SQLiteDatabase db,String radio,String device,String stream,String sender,String label,int channel,double lat,double lon,long time,long now){
        if(!recent(time,now)||!NodePosition.valid(lat,lon)||device.isEmpty()||stream.isEmpty()||stream.length()>160||sender.isEmpty()||sender.length()>160||label.length()>200||!(radio.equals("mc")||radio.equals("mt")))return;
        ContentValues v=new ContentValues();v.put("radio",radio);v.put("device",device);v.put("stream",stream);v.put("sender",sender);v.put("label",label);v.put("channel",channel);v.put("lat",lat);v.put("lon",lon);v.put("time",time);
        db.insertWithOnConflict("map_shared_positions",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    static JSONArray read(SQLiteDatabase db)throws JSONException{
        JSONArray out=new JSONArray();
        try(Cursor c=db.rawQuery("SELECT radio,device,stream,sender,label,channel,lat,lon,time FROM map_shared_positions ORDER BY time,id",null)){
            while(c.moveToNext())out.put(new JSONObject().put("radio",c.getString(0)).put("device",c.getString(1)).put("stream",c.getString(2)).put("sender",c.getString(3)).put("label",c.getString(4)).put("channel",c.getInt(5)).put("lat",c.getDouble(6)).put("lon",c.getDouble(7)).put("time",c.getLong(8)));
        }return out;
    }
    static void merge(SQLiteDatabase db,JSONArray rows)throws JSONException{
        if(rows==null)return;long now=System.currentTimeMillis();
        for(int i=0;i<rows.length();i++){JSONObject p=rows.getJSONObject(i);record(db,p.getString("radio"),p.getString("device"),p.getString("stream"),p.getString("sender"),p.getString("label"),p.getInt("channel"),p.getDouble("lat"),p.getDouble("lon"),p.getLong("time"),now);}
    }
    static void prune(SQLiteDatabase db,long now){
        String cutoff=Long.toString(now-RETENTION);db.beginTransaction();
        try{
            db.delete("map_shared_positions","time<?",new String[]{cutoff});
            db.delete("map_positions","CASE WHEN gps_time>0 THEN gps_time WHEN position_time>0 THEN position_time ELSE reported_at END<CAST(? AS INTEGER)",new String[]{cutoff});
            db.delete("map_heard","heard_at<?",new String[]{cutoff});
            db.delete("map_alerts","time<?",new String[]{cutoff});
            db.delete("map_items","kind='mark' AND radio<>'' AND edited<?",new String[]{cutoff});
            // Retain identities, but remove stale coordinates and stale fence transition state.
            String stale="CASE WHEN gps_time>0 THEN gps_time WHEN position_time>0 THEN position_time WHEN position_received>0 THEN position_received ELSE position_at END<"+cutoff;
            db.execSQL("DELETE FROM map_geofence_state WHERE EXISTS(SELECT 1 FROM map_nodes n WHERE n.radio=map_geofence_state.radio AND n.device=map_geofence_state.device AND n.id=map_geofence_state.node AND "+stale+")");
            db.execSQL("UPDATE map_nodes SET lat=NULL,lon=NULL,position_at=0,position_time=0,gps_time=0,position_received=0,precision_bits=0 WHERE (lat IS NOT NULL OR lon IS NOT NULL) AND "+stale);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}
