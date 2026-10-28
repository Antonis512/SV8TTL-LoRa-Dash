package org.overmesh.android;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class UsbRadio {
    interface Listener { void ready(); default void detected(boolean meshcore) {} void received(byte[] bytes); void status(String text); default void written(byte[] bytes,boolean success,String reason) {} }
    private final UsbManager manager;
    private final Listener listener;
    private volatile SerialFraming framing;
    private volatile boolean identified;
    private UsbSerialPort port;
    private Thread reader;
    private volatile boolean running;
    private ExecutorService writer;

    UsbRadio(Context context, boolean meshcore, Listener listener) {
        manager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
        this.listener = listener;
        framing = new SerialFraming(meshcore ? SerialFraming.Kind.MESHCORE : SerialFraming.Kind.MESHTASTIC);
    }

    List<UsbSerialDriver> drivers() { return UsbSerialProber.getDefaultProber().findAllDrivers(manager); }
    boolean hasPermission(UsbDevice device) { return manager.hasPermission(device); }

    void connect(UsbSerialDriver driver) {
        close();
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) { listener.status("USB permission or device open failed"); return; }
        try {
            port = driver.getPorts().get(0);
            port.open(connection);
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            running = true;identified=false;
            SerialFraming mtFrames=new SerialFraming(SerialFraming.Kind.MESHTASTIC);
            SerialFraming mcFrames=new SerialFraming(SerialFraming.Kind.MESHCORE);
            writer = Executors.newSingleThreadExecutor(task -> new Thread(task, "OverMesh USB writer"));
            UsbSerialPort current = port;
            reader = new Thread(() -> {
                byte[] buffer = new byte[512];
                long nextProbe=0;boolean probeMc=false;
                while (running && port==current) {
                    try {
                        if(!identified && System.currentTimeMillis()>=nextProbe) {
                            boolean mc=probeMc;probeMc=!probeMc;nextProbe=System.currentTimeMillis()+2000;
                            byte[] probe=mc?new MeshCoreProtocol().initialCommand():new MeshtasticProtocol().initialCommand();
                            current.write((mc?mcFrames:mtFrames).encode(probe),2000);
                            listener.status("USB detecting radio type: probing "+(mc?"MC":"MT"));
                        }
                        int length = current.read(buffer, 1000);
                        if (length > 0) {
                            byte[] chunk=java.util.Arrays.copyOf(buffer,length);
                            if(identified) framing.feed(chunk,listener::received);
                            else {
                                mtFrames.feed(chunk,packet->identify(false,mtFrames,packet));
                                if(!identified)mcFrames.feed(chunk,packet->identify(true,mcFrames,packet));
                            }
                        }
                    } catch (IOException error) { if (running) listener.status("USB read failed: " + error.getMessage()); break; }
                }
            }, "OverMesh USB reader");
            reader.start(); listener.status("USB open; detecting radio type");
        } catch (Exception error) { listener.status("USB open failed: " + error.getMessage()); close(); }
    }

    private void identify(boolean mc,SerialFraming detected,byte[] packet) {
        if(!identified) {
            if(!RadioDetection.isIdentity(mc,packet))return;
            framing=detected;identified=true;
            listener.detected(mc);listener.status("USB radio type verified: "+(mc?"MC":"MT"));listener.ready();
        }
        if(framing==detected)listener.received(packet);
    }
    void send(byte[] packet) {
        UsbSerialPort current = port;
        ExecutorService queue = writer;
        if (!running || current == null || queue == null) { listener.status("USB disconnected"); return; }
        queue.execute(() -> {
            if (!running || port != current) return;
            try { current.write(framing.encode(packet), 2000); listener.written(packet,true,""); }
            catch (Exception error) { listener.written(packet,false,"USB write failed: "+error.getMessage()); if (running) listener.status("USB write failed: " + error.getMessage()); }
        });
    }

    void close() {
        running = false;
        if (writer != null) { writer.shutdownNow(); writer = null; }
        if (port != null) { try { port.close(); } catch (IOException ignored) {} port = null; }
    }
}
