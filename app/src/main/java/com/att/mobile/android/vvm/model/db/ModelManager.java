
package com.att.mobile.android.vvm.model.db;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import com.att.mobile.android.infra.utils.Crypto;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.Dispatcher;
import com.att.mobile.android.vvm.control.EventListener;
import com.att.mobile.android.vvm.control.IEventDispatcher;
import com.att.mobile.android.vvm.control.files.VvmFileUtils;
import com.att.mobile.android.vvm.control.receivers.NotificationService;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.ACTIONS;
import com.att.mobile.android.vvm.model.Constants.ATTM_STATUS;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.Constants.EXTRAS;
import com.att.mobile.android.vvm.model.Constants.KEYS;
import com.att.mobile.android.vvm.model.Constants.PasswordChangeRequiredStatus;
import com.att.mobile.android.vvm.model.Message;
import com.att.mobile.android.vvm.model.Message.CAN_OVERWRITE_STATE;
import com.att.mobile.android.vvm.model.db.upgrade.DBUpgrade;
import com.att.mobile.android.vvm.model.db.upgrade.DBUpgradeFactory;
import com.att.mobile.android.vvm.model.greeting.Greeting;
import com.att.mobile.android.vvm.screen.WelcomeActivity;
import com.att.mobile.android.vvm.watson.WatsonHandler;
import com.att.mobile.android.vvm.widget.VVMWidgetUpdateService;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * TODO - Royi: 01. open / close DB on every action 02. remove synchronization 03. use DB transactions with rollbacks
 */
public class ModelManager implements IEventDispatcher {

	/** holds database details include table names */
	private static final String DATABASE_NAME = "messages";
	public static final int DB_VERSION_1 = 1;
	public static final int DB_VERSION_2 = 2;
	public static final String DATABASE_TABLE_INBOX = "inbox";
	public final static String NO_TRANSCRIPTION_STRING = " ";
	private static final String TAG = "ModelManager";
 
	/** holds Inbox Table colum names */
	public static interface Inbox extends BaseColumns {
		public static final String KEY_UID = "uid";
		public static final String KEY_TIME_STAMP = "time";
		public static final String KEY_PHONE_NUMBER = "phone_number";
		public static final String KEY_TRANSCRIPTION = "transcription";
		public static final String KEY_FILE_NAME = "file_name";
		public static final String KEY_SAVED_STATE = "saved_state";
		public static final String KEY_IS_DELETED = "forward_state";
		public static final String KEY_IS_READ = "read_state";
		public static final String KEY_IS_TUISKIPPED = "tuiskipped";
		public static final String KEY_URGENT_STATUS = "urgent";
		public static final String KEY_DELIVERY_STATUS = "delivery_status";
		public static final String KEY_WAS_DOWNLOADED = "was_downloaded";
		// used to mark saved messages when SIM swap to allow new SIM messages
		// overwrite old SIM messages in case of the same message uid
		public static final String KEY_CAN_OVERWRITE = "can_overwrite";
		public static final String KEY_WATSON_TRANSCRIPTION = "watson_transcription";
	}

	/** holds Inbox table create SQL statement */
	private static final String TABLE_INBOX_CREATE_STATEMENT = new StringBuilder(
			"CREATE TABLE IF NOT EXISTS ").append(DATABASE_TABLE_INBOX)
			.append("(").append(Inbox._ID)
			.append(" INTEGER PRIMARY KEY AUTOINCREMENT, ")
			.append(Inbox.KEY_UID).append(" BIGINT UNIQUE NOT NULL, ")
			.append(Inbox.KEY_TIME_STAMP).append(" INTEGER NOT NULL, ")
			.append(Inbox.KEY_PHONE_NUMBER).append(" VARCHAR(20), ")
			.append(Inbox.KEY_TRANSCRIPTION).append(" TEXT, ")
			.append(Inbox.KEY_FILE_NAME).append(" TEXT, ")
			.append(Inbox.KEY_SAVED_STATE).append(" INT DEFAULT 0, ")
			.append(Inbox.KEY_IS_DELETED).append(" INT DEFAULT 0, ")
			.append(Inbox.KEY_IS_TUISKIPPED).append(" INT DEFAULT 0, ")
			.append(Inbox.KEY_URGENT_STATUS).append(" INT DEFAULT 0, ")
			.append(Inbox.KEY_DELIVERY_STATUS).append(" INT DEFAULT 0, ")
			.append(Inbox.KEY_WAS_DOWNLOADED).append(" INT DEFAULT ")
			.append(Message.WAS_DOWNLOADED_STATE.NO).append(", ")
			.append(Inbox.KEY_CAN_OVERWRITE).append(" INT DEFAULT ")
			.append(Message.CAN_OVERWRITE_STATE.NO).append(", ")
			.append(Inbox.KEY_IS_READ).append(" INT NOT NULL,")
			.append(Inbox.KEY_WATSON_TRANSCRIPTION).append(" INT DEFAULT 0);").toString();

	/*
	* "SELECT t.*, ss.count AS count "
    + "FROM inbox t "
    + "INNER JOIN ("
    + "    SELECT phone_number, MAX(time) AS maxdate, COUNT(phone_number) AS count "
    + "    FROM inbox "
    + "    GROUP BY phone_number "
    + ") ss ON t.phone_number = ss.phone_number AND t.time = ss.maxdate "
    + "GROUP BY t.phone_number "
    + "ORDER BY t.time DESC ",*/
	private static final String TABLE_INBOX_QUERY_AGGREGATED = new StringBuilder(
			"SELECT t.*, ss.count AS count FROM ").append(DATABASE_TABLE_INBOX).append(" t ").
			append("INNER JOIN (SELECT ")
			.append(Inbox._ID).append(",")
			.append(Inbox.KEY_UID).append(",")
			.append(Inbox.KEY_TIME_STAMP).append(",")
			.append(Inbox.KEY_PHONE_NUMBER).append(",")
			.append(Inbox.KEY_TRANSCRIPTION).append(",")
			.append(Inbox.KEY_FILE_NAME).append(",")
			.append(Inbox.KEY_IS_READ).append(",")
			.append(Inbox.KEY_SAVED_STATE).append(",")
			.append(Inbox.KEY_IS_DELETED).append(",")
			.append(Inbox.KEY_URGENT_STATUS).append(",")
			.append(Inbox.KEY_DELIVERY_STATUS).append(",")
			.append(" MAX(").append(Inbox.KEY_TIME_STAMP)
			.append(") AS maxdate, COUNT(").append(Inbox.KEY_PHONE_NUMBER).append(") AS count ")
			.append("    FROM ").append(DATABASE_TABLE_INBOX)
			.append("    GROUP BY ").append(Inbox.KEY_PHONE_NUMBER)
			.append(" ) ss ON t.").append(Inbox.KEY_PHONE_NUMBER).
					append(" = ss.").append(Inbox.KEY_PHONE_NUMBER).append(" AND t.").append(Inbox.KEY_TIME_STAMP).append(" = ss.maxdate ")
			.append("GROUP BY t.").append(Inbox.KEY_PHONE_NUMBER)
			.append(" ORDER BY t.").append(Inbox.KEY_TIME_STAMP).append(" DESC ").toString();

	private static final String allMessagesCountQuery = "SELECT COUNT(*) FROM " + DATABASE_TABLE_INBOX;

    public static final String[] COLUMNS_VM_LIST = new String[] {
            Inbox._ID,
            Inbox.KEY_UID, Inbox.KEY_TIME_STAMP,
            Inbox.KEY_PHONE_NUMBER, Inbox.KEY_TRANSCRIPTION,
            Inbox.KEY_FILE_NAME, Inbox.KEY_IS_READ,
            Inbox.KEY_SAVED_STATE, Inbox.KEY_IS_DELETED,
            Inbox.KEY_URGENT_STATUS, Inbox.KEY_DELIVERY_STATUS
    };

    public static final String[] AGGREGATED_VM = new String[] { Inbox._ID, Inbox.KEY_UID, Inbox.KEY_PHONE_NUMBER, "count_read", "count_unread" };
    public static final int IND_AGGREGATED_READ_COUNT       = 3;
    public static final int IND_AGGREGATED_UNREAD_COUNT     = 4;

    public static final String AND_NOT_DELETED = new StringBuilder().append(" AND ").append(Inbox.KEY_IS_DELETED).append("!=").append(Message.ReadDeletedState.DELETED).toString();

    public static final String WHERE_PHONE_NUMBER_AND_SAVED = new StringBuilder().append(Inbox.KEY_PHONE_NUMBER).append("=? AND ").append(Inbox.KEY_SAVED_STATE).append("=?").append(AND_NOT_DELETED).toString();
    public static final String WHERE_PHONE_NUMBER_AND_SAVED_NOT = new StringBuilder().append(Inbox.KEY_PHONE_NUMBER).append("=? AND ").append(Inbox.KEY_SAVED_STATE).append("!=?").append(AND_NOT_DELETED).toString();
    public static final String WHERE_SAVED = new StringBuilder().append(Inbox.KEY_SAVED_STATE).append("=?").append(AND_NOT_DELETED).toString();
    public static final String WHERE_SAVED_NOT = new StringBuilder().append(Inbox.KEY_SAVED_STATE).append("!=?").append(AND_NOT_DELETED).toString();
    public static final String WHERE_MARKED_AS_DELETE = new StringBuilder().append(Inbox.KEY_IS_DELETED).append(" = ").append(Message.ReadDeletedState.DELETED).toString();

	private static ModelManager instance;
	private Context context;

	private DatabaseHelper helper;
	private SQLiteDatabase db;

	private HashMap<String, String> metadataHashMap = null;
	private Dispatcher dispatcher = new Dispatcher();

	private static Object lock = new Object();

	/* holds application's shared preferences for reading and editing */
	private SharedPreferences prefs;
	private SharedPreferences.Editor prefsEditor = null;

	/** holds the UIDs of the messages to delete */
//	private Set<Long> messageToDeleteUIDs = null;

	/** holds the UIDs of the messages to mark as read */
	private Set<Long> messageToMarkAsReadUIDs = null;

	/** holds greetings */
	private ArrayList<Greeting> greetingList = null;

	/**
	 * holds the file names of the files which holds the pending delete and mark as read message
	 */
	public String pendingDeletesFilename;
	public String pendingReadsFilename;

	public static void createInstance(Context context) {
		// thread safe singleton
		synchronized (lock) {
			if (instance == null && context != null) {
				instance = new ModelManager(context);
				WatsonHandler.createInstance(context);
			}
		}
	}

	public static ModelManager getInstance() {
		if (instance == null) {
			ModelManager.createInstance(VVMApplication.getContext());
//			Log.e(TAG,
//					"ModelManager.getInstance() must call create instance before calling getInstance()");
		}
		return instance;
	}

	private ModelManager(Context context) {

		this.context = context;
		helper = new DatabaseHelper(context);
		metadataHashMap = new HashMap<String, String>();
		prefs = context.getSharedPreferences(Constants.KEYS.PREFERENCE_FILE_NAME, Context.MODE_PRIVATE);
		open();

		pendingDeletesFilename = context.getString(R.string.pendingDeletesFile,
				"deletes.ser");
		pendingReadsFilename = context.getString(R.string.pendingReadsFile,
				"reads.ser");

		// creates the message to delete and message to mark as read UIDs
		// collections
//		messageToDeleteUIDs = Collections.synchronizedSet(new HashSet<Long>());
//		messageToMarkAsReadUIDs = Collections
//				.synchronizedSet(new HashSet<Long>());
	}

	// ---opens the database---
	public synchronized ModelManager open() throws SQLException {
		db = helper.getWritableDatabase();
		return this;
	}

	public Dispatcher getDispatcher () {
		return dispatcher;
	}

	// ---closes the database---
	public synchronized void close() {
		helper.close();
	}

    public SQLiteDatabase getDb () {
        return db;
    }

	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * Insert welcome to the inbox, hard coded text and amr file
	 */
	public synchronized void insertWelcomeMessage() {

		Logger.i(TAG, "insertWelcomeMessage");
		// check if the user has already got the 'Welcome message'
		if (!getSharedPreferenceValue(KEYS.IS_WELCOME_MESSAGE_INSERTED, Boolean.class, false)) {
			// insert a welcome message to the data base to present it when user
			// first time use

			String fileName = Constants.WELCOME_MESSAGE_FILE_NAME;
			File copiedFile = new File(context.getFilesDir(), fileName);
			// copy the amr file to the application root directory
			// N. Slesuratin - the file name was changed to handle both old and new welcome message - US31911 "new welcome message"
			VvmFileUtils.copyFile(
					context.getResources().openRawResource(R.raw.welcome_amr_new),
					copiedFile);

			ContentValues contentValues = new ContentValues();

			// Put a big long id
			contentValues.put(Inbox.KEY_UID, Constants.WELCOME_MESSAGE_ID);
			contentValues.put(Inbox.KEY_TIME_STAMP, System.currentTimeMillis());
			contentValues.put(Inbox.KEY_PHONE_NUMBER, context.getString(R.string.welcomeMessagePhoneNumber));
			contentValues.put(Inbox.KEY_IS_READ, 0);
			contentValues.put(Inbox.KEY_TRANSCRIPTION, context.getString(R.string.welcomeMessageTranscription));
			contentValues.put(Inbox.KEY_FILE_NAME, fileName);

			contentValues.put(Inbox.KEY_SAVED_STATE, Message.SavedStates.INTERNAL_STORAGE);

			contentValues.put(Inbox.KEY_WAS_DOWNLOADED, Message.WAS_DOWNLOADED_STATE.YES);

			// insert the welcome message to the data base.
			try {
				long rowID = db.insertWithOnConflict(DATABASE_TABLE_INBOX, null, contentValues, SQLiteDatabase.CONFLICT_IGNORE);

				if (rowID != -1) {

					// set true to avoid adding the welcome message again (once for a new account)
					setSharedPreference(KEYS.IS_WELCOME_MESSAGE_INSERTED, true);

					Intent intent = new Intent(ACTIONS.ACTION_NEW_UNREAD_MESSAGE);
					intent.setClass(context, NotificationService.class);
					intent.putExtra(EXTRAS.EXTRA_NEW_MESSAGE_ROW_ID, rowID);
					context.startService(intent);
				}
			} catch (SQLiteException e) {
				Logger.e(TAG, "", e);
			}
		}
	}

	private void updateWidgets() {
		context.startService(new Intent(context, VVMWidgetUpdateService.class));
	}
	
	
	/**
	 * Inserts a new messge to application's database.
	 * 
	 * @param message (Message) message at the server.
	 * @return (boolean) true in case the message was updated / inserted successfully, false otherwise.
	 */
	public synchronized boolean updateOrInsertMessageToInbox(Message message) {
		long messageUID = message.getUid();
		long timeStamp = message.getDateMillis();
		String phoneNumber = message.getSenderPhoneNumber();
		boolean isRead = message.isRead();
		boolean isTuiskipped = message.isTuiskipped();
		boolean isUrgent = message.isUrgent();
		boolean isdeliveryStatus = message.isDeliveryStatus();
		// marked as true if we had another message with same uid that could be
		// overwritten and was deleted, so no need to try and update first, we
		// can go right ahead and insert a new message
		boolean oldFileDeleted = deleteMessageMarkedAsOverwrite(messageUID);

		// creates the content values for the message
		ContentValues contentValues = new ContentValues();
		// holds the row ID of the inserted / updated message
		long rowID = -1;
		ArrayList<Long> rowIDs = new ArrayList<Long>();

		// first, a message with the given UID is trying to be updated with the
		// read state
		// (the only thing that can be changed by the server for an already
		// exist message which
		// the application doesn't fetch on later requests)
		contentValues.put(Inbox.KEY_IS_READ, isRead ? 1 : 0);
		contentValues.put(Inbox.KEY_IS_TUISKIPPED, isTuiskipped ? 1 : 0);
		contentValues.put(Inbox.KEY_URGENT_STATUS, isUrgent ? 1 : 0);
		contentValues.put(Inbox.KEY_DELIVERY_STATUS, isdeliveryStatus ? 1 : 0);

		try {
			if (oldFileDeleted
					|| db.update(DATABASE_TABLE_INBOX, contentValues,
							new StringBuilder(Inbox.KEY_UID).append(" = ")
									.append(messageUID).toString(), null) != 1) {
				// If oldFileDeleted is marked as true or
				// in case updating a message with the given UID failed, it
				// means
				// that the message doesn't exist
				// in the application and should be inserted as a new message
				contentValues.put(Inbox.KEY_UID, messageUID);
				contentValues.put(Inbox.KEY_TIME_STAMP, timeStamp);
				contentValues.put(Inbox.KEY_PHONE_NUMBER, phoneNumber);

				// Mark it as save if auto saved
				contentValues.put(Inbox.KEY_SAVED_STATE, Message.SavedStates.INTERNAL_STORAGE);

				// in case inserting the new message succeed
				if ((rowID = db.insertWithOnConflict(DATABASE_TABLE_INBOX,
						null, contentValues, SQLiteDatabase.CONFLICT_IGNORE)) != -1) {
					rowIDs.add(rowID);

					// notifies any registered listeners regarding the new
					// inserted
					// message
//					notifyListeners(EVENTS.NEW_MESSAGE, rowIDs);
					// notify on unread message that was inserted - for
					// notifications
					if (!isRead) {
						updateWidgets();
						Intent intent = new Intent(ACTIONS.ACTION_NEW_UNREAD_MESSAGE);
						intent.setClass(context, NotificationService.class);
						intent.putExtra(EXTRAS.EXTRA_NEW_MESSAGE_ROW_ID, rowID);
						context.startService(intent);
					}
					notifyListeners(EVENTS.NEW_MESSAGE, rowIDs);
					Logger.d(TAG,"ModelManager.updateOrInsertMessageToInbox() - message was inserted");
					return true;
				}

				// new message couldn't be inserted
				Logger.d(TAG,"ModelManager.updateOrInsertMessageToInbox() - new message couldn't be inserted.  messageUID = "+ messageUID+ " timeStamp = "+ timeStamp);
				return false;
			}
		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);
			return false;
		}

