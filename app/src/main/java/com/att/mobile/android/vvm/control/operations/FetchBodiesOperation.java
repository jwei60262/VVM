/**
 * 
 */
package com.att.mobile.android.vvm.control.operations;

import java.util.ArrayList;
import java.util.Hashtable;

import android.content.Context;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.Dispatcher;
import com.att.mobile.android.vvm.control.files.VvmFileUtils;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.Constants.TRANSACTIONS;
import com.att.mobile.android.vvm.model.db.MessageDo;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.protocol.BodyPart;
import com.att.mobile.android.vvm.protocol.BodyStructure;
import com.att.mobile.android.vvm.protocol.response.FetchBodiesStructureResponse;
import com.att.mobile.android.vvm.protocol.response.FetchResponse;
import com.att.mobile.android.vvm.protocol.response.IMAP4Response;
import com.att.mobile.android.vvm.watson.WatsonHandler;

/**
 * An operation which performs fetching messages bodies (audio file and its
 * transcription) from the server.
 * 
 * 
 * Every IMAP transaction is retried for up to 5 times � after 5th retry the
 * transaction is thrown away from the client network queue. For the FETCH
 * BODIES operation(*) - after the 5th failure � the transaction is then pushed
 * again to the client network queue and once executed by the client it can be
 * retried again for 5 times. Pushing the FETCH BODIES request to the queue can
 * happen up to 10 times, so totally the transaction will be retired up to 50
 * times. 
 * (*)FETCH BODIES operation - the client queries which messages has no
 * audio or transcription and for each message a FETCH BODY request is sent, so
 * that every retry is trying to send FETCH BODY requests one after the other
 * for all messages that has no audio or transcription
 * 
 * 
 */
public class FetchBodiesOperation extends Operation {
	private static final String TAG = "FetchBodiesOperation";

	/**
	 * FetchBodiesOperation constructor.
	 * 
	 * @param context
	 *            (Context != null) application's context.
	 * @param dispatcher
	 *            (Dispatcher != null) a dispatcher holding registered
	 *            listeners.
	 */
	public FetchBodiesOperation(Context context, Dispatcher dispatcher) {
		super(context);
		type = OperationTypes.TYPE_FETCH_BODIES;
		this.dispatcher = dispatcher;
	}

