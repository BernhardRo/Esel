package esel.esel.esel.datareader;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

import esel.esel.esel.util.SP;
import esel.esel.esel.receivers.ReadReceiver;

/**
 * Created by bernhard on 24-01-18.
 */
public class EsNotificationListener extends NotificationListenerService {

    private static List<SGV> lastReadings = new ArrayList<>();
    private static final ReadReceiver rr = new ReadReceiver();
    private static long lastProcessedTimestamp = 0; // Timestamp dell'ultima lettura elaborata

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {

        boolean use_patched_es = SP.getBoolean("use_patched_es", true);
        if (use_patched_es) {
            return;
        }
        if (sbn.getPackageName().equals("com.senseonics.gen12androidapp") ||
        sbn.getPackageName().equals("com.senseonics.androidapp") ||
        sbn.getPackageName().equals("com.senseonics.eversense365.us") ||
                sbn.getPackageName().contains("com.senseonics.")) {
            Notification notification = sbn.getNotification();
            if (notification != null && notification.tickerText != null) {
                try {
                    long currentTimestamp = notification.when;
                    long fiveMinutes = 300000; // 5 minuti in millisecondi

                    // Controlla se sono passati almeno 4 minuti dall'ultima lettura
                    if (currentTimestamp - lastProcessedTimestamp >= fiveMinutes * 0.8) { // 4 minuti

                        SGV sgv = generateSGV(notification, lastReadings.size());
                        if (sgv != null) {
                            lastReadings.add(sgv);
                            rr.CallBroadcast(null);
                            lastProcessedTimestamp = currentTimestamp; // Aggiorna il timestamp dell'ultima lettura elaborata
                        }
                    }

                } catch (NumberFormatException err) {
                    err.printStackTrace();
                }
            }
        }
    }
    public static List<SGV> getData(int number, long lastReadingTime) {
        List<SGV> result = new ArrayList<>();
        for (SGV reading : lastReadings) {
            //if(reading.timestamp > lastReadingTime){
            result.add(reading);
            //}
        }

        while (result.size() > number) {
            result.remove(0);
        }

        if (result.size() == number) {
            SGV last = lastReadings.get(lastReadings.size() - 1);
            lastReadings.clear();
            lastReadings.add(last);
        }

        return result;
    }
    public static SGV generateSGV(Notification notification, int record) {
        long timestamp = notification.when;
        String tickerText = (String) notification.tickerText;
        int value;
        if (tickerText.contains(".") || tickerText.contains(",")) { //is mmol/l
            float valuef = Float.parseFloat(tickerText);
            value = SGV.Convert(valuef);
        } else {
            value = Integer.parseInt(tickerText);
        }

        if (lastReadings.size() > 0) {
            long five_min = 300000l;
            SGV oldSgv = lastReadings.get(lastReadings.size() - 1);
            long lastreadingtime = oldSgv.timestamp; // SP.getLong("lastreadingtime_nl",timestamp);
            int lastreadingvalue = oldSgv.raw; //SP.getInt("lastreadingvalue_nl",value);
            if (value == lastreadingvalue && (lastreadingtime + (five_min * 1.1)) > timestamp) { // no new value // 5 min 30 secs grace time
                return null;
            }
        }

        // SP.putLong("lastreadingtime_nl",timestamp);
        // SP.putInt("lastreadingvalue_nl",value);

        return new SGV(value, timestamp, record);
    }
}