package org.overmesh.android;
import java.util.*;
public class MeshCorePathNamesSmoke {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        Map<String,String> repeaters=new LinkedHashMap<>();
        repeaters.put("08abcdef1234","Hill");repeaters.put("08ABCDEF1234","Hill");
        check(MeshCorePathNames.resolve("08",repeaters).equals("Hill"),"Same identity must not collide with itself");
        repeaters.put("08bbef123456","Valley");
        check(MeshCorePathNames.resolve("08",repeaters).equals("Multiple repeaters: Hill [08abcdef1234] / Valley [08bbef123456]"),"Show every distinct candidate");
        check(MeshCorePathNames.resolve("08AB",repeaters).equals("Hill"),"Use both bytes and ignore hex case");
        check(MeshCorePathNames.resolve("08ABCD",repeaters).equals("Hill"),"Use all three bytes");
        check(MeshCorePathNames.resolve("07",repeaters).equals("Unknown repeater"),"Unknown hash");
        check(MeshCorePathNames.resolve("",repeaters).equals("Unknown repeater"),"Empty hash must not match everything");
        check(MeshCorePathNames.resolve("0",repeaters).equals("Unknown repeater"),"Reject incomplete byte");
        System.out.println("MC path names passed: duplicate identities, collisions, hash widths, case and unknown hashes.");
    }
}
