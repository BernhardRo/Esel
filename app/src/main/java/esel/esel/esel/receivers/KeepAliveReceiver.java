package esel.esel.esel.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import esel.esel.esel.Esel;
import esel.esel.esel.util.SP;

/**
 * Created by adrian on 04/08/17.
 */

public class KeepAliveReceiver extends BroadcastReceiver {

    public static final long REPEAT_TIME = 10 * 60 * 1000L;

private static final String TAG = "KeepAliveReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Esel:KeepAliveReceiver");
        wl.acquire();

        Log.d(TAG, "onReceive called");
        long lastReadReceiverCall = SP.getLong("readReceiver-called", -1L);

        if (lastReadReceiverCall + REPEAT_TIME < System.currentTimeMillis()){
            Esel.getsInstance().startReadReceiver();
        }
        wl.release();
    }



    public void setAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, KeepAliveReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        am.cancel(pi);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), REPEAT_TIME, pi);
    }

    public void cancelAlarm(Context context) {
        Intent intent = new Intent(context, KeepAliveReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
    }




}
