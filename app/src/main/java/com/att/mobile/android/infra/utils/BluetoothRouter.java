package com.att.mobile.android.infra.utils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.control.files.VvmFileUtils;

/**
 * Allow routing of audio to Bluetooth SCO devices. BluetoothRouter is an
 * extension of BroadcastReceiver and listen to
 * {@link AudioManager#ACTION_SCO_AUDIO_STATE_CHANGED}, applications using the
 * BluetoothRouter must specify a receiver in androidManifest.xml with the above
 * action. <receiver
 * android:name="com.att.mobile.android.infra.utils.BluetoothRouter">
 * <intent-filter> <action
 * android:name="android.media.SCO_AUDIO_STATE_CHANGED"/> </intent-filter>
 * </receiver>
 * 
 * @author ldavid
 */
public enum BluetoothRouter {
	INSTANCE;

	public static final String LOG_TAG = "BluetoothRouter";
	/**
	 * Used to handle BT devices connected to the handset.
	 */
	private BluetoothConnectionsHandler bluetoothConnectionsHandler;
	/**
	 * Used to handle routing timeout.
	 */
	private HandlerThread helperHandlerThread;
	/**
	 * used to notify routing success or timeout.
	 */
	private static Object bluetoothLockObject = new Object();

	public static final int START_ROUTE = 101;

	public static final int STOP_ROUTE = 102;

	public static final int ADD_CONNECTED_DEVICE = 103;

	public static final int REMOVE_CONNECTED_DEVICE = 104;

	public static final String EXTRA_DEVICE_ADDRESS = "deviceAddress";

	/**
	 * The actual time to wait untill releasing the wait for routing. message
	 * will be played right after this timeout is released. value is 2000
	 * millis.
	 */

	private Set<String> connectedDevices;

	private boolean routed = false;

	/**
	 * Starts the BluetoothRouter thread to monitor route timeout.
	 */
	private BluetoothRouter() {

		helperHandlerThread = new HandlerThread("BluetoothRouter");
		helperHandlerThread.start();
	}
	
