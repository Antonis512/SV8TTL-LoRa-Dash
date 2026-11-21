package org.overmesh.android;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Compact contact snapshot and explicit favorite/assignment transitions, independent of radio connectivity. */
final class ContactFavorites {
    static JSONObject snapshot(JSONArray contacts) throws Exception {
        JSONObject result=new JSONObject();
        for(int i=0;i<contacts.length();i++){
            JSONObject c=contacts.getJSONObject(i);
            if(!c.optString("kind","contact").equals("contact")||c.optLong("deletedAt")!=0)continue;
            JSONArray nodes=new JSONArray(),assigned=c.optJSONArray("nodes");
            if(assigned!=null)for(int j=0;j<assigned.length();j++){
                JSONObject n=assigned.getJSONObject(j);String radio=n.optString("radio"),id=n.optString("id");
                if((radio.equals("mt")&&id.matches("![0-9a-fA-F]{8}"))||(radio.equals("mc")&&id.matches("[0-9a-fA-F]{12}")))nodes.put(radio+":"+id.toLowerCase(java.util.Locale.ROOT));
            }
            result.put(c.getString("id"),new JSONObject().put("favorite",c.optBoolean("favorite")).put("nodes",nodes));
        }
        return result;
    }
    static Map<String,Boolean> changes(JSONObject before,JSONObject after) throws Exception {
        Map<String,Boolean> result=new LinkedHashMap<>();
        java.util.Iterator<String> ids=after.keys();
        while(ids.hasNext()){
            String id=ids.next();JSONObject next=after.getJSONObject(id),old=before.optJSONObject(id);
            boolean value=next.optBoolean("favorite"),previous=old!=null&&old.optBoolean("favorite");
            java.util.Set<String> prior=new java.util.HashSet<>();
            if(old!=null){JSONArray nodes=old.getJSONArray("nodes");for(int i=0;i<nodes.length();i++)prior.add(nodes.getString(i));}
            JSONArray nodes=next.getJSONArray("nodes");
            for(int i=0;i<nodes.length();i++){String node=nodes.getString(i);if(value!=previous||(value&&!prior.contains(node)))result.put(node,value);}
        }
        return result;
    }
}
