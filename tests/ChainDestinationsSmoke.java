package org.overmesh.android;
public final class ChainDestinationsSmoke {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        ChainDestinations d=new ChainDestinations();
        d.select("mc1","channel:0",true);d.select("mc1","dm:alice",true);
        check(d.selected("mc1","channel:0")&&d.selected("mc1","dm:alice"),"Switching type preserves both sets");
        check(d.hasUnsaved()&&d.snapshot().isEmpty(),"Draft not committed before Save");d.save("mc1");
        d.select("mt1","dm:alice",true);d.save("mt1");
        check(d.snapshot().get("mc1").size()==2&&d.snapshot().get("mt1").size()==1,"Same peer scoped to own radio");
        d.select("mc1","dm:bob",true);d.select("mc1","dm:bob",false);
        check(!d.hasUnsaved()&&d.selected("mc1","dm:alice"),"Search view changes do not discard hidden checks");
        d.select("mc1","channel:0",false);d.save("mc1");
        check(d.snapshot().get("mc1").size()==1,"Revisiting radio replaces its saved selections");
        d.snapshot().clear();check(d.snapshot().size()==2,"Snapshot cannot mutate selection");
        check(new ChainDestinations().snapshot().isEmpty(),"New or cancelled chain cannot leak selection globally");
        System.out.println("Chain destination selection checks passed");
        d.select("mt1","dm:bob",true);d.select("mt1","dm:charlie",true);d.select("mc1","channel:1",true);
        try{d.select("mc2","channel:1",true);throw new AssertionError("Sixth destination accepted");}catch(IllegalArgumentException expected){}
        check(!d.selected("mc2","channel:1"),"Rejected selection leaves state unchanged");
        d.select("mc1","channel:1",false);d.select("mc2","channel:1",true);
        d.save("mt1");d.save("mc1");d.save("mc2");
        try{d.addSaved(java.util.Arrays.asList(new String[]{"mt1","dm:alice"},new String[]{"mt2","dm:new"}));throw new AssertionError("Oversize saved set accepted");}catch(IllegalArgumentException expected){}
        check(!d.selected("mt2","dm:new")&&!d.hasUnsaved(),"Saved set rejected atomically");
        d.addSaved(java.util.Collections.singletonList(new String[]{"mt1","dm:alice"}));
        check(d.snapshot().values().stream().mapToInt(java.util.Set::size).sum()==5,"Duplicate destinations do not consume another slot");
        System.out.println("Five-destination limit and atomic saved-set checks passed");
    }
}
