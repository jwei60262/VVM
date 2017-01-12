package com.att.mobile.android.vvm.model;


import com.att.mobile.android.vvm.R;

public class Constants {

	public static final String SHARED_FILE_TIME_FORMAT = "%Y-%m-%d.%H.%M.%S";
	
	public static interface TRANSACTIONS{
		public static final byte TRANSACTION_LOGIN                 			= 0;
		public static final byte TRANSACTION_LOGOUT                			= 1;
		public static final byte TRANSACTION_SELECT_INBOX          			= 2;
		public static final byte TRANSACTION_DELETE                			= 3;
		public static final byte TRANSACTION_FETCH_AUDIO_ATTACHMENT  		= 4;
		public static final byte TRANSACTION_EXPUNGE               			= 5;
		public static final byte TRANSACTION_FETCH_HEADERS					= 6;
		public static final byte TRANSACTION_READ							= 7;
		public static final byte TRANSACTION_NOOP							= 8;
		public static final byte TRANSACTION_GET_METADATA					= 9;
		public static final byte TRANSACTION_SEND_GREETING_REQUEST			= 10;
		public static final byte TRANSACTION_SEND_GREETING_DATA				= 11;
		public static final byte TRANSACTION_SET_METADATA					= 12;
		public static final byte TRANSACTION_FETCH_GREETIGNS_BODIES			= 13;
		public static final byte TRANSACTION_TUI_SKIP						= 14;
		public static final byte TRANSACTION_XCHANGE_TUI_PASSWORD			= 15;
		public static final byte TRANSACTION_SET_METADATA_CLIENT_ID			= 16;
		public static final byte TRANSACTION_FETCH_TRANSCRIPTION = 17;
		public static final byte TRANSACTION_FETCH_BODYSTRUCTURE_LIST = 18;
		public static final byte TRANSACTION_GETQUOTA = 19;
	}
	
	public static interface ACTIONS{ 
		public static final String ACTION_LAUNCH_INBOX_FROM_NOTIFICATION	= "com.att.mobile.android.vvm.LAUNCH_INBOX_FROM_NOTIFICATION";
		public static final String ACTION_LAUNCH_PLAYER_FROM_NOTIFICATION	= "com.att.mobile.android.vvm.LAUNCH_PLAYER_FROM_NOTIFICATION";
		public static final String ACTION_CLEAR_NOTIFICATION	= "com.att.mobile.android.vvm.INTENT_CLEAR_NOTIFICATION";
		public static final String ACTION_CLEAR_UPDATE_NOTIFICATION	= "com.att.mobile.android.vvm.INTENT_CLEAR_UPDATE_NOTIFICATION";
		public static final String ACTION_CLEAR_ALL_NOTIFICATIONS = "com.att.mobile.android.vvm.INTENT_CLEAR_ALL_NOTIFICATIONS";
		public static final String ACTION_UPDATE_NOTIFICATION	= "com.att.mobile.android.vvm.ACTION_UPDATE_NOTIFICATION";
		public static final String ACTION_UPDATE_NOTIFICATION_AFTER_REFRESH	= "com.att.mobile.android.vvm.ACTION_UPDATE_NOTIFICATION_AFTER_REFRESH";
		public static final String ACTION_NEW_SMS_NOTIFICATION = "com.att.mobile.android.vvm.ACTION_NEW_MESSAGE_NOTIFICATION";
		public static final String ACTION_BOOT_COMPLETED = "com.att.mobile.android.vvm.BOOT_COMPLETED";
		public static final String ACTION_NEW_UNREAD_MESSAGE = "com.att.mobile.android.vvm.NEW_UNREAD_MESSAGE";
		public static final String ACTION_MAILBOX_CREATED	= "com.att.mobile.android.vvm.MAILBOX_CREATED";
		public static final String ACTION_PASSWORD_MISSMATCH	= "com.att.mobile.android.vvm.ACTION_PASSWORD_MISSMATCH";
		// action to launch at&t messages from VVM
		public final static String ACTION_LAUNCH_ATTM = "com.att.android.ACTION_LAUNCH_ATTM";
	}
	
