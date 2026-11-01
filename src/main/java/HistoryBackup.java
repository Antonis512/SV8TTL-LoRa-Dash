package org.overmesh.android;

import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.JsonToken;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/** Streams message arrays; legacy JSON and new JSON share the same import path. */
final class HistoryBackup {
    interface ReaderSource {Reader open() throws Exception;}
    interface NodeMerger {void merge(JSONObject node) throws Exception;}
    static Object readValue(JsonReader in) throws Exception {
        switch(in.peek()) {
            case BEGIN_OBJECT: {
                JSONObject object=new JSONObject();in.beginObject();while(in.hasNext()){String name=in.nextName();object.put(name,readValue(in));}in.endObject();return object;
            }
            case BEGIN_ARRAY: {
                JSONArray array=new JSONArray();in.beginArray();while(in.hasNext())array.put(readValue(in));in.endArray();return array;
            }
            case BOOLEAN:return in.nextBoolean();
            case NULL:in.nextNull();return JSONObject.NULL;
            case NUMBER:{String value=in.nextString();try{long n=Long.parseLong(value);if(n>=Integer.MIN_VALUE&&n<=Integer.MAX_VALUE)return Integer.valueOf((int)n);return Long.valueOf(n);}catch(NumberFormatException e){return Double.parseDouble(value);}}
            default:return in.nextString();
        }
    }
    static void writeValue(JsonWriter out,Object value) throws Exception {
        if(value==null||value==JSONObject.NULL){out.nullValue();return;}
        if(value instanceof JSONObject){out.beginObject();Iterator<String> keys=((JSONObject)value).keys();while(keys.hasNext()){String key=keys.next();out.name(key);writeValue(out,((JSONObject)value).get(key));}out.endObject();}
        else if(value instanceof JSONArray){out.beginArray();JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)writeValue(out,a.get(i));out.endArray();}
        else if(value instanceof Boolean)out.value((Boolean)value);
        else if(value instanceof Number)out.value((Number)value);
        else out.value(value.toString());
    }
    static JSONObject message(ChatStore.Message m) throws Exception {
        return new JSONObject().put("channel",m.channel).put("peer",m.peer).put("sender",m.sender).put("text",m.text).put("time",m.time)
            .put("receivedAt",m.receivedAt).put("own",m.own).put("reaction",m.reaction).put("packetId",m.packetId).put("replyId",m.replyId)
            .put("delivery",m.delivery).put("unread",m.unread).put("hops",m.hops).put("heardRepeats",m.heardRepeats).put("paths",new JSONArray(m.paths));
    }
    static void writeNode(JsonWriter out,ChatStore store,ChatStore.KnownRadio radio) throws Exception {
        out.beginObject();out.name("version").value(2);out.name("radio").value(radio.radio);out.name("nodeId").value(radio.id);
        out.name("name").value(radio.name);out.name("address").value(radio.address);out.name("lastSeen").value(radio.lastSeen);
        out.name("lastHeard");writeValue(out,new JSONObject(store.heardNodes(radio.radio,radio.id)));
        out.name("channels").beginArray();for(ChatStore.SavedChannel c:store.channels(radio.radio,radio.id))writeValue(out,new JSONObject().put("index",c.index).put("name",c.name));out.endArray();
        out.name("messages").beginArray();store.visitMessages(radio.radio,radio.id,m->writeValue(out,message(m)));out.endArray();out.endObject();
    }
    static void export(Writer destination,ChatStore store,String radio,String id,JSONObject settings) throws Exception {
        export(destination,store,radio,id,settings,new MapData(store));
    }
    static void export(Writer destination,ChatStore store,String radio,String id,JSONObject settings,MapData mapData) throws Exception {
        try(JsonWriter out=new JsonWriter(destination)) {
            if(settings==null){ChatStore.KnownRadio node=store.getRadio(radio,id);if(node==null)throw new IllegalArgumentException("Radio no longer saved");writeNode(out,store,node);}
            else {
                out.beginObject();out.name("format").value("overmesh-app-backup");out.name("version").value(4);
                out.name("settings");writeValue(out,settings);out.name("log");writeValue(out,store.logItems());out.name("map");writeValue(out,mapData.backup());out.name("radios").beginArray();
                for(ChatStore.KnownRadio node:store.radios())if(!node.id.isEmpty())writeNode(out,store,node);
                out.endArray();out.endObject();
            }
        }
    }
    static final class Headers {final List<JSONObject> nodes=new ArrayList<>();JSONObject settings,map;JSONArray log;}
    static JSONObject readNode(JsonReader in,JSONObject header,NodeMerger merger) throws Exception {
        JSONObject actual=new JSONObject();boolean messages=false;
        in.beginObject();while(in.hasNext()) {
            String name=in.nextName();
            if(name.equals("messages")) {
                messages=true;
                if(merger==null)in.skipValue();
                else {
                    in.beginArray();while(in.hasNext()) {
                        Object value=readValue(in);if(!(value instanceof JSONObject))throw new IllegalArgumentException("Invalid message record");
                        JSONObject message=(JSONObject)value;message.getInt("channel");message.getString("sender");message.getString("text");
                        merger.merge(new JSONObject().put("radio",header.getString("radio")).put("nodeId",header.getString("nodeId")).put("channels",new JSONArray()).put("messages",new JSONArray().put(message)));
                    }in.endArray();
                }
            }else actual.put(name,readValue(in));
        }in.endObject();
        int version=actual.optInt("version");String radio=actual.optString("radio");
        if(!messages||version<1||version>2||(!radio.equals("mc")&&!radio.equals("mt"))||actual.optString("nodeId").isEmpty()||actual.optJSONArray("channels")==null)
            throw new IllegalArgumentException("Invalid node backup");
        actual.put("messages",new JSONArray());
        if(header!=null&&(!header.getString("nodeId").equals(actual.getString("nodeId"))||!header.getString("radio").equals(actual.getString("radio"))))throw new IllegalArgumentException("Backup changed while reading");
        return actual;
    }
    static Headers read(ReaderSource source,boolean full,Headers headers,NodeMerger merger) throws Exception {
        Headers result=new Headers();int index=0;
        try(JsonReader in=new JsonReader(source.open())) {
            if(!full)result.nodes.add(readNode(in,headers==null?null:headers.nodes.get(0),merger));
            else {
                String format="";int version=0;boolean radios=false;
                in.beginObject();while(in.hasNext()) {
                    String name=in.nextName();
                    if(name.equals("radios")) {
                        radios=true;in.beginArray();while(in.hasNext()) {
                            if(headers!=null&&index>=headers.nodes.size())throw new IllegalArgumentException("Backup changed while reading");
                            result.nodes.add(readNode(in,headers==null?null:headers.nodes.get(index),merger));index++;
                        }in.endArray();
                    }else if(name.equals("format"))format=in.nextString();
                    else if(name.equals("version"))version=in.nextInt();
                    else if(name.equals("settings"))result.settings=(JSONObject)readValue(in);
                    else if(name.equals("log"))result.log=(JSONArray)readValue(in);
                    else if(name.equals("map"))result.map=(JSONObject)readValue(in);
                    else in.skipValue();
                }in.endObject();
                if(!radios||!format.equals("overmesh-app-backup")||version<1||version>4||result.settings==null||(version>=3&&result.log==null)||(version>=4&&result.map==null))throw new IllegalArgumentException("Unsupported app backup");
                if(headers!=null&&index!=headers.nodes.size())throw new IllegalArgumentException("Backup changed while reading");
            }
            if(in.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Unexpected data after backup");
        }return result;
    }
}
