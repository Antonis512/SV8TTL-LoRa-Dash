"""Exercise the production full-export method against successful/broken document providers."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / "src/main/java/MainActivity.java").read_text()
start = source.index("private void exportVerifiedFullBackup(")
export = source[start:source.index("    private void exportFullArchive(", start)]
host = r'''
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
public class BackupProviderProbe {
 static class JSONObject {}
 static class Uri {byte[] data={7,8,9};boolean truncate,noRead,failWrite;int opens;}
 static class Handler {void post(Runnable work){work.run();}}
 class Resolver {
  OutputStream openOutputStream(Uri uri,String mode){uri.opens++;return new ByteArrayOutputStream(){
   @Override public void write(byte[] bytes,int offset,int count){if(uri.failWrite)throw new IllegalStateException("disk full");super.write(bytes,offset,count);}
   @Override public void close(){byte[] bytes=toByteArray();uri.data=uri.truncate?Arrays.copyOf(bytes,bytes.length-22):bytes;}
  };}
  InputStream openInputStream(Uri uri){return uri.noRead?null:new ByteArrayInputStream(uri.data);}
 }
 File cache;boolean closing,badArchive;Handler main=new Handler();
 BackupProviderProbe(File cache){this.cache=cache;}
 File getCacheDir(){return cache;}Resolver getContentResolver(){return new Resolver();}
 void nodeActionResult(String text){}void debug(String text){}
 void exportFullArchive(OutputStream destination,JSONObject settings)throws Exception{
  if(badArchive){destination.write(new byte[]{'P','K',0,0});return;}
  try(ZipOutputStream zip=new ZipOutputStream(destination)){zip.putNextEntry(new ZipEntry("history.json"));zip.write("{}".getBytes("UTF-8"));zip.closeEntry();}
 }
 __EXPORT__
 static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception{
  File cache=Files.createTempDirectory("backup-provider-").toFile();BackupProviderProbe app=new BackupProviderProbe(cache);
  try{
   Uri good=new Uri();app.exportVerifiedFullBackup(good,new JSONObject());check(good.opens==1&&good.data.length>22,"Successful provider copy is verified");
   for(int failure=0;failure<3;failure++){
    Uri uri=new Uri();uri.truncate=failure==0;uri.noRead=failure==1;uri.failWrite=failure==2;boolean completed=false;
    try{app.exportVerifiedFullBackup(uri,new JSONObject());completed=true;}catch(Exception expected){}
    check(!completed,"No successful completion on truncated copy, unreadable file or failed write");
    check(cache.list().length==0,"Private staging removed after provider failure");
   }
   app.badArchive=true;Uri existing=new Uri();byte[] original=existing.data.clone();
   try{app.exportVerifiedFullBackup(existing,new JSONObject());throw new AssertionError("Invalid staged ZIP accepted");}catch(IOException expected){}
   check(existing.opens==0&&Arrays.equals(existing.data,original),"Do not open or truncate destination before staged archive passes validation");
   check(cache.list().length==0,"Private staging removed after invalid export");
   System.out.println("Backup provider checks passed: real export method rejects false-success paths and preserves destination until archive validation");
  }finally{for(File file:cache.listFiles())file.delete();cache.delete();}
 }
}
'''.replace("__EXPORT__", export)

jdk = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else None
with tempfile.TemporaryDirectory(prefix="backup-provider-test-") as tmp:
    java = Path(tmp) / "BackupProviderProbe.java"
    java.write_text(host)
    archive = (root / "src/main/java/BackupArchive.java").read_text().replace("package org.overmesh.android;", "")
    helper = Path(tmp) / "BackupArchive.java"
    helper.write_text(archive)
    subprocess.run([str(jdk / "javac") if jdk else "javac", "-d", tmp, str(helper), str(java)], check=True)
    subprocess.run([str(jdk / "java") if jdk else "java", "-cp", tmp, "BackupProviderProbe"], check=True)
