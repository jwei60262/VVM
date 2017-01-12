package com.att.mobile.android.vvm.control;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.control.operations.DeleteOperation;
import com.att.mobile.android.vvm.control.operations.FetchBodiesOperation;
import com.att.mobile.android.vvm.control.operations.FetchHeadersOperation;
import com.att.mobile.android.vvm.control.operations.GetMetaDataOperation;
import com.att.mobile.android.vvm.control.operations.GetQuotaOperation;
import com.att.mobile.android.vvm.control.operations.LoginOperation;
import com.att.mobile.android.vvm.control.operations.MarkAsReadOperation;
import com.att.mobile.android.vvm.control.operations.Operation;
import com.att.mobile.android.vvm.control.operations.Operation.OperationTypes;
import com.att.mobile.android.vvm.control.operations.Operation.Result;
import com.att.mobile.android.vvm.control.operations.SelectInboxOperation;
import com.att.mobile.android.vvm.control.operations.SendGreetingOperation;
import com.att.mobile.android.vvm.control.operations.SetMetaDataOperation;
import com.att.mobile.android.vvm.control.operations.TuiSkipOperation;
import com.att.mobile.android.vvm.control.operations.XChangeTUIPasswordOperation;
import com.att.mobile.android.vvm.control.receivers.NotificationService;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.ACTIONS;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.Constants.TRANSACTIONS;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.protocol.IMAP4Handler;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Istelman this class is used to run a sequence of network transactions
 *         unified under one Runnable operation object in a separate thread from
 *         the UI thread. the class uses the LOOPR-Handler android pattern.
 *         Operation are pushed to the thread's Looper messages queue and
 *         handled according to the Looper's logic. the queue is responsible for
 *         the login and logout from the server. between each login and logout
 *         all pending requests will be sent. for each type of transaction the
 *         uids of that transaction are saved in a set and when that queued
 *         transaction is popped and executed, it performs the transaction on
 *         all the uids collected in the set so far. Upon response from the
 *         server, all the succeeded actions' uids are removed from thats'
 *         transaction uid set so that they won't be re-sent. when ever someone
 *         wants to perform an operation on the server (i.e, delete, mark as
 *         read...) it notifies the queue about the type of operation it wants
 *         to perform and the related uids, the queue then adds the uids to
 *         that' transaction set and possibly removes them from another set -
 *         depending on the transaction priority (e.g, delete overcomes mark as
 *         read). In addition - the queue is responsible for a retry mechanism
 *         for failed or not sent transactions. for persistence the uid data
 *         sets are saved (if not empty) when application closes so the next
 *         time application runs, these will be sent to the server. Retry
 *         mechanism: Every IMAP transaction is retried for up to 5 times �
 *         after 5th retry the transaction is thrown away from the client
 *         network queue. For the FETCH BODIES operation(*) - after the 5th
 *         failure � the transaction is then pushed again to the client network
 *         queue and once executed by the client it can be retried again for 5
 *         times. Pushing the FETCH BODIES request to the queue can happen up to
 *         10 times, so totally the transaction will be retired up to 50 times.
 *         (*)FETCH BODIES operation - the client queries which messages has no
 *         audio or transcription and for each message a FETCH BODY request is
 *         sent, so that every retry is trying to send FETCH BODY requests one
 *         after the other for all messages that has no audio or transcription
 */
public final class OperationsQueue extends Thread implements IEventDispatcher {

	private static Boolean isnetworkFailureLockObjectNotified = false;
//	private static ConnectivityManager connectivityManager;
//	private NetworkRequest networkRequest;
	public static Boolean getIsnetworkFailureLockObjectNotified() {
		return isnetworkFailureLockObjectNotified;
	}
	
	public static void setIsnetworkFailureLockObjectNotified(
			Boolean isnetworkFailureLockObjectNotified) {
		OperationsQueue.isnetworkFailureLockObjectNotified = isnetworkFailureLockObjectNotified;
	}

	/** holds whether the operations queque thread is currently alive */
	private boolean isAlive = false;
	private static final String TAG = "OperationsQueue";

	/** holds the operations queue itseld */
	private LinkedBlockingQueue<Operation> operationsQueue = null;

	/** holds whether the application is currently logged in to the server */
	private boolean isLoggedIn = false;

	/** holds synchronization object for when login fails due to a network error */
	private Object networkFailureLockObject = new Object();


	/** holds the pending fetch headers operation */
	private FetchHeadersOperation pendingFetchHeadersOperation = null;

	/** holds the pending delete operation */
//	private DeleteOperation pendingDeleteOperation = null;

	/** holds the pending mark as read operation */
//	private MarkAsReadOperation pendingMarkAsReadOperation = null;

	/** holds the pending get greeting details operation */
	private GetMetaDataOperation pendingGetGreetingsOperation = null;

	/** holds the pending get existing greeting operation */
	private GetMetaDataOperation pendingGetExistingGreetingsOperation = null;

	/**
	 * holds synchronization objects for the fetch headers, delete, skip and
	 * mark as read operations
	 */
	private Object fetchHeadersOperationLockObject = new Object();
//	private Object deleteOperationLockObject = new Object();
//	private Object markAsReadOperationLockObject = new Object();
	private Object getMetaDataOperationLockObject = new Object();
	private Object tuiSkipOperationLockObject = new Object();
//	private final static Object syncObj = new Object();

	/**
	 * holds the number of seconds to wait before retrying the current failed
	 * operation
	 */
	private static int retryIntervalSeconds;

	/**
	 * holds the number of seconds to wait before retrying the another fetch
	 * bodies operation
	 */
	private static int retryFetchSeconds;

	/**
	 * holds the maximum number of retries for a socket connection establishment
	 */
	private static int maxRetriesConnect;
	/** holds the maximum number of retries for a single transaction */
	private static int maxRetriesTransaction;
	/** holds the maximum number of retries for fetch bodies request */
	private static int maxRetriesFetchRequest;

	/** holds the current network and protocol retires number */
	private int currentNetworkRetries;
	private int currentProtocolRetries;
	private int currentFetchBodiesRetries;

