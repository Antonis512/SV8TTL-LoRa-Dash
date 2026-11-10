package org.overmesh.android;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.*;

/** ZIP integrity and provider-copy checks, independent of Android document APIs. */
final class BackupArchive {
    private BackupArchive() {}
    static OutputStream entryStream(OutputStream zip) {
        return new FilterOutputStream(zip) {
            // FilterOutputStream's default bulk write writes each byte separately.
            @Override public void write(byte[] bytes,int offset,int length)throws IOException{out.write(bytes,offset,length);}
            @Override public void close()throws IOException{flush();}
        };
    }
    static void validate(File file)throws IOException {
        validate(file,true);
    }
    private static void validate(File file,boolean full)throws IOException {
        try(ZipFile zip=new ZipFile(file)) {
            ZipEntry history=zip.getEntry("history.json");
            if(full&&(history==null||history.isDirectory()||history.getSize()==0))throw new IOException("Full backup has no readable history.json");
            Set<String> names=new HashSet<>();byte[] bytes=new byte[65536];
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry entry=entries.nextElement();
                if(!names.add(entry.getName())||names.size()>(full?1024:2500))throw new IOException("Invalid or duplicate backup ZIP entries");
                if(!full&&(!entry.getName().matches("tiles/[0-9]{1,2}/[0-9]+/[0-9]+\\.jpg")||entry.getSize()<=0||entry.getSize()>2000000))
                    throw new IOException("Invalid satellite map pack inside backup");
                if(entry.isDirectory())continue;
                CRC32 crc=new CRC32();long length=0;
                try(InputStream input=zip.getInputStream(entry)) {
                    int count;while((count=input.read(bytes))!=-1){crc.update(bytes,0,count);length+=count;}
                }
                if(length!=entry.getSize()||crc.getValue()!=entry.getCrc())throw new ZipException("Backup entry checksum or length mismatch");
                if(full&&entry.getName().startsWith("packs/")&&entry.getName().endsWith(".satpack")) {
                    File nested=File.createTempFile("backup-satellite-",".zip",file.getParentFile());
                    try {
                        try(InputStream input=zip.getInputStream(entry);OutputStream output=new BufferedOutputStream(new FileOutputStream(nested))){
                            int count;while((count=input.read(bytes))!=-1)output.write(bytes,0,count);
                        }
                        validate(nested,false);
                    }finally{if(!nested.delete())nested.deleteOnExit();}
                }
            }
            if(!full&&names.isEmpty())throw new IOException("Satellite map pack inside backup is empty");
        }catch(ZipException|EOFException damaged) {
            throw new IOException((full?"Backup ZIP":"Satellite map pack ZIP inside backup")+" is incomplete or damaged (missing ZIP end or invalid contents). Choose another complete backup, or export again from the original installation and wait for 'Backup exported and verified'.",damaged);
        }
    }
    static void copy(File source,OutputStream destination)throws IOException {
        byte[] bytes=new byte[65536];
        try(InputStream input=new BufferedInputStream(new FileInputStream(source))){int count;while((count=input.read(bytes))!=-1)destination.write(bytes,0,count);}
    }
    private static final class Fingerprint {
        long length;byte[] digest;
    }
    private static Fingerprint fingerprint(InputStream input)throws IOException {
        MessageDigest hash;
        try{hash=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new IOException("SHA-256 unavailable",impossible);}
        Fingerprint result=new Fingerprint();byte[] bytes=new byte[65536];int count;
        while((count=input.read(bytes))!=-1){hash.update(bytes,0,count);result.length+=count;}
        result.digest=hash.digest();return result;
    }
    static void verifyCopy(File prepared,InputStream saved)throws IOException {
        Fingerprint expected;
        try(InputStream input=new BufferedInputStream(new FileInputStream(prepared))){expected=fingerprint(input);}
        Fingerprint actual=fingerprint(saved);
        if(expected.length!=actual.length||!MessageDigest.isEqual(expected.digest,actual.digest))
            throw new IOException("The saved backup does not match the completed archive. The file may be truncated or changed by the storage provider. Export again to another location; do not use this file for migration.");
    }
}
