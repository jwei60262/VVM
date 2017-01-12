package com.att.mobile.android.vvm.control.receivers;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.text.TextUtils;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.control.ATTM.AttmUtils;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.ACTIONS;
import com.att.mobile.android.vvm.model.db.ModelManager;

public class SmsMessageReceiver extends BroadcastReceiver {

	private static final String TAG = "SmsMessageReceiver";
	private static  Map<String, String> savedmsg = null; 

	@Override
	public void onReceive(Context context, Intent intent) {

		// if that's the first entry to the app.
		ModelManager.createInstance(context);
		AttmUtils.isUibReadyToReplaceLegacyVvm();
		
		Logger.d(TAG, "SmsMessageReceiver.onReceive() ACTION = " + intent.getAction());
		
		if (ModelManager.getInstance().getAttmStatus() != Constants.ATTM_STATUS.PROVISIONED && intent.getAction()
				.equals("android.intent.action.DATA_SMS_RECEIVED")) {
			handleIncomingSMS(context, intent);
		}
	}


	/**
	 * retrieve SMS
	 * 
	 * @param intent
	 */
	private void handleIncomingSMS(Context context, Intent intent) {

		Logger.d(TAG, "SmsMessageReceiver.handleIncomingSMS()");
		Map<String, String> msg = retrieveMessages(intent);
		// get a list of all the new Sms messages that arrived
		String currMessageText = null;
		if(msg != null){
			for (String sender : msg.keySet()) {
				currMessageText = msg.get(sender);
				Logger.d(TAG, "SmsMessageReceiver.handleIncomingSMS() text = "
						+ currMessageText);
				if (currMessageText != null && currMessageText.length() > 0
						&& (matchSMS(currMessageText) || isSavedMsgExist(sender))) {

					try {
						// avoid processing this message by other applications
						Logger.d(TAG,
								"handleIncomingSMS() going to abortBroadcast");
						intent.setClass(context, SmsMessageReceiver.class);
						setOrderedHint(true);
						this.abortBroadcast();
						Logger.d(TAG, "abortBroadcast successfuly");
					} catch (Exception e) {
						Log.e(TAG, "handleIncomingSMS() abortBroadcast failed", e);
					}

					if(isSavedMsgExist(sender)){
						StringBuilder previousparts = new StringBuilder(savedmsg.get(sender));
						Logger.d(TAG, "####handleIncomingSMS() isSavedMsgExist previousparts = "+previousparts);
						previousparts.append(currMessageText);
						Logger.d(TAG, "####handleIncomingSMS() isSavedMsgExist concatened previousparts = "+previousparts);
						savedmsg.put(sender, previousparts.toString());
						currMessageText = previousparts.toString();
						Logger.d(TAG, "handleIncomingSMS() isSavedMsgExist currMessageText = "+currMessageText);
					}
						processMessage(context, currMessageText, sender);
				}
			}
		}
	}


	private void processMessage(Context context, String currMessageText,
			String sender) {
		int index = currMessageText.lastIndexOf("t=");
		Logger.d(TAG, "####processMessage() currMessageText.lastIndexOf('t=') = "+index);
		String chk = currMessageText.substring(index+2);
		Logger.d(TAG, "####processMessage() currMessageText.substring(index+2) = "+chk);
		if(index < 0 ||checkSMS(chk)){
			savedmsg = null;
			// start service
			Intent notificationServiceIntent = new Intent(
					ACTIONS.ACTION_NEW_SMS_NOTIFICATION);
			notificationServiceIntent.putExtra(Intent.EXTRA_TEXT,
					currMessageText);
			notificationServiceIntent.setClass(context,
					NotificationService.class);
			context.startService(notificationServiceIntent);
		} else {
			if(savedmsg == null) {
				savedmsg = new HashMap<String, String>(1);
			} 
			savedmsg.put(sender, currMessageText);
		}
	}

