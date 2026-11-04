package org.overmesh.android;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** One composer owns one draft. Channel and DM selections share each radio's set. */
final class ChainDestinations {
    static final int MAX_DESTINATIONS=5;
    private final Map<String,Set<String>> edited=new LinkedHashMap<>(),saved=new LinkedHashMap<>();
    void select(String radio,String destination,boolean checked) {
        if(checked&&!selected(radio,destination)&&edited.values().stream().mapToInt(Set::size).sum()>=MAX_DESTINATIONS)
            throw new IllegalArgumentException("A chain can include at most 5 destinations.");
        Set<String> set=edited.computeIfAbsent(radio,k->new LinkedHashSet<>());
        if(checked)set.add(destination);else set.remove(destination);
    }
    boolean selected(String radio,String destination){return edited.containsKey(radio)&&edited.get(radio).contains(destination);}
    void addSaved(java.util.List<String[]> targets) {
        Set<java.util.List<String>> combined=new LinkedHashSet<>();
        for(Map.Entry<String,Set<String>> radio:edited.entrySet())for(String id:radio.getValue())combined.add(java.util.Arrays.asList(radio.getKey(),id));
        for(String[] target:targets)combined.add(java.util.Arrays.asList(target[0],target[1]));
        if(combined.size()>MAX_DESTINATIONS)throw new IllegalArgumentException("This set would exceed the limit of 5 destinations. No destinations were added.");
        for(String[] target:targets){select(target[0],target[1],true);save(target[0]);}
    }
    void save(String radio){saved.put(radio,new LinkedHashSet<>(edited.getOrDefault(radio,new LinkedHashSet<>())));}
    boolean hasUnsaved(){for(String radio:edited.keySet())if(!edited.get(radio).equals(saved.getOrDefault(radio,new LinkedHashSet<>())))return true;return false;}
    Map<String,Set<String>> snapshot(){Map<String,Set<String>> copy=new LinkedHashMap<>();for(String radio:saved.keySet())copy.put(radio,new LinkedHashSet<>(saved.get(radio)));return copy;}
}