	/**
	 * Executes the fetch bodies operation.
	 * 
	 * @return (int) fetch bodies operation result.
	 */
	@Override
	public int execute() {
		Logger.d(TAG, "FetchBodiesOperation.execute()");

		// manage final combined result to support looping thru all messages
		// (both missing-file and missing-transcription)
		int finalResult = Operation.Result.SUCCEED;

		// gets all message ID-UID pairs of message which have no file
		MessageDo[] missingFileMessageDos = ModelManager.getInstance().getMessagesToDownload(false);
		if (missingFileMessageDos != null && missingFileMessageDos.length > 0) {
			int result = -1;
			// traverses over all message ID-UID pairs
			for (MessageDo messageDo : missingFileMessageDos) {
				// in case the current message is NOT pending for delete at the
				// server
				if (!ModelManager.getInstance().isMessagePendingForDelete(
						messageDo.getUid())) {
					// check if there's an available space on internal storage
					if (VVMApplication.isMemoryLow())// != null
														// !checkForAvailableSpace())
					{
						// notifies any registered listeners that fetching
						// message bodies failed
						// due to no storage space
						dispatcher.notifyListeners(
								EVENTS.RETRIEVE_BODIES_FAILED_NOT_ENOUGH_SPACE,
								null);

						return Result.FAILED_NOT_ENOUGH_SPACE;
					}
					Logger.d(TAG, "FetchBodiesOperation.execute() fetching missedfile message Uid = "+messageDo.getUid());

					// fetches the message from the server
					result = fetchMessage(messageDo);
					if (result != Operation.Result.SUCCEED) {
						WatsonHandler.resetToken();
						return result;
					}
				}
			}
		}
		Logger.d(TAG, "FetchBodiesOperation: all missing files fetched");

		// gets all message ID-UID pairs of message which have file but have no transcription
		MessageDo[] missingTranscriptionMessageDos = ModelManager.getInstance().getMessagesToDownload(true);
		
		if (missingTranscriptionMessageDos != null && missingTranscriptionMessageDos.length > 0) {
			int result = -1;

			// fetch body-structure of all messages
			ArrayList<BodyStructure> bodiesStructure = fetchBodyStructureForNonTranscriptMessages(missingTranscriptionMessageDos);
			if (bodiesStructure != null) {
				Hashtable<Long, BodyStructure> bodiesStructureMap = new Hashtable<Long, BodyStructure>();
				for (BodyStructure bodyStructureItem : bodiesStructure) {
					bodiesStructureMap.put(bodyStructureItem.getUid(), bodyStructureItem);
				}

				// traverses over all message ID-UID pairs
				for (MessageDo messageDo : missingTranscriptionMessageDos) {
					// in case the current message is NOT pending for delete at the server
					if (!ModelManager.getInstance().isMessagePendingForDelete(messageDo.getUid())) {
						BodyStructure bodyStructure = (BodyStructure) bodiesStructureMap.get(messageDo.getUid());

						// bodyStructure can be null if the ALU-message-uid was updated (in ALU) due to new
						// transcription
						if (bodyStructure != null) {
							// fetches the message from the server
							Logger.d(TAG, "FetchBodiesOperation.execute() fetching missing transcription message Uid = "+messageDo.getUid());
							result = fetchTextMessage(messageDo, bodyStructure);
							if (result != Operation.Result.SUCCEED) {
								finalResult = result;
							}
						}
					}
				}
				Logger.d(TAG, "FetchBodiesOperation: all missing transcriptions fetched");
			}
		}

		// notifies any registered listeners that fetching message bodies has
		// finished
		dispatcher.notifyListeners(EVENTS.RETRIEVE_BODIES_FINISHED, null);
		if (finalResult != Operation.Result.SUCCEED){
			WatsonHandler.resetToken();
			return finalResult;
		}
		if(finalResult == Operation.Result.SUCCEED && (!ModelManager.getInstance().wasMailboxRefreshedOnStartup())){
			ModelManager.getInstance().setInboxRefreshed(true);
		}
//		WatsonHandler.resetToken();
		return Operation.Result.SUCCEED;
	}

	/**
	 * Fetches the message from the server by fetching its audio file and its
	 * transcription (if needed).
	 * 
	 * @param idUidPair
	 *            (IdUidPair != null) message's ID-UID pair.
	 * 
	 * @return (boolean) true for successful message fetch, false otherwise.
	 */
	private int fetchMessage(MessageDo messageDo) {
		// holds message's audio file name
		String userName = ModelManager.getInstance().getSharedPreferenceValue(
				Constants.KEYS.PREFERENCE_MAILBOX_NUMBER, String.class, null);

		if (userName == null) {
			return Operation.Result.FAILED;
		}

		// prepare file name
		String fileName = new StringBuilder(userName).append("_")
				.append(messageDo.getId()).append(".amr").toString();

		// fetches message's audio file and transcription from the server and
		// gets the response
		IMAP4Response fetchFileAndTranscriptionResponse = fetchFileAndTranscription(
				messageDo, fileName);

		// holds result
		int res = fetchFileAndTranscriptionResponse.getResult();

		// in case message's audio file and transcription were fetched
		// successfully
		if (res == IMAP4Response.RESULT_OK) {
			// stores message's transcription in application's database (the
			// file was already saved in application's storage at this point)
//			String transcr = ((FetchResponse) fetchFileAndTranscriptionResponse).getMessageTranscription();
//			Logger.d(TAG,
//					"FetchBodiesOperation.fetchMessage() completed successfully, uid = "
//							+ messageDo.getUid()+ " got Nuance transcription = "+transcr);
//			if(ModelManager.getInstance().isTranscriptionNotEmpty(transcr)){
//				ModelManager.getInstance().setMessageDetailsFromBodyText(
//						context,
//						messageDo.getId(),
//						messageDo.getUid(),transcr);
//			}
			Logger.d(TAG,
					"FetchBodiesOperation.fetchMessage() completed successfully, uid = "
							+ messageDo.getUid());
			ModelManager.getInstance().setMessageDetailsFromBodyText(
					context,
					messageDo.getId(),
					messageDo.getUid(),
					((FetchResponse) fetchFileAndTranscriptionResponse)
							.getMessageTranscription());
			return Operation.Result.SUCCEED;
		}

		// delete the file anyway, an error could be resulted also by a network
		// failure.
		VvmFileUtils.deleteInternalFile(context, fileName);

		if (res == IMAP4Response.RESULT_ERROR) {
			Logger.d(TAG,
					"FetchBodiesOperation.fetchMessage() failed!, uid = "
							+ messageDo.getUid());
			return Operation.Result.FAILED;
		}

		if (res == IMAP4Response.CONNECTION_CLOSED) {
			Logger.d(TAG,
					"FetchBodiesOperation.fetchMessage() failed! CONNECTION_CLOSED, uid = "
							+ messageDo.getUid());
			return Operation.Result.CONNECTION_CLOSED;
		}

		return Operation.Result.NETWORK_ERROR;
	}

