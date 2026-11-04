package org.overmesh.android;

/** Elapsed time survives app restarts; wall time is only a fallback after a phone reboot. */
final class SendCooldown {
    static final long DURATION_MS=5*60*1000L;
    static long remaining(long wallNow,long elapsedNow,int boot,long wallEnd,long elapsedEnd,int savedBoot) {
        if(wallEnd<=0)return 0;
        long remaining=boot>=0&&boot==savedBoot?elapsedEnd-elapsedNow:wallEnd-wallNow;
        return Math.max(0,Math.min(DURATION_MS,remaining));
    }
}
