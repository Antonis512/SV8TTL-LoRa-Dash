package org.overmesh.android;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** One composer owns one draft. Channel and DM selections share each radio's set. */
final class ChainDestinations {
    private final Map<String,Set<String>> edited=new LinkedHashMap<>(),saved=new LinkedHashMap<>();
    void select(String radio,String destination,boolean checked) {
        Set<String> set=edited.computeIfAbsent(radio,k->new LinkedHashSet<>());
        if(checked)set.add(destination);else set.remove(destination);
    }
    boolean selected(String radio,String destination){return edited.containsKey(radio)&&edited.get(radio).contains(destination);}
    void save(String radio){saved.put(radio,new LinkedHashSet<>(edited.getOrDefault(radio,new LinkedHashSet<>())));}
    boolean hasUnsaved(){for(String radio:edited.keySet())if(!edited.get(radio).equals(saved.getOrDefault(radio,new LinkedHashSet<>())))return true;return false;}
    Map<String,Set<String>> snapshot(){Map<String,Set<String>> copy=new LinkedHashMap<>();for(String radio:saved.keySet())copy.put(radio,new LinkedHashSet<>(saved.get(radio)));return copy;}
}