	//TODO - Royi - move this to operationsQueue
	public static interface EVENTS
    {
                public static final int NEW_MESSAGE = 1;
                public static final int MESSAGE_READ_STATE_CHANGED = 2;
                public static final int MESSAGE_MARKED_AS_SAVED = 3;
                public static final int MESSAGE_FILE_DOWNLOADED = 4;
                public static final int MESSAGE_TRANSCRIPTION_DOWNLOADED = 5;
				public static final int MARK_AS_DELETED_FINISHED = 6;
                public static final int MESSAGE_FILE_DOWNLOAD_ERROR = 7;
                public static final int MESSAGE_MARKED_AS_UNSAVED = 8;
                public static final int DELETE_FINISHED = 10;
                public static final int SIM_SWAPED = 11;
                public static final int SIM_VALID = 12;
                public static final int RETRIEVE_MESSAGES_STARTED = 21;
                public static final int RETRIEVE_HEADERS_FINISHED = 22;
                public static final int RETRIEVE_BODIES_FINISHED = 23;
                
                public static final int GET_METADATA_GREETING_DETAILS_FINISHED = 24;
                public static final int GET_METADATA_EXISTING_GREETINGS_FINISHED = 25;
                
                public static final int GET_METADATA_GREETING_FAILED = 26;
                public static final int GET_METADATA_PASSWORD_FINISHED = 27;
                
                public static final int IDENTIFY_USER_STARTED = 28;
                public static final int IDENTIFY_USER_FINISHED = 29;
                public static final int IDENTIFY_USER_FAILED = 30;
                
                public static final int INBOX_STARTED = 31;
                public static final int INBOX_FINISHED = 32;
                
                
                public static final int SET_METADATA_STARTED = 33;
                public static final int SET_METADATA_FINISHED = 34;
                public static final int SET_METADATA_FAILED = 35;
                
                public static final int SIM_STATED_CHANGED = 36; 
                
                public static final int SET_METADATA_PASSWORD_FINISHED = 37;
                
                public static final int PASSWORD_CHANGE_FINISHED = 38;
                public static final int PASSWORD_CHANGE_FAILED = 39;
                
                public static final int SELECT_INBOX_FINISHED_WITH_NO_MESSAGES = 40;
                
                public static final int RETRIEVE_BODIES_FAILED_NOT_ENOUGH_SPACE = 41;
                
                public static final int MESSAGES_ALLMOST_FULL 	= 42;
                public static final int MESSAGES_FULL 			= 43;

                
                /** events IDs for greeting upload */
                public static final int GREETING_UPLOAD_SUCCEED	= 44;
                public static final int GREETING_UPLOAD_FAILED 	= 45;
                
                public static final int SET_METADATA_GREETING_FINISHED = 46;
                public static final int SET_METADATA_GREETING_FAILED = 47;
                
                public static final int CONTACTS_CHANGED = 48;
                
                public static final int LOGIN_FAILED_DUE_TO_WRONG_PASSWORD = 49;
                public static final int LOGIN_FAILED = 50;
                
                public static final int PASSWORD_ASYNC_SET_CANCELED = 52;
                
                public static final int ENTER_PASSWORD_FINISHED = 53;
                
                //connection
                public static final int CONNECTION_LOST = 53;
                public static final int CONNECTION_CONNECTED = 54;
                
                public static final int LOGIN_SUCCEEDED = 55;
                
                
                public static final int ENTER_PASSWORD_CANCELED = 56;
                
                public static final int REFRESH_UI = 57;
                
                public static final int BACK_FROM_PASSWORD_CHANGE = 58;
                
                public static final int XCHANGE_TUI_PASSWORD_FINISHED_SUCCESSFULLY = 59;
                public static final int XCHANGE_TUI_PASSWORD_FAILED = 60;
                
                public static final int WELCOME_WIZARD_FINISHED = 61;
                public static final int BACK_FROM_CONFIGURE_ALL_DONE = 62;
                
                public static final int FETCH_BODIES_MAX_RETRIES = 63;
                
                public static final int START_WELCOME_ACTIVITY = 64;
                
                public static final int ATTM_SERVICE_CONNECTED = 65;
                // to be able to close the welcome activity once launcher is finished
                public static final int BACK_FROM_ATTM_LAUNCHAR = 66;
                public static final int NON_ADMIN_USER = 67;

