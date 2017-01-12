package com.att.mobile.android.vvm.control;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.widget.Toast;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.db.ModelManager;

import java.util.ArrayList;


/**
 * Created by hginsburg on 22/07/2015.
 */
public class SetupController implements EventListener {

    private static final String TAG = "SetupController";

    private static SetupController _instance;

    private Context _context;
    private TelephonyManager telephonyManager = null;
    private ModelManager modelManager;
    private OnSetupCallbackListener _callback;
    private static SetupCallBackHandler setupCallBackHandler;
	private boolean isTC = false;

    private String mailboxNumber;

    public void setMailboxNumber(String mailboxNumber) {
		this.mailboxNumber = mailboxNumber;
	}

	private static final int WAIT_FOR_SMS_TIMER = 1;

    // the time the counter to wait fir SMS has started
    private long timerStartTime;
    private int waitMaxTime;

    private static String stateString = "N/A";

    public static synchronized SetupController getInstance( Context context ) {
        if ( _instance == null ) {
            _instance = new SetupController(context);
        }
        return _instance;
    }

    private SetupController ( Context context ) {

        _context = context;
        modelManager = ModelManager.getInstance();
        setupCallBackHandler = new SetupCallBackHandler();

        modelManager.addEventListener(this);

        waitMaxTime = ModelManager.getInstance().getSharedPreferenceValue(Constants.KEYS.PREFERENCE_TIMER_DEFAULT, Integer.class, context.getString(R.string.accountSetupWaitTime) != null ?
                Integer.parseInt(context.getString(R.string.accountSetupWaitTime).trim()) : Integer.parseInt("60"));

    }

    public void close() {
        modelManager.removeEventListener(this);
        setupCallBackHandler = null;
        _instance = null;
    }

    private class SetupCallBackHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {

