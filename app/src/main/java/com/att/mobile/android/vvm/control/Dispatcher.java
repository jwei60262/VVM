package com.att.mobile.android.vvm.control;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.att.mobile.android.infra.utils.Logger;

public class Dispatcher {
	private static final String TAG = "Dispatcher";

	private final List<EventListener> listeners = new ArrayList<EventListener>();

	public final void addListener(EventListener listener) {
		synchronized (listeners) {
			for (EventListener currListener : listeners) {
				if (listener.equals(currListener)){
					return;
				}
			}
			listeners.add(listener);
		}
	}
	
	/**
	 * Returns whether the dispatcher contains no listeners.
	 * @return
	 */
	public boolean isEmpty() {
		synchronized (listeners) {
			return listeners.isEmpty();
		}
	}

	public final void removeListener(EventListener listener) {
		synchronized (listeners) {
			listeners.remove(listener);
		}
	}

	/**
	 * remove all the listeners from this dispatcher
	 */
	public final void removeListeners() {
		synchronized (listeners) {
			Iterator<EventListener> it = listeners.iterator();
			while (it.hasNext()) {
				it.next();
				it.remove();
			}
		}
	}

	public final void notifyListeners(int eventId,  ArrayList<Long> messageIDs)
	{
		synchronized (listeners) {
			for (EventListener listener : listeners) {
				Logger.d(TAG, "notifyListener " + listener.getClass().getName());
				Logger.d(TAG, "notifyListener " + "eventId = " + eventId);
				listener.onUpdateListener(eventId, messageIDs);
			}
		}
	}	
	
	public final void notifyListenersForNetworkFailure()
	{
		synchronized (listeners)
		{
			for (EventListener listener : listeners)
			{
				listener.onNetworkFailure();
			}
		}
	}	
}