                public static final int NETWORK_CONNECTION_LOST = 70;
                
                public static final int NETWORK_CONNECTION_RESTORED = 71;
				public static final int GREETING_INIT_FINISHED = 72;
                public static final int BACK_FROM_GREETINGS = 73;

				public static final int PERMISSIONS_GRANTED = 74;
				public static final int EXIT_APP = 75;
				public static final int MESSAGE_CONTACT_INFO_UPDATED = 76;
				public static final int UI_UPDATED = 77;
				public static final int PLAYER_PAUSED = 78;

	}

	
	
	public static interface EXTRAS{
		public static final String EXTRA_NEW_MESSAGE_ROW_ID	= "extra_new_message_row_id";
		public static final String EXTRA_SHORTCUT_DUPLICATE = "duplicate";
		public static final String EXTRA_REFRESH_INBOX = "refresh_inbox";
	}

	
	/**
	 * The GETMETADATA and SETMETADATA commands are supported for the �INBOX�
		mailbox with the following variables:
		all values are added with a space to prepare a variable to be appended to the requested variables list of the imap4 get meta data command
	 */
	public static interface METADATA_VARIABLES{
		
		public static final String GreetingType = "/private/vendor/vendor.alu/messaging/GreetingType";
		public static final String GreetingTypesAllowed = "/private/vendor/vendor.alu/messaging/GreetingTypesAllowed";
		public static final String MinPasswordDigits = "/private/vendor/vendor.alu/messaging/MinPasswordDigits";
		public static final String MaxPasswordDigits = "/private/vendor/vendor.alu/messaging/MaxPasswordDigits";
		public static final String MinMessageLength = "/private/vendor/vendor.alu/messaging/MinMessageLength";
		public static final String MaxMessageLength = "/private/vendor/vendor.alu/messaging/MaxMessageLength";
		public static final String MaxGreetingLength = "/private/vendor/vendor.alu/messaging/MaxGreetingLength";
		public static final String MaxRecordedNameLength = "/private/vendor/vendor.alu/messaging/MaxRecordedNameLength";
		public static final String GreetingsPersonal = "/private/vendor/vendor.alu/messaging/Greetings/Personal";
		public static final String GreetingsStandartWithName = "/private/vendor/vendor.alu/messaging/Greetings/StandartWithName";
		public static final String GreetingsStandartWithNumber = "/private/vendor/vendor.alu/messaging/Greetings/StandartWithNumber";
		public static final String GreetingsPersonalBusy = "/private/vendor/vendor.alu/messaging/Greetings/PersonalBusy";
		public static final String GreetingsPersonalNoAnswer = "/private/vendor/vendor.alu/messaging/Greetings/PersonalNoAnswer";
		public static final String GreetingsExtendedAbsence = "/private/vendor/vendor.alu/messaging/Greetings/ExtendedAbsence";
		public static final String ChangeableGreetings = "/private/vendor/vendor.alu/messaging/ChangeableGreetings";
		public static final String RecordedName = "/private/vendor/vendor.alu/messaging/RecordedName";
		public static final String TUIPassword = "/private/vendor/vendor.alu/messaging/TUIPassword";
		public static final String AudioAcceptTypes = "/private/vendor/vendor.alu/messaging/AudioAcceptTypes";
		public static final String EAGStatus = "/private/vendor/vendor.alu/messaging/EAGStatus";
		public static final String ClientID = "/private/vendor/vendor.alu/messaging/ClientID";
	}
	
	public final static int WELCOME_MESSAGE_ID = Integer.MAX_VALUE; 
	
	public static final boolean IS_DEMO_VERSION = false;
	//public static final String SERVER_DATE_TIME_FORMAT = "E, dd MMM yyyy HH:mm:ss Z";
	
//	public static final String LOG_TAG = "com.att.mobile.android.vvm";
	
	//this string send in any IMAP4 command it is important to have space in the end of the String 
	public final static String IMAP4_TAG_STR = "ATT_VVM__ ";
	