		// the message was already exists and its read state was updated -
		// notifies any registered listeners regarding it
		rowIDs.add(rowID);
		notifyListeners(EVENTS.MESSAGE_READ_STATE_CHANGED, rowIDs);
		Logger.d(TAG, "ModelManager.updateOrInsertMessageToInbox() - message read state was updated");
		return true;
	}

	/**
	 * @param messageUID
	 * @return
	 */
	private synchronized boolean deleteMessageMarkedAsOverwrite(long messageUID) {

		boolean oldMessageDeleted = false;
		Cursor cr = null;
		try {
			// check if message exist and we can overwrite it
			cr = getMessageByUid(messageUID);
			if (cr != null && cr.moveToFirst()) {
				boolean canOverwrite = (cr.getInt(cr
						.getColumnIndex(Inbox.KEY_CAN_OVERWRITE)) == CAN_OVERWRITE_STATE.YES);
				// delete to overwrite later by inserting new message
				if (canOverwrite) {
					String fileName = cr.getString(cr
							.getColumnIndex(Inbox.KEY_FILE_NAME));
					try {
						if (db.delete(DATABASE_TABLE_INBOX, Inbox.KEY_UID + "="
								+ messageUID, null) == 1) {
							// delete message file
							VvmFileUtils.deleteInternalFile(context, fileName);
							// Delete external files
							VvmFileUtils.deleteExternalFile(context, fileName);
							// marked as true if we had another message with same uid
							// that could be overwritten and was deleted, so no need to
							// try and update first, we can go right ahead and insert a new message
							oldMessageDeleted = true;
						}
					} catch (SQLiteException e) {
						Logger.e(TAG, e.getMessage(), e);
					}
				}

			}
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
		} finally {
			if (cr != null) {
				cr.close();
			}
		}
		return oldMessageDeleted;
	}

	/**
	 * Sets message's new UID.
	 * 
	 * @param oldUID (long) messaeg's old UID.
	 * @param newUID (long) message's new UID to set.
	 * @return (boolean) true in case setting the new UID for the messages succeed, false otherwise.
	 */
	public synchronized boolean updateUid(long oldUID, long newUID) {
		// creates the content values for the message
		ContentValues updatedValues = new ContentValues();
		updatedValues.put(Inbox.KEY_UID, newUID);
		// reset message download - since new transcription is probably ready,
		// that the only reason a message uid will be changed
		updatedValues.put(Inbox.KEY_WAS_DOWNLOADED,
                Message.WAS_DOWNLOADED_STATE.NO);
		try {
			if (db.update(
					DATABASE_TABLE_INBOX,
					updatedValues,
					new StringBuilder(Inbox.KEY_UID).append(" = ")
							.append(oldUID).toString(), null) == 1) {

				return true;
			}
		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);

		}
		return false;
	}

	/**
	 * Sets a transcription for an existing message. The message updates message's UID as well, since it is being
	 * changed by the server when transcription is available for the message.
	 * @param rowID (long) message's row ID in application's database.
	 * @param UID (long) message's new UID at the server.
	 * @param transcription (String != null) message's transcription.
	 * 
	 * @return (boolean) true in case message's new UID and transcription were successfully updated, false otherwise. A
	 *         voicemail from 97239766012 at 08:05:15 AM 04/27/11 could not be processed to text.
	 */
	public synchronized boolean setMessageDetailsFromBodyText(Context context, long rowID, long UID, String transcription) {

		if (transcription == null) {
			// 1 lenght blank string means no transcription should be set for
			// the message
			transcription = NO_TRANSCRIPTION_STRING;
		} else {
			transcription = transcription.trim();
			if ((transcription.startsWith(Message.TranscriptionFixes.NO_TRANSCRIPTION_PREFIX) &&
					transcription.endsWith(Message.TranscriptionFixes.NO_TRANSCRIPTION_POSTFIX)) ||
					(transcription.startsWith(Message.TranscriptionFixes.NO_TRANSCRIPTION_PREFIX_1) &&
							transcription.trim().endsWith(Message.TranscriptionFixes.NO_TRANSCRIPTION_POSTFIX_1))) {
				// 1 Length blank string means no transcription should be set
				// for the message
				transcription = context
						.getString(R.string.trascriptionErrorText);
			}
		}

		// updates message's transcription and UID
		ContentValues updatedValues = new ContentValues();
		updatedValues.put(Inbox.KEY_UID, UID);
		updatedValues.put(Inbox.KEY_TRANSCRIPTION, transcription);
		updatedValues.put(Inbox.KEY_WATSON_TRANSCRIPTION, Message.WatsonTranscriptionState.TRANSCRIPTION_RECEIVED);
		updatedValues.put(Inbox.KEY_WAS_DOWNLOADED,	Message.WAS_DOWNLOADED_STATE.YES);

		try {
			boolean wasUpdate = db.update(DATABASE_TABLE_INBOX, updatedValues,
					new StringBuilder(Inbox._ID).append(" = ").append(rowID).toString(), null) == 1;

			// in case message's transcription was set successfully
			if (wasUpdate) {
				ArrayList<Long> rowIDs = new ArrayList<Long>();
				rowIDs.add(rowID);
				// notifies any registered listeners regarding the
				// transcription
				// set for the message
				notifyListeners(EVENTS.MESSAGE_TRANSCRIPTION_DOWNLOADED, rowIDs);
				Logger.d(TAG, "ModelManager.setMessageDetailsFromBodyText() - transcription updated for message with row ID " + rowID);
				return true;
			}
		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);
		}
		// returns that message's transcription wasn't updated
		Logger.d(TAG, "ModelManager.setMessageDetailsFromBodyText() - transcription was not updated for message with row ID " + rowID);
		return false;
	}

	/**
	 * Sets a transcription for an existing message. 
	 * @param messageID (long) message's  ID in application's database.
	 * @param transcription (String != null) message's transcription.
	 * 
	 * @return (boolean) true in case message's  transcription were successfully updated, false otherwise. A
	 *         voicemail from 97239766012 at 08:05:15 AM 04/27/11 could not be processed to text.
	 */
	public synchronized boolean setMessageTranscription(long messageID, String transcription) {
			transcription = transcription.trim();

		// updates message's transcription 
		ContentValues updatedValues = new ContentValues();
		updatedValues.put(Inbox.KEY_TRANSCRIPTION, transcription);

		try {
			boolean wasUpdate = db.update(DATABASE_TABLE_INBOX, updatedValues,
					new StringBuilder(Inbox._ID).append(" = ").append(messageID)
							.toString(), null) == 1;

			// in case message's transcription was set successfully
			if (wasUpdate) {
				ArrayList<Long> rowIDs = new ArrayList<Long>();
				rowIDs.add(messageID);
				// notifies any registered listeners regarding the
				// transcription
				// set for the message
				notifyListeners(EVENTS.MESSAGE_TRANSCRIPTION_DOWNLOADED, rowIDs);
				Logger.d(
						TAG,
						"ModelManager.setMessageTranscription() - transcription updated for message with message ID "
								+ messageID);
				return true;
			}
		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);
		}
		// returns that message's transcription wasn't updated
		Logger.d(
				TAG,
				"ModelManager.setMessageTranscription() - transcription was not updated for message with message ID "
						+ messageID);
		return false;
	}

	/**
	 * Sets a transcription error  for an existing message. 
	 * @param messageID (long) message's message ID in application's database.
	 * 
	 * @return (boolean) true in case message's new UID and transcription were successfully updated, false otherwise. 
	 */
	public synchronized boolean setMessageTranscriptionError(long messageID) {
		// updates message's transcription and watson status to error
		
		ContentValues updatedValues = new ContentValues();
		if(!isMessageHasTranscription(messageID)){
			updatedValues.put(Inbox.KEY_TRANSCRIPTION, context.getString(R.string.trascriptionErrorText));
		}
		updatedValues.put(Inbox.KEY_WATSON_TRANSCRIPTION, Message.WatsonTranscriptionState.TRANSCRIPTION_FAILED);

		try {
			boolean wasUpdate = db.update(DATABASE_TABLE_INBOX, updatedValues,
					new StringBuilder(Inbox._ID).append(" = ").append(messageID)
							.toString(), null) == 1;

			// in case message's transcription was set successfully
			if (wasUpdate) {
				ArrayList<Long> rowIDs = new ArrayList<Long>();
				rowIDs.add(messageID);
				Logger.d(
						TAG,
						"ModelManager.setMessageTranscriptionError() - transcription error updated for message with message ID = "+ messageID+" status set to - "+ Message.WatsonTranscriptionState.TRANSCRIPTION_FAILED);
				return true;
			}
		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);
		}
		// returns that message's transcription wasn't updated
		Logger.d( TAG, "ModelManager.setMessageTranscriptionError() - transcription error was not updated for message with message ID " + messageID);
		return false;
	}
	
	
	/**
	 * Sets messages current saved state. The given saved state will REPLACE any previous set saved state for the
	 * messages.
	 * 
	 * @param messageIDs (long[] != null) messages row IDs in application's database.
	 * @param savedState (int) messages' saved state to add (@see MessageSavedStates).
	 * @return (int) the number of messages which their saved state was set.
	 */
	public synchronized int setMessagesSavedState(Long[] messageIDs, int savedState) {

		// in case there are no messages to set their saved state, do nothing
		if (messageIDs == null || messageIDs.length == 0) {
			return 0;
		}

		// creates the content values with messags' new saved state
		ContentValues updatedValues = new ContentValues();
		updatedValues.put(Inbox.KEY_SAVED_STATE, savedState);

		StringBuilder whereClauseSB = new StringBuilder(Inbox._ID).append(buildWhere(messageIDs));

		// updates message with their new saved state
		int numberOfUpdatedMessage = 0;
		try {
			numberOfUpdatedMessage = db.update(DATABASE_TABLE_INBOX, updatedValues, whereClauseSB.toString(), null);
			Logger.d(TAG, "ModelManager.setMessageSavedState() savedState=" + savedState + numberOfUpdatedMessage + " messages");

			if (numberOfUpdatedMessage > 0) {
				if ( savedState == Message.SavedStates.INTERNAL_STORAGE_AS_SAVED ) {
                    ArrayList<Long> params = new ArrayList<Long>();
                    params.add(Long.valueOf(numberOfUpdatedMessage));
                    notifyListeners(EVENTS.MESSAGE_MARKED_AS_SAVED, params);
                } else if ( savedState == Message.SavedStates.INTERNAL_STORAGE ) {
                    notifyListeners(EVENTS.MESSAGE_MARKED_AS_UNSAVED, null);
                }
            }
            return numberOfUpdatedMessage;

		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);
		}

		// returns the number of messages which their saved state was set
		return numberOfUpdatedMessage;
	}

    public synchronized int setMessagesSavedState(String[] phoneNumbers, int savedState) {

        // in case there are no messages to set their saved state, do nothing
        if (phoneNumbers == null || phoneNumbers.length == 0) {
            return 0;
        }

        // creates the content values with messags' new saved state
        ContentValues updatedValues = new ContentValues();
        updatedValues.put(Inbox.KEY_SAVED_STATE, savedState);

        StringBuilder whereClauseSB = new StringBuilder(Inbox.KEY_PHONE_NUMBER).append(buildWhere(phoneNumbers));

        int numberOfUpdatedMessage = 0;
        try {
            numberOfUpdatedMessage = db.update(DATABASE_TABLE_INBOX, updatedValues, whereClauseSB.toString(), null);
            Logger.d(TAG, "ModelManager.setMessageSavedState() - saved state was set for " + numberOfUpdatedMessage + " messages");

            if (numberOfUpdatedMessage > 0) {
                if ( savedState == Message.SavedStates.INTERNAL_STORAGE_AS_SAVED ) {
                    ArrayList<Long> params = new ArrayList<Long>();
                    params.add(Long.valueOf(numberOfUpdatedMessage));
                    notifyListeners(EVENTS.MESSAGE_MARKED_AS_SAVED, params);
                } else if ( savedState == Message.SavedStates.INTERNAL_STORAGE ) {
                    notifyListeners(EVENTS.MESSAGE_MARKED_AS_UNSAVED, null);
                }
                Logger.d(TAG, "ModelManager.setMessagesSavedState() - "	+ numberOfUpdatedMessage + " savedState=" + savedState);
                return numberOfUpdatedMessage;
            }

        } catch (SQLiteException e) {
            Logger.e(TAG, e.getMessage(), e);
        }

        // returns the number of messages which their saved state was set
        return numberOfUpdatedMessage;
    }


    private String buildWhere ( long[] longArr ) {

        StringBuilder whereClause = new StringBuilder(" IN (");
        for (long next : longArr) {
            whereClause.append(next).append(",");
        }
        whereClause.setCharAt(whereClause.length() - 1, ')');
        return whereClause.toString();
    }

    private String buildWhere ( Long[] longArr ) {

        StringBuilder whereClause = new StringBuilder(" IN (");
        for (long next : longArr) {
            whereClause.append(next).append(",");
        }
        whereClause.setCharAt(whereClause.length() - 1, ')');
        return whereClause.toString();
    }
    private String buildWhere ( String[] strArr ) {

        StringBuilder whereClause = new StringBuilder(" IN (");
        for (String next : strArr) {
            whereClause.append("'").append(next).append("',");
        }
        whereClause.setCharAt(whereClause.length() - 1, ')');
        return whereClause.toString();
    }

	private String buildAndNotSaved(){
		return " And " + Inbox.KEY_SAVED_STATE + " <> " + Message.SavedStates.INTERNAL_STORAGE_AS_SAVED;
	}
	private String buildAndSaved(){
		return " And " + Inbox.KEY_SAVED_STATE + " = " + Message.SavedStates.INTERNAL_STORAGE_AS_SAVED;
	}
	/**
	 * Sets messages current read state.
	 * 
	 * @param rowIDs (long[] != null) messages row IDs in application's database.
	 * @return (int) the number of messages which their read state was set.
	 */
	public synchronized int setMessagesAsRead(long[] rowIDs) {
		// in case there are no messages to set their read state, do nothing
		if (rowIDs.length == 0) {
			return 0;
		}

		// creates the content values with messages' new read state
		ContentValues updatedValues = new ContentValues();
		updatedValues.put(Inbox.KEY_IS_READ, Message.ReadDeletedState.READ);

		// creates the where clause for the SQL update statement
		ArrayList<Long> rowIDsArr = new ArrayList<Long>();
		StringBuilder whereClauseSB = new StringBuilder(Inbox._ID).append(buildWhere(rowIDs));

		// updates messages with their new read state
		int numberOfUpdatedMessage = 0;
		try {
			numberOfUpdatedMessage = db.update(DATABASE_TABLE_INBOX, updatedValues, whereClauseSB.toString(), null);
			Logger.d(TAG, "ModelManager.setMessagesReadState() - true. read state was set for " + numberOfUpdatedMessage + " messages");

			// in case all messages saved state were set
			if (numberOfUpdatedMessage == rowIDs.length) {
				// notifies any registered listeners regarding the read state
				// set
				notifyListeners(EVENTS.MESSAGE_READ_STATE_CHANGED, rowIDsArr);
				Logger.d(TAG, "ModelManager.setMessagesReadState() - " + numberOfUpdatedMessage	+ " messages were marked as saved");
			} else {
				Logger.d(TAG, "ModelManager.setMessagesReadState() - true. read state was set for " + numberOfUpdatedMessage + " messages while total number of messages to set their saved status was " + rowIDs.length);
			}
		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);
		}
		updateWidgets();
		// returns the number of messages which their read state was set
		return numberOfUpdatedMessage;
	}
	/**
	 * Sets messages current read state.
	 *
	 * @param UID
	 * @return (int) the number of messages which their read state was set.
	 */
	public synchronized int setMessageAsRead(long UID) {

		// creates the content values with messages' new read state
		ContentValues updatedValues = new ContentValues();
		updatedValues.put(Inbox.KEY_IS_READ, Message.ReadDeletedState.READ);
		ArrayList<Long> arrIDs =	new ArrayList<Long>();
		arrIDs.add(UID);
		// creates the where clause for the SQL update statement
		StringBuilder whereClauseSB = new StringBuilder(Inbox.KEY_UID).append("=").append(UID);

		// updates messages with their new read state
		int numberOfUpdatedMessage = 0;
		try {
			numberOfUpdatedMessage = db.update(DATABASE_TABLE_INBOX,
					updatedValues, whereClauseSB.toString(), null);
			Logger.d(TAG,"ModelManager.setMessagesReadState() - true. read state was set for " + numberOfUpdatedMessage + " messages");

			// in case all messages saved state were set
			// notifies any registered listeners regarding the read state
			// set
			notifyListeners(EVENTS.MESSAGE_READ_STATE_CHANGED, arrIDs);
			Logger.d(TAG,"ModelManager.setMessagesReadState() - " + numberOfUpdatedMessage + " messages were marked as saved");
		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);
		}
		updateWidgets();
		// returns the number of messages which their read state was set
		return numberOfUpdatedMessage;
	}

	public synchronized int setMessagesAsDeleted(Long[] iDs) {
		// in case there are no messages to set their deleted state, do nothing
		if (iDs == null || iDs.length == 0) {
			return 0;
		}

		// creates the content values with messages' new deleted state
		ContentValues updatedValues = new ContentValues();
		updatedValues.put(Inbox.KEY_IS_DELETED, Message.ReadDeletedState.DELETED);

		// creates the where clause for the SQL update statement
		StringBuilder whereClauseSB = new StringBuilder(Inbox._ID).append(buildWhere(iDs));
        Logger.d(TAG, "setMessagesAsDeleted() whereClauseSB=" + whereClauseSB);

		// updates messages with their new deleted state
		int numberOfUpdatedMessage = 0;
		try {
			numberOfUpdatedMessage = db.update(DATABASE_TABLE_INBOX, updatedValues, whereClauseSB.toString(), null);
			Logger.d(TAG, "setMessagesAsDeleted() - true. deleted state was set for " + numberOfUpdatedMessage + " messages");

			// in case all messages saved state were set
			if (numberOfUpdatedMessage > 0) {
				notifyListeners(EVENTS.MARK_AS_DELETED_FINISHED, null);
				Logger.d(TAG, "ModelManager.setMessagesReadState() - " + numberOfUpdatedMessage + " messages were marked as saved");
			} else {
				Logger.d(TAG, "ModelManager.setMessagesReadState() - true. read state was set for 0 messages.");
			}
		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);
		}
		updateWidgets();
		// returns the number of messages which their read state was set
		return numberOfUpdatedMessage;
	}

    /**
     * Update KEY_IS_DELETED to DELETED parameter for all phone numbers in the list
     * @param phoneNumbers
     * @return
     */
    public int setMessagesAsDeleted(String[] phoneNumbers, boolean deleteSaved) {
        // in case there are no messages to set their deleted state, do nothing
        if (phoneNumbers == null || phoneNumbers.length == 0) {
            return 0;
        }

        // creates the content values with messages' new deleted state
        ContentValues updatedValues = new ContentValues();
        updatedValues.put(Inbox.KEY_IS_DELETED, Message.ReadDeletedState.DELETED);

        // creates the where clause for the SQL update statement
        StringBuilder whereClauseSB = new StringBuilder(Inbox.KEY_PHONE_NUMBER).append(buildWhere(phoneNumbers));
		whereClauseSB.append(deleteSaved ? buildAndSaved() : buildAndNotSaved());
        Logger.d(TAG, "setMessagesAsDeleted() whereClauseSB=" + whereClauseSB);

        // updates messages with their new deleted state
        int numberOfUpdatedMessage = 0;
        try {
            numberOfUpdatedMessage = db.update(DATABASE_TABLE_INBOX, updatedValues, whereClauseSB.toString(), null);
            Logger.d(TAG, "setMessagesAsDeleted() - true. deleted state was set for " + numberOfUpdatedMessage + " messages");

            notifyListeners(EVENTS.MARK_AS_DELETED_FINISHED, null);

        } catch (SQLiteException e) {
            Logger.e(TAG, e.getMessage(), e);
        }

        updateWidgets();

        return numberOfUpdatedMessage;
    }


	public void deleteMessagesMarkedAsDeleted() {

		MessageDo[] messageDos = getAllMarkedAsDeletedMessages();
		if (messageDos.length > 0) {
			deleteMessagesFiles(messageDos);
			// deletes the message from the application data base
			deleteMessageFromDB(messageDos);
			// go update notification since new messages num may have been changed
			((VVMApplication) (context.getApplicationContext())).updateNotification();
			Logger.d(TAG, "ModelManager.deleteMessages() notifyListeners EVENTS.DELETE_FINISHED");
			notifyListeners(EVENTS.DELETE_FINISHED, null);
		}
	}


	public synchronized MessageDo[] getAllMarkedAsDeletedMessages() {
		Cursor queryResults = null;
		try {
			// gets the file names of all existing messages in the application
			queryResults = db.query(DATABASE_TABLE_INBOX, new String[] {
							Inbox.KEY_UID, Inbox._ID, Inbox.KEY_TIME_STAMP, Inbox.KEY_PHONE_NUMBER, Inbox.KEY_FILE_NAME, Inbox.KEY_IS_READ, Inbox.KEY_TRANSCRIPTION
					},
					WHERE_MARKED_AS_DELETE, null, null, null, Inbox.KEY_TIME_STAMP + " desc");
			if (queryResults == null) {
				Logger.d(TAG,"getAllMarkedAsDeletedMessages() - No MarkedAsDeleted messages in the application");
				return new MessageDo[0];
			}

			int numberOfExistingMessages = queryResults.getCount();
			if (numberOfExistingMessages > 0 && queryResults.moveToFirst()) {
				MessageDo[] messageDos = new MessageDo[numberOfExistingMessages];
				int position = 0;
				do {
					messageDos[position] = new MessageDo();
					messageDos[position].setFileName(queryResults
							.getString(queryResults
									.getColumnIndex(Inbox.KEY_FILE_NAME)));
					messageDos[position].setId(queryResults
							.getLong(queryResults.getColumnIndex(Inbox._ID)));
					messageDos[position].setPhoneNumber(queryResults.getString(queryResults.getColumnIndex(Inbox.KEY_PHONE_NUMBER)));
					messageDos[position].setReadState(queryResults.getString(queryResults.getColumnIndex(Inbox.KEY_IS_READ)));
					messageDos[position].setTime(queryResults.getString(queryResults.getColumnIndex(Inbox.KEY_TIME_STAMP)));
					messageDos[position].setTranscription(queryResults.getString(queryResults.getColumnIndex(Inbox.KEY_TRANSCRIPTION)));
					messageDos[position++]
							.setUid(queryResults.getLong(queryResults
									.getColumnIndex(Inbox.KEY_UID)));


				} while (queryResults.moveToNext()
						&& !queryResults.isAfterLast());

				return messageDos;
			}
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
		} finally {
			// releases resources if needed
			if (queryResults != null) {
				queryResults.close();
			}
		}

		return new MessageDo[0];
	}
	/**
	 * Sets message's file name in appication's database. This method is being executed when the voice message file was
	 * downloaded from the server and stored in application's internal storage. The method updates message's saved state
	 * as well, with the Message.INTERNAL_STORAGE flag (added to the already existing Message.TUI flag).
	 * 
	 * @param messageID (long) the ID of the message to set its file name.
	 * @param fileName (String != null) message's audio file name.
	 * @return (boolean) true in case the file name for the message has been set, false oterhwise.
	 */
	public synchronized boolean setMessageFileName(long messageID, String fileName) {

		Logger.d(TAG, "setMessageFileName messageID=" + messageID + " fileName=" + fileName);
		ContentValues updatedValues = new ContentValues();
		updatedValues.put(Inbox.KEY_FILE_NAME, fileName);

		updatedValues.put(Inbox.KEY_SAVED_STATE, Message.SavedStates.INTERNAL_STORAGE);

		// updates message's record with the file name and new saved state in
		// application's database
		boolean wasMessageSet = false;
		try {
			wasMessageSet = db.update(DATABASE_TABLE_INBOX, updatedValues,
					new StringBuilder(Inbox._ID).append(" = ")
							.append(messageID).toString(), null) == 1;

			// in case message update was successfull
			if (wasMessageSet) {
				ArrayList<Long> messageIds = new ArrayList<Long>();
				messageIds.add(messageID);
				// notifies any registered listernes that message's audio file
				// has
				// been downloaded
				if(WatsonHandler.needRequestWatson( messageID)){
					Logger.d(TAG, "setMessageFileName need request Watson transcription message Uid = "+messageID + " fileName = " + fileName+ "callWatsonTranscriptionTask");
					WatsonHandler.getInstance().callWatsonTranscriptionTask(messageID, fileName);
				}
				notifyListeners(EVENTS.MESSAGE_FILE_DOWNLOADED, messageIds);
			}
		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);
		}
		// returns whether the message file name has been set
		return wasMessageSet;
	}

	public synchronized boolean setMessageErrorFile(long messageID) {
		ContentValues updatedValues = new ContentValues();

		updatedValues.put(Inbox.KEY_SAVED_STATE, Message.SavedStates.ERROR);

		// updates message's record with the file name and new saved state in
		// application's database
		boolean wasMessageSet = false;
		try {
			wasMessageSet = db.update(DATABASE_TABLE_INBOX, updatedValues,
					new StringBuilder(Inbox._ID).append(" = ")
							.append(messageID).toString(), null) == 1;

			// in case message update was successfull
			if (wasMessageSet) {
				ArrayList<Long> messageIds = new ArrayList<Long>();
				messageIds.add(messageID);
				// notifies any registered listernes that message's audio file
				// has
				// been downloaded
				notifyListeners(EVENTS.MESSAGE_FILE_DOWNLOAD_ERROR, messageIds);
			}
		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);
		}

		// returns whether the message file name has been set
		return wasMessageSet;
	}

	public synchronized boolean setMessageWatsonStatus(long messageID, int watsonStatus){

		ContentValues updatedValues = new ContentValues();
		updatedValues.put(Inbox.KEY_WATSON_TRANSCRIPTION, watsonStatus);
		boolean wasMessageSet = false;
		try {
			wasMessageSet = db.update(DATABASE_TABLE_INBOX, updatedValues,	new StringBuilder(Inbox._ID).append(" = ").append(messageID).toString(), null) == 1;
			Logger.d(TAG, "ModelManager.getInstance() setMessageFileNotFound(" + messageID + ") Finished");
		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);
		}
		return wasMessageSet;

	}
	
	public synchronized int getMessageWatsonStatus(long messageID){
		Cursor cursor = null;

		try {
			// queries the database for message's transcription
			cursor = db.query(DATABASE_TABLE_INBOX, new String[] { Inbox.KEY_WATSON_TRANSCRIPTION },
					new StringBuilder(Inbox._ID).append("=").append(messageID)
							.toString(), null, null, null, null);

			if (cursor != null && cursor.getCount() == 1 && cursor.moveToFirst()) {
				// returns the transcription for the message with the given ID
				int wStatus = cursor.getInt(cursor.getColumnIndex(Inbox.KEY_WATSON_TRANSCRIPTION));
				Logger.d(TAG, "ModelManager.getInstance() getMessageWatsonStatus(" + messageID + ") wStatus = "+ wStatus);
                return wStatus;
			}

			return -1;
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
			return -1;
		} finally {
			// releases resources if needed
			if (cursor != null) {
				cursor.close();
			}
		}

	}

