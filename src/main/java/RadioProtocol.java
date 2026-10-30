package org.overmesh.android;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

interface RadioProtocol {
    interface Events {
        void ownNode(String id, String name);
        void syncComplete();
        default void syncFailed(String reason) {status(reason);}
        void status(String text);
        void message(int channel, String sender, String text, long receivedAt, int hops);
        void directMessage(String id, String text, long receivedAt, int hops);
        void node(String id, String name, String role, String model, int channel, int hops, double latitude, double longitude);
        void position(String id, double latitude, double longitude);
        void ownPosition(double latitude, double longitude);
        void actionResult(String text);
        void channel(int index, String name);
        void sendResult(boolean accepted, String detail);
        default void delivery(long requestId, String state) {}
        default void trace(TraceResult result) {}
        default void signalQuality(long requestId,String target,String result) {}
        default void signalSample(String target,long seconds,Float snr,Integer rssi,int hops,String stats,boolean requested,boolean mqtt) {}
        default void advertExport(String uri) {}
        default void directPacket(String id,String text,long time,int hops,long packetId,long replyId) {directMessage(id,text,time,hops);}
        default void reaction(int channel,String sender,String peer,String text,long time,long packetId,long replyId) {}
        default void lastHeard(String id,long seconds) {}
        default void channelMessage(int channel,String sender,String text,long receivedAt,int hops,long packetId,long replyId) { message(channel,sender,text,receivedAt,hops); }
        default void advertResult(String text,boolean success) { actionResult(text); }
        default void heardRepeat(long requestId) {}
        default void heardPath(long requestId,String[] path,int snr,int rssi) {}
        default void channelPath(int channel,long timestamp,String text,String[] path,int snr,int rssi) {}

    }
    default long lastRequestId() { return 0; }
    default boolean requestPending(long id) { return false; }
    default void expireRequest(long id) {}
    default boolean automaticDeliveryPending(long id) { return false; }
    default byte[] poll(Events events) { return null; }
    default void cancelAll() {}
    default boolean tracksRepeats(long id) {return false;}
    default int textLimit(boolean direct) { return 200; }
    default byte[] advert(String action) { throw new IllegalArgumentException("Choose a MeshCore device"); }
    byte[] initialCommand();
    default byte[] sendReaction(String peer,int channel,String number,long replyId) {throw new IllegalArgumentException("Reactions require Meshtastic");}
    byte[] sendChannel(int channel, String text);
    default byte[] sendChannelReply(int channel,String text,long replyId) {return sendChannel(channel,text);}
    default byte[] nodeCommand(String action, String id, String text, int channel) { throw new IllegalArgumentException("Action is not supported by this radio"); }
    void receive(byte[] packet, Events events);
    default byte[] nextCommand() { return null; }
}

final class MeshtasticProtocol implements RadioProtocol {
    private final SecureRandom random = new SecureRandom();
    private long myNode, lastId;
    private final java.util.Map<Long,Long> messageTargets=new java.util.LinkedHashMap<>();
    private final java.util.Map<Long,String> traceTargets=new java.util.HashMap<>();
    private final java.util.Map<Long,String> signalTargets=new java.util.HashMap<>();
    @Override public long lastRequestId() { return lastId; }
    @Override public void expireRequest(long id) { traceTargets.remove(id);signalTargets.remove(id); }
    @Override public boolean requestPending(long id) {return traceTargets.containsKey(id)||signalTargets.containsKey(id);}
    private long newId() { long id; do {id=random.nextInt()&0xffffffffL;} while(id==0||messageTargets.containsKey(id)||traceTargets.containsKey(id)||signalTargets.containsKey(id)); return lastId=id; }
    private void rememberMessage(long id,long target) {
        if(messageTargets.size()>=256) messageTargets.remove(messageTargets.keySet().iterator().next());
        messageTargets.put(id,target);
    }

    private final java.util.Map<String,String> longNames = new java.util.HashMap<>();

    private String reportedName(String id, String longName, String shortName) {
        if (!longName.trim().isEmpty()) longNames.put(id, longName);
        return longNames.getOrDefault(id, shortName);
    }

    @Override public byte[] initialCommand() {
        int nonce = random.nextInt(Integer.MAX_VALUE - 1) + 1;
        return new ProtoWire.Writer().varint(3, nonce).build();
    }

    @Override public byte[] sendChannel(int channel, String text) {return sendChannelReply(channel,text,0);}
    @Override public byte[] sendChannelReply(int channel,String text,long replyId) {
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        if (utf8.length == 0 || utf8.length > 200) throw new IllegalArgumentException("Meshtastic text must be 1–200 UTF-8 bytes");
        ProtoWire.Writer dataWriter=new ProtoWire.Writer().varint(1,1).bytes(2,utf8);
        if(replyId!=0) dataWriter.fixed32(7,replyId);
        byte[] data=dataWriter.build();
        long id = newId();
        rememberMessage(id,0xffffffffL);
        byte[] packet = new ProtoWire.Writer()
                .fixed32(2, 0xffffffffL).varint(3, channel).bytes(4, data)
                .fixed32(6, id).varint(9, 3).varint(10,1).build();
        return new ProtoWire.Writer().bytes(1, packet).build();
    }

    @Override public byte[] sendReaction(String peer,int channel,String number,long replyId) {
        if(!(number.matches("[0-7]")||number.equals("👍"))||replyId<=0||replyId>0xffffffffL) throw new IllegalArgumentException("Choose thumbs up or 0 to 7 on a message with a packet reference");
        if(!peer.isEmpty()&&!peer.matches("![0-9a-fA-F]{8}")) throw new IllegalArgumentException("Invalid reaction recipient");
        long destination=peer.isEmpty()?0xffffffffL:Long.parseUnsignedLong(peer.substring(1),16);
        byte[] data=new ProtoWire.Writer().varint(1,1).bytes(2,number.getBytes(StandardCharsets.UTF_8)).fixed32(7,replyId).fixed32(8,1).build();
        long id=newId();rememberMessage(id,destination);
        byte[] packet=new ProtoWire.Writer().fixed32(2,destination).varint(3,channel).bytes(4,data).fixed32(6,id).varint(9,3).varint(10,1).build();
        return new ProtoWire.Writer().bytes(1,packet).build();
    }
    @Override public byte[] nodeCommand(String action, String id, String text, int channel) {
        if (!id.matches("![0-9a-fA-F]{8}")) throw new IllegalArgumentException("Invalid Meshtastic node ID");
        long destination = Long.parseUnsignedLong(id.substring(1), 16);
        int port;
        byte[] body;
        boolean response = false;
        switch (action) {
            case "dm":
                body = text.trim().getBytes(StandardCharsets.UTF_8);
                if (body.length == 0 || body.length > 200) throw new IllegalArgumentException("Message must be 1–200 UTF-8 bytes");
                port = 1; break;
            case "tr": port = 70; body = new byte[0]; response = true; break;
            case "req": port = 4; body = new byte[0]; response = true; break;
            case "pos": port = 3; body = new byte[0]; response = true; break;
            case "signal": port=67;body=new ProtoWire.Writer().bytes(6,new byte[0]).build();response=true;break;
            default: throw new IllegalArgumentException("Unknown node action");
        }
        ProtoWire.Writer dataWriter = new ProtoWire.Writer().varint(1, port).bytes(2, body)
                .varint(3, response ? 1 : 0);
        if ("tr".equals(action)) dataWriter.fixed32(4, destination);
        byte[] data = dataWriter.build();
        long requestId=newId();
        if("dm".equals(action)) rememberMessage(requestId,destination);
        if("tr".equals(action)) traceTargets.put(requestId,id);
        if("signal".equals(action)) signalTargets.put(requestId,id);
        byte[] packet = new ProtoWire.Writer().fixed32(2, destination)
                .varint(3, channel).bytes(4, data).fixed32(6, requestId)
                .varint(9, 3).varint(10, 1).build();
        return new ProtoWire.Writer().bytes(1, packet).build();
    }

