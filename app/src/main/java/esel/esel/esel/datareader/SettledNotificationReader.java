package esel.esel.esel.datareader;

import android.app.Notification;
import android.os.Handler;
import android.os.Looper;

import java.util.Collections;
import java.util.List;

import esel.esel.esel.receivers.ReadReceiver;
import esel.esel.esel.util.SP;

public class SettledNotificationReader {
    // Eversense can post the previous value just before the updated reading.
    private static final long NOTIFICATION_QUIET_PERIOD_MS = 2_000L;
    private static final String PREF_KEY = "settle_eversense_notifications";
    private static volatile SGV lastReading;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final NotificationBurst burst = new NotificationBurst();
    private final ReadReceiver receiver;
    private final Runnable forwardReading = this::forwardReading;

    SettledNotificationReader(ReadReceiver receiver) {
        this.receiver = receiver;
    }

    public static boolean isEnabled() {
        return SP.getBoolean(PREF_KEY, false);
    }

    void onNotificationPosted(Notification notification) {
        String ticker = notification.tickerText.toString();
        SGV reading = new SGV(EsNotificationListener.parseTicker(ticker), notification.when, 0);
        if (burst.offer(reading)) {
            handler.removeCallbacks(forwardReading);
            handler.postDelayed(forwardReading, NOTIFICATION_QUIET_PERIOD_MS);
        }
    }

    void clear() {
        handler.removeCallbacks(forwardReading);
        burst.take();
    }

    private void forwardReading() {
        SGV reading = burst.take();
        if (reading == null || !isEnabled() || SP.getBoolean("use_patched_es", true)) {
            return;
        }

        SGV previous = lastReading;
        long fiveMin = 5 * 60 * 1000L;
        if (previous != null && (reading.timestamp <= previous.timestamp
                || (reading.raw == previous.raw && reading.timestamp - previous.timestamp < fiveMin))) {
            return;
        }

        lastReading = reading;
        receiver.CallBroadcast(null);
    }

    public static List<SGV> getData(long lastReadingTime) {
        SGV reading = lastReading;
        if (reading == null || reading.timestamp <= lastReadingTime) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new SGV(reading.raw, reading.timestamp, reading.record));
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
