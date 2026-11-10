package org.overmesh.android;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Local map records. Radio identity and receiving device are kept separate. */
final class MapData {
    private final ChatStore store;
    MapData(ChatStore store){this.store=store;}
    private SQLiteDatabase db(){return store.getWritableDatabase();}
    static boolean valid(double lat,double lon){return NodePosition.valid(lat,lon);}
    void node(String radio,String device,String id,String name,String role,double lat,double lon,boolean fresh) {
        node(radio,device,id,name,role,new NodePosition(lat,lon,0,0,fresh?System.currentTimeMillis():0,0,"legacy"),fresh);
    }
    private static void putPosition(ContentValues values,NodePosition position) {
        values.put("lat",position.latitude);values.put("lon",position.longitude);
        values.put("position_time",position.reportTime);values.put("gps_time",position.fixTime);
        values.put("position_received",position.receivedTime);values.put("precision_bits",position.precisionBits);
        values.put("position_source",position.source);
    }
    private static JSONObject positionJson(JSONObject item,NodePosition p) throws JSONException {
        return item.put("positionTime",p.reportTime).put("fixTime",p.fixTime).put("receivedAt",p.receivedTime)
                .put("precisionBits",p.precisionBits).put("positionSource",p.source);
    }
    private static NodePosition backupPosition(JSONObject item) {
        return new NodePosition(item.optDouble("lat",Double.NaN),item.optDouble("lon",Double.NaN),
                item.optLong("positionTime"),item.optLong("fixTime"),item.optLong("receivedAt",item.optLong("positionAt")),
                item.optInt("precisionBits"),item.optString("positionSource","legacy"));
    }
    NodePosition node(String radio,String device,String id,String name,String role,NodePosition incoming,boolean fresh) {
        if(device.isEmpty()||id.isEmpty()||(!radio.equals("mt")&&!radio.equals("mc")))return incoming;
        SQLiteDatabase db=db();long now=System.currentTimeMillis();
        NodePosition old=null;
        try(Cursor c=db.rawQuery("SELECT lat,lon,position_time,gps_time,position_received,precision_bits,position_source,position_at FROM map_nodes WHERE radio=? AND device=? AND id=?",new String[]{radio,device,id})) {
            if(c.moveToFirst())old=new NodePosition(c.isNull(0)?Double.NaN:c.getDouble(0),c.isNull(1)?Double.NaN:c.getDouble(1),
                    c.getLong(2),c.getLong(3),c.getLong(4)>0?c.getLong(4):c.getLong(7),c.getInt(5),c.getString(6));
        }
        NodePosition chosen=NodePosition.choose(old,incoming);
        ContentValues row=new ContentValues();row.put("radio",radio);row.put("device",device);row.put("id",id);
        row.put("name",name==null?"":name);row.put("role",role==null?"":role);
        db.insertWithOnConflict("map_nodes",null,row,SQLiteDatabase.CONFLICT_IGNORE);
        ContentValues update=new ContentValues();if(name!=null&&!name.isEmpty())update.put("name",name);if(role!=null&&!role.isEmpty())update.put("role",role);
        if(chosen!=null&&chosen.valid()){
            putPosition(update,chosen);update.put("position_at",chosen.receivedTime);
        }
        if(update.size()>0)db.update("map_nodes",update,"radio=? AND device=? AND id=?",new String[]{radio,device,id});
        if(fresh&&chosen!=null&&chosen.valid()&&chosen!=old&&!chosen.source.equals("mt_preserved")){
            double previousLat=Double.NaN,previousLon=Double.NaN;long previousAt=0;
            try(Cursor c=db.rawQuery("SELECT lat,lon,reported_at FROM map_positions WHERE radio=? AND device=? AND node=? ORDER BY reported_at DESC,id DESC LIMIT 1",new String[]{radio,device,id})){
                if(c.moveToFirst()){previousLat=c.getDouble(0);previousLon=c.getDouble(1);previousAt=c.getLong(2);}
            }
            if(chosen.latitude!=previousLat||chosen.longitude!=previousLon||now-previousAt>=300000){
                ContentValues point=new ContentValues();point.put("radio",radio);point.put("device",device);point.put("node",id);
                putPosition(point,chosen);point.put("reported_at",chosen.receivedTime>0?chosen.receivedTime:now);db.insert("map_positions",null,point);
            }
        }
        return chosen;
    }
    void heard(String radio,String device,String id,long seconds){
        if(device.isEmpty()||id.isEmpty()||seconds<=0)return;
        long ms=seconds*1000L;if(ms>System.currentTimeMillis()+300000)return;
        try(Cursor c=db().rawQuery("SELECT heard_at FROM map_heard WHERE radio=? AND device=? AND node=? ORDER BY heard_at DESC LIMIT 1",new String[]{radio,device,id})){
            if(c.moveToFirst()&&Math.abs(ms-c.getLong(0))<60000)return;
        }
        ContentValues v=new ContentValues();v.put("radio",radio);v.put("device",device);v.put("node",id);v.put("heard_at",ms);db().insert("map_heard",null,v);
    }
    JSONArray nodes() throws JSONException {
        JSONArray out=new JSONArray();
        try(Cursor c=db().rawQuery("SELECT n.radio,n.device,n.id,n.name,n.role,n.lat,n.lon,n.position_at,COALESCE((SELECT MAX(h.heard_at) FROM map_heard h WHERE h.radio=n.radio AND h.device=n.device AND h.node=n.id),0) , r.settings,n.position_time,n.gps_time,n.position_received,n.precision_bits,n.position_source FROM map_nodes n LEFT JOIN node_heard r ON r.radio=n.radio AND r.node_id=n.device AND r.peer=n.id ORDER BY n.name,n.id",null)){
            while(c.moveToNext()){
                JSONObject item=new JSONObject().put("radio",c.getString(0)).put("device",c.getString(1)).put("id",c.getString(2)).put("name",c.getString(3)).put("role",c.getString(4)).put("positionAt",c.getLong(7)).put("lastHeard",c.getLong(8)).put("receivingRf",c.getString(9));
                if(!c.isNull(5)&&!c.isNull(6))item.put("lat",c.getDouble(5)).put("lon",c.getDouble(6));
                positionJson(item,new NodePosition(c.isNull(5)?Double.NaN:c.getDouble(5),c.isNull(6)?Double.NaN:c.getDouble(6),c.getLong(10),c.getLong(11),c.getLong(12)>0?c.getLong(12):c.getLong(7),c.getInt(13),c.getString(14)));out.put(item);
            }
        }return out;
    }
    JSONArray positions(String radio,String device,String node,long from,long to) throws JSONException {return positions(radio,device,node,from,to,"10000");}
    private JSONArray positions(String radio,String device,String node,long from,long to,String limit) throws JSONException {
        JSONArray out=new JSONArray();List<String> args=new ArrayList<>();String where="1=1";
        if(!radio.isEmpty()){where+=" AND radio=?";args.add(radio);}if(!device.isEmpty()){where+=" AND device=?";args.add(device);}if(!node.isEmpty()){where+=" AND node=?";args.add(node);}
        if(from>0){where+=" AND reported_at>=?";args.add(Long.toString(from));}if(to>0){where+=" AND reported_at<=?";args.add(Long.toString(to));}
        try(Cursor c=db().query("map_positions",new String[]{"radio","device","node","lat","lon","reported_at","position_time","gps_time","position_received","precision_bits","position_source"},where,args.toArray(new String[0]),null,null,"reported_at ASC,id ASC",limit)){
            while(c.moveToNext())out.put(positionJson(new JSONObject().put("radio",c.getString(0)).put("device",c.getString(1)).put("node",c.getString(2)).put("lat",c.getDouble(3)).put("lon",c.getDouble(4)).put("time",c.getLong(5)),
                    new NodePosition(c.getDouble(3),c.getDouble(4),c.getLong(6),c.getLong(7),c.getLong(8)>0?c.getLong(8):c.getLong(5),c.getInt(9),c.getString(10))));
        }return out;
    }
    JSONArray heardEvents() throws JSONException {return heardEvents(true);}
    private JSONArray heardEvents(boolean limited) throws JSONException {
        JSONArray out=new JSONArray();try(Cursor c=db().rawQuery("SELECT radio,device,node,heard_at FROM map_heard ORDER BY heard_at DESC"+(limited?" LIMIT 50000":""),null)){
            while(c.moveToNext())out.put(new JSONObject().put("radio",c.getString(0)).put("device",c.getString(1)).put("node",c.getString(2)).put("time",c.getLong(3)));
        }return out;
    }
    JSONArray items() throws JSONException {
        JSONArray out=new JSONArray();try(Cursor c=db().rawQuery("SELECT id,kind,title,description,geometry,enabled,radio,device,channel,created,edited FROM map_items ORDER BY edited DESC",null)){
            while(c.moveToNext())out.put(new JSONObject().put("id",c.getString(0)).put("kind",c.getString(1)).put("title",c.getString(2)).put("description",c.getString(3)).put("geometry",new JSONObject(c.getString(4))).put("enabled",c.getInt(5)!=0).put("radio",c.getString(6)).put("device",c.getString(7)).put("channel",c.getInt(8)).put("created",c.getLong(9)).put("edited",c.getLong(10)));
        }return out;
    }
    void put(JSONObject item,boolean imported) throws JSONException {
        String id=item.getString("id"),kind=item.getString("kind"),title=item.getString("title").trim();
        if(!id.matches("[a-zA-Z0-9-]{1,80}")||!java.util.Arrays.asList("mark","overlay","geofence").contains(kind)||title.isEmpty()||title.length()>100)throw new JSONException("Invalid map item");
        JSONObject geometry=item.getJSONObject("geometry");String type=geometry.optString("type");JSONArray coordinates=geometry.optJSONArray("coordinates");
        if(coordinates==null||!(kind.equals("mark")?type.equals("Point"):kind.equals("geofence")?type.equals("Polygon"):type.equals("LineString")||type.equals("Polygon")))throw new JSONException("Invalid geometry");
        validateGeometry(type,coordinates);
        if(geometry.toString().length()>100000)throw new JSONException("Map item is too large");
        long now=System.currentTimeMillis(),created=item.optLong("created",now),edited=item.optLong("edited",now);
        if(imported)try(Cursor c=db().rawQuery("SELECT edited FROM map_items WHERE id=?",new String[]{id})){if(c.moveToFirst()&&c.getLong(0)>=edited)return;}
        ContentValues v=new ContentValues();v.put("id",id);v.put("kind",kind);v.put("title",title);v.put("description",item.optString("description","").substring(0,Math.min(2000,item.optString("description","").length())));v.put("geometry",geometry.toString());v.put("enabled",item.optBoolean("enabled",true)?1:0);v.put("radio",item.optString("radio",""));v.put("device",item.optString("device",""));v.put("channel",item.optInt("channel",-1));v.put("created",created);v.put("edited",edited);db().insertWithOnConflict("map_items",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    void delete(String id){db().delete("map_items","id=?",new String[]{id});db().delete("map_geofence_state","item=?",new String[]{id});}
    JSONArray geofenceChanges(String radio,String device,String node,double lat,double lon) throws JSONException {
        JSONArray changes=new JSONArray();if(!valid(lat,lon))return changes;
        SQLiteDatabase database=db();database.beginTransaction();
        try { JSONArray fences=items();for(int i=0;i<fences.length();i++){JSONObject item=fences.getJSONObject(i);if(!item.getString("kind").equals("geofence")||!item.getBoolean("enabled"))continue;
            JSONObject geometry=item.getJSONObject("geometry");if(!geometry.optString("type").equals("Polygon"))continue;
            boolean inside=insidePolygon(geometry.getJSONArray("coordinates").getJSONArray(0),lat,lon);String id=item.getString("id");boolean known=false,old=false;
            try(Cursor c=db().rawQuery("SELECT inside FROM map_geofence_state WHERE item=? AND radio=? AND device=? AND node=?",new String[]{id,radio,device,node})){if(c.moveToFirst()){known=true;old=c.getInt(0)!=0;}}
            ContentValues v=new ContentValues();v.put("item",id);v.put("radio",radio);v.put("device",device);v.put("node",node);v.put("inside",inside?1:0);db().insertWithOnConflict("map_geofence_state",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            if(known ? old!=inside : inside){
                String event=!known ? "appeared in" : inside ? "entered" : "left";
                String name=node;
                try(Cursor c=database.rawQuery("SELECT name FROM map_nodes WHERE radio=? AND device=? AND id=?",new String[]{radio,device,node})){if(c.moveToFirst()&&!c.getString(0).isEmpty())name=c.getString(0);}
                JSONObject alert=new JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("time",System.currentTimeMillis()).put("fence",id).put("title",item.getString("title")).put("node",node).put("name",name).put("radio",radio).put("device",device).put("lat",lat).put("lon",lon).put("event",event);
                saveAlert(alert);changes.put(alert);
            }
        }database.setTransactionSuccessful();return changes;
        }finally{database.endTransaction();}
    }
    JSONArray alerts() throws JSONException {
        JSONArray out=new JSONArray();try(Cursor c=db().rawQuery("SELECT body FROM map_alerts ORDER BY time DESC,id",null)){while(c.moveToNext())out.put(new JSONObject(c.getString(0)));}return out;
    }
    void deleteAlert(String id){db().delete("map_alerts","id=?",new String[]{id});}
    private void saveAlert(JSONObject alert) throws JSONException {
        if(!alert.getString("id").matches("[a-zA-Z0-9-]{1,80}")||!validIdentity(alert.getString("radio"),alert.getString("device"),alert.getString("node"))||!valid(alert.getDouble("lat"),alert.getDouble("lon")))throw new JSONException("Invalid geofence alert");
        ContentValues row=new ContentValues();row.put("id",alert.getString("id"));row.put("time",alert.getLong("time"));row.put("body",alert.toString());db().insertWithOnConflict("map_alerts",null,row,SQLiteDatabase.CONFLICT_IGNORE);
    }
    private static boolean insidePolygon(JSONArray points,double lat,double lon){
        boolean inside=false;try {for(int i=0,j=points.length()-1;i<points.length();j=i++){
            JSONArray a=points.getJSONArray(i),b=points.getJSONArray(j);double ax=a.getDouble(0),ay=a.getDouble(1),bx=b.getDouble(0),by=b.getDouble(1);
            if((ay>lat)!=(by>lat)&&lon<(bx-ax)*(lat-ay)/(by-ay)+ax)inside=!inside;
        }}catch(JSONException ignored){return false;}return inside;
    }
    private static void validateGeometry(String type,JSONArray coordinates) throws JSONException {
        if(type.equals("Point")){validatePoint(coordinates);return;}
        JSONArray points=type.equals("Polygon")?coordinates.getJSONArray(0):coordinates;
        if(points.length()<(type.equals("Polygon")?4:2)||points.length()>5000)throw new JSONException("Invalid number of map points");
        for(int i=0;i<points.length();i++)validatePoint(points.getJSONArray(i));
        if(type.equals("Polygon")){
            JSONArray first=points.getJSONArray(0),last=points.getJSONArray(points.length()-1);
            if(first.getDouble(0)!=last.getDouble(0)||first.getDouble(1)!=last.getDouble(1))throw new JSONException("Area must close at its starting point");
        }
    }
    private static void validatePoint(JSONArray point) throws JSONException {
        if(point.length()!=2||!valid(point.getDouble(1),point.getDouble(0)))throw new JSONException("Invalid map coordinates");
    }
    JSONObject snapshot() throws JSONException {return new JSONObject().put("nodes",nodes()).put("positions",positions("","","",0,0)).put("heard",heardEvents()).put("items",items()).put("alerts",alerts());}
    JSONObject backup() throws JSONException {
        JSONArray fences=new JSONArray();try(Cursor c=db().rawQuery("SELECT item,radio,device,node,inside FROM map_geofence_state",null)){
            while(c.moveToNext())fences.put(new JSONObject().put("item",c.getString(0)).put("radio",c.getString(1)).put("device",c.getString(2)).put("node",c.getString(3)).put("inside",c.getInt(4)));
        }
        return new JSONObject().put("nodes",nodes()).put("positions",positions("","","",0,0,null)).put("heard",heardEvents(false)).put("items",items()).put("fenceState",fences).put("alerts",alerts());
    }
    void mergeBackup(JSONObject data) throws JSONException {
        if(data==null)return;SQLiteDatabase db=db();
        JSONArray ns=data.optJSONArray("nodes"),ps=data.optJSONArray("positions"),hs=data.optJSONArray("heard"),items=data.optJSONArray("items"),fs=data.optJSONArray("fenceState");
        if(ns==null||ps==null||hs==null||items==null)throw new JSONException("Invalid Map backup");
        JSONArray alerts=data.optJSONArray("alerts");if(alerts!=null)for(int i=0;i<alerts.length();i++)saveAlert(alerts.getJSONObject(i));
        for(int i=0;i<ns.length();i++){JSONObject n=ns.getJSONObject(i);String radio=n.getString("radio"),device=n.getString("device"),id=n.getString("id");if(!validIdentity(radio,device,id))continue;
            ContentValues v=new ContentValues();v.put("radio",radio);v.put("device",device);v.put("id",id);v.put("name",n.optString("name",id));v.put("role",n.optString("role",""));if(n.has("lat")&&n.has("lon")&&valid(n.optDouble("lat"),n.optDouble("lon"))){v.put("lat",n.getDouble("lat"));v.put("lon",n.getDouble("lon"));}v.put("position_at",n.optLong("positionAt",0));NodePosition imported=backupPosition(n);if(imported.valid())putPosition(v,imported);db.insertWithOnConflict("map_nodes",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        }
        for(int i=0;i<ps.length();i++){JSONObject p=ps.getJSONObject(i);String radio=p.getString("radio"),device=p.getString("device"),node=p.getString("node");double lat=p.getDouble("lat"),lon=p.getDouble("lon");long time=p.getLong("time");if(!validIdentity(radio,device,node)||!valid(lat,lon)||time<=0)continue;
            ContentValues v=new ContentValues();v.put("radio",radio);v.put("device",device);v.put("node",node);v.put("lat",lat);v.put("lon",lon);v.put("reported_at",time);NodePosition imported=backupPosition(p);if(imported.valid())putPosition(v,imported);db.insertWithOnConflict("map_positions",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        }
        for(int i=0;i<hs.length();i++){JSONObject h=hs.getJSONObject(i);String radio=h.getString("radio"),device=h.getString("device"),node=h.getString("node");long time=h.getLong("time");if(!validIdentity(radio,device,node)||time<=0)continue;
            ContentValues v=new ContentValues();v.put("radio",radio);v.put("device",device);v.put("node",node);v.put("heard_at",time);db.insertWithOnConflict("map_heard",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        }
        for(int i=0;i<items.length();i++)put(items.getJSONObject(i),true);
        if(fs!=null)for(int i=0;i<fs.length();i++){JSONObject f=fs.getJSONObject(i);ContentValues v=new ContentValues();v.put("item",f.getString("item"));v.put("radio",f.getString("radio"));v.put("device",f.getString("device"));v.put("node",f.getString("node"));v.put("inside",f.optInt("inside",0)!=0?1:0);db.insertWithOnConflict("map_geofence_state",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    }
    private static boolean validIdentity(String radio,String device,String node){return (radio.equals("mc")||radio.equals("mt"))&&device.length()>0&&device.length()<160&&node.length()>0&&node.length()<160;}
}
