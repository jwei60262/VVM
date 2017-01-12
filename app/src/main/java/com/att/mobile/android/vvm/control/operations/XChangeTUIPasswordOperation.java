package com.att.mobile.android.vvm.control.operations;

import android.content.Context;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.control.Dispatcher;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.Constants.PasswordChangeRequiredStatus;
import com.att.mobile.android.vvm.model.Constants.TRANSACTIONS;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.protocol.response.IMAP4Response;

/**
 * An operation which performs password change in unauthenticated state of
 * mailbox the operation can be performed without login
 * 
 * MAB differs from the OMTP V1.2 standard in allowing the command to also be
 * used in the unauthenticated state so that the user can, for example, change a
 * password that has expired. Using this command in the unauthenticated state
 * does not authenticate the user. When used in the unauthenticated state, an
 * additional argument, USER, is required which provides the username or
 * tokenvalue as described below in LOGIN Addition. In the authenticated state,
 * the username is optional and, if not provided, defaults to be the same as the
 * user provided at login. The full syntax would be:
 * 
 * CNS1 XCHANGE_TUI_PWD USER=<Username or Tokenvalue> PWD=<Value>
 * OLD_PWD=<Value> Example: CNS1 XCHANGE_TUI_PWD
 * USER=4:16143674623:A:yellowstone:ms01:IMAP4:10493 PWD=013872 OLD_PWD=1234
 */
public class XChangeTUIPasswordOperation extends Operation implements Runnable{
	// the password to set instead of admin password
	private String newPassword;
	private static final String TAG = "XChangeTUIPasswordOperation";

	/**
	 * LoginOperation constructor.
	 * 
	 * @param context
	 *            (Context != null) application's context.
	 */
	public XChangeTUIPasswordOperation(Context context, String newPassword,
			Dispatcher dispatcher) {
		super(context);
		this.newPassword = newPassword;
		this.dispatcher = dispatcher;
	}

	/**
	 * Executes the login operation.
	 * 
	 * @return (int) login operation result.
	 * 
	 *         CNS1 XCHANGE_TUI_PWD USER=<Username or Tokenvalue> PWD=<Value>
	 *         165 OLD_PWD=<Value> 166 167 Example: 168 169 CNS1 XCHANGE_TUI_PWD
	 *         USER=4:16143674623:A:yellowstone:ms01:IMAP4:10493 170 PWD=013872
	 *         OLD_PWD=1234
	 */
	@Override
	public int execute() {
		Logger.d(TAG, "XChangeTUIPasswordOperation.execute()");
		// get current stored username and password. user name is the token we
		// got from the server in SMS,
		// if not exists then we use the mail box number we got from server in
		// SMS
		String username = ModelManager.getInstance().getSharedPreferenceValue(
				Constants.KEYS.PREFERENCE_TOKEN, String.class, null);
		if (username == null || username.equals("")) {
			username = ModelManager.getInstance().getSharedPreferenceValue(
					Constants.KEYS.PREFERENCE_MAILBOX_NUMBER, String.class, null);
		}
		String password = ModelManager.getInstance().getPassword();

		// in case no username or password
		if (username == null || username.equals("") || password == null
				|| password.equals("")) {
			// returns that login failed
			Logger.d(TAG,
					"XChangeTUIPasswordOperation.execute() login operation failed, username or password is missing");
			return Operation.Result.FAILED;
		}

		// creates the command
		byte[] command = new StringBuilder(Constants.IMAP4_TAG_STR)
				.append("XCHANGE_TUI_PWD USER=").append(username)
				.append(" PWD=").append(newPassword).append(" OLD_PWD=")
				.append(password).append("\r\n").toString().getBytes();

		// executes the command and gets the response and its result
		IMAP4Response imap4Response = executeIMAP4Command(
				TRANSACTIONS.TRANSACTION_XCHANGE_TUI_PASSWORD, command);
		int responseResult = imap4Response.getResult();

		if (responseResult == IMAP4Response.RESULT_OK) {
			
			Logger.d(TAG, "XChangeTUIPasswordOperation.execute() completed successfully");
			// save the new password to the model
			ModelManager.getInstance().setPassword(newPassword);
			// remove the required password flag
			ModelManager.getInstance().setPasswordChangeRequired(PasswordChangeRequiredStatus.NONE);
			// notify screen
			dispatcher.notifyListeners(EVENTS.XCHANGE_TUI_PASSWORD_FINISHED_SUCCESSFULLY,
					null);
			return Operation.Result.SUCCEED;
		} else {

			dispatcher.notifyListeners(EVENTS.XCHANGE_TUI_PASSWORD_FAILED,
					null);
			if (responseResult == IMAP4Response.RESULT_ERROR) {
				Logger.d(TAG, "XChangeTUIPasswordOperation.execute() failed");
				return Operation.Result.FAILED;
			}

			Logger.d(TAG, "XChangeTUIPasswordOperation.execute() failed due to network error");
			return Operation.Result.NETWORK_ERROR;
		}
	}

	@Override
	public void run() {
		execute();
	}
}
