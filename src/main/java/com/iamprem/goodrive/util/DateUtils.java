package com.iamprem.goodrive.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by prem on 8/22/15.
 */
public class DateUtils {

    public static String long2UTCString(long dateInMilliSec){

        Date date = new Date(dateInMilliSec);
        //'2012-06-04T12:00:00'
        SimpleDateFormat formatUTC = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        formatUTC.setTimeZone(TimeZone.getTimeZone("UTC"));
        String dateString = formatUTC.format(date);

        return dateString;
    }
}
