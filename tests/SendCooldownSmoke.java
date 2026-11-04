package org.overmesh.android;
public final class SendCooldownSmoke {
    static void check(long actual,long expected){if(actual!=expected)throw new AssertionError(actual+" != "+expected);}
    public static void main(String[] args) {
        check(SendCooldown.remaining(1000,100,7,0,0,-1),0);
        check(SendCooldown.remaining(1000,100,7,301000,300100,7),300000);
        check(SendCooldown.remaining(9999999,60100,7,301000,300100,7),240000);
        check(SendCooldown.remaining(1,60100,7,301000,300100,7),240000);
        check(SendCooldown.remaining(1000,300100,7,301000,300100,7),0);
        check(SendCooldown.remaining(61000,100,8,301000,300100,7),240000);
        check(SendCooldown.remaining(1000,100,8,9999999,300100,7),300000);
        check(SendCooldown.remaining(400000,100,8,301000,300100,7),0);
        System.out.println("Cooldown handles app restarts, clock changes, expiry and phone reboot");
    }
}
