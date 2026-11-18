package org.overmesh.android;

/** Circle/polygon intersection in metres; polygon points are [longitude, latitude]. */
final class GeofenceGeometry {
    private static final double EARTH=6371000;
    static boolean overlaps(double[][][] rings,double lat,double lon,double radius) {
        if(rings.length==0)return false;
        boolean inside=inside(rings[0],lat,lon);
        for(int i=1;i<rings.length;i++)if(inside(rings[i],lat,lon))inside=false;
        if(inside)return true;
        for(double[][] ring:rings)for(int i=0,j=ring.length-1;i<ring.length;j=i++)
            if(distanceToSegment(lat,lon,ring[j][1],ring[j][0],ring[i][1],ring[i][0])<=radius+0.001)return true;
        return false;
    }
    private static double longitude(double delta){return ((delta+180)%360+360)%360-180;}
    private static boolean inside(double[][] ring,double lat,double lon) {
        if(ring.length==0)return false;
        boolean result=false;double anchor=ring[0][0],query=longitude(lon-anchor);
        for(int i=0,j=ring.length-1;i<ring.length;j=i++){
            // Unwrap the polygon around its own first vertex, not around a remote query.
            double ax=longitude(ring[i][0]-anchor),bx=longitude(ring[j][0]-anchor);
            double ay=ring[i][1],by=ring[j][1];
            if((ay>lat)!=(by>lat)&&query<(bx-ax)*(lat-ay)/(by-ay)+ax)result=!result;
        }
        return result;
    }
    private static double angular(double a,double b,double c,double d){
        double x=Math.toRadians(c-a),y=Math.toRadians(longitude(d-b));
        double h=Math.sin(x/2)*Math.sin(x/2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.sin(y/2)*Math.sin(y/2);
        return 2*Math.asin(Math.sqrt(Math.max(0,Math.min(1,h))));
    }
    private static double bearing(double a,double b,double c,double d){
        double x=Math.toRadians(a),y=Math.toRadians(c),delta=Math.toRadians(longitude(d-b));
        return Math.atan2(Math.sin(delta)*Math.cos(y),Math.cos(x)*Math.sin(y)-Math.sin(x)*Math.cos(y)*Math.cos(delta));
    }
    private static double distanceToSegment(double lat,double lon,double a,double b,double c,double d){
        double length=angular(a,b,c,d),from=angular(a,b,lat,lon),difference=bearing(a,b,lat,lon)-bearing(a,b,c,d);
        double along=Math.atan2(Math.sin(from)*Math.cos(difference),Math.cos(from));
        if(length>0&&along>=0&&along<=length)
            return EARTH*Math.asin(Math.min(1,Math.abs(Math.sin(from)*Math.sin(difference))));
        return EARTH*Math.min(from,angular(c,d,lat,lon));
    }
    // Stored states retain compatibility: 0 outside, 1 exact inside, 2 possible overlap.
    static String event(boolean known,int old,int current){
        if(current==2)return !known||old==0?"possible entrance into":null;
        if(current==1)return !known?"appeared in":old!=1?"entered":null;
        return known&&old!=0?(old==2?"uncertainty area left":"left"):null;
    }
}
