package com.att.mobile.android.vvm.control.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.VVMApplication;

/**
 * Receiver for system broadcasts regarding its storage status.
 * This receiver listens to DEVICE_STORAGE_LOW & DEVICE_STORAGE_OK and sets up 
 * a flag inside VVMApplication accordingly.
 * 
 * @author mkoltnuk
 *
 */
public class LowMemoryReceiver extends BroadcastReceiver
{
	private static final String TAG = "LowMemoryReceiver";
	private String action = null;
	
	//holds an instance of the class, used for dynamic registration of the receiver
	private static LowMemoryReceiver DYNAMIC_REG_INSTANCE = new LowMemoryReceiver();

	//holds an instance of the intent filter for this receiver, used for dynamic registration
	private static IntentFilter DYNAMIC_REG_INTENT_FILTER = new IntentFilter();

	//holds whether the broadcast receiver is already registered for receiving notifications
	private static boolean isReceiverRegistered = false;
	
	@Override
	public void onReceive(Context context, Intent intent)
	{
		Logger.d(TAG, "LowMemoryReceiver.onReceive() ACTION = " + intent.getAction());   		
		
		// get intent's action
		this.action = intent.getAction();
		
		// no relevant action to deal with
		if (this.action == null)
		{
			return;
		}
		
		// is it the action we're looking for?
		if(this.action.equals(Intent.ACTION_DEVICE_STORAGE_LOW) || 
		   this.action.equals(Intent.ACTION_DEVICE_STORAGE_OK))
		{
			// handle the broadcast
			handleMemoryFlag();
		}
	}

	/**
	 * Handles no memory system broadcasts
	 */
	private void handleMemoryFlag() 
	{
		Logger.d(TAG, "LowMemoryReceiver.handleNoMemory() action = " + action);   
		if (this.action.equals(Intent.ACTION_DEVICE_STORAGE_LOW))
		{
			VVMApplication.setMemoryLow(true);
		}
		else
		{
			VVMApplication.setMemoryLow(false);
		}
	}
	
	/**
	 * Registers this broadcast receiver dynamically to receive notifications regarding Low/OK memory status.
	 * 
	 * @param context (Context != null) application's context.
	 */
	public synchronized static void registerReceiverDynamicly(Context context)
	{
		//registers the receiver only in case it is not already registered and updates that it is registered
		if(!isReceiverRegistered)
		{
			// adds a filter (is such exists, its ignored)
			DYNAMIC_REG_INTENT_FILTER.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
			DYNAMIC_REG_INTENT_FILTER.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
			
			context.registerReceiver(DYNAMIC_REG_INSTANCE, DYNAMIC_REG_INTENT_FILTER);
			
			isReceiverRegistered = true;
			Logger.d(TAG, "registerReceiverDynamicly() LowMemoryReceiver has been dinamicly registered.");
		}
	}

	/**
	 * Unregisters the registered broadcast receiver dynamically from receiving notification regarding network state changes.
	 * 
	 * @param context (Context != null) application's context.
	 */
	public synchronized static void unregisterReceiverDynamicly(Context context)
	{
		//in case the receiver is already registered, unregisters it and updates that it is not registered anymore
		if(isReceiverRegistered)
		{
			context.unregisterReceiver(DYNAMIC_REG_INSTANCE);
			
			isReceiverRegistered = false;
			Logger.d(TAG, "unregisterReceiverDynamicly() LowMemoryReceiver has been dinamicly unregistered.");
		}
	}
}
