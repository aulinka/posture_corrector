package sk.umb.bachelor.degree.posture_corrector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class BluetoothService extends Service {
    private static final String CHANNEL_ID = "BluetoothServiceChannel";
    private static final int NOTIFICATION_ID = 71;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private final IBinder binder = new BluetoothServiceBinder();

    private AppDatabase db;

    private PostureCorrectorDevice.Posture lastPosture = null;
    private LocalDateTime lastPostureChangeTime = null;

    private PostureCorrectorDevice device;

    public PostureCorrectorDevice getDevice() {
        return device;
    }

    public void disconnect() {
        device.disconnect();
    }

    public class BluetoothServiceBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    public static boolean isRunning(Context context) {
        return Utils.isServiceRunningInForeground(context, BluetoothService.class);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        notificationBuilder = createNotificationBuilder();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        startForeground(NOTIFICATION_ID, notificationBuilder.build());

        db = AppDatabase.getInstance(this);
        this.connect();

        return START_STICKY;
    }

    public void connect() {
        device = new PostureCorrectorDevice(this);
        device.connectToDevice("D4:D4:DA:44:28:86", postureCallback);
        broadcastConnectionChange();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private final PostureCorrectorDevice.PostureCorrectorDeviceCallback postureCallback = new PostureCorrectorDevice.PostureCorrectorDeviceCallback() {
        @Override
        public void onPostureChange(PostureCorrectorDevice.Posture posture) {
            super.onPostureChange(posture);
            if (lastPosture != null) {
                if (lastPosture != posture) {
                    Duration duration = Duration.between(lastPostureChangeTime, LocalDateTime.now());
                    DayStatisticDao dao = db.dayStatisticDao();
                    DayStatistic statistic = dao.getOrCreateToday();
                    if (lastPosture == PostureCorrectorDevice.Posture.POSTURE_HUNCHED) {
                        statistic.hunchedPostureDuration += duration.getSeconds();
                    }
                    if (posture == PostureCorrectorDevice.Posture.POSTURE_HUNCHED) {
                        statistic.hunchedCount++;
                    }
                    Log.d("onPostureChange",
                            (lastPosture == PostureCorrectorDevice.Posture.POSTURE_HUNCHED ? "Hunched" : "Straight") +
                            ", " + duration.getSeconds());
                    statistic.usageDuration += duration.getSeconds();
                    dao.insertOrUpdate(statistic);
                    lastPostureChangeTime = LocalDateTime.now();
                }
            } else {
                lastPostureChangeTime = LocalDateTime.now();
            }
            lastPosture = posture;
            updateNotification("Stav", posture == PostureCorrectorDevice.Posture.POSTURE_STRETCHED ? "Vystrety" : "Zhrbeny");
            Intent intent = new Intent("data-change");
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        }

        @Override
        public void onConnect() {
            super.onConnect();
            broadcastConnectionChange();
        }

        @Override
        public void onDisconnect() {
            super.onDisconnect();
            broadcastConnectionChange();
        }
    };

    void broadcastConnectionChange() {
        Intent intent = new Intent("connection-change");
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Bluetooth Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setVibrationPattern(new long[] { 0, 500 });
            channel.enableVibration(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    public void updateNotification(String title, String message) {
        if (notificationBuilder != null) {
            notificationBuilder.setContentTitle(title)
                    .setVibrate(new long[] { 0, 500 })
                    .setContentText(message);

            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
            }
        }
    }

    private NotificationCompat.Builder createNotificationBuilder() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bluetooth Service")
                .setContentText("Bluetooth Service is running")
                .setVibrate(new long[] { 0, 500 })
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setChannelId(CHANNEL_ID);
    }

    private Notification createNotification() {

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Bluetooth Service")
                .setContentText("Bluetooth Service is running")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setVibrate(new long[] { 0, 500 })
                .setChannelId(CHANNEL_ID);

        return builder.build();
    }
}
