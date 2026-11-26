package org.overmesh.android;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;

/** Phone-local people, including embedded thumbnails and portable node associations. */
final class ContactsStore {
    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS contact_items(id TEXT PRIMARY KEY,body TEXT NOT NULL,edited INTEGER NOT NULL)");
    }
    static JSONArray all(ChatStore store) throws Exception {
        JSONArray items=new JSONArray();
        try(Cursor cursor=store.getReadableDatabase().rawQuery("SELECT body FROM contact_items ORDER BY edited DESC,id",null)) {
            while(cursor.moveToNext())items.put(new JSONObject(cursor.getString(0)));
        }
        return items;
    }
    static void validate(JSONObject item) throws Exception {
        if(!item.getString("id").matches("[a-zA-Z0-9_-]{1,100}"))throw new IllegalArgumentException("Invalid contact identifier");
        String kind=item.optString("kind","contact");
        if(!kind.equals("contact")&&!kind.equals("category"))throw new IllegalArgumentException("Invalid contact type");
        if(item.toString().length()>600000)throw new IllegalArgumentException("Contact is too large");
        if(item.optLong("edited")<0||item.optLong("deletedAt")<0)throw new IllegalArgumentException("Invalid contact date");
        boolean named=false;
        for(String field:new String[]{"displayName","name","surname","alias","organization","role","title"}) {
            if(item.has(field)&&!(item.get(field) instanceof String))throw new IllegalArgumentException("Invalid "+field);
            String value=item.optString(field);if(value.length()>500)throw new IllegalArgumentException("Contact name is too long");
            if(java.util.Arrays.asList("displayName","name","surname","alias","title").contains(field)&&!value.trim().isEmpty())named=true;
        }
        if(!named)throw new IllegalArgumentException("Enter a name, alias or display name");
        String photo=item.optString("photo");
        if(!photo.isEmpty()&&(!photo.startsWith("data:image/jpeg;base64,")||photo.length()>400000))throw new IllegalArgumentException("Choose a smaller JPEG profile photo");
        for(String field:new String[]{"callsigns","phones","emails","addresses","custom","notes","nodes","categories","logs"}) {
            if(item.has(field)&&!(item.get(field) instanceof JSONArray))throw new IllegalArgumentException("Invalid "+field);
            JSONArray entries=item.optJSONArray(field);if(entries!=null&&entries.length()>500)throw new IllegalArgumentException("Too many "+field);
        }
        for(String field:new String[]{"callsigns","phones","emails","addresses","custom"}){
            JSONArray rows=item.optJSONArray(field);if(rows==null)continue;
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);
                if(!(row.get("value") instanceof String)||!(row.opt("label") instanceof String)||row.getString("value").length()>20000||row.getString("label").length()>500)throw new IllegalArgumentException("Invalid "+field+" entry");
                if(field.equals("custom")&&!java.util.Arrays.asList("text","number","date","url").contains(row.optString("type","text")))throw new IllegalArgumentException("Invalid custom field type");
            }
        }
        for(String field:new String[]{"categories","logs"}){JSONArray rows=item.optJSONArray(field);if(rows!=null)for(int i=0;i<rows.length();i++)if(!(rows.get(i) instanceof String))throw new IllegalArgumentException("Invalid "+field+" reference");}
        JSONArray notes=item.optJSONArray("notes");if(notes!=null)for(int i=0;i<notes.length();i++){JSONObject n=notes.getJSONObject(i);if(!(n.get("text") instanceof String)||n.getLong("date")<0||n.getString("text").length()>100000)throw new IllegalArgumentException("Invalid note");}
        if(item.has("androidLink")&&!item.isNull("androidLink")){JSONObject link=item.getJSONObject("androidLink");if(!link.optString("uri").startsWith("content://"))throw new IllegalArgumentException("Invalid Android contact link");}
        JSONArray nodes=item.optJSONArray("nodes");
        if(nodes!=null)for(int i=0;i<nodes.length();i++) {
            JSONObject n=nodes.getJSONObject(i);
            if(!java.util.Arrays.asList("mc","mt").contains(n.optString("radio"))||n.optString("id").isEmpty())throw new IllegalArgumentException("Invalid assigned node");
        }
    }
    static void merge(ChatStore store,JSONArray items,boolean newerOnly) throws Exception {
        if(items.length()>10000)throw new IllegalArgumentException("Too many contacts");
        // Validate the entire batch before changing any record.
        for(int i=0;i<items.length();i++)validate(items.getJSONObject(i));
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try {
            for(int i=0;i<items.length();i++) {
                JSONObject item=items.getJSONObject(i);String id=item.getString("id");
                try(Cursor c=db.rawQuery("SELECT edited,body FROM contact_items WHERE id=?",new String[]{id})) {
                    if(c.moveToFirst()) {
                        if(newerOnly&&c.getLong(0)>=item.optLong("edited"))continue;
                        preserveNodeNames(item,new JSONObject(c.getString(1)));
                    }
                }
                ContentValues row=new ContentValues();row.put("id",id);row.put("body",item.toString());row.put("edited",item.optLong("edited"));
                if(db.insertWithOnConflict("contact_items",null,row,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new IllegalStateException("Could not save contact");
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private static boolean sameNode(String radio,String a,String b) {
        if(a.equalsIgnoreCase(b))return true;
        return radio.equals("mc")&&((a.length()==64&&b.length()==12&&a.substring(0,12).equalsIgnoreCase(b))
                ||(b.length()==64&&a.length()==12&&b.substring(0,12).equalsIgnoreCase(a)));
    }
    private static void preserveNodeNames(JSONObject incoming,JSONObject saved) throws Exception {
        JSONArray nodes=incoming.optJSONArray("nodes"),old=saved.optJSONArray("nodes");
        if(nodes==null||old==null)return;
        for(int i=0;i<nodes.length();i++) {
            JSONObject n=nodes.getJSONObject(i);String radio=n.optString("radio");
            for(int j=0;j<old.length();j++) {
                JSONObject previous=old.getJSONObject(j);long time=previous.optLong("nameObservedAt");
                if(time>0&&time>=n.optLong("nameObservedAt")&&radio.equals(previous.optString("radio"))
                        &&sameNode(radio,n.optString("id"),previous.optString("id"))) {
                    n.put("name",previous.optString("name")).put("nameObservedAt",time);
                }
            }
        }
    }
    static void updateNodeName(ChatStore store,String radio,String id,String name,long observedAt) throws Exception {
        if(name.trim().isEmpty()||observedAt<0||observedAt>System.currentTimeMillis()+300000)return;
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try {
        JSONArray changes=new JSONArray(),items=all(store);
        for(int i=0;i<items.length();i++){
            JSONObject person=items.getJSONObject(i);if(person.optLong("deletedAt")!=0)continue;
            JSONArray nodes=person.optJSONArray("nodes");if(nodes==null)continue;boolean changed=false;
            for(int j=0;j<nodes.length();j++){JSONObject n=nodes.getJSONObject(j);
                boolean same=sameNode(radio,n.optString("id"),id);
                long previous=n.optLong("nameObservedAt");
                // Unknown-age snapshots can fill an empty assignment, never undo a name.
                // Equal timestamps retain the first name; a later matching report advances freshness.
                if(radio.equals(n.optString("radio"))&&same
                        &&(observedAt>previous||(observedAt==0&&previous==0&&n.optString("name").isEmpty()))) {
                    n.put("name",name).put("nameObservedAt",observedAt);changed=true;
                }
            }
            if(changed)changes.put(person.put("edited",Math.max(System.currentTimeMillis(),person.optLong("edited")+1)));
        }
        if(changes.length()>0)merge(store,changes,false);
        db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    static void forgetNode(ChatStore store,String radio,String id) throws Exception {
        JSONArray changes=new JSONArray(),items=all(store);
        for(int i=0;i<items.length();i++){
            JSONObject item=items.getJSONObject(i);JSONArray nodes=item.optJSONArray("nodes");if(nodes==null)continue;
            JSONArray keep=new JSONArray();for(int j=0;j<nodes.length();j++){JSONObject n=nodes.getJSONObject(j);if(!radio.equals(n.optString("radio"))||!id.equals(n.optString("id")))keep.put(n);}
            if(keep.length()!=nodes.length())changes.put(item.put("nodes",keep).put("edited",System.currentTimeMillis()));
        }
        merge(store,changes,false);
    }
}
