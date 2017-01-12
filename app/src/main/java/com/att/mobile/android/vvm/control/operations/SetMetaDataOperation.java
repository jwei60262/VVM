package com.att.mobile.android.vvm.control.operations;

import android.content.Context;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.control.Dispatcher;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.Constants.TRANSACTIONS;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.protocol.response.IMAP4Response;


/**
 * An operation which performs setting a meta data variable at the server.
 * 
 * Specific request example:
 * <Tag> SETMETADATA INBOX
 * (/private/vendor/vendor.alu/messaging/TUIPassword �1515�)
 *
 * Specific response example:		
 * METADATA �INBOX� (/private/vendor/vendor.alu/messaging/TUIPassword)
 * <Tag> OK <Descriptive Text>
 */
public class SetMetaDataOperation extends Operation
{
	/** holds the variable to set and its value */
	private String variable;
	private String variableValue;
	private static final String TAG = "SetMetaDataOperation";
	
	/**
	 * SetMetaDataOperation constructor.
	 * 
	 * @param context (Context != null) application's context.
	 * @param operationType (OperationTypes) the specific set meta data oepration to execute.
	 * @param variable (String != null) the variable name to set.
	 * @param variableValue (String != null) the value of the variable to set.
	 */
	public SetMetaDataOperation(Context context, OperationTypes operationType, String variable, String variableValue, Dispatcher dispatcher)
	{
		super(context);
		this.type = operationType;
		this.variable = variable;
		this.variableValue = variableValue;
		this.dispatcher = dispatcher;
	}
	
	/**
	 * Executes the set meta data operation.
	 * 
	 * @return (int) set meta data operation result. 
	 */
    @Override
	public int execute()
    {
    	Logger.d(TAG, "SetMetaDataOperation.execute() - setting meta data variable " + this.variable + " with its value " + this.variableValue);

    	//creates the set meta data command
		byte[] command = new StringBuilder(Constants.IMAP4_TAG_STR).append("SETMETADATA INBOX (").append(variable).
			append(" \"").append(variableValue).append("\")\r\n").toString().getBytes();

		//executes the set meta data command and gets the reponse and its result
		IMAP4Response setMetaDataResponse = executeIMAP4Command(TRANSACTIONS.TRANSACTION_SET_METADATA, command);
		int setMetaDataResponseResult = setMetaDataResponse.getResult();
		
		//in case setting the meta data succeed
		if(setMetaDataResponseResult == IMAP4Response.RESULT_OK)
		{
			Logger.d(TAG, "SetMetaDataOperation.execute() completed successfully");
			
			//in case setting a password meta data succeed
			if(type == OperationTypes.TYPE_SET_META_DATA_PASSWORD)
			{
				// save the new password
				ModelManager.getInstance().setPassword(this.variableValue);
				//notifies and registered listener that setting password meta data operation has finished
				dispatcher.notifyListeners(EVENTS.SET_METADATA_PASSWORD_FINISHED, null);
			}
			else if(type == OperationTypes.TYPE_SET_META_DATA_GREETING_TYPE)
			{
				//notifies and registered listener that setting password meta data operation has finished
				dispatcher.notifyListeners(EVENTS.SET_METADATA_GREETING_FINISHED, null);
			}
			
			return Result.SUCCEED;
		}
		//in case setting the meta data failed
		else if(setMetaDataResponseResult == IMAP4Response.RESULT_ERROR)
		{
			Log.e("SetMetaDataOperation.execute()", "set meta data operation failed");
			
			if(type == OperationTypes.TYPE_SET_META_DATA_GREETING_TYPE)
			{
				//notifies and registered listener that setting password meta data operation has finished
				dispatcher.notifyListeners(EVENTS.SET_METADATA_GREETING_FAILED, null);
			}
			
			return Result.FAILED;
		}
		
		Log.e("SetMetaDataOperation.execute()", "set meta data operation failed due to a network error");
		return Result.NETWORK_ERROR;
    }
}