package com.att.mobile.android.vvm.control.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.model.Constants.ACTIONS;

/**
 * This receiver is listening for BOOT_COMPLETED event by the system
 * and if a further action is needed, an Intent is sent to the NotificationService.
 * 
 * @author mkoltnuk
 */
public class BootReceiver extends BroadcastReceiver {

	private static final String TAG = "BootReceiver";
	
	@Override
	public void onReceive(Context context, Intent intent)
	{
		// is it the action we're looking for?
		if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED))
		{
			Logger.d(TAG, "Got the BOOT_COMPLETED event!");
			
			// handle the broadcast
			handleBootCompleted(context);
		}
	}

	/**
	 * Handles boot completed system broadcast
	 */
	private void handleBootCompleted(Context context) 
	{
		Logger.d(TAG, "handleBootCompleted: sending intent to notification service");
		
		// start service
		Intent notificationServiceIntent = new Intent(ACTIONS.ACTION_BOOT_COMPLETED);
		notificationServiceIntent.setClass(context, NotificationService.class);
		try {
			context.startService(notificationServiceIntent);
		} catch (Exception e) {
			Logger.d(TAG, "Exception starting Notification Service!: "+ e);
		}
	}
}
