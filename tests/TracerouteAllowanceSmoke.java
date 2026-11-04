package org.overmesh.android;
public final class TracerouteAllowanceSmoke {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        TracerouteAllowance allowance=new TracerouteAllowance();
        check(allowance.remaining(0)==0,"First trace is immediately available");
        check(allowance.claim(0,"mt-a",1),"Claim at boot time zero");
        check(!allowance.claim(1,"mt-b",2),"Different receiver and destination cannot bypass global window");
        check(allowance.remaining(1000)==29000,"Rejected taps do not restart countdown");
        check(!allowance.claim(29999,"mt-a",3),"Blocked just before boundary");
        check(allowance.claim(30000,"mt-a",3),"Allowed exactly at boundary, independent of earlier response");
        allowance.submissionFailed("mt-a",1);
        check(allowance.remaining(30001)==29999,"Late failure from old request cannot reset newer claim");
        allowance.submissionFailed("mt-b",3);
        check(allowance.remaining(30001)==29999,"Different connection cannot release claim");
        allowance.submissionFailed("mt-a",3);
        check(allowance.remaining(30001)==0,"Failed submission releases current claim");
        check(allowance.claim(30001,"mt-a",4),"Retry permitted after failed submission");
        check(allowance.remaining(60001)==0,"Cooldown expires without a response");
        System.out.println("Shared traceroute cooldown, exact boundary, failed submissions and stale failures passed");
    }
}
