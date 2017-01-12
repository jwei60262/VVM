package com.att.mobile.android.infra.media;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;

/**
 * This class encapsulates the native android media player and is being used for
 * playing only audio.<br/>
 * Encapsulating the native andoid media player is being done for 2 reasons:
 * 
 * <br/>
 * <br/>
 * 
 * <b>01.</b> minimizing the state machine of the native media player from 9
 * states to only 5 states in this audio player.<br/>
 * this is being done by merging native player's IDLE and ERROR states to a
 * single state named IDLE and by merging native player's INITIALIZED, PREPARED,
 * STOPPED and PLAYBACK_COMPLETED states to a single state named READY. <br/>
 * minimizing the state machine is being done by automatically moving the native
 * player from one of its states to another when performing ceratin action on
 * this player. for example, when initializing this player, both players (this
 * one, and the native it holds) are first in their IDLE state. this player
 * moves the native player from the IDLE state to the INITIALIZED state to the
 * PREPARED state, and sets its own state as READY.
 * 
 * <br/>
 * <br/>
 * 
 * <b>02.</b> Being able to register a listener on the audio player which will
 * be notified regarding certain events that occurred in the player. <br/>
 * The native player allows to register listeners for 3 types of events:
 * onError, onCompletion and onPrepare. First 2 events are fired by this player
 * as well (when the native fires them), while the third is not (this player
 * doesn't use the asynchronic prepare() method on the native player in any
 * way). <br/>
 * In addition to the events fired by the native player, this player fires few
 * more events. Although the additional events are synchronic, the listener gets
 * notified regarding them to allow the player to run in a different thread than
 * its listener (this player, like the native one, doens't use a different
 * thread, but players are usually created and working in a different thread /
 * service).
 * 
 * <br/>
 * <br/>
 * 
 * <b>Note:</b> the player doesn't fire any event regarding playback progressing
 * or seeking - getting the current position of player's playback needs to be
 * done by querying the player itself (using the getCurrentPosition() method).
 * 
 * @see MediaPlayerEventsListener full list of player's fired events
 */
public final class AudioPlayer {
	/**
	 * Holds possible player's states.
	 */
	public static interface State {
		public static final int IDLE = 0;
		public static final int READY = 1;
		public static final int STARTED = 2;
		public static final int PAUSED = 3;
		public static final int RELEASED = 4;
	}
	
	public static interface PlayerError {
		public static final int FILE_NOT_FOUND = 9999;
	}
	private static final String TAG = "AudioPlayer";

	/** holds the audio player for playback */
	private MediaPlayer audioPlayer = null;

	/** holds current player's state, which is set to IDLE by default */
	private int currentState = State.IDLE;

	/** holds current handled media duration */
	private int mediaDuration;

	/** holds a player events listener */
	private MediaPlayerEventsListener audioPlayerEventsListener = null;

