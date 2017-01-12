package com.att.mobile.android.vvm.control.receivers;

import java.util.ArrayList;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

import com.att.mobile.android.vvm.control.Dispatcher;
import com.att.mobile.android.vvm.control.EventListener;
import com.att.mobile.android.vvm.control.IEventDispatcher;
import com.att.mobile.android.vvm.model.Constants.EVENTS;

public class ContactsContentObserver extends ContentObserver implements
		IEventDispatcher {
	private Dispatcher dispatcher = new Dispatcher();
	private static ContactsContentObserver instance;
	private static Object lock = new Object();
	private boolean isRegistered = false;
	private static final String TAG = "ContactsContentObserver";

	public static void createInstance(Handler handler) {
		synchronized (lock) {
			if (instance == null) {
				instance = new ContactsContentObserver(handler);
			}
		}
	}

	public static ContactsContentObserver getInstance() {
		if (instance == null) {
			Log.e(TAG,
					"must call create instance before calling getinstance");
		}
		return instance;
	}

	private ContactsContentObserver(Handler handler) {
		super(handler);
	}

	@Override
	public void onChange(boolean selfChange) {
		notifyListeners(EVENTS.CONTACTS_CHANGED, null);
	}

	/**
	 * add event listener to contacts changes and register the observer if not already registered
	 * @param context
	 * @param listener
	 */
	public void addEventListener(Context context, EventListener listener) {
		addEventListener(listener);
		synchronized (lock) {
			if (!isRegistered) {
				context.getContentResolver().registerContentObserver(
						RawContacts.CONTENT_URI, true,
						ContactsContentObserver.getInstance());
				isRegistered = true;
			}
		}
	}

	@Override
	public void addEventListener(EventListener listener) {
		dispatcher.addListener(listener);
	}

	/**
	 * remove event listener from contacts changes and unregister the observer if no more listeners
	 * @param context
	 * @param listener
	 */
	public void removeEventListener(Context context, EventListener listener) {
		removeEventListener(listener);
		synchronized (lock) {
			if (dispatcher.isEmpty()) {
				context.getContentResolver().unregisterContentObserver(
						ContactsContentObserver.getInstance());
				isRegistered = false;
			}
		}
	}

	@Override
	public void removeEventListener(EventListener listener) {
		dispatcher.removeListener(listener);
	}

	/**
	 * remove all event listeners from contacts changes and unregister the observer if no more listeners
	 * @param context
	 */
	public void removeEventListeners(Context context) {
		removeEventListeners();
		synchronized (lock) {
			if (dispatcher.isEmpty()) {
				context.getContentResolver().unregisterContentObserver(
						ContactsContentObserver.getInstance());
				isRegistered = false;
			}
		}
	}

	@Override
	public void removeEventListeners() {
		dispatcher.removeListeners();
	}

	@Override
	public void notifyListeners(int eventId, ArrayList<Long> messageIDs) {
		dispatcher.notifyListeners(eventId, messageIDs);
	}
}
