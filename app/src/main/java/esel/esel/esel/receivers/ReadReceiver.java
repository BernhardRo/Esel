package esel.esel.esel.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import org.json.JSONArray;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import esel.esel.esel.Esel;
import esel.esel.esel.LogActivity;
import esel.esel.esel.datareader.Datareader;
import esel.esel.esel.datareader.EsNotificationListener;
import esel.esel.esel.datareader.EsNowDatareader;
import esel.esel.esel.datareader.SGV;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.LocalBroadcaster;
import esel.esel.esel.util.SP;
import esel.esel.esel.util.ToastUtils;
import retrofit2.Call;

/**
 * Created by adrian on 04/08/17.
 */

public class ReadReceiver extends BroadcastReceiver {

    public static final long REPEAT_TIME = 1 * 20 * 1000L;

    private static final String TAG = "ReadReceiver";

    private boolean suppressBroadcast =false;
    JSONArray output = new JSONArray();

    @Override
    public synchronized void onReceive(Context context, Intent intent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Esel:ReadReceiver:Broadcast");//Broadcast
        wl.acquire();

        EselLog.LogV(TAG, "onReceive called");
        setAlarm(Esel.getsInstance());

        CallBroadcast(context);

        wl.release();

    }

    public void CallBroadcast(Context context){
        int sync = 8;
        try {

            sync = SP.getInt("max-sync-hours", sync);

        } catch (Exception e) {
            e.printStackTrace();
        }
        long syncTime = sync * 60 * 60 * 1000L;
        long currentTime = System.currentTimeMillis();

        try {

            SP.putLong("readReceiver-called", System.currentTimeMillis());


            //String datastring = Datareader.readData();


            long lastReadingTime = SP.getLong("lastReadingTime", currentTime);

            if (lastReadingTime + syncTime < currentTime) {
                lastReadingTime = currentTime - syncTime;
            }

            broadcastData(context, lastReadingTime, true);


        } catch (Exception e) {
            String msg = e.getMessage();
            ToastUtils.makeToast("Exception: " + msg);
            EselLog.LogE(TAG,msg);
        }


        //auto full sync in specific time intervals
        long autoSycInterval = SP.getInt("auto-sync-interval",3)* 60 * 60 * 1000L ;
        long lastFullSync = SP.getLong("last_full_sync", currentTime - autoSycInterval - 1);

        if(autoSycInterval > 0 && (currentTime - lastFullSync) > autoSycInterval){
            FullSync(context,sync);
        }

        boolean use_esdms = SP.getBoolean("use_esdms",false);
        if(use_esdms){
            EsNowDatareader.updateLogin();
        }

    }

    public void FullSync(Context context, int syncHours){
        long currentTime = System.currentTimeMillis();
        long syncTime = syncHours * 60 * 60 * 1000L;
        long lastTimestamp = currentTime - syncTime;


        boolean use_esdms = SP.getBoolean("use_esdms", false);
        if(use_esdms){

            class DataHandler implements EsNowDatareader.ProcessResultI{

                @Override
                public void ProcessResult(List<SGV> data) {
                    try {
                        int written = ProcesssValues( false, data);
                        String msg = "Full Sync done: Read " + written + " values from DB\n(last " + syncHours + " hours)";
                        //ToastUtils.makeToast(msg);
                        EselLog.LogI(TAG,msg,true);
                    }
                    catch(Exception e){
                        //ToastUtils.makeToast("No access to eversensedms");
                        EselLog.LogE(TAG,"No access to eversensedms",true);
                    }
                }
            }

            EsNowDatareader reader = new EsNowDatareader();
            reader.queryLastValues(new DataHandler(),syncHours);



        }else {

            //disable smoothing as historical data will be overwritten
            int written =  broadcastData(context, lastTimestamp, false);
            String msg = "Full Sync done: Read " + written + " values from DB\n(last " + syncHours + " hours)";
            //ToastUtils.makeToast(msg);
            EselLog.LogI(TAG,msg,true);
        }

        SP.putLong("last_full_sync", currentTime);



    }

    public void FullExport(Context context,File file, int syncHours){
        long currentTime = System.currentTimeMillis();
        long syncTime = syncHours * 60 * 60 * 1000L;
        long lastTimestamp = currentTime - syncTime;

        int written = 0;
        suppressBroadcast = true;
        boolean use_esdms = SP.getBoolean("use_esdms", false);

        if (use_esdms) {

            class DataHandler implements EsNowDatareader.ProcessResultI {

                @Override
                public void ProcessResult(List<SGV> data) {
                    int written = ProcesssValues( false,data);
                    String msg = "Full Sync done: Read " + written + " values from DB\n(last " + syncHours + " hours)";
                    //ToastUtils.makeToast(msg);
                    EselLog.LogI(TAG, msg,true);
                    WriteData(file,output.toString());
                    output = new JSONArray();
                    suppressBroadcast = false;

                }
            }

            EsNowDatareader reader = new EsNowDatareader();
            reader.queryLastValues(new DataHandler(), syncHours);

        }else {

            written = broadcastData(context, lastTimestamp, false);
            suppressBroadcast = false;
            WriteData(file,output.toString());
            output = new JSONArray();
            String msg = "Full Sync done: Read " + written + " values from DB\n(last " + syncHours + " hours)";
            //ToastUtils.makeToast(msg);
            EselLog.LogI(TAG, msg,true);

        }


        SP.putLong("last_full_sync", currentTime);

    }

