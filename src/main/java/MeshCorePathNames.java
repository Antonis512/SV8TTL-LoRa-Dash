package org.overmesh.android;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Resolve short route identifiers only against the selected radio's repeaters. */
final class MeshCorePathNames {
    static String resolve(String hash, Map<String,String> repeaters) {
        if(hash==null||!hash.matches("(?i)(?:[0-9a-f]{2}){1,3}"))return "Unknown repeater";
        String prefix=hash.toLowerCase(Locale.ROOT);
        Map<String,String> matches=new TreeMap<>();
        for(Map.Entry<String,String> item:repeaters.entrySet()) {
            String id=item.getKey().toLowerCase(Locale.ROOT);
            if(id.startsWith(prefix)) {
                String name=item.getValue()==null?"":item.getValue();
                if(!matches.containsKey(id)||!name.isEmpty())matches.put(id,name);
            }
        }
        if(matches.isEmpty())return "Unknown repeater";
        if(matches.size()==1) {
            Map.Entry<String,String> item=matches.entrySet().iterator().next();
            return item.getValue().isEmpty()?item.getKey():item.getValue();
        }
        java.util.List<String> labels=new java.util.ArrayList<>();
        for(Map.Entry<String,String> item:matches.entrySet())
            labels.add(item.getValue().isEmpty()?item.getKey():item.getValue()+" ["+item.getKey()+"]");
        return "Multiple repeaters: "+String.join(" / ",labels);
    }
}
