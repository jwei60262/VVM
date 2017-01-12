
package com.att.mobile.android.vvm.control.receivers;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.att.mobile.android.infra.sim.SimManager;
import com.att.mobile.android.infra.utils.ContactUtils;
import com.att.mobile.android.infra.utils.Crypto.IMAP4;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.OperationsQueue;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.ACTIONS;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.Constants.EXTRAS;
import com.att.mobile.android.vvm.model.Constants.MessageFilter;
import com.att.mobile.android.vvm.model.Constants.PasswordChangeRequiredStatus;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.model.db.ModelManager.Inbox;
import com.att.mobile.android.vvm.screen.InboxActivity;
import com.att.mobile.android.vvm.screen.PlayerActivity;
import com.att.mobile.android.vvm.screen.WelcomeActivity;
/**
 * this service provide notification services for the VVM application and used as the handler of directed SMS
 * notifications coming from the server once the SMS receiver is getting a new SMS that should be handled by the VVM
 * application it calls this service with a matching intent and the SMS data. the service will parse the SMS and decide
 * witch operation to perform in the application - usually it will enqueue a fetch operation to get the VVM application
 * synced with the server changes (i.e new message, password changed, etc). this service also manage the notification
 * bar for the VVM application - clear the notification bar when asked, or pop up a new notification on a new message
 * inserted into the DB. the service is not killed and remain running once started in order to keep listening to new
 * message events from the DB especially for the cases where the application is moved to the background and no activity
 * is alive.
 * 
 * @author ldavid
 */
public class NotificationService extends IntentService {
	private static final String TAG = "NotificationService";
//	private static final String UPDATES_TAG = TAG + ".checkUpdates";// com.att.mobile.android.vvm.NotificationService.checkUpdates
	private static final int NOTIFICATION_ID = 1;
	private static final int UPDATE_AVAILABLE_NOTIFICATION_ID = 2;

	private ModelManager modelManager = null;
	private NotificationManager notificationManager = null;
	private Context applicationContext = null;

	private boolean hostFound = false;
	private boolean userFound = false;
	private boolean passwordFoundInSMS = false;
	private MailboxStatus mailboxStatus = MailboxStatus.UNKNOWN;
	
	public NotificationService() {
		super("NotificationService");
	}

	@Override
	public void onCreate() {

		Logger.d(TAG, "onCreate()");
		super.onCreate();
        applicationContext = getApplicationContext();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        ModelManager.createInstance(getApplicationContext());
        modelManager = ModelManager.getInstance();

    }

