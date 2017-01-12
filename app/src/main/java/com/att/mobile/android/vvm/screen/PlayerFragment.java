package com.att.mobile.android.vvm.screen;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.QuickContactBadge;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.att.mobile.android.infra.media.AudioPlayer;
import com.att.mobile.android.infra.media.MediaPlayerEventsListener;
import com.att.mobile.android.infra.utils.AccessibilityUtils;
import com.att.mobile.android.infra.utils.AlertDlgUtils;
import com.att.mobile.android.infra.utils.ContactUtils;
import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.LoadingProgressBar;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.PermissionUtils;
import com.att.mobile.android.infra.utils.PicassoUtils;
import com.att.mobile.android.infra.utils.TimeDateUtils;
import com.att.mobile.android.infra.utils.Utils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.control.AsyncLoader;
import com.att.mobile.android.vvm.control.EventListener;
import com.att.mobile.android.vvm.control.files.VvmFileUtils;
import com.att.mobile.android.vvm.model.Constants;
import com.att.mobile.android.vvm.model.ContactObject;
import com.att.mobile.android.vvm.model.Message;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.PicassoTools;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class PlayerFragment extends Fragment implements /*LoaderManager.LoaderCallbacks<Cursor>,*/ EventListener,
		MediaPlayerEventsListener, AudioManager.OnAudioFocusChangeListener {

	private static final String TAG = "PlayerFragment";
	private static final int daysDepth = 10;
//	private static final int daysSize = daysDepth * 2 + 1;
	private static final int URL_LOADER = 0;
	private static final int REQUEST_CALL_PHONE_PERMISSION = 10;
	private static final int PERMISSIONS_REQUEST_EXPORT = 11;
	private static final int PERMISSIONS_REQUEST_SHARE = 12;


	private ImageView nextMessageButton;
	private ImageView previousMessageButton;
	private TextView numberOfmessages;
	private QuickContactBadge avatarImageView;
	private TextView nameTextView;
	private TextView dateTextView;
	private TextView phoneTypeTextView;
	private View phoneTypeLayout;
	private ImageView savedImageView;
	private ImageView urgentImageView;
	private ImageView playButton;
	private SeekBar defaultSeekBar;
	private TextView playerProgressTime;
	private TextView playerTimeDelim;
	private TextView playerTotalTime;
	private ImageView callButton;
	private ImageView sendMsgButton;
	private ImageView deleteButton;
	private TextView speakerText;
	private TextView textTranscriptionBody;
	private ImageView speakerButton;
	private ProgressBar mProgressBar;
	private int filterType;
	private String contactUri = null;
	private String displayName = null;
	// amount of all messages in the model
	private int totalMessages;
	private int mespos;
	// holds whether the screen was launched by the inbox screen
	private boolean screenLaunchedByInbox = false;
	// holds the audio player
	private Context mContext;
//	private PlayerActivity mActivity;
//	private Bitmap defaultAvatar;
	private ModelManager modelManager;
	// used to get call state when trying to play a message - in general we
	// should not allow playing while in a call
	private TelephonyManager telephonyManager = null;
    private CustomPhoneStateListener phoneStateListener;
	// the message itself
	private Message message;
	private ImageView bgImageView;
	private Uri contactImageUri = null;
	private String senderDisplayName = null;
	private LoadingProgressBar downloadingGauge;
//	private PowerManager powerManager;
//	private PowerManager.WakeLock wakeLock;
//	private int field = 0x00000020;
//	private SensorManager mSensorManager;
//	private Sensor mProximity;
	private AudioPlayer audioPlayer = null;
	private boolean isAutoPlayMode = false;
	private VVMApplication vvmApplication;
    private Toolbar toolbar;
    private AudioManager audioManager;
    private boolean contactsChanged = false;

//	private SensorEventListener mSensorEventListener = new SensorEventListener(){
//
//		@Override
//		public void onSensorChanged(SensorEvent event) {
//			if (event.values[0] == 0) {
//				if(audioPlayer== null){
//					return;
//				}
//				if(!isProximityHeld && audioPlayer.isPlaying()) {
//					Logger.d(TAG, "onSensorChanged event.values[0] == 0");
//					isProximityHeld = true;
//					if(vvmApplication.isApplicationSpeakerOn()){
//						if(!vvmApplication.isCurrentlyApplicationAudioMode()){
//							vvmApplication.setCurrentlyApplicationAudioMode(true);
//						}
//						swapAudioSource();
//						wasSpeakerSwaped = true;
//					}
//					wakeLock.acquire();
//				}
//			} else {
//				if(isProximityHeld) {
//					Logger.d(TAG, "onSensorChanged event.values[0] != 0");
//					isProximityHeld = false;
//					wakeLock.release();
//					if(wasSpeakerSwaped && !vvmApplication.isApplicationSpeakerOn()){
//						wasSpeakerSwaped = false;
//						if(!vvmApplication.isCurrentlyApplicationAudioMode()){
//							vvmApplication.setCurrentlyApplicationAudioMode(true);
//						}
//						swapAudioSource();
//					}
//					refreshSpeakerButton();
//				}
//			}
//		}
//
//		@Override
//		public void onAccuracyChanged(Sensor sensor, int accuracy) {
//			// TODO Auto-generated method stub
//
//		}
//
//	};
//	private AudioPlayer audioPlayer = null;
	// holds a timer, used for scheduling timer tasks for observing player's
	// playback progress
	private Timer timer = new Timer();
	private AsyncLoader dataLoader;

	// holds the current player playback observer (timer task to observe
	// player's playback progress)
	private PlayerPlaybackObserver playerPlaybackObserver;

//	private boolean isOptionsMenuOpened;
//	private SwirlDialogFragment editNameDialog;

	@Override
	public void onUpdateListener(int eventId, ArrayList<Long> messageIDs) {
		if (eventId == Constants.EVENTS.CONTACTS_CHANGED) {
			Logger.d(TAG, "onUpdateListener() CONTACTS_CHANGED");
            contactsChanged = true;
		}
		else if (eventId == Constants.EVENTS.PLAYER_PAUSED) {
			Logger.d(TAG, "onUpdateListener() PLAYER_PAUSED");
			if(!((PlayerActivity)getActivity()).wasProximityHold()) {
				if(audioPlayer.isPlaying()){
					Logger.d(TAG, "####### onUpdateListener()  wasProximityHold = false and audioPlayer is playing");
					audioPlayer.pause();
				}
			}
		}
		else if (eventId == Constants.EVENTS.MESSAGE_CONTACT_INFO_UPDATED) {
			Logger.d(TAG, "onUpdateListener() MESSAGE_CONTACT_INFO_UPDATED");
			runUpdateUI();
		}
		else if(eventId == Constants.EVENTS.RETRIEVE_HEADERS_FINISHED){
			if(this.getUserVisibleHint()) {
				Logger.d(TAG, "##### onUpdateListener() current visible fragment RETRIEVE_HEADERS_FINISHED call refreshAdapter mesId = " + message.getRowId() + " mespos = " + mespos);
				((PlayerActivity) getActivity()).refreshAdapter(message.getRowId(), mespos);
			}
		} else if(eventId == Constants.EVENTS.MESSAGE_FILE_DOWNLOADED){
			for(long mesId: messageIDs){
				if(mesId == message.getRowId() && this.getUserVisibleHint()){
					updateMessage();
					getActivity().runOnUiThread(new Runnable() {
						@Override
						public void run() {
					updatePlayer();
						}
					});
					Logger.d(TAG, "##### onUpdateListener() current visible fragment MESSAGE_FILE_DOWNLOADED  mesId = " + mesId + " message and player updated ");
				}
			}
		}
	}

	private void updateMessage() {
		message.setFileName(modelManager.getMessageFileName(message.getRowId()));
	}

	@Override
	public void onNetworkFailure() {

	}
	void runUpdateUI(){
		PicassoTools.clearCache(Picasso.with(getActivity()));
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				updateContactDetailsUI();
			}
		});

	}


	class PlayerSetErrorAsyncTask extends AsyncTask<Void, Void, Void> {

		private int _errorCode;

		PlayerSetErrorAsyncTask(int errorCode) {
			_errorCode = errorCode;
		}

		@Override
		protected Void doInBackground(Void... params) {

			if (_errorCode == AudioPlayer.PlayerError.FILE_NOT_FOUND) {
				// should update model with message file name - null and refresg screen
//				adapter.setMessageFileNotFound(messageId);
			}
//			message = adapter.getMessageDetails(messageId, true);
			return null;
		}

		@Override
		protected void onPostExecute(Void aBoolean) {
			initializePlayerUI(false);
		}
	}

	private  class ContactLoaderAsync extends AsyncTask<Void,Void,ContactObject> {

		WeakReference<Message> mMessage;
		private String mPhoneNumber;

	public ContactLoaderAsync(String phoneNumber, Message mes){

		mPhoneNumber= phoneNumber;
		mMessage = new WeakReference<>(mes);
	}

	@Override
	protected ContactObject doInBackground(Void... params) {

		return ContactUtils.getContact(mPhoneNumber);
	}

	@Override
	protected void onPostExecute(ContactObject contactObject) {
		super.onPostExecute(contactObject);

		Message msg = mMessage.get();
		if(msg != null) {
		if(contactObject != null){

			String displayName = contactObject.getDisplayName();
			Logger.i(TAG, "#### ContactLoaderAsync displayName=" + displayName);
				msg.setSenderDisplayName(TextUtils.isEmpty(displayName) ? mPhoneNumber : displayName);
			String phoneTypeText = (contactObject.getPhoneType() == android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM	&& !TextUtils.isEmpty(contactObject.getPhoneLabel())) ? contactObject.getPhoneLabel() : ContactUtils.getPhoneTypeText(contactObject.getPhoneType());
				msg.setPhoneNumberLabel(phoneTypeText);
			Uri contactPhotoUri = contactObject.getPhotoUri();
			if(contactPhotoUri != null ){
					msg.setContactImageUri(contactPhotoUri);
			} else {
					msg.setContactImageUri(null);
			}
				if (!TextUtils.isEmpty(contactObject.getContactLookup())) {
					msg.setContactLookupKey(contactObject.getContactLookup());
				}
		} else {
				msg.setSenderDisplayName(!TextUtils.isEmpty(msg.getSenderPhoneNumber()) ? msg.getSenderPhoneNumber() : mContext.getString(R.string.privateNumber));
				msg.setContactLookupKey(null);
				msg.setSenderBitmap(null);
				msg.setContactImageUri(null);
				msg.setPhoneNumberLabel(null);
			}
		}
		modelManager.notifyListeners(Constants.EVENTS.MESSAGE_CONTACT_INFO_UPDATED, null);
	}
}


	@Override
	public void onAudioFocusChange(int focusChange) {

	}


	/**
	 * Listener for the phone state (call state)
	 */

    private class CustomPhoneStateListener extends PhoneStateListener {

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            Logger.d(TAG, ":phoneStateListener state=" + state);
            refreshPlayButton();
            super.onCallStateChanged(state, incomingNumber);
        }
    }

	/**
	 * A timer task which observes player's playback progress.
	 */
	private class PlayerPlaybackObserver extends TimerTask {
		/** holds the observing interval in milliseconds */
		private static final int OBSERVING_INTERVAL = 200;

		/**
		 * holds a runnable object to run from the UI thread and update player's progress UI components
		 */
		private Runnable playerProgressUIComponentsUpdater = new Runnable() {
			/**
			 * Updates player progress UI components.
			 */
			@Override
			public void run() {
				Logger.d(TAG, "playerProgressUIComponentsUpdater.run()");
				if(PlayerFragment.this.getUserVisibleHint() && audioPlayer != null) {
					updatePlayerProgressUIComponents();
				}
				Logger.d(TAG, "playerProgressUIComponentsUpdater.run() ended");
			}
		};

		/**
		 * Runs the task.
		 */
		@Override
		public void run() {
			Logger.d(TAG, "PlayerPlaybackObserver.run()");
			// updates player's progress UI components, from the UI thread
			Activity act = getActivity();
			if(act != null) {
				act.runOnUiThread(playerProgressUIComponentsUpdater);
			}
			Logger.d(TAG, "PlayerPlaybackObserver.run() ended");
		}
	}

	/**
	 * Called upon player's initialization - updates player's UI with the total time of the media to be played in a specific format.
	 */
	@Override
	public void onPlayerInitialization(final int mediaToBePlayedDuration) {

		Logger.d(TAG, "onPlayerInitialization()");

		initializePlayerUI(true);

		// sets the duration of the media to be played in graphical player's proper UI component,
		// after converting it to a pre-defined format (mm:ss)
		playerTotalTime.setText(TimeDateUtils.formatDuration(mediaToBePlayedDuration / 1000));

		Logger.d(TAG, "onPlayerInitialization() ended");
	}
	@Override
	public void onPlaybackStart(int startingPosition) {
		Logger.d(TAG, "onPlaybackStart()");

		// adds the ID of the being played message to the messages to mark as
		// deleted
		if (!message.isRead()) {
			((PlayerActivity)getActivity()).updateMessageToMarkAsReadStatus(message.getUid());
		}
		if(modelManager.getProximitySwitcher()){
				((PlayerActivity)getActivity()).registerProximityListener();
		} else {
			vvmApplication.acquireWakeLock();
		}

		// starts observing for player's playback progress
		startObservingPlayerPlaybackProgress();

		// updates player's play button UI
		updatePlayButtonUI(PlayButtonUIStates.PAUSE);

		Logger.d(TAG, "onPlaybackStart() ended");

	}

	@Override
	public void onPlaybackPause() {
		Logger.d(TAG, "onPlaybackPause()");
		// stops observing for player's playback progress
		stopObservingPlayerPlaybackProgress();

		// updates player's play button UI
		updatePlayButtonUI(PlayButtonUIStates.PLAY);
		if(modelManager.getProximitySwitcher()){
//			if(!isAutoPlayMode){
				((PlayerActivity)getActivity()).unregisterProximityListener();
//			}
//			mSensorManager.unregisterListener(mSensorEventListener);
		} else {
			vvmApplication.releaseWakeLock();
		}
		Logger.d(TAG, "onPlaybackPause() ended");

	}

	@Override
	public void onPlaybackStop() {
		Logger.d(TAG, "onPlaybackStop() isProximityHeld=" + ((PlayerActivity)getActivity()).wasProximityHold());
		// stops observing for player's playback progress
		stopObservingPlayerPlaybackProgress();

		// updates player's play button UI
		updatePlayButtonUI(PlayButtonUIStates.PLAY);

		// updates player's progress UI components (seek bar and progress time
		// text), since the audio playback is returned to its begining by the player
		updatePlayerProgressUIComponents();

//		if(!((PlayerActivity)getActivity()).wasProximityHold()){

			if(modelManager.getProximitySwitcher()){
//				if(!((PlayerActivity)getActivity()).wasProximityHold()) {
					((PlayerActivity) getActivity()).unregisterProximityListener();
//				}
			//				mSensorManager.unregisterListener(mSensorEventListener);
			} else {
				vvmApplication.releaseWakeLock();
			}
//		}
		Logger.d(TAG, "onPlaybackStop() ended");

	}

	@Override
	public void onPlaybackEnd() {
		Logger.d(TAG, "onPlaybackEnd()");

		// same behaviour as playback stop
		onPlaybackStop();

		Logger.d(TAG, "onPlaybackEnd() ended");
		if(isAutoPlayMode ) {
			Logger.d(TAG, "#####onPlaybackEnd()mespos ="+mespos);
			if(mespos+1 == totalMessages){
				((PlayerActivity)getActivity()).unregisterProximityListener();
				getActivity().finish();
			} else {
				goToNextMessage();
			}
		}
	}

	@Override
	public void onPlayerError(int errorCode) {
		Logger.d(TAG, "onPlayerError()");

		if (errorCode != AudioPlayer.PlayerError.FILE_NOT_FOUND) {
//			showToast(getString(R.string.playerError));
		}

		new PlayerSetErrorAsyncTask(errorCode).execute();

		audioPlayer.reset();
		if(modelManager.getProximitySwitcher()){
				((PlayerActivity)getActivity()).unregisterProximityListener();
		//			mSensorManager.unregisterListener(mSensorEventListener);
		} else {
			vvmApplication.releaseWakeLock();
		}

		Logger.d(TAG, "onPlayerError() ended");

	}
	/**
	 * Starts observing player's playback progress.
	 */
	private void startObservingPlayerPlaybackProgress() {

		Logger.d(TAG, "startObservingPlayerPlaybackProgress()");

		// in case a schedualed player playback observer time task doesn't exist
		if (playerPlaybackObserver == null) {
			playerPlaybackObserver = new PlayerPlaybackObserver();
			timer.schedule(playerPlaybackObserver,
					PlayerPlaybackObserver.OBSERVING_INTERVAL,
					PlayerPlaybackObserver.OBSERVING_INTERVAL);
		}

		Logger.d(TAG, "startObservingPlayerPlaybackProgress() ended");
	}

	/**
	 * Stops observing player's playback progress.
	 */
	private void stopObservingPlayerPlaybackProgress() {

		Logger.d(TAG, "stopObservingPlayerPlaybackProgress()");

		// in case a schedualed player playback observer time task exists
		if (playerPlaybackObserver != null) {
			// cancels the player playback observer time task
			playerPlaybackObserver.cancel();
			playerPlaybackObserver = null;
		}

		Logger.d(TAG, "stopObservingPlayerPlaybackProgress() ended");
	}

	/**
	 * Holds all possible UI states for player's play button.
	 */
	private static class PlayButtonUIStates {
		private static final int DISABLE = 0;
		private static final int PLAY = 1;
		private static final int PAUSE = 2;
	}

	private static class PlayButtonFlipperState {
		private static final int DOWNLOADING = 0;
		private static final int PLAY = 1;
	}