    @Override public void receive(byte[] packet, Events events) {
        try {
            ProtoWire.Reader root = new ProtoWire.Reader(packet);
            while (root.next()) {
                if (root.field == 2) readPacket(root.bytes(), events);
                else if (root.field == 3) readMyInfo(root.bytes(), events);
                else if (root.field == 4) readNode(root.bytes(), events);
                else if (root.field == 10) readChannel(root.bytes(), events);
                else if (root.field == 11) {
                    ProtoWire.Reader queue=new ProtoWire.Reader(root.bytes()); long id=0; int result=0;
                    while(queue.next()) { if(queue.field==1) result=(int)queue.number(); else if(queue.field==4) id=queue.number(); else queue.skip(); }
                    if(messageTargets.containsKey(id)) events.delivery(id,result==0?"accepted":"failed: Radio queue rejected the message (code "+result+")");
                }
                else if (root.field == 7) { root.number(); events.status("Meshtastic configuration loaded"); events.syncComplete(); }
                else root.skip();
            }
        } catch (IllegalArgumentException error) { events.status("Ignored malformed Meshtastic frame: " + error.getMessage()); }
    }

    private void readMyInfo(byte[] bytes, Events events) {
        ProtoWire.Reader r = new ProtoWire.Reader(bytes);
        while (r.next()) { if (r.field == 1) myNode = r.number(); else r.skip(); }
        if (myNode != 0) events.ownNode("!" + String.format("%08x", myNode), "Meshtastic !" + String.format("%08x", myNode));
    }

    private void readNode(byte[] bytes, Events events) {
        ProtoWire.Reader r = new ProtoWire.Reader(bytes); long num = 0, lastHeard=0; String name = ""; String role = "";
        String model = "", shortName = ""; int channel = 0, hops = -1; double lat = Double.NaN, lon = Double.NaN;
        while (r.next()) {
            if (r.field == 1) num = r.number();
            else if (r.field == 2) {
                role = "Client"; // proto3 omits the default enum value (CLIENT = 0).
                ProtoWire.Reader user = new ProtoWire.Reader(r.bytes());
                while (user.next()) {
                    if (user.field == 2) name = new String(user.bytes(), StandardCharsets.UTF_8);
                    else if (user.field == 3) shortName = new String(user.bytes(), StandardCharsets.UTF_8);
                    else if (user.field == 5) model = hardwareModel((int)user.number());
                    else if (user.field == 7) role = roleName((int)user.number());
                    else user.skip();
                }
            } else if (r.field == 3) {
                double[] position = readPosition(r.bytes()); lat = position[0]; lon = position[1];
            } else if (r.field == 5) lastHeard=r.number()&0xffffffffL;
            else if (r.field == 7) channel = (int)r.number();
            else if (r.field == 9) hops = (int)r.number();
            else r.skip();
        }
        String id = "!" + String.format("%08x", num);
        name = reportedName(id, name, shortName);
        events.node(id, name, role, model, channel, hops, lat, lon);
        events.lastHeard(id,lastHeard);
        if (num == myNode && !name.isEmpty()) events.ownNode(id, name);
        if (num == myNode && Double.isFinite(lat) && Double.isFinite(lon)) events.ownPosition(lat, lon);
    }

    private double[] readPosition(byte[] bytes) {
        double lat = Double.NaN, lon = Double.NaN;
        ProtoWire.Reader r = new ProtoWire.Reader(bytes);
        while (r.next()) {
            if (r.field == 1) lat = (int)r.number() / 1e7;
            else if (r.field == 2) lon = (int)r.number() / 1e7;
            else r.skip();
        }
        if (lat == 0 && lon == 0) return new double[]{Double.NaN, Double.NaN};
        return new double[]{lat, lon};
    }

