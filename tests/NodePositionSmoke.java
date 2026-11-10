package org.overmesh.android;
import java.lang.reflect.Proxy;
import java.util.*;

public final class NodePositionSmoke {
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static NodePosition position(double lat,double lon,long time,int bits){return new NodePosition(lat,lon,time,time,time+1000,bits,"mt_live");}
    static double coarse(double value,int bits){long width=1L<<(32-bits);return (Math.floorDiv(Math.round(value*1e7),width)*width+width/2)/1e7;}
    static void little(byte[] bytes,int offset,int value){for(int i=0;i<4;i++)bytes[offset+i]=(byte)(value>>>(8*i));}
    public static void main(String[] args){
        NodePosition precise=position(38,23.74,1700000000000L,32);
        NodePosition coarse=position(coarse(38,13),coarse(23.74,13),1700000010000L,13);
        NodePosition saved=NodePosition.choose(precise,coarse);
        check(NodePosition.choose(NodePosition.unknown(coarse.latitude,coarse.longitude),coarse)==coarse,"Legacy coordinates with unknown precision adopt newly received sharing precision");
        check(saved.latitude==precise.latitude&&saved.longitude==precise.longitude&&saved.precisionBits==32,"Same-cell coarse report preserves the known precise coordinate");
        check(saved.source.equals("mt_preserved")&&saved.fixTime==precise.fixTime&&saved.reportTime==coarse.reportTime,"Preserved coordinates retain their fix time and the new report provenance");
        NodePosition moved=position(coarse(38.2,13),coarse(23.9,13),1700000020000L,13);
        check(NodePosition.choose(saved,moved)==moved,"A different coarse cell indicates movement");
        check(NodePosition.choose(moved,precise)==moved,"Older replay cannot move a node backwards");
        NodePosition partial=position(38.1,Double.NaN,1700000030000L,32);
        check(!partial.valid()&&Double.isNaN(partial.latitude)&&NodePosition.choose(precise,partial)==precise,"Partial fixes never mix a new latitude with an old longitude");
        check(position(0,23,1,32).valid()&&position(38,0,1,32).valid(),"Equator and prime meridian are valid when both axes were supplied");
        check(!position(0,0,1,32).valid()&&!position(91,23,1,32).valid(),"Unknown and out-of-range coordinates are excluded");
        NodePosition south=position(-33.8688,151.2093,1700000000000L,32);
        check(NodePosition.sameCoarseCell(south,position(coarse(south.latitude,13),coarse(south.longitude,13),1700000010000L,13)),"Coarse cells handle signed coordinates");
        check(NodePosition.choose(precise,new NodePosition(39,24,0,0,1700000040000L,0,"mt_snapshot"))==precise,"An undated cache cannot replace a dated fix");
        NodePosition future=new NodePosition(38,23,System.currentTimeMillis()+3600000,System.currentTimeMillis()+3600000,0,99,"mt_live");
        check(future.reportTime==0&&future.fixTime==0&&future.precisionBits==0,"Invalid times and precision cannot poison ordering");
        List<NodePosition> reports=new ArrayList<>();List<String> order=new ArrayList<>();
        RadioProtocol.Events events=(RadioProtocol.Events)Proxy.newProxyInstance(RadioProtocol.Events.class.getClassLoader(),new Class[]{RadioProtocol.Events.class},(proxy,method,values)->{
            if(method.getName().equals("positionInfo")){reports.add((NodePosition)values[1]);order.add("metadata");}
            if(method.getName().equals("node")||method.getName().equals("position"))order.add(method.getName());return null;
        });
        byte[] pos=new ProtoWire.Writer().fixed32(1,-338688000).fixed32(2,1512093000).fixed32(4,1700000010).fixed32(7,1700000000).varint(23,13).build();
        MeshtasticProtocol mt=new MeshtasticProtocol();
        mt.receive(new ProtoWire.Writer().bytes(4,new ProtoWire.Writer().varint(1,123).bytes(3,pos).build()).build(),events);
        NodePosition report=reports.get(0);
        check(report.latitude==-33.8688&&report.longitude==151.2093&&report.reportTime==1700000010000L&&report.fixTime==1700000000000L&&report.precisionBits==13&&report.source.equals("mt_snapshot"),"NodeInfo retains timestamps, precision and signed fixed-point coordinates");
        check(order.equals(Arrays.asList("metadata","node")),"Metadata is delivered before the main-thread node update");
        byte[] data=new ProtoWire.Writer().varint(1,3).bytes(2,pos).build();
        mt.receive(new ProtoWire.Writer().bytes(2,new ProtoWire.Writer().fixed32(1,123).bytes(4,data).build()).build(),events);
        check(reports.get(1).source.equals("mt_live")&&reports.get(1).receivedTime>0,"Live positions retain independent phone receipt time");
        byte[] mc=new byte[148];mc[0]=3;mc[1]=7;mc[33]=2;mc[35]=(byte)255;
        little(mc,132,1700000010);little(mc,136,-33868800);little(mc,140,151209300);
        new MeshCoreProtocol().receive(mc,events);report=reports.get(2);
        check(report.latitude==-33.8688&&report.longitude==151.2093&&report.precisionBits==0&&report.fixTime==0&&report.reportTime==1700000010000L&&report.source.equals("mc_snapshot"),"MeshCore advertised locations keep their own scale and timestamp meaning");
        System.out.println("Node position checks passed: precision preservation, movement, stale/partial fixes, signed axes, time validation and MT/MC metadata decoding");
    }
}
