package com.att.mobile.android.vvm.control.operations;

import android.content.Context;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.control.Dispatcher;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.TRANSACTIONS;
import com.att.mobile.android.vvm.protocol.response.IMAP4Response;


/**
 * An operation which performs fetching message headers from the server.
 */
public class FetchHeadersOperation extends Operation
{
	private static final String TAG = "FetchHeadersOperation";
	/**
	 * FetchHeadersOperation constructor.
	 * 
	 * @param context (Context != null) application's context.
	 * @param dispatcher (Dispatcher != null) a dispatcher holding registered listeners.
	 */
	public FetchHeadersOperation(Context context, Dispatcher dispatcher)
	{
		super(context);
		type = OperationTypes.TYPE_FETCH_HEADERS;
		this.dispatcher = dispatcher;
	}

	/**
	 * Executes the fetch headers operation.
	 * 
	 * @return (int) fetch headers operation result. 
	 */
    @Override
	public int execute()
    {
    	Logger.d(TAG, "FetchHeadersOperation.execute()");
    	
		//creates the fetch headers comman
		byte[] command = new StringBuilder(Constants.IMAP4_TAG_STR).append("FETCH 1:* (UID FLAGS BODY.PEEK[HEADER.FIELDS (DATE X-CLI_NUMBER X-ALU-PREVIOUS-UID)])\r\n").
			toString().getBytes();
		
		//executes the fetch headers command and get the response and its result
		IMAP4Response imap4Response = executeIMAP4Command(TRANSACTIONS.TRANSACTION_FETCH_HEADERS, command);
		int responseResult = imap4Response.getResult();
		
		//in case fetching headers completed successfully
		if(responseResult == IMAP4Response.RESULT_OK)
		{
			Logger.d(TAG, "FetchHeadersOperation.execute() completed successfully");
			
			return Result.SUCCEED;
		}
		
		//in case fetching headers failed
		if(responseResult == IMAP4Response.RESULT_ERROR)
		{
			Log.e(TAG, "FetchHeadersOperation.execute() failed");
			return Result.FAILED;
		}
		
		Log.e(TAG, "FetchHeadersOperation.execute() failed due to a network error");
		return Result.NETWORK_ERROR;
    }
}