	@Override
	public void onHandleIntent(Intent intent) {
		if(intent != null){
			String action = intent.getAction();
			Logger.d(TAG, "onHandleIntent() action = " + action);
			if(action != null){
				// new SMS event sent from SMS receiver
				if (action.equals(ACTIONS.ACTION_NEW_SMS_NOTIFICATION)) {
                    Logger.d(TAG, "onHandleIntent() isSetupStarted = " + ModelManager.getInstance().isSetupStarted() + " isSetupCompleted=" + ModelManager.getInstance().isSetupCompleted());
                    if (!ModelManager.getInstance().isSetupStarted() && (!ModelManager.getInstance().isSetupCompleted())) {
						notifyMailboxSetupRequired();
					} else {
						handleIncomingSMS(intent.getStringExtra(Intent.EXTRA_TEXT));
					}
				}
				// boot completed but there are new messages that a notification, will handle only if no need to run ATTM
				else if (action.equals(ACTIONS.ACTION_BOOT_COMPLETED)
						&& modelManager.getAttmStatus() != Constants.ATTM_STATUS.PROVISIONED) {
					// check if any new messages were notified (notification bar) before
					// the shutdown/reboot
					Boolean hadNotificationsBeforeReboot = modelManager
							.hadNotificationsBeforeReboot();
					Logger.d(TAG,
							"onHandleIntent() NOTIFICATIONS_EXIST_BEFORE_REBOOT = "
									+ hadNotificationsBeforeReboot);

					// if such value exists and its 'true' send an intent to the
					// notification service
					if (hadNotificationsBeforeReboot != null && hadNotificationsBeforeReboot) {
						if (!SimManager.getInstance(this).validateSim().isSimSwapped()) {
							notifyNewMessages(action, -1);
						}
					}
				}

				// called when other activities that popped up notifications like
				// password change or mailbox created wants to remove the notification
				else if (action.equals(ACTIONS.ACTION_CLEAR_NOTIFICATION)) {
					clearNotification();
				} else if (action.equals(ACTIONS.ACTION_CLEAR_UPDATE_NOTIFICATION)) {
					clearUpdateNotification();
				} else if (action.equals(ACTIONS.ACTION_CLEAR_ALL_NOTIFICATIONS)) {
					clearNotification();
					clearUpdateNotification();
				}
				// send when message or messages are added or marked as read or deleted
				// or when
				// sync with server is finished
				// will be handled only when ATTM is not installed and provisioned
				else if (action.equals(ACTIONS.ACTION_NEW_UNREAD_MESSAGE)
						&& modelManager.getAttmStatus() != Constants.ATTM_STATUS.PROVISIONED) {
					long messageId = intent.getLongExtra(
							EXTRAS.EXTRA_NEW_MESSAGE_ROW_ID, -1);
					notifyNewMessages(action, messageId);
				}
				// will be handled only when ATTM is not installed and provisioned
				else if ((action
						.equals(ACTIONS.ACTION_UPDATE_NOTIFICATION_AFTER_REFRESH)
						|| action.equals(ACTIONS.ACTION_UPDATE_NOTIFICATION))
						&& modelManager.getAttmStatus() != Constants.ATTM_STATUS.PROVISIONED) {
					notifyNewMessages(action, -1);
				}
				// will be handled only when ATTM is not installed and provisioned
				else if (action.equals(ACTIONS.ACTION_PASSWORD_MISSMATCH)
						&& modelManager.getAttmStatus() != Constants.ATTM_STATUS.PROVISIONED) {
					if(modelManager.getMailBoxStatus().equals("C") || modelManager.getMailBoxStatus().equals("R") ){
						modelManager.setCurrentSetupState(Constants.SETUP_STATUS.RESET_PASSWORD);

					} else {
						modelManager.setCurrentSetupState(Constants.SETUP_STATUS.ENTER_EXISTING_PWD);
					}
					if (!((VVMApplication) getApplicationContext()).isVisible()) {
						notifyPasswordMissmatch();
					} else {
						modelManager.notifyListeners(EVENTS.START_WELCOME_ACTIVITY, null);
					}
				}
			}
		}
//		return START_NOT_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	/**
	 * notify Mailbox password is wrong and needs to be changed
	 */
	private void notifyPasswordMissmatch() {

		notificationManager.cancel(NOTIFICATION_ID);

		modelManager.setNotificationsBeforeReboot(true);
		modelManager.setNeedRefreshInbox(true);

		// prepare a notification intent that will be launched when tapping the
		// notification item
		Intent notificationIntent = new Intent();

		// the text shown on the status bar when notification is sent :
		// "You must set up Voicemail to receive new messages."
		String tickerText = getString(R.string.passwordMissmatchNotificationTickerText);

		// set expanded message text:
		// "You must set up Voicemail to receive new messages.\nTap here to set up."
		String contentText = getString(R.string.passwordMissmatchNotificationContentText);

		notificationIntent.setAction(ACTIONS.ACTION_MAILBOX_CREATED);

		notificationIntent.setClass(this, WelcomeActivity.class);
		// in case we already have a player activity in the stack we
		// want to use it and that all other activities on top to be
		// cleared.
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
//		Notification notification = new Notification(
//				R.drawable.ic_stat_notify_vvm, tickerText,
//				System.currentTimeMillis());
//
//		notification.setLatestEventInfo(applicationContext,
//				getText(R.string.passwordMissmatchNotificationContentTitle),
//				contentText, contentIntent);
		Notification notification;
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
			notification = new Notification.Builder(this)
			.setContentTitle(getText(R.string.passwordMissmatchNotificationContentTitle))
			.setContentText(contentText)
			.setTicker(tickerText)
			.setSmallIcon(R.drawable.ic_stat_notify_vvm)
			.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_voice_mail))
			.setContentIntent(contentIntent)
			.build();
		} else {
			notification = new NotificationCompat.Builder(this)
			.setContentTitle(getText(R.string.passwordMissmatchNotificationContentTitle))
			.setContentText(contentText)
			.setTicker(tickerText)
			.setSmallIcon(R.drawable.ic_stat_notify_vvm)
			.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_voice_mail))
			.setContentIntent(contentIntent)
			.build();
		}
		// play the default notification sound
		notification.defaults |= Notification.DEFAULT_SOUND;
		notification.defaults |= Notification.DEFAULT_VIBRATE;