//	/**
//	 * Swaps application's audio source (between earphone and speaker).
//	 */
//	private void swapAudioSource() {
//
//		Logger.d(TAG, "swapAudioSource()");
////		if(!VVMApplication.getInstance().isCurrentlyApplicationAudioMode()){
//			VVMApplication.getInstance().setApplicationAudioMode();
////		}
//		VVMApplication.getInstance().setIsApplicationSpeakerOn(!VVMApplication.getInstance().isApplicationSpeakerOn());
//		audioManager.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
//		Logger.d(TAG, "swapAudioSource() ended. audioManager.isSpeakerphoneOn() = " + audioManager.isSpeakerphoneOn());
//
//		refreshSpeakerButton();
//
//	}
	static PlayerFragment init(Message message, int mespos, int totalMessages, boolean isAutoPlay) {
		PlayerFragment playerFrag = new PlayerFragment();
		// Supply val input as an argument.
		Bundle args = new Bundle();
		args.putParcelable(PlayerActivity.IntentExtraNames.CURRENT_MESSAGE, message);
		args.putInt("POSITION", mespos);
		args.putInt("TOTAL", totalMessages);
		args.putBoolean("AUTOPLAY", isAutoPlay);
		playerFrag.setArguments(args);

		return playerFrag;
	}

	@Override
	public void onResume() {
		super.onResume();
        if ( contactsChanged ) {
            new ContactLoaderAsync(message.getSenderPhoneNumber(), message).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            contactsChanged = false;
        }
		refreshSpeakerButton();
		refreshPlayButton();
	}

