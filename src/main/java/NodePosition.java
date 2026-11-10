package org.overmesh.android;

/** Immutable reported coordinates and provenance. All times are epoch milliseconds. */
final class NodePosition {
    final double latitude,longitude;
    final long reportTime,fixTime,receivedTime;
    final int precisionBits;
    final String source;
    NodePosition(double lat,double lon,long report,long fix,long received,int bits,String source) {
        boolean valid=valid(lat,lon);
        latitude=valid?lat:Double.NaN;longitude=valid?lon:Double.NaN;
        long limit=System.currentTimeMillis()+300000;
        reportTime=report>0&&report<=limit?report:0;fixTime=fix>0&&fix<=limit?fix:0;
        receivedTime=received>0&&received<=limit?received:0;
        precisionBits=bits>=0&&bits<=32?bits:0;this.source=source==null?"legacy":source;
    }
    static boolean valid(double lat,double lon) {
        return Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180&&!(lat==0&&lon==0);
    }
    boolean valid(){return valid(latitude,longitude);}
    long time(){return fixTime>0?fixTime:reportTime;}
    static NodePosition unknown(double lat,double lon){return new NodePosition(lat,lon,0,0,0,0,"legacy");}
    private static long cellCenter(double value,int bits) {
        long width=1L<<(32-bits),coordinate=Math.round(value*10000000);
        return Math.floorDiv(coordinate,width)*width+width/2;
    }
    static boolean sameCoarseCell(NodePosition precise,NodePosition coarse) {
        int bits=coarse.precisionBits;
        return precise.valid()&&coarse.valid()&&(precise.precisionBits>0||precise.source.startsWith("mt"))&&bits>0&&bits<32&&
                (precise.precisionBits==0?32:precise.precisionBits)>bits&&
                cellCenter(precise.latitude,bits)==Math.round(coarse.latitude*10000000)&&
                cellCenter(precise.longitude,bits)==Math.round(coarse.longitude*10000000);
    }
    /** Apply independently to each receiver's history; unknown time is not evidence of a newer fix. */
    static NodePosition choose(NodePosition old,NodePosition incoming) {
        if(old==null||!old.valid())return incoming;
        if(incoming==null||!incoming.valid())return old;
        if(old.time()>0&&incoming.time()>0&&incoming.time()<old.time())return old;
        if(incoming.source.startsWith("mt")&&sameCoarseCell(old,incoming))
            return new NodePosition(old.latitude,old.longitude,incoming.reportTime>0?incoming.reportTime:old.reportTime,
                    old.fixTime,Math.max(old.receivedTime,incoming.receivedTime),old.precisionBits,"mt_preserved");
        // An undated downloaded cache must not replace a dated live position.
        if(old.time()>0&&incoming.time()==0&&incoming.source.endsWith("snapshot"))return old;
        return incoming;
    }
}
