package com.att.mobile.android.infra.media;

/**
 * An interface used to listen to media player's events.
 * 
 * <br/><br/>
 * 
 * By implementing the inteferce, a class' object will be notified regarding to 2 different event types:
 * 
 * <br/><br/>
 * 
 * <b>01.</b> Native player events - events which are fired by the native player implementation. <br/>
 * 	   	      those events include playback ending and player's error.
 * 
 * 	   <br/><br/>
 * 
 * <b>02.</b> Non-native player events - events which are fired by application's player implementation
 * 	   		  (which encapsulates the native player implementation). <br/>
 * 	   		  those events include start playing media, pasuing it, seeking it, stoping it etc.
 */
public interface MediaPlayerEventsListener
{
	/**
	 * Called upon player's initialization.
	 * 
	 * @param (int) the duration (in milliseconds) of the media to be played by the player.
	 */
	public void onPlayerInitialization(final int mediaToBePlayedDuration);
	
	/**
	 * Called uopn player's playback start.
	 * 
	 * @param startingPosition (int) playback's starting position.
	 */
	public void onPlaybackStart(int startingPosition);
	
	/**
	 * Called upon player's playback pause.
	 */
	public void onPlaybackPause();
	
	/**
	 * Called upon player's playback stop.
	 */
	public void onPlaybackStop();
	
	/**
	 * Called upon player's playback ending.
	 */
	public void onPlaybackEnd();
	
	/**
	 * Called upon player's error.
	 * TODO - Royi - this method may need to get some parameters which identifies the error.
	 */
	public void onPlayerError(int errorCode);
}
