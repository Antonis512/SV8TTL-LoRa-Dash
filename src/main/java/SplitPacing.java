package org.overmesh.android;

/** Monotonic pacing after a result, rather than after enqueueing a transport write. */
final class SplitPacing {
    boolean awaitingResult;
    boolean decisionRequired;
    long nextSend, observationDeadline;
    void sent(){awaitingResult=true;decisionRequired=false;observationDeadline=0;}
    void retry(long now,long interval){awaitingResult=false;decisionRequired=false;observationDeadline=0;nextSend=now+interval;}
    boolean ready(long now,boolean channelObservation,String result,int repeats,boolean retrying,long interval,long observation) {
        if(decisionRequired)return false;
        if(awaitingResult) {
            if(retrying)return false;
            boolean terminal=result.startsWith("failed")||result.startsWith("unconfirmed")||result.equals("delivered")||result.equals("delivered_mesh");
            if(channelObservation&&result.equals("sent_radio")) {
                if(observationDeadline==0)observationDeadline=now+observation;
                terminal=repeats>0||now>=observationDeadline;
            }
            if(!terminal)return false;
            awaitingResult=false;nextSend=now+interval;
            if(result.startsWith("failed")||result.startsWith("unconfirmed")||(channelObservation&&result.equals("sent_radio")&&repeats<=0)) {
                decisionRequired=true;return false;
            }
        }
        return now>=nextSend;
    }
}
