package com.att.mobile.android.infra.utils;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PhoneLookup;
import android.text.TextUtils;

import com.att.mobile.android.vvm.R;
import com.att.mobile.android.vvm.VVMApplication;
import com.att.mobile.android.vvm.model.ContactObject;

import java.util.HashMap;
import java.util.Map;


public class ContactUtils {

	public static ContactObject getContact(String phoneNumber){

		if(TextUtils.isEmpty(phoneNumber)){
			return null;
		}

		Cursor cursor = null;

		try {
			// try to find a contact with the passed phone number
			ContentResolver cr = VVMApplication.getContext().getContentResolver();
			Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
			cursor = cr.query(uri, new String[] { ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_ID, ContactsContract.PhoneLookup._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.PhoneLookup.TYPE,ContactsContract.PhoneLookup.LABEL  }, null, null,
					null);
			long photoId = 0;
			String displayName = null;

			// if contact found - try to get its photo and display name
			if (cursor != null && cursor.moveToFirst()) {
				photoId = cursor.getLong(cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_ID));
				displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
				long contactID = cursor.getLong(cursor.getColumnIndex(ContactsContract.PhoneLookup._ID));
				String lookupKey = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY));
				int phoneType = cursor.getInt(cursor.getColumnIndex(ContactsContract.PhoneLookup.TYPE));
				String phoneLabel = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.LABEL));
				uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactID);

				ContactObject contact = new ContactObject(uri, displayName, contactID, lookupKey, photoId, phoneType,phoneLabel);

				return contact;

			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			// releases resources if needed
			if (cursor != null) {
				cursor.close();
			}
		}

		return null;
	}
	
	/**
     * get a cursor with information about a single contact, given the contact
     * id. Returned  is already positioned for getting the information
     * It is caller responsibility to close the cursor when done.
     */
    public static Cursor getContactInfo(Context context, long contactId)
    {
        String where = ContactsContract.Contacts._ID + " = ?";
        String[] whereArgs = new String[] { String.valueOf(contactId) };

        Cursor cursor = context.getContentResolver().query(
        		ContactsContract.Contacts.CONTENT_URI,
                null,
                where,
                whereArgs,
                null);
        if(cursor == null) {
        	return null;
        }
        if(!cursor.moveToFirst()) {
        	cursor.close();
        	return null;
        }

        return cursor;
    }
    
	/**
     * get a cursor with contact information, given a phone number.
	 * Returned cursor is already positioned for getting the information
     * It is caller responsibility to close the cursor when done.
     */
    public static Cursor getContactInfoByPhoneNumber(Context context, String phoneNum)
    {
    	if (phoneNum == null || phoneNum.trim().length() == 0) {
    		return null;
    	}
		Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNum));

		Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
		
		if(cursor == null) {
        	return null;
        }
        if(!cursor.moveToFirst()) {
        	cursor.close();
        	return null;
        }

        return cursor;
    }

	/**
	 * get the display name of a contact, given a cursor retrieved from
	 * contacts content provider
	 */
    public static String getContactDisplayName(Cursor cursor)
	{
        int index = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
        String result = cursor.getString(index);
        return result;
	}
    
    /**
	 * return the id of the given contact at the cursor position.
	 * The id can be used to get other information later
	 */
    public static long getContactId(Cursor cursor)
	{
    	int idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID);
    	long id = cursor.getLong(idIndex);
    	return id;
	}
    
    /**
     * 
     * @param context
     * @param phoneNumber
     * @return
     */
    public static String getContactDisplayName(Context context, String phoneNumber)
    {
    	Cursor cursor = null;
    	try
    	{
	    	cursor = getContactInfoByPhoneNumber(context, phoneNumber);
	    	if (cursor != null){
	    		return getContactDisplayName(cursor);
	    	}
	    	return null;
    	}
    	finally
    	{
    		if(cursor != null)
    		{
    			cursor.close();
    		}
    	}
    	
    }
	static Map<Integer,String> phoneTypeMap = new HashMap<Integer,String>();
	static
	{
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM, VVMApplication.getContext().getString(R.string.phoneTypeCustom));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE, VVMApplication.getContext().getString(R.string.phoneTypeMobile));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_HOME, VVMApplication.getContext().getString(R.string.phoneTypeHome));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_WORK, VVMApplication.getContext().getString(R.string.phoneTypeWork));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_ASSISTANT, VVMApplication.getContext().getString(R.string.phoneTypeAssistant));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_CALLBACK, VVMApplication.getContext().getString(R.string.phoneTypeCallback));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_CAR, VVMApplication.getContext().getString(R.string.phoneTypeCar));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_COMPANY_MAIN, VVMApplication.getContext().getString(R.string.phoneTypeCompanyMain));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME, VVMApplication.getContext().getString(R.string.phoneTypeFaxHome));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK, VVMApplication.getContext().getString(R.string.phoneTypeFaxWork));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_ISDN, VVMApplication.getContext().getString(R.string.phoneTypeIsdn));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MAIN, VVMApplication.getContext().getString(R.string.phoneTypeMain));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MMS, VVMApplication.getContext().getString(R.string.phoneTypeMms));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_OTHER, VVMApplication.getContext().getString(R.string.phoneTypeOther));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_OTHER_FAX, VVMApplication.getContext().getString(R.string.phoneTypeOtherFax));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_PAGER, VVMApplication.getContext().getString(R.string.phoneTypePager));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_RADIO, VVMApplication.getContext().getString(R.string.phoneTypeRadio));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_TELEX, VVMApplication.getContext().getString(R.string.phoneTypeTelex));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_TTY_TDD, VVMApplication.getContext().getString(R.string.phoneTypeTtyTdd));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE, VVMApplication.getContext().getString(R.string.phoneTypeWorkMobile));
		phoneTypeMap.put(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_WORK_PAGER, VVMApplication.getContext().getString(R.string.phoneTypeWorkPager));
	}
	public static String getPhoneTypeText(int phoneType){
		return phoneTypeMap.get(phoneType);
	}
}
