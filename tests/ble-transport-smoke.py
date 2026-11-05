"""Run the real BleRadio against a deterministic, single-operation fake GATT.

No radio, Android emulator, Gradle task, or APK is needed. Set JAVA_HOME to a JDK.
"""
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build' / 'ble-transport-test'
JDK = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Android/Android Studio/jbr'))

sources = {
'android/content/Context.java': 'package android.content; public class Context {}',
'android/os/Looper.java': 'package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java': '''package android.os;
import java.util.*;
public class Handler {
 static ArrayDeque<Runnable> queue=new ArrayDeque<>();
 static ArrayList<Runnable> timers=new ArrayList<>();
 public Handler(Looper l){}
 public boolean post(Runnable r){queue.add(r);return true;}
 public boolean postDelayed(Runnable r,long delay){timers.add(r);return true;}
 public void removeCallbacks(Runnable r){timers.remove(r);queue.remove(r);}
 public static void flush(){int n=0;while(!queue.isEmpty()){if(++n>1000)throw new AssertionError("Loop");queue.remove().run();}}
 public static void expire(){var pending=new ArrayList<>(timers);timers.clear();pending.forEach(Runnable::run);flush();}
}''',
'android/bluetooth/BluetoothProfile.java': '''package android.bluetooth;
public interface BluetoothProfile {int STATE_CONNECTED=2,STATE_DISCONNECTED=0;}''',
'android/bluetooth/BluetoothGattCallback.java': '''package android.bluetooth;
public class BluetoothGattCallback {
 public void onConnectionStateChange(BluetoothGatt g,int status,int state){}
 public void onServicesDiscovered(BluetoothGatt g,int status){}
 public void onMtuChanged(BluetoothGatt g,int mtu,int status){}
 public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){}
 public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){}
 public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int status){}
 public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){}
}''',
'android/bluetooth/BluetoothDevice.java': '''package android.bluetooth;
import android.content.Context;
public class BluetoothDevice {
 public static final int TRANSPORT_LE=2;
 public BluetoothGatt gatt;
 public BluetoothDevice(boolean mc){gatt=new BluetoothGatt(mc);}
 public String getAddress(){return "TEST";}
 public BluetoothGatt connectGatt(Context c,boolean auto,BluetoothGattCallback callback,int transport){gatt.callback=callback;return gatt;}
}''',
'android/bluetooth/BluetoothGattDescriptor.java': '''package android.bluetooth;
import java.util.UUID;
public class BluetoothGattDescriptor {
 public static final byte[] ENABLE_NOTIFICATION_VALUE={1,0};
 public UUID getUuid(){return UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");}
 public void setValue(byte[] value){}
}''',
'android/bluetooth/BluetoothGattCharacteristic.java': '''package android.bluetooth;
import java.util.UUID;
public class BluetoothGattCharacteristic {
 public static final int WRITE_TYPE_DEFAULT=2;
 byte[] value=new byte[0];
 public void setWriteType(int type){}
 public void setValue(byte[] v){value=v;}
 public byte[] getValue(){return value;}
 public BluetoothGattDescriptor getDescriptor(UUID id){return new BluetoothGattDescriptor();}
}''',
'android/bluetooth/BluetoothGattService.java': '''package android.bluetooth;
import java.util.*;
public class BluetoothGattService {
 Map<UUID,BluetoothGattCharacteristic> characteristics=new HashMap<>();
 public BluetoothGattCharacteristic getCharacteristic(UUID id){return characteristics.computeIfAbsent(id,k->new BluetoothGattCharacteristic());}
}''',
'android/bluetooth/BluetoothGatt.java': '''package android.bluetooth;
import java.util.*;
public class BluetoothGatt {
 public static final int GATT_SUCCESS=0;
 public BluetoothGattCallback callback;
 public BluetoothGattService service=new BluetoothGattService();
 public BluetoothGattCharacteristic current;
 public BluetoothGattDescriptor descriptor;
 public String pending="";
 public List<String> operations=new ArrayList<>();
 public boolean mc,closed,refuse;
 public BluetoothGatt(boolean mc){this.mc=mc;}
 public BluetoothGattService getService(UUID id){return id.toString().startsWith(mc?"6e400001":"6ba1b218")?service:null;}
 boolean start(String kind){if(!pending.isEmpty())throw new AssertionError("Overlapping "+pending+" / "+kind);if(refuse)return false;pending=kind;operations.add(kind);return true;}
 public boolean discoverServices(){return start("services");}
 public boolean requestMtu(int mtu){return start("mtu");}
 public boolean setCharacteristicNotification(BluetoothGattCharacteristic c,boolean on){return true;}
 public boolean writeDescriptor(BluetoothGattDescriptor d){descriptor=d;return start("descriptor");}
 public boolean writeCharacteristic(BluetoothGattCharacteristic c){current=c;return start("write");}
 public boolean readCharacteristic(BluetoothGattCharacteristic c){current=c;return start("read");}
 public void disconnect(){}
 public void close(){closed=true;pending="";}
 public void finish(int status,byte[] data){String kind=pending;pending="";
  switch(kind){
   case "services":callback.onServicesDiscovered(this,status);break;
   case "mtu":callback.onMtuChanged(this,512,status);break;
   case "descriptor":callback.onDescriptorWrite(this,descriptor,status);break;
   case "write":callback.onCharacteristicWrite(this,current,status);break;
   case "read":current.setValue(data);callback.onCharacteristicRead(this,current,status);break;
   default:throw new AssertionError("No pending operation");
  }
 }
}''',
'org/overmesh/android/BleTransportSmoke.java': '''package org.overmesh.android;
import android.bluetooth.*;import android.os.Handler;import android.content.Context;import java.util.*;
public class BleTransportSmoke {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static class Listener implements BleRadio.Listener {
  int ready;List<byte[]> received=new ArrayList<>();List<String> statuses=new ArrayList<>();
  public void ready(){ready++;}public void received(byte[] b){received.add(b);}public void status(String s){statuses.add(s);}
 }
 static BluetoothGatt connect(BleRadio radio,boolean mc){
  BluetoothDevice device=new BluetoothDevice(mc);radio.connect(device);BluetoothGatt g=device.gatt;
  g.callback.onConnectionStateChange(g,0,2);Handler.flush();
  g.finish(0,null);Handler.flush();g.finish(0,null);Handler.flush();g.finish(0,null);Handler.flush();return g;
 }
 public static void main(String[] args){
  for(boolean mc:new boolean[]{false,true}){
   Listener l=new Listener();BleRadio radio=new BleRadio(new Context(),mc,l);BluetoothGatt g=connect(radio,mc);
   check(l.ready==1,"One ready event");
   g.callback.onMtuChanged(g,512,0);Handler.flush();check(l.ready==1,"Duplicate MTU must not subscribe again");
   radio.send(new byte[]{1});Handler.flush();
   if(!mc){check(g.pending.equals("read"),"Write waits behind initial read");g.finish(0,new byte[0]);Handler.flush();}
   check(g.pending.equals("write"),"Initial command starts");
   BluetoothGattCharacteristic notification=new BluetoothGattCharacteristic();byte[] bytes={7};notification.setValue(bytes);
   g.callback.onCharacteristicChanged(g,notification);bytes[0]=99;Handler.flush();
   check(g.pending.equals("write"),"Notification must not overlap write");
   if(mc)check(l.received.get(0)[0]==7,"Notification copied before posting");
   g.finish(0,null);Handler.flush();
   if(!mc){
    check(g.pending.equals("read"),"Notification retained during write");
    radio.send(new byte[]{2});Handler.flush();check(g.pending.equals("read"),"Write waits during drain");
    byte[] payload={42};g.finish(0,payload);payload[0]=0;Handler.flush();
    check(l.received.get(0)[0]==42,"Read payload copied before posting");
    check(g.pending.equals("write"),"Queued write proceeds after read");g.finish(0,null);Handler.flush();
    check(g.pending.equals("read"),"Drain resumes after write");g.finish(0,new byte[0]);Handler.flush();
    check(g.pending.isEmpty(),"Empty read ends drain");
   }
   radio.close();Handler.expire();check(l.statuses.stream().noneMatch(s->s.contains("timed out")),"Closed timers ignored");
   BluetoothGatt old=g;g=connect(radio,mc);
   old.callback.onConnectionStateChange(old,133,0);Handler.flush();check(!g.closed,"Old disconnect must not close new connection");
   if(!mc){g.finish(0,new byte[0]);Handler.flush();}
   radio.send(new byte[]{3});Handler.flush();Handler.expire();check(g.closed,"Stalled write closes transport");
   check(l.statuses.get(l.statuses.size()-1).contains("timed out during write"),"Stage-specific timeout");
   g=connect(radio,mc);if(!mc){g.finish(0,new byte[0]);Handler.flush();}
   radio.send(new byte[]{4});Handler.flush();g.finish(133,null);Handler.flush();check(g.closed,"Failed write closes transport");
   g=connect(radio,mc);if(!mc){g.finish(133,null);Handler.flush();check(g.closed,"Failed read cannot silently stall loading");}
   else {g.refuse=true;radio.send(new byte[]{5});Handler.flush();check(g.closed,"Rejected write closes transport");}
   // A queued send from the previous session must never be sent to a replacement GATT.
   radio.send(new byte[]{6});radio.close();g=connect(radio,mc);Handler.flush();
   check(!g.operations.contains("write"),"No stale command on reconnect");radio.close();Handler.expire();
  }
  System.out.println("BLE MT/MC serialization, payload snapshots, reconnect isolation, setup idempotency and failures passed");
 }
}'''
}

files = []
for name, content in sources.items():
    target = OUT / 'src' / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding='utf-8')
    files.append(str(target))
classes = OUT / 'classes'
classes.mkdir(parents=True, exist_ok=True)
subprocess.run([str(JDK / 'bin' / 'javac'), '-d', str(classes), *files,
                str(ROOT / 'src/main/java/BleRadio.java')], check=True)
subprocess.run([str(JDK / 'bin' / 'java'), '-cp', str(classes),
                'org.overmesh.android.BleTransportSmoke'], check=True)