    private String hardwareModel(int model) {
        // Wire IDs from Meshtastic HardwareModel; display names, not generated protobuf code.
        switch (model) {
            case 0: return "";
            case 1: return "TLORA V2";
            case 2: return "TLORA V1";
            case 3: return "TLORA V2 1 1P6";
            case 4: return "T-Beam";
            case 5: return "HELTEC V2 0";
            case 6: return "TBEAM V0P7";
            case 7: return "T-Echo";
            case 8: return "TLORA V1 1P3";
            case 9: return "RAK4631";
            case 10: return "HELTEC V2 1";
            case 11: return "HELTEC V1";
            case 12: return "LILYGO TBEAM S3 CORE";
            case 13: return "RAK11200";
            case 14: return "NANO G1";
            case 15: return "TLORA V2 1 1P8";
            case 16: return "TLORA T3 S3";
            case 17: return "NANO G1 EXPLORER";
            case 18: return "NANO G2 ULTRA";
            case 19: return "LORA TYPE";
            case 20: return "WIPHONE";
            case 21: return "WIO WM1110";
            case 22: return "RAK2560";
            case 23: return "HELTEC HRU 3601";
            case 24: return "HELTEC WIRELESS BRIDGE";
            case 25: return "STATION G1";
            case 26: return "RAK11310";
            case 27: return "MAKERFABS TRACKER";
            case 28: return "MAKERFABS RESERVED";
            case 29: return "CANARYONE";
            case 30: return "RP2040 LORA";
            case 31: return "STATION G2";
            case 32: return "LORA RELAY V1";
            case 33: return "T ECHO PLUS";
            case 34: return "PPR";
            case 35: return "GENIEBLOCKS";
            case 36: return "NRF52 UNKNOWN";
            case 37: return "PORTDUINO";
            case 38: return "ANDROID SIM";
            case 39: return "DIY V1";
            case 40: return "NRF52840 PCA10059";
            case 41: return "DR DEV";
            case 42: return "M5STACK";
            case 43: return "Heltec V3";
            case 44: return "HELTEC WSL V3";
            case 45: return "BETAFPV 2400 TX";
            case 46: return "BETAFPV 900 NANO TX";
            case 47: return "RPI PICO";
            case 48: return "HELTEC WIRELESS TRACKER";
            case 49: return "HELTEC WIRELESS PAPER";
            case 50: return "T DECK";
            case 51: return "T WATCH S3";
            case 52: return "PICOMPUTER S3";
            case 53: return "HELTEC HT62";
            case 54: return "EBYTE ESP32 S3";
            case 55: return "ESP32 S3 PICO";
            case 56: return "CHATTER 2";
            case 57: return "HELTEC WIRELESS PAPER V1 0";
            case 58: return "HELTEC WIRELESS TRACKER V1 0";
            case 59: return "UNPHONE";
            case 60: return "TD LORAC";
            case 61: return "CDEBYTE EORA S3";
            case 62: return "TWC MESH V4";
            case 63: return "NRF52 PROMICRO DIY";
            case 64: return "RADIOMASTER 900 BANDIT NANO";
            case 65: return "HELTEC CAPSULE SENSOR V3";
            case 66: return "HELTEC VISION MASTER T190";
            case 67: return "HELTEC VISION MASTER E213";
            case 68: return "HELTEC VISION MASTER E290";
            case 69: return "HELTEC MESH NODE T114";
            case 70: return "SENSECAP INDICATOR";
            case 71: return "TRACKER T1000 E";
            case 72: return "RAK3172";
            case 73: return "WIO E5";
            case 74: return "RADIOMASTER 900 BANDIT";
            case 75: return "ME25LS01 4Y10TD";
            case 76: return "RP2040 FEATHER RFM95";
            case 77: return "M5STACK COREBASIC";
            case 78: return "M5STACK CORE2";
            case 79: return "RPI PICO2";
            case 80: return "M5STACK CORES3";
            case 81: return "SEEED XIAO S3";
            case 82: return "MS24SF1";
            case 83: return "TLORA C6";
            case 84: return "WISMESH TAP";
            case 85: return "ROUTASTIC";
            case 86: return "MESH TAB";
            case 87: return "MESHLINK";
            case 88: return "XIAO NRF52 KIT";
            case 89: return "THINKNODE M1";
            case 90: return "THINKNODE M2";
            case 91: return "T ETH ELITE";
            case 92: return "HELTEC SENSOR HUB";
            case 93: return "MUZI BASE";
            case 94: return "HELTEC MESH POCKET";
            case 95: return "SEEED SOLAR NODE";
            case 96: return "NOMADSTAR METEOR PRO";
            case 97: return "CROWPANEL";
            case 98: return "LINK 32";
            case 99: return "SEEED WIO TRACKER L1";
            case 100: return "SEEED WIO TRACKER L1 EINK";
            case 101: return "MUZI R1 NEO";
            case 102: return "T DECK PRO";
            case 103: return "T LORA PAGER";
            case 104: return "M5STACK RESERVED";
            case 105: return "WISMESH TAG";
            case 106: return "RAK3312";
            case 107: return "THINKNODE M5";
            case 108: return "HELTEC MESH SOLAR";
            case 109: return "T ECHO LITE";
            case 110: return "HELTEC V4";
            case 111: return "M5STACK C6L";
            case 112: return "M5STACK CARDPUTER ADV";
            case 113: return "HELTEC WIRELESS TRACKER V2";
            case 114: return "T WATCH ULTRA";
            case 115: return "THINKNODE M3";
            case 116: return "WISMESH TAP V2";
            case 117: return "RAK3401";
            case 118: return "RAK6421";
            case 119: return "THINKNODE M4";
            case 120: return "THINKNODE M6";
            case 121: return "MESHSTICK 1262";
            case 122: return "TBEAM 1 WATT";
            case 123: return "T5 S3 EPAPER PRO";
            case 124: return "TBEAM BPF";
            case 125: return "MINI EPAPER S3";
            case 126: return "TDISPLAY S3 PRO";
            case 127: return "HELTEC MESH NODE T096";
            case 128: return "MESH TRACKER X1";
            case 129: return "THINKNODE M7";
            case 130: return "THINKNODE M8";
            case 131: return "THINKNODE M9";
            case 132: return "HELTEC V4 R8";
            case 133: return "HELTEC MESH NODE T1";
            case 134: return "STATION G3";
            case 135: return "T IMPULSE PLUS";
            case 136: return "T ECHO CARD";
            case 137: return "SEEED WIO TRACKER L2";
            case 138: return "CROWPANEL P4";
            case 139: return "HELTEC MESH TOWER V2";
            case 140: return "MESHNOLOGY W10";
            case 141: return "HELTEC RC32";
            case 142: return "HELTEC RC52";
            case 143: return "HELTEC RCC6";
            case 144: return "SEEED WIO TRACKER L1 PRO 1W";
            case 145: return "MESHNOLOGY W12";
            case 146: return "MESHPAGER X2";
            case 147: return "T CONNECT PRO";
            case 148: return "AXIOMETA GENESIS MINI";
            case 149: return "MAKERFABS NOMAD TERMINAL";
            case 150: return "THINKNODE MX";
            case 255: return "PRIVATE HW";
            default: return "Unrecognized hardware (update app)";
        }
    }

    private String roleName(int role) {
        switch (role) {
            case 0: return "Client";
            case 1: return "Client Mute";
            case 2: return "Router";
            case 3: return "Router Client (deprecated)";
            case 4: return "Repeater (deprecated)";
            case 5: return "Tracker";
            case 6: return "Sensor";
            case 7: return "TAK";
            case 8: return "Client Hidden";
            case 9: return "Lost and Found";
            case 10: return "TAK Tracker";
            case 11: return "Router Late";
            case 12: return "Client Base";
            default: return "Role " + role;
        }
    }

