package com.att.mobile.android.vvm.control.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.Dispatcher;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.db.ModelManager;

public class ConnectivityReceiver extends BroadcastReceiver{

	private static final String TAG = ConnectivityReceiver.class.getSimpleName();

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub

		if (intent == null || intent.getExtras() == null) {
			return;
		}

		Logger.d(TAG, "ConnectivityActionReceiver started");

		ModelManager modelManager = ModelManager.getInstance();

		
		
		boolean isConnected = Utils.isNetworkAvailable();
		boolean wasConnected = modelManager.getSharedPreferenceValue(Constants.SP_KEY_IS_CONNECTED_TO_INTERNET, Boolean.class, false);
		if (isConnected) {

			if (!wasConnected) {
				modelManager.setSharedPreference(Constants.SP_KEY_IS_CONNECTED_TO_INTERNET, isConnected);

				modelManager.notifyListeners(EVENTS.NETWORK_CONNECTION_RESTORED, null);
				
				// NotifyUtils.nofityConnectivityChangedToUI(true);

				Logger.d(TAG, "connectivity restored - refreshing from server");

			}

		} else {

			if (wasConnected) {
				modelManager.setSharedPreference(Constants.SP_KEY_IS_CONNECTED_TO_INTERNET, isConnected);

				Logger.d(TAG, "connectivity lost");

				modelManager.notifyListeners(EVENTS.NETWORK_CONNECTION_LOST, null);
				// NotifyUtils.nofityConnectivityChangedToUI(false);

			}
		}
	}
}