//	/**
//	 * Gets all message from inbox, according to a given filter type, grouped by phone number.
//	 *
//	 * @param filterType (int) the filter type to get inbox messages according to (@see MessageFilter).
//	 * @return (Cursor) inbox messages according to the given filter.
//	 */
//	public synchronized Cursor getAllAggregatedMessagesFromInbox(int filterType) {
		// holds the where clause for the query
//		StringBuilder whereClause = null;

		// builds query's where clause according to the given filter type
//		switch (filterType) {
//			case (MessageFilter.TYPE_ALL): {
//				// nothing to do here
//				break;
//			}
//			case (MessageFilter.TYPE_UNREAD): {
//				whereClause = new StringBuilder().append(Inbox.KEY_IS_READ)
//						.append("=").append(Message.ReadDeletedState.UNREAD);
//				break;
//			}
//			case (MessageFilter.TYPE_SAVED): {
//				whereClause = new StringBuilder().append(Inbox.KEY_SAVED_STATE)
//						.append(" = ")
//						.append(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED);
//				break;
//			}
//			default: {
//				break;
//			}
//		}
//
//		Cursor messages = null;
//		try {
//
//			messages = db.rawQuery(TABLE_INBOX_QUERY_AGGREGATED,null);
//			// performs the query against application's database
//
//			// in case messages exist, moves to the first one
//			if (messages != null) {
//				messages.moveToFirst();
//			}
//		} catch (SQLiteException e) {
//			Logger.e(TAG, e.getMessage(), e);
//		}
//		return messages;
//	}
	/**
	 * Gets all message from inbox, according to a given filter type.
	 * 
	 * @param filterType (int) the filter type to get inbox messages according to (@see MessageFilter).
	 * @return (Cursor) inbox messages according to the given filter.
	 */
	public synchronized Cursor getAllMessagesFromInbox(int filterType, String phoneNumber) {

//		 builds query's where clause according to the given filter type
		boolean isGroupAndAggreg = isGroupByContact() && phoneNumber != null;
		String where =  isGroupAndAggreg ? (filterType == Constants.MessageFilter.TYPE_SAVED ? ModelManager.WHERE_PHONE_NUMBER_AND_SAVED : ModelManager.WHERE_PHONE_NUMBER_AND_SAVED_NOT) :
				(filterType == Constants.MessageFilter.TYPE_SAVED ? ModelManager.WHERE_SAVED : ModelManager.WHERE_SAVED_NOT);
		String[] selectArgs = isGroupAndAggreg ? new String[]{ String.valueOf(phoneNumber), String.valueOf(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED)}
				: new String[] {String.valueOf(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED)};
		Logger.i(TAG, "getAllMessagesFromInbox whereClause=" + where.toString());

		Cursor messages = null;
		try {

			// performs the query against application's database
			messages = db.query(DATABASE_TABLE_INBOX, COLUMNS_VM_LIST, where.toString() , selectArgs, null, null, Inbox.KEY_TIME_STAMP + " desc");

			// in case messages exist, moves to the first one
			if (messages != null) {
				messages.moveToFirst();
			}
		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);
		}
		return messages;
	}


	/**
	 * Gets all message from inbox, according to a given filter type.
	 *
	 * @return (Cursor) inbox messages according to the given filter.
	 */
	public synchronized Cursor getAllMessagesFromInbox() {

//		String whereClause = Inbox.KEY_SAVED_STATE+"!=? AND " + Inbox.KEY_IS_DELETED + "!=?";
//		String [] whereArgs = {String.valueOf(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED), String.valueOf(Message.ReadDeletedState.DELETED)};
		String whereClause = Inbox.KEY_SAVED_STATE+"!=?";
		String [] whereArgs = {String.valueOf(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED)};

		return db.query(DATABASE_TABLE_INBOX, new String[] {
							Inbox._ID,
							Inbox.KEY_UID, Inbox.KEY_TIME_STAMP,
							Inbox.KEY_PHONE_NUMBER, Inbox.KEY_TRANSCRIPTION,
							Inbox.KEY_FILE_NAME, Inbox.KEY_IS_READ,
							Inbox.KEY_SAVED_STATE,
							Inbox.KEY_URGENT_STATUS, Inbox.KEY_DELIVERY_STATUS
					},
					whereClause, whereArgs,
					null, null, Inbox.KEY_TIME_STAMP + " desc");
	}

//	public synchronized Cursor getSavedMessagesFromInbox() {
//
////		String whereClause = Inbox.KEY_SAVED_STATE+"=? AND " + Inbox.KEY_IS_DELETED + "!=?";
////		String [] whereArgs = {String.valueOf(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED), String.valueOf(Message.ReadDeletedState.DELETED)};
//		String whereClause = Inbox.KEY_SAVED_STATE+"=?";
//		String [] whereArgs = {String.valueOf(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED)};
////
//		// performs the query against application's database
//		return db.query(DATABASE_TABLE_INBOX, new String[] {
//						Inbox._ID,
//						Inbox.KEY_UID, Inbox.KEY_TIME_STAMP,
//						Inbox.KEY_PHONE_NUMBER, Inbox.KEY_TRANSCRIPTION,
//						Inbox.KEY_FILE_NAME, Inbox.KEY_IS_READ,
//						Inbox.KEY_SAVED_STATE,
//						Inbox.KEY_URGENT_STATUS, Inbox.KEY_DELIVERY_STATUS
//				},
//				whereClause, whereArgs,
//				null, null, Inbox.KEY_TIME_STAMP + " desc");
//	}

//	public Cursor getUserMessagesFromInbox(String phoneNumber, boolean getSaved) {
//
//		String equilibrium;
//		if(getSaved){
//			equilibrium = "==?";
//		}else{
//			equilibrium = "!=?";
//		}
//
////		String whereClause = Inbox.KEY_PHONE_NUMBER+"=? AND " + Inbox.KEY_IS_DELETED + "!=? AND "+ Inbox.KEY_SAVED_STATE + equilibrium;
////
////
////		String [] whereArgs = {TextUtils.isEmpty(phoneNumber) ? "" : phoneNumber, String.valueOf(Message.ReadDeletedState.DELETED), String.valueOf(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED)};
//		String whereClause = Inbox.KEY_PHONE_NUMBER+"=? AND " + Inbox.KEY_SAVED_STATE + equilibrium;
//
//
//		String [] whereArgs = {TextUtils.isEmpty(phoneNumber) ? "" : phoneNumber,  String.valueOf(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED)};
//
//		Cursor messages = null;
//		try {
//
//			// performs the query against application's database
//			messages = db.query(DATABASE_TABLE_INBOX, new String[] {
//							Inbox._ID,
//							Inbox.KEY_UID, Inbox.KEY_TIME_STAMP,
//							Inbox.KEY_PHONE_NUMBER, Inbox.KEY_TRANSCRIPTION,
//							Inbox.KEY_FILE_NAME, Inbox.KEY_IS_READ,
//							Inbox.KEY_SAVED_STATE,
//							Inbox.KEY_URGENT_STATUS, Inbox.KEY_DELIVERY_STATUS
//					},
//					whereClause, whereArgs,
//					null, null, Inbox.KEY_TIME_STAMP + " desc");
//
//		} catch (SQLiteException e) {
//			Logger.e(TAG, e.getMessage(), e);
//		}
//		return messages;
//	}

//	public interface VoiceMailRequestCallback{
//		void onDone(ArrayList<VoiceMailObject> voiceMailObjects);
//	}
//
//	public interface VoiceMailAsAggregatedRequestCallback{
//		void onDone(ArrayList<AggregatedItemObject> aggregatedItemObjects);
//	}
//
//	private static GetSavedVoiceMailsAsAggregatedAsync getSavedAsAggAsync;
//
//	public static void getSavedVoiceMailsAsAggregated(VoiceMailAsAggregatedRequestCallback callback) {
//
//		if(getSavedAsAggAsync != null && getSavedAsAggAsync.getStatus() != AsyncTask.Status.FINISHED){
//			getSavedAsAggAsync.cancel(true);
//		}
//
//		getSavedAsAggAsync = new GetSavedVoiceMailsAsAggregatedAsync(callback);
//		getSavedAsAggAsync.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//
//	}

