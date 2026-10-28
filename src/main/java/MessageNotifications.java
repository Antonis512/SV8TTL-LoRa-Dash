package org.overmesh.android;

import android.app.*;
import android.content.Context;
import android.content.Intent;

/** Optional message previews, separate from the radio connection service. */
final class MessageNotifications {
    static final int ID=202;
    static final String CHANNEL="background_messages";
    static void createChannel(Context context) {
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Background messages",NotificationManager.IMPORTANCE_HIGH);
        // Message sounds are controlled by the existing MC/MT/DM sound settings.
        channel.setSound(null,null);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    static boolean allowed(Context context) {
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        NotificationChannel channel=manager.getNotificationChannel(CHANNEL);
        return manager.areNotificationsEnabled() && (channel==null || channel.getImportance()!=NotificationManager.IMPORTANCE_NONE);
    }
    static void cancel(Context context) {context.getSystemService(NotificationManager.class).cancel(ID);}
    static void show(Context context,String title,String text) {
        createChannel(context);
        if(!allowed(context))return;
        Intent open=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending=PendingIntent.getActivity(context,ID,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(context,CHANNEL)
                .setSmallIcon(R.drawable.ic_radio_unread).setContentTitle(title).setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(pending)
                .setAutoCancel(true).setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(Notification.VISIBILITY_PRIVATE).build();
        try {context.getSystemService(NotificationManager.class).notify(ID,notification);}
        catch(SecurityException denied) { /* Permission can change while the app is running. */ }
    }
}
