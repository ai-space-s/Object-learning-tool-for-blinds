package com.wmakerlab.objectlearningtool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

public class MediaProjectionService extends Service {

    private static final int NOTIFICATION_ID = 12345;
    private static final String CHANNEL_ID = "MediaProjectionChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        Notification.Action stopAction = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_delete),
                "Stop",
                createStopIntent()
        ).build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Mediapipe 화면 캡쳐 서비스")
                    .setContentText("화면 캡쳐 대기 중...")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .addAction(stopAction)
                    .build();

            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Media Projection Service",
                NotificationManager.IMPORTANCE_DEFAULT
        );

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    private PendingIntent createStopIntent() {
        Intent stopIntent = new Intent(this, MediaProjectionService.class);
        stopIntent.setAction("ACTION_STOP_SERVICE");
        return PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "ACTION_STOP_SERVICE".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
    }
}