//		notification.audioStreamType = Notification.STREAM_DEFAULT;

		notificationManager.cancel(NOTIFICATION_ID);
		try {
			notificationManager.notify(NOTIFICATION_ID, notification);
		} catch (Exception e) {
			Log.e(TAG, "notifyMailboxCreated() Exception occured: "
					+ e);
		}

		Logger.d(TAG, "notifyPasswordMissmatch() Showing notification: "
				+ contentText);
	}

	/**
	 * notify Mailbox Created after getting SMS with S=C
	 */
	private void notifyMailboxSetupRequired() {

		String tickerText = null;
		String contentText = null;
		notificationManager.cancel(NOTIFICATION_ID);

		modelManager.setNotificationsBeforeReboot(true);

		// prepare a notification intent that will be launched when tapping the
		// notification item
		Intent notificationIntent = new Intent();

		// the text shown on the status bar when notification is sent :
		if (mailboxStatus == MailboxStatus.INITIALIZED) {
			tickerText = getString(R.string.WelcomeATTServiceText);
		} else {
			tickerText = getString(R.string.mailboxSetupRequiredNotificationText);
		}
		contentText = getString(R.string.mailboxSetupRequiredNotificationContentText);

		notificationIntent.setAction(ACTIONS.ACTION_MAILBOX_CREATED);

		notificationIntent.setClass(this, WelcomeActivity.class);
		// in case we already have a player activity in the stack we
		// want to use it and that all other activities on top to be
		// cleared.
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
//		Notification notification = new Notification(
//				R.drawable.ic_stat_notify_vvm, tickerText,
//				System.currentTimeMillis());
//
//		notification.setLatestEventInfo(applicationContext,
//				getText(R.string.mailboxSetupRequiredNotificationTitle),
//				contentText, contentIntent);
		Notification notification;
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
		notification = new Notification.Builder(this)
        .setContentTitle(getText(R.string.mailboxSetupRequiredNotificationTitle))
         .setContentText(contentText)
         .setTicker(tickerText)
         .setSmallIcon(R.drawable.ic_stat_notify_vvm)
         .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_voice_mail))
         .setWhen(System.currentTimeMillis())
         .setContentIntent(contentIntent)
         .build();
		} else {
			notification = new NotificationCompat.Builder(this)
	        .setContentTitle(getText(R.string.mailboxSetupRequiredNotificationTitle))
	         .setContentText(contentText)
	         .setTicker(tickerText)
	         .setSmallIcon(R.drawable.ic_stat_notify_vvm)
	         .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_voice_mail))
	         .setWhen(System.currentTimeMillis())
	         .setContentIntent(contentIntent)
	         .build();
		}

		// play the default notification sound
		notification.defaults |= Notification.DEFAULT_SOUND;
		notification.defaults |= Notification.DEFAULT_VIBRATE;