	public final static String BACKUP_DATE_FORMAT = "MM/dd"; // defined according to Nexus S (should be the target device default)
	public final static String TIME_FORMAT_24 = "kk:mm";
	public final static String TIME_FORMAT_12 = "hh:mm AA";
	public final static String EXACT_DAY_OF_WEEK_FORMAT = "EEEE";
	public final static String SHORTCODE_FOR_MO_SMS = "94183567";
	
//	public final static long[] VIBRATE_PATTERN = new long[]{0, 1000, 500, 1000, 500};
// N. Slesuratin - the file name was changed to handle both old and new welcome message - US31911 "new welcome message"
	public static final String WELCOME_MESSAGE_FILE_NAME = "welcome_amr_new.amr";
	
	public static class KEYS
	{
            public static final String PREFERENCE_FILE_NAME = "vvmPreferences"; 
            
            /* greetings related keys */
            public static final String PREFERENCE_DEFAULT_GREETING = "defaultGreetingPref";
            public static final String PREFERENCE_NAME_GREETING = "nameGreetingPref";
            public static final String PREFERENCE_CUSTOM_GREETING = "customGreetingPref";
             
            /* these prefs are temp until we have a configuration SMS coming from the server with host, port, user name */
            public static final String PREFERENCE_HOST = "hostPref";
            public static final String PREFERENCE_PORT = "portPref";
            public static final String PREFERENCE_SSL_PORT = "sslPortPref";
            public static final String PREFERENCE_MAILBOX_NUMBER = "userPref";
            public static final String PREFERENCE_MAILBOX_STATUS = "mailboxStatus";
           public static final String PREFERENCE_PASSWORD = "passwordPref";
            public static final String PREFERENCE_TOKEN = "tokenPref";
            public static final String PREFERENCE_DEBUG_SSL_ON = "debugSslOnPref";
            public static final String WATSON_TOKEN = "watsonToken";
            public static final String PREFERENCE_BEGIN_SETUP_TIME = "mosmsTimePref";
           
            public static final String PREFERENCE_TIMER_DEFAULT = "timerDefaultPref";
           
            /* registration key */
            public static final String ALTERNATIVE_PORT = "altPort";
            
            /* change password flag */
            public static final String PASSWORD_CHANGE_REQUIRED_STATUS = "PasswordChangeRequiredStatus";
             
            /* welcome message flag */
            public static final String IS_WELCOME_MESSAGE_INSERTED = "isWelcomeMessageInserted";
            
            /* first time use */
            public static final String IS_FIRST_USE = "isFirstUse";

            /* was inbox refreshed*/
            public static final String WAS_INBOX_REFRESHED = "wasInboxRefreshed";

		/* need refresh inbox */
		public static final String NEED_REFRESH_INBOX = "needRefreshInbox";

		/* is setup started*/
            public static final String IS_SETUP_STARTED = "isSetupStarted";

            /* is setup completed*/
            public static final String IS_SETUP_COMPLETED = "isSetupCompleted";

            public static final String CURRENT_SETUP_STATE = "currentSetupState";
           
            /* change sim */
            public  static final String SIM_ID = "simId";
            public static final String SIM_SWAP = "simSwap";
            
            /* notification on reboot */
            public static final String DID_NOTIFICATIONS_EXIST_BEFORE_REBOOT = "didNotificationsExistBeforeReboot";
            
            /* save the checkbox of create shortcut in the cofigure all done screen during the setup process */
            public static final String CREATE_SHORTCUT_CHECKBOX = "createShortcutCheckbox";
            
            public static final String ATTM_STATUS = "isAttmInstalled";
            
            public static final String SHOULD_CHECK_ATTM_STATUS_ON_FOREGROUND = "shouldCheckAttmStatusOnForeground";
            
            /* not in use */
            public static final String NOTIFY_SETUP_REQUIRED_COUNTER = "notifySetupRequiredCounter";
            
            public static final String AVAILABLE_VERSION_ON_MARKET = "availableVersionOnMarket";
            
            public static final String LAST_UPDATE_CHECK_DATE = "lastUpdateCheckDate";

            public static final String DO_NOT_SHOW_LAUNCH_ATTM_SCREEN = "showLaunchAttmScreenOnlyOnce";

		/**
		 * Since the setup states were changed on version 4.0 we set a flag so we will now to initiate the setup state
		 */
			public static final String FIRST_LOGIN_4_0 = "first_login_4_0";