	/**
	 * The device will be routed to the connected BlueTooth SCO headset if there
	 * is one connected and Bluetooth SCO is available and Bluetooth A2DP is
	 * off. if routing is not finish within 1 second a timeout is activated to
	 * finish the method and return to caller.
	 * 
	 * @param context
	 */
	public void startRouteAudioToBluetooth(final Context context) {
	
		AudioManager audioManager = (AudioManager) context
				.getSystemService(Context.AUDIO_SERVICE);

		Logger.d(LOG_TAG,
				"startRouteAudioToBluetooth() isBluetoothScoAvailableOffCall = "
						+ audioManager.isBluetoothScoAvailableOffCall());
		Logger.d(LOG_TAG, "startRouteAudioToBluetooth() isBluetoothScoOn = "
				+ audioManager.isBluetoothScoOn());
		Logger.d(LOG_TAG, "startRouteAudioToBluetooth() isBluetoothA2dpOn = "
				+ audioManager.isBluetoothA2dpOn());

		/**
		 * check that - there is a connected BlueTooth SCO headset and routing
		 * audio to SCO device is allowed and SCO is off and A2DP is off - for
		 * bluetooth devices supporting A2DP android will route audio
		 * automatically
		 */
		if (connectedDevices != null && connectedDevices.size() > 0
				&& audioManager.isBluetoothScoAvailableOffCall()
				&& !audioManager.isBluetoothScoOn()) {

			Logger.d(LOG_TAG,
					"routeAudioToBluetooth() going to route audio to Bluetooth device");

			audioManager.setBluetoothScoOn(true);
			try {
				audioManager.startBluetoothSco();
			} catch (NullPointerException e) {
				Log.e(LOG_TAG, "startBluetoothSco() failed. no bluetooth device connected.", e);
			}
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1){  // Build.VERSION_CODES.JELLY_BEAN_MR1 = 17
				audioManager.setMode(AudioManager.MODE_IN_CALL);
			}
//			bluetoothRouterReceiver = new BluetoothRouterReceiver();
//			IntentFilter filter = new IntentFilter(
//					AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED);
//			context.registerReceiver(bluetoothRouterReceiver, filter);
		}
	}

	/**
	 * Stop routing of audio to connected Bluetooth SCO device.
	 * 
	 * @param context
	 */
	public void stopRouteAudioToBluetooth(final Context context) {

		AudioManager audioManager = (AudioManager) context
				.getSystemService(Context.AUDIO_SERVICE);
			audioManager.setBluetoothScoOn(false);
		audioManager.stopBluetoothSco();
		routed = false;
	}

	/**
	 * Used by {@link BluetoothRouterReceiver} to notify the router that audio
	 * routing procees is in atate
	 * {@link AudioManager#SCO_AUDIO_STATE_CONNECTED}
	 */
	public void notifyRouted(Context context) {
		Logger.d(LOG_TAG, "notifyRouted()");
//		synchronized (bluetoothLockObject) {
//			routed = true;
//			AudioManager audioManager = (AudioManager) context
//					.getSystemService(Context.AUDIO_SERVICE);
//			audioManager.setBluetoothScoOn(true);
//			audioManager.startBluetoothSco();
//		}
	}

	/**
	 * Handle route timeout to avoid hanging and delays in callers while routing
	 * can take more than few seconds.
	 * 
	 * @author ldavid
	 */
	private class BluetoothConnectionsHandler extends Handler {

		public BluetoothConnectionsHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {

			if (msg.what == ADD_CONNECTED_DEVICE) {
				Logger.d(LOG_TAG,
						"BluetoothRouterHandler.handleMessage() ADD_CONNECTED_DEVICE");
				addConnectedDevice((Context) msg.obj,
						msg.getData().getString(EXTRA_DEVICE_ADDRESS));
			} else if (msg.what == REMOVE_CONNECTED_DEVICE) {
				Logger.d(LOG_TAG,
						"BluetoothRouterHandler.handleMessage() REMOVE_CONNECTED_DEVICE");
				removeConnectedDevice((Context) msg.obj, msg.getData()
						.getString(EXTRA_DEVICE_ADDRESS));
			} else {
				super.handleMessage(msg);
			}
		}
	}

	/**
	 * Keep track of connected bluetooth devices that supports audio. if such
	 * device has been connected and playback of a message is on then an attempt
	 * to route the message audio to the connected device will be done.
	 * 
	 * @param context
	 * @param deviceAddress
	 */
	private void addConnectedDevice(Context context, String deviceAddress) {
		try {
			synchronized (bluetoothLockObject) {
				if (connectedDevices == null) {
					loadConnectedDevices(context);
				}
				if (connectedDevices != null
						&& !connectedDevices.contains(deviceAddress)) {
					connectedDevices.add(deviceAddress);
				}
				saveConnectedDevices(context);
			}
		} catch (Exception e) {
			Log.e(LOG_TAG, e.getMessage(), e);
		}
	}

	/**
	 * Keep track of connected bluetooth devices that supports audio. when no
	 * more devices connected the route of audio is stopped.
	 * 
	 * @param context
	 * @param deviceAddress
	 */
	private void removeConnectedDevice(Context context, String deviceAddress) {
		try {
			synchronized (bluetoothLockObject) {
				if (connectedDevices == null) {
					loadConnectedDevices(context);
				}
				if (connectedDevices != null) {
					connectedDevices.remove(deviceAddress);
				}
				if (connectedDevices == null || connectedDevices.size() == 0) {
					if (routed) {
						stopRouteAudioToBluetooth(context);
						routed = false;
					}
				}
				saveConnectedDevices(context);
			}
		} catch (Exception e) {
			Log.e(LOG_TAG, e.getMessage(), e);
		}
	}

	/**
	 * store the connected devices set in persistant storage as a file.
	 * 
	 * @param context
	 */
	private void saveConnectedDevices(Context context) {
		// in case a mark as read operation is pending for execution
		boolean success;
		if (connectedDevices != null && !connectedDevices.isEmpty()) {

			Logger.d(LOG_TAG, "BluetoothRouterHandler.saveConnectedDevices() "
					+ connectedDevices.size() + " connected Bluetooth devices");

			success = VvmFileUtils.saveSerializable(context, connectedDevices,
					context.getString(R.string.connectedbtFile,
							"connectedbt.ser"));

			if (success) {
				Logger.d(LOG_TAG,
						"BluetoothRouterHandler.saveConnectedDevices() file saved");
			} else {
				Logger.d(LOG_TAG,
						"BluetoothRouterHandler.saveConnectedDevices() failed to save file");
			}

		} else {

			Logger.d(LOG_TAG,
					"BluetoothRouterHandler.saveConnectedDevices() no connected Bluetooth devices");

			success = VvmFileUtils.deleteInternalFile(context, context.getString(
					R.string.connectedbtFile, "connectedbt.ser"));

			if (success) {
				Logger.d(LOG_TAG,
						"BluetoothRouterHandler.saveConnectedDevices() file deleted");
			} else {
				Logger.d(LOG_TAG,
						"BluetoothRouterHandler.saveConnectedDevices() failed to delete file");
			}
		}
	}

	/**
	 * load the connected devices set from persistant storage.
	 */
	@SuppressWarnings("unchecked")
	public void loadConnectedDevices(Context context) {
		try {
			connectedDevices = (Set<String>) VvmFileUtils.loadSerializable(
					context, context.getString(R.string.connectedbtFile,
							"connectedbt.ser"));
		} catch (Exception e) {
			Log.e(LOG_TAG, e.getMessage(), e);
		}
		if (connectedDevices == null) {
			connectedDevices = Collections
					.synchronizedSet(new HashSet<String>());
		}
		Logger.d(LOG_TAG, "BluetoothRouterHandler.loadConnectedDevices() "
				+ connectedDevices.size() + " connected devices");
	}

	/**
	 * Allows components from outside the BluetoothRouter to report connection
	 * and disconnection of bluetooth devices.
	 * 
	 * @param eventId
	 * @param deviceAddress
	 */
	public void deviceConnectionStateChanged(int eventId, String deviceAddress,
			Context context) {
		if (bluetoothConnectionsHandler == null) {
			bluetoothConnectionsHandler = new BluetoothConnectionsHandler(
					helperHandlerThread.getLooper());
		}

		Message message = bluetoothConnectionsHandler.obtainMessage(eventId,
				context);
		Bundle bundle = new Bundle();
		bundle.putString(EXTRA_DEVICE_ADDRESS, deviceAddress);
		message.setData(bundle);
		bluetoothConnectionsHandler.sendMessage(message);
	}
}