//	private static class GetSavedVoiceMailsAsAggregatedAsync extends AsyncTask<Void,Void,ArrayList<AggregatedItemObject>>{
//
//		WeakReference <VoiceMailAsAggregatedRequestCallback> mailRequestCallback;
//
//		public GetSavedVoiceMailsAsAggregatedAsync(VoiceMailAsAggregatedRequestCallback callback){
//			mailRequestCallback = new WeakReference<VoiceMailAsAggregatedRequestCallback>(callback);
//		}
//
//		@Override
//		protected ArrayList<AggregatedItemObject> doInBackground(Void... params) {
//
//			ArrayList<VoiceMailObject> allVoiceMails = new ArrayList<>();
//			ArrayList<AggregatedItemObject> mAggItems = new ArrayList<>();
//			Cursor cursor = null;
//
//			try {
//				cursor = ModelManager.getInstance().getSavedMessagesFromInbox();
//
//				if (cursor == null) {
//					return mAggItems;
//				}
//
//				while (cursor.moveToNext()) {
//
//					long id = cursor.getLong(cursor.getColumnIndex(Inbox._ID));
//					long uid = cursor.getLong(cursor.getColumnIndex(Inbox.KEY_UID));
//					String senderPhoneNumber = cursor.getString(cursor.getColumnIndex(Inbox.KEY_PHONE_NUMBER));
//					boolean isDeliveryOk = cursor.getInt(cursor.getColumnIndex(Inbox.KEY_DELIVERY_STATUS)) == 1;
//					long timeStamp = cursor.getLong(cursor.getColumnIndex(Inbox.KEY_TIME_STAMP));
//					String transcription = cursor.getString(cursor.getColumnIndex(Inbox.KEY_TRANSCRIPTION));
//					boolean isRead = cursor.getInt(cursor.getColumnIndex(Inbox.KEY_IS_READ)) == Message.ReadDeletedState.READ;
//					int savedStatus = cursor.getInt(cursor.getColumnIndex(Inbox.KEY_SAVED_STATE));
//					String fileName = cursor.getString(cursor.getColumnIndex(Inbox.KEY_FILE_NAME));
//					boolean isUrgent = (cursor.getInt(cursor.getColumnIndex(Inbox.KEY_URGENT_STATUS)) == 1);
//
//					VoiceMailObject voiceMailObject = new VoiceMailObject(id, uid, timeStamp, senderPhoneNumber, fileName, isRead, savedStatus, isUrgent, isDeliveryOk, transcription, null);
//					allVoiceMails.add(allVoiceMails.size(), voiceMailObject);
//				}
//
//			} catch ( Exception e ) {
//				Logger.e(TAG, "doInBackground exception=" + e.getMessage(), e);
//			} finally {
//				if ( cursor != null ) {
//					cursor.close();
//				}
//			}
//
//			for(VoiceMailObject voiceMailObject : allVoiceMails){
//
//				String phoneNumber = voiceMailObject.getPhoneNumber();
//				if(!mAggItems.contains(new AggregatedItemObject(phoneNumber, voiceMailObject.isSaved()))){
//
//					AggregatedItemObject agg = new AggregatedItemObject(phoneNumber, voiceMailObject.isSaved());
//					agg.setLastVoiceMailDate(voiceMailObject.getTimeStamp());
//                    agg.addMessageId(voiceMailObject.getId());
//					agg.addMessageUid(voiceMailObject.getUid());
//					agg.incrementTotalCount();
//					if(voiceMailObject.isRead()){
//						agg.incrementReadCount();
//					}
//
//					mAggItems.add(mAggItems.size(), agg);
//
//				}else{
//
//					AggregatedItemObject agg = mAggItems.get(mAggItems.indexOf(new AggregatedItemObject(phoneNumber, voiceMailObject.isSaved())));
//					agg.incrementTotalCount();
//                    agg.addMessageId(voiceMailObject.getId());
//					agg.addMessageUid(voiceMailObject.getUid());
//					if(voiceMailObject.isRead()){
//						agg.incrementReadCount();
//					}
//
//				}
//
//			}
//
//			return mAggItems;
//		}
//
//		@Override
//		protected void onPostExecute(ArrayList<AggregatedItemObject> results) {
//			super.onPostExecute(results);
//			VoiceMailAsAggregatedRequestCallback callback = mailRequestCallback.get();
//			if(callback != null) {
//				callback.onDone(results);
//			}
//		}
//	}
//
//	private static GetAllVoiceMailsAsAggregatedAsync getAllAsAggAsync;
//
//	public static void getAllVoiceMailsAsAggregated(VoiceMailAsAggregatedRequestCallback callback) {
//		Logger.d(TAG, "getAllVoiceMailsAsAggregated");
//		if(getAllAsAggAsync != null && getAllAsAggAsync.getStatus() != AsyncTask.Status.FINISHED){
//			Logger.d(TAG, "getAllVoiceMailsAsAggregated - cancel async");
//			getAllAsAggAsync.cancel(true);
//		}
//
//		getAllAsAggAsync = new GetAllVoiceMailsAsAggregatedAsync(callback);
//		getAllAsAggAsync.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//		Logger.d(TAG, "getAllVoiceMailsAsAggregated - run on executer");
//	}
//
//	private static GetAllVoiceMailsAsync getAllAsync;
//
//	public static void getAllVoiceMails(VoiceMailRequestCallback callback){
//
//		if(getAllAsync != null && getAllAsync.getStatus() != AsyncTask.Status.FINISHED){
//			getAllAsync.cancel(true);
//		}
//
//		getAllAsync = new GetAllVoiceMailsAsync(callback);
//		getAllAsync.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//	}
//
//	private static class GetAllVoiceMailsAsAggregatedAsync extends AsyncTask<Void,Void,ArrayList<AggregatedItemObject>>{
//
//		WeakReference <VoiceMailAsAggregatedRequestCallback> mailRequestCallback;
//
//		public GetAllVoiceMailsAsAggregatedAsync(VoiceMailAsAggregatedRequestCallback callback){
//			mailRequestCallback = new WeakReference<VoiceMailAsAggregatedRequestCallback>(callback);
//		}
//
//		@Override
//		protected ArrayList<AggregatedItemObject> doInBackground(Void... params) {
//			Logger.d(TAG, "GetAllVoiceMailsAsAggregatedAsync - started");
//			Cursor cursor = null;
//			ArrayList<VoiceMailObject> allVoiceMails = new ArrayList<>();
//			ArrayList<AggregatedItemObject> mAggItems = new ArrayList<>();
//
//			try {
//				cursor = ModelManager.getInstance().getAllMessagesFromInbox();
//				if (cursor == null) {
//					Logger.d(TAG, "GetAllVoiceMailsAsAggregatedAsync - cursor is null");
//					return mAggItems;
//				}
//				if(cursor.moveToFirst()){
//					do {
//					long id = cursor.getLong(cursor.getColumnIndex(Inbox._ID));
//					long uid = cursor.getLong(cursor.getColumnIndex(Inbox.KEY_UID));
//					String senderPhoneNumber = cursor.getString(cursor.getColumnIndex(Inbox.KEY_PHONE_NUMBER));
//					boolean isDeliveryOk = cursor.getInt(cursor.getColumnIndex(Inbox.KEY_DELIVERY_STATUS)) == 1;
//					long timeStamp = cursor.getLong(cursor.getColumnIndex(Inbox.KEY_TIME_STAMP));
//					String transcription = cursor.getString(cursor.getColumnIndex(Inbox.KEY_TRANSCRIPTION));
//					boolean isRead = cursor.getInt(cursor.getColumnIndex(Inbox.KEY_IS_READ)) == Message.ReadDeletedState.READ;
//					int savedStatus = cursor.getInt(cursor.getColumnIndex(Inbox.KEY_SAVED_STATE));
//					String fileName = cursor.getString(cursor.getColumnIndex(Inbox.KEY_FILE_NAME));
//					boolean isUrgent = (cursor.getInt(cursor.getColumnIndex(Inbox.KEY_URGENT_STATUS)) == 1);
//
//					VoiceMailObject voiceMailObject = new VoiceMailObject(id, uid, timeStamp, senderPhoneNumber, fileName, isRead, savedStatus, isUrgent, isDeliveryOk, transcription, null);
//					allVoiceMails.add(allVoiceMails.size(), voiceMailObject);
//					}while (cursor.moveToNext());
//				}
//
//				Logger.d(TAG, "GetAllVoiceMailsAsAggregatedAsync - done");
//			} catch ( Exception e ) {
//				Logger.e(TAG, "GetAllVoiceMailsAsAggregatedAsync.doInBackground exception=" + e.getMessage(), e);
//			} finally {
//				if ( cursor != null ) {
//					cursor.close();
//				}
//			}
//
//			for(VoiceMailObject voiceMailObject : allVoiceMails){
//
//				String phoneNumber = voiceMailObject.getPhoneNumber();
//				if(!mAggItems.contains(new AggregatedItemObject(phoneNumber, voiceMailObject.isSaved()))){
//
//					AggregatedItemObject agg = new AggregatedItemObject(phoneNumber, voiceMailObject.isSaved());
//					agg.addMessageId(voiceMailObject.getId());
//					agg.addMessageUid(voiceMailObject.getUid());
//					agg.setLastVoiceMailDate(voiceMailObject.getTimeStamp());
//					agg.incrementTotalCount();
//					if(voiceMailObject.isRead()){
//						agg.incrementReadCount();
//					}
//
//					mAggItems.add(mAggItems.size(), agg);
//
//				}else{
//
//					AggregatedItemObject agg = mAggItems.get(mAggItems.indexOf(new AggregatedItemObject(phoneNumber, voiceMailObject.isSaved())));
//					agg.incrementTotalCount();
//					agg.addMessageId(voiceMailObject.getId());
//					agg.addMessageUid(voiceMailObject.getUid());
//					if(voiceMailObject.isRead()){
//						agg.incrementReadCount();
//					}
//
//				}
//
//			}
//
//			return mAggItems;
//		}
//
//		@Override
//		protected void onPostExecute(ArrayList<AggregatedItemObject> results) {
//			super.onPostExecute(results);
//			VoiceMailAsAggregatedRequestCallback callback = mailRequestCallback.get();
//			if(callback != null) {
//				Logger.d(TAG, "GetAllVoiceMailsAsAggregatedAsync - onPostExecute - callback not null");
//				callback.onDone(results);
//			}else{
//				Logger.d(TAG, "GetAllVoiceMailsAsAggregatedAsync - onPostExecute - callback not null");
//			}
//		}
//	}
//
//	private static class GetAllVoiceMailsAsync extends AsyncTask<Void,Void,ArrayList<VoiceMailObject>>{
//
//		WeakReference<VoiceMailRequestCallback> mailRequestCallback;
//
//		public GetAllVoiceMailsAsync(VoiceMailRequestCallback callback){
//			 mailRequestCallback = new WeakReference<VoiceMailRequestCallback>(callback);
//		}
//
//		@Override
//		protected ArrayList<VoiceMailObject> doInBackground(Void... params) {
//
//			ArrayList<VoiceMailObject> results = new ArrayList<>();
//			Cursor cursor = null;
//
//			try {
//				cursor = ModelManager.getInstance().getAllMessagesFromInbox();
//
//				if (cursor == null) {
//					return results;
//				}
//
//				while (cursor.moveToNext()) {
//
//					long id = cursor.getLong(cursor.getColumnIndex(Inbox._ID));
//					long uid = cursor.getLong(cursor.getColumnIndex(Inbox.KEY_UID));
//					String senderPhoneNumber = cursor.getString(cursor.getColumnIndex(Inbox.KEY_PHONE_NUMBER));
//					boolean isDeliveryOk = cursor.getInt(cursor.getColumnIndex(Inbox.KEY_DELIVERY_STATUS)) == 1;
//					long timeStamp = cursor.getLong(cursor.getColumnIndex(Inbox.KEY_TIME_STAMP));
//					String transcription = cursor.getString(cursor.getColumnIndex(Inbox.KEY_TRANSCRIPTION));
//					boolean isRead = cursor.getInt(cursor.getColumnIndex(Inbox.KEY_IS_READ)) == Message.ReadDeletedState.READ;
//					int savedStatus = cursor.getInt(cursor.getColumnIndex(Inbox.KEY_SAVED_STATE));
//					String fileName = cursor.getString(cursor.getColumnIndex(Inbox.KEY_FILE_NAME));
//					boolean isUrgent = (cursor.getInt(cursor.getColumnIndex(Inbox.KEY_URGENT_STATUS)) == 1);
//
//					VoiceMailObject voiceMailObject = new VoiceMailObject(id, uid, timeStamp, senderPhoneNumber, fileName, isRead, savedStatus, isUrgent, isDeliveryOk, transcription, null);
//					results.add(results.size(), voiceMailObject);
//				}
//
//			} catch ( Exception e ) {
//				Logger.e(TAG, "GetAllVoiceMailsAsync.doInBackground exception=" + e.getMessage(), e);
//			} finally {
//				if ( cursor != null ) {
//					cursor.close();
//				}
//			}
//
//			return results;
//		}
//
//		@Override
//		protected void onPostExecute(ArrayList<VoiceMailObject> results) {
//			super.onPostExecute(results);
//
//			VoiceMailRequestCallback callback = mailRequestCallback.get();
//			if(callback != null) {
//				callback.onDone(results);
//			}
//		}
//	}

//	private static GetSavedVoiceMailsAsync savedVMAsync;
//
//	public static void getSavedVoiceMails(VoiceMailRequestCallback callback){
//
//		if(savedVMAsync != null && savedVMAsync.getStatus() != AsyncTask.Status.FINISHED){
//			savedVMAsync.cancel(true);
//		}
//
//		savedVMAsync = new GetSavedVoiceMailsAsync(callback);
//		savedVMAsync.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//	}
//
//	private static class GetSavedVoiceMailsAsync extends AsyncTask<Void,Void,ArrayList<VoiceMailObject>>{
//
//		WeakReference <VoiceMailRequestCallback> mailRequestCallback;
//
//		public GetSavedVoiceMailsAsync(VoiceMailRequestCallback callback){
//			mailRequestCallback = new WeakReference<VoiceMailRequestCallback>(callback);
//		}
//
//		@Override
//		protected ArrayList<VoiceMailObject> doInBackground(Void... params) {
//
//			Cursor cursor = null;
//			ArrayList<VoiceMailObject> results = new ArrayList<>();
//
//			try {
//				cursor = ModelManager.getInstance().getSavedMessagesFromInbox();
//
//				if (cursor == null) {
//					return results;
//				}
//
//				while (cursor.moveToNext()) {
//
//					long id = cursor.getLong(cursor.getColumnIndex(Inbox._ID));
//					long uid = cursor.getLong(cursor.getColumnIndex(Inbox.KEY_UID));
//					String senderPhoneNumber = cursor.getString(cursor.getColumnIndex(Inbox.KEY_PHONE_NUMBER));
//					boolean isDeliveryOk = cursor.getInt(cursor.getColumnIndex(Inbox.KEY_DELIVERY_STATUS)) == 1;
//					long timeStamp = cursor.getLong(cursor.getColumnIndex(Inbox.KEY_TIME_STAMP));
//					String transcription = cursor.getString(cursor.getColumnIndex(Inbox.KEY_TRANSCRIPTION));
//					boolean isRead = cursor.getInt(cursor.getColumnIndex(Inbox.KEY_IS_READ)) == Message.ReadDeletedState.READ;
//					int savedStatus = cursor.getInt(cursor.getColumnIndex(Inbox.KEY_SAVED_STATE));
//					String fileName = cursor.getString(cursor.getColumnIndex(Inbox.KEY_FILE_NAME));
//					boolean isUrgent = (cursor.getInt(cursor.getColumnIndex(Inbox.KEY_URGENT_STATUS)) == 1);
//
//					VoiceMailObject voiceMailObject = new VoiceMailObject(id, uid, timeStamp, senderPhoneNumber, fileName, isRead, savedStatus, isUrgent, isDeliveryOk, transcription, null);
//					results.add(results.size(), voiceMailObject);
//				}
//			} catch ( Exception e ) {
//				Logger.e(TAG, "GetSavedVoiceMailsAsync.doInBackground exception=" + e.getMessage(), e);
//			} finally {
//				if ( cursor != null ) {
//					cursor.close();
//				}
//			}
//
//			return results;
//		}
//
//		@Override
//		protected void onPostExecute(ArrayList<VoiceMailObject> results) {
//			super.onPostExecute(results);
//			VoiceMailRequestCallback callback = mailRequestCallback.get();
//			if(callback != null) {
//				callback.onDone(results);
//			}
//		}
//	}
//
//	private static getUserVoiceMails getUserVMss;
//
//	public static void getUserVoiceMails(String phoneNumber, boolean getSaved, VoiceMailRequestCallback callback){
//
//		if(getUserVMss != null && getUserVMss.getStatus() != AsyncTask.Status.FINISHED){
//			getUserVMss.cancel(true);
//		}
//
//		getUserVMss = new getUserVoiceMails(callback, getSaved);
//		getUserVMss.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, phoneNumber);
//
//	}
//
//	private static class getUserVoiceMails extends AsyncTask<String,Void,ArrayList<VoiceMailObject>>{
//
//		WeakReference <VoiceMailRequestCallback> mailRequestCallback;
//		boolean mGetSaved;
//
//		public getUserVoiceMails(VoiceMailRequestCallback callback, boolean getSaved){
//			mailRequestCallback = new WeakReference<VoiceMailRequestCallback>(callback);
//			mGetSaved= getSaved;
//		}
//
//		@Override
//		protected ArrayList<VoiceMailObject> doInBackground(String... params) {
//
//			String phoneNumber = params[0];
//			Cursor cursor = null;
//			ArrayList<VoiceMailObject> results = new ArrayList<>();
//
//			try {
//				cursor = ModelManager.getInstance().getUserMessagesFromInbox(phoneNumber, mGetSaved);
//
//				if (cursor == null) {
//					return results;
//				}
//
//				while (cursor.moveToNext()) {
//
//					long id = cursor.getLong(cursor.getColumnIndex(Inbox._ID));
//					long uid = cursor.getLong(cursor.getColumnIndex(Inbox.KEY_UID));
//					String senderPhoneNumber = cursor.getString(cursor.getColumnIndex(Inbox.KEY_PHONE_NUMBER));
//					boolean isDeliveryOk = cursor.getInt(cursor.getColumnIndex(Inbox.KEY_DELIVERY_STATUS)) == 1;
//					long timeStamp = cursor.getLong(cursor.getColumnIndex(Inbox.KEY_TIME_STAMP));
//					String transcription = cursor.getString(cursor.getColumnIndex(Inbox.KEY_TRANSCRIPTION));
//					boolean isRead = cursor.getInt(cursor.getColumnIndex(Inbox.KEY_IS_READ)) == Message.ReadDeletedState.READ;
//					int savedStatus = cursor.getInt(cursor.getColumnIndex(Inbox.KEY_SAVED_STATE));
//					String fileName = cursor.getString(cursor.getColumnIndex(Inbox.KEY_FILE_NAME));
//					boolean isUrgent = (cursor.getInt(cursor.getColumnIndex(Inbox.KEY_URGENT_STATUS)) == 1);
//
//					VoiceMailObject voiceMailObject = new VoiceMailObject(id, uid, timeStamp, senderPhoneNumber, fileName, isRead, savedStatus, isUrgent, isDeliveryOk, transcription, null);
//					results.add(results.size(), voiceMailObject);
//				}
//
//			} catch ( Exception e ) {
//				Logger.e(TAG, "doInBackground exception=" + e.getMessage(), e);
//			} finally {
//				if ( cursor != null ) {
//					cursor.close();
//				}
//			}
//			return results;
//		}
//
//		@Override
//		protected void onPostExecute(ArrayList<VoiceMailObject> results) {
//			super.onPostExecute(results);
//			VoiceMailRequestCallback callback = mailRequestCallback.get();
//			if(callback != null) {
//				callback.onDone(results);
//			}
//		}
//	}

