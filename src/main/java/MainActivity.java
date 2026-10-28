package org.overmesh.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.net.Uri;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.content.ActivityNotFoundException;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.graphics.Insets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.TreeMap;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private static final String USB_PERMISSION = "org.overmesh.android.USB_PERMISSION";
    private static final String APP_URL = "https://appassets.androidplatform.net/assets/index.html";
    private static final int GREEN = 0xff17695c;
    private static final int DARK = 0xff173d36;
    private final Handler main = new Handler(Looper.getMainLooper());
    private DebugLog debugLog;
    private static final int EXPORT_DEBUG_LOG=490;
    private void debug(String text) { if(debugLog!=null) debugLog.write(text); }
    private int sessionSerial;
    private final RadioSession mt = new RadioSession("Meshtastic", false);
    private final RadioSession mc = new RadioSession("MeshCore", true);
    private final Map<String,RadioSession> sessions = new LinkedHashMap<>();
    private RadioSession selectedMt = mt, selectedMc = mc;
    private RadioSession chatLeft=mc, chatRight=mt;
    private final Map<String, BluetoothDevice> discovered = new LinkedHashMap<>();
    private BluetoothLeScanner scanner;
    private RadioSession scanningSession, awaitingUsbSession, selected = mt;
    private int scanToken;
    private ChatStore chatStore;
    private SendChain sendChain;
    private long nextChainSend;
    private final Runnable chainRunner=new Runnable(){public void run(){if(closing)return;tickChain();main.postDelayed(this,500);}};
    private final Runnable splitRunner=new Runnable(){public void run(){if(closing)return;for(RadioSession s:sessions.values())tickSplit(s);main.postDelayed(this,100);}};
    private final java.util.concurrent.ExecutorService historyWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean connectingAvailable;
    private int availableRun;
    private boolean retryingAvailable;
    private String availableDiscoveryError="";
    private final Map<String,UsbSerialDriver> expectedAvailableUsb=new LinkedHashMap<>();
    private final Map<String,ChatStore.KnownRadio> expectedAvailableBle=new LinkedHashMap<>();
    private int savedViewRevision;
    private boolean historyTransferRunning;
    private final ArrayDeque<UsbSerialDriver> availableUsb=new ArrayDeque<>();
    private final ArrayDeque<ChatStore.KnownRadio> availableBle=new ArrayDeque<>();
    private UsbSerialDriver awaitingUsb;
    private FrameLayout pages;
    private LinearLayout connectionsPage, messagesPage, nodesPage, chatList;
    private ScrollView chatScroll;
    private Spinner channels;
    private EditText compose;
    private TextView chatTitle, chatHint, mtStatus, mcStatus, nodeText, activeLabel;
    private int page = 0;
    private boolean resumed,radioServiceRunning,closing,disconnectDialogVisible;
    private String notificationText="";
    private final ArrayDeque<String> connectionWarnings=new ArrayDeque<>();
    private final Map<String,Runnable> pendingBleActions=new LinkedHashMap<>();
    private final Map<String,Runnable> pendingKnownConnections=new LinkedHashMap<>();
    private boolean knownConnectionTickPending;
    private boolean blePermissionPending,bleEnablePending,bleNeedsScan;
    private static final int BLE_PERMISSIONS=41, ENABLE_BLUETOOTH=409;
    private BluetoothAdapter bluetoothAdapter() {
        BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        return manager==null?null:manager.getAdapter();
    }
    private boolean bluetoothEnabled() {
        try {BluetoothAdapter adapter=bluetoothAdapter();return adapter!=null&&adapter.isEnabled();}
        catch(SecurityException denied){return false;}
    }
    private void reconcileBleState() {
        if(bluetoothEnabled())return;
        for(RadioSession s:sessions.values()) if(s.ble!=null) {
            boolean unexpected=s.connected;
            s.generation++;s.markPendingUnconfirmed();s.close();s.loading=false;s.pendingAudio=null;s.state="Bluetooth is off or permission is unavailable";
            if(unexpected&&!closing)showDisconnectWarning(s.ownName.isEmpty()?s.name:s.ownName);
        }
    }
    private final BroadcastReceiver bluetoothStateReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            int state=intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR);
            if(state==BluetoothAdapter.STATE_OFF||state==BluetoothAdapter.STATE_TURNING_OFF) {
                reconcileBleState();
                if(scanningSession!=null)stopScanning(scanningSession,"Bluetooth is off");
                publish();
            }
        }
    };
    private void queueBle(String key,boolean scan,Runnable action) {
        if(closing)return;
        pendingBleActions.put(key,action);bleNeedsScan|=scan;continueBleRequests();publish();
    }
    private void rejectBleRequests(String message) {
        boolean wasAvailable=connectingAvailable;
        connectingAvailable=false;
        // Do not reopen the permission/activation prompt for every queued radio.
        pendingKnownConnections.clear();
        pendingBleActions.clear();bleNeedsScan=false;blePermissionPending=false;bleEnablePending=false;
        if(scanningSession!=null){scanningSession.state=message;scanningSession.scanning=false;scanningSession=null;}
        if(pageReady)web.evaluateJavascript("window.onActionNotice('Bluetooth',"+JSONObject.quote(message)+")",null);
        if(wasAvailable){availableDiscoveryError=message;finishAvailableConnections();}
        publish();
    }
    private void continueBleRequests() {
        if(closing||pendingBleActions.isEmpty()||blePermissionPending||bleEnablePending)return;
        if(bluetoothAdapter()==null){rejectBleRequests("Bluetooth is not available on this phone");return;}
        List<String> missing=new ArrayList<>();
        String[] needed=Build.VERSION.SDK_INT>=31?new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN}:
                bleNeedsScan?new String[]{Manifest.permission.ACCESS_FINE_LOCATION}:new String[0];
        for(String permission:needed)if(checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED)missing.add(permission);
        if(!missing.isEmpty()) {blePermissionPending=true;requestPermissions(missing.toArray(new String[0]),BLE_PERMISSIONS);return;}
        if(!bluetoothEnabled()) {
            reconcileBleState();publish();bleEnablePending=true;
            if(pageReady)web.evaluateJavascript("window.onActionNotice('Bluetooth is off','Allow Android to turn Bluetooth on to continue connecting the selected radios.')",null);
            try {startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),ENABLE_BLUETOOTH);}
            catch(ActivityNotFoundException|SecurityException error){rejectBleRequests("Cannot request Bluetooth activation. Turn it on in Android settings, then connect again.");}
            return;
        }
        List<Runnable> actions=new ArrayList<>(pendingBleActions.values());pendingBleActions.clear();bleNeedsScan=false;
        for(Runnable action:actions)if(!closing)action.run();
    }
    void closeFromRecents() {
        debug("Lifecycle closing");closing=true;pendingBleActions.clear();
        for(RadioSession s:sessions.values()){s.generation++;s.markPendingUnconfirmed();s.close();}
        getSystemService(android.app.NotificationManager.class).cancel(201);
        finish();
    }
    private void showDisconnectWarning(String name) {
        if(closing)return;
        debug("Unexpected device disconnection");
        playDisconnectSound();connectionWarnings.add(name);displayConnectionWarning();
    }
    private void displayConnectionWarning() {
        if(!resumed||closing||disconnectDialogVisible||connectionWarnings.isEmpty())return;
        disconnectDialogVisible=true;
        if(pageReady)web.evaluateJavascript("window.beforeConnectionWarning&&window.beforeConnectionWarning()",null);
        new AlertDialog.Builder(this).setTitle("Radio disconnected")
            .setMessage(connectionWarnings.remove()+" disconnected unexpectedly. Your draft is kept. Reconnect the radio to send.")
            .setCancelable(false).setPositiveButton("OK",(dialog,which)->{}).setOnDismissListener(dialog->{
                disconnectDialogVisible=false;
                if(pageReady&&!closing)web.evaluateJavascript("window.afterConnectionWarning&&window.afterConnectionWarning()",null);
                displayConnectionWarning();
            }).show();
    }
    private boolean visible;
    @Override protected void onStart() {
        super.onStart();visible=true;MessageNotifications.cancel(this);
    }
    @Override protected void onStop() {
        visible=false;super.onStop();
    }
    @Override protected void onResume() {
        super.onResume();debug("Lifecycle resume");if(closing)return;resumed=true;reconcileBleState();notificationText="";updateRunningNotification();displayConnectionWarning();publish();
    }
    @Override protected void onPause() {
        debug("Lifecycle pause");resumed=false;
        if(chatStore==null||chatStore.unreadCount()==0)getSystemService(android.app.NotificationManager.class).cancel(201);notificationText="";
        super.onPause();
    }
    private void updateRunningNotification() {
        if(closing)return;
        int count=0;for(RadioSession session:sessions.values())if(session.connected)count++;
        String text=count>0?count+" radio connection"+(count==1?"":"s")+" active · tap to return":"App open · no radios connected · tap to return";
        int unread=chatStore==null?0:chatStore.unreadCount();
        if(unread>0)text=unread+" unread · "+text;
        if(text.equals(notificationText))return;
        try {
            if(count>0) {
                getSystemService(android.app.NotificationManager.class).cancel(201);
                Intent intent=new Intent(this,RadioConnectionService.class).putExtra("text",text).putExtra("unread",unread);
                if(radioServiceRunning)startService(intent);
                else {if(!resumed)return;startForegroundService(intent);radioServiceRunning=true;}
            } else {
                if(radioServiceRunning){stopService(new Intent(this,RadioConnectionService.class));radioServiceRunning=false;}
                if((resumed||unread>0)&&(Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED))
                    getSystemService(android.app.NotificationManager.class).notify(201,RadioConnectionService.notification(this,text,unread));
            }
            notificationText=text;
        } catch(IllegalStateException|SecurityException unavailable) {notificationText="";}
    }
    private WebView web;
    private boolean pageReady;
    private static final int EXPORT_JSON=401, EXPORT_CSV=402, IMPORT_JSON=403, EXPORT_DM_CSV=404, PICK_SOUND=405,
            EXPORT_APP_JSON=406, IMPORT_APP_JSON=407;
    private String transferRadio, transferNode, transferPeer;
    private static final int EXPORT_CHANNEL_CSV=408;
    private int transferChannel;
    private String pickingSound;
    private long lastSoundAt;

    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!USB_PERMISSION.equals(intent.getAction())) return;
            RadioSession session = awaitingUsbSession;
            UsbSerialDriver driver = awaitingUsb;
            awaitingUsbSession = null; awaitingUsb = null;
            if (session == null || driver == null || session.usb==null || !session.loading) return;
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) session.usb.connect(driver);
            else session.updateStatus("USB permission denied");
        }
    };

    private final class RadioSession implements RadioProtocol.Events {
        final String key;
        String name;
        boolean meshcore;
        final Map<Integer, String> channelNames = new TreeMap<>();
        final Map<Integer, List<ChatEntry>> chats = new TreeMap<>();
        final Map<String, List<ChatEntry>> directs = new TreeMap<>();
        int historyRevision;
        final java.util.Set<String> historyRequests=new java.util.HashSet<>();
        final Map<String,Long> readThrough=new java.util.HashMap<>();
        final Map<String, NodeEntry> knownNodes = new TreeMap<>();
        final ArrayDeque<ChatEntry> pendingSends = new ArrayDeque<>();
        final Map<Long,ChatEntry> trackedMessages=new java.util.LinkedHashMap<>();
        final ArrayDeque<SplitSend> splitSends=new ArrayDeque<>();
        long nextSplitSend;
        final Map<byte[],Long> outgoingWrites=new java.util.IdentityHashMap<>();
        void sendTracked(byte[] payload) {
            outgoingWrites.put(payload,protocol.lastRequestId()); send(payload);
        }
        void written(byte[] payload,boolean success,String reason) {
            debug(key+" write bytes="+payload.length+" success="+success+(success?"":" reason="+reason));
            Long token=outgoingWrites.remove(payload); if(token==null) return;
            ChatEntry entry=trackedMessages.get(token);
            if(!success) {
                if(entry!=null) delivery(token,"failed: "+reason);
                else if(pendingTrace==token) {
                    pendingTrace=0;
                    showTrace(this,new TraceResult(token,traceTarget,new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),reason));
                }
                protocol.expireRequest(token);
            } else if(entry!=null&&entry.delivery.equals("queued")) delivery(token,"sent_to_radio");
        }
        long pendingTrace;
        String traceTarget="",pendingTraceKind="tr";
        JSONObject lastTrace;

        void track(ChatEntry entry, long token) {
            trackedMessages.put(token,entry);
            int currentGeneration=generation;
            main.postDelayed(() -> {
                if(generation!=currentGeneration || trackedMessages.get(token)!=entry) return;
                if(protocol.automaticDeliveryPending(token)) return;
                if(entry.delivery.equals("queued")||entry.delivery.equals("sent_to_radio")||entry.delivery.equals("accepted")||entry.delivery.equals("relayed")||
                        (meshcore&&entry.delivery.equals("sent_radio")&&directs.values().stream().anyMatch(list->list.contains(entry)))) {
                    entry.delivery="unconfirmed"; chatStore.setDelivery(entry.id,entry.delivery);
                    protocol.expireRequest(token); publish();
                }
            },120000);
            if(trackedMessages.size()>256) trackedMessages.remove(trackedMessages.keySet().iterator().next());
        }
        @Override public void delivery(long token,String status) {
            int currentGeneration=generation;
            main.post(() -> {
                if(generation!=currentGeneration) return;
                ChatEntry entry=trackedMessages.get(token); if(entry==null) return;
                // A late queue response or local relay report cannot undo delivery confirmation.
                if((entry.delivery.equals("delivered")||entry.delivery.equals("delivered_mesh")) && !status.equals("delivered")) return;
                if(status.equals("accepted")&&!entry.delivery.equals("queued")&&!entry.delivery.equals("sent_to_radio")) return;
                if(status.equals("sent_to_radio")&&!entry.delivery.equals("queued")) return;
                entry.delivery=status; chatStore.setDelivery(entry.id,status); publish();
            });
        }
        final Map<String,JSONArray> pendingPaths=new java.util.LinkedHashMap<>();
        String pathKey(int channel,long stamp,String text) {return channel+":"+stamp+":"+text;}
        JSONObject pathRecord(String[] hashes,int snr,int rssi) {
            JSONObject item=new JSONObject();
            try {item.put("path",new JSONArray(java.util.Arrays.asList(hashes))).put("snr",snr).put("rssi",rssi);} catch(JSONException ignored) {}
            return item;
        }
        void addPath(ChatEntry entry,JSONObject record) {
            try {
                JSONArray records=new JSONArray(entry.paths);
                if(records.length()>=32) records.remove(0);
                records.put(record);entry.paths=records.toString();chatStore.setPaths(entry.id,entry.paths);
            } catch(JSONException ignored) {}
        }
        @Override public void heardPath(long token,String[] hashes,int snr,int rssi) {
            int activeGeneration=generation;
            main.post(()->{if(generation!=activeGeneration)return;ChatEntry entry=trackedMessages.get(token);
                if(entry!=null) {addPath(entry,pathRecord(hashes,snr,rssi));publish();}});
        }
        @Override public void channelPath(int channel,long stamp,String text,String[] hashes,int snr,int rssi) {
            int activeGeneration=generation;
            main.post(()->{
                if(generation!=activeGeneration)return;
                JSONObject record=pathRecord(hashes,snr,rssi);
                for(ChatEntry entry:chats.getOrDefault(channel,java.util.Collections.emptyList()))
                    if(!entry.own&&entry.receivedAt==stamp&&entry.text.equals(text)) {addPath(entry,record);publish();return;}
                String key=pathKey(channel,stamp,text);
                if(pendingPaths.size()>=256) pendingPaths.remove(pendingPaths.keySet().iterator().next());
                JSONArray paths=pendingPaths.computeIfAbsent(key,k->new JSONArray());
                if(paths.length()>=32)paths.remove(0);paths.put(record);
            });
        }
        @Override public void heardRepeat(long token) {
            int currentGeneration=generation;
            main.post(() -> {
                if(generation!=currentGeneration) return;
                ChatEntry entry=trackedMessages.get(token);
                if(entry==null||entry.heardRepeats<0||entry.heardRepeats==Integer.MAX_VALUE) return;
                entry.heardRepeats++;
                chatStore.setHeardRepeats(entry.id,entry.heardRepeats);publish();
            });
        }
        @Override public void trace(TraceResult result) {
            int currentGeneration=generation;
            main.post(() -> {
                if(generation!=currentGeneration || pendingTrace!=result.requestId) return;
                pendingTrace=0;
                showTrace(this,result);
            });
        }
        @Override public void advertExport(String uri) {
            int currentGeneration=generation;
            main.post(() -> { if(generation==currentGeneration) {copyText(uri);showAdvertResult(this,"Advert link copied to clipboard",true);} });
        }
        RadioProtocol protocol;
        BleRadio ble;
        UsbRadio usb;
        String state = "Disconnected";
        String requestedRadio="", requestedId="";
        boolean knownOnly;
        String usbDeviceName="";
        boolean typeVerified;
        long lastConnectionActivity, loadingStartedAt;
        int loadingMessages;
        boolean connected, syncSucceeded;
        boolean loading;
        String pendingAudio;
        boolean scanning;
        int scanProgress;
        String scanReturnState = "Disconnected";
        String ownId = "", ownName = "", bleAddress = "";
        boolean unread;
        int generation;
        double ownLatitude = Double.NaN, ownLongitude = Double.NaN;

        RadioSession(String name, boolean meshcore) {
            this.name = name; this.meshcore = meshcore;
            this.key=(meshcore?"mc":"mt")+"-"+(++sessionSerial);
        }

        boolean matches(String radio,String id) {
            return (id.equals(ownId)&&radio.equals(meshcore?"mc":"mt")) ||
                (id.equals(requestedId)&&radio.equals(requestedRadio));
        }
        void detected(boolean detectedMeshcore) {
            if(meshcore!=detectedMeshcore) {
                markPendingUnconfirmed();ownId="";ownName="";chats.clear();directs.clear();
                knownNodes.clear();channelNames.clear();
            }
            boolean changed=meshcore!=detectedMeshcore;
            meshcore=detectedMeshcore;name=meshcore?"MeshCore":"Meshtastic";
            if(changed) {
                RadioSession fallback=null;
                for(RadioSession candidate:sessions.values()) if(candidate!=this&&candidate.meshcore!=meshcore) {
                    fallback=candidate;if(candidate.connected)break;
                }
                if(fallback==null) {
                    fallback=new RadioSession(meshcore?"Meshtastic":"MeshCore",!meshcore);
                    sessions.put(fallback.key,fallback);
                }
                if(meshcore&&selectedMt==this)selectedMt=fallback;
                if(!meshcore&&selectedMc==this)selectedMc=fallback;
            }
            if(meshcore)selectedMc=this;else selectedMt=this;
            protocol=meshcore?new MeshCoreProtocol():new MeshtasticProtocol();
            configureAttempts(this);
            typeVerified=true;
            debug(key+" detected="+(meshcore?"MC":"MT"));publish();
        }
        void start() {
            syncSucceeded=false;
            generation++;
            close();
            protocol = meshcore ? new MeshCoreProtocol() : new MeshtasticProtocol();
            configureAttempts(this);
            markPendingUnconfirmed();
            knownNodes.clear(); pendingSends.clear(); connected = false;
            ownLatitude = Double.NaN; ownLongitude = Double.NaN;
            channelNames.clear();
            if(!ownId.isEmpty()) loadSessionHistory(this);
            bleAddress = ""; unread = false;
            loading = true; pendingAudio = null; typeVerified=false;
            int attempt=generation;lastConnectionActivity=loadingStartedAt=SystemClock.elapsedRealtime();loadingMessages=0;
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    if(generation!=attempt||!loading)return;
                    if(SystemClock.elapsedRealtime()-lastConnectionActivity<60000) {main.postDelayed(this,15000);return;}
                    markPendingUnconfirmed();generation++;close();loading=false;
                    updateStatus("Connection/loading timed out. Reconnect to try again.");
                }
            },60000);
            updateStatus("Connecting…");
            publish();
        }

        void close() {
            if(closing)stopSplits(this,"App closed; unsent split parts cancelled");
            if (ble != null) { ble.close(); ble = null; }
            if (usb != null) { usb.close(); usb = null; }
            connected = false;
        }

        void disconnect() {
            generation++;
            close();
            protocol = null;
            markPendingUnconfirmed();
            knownNodes.clear(); channelNames.clear(); pendingSends.clear();
            chats.clear(); directs.clear(); ownId = ""; ownName = ""; unread = false;
            loading = false; pendingAudio = null;
            updateStatus("Disconnected");
            publish();
        }

        void markPendingUnconfirmed() {
            pendingPaths.clear();
            if(protocol!=null) protocol.cancelAll();
            if(pendingTrace!=0) showTrace(this,new TraceResult(pendingTrace,traceTarget,
                    new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),
                    "Radio disconnected before the trace completed. Reconnect and try again."));
            for(ChatEntry entry:trackedMessages.values()) {
                if(entry.delivery.equals("queued")||entry.delivery.equals("sent_to_radio")||entry.delivery.equals("accepted")||entry.delivery.equals("relayed")||entry.delivery.startsWith("sending:")) {
                    entry.delivery="unconfirmed"; chatStore.setDelivery(entry.id,entry.delivery);
                }
            }
            trackedMessages.clear(); outgoingWrites.clear(); pendingTrace=0; lastTrace=null;
            for (ChatEntry entry : pendingSends) {
                entry.delivery = "unconfirmed";
                if (chatStore != null) chatStore.setDelivery(entry.id, entry.delivery);
            }
        }

        void ready() { ready(true); }
        void ready(boolean sendInitial) {
            if(ble!=null&&!bluetoothEnabled()){reconcileBleState();publish();return;}
            connected = true; updateStatus("Connected");
            if (protocol != null) {byte[] initial=protocol.initialCommand();if(sendInitial)send(initial);}
            int activeGeneration=generation;
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    if(generation!=activeGeneration||!connected||protocol==null) return;
                    byte[] next=protocol.poll(RadioSession.this);
                    if(next!=null) sendTracked(next);
                    main.postDelayed(this,250);
                }
            },250);
        }

        void send(byte[] payload) {
            debug(key+" TX bytes="+payload.length+(meshcore&&payload.length>0?" type="+(payload[0]&255):""));
            if (ble != null) ble.send(payload);
            else if (usb != null) usb.send(payload);
        }

        void receive(byte[] payload) {
            lastConnectionActivity=SystemClock.elapsedRealtime();
            debug(key+" RX bytes="+payload.length+(meshcore&&payload.length>0?" type="+(payload[0]&255):""));
            if (protocol == null) return;
            if(meshcore&&loading&&payload.length>0&&(payload[0]==7||payload[0]==8||payload[0]==16||payload[0]==17||payload[0]==27))loadingMessages++;
            protocol.receive(payload, this);
            byte[] next = protocol.nextCommand();
            if (next != null) send(next);
        }

        void updateStatus(String text) {
            int currentGeneration = generation;
            main.post(() -> {
                if (generation != currentGeneration) return;
                if (text.startsWith("BLE disconnected") || text.startsWith("USB disconnected") || text.startsWith("USB read failed")) {
                    boolean wasConnected=connected;
                    markPendingUnconfirmed(); connected = false; loading = false; pendingAudio = null;
                    if(wasConnected) showDisconnectWarning(ownName.isEmpty()?name:ownName);
                }
                if (text.startsWith("No USB serial") || text.startsWith("USB permission denied") ||
                        text.startsWith("USB open failed") || text.startsWith("BLE service discovery failed") ||
                        text.startsWith("Selected radio service not found") ||
                        text.startsWith("Required BLE characteristics missing") ||
                        text.startsWith("BLE notifications unavailable") ||
                        text.startsWith("BLE notification descriptor missing") ||
                        text.startsWith("BLE notification setup failed") || text.startsWith("USB permission or device open failed")) {close();loading = false;}
                debug(key+" "+text);
                state = text;
                if (this == mt && mtStatus != null) mtStatus.setText("Meshtastic · " + text);
                if (this == mc && mcStatus != null) mcStatus.setText("MeshCore · " + text);
                if (selected == this && activeLabel != null) activeLabel.setText(name + " · " + text);
                publish();
            });
        }

        @Override public void status(String text) { updateStatus(text); }
        @Override public void syncComplete() {
            int currentGeneration=generation;
            main.post(() -> {
                if(generation!=currentGeneration) return;
                loading=false;syncSucceeded=true;debug(key+" sync complete elapsedMs="+(SystemClock.elapsedRealtime()-loadingStartedAt)+" messages="+loadingMessages+" nodes="+knownNodes.size()+" channels="+channelNames.size());
                if(pendingAudio!=null) playMessageSound(pendingAudio);
                pendingAudio=null;
                publish();
            });
        }
        @Override public void syncFailed(String reason) {
            int currentGeneration=generation;
            main.post(()->{
                if(generation!=currentGeneration)return;
                loading=false;syncSucceeded=false;pendingAudio=null;updateStatus(reason);
                if(pageReady)web.evaluateJavascript("window.onActionNotice('Radio loading error',"+JSONObject.quote(reason)+")",null);
                publish();
            });
        }
        @Override public void ownNode(String id, String name) {
            int currentGeneration = generation;
            main.post(() -> {
                if (generation != currentGeneration || id.isEmpty()) return;
                String radio = meshcore ? "mc" : "mt";
                if(knownOnly&&chatStore.getRadio(radio,id)==null){generation++;close();connected=false;loading=false;updateStatus("Discovered radio is not saved; add it manually");return;}
                if(!requestedId.isEmpty()&&(!requestedId.equals(id)||!requestedRadio.equals(radio))){generation++;close();connected=false;loading=false;updateStatus("Radio identity differs from the saved device");return;}
                for(RadioSession other:sessions.values())if(other!=this&&other.connected&&other.meshcore==meshcore&&other.ownId.equals(id)) {
                    generation++;close();connected=false;loading=false;updateStatus("This radio is already connected");return;
                }
                if (!id.equals(ownId)) {
                    readThrough.clear();
                    ownId = id; ownName = name; chats.clear(); directs.clear();
                    loadSessionHistory(this);
                }
                NodeEntry local = knownNodes.get(id);
                if (local != null && !local.name.isEmpty()) ownName = local.name;
                else if (!name.isEmpty() && (!name.equals("Meshtastic " + id) || ownName.isEmpty())) ownName = name;
                chatStore.saveRadio(radio, id, ownName, bleAddress);
                requestedId="";requestedRadio="";
                if (meshcore) selectedMc=this; else selectedMt=this;
                for (Map.Entry<Integer,String> item : channelNames.entrySet())
                    chatStore.saveChannel(radio,id,item.getKey(),item.getValue());
                publish();
            });
        }
        @Override public void reaction(int channel,String sender,String peer,String text,long time,long packetId,long replyId) {
            int currentGeneration=generation;
            main.post(()->{
                if(generation!=currentGeneration||ownId.isEmpty()||text.isEmpty()||chatStore.hasPacket("mt",ownId,packetId))return;
                ChatEntry entry=new ChatEntry(sender,text,false,time);entry.packetId=packetId;entry.replyId=replyId;entry.reaction=true;
                entry.id=chatStore.save("mt",ownId,peer.isEmpty()?channel:-1,sender,text,entry.time,time,false,"",peer);
                chatStore.setReferences(entry.id,packetId,replyId);chatStore.setReaction(entry.id,true);
                if(peer.isEmpty())chats.computeIfAbsent(channel,ignored->new ArrayList<>()).add(entry);
                else directs.computeIfAbsent(peer,ignored->new ArrayList<>()).add(entry);
                publish();
            });
        }
        @Override public void lastHeard(String id,long seconds) {
            int currentGeneration=generation;
            main.post(()->{if(generation!=currentGeneration) return;chatStore.heard(meshcore?"mc":"mt",ownId,id,seconds);publishRadioUpdate();});
        }
        @Override public void signalQuality(long requestId,String target,String result) {
            int currentGeneration=generation;
            main.post(()->{if(generation!=currentGeneration||closing)return;
                NodeEntry contact=knownNodes.get(target);
                new AlertDialog.Builder(MainActivity.this).setTitle("Signal Quality · "+(contact==null?target:contact.name))
                    .setMessage(result).setPositiveButton("OK",null).show();
            });
        }
        @Override public void advertResult(String text,boolean success) {
            int currentGeneration=generation;
            main.post(()->{if(generation==currentGeneration) showAdvertResult(this,text,success);});
        }
        @Override public void message(int channel,String sender,String text,long receivedAt,int hops) {channelMessage(channel,sender,text,receivedAt,hops,0,0);}
        @Override public void channelMessage(int channel, String sender, String text, long receivedAt, int hops,long packetId,long replyId) {
            int currentGeneration = generation;
            main.post(() -> {
                if (generation != currentGeneration) return;
                if (ownId.isEmpty()) return;
                if ((receivedAt>0 || loading) && chatStore.hasMessage(meshcore ? "mc" : "mt",ownId,channel,"",sender,text,receivedAt,false)) return;
                ChatEntry entry = new ChatEntry(sender, text, false, receivedAt);
                entry.hops=hops;entry.packetId=packetId;entry.replyId=replyId;entry.unread=true;
                JSONArray captured=pendingPaths.remove(pathKey(channel,receivedAt,text));
                if(captured!=null)entry.paths=captured.toString();
                entry.id=chatStore.saveIncoming(meshcore?"mc":"mt",ownId,channel,entry.sender,
                    entry.text,entry.time,entry.receivedAt,"",hops,packetId,replyId,entry.paths);
                chats.computeIfAbsent(channel, ignored -> new ArrayList<>()).add(entry);
                unread = true;
                showMessageNotification(this, sender, text);
                if(loading) pendingAudio=meshcore?"mc":"mt"; else playMessageSound(meshcore?"mc":"mt");
                if (selected == this && page == 1 && selectedChannel() == channel) renderChat();
                publishRadioUpdate();
            });
        }
        @Override public void directMessage(String id,String text,long receivedAt,int hops) {directPacket(id,text,receivedAt,hops,0,0);}
        @Override public void directPacket(String id, String text, long receivedAt, int hops,long packetId,long replyId) {
            int currentGeneration = generation;
            main.post(() -> {
                if (generation != currentGeneration) return;
                if (ownId.isEmpty()) return;
                if ((receivedAt>0 || loading) && chatStore.hasMessage(meshcore ? "mc" : "mt",ownId,-1,id,id,text,receivedAt,false)) return;
                ChatEntry entry = new ChatEntry(id, text, false, receivedAt);
                entry.hops=hops;entry.packetId=packetId;entry.replyId=replyId;entry.unread=true;
                entry.id=chatStore.saveIncoming(meshcore?"mc":"mt",ownId,-1,id,text,entry.time,
                    entry.receivedAt,id,hops,packetId,replyId,entry.paths);
                chatStore.heard(meshcore?"mc":"mt",ownId,id,receivedAt);
                directs.computeIfAbsent(id, ignored -> new ArrayList<>()).add(entry);
                unread = true;
                showMessageNotification(this, id, text);
                if(loading) pendingAudio="dm"; else playMessageSound("dm");
                publishRadioUpdate();
            });
        }
        @Override public void node(String id, String name, String role, String model, int channel, int hops, double latitude, double longitude) {
            int currentGeneration = generation;
            main.post(() -> {
                if (generation != currentGeneration) return;
                NodeEntry old = knownNodes.get(id);
                knownNodes.put(id, new NodeEntry(id,
                        name.isEmpty() && old != null ? old.name : name,
                        role.isEmpty() && old != null ? old.role : role,
                        model.isEmpty() && old != null ? old.model : model,
                        channel,
                        hops < 0 && old != null ? old.hops : hops,
                        !Double.isFinite(latitude) && old != null ? old.latitude : latitude,
                        !Double.isFinite(longitude) && old != null ? old.longitude : longitude));
                if (id.equals(ownId) && !name.isEmpty()) {
                    ownName = name;
                    chatStore.saveRadio(meshcore ? "mc" : "mt", id, name, bleAddress);
                }
                if(!ownId.isEmpty()&&!name.isEmpty())getPreferences(0).edit()
                        .putString("contact_name:"+(meshcore?"mc":"mt")+":"+ownId+":"+id,name).apply();
                if (selected == this && page == 2) renderNodes();
                publishRadioUpdate();
            });
        }
        @Override public void position(String id, double latitude, double longitude) {
            int currentGeneration = generation;
            main.post(() -> {
                if (generation != currentGeneration) return;
                NodeEntry old = knownNodes.get(id);
                if (old != null) knownNodes.put(id, new NodeEntry(id, old.name, old.role,
                        old.model, old.channel, old.hops, latitude, longitude));
                publishRadioUpdate();
            });
        }
        @Override public void ownPosition(double latitude, double longitude) {
            int currentGeneration = generation;
            main.post(() -> {
                if (generation != currentGeneration) return;
                if (latitude == 0 && longitude == 0) return;
                ownLatitude = latitude; ownLongitude = longitude; publishRadioUpdate();
            });
        }
        @Override public void actionResult(String text) {
            int currentGeneration = generation;
            main.post(() -> {
                if (generation != currentGeneration) return;
                updateStatus(text);
                if (pageReady) web.evaluateJavascript("window.onNodeActionResult(" + JSONObject.quote(text) + ","+JSONObject.quote(key)+")", null);
            });
        }
        @Override public void sendResult(boolean accepted, String detail) {
            int currentGeneration = generation;
            main.post(() -> {
                if (generation != currentGeneration) return;
                ChatEntry entry = pendingSends.poll();
                if (entry != null) {
                    entry.delivery = accepted ? "sent" : "failed";
                    chatStore.setDelivery(entry.id, entry.delivery);
                }
                if (!accepted) updateStatus(detail);
                publish();
            });
        }
        @Override public void channel(int index, String name) {
            int currentGeneration = generation;
            main.post(() -> {
                if (generation != currentGeneration) return;
                channelNames.put(index, name.isEmpty() ? "Channel " + index : name);
                if (!ownId.isEmpty()) chatStore.saveChannel(meshcore ? "mc" : "mt", ownId, index, channelNames.get(index));
                if (selected == this) refreshChannels(index);
                publishRadioUpdate();
            });
        }
    }

    private static String displayTime(long seconds) {
        if(seconds<=0)return "Time unavailable";
        Date date=new Date(seconds*1000);
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)+" "+new java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(date);
    }
    private static final class ChatEntry {
        boolean reaction;
        long id = -1, packetId=0, replyId=0;
        final String sender, text, time;
        final long receivedAt;
        final boolean own;
        String delivery;
        String paths="[]";
        boolean unread;
        int hops=-1, heardRepeats=-1;
        ChatEntry(String sender, String text, boolean own) {
            this(sender,text,own,System.currentTimeMillis()/1000);
        }
        ChatEntry(String sender, String text, boolean own, long receivedAt) {
            this.sender = sender; this.text = text; this.own = own;
            this.delivery = own ? "queued" : "";
            this.receivedAt = receivedAt;
            this.time = displayTime(receivedAt);
        }
        ChatEntry(ChatStore.Message saved) {
            reaction=saved.reaction;id = saved.id; packetId=saved.packetId;replyId=saved.replyId; sender = saved.sender; text = saved.text; time = displayTime(saved.receivedAt);
            receivedAt = saved.receivedAt; own = saved.own; delivery = saved.delivery; unread=saved.unread; hops=saved.hops; heardRepeats=saved.heardRepeats;paths=saved.paths;
        }
    }

    private static final class NodeEntry {
        final String id, name, role, model;
        final int channel, hops;
        final double latitude, longitude;
        NodeEntry(String id, String name, String role, String model, int channel, int hops, double latitude, double longitude) {
            this.id = id; this.name = name; this.role = role; this.model = model;
            this.channel = channel; this.hops = hops; this.latitude = latitude; this.longitude = longitude;
        }
    }

    private static final class ChannelChoice {
        final int index;
        final String name;
        ChannelChoice(int index, String name) { this.index = index; this.name = name; }
        @Override public String toString() { return name + "  ·  " + index; }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        debugLog=new DebugLog(getFilesDir());
        String appVersion="unknown";
        try{appVersion=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        debug("App start version="+appVersion+" Android="+Build.VERSION.RELEASE+" model="+Build.MODEL);
        debugLog.captureCrashes();
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::navigateBack);
        sessions.put(mt.key,mt); sessions.put(mc.key,mc);
        RadioConnectionService.owner=new java.lang.ref.WeakReference<>(this);
        chatStore = new ChatStore(this);
        chatStore.markInterruptedSends();
        try{String pending=chatStore.pendingSettings();if(pending!=null){mergeSettings(new JSONObject(pending));chatStore.clearPendingSettings();}}catch(Exception error){debug("Backup settings recovery pending");}
        try {sendChain=new SendChain(getFilesDir());}catch(Exception error){debug("Cannot restore sending chain: "+error.getClass().getSimpleName());}
        main.postDelayed(chainRunner,1000);
        main.postDelayed(splitRunner,100);
        // Android 15+ draws apps edge to edge. Keep the entire web viewport inside
        // the system bars so its fixed header, composer, and bottom tabs remain usable.
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(0xff0d1117);
        getWindow().setNavigationBarColor(0xff0d1117);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbPermissionReceiver, new IntentFilter(USB_PERMISSION), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(usbPermissionReceiver, new IntentFilter(USB_PERMISSION));
        if(Build.VERSION.SDK_INT>=33)registerReceiver(bluetoothStateReceiver,new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),Context.RECEIVER_EXPORTED);
        else registerReceiver(bluetoothStateReceiver,new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        WebViewAssetLoader assets = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assets.shouldInterceptRequest(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();
                if (APP_URL.equals(target.toString())) return false;
                String scheme = target.getScheme();
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, target)); }
                    catch (ActivityNotFoundException error) { android.util.Log.w("OverMesh", "No browser for link", error); }
                }
                return true;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                Uri resource=request.getUrl();
                // Keep queries, fragments and external paths (which can contain secrets) out of exports.
                String location=resource.getScheme()+"://"+resource.getHost();
                if("appassets.androidplatform.net".equals(resource.getHost())) location+=resource.getPath();
                debug("WebView load error code="+error.getErrorCode()+" mainFrame="+request.isForMainFrame()+" resource="+location);
            }
            @Override public void onPageFinished(WebView view, String url) {
                debug("WebView page finished app="+APP_URL.equals(url));
                pageReady = APP_URL.equals(url); publish();
            }
        });
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        FrameLayout safeArea = new FrameLayout(this);
        safeArea.setBackgroundColor(0xff0d1117);
        safeArea.addView(web, new FrameLayout.LayoutParams(-1, -1));
        safeArea.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets keyboard = insets.getInsets(WindowInsets.Type.ime());
                left = bars.left; top = bars.top; right = bars.right;
                bottom = Math.max(bars.bottom, keyboard.bottom);
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
        setContentView(safeArea);
        safeArea.requestApplyInsets();
        web.loadUrl(APP_URL);
    }

    private void navigateBack() {
        if (pageReady && web != null) web.evaluateJavascript("window.navigateBack()", null);
    }
    @Override public void onBackPressed() { navigateBack(); }

    private boolean backgroundMessagesAllowed() {
        return getPreferences(0).getBoolean("background_message_notifications",false) && MessageNotifications.allowed(this);
    }
    private void showMessageNotification(RadioSession s, String sender, String text) {
        if (visible || closing || !backgroundMessagesAllowed()) return;
        NodeEntry contact = s.knownNodes.get(sender);
        String name = contact != null && !contact.name.isEmpty() ? contact.name : sender;
        MessageNotifications.show(this,(s.meshcore ? "MC" : "MT") + " · " + s.ownName + " · " + name,text);
    }

    private final class Bridge {
        @JavascriptInterface public void showActionNotice(String title,String text) {
            main.post(() -> {
                if(visible && !closing && !isFinishing())
                    new AlertDialog.Builder(MainActivity.this).setTitle(title).setMessage(text).setPositiveButton("OK",null).show();
            });
        }
        @JavascriptInterface public void openSavedChat(String radio,String id,int channel){main.post(()->{
            ChatStore.KnownRadio known=chatStore.getRadio(radio,id);if(known==null)return;
            RadioSession target=null;for(RadioSession s:sessions.values())if(s.matches(radio,id)){target=s;if(s.connected)break;}
            if(target==null){target=new RadioSession(known.name,radio.equals("mc"));target.ownId=id;target.ownName=known.name;sessions.put(target.key,target);loadSessionHistory(target);}
            chatLeft=target;publish();if(pageReady)web.evaluateJavascript("window.openSavedChatView("+channel+")",null);
        });}
        @JavascriptInterface public void connectAllAvailable(){main.post(MainActivity.this::connectAllAvailable);}
        @JavascriptInterface public void sendingChain(){main.post(()->showChain());}
        @JavascriptInterface public void newMultiSend(){main.post(()->chooseMultiSend());}
        @JavascriptInterface public void setMultiSendEnabled(boolean enabled){main.post(()->{
            getPreferences(0).edit().putBoolean("multi_send_enabled",enabled).apply();
            try{if(!enabled&&sendChain!=null){sendChain.data.put("paused",true);sendChain.save();}}catch(Exception error){chainError(error);}publish();
        });}
        @JavascriptInterface public void setChannelObservationSeconds(int seconds){main.post(()->{if(seconds>=1&&seconds<=300)getPreferences(0).edit().putInt("mc_channel_observation_seconds",seconds).apply();publish();});}
        @JavascriptInterface public void setMessageSplitEnabled(boolean enabled){main.post(()->{getPreferences(0).edit().putBoolean("message_split_enabled",enabled).apply();publish();});}
        @JavascriptInterface public void setMessageSplitIntervalSeconds(int seconds){main.post(()->{if(seconds>=1&&seconds<=300)getPreferences(0).edit().putInt("message_split_interval_seconds",seconds).apply();publish();});}
        @JavascriptInterface public void setSettlingSeconds(int seconds){main.post(()->{if(seconds>=1&&seconds<=30)getPreferences(0).edit().putInt("send_settling_seconds",seconds).apply();publish();});}
        @JavascriptInterface public void setMeshCoreAttempts(int direct,int flood) {
            main.post(()->{
                if(direct<1||direct>10||flood<1||flood>10){nodeActionResult("Choose 1–10 attempts for each route type");return;}
                getPreferences(0).edit().putInt("mc_direct_attempts",direct).putInt("mc_flood_attempts",flood).apply();
                for(RadioSession s:sessions.values())configureAttempts(s);publish();
            });
        }
        @JavascriptInterface public void loadOlder(String key,String peer,int channel,long beforeId) {
            main.post(()->{
                RadioSession s=sessions.get(key);if(s==null||s.ownId.isEmpty()||beforeId<=0)return;
                String request=peer+":"+channel;
                if(!s.historyRequests.add(request))return;
                String identity=s.ownId;int revision=s.historyRevision;
                historyWorker.execute(()->{
                    try {
                        List<ChatStore.Message> rows=chatStore.page(s.meshcore?"mc":"mt",identity,peer,channel,beforeId);
                        main.post(()->{
                            s.historyRequests.remove(request);
                            if(closing||revision!=s.historyRevision||!identity.equals(s.ownId))return;
                            mergeHistory(s,rows);publish();
                            if(pageReady)web.evaluateJavascript("window.onHistoryPage("+JSONObject.quote(key)+","+JSONObject.quote(peer)+","+channel+","+rows.size()+")",null);
                        });
                    } catch(RuntimeException error) {main.post(()->{s.historyRequests.remove(request);nodeActionResult("Could not load older messages");if(pageReady&&!closing)web.evaluateJavascript("window.onHistoryPage("+JSONObject.quote(key)+","+JSONObject.quote(peer)+","+channel+",-1)",null);});}
                });
            });
        }
        @JavascriptInterface public void copyText(String text) { main.post(() -> MainActivity.this.copyText(text)); }
        @JavascriptInterface public int messagePartCount(String text,int limit) {
            try{return MessageChunks.split(text,limit).size();}
            catch(IllegalArgumentException error){return -1;}
        }
        @JavascriptInterface public void messageAction(String key,long id,String action) {
            main.post(() -> MainActivity.this.messageAction(key,id,action));
        }
        @JavascriptInterface public void sendAdvert(String key,String action) {
            main.post(() -> {
                RadioSession s=sessions.get(key);
                if(s==null||!s.connected||!s.meshcore||s.loading) {nodeActionResult("Choose a connected, fully loaded MC device");return;}
                try {
                    byte[] command=s.protocol.advert(action); long token=s.protocol.lastRequestId(); int generation=s.generation;
                    s.send(command); showAdvertResult(s,action.equals("copy")?"Requesting advert link…":"Sending advert…",false);
                    main.postDelayed(() -> {if(s.generation==generation&&s.protocol.requestPending(token)) {
                        s.protocol.expireRequest(token);showAdvertResult(s,"No response from radio to the advert request",false);
                    }},15000);
                } catch(IllegalArgumentException error) {nodeActionResult(error.getMessage());}
            });
        }
        @JavascriptInterface public void setTextSize(String kind,int step) {
            main.post(() -> {
                if(!kind.equals("composer")&&!kind.equals("side_mc")&&!kind.equals("side_mt")) return;
                getPreferences(0).edit().putInt(kind+"_font_step",Math.max(0,Math.min(6,step))).apply(); publish();
            });
        }
        @JavascriptInterface public void closeApp() { main.post(() -> finishAndRemoveTask()); }
        @JavascriptInterface public void exportChannelCsv(String key, int channel) {
            main.post(() -> MainActivity.this.exportChannelCsv(key, channel));
        }
        @JavascriptInterface public void getBackupChannels(String key) {
            main.post(() -> {
                RadioSession s = sessions.get(key);
                if (!pageReady) return;
                JSONArray channels = new JSONArray();
                try {
                    if (s != null && s.connected) for (Map.Entry<Integer,String> c : s.channelNames.entrySet())
                        channels.put(new JSONObject().put("index",c.getKey()).put("name",c.getValue()));
                    web.evaluateJavascript("window.onBackupChannels("+JSONObject.quote(key)+","+channels+")", null);
                } catch (JSONException e) { nodeActionResult("Could not load channel names"); }
            });
        }
        @JavascriptInterface public void connectBle(String id) {
            main.post(() -> { RadioSession s=newConnectionSession(id); if (s != null) MainActivity.this.connectBle(s); });
        }
        @JavascriptInterface public void connectUsb(String id) {
            main.post(() -> { RadioSession s=newConnectionSession(id); if (s != null) MainActivity.this.connectUsb(s); });
        }
        @JavascriptInterface public void disconnect(String id) {
            main.post(() -> { RadioSession s = session(id); if (s != null) MainActivity.this.disconnect(s); });
        }
        @JavascriptInterface public void stopScanning(String id) {
            main.post(() -> { RadioSession s = session(id); if (s != null) MainActivity.this.stopScanning(s, "Scan stopped"); });
        }
        @JavascriptInterface public void sendMessage(String id, int channel, String text) {
            main.post(() -> MainActivity.this.sendMessage(session(id), channel, text));
        }
        @JavascriptInterface public void nodeAction(String radio, String id, String action, String text) {
            main.post(() -> MainActivity.this.nodeAction(session(radio), id, action, text));
        }
        @JavascriptInterface public void setNodeName(String radio,String id,String name) {
            main.post(()->{
                if((!"mc".equals(radio)&&!"mt".equals(radio))||id==null||id.isEmpty()||name==null)return;
                String value=name.trim();
                if(value.length()>80){nodeActionResult("Use up to 80 characters for the name");return;}
                String key="node_name_"+radio+":"+id;
                SharedPreferences.Editor editor=getPreferences(0).edit();
                if(value.isEmpty())editor.remove(key);else editor.putString(key,value);
                if(!editor.commit()){nodeActionResult("Could not save node name");return;}
                publish();
            });
        }
        @JavascriptInterface public void connectKnown(String radio, String id) {
            main.post(() -> MainActivity.this.connectKnown(radio,id));
        }
        @JavascriptInterface public void exportDebugLog() {
            main.post(()->{
                debug("Debug log export requested");
                startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/plain").putExtra(Intent.EXTRA_TITLE,"SV8TTL-LoRa-Dash-debug-"+System.currentTimeMillis()+".txt"),EXPORT_DEBUG_LOG);
            });
        }
        @JavascriptInterface public void disconnectAll() {
            main.post(()->{
                connectingAvailable=false;availableRun++;availableUsb.clear();availableBle.clear();
                pendingKnownConnections.clear();
                pendingBleActions.clear();
                if(scanningSession!=null)MainActivity.this.stopScanning(scanningSession,"Scan cancelled");
                awaitingUsb=null;awaitingUsbSession=null;
                for(RadioSession s:sessions.values()) MainActivity.this.disconnect(s);
                publish();
            });
        }
        @JavascriptInterface public void disconnectKnown(String radio,String id) {
            main.post(() -> {
                pendingKnownConnections.remove(radio+":"+id);
                pendingBleActions.remove("known:"+radio+":"+id);
                for(RadioSession s:sessions.values()) if(s.matches(radio,id)) {
                    pendingBleActions.remove("device:"+s.key);
                    if(awaitingUsbSession==s){awaitingUsb=null;awaitingUsbSession=null;}
                    MainActivity.this.disconnect(s);
                }
                publish();
            });
        }
        @JavascriptInterface public void viewSaved(String radio, String id) {
            main.post(() -> MainActivity.this.viewSaved(radio,id));
        }
        @JavascriptInterface public void removeKnown(String radio, String id) {
            main.post(() -> MainActivity.this.removeKnown(radio,id));
        }
        @JavascriptInterface public void eraseOlder(String radio, String id, int days) {
            main.post(() -> MainActivity.this.eraseOlder(radio,id,days));
        }
        @JavascriptInterface public void exportNode(String radio, String id, String format) {
            main.post(() -> MainActivity.this.exportNode(radio,id,format));
        }
        @JavascriptInterface public void importNode() {
            main.post(MainActivity.this::importNode);
        }
        @JavascriptInterface public void chooseNodeExport() {main.post(MainActivity.this::chooseNodeExport);}
        @JavascriptInterface public void destinationSets() {main.post(MainActivity.this::showDestinationSets);}
        @JavascriptInterface public void exportAppBackup() {
            main.post(MainActivity.this::exportAppBackup);
        }
        @JavascriptInterface public void importAppBackup() {
            main.post(MainActivity.this::importAppBackup);
        }
        @JavascriptInterface public void markRead(String radio) {
            main.post(() -> { RadioSession s=session(radio); if(s!=null){s.unread=false;publish();} });
        }
        @JavascriptInterface public void markDirectRead(String radio,String peer) {
            main.post(() -> MainActivity.this.markDirectRead(radio,peer));
        }
        @JavascriptInterface public void markChannelRead(String radio,int channel) {
            main.post(() -> MainActivity.this.markChannelRead(radio,channel));
        }
        @JavascriptInterface public void clearDirect(String radio,String peer) {
            main.post(() -> MainActivity.this.clearDirect(radio,peer));
        }
        @JavascriptInterface public void eraseDirectOlder(String radio,String peer,int days) {
            main.post(() -> MainActivity.this.eraseDirectOlder(radio,peer,days));
        }
        @JavascriptInterface public void exportDirectCsv(String radio,String peer) {
            main.post(() -> MainActivity.this.exportDirectCsv(radio,peer));
        }
        @JavascriptInterface public void setBackgroundMessageNotifications(boolean enabled) {
            main.post(() -> {
                getPreferences(0).edit().putBoolean("background_message_notifications",enabled).apply();
                if(!enabled)MessageNotifications.cancel(MainActivity.this);
                else {
                    MessageNotifications.createChannel(MainActivity.this);
                    if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},40);
                }
                publish();
            });
        }
        @JavascriptInterface public void openMessageNotificationSettings() {
            main.post(() -> {
                MessageNotifications.createChannel(MainActivity.this);
                Intent intent=new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName())
                        .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,MessageNotifications.CHANNEL);
                try {startActivity(intent);}catch(ActivityNotFoundException unavailable){nodeActionResult("Android notification settings unavailable");}
            });
        }
        @JavascriptInterface public void setAudioEnabled(boolean enabled) {
            main.post(() -> {
                getPreferences(0).edit().putBoolean("message_audio",enabled).apply();
                publish();
            });
        }
        @JavascriptInterface public void setDisconnectAudioEnabled(boolean enabled) {
            main.post(()->{getPreferences(0).edit().putBoolean("disconnect_audio",enabled).apply();debug("Disconnect audio enabled="+enabled);publish();});
        }
        @JavascriptInterface public void diagnostic(String category,String detail) {
            if(category==null||detail==null)return;
            if(!category.matches("ui|js-error|js-rejection"))return;
            debug("Web "+category+" "+detail.replaceAll("[^a-zA-Z0-9_:. /-]","?").substring(0,Math.min(detail.length(),160)));
        }
        @JavascriptInterface public void pickSound(String kind) {
            main.post(() -> MainActivity.this.pickSound(kind));
        }
        @JavascriptInterface public void setSoundMode(String mode) {
            main.post(() -> { if("shared".equals(mode)||"separate".equals(mode))
                getPreferences(0).edit().putString("sound_mode",mode).apply(); publish(); });
        }
        @JavascriptInterface public void setSoundRepeats(String kind,int count) {
            main.post(() -> { if(validSoundKind(kind) && count>=1 && count<=3)
                getPreferences(0).edit().putInt("sound_repeats_"+kind,count).apply(); publish(); });
        }
        @JavascriptInterface public void setChatFontStep(int step) {
            main.post(() -> {
                getPreferences(0).edit().putInt("chat_font_step",Math.max(0,Math.min(6,step))).apply();
                publish();
            });
        }
        @JavascriptInterface public void finishComposing() {
            main.post(()->{if(web!=null){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(web.getWindowToken(),0);}});
        }
        @JavascriptInterface public void react(String key,long messageId,String number) {
            main.post(()->sendReaction(key,messageId,number));
        }
        @JavascriptInterface public void sendChannelReply(String key,int channel,String text,long replyId) {
            main.post(()->MainActivity.this.sendMessage(session(key),channel,text,replyId));
        }
        @JavascriptInterface public void about() {
            main.post(()->{
                try {
                    StringBuilder notices=new StringBuilder();
                    for(String file:getAssets().list("licenses")) {
                        try(java.io.InputStream input=getAssets().open("licenses/"+file)) { notices.append(file).append("\n\n").append(readNotice(input)).append("\n\n"); }
                    }
                    String version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;
                    web.evaluateJavascript("window.onAbout("+JSONObject.quote(version)+","+JSONObject.quote(notices.toString())+")",null);
                } catch(Exception error) {nodeActionResult("Could not load app notices");}
            });
        }
        @JavascriptInterface public void selectChatDevice(String side,String radio,String key) {
            main.post(()->{
                if(!side.equals("left")&&!side.equals("right"))return;
                RadioSession target;
                if(key.isEmpty()) target=new RadioSession("No device",false);
                else {
                    target=sessions.get(key);
                    if(key.startsWith("saved:")) {
                        String id=key.substring(6);
                        ChatStore.KnownRadio known=chatStore.getRadio(radio,id);if(known==null)return;
                        for(RadioSession candidate:sessions.values())if(candidate.ownId.equals(id)&&radio.equals(candidate.meshcore?"mc":"mt")){target=candidate;if(candidate.connected)break;}
                        if(target==null){target=new RadioSession(known.name,radio.equals("mc"));target.ownId=id;target.ownName=known.name;sessions.put(target.key,target);loadSessionHistory(target);}
                    }
                    if(target==null||!radio.equals(target.meshcore?"mc":"mt"))return;
                }
                if(side.equals("left"))chatLeft=target;else chatRight=target;
                publish();
            });
        }
        @JavascriptInterface public void deleteConversation(String key,String peer) {
            main.post(()->{
                RadioSession s=session(key);if(s==null||s.ownId.isEmpty()||peer==null)return;
                String radio=s.meshcore?"mc":"mt";
                List<ChatEntry> entries=s.directs.get(peer);
                if(entries!=null)for(ChatEntry entry:new ArrayList<>(entries)){
                    for(Map.Entry<Long,ChatEntry> tracked:s.trackedMessages.entrySet())if(tracked.getValue().id==entry.id&&s.protocol!=null)s.protocol.expireRequest(tracked.getKey());
                    s.trackedMessages.values().removeIf(tracked->tracked.id==entry.id);
                }
                chatStore.clearDirect(s.meshcore?"mc":"mt",s.ownId,peer);
                for(RadioSession other:sessions.values())if(other.meshcore==s.meshcore&&other.ownId.equals(s.ownId))loadSessionHistory(other);
                publish();
            });
        }
        @JavascriptInterface public void selectDevice(String radio,String key) {
            main.post(() -> {
                RadioSession s=sessions.get(key);
                if(key.startsWith("saved:")) {
                    String id=key.substring(6);ChatStore.KnownRadio known=chatStore.getRadio(radio,id);if(known==null)return;
                    for(RadioSession candidate:sessions.values()) if(candidate.ownId.equals(id)&&radio.equals(candidate.meshcore?"mc":"mt")) {s=candidate;if(candidate.connected)break;}
                    if(s==null) {s=new RadioSession(known.name,radio.equals("mc"));s.ownId=id;s.ownName=known.name;sessions.put(s.key,s);loadSessionHistory(s);}
                }
                if(s==null || !radio.equals(s.meshcore?"mc":"mt")) return;
                if(s.meshcore) selectedMc=s; else selectedMt=s;
                publish();
            });
        }
        @JavascriptInterface public void refresh() { main.post(MainActivity.this::publish); }
    }

    private RadioSession session(String id) {
        if ("left".equals(id)) return chatLeft;
        if ("right".equals(id)) return chatRight;
        if ("mt".equals(id)) return selectedMt;
        if ("mc".equals(id)) return selectedMc;
        return sessions.get(id);
    }
    private RadioSession newConnectionSession(String radio) {
        if(!"mt".equals(radio) && !"mc".equals(radio)) return null;
        RadioSession current=session(radio);
        if(current!=null && !current.connected && !current.loading && current.ownId.isEmpty()) return current;
        RadioSession next=new RadioSession("mc".equals(radio)?"MeshCore":"Meshtastic","mc".equals(radio));
        sessions.put(next.key,next);
        if(next.meshcore) selectedMc=next; else selectedMt=next;
        return next;
    }

    private boolean validSoundKind(String kind) {
        return "mc".equals(kind)||"mt".equals(kind)||"dm".equals(kind)||"shared".equals(kind)||"disconnect".equals(kind);
    }
    private void pickSound(String kind) {
        if(!validSoundKind(kind)) return;
        pickingSound=kind;
        Intent picker=new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        picker.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_NOTIFICATION);
        picker.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false);
        String saved=getPreferences(0).getString("sound_uri_"+kind,"");
        picker.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                saved.isEmpty()?RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION):Uri.parse(saved));
        try { startActivityForResult(picker,PICK_SOUND); }
        catch(ActivityNotFoundException error) { nodeActionResult("System sound picker unavailable"); }
    }
    private void playMessageSound(String kind) {
        if(!visible && !backgroundMessagesAllowed())return;
        if(!getPreferences(0).getBoolean("message_audio",true)) return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastSoundAt<700) return;
        lastSoundAt=now;
        String mode=getPreferences(0).getString("sound_mode","separate");
        String soundKind="shared".equals(mode)?"shared":kind;
        int count=getPreferences(0).getInt("sound_repeats_"+soundKind,1);
        String saved=getPreferences(0).getString("sound_uri_"+soundKind,"");
        Uri uri=saved.isEmpty()?RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION):Uri.parse(saved);
        for(int i=0;i<count;i++) {
            int delay=i*1100;
            main.postDelayed(() -> {if(!closing && (visible || backgroundMessagesAllowed()))playTone(uri);},delay);
        }
    }
    private void playDisconnectSound() {
        if(!getPreferences(0).getBoolean("disconnect_audio",true))return;
        String saved=getPreferences(0).getString("sound_uri_disconnect","");
        Uri uri=saved.isEmpty()?RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION):Uri.parse(saved);
        int count=getPreferences(0).getInt("sound_repeats_disconnect",1);
        for(int i=0;i<count;i++)main.postDelayed(()->{if(!closing)playTone(uri);},i*1100);
    }
    private void playTone(Uri uri) {
        try {
            Ringtone tone=RingtoneManager.getRingtone(this,uri);
            if(tone!=null) tone.play();
        } catch(RuntimeException error) { debug("Sound playback failed "+error.getClass().getSimpleName()); }
    }

    private void connectKnown(String radio,String id) {
        pendingKnownConnections.put(radio+":"+id,()->startKnownConnection(radio,id));
        scheduleKnownConnections();publish();
    }
    private void scheduleKnownConnections() {
        if(closing||knownConnectionTickPending||pendingKnownConnections.isEmpty())return;
        knownConnectionTickPending=true;
        main.postDelayed(()->{
            knownConnectionTickPending=false;
            if(closing){pendingKnownConnections.clear();return;}
            boolean busy=connectingAvailable||awaitingUsbSession!=null||!pendingBleActions.isEmpty();
            for(RadioSession s:sessions.values())busy|=s.loading;
            if(!busy&&!pendingKnownConnections.isEmpty()) {
                // Finish one radio's handshake and initial sync before starting another.
                String key=pendingKnownConnections.keySet().iterator().next();
                Runnable action=pendingKnownConnections.remove(key);
                action.run();publish();
            }
            scheduleKnownConnections();
        },500);
    }
    private void startKnownConnection(String radio,String id) {
        ChatStore.KnownRadio item=chatStore.getRadio(radio,id);if(item==null)return;
        for(RadioSession existing:sessions.values()) if((existing.connected||existing.loading)&&existing.matches(radio,id))return;
        if(!item.address.isEmpty()){queueBle("known:"+radio+":"+id,false,()->connectKnownReady(radio,id));return;}
        connectKnownReady(radio,id);
    }
    private void connectKnownReady(String radio, String id) {
        ChatStore.KnownRadio item=chatStore.getRadio(radio,id);
        if (item==null) return;
        for(RadioSession existing:sessions.values()) if(existing.connected && id.equals(existing.ownId) &&
                radio.equals(existing.meshcore?"mc":"mt")) {
            if(existing.meshcore) selectedMc=existing; else selectedMt=existing;
            publish(); return;
        }
        RadioSession s=null;
        for(RadioSession candidate:sessions.values()) if(!candidate.connected&&id.equals(candidate.ownId)&&radio.equals(candidate.meshcore?"mc":"mt")) {s=candidate;break;}
        if(s==null) s=newConnectionSession(radio);
        s.requestedRadio=radio;s.requestedId=id;
        if(s.meshcore)selectedMc=s;else selectedMt=s;
        if (item.address.isEmpty()) { connectUsb(s); return; }
        BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null) { s.updateStatus("Bluetooth unavailable"); return; }
        if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) {
            s.updateStatus("Grant Bluetooth permission first"); return;
        }
        try { connectDevice(s,adapter.getRemoteDevice(item.address)); }
        catch(IllegalArgumentException error) { s.updateStatus("Saved Bluetooth address is invalid"); }
    }

    private void viewSaved(String radio,String id) {
        if(chatStore.getRadio(radio,id)==null || !pageReady) return;
        int revision=++savedViewRevision;
        historyWorker.execute(()->{
        try {
            JSONObject data=new JSONObject().put("radio",radio).put("id",id);
            JSONArray channels=new JSONArray(), messages=new JSONArray();
            for(ChatStore.SavedChannel c:chatStore.channels(radio,id))
                channels.put(new JSONObject().put("index",c.index).put("name",c.name));
            for(ChatStore.Message m:chatStore.recent(radio,id))
                messages.put(new JSONObject().put("channel",m.channel).put("peer",m.peer).put("packetId",m.packetId).put("replyId",m.replyId).put("reaction",m.reaction)
                        .put("sender",m.sender).put("text",m.text).put("time",displayTime(m.receivedAt))
                        .put("receivedAt",m.receivedAt).put("own",m.own).put("delivery",m.delivery).put("hops",m.hops).put("heardRepeats",m.heardRepeats).put("paths",new JSONArray(m.paths)));
            data.put("channels",channels).put("messages",messages);
            main.post(()->{if(pageReady&&!closing&&revision==savedViewRevision)web.evaluateJavascript("window.onSavedNode("+data.toString()+")",null);});
        } catch(JSONException error) { android.util.Log.e("OverMesh","Saved history error",error); }
        });
    }

    private void removeKnown(String radio,String id) {
        ChatStore.KnownRadio item=chatStore.getRadio(radio,id);
        if(item==null) return;
        new AlertDialog.Builder(this).setTitle("Remove "+item.name+"?")
                .setMessage("This removes its saved messages and channel names from this phone.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Remove",(dialog,which)->{
                    for(RadioSession s:sessions.values()) if(id.equals(s.ownId) &&
                            radio.equals(s.meshcore?"mc":"mt")) disconnect(s);
                    chatStore.deleteRadio(radio,id);
                    publish();
                    if(pageReady) web.evaluateJavascript("window.onSavedNodeRemoved()",null);
                }).show();
    }

    private void eraseOlder(String radio,String id,int days) {
        if(chatStore.getRadio(radio,id)==null || days<1) return;
        long cutoff=System.currentTimeMillis()/1000L-days*86400L;
        new AlertDialog.Builder(this).setTitle("Erase messages older than "+days+" days?")
                .setMessage("Only messages for this node will be erased.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Erase",(dialog,which)->{
                    int count=chatStore.eraseOlderThan(radio,id,cutoff);
                    for(RadioSession s:sessions.values()) if(id.equals(s.ownId) &&
                            radio.equals(s.meshcore?"mc":"mt")) loadSessionHistory(s);
                    viewSaved(radio,id); publish();
                    nodeActionResult("Erased "+count+" messages");
                }).show();
    }

    private void loadSessionHistory(RadioSession s) {
        s.chats.clear(); s.directs.clear();
        s.historyRequests.clear();
        int revision=++s.historyRevision;String identity=s.ownId;
        historyWorker.execute(()->{
            try {
                List<ChatStore.Message> rows=chatStore.recent(s.meshcore?"mc":"mt",identity);
                main.post(()->{if(!closing&&revision==s.historyRevision&&identity.equals(s.ownId)){mergeHistory(s,rows);publish();}});
            } catch(RuntimeException error){main.post(()->nodeActionResult("Could not load message history"));}
        });
    }
    private void mergeHistory(RadioSession s,List<ChatStore.Message> rows) {
        for(ChatStore.Message saved:rows) {
            List<ChatEntry> history=saved.peer.isEmpty()?s.chats.computeIfAbsent(saved.channel,ignored->new ArrayList<>()):s.directs.computeIfAbsent(saved.peer,ignored->new ArrayList<>());
            if(history.stream().anyMatch(item->item.id==saved.id))continue;
            ChatEntry entry=new ChatEntry(saved);
            String conversation=saved.peer.isEmpty()?"c:"+saved.channel:"d:"+saved.peer;
            for(ChatEntry pending:s.trackedMessages.values()) if(pending.id==saved.id) {
                entry=pending; entry.unread=saved.unread; break;
            }
            if(saved.id<=s.readThrough.getOrDefault(conversation,0L))entry.unread=false;
            history.add(entry);
        }
        java.util.Comparator<ChatEntry> order=java.util.Comparator.comparingLong((ChatEntry e)->e.receivedAt).thenComparingLong(e->e.id);
        for(List<ChatEntry> history:s.chats.values())history.sort(order);
        for(List<ChatEntry> history:s.directs.values())history.sort(order);
    }
    private void markDirectRead(String radio,String peer) {
        RadioSession s=session(radio);
        if(s==null || s.ownId.isEmpty() || peer==null) return;
        chatStore.markDirectRead(s.meshcore?"mc":"mt",s.ownId,peer);
        s.readThrough.put("d:"+peer,chatStore.maxMessageId());
        List<ChatEntry> history=s.directs.get(peer);
        if(history!=null) for(ChatEntry entry:history) entry.unread=false;
        publish();
    }
    private void markChannelRead(String radio,int channel) {
        RadioSession s=session(radio);
        if(s==null || s.ownId.isEmpty()) return;
        chatStore.markChannelRead(s.meshcore?"mc":"mt",s.ownId,channel);
        s.readThrough.put("c:"+channel,chatStore.maxMessageId());
        List<ChatEntry> history=s.chats.get(channel);
        if(history!=null) for(ChatEntry entry:history) entry.unread=false;
        publish();
    }
    private void clearDirect(String radio,String peer) {
        RadioSession s=session(radio);
        if(s==null || s.ownId.isEmpty() || peer==null) return;
        new AlertDialog.Builder(this).setTitle("Clear DM history?")
                .setMessage("Delete all direct messages with "+peer+" on this node?")
                .setNegativeButton("Cancel",null).setPositiveButton("Clear",(dialog,which)->{
                    chatStore.clearDirect(s.meshcore?"mc":"mt",s.ownId,peer);
                    loadSessionHistory(s); publish();
                }).show();
    }
    private void eraseDirectOlder(String radio,String peer,int days) {
        RadioSession s=session(radio);
        if(s==null || s.ownId.isEmpty() || peer==null || days<1) return;
        long cutoff=System.currentTimeMillis()/1000L-days*86400L;
        new AlertDialog.Builder(this).setTitle("Erase older DMs?")
                .setMessage("Delete messages with "+peer+" older than "+days+" days?")
                .setNegativeButton("Cancel",null).setPositiveButton("Erase",(dialog,which)->{
                    chatStore.eraseDirectOlderThan(s.meshcore?"mc":"mt",s.ownId,peer,cutoff);
                    loadSessionHistory(s); publish();
                }).show();
    }
    private void exportChannelCsv(String key, int channel) {
        RadioSession s=sessions.get(key);
        if(s==null || !s.connected || s.ownId.isEmpty() || !s.channelNames.containsKey(channel)) {
            nodeActionResult("Choose a connected device and a reported channel"); return;
        }
        transferRadio=s.meshcore?"mc":"mt"; transferNode=s.ownId; transferChannel=channel;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/csv").putExtra(Intent.EXTRA_TITLE,"SV8TTL-LoRa-Dash-"+transferRadio+"-"+
                        s.ownId.replaceAll("[^a-zA-Z0-9]", "")+"-channel-"+channel+".csv");
        startActivityForResult(intent,EXPORT_CHANNEL_CSV);
    }
    private void exportDirectCsv(String radio,String peer) {
        RadioSession s=session(radio);
        if(s==null || s.ownId.isEmpty() || peer==null) return;
        transferRadio=s.meshcore?"mc":"mt"; transferNode=s.ownId; transferPeer=peer;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/csv").putExtra(Intent.EXTRA_TITLE,
                        "SV8TTL-LoRa-Dash-DM-"+radio+"-"+peer.replaceAll("[^a-zA-Z0-9]","")+".csv");
        startActivityForResult(intent,EXPORT_DM_CSV);
    }

    private void exportNode(String radio,String id,String format) {
        ChatStore.KnownRadio item=chatStore.getRadio(radio,id);
        if(item==null || (!"json".equals(format) && !"csv".equals(format))) return;
        transferRadio=radio; transferNode=id;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("csv".equals(format)?"text/csv":"application/json")
                .putExtra(Intent.EXTRA_TITLE,"SV8TTL-LoRa-Dash-"+radio+"-"+id.replaceAll("[^a-zA-Z0-9]","")+
                        ("csv".equals(format)?".csv":".json"));
        startActivityForResult(intent,"csv".equals(format)?EXPORT_CSV:EXPORT_JSON);
    }
    private void importNode() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json");
        startActivityForResult(intent,IMPORT_JSON);
    }
    private void chooseNodeExport() {
        List<ChatStore.KnownRadio> radios=new ArrayList<>(chatStore.radios());List<String> names=new ArrayList<>();
        for(ChatStore.KnownRadio radio:radios)names.add(radio.name+" · "+radio.radio+" · "+radio.id);
        if(radios.isEmpty()){nodeActionResult("No saved nodes to export");return;}
        new AlertDialog.Builder(this).setTitle("Export node backup").setItems(names.toArray(new String[0]),(d,w)->{
            ChatStore.KnownRadio radio=radios.get(w);exportNode(radio.radio,radio.id,"json");
        }).setNegativeButton("Cancel",null).show();
    }
    private void exportAppBackup() {
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE,"SV8TTL-LoRa-Dash-full-backup.json");
        startActivityForResult(intent,EXPORT_APP_JSON);
    }
    private void importAppBackup() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json");
        startActivityForResult(intent,IMPORT_APP_JSON);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==ENABLE_BLUETOOTH) {
            bleEnablePending=false;
            if(resultCode==RESULT_OK&&bluetoothEnabled())continueBleRequests();
            else rejectBleRequests("Bluetooth remains off. No BLE radios were connected.");
            return;
        }
        if(requestCode==PICK_SOUND) {
            if(resultCode==RESULT_OK && data!=null && pickingSound!=null) {
                Uri chosen;
                if(Build.VERSION.SDK_INT>=33) chosen=data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI,Uri.class);
                else chosen=data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if(chosen!=null) getPreferences(0).edit().putString("sound_uri_"+pickingSound,chosen.toString()).apply();
                publish();
            }
            pickingSound=null; return;
        }
        if(resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        if(requestCode==EXPORT_APP_JSON||requestCode==EXPORT_JSON||requestCode==IMPORT_APP_JSON||requestCode==IMPORT_JSON) {
            transferHistory(requestCode,data.getData());return;
        }
        try {
            if(requestCode==EXPORT_DEBUG_LOG) {
                try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
                    if(out==null)throw new IOException("Cannot open destination");
                    debugLog.export(out);
                }
                nodeActionResult("Debug log exported");return;
            }
            if(requestCode==EXPORT_CHANNEL_CSV || requestCode==EXPORT_CSV || requestCode==EXPORT_DM_CSV) {
                byte[] bytes=(requestCode==EXPORT_DM_CSV?dmCsv(transferRadio,transferNode,transferPeer):
                        nodeCsv(transferRadio,transferNode,requestCode==EXPORT_CHANNEL_CSV?transferChannel:null)).getBytes(StandardCharsets.UTF_8);
                try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
                    if(out==null)throw new IOException("Cannot open destination");out.write(bytes);
                }
                nodeActionResult("Exported node history as CSV");
            }
        } catch(Exception error) {
            new AlertDialog.Builder(this).setTitle("File transfer failed")
                    .setMessage(error.getMessage()).setPositiveButton("OK",null).show();
        }
    }
    private void transferHistory(int requestCode,Uri uri) {
        if(historyTransferRunning){nodeActionResult("Wait for the current history transfer to finish");return;}
        final String radio=transferRadio,id=transferNode;
        final boolean exporting=requestCode==EXPORT_APP_JSON||requestCode==EXPORT_JSON;
        final boolean full=requestCode==EXPORT_APP_JSON||requestCode==IMPORT_APP_JSON;
        JSONObject settings=new JSONObject();
        try{for(Map.Entry<String,?> e:getPreferences(0).getAll().entrySet())settings.put(e.getKey(),e.getValue());}catch(JSONException error){nodeActionResult(error.getMessage());return;}
        historyTransferRunning=true;
        nodeActionResult(exporting?"Exporting history…":"Validating and importing history…");
        historyWorker.execute(()->{
            try {
                if(exporting) {
                    OutputStream stream=getContentResolver().openOutputStream(uri,"wt");if(stream==null)throw new IOException("Cannot open destination");
                    HistoryBackup.export(new java.io.OutputStreamWriter(stream,StandardCharsets.UTF_8),chatStore,radio,id,full?settings:null);
                    main.post(()->{if(!closing)nodeActionResult("Backup exported. Sound selections are included; sound files are not embedded.");});
                }else {
                    // Copy once to private staging so both streaming passes see the same bytes.
                    java.io.File staged=java.io.File.createTempFile("history-import-",".json",getCacheDir());
                    try {
                        try(InputStream input=getContentResolver().openInputStream(uri);OutputStream out=new java.io.FileOutputStream(staged)) {
                            if(input==null)throw new IOException("Cannot open backup");byte[] buffer=new byte[8192];int count;
                            while((count=input.read(buffer))!=-1)out.write(buffer,0,count);
                        }
                        HistoryBackup.ReaderSource source=()->new java.io.InputStreamReader(new java.io.FileInputStream(staged),StandardCharsets.UTF_8);
                        HistoryBackup.Headers headers=HistoryBackup.read(source,full,null,null);
                        int[] added={0,0,0};android.database.sqlite.SQLiteDatabase db=chatStore.getWritableDatabase();db.beginTransaction();
                        try {
                            HistoryBackup.NodeMerger merge=node->{int[] counts=mergeNodeBackup(node);for(int i=0;i<3;i++)added[i]+=counts[i];};
                            for(JSONObject header:headers.nodes){validateNodeBackup(header);merge.merge(header);}
                            HistoryBackup.read(source,full,headers,merge);
                            if(full)chatStore.pendingSettings(headers.settings.toString());
                            db.setTransactionSuccessful();
                        }finally{db.endTransaction();}
                        main.post(()->{
                            if(closing)return;
                            try {
                                if(full){mergeSettings(headers.settings);chatStore.clearPendingSettings();}
                                refreshImportedHistory();
                                new AlertDialog.Builder(this).setTitle("Backup imported").setMessage("Added "+added[0]+" radios, "+added[1]+" channels and "+added[2]+" messages. Existing data was kept. Unavailable sound files must be selected again in Config.").setPositiveButton("OK",null).show();
                            }catch(Exception error){nodeActionResult("History imported; settings recovery is pending: "+error.getMessage());}
                        });
                    }finally{if(!staged.delete())debug("Import staging cleanup pending");}
                }
            }catch(Exception error){main.post(()->{if(!closing)new AlertDialog.Builder(this).setTitle("File transfer failed").setMessage(error.getMessage()).setPositiveButton("OK",null).show();});}
            finally{main.post(()->historyTransferRunning=false);}
        });
    }
    private void validateNodeBackup(JSONObject json) throws JSONException {
        if(json.optInt("version")<1||json.optInt("version")>2) throw new JSONException("Unsupported node backup version");
        String radio=json.getString("radio"), id=json.getString("nodeId");
        if((!"mt".equals(radio) && !"mc".equals(radio)) || id.isEmpty())
            throw new JSONException("Invalid node identity");
        JSONArray channels=json.getJSONArray("channels"), messages=json.getJSONArray("messages");
        for(int i=0;i<channels.length();i++) {
            JSONObject channel=channels.getJSONObject(i);
            channel.getInt("index"); channel.getString("name");
        }
        for(int i=0;i<messages.length();i++) {
            JSONObject message=messages.getJSONObject(i);
            message.getInt("channel"); message.getString("sender"); message.getString("text");
        }
    }
    private int[] mergeNodeBackup(JSONObject json) throws JSONException {
        String radio=json.getString("radio"), id=json.getString("nodeId");
        JSONObject heard=json.optJSONObject("lastHeard");
        if(heard!=null) {Iterator<String> peers=heard.keys();while(peers.hasNext()){String peer=peers.next();chatStore.heard(radio,id,peer,heard.getLong(peer));}}
        int radios=chatStore.importRadio(radio,id,json.optString("name",id),
                json.optString("address",""),json.optLong("lastSeen",System.currentTimeMillis()))?1:0;
        Map<Integer,String> existing=new TreeMap<>();
        for(ChatStore.SavedChannel item:chatStore.channels(radio,id)) existing.put(item.index,item.name);
        int channelsAdded=0, messagesAdded=0;
        JSONArray channels=json.getJSONArray("channels"), messages=json.getJSONArray("messages");
        for(int i=0;i<channels.length();i++) {
            JSONObject channel=channels.getJSONObject(i); int index=channel.getInt("index");
            if(!existing.containsKey(index)) {
                chatStore.saveChannel(radio,id,index,channel.getString("name"));
                existing.put(index,channel.getString("name")); channelsAdded++;
            }
        }
        for(int i=0;i<messages.length();i++) {
            JSONObject message=messages.getJSONObject(i);
            int channel=message.getInt("channel"); String peer=message.optString("peer","");
            String sender=message.getString("sender"), body=message.getString("text");
            long receivedAt=message.optLong("receivedAt",0); boolean own=message.optBoolean("own",false);
            long duplicate=chatStore.findImportedMessage(radio,id,channel,peer,sender,body,receivedAt,own,message.optBoolean("reaction",false),message.optLong("packetId",0));
            if(duplicate>=0) {
                JSONArray paths=message.optJSONArray("paths");
                chatStore.enrichMessage(duplicate,message.optLong("packetId",0)&0xffffffffL,message.optLong("replyId",0)&0xffffffffL,message.optInt("hops",-1),Math.max(-1,Math.min(1000000,message.optInt("heardRepeats",-1))),paths!=null&&paths.length()<=32?paths.toString():"[]");
                continue;
            }
            String delivery=message.optString("delivery","");
            if(own&&(delivery.equals("queued")||delivery.equals("sent_to_radio")||delivery.equals("accepted")||delivery.equals("relayed")||delivery.startsWith("sending:")||(radio.equals("mc")&&!peer.isEmpty()&&delivery.equals("sent_radio"))))delivery="unconfirmed: Imported without a final delivery result";
            long imported=chatStore.save(radio,id,channel,sender,body,message.optString("time",""),
                    receivedAt,own,delivery,peer);
            if(message.optBoolean("unread",false)) chatStore.setUnread(imported,true);
            chatStore.setHops(imported,message.optInt("hops",-1));
            chatStore.setReaction(imported,message.optBoolean("reaction",false));
            chatStore.setReferences(imported,message.optLong("packetId",0)&0xffffffffL,message.optLong("replyId",0)&0xffffffffL);
            JSONArray paths=message.optJSONArray("paths");if(paths!=null&&paths.length()<=32)chatStore.setPaths(imported,paths.toString());
            chatStore.setHeardRepeats(imported,Math.max(-1,Math.min(1000000,message.optInt("heardRepeats",-1))));
            messagesAdded++;
        }
        return new int[]{radios,channelsAdded,messagesAdded};
    }
    private void refreshImportedHistory() {
        for(RadioSession session:sessions.values()) if(!session.ownId.isEmpty())
            loadSessionHistory(session);
        publish();
        if(pageReady) web.evaluateJavascript("window.onAppBackupImported()",null);
    }
    private int mergeSettings(JSONObject settings) throws JSONException {
        SharedPreferences preferences=getPreferences(0);
        SharedPreferences.Editor editor=preferences.edit();
        int added=0;
        Iterator<String> keys=settings.keys();
        while(keys.hasNext()) {
            String key=keys.next();
            if(preferences.contains(key)) continue;
            Object value=settings.get(key);
            if(value==JSONObject.NULL) continue;
            if(key.startsWith("sound_uri_") && value instanceof String) {
                try(InputStream sound=getContentResolver().openInputStream(Uri.parse((String)value))) {
                    if(sound==null) continue;
                } catch(Exception unavailable) { continue; }
            }
            if(value instanceof Boolean) editor.putBoolean(key,(Boolean)value);
            else if(value instanceof Integer) editor.putInt(key,(Integer)value);
            else if(value instanceof Long) editor.putLong(key,(Long)value);
            else if(value instanceof Number) editor.putFloat(key,((Number)value).floatValue());
            else if(value instanceof String) editor.putString(key,(String)value);
            else continue;
            added++;
        }
        if(!editor.commit())throw new JSONException("Could not save imported settings; recovery retained");
        return added;
    }
    private String nodeCsv(String radio,String id,Integer channelFilter) {
        Map<Integer,String> names=new TreeMap<>();
        for(ChatStore.SavedChannel c:chatStore.channels(radio,id)) names.put(c.index,c.name);
        StringBuilder out=new StringBuilder("radio,node_id,channel,channel_name,peer,sender,text,received_at,time,own,delivery,hops\r\n");
        for(ChatStore.Message m:chatStore.load(radio,id)) {
            if(channelFilter != null && (m.channel != channelFilter || !m.peer.isEmpty())) continue;
            String[] fields={radio,id,Integer.toString(m.channel),names.getOrDefault(m.channel,""),
                    m.peer,m.sender,m.text,Long.toString(m.receivedAt),m.time,Boolean.toString(m.own),m.delivery,Integer.toString(m.hops)};
            for(int i=0;i<fields.length;i++) {
                if(i>0) out.append(',');
                out.append('"').append(fields[i].replace("\"","\"\"")).append('"');
            }
            out.append("\r\n");
        }
        return out.toString();
    }
    private String dmCsv(String radio,String id,String peer) {
        StringBuilder out=new StringBuilder("radio,node_id,peer,sender,text,received_at,time,own,delivery,hops\r\n");
        for(ChatStore.Message m:chatStore.load(radio,id)) {
            if(!peer.equals(m.peer)) continue;
            String[] fields={radio,id,peer,m.sender,m.text,Long.toString(m.receivedAt),
                    m.time,Boolean.toString(m.own),m.delivery,Integer.toString(m.hops)};
            for(int i=0;i<fields.length;i++) {
                if(i>0) out.append(',');
                out.append('"').append(fields[i].replace("\"","\"\"")).append('"');
            }
            out.append("\r\n");
        }
        return out.toString();
    }

    private boolean radioPublishPending;
    private void configureAttempts(RadioSession s) {
        if(s.protocol instanceof MeshCoreProtocol)((MeshCoreProtocol)s.protocol).setAttempts(
                Math.max(1,Math.min(10,getPreferences(0).getInt("mc_direct_attempts",4))),
                Math.max(1,Math.min(10,getPreferences(0).getInt("mc_flood_attempts",4))));
    }
    private final Runnable radioPublisher=()->{radioPublishPending=false;publish();};
    private void publishRadioUpdate() {
        boolean loading=false;
        for(RadioSession session:sessions.values()) loading|=session.loading;
        if(!loading) {publish();return;}
        if(radioPublishPending)return;
        radioPublishPending=true;
        main.postDelayed(radioPublisher,100);
    }
    private void publish() {
        if(radioPublishPending){main.removeCallbacks(radioPublisher);radioPublishPending=false;}
        if(closing)return;
        reconcileBleState();
        updateRunningNotification();
        if (!pageReady || web == null) return;
        try {
            chatLeft=resolveChatSession(chatLeft);chatRight=resolveChatSession(chatRight);
            JSONObject data = new JSONObject().put("mt", sessionJson(selectedMt)).put("mc", sessionJson(selectedMc))
                    .put("left",sessionJson(chatLeft)).put("right",sessionJson(chatRight));
            data.put("mcDirectAttempts",getPreferences(0).getInt("mc_direct_attempts",4)).put("mcFloodAttempts",getPreferences(0).getInt("mc_flood_attempts",4));
            data.put("multiSendEnabled",getPreferences(0).getBoolean("multi_send_enabled",false));
            JSONObject nodeNames=new JSONObject();
            for(Map.Entry<String,?> entry:getPreferences(0).getAll().entrySet())if(entry.getKey().startsWith("node_name_")&&entry.getValue() instanceof String)
                nodeNames.put(entry.getKey().substring("node_name_".length()),entry.getValue());
            data.put("nodeNames",nodeNames);
            data.put("channelObservationSeconds",channelObservationSeconds());
            data.put("messageSplitEnabled",messageSplitEnabled()).put("messageSplitIntervalSeconds",splitIntervalMs()/1000);
            data.put("connectingAvailable",connectingAvailable);
            data.put("settlingSeconds",getPreferences(0).getInt("send_settling_seconds",3));
            JSONArray myNodes = new JSONArray();
            for (ChatStore.KnownRadio item : chatStore.radios()) {
                boolean queued=pendingKnownConnections.containsKey(item.radio+":"+item.id);
                boolean connected=false,connecting=queued||pendingBleActions.containsKey("known:"+item.radio+":"+item.id);
                String connectionStatus=connecting?"Waiting for Bluetooth permission or activation…":"",detectedRadio="";
                for(RadioSession s:sessions.values()) if(s.matches(item.radio,item.id)) {
                    connected|=s.connected;connecting|=s.loading;
                    connectionStatus=s.state;
                    if(s.typeVerified&&(s.connected||s.loading))detectedRadio=s.meshcore?"mc":"mt";
                }
                if(queued)connectionStatus="Queued; waiting for the previous connection…";
                myNodes.put(new JSONObject().put("radio",item.radio).put("id",item.id)
                        .put("name",item.name).put("address",item.address)
                        .put("lastSeen",item.lastSeen)
                        .put("connected",connected).put("connecting",connecting).put("connectionStatus",connectionStatus).put("detectedRadio",detectedRadio)
                        .put("unread",chatStore.hasUnread(item.radio,item.id,false))
                        .put("dmUnread",chatStore.hasUnread(item.radio,item.id,true))
                        .put("channelUnread",chatStore.hasUnreadChannel(item.radio,item.id)));
            }
            boolean canDisconnectAll=!pendingBleActions.isEmpty()||!pendingKnownConnections.isEmpty();
            for(RadioSession s:sessions.values())canDisconnectAll|=s.connected||s.loading||s.scanning;
            data.put("canDisconnectAll",canDisconnectAll);
            data.put("myNodes",myNodes).put("audioEnabled",getPreferences(0).getBoolean("message_audio",true));
            data.put("backgroundMessageNotifications",getPreferences(0).getBoolean("background_message_notifications",false))
                    .put("messageNotificationsAllowed",MessageNotifications.allowed(this));
            JSONObject sound=new JSONObject().put("mode",getPreferences(0).getString("sound_mode","separate"));
            for(String kind:new String[]{"mc","mt","dm","shared","disconnect"})
                sound.put(kind,new JSONObject().put("selected",!getPreferences(0).getString("sound_uri_"+kind,"").isEmpty())
                    .put("repeats",getPreferences(0).getInt("sound_repeats_"+kind,1)));
            data.put("sound",sound).put("disconnectAudioEnabled",getPreferences(0).getBoolean("disconnect_audio",true)).put("chatFontStep",getPreferences(0).getInt("chat_font_step",0))
                    .put("composerFontStep",getPreferences(0).getInt("composer_font_step",0))
                    .put("sideMcFontStep",getPreferences(0).getInt("side_mc_font_step",0))
                    .put("sideMtFontStep",getPreferences(0).getInt("side_mt_font_step",0));
            JSONArray active=new JSONArray();
            for(RadioSession s:sessions.values()) if(s.connected)
                active.put(new JSONObject().put("key",s.key).put("radio",s.meshcore?"mc":"mt")
                        .put("id",s.ownId).put("name",s.ownName.isEmpty()?s.name:s.ownName)
                        .put("unread",hasUnread(s)).put("dmUnread",hasUnreadDm(s))
                        .put("channelUnread",hasUnreadChannel(s)));
            data.put("devices",active).put("selectedMc",selectedMc.key).put("selectedMt",selectedMt.key);
            web.evaluateJavascript("window.onRadioState(" + data.toString() + ")", null);
        } catch (JSONException error) { android.util.Log.e("OverMesh", "Could not encode radio state", error); }
    }

    private void publishScanProgress(RadioSession session) {
        if (!pageReady || web == null) return;
        web.evaluateJavascript("window.onScanProgress('" + (session.meshcore ? "mc" : "mt") +
                "'," + session.scanProgress + ")", null);
    }

    private RadioSession resolveChatSession(RadioSession current) {
        if(!current.ownId.isEmpty())for(RadioSession s:sessions.values())
            if(s.connected&&s.meshcore==current.meshcore&&s.ownId.equals(current.ownId))return s;
        return current;
    }

    private JSONObject sessionJson(RadioSession session) throws JSONException {
        JSONObject obj = new JSONObject().put("radio",session.meshcore?"mc":"mt").put("name", session.name).put("state", session.state)
                .put("historyRevision",session.historyRevision)
                .put("connected", session.connected).put("scanning", session.scanning)
                .put("scanProgress", session.scanProgress).put("ownId",session.ownId)
                .put("ownName",session.ownName).put("key",session.key)
                .put("channelTextLimit",session.protocol==null?0:session.protocol.textLimit(false))
                .put("directTextLimit",session.protocol==null?0:session.protocol.textLimit(true))
                .put("unread",hasUnread(session)).put("loading",session.loading);
        if (Double.isFinite(session.ownLatitude) && Double.isFinite(session.ownLongitude))
            obj.put("ownLatitude", session.ownLatitude).put("ownLongitude", session.ownLongitude);
        JSONArray channelsJson = new JSONArray();
        for (Map.Entry<Integer, String> entry : session.channelNames.entrySet())
            channelsJson.put(new JSONObject().put("index", entry.getKey()).put("name", entry.getValue()));
        if(!session.connected&&!session.ownId.isEmpty()) {
            channelsJson=new JSONArray();
            for(ChatStore.SavedChannel saved:chatStore.channels(session.meshcore?"mc":"mt",session.ownId))
                channelsJson.put(new JSONObject().put("index",saved.index).put("name",saved.name));
        }
        JSONArray nodesJson = new JSONArray();
        for (NodeEntry node : session.knownNodes.values())
            {
                JSONObject item = new JSONObject().put("id", node.id).put("name", node.name)
                        .put("lastHeard",chatStore.lastHeard(session.meshcore?"mc":"mt",session.ownId,node.id)).put("role", node.role).put("model", node.model).put("hops", node.hops)
                        .put("favorite", getPreferences(0).getBoolean((session.meshcore ? "mc:" : "mt:") + session.ownId + ":" + node.id, false));
                if (Double.isFinite(node.latitude) && Double.isFinite(node.longitude))
                    item.put("latitude", node.latitude).put("longitude", node.longitude);
                nodesJson.put(item);
            }
        obj.put("channels", channelsJson).put("nodes", nodesJson).put("lastTrace",session.lastTrace==null?JSONObject.NULL:session.lastTrace);
        obj.put("historyUnread",new JSONObject(chatStore.unreadConversations(session.meshcore?"mc":"mt",session.ownId)));
        JSONObject directsJson = new JSONObject();
        boolean dmUnread=false;
        for (Map.Entry<String, List<ChatEntry>> conversation : session.directs.entrySet()) {
            JSONArray messages = new JSONArray();
            for (ChatEntry chat : conversation.getValue()) {
                if(chat.unread) dmUnread=true;
                messages.put(new JSONObject()
                    .put("id", chat.id).put("reaction",chat.reaction).put("packetId",chat.packetId).put("replyId",chat.replyId).put("sender", chat.sender).put("senderName", session.knownNodes.containsKey(chat.sender) ? session.knownNodes.get(chat.sender).name : chat.sender).put("text", chat.text)
                    .put("time", chat.time).put("receivedAt",chat.receivedAt)
                    .put("own", chat.own).put("delivery", chat.delivery).put("unread",chat.unread).put("hops",chat.hops).put("heardRepeats",chat.heardRepeats).put("paths",new JSONArray(chat.paths)));
            }
            directsJson.put(conversation.getKey(), messages);
        }
        JSONObject peerNames=new JSONObject();
        for(String peer:session.directs.keySet())peerNames.put(peer,getPreferences(0).getString(
                "contact_name:"+(session.meshcore?"mc":"mt")+":"+session.ownId+":"+peer,peer));
        obj.put("directs", directsJson).put("dmUnread",hasUnreadDm(session)).put("peerNames",peerNames);
        JSONObject conversations = new JSONObject();
        for (Map.Entry<Integer, List<ChatEntry>> entry : session.chats.entrySet()) {
            JSONArray messages = new JSONArray();
            for (ChatEntry chat : entry.getValue())
                messages.put(new JSONObject().put("id", chat.id).put("channel", entry.getKey())
                        .put("reaction",chat.reaction).put("packetId",chat.packetId).put("replyId",chat.replyId).put("sender", chat.sender).put("senderName",session.knownNodes.containsKey(chat.sender)?
                                session.knownNodes.get(chat.sender).name:chat.sender)
                        .put("text", chat.text).put("time", chat.time)
                        .put("receivedAt",chat.receivedAt).put("own", chat.own)
                        .put("delivery", chat.delivery).put("unread",chat.unread).put("hops",chat.hops).put("heardRepeats",chat.heardRepeats).put("paths",new JSONArray(chat.paths)));
            conversations.put(String.valueOf(entry.getKey()), messages);
        }
        return obj.put("chats", conversations);
    }
    private boolean hasUnreadDm(RadioSession session) {
        return !session.ownId.isEmpty()&&chatStore.hasUnread(session.meshcore?"mc":"mt",session.ownId,true);
    }
    private boolean hasUnread(RadioSession session) {
        if(hasUnreadDm(session)) return true;
        return hasUnreadChannel(session);
    }
    private boolean hasUnreadChannel(RadioSession session) {
        return !session.ownId.isEmpty()&&chatStore.hasUnreadChannel(session.meshcore?"mc":"mt",session.ownId);
    }

    private void sendReaction(String key,long messageId,String number) {
        RadioSession s=sessions.get(key);
        if(s==null||s.meshcore||!s.connected||s.loading||s.protocol==null){nodeActionResult("Connect the corresponding Meshtastic radio first");return;}
        ChatStore.Message original=null;
        for(ChatStore.Message m:chatStore.load("mt",s.ownId))if(m.id==messageId){original=m;break;}
        if(original==null||original.reaction||original.packetId==0){nodeActionResult("Original packet reference unavailable for this message");return;}
        int channel=original.channel;
        if(!original.peer.isEmpty()) {
            NodeEntry contact=s.knownNodes.get(original.peer);
            if(contact==null){nodeActionResult("Wait for this contact to load");return;}
            channel=contact.channel;
        } else if(!s.channelNames.containsKey(channel)){nodeActionResult("Wait for this channel to load");return;}
        try {
            byte[] payload=s.protocol.sendReaction(original.peer,channel,number,original.packetId);
            ChatEntry entry=new ChatEntry("You",number,true);entry.reaction=true;entry.replyId=original.packetId;entry.packetId=s.protocol.lastRequestId();
            entry.id=chatStore.save("mt",s.ownId,original.channel,"You",number,entry.time,entry.receivedAt,true,entry.delivery,original.peer);
            chatStore.setReferences(entry.id,entry.packetId,entry.replyId);chatStore.setReaction(entry.id,true);
            if(original.peer.isEmpty())s.chats.computeIfAbsent(channel,ignored->new ArrayList<>()).add(entry);
            else s.directs.computeIfAbsent(original.peer,ignored->new ArrayList<>()).add(entry);
            s.track(entry,entry.packetId);s.sendTracked(payload);publish();
        } catch(IllegalArgumentException error){nodeActionResult(error.getMessage());}
    }
    private void sendMessage(RadioSession session,int channel,String text) {sendMessage(session,channel,text,0);}
    private void sendMessage(RadioSession session, int channel, String text,long replyId) {
        if (session == null) return;
        if (!session.connected || session.protocol == null) { nodeActionResult("Connect the radio first"); return; }
        if (session.ownId.isEmpty() || session.loading) { nodeActionResult("Wait for the radio to finish loading"); return; }
        if (!session.channelNames.containsKey(channel)) { nodeActionResult("Wait for this radio's channel names"); return; }
        text = text == null ? "" : text.trim();
        if (text.isEmpty()) return;
        if(text.getBytes(StandardCharsets.UTF_8).length>session.protocol.textLimit(false)) {
            startSplit(session,"",channel,text,replyId);return;
        }
        try {
            byte[] payload = session.protocol.sendChannelReply(channel, text,replyId);
            ChatEntry entry = new ChatEntry("You", text, true);
            entry.id = chatStore.save(session.meshcore ? "mc" : "mt", session.ownId, channel, entry.sender,
                    entry.text, entry.time, entry.receivedAt, entry.own, entry.delivery, "");
            entry.packetId=session.meshcore?0:session.protocol.lastRequestId();entry.replyId=replyId;chatStore.setReferences(entry.id,entry.packetId,replyId);
            entry.heardRepeats=session.protocol.tracksRepeats(session.protocol.lastRequestId())?0:-1;
            chatStore.setHeardRepeats(entry.id,entry.heardRepeats);
            session.chats.computeIfAbsent(channel, ignored -> new ArrayList<>()).add(entry);
            session.track(entry,session.protocol.lastRequestId());
            session.sendTracked(payload);
            composeQueued(session,"",text);
            publish();
        } catch (IllegalArgumentException error) { nodeActionResult(error.getMessage()); }
    }

    private void nodeAction(RadioSession session, String id, String action, String text) {
        if (session == null || id == null || action == null) return;
        debug(session.key+" node action="+action+" target="+id);
        if (session.ownId.isEmpty() || session.loading) { nodeActionResult("Wait for the radio to finish loading"); return; }
        NodeEntry entry = session.knownNodes.get(id);
        if (entry == null || !session.connected) { nodeActionResult("Connect the radio and reload this contact first"); return; }
        String key = (session.meshcore ? "mc:" : "mt:") + session.ownId + ":" + id;
        try {
            if ("favorite".equals(action)) {
                boolean next = !getPreferences(0).getBoolean(key, false);
                getPreferences(0).edit().putBoolean(key, next).apply();
                publish(); nodeActionResult(next ? "Added to favorites" : "Removed from favorites"); return;
            }
            if ("map".equals(action)) {
                if (!Double.isFinite(entry.latitude) || !Double.isFinite(entry.longitude)) {
                    nodeActionResult("No position reported by this contact"); return;
                }
                Uri uri = Uri.parse("geo:" + entry.latitude + "," + entry.longitude + "?q=" +
                        entry.latitude + "," + entry.longitude + "(" + Uri.encode(entry.name) + ")");
                startActivity(new Intent(Intent.ACTION_VIEW, uri)); return;
            }
            if ("share".equals(action)) {
                String details = session.name + " · " + entry.name + " · " + entry.id + " · " + entry.role;
                if (!entry.model.isEmpty()) details += " · " + entry.model;
                if (Double.isFinite(entry.latitude) && Double.isFinite(entry.longitude))
                    details += " · " + entry.latitude + ", " + entry.longitude;
                Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, details);
                startActivity(Intent.createChooser(share, "Share node info")); return;
            }
            if ("dm".equals(action) && (text == null || text.trim().isEmpty())) {
                nodeActionResult("Enter a direct message first"); return;
            }
            if("dm".equals(action)&&text.getBytes(StandardCharsets.UTF_8).length>session.protocol.textLimit(true)) {
                startSplit(session,id,-1,text,0);return;
            }
            if (session.meshcore && "req".equals(action) &&
                    !(entry.role.equals("Repeater") || entry.role.equals("Sensor"))) {
                nodeActionResult("Status requests are for MeshCore repeaters and sensors"); return;
            }
            boolean isTrace="tr".equals(action)||"trace-path".equals(action)||"ping".equals(action);
            if(isTrace&&session.pendingTrace!=0) { nodeActionResult("Wait for the current trace to finish"); return; }
            byte[] payload = session.protocol.nodeCommand(action, id, text == null ? "" : text, entry.channel);
            if(isTrace) {
                long token=session.protocol.lastRequestId(); int generation=session.generation;
                session.pendingTrace=token; session.traceTarget=id;session.pendingTraceKind=action;
                showTracePending(session,token,id);
                main.postDelayed(() -> {
                    if(session.generation!=generation||session.pendingTrace!=token) return;
                    session.protocol.expireRequest(token); session.pendingTrace=0;
                    showTrace(session,new TraceResult(token,id,new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),
                            "ping".equals(action)?"No Ping reply within 120 seconds. Zero-hop Ping requires a directly reachable repeater or repeating room server.":"No trace reply within 120 seconds. The route or destination may be unavailable."));
                },120000);
            }
            if ("dm".equals(action)) {
                ChatEntry message = new ChatEntry("You", text.trim(), true);
                message.id = chatStore.save(session.meshcore ? "mc" : "mt", session.ownId, -1, message.sender,
                        message.text, message.time, message.receivedAt, true, message.delivery, id);
                message.packetId=session.meshcore?0:session.protocol.lastRequestId();chatStore.setReferences(message.id,message.packetId,0);
                session.directs.computeIfAbsent(id, ignored -> new ArrayList<>()).add(message);
                session.track(message,session.protocol.lastRequestId());
            }
            session.sendTracked(payload);
            if("signal".equals(action)) {
                long token=session.protocol.lastRequestId();int generation=session.generation;
                main.postDelayed(()->{if(session.generation==generation&&session.protocol.requestPending(token)) {
                    session.protocol.expireRequest(token);
                    session.signalQuality(token,id,"No Signal Quality response within 120 seconds. The node may be unreachable or its firmware may not support remote local-statistics requests.");
                }},120000);
            }
            if("dm".equals(action)) composeQueued(session,id,text.trim());
            if(session.meshcore&&!isTrace&&!"dm".equals(action)) {
                long token=session.protocol.lastRequestId(); int generation=session.generation;
                main.postDelayed(() -> {if(session.generation==generation&&session.protocol.requestPending(token)) {
                    session.protocol.expireRequest(token);nodeActionResult("No response from radio to the command");
                }},15000);
            }
            publish();
            if(!isTrace&&!"dm".equals(action)) nodeActionResult(("signal".equals(action) ? "Signal Quality request" : action.toUpperCase()) + " sent to radio for " + entry.name);
        } catch (IllegalArgumentException | ActivityNotFoundException error) { nodeActionResult(error.getMessage()); }
    }

    private void chainError(Exception error) {
        try {if(sendChain!=null)sendChain.data.put("paused",true);}catch(JSONException ignored){}
        nodeActionResult("Sending paused: "+error.getMessage());
    }
    private JSONObject chainTarget(RadioSession s,String peer,int channel,String label) throws JSONException {
        return new JSONObject().put("radio",s.meshcore?"mc":"mt").put("nodeId",s.ownId).put("peer",peer).put("channel",channel)
                .put("label",s.ownName+" → "+label);
    }
    private static final class SplitSend {
        final String peer;final int channel;final long replyId;
        String radio,nodeId;boolean retryRequested;int problemIndex=-1;String problemReason="";
        final List<ChatEntry> entries=new ArrayList<>();
        int next;long waitingSince,token;final SplitPacing pacing=new SplitPacing();
        SplitSend(String peer,int channel,long replyId){this.peer=peer;this.channel=channel;this.replyId=replyId;}
    }
    private void startSplit(RadioSession s,String peer,int channel,String text,long replyId) {
        try {
            List<String> parts=messageParts(text,s.protocol.textLimit(!peer.isEmpty()));
            SplitSend job=new SplitSend(peer,channel,replyId);
            job.radio=s.meshcore?"mc":"mt";job.nodeId=s.ownId;
            for(String part:parts) {
                ChatEntry entry=new ChatEntry("You",part,true);
                entry.id=chatStore.save(s.meshcore?"mc":"mt",s.ownId,channel,entry.sender,part,entry.time,entry.receivedAt,true,entry.delivery,peer);
                entry.replyId=replyId;chatStore.setReferences(entry.id,0,replyId);job.entries.add(entry);
                if(peer.isEmpty())s.chats.computeIfAbsent(channel,k->new ArrayList<>()).add(entry);
                else s.directs.computeIfAbsent(peer,k->new ArrayList<>()).add(entry);
            }
            s.splitSends.add(job);composeQueued(s,peer,text.trim());publish();tickSplit(s);
        }catch(Exception error){nodeActionResult("Cannot split message: "+error.getMessage());}
    }
    private void stopSplits(RadioSession s,String reason) {
        for(SplitSend job:s.splitSends)for(int i=job.next;i<job.entries.size();i++) {
            ChatEntry entry=job.entries.get(i);entry.delivery="failed: "+reason;
            if(chatStore!=null)chatStore.setDelivery(entry.id,entry.delivery);
        }
        s.splitSends.clear();s.nextSplitSend=0;
    }
    private void tickSplit(RadioSession s) {
        if(s.splitSends.isEmpty())return;
        SplitSend job=s.splitSends.peek();
        if(job.problemIndex>=0){showSplitFailure(s,job);return;}
        if(job.next==job.entries.size()&&!job.pacing.awaitingResult&&!job.pacing.decisionRequired){s.splitSends.remove();return;}
        boolean original=s.connected&&s.protocol!=null&&job.nodeId.equals(s.ownId)&&job.radio.equals(s.meshcore?"mc":"mt");
        if(!original) {
            if(job.retryRequested) {
                for(RadioSession target:sessions.values())if(target.connected&&!target.loading&&target.protocol!=null&&job.nodeId.equals(target.ownId)&&job.radio.equals(target.meshcore?"mc":"mt")) {
                    s.splitSends.remove(job);target.splitSends.add(job);
                    for(ChatEntry entry:job.entries) {
                        List<ChatEntry> history=job.peer.isEmpty()?target.chats.computeIfAbsent(job.channel,k->new ArrayList<>()):target.directs.computeIfAbsent(job.peer,k->new ArrayList<>());
                        history.removeIf(existing->existing.id==entry.id);history.add(entry);
                    }
                    publish();break;
                }
            }else failSplit(s,job,Math.min(job.pacing.awaitingResult?job.next-1:job.next,job.entries.size()-1),"Radio disconnected. Reconnect the original radio after choosing Retry.");
            return;
        }
        long now=SystemClock.elapsedRealtime();
        ChatEntry previous=job.next>0?job.entries.get(job.next-1):null;
        boolean ready=job.pacing.ready(now,s.meshcore&&job.peer.isEmpty(),previous==null?"":previous.delivery,
            previous==null?0:previous.heardRepeats,s.protocol.automaticDeliveryPending(job.token),splitIntervalMs(),channelObservationSeconds()*1000L);
        s.nextSplitSend=Math.max(s.nextSplitSend,job.pacing.nextSend);
        if(job.pacing.decisionRequired) {
            String reason=previous.delivery.equals("sent_radio")?"unconfirmed: No repeat heard within the channel observation timeout":previous.delivery;
            failSplit(s,job,job.next-1,reason);return;
        }
        if(!ready)return;
        if(job.next==job.entries.size()){s.splitSends.remove();return;}
        if(now<s.nextSplitSend)return;
        if(sendChain!=null)for(int i=0;i<sendChain.items().length();i++) {
            JSONObject item=sendChain.items().optJSONObject(i);
            if("active".equals(item.optString("state"))&&s==chainRadio(item))return;
        }
        if(s.loading||(s.protocol instanceof MeshCoreProtocol&&((MeshCoreProtocol)s.protocol).busyForSend())) {
            if(job.waitingSince==0)job.waitingSince=now;
            if(now-job.waitingSince>=15000)failSplit(s,job,job.next,"Radio command remained busy. Reconnect the original radio before retrying.");
            return;
        }
        int sendIndex=job.next;
        try {
            ChatEntry entry=job.entries.get(job.next);
            NodeEntry contact=job.peer.isEmpty()?null:s.knownNodes.get(job.peer);
            if(!job.peer.isEmpty()&&contact==null)throw new IllegalArgumentException("Contact is no longer loaded");
            if(job.peer.isEmpty()&&!s.channelNames.containsKey(job.channel))throw new IllegalArgumentException("Channel is no longer available");
            byte[] payload=job.peer.isEmpty()?s.protocol.sendChannelReply(job.channel,entry.text,job.replyId):s.protocol.nodeCommand("dm",job.peer,entry.text,contact.channel);
            long token=s.protocol.lastRequestId();entry.packetId=s.meshcore?0:token;
            chatStore.setReferences(entry.id,entry.packetId,job.replyId);
            entry.heardRepeats=s.protocol.tracksRepeats(token)?0:-1;chatStore.setHeardRepeats(entry.id,entry.heardRepeats);
            s.track(entry,token);job.next++;job.waitingSince=0;job.token=token;job.retryRequested=false;job.pacing.sent();
            s.sendTracked(payload);publish();
        }catch(Exception error){if(job.next>sendIndex)s.protocol.expireRequest(job.token);failSplit(s,job,sendIndex,String.valueOf(error.getMessage()));}
    }
    private AlertDialog splitFailureDialog;
    private void failSplit(RadioSession s,SplitSend job,int index,String reason) {
        job.problemIndex=index;job.problemReason=reason;
        ChatEntry entry=job.entries.get(index);
        entry.delivery=reason.startsWith("unconfirmed")||reason.startsWith("failed")?reason:"failed: "+reason;
        chatStore.setDelivery(entry.id,entry.delivery);publish();showSplitFailure(s,job);
    }
    private void showSplitFailure(RadioSession s,SplitSend job) {
        if(!resumed||closing||splitFailureDialog!=null)return;
        int index=job.problemIndex;ChatEntry failed=job.entries.get(index);
        LinearLayout content=vertical();content.setPadding(dp(16),dp(8),dp(16),dp(8));
        content.addView(label("Part "+(index+1)+" of "+job.entries.size()+" · "+job.radio.toUpperCase()+" · "+job.nodeId,14,Color.GRAY,false));
        TextView body=label(failed.text,17,0xffc9d1d9,false);body.setTextIsSelectable(true);content.addView(body);
        content.addView(label(job.problemReason+"\n\nRetry resends this part before continuing. Missing confirmation does not prove non-delivery; a retry may create a duplicate. If disconnected, reconnect the original radio to continue. Cancel stops the remaining parts of this message.",14,Color.GRAY,false));
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Split message needs attention").setView(scroll).setCancelable(false)
            .setNegativeButton("Cancel",(d,w)->{
                for(int i=index+1;i<job.entries.size();i++){ChatEntry entry=job.entries.get(i);entry.delivery="failed: Remaining split parts cancelled by you";chatStore.setDelivery(entry.id,entry.delivery);}
                s.splitSends.remove(job);publish();
            }).setPositiveButton("Retry",null).create();
        splitFailureDialog=dialog;dialog.setOnDismissListener(d->splitFailureDialog=null);dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                ChatEntry retry=new ChatEntry("You",failed.text,true);retry.replyId=job.replyId;
                retry.id=chatStore.save(job.radio,job.nodeId,job.channel,retry.sender,retry.text,retry.time,retry.receivedAt,true,retry.delivery,job.peer);
                chatStore.setReferences(retry.id,0,job.replyId);job.entries.set(index,retry);
                if(job.nodeId.equals(s.ownId)&&job.radio.equals(s.meshcore?"mc":"mt")) {
                    if(job.peer.isEmpty())s.chats.computeIfAbsent(job.channel,k->new ArrayList<>()).add(retry);else s.directs.computeIfAbsent(job.peer,k->new ArrayList<>()).add(retry);
                }
                job.next=index;job.waitingSince=0;job.token=0;job.problemIndex=-1;job.retryRequested=true;job.pacing.retry(SystemClock.elapsedRealtime(),splitIntervalMs());
                dialog.dismiss();publish();
            }catch(Exception error){nodeActionResult("Cannot retry split part: "+error.getMessage());}
        });
    }
    private RadioSession chainRadio(JSONObject item) {
        for(RadioSession s:sessions.values())if(s.connected&&!s.loading&&s.ownId.equals(item.optString("nodeId"))&&(s.meshcore?"mc":"mt").equals(item.optString("radio")))return s;
        return null;
    }
    private boolean chainUnfinished() {
        if(sendChain==null)return false;
        for(int i=0;i<sendChain.items().length();i++){String state=sendChain.items().optJSONObject(i).optString("state");if(state.equals("pending")||state.equals("active")||state.equals("deferred")||state.equals("unconfirmed"))return true;}
        return false;
    }
    private void chooseMultiSend() {
        if(!getPreferences(0).getBoolean("multi_send_enabled",false)){nodeActionResult("Enable Send to multiple in Config first");return;}
        if(chainUnfinished()){showChain();return;}
        new MultiSendComposer().show();
    }
    private Map<String,JSONObject> destinationSets() {
        Map<String,JSONObject> sets=new LinkedHashMap<>();
        for(Map.Entry<String,?> entry:getPreferences(0).getAll().entrySet())if(entry.getKey().startsWith("destination_set_")) {
            try{sets.put(entry.getKey(),new JSONObject(String.valueOf(entry.getValue())));}catch(JSONException ignored){}
        }
        return sets;
    }
    private void showDestinationSets() {
        LinearLayout content=vertical();
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Destination sets").setView(scroll).setPositiveButton("Close",null).create();
        content.addView(label("Save named groups of channels and DMs. Connect their radios to create or edit destinations. Sets are included in App Backup.",14,Color.GRAY,false));
        Button create=new Button(this);create.setText("Create destination set");content.addView(create);
        for(Map.Entry<String,JSONObject> entry:destinationSets().entrySet()) {
            JSONObject set=entry.getValue();Button button=new Button(this);button.setText(set.optString("name"));content.addView(button);
            button.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(set.optString("name"))
                .setItems(new String[]{"Edit destinations / name","Delete set"},(d,w)->{
                    if(w==0){dialog.dismiss();new MultiSendComposer(true,entry.getKey(),set).show();}
                    else new AlertDialog.Builder(this).setMessage("Delete destination set “"+set.optString("name")+"”?").setNegativeButton("Cancel",null)
                        .setPositiveButton("Delete",(a,b)->{getPreferences(0).edit().remove(entry.getKey()).apply();dialog.dismiss();showDestinationSets();}).show();
                }).show());
        }
        create.setOnClickListener(v->{dialog.dismiss();new MultiSendComposer(true,null,null).show();});dialog.show();
    }
    private final class MultiSendComposer {
        final boolean editingSet;final String setKey;final JSONObject initialSet;EditText setName;
        MultiSendComposer(){this(false,null,null);}
        MultiSendComposer(boolean editingSet,String setKey,JSONObject initialSet){this.editingSet=editingSet;this.setKey=setKey;this.initialSet=initialSet;}
        final ChainDestinations selection=new ChainDestinations();
        final List<RadioSession> radios=new ArrayList<>();
        final Map<String,Map<String,JSONObject>> choices=new LinkedHashMap<>();
        Spinner radioPicker;EditText search,message;LinearLayout list;TextView summary,error;
        android.widget.CheckBox parallel;Button channelButton,dmButton;boolean direct;
        RadioSession current(){int i=radioPicker.getSelectedItemPosition();return i>=0&&i<radios.size()?radios.get(i):null;}
        void show() {
            try {
                List<String> names=new ArrayList<>();
                for(RadioSession s:sessions.values())if(s.connected&&!s.loading&&!s.ownId.isEmpty()) {
                    radios.add(s);names.add((s.ownName.isEmpty()?s.ownId:s.ownName)+" · "+(s.meshcore?"MC":"MT")+" · "+s.ownId);
                    Map<String,JSONObject> targets=new LinkedHashMap<>();choices.put(s.key,targets);
                    for(Map.Entry<Integer,String> c:s.channelNames.entrySet())targets.put("channel:"+c.getKey(),chainTarget(s,"",c.getKey(),c.getValue()).put("display",c.getValue()));
                    List<NodeEntry> nodes=new ArrayList<>(s.knownNodes.values());nodes.sort((a,b)->a.name.compareToIgnoreCase(b.name));
                    for(NodeEntry n:nodes)if(!n.id.equals(s.ownId)&&(s.meshcore?(n.role.equals("User")||n.role.equals("Room server")):n.id.matches("![0-9a-fA-F]{8}")&&!n.id.equals("!00000000")&&!n.id.equals("!ffffffff")))
                        targets.put("dm:"+n.id,chainTarget(s,n.id,-1,n.name).put("display",n.name+" · "+n.id));
                }
                if(radios.isEmpty()){nodeActionResult("Connect a radio and wait for it to finish loading");return;}
                LinearLayout layout=vertical();layout.setPadding(dp(16),dp(8),dp(16),dp(8));
                setName=new EditText(MainActivity.this);setName.setSingleLine(true);setName.setHint("Destination set name");
                if(editingSet)layout.addView(setName);
                else {
                    Button savedSets=new Button(MainActivity.this);savedSets.setText("Add saved destination set");layout.addView(savedSets);
                    savedSets.setOnClickListener(v->{
                        List<JSONObject> sets=new ArrayList<>(destinationSets().values());List<String> labels=new ArrayList<>();for(JSONObject set:sets)labels.add(set.optString("name"));
                        if(sets.isEmpty()){error.setText("Create a destination set in Config → Sending options first.");return;}
                        new AlertDialog.Builder(MainActivity.this).setTitle("Add destination set").setItems(labels.toArray(new String[0]),(d,w)->{
                            try{loadSet(sets.get(w));render();updateSummary();error.setText("");}catch(Exception failure){error.setText(failure.getMessage());}
                        }).setNegativeButton("Cancel",null).show();
                    });
                }
                layout.addView(label("Select Radio",16,DARK,true));
                radioPicker=new Spinner(MainActivity.this,Spinner.MODE_DROPDOWN);
                ArrayAdapter<String> adapter=new ArrayAdapter<>(MainActivity.this,android.R.layout.simple_spinner_item,names);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);radioPicker.setAdapter(adapter);layout.addView(radioPicker);
                layout.addView(label("Select Channel / DM",16,DARK,true));
                LinearLayout types=horizontal();channelButton=new Button(MainActivity.this);channelButton.setText("Channel");dmButton=new Button(MainActivity.this);dmButton.setText("DM · Select Nodes");types.addView(channelButton,new LinearLayout.LayoutParams(0,-2,1));types.addView(dmButton,new LinearLayout.LayoutParams(0,-2,1));layout.addView(types);
                search=new EditText(MainActivity.this);search.setSingleLine(true);search.setHint("Search nodes by name or ID");layout.addView(search);
                list=vertical();ScrollView options=new ScrollView(MainActivity.this);options.addView(list);layout.addView(options,new LinearLayout.LayoutParams(-1,dp(200)));
                Button save=new Button(MainActivity.this);save.setText("Save Destinations");layout.addView(save);
                summary=new TextView(MainActivity.this);layout.addView(summary);
                message=new EditText(MainActivity.this);message.setHint("Message for this chain");message.setMinLines(2);layout.addView(message);
                parallel=new android.widget.CheckBox(MainActivity.this);parallel.setText("Send through different radios simultaneously");layout.addView(parallel);
                if(editingSet){message.setVisibility(View.GONE);parallel.setVisibility(View.GONE);}
                layout.addView(label("Save channels and DMs for each radio, then select another radio. Saved sets can be combined with manual selections.",14,Color.GRAY,false));
                error=new TextView(MainActivity.this);error.setTextColor(0xffb3261e);layout.addView(error);
                ScrollView scroll=new ScrollView(MainActivity.this);scroll.setFillViewport(true);scroll.addView(layout);
                AlertDialog dialog=new AlertDialog.Builder(MainActivity.this).setTitle(editingSet?"Destination set":"Send to multiple").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton(editingSet?"Save set":"Send Chain",null).create();
                channelButton.setOnClickListener(v->{direct=false;render();});dmButton.setOnClickListener(v->{direct=true;render();});
                radioPicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){search.setText("");render();}});
                search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){render();}public void afterTextChanged(android.text.Editable e){}});
                save.setOnClickListener(v->{RadioSession s=current();if(s!=null){selection.save(s.key);error.setText("");updateSummary();}});
                dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(editingSet)saveSet(dialog);else send(dialog);}));
                if(initialSet!=null){setName.setText(initialSet.optString("name"));loadSet(initialSet);}
                render();updateSummary();dialog.show();
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }catch(Exception failure){nodeActionResult("Cannot open destination selection: "+failure.getMessage());}
        }
        void loadSet(JSONObject set) throws Exception {
            JSONArray targets=set.getJSONArray("targets");List<String[]> resolved=new ArrayList<>();
            // Resolve everything before applying, so an unavailable radio cannot silently truncate a set.
            for(int i=0;i<targets.length();i++) {
                JSONObject target=targets.getJSONObject(i);String foundRadio=null,foundId=null;
                for(RadioSession radio:radios)for(Map.Entry<String,JSONObject> option:choices.get(radio.key).entrySet()) {
                    JSONObject current=option.getValue();
                    if(current.optString("radio").equals(target.optString("radio"))&&current.optString("nodeId").equals(target.optString("nodeId"))&&current.optString("peer").equals(target.optString("peer"))&&current.optInt("channel")==target.optInt("channel")) {foundRadio=radio.key;foundId=option.getKey();}
                }
                if(foundRadio==null)throw new IllegalArgumentException("Destination unavailable: "+target.optString("label")+". Connect its original radio and load its channels/contacts first.");
                resolved.add(new String[]{foundRadio,foundId});
            }
            if(selection.hasUnsaved())throw new IllegalArgumentException("Save your manual destinations before adding a set.");
            for(String[] target:resolved){selection.select(target[0],target[1],true);selection.save(target[0]);}
        }
        void saveSet(AlertDialog dialog) {
            try {
                String name=setName.getText().toString().trim();if(name.isEmpty())throw new IllegalArgumentException("Enter a set name");
                if(selection.hasUnsaved())throw new IllegalArgumentException("Save Destinations for each edited radio first");
                JSONArray targets=new JSONArray();
                for(Map.Entry<String,java.util.Set<String>> radio:selection.snapshot().entrySet())for(String id:radio.getValue())targets.put(new JSONObject(choices.get(radio.getKey()).get(id).toString()));
                if(targets.length()==0)throw new IllegalArgumentException("Select and save at least one destination");
                for(Map.Entry<String,JSONObject> existing:destinationSets().entrySet())if(!existing.getKey().equals(setKey)&&existing.getValue().optString("name").equalsIgnoreCase(name))throw new IllegalArgumentException("Choose a different name; a set with this name already exists");
                String key=setKey==null?"destination_set_"+java.util.UUID.randomUUID():setKey;
                if(!getPreferences(0).edit().putString(key,new JSONObject().put("name",name).put("targets",targets).toString()).commit())throw new IOException("Could not save destination set");
                dialog.dismiss();showDestinationSets();
            }catch(Exception failure){error.setText(failure.getMessage());}
        }
        void render() {
            list.removeAllViews();search.setVisibility(direct?View.VISIBLE:View.GONE);channelButton.setEnabled(direct);dmButton.setEnabled(!direct);
            RadioSession s=current();if(s==null)return;
            String query=search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);int count=0;
            for(Map.Entry<String,JSONObject> option:choices.get(s.key).entrySet()) {
                String id=option.getKey(),name=option.getValue().optString("display");
                if(id.startsWith("dm:")!=direct||(direct&&!name.toLowerCase(java.util.Locale.ROOT).contains(query)))continue;
                android.widget.CheckBox check=new android.widget.CheckBox(MainActivity.this);check.setText(name);check.setChecked(selection.selected(s.key,id));
                check.setOnCheckedChangeListener((button,checked)->{selection.select(s.key,id,checked);updateSummary();});list.addView(check);count++;
            }
            if(count==0)list.addView(label(direct?"No matching nodes on this radio":"No channels reported by this radio",14,Color.GRAY,false));
        }
        void updateSummary() {
            StringBuilder text=new StringBuilder();int total=0;
            for(Map.Entry<String,java.util.Set<String>> radio:selection.snapshot().entrySet())for(String id:radio.getValue()) {
                JSONObject target=choices.get(radio.getKey()).get(id);total++;
                text.append("\n").append(target.optString("label")).append(id.startsWith("dm:")?" · DM":" · channel");
            }
            summary.setText("Saved destinations: "+total+text+(selection.hasUnsaved()?"\nUnsaved changes — press Save Destinations for each edited radio.":""));
        }
        void send(AlertDialog dialog) {
            try {
                if(!getPreferences(0).getBoolean("multi_send_enabled",false))throw new IllegalArgumentException("Enable Send to multiple in Config first");
                if(selection.hasUnsaved())throw new IllegalArgumentException("Save Destinations for each edited radio first");
                if(sendChain==null)throw new IllegalArgumentException("Sending storage is unavailable");
                if(chainUnfinished())throw new IllegalArgumentException("Finish or cancel the existing chain first");
                String text=message.getText().toString().trim();if(text.isEmpty())throw new IllegalArgumentException("Enter a message");
                JSONArray items=new JSONArray();
                for(Map.Entry<String,java.util.Set<String>> radio:selection.snapshot().entrySet())for(String id:radio.getValue()) {
                    JSONObject target=choices.get(radio.getKey()).get(id);RadioSession s=chainRadio(target);String peer=target.optString("peer");
                    if(s==null)throw new IllegalArgumentException("Reconnect "+target.optString("label"));
                    if(peer.isEmpty()?!s.channelNames.containsKey(target.optInt("channel")):!s.knownNodes.containsKey(peer))throw new IllegalArgumentException("Destination is no longer available: "+target.optString("label"));
                    List<String> parts=messageParts(text,s.protocol.textLimit(!peer.isEmpty()));
                    for(int part=0;part<parts.size();part++)items.put(new JSONObject(target.toString()).put("text",parts.get(part)).put("part",part+1).put("parts",parts.size()).put("state","pending"));
                }
                if(items.length()==0)throw new IllegalArgumentException("Select and save at least one destination");
                JSONObject previous=sendChain.data;
                sendChain.data=SendChain.empty().put("items",items).put("originalText",text).put("concurrent",parallel.isChecked()).put("multiple",true).put("paused",false);
                try{sendChain.save();}catch(Exception failure){sendChain.data=previous;throw failure;}
                dialog.dismiss();showChain();
            }catch(Exception failure){error.setText(failure.getMessage());}
        }
    }

    private void tickChain() {
        if(sendChain==null)return;
        try {
            long now=SystemClock.elapsedRealtime();boolean changed=false,active=false;
            java.util.Set<String> busy=new java.util.HashSet<>();
            for(int i=0;i<sendChain.items().length();i++) {
                JSONObject item=sendChain.items().getJSONObject(i);RadioSession s=chainRadio(item);
                // Tracked entries retain late ACKs after an item's original timeout.
                ChatEntry entry=null;
                if(s!=null)for(ChatEntry candidate:s.trackedMessages.values())if(candidate.id==item.optLong("messageId",-1)){entry=candidate;break;}
                boolean repeatHeard=entry!=null&&item.optString("peer").isEmpty()&&entry.heardRepeats>0;
                if(entry!=null&&(repeatHeard||entry.delivery.equals("delivered")||entry.delivery.equals("delivered_mesh"))&&!item.optString("state").equals("complete")) {
                    boolean wasActive=item.optString("state").equals("active");
                    item.put("state","complete").put("detail",repeatHeard?"Repeat heard (not recipient delivery)":entry.delivery);changed=true;
                    if(wasActive){nextChainSend=now+chainGapMs();if(s!=null&&item.optInt("parts",1)>1)s.nextSplitSend=now+splitIntervalMs();}
                }
                if(!item.optString("state").equals("active"))continue;
                String result=entry==null?"Waiting for previous transmission window after disconnection or deletion":entry.delivery;
                if(!result.equals(item.optString("detail"))){item.put("detail",result);changed=true;}
                if(item.optString("radio").equals("mc")&&item.optString("peer").isEmpty()&&result.equals("sent_radio")&&!item.optBoolean("observationStarted")) {
                    item.put("observationStarted",true).put("deadline",now+item.optLong("observationMs"));changed=true;
                }
                boolean automatic=s!=null&&s.protocol.automaticDeliveryPending(item.optLong("token"));
                if(automatic&&s.protocol instanceof MeshCoreProtocol) {
                    long due=now+((MeshCoreProtocol)s.protocol).responseWaitMillis(item.optLong("token"));
                    if(due>item.optLong("deadline")){item.put("deadline",due);changed=true;}
                }
                boolean channel=item.optString("peer").isEmpty();
                boolean observed=entry!=null&&channel&&entry.heardRepeats>0;
                boolean advance=channel&&item.optString("radio").equals("mc")&&item.optBoolean("advanceRequested")&&result.equals("sent_radio");
                boolean terminal=result.startsWith("failed")||result.startsWith("unconfirmed")||result.equals("delivered")||result.equals("delivered_mesh");
                // MC channel acceptance is not delivery. Observe repeats until the configured deadline.
                if(!automatic&&(terminal||observed||advance||now>=item.optLong("deadline"))) {
                    boolean success=result.equals("delivered")||result.equals("delivered_mesh")||observed;
                    item.put("state",success?"complete":item.optBoolean("defer")?"deferred":"unconfirmed")
                        .put("detail",observed?"Repeat heard (not recipient delivery)":terminal?result:advance?"Observation ended by you; delivery unconfirmed":"No confirmation within observation window");
                    changed=true;nextChainSend=now+chainGapMs();if(s!=null&&item.optInt("parts",1)>1)s.nextSplitSend=now+splitIntervalMs();
                } else {active=true;busy.add(item.optString("radio")+":"+item.optString("nodeId"));}
            }
            if(changed)sendChain.save();
            if(sendChain.completedSuccessfully()&&!sendChain.data.optBoolean("completionNotified")) {
                sendChain.data.put("completionNotified",true);sendChain.save();
                new AlertDialog.Builder(this).setMessage("Chain Completed Successfully").setPositiveButton("OK",null).show();
            }
            if(sendChain.paused()||now<nextChainSend)return;
            if(sendChain.data.optBoolean("multiple")&&!getPreferences(0).getBoolean("multi_send_enabled",false))return;
            boolean concurrent=sendChain.data.optBoolean("concurrent")&&!sendChain.data.optBoolean("stepOnce");
            if(active&&!concurrent)return;
            for(int i=0;i<sendChain.items().length();i++) {
                JSONObject item=sendChain.items().getJSONObject(i);
                if(!item.optString("state").equals("pending"))continue;
                if(sendChain.data.optBoolean("stepOnce")&&!item.optBoolean("manualRepeat"))continue;
                String identity=item.optString("radio")+":"+item.optString("nodeId");
                if(busy.contains(identity))continue;
                RadioSession s=chainRadio(item);if(s==null){if(!concurrent)return;continue;}
                if(!s.splitSends.isEmpty()){if(!concurrent)return;continue;}
                if(s.protocol instanceof MeshCoreProtocol&&((MeshCoreProtocol)s.protocol).busyForSend()){
                    String reason=((MeshCoreProtocol)s.protocol).sendWaitReason();
                    if(!reason.equals(item.optString("detail"))){item.put("detail",reason);sendChain.save();debug("Sending chain: "+reason);}
                    if(!concurrent)return;continue;
                }
                if(item.optInt("parts",1)>1&&now<s.nextSplitSend){if(!concurrent)return;continue;}
                dispatchChain(s,item);busy.add(identity);if(!concurrent||sendChain.paused())return;
            }
        }catch(Exception error){chainError(error);}
    }
    private boolean messageSplitEnabled(){return getPreferences(0).getBoolean("message_split_enabled",true);}
    private long splitIntervalMs(){return 1000L*Math.max(1,Math.min(300,getPreferences(0).getInt("message_split_interval_seconds",1)));}
    private List<String> messageParts(String text,int limit) {
        if(!messageSplitEnabled()&&text.getBytes(StandardCharsets.UTF_8).length>limit)
            throw new IllegalArgumentException("Message Split is disabled. Shorten the message or enable Message Split in Sending options.");
        return MessageChunks.split(text,limit);
    }
    private int channelObservationSeconds(){return Math.max(1,Math.min(300,getPreferences(0).getInt("mc_channel_observation_seconds",5)));}
    private long chainGapMs(){return 1000L*Math.max(1,Math.min(30,getPreferences(0).getInt("send_settling_seconds",3)));}
    private void dispatchChain(RadioSession s,JSONObject item) throws Exception {
        String peer=item.getString("peer"),text=item.getString("text");int channel=item.getInt("channel");
        if(peer.isEmpty()&&!s.channelNames.containsKey(channel))return;
        NodeEntry contact=peer.isEmpty()?null:s.knownNodes.get(peer);if(!peer.isEmpty()&&contact==null)return;
        // Persist intent before issuing a command; interruption can never silently replay it.
        long observationMs=s.meshcore&&peer.isEmpty()?channelObservationSeconds()*1000L:120000L;
        sendChain.starting(item);
        item.remove("advanceRequested");item.remove("observationStarted");
        item.put("state","active").put("observationMs",observationMs).put("deadline",SystemClock.elapsedRealtime()+120000L);sendChain.save();
        try {
            long replyId=peer.isEmpty()?item.optLong("replyId"):0;
            byte[] payload=peer.isEmpty()?s.protocol.sendChannelReply(channel,text,replyId):s.protocol.nodeCommand("dm",peer,text,contact.channel);
            ChatEntry entry=new ChatEntry("You",text,true);
            entry.id=chatStore.save(s.meshcore?"mc":"mt",s.ownId,channel,entry.sender,text,entry.time,entry.receivedAt,true,entry.delivery,peer);
            long token=s.protocol.lastRequestId();entry.packetId=s.meshcore?0:token;entry.replyId=replyId;chatStore.setReferences(entry.id,entry.packetId,replyId);
            entry.heardRepeats=s.protocol.tracksRepeats(token)?0:-1;chatStore.setHeardRepeats(entry.id,entry.heardRepeats);
            item.put("messageId",entry.id).put("token",token);sendChain.save();
            if(peer.isEmpty())s.chats.computeIfAbsent(channel,k->new ArrayList<>()).add(entry);else s.directs.computeIfAbsent(peer,k->new ArrayList<>()).add(entry);
            s.track(entry,token);publish();s.sendTracked(payload);
            if(item.optInt("parts",1)>1)s.nextSplitSend=SystemClock.elapsedRealtime()+splitIntervalMs();
        }catch(Exception error){item.put("state","unconfirmed").put("detail",error.getMessage());sendChain.data.put("paused",true);sendChain.save();throw error;}
    }
    private void showChain() {
        if(sendChain==null){nodeActionResult("Sending storage is unavailable");return;}
        if(sendChain.items().length()==0) {
            new AlertDialog.Builder(this).setTitle("Send to multiple")
                .setMessage("No saved sending chain.")
                .setPositiveButton("New multi-send",(d,w)->chooseMultiSend())
                .setNegativeButton("Close",null).show();return;
        }
        try {
            LinearLayout content=vertical();StringBuilder summary=new StringBuilder(sendChain.paused()?"Paused\n":"Running\n");
            for(int i=0;i<sendChain.items().length();i++) {
                JSONObject item=sendChain.items().getJSONObject(i);summary.append(i+1).append(". ").append(item.optString("label")).append(" · part ").append(item.optInt("part")).append('/').append(item.optInt("parts")).append(" · ").append(item.optString("state")).append("\n").append(item.optString("detail")).append("\n");
            }
            TextView details=new TextView(this);details.setText(summary);content.addView(details);
            LinearLayout activeBubbles=vertical();content.addView(activeBubbles);
            Button nextPart=new Button(this);nextPart.setText("Send Next");content.addView(nextPart);
            TextView information=label("i",20,Color.rgb(233,173,65),true);
            information.setGravity(Gravity.CENTER);information.setTypeface(android.graphics.Typeface.SERIF,android.graphics.Typeface.BOLD);
            information.setContentDescription("Sending controls information");information.setFocusable(true);
            android.graphics.drawable.GradientDrawable infoCircle=new android.graphics.drawable.GradientDrawable();
            infoCircle.setShape(android.graphics.drawable.GradientDrawable.OVAL);infoCircle.setColor(Color.rgb(39,48,66));infoCircle.setStroke(dp(1),Color.rgb(233,173,65));
            information.setBackground(infoCircle);LinearLayout.LayoutParams infoSize=new LinearLayout.LayoutParams(dp(36),dp(36));infoSize.setMargins(dp(6),dp(6),dp(6),dp(6));content.addView(information,infoSize);
            Button previousPart=new Button(this);previousPart.setText("Send again the previous");content.addView(previousPart);
            information.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Sending controls").setMessage("Send Next ends the current MeshCore channel observation after radio acceptance, keeps the pause before the next message, and continues automatically. For MeshCore DMs and Meshtastic it resumes pending work but waits for active retries or acknowledgment timeouts; it does not cancel radio transmissions. Resending the previous part may create a duplicate and pauses afterward.").setPositiveButton("OK",null).show());
            Button pause=new Button(this);pause.setText(sendChain.paused()?"Continue pending messages":"Pause after this message");content.addView(pause);
            Button retry=new Button(this);retry.setText("Review unsuccessful messages");content.addView(retry);
            Button fresh=new Button(this);fresh.setText("New multi-send");content.addView(fresh);
            Button cancelChain=new Button(this);cancelChain.setText("Cancel Chain");content.addView(cancelChain);
            content.addView(label("Cancel remaining stops queued messages only. The active message and its retries may finish. Use Stop retries to stop future MeshCore DM attempts.",14,Color.GRAY,false));
            content.addView(label("Cancel Chain removes this saved queue. Chat messages remain; transmissions already handed to a radio may finish.",14,Color.GRAY,false));
            fresh.setVisibility(sendChain.data.optBoolean("multiple")?View.VISIBLE:View.GONE);
            Button stopContinue=new Button(this);stopContinue.setText("Stop retries and continue");content.addView(stopContinue);
            Button stopLater=new Button(this);stopLater.setText("Stop retries and retry later");content.addView(stopLater);
            ScrollView scroll=new ScrollView(this);scroll.addView(content);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle(sendChain.data.optBoolean("multiple")?"Send to multiple · progress":"Split message · progress").setView(scroll).setPositiveButton("Close",null)
                .setNeutralButton("Refresh",(d,w)->showChain()).setNegativeButton("Cancel remaining",(d,w)->{
                    try{int cancelled=sendChain.cancelRemaining();showChain();new AlertDialog.Builder(this).setTitle("Remaining messages cancelled").setMessage(cancelled+" queued messages cancelled. The current transmission and its retries may finish. Nothing already transmitted can be recalled.").setPositiveButton("OK",null).show();}catch(Exception error){chainError(error);}
                }).create();
            pause.setOnClickListener(v->{try{sendChain.data.put("paused",!sendChain.paused()).put("stepOnce",false);sendChain.save();dialog.dismiss();showChain();}catch(Exception error){chainError(error);}});
            cancelChain.setOnClickListener(v->{
                try {
                    // Keep tracked chat entries so late delivery results still reach history.
                    for(int i=0;i<sendChain.items().length();i++) {
                        JSONObject item=sendChain.items().getJSONObject(i);
                        if(!item.optString("state").equals("active"))continue;
                        RadioSession s=chainRadio(item);
                        if(s!=null&&s.protocol instanceof MeshCoreProtocol&&!item.optString("peer").isEmpty())
                            ((MeshCoreProtocol)s.protocol).stopFutureAttempts(item.optLong("token"));
                    }
                    sendChain.cancelChain();dialog.dismiss();showChain();
                }catch(Exception error){chainError(error);}
            });
            nextPart.setOnClickListener(v->{try{sendChain.sendNext();nextPart.setEnabled(false);}catch(Exception error){chainError(error);}});
            previousPart.setOnClickListener(v->{
                JSONObject previous=sendChain.previousFinished(),original=sendChain.data;if(previous==null)return;
                try{sendChain.pause();}catch(Exception error){chainError(error);return;}
                new AlertDialog.Builder(MainActivity.this).setTitle("Send again the previous part?")
                    .setMessage(previous.optString("label")+" · part "+previous.optInt("part")+"/"+previous.optInt("parts")+"\n\n"+previous.optString("text")+"\n\nThis creates another message on the same radio and destination. The recipient may already have it.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Send again",(d,w)->{try{if(sendChain.data==original)sendChain.repeatPrevious(previous);}catch(Exception error){chainError(error);}}).show();
            });
            retry.setOnClickListener(v->reviewChainFailures());fresh.setOnClickListener(v->{dialog.dismiss();chooseMultiSend();});
            stopContinue.setOnClickListener(v->chooseStopChain(false));stopLater.setOnClickListener(v->chooseStopChain(true));
            dialog.show();
            main.post(new Runnable(){public void run(){
                if(closing||!dialog.isShowing())return;
                boolean manual=sendChain.data.optBoolean("stepOnce"),enabled=!sendChain.data.optBoolean("multiple")||getPreferences(0).getBoolean("multi_send_enabled",false);
                nextPart.setEnabled(enabled&&!manual&&sendChain.hasPending());previousPart.setEnabled(enabled&&!manual&&sendChain.previousFinished()!=null);
                pause.setText(sendChain.paused()?"Continue pending messages":"Pause after this message");
                boolean hasActive=false;for(int i=0;i<sendChain.items().length();i++)hasActive|=sendChain.items().optJSONObject(i).optString("state").equals("active");
                StringBuilder live=new StringBuilder(!hasActive&&!sendChain.hasPending()?"Finished\n":sendChain.paused()?"Paused (active transmissions may finish)\n":"Running\n");boolean canStop=false;
                activeBubbles.removeAllViews();
                if(manual)live.append("One manual send requested; waiting for active transmissions, pause before the next message and original radio.\n");
                for(int i=0;i<sendChain.items().length();i++) {
                    JSONObject item=sendChain.items().optJSONObject(i);String status=item.optString("state");
                    live.append(i+1).append(". ").append(item.optString("label")).append(" · part ").append(item.optInt("part")).append('/').append(item.optInt("parts")).append(" · ").append(status).append("\n");
                    if(!item.optString("detail").isEmpty())live.append(item.optString("detail")).append("\n");
                    JSONArray attempts=item.optJSONArray("attempts");
                    if(attempts!=null)for(int a=0;a<attempts.length();a++){JSONObject attempt=attempts.optJSONObject(a);live.append("  Earlier attempt ").append(a+1).append(": ").append(attempt.optString("state")).append(" · ").append(attempt.optString("detail")).append("\n");}
                    if(status.equals("pending")&&chainRadio(item)==null)live.append("Waiting for original radio to reconnect\n");
                    if(status.equals("active")) {
                        LinearLayout bubble=vertical();bubble.setPadding(dp(12),dp(10),dp(12),dp(10));
                        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
                        background.setColor(Color.rgb(39,48,66));background.setCornerRadius(dp(14));bubble.setBackground(background);
                        bubble.addView(label(item.optString("label")+" · part "+item.optInt("part")+"/"+item.optInt("parts"),14,Color.rgb(197,160,255),true));
                        bubble.addView(label(item.optString("text"),17,Color.WHITE,false));
                        String delivery=item.optString("detail");RadioSession radio=chainRadio(item);
                        if(radio!=null)for(ChatEntry entry:radio.trackedMessages.values())if(entry.id==item.optLong("messageId",-1)) {
                            delivery=entry.delivery+(entry.heardRepeats>=0?"\nHeard repeats: "+entry.heardRepeats+" (not recipient delivery)":"");break;
                        }
                        bubble.addView(label(delivery,14,Color.LTGRAY,false));activeBubbles.addView(bubble);
                    }
                    canStop|=status.equals("active")&&item.optString("radio").equals("mc")&&!item.optString("peer").isEmpty();
                }
                details.setText(live);stopContinue.setVisibility(canStop?View.VISIBLE:View.GONE);stopLater.setVisibility(canStop?View.VISIBLE:View.GONE);
                main.postDelayed(this,250);
            }});
        }catch(Exception error){chainError(error);}
    }
    private void chooseStopChain(boolean later) {
        List<JSONObject> active=new ArrayList<>();List<String> labels=new ArrayList<>();
        for(int i=0;i<sendChain.items().length();i++){JSONObject item=sendChain.items().optJSONObject(i);if(item.optString("state").equals("active")&&item.optString("radio").equals("mc")&&!item.optString("peer").isEmpty()){active.add(item);labels.add(item.optString("label"));}}
        new AlertDialog.Builder(this).setTitle("Stop future attempts for which DM?").setItems(labels.toArray(new String[0]),(d,w)->{
            try{JSONObject item=active.get(w);RadioSession s=chainRadio(item);if(s!=null&&s.protocol instanceof MeshCoreProtocol)((MeshCoreProtocol)s.protocol).stopFutureAttempts(item.optLong("token"));item.put("defer",later);sendChain.save();}catch(Exception error){chainError(error);}
        }).setNegativeButton("Cancel",null).show();
    }
    private void reviewChainFailures() {
        List<JSONObject> failed=new ArrayList<>();List<String> labels=new ArrayList<>();
        for(int i=0;i<sendChain.items().length();i++){JSONObject item=sendChain.items().optJSONObject(i);String status=item.optString("state");if(status.equals("unconfirmed")||status.equals("deferred")){failed.add(item);labels.add(item.optString("label")+" · part "+item.optInt("part")+" · "+item.optString("detail"));}}
        boolean[] selected=new boolean[failed.size()];
        new AlertDialog.Builder(this).setTitle("Choose messages to retry (duplicates are possible)")
            .setMultiChoiceItems(labels.toArray(new String[0]),selected,(d,w,checked)->selected[w]=checked).setNegativeButton("Cancel",null)
            .setPositiveButton("Resend selected",(d,w)->{try{boolean any=false;for(int i=0;i<selected.length;i++)if(selected[i]){sendChain.retry(failed.get(i));any=true;}if(any){sendChain.data.put("paused",false).put("stepOnce",false);sendChain.save();}}catch(Exception error){chainError(error);}}).show();
    }
    private void composeQueued(RadioSession s,String peer,String text) {
        if(pageReady) web.evaluateJavascript("window.onComposeQueued("+JSONObject.quote(s.key)+","+JSONObject.quote(peer)+","+JSONObject.quote(text)+")",null);
    }
    private void copyText(String text) {
        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard!=null && text!=null) clipboard.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.app_name),text));
    }
    private void messageAction(String key,long id,String action) {
        RadioSession s=sessions.get(key);
        if(s==null||s.ownId.isEmpty()) {nodeActionResult("This radio session is no longer available");return;}
        String radio=s.meshcore?"mc":"mt";
        ChatStore.Message message=null;
        for(ChatStore.Message item:chatStore.load(radio,s.ownId)) if(item.id==id) {message=item;break;}
        if(message==null) {nodeActionResult("Message no longer exists");return;}
        final ChatStore.Message saved=message;
        if(action.equals("delete")) {
            new AlertDialog.Builder(this).setTitle("Delete this message?").setMessage("This removes only the local copy on this phone.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Delete",(dialog,which)->{
                        chatStore.deleteMessage(radio,s.ownId,id);
                        for(Map.Entry<Long,ChatEntry> tracked:s.trackedMessages.entrySet())
                            if(tracked.getValue().id==id) s.protocol.expireRequest(tracked.getKey());
                        s.trackedMessages.values().removeIf(entry->entry.id==id);
                        loadSessionHistory(s);publish();
                    }).show();
        } else if(action.equals("cancel")&&saved.own) {
            for(Map.Entry<Long,ChatEntry> tracked:s.trackedMessages.entrySet()) {
                if(tracked.getValue().id==id&&s.protocol.automaticDeliveryPending(tracked.getKey())) {
                    if(s.protocol instanceof MeshCoreProtocol)((MeshCoreProtocol)s.protocol).stopFutureAttempts(tracked.getKey());
                    nodeActionResult("Future retries stopped; waiting for the current attempt's response window");
                }
            }
        } else if(action.equals("retry")&&saved.own) {
            for(Map.Entry<Long,ChatEntry> tracked:s.trackedMessages.entrySet())
                if(tracked.getValue().id==id&&s.protocol.automaticDeliveryPending(tracked.getKey())) {
                    nodeActionResult("This message is still retrying. Stop retries first.");return;
                }
            if(!s.connected) {nodeActionResult("Reconnect this radio before retrying");return;}
            if(saved.peer.isEmpty()) sendMessage(s,saved.channel,saved.text,saved.replyId);
            else nodeAction(s,saved.peer,"dm",saved.text);
        }
    }
    private JSONObject traceBase(RadioSession s,long token,String target) throws JSONException {
        NodeEntry contact=s.knownNodes.get(target);
        return new JSONObject().put("key",s.key).put("radio",s.meshcore?"mc":"mt").put("requestId",token)
                .put("action",s.pendingTraceKind).put("target",target).put("name",contact==null||contact.name.isEmpty()?target:contact.name);
    }
    private void showTracePending(RadioSession s,long token,String target) {
        try {
            JSONObject result=traceBase(s,token,target).put("pending",true);
            if(pageReady) web.evaluateJavascript("window.onTraceResult("+result+")",null);
        } catch(JSONException error) {nodeActionResult("Could not display trace");}
    }
    private JSONArray traceNodes(RadioSession s,List<String> ids,List<Integer> snrs) throws JSONException {
        JSONArray result=new JSONArray();
        for(int i=0;i<ids.size();i++) {
            String id=ids.get(i),name=id;
            if(id.equals(s.ownId)) name=s.ownName;
            else if(id.equals("!ffffffff")) name="Unknown hop";
            else if(s.meshcore) {
                // Discovery endpoints are contact IDs, whereas intermediate hops are short hashes.
                NodeEntry endpoint=id.length()>6?s.knownNodes.get(id):null;
                if(endpoint!=null)name=endpoint.name.isEmpty()?id:endpoint.name;
                else {
                    Map<String,String> repeaters=new LinkedHashMap<>();
                    for(NodeEntry contact:s.knownNodes.values())
                        if("Repeater".equals(contact.role))repeaters.put(contact.id,contact.name);
                    name=MeshCorePathNames.resolve(id,repeaters);
                }
            } else if(s.knownNodes.containsKey(id)&&!s.knownNodes.get(id).name.isEmpty()) name=s.knownNodes.get(id).name;
            JSONObject hop=new JSONObject().put("id",id).put("name",name);
            if(i>0 && snrs.size()==ids.size()-1 && snrs.get(i-1)!=-128) hop.put("snr",snrs.get(i-1)/4.0);
            result.put(hop);
        }
        return result;
    }
    private void showTrace(RadioSession s,TraceResult trace) {
        try {
            JSONObject result=traceBase(s,trace.requestId,trace.target).put("pending",false).put("note",trace.note)
                    .put("forward",traceNodes(s,trace.forward,trace.forwardSnr))
                    .put("backward",traceNodes(s,trace.backward,trace.backwardSnr));
            s.lastTrace=result; publish();
            if(pageReady) web.evaluateJavascript("window.onTraceResult("+result+")",null);
        } catch(JSONException error) {nodeActionResult("Could not display trace route");}
    }
    private String readNotice(java.io.InputStream input) throws java.io.IOException {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=input.read(buffer))!=-1)out.write(buffer,0,count);
        return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
    }
    private void showAdvertResult(RadioSession s,String text,boolean success) {
        if(pageReady&&web!=null) web.evaluateJavascript("window.onAdvertResult("+JSONObject.quote(s.key)+","+JSONObject.quote(text)+","+success+")",null);
    }
    private void nodeActionResult(String text) {
        debug("Operation result: "+text);
        if (pageReady && web != null) web.evaluateJavascript("window.onNodeActionResult(" + JSONObject.quote(text) + ")", null);
    }

    private void disconnect(RadioSession session) {
        if (scanningSession == session) stopScanning(session, "Scan stopped");
        if (awaitingUsbSession == session) { awaitingUsbSession = null; awaitingUsb = null; }
        session.disconnect();
        chooseFallback(session);
    }
    private void chooseFallback(RadioSession ended) {
        if(ended.meshcore && selectedMc!=ended || !ended.meshcore && selectedMt!=ended) return;
        for(RadioSession candidate:sessions.values()) if(candidate!=ended && candidate.connected &&
                candidate.meshcore==ended.meshcore) {
            if(ended.meshcore) selectedMc=candidate; else selectedMt=candidate;
            return;
        }
    }

    private void buildUi() {
        LinearLayout root = vertical(); root.setBackgroundColor(0xfff5f7f4); setContentView(root);
        TextView title = label(getString(R.string.app_name), 23, DARK, true); title.setPadding(dp(16), dp(16), dp(16), dp(2)); root.addView(title);
        activeLabel = label("Meshtastic · Disconnected", 13, GREEN, false);
        activeLabel.setPadding(dp(16), 0, dp(16), dp(12)); root.addView(activeLabel);
        pages = new FrameLayout(this); root.addView(pages, new LinearLayout.LayoutParams(-1, 0, 1));
        buildConnections(); buildMessages(); buildNodes();
        LinearLayout nav = horizontal(); nav.setBackgroundColor(Color.WHITE); root.addView(nav);
        addNav(nav, "Radios", 0); addNav(nav, "Messages", 1); addNav(nav, "Nodes", 2);
    }

    private void buildConnections() {
        connectionsPage = vertical(); connectionsPage.setPadding(dp(16), dp(8), dp(16), dp(16));
        pages.addView(connectionsPage);
        connectionsPage.addView(label("Radios", 22, DARK, true));
        connectionsPage.addView(label("Connect one of each. Both can stay connected while you switch chats.", 14, DARK, false));
        mtStatus = label("Meshtastic · Disconnected", 16, GREEN, true);
        mcStatus = label("MeshCore · Disconnected", 16, GREEN, true);
        radioCard(connectionsPage, mt, mtStatus);
        radioCard(connectionsPage, mc, mcStatus);
    }

    private void radioCard(LinearLayout parent, RadioSession session, TextView statusView) {
        LinearLayout card = vertical(); card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(rounded(Color.WHITE));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(16); parent.addView(card, cardParams);
        card.addView(statusView);
        LinearLayout row = horizontal(); card.addView(row);
        Button ble = new Button(this); ble.setText("Bluetooth"); row.addView(ble, new LinearLayout.LayoutParams(0, -2, 1));
        Button usb = new Button(this); usb.setText("USB serial"); row.addView(usb, new LinearLayout.LayoutParams(0, -2, 1));
        ble.setOnClickListener(view -> connectBle(session));
        usb.setOnClickListener(view -> connectUsb(session));
    }

    private void buildMessages() {
        messagesPage = vertical(); messagesPage.setPadding(dp(12), dp(4), dp(12), dp(8)); pages.addView(messagesPage);
        messagesPage.addView(label("Messages", 22, DARK, true));
        LinearLayout familyRow = horizontal(); messagesPage.addView(familyRow);
        Button mtTab = new Button(this); mtTab.setText("Meshtastic"); familyRow.addView(mtTab, new LinearLayout.LayoutParams(0, -2, 1));
        Button mcTab = new Button(this); mcTab.setText("MeshCore"); familyRow.addView(mcTab, new LinearLayout.LayoutParams(0, -2, 1));
        mtTab.setOnClickListener(view -> select(mt)); mcTab.setOnClickListener(view -> select(mc));
        chatTitle = label("Meshtastic channels", 17, DARK, true); messagesPage.addView(chatTitle);
        channels = new Spinner(this); messagesPage.addView(channels);
        channels.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { renderChat(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { renderChat(); }
        });
        chatScroll = new ScrollView(this); messagesPage.addView(chatScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        chatList = vertical(); chatList.setPadding(dp(4), dp(10), dp(4), dp(10)); chatScroll.addView(chatList);
        chatHint = label("Connect a radio and select a channel to start chatting.", 13, GREEN, false);
        messagesPage.addView(chatHint);
        LinearLayout composer = horizontal(); messagesPage.addView(composer);
        compose = new EditText(this); compose.setSingleLine(true); compose.setHint("Write a channel message");
        composer.addView(compose, new LinearLayout.LayoutParams(0, -2, 1));
        Button send = new Button(this); send.setText("Send"); composer.addView(send);
        send.setOnClickListener(view -> sendMessage());
        refreshChannels(0);
    }

    private void buildNodes() {
        nodesPage = vertical(); nodesPage.setPadding(dp(16), dp(8), dp(16), dp(16)); pages.addView(nodesPage);
        nodesPage.addView(label("Nodes and contacts", 22, DARK, true));
        LinearLayout row = horizontal(); nodesPage.addView(row);
        Button mtTab = new Button(this); mtTab.setText("Meshtastic"); row.addView(mtTab, new LinearLayout.LayoutParams(0, -2, 1));
        Button mcTab = new Button(this); mcTab.setText("MeshCore"); row.addView(mcTab, new LinearLayout.LayoutParams(0, -2, 1));
        mtTab.setOnClickListener(view -> select(mt)); mcTab.setOnClickListener(view -> select(mc));
        ScrollView scroll = new ScrollView(this); nodesPage.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        nodeText = label("Connect a radio to load nodes or contacts.", 15, DARK, false);
        nodeText.setPadding(dp(8), dp(12), dp(8), dp(8)); scroll.addView(nodeText);
    }

    private void addNav(LinearLayout row, String title, int target) {
        Button button = new Button(this); button.setText(title);
        row.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        button.setOnClickListener(view -> showPage(target));
    }

    private void showPage(int target) {
        page = target;
        connectionsPage.setVisibility(target == 0 ? View.VISIBLE : View.GONE);
        messagesPage.setVisibility(target == 1 ? View.VISIBLE : View.GONE);
        nodesPage.setVisibility(target == 2 ? View.VISIBLE : View.GONE);
        if (target == 1) renderChat();
        if (target == 2) renderNodes();
    }

    private void select(RadioSession session) {
        selected = session;
        refreshSelected();
    }

    private void refreshSelected() {
        if (activeLabel == null) return;
        activeLabel.setText(selected.name + " · " + selected.state);
        chatTitle.setText(selected.name + " channels");
        refreshChannels(0); renderNodes();
    }

    private int selectedChannel() {
        Object item = channels == null ? null : channels.getSelectedItem();
        return item instanceof ChannelChoice ? ((ChannelChoice)item).index : 0;
    }

    private void refreshChannels(int preferred) {
        if (channels == null) return;
        int keep = selectedChannel();
        List<ChannelChoice> items = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : selected.channelNames.entrySet()) items.add(new ChannelChoice(entry.getKey(), entry.getValue()));
        channels.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        int target = keep;
        for (int i = 0; i < items.size(); i++) if (items.get(i).index == target) { channels.setSelection(i); break; }
        renderChat();
    }

    private void renderChat() {
        if (chatList == null || chatHint == null) return;
        chatList.removeAllViews();
        int channel = selectedChannel();
        List<ChatEntry> entries = selected.chats.get(channel);
        if (entries == null || entries.isEmpty()) {
            chatList.addView(label("No messages on this channel yet.", 15, DARK, false));
        } else {
            for (ChatEntry entry : entries) {
                LinearLayout outer = horizontal(); outer.setGravity(entry.own ? Gravity.END : Gravity.START);
                LinearLayout.LayoutParams outerParams = new LinearLayout.LayoutParams(-1, -2);
                outerParams.bottomMargin = dp(8); chatList.addView(outer, outerParams);
                LinearLayout bubble = vertical(); bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
                bubble.setBackground(rounded(entry.own ? 0xffd3eee3 : Color.WHITE));
                outer.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
                bubble.addView(label(entry.sender + (entry.own ? " · " + entry.delivery : "") + "  ·  " + entry.time, 12, GREEN, true));
                bubble.addView(label(entry.text, 16, DARK, false));
            }
        }
        chatHint.setText(selected.connected ? "Channel " + channel + " · messages are sent through the radio" : "Connect " + selected.name + " on the Radios tab to send.");
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void renderNodes() {
        if (nodeText == null) return;
        StringBuilder text = new StringBuilder(selected.name).append(" · ").append(selected.knownNodes.size()).append(" entries\n\n");
        if (selected.knownNodes.isEmpty()) text.append("Connect the radio to load nodes or contacts.");
        else for (NodeEntry node : selected.knownNodes.values()) text.append(node.name).append("  ").append(node.id).append(" · ").append(node.role).append("\n");
        nodeText.setText(text.toString());
    }

    private void sendMessage() {
        if (!selected.connected || selected.protocol == null) { selected.updateStatus("Connect this radio on the Radios tab first"); return; }
        if (selected.ownId.isEmpty()) { selected.updateStatus("Waiting for radio identity"); return; }
        String text = compose.getText().toString().trim();
        if (text.isEmpty()) return;
        int channel = selectedChannel();
        try {
            byte[] payload = selected.protocol.sendChannel(channel, text);
            ChatEntry entry = new ChatEntry("You", text, true);
            entry.id = chatStore.save(selected.meshcore ? "mc" : "mt", selected.ownId, channel, entry.sender,
                    entry.text, entry.time, entry.receivedAt, entry.own, entry.delivery, "");
            selected.chats.computeIfAbsent(channel, ignored -> new ArrayList<>()).add(entry);
            selected.track(entry,selected.protocol.lastRequestId());
            selected.sendTracked(payload);
            compose.setText(""); renderChat();
        } catch (IllegalArgumentException error) { selected.updateStatus(error.getMessage()); }
    }

    private void connectUsb(RadioSession session) {
        connectUsb(session,null);
    }
    private void connectUsb(RadioSession session,UsbSerialDriver chosen) {
        session.start();
        final int generation = session.generation;
        session.usb = new UsbRadio(this, session.meshcore, new UsbRadio.Listener() {
            @Override public void detected(boolean meshcore) { main.post(()->{if(session.generation==generation)session.detected(meshcore);}); }
            @Override public void ready() { main.post(() -> { if (session.generation == generation) session.ready(false); }); }
            @Override public void received(byte[] bytes) { main.post(() -> { if (session.generation == generation) session.receive(bytes); }); }
            @Override public void written(byte[] bytes,boolean success,String reason) { main.post(() -> {if(session.generation==generation) session.written(bytes,success,reason);}); }
            @Override public void status(String text) { main.post(() -> { if (session.generation == generation) session.updateStatus(text); }); }
        });
        List<UsbSerialDriver> drivers = session.usb.drivers();
        if (drivers.isEmpty()) { session.loading=false;session.updateStatus("No USB serial radio found"); return; }
        if(chosen!=null){openUsbDriver(session,chosen);return;}
        String[] names = new String[drivers.size()];
        for (int i = 0; i < drivers.size(); i++) names[i] = drivers.get(i).getDevice().getDeviceName();
        new AlertDialog.Builder(this).setTitle("Choose " + session.name + " USB radio").setItems(names, (dialog, which) -> {
            UsbSerialDriver driver = drivers.get(which);
            openUsbDriver(session,driver);
        }).setOnCancelListener(dialog->{session.loading=false;session.close();session.updateStatus("Connection cancelled");}).show();
    }

    private void openUsbDriver(RadioSession session,UsbSerialDriver driver) {
        for(RadioSession other:sessions.values())if(other!=session&&other.usb!=null&&(other.connected||other.loading)&&other.usbDeviceName.equals(driver.getDevice().getDeviceName())) {
            session.close();session.loading=false;session.updateStatus("USB device already in use");return;
        }
        session.usbDeviceName=driver.getDevice().getDeviceName();
        if(session.usb.hasPermission(driver.getDevice()))session.usb.connect(driver);
        else {
            if(awaitingUsbSession!=null){session.close();session.loading=false;session.updateStatus("Finish the current USB permission request first");return;}
            awaitingUsbSession=session;awaitingUsb=driver;
            PendingIntent permission=PendingIntent.getBroadcast(this,0,new Intent(USB_PERMISSION).setPackage(getPackageName()),PendingIntent.FLAG_MUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            ((UsbManager)getSystemService(USB_SERVICE)).requestPermission(driver.getDevice(),permission);
        }
    }
    private void connectAllAvailable() {
        if(connectingAvailable){nodeActionResult("Discovering available radios already");return;}
        if(awaitingUsbSession!=null){nodeActionResult("Finish the current USB permission request first");publish();return;}
        connectingAvailable=true;availableRun++;retryingAvailable=false;availableDiscoveryError="";
        expectedAvailableUsb.clear();expectedAvailableBle.clear();publish();availableUsb.clear();availableBle.clear();
        availableUsb.addAll(com.hoho.android.usbserial.driver.UsbSerialProber.getDefaultProber().findAllDrivers((UsbManager)getSystemService(USB_SERVICE)));
        for(UsbSerialDriver driver:availableUsb)expectedAvailableUsb.put(driver.getDevice().getDeviceName(),driver);
        for(ChatStore.KnownRadio item:chatStore.radios())if(availableBleConnected(item))expectedAvailableBle.put(item.radio+":"+item.id,item);
        connectNextAvailableUsb();
    }
    private void connectNextAvailableUsb() {
        if(!connectingAvailable)return;
        if(closing){connectingAvailable=false;return;}
        while(!availableUsb.isEmpty()) {
            UsbSerialDriver driver=availableUsb.removeFirst();RadioSession used=null;
            for(RadioSession s:sessions.values())if(s.usb!=null&&(s.connected||s.loading)&&s.usbDeviceName.equals(driver.getDevice().getDeviceName()))used=s;
            if(used!=null){if(used.loading){waitAvailableConnection(used,SystemClock.elapsedRealtime()+70000,this::connectNextAvailableUsb);return;}if(used.syncSucceeded)continue;disconnect(used);}
            RadioSession s=newConnectionSession("mt");s.knownOnly=true;connectUsb(s,driver);
            waitAvailableConnection(s,SystemClock.elapsedRealtime()+70000,this::connectNextAvailableUsb);return;
        }
        if(retryingAvailable&&availableBle.isEmpty())finishAvailableConnections();
        else if(retryingAvailable)queueBle("available",true,this::connectNextAvailableBle);
        else queueBle("available",true,this::scanAvailableBle);
    }
    private void waitAvailableConnection(RadioSession s,long deadline,Runnable next) {
        int run=availableRun;
        main.postDelayed(()->{
            if(closing||!connectingAvailable||run!=availableRun)return;
            if(s==awaitingUsbSession){waitAvailableConnection(s,SystemClock.elapsedRealtime()+70000,next);return;}
            if(s.loading&&SystemClock.elapsedRealtime()<deadline){waitAvailableConnection(s,deadline,next);return;}
            if(s.loading){s.close();s.loading=false;s.connected=false;s.updateStatus("Connection timed out");}
            next.run();
        },1000);
    }
    private void scanAvailableBle() {
        if(!connectingAvailable)return;
        int run=availableRun;
        if(scanningSession!=null)stopScanning(scanningSession,"Scan stopped");
        BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        BluetoothLeScanner scan=adapter==null?null:adapter.getBluetoothLeScanner();
        if(scan==null){availableDiscoveryError="Bluetooth scanner unavailable";finishAvailableConnections();return;}
        java.util.Set<String> addresses=new java.util.HashSet<>();
        ScanCallback callback=new ScanCallback(){
            @Override public void onScanResult(int type,ScanResult result){main.post(()->addresses.add(result.getDevice().getAddress()));}
            @Override public void onScanFailed(int error){main.post(()->{if(run==availableRun)availableDiscoveryError="Bluetooth discovery failed: "+error;});}
        };
        try{scan.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback);}
        catch(SecurityException|IllegalStateException error){availableDiscoveryError="Bluetooth discovery could not start";finishAvailableConnections();return;}
        nodeActionResult("Searching for known Bluetooth radios for 8 seconds…");
        main.postDelayed(()->{
            try{scan.stopScan(callback);}catch(SecurityException|IllegalStateException ignored){}
            if(closing||!connectingAvailable||run!=availableRun)return;
            for(ChatStore.KnownRadio radio:chatStore.radios())if(addresses.contains(radio.address)) {
                availableBle.add(radio);expectedAvailableBle.put(radio.radio+":"+radio.id,radio);
            }
            connectNextAvailableBle();
        },8000);
    }
    private void connectNextAvailableBle() {
        if(!connectingAvailable||closing)return;
        while(!availableBle.isEmpty()) {
            ChatStore.KnownRadio item=availableBle.removeFirst();RadioSession connected=null;
            for(RadioSession s:sessions.values())if((s.connected||s.loading)&&s.matches(item.radio,item.id))connected=s;
            if(connected!=null){if(connected.loading){waitAvailableConnection(connected,SystemClock.elapsedRealtime()+70000,this::connectNextAvailableBle);return;}if(connected.syncSucceeded)continue;disconnect(connected);}
            connectKnownReady(item.radio,item.id);
            for(RadioSession s:sessions.values())if(s.matches(item.radio,item.id)){waitAvailableConnection(s,SystemClock.elapsedRealtime()+70000,this::connectNextAvailableBle);return;}
        }
        finishAvailableConnections();
    }

    private boolean availableUsbConnected(String name) {
        for(RadioSession s:sessions.values())if(s.connected&&!s.loading&&s.syncSucceeded&&name.equals(s.usbDeviceName))return true;
        return false;
    }
    private boolean availableBleConnected(ChatStore.KnownRadio radio) {
        for(RadioSession s:sessions.values())if(s.connected&&!s.loading&&s.syncSucceeded&&s.matches(radio.radio,radio.id))return true;
        return false;
    }
    private void finishAvailableConnections() {
        connectingAvailable=false;
        List<UsbSerialDriver> failedUsb=new ArrayList<>();List<ChatStore.KnownRadio> failedBle=new ArrayList<>();
        StringBuilder failed=new StringBuilder();
        for(Map.Entry<String,UsbSerialDriver> item:expectedAvailableUsb.entrySet())if(!availableUsbConnected(item.getKey())) {
            failedUsb.add(item.getValue());String name=item.getKey(),reason="Not connected";
            for(RadioSession s:sessions.values())if(item.getKey().equals(s.usbDeviceName)){if(!s.ownName.isEmpty())name=s.ownName;reason=s.state;}
            failed.append("• USB · ").append(name).append(" — ").append(reason).append('\n');
        }
        for(ChatStore.KnownRadio item:expectedAvailableBle.values())if(!availableBleConnected(item)) {
            failedBle.add(item);String reason="Not connected";
            for(RadioSession s:sessions.values())if(s.matches(item.radio,item.id))reason=s.state;
            failed.append("• ").append(item.name.isEmpty()?item.id:item.name).append(" — ").append(reason).append('\n');
        }
        publish();if(closing)return;
        if(failed.length()==0&&availableDiscoveryError.isEmpty()) {
            nodeActionResult(expectedAvailableUsb.isEmpty()&&expectedAvailableBle.isEmpty()?"No available USB or known Bluetooth radios found.":"All discovered available radios are connected.");return;
        }
        boolean discoveryFailed=!availableDiscoveryError.isEmpty();
        if(discoveryFailed)failed.append('\n').append(availableDiscoveryError).append(". Bluetooth availability could not be fully checked.");
        int run=availableRun;
        new AlertDialog.Builder(this).setTitle("Some devices could not connect")
            .setMessage(failed.toString()+"\nRetry the failed connections? Connected devices will stay connected.")
            .setNegativeButton("Cancel",null).setPositiveButton("Retry",(d,w)->{
                if(closing||connectingAvailable||run!=availableRun)return;
                if(discoveryFailed){connectAllAvailable();return;}
                connectingAvailable=true;availableRun++;retryingAvailable=true;
                availableUsb.clear();availableBle.clear();availableUsb.addAll(failedUsb);availableBle.addAll(failedBle);
                publish();connectNextAvailableUsb();
            }).show();
    }

    private void connectBle(RadioSession session) {
        queueBle("scan:"+session.key,true,()->{
            if(scanningSession==session&&session.scanning)return;
            if(scanningSession!=null)stopScanning(scanningSession,"Scan stopped");
            scanningSession=session;scanBle(session);
        });
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==40){
            if(grants.length==0 || grants[0]!=PackageManager.PERMISSION_GRANTED)
                getPreferences(0).edit().putBoolean("background_message_notifications",false).apply();
            notificationText="";publish();return;
        }
        if(requestCode==BLE_PERMISSIONS) {
            blePermissionPending=false;
            boolean allowed=grants.length>0;for(int grant:grants)allowed&=grant==PackageManager.PERMISSION_GRANTED;
            if(allowed)continueBleRequests();
            else rejectBleRequests("Bluetooth permission is needed. Allow Nearby devices in Android settings, then connect again.");
        }
    }

    private void scanBle(RadioSession session) {
        BluetoothManager manager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            scanningSession = null; session.updateStatus("Enable Bluetooth first"); return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { scanningSession = null; session.updateStatus("BLE scanner unavailable"); return; }
        discovered.clear();
        session.scanReturnState = session.state;
        session.scanning = true;
        session.scanProgress = 0;
        int token = ++scanToken;
        try {
            scanner.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
        } catch (SecurityException | IllegalStateException error) {
            scanner = null; scanningSession = null; session.scanning = false;
            session.updateStatus("BLE scan could not start"); return;
        }
        session.updateStatus("Scanning for 8 seconds…");
        long started = SystemClock.elapsedRealtime();
        Runnable progress = new Runnable() {
            @Override public void run() {
                if (scanToken != token || scanningSession != session || !session.scanning) return;
                long elapsed = SystemClock.elapsedRealtime() - started;
                session.scanProgress = (int)Math.min(100, elapsed * 100 / 8000);
                publishScanProgress(session);
                if (elapsed >= 8000) finishScanning(session);
                else main.postDelayed(this, 250);
            }
        };
        main.post(progress);
    }

    private void stopScanning(RadioSession session, String reason) {
        if (scanningSession != session || !session.scanning) return;
        scanToken++;
        if (scanner != null) { try{scanner.stopScan(scanCallback);}catch(SecurityException|IllegalStateException ignored){} scanner = null; }
        scanningSession = null; session.scanning = false; session.scanProgress = 0;
        session.updateStatus(session.connected ? session.scanReturnState : reason);
        publish();
    }

    private void finishScanning(RadioSession session) {
        if (scanningSession != session || !session.scanning) return;
        scanToken++;
        if (scanner != null) { try{scanner.stopScan(scanCallback);}catch(SecurityException|IllegalStateException ignored){} scanner = null; }
        scanningSession = null; session.scanning = false; session.scanProgress = 100;
        publish();
        if (discovered.isEmpty()) { session.updateStatus("No " + session.name + " BLE radios identified"); return; }
        BluetoothDevice[] devices = discovered.values().toArray(new BluetoothDevice[0]);
        String[] names = discovered.keySet().toArray(new String[0]);
        String returnState = session.scanReturnState;
        session.updateStatus("Choose a radio");
        new AlertDialog.Builder(this).setTitle("Choose " + session.name + " BLE radio")
                .setItems(names, (dialog, which) -> connectDevice(session,devices[which]))
                .setOnCancelListener(dialog -> session.updateStatus(returnState))
                .show();
    }

    private void connectDevice(RadioSession session,BluetoothDevice device) {
        queueBle("device:"+session.key,false,()->connectDeviceReady(session,device));
    }
    private void connectDeviceReady(RadioSession session,BluetoothDevice device) {
        if(!bluetoothEnabled()){queueBle("device:"+session.key,false,()->connectDeviceReady(session,device));return;}
        session.start();
        session.bleAddress=device.getAddress();
        final int generation=session.generation;
        session.ble=new BleRadio(this,session.meshcore,new BleRadio.Listener() {
            @Override public void detected(boolean meshcore) { main.post(()->{if(session.generation==generation)session.detected(meshcore);}); }
            @Override public void ready() { main.post(()->{if(session.generation==generation) session.ready();}); }
            @Override public void received(byte[] bytes) { main.post(()->{if(session.generation==generation) session.receive(bytes);}); }
            @Override public void written(byte[] bytes,boolean success,String reason) { main.post(()->{if(session.generation==generation) session.written(bytes,success,reason);}); }
            @Override public void status(String text) { main.post(()->{if(session.generation==generation) session.updateStatus(text);}); }
        });
        try{session.ble.connect(device);}catch(SecurityException|IllegalStateException error){session.close();session.loading=false;session.updateStatus("BLE connection could not start. Check Bluetooth and Nearby devices permission.");}
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            main.post(() -> {
                if (scanningSession == null || !scanningSession.scanning) return;
                if (!matchesScanFamily(result,scanningSession)) return;
                BluetoothDevice device = result.getDevice();
                String name = result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
                discovered.put((name == null ? "Radio" : name) + "  " + device.getAddress(), device);
            });
        }
        @Override public void onScanFailed(int code) {
            main.post(() -> { if (scanningSession != null) stopScanning(scanningSession, "BLE scan failed: " + code); });
        }
    };

    private boolean matchesScanFamily(ScanResult result,RadioSession session) {
        ScanRecord record=result.getScanRecord();
        if(record==null) return false;
        List<ParcelUuid> services=record.getServiceUuids();
        if(services!=null && !services.isEmpty()) {
            boolean wanted=false, opposite=false;
            for(ParcelUuid advertised:services) {
                if(advertised.getUuid().equals(session.meshcore?BleRadio.MC_SERVICE:BleRadio.MT_SERVICE)) wanted=true;
                if(advertised.getUuid().equals(session.meshcore?BleRadio.MT_SERVICE:BleRadio.MC_SERVICE)) opposite=true;
            }
            if(opposite) return false;
            if(wanted) return true;
        }
        String name=record.getDeviceName();
        if(name==null) return false;
        String lower=name.toLowerCase(java.util.Locale.ROOT);
        if(session.meshcore) return lower.startsWith("meshcore") || lower.startsWith("meshcore-");
        return lower.startsWith("meshtastic") || lower.startsWith("meshtastic_");
    }

    private LinearLayout vertical() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout horizontal() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(sp); view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }
    private GradientDrawable rounded(int color) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(14)); return shape;
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        MessageNotifications.cancel(this);
        debug("Lifecycle closing");closing=true;pendingBleActions.clear();
        if(RadioConnectionService.owner.get()==this)RadioConnectionService.owner.clear();
        stopService(new Intent(this,RadioConnectionService.class));
        getSystemService(android.app.NotificationManager.class).cancel(RadioConnectionService.NOTIFICATION_ID);
        getSystemService(android.app.NotificationManager.class).cancel(201);
        scanToken++;
        if (scanner != null)try{scanner.stopScan(scanCallback);}catch(SecurityException|IllegalStateException ignored){}
        for(RadioSession s:sessions.values()) {
            s.markPendingUnconfirmed(); s.generation++; s.close();
        }
        unregisterReceiver(usbPermissionReceiver);
        unregisterReceiver(bluetoothStateReceiver);
        pageReady = false;
        if (web != null) { web.removeJavascriptInterface("AndroidBridge"); web.destroy(); web = null; }
        historyWorker.execute(()->chatStore.close());historyWorker.shutdown();
        super.onDestroy();
    }
}
