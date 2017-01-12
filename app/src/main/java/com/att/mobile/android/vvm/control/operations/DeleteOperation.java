package com.att.mobile.android.vvm.control.operations;

import android.content.Context;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.TRANSACTIONS;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.protocol.response.IMAP4Response;

import java.util.Arrays;


/**
 * An operation which performs deleting messages at the server.
 * handle case when message to delete UIDs have not been set!
 */
public class DeleteOperation extends Operation
{
	private static final String TAG = "DeleteOperation";
	private Long[] messageToDeleteUIDs;
	/**
	 * DeleteOperation constructor.
	 * 
	 */
	public DeleteOperation(Context context,Long[] messageToDeleteUIDs)
	{
		super(context);
		type = OperationTypes.DELETE;
		this.messageToDeleteUIDs = Arrays.copyOf(messageToDeleteUIDs, messageToDeleteUIDs.length);
	}


	public DeleteOperation(Context context)
	{
		super(context);
		type = OperationTypes.DELETE;
	}
	
	/**
	 * Executes the mark messages as deleted and expunge operations.
	 * 
	 * @return (int) delete operation result. 
	 */
	@Override
	public int execute()
	{
		//in case marking messages as deleted operation succeed
		int operationResult;
		if((operationResult = deleteMessages()) == Result.SUCCEED)
		{
			//performs the expunge operation
			operationResult = expunge();
		}
		
		//returns operation result
		return operationResult;
	}

	/**
	 * Executes the delete messages operation.
	 * 
	 * @return (int) delete messages operation result. 
	 */
	private int deleteMessages()
	{
		Logger.d(TAG, "DeleteOperation.deleteMessages()");
		
		//in case there are no messages to mark as deleted
		if(messageToDeleteUIDs == null || messageToDeleteUIDs.length == 0)
		{
			//returns that the operation succeed
			Logger.d(TAG, "DeleteOperation.deleteMessages() completed successfully - no messages to delete");
			return Result.SUCCEED;
		}

		//builds the command string
//		Iterator<Long> it = messageToDeleteUIDs.iterator();
		StringBuilder command = new StringBuilder(Constants.IMAP4_TAG_STR).append("UID STORE ");
		for(long mUID: messageToDeleteUIDs){
			command.append(mUID);
				command.append(',');
			}
		command.deleteCharAt(command.lastIndexOf(","));
		command.append(" +FLAGS (\\Deleted)\r\n");

		//performs the messages mark as deleted at the server
		IMAP4Response deleteOperationResponse = executeIMAP4Command(TRANSACTIONS.TRANSACTION_DELETE, command.toString().getBytes());
		int responseResult = deleteOperationResponse.getResult();

		//in case marking messages as deleted completed successfully
		if(responseResult == IMAP4Response.RESULT_OK)
		{
			//removes the deleted and marked as deleted message UIDs from the pending UIDs collections
//			ModelManager.getInstance().removeDeletedAndMarkedAsReadMessageUIDs(((StoreResponse)deleteOperationResponse).getDeletedUids(), ((StoreResponse)deleteOperationResponse).getReadUids());
			ModelManager.getInstance().deleteMessagesMarkedAsDeleted();
			Logger.d(TAG, "DeleteOperation.deleteMessages() completed successfully");
			return Result.SUCCEED;
		}
		
		//in case marking messages as delete failed
		if(responseResult == IMAP4Response.RESULT_ERROR)
		{
			Logger.d(TAG, "DeleteOperation.deleteMessages() failed");
			return Result.FAILED;
		}
		
		Logger.d(TAG, "DeleteOperation.deleteMessages() failed due to a network error");
		return Result.NETWORK_ERROR;
	}
	
	/**
	 * Executes the expunge operation.
	 * 
	 * @return (int) expunge operation result. 
	 */
	private int expunge()
	{
		Logger.d(TAG, "DeleteOperation.expunge()");
		
		//creates the expunge command
		byte[] command = new StringBuilder(Constants.IMAP4_TAG_STR).append("EXPUNGE\r\n").toString().getBytes();

		//executes the expunge command
		IMAP4Response expungeOperationResponse = executeIMAP4Command(TRANSACTIONS.TRANSACTION_EXPUNGE, command);
		int responseResult = expungeOperationResponse.getResult();
		Logger.d(TAG, "DeleteOperation.expunge() - response is: " + expungeOperationResponse);

		//in case marking messages as deleted completed successfully
		if(responseResult == IMAP4Response.RESULT_OK)
		{
			Logger.d(TAG, "DeleteOperation.expunge()completed successfully");
			return Result.SUCCEED;
		}
		
		//in case marking messages as delete failed
		if(responseResult == IMAP4Response.RESULT_ERROR)
		{
			Log.e(TAG, "DeleteOperation.expunge() failed");
			return Result.FAILED;
		}
		
		Log.e(TAG, "DeleteOperation.expunge() failed due to a network error");
		return Result.NETWORK_ERROR;
	}
}
