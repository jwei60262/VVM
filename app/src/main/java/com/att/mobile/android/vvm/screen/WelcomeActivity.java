package com.att.mobile.android.vvm.screen;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.att.mobile.android.infra.utils.AlertDlgUtils;
import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.LoadingProgressBar;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.PermissionUtils;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.ATTM.AttmUtils;
import com.att.mobile.android.vvm.control.SetupController;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.ATTM_STATUS;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.Constants.KEYS;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.ui_components.CompoundTextWithBullet;

import java.util.ArrayList;


/**
 * Created by hginsburg on 21/07/2015.
 */
public class WelcomeActivity extends VVMActivity implements SetupController.OnSetupCallbackListener {

	private static final String TAG = "WelcomeActivity";
	private ModelManager modelManager;

	private SetupController setupController;
	private String mailboxNumber = null;

	private Button btnBeginSetup = null;
	private Button btnCancel = null;
	private Button btnTryAgain = null;
	private Button btnQuit = null;
	private Button btnCallVoicemail = null;
	private Activity mActivity;
	private static String stateString = "N/A";

	private static final int REQUEST_CODE_ENTER_PASSWORD = 1;
	private static final int REQUEST_CODE_OPEN_SETTINGS = 2;
	private static final int REQUEST_CODE_ENTER_GREETINGS = 3;
	private static final int REQUEST_CODE_RESET_PASSWORD = 4;

	private LoadingProgressBar loadingGauge = null;

	private TextView txtSetupRetryHeader;
	private TextView txtSetupRetrySubHeader;
	private CompoundTextWithBullet txtSetupRetry_Sub_1;
	private CompoundTextWithBullet txtSetupRetry_Sub_2;

	private final RotateAnimation rotateAnimation = new RotateAnimation(0.0f,
			360.0f, Animation.RELATIVE_TO_SELF, 0.5f,
			Animation.RELATIVE_TO_SELF, 0.5f);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Logger.i(TAG, "onCreate");
		super.onCreate(savedInstanceState);


		mActivity = this;
		handler = new Handler();
		setupController = SetupController.getInstance(this);

		modelManager = ModelManager.getInstance();
		modelManager.addEventListener(this);

