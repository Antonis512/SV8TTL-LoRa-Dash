package org.overmesh.android;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.UUID;

final class BleRadio {
    interface Listener { void ready(); default void detected(boolean meshcore) {} void received(byte[] bytes); void status(String text); default void written(byte[] bytes,boolean success,String reason) {} }
    static final UUID MT_SERVICE = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd");
    static final UUID MC_SERVICE = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID MT_FROM = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002");
    private static final UUID MT_TO = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7");
    private static final UUID MT_NUM = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453");
    private static final UUID MC_TO = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID MC_FROM = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private boolean meshcore;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Queue<byte[]> writes = new ArrayDeque<>();
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic from, to, count;
    private boolean busy, draining;

    BleRadio(Context context, boolean meshcore, Listener listener) {
        this.context = context; this.meshcore = meshcore; this.listener = listener;
    }

    void connect(BluetoothDevice device) {
        close(); listener.status("Connecting to " + device.getAddress());
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE);
    }

    void send(byte[] packet) {
        main.post(() -> { writes.add(packet); pump(); });
    }

    void close() {
        if (gatt != null) {
            BluetoothGatt closing=gatt;gatt=null;
            try{closing.disconnect();}catch(SecurityException ignored){}
            try{closing.close();}catch(SecurityException ignored){}
        }
        from = null; to = null; count = null; busy = false; draining = false; writes.clear();
    }

    private void pump() {
        if (busy || gatt == null || to == null || writes.isEmpty()) return;
        byte[] data = writes.peek();
        to.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        to.setValue(data); busy = true;
        if (!gatt.writeCharacteristic(to)) { busy = false; writes.poll(); listener.written(data,false,"BLE write could not start"); listener.status("BLE write could not start"); pump(); }
    }

    private void drain() {
        if (!meshcore && !draining && gatt != null && from != null) {
            draining = true;
            if (!gatt.readCharacteristic(from)) draining = false;
        }
    }

    private void enableNotifications(BluetoothGatt bt) {
        if (!bt.setCharacteristicNotification(count, true)) { listener.status("BLE notifications unavailable"); return; }
        BluetoothGattDescriptor descriptor = count.getDescriptor(CCCD);
        if (descriptor == null) { listener.status("BLE notification descriptor missing"); return; }
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (!bt.writeDescriptor(descriptor)) listener.status("BLE notification setup failed");
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt bt, int status, int state) {
            main.post(() -> {
                if (bt != gatt) return;
                if (status != BluetoothGatt.GATT_SUCCESS || state == BluetoothProfile.STATE_DISCONNECTED) {
                    listener.status("BLE disconnected (status " + status + ")"); close();
                } else if (state == BluetoothProfile.STATE_CONNECTED) {
                    listener.status("BLE connected; discovering services"); bt.discoverServices();
                }
            });
        }

        @Override public void onServicesDiscovered(BluetoothGatt bt, int status) {
            main.post(() -> {
                if (bt != gatt) return;
                if(status != BluetoothGatt.GATT_SUCCESS) { listener.status("BLE service discovery failed"); return; }
                if(bt.getService(MT_SERVICE)!=null) meshcore=false;
                else if(bt.getService(MC_SERVICE)!=null) meshcore=true;
                else {listener.status("Selected radio service not found");return;}
                android.bluetooth.BluetoothGattService service = bt.getService(meshcore ? MC_SERVICE : MT_SERVICE);
                if (service == null) { listener.status("Selected radio service not found"); return; }
                from = service.getCharacteristic(meshcore ? MC_FROM : MT_FROM);
                to = service.getCharacteristic(meshcore ? MC_TO : MT_TO);
                count = meshcore ? from : service.getCharacteristic(MT_NUM);
                if (from == null || to == null || count == null) { listener.status("Required BLE characteristics missing"); return; }
                listener.detected(meshcore);
                listener.status("Radio type verified: "+(meshcore?"MC":"MT"));
                if (!bt.requestMtu(512)) enableNotifications(bt);
            });
        }

        @Override public void onMtuChanged(BluetoothGatt bt, int mtu, int status) {
            main.post(() -> {
                if (bt != gatt) return;
                enableNotifications(bt);
            });
        }

        @Override public void onDescriptorWrite(BluetoothGatt bt, BluetoothGattDescriptor descriptor, int status) {
            main.post(() -> {
                if (bt != gatt) return;
                if (status != BluetoothGatt.GATT_SUCCESS) { listener.status("BLE notification setup failed: " + status); return; }
                listener.status("BLE ready"); listener.ready(); if (!meshcore) main.postDelayed(() -> drain(), 350);
            });
        }

        @Override public void onCharacteristicChanged(BluetoothGatt bt, BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            main.post(() -> {
                if (bt != gatt || value == null) return;
                if (meshcore) listener.received(Arrays.copyOf(value, value.length));
                else drain();
            });
        }

        @Override public void onCharacteristicRead(BluetoothGatt bt, BluetoothGattCharacteristic characteristic, int status) {
            byte[] value = characteristic.getValue();
            main.post(() -> {
                if (bt != gatt) return;
                draining = false;
                if (status == BluetoothGatt.GATT_SUCCESS && value != null && value.length > 0) {
                    listener.received(Arrays.copyOf(value, value.length)); main.post(() -> drain());
                }
            });
        }

        @Override public void onCharacteristicWrite(BluetoothGatt bt, BluetoothGattCharacteristic characteristic, int status) {
            main.post(() -> {
                if (bt != gatt) return;
                busy = false; byte[] written=writes.poll();
                boolean success=status==BluetoothGatt.GATT_SUCCESS;
                if(written!=null) listener.written(written,success,success?"":"BLE write failed: "+status);
                if (!success) {
                    // An untagged command may have reached the radio. Do not retry it
                    // or send later commands on a connection with uncertain state.
                    close();
                    listener.status("BLE disconnected after write failure (status " + status + "). Reconnect to try again.");
                    return;
                }
                pump();
            });
        }
    };
}
