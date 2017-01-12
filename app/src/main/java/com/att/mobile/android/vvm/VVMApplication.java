
package com.att.mobile.android.vvm;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import com.att.mobile.android.infra.sim.SimManager;
import com.att.mobile.android.infra.utils.BluetoothRouter;
import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.TimeDateUtils;
import com.att.mobile.android.vvm.control.ATTM.AttmUtils;
import com.att.mobile.android.vvm.control.OperationsQueue;
import com.att.mobile.android.vvm.control.receivers.LowMemoryReceiver;
import com.att.mobile.android.vvm.control.receivers.NotificationService;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.ACTIONS;
import com.att.mobile.android.vvm.model.db.ModelManager;

import java.lang.reflect.Field;

public class VVMApplication extends Application {

	private static final int CHECK_IF_ON_BACKGROUND = 0;

	private boolean isVisible = false;

	// holds a wake-lock for being able to keep device's screen ON
	private WakeLock mWakeLock = null;

	// holds whether a wake lock has been acquired
	private boolean mWakeLockLocked = false;

	/** holds whether the current audio mode is application's audio mode */
	private boolean isCurrentlyApplicationAudioMode = false;
	public boolean isCurrentlyApplicationAudioMode() {
		return isCurrentlyApplicationAudioMode;
	}

	public void setCurrentlyApplicationAudioMode(
			boolean isCurrentlyApplicationAudioMode) {
		this.isCurrentlyApplicationAudioMode = isCurrentlyApplicationAudioMode;
	}

	private static final String TAG = "VVMApplication";

	/** holds device's current audio mode, speaker state and music volume */
	private int deviceAudioMode = -1;
	private boolean isDeviceSpeakerOn;
	private int deviceMusicVolume = -1;

	/** holds application's spaker state (ON / OFF) - default is OFF */
	private boolean isApplicationSpeakerOn = false;

	/* holds a value that indicates if device's memory is low */
	private static boolean isMemoryLow = false;

	private AppHelperHandler appHelperHandler;

	private String clientId = null;

	private static String applicationVersion = null;

	private int applicationVersionCode;

	// if the application is release or debug mode to enable log prints and dev
	// settings screen
	private static boolean isDebugMode = false;
	private static Context appcontext;
	public static void setAppcontext(Context appcontext) {
		VVMApplication.appcontext = appcontext;
	}

	private static VVMApplication vvmapplication;
	public static Context getContext() {
		return appcontext;
	}

	
	public static VVMApplication getVvmapplication() {
		return vvmapplication;
	}

	public static void setVvmapplication(VVMApplication vvmapplication) {
		VVMApplication.vvmapplication = vvmapplication;
	}

	public static void setApplicationVersion(String applicationVersion) {
		VVMApplication.applicationVersion = applicationVersion;
	}

	public static void setDebugMode(boolean isDebugMode) {
		VVMApplication.isDebugMode = isDebugMode;
	}
	public static boolean isAdminUser(Context context)
    {
        UserHandle uh = Process.myUserHandle();
        UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
        if(null != um)
        {
            long userSerialNumber = um.getSerialNumberForUser(uh);
            Log.d("", "userSerialNumber = " + userSerialNumber);

            return 0 == userSerialNumber;
        }
        else
            return false;
    }

	@Override
	public void onCreate() {
		super.onCreate();

		setAppcontext(getApplicationContext());
		if(VVMApplication.vvmapplication == null){
			VVMApplication.setVvmapplication(VVMApplication.this);
		}
		// create model instance
		ModelManager.createInstance(getApplicationContext());


		// create queue instance
		OperationsQueue.createInstance(getApplicationContext());

		// gets the power manager and a wake-lock
		PowerManager mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "Flashlight");

		// initializes application's font utils
		FontUtils.initFonts(getApplicationContext());

		// creates the sim manager , it will starting listening when first
		// listener is registered
        SimManager simManager = SimManager.getInstance(getApplicationContext());
        if ( ModelManager.getInstance().getCurrentSetupState() != Constants.SETUP_STATUS.UNKNOWN ) {
            simManager.startListening();
            simManager.startSimValidation();
        }

		appHelperHandler = new AppHelperHandler();

		setApplicationVersionFromPackgeInfo();
		
		setClientId();

		// if the application is release or debug mode to enable log prints and
		// dev settings screen
		try {
			Bundle b = getPackageManager().getApplicationInfo(getPackageName(),
					PackageManager.GET_META_DATA).metaData;
			if (b != null) {
				Object val = b.get("debugMode");
				if (val != null) {
					VVMApplication.setDebugMode(((Boolean) val).booleanValue());
				}
			}
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}