    private void readPacket(byte[] bytes, Events events) {
        Float signalSnr=null;Integer signalRssi=null;boolean mqtt=false;
        ProtoWire.Reader r = new ProtoWire.Reader(bytes); long from = 0, to = 0, receivedAt = 0, packetId=0; int channel = 0, hopStart=-1, hopLimit=-1; byte[] decoded = null;
        while (r.next()) {
            if (r.field == 1) from = r.number();
            else if (r.field == 2) to = r.number();
            else if (r.field == 3) channel = (int)r.number();
            else if (r.field == 4) decoded = r.bytes();
            else if (r.field == 6) packetId=r.number()&0xffffffffL;
            else if (r.field == 7) receivedAt = r.number() & 0xffffffffL;
            else if (r.field == 8) signalSnr=Float.intBitsToFloat((int)r.number());
            else if (r.field == 12) signalRssi=(int)r.number();
            else if (r.field == 9) hopLimit=(int)r.number();
            else if (r.field == 15) hopStart=(int)r.number();
            else if (r.field == 14) mqtt=r.number()!=0;
            else r.skip();
        }
        ProtoWire.Reader data = new ProtoWire.Reader(decoded==null?new byte[0]:decoded); int port = 0; long requestId=0, replyId=0, emoji=0; boolean wantResponse=false,hasBitfield=false; byte[] body = null;
        while (data.next()) {
            if (data.field == 1) port = (int)data.number();
            else if (data.field == 2) body = data.bytes();
            else if (data.field == 3) wantResponse=data.number()!=0;
            else if (data.field == 6) requestId=data.number();
            else if (data.field == 7) replyId=data.number()&0xffffffffL;
            else if (data.field == 8) emoji=data.number();
            else if (data.field == 9) {hasBitfield=true;data.number();}
            else data.skip();
        }
        String sender = "!" + String.format("%08x", from);
        if(from!=myNode) events.lastHeard(sender,receivedAt);
        int hops=hopStart>=0 && hopLimit>=0 && hopStart>=hopLimit ? hopStart-hopLimit : -1;
        // Old firmware omits hop_start: zero without a modern Data bitfield is not proof of direct RF.
        int signalStart=hopStart<0&&hasBitfield?0:hopStart;
        int signalLimit=hopLimit<0?0:hopLimit;
        int signalHops=(signalStart>0||hasBitfield)&&signalStart>=signalLimit?signalStart-signalLimit:-1;
        if(signalSnr!=null&&!Float.isFinite(signalSnr))signalSnr=null;
        String statsText="";boolean requested=false;
        if(port==67&&body!=null&&!wantResponse) {
            ProtoWire.Reader telemetry=new ProtoWire.Reader(body);byte[] stats=null;
            while(telemetry.next()) {
                if(telemetry.field==6)stats=telemetry.bytes();
                else telemetry.skip();
            }
            if(stats!=null) {
                statsText=SignalQuality.statistics(stats);
                requested=sender.equals(signalTargets.get(requestId));
            }
        }
        if(from!=0&&((from!=myNode&&!mqtt&&(signalSnr!=null||signalRssi!=null))||!statsText.isEmpty()))
            events.signalSample(sender,receivedAt,from==myNode||mqtt?null:signalSnr,
                from==myNode||mqtt?null:signalRssi,signalHops,statsText,requested,mqtt);
        if(requested) {
            signalTargets.remove(requestId);events.signalQuality(requestId,sender,"Node statistics received and saved.");
        }
        if (port == 1 && body != null) {
            if (from == myNode) return; // Already recorded as an outgoing message; an echo is not unread.
            String text = new String(body, StandardCharsets.UTF_8);
            if(emoji!=0) {
                if(replyId!=0&&(to==myNode||to==0xffffffffL)) events.reaction(channel,sender,to==myNode?sender:"",text,receivedAt,packetId,replyId);
                return;
            }
            if (to == myNode && from != myNode) events.directPacket(sender,text,receivedAt,hops,packetId,replyId);
            else events.channelMessage(channel,sender,text,receivedAt,hops,packetId,replyId);
        }
        else if (port == 3 && body != null) {
            double[] position = readPosition(body);
            if (Double.isFinite(position[0]) && Double.isFinite(position[1])) events.position(sender, position[0], position[1]);
        } else if (port == 4 && body != null) {
            ProtoWire.Reader user = new ProtoWire.Reader(body);
            String name = "", shortName = "", role = "Client", model = "";
            while (user.next()) {
                if (user.field == 2) name = new String(user.bytes(), StandardCharsets.UTF_8);
                else if (user.field == 3) shortName = new String(user.bytes(), StandardCharsets.UTF_8);
                else if (user.field == 5) model = hardwareModel((int)user.number());
                else if (user.field == 7) role = roleName((int)user.number());
                else user.skip();
            }
            events.node(sender, reportedName(sender, name, shortName), role, model, channel, -1, Double.NaN, Double.NaN);
        } else if (port == 5 && body != null && requestId!=0) {
            ProtoWire.Reader routing=new ProtoWire.Reader(body); int error=0;
            while(routing.next()) { if(routing.field==3) error=(int)routing.number(); else routing.skip(); }
            if(error!=0&&signalTargets.containsKey(requestId))events.signalQuality(requestId,signalTargets.remove(requestId),routingError(error));
            Long target=messageTargets.get(requestId);
            if(target!=null) events.delivery(requestId,error!=0?"failed: "+routingError(error):
                    target==0xffffffffL?"delivered_mesh":from==target?"delivered":"relayed");
            if(error!=0 && traceTargets.containsKey(requestId)) {
                String traceTarget=traceTargets.remove(requestId);
                events.trace(new TraceResult(requestId,traceTarget,new java.util.ArrayList<>(),new java.util.ArrayList<>(),
                        new java.util.ArrayList<>(),new java.util.ArrayList<>(),routingError(error)));
            }
        } else if (port == 70 && body != null && !wantResponse && traceTargets.containsKey(requestId)
                && sender.equals(traceTargets.get(requestId))) {
            ProtoWire.Reader route = new ProtoWire.Reader(body);
            java.util.List<String> forward=new java.util.ArrayList<>(),back=new java.util.ArrayList<>();
            java.util.List<Integer> snr=new java.util.ArrayList<>(),backSnr=new java.util.ArrayList<>();
            forward.add("!"+String.format("%08x",myNode));
            while(route.next()) {
                if(route.field==1 || route.field==3) {
                    java.util.List<String> ids=route.field==1?forward:back;
                    for(long n:TraceResult.repeated(route,true)) ids.add("!"+String.format("%08x",n));
                } else if(route.field==2 || route.field==4) {
                    java.util.List<Integer> values=route.field==2?snr:backSnr;
                    for(long n:TraceResult.repeated(route,false)) values.add((int)n);
                } else route.skip();
            }
            forward.add(sender);
            boolean hasBack=!back.isEmpty()||!backSnr.isEmpty();
            if(hasBack) { back.add(0,sender); back.add("!"+String.format("%08x",myNode)); }
            traceTargets.remove(requestId);
            events.trace(new TraceResult(requestId,sender,forward,back,snr,backSnr,
                    hasBack?"":"Return route not reported by this firmware."));
        }
    }
    private String routingError(int code) {
        switch(code) {
            case 1:return "No route. The recipient may be offline or out of range.";
            case 2:return "A relay refused the message (NAK). Wait and retry.";
            case 3:return "Timed out waiting for acknowledgment. Check range and retry.";
            case 4:return "No radio interface is available. Reconnect the radio.";
            case 5:return "All radio retries exhausted. Improve signal or retry later.";
            case 6:return "No matching channel. Check the channel and encryption key.";
            case 7:return "Message too large. Shorten the text.";
            case 8:return "The destination app did not respond.";
            case 9:return "Duty-cycle limit reached. Wait before retrying.";
            case 32:return "The destination rejected the request.";
            case 33:return "Not authorized on the destination channel.";
            case 34:return "Public-key encryption failed.";
            case 35:return "Recipient cannot decrypt: public key unknown.";
            case 38:return "Radio airtime rate limit reached. Wait before retrying.";
            case 39:return "Recipient public key missing. Request node information.";
            default:return "Radio delivery error "+code;
        }
    }

