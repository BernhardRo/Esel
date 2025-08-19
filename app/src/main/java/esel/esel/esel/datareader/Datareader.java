package esel.esel.esel.datareader;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import esel.esel.esel.Esel;
import esel.esel.esel.R;
import esel.esel.esel.util.SP;

import static android.content.ContentValues.TAG;

/**
 * Created by bernhard on 18-11-03.
 */

public class Datareader {

    public static String uriGlucose = "content://com.senseonics.gen12androidapp.glucose";
    public static String uriTransmitter = "content://com.senseonics.gen12androidapp.transmitter";

    public static List<SGV> readDataFromContentProvider(Context context ) {
       return readDataFromContentProvider(context,  2,0);
    }

    public static List<SGV> readDataFromContentProvider(Context context, int number, long lastReadingTime)  {

        Uri uri                = Uri.parse(uriGlucose);;

        String[] selection = {"timestamp","glucoseLevel"};

        String where = null;
        String sortOder = "timestamp asc LIMIT " + number;
        if (lastReadingTime > 0) {
            where = "timestamp > " + (lastReadingTime-1L);
        }

        Cursor item   = context.getContentResolver().query(uri, null, where, null, sortOder);

        boolean reverseorder = false;

        if(item == null || item.getCount()==0){
            item   = context.getContentResolver().query(uri, null, null, null, "timestamp desc LIMIT 2");
            reverseorder = true;
        }

        item.moveToFirst();

        StringBuilder sb = new StringBuilder("");

        List<SGV> valueArray = new ArrayList<SGV>();

        do {
            String timestamp_str    = item.getString(0);
            String glucoseLevel_str   = item.getString(1);
                        String groupId = item.getString(2);
                        String recordNumber = item.getString(3);
            //           String glucoseRaw1    = item.getString(4);
            //           String glucoseRaw2   = item.getString(5);
            //           String glucoseRaw3 = item.getString(6);
            //           String glucoseRaw4 = item.getString(7);
            //          String glucoseRaw5    = item.getString(8);
            //           String glucoseRaw6   = item.getString(9);
            //           String glucoseRaw7 = item.getString(10);
            //           String glucoseRaw8 = item.getString(11);

            String line = timestamp_str + "," +glucoseLevel_str + "," + recordNumber;
            sb.append(timestamp_str + "," +glucoseLevel_str + "\n");

            SGV sgv = Datareader.generateSGV(line);

            valueArray.add(sgv);


            // Do work...
        } while (item.moveToNext());



        //       Cursor transmitter   = context.getContentResolver().query(Uri.parse(uriTransmitter), null, null, null, null);
//
//
        //       transmitter.moveToFirst();
        //       String id    = transmitter.getString(0);
        //       String name   = transmitter.getString(1);
        //       String address = transmitter.getString(2);
//        String status = transmitter.getString(3);
//        do {
//            // Do work...
//        } while (transmitter.moveToNext());

        if(reverseorder && valueArray.size()==2){
            valueArray.add(valueArray.get(0));
            valueArray.remove(0);
        }

        Log.d(TAG, "readDataFromContentProvider called, result = " + valueArray);

        return valueArray;
    }


    public static SGV generateSGV(String dataString){
        String[] tokens = dataString.split(",");
        long timestamp = Long.parseLong(tokens[0]);
        int value = Integer.parseInt(tokens[1]);
        int record = Integer.parseInt(tokens[2]);
        return new SGV(value, timestamp,record);
    }

}


