package com.att.mobile.android.infra.media;

/**
 * An interface used to listen to media recorder's events.
 * 
 * <br/><br/>
 * 
 * By implementing the inteferce, a class' object will be notified regarding the following events:
 * 
 * <br/><br/>
 * 
 * 01. recorder initialization
 * 01. start of the recording
 * 02. end of the recording
 * 03. any occurred error in the internal recorder
 */
public interface MediaRecorderEventsListener 
{
	/**
	 * Called upon recorder's initialization.
	 * 
	 * @param (int) the duration (in milliseconds) of the media to be recorded.
	 */
	public void onRecorderInitialization(int maximumRecordDuration);
	
	/**
	 * Called upon recorder's recording start.
	 */
	public void onRecordingStart();
	
	/**
	 * Called upon recorder's recording stop.
	 */
	public void onRecordingStop();
	
	/**
	 * Called upon recorder's recording end.
	 */
	public void onRecordingEnd();
	
	/**
	 * Called upon recorder's error.
	 * TODO - Royi - this method may need to get some parameters which identifies the error.
	 */
	public void onRecorderError();
}