//	/**
//	 * Gets all inbox message IDs in the same order they appears in the inbox screen, according to the given filter type
//	 * (@see MessageFilter).
//	 *
//	 * @param filterType (int) the filter type to get inbox message IDs accoding to.
//	 * @return (Cursor) a cursor contains all inbox message IDs in the same order they appears in the inbox screen,
//	 *         according to the given filter type.
//	 */
//	public synchronized Cursor getInboxMessageIDs( int filterType, String phoneNumber ) {
//
//		Logger.i(TAG, "getInboxMessageIDs filterType=" + filterType + " phoneNumber=" + phoneNumber);
//		// holds query's where clause
//		StringBuilder whereClause = new StringBuilder();
//
//		// builds query's where clause according to the given filter type
//		switch (filterType) {
//			case (MessageFilter.TYPE_ALL):
//				whereClause.append(Inbox.KEY_SAVED_STATE).append("!=").append(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED);
//				break;
//
//			case (MessageFilter.TYPE_UNREAD):
//				whereClause .append(Inbox.KEY_IS_READ).append("=").append(Message.ReadDeletedState.UNREAD);
//				break;
//
//			case (MessageFilter.TYPE_SAVED):
//				whereClause.append(Inbox.KEY_SAVED_STATE).append(" = ").append(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED);
//				break;
//			default:
//				break;
//
//		}
//
//		if ( phoneNumber != null ) {
//			if ( whereClause == null ) {
//				whereClause = new StringBuilder();
//			} else {
//				whereClause.append(" AND ");
//		}
//			whereClause.append(Inbox.KEY_PHONE_NUMBER).append("='").append(phoneNumber).append("'");
//		}
//
//		Logger.i(TAG, "getInboxMessageIDs whereClause=" + (whereClause != null ? whereClause.toString() : "null"));
//
//		return db.query(DATABASE_TABLE_INBOX, new String[] {Inbox._ID},	whereClause != null ? whereClause.toString() : null, null,	null, null, Inbox.KEY_TIME_STAMP + " desc");
//	}
//
	// ---retrieves a particular message---
	public synchronized Cursor getMessage(long messageId) throws SQLException {
		Cursor mCursor = db.query(true, DATABASE_TABLE_INBOX, new String[] {
				Inbox._ID, Inbox.KEY_UID, Inbox.KEY_TIME_STAMP,
				Inbox.KEY_PHONE_NUMBER, Inbox.KEY_TRANSCRIPTION,
				Inbox.KEY_FILE_NAME, Inbox.KEY_IS_READ, Inbox.KEY_SAVED_STATE,
						Inbox.KEY_URGENT_STATUS,
				Inbox.KEY_DELIVERY_STATUS, Inbox.KEY_WATSON_TRANSCRIPTION
		}, Inbox._ID + "=" + messageId, null,
				null, null, null, null);
		if (mCursor != null) {
			mCursor.moveToFirst();
		}

		return mCursor;
	}
	public String getSenderPhoneNumber(long messageId){
		Cursor c = null;
		String phonenumber = null;
		try{
			c = db.query(true, DATABASE_TABLE_INBOX, new String[] {
							Inbox._ID, Inbox.KEY_PHONE_NUMBER}, Inbox._ID + "=" + messageId, null,
					null, null, null, null);
			if (c != null) {
				c.moveToFirst();
				phonenumber = c.getString(c.getColumnIndex(Inbox.KEY_PHONE_NUMBER));
			}
			return phonenumber;
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
			return null;
		} finally {
			if (c != null) {
				c.close();
			}
		}
	}

	public long getMessageTimeStamp(long messageId){
		Cursor c = null;
		long timestamp = 0;
		try{
			c = db.query(true, DATABASE_TABLE_INBOX, new String[] {
							Inbox._ID, Inbox.KEY_TIME_STAMP}, Inbox._ID + "=" + messageId, null,
					null, null, null, null);
			if (c != null) {
				c.moveToFirst();
				timestamp = c.getLong(c.getColumnIndex(Inbox.KEY_TIME_STAMP));
			}
			return timestamp;
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
			return -1;
		} finally {
			if (c != null) {
				c.close();
			}
		}
	}

	public boolean isMessageSaved(long messageId) {
		Cursor cursor = null;

		try {
			// queries the database for message's saved state
			cursor = db.query(DATABASE_TABLE_INBOX,
					new String[] {
							Inbox.KEY_SAVED_STATE
					},
					new StringBuilder(Inbox._ID).append("=").append(messageId)
							.toString(), null, null, null, null);

			if (cursor != null && cursor.getCount() == 1&& cursor.moveToFirst()) {
				return cursor.getInt(cursor.getColumnIndex(Inbox.KEY_SAVED_STATE))== Message.SavedStates.INTERNAL_STORAGE_AS_SAVED;
			}

			return false;
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
			return false;
		} finally {
			// releases resources if needed
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	public Message createMessageFromCursor(Cursor cursor) {

		if (cursor != null && cursor.getCount() > 0) {
			Message message = new Message();
//			if(cursor.getColumnIndex(Inbox.KEY_IS_DELETED )!= Message.ReadDeletedState.DELETED) {
				setMessageDetails(cursor, message);
//			} else {
//						message = null;
//			}
			return message;
		}
		return null;
	}

	private void setMessageDetails(Cursor cursor, Message message) {
		message.setDateLong(cursor.getLong(cursor.getColumnIndex(Inbox.KEY_TIME_STAMP)));
		message.setFileName(cursor.getString(cursor.getColumnIndex(Inbox.KEY_FILE_NAME)));
		boolean isRead = (cursor.getInt(cursor.getColumnIndex(Inbox.KEY_IS_READ)) == Message.ReadDeletedState.READ);
		message.setRead(isRead);
		message.setSavedState(cursor.getInt(cursor.getColumnIndex(Inbox.KEY_SAVED_STATE)));
		message.setRowId(cursor.getLong(cursor.getColumnIndex(Inbox._ID)));
		message.setUid(cursor.getLong(cursor.getColumnIndex(Inbox.KEY_UID)));

		message.setUrgentStatus(cursor.getInt(cursor.getColumnIndex(Inbox.KEY_URGENT_STATUS)));
		message.setDeliveryStatus(cursor.getInt(cursor.getColumnIndex(Inbox.KEY_DELIVERY_STATUS)) == 1);

		String senderPhoneNumber = cursor.getString(cursor.getColumnIndex(Inbox.KEY_PHONE_NUMBER));

		message.setSenderPhoneNumber(senderPhoneNumber);

		// special case for delivery status messages
		if (message.isDeliveryStatus()) {
            message.setSenderDisplayName(context.getString(R.string.deliveryStatusSenderName));
        } else if (senderPhoneNumber != null && senderPhoneNumber.length() > 0) {
            message.setSenderDisplayName(senderPhoneNumber);
        } else {
            message.setSenderDisplayName(context.getString(R.string.privateNumber));
        }

		message.setTranscription(context, cursor.getString(cursor.getColumnIndex(Inbox.KEY_TRANSCRIPTION)));
	}

	public synchronized Cursor getMessageByUid(long messageUid) throws SQLException {

		return db.query(true, DATABASE_TABLE_INBOX, new String[]{
						Inbox._ID, Inbox.KEY_UID, Inbox.KEY_CAN_OVERWRITE,
						Inbox.KEY_FILE_NAME
				}, Inbox.KEY_UID + "=" + messageUid, null,
				null, null, null, null);
	}

	// ---retrieves a particular message---
	public synchronized void setMessageFileNotFound(long messageId) throws SQLException {

		Logger.d(TAG, "setMessageFileNotFound messageId=" + messageId );
		StringBuilder where = new StringBuilder(Inbox._ID).append(" = ").append(messageId);

		ContentValues contentValues = new ContentValues();
		contentValues.put(Inbox.KEY_FILE_NAME, "");
		contentValues.put(Inbox.KEY_SAVED_STATE, Message.SavedStates.ERROR);
		contentValues.put(Inbox.KEY_WATSON_TRANSCRIPTION, Message.WatsonTranscriptionState.TRANSCRIPTION_FAILED);

		try {
			db.update(DATABASE_TABLE_INBOX, contentValues, where.toString(), null);
			Logger.d(TAG, "ModelManager.getInstance() setMessageFileNotFound(" + messageId + ") Finished");
		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);
		}
	}

//	/**
//	 * Gets the total count of application's messages, according to a given filter type (@see MessageFilter).
//	 *
//	 * @param filterType (int) the filter to get the total message count according to.
//	 * @return (int) the total count of application's messages, according to a given filter type (@see MessageFilter).
//	 */
//	public synchronized int getMessagesCount( int filterType, String phoneNumber ) {
//		// holds SQL query
//		StringBuilder sqlQuery = new StringBuilder(allMessagesCountQuery);
//
//		// builds query's where clause according to the given filter type
//		switch (filterType) {
//			case (MessageFilter.TYPE_ALL): {
//				sqlQuery.append(" WHERE ").append(Inbox.KEY_SAVED_STATE).append("!=").append(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED);
//				break;
//			}
//			case (MessageFilter.TYPE_UNREAD): {
//				sqlQuery.append(" WHERE ").append(Inbox.KEY_IS_READ).append("=").append(Message.ReadDeletedState.UNREAD);
//				break;
//			}
//			case (MessageFilter.TYPE_SAVED): {
//				sqlQuery.append(" WHERE ").append(Inbox.KEY_SAVED_STATE).append(" = ").append(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED);
//				break;
//			}
//			default:
//				break;
//		}
//
//		if ( phoneNumber != null ) {
//			sqlQuery.append(" AND ");
//			sqlQuery.append(Inbox.KEY_PHONE_NUMBER).append("='").append(phoneNumber).append("'");
//		}
//		Logger.i(TAG, "getMessagesCount sqlQuery=" + sqlQuery.toString());
//
//		Cursor c = null;
//		int count = 0;
//		try {
//			c = db.rawQuery(sqlQuery.toString(), null);
//
//			if (c != null && c.moveToFirst()) {
//				count = c.getInt(0);
//			}
//			return count;
//		} catch (Exception e) {
//			Logger.e(TAG, e.getMessage(), e);
//			return 0;
//		} finally {
//			if (c != null) {
//				c.close();
//			}
//		}
//	}

	public synchronized int getMessagePosition(long messageId,int filterType, String phoneNumber) {
		// holds the SQL query
		StringBuilder sqlQuery = new StringBuilder("SELECT COUNT(*) FROM ").append(DATABASE_TABLE_INBOX).append(" WHERE ");
		boolean isGroupAndAggreg = isGroupByContact() && phoneNumber != null;
		long messageDate = getMessageTimeStamp(messageId);
		Logger.d(TAG, " getMessagePosition()  messageId = "+messageId + " filterType = "+ filterType+ " phoneNumber = "+phoneNumber+ " messageDate = "+messageDate);
		sqlQuery.append(Inbox.KEY_TIME_STAMP).append(" >= ").append(messageDate);
		StringBuilder wher = isGroupAndAggreg ? (filterType == Constants.MessageFilter.TYPE_SAVED ? new StringBuilder().append(Inbox.KEY_PHONE_NUMBER).append(" == '").append(String.valueOf(phoneNumber)).append("' AND ").append(Inbox.KEY_SAVED_STATE).append(" == ").append(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED).append(AND_NOT_DELETED) :
				 new StringBuilder().append(Inbox.KEY_PHONE_NUMBER).append(" == '").append(String.valueOf(phoneNumber)).append("' AND ").append(Inbox.KEY_SAVED_STATE).append(" != ").append(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED).append(AND_NOT_DELETED)) :
				(filterType == Constants.MessageFilter.TYPE_SAVED ? new StringBuilder().append(Inbox.KEY_SAVED_STATE).append(" == ").append(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED).append(AND_NOT_DELETED) :
						new StringBuilder().append(Inbox.KEY_SAVED_STATE).append(" != ").append(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED).append(AND_NOT_DELETED));
		sqlQuery.append(" AND ").append(wher);
		Logger.i(TAG, " getMessagePosition sqlQuery=" + sqlQuery.toString());

		Cursor c = null;
		try {
			c = db.rawQuery(sqlQuery.toString(), null);
			int count = 1;
			if (c != null && c.moveToFirst()) {
				count = c.getInt(0);
			}
			Logger.d(TAG, " getMessagePosition()  messageId = "+messageId + " filterType = "+ filterType+ " phoneNumber = "+phoneNumber+ " returns count = "+count);
			return count;
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
			return -1;
		} finally {
			// releases resources if needed
			if (c != null) {
				c.close();
			}
		}
	}

	/**
	 * a method to retrieve all messages with no audio files in order to download their file
	 * 
	 * @author istelman
	 * @param filterByTranscription TODO
	 * @return - an array of all the uids of the messages that needs audio files downloaded
	 */
	public synchronized MessageDo[] getMessagesToDownload(boolean filterByTranscription) {
		Cursor mCursor = null;
		MessageDo[] uids = null;
		try {

			StringBuilder selection = null;
			if (filterByTranscription) {				
				selection = new StringBuilder(Inbox.KEY_FILE_NAME).append(" IS NOT NULL AND ")
						.append(Inbox.KEY_FILE_NAME).append( "<>'' AND ")
						.append("trim(").append(Inbox.KEY_TRANSCRIPTION).append(")='' ");
			}
			else {				
				selection = new StringBuilder(Inbox.KEY_FILE_NAME).append(" IS NULL OR ")
						.append(Inbox.KEY_FILE_NAME).append("='' AND ")
						.append(Inbox.KEY_WAS_DOWNLOADED).append("=").append(Message.WAS_DOWNLOADED_STATE.NO);
			}

			mCursor = db.query(true, DATABASE_TABLE_INBOX, new String[] {
					Inbox._ID, Inbox.KEY_UID, Inbox.KEY_FILE_NAME
			}, selection.toString(),
					null, null, null, Inbox.KEY_TIME_STAMP + " desc", null);

			if (mCursor != null && mCursor.moveToFirst()) {
				uids = new MessageDo[mCursor.getCount()];
				int i = 0;
				while (mCursor.isAfterLast() == false) {
					uids[i] = new MessageDo();
					uids[i].setUid(mCursor.getLong(mCursor
							.getColumnIndex(Inbox.KEY_UID)));
					uids[i].setId(mCursor.getLong(mCursor
							.getColumnIndex(Inbox._ID)));
					uids[i].setFileName(mCursor.getString(mCursor
							.getColumnIndex(Inbox.KEY_FILE_NAME)));
					mCursor.moveToNext();
					i++;
				}
			}

			return uids;
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
			return uids;
		} finally {
			// releases resources if needed
			if (mCursor != null) {
				mCursor.close();
			}
		}
	}

//	/**
//	 * a method to retrieve all messages with watson transcription status WAIT_FOR_TRANSCRIPTION or RETRY
//	 *
//	 * @return - an array of all the uids of the messages that needs REQUEST WATSON TRANSCRIPTION
//	 */
//	public synchronized MessageDo[] getMessagesToTranscript() {
//		Cursor mCursor = null;
//		MessageDo[] uids = null;
//		try {
//
//			StringBuilder selection = null;
//				selection = new StringBuilder(Inbox.KEY_WATSON_TRANSCRIPTION).append(" = ").append(Message.WatsonTranscriptionState.WAIT_FOR_TRANSCRIPTION)
//						.append(" OR ").append(Inbox.KEY_WATSON_TRANSCRIPTION).append(" = ").append(Message.WatsonTranscriptionState.RETRY);
//
//			mCursor = db.query(true, DATABASE_TABLE_INBOX, new String[] {
//					Inbox._ID, Inbox.KEY_UID, Inbox.KEY_FILE_NAME, Inbox.KEY_TRANSCRIPTION
//			}, selection.toString(),
//					null, null, null, Inbox.KEY_TIME_STAMP + " desc", null);
//
//			if (mCursor != null && mCursor.moveToFirst()) {
//				uids = new MessageDo[mCursor.getCount()];
//				int i = 0;
//				while (mCursor.isAfterLast() == false) {
//					uids[i] = new MessageDo();
//					uids[i].setUid(mCursor.getLong(mCursor
//							.getColumnIndex(Inbox.KEY_UID)));
//					uids[i].setId(mCursor.getLong(mCursor
//							.getColumnIndex(Inbox._ID)));
//					uids[i].setFileName(mCursor.getString(mCursor
//							.getColumnIndex(Inbox.KEY_FILE_NAME)));
//					Logger.d(TAG, "ModelManager.getMessagesToTranscript() added to array message uid = "+mCursor.getLong(mCursor
//							.getColumnIndex(Inbox.KEY_UID)) +" message id = "+ mCursor.getLong(mCursor
//									.getColumnIndex(Inbox._ID))+ " fileName = "+mCursor.getString(mCursor
//											.getColumnIndex(Inbox.KEY_FILE_NAME)));
//					mCursor.moveToNext();
//					i++;
//				}
//			}
//
//			return uids;
//		} catch (Exception e) {
//			Logger.e(TAG, e.getMessage(), e);
//			return uids;
//		} finally {
//			// releases resources if needed
//			if (mCursor != null) {
//				mCursor.close();
//			}
//		}
//	}

	/**
	 * get all messages that was not marked as tuiskipped in the server
	 * 
	 * @return
	 */
	public synchronized MessageDo[] getUnSkippedMessages() {

		Cursor mCursor = null;
		MessageDo[] uids = null;
		try {

			String query = new StringBuilder("select _id, uid from ")
					.append(DATABASE_TABLE_INBOX).append(" where ")
					.append(Inbox.KEY_IS_TUISKIPPED).append(" = 0")
					.append(" AND ").append(Inbox.KEY_UID).append(" < ")
					.append(Constants.WELCOME_MESSAGE_ID).toString();

			mCursor = db.rawQuery(query, null);

			long id;
			long uid;

			if (mCursor != null && mCursor.moveToFirst()) {
				uids = new MessageDo[mCursor.getCount()];
				int i = 0;
				while (mCursor.isAfterLast() == false) {
					id = mCursor.getLong(mCursor.getColumnIndex(Inbox._ID));
					uid = mCursor
							.getLong(mCursor.getColumnIndex(Inbox.KEY_UID));
					uids[i] = new MessageDo();
					uids[i].setUid(uid);
					uids[i].setId(id);
					mCursor.moveToNext();
					i++;
				}
			}
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
		} finally {
			// releases resources if needed
			if (mCursor != null) {
				mCursor.close();
			}
		}
		return uids;
	}

	/**
	 * update all messages in DB that tuiskipped flag was successfully set for them in the server
	 * 
	 * @param tuiskippedUIs
	 */
	public synchronized void updateTuiskipped(Set<Long> tuiskippedUIs) {

		if (tuiskippedUIs != null && !tuiskippedUIs.isEmpty()) {
			StringBuilder where = new StringBuilder();
			Iterator<Long> it = tuiskippedUIs.iterator();
			while (it.hasNext()) {
				where.append(Inbox.KEY_UID).append(" = ").append(it.next());
				if (it.hasNext()) {
					where.append(" OR ");
				}
			}
			ContentValues contentValues = new ContentValues();
			contentValues.put(Inbox.KEY_IS_TUISKIPPED, 1);
			try {
				db.update(DATABASE_TABLE_INBOX, contentValues,
						where.toString(), null);
			} catch (SQLiteException e) {
				Logger.e(TAG, e.getMessage(), e);
			}
		}
	}

	/**
	 * @author istelman
	 * @return the number of unread messages
	 */
	public synchronized int getNewMessagesCount() {
		Cursor c = null;
		try {
			c = db.rawQuery("select count(*) from " + DATABASE_TABLE_INBOX
					+ " where " + Inbox.KEY_IS_READ + "="
					+ Message.ReadDeletedState.UNREAD, null);
			int count = 1;
			if (c != null && c.moveToFirst()) {
				count = c.getInt(0);
			}

			return count;
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
			return 0;
		} finally {
			// releases resources if needed
			if (c != null) {
				c.close();
			}
		}
	}

	/**
	 * @return
	 */
	public synchronized Cursor getLastUnreadMessage() {

		String where = new StringBuilder(Inbox.KEY_IS_READ).append("=")
				.append(Message.ReadDeletedState.UNREAD).append(" AND ")
				.append(Inbox.KEY_TIME_STAMP).append("=(SELECT MAX(")
				.append(Inbox.KEY_TIME_STAMP).append(") FROM ")
				.append(DATABASE_TABLE_INBOX).append(" WHERE ")
				.append(Inbox.KEY_IS_READ).append("=")
				.append(Message.ReadDeletedState.UNREAD).append(")").toString();

		/*
		 * filter the welcome was canceled .append(" AND ") .append(Inbox.KEY_PHONE_NUMBER).append(" != '")
		 * .append(context.getString (R.string.welcomeMessagePhoneNumber)).append("'")
		 */
		Cursor c = null;
		try {
			c = db.query(true, DATABASE_TABLE_INBOX, new String[] {
					Inbox._ID,
					Inbox.KEY_UID, Inbox.KEY_TIME_STAMP,
					Inbox.KEY_PHONE_NUMBER, Inbox.KEY_TRANSCRIPTION,
					Inbox.KEY_FILE_NAME, Inbox.KEY_IS_READ,
					Inbox.KEY_SAVED_STATE, Inbox.KEY_IS_DELETED,
					Inbox.KEY_DELIVERY_STATUS
			}, where, null, null, null, null,
					null);

		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);
		}
		if (c != null) {
			c.moveToFirst();
		}
		return c;
	}

