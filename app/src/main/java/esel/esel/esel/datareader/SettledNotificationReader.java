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
    private static final long NOTIFICATION_WINDOW_MS = 2_000L;
    private static final long SAME_VALUE_INTERVAL_MS = 5 * 60 * 1000L;
    private static final String PREF_KEY = "settle_eversense_notifications";
    private static final String LAST_TIMESTAMP_KEY = "last_settled_notification_time";
    private static final String LAST_RAW_VALUE_KEY = "last_settled_notification_raw";
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
        if (burst.offerAndStartWindow(reading, System.currentTimeMillis())) {
            handler.postDelayed(forwardReading, NOTIFICATION_WINDOW_MS);
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

        if (!isNewReading(reading, lastAcceptedReading())) {
            return;
        }

        if (SP.getBoolean("overwrite_bg", false)) {
            receiver.CallBroadcast(null);
            return;
        }

        SGV broadcastReading = new SGV(reading.raw, reading.timestamp, reading.record);
        if (receiver.CallBroadcast(null, broadcastReading)) {
            lastReading = reading;
            SP.sharedPreferences.edit()
                    .putLong(LAST_TIMESTAMP_KEY, reading.timestamp)
                    .putInt(LAST_RAW_VALUE_KEY, reading.raw)
                    .apply();
        }
    }

    private static SGV lastAcceptedReading() {
        if (lastReading != null) {
            return lastReading;
        }
        long timestamp = SP.getLong(LAST_TIMESTAMP_KEY, -1L);
        if (timestamp > 0) {
            return new SGV(SP.getInt(LAST_RAW_VALUE_KEY, -1), timestamp, 0);
        }
        // Older builds only saved the receiver's reading. It is raw when smoothing is off.
        if (!SP.getBoolean("smooth_data", false)) {
            timestamp = SP.getLong("lastReadingTime", -1L);
            int value = SP.getInt("lastReadingValue", -1);
            if (timestamp > 0 && value >= 39) {
                return new SGV(value, timestamp, 0);
            }
        }
        return null;
    }

    static boolean isNewReading(SGV reading, SGV previous) {
        return previous == null || (reading.timestamp > previous.timestamp
                && (reading.raw != previous.raw || reading.timestamp - previous.timestamp >= SAME_VALUE_INTERVAL_MS));
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

        boolean offerAndStartWindow(SGV reading, long currentTime) {
            // Use the same value and future timestamp limits as ReadReceiver.
            if (reading.value < 39 || reading.timestamp <= 0 || reading.timestamp > currentTime + 60_000L
                    || (pending != null && reading.timestamp < pending.timestamp)) {
                return false;
            }
            boolean startWindow = pending == null;
            pending = reading;
            return startWindow;
        }

        SGV take() {
            SGV reading = pending;
            pending = null;
            return reading;
        }
    }
}
