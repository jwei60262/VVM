package com.att.mobile.android.vvm.screen;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.control.OperationsQueue;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.screen.AudioRecorderActivity.IntentExtras;

import java.util.ArrayList;

/**
 * This activity is the main entrance for the application.
 * 
 * @author Mark Koltnuk
 * 
 */
public class SetupCompleteActivity extends VVMActivity {

	private TextView personalGreeting = null;
	private View whatIsLayout = null;
	private Button btnNext = null;
	private static ProgressDialog greetingsGauge = null;
	private Boolean isCanceled = true;
	private static final String TAG = "SetupCompleteActivity";

	/**
	 * Fires when a button is clicked
	 */
	private final OnClickListener buttonClickListener = new OnClickListener() {
		/**
		 * Fires when the next button is clicked
		 */
		@Override
		public void onClick(View view) {
			int event = (view == btnNext) ? EVENTS.WELCOME_WIZARD_FINISHED
					: EVENTS.BACK_FROM_CONFIGURE_ALL_DONE;
			setResult(event);
			addShortcut();
			finish();
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// set content
		setContentView(R.layout.configure_all_done);
		this.findViewById(android.R.id.content).getRootView().setContentDescription(getString(R.string.ConfigureAllDoneHeader));


		btnNext = (Button) findViewById(R.id.btnNext);
		btnNext.setOnClickListener(buttonClickListener);

		boolean isExistingMailBox = ModelManager.getInstance().getMailBoxStatus().equals("I") ? true : false;

		personalGreeting = (TextView) findViewById(R.id.personalGreeting);
		whatIsLayout = (View) findViewById(R.id.whatIsLayout);
		View setGreetings = (View) findViewById(R.id.setupGreetingsLayout);
		if(isExistingMailBox){
			setGreetings.setVisibility(View.GONE);
		} else {
			personalGreeting.setText(R.string.ConfigureAllDoneGreetingLink);
			whatIsLayout.setContentDescription(getString(R.string.ConfigureAllDoneGreetingLinkButton));
			setGreetings.setVisibility(View.VISIBLE);
		}
		whatIsLayout.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				getGreetingsDetails();
			}
		});
	}

	@Override
	protected void onResume() {

		OperationsQueue.getInstance().addEventListener(this);
		super.onResume();
		ModelManager.getInstance().addEventListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		ModelManager.getInstance().removeEventListener(this);
	}

	@Override
	protected void onDestroy() {
		Logger.d(TAG, "SetupCompleteActivity.onDestroy()");
		OperationsQueue.getInstance().removeEventListener(this);
		super.onDestroy();
	}

	/**
	 * Show loading gauge and enqueue the GetGreetingsDetailsOperation
	 */
	private void getGreetingsDetails() {
		Logger.d(TAG,
				"SetupCompleteActivity.getGreetingsDetails()");

		// we have data connection
		if (Utils.isNetworkAvailable()) {
			startGauge();
			// enqueues a get greetings details operation to the operations
			// queue
			OperationsQueue.getInstance().enqueueGetGreetingsDetailsOperation();
		} else {
			new AlertDialog.Builder(this)
					.setTitle(getResources().getString(R.string.attentionText))
					.setMessage(
							getResources().getString(R.string.noDataConnectionToast))
					.setIcon(android.R.drawable.ic_dialog_alert).create()
					.show();
		}
	}

	/**
	 * Show waiting animation
	 */
	private void startGauge() {
		Logger.d(TAG, "SetupCompleteActivity.startGauge");
		greetingsGauge = ProgressDialog.show(this, null, getString(R.string.pleaseWait), true, true);
		isCanceled = false;
		greetingsGauge.setCanceledOnTouchOutside(false);
		greetingsGauge.setOnCancelListener(new OnCancelListener() {
			/**
			 * Fires when back is pressed whle the gauge is shown. It means that
			 * the user is trying to cancel an async operation to the server.
			 */
			@Override
			public void onCancel(DialogInterface dialog) {
				isCanceled = true;
			}
		});
	}

	/**
	 * Stop waiting animation
	 */
	private void stopGauge() {
		Logger.d(TAG, "SetupCompleteActivity.stopGauge");
		if (greetingsGauge != null && greetingsGauge.isShowing()) {
			try {
				greetingsGauge.dismiss();
			} catch (Exception e) {
				Log.e(TAG, e.getMessage(), e);
			}
			greetingsGauge = null; // just in case dismiss did not do the job
		}
	}

	@Override
	public void onBackPressed() {
		Logger.d(TAG, "onBackPressed()");
		setResult(EVENTS.BACK_FROM_CONFIGURE_ALL_DONE);
		VVMActivity.exit(this);
		finish();
	}

	@Override
	public void onUpdateListener(final int eventId, ArrayList<Long> messageIDs) {
		// holds the context as final value for later use
		final Context context = this;

		handler.post(new Runnable() {
			@Override
			public void run() {
				// in case getting greetings meta data was canceled by the user,
				// do
				// nothing
				if (isCanceled) {
					return;
				}

				switch (eventId) {

				case EVENTS.GET_METADATA_GREETING_FAILED:
						Logger.d(TAG, "SetupCompleteActivity.onUpdateListener() GET_METADATA_GREETING_FAILED");

					// stop loading animation
					stopGauge();
					break;

				case EVENTS.GET_METADATA_GREETING_DETAILS_FINISHED:
						Logger.d(TAG, "SetupCompleteActivity.onUpdateListener() GET_METADATA_GREETING_DETAILS_FINISHED");
					if (ModelManager.getInstance().getMetadata() != null) {
						// enqueues a get existing greetings operations to the
						// operations queue
						OperationsQueue.getInstance()
						.enqueueGetExistingGreetingsOperation();
						return;
					}
					break;

				case EVENTS.GET_METADATA_EXISTING_GREETINGS_FINISHED:
						Logger.d(TAG, "SetupCompleteActivity.onUpdateListener() GET_METADATA_EXISTING_GREETINGS_FINISHED");
					// stops loading animation
					stopGauge();
					// stop listening to events
						OperationsQueue.getInstance().removeEventListener((SetupCompleteActivity) context);

					// start the greetings screen
						Intent i = new Intent(context, GreetingActionsActivity.class);
					// Next intenet to go after saving.
						i.putExtra(IntentExtras.NEXT_INTENT, new Intent(context, SetupCompleteActivity.class));
					startActivityForResult(i, 0);
					break;

				case EVENTS.LOGIN_FAILED:
						Logger.d(TAG, "SetupCompleteActivity.onUpdateListener() LOGIN_FAILED");

					stopGauge();
					Utils.showToast(R.string.actionNotCompletedError , Toast.LENGTH_LONG);

					break;

				default:
					break;
				}
			}
		});
	}

	private void addShortcut() {

		// creates the intent for application's shortcut, settings it's action
		// (which causes the shortcut
		// to be removed when the application is uninstalled) and class
		Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
		shortcutIntent.setComponent(new ComponentName(getApplicationContext(), WelcomeActivity.class));
		shortcutIntent.addCategory(Intent.CATEGORY_LAUNCHER);

		// creates the intent for installing application's shortcut, adding
		// application's shortcut intent,
		// application's name and icon resource as its extra data
		Intent intent = new Intent("com.android.launcher.action.INSTALL_SHORTCUT");
		intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
		intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.app_name));
		intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this, R.drawable.ic_voice_mail));
		// avoid duplicating the shortcut if already exists on home screen
		intent.putExtra(Constants.EXTRAS.EXTRA_SHORTCUT_DUPLICATE, false);
		// creates application's shortcut
		this.sendBroadcast(intent);
	}
}