//		notification.audioStreamType = Notification.STREAM_DEFAULT;

		notificationManager.cancel(NOTIFICATION_ID);
		try {
			notificationManager.notify(NOTIFICATION_ID, notification);
		} catch (Exception e) {


			Log.e(TAG, "notifyMailboxCreated() Exception occured: "
					+ e);
		}

		Logger.d(TAG, "notifyMailboxCreated() Showing notification: "
				+ contentText);
	}

	/**
	 * when isNewMessageInserted is false: update the new message notification with the current number of unread
	 * messages remove notification if no unread messages called when message or messages are marked as read or deleted
	 * or when sync with server is finished when isNewMessageInserted is true: called when a new message is inserted to
	 * the DB pop up a notification to the notification bar if the application is in the background
	 */
	private void notifyNewMessages(String action, long messageId) {

		int newMessagesCount = modelManager.getNewMessagesCount()
					/*- PlayerActivity.getNumOfMessagesToMarkRead()*/;

		// if there are no new messages cancel any existing notification
		// no matter what is the settings of the notification
		if (newMessagesCount == 0) {
			notificationManager.cancel(NOTIFICATION_ID);
			modelManager.setNotificationsBeforeReboot(false);
			Logger.d(TAG, "Cancel notification: no new messages");
		} else {

			// read new message notification setting value
			Boolean notifyOnNewMessage = modelManager
					.isNotifyOnNewMessagesEnabled();

			// only if the 'notify on new message(s)' preference is set to 'true' or
			// on its default value (null = true)
			if (notifyOnNewMessage == null || notifyOnNewMessage) {
				Logger.d(TAG,
						"Going to notify the user about new message, because the setting is set to ON");

				// we have new message(s) set the flag to 'true' for REBOOT
				// possibility
				// there is a code that handles turning it off
				modelManager.setNotificationsBeforeReboot(true);

				// prepare a notification intent that will be launched when tapping
				// the
				// notification item
				Intent notificationIntent = new Intent();

				// set expanded message text: "New message from <name or number>)
				StringBuilder contentText = null; // the text inside the
				// notification
				// list
				StringBuilder tickerText = null; // the text shown on the status bar
				// when notification is sent

				// set ticker text
				tickerText = new StringBuilder(
						getText(R.string.newSingleMessageNotificationText));
				tickerText.append(' ');

				Cursor messageCursor = null;
				long when = System.currentTimeMillis();
				try {
					// get the new message details from DB
					// if the message id is known
					if (messageId > -1) {
						messageCursor = modelManager.getMessage(messageId);
					}
					// if the message id is not known - just get the newest message
					else {
						messageCursor = modelManager.getLastUnreadMessage();
						messageId = messageCursor.getLong(messageCursor
								.getColumnIndex(Inbox._ID));
					}

					if (messageCursor != null) {
						when = messageCursor.getLong(messageCursor
								.getColumnIndex(Inbox.KEY_TIME_STAMP));
						String lastCallerPhoneNumber = messageCursor
								.getString(messageCursor
										.getColumnIndex(Inbox.KEY_PHONE_NUMBER));
						boolean isDSN = (messageCursor.getInt(messageCursor
								.getColumnIndex(Inbox.KEY_DELIVERY_STATUS)) == 1);
						String lastCallerName = ContactUtils.getContactDisplayName(
								this, lastCallerPhoneNumber);

						// sender name resolving
						if (lastCallerName != null && lastCallerName.length() > 0) {
							tickerText.append(lastCallerName);
						} else if (lastCallerPhoneNumber != null && lastCallerPhoneNumber.equals(getString(R.string.welcomeMessagePhoneNumber))) {
							tickerText.append(lastCallerPhoneNumber);
						} else if (lastCallerPhoneNumber != null && lastCallerPhoneNumber.length() > 0) {
							tickerText.append(PhoneNumberUtils.formatNumber(lastCallerPhoneNumber, getString(R.string.defaultCountryIso))); // nslesuratin - works also for pre-LolliPop, don't change!
						} else if (isDSN) {
							tickerText.append(applicationContext
									.getString(R.string.deliveryStatusSenderName));
						} else {
							tickerText.append(applicationContext
									.getString(R.string.privateNumber));
						}
						boolean isSetupCompleted = modelManager.isSetupCompleted();

						if (newMessagesCount == 1
								/*&& !action
										.equals(ACTIONS.ACTION_UPDATE_NOTIFICATION)*/) {
							// ticker text is same as content text
							contentText = tickerText;
							// Tapping the expanded message will launch the player
							// activity
							// when there is a single new message
							notificationIntent
									.setAction(ACTIONS.ACTION_LAUNCH_PLAYER_FROM_NOTIFICATION);
							if(!isSetupCompleted){
								notificationIntent.setClass(this, WelcomeActivity.class);
							} else {
								notificationIntent.setClass(this, PlayerActivity.class);
							}
							notificationIntent
									.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

							int filtertype = modelManager.isMessageSaved(messageId) ? MessageFilter.TYPE_SAVED : MessageFilter.TYPE_ALL;
							notificationIntent.putExtra(PlayerActivity.IntentExtraNames.EXTRA_ID,messageId);
							notificationIntent.putExtra(PlayerActivity.IntentExtraNames.FILTER_TYPE,filtertype);
							notificationIntent.putExtra(PlayerActivity.IntentExtraNames.LAUNCHED_FROM_INBOX,false);
						}
						// set expanded message text: "You have <#> new voice
						// messages)
						else {
							contentText = new StringBuilder(
									getText(R.string.newMessageNotificationTextStart))
									.append(' ')
									.append(newMessagesCount)
									.append(' ')
									.append(getText(R.string.newMessageNotificationTextEnd));

							// Tapping the expanded message will launch
							// InboxActivity
							// for
							// multiple new messages
							notificationIntent
									.setAction(ACTIONS.ACTION_LAUNCH_INBOX_FROM_NOTIFICATION);
							if(!isSetupCompleted){
								notificationIntent.setClass(this, WelcomeActivity.class);
							} else {
								notificationIntent.setClass(this, InboxActivity.class);

							}
							// in case we already have an activity in the stack we
							// want to use it and that all other activities on top
							// to be
							// cleared.
							notificationIntent
									.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
						}
					}
				} catch (Throwable e) {
					clearNotification();
					contentText = new StringBuilder(
							getText(R.string.newMessageNotification));
					notificationIntent
							.setAction(ACTIONS.ACTION_LAUNCH_INBOX_FROM_NOTIFICATION);
					notificationIntent.setClass(this, InboxActivity.class);
					notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				} finally {
					// releases resources if needed
					if (messageCursor != null) {
						messageCursor.close();
					}
				}

				PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
						notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

				Notification notification = null;
//				if (action.equals(ACTIONS.ACTION_UPDATE_NOTIFICATION)
//						|| action
//								.equals(ACTIONS.ACTION_UPDATE_NOTIFICATION_AFTER_REFRESH)) {
////					notification = new Notification(R.drawable.ic_stat_notify_vvm,
////							null, when);
//					tickerText =null;
//				} else {
////					notification = new Notification(R.drawable.ic_stat_notify_vvm,
////							tickerText, when);
//				}

//				notification.setLatestEventInfo(applicationContext,
//						getText(R.string.notificationTitle), contentText,
//						contentIntent);
				if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
				notification = new Notification.Builder(this)
		        .setContentTitle(getText(R.string.notificationTitle))
		         .setContentText(contentText)
		         .setTicker(tickerText)
		         .setSmallIcon(R.drawable.ic_stat_notify_vvm)
		         .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_voice_mail))
		         .setWhen(when)
		         .setContentIntent(contentIntent)
		         .build();
				} else {
					notification = new NotificationCompat.Builder(this)
			        .setContentTitle(getText(R.string.notificationTitle))
			         .setContentText(contentText)
			         .setTicker(tickerText)
			         .setSmallIcon(R.drawable.ic_stat_notify_vvm)
			         .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_voice_mail))
			         .setWhen(when)
			         .setContentIntent(contentIntent)
			         .build();
				}
				// only when the notification is called upon a new message added to
				// DB
				if (action.equals(ACTIONS.ACTION_NEW_UNREAD_MESSAGE)) {
					// play the default notification sound
					notification.defaults |= Notification.DEFAULT_SOUND;
					notification.defaults |= Notification.DEFAULT_VIBRATE;
//					notification.audioStreamType = Notification.STREAM_DEFAULT;
					

					notificationManager.cancel(NOTIFICATION_ID);
				}

				try {
					notificationManager.notify(NOTIFICATION_ID, notification);
				} catch (Exception e) {
					Log.e(TAG, "notifyMailboxCreated() Exception occured: "
							+ e);
				}

				Logger.d(TAG, "newMessageNotification() Showing notification: "
						+ contentText);
			} else {
				Logger.d(TAG,
						"New message notification will not be shown, because the setting is OFF");
			}

		}
	}

	/**
	 * parse the sms received and save its updates data to the shared preferences file update host,port, password.
	 * examples: smpp-vip.anypath.com:143?f=0&v=600&m=2546636842&p=&S=R&s=993&d=587
	 * &t=4:2546636842:A:nbvip.ap2:ms02:IMAP4§STT§AMR:21799 MAB1:5400?f=1&v=400
	 * &m=16148607555&s=5433&S=C&P=$gHy9&p=$gHy9&t=4:16148607555 :A:CMSA:R:17506:ms01:AMMC:Rs4Dt6
	 * 
	 * @param text
	 */
	private void handleIncomingSMS(String text) {

        Logger.d(TAG, "#### NotificationService::handleIncomingSMS text = "+text);
		
		try {
			// get host and port details
			extractHost(text);
			// get parameters
			extractParameters(text);
			
			Logger.d(TAG, "#### NotificationService::handleIncomingSMS mailboxStatus=" + mailboxStatus );

			// make sure that the minimum mandatory parameters exist
			if (hostFound && userFound) {

				switch (mailboxStatus) {
					case INITIALIZED:

						String password = modelManager.getPassword();

						if (modelManager.isFirstTimeUse()) {
							// we want to go through the insert password screen in
							// the setup flow in case we got SMS with no password
							// for the first time
							if (password == null) {
								modelManager
										.setPasswordChangeRequired(PasswordChangeRequiredStatus.PASSWORD_MISSING);
							} else if (passwordFoundInSMS) {
								modelManager
										.setPasswordChangeRequired(PasswordChangeRequiredStatus.NONE);
							}

							/*
							 * code not in use, may be asked to have it in the future // check counter of number of
							 * times to show notification of setup requeried when getting SMS with S=I int
							 * notificationCount = modelManager.getSharedPreferenceValue(
							 * KEYS.NOTIFY_SETUP_REQUIRED_COUNTER, Integer.class, 0); int maxNotificationsOnsetup =
							 * Integer.valueOf(getString( R.string.maxNotificationsOnSetup, 2)); if (notificationCount <
							 * maxNotificationsOnsetup){ notifyMailboxSetupRequired(); // advance counter and save it
							 * ++notificationCount; modelManager.setSharedPreference
							 * (KEYS.NOTIFY_SETUP_REQUIRED_COUNTER, notificationCount); }
							 */

							// notify the setup is required to the status bar
							notifyMailboxSetupRequired();

							// start the welcome activity if application is visible
							modelManager.notifyListeners(EVENTS.START_WELCOME_ACTIVITY, null);
						} else {
							// no password so no point of sending fetch request - we
							// want to go through the insert password screen in case
							// we got SMS with no password
							if (password == null) {
								modelManager
										.setPasswordChangeRequired(PasswordChangeRequiredStatus.PASSWORD_MISSING);
							} else {
								OperationsQueue.getInstance()
										.enqueueFetchHeadersAndBodiesOperation();
							}
						}
						break;

					case CREATED:
						// in order to start from the welcome again
						//modelManager.setFirstTimeUse(true);

						// code not in use, may be asked to have it in the future
						// modelManager.setSharedPreference(KEYS.NOTIFY_SETUP_REQUIRED_COUNTER,
						// 0);

						if (passwordFoundInSMS) {
							modelManager
									.setPasswordChangeRequired(PasswordChangeRequiredStatus.TEMPORARY_PASSWORD);
						}

						// notify any way
						if (modelManager.isSetupCompleted()) {
							modelManager.setCurrentSetupState(Constants.SETUP_STATUS.RESET_PASSWORD);
							modelManager.setNeedRefreshInbox(true);
						} else {
                            modelManager.setFirstTimeUse(true);
                        }

						if (!((VVMApplication) getApplicationContext()).isVisible()) {
							//							notifyPasswordMissmatch();
							notifyMailboxSetupRequired();
						} else {
							// start the welcome activity if application is visible
							modelManager.notifyListeners(EVENTS.START_WELCOME_ACTIVITY, null);
						}
						break;

					case PASSWORD_RESET_BY_ADMIN:
						// password change is needed, turn the key ON
						ModelManager.getInstance().setPasswordChangeRequired(
								PasswordChangeRequiredStatus.RESET_BY_ADMIN);

						// notify any way
						notifyMailboxSetupRequired();

						// start the welcome activity if application is visible
						modelManager.notifyListeners(EVENTS.START_WELCOME_ACTIVITY, null);
						break;
					default: 
						modelManager.setSetupStarted(false);
						modelManager.setCurrentSetupState(Constants.SETUP_STATUS.UNKNOWN_MAILBOX);
						// start the welcome activity if application is visible
						modelManager.notifyListeners(EVENTS.START_WELCOME_ACTIVITY, null);
                        break;
                }
            } else {
                Log.e(TAG, "handleIncomingSMS() Host and Mailbox were not found in SMS.");
			}
			// turn value flags off
			turnFlagsOff();
		} catch (Exception e) {
			Log.e(TAG,
					"handleIncomingSMS() Exception while proccesing SMS header/details",
					e);
		}
	}

	private void extractHost(String text) {
		int queryStringStart = text.indexOf("?");

		if (queryStringStart > 0) {
			String[] hostPortArr = text.substring(0, queryStringStart).split(
                    ":"); // smpp-vip.anypath.com:143
			String host = hostPortArr[0];

			if (host != null) {
				// put in shared preferences
				modelManager.setSharedPreference(
						Constants.KEYS.PREFERENCE_HOST, host);
				hostFound = true;
				Log.d(TAG, "NotificationService.extractHost() , host = " + host);
			}
		} else {
			Log.e(TAG, "extractHost() SMS text is empty cannot get details!");
		}
	}

	/**
	 * Finds needed parameters in SMS and initializes corresponding class fields
	 * 
	 * @param text
	 */
	private void extractParameters(String text) {
		int queryStringStart = text.indexOf("?");

		if (queryStringStart > 0) {
			String queryString = text.substring(queryStringStart + 1); // f=0&v=600&m=2546636842&p=&S=R&s=993&d=587&t=4:2546636842:A:nbvip.ap2:ms02:IMAP4§STT§AMR:21799
			// MAB1:5400?f=1&v=400&m=16148607555&s=5433&S=C&P=$gHy9&p=$gHy9&t=4:16148607555:A:CMSA:R:17506:ms01:AMMC:Rs4Dt6

			// get key/value pair as an array (ex. k=v)
			String[] paramsArr = queryString.split("&");

			// process each pair
			for (String param : paramsArr) {
				if (param != null) {
					// save needed parameters to shared preferences and throw
					// out the others
					handleParameter(param);
				}
			}
		} else {
			Log.e(TAG,
					"extractParameters() SMS text is empty cannot get details!");
		}
	}

	/**
	 * Handles parameter/value pairs. d=<SMTPcxnInfo> VIP to use for the SMTP interface Format = XXX.YYY.ZZZ.WWW:port
	 * number Or Port number f=<CLIFlag> CLI restricted flag (1=true, 0-false) v=<versionID> Version of the MAB software
	 * generating the message m=<mailbox> The mailbox number of the subscriber s=<sslport> MAB SSL port number
	 * S=<status> Mailbox Status C (for Created) I (for Initialized) U (for Unknown) N (for No Such Mailbox) R (for
	 * Password Change Required) D (for Deactivated Enhanced Services) p=<password> The initial mailbox password
	 * P=<password> The mailbox password t=<tokenvalue> Token needed by the MAB to identify VMS of origin. Its content
	 * is variable and can change. The <tokenvalue> needs to be returned to the MAB as received.
	 * 
	 * @param param the parameter - key and value
	 */
	private void handleParameter(String param) {
		String paramKey = null;
		String paramValue = null;
		String[] p_v = null;

		p_v = param.split("=");
		if (p_v.length == 2) {
			paramKey = p_v[0]; // key
			paramValue = p_v[1]; // value

			// make sure we have a valid pair
			if (paramKey != null && paramValue != null) {
				// mailbox status
				if (paramKey.equals("S")) {
					setMailboxStatus(paramValue);
				}
				// ssl port - used for http clients, not needed here
				/*
				 * else if (paramKey.equals("s")) { modelManager.setSharedPreference( Constants.KEYS.ALTERNATIVE_PORT,
				 * paramValue); }
				 */
				// password
				else if (paramKey.equals("P")) {
					String telephoneNumber = modelManager
							.getSharedPreferenceValue(
									Constants.KEYS.PREFERENCE_MAILBOX_NUMBER,
									String.class, "");
					String decryptedPassword = IMAP4.decrypt(paramValue, telephoneNumber);
					Log.d(TAG, "NotificationService.handleParameter() 'p'");
					modelManager.setPassword(decryptedPassword);
					passwordFoundInSMS = true;
				}
				// mailbox
				else if (paramKey.equals("m")) {
					modelManager.setSharedPreference(
							Constants.KEYS.PREFERENCE_MAILBOX_NUMBER, paramValue);
					userFound = true;
					Log.d(TAG, "NotificationService.handleParameter() 'm', PREFERENCE_MAILBOX_NUMBER = " + paramValue);
				}
				// token
				else if (paramKey.equals("t")) {
					modelManager.setSharedPreference(
							Constants.KEYS.PREFERENCE_TOKEN, paramValue);
					Log.d(TAG, "NotificationService.handleParameter() 't', token = " + paramValue);
					userFound = true;
				}
				// i=secondary address info added in server mab7. value in SMS should be "i=143/993" - ssl and regular
				// ports
				else if (paramKey.equals("i")) {
					String[] ports = paramValue.split("/");
					if (ports != null && ports.length == 2) {
						Log.d(TAG, "NotificationService.handleParameter() 'i', port = " + ports[0] + " ssl port = "
								+ ports[1]);
						modelManager.setSharedPreference(
								Constants.KEYS.PREFERENCE_PORT, ports[0]);
						modelManager.setSharedPreference(
								Constants.KEYS.PREFERENCE_SSL_PORT, ports[1]);
					}
				}
			}
		}
	}

	private void clearNotification() {
		notificationManager.cancel(NOTIFICATION_ID);
		modelManager.setNotificationsBeforeReboot(false);
	}

	private void clearUpdateNotification() {
		notificationManager.cancel(UPDATE_AVAILABLE_NOTIFICATION_ID);
	}

	/**
	 * Holds possible mailbox status
	 */
	public  enum MailboxStatus {
		CREATED, INITIALIZED, UNKNOWN, NO_SUCH_MAILBOX, PASSWORD_RESET_BY_ADMIN, DEACTIVATED_ENHANCED_SERVICES;
	}

	private void setMailboxStatus(String status) {
		if (status.equals("C")) {
			this.mailboxStatus = MailboxStatus.CREATED;
		} else if (status.equals("I")) {
			this.mailboxStatus = MailboxStatus.INITIALIZED;
		} else if (status.equals("N")) {
			this.mailboxStatus = MailboxStatus.NO_SUCH_MAILBOX;
		} else if (status.equals("R")) {
			this.mailboxStatus = MailboxStatus.PASSWORD_RESET_BY_ADMIN;
		} else if (status.equals("U")) {
			this.mailboxStatus = MailboxStatus.UNKNOWN;
		} else if (status.equals("D")) {
			this.mailboxStatus = MailboxStatus.DEACTIVATED_ENHANCED_SERVICES;
		}
		modelManager.setMailBoxStatus(status);
	}

	private void turnFlagsOff() {
		hostFound = false;
		userFound = false;
	}


