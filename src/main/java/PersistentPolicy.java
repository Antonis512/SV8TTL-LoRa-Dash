package org.overmesh.android;

/** Timing uses monotonic time; expiry uses wall time so it also elapses while the app is closed. */
final class PersistentPolicy {
    static final long MIN_INTERVAL=120000, COMPOSER_WAIT=45000, FIX_AGE=30000;
    static boolean fresh(long now,long captured,float accuracy) {
        return captured>0 && now>=captured && now-captured<=FIX_AGE && Float.isFinite(accuracy) && accuracy>=0 && accuracy<=25;
    }
    static boolean moved(double metres) {return Double.isFinite(metres)&&metres>=100;}
    static boolean exhausted(int attempts,int maxRetries) {return maxRetries>=0&&attempts>=1L+maxRetries;}
    static String success(String status,boolean meshcore,int repeats) {
        if("delivered".equals(status))return "Delivered";
        if(!meshcore&&("delivered_mesh".equals(status)||"relayed".equals(status)))return "Reached mesh";
        if(meshcore&&repeats>0)return "Heard by repeater";
        return "";
    }
}