    private void WriteData(File file,String data){

        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdir();
        }
        if (!file.getParentFile().canWrite()) {
            String msg = "Error: can not write data. Please enable the storage access permission for Esel.";
            //ToastUtils.makeToast(msg);
            EselLog.LogE(TAG, msg,true);
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
                FileWriter fileWriter = new FileWriter(file.getAbsoluteFile());
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                bufferedWriter.write(data.toString());
                bufferedWriter.close();

            } catch (IOException err) {
                String msg = "Error creating file: " + err.toString() + " occured at: " + err.getStackTrace().toString();
                //ToastUtils.makeToast(msg);
                EselLog.LogE(TAG, msg,true);
            }
        }

    }

    public int broadcastData(Context context, long lastReadingTime, boolean is_continuous_run) {
        int result = 0;
        try {

            long currentTime = System.currentTimeMillis();
            SP.putLong("readReceiver-called", System.currentTimeMillis());

            int size = 2;
            long updatedReadingTime = lastReadingTime;

            boolean use_patched_es = SP.getBoolean("use_patched_es", true);
            do {
                lastReadingTime = updatedReadingTime;

                List<SGV> valueArray = new ArrayList<>();

                if (SP.getBoolean("overwrite_bg", false)) { //send constant values e.g. for debugging purpose

                    //if(currentTime - lastReadingTime > 30000) {
                        int bg = SP.getInt("bg_value", 120);
                        SGV sgv = new SGV(bg, currentTime, 1);
                        valueArray.add(new SGV(bg, lastReadingTime, 1));
                        valueArray.add(new SGV(bg, currentTime, 2));

                    //}


                }else if (use_patched_es){
                    valueArray = Datareader.readDataFromContentProvider(context, size, lastReadingTime);

                    if (valueArray.size() == 0) {
                        //ToastUtils.makeToast("DB not readable!");
                        EselLog.LogE(TAG,"DB not readable!",true);
                    }
                }else {
                    boolean read_from_nl = true;
                    if(read_from_nl){
                        valueArray = EsNotificationListener.getData(size,lastReadingTime);
                    }
                }

                if (valueArray.size() != size) {
                    return result;
                }

                result +=  ProcesssValues(is_continuous_run,valueArray);

                updatedReadingTime = SP.getLong("lastReadingTime", lastReadingTime);
            } while (updatedReadingTime != lastReadingTime);

        } catch (android.database.CursorIndexOutOfBoundsException eb) {
            eb.printStackTrace();
            //ToastUtils.makeToast("DB is empty!\nIt can take up to 15min with running Eversense App until values are available!");
            EselLog.LogW(TAG,"DB is empty! It can take up to 15min with running Eversense App until values are available!",true);
        } catch (Exception e) {
            e.printStackTrace();
            SP.putInt("lastReadingValue", 120);
        }

        //wl.release();

        return result;
    }

    private int ProcesssValues( boolean is_continuous_run, List<SGV> valueArray) {
        int result = 0;

        long currentTime = System.currentTimeMillis();
        long fiveMin = 5 * 60 * 1000L;
        long fourMin = 4 * 60 * 1000L;

        for (int i = 0; i < valueArray.size(); i++) {
            SGV sgv = valueArray.get(i);
            long oldTime = SP.getLong("lastReadingTime", -1L);

            boolean acceptValue = oldTime != sgv.timestamp; //is new value

            if(sgv.timestamp - currentTime > (60 * 1000)){
                //sgv is from future
                long shiftValue = sgv.timestamp - currentTime;
                float sec = shiftValue/1000f;
                EselLog.LogW(TAG,"broadcastData called, value is in future by [sec] " + sec);
                acceptValue = false;
            }

            if(sgv.timestamp - oldTime < fourMin){
                EselLog.LogW(TAG,"broadcastData called, value ignored as it is not older than 4 min: " + sgv.value + ", timestamp: " + sgv.timestamp);
                acceptValue = false;
            }

            if (acceptValue) {
                //if (!futureValue) {
                int oldValue = sgv.value;

                oldValue = SP.getInt("lastReadingValue", -1);

                long sgvTime = sgv.timestamp;
                //check if old value is not older than 12min
                boolean hasTimeGap = (sgvTime - oldTime) > 12 * 60 *1000L;

                if (sgv.value >= 39 /*&& oldValue >= 39*/) { //check  for old value to ignore first 5 min
                    //ToastUtils.makeToast(sgv.toString());
                    if(is_continuous_run) {
                        boolean enable_smooth = SP.getBoolean("smooth_data", false) && !hasTimeGap;
                        sgv.smooth(oldValue, enable_smooth);
                    }

                    double slopeByMinute = 0d;
                    if (oldTime != sgvTime) {
                        slopeByMinute = (sgv.value - oldValue ) * 60000.0d / (( sgvTime - oldTime) * 1.0d);
                    }
                    if(!hasTimeGap){
                        sgv.setDirection(slopeByMinute);
                    }

                    try {
                        if (!suppressBroadcast) {
                            LocalBroadcaster.broadcast(sgv,is_continuous_run);
                        } else {
                            LocalBroadcaster.addSgvEntry(output, sgv);
                        }

                        result++;
                    }
                    catch(Exception e){
                        EselLog.LogE(TAG,"LocalBroadcaster.broadcast exception, result = " + e.getMessage());
                    }
                } else {
                    ToastUtils.makeToast("NOT A READING!");
                }
                SP.putLong("lastReadingTime", sgvTime);
                SP.putInt("lastReadingValue", sgv.value);
                //SP.putFloat("lastReadingDirection", slopeByMinute);
            }
        }

        return result;
    }


    public void setAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, ReadReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        /*try {
            pi.send();
        } catch (PendingIntent.CanceledException e) {
        }*/
        am.cancel(pi);
        am.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + REPEAT_TIME, pi);
    }

    public void cancelAlarm(Context context) {
        Intent intent = new Intent(context, ReadReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
    }


}
