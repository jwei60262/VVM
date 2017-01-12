package com.att.mobile.android.vvm.control.operations;

import android.content.Context;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.TRANSACTIONS;
import com.att.mobile.android.vvm.protocol.response.IMAP4Response;
import com.att.mobile.android.vvm.protocol.response.SelectResponse;


/**
 * An operation which performs getting the number of message currently in the server.
 */
public class SelectInboxOperation extends Operation
{
	private static final String TAG = "SelectInboxOperation";
	private int serverNumberOfMessage = 0;
	/**
	 * 
	 */
	public static class Result
	{
		public static final int SUCCEED_NO_MESSAGES_EXIST = Operation.Result.nextReusltID++;
	}
	
	/**
	 * LoginOperation constructor.
	 * 
	 * @param context (Context != null) application's context.
	 */
	public SelectInboxOperation(Context context)
	{
		super(context);
	}
	
	/**
	 * Executes the select inbox operation.
	 * 
	 * @return (int) select inbox operation result. 
	 */
	@Override
	public int execute()
	{
		Logger.d(TAG, "SelectInboxOperation.execute()");

		//creates the command
		byte[] command = new StringBuilder(Constants.IMAP4_TAG_STR).append("SELECT INBOX\r\n").toString().getBytes();
		IMAP4Response selectInboxResponse = executeIMAP4Command(TRANSACTIONS.TRANSACTION_SELECT_INBOX, command);
		int responseResult = selectInboxResponse.getResult();

		//in case login completed successfully
		if(responseResult == IMAP4Response.RESULT_OK)
		{
			Logger.d(TAG, "SelectInboxOperation.execute() completed successfully");
			
			//gets the number of messages exist at the server
			serverNumberOfMessage = ((SelectResponse)selectInboxResponse).getExists();
			
			//in case there are no message at the server
			if(serverNumberOfMessage == 0)
			{
				return Result.SUCCEED_NO_MESSAGES_EXIST;
			}
			
			return Operation.Result.SUCCEED;
		}
		
		//in case login failed
		if(responseResult == IMAP4Response.RESULT_ERROR)
		{
			Logger.d(TAG, "SelectInboxOperation.execute() failed");
			return Operation.Result.FAILED;
		}
		
		Logger.d(TAG, "SelectInboxOperation.execute() failed due to a network error");
		return Operation.Result.NETWORK_ERROR;
	}

	public void setServerNumberOfMessage(int serverNumberOfMessage) {
		this.serverNumberOfMessage = serverNumberOfMessage;
	}

	public int getServerNumberOfMessage() {
		return serverNumberOfMessage;
	}
}
