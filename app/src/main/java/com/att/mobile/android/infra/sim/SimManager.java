package com.att.mobile.android.infra.sim;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.Dispatcher;
import com.att.mobile.android.vvm.control.EventListener;
import com.att.mobile.android.vvm.control.IEventDispatcher;
import com.att.mobile.android.vvm.control.OperationsQueue;
import com.att.mobile.android.vvm.control.receivers.NotificationService;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.ACTIONS;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.Constants.KEYS;
import com.att.mobile.android.vvm.model.db.ModelManager;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

/**
 * This class is used mainly to retrieve Sim card properties, and to track Sim
 * card state changes. (Either real-time via registering a listener), or by
 * polling.
 * 
 * One of the most important methods in the class is
 * {@link SimManager#validateSim()} It compares the current sim ID, to a one
 * saved in a previous call to the method. The sim ID is saved encrypted in a
 * private SharedPreferences file to prevent user tampering.
 * 
 * If the user modifies the content of the file or deletes it, a Sim swap is
 * assumed.
 * 
 */
public class SimManager implements IEventDispatcher {

	private static final String ENCRYPTION_ALGORITHM = "SHA-1";

	private static final int EVENT_SERVICE_STATE_CHANGED = 1;

	private Context mContext;
	
	private Dispatcher dispatcher;

	private HandlerThread handlerThread;
	private SimManagerHandler handler;

	private static final String TAG = "SimManager";

	private static SimManager instance = null;

	private TelephonyManager mTelephonyManager;

	/**
	 * Private constructor - singleton
	 */
	private SimManager(Context context) {

		mContext = context;
		
		handlerThread = new HandlerThread("SimManager");
		handlerThread.start();

		handler = new SimManagerHandler(handlerThread.getLooper());

		dispatcher = new Dispatcher();

		mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        Logger.d(TAG, "CTOR");
	}

    public void startListening () {
        Logger.d(TAG, "startListening");
        mTelephonyManager.listen(new SimPhoneStateListener(), PhoneStateListener.LISTEN_SERVICE_STATE);
    }

	/**
	 * Returns the single SimManager instance
	 */
	public static final SimManager getInstance( Context context ) {
		if (instance == null) {
			instance = new SimManager(context);
		}
		return instance;
	}


    public void startSimValidation() {
        Logger.d(TAG, "startSimValidation()");
        handler.sendEmptyMessage(EVENT_SERVICE_STATE_CHANGED);
    }

	/**
	 * Validate the SIM. The SIM is considered valid if the queried SIM unique
	 * ID matches the one that was queried in a previous call to this method.
	 * 
	 * @return SimValidationResult
	 */
	public SimValidationResult validateSim() {
		int simState = getSimState();

		Logger.d(TAG, "validateSim() simState = " + simState);

		// Check if there's a sim in the device
		if (simState == TelephonyManager.SIM_STATE_ABSENT) {
			return new SimValidationResult(SimValidationResult.NO_SIM);
		}
		
		if ( ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ) {
			return new SimValidationResult(SimValidationResult.NO_PERMISSION);
		}

		// If the sim is not in 'ready' state, we cannot retrieve any of its
		// properties
		if (simState != TelephonyManager.SIM_STATE_READY) {
			return new SimValidationResult(SimValidationResult.NOT_READY);
		}

		String savedEncryptedSimId = getSavedEncryptedSimId();

		// We don't have a saved simId, this means one of the following:
		// 1. This is the first time validateSim has been called
		// 2. A (malicious) user has deleted the file
		if (savedEncryptedSimId == null) {
			saveEncryptedSimId();
			return new SimValidationResult(SimValidationResult.FIRST_SIM_USE);
		}
		String uniqueSimId = getUniqueSimId();

		if (uniqueSimId == null) {
			return new SimValidationResult(SimValidationResult.NOT_READY);
		}
		try {
			if (!savedEncryptedSimId.equals(encrypt(uniqueSimId))) {
				return new SimValidationResult(SimValidationResult.SIM_SWAPPED);
			}
		} catch (IllegalArgumentException Iae) {
			Log.e(TAG, "encrypt sim failed", Iae);
			return new SimValidationResult(SimValidationResult.NOT_READY);
		}

		return new SimValidationResult(SimValidationResult.SIM_VALID);
	}

	/**
	 * Returns the saved encrypted sim id or null if no saved id exists.
	 */
	private String getSavedEncryptedSimId() {
		String savedSimId = null;

		try {

			savedSimId = ModelManager.getInstance().getSharedPreferenceValue(
					Constants.KEYS.SIM_ID, String.class, null);

			Logger.d(TAG, "getSavedEncryptedSimId() Loaded encrypted Sim ID: "
                    + savedSimId);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}

		return savedSimId;
	}

