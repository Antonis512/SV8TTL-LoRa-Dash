package org.overmesh.android;

/** One app-wide MT allowance, shared by every destination and UI entry point. Main thread only. */
final class TracerouteAllowance {
    static final long INTERVAL_MS=30000L;
    private long until,request;
    private String receiver="";
    long remaining(long elapsedNow){return Math.max(0,until-elapsedNow);}
    boolean claim(long elapsedNow,String source,long token) {
        if(remaining(elapsedNow)>0)return false;
        until=elapsedNow+INTERVAL_MS;receiver=source;request=token;return true;
    }
    void submissionFailed(String source,long token) {
        if(receiver.equals(source)&&request==token){until=0;receiver="";request=0;}
    }
}