    private void readChannel(byte[] bytes, Events events) {
        ProtoWire.Reader r = new ProtoWire.Reader(bytes); int index = 0; int role = 0; String name = "";
        while (r.next()) {
            if (r.field == 1) index = (int)r.number();
            else if (r.field == 2) {
                ProtoWire.Reader settings = new ProtoWire.Reader(r.bytes());
                while (settings.next()) {
                    if (settings.field == 3) name = new String(settings.bytes(), StandardCharsets.UTF_8);
                    else settings.skip();
                }
            } else if (r.field == 3) role = (int)r.number();
            else r.skip();
        }
        if (role != 0) events.channel(index, name.isEmpty() ? "Default" : name);
    }
}

final class MeshCoreProtocol implements RadioProtocol {
    private final MeshCoreRepeatTracker repeats=new MeshCoreRepeatTracker();
    private final java.util.Map<Integer,byte[]> channelSecrets=new java.util.HashMap<>();
    private byte[] advertName;
    @Override public boolean tracksRepeats(long id) {return repeats.contains(id);}
    private int firmwareVersion;
    private int configuredDirectAttempts=4,configuredFloodAttempts=4;
    void setAttempts(int direct,int flood) {
        if(direct<1||direct>10||flood<1||flood>10)throw new IllegalArgumentException("Attempts must be between 1 and 10");
        configuredDirectAttempts=direct;configuredFloodAttempts=flood;
    }
    void stopFutureAttempts(long id) {DmSend job=dmSends.get(id);if(job!=null){job.total=job.attempt+1;job.stopRequested=true;}}
    boolean busyForSend(){return initializing||syncInFlight||syncStalled||!operation.isEmpty()||discoveryId!=0||!dmSends.isEmpty();}
    boolean splitCommandBusy(){return initializing||syncInFlight||syncStalled||!operation.isEmpty()||discoveryId!=0||dmSends.size()>=32;}
    String sendWaitReason() {
        if(syncStalled||operation.equals("dm-stalled"))return "Reconnect this radio: its previous operation timed out. Pending parts have not been sent.";
        if(initializing||syncInFlight)return "Waiting for MeshCore message loading to finish";
        if(!dmSends.isEmpty())return "Waiting for the active MeshCore DM retry/response cycle";
        return "Waiting for the previous MeshCore radio operation to finish";
    }
    long responseWaitMillis(long id){DmSend job=dmSends.get(id);return job==null?0:Math.max(0,(job.due==Long.MAX_VALUE?commandDeadline:job.due)-clock.getAsLong());}
    private final java.util.function.LongSupplier clock;
    private final java.util.function.IntSupplier jitter;
    MeshCoreProtocol() { this(() -> System.nanoTime()/1000000L, () -> new SecureRandom().nextInt(7001)+1000); }
    MeshCoreProtocol(java.util.function.LongSupplier clock, java.util.function.IntSupplier jitter) { this.clock=clock;this.jitter=jitter; }
    private static final class DmSend {
        final long id; final byte[] frame, key; final int directAttempts; int total;
        int attempt; long due=Long.MAX_VALUE; boolean resetDone, actualFlood,stopRequested;
        DmSend(long id,byte[] frame,byte[] key,int direct,int total) {
            this.id=id;this.frame=frame;this.key=key;this.directAttempts=direct;this.total=total;actualFlood=direct==0;
        }
        String status() { return "sending: Attempt "+(attempt+1)+"/"+total+(actualFlood?" · flood":" · known route")+" · awaiting recipient"; }
    }
    private final java.util.Map<Long,DmSend> dmSends=new java.util.LinkedHashMap<>();
    private final java.util.Set<String> knownRoutes=new java.util.HashSet<>();
    private long commandDeadline;
    @Override public boolean automaticDeliveryPending(long id) { return dmSends.containsKey(id); }
    @Override public void cancelAll() { dmSends.clear();acknowledgments.clear();repeats.clear();operation="";discoveryId=0;traceTag=0;tracePath=null; }
    private void stopDm(long id) { dmSends.remove(id); }
    @Override public byte[] poll(Events events) {
        long now=clock.getAsLong();
        if((initializing||syncInFlight)&&!syncStalled&&now>=loadDeadline) {
            initializing=false;syncInFlight=false;syncRequested=false;syncStalled=true;pending=null;
            events.syncFailed("MeshCore loading stalled: radio did not respond. Reconnect this radio.");
        }
        if(syncStalled)return null;
        if((operation.equals("dm")||operation.equals("dm-reset")) && now>=commandDeadline) {
            for(long id:new java.util.ArrayList<>(dmSends.keySet()))
                events.delivery(id,"unconfirmed: Radio did not respond; reconnect before sending again");
            dmSends.clear();
            // An untagged late response cannot safely be assigned to another command.
            operation="dm-stalled";
        }
        if(!operation.isEmpty()||discoveryId!=0) return null;
        for(DmSend job:new java.util.ArrayList<>(dmSends.values())) {
            if(now<job.due) continue;
            if(job.stopRequested&&job.resetDone){stopDm(job.id);events.delivery(job.id,"unconfirmed: Retries stopped before next transmission");continue;}
            if(!job.resetDone && job.due!=0) {
                job.attempt++;
                if(job.attempt>=job.total) {
                    stopDm(job.id);events.delivery(job.id,"unconfirmed: No recipient acknowledgment after "+job.total+" attempts");continue;
                }
            }
            pendingId=lastId=job.id;commandDeadline=now+15000;
            if(job.attempt>=job.directAttempts&&!job.resetDone) {
                operation="dm-reset";byte[] reset=new byte[33];reset[0]=13;
                System.arraycopy(job.key,0,reset,1,32);job.due=Long.MAX_VALUE;return reset;
            }
            operation="dm";job.resetDone=false;job.due=Long.MAX_VALUE;
            job.actualFlood=job.attempt>=job.directAttempts;
            byte[] frame=job.frame.clone();frame[2]=(byte)job.attempt;
            events.delivery(job.id,job.status());return frame;
        }
        return null;
    }
    private int channelTextLimit;
    @Override public int textLimit(boolean direct) { return direct ? 138 : channelTextLimit; }
    private boolean syncRequested;
    private boolean syncInFlight, channelsLoaded, initializing, syncStalled;
    private long loadDeadline;
    private byte[] pending;
    private int maxChannels = 1;
    private int channelQuery;
    private String operation="", traceTarget="", ownKey="";
    private long sequence=0, lastId=0, pendingId=0, traceTag=0;
    private long discoveryId=0;
    private String discoveryTarget="";
    private int traceTurn=0;
    private boolean tracePing=false;
    private long traceStarted=0;
    private byte[] tracePath;
    private final java.util.Map<Long,Long> acknowledgments=new java.util.LinkedHashMap<>();
    private final java.util.Map<String,Integer> contactTypes=new java.util.HashMap<>();
    @Override public long lastRequestId() { return lastId; }
    @Override public boolean requestPending(long id) { return pendingId==id&&!operation.isEmpty(); }
    private void begin(String kind) {
        if(syncStalled)throw new IllegalArgumentException("Reconnect this radio after its loading timeout");
        if(operation.equals("dm-stalled")) throw new IllegalArgumentException("Reconnect this radio after its command-response timeout");
        if(!operation.isEmpty()) throw new IllegalArgumentException("Wait for the previous MeshCore command to respond");
        if(discoveryId!=0) throw new IllegalArgumentException("Wait for the MeshCore path discovery to finish");
        operation=kind; pendingId=lastId=++sequence;
    }
    @Override public void expireRequest(long id) {
        stopDm(id);
        if(pendingId==id && !operation.equals("dm")&&!operation.equals("dm-reset")&&!operation.equals("dm-stalled")) operation="";
        if(traceTag==id) { traceTag=0; tracePath=null; }
        if(discoveryId==id) discoveryId=0;
    }
    @Override public byte[] advert(String action) {
        if(!action.equals("zero")&&!action.equals("flood")&&!action.equals("copy")) throw new IllegalArgumentException("Unknown advert action");
        begin(action.equals("copy")?"export":"advert");
        return action.equals("copy")?new byte[]{17}:new byte[]{7,(byte)(action.equals("flood")?1:0)};
    }
    private final java.util.Map<String, byte[]> contactKeys = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, byte[]> contactPaths = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Integer> contactPathWidths = new java.util.concurrent.ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private long lastMessageTimestamp;

