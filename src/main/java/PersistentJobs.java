package org.overmesh.android;

import org.json.*;
import java.util.*;

/** Main-thread scheduler. Each attempt is persisted before handing a packet to the radio. */
final class PersistentJobs {
    interface Host {
        long elapsed(); long wall();
        void save(String json) throws Exception;
        String waitReason(JSONObject job) throws Exception;
        boolean busy(JSONObject job);
        double[] location(); // latitude, longitude; null unless fresh and accurate
        long send(JSONObject job,String text,double[] fix) throws Exception;
        void stopAttempts(JSONObject job);
        void changed();
    }
    final JSONArray jobs;
    private final Host host;
    private final Map<String,Long> due=new HashMap<>();
    PersistentJobs(Host host,String saved) throws Exception {
        this.host=host;jobs=new JSONArray(saved);
        for(int i=0;i<jobs.length();i++)if(live(jobs.getJSONObject(i))) {
            JSONObject j=jobs.getJSONObject(i);j.put("state","paused").put("detail","Paused after app restart — Resume when ready");
        }
        persist();
    }
    static boolean live(JSONObject j){return !"done".equals(j.optString("state"));}
    int activeCount(){int n=0;for(int i=0;i<jobs.length();i++)if("active".equals(jobs.optJSONObject(i).optString("state")))n++;return n;}
    boolean needsLocation(){for(int i=0;i<jobs.length();i++){JSONObject j=jobs.optJSONObject(i);if("active".equals(j.optString("state"))&&j.optBoolean("location"))return true;}return false;}
    void persist() throws Exception {host.save(jobs.toString());}
    static void remember(JSONArray ids,long id){if(ids.length()>=256)ids.remove(1);ids.put(id);}
    void add(JSONObject target,String text,boolean location,long interval,long duration,int retries) throws Exception {
        if(interval<PersistentPolicy.MIN_INTERVAL||interval>604800000L||duration<0||duration>2592000000L||retries< -1||retries>10000)throw new IllegalArgumentException("Check interval, expiry and retry limit");
        int active=0;for(int i=0;i<jobs.length();i++)if(live(jobs.getJSONObject(i))){
            JSONObject existing=jobs.getJSONObject(i);active++;
            if(existing.optBoolean("location")==location&&existing.optString("radio").equals(target.optString("radio"))&&existing.optString("nodeId").equals(target.optString("nodeId"))&&existing.optString("peer").equals(target.optString("peer"))&&existing.optInt("channel")==target.optInt("channel"))throw new IllegalArgumentException("This destination already has an active job. Stop it before starting another.");
        }
        if(active>=20)throw new IllegalArgumentException("Stop an existing job first (maximum 20)");
        while(jobs.length()>=50){int remove=-1;for(int i=0;i<jobs.length();i++)if(!live(jobs.getJSONObject(i))){remove=i;break;}if(remove<0)break;jobs.remove(remove);}
        JSONObject j=new JSONObject(target.toString()).put("id",UUID.randomUUID().toString()).put("text",text).put("location",location)
            .put("interval",interval).put("expires",duration==0?0:host.wall()+duration).put("maxRetries",retries)
            .put("attempts",0).put("created",host.wall()).put("state","active").put("detail","Ready").put("messageIds",new JSONArray()).put("packetIds",new JSONArray());
        jobs.put(j);
        try{persist();}catch(Exception e){jobs.remove(jobs.length()-1);throw e;}
        host.changed();
    }
    void action(String id,String action) throws Exception {
        for(int i=0;i<jobs.length();i++) {
            JSONObject j=jobs.getJSONObject(i);if(!j.optString("id").equals(id)||!live(j))continue;
            if("resume".equals(action)){
                if(j.optLong("expires")>0&&host.wall()>=j.optLong("expires")){finish(j,"Expired");continue;}
                String wait=host.waitReason(j);if(!wait.isEmpty())throw new IllegalArgumentException(wait);
                j.put("state","active").put("detail","Resumed");
                // Never replay immediately after recovery: preserve the minimum interval.
                due.put(id,host.elapsed()+(j.optInt("attempts")>0?j.optLong("interval"):0));
            }else if("stop".equals(action)||"answered".equals(action))finish(j,"answered".equals(action)?"Marked answered":"Cancelled");
        }
        persist();host.changed();
    }
    void stopAll() throws Exception {for(int i=0;i<jobs.length();i++)if(live(jobs.getJSONObject(i)))finish(jobs.getJSONObject(i),"Cancelled");persist();host.changed();}
    private void finish(JSONObject j,String reason) throws Exception {
        host.stopAttempts(j);j.put("state","done").put("detail",reason);due.remove(j.optString("id"));
    }
    void result(long messageId,String status,boolean meshcore,int repeats) throws Exception {
        if(status.startsWith("failed"))for(int i=0;i<jobs.length();i++){
            JSONObject j=jobs.getJSONObject(i);JSONArray ids=j.optJSONArray("messageIds");
            if(live(j)&&j.optBoolean("location")&&ids!=null&&ids.length()>0&&ids.optLong(ids.length()-1)==messageId){
                // A failed radio write must not make this fix the movement baseline.
                if(j.has("previousLatitude"))j.put("latitude",j.getDouble("previousLatitude")).put("longitude",j.getDouble("previousLongitude"));
                else{j.remove("latitude");j.remove("longitude");}
                j.put("detail","Position send failed — waiting for next interval");persist();host.changed();
            }
        }
        String success=PersistentPolicy.success(status,meshcore,repeats);if(success.isEmpty())return;
        for(int i=0;i<jobs.length();i++){
            JSONObject j=jobs.getJSONObject(i);if(!live(j)||j.optBoolean("location"))continue;
            JSONArray ids=j.getJSONArray("messageIds");for(int n=0;n<ids.length();n++)if(ids.getLong(n)==messageId){finish(j,success);persist();host.changed();break;}
        }
    }
    void incoming(String radio,String device,int channel,String peer,long stamp,long replyId,boolean historical) throws Exception {
        if(historical)return;
        String before=jobs.toString();
        for(int i=0;i<jobs.length();i++){
            JSONObject j=jobs.getJSONObject(i);
            if(!live(j)||j.optBoolean("location")||j.optInt("attempts")==0||!radio.equals(j.optString("radio"))||!device.equals(j.optString("nodeId"))||!peer.equals(j.optString("peer"))||peer.isEmpty()&&channel!=j.optInt("channel"))continue;
            JSONArray packets=j.getJSONArray("packetIds");boolean linked=false;
            for(int n=0;n<packets.length();n++)if(replyId!=0&&packets.getLong(n)==replyId)linked=true;
            if(linked)finish(j,"Answered by linked reply");
            else if(!peer.isEmpty()&&stamp/1000>=j.optLong("firstSent")/1000)j.put("answerAvailable",true);
        }
        if(!before.equals(jobs.toString())){persist();host.changed();}
    }
    void tick() {
        String before=jobs.toString();
        try {
            for(int i=0;i<jobs.length();i++) {
                JSONObject j=jobs.getJSONObject(i);if(!live(j))continue;
                if(j.optLong("expires")>0&&host.wall()>=j.optLong("expires")){finish(j,"Expired");continue;}
                if(!"active".equals(j.optString("state")))continue;
                String id=j.getString("id");long now=host.elapsed();
                if(now<due.getOrDefault(id,0L))continue;
                if(!j.optBoolean("location")&&PersistentPolicy.exhausted(j.optInt("attempts"),j.optInt("maxRetries",-1))){finish(j,"Maximum retries reached — delivery unconfirmed");continue;}
                String wait=host.waitReason(j);if(!wait.isEmpty()){j.put("detail",wait);continue;}
                if(host.busy(j)){due.put(id,now+PersistentPolicy.COMPOSER_WAIT);j.put("nextAt",host.wall()+PersistentPolicy.COMPOSER_WAIT).put("detail","Waiting for manual activity — checking again in 45 seconds");continue;}
                double[] fix=null;
                if(j.optBoolean("location")){
                    fix=host.location();if(fix==null){j.put("detail","Waiting for accurate location (≤25 m, ≤30 seconds old)");continue;}
                    if(j.has("latitude")){
                        double distance=distance(j.getDouble("latitude"),j.getDouble("longitude"),fix[0],fix[1]);
                        if(!PersistentPolicy.moved(distance)){j.put("detail","Stationary — less than 100 m from last sent position");continue;}
                    }
                }
                String text=fix==null?j.getString("text"):String.format(Locale.ROOT,"m:%.6f,%.6f|Location|poi",fix[0],fix[1]);
                // Write-ahead checkpoint: on process death this job restores paused.
                j.put("detail","Submitting to radio");persist();
                try {
                    long packet=host.send(j,text,fix);
                    remember(j.getJSONArray("packetIds"),packet);
                    j.put("attempts",j.optInt("attempts")+1).put("lastSent",host.wall());
                    if(!j.has("firstSent"))j.put("firstSent",host.wall());
                    if(fix!=null){
                        if(j.has("latitude"))j.put("previousLatitude",j.getDouble("latitude")).put("previousLongitude",j.getDouble("longitude"));
                        else{j.remove("previousLatitude");j.remove("previousLongitude");}
                        j.put("latitude",fix[0]).put("longitude",fix[1]);
                    }
                    j.put("detail",fix==null?"Waiting for mesh / delivery confirmation":"Position sent — waiting for interval and movement");
                } catch(Exception e){j.put("state","paused").put("detail","Paused: "+e.getMessage());}
                due.put(id,now+j.getLong("interval"));
                j.put("nextAt",host.wall()+j.getLong("interval"));
                persist();
            }
            if(!before.equals(jobs.toString())){persist();host.changed();}
        }catch(Exception e){
            // Fail closed if durable storage fails; never silently continue transmitting.
            for(int i=0;i<jobs.length();i++)try{JSONObject j=jobs.getJSONObject(i);if(live(j)){host.stopAttempts(j);j.put("state","paused").put("detail","Paused: could not save job state");}}catch(Exception ignored){}
            host.changed();
        }
    }
    static double distance(double a,double b,double c,double d){double x=Math.toRadians(c-a),y=Math.toRadians(d-b);double h=Math.sin(x/2)*Math.sin(x/2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.sin(y/2)*Math.sin(y/2);return 6371000*2*Math.asin(Math.sqrt(Math.min(1,h)));}
}
