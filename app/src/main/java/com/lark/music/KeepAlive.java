package com.lark.music;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

public class KeepAlive extends Service {
    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int f, int id) {
        NotificationChannel ch = new NotificationChannel("lark", "Lark", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "lark")
            .setContentTitle("Lark")
            .setContentText("Music player is running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi).build();
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        return START_STICKY;
    }
}
