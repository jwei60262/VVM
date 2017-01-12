
package com.att.mobile.android.vvm.screen;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.OperationsQueue;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.model.db.ModelManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * The Greeting Recorder Screen.
 */
public class GreetingRecorderActivity extends AudioRecorderActivity {
	/** holds greeting unique ID */
	private String greetingUniqueId = null;
	private static final String TAG = "GreetingRecorderActivity";

	/** greeting type to set the greeting type after setting the recoding **/
	private String imapSelectionGreetingType = null;

	/** greeting type to set the greeting recoding **/
	private String imapRecordingGreetingType = null;

	/** greeting type to set to the server **/
	private String greetingType = null;

	private boolean isErrorDialogShown = false;
	/** file name we use for voice message */



	/**
	 * Called upon screen creation.
	 *
	 * @param savedInstanceState (Bundle) any previous saved instance state.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// gets the extras from the intent which launched this screen
		Bundle intentExtras = getIntent().getExtras();

		// sets recording maximum duration
		maximumRecordDuration = intentExtras
				.getInt(IntentExtras.MAX_RECORDING_MILSEC_DURATION);
		if (maximumRecordDuration == 0) {
			Logger.d(TAG, "GreetingRecorderActivity.onCreate() - MAX_RECORDING_MILSEC_DURATION extra is missing in the launching intent or is equal to 0");
		}
		setTotalRecordingTime(maximumRecordDuration);
		greetingUniqueId = intentExtras
				.getString(IntentExtras.GREETING_UNIQUE_ID);
		if (greetingUniqueId == null) {
			Logger.d(
					TAG,
					"GreetingRecorderActivity.onCreate() - GREETING_UNIQUE_ID extra is missing in the launching intent");
		}

		imapSelectionGreetingType = intentExtras
				.getString(IntentExtras.IMAP_SELECTION_GREETING_TYPE);
		if (imapSelectionGreetingType == null) {
			Logger.d(
					TAG,
					"GreetingRecorderActivity.onCreate() - IMAP_SELECTION_GREETING_TYPE extra is missing in the launching intent");
		}

		imapRecordingGreetingType = intentExtras
				.getString(IntentExtras.IMAP_RECORDING_GREETING_TYPE);
		if (imapRecordingGreetingType == null) {
			Logger.d(
					TAG,
					"GreetingRecorderActivity.onCreate() - IMAP_RECORDING_GREETING_TYPE extra is missing in the launching intent");
		}

		greetingType = intentExtras.getString(IntentExtras.GREETING_TYPE);
		if (greetingType == null) {
			Logger.d(
					TAG,
					"GreetingRecorderActivity.onCreate() - GREETING_TYPE extra is missing in the launching intent");
		}

		// sets progress text initial value as the maximum recording length
		updateRecordingProgressTextUIComponent();

		// registers the screen as an events listener for the operations queue
		OperationsQueue.getInstance().addEventListener(this);
	}

	@Override
	public void onBackPressed() {

		setRes(EVENTS.SET_METADATA_FAILED, null);
		// releases the acquired wake look, allowing the screen to turn OFF
		AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		audioManager.abandonAudioFocus(this);
		VVMApplication.getInstance().releaseWakeLock();
		// resotres device's audio mode before leaving the screen
		VVMApplication.getInstance().restoreDeviceAudioMode();
		VVMApplication.getInstance().setIsApplicationSpeakerOn(false);
	}

	/**
	 * Callback for the send button, called upon button click.
	 *
	 * @param buttonView (View != null) the send button.
	 */
	public void sendButtonOnClickCallback(View buttonView) {
		if (Utils.isNetworkAvailable()) {
			super.sendButtonOnClickCallback(buttonView);
			// sets upload recording UI (makes the upload panel visible)
			setUploadRecordingUIMode();

			FileInputStream recordingAudioFileInputStream = null;

			try {
				// gets the binary data of the recorded audio file
				File recordingAudioFile = new File(getRecFilePath());
				int recordingAudioFileSize = (int) recordingAudioFile.length();
				byte[] recordingAudioFileData = new byte[recordingAudioFileSize];
				recordingAudioFileInputStream = new FileInputStream(recordingAudioFile);

				if (recordingAudioFileInputStream.read(recordingAudioFileData) != recordingAudioFileSize) {
					Logger.d(TAG, "recording greeting audio file data couldn't be read");
					return;
				}

				// sends the recording audio file (greeting) to the sever
				OperationsQueue.getInstance().enqueueSendGreetingOperation(imapRecordingGreetingType, recordingAudioFileData);
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

		} else {
			Utils.showToast(R.string.noConnectionToast, Toast.LENGTH_SHORT);
		}
	}

	/**
	 * Sets screen's UI mode for when the recorded audio is being uploaded to the server.
	 */
	private void setUploadRecordingUIMode() {
		// disables all recorder's buttons
		recordButton.setEnabled(false);
		playButton.setEnabled(false);
		sendButton.setEnabled(false);

		// sets the upladoing panel as visible
		showGauge("");
	}

	private void setRes(int res, String value) {
		Intent dataIntent = new Intent();
		if (value != null) {
			dataIntent.putExtra("data", value);
			setResult(res, dataIntent);
		} else {
			setResult(res, null);
		}

		finish();
	}

	/**
	 * Called by an event dispatcher the screen has been registered to, to notify the screen regarding an event for a
	 * specific message.
	 */
	@Override
	public void onUpdateListener(int eventId, ArrayList<Long> messageIDs) {
		// in case a greeting upload succeed
		if (eventId == Constants.EVENTS.GREETING_UPLOAD_SUCCEED) {
			// on success of setting the recording to the desired greeting type
			// - we set the selected greeting type to be the desired one
			OperationsQueue.getInstance()
					.enqueueSetMetaDataGreetingTypeOperation(
							Constants.METADATA_VARIABLES.GreetingType,
							greetingType);
		}
		// in case a greeting upload and set greeting type succeeded
		else if (eventId == Constants.EVENTS.SET_METADATA_GREETING_FINISHED) {
			// on success of setting the recording to the desired greeting type
			// - we set the selected greeting type to be the desired one
			setRes(EVENTS.SET_METADATA_FINISHED,
					greetingUniqueId != null ? greetingUniqueId : null);
			dismissGauge();
		}
		// in case a greeting upload failed or set greeting type failed
		else if (eventId == Constants.EVENTS.GREETING_UPLOAD_FAILED
				|| eventId == Constants.EVENTS.SET_METADATA_GREETING_FAILED) {
			setRes(EVENTS.GREETING_UPLOAD_FAILED,
					greetingUniqueId != null ? greetingUniqueId : null);
			dismissGauge();
		} else {
			super.onUpdateListener(eventId, messageIDs);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

        if ( mustBackToSetupProcess() ) {
            return;
        }

		ModelManager.getInstance().addEventListener(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		ModelManager.getInstance().removeEventListener(this);
	}

	/**
	 * Called upon activity destroy.
	 */
	@Override
	protected void onDestroy() {
		// un-registers the screen as an events listener for the operations
		// queue
		OperationsQueue.getInstance().removeEventListener(this);

		super.onDestroy();
	}
}
