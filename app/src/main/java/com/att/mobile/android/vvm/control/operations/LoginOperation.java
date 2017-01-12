package com.att.mobile.android.vvm.control.operations;

import android.content.Context;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.PasswordChangeRequiredStatus;
import com.att.mobile.android.vvm.model.Constants.TRANSACTIONS;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.protocol.response.IMAP4Response;

/**
 * An operation which performs login against the server.
 */
public class LoginOperation extends Operation
{
	private static final String TAG = "LoginOperation";
	/**
	 * LoginOperation constructor.
	 * 
	 * @param context (Context != null) application's context.
	 */
	public LoginOperation(Context context)
	{
		super(context);
	}
	
	/**
	 * Executes the login operation.
	 * 
	 * @return (int) login operation result. 
	 */
	@Override
	public int execute()
	{
		
		// 1 SETMETADATA INOX (/private/vendor/vendor_alu/messaging/ClientID "a:b:c")
		// client id format is: <client vendor>:<phoneid>:<platdorm\OS>:<IMEI>:<vvmid>
		String clientId = ((VVMApplication)context.getApplicationContext()).getClientId();
		
		byte[] clientIdCommand = new StringBuilder(Constants.IMAP4_TAG_STR).
		append("SETMETADATA INBOX (").append(Constants.METADATA_VARIABLES.ClientID).append(" \"").append(clientId).append("\")\r\n").toString().getBytes();
		//executes set metadata command to set client id before any login
		Logger.d(TAG, "LoginOperation.execute() set metadata client id command = "+clientIdCommand.toString());
		IMAP4Response clientIdResponse = executeIMAP4Command(TRANSACTIONS.TRANSACTION_LOGIN, clientIdCommand);
		
		// stop the operation only on network error
		if (clientIdResponse.getResult() == IMAP4Response.CONNECTION_CLOSED){
			Logger.d(TAG, "LoginOperation.execute() set metadata client id failed due to network error");
			return Operation.Result.NETWORK_ERROR;
		}
		
		// get current stored username and password. user name is the token we got from the server in SMS, 
		// if not exists then we use the mail box  number we got from server in SMS
		String username = ModelManager.getInstance().getSharedPreferenceValue(Constants.KEYS.PREFERENCE_TOKEN, String.class, null);
		Logger.d(TAG, "####LoginOperation.execute() login operation  username from PREFERENCE_TOKEN = " +username );
		if (username == null || username.equals("")){
			username = ModelManager.getInstance().getSharedPreferenceValue(Constants.KEYS.PREFERENCE_MAILBOX_NUMBER, String.class, null);
			Logger.d(TAG, "####LoginOperation.execute() login operation  username from PREFERENCE_MAILBOX_NUMBER = " +username );
		}
		String password = ModelManager.getInstance().getPassword();
		
		//in case username or password
		if(username == null || username.equals("") || password == null || password.equals(""))
		{
			//returns that login failed
			Logger.d(TAG, "LoginOperation.execute() login operation failed, username or password is missing");
			return Operation.Result.FAILED;
		}

		//creates the command
		byte[] command = new StringBuilder(Constants.IMAP4_TAG_STR).
			append("LOGIN ").append(username).append(" ").append(password).append("\r\n").toString().getBytes();
		Logger.d(TAG, "####LoginOperation.execute() login operation  username  = " +username +" password = "+password);
		
		//executes the login command and gets the response and its result
		IMAP4Response imap4Response = executeIMAP4Command(TRANSACTIONS.TRANSACTION_LOGIN, command);
		int responseResult = imap4Response.getResult();
		
		//in case login completed successfully
		if(responseResult == IMAP4Response.RESULT_OK)
		{
			return Operation.Result.SUCCEED;
		}
		
		//in case login failed
		if(responseResult == IMAP4Response.RESULT_ERROR)
		{
			String desc = imap4Response.getDescription();
			if (desc != null && (desc.contains(context.getString(R.string.invalidPassword))||
					(desc.contains(context.getString(R.string.couldNotLocateError))))){
				ModelManager.getInstance().setPasswordChangeRequired(PasswordChangeRequiredStatus.CHANGED_IN_TUI);
				Logger.d(TAG, "####LoginOperation.execute() login operation failed,  password is CHANGED_IN_TUI");
				ModelManager.getInstance().setCurrentSetupState(Constants.SETUP_STATUS.ENTER_EXISTING_PWD);
				return Result.FAILED_WRONG_PASSWORD;
			}
			else if (desc != null && desc.contains(context.getString(R.string.password_expired))){
				ModelManager.getInstance().setPasswordChangeRequired(PasswordChangeRequiredStatus.RESET_BY_ADMIN);
				Logger.d(TAG, "####LoginOperation.execute() login operation failed,  password is RESET_BY_ADMIN");
				ModelManager.getInstance().setCurrentSetupState(Constants.SETUP_STATUS.RESET_PASSWORD);
				return Result.FAILED_WRONG_PASSWORD;
			}
			return Operation.Result.FAILED;
		}
		
		return Operation.Result.NETWORK_ERROR;
	}
}
