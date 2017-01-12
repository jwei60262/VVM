package com.att.mobile.android.vvm.control;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.widget.ImageView;
import android.widget.TextView;

import com.att.mobile.android.infra.utils.Logger;
import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.model.Constants.EVENTS;
import com.att.mobile.android.vvm.screen.InboxActivity;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;

/**
 * 
 * 
 * @author Inon Stelman
 * 
 */

public class AsyncLoader {

	// holds the default avatar image, for cases where the contact has not
	// avatar
	private final Drawable defaultAvatar;

	// the simplest in-memory cache implementation. This should be replaced with
	// something like SoftReference or BitmapOptions.inPurgeable(since 1.6)

	private HashMap<String, SoftReference<Bitmap>> photosCache = new HashMap<String, SoftReference<Bitmap>>();

	private HashMap<String, String> namesCache = new HashMap<String, String>();

	private HashMap<String, String> lookupKeysCache = new HashMap<String, String>();

	private Context context;

	private DataQueue dataQueue = new DataQueue();
	private DataLoader dataLoaderThread;
	

	public AsyncLoader(Context context) {
		// Make the background thread low priority. This way it will not affect
		// the UI performance
		
		this.context = context;

		dataLoaderThread = new DataLoader(context);
		dataLoaderThread.setPriority(Thread.NORM_PRIORITY - 1);

		// gets the default avatar
		defaultAvatar = context.getResources().getDrawable(R.drawable.avatar_no_photo);
	}

	public void displayData(long id, String phoneNumber, ImageView imageView, TextView textView) {

		if (photosCache.containsKey(phoneNumber)) {
			SoftReference<Bitmap> photo = photosCache.get(phoneNumber);
			// if the soft reference itself is null it means that there is no
			// photo for this contact
			// and we use the default icon
			if (photo == null) {
				imageView.setImageDrawable(defaultAvatar);
			}
			// if the photo is cached and still exists in the soft reference we
			// use it
			else if (photo.get() != null) {
				imageView.setImageBitmap(photo.get());
			}
			// if the soft reference exists but the bitmap was garbage collected
			// we put it in the queue
			// so we fetch it again
			else {
				queueData(id, phoneNumber, imageView, textView);
				return;
			}

			String cachedName = namesCache.get(phoneNumber);
			if (cachedName != null && !cachedName.equals("")) {
				textView.setText(cachedName);
			}
		} else {
			queueData(id, phoneNumber, imageView, textView);
		}
	}

	public void removeFromCache(String phoneNumber) {
		photosCache.remove(phoneNumber);
		namesCache.remove(phoneNumber);
		lookupKeysCache.remove(phoneNumber);
	}

	// queue a phone number for retrieval of photo and name
	private void queueData(long id, String phoneNumber, ImageView imageView, TextView textView) {
		// This ImageView may be used for other images before. So there may be
		// some old tasks in the queue. We need to discard them.
		// dataQueue.clean(imageView);
		DataToLoad p = new DataToLoad(id, phoneNumber, imageView, textView);
		synchronized (dataQueue.dataToLoad) {
			Logger.d("***AsyncLoader***", "pushed into queue id = [" + id + "]");

			dataQueue.dataToLoad.push(p);
			dataQueue.dataToLoad.notifyAll();
		}

		// start thread if it's not started yet
		// this line is crashing on motorola atrix platform 2.2
		if (dataLoaderThread.getState() == Thread.State.NEW)
			dataLoaderThread.start();
	}

