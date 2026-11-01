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
    static boolean valid(double lat,double lon){return Double.isFinite(lat)&&Double.isFinite(lon)&&lat>=-90&&lat<=90&&lon>=-180&&lon<=180&&!(lat==0&&lon==0);}
    void node(String radio,String device,String id,String name,String role,double lat,double lon,boolean fresh) {
        if(device.isEmpty()||id.isEmpty()||(!radio.equals("mt")&&!radio.equals("mc")))return;
        SQLiteDatabase db=db();long now=System.currentTimeMillis();
        ContentValues row=new ContentValues();row.put("radio",radio);row.put("device",device);row.put("id",id);
        row.put("name",name==null?"":name);row.put("role",role==null?"":role);
        if(valid(lat,lon)){row.put("lat",lat);row.put("lon",lon);if(fresh)row.put("position_at",now);}
        db.insertWithOnConflict("map_nodes",null,row,SQLiteDatabase.CONFLICT_IGNORE);
        ContentValues update=new ContentValues();if(name!=null&&!name.isEmpty())update.put("name",name);if(role!=null&&!role.isEmpty())update.put("role",role);
        if(valid(lat,lon)){update.put("lat",lat);update.put("lon",lon);if(fresh)update.put("position_at",now);}
        if(update.size()>0)db.update("map_nodes",update,"radio=? AND device=? AND id=?",new String[]{radio,device,id});
        if(fresh&&valid(lat,lon)){
            double previousLat=Double.NaN,previousLon=Double.NaN;long previousAt=0;
            try(Cursor c=db.rawQuery("SELECT lat,lon,reported_at FROM map_positions WHERE radio=? AND device=? AND node=? ORDER BY reported_at DESC,id DESC LIMIT 1",new String[]{radio,device,id})){
                if(c.moveToFirst()){previousLat=c.getDouble(0);previousLon=c.getDouble(1);previousAt=c.getLong(2);}
            }
            // Repeated identical positions are useful as fresh reports, but not as trail points every second.
            if(lat!=previousLat||lon!=previousLon||now-previousAt>=300000){
                ContentValues point=new ContentValues();point.put("radio",radio);point.put("device",device);point.put("node",id);point.put("lat",lat);point.put("lon",lon);point.put("reported_at",now);db.insert("map_positions",null,point);
            }
        }
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
        try(Cursor c=db().rawQuery("SELECT n.radio,n.device,n.id,n.name,n.role,n.lat,n.lon,n.position_at,COALESCE((SELECT MAX(h.heard_at) FROM map_heard h WHERE h.radio=n.radio AND h.device=n.device AND h.node=n.id),0) FROM map_nodes n ORDER BY n.name,n.id",null)){
            while(c.moveToNext()){
                JSONObject item=new JSONObject().put("radio",c.getString(0)).put("device",c.getString(1)).put("id",c.getString(2)).put("name",c.getString(3)).put("role",c.getString(4)).put("positionAt",c.getLong(7)).put("lastHeard",c.getLong(8));
                if(!c.isNull(5)&&!c.isNull(6))item.put("lat",c.getDouble(5)).put("lon",c.getDouble(6));out.put(item);
            }
        }return out;
    }
    JSONArray positions(String radio,String device,String node,long from,long to) throws JSONException {return positions(radio,device,node,from,to,"10000");}
    private JSONArray positions(String radio,String device,String node,long from,long to,String limit) throws JSONException {
        JSONArray out=new JSONArray();List<String> args=new ArrayList<>();String where="1=1";
        if(!radio.isEmpty()){where+=" AND radio=?";args.add(radio);}if(!device.isEmpty()){where+=" AND device=?";args.add(device);}if(!node.isEmpty()){where+=" AND node=?";args.add(node);}
        if(from>0){where+=" AND reported_at>=?";args.add(Long.toString(from));}if(to>0){where+=" AND reported_at<=?";args.add(Long.toString(to));}
        try(Cursor c=db().query("map_positions",new String[]{"radio","device","node","lat","lon","reported_at"},where,args.toArray(new String[0]),null,null,"reported_at ASC,id ASC",limit)){
            while(c.moveToNext())out.put(new JSONObject().put("radio",c.getString(0)).put("device",c.getString(1)).put("node",c.getString(2)).put("lat",c.getDouble(3)).put("lon",c.getDouble(4)).put("time",c.getLong(5)));
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
        JSONArray fences=items();for(int i=0;i<fences.length();i++){JSONObject item=fences.getJSONObject(i);if(!item.getString("kind").equals("geofence")||!item.getBoolean("enabled"))continue;
            JSONObject geometry=item.getJSONObject("geometry");if(!geometry.optString("type").equals("Polygon"))continue;
            boolean inside=insidePolygon(geometry.getJSONArray("coordinates").getJSONArray(0),lat,lon);String id=item.getString("id");boolean known=false,old=false;
            try(Cursor c=db().rawQuery("SELECT inside FROM map_geofence_state WHERE item=? AND radio=? AND device=? AND node=?",new String[]{id,radio,device,node})){if(c.moveToFirst()){known=true;old=c.getInt(0)!=0;}}
            ContentValues v=new ContentValues();v.put("item",id);v.put("radio",radio);v.put("device",device);v.put("node",node);v.put("inside",inside?1:0);db().insertWithOnConflict("map_geofence_state",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            if(known&&old!=inside)changes.put(new JSONObject().put("title",item.getString("title")).put("node",node).put("inside",inside));
        }return changes;
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
    JSONObject snapshot() throws JSONException {return new JSONObject().put("nodes",nodes()).put("positions",positions("","","",0,0)).put("heard",heardEvents()).put("items",items());}
    JSONObject backup() throws JSONException {
        JSONArray fences=new JSONArray();try(Cursor c=db().rawQuery("SELECT item,radio,device,node,inside FROM map_geofence_state",null)){
            while(c.moveToNext())fences.put(new JSONObject().put("item",c.getString(0)).put("radio",c.getString(1)).put("device",c.getString(2)).put("node",c.getString(3)).put("inside",c.getInt(4)));
        }
        return new JSONObject().put("nodes",nodes()).put("positions",positions("","","",0,0,null)).put("heard",heardEvents(false)).put("items",items()).put("fenceState",fences);
    }
    void mergeBackup(JSONObject data) throws JSONException {
        if(data==null)return;SQLiteDatabase db=db();
        JSONArray ns=data.optJSONArray("nodes"),ps=data.optJSONArray("positions"),hs=data.optJSONArray("heard"),items=data.optJSONArray("items"),fs=data.optJSONArray("fenceState");
        if(ns==null||ps==null||hs==null||items==null)throw new JSONException("Invalid Map backup");
        for(int i=0;i<ns.length();i++){JSONObject n=ns.getJSONObject(i);String radio=n.getString("radio"),device=n.getString("device"),id=n.getString("id");if(!validIdentity(radio,device,id))continue;
            ContentValues v=new ContentValues();v.put("radio",radio);v.put("device",device);v.put("id",id);v.put("name",n.optString("name",id));v.put("role",n.optString("role",""));if(n.has("lat")&&n.has("lon")&&valid(n.optDouble("lat"),n.optDouble("lon"))){v.put("lat",n.getDouble("lat"));v.put("lon",n.getDouble("lon"));}v.put("position_at",n.optLong("positionAt",0));db.insertWithOnConflict("map_nodes",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        }
        for(int i=0;i<ps.length();i++){JSONObject p=ps.getJSONObject(i);String radio=p.getString("radio"),device=p.getString("device"),node=p.getString("node");double lat=p.getDouble("lat"),lon=p.getDouble("lon");long time=p.getLong("time");if(!validIdentity(radio,device,node)||!valid(lat,lon)||time<=0)continue;
            ContentValues v=new ContentValues();v.put("radio",radio);v.put("device",device);v.put("node",node);v.put("lat",lat);v.put("lon",lon);v.put("reported_at",time);db.insertWithOnConflict("map_positions",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        }
        for(int i=0;i<hs.length();i++){JSONObject h=hs.getJSONObject(i);String radio=h.getString("radio"),device=h.getString("device"),node=h.getString("node");long time=h.getLong("time");if(!validIdentity(radio,device,node)||time<=0)continue;
            ContentValues v=new ContentValues();v.put("radio",radio);v.put("device",device);v.put("node",node);v.put("heard_at",time);db.insertWithOnConflict("map_heard",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        }
        for(int i=0;i<items.length();i++)put(items.getJSONObject(i),true);
        if(fs!=null)for(int i=0;i<fs.length();i++){JSONObject f=fs.getJSONObject(i);ContentValues v=new ContentValues();v.put("item",f.getString("item"));v.put("radio",f.getString("radio"));v.put("device",f.getString("device"));v.put("node",f.getString("node"));v.put("inside",f.optInt("inside",0)!=0?1:0);db.insertWithOnConflict("map_geofence_state",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    }
    private static boolean validIdentity(String radio,String device,String node){return (radio.equals("mc")||radio.equals("mt"))&&device.length()>0&&device.length()<160&&node.length()>0&&node.length()<160;}
}
