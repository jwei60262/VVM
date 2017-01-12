
package com.att.mobile.android.vvm.screen;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.att.mobile.android.infra.media.AudioPlayer;
import com.att.mobile.android.infra.media.AudioRecorder;
import com.att.mobile.android.infra.media.MediaPlayerEventsListener;
import com.att.mobile.android.infra.media.MediaRecorderEventsListener;
import com.att.mobile.android.infra.utils.AlertDlgUtils;
import com.att.mobile.android.infra.utils.FontUtils;
import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.infra.utils.PermissionUtils;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.model.db.ModelManager;
import com.att.mobile.android.vvm.model.greeting.GreetingFactory;

import java.io.File;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * The Audio Recorder Screen.
 */
public abstract class AudioRecorderActivity extends VVMActivity implements MediaRecorderEventsListener,
        MediaPlayerEventsListener, OnAudioFocusChangeListener {

    private static final String TAG = "AudioRecorderActivity";

    protected    String imapRecordingGreetingType = null;
    /**
     * Holds all possible states for the audio in the screen.
     */
    // private static class AudioState
    // {
    // private final static int IDLE = 0;
    // private final static int RECRODING = 1;
    // private final static int PLAYBACK = 2;
    // private final static int PLAYBACK_PAUSED = 3;
    // }

	private static final int REQUEST_RECORD_AUDIO_PERMISSION = 10;

    /**
     * Holds all extra keys for the intent which launches this screen.
     */
    public static class IntentExtras {
        public static final String SCREEN_TITLE = "SCREEN_TITLE";
        public static final String MAX_RECORDING_MILSEC_DURATION = "MAX_RECORDING_MILSEC_DURATION";
        public static final String GREETING_UNIQUE_ID = "GREETING_UNIQUE_ID";
        public static final String IMAP_SELECTION_GREETING_TYPE = "IMAP_SELECTION_GREETING_TYPE";
        public static final String IMAP_RECORDING_GREETING_TYPE = "IMAP_RECORDING_GREETING_TYPE";
        public static final String GREETING_TYPE = "GREETING_TYPE";
        public static final String NEXT_INTENT = "NEXT_INTENT";
    }

    /**
     * Holds all possible states for audio recorder's record button. (states are declated as Strings to be able to set
     * them as Tag for the button itself)
     */
    private static class RecordButtonStates {
        private final static String RECORD = "RECORD";
        private final static String STOP_RECORDING = "STOP_RECORDING";
    }

    /**
     * Holds all possible states for audio recorder's play button. (states are declated as Strings to be able to set
     * them as Tag for the button itself)
     */
    private static class PlayButtonStates {
        private final static String PLAY = "PLAY";
        private final static String PAUSE_PLAYBACK = "PAUSE_PLAYBACK";
    }



    /**
     * A timer task which observes audio recorder's recodring progress.
     */
    private class AudioRecorderRecordingObserver extends TimerTask {
        /** holds the observing interval in milliseconds */
        private static final int OBSERVING_INTERVAL = 250;

        /** holds the audio recorder to observe */
        private AudioRecorder audioRecorder = null;

        /** holds whether the observing is for a dummy recording */
        private boolean isObservingForDummyRecording = false;

        /**
         * AudioRecorderRecordingObserver constuctor.
         *
         * @param audioRecorder (AudioRecorder != null) the audio recorder

         */
        private AudioRecorderRecordingObserver(AudioRecorder audioRecorder, boolean isForDummyRecording) {
            // saves the audio recorder to observe
            this.audioRecorder = audioRecorder;

            // sets whether the created audio recorder recording observer is for a dummy audio recording
            isObservingForDummyRecording = isForDummyRecording;
        }

        /**
         * Runs the task.
         */
        @Override
        public void run() {
            // in case the dummy audio recorder is being observed
            if (isObservingForDummyRecording) {
                observeDummyRecording();
            } else {
                observeRealRecording();
            }
        }

        /**
         * Observes the dummy audio recording.
         */
        private void observeDummyRecording() {
			/*
			 * //updates dummy audio recorder's current recording position dummyRecordingProgress += OBSERVING_INTERVAL;
			 * //Note: when the native audio player reaches its recording maximum duration, // it automatically stops
			 * the recording. The SDK gives an option to register // an onInfoListener to the native audio player to be
			 * notified when the maximum // duration of the recording is reached, but for some reason this event is
			 * never // fired by the system, so there is no option to react on it. // Therefore, while observing the
			 * audio recorder a check whether // recroding's maximum duraion was reached, and in case it was, the
			 * onRecordStop() // method is executed. if(dummyRecordingProgress > maximumDummyRecordDuration) { //Note: a
			 * call to audioRecorder.stop() MUST be done so that its registered events listener // will execute its
			 * onRecordingStop() method (no matter who is the listener). audioRecorder.stop();
			 * Logger.d(TAG,
			 * "AudioRecorderRecordingObserver.run() - restarting observing the dummy audio recorder"); //initializes
			 * and start the dummy recording audioRecorder.initializeMedia(dummyRecordingAudioFilePath,
			 * maximumDummyRecordDuration); audioRecorder.start(); }
			 */
        }

        /**
         * Observes the real audio recording.
         */
        private void observeRealRecording() {
            // updates audio recorder's current recording position
            currentRecordingProgress += OBSERVING_INTERVAL;

            // updates the progress UI components
            updateRecordingProgressUIComponents();

            // Note: when the native audio player reaches its recording maximum duration,
            // it automatically stops the recording. The SDK gives an option to register
            // an onInfoListener to the native audio player to be notified when the maximum
            // duration of the recording is reached, but for some reason this event is never
            // fired by the system, so there is no option to react on it.
            // Therefore, while observing the audio recorder a check whether
            // recroding's maximum duraion was reached, and in case it was, the onRecordStop()
            // method is executed.
            if (currentRecordingProgress > maximumRecordDuration) {
                // Note: a call to audioRecorder.stop() MUST be done so that its registered events listener
                // will execute its onRecordingStop() method (no matter who is the listener).
                audioRecorder.stop();
            }
        }

    }

    /**
     * A timer task which observes audio player's playback progress.
     */
    private class AudioPlayerPlaybackObserver extends TimerTask {
        /** holds the observing interval in milliseconds */
        private static final int OBSERVING_INTERVAL = 200;

        /**
         * Runs the task.
         */
        @Override
        public void run() {
            // updates audio player's progress UI components
            updatePlaybackProgressUIComponents(false);
        }
    }


    /**
     * A timer task which updates the audio equalizer in the screen according to the currently recording / playback.
     */
    private static class AudioEqualizerUpdater extends TimerTask {
        /** holds the update interval in milliseconds */
        private static final int UPDATE_INTERVAL = 250;

        /** holds the sampled microphone volume multiplier */
        private static final float VOLUME_MULTIPLIER = 2f;

        /** holds the audio recorder activity */
        private AudioRecorderActivity audioRecorderActivity = null;

        /** holds the audio equalizer to update */
        // private Equalizer audioEqualizer = null;
        private DotMeterView meter = null;

        /** holds the audio recorder to update the audio equalizer according to */
        private AudioRecorder audioRecorder = null;

        /** holds the audio player to update the audio equalizer according to */
        private AudioPlayer audioPlayer = null;

        /** holds whether audio equalizer updates are for a dummy audio recording */
        private boolean isUpdateForDummyRecording = false;

        /** holds all playback equalizer values, based on the dispalyed recording equalizer values */
        private int[][] playbackEqualizerValues = null;

        /** holds the position of the next equalizer value to display (relvant for audio playback only) */
        private int playbackEqualizerValuesPosition = 0;

        /**
         * holds a runnable object to run from the UI thread and update player's progress UI components
         */
        private Runnable audioEqualizerUIComponentUpdater = new Runnable()
        {
            /**
             * Updates audio equalizer UI component.
             */
            @Override
            public void run()
            {
                // in case the audio equalizer updater is for an audio player
                if (audioPlayer != null)
                {
                    synchronized (this)
                    {
                        // gets the next audio equalizer value from the stored values based on the recording
                        // Note: each recording equalizer value (int[]) is being cloned before sent for being displayed
                        // in the audio equalizer, to support multiple playbacks one after the ohter and to support
                        // seeking the playback (equalizer source code changes the values while painting them,
                        // and they can't be used again unlsess cloned)
                        if (playbackEqualizerValuesPosition < playbackEqualizerValues.length)
                        {
                            Logger.d("AudioEqualizerUpdater.run()", "current equalizer values position is "
                                    + playbackEqualizerValuesPosition);
                            // Grab the mic volume and tell the meter to update here
                            // int micval = (int) (audioRecorder.getMaxAmplitude() / (512f * VOLUME_MULTIPLIER));
                            // meter.setEqualizerValues(micval);
                            if (playbackEqualizerValues[playbackEqualizerValuesPosition] != null) {
                                meter.setEqualizerValues(playbackEqualizerValues[playbackEqualizerValuesPosition]
                                        .clone());
                            }
                            ++playbackEqualizerValuesPosition;
                        }
                    }
                }
                else
                {
                    // calculates the current equalizer value
                    int equalizerValue = (int) (audioRecorder.getMaxAmplitude() / (512f * VOLUME_MULTIPLIER));
                    // updates audio equalizer UI component and gets the equalizer values
                    int[] recordingEqualizerValues = meter.setEqualizerValues(equalizerValue);

                    // in case the audio equalizer updater is for a real audio recorder
                    if (!isUpdateForDummyRecording)
                    {
                        // stores the calculated value in the audio equalizer values collection,
                        // so that the audio playback equalizer will be the same as the recording
                        AudioRecorderActivity.recordingEqualizerValues.add(recordingEqualizerValues);
                    }
                }

            }
        };

        /**
         * AudioEqualizerUpdater default constructor.
         */
        private AudioEqualizerUpdater() {
        }

        /**
         * Creates an audio equalizer updater for a dummy audio recorder (for when the audio equalizer should be updated
         * while NOT actualy recording audio).
         *
         * @param audioRecorderActivity (AudioRecorderActivity != null) the audio recorder activity.
         * @param audioEqualizer (AudioEqualizer != null) the audio equalizer to update.
         * @param dummyAudioRecorder (AudioRecorder != null) the dummy audio recorder to update the audio equalizer
         *            according to.
         * @return (AudioEqualizerUpdater != null) an audio equalizer updater.
         */
//		private static AudioEqualizerUpdater createUpdaterForDummyAudioRecorder(
//				AudioRecorderActivity audioRecorderActivity,
//																				DotMeterView meter,
//																				AudioRecorder dummyAudioRecorder) {
//			// creates an audio equalizer updater
//			AudioEqualizerUpdater audioEqualizerUpdater = new AudioEqualizerUpdater();
//
//			// stores the audio recorder activity
//			audioEqualizerUpdater.audioRecorderActivity = audioRecorderActivity;
//
//			// sets that the created audio equalizer updater is for a dummy audio recorder
//			audioEqualizerUpdater.isUpdateForDummyRecording = true;
//
//			// sets the audio equalizer to update by the audio equalizer updater,
//			// and the audio recorder to update the audio equalizer according to
//			audioEqualizerUpdater.meter = meter;
//			audioEqualizerUpdater.audioRecorder = dummyAudioRecorder;
//
//			// returns the created audio equalizer updater
//			return audioEqualizerUpdater;
//		}

        /**
         * Creates an audio equalizer updater for a real audio recorder (for when the audio equalizer should be updated
         * while actualy recording audio).
         *
         * @param audioRecorderActivity (AudioRecorderActivity != null) the audio recorder activity.
         * @param audioRecorder (AudioRecorder != null) the audio recorder to update the audio equalizer according to.
         * @return (AudioEqualizerUpdater != null) an audio equalizer updater.
         */
        private static AudioEqualizerUpdater createUpdaterForRealAudioRecorder(
                AudioRecorderActivity audioRecorderActivity,
                DotMeterView meter,
                AudioRecorder audioRecorder) {
            // creates an audio equalizer updater
            AudioEqualizerUpdater audioEqualizerUpdater = new AudioEqualizerUpdater();

            // stores the audio recorder activity
            audioEqualizerUpdater.audioRecorderActivity = audioRecorderActivity;

            // sets the audio equalizer to update by the audio equalizer updater,
            // and the audio recorder to update the audio equalizer according to
            audioEqualizerUpdater.meter = meter;
            audioEqualizerUpdater.audioRecorder = audioRecorder;

            // creates the collection to hold recording equalizer values
            AudioRecorderActivity.recordingEqualizerValues = new LinkedList<int[]>();

            // returns the created audio equalizer updater
            return audioEqualizerUpdater;
        }

        /**
         * Creates an audio equalizer updater for an audio player.
         *
         * @param audioRecorderActivity (AudioRecorderActivity != null) the audio recorder activity.
         * @param audioPlayer (AudioPlayer != null) the audio player to update the audio equalizer according to.
         * @return (AudioEqualizerUpdater != null) an audio equalizer updater.
         */
        private static AudioEqualizerUpdater createUpdaterForAudioPlayer(AudioRecorderActivity audioRecorderActivity,
                                                                         DotMeterView meter,
                                                                         AudioPlayer audioPlayer) {
            // creates an audio equalizer updater
            AudioEqualizerUpdater audioEqualizerUpdater = new AudioEqualizerUpdater();

            // stores the audio recorder activity
            audioEqualizerUpdater.audioRecorderActivity = audioRecorderActivity;

            // sets the audio equalizer to update by the audio equalizer updater,
            // and the audio player to update the audio equalizer according to
            audioEqualizerUpdater.meter = meter;
            audioEqualizerUpdater.audioPlayer = audioPlayer;

            // copies the recording equalizer values to the playback equalizer values,
            // to get the same equalizer for the playback as was for the recording
            int position = 0;
            audioEqualizerUpdater.playbackEqualizerValues = new int[AudioRecorderActivity.recordingEqualizerValues
                    .size()][];
            for (int[] recordingEqualizerValue : recordingEqualizerValues) {
                audioEqualizerUpdater.playbackEqualizerValues[position++] = recordingEqualizerValue;
            }

            // since there can be a scenario where the playback doesn't start from the begining (when the user moves
            // the seek bar to a point which is not the starting point, and then clicks the play button),
            // the playback equalizer values position must be calculated according to the audio player progress,
            // for the equalizer to match the playback
            int mediaDuration = audioPlayer.getMediaDuration();
            audioEqualizerUpdater
                    .updatePlaybackEqualizerValuesPosition(
                            (mediaDuration / AudioEqualizerUpdater.UPDATE_INTERVAL)
                                    -
                                    ((mediaDuration - audioPlayer.getCurrentPosition()) / AudioEqualizerUpdater.UPDATE_INTERVAL));

            // returns the created audio equalizer updater
            return audioEqualizerUpdater;
        }

        /**
         * Runs the task.
         */
        @Override
        public void run() {
            // updates audio equalizer UI component, from the UI thread
            audioRecorderActivity.runOnUiThread(audioEqualizerUIComponentUpdater);
        }

        /**
         * Updates the current position of the playback equalizer values to be displayed in the equalizer, used when
         * thoe playback is seeked to a specific position.
         *
         * @param playbackEqualizerValuesPosition (int) the position of the current playback equalizer value to display
         *            next.
         */
        private synchronized void updatePlaybackEqualizerValuesPosition(int playbackEqualizerValuesPosition) {
            Logger.d("AudioEqualizerUpdater.updatePlaybackEqualizerValuesPosition()",
                    "new equalizer values position is " + playbackEqualizerValuesPosition);
            this.playbackEqualizerValuesPosition = playbackEqualizerValuesPosition;
        }
    }

    /** holds the audio state for the screen */
    // private int audioState = AudioState.IDLE;

    /** holds screen's title */
    protected TextView screenTitle = null;

    /** holds audio equalizer */
    // private Equalizer audioEqualizer = null;
    private DotMeterView meter;


    /** holds audio recorder's recording progress bar */
    private SeekBar recordingProgressBar = null;

    /** holds progress text for the audio recorder (recording) */
    private TextView recordingProgressTextView = null;
    protected TextView recordingTotalTextView = null;

    /** holds audio player's playback seek bar */
    private SeekBar recordingPlaybackSeekBar = null;

    /** holds progress text for the audio palyer (recording playback) */
    private TextView recordingPlaybackProgressTextView = null;

    /** holds audio recorder's record, play and send buttons */
    protected ImageButton recordButton = null;
    protected ImageButton playButton = null;
    protected Button sendButton = null;

    /** holds all dispalyed recording equalizer values, to be displayed later as playback equalizer values */
    private static LinkedList<int[]> recordingEqualizerValues = null;

//	/** holds an audio recorder */
//	private AudioRecorder dummyAudioRecorder = null;

    /** holds dummy recording file path */
    private String dummyRecordingAudioFilePath = null;

//	/** holds dummy recording maximum duration, in milliseconds */
//	private static final int maximumDummyRecordDuration = 60 * 1000;

    /** holds an audio recorder */
    private AudioRecorder audioRecorder = null;

    /** holds recording file path */
   // protected String recordingAudioFilePath = null;

    protected String recordNameFilePath;

    protected String recordCustomFilePath;

    /** holds recording maximum duration, in milliseconds */
    protected int maximumRecordDuration;

    /** holds recording current progress, in milliseconds */
    private int currentRecordingProgress;

    /** holds a timer, used for schedualing timer tasks for updating audio recorder's progress bar */
    private Timer timer = new Timer();

    /** holds an audio equalizer update (timer task to udpate the audio equalizer UI) */
    private AudioEqualizerUpdater audioEqualizerUpdater = null;

    /** holds an audio recorder recording observer (timer task to update audio recorder UI) */
    private AudioRecorderRecordingObserver audioRecorderRecordingObserver = null;

    /** holds an audio player */
    private AudioPlayer audioPlayer = null;

    /** holds an audio player playback observer (timer task to update audio player UI) */
    private AudioPlayerPlaybackObserver audioPlayerPlaybackObserver = null;

    /** holds the instance of the VVM application */
    private VVMApplication vvmApplication = null;

    /**
     * Called upon screen creation.
     *
     * @param savedInstanceState (Bundle) any previous saved instance state.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // must be called
        super.onCreate(savedInstanceState);
        Logger.d(TAG, "AudioRecorderActivity.onCreate()");

        // get the instance of the VVM application
        vvmApplication = VVMApplication.getInstance();

        // gets the path for the audio recording and the dummy audio recording
        // TODO - get file name from config parameters
        String pathPrefix = new StringBuffer(vvmApplication.getFilesDir().getAbsolutePath()).append(File.separator)
                .toString();
       // recordingAudioFilePath = new StringBuffer(pathPrefix).append("recording.amr").toString();
       // dummyRecordingAudioFilePath = new StringBuffer(pathPrefix).append("dumrecording.amr").toString();

        recordNameFilePath = new StringBuffer(pathPrefix).append(GreetingFactory.NAME+ ".amr").toString();
        recordCustomFilePath  = new StringBuffer(pathPrefix).append(GreetingFactory.CUSTOM +".amr").toString();

        imapRecordingGreetingType =  getIntent().getExtras().getString(IntentExtras.IMAP_RECORDING_GREETING_TYPE);
        // sets screen's layout
        setContentView(R.layout.audio_recorder);

        initActionBar(R.string.record_greeting , true);
        // gets screen's audio equalizer
        meter = (DotMeterView) findViewById(R.id.audioEqualizer);
        // audioEqualizer = (Equalizer)findViewById(R.id.audioEqualizer);

        // if(isHighDensityScreen())
        // {
        // audioEqualizer.setDensity(HIGH_DENSITY);
        // }
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        initSeekBars();
        setFontAndText();


//		// creates the dummy audio recorder for the "offline" audio equalizer,
//		// and sets its events listener
//		dummyAudioRecorder = new AudioRecorder(dummyRecordingAudioFilePath, maximumDummyRecordDuration);
//		dummyAudioRecorder.registerAudioRecorderEventsListener(new MediaRecorderEventsListener()
//		{
//			@Override
//			public void onRecorderInitialization(int maximumRecordDuration)
//			{
//			}
//
//			@Override
//			public void onRecordingStart()
//			{
//				// starts observing audio recorder's recording progress
//				startObservingAudioRecorderRecordingProgress(true);
//
//				// starts updating the audio equalizer
//				startUpdatingAudioEqualzierForDummyRecording();
//			}
//
//			@Override
//			public void onRecordingStop()
//			{
//				// stops observing audio recorder's recording progress
//				stopObservingAudioRecorderRecordingProgress();
//
//				// stops updating the audio equalizer
//				stopUpdatingAudioEqualizer();
//			}
//
//			@Override
//			public void onRecordingEnd()
//			{
//			}
//
//			@Override
//			public void onRecorderError()
//			{
//			}
//		});
    }

    private void setFontAndText() {
        // gets the progress text of the recording
        recordingProgressTextView = (TextView) findViewById(R.id.recordingProgressText);
        recordingProgressTextView.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Medium));
        recordingTotalTextView = (TextView) findViewById(R.id.recordingTotalTime);
        recordingTotalTextView.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Medium));
        ((TextView) findViewById(R.id.timeDelim)).setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Medium));


        // gets the progress text of the recording playback
        recordingPlaybackProgressTextView = (TextView) findViewById(R.id.recordingProgressText);
        recordingPlaybackProgressTextView.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.Roboto_Medium));

        // gets audio recorder's record button, and sets its tag (its current state)
        recordButton = (ImageButton) findViewById(R.id.audioRecordedRecordButton);
        recordButton.setTag(RecordButtonStates.RECORD);
        recordButton.setContentDescription(getString(R.string.recordTxt));

        // gets audio recorder's play button, disables it (there is no recording to play at the moment)
        // and sets its tag (its current state)
        playButton = (ImageButton) findViewById(R.id.audioRecordedPlayButton);
        playButton.setTag(PlayButtonStates.PLAY);
        playButton.setEnabled(false);
        playButton.setContentDescription(getString(R.string.play));

        // gets audio recorder's send button and disables it (there is no recording to send to the server)
        sendButton = (Button) findViewById(R.id.audioRecordedSendButton);
        sendButton.setTypeface(FontUtils.getTypeface(FontUtils.FontNames.OmnesATT_Medium));
        sendButton.setEnabled(false);
    }

    private void initSeekBars() {
        // gets audio recorder's audio recording progress bar
        recordingProgressBar = (SeekBar) findViewById(R.id.recordingProgressBar);

        // gets audio palyer's audio recording playback progress bar
        recordingPlaybackSeekBar = (SeekBar) findViewById(R.id.recordingProgressBar);


//        recordingPlaybackSeekBar.getProgressDrawable().setColorFilter(getResources().getColor(R.color.orange), PorterDuff.Mode.SRC_ATOP);
//        recordingProgressBar.getProgressDrawable().setColorFilter(getResources().getColor(R.color.orange), PorterDuff.Mode.SRC_ATOP);
//
//
//        ShapeDrawable thumb = new ShapeDrawable(new OvalShape());
//        thumb.setIntrinsicHeight(20);
//        thumb.setIntrinsicWidth(20);
//        thumb.getPaint().setColor(getResources().getColor(R.color.orange));
//        recordingProgressBar.setThumb(thumb);
//        recordingPlaybackSeekBar.setThumb(thumb);


        recordingPlaybackSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            /**
             * Called upon seek bar's progress change.
             *
             * @param seekBar (SeekBar != null) the seek bar which its progress was changed.
             * @param seekBarProgress (int) seek bar's progress level (0 - bar's max value or 100).
             * @param fromUser (boolean) true in case the progress change was due to user event, false otherwise.
             */
            @Override
            public void onProgressChanged(SeekBar seekBar,
                                          int seekBarProgress, boolean fromUser) {
                // only in case the user has changed seek bar's progress
                if (fromUser) {

                    // seeks player's playback from the new media
                    // position
                    if (audioPlayer != null) {
                        audioPlayer.seekTo(seekBarProgress);
                    }

                    // in case the media handled by the player is not
                    // being played at the moment,
                    // an update to player's UI (seek bar and progress
                    // text) must be done manually,
                    // since there is no observation on the player which
                    // updates those UI components
                    // every defined intervaled time
                    if (audioPlayer != null && !audioPlayer.isPlaying()) {
                        updatePlaybackProgressUIComponents(false);
                    } else {
                        // updates the player equalizer values position, to match the equalizer to the new playback
                        // position
                        if (audioEqualizerUpdater != null) {
                            audioEqualizerUpdater.updatePlaybackEqualizerValuesPosition(seekBarProgress
                                    / AudioEqualizerUpdater.UPDATE_INTERVAL + 1);
                        }
                    }
                }
            }

            /**
             * Notification that the user has started a touch gesture. Clients may want to use this to disable advancing
             * the seekbar.
             */
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            /**
             * Notification that the user has started a touch gesture. Clients may want to use this to disable advancing
             * the seekbar.
             */
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    /**
     * Called upon activity start.
     */
    @Override
    protected void onStart() {
        super.onStart();
        Logger.d(TAG, "AudioRecorderActivity.onStart()");
    }

    /**
     * Called upon activity resume.
     */
    @Override
    protected void onResume() {
        Logger.d(TAG, "AudioRecorderActivity.onResume()");
        super.onResume();
        ModelManager.getInstance().addEventListener(this);

        meter.startRefreshingLoop();

        // in case the screen gets its focus after creation or resumed from pause
        if (screenStateBeforeGettingFocus == ActivityStateBeforeGettingFocus.ONLY_PAUSED
                || screenStateBeforeGettingFocus == ActivityStateBeforeGettingFocus.CREATED) {
            // regsiters the audio player events listener to the audio player, if needed
            if (audioPlayer != null) {
                audioPlayer.registerAudioPlayerEventsListener(this);
            }

//			// make sure no other player or recorder is alive
//			if (audioPlayer == null && (audioRecorder == null || !audioRecorder.isRecording())) {
//				// initializes and start the dummy recording
//				dummyAudioRecorder.start();
//			}
        }
    }

    /**
     * Called when activity's window focus is changed. This method is implemented in addition to the onResume() method
     * to handle a scenario when device's screen goes OFF and ON. When the screen goes ON activity's onResume() method
     * will be called although the activity is may NOT be visible to the user (lock screen may be displayed). When the
     * user will unlock the screen, this method will be called with the TRUE paramter value, to notify that the activity
     * is really visible to the user.
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // in case the window has no focus, do nothing (onPause() handle this scenario)
        if (!hasFocus) {
            Logger.d(TAG, "AudioRecorderActivity.onWindowFocusChanged() - activity is NOT visible");
            return;
        }

        Logger.d(TAG, "AudioRecorderActivity.onWindowFocusChanged() - activity IS visible");

        // in case the screen gets its focus after only losing its focus, nothing should be done!
        if (screenStateBeforeGettingFocus == ActivityStateBeforeGettingFocus.ONLY_LOST_FOCUS) {
            return;
        }

        // in case the screen gets its focus after resumed from pause with no focus, and an audio player exists
        if (screenStateBeforeGettingFocus == ActivityStateBeforeGettingFocus.PAUSED_AND_LOST_FOCUS &&
                audioPlayer != null) {
            // regsiters the audio player events listener to the audio player
            audioPlayer.registerAudioPlayerEventsListener(this);

//			// make sure no other player or recorder is alive
//			if (audioPlayer == null && (audioRecorder == null || !audioRecorder.isRecording())) {
//				// initializes and start the dummy recording
//				dummyAudioRecorder.start();
//			}
        }
    }

//	/**
//	 * Starts updating the audio equalizer for the dummy audio recorder.
//	 */
//	private void startUpdatingAudioEqualzierForDummyRecording() {
//		// in case the method was called from a thread which is not the UI
//		// thread
//		if (!isRunningFromUIThread()) {
//			runOnUiThread(new Runnable() {
//				@Override
//				public void run() {
//					startUpdatingAudioEqualzierForDummyRecording();
//				}
//			});
//
//			return;
//		}
//
//		// activates the audio equalizer in gray mode
//		meter.activate(false);
//
////		// in case a schedualed audio equalizer updater time task doesn't exist
////		if (audioEqualizerUpdater == null) {
////			audioEqualizerUpdater = AudioEqualizerUpdater.createUpdaterForDummyAudioRecorder(this, meter,
////					dummyAudioRecorder);
////			timer.schedule(audioEqualizerUpdater,
////					0,
////					AudioEqualizerUpdater.UPDATE_INTERVAL);
////		}
//	}

    /**
     * Starts updating the audio equalizer for the real audio recorder.

     *            playback, false in case audio equalizer updates are for audio recording.
     */
    private void startUpdatingAudioEqualzierForRealRecording() {
        // in case the method was called from a thread which is not the UI
        // thread
        if (!isRunningFromUIThread()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    startUpdatingAudioEqualzierForRealRecording();
                }
            });

            return;
        }

        // activates the audio equalizer in colored mode
        meter.activate(true);

        // in case a schedualed audio equalizer updater time task doesn't exist
        if (audioEqualizerUpdater == null) {
            audioEqualizerUpdater = AudioEqualizerUpdater.createUpdaterForRealAudioRecorder(this, meter, audioRecorder);
            timer.schedule(audioEqualizerUpdater,
                    0,
                    AudioEqualizerUpdater.UPDATE_INTERVAL);
        }
    }

    /**
     * Starts updating the audio equalizer for the audio player.
     */
    private void startUpdatingAudioEqualzierPlayback() {
        // in case the method was called from a thread which is not the UI
        // thread
        if (!isRunningFromUIThread()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    startUpdatingAudioEqualzierPlayback();
                }
            });

            return;
        }

        // activates the audio equalizer in colored mode
        meter.activate(true);

        // in case a schedualed audio equalizer updater time task doesn't exist
        if (audioEqualizerUpdater == null) {
            audioEqualizerUpdater = AudioEqualizerUpdater.createUpdaterForAudioPlayer(this, meter, audioPlayer);
            timer.schedule(audioEqualizerUpdater,
                    0,
                    AudioEqualizerUpdater.UPDATE_INTERVAL);
        }
    }

    /**
     * Stops updating the audio equalizer.
     */
    private void stopUpdatingAudioEqualizer() {
        // in case the method was called from a thread which is not the UI
        // thread
        if (!isRunningFromUIThread()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stopUpdatingAudioEqualizer();
                }
            });

            return;
        }

        // deactivates the audio equalizer
        meter.deactivate();

        // in case a schedualed audio equalizer updater time task exists
        if (audioEqualizerUpdater != null) {
            // cancels the audio equalizer updater time task
            audioEqualizerUpdater.cancel();
            audioEqualizerUpdater = null;
        }
    }

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

		Logger.d(TAG, "onRequestPermissionsResult requestCode=" + requestCode);
		switch (requestCode) {

        	case REQUEST_RECORD_AUDIO_PERMISSION:
        		if (PermissionUtils.isPermissionGranted(Manifest.permission.RECORD_AUDIO)){
        			recordButtonOnClickCallback(recordButton);
        		} else if (PermissionUtils.wasNeverAskAgainChecked(Manifest.permission.RECORD_AUDIO, this)) {
                    AlertDlgUtils.showDialog(this, R.string.permissions, R.string.enable_permissions_dialog, R.string.ok_got_it, 0, true, new AlertDlgUtils.AlertDlgInterface() {
                        @Override
                        public void handlePositiveButton(View view) {
                        }

                        @Override
                        public void handleNegativeButton(View view) {
                        }
                    });
                }
        		break;

        	default:
        		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}

    /**
     * Callback for the record button, called upon button click.
     *
     * @param buttonView (View != null) the record button.
     */
    public void recordButtonOnClickCallback(View buttonView) {

		if ( ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ) {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
			return;
		}

        // in case record button's state is "RECORD"
        if (((String) recordButton.getTag()).equals(RecordButtonStates.RECORD)) {
            // creates the audio recorder if needed
            if(imapRecordingGreetingType == null){
                return;
            }
            if (audioRecorder == null) {
                audioRecorder = new AudioRecorder(getRecFilePath(), maximumRecordDuration);
            }

            // sets the screen as audio recorder's events listener
            audioRecorder.registerAudioRecorderEventsListener(this);

            // in case the audio player is currently playing or paused
            // Note: the order of the following validations is CRITICAL.
            // the reason is due to the existence off the dummy audio recorder and the "offline" equlaizer feature.
            // because the "offline" equalizer must reacte to sounds whenever there is no in progress recording or
            // playback,
            // when a playback is being stopped the dummy recorder starts its recording.
            // when the user plays a recorded audio and clicks the record button while playback is being played,
            // the audio playback must be stopped, but this will cause the dummy recorder to start its recroding.
            // this will cause 2 audio recorders to try record audio at the same time which will cause an ERROR.
            // therefore, the audio player is first stopped (causing the dummy audio recording to start recording)
            // and just then the validation for the dummy recorder is being done (which will always return true and the
            // dummy recroding will be stopped).
            // PLEASE BE AWARE that it works only because both audio recroders and the audio player
            // run from the main application's thread (which means all operations and listeners' events are synchronic)
            if (audioPlayer != null) {
                // stops audio player's playback
                audioPlayer.stop();
            }

//			// in case the dummy audio recorder is currently recording
//			if (dummyAudioRecorder.isRecording()) {
//				// stops the dummy audio recorder recodring
//				dummyAudioRecorder.stop();
//			}

            // starts to record audio
            audioRecorder.start();
        }
        // in case record button's state is "STOP_RECORDING"
        else {
            // stops the audio recording
            audioRecorder.stop();
            playButton.setVisibility(View.VISIBLE);
            playButton.setTag(PlayButtonStates.PLAY);
            playButton.setEnabled(true);
            playButton.setContentDescription(getString(R.string.play));
        }
    }

    /**
     * Returns whether the current thread is the UI thread.
     *
     * @return (boolean) whether the current thread is the UI thread.
     */
    private boolean isRunningFromUIThread() {
        return Looper.getMainLooper().getThread().getId() == Thread
                .currentThread().getId();
    }

    /**
     * Switches the current state of audio recorder's recrod button.
     *
     * @see RecordButtonStates
     * @param recordButtonState (String) record button state to set.
     */
    private void switchRecordButtonState(final String recordButtonState) {
        // gets the current state of audio recorder's record button
        String currentRecordButtonState = (String) recordButton.getTag();

        // in case the record button is already in the state it should be, do nothing
        if (recordButtonState.equals(currentRecordButtonState)) {
            return;
        }

        // in case the method was called from a thread which is not the UI
        // thread
        if (!isRunningFromUIThread()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switchRecordButtonState(recordButtonState);
                }
            });

            return;
        }

        // in case the record button state should be "STOP_RECORDING"
        if (recordButtonState.equals(RecordButtonStates.STOP_RECORDING)) {
            // switch record button's state to "STOP_RECORDING"
            recordButton.setImageResource(R.drawable.stop);
            recordButton.setTag(RecordButtonStates.STOP_RECORDING);
            recordButton.setContentDescription(getString(R.string.stopText));
        }
        // in case the record button state should be "RECORD"
        else if (recordButtonState.equals(RecordButtonStates.RECORD)) {
            // switch record button's state to "RECORD"
            recordButton.setImageResource(R.drawable.record);
            recordButton.setTag(RecordButtonStates.RECORD);
            recordButton.setContentDescription(getString(R.string.recordTxt));
            playButton.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Callback for the play button, called upon button click.
     *
     * @param buttonView (View != null) the play button.
     */
    public void sendButtonOnClickCallback(View buttonView){
        // stop playing when hitting send
        if (audioPlayer != null){
            audioPlayer.stop();
        }
    }

    /**
     * Switches the current state of audio recorder's play button.
     *
     * @see PlayButtonStates
     * @param playButtonState (String) play button state to set.
     */
    private void switchPlayButtonState(final String playButtonState) {
        // gets the current state of audio recorder's play button
        String currentPlayButtonState = (String) playButton.getTag();

        // in case the play button is already in the state it should be, do nothing
        if (playButtonState.equals(currentPlayButtonState)) {
            return;
        }

        // in case the method was called from a thread which is not the UI
        // thread
        if (!isRunningFromUIThread()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switchPlayButtonState(playButtonState);
                }
            });

            return;
        }

        // in case the play button state should be "PAUSE_PLAYBACK"
        if (playButtonState.equals(PlayButtonStates.PAUSE_PLAYBACK)) {
            // switch play button's state to "PAUSE_PLAYBACK"
            playButton.setImageResource(R.drawable.ic_stop);
            playButton.setTag(PlayButtonStates.PAUSE_PLAYBACK);
            enableRecordButton(false);
            playButton.setContentDescription(getString(R.string.pause));
        }
        // in case the play button state should be "PLAY" and is currently "PAUSE_PLAYBACK"
        else if (playButtonState.equals(PlayButtonStates.PLAY)) {
            // switch play button's state to "PLAY"
            playButton.setImageResource(R.drawable.ic_play);
            playButton.setTag(PlayButtonStates.PLAY);
            enableRecordButton(true);
            playButton.setContentDescription(getString(R.string.play));
        }
    }

    private void enableRecordButton(boolean shouldEnable){
        recordButton.setEnabled(shouldEnable);
        recordButton.setImageResource(shouldEnable ? R.drawable.record : R.drawable.record_disabled);
    }

   protected String getRecFilePath(){
       if(imapRecordingGreetingType == null){
           return null;
       }
       if(imapRecordingGreetingType.contains(GreetingFactory.RECORD_NAME)){
            return recordNameFilePath;
       }
       else {
            return recordCustomFilePath;
       }
   }

    /**
     * Callback for the send button, called upon button click.
     *
     * @param buttonView (View != null) the play button.
     */
    public void playButtonOnClickCallback(View buttonView) {
        // in case play button's state is "PLAY"
        if (((String) playButton.getTag()).equals(PlayButtonStates.PLAY)) {
            // in case the dummy audio recorder is currently recording
//			if (dummyAudioRecorder.isRecording()) {
//				// stops the dummy audio recorder recodring
//				dummyAudioRecorder.stop();
//			}
            enableRecordButton(false);

            // creates an audio player if needed (and registers the screen as audio player's events listener)
            if (audioPlayer == null) {
                audioPlayer = new AudioPlayer();
                audioPlayer.registerAudioPlayerEventsListener(this);
                // initializes the audio player with the recorded audio file
                audioPlayer.initializeMedia(getRecFilePath());

            }
            // acquires wake look to prevent the screen from turning OFF
            vvmApplication.acquireWakeLock();
            // sets application's audio mode and sets speaker's state as ON
            vvmApplication.setApplicationAudioMode();
            vvmApplication.setIsApplicationSpeakerOn(true);
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if(audioManager.isSpeakerphoneOn()){
                audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,  AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            } else {
                audioManager.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            }
            audioPlayer.start(audioPlayer.getCurrentPosition());
        }
        // in case play button's state is "PASUE_PLAYBACK"
        else {
            // pauses recording playback
            if (audioPlayer != null && audioPlayer.isPlaying()){
                audioPlayer.pause();
            }
            enableRecordButton(true);
        }
    }

    /**
     * Called upon audio recorder's initialization. Note: in case previous recording playback was played while clicking
     * on the record button, the playback has already been stopped, causing the play button to return to its PLAY state.
     *
     * @param maximumRecordDuration (int > 0) maximum record duration, in milliseconds.
     */
    @Override
    public void onRecorderInitialization(final int maximumRecordDuration) {
        // in case the method was called from a thread which is not the UI
        // thread
        if (!isRunningFromUIThread()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onRecorderInitialization(maximumRecordDuration);
                }
            });

            return;
        }

        // stores the current recording progress (the maximum record length is a final value and is never changed)
        this.currentRecordingProgress = 0;

        // sets audio recorder's audio recording progress bar length
        recordingProgressBar.setMax(maximumRecordDuration);

        // switches between audio player's seek bar to audio recorder's progress bar, if needed
        switchProgressAndSeekBars(false);

        // updates recording progress bar and progress text
        updateRecordingProgressUIComponents();

        // disables the play and send buttons
        playButton.setEnabled(false);
        sendButton.setEnabled(false);
    }

    /**
     * Called upon recorder's recording start.
     */
    @Override
    public void onRecordingStart() {
        // updates that recrod is currently active
        // audioState = AudioState.RECRODING;

        // acquires wake look to prevent the screen from turning OFF
        vvmApplication.acquireWakeLock();

        // starts observing audio recorder's recording progress
        startObservingAudioRecorderRecordingProgress(false);
        setTotalRecordingTime(maximumRecordDuration);

        // starts updating the audio equalizer
        startUpdatingAudioEqualzierForRealRecording();

        // switches the state of the record button to "STOP_RECORDING"
        switchRecordButtonState(RecordButtonStates.STOP_RECORDING);
    }

    /**
     * Called upon audio recorder's recording stop.
     */
    @Override
    public void onRecordingStop() {
        // in case the method was called from a thread which is not the UI
        // thread
        if (!isRunningFromUIThread()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onRecordingStop();
                }
            });

            return;
        }

        // updates that recrod is not currently active
        // audioState = AudioState.IDLE;

        // releases the acquired wake look, allowing the screen to turn OFF
        vvmApplication.releaseWakeLock();

        // unregisters the screen from getting audio recorder's notifications
        audioRecorder.unregisterAudioRecorderEventsListener(this);

        // stops observing audio recorder's recording progress
        stopObservingAudioRecorderRecordingProgress();

        // stops updating the audio equalizer
        stopUpdatingAudioEqualizer();

        // switches the state of the record button to "RECORD"
        switchRecordButtonState(RecordButtonStates.RECORD);

        // enables audio recorder's play & send buttons (to be able to play and send to server the recorded audio)
        playButton.setEnabled(true);
        sendButton.setEnabled(true);

        // switches between audio recorder's progress bar and audio player's seek bar, if needed
        switchProgressAndSeekBars(true);

        // updates audio player's playback progress UI components
        updatePlaybackProgressUIComponents(true);

        recordingPlaybackSeekBar.setMax(currentRecordingProgress);