//	/**
//	 * check for VVM application updates using the web market
//	 */
//	public void checkForUpdate() {
//
//		Logger.d(UPDATES_TAG, "NotificationService.checkForUpdate() ");
//		// check in settings is user enable the check for update preference
//		if (modelManager.isCheckforUpdatesEnabled()) {
//			// the interval for checking new updates, usualy 14 days
//			int checkForUpdatesIntervalInDays = Integer
//					.valueOf(getString(R.string.checkForUpdatesIntervalInDays));
//			// get the last check date
//			long lastUpateCheckDate = modelManager.getSharedPreferenceValue(
//					KEYS.LAST_UPDATE_CHECK_DATE, Long.class, Integer.valueOf(0)
//							.longValue());
//
//			Time lastCheckTime = new Time();
//			lastCheckTime.set(lastUpateCheckDate);
//			Time now = new Time();
//			now.setToNow();
//
//			// check if no check was ever done or the interval for checking has
//			// passed.
//			// if the year now is different than the year of checking we add the
//			// 365
//			// days for the currect year day to get the real gap between year
//			// day of
//			// now the year day of last check
//
//			if (lastUpateCheckDate == 0
//					|| (now.yearDay + ((now.year - lastCheckTime.year) * 365) - lastCheckTime.yearDay) >= checkForUpdatesIntervalInDays) {
//
//				Logger.d(UPDATES_TAG,
//						"NotificationService.checkForUpdate() going to check for VVM update on web market");
//
//				String MARKET_URL = "https://market.android.com/details?id=";
//				String PACKAGE_NAME = getPackageName();
//
//				try {
//					//
//					String url = MARKET_URL + PACKAGE_NAME;
//
//					Logger.d(UPDATES_TAG,
//							"NotificationService.checkForUpdate() url =  " + url);
//
//					HttpParams httpParameters = new BasicHttpParams();
//					// Set the timeout in milliseconds until a connection is established.
//					int timeoutConnection = 30000;
//					HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
//					// Set the default socket timeout (SO_TIMEOUT)
//					// in milliseconds which is the timeout for waiting for data.
//					int timeoutSocket = 60000;
//					HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
//
//					DefaultHttpClient httpClient = new DefaultHttpClient(httpParameters);
//					HttpRequestBase httpGet = new HttpGet(url);
//
//					HttpResponse response = httpClient.execute(httpGet);
//					int res = response.getStatusLine().getStatusCode();
//					Logger.d(UPDATES_TAG,
//							"NotificationService.checkForUpdate() res =  " + res);
//
//					// save the last time check for update was done
//					modelManager.setSharedPreference(
//							KEYS.LAST_UPDATE_CHECK_DATE,
//							System.currentTimeMillis());
//
//					if (res == HttpStatus.SC_OK) {
//
//						Logger.d(UPDATES_TAG,
//								"NotificationService.checkForUpdate() package "
//										+ PACKAGE_NAME + " was Found");
//
//						// go analyze the response
//						ByteArrayOutputStream outstream = new ByteArrayOutputStream();
//						response.getEntity().writeTo(outstream);
//						byte[] responseBody = outstream.toByteArray();
//						String responseStr = new String(responseBody);
//
//						// format of response is:
//						// <dd itemprop="softwareVersion">0.8.50</dd>
//						int index = responseStr.indexOf("softwareVersion");
//						if (index > -1) {
//							int indexStart = responseStr.indexOf(">", index);
//							int indexEnd = responseStr.indexOf("<", index);
//
//							if (indexStart > -1 && indexEnd > indexStart) {
//								String marketVersionName = responseStr
//										.substring(indexStart + 1, indexEnd);
//
//								// save the market version name
//								modelManager.setSharedPreference(
//										KEYS.AVAILABLE_VERSION_ON_MARKET,
//										marketVersionName);
//
//								if (isMarketVersionHigher(((VVMApplication) getApplication()).getApplicationVersion(),
//										marketVersionName)) {
//									Logger.d(
//											UPDATES_TAG,
//											"UpdatesChecker.checkForUpdate() found newer version on market, going to notify user");
//									NotifyUpdateAvialable();
//								} else {
//									Logger.d(UPDATES_TAG,
//											"UpdatesChecker.checkForUpdate() no updates");
//								}
//							}
//						}
//					} else {
//						Logger.d(UPDATES_TAG,
//								"UpdatesChecker.checkForUpdate() package "
//										+ PACKAGE_NAME + " was not Found");
//					}
//				} catch (Exception e) {
//					Log.e(TAG, e.getMessage(), e);
//				}
//			} else {
//				Logger.d(
//						UPDATES_TAG,
//						"UpdatesChecker.checkForUpdate() not going to check for updates, last check for updates was "
//								+ lastCheckTime.format3339(false));
//			}
//		}
//	}

