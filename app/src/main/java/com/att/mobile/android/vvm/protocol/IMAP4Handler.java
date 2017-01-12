
package com.att.mobile.android.vvm.protocol;

import android.content.Context;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.OperationsQueue;
import com.att.mobile.android.vvm.control.files.FileCloserTask;
import com.att.mobile.android.vvm.control.files.FileWriterQueue;
import com.att.mobile.android.vvm.control.files.FileWriterTask;
import com.att.mobile.android.vvm.control.files.VvmFileUtils;
import com.att.mobile.android.vvm.control.network.NetworkHandler;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.TRANSACTIONS;
import com.att.mobile.android.vvm.model.Message;
import com.att.mobile.android.vvm.model.db.MessageDo;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.protocol.response.FetchHeadersResponse;
import com.att.mobile.android.vvm.protocol.response.FetchResponse;
import com.att.mobile.android.vvm.protocol.response.GetQuotaResponse;
import com.att.mobile.android.vvm.protocol.response.IMAP4Response;
import com.att.mobile.android.vvm.protocol.response.SelectResponse;
import com.att.mobile.android.vvm.protocol.response.StoreResponse;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Locale;

import javax.net.ssl.SSLException;

public class IMAP4Handler {

	private static final int PARSE_STATE_NONE = 0;
	private static final int PARSE_STATE_FOUND_AMR = 1;
	private static final int PARSE_STATE_CHUNCKING_AMR = 2;
	private static final int PARSE_STATE_PARSING_TEXT_ITEM = 3;
	private static final int PARSE_STATE_END = 4;

	private IMAP4Parser parser;
	private NetworkHandler networkHandler;
	private Context context;
	private boolean init = false;
	private ModelManager modelManager;
	private long chunkingTimeStamp = 0;

	// strings and constants values
	private final static byte SPACE = 32;
	private final static char CHAR_OPEN_BRACKET = '(';
	private final static String STR_NO = " no";
	private final static String STR_BAD = " bad";
	private final static String STR_EXISTS = " exists";
	private final static String STR_OK = " ok";
	private final static String STR_BYE = "* bye";
	private final static String STR_METADATA = "metadata";
	private static final String TAG = "IMAP4Handler";
	private final static String STR_MESSAGE = " message";

	/**
	 * SingletonHolder is loaded on the first execution of Singleton.getInstance() or the first access to
	 * SingletonHolder.INSTANCE, not before.
	 */
	private static class IMAP4HandlerHolder {
		private static final IMAP4Handler INSTANCE = new IMAP4Handler();
	}

	public static IMAP4Handler getInstance() {
		return IMAP4HandlerHolder.INSTANCE;
	}

	private IMAP4Handler() {
	}

	/**/
	public void init(Context context) {
		synchronized (this) {
			if (!init) {
				this.context = context;
				networkHandler = new NetworkHandler();

				parser = new IMAP4Parser(context, networkHandler);
				modelManager = ModelManager.getInstance();
				init = true;

			}
		}
	}

	/**
	 * this method executes an IMAP4 command by; 1. sending the command using the write method of the tcp handler. 2.
	 * Receiving the response and return it to caller.
	 * 
	 */
	public synchronized IMAP4Response executeImapCommand(byte transaction,
			byte[] command) {

		IMAP4Response response = null;

		try {
			Logger.d(TAG,
					"IMAP4Handler.executeImapCommand() send:"
							+ new String(command));
			// send command
			networkHandler.send(command);
			// Receive a parsed response
			response = receiveImapResponse(transaction, Constants.IMAP4_TAG_STR);
		} catch (ParseException e) {
			Log.e(TAG, e.toString(), e);
			response = new IMAP4Response(IMAP4Response.RESULT_ERROR,
					e.getMessage());
		}catch (Exception e) {
			networkHandler.close();
			OperationsQueue.getInstance().notifyDisconnect();
			Log.e(TAG, e.toString(), e);
			response = new IMAP4Response(IMAP4Response.CONNECTION_CLOSED,
					e.getMessage());
		} 
		return response;
	}

