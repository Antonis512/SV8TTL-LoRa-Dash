"""Exercise the production first-launch permission methods with a fake Android host.

Requires a JDK; does not package or install an APK or use the phone's settings.
"""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / "src/main/java/MainActivity.java").read_text()
initial = source.split("    private void requestInitialNotificationPermission()", 1)[1]
initial = "private void requestInitialNotificationPermission()" + initial.split("    @Override protected void onPause()", 1)[0]
result = source.split("        if(requestCode==MESSAGE_NOTIFICATION_PERMISSION||requestCode==INITIAL_NOTIFICATION_PERMISSION)", 1)[1]
result = "if(requestCode==MESSAGE_NOTIFICATION_PERMISSION||requestCode==INITIAL_NOTIFICATION_PERMISSION)" + result.split("        if(requestCode==BLE_PERMISSIONS)", 1)[0]

harness = r'''
import java.io.*;
import java.nio.file.*;
public class NotificationPermissionProbe {
 static class Build { static class VERSION { static int SDK_INT=33; } }
 static class Manifest { static class permission { static String POST_NOTIFICATIONS="notifications"; } }
 static class PackageManager { static int PERMISSION_GRANTED=0; }
 static class MessageNotifications { static void createChannel(NotificationPermissionProbe a){a.channels++;} }
 static final int MESSAGE_NOTIFICATION_PERMISSION=40, INITIAL_NOTIFICATION_PERMISSION=43;
 boolean closing,resumed=true,initialNotificationPermissionChecked,notificationPermissionPending;
 boolean blePermissionPending,bleEnablePending,markPermissionPending,preview;
 int permission=-1,requests,channels,publishes,bleResumes,lastCode;
 String notificationText="old";
 File directory;
 NotificationPermissionProbe(File directory){this.directory=directory;}
 File getNoBackupFilesDir(){return directory;}
 int checkSelfPermission(String permission){return this.permission;}
 void debug(String message){}
 void requestPermissions(String[] permissions,int code){requests++;lastCode=code;}
 void publish(){publishes++;}
 void continueBleRequests(){bleResumes++;}
 NotificationPermissionProbe getPreferences(int mode){return this;}
 NotificationPermissionProbe edit(){return this;}
 NotificationPermissionProbe putBoolean(String key,boolean value){preview=value;return this;}
 void apply(){}
 __INITIAL__
 void result(int requestCode,int...grants){__RESULT__}
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static File fresh()throws Exception{return Files.createTempDirectory("notification-install-").toFile();}
 public static void main(String[] args)throws Exception{
  File install=fresh();NotificationPermissionProbe a=new NotificationPermissionProbe(install);
  a.requestInitialNotificationPermission();
  check(a.requests==1&&a.lastCode==43&&a.notificationPermissionPending&&a.channels==1,"Fresh Android 13 install requests permission independently of previews");
  check(!a.preview,"First-launch permission does not enable message previews");
  a.requestInitialNotificationPermission();check(a.requests==1,"No duplicate in-flight request");
  a.permission=0;a.result(43,0);
  check(!a.notificationPermissionPending&&a.publishes==1&&a.bleResumes==1&&a.notificationText.isEmpty(),"Grant refreshes running notification and resumes queued Bluetooth work");
  NotificationPermissionProbe restart=new NotificationPermissionProbe(install);
  restart.requestInitialNotificationPermission();check(restart.requests==0,"Prompt decision survives activity recreation and restart");
  a=new NotificationPermissionProbe(fresh());a.preview=true;a.requestInitialNotificationPermission();a.result(43,-1);
  check(a.preview&&!a.notificationPermissionPending,"Automatic denial preserves the user's preview preference");
  restart=new NotificationPermissionProbe(a.directory);restart.requestInitialNotificationPermission();check(restart.requests==0,"Denied permission is not repeatedly requested on startup");
  a.result(40,-1);check(!a.preview,"Explicit preview permission denial keeps existing opt-out behavior");
  a.preview=true;a.result(40,0);check(a.preview,"Explicit grant preserves preview opt-in");
  a=new NotificationPermissionProbe(fresh());a.requestInitialNotificationPermission();a.result(43);
  check(!a.notificationPermissionPending,"Dismissed request clears in-flight state");
  a=new NotificationPermissionProbe(fresh());a.blePermissionPending=true;a.requestInitialNotificationPermission();
  check(a.requests==0&&!a.initialNotificationPermissionChecked,"Do not overlap Bluetooth permission request");
  a.blePermissionPending=false;a.requestInitialNotificationPermission();check(a.requests==1,"Deferred first-launch prompt remains available");
  a=new NotificationPermissionProbe(fresh());a.permission=0;a.requestInitialNotificationPermission();check(a.requests==0,"No prompt when already granted");
  Build.VERSION.SDK_INT=32;a=new NotificationPermissionProbe(fresh());a.requestInitialNotificationPermission();
  check(a.requests==0&&a.directory.list().length==0,"Older Android versions need no runtime notification permission");
  System.out.println("Notification permission checks passed: first launch, grant/deny/dismiss, restart, preview independence, Bluetooth deferral and older Android");
 }
}
'''.replace("__INITIAL__", initial).replace("__RESULT__", result)

jdk = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else None
with tempfile.TemporaryDirectory(prefix="notification-permission-") as tmp:
    output = Path(tmp)
    java = output / "NotificationPermissionProbe.java"
    java.write_text(harness)
    subprocess.run([str(jdk / "javac") if jdk else "javac", "-d", tmp, str(java)], check=True)
    subprocess.run([str(jdk / "java") if jdk else "java", "-cp", tmp, "NotificationPermissionProbe"], check=True)