		// bind to ATTM service to ask if installed and provisioned when the welcome activity starts
		AttmUtils.initAttmService();
		LowMemoryReceiver.registerReceiverDynamicly(this);
		
		BluetoothRouter.INSTANCE.loadConnectedDevices(this);
	}

	public static VVMApplication getInstance(){
		return vvmapplication;
	}
	@Override
	public void onTerminate() {
		LowMemoryReceiver.unregisterReceiverDynamicly(this);
		super.onTerminate();
	}

	public boolean isVisible() {
		return isVisible;
	}

	public void setVisible(boolean isVisible) {
		this.isVisible = isVisible;

		if (!isVisible) {
			// check if a new activity the on the isVisible flag or not
			// if no activity set that flag on after 1 second we can assume the
			// application is in the background
			appHelperHandler.sendMessageDelayed(
					appHelperHandler.obtainMessage(CHECK_IF_ON_BACKGROUND),
					700);
		} else {
			// refresh time format settings on resume of any activity
			TimeDateUtils.refreshDateFormat(getApplicationContext());
		}
	}

	/**
	 * Acquires a wake lock - keeps device's screen ON.
	 */
	public void acquireWakeLock() {
		if (!mWakeLockLocked) {
			mWakeLock.acquire();
			mWakeLockLocked = true;
		}
	}

	/**
	 * Releases a wake lock - allows device's screen to go OFF if needed.
	 */
	public void releaseWakeLock() {
		if (mWakeLockLocked) {
			mWakeLock.release();
			mWakeLockLocked = false;
		}
	}

	/**
	 * Sets application's audio mode, after storing device's current audio mode for it to later be restored. Setting
	 * application's audio mode includes setting application's speaker state (ON / OFF).
	 */
	public  void setApplicationAudioMode() {
		// in case the current audio mode is application's audio mode, do
		// nothing
		if (!isCurrentlyApplicationAudioMode) {

			// stores device's current audio mode and speaker state,
			// to be able to restore it when the playback will be stopped
			// gets device's audio manager
			AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			deviceAudioMode = audioManager.getMode();
			isDeviceSpeakerOn = audioManager.isSpeakerphoneOn();
			Logger.d(TAG,
					"VVMApplication.setApplicationAudioMode() - device's audio mode is :"
							+ deviceAudioMode);
			Logger.d(TAG,
					"VVMApplication.setApplicationAudioMode() - device's speaker is "
							+ (isDeviceSpeakerOn ? "ON" : "OFF"));

			// in case music is active in the device
			if (audioManager.isMusicActive()) {
				Logger.d(
						TAG,
						"VVMApplication.setApplicationAudioMode() - music is currently active in the device");

				// stores device's music volume to be able to restore it when the
				// playback will be stopped
				deviceMusicVolume = audioManager
						.getStreamVolume(AudioManager.STREAM_MUSIC);
				Logger.d(TAG,
						"VVMApplication.setApplicationAudioMode() - music turned OFF");
			} else {
				// the music volume has no meaning
				deviceMusicVolume = -1;
			}

			// sets audio manager's mode to be able to play the VVM stream
			// and sets application's speaker's mode
			// ICS & 4.1 support - set audio mode to MODE_IN_COMMUNICATION
			if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN) {
				try {
					// set audio mode with reflection to AudioManager.MODE_IN_COMMUNICATION = 3 (from API level 11)
					Field field = AudioManager.class.getField("MODE_IN_COMMUNICATION");
					int mode = field.getInt(audioManager);

					Logger.d(TAG,
							"VVMApplication.setApplicationAudioMode() - going to set audio mode " + mode);

					audioManager.setMode(mode);
				} catch (Exception e) {
					Log.e(TAG,
							"VVMApplication.setApplicationAudioMode() Error setting MODE_IN_COMMUNICATION", e);
				}
			} else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {   //Build.VERSION_CODES.JELLY_BEAN = 16
				audioManager.setMode(AudioManager.MODE_IN_CALL);
				Log.d(TAG, "VVMApplication.setPlayerAudioMode() - going to set audio mode IN_CALL");

			} else {
				audioManager.setMode(AudioManager.MODE_NORMAL);
			}

//		audioManager.setStreamSolo(AudioManager.STREAM_VOICE_CALL, true);

			// sets that current audio mode is application's audio mode
			isCurrentlyApplicationAudioMode = true;
		}
		if(isAccesibilityOn()){
			isApplicationSpeakerOn = true;
		}
		setIsApplicationSpeakerOn(isApplicationSpeakerOn);

