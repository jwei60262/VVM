package com.att.mobile.android.vvm.model;

import android.net.Uri;

public class ContactObject {

    private Uri mUri;
    private String mDisplayName;
    private long mContactId;
    private String mContactLookup;
    private long mPhotoId;
    private int mPhoneType;
    private  String mPhoneLabel;
    private static final int DUMMY_PHOTO_ID = 1;

    public ContactObject(Uri uri, String displayName, long contactId, String contactLookup, long photoId, int phoneType, String phoneLabel){
        mUri = uri;
        mDisplayName = displayName;
        mContactId = contactId;
        mContactLookup = contactLookup;
        mPhotoId = photoId;
        mPhoneType = phoneType;
        mPhoneLabel = phoneLabel;
    }

    public ContactObject(Uri uri, String displayName){
        mUri = uri;
        mDisplayName = displayName;
        if ( mUri != null ) {
            mPhotoId = DUMMY_PHOTO_ID;
        }
    }

    public Uri getPhotoUri() {
        if(mPhotoId == 0){
            return null;
        }
        return mUri;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public long getContactId() {
        return mContactId;
    }

    public String getContactLookup() {
        return mContactLookup;
    }

    public long getPhotoId() {
        return mPhotoId;
    }

    public int getPhoneType() {
        return mPhoneType;
    }

    public String getPhoneLabel() {
        return mPhoneLabel;
    }

    @Override
    public String toString() {
        return new StringBuilder().append(" ContactObject mUri=").append(mUri == null ? "null" : mUri.toString()).
                append(" mDisplayName=").append(mDisplayName).
                append(" mContactId=").append(mContactId).
                append(" mContactLookup=").append(mContactLookup).
                append(" mPhotoId=").append(mPhotoId).
                append(" mPhoneType=").append(mPhoneType).
                append(" mPhoneLabel=").append(mPhoneLabel).toString();
    }
}
