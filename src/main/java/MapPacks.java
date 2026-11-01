package org.overmesh.android;

import android.content.Context;
import android.webkit.WebResourceResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/** Private, phone-local PMTiles packs served only to the app's WebView origin. */
final class MapPacks {
    private final File dir,manifest;
    MapPacks(Context context){dir=new File(context.getFilesDir(),"map-packs");dir.mkdirs();manifest=new File(dir,"packs.json");}
    long freeSpace(){return dir.getUsableSpace();}
    JSONArray list() {
        // Readers must not wait for a multi-GB import holding this instance lock.
        synchronized(manifest) {
        try(FileInputStream in=new FileInputStream(manifest)){
            if(manifest.length()>1000000)throw new IOException("Map pack list is too large");
            byte[] bytes=new byte[(int)manifest.length()];int count=0;
            while(count<bytes.length){int n=in.read(bytes,count,bytes.length-count);if(n<0)throw new IOException("Incomplete map pack list");count+=n;}
            return new JSONArray(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
        }
        catch(Exception ignored){return new JSONArray();}
        }
    }
    synchronized boolean hasBackupOrigin(String id){
        JSONArray packs=list();for(int i=0;i<packs.length();i++){JSONObject pack=packs.optJSONObject(i);
            if(pack!=null&&(id.equals(pack.optString("id"))||id.equals(pack.optString("backupOrigin"))))return true;
        }return false;
    }
    synchronized JSONObject addBackup(InputStream source,JSONObject metadata) throws Exception {
        String origin=metadata.getString("id");if(hasBackupOrigin(origin))return null;
        JSONObject added=metadata.optString("schema").equals("copernicus")?addSatelliteArchive(source,metadata):
                add(source,metadata.optString("title","Offline map"),metadata.getString("kind"),
                metadata.optString("schema","protomaps"),metadata.optString("source",""),metadata.optString("captureDate",""));
        added.put("backupOrigin",origin);JSONArray packs=list();
        for(int i=0;i<packs.length();i++)if(added.getString("id").equals(packs.getJSONObject(i).optString("id")))packs.put(i,added);
        save(packs);return added;
    }
    synchronized JSONObject installCopernicus(File source,String title,CopernicusMaps.Plan plan,String from,String to)throws Exception {
        String safeTitle=title==null?"":title.trim();if(safeTitle.length()>100)safeTitle=safeTitle.substring(0,100);
        JSONObject metadata=new JSONObject().put("title",safeTitle.isEmpty()?"Copernicus satellite area":safeTitle)
                .put("kind","satellite").put("schema","copernicus").put("source","Copernicus Sentinel-2 L2A")
                .put("captureDate","").put("requestedRange",from+" to "+to).put("bounds",plan.json().getJSONArray("bounds"))
                .put("minZoom",plan.minZoom).put("maxZoom",plan.maxZoom).put("tileCount",plan.tiles.size());
        try(FileInputStream in=new FileInputStream(source)){return addSatelliteArchive(in,metadata);}
    }
    private JSONObject addSatelliteArchive(InputStream input,JSONObject metadata)throws Exception {
        String id=java.util.UUID.randomUUID().toString();File temp=new File(dir,id+".download"),target=new File(dir,id+".satpack");long size=0;
        try(FileOutputStream out=new FileOutputStream(temp)){byte[] bytes=new byte[65536];int n;while((n=input.read(bytes))!=-1){size+=n;
            if(size>4L*1024*1024*1024)throw new IOException("Satellite pack exceeds 4 GB");out.write(bytes,0,n);}out.getFD().sync();
        }catch(Exception error){temp.delete();throw error;}
        try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(temp)){int count=0;java.util.Enumeration<? extends java.util.zip.ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){java.util.zip.ZipEntry entry=entries.nextElement();
                if(!entry.getName().matches("tiles/[0-9]{1,2}/[0-9]+/[0-9]+\\.jpg")||entry.getSize()>2000000)throw new IOException("Invalid satellite pack tile");
                if(++count>2500)throw new IOException("Satellite pack has too many tiles");
            }if(count<1)throw new IOException("Satellite pack is empty");
        }
        catch(Exception error){temp.delete();throw error;}
        if(!temp.renameTo(target)){temp.delete();throw new IOException("Could not install satellite pack");}
        JSONObject item=new JSONObject(metadata.toString()).put("id",id).put("size",size).put("installed",System.currentTimeMillis());
        try{JSONArray list=list();list.put(item);save(list);}catch(Exception error){target.delete();throw error;}return item;
    }
    private synchronized void save(JSONArray list) throws IOException {
        synchronized(manifest) {
        File temp=new File(dir,"packs.tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(list.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}
        if(!temp.renameTo(manifest)){if(manifest.exists()&&!manifest.delete())throw new IOException("Cannot replace map pack list");if(!temp.renameTo(manifest))throw new IOException("Cannot save map pack list");}
        }
    }
    synchronized JSONObject add(InputStream source,String title,String kind) throws Exception {
        return add(source,title,kind,"protomaps");
    }
    synchronized JSONObject add(InputStream source,String title,String kind,String schema) throws Exception {
        return add(source,title,kind,schema,kind.equals("street")?"OpenStreetMap":"","");
    }
    synchronized JSONObject add(InputStream source,String title,String kind,String schema,String sourceName,String captureDate) throws Exception {
        if(!kind.equals("street")&&!kind.equals("satellite"))throw new IOException("Choose a map type");
        if(!java.util.Arrays.asList("protomaps","shortbread","raster").contains(schema))throw new IOException("Choose a supported map format");
        if(kind.equals("satellite")!=schema.equals("raster"))throw new IOException("Choose a vector format for detailed maps or raster for satellite");
        if(kind.equals("satellite")&&(sourceName==null||sourceName.trim().isEmpty()))throw new IOException("Enter the satellite imagery source for attribution");
        java.io.PushbackInputStream peek=new java.io.PushbackInputStream(source,4);byte[] first=new byte[4];int read=peek.read(first);if(read>0)peek.unread(first,0,read);
        if(read==4&&first[0]=='P'&&first[1]=='K'){
            try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(peek)){
                java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){if(!entry.isDirectory()&&entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pmtiles"))return add(zip,title,kind,schema,sourceName,captureDate);zip.closeEntry();}
            }throw new IOException("ZIP contains no PMTiles file");
        }
        String id=java.util.UUID.randomUUID().toString();File temp=new File(dir,id+".download"),target=new File(dir,id+".pmtiles");long size=0;
        try(FileOutputStream out=new FileOutputStream(temp)){
            byte[] buffer=new byte[65536];int n;while((n=peek.read(buffer))!=-1){size+=n;if(size>4L*1024*1024*1024)throw new IOException("Map pack exceeds 4 GB");
                if((size&0x7fffff)<n&&dir.getUsableSpace()<16L*1024*1024)throw new IOException("Not enough free phone space for this map pack");
                out.write(buffer,0,n);}out.getFD().sync();
        }catch(Exception error){temp.delete();throw error;}
        int minZoom,maxZoom;double west,south,east,north;
        try(RandomAccessFile file=new RandomAccessFile(temp,"r")){
            byte[] header=new byte[127];if(file.length()<127||file.read(header)!=127||!new String(header,0,7,java.nio.charset.StandardCharsets.US_ASCII).equals("PMTiles")||header[7]!=3)throw new IOException("Choose a PMTiles v3 map pack");
            int tileType=header[99]&255;if(schema.equals("raster")?tileType<2||tileType>5:tileType!=1)throw new IOException("Map pack tile type does not match the chosen format");
            minZoom=header[100]&255;maxZoom=header[101]&255;
            java.nio.ByteBuffer h=java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            west=h.getInt(102)/1e7;south=h.getInt(106)/1e7;east=h.getInt(110)/1e7;north=h.getInt(114)/1e7;
        }catch(Exception error){temp.delete();throw error;}
        if(!temp.renameTo(target)){temp.delete();throw new IOException("Cannot install map pack");}
        JSONObject item=new JSONObject().put("id",id).put("title",title.trim().isEmpty()?kind.equals("street")?"Detailed map":"Satellite view":title.trim()).put("kind",kind).put("schema",schema).put("size",size).put("minZoom",minZoom).put("maxZoom",maxZoom)
                .put("bounds",new JSONArray().put(west).put(south).put(east).put(north)).put("installed",System.currentTimeMillis())
                .put("source",sourceName==null?"":sourceName.trim().substring(0,Math.min(100,sourceName.trim().length())))
                .put("captureDate",captureDate==null?"":captureDate.trim().substring(0,Math.min(40,captureDate.trim().length())));
        try{JSONArray current=list();current.put(item);save(current);}catch(Exception error){target.delete();throw error;}return item;
    }
    JSONObject download(String address,String title,String kind,String schema,String sourceName,String captureDate) throws Exception {
        URL url=new URL(address);for(int redirect=0;redirect<4;redirect++){
            if(!url.getProtocol().equals("https"))throw new IOException("Use an HTTPS map pack URL");
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(20000);connection.setReadTimeout(30000);connection.setInstanceFollowRedirects(false);
            try{int status=connection.getResponseCode();if(status==301||status==302||status==303||status==307||status==308){String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Map download redirect has no address");url=new URL(url,location);continue;}
                if(status!=200)throw new IOException("Map download returned HTTP "+status);try(InputStream in=connection.getInputStream()){return add(in,title,kind,schema,sourceName,captureDate);}}
            finally{connection.disconnect();}
        }throw new IOException("Too many map download redirects");
    }
    synchronized void remove(String id) throws IOException {
        if(!id.matches("[0-9a-f-]{36}"))return;JSONArray before=list(),after=new JSONArray();for(int i=0;i<before.length();i++){JSONObject row=before.optJSONObject(i);if(row!=null&&!id.equals(row.optString("id")))after.put(row);}
        save(after);new File(dir,id+".pmtiles").delete();new File(dir,id+".satpack").delete();
    }
    File file(String id){return file(id,"pmtiles");}
    File file(String id,String extension){return id.matches("[0-9a-f-]{36}")&&java.util.Arrays.asList("pmtiles","satpack").contains(extension)?new File(dir,id+"."+extension):null;}
    WebResourceResponse serveSatellite(String path){
        if(!path.matches("/satellite/[0-9a-f-]{36}/[0-9]{1,2}/[0-9]+/[0-9]+\\.jpg"))return null;
        String[] bits=path.split("/");File archive=file(bits[2],"satpack");if(archive==null||!archive.isFile())return null;
        try{java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive);String name="tiles/"+bits[3]+"/"+bits[4]+"/"+bits[5];java.util.zip.ZipEntry entry=zip.getEntry(name);
            if(entry==null){zip.close();return null;}InputStream raw=zip.getInputStream(entry);
            InputStream body=new java.io.FilterInputStream(raw){@Override public void close()throws IOException{try{super.close();}finally{zip.close();}}};
            return new WebResourceResponse("image/jpeg",null,body);
        }catch(Exception ignored){return null;}
    }
    WebResourceResponse serveSlice(String path,String offset,String length) {
        try {
            long start=Long.parseLong(offset),count=Long.parseLong(length);
            if(start<0||count<=0||count>Integer.MAX_VALUE||start>Long.MAX_VALUE-count)throw new IOException("Invalid map slice");
            WebResourceResponse response=serve(path,"bytes="+start+"-"+(start+count-1));
            if(response!=null)return response;
        }catch(Exception ignored){}
        return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",new HashMap<>(),
                new java.io.ByteArrayInputStream("Map data unavailable".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    WebResourceResponse serve(String path,String range) {
        if(!path.matches("/packs/[0-9a-f-]{36}\\.pmtiles"))return null;
        String id=path.substring(7,path.length()-8);File source=file(id);if(source==null||!source.isFile())return null;
        try {
            long length=source.length(),start=0,end=length-1;if(range!=null&&range.matches("bytes=[0-9]+-[0-9]*")){
                String[] bits=range.substring(6).split("-",-1);start=Long.parseLong(bits[0]);if(!bits[1].isEmpty())end=Math.min(end,Long.parseLong(bits[1]));
            }
            if(start<0||start>=length||end<start)return null;
            RandomAccessFile file=new RandomAccessFile(source,"r");file.seek(start);long count=end-start+1;
            InputStream body=new InputStream(){long left=count;@Override public int available(){return (int)Math.min(Integer.MAX_VALUE,left);}@Override public int read() throws IOException {if(left<=0)return -1;int value=file.read();if(value>=0)left--;else left=0;return value;}@Override public int read(byte[] b,int off,int len)throws IOException{if(left<=0)return -1;int n=file.read(b,off,(int)Math.min(len,left));if(n>0)left-=n;return n;}@Override public void close()throws IOException{file.close();}};
            Map<String,String> headers=new HashMap<>();headers.put("Accept-Ranges","bytes");headers.put("Cache-Control","private, max-age=3600");
            boolean partial=range!=null&&range.startsWith("bytes=");if(partial)headers.put("Content-Range","bytes "+start+"-"+end+"/"+length);
            return new WebResourceResponse("application/octet-stream",null,partial?206:200,partial?"Partial Content":"OK",headers,body);
        }catch(Exception ignored){return null;}
    }
}
