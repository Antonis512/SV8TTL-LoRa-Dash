package org.overmesh.android;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps active user-initiated radio sessions visible while the Activity is in the background. */
public final class RadioConnectionService extends Service {
    static java.lang.ref.WeakReference<MainActivity> owner=new java.lang.ref.WeakReference<>(null);
    static final int NOTIFICATION_ID=200;
    static final String CHANNEL="radio_connections";
    static Notification notification(android.content.Context context,String text,int unread) {
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Radio connections",NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(context,CHANNEL).setSmallIcon(unread>0?R.drawable.ic_radio_unread:R.drawable.ic_radio)
                .setColor(unread>0?0xff219653:0xff17695c).setColorized(unread>0)
                .setLargeIcon(unread>0?unreadIcon():null).setNumber(unread)
                .setContentTitle(unread>0?context.getString(R.string.app_name)+" · "+unread+" unread message"+(unread==1?"":"s"):context.getString(R.string.app_name)+" running").setContentText(text).setContentIntent(pending)
                .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).build();
    }
    private static android.graphics.Bitmap unreadIcon() {
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(96,96,android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(image);
        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new android.graphics.RadialGradient(48,48,47,new int[]{0xff55ee88,0xaa32bb65,0x0032bb65},null,android.graphics.Shader.TileMode.CLAMP));
        canvas.drawCircle(48,48,47,paint);paint.setShader(null);paint.setColor(0xff17653a);canvas.drawRoundRect(22,28,74,66,6,6,paint);
        paint.setColor(0xffffffff);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(4);
        canvas.drawLine(25,32,48,49,paint);canvas.drawLine(48,49,71,32,paint);return image;
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        String text=intent==null?"Radio connections active":intent.getStringExtra("text");
        Notification notification=notification(this,text,intent==null?0:intent.getIntExtra("unread",0));
        if(Build.VERSION.SDK_INT>=29) startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(NOTIFICATION_ID,notification);
        return START_NOT_STICKY;
    }
    @Override public void onTaskRemoved(Intent rootIntent) {
        MessageNotifications.cancel(this);
        MainActivity activity=owner.get();if(activity!=null)activity.closeFromRecents();
        stopForeground(STOP_FOREGROUND_REMOVE);
        getSystemService(NotificationManager.class).cancel(201);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }
    @Override public IBinder onBind(Intent intent) {return null;}
    @Override public void onDestroy() {stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
