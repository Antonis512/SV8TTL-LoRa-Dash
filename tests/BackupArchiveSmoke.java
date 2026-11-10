package org.overmesh.android;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class BackupArchiveSmoke {
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static void rejected(File file)throws Exception{
        try{BackupArchive.validate(file);throw new AssertionError("Damaged backup accepted");}
        catch(IOException expected){check(expected.getMessage().contains("incomplete or damaged"),"Clear damaged-ZIP diagnosis");}
    }
    public static void main(String[] args)throws Exception {
        File directory=Files.createTempDirectory("backup-archive-").toFile();
        try{
            File good=new File(directory,"good.zip");
            String history="{\"format\":\"overmesh-app-backup\",\"version\":5,\"note\":\"UNIQUE-BACKUP-CONTENT Ελληνικά\",\"radios\":[]}";
            byte[] payload=history.getBytes(StandardCharsets.UTF_8),pack=new byte[args.length>0?Integer.parseInt(args[0]):131073];new Random(7).nextBytes(pack);
            try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(good))){
                ZipEntry entry=new ZipEntry("history.json");CRC32 crc=new CRC32();crc.update(payload);
                entry.setMethod(ZipEntry.STORED);entry.setSize(payload.length);entry.setCrc(crc.getValue());zip.putNextEntry(entry);
                try(Writer writer=new OutputStreamWriter(BackupArchive.entryStream(zip),StandardCharsets.UTF_8)){writer.write(history);}
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("packs.json"));zip.write("[]".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
                zip.putNextEntry(new ZipEntry("packs/test.pmtiles"));zip.write(pack);zip.closeEntry();
            }
            BackupArchive.validate(good);
            try(ZipFile zip=new ZipFile(good);InputStream input=zip.getInputStream(zip.getEntry("history.json"))){
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[256];int count;while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
                check(Arrays.equals(bytes.toByteArray(),payload),"Writer close preserves Unicode and does not close the enclosing ZIP");
            }
            byte[] original=Files.readAllBytes(good.toPath());
            File truncated=new File(directory,"missing-end.zip");Files.write(truncated.toPath(),Arrays.copyOf(original,original.length-22));rejected(truncated);
            ByteArrayOutputStream satellite=new ByteArrayOutputStream();
            try(ZipOutputStream tiles=new ZipOutputStream(satellite)){tiles.putNextEntry(new ZipEntry("tiles/1/0/0.jpg"));tiles.write(new byte[]{(byte)255,(byte)216,(byte)255});tiles.closeEntry();}
            for(boolean damaged:new boolean[]{false,true}){
                File nested=new File(directory,damaged?"bad-satellite.zip":"satellite.zip");byte[] tileZip=satellite.toByteArray();
                try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(nested))){
                    zip.putNextEntry(new ZipEntry("history.json"));zip.write(payload);zip.closeEntry();
                    zip.putNextEntry(new ZipEntry("packs/test.satpack"));zip.write(damaged?Arrays.copyOf(tileZip,tileZip.length-22):tileZip);zip.closeEntry();
                }
                if(damaged){try{BackupArchive.validate(nested);throw new AssertionError("Damaged inner map ZIP accepted");}catch(IOException expected){check(expected.getMessage().startsWith("Satellite map pack ZIP"),"Identify the broken inner archive");}}
                else BackupArchive.validate(nested);
            }
            File corrupt=new File(directory,"bad-crc.zip");byte[] altered=original.clone();
            byte[] marker="UNIQUE-BACKUP-CONTENT".getBytes(StandardCharsets.UTF_8);int at=-1;
            outer:for(int i=0;i<altered.length-marker.length;i++){for(int j=0;j<marker.length;j++)if(altered[i+j]!=marker[j])continue outer;at=i;break;}
            check(at>=0,"Stored test entry located");altered[at]^=1;Files.write(corrupt.toPath(),altered);rejected(corrupt);
            ByteArrayOutputStream destination=new ByteArrayOutputStream();BackupArchive.copy(good,destination);
            BackupArchive.verifyCopy(good,new ByteArrayInputStream(destination.toByteArray()));
            try{BackupArchive.verifyCopy(good,new ByteArrayInputStream(Arrays.copyOf(original,original.length-22)));throw new AssertionError("Truncated provider copy accepted");}catch(IOException expected){}
            try{BackupArchive.verifyCopy(good,new ByteArrayInputStream(altered));throw new AssertionError("Changed provider bytes accepted");}catch(IOException expected){}
            int[] writes={0,0};OutputStream sink=new OutputStream(){public void write(int value){writes[0]++;}public void write(byte[] bytes,int offset,int count){writes[1]++;}};
            BackupArchive.entryStream(sink).write(pack);
            check(writes[0]==0&&writes[1]==1,"ZIP entry wrapper keeps bulk writes efficient");
            OutputStream failing=new OutputStream(){public void write(int value)throws IOException{throw new IOException("Provider disk full");}};
            try{BackupArchive.copy(good,failing);throw new AssertionError("Provider failure ignored");}catch(IOException expected){}
            System.out.println("Backup archive checks passed (synthetic map-entry bytes="+pack.length+"): completed ZIP/Unicode/multiple entries, missing end, CRC corruption, inner satellite ZIPs, provider readback/truncation, failed writes and bulk streaming");
        }finally{for(File file:Objects.requireNonNull(directory.listFiles()))file.delete();directory.delete();}
    }
}
