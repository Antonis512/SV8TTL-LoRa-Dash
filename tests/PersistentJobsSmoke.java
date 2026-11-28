package org.overmesh.android;

import org.json.*;

/** Runs against Android's real JSON implementation with a deterministic clock and fake radio. */
public final class PersistentJobsSmoke {
    static int assertions;
    static void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
    static final class Rig implements PersistentJobs.Host {
        long now=100000,wall=1700000000000L;int sends,stops;boolean busy,connected=true,failSave;double[] fix;String saved="[]";PersistentJobs jobs;
        Rig()throws Exception{jobs=new PersistentJobs(this,saved);}
        public long elapsed(){return now;}public long wall(){return wall;}
        public void save(String value)throws Exception{if(failSave)throw new Exception("disk full");saved=value;}
        public String waitReason(JSONObject j){return connected?"":"Disconnected";}
        public boolean busy(JSONObject j){return busy;}
        public double[] location(){return fix;}
        public long send(JSONObject j,String text,double[] fix)throws Exception{sends++;j.getJSONArray("messageIds").put(sends);return sends+100;}
        public void stopAttempts(JSONObject j){stops++;}public void changed(){}
        void advance(long ms){now+=ms;wall+=ms;jobs.tick();}
        JSONObject add(boolean location,int max,long expiry)throws Exception{
            JSONObject target=new JSONObject().put("radio","mt").put("nodeId","own").put("peer","peer").put("channel",-1);
            jobs.add(target,"hello",location,120000,expiry,max);return jobs.jobs.getJSONObject(jobs.jobs.length()-1);
        }
    }
    public static void main(String[] args)throws Exception{
        check(PersistentPolicy.fresh(31000,1000,25),"30 seconds and 25 m inclusive");
        check(!PersistentPolicy.fresh(31001,1000,25),"Stale fix rejected");
        check(!PersistentPolicy.fresh(31000,1000,25.01f),"Poor accuracy rejected");
        check(!PersistentPolicy.fresh(31000,32000,5),"Future timestamp rejected");
        check(!PersistentPolicy.fresh(31000,1000,Float.NaN),"Unknown accuracy rejected");
        check(!PersistentPolicy.moved(99.99)&&PersistentPolicy.moved(100),"100 m movement threshold");
        check(!PersistentPolicy.exhausted(50000,-1),"Retry limit optional");
        check(PersistentPolicy.success("sent_radio",true,0).isEmpty(),"Local radio acceptance is not mesh evidence");
        check(PersistentPolicy.success("accepted",false,0).isEmpty(),"MT queued is not delivered");
        check(PersistentPolicy.success("relayed",false,0).equals("Reached mesh"),"MT relay evidence stops retries");
        Rig r=new Rig();JSONObject j=r.add(false,2,0);r.jobs.tick();check(r.sends==1,"Initial send");
        r.advance(119999);check(r.sends==1,"No retry before two minutes");r.advance(1);check(r.sends==2,"Retry after two minutes");
        r.advance(120000);check(r.sends==3,"Two retries are three attempts");r.advance(120000);check(r.sends==3&&j.getString("state").equals("done"),"Limit exhausted");
        r=new Rig();j=r.add(false,-1,0);r.busy=true;r.jobs.tick();check(r.sends==0,"Manual activity wins");
        r.busy=false;r.advance(44999);check(r.sends==0,"45 second deferral retained");r.advance(1);check(r.sends==1,"Send after manual deferral");
        r.busy=true;r.advance(120000);r.jobs.result(1,"delivered",false,0);r.advance(45000);check(r.sends==1&&!PersistentJobs.live(j),"ACK during manual wait stops retry");
        r=new Rig();j=r.add(false,-1,90000);r.busy=true;r.jobs.tick();r.advance(90000);check(r.sends==0&&!PersistentJobs.live(j),"Expiry applies while composing");
        r=new Rig();j=r.add(false,-1,0);r.connected=false;r.jobs.tick();r.advance(600000);check(r.sends==0,"Disconnected sends nothing");r.connected=true;r.jobs.tick();check(r.sends==1,"Reconnect sends one, no catch-up burst");
        r.advance(120000);r.jobs.result(1,"delivered_mesh",false,0);check(!PersistentJobs.live(j),"Late ACK for earlier attempt completes job");
        r=new Rig();j=r.add(false,-1,0);r.jobs.tick();r.jobs.result(99,"delivered",false,0);check(PersistentJobs.live(j),"Unrelated ACK ignored");
        r.jobs.incoming("mc","own",-1,"peer",r.wall,101,false);check(PersistentJobs.live(j),"Other protocol reply ignored");
        r.jobs.incoming("mt","own",-1,"peer",r.wall-60000,0,false);check(!j.optBoolean("answerAvailable"),"Older message ignored");
        r.jobs.incoming("mt","own",-1,"peer",r.wall,0,false);check(j.optBoolean("answerAvailable")&&PersistentJobs.live(j),"Later unlinked message offers manual stop");
        r.jobs.action(j.getString("id"),"answered");check(!PersistentJobs.live(j)&&j.getString("detail").equals("Marked answered"),"Manual answer stops");
        r=new Rig();j=r.add(false,-1,0);r.jobs.tick();r.jobs.incoming("mt","own",-1,"peer",r.wall,101,true);check(PersistentJobs.live(j),"History does not auto-answer");
        r.jobs.incoming("mt","own",-1,"peer",r.wall,101,false);check(!PersistentJobs.live(j),"Linked reply stops automatically");
        r=new Rig();j=r.add(false,-1,0);r.jobs.tick();r.jobs.result(1,"sent_radio",true,1);check(!PersistentJobs.live(j),"Matching MC repeater evidence stops");
        r=new Rig();j=r.add(true,-1,0);r.jobs.tick();check(r.sends==0,"Wait for fresh fix");r.fix=new double[]{37.9,23.7};r.jobs.tick();check(r.sends==1,"Initial location");
        r.advance(120000);check(r.sends==1,"Stationary does not transmit");r.fix=new double[]{37.9005,23.7};r.jobs.tick();check(r.sends==1,"55 m does not transmit");
        r.fix=new double[]{37.901,23.7};r.jobs.tick();check(r.sends==2,"111 m sends after interval");r.fix=new double[]{37.903,23.7};r.advance(119999);check(r.sends==2,"Movement alone cannot bypass interval");
        r.busy=true;r.advance(1);check(r.sends==2,"Location yields to composing");r.fix=null;r.busy=false;r.advance(45000);check(r.sends==2,"Recheck fix after deferral");
        r.jobs.result(2,"delivered",false,0);check(PersistentJobs.live(j),"Location ACK does not stop tracking");
        r.jobs=new PersistentJobs(r,r.saved);j=r.jobs.jobs.getJSONObject(0);check(j.getString("state").equals("paused"),"Restart restores paused");r.fix=new double[]{38,24};r.advance(600000);check(r.sends==2,"No unsolicited restart send");
        r.jobs.action(j.getString("id"),"resume");r.jobs.tick();check(r.sends==2,"Resume retains minimum interval");r.advance(120000);check(r.sends==3,"Explicit resume continues");
        r.jobs.stopAll();r.advance(120000);check(r.sends==3&&!PersistentJobs.live(j),"Stop all cancels");
        r=new Rig();j=r.add(false,-1,0);r.failSave=true;r.jobs.tick();check(r.sends==0&&j.getString("state").equals("paused"),"Storage failure fails closed");
        r=new Rig();j=r.add(true,-1,0);r.fix=new double[]{37.9,23.7};r.jobs.tick();r.jobs.result(1,"failed: BLE write",false,0);
        check(!j.has("latitude"),"Failed initial write does not establish stationary baseline");r.advance(120000);check(r.sends==2,"Failed location can retry without moving");
        r.fix=new double[]{37.901,23.7};r.advance(120000);r.jobs.result(3,"failed: BLE write",false,0);check(j.getDouble("latitude")==37.9,"Failed later write restores last sent baseline");
        try{r.add(true,-1,0);throw new AssertionError("Duplicate job accepted");}catch(IllegalArgumentException expected){check(true,"Duplicate job rejected");}
        JSONArray ids=new JSONArray();for(int i=0;i<500;i++)PersistentJobs.remember(ids,i);check(ids.length()==256&&ids.getLong(0)==0&&ids.getLong(255)==499,"Bounded tracking retains original and latest attempts");
        r=new Rig();j=r.add(false,0,0);r.jobs.tick();r.advance(120000);check(r.sends==1&&!PersistentJobs.live(j),"Zero optional retries permits initial attempt only");
        System.out.println("PersistentJobsSmoke: "+assertions+" assertions passed");
    }
}