    @Override public byte[] initialCommand() {
        initializing=true;channelsLoaded=false;syncStalled=false;syncInFlight=false;syncRequested=false;
        loadDeadline=clock.getAsLong()+30000;
        byte[] name = "SV8TTL LoRa Dash".getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[8 + name.length]; result[0] = 1;
        System.arraycopy(name, 0, result, 8, name.length); return result;
    }

    @Override public byte[] sendChannel(int channel, String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        if (channelTextLimit==0) throw new IllegalArgumentException("Wait for the MeshCore radio name before sending");
        if (text.indexOf('\0')>=0 || body.length == 0 || body.length > channelTextLimit)
            throw new IllegalArgumentException("MeshCore channel text must be 1–"+channelTextLimit+" UTF-8 bytes (radio name included separately)");
        byte[] packet = new byte[7 + body.length]; packet[0] = 3; packet[2] = (byte)channel;
        long now = Math.max(System.currentTimeMillis() / 1000,lastMessageTimestamp+1);
        lastMessageTimestamp=now;
        for (int i = 0; i < 4; i++) packet[3 + i] = (byte)(now >>> (8 * i));
        System.arraycopy(body, 0, packet, 7, body.length);
        begin("channel");
        repeats.remember(lastId,packet,advertName,channelSecrets.get(channel));
        return packet;
    }

    @Override public byte[] nodeCommand(String action, String id, String text, int channel) {
        byte[] key = contactKeys.get(id);
        if (key == null) throw new IllegalArgumentException("Contact is no longer loaded");
        if ("dm".equals(action)) {
            if(dmSends.size()>=32) throw new IllegalArgumentException("Wait for pending direct messages to finish");
            byte[] body = text.trim().getBytes(StandardCharsets.UTF_8);
            if (text.indexOf('\0')>=0 || body.length == 0 || body.length > 138) throw new IllegalArgumentException("MeshCore DM must be 1–138 UTF-8 bytes, without NUL characters");
            byte[] packet = new byte[13 + body.length]; packet[0] = 2;
            long now = Math.max(System.currentTimeMillis() / 1000,lastMessageTimestamp+1);
            lastMessageTimestamp=now;
            for (int i = 0; i < 4; i++) packet[3 + i] = (byte)(now >>> (8 * i));
            System.arraycopy(key, 0, packet, 7, 6);
            System.arraycopy(body, 0, packet, 13, body.length);
            begin("dm");
            boolean direct=knownRoutes.contains(id);
            // Official composer limit leaves space for extended retry metadata.
            boolean extended=firmwareVersion>=4;
            int directCount=direct?(extended?configuredDirectAttempts:2):0;
            int floodCount=extended?configuredFloodAttempts:direct?1:3;
            DmSend job=new DmSend(lastId,packet,key.clone(),directCount,directCount+floodCount);
            dmSends.put(lastId,job);commandDeadline=clock.getAsLong()+15000;
            return packet;
        }
        if ("req".equals(action)) {
            begin("status");
            byte[] packet = new byte[33]; packet[0] = 27;
            System.arraycopy(key, 0, packet, 1, 32);
            return packet;
        }
        if ("tr".equals(action)) {
            begin("discovery"); discoveryId=lastId; discoveryTarget=id;
            byte[] packet=new byte[34]; packet[0]=52;
            System.arraycopy(key,0,packet,2,32);
            return packet;
        }
        if ("ping".equals(action)) {
            int type=contactTypes.getOrDefault(id,0);
            if(type!=2&&type!=3) throw new IllegalArgumentException("Ping requires a repeater or repeating room server");
            int width;
            try { width=Integer.parseInt(text); } catch(NumberFormatException error) { throw new IllegalArgumentException("Choose a Ping ID length"); }
            if(width!=1&&width!=2&&width!=4) throw new IllegalArgumentException("Ping ID length must be 1, 2 or 4 bytes");
            begin("trace");tracePing=true;traceStarted=clock.getAsLong();
            traceTarget=id;traceTurn=1;tracePath=java.util.Arrays.copyOf(key,width);
            long tag;do {tag=random.nextInt()&0xffffffffL;}while(tag==0);
            traceTag=lastId=pendingId=tag;
            byte[] packet=new byte[10+width];packet[0]=36;
            for(int i=0;i<4;i++)packet[1+i]=(byte)(tag>>>(8*i));
            packet[9]=(byte)(width==1?0:width==2?1:2);
            System.arraycopy(tracePath,0,packet,10,width);
            return packet;
        }
        if ("trace-path".equals(action)) {
            byte[] path = contactPaths.get(id);
            Integer width = contactPathWidths.get(id);
            if (path == null || width == null)
                throw new IllegalArgumentException("No learned route is available to trace");
            if (width == 3) throw new IllegalArgumentException("This radio does not support tracing a 3-byte hash route");
            if (width != 1 && width != 2 && width != 4)
                throw new IllegalArgumentException("Unsupported route hash width");
            // TRACE follows repeater hashes. A user/client does not forward a TRACE.
            boolean repeater=contactTypes.getOrDefault(id,0)==2;
            int count=path.length/width;
            int outward=count+(repeater?1:0);
            if(outward==0) throw new IllegalArgumentException("No repeater path is available to trace");
            int total=outward*2-1;
            if(total>63 || total*width>170) throw new IllegalArgumentException("Round-trip trace path is too long");
            byte[] roundTrip=new byte[total*width];
            System.arraycopy(path,0,roundTrip,0,path.length);
            if(repeater) System.arraycopy(key,0,roundTrip,path.length,width);
            for(int i=0;i<outward-1;i++) System.arraycopy(roundTrip,(outward-2-i)*width,roundTrip,(outward+i)*width,width);
            begin("trace");
            tracePing=false;traceTarget=id; traceTurn=outward; tracePath=roundTrip;
            long tag; do {tag=random.nextInt()&0xffffffffL;} while(tag==0);
            traceTag=lastId=pendingId=tag;
            byte[] packet = new byte[10 + roundTrip.length]; packet[0] = 36;
            for (int i = 0; i < 4; i++) packet[1 + i] = (byte)(tag >>> (8 * i));
            packet[9] = (byte)(width == 1 ? 0 : width == 2 ? 1 : 2);
            System.arraycopy(roundTrip, 0, packet, 10, roundTrip.length);
            return packet;
        }
        throw new IllegalArgumentException("Action is not supported by MeshCore");
    }