//	@Override
//	public void onPause() {
//		Logger.d(TAG, "####### onPause()  entered");
//		if(!((PlayerActivity)getActivity()).wasProximityHold()) {
//			if(audioPlayer.isPlaying()){
//				Logger.d(TAG, "####### onPause()  wasProximityHold = false and audioPlayer is playing");
//				audioPlayer.pause();
//			}
//
////			stopPlayer();
//		}
//		super.onPause();
//
//	}

	@Override
	public void onDestroy() {

        Logger.d(TAG, "onDestroy()  mespos=" + mespos);

        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        modelManager.removeEventListener(this);
        if ( audioPlayer != null ) {
            audioPlayer.release();
        }
        audioManager.abandonAudioFocus(this);

        audioManager = null;

        nextMessageButton = null;
        previousMessageButton = null;
        avatarImageView = null;
        savedImageView = null;
        urgentImageView = null;
        playButton = null;
        defaultSeekBar = null;
        playerProgressTime = null;
        playerTimeDelim = null;
        playerTotalTime = null;
        callButton = null;
        sendMsgButton = null;
        deleteButton = null;
        textTranscriptionBody = null;
        speakerButton = null;
        mProgressBar = null;
        contactUri = null;
        displayName = null;
        mContext = null;
        modelManager = null;
        phoneStateListener = null;
        telephonyManager = null;
        message = null;
        bgImageView = null;
        downloadingGauge = null;
//        powerManager = null;
//        wakeLock = null;
//        mSensorManager = null;
//        mProximity = null;
        audioPlayer = null;
//        mSensorEventListener = null;
        toolbar = null;
        numberOfmessages = null;

        super.onDestroy();
    }


	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = VVMApplication.getContext();
		modelManager = ModelManager.getInstance();
		telephonyManager = (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);
        phoneStateListener = new CustomPhoneStateListener();
		vvmApplication = VVMApplication.getInstance();
		if(getArguments() != null ){
			message = getArguments().getParcelable(PlayerActivity.IntentExtraNames.CURRENT_MESSAGE);
			mespos = getArguments().getInt("POSITION");
			totalMessages = getArguments().getInt("TOTAL");
			isAutoPlayMode = getArguments().getBoolean("AUTOPLAY");
            Logger.d(TAG, "onCreate()  mespos=" + mespos);
		}
		audioPlayer = new AudioPlayer();
        audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
		if(VVMApplication.getInstance().isAccesibilityOn() /*&& (!VVMApplication.getInstance().isCurrentlyApplicationAudioMode())*/){
			VVMApplication.getInstance().setApplicationAudioMode();
			audioManager.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
		}
		refreshSpeakerButton();
		setHasOptionsMenu(true);
		modelManager.addEventListener(this);
		int events = PhoneStateListener.LISTEN_CALL_STATE;
        telephonyManager.listen(phoneStateListener, events);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.playerpage, null);

		nextMessageButton = (ImageView) view.findViewById(R.id.nextButton);
		previousMessageButton = (ImageView) view.findViewById(R.id.previousButton);
		numberOfmessages = (TextView) view.findViewById(R.id.numberOfMessages);
		// gets contact's avatar imageview
		avatarImageView = (QuickContactBadge) view.findViewById(R.id.senderImage);
		bgImageView = (ImageView) view.findViewById(R.id.player_user_bg_image);

		// gets other UI components and sets their typeface if needed
		nameTextView = (TextView) view.findViewById(R.id.name);
		dateTextView = (TextView) view.findViewById(R.id.date);
		phoneTypeTextView = (TextView) view.findViewById(R.id.phoneType);
		phoneTypeLayout = (View) view.findViewById(R.id.userPhoneTypeLayout);
		savedImageView = (ImageView) view.findViewById(R.id.savedStatus);
		urgentImageView = (ImageView) view.findViewById(R.id.urgentStatus);
		playButton = (ImageView) view.findViewById(R.id.playButton);
		speakerText = (TextView) view.findViewById(R.id.speaker_text);

		// gets the number of messages textview and sets its typeface (cannot be done via XML)
		numberOfmessages.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
		nameTextView.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Medium));
		dateTextView.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
		phoneTypeTextView.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));
		speakerText.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Light));
		downloadingGauge = (LoadingProgressBar) view.findViewById(R.id.downloadingGauge);

		defaultSeekBar = (SeekBar) view.findViewById(R.id.seekBarDefault);

		playerProgressTime = (TextView) view.findViewById(R.id.progressTime);
		playerTimeDelim = (TextView) view.findViewById(R.id.timeDelim);
		playerTotalTime = (TextView) view.findViewById(R.id.playerTotalTime);
		callButton = (ImageView) view.findViewById(R.id.callBack);
		sendMsgButton = (ImageView) view.findViewById(R.id.sendMessage);
		deleteButton = (ImageView) view.findViewById(R.id.delete);
		textTranscriptionBody = (TextView) view.findViewById(R.id.textTranscriptionBody);

		textTranscriptionBody.setVerticalFadingEdgeEnabled(true);

		playerProgressTime.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Medium));
		playerTotalTime.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Medium));
		textTranscriptionBody.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Regular));

		// Creating buttons listener move from loadspeaker to earphone
		speakerButton = (ImageView) view.findViewById(R.id.loadspeaker);
		numberOfmessages.setText((mespos + 1) + " of " + totalMessages);
		LinearLayout messageNumberLayout = (LinearLayout) view.findViewById(R.id.messageTitleLayout);
		messageNumberLayout.setContentDescription(getString(R.string.messageText) + numberOfmessages.getText());
		if(message.getSavedState() == Message.SavedStates.INTERNAL_STORAGE_AS_SAVED){
			savedImageView.setVisibility(View.VISIBLE);

		}
		previousMessageButton.setEnabled(mespos > 0);
		previousMessageButton.invalidate();
		nextMessageButton.setEnabled(mespos + 1 < totalMessages);
