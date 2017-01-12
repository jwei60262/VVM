
package com.att.mobile.android.infra.media;

import java.io.File;
import java.io.IOException;

import android.media.MediaRecorder;
import android.media.MediaRecorder.OnErrorListener;
import android.util.Log;

import com.att.mobile.android.infra.utils.Logger;

/**
 * Handles recording audio in the application.
 */
public class AudioRecorder {

	/** holds audio recorder's current state */
	private boolean isRecording = false;
	private static final String TAG = "AudioRecorder";

	/** holds the native audio recorder */
	private MediaRecorder mediaRecorder;

	/** holds the full path of the new audio file to be created */
	private String recordedAudioFileFullPath;

	/** holds the maximum record duration, in milliseconds */
	private int maximumRecordDuration;

	/** holds a recorder events listener */
	private MediaRecorderEventsListener audioRecorderEventsListener = null;

	/**
	 * AudioRecorder default constructor.
	 */
	public AudioRecorder(String recordedAudioFileFullPath, int maximumRecordDuration) {
		// sotres the full path of the audio file to be recorder
		this.recordedAudioFileFullPath = recordedAudioFileFullPath;

		// stores the maximum record duration
		this.maximumRecordDuration = maximumRecordDuration;
	}

	/**
	 * Checks the existence of the storage to store to the recorded audio file in.
	 * 
	 * @throw IOException in case the storage doesn't exist.
	 */
	private void checkStorageFileExistens() throws IOException {
		// gets the folder where the recorded audio file should be stored in
		File audioFileFolder = new File(recordedAudioFileFullPath).getParentFile();

		// in case the folder doesn't exist and cannot be created
		if (!audioFileFolder.exists() && !audioFileFolder.mkdirs()) {
			throw new IOException(
					"AudioRecorder.checkStorageExistene() - the path to store the audio file to be recorded doesn't exist and couldn't be created.");
		}
		File audioFile = new File(recordedAudioFileFullPath);
		if(audioFile.exists()){
			boolean wasDeleted = audioFile.delete();
			Logger.d(TAG, "AudioRecoder.checkStorageFileExistens() - audioFile.delete() returned " + wasDeleted);
		}
	}

	/**
	 * Gets the full path of the audio file to be recorded.
	 * 
	 * @return (String) the full path of the audio file to be recorded.
	 */
	public String getPath() {
		return recordedAudioFileFullPath;
	}

	/**
	 * Gets the maximum record duration for the current recorded audio file.
	 * 
	 * @return (int) the maximum record duration for the current recorded audio file.
	 */
	public int getMaximumRecordDuration() {
		return maximumRecordDuration;
	}

	/**
	 * Starts the audio recording, moves the audio recorder to its RECORDING state. The method should be called only in
	 * case the audio recorder is currently in the READY state, and will be ignored otherwise.
	 */
	public void start() {

		try {
			// checks storage existene
			checkStorageFileExistens();

			mediaRecorder = new MediaRecorder();
			mediaRecorder.setOnErrorListener(new OnErrorListener() {
				/**
				 * Called upon audio recorder's error.
				 * 
				 * @param mediaRecorder (MediaPlayer != null) the audio recorder.
				 * @param what (int) the type of error that has occurred: MEDIA_ERROR_UNKNOWN or
				 *            MEDIA_ERROR_SERVER_DIED.
				 * @param extra (int) an extra code, specific to the error. Typically implementation dependant.
				 */
				public void onError(MediaRecorder mediaRecorder, int what, int extra)
				{
					handlePlayerError(what, extra);
				}
			});

			mediaRecorder.reset();
			// sets recorder's output format, audio encoder, output file and maximum recording length
			mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
			mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
			mediaRecorder.setOutputFile(recordedAudioFileFullPath);
			mediaRecorder.setMaxDuration(maximumRecordDuration);
			mediaRecorder.prepare();
			mediaRecorder.start();

			// sets recorder's state as ready
			// in case an audio recorder events listener exists, notifies it regarding the recorder initialization
			if (audioRecorderEventsListener != null) {
				audioRecorderEventsListener.onRecorderInitialization(maximumRecordDuration);
			}
			Logger.d(TAG, "AudioRecorder.start() - init finished, starting the audio recording...");

			// sets audio recorder's current state as recording
			isRecording = true;
		} catch (IOException e) {
			Log.e(TAG,
					"AudioRecoder.start() -  audio recording couldn't be started", e);
			handlePlayerError(-1, -1);
			return;
		} catch (IllegalStateException e) {
			// this should not happen due to audio recorder's states managment
			Log.e(TAG, "AudioRecoder.start() - audio recording couldn't be started", e);
			handlePlayerError(-1, -1);
			return;
		}catch (Exception e) {
			Log.e(TAG, "AudioRecoder.start() - audio recording couldn't be started", e);
			handlePlayerError(-1, -1);
			return;
		}

		// in case an audio recorder events listener exists, notifies it regarding the start of the audio recording
		if (audioRecorderEventsListener != null) {
			audioRecorderEventsListener.onRecordingStart();
		}
	}