//		// initializes and start the dummy recording
//		dummyAudioRecorder.start();
    }

    /**
     * Called upon audio recorder's recording end.
     */
    @Override
    public void onRecordingEnd() {
        // same behaviour as on record stop
        onRecordingStop();
    }

    /**
     * Called upon audio recorder's error.
     */
    @Override
    public void onRecorderError() {
        // TODO - Royi
    }

    /**
     * Updates audio recorder's progress UI components - progress bar's progress and progress time text.
     */
    private void updateRecordingProgressUIComponents() {
        // in case the method was called from a thread which is not the UI
        // thread
        if (!isRunningFromUIThread()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateRecordingProgressUIComponents();
                }
            });

            return;
        }

        updateRecordingProgressBarProgressUIComponent();
        updateRecordingProgressTextUIComponent();
    }

    /**
     * Updates audio recorder's recording progress bar progress UI component.
     */
    private void updateRecordingProgressBarProgressUIComponent() {
        // in case the method was called from a thread which is not the UI
        // thread
        if (!isRunningFromUIThread()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateRecordingProgressBarProgressUIComponent();
                }
            });

            return;
        }

        // sets audio recorder's progress bar progress
        recordingProgressBar.setProgress(currentRecordingProgress);
    }

    /**
     * Updates audio recorder's recording progress text UI component.
     */
    protected void updateRecordingProgressTextUIComponent() {
        // in case the method was called from a thread which is not the UI
        // thread
        if (!isRunningFromUIThread()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateRecordingProgressTextUIComponent();
                }
            });

            return;
        }

        // sets recording's progress text
        int backwardSecondsRecordingProgress = currentRecordingProgress / 1000;

        // since 1.7 seconds should be displayed as 2 seconds
        if (((maximumRecordDuration - currentRecordingProgress) % 1000) > 0) {
            ++backwardSecondsRecordingProgress;
        }

        // calculates total number of minutes and seconds
        int currentRecordingMinutesProgress = backwardSecondsRecordingProgress / 60;
        backwardSecondsRecordingProgress = backwardSecondsRecordingProgress % 60;

        String progressText = new StringBuilder().append(currentRecordingMinutesProgress).
                append(":").append(backwardSecondsRecordingProgress < 10 ? "0" : "").
                append(backwardSecondsRecordingProgress).toString();
        recordingProgressTextView.setText(progressText);
    }

    /**
     * Starts observing audio recorder's recording progress.
     *
     * @param isForDummyRecording (boolean) true in case the observing is for dummy recording, false otherwise.
     */
    private void startObservingAudioRecorderRecordingProgress(boolean isForDummyRecording) {
        // in case a schedualed audio recorder recording observer time task doesn't exist
        if (audioRecorderRecordingObserver == null) {
            audioRecorderRecordingObserver = new AudioRecorderRecordingObserver(
					/*isForDummyRecording ? dummyAudioRecorder :*/ audioRecorder, isForDummyRecording);
            timer.schedule(audioRecorderRecordingObserver,
                    0,
                    AudioRecorderRecordingObserver.OBSERVING_INTERVAL);
        }
    }

    /**
     * Stops observing audio recorder's recording progress.
     */
    private void stopObservingAudioRecorderRecordingProgress() {
        // in case a schedualed audio recorder recording observer time task exists
        if (audioRecorderRecordingObserver != null) {
            // cancels the audio recorder recording observer time task
            audioRecorderRecordingObserver.cancel();
            audioRecorderRecordingObserver = null;
        }
    }

    /**
     * Switches between the recording progress bar and the recording playback seek bar UI components.
     *
     * @param isRecordingPlaybackSeekBar (boolean) true in case the recording playback seek bar should be displayed,
     *            false in case the recording progress bar should be displayed.
     */
    private void switchProgressAndSeekBars(final boolean isRecordingPlaybackSeekBar) {
        // in case the method was called from a thread which is not the UI
        // thread
        if (!isRunningFromUIThread()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switchProgressAndSeekBars(isRecordingPlaybackSeekBar);
                }
            });

            return;
        }
    }

    /**
     * Called upon audio player's initialization.
     */
    @Override
    public void onPlayerInitialization(int mediaToBePlayedDuration) {
        // sets audio player's audio recording playback seek bar length
        recordingPlaybackSeekBar.setMax(mediaToBePlayedDuration);

        // switches between audio recorder's progress bar and audio player's seek bar, if needed
        switchProgressAndSeekBars(true);

        // updates audio player's playback progress UI components
        updatePlaybackProgressUIComponents(true);
    }

    /*
     * Called uopn audio player's playback start.
     * @param startingPosition (int) playback's starting position.
     */
    @Override
    public void onPlaybackStart(int startingPosition) {


        // starts observing audio player's playback progress
        startObservingAudioPlayerPlaybackProgress();

        // starts updating the audio equalizer
        startUpdatingAudioEqualzierPlayback();

        // switches the state of the play button to "PAUSE_PLAYBACK"
        switchPlayButtonState(PlayButtonStates.PAUSE_PLAYBACK);
    }

    /**
     * Called upon audio player's playback pause.
     */
    @Override
    public void onPlaybackPause() {
        // updates that playback is currently paused
        // audioState = AudioState.PLAYBACK_PAUSED;

        // releases the acquired wake look, allowing the screen to turn OFF
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.abandonAudioFocus(this);
        vvmApplication.releaseWakeLock();
        // resotres device's audio mode
        vvmApplication.restoreDeviceAudioMode();
        vvmApplication.setIsApplicationSpeakerOn(false);

        // stops observing audio player's playback progress
        stopObservingAudioPlayerPlaybackProgress();

        // stops updating the audio equalizer
        stopUpdatingAudioEqualizer();

        // switches the state of the play button to "PLAY"
        switchPlayButtonState(PlayButtonStates.PLAY);
//		// initializes and start the dummy recording
//		dummyAudioRecorder.start();
    }

    /**
     * Called upon audio player's playback stop.
     */
    @Override
    public void onPlaybackStop() {
        // updates that playback is not currently active
        // audioState = AudioState.IDLE;

        // releases the acquired wake look, allowing the screen to turn OFF
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.abandonAudioFocus(this);
        vvmApplication.releaseWakeLock();
        // resotres device's audio mode before leaving the screen
        vvmApplication.restoreDeviceAudioMode();
        vvmApplication.setIsApplicationSpeakerOn(false);

        // stops observing audio player's playback progress
        stopObservingAudioPlayerPlaybackProgress();

        // stops updating the audio equalizer
        stopUpdatingAudioEqualizer();

        // switches the state of the play button to "PLAY"
        switchPlayButtonState(PlayButtonStates.PLAY);

        // updates audio player's progress UI components (seek bar and progress time
        // text), since the audio playback is returned to its begining by the player
        updatePlaybackProgressUIComponents(true);

        if (audioPlayer != null){
            audioPlayer.release();
            audioPlayer = null;
        }

//		// initializes and start the dummy recording
//		dummyAudioRecorder.start();
    }

    /**
     * Called upon audio player's playback ending - stops observing audio player's playback progress, and updates audio
     * player's UI components (play button, seek-bar and progress time text).
     */
    @Override
    public void onPlaybackEnd() {
        // same behaviour as playback stop
        onPlaybackStop();
    }

    /**
     * Called upon audio player's error.
     */
    @Override
    public void onPlayerError(int errorCode) {
    }

    /**
     * Updates player's playback progress UI components - seek bar's progress and progress time text.
     *
     * @param backwardsProgress (boolean) true in case backwards progress should be displyed, false otherwise. Note:
     *            currently only the progress text can be displayed backwards, and NOT the progress bar.
     */
    private void updatePlaybackProgressUIComponents(final boolean backwardsProgress) {
        // in case the method was called from a thread which is not the UI
        // thread
        if (!isRunningFromUIThread()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updatePlaybackProgressUIComponents(backwardsProgress);
                }
            });

            return;
        }

        updatePlaybackProgressBarProgressUIComponent();
        updatePlaybackProgressTextUIComponent(backwardsProgress);
    }

    /**
     * Updates audio player's palyback progress bar progress UI component.
     */
    private void updatePlaybackProgressBarProgressUIComponent() {
        // in case the method was called from a thread which is not the UI
        // thread
        if (!isRunningFromUIThread()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updatePlaybackProgressBarProgressUIComponent();
                }
            });

            return;
        }

        int mediaDuration = 0;
        int mediaCurrentPosition = 0;
        if (audioPlayer != null) {
            // gets the current position of the being played media, and its duration
            mediaDuration = audioPlayer.getMediaDuration();
            mediaCurrentPosition = audioPlayer.getCurrentPosition();
        }

        // sets player's seek bar progress according to the being handled media
        // progress
        recordingPlaybackSeekBar.setProgress((mediaDuration > 0) ? mediaCurrentPosition : 0);
    }



    protected void setTotalRecordingTime(int totalRecordingTime){
        int recordingPlaybackSecondsProgress = (totalRecordingTime / 1000);
        recordingPlaybackSecondsProgress = adjustRecordingPlaybackSecondsProgress(totalRecordingTime, recordingPlaybackSecondsProgress);

        int recordingPlaybackMinutesProgress = recordingPlaybackSecondsProgress / 60;
        recordingPlaybackSecondsProgress = recordingPlaybackSecondsProgress % 60;


        String progressText = new StringBuilder().append(recordingPlaybackMinutesProgress).
                append(":").append(recordingPlaybackSecondsProgress < 10 ? "0" : "").
                append(recordingPlaybackSecondsProgress).toString();
        recordingTotalTextView.setText(progressText);
        Logger.d(TAG, "AudioRecorderActivity.updatePlaybackProgressTextUIComponent() - " + progressText);
    }


    /**
     * Updates audio player's playback progress text UI component.
     *
     * @param backwardsProgress (boolean) true in case a backwards progress should be displayed, false otherwise.
     */
    private void updatePlaybackProgressTextUIComponent(final boolean backwardsProgress) {
        // in case the method was called from a thread which is not the UI
        // thread
        if (!isRunningFromUIThread()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updatePlaybackProgressTextUIComponent(backwardsProgress);
                }
            });

            return;
        }

        int mediaDuration = 0;
        int currentPosition = 0;
        // gets playback maximum duration and current position
        if (audioPlayer != null) {
            mediaDuration = audioPlayer.getMediaDuration();
            currentPosition = audioPlayer.getCurrentPosition();
        }else{
            mediaDuration = currentRecordingProgress;
            currentPosition = 0;
        }
        setTotalRecordingTime(mediaDuration);

        // calculates total number of minutes and seconds of the progress
        int recordingPlaybackSecondsProgress;
        if (backwardsProgress) {
            int recordTimeLeft = mediaDuration - currentPosition;
            recordingPlaybackSecondsProgress = (recordTimeLeft / 1000);

            // since 1.7 seconds should still be displayed as 2 seconds
            recordingPlaybackSecondsProgress = adjustRecordingPlaybackSecondsProgress(recordTimeLeft, recordingPlaybackSecondsProgress);
        } else {
            recordingPlaybackSecondsProgress = (currentPosition / 1000);
        }

        int recordingPlaybackMinutesProgress = recordingPlaybackSecondsProgress / 60;
        recordingPlaybackSecondsProgress = recordingPlaybackSecondsProgress % 60;

        String progressText = new StringBuilder().append(recordingPlaybackMinutesProgress).
                append(":").append(recordingPlaybackSecondsProgress < 10 ? "0" : "").
                append(recordingPlaybackSecondsProgress).toString();
        recordingPlaybackProgressTextView.setText(progressText);
        Logger.d(TAG, "AudioRecorderActivity.updatePlaybackProgressTextUIComponent() - " + progressText);
    }

    private int adjustRecordingPlaybackSecondsProgress(int recordTime, int recordingPlaybackSecondsProgress) {
        if (((recordTime) % 1000) > 0) {
            // but, since recording stops a few milliseconds after the maximum duration,
            // recording of 10 seconds is actually 10.X seconds, and 11 shouldn't be displayed
            // in the progress text
            if (recordingPlaybackSecondsProgress +1 < maximumRecordDuration / 1000 ) {
                ++recordingPlaybackSecondsProgress;
            }
        }
        return recordingPlaybackSecondsProgress;
    }

    /**
     * Starts observing audio player's playback progress.
     */
    private void startObservingAudioPlayerPlaybackProgress() {
        // in case a schedualed player playback observer time task doesn't exist
        if (audioPlayerPlaybackObserver == null) {
            audioPlayerPlaybackObserver = new AudioPlayerPlaybackObserver();
            timer.schedule(audioPlayerPlaybackObserver,
                    AudioPlayerPlaybackObserver.OBSERVING_INTERVAL,
                    AudioPlayerPlaybackObserver.OBSERVING_INTERVAL);
        }
    }

    /**
     * Stops observing audio player's playback progress.
     */
    private void stopObservingAudioPlayerPlaybackProgress() {
        // in case a schedualed player playback observer time task exists
        if (audioPlayerPlaybackObserver != null) {
            // cancels the player playback observer time task
            audioPlayerPlaybackObserver.cancel();
            audioPlayerPlaybackObserver = null;
        }
    }

    /**
     * Called upon activity pause.
     */
    @Override
    protected void onPause() {
        Logger.d(TAG, "AudioRecorderActivity.onPause()");

        meter.killRefreshingLoop();

        // stops dummy audio recorder's recording if needed
//		if (dummyAudioRecorder.isRecording()) {
//			dummyAudioRecorder.stop();
//		}
        // stops audio recorder's recording if needed
		/*else*/ if (audioRecorder != null && audioRecorder.isRecording()) {
            // simulates record button's click for stopping the audio recording
            recordButtonOnClickCallback(recordButton);
        } else if (audioPlayer != null) {
            // pauses audio player's playback if needed
            if (audioPlayer.isPlaying()) {
                // simulates play button's click for pausing the audio recording playback
                playButtonOnClickCallback(playButton);
            }

            // unregistes the screen from getting player's notifications
            audioPlayer.unregisterAudioPlayerEventsListener(this);
        }

        // lets the UI thread to sleep for 50 more milliseconds to make a smoother synchronization with
        // the audio recorder / player in case it is still recording / playing while the screen is being paused
        try {
            Logger.d(TAG, "AudioRecorderActivity.onPause() -  sleeping for 50 milliseconds while pausing");
            Thread.sleep(50);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }

        super.onPause();
        ModelManager.getInstance().removeEventListener(this);
    }

    /**
     * Called upon activity destruction.
     */
    @Override
    protected void onDestroy() {
        Logger.d(TAG, "AudioRecorderActivity.onDestroy()");

//		// releases the dummy audio recorder and audio player
//		if (dummyAudioRecorder != null) {
//			dummyAudioRecorder.stop();
//		}

        // releases the audio recorder and audio player, if needed
        if (audioRecorder != null) {
            audioRecorder.stop();
        }

        if (audioPlayer != null) {
            audioPlayer.release();
            audioPlayer = null;
        }

//        File dummyRecordedAudioFile = new File(dummyRecordingAudioFilePath);
//        boolean dummyDeleted = dummyRecordedAudioFile.delete();
//        Logger.d(TAG, "AudioRecorderActivity.onDestroy dummy temp recorded file deleted " + dummyDeleted);
        if (((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getCallState() != TelephonyManager.CALL_STATE_OFFHOOK) {
            vvmApplication.setIsApplicationSpeakerOn(vvmApplication.isApplicationSpeakerOn());
        }

        super.onDestroy();
    }
    @Override
    public void onAudioFocusChange(int arg0) {
        // TODO Auto-generated method stub

    }

}
