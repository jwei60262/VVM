package com.att.mobile.android.vvm.control.operations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import android.content.Context;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.control.Dispatcher;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.Constants.METADATA_VARIABLES;
import com.att.mobile.android.vvm.model.Constants.TRANSACTIONS;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.model.greeting.Greeting;
import com.att.mobile.android.vvm.model.greeting.GreetingFactory;
import com.att.mobile.android.vvm.protocol.response.IMAP4Response;
import com.att.mobile.android.vvm.protocol.response.MetaDataResponse;
import com.att.mobile.android.vvm.screen.GreetingRecorderActivity;

/**
 * An operation which performs get meta data from the server.
 */
public class GetMetaDataOperation extends Operation {
	/**
	 * holds the transaction type, according to the operation specific get meta
	 * data type
	 */
	private byte transactionType;
	private static final String TAG = "GetMetaDataOperation";

	/**
	 * holds coma separated list of metadata variables to get from the server in
	 * the operation
	 */
	private String variables;

	/** holds server's response variables */
	private HashMap<String, String> variablesResponseValues;

	/**
	 * GetMetaDataOperation constructor.
	 * 
	 * @param context
	 *            (Context != null) application's context.
	 * @param operationType
	 *            (OperationTypes) the specific get meta data oepration to
	 *            execute.
	 * @param dispatcher
	 *            (Dispatcher != null) a dispatcher holding registered
	 *            listeners.
	 */
	public GetMetaDataOperation(Context context, OperationTypes operationType,
			Dispatcher dispatcher) {
		super(context);
		type = operationType;
		this.dispatcher = dispatcher;

		if (type == OperationTypes.TYPE_GET_META_DATA_GREETINGS_DETAILS) {
			initializeGetGreetingsMetaData();
		}

		if (type == OperationTypes.TYPE_GET_META_DATA_EXISTING_GREETINGS) {
			initializeGetExistingGreetingsMetaData();
		}

		if (type == OperationTypes.TYPE_GET_META_DATA_PASSWORD) {
			initializeGetPasswordLengthMetaData();
		}
	}

	/**
	 * Initializes the get meta data operation as a get password length meta
	 * data operation.
	 */
	private void initializeGetPasswordLengthMetaData() {
		// sets the transaction type
		this.transactionType = TRANSACTIONS.TRANSACTION_GET_METADATA;

		// stores command's variables
		this.variables = new StringBuilder(METADATA_VARIABLES.MaxPasswordDigits)
		.append(" ").append(METADATA_VARIABLES.MinPasswordDigits)
		.toString();
	}

	/**
	 * Initializes the get meta data operation as a get greeting meta data
	 * operation.
	 */
	private void initializeGetGreetingsMetaData() {
		// sets the transaction type
		this.transactionType = TRANSACTIONS.TRANSACTION_GET_METADATA;

		// stores command's variables
		this.variables = new StringBuilder(
				METADATA_VARIABLES.GreetingTypesAllowed).append(" ")
				.append(METADATA_VARIABLES.ChangeableGreetings).append(" ")
				.append(METADATA_VARIABLES.GreetingType).append(" ")
				.append(METADATA_VARIABLES.MaxGreetingLength).append(" ")
				.append(METADATA_VARIABLES.MaxRecordedNameLength).toString();
	}

	/**
	 * Initializes the get meta data operation as a get existing greetings meta
	 * data operation.
	 */
	private void initializeGetExistingGreetingsMetaData() {
		// sets the transaction type
		this.transactionType = TRANSACTIONS.TRANSACTION_FETCH_GREETIGNS_BODIES;
		Logger.d(TAG,
				"GetMetaDataOperation initializeGetExistingGreetingsMetaData");

		// gets stored greeting meta data
		HashMap<String, String> greetingMetaData = ModelManager.getInstance()
		.getMetadata();

		if (greetingMetaData != null) {
			// creates the greeting list
			ArrayList<Greeting> greetingList = GreetingFactory.createGreetings(
					context, greetingMetaData
					.get(METADATA_VARIABLES.GreetingTypesAllowed),
					greetingMetaData
					.get(METADATA_VARIABLES.ChangeableGreetings),
					greetingMetaData.get(METADATA_VARIABLES.GreetingType),
					greetingMetaData.get(METADATA_VARIABLES.MaxGreetingLength),
					greetingMetaData
					.get(METADATA_VARIABLES.MaxRecordedNameLength));

			// stores it in the model
			ModelManager.getInstance().setGreetingList(greetingList);

			// reverses the created greeting list
			Collections.reverse(greetingList);
		}
	}

