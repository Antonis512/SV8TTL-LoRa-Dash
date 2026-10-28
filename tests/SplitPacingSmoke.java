package org.overmesh.android;

public final class SplitPacingSmoke {
    static void check(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    public static void main(String[] args) {
        SplitPacing p=new SplitPacing();
        check(p.ready(0,false,"",0,false,4000,5000),"First part is ready");p.sent();
        check(!p.ready(10000,false,"sent_to_radio",0,false,4000,5000),"Transport enqueue must not release next part");
        check(!p.ready(11000,false,"delivered",0,true,4000,5000),"Wait for active radio retry window");
        check(!p.ready(12000,false,"delivered",0,false,4000,5000),"Interval starts at result");
        check(!p.ready(15999,false,"delivered",0,false,4000,5000),"Full interval must elapse");
        check(p.ready(16000,false,"delivered",0,false,4000,5000),"Part released after interval");p.sent();
        check(!p.ready(17000,true,"sent_radio",0,false,4000,5000),"Channel acceptance starts observation");
        check(!p.ready(21999,true,"sent_radio",0,false,4000,5000),"Channel still observing");
        check(!p.ready(22000,true,"sent_radio",0,false,4000,5000)&&p.decisionRequired,"Observation expiry requires user decision");
        check(!p.ready(26000,true,"sent_radio",0,false,4000,5000),"Unconfirmed channel must not advance without permission");
        p.retry(26000,4000);check(!p.ready(29999,true,"",0,false,4000,5000),"Explicit retry still respects interval");
        check(p.ready(30000,true,"",0,false,4000,5000),"Retry releases only after interval");p.sent();
        check(!p.ready(31000,true,"sent_radio",1,false,4000,5000),"Heard repeat starts interval");
        check(p.ready(35000,true,"sent_radio",1,false,4000,5000),"Heard repeat respects interval");p.sent();
        check(!p.ready(36000,false,"failed: timeout",0,false,4000,5000)&&p.decisionRequired,"Failure pauses for decision");
        check(!p.ready(40000,false,"failed: timeout",0,false,4000,5000),"Failure cannot advance automatically");
        p.retry(40000,4000);check(p.ready(44000,false,"",0,false,4000,5000),"Explicit retry unblocks dispatch");p.sent();
        check(!p.ready(45000,false,"unconfirmed: No acknowledgment",0,true,4000,5000)&&!p.decisionRequired,"Do not prompt during automatic retries");
        check(!p.ready(46000,false,"unconfirmed: No acknowledgment",0,false,4000,5000)&&p.decisionRequired,"Exhausted retries require decision");
        System.out.println("Split pacing checks passed");
    }
}