	/**
	 * this method executes an IMAP4 command by; 1. sending the command using the write method of the tcp handler. 2.
	 * Receiving the response and return it to caller.
	 * 
	 */
	public synchronized void executeImapCommandWithoutResponse(	byte transaction, byte[] command, int chunkSize ) {
		
		try {
			Logger.d(TAG, "executeImapCommandWithoutResponse() send: " + new String(command));
			// in case no chunk size was passed
			if (chunkSize == -1) {
				// sends the command as a single chunk
				networkHandler.send(command);
				return;
			}

			// holds the number bytes sent and still to send
			int numberOfBytesSent = 0;
			int numberOfBytesStillToSend = command.length;

			// as long as chunk size is smaller than the amount of bytes still
			// to send
			while (chunkSize < numberOfBytesStillToSend) {
				// sends the next chunk of bytes, and updated the counters
				networkHandler.send(command, numberOfBytesSent, chunkSize);
				numberOfBytesSent += chunkSize;
				numberOfBytesStillToSend -= chunkSize;
			}

			// when the amount of bytes still to send is smaller than the chunk
			// size,
			// and there are still some bytes to be sent, sends them
			if (numberOfBytesStillToSend > 0) {
				networkHandler.send(command, numberOfBytesSent,	numberOfBytesStillToSend);
			}

		} catch (Exception e) {
			networkHandler.close();
			OperationsQueue.getInstance().notifyDisconnect();
			Log.e(TAG, e.toString(), e);
		} 
	}
	public synchronized IMAP4Response executeGetGreetingFileCommand(String command, String fileName) {

		IMAP4Response response = null;

		try {
			Log.d(TAG, "IMAP4Handler.getAttachment() send:" + command);
			// send command
			networkHandler.send(command.getBytes());
			chunkingTimeStamp = System.currentTimeMillis();
			// Receive a parsed response
			response = receiveGreetingFile(fileName);
		} catch (IOException e) {
			networkHandler.close();
			OperationsQueue.getInstance().notifyDisconnect();
			Log.e(TAG, e.getMessage(), e);
			response = new IMAP4Response(IMAP4Response.CONNECTION_CLOSED,
					e.getMessage());

		}
		return response;
	}

	public synchronized IMAP4Response executeGetBodyTextCommand(String command,
			String fileName, MessageDo messageDo) {

		IMAP4Response response = null;

		try {
			Logger.d(TAG, "IMAP4Handler.getAttachment() send:"
					+ command);
			// send command
			networkHandler.send(command.getBytes());
			chunkingTimeStamp = System.currentTimeMillis();
			// Receive a parsed response
			response = receiveBodyText(fileName, messageDo);
		} catch (Exception e) {
			networkHandler.close();
			OperationsQueue.getInstance().notifyDisconnect();
			Log.e(TAG, e.getMessage(), e);
			response = new IMAP4Response(IMAP4Response.CONNECTION_CLOSED,
					e.getMessage());

		}
		return response;
	}