//	/**
//	 * Checks if the given market version is higher than the current application version
//	 * 
//	 * @param context
//	 * @param marketVersionName
//	 * @return
//	 */
//	public static boolean isMarketVersionHigher(String currentVersionName,
//			String marketVersionName) {
//
//		Logger.d(UPDATES_TAG,
//				"NotificationService.isMarketVersionHigher() app version " + currentVersionName + ", market version "
//						+ marketVersionName);
//
//		if (!marketVersionName.equals(currentVersionName)) {
//
//			StringTokenizer marketTokenizer = new StringTokenizer(marketVersionName, ".");
//			StringTokenizer currentTokenizer = new StringTokenizer(currentVersionName, ".");
//
//			// go over the build numbers and compare each number between the dots -
//			// i.e. if market build number is x.y.z and current app build number is a.b.c we compare x to a then y to b
//			// and then z to c
//			// if we found all equal or one of the market is lower than the current we will return false to say not
//			// upgrade available.
//			// if we find market build is higher we return true to mark that there is an update available.
//			while (marketTokenizer.hasMoreTokens() && currentTokenizer.hasMoreTokens()) {
//				int marketToken = Integer.parseInt(marketTokenizer.nextToken().trim());
//				int currentToken = Integer.parseInt(currentTokenizer.nextToken().trim());
//				// if the market's is higher - then we have an update
//				if (marketToken > currentToken) {
//					return true;
//				}
//				// if the market's is lower - bail we have a newer build than the market's
//				else if (marketToken < currentToken) {
//					return false;
//				}
//				// otherwise - continue checking with next token
//			}
//		}
//		// all tokens were equal
//		return false;
//	}

