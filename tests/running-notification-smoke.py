"""Execute production notification lifecycle methods with a fake Android host."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / "src/main/java/MainActivity.java").read_text()

def method(signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]

host = r'''
import java.util.*;
class Parent {protected void onPause(){}}
public class RunningNotificationProbe extends Parent {
 static class Build {static class VERSION {static int SDK_INT=33;}}
 static class Manifest {static class permission {static String POST_NOTIFICATIONS="notifications";}}
 static class PackageManager {static int PERMISSION_GRANTED=0;}
 static class android {static class app {static class NotificationManager {
  Map<Integer,String> notifications=new HashMap<>();List<Integer> cancelled=new ArrayList<>();
  void notify(int id,String value){notifications.put(id,value);}
  void cancel(int id){cancelled.add(id);notifications.remove(id);}
 }}}
 static class RadioConnectionService {static String notification(Object context,String text,int unread){return text+" unread="+unread;}}
 static class Intent {Intent(Object context,Class<?> type){} Intent putExtra(String key,Object value){return this;}}
 static class RadioSession {boolean connected;RadioSession(boolean connected){this.connected=connected;}}
 static class ChatStore {int unread;int unreadCount(){return unread;}}
 boolean closing,resumed=true,radioServiceRunning,historyTransferRunning,failStart;
 int permission=0,foregroundStarts,serviceUpdates,serviceStops;
 String notificationText="";
 Map<String,RadioSession> sessions=new HashMap<>();ChatStore chatStore=new ChatStore();
 android.app.NotificationManager manager=new android.app.NotificationManager();
 <T>T getSystemService(Class<T> type){return type.cast(manager);}
 int checkSelfPermission(String permission){return this.permission;}
 void debug(String text){}
 void startForegroundService(Intent intent){if(failStart)throw new IllegalStateException("blocked");foregroundStarts++;}
 void startService(Intent intent){serviceUpdates++;}
 void stopService(Intent intent){serviceStops++;}
 __PAUSE__
 __UPDATE__
 __NOTIFY__
 static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 public static void main(String[] args){
  RunningNotificationProbe app=new RunningNotificationProbe();app.updateRunningNotification();
  check(app.manager.notifications.containsKey(201),"Running status exists without a radio or unread messages");
  app.onPause();check(!app.resumed&&app.manager.notifications.containsKey(201)&&app.manager.cancelled.isEmpty(),"Home/background must not remove the running icon");
  check(app.foregroundStarts==0,"No connected-device foreground service without a connection");
  app.historyTransferRunning=true;app.notificationText="";app.updateRunningNotification();
  check(app.manager.notifications.get(201).contains("Backup transfer"),"Transfer status remains visible in the background");
  app.historyTransferRunning=false;app.sessions.put("mt",new RadioSession(true));app.notificationText="";app.updateRunningNotification();
  check(app.foregroundStarts==0&&app.manager.notifications.containsKey(201),"Late background connection retains status without forbidden foreground-service launch");
  app.resumed=true;app.notificationText="";app.updateRunningNotification();
  check(app.foregroundStarts==1&&app.radioServiceRunning&&app.manager.cancelled.isEmpty(),"Keep fallback notification until service actually posts foreground status");
  app.notificationText="";app.updateRunningNotification();check(app.serviceUpdates==1,"Existing radio service is updated");
  app.sessions.clear();app.notificationText="";app.updateRunningNotification();check(app.serviceStops==1&&app.manager.notifications.containsKey(201),"Disconnecting retains app-running notification");
  app.sessions.put("mt",new RadioSession(true));app.failStart=true;app.notificationText="";app.updateRunningNotification();
  check(!app.radioServiceRunning&&app.manager.notifications.containsKey(201),"Service-start failure retains normal status notification");
  app=new RunningNotificationProbe();app.permission=-1;app.onPause();check(app.manager.notifications.isEmpty(),"Notification permission denial is respected");
  app=new RunningNotificationProbe();app.closing=true;app.onPause();check(app.manager.notifications.isEmpty(),"Closing app does not repost status");
  System.out.println("Running notification checks passed: no-radio background, transfer status, permission denial, foreground transition/failure and closing");
 }
}
'''.replace("__PAUSE__", method("protected void onPause()"))\
   .replace("__UPDATE__", method("private void updateRunningNotification()"))\
   .replace("__NOTIFY__", method("private void notifyRunningStatus("))

jdk = Path(os.environ["JAVA_HOME"]) / "bin" if os.environ.get("JAVA_HOME") else None
with tempfile.TemporaryDirectory(prefix="running-notification-") as tmp:
    java = Path(tmp) / "RunningNotificationProbe.java"
    java.write_text(host)
    subprocess.run([str(jdk / "javac") if jdk else "javac", "-d", tmp, str(java)], check=True)
    subprocess.run([str(jdk / "java") if jdk else "java", "-cp", tmp, "RunningNotificationProbe"], check=True)
