package org.overmesh.android;
import java.nio.file.*;import java.io.*;
public class DebugLogSmoke {
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("overmesh-log-test");
  try{
   DebugLog log=new DebugLog(root.toFile());
   for(int i=0;i<1600;i++)log.write("entry="+i+" "+"x".repeat(8100));
   log=new DebugLog(root.toFile());log.write("restart-marker");
   File[] files=root.toFile().listFiles();long total=0;for(File f:files){total+=f.length();if(f.length()>2*1024*1024)throw new AssertionError("File too large");}
   if(files.length!=5||total>10*1024*1024)throw new AssertionError("Rotation budget");
   ByteArrayOutputStream out=new ByteArrayOutputStream();log.export(out);String exported=out.toString("UTF-8");
   if(exported.contains("entry=0 ")||!exported.endsWith("restart-marker\n")||exported.indexOf("entry=1598 ")>exported.indexOf("entry=1599 "))throw new AssertionError("Export chronology");
   System.out.println("10 MB log rotation/restart/export checks passed");
  }finally{for(File f:root.toFile().listFiles())Files.delete(f.toPath());Files.delete(root);}
 }
}
