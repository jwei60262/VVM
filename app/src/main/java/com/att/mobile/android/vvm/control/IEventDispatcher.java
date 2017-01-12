package com.att.mobile.android.vvm.control;

import java.util.ArrayList;

public interface IEventDispatcher {

	/**
	 * Add a listener to the dispatcher that would receive the events when they occurs
	 */
	public void addEventListener(EventListener listener);

	/**
	 * Remove the listener from the dispatcher
	 */
	public void removeEventListener(EventListener listener);

	/**
	 * Remove all event listeners
	 */
	public void removeEventListeners();
	
	/**
	 * Notifies all listeners that a certain event has happened.
	 * 
	 * @param eventId (int) the ID of the event that happened.
	 * @param messageIDs (ArrayList<Long>) a list of all message IDs that were handled by the event,
	 * 							  or null in case all existing messages were handled.
	 * @param statsCode (StatusCode) the status code for the event.
	 */
	public void notifyListeners(int eventId,  ArrayList<Long> messageIDs);
}