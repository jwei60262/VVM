
package com.att.mobile.android.vvm.screen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.att.mobile.android.infra.sim.SimManager;
import com.att.mobile.android.infra.utils.AlertDlgUtils;
import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.PermissionUtils;
import com.att.mobile.android.infra.utils.SwirlDialogFragment;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.ATTM.AttmUtils;
import com.att.mobile.android.vvm.control.EventListener;
import com.att.mobile.android.vvm.control.OperationsQueue;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.ATTM_STATUS;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.PicassoTools;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * The base class for all VVM application activities.
 */
public class VVMActivity extends AppCompatActivity implements EventListener {
	private static final String TAG = "VVMActivity";
	/**
	 * Holds all possible activity states before getting its focus.
	 */
	protected static class ActivityStateBeforeGettingFocus {
		protected static final int CREATED = 0;
		protected static final int ONLY_PAUSED = 1;
		protected static final int PAUSED_AND_LOST_FOCUS = 2;
		protected static final int ONLY_LOST_FOCUS = 3;
	}


	protected Toolbar toolbar;
	protected TextView toolBarTV;
    protected ImageView toolBarPlayImage;
    protected Handler handler;

	public final static float HIGH_DENSITY = 1.5f;
	public final static float MID_DENSITY = 1.0f;

	// holds activity state before getting its focus
	protected int screenStateBeforeGettingFocus;

	/** holds the instance of the VVM application */
	static VVMApplication vvmApplication = null;
	private ModelManager modelManager;
	protected boolean isMenuButtonExists = true;

	protected SwirlDialogFragment editNameDialog;
    protected SimManager simManager;

    protected boolean contactsChanged = false;

    protected class RunDeleteAsyncTask extends AsyncTask<Void, Void, Void> {

        Long[] messageIDs;

        RunDeleteAsyncTask( Long[] messageIDs ) {
            this.messageIDs = Arrays.copyOf(messageIDs, messageIDs.length) ;
        }

        @Override
        protected Void doInBackground(Void... params) {

            ModelManager.getInstance().setMessagesAsDeleted(messageIDs);
            Long[] messagesUIDs = ModelManager.getInstance().getMessageUIDsToDelete();
            OperationsQueue.getInstance().enqueueDeleteOperation(VVMActivity.this, messagesUIDs);
            return null;
        }

    }


    protected class MarkMessageAsAsyncTask extends AsyncTask<Void, Void, Integer> {

        Long[] mMessageIds;
        int mSavedState;

        public MarkMessageAsAsyncTask(Long[] messageIds, int savedState) {
            mMessageIds = Arrays.copyOf(messageIds, messageIds.length);
            mSavedState = savedState;
        }

        @Override
        protected Integer doInBackground(Void... params) {

            Logger.d(TAG, "MarkMessageAsSaveAsyncTask.doInBackground() start messageId=" + mMessageIds);

            return modelManager.setMessagesSavedState(mMessageIds, mSavedState);
        }

    }

	/**
	 * Called upon activity creation.
	 * 
	 * @param savedInstanceState (Bundle) any previous activity saved instance state.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Logger.d(TAG, "VVMActivity.onCreate()");

		super.onCreate(savedInstanceState);
		// gets the instance of the VVM application
		vvmApplication = VVMApplication.getInstance();
        simManager = SimManager.getInstance(this);
        handler = new Handler();

		// screen will soon get its focus, udates that it was created before getting its focus
		screenStateBeforeGettingFocus = ActivityStateBeforeGettingFocus.CREATED;
		// get model manager instance
		ModelManager.createInstance(getApplicationContext());

		modelManager = ModelManager.getInstance();
		if (Constants.IS_DEMO_VERSION) {
			Utils.addDemoWatermark(this);
		}

		setInitializeSetupState();
	}

	/**
	 * in case of an upgrade from 3.x to 4.x we need to make sure that the setup states are correct since the values changed
	 */
	private void setInitializeSetupState() {
		boolean isFirstTimeLogin4_0 = modelManager.getSharedPreferenceValue(Constants.KEYS.FIRST_LOGIN_4_0, Boolean.class, true);
		boolean setupComplete = modelManager.isSetupCompleted();
		if (isFirstTimeLogin4_0 ){
			 modelManager.setSharedPreference(Constants.KEYS.FIRST_LOGIN_4_0, false);
			 if(setupComplete) {
				 modelManager.setCurrentSetupState(Constants.SETUP_STATUS.SUCCESS);
			 } else {
				 modelManager.setCurrentSetupState(Constants.SETUP_STATUS.UNKNOWN);
			 }
		}
	}