            Logger.i("SetupCallBackHandler", "handleMessage msg.what=" + msg.what);
            if (msg.what == WAIT_FOR_SMS_TIMER) {

                int currentState = modelManager.getCurrentSetupState();
                Logger.i("SetupCallBackHandler", "handleMessage currentState=" + currentState + " " + Constants.getSetupStatusString(currentState));
                switch (currentState) {
                    case Constants.SETUP_STATUS.WAIT_BINARY_SMS1:
                        modelManager.setCurrentSetupState(Constants.SETUP_STATUS.TRY_MO_SMS_AGAIN);
                        break;
                    case Constants.SETUP_STATUS.WAIT_BINARY_SMS2:
                        //modelManager.setCurrentSetupState(Constants.SETUP_STATUS.CALL_VOICE_MAIL);
                        modelManager.setCurrentSetupState(Constants.SETUP_STATUS.SHOW_CALL_VOICE_MAIL);
                        break;
                    case Constants.SETUP_STATUS.WAIT_CALL_BINARY_SMS:
                        //modelManager.setCurrentSetupState(Constants.SETUP_STATUS.CALL_VOICE_MAIL);
                        modelManager.setCurrentSetupState(Constants.SETUP_STATUS.SHOW_CALL_VOICE_MAIL);
                        break;
                    default:
                        break;
                }

                callUiCallback();
                setTimerWorking(false);

            } else {
                super.handleMessage(msg);
            }
        }

    }

    private void callUiCallback() {
        if ( _callback != null ) {
            _callback.onSetupStateChange();
        }
    }

    /**
     * Call back interface
     */
    public interface OnSetupCallbackListener {

        public void onSetupStateChange();
    }

    public void registerCallback ( OnSetupCallbackListener callback ) {
        Logger.i(TAG, "registerCallback callback=" + callback);
        _callback = callback;
    }

    public void unregisterCallback() {
        Logger.i(TAG, "unregisterCallback");
        _callback = null;
    }

    public synchronized void handleSetup() {

        int currentState = modelManager.getCurrentSetupState();
        boolean needUiCallBack =false;
        Logger.i(TAG, "handleSetup currentSetupState=" + currentState + " " + Constants.getSetupStatusString(currentState));

        switch ( currentState ) {
            case Constants.SETUP_STATUS.UNKNOWN:
                Logger.i(TAG, "mailboxNumber=" + mailboxNumber);
                if (TextUtils.isEmpty(mailboxNumber)) {
                    modelManager.setCurrentSetupState(Constants.SETUP_STATUS.INIT_CALL_VOICE_MAIL);
                } else {
                    modelManager.setCurrentSetupState(Constants.SETUP_STATUS.INIT_WITH_MSISDN);
                }
                return;

            case Constants.SETUP_STATUS.WAIT_BINARY_SMS1:
            case Constants.SETUP_STATUS.WAIT_BINARY_SMS2:
                sendMOSMS();
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    Utils.showToast(_context.getString(R.string.mosmsToast),Toast.LENGTH_LONG);
                }
                break;

            case Constants.SETUP_STATUS.CALL_VOICE_MAIL:
            case Constants.SETUP_STATUS.INIT_CALL_VOICE_MAIL:
                if (callVoicemail()) {
                    modelManager.setCurrentSetupState(Constants.SETUP_STATUS.WAIT_CALL_BINARY_SMS);
                    needUiCallBack = false;
                } else {
                    modelManager.setCurrentSetupState(Constants.SETUP_STATUS.NO_VOICE_MAIL_NUMBER);
                    needUiCallBack = true;
                }
                break;
            case Constants.SETUP_STATUS.NO_VOICE_MAIL_NUMBER:
                needUiCallBack = true;
            default:
                break;
        }

        if ( needUiCallBack ) {
            callUiCallback();
        }
    }

    /**
     * Call voicemail
     */
    private boolean callVoicemail() {
        Logger.d(TAG, "callVoicemail");
        // we have the permission for sure but the code - _context.startActivity(it); requires that we check for the permission
        if ( ContextCompat.checkSelfPermission(_context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED ) {
            TelephonyManager manager = (TelephonyManager) _context.getSystemService(Context.TELEPHONY_SERVICE);
            String voicemailNum = manager.getVoiceMailNumber();
            //voicemailNum = null; // TMP Test

            if ( !TextUtils.isEmpty(voicemailNum) ) {
                Logger.d(TAG, "callVoicemail => calling number " + voicemailNum);
                // call voicemail
                Uri uri = Uri.parse("tel:" + voicemailNum);
                Intent it = new Intent(Intent.ACTION_CALL, uri);
                _context.startActivity(it);
                return true;
            } else {
                Logger.e(TAG, "callVoicemail() voicemail number cannot be found", null);
            }
            return false;
        }
        // in case we don't have the permission (shouldn't happen) return
        // the same as when we don't have the number
        return false;
    }


    private void sendMOSMS(){
        setTimerWorking(true);
        SmsManager smsManager = SmsManager.getDefault();
        String smsToSend = (new StringBuilder().append("GET?c=").append(VVMApplication.getInstance().getClientId()).append("&v=1.0&l=").append(mailboxNumber).append("&AD")).toString();
        Logger.d(TAG, "before sending MO-SMS smstosend= " + smsToSend);
        smsManager.sendTextMessage(Constants.SHORTCODE_FOR_MO_SMS, null, smsToSend, null, null);
    }

    /**
     * Start/Stop timer
     *
     * @param isWorking
     *            True to start timer, False to stop timer
     */
    public void setTimerWorking(boolean isWorking) {

        Logger.i(TAG, "setTimerWorking=" + isWorking);
        if (isWorking) {
            (VVMApplication.getInstance()).acquireWakeLock();
            setupCallBackHandler.sendEmptyMessageDelayed(WAIT_FOR_SMS_TIMER, waitMaxTime*1000);
            timerStartTime = System.currentTimeMillis();
        } else {
            (VVMApplication.getInstance()).releaseWakeLock();
            setupCallBackHandler.removeMessages(WAIT_FOR_SMS_TIMER);
        }
    }

    public void cancelTimerWorking () {

        Logger.i(TAG, "cancelTimerWorking");
        setupCallBackHandler.removeMessages(WAIT_FOR_SMS_TIMER);
    }

    @Override
    public void onUpdateListener(int eventId, ArrayList<Long> messageIDs) {

        Logger.d(TAG, "onUpdateListener() CurrentSetupState=" + modelManager.getCurrentSetupState() + " eventId=" + eventId + " callback=" + _callback);

        if ( modelManager.getCurrentSetupState() == Constants.SETUP_STATUS.SUCCESS ) {
            return;
        }

        // mark that provisioninf SMS was recieved
        if (eventId == Constants.EVENTS.START_WELCOME_ACTIVITY) {
            String mboxStatus = ModelManager.getInstance().getMailBoxStatus();
            Logger.d(TAG, "onUpdateListener() PROVISIONING_SMS mboxStatus=" + mboxStatus);
            ModelManager.getInstance().setSharedPreference(Constants.KEYS.PREFERENCE_BEGIN_SETUP_TIME, (long) 0);
            int currentState = modelManager.getCurrentSetupState();
            // set UI state and load components
             if( mboxStatus.equals("I") && (currentState == Constants.SETUP_STATUS.WAIT_BINARY_SMS1 || currentState == Constants.SETUP_STATUS.WAIT_BINARY_SMS2 || currentState == Constants.SETUP_STATUS.WAIT_CALL_BINARY_SMS)) {
                String passwordValue = ModelManager.getInstance().getSharedPreferenceValue(Constants.KEYS.PREFERENCE_PASSWORD, String.class, null);
                if ( TextUtils.isEmpty(passwordValue) ) {
                    modelManager.setCurrentSetupState(Constants.SETUP_STATUS.ENTER_EXISTING_PWD);
                } else {
                    modelManager.setSetupCompleted();
                }
            }
            if ( mboxStatus.equals("C") || mboxStatus.equals("R") ){
                if(modelManager.isSetupCompleted()){
                    modelManager.setCurrentSetupState(Constants.SETUP_STATUS.RESET_PASSWORD);
                } else {
                    modelManager.setCurrentSetupState(Constants.SETUP_STATUS.ENTER_PASSWORD);
                }
            } else {
                currentState = modelManager.getCurrentSetupState();
                if ( currentState == Constants.SETUP_STATUS.WAIT_BINARY_SMS1 ) {
                    modelManager.setCurrentSetupState(Constants.SETUP_STATUS.WAIT_BINARY_SMS2);
                } else if ( currentState == Constants.SETUP_STATUS.WAIT_BINARY_SMS2 ) {
                    //modelManager.setCurrentSetupState(Constants.SETUP_STATUS.CALL_VOICE_MAIL);
                    modelManager.setCurrentSetupState(Constants.SETUP_STATUS.SHOW_CALL_VOICE_MAIL);
                }
            }
        }
        setupCallBackHandler.removeMessages(WAIT_FOR_SMS_TIMER);
        setTimerWorking(false);

        if ( _callback != null ) {
            _callback.onSetupStateChange();
        }
    }

    @Override
    public void onNetworkFailure() {
        Logger.d(TAG, "onNetworkFailure");
    }

}