		public static final String PKEY = ".ldSignature";
        	public static final String SIMULATE_TOKEN_ERROR = "simulatetokenerror";
        	public static final String SIMULATE_TRANSL_ERROR = "simulatetranslerror";
        	public static final String TokenErrorCode = "TokenErrorCode";
        	public static final String TranslErrorCode = "TranslErrorCode";
        	public static final String TokenRetryFail = "TokenRetryFail";
        	public static final String TranslRetryFail = "TranslRetryFail";
        	public static final String MinConfidence = "MinConfidence";
       
            public static final String ATTM_JSON_PASSWORD_KEY = "Password";
            public static final String ATTM_JSON_HOST_KEY = "Host";
            public static final String ATTM_JSON_TOKEN_KEY = "Token";
            public static final String ATTM_JSON_MAILBOX_KEY = "MailBox";
            public static final String ATTM_JSON_PORT_KEY = "Port";
            public static final String ATTM_JSON_SSL_PORT_KEY = "SSLPort";
    		public static final String MAX_MESSAGES	 = "MaxMessages"; //							= 40;
    		public static final String MAX_MESSAGES_WARNING	= "MaxMessagesWarning" ; //					= 37;

		public static String PERMISSION_EVER_REQUESTED = "pref_permissions_ever_requested";
	}
	
	public static interface SETUP_STATUS {
		public static final int UNKNOWN 				= -1;
		public static final int INIT_WITH_MSISDN		= 1;
		public static final int WAIT_BINARY_SMS1		= 3;
		public static final int WAIT_BINARY_SMS2		= 4;
		public static final int WAIT_CALL_BINARY_SMS	= 5;
		public static final int TRY_MO_SMS_AGAIN		= 6;
		public static final int CALL_VOICE_MAIL			= 7;
		public static final int NO_VOICE_MAIL_NUMBER	= 8;
		public static final int ENTER_EXISTING_PWD		= 9;
		public static final int ENTER_PASSWORD			= 10;
		public static final int INIT_GREETINGS			= 12;
		public static final int SUCCESS					= 13;
		public static final int RESET_PASSWORD			= 14;
		public static final int INIT_CALL_VOICE_MAIL	= 15;
		public static final int UNKNOWN_MAILBOX			= 17;
        public static final int SHOW_CALL_VOICE_MAIL    = 18;
	}

	public static String getSetupStatusString ( int setupStatus ) {

		String setupStatusString = "NULL";

		switch (setupStatus) {
			case SETUP_STATUS.UNKNOWN:
				setupStatusString = "UNKNOWN";
				break;
			case SETUP_STATUS.INIT_WITH_MSISDN:
				setupStatusString = "INIT_WITH_MSISDN";
				break;
			case SETUP_STATUS.WAIT_BINARY_SMS1:
				setupStatusString = "WAIT_BINARY_SMS1";
				break;
			case SETUP_STATUS.WAIT_BINARY_SMS2:
				setupStatusString = "WAIT_BINARY_SMS2";
				break;
			case SETUP_STATUS.WAIT_CALL_BINARY_SMS:
				setupStatusString = "WAIT_CALL_BINARY_SMS";
				break;
			case SETUP_STATUS.TRY_MO_SMS_AGAIN:
				setupStatusString = "TRY_MO_SMS_AGAIN";
				break;
			case SETUP_STATUS.CALL_VOICE_MAIL:
				setupStatusString = "CALL_VOICE_MAIL";
				break;
			case SETUP_STATUS.INIT_CALL_VOICE_MAIL:
				setupStatusString = "INIT_CALL_VOICE_MAIL";
				break;
			case SETUP_STATUS.NO_VOICE_MAIL_NUMBER:
				setupStatusString = "NO_VOICE_MAIL_NUMBER";
				break;
			case SETUP_STATUS.ENTER_PASSWORD:
				setupStatusString = "ENTER_PASSWORD";
				break;
			case SETUP_STATUS.ENTER_EXISTING_PWD:
				setupStatusString = "ENTER_EXISTING_PWD";
				break;
			case SETUP_STATUS.SUCCESS:
				setupStatusString = "SUCCESS";
				break;
			case SETUP_STATUS.RESET_PASSWORD:
				setupStatusString = "RESET_PASSWORD";
				break;
			case SETUP_STATUS.UNKNOWN_MAILBOX:
				setupStatusString = "UNKNOWN_MAILBOX";
				break;
            case SETUP_STATUS.SHOW_CALL_VOICE_MAIL:
                setupStatusString = "SHOW_CALL_VOICE_MAIL";
                break;
			default:
				setupStatusString = "UNKNOWN";
		}
		return setupStatusString;
	}
	/**
	 * Holds the possible change password required statuses
	 */
	public static interface ATTM_STATUS
	{
		public static final int UNKNOWN = -1;
		public static final int NOT_INSTALLED = 0;
		public static final int INSTALLED_NOT_PROVISIONED = 1;
		public static final int PROVISIONED = 2;
	}
	