//		nextMessageButton.invalidate();
		toolbar = (Toolbar) view.findViewById(R.id.toolbar);

		if (toolbar != null) {
			((PlayerActivity)getActivity()).setSupportActionBar(toolbar);
			ActionBar supportActionBar = ((PlayerActivity)getActivity()).getSupportActionBar();
			supportActionBar.setDisplayShowTitleEnabled(false);
			supportActionBar.setHomeButtonEnabled(true);
			supportActionBar.setDisplayHomeAsUpEnabled(true);
		}
		initContactInfo();
		updateContactDetailsUI();
		updateMessageTranscriptionUI();

        //if(this.getUserVisibleHint()) {
			updatePlayer();
		//}

		initButtonsListeners();
		refreshSpeakerButton();

        if(isAutoPlayMode && mespos == 0 && this.getUserVisibleHint()){
			if(modelManager.getProximitySwitcher()){
				((PlayerActivity)getActivity()).registerProximityListener();
			} else {
				vvmApplication.acquireWakeLock();
			}
	        handlePlayButtonClick(null);
        }

		return view;

	}
	public void refreshSpeakerButton() {
		if(speakerButton != null){
			if(VVMApplication.getInstance().isApplicationSpeakerOn()){
				speakerButton.setImageResource(R.drawable.ic_speaker);
				speakerButton.setContentDescription(getString(R.string.speaker_on));
				speakerText.setText(R.string.speaker_on);
			}else{
				speakerButton.setImageResource(R.drawable.ic_speaker_off);
				speakerButton.setContentDescription(getString(R.string.speaker_off));
				speakerText.setText(R.string.speaker_off);
			}
			speakerButton.invalidate();
		}
	}

	/**
	 * Updates message's transcription text UI component.
	 */
	private void updateMessageTranscriptionUI() {

		Logger.d(TAG, "updateMessageTranscriptionUI()");
		// updates message's transcription text
		textTranscriptionBody.setMovementMethod(LinkMovementMethod.getInstance());
		textTranscriptionBody.setLinksClickable(true);
		textTranscriptionBody.setText(Html.fromHtml(message.getTranscription()));

	}


	/**
	 * Updates message's details UI compoents (contact name & avater etc.)
	 */
	 void updateContactDetailsUI() {

		Logger.d(TAG, "updateMessageDetailsUI()");
		 String phoneNumber = message.getSenderPhoneNumber();
		Uri imageUri = message.getContactImageUri();
		 if(isVisible) {
			 setStatusBarColor();
		 }
		if(imageUri != null){
			avatarImageView.setVisibility(View.VISIBLE);
			loadContactImages(imageUri);
			avatarImageView.invalidate();
		}else{
			if(bgImageView != null) {
				bgImageView.setColorFilter(Color.argb(0, 255, 255, 255));
				int color = Utils.getDefaultAvatarBackground(phoneNumber);
				bgImageView.setBackgroundColor(getContext().getResources().getColor(color));
				PicassoUtils.loadDefaultImage(bgImageView , R.drawable.avatar);

			}
			if(avatarImageView != null)
			avatarImageView.setVisibility(View.GONE);
			contactImageUri = null;
		}

		callButton.setEnabled(!isValidPhoneNumber(phoneNumber));
		//callButton.invalidate();
		sendMsgButton.setEnabled(!isValidPhoneNumber(phoneNumber));
		sendMsgButton.invalidate();
		// set the quick contact badge with the contact details
		if (message.getContactLookupKey() != null) {
			avatarImageView.setClickable(true);
			avatarImageView.assignContactUri(Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI,	message.getContactLookupKey()));
			avatarImageView.setContentDescription(mContext.getString(R.string.contactInfoTxt));
		} else if (phoneNumber != null && phoneNumber.length() > 0 && (!phoneNumber.equals(mContext.getString(R.string.welcomeMessagePhoneNumber)))) {
			avatarImageView.setClickable(true);
			avatarImageView.assignContactFromPhone(phoneNumber, true);
			avatarImageView.setContentDescription(mContext.getString(R.string.addToContactsTxt));
		} else {
			avatarImageView.setClickable(false);
			avatarImageView.setContentDescription(mContext.getString(R.string.contactDisabledTxt));
		}
		avatarImageView.invalidate();
		String displayName = getFormattedDisplayName(message.getSenderDisplayName());
		initSenderName(displayName);

		// updates the date text
		dateTextView.setText(TimeDateUtils.getFriendlyDate(message.getDateMillis(), mContext, false));

		if (message.getPhoneNumberLabel() != null) {
			phoneTypeLayout.setVisibility(View.VISIBLE);
			phoneTypeTextView.setText(message.getPhoneNumberLabel());
		} else {
			phoneTypeLayout.setVisibility(View.INVISIBLE);
		}

		// update message urgent status
		urgentImageView.setVisibility(message.isUrgent() ? View.VISIBLE : View.INVISIBLE);

		Logger.d(TAG, "updateMessageDetailsUI() ended");
	}

	private void initSenderName(String displName) {
		if ( displName == null ) {
			Logger.d(TAG, "initSenderName for NULL");
			return;
		}
		Logger.d(TAG, "initSenderName displName=" + displName);
		if ( senderDisplayName == null || !senderDisplayName.equals(displName) ) {
			nameTextView.setText(displName);
			senderDisplayName = displName;
			nameTextView.invalidate();
		}
	}

	private void loadContactImages(Uri newContactImageUri) {
		if ( newContactImageUri == null ) {
			Logger.d(TAG, "loadContactImages for NULL uri");
			return;
		}
		Logger.d(TAG, "loadContactImages newContactImageUri=" + newContactImageUri.toString());
		if ( contactImageUri==null || !contactImageUri.equals(newContactImageUri) ) {
			PicassoTools.clearCache(Picasso.with(getActivity()));
			Picasso.with(VVMApplication.getContext()).load(newContactImageUri).memoryPolicy(MemoryPolicy.NO_CACHE).into(bgImageView);
			Picasso.with(VVMApplication.getContext()).load(newContactImageUri).transform(new PicassoUtils.CircleTransform()).memoryPolicy(MemoryPolicy.NO_CACHE).error(R.drawable.avatar_no_photo).into(avatarImageView);
			contactImageUri = newContactImageUri;
		}
	}
	/**
	 * Initializes player's UI.
	 * @param audioFileExists (boolean) true in case message's audio file attachment exist, false otherwise.
	 */
	private void initializePlayerUI(final boolean audioFileExists) {

		Logger.d(TAG, " initializePlayerUI() audioFileExists=" + audioFileExists+ " messageId = "+( message != null ? message.getRowId(): "null") + " mespos = "+ mespos);

		if (audioFileExists) {
			refreshPlayButton();

			setPlayButton(PlayButtonFlipperState.PLAY);

			downloadingGauge.clearAnimation();
			defaultSeekBar.setEnabled(true);

			playerProgressTime.setVisibility(View.VISIBLE);
			playerTotalTime.setVisibility(View.VISIBLE);
			playerTimeDelim.setVisibility(View.VISIBLE);

			updatePlayerProgressUIComponents();
		} else {
			defaultSeekBar.setProgress(0);
			// sets player's UI to be un playable
			updatePlayButtonUI(PlayButtonUIStates.DISABLE);
			// set elapsed time and total time to 0
			playerProgressTime.setText(DateUtils.formatElapsedTime(0));
			playerTotalTime.setText(TimeDateUtils.formatDuration(0));
			playerProgressTime.setVisibility(View.INVISIBLE);
			playerTotalTime.setVisibility(View.INVISIBLE);
			playerTimeDelim.setVisibility(View.INVISIBLE);

			defaultSeekBar.setEnabled(false);

			if (message == null || message.getSavedState() == Message.SavedStates.ERROR) {
				setPlayButton(PlayButtonFlipperState.PLAY);
				updatePlayButtonUI(PlayButtonUIStates.DISABLE);
				if ( downloadingGauge.isStart() ) {
					downloadingGauge.stop();
				}

				Utils.showToast(R.string.playerScreenDowbloadFileError, Toast.LENGTH_LONG);

			}
			// on low memory we do not display the downloading animation - but display the player in disabled mode
			else if (VVMApplication.isMemoryLow()) {
				// no memory error should appear when there is a problem with memory and the file does not exists
				setPlayButton(PlayButtonFlipperState.PLAY);
				updatePlayButtonUI(PlayButtonUIStates.DISABLE);
				if ( downloadingGauge.isStart() ) {
					downloadingGauge.stop();
				}
				Utils.showToast(R.string.noMemoryError, Toast.LENGTH_LONG);
			} else {
				Logger.d(TAG, "initializePlayerUI() start downloading gauge");
				setPlayButton(PlayButtonFlipperState.DOWNLOADING);
				if ( !downloadingGauge.isStart() ) {
					downloadingGauge.start();
				}
				// no memory error should not appear when there is no problem with memory - that means the file is in the downloading state
			}
		}

		Logger.d(TAG, "initializePlayerUI() ended");
	}

	private void refreshPlayButton() {
		// sets player's UI to be ready to play
		if (telephonyManager != null && telephonyManager.getCallState() != TelephonyManager.CALL_STATE_OFFHOOK) {
			if(audioPlayer != null && audioPlayer.isPlaying()){
				updatePlayButtonUI(PlayButtonUIStates.PAUSE);

			} else {
				updatePlayButtonUI(PlayButtonUIStates.PLAY);
			}
        } else {
			if(audioPlayer != null && audioPlayer.isPlaying()) {
				audioPlayer.pause();
				updatePlayButtonUI(PlayButtonUIStates.PLAY);
			}
				updatePlayButtonUI(PlayButtonUIStates.DISABLE);
        }
	}

	private void setPlayButton ( int state ) {

		if ( state == PlayButtonFlipperState.PLAY ) {
			playButton.setVisibility(View.VISIBLE);
			downloadingGauge.setVisibility(View.GONE);
		} else if ( state == PlayButtonFlipperState.DOWNLOADING ) {
			playButton.setVisibility(View.GONE);
			downloadingGauge.setVisibility(View.VISIBLE);
		}
	}
	/**
	 * Updates player's play button UI.
	 *
	 * @param playButtonType (int) the new type of the play button after the UI update.
	 */
	private void updatePlayButtonUI(final int playButtonType) {

        Logger.d(TAG, "updatePlayButtonUI()");
        if (!this.getUserVisibleHint() || playButton==null) {
            return;
        }

		// according to the requested play button type
		switch (playButtonType) {
			// in case the play button type should be 'play' button
			case (PlayButtonUIStates.PLAY): {
				// sets play button's background and enables it in case it was
				// previously disabled
				playButton.setImageResource(R.drawable.ic_play);
				playButton.setContentDescription(mContext.getString(R.string.play));
				playButton.setEnabled(true);
				break;
			}
			// in case the play button type should be disabled 'play' button
			case (PlayButtonUIStates.DISABLE): {
				// sets play button's background and disables it
				playButton.setImageResource(R.drawable.ic_play_disabled);
				playButton.setContentDescription(mContext.getString(R.string.play));
				playButton.setEnabled(false);

				break;
			}
			// in case the play button type should be 'pause' button
			case (PlayButtonUIStates.PAUSE): {
				// sets play button's background and disables it
				playButton.setImageResource(R.drawable.ic_stop);
				playButton.setContentDescription(mContext.getString(R.string.pause));
				playButton.setEnabled(true);

				break;
			}
			default:
				break;
		}

		Logger.d(TAG, "updatePlayButtonUI() ended");
	}
	/**
	 * Updates player's progress UI components - seek bar's progress and progress time text.
	 */
	private void updatePlayerProgressUIComponents() {

		Logger.d(TAG, "updatePlayerProgressUIComponents()");

		updatePlayerSeekBarProgressUIComponent();
		updatePlayerProgressTextUIComponent();

		Logger.d(TAG, "updatePlayerProgressUIComponents() ended");
	}

	/**
	 * Updates player's seek bar progress UI component.
	 */
	private void updatePlayerSeekBarProgressUIComponent() {

		// gets the current position of the being played media, and its duration
		int mediaDuration =/*mActivity.*/audioPlayer.getMediaDuration();
		int mediaCurrentPosition =/*mActivity.*/audioPlayer.getCurrentPosition();

		// sets player's seek bar progress according to the being handled media progress
		int progress = (mediaDuration > 0) ? (defaultSeekBar.getMax() * mediaCurrentPosition / mediaDuration) : 0;
		//Logger.d(TAG, "updatePlayerSeekBarProgressUIComponent progress=" + progress);
		defaultSeekBar.setProgress(progress);

	}

	/**
	 * Updates player's progress text UI component.
	 */
	private void updatePlayerProgressTextUIComponent() {

		Logger.d(TAG, "updatePlayerProgressTextUIComponent()");

		// gets the the being played media duration
		int mediaCurrentPosition = /*mActivity.*/audioPlayer.getCurrentPosition();

		// gets media's progress time as text (mm:ss)
		// updates player's progress time UI component
		playerProgressTime.setText(DateUtils.formatElapsedTime(mediaCurrentPosition / 1000));

		Logger.d(TAG, "updatePlayerProgressTextUIComponent() ended");
	}

	private void initButtonsListeners() {

		defaultSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			/**
			 * Called upon seek bar's progress change.
			 *
			 * @param seekBar (SeekBar != null) the seek bar which its progress was changed.
			 * @param progress (int) seek bar's progress level (0 - bar's max value or 100).
			 * @param fromUser (boolean) true in case the progress change was due to user event, false
			 *            otherwise.
			 */
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

				Logger.d(TAG, "onProgressChanged()");
				// only in case the user has changed seek bar's progress
				if (fromUser) {
					// calculates the new position of media currently being played
					int seekBarProgress = seekBar.getProgress();
					int seekBarMax = seekBar.getMax();
					int playbackPosition = (seekBarProgress * /*mActivity.*/audioPlayer.getMediaDuration()) / seekBarMax;

					// seeks player's playback from the new media position
					/*mActivity.*/
					audioPlayer.seekTo(playbackPosition);

					// in case the media handled by the player is not being played at the moment, an update to player's UI (seek bar and progress
					// text) must be done manually, since there is no observation on the player which updates those UI components every defined intervaled time
					if (!/*mActivity.*/audioPlayer.isPlaying()) {
						updatePlayerProgressUIComponents();
					}
				}
			}

			/**
			 * Notification that the user has started a touch gesture. Clients may want to use this to disable advancing the seekbar.
			 */
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			/**
			 * Notification that the user has started a touch gesture. Clients may want to use this to disable advancing the seekbar.
			 */
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
		playButton.setOnClickListener(new View.OnClickListener() {
			/**
			 * Called upon button's click.
			 *
			 * @param button (View != null) the clicked button.
			 */
			public void onClick(View button) {

					handlePlayButtonClick(button);
			}
		});
		// sets call button's on click listener
		callButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// einat : 	although we have the PHONE permissions group (for requesting READ_PHONE_STATE, android is not committed that the groups
				// 			will remain the same in the future. As a precaution - check for the call_phone permission
				if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
					ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.CALL_PHONE}, REQUEST_CALL_PHONE_PERMISSION);
					return;
				}

				call();
			}

		});

		sendMsgButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				try {
					Log.i(TAG, "sendMsgButton.onClick");
					String phoneNumber = message.getSenderPhoneNumber();
					Intent intent = new Intent(Intent.ACTION_SENDTO);
					intent.setData(Uri.parse("sms:" + phoneNumber));
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					mContext.startActivity(intent);
				} catch (Exception e) {
					Log.e(TAG, "Failed to invoke SMS", e);
				}
			}
		});

		// sets delete message button's on click listener
		deleteButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				/*mActivity.*/
				audioPlayer.pause();
				final FragmentActivity activity = getActivity();

				AlertDlgUtils.showRightAlignedDialog(activity, R.string.deleteMessageDialogTitle, R.string.deleteDialogMessage, R.string.deleteCAPS, R.string.cancelCAPS, new AlertDlgUtils.AlertDlgInterface() {
					@Override
					public void handlePositiveButton(View view) {
						PlayerActivity playerActivity = (PlayerActivity) getActivity();
						if(playerActivity != null && message != null) {
							playerActivity.deleteVMs(new Long[]{message.getRowId()});
						}
					}

					@Override
					public void handleNegativeButton(View view) {
					}
				});


			}
		});
		speakerButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				((PlayerActivity)getActivity()).swapAudioSource();
				refreshSpeakerButton();
				AccessibilityUtils.sendEvent(v.getContentDescription().toString(), v);
			}
		});
		nextMessageButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				goToNextMessage();
			}
		});

		previousMessageButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				((PlayerActivity) getActivity()).switchPage(mespos - 1 >= 0 ? mespos - 1 : mespos);
			}
		});
		increaseButtonHitArea(nextMessageButton);
		increaseButtonHitArea(previousMessageButton);
	}


	private void increaseButtonHitArea(final View btn){
	 final View parent = (View) btn.getParent();  // button: the view you want to enlarge hit area
		parent.post(new Runnable() {
			public void run() {
				final Rect rect = new Rect();
				btn.getHitRect(rect);
				rect.top -= 50;    // increase top hit area
				rect.left -= 50;   // increase left hit area
				rect.bottom += 50; // increase bottom hit area
				rect.right += 50;  // increase right hit area
				parent.setTouchDelegate(new TouchDelegate(rect, btn));
			}
		});
 }

	private void call() {
		try {
			String phoneNumber = message.getSenderPhoneNumber();
			Intent intent = new Intent();
			if (message.getSenderPhoneNumber() == null || message.getSenderPhoneNumber().length() == 0) {
				intent.setAction(Intent.ACTION_VIEW);
				intent.setData(ContactsContract.Contacts.CONTENT_URI);
			} else {
				intent.setAction(Intent.ACTION_CALL);
				intent.setData(Uri.parse("tel:" + phoneNumber));
			}
			startActivity(intent);
		} catch (Exception e) {
			Log.e(TAG, "Failed to invoke call", e);

		}
	}

	private void openCallScreen() {
		try {
			String phoneNumber = message.getSenderPhoneNumber();
			Intent intent = new Intent();
			if (message.getSenderPhoneNumber() == null || message.getSenderPhoneNumber().length() == 0) {
				intent.setAction(Intent.ACTION_VIEW);
				intent.setData(ContactsContract.Contacts.CONTENT_URI);
			} else {
				intent.setAction(Intent.ACTION_DIAL);
				intent.setData(Uri.parse("tel:" + phoneNumber));
			}
			startActivity(intent);
		} catch (Exception e) {
			Log.e(TAG, "Failed to invoke call", e);

		}
	}

	private void goToNextMessage() {
		((PlayerActivity)getActivity()).switchPage((mespos + 2) >= totalMessages ? mespos + 2 : mespos + 1);
	}

	/**
	 * Handles the click event on player's play button. Note: for the media to be played, the player must first be
	 * initialized with the media.
	 *
	 * @param playButton (Button != null) the clicked player's play button.
	 */
	private void handlePlayButtonClick(View playButton) {

		Logger.d(TAG, "#####handlePlayButtonClick()mespos ="+mespos);

		// in case the player is currently playing a media
		if (audioPlayer.isPlaying()) {
			// pauses player's playback
			audioPlayer.pause();
			return;
		}

		// calculates the new position of media currently being handled by the
		// player
		int seekBarProgress = defaultSeekBar.getProgress();
		int seekBarMax = defaultSeekBar.getMax();

		int mediaDuration = audioPlayer.getMediaDuration();
		if (mediaDuration == -1) {
			//updatePlayer();
			audioPlayer.initializeMedia(message.getFileFullPath(mContext));
			mediaDuration = audioPlayer.getMediaDuration();
		}
		if (mediaDuration != -1) {
			int playbackPosition = (seekBarProgress * mediaDuration) / seekBarMax;

			vvmApplication.setApplicationAudioMode();
			audioManager.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
			refreshSpeakerButton();

			// starts player's playback from the new media position
			audioPlayer.start(playbackPosition);
		} else {
				Utils.showToast(R.string.playerError, Toast.LENGTH_LONG);

		}

		Logger.d(TAG, "handlePlayButtonClick() ended");
	}
	public void updatePlayer() {
		Logger.d(TAG, "updatePlayer message.getFileName()=" + message.getFileName()+ " messageId = "+ message.getRowId() + " mespos = "+ mespos);
		audioPlayer.registerAudioPlayerEventsListener(this);
		if (message.getFileName() == null) {
			updateMessage();
		}

		if (message.getFileName() == null) {
			audioPlayer.reset();
			initializePlayerUI(false);
		} else {
			audioPlayer.initializeMedia(message.getFileFullPath(mContext));
			initializePlayerUI(true);
		}
	}
	/**
	 * Called upon first creation of screen's menu.
	 *
	 * @param menu (Menu != null) the created menu.
	 */
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.player_menu, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	/**
	 * Send text, Send voice, Save (Disable if voice is not available), Forward (Disable if voice is not available),
	 * More: View caller details (disable if no match), Add to contacts (Disable if calling number is blocked), Copy
	 * message text (Disable if transcription is not available)
	 */


	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);

		boolean isVoiceFileExist = (message != null) && (message.getFileName() != null);
		boolean isError = (message != null) && (message.getSavedState() == Message.SavedStates.ERROR);
		boolean isSaved = (message != null) && (message.getSavedState() == Message.SavedStates.INTERNAL_STORAGE_AS_SAVED);

		// gets the "Save" menu option
		MenuItem save = menu.findItem(R.id.menu_save);
		MenuItem unsave = menu.findItem(R.id.menu_unsave);
		// in case the audio attachment was not yet downloaded
		// or device's external storage doesn't exist at the moment
		// or the message is already marked as saved, disables the "Save" menu option
		if(save != null && unsave != null){
			if(isSaved){
				save.setVisible(false);
				unsave.setVisible(true);
				unsave.setEnabled((message != null) && (isVoiceFileExist && !isError));
			} else {
				save.setVisible(true);
				save.setEnabled((message != null) && (isVoiceFileExist && !isSaved && !isError));
				unsave.setVisible(false);
			}
		}

		// "Share" menu option
		MenuItem share = menu.findItem(R.id.menu_share);
		if(share != null){
			share.setEnabled(isVoiceFileExist && !isError);
		}
		// "Export" menu option
		MenuItem export = menu.findItem(R.id.menu_export);
		if (export != null) {
			export.setEnabled((message != null) && (isVoiceFileExist && !isError && VvmFileUtils.isExternalStorageExist()));
		}
		// viewCallerDetails - invisible if sender is not a contact
		MenuItem viewCallerDetails = menu.findItem(R.id.menu_view_caller_details);
		MenuItem addToContacts = menu.findItem(R.id.menu_add_to_contacts);
		if(viewCallerDetails != null && addToContacts != null) {
			if ((message != null) && (message.getContactLookupKey() != null)) {
				viewCallerDetails.setVisible(true);
				viewCallerDetails.setEnabled(true);
				addToContacts.setVisible(false);
			} else {
				viewCallerDetails.setVisible(false);
				addToContacts.setVisible(true);
				if ((message != null) && (message.getSenderPhoneNumber() != null
						&& message.getSenderPhoneNumber().length() > 0
						&& !message.getSenderPhoneNumber().equals(getString(R.string.welcomeMessagePhoneNumber)))) {
					addToContacts.setEnabled(true);
				} else {
					addToContacts.setEnabled(false);
				}
			}
		}
		//also remove "Contact details" / "Add to Contanct" for Private number
		if(message != null && (TextUtils.isEmpty(message.getSenderPhoneNumber())
				|| message.getSenderPhoneNumber().equalsIgnoreCase(getActivity().getString(R.string.unknown)) || message.getSenderPhoneNumber().equals(getString(R.string.welcomeMessagePhoneNumber)))){
			viewCallerDetails.setVisible(false);
			addToContacts.setVisible(false);
		}

		// Copy message text - disabled if no transcription
		MenuItem copyMessageText = menu.findItem(R.id.menu_copy_message_text);
		if (copyMessageText != null){
			String messageText = message != null ? message.getTranscription().trim() : null;
			// " " means no transcription
			if (TextUtils.isEmpty(messageText)
					|| messageText.equals(ModelManager.NO_TRANSCRIPTION_STRING)
					|| messageText.equals(getString(R.string.trascriptionErrorText))
					|| messageText.equals(getString(R.string.noTranscriptionMessage))) {
				copyMessageText.setEnabled(false);
			} else {
				copyMessageText.setEnabled(true);
			}
		}
	}


	@TargetApi(Build.VERSION_CODES.M)
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		String phoneNumber = message != null ? message.getSenderPhoneNumber(): null;
		PlayerActivity activity = (PlayerActivity)getActivity();
		// Handle item selection
		switch (item.getItemId()) {

			case android.R.id.home:
				activity.onBackPressed();
				stopPlayer();
				return true;

			case R.id.menu_save:
                if ( activity != null ) {
                    activity.markMessagesAs(new Long[]{Long.valueOf(message.getRowId())}, Message.SavedStates.INTERNAL_STORAGE_AS_SAVED );
                }
				break;
			case R.id.menu_unsave:
				if ( activity != null ) {
					activity.markMessagesAs(new Long[]{Long.valueOf(message.getRowId())}, Message.SavedStates.INTERNAL_STORAGE );
				}
				break;

			case R.id.menu_add_to_contacts:
				if(phoneNumber != null){
					try {
						Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
						intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
						intent.putExtra(ContactsContract.Intents.Insert.PHONE,phoneNumber);
						intent.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE,	android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
						intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(intent);
					} catch (Exception e) {
						Log.e(TAG, "Failed to invoke contacts activity",e);
					}
				}
				break;

			case R.id.menu_export:
				if (getActivity()!= null && ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
					exportTofile();
				} else {
					requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_EXPORT);
				}
				break;

			case R.id.menu_share:
				if (getActivity()!= null && ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
					shareFile();
				} else {
					requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_SHARE);
				}
				break;
			case R.id.menu_view_caller_details:
				viewCallerDetails();
				break;

			case R.id.menu_copy_message_text:
				String text = message.getTranscription();
				if (text != null) {
					copyTextToClipboard(text);
				}

				break;

			default:
