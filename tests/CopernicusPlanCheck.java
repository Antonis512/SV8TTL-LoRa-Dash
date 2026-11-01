package org.overmesh.android;

/** Run with the Android SDK jar and CopernicusMaps.java; no network credentials needed. */
public final class CopernicusPlanCheck {
    public static void main(String[] args) {
        CopernicusMaps.Plan athens=CopernicusMaps.plan(23.70,37.96,23.74,38.00);
        if(athens.maxZoom!=14||athens.minZoom!=8||athens.tiles.isEmpty())throw new AssertionError("Native-detail area plan failed");
        for(CopernicusMaps.Tile tile:athens.tiles)
            if(tile.x<0||tile.y<0||tile.x>=1<<tile.z||tile.y>=1<<tile.z)throw new AssertionError("Invalid tile coordinate");
        boolean rejected=false;
        try{CopernicusMaps.plan(20,35,30,45);}catch(IllegalArgumentException expected){rejected=true;}
        if(!rejected)throw new AssertionError("Oversized area should be rejected before download");
        System.out.println("Copernicus area planning passed: "+athens.tiles.size()+" tiles");
    }
}