	/**
	 * Fetches message's audio file and its transcription from the server.
	 * 
	 * @param idUidPair
	 *            (IdUidPair != null) message's ID and UID pair.
	 * @param fileName
	 *            (String != null) message's audio file name, to save the file
	 *            to in application's storage.
	 * 
	 * @return (IMAP4Response != null) the response for the fetch operation.
	 */
	private IMAP4Response fetchFileAndTranscription(MessageDo messageDo,
			String fileName) {
		Logger.d(TAG,
				"FetchBodiesOperation.fetchFileAndTranscription() - fetching audio file and its transcription for message with UID "
						+ messageDo.getUid());

		// creates the command for getting message's file from the server
		String command = new StringBuilder(Constants.IMAP4_TAG_STR)
				.append("UID FETCH ").append(messageDo.getUid())
				.append(" (BODY.PEEK[TEXT])\r\n").toString();

		// executes the command and returns the response
		return executeGetBodyTextCommand(command, fileName, messageDo);
	}

	/**
	 * Fetches the body-structure of list of messages
	 * 
	 * @param messagesDos - array of messages
	 * @return (ArrayList<BodyStructure>) list of body-structure.
	 */
	private ArrayList<BodyStructure> fetchBodyStructureForNonTranscriptMessages(MessageDo[] messagesDos) {
		// extract comma-separated uid's
		StringBuilder uids = new StringBuilder();
		for (MessageDo message : messagesDos) {
			uids.append(message.getUid()).append(',');
			Logger.d(TAG,
					"FetchBodiesOperation.fetchBodyStructureForNonTranscriptMessages() - fetching Nuance transcription for message with UID "
							+ message.getUid());
		}
		// remove the last comma
		uids.deleteCharAt(uids.length() - 1);

		// creates the command for getting message's file from the server
		// e.g. UID FETCH 2485,2487,2489 BODYSTRUCTURE
		String command = new StringBuilder(Constants.IMAP4_TAG_STR)
				.append("UID FETCH ").append(uids)
				.append(" BODYSTRUCTURE\r\n").toString();

		// executes the command and hold the response
		IMAP4Response response = executeIMAP4Command(TRANSACTIONS.TRANSACTION_FETCH_BODYSTRUCTURE_LIST, command
				.toString().getBytes());
		// check no errors and response is not general imap response
		if (response.getResult() == IMAP4Response.RESULT_OK && response instanceof FetchBodiesStructureResponse) {
			Logger.d(TAG,
					"FetchBodiesOperation.fetchBodyStructureForNonTranscriptMessages() - fetching Nuance transcription result OK ");
			return ((FetchBodiesStructureResponse) response).getBodyStructureList();
		} else {
			return null;
		}
	}

