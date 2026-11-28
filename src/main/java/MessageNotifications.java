package org.overmesh.android;

import android.app.*;
import android.content.Context;
import android.content.Intent;

/** Optional message previews, separate from the radio connection service. */
final class MessageNotifications {
    static final int ID=202;
    static final String CHANNEL="background_messages";
    static void createChannel(Context context) {
        NotificationChannel channel=new NotificationChannel(CHANNEL,AppLanguage.text(context,"Background messages"),NotificationManager.IMPORTANCE_HIGH);
        // Message sounds are controlled by the existing MC/MT/DM sound settings.
        channel.setSound(null,null);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    private static final String NEW_NODES="mt_new_nodes";
    static void showNewNode(Context context,String device,String id,String name) {
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(NEW_NODES,AppLanguage.text(context,"New Node Seen"),NotificationManager.IMPORTANCE_DEFAULT));
        if(!manager.areNotificationsEnabled() || manager.getNotificationChannel(NEW_NODES).getImportance()==NotificationManager.IMPORTANCE_NONE)return;
        Intent open=new Intent(context,MainActivity.class)
                .setData(new android.net.Uri.Builder().scheme("loradash").authority("node").appendPath(device).appendPath(id).build())
                .putExtra("newNode",id).putExtra("receivingDevice",device)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification alert=new Notification.Builder(context,NEW_NODES).setSmallIcon(R.drawable.ic_radio)
                .setContentTitle(AppLanguage.text(context,"New Node Seen")).setContentText(name.equals(id)?id:name+" · "+id)
                .setContentIntent(pending).setAutoCancel(true).setCategory(Notification.CATEGORY_STATUS).build();
        try{manager.notify("new-node:"+device+":"+id,204,alert);}catch(SecurityException denied){}
    }
    static boolean allowed(Context context) {
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        NotificationChannel channel=manager.getNotificationChannel(CHANNEL);
        return manager.areNotificationsEnabled() && (channel==null || channel.getImportance()!=NotificationManager.IMPORTANCE_NONE);
    }
    static void cancel(Context context) {context.getSystemService(NotificationManager.class).cancel(ID);}
    static void show(Context context,String title,String text) {
        show(context,title,text,false);
    }
    static void showGeofence(Context context,String text,String radio,String node){show(context,AppLanguage.text(context,"Map geofence"),text,true,radio,node);}
    private static void show(Context context,String title,String text,boolean geofence){show(context,title,text,geofence,"","");}
    private static void show(Context context,String title,String text,boolean geofence,String radio,String node){
        createChannel(context);
        if(!allowed(context))return;
        int notificationId=geofence?203:ID;
        Intent open=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if(geofence)open.putExtra("geofenceHistory",true);
        PendingIntent pending=PendingIntent.getActivity(context,notificationId,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(context,CHANNEL)
                .setSmallIcon(R.drawable.ic_radio_unread).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(pending)
                .setAutoCancel(true).setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE);
        if(geofence){
            Intent exclude=new Intent(context,MainActivity.class).setData(new android.net.Uri.Builder().scheme("loradash").authority("geofence-exclude").appendPath(radio).appendPath(node).build())
                .putExtra("excludeGeofenceNode",node).putExtra("geofenceRadio",radio).putExtra("geofenceHistory",true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent action=PendingIntent.getActivity(context,0,exclude,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(null,AppLanguage.text(context,"Exclude node"),action).build());
        }
        Notification notification=builder.build();
        try {context.getSystemService(NotificationManager.class).notify(notificationId,notification);}
        catch(SecurityException denied) { /* Permission can change while the app is running. */ }
    }
}
