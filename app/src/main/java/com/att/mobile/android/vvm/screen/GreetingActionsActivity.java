package com.att.mobile.android.vvm.screen;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.att.mobile.android.infra.utils.AlertDlgUtils;
import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.LoadingProgressBar;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.EventListener;
import com.att.mobile.android.vvm.control.OperationsQueue;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.model.greeting.Greeting;
import com.att.mobile.android.vvm.screen.AudioRecorderActivity.IntentExtras;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * @author mkoltnuk
 */
public class GreetingActionsActivity extends VVMActivity implements EventListener {

	private boolean isLoginProcess = false;
	private LoadingProgressBar loadingProgressBar ;
	private static final String TAG = "GreetingActionsActivity";
	private ViewFlipper vf;
	private ListView greetingList;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		Logger.d(TAG, "onCreate()");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.greeting_types);

		isLoginProcess  = !(ModelManager.getInstance().getCurrentSetupState() == Constants.SETUP_STATUS.SUCCESS);
		initActionBar(R.string.greeting , !isLoginProcess);

		vf = (ViewFlipper) findViewById( R.id.viewFlipper );
		greetingList = (ListView) findViewById(R.id.list);

		if (ModelManager.getInstance().getGreetingList() != null && ModelManager.getInstance().getGreetingList().size() > 0) {
			setListView();
			greetingList.setAdapter(new GreetingsAdapter(this, R.layout.greeting_item, ModelManager.getInstance().getGreetingList()));
		} else {
			// back to wizard
			if ( isLoginProcess ) {
				OperationsQueue.getInstance().enqueueGetGreetingsDetailsOperation();
				setSwirlView();
			} else {
				finish();
			}
		}


		if (isLoginProcess) {
			setFinishSetupButton();
		}

	}

	private void setFinishSetupButton() {
		Button finishSetup = (Button) findViewById(R.id.buttonFinishSetup);
		finishSetup.setVisibility(View.VISIBLE);
		finishSetup.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Medium));
		finishSetup.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ModelManager.getInstance().setSetupCompleted();
                setResult(EVENTS.WELCOME_WIZARD_FINISHED);
                finish();
            }
        });
	}

	private void setListView() {
		vf.setDisplayedChild(vf.indexOfChild(findViewById(R.id.listView)));
		toolBarTV.setText(R.string.greeting);
	}


	@Override
	protected void onResume() {
		Logger.d(TAG, "onResume()");
		super.onResume();

        if ( mustBackToSetupProcess() ) {
            return;
        }

		((VVMApplication) getApplicationContext()).setVisible(true);
		ModelManager.getInstance().addEventListener(this);
	}

	@Override
	protected void onDestroy() {
		Logger.d(TAG, "onDestroy()");

		// clear metadata from model
		ModelManager.getInstance().getMetadata().clear();

		Logger.d(TAG, "onDestroy() - greetings metadata cleared from model");

		super.onDestroy();
	}

	@Override
	protected void onPause() {
		Logger.d(TAG, "onPause()");
		super.onPause();

		((VVMApplication) getApplicationContext()).setVisible(false);
		ModelManager.getInstance().removeEventListener(this);
	}

	@Override
	public void onBackPressed() {
		Logger.d(TAG, "onBackPressed()");
		if ( isLoginProcess ) {
			setResult(Constants.EVENTS.BACK_FROM_PASSWORD_CHANGE);
			finish();
		} else {
			super.onBackPressed();
		}
	}

	/**
	 * Custom greetings array adapter
	 */
	private class GreetingsAdapter extends ArrayAdapter<Greeting> {
		private ArrayList<Greeting> items;

		public GreetingsAdapter(Context context, int textViewResourceId,
				ArrayList<Greeting> items) {
			super(context, textViewResourceId, items);
			this.items = items;
		}

		public void updateItems(ArrayList<Greeting> items) {
			this.items = items;
			super.notifyDataSetChanged();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.greeting_item, null);
			}

			Greeting g = items.get(position);
			if (g != null) {
				TextView headerTxt = (TextView) v.findViewById(R.id.headerTextItem);
				TextView subTxt = (TextView) v.findViewById(R.id.subTextItem);
				ImageView img = (ImageView) v.findViewById(R.id.imageItem);

				if (headerTxt != null && subTxt != null && img != null) {
					headerTxt.setText(g.getDisplayName());
					headerTxt.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
					subTxt.setText(g.getDesc());
					subTxt.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
					img.setVisibility(g.getIsSelected() ? ImageView.VISIBLE	: ImageView.INVISIBLE);
				}

				v.setTag(g);
				if(g.getIsSelected()){
					v.setContentDescription(headerTxt.getText() + " " +  subTxt.getText() + getString(R.string.checked));
				}else{
					v.setContentDescription(headerTxt.getText() + " " +  subTxt.getText() + getString(R.string.unchecked));
				}
				wireNeededListener(v);
			}

			return v;
		}
	}
	private Greeting currentGreetingForUseExisting = null;
	/**
	 * Wires click listener to each list view item
	 * 
	 * @param listViewItem List item to wire the click listener
	 */
	public void wireNeededListener(View listViewItem) {

		listViewItem.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				final Greeting greeting = (Greeting) v.getTag();
				final String originalType = greeting.getOriginalType().toString();

				// changeable greeting
				if (greeting.isChangeable()) {
					Logger.d(TAG, "CHANGEABLE greeting selected.");

					if (hasExistingRecording(greeting.getDisplayName())) { // greeting has
						// stream, can use existing
						Logger.d(TAG, "Greeting contains stream, going to show a menu");
						int titleId = greeting.getDisplayName().equals("Custom") ? R.string.customGreeting : R.string.nameGreeting;
						int textId = greeting.getDisplayName().equals("Custom") ? R.string.custom_greeting_change : R.string.name_greeting_change;
						AlertDlgUtils.showDialog(GreetingActionsActivity.this, titleId, textId, R.string.UseExistingText, R.string.RecordNewText, true, new AlertDlgUtils.AlertDlgInterface() {
							@Override
							public void handlePositiveButton(View view) {
								Logger.d(TAG, "GreetingActionsActivity:=> Use existing greeting pressed");
								FileInputStream recordingAudioFileInputStream = null;
								try {

									String pathPrefix = new StringBuffer(vvmApplication.getFilesDir().getAbsolutePath()).append(File.separator).toString();
									String recordNameFilePath = new StringBuffer(pathPrefix).append(greeting.getDisplayName()).append(".amr").toString();

									File recordingAudioFile = new File(recordNameFilePath);


									int recordingAudioFileSize = (int) recordingAudioFile.length();
									byte[] recordingAudioFileData = new byte[recordingAudioFileSize];
									 recordingAudioFileInputStream = new FileInputStream(recordingAudioFile);

									if (recordingAudioFileInputStream.read(recordingAudioFileData) != recordingAudioFileSize) {
										Logger.d(TAG, "recording greeting audio file data couldn't be read");
										return;
									}
									String imapRecordingGreetingType = null;
									// sends the recording audio file (greeting) to the sever
									if(greeting.getDisplayName().equals("Custom")){
										imapRecordingGreetingType = "/private/vendor/vendor.alu/messaging/Greetings/Personal";
									}else{
										imapRecordingGreetingType = "/private/vendor/vendor.alu/messaging/RecordedName";
									}
									showGauge(null);
									OperationsQueue.getInstance().enqueueSendGreetingOperation(imapRecordingGreetingType, recordingAudioFileData);
									 currentGreetingForUseExisting = greeting;
								} catch (Exception e) {
									Logger.e(TAG,"recorded greeting audio file couldn't be sent to server - " , e);
								} finally {
									if (recordingAudioFileInputStream != null) {
										try {
											recordingAudioFileInputStream.close();
										} catch (IOException e) {
											e.printStackTrace();
										}
									}
								}


							}

							@Override
							public void handleNegativeButton (View view) {
								new Handler().postDelayed(new Runnable() {
									@Override
									public void run() {
										stopGauge();
									}
								}, 2500);

								Logger.d(TAG, "GreetingActionsActivity:showMenu::onClick => item 1 was clicked");
								Logger.d(TAG, "GreetingActionsActivity:showMenu::onClick => starting recorder");
								startRecorderActivity(greeting.getDisplayName(), greeting.getMaxAllowedRecordTime(), greeting.getUniqueId(), greeting.getImapSelectionVariable(), greeting.getImapRecordingVariable(), originalType);
							}
						});

					} else {
						Logger.d(TAG, "No stream found for greeting, going to start recorder");
						startRecorderActivity(greeting.getDisplayName(),
								greeting.getMaxAllowedRecordTime(), greeting
										.getUniqueId(), greeting
										.getImapSelectionVariable(), greeting
										.getImapRecordingVariable(), greeting
										.getOriginalType().toString());
					}
				}
				// non changeable greeting
				else {
					Logger.d(TAG, "NON CHANGEABLE greeting selected. Going to set greeting (" + greeting.getDisplayName() + ") as selected.");

					// make sure we have type and this is not an already
					// selected greeting
					if (!TextUtils.isEmpty(originalType)) {
						if (!greeting.getIsSelected()) {

							if (Utils.isNetworkAvailable()) {
								Logger.d(TAG, "Going to set greeting (" + greeting.getDisplayName() + ") as selected.");

								// enqueus a set meta data operation to the operations queue
								OperationsQueue.getInstance().enqueueSetMetaDataOperation(Constants.METADATA_VARIABLES.GreetingType, originalType);
								// set greeting as selected
								setSelectedListViewItem(greeting.getUniqueId());

								Logger.d(TAG, "Operation 'SetMetaDataOperation' enqueued.");
								Logger.d(TAG, "Operation is " + Constants.METADATA_VARIABLES.GreetingType + " Value is " + originalType);
							} else {
								Utils.showToast(R.string.noConnectionToast, Toast.LENGTH_SHORT);
							}
						} else {
							Logger.d(TAG, "No need to set as selected, its already is.");
						}
					} else {
						Logger.d(TAG, "wireNeededListener() Failed to determine original greeting type, cannot enqueue operation.");
					}
				}
			}
		});
	}

	private boolean hasExistingRecording(String type) {

		String pathPrefix = new StringBuffer(vvmApplication.getFilesDir().getAbsolutePath()).append(File.separator).toString();
		String recordNameFilePath = new StringBuffer(pathPrefix).append(type).append(".amr").toString();

		File file = new File(recordNameFilePath);
		if(file.exists()) {
			return true;
		}
		return false;
	}

