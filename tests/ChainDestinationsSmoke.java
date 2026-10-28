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
    }
}