	/**
	 * Handles a request/response pair. This is a convenience method used internally to handle sending a request to the
	 * IMAP server as well as receiving the response. If the response starts with a "-" sign, and thus denotes a
	 * protocol error, an exception is raised to reflect it. Note that the request is only sent if it doesn't equal
	 * null, while the response is always being waited for.
	 * 
	 * @see Exception
	 */
	private synchronized IMAP4Response receiveImapResponse(byte transaction,
			String tag) throws IOException, SSLException, ParseException {
		int messageExists = 0;
		int quota = 0;
		StringBuffer textBuf = null;
		int index = -1;
		boolean isEnd = false;

		String nextLine = null;
		String nextLineLower = null;
		IMAP4Response response = null;
		ArrayList<Long> uids = null;

		// read first line of response
		nextLine = new String(networkHandler.receiveNextData());
		Logger.d(TAG,
				"IMAP4Handler.receiveImapResponse() received: " + nextLine);
		nextLineLower = IMAP4Parser.toLowerCase(nextLine);

		while (!isEnd) {
			if (nextLineLower.startsWith(STR_BYE)) {
				if (transaction == TRANSACTIONS.TRANSACTION_LOGOUT) {
					return new IMAP4Response(IMAP4Response.RESULT_OK, STR_OK);
				} else {
					Logger.d(TAG,
							"BYE recieved - Connection closed by server");
					return new IMAP4Response(IMAP4Response.CONNECTION_CLOSED,
							nextLineLower);
				}
			}
			// check if we got to the last line of the response
			isEnd = nextLine.trim().startsWith(tag);

			/*
			 * The NO response indicates an operational error message from the server. When tagged, it indicates
			 * unsuccessful completion of the associated command. The un tagged form indicates a warning; the command
			 * can still complete successfully. The human-readable text describes the condition.
			 */
			/*
			 * The BAD response indicates an error message from the server. When tagged, it reports a protocol-level
			 * error in the client's command; the tag indicates the command that caused the error. The un tagged form
			 * indicates a protocol-level error for which the associated command can not be determined; it can also
			 * indicate an internal server failure. The human-readable text describes the condition.
			 */
			if (isEnd) {
				int errIndex = -1;
				int endOfMsgIndex = nextLineLower.indexOf(CHAR_OPEN_BRACKET);
				if ((errIndex = nextLineLower.indexOf(STR_NO)) >= 0) {
					endOfMsgIndex = endOfMsgIndex > errIndex ? endOfMsgIndex
							: nextLineLower.length();
					response = new IMAP4Response(IMAP4Response.RESULT_ERROR,
							nextLineLower.substring(errIndex + STR_NO.length(),
									endOfMsgIndex));
					return response;
				} else if ((errIndex = nextLineLower.indexOf(STR_BAD)) >= 0) {
					endOfMsgIndex = endOfMsgIndex > errIndex ? endOfMsgIndex
							: nextLineLower.length();
					response = new IMAP4Response(IMAP4Response.RESULT_ERROR,
							nextLineLower.substring(
									errIndex + STR_BAD.length(), endOfMsgIndex));
					return response;
				}
			}

			switch (transaction) {
				case TRANSACTIONS.TRANSACTION_SELECT_INBOX:

					if ((index = nextLineLower.indexOf(STR_EXISTS)) >= 0) {
						messageExists = Integer
								.parseInt(nextLine.substring(
										(nextLine.lastIndexOf(SPACE, index - 1)) + 1,
										index).trim());
					}
					if (isEnd) {
						response = new SelectResponse(IMAP4Response.RESULT_OK);
						((SelectResponse) response).setExists(messageExists);
						return response;
					}
					break;

				case TRANSACTIONS.TRANSACTION_GETQUOTA:

					if ((index = nextLineLower.indexOf(STR_MESSAGE)) >= 0) {
						messageExists = Integer.parseInt(nextLine.substring((nextLine.indexOf(SPACE, index+1)) + 1, (nextLine.indexOf(SPACE, index+1)) + 3).trim());
						quota = Integer.parseInt(nextLine.substring((nextLine.lastIndexOf(SPACE)) + 1, (nextLine.lastIndexOf(SPACE)) + 3).trim());
					}
					if (isEnd) {
						response = new GetQuotaResponse(IMAP4Response.RESULT_OK);
						((GetQuotaResponse) response).setExists(messageExists);
						((GetQuotaResponse) response).setQuota(quota);
						return response;
					}
					break;

				case TRANSACTIONS.TRANSACTION_FETCH_HEADERS:
					if (uids == null) {
						uids = new ArrayList<Long>();
					}
					if (isEnd) {
						// after we got all messages from the server we now want to
						// sync our DB by
						// deleting all messages we have locally and are not saved
						// or forwarded and are
						// not present on the server (i.e, deleted from server)
						modelManager.deleteMessagesNotOnServer(uids);
//						modelManager.removeServerNoLongerExistMessageUIDs(uids);
						// go update notification since new messages num may have
						// been changed
						((VVMApplication) (context.getApplicationContext())).updateNotificationAfterRefresh();

						response.setResult(IMAP4Response.RESULT_OK);
						return response;
					}
					if (response == null) {
						response = new FetchHeadersResponse();
					}
					Message message = parser.parseHeader(
							(FetchHeadersResponse) response, nextLine, context);
					// check if message uid has change and if so update it
					if (message.getPreviousUid() != -1) {
						modelManager.updateUid(message.getPreviousUid(),
								message.getUid());
//						modelManager.replaceUID(message.getPreviousUid(),
//								message.getUid());
					}

					// if the message with the current uid is pending for delete we
					// ignore it
					// if its pending for mark as read we also ignore it so we do
					// not change its read status
					if (!modelManager.isMessagePendingForDelete(message.getUid())
							&& !modelManager.isMessagePendingForMarkAsRead(message
									.getUid())) {
						modelManager.updateOrInsertMessageToInbox(message);

					}
					uids.add(message.getUid());
					break;

				case TRANSACTIONS.TRANSACTION_DELETE:
				case TRANSACTIONS.TRANSACTION_READ:
				case TRANSACTIONS.TRANSACTION_TUI_SKIP:
					if (response == null) {
						response = new StoreResponse();
					}

					if (isEnd) {
						response.setResult(IMAP4Response.RESULT_OK);
						return response;
					}
					parser.parseStore((StoreResponse) response, nextLine);
					break;

				case TRANSACTIONS.TRANSACTION_LOGIN:
				case TRANSACTIONS.TRANSACTION_EXPUNGE:
				case TRANSACTIONS.TRANSACTION_LOGOUT:
				case TRANSACTIONS.TRANSACTION_NOOP:
				case TRANSACTIONS.TRANSACTION_SET_METADATA:
				case TRANSACTIONS.TRANSACTION_XCHANGE_TUI_PASSWORD:
				case TRANSACTIONS.TRANSACTION_FETCH_GREETIGNS_BODIES:
				case TRANSACTIONS.TRANSACTION_SEND_GREETING_DATA:
					if (isEnd) {
						if (nextLineLower.indexOf(STR_OK) >= 0) {
							return new IMAP4Response(IMAP4Response.RESULT_OK,
									STR_OK);
						} else {
							return new IMAP4Response(IMAP4Response.RESULT_ERROR,
									STR_BAD);
						}
					}
					break;

				case TRANSACTIONS.TRANSACTION_GET_METADATA:
					if (isEnd) {
					if (textBuf != null){
						response = parser.parseMetaData(textBuf.toString());
						response.setResult(IMAP4Response.RESULT_OK);
				}else {
					response = new IMAP4Response(IMAP4Response.RESULT_ERROR, STR_BAD);
				}
						return response;
					}
					if (textBuf == null
							&& (nextLineLower.indexOf(STR_METADATA) > 0)) {
						textBuf = new StringBuffer(nextLine);
					} else if (textBuf != null) {
						textBuf.append(nextLine);
					}
					break;

			case TRANSACTIONS.TRANSACTION_FETCH_TRANSCRIPTION:
				if (isEnd) {
					if (textBuf != null){
					response = parser.parseTranscription(textBuf.toString());
					response.setResult(IMAP4Response.RESULT_OK);
				}else {
					response = new IMAP4Response(IMAP4Response.RESULT_ERROR, STR_BAD);
				}
					return response;
				}
				if (textBuf == null) {
					textBuf = new StringBuffer(nextLine);
				} else if (!nextLine.startsWith("--")) {
					textBuf.append(nextLine);
				}
				break;

			case TRANSACTIONS.TRANSACTION_FETCH_BODYSTRUCTURE_LIST:
				if (isEnd) {
					if (textBuf != null){
					response = parser.parseBodiesStructure(textBuf.toString());
					response.setResult(IMAP4Response.RESULT_OK);
					}else {
						response = new IMAP4Response(IMAP4Response.RESULT_ERROR, STR_BAD);
					}
					return response;
				}
				if (textBuf == null) {
					textBuf = new StringBuffer(nextLine);
				} else  {
					textBuf.append(nextLine);
				}
				break;
			default: 
				break;

			}// end of switch

			if (!isEnd) {
				// read next line of the response
				nextLine = new String(networkHandler.receiveNextData());
				Logger.d(TAG,
						"IMAP4Handler.receiveImapResponse() received: "
								+ nextLine);
				nextLineLower = IMAP4Parser.toLowerCase(nextLine);
			}
		}// end of while

		return new IMAP4Response(IMAP4Response.RESULT_ERROR, STR_BAD);
	}