//				super.onOptionsItemSelected(item);
				break;
		}

		// option has been selected, menu will be closed
//		isOptionsMenuOpened = false;

		return true;
	}
	/**
	 * open the contacts activity to view the caller details in the address book
	 */
	private void viewCallerDetails() {

		Logger.d(TAG, "PlayerActivity.viewCallerDetails()");

		try {
			Intent intent = new Intent(Intent.ACTION_VIEW);
			Uri lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI,
					message.getContactLookupKey());
			intent.setData(lookupUri);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		} catch (Exception e) {
			Logger.e(TAG,"Failed to invoke contacts activity", e);
		}
	}
	private ContactObject contactObject;

	private void initContactInfo(){
		 contactObject =ContactUtils.getContact(message.getSenderPhoneNumber());
		if(message != null) {
			if(contactObject != null){

				String displayName = contactObject.getDisplayName();
				Logger.i(TAG, "#### ContactLoaderAsync displayName=" + displayName);
				message.setSenderDisplayName(TextUtils.isEmpty(displayName) ? message.getSenderPhoneNumber() : displayName);
				String phoneTypeText = (contactObject.getPhoneType() == android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM	&& !TextUtils.isEmpty(contactObject.getPhoneLabel())) ? contactObject.getPhoneLabel() : ContactUtils.getPhoneTypeText(contactObject.getPhoneType());
				message.setPhoneNumberLabel(phoneTypeText);
				Uri contactPhotoUri = contactObject.getPhotoUri();
				if(contactPhotoUri != null ){
					message.setContactImageUri(contactPhotoUri);
				} else {
					message.setContactImageUri(null);
				}
				if (!TextUtils.isEmpty(contactObject.getContactLookup())) {
					message.setContactLookupKey(contactObject.getContactLookup());
				}
			} else {
				message.setSenderDisplayName(!TextUtils.isEmpty(message.getSenderPhoneNumber()) ? message.getSenderPhoneNumber() : mContext.getString(R.string.privateNumber));
				message.setContactLookupKey(null);
				message.setSenderBitmap(null);
				message.setContactImageUri(null);
				message.setPhoneNumberLabel(null);
			}
		}
	}
	/**
	 * copy text to system clipboard using 2 different methods for ICS and for older SKDs
	 *
	 * @param text
	 */
	private void copyTextToClipboard(String text) {
		//remove this for welcome message
		text = text.replace("<br/>", "");

		try {
			ClipboardManager clipboard = (ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);
			ClipData clip = ClipData.newPlainText("transcription", text);
			// Set the clipboard's primary clip.
			clipboard.setPrimaryClip(clip);
			Utils.showToast(R.string.text_copied_toast, Toast.LENGTH_LONG);

		} catch (Exception e) {
			Log.e(TAG, "Failed to copy transcription to clipboard", e);
		}

	}


	/**
	 * save file to external music directory and show chooser menu if no external memory, file is saved to internal
	 * application memory note - gmail limitation - can't send attachment from internal memory - email with arrive with
	 * no attachment.
	 */
	private void shareFile() {

		Logger.d(TAG, "shareFile()");
		try {
			String fileName = message.getFileName();
			boolean success = false;

			// file exists and
			if (fileName != null) {
				// the file is in internal storage
				Time time = new Time();
				time.set(message.getDateMillis());
				String sharedFileName = "voicemail-shared-"
						+ time.format(Constants.SHARED_FILE_TIME_FORMAT);

				// for messages that include mp3 file - like the welcome message.
				if (fileName.endsWith(".mp3")) {
					sharedFileName += ".mp3";
				} else {
					sharedFileName += ".amr";
				}

				// try to share using the external storage
				success = VvmFileUtils.copyFileToDeviceExternalStorage(mContext, message.getFileName(), sharedFileName, Environment.DIRECTORY_MUSIC, true);
				Logger.d(TAG, "shareFile() save files to external storage, result = " + success);

				if (success) {
					Logger.d(TAG, "shareFile() file saved to external app storage, going to share it");
					shareAudioFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath(), sharedFileName);
				}else{
					// try to share using the internal storage
					success = VvmFileUtils.copyFileAsReadable(mContext, message.getFileName(), sharedFileName);

					if (success) {
						Logger.d(TAG, "shareFile() file saved to internal app storage, going to share it");
						shareAudioFile(mContext.getFilesDir().getAbsolutePath(), sharedFileName);
					}
				}

			}
		} catch (Exception e) {
			Log.e(TAG, "Failed to share attachment", e);
		}
	}

	private void shareAudioFile(String directory, String fileName){
		VVMActivity.showAudioShareMenu(mContext, Uri.parse("file://" + directory + "/" + fileName));
	}
	/**
	 * save the actual voice file to Music Directory
	 */
	private boolean exportTofile() {

		Logger.d(TAG, "exportTofile()");

		// constructs files
		Time time = new Time();
		time.set(message.getDateMillis());

		String fileName = message.getFileName();

		if (fileName != null) {
			String sharedFileName = "voicemail-"+ time.format(Constants.SHARED_FILE_TIME_FORMAT);

			// for messages that include mp3 file - like the welcome message.
			if (fileName.endsWith(".mp3")) {
				sharedFileName += ".mp3";
			} else {
				sharedFileName += ".amr";
			}

			// in case file's copy to device's external storage succeed
			if (VvmFileUtils.copyFileToDeviceExternalStorage(mContext,
					message.getFileName(), sharedFileName, Environment.DIRECTORY_MUSIC,
					false)) {
				// displays notification to the user that the file has been saved
				String folder = Environment.getExternalStoragePublicDirectory(
						Environment.DIRECTORY_MUSIC).toString();
				if (folder != null) {
					folder = folder.substring(folder.lastIndexOf('/'));
				}
				Utils.showToast(getString(R.string.exported) + " \n " + folder + "/" + sharedFileName, Toast.LENGTH_LONG);

				return true;
			}

			// displays notification to the user that the file hasn't been saved
			Utils.showToast(R.string.you_dont_have_enough_space, Toast.LENGTH_LONG);

		}
		return false;
	}
	private boolean isValidPhoneNumber(String phoneNum) {
		return phoneNum == null
				|| "".equals(phoneNum)
				|| phoneNum.equals(this
				.getString(R.string.welcomeMessagePhoneNumber));
	}
	private boolean  isVisible = false;

	@Override
	public void setUserVisibleHint(boolean isVisibleToUser) {
		super.setUserVisibleHint(isVisibleToUser);
		if (isVisibleToUser && getActivity() != null) {
			setStatusBarColor();

			isVisible = true;
			refreshSpeakerButton();
			if(this.getView() != null) {
				if (audioPlayer != null) {
					updatePlayer();
					if(isAutoPlayMode){
						if (telephonyManager != null && telephonyManager.getCallState() != TelephonyManager.CALL_STATE_OFFHOOK) {
							handlePlayButtonClick(null);
						}
					}
				}
			}
		} else {
			isVisible = false;
			stopPlayer();

		}
	}

	private void setStatusBarColor() {
		if (message != null && message.getContactImageUri() != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getActivity().getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(this.getResources().getColor(R.color.darker_tint_color));
            bgImageView.setBackgroundColor(this.getResources().getColor(R.color.darker_tint_color));
        }
    } else {
        int color = Utils.getDefaultAvatarBackground((message == null || message.getSenderPhoneNumber() == null) ? "" : message.getSenderPhoneNumber());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getActivity().getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ResourcesCompat.getColor(getResources(),color,null));
        }
    }
	}

	private void stopPlayer() {
		if (audioPlayer != null && audioPlayer.isPlaying()) {
            audioPlayer.stop();
            audioPlayer.reset();
			defaultSeekBar.setProgress(0);
            playerProgressTime.setText(DateUtils.formatElapsedTime(0));
			((PlayerActivity)getActivity()).unregisterProximityListener();
        }
	}

	private   void showSavedDialog(final Activity activity) {
		if(!ModelManager.getInstance().getSharedPreferenceValue(Constants.DO_NOT_SHOW_SAVED_DIALOG_AGAIN, Boolean.class, false)) {

			AlertDlgUtils.showDialogWithCB(activity, R.string.message_saved_dialog_header, R.string.message_saved_dialog_body, R.string.do_not_show_again, R.string.ok_got_it_caps, R.string.saved_dialog_go_to_saved, new AlertDlgUtils.AlertDlgInterface() {
				@Override
				public void handlePositiveButton(View view) {

				}

				@Override
				public void handleNegativeButton(View view) {
					Intent startActivityIntent = new Intent(activity, InboxActivity.class);
					startActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					startActivityIntent.setAction(Constants.ACTION_GOTO_SAVED);
					activity.startActivity(startActivityIntent);
					activity.finish();
				}
			});
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		Logger.d(TAG, "onRequestPermissionsResult requestCode=" + requestCode);
		switch (requestCode) {

			case REQUEST_CALL_PHONE_PERMISSION:
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					call();
				} else {
					openCallScreen();
				}
				break;

			case PERMISSIONS_REQUEST_EXPORT:
				if (PermissionUtils.isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)){
					exportTofile();
				} else {
					handleNeverAskAgain();
				}
				break;
			case PERMISSIONS_REQUEST_SHARE:
				if (PermissionUtils.isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)){
					shareFile();
				} else {
					handleNeverAskAgain();
				}
				break;

			default:
				super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}

	private void handleNeverAskAgain() {
		if (PermissionUtils.wasNeverAskAgainChecked(Manifest.permission.WRITE_EXTERNAL_STORAGE, getActivity())) {
            AlertDlgUtils.showDialog(getActivity(), R.string.permissions, R.string.enable_permissions_dialog, R.string.ok_got_it, 0, true, new AlertDlgUtils.AlertDlgInterface() {
				@Override
				public void handlePositiveButton(View view) {
				}

				@Override
				public void handleNegativeButton(View view) {
				}
			});
        }
	}



	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public String getFormattedDisplayName(String phoneNumber ) {

		Context context = VVMApplication.getContext();

		String displayName = phoneNumber;
		if (contactObject != null) {
			displayName = contactObject.getDisplayName();
			if (displayName.equals(phoneNumber)) {
				return displayName;
			}
		}
		if (displayName.equals(context.getString(R.string.privateNumber))) {
			return context.getString(R.string.privateNumber);
		}
		//At this point displayName is not null/empty for sure:
		if (!displayName.equals(context.getString(R.string.welcomeMessagePhoneNumber))) {
			//No need to format the Welcome to VVM number
			displayName = PhoneNumberUtils.formatNumber(displayName, context.getString(R.string.defaultCountryIso));
		}
		return displayName;
	}


}