	/** holds the IMAP4Handler and the model manager instances */
	private static IMAP4Handler imap4handler;
	private static ModelManager modelManager;

	/** holds the events dispatcher */
	private Dispatcher dispatcher;

	/**
	 * the current operation handled by the queue
	 */
	private Operation currentOperation;

	private static Context context;

	private int numberOfMessages = 0;


	/**
	 * handler and handler thread to handle operation queue tasks outside the
	 * operation queue thread for the retry of fetch bodies - we need to enqueue
	 * a fetch bodies operation outside of the operations queue thread
	 */
	private HandlerThread helperHandlerThread;
	private HelperHandler helperHandler;

	/**
	 * mark that this operation is in process and don't let another one enter
	 * the queue
	 */
	private boolean isXChangeTUIPasswordOperationPending = false;
	private static final int EVENT_RETRY_FETCH_BODIES = 1;
	private static final int EVENT_XCHANGE_PASSWORD = 2;

	private static class OperationsQueueHolder {
		private static final OperationsQueue INSTANCE = new OperationsQueue();
	}

	public static void createInstance(Context c) {
		if (context == null) {
			context = c;
		}
		// get user details from application preferences/settings
		retryIntervalSeconds = Integer.valueOf(context.getString(
				R.string.retryIntervalSeconds, 30));
		retryFetchSeconds = Integer.valueOf(context.getString(
				R.string.retryFetchSeconds, 60));
		maxRetriesConnect = Integer.valueOf(context.getString(
				R.string.maxRetriesConnect, 2));
		maxRetriesTransaction = Integer.valueOf(context.getString(
				R.string.maxRetriesTransaction, 5));
		maxRetriesFetchRequest = Integer.valueOf(context.getString(
				R.string.maxRetriesFetchRequest, 10));

		imap4handler = IMAP4Handler.getInstance();
		imap4handler.init(context);

		modelManager = ModelManager.getInstance();

	}

	public static OperationsQueue getInstance() {
		if (context == null) {
			Log.e(TAG,
					"OperationsQueue.getInstance() must call create instance before calling getInstance()");
		}
		return OperationsQueueHolder.INSTANCE;
	}

	/**
	 * OperationsQueue default constructor.
	 */
	private OperationsQueue() {
		super(OperationsQueue.class.getSimpleName());

		// creates the operations queue
		operationsQueue = new LinkedBlockingQueue<Operation>();

		// loads any saved message UIDs for delete or mark as read
//		loadPendingMessageUIDs();

		// creates the events dispatcher
		dispatcher = ModelManager.getInstance().getDispatcher();
//		connectivityManager = (ConnectivityManager) context.getSystemService(
//				Context.CONNECTIVITY_SERVICE);
//		NetworkRequest.Builder builder = new NetworkRequest.Builder();
//
//		builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
//		builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
//
//		networkRequest = builder.build();

		// starts the thread of the operations queue
		start();

		helperHandlerThread = new HandlerThread(
				"OperationsQueueHelperHandlerThread");
		helperHandlerThread.start();
		// wait until handler thread is on
//		while (!helperHandlerThread.isAlive()) {
//			Logger.d(TAG, "wait until handler thread is on");
//		};
		helperHandler = new HelperHandler(helperHandlerThread.getLooper());
	}

