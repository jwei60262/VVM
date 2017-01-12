package com.att.mobile.android.vvm.control.operations;

import android.content.Context;

import com.att.mobile.android.vvm.control.Dispatcher;
import com.att.mobile.android.vvm.model.db.MessageDo;
import com.att.mobile.android.vvm.protocol.IMAP4Handler;
import com.att.mobile.android.vvm.protocol.response.IMAP4Response;


/**
 * An abstract class representing an operation against the server.
 * Any specific operation must extend this class and implement its execute() method.
 */
public abstract class Operation
{
	/**
	 * Holds all possible application's operation types. 
	 */
	public static enum OperationTypes
	{
		LOGIN,
		SELECT_INBOX,
		TYPE_FETCH_HEADERS,
		TYPE_FETCH_BODIES,
		DELETE,
		MARK_AS_READ,
		SKIPPED,
		
		TYPE_GET_META_DATA_GREETINGS_DETAILS,
		TYPE_GET_META_DATA_EXISTING_GREETINGS,
		TYPE_GET_META_DATA_PASSWORD,
		
		TYPE_SET_META_DATA_GENERAL,
		TYPE_SET_META_DATA_PASSWORD,
		TYPE_SET_META_DATA_GREETING_TYPE,
		TYPE_SEND_GREETING
	}
	
	/**
	 * Holds all possible operation results.
	 */
	public static class Result
	{
		static int nextReusltID = 0;
		public static final int SUCCEED = nextReusltID++;
		public static final int FAILED = nextReusltID++;
		public static final int NETWORK_ERROR = nextReusltID++;
		public static final int SUCCEED_NO_MESSAGES_EXIST = nextReusltID++;
		public static final int ALREADY_LOGGED_IN = nextReusltID++;
		public static final int FAILED_WRONG_PASSWORD = nextReusltID++;
		public static final int CONNECTION_CLOSED = nextReusltID++;
		public static final int FAILED_NOT_ENOUGH_SPACE = nextReusltID++;
	}

	/** holds a context */
	protected Context context;

	/** holds operation's type */
	protected OperationTypes type;

	/** holds the protocol handler for executing and parsing commands */
	private IMAP4Handler imap4handler = IMAP4Handler.getInstance();

	/** holds events dispatcher */
	protected Dispatcher dispatcher = null;

	
	/**
	 * Operation constructor.
	 * 
	 * @param context (Context != null) application's context.
	 */
	protected Operation(Context context)
	{
		this.context = context;
	}

	/**
	 * Executes the operation.
	 * TODO - Royi - this should be in the same package as the queue, and be package protected
	 */
	public abstract int execute();

	/**
	 * Executes operation's IMAP4 command and returns the response.
	 * 
	 * @param transactionId (byte) the transaction ID.
	 * @param command (byte[] != null) the IMAP4 command as byte-stream.
	 * 
	 * @return (IMAP4Response != null) the IMAP4 response.
	 */
	protected IMAP4Response executeIMAP4Command(byte transactionId, byte[] command)
	{
		return imap4handler.executeImapCommand(transactionId, command);
	}
	
	/**
	 * Executes operation's IMAP4 command without receiving the response.
	 * 
	 * @param transactionId (byte) the transaction ID.
	 * @param command (byte[] != null) the IMAP4 command as byte-stream.
	 * @param chunkSize (int) the size of a single chunk when sending the data, or -1 to send the whole data in single chunk.
	 */
	protected void executeIMAP4CommandWithoutResponse(byte transactionId, byte[] command, int chunkSize)
	{
		imap4handler.executeImapCommandWithoutResponse(transactionId, command, chunkSize);
	}
	
	/**
	 * Executes operation's IMAP4 get body text command and returns the response.
	 * 
	 * @param command
	 * @param fileName
	 * @param idUidPair
	 * @return
	 */
	public IMAP4Response executeGetBodyTextCommand(String command, String fileName, MessageDo messageDo)
	{
		return imap4handler.executeGetBodyTextCommand(command, fileName, messageDo);
	}
	
	/**
	 * Executes operation's IMAP4 get body text command and returns the response.
	 * 
	 * @param command
	 * @param fileName
	 * @param idUidPair
	 * @return
	 */
	public IMAP4Response executeGetBodyTextCommand(String command, String fileName)
	{
		return imap4handler.executeGetGreetingFileCommand(command, fileName);
	}
	
	/**
	 * Returns the type of the operation.
	 * 
	 * @return (OperationType) the operation type.
	 */
	public OperationTypes getType()
	{
		return type;
	}
}