	private int extractDataLength(String data) {

		int start = data.indexOf("{");
		int end = data.indexOf("}", start);
		return Integer.parseInt(data.substring(start + 1, end).trim());
	}


	private synchronized IMAP4Response receiveGreetingFile(String fileName) throws IOException {

		IMAP4Response response = null;
		BufferedOutputStream outputStream = null;
		byte[] actualRead = null;
		int dataLengthRemained = 0;
		String nextStr = "";
		int status = PARSE_STATE_NONE;

		Log.d(TAG, "receiveGreetingFile started");
		try{
			while (nextStr != null) {
				switch (status) {

				case PARSE_STATE_NONE:

					nextStr = new String(networkHandler.receiveNextData());

					if (nextStr != null && nextStr.toLowerCase(Locale.US).startsWith(STR_BYE)) {
						throw new IOException("connection closed");
					}
					// check if end
					else if (nextStr != null
							&& nextStr.indexOf(Constants.IMAP4_TAG_STR) > -1) {
						if (nextStr.indexOf(" GETMETADATA failed") > 0) {
							return new IMAP4Response(IMAP4Response.RESULT_ERROR);
						}
						status = PARSE_STATE_END;
					} else {
						String lowerCaseNextStr = nextStr.toLowerCase(Locale.US);

						if (lowerCaseNextStr.contains("~{")) {
							dataLengthRemained = extractDataLength(nextStr);
						} else {
							dataLengthRemained -= (nextStr.length() + "\r\n".length());
						}

						if (lowerCaseNextStr.contains("content-type: audio/amr")) {
							status = PARSE_STATE_FOUND_AMR;
						}
					}
					break;

				case PARSE_STATE_FOUND_AMR:

					nextStr = new String(networkHandler.receiveNextData());

					if (nextStr != null && nextStr.toLowerCase(Locale.US).startsWith(STR_BYE)) {
						throw new IOException("connection closed");
					}
					// check if end
					else if (nextStr != null
							&& nextStr.indexOf(Constants.IMAP4_TAG_STR) > -1) {
						status = PARSE_STATE_END;
					}

					dataLengthRemained -= (nextStr.length() + "\r\n".length());

					// in case headers have been fully read and the next data is
					// file's data
					if (nextStr.equals("")) {
						FileOutputStream openFileOutput = context.openFileOutput(fileName, Context.MODE_PRIVATE);
						outputStream = new BufferedOutputStream(openFileOutput);

						status = PARSE_STATE_CHUNCKING_AMR;
					}

					break;

				case PARSE_STATE_CHUNCKING_AMR:

					actualRead = networkHandler.receiveNextChunk(Math.min(100, dataLengthRemained));
					dataLengthRemained -= actualRead.length;
					Log.d(TAG, "receiveGreetingFile PARSE_STATE_CHUNCKING_AMR dataLengthRemained = "
							+ dataLengthRemained);

					outputStream.write(actualRead);
					outputStream.flush();

					long currentTime = System.currentTimeMillis();
					Log.d(TAG, "receiveBodyText() get chunk took " + (currentTime - chunkingTimeStamp) + " millis");

					// send noop
					if (currentTime - chunkingTimeStamp >= 20000) {

						chunkingTimeStamp = currentTime;
						Log.d(TAG, "receiveBodyText() send: NOOP_TAG NOOP");

						networkHandler.send("NOOP_TAG NOOP\r\n".getBytes());
					}

					if (dataLengthRemained == 0) {

						Log.d(TAG, "receiveGreetingFile ended");

						status = PARSE_STATE_NONE;

					}

					break;

				case PARSE_STATE_END:
					return new IMAP4Response(IMAP4Response.RESULT_OK);
				}
			}
		} finally{
			if(outputStream != null){
				outputStream.close();
			}

		}
		return response;

	}
	/**
	 * parse fetch response to get and save the body transcription and the audio attachment from the full body text.
	 * 
	 * @param fileName
	 * @return
	 * @throws IOException
	 */
	private synchronized IMAP4Response receiveBodyText(String fileName,
			MessageDo messageDo) throws IOException, SSLException {
		// Note: in case server's response contains more than a single file
		// (for example when a forwarded message is received), all files will be
		// saved
		// as a SINGLE file (they will be concatenated to each other to create a
		// single audio file)

		// holds message file(s) size, and the number of bytes still to be read
		int messageFilesBytesSize = 0;
		int bytesStillToRead = 0;

		// holds the current chunk number for the CURRENT received file
		int currentChunkNumber = 0;

		int status = PARSE_STATE_NONE;
		ArrayList<String> textAttachments = new ArrayList<String>();
		StringBuffer currentTextAttachment = null;
		boolean isDeliveryStatus = false;
		String nextStr = null;
		byte[] actualRead = null;
		// Store chunks of files if not ended with LF, so we can provide the
		// file writer only chunks that will be decoded fromm base64
		// successfully
		ByteArrayOutputStream currentChunkBuffer = null;
		IMAP4Response response = null;
		int numOfAmrFilesFound = 0;

		// if file name already known we have it so ignore file data in response
		// and get only transcription
		boolean skipFileFetching = (messageDo.getFileName() != null && messageDo.getFileName().length() > 0);

		// read first line of response
		nextStr = new String(networkHandler.receiveNextData());
		Logger.d(TAG, "IMAP4Handler.receiveBodyText() received: "
				+ nextStr);

		// TODO - Royi - move into the while loop (first thing)
		if (nextStr != null && nextStr.toLowerCase(Locale.US).startsWith(STR_BYE)) {
			throw new IOException("connection closed");
		}
		// check if end
		else if (nextStr != null
				&& nextStr.indexOf(Constants.IMAP4_TAG_STR) > -1) {
			status = PARSE_STATE_END;
		}

		while (nextStr != null) {
			switch (status) {

				case PARSE_STATE_NONE:
					String lowerCaseNextStr = nextStr.toLowerCase(Locale.US);
					// append all text body parts to the transcription
					if (lowerCaseNextStr.contains("content-type: text/plain")) {
						status = PARSE_STATE_PARSING_TEXT_ITEM;
					}
					// check that we don't have to skip fetching the file and that
					// it is not just transcription fetching
					// then check header contains amr file content type
					else if (!skipFileFetching
							&& lowerCaseNextStr.contains("content-type: audio/amr")) {
						status = PARSE_STATE_FOUND_AMR;

						// updates the number of files for the current handled
						// message
						++numOfAmrFilesFound;
					}
					// Content-type: multipart/report; report-type="delivery-status"
					// Content-Type: Message/Delivery-Status

					else if (lowerCaseNextStr
							.contains("content-type: multipart/report")
							&& lowerCaseNextStr
									.contains("report-type=\"delivery-status\"")
							|| lowerCaseNextStr
									.contains("content-type: message/delivery-status")) {
						isDeliveryStatus = true;

						Logger.d(TAG,
								"IMAP4Handler.receiveBodyText() message type is delivery-status");
					}
					break;

				//
				case PARSE_STATE_FOUND_AMR: {
					// in case headers have been fully read and the next data is
					// file's data
					if (nextStr.equals("")) {
						status = PARSE_STATE_CHUNCKING_AMR;
					}
					// in case file's size hasn't been read yet from the headers
					else if (bytesStillToRead == 0) {
						// in case the current header line contains file's size
						int contentLenghtPosition = nextStr
								.indexOf("Content-Length:");
						if (contentLenghtPosition >= 0) {
							// reads file's size and updates message's file(s) size,
							// bytes still to read and bytes read counters
							bytesStillToRead = Integer.parseInt(nextStr.substring(
									contentLenghtPosition
											+ "Content-Length:".length() + 1)
									.trim());
							messageFilesBytesSize += bytesStillToRead;

							// new handled file - updates that no chunks has been
							// stored yet for the current file
							currentChunkNumber = 0;
						}
					}
					break;
				}

				case PARSE_STATE_CHUNCKING_AMR: {

					Logger.d(TAG,
							"IMAP4Handler.receiveBodyText() PARSE_STATE_CHUNCKING_AMR bytesStillToRead = "
									+ bytesStillToRead);
					// as long as there are bytes still to be read
					while (bytesStillToRead > 0) {
						// in case the current read bytes size is greater than 0
						if (actualRead != null && actualRead.length > 0) {
							// update number of bytes still to read
							bytesStillToRead -= actualRead.length;

							if (currentChunkBuffer == null) {
								currentChunkBuffer = new ByteArrayOutputStream();
							}
							currentChunkBuffer.write(actualRead);
							byte[] currentChunk = currentChunkBuffer.toByteArray();

							// go write chunk if it ends propertly with LF or its
							// the last chunk
							if (bytesStillToRead == 0
									|| currentChunk[currentChunk.length - 1] == 0x0A) {
								// updates the current chunk number of the file
								++currentChunkNumber;

								// creates a file writer task to save file's chunk
								// into the file system
								FileWriterTask task = new FileWriterTask(messageDo,
										numOfAmrFilesFound, fileName,
										messageFilesBytesSize, currentChunk,
										currentChunkNumber);
								FileWriterQueue.getInstance(context)
										.enqueueFileWriterTask(task);

								// reset data holders
								currentChunkBuffer = null;
								actualRead = null;
								currentChunk = null;
							} else {
								Logger.d(
										TAG,
										"IMAP4Handler.receiveBodyText() chunk not ending with LF or not last chunk! wait to append next chunk...");
							}
						}

						Logger.d(TAG,
								"IMAP4Handler.receiveBodyText() check if more bytes to read = "
										+ bytesStillToRead);

						// in case there are more bytes to be read
						if (bytesStillToRead > 0) {

							long currentTime = System.currentTimeMillis();
							Logger.d(TAG,
									"IMAP4Handler.receiveBodyText() get chunk took "
											+ (currentTime - chunkingTimeStamp)
											+ " millis");

							// send noop
							if (currentTime - chunkingTimeStamp >= 20000) {
								chunkingTimeStamp = currentTime;
								Logger.d(TAG,
										"IMAP4Handler.receiveBodyText() send: NOOP_TAG NOOP");

								networkHandler.send("NOOP_TAG NOOP\r\n".getBytes());
							}

							Logger.d(TAG,
									"IMAP4Handler.receiveBodyText() going to get next chunk bytesStillToRead = "
											+ bytesStillToRead);

							// reads the next chunk of bytes
							actualRead = networkHandler
									.receiveNextChunk(bytesStillToRead);

							Logger.d(TAG,
									"IMAP4Handler.receiveBodyText() actual read = "
											+ actualRead.length);

							// in case the connection has been closed by the server
							if (new String(actualRead).toLowerCase(Locale.US).startsWith(
									STR_BYE)) {
								// deletes the file from file system and throws an
								// exception
								VvmFileUtils.deleteInternalFile(context, fileName);
								throw new IOException("connection closed");
							}
						}
					}

					status = PARSE_STATE_NONE;
					break;
				}
				case PARSE_STATE_PARSING_TEXT_ITEM:
					// get the transcription
					while (!nextStr.equals("")) {
						nextStr = new String(networkHandler.receiveNextData());
						Logger.d(TAG,
								"IMAP4Handler.receiveBodyText() received: "
										+ nextStr);
						if (nextStr.toLowerCase(Locale.US).startsWith(STR_BYE)) {
							throw new IOException("connection closed");
						}
					}
					currentTextAttachment = new StringBuffer();
					while (!nextStr.startsWith("--")) {
						currentTextAttachment.append(nextStr);
						nextStr = new String(networkHandler.receiveNextData());
						Logger.d(TAG,
								"IMAP4Handler.receiveBodyText() received: "
										+ nextStr);
						if (nextStr.toLowerCase(Locale.US).startsWith(STR_BYE)) {
							throw new IOException("connection closed");
						}
					}

					// check if text is not an ALU link - we don't want the user to
					// see it
					// "Additional information on .lvp audio files can be found at http://www.alcatel-lucent.com/mvp"
					if (!currentTextAttachment.toString().equals(
							Constants.ALU_LINK_TEXT)) {
						textAttachments.add(currentTextAttachment.toString());
					}
					status = PARSE_STATE_NONE;
					break;

				case PARSE_STATE_END:
					/*
					 * The NO response indicates an operational error message from the server. When tagged, it indicates
					 * unsuccessful completion of the associated command. The untagged form indicates a warning; the
					 * command can still complete successfully. The human-readable text describes the condition.
					 */
					/*
					 * The BAD response indicates an error message from the server. When tagged, it reports a
					 * protocol-level error in the client's command; the tag indicates the command that caused the
					 * error. The untagged form indicates a protocol-level error for which the associated command can
					 * not be determined; it can also indicate an internal server failure. The human-readable text
					 * describes the condition.
					 */
					int errIndex = -1;
					int endOfMsgIndex = nextStr.indexOf(CHAR_OPEN_BRACKET);
					if ((errIndex = nextStr.indexOf(STR_NO)) >= 0
							|| (errIndex = nextStr.indexOf(STR_NO.toUpperCase(Locale.US))) >= 0) {
						endOfMsgIndex = endOfMsgIndex > errIndex ? endOfMsgIndex
								: nextStr.length();
						response = new IMAP4Response(IMAP4Response.RESULT_ERROR,
								nextStr.substring(errIndex + STR_NO.length(),
										endOfMsgIndex));

					} else if ((errIndex = nextStr.indexOf(STR_BAD)) >= 0
							|| (errIndex = nextStr.indexOf(STR_BAD.toUpperCase(Locale.US))) >= 0) {
						endOfMsgIndex = endOfMsgIndex > errIndex ? endOfMsgIndex
								: nextStr.length();
						response = new IMAP4Response(IMAP4Response.RESULT_ERROR,
								nextStr.substring(errIndex + STR_BAD.length(),
										endOfMsgIndex));
					} else {
						response = new FetchResponse();
						response.setResult(IMAP4Response.RESULT_OK);
						String transcription = null;

						if (isDeliveryStatus) {
							StringBuilder fullTrascription = new StringBuilder();
							if (!textAttachments.isEmpty()) {
								for (String str : textAttachments) {
									fullTrascription.append(str).append("\n");
								}
								transcription = fullTrascription.toString();
							}
							((FetchResponse) response)
									.setIsDeliveryStatusMessage(true);
						} else {
							if (!textAttachments.isEmpty()) {
								transcription = textAttachments.get(0);
							}
						}

						if (transcription != null) {
							Logger.d(TAG, "receiveBodyText() fetch Nuance transcription completed successfully, uid = "
									+ messageDo.getUid()+ " Nuance transcription = "+transcription);

							((FetchResponse) response).setMessageTranscription(transcription);
//							modelManager.setSharedPreference(KEYS.IS_TC_ACCEPTED, modelManager.getSharedPreferenceValue(KEYS.IS_TC_ACCEPTED, Boolean.class, true));	
							}
//						Logger.d(TAG, "setSharedPreference");
					}

					if (messageFilesBytesSize > 0) {
						// creates and enqueues a file closer task (since all
						// message files were downloaded)
						FileCloserTask fileCloserTask = new FileCloserTask(
								messageDo, fileName, messageFilesBytesSize);
						FileWriterQueue.getInstance(context).enqueueFileCloserTask(
								fileCloserTask);
					}

					return response;
			}
			// advance to the next line of data from the network response
			if (status != PARSE_STATE_CHUNCKING_AMR) {
				nextStr = new String(networkHandler.receiveNextData());

				if (!skipFileFetching
						|| status == PARSE_STATE_PARSING_TEXT_ITEM) {
					Logger.d(TAG,
							"IMAP4Handler.receiveBodyText() received: "
									+ nextStr);
				}

				if (nextStr.toLowerCase(Locale.US).startsWith(STR_BYE)) {
					throw new IOException("connection closed");
				}

				// check if we got to the last chunk of the response
				if (nextStr.indexOf(Constants.IMAP4_TAG_STR) > -1) {
					if (skipFileFetching) {
						Logger.d(TAG,
								"IMAP4Handler.receiveBodyText() received: "
										+ nextStr);
					}
					status = PARSE_STATE_END;
				}
			}
		} // end of while
		return new IMAP4Response(IMAP4Response.RESULT_ERROR, null);
	}