	/**
	 * Runs the thread.
	 */
	@Override
	public void run() {
		// updates that the operations queue thread is alive
		isAlive = true;

		// holds the current operation
		currentOperation = null;

		// for the handler to work ropertly
		Looper.prepare();

		// as long as the thread is alive
		while (isAlive) {
			// in case no operation was failed and needs to be re-execute
			if (currentOperation == null) {
				try {
					// gets the current operation in the queue (waits if there
					// are no operations at the moment)
					currentOperation = operationsQueue.poll((60 * 60),
							TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					// continue trying to poll the next operation to execute
					continue;
				}
			}

			Logger.d(TAG,
					"OperationsQueue.run() currentOperation = "
							+ currentOperation);
			// verifies login to the server
			int loginResult = verifyLogin();
			if (loginResult != Result.ALREADY_LOGGED_IN) {
				// in case login didn't succeed (means that no connection exists
				// to the server)
				if (loginResult != Result.SUCCEED) {
					// performs another login retry
					// (in case the operations queue has not been reset due to
					// maximum network or protocol retries or wrong password,
					// otherwise just wait for next operation enqueue)
					continue;
				}

				dispatcher.notifyListeners(EVENTS.LOGIN_SUCCEEDED, null);

				// notifies any registered listeners that retrieving messages
				// started
				if (pendingFetchHeadersOperation != null) {
					dispatcher.notifyListeners(
							EVENTS.RETRIEVE_MESSAGES_STARTED, null);
				}
				// gets the number of messages exist at the server
				int selectInboxResult = selectInbox();

				// in case login didn't succeed
				if (selectInboxResult != Result.SUCCEED
						&& selectInboxResult != Result.SUCCEED_NO_MESSAGES_EXIST) {
					// performs another select inbox retry
					// (in case the operations queue has not been reset due to
					// maximum network / protocol retries,
					// otherwise just wait for next operation enqueue)
					continue;
				}

				// in case the operation is not a get/set meta data and no
				// messages exists at the server
				if (currentOperation != null
						&& !(currentOperation instanceof GetMetaDataOperation)
						&& !(currentOperation instanceof SetMetaDataOperation)
						&& !(currentOperation instanceof SendGreetingOperation)
						&& selectInboxResult == Result.SUCCEED_NO_MESSAGES_EXIST) {
					// notifies any registered listener that fetching headers
					// operation finished
					dispatcher
							.notifyListeners(
									EVENTS.SELECT_INBOX_FINISHED_WITH_NO_MESSAGES,
									null);

					// resets the queue
					resetQueue();

					// waits for next operation enqueue
					continue;
				}
			}

			// in case the current operation is a fetch headers operation,
			// the operation is no longer pending for execution
			if (currentOperation != null
					&& currentOperation.getType() == OperationTypes.TYPE_FETCH_HEADERS) {
				// notifies any registered listeners that retrieving messages
				// started
				dispatcher.notifyListeners(EVENTS.RETRIEVE_MESSAGES_STARTED,
						null);
//			}
//			// in case the current operation is a delete or mark as read
//			// operation,
//			// the operation is no longer pending for execution
//			else if (currentOperation != null
//					&& currentOperation.getType() == OperationTypes.DELETE) {
//				synchronized (deleteOperationLockObject) {
//					// and sets that no delete operation is currently pending
//					pendingDeleteOperation = null;
//				}
//			} else if (currentOperation != null
//					&& currentOperation.getType() == OperationTypes.MARK_AS_READ) {
//				synchronized (markAsReadOperationLockObject) {
//					// sets message to mark as read UIDs in the mark as read
//					// operation,
//					// and sets that no mark as read operation is currently
//					// pending
//					((MarkAsReadOperation) currentOperation)
//							.setMessageToMarkAsReadUIDs(modelManager
//									.getMessageUIDsToMarkAsRead());
//					pendingMarkAsReadOperation = null;
//				}
			} else if (currentOperation != null
					&& currentOperation.getType() == OperationTypes.TYPE_GET_META_DATA_GREETINGS_DETAILS) {
				synchronized (getMetaDataOperationLockObject) {
					pendingGetGreetingsOperation = null;
				}
			} else if (currentOperation != null
					&& currentOperation.getType() == OperationTypes.TYPE_GET_META_DATA_EXISTING_GREETINGS) {
				synchronized (getMetaDataOperationLockObject) {
					pendingGetExistingGreetingsOperation = null;
				}
			}

			// performs the current operation
			int currentOperationResult = Operation.Result.FAILED;
			if (currentOperation != null) {
				currentOperationResult = currentOperation.execute();
			}

			// in case operation failed due to a network error
			if (currentOperationResult == Operation.Result.NETWORK_ERROR) {
				// waits for a network to become available or a retry interval
				if (!retryOnNetworkError()) {
					// in case maximum number of network retries has been
					// reached
					resetQueueOnFailure(Operation.Result.NETWORK_ERROR);
				}

				// performs another operation retry
				// (in case the operations queue has not been reset due to
				// maximum network / protocl retries,
				// otherwise just wait for next operation enqueue)
				continue;
			}

			// in case operation failed NOT due to a network error
			else if (currentOperationResult == Operation.Result.FAILED) {
				logoutAndTerminateConnection();

				// in case maximum number of protocol retries has been reached
				if (++currentProtocolRetries >= maxRetriesTransaction) {
					Log.e(TAG,
							"OperationsQueue.run() - maximum number of protocol retries has been reached!");
					// once max retries has reached we simply drop this
					// operation and continue to handle the next operation in
					// the queue
					if (currentOperation != null
							&& currentOperation.getType() == OperationTypes.TYPE_FETCH_HEADERS) {

						synchronized (fetchHeadersOperationLockObject) {
							pendingFetchHeadersOperation = null;
						}
					}
					currentOperation = null;
				}

				// performs another operation retry
				// (in case the operations queue has not been reset due to
				// maximum network / protocl retries,
				// otherwise just wait for next operation enqueue)
				continue;
			}

			// in case we failed because there's no storage place
			else if (currentOperationResult == Result.FAILED_NOT_ENOUGH_SPACE) {
				logoutAndTerminateConnection();

				currentOperation = null;

				continue;
			}

			// if fetch headers finished successfully
			else if (currentOperation != null
					&& currentOperation.getType() == OperationTypes.TYPE_FETCH_HEADERS) {

				synchronized (fetchHeadersOperationLockObject) {
					pendingFetchHeadersOperation = null;
				}
				// notifies any registered listener that fetching headers
				// operation finished
				dispatcher.notifyListeners(EVENTS.RETRIEVE_HEADERS_FINISHED,
						null);
				int quota = ModelManager.getInstance().getSharedPreferenceValue(Constants.KEYS.MAX_MESSAGES, Integer.class, Constants.DEFAULT_MAX_MESSAGES);	
				if (getNumberOfMessages() >= quota - Constants.DEFAULT_MAX_MESSAGES_WARNING) {
					if (getNumberOfMessages() >= quota) {
						notifyListeners(EVENTS.MESSAGES_FULL, null);
					} else {
						notifyListeners(EVENTS.MESSAGES_ALLMOST_FULL, null);
					}
				}
			}
			// special handling for fetch bodies request
			else if (currentOperation != null
					&& currentOperation.getType() == OperationTypes.TYPE_FETCH_BODIES) {
				// in case we failed because there's no storage place
				if (currentOperationResult == Result.CONNECTION_CLOSED) {
					// retry a few times on network errors
					if (retryOnNetworkError()) {
						continue;
					}
					// in case network retry of the transaction has faild we
					// retry the request all over again
					else {
						retryFetchBodies();
					}
				}
				// in case of success - reset the retries number
				else if (currentOperationResult == Result.SUCCEED) {
					currentFetchBodiesRetries = 0;
				}
			}

			// operation executed successfully, cleans its reference and resets
			// the retries numbers
			currentOperation = null;
			currentNetworkRetries = 0;
			currentProtocolRetries = 0;

			// in case there are more operations currently in the queue,
			// continue to the next one without logout
			if (!operationsQueue.isEmpty()) {
				continue;
			} else {
				synchronized (fetchHeadersOperationLockObject) {
					pendingFetchHeadersOperation = null;
				}
			}

			// performs logout from the server
			logoutAndTerminateConnection();
		}
	}

	/**
	 * verify connection exists and open if not exists
	 * 
	 * @return
	 */
	private int connect() {
		// in case a mobile network connection doesn't exists, waits for it
		String hostAddress = ModelManager.getInstance()
				.getSharedPreferenceValue(Constants.KEYS.PREFERENCE_HOST,
						String.class, null);

		if (hostAddress == null || imap4handler == null) {
			Log.d(TAG, "connect() No host is saved, connect has failed");
			resetQueueOnFailure(Operation.Result.FAILED);
			return Operation.Result.FAILED;
		}

			imap4handler.connect();

		// in case establishing a connection failed
		if (!imap4handler.isConnected()) {
			// waits for a network to become available or for a retry interval
			if (!retryOnConnectError()) {
				// maximum number of network retries has been reached
				resetQueueOnFailure(Operation.Result.NETWORK_ERROR);
				notifyListeners(EVENTS.LOGIN_FAILED, null);
			}
			// retry network error s o login will retry by the operation queue
			// and so the connection will retry as well
			return Operation.Result.NETWORK_ERROR;
		} 
		// we should reset the network retries now - for next network errors to
		// start fresh
			currentNetworkRetries = 0;

		return Operation.Result.SUCCEED;
	}

	/**
	 * Verifies that the user is logged in to the server.
	 * 
	 * @return (int) the result of the login operation against the server,
	 */
	private int verifyLogin() {
		// in case the user is already logged in to the server
		if (isLoggedIn) {
			return Result.ALREADY_LOGGED_IN;
		}

		// verify connection exists and open if not exists
		if (connect() == Operation.Result.NETWORK_ERROR) {
			return Operation.Result.NETWORK_ERROR;
		}

		// creates the special login operation, and performs it
		LoginOperation loginOperation = new LoginOperation(context);
		int loginOperationResult = loginOperation.execute();

		// in case login was failed not due to network error
		if (loginOperationResult == Result.FAILED) {
			Logger.d(TAG,
					"OperationsQueue.verifyLogin() - login failed.");

			logoutAndTerminateConnection();

			// in case maximum number of protocol retries has been reached
			if (++currentProtocolRetries >= maxRetriesTransaction) {
				Log.e(TAG,
						"OperationsQueue.verifyLogin() - maximum number of protocol retries has been reached!");
				resetQueueOnFailure(Result.FAILED);
				notifyListeners(EVENTS.LOGIN_FAILED, null);
			} else {
				// wait the retry interval before trying to re login
				try {
					Thread.sleep(retryIntervalSeconds * 1000);
				} catch (Exception e) {
					Log.e(TAG, e.getMessage(), e);
				}
			}

			return Result.FAILED;
		}
		// in case login failed due to a network error
		else if (loginOperationResult == Result.NETWORK_ERROR) {
			// waits for a network to become available or for a retry interval
			if (!retryOnNetworkError()) {
				// maximum number of network retries has been reached
				resetQueueOnFailure(Result.NETWORK_ERROR);
				notifyListeners(EVENTS.LOGIN_FAILED, null);
			}
			return Result.NETWORK_ERROR;
		} else if (loginOperationResult == Result.FAILED_WRONG_PASSWORD) {
			Logger.d(TAG,
					"OperationsQueue.verifyLogin() - login failed due to wrong password");

			logoutAndTerminateConnection();

			resetQueueOnFailure(Result.FAILED);

			// notify listeners including notification service for the case we
			// are on the background
			notifyListeners(EVENTS.LOGIN_FAILED_DUE_TO_WRONG_PASSWORD, null);

			Intent intent = new Intent(ACTIONS.ACTION_PASSWORD_MISSMATCH);
			intent.setClass(context, NotificationService.class);
			context.startService(intent);

			return Result.FAILED_WRONG_PASSWORD;
		}

		// updates that the application is logged in
		isLoggedIn = true;

		// login was successful
		return Result.SUCCEED;
	}

	/**
	 * Gets the number of messages currently at the server (select inbox
	 * operation).
	 * 
	 * @return (int) the result of the select inbox operation against the
	 *         server.
	 */
	private int selectInbox() {
		GetQuotaOperation getQuotaOperation = new GetQuotaOperation(
				context);
		int getQuotaResult = getQuotaOperation.execute();
		Logger.d(TAG,	"OperationsQueue.selectInbox() - getQuotaResult "+ getQuotaResult);
		if(getQuotaResult == Operation.Result.SUCCEED){
			ModelManager.getInstance().setSharedPreference(	Constants.KEYS.MAX_MESSAGES, getQuotaOperation.getServerQuota());	
			Logger.d(TAG,	"OperationsQueue.selectInbox() - getQuotaResult succeeded");
		} else if (getQuotaResult == Operation.Result.NETWORK_ERROR) {
			// waits for a network to become available or a retry interval
			if (!retryOnNetworkError()) {
				// in case maximum number of network retries has been reached
				resetQueueOnFailure(Operation.Result.NETWORK_ERROR);
			}

			return Result.NETWORK_ERROR;
		}

		// in case getting quota at the server failed NOT due
		// to a network error
		if (getQuotaResult == Operation.Result.FAILED) {
			logoutAndTerminateConnection();

			// in case maximum number of protocol retries has been reached
			if (++currentProtocolRetries >= maxRetriesTransaction) {
				Log.e(TAG,	"OperationsQueue.selectInbox() - maximum number of protocol retries has been reached!");
				resetQueueOnFailure(Result.FAILED);
			}

			return Result.FAILED;
		}

		// creates the special select inbox operation, and performs it
		SelectInboxOperation selectInboxOperation = new SelectInboxOperation(
				context);
		int selectInboxResult = selectInboxOperation.execute();
		Logger.d(TAG,
				"OperationsQueue.selectInbox() - selectInboxResult "
						+ selectInboxResult);

		// in case getting the number of messages at the server failed due to a
		// network error
		if (selectInboxResult == Operation.Result.NETWORK_ERROR) {
			// waits for a network to become available or a retry interval
			if (!retryOnNetworkError()) {
				// in case maximum number of network retries has been reached
				resetQueueOnFailure(Operation.Result.NETWORK_ERROR);
			}

			return Result.NETWORK_ERROR;
		}

		// in case getting the number of messages at the server failed NOT due
		// to a network error
		if (selectInboxResult == Operation.Result.FAILED) {
			logoutAndTerminateConnection();

			// in case maximum number of protocol retries has been reached
			if (++currentProtocolRetries >= maxRetriesTransaction) {
				Log.e(TAG,
						"OperationsQueue.getServerNumberOfMessages() - maximum number of protocol retries has been reached!");
				resetQueueOnFailure(Result.FAILED);
			}

			return Result.FAILED;
		}

		// select inbox operation succeed
		if (pendingFetchHeadersOperation != null) {
			setNumberOfMessages(selectInboxOperation.getServerNumberOfMessage());
		}

		if (selectInboxResult == SelectInboxOperation.Result.SUCCEED_NO_MESSAGES_EXIST) {
			// server has no messages, no reason for us to keep them, we don't
			// delete the welcome message here
			//modelManager.deleteUnsavedMessages(false);
			return Result.SUCCEED_NO_MESSAGES_EXIST;
		}
		return Result.SUCCEED;
	}

	/**
	 * Waits (pauses the operations queue thread) as a result of a socket
	 * connection error against the server. when interval waiting is over the
	 * connection will retry to connect or if all retries were made the queue is
	 * reset and user is notified.
	 */
	private boolean retryOnConnectError() {
		if (++currentNetworkRetries >= maxRetriesConnect) {
			Log.e(TAG,
					"OperationsQueue.retryOnConnectError() - maximum number of socket connection retries has been reached!");
			return false;
		}
		Logger.d(TAG,
				"OperationsQueue.retryOnConnectError() - socket connection retry "
						+ currentNetworkRetries);
		try {
			// waits predefined interval time before trying to login
			// once again
			Thread.sleep(retryIntervalSeconds * 1000);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
		// maximum number of retires hasn't been reached
		return true;
	}

	/**
	 * Waits (pauses the operations queue thread) as a result of a network error
	 * against the server. When network error has been resolved (as a result of
	 * network exists notifiaction or as a result of interval waiting, the
	 * application is disconnected from the server.
	 */
	private boolean retryOnNetworkError() {
		try {
			if (++currentNetworkRetries >= maxRetriesTransaction) {
				Log.e(TAG,
						"OperationsQueue.retryOnNetworkError() - maximum number of network retries has been reached!");
				return false;
			}
//			// in case network is NOT connected but may be connected if we force
//			// hipri
//			int networkState = NetworkStateChangesReceiver
//					.getMobileConnectionState();
//			if (networkState == NetworkStateChangesReceiver.MOBILE_CONNECTION_STATE_NOT_CONNECTED) {
//				// waits for the mobile network connection to become available
//				waitForMobileNetworkConnection();
//			}
//			// in case network is available or may be available after the retry
//			// interval
//			else {
				Logger.d(TAG,
						"OperationsQueue.retryOnNetworkError() - network retry "
								+ currentNetworkRetries);
				try {
					// waits predefined interval time before trying to login
					// once again
					Thread.sleep(retryIntervalSeconds * 1000);
				} catch (Exception e) {
					Log.e(TAG, e.getMessage(), e);
				}
//			}
			// maximum number of retires hasn't been reached
			return true;
		} finally {
			// performs logout from the server and terminates the connection, if
			// needed
			logoutAndTerminateConnection();
		}
	}

	/**
	 * handle retry of fetch headers after operation failed 3 times during
	 * execution inside the operation queue
	 */
	private void retryFetchBodies() {

		if (currentFetchBodiesRetries <= maxRetriesFetchRequest) {

			helperHandler.sendEmptyMessageDelayed(EVENT_RETRY_FETCH_BODIES,
					retryFetchSeconds * 1000);
			++currentFetchBodiesRetries;

			Logger.d(TAG,
					"OperationsQueue.retryFetchBodies() - retry fetch bodies num "
							+ currentFetchBodiesRetries + " in "
							+ (retryFetchSeconds * 1000) + " millis");

		} else {
			Logger.d(
					TAG,
					"OperationsQueue.retryFetchBodies() - max retries of fetch bodies request has reached");
			// reset for future requests
			currentFetchBodiesRetries = 0;

			OperationsQueue.getInstance().notifyListeners(
					EVENTS.CONNECTION_LOST, null);
		}
	}

//	/**
//	 * Waits for a mobile network connection to become available (block the
//	 * thread till it is available).
//	 * @return TODO
//	 */
//	private boolean waitForMobileNetworkConnection() {
//		Logger.d(TAG, "OperationsQueue.waitForMobileNetworkConnection() - waiting for mobile network connection");
//
//		// registers the network state changes broadcast receiver to listen to
//		// network state changes
////		NetworkStateChangesReceiver.registerReceiver(context);
////		NetworkStateChangesReceiver.forceMobileConnection();
//		synchronized (networkFailureLockObject) {
//			try {
//				// waits for network to be available
//		                isnetworkFailureLockObjectNotified = false;
//				networkFailureLockObject.wait(70000);
//				Logger.d(TAG, "OperationsQueue.waitForMobileNetworkConnection() - notified, wait is released!");
//			} catch (Exception e) {
//				Log.e(TAG, "OperationsQueue.waitForMobileNetworkConnection() - operations queue cannot wait for network connection to be available - "
//								+ e);
//			}
//		}
//		return isnetworkFailureLockObjectNotified;
//	}

	/**
	 * Resets the queue as a result of failure (maximum number of retries on
	 * network / protocol failure).
	 * 
	 * @param operationFailureType
	 *            (int) the operation failure type (Result.FAILD /
	 *            Result.NETWORK_ERROR)
	 */
	private void resetQueueOnFailure(int operationFailureType) {
		if (operationFailureType == Result.NETWORK_ERROR) {
			// notifies any registered listeners that a network failure has
			// occurred
			// (maximum number of retires has been reached)
			dispatcher.notifyListenersForNetworkFailure();
		}
		// resets the queue
		resetQueue();
	}

	/**
	 * Resets the operations queue by cleaning it and disconnect from the
	 * server.
	 */
	public void resetQueue() {
		// performs logout and terminates connection against the server, if
		// needed
		logoutAndTerminateConnection();

		// cleares any pending operation (pending UID for delete and mark as
		// read
		// are not deleted, and will be sent to server on next delete or mark as
		// read
		// opreation enque)
		operationsQueue.clear();

		// resets the number of network / protocol retires
		currentNetworkRetries = 0;
		currentProtocolRetries = 0;
		currentFetchBodiesRetries = 0;

		// reset the pending fetch so we can insert a new fetch operation to the
		// queue
		pendingFetchHeadersOperation = null;

		// sets the current operation as null, to stop retries
		this.currentOperation = null;

	}

	/**
	 * Enqueues an operation to the operations queue.
	 * 
	 * @param operation
	 *            (Operation != null) the operation to enqueue.
	 */
	private void enqueueOperation(Operation operation) {
		// will be handled only when ATTM is not installed and provisioned
		if (modelManager.getAttmStatus() != Constants.ATTM_STATUS.PROVISIONED) {
			operationsQueue.add(operation);
		}
	}

	/**
	 * Enqueues fetch headers and fetch bodies operations.
	 */
	public void enqueueFetchHeadersAndBodiesOperation() {
		// in case a fetch headers operation is already pending for executing,
		// do nothing
		synchronized (fetchHeadersOperationLockObject) {
			if (pendingFetchHeadersOperation != null) {
				Logger.d(
						TAG,
						"OperationsQueue.enqueueFetchHeadersAndBodiesOperation() - fetch headers operation is already pending for execution");
				return;
			}
		}

		Logger.d(
				TAG,
				"OperationsQueue.enqueueFetchHeadersAndBodiesOperation() - fetch headers and bodies operations are being queued.");
		pendingFetchHeadersOperation = new FetchHeadersOperation(context,
				dispatcher);

		// enqueue fetch headers
		enqueueOperation(pendingFetchHeadersOperation);
		if(modelManager.isNotifyOnNewMessagesEnabled()){
			// enqueue TUI skip
			synchronized (tuiSkipOperationLockObject) {
				enqueueOperation(new TuiSkipOperation(context));
			}
		}
		// enqueue fetch bodies
		enqueueOperation(new FetchBodiesOperation(context, dispatcher));

	}

//	/**
//	 * Enqueues a delete operation to the operations queue.
//	 *
//	 * @param messageID
//	 *            (long) the ID of the message to delete.
//	 * @param messageUID
//	 *            (long) the UID of the message to delete.
//	 */
//	public void enqueueDeleteOperation(long messageID, long messageUID) {
//		synchronized (deleteOperationLockObject) {
//			// avoid delete the welcome message from the server since it not
//			// exists on the server
//			if (messageUID != Constants.WELCOME_MESSAGE_ID) {
//				// adds the message UID to the message to delete UIDs collection
//				ModelManager.getInstance().addMessageUIDToDelete(messageUID);
//
//				// in case a delete operation is not pending for execution
//				if (pendingDeleteOperation == null) {
//					pendingDeleteOperation = new DeleteOperation(context);
//					// enqueues the delete operation to the operations queue
//					enqueueOperation(pendingDeleteOperation);
//				}
//			}
//		}
//
//		// deletes the message from the application
//		modelManager.deleteMessagePermanently(messageID);
//
//		// go update notification since new messages num may have been changed
//		((VVMApplication) (context.getApplicationContext()))
//				.updateNotification();
//	}
	public void enqueueDeleteOperation(Context context, Long[] mesUIDs){
		if(mesUIDs != null && mesUIDs.length > 0){
			enqueueOperation(new DeleteOperation(context, mesUIDs));
		}
	}
//	/**
//	 * Enqueues a delete operation to the operations queue of the specified
//	 * messages.
//	 *
//	 * @param messageIDs
//	 */
//	public void enqueueDeleteOperation(MessageDo[] messageIDs) {
//		if (messageIDs.length == 0)
//			return;
//
//		synchronized (deleteOperationLockObject) {
//			// adds the message UID to the message to delete UIDs collection
//			for (int i = 0; i < messageIDs.length; i++) {
//				modelManager.addMessageUIDToDelete(messageIDs[i].getUid());
//			}
//
//			// in case a delete operation is not pending for execution
//			if (pendingDeleteOperation == null
//					&& modelManager.getMessageUIDsToDelete() != null
//					&& !modelManager.getMessageUIDsToDelete().isEmpty()) {
//				pendingDeleteOperation = new DeleteOperation(context);
//				// enqueues the delete operation to the operations queue
//				enqueueOperation(pendingDeleteOperation);
//			}
//		}
//	}
public void enqueueMarkAsReadOperation(Context context, long mesUID){
	if(mesUID != -1 ){
		enqueueOperation(new MarkAsReadOperation(context, mesUID));
	}
}

//	/**
//	 * Enqueues a mark as operation to the operations queue.
//	 *
//	 * @param messageIDs
//	 *            (long[] != null) the IDs of the messages to mark as read.
//	 * @param messageUIDs
//	 *            (long[] != null) the UIDs of the messages to mark as read.
//	 */
//	public void enqueueMarkAsReadOperation(long[] messageIDs, long[] messageUIDs) {
//		synchronized (markAsReadOperationLockObject) {
//			// adds the message UID to the message to mark as read UIDs
//			// collection
//			modelManager.addMessageUIDToMarkAsRead(messageUIDs);
//
//			// in case a mark as read operation is not pending for execution
//			if (pendingMarkAsReadOperation == null
//					&& modelManager.getMessageUIDsToMarkAsRead() != null
//					&& !modelManager.getMessageUIDsToMarkAsRead().isEmpty()) {
//				pendingMarkAsReadOperation = new MarkAsReadOperation(context);
//				// enqueues the mark as read operation to the operations queue
//				enqueueOperation(pendingMarkAsReadOperation);
//			}
//		}
//
//		// marks the messages as read in the application
//		modelManager.setMessagesAsRead(messageIDs);
//
//		// go update notification since new messages num may have been changed
//		((VVMApplication) (context.getApplicationContext()))
//				.updateNotification();
//	}
	/**
	 * Enqueues a get meta data as a get greetings details operation to the
	 * operations queue.
	 */
	public void enqueueGetGreetingsDetailsOperation() {

		synchronized (getMetaDataOperationLockObject) {
			// enqueues a get meta data as a get greetings details operation to
			// the
			// operations queue
			Logger.d(TAG,
					"OperationsQueue::enqueueGetGreetingsDetailsOperation");

			if (pendingGetGreetingsOperation == null
					|| pendingGetGreetingsOperation.getType() != OperationTypes.TYPE_GET_META_DATA_GREETINGS_DETAILS) {
				pendingGetGreetingsOperation = new GetMetaDataOperation(
						context,
						OperationTypes.TYPE_GET_META_DATA_GREETINGS_DETAILS,
						dispatcher);
				Logger.d(TAG,
						"OperationsQueue::enqueueGetGreetingsDetailsOperation::enqueue");
				enqueueOperation(pendingGetGreetingsOperation);
			}
		}
	}

	/**
	 * Enqueues a get meta data as a get password length details operation to
	 * the operations queue.
	 */
	public void enqueueGetPasswordLengthOperation() {
		// enqueues a get meta data as a get password details operation to the
		// operations queue
		enqueueOperation(new GetMetaDataOperation(context,
				OperationTypes.TYPE_GET_META_DATA_PASSWORD, dispatcher));
	}

	/**
	 * Enqueues a get meta data as a get existing greetings operation to the
	 * operations queue.
	 */
	public void enqueueGetExistingGreetingsOperation() {

		synchronized (getMetaDataOperationLockObject) {
			// enqueues a get meta data as a get existing greetings operation to
			// the
			// operations queue

			if (pendingGetExistingGreetingsOperation == null
					|| pendingGetExistingGreetingsOperation.getType() != OperationTypes.TYPE_GET_META_DATA_EXISTING_GREETINGS) {
				pendingGetExistingGreetingsOperation = new GetMetaDataOperation(
						context,
						OperationTypes.TYPE_GET_META_DATA_EXISTING_GREETINGS,
						dispatcher);
				enqueueOperation(pendingGetExistingGreetingsOperation);
			}
		}
	}

	/**
	 * Enqueues a set meta data operation to the operations queue.
	 * 
	 * @param metaDataToSet
	 *            (String != null) the meta data to set.
	 * @param metaDataValue
	 *            (String != null) meta data's value.
	 */
	public void enqueueSetMetaDataOperation(String metaDataToSet,
			String metaDataValue) {
		// enqueues a set meta data operation to the operations queue
		enqueueOperation(new SetMetaDataOperation(context,
				OperationTypes.TYPE_SET_META_DATA_GENERAL, metaDataToSet,
				metaDataValue, dispatcher));
	}

	/**
	 * Enqueues a set meta data operation to the operations queue.
	 * 
	 * @param metaDataToSet
	 *            (String != null) the meta data to set.
	 * @param metaDataValue
	 *            (String != null) meta data's value.
	 */
	public void enqueueSetMetaDataGreetingTypeOperation(String metaDataToSet,
			String metaDataValue) {
		// enqueues a set meta data operation to the operations queue
		enqueueOperation(new SetMetaDataOperation(context,
				OperationTypes.TYPE_SET_META_DATA_GREETING_TYPE, metaDataToSet,
				metaDataValue, dispatcher));
	}

	/**
	 * Enqueues a set password meta data operation to the operations queue.
	 * 
	 * @param password
	 *            (String != null) the password to set.
	 */
	public void enqueueSetPasswordMetaDataOperation(String password) {
		// enqueues a set meta data operation to the operations queue
		enqueueOperation(new SetMetaDataOperation(context,
				OperationTypes.TYPE_SET_META_DATA_PASSWORD,
				Constants.METADATA_VARIABLES.TUIPassword, password, dispatcher));
	}

	/**
	 * run the XCHANGE_TUI_PASSWORD operation on this thread but not on the
	 * operations queue this operation does not require login, in order to leave
	 * the operations queue simple the operation is run outside of the queue.
	 * other operations requires login and the operations queue is design to
	 * make sure login is executed before them. this operation reset the queue
	 * in order to run without any disturbs. any other pending operation is not
	 * important in this stage since password must be changed, new operations
	 * will not enter the queue since the application status does not allow the
	 * user to do anything other than change the admin password. new SMS will
	 * come with S=R status and will only trigger the change password screen
	 * only.
	 * 
	 * @param newPassword
	 *            (String != null) the password to set.
	 */
	public void enqueueXChangeTUIPasswordOperation(String newPassword) {

		if (!isXChangeTUIPasswordOperationPending) {
			// remove all pending requests from the queue
			resetQueue();
			helperHandler.sendMessage(helperHandler.obtainMessage(
					EVENT_XCHANGE_PASSWORD, newPassword));
			isXChangeTUIPasswordOperationPending = true;
		}
	}

	/**
	 * Enqueues a send greeting operation to the operations queue.
	 * 
	 * @param greetingData
	 *            (byte[] != null) the greeting data to send.
	 */
	public void enqueueSendGreetingOperation(String greetingType,
			byte[] greetingData) {
		// enqueues a send greeting operation to the operations queue
		enqueueOperation(new SendGreetingOperation(context, greetingType,
				greetingData, dispatcher));
	}

//	/**
//	 * @author istelman if there are pending requests and application closes
//	 *         save the uid sets so that next time application starts we will
//	 *         retry to send these requests
//	 */
//	public void saveDeleteAndMarkAsReadPendingUIDs() {
//		// in case a delete operation is pending for execution
//		if (pendingDeleteOperation != null) {
//			VvmFileUtils.saveSerializable(context,
//					modelManager.getMessageUIDsToDelete(),
//					modelManager.pendingDeletesFilename);
//		} else {
//			VvmFileUtils.deleteInternalFile(context,
//					modelManager.pendingDeletesFilename);
//		}
//
//		// in case a mark as read operation is pending for execution
//		if (pendingMarkAsReadOperation != null) {
//			VvmFileUtils.saveSerializable(context,
//					modelManager.getMessageUIDsToMarkAsRead(),
//					modelManager.pendingReadsFilename);
//		} else {
//			VvmFileUtils.deleteInternalFile(context,
//					modelManager.pendingReadsFilename);
//		}
//	}

//	/**
//	 * Loads any saved pending message UIDs for delete or mark as read, and
//	 * enqueues proper operations to the queue if needed
//	 */
//	@SuppressWarnings("unchecked")
//	private void loadPendingMessageUIDs() {
//		// in case messages are pending to delete
//		Set<Long> messageToDeleteUIDs = (Set<Long>) VvmFileUtils.loadSerializable(
//				context, modelManager.pendingDeletesFilename);
//		if (messageToDeleteUIDs != null) {
//			modelManager.setMessageUIDsToDelete(messageToDeleteUIDs);
//
//			// enqueues the delete operation to the operations queue
//			enqueueOperation(new DeleteOperation(context));
//		}
//
//		// in case messages are pending to mark as read
//		Set<Long> messageToMarkAsReadUIDs = (Set<Long>) VvmFileUtils
//				.loadSerializable(context, modelManager.pendingReadsFilename);
//		if (messageToMarkAsReadUIDs != null) {
//			modelManager.setMessageUIDsToMarkAsRead(messageToMarkAsReadUIDs);
//
//			// enqueues the mark as read operation to the operations queue
//			enqueueOperation(new MarkAsReadOperation(context));
//		}
//
//		// deletes the files which stores the pending message UIDs for delete
//		// and mark as read from application's storage
//		VvmFileUtils.deleteInternalFiles(context, new String[] {
//				modelManager.pendingDeletesFilename,
//				modelManager.pendingReadsFilename });
//	}

	/**
	 * Performs logout from the server and terminates the connection. //TODO -
	 * Royi - create an operation for logout ?
	 */
	private void logoutAndTerminateConnection() {
		Logger.d(TAG,
				"OperationsQueue.logoutAndTerminateConnection()");

		// in case the connection against the server is already terminated, do
		// noting
		if (!imap4handler.isConnected()) {
			Logger.d(TAG,
					"OperationsQueue.logoutAndTerminateConnection() - connection not connected");
		} else {

			// creates the logout command
			String command = new StringBuilder(Constants.IMAP4_TAG_STR).append(
					"LOGOUT\r\n").toString();

			// executes the logut command
			imap4handler.executeImapCommand(TRANSACTIONS.TRANSACTION_LOGOUT,
					command.getBytes());

			// termiates the connection against the server
			imap4handler.close();

			Logger.d(
					TAG,
					"OperationsQueue.logoutAndTerminateConnection() - End of session, connection closed by client");
		}


		// updates that the application is not logged in anymore
		isLoggedIn = false;
	}

	/**
	 * Used to notify the operations queue that the application is disconnected
	 * from the server.
	 */
	public void notifyDisconnect() {
		// updates that the application is no longer logged int
		isLoggedIn = false;
	}

	/**
	 * Notifies the operations queue that an active network connection now
	 * exists. This method is being used by the network state changes receiver.
	 */
	public void notifyMobileConnectionExists(int mobileConnectionState) {
		// notifies the operations queue that a network connection is currently
		// active,
		// for case it is waiting for one

//		if (mobileConnectionState == NetworkStateChangesReceiver.MOBILE_CONNECTION_STATE_CONNECTED_SERVER_ROUTE_FAILED) {
//			resetQueueOnFailure(Operation.Result.NETWORK_ERROR);
//			notifyListeners(EVENTS.LOGIN_FAILED, null);
//		}

		synchronized (networkFailureLockObject) {
			if (networkFailureLockObject != null) {
				OperationsQueue.setIsnetworkFailureLockObjectNotified(true);
				networkFailureLockObject.notifyAll();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.att.mobile.android.vvm.control.IEventDispatcher#addEventListener(
	 * com.att.mobile.android.vvm.control .EventListener)
	 */
	@Override
	public void addEventListener(EventListener listener) {
		dispatcher.addListener(listener);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.att.mobile.android.vvm.control.IEventDispatcher#removeEventListener
	 * (com.att.mobile.android.vvm. control.EventListener)
	 */
	@Override
	public void removeEventListener(EventListener listener) {
		dispatcher.removeListener(listener);
	}

	/**
	 * Remove all event listeners
	 */
	public void removeEventListeners() {
		dispatcher.removeListeners();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.att.mobile.android.vvm.control.IEventDispatcher#notifyListeners(int,
	 * com.att.mobile.android.vvm.control.Operation.StatusCode)
	 */
	@Override
	public void notifyListeners(int eventId, ArrayList<Long> messageIDs) {
		dispatcher.notifyListeners(eventId, messageIDs);
	}

	public synchronized void setNumberOfMessages(int numberOfMessages) {
		this.numberOfMessages = numberOfMessages;
	}

	public synchronized void setQuota(int numberOfMessages) {
		ModelManager.getInstance().setSharedPreference(	Constants.KEYS.MAX_MESSAGES, numberOfMessages);	
		Logger.d(TAG,	"OperationsQueue.setQuota() server quota = "+numberOfMessages);
	}
	public synchronized int getNumberOfMessages() {
		return numberOfMessages;
	}

	private class HelperHandler extends Handler {

		public HelperHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {

			if (modelManager.getAttmStatus() != Constants.ATTM_STATUS.PROVISIONED) {
				// add new fetch bodies operation to the queue
				if (msg.what == EVENT_RETRY_FETCH_BODIES) {
					Logger.d(TAG,
							"OperationsQueue.handleMessage() EVENT_RETRY_FETCH_BODIES");
					enqueueOperation(new FetchBodiesOperation(context,
							dispatcher));
				} else if (msg.what == EVENT_XCHANGE_PASSWORD) {
					Logger.d(TAG,
							"OperationsQueue.handleMessage() EVENT_XCHANGE_PASSWORD");

					// verify connection exists and open if not exists
					if (connect() == Operation.Result.SUCCEED) {
						XChangeTUIPasswordOperation xChangeTUIPasswordOperation = new XChangeTUIPasswordOperation(
								context, (String) msg.obj, dispatcher);
						xChangeTUIPasswordOperation.execute();
						isXChangeTUIPasswordOperationPending = false;
						imap4handler.close();
					}
				}
			}
		}
	}
}