	/**
	 * Returns whether the audio recorder is currently recording.
	 * 
	 * @return (boolean) true in case the audio recorder is currently recording, false otherwise.
	 */
	public boolean isRecording() {
		return isRecording;
	}


	/**
	 * 
	 * @return
	 */
	public int getMaxAmplitude() {
		// in case audio is not being recorded at the moment
		if (mediaRecorder == null){
			return 0;
		}	

		return mediaRecorder.getMaxAmplitude();
	}

	/**
	 * Stops the audio recording, moves the audio recored back to its READY state. The method should be called only in
	 * case the player is currently recording audio, and will be ignored otherwise.
	 */
	public void stop() {

		try {
			if (mediaRecorder != null) {
				mediaRecorder.stop();

				// sets audio recorder's state as IDLE (according to native recorder states machine)
				Logger.d(TAG, "AudioRecoder.stop() - audio recording stopped");
				mediaRecorder.release();

				// releases resources
				mediaRecorder = null;

				// sets audio recorder's state as RELEASED
				isRecording = false;

				Logger.d(TAG, "AudioRecoder.release() - audio recorder has been released");
			}

		} catch (IllegalStateException e) {
			// Note: since the native player doesn't fire the event for when the maximum recording
			// duration was reached but still stops the recording (probably a BUG), calling the stop() method
			// on the audio recorder when an extrenal observer finds out that the maximum recording
			// duration has been reached (so that recorder's listener to be notified) will cause
			// an IllegalStateException exception to be thrown and therefore it is being ignored

			// this should not happen due to audio recorder's states managment
			Log.e(TAG, "AudioRecorder.stop() - audio recording couldn't be stopped", e);
			handlePlayerError(-1, -1);
		}

		// in case the audio recording stopped and an audio recoder events listener exists, notifies it regarding the
		// recording stop
		if (!isRecording && audioRecorderEventsListener != null) {
			audioRecorderEventsListener.onRecordingStop();
		}
	}

	/**
	 * Called upon audio recorder's error.
	 */
	private void handlePlayerError(int what, int extra) {
		// in case the audio recorder was not already released due to the occurred error
		if (mediaRecorder != null) {
			mediaRecorder.release();
		}
		// releases resources
		mediaRecorder = null;
		isRecording = false;

		// in case an audio recorder events listener exists, notifies it regarding the player's error
		if (audioRecorderEventsListener != null) {
			audioRecorderEventsListener.onRecorderError();
		}
	}

	/**
	 * Registers an audio recorder events listener, to be notified regarding pre-defined audio recorder's events.
	 * 
	 * @param audioRecorderEventsListener (MediaRecorderEventsListener) an audio recorder events listeners.
	 */
	public void registerAudioRecorderEventsListener(MediaRecorderEventsListener audioRecorderEventsListener) {
		// in case an audio recorder events listener is already registered
		if (this.audioRecorderEventsListener != null) {
			return;
		}

		this.audioRecorderEventsListener = audioRecorderEventsListener;
	}

	/**
	 * Unregisters an audio recoder events listener from being notified regarding pre-defined audio recorder's events.
	 * 
	 * @param audioRecorderEventsListener (MediaRecorderEventsListener) an audio recorder events listeners which was
	 *            registered before.
	 */
	public void unregisterAudioRecorderEventsListener(MediaRecorderEventsListener audioRecorderEventsListener) {
		// only in case the audio recorder events listener to be unregistered is the current registered one
		if (this.audioRecorderEventsListener == audioRecorderEventsListener) {
			this.audioRecorderEventsListener = null;
		}
	}
}
