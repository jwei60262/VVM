
package com.att.mobile.android.vvm.control.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.att.mobile.android.vvm.control.ATTM.AttmUtils;

/**
 * Receiver of changing in states of packages(e.g. removing, installing...)
 */
public class PackageStateChangedReceiver extends BroadcastReceiver {
	private String TAG = "PackageStateChangedReceiver";

	@Override
	public void onReceive(Context ctx, Intent intent) {
		final String action = intent.getAction();
		String packageName = getPackageName(intent.getDataString());

		if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
			// if you add more package name option - please change to factory instead of if else.
			if (packageName.equalsIgnoreCase(AttmUtils.ATTM_PCKG_NAME)) {
				Log.i(TAG, "PackageStateChangedReceiver.onReceive() attm application has been uninstalled");
				AttmUtils.handlePackageUninstall();
			}

		} else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
			if (packageName.equalsIgnoreCase(AttmUtils.ATTM_PCKG_NAME)) {
				Log.i(TAG, "PackageStateChangedReceiver.onReceive() attm application has been installed");
				AttmUtils.handlePackageInstall();
			}
		}
	}

	private String getPackageName(String data) {

		return data.substring(data.indexOf(":") + 1);

	}
}