	private Bitmap updateContactCaches(String phoneNumber, int width, int height) {
		Cursor cursor = null;
		Bitmap bitmap = null;
		InputStream is = null;

		try {
			// try to find a contact with the passed phone number
			ContentResolver cr = context.getContentResolver();
			Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
			cursor = cr.query(uri, new String[] { PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_ID, PhoneLookup._ID, Contacts.LOOKUP_KEY }, null, null, null);
			
			long photoId = 0;
			String displayName = null;

			// if contact found - try to get its photo and display name
			if (cursor != null && cursor.moveToFirst()) {
				photoId = cursor.getLong(cursor.getColumnIndex(PhoneLookup.PHOTO_ID));
				displayName = cursor.getString(cursor.getColumnIndex(PhoneLookup.DISPLAY_NAME));
				long contactID = cursor.getLong(cursor.getColumnIndex(PhoneLookup._ID));
				String lookupKey = cursor.getString(cursor.getColumnIndex(Contacts.LOOKUP_KEY));

				uri = null;
				uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactID);

				// save the contactID into cache
				lookupKeysCache.put(phoneNumber, lookupKey);

				is = null;
				if (photoId != 0) {
					is = ContactsContract.Contacts.openContactPhotoInputStream(cr, uri);
				}
				namesCache.put(phoneNumber, displayName);

				// set the default icon for this contact (null - means load the
				// default icon)
				if (is == null) {
					photosCache.put(phoneNumber, null);
				}
				else{
					bitmap = BitmapFactory.decodeStream(is);
					if (width > 0 && height > 0) {
						bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
					}
					photosCache.put(phoneNumber, new SoftReference<Bitmap>(bitmap));
				}
			} else {
				removeFromCache(phoneNumber);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			// releases resources if needed
			if (cursor != null) {
				cursor.close();
			}
			if(is != null){
				try {
					is.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return bitmap;
	}

	public Uri getContactUri(String phoneNumber) {
		if (phoneNumber == null) {
			return null;
		}

		if (lookupKeysCache.containsKey(phoneNumber)) {
			return Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKeysCache.get(phoneNumber));
		}

		return null;
	}

	/**
	 * when contacts DB was changed we run a background updated so our contact cache is updated all time 
	 * and a real time data will be displayed in the inbox screen
	 */
	public void updatedCaches() {
		
		Set<String> cachePhoneNumbers = lookupKeysCache.keySet();
		Iterator<String> it = cachePhoneNumbers.iterator();
		
		// use default avater to supply with width and height so that the contacts avatar can be resized before put in the cache
		ImageView defaultImage = new ImageView(context);
		defaultImage.setImageDrawable(defaultAvatar);
		
		// go over the phone numbers in the caches and queue a task that will update from address book
		// we don't set the id or image tag so tag the queue will know there is no UI components to update on this task
		while(it.hasNext()){
			queueData(-1, it.next(), defaultImage, null);
		}
	}

	// Task for the queue
	private static class DataToLoad {
		public long id;
		public String phoneNumber;
		public ImageView imageView;
		public TextView textView;

		public DataToLoad(long id, String phoneNumber, ImageView imageView, TextView textView) {
			this.phoneNumber = phoneNumber;
			this.imageView = imageView;
			this.textView = textView;
			this.id = id;
		}
	}

	public void stopThread() {
		dataLoaderThread.interrupt();
	}

	// stores list of photos to download
	private static class DataQueue {
		private Stack<DataToLoad> dataToLoad = new Stack<DataToLoad>();

		// removes all instances of this ImageView
		// public void clean(ImageView image){
		// Iterator<DataToLoad> iterator = dataToLoad.iterator();
		// while (iterator.hasNext()) {
		// AsyncLoader.DataToLoad dataToLoad = (AsyncLoader.DataToLoad)
		// iterator.next();
		// if ( dataToLoad.imageView == image){
		// iterator.remove();
		// }
		//
		// }
		// }
	}

	private class DataLoader extends Thread {
		
		private Context context;
		
		// set when tasks that do not update view and UI directly are handled
		// so the queue can notify the inbox that such tasks were handled 
		// and that data in the cache was updated with the UI being updated
		boolean inboxRefreshNeeded = false;
		
		public DataLoader(Context context) {
			this.context = context;
		}
		
		public void run() {
			try {
				while (true) {
					// thread waits until there are any images to load in the
					// queue
					synchronized (dataQueue.dataToLoad) {
						if (dataQueue.dataToLoad.size() == 0) {
							// notify inbox and reset flag
							if (inboxRefreshNeeded){
								if (context instanceof InboxActivity){
									((InboxActivity)context).onUpdateListener(EVENTS.REFRESH_UI, null);	
								}
								inboxRefreshNeeded = false;
							}
							dataQueue.dataToLoad.wait();
						}
					}
					if (dataQueue.dataToLoad.size() != 0) {
						DataToLoad dataToLoad;
						synchronized (dataQueue.dataToLoad) {
							dataToLoad = dataQueue.dataToLoad.pop();
						}
						Bitmap bitmap = updateContactCaches(dataToLoad.phoneNumber, dataToLoad.imageView.getWidth(), dataToLoad.imageView.getHeight());

						// when no tag is exist - only update of the cache will take place and no ui components will be updated
						Long imageViewTag = (Long) dataToLoad.imageView.getTag(R.string.key_one);
						if (imageViewTag != null && imageViewTag == dataToLoad.id) {
							Logger.d("***AsyncLoader***", "popped from queue id = [" + dataToLoad.id + "]");
							DataDisplayer dataDisplayerTask = new DataDisplayer(bitmap, dataToLoad.imageView, namesCache.get(dataToLoad.phoneNumber), dataToLoad.textView);
							Activity viewContextActivity = (Activity) dataToLoad.imageView.getContext();
							viewContextActivity.runOnUiThread(dataDisplayerTask);
						}else{
							// inbox should refresh its UI
							inboxRefreshNeeded = true;
						}
					}
					if (Thread.interrupted())
						break;
				}
			} catch (InterruptedException e) {
				// allow thread to exit
			}
		}
	}

	// Used to display bitmap in the UI thread
	private static class DataDisplayer implements Runnable {
		private Bitmap bitmap;
		private ImageView imageView;
		private TextView textView;
		private String name;

		private DataDisplayer(Bitmap b, ImageView i, String name, TextView t) {
			bitmap = b;
			imageView = i;
			this.name = name;
			textView = t;

		}

		public void run() {
			if (bitmap != null) {
				imageView.setImageBitmap(bitmap);
			}
			if (name != null && name.length() > 0) {
				textView.setText(name);
			}
		}
	}

	/**
	 * Gets the default avatar for message's contact.
	 * 
	 * @return (Drawable) the default avatar.
	 */
	public Drawable getDefaultAvatar() {
		return defaultAvatar;
	}

}
