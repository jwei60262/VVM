package com.att.mobile.android.infra.utils;

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.Time;

import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.model.Constants;

/**
 * Time utilities class used for formatting date/time.
 * @author mkoltnuk
 */
public class TimeDateUtils {

	public static final String LOG_TAG_TIMEDATE_UTILS = "TimeDateUtils";
	private static String dateFormatStr = Constants.BACKUP_DATE_FORMAT;
//	private static String timeFormatStr = Constants.TIME_FORMAT_24;
	private static String timeFormatStr = Constants.TIME_FORMAT_12;
	private static Time now = new Time(); // Performance optimization

	/**
	 * Create readable form of the specified time stamp.
	 * 
	 * Business logic:
	 * 					 Today     : Show time. 
	 *					 Yesterday : "Yesterday". - Not valid behave as Last week.
 	 *					 Last week : Day of week.  
	 *					 Otherwise : Show date. 
	 *	
	 * Uses the device time and date format setting.
	 */
	public static String getFriendlyDate(long date, Context context, Boolean shortFotmat) {
		String retVal = null;
		
		refreshDateFormat(context); // refresh formats
		
		now.setToNow();

		Time targetTime = new Time(); // Performance optimization
		targetTime.set(date);

		//This Year
		if (now.year == targetTime.year){
			//Today
			if(now.yearDay == targetTime.yearDay){
				retVal = shortFotmat ? getMessageTime(date) : context.getString(R.string.TodayText)+" "+getMessageTime(date);
			}
			//Yesterday
			else if(now.yearDay-1 == targetTime.yearDay){				
							String yesterdayStr = context.getString(R.string.YesterdayText); 
							retVal = shortFotmat ? yesterdayStr : yesterdayStr + " " + getMessageTime(date);
			}
			
			//Last week
			else if(targetTime.yearDay >= (now.yearDay - 7)){
				String dow =  (String) DateFormat.format(Constants.EXACT_DAY_OF_WEEK_FORMAT, date); // EEEE
				retVal = shortFotmat ? dow : dow + " " + getMessageTime(date);
				
			}else{//Rest of current year
				String tmpDate =  (String) DateFormat.format(getDateNoYear(), date);
				retVal = shortFotmat ? tmpDate : tmpDate + " " + getMessageTime(date);								
			}
		}		
		// more than a year ago
		else {
				String tmpDate = (String) DateFormat.format(fixDateSplitter(dateFormatStr), date);
				retVal = shortFotmat ? tmpDate : tmpDate + " " + getMessageTime(date);	
			
		}
		
		return retVal;
	}

	/**
	 * Return string representation time part of the given date
	 * 
	 * @param date Given date.
	 * 
	 * @return Time 
	 * 
	 */
	 public static String getMessageTime(long date) {
		String time = ((String) DateFormat.format(timeFormatStr, date));
		
		// work arround Android issue - returns lower case am/pm even when ask for upper case AM/PM
		if (timeFormatStr.equals(Constants.TIME_FORMAT_12) && timeFormatStr.endsWith("AA")){
			
			
			if (time.endsWith("am")){
				time = time.replace("am", "AM");
			}
			else if (time.endsWith("pm")){
				time = time.replace("pm", "PM");
			}
		}
		
		return time;
	}

	/**
	 * @param time
	 * @return
	 */
	public static String formatDuration(int time) {
		int minutes = time / 60;
		int seconds = time % 60;
		String minutesString = null;
		if (minutes < 10)
			minutesString = "0" + minutes;
		else
			minutesString = "" + minutes;
		String secondsString = null;
		if (seconds < 10)
			secondsString = "0" + seconds;
		else
			secondsString = "" + seconds;
		return minutesString + ":" + secondsString;
	}
	
	/**
	 * Initializes current device's date/time formats
	 * @param context
	 */
	public static void refreshDateFormat(Context context) {
		dateFormatStr = getDeviceDateFormat(context);
		timeFormatStr = getDeviceTimeFormat(context);
	}
	
	/**
	 * Gets device's time format. If not found a default HH:mm is set
	 * @param context
	 * @return
	 */
	private static synchronized String getDeviceTimeFormat(Context context){
		timeFormatStr = android.provider.Settings.System.getString(
				context.getContentResolver(),
				android.provider.Settings.System.TIME_12_24);
		
		if (timeFormatStr == null) {
			timeFormatStr = Constants.TIME_FORMAT_12; 
		}
		else {
			boolean b24 = !timeFormatStr.equals("12");
			timeFormatStr = b24 ? Constants.TIME_FORMAT_24 : Constants.TIME_FORMAT_12;
		}
		return timeFormatStr;
	}
	
	/**
	 * Gets device's date format. If not found a default DD/MM is set
	 * @param context
	 * @return
	 */
	private static synchronized String getDeviceDateFormat(Context context){
		dateFormatStr = android.provider.Settings.System.getString(
				context.getContentResolver(),
				android.provider.Settings.System.DATE_FORMAT);
		
		if (dateFormatStr == null || dateFormatStr.length() == 0) {
			dateFormatStr = Constants.BACKUP_DATE_FORMAT; // set default value to DD/MM
		}
		return dateFormatStr;
	}
	
	/**
	 * remove year from date format
	 * @return
	 */
	private static String getDateNoYear(){
		String tmpFormat = fixDateSplitter(dateFormatStr.replace("yyyy", ""));
		// make sure we have at least 6 chars (mm/dd/  or dd/mm/ or /mm/dd) 
		if (tmpFormat.length() >= 5){
			// after removing 'yyyy' from the date we need to fix the string
			tmpFormat = tmpFormat.startsWith("/") ? tmpFormat.substring(1) :
													tmpFormat.substring(0, tmpFormat.length() - 1);
		}
		// some devices has formats like "Sat, Dec 31, 2011" so when removing the year we should remove the ', ' as well 
		tmpFormat = tmpFormat.trim();
		if (tmpFormat.endsWith(",")){
			tmpFormat = tmpFormat.substring(0, tmpFormat.length()-1);
		}
		return tmpFormat;
	}	
	
	/**
	 * Changes the default date splitter which is '-' to '/'
	 * 
	 * @param date
	 * @return
	 */
	private static String fixDateSplitter(String date){
		return date.replace("-", "/");
	}
}