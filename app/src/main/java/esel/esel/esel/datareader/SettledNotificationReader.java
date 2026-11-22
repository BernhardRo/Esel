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

        SGV previous = lastReading;
        long previousTime = previous == null ? SP.getLong("lastReadingTime", -1L) : previous.timestamp;
        // The receiver already saves the value before smoothing.
        int previousValue = previous == null ? SP.getInt("lastReadingRaw", -1) : previous.value;
        if (!canForward(reading, previousTime, previousValue)) {
            return;
        }

        if (SP.getBoolean("overwrite_bg", false)) {
            receiver.CallBroadcast(null);
            return;
        }

        SGV broadcastReading = new SGV(reading.raw, reading.timestamp, reading.record);
        if (receiver.CallBroadcast(null, broadcastReading, hasChangedValue(reading, previousValue))) {
            lastReading = reading;
        }
    }

    static boolean hasChangedValue(SGV reading, int previousValue) {
        return previousValue >= 39 && reading.value != previousValue;
    }

    static boolean canForward(SGV reading, long previousTime, int previousValue) {
        return reading.timestamp > previousTime && (reading.value != previousValue
                || reading.timestamp - previousTime >= SAME_VALUE_INTERVAL_MS);
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