//	/**
//	 * @author istelman
//	 * @return - the number of saved messages
//	 */
//	public synchronized int getSavedMessagesCount() {
//		Cursor c = null;
//		int count = 1;
//		try {
//			String query = new StringBuilder("select count(*) from ")
//					.append(DATABASE_TABLE_INBOX).append(" where ")
//					.append(Inbox.KEY_SAVED_STATE).append(" = ")
//					.append(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED)
//					.toString();
//
//			c = db.rawQuery(query, null);
//			count = 0;
//			if (c != null && c.moveToFirst()) {
//				count = c.getInt(0);
//			}
//		} catch (Exception e) {
//			Logger.e(TAG, e.getMessage(), e);
//			return 0;
//		} finally {
//			// releases resources if needed
//			if (c != null) {
//				c.close();
//			}
//		}
//		return count;
//	}

	/**
	 * Gets the file name for a message with a given message ID.
	 * 
	 * @param messageID (long) the message ID.
	 * @return (String) the file name for the message with the given ID, or null in case the message or its file doesn't
	 *         exist.
	 */
	public synchronized String getMessageFileName(long messageID) {
		Cursor cursor = null;

		try {
			// queries the database for message's file name
			cursor = db.query(DATABASE_TABLE_INBOX,
					new String[] {
						Inbox.KEY_FILE_NAME
					},
					new StringBuilder(Inbox._ID).append("=").append(messageID)
							.toString(), null, null, null, null);

			// in case file
			if (cursor != null && cursor.getCount() == 1
					&& cursor.moveToFirst()) {
				// returns the file name for the message with the given ID
				String fileName = cursor.getString(cursor
						.getColumnIndex(Inbox.KEY_FILE_NAME));
				if (fileName != null && fileName.length() > 0) {
					return fileName;
				}
			}

			return null;
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
			return null;
		} finally {
			// releases resources if needed
			if (cursor != null) {
				cursor.close();
			}
		}
	}
	/**
	 * Gets the UID for a message with a given message ID.
	 * 
	 * @param messageID (long) the message ID.
	 * @return (long) the UID for the message with the given ID.
	 */
	public synchronized long getMessageUID(long messageID) {
		Cursor cursor = null;

		try {
			// queries the database for message's UID
			cursor = db.query(DATABASE_TABLE_INBOX,
					new String[] {Inbox.KEY_UID},
					new StringBuilder(Inbox._ID).append("=").append(messageID)
							.toString(), null, null, null, null);

			if (cursor != null && cursor.getCount() == 1
					&& cursor.moveToFirst()) {
				// returns the file name for the message with the given ID
				long uid = cursor.getLong(cursor
						.getColumnIndex(Inbox.KEY_UID));
					return uid;
			}

			return -1;
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
			return -1;
		} finally {
			// releases resources if needed
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	/**
	 * Gets the transcription for a message with a given message ID.
	 * 
	 * @param messageID (long) the message ID.
	 * @return (String) the transcription for the message with the given ID, or null in case the message doesn't
	 *         exist.
	 */
	public synchronized String getMessageTranscription(long messageID) {
		Cursor cursor = null;

		try {
			// queries the database for message's transcription
			cursor = db.query(DATABASE_TABLE_INBOX,
					new String[] {
						Inbox.KEY_TRANSCRIPTION
					},
					new StringBuilder(Inbox._ID).append("=").append(messageID)
							.toString(), null, null, null, null);

			if (cursor != null && cursor.getCount() == 1
					&& cursor.moveToFirst()) {
				// returns the transcription for the message with the given ID
				String tirgum = cursor.getString(cursor
						.getColumnIndex(Inbox.KEY_TRANSCRIPTION));
					return tirgum;
			}

			return null;
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
			return null;
		} finally {
			// releases resources if needed
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	public boolean isMessageHasTranscription(long messageID){
		String transcription = getMessageTranscription(messageID);
		return  isTranscriptionNotEmpty(transcription);
	}

	public boolean isTranscriptionNotEmpty(String transcription){
		return  !(TextUtils.isEmpty(transcription) || transcription.equals(ModelManager.NO_TRANSCRIPTION_STRING)/* || 
		(transcription.startsWith(TranscriptionFixes.NO_TRANSCRIPTION_PREFIX) && transcription.endsWith(TranscriptionFixes.NO_TRANSCRIPTION_POSTFIX))
		|| (transcription.startsWith(TranscriptionFixes.NO_TRANSCRIPTION_PREFIX_1) && transcription.trim().endsWith(TranscriptionFixes.NO_TRANSCRIPTION_POSTFIX_1))
		|| transcription.equals(context.getString(R.string.trascriptionErrorText))*/);
	}

	public void saveMetadata(HashMap<String, String> map) {
		if (map != null) {
			this.metadataHashMap = map;
		}
	}

	public HashMap<String, String> getMetadata() {
		return this.metadataHashMap;
	}

	// ////////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Delete messages which are no longer exist in the server, from the application. <br/>
	 * The method performs 2 main actions: <br/>
	 * <br/>
	 * 01. Deletes the attachments of ALL messages which no longer exists in the server, from application INTERNAL
	 * storage.<br/>
	 * 02. Deletes the actual message recrods from application database, ONLY for messages which no longer exist in the
	 * server, and were also NOT marked as saved by the user in the application.
	 * 
	 * @param serverExistingMessageIDs (ArrayList<Long> != null) the IDs of all messages which currently exist in the
	 *            server.
	 * @return (int) the number of deleted messages from the application (messages which both their attachment was
	 *         deleted from application's internal storage, and their record was deleted from application's database).
	 */
	public synchronized int deleteMessagesNotOnServer( ArrayList<Long> serverExistingMessageIDs) {
		// holds server's existing message IDs
		StringBuilder serverExistingMessageIDsSB = new StringBuilder();

		serverExistingMessageIDsSB.append("" + Constants.WELCOME_MESSAGE_ID);

		// in case there are still some messages at the server
		if (!serverExistingMessageIDs.isEmpty()) {
			serverExistingMessageIDsSB.append(",");
			// creates server existing message IDs string (comma separated IDs)
			serverExistingMessageIDsSB = new StringBuilder();
			int numberOfServerExistingMessages = serverExistingMessageIDs.size();
			for (int position = 0; position < numberOfServerExistingMessages; ++position) {
				serverExistingMessageIDsSB.append(serverExistingMessageIDs.get(position)).append(",");
			}
			serverExistingMessageIDsSB.deleteCharAt(serverExistingMessageIDsSB.length() - 1);
		}

		// holds SQL statment where clause to be applied on all CURRENT USER
		// messages which do not exist anymore in the server,
		// (other users messages which doens't have a file yet don't matter
		// since they where deleted when user changed SIM card)
		String username = ModelManager.getInstance().getSharedPreferenceValue(
				Constants.KEYS.PREFERENCE_MAILBOX_NUMBER, String.class, null);
		StringBuilder whereClauseSB = new StringBuilder(Inbox.KEY_FILE_NAME)
				.append(" LIKE '").append(username).append("%' AND ")
				.append(Inbox.KEY_UID).append(" NOT IN (")
				.append(serverExistingMessageIDsSB.toString()).append(")");

		// holds the IDs and file names of all messages which should be deleted
		// from the application
		// (server non-existing messages which were not marked as saved or were
		// forwarded by the user in the application)
		ArrayList<Long> messageIDsToDeleteArrayList = null;
		ArrayList<String> messageFileNamesToDeleteArrayList = null;

		// holds the IDs of all messages which their saved state should be
		// update in application's database
		// (IDs pf server non-existing messages which were marked as saved by
		// the user in the application)
		ArrayList<Long> messageIDsToUpdateSavedStateArrayList = null;

		// holds the details of all messages which exists in the application but
		// do not exist anymore in the server
		Cursor serverNonExistingMessageDetails = null;
		try {
			// gets the details of all messages which exists in the application
			// but do not exist anymore in the server
			serverNonExistingMessageDetails = db.query(DATABASE_TABLE_INBOX, new String[] {	Inbox._ID, Inbox.KEY_SAVED_STATE,Inbox.KEY_FILE_NAME }, whereClauseSB.toString(), null, null, null, null);

			// in case there is at least one message which exists in the
			// application and doesn't exist in the server anymore
			if (serverNonExistingMessageDetails != null	&& serverNonExistingMessageDetails.moveToFirst()) {
				// creates the IDs and attachment file names collections
				int numberOfServerNonExistingMessages = serverNonExistingMessageDetails.getCount();
				messageIDsToDeleteArrayList = new ArrayList<Long>(numberOfServerNonExistingMessages);
				messageFileNamesToDeleteArrayList = new ArrayList<String>(numberOfServerNonExistingMessages);
				messageIDsToUpdateSavedStateArrayList = new ArrayList<Long>(numberOfServerNonExistingMessages);

				// traverses over server non-existing message details
				do {
					// gets current message ID
					long messageID = serverNonExistingMessageDetails.getLong(serverNonExistingMessageDetails.getColumnIndex(Inbox._ID));

					// gets current message saved state
					int messageSavedState = serverNonExistingMessageDetails.getInt(serverNonExistingMessageDetails.getColumnIndex(Inbox.KEY_SAVED_STATE));

					// in case the current message was marked as saved by the
					// user
					if (messageSavedState == Message.SavedStates.INTERNAL_STORAGE_AS_SAVED) {
						// adds the message ID to the message IDs to update
						// their saved state collection
						messageIDsToUpdateSavedStateArrayList.add(messageID);
					} else {
						// adds the message ID to the message IDs to delete
						// collection
						messageIDsToDeleteArrayList.add(messageID);

						// adds the message file name to the message file names
						// to delete collection
						String fileName = serverNonExistingMessageDetails.getString(serverNonExistingMessageDetails.getColumnIndex(Inbox.KEY_FILE_NAME));
						messageFileNamesToDeleteArrayList.add(fileName);
					}
				} while (serverNonExistingMessageDetails.moveToNext() && !serverNonExistingMessageDetails.isAfterLast());
			} else {
				// no messages should be deleted from the application
				return 0;
			}
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
		} finally {
			// releases resources if needed
			if (serverNonExistingMessageDetails != null) {
				serverNonExistingMessageDetails.close();
			}
		}

		// in case all application's messages exists on server, do nothing
		if ((messageIDsToUpdateSavedStateArrayList == null || messageIDsToUpdateSavedStateArrayList.isEmpty())
				&& (messageIDsToDeleteArrayList == null ||  messageIDsToDeleteArrayList.isEmpty())) {
			Logger.d( TAG, "ModelManager.delteMessagesNotOnServer() - all application's messages exist on server");
			return 0;
		}

		// deletes attachments for all server non-existing message which were
		// not marked as saved or were forwarded by the user
		int numberOfDeletedRecords = 0;
//		int numberOfIDs = messageIDsToDeleteArrayList.size(); // could be messageFileNamesToDeleteArrayList, they have the same size
//		if (numberOfIDs > 0) {
//			String[] internalAttachmentsToDelete = new String[numberOfIDs];
//			messageFileNamesToDeleteArrayList.toArray(internalAttachmentsToDelete);
//
//			VvmFileUtils.deleteInternalFiles(context, internalAttachmentsToDelete);
//
//			// holds all server non-existing message IDs which were not marked
//			// as saved or were forwarded by the user
//			StringBuilder messageIDsToDeleteSB = new StringBuilder();
//			long[] messageIDsToDelete = new long[numberOfIDs];
//			for (int position = 0; position < numberOfIDs; ++position) {
//				messageIDsToDelete[position] = messageIDsToDeleteArrayList.get(position);
//				messageIDsToDeleteSB.append(messageIDsToDelete[position]).append(",");
//			}
//			messageIDsToDeleteSB.deleteCharAt(messageIDsToDeleteSB.length() - 1);
//
//			// holds SQL statment where clause to be applied on all messages
//			// which should be deleted
//			whereClauseSB = new StringBuilder(Inbox._ID).append(" IN (").append(messageIDsToDeleteSB.toString()).append(")");
//
//			// deletes all server non-existing message which were not marked as
//			// saved or were forwarded by the user from application's database
//			try {
//				numberOfDeletedRecords = db.delete(DATABASE_TABLE_INBOX, whereClauseSB.toString(), null);
//			} catch (SQLiteException e) {
//				Logger.e(TAG, e.getMessage(), e);
//			}
//
//			if (numberOfDeletedRecords > 0) {
//				Logger.d(TAG, "ModelManager.deleteMessagesNotOnServer() notifyListeners EVENTS.DELETE_FINISHED");
//				notifyListeners(EVENTS.DELETE_FINISHED, messageIDsToDeleteArrayList);
//			}

//			Logger.d(TAG, "ModelManager.deleteMessagesNotOnServer() - " + numberOfDeletedRecords + " massages deletd.");
//		}

//		if (messageIDsToUpdateSavedStateArrayList.size() > 0) {
//			setMessagesSavedState(messageIDsToUpdateSavedStateArrayList.toArray(new Long[messageIDsToUpdateSavedStateArrayList.size()]), Message.SavedStates.INTERNAL_STORAGE_AS_SAVED);
//		}
		updateWidgets();

		// returns the number of deleted messages from application's database
		return numberOfDeletedRecords;
	}

//	/**
//	 * Deletes a single message from the application, permanently (including its file, even if it is being stored in
//	 * external storage - message is a saved message).
//	 *
//	 * @param messageID (long) the ID of the message to delete.
//	 * @return (boolean) true in case the message deleted, false otherwise.
//	 */
//	public synchronized boolean deleteMessagePermanently(long messageID) {
//		// deletes message's file from internal and external storage (it might
//		// not exists in the external storage)
//		// TODO - Royi - what if file wasn't deleted ?
//		VvmFileUtils.deleteInternalFile(context, getMessageFileName(messageID));
//		VvmFileUtils.deleteExternalFile(context, getMessageFileName(messageID));
//
//		// deletes message's record from application's database
//		try {
//			if (db.delete(DATABASE_TABLE_INBOX, new StringBuilder(Inbox._ID).append("=").append(messageID).toString(), null) == 1) {
//				ArrayList<Long> messageIds = new ArrayList<Long>();
//				messageIds.add(messageID);
//				notifyListeners(EVENTS.DELETE_FINISHED, messageIds);
//				Logger.d(TAG, "ModelManager.deleteMessageFromInbox() - message with ID " + messageID + " deleted");
//				updateWidgets();
//				return true;
//			}
//		} catch (SQLiteException e) {
//			Logger.e(TAG, e.getMessage(), e);
//		}
//
//		return false;
//	}

	/**
	 * Deletes the specified messages array from the application, permanently (including its file, even if it is being
	 * stored in external storage - message is a saved message).
	 * 
	 * @param messageDos
	 * @return
	 */
	public synchronized boolean deleteMessageFromDB(MessageDo[] messageDos) {
		if (messageDos.length == 0)
			return false;
		// deletes message's file from internal and external storage (it might
		// not exists in the external storage)
		StringBuilder whereBuilder = new StringBuilder();
		ArrayList<Long> messageIds = new ArrayList<Long>();
		for (int i = 0; i < messageDos.length; i++) {
			VvmFileUtils.deleteInternalFile(context,
					getMessageFileName(messageDos[i].getId()));
			VvmFileUtils.deleteExternalFile(context,
					getMessageFileName(messageDos[i].getId()));
			whereBuilder.append(Inbox._ID + "=" + messageDos[i].getId()
					+ " OR ");
			messageIds.add(messageDos[i].getId());
		}

		String where = whereBuilder.toString();
		// cat the last " OR "

		where = where.substring(0, where.length() - 4);

		// deletes message's record from application's database
		int numOfDeleted = 0;
		try {
			numOfDeleted = db.delete(DATABASE_TABLE_INBOX, where, null);
		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);
			return false;
		}
		if (numOfDeleted > 0) {
			notifyListeners(EVENTS.DELETE_FINISHED, messageIds);
			Logger.d(TAG,
					"ModelManager.deleteMessageFromInbox() - message with IDs "
							+ messageIds.toString() + " deleted");
			updateWidgets();
			return true;
		}

		return false;
	}

//	/**
//	 * delete All Message Permanently
//	 *
//	 * @return
//	 */
//	public synchronized boolean deleteAllMessageFromDB() {
//		// deletes message's file from internal and external storage (it might
//		// not exists in the external storage)
//		// deletes message's record from application's database
//		try {
//			if (db.delete(DATABASE_TABLE_INBOX, null, null) == 1) {
//				ArrayList<Long> messageIds = new ArrayList<Long>();
//				Logger.d(TAG, "ModelManager.deleteAllMessageFromDB() notifyListeners EVENTS.DELETE_FINISHED");
//				notifyListeners(EVENTS.DELETE_FINISHED, messageIds);
//				updateWidgets();
//			return true;
//			}
//		} catch (SQLiteException e) {
//			Logger.e(TAG, e.getMessage(), e);
//			return false;
//		}
//
//		return false;
//	}

	/**
	 * delete messages that were not saved locally on our app, but are constantly synced with the server
	 * 
	 * @return the delete messages MessageDo array
	 */
	public MessageDo[] deleteUnsavedMessages(boolean deleteWelcomeMessage) {
		MessageDo[] messageDos = getAllMessages(false, deleteWelcomeMessage);

		if (messageDos.length > 0) {

			deleteMessagesFiles(messageDos);
			// deletes the message from the application data base
			deleteMessageFromDB(messageDos);
			// go update notification since new messages num may have been
			// changed
			((VVMApplication) (context.getApplicationContext()))
					.updateNotification();
			Logger.d(TAG, "ModelManager.deleteUnsavedMessages() notifyListeners EVENTS.DELETE_FINISHED");
			notifyListeners(EVENTS.DELETE_FINISHED, null);
		}


		return messageDos;
	}

//	/**
//	 * delete all messages including ones that were saved locally on our app and ones that are synced with the server
//	 *
//	 * @return the delete messages MessageDo array
//	 */
//	public MessageDo[] deleteAllMessages() {
//		MessageDo[] messageDos = getAllMessages(true, true);
//
//		if (messageDos.length > 0) {
//
//			deleteMessagesFiles(messageDos);
//			// deletes the message from the application data base
//			deleteAllMessageFromDB();
//			// go update notification since new messages num may have been
//			// changed
//			((VVMApplication) (context.getApplicationContext()))
//					.updateNotification();
//			Logger.d(TAG, "ModelManager.deleteAllMessages() notifyListeners EVENTS.DELETE_FINISHED");
//			notifyListeners(EVENTS.DELETE_FINISHED, null);
//		}
//
//
//		return messageDos;
//	}
//	/**
//	 * delete  messages
//	 *
//	 */
//	public void deleteMessages(MessageDo[] messageDos) {
//
//		if (messageDos.length > 0) {
//
//			deleteMessagesFiles(messageDos);
//			// deletes the message from the application data base
//			deleteMessageFromDB(messageDos);
//			// go update notification since new messages num may have been
//			// changed
//			((VVMApplication) (context.getApplicationContext()))
//					.updateNotification();
//			Logger.d(TAG, "ModelManager.deleteMessages() notifyListeners EVENTS.DELETE_FINISHED");
//			notifyListeners(EVENTS.DELETE_FINISHED, null);
//		}
//	}

	/**
	 * Delete the internal files and the external files that specified in the specified InboxDo array. And then notify
	 * <b>EVENTS.DELETE_FINISHED</b>
	 * 
	 * @param messageDos
	 */
	private void deleteMessagesFiles(MessageDo[] messageDos) {
		// deletes all messages' files from internal & external storage
		for (MessageDo messageDo : messageDos) {
			// Delete internal files
			VvmFileUtils.deleteInternalFile(context, messageDo.getFileName());

			// Delete external files
			VvmFileUtils.deleteExternalFile(context, messageDo.getFileName());
		}
	}

	/**
	 * get all message from inbox is saved is true it return the saved message either.
	 * 
	 * @param saved if true it return the saved message either
	 * @return array of InboxDo if there is no messages it return InboxDo array length 0;
	 */
	public synchronized MessageDo[] getAllMessages(boolean saved, boolean getWelcomeMessage) {
		Cursor queryResults = null;
		try {
			String whereClause = null;
			// if saved is null the where clause is null
			// if we want the welcome message include - don't add any filter to
			// the where
			if (getWelcomeMessage) {
				whereClause = saved ? null : Inbox.KEY_SAVED_STATE + " != "
						+ Message.SavedStates.INTERNAL_STORAGE_AS_SAVED;
			} else {
				// if saved is null the where clause is null
				// if we want the welcome message excluded - filter it in the
				// query
				whereClause = saved ? "" : Inbox.KEY_SAVED_STATE + " != "
						+ Message.SavedStates.INTERNAL_STORAGE_AS_SAVED
						+ " AND " + Inbox.KEY_UID + " != "
						+ Constants.WELCOME_MESSAGE_ID;
			}
			// gets the file names of all existing messages in the application
			queryResults = db.query(DATABASE_TABLE_INBOX, new String[] {
					Inbox.KEY_UID, Inbox._ID, Inbox.KEY_TIME_STAMP, Inbox.KEY_PHONE_NUMBER, Inbox.KEY_FILE_NAME, Inbox.KEY_IS_READ, Inbox.KEY_TRANSCRIPTION
			},
					whereClause, null, null, null, Inbox.KEY_TIME_STAMP + " desc");
			if (queryResults == null) {
				Logger.d(
						TAG,
						"ModelManager.deleteAllMessages() - couldn't delete all messages in the application");

				return new MessageDo[0];
			}

			int numberOfExistingMessages = queryResults.getCount();
			if (numberOfExistingMessages > 0 && queryResults.moveToFirst()) {
				MessageDo[] messageDos = new MessageDo[numberOfExistingMessages];
				int position = 0;
				do {
					messageDos[position] = new MessageDo();
					messageDos[position].setFileName(queryResults
							.getString(queryResults
									.getColumnIndex(Inbox.KEY_FILE_NAME)));
					messageDos[position].setId(queryResults
							.getLong(queryResults.getColumnIndex(Inbox._ID)));
					messageDos[position].setPhoneNumber(queryResults.getString(queryResults.getColumnIndex(Inbox.KEY_PHONE_NUMBER)));
					messageDos[position].setReadState(queryResults.getString(queryResults.getColumnIndex(Inbox.KEY_IS_READ)));
					messageDos[position].setTime(queryResults.getString(queryResults.getColumnIndex(Inbox.KEY_TIME_STAMP)));
					messageDos[position].setTranscription(queryResults.getString(queryResults.getColumnIndex(Inbox.KEY_TRANSCRIPTION)));
					messageDos[position++]
							.setUid(queryResults.getLong(queryResults
									.getColumnIndex(Inbox.KEY_UID)));
					

				} while (queryResults.moveToNext()
						&& !queryResults.isAfterLast());

				return messageDos;
			}
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
		} finally {
			// releases resources if needed
			if (queryResults != null) {
				queryResults.close();
			}
		}

		return new MessageDo[0];
	}

	/**
	 * /** Sets a specific value into the shared preferences of the application. The value should be in the form of:
	 * [key],[value]
	 * 
	 * @author mkoltnuk
	 * @param key is the key name to which the value will belong
	 * @param value the value to store
	 */
	public synchronized <T extends Object> void setSharedPreference(String key,
			T value) {
		prefsEditor = prefs.edit();

		if (value instanceof Boolean) {
			prefsEditor.putBoolean(key, (Boolean) value);
		} else if (value instanceof Integer) {
			prefsEditor.putInt(key, (Integer) value);
		} else if (value instanceof Float) {
			prefsEditor.putFloat(key, (Float) value);
		} else if (value instanceof Long) {
			prefsEditor.putLong(key, (Long) value);
		} else if (value instanceof String) {
			prefsEditor.putString(key, (String) value);
		} else {
			Logger.d(
					TAG,
					"ModelManager.setSharedPreference() - [key: "
							+ key
							+ "] [value: "
							+ value
							+ " of type "
							+ value
							+ "]. T can be the one of the following types: Boolean, Integer, Float, Long, String.");
		}

		// commit changes
		prefsEditor.commit();

		// release the editor
		prefsEditor = null;
	}

//	/**
//	 * @param <T>
//	 * @param key
//	 */
//	public synchronized <T extends Object> void removeSharedPreference(String key) {
//		prefsEditor = prefs.edit();
//		prefsEditor.remove(key);
//		// commit changes
//		prefsEditor.commit();
//		// release the editor
//		prefsEditor = null;
//	}

	/**
	 * Gets the value of a specific preference
	 * 
	 * @author mkoltnuk
	 * @param <T>
	 * @param key is the name of the preference to look for in the shared preferences
	 * @param type is the type to which the value will get the cast to
	 * @param defaultValue is the default value to return if such key not found
	 * @return requested value of a specific key
	 */
	public synchronized <T> T getSharedPreferenceValue(String key,
			Class<T> type, T defaultValue) {
		try {
			Object val = prefs.getAll().get(key);
			// key not found - go look in the default shared preferences
			if (val == null) {
				val = PreferenceManager.getDefaultSharedPreferences(context)
						.getAll().get(key);
			}
			T res = type.cast(val);

			return res == null ? defaultValue : res;
		} catch (Exception cce) {
			Logger.e(TAG,
					"ModelManager.getSharedPreferenceValue() exception", cce);
			return defaultValue;
		}
	}

//	/**
//	 * Gets a value that indicates if AutoSaveMessage is On/Off
//	 *
//	 * @author mkoltnuk
//	 * @return
//	 */
//	public synchronized Boolean isAutoSaveNewMessage() {
//		return getSharedPreferenceValue(
//				this.context.getString(R.string.pref_Autosave_Toggle_key),
//				Boolean.class, true);
//	}
//


 	public synchronized boolean getClientSideTranscription(){
		return (boolean) getSharedPreferenceValue(this.context.getString(R.string.pref_Transcription_Toggle_key), Boolean.class, false);
	}

	public synchronized boolean getProximitySwitcher(){
		return (boolean) getSharedPreferenceValue(this.context.getString(R.string.pref_Proximity_Toggle_key), Boolean.class, true);
	}

	/**
	 * Gets a value indicating if the application should notify the user about new messages.
	 * 
	 * @return
	 */
	public  Boolean isNotifyOnNewMessagesEnabled() {
		return getSharedPreferenceValue(
				this.context.getString(R.string.pref_Notifications_Toggle_key),
				Boolean.class, true);
	}

	public  Boolean isGroupByContact() {
		return getSharedPreferenceValue(
				this.context.getString(R.string.pref_GroupByContact_key),
				Boolean.class, false); 
	}


	/**
	 * Checks if there were notifications before the reboot
	 * 
	 * @return
	 */
	public synchronized Boolean hadNotificationsBeforeReboot() {
		return getSharedPreferenceValue(
				KEYS.DID_NOTIFICATIONS_EXIST_BEFORE_REBOOT, Boolean.class, null);
	}

	/**
	 * Sets a value indicating if to notify the user on new messages
	 * 
	 * @param haveNotifications
	 */
	public synchronized void setNotificationsBeforeReboot(
			Boolean haveNotifications) {
		setSharedPreference(KEYS.DID_NOTIFICATIONS_EXIST_BEFORE_REBOOT,
				haveNotifications);
	}

//	/**
//	 * Checks if the user got the provisioning SMS
//	 *
//	 * @return
//	 */
//	public synchronized Boolean isUserIdentified() {
//		// get values
//		String username = getSharedPreferenceValue(
//				Constants.KEYS.PREFERENCE_MAILBOX_NUMBER, String.class, null);
//		if (username == null) {
//			Logger.d(TAG,
//					"ModelManager.isUserIdentified - [UNIDENTIFIED USER!]");
//			return false;
//		} else {
//			Logger.d(TAG,
//					"ModelManager.isUserIdentified - [USER HAS BEEN IDENTIFIED]");
//			return true;
//		}
//	}

	/**
	 * Checks if the user must change his password. This will usually happen when a SMS with S=R will arrive (admin
	 * reset) or on first time use.
	 */
	public synchronized Integer getPasswordChangeRequiredStatus() {
		Integer ans = getSharedPreferenceValue(
				KEYS.PASSWORD_CHANGE_REQUIRED_STATUS, Integer.class,
				PasswordChangeRequiredStatus.NONE);

		Logger.d(TAG,
				"ModelManager.getPasswordChangeRequiredStatus() " + ans);

		return ans;
	}

	/**
	 * Gets a value that indicates if this is the first time use of application
	 * 
	 * @return True if this is the first time, False otherwise
	 */
	public synchronized Boolean isFirstTimeUse() {
		// get the value indicating is this the first time use.
		// default value is set to true, because when this method is called for
		// the first time
		// it won't have a value and default will be returned
		return getSharedPreferenceValue(KEYS.IS_FIRST_USE, Boolean.class, true);
	}

	/**
	 * Sets a boolean value into IS_FIRST_TIME use preference
	 * 
	 * @param firstTime Boolean value indicating if this is the first time use
	 */
	public synchronized void setFirstTimeUse(Boolean firstTime) {
		setSharedPreference(KEYS.IS_FIRST_USE, firstTime);
		Logger.d(TAG, "First time use flag was set to: " + firstTime);
	}
	/**
	 * Gets a value that indicates if inbox was refreshed on startup
	 * 
	 * @return True if inbox  was already refreshed
	 */
	public synchronized Boolean wasMailboxRefreshedOnStartup() {
		return getSharedPreferenceValue(KEYS.WAS_INBOX_REFRESHED, Boolean.class, false);
	}

	/**
	 * Sets a boolean value into WAS_INBOX_REFRESHED use preference
	 * 
	 * @param inboxRefreshed Boolean value indicating if inbox was refreshed on startup
	 */
	public synchronized void setInboxRefreshed(Boolean inboxRefreshed) {
		setSharedPreference(KEYS.WAS_INBOX_REFRESHED, inboxRefreshed);
		Logger.d(TAG, "inbox refreshed was set to: " + inboxRefreshed);
	}

	public synchronized void setMailBoxStatus(String status) {
		setSharedPreference(KEYS.PREFERENCE_MAILBOX_STATUS, status);
		Logger.d(TAG, "Mailbox status was set to: "	+ status);
	}
	
	public synchronized String getMailBoxStatus() {
		return getSharedPreferenceValue(KEYS.PREFERENCE_MAILBOX_STATUS, String.class, "U");
	}
	
	public synchronized int getCurrentSetupState() {
		int currentState = getSharedPreferenceValue(KEYS.CURRENT_SETUP_STATE, Integer.class, Constants.SETUP_STATUS.UNKNOWN);
		return currentState /*getSharedPreferenceValue(KEYS.CURRENT_SETUP_STATE, Integer.class, Constants.SETUP_STATUS.UNKNOWN)*/;
	}

	public synchronized void setCurrentSetupState(int mCurrentState) {
		setSharedPreference(KEYS.CURRENT_SETUP_STATE, mCurrentState);
	}


	/**
	 * Gets a value that indicates if application setup was already started
	 * 
	 * @return True if application setup was already started
	 */
	public synchronized Boolean isSetupStarted() {
		return getSharedPreferenceValue(KEYS.IS_SETUP_STARTED, Boolean.class, false);
	}

	/**
	 * Sets a boolean value into IS_SETUP_STARTED use preference
	 * 
	 * @param setupStarted Boolean value indicating if application setup was already started
	 */
	public synchronized void setSetupStarted(Boolean setupStarted) {
		setSharedPreference(KEYS.IS_SETUP_STARTED, setupStarted);
		Logger.d(TAG, "is setup started flag was set to: " + setupStarted);
	}

	public synchronized Boolean isNeedRefreshInbox() {
		return getSharedPreferenceValue(KEYS.NEED_REFRESH_INBOX, Boolean.class, false);
	}

	public synchronized void setNeedRefreshInbox(Boolean needRefresh) {
		setSharedPreference(KEYS.NEED_REFRESH_INBOX, needRefresh);
		Logger.d(TAG, "is need refresh inbox flag was set to: " + needRefresh);
	}
	/**
	 * Gets a value that indicates if application setup was already completed
	 *
	 * @return True if application setup was already completed
	 */
	public synchronized Boolean isSetupCompleted() {
		return getSharedPreferenceValue(KEYS.IS_SETUP_COMPLETED, Boolean.class, false);
	}

    public synchronized void setSetupCompleted (){

        Logger.d(TAG, "setSetupCompleted");
		// flow ended so password was changed successfully
		setPasswordChangeRequired(Constants.PasswordChangeRequiredStatus.NONE);
		setCurrentSetupState(Constants.SETUP_STATUS.SUCCESS);
		if( !isSetupCompleted()) {
			setSetupCompleted(true);
			setFirstTimeUse(false);
			insertWelcomeMessage();
			// reset shortcut creation checkbox
			setSharedPreference(Constants.KEYS.CREATE_SHORTCUT_CHECKBOX, true);

			addShortcut();
		}
    }

	private void addShortcut() {

		final Context context = VVMApplication.getContext();
		uninstallShortcutBrute(context);

		// creates the intent for application's shortcut, settings it's action
		// (which causes the shortcut
		// to be removed when the application is uninstalled) and class
		Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
		shortcutIntent.setComponent(new ComponentName(context, WelcomeActivity.class));
		shortcutIntent.addCategory(Intent.CATEGORY_LAUNCHER);

		// creates the intent for installing application's shortcut, adding
		// application's shortcut intent,
		// application's name and icon resource as its extra data
		final Intent intent = new Intent();//"com.android.launcher.action.INSTALL_SHORTCUT");
		intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
		intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, context.getString(R.string.app_name));
		intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(context, R.drawable.ic_voice_mail));
		// avoid duplicating the shortcut if already exists on home screen
		intent.putExtra(Constants.EXTRAS.EXTRA_SHORTCUT_DUPLICATE, false);

		//try to uninstall existing shortcuts first.
		intent.setAction("com.android.launcher.action.UNINSTALL_SHORTCUT");
		context.sendBroadcast(intent);
		Log.d(TAG, "uninstallShortcut - ours");


		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				intent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
				context.sendBroadcast(intent);
				Log.d(TAG, "install Shortcut - ours");
			}
		}, 5000);
	}

	/**
	 * Try to uninstall shortcut created by Google Play
	 * @param context
	 */
	private void uninstallShortcutBrute(Context context) {
		Log.d(TAG, "uninstallShortcutBrute - Google play shortcut");
		Intent intent = new Intent();
		//
		String oldShortcutUri = "#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10000000;package=com.att.mobile.android.vvm;component=com.att.mobile.android.vvm/.screen.WelcomeActivity;end";
		try {
			Intent altShortcutIntent  = Intent.parseUri(oldShortcutUri,0);
			intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, altShortcutIntent);
			intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, context.getString(R.string.app_name));
		} catch (Exception e) {
			Log.e(TAG,"",e);
		}
		intent.setAction("com.android.launcher.action.UNINSTALL_SHORTCUT");
		context.sendBroadcast(intent);
		Log.d(TAG, "uninstallShortcutBrute - Google play shortcut. Uninstall sent");
	}

	/**
	 * Sets a boolean value into IS_SETUP_COMPLETED use preference
	 * 
	 * @param setupCompleted Boolean value indicating if application setup was already completed
	 */
	public synchronized void setSetupCompleted(Boolean setupCompleted) {
		setSharedPreference(KEYS.IS_SETUP_COMPLETED, setupCompleted);
		Logger.d(TAG, "is setup completed flag was set to: " + setupCompleted);
	}
	/**
	 * Sets the user password
	 * 
	 * @param password
	 */
	public synchronized void setPassword(String password) {
		// save the new password
		String encryptedPassword;
		try {
			if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {  //Build.VERSION_CODES.JELLY_BEAN = 16
				encryptedPassword = Crypto.encrypt(VVMApplication.getContext().getPackageName()
						+ Constants.KEYS.PKEY, password, Crypto.PROVIDER_CRYPTO);
			} else {
				encryptedPassword = Crypto.encrypt(VVMApplication.getContext().getPackageName()
						+ Constants.KEYS.PKEY, password, null);
			}
			// for backwards compatabily we add a signature to distict between old unencrypted passwords and new
			// encrypted passwords
			String signedPassword = Constants.KEYS.PKEY + encryptedPassword;
			ModelManager.getInstance().setSharedPreference(	Constants.KEYS.PREFERENCE_PASSWORD, signedPassword);
			Logger.d(TAG, "####ModelManager.setPassword() password = "+ password+ " saved");
		} catch (Exception e) {
			Logger.e(TAG, "ModelManager.setPassword() failed", e);
		}
	}

	/**
	 * get password decrypted from shared prefs.
	 * 
	 * @return
	 */
	public synchronized String getPassword() {
		String password = null;
		String passwordValue = ModelManager.getInstance().getSharedPreferenceValue(Constants.KEYS.PREFERENCE_PASSWORD, String.class, null);

		if ( !TextUtils.isEmpty(passwordValue) ) {

			// check if the value has the new encrypted signature and should be decrypted or not and then shouls be re
			// saved encrypted.
			if (passwordValue.startsWith(Constants.KEYS.PKEY)) {
				// skip the signature and decrypt the rest
				String encryptedPassword = passwordValue.substring(Constants.KEYS.PKEY.length());
				try {
					if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) { //Build.VERSION_CODES.JELLY_BEAN = 16
						password = Crypto.decrypt(VVMApplication.getContext().getPackageName() + Constants.KEYS.PKEY,
								encryptedPassword, Crypto.PROVIDER_CRYPTO);
					} else {
						password = Crypto.decrypt(VVMApplication.getContext().getPackageName() + Constants.KEYS.PKEY,
								encryptedPassword, null);
					}
				} catch (Exception e) {
					Logger.e(TAG, "#### ModelManager.getPassword() failed", e);
				}
			}
			// we have a password that was saved non encrypted - go save it ancrypted and return it to the caller
			else {
				// return the never beeen encrypted password
				password = passwordValue;
				// from now this password will be saved encrypted
				setPassword(passwordValue);
			}
		}
		Logger.d(TAG, "####ModelManager.getPassword() returns password = "+ password);
		return password;
	}

	/**
	 * Sets a value that indicates if a password change is needed
	 * 
	 * @param passwordChangeRequiredStatus Boolean value to set the flag to. In case the value is False the changePasswordReason is
	 *            ignored.
	 */
	public synchronized void setPasswordChangeRequired(	int passwordChangeRequiredStatus ) {
		setSharedPreference(Constants.KEYS.PASSWORD_CHANGE_REQUIRED_STATUS,	Integer.valueOf(passwordChangeRequiredStatus));
	}

