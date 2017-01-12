package com.att.mobile.android.infra.utils;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

/**
 * Listen to {@link AudioManager#ACTION_SCO_AUDIO_STATE_CHANGED} and notify
 * {@link BluetoothRouter} once state {@link AudioManager#EXTRA_SCO_AUDIO_STATE}
 * is {@link AudioManager#SCO_AUDIO_STATE_CONNECTED}.
 * 
 * @author ldavid
 * 
 */
public class BluetoothRouterReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Logger.d(BluetoothRouter.LOG_TAG, "BluetoothRouterReceiver.onReceive() action = "
				+ intent.getAction());
		if (intent.getAction().equals(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)) {
			int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_CONNECTED);
			Logger.d(BluetoothRouter.LOG_TAG,
			"BluetoothRouterReceiver.onReceive() state = " + state);
			// if we know headset is connected we can notify and keep going
			// to play the message
			if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {

				Logger.d(BluetoothRouter.LOG_TAG,
						"BluetoothRouterReceiver.onReceive() SCO_AUDIO_STATE_CONNECTED");
				BluetoothRouter.INSTANCE.notifyRouted(context);
			} /*else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
				Logger.d(BluetoothRouter.LOG_TAG,
						"BluetoothRouterReceiver.onReceive() SCO_AUDIO_STATE_DISCONNECTED");
				BluetoothRouter.INSTANCE.stopRouteAudioToBluetooth(context);
			}*/
		} else if (intent.getAction().equals(
				BluetoothDevice.ACTION_ACL_CONNECTED)) {
			BluetoothDevice device = intent
					.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

			BluetoothRouter.INSTANCE.deviceConnectionStateChanged(
					BluetoothRouter.ADD_CONNECTED_DEVICE, device.getAddress(),
					context);
		} else if (intent.getAction().equals(
				BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
			BluetoothDevice device = intent
					.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			BluetoothRouter.INSTANCE.deviceConnectionStateChanged(
					BluetoothRouter.REMOVE_CONNECTED_DEVICE,
					device.getAddress(), context);
		}
	}
}