//	/**
//	 * shoe menu of selection to the user between setting existing recording to this greeting type or creating new one
//	 * with the recorder screen
//	 *
//	 * @param gUniqueId
//	 * @param gDisplayName
//	 * @param gMaxAllowedRecordTime
//	 * @param imapSelectionGreetingType
//	 * @param imapRecordingGreetingType
//	 * @param greetingType
//	 * @param items
//	 */
//	private void showMenu(String gUniqueId, String gDisplayName, int gMaxAllowedRecordTime, String imapSelectionGreetingType,
//							String imapRecordingGreetingType, String greetingType, CharSequence[] items) {
//
//		AlertDialog.Builder builder = new AlertDialog.Builder(GreetingActionsActivity.this);
//		builder.setTitle(gDisplayName + " "	+ getString(R.string.GreetingOptionsTitleText));
//
//		final String greetingDisplayName = gDisplayName;
//		final int greetingMaxAllowedRecordTime = gMaxAllowedRecordTime;
//		final String uniqueId = gUniqueId;
//		final String type = greetingType;
//		final String imapSelectionType = imapSelectionGreetingType;
//		final String imapRecordingType = imapRecordingGreetingType;
//
//		builder.setSingleChoiceItems(items, 0,
//				new DialogInterface.OnClickListener() {
//
//					public void onClick(DialogInterface dialog, int item) {
//						// close dialog
//						try {
//
//							if (item == 0) {
//								if (Utils.isNetworkAvailable()) {
//									// set selected visually
//									setSelectedListViewItem(uniqueId);
//
//									// set as selected greeting on server
//									// enqueue a set meta data operation to the
//									// operations queue
//									new Thread(new Runnable() {
//										public void run() {
//											OperationsQueue.getInstance().enqueueSetMetaDataOperation(Constants.METADATA_VARIABLES.GreetingType, type);
//										}
//									}).start();
//									dialog.dismiss();
//								} else {
//									Snackbar.make(findViewById(android.R.id.content), getString(R.string.noDataConnectionToast), Snackbar.LENGTH_LONG).show();
//								}
//							} else { // (item == 1)
//								dialog.dismiss();
//								// show progress gauge until the recorder screen is shown
//								startGauge();
//								new Handler().postDelayed(new Runnable() {
//									@Override
//									public void run() {
//										stopGauge();
//									}
//								}, 2500);
//
//								Logger.d(TAG, "showMenu::onClick => item 1 was clicked");
//								Logger.d(TAG, "showMenu::onClick => starting recorder activity title=" + greetingDisplayName + " maxRecordTime=" + greetingMaxAllowedRecordTime + " seconds");
//								startRecorderActivity(greetingDisplayName, greetingMaxAllowedRecordTime, uniqueId, imapSelectionType, imapRecordingType, type);
//							}
//						} catch (Exception e) {
//							Log.e(TAG, e.getMessage(), e);
//						}
//					}
//				});
//
//		AlertDialog alert = builder.create();
//		alert.show();
//	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == EVENTS.SET_METADATA_FINISHED) {
			if (data != null) {
				Bundle extras = data.getExtras();
				if (extras.get("data") != null) {
					setSelectedListViewItem(extras.get("data").toString());
					((GreetingsAdapter) greetingList.getAdapter()).updateItems(ModelManager.getInstance().getGreetingList());
				}
			}
			Utils.showToast(R.string.greeting_changed, Toast.LENGTH_LONG);

		} else if (resultCode == EVENTS.SET_METADATA_FAILED) {
			// do nothing - so the previous item is still selected

			Utils.showToast(R.string.greeting_not_changed, Toast.LENGTH_LONG);
		}

		// back to wizard
		if (isLoginProcess) {
			setResult(resultCode);
			finish();
		}
	}

	/**
	 * @param title
	 * @param maxAllowedRecordTime
	 * @param greetingUniqueId
	 * @param greetingType the value of the set greeting type transaction
	 */
	private void startRecorderActivity(String title, int maxAllowedRecordTime,
			String greetingUniqueId, String imapSelectionGreetingType,
			String imapRecordingGreetingType, String greetingType) {
		// start recorder activity
		Intent intent = new Intent(GreetingActionsActivity.this, GreetingRecorderActivity.class);
		intent.putExtra(IntentExtras.SCREEN_TITLE, title);
		intent.putExtra(IntentExtras.MAX_RECORDING_MILSEC_DURATION, maxAllowedRecordTime * 1000);
		intent.putExtra(IntentExtras.GREETING_UNIQUE_ID, greetingUniqueId);
		intent.putExtra(IntentExtras.IMAP_SELECTION_GREETING_TYPE, imapSelectionGreetingType);
		intent.putExtra(IntentExtras.IMAP_RECORDING_GREETING_TYPE, imapRecordingGreetingType);
		intent.putExtra(IntentExtras.GREETING_TYPE, greetingType);
		startActivityForResult(intent, EVENTS.SET_METADATA_STARTED);
	}

	@SuppressWarnings("unchecked")
	private void
	setSelectedListViewItem(String selectedUniqueId) {
		ArrayList<Greeting> greetingList = ModelManager.getInstance().getGreetingList();

		for (Greeting greeting : greetingList) {
			if (greeting.getUniqueId().equals(selectedUniqueId)) {
				greeting.setIsSelected(true);
			} else {
				greeting.setIsSelected(false);
			}
		}
		setListView();

		((ArrayAdapter<Greeting>) this.greetingList.getAdapter()).notifyDataSetChanged();
	}
	
	
	public void onUpdateListener(final int eventId, final ArrayList<Long> messageIDs) {

		Logger.d(TAG, "GreetingActionsActivity.onUpdateListener() eventId=" + eventId);

		runOnUiThread(new Runnable() {

			@Override
			public void run() {

				switch (eventId) {

					case Constants.EVENTS.GREETING_UPLOAD_SUCCEED:
						// on success of setting the recording to the desired greeting type
						// - we set the selected greeting type to be the desired one

						if(currentGreetingForUseExisting != null) {
							OperationsQueue.getInstance().enqueueSetMetaDataGreetingTypeOperation(Constants.METADATA_VARIABLES.GreetingType, currentGreetingForUseExisting.getOriginalType().toString());
						}
						else{
							dismissGauge();
							Utils.showToast(R.string.greeting_not_changed, Toast.LENGTH_LONG);

						}
						break;
					// in case a greeting upload and set greeting type succeeded

					case Constants.EVENTS.SET_METADATA_GREETING_FINISHED:
						// on success of setting the recording to the desired greeting type
						// - we set the selected greeting type to be the desired one
						dismissGauge();
						if(currentGreetingForUseExisting != null) {
							setSelectedListViewItem(currentGreetingForUseExisting.getUniqueId());
							Utils.showToast(R.string.greeting_changed, Toast.LENGTH_LONG);


						}
						else{

							Utils.showToast(R.string.greeting_not_changed, Toast.LENGTH_LONG);

						}

						break;
					// in case a greeting upload failed or set greeting type failed
					case Constants.EVENTS.GREETING_UPLOAD_FAILED:
					case Constants.EVENTS.SET_METADATA_GREETING_FAILED:
						dismissGauge();

						Utils.showToast(R.string.greeting_not_changed, Toast.LENGTH_LONG);

						break;
					case EVENTS.START_WELCOME_ACTIVITY:
						Logger.d(TAG, "onUpdateListener() START_WELCOME_ACTIVITY");
						// show welcome screen if application is in foreground
						if (((VVMApplication) getApplicationContext()).isVisible()) {
							startActivity(new Intent(GreetingActionsActivity.this, WelcomeActivity.class));
						}
						break;

					case Constants.EVENTS.GET_METADATA_GREETING_DETAILS_FINISHED:
						Logger.d(TAG, "onUpdateListener() GET_METADATA_GREETING_DETAILS_FINISHED");
						if (ModelManager.getInstance().getMetadata() != null) {
							OperationsQueue.getInstance().enqueueGetExistingGreetingsOperation();
						}
						break;

					case Constants.EVENTS.GET_METADATA_EXISTING_GREETINGS_FINISHED:
						Logger.d(TAG, "onUpdateListener() GET_METADATA_EXISTING_GREETINGS_FINISHED");
						if (ModelManager.getInstance().getGreetingList() != null && ModelManager.getInstance().getGreetingList().size() > 0) {
							setListView();
							// sets the greetingList adapter
							greetingList.setAdapter(new GreetingsAdapter(GreetingActionsActivity.this, R.layout.greeting_item, ModelManager.getInstance().getGreetingList()));
						}
						stopGauge();
						break;

					case EVENTS.GET_METADATA_GREETING_FAILED:
						Logger.d(TAG, "onUpdateListener() GET_METADATA_GREETING_FAILED");
						stopGauge();
						setErrorView();
						break;
				}

			}
		});
	}

	private void setErrorView() {
		vf.setDisplayedChild(vf.indexOfChild(findViewById(R.id.ErrorView)));
		toolBarTV.setText(R.string.greeting_error);
		((TextView)findViewById(R.id.txtOops)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
		((TextView)findViewById(R.id.greeting_error_sub1)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
		((TextView)findViewById(R.id.greeting_error_sub2)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
	}

	@Override
	public void onNetworkFailure() {
	}

	private void startGauge() {
		Logger.d(TAG, "startGauge");
		loadingProgressBar.setVisibility(View.VISIBLE);
		loadingProgressBar.start();
	}

	private void setSwirlView() {
		toolBarTV.setText(getString(R.string.greeting));
		vf.setDisplayedChild(vf.indexOfChild(findViewById(R.id.swirlView)));
		loadingProgressBar = (LoadingProgressBar) findViewById(R.id.gaugeSetupProgress);
		((TextView)findViewById(R.id.txtHeader)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular), Typeface.BOLD);
		loadingProgressBar.start();
		startGauge();
	}

	/**
	 * Stop waiting animation
	 */
	private void stopGauge() {
		Logger.d(TAG, "stopGauge");
		if(loadingProgressBar != null) {
			loadingProgressBar.stop();
		}
	}
}