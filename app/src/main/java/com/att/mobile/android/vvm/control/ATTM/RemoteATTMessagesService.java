
package com.att.mobile.android.vvm.control.ATTM;

import java.io.File;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.att.mobile.android.infra.utils.Crypto;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.files.VvmFileUtils;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.db.ModelManager;

/**
 * @author yhaguel Service provides ATTM application information about VVM application such as setup details
 */

public class RemoteATTMessagesService extends Service {
	private ModelManager modelManager = null;
	public final static String INTENT_BIND_ATTM_SERVICE = "com.att.action.BIND_ATTM_SERVICE";
	private final static String TAG = "RemoteATTMessagesService";
	
	

	@Override
	public void onCreate() {
		super.onCreate();
		ModelManager.createInstance(getApplicationContext());
		modelManager = ModelManager.getInstance();
		Log.d(TAG, "The AIDLMessageService was created.");
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "The AIDLMessageService was destroyed.");
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent intent) {

		return new IRemoteATTMessagesService.Stub() {

			@Override
			public boolean isLegacyVVMUibCompatible() throws RemoteException {
				return isATTMEnabled();
			}

			@Override
			public String getVVMConnectivityCredentials() throws RemoteException {
				return getSetupDetails();
			}

			@Override
			public byte[] getFileFromMessage(String fileName) throws RemoteException {
				return getFileBuffer(fileName);
			}

		};

	}

	byte[] getFileBuffer(String fileName) {
		Log.i(TAG, "getFileBuffer fileName: " + fileName);
		String fullFilePath = new StringBuilder(VVMApplication.getContext().getFilesDir().getPath())
				.append(File.separator).append(fileName).toString();
		return VvmFileUtils.getFileBytes(fullFilePath);
	}

	boolean isATTMEnabled() {
		return true;
	}

	/**
	 * get all VVM setup data: servr ,port, mailbox number as it saved on shared prefs.
	 * @return
	 */
	String getSetupDetails() {

		Log.i(TAG, "getSetupDetails");

		String setupDetails = null;
		// the complete JsonObjcet for sending to the sever
		JSONObject details = new JSONObject();

		try {
			// Please move key from key-value to interface.
			details.put(Constants.KEYS.ATTM_JSON_PASSWORD_KEY, modelManager.getPassword());
			details.put(Constants.KEYS.ATTM_JSON_HOST_KEY,
					modelManager.getSharedPreferenceValue(Constants.KEYS.PREFERENCE_HOST, String.class, ""));
			details.put(Constants.KEYS.ATTM_JSON_TOKEN_KEY,
					modelManager.getSharedPreferenceValue(Constants.KEYS.PREFERENCE_TOKEN, String.class, ""));
			details.put(Constants.KEYS.ATTM_JSON_MAILBOX_KEY,
					modelManager.getSharedPreferenceValue(Constants.KEYS.PREFERENCE_MAILBOX_NUMBER, String.class, ""));
			details.put(Constants.KEYS.ATTM_JSON_PORT_KEY,
					modelManager.getSharedPreferenceValue(Constants.KEYS.PREFERENCE_PORT, String.class, ""));
			details.put(Constants.KEYS.ATTM_JSON_SSL_PORT_KEY,
					modelManager.getSharedPreferenceValue(Constants.KEYS.PREFERENCE_SSL_PORT, String.class, ""));

			if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) { //Build.VERSION_CODES.JELLY_BEAN = 16
				setupDetails = Crypto.encrypt(VVMApplication.getContext().getPackageName() + Constants.KEYS.PKEY, details.toString(), Crypto.PROVIDER_CRYPTO);
			} else {
				setupDetails = Crypto.encrypt(VVMApplication.getContext().getPackageName() + Constants.KEYS.PKEY, details.toString(), null);
			}
		} catch (JSONException e) {
			Log.e(TAG, "Failed To Create JSON Object", e);
		}
		catch (Exception e) {
			Log.e(TAG, "Failed to get setup data", e);
		}
		return setupDetails;
	}

}