	/**
	 * Called when activity's window focus is changed.
	 * 
	 * @param hasFocus (boolean) true in case the activity has focus, false otherwise.
	 */
	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);

		// in case the screen lost focus
		if (!hasFocus) {
			// in case the activity was paused before losing its focus
			if (screenStateBeforeGettingFocus == ActivityStateBeforeGettingFocus.ONLY_PAUSED) {
				// sets that the screen was paused and lost focus before getting its focus
				screenStateBeforeGettingFocus = ActivityStateBeforeGettingFocus.PAUSED_AND_LOST_FOCUS;
			} else {
				// sets that the screen only lost focus before getting its focus
				screenStateBeforeGettingFocus = ActivityStateBeforeGettingFocus.ONLY_LOST_FOCUS;
			}
		}
	}

	/**
	 * Called upon activity resume.
	 */
	@Override
	protected void onResume() {
		Logger.d(TAG, "VVMActivity.onResume()");
		super.onResume();

        simManager.addEventListener(this);

		if((android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) && !PermissionUtils.areRequiredPermissionsGranted()){

            Logger.i(TAG, "Permissions: Need permissions SetupState=" + modelManager.getCurrentSetupState());
            if ( modelManager.getCurrentSetupState() != Constants.SETUP_STATUS.SUCCESS ) {
                WelcomeActivity.backToPrevState();
            }
			Intent newIntent = new Intent(this, PermissionsActivity.class);
			startActivityForResult(newIntent, PermissionsActivity.PERMISSION_ACTIVITY_RESULT);
			return;

		} else {

            if ( ModelManager.getInstance().getSharedPreferenceValue(Constants.KEYS.SIM_SWAP, Boolean.class, false) ) {
                showSimSwappedDialog();
            }

            // check if attm status has changed
            checkAttmStatus();
            // updates that the application is visible
            vvmApplication.setVisible(true);
            if (Constants.IS_DEMO_VERSION) {
                Utils.addDemoWatermark(this);
            }

            if ( contactsChanged ) {
                refreshContacts();
                contactsChanged = false;
            }
		}
	}

    protected boolean mustBackToSetupProcess () {

        if ( modelManager.isSetupCompleted() && modelManager.getCurrentSetupState() != Constants.SETUP_STATUS.SUCCESS ) {
            // start welcome screen for show reset password screen
            Intent startActivityIntent = new Intent();
            startActivityIntent.setClass(this, WelcomeActivity.class);
            startActivity(startActivityIntent);
            return true;
        }
        return false;
    }

	private void
	checkAttmStatus() {
		// run in another thread to avoid blocking of UI thread
		final Activity thisActivity = this;

		Logger.d(TAG, "VVMActivity.checkAttmStatus() check if need to check ATTM status");
		
		if (modelManager.shouldCheckAttmStatusOnForeground()) {
			
			Logger.d(TAG, "VVMActivity.checkAttmStatus() going to check ATTM Status");
			
			new Thread(new Runnable() {

				@Override
				public void run() {

					// reset the check flag no other onResume methods will not check as well
					modelManager.setCheckAttmStatusOnForeground(false);
					// check if attm status has changes, call welcome activity in order of it to navigate to the right
					// activity
					if (thisActivity.getClass() != WelcomeActivity.class
							&& thisActivity.getClass() != ATTMLauncherActivity.class) {

						int attStatusBeforeCheck = modelManager.getAttmStatus();
						AttmUtils.isUibReadyToReplaceLegacyVvm();
						if (attStatusBeforeCheck != modelManager.getAttmStatus()
								|| modelManager.getAttmStatus() == ATTM_STATUS.PROVISIONED) {

							Intent startActivityIntent = new Intent();
							startActivityIntent.setClass(thisActivity, WelcomeActivity.class);
							startActivityIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

							startActivity(startActivityIntent);
						}
					}
				}
			}).start();
		}
	}

	/**
	 * Called upon activity pause.
	 */
	@Override
	protected void onPause() {
		Logger.d(TAG, "VVMActivity.onPause()");

        simManager.removeEventListener(this);
		// sets that the screen was only paused before getting its focus
		screenStateBeforeGettingFocus = ActivityStateBeforeGettingFocus.ONLY_PAUSED;

		// updates that the application is not visible
		vvmApplication.setVisible(false);

		super.onPause();
	}

    protected void refreshContacts () {

    }

	/**
	 * Called upon activity destroy.
	 */
	@Override
	protected void onDestroy() {
//		new SaveDataSetsTask().execute();

        if ( contactsChanged ) {
            PicassoTools.clearCache(Picasso.with(this));
        }

		super.onDestroy();
	}