//	/**
//	 * Gets the minimum password length allowed (as returned from server)
//	 *
//	 * @return Minimum allowed length or -1 otherwise
//	 */
//	public synchronized int getMinPasswordLength() {
//		int val = Integer.parseInt(context
//				.getString(R.string.minPasswordLenght).trim());
//		try {
//			val = Integer.parseInt(getMetadata().get(
//					METADATA_VARIABLES.MinPasswordDigits).trim());
//		} catch (NumberFormatException e) {
//			Logger.e(TAG,
//					"ModelManager.getMinPasswordLength() failed with exception",
//					e);
//		}
//		return val;
//	}

//	/**
//	 * Gets the maximum password length allowed (as returned from server)
//	 *
//	 * @return Maximum allowed length or -1 otherwise
//	 */
//	public synchronized int getMaxPasswordLength() {
//		int val = Integer.parseInt(context
//				.getString(R.string.maxPasswordLenght).trim());
//		try {
//			val = Integer.parseInt(getMetadata().get(
//					METADATA_VARIABLES.MaxPasswordDigits).trim());
//		} catch (NumberFormatException e) {
//			Logger.e(TAG,
//					"ModelManager.getMaxPasswordLength() failed with exception",
//					e);
//		}
//		return val;
//	}

	public synchronized void setAttmStatus(int attmStatus) {
		setSharedPreference(Constants.KEYS.ATTM_STATUS, attmStatus);
	}

	public synchronized int getAttmStatus() {
		return getSharedPreferenceValue(Constants.KEYS.ATTM_STATUS, Integer.class, ATTM_STATUS.UNKNOWN);
	}

	/**
	 * save a flag that indicated whether to check Attm status on the onResume method - we mark when app goes to
	 * background and check attm status once app is back on foreground.
	 * 
	 * @param shouldCheck
	 */
	public synchronized void setCheckAttmStatusOnForeground(boolean shouldCheck) {
		ModelManager.getInstance().setSharedPreference(
				Constants.KEYS.SHOULD_CHECK_ATTM_STATUS_ON_FOREGROUND, shouldCheck);
	}

	/**
	 * get the flag that indicated whether to check Attm status on the onResume method - we mark when app goes to
	 * background and check attm status once app is back on foreground.
	 * 
	 */
	public  boolean shouldCheckAttmStatusOnForeground() {
		return ModelManager.getInstance().getSharedPreferenceValue(
				Constants.KEYS.SHOULD_CHECK_ATTM_STATUS_ON_FOREGROUND, Boolean.class, false);
	}

	/**
	 * Clears all values stored in the shared preferences
	 * 
	 * @author mkoltnuk
	 */
	public synchronized void clearPreferences() {
		Logger.d(TAG, "ModelManager::clearPreferences()");
		prefsEditor = prefs.edit();
		prefsEditor.clear();
		prefsEditor.commit();

		prefsEditor = null;

	}