	/**
	 * connect via the network handler
	 */
	public synchronized boolean connect() {
		boolean res = false;
		String host = modelManager.getSharedPreferenceValue(
				Constants.KEYS.PREFERENCE_HOST, String.class, null);

		String useSslStr = context.getString(R.string.useSSL);
		boolean useSSL = ((useSslStr != null) && (useSslStr.equals("true")));

		// get the debug use SSL flag if set by debug screen
		if (VVMApplication.isDebugMode()) {
			boolean useSslDebug = modelManager.getSharedPreferenceValue(
					Constants.KEYS.PREFERENCE_DEBUG_SSL_ON, Boolean.class, useSSL);
			useSSL = useSslDebug;
		}

		if (host != null) {
			int timeout = Integer.parseInt(context.getString(R.string.socketConnectionTimeout).trim());
			if (useSSL) {
				int sslPort = Integer.valueOf(modelManager.getSharedPreferenceValue(
						Constants.KEYS.PREFERENCE_SSL_PORT, String.class, "0"));
				// if no port on shared prefs - get default from config file
				if (sslPort == 0) {
					sslPort = Integer.parseInt(context.getString(R.string.sslPort).trim());
				}
				res = networkHandler.connectSSL(context, host, sslPort, timeout);
			} else {
				// port in not taken from the SMS. we use hard coded imap port
				// number 143
				int port = Integer.valueOf(modelManager.getSharedPreferenceValue(
						Constants.KEYS.PREFERENCE_PORT, String.class, "0"));
				// if no port on shared prefs - get default from config file
				if (port == 0) {
					port = Integer.parseInt(context.getString(R.string.port).trim());
				}
				res = networkHandler.connect(host, port, timeout);
			}
			if (res) {
				Log.d(TAG, "IMAP4Handler.connect() connected successfully");
			} else {
				Log.e(TAG, "IMAP4Handler.connect() connection failed");
			}
		} else {
			Log.e(TAG, "Host is not set!");
		}

		return res;
	}

	public synchronized boolean isConnected() {
		return (networkHandler != null && networkHandler.isConnected());
	}

	public synchronized void close() {
		networkHandler.close();
	}
}