//	public static void showSnackBar(String text, int time) {
//		final Toast tag = Toast.makeText(vvmApplication, text,Toast.LENGTH_SHORT);
//
//		tag.show();
//
//		new CountDownTimer(time, 1000)
//		{
//
//		    public void onTick(long millisUntilFinished) {tag.show();}
//		    public void onFinish() {tag.show();}
//
//		}.start();
//	}

	public CharSequence getBodyTextWithLink(String linkableText) {
		String link = getString(R.string.ForgotPswTextLink);
		int startLink = linkableText.indexOf(link);
		int endLink = startLink + link.length();
		SpannableString sbuilder = new SpannableString(linkableText);
		sbuilder.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View arg0) {
                Intent intent = initCall("611");
                startActivity(intent);
            }
        }, startLink, endLink, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

		return sbuilder;
	}

	private  Intent initCall(CharSequence phoneNumber){

			Uri uri = Uri.parse("tel:" + phoneNumber);
			Intent it = new Intent(Intent.ACTION_CALL, uri);
			it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			return it;
	}

//	/**
//	 * @author istelman async task for saving serializable data sets and not blocking the UI
//	 */
//	private static class SaveDataSetsTask extends AsyncTask<Void, Void, Void> {
//
//		@Override
//		protected Void doInBackground(Void... params) {
//			// this will save the Operation Queue tasks.
//			OperationsQueue.getInstance().saveDeleteAndMarkAsReadPendingUIDs();
//			Logger.d(TAG, "VVMActivity:onDestroy() - Saving queue data sets.");
//			return null;
//		}
//	}
	

	/**
	 * Returns whether the device has high density screen.
	 * 
	 * @return (boolean) whether the device has high density screen.
	 */
	protected boolean isHighDensityScreen() {
		DisplayMetrics displayMetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
		Logger.d(TAG, "VVMActivity.isHighDensityScreen() - " + displayMetrics.density);
		return displayMetrics.density == HIGH_DENSITY;
	}

	@Override
	public void onUpdateListener(int eventId, ArrayList<Long> messageIDs) {

        switch (eventId) {

            case EVENTS.START_WELCOME_ACTIVITY:
                Logger.d(TAG, "VVMActivity.onUpdateListener() - START_WELCOME_ACTIVITY");
                // show welcome screen if application is in foreground
                if (((VVMApplication) getApplicationContext()).isVisible()) {
                    startActivity(new Intent(this, WelcomeActivity.class));
                }
                break;

            case EVENTS.SIM_SWAPED:
                Logger.d(TAG, "onUpdateListener() SIM_SWAPED");
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            showSimSwappedDialog();
                        }
                    }
                });
                break;

            case Constants.EVENTS.CONTACTS_CHANGED:
                contactsChanged = true;
                break;

            default:
                Logger.d(TAG, "onUpdateListener() eventId=" + eventId + " no action.");
        }
	}

	@Override
	public void onNetworkFailure() {
		// TODO Auto-generated method stub
	}

	/**
	 * copy text to system clipboard using 2 different methods for ICS and for older SKDs
	 * 
	 * @param text
	 */