	/**
	 * Fetches the message from the server by fetching its transcription.
	 * 
	 * @param idUidPair (IdUidPair != null) message's ID-UID pair.
	 * @return (int) Operation.Result.
	 */
	private int fetchTextMessage(MessageDo messageDo, BodyStructure bodyStructure) {
		// loop thru all body parts and find the indexes of:
		// * transcription part (TEXT=PLAIN, x-alu-comp-reason=VTT)
		// e.g. ("TEXT" "PLAIN" ("CHARSET" "ascii" "X-SKIP_COMP" "yes") NIL NIL "7BIT" 92 0 NIL ("INLINE" NIL) ("NIL"))
		// * message part (content-type = message/rfc...)
		// e.g. (("MESSAGE" "RFC822" ("NAME" "Untitled message") NIL "Original Message" "BASE64" 15612) ... )
		int transcriptionIndex = -1;
		int messageIndex = -1;
		for (int i = 0; i < bodyStructure.getBodyParts().size(); i++) {
			BodyPart bodyPart = bodyStructure.getBodyPart(i);
			if (bodyPart.getContentType().equalsIgnoreCase("text/plain") && bodyPart.getIsVttAluReason()) {
				transcriptionIndex = i;
			} else if (bodyPart.getContentType().toLowerCase().startsWith("message/rfc")) {
				messageIndex = i;
			}
		}

		// in case of forwarded message - the entire message will be fetched again
		// (including audio and text) to support nested text parts
		if (messageIndex != -1) {
			return fetchMessage(messageDo);
		}

		// when there is no body-part for transcription - we should ignore the FETCH BODY[index]
		if (transcriptionIndex == -1) {
			Logger.d(TAG, "Nuance Transcription part is missing on messageUid=" + messageDo.getUid());
			return Operation.Result.SUCCEED;
		}

		// fetches message's transcription from the server and
		// gets the response
		IMAP4Response fetchTranscriptionResponse = fetchTranscription(
				messageDo, transcriptionIndex);
		
		// holds result
		int res = fetchTranscriptionResponse.getResult();

		// in case message's transcription were fetched successfully
		if (res == IMAP4Response.RESULT_OK) {
			// stores message's transcription in application's database
//			String transcr = ((FetchResponse) fetchTranscriptionResponse).getMessageTranscription();
//			Logger.d(TAG,
//					"FetchBodiesOperation.fetchTextMessage() completed successfully, uid = "
//							+ messageDo.getUid()+ " got Nuance transcription = "+transcr);
//			if(ModelManager.getInstance().isTranscriptionNotEmpty(transcr)){
//				ModelManager.getInstance().setMessageDetailsFromBodyText(
//						context,
//						messageDo.getId(),
//						messageDo.getUid(), transcr);
//			}
			Logger.d(TAG,
					"FetchBodiesOperation.fetchTextMessage() completed successfully, uid = "
							+ messageDo.getUid()+ " got transcription = "+((FetchResponse) fetchTranscriptionResponse)
							.getMessageTranscription());
			ModelManager.getInstance().setMessageDetailsFromBodyText(
					context,
					messageDo.getId(),
					messageDo.getUid(),
					((FetchResponse) fetchTranscriptionResponse)
							.getMessageTranscription());
			return Operation.Result.SUCCEED;
		}

		if (res == IMAP4Response.RESULT_ERROR) {
			Logger.d(TAG,
					"FetchBodiesOperation.fetchTextMessage() failed!, uid = "
							+ messageDo.getUid());
			return Operation.Result.FAILED;
		}

		if (res == IMAP4Response.CONNECTION_CLOSED) {
			Logger.d(TAG,
					"FetchBodiesOperation.fetchTextMessage() failed! CONNECTION_CLOSED, uid = "
							+ messageDo.getUid());
			return Operation.Result.CONNECTION_CLOSED;
		}

		if (res == Operation.Result.SUCCEED_NO_MESSAGES_EXIST) {
			Logger.d(TAG,
					"FetchBodiesOperation.fetchTextMessage() skipped! Nuance transcription was not found, uid = "
							+ messageDo.getUid());
			return Operation.Result.SUCCEED_NO_MESSAGES_EXIST;
		}

		return Operation.Result.NETWORK_ERROR;
	}

	/**
	 * Fetches message's transcription from the server.
	 * 
	 * @param idUidPair (IdUidPair != null) message's ID and UID pair.
	 * @return (IMAP4Response != null) the response for the fetch operation.
	 */
	private IMAP4Response fetchTranscription(MessageDo messageDo, int transcriptionIndex) {
		Logger.d(TAG,
				"FetchBodiesOperation.fetchTranscription() - fetching Nuance transcription for message with UID "
						+ messageDo.getUid());

		// creates the command for getting message's file from the server
		String command = new StringBuilder(Constants.IMAP4_TAG_STR)
				.append("UID FETCH ").append(messageDo.getUid())
				.append(" (BODY.PEEK[" + (transcriptionIndex + 1) + "])\r\n").toString();

		// executes the command and returns the response
		return executeIMAP4Command(TRANSACTIONS.TRANSACTION_FETCH_TRANSCRIPTION, command.toString().getBytes());
	}
}
