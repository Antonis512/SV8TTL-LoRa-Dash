package org.overmesh.android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** Phone-local history partitioned by the radio's own stable identity. */
final class ChatStore extends SQLiteOpenHelper {
    static final class Message {
        final long id, receivedAt;
        long packetId,replyId;
        boolean reaction;
        final String radio, nodeId, sender, text, time, delivery, peer, paths;
        final int channel, hops, heardRepeats;
        final boolean own, unread;
        Message(long id, String radio, String nodeId, int channel, String sender, String text,
                String time, long receivedAt, boolean own, String delivery, String peer, boolean unread, int hops, int heardRepeats, String paths) {
            this.id=id; this.radio=radio; this.nodeId=nodeId; this.channel=channel;
            this.sender=sender; this.text=text; this.time=time; this.receivedAt=receivedAt;
            this.own=own; this.delivery=delivery; this.peer=peer; this.unread=unread; this.hops=hops; this.heardRepeats=heardRepeats;this.paths=paths;
        }
    }
    static final class KnownRadio {
        final String radio, id, name, address;
        final long lastSeen;
        KnownRadio(String radio, String id, String name, String address, long lastSeen) {
            this.radio=radio; this.id=id; this.name=name; this.address=address; this.lastSeen=lastSeen;
        }
    }
    static final class SavedChannel {
        final int index;
        final String name;
        SavedChannel(int index, String name) { this.index=index; this.name=name; }
    }
    ChatStore(Context context) { super(context, "chat-history.db", null, 13); setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, radio TEXT NOT NULL, " +
                "node_id TEXT NOT NULL DEFAULT '', channel INTEGER NOT NULL, sender TEXT NOT NULL, " +
                "body TEXT NOT NULL, time TEXT NOT NULL, received_at INTEGER NOT NULL DEFAULT 0, " +
                "own INTEGER NOT NULL, delivery TEXT NOT NULL, peer TEXT NOT NULL DEFAULT '', unread INTEGER NOT NULL DEFAULT 0, hops INTEGER NOT NULL DEFAULT -1, heard_repeats INTEGER NOT NULL DEFAULT -1, paths TEXT NOT NULL DEFAULT '[]', packet_id INTEGER NOT NULL DEFAULT 0, reply_id INTEGER NOT NULL DEFAULT 0, reaction INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX messages_node ON messages(radio,node_id,channel,id)");
        createRadioTables(db);
        createHistoryIndex(db);
        createSignalHistory(db);
    }
    private void createHistoryIndex(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS messages_history ON messages(radio,node_id,peer,channel,received_at,id)");
        db.execSQL("CREATE TABLE IF NOT EXISTS pending_backup_settings(id INTEGER PRIMARY KEY,body TEXT NOT NULL)");
    }
    private void createRadioTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS node_heard (radio TEXT NOT NULL,node_id TEXT NOT NULL,peer TEXT NOT NULL,heard INTEGER NOT NULL,PRIMARY KEY(radio,node_id,peer))");
        db.execSQL("CREATE TABLE IF NOT EXISTS known_radios (radio TEXT NOT NULL, id TEXT NOT NULL, " +
                "name TEXT NOT NULL, address TEXT NOT NULL DEFAULT '', last_seen INTEGER NOT NULL, PRIMARY KEY(radio,id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS channel_names (radio TEXT NOT NULL, node_id TEXT NOT NULL, " +
                "channel INTEGER NOT NULL, name TEXT NOT NULL, PRIMARY KEY(radio,node_id,channel))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<13)createSignalHistory(db);
        if(oldVersion<10) db.execSQL("ALTER TABLE messages ADD COLUMN reaction INTEGER NOT NULL DEFAULT 0");
        if(oldVersion<9) {
            createRadioTables(db);
            db.execSQL("ALTER TABLE messages ADD COLUMN packet_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE messages ADD COLUMN reply_id INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 2) db.execSQL("ALTER TABLE messages ADD COLUMN peer TEXT NOT NULL DEFAULT ''");
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE messages ADD COLUMN node_id TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE messages ADD COLUMN received_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE INDEX messages_node ON messages(radio,node_id,channel,id)");
            createRadioTables(db);
            db.execSQL("INSERT OR IGNORE INTO known_radios(radio,id,name,address,last_seen) " +
                    "SELECT radio,'','Unassigned legacy history','',0 FROM messages GROUP BY radio");
        }
        if (oldVersion < 4) db.execSQL("ALTER TABLE messages ADD COLUMN unread INTEGER NOT NULL DEFAULT 0");
        if (oldVersion < 5) {
            db.execSQL("DELETE FROM messages WHERE node_id=''");
            db.execSQL("DELETE FROM known_radios WHERE id=''");
        }
        if (oldVersion < 8) db.execSQL("ALTER TABLE messages ADD COLUMN paths TEXT NOT NULL DEFAULT '[]'");
        if (oldVersion < 7) db.execSQL("ALTER TABLE messages ADD COLUMN heard_repeats INTEGER NOT NULL DEFAULT -1");
        if (oldVersion < 6) db.execSQL("ALTER TABLE messages ADD COLUMN hops INTEGER NOT NULL DEFAULT -1");
        if (oldVersion < 11) createHistoryIndex(db);
        if (oldVersion < 12) db.execSQL("CREATE TABLE IF NOT EXISTS pending_backup_settings(id INTEGER PRIMARY KEY,body TEXT NOT NULL)");
    }
    private void createSignalHistory(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS signal_history(id INTEGER PRIMARY KEY AUTOINCREMENT,node_id TEXT NOT NULL,peer TEXT NOT NULL,body TEXT NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS signal_peer ON signal_history(node_id,peer,id)");
    }
    void saveSignal(String ownId,String peer,String body) {
        if(ownId.isEmpty())return;
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ContentValues values=new ContentValues();values.put("node_id",ownId);values.put("peer",peer);values.put("body",body);
            db.insertOrThrow("signal_history",null,values);
            db.execSQL("DELETE FROM signal_history WHERE node_id=? AND peer=? AND id NOT IN (SELECT id FROM signal_history WHERE node_id=? AND peer=? ORDER BY id DESC LIMIT 1000)",new Object[]{ownId,peer,ownId,peer});
            db.setTransactionSuccessful();
        } finally {db.endTransaction();}
    }
    org.json.JSONArray signals(String ownId,String peer) {
        org.json.JSONArray rows=new org.json.JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM signal_history WHERE node_id=? AND peer=? ORDER BY id DESC LIMIT 1000",new String[]{ownId,peer})) {
            while(c.moveToNext())try{rows.put(new org.json.JSONObject(c.getString(0)));}catch(org.json.JSONException ignored){}
        }
        return rows;
    }
    void clearSignals(String ownId,String peer) {getWritableDatabase().delete("signal_history","node_id=? AND peer=?",new String[]{ownId,peer});}
    void pendingSettings(String json) {ContentValues v=new ContentValues();v.put("id",1);v.put("body",json);getWritableDatabase().insertWithOnConflict("pending_backup_settings",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    String pendingSettings() {try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM pending_backup_settings WHERE id=1",null)){return c.moveToFirst()?c.getString(0):null;}}
    void clearPendingSettings(){getWritableDatabase().delete("pending_backup_settings","id=1",null);}
    void setReaction(long id,boolean reaction) {
        ContentValues v=new ContentValues();v.put("reaction",reaction?1:0);getWritableDatabase().update("messages",v,"id=?",new String[]{Long.toString(id)});
    }
    boolean hasPacket(String radio,String nodeId,long packetId) {
        if(packetId==0)return false;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM messages WHERE radio=? AND node_id=? AND packet_id=? LIMIT 1",new String[]{radio,nodeId,Long.toString(packetId)})){return c.moveToFirst();}
    }
    void setReferences(long id,long packetId,long replyId) {
        ContentValues v=new ContentValues();v.put("packet_id",packetId);v.put("reply_id",replyId);
        getWritableDatabase().update("messages",v,"id=?",new String[]{Long.toString(id)});
    }
    long lastHeard(String radio,String nodeId,String peer) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT heard FROM node_heard WHERE radio=? AND node_id=? AND peer=?",new String[]{radio,nodeId,peer})) {return c.moveToFirst()?c.getLong(0):0;}
    }
    void heard(String radio,String nodeId,String peer,long seconds) {
        if(nodeId.isEmpty()||seconds<=0||seconds>System.currentTimeMillis()/1000+300) return;
        if(seconds<=lastHeard(radio,nodeId,peer)) return;
        ContentValues v=new ContentValues();v.put("radio",radio);v.put("node_id",nodeId);v.put("peer",peer);v.put("heard",seconds);
        getWritableDatabase().insertWithOnConflict("node_heard",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    void setPaths(long id,String paths) {
        if(id<0) return;
        ContentValues v=new ContentValues();v.put("paths",paths);
        getWritableDatabase().update("messages",v,"id=?",new String[]{Long.toString(id)});
    }
    void setHeardRepeats(long id,int count) {
        if(id<0) return;
        ContentValues v=new ContentValues();v.put("heard_repeats",count);
        getWritableDatabase().update("messages",v,"id=?",new String[]{Long.toString(id)});
    }
    long save(String radio, String nodeId, int channel, String sender, String text, String time,
              long receivedAt, boolean own, String delivery, String peer) {
        ContentValues v=new ContentValues();
        v.put("radio",radio); v.put("node_id",nodeId); v.put("channel",channel);
        v.put("sender",sender); v.put("body",text); v.put("time",time);
        v.put("received_at",receivedAt); v.put("own",own?1:0); v.put("delivery",delivery); v.put("peer",peer);
        return getWritableDatabase().insertOrThrow("messages",null,v);
    }
    long saveIncoming(String radio,String nodeId,int channel,String sender,String text,String time,
                      long receivedAt,String peer,int hops,long packetId,long replyId,String paths) {
        ContentValues v=new ContentValues();
        v.put("radio",radio);v.put("node_id",nodeId);v.put("channel",channel);
        v.put("sender",sender);v.put("body",text);v.put("time",time);v.put("received_at",receivedAt);
        v.put("own",0);v.put("delivery","");v.put("peer",peer);v.put("unread",1);
        v.put("hops",hops);v.put("packet_id",packetId);v.put("reply_id",replyId);v.put("paths",paths);
        return getWritableDatabase().insertOrThrow("messages",null,v);
    }
    void deleteMessage(String radio,String nodeId,long id) {
        getWritableDatabase().delete("messages","id=? AND radio=? AND node_id=?",new String[]{Long.toString(id),radio,nodeId});
    }
    void setDelivery(long id, String delivery) {
        if (id<0) return;
        ContentValues v=new ContentValues(); v.put("delivery",delivery);
        getWritableDatabase().update("messages",v,"id=?",new String[]{Long.toString(id)});
    }
    void setHops(long id,int hops) {
        if(id<0) return;
        ContentValues v=new ContentValues(); v.put("hops",hops);
        getWritableDatabase().update("messages",v,"id=?",new String[]{Long.toString(id)});
    }
    void markInterruptedSends() {
        ContentValues v=new ContentValues(); v.put("delivery","unconfirmed");
        getWritableDatabase().update("messages",v,"own=1 AND (delivery IN (?,?,?,?) OR delivery LIKE 'sending:%' OR (radio='mc' AND peer<>'' AND delivery='sent_radio'))",new String[]{"queued","sent_to_radio","accepted","relayed"});
    }
    List<Message> load(String radio, String nodeId) {
        return readMessages("radio=? AND node_id=?",new String[]{radio,nodeId},"received_at ASC,id ASC",null);
    }
    // Unknown dates (zero) precede dated messages, with stable ID ordering for ties.
    List<Message> page(String radio,String nodeId,String peer,int channel,long beforeId) {
        String where="radio=? AND node_id=? AND peer=?"+(peer.isEmpty()?" AND channel=?":"");
        List<String> args=new ArrayList<>();args.add(radio);args.add(nodeId);args.add(peer);
        if(peer.isEmpty())args.add(Integer.toString(channel));
        if(beforeId>0) {
            where+=" AND (received_at<(SELECT received_at FROM messages WHERE id=?) OR (received_at=(SELECT received_at FROM messages WHERE id=?) AND id<?))";
            for(int i=0;i<3;i++)args.add(Long.toString(beforeId));
        }
        List<Message> result=readMessages(where,args.toArray(new String[0]),"received_at DESC,id DESC","100");
        java.util.Collections.reverse(result);return result;
    }
    List<Message> recent(String radio,String nodeId) {
        List<Message> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT peer,CASE WHEN peer='' THEN channel ELSE -1 END FROM messages WHERE radio=? AND node_id=? GROUP BY peer,CASE WHEN peer='' THEN channel ELSE -1 END",new String[]{radio,nodeId})) {
            while(c.moveToNext())result.addAll(page(radio,nodeId,c.getString(0),c.getInt(1),0));
        }
        return result;
    }
    java.util.Map<String,Long> heardNodes(String radio,String nodeId) {
        java.util.Map<String,Long> result=new java.util.LinkedHashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT peer,heard FROM node_heard WHERE radio=? AND node_id=?",new String[]{radio,nodeId})) {
            while(c.moveToNext())result.put(c.getString(0),c.getLong(1));
        }
        return result;
    }
    private List<Message> readMessages(String where,String[] args,String order,String limit) {
        List<Message> result=new ArrayList<>();
        try (Cursor c=getReadableDatabase().query("messages",
                new String[]{"id","radio","node_id","channel","sender","body","time","received_at","own","delivery","peer","unread","hops","heard_repeats","paths","packet_id","reply_id","reaction"},
                where,args,null,null,order,limit)) {
            while(c.moveToNext()) { Message message=new Message(c.getLong(0),c.getString(1),c.getString(2),
                    c.getInt(3),c.getString(4),c.getString(5),c.getString(6),c.getLong(7),
                    c.getInt(8)!=0,c.getString(9),c.getString(10),c.getInt(11)!=0,c.getInt(12),c.getInt(13),c.getString(14));
                message.packetId=c.getLong(15);message.replyId=c.getLong(16);message.reaction=c.getInt(17)!=0;result.add(message);
            }
        }
        return result;
    }
    interface MessageVisitor {void accept(Message message) throws Exception;}
    long maxMessageId(){try(Cursor c=getReadableDatabase().rawQuery("SELECT MAX(id) FROM messages",null)){return c.moveToFirst()?c.getLong(0):0;}}
    void visitMessages(String radio,String nodeId,MessageVisitor visitor) throws Exception {
        long after=0,last=maxMessageId();
        while(true) {
            List<Message> page=readMessages("radio=? AND node_id=? AND id>? AND id<=?",new String[]{radio,nodeId,Long.toString(after),Long.toString(last)},"id ASC","100");
            if(page.isEmpty())return;
            for(Message message:page){visitor.accept(message);after=message.id;}
        }
    }
    java.util.Map<String,Boolean> unreadConversations(String radio,String nodeId) {
        java.util.Map<String,Boolean> result=new java.util.HashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT peer,channel FROM messages WHERE radio=? AND node_id=? AND unread=1 GROUP BY peer,channel",new String[]{radio,nodeId})) {
            while(c.moveToNext())result.put(c.getString(0).isEmpty()?"c:"+c.getInt(1):"d:"+c.getString(0),true);
        }return result;
    }
    void saveRadio(String radio, String id, String name, String address) {
        KnownRadio prior=getRadio(radio,id);
        ContentValues v=new ContentValues(); v.put("radio",radio); v.put("id",id);
        v.put("name",name.isEmpty() && prior!=null?prior.name:name);
        v.put("address",address.isEmpty() && prior!=null?prior.address:address);
        v.put("last_seen",System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("known_radios",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    boolean importRadio(String radio,String id,String name,String address,long lastSeen) {
        if(getRadio(radio,id)!=null) return false;
        ContentValues v=new ContentValues(); v.put("radio",radio); v.put("id",id);
        v.put("name",name); v.put("address",address); v.put("last_seen",lastSeen);
        return getWritableDatabase().insertWithOnConflict("known_radios",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1;
    }
    KnownRadio getRadio(String radio, String id) {
        try(Cursor c=getReadableDatabase().query("known_radios",new String[]{"radio","id","name","address","last_seen"},
                "radio=? AND id=?",new String[]{radio,id},null,null,null)) {
            return c.moveToFirst()?new KnownRadio(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4)):null;
        }
    }
    List<KnownRadio> radios() {
        List<KnownRadio> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("known_radios",new String[]{"radio","id","name","address","last_seen"},
                null,null,null,null,"last_seen DESC")) {
            while(c.moveToNext()) result.add(new KnownRadio(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4)));
        }
        return result;
    }
    void saveChannel(String radio,String nodeId,int index,String name) {
        ContentValues v=new ContentValues(); v.put("radio",radio); v.put("node_id",nodeId);
        v.put("channel",index); v.put("name",name);
        getWritableDatabase().insertWithOnConflict("channel_names",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    List<SavedChannel> channels(String radio,String nodeId) {
        List<SavedChannel> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("channel_names",new String[]{"channel","name"},
                "radio=? AND node_id=?",new String[]{radio,nodeId},null,null,"channel ASC")) {
            while(c.moveToNext()) result.add(new SavedChannel(c.getInt(0),c.getString(1)));
        }
        return result;
    }
    void deleteRadio(String radio,String id) {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            String[] args={radio,id};
            db.delete("messages","radio=? AND node_id=?",args);
            db.delete("channel_names","radio=? AND node_id=?",args);
            db.delete("node_heard","radio=? AND node_id=?",args);
            if("mt".equals(radio))db.delete("signal_history","node_id=?",new String[]{id});
            db.delete("known_radios","radio=? AND id=?",args);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    int eraseOlderThan(String radio,String id,long cutoff) {
        return getWritableDatabase().delete("messages",
                "radio=? AND node_id=? AND received_at>0 AND received_at<?",
                new String[]{radio,id,Long.toString(cutoff)});
    }
    boolean hasMessage(String radio,String id,int channel,String peer,String sender,String body,long receivedAt,boolean own) {
        try(Cursor c=getReadableDatabase().query("messages",new String[]{"id"},
                "radio=? AND node_id=? AND channel=? AND peer=? AND sender=? AND body=? AND received_at=? AND own=? AND reaction=0",
                new String[]{radio,id,Integer.toString(channel),peer,sender,body,Long.toString(receivedAt),own?"1":"0"},
                null,null,null,"1")) { return c.moveToFirst(); }
    }
    long findImportedMessage(String radio,String id,int channel,String peer,String sender,String body,long receivedAt,boolean own,boolean reaction,long packetId) {
        if(packetId!=0)try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM messages WHERE radio=? AND node_id=? AND packet_id=? AND own=? AND sender=? AND body=? LIMIT 1",new String[]{radio,id,Long.toString(packetId),own?"1":"0",sender,body})){if(c.moveToFirst())return c.getLong(0);}
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM messages WHERE radio=? AND node_id=? AND channel=? AND peer=? AND sender=? AND body=? AND received_at=? AND own=? AND reaction=? LIMIT 1",new String[]{radio,id,Integer.toString(channel),peer,sender,body,Long.toString(receivedAt),own?"1":"0",reaction?"1":"0"})){return c.moveToFirst()?c.getLong(0):-1;}
    }
    void enrichMessage(long id,long packetId,long replyId,int hops,int repeats,String paths) {
        getWritableDatabase().execSQL("UPDATE messages SET packet_id=CASE WHEN packet_id=0 THEN ? ELSE packet_id END,reply_id=CASE WHEN reply_id=0 THEN ? ELSE reply_id END,hops=CASE WHEN hops<0 THEN ? ELSE hops END,heard_repeats=CASE WHEN heard_repeats<0 THEN ? ELSE heard_repeats END,paths=CASE WHEN paths='[]' THEN ? ELSE paths END WHERE id=?",new Object[]{packetId,replyId,hops,repeats,paths,id});
    }
    int unreadCount() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM messages WHERE unread=1 AND own=0",null)) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    boolean hasUnread(String radio,String id,boolean directOnly) {
        String where="radio=? AND node_id=? AND unread=1"+(directOnly?" AND peer<>''":"");
        try(Cursor c=getReadableDatabase().query("messages",new String[]{"id"},where,
                new String[]{radio,id},null,null,null,"1")) { return c.moveToFirst(); }
    }
    boolean hasUnreadChannel(String radio,String id) {
        try(Cursor c=getReadableDatabase().query("messages",new String[]{"id"},
                "radio=? AND node_id=? AND unread=1 AND peer=''",new String[]{radio,id},
                null,null,null,"1")) { return c.moveToFirst(); }
    }
    void setUnread(long messageId,boolean unread) {
        if(messageId<0) return;
        ContentValues v=new ContentValues(); v.put("unread",unread?1:0);
        getWritableDatabase().update("messages",v,"id=?",new String[]{Long.toString(messageId)});
    }
    void markDirectRead(String radio,String id,String peer) {
        ContentValues v=new ContentValues(); v.put("unread",0);
        getWritableDatabase().update("messages",v,"radio=? AND node_id=? AND peer=?",
                new String[]{radio,id,peer});
    }
    void markChannelRead(String radio,String id,int channel) {
        ContentValues v=new ContentValues(); v.put("unread",0);
        getWritableDatabase().update("messages",
                v,"radio=? AND node_id=? AND channel=? AND peer=''",
                new String[]{radio,id,Integer.toString(channel)});
    }
    int clearDirect(String radio,String id,String peer) {
        return getWritableDatabase().delete("messages","radio=? AND node_id=? AND peer=?",
                new String[]{radio,id,peer});
    }
    int eraseDirectOlderThan(String radio,String id,String peer,long cutoff) {
        return getWritableDatabase().delete("messages",
                "radio=? AND node_id=? AND peer=? AND received_at>0 AND received_at<?",
                new String[]{radio,id,peer,Long.toString(cutoff)});
    }
}