		if ((modelManager.getCurrentSetupState() == Constants.SETUP_STATUS.UNKNOWN) && (PermissionUtils.areRequiredPermissionsGranted())) {
            getCTN();
//				setupController.handleSetup();
		}
	}

	private void getCTN() {
        // This is the start of login process. Start SIM listening here.
        simManager.startListening();
        simManager.startSimValidation();
        
		mailboxNumber = Utils.getCTNFromSim();
		Logger.i(TAG, "getCTN mailboxNumber = " + mailboxNumber);
		setupController.setMailboxNumber(mailboxNumber);
	}

	/**
	 * Starts AT&T messages Launcher Activity
	 *
	 * @return true if no exceptions were caught
	 */
	private boolean launchATTMActivity() {

		boolean result = false;
		try {

			boolean alreayShowedLaunchATTMScreen = modelManager.getSharedPreferenceValue(
					KEYS.DO_NOT_SHOW_LAUNCH_ATTM_SCREEN, Boolean.class, false);
			if (alreayShowedLaunchATTMScreen) {
				result = AttmUtils.launchATTMApplication();
				finish();
			} else {
				Intent startActivityIntent = new Intent(this, ATTMLauncherActivity.class);
				startActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);// This flag ensures all activities on top
				// of the ATTMLauncherActivity are
				// cleared.
				startActivityForResult(startActivityIntent, 0);
				result = true;

			}

		} catch (Exception e) {
			Logger.e(TAG, "launchAttm() - error starting AT&T Messages", e);
			result = false;
		}
		return result;

	}

	@Override
	protected void onResume() {

		Logger.i(TAG, "onResume");
		super.onResume();
		if (Build.VERSION.SDK_INT > 20 && !VVMApplication.isAdminUser(this)) {
			startActivityForResult(new Intent(WelcomeActivity.this, NonAdminUserActivity.class), 0);
		}else{
			if (!resolveLaunchAttm() ) {
				setupController.registerCallback(this);
				showSetupScreen();
			}
		}
	}

	@Override
	protected void onPause() {

		setupController.unregisterCallback();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		// stop call state listener
		stopListening();
		//setupController.unregisterCallback();
		super.onDestroy();
	}

	/**
	 * Listener for the phone state (call state)
	 */
	private final PhoneStateListener phoneStateListener = new PhoneStateListener() {
		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			Logger.d(TAG, ":phoneStateListener state=" + state);
			switch (state) {
				case TelephonyManager.CALL_STATE_IDLE:
					if (stateString.equals("Off Hook")) { // hang up state
						stateString = "Idle";
						modelManager.setCurrentSetupState(Constants.SETUP_STATUS.WAIT_CALL_BINARY_SMS);
						showSetupScreen();
						setupController.handleSetup();
					}
					break;
				case TelephonyManager.CALL_STATE_OFFHOOK:
					stateString = "Off Hook";
					break;
				case TelephonyManager.CALL_STATE_RINGING:
					stateString = "Ringing";
					break;
				default:
					break;
			}
			Logger.d(TAG, "AccountSetupActivity::phoneStateListener => stateString=" + stateString);

			super.onCallStateChanged(state, incomingNumber);
		}

	};
	private TelephonyManager telephonyManager;

	/**
	 * Start listening to phone's CALL_STATE
	 */
	private void startCallStateListener() {
		Logger.d(TAG, "startCallStateListener => start listening to PhoneState");
		telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
		int events = PhoneStateListener.LISTEN_CALL_STATE;
		telephonyManager.listen(phoneStateListener, events);
	}

	/**
	 * Stop listening to phone's CALL_STATE
	 */
	private void stopListening() {
		Logger.d(TAG, "AccountSetupActivity::startCallStateListener => stop listening to PhoneState");
		telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
		telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		Logger.i(TAG, "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode);

		switch (resultCode) {
			case Constants.EVENTS.PERMISSIONS_GRANTED:
				getCTN();
				int currentSetupState = modelManager.getCurrentSetupState();
				if(currentSetupState == Constants.SETUP_STATUS.UNKNOWN){
					showSetupScreen();
				} else {
					proceedToNextSetupState(currentSetupState);
					setupController.handleSetup();
				}
				break;

			case Constants.EVENTS.WELCOME_WIZARD_FINISHED:
				modelManager.setSetupCompleted();
				break;

			case Constants.EVENTS.ENTER_PASSWORD_FINISHED:
			case Constants.EVENTS.PASSWORD_CHANGE_FINISHED:
				if (requestCode == REQUEST_CODE_RESET_PASSWORD) {
					modelManager.setSetupCompleted();
				} else {
					modelManager.setPasswordChangeRequired(Constants.PasswordChangeRequiredStatus.NONE);
					if( !modelManager.isSetupCompleted()) {
							modelManager.setCurrentSetupState(Constants.SETUP_STATUS.INIT_GREETINGS);
					}
				}
				break;
			case Constants.EVENTS.NON_ADMIN_USER:
			case Constants.EVENTS.BACK_FROM_PASSWORD_CHANGE:
			case Constants.EVENTS.ENTER_PASSWORD_CANCELED:
			case Constants.EVENTS.BACK_FROM_GREETINGS:
				finish();
				break;

			default:
				super.onActivityResult(requestCode, resultCode, data);
				break;
		}

	}

	private void showSetupScreen() {
		int currentState = modelManager.getCurrentSetupState();
		Logger.i(TAG, "showSetupScreen currentState=" + currentState + " " + Constants.getSetupStatusString(currentState));

		if (currentState != Constants.SETUP_STATUS.SUCCESS && isTenMinutesPassed()) {
			modelManager.setSharedPreference(Constants.KEYS.PREFERENCE_BEGIN_SETUP_TIME, (long) 0);
			modelManager.setCurrentSetupState(Constants.SETUP_STATUS.UNKNOWN);
			currentState = Constants.SETUP_STATUS.UNKNOWN;
		}

		switch (currentState) {

			case Constants.SETUP_STATUS.UNKNOWN:
			case Constants.SETUP_STATUS.INIT_WITH_MSISDN:
				initWithMsisdnScreen();
				break;

			case Constants.SETUP_STATUS.UNKNOWN_MAILBOX:
				showQuitSetupScreen();
				break;

			case Constants.SETUP_STATUS.WAIT_CALL_BINARY_SMS:
			case Constants.SETUP_STATUS.WAIT_BINARY_SMS1:
			case Constants.SETUP_STATUS.WAIT_BINARY_SMS2:
				initWaitBinarySms(currentState);
				break;

			case Constants.SETUP_STATUS.TRY_MO_SMS_AGAIN:
				showTryAgainScreen();
				break;

			case Constants.SETUP_STATUS.ENTER_EXISTING_PWD:
				startActivityForResult(new Intent(WelcomeActivity.this, EnterExistingPasswordActivity.class), REQUEST_CODE_RESET_PASSWORD);
				break;

			case Constants.SETUP_STATUS.ENTER_PASSWORD:
				startActivityForResult(new Intent(this, ChangePasswordActivity.class), REQUEST_CODE_ENTER_PASSWORD);
				break;
			case Constants.SETUP_STATUS.RESET_PASSWORD:
				startActivityForResult(new Intent(this, ChangePasswordActivity.class), REQUEST_CODE_RESET_PASSWORD);
				break;

            case Constants.SETUP_STATUS.SHOW_CALL_VOICE_MAIL:
            //case Constants.SETUP_STATUS.CALL_VOICE_MAIL:
				showCallVoiceMailScreen();
				break;
			case Constants.SETUP_STATUS.INIT_CALL_VOICE_MAIL:
				showNoMsisdnErrorScreen();

				break;
			case Constants.SETUP_STATUS.NO_VOICE_MAIL_NUMBER:
				showCallVoiceMailScreen();
				showNoVoiceMailDialog();
				break;

			case Constants.SETUP_STATUS.SUCCESS:
				setupController.close();
				Intent intent = new Intent(this, InboxActivity.class);
				boolean needRefresh = modelManager.isNeedRefreshInbox();
				intent.putExtra(Constants.EXTRAS.EXTRA_REFRESH_INBOX, needRefresh);
//				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
				startActivity(intent);
				finish();
				break;

			case Constants.SETUP_STATUS.INIT_GREETINGS:
				startActivityForResult(new Intent(WelcomeActivity.this, GreetingActionsActivity.class), REQUEST_CODE_ENTER_GREETINGS);
				break;

			default:
				Logger.i(TAG, "#### NO SCREEN FOR STATE " + currentState);
		}
	}

	private void showCallVoiceMailScreen() {

		Logger.i(TAG, "showCallVoiceMailScreen");

		setContentView(R.layout.call_voice_mail);
        this.findViewById(android.R.id.content).getRootView().setContentDescription(getString(R.string.account_setup_call_voicemail_content_description));
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            this.findViewById(android.R.id.content).getRootView().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
        }
        setCallVoicemailFontsAndButtons();
		initActionBar(R.string.AccountSetupText, false);
	}

	private void setCallVoicemailFontsAndButtons() {
		this.btnCallVoicemail = (Button) findViewById(R.id.upperButton);
		btnCallVoicemail.setText(R.string.CallVoicemailText);
		this.btnCallVoicemail.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
                modelManager.setCurrentSetupState(Constants.SETUP_STATUS.CALL_VOICE_MAIL);
				setupController.handleSetup();
				modelManager.setSetupStarted(true);
			}
		});

		this.btnQuit = (Button) findViewById(R.id.lowerButton);
		btnQuit.setText(R.string.QuitText);
		btnQuit.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                modelManager.setCurrentSetupState(Constants.SETUP_STATUS.SHOW_CALL_VOICE_MAIL);
                finish();
            }
        });

		//set fonts
		((TextView) findViewById(R.id.call_failed)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular), Typeface.BOLD);
		((TextView) findViewById(R.id.AccountVerificationFailedText2)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
		this.btnCallVoicemail.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Medium));
		this.btnQuit.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Medium));
	}

	private void showNoMsisdnErrorScreen() {

		Logger.i(TAG, "showCallVoiceMailScreen");
		setContentView(R.layout.setup_error_missing_msisdn);

		this.findViewById(android.R.id.content).getRootView().setContentDescription(getString(R.string.account_setup_call_voicemail_content_description));
		setContectDescription();
		setNoMsisdnFontAndButtons();
		rescaleTextSizeOnHugeFont();
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void setContectDescription() {
		(findViewById(R.id.title)).setClickable(false);
		(findViewById(R.id.title)).setFocusable(false);
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			(findViewById(R.id.title)).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		}
	}

	private void setNoMsisdnFontAndButtons() {
		this.btnCallVoicemail = (Button) findViewById(R.id.upperButton);
		this.btnCallVoicemail.setText(R.string.CallVoicemailText);
		this.btnCallVoicemail.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setupController.handleSetup();
				modelManager.setSetupStarted(true);
			}
		});

		this.btnQuit = (Button) findViewById(R.id.lowerButton);
		btnQuit.setText(R.string.QuitText);
		btnQuit.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				modelManager.setCurrentSetupState(Constants.SETUP_STATUS.INIT_CALL_VOICE_MAIL);
				finish();
			}
		});

		// set fonts
		((TextView) findViewById(R.id.welcome_to)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_LightItalic));
		((TextView) findViewById(R.id.missing_msisdn_sub_text)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
		((TextView) findViewById(R.id.missing_msisdn_line1)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
		((TextView) findViewById(R.id.missing_msisdn_line2)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
		((TextView) findViewById(R.id.missing_msisdn_line3)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
		((TextView) findViewById(R.id.missing_msisdn_final_text)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
		this.btnCallVoicemail.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Medium));
		this.btnQuit.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Medium));
		initTitleFont();
		rescaleTextSizeOnHugeFont();
	}


	private void initTitleFont() {
		TextView visualTv = (TextView) findViewById(R.id.title_visual);
		visualTv.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_LightItalic));

		TextView attTv = (TextView) findViewById(R.id.title_att);
		attTv.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_RegularItalic));
	}


	@Override
	public void onUpdateListener(int eventId, ArrayList<Long> messageIDs) {
		switch (eventId) {

			case EVENTS.ATTM_SERVICE_CONNECTED:
				Logger.d(TAG,
						"onUpdateListener() ATTM_SERVICE_CONNECTED");

				handler.post(new Runnable() {
								 @Override
								 public void run() {

									 resolveLaunchAttm();
								 }
							 }
				);
				break;
			default:
				break;
		}
	}

	/**
	 * check the status of ATT messages and redirect if needed, if the status is unknown a gauge will be shown until the
	 * check is done and event is received with updated status
	 */
	private boolean resolveLaunchAttm() {

		Logger.d(TAG, "resolveLaunchAttm()");
		boolean result = false;


		AttmUtils.isUibReadyToReplaceLegacyVvm();
		int attmStatus = modelManager.getAttmStatus();

		// check if ATTM is installed and provisioned
		// if we have no update yet on ATT Messages status - we wait for the check initiated by the VVM application
		if (attmStatus == ATTM_STATUS.UNKNOWN) {

			Logger.d(TAG, "resolveLaunchAttm() ATTM status = UNKNOWN");
			startGauge();
			result = true;
		} else if (attmStatus == ATTM_STATUS.PROVISIONED) {
			Logger.d(TAG,
					"resolveLaunchAttm() ATTM status = PROVISIONED, going to launch ATTM");
			stopGauge();
			if (launchATTMActivity()) {
				Logger.d(TAG, "resolveLaunchAttm() launched ATTM, going to kill VVM");
				result = true;
			}
		} else {
			if (attmStatus == ATTM_STATUS.NOT_INSTALLED) {
				Logger.d(TAG, "resolveLaunchAttm() ATTM status = NOT_INSTALLED, VVM will ran as usual");
			} else if (attmStatus == ATTM_STATUS.INSTALLED_NOT_PROVISIONED) {

				Logger.d(TAG, "resolveLaunchAttm() ATTM status = INSTALLED_NOT_PROVISIONED, VVM will ran as usual");
			}
			stopGauge();
		}
		return result;
	}

	private void showNoVoiceMailDialog() {
		AlertDlgUtils.showDialog(this, R.string.attentionText, R.string.missingVoicemailNumber, R.string.settings_menu_title, 0, true, new AlertDlgUtils.AlertDlgInterface() {
            @Override
            public void handlePositiveButton(View view) {
                try {
                    Intent intent = new Intent(Settings.ACTION_SETTINGS);//(Settings.ACTION_VOICE_INPUT_SETTINGS);
                    WelcomeActivity.this.startActivityForResult(intent, REQUEST_CODE_OPEN_SETTINGS);
                } catch (ActivityNotFoundException anfe) {
                    Logger.d(TAG, "NoVoiceMailDialog.onClick ActivityNotFoundException=" + anfe.getMessage());
                }
                //modelManager.setCurrentSetupState(Constants.SETUP_STATUS.CALL_VOICE_MAIL);
                modelManager.setCurrentSetupState(Constants.SETUP_STATUS.SHOW_CALL_VOICE_MAIL);
            }

            @Override
            public void handleNegativeButton(View view) {
            }
        });
	}

	private void showTryAgainScreen() {

		Logger.i(TAG, "showTryAgainScreen");
		setContentView(R.layout.account_setup_error);
		initTextViews();
		this.findViewById(android.R.id.content).getRootView().setContentDescription(getString(R.string.account_setup_try_again_content_description));
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
			this.findViewById(android.R.id.content).getRootView().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
		}

		setTryAgainScreenTextAndButtons();
		initActionBar(R.string.AccountSetupText, false);
	}


	private void setTryAgainScreenTextAndButtons() {
		txtSetupRetryHeader.setText(R.string.somethingIsntRight);
		txtSetupRetrySubHeader.setText(R.string.AccountSetupRetryTextSub);
		txtSetupRetry_Sub_1.setText(R.string.AccountSetupRetrySubTextLine1);
		txtSetupRetry_Sub_2.setText(R.string.AccountSetupRetrySubTextLine2);

		btnTryAgain = (Button) findViewById(R.id.upperButton);
		btnQuit = (Button) findViewById(R.id.lowerButton);

		btnTryAgain.setText(R.string.TryAgainText);
		btnTryAgain.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                modelManager.setCurrentSetupState(Constants.SETUP_STATUS.WAIT_BINARY_SMS2);
                showSetupScreen();
                setupController.handleSetup();
            }
        });
		btnQuit.setText(R.string.QuitText);
		btnQuit.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //modelManager.setCurrentSetupState(Constants.SETUP_STATUS.CALL_VOICE_MAIL);
                modelManager.setCurrentSetupState(Constants.SETUP_STATUS.SHOW_CALL_VOICE_MAIL);
                finish();
            }
        });
		setAccountErrorXmlFonts();
	}

	private void showQuitSetupScreen() {

		Logger.i(TAG, "showQuitSetupScreen");
		setContentView(R.layout.account_setup_error);
		initTextViews();
		this.findViewById(android.R.id.content).getRootView().setContentDescription(getString(R.string.AccountVerificationFailedTxt));

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
			this.findViewById(android.R.id.content).getRootView().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
		}

		setQuitSetupScreenTextAndButtons();
		initActionBar(R.string.AccountSetupText, false);
	}

	private void setQuitSetupScreenTextAndButtons() {
		txtSetupRetryHeader.setText(R.string.AccountSetupMoSmsRetryHeader);
		txtSetupRetrySubHeader.setText(R.string.AccountSetupMoSmsRetryTextSub);
		txtSetupRetry_Sub_1.setText(R.string.AccountSetupMoSmsRetrySubTextLine1);
		txtSetupRetry_Sub_2.setText(R.string.AccountSetupMoSmsRetrySubTextLine2);

		Button bottomButton = (Button) findViewById(R.id.lowerButton);
		bottomButton.setVisibility(View.GONE);

		btnQuit = (Button) findViewById(R.id.upperButton);
		btnQuit.setText(R.string.QuitText);
		btnQuit.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                modelManager.setCurrentSetupState(Constants.SETUP_STATUS.UNKNOWN);
                finish();
            }
        });
		setAccountErrorXmlFonts();
	}

	private void initTextViews() {
		txtSetupRetryHeader = ((TextView) findViewById(R.id.txtSetupRetryHeader));
		txtSetupRetrySubHeader = ((TextView) findViewById(R.id.txtSetupRetrySubHeader));
		txtSetupRetry_Sub_1 = ((CompoundTextWithBullet) findViewById(R.id.txtSetupRetry_Sub_1));
		txtSetupRetry_Sub_2 = ((CompoundTextWithBullet) findViewById(R.id.txtSetupRetry_Sub_2));
	}

	private void setAccountErrorXmlFonts() {
		txtSetupRetryHeader.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
		txtSetupRetrySubHeader.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
		((Button) findViewById(R.id.upperButton)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Medium));
		((Button) findViewById(R.id.lowerButton)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Medium));
	}

	private void initWaitBinarySms(final int state) {

        Logger.i(TAG, "initWaitBinarySms");
		setupController.setTimerWorking(true);

		setContentView(R.layout.account_setup_loading);
		this.findViewById(android.R.id.content).getRootView().setContentDescription(getString(R.string.account_setup_content_description));
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            this.findViewById(android.R.id.content).getRootView().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
        }
		this.loadingGauge = (LoadingProgressBar) findViewById(R.id.gaugeSetupProgress);

		startGauge(); // show loading animation

		setWaitBinarySmsFontAndButtons(state);
		initActionBar(R.string.AccountSetupText, false);

	}

	private void setWaitBinarySmsFontAndButtons(final int state) {
		this.btnCancel = (Button) findViewById(R.id.btnSetupCancel);
		this.btnCancel.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Medium));
		this.btnCancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Logger.i(TAG, "btnCancel click");
                proceedToNextSetupState(state);
                setupController.cancelTimerWorking();
                showSetupScreen();
            }
        });

		((TextView) findViewById(R.id.SettingAccountText)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
	}

	private void proceedToNextSetupState(int state) {
		if (state == Constants.SETUP_STATUS.WAIT_BINARY_SMS1) {
			modelManager.setCurrentSetupState(Constants.SETUP_STATUS.TRY_MO_SMS_AGAIN);
		} else if ((state == Constants.SETUP_STATUS.WAIT_BINARY_SMS2) || (state == Constants.SETUP_STATUS.WAIT_CALL_BINARY_SMS)) {
			//modelManager.setCurrentSetupState(Constants.SETUP_STATUS.CALL_VOICE_MAIL);
            modelManager.setCurrentSetupState(Constants.SETUP_STATUS.SHOW_CALL_VOICE_MAIL);
		}
	}

    public static void backToPrevState () {

        int state = ModelManager.getInstance().getCurrentSetupState();
        Logger.i(TAG, "backToPrevState state=" + state);
        if ( state == Constants.SETUP_STATUS.WAIT_BINARY_SMS2 ||
                state == Constants.SETUP_STATUS.WAIT_CALL_BINARY_SMS ) {
            Logger.i(TAG, "backToPrevState SET SHOW_CALL_VOICE_MAIL state.");
            ModelManager.getInstance().setCurrentSetupState(Constants.SETUP_STATUS.SHOW_CALL_VOICE_MAIL);
        }
    }

	private void initWithMsisdnScreen() {

		Logger.i(TAG, "initWithMsisdnScreen");
		setContentView(R.layout.welcome);

		initWelcomeScreenUIElements();
		this.btnBeginSetup.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Logger.i(TAG, "btnBeginSetup click.time saved  " + System.currentTimeMillis());
				modelManager.setSetupStarted(true);
				ModelManager.getInstance().setSharedPreference(Constants.KEYS.PREFERENCE_BEGIN_SETUP_TIME, System.currentTimeMillis());

				if (TextUtils.isEmpty(mailboxNumber)) {
					modelManager.setCurrentSetupState(Constants.SETUP_STATUS.INIT_CALL_VOICE_MAIL);
				} else {
					modelManager.setCurrentSetupState(Constants.SETUP_STATUS.WAIT_BINARY_SMS1);
					setupController.handleSetup();
				}
				showSetupScreen();
			}
		});
	}

	private void initWelcomeScreenUIElements() {

		initWelcomeScreenButton();
        initTitleFont();
        initWelcomeScreenTCTextFont();
        rescaleTextSizeOnHugeFont();

        final ScrollView scroll = (ScrollView)findViewById(R.id.scroll_screen);
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

	private void initWelcomeScreenButton() {
		btnBeginSetup = (Button) findViewById(R.id.btnBeginsetup);
		btnBeginSetup.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Medium));
	}

	private void initWelcomeScreenTCTextFont() {

		TextView byClickingTv = (TextView) findViewById(R.id.tvTC);
		byClickingTv.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Regular));

		TextView termsOfServiceTv = (TextView) findViewById(R.id.tvTC1);
		termsOfServiceTv.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Regular));
		termsOfServiceTv.setMovementMethod(LinkMovementMethod.getInstance());


		TextView andTv = (TextView) findViewById(R.id.tvTC2);
		andTv.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Regular));

		TextView privacyPolicyTv = (TextView) findViewById(R.id.tvTC3);
		privacyPolicyTv.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Regular));
		privacyPolicyTv.setMovementMethod(LinkMovementMethod.getInstance());
	}

	private void initLinkView(TextView tvTC) {
		if (tvTC != null) {
			tvTC.setMovementMethod(LinkMovementMethod.getInstance());
		}
	}

	private void rescaleTextSizeOnHugeFont() {
		if (getResources().getConfiguration().fontScale >= 1.3f) {
			TextView tv1 = (TextView) findViewById(R.id.title_att);
			if (tv1 != null) {
				tv1.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.welcome_screen_title_max_size_for_huge_font));
			}
			TextView tv2 = (TextView) findViewById(R.id.title_visual);
			if (tv2 != null) {
				tv2.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.welcome_screen_title_max_size_for_huge_font));
			}
		}
	}

	@Override
	public void onSetupStateChange() {

		Logger.i(TAG, "onSetupStateChange");
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				showSetupScreen();
			}
		});
	}

	/**
	 * Show waiting animation
	 */

	private void startGauge() {
		Logger.d(TAG, "startGauge");
		this.loadingGauge.setVisibility(View.VISIBLE);
		// starts gauge's animation
		createAnimation();
		this.loadingGauge.start();
	}

	/**
	 * Create loading animation
	 */
	private void createAnimation() {
		Logger.d(TAG, "createAnimation");
		this.rotateAnimation.setDuration(1200);
		this.rotateAnimation.setInterpolator(new LinearInterpolator());
		this.rotateAnimation.setRepeatCount(RotateAnimation.INFINITE);
	}

	/**
	 * Stop waiting animation
	 */
	private void stopGauge() {
		Logger.d(TAG, "stopGauge");
		// close animation
		if (this.loadingGauge != null) {
			this.loadingGauge.stop();
			this.loadingGauge.setVisibility(View.INVISIBLE);
		}
	}

	public static boolean isTenMinutesPassed() {
		Long sentTime = ModelManager.getInstance().getSharedPreferenceValue( Constants.KEYS.PREFERENCE_BEGIN_SETUP_TIME, Long.class, (long)0);
		if (sentTime == 0) {
			Logger.d(TAG, "isTenMinutesPassed return false.");
			return false;
		}
		long timeDiff = System.currentTimeMillis() - sentTime;
		Logger.d(TAG, "isTenMinutesPassed timeDiff=" + timeDiff);
		boolean tenMinutes = (timeDiff > 600000);
		Logger.d(TAG, "isTenMinutesPassed return=" + tenMinutes);
		return tenMinutes;
	}
}