	/**
	 * AudioPlayer default constructor.
	 */
	public AudioPlayer() {
		// creates the native audio player
		audioPlayer = new MediaPlayer();

		// sets internal player's listeners for playback completion and error
		audioPlayer.setOnCompletionListener(new OnCompletionListener() {
			/**
			 * Called upon media playback completion.
			 * 
			 * @param mediaPlayer
			 *            (MediaPlayer != null) the media player itself.
			 */
			public void onCompletion(MediaPlayer mediaPlayer) {
				handlePlaybackEnded();
			}

		});

		audioPlayer.setOnErrorListener(new OnErrorListener() {
			/**
			 * Called upon audio player's error.
			 * 
			 * @param mediaPlayer
			 *            (MediaPlayer != null) the media player itself.
			 * @param what
			 *            (int) the type of error that has occurred:
			 *            MEDIA_ERROR_UNKNOWN or MEDIA_ERROR_SERVER_DIED.
			 * @param extra
			 *            (int) an extra code, specific to the error. Typically
			 *            implementation dependant.
			 */
			public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
				
				currentState = State.IDLE;
				handlePlayerError(what, extra);

				// error was handled
				return true;
			}
		});
	}

	/**
	 * Initializes the audio player with a media to be handled, moves it to the
	 * READY state. in case the audio player is in any other state than IDLE
	 * (which includes STARTED - playing another media), it will be restarted -
	 * causing any being played media to stop its playback, and than initialized
	 * to be able to handle the new media.
	 * 
	 * @param mediaURI
	 *            (String != null) a path to the media to be handled by the
	 *            player.
	 */
	public void initializeMedia(String mediaURI) {
		// in case the audio player was already released
		if (currentState == State.RELEASED) {
			Log.e(TAG,
					"AudioPlayer.initializeMedia() - media initialization couldn't be done - audio player was already released");
			return;
		}

		// in case audio player's current state is not IDLE
		if (currentState != State.IDLE) {
			// resets the audio player
			reset();
		}

		try {
			Logger.d(TAG,
					"AudioPlayer.initializeMedia() - initializing audio player...");

			if (mediaURI == null || mediaURI.length() == 0){
				Log.e(TAG,
						"AudioPlayer.initializeMedia() - media initialization couldn't be done -no media URI");
				handlePlayerError(MediaPlayer.MEDIA_ERROR_UNKNOWN, -1);
				return;
			}
			// in case of a content URI
			if (mediaURI.startsWith("content:")) {
				audioPlayer.setDataSource(mediaURI);
			} else {
				// creates a file descriptor for the file to be played
                File file = new File(mediaURI);
                file.setReadable(true, false);
                FileInputStream mediaInputStream = new FileInputStream(file);
                try {
                	audioPlayer.setDataSource(mediaInputStream.getFD());
                } catch (Exception e) {
                	Log.e(TAG,
                			"AudioPlayer.setDataSource() failed ",
                			e);
                } finally{
                	if(mediaInputStream != null){
                		mediaInputStream.close();
                	}
                }
			}

			audioPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
			audioPlayer.prepare();

			// sets audio player's state as ready
			currentState = State.READY;

			Logger.d(TAG,
					"AudioPlayer.initializeMedia() - media has been initialized");
		} catch (FileNotFoundException e) {
			Log.e(TAG,
					"AudioPlayer.initializeMedia() - media initialization couldn't be done",
					e);
			handlePlayerError(PlayerError.FILE_NOT_FOUND, -1);
			return;
		} catch (IOException e) {
			Log.e(TAG,
					"AudioPlayer.initializeMedia() - media initialization couldn't be done",
					e);
			handlePlayerError(MediaPlayer.MEDIA_ERROR_UNKNOWN, -1);
			return;
		} catch (IllegalStateException e) {
			Log.e(TAG,
					"AudioPlayer.initializeMedia() - media initialization couldn't be done",
					e);
			handlePlayerError(MediaPlayer.MEDIA_ERROR_UNKNOWN, -1);
			return;
		}  catch (Exception ex) {
			Log.e(TAG, ex.getMessage(), ex);
			handlePlayerError(MediaPlayer.MEDIA_ERROR_UNKNOWN, -1);
			return;
		}

		// stores being handled media duration to prevent additional duration
		// querying
		this.mediaDuration = audioPlayer.getDuration();

		// in case an audio player events listener exists, notifies it regarding
		// the player initialization
		if (audioPlayerEventsListener != null) {
			audioPlayerEventsListener.onPlayerInitialization(mediaDuration);
		}
	}

	/**
	 * Gets the duration of the current being handled media by the audio player.
	 * 
	 * @return (int) the duration of the current being handled media by the
	 *         audio player, or -1 in case the audio player doesn't currently
	 *         handle any media.
	 */
	public int getMediaDuration() {
		// in case the audio player was already released
		if (currentState == State.RELEASED) {
			Log.e(TAG,
					"AudioPlayer.getMediaDuration() - getting media duration couldn't be done - audio player was already released");
			return -1;
		}

		// in case the audio player is currently in the IDLE state (has not been
		// initialized)
		if (currentState == State.IDLE) {
			// no media is currently being handled by the player
			Log.e(TAG,
					"AudioPlayer.getMediaDuration() - getting media duration couldn't be done - no media is currently being handled by the player");
			return -1;
		}

		// returns current being handled media duration
		return mediaDuration;
	}

	/**
	 * Starts the playback of the being handled media, moves the audio player to
	 * its STARTED state The method should be called only in case the audio
	 * player is currently in the READY or PAUSED state, and will be ignored
	 * otherwise.
	 */
	public void start() {
		start(0);
	}

	/**
	 * Starts the playback of the being handled media, moves the audio player to
	 * its STARTED state. The method should be called only in case the audio
	 * player is currently in the READY or PAUSED state, and will be ignored
	 * otherwise.
	 * 
	 * @param startingPosition
	 *            (int >= 0) the position in the media to start the playback
	 *            from.
	 */
	public void start(int startingPosition) {
		// in case the audio player was already released
		if (currentState == State.RELEASED) {
			Log.e(TAG,
					"AudioPlayer.start() - media playback couldn't be started - audio player was already released");
			return;
		}

		// in case audio player's current state is IDLE or STARTED, media
		// playback cannot be started
		// TODO - Royi - consider allowing STARTED STATE (using seekTo)
		if (currentState == State.IDLE || currentState == State.STARTED) {
			Log.e(TAG,
					"AudioPlayer.start() - media playback couldn't be started - audio player's state is not READY or PAUSED");
			return;
		}

		// in case a specific start position for the handled media exists
		if (startingPosition >= 0) {
			// seeks the audio player to the specific position in the media
			audioPlayer.seekTo(startingPosition);
		}

		try {
			// starts the media playback
			audioPlayer.start();
			Logger.d(TAG,
					"AudioPlayer.start() - media playback has been started");

			// sets audio player's state as STARTED
			currentState = State.STARTED;
		} catch (IllegalStateException e) {
			// this should not happen due to audip player's states managment
			Log.e(TAG,
					"AudioPlayer.start() - media playback couldn't be started",
					e);
			handlePlayerError(MediaPlayer.MEDIA_ERROR_UNKNOWN, -1);
			return;
		}

		// in case an audio player events listener exists, notifies it regarding
		// the playback start
		if (audioPlayerEventsListener != null) {
			audioPlayerEventsListener.onPlaybackStart(startingPosition);
		}
	}

	/**
	 * Returns whether the audio player is currently playing.
	 * 
	 * @return (boolean) true in case the audio player is currently playing,
	 *         false otherwise.
	 */
	public boolean isPlaying() {
		return (currentState == State.STARTED);
	}

	/**
	 * Seeks the being hadndled media playback to a specified position. The
	 * method should be called only in case the audio player is currently not in
	 * the IDLE state, and will be ignored otherwise.
	 * 
	 * @param position
	 *            (int) the position to seek the media playback to.
	 */
	public void seekTo(int position) {
		// in case the audio player was already released
		if (currentState == State.RELEASED) {
			Log.e(TAG,
					"AudioPlayer.seekTo() - media playback couldn't be seeked - audio player was already released");
			return;
		}

		if (currentState == State.IDLE) {
			Log.e(TAG,
					"AudioPlayer.seekTo() - media playback couldn't be seeked - audio player's state is IDLE");
			return;
		}

		try {
			// seeks the media playback to the requested position
			audioPlayer.seekTo(position);
			Logger.d(TAG,
					"AudioPlayer.seekTo() - media playback seeked to position: "
							+ position);
		} catch (IllegalStateException e) {
			Log.e(TAG,
					"AudioPlayer.seekTo() - media playback couldn't be seeked",
					e);
			handlePlayerError(MediaPlayer.MEDIA_ERROR_UNKNOWN, -1);
		}
	}

	/**
	 * Gets the current position of the being handled media by the audio player.
	 * 
	 * @param (int) the current position of the being handled media by the audio
	 *        player, or -1 in case the audio player doesn't currently handle
	 *        any media.
	 */
	public int getCurrentPosition() {
		// in case the audio player was already released
		if (currentState == State.RELEASED) {
			Log.e(TAG,
					"AudioPlayer.getCurrentPosition() - couldn't get media current position - audio player was already released");
			return -1;
		}

		// in case the audio player is currently in the IDLE state (has not been
		// initialized)
		if (currentState == State.IDLE) {
			// no media is currently being handled by the player
			Log.e(TAG,
					"AudioPlayer.getCurrentPosition() - couldn't get media current position - no media is currently being handled by the player");
			return -1;
		}

		// returns being handled media current position
		return audioPlayer.getCurrentPosition();
	}

	/**
	 * Pauses the playback of the being handled media, moves the audio player to
	 * its PAUSED state. The method should be called only in case the audio
	 * player is currently in the STARTED state, and will be ignored otherwise.
	 */
	public void pause() {
		// in case the audio player was already released
		if (currentState == State.RELEASED) {
			Log.e(TAG,
					"AudioPlayer.pause() - media playback couldn't be paused - audio player was already released");
			return;
		}

		// in case the handled media is currently not being played, do nothing
		if (currentState != State.STARTED) {
			Log.e(TAG,
					"AudioPlayer.pause() - media playback couldn't be paused - media is not being played");
			return;
		}

		try {
			// pauses the media playback
			audioPlayer.pause();
			Logger.d(TAG,
					"AudioPlayer.pause() - media playback has been paused");

			// sets audio player's state as PAUSED
			currentState = State.PAUSED;
		} catch (IllegalStateException e) {
			Log.e(TAG,
					"AudioPlayer.pause() - media playback couldn't be paused",
					e);
			handlePlayerError(MediaPlayer.MEDIA_ERROR_UNKNOWN, -1);
			return;
		}

		// in case an audio player events listener exists, notifies it regarding
		// the playback pause
		if (audioPlayerEventsListener != null) {
			audioPlayerEventsListener.onPlaybackPause();
		}
	}

	/**
	 * return whether the audio player is currently paused.
	 * 
	 * @return (boolean) true in case the audio player is currently paused,
	 *         false otherwise.
	 */
	public boolean isPaused() {
		return (currentState == State.PAUSED);
	}

	/**
	 * Stops the playback of the being handled media, moves the audio player
	 * back to its READY state. The method should be called only in case the
	 * player is currently in the STARTED or PAUSED state, and will be ignored
	 * otherwise.
	 */
	public void stop() {
		// in case the audio player was already released
		if (currentState == State.RELEASED) {
			Log.e(TAG,
					"AudioPlayer.stop() - media playback couldn't be stopped - audio player was already released");
			return;
		}

		// in case the audio player is in the IDLE mode
		if (currentState == State.IDLE) {
			Log.e(TAG,
					"AudioPlayer.stop() - media playback couldn't be stopped - the audio player is currently not handling the media.");
			return;
		}

		// in case the audio player is in the READY mode
		if (currentState == State.READY) {
			Log.e(TAG,
					"AudioPlayer.stop() - media playback couldn't be stopped - media is not being played at the moment.");
			return;
		}

		try {
			audioPlayer.stop();
//			audioPlayer.prepare();   *****US60320: Add workaround to fix native mediaplayer issue https://code.google.com/p/android-developer-preview/issues/detail?id=1787 *****
			Logger.d(TAG,
					"AudioPlayer.stop() - media playback has been stopped");

			// sets audio player's state as READY
			currentState = State.READY;
//		} catch (IOException e) {
//			// this should not happen due to audio player's states managment
//			Log.e(TAG,
//					"AudioPlayer.stop() - media playback couldn't be stopped",
//					e);
//			audioPlayer.release();
//			handlePlayerError(MediaPlayer.MEDIA_ERROR_UNKNOWN, -1);
		} catch (IllegalStateException e) {
			// this should not happen due to audio player's states managment
			Log.e(TAG,
					"AudioPlayer.stop() - media playback couldn't be stopped",
					e);
			audioPlayer.release();
			handlePlayerError(MediaPlayer.MEDIA_ERROR_UNKNOWN, -1);
		}

		// in case the media playback stopped and an audio player events
		// listener exists, notifies it regarding the playback stop
		if (currentState == State.READY && audioPlayerEventsListener != null) {
			audioPlayerEventsListener.onPlaybackStop();
		}
	}

	/**
	 * Gets audio player's current state (@see State)
	 * 
	 * @return (int) audio player's current state.
	 */
	public int getCurrentState() {
		return currentState;
	}

	/**
	 * Resets the audio player, moves it back to the IDLE state. TODO - Royi -
	 * add event for player release ?
	 */
	public void reset() {
		// in case the audio player was already released
		if (currentState == State.RELEASED) {
			Log.e(TAG,
					"AudioPlayer.reset() - media playback couldn't be restarted - audio player was already released");
			return;
		}

		// in case the handled media is currently being played (played or
		// paused)
		if (currentState == State.STARTED || currentState == State.PAUSED) {
			try {
				audioPlayer.stop();
//				audioPlayer.prepare();
				Logger.d(TAG,
						"AudioPlayer.reset() - media playback has been stopped");

				// sets audio player's state as READY
				currentState = State.READY;
//			} catch (IOException e) {
//				// this should not happen due to audio player's states managment
//				Log.e(TAG,
//						"AudioPlayer.reset() - media playback couldn't be stopped",
//						e);
			} catch (IllegalStateException e) {
				// this should not happen due to audio player's states managment
				Log.e(TAG,
						"AudioPlayer.reset() - media playback couldn't be stopped",
						e);
			}
		}

		// the audio player can now be only in 2 states: IDLE or READY, and
		// needs to be restarted
		// only in case it is in the READY state (IDLE means the audio player
		// was already restarted)
		if (currentState == State.READY) {
			// in case the media playback stopped and an audio player events
			// listener exists, notifies it regarding the playback stop
			if (audioPlayerEventsListener != null) {
				audioPlayerEventsListener.onPlaybackStop();
			}
			// restarts the native player and sets the current state as idle
			audioPlayer.reset();
			currentState = State.IDLE;

			Logger.d(TAG,
					"AudioPlayer.reset() - audio player has been restarted");
		}
	}

	/**
	 * Releases the audio player and its resources, moves it back to the IDLE
	 * state. The audio player is not valid anymore after calling this method
	 * and cannot be used in any way.
	 */
	public void release() {
		// in case the audio player was already released
		if (currentState == State.RELEASED) {
			Log.e(TAG,
					"AudioPlayer.release() - audio player was already released");
			return;
		}

		// in case the handled media is currently being played (played or
		// paused)
		if (currentState == State.STARTED || currentState == State.PAUSED) {
			// stops the playback of the handled media
			stop();
		}

		// resets and releases the audio player
			audioPlayer.reset();
			audioPlayer.release();

		// releases resources
		audioPlayer = null;
		audioPlayerEventsListener = null;

		// sets audio player's state as RELEASED
		currentState = State.RELEASED;

		Logger.d(TAG,
				"AudioPlayer.release() - player has been released");
	}

	/**
	 * Handles media playback ending:
	 * 
	 * 01. seeks the media to its begining 02. sets the player as ready to play
	 * (the same media) 03. in case a player evets listener is registered, it is
	 * being notified about the playback ending.
	 */
	private void handlePlaybackEnded() {
		Logger.d(TAG,
				"AudioPlayer.handlePlaybackEnded() - media playback eneded");

		// seeks the player to the begining of the handled media, and sets its
		// state to be READY
		seekTo(0);
		currentState = State.READY;

		Logger.d(TAG,
				"AudioPlayer.handlePlaybackEnded() - media playback restarted to its begining");

		// in case a player events listener exist, notifies it regarding the
		// playback ending
		if (audioPlayerEventsListener != null) {
			audioPlayerEventsListener.onPlaybackEnd();
		}
	}

	/**
	 * Called upon audio player's error. TODO - Royi - what & extra
	 */
	private void handlePlayerError(int what, int extra) {
		// in case the audio player was not already released due to the occurred
		// error
		if (currentState != State.IDLE) {
			// releases the audio player
			reset();
		}

		// in case an audio player events listener exists, notifies it regarding
		// the player's error
		if (audioPlayerEventsListener != null) {
			audioPlayerEventsListener
					.onPlayerError(what);
		}
	}

	/**
	 * Registers an audio player events listener, to be notified regarding
	 * pre-defined audio player's events.
	 * 
	 * @param audioPlayerEventsListener
	 *            (MediaPlayerEventsListener) an audio player events listeners.
	 */
	public void registerAudioPlayerEventsListener(
			MediaPlayerEventsListener audioPlayerEventsListener) {
		// in case an audio player events listener is already registered
		if (this.audioPlayerEventsListener != null) {
			return;
		}

		this.audioPlayerEventsListener = audioPlayerEventsListener;
	}

	/**
	 * Unregisters an audio player events listener from being notified regarding
	 * pre-defined audio player's events.
	 * 
	 * @param audioPlayerEventsListener
	 *            (MediaPlayerEventsListener) an audio player events listeners
	 *            which was registered before.
	 */
	public void unregisterAudioPlayerEventsListener(
			MediaPlayerEventsListener audioPlayerEventsListener) {
		// only in case the audio player events listener to be unregistered is
		// the current registered one
		if (this.audioPlayerEventsListener == audioPlayerEventsListener) {
			this.audioPlayerEventsListener = null;
		}
	}
}
