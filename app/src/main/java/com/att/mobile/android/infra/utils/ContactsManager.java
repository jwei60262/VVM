package com.att.mobile.android.infra.utils;

import java.util.HashMap;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;

import com.att.mobile.android.vvm.model.Contact;

/**
 * The contacts manager allows the UI and data managers to access
 * the contacts database on the android device.
 * It also maintains a cache of recently accessed contacts, and provides
 * quick lookup by phone number or contact Id
 * TODO need to clear the cache when contacts database is updated
 * (register a broadcast receiver somewhere)
 */
public class ContactsManager
{
	/** the contacts manager has only a single instance */
	private static ContactsManager sInstance = new ContactsManager();

	/** map from phone number to contact information structure, caches
	 * recently accessed phone numbers
	 */
	private HashMap<String, Contact> phoneLookup;

	private Context context;

	private ContactsManager(){
		phoneLookup = new HashMap<String, Contact>();
	}

	public static ContactsManager getInstance(){
		return sInstance;
	}


	/*
	 * Reset contacts structures cache
	 */
	public void resetCache(){
		phoneLookup.clear();
	}

	/**
	 * locates a contact by phone number.
	 * If the contact is in the cache, returns it, otherwise look it up
	 * in the contacts database. If not found, still cache a Contact structure
	 * for unknown contact
	 * Supply a context (Activity usually) so the contacts manager can
	 * access the contacts database (content provider)
	 */
	public Contact lookupContactByPhone(Context context, String number)
	{
		this.context = context;

		if(TextUtils.isEmpty(number)) {
			return null;
		}

		// first, look in the cache
		Contact result = phoneLookup.get(number);
		if(result != null) {
			return result;
		}
		
		Cursor c = null;
		try
		{
			// look in the contacts database on the phone
			c = ContactUtils.getContactInfoByPhoneNumber(context, number);
			if(c == null) {
				// unknown contact
				return null;
			} else {
				// populate the Contact from the returned cursor
				result = createContactFromCursor(c, number);
			}

			// put it in the cache
			phoneLookup.put(number, result);
	
			return result;
		}
		finally
		{
			//releases resources if needed
			if(c != null)
			{
				c.close();
			}
		}
	}

	/**
	 * create a Contact structure for an existing contact, given a Cursor
	 * of contact information from contacts content provider
	 */
	private Contact createContactFromCursor(Cursor cursor, String number)
	{
		//TODO save phone type,
		Contact result = new Contact();
		if(cursor.moveToFirst()){
			result.contactId = ContactUtils.getContactId(cursor);
			initContactNames(result, number);

			Contact current = result;
			while(cursor.moveToNext()){
				Contact tmp = new Contact();
				tmp.contactId = ContactUtils.getContactId(cursor);
				initContactNames(tmp, number);
				current.next = tmp;
				current = tmp;
			}
		}
		return result;
	}

	/*
	 * Assign names to the given contact. Try using structured name first
	 * If not found - get the base display name and if that not found either -
	 * just set the phone number as display name
	 */
	private void initContactNames(Contact c, String num){
		Cursor nameCursor = getContactName(context, c.contactId);
		c.displayName = "";
		if(nameCursor != null){
			try{
				c.displayName = getContactDisplayName(nameCursor);
				c.firstName = getContactFirstName(nameCursor);
				// workaround for phonebook lookup problem:
				if (c.firstName == null)
					c.firstName = "";
				c.middleName = getContactMidName(nameCursor);
				c.lastName = getContactLastName(nameCursor);
			}finally{
					nameCursor.close();
			}
		}
		if(TextUtils.isEmpty(c.displayName)) {
			Cursor cursor = ContactUtils.getContactInfo(context, c.contactId);
			if(cursor != null) {
				try {
					c.displayName = ContactUtils.getContactDisplayName(cursor);
				}finally{
						cursor.close();
				}
			}
			if(TextUtils.isEmpty(c.displayName))
				c.displayName = num;
		}
	}

    private static Cursor getContactName(Context context, long contactId)
    {
    	// query from data
    	ContentResolver resolver = context.getContentResolver();
    	Uri uri = ContactsContract.Data.CONTENT_URI;
    	String where = ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?";
        String[] whereArgs = new String[]{String.valueOf(contactId),
        								ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE};

    	Cursor c = resolver.query(uri, null, where, whereArgs, null);
    	if(c != null){
    		if(!c.moveToFirst()){
    			c.close();
    			return null;
    		}
    	}
    	return c;
    }

    private static String getContactDisplayName(Cursor cursor)
	{
        int index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
        String result = cursor.getString(index);
        return result;
	}
    private static String getContactFirstName(Cursor cursor)
	{
        int index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
        String result = cursor.getString(index);
        return result;
	}
    private static String getContactMidName(Cursor cursor)
	{
        int index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME);
        String result = cursor.getString(index);
        return result;
	}
    private static String getContactLastName(Cursor cursor)
	{
        int index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME);
        String result = cursor.getString(index);
        return result;
	}
}