//		// //////////////////////////////////////////////////////
//		if (!isApplicationSpeakerOn) {
//			BluetoothRouter.INSTANCE.startRouteAudioToBluetooth(this);
//		}
//		// //////////////////////////////////////////////////////
		Logger.d(
				TAG,
				"VVMApplication.setApplicationAudioMode() - application's audio mode is set (including last speaker state)");
	}

	/**
	 * Restores device's audio mode to the one that was before application launch.
	 */
	public  void restoreDeviceAudioMode() {
		// restores device's audio mode in case it not already active
		new Thread(new Runnable() {
			public void run() {
				AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				if (isCurrentlyApplicationAudioMode) {
					//			audioManager.setStreamSolo(AudioManager.STREAM_VOICE_CALL, false);
					audioManager.setMode(deviceAudioMode);
					audioManager.setSpeakerphoneOn(isDeviceSpeakerOn);

					// in case music was played while setting application's audio mode,
					// restores the music stream and its volume
					if (deviceMusicVolume != -1) {
						audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
								deviceMusicVolume, 0);
					}

					Logger.d(TAG,
							"VVMApplication.restoreDeviceAudioMode() isBluetoothScoOn = "
									+ audioManager.isBluetoothScoOn() + "  deviceAudioMode = "+ deviceAudioMode+ " isDeviceSpeakerOn = " +isDeviceSpeakerOn);
					BluetoothRouter.INSTANCE.stopRouteAudioToBluetooth(appcontext);

				}
				// ////////////////////////////////////////////////
				audioManager.setMode(AudioManager.MODE_NORMAL);
				// ////////////////////////////////////////////////

				// sets that current audio mode is device's audio mode
				isCurrentlyApplicationAudioMode = false;
				Logger.d(TAG,
						"VVMApplication.restoreDeviceAudioMode() - device's audio mode state restored");
			}
		}).start();
	}

	/**
	 * Sets whether the application should use the speaker for its audio output. In case the application is currently in
	 * application's audio mode, sets the new speaker state, otherwise just stores it for the next time application's
	 * audio mode will be set.
	 * 
	 */
	public  void setIsApplicationSpeakerOn(
			boolean isApplicationSpeakerOn) {
		
		if (isApplicationSpeakerOn){
			Logger.d(
					TAG,
					"VVMApplication.setIsApplicationSpeakerOn() - going to set application speaker state to ON" );
			}else{
				Logger.d(
						TAG,
						"VVMApplication.setIsApplicationSpeakerOn() - going to set application speaker state to OFF" );
			}
		
		this.isApplicationSpeakerOn = isApplicationSpeakerOn;

		// //////////////////////////////////////////////////////
		// if we want to turn speaker on - first we have to stop route to bluetooth headset
		if (isApplicationSpeakerOn) {
			BluetoothRouter.INSTANCE.stopRouteAudioToBluetooth(this);
		}
		// //////////////////////////////////////////////////////

		if (isCurrentlyApplicationAudioMode) {
			AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1){   // Build.VERSION_CODES.JELLY_BEAN_MR1 = 17
				audioManager.setMode(AudioManager.MODE_IN_CALL);
			}
			audioManager.setSpeakerphoneOn(isApplicationSpeakerOn);
			if (isApplicationSpeakerOn){
			Logger.d(
					TAG,
					"VVMApplication.setIsApplicationSpeakerOn() - application speaker state was set ON" );
			}else{
				Logger.d(
						TAG,
						"VVMApplication.setIsApplicationSpeakerOn() - application speaker state was set OFF" );
			}

			// ///////////////////////////////////////////////////////////////////////////////////////////////////
			// if we want to turn speaker off- we have to then route audio to bluetooth in case its connected
			if (!isApplicationSpeakerOn) {
				BluetoothRouter.INSTANCE.startRouteAudioToBluetooth(this);
			}
			// //////////////////////////////////////////////////////////////////////////////////////////////////
		}
	}

	public boolean isAccesibilityOn(){
		AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
		boolean isAccessibilityEnabled = am.isEnabled();
		boolean isExploreByTouchEnabled = am.isTouchExplorationEnabled();
		return isAccessibilityEnabled && isExploreByTouchEnabled;
	}
	/**
	 * Returns whether the application should use the speaker for its audio output.
	 * 
	 * @return (boolean) true in case the application should use the speaker for its audio output, false in case the
	 *         earphone should be used.
	 */
	public  boolean isApplicationSpeakerOn() {
		return isApplicationSpeakerOn;
	}

	public synchronized static void setMemoryLow(boolean isMemoryLow) {
		VVMApplication.isMemoryLow = isMemoryLow;
	}

	public synchronized static boolean isMemoryLow() {
		return isMemoryLow;
	}

	private static class AppHelperHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {

			if (msg.what == CHECK_IF_ON_BACKGROUND) {
				// check if a new activity the on the isVisible flag or not
				// if no activity set that flag on after 1 second we can assume
				// the application is in the background
				if (!getInstance().isVisible) {

					Logger.d(
							TAG,
							"VVMApplication went to background");

					if (getInstance().isApplicationSpeakerOn()) {
						// application's last speaker state must be reset to OFF
						// when
						// leaving the application)
						getInstance().setIsApplicationSpeakerOn(false);

						TimeDateUtils
								.refreshDateFormat(getInstance().getApplicationContext());

					}
					// app should check Attm status on the onResume method once it is foregrounded - we mark when app
					// goes to background and check attm status once app is back on foreground.
					ModelManager.getInstance().setCheckAttmStatusOnForeground(true);
				}
			} else {
				super.handleMessage(msg);
			}
		}
	}

	/**
	 * Clears notifications from the notifications bar
	 */
	public void clearNotification() {
		Intent intentService = new Intent(ACTIONS.ACTION_CLEAR_NOTIFICATION);
		intentService.setClass(this, NotificationService.class);
		this.startService(intentService);
	}

	/**
	 * update notification on the notifications bar according to new messages count in DB
	 */
	public void updateNotification() {
		Intent intentService = new Intent(ACTIONS.ACTION_UPDATE_NOTIFICATION);
		intentService.setClass(this, NotificationService.class);
		this.startService(intentService);
	}

	/**
	 * update notification on the notifications bar according to new messages count in DB after refresh we want the
	 * notification service to behave the same as after new message
	 */
	public void updateNotificationAfterRefresh() {
		Intent intentService = new Intent(
				ACTIONS.ACTION_UPDATE_NOTIFICATION_AFTER_REFRESH);
		intentService.setClass(this, NotificationService.class);
		this.startService(intentService);
	}

	/**
	 * Get package version, if not found return an empty string
	 */
	public static String getApplicationVersion() {

		return applicationVersion;
	}

	/**
	 * Get application version code,
	 */
	public int getApplicationVersionCode() {

		return applicationVersionCode;
	}

	public void setApplicationVersionFromPackgeInfo() {

		try {
			PackageInfo manager = getPackageManager().getPackageInfo(
					getPackageName(), 0);
			if (manager != null) {
				VVMApplication.setApplicationVersion(manager.versionName);
				applicationVersionCode = manager.versionCode;
			} else {
				VVMApplication.setApplicationVersion("");
			}

		} catch (NameNotFoundException e) {
			Log.e(TAG,
					"PreferencesActivity.setApplicationVersion() An exception was thrown while getting package info.",
					e);
		}
	}

	/**
	 * client id format is: <Client vendor>:<device model>-<android platform version>-<subscriber id (SIM unique
	 * identifier)>:<client build number>
	 * 
	 * @return
	 */
	public String getClientId() {
		if (clientId == null) {
			setClientId();
		}
		return clientId;
	}

	private void setClientId() {
		// Client vendor
		StringBuilder clientIdBuilder = new StringBuilder("ATTV:");

		// Device model
		String phoneModel = android.os.Build.MODEL;
		if (phoneModel != null && phoneModel.length() > 0) {
			clientIdBuilder.append(phoneModel);
			clientIdBuilder.append("/");
		}

		// Android version
		String AndroidVersion = android.os.Build.VERSION.RELEASE;
		if (AndroidVersion != null && AndroidVersion.length() > 0) {
			clientIdBuilder.append(AndroidVersion);
			clientIdBuilder.append(":");
		}


		// client build number
		clientIdBuilder.append(getApplicationVersion());

		clientId = clientIdBuilder.toString();
		Logger.d(TAG,	"VVMApplication.setClientId() - clientId = " + clientId );
	}

	/**
	 * if the application is release or debug mode to enable log prints and dev settings screen
	 * 
	 * @return
	 */
	public static boolean isDebugMode() {
		return isDebugMode;
	}
}