//	/**
//	 * Clears a specific value of a given key
//	 *
//	 * @param key the name of the key that its value will be removed
//	 */
//	public synchronized void clearPreference(String key) {
//		if (key != null && !key.equalsIgnoreCase("")) {
//			prefsEditor = prefs.edit();
//			prefsEditor.remove(key);
//			prefsEditor.commit();
//			prefsEditor = null;
//		} else {
//			Logger.d(TAG,
//					"ModelManager.clearPreference() - key cannot be null or empty");
//		}
//	}

	/*
	 * (non-Javadoc)
	 * @see com.att.mobile.android.vvm.control.IEventDispatcher#addEventListener(com.att.mobile.android.vvm.control
	 * .EventListener)
	 */
	@Override
	public void addEventListener(EventListener listener) {
		dispatcher.addListener(listener);
	}

	/*
	 * (non-Javadoc)
	 * @see com.att.mobile.android.vvm.control.IEventDispatcher#removeEventListener(com.att.mobile.android.vvm.
	 * control.EventListener)
	 */
	@Override
	public void removeEventListener(EventListener listener) {
		dispatcher.removeListener(listener);
	}

	/*
	 * (non-Javadoc)
	 * @see com.att.mobile.android.vvm.control.IEventDispatcher#notifyListeners(int,
	 * com.att.mobile.android.vvm.control.Operation.StatusCode)
	 */
	@Override
	public void notifyListeners(int eventId, ArrayList<Long> messageIDs) {
		dispatcher.notifyListeners(eventId, messageIDs);
	}

	@Override
	public void removeEventListeners() {
		dispatcher.removeListeners();
	}

	private static class DatabaseHelper extends SQLiteOpenHelper {
		private static final String OLD_DB_VER = "old_db_ver";
		private static final String NEW_DB_VER = "new_db_ver";
		private Context mContext;
		private static final String DB_PREFERENCES = "db_pref";
		private static final String TAG = "DatabaseHelper";

		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DB_VERSION_2);
//			super(context, DATABASE_NAME, null, DATABASE_VERSION);
			mContext = context;
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(TABLE_INBOX_CREATE_STATEMENT);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Logger.d(TAG, "onUpgrade oldVesrion=" + oldVersion + " newVersion=" + newVersion);
	    	
	    	SharedPreferences local = mContext.getSharedPreferences(DB_PREFERENCES, Context.MODE_PRIVATE);
	    	
	    	Editor editor = local.edit();
	    	editor.putInt(OLD_DB_VER, oldVersion);
	    	editor.putInt(NEW_DB_VER, newVersion);
	    	editor.commit();
	    	
		}

		@Override
		public synchronized SQLiteDatabase getWritableDatabase() {
	    	SQLiteDatabase db = super.getWritableDatabase();
	    	
	    	// this does nothing since API 16 (jelly bean) but in earlier APIs it will enable greater DB performance 
	    	//db.setLockingEnabled(false);
	    	
	    	final SharedPreferences local = mContext.getSharedPreferences(DB_PREFERENCES, Context.MODE_PRIVATE);
	    	int currentVersion = db.getVersion();
	    	int oldVer = local.getInt(OLD_DB_VER, currentVersion);
	    	final int newVer = local.getInt(NEW_DB_VER, currentVersion);
	    	
	    	if (newVer == oldVer) {
	    		return db;
		}
	    			
	    	DBUpgrade upgradeScript = DBUpgradeFactory.getDBUpgradeScript(oldVer, newVer);
	    	upgradeScript.setUpgradeListener(new DBUpgrade.OnUpgradeListener() {
				@Override
				protected void onDBUpgradeEnded(boolean success) {
					
					Logger.i(TAG, "upgrade ended isSuccess = " + success);
					
					if (success) {
						Editor editor = local.edit();
				    	editor.putInt(OLD_DB_VER, newVer);
				    	editor.putInt(NEW_DB_VER, newVer);
				    	editor.commit();
					}// else TODO - should consider dropping and creating the DB from scratch
					
//					EventsHelper.reportDBUpgradeEnded(mContext, success);
	    	}
			});
	    	
	    	upgradeScript.upgrade(db, mContext);
	    	
	    	Logger.i(TAG, "getWritableDatabase - returned DB");
	    	
	    	return db;
	 		}
		
	}

//	/**
//	 * Sets the message UIDs to delete. param messageToDeleteUIDs (Set<Long> != null) message UIDs to delete.
//	 */
//	public void setMessageUIDsToDelete(Set<Long> messageToDeleteUIDs) {
//		this.messageToDeleteUIDs = messageToDeleteUIDs;
//	}
//
//	/**
//	 * Adds a message UID to the message UIDs to delete collection for the delete operation.
//	 *
//	 * @param messageUID (long) the UID of the message to delete.
//	 */
//	public void addMessageUIDToDelete(long messageUID) {
//		if (messageToDeleteUIDs != null && messageUID != Constants.WELCOME_MESSAGE_ID) {
//			messageToDeleteUIDs.add(messageUID);
//		}
//	}

	/**
	 * Returns whether a message with a given UID is currently pending for delete at the server.
	 *
	 * @param messageUID (long) message UID.
	 * @return (boolean) true in case the message is currently pending for delete at the server, false otherwise.
	 */
	public boolean isMessagePendingForDelete(long messageUID) {
		Cursor cursor = null;

		try {
			// queries the database for message's transcription
			cursor = db.query(DATABASE_TABLE_INBOX,
					new String[] {
							Inbox.KEY_IS_DELETED
					},
					new StringBuilder(Inbox.KEY_UID).append("=").append(messageUID)
							.toString(), null, null, null, null);

			if (cursor != null && cursor.getCount() == 1
					&& cursor.moveToFirst()) {
				// returns the transcription for the message with the given ID
				return cursor.getInt(cursor
						.getColumnIndex(Inbox.KEY_IS_DELETED))== Message.ReadDeletedState.DELETED;
			}

			return false;
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
			return false;
		} finally {
			// releases resources if needed
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	/**
	 * Returns the message UIDs to delete.
	 * 
	 * @return (Set<Long> != null) message UIDs to delete.
	 */
	public Long[] getMessageUIDsToDelete() {

        Cursor cursor = null;
        try {
            cursor = db.query(DATABASE_TABLE_INBOX, new String[] { Inbox.KEY_UID }, WHERE_MARKED_AS_DELETE, null, null, null, null);
            if ( cursor != null ) {

                Long[] uids = new Long[cursor.getCount()];
                int i = 0;
                while (cursor.moveToNext()) {
                    uids[i++] = cursor.getLong(0);
                }
                return uids;
            }

        } catch (Exception e) {
            Logger.e(TAG, e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

	/**
	 * Returns whether a message with a given UID is currently pending for mark as read at the server.
	 * 
	 * @param messageUID (long) message UID.
	 * @return (boolean) true in case the message is currently pending for mark as read at the server, false otherwise.
	 */
	public boolean isMessagePendingForMarkAsRead(long messageUID) {
		Cursor cursor = null;

		try {
			// queries the database for message's transcription
			cursor = db.query(DATABASE_TABLE_INBOX,
					new String[] {
							Inbox.KEY_IS_READ
					},
					new StringBuilder(Inbox.KEY_UID).append("=").append(messageUID)
							.toString(), null, null, null, null);

			if (cursor != null && cursor.getCount() == 1&& cursor.moveToFirst()) {
				// returns the transcription for the message with the given ID
				return cursor.getInt(cursor.getColumnIndex(Inbox.KEY_IS_READ))== Message.ReadDeletedState.READ;
		}

		return false;
		} catch (Exception e) {
			Logger.e(TAG, e.getMessage(), e);
			return false;
		} finally {
			// releases resources if needed
			if (cursor != null) {
				cursor.close();
			}
		}
	}

//	/**
//	 * Returns the message UIDs to mark as read.
//	 *
//	 * @return (Set<Long> != null) message UIDs to mark as read.
//	 */
//	public Set<Long> getMessageUIDsToMarkAsRead() {
//		MessageDo[] mes = getAllMarkedAsReadMessages();
//		Set<Long> messageToReadUIDs = Collections.synchronizedSet(new HashSet<Long>());
//		for (MessageDo msg : mes){
//			messageToReadUIDs.add(msg.getUid());
//			}
//		return messageToReadUIDs;
//		}

//	/**
//	 * Removes UIDs of deleted and marked as deleted messages from the match collections.
//	 *
//	 * @param deletedUIDs (Set<Long> != null) deleted message UIDs.
//	 * @param readUIDs (Set<Long> != null) marked as read message UIDs.
//	 */
//	public void removeDeletedAndMarkedAsReadMessageUIDs(Set<Long> deletedUIDs,
//			Set<Long> readUIDs) {
//		Iterator<Long> deleteIteration = deletedUIDs.iterator();
//		Long messageToRemove;
//		while (deleteIteration.hasNext()) {
//			messageToRemove = deleteIteration.next();
////			if (messageToDeleteUIDs != null) {
////				messageToDeleteUIDs.remove(messageToRemove);
////			}
//			if (messageToMarkAsReadUIDs != null) {
//				messageToMarkAsReadUIDs.remove(messageToRemove);
//			}
//		}
//
//		Iterator<Long> readIteration = readUIDs.iterator();
//		while (readIteration.hasNext()) {
//			messageToRemove = readIteration.next();
//			if (messageToMarkAsReadUIDs != null) {
//				messageToMarkAsReadUIDs.remove(messageToRemove);
//			}
//		}
//	}

//	/**
//	 * Removes message UIDs of message which no longer exist at the server from the pending message UIDs collections.
//	 *
//	 * @param serverMessageUIDs (ArrayList<Long> != null) list of UIDs of server EXISTING messages.
//	 */
//	public void removeServerNoLongerExistMessageUIDs(
//			ArrayList<Long> serverMessageUIDs) {
//		if (messageToDeleteUIDs != null) {
//			Iterator<Long> messageToDeleteUIDIteration = messageToDeleteUIDs
//					.iterator();
//			while (messageToDeleteUIDIteration.hasNext()) {
//				if (!serverMessageUIDs.contains(messageToDeleteUIDIteration
//						.next())) {
//					// removes the message UID from the message UIDs peding for
//					// delete collection
//					messageToDeleteUIDIteration.remove();
//				}
//			}
//		}
//
//		if (messageToMarkAsReadUIDs != null) {
//			// traverses over all message UIDs pending for mark as read
//			Iterator<Long> messageToMarkAsReadUIDIteration = messageToMarkAsReadUIDs
//					.iterator();
//			while (messageToMarkAsReadUIDIteration.hasNext()) {
//				// in case the message UID pending for mark as read doesn't
//				// exist at
//				// the server anymore
//				if (!serverMessageUIDs.contains(messageToMarkAsReadUIDIteration
//						.next())) {
//					// removes the message UID from the message UIDs pending for
//					// mark as read collection
//					messageToMarkAsReadUIDIteration.remove();
//				}
//			}
//		}
//	}

//	/**
//	 * @author istelman if there are pending requests and application closes save the uid sets so that next time
//	 *         application starts we will retry to send these requests
//	 */
//	public void resetDeleteAndMarkAsReadPendingUIDs() {
//
////		if (messageToDeleteUIDs != null) {
////			messageToDeleteUIDs.clear();
////		} else {
////			messageToDeleteUIDs = Collections.synchronizedSet(new HashSet<Long>());
////		}
//		if (messageToMarkAsReadUIDs != null) {
//			messageToMarkAsReadUIDs.clear();
//		} else {
//			messageToMarkAsReadUIDs = Collections.synchronizedSet(new HashSet<Long>());
//		}
//
//		VvmFileUtils.deleteInternalFile(context, pendingDeletesFilename);
//		VvmFileUtils.deleteInternalFile(context, pendingReadsFilename);
//
//		Log.d(TAG,
//				"ModelManager.resetDeleteAndMarkAsReadPendingUIDs() Delete and mark as read cache was reset.");
//		}

//	/**
//	 * Replaces message to delete or to mark as read old UID with its new UID in the match collections.
//	 *
//	 * @param oldUID (long) message old UID.
//	 * @param newUID (long) message new UID.
//	 */
//	public void replaceUID(long oldUID, long newUID) {
////		if (messageToDeleteUIDs != null && messageToDeleteUIDs.remove(oldUID)) {
////			messageToDeleteUIDs.add(newUID);
////		}
//
//		if (messageToMarkAsReadUIDs != null
//				&& messageToMarkAsReadUIDs.remove(oldUID)) {
//			messageToMarkAsReadUIDs.add(newUID);
//		}
//	}

	/**
	 * Sets the greeting list.
	 * 
	 * @param greetingList (ArrayList<Greeting> != null) the greeting list to set.
	 */
	public void setGreetingList(ArrayList<Greeting> greetingList) {
		this.greetingList = greetingList;
	}

	/**
	 * Gets the greeting list.
	 * 
	 * @return (ArrayList<Greeting>) the greeting list.
	 */
	public ArrayList<Greeting> getGreetingList() {
		return greetingList;
	}

	/**
	 * used to mark messages when SIM swap to allow new SIM messages overwrite old SIM messages in case of the same
	 * message uid
	 */
	public synchronized void markAllMessagesAsOverwrite() {

		ContentValues contentValues = new ContentValues();
		contentValues.put(Inbox.KEY_CAN_OVERWRITE, CAN_OVERWRITE_STATE.YES);
		try {
			db.update(DATABASE_TABLE_INBOX, contentValues, null, null);
		} catch (SQLiteException e) {
			Logger.e(TAG, e.getMessage(), e);
		}
	}
//	/**
//	 * Gets all message from inbox, according to a given filter type.
//	 *
//	 * @param filterType (int) the filter type to get inbox messages according to (@see MessageFilter).
//	 * @return (Cursor) inbox messages according to the given filter.
//	 */
//	public synchronized Cursor getAllMessagesFromInbox(int filterType) {
//		// holds the where clause for the query
//		StringBuilder whereClause = null;
//
//		// builds query's where clause according to the given filter type
//		switch (filterType) {
//			case (MessageFilter.TYPE_ALL): {
//				// nothing to do here
//				break;
//			}
//			case (MessageFilter.TYPE_UNREAD): {
//				whereClause = new StringBuilder().append(Inbox.KEY_IS_READ)
//						.append("=").append(Message.ReadDeletedState.UNREAD);
//				break;
//			}
//			case (MessageFilter.TYPE_SAVED): {
//				whereClause = new StringBuilder().append(Inbox.KEY_SAVED_STATE)
//						.append(" = ")
//						.append(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED);
//				break;
//			}
//			default: {
//				break;
//			}
//		}
//
//		Cursor messages = null;
//		try {
//
//			// performs the query against application's database
//			messages = db.query(DATABASE_TABLE_INBOX, new String[] {
//							Inbox._ID,
//							Inbox.KEY_UID, Inbox.KEY_TIME_STAMP,
//							Inbox.KEY_PHONE_NUMBER, Inbox.KEY_TRANSCRIPTION,
//							Inbox.KEY_FILE_NAME, Inbox.KEY_IS_READ,
//							Inbox.KEY_SAVED_STATE, Inbox.KEY_IS_DELETED,
//							Inbox.KEY_URGENT_STATUS, Inbox.KEY_DELIVERY_STATUS
//					},
//					whereClause != null ? whereClause.toString() : null, null,
//					null, null, Inbox.KEY_TIME_STAMP + " desc");
//
//			// in case messages exist, moves to the first one
//			if (messages != null) {
//				messages.moveToFirst();
//			}
//		} catch (SQLiteException e) {
//			Logger.e(TAG, e.getMessage(), e);
//		}
//		return messages;
//	}
//	/**
//	 * Gets the total count of application's messages, according to a given filter type (@see MessageFilter).
//	 *
//	 * @param filterType (int) the filter to get the total message count according to.
//	 * @return (int) the total count of application's messages, according to a given filter type (@see MessageFilter).
//	 */
//	public synchronized int getMessagesCount(int filterType) {
//		// holds SQL query
//		StringBuilder sqlQuery = null;
//
//		// builds query's where clause according to the given filter type
//		switch (filterType) {
//			case (MessageFilter.TYPE_ALL): {
//				// nothing to do here
//				break;
//			}
//			case (MessageFilter.TYPE_UNREAD): {
//				sqlQuery = new StringBuilder(allMessagesCountQuery)
//						.append(" WHERE ").append(Inbox.KEY_IS_READ).append("=")
//						.append(Message.ReadDeletedState.UNREAD);
//				break;
//			}
//			case (MessageFilter.TYPE_SAVED): {
//				sqlQuery = new StringBuilder(allMessagesCountQuery)
//						.append(" WHERE ").append(Inbox.KEY_SAVED_STATE)
//						.append(" = ")
//						.append(Message.SavedStates.INTERNAL_STORAGE_AS_SAVED);
//				break;
//			}
//			default:
//				break;
//		}
//
//		Cursor c = null;
//		int count = 0;
//		try {
//			c = db.rawQuery(sqlQuery != null ? sqlQuery.toString()
//					: allMessagesCountQuery, null);
//
//			if (c != null && c.moveToFirst()) {
//				count = c.getInt(0);
//			}
//			return count;
//		} catch (SQLiteException e) {
//			Logger.e(TAG, e.getMessage(), e);
//			return 0;
//		} finally {
//			if (c != null) {
//				c.close();
//			}
//		}
//	}


}
