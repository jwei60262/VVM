package com.att.mobile.android.vvm.control.operations;

import android.content.Context;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.TRANSACTIONS;
import com.att.mobile.android.vvm.protocol.response.IMAP4Response;


/**
 * An operation which performs marking messages as read at the server.
 * handle case when message to mark as read UIDs have not been set!
 */
public class MarkAsReadOperation extends Operation
{
	private static final String TAG = "MarkAsReadOperation";
	private long messageUID;

	
	/**
	 * MarkAsReadOperation constructor.
	 * 
	 * @param context (Context != null) application's context.
	 */
	public MarkAsReadOperation(Context context, long mUID)
	{
		super(context);
		type = OperationTypes.MARK_AS_READ;
		messageUID = mUID;
	}
	

	/**
	 * Executes the mark messages as read operation.
	 * 
	 * @return (int) mark as read operation result. 
	 */
	@Override
	public int execute()
	{
		Logger.d(TAG, "MarkAsReadOperation.execute()");
		
		//builds the command string
		StringBuilder command = new StringBuilder(Constants.IMAP4_TAG_STR).append("UID STORE ");
			command.append(messageUID);
		command.append(" +FLAGS (\\Seen)\r\n");

		//performs the messages mark as read at the server
		IMAP4Response markAsReadOperationResponse = executeIMAP4Command(TRANSACTIONS.TRANSACTION_READ, command.toString().getBytes());
		int responseResult = markAsReadOperationResponse.getResult();
		
		//in case marking messages as read completed successfully
		if(responseResult == IMAP4Response.RESULT_OK)
		{
			//removes the deleted and marked as deleted message UIDs from the pending UIDs collections
			Logger.d(TAG, "MarkAsReadOperation.execute() completed successfully");
//			ModelManager.getInstance().removeDeletedAndMarkedAsReadMessageUIDs(((StoreResponse)markAsReadOperationResponse).getDeletedUids(), ((StoreResponse)markAsReadOperationResponse).getReadUids());
			return Result.SUCCEED;
		}
		
		//in case marking messages as read failed
		if(responseResult == IMAP4Response.RESULT_ERROR)
		{
			Log.e(TAG, "MarkAsReadOperation.execute() operation failed");
			return Result.FAILED;
		}
		
		Log.e(TAG, "MarkAsReadOperation.execute() failed due to a network error");
		return Result.NETWORK_ERROR;
	}
}