	private void saveEncryptedSimId() {
		try {
			String simId = getUniqueSimId();

			Logger.d(TAG, "saveEncryptedSimId() Sim ID: " + simId);

			String encryptedSimId = encrypt(simId);

			Logger.d(TAG, "saveEncryptedSimId() Saving encrypted Sim ID: "
					+ encryptedSimId);

			ModelManager.getInstance().setSharedPreference(KEYS.SIM_ID,
					encryptedSimId);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private String encrypt(String input) {
		if (input == null) {
			throw new IllegalArgumentException(
					"encrypt() Cannot encrypt a null string");
		}
		try {
			MessageDigest algorithm = MessageDigest
					.getInstance(ENCRYPTION_ALGORITHM);
			algorithm.update(input.getBytes());
			byte[] encryptedOutput = algorithm.digest();

			// Convert to hexadecimal format and return
			BigInteger bi = new BigInteger(encryptedOutput);
			return bi.toString(16);
		} catch (NoSuchAlgorithmException e) {
			// Of course this should never happen. In a hypothetical situation
			// it does,
			// we consider it an unrecoverable error. This is why we throw an
			// error and not a
			// standard exception
			throw new Error("Failed to create encryption algorithm");
		}
	}

	/**
	 * This message returns what we consider a unique sim ID.
	 */
	private String getUniqueSimId() {
		return getSubscriberId();
	}

	/**
	 * Returns one of the following constants:
	 * TelephonyManager.SIM_STATE_UNKNOWN TelephonyManager.SIM_STATE_ABSENT
	 * TelephonyManager.SIM_STATE_PIN_REQUIRED
	 * TelephonyManager.SIM_STATE_PUK_REQUIRED
	 * TelephonyManager.SIM_STATE_NETWORK_LOCKED
	 * TelephonyManager.SIM_STATE_READY
	 * 
	 * Requires Permission: {@link android.Manifest.permission#READ_PHONE_STATE
	 * READ_PHONE_STATE}
	 * 
	 * @see android.telephony.TelephonyManager#getSimState
	 */
	public int getSimState() {
		return mTelephonyManager.getSimState();
	}

	/**
	 * Returns the unique subscriber ID, for example, the IMSI for a GSM phone.
	 * Return null if it is unavailable.
	 * 
	 * Requires Permission: {@link android.Manifest.permission#READ_PHONE_STATE
	 * READ_PHONE_STATE}
	 * 
	 * @see android.telephony.TelephonyManager#getSubscriberId
	 */
	public String getSubscriberId() {
		
		return mTelephonyManager.getSubscriberId();
		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.att.mobile.android.vvm.control.IEventDispatcher#addEventListener(com.att.mobile.android.vvm.control
	 * .EventListener)
	 */
	@Override
	public void addEventListener(EventListener listener) {

        dispatcher.addListener(listener);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.att.mobile.android.vvm.control.IEventDispatcher#removeEventListener(com.att.mobile.android.vvm.
	 * control.EventListener)
	 */
	@Override
	public void removeEventListener(EventListener listener) {
		dispatcher.removeListener(listener);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.att.mobile.android.vvm.control.IEventDispatcher#notifyListeners(int,
	 * com.att.mobile.android.vvm.control.Operation.StatusCode)
	 */
	@Override
	public void notifyListeners(int eventId, ArrayList<Long> messageIDs) {
		dispatcher.notifyListeners(eventId, messageIDs);
	}

	/**
	 * Clean stored user details and data TODO close cursors
	 */
	public void clearUserDataOnSimSwap() {
		Logger.d(TAG, "clearUserDeatilsOnSimSwap()");
		// clear all VVM notifications once encore is launched
		Intent intentService = new Intent(ACTIONS.ACTION_CLEAR_ALL_NOTIFICATIONS);
		intentService.setClass(VVMApplication.getContext(), NotificationService.class);
		VVMApplication.getContext().startService(intentService);

		ModelManager.getInstance().clearPreferences();

		// Clear queue
		OperationsQueue.getInstance().resetQueue();

		ModelManager.getInstance().deleteUnsavedMessages(false);

		// remove all pending to delete or mark as read messages and also delete
		// its backup files
		// so other users will not face a problem with the client trying to
		// perform an operation on a message that does not exists
//		ModelManager.getInstance().resetDeleteAndMarkAsReadPendingUIDs();
		
		// mark the rest of the messages as can be overwrite
		ModelManager.getInstance().markAllMessagesAsOverwrite();
	}




	@Override
	public void removeEventListeners() {
	}

	private class SimManagerHandler extends Handler {

		public SimManagerHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {

			if (msg.what == EVENT_SERVICE_STATE_CHANGED) {
				Logger.d(TAG, "handleMessage()");
				// Only notify listeners about the change if the current sim
				// status
				// is different than the previous one.
				SimValidationResult currResult = validateSim();

				Logger.d(TAG, "handleMessage() SimValidationResult = " + currResult);

				if (currResult.hashCode() == SimValidationResult.SIM_SWAPPED) {
					// remove the listener so that the sim manager will start listening again once the inbox activity will run again
					// and registen as a listener
                    ModelManager.getInstance().setSharedPreference(KEYS.SIM_SWAP, true);
					notifyListeners(EVENTS.SIM_SWAPED, null);

				} else if ( currResult.hashCode() == SimValidationResult.NO_PERMISSION) {
					// no action here. Don't check SIM swap.
				} else if (currResult.hashCode() != SimValidationResult.NOT_READY && currResult.hashCode() != SimValidationResult.NO_SIM) {
                    ModelManager.getInstance().setSharedPreference(KEYS.SIM_SWAP, false);
                    notifyListeners(EVENTS.SIM_VALID, null);
				}
			}

		}

	}
}
