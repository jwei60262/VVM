
package com.att.mobile.android.vvm.control.ATTM;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.receivers.NotificationService;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.ACTIONS;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.Constants.KEYS;
import com.att.mobile.android.vvm.model.db.ModelManager;

public class AttmUtils {

	private static final String TAG = "AttmUtils";
	public static final String VVM_PCKG_NAME = "com.att.mobile.android.vvm";
	public static final String ATTM_PCKG_NAME = "com.att.android.mobile.attmessages";

	private static IRemoteVvmService remoteVvmService;
	private static ServiceConnection conn;

	static class IRemoteATTMessagesServiceConnection implements ServiceConnection {
		@Override
		public void onServiceConnected(ComponentName className,
				IBinder boundService) {
			remoteVvmService = IRemoteVvmService.Stub.asInterface((IBinder) boundService);
			Logger.d(TAG, "IRemoteATTMessagesServiceConnection onServiceConnected()");
			
			// run in another thread to avoid blocking of UI thread
			new Thread(new Runnable() {
				
				@Override
				public void run() {
					isUibReadyToReplaceLegacyVvm();
					ModelManager.getInstance().notifyListeners(EVENTS.ATTM_SERVICE_CONNECTED, null);
				}
			}).start();
			
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			remoteVvmService = null;
			Logger.d(TAG, "IRemoteVvmService onServiceDisconnected");
		}
	};
	
     
	public static synchronized void initAttmService() {
		try {
			if (remoteVvmService == null) {
				conn = new IRemoteATTMessagesServiceConnection();
	              Intent explicitIntent = new Intent();
	              explicitIntent.setComponent(new ComponentName("com.att.android.mobile.attmessages", "com.att.mobile.android.vvm.control.ATTM.RemoteVvmService"));
	              Log.d(TAG, "bindService explicitIntent");
	              boolean isServiceBound = VVMApplication.getContext().bindService(explicitIntent, conn, Context.BIND_AUTO_CREATE);
	              Log.d(TAG, "bindService isServiceBound=" + isServiceBound);
				}
			
		} catch (SecurityException e) {
			Log.e(TAG, "initAttmService() bind to ATTM service failed", e);
		}
	}

	public static void releaseAttmService() {
		VVMApplication.getContext().unbindService(conn);
		conn = null;
	}

	/**
	 * Call ATTM to see if it's installed and provisioned and update correspondingly. This function should be called
	 * whenever VVM application is launched
	 */
	public static boolean isUibReadyToReplaceLegacyVvm() {
		
		Logger.d(TAG, "isUibReadyToReplaceLegacyVvm() going to check with AT&T Messages");
		Boolean isUibReadyToReplaceLegacyVvm = false;

		if (isAttmInstalled()) {
			if (remoteVvmService != null) {
				try {
					isUibReadyToReplaceLegacyVvm = remoteVvmService.isUibReadyToReplaceLegacyVvm();

					ModelManager.getInstance().setAttmStatus(
							isUibReadyToReplaceLegacyVvm ? Constants.ATTM_STATUS.PROVISIONED
									: Constants.ATTM_STATUS.INSTALLED_NOT_PROVISIONED);
					Logger.d(TAG,
							"IsUibReadyToReplaceLegacyVvm() remoteVvmService.IsUibReadyToReplaceLegacyVvm returned "
									+ isUibReadyToReplaceLegacyVvm);
				} catch (RemoteException e) {
					Log.e(TAG,
							"IsUibReadyToReplaceLegacyVvm() call remoteVvmService.IsUibReadyToReplaceLegacyVvm() failed",
							e);
					ModelManager.getInstance().setAttmStatus(Constants.ATTM_STATUS.INSTALLED_NOT_PROVISIONED);
				}
			} else {
				Logger.d(TAG, "isUibReadyToReplaceLegacyVvm() ATTM is not connected, VVM will run as usual");
				initAttmService();
				isUibReadyToReplaceLegacyVvm = false;
				ModelManager.getInstance().setAttmStatus(Constants.ATTM_STATUS.INSTALLED_NOT_PROVISIONED);
			}
		} else {
			Logger.d(TAG, "isUibReadyToReplaceLegacyVvm() ATTM is not installed, VVM will run as usual");
			isUibReadyToReplaceLegacyVvm = false;
			ModelManager.getInstance().setAttmStatus(Constants.ATTM_STATUS.NOT_INSTALLED);
		}

		Logger.d(TAG, "IsUibReadyToReplaceLegacyVvm() result = " + isUibReadyToReplaceLegacyVvm);
		return isUibReadyToReplaceLegacyVvm;
	}

	/**
	 * check with package manager if AT&T Messages is installed.
	 * 
	 * @return
	 */
	private static Boolean isAttmInstalled() {
		
		boolean result = false;
		PackageManager pm = VVMApplication.getContext().getPackageManager();

		try {
			pm.getPackageInfo(ATTM_PCKG_NAME, PackageManager.GET_ACTIVITIES);
			Log.i(TAG, "isAttmInstalled() AT&T Messages is installed");
			result = true;
		} catch (PackageManager.NameNotFoundException e) {
			Log.i(TAG, "isAttmInstalled() AT&T Messages is NOT installed");
			result = false;
		}
		return result;
	        

	}
/**
 * launches ATTM application  to replcase vvm
 * 
 * @return
 */
	public static boolean launchATTMApplication() {

		Logger.d(TAG, "launchATTMApplication() going to start AT&T Messages");
		boolean result = false;
		try {
			
			// clear all VVM notifications once encore is launched
			Intent intentService = new Intent(ACTIONS.ACTION_CLEAR_ALL_NOTIFICATIONS);
			intentService.setClass(VVMApplication.getContext(), NotificationService.class);
			VVMApplication.getContext().startService(intentService);
			
			Intent intent = new Intent(Constants.ACTIONS.ACTION_LAUNCH_ATTM);
			VVMApplication.getContext().sendBroadcast(intent);		

			result = true;

		} catch (Exception e) {
			Log.e(TAG, "launchATTMApplication() - error starting AT&T Messages", e);
			result = false;
		}
		return result;
	}
	/**
	 * listens to ATTM package uninstall event and changes the attm status accordingly
	 */
	public static void handlePackageUninstall() {
		Logger.d(TAG, "handlePackageUninstall() changing attm status to NOT_INSTALLED");
		ModelManager.getInstance().setAttmStatus(Constants.ATTM_STATUS.NOT_INSTALLED);
		ModelManager.getInstance().setSharedPreference(KEYS.DO_NOT_SHOW_LAUNCH_ATTM_SCREEN, false);
	}

	/**
	 * listens to ATTM package install event and changes the attm status accordingly
	 */
	public static void handlePackageInstall() {
		Logger.d(TAG, "handlePackageUninstall() changing attm status to INSTALLED_NOT_PROVISIONED");
		ModelManager.getInstance().setAttmStatus(Constants.ATTM_STATUS.INSTALLED_NOT_PROVISIONED);		
		
		initAttmService();
	}
	
}