//	/**
//	 * 
//	 */
//	private void NotifyUpdateAvialable() {
//
//		Logger.d(UPDATES_TAG, "NotifyUpdateAvialable()");
//
//		String tickerText = null;
//		String contentText = null;
//		notificationManager.cancel(UPDATE_AVAILABLE_NOTIFICATION_ID);
//
//		// the text shown on the status bar when notification is sent :
//		tickerText = getString(R.string.updateNotificationTickerText);
//		contentText = getString(R.string.updateNotificationContentText);
//
//		// prepare a notification intent that will be launched when tapping the
//		// notification item
//		Intent notificationIntent = new Intent(Intent.ACTION_VIEW);
//		notificationIntent.setData(Uri.parse("market://details?id=" + getPackageName()));
//		// notificationIntent.setData(Uri.parse("market://details?id=com.att.vvm"));
//		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//
//		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
//				notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
//		Notification notification = new Notification(
//				R.drawable.ic_stat_notify_vvm, tickerText,
//				System.currentTimeMillis());
//
//		notification.setLatestEventInfo(applicationContext,
//				getText(R.string.updateNotificationTitleText), contentText,
//				contentIntent);
//
//		// clear once clicked by user
//		notification.flags |= Notification.FLAG_AUTO_CANCEL;
//		// play the default notification sound
//		notification.defaults |= Notification.DEFAULT_SOUND;
//		notification.defaults |= Notification.DEFAULT_VIBRATE;
//		notification.audioStreamType = Notification.STREAM_DEFAULT;
//		
//
//		notificationManager.cancel(UPDATE_AVAILABLE_NOTIFICATION_ID);
//		notificationManager.notify(UPDATE_AVAILABLE_NOTIFICATION_ID,
//				notification);
//
//		Logger.d(UPDATES_TAG, "NotifyUpdateAvialable() going to notify");
//	}
}