    @Override public void receive(byte[] packet, Events events) {
        if (packet.length == 0) return;
        int type = packet[0] & 255;
        if(syncStalled)return;
        if(initializing&&(type==2||type==3||type==4||type==5||type==13||type==18))loadDeadline=clock.getAsLong()+30000;
        if (type == 5) {
            events.status("MeshCore companion ready"); pending = new byte[]{0x16, 0x03};
            if(packet.length>=58) {
                int end=58; while(end<packet.length&&packet[end]!=0) end++;
                advertName=java.util.Arrays.copyOfRange(packet,58,end);
                // Keep ciphertext within 144 bytes (nine AES blocks). The 176-byte
                // companion RX log then has room for its 3-byte prefix, 2-byte
                // packet header, 3-byte group overhead, 4-byte scope and 20 path
                // bytes. Larger packets can be transmitted but their repeats may
                // never reach the app. Plaintext includes timestamp/type (5) and
                // the exact UTF-8 sender name followed by ": " (2).
                channelTextLimit=Math.max(0,144-5-advertName.length-2);
                events.status("MeshCore channel budget: nameBytes="+advertName.length+", textBytes="+channelTextLimit+", encryptedBytes<=144");
            }
            if (packet.length >= 36) {
                String name = packet.length > 58 ? new String(packet, 58, packet.length - 58, StandardCharsets.UTF_8).replace("\u0000", "").trim() : "MeshCore";
                ownKey=hex(packet,4,32);
                events.ownNode(ownKey, name.isEmpty() ? "MeshCore" : name);
            }
            if (packet.length >= 44) events.ownPosition(littleInt(packet, 36) / 1e6, littleInt(packet, 40) / 1e6);
        }
        else if (type == 13) {
            if(packet.length>1) firmwareVersion=packet[1]&255;
            events.status("MeshCore device info loaded");
            if (packet.length > 3) maxChannels = Math.min(40, Math.max(1, packet[3] & 255));
            pending = new byte[]{4};
        }
        else if ((type == 3 || type == 0x8a) && packet.length >= 132) {
            int end = 100; while (end < 132 && packet[end] != 0) end++;
            StringBuilder id = new StringBuilder();
            for (int i = 1; i <= 6; i++) id.append(String.format("%02x", packet[i] & 255));
            contactTypes.put(id.toString(),packet[33]&255);
            contactKeys.put(id.toString(), java.util.Arrays.copyOfRange(packet, 1, 33));
            int encodedPath = packet[35] & 255;
            int hops = encodedPath == 0xff ? -1 : encodedPath & 0x3f;
            if(hops>=0) knownRoutes.add(id.toString()); else knownRoutes.remove(id.toString());
            if (hops > 0) {
                int width = (encodedPath >>> 6) + 1;
                int size = hops * width;
                if (size <= 64) {
                    contactPaths.put(id.toString(), java.util.Arrays.copyOfRange(packet, 36, 36 + size));
                    contactPathWidths.put(id.toString(), width);
                }
            } else {
                contactPaths.remove(id.toString()); contactPathWidths.remove(id.toString());
            }
            double lat = Double.NaN, lon = Double.NaN;
            if (packet.length >= 144) {
                lat = littleInt(packet, 136) / 1e6;
                lon = littleInt(packet, 140) / 1e6;
                if (lat == 0 && lon == 0) { lat = Double.NaN; lon = Double.NaN; }
            }
            events.node(id.toString(), new String(packet, 100, end - 100, StandardCharsets.UTF_8),
                    contactType(packet[33] & 255), "", 0, hops, lat, lon);
            if(packet.length>=136) events.lastHeard(id.toString(),littleInt(packet,132)&0xffffffffL);
        }
        else if (type == 4) { events.status("MeshCore loading channels"); channelQuery = 0; pending = new byte[]{0x1f, 0}; }
        else if (type == 0 && !operation.isEmpty()) {
            if(operation.equals("dm-reset")) {
                DmSend job=dmSends.get(pendingId);
                if(job!=null) {job.resetDone=true;job.due=0;knownRoutes.remove(hex(job.key,0,6));contactPaths.remove(hex(job.key,0,6));}
                operation="";return;
            }
            if(operation.equals("channel")) events.delivery(pendingId,"sent_radio");
            else if(operation.equals("advert")) events.advertResult("Advert accepted by radio",true);
            else if(!operation.equals("status")) return;
            operation="";
        }
        else if(type==6 && packet.length>=10) {
            long token=littleInt(packet,2)&0xffffffffL;
            if(operation.equals("dm")) {
                if(acknowledgments.size()>=512) acknowledgments.entrySet().removeIf(item->!dmSends.containsKey(item.getValue()));
                DmSend job=dmSends.get(pendingId);
                if(job!=null) {
                    job.actualFlood=packet[1]!=0;
                    acknowledgments.put(token,pendingId);
                    job.due=clock.getAsLong()+(littleInt(packet,6)&0xffffffffL)+jitter.getAsInt();
                    events.delivery(pendingId,job.status());
                }
                operation="";
            } else if(operation.equals("trace") && token==traceTag) operation="";
            else if(operation.equals("status")) operation="";
            else if(operation.equals("discovery")) operation="";
        }
        else if(type==0x82 && packet.length>=9) {
            Long id=acknowledgments.remove(littleInt(packet,1)&0xffffffffL);
            if(id!=null) {
                stopDm(id);acknowledgments.values().removeIf(value->value.equals(id));
                events.delivery(id,"delivered");
            }
        }
        else if(type==11 && operation.equals("export") && packet.length>1) {
            operation=""; events.advertExport("meshcore://"+hex(packet,1,packet.length-1));
        }
        else if (type == 1) {
            String error = "MeshCore command error " + (packet.length > 1 ? packet[1] & 255 : "unknown");
            if(operation.equals("dm")||operation.equals("dm-reset")||operation.equals("channel")) {
                stopDm(pendingId);events.delivery(pendingId,"failed: "+error+"; automatic retries stopped");
            }
            else if(operation.equals("trace")||operation.equals("discovery")) {
                events.trace(new TraceResult(operation.equals("discovery")?discoveryId:traceTag,
                        operation.equals("discovery")?discoveryTarget:traceTarget,new java.util.ArrayList<>(),new java.util.ArrayList<>(),
                        new java.util.ArrayList<>(),new java.util.ArrayList<>(),error)); traceTag=0;discoveryId=0;
            } else if(operation.equals("advert")||operation.equals("export")) events.advertResult(error,false);
            else events.actionResult(error);
            operation="";
        }
        else if (type == 8 || type == 17) {
            int start = type == 17 ? 4 : 1;
            if (packet.length < start + 7) return;
            int channel = packet[start] & 255;
            String message = new String(packet, start + 7, packet.length - start - 7, StandardCharsets.UTF_8);
            int path=packet[start+1]&255;
            int hops=path==255?-1:path&0x3f;
            events.message(channel, "MeshCore", message, littleInt(packet, start + 3) & 0xffffffffL,hops);
            syncInFlight=false;syncRequested = true;
        } else if (type == 7 || type == 16) {
            int start = type == 16 ? 4 : 1;
            if (packet.length < start + 12) return;
            String id = hex(packet, start, 6);
            int textStart = start + 12 + ((packet[start + 7] & 255) == 2 ? 4 : 0);
            if (packet.length >= textStart)
                events.directMessage(id, new String(packet, textStart, packet.length - textStart, StandardCharsets.UTF_8), littleInt(packet, start + 8) & 0xffffffffL,
                        (packet[start+6]&255)==255?-1:(packet[start+6]&0x3f));
            syncInFlight=false;syncRequested = true;
        } else if (type == 27 && syncInFlight) {
            // Binary channel data also consumes a queue item, even though this UI displays text only.
            syncInFlight=false;syncRequested=true;
            events.status("MeshCore skipped unsupported binary channel data ("+packet.length+" bytes)");
        } else if (type == 131) { syncRequested = true; }
        else if (type == 10 && syncInFlight) {
            boolean initialSync=initializing;
            syncInFlight=false;syncRequested=false;initializing=false;
            if(initialSync) events.syncComplete();
        }
        else if (type == 0x88) {
            MeshCoreRepeatTracker.Observation observation=MeshCoreRepeatTracker.observe(packet);
            if(observation!=null) {
                long id=repeats.match(packet);
                if(id!=0) {events.heardRepeat(id);events.heardPath(id,observation.path,observation.snr,observation.rssi);}
                else for(java.util.Map.Entry<Integer,byte[]> item:channelSecrets.entrySet()) {
                    byte[] plain=MeshCoreRepeatTracker.decode(observation,item.getValue());
                    if(plain!=null) events.channelPath(item.getKey(),littleInt(plain,0)&0xffffffffL,
                            new String(plain,5,plain.length-5,StandardCharsets.UTF_8),observation.path,observation.snr,observation.rssi);
                }
            }
        }
        else if (type == 18 && packet.length >= 34) {
            int channel = packet[1] & 255;
            if(packet.length>=50) channelSecrets.put(channel,java.util.Arrays.copyOfRange(packet,34,50));
            else channelSecrets.remove(channel);
            int end = 2; while (end < 34 && packet[end] != 0) end++;
            String name = new String(packet, 2, end - 2, StandardCharsets.UTF_8);
            if (!name.isEmpty()) events.channel(channel, name);
            if (++channelQuery < maxChannels) pending = new byte[]{0x1f, (byte)channelQuery};
            else { channelsLoaded=true;syncRequested = true;events.status("MeshCore loading queued messages"); }
        }
        else if (type == 0x87 && packet.length >= 8) events.actionResult("MeshCore status reply received from " + hex(packet, 2, 6));
        else if(type==0x8d && packet.length>=10 && discoveryId!=0 && hex(packet,2,6).equals(discoveryTarget)) {
            java.util.List<String> outward=new java.util.ArrayList<>(),back=new java.util.ArrayList<>();
            int offset=8;
            for(java.util.List<String> route:java.util.Arrays.asList(outward,back)) {
                if(offset>=packet.length) return;
                int encoded=packet[offset++]&255, width=(encoded>>>6)+1, count=encoded&63;
                if(width>3||count*width>64||offset+count*width>packet.length) return;
                for(int i=0;i<count;i++) {route.add(hex(packet,offset,width));offset+=width;}
            }
            outward.add(0,ownKey);outward.add(discoveryTarget);
            back.add(0,discoveryTarget);back.add(ownKey);
            long id=discoveryId;discoveryId=0;operation="";
            events.trace(new TraceResult(id,discoveryTarget,outward,back,new java.util.ArrayList<>(),new java.util.ArrayList<>(),
                    "MeshCore path discovery reached the receiver and reported both directions. This response does not include per-hop SNR."));
        }
        else if (type == 0x89 && packet.length >= 12 && traceTag!=0) {
            long tag=littleInt(packet,4)&0xffffffffL;
            int bytes=packet[2]&255, width=1<< (packet[3]&3), count=bytes/width;
            if(tag!=traceTag || bytes%width!=0 || packet.length<12+bytes+count+1 || tracePath==null ||
                    !java.util.Arrays.equals(tracePath,java.util.Arrays.copyOfRange(packet,12,12+bytes))) return;
            java.util.List<String> forward=new java.util.ArrayList<>(),back=new java.util.ArrayList<>();
            java.util.List<Integer> snr=new java.util.ArrayList<>(),backSnr=new java.util.ArrayList<>();
            forward.add(ownKey);
            for(int i=0;i<count;i++) {
                String hash=hex(packet,12+i*width,width);
                if(i<traceTurn) { forward.add(hash); snr.add((int)packet[12+bytes+i]); }
                if(i>=traceTurn-1) { back.add(hash); if(i>=traceTurn) backSnr.add((int)packet[12+bytes+i]); }
            }
            back.add(ownKey); backSnr.add((int)packet[12+bytes+count]);
            String note=contactTypes.getOrDefault(traceTarget,0)==2?
                    "Tested repeater path, outward and back. This tests the chosen route, not a separate route discovery.":
                    "Tested path to the last learned repeater and back. MeshCore clients do not forward TRACE; the final link to this receiver is not measured.";
            if(tracePing) note="Zero-hop Ping reply in "+Math.max(0,clock.getAsLong()-traceStarted)+" ms. SNR is reported for each direction. The responder is identified by the chosen short ID; matching IDs cannot distinguish devices.";
            events.trace(new TraceResult(traceTag,traceTarget,forward,back,snr,backSnr,note));
            traceTag=0; tracePath=null;
        }
    }

    private int littleInt(byte[] data, int offset) {
        return (data[offset] & 255) | ((data[offset + 1] & 255) << 8) |
                ((data[offset + 2] & 255) << 16) | (data[offset + 3] << 24);
    }

    private String hex(byte[] bytes, int offset, int length) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) result.append(String.format("%02x", bytes[offset + i] & 255));
        return result.toString();
    }

    private String contactType(int type) {
        switch (type) {
            case 1: return "User";
            case 2: return "Repeater";
            case 3: return "Room server";
            case 4: return "Sensor";
            default: return "Contact type " + type;
        }
    }

    @Override public byte[] nextCommand() {
        if (pending != null) { byte[] result = pending; pending = null; return result; }
        if (channelsLoaded&&syncRequested&&!syncInFlight&&!syncStalled) {
            syncRequested=false;syncInFlight=true;loadDeadline=clock.getAsLong()+30000;return new byte[]{10};
        }
        return null;
    }
}
