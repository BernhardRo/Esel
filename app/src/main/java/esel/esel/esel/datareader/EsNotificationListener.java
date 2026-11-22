package esel.esel.esel.datareader;

import android.app.Notification;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Collections;
import java.util.List;

import esel.esel.esel.util.SP;
import esel.esel.esel.receivers.ReadReceiver;

/**
 * Created by bernhard on 24-01-18.
 */
public class EsNotificationListener extends NotificationListenerService {

    // Wait for Eversense to replace the previous value with the updated value.
    private static final long NOTIFICATION_QUIET_PERIOD_MS = 2_000L;
    private static volatile SGV lastReading;
    private static final ReadReceiver rr = new ReadReceiver();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final NotificationBurst burst = new NotificationBurst();
    private final Runnable forwardReading = this::forwardReading;
    
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        
        boolean use_patched_es = SP.getBoolean("use_patched_es", true);
        if(use_patched_es){
            return;
        }

        if (sbn.getPackageName().equals("com.senseonics.gen12androidapp") ||
                sbn.getPackageName().equals("com.senseonics.androidapp") ||
                sbn.getPackageName().equals("com.senseonics.eversense365.us") ||
                sbn.getPackageName().equals("com.senseonics.eversense365.ous") ||
                sbn.getPackageName().contains("com.senseonics.")
        ) {
            Notification notification = sbn.getNotification();
            if (notification != null && notification.tickerText != null) {
                try {
                    if (burst.offer(generateSGV(notification, 0))) {
                        handler.removeCallbacks(forwardReading);
                        handler.postDelayed(forwardReading, NOTIFICATION_QUIET_PERIOD_MS);
                    }
                } catch (NumberFormatException err) {
                    err.printStackTrace();
                }



            }
        }

    }

    private void forwardReading() {
        SGV reading = burst.take();
        if (reading == null || SP.getBoolean("use_patched_es", true)) {
            return;
        }

        SGV previous = lastReading;
        long fiveMin = 5 * 60 * 1000L;
        if (previous != null && (reading.timestamp <= previous.timestamp
                || (reading.raw == previous.raw && reading.timestamp - previous.timestamp < fiveMin))) {
            return;
        }

        lastReading = reading;
        rr.CallBroadcast(null);
    }

    @Override
    public void onListenerDisconnected() {
        handler.removeCallbacks(forwardReading);
        burst.take();
        super.onListenerDisconnected();
    }

    public static List<SGV> getData(long lastReadingTime) {
        SGV reading = lastReading;
        if (reading == null || reading.timestamp <= lastReadingTime) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new SGV(reading.raw, reading.timestamp, reading.record));
    }

    private static SGV generateSGV(Notification notification ,int record){
        long timestamp = notification.when;
        String tickerText = notification.tickerText.toString();
        int value;
        if(tickerText.contains("HI")){
            value =410;
        } else if(tickerText.contains(".") || tickerText.contains(",")){ //is mmol/l
            float valuef = Float.parseFloat(tickerText);
            value = SGV.Convert(valuef);
        }else{
            value =Integer.parseInt(tickerText);
        }

        return new SGV(value, timestamp, record);
    }

    static final class NotificationBurst {
        private SGV pending;

        boolean offer(SGV reading) {
            if (pending != null && reading.timestamp < pending.timestamp) {
                return false;
            }
            pending = reading;
            return true;
        }

        SGV take() {
            SGV reading = pending;
            pending = null;
            return reading;
        }
    }
}
