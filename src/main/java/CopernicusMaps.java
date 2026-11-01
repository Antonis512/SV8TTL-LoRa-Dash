package org.overmesh.android;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Downloads true-colour Sentinel-2 tiles directly into a private offline pack. */
final class CopernicusMaps {
    interface Progress {void update(int done,int total);}
    static final class Tile {final int z,x,y;Tile(int z,int x,int y){this.z=z;this.x=x;this.y=y;}}
    static final class Plan {
        final double west,south,east,north;final int minZoom,maxZoom;final List<Tile> tiles;
        Plan(double w,double s,double e,double n,int min,int max,List<Tile> list){west=w;south=s;east=e;north=n;minZoom=min;maxZoom=max;tiles=list;}
        JSONObject json() throws Exception {return new JSONObject().put("bounds",new JSONArray().put(west).put(south).put(east).put(north))
                .put("minZoom",minZoom).put("maxZoom",maxZoom).put("tiles",tiles.size())
                .put("estimatedMB",Math.max(1,(int)Math.ceil(tiles.size()*0.15)));}
    }
    static Plan plan(double west,double south,double east,double north) {
        if(!Double.isFinite(west)||!Double.isFinite(south)||!Double.isFinite(east)||!Double.isFinite(north)
                ||west< -180||east>180||south< -80||north>80||west>=east||south>=north||east-west>20||north-south>20)
            throw new IllegalArgumentException("Choose a smaller visible area away from the poles and date line");
        double middle=(south+north)/2;
        int max=Math.min(14,Math.max(0,(int)Math.ceil(Math.log(156543.03392*Math.cos(Math.toRadians(middle))/10)/Math.log(2))));
        int min=Math.max(0,max-6);List<Tile> tiles=new ArrayList<>();
        for(int z=min;z<=max;z++){
            int x0=tileX(west,z),x1=tileX(Math.nextDown(east),z),y0=tileY(north,z),y1=tileY(Math.nextUp(south),z);
            for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++){
                tiles.add(new Tile(z,x,y));if(tiles.size()>2500)throw new IllegalArgumentException("Area needs more than 2,500 tiles at native detail. Zoom in and choose a smaller area.");
            }
        }return new Plan(west,south,east,north,min,max,tiles);
    }
    private static int tileX(double lon,int z){int n=1<<z;return Math.max(0,Math.min(n-1,(int)Math.floor((lon+180)/360*n)));}
    private static int tileY(double lat,int z){int n=1<<z;double r=Math.toRadians(lat);return Math.max(0,Math.min(n-1,(int)Math.floor((1-Math.log(Math.tan(r)+1/Math.cos(r))/Math.PI)/2*n)));}
    private static double mercator(int index,int z){return (index/(double)(1<<z)*2-1)*20037508.342789244;}
    private static double mercatorY(int index,int z){return (1-index/(double)(1<<z)*2)*20037508.342789244;}
    private static String readError(HttpURLConnection connection)throws Exception {
        try(InputStream input=connection.getErrorStream()){if(input==null)return "HTTP "+connection.getResponseCode();
            byte[] bytes=new byte[4096];int n=input.read(bytes);String error=n>0?new String(bytes,0,n,StandardCharsets.UTF_8):"";
            JSONObject json=new JSONObject(error);return json.optString("error",json.optString("message","HTTP "+connection.getResponseCode()));
        }catch(Exception ignored){return "HTTP "+connection.getResponseCode();}
    }
    private static String token(String clientId,String secret)throws Exception {
        URL url=new URL("https://identity.dataspace.copernicus.eu/auth/realms/CDSE/protocol/openid-connect/token");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setRequestMethod("POST");c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
        String body="grant_type=client_credentials&client_id="+URLEncoder.encode(clientId,"UTF-8")+"&client_secret="+URLEncoder.encode(secret,"UTF-8");
        try{try(OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}
            if(c.getResponseCode()!=200)throw new IllegalArgumentException("Copernicus sign-in failed: "+readError(c));
            try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[4096];int n;
                while((n=in.read(bytes))!=-1){out.write(bytes,0,n);if(out.size()>100000)throw new IllegalArgumentException("Unexpected sign-in response");}
                return new JSONObject(out.toString("UTF-8")).getString("access_token");}
        }finally{c.disconnect();}
    }
    private static final String EVAL="//VERSION=3\nfunction setup(){return {input:['B02','B03','B04'],output:{bands:3,sampleType:'AUTO'}};}\nfunction evaluatePixel(s){return [2.5*s.B04,2.5*s.B03,2.5*s.B02];}";
    private static void checkAvailability(Plan plan,String from,String to,String accessToken)throws Exception {
        JSONObject body=new JSONObject().put("collections",new JSONArray().put("sentinel-2-l2a"))
                .put("bbox",new JSONArray().put(plan.west).put(plan.south).put(plan.east).put(plan.north))
                .put("datetime",from+"T00:00:00Z/"+to+"T23:59:59Z").put("limit",1);
        HttpURLConnection c=(HttpURLConnection)new URL("https://sh.dataspace.copernicus.eu/catalog/v1/search").openConnection();
        c.setRequestMethod("POST");c.setConnectTimeout(20000);c.setReadTimeout(30000);c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+accessToken);
        try{try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            if(c.getResponseCode()!=200)throw new IllegalArgumentException("Copernicus scene search failed: "+readError(c));
            try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[4096];int n;
                while((n=in.read(bytes))!=-1){out.write(bytes,0,n);if(out.size()>100000)throw new IllegalArgumentException("Unexpected scene search response");}
                if(new JSONObject(out.toString("UTF-8")).getJSONArray("features").length()==0)
                    throw new IllegalArgumentException("No Sentinel-2 imagery was found for this area and date range. Widen the dates.");}
        }finally{c.disconnect();}
    }
    private static byte[] tile(Tile tile,String from,String to,String accessToken)throws Exception {
        double left=mercator(tile.x,tile.z),right=mercator(tile.x+1,tile.z),top=mercatorY(tile.y,tile.z),bottom=mercatorY(tile.y+1,tile.z);
        JSONObject request=new JSONObject().put("input",new JSONObject()
                .put("bounds",new JSONObject().put("bbox",new JSONArray().put(left).put(bottom).put(right).put(top))
                        .put("properties",new JSONObject().put("crs","http://www.opengis.net/def/crs/EPSG/0/3857")))
                .put("data",new JSONArray().put(new JSONObject().put("type","sentinel-2-l2a")
                        .put("dataFilter",new JSONObject().put("timeRange",new JSONObject()
                                .put("from",from+"T00:00:00Z").put("to",to+"T23:59:59Z"))
                                .put("mosaickingOrder","leastCC")))))
                .put("output",new JSONObject().put("width",256).put("height",256)
                        .put("responses",new JSONArray().put(new JSONObject().put("identifier","default")
                                .put("format",new JSONObject().put("type","image/jpeg")))))
                .put("evalscript",EVAL);
        HttpURLConnection c=(HttpURLConnection)new URL("https://sh.dataspace.copernicus.eu/process/v1").openConnection();
        c.setRequestMethod("POST");c.setConnectTimeout(20000);c.setReadTimeout(60000);c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","image/jpeg");c.setRequestProperty("Authorization","Bearer "+accessToken);
        try{try(OutputStream out=c.getOutputStream()){out.write(request.toString().getBytes(StandardCharsets.UTF_8));}
            if(c.getResponseCode()!=200)throw new IllegalArgumentException("Copernicus tile request failed: "+readError(c));
            try(InputStream in=c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[8192];int n;
                while((n=in.read(bytes))!=-1){out.write(bytes,0,n);if(out.size()>2000000)throw new IllegalArgumentException("Satellite tile is too large");}
                byte[] result=out.toByteArray();if(result.length<4||(result[0]&255)!=255||(result[1]&255)!=216)throw new IllegalArgumentException("Copernicus did not return a JPEG tile");return result;}
        }finally{c.disconnect();}
    }
    static File download(File cacheDir,Plan plan,String from,String to,String clientId,String secret,Progress progress)throws Exception {
        if(!from.matches("\\d{4}-\\d{2}-\\d{2}")||!to.matches("\\d{4}-\\d{2}-\\d{2}")||from.compareTo(to)>0)
            throw new IllegalArgumentException("Choose a valid imagery date range");
        java.time.LocalDate.parse(from);java.time.LocalDate end=java.time.LocalDate.parse(to);
        if(end.isAfter(java.time.LocalDate.now(java.time.ZoneOffset.UTC)))throw new IllegalArgumentException("Imagery end date cannot be in the future");
        if(clientId.trim().isEmpty()||secret.isEmpty())throw new IllegalArgumentException("Enter your Copernicus OAuth client ID and secret");
        String access=token(clientId.trim(),secret);checkAvailability(plan,from,to,access);long refreshed=System.currentTimeMillis();
        File file=File.createTempFile("copernicus-",".satpack",cacheDir);
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(file))){zip.setLevel(0);int done=0;long lastRequest=0;
            for(Tile t:plan.tiles){if(System.currentTimeMillis()-refreshed>45L*60*1000){access=token(clientId.trim(),secret);refreshed=System.currentTimeMillis();}
                long wait=lastRequest+300-System.currentTimeMillis();if(wait>0)Thread.sleep(wait);lastRequest=System.currentTimeMillis();
                byte[] bytes=tile(t,from,to,access);zip.putNextEntry(new ZipEntry("tiles/"+t.z+"/"+t.x+"/"+t.y+".jpg"));zip.write(bytes);zip.closeEntry();
                progress.update(++done,plan.tiles.size());}
        }catch(Exception error){file.delete();throw error;}return file;
    }
}
