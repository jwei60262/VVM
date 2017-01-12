package com.att.mobile.android.vvm.control.operations;

import java.util.ArrayList;
import java.util.Arrays;

import android.content.Context;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.control.Dispatcher;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.Constants.TRANSACTIONS;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.model.greeting.Greeting;
import com.att.mobile.android.vvm.protocol.response.IMAP4Response;


/**
 * An operation that handles sending greeting message to the server. 
 */
public class SendGreetingOperation extends Operation
{
	/** holds the chunk size for sending the greeting data to the serve */
	private static int CHUNK_SIZE = 1024; 
	private static final String TAG = "SendGreetingOperation";
	
	/** greeting type to set to the server **/
	String greetingType = null;
	/** holds the binary data of greeting's audio file */
	private byte[] greetingAudioData = null;
	
	/**
	 * Send Greeting Operation constructor.
	 * 
	 * @param context (Context != null) a context.
	 * @param greetingAudioData (byte[] != null) the byte-stream data of the greeting to send.
	 * @param variableValue (String != null) the value of the variable to set.
	 */
	public SendGreetingOperation(Context context, String greetingType, byte[] greetingAudioData, Dispatcher dispatcher)
	{
		super(context);
		type = OperationTypes.TYPE_SEND_GREETING;
		this.greetingType = greetingType;
		this.greetingAudioData = Arrays.copyOf(greetingAudioData, greetingAudioData.length);
		this.dispatcher = dispatcher;
	}
	
	/**
	 * Sends the greeting to the server.
	 */
    @Override
	public int execute()
    {
    	Logger.d(TAG, "SendGreetingOperation.execute()");

    	//holds the content-type string
    	//TODO - Royi - strings file ?
    	String contentTypeStr = "Content-Type: audio/amr\n\n";
    	
    	//creates the set meta data command (for the greeting)
		String command = new StringBuilder(Constants.IMAP4_TAG_STR).append("SETMETADATA INBOX (").
			append(greetingType).append(" {").append(greetingAudioData.length + contentTypeStr.length()).append("}\r\n").
			append(contentTypeStr).toString();

		//sends the set meta data command, without receiving the response
		executeIMAP4CommandWithoutResponse(TRANSACTIONS.TRANSACTION_SEND_GREETING_REQUEST, command.getBytes(), -1);
		
		//send the actual greeting's data to the server, without receiving the response
		executeIMAP4CommandWithoutResponse(TRANSACTIONS.TRANSACTION_SEND_GREETING_DATA, greetingAudioData, CHUNK_SIZE);

		//sends the closer of the send greeting command and gets the response with its result
		//TODO - Royi - reads ALL response's lines ?
		IMAP4Response sendGreetingResponse = executeIMAP4Command(TRANSACTIONS.TRANSACTION_SEND_GREETING_DATA, ")\r\n".getBytes());
		int sendGreetingResponseResult = sendGreetingResponse.getResult();
		
		//in case sending the greeting succeed
		if(sendGreetingResponseResult == IMAP4Response.RESULT_OK)
		{
			Logger.d(TAG, "SendGreetingOperation.execute() completed successfully");

			// save stream to greeting in the model
			ArrayList<Greeting> greetingList = ModelManager.getInstance().getGreetingList();
			String stream = new String(greetingAudioData);
			for (Greeting greeting : greetingList) {
				// in case the current greeting is changeable
				if(greetingType != null && greeting != null){
					if (greetingType.equals(greeting.getImapRecordingVariable())){
						ModelManager.getInstance().getMetadata().put(greetingType, stream);
						greeting.setExistingRecording(true);
					}
				}
			}
			
			//notifies any registered listeners that uploading the greeting to the server succeed
			dispatcher.notifyListeners(EVENTS.GREETING_UPLOAD_SUCCEED, null);
			
			return Result.SUCCEED;
		}
		
		//in case sending the greeting failed
		if(sendGreetingResponseResult == IMAP4Response.RESULT_ERROR)
		{
			Logger.d(TAG, "SendGreetingOperation.execute() failed");

			//notifies any registered listeners that uploading the greeting to the server failed
			dispatcher.notifyListeners(EVENTS.GREETING_UPLOAD_FAILED, null);
			
			return Result.FAILED;
		}
		
		Logger.d(TAG, "SendGreetingOperation.execute() failed due to a network error");
		return Result.NETWORK_ERROR;
    }
}