//	public void copyTextToClipboard(String text) {
//		// for ICS use modern way of copy to clipboard
////		if (Build.VERSION.SDK_INT > 10) { // Build.VERSION_CODES.GINGERBREAD_MR1 = 10
//
//
//			try {
//				// get the clipboard manager from system
//				Class<?> clipboardManagerClass = Class.forName("android.content.ClipboardManager");
//				Object clipboardManagerObject = getSystemService(CLIPBOARD_SERVICE);
//
//				// Creates a new text clip to put on the clipboard
//				Class<?> clipDataClass = Class.forName("android.content.ClipData");
//				Method methodNewPlainText = clipDataClass.getMethod("newPlainText", new Class[] {
//						CharSequence.class, CharSequence.class
//				});
//				Object clipData = methodNewPlainText.invoke(null, new Object[] {
//						"transcription", text
//				});
//				// Set the clipboard's primary clip.
//				Method methodSetPrimaryClip = clipboardManagerClass.getMethod("setPrimaryClip", clipDataClass);
//				methodSetPrimaryClip.invoke(clipboardManagerObject, clipData);
//				Utils.showToast(R.string.text_copied_toast, Toast.LENGTH_LONG);
//
//			} catch (Exception e) {
//				Log.e(TAG, "Failed to copy transcription to clipboard", e);
//			}
//
////		} else {
////			// use the old way for 2.X SDKs
////			android.text.ClipboardManager clipboard = (android.text.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
////			clipboard.setText(text);
////			VVMActivity.showSnackBar(getString(R.string.text_copied_toast));
////		}
//	}
	
	/**
	 * Show Audio Share menu
	 */
	public static void showAudioShareMenu(Context context, Uri fileUri) {
		
		Logger.d(TAG, "VvmActivity.showAudioShareMenu() Uri = " + fileUri);
		// create "share as" with the attached amr file
		
		Intent intent = new Intent(Intent.ACTION_SEND);
		
		// we may share mp3 files
		
		if (fileUri.toString().endsWith(".mp3")) {
			intent.setType("audio/mp3");
		}else{
			intent.setType("audio/amr");
		}
		intent.putExtra(Intent.EXTRA_STREAM, fileUri);

		Logger.d(TAG, "VvmActivity.showAudioShareMenu() add intent for HTC android.intent.action.SEND_MSG");

		// special case for htc to have MMS in the chooser as well
		Intent htcIntent1 = new Intent("android.intent.action.SEND_MSG");
		htcIntent1.setType(intent.getType());
		htcIntent1.putExtra(Intent.EXTRA_STREAM, fileUri);
		
		Logger.d(TAG, "VvmActivity.showAudioShareMenu() add intent for HTC com.htc.intent.action.SEND_MSG");
		// Lior: added another intent action according to HTC input on 12/16/2012
		Intent htcIntent2 = new Intent("com.htc.intent.action.SEND_MSG");
		htcIntent2.setType(intent.getType());
		htcIntent2.putExtra(Intent.EXTRA_STREAM, fileUri);

		Intent chooser = Intent.createChooser(intent, context.getString(R.string.share_menu_title));
		chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] {htcIntent1, htcIntent2});
		context.startActivity(chooser);
	}
	private static void launchHome(Activity act) {
		Intent mHomeIntent = new Intent(Intent.ACTION_MAIN, null);
		mHomeIntent.addCategory(Intent.CATEGORY_HOME);
		mHomeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		act.startActivity(mHomeIntent);
	}
	public static void exit(Activity act) {
		launchHome(act);
		act.finish();
	}

	/**
	 * Show waiting animation
	 */
	protected void showGauge(String loadingText) {

		FragmentManager fm = getSupportFragmentManager();
		editNameDialog = new SwirlDialogFragment();
		editNameDialog.setBodyText(loadingText);
		editNameDialog.setCancelable(false);
		editNameDialog.show(fm, "fragment_edit_name");
	}


	/**
	 * Stop waiting animation
	 */
	protected  void dismissGauge() {
		if(editNameDialog != null ){
			editNameDialog.dismissAllowingStateLoss();
		}
	}

	protected void initActionBar(int headerId, boolean shouldDisplayBackOption) {

        Logger.d(TAG, "initActionBar shouldDisplayBackOption=" + shouldDisplayBackOption);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
		toolBarTV = (TextView) findViewById(R.id.header_title);
		if (toolbar != null && toolBarTV != null) {

			toolBarTV.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
			toolBarTV.setText(headerId);
			setSupportActionBar(toolbar);
			ActionBar supportActionBar = getSupportActionBar();
			supportActionBar.setDisplayShowTitleEnabled(false);

			supportActionBar.setHomeButtonEnabled(shouldDisplayBackOption);
			supportActionBar.setDisplayHomeAsUpEnabled(shouldDisplayBackOption);

		}
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Logger.i(TAG, "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode);
		if(exitAppDueToMissingPermmision(requestCode,resultCode)){
			exit(this);
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	private boolean exitAppDueToMissingPermmision(int requestCode, int resultCode) {
		if ((requestCode == PermissionsActivity.PERMISSION_ACTIVITY_RESULT) && (resultCode == PermissionsActivity.PERMISSION_DENIED_EXIT_APP)){
			finish();
			return true;
		}
		return false;
	}


    protected void deleteVMs(Long[] mesIDs) {
        Logger.d(TAG, "deleteVMs()");
        (new RunDeleteAsyncTask(mesIDs)).execute();
    }

    protected void markMessagesAs (Long[] mesUIDs, int savedState) {
        Logger.d(TAG, "markMessagesAs() savedState=" + savedState);
        new MarkMessageAsAsyncTask( mesUIDs, savedState).execute();
    }

    protected   void showSavedDialog(final Activity activity, boolean isMulipleMessages) {
		int titleId;
		int bodyId;
		if(isMulipleMessages){
			titleId = R.string.messages_saved_dialog_header;
			bodyId = R.string.messages_saved_dialog_body;
		}else{
			titleId = R.string.message_saved_dialog_header;
			bodyId = R.string.message_saved_dialog_body;
		}
        if(!ModelManager.getInstance().getSharedPreferenceValue(Constants.DO_NOT_SHOW_SAVED_DIALOG_AGAIN, Boolean.class, false)){

            AlertDlgUtils.showDialogWithCB(activity,titleId, bodyId, R.string.do_not_show_again, R.string.ok_got_it_caps, R.string.saved_dialog_go_to_saved, new AlertDlgUtils.AlertDlgInterface() {
				@Override
				public void handlePositiveButton(View view) {
                    goToGotItScreen();
				}

				@Override
				public void handleNegativeButton(View view) {
					goToInboxSavedTab();
				}
			});
        }
    }

    protected void goToGotItScreen () {
        // Default implementation is empty
    }

    protected void goToInboxSavedTab () {

        Intent startActivityIntent = new Intent(this, InboxActivity.class);
        startActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivityIntent.setAction(Constants.ACTION_GOTO_SAVED);
        startActivity(startActivityIntent);
        finish();
    }


    protected void showSimSwappedDialog() {

        AlertDlgUtils.showDialog(VVMActivity.this, R.string.simSwapDialogTitle, R.string.simSwapDialogText, R.string.ok_got_it_caps, R.string.cancel, false, new AlertDlgUtils.AlertDlgInterface() {
            @Override
            public void handlePositiveButton(View view) {
                {
                    try {
                        simManager.clearUserDataOnSimSwap();
                        Intent intent = new Intent(VVMActivity.this, WelcomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }
            }

            @Override
            public void handleNegativeButton(View view) {
                {
                    finish();
                    exit(VVMActivity.this);
                }
            }

        });
    }

}
