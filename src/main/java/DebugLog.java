package org.overmesh.android;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Bounded, private diagnostic files. Never pass packet contents or secrets here. */
final class DebugLog {
    private final File current;
    private final File[] files=new File[5];
    private static final int FILE_LIMIT=2*1024*1024;
    private final SimpleDateFormat clock = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    DebugLog(File directory) {
        current = new File(directory, "debug.log");
        files[0]=current;
        files[1]=new File(directory,"debug-previous.log");
        for(int i=2;i<files.length;i++)files[i]=new File(directory,"debug-previous-"+i+".log");
        clock.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
    private static DebugLog crashLog;
    private static boolean crashHandlerInstalled;
    void captureCrashes() {
        synchronized(DebugLog.class) {
            crashLog=this;
            if(crashHandlerInstalled)return;
            crashHandlerInstalled=true;
            Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
                DebugLog log;
                synchronized(DebugLog.class) {log=crashLog;}
                log.write("Uncaught "+error.getClass().getName()+" thread="+thread.getName());
                for(StackTraceElement frame:error.getStackTrace())log.write("  at "+frame);
                if(previous!=null)previous.uncaughtException(thread,error);
            });
        }
    }
    synchronized void write(String event) {
        try {
            String safe=event.replace('\n',' ').replace('\r',' ');
            if(safe.length()>8192)safe=safe.substring(0,8192)+" [truncated]";
            byte[] bytes=(clock.format(new Date())+" "+safe+"\n").getBytes(StandardCharsets.UTF_8);
            if(current.length()+bytes.length>FILE_LIMIT) {
                for(int i=files.length-1;i>0;i--)if(files[i-1].exists())
                    java.nio.file.Files.move(files[i-1].toPath(),files[i].toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            try(FileOutputStream out=new FileOutputStream(current,true)){out.write(bytes);}
        } catch(IOException error) { android.util.Log.e("OverMesh", "Debug log write failed", error); }
    }
    synchronized void export(OutputStream out) throws IOException {
        for(int i=files.length-1;i>=0;i--)
            if(files[i].exists()) java.nio.file.Files.copy(files[i].toPath(),out);
    }
}