	/**
	 * Holds the possible change password required statuses
	 */
	public static interface PasswordChangeRequiredStatus
	{
		public static final int NONE = -1;
		public static final int CHANGED_IN_TUI = 1;
		public static final int TEMPORARY_PASSWORD = 2;
		public static final int RESET_BY_ADMIN = 3;
		public static final int PASSWORD_MISSING = 4;
	}
	
	// ALU text attachment - we wan to ignore that attachment
	// "Additional information on .lvp audio files can be found at http://www.alcatel-lucent.com/mvp"
	public static final String ALU_LINK_TEXT = "Additional information on .lvp audio files can be found at http://www.alcatel-lucent.com/mvp";
	
	public static interface MessageFilter {

		public static final int TYPE_ALL = 1;
		public static final int TYPE_UNREAD = 2;
		public static final int TYPE_SAVED = 3;
	}
	
	// constants of AT&T Messages application
	public static interface ATTM {
		public final static String PACKAGE_NAME = "com.att.android.mobile.attmessages";
		public final static String RECEIVER_CLASS_NAME = "com.att.uinbox.syncmanager.LaunchByVvmReceiver";
	}

	public static final int DEFAULT_MAX_MESSAGES = 40;

	public static final int DEFAULT_MAX_MESSAGES_WARNING = 3;
	public static final String DEFAULT_MIN_CONFIDENCE_LEVEL = "0.0";

	public static final String SP_KEY_IS_CONNECTED_TO_INTERNET = "com.att.isconnectedtointernet";


    public static final String INTENT_DATA_USER_PHONE = "intent.data.user.phone";
    public static final String INTENT_DATA_FILTER_TYPE = "intent.data.filter.type";
    public static final String INTENT_DATA_USER_NAME = "intent.data.user.name";
    public static final String INTENT_DATA_USER_PHOTO_URI = "intent.data.user.photo.uri";



	public static final String DO_NOT_SHOW_SAVED_DIALOG_AGAIN	= "doNotShowSavedDialogAgain" ;
	public static final String ACTION_GOTO_SAVED = "go_to_saved";


	public static int[] DEFAULT_AVATAR_IDs = {
			R.drawable.no_avatar_purple,
			R.drawable.no_avatar_blue,
			R.drawable.no_avatar_green,
			R.drawable.no_avatar_orange,
			R.drawable.no_avatar_orange
	};

	public static int[] DEFAULT_AVATAR_COLORS= {
			R.color.purple,
			R.color.blue,
			R.color.green,
			R.color.orange_light,
			R.color.orange_light
	};


	public enum AVATAR_IND { PURPLE, BLUE, GREEN, ORANGE, DEFAULT };

	public static int[] AVATAR_FOR_LAST_NUMBER_ARR = {
			AVATAR_IND.ORANGE.ordinal(),   // 0
			AVATAR_IND.ORANGE.ordinal(),   // 1
			AVATAR_IND.ORANGE.ordinal(),   // 2
			AVATAR_IND.BLUE.ordinal(),     // 3
			AVATAR_IND.BLUE.ordinal(),     // 4
			AVATAR_IND.BLUE.ordinal(),     // 5
			AVATAR_IND.GREEN.ordinal(),    // 6
			AVATAR_IND.GREEN.ordinal(),    // 7
			AVATAR_IND.PURPLE.ordinal(),   // 8
			AVATAR_IND.PURPLE.ordinal(),   // 9
	};

}
