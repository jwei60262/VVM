package com.att.mobile.android.vvm.control;

import java.util.ArrayList;

public interface EventListener
{
	/**
	 * Executed when the listener needs to be notified regarding an event that happened.
	 * 
	 * @param eventId (int) the ID of the event that happened.
	 * @param messageIDs (ArrayList<Long>) a list of all message IDs that were handled by the event,
	 * 							  or null in case all existing messages were handled.
	 */
	public void onUpdateListener(int eventId,  ArrayList<Long> messageIDs);
	
	/**
	 * Executed when a network failure occurred.
	 */
	public void onNetworkFailure();
}
