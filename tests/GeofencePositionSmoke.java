package org.overmesh.android;

/** Synthetic coordinates: these do not establish any reported node's real location. */
public final class GeofencePositionSmoke {
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static double center(double value,int bits){long width=1L<<(32-bits);return (Math.floorDiv(Math.round(value*1e7),width)*width+width/2)/1e7;}
    static NodePosition position(double lat,double lon,int bits,String source){return new NodePosition(lat,lon,0,0,0,bits,source);}
    public static void main(String[] args){
        for(int bits=10;bits<=19;bits++)for(double lat:new double[]{38,-33.8688}){
            double x=center(lat,bits),y=center(23.74,bits);
            check(position(x,y,0,"mt_live").canTriggerGeofence("mt")&&position(x,y,0,"mt_live").uncertaintyRadius("mt")>0,"Missing precision uses the visible uncertainty circle: "+bits);
            check(position(x,y,bits,"mt_live").canTriggerGeofence("mt")&&position(x,y,bits,"mt_live").uncertaintyRadius("mt")>0,"Explicit coarse coordinates permit possible entrance alerts");
            check(position(x,y,32,"mt_live").canTriggerGeofence("mt"),"Explicit full precision overrides the grid heuristic");
            check(position(x,y,0,"mc_snapshot").canTriggerGeofence("mc"),"MT grid inference must not apply to MC");
            check(position(x,y,0,"legacy").uncertaintyRadius("mt")==position(x,y,bits,"mt_live").uncertaintyRadius("mt"),"Legacy MT positions use the same radius");
            check(position(x,y,32,"mt_live").uncertaintyRadius("mt")==0&&position(x,y,0,"mc_snapshot").uncertaintyRadius("mc")==0,"Full precision and MC have no inferred radius");
        }
        check(position(38,23.74,0,"mt_live").canTriggerGeofence("mt"),"Ordinary coordinates without precision retain existing behavior");
        check(position(center(38,13),23.74,0,"mt_live").canTriggerGeofence("mt"),"A grid match on just one axis is insufficient");
        check(!position(38,23.74,32,"mt_preserved").canTriggerGeofence("mt"),"Preserved old precise coordinates cannot create new crossings");
        check(!position(Double.NaN,23,32,"mt_live").canTriggerGeofence("mt"),"Invalid coordinates cannot trigger crossings");
        double[][] square={{23,38},{23.01,38},{23.01,38.01},{23,38.01},{23,38}};
        double[][][] polygon={square};
        check(!GeofenceGeometry.overlaps(polygon,38.005,22.999,0),"Centre outside");
        check(GeofenceGeometry.overlaps(polygon,38.005,22.999,100),"Partial circle overlap triggers even with centre outside");
        check(!GeofenceGeometry.overlaps(polygon,38.005,22.999,50),"Separated circle remains outside");
        check(GeofenceGeometry.overlaps(polygon,37.999,22.999,150),"Corner overlap");
        check(!GeofenceGeometry.overlaps(polygon,37.999,22.999,100),"Circle short of corner stays outside");
        check(GeofenceGeometry.overlaps(polygon,38.005,23.005,0),"Exact inside preserved");
        check(GeofenceGeometry.overlaps(polygon,38.005,23.005,5000),"Circle containing entire fence overlaps");
        double[][][] equator={{{23,0},{23.01,0},{23.01,0.01},{23,0.01},{23,0}}};
        double touching=6371000*Math.toRadians(0.001);
        check(GeofenceGeometry.overlaps(equator,-0.001,23.005,touching),"Exact tangency counts");
        check(!GeofenceGeometry.overlaps(equator,-0.001,23.005,touching-0.1),"Small gap does not count");
        double[][][] dateLine={{{179.99,38},{-179.99,38},{-179.99,38.01},{179.99,38.01},{179.99,38}}};
        check(GeofenceGeometry.overlaps(dateLine,38.005,179.989,100),"Date line edge overlap");
        check(!GeofenceGeometry.overlaps(dateLine,38.005,0,100),"Opposite side of globe is outside");
        double[][][] withHole={square,{{23.002,38.002},{23.008,38.002},{23.008,38.008},{23.002,38.008},{23.002,38.002}}};
        check(!GeofenceGeometry.overlaps(withHole,38.005,23.005,20),"Circle wholly in a hole is outside");
        check(GeofenceGeometry.overlaps(withHole,38.005,23.005,300),"Circle touching the hole boundary overlaps fence");
        check("possible entrance into".equals(GeofenceGeometry.event(false,0,2)),"First overlap alerts");
        check("possible entrance into".equals(GeofenceGeometry.event(true,0,2)),"Outside to overlap alerts");
        check(GeofenceGeometry.event(true,2,2)==null,"Repeated overlap does not alert");
        check("uncertainty area left".equals(GeofenceGeometry.event(true,2,0)),"Separation does not claim confirmed node exit");
        check("entered".equals(GeofenceGeometry.event(true,2,1)),"Precise inside report confirms entry");
        check(GeofenceGeometry.event(true,1,2)==null,"Reduced certainty while overlapping is not a new entrance");
        check("left".equals(GeofenceGeometry.event(true,1,0)),"Exact exit preserved");
        System.out.println("Geofence position checks passed");
    }
}