	/**
	 * fetch SMS from the intent to SMS messages array
	 *	known issue:
	 *	SMS ADN from server icludes a token, in the token there was a problem when server send '_'
	 *	ALU remove all the '_' and issue was solved
	 *  example for good SMS ADN text 
	 *  text = smpp-vip.anypath.com:143?f=0&v=601&m=2546636842&S=I&s=993&d=587&t=4:2546636842:A:nbvip.ap2:ms02:IMAP4STTAMR:21799
	 *  example for bad SMS ADN text 
	 *  text = smpp-vip.anypath.com:143?f=0&v=601&m=2546636842&S=I&s=993&d=587&t=4:2546636842:A:nbvip.ap2:ms02:IMAP4_STT_AMR:21799
	 * @param intent
	 * @return
	 */
	private SmsMessage[] getMessagesFromIntent(Intent intent) {
		SmsMessage retMsgs[] = null;
		Bundle bdl = intent.getExtras();
		try {
			Object pdus[] = (Object[]) bdl.get("pdus");
			retMsgs = new SmsMessage[pdus.length];

			for (int n = 0; n < pdus.length; n++) {
				byte[] byteData = (byte[]) pdus[n];
				retMsgs[n] = SmsMessage.createFromPdu(byteData);
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
		return retMsgs;
	}
	
	
    private static Map<String, String> retrieveMessages(Intent intent) {
        Map<String, String> msg = null; 
        SmsMessage[] msgs;
        Bundle bundle = intent.getExtras();
        
        if (bundle != null && bundle.containsKey("pdus")) {
            Object[] pdus = (Object[]) bundle.get("pdus");

            if (pdus != null) {
                int nbrOfpdus = pdus.length;
                msg = new HashMap<String, String>(nbrOfpdus);
                msgs = new SmsMessage[nbrOfpdus];
                
                // There can be multiple SMS from multiple senders, there can be a maximum of nbrOfpdus different senders
                // However, send long SMS of same sender in one message
                for (int i = 0; i < nbrOfpdus; i++) {
                    msgs[i] = SmsMessage.createFromPdu((byte[])pdus[i]);
                    
                    String originatinAddress = msgs[i].getOriginatingAddress();
                    
                    // Check if index with number exists                    
                    if (!msg.containsKey(originatinAddress)) { 
                        // Index with number doesn't exist                                               
                        // Save string into associative array with sender number as index
                        msg.put(msgs[i].getOriginatingAddress(), msgs[i].getMessageBody()); 
                        
                    } else {    
                        // Number has been there, add content but consider that
                        // msg.get(originatinAddress) already contains sms:sndrNbr:previousparts of SMS, 
                        // so just add the part of the current PDU
                        StringBuilder previousparts = new StringBuilder(msg.get(originatinAddress));
    					Logger.d(TAG, "retrieveMessages() previousparts = "+previousparts);
    					previousparts.append(msgs[i].getMessageBody());
       					Logger.d(TAG, "retrieveMessages() concatened previousparts = "+previousparts);
                        msg.put(originatinAddress, previousparts.toString());
                    }
                }
            }
        }
        
        return msg;
    }
    
    private boolean checkSMS(String smsText){
    	StringTokenizer tokenizer = new StringTokenizer(smsText,":");
    	if(tokenizer.countTokens() >= 7){
    		return true;
    	} else {
    		return false;
    	}
    }
    
    private boolean isSavedMsgExist(String origAddress){
    	return (savedmsg != null && (!TextUtils.isEmpty(savedmsg.get(origAddress))));
    }
    
	/**
	 * this method will validate the pattern of SMS directed to VVM application according to ALU application directed SMS format.
	 * updated to "MAB R6.0 Outgoing Notifications Specification Issue 1.1"
	 * 
	 * other application may register to the same port or platform may deliver other port directed SMS to VVM.
	 * we have found that port registration is probably not acting as filter for DATA_SMS intents.
	 * 
	 *  pattern explaination:
	 *  <MAB server>:<portnumber>?<namevaluepairlist>
	 *  <namevaluepairlist> = <namevaluepair> [ & <namevaluepairlist> ]
	 *  <namevaluepair> = <name> = <value>
	 * 
	 * examples for valid SMS
	 * smpp-vip.anypath.com:143?f=0&v=601&m=2546636842&S=I&s=993&d=587&t=4:2546636842:A:nbvip.ap2:ms02:IMAP4STTAMR:21799
	 * 
	 * redwa2acds.attwireless.net:5400?f=0&v=601&m=2138320485&p=&S=I&s=993&d=587&t=4:2138320485:A:subnetname3637:ms04:IMAP4MWI_STT_D:33135
	 *
	 * @param smsText
	 * @return
	 */
	private boolean matchSMS(String smsText){
		
		String validtionPattern = ".+:\\d+\\?([a-zA-Z]*[=][^&]*)(&([a-zA-Z][=][^&]*))*";
		
		// check for matching pattern
		// check for mandatory fileds as well
		boolean  res =  smsText.matches(validtionPattern) &&
				(smsText.contains("m=") || smsText.contains("t="));
		
		Logger.d(TAG, "matchSMS() check for VVM SMS format and mandatory fields, result = " + res);
		
		return res;
	}
}