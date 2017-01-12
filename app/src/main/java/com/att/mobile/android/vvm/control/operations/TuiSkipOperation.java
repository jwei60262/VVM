package com.att.mobile.android.vvm.control.operations;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import android.content.Context;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.TRANSACTIONS;
import com.att.mobile.android.vvm.model.db.MessageDo;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.protocol.response.IMAP4Response;
import com.att.mobile.android.vvm.protocol.response.StoreResponse;

/**
 * This operation is for marking messages on the server as 'TUISkipped'.
 * This is needed when we want get MWI (to clear notification) from the server and show our
 * new voicemail message notification instead of the default one. 
 *  
 * @author mkoltnuk
 */
public class TuiSkipOperation extends Operation
{
	/** holds the UIDs of the messages to set as TUI SKIPPED */
	private Set<Long> requestedMessageToTuiSkipUIDs = null;
	private static final String TAG = "TuiSkipOperation";
	
	/**
	 * TuiSkipOperation constructor
	 * 
	 * @param context (Context != null) application's context.
	 */
	public TuiSkipOperation(Context context) {
		super(context);
		type = OperationTypes.SKIPPED;
	}

	@Override
	public int execute(){
		Logger.d(TAG, "TuiSkipOperation.execute()");
		
		//adds the message UID to the message to TuiSkip UIDs collection
		MessageDo[] messageDos = ModelManager.getInstance().getUnSkippedMessages();
		
		//in case there are no messages to skip (no unread messages)
		if (messageDos == null || messageDos.length == 0)
		{
			//returns that the operation succeed
			Logger.d(TAG, "TuiSkipOperation.execute() completed successfully - no messages to skip)");
			return Result.SUCCEED;
		}
		
		this.requestedMessageToTuiSkipUIDs = new HashSet<Long>();
		
		//traverses over all message ID-UID pairs
		for (MessageDo messageDo : messageDos)
		{
			// any non-dummy message should be added
			if (messageDo.getUid() < Constants.WELCOME_MESSAGE_ID)
			{
				this.requestedMessageToTuiSkipUIDs.add(messageDo.getUid());
			}
		}
		
		StringBuilder command = new StringBuilder(Constants.IMAP4_TAG_STR).append("UID STORE ");
		//builds the command string
		Iterator<Long> it = requestedMessageToTuiSkipUIDs.iterator();
		if (it.hasNext()){
			command.append(it.next());
		}
		while(it.hasNext()){
			command.append(',').append(it.next());
		}
		command.append(" +FLAGS (TUISkipped)\r\n");
		
		//performs the Tui skip messages at the server
		IMAP4Response tuiSkipOperationResponse = executeIMAP4Command(TRANSACTIONS.TRANSACTION_TUI_SKIP, command.toString().getBytes());
		int responseResult = tuiSkipOperationResponse.getResult();
		
		//in case skipping the messages completed successfully
		if(responseResult == IMAP4Response.RESULT_OK)
		{
			ModelManager.getInstance().updateTuiskipped(((StoreResponse)tuiSkipOperationResponse).getSkippedUids());
			
			// in case all the requested TuiSkip uids were actually skipped
			if ( ModelManager.getInstance().getUnSkippedMessages() != null)
			{
				Logger.d(TAG, "TuiSkipOperation.execute() NOT all requested messages were TUISkipped.");
				return Result.FAILED;
			}
			
			Logger.d(TAG, "TuiSkipOperation.execute() all requested messages were TUISkipped.");
			return Result.SUCCEED;			
		}
		
		//in case skipping has failed
		if(responseResult == IMAP4Response.RESULT_ERROR)
		{
			Log.e(TAG, "TuiSkipOperation.execute() failed");
			return Result.FAILED;
		}
		
		Log.e(TAG, "TuiSkipOperation.execute() failed due to a network error");
		return Result.NETWORK_ERROR;
	}
}