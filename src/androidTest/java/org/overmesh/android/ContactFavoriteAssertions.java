package org.overmesh.android;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;

final class ContactFavoriteAssertions {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void run() throws Exception {
        JSONObject c=new JSONObject().put("id","person").put("kind","contact").put("favorite",false)
            .put("nodes",new JSONArray().put(new JSONObject().put("radio","mt").put("id","!abcdef12"))
                .put(new JSONObject().put("radio","mc").put("id","abcdefabcdef")));
        JSONObject before=ContactFavorites.snapshot(new JSONArray().put(c));
        c.put("favorite",true);JSONObject after=ContactFavorites.snapshot(new JSONArray().put(c));
        Map<String,Boolean> changes=ContactFavorites.changes(before,after);
        check(changes.size()==2&&changes.get("mt:!abcdef12")&&changes.get("mc:abcdefabcdef"),"Favorite Contact fans out to MT and MC");
        check(ContactFavorites.changes(after,after).isEmpty(),"Reading Contact again must not overwrite official-app changes");
        before=after;c.getJSONArray("nodes").put(new JSONObject().put("radio","mt").put("id","!12345678"));
        after=ContactFavorites.snapshot(new JSONArray().put(c));changes=ContactFavorites.changes(before,after);
        check(changes.size()==1&&changes.get("mt:!12345678"),"New assignment inherits favorite");
        before=after;c.put("favorite",false);after=ContactFavorites.snapshot(new JSONArray().put(c));changes=ContactFavorites.changes(before,after);
        check(changes.size()==3&&!changes.containsValue(true),"Unfavorite Contact removes favorite from all assigned nodes");
        before=after;c.getJSONArray("nodes").put(new JSONObject().put("radio","mc").put("id","123456123456"));
        after=ContactFavorites.snapshot(new JSONArray().put(c));check(ContactFavorites.changes(before,after).isEmpty(),"New assignment to nonfavorite Contact leaves radio state alone");
        c.put("favorite",true);before=ContactFavorites.snapshot(new JSONArray().put(c));c.put("deletedAt",1);
        after=ContactFavorites.snapshot(new JSONArray().put(c));check(ContactFavorites.changes(before,after).isEmpty(),"Deleting Contact does not modify radio favorites");
        c.remove("deletedAt");c.getJSONArray("nodes").put(new JSONObject().put("radio","mc").put("id","heard-name:Unknown"));
        after=ContactFavorites.snapshot(new JSONArray().put(c));check(ContactFavorites.changes(new JSONObject(),after).size()==4,"Do not send unverified name-only assignments");
    }
}
