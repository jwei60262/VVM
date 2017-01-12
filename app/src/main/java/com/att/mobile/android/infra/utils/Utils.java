package com.att.mobile.android.infra.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.ContactObject;
import com.att.mobile.android.vvm.screen.WelcomeActivity;

public class Utils {
	private static final String TAG = "Utils";
	/**
	 * Watermark is a TextView Label with 45 degree added on the contentView
	 */
	public static void addDemoWatermark(Activity activity) {
		// fake id 12489634
		int watermarkId = 12489634;
		if (null == activity.findViewById(watermarkId)) {
			// Applying demo label
			RotateAnimation anim = new RotateAnimation(0, 45,
					Animation.RELATIVE_TO_SELF, 0.5f,
					Animation.RELATIVE_TO_SELF, 0.5f);
			anim.setDuration(0);
			anim.setFillAfter(true);
			TextView tv = new TextView(activity);
			tv.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
			tv.setGravity(Gravity.CENTER);
			tv.setFocusable(false);
			tv.setTextColor(Color.RED);
			tv.setTextSize(23f);
			tv.setTypeface(Typeface.DEFAULT_BOLD);
			tv.setText(activity.getString(R.string.demoWatermark) + " " + VVMApplication.getApplicationVersion());

			tv.setId(watermarkId);

			activity.addContentView(tv, new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					LinearLayout.LayoutParams.MATCH_PARENT));

			tv.startAnimation(anim);
		} else {
			activity.findViewById(watermarkId).invalidate();
		}
	}
	
	/**
	 * convert to E164
	 * @param phoneNumber
	 */
	public static String convertPhoneNumberToE164(String phoneNumber){
			if (phoneNumber.length() > 10
				&& !phoneNumber.startsWith("0")
				&& !phoneNumber.startsWith("+")) {
			phoneNumber = "+" + phoneNumber;
		}
		
		return phoneNumber;
	}
	
	public static String getCTNFromSim(){
		
		
		final TelephonyManager tMgr = (TelephonyManager) VVMApplication.getContext().getSystemService(
				Context.TELEPHONY_SERVICE);
		if (tMgr == null){
			Logger.i(TAG,"Can't get the MSISDN, can't get TelephonyManager service");
			return "";
		}

		Logger.d(TAG, "getMsisdn.  trying to extract from SIM");
		String msisdnNum = tMgr.getLine1Number();
		msisdnNum = removeCodeCountryFromMSISDN(msisdnNum);
		
		if(TextUtils.isEmpty(msisdnNum)){
			msisdnNum = "";
		}
		
		return msisdnNum;
	}

	/**
	 * Remove country code from the given MSISDN string
	 * 
	 * @param msisdnStr The string of MSISDN
	 * @return The MSISDN string without the country code
	 */
	public static String removeCodeCountryFromMSISDN(String msisdnStr) {
		if (!TextUtils.isEmpty(msisdnStr)) {
			if (msisdnStr.startsWith("+1")) {
				Logger.d(TAG, "The MSISDN number strat with +1");
				return msisdnStr.substring(2);
				// return msisdnStr.replace("+1", "");
			} else if (msisdnStr.startsWith("1")) {
				Logger.d(TAG, "The MSISDN number strat with 1");
				return msisdnStr.substring(1);
				// return msisdnStr.replace("1", "");
			}
		}
		return msisdnStr;
	}

	/**
	 * This method extracts from address the hostname
	 * @param url eg. http://some.where.com:8080/sync
	 * @return some.where.com
	 */
	public static String extractAddressFromUrl(String url) {
	    String urlToProcess = null;

	    //find protocol
	    int protocolEndIndex = url.indexOf("://");
	    if(protocolEndIndex>0) {
	        urlToProcess = url.substring(protocolEndIndex + 3);
	    } else {
	        urlToProcess = url;
	    }

	    // If we have port number in the address we strip everything
	    // after the port number
	    int pos = urlToProcess.indexOf(':');
	    if (pos >= 0) {
	        urlToProcess = urlToProcess.substring(0, pos);
	    }

	    // If we have resource location in the address then we strip
	    // everything after the '/'
	    pos = urlToProcess.indexOf('/');
	    if (pos >= 0) {
	        urlToProcess = urlToProcess.substring(0, pos);
	    }

	    // If we have ? in the address then we strip
	    // everything after the '?'
	    pos = urlToProcess.indexOf('?');
	    if (pos >= 0) {
	        urlToProcess = urlToProcess.substring(0, pos);
	    }
	    return urlToProcess;
	}
	public static boolean isWiFiOn(Context context){
	       final ConnectivityManager connMgr = (ConnectivityManager) context
	                .getSystemService(Context.CONNECTIVITY_SERVICE);
	
	        final android.net.NetworkInfo wifi = connMgr
	                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
	       return wifi.isAvailable() && wifi.isConnectedOrConnecting();
	}
	/**
	 * Return a "good" intent to launch a front-door activity in this package.
	 * This intent should have the same characteristics as the intent used by
	 * the launcher.
	 * 
	 * @param context
	 *            the context in which to create this intent
	 * @param shouldClearTop
	 *            should the flag FLAG_ACTIVITY_CLEAR_TOP be added to this intent
	 * @return and intent or null
	 */
	public static Intent getLaunchingIntent(Context context, boolean shouldClearTop) {
		try {
			
			PackageManager pm = context.getPackageManager();
			Intent launchingIntent =  pm.getLaunchIntentForPackage(context.getPackageName());

			Logger.d(TAG, "getLaunchingIntent");
			
			launchingIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
					| (shouldClearTop ? Intent.FLAG_ACTIVITY_CLEAR_TOP : 0));

			launchingIntent.setClass(context, WelcomeActivity.class);
			launchingIntent.setPackage(null);
			return launchingIntent;
		} catch (Exception e) {
			Logger.d(TAG, e.toString());
			return null;
		}
	}

	/**
	 * check if Mobie available, will also check the mobile data settings and airplane mode.
	 * 
	 * @return
	 */
	public static boolean isNetworkAvailable() {
	
		ConnectivityManager connectivityManager = (ConnectivityManager) VVMApplication.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
	
	}
	public static boolean isEmptyOrPrivateNum(String number) {
		String unknownString = VVMApplication.getContext().getResources().getString(R.string.unknownNumber);
		String privateNumber = VVMApplication.getContext().getResources().getString(R.string.privateNumber);
		return TextUtils.isEmpty(number) || number.equalsIgnoreCase(unknownString) || number.equalsIgnoreCase(privateNumber);
	}

	public static void showToast(int toastText, int length) {
		Toast.makeText(VVMApplication.getContext(), toastText, length).show();
	}

	public static void showToast(String toastText, int length) {
		Toast.makeText(VVMApplication.getContext(), toastText, length).show();
	}

	public static int getDefaultAvatarBackground(String phoneNumber) {

		int avatarIndex = Constants.AVATAR_IND.ORANGE.ordinal();
		if ( !TextUtils.isEmpty(phoneNumber) ) {
			int ind = phoneNumber.length() - 1;
			if ( ind >= 0 ) {
				char lastChar = phoneNumber.charAt(phoneNumber.length() - 1);
				if ( Character.isDigit(lastChar) ) {
					int lastCharInt = Character.getNumericValue(lastChar);
					avatarIndex =  Constants.AVATAR_FOR_LAST_NUMBER_ARR[lastCharInt];
				}
			}
		}
		return   Constants.DEFAULT_AVATAR_COLORS[avatarIndex];
	}



	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public static String getFormattedDisplayName(String phoneNumber ,ContactObject contactObject) {

		Context context = VVMApplication.getContext();

		String displayName = phoneNumber;
		if (contactObject != null) {
			displayName = contactObject.getDisplayName();
			if (displayName != null && !displayName.equals(phoneNumber)) {
				return displayName;
			}
		}
		if (Utils.isEmptyOrPrivateNum(displayName)) {
			return context.getString(R.string.privateNumber);
		}
		//At this point displayName is not null/empty for sure:
		if (!displayName.equals(context.getString(R.string.welcomeMessagePhoneNumber))) {
			//No need to format the Welcome to VVM number
			displayName = PhoneNumberUtils.formatNumber(displayName, context.getString(R.string.defaultCountryIso));
		}
		return displayName;
	}

}
