package com.att.mobile.android.vvm.control.operations;

import android.content.Context;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.TRANSACTIONS;
import com.att.mobile.android.vvm.protocol.response.GetQuotaResponse;
import com.att.mobile.android.vvm.protocol.response.IMAP4Response;


/**
 * An operation which performs getting the number of message currently in the server.
 */
public class GetQuotaOperation extends Operation
{
	private static final String TAG = "GetQuotaOperation";
	private int serverQuota = 0;
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
	public GetQuotaOperation(Context context)
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
		Logger.d(TAG, "GetQuotaOperation.execute()");

		//creates the command
		byte[] command = new StringBuilder(Constants.IMAP4_TAG_STR).append("GETQUOTA\n").toString().getBytes();
		IMAP4Response getQuotaResponse = executeIMAP4Command(TRANSACTIONS.TRANSACTION_GETQUOTA, command);
		int responseResult = getQuotaResponse.getResult();

		//in case login completed successfully
		if(responseResult == IMAP4Response.RESULT_OK)
		{
			
			serverQuota = ((GetQuotaResponse)getQuotaResponse).getQuota();
			Logger.d(TAG, "GetQuotaOperation.execute() completed successfully, serverQuota = "+serverQuota);
			return Operation.Result.SUCCEED;
		}
		
		//in case login failed
		if(responseResult == IMAP4Response.RESULT_ERROR)
		{
			Logger.d(TAG, "GetQuotaOperation.execute() failed");
			return Operation.Result.FAILED;
		}
		
		Logger.d(TAG, "GetQuotaOperation.execute() failed due to a network error");
		return Operation.Result.NETWORK_ERROR;
	}

	public int getServerQuota() {
		return serverQuota;
	}

}