	/**
	 * Executes the get meta data operation.
	 * 
	 * @return (int) get meta data operation result.
	 */
	@Override
	public int execute() {

		IMAP4Response getMetaDataResponse = null;

		// when getting existing greeting - we don't want to failed the entire
		// flow of getting the greetings types if no existing greeting was found
		if (this.type == OperationTypes.TYPE_GET_META_DATA_EXISTING_GREETINGS) {
			// go over all greetings
			for (Greeting greeting : ModelManager.getInstance().getGreetingList()) {
				// get existing record for changeable greetings
				
				if (greeting.isChangeable()){
					variables = greeting.getImapRecordingVariable();
					getMetaDataResponse = getMetaDataAttachedFile(greeting.getDisplayName() + ".amr");

					if (getMetaDataResponse.getResult() == IMAP4Response.RESULT_OK) {
						// set stream on current greeting (if such exist)
							greeting.setExistingRecording(true);
						
				} else if(getMetaDataResponse.getResult() == IMAP4Response.RESULT_ERROR){
					greeting.setExistingRecording(false);
					
				} else if (getMetaDataResponse.getResult() == IMAP4Response.CONNECTION_CLOSED) {
						// notifies and registered listener that getting meta data
						// operation has
						// failed
						dispatcher.notifyListeners(
								EVENTS.GET_METADATA_GREETING_FAILED, null);
						Log.e(TAG, "GetMetaDataOperation.execute() failed due to a network error!");
						return Result.NETWORK_ERROR;
					}
				}
			}

			// notifies and registered listener that getting meta data operation
			// has finished
			dispatcher.notifyListeners(
					EVENTS.GET_METADATA_EXISTING_GREETINGS_FINISHED, null);
			return Result.SUCCEED;

		} else {
		getMetaDataResponse = getMetaData();

		// in case getting the meta data succeed
		if (getMetaDataResponse.getResult() == IMAP4Response.RESULT_OK) {
			// stores the response values
			this.variablesResponseValues = ((MetaDataResponse) getMetaDataResponse)
			.getVariables();

			if (this.type == OperationTypes.TYPE_GET_META_DATA_GREETINGS_DETAILS) {
				// store data into model
				ModelManager.getInstance()
				.saveMetadata(variablesResponseValues);

				// notifies and registered listener that getting meta data
				// operation has finished
				dispatcher.notifyListeners(
						EVENTS.GET_METADATA_GREETING_DETAILS_FINISHED, null);
			} else if (this.type == OperationTypes.TYPE_GET_META_DATA_PASSWORD) {
				// store data into model
				ModelManager.getInstance()
				.saveMetadata(variablesResponseValues);

				// notifies and registered listener that getting meta data
				// operation has finished
				dispatcher.notifyListeners(
						EVENTS.GET_METADATA_PASSWORD_FINISHED, null);
			}
			return Result.SUCCEED;
		}
		// notifies and registered listener that getting meta data operation has
		// failed
		dispatcher.notifyListeners(EVENTS.GET_METADATA_GREETING_FAILED, null);

		// in case getting meta data failed
		if (getMetaDataResponse.getResult() == IMAP4Response.RESULT_ERROR) {
			Log.e(TAG, "GetMetaDataOperation.execute() failed!");
			return Result.FAILED;
		}

		Log.e(TAG, "GetMetaDataOperation.execute() failed due to a network error!");
		return Result.NETWORK_ERROR;
	}
	}



	/**
	 * Gets the requested meta data from the server
	 * 
	 * Examples for network transactions: request: a1 GETMETADATA INBOX
	 * /private/vendor/vendor.alu/messaging/GreetingType response: METADATA
	 * "INBOX" (/private/vendor/vendor.alu/messaging/GreetingType
	 * "StandardWithNumber") a1 OK GETMETADATA complete
	 * 
	 * request: a3 GETMETADATA INBOX
	 * /private/vendor/vendor.alu/messaging/GreetingTypesAllowed response:
	 * METADATA "INBOX"
	 * (/private/vendor/vendor.alu/messaging/GreetingTypesAllowed
	 * "Personal,StandardWithName,StandardWithNumber") a3 OK GETMETADATA
	 * complete
	 * 
	 * @return (IMAP4Response != null) the get meta data response.
	 */
	private IMAP4Response getMetaData() {

		Logger.d(TAG,
				"GetMetaDataOperation.getMetaData() - requested variables are "
				+ this.variables);

		// creates the get meta data command
		byte[] command = new StringBuilder(Constants.IMAP4_TAG_STR)
		.append("GETMETADATA INBOX ").append(this.variables)
		.append("\r\n").toString().getBytes();

		// executes the get meta data command and returns the response
		return executeIMAP4Command(this.transactionType, command);
	}

	private IMAP4Response getMetaDataAttachedFile(String fileName) {

		Log.d(TAG,
				"GetMetaDataOperation.getMetaData() - requested variables are "
				+ this.variables);

		// creates the get meta data command
		byte[] command = new StringBuilder(Constants.IMAP4_TAG_STR)
		.append("GETMETADATA INBOX ").append(this.variables)
		.append("\r\n").toString().getBytes();

		// executes the get meta data command and returns the response
		return executeGetBodyTextCommand( new String(command), fileName);
